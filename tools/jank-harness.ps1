# 卡顿测量台：同一个手势序列 + gfxinfo 前后对比（模拟器上可复现；真机不吃注入手势）
# 用法: .\jank-harness.ps1 -Serial emulator-5554 -Swipes 8
param(
    [string]$Serial = "emulator-5554",
    [string]$Activity = "com.dsh.mobile/com.dsh.mobile.glass.GlassChatPageActivity",
    [int]$Swipes = 8,
    [switch]$Restart
)
$adb = "C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe"

if ($Restart) {
    & $adb -s $Serial shell am force-stop com.dsh.mobile | Out-Null
    Start-Sleep -Seconds 2
    & $adb -s $Serial shell am start -n $Activity | Out-Null
    Start-Sleep -Seconds 7
}
# 基线：清零，然后只做"加载 + 一次静置"
& $adb -s $Serial shell dumpsys gfxinfo com.dsh.mobile reset | Out-Null
& $adb -s $Serial shell input swipe 540 900 540 2200 200 | Out-Null
Start-Sleep -Milliseconds 800

# 被测动作：固定序列的上下滑（每次 400ms）
1..$Swipes | ForEach-Object {
    & $adb -s $Serial shell input swipe 540 1900 540 900 400 | Out-Null
    Start-Sleep -Milliseconds 250
    & $adb -s $Serial shell input swipe 540 900 540 1900 400 | Out-Null
    Start-Sleep -Milliseconds 250
}
Start-Sleep -Seconds 2

$raw = & $adb -s $Serial shell dumpsys gfxinfo com.dsh.mobile 2>&1
function Graba($label, $pattern) {
    $l = $raw | Select-String $pattern | Select -First 1
    if ($l) { "{0,-26} {1}" -f $label, ($l.Line.Trim()) } else { "{0,-26} (未找到)" -f $label }
}
"=== 测量台结果 ($Serial, $Swipes 个来回) ==="
Graba "Total frames"        "Total frames rendered"
Graba "Janky frames"        "Janky frames:"
Graba "P50 / P90 / P99"     "99th percentile"
Graba "Slow UI thread"      "Slow UI thread"
Graba "Slow bitmap uploads" "Slow bitmap uploads"
Graba "Slow draw commands"  "Slow issue draw commands"
Graba "Missed Vsync"        "Missed Vsync"
$hl = $raw | Select-String "HISTOGRAM" | Select -First 1
if ($hl) { "HISTOGRAM: " + ($hl.Line -replace '^\s*HISTOGRAM:\s*','').Substring(0, [Math]::Min(220, ($hl.Line -replace '^\s*HISTOGRAM:\s*','').Length)) }
