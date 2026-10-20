<#
安装 DSH 掌上通 的 PC 端插件（dsh-mobile-access）。

用法（在仓库根目录执行）：
    pwsh -File .\pc-plugin\install.ps1
    pwsh -File .\pc-plugin\install.ps1 -Profile desktop     # 指定 profile

它会做四件事：
 1. 把 pc-plugin\dsh-mobile-access 复制到 ~/.dsh/local-plugins/dsh-mobile-access
    （旧目录与任何同名残留先挪到 ~/.dsh/backups/ —— **不留在 local-plugins 里**，
      否则 DSH 会扫到第二个同名插件，用户看到"两个插件"甚至渲染到旧版面板）
 2. 把安装包 dist\dsh-mobile.apk 放进插件目录的 app\（面板据此提供「扫码下载 App」）
 3. 在目标 profile 的 package.json 里登记依赖与 bundle
    （dsh-plugin-mobile-gateway 只登记依赖；它的宿主行由本插件的 cordis.patch.yml 挂载，
     旧版写进 bundles 的残留项会被摘掉，避免同一个行被两个 bundle 层各插一次）
 4. 在目标 profile 的 cordis.patch.yml 里补一段 mobile-gateway 配置（lanPort 3091，避开 dsh-pocket 的 3081）

改完需要**重启一次 DSH** 才生效。原文件都会先备份成 *.bak-install。
#>
[CmdletBinding()]
param(
  [string]$Profile = 'desktop'
)

$ErrorActionPreference = 'Stop'

# ── [2026-10-07 合并朋友 fork] 文本一律显式按 UTF-8（无 BOM）读写 ──────────────
# 为什么不用 Get-Content / Set-Content 的默认行为：
#   · Windows PowerShell 5.1 的 Get-Content 默认按 ANSI 代码页解码，而 profile 的
#     package.json / cordis.patch.yml 里有中文（例如 link:L:/DSH自制插件/...）——
#     读成乱码再写回去就会**把用户的中文路径写坏**；
#   · Set-Content -Encoding utf8 在 5.1 会写出 BOM，JSON/YAML 前面的 BOM 会让一些
#     解析器报错，version.txt 也会带上看不见的字符。
# 注意：**本脚本自身的 [1/4] 备份位置等修复照旧**（朋友那一版把它们退回去了，没有采用）。
$utf8 = New-Object System.Text.UTF8Encoding($false)
function Read-Text([string]$p) { return [System.IO.File]::ReadAllText($p, $utf8) }
function Write-Text([string]$p, [string]$t) { [System.IO.File]::WriteAllText($p, $t, $utf8) }
# 写 JSON：5.1 的 ConvertTo-Json 会把中文转义成 \uXXXX（仍合法、DSH 读得懂，但人看着累），
# 还原成字符后再解析校验一次，不过就用转义版兜底。
function Write-JsonFile([string]$p, $o) {
  $json = $o | ConvertTo-Json -Depth 20
  $unescaped = [System.Text.RegularExpressions.Regex]::Replace(
    $json, '(?<!\\)\\u([0-9a-fA-F]{4})',
    [System.Text.RegularExpressions.MatchEvaluator] { param($m) [string][char]([Convert]::ToInt32($m.Groups[1].Value, 16)) })
  $text = $json
  try { $null = $unescaped | ConvertFrom-Json; $text = $unescaped } catch { }
  Write-Text $p $text
}
$repoRoot = Split-Path $PSScriptRoot -Parent
$dshHome = Join-Path $env:USERPROFILE '.dsh'
$profileDir = Join-Path $dshHome "profiles\$Profile"
$pluginSrc = Join-Path $PSScriptRoot 'dsh-mobile-access'
$pluginDst = Join-Path $dshHome 'local-plugins\dsh-mobile-access'
# [P1 修复] 插件目录的备份统一放这里，**绝不放 local-plugins\**：
# 那个目录会被 DSH 当插件来源扫描，里面留一份 dsh-mobile-access.bak-install-*（package.json
# 的 name 同样是 dsh-mobile-access）就会变成"两个插件"，用户还可能看到旧版面板。
$backupDir = Join-Path $dshHome 'backups'
$apkSrc = Join-Path $repoRoot 'dist\dsh-mobile.apk'
$pluginName = 'dsh-mobile-access'
$gatewayName = 'dsh-plugin-mobile-gateway'
$gatewayVersion = '0.9.0'

