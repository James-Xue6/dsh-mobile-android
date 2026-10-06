<#
一条命令发版：DSH 掌上通

发版节奏（重要）：
  **攒够改动再发版**，不要每改一点就发。每次发版都会让所有已安装的用户收到更新提示，
  发太勤会打扰人。建议一个「有一件值得说的事」的批次发一次。
  版本号按 0.8 -> 0.81 -> 0.82 -> ... 递增；
  versionCode 由脚本自动 +1，负责真正的「新旧」判断（版本号只是给人看的）。

用法（仓库根目录）：
    pwsh -File .\release.ps1 -Version 0.81 -Notes "这次改了什么"
    pwsh -File .\release.ps1 -Version 0.81 -Notes "..." -SkipPush     # 只做本地

注意：**普通的 git 提交不会触发更新提示** —— 只有本脚本（改 version.json + 打 tag）才会。
所以平时随便提交，想发版时再跑这个。

它会依次做：
 1. 校验版本号格式（0.81 / 0.8.1 这种都行），并与当前 AndroidManifest 比对
 2. 改 AndroidManifest：versionName=x.y，versionCode=当前+1
 3. 构建 APK（build.ps1）
 4. 更新 dist/version.json 更新清单（App 的「检查更新」就是读它）
 5. 把新 APK 与 version.txt 同步进本机插件目录（局域网直发用）
 6. git commit + tag vX.Y + push（含 tag）；若配了 gitee 远程则一并同步
 7. 刷新 jsDelivr 对 version.json 与 APK 的缓存，让老客户端立刻看到新版本

发完之后：老版本 App 启动时会自动提示更新（6 小时内只查一次），
也可以在 设置 → 关于 → 检查更新 里手动查。

[2026-10-07 合并朋友 fork 的改进 · 逐条落地]
  · 文本一律用 .NET 显式读写 UTF-8（**无 BOM**）—— Windows PowerShell 5.1 下
    Get-Content 默认按 ANSI 代码页解码，会把 AndroidManifest.xml 里的中文注释写坏；
    而 Set-Content -Encoding utf8 在 5.1 会写出 BOM（BOM 对 JSON/YAML 是坏味道）。
  · 写 dist/version.json 后**必须能解析回来**，否则中止发版（JSON_SELFCHECK）：
    更新说明里混进英文引号会把 JSON 撑破，url 字段被说明文字挤占 → App 拿着假网址下载必然失败。
  · git push 前探测配置里的代理是否还活着：端口不通就**本次绕过**它，
    否则会以「Failed to connect to github.com:443 over proxy …」直接失败（报错看着像网络问题，很难查）。
  · 下载地址可由 -ApkBase 覆盖；未指定时按远程推导（**优先 gitee**：国内直连最快，
    且本脚本本来就会同步到 gitee），jsDelivr / GitHub 仍作为 mirror 保留。
  · -AllowLocalKey 透传给 build.ps1（fork / 本机构建用本机密钥）；
    为避免「非官方密钥的包被推给所有用户」，它与 -SkipPush 必须同时出现。
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$Version,
  [Parameter(Mandatory = $true)][string]$Notes,
  [switch]$SkipPush,
  # 透传给 build.ps1：没有官方密钥库时用本机专用密钥构建（fork / 本机二次开发用）。
  # 本仓库的官方密钥存在，正常发版**不需要**它。
  [switch]$AllowLocalKey,
  # 下载地址前缀（默认按远程推导）。形如 https://gitee.com/<owner>/<repo>/raw
  [string]$ApkBase = ''
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# ---------------------------------------------------------------- UTF-8 读写（5.1 兼容）
$utf8 = New-Object System.Text.UTF8Encoding($false)
function Read-Text([string]$p) { return [System.IO.File]::ReadAllText($p, $utf8) }
function Write-Text([string]$p, [string]$t) { [System.IO.File]::WriteAllText($p, $t, $utf8) }
# 写 JSON：5.1 的 ConvertTo-Json 会把中文转义成 \uXXXX（合法但难读），还原后再解析校验一次
function Write-JsonFile([string]$p, $o) {
  $json = $o | ConvertTo-Json -Depth 6
  $unescaped = [System.Text.RegularExpressions.Regex]::Replace(
    $json, '(?<!\\)\\u([0-9a-fA-F]{4})',
    [System.Text.RegularExpressions.MatchEvaluator] { param($m) [string][char]([Convert]::ToInt32($m.Groups[1].Value, 16)) })
  $text = $json
  try { $null = $unescaped | ConvertFrom-Json; $text = $unescaped } catch { }
  Write-Text $p $text
}

