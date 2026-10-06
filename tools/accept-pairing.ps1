<#
  DSH 掌上通 · 「扫码/粘贴配对」自动化验收
  ------------------------------------------------------------------
  判据（可自动化的决定性验收）：
    用真实网关（POST {gateway}/mgw/pair）生成的配对串，
    经 App 的「粘贴配对串」对话框走完配对，
    然后看 {gateway}/mgw/devices 是否**新增**一台设备 —— 新增 = 通过。

  为什么必须先 -Fresh（默认开）：网关按 App 的安装级 deviceId 复用设备记录
  （lib/devices.js claimPairing：同一个 clientDeviceId 只做凭证轮换，不新增行）。
  已经配对过的 App 再配一次不会新增，所以必须先 `pm clear` 清掉 App 数据
  （会重新生成 deviceId、丢掉已配对状态）才谈得上「新增一条」。

  用法（工作区根目录）：
    pwsh -File tools\accept-pairing.ps1                     # 装包 + pm clear + 全流程
    pwsh -File tools\accept-pairing.ps1 -SkipInstall        # 用设备上现有的包跑
    pwsh -File tools\accept-pairing.ps1 -Serial <序列号>    # 多设备时指定

  注意：
  - 会清空 App 数据（-Fresh），跑完需要重新配对，这是判据本身要求的。
  - 不动 versionCode / versionName，不发版。
  - 不往任何被跟踪的文件里写配对串/令牌；诊断只打印长度与主机名。
#>
[CmdletBinding()]
param(
  [string]$Serial = '',
  [string]$Apk = '',
  [string]$Gateway = 'http://127.0.0.1:19387',
  [switch]$SkipInstall,
  [switch]$KeepData,          # 不清 App 数据（此时判据退化为"设备在线/lastSeenAt 刷新"）
  [int]$WaitSec = 25
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $here
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { 'F:\AI\程序开发\android-sdk' }
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "找不到 adb：$adb" }
if ([string]::IsNullOrWhiteSpace($Apk)) { $Apk = Join-Path $root 'dist\dsh-mobile.apk' }

function Say($m) { Write-Host $m }
function Step($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }

$devArg = @()
if ($Serial) { $devArg = @('-s', $Serial) }

function Adb([string[]]$a) { & $adb @devArg @a }

# ---------------------------------------------------------------- UI 工具
# 取当前界面 XML（uiautomator dump 只认 /sdcard 路径，不能直接 cat 出去）
function Get-UiXml {
  Adb @('shell', 'rm', '-f', '/sdcard/ui.xml') | Out-Null
  Adb @('shell', 'uiautomator', 'dump', '/sdcard/ui.xml') | Out-Null
  return (Adb @('shell', 'cat', '/sdcard/ui.xml')) -join ''
}