function Info($m) { Write-Host "  $m" }
function Ok($m) { Write-Host "  OK  $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  !   $m" -ForegroundColor Yellow }

Write-Host "`n=== DSH 掌上通 · PC 插件安装 ===" -ForegroundColor Cyan
Info "仓库     : $repoRoot"
Info "profile  : $Profile  ($profileDir)"

if (-not (Test-Path $pluginSrc)) { throw "找不到插件源码：$pluginSrc" }
if (-not (Test-Path $profileDir)) { throw "找不到 profile 目录：$profileDir（profile 名写对了吗？）" }

# ---------------------------------------------------------------- 1. 复制插件
Write-Host "`n[1/4] 复制插件到 local-plugins" -ForegroundColor Cyan
# 先把 local-plugins\ 里所有 dsh-mobile-access* 残留（旧的正式目录 + 历史 .bak-install-*）
# 统一挪到 ~/.dsh/backups/。为什么必须挪走而不是原地改名：
# 原地改名后目录里的 package.json 仍然写着 name = dsh-mobile-access，DSH 扫描时
# 会把它当成第二个同名插件 → 用户看到"两个插件"，甚至渲染的是旧版面板。
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$pluginRoot = Split-Path $pluginDst -Parent
New-Item -ItemType Directory -Force -Path $pluginRoot | Out-Null
$stale = @(Get-ChildItem -Path $pluginRoot -Directory -Filter "$pluginName*" -ErrorAction SilentlyContinue)
foreach ($d in $stale) {
  $dest = Join-Path $backupDir ($d.Name + '.' + (Get-Date -Format yyyyMMdd-HHmmss))
  Move-Item $d.FullName $dest
  Warn "移走同名残留：local-plugins\$($d.Name)  ->  $dest"
}
if ($stale.Count -eq 0) { Info "local-plugins 下没有同名残留" }
Copy-Item $pluginSrc $pluginDst -Recurse -Force
Ok "已复制 -> $pluginDst"

# ---------------------------------------------------------------- 2. 放安装包
Write-Host "`n[2/4] 放入手机安装包（面板用于扫码下载）" -ForegroundColor Cyan
if (Test-Path $apkSrc) {
  $appDir = Join-Path $pluginDst 'app'
  New-Item -ItemType Directory -Force -Path $appDir | Out-Null
  Copy-Item $apkSrc (Join-Path $appDir 'dsh-mobile.apk') -Force
  Ok ("已放入 app\dsh-mobile.apk（{0:N1} KB）" -f ((Get-Item $apkSrc).Length / 1KB))
  # version.txt 必须与这个 APK 一致：面板「安装包信息」显示它，别再出现
  # 「文件是 0.81 的包、版本号写着 0.8」这种对不上的情况。
  # 以 AndroidManifest 的 versionName 为准（build.ps1 就是照它打的包）；
  # 读不到再退到 dist\version.json。注意 version.json 只有跑 release.ps1 才会更新，
  # 所以它可能落后于工作区里刚构建出来的包，只作兜底。
  $verName = ''
  $manifestPath = Join-Path $repoRoot 'AndroidManifest.xml'
  if (Test-Path $manifestPath) {
    $mName = [regex]::Match((Read-Text $manifestPath), 'android:versionName="([^"]+)"')
    if ($mName.Success) { $verName = $mName.Groups[1].Value.Trim() }
  }
  if (-not $verName) {
    $verJson = Join-Path $repoRoot 'dist\version.json'
    if (Test-Path $verJson) {
      try { $verName = (Read-Text $verJson | ConvertFrom-Json).versionName } catch { }
    }
  }
  if ($verName) {
    Write-Text (Join-Path $appDir 'version.txt') $verName
    Ok "app\version.txt = $verName（与 APK 的 versionName 一致）"
  } else {
    Warn "没能读到 versionName，app\version.txt 未更新（面板会显示成兜底版本 0.2）"
  }
} else {
  Warn "仓库里没有 dist\dsh-mobile.apk，先跑一次 .\build.ps1 再来；面板会显示「没有安装包」"
}

