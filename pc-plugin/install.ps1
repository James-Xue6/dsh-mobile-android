<#
安装 DSH 掌上通 的 PC 端插件（dsh-mobile-access）。

用法（在仓库根目录执行）：
    pwsh -File .\pc-plugin\install.ps1
    pwsh -File .\pc-plugin\install.ps1 -Profile desktop     # 指定 profile

它会做四件事：
 1. 把 pc-plugin\dsh-mobile-access 复制到 ~/.dsh/local-plugins/dsh-mobile-access（旧目录先备份）
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
$repoRoot = Split-Path $PSScriptRoot -Parent
$dshHome = Join-Path $env:USERPROFILE '.dsh'
$profileDir = Join-Path $dshHome "profiles\$Profile"
$pluginSrc = Join-Path $PSScriptRoot 'dsh-mobile-access'
$pluginDst = Join-Path $dshHome 'local-plugins\dsh-mobile-access'
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
if (Test-Path $pluginDst) {
  $bak = "$pluginDst.bak-install-$(Get-Date -Format yyyyMMdd-HHmmss)"
  Move-Item $pluginDst $bak
  Warn "已存在旧插件，备份为：$bak"
}
New-Item -ItemType Directory -Force -Path (Split-Path $pluginDst -Parent) | Out-Null
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
    $mName = [regex]::Match((Get-Content $manifestPath -Raw), 'android:versionName="([^"]+)"')
    if ($mName.Success) { $verName = $mName.Groups[1].Value.Trim() }
  }
  if (-not $verName) {
    $verJson = Join-Path $repoRoot 'dist\version.json'
    if (Test-Path $verJson) {
      try { $verName = (Get-Content $verJson -Raw | ConvertFrom-Json).versionName } catch { }
    }
  }
  if ($verName) {
    Set-Content -Path (Join-Path $appDir 'version.txt') -Value $verName -Encoding utf8 -NoNewline
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
$pkg = Get-Content $pkgPath -Raw | ConvertFrom-Json

if (-not $pkg.dependencies.PSObject.Properties[$gatewayName]) {
  $pkg.dependencies | Add-Member -NotePropertyName $gatewayName -NotePropertyValue $gatewayVersion
  Ok "依赖 + $gatewayName = $gatewayVersion"
} else {
  Info "依赖已有 $gatewayName"
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
$bundles = @($pkg.dsh.profile.bundles)
if ($bundles -notcontains $pluginName) { $bundles += $pluginName; Ok "bundles + $pluginName" }
else { Info "bundles 已有 $pluginName" }
if ($bundles -contains $gatewayName) {
  $bundles = @($bundles | Where-Object { $_ -ne $gatewayName })
  Ok "bundles - $gatewayName（改由本插件的 patch 挂载，避免重复行）"
}
$pkg.dsh.profile.bundles = $bundles
$pkg | ConvertTo-Json -Depth 20 | Set-Content $pkgPath -Encoding utf8
Ok "package.json 已更新（备份：package.json.bak-install）"

# ---------------------------------------------------------------- 4. 补网关配置
Write-Host "`n[4/4] 补 mobile-gateway 配置（lanPort 3091）" -ForegroundColor Cyan
$patchPath = Join-Path $profileDir 'cordis.patch.yml'
$patch = Get-Content $patchPath -Raw
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
  Add-Content -Path $patchPath -Value $block -Encoding utf8
  Ok "cordis.patch.yml 已追加配置（备份：cordis.patch.yml.bak-install）"
}

# ---------------------------------------------------------------- 完成
Write-Host "`n=== 安装完成 ===" -ForegroundColor Cyan
Write-Host @"
下一步：
  1. 重启 DSH 桌面版（panel/宿主代码都是启动时加载的）
  2. 点左侧边栏底部的「移动设备」按钮 —— PC 端只有这一个入口
       抽屉第一屏就是「手机接入」卡片，只有三个按钮：
         · 下载 App        —— 手机连同一 WiFi 扫码即可下载安装 APK
         · 生成公网二维码   —— 出门在外用（临时域名，电脑重启后会变，需重新扫码）
         · 生成内网二维码   —— 在家用
       网关开关、公网隧道、已配对设备与吊销都在下面的「高级设置」里（默认收起）
       · 设置 → 通用 →「手机接入」现在只做跳转，不再重复一份功能
  3. 若面板提示网关不可用，确认 dsh-plugin-mobile-gateway 已装且已重启
"@

# ---------------------------------------------------------------- 附：网关面板补丁
# 「手机接入」并入「移动设备」这一步要改第三方网关包的 lib/client.js（升级会被覆盖），
# 所以走 pc-plugin\patches\ 下的「打补丁 + 可重放脚本」。脚本自带版本校验（0.9.0），
# 版本不符会报错退出而不是把插件降级 —— 这里只在文件已就位时顺手跑一次。
$gwClient = Join-Path $profileDir 'node_modules\dsh-plugin-mobile-gateway\lib\client.js'
$patchScript = Join-Path $PSScriptRoot 'patches\restore-gateway-panel-fix.ps1'
if (Test-Path $gwClient) {
  Write-Host "`n[附] 叠加网关面板补丁（下拉实底 + 手机接入三个按钮置顶 + 其余收进高级设置）" -ForegroundColor Cyan
  if (Test-Path $patchScript) {
    try { & $patchScript } catch { Warn "补丁未应用：$($_.Exception.Message)" }
  } else { Warn "找不到 $patchScript" }
} else {
  Warn "网关插件还没落盘（$gwClient 不存在）：先让 DSH 装好 dsh-plugin-mobile-gateway，再手动跑一次 pwsh -File .\pc-plugin\patches\restore-gateway-panel-fix.ps1"
}