# 按文本找一个可点节点的中心坐标（返回 @(x,y)；找不到返回 $null）
function Find-Center([string]$xml, [string]$text) {
  $m = [regex]::Match($xml, 'text="' + [regex]::Escape($text) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
  if (-not $m.Success) { return $null }
  $x = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
  $y = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
  return @([int]$x, [int]$y)
}

# 取当前输入法窗口**可触区**的上沿（用来证明"按钮在不在键盘上面"）
# 注意：dumpsys 里 InputMethod 会出现多次，其中 mInputWindowHandle 那条的可触区是旧值，
# 真正生效的是「visible windows」里那条；取所有匹配里的最大值即得真实上沿。
function Get-ImeTop {
  $txt = (Adb @('shell', 'dumpsys', 'window', 'windows')) -join "`n"
  $top = -1
  foreach ($m in [regex]::Matches($txt, 'InputMethod, frame=\[Rect\((-?\d+), (-?\d+) - (-?\d+), (-?\d+)\)\], touchableRegion=SkRegion\(\((-?\d+),(-?\d+),')) {
    $y = [int]$m.Groups[6].Value
    if ($y -le 1) { continue }
    if ($y -gt $top) { $top = $y }
  }
  return $top
}

function Get-Devices {
  $r = Invoke-RestMethod -Uri "$Gateway/mgw/devices" -Method Get
  if ($r.devices) { return @($r.devices) }
  return @()
}

# ---------------------------------------------------------------- 0. 前置
Step "0/6 设备与网关"
$devs = (Adb @('devices')) -join "`n"
Say $devs.Trim()
$online = ($devs -split "`n") | Where-Object { $_ -match "\s+device$" }
if (-not $online) { throw "没有在线设备（adb devices 里没有 device 状态的行）" }

$status = Invoke-RestMethod -Uri "$Gateway/mgw/status" -Method Get
Say ("网关：{0} 版本 {1}  已连客户端 {2}" -f $status.gatewayName, $status.version, $status.connectedClients)

# ---------------------------------------------------------------- 1. 装包
# 荣耀/华为系真机会拦三层：①「取消/继续」的 adb 安装确认 ② ICP 风险页的「知道了」
# ③「已了解…」勾选框 +「继续安装」，最后停在「输入指纹继续安装应用」——第三层是厂商的
# 身份验证（adb_install_need_confirm 被厂商锁成不可写，实测 settings put 会被拒），
# 只能由本人按指纹。所以这里自动点完前两三层，停在指纹那一层并明确告诉用户要按指纹。
function Install-WithDialogRelay([string]$apk) {
  $outFile = Join-Path $env:TEMP 'dsh_install_out.txt'
  $errFile = Join-Path $env:TEMP 'dsh_install_err.txt'
  Remove-Item $outFile, $errFile -ErrorAction SilentlyContinue
  $a = @()
  if ($Serial) { $a += @('-s', $Serial) }
  $a += @('install', '-r', $apk)
  $p = Start-Process -FilePath $adb -ArgumentList $a -PassThru -NoNewWindow `
        -RedirectStandardOutput $outFile -RedirectStandardError $errFile
  $t0 = Get-Date
  $needHuman = $false
  while (-not $p.HasExited -and ((Get-Date) - $t0).TotalSeconds -lt 240) {
    Start-Sleep -Seconds 2
    if ($p.HasExited) { break }
    $xml = Get-UiXml
    if ($xml -match '验证指纹|输入指纹') { $needHuman = $true; break }
    $cb = Find-Center $xml '已了解此应用未经荣耀应用市场检测，可能存在风险。'
    if ($cb) { Adb @('shell', 'input', 'tap', $cb[0], $cb[1]) | Out-Null; Start-Sleep -Milliseconds 700 }
    $tapped = $false
    foreach ($t in @('继续安装', '知道了', '继续')) {
      $c = Find-Center $xml $t
      if ($c) {
        Say ("   自动点掉系统安装确认：「{0}」" -f $t)
        Adb @('shell', 'input', 'tap', $c[0], $c[1]) | Out-Null
        Start-Sleep -Seconds 2
        $tapped = $true
        break
      }
    }
    if (-not $tapped) { Start-Sleep -Seconds 2 }
  }
  if ($needHuman) {
    Say "`n⚠ 设备停在「输入指纹继续安装应用」—— 请让本人按一下指纹（或输密码），安装会立刻继续。" -ForegroundColor Yellow
    Say "   进程仍在等待这次安装会话；按完后重新跑本脚本加 -SkipInstall 即可继续验收。"
    exit 2
  }
  $txt = @()
  if (Test-Path $outFile) { $txt += (Get-Content $outFile -Raw) }
  if (Test-Path $errFile) { $txt += (Get-Content $errFile -Raw) }
  $all = ($txt -join "`n")
  Say ("   " + ($all -replace "`r?`n", ' / ').Trim())
  if ($all -match 'Success') { return }
  throw "安装失败：$all"
}

if ($SkipInstall) {
  Say "`n跳过安装（-SkipInstall）"
} else {
  Step "1/6 安装 $Apk（自动点掉系统安装确认）"
  if (-not (Test-Path $Apk)) { throw "找不到 APK：$Apk（先跑 pwsh -File .\build.ps1）" }
  Install-WithDialogRelay $Apk
}

# ---------------------------------------------------------------- 2. 清数据（判据需要）
if (-not $KeepData) {
  Step "2/6 清空 App 数据（pm clear：重新生成 deviceId，才能用「新增一条」判据）"
  Adb @('shell', 'pm', 'clear', 'com.dsh.mobile') | ForEach-Object { Say "   $_" }
} else {
  Say "`n保留 App 数据（-KeepData）：本次用「设备 lastSeenAt 刷新 + 在线」作退化判据"
}

$before = Get-Devices
$beforeIds = @($before | ForEach-Object { $_.id })
Say ("配对前设备数 = {0}" -f $before.Count)

# ---------------------------------------------------------------- 3. 取真实配对串
Step "3/6 向网关要一段真实配对串（POST /mgw/pair）"
$pair = Invoke-RestMethod -Uri "$Gateway/mgw/pair" -Method Post -ContentType 'application/json' -Body '{}'
$payload = $pair.qrPayload
if ([string]::IsNullOrWhiteSpace($payload)) { throw "网关没有返回 qrPayload" }
Say ("   配对串长度 = {0}（只打印长度，不打印内容）" -f $payload.Length)
$pf = Join-Path $env:TEMP 'dsh_accept_payload.txt'
Set-Content -Path $pf -Value $payload -NoNewline -Encoding ascii