# ---------------------------------------------------------------- git 代理探测
# 配置里的代理可能是历史遗留（本机 127.0.0.1:7890 关掉之后）：端口不通就本次绕过，
# 否则 push 会直接失败，而报错信息看起来像「网络问题」，很难查。
function Get-GitProxyArgs {
  $p = (& git config --get http.proxy) 2>$null
  if (-not $p) { return @() }
  if ($p -match '^\s*https?://(?:[^@/]*@)?([^:/]+):(\d+)') {
    $proxyHost = $Matches[1]
    $proxyPort = [int]$Matches[2]
    $alive = Test-NetConnection -ComputerName $proxyHost -Port $proxyPort -InformationLevel Quiet -WarningAction SilentlyContinue
    if (-not $alive) {
      Write-Host "  （配置的 git 代理 $proxyHost`:$proxyPort 连不上，本次 push 绕过它）" -ForegroundColor Yellow
      return @('-c', 'http.proxy=', '-c', 'https.proxy=')
    }
    Write-Host "  （git 代理 $proxyHost`:$proxyPort 可达，按配置使用）"
  }
  return @()
}

# 允许 0.81 这种两位、也允许 0.8.1 这种三段
if ($Version -notmatch '^\d+(\.\d+)+$') { throw "版本号要形如 0.81 或 0.8.1（收到：$Version）" }

# 非官方密钥的包**绝不允许**走发版推送：那会让所有已装旧版的用户覆盖安装失败。
# 允许 -AllowLocalKey 只是为了 fork/本机能构建出包，因此必须与 -SkipPush 同时出现。
if ($AllowLocalKey -and -not $SkipPush) {
  throw "-AllowLocalKey（用本机密钥构建）与推送发版不能同时使用：本机密钥的包推给用户会让老用户覆盖安装失败。要本机构建请加 -SkipPush。"
}

$manifestPath = Join-Path $PSScriptRoot 'AndroidManifest.xml'
# 显式 UTF-8 读取：Get-Content 在 Windows PowerShell 5.1 下按 ANSI 解码，会把中文注释写坏
$manifest = Read-Text $manifestPath
$mCode = [regex]::Match($manifest, 'android:versionCode="(\d+)"')
$mName = [regex]::Match($manifest, 'android:versionName="([^"]+)"')
if (-not $mCode.Success -or -not $mName.Success) { throw 'AndroidManifest 里找不到 versionCode/versionName' }
$oldCode = [int]$mCode.Groups[1].Value
$oldName = $mName.Groups[1].Value
$newCode = $oldCode + 1

Write-Host "`n=== 发版 $oldName -> $Version（versionCode $oldCode -> $newCode）===" -ForegroundColor Cyan

# ---------------------------------------------------------------- 改版本号并构建
$manifest = $manifest.Replace("android:versionCode=""$oldCode""", "android:versionCode=""$newCode""")
$manifest = $manifest.Replace("android:versionName=""$oldName""", "android:versionName=""$Version""")
# 显式 UTF-8 **无 BOM** 写回：Set-Content -Encoding utf8 在 5.1 会加 BOM
Write-Text $manifestPath $manifest
Write-Host "  OK AndroidManifest 已更新"

$buildArgs = @()
if ($AllowLocalKey) { $buildArgs += '-AllowLocalKey' }
& (Join-Path $PSScriptRoot 'build.ps1') @buildArgs
$apk = Join-Path $PSScriptRoot 'dist\dsh-mobile.apk'
if (-not (Test-Path $apk)) { throw '构建没有产出 dist\dsh-mobile.apk' }

# ---------------------------------------------------------------- 更新清单
$slug = 'James-Xue6/dsh-mobile-android'
$versionJsonPath = Join-Path $PSScriptRoot 'dist\version.json'

