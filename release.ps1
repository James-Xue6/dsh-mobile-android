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
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$Version,
  [Parameter(Mandatory = $true)][string]$Notes,
  [switch]$SkipPush
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# 允许 0.81 这种两位、也允许 0.8.1 这种三段
if ($Version -notmatch '^\d+(\.\d+)+$') { throw "版本号要形如 0.81 或 0.8.1（收到：$Version）" }

$manifestPath = Join-Path $PSScriptRoot 'AndroidManifest.xml'
$manifest = Get-Content $manifestPath -Raw
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
Set-Content $manifestPath $manifest -Encoding utf8 -NoNewline
Write-Host "  OK AndroidManifest 已更新"

& (Join-Path $PSScriptRoot 'build.ps1')
$apk = Join-Path $PSScriptRoot 'dist\dsh-mobile.apk'
if (-not (Test-Path $apk)) { throw '构建没有产出 dist\dsh-mobile.apk' }

# ---------------------------------------------------------------- 更新清单
$slug = 'James-Xue6/dsh-mobile-android'
$versionJsonPath = Join-Path $PSScriptRoot 'dist\version.json'

# 保留上一版清单里的手工配置（feedback / page），别被发版冲掉
$keep = [ordered]@{}
if (Test-Path $versionJsonPath) {
  try {
    $old = Get-Content $versionJsonPath -Raw | ConvertFrom-Json
    if ($old.feedback) { $keep.feedback = $old.feedback }
    if ($old.page) { $keep.page = $old.page }
  } catch { Write-Warning '旧的 version.json 解析失败，将重新生成' }
}

$manifestJson = [ordered]@{
  versionCode = $newCode
  versionName = $Version
  notes       = $Notes
  url         = "https://cdn.jsdelivr.net/gh/$slug@v$Version/dist/dsh-mobile.apk"
  mirror      = "https://github.com/$slug/raw/v$Version/dist/dsh-mobile.apk"
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

$manifestJson | ConvertTo-Json -Depth 6 | Set-Content $versionJsonPath -Encoding utf8
Write-Host "  OK dist/version.json 已更新（versionCode=$newCode）"

# ---------------------------------------------------------------- 同步插件目录
$localApp = Join-Path $env:USERPROFILE '.dsh\local-plugins\dsh-mobile-access\app'
if (Test-Path (Split-Path $localApp -Parent)) {
  New-Item -ItemType Directory -Force -Path $localApp | Out-Null
  Copy-Item $apk (Join-Path $localApp 'dsh-mobile.apk') -Force
  Set-Content (Join-Path $localApp 'version.txt') $Version -Encoding ascii -NoNewline
  Write-Host "  OK 已同步到本机插件目录（局域网直发）"
} else {
  Write-Warning "本机没有安装 dsh-mobile-access 插件，跳过插件目录同步"
}

# ---------------------------------------------------------------- git
& git add -A
& git commit -q -m "release: $Version`n`n$Notes`n`nversionCode $oldCode -> $newCode"
& git tag -a "v$Version" -m "DSH 掌上通 $Version：$Notes"
Write-Host "  OK 已提交并打 tag v$Version"
if (-not $SkipPush) {
  & git push
  & git push origin "v$Version"
  Write-Host "  OK 已推送分支与 tag（GitHub）"

  # 如果配了 gitee 远程，一并同步：国内用户从 Gitee 拉代码/下安装包更快
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
  CDN    $($manifestJson.url)
  GitHub $($manifestJson.mirror)
"@
