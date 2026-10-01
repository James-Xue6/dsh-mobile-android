# 恢复 dsh-plugin-mobile-gateway 面板的「下拉弹出层透明穿透」修复
# 用法: pwsh -File restore-gateway-panel-fix.ps1
#
# 背景:
#   lib/client.js 的 styles.input 是 background:'transparent'；
#   <select> 复用它时，Chromium 的原生下拉弹出层没有实底，
#   下层面板文字会穿透上来（截图里表现为下拉列表半透明、文字叠加看不清）。
#
# 修法:
#   新增 styles.select / styles.option（实底 colors.panel + colorScheme:'dark'），
#   三处 <select>（网关运行模式 / 接入方式 / 配对连接方式）改用新样式，
#   并给全部 <option> 显式加 style。
#
# 注意: 本脚本是「整文件覆盖」。若网关插件升级过（版本变化），不要直接覆盖，
#       请按上面说明手动改，否则会把插件降级。先比对版本再决定。
$ErrorActionPreference = 'Stop'
$dst = Join-Path $env:USERPROFILE '.dsh\profiles\desktop\node_modules\dsh-plugin-mobile-gateway\lib\client.js'
$src = Join-Path $PSScriptRoot 'dsh-plugin-mobile-gateway.client.js.patched'
if (-not (Test-Path $dst)) { throw "找不到目标: $dst" }
if (-not (Test-Path $src)) { throw "找不到补丁文件: $src" }
$pkg = Join-Path (Split-Path (Split-Path $dst -Parent) -Parent) 'package.json'
if (Test-Path $pkg) {
  $ver = (Get-Content $pkg -Raw | ConvertFrom-Json).version
  Write-Host "当前网关插件版本: $ver"
  if ($ver -ne '0.9.0') { Write-Host "⚠ 版本不是 0.9.0，覆盖可能降级，请先手动比对！" -ForegroundColor Yellow }
}
Copy-Item $dst "$dst.bak-before-restore" -Force -ErrorAction SilentlyContinue
Copy-Item $src $dst -Force
Write-Host '已恢复面板下拉修复。重启 DSH 桌面版后生效。'