# 下载地址：优先用 -ApkBase；没传就按远程推导。
# 为什么**优先 gitee**（而不是 origin=GitHub）：本项目的用户在国内，
# raw.githubusercontent.com 常年直连超时，而 gitee 镜像本来就会在下面同步（快得多）。
# 推导不出来时退回原来的 jsDelivr 地址（保持既有行为，不因为推导失败而发不出版）。
function Get-DownloadUrl([string]$ver) {
  if ($ApkBase) { return "$ApkBase/dist/dsh-mobile.apk" }
  $remotes = @(& git remote)
  if ($remotes -contains 'gitee') {
    $gu = (& git remote get-url gitee) 2>$null
    if ($gu -match 'gitee\.com[:/]([^/]+)/([^/]+?)(?:\.git)?/?$') {
      return "https://gitee.com/$($Matches[1])/$($Matches[2])/raw/v$ver/dist/dsh-mobile.apk"
    }
  }
  $origin = (& git remote get-url origin) 2>$null
  if ($origin -match 'gitee\.com[:/]([^/]+)/([^/]+?)(?:\.git)?/?$') {
    return "https://gitee.com/$($Matches[1])/$($Matches[2])/raw/v$ver/dist/dsh-mobile.apk"
  }
  if ($origin -match 'github\.com[:/]([^/]+)/([^/]+?)(?:\.git)?/?$') {
    return "https://raw.githubusercontent.com/$($Matches[1])/$($Matches[2])/v$ver/dist/dsh-mobile.apk"
  }
  Write-Warning "认不出远程，下载地址退回 jsDelivr：$origin"
  return "https://cdn.jsdelivr.net/gh/$slug@v$ver/dist/dsh-mobile.apk"
}

# **JSON 自检（JSON_SELFCHECK）**：更新说明里一旦混进英文引号，拼出来的 version.json 会被撑破 ——
# 那时 url 字段会挤进说明文字，App 拿着假网址下载必然失败（用户实测"更新不了"就是它 ✗）。
# 所以：写完立刻解析回来，解析不了就直接中止发版，绝不让坏包发出去。
function Assert-Json([string]$path) {
  try {
    $o = (Read-Text $path) | ConvertFrom-Json
    if (-not $o.url -or $o.url -notmatch '^https?://') { throw "url 字段不是合法网址：$($o.url)" }
    if (-not $o.notes) { throw 'notes 字段为空' }
    if (-not $o.sha256) { throw 'sha256 字段为空' }
    Write-Host "  OK version.json 自检通过（url 合法、notes/sha256 完整）"
  } catch {
    throw "version.json 自检失败，已中止发版（八成是 -Notes 里混了英文引号，改用「」）：$($_.Exception.Message)"
  }
}

# 保留上一版清单里的手工配置（feedback / page），别被发版冲掉
$keep = [ordered]@{}
if (Test-Path $versionJsonPath) {
  try {
    $old = Read-Text $versionJsonPath | ConvertFrom-Json
    if ($old.feedback) { $keep.feedback = $old.feedback }
    if ($old.page) { $keep.page = $old.page }
  } catch { Write-Warning '旧的 version.json 解析失败，将重新生成' }
}

$manifestJson = [ordered]@{
  versionCode = $newCode
  versionName = $Version
  notes       = $Notes
  # 主线路：按远程推导（国内优先 gitee）；-ApkBase 可覆盖
  url         = (Get-DownloadUrl $Version)
  # 备用线路：jsDelivr CDN（全局可达；主线路挂了它顶上）。
  # 注意**不能**指向上游作者的仓库：那边签名/内容与我们的包不同，sha256 必然对不上 ✗
  mirror      = "https://cdn.jsdelivr.net/gh/$slug@v$Version/dist/dsh-mobile.apk"
  # [应用内更新·2026-10-05] 给 App 内下载做完整性校验用：App 下完 APK 会实算 sha256 比对，
  # 不一致就丢弃并提示（防 CDN/中间人给到坏包）。size 用于"清单缺 sha256"时的降级核对。
  sha256      = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
  size        = (Get-Item -LiteralPath $apk).Length
}
if (-not $keep.page) { $keep.page = "https://github.com/$slug" }
foreach ($k in $keep.Keys) { $manifestJson[$k] = $keep[$k] }
if (-not $manifestJson.Contains('feedback')) {
  Write-Warning '清单里没有 feedback 段：App 的「意见反馈」将只能「复制」或「发到用户自己的电脑」'
}

Write-JsonFile $versionJsonPath $manifestJson
Assert-Json $versionJsonPath      # 自检：解析不了就中止发版 —— 绝不让假网址发出去
Write-Host "  OK dist/version.json 已更新（versionCode=$newCode）"
Write-Host "     主线路 $($manifestJson.url)"
Write-Host "     备用   $($manifestJson.mirror)"