# ---------------------------------------------------------------- 3. 登记 profile
Write-Host "`n[3/4] 登记到 profile 的 package.json" -ForegroundColor Cyan
$pkgPath = Join-Path $profileDir 'package.json'
Copy-Item $pkgPath "$pkgPath.bak-install" -Force
$pkg = Read-Text $pkgPath | ConvertFrom-Json

# [2026-10-20 整合] 协议层已 vendor 进本插件，不再需要第三方包 dsh-plugin-mobile-gateway。
# 老机器上还残留着这条依赖 + 它的 node_modules，这里顺手摘掉（否则插件列表里仍会有第二个条目，
# 而且 DSH 还会继续把它当独立插件加载一遍）。
if ($pkg.dependencies.PSObject.Properties[$gatewayName]) {
  $pkg.dependencies.PSObject.Properties.Remove($gatewayName)
  Ok "依赖 - $gatewayName（协议层已内置在本插件 net）"
} else {
  Info "已无 $gatewayName 依赖（已是整合版）"
}
$linkTarget = 'link:' + ($pluginDst -replace '\\', '/')
if (-not $pkg.dependencies.PSObject.Properties[$pluginName]) {
  $pkg.dependencies | Add-Member -NotePropertyName $pluginName -NotePropertyValue $linkTarget
  Ok "依赖 + $pluginName = $linkTarget"
} else {
  if ($pkg.dependencies.$pluginName -ne $linkTarget) {
    $pkg.dependencies.$pluginName = $linkTarget
    Ok "依赖 $pluginName 指向已更新"
  } else { Info "依赖已有 $pluginName" }
}

