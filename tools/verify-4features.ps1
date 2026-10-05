# =====================================================================
#  DSH 掌上通 · 四项优化 UI 一键验收（2026-10-05）
#
#  为什么有这个脚本：②③④ 是"点上去才看得见"的交互，只能真机/模拟器验。
#  它把"逐项点一遍 + 存证据"固化下来，设备一到位就能一条命令出结论。
#
#  前置：设备已连上 adb，且 App **已配对**（没配对先跑 tools\accept-pairing.ps1）。
#
#  用法（仓库根目录）：
#    pwsh -File tools\verify-4features.ps1
#    pwsh -File tools\verify-4features.ps1 -Serial emulator-5554
#    pwsh -File tools\verify-4features.ps1 -SkipInstall
#
#  输出：evidence\ui-4features-<时间戳>\ 下的 uiautomator XML + 一张结论表；
#        退出码 0 = 四项都看到预期控件；1 = 有项没看到（脚本会打印每项的实际界面片段）。
#
#  说明：本脚本**只做"看到了什么"的判定**，不做"行为是否正确"的判断 ——
#        例如它确认「文件/相册/拍照」三项出现在面板上，但不替你判断点「相册」后
#        选中的图有没有正确暂存（那要看 UI 里有没有出现附件条，本脚本也查）。
# =====================================================================
[CmdletBinding()]
param(
  [string]$Serial = '',
  [string]$Apk = '',
  [string]$SdkRoot = '',
  [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $here

if ([string]::IsNullOrWhiteSpace($SdkRoot)) {
  $SdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { 'F:\AI\程序开发\android-sdk' }
}
$adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "找不到 adb：$adb" }
if ([string]::IsNullOrWhiteSpace($Apk)) { $Apk = Join-Path $root 'dist\dsh-mobile.apk' }

$devArg = @()
if ($Serial) { $devArg = @('-s', $Serial) }
function Adb([string[]]$a) { & $adb @devArg @a }

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$ev = Join-Path $root "evidence\ui-4features-$stamp"
New-Item -ItemType Directory -Force -Path $ev | Out-Null

function Say($m)  { Write-Host $m }
function Step($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }
function Pass($m) { Write-Host "  ✅ $m" -ForegroundColor Green }
function Fail($m) { Write-Host "  ❌ $m" -ForegroundColor Red }

$script:fail = 0
function Check($what, $cond, $detail) {
  if ($cond) { Pass "$what$(if($detail){" → $detail"})" }
  else { Fail "$what$(if($detail){" → $detail"})"; $script:fail++ }
}

# ---- UI 工具 --------------------------------------------------------
function Dump-Ui([string]$name) {
  Adb @('shell', 'rm', '-f', '/sdcard/ui.xml') | Out-Null
  Adb @('shell', 'uiautomator', 'dump', '/sdcard/ui.xml') | Out-Null
  $xml = (Adb @('shell', 'cat', '/sdcard/ui.xml')) -join ''
  if ($name) { Set-Content -Path (Join-Path $ev "$name.xml") -Value $xml -Encoding utf8 }
  return $xml
}
function Find-Center([string]$xml, [string]$text) {
  $m = [regex]::Match($xml, 'text="' + [regex]::Escape($text) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
  if (-not $m.Success) { return $null }
  return @([int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2),
           [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2))
}
function Has-Text([string]$xml, [string]$text) { return $xml -match ('text="' + [regex]::Escape($text) + '"') }
# 包含匹配：节点文本只是"含有"这段字（例如输入框提示实际是「给 Agent 派个任务…」带省略号，
# 用精确匹配会判失败）。注意：**不要**用它去判 chip / 选项 —— 会话正文里也可能出现这些词，
# 会假阳性；那些地方一律用精确匹配 Has-Text。
function Has-Contains([string]$xml, [string]$text) { return $xml -match ('text="[^"]*' + [regex]::Escape($text) + '[^"]*"') }
function Tap-Text([string]$xml, [string]$text) {
  $c = Find-Center $xml $text
  if (-not $c) { return $false }
  Adb @('shell', 'input', 'tap', $c[0], $c[1]) | Out-Null
  return $true
}
function Back { Adb @('shell', 'input', 'keyevent', '4') | Out-Null; Start-Sleep -Milliseconds 800 }

# ---- 0. 设备与安装 ---------------------------------------------------
Step "0/7 设备与安装"
$devs = (Adb @('devices')) -join "`n"
Say ($devs.Trim())
$online = [regex]::Matches($devs, '(\S+)\s+device\b')
if ($online.Count -eq 0) { throw "没有 online 的设备（只有 offline 或空）。模拟器没起完 / 真机没授权 USB 调试。" }
Say ("online 设备数 = {0}" -f $online.Count)

if ($SkipInstall) {
  Say "跳过安装（-SkipInstall）"
} else {
  if (-not (Test-Path $Apk)) { throw "找不到 APK：$Apk" }
  $fi = Get-Item $Apk
  Say ("安装 {0}（{1} KB）…" -f $fi.Name, [int]($fi.Length / 1KB))
  $out = (Adb @('install', '-r', $Apk)) -join ' '
  if ($out -match 'Success') { Pass "安装成功" } else { Fail "安装失败：$out"; throw "安装失败" }
  # 回读版本号，确认装的是新包
  $vi = (Adb @('shell', 'dumpsys', 'package', 'com.dsh.mobile')) -join "`n"
  $vm = [regex]::Match($vi, 'versionName=(\S+)')
  if ($vm.Success) { Say ("设备上 versionName = {0}" -f $vm.Groups[1].Value) }
}

# ---- 1. 打开 App 并进对话页 -----------------------------------------
Step "1/7 打开 App"
Adb @('shell', 'am', 'force-stop', 'com.dsh.mobile') | Out-Null
Start-Sleep -Milliseconds 600
Adb @('shell', 'am', 'start', '-n', 'com.dsh.mobile/.MainActivity') | Out-Null
Start-Sleep -Seconds 4

$xml = Dump-Ui '01-launch'
# 冷启动可能落在**设备页**：实测设备在线时也不会自动跳对话页，页面上有个「进入对话」按钮。
# 这里最多重试 3 次把它点进去；点不动就报错退出，让调用方分清"没进对话页"和"功能没做"。
for ($try = 0; $try -lt 3; $try++) {
  if (Has-Contains $xml '给 Agent 派个任务') { break }
  $tapped = $false
  foreach ($t in @('进入对话', '打开', '开始对话', '进入')) {
    if (Has-Text $xml $t) { Tap-Text $xml $t | Out-Null; $tapped = $true; break }
  }
  if (-not $tapped) { break }
  Start-Sleep -Seconds 3
  $xml = Dump-Ui ("02-after-enter-$try")
}
if (Has-Contains $xml '给 Agent 派个任务') { Pass "已在对话页（看到输入框提示）" }
else {
  Fail "没进到对话页（没看到输入框提示「给 Agent 派个任务…」）"
  Say "  先把 App 配对好（tools\accept-pairing.ps1），或手动连一次；然后重跑本脚本。"
  Say "  当前界面已存到 $ev"
  exit 1
}

# ---- 2. 任务④ 权限 chip --------------------------------------------
Step "2/7 任务④：chip 行有没有「权限」"
Check "chip 行出现「权限」" (Has-Text $xml '权限') ""
Check "chip 行出现「生成物」" (Has-Text $xml '生成物') ""
if (Has-Text $xml '权限') {
  # 必须**即时重 dump**：chip 文案会随权限回帧变长（「权限」→「权限 · 完全访问」），
  # 旧 dump 的坐标会指到隔壁 chip（实测点歪到「模型」，弹出的是模型面板）。
  $fresh = Dump-Ui '03a-before-perm-tap'
  Tap-Text $fresh '权限' | Out-Null
  Start-Sleep -Seconds 2
  $perm = Dump-Ui '03-permission-sheet'
  Check "点「权限」弹出选择面板" (Has-Contains $perm '只影响这条对话') ""
  foreach ($v in @('read-only', 'workspace-write', 'danger-full-access')) {
    Check "面板里有 $v" (Has-Text $perm $v) ""
  }
  Back
}

# ---- 3. 任务② 加号三选项 + 附件条 -----------------------------------
Step "3/7 任务②：加号弹「文件 / 相册 / 拍照」"
$xml = Dump-Ui '04-chat'
# 加号在输入条最左侧；uiautomator 拿不到 contentDescription 时按坐标兜底
$tapPlus = $false
$m = [regex]::Match($xml, 'resource-id="[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
# 优先按描述找
foreach ($d in @('添加附件', '加号', '附件')) {
  $c = Find-Center $xml $d
  if ($c) { Adb @('shell', 'input', 'tap', $c[0], $c[1]) | Out-Null; $tapPlus = $true; break }
}
if (-not $tapPlus) {
  # 兜底：屏幕底部输入条左端（1080x2340 常见布局：x≈100, y≈屏高-150）
  $size = (Adb @('shell', 'wm', 'size')) -join ''
  $mm = [regex]::Match($size, '(\d+)x(\d+)')
  if ($mm.Success) {
    $w = [int]$mm.Groups[1].Value; $h = [int]$mm.Groups[2].Value
    Say ("  按坐标点加号（{0},{1}）" -f [int]($w * 0.09), ($h - 150))
    Adb @('shell', 'input', 'tap', [int]($w * 0.09), ($h - 150)) | Out-Null
    $tapPlus = $true
  }
}
Start-Sleep -Seconds 2
$att = Dump-Ui '05-attach-sheet'
Check "加号弹出面板里有「文件」" (Has-Text $att '文件') ""
Check "加号弹出面板里有「相册」" (Has-Text $att '相册') ""
Check "加号弹出面板里有「拍照」" (Has-Text $att '拍照') ""
Check "面板说明了「先暂存、一起发送」" ((Has-Contains $att '一起发送') -or (Has-Contains $att '先放在输入框上方')) ""
Back

# ---- 4. 任务① 生成物面板 -------------------------------------------
Step "4/7 任务①：点「生成物」出面板"
$xml = Dump-Ui '06-chat-again'
if (Has-Text $xml '生成物') {
  Tap-Text $xml '生成物' | Out-Null
  Start-Sleep -Seconds 3
  $art = Dump-Ui '07-artifact-panel'
  # [2026-10-06 需求修正] 生成物面板改成**只列本次产出文件**，不再列工作目录、没有目录下钻。
  $opened = (Has-Text $art '生成物') -and ((Has-Contains $art '本次对话产出的文件') -or (Has-Text $art '本次对话还没有产出文件'))
  Check "点「生成物」弹出面板（只列本次产出文件）" $opened ""
  Check "面板里**没有**「← 返回上级」（目录浏览已删除）" (-not (Has-Contains $art '返回上级')) ""
  Check "面板里没有目录行（「文件夹 · 点一下进去」已删除）" (-not (Has-Contains $art '文件夹 · 点一下进去')) ""
  Check "面板有「关闭」" (Has-Text $art '关闭') ""
  Back
} else {
  Fail "对话页没有「生成物」chip"
}

# ---- 4.5 「用量」面板（2026-10-06 新增）-----------------------------
Step "5/7 用量：点「用量」出数据面板"
$xml = Dump-Ui '07b-chat-usage'
if (Has-Text $xml '用量') {
  Tap-Text $xml '用量' | Out-Null
  Start-Sleep -Seconds 3
  $u = Dump-Ui '07c-usage-panel'
  # 面板要么有数据（「缓存命中」/「合计」），要么是空态；两种情况都算"开了"
  $empty = Has-Contains $u '还没有用量'
  $openedU = (Has-Text $u '用量') -and ($empty -or ((Has-Contains $u '合计') -and (Has-Contains $u '缓存命中')))
  Check "点「用量」弹出面板" $openedU ""
  Check "面板有「合计」行" ($empty -or (Has-Contains $u '合计')) ""
  Check "面板有「缓存命中」行" ($empty -or (Has-Contains $u '缓存命中')) ""
  Check "面板有「输出」行" ($empty -or (Has-Contains $u '输出')) ""
  Check "面板有「关闭」" (Has-Text $u '关闭') ""
  Back
} else {
  Fail "对话页没有「用量」chip"
}

# ---- 5. 任务③ 消息文本可选中 ---------------------------------------
Step "6/7 任务③：消息文本点一下能不能出选区"
$xml = Dump-Ui '08-chat-before-tap'
# 找一条**真正的消息气泡**：避开顶部标题区（y<420）和底部 chip/输入区（y>1900），
# 并且排除 chip 文案本身 —— 否则会点到标题或 chip，测的就不是"消息文本能不能选"。
$mAll = [regex]::Matches($xml, 'text="([^"]{12,400})"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
$tapped = $false
foreach ($mm in $mAll) {
  $t = $mm.Groups[1].Value
  $y1 = [int]$mm.Groups[3].Value
  if ($y1 -lt 420 -or $y1 -gt 1900) { continue }
  if ($t -match '权限|生成物|模型|思考|用量|任务|项目|程序开发|Deepseek|给 Agent|发送|刷新|关闭|取消|添加附件') { continue }
  $x = [int](([int]$mm.Groups[2].Value + [int]$mm.Groups[4].Value) / 2)
  $y = [int](([int]$mm.Groups[3].Value + [int]$mm.Groups[5].Value) / 2)
  Say ("  点气泡文本「{0}」@ ({1},{2})" -f $t.Substring(0, [Math]::Min(20, $t.Length)), $x, $y)
  Adb @('shell', 'input', 'tap', $x, $y) | Out-Null
  $tapped = $true
  break
}
Start-Sleep -Seconds 2
$sel = Dump-Ui '09-after-tap'
if ($tapped) {
  # 选中后系统会弹 SelectionActionMode（含「复制」/「全选」），或至少出现选中手柄
  $hasCopy = (Has-Text $sel '复制') -or (Has-Text $sel '全选') -or (Has-Text $sel 'Copy')
  Check "单击后出现选区/复制工具条（或至少被选中）" $hasCopy "（不同 ROM 的浮动工具条行为可能不同）"
} else {
  Fail "没找到可点的消息气泡（会话里可能还没消息）"
}

# ---- 6. 结论 --------------------------------------------------------
Step "7/7 结论"
Say ("证据目录：{0}" -f $ev)
if ($script:fail -eq 0) {
  Write-Host "`n✅ 四项预期控件全部看到" -ForegroundColor Green
  exit 0
} else {
  Write-Host ("`n❌ 有 {0} 项没看到（见上面逐条 ❌；XML 已存证据目录，可逐个核对）" -f $script:fail) -ForegroundColor Red
  exit 1
}