# ---------------------------------------------------------------- 同步插件目录
$localApp = Join-Path $env:USERPROFILE '.dsh\local-plugins\dsh-mobile-access\app'
if (Test-Path (Split-Path $localApp -Parent)) {
  New-Item -ItemType Directory -Force -Path $localApp | Out-Null
  Copy-Item $apk (Join-Path $localApp 'dsh-mobile.apk') -Force
  # version.txt 显式 UTF-8 无 BOM（Set-Content -Encoding utf8 在 5.1 会带 BOM）
  Write-Text (Join-Path $localApp 'version.txt') $Version
  Write-Host "  OK 已同步到本机插件目录（局域网直发）"
} else {
  Write-Warning "本机没有安装 dsh-mobile-access 插件，跳过插件目录同步"
}

# ---------------------------------------------------------------- git
# [2026-10-07 合并自测发现的真 bug] 这几步原来**不看退出码**：仓库里没配 user.name/user.email
# 时 `git commit` 会以 "Author identity unknown" 失败，而脚本照样打印「OK 已提交并打 tag」，
# 发版看似成功、其实什么都没提交 —— 后面 push 也就没有东西可推。
# 现在每一步都查退出码，失败就中止（并且**不再**打印那句 OK）。
& git add -A
if ($LASTEXITCODE -ne 0) { throw "git add 失败 (exit $LASTEXITCODE)" }
& git commit -q -m "release: $Version`n`n$Notes`n`nversionCode $oldCode -> $newCode"
if ($LASTEXITCODE -ne 0) {
  throw "git commit 失败 (exit $LASTEXITCODE)。常见原因：仓库没配提交身份 —— 跑一次 `git config user.name 你的名字` 与 `git config user.email 你的邮箱` 再重试。"
}
& git tag -a "v$Version" -m "DSH 掌上通 $Version：$Notes"
if ($LASTEXITCODE -ne 0) { throw "git tag 失败 (exit $LASTEXITCODE)" }
Write-Host "  OK 已提交并打 tag v$Version"
if (-not $SkipPush) {
  # 推送前先看配置里的代理还活着没有：不通就本次绕过（否则报错像"网络问题"，很难查）
  $proxyArgs = Get-GitProxyArgs
  & git @proxyArgs push
  if ($LASTEXITCODE -ne 0) { throw "git push 失败 (exit $LASTEXITCODE)：分支没推上去，老客户端就看不到新版本" }
  & git @proxyArgs push origin "v$Version"
  if ($LASTEXITCODE -ne 0) { throw "git push tag v$Version 失败 (exit $LASTEXITCODE)" }
  Write-Host "  OK 已推送分支与 tag（GitHub）"

  # 如果配了 gitee 远程，一并同步：国内用户从 Gitee 拉代码/下安装包更快
  # （gitee 是国内的，**不套代理**：套上去只会更慢/更容易失败）
  $remotes = @(& git remote)
  if ($remotes -contains 'gitee') {
    try {
      & git push gitee HEAD:main
      & git push gitee "v$Version"
      Write-Host "  OK 已同步到 Gitee"
    } catch {
      Write-Warning "同步 Gitee 失败（不影响 GitHub）：$($_.Exception.Message)"
    }
  } else {
    Write-Host "  （未配置 gitee 远程，跳过同步；配好后自动会带上）"
  }
} else {
  Write-Host "  (--SkipPush：未推送)"
}

# ---------------------------------------------------------------- 刷 CDN 缓存
if (-not $SkipPush) {
  foreach ($p in @("dist/version.json", "dist/dsh-mobile.apk")) {
    $u = "https://purge.jsdelivr.net/gh/$slug@main/$p"
    try { Invoke-WebRequest $u -UseBasicParsing -TimeoutSec 40 | Out-Null; Write-Host "  OK 已刷新 CDN 缓存：$p" }
    catch { Write-Warning "刷新 CDN 缓存失败（不影响使用，最多晚 12 小时生效）：$p" }
  }
}

Write-Host "`n=== 发版完成：$Version ===" -ForegroundColor Green
Write-Host @"
老版本 App 的更新提醒会自动生效（启动时查一次；也可在 设置 → 关于 → 检查更新 手动查）。
更新包下载地址：
  主线路 $($manifestJson.url)
  备用   $($manifestJson.mirror)
  校验值 sha256 $($manifestJson.sha256)
"@