# 只把本插件登记为 bundle：dsh-plugin-mobile-gateway 现在由本插件自己的
# cordis.patch.yml 挂载（id: mobile-gateway），这里不再把它塞进 bundles；
# 已经装过旧版的要把残留项摘掉，否则同一个行会被两个 bundle 层各插一次。
#
# [P1 修复] 追加前**先查重**：同一项在 bundles 里出现两次，DSH 会把同一个插件加载两遍
# （本机实测过 "dsh-mobile-access" 出现 2 次的报告）。这里顺手做归一化（去空白、丢空项）
# 并对整表去重，把已经存在的重复项一起修掉。
$bundles = @($pkg.dsh.profile.bundles | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
$beforeCount = $bundles.Count
$bundles = @($bundles | Select-Object -Unique)
if ($bundles.Count -lt $beforeCount) { Warn "bundles 里有 $($beforeCount - $bundles.Count) 个重复项，已去掉" }
if ($bundles -notcontains $pluginName) { $bundles += $pluginName; Ok "bundles + $pluginName" }
else { Info "bundles 已有 $pluginName（查重通过，不重复追加）" }
if ($bundles -contains $gatewayName) {
  $bundles = @($bundles | Where-Object { $_ -ne $gatewayName })
  Ok "bundles - $gatewayName（改由本插件的 patch 挂载，避免重复行）"
}
$pkg.dsh.profile.bundles = @($bundles)
Write-JsonFile $pkgPath $pkg
Ok "package.json 已更新（备份：package.json.bak-install）"
# 落盘后复核：只看"没报错"不算验证 —— bundles 写坏了会直接把 DSH 卡在启动
$verify = @((Read-Text $pkgPath | ConvertFrom-Json).dsh.profile.bundles)
$dup = @($verify | Group-Object | Where-Object { $_.Count -gt 1 })
if ($dup.Count -gt 0) { throw "package.json 里 bundles 仍有重复项：$($dup.Name -join ', ')" }
if (@($verify | Where-Object { $_ -eq $pluginName }).Count -ne 1) {
  throw "package.json 里 $pluginName 在 bundles 中出现次数不是 1"
}
Ok "复核通过：bundles 无重复，$pluginName 恰好 1 次（共 $($verify.Count) 项）"

# ---------------------------------------------------------------- 4. 补网关配置
Write-Host "`n[4/4] 补 mobile-gateway 配置（lanPort 3091）" -ForegroundColor Cyan
$patchPath = Join-Path $profileDir 'cordis.patch.yml'
$patch = Read-Text $patchPath
if ($patch -match 'id:\s*mobile-gateway') {
  Info "cordis.patch.yml 已有 mobile-gateway 配置，跳过"
} else {
  Copy-Item $patchPath "$patchPath.bak-install" -Force
  $block = @'

# == DSH 掌上通：手机端网关（由 pc-plugin/install.ps1 追加）==
# lanPort 用 3091，避开 dsh-pocket 默认占用的 3081。
- id: mobile-gateway
  config:
    path: /ws/mobile
    requireAuth: true
    gatewayEnabled: true
    gatewayMode: persistent
    adminLoopbackOnly: true
    pairingTtlMs: 300000
    lanEnabled: true
    lanHost: 0.0.0.0
    lanPort: 3091
'@
  Write-Text $patchPath ($patch + $block)
  Ok "cordis.patch.yml 已追加配置（备份：cordis.patch.yml.bak-install）"
}

# ---------------------------------------------------------------- 附：local-plugins 残留体检
# 「两个插件 / 老界面」的根因就是这里留着 dsh-mobile-access.bak-install-*。
# 本脚本 [1/4] 已经自动把它们挪走了；这一段是落盘后的复核 + 手动清理指引。
Write-Host "`n[附] 检查 local-plugins 下的同名残留" -ForegroundColor Cyan
$leftover = @(Get-ChildItem -Path $pluginRoot -Directory -Filter "$pluginName*" -ErrorAction SilentlyContinue |
              Where-Object { $_.FullName -ne $pluginDst })
if ($leftover.Count -eq 0) {
  Ok "local-plugins 下只剩一个 $pluginName（没有 .bak-install-* 残留）"
} else {
  Warn "local-plugins 下仍有同名目录：$($leftover.Name -join ', ')"
  Warn "  手动清理（挪到备份目录，不要直接删）："
  foreach ($d in $leftover) {
    Warn "    Move-Item '$($d.FullName)' '$backupDir\$($d.Name).manual'"
  }
}

# ---------------------------------------------------------------- 迁移：摘掉第三方网关包的行与残留
# [2026-10-20 整合] 协议层已 vendor 进本插件 net。老机器上还留着
# ① profile 的 cordis.patch.yml 里 `- id: mobile-gateway` 配置块；② node_modules 里的
# dsh-plugin-mobile-gateway 目录。前者会让插件列表继续出现第二个条目（行还在就还会挂载），
# 所以必须删；后者已经没有任何引用，留着不影响，但要提醒可以清。
Write-Host "`n[迁移] 摘掉旧的 mobile-gateway 组合行" -ForegroundColor Cyan
$patchPath = Join-Path $profileDir 'cordis.patch.yml'
if (Test-Path $patchPath) {
  $patchText = Read-Text $patchPath
  if ($patchText -match '(?m)^\s*-\s*id:\s*mobile-gateway\s*$') {
    Copy-Item $patchPath "$patchPath.bak-merge" -Force
    $lines = $patchText -split "`n"
    $start = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
      if ($lines[$i] -match '^\s*-\s*id:\s*mobile-gateway\s*$') { $start = $i; break }
    }
    if ($start -ge 0) {
      # 往前吞掉本脚本当年追加的注释头（# == DSH 掌上通… / # lanPort 用 3091…）
      $s = $start
      while ($s - 1 -ge 0 -and $lines[$s - 1] -match '^\s*#') { $s-- }
      # 往后吞掉该 list item 的所有缩进行与空行
      $e = $start
      while ($e + 1 -lt $lines.Count -and ($lines[$e + 1] -match '^\s+\S' -or $lines[$e + 1].Trim() -eq '')) { $e++ }
      $kept = @()
      for ($i = 0; $i -lt $lines.Count; $i++) { if ($i -lt $s -or $i -gt $e) { $kept += $lines[$i] } }
      $newText = ($kept -join "`n")
      Write-Text $patchPath $newText
      $after = Read-Text $patchPath
      if ($after -match '(?m)^\s*-\s*id:\s*mobile-gateway\s*$') { throw "迁移失败：cordis.patch.yml 里仍有 mobile-gateway 行" }
      Ok "cordis.patch.yml 已移除 mobile-gateway 行（备份：cordis.patch.yml.bak-merge）"
    }
  } else {
    Info "cordis.patch.yml 里没有 mobile-gateway 行（已是整合版）"
  }
}

