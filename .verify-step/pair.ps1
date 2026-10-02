# 把真机重新配对到本机网关（不改任何被跟踪的文件；配对串只落临时文件，不落仓库）
# 用法: pwsh -File .verify-step\pair.ps1
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe'
$Gw  = 'http://127.0.0.1:19387'

function Ui {
  & $adb shell rm -f /sdcard/ui.xml | Out-Null
  for ($i = 0; $i -lt 6; $i++) {
    $o = & $adb shell uiautomator dump /sdcard/ui.xml 2>&1
    if ($o -match 'dumped') { break }
    Start-Sleep -Milliseconds 900
  }
  return ((& $adb shell cat /sdcard/ui.xml) -join '')
}
function Texts([string]$xml) {
  return (([regex]::Matches($xml, 'text="([^"]*)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne '' }) -join ' | ')
}
function Center([string]$xml, [string]$text) {
  $m = [regex]::Match($xml, 'text="' + [regex]::Escape($text) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
  if (-not $m.Success) { return $null }
  return @([int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2),
           [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2))
}
function Tap($p) { if ($p) { & $adb shell input tap $p[0] $p[1] | Out-Null } }

$pair = Invoke-RestMethod -Uri "$Gw/mgw/pair" -Method Post -ContentType 'application/json' -Body '{}'
$payload = $pair.qrPayload
if ([string]::IsNullOrWhiteSpace($payload)) { throw '网关没有返回 qrPayload' }
Write-Host ("配对串长度 = {0}" -f $payload.Length)

# 1) 冷启动到设备页（Activity 重建，避免停在上一次的弹层上）
& $adb shell am force-stop com.dsh.mobile | Out-Null
Start-Sleep -Milliseconds 800
& $adb shell am start -n com.dsh.mobile/.MainActivity | Out-Null
Start-Sleep -Seconds 5

# 2) 进设置页（右上角齿轮）；若已经停在设置页就跳过
$xml = Ui
Write-Host ("起始界面: " + (Texts $xml))
if (-not (Center $xml '连接设置')) {
  $gear = Center $xml '⚙'
  if (-not $gear) { throw "没找到齿轮" }
  Tap $gear; Start-Sleep -Seconds 4
}

# 3) 往下翻到「粘贴配对串」
$paste = $null
for ($k = 0; $k -lt 6; $k++) {
  $xml = Ui
  $paste = Center $xml '粘贴配对串'
  if ($paste) { break }
  if (Center $xml '连接设置') {
    & $adb shell input swipe 960 900 960 300 400 | Out-Null
    Start-Sleep -Seconds 2
  } else {
    # 没在设置页：再点一次齿轮
    $g2 = Center $xml '⚙'
    if ($g2) { Tap $g2; Start-Sleep -Seconds 3 } else { break }
  }
}
if (-not $paste) {
  Write-Host ("当前界面: " + (Texts $xml))
  throw '找不到粘贴配对串'
}
Tap $paste; Start-Sleep -Seconds 3

# 4) 对话框里手输配对串（剪贴板为空时落到手输框）
$xml = Ui
$m = [regex]::Match($xml, 'class="android\.widget\.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
if (-not $m.Success) {
  Write-Host ("对话框界面: " + (Texts $xml))
  # 可能弹的是剪贴板确认框：点「手动输入」
  $mi = Center $xml '手动输入'
  if (-not $mi) { $mi = Center $xml '粘贴' }
  if ($mi) { Tap $mi; Start-Sleep -Seconds 2; $xml = Ui
    $m = [regex]::Match($xml, 'class="android\.widget\.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') }
  if (-not $m.Success) { throw '配对对话框里没有输入框' }
}
$ex = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
$ey = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
& $adb shell input tap $ex $ey | Out-Null
Start-Sleep -Milliseconds 900
& $adb shell input text $payload | Out-Null
Start-Sleep -Seconds 1

$xml = Ui
$btn = Center $xml '配对'
if (-not $btn) { Write-Host ("对话框: " + (Texts $xml)); throw '对话框里没有「配对」按钮' }
Tap $btn
Write-Host '已点「配对」，等待连接…'
for ($i = 0; $i -lt 15; $i++) {
  Start-Sleep -Seconds 2
  try {
    $st = Invoke-RestMethod -Uri "$Gw/mgw/status" -Method Get -TimeoutSec 4
    if ($st.connectedClients -gt 0) { Write-Host ("✓ 网关报告已连客户端 {0}" -f $st.connectedClients); break }
  } catch { }
}
$xml = Ui
Write-Host ("配对后界面: " + (Texts $xml))