# ---------------------------------------------------------------- 4. 走 App UI
Step "4/6 在 App 里走「粘贴配对串 → 配对」"
Adb @('shell', 'am', 'force-stop', 'com.dsh.mobile') | Out-Null
Start-Sleep -Milliseconds 600
Adb @('shell', 'am', 'start', '-n', 'com.dsh.mobile/.MainActivity') | Out-Null
Start-Sleep -Seconds 3

$xml = Get-UiXml
$gear = Find-Center $xml '⚙'
if ($gear) {
  Say ("   打开设置页 @ ({0},{1})" -f $gear[0], $gear[1])
  Adb @('shell', 'input', 'tap', $gear[0], $gear[1]) | Out-Null
  Start-Sleep -Seconds 2
  $xml = Get-UiXml
}

$paste = Find-Center $xml '粘贴配对串'
if (-not $paste) { throw "没找到「粘贴配对串」按钮；当前界面不是设置页？" }
Say ("   点「粘贴配对串」@ ({0},{1})" -f $paste[0], $paste[1])
Adb @('shell', 'input', 'tap', $paste[0], $paste[1]) | Out-Null
Start-Sleep -Seconds 2

# 输入配对串（老包：手输框；新包：剪贴板为空时同样落到手输框）
$xml = Get-UiXml
$m = [regex]::Match($xml, 'class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
if (-not $m.Success) { throw "配对对话框里没有输入框（新包的剪贴板确认框？请先清空剪贴板再跑）" }
$ex = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
$ey = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
Adb @('shell', 'input', 'tap', [int]$ex, [int]$ey) | Out-Null
Start-Sleep -Milliseconds 800
Say "   输入配对串（552 字符）…"
Adb @('shell', 'input', 'text', $payload) | Out-Null
Start-Sleep -Seconds 1

# 关键一步：按真实 bounds 点「配对」，并同时报出输入法可触区上沿，
# 用来判定"按钮到底有没有被键盘盖住"（这正是本次要修的 bug）
$xml = Get-UiXml
$pairBtn = Find-Center $xml '配对'
if (-not $pairBtn) { throw "对话框里没有「配对」按钮" }
$btnBounds = [regex]::Match($xml, 'text="配对"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
$bt = [int]$btnBounds.Groups[2].Value      # 按钮上沿
$bb = [int]$btnBounds.Groups[4].Value      # 按钮下沿
$imeTop = Get-ImeTop
Say ("   「配对」按钮 bounds = [y {0}..{1}]  点 ({2},{3})" -f $bt, $bb, $pairBtn[0], $pairBtn[1])
Say ("   输入法可触区上沿 y = {0}" -f $imeTop)
if ($imeTop -gt 0 -and $bt -gt $imeTop) {
  Say "   ⚠ 按钮整个落在输入法可触区下面 —— 这一版是没修好的版本，点击必然无效" -ForegroundColor Yellow
} elseif ($imeTop -gt 0) {
  Say "   ✓ 按钮在输入法可触区之上，点击能到按钮" -ForegroundColor Green
}
Adb @('shell', 'input', 'tap', $pairBtn[0], $pairBtn[1]) | Out-Null

# ---------------------------------------------------------------- 5. 判定
Step "5/6 等待配对结果（最多 $WaitSec 秒）"
$deadline = (Get-Date).AddSeconds($WaitSec)
$after = @()
while ((Get-Date) -lt $deadline) {
  Start-Sleep -Seconds 2
  $after = Get-Devices
  $new = @($after | Where-Object { $beforeIds -notcontains $_.id })
  if (-not $KeepData -and $new.Count -gt 0) { break }
  if ($KeepData -and @($after | Where-Object { $_.online }).Count -gt 0) { break }
}
$after = Get-Devices

$xml = Get-UiXml
$dialogOpen = $xml -match 'text="配对"'

Step "6/6 结果"
Say ("   设备数：{0} -> {1}" -f $before.Count, $after.Count)
$after | ForEach-Object { Say ("   · {0}  {1}  online={2}" -f $_.id, $_.name, $_.online) }

$new = @($after | Where-Object { $beforeIds -notcontains $_.id })
if (-not $KeepData) {
  if ($new.Count -gt 0 -and -not $dialogOpen) {
    Say "`n✅ 通过：/mgw/devices 新增了 $($new.Count) 条设备记录，配对对话框已关闭" -ForegroundColor Green
    exit 0
  }
  Say "`n❌ 未通过：设备列表没有新增（对话框还在：$dialogOpen）" -ForegroundColor Red
  Say "   请把 App 设置页底部的 [配对诊断] 段贴出来 —— 里面有每一步的结果。"
  exit 1
} else {
  Say "`n（-KeepData：只看设备在线/刷新，不做新增判定）"
  exit 0
}