$gwNodeModules = Join-Path $profileDir 'node_modules\dsh-plugin-mobile-gateway'
if (Test-Path $gwNodeModules) {
  Warn "node_modules 里还留着旧的 dsh-plugin-mobile-gateway 目录（已无引用，留着不影响）"
  Warn "  想清理可以：Move-Item '$gwNodeModules' '$backupDir\dsh-plugin-mobile-gateway.merged'"
}

# ---------------------------------------------------------------- 验收：现在真的只有一个插件
Write-Host "`n[验收] 单插件整合是否到位" -ForegroundColor Cyan
$fails = @()
$vendorIndex = Join-Path $pluginDst 'net\index.mjs'
$vendorAdapter = Join-Path $pluginDst 'net\dsh-host-adapter.mjs'
$clientFile = Join-Path $pluginDst 'client.js'
$checks = @(
  @{ Name = '内置协议层（net/index.mjs）'; Ok = (Test-Path $vendorIndex) },
  @{ Name = 'Connection Peer 已并入 net 基线';      Ok = ((Test-Path $vendorIndex) -and ((Read-Text $vendorIndex) -match 'createPeer')) },
  @{ Name = 'adapter 全部出口透传 peer';               Ok = ((Test-Path $vendorAdapter) -and ((Read-Text $vendorAdapter) -match 'local patch: DSH 0\.2\.1-rc\.2')) },
  @{ Name = 'client 是合并版（含面板 + 手机接入）';     Ok = ((Test-Path $clientFile) -and ((Read-Text $clientFile) -match 'settings\.general\.item') -and ((Read-Text $clientFile) -match 'sidebar\.footer\.action')) },
  @{ Name = 'profile 不再依赖第三方网关包';            Ok = (-not ((Read-Text $pkgPath | ConvertFrom-Json).dependencies.PSObject.Properties['dsh-plugin-mobile-gateway'])) },
  @{ Name = 'cordis.patch.yml 里没有 mobile-gateway 行'; Ok = (-not ((Read-Text $patchPath) -match '(?m)^\s*-\s*id:\s*mobile-gateway\s*$')) }
)
foreach ($c in $checks) {
  if ($c.Ok) { Write-Host "  OK  $($c.Name)" } else { Write-Host "  缺失  $($c.Name)" -ForegroundColor Yellow; $fails += $c.Name }
}
if ($fails.Count -gt 0) {
  Write-Host ""
  Write-Host "  !! $($fails.Count) 项没到位：$($fails -join '；')" -ForegroundColor Red
  Write-Host "  常见原因：插件目录没复制全（net/ 被漏掉）或 profile 补丁没写回。重跑一次本脚本再看。" -ForegroundColor Yellow
} else {
  Write-Host "  整合完成：一个插件、一条补丁行、协议层内置。" -ForegroundColor Green
}

# ---------------------------------------------------------------- 完成
Write-Host "`n=== 安装完成 ===" -ForegroundColor Cyan
Write-Host @"
下一步：
  1. 重启 DSH 桌面版（panel/宿主代码都是启动时加载的）
  2. 点左侧边栏底部的「移动设备」按钮 —— PC 端只有这一个入口
       弹窗第一屏就是「手机接入」卡片：
         · 下载 App        —— 手机连同一 WiFi 扫码即可下载安装 APK
         · 生成公网二维码   —— 出门在外用（临时域名，电脑重启后会变）
         · 生成内网二维码   —— 在家用
       网关开关 / 公网隧道 / 已配对设备在卡片里的「高级设置」中
  3. 插件列表里现在**只有一个** dsh-mobile-access（协议层已内置，不再是第二个插件）
  4. 若曾经看到"两个插件 / 老界面"：那是 local-plugins 里留了 .bak-install-* 残留。
     本脚本 [1/4] 已自动挪到 $backupDir；想手动再清一遍就照上面打印的命令做
     （把 <残留目录名> 换成实际名字，挪走而不是删除）
"@

