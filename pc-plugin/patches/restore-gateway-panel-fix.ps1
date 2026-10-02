# 恢复 / 叠加 dsh-plugin-mobile-gateway 面板的两处修复
# 用法: pwsh -File restore-gateway-panel-fix.ps1
#
# 背景（两处修复叠在同一个补丁文件里）:
#
#  1) 下拉弹出层透明穿透
#     lib/client.js 的 styles.input 是 background:'transparent'；
#     <select> 复用它时，Chromium 的原生下拉弹出层没有实底，
#     下层面板文字会穿透上来（截图里表现为下拉列表半透明、文字叠加看不清）。
#     修法: 新增 styles.select / styles.option（实底 colors.panel + colorScheme:'dark'），
#     三处 <select>（网关运行模式 / 接入方式 / 配对连接方式）改用新样式，
#     并给全部 <option> 显式加 style。
#
#  2) 两个 PC 面板合并成一个入口（用户第 3 次反馈）
#     「手机接入」（dsh-mobile-access，挂在 设置→通用）与「移动设备」
#     （本插件，侧边栏底部按钮 + 抽屉）原本是两个入口，用起来要来回找。
#     修法: 本面板底部新增可展开的「手机接入」区块，内部直接渲染
#     dsh-mobile-access 通过 window.__DSH_MOBILE_ACCESS__ 暴露的主面板组件；
#     同时本插件把 window.__DSH_MOBILE_GATEWAY__.open() 暴露出去，
#     供「设置 → 通用 → 手机接入」那一行改成跳转。
#
#  3) 东西太多看不明白（用户第 4 次反馈）→ 面板重排为「三个按钮 + 一个高级区」
#     「配对二维码」与「安装包二维码」长得一样、上下堆了好几张码，
#     加上网关运行模式/设备鉴权/隧道状态/设备列表，打开抽屉根本不知道先点哪。
#     修法（本轮重设计，取代了上一轮「只加第 1/2 步徽标」的那版改动）:
#     · 抽屉最上方直接渲染「手机接入」主面板（dsh-mobile-access/client.js），
#       面板里只剩三件事：① 下载 App、② 生成公网二维码、③ 生成内网二维码，
#       二维码一律改用弹窗展示（局域网直发 / 公网镜像 作为弹窗里的两个标签）；
#     · 网关运行模式、设备鉴权、公网隧道、配对连接方式、已配对设备与吊销、
#       手动填地址全部收进默认收起的「高级设置（一般用不到）」；
#     · 上一轮加的「第一次使用 · 按两步走」引导卡与「第 1/2 步」徽标已删除
#       （顺序说明挪进主面板本身）。功能一个没删，只是默认不可见。
#
# 注意: 本脚本是「整文件覆盖」。网关插件升级过（版本变化）时不要覆盖，
#       否则会把插件降级 —— 所以下面**版本不符直接报错退出**，
#       需要时按上面的说明手动移植，或更新同目录下的 .patched 基线。
$ErrorActionPreference = 'Stop'
$expectedVersion = '0.9.0'
$dst = Join-Path $env:USERPROFILE '.dsh\profiles\desktop\node_modules\dsh-plugin-mobile-gateway\lib\client.js'
$src = Join-Path $PSScriptRoot 'dsh-plugin-mobile-gateway.client.js.patched'
if (-not (Test-Path $dst)) { throw "找不到目标: $dst" }
if (-not (Test-Path $src)) { throw "找不到补丁文件: $src" }

$pkg = Join-Path (Split-Path (Split-Path $dst -Parent) -Parent) 'package.json'
if (-not (Test-Path $pkg)) { throw "找不到网关插件的 package.json: $pkg" }
$ver = (Get-Content $pkg -Raw | ConvertFrom-Json).version
Write-Host "当前网关插件版本: $ver（本补丁基线: $expectedVersion）"
if ($ver -ne $expectedVersion) {
  throw "网关插件版本是 $ver，与本补丁基线 $expectedVersion 不符。整文件覆盖会降级插件。请先按本脚本头部注释手动比对/移植，或更新同目录的 .patched 基线后重跑。"
}

# 打补丁前的原文必须留一份可回退的备份（带时间戳，不覆盖历史备份）
$bak = "$dst.bak-$expectedVersion-$(Get-Date -Format yyyyMMdd-HHmmss)"
Copy-Item $dst $bak -Force
Write-Host "已备份原文件: $bak"

Copy-Item $src $dst -Force

# 落地后自检：两个修复的标记都必须在文件里，否则宁可不留下一个残缺的面板
$after = Get-Content $dst -Raw
$missing = @()
if ($after -notmatch 'styles\.select') { $missing += '下拉实底样式(styles.select)' }
if ($after -notmatch '__DSH_MOBILE_ACCESS__') { $missing += '手机接入跨 bundle 桥(__DSH_MOBILE_ACCESS__)' }
if ($after -notmatch '__DSH_MOBILE_GATEWAY__') { $missing += '面板打开桥(__DSH_MOBILE_GATEWAY__)' }
if ($after -notmatch '三个按钮：先下载 App，再扫码连接') { $missing += '主面板(三个按钮)说明' }
if ($after -notmatch '高级设置（一般用不到）') { $missing += '高级设置折叠区' }
if ($after -notmatch 'setAdvancedOpen') { $missing += '高级设置折叠状态(setAdvancedOpen)' }
if ($after -match '第一次使用 · 按两步走') { $missing += '残留的旧分步引导卡' }
if ($after -match 'accessOpen') { $missing += '残留的旧折叠状态(accessOpen)' }
if ($missing.Count -gt 0) { throw "覆盖后自检失败，缺: $($missing -join '、')。已备份的原文在 $bak，请手动恢复。" }

Write-Host '面板修复已就位（下拉实底 + 手机接入主面板置顶 + 其余收进高级设置）。重启 DSH 桌面版后生效。'
