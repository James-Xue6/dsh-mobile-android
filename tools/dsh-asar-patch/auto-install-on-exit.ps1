# [V3.54] DSH app.asar session patch -- watcher-style auto install (ASCII only: Windows PowerShell 5.1
# reads .ps1 as ANSI unless it has a UTF-8 BOM, so keep this file ASCII to avoid parse errors).
#
# Why a watcher: app.asar is locked while DSH runs (rename fails with "being used by another process"),
# so the swap can only happen after DSH fully exits. The user only has to QUIT DSH; this watcher does
# backup -> swap -> verify -> relaunch DSH by itself.

$ErrorActionPreference = 'Continue'
$RES     = 'D:\AI\DSH\resources'
$APP     = Join-Path $RES 'app.asar'
$PATCHED = Join-Path $RES 'app.asar.patched'
$EXE     = 'D:\AI\DSH\DeepSeek Harness.exe'
$EXPECT  = 121348401
$LOG     = Join-Path $RES 'dsh-asar-patch.log'
$TASK    = 'DSH-asar-patch-once'

function Log($m) {
    $line = "{0}  {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $m
    Add-Content -LiteralPath $LOG -Value $line -Encoding UTF8
}

Log "=== watcher started; waiting for DSH to fully exit (max 30 min) ==="

if (-not (Test-Path -LiteralPath $PATCHED)) { Log "ERR app.asar.patched not found; abort"; exit 1 }
if ((Get-Item -LiteralPath $APP).Length -eq $EXPECT) { Log "INFO app.asar is already patched; nothing to do"; exit 0 }

$deadline = (Get-Date).AddMinutes(30)
while ((Get-Date) -lt $deadline) {
    $p = Get-Process -Name 'DeepSeek Harness' -ErrorAction SilentlyContinue
    if (-not $p) { break }
    Start-Sleep -Milliseconds 700
}
if (Get-Process -Name 'DeepSeek Harness' -ErrorAction SilentlyContinue) {
    Log "ERR DSH did not exit within 30 min; nothing changed"; exit 1
}
Log "OK DSH exited"
Start-Sleep -Seconds 4

$ts  = Get-Date -Format 'yyyyMMdd-HHmmss'
$BAK = Join-Path $RES ("app.asar.bak-" + $ts)
try { Move-Item -LiteralPath $APP -Destination $BAK -ErrorAction Stop; Log ("STEP1 backup -> " + $BAK) }
catch { Log ("ERR backup rename failed: " + $_.Exception.Message); exit 1 }

try { Move-Item -LiteralPath $PATCHED -Destination $APP -ErrorAction Stop; Log "STEP2 patch moved in" }
catch {
    Log ("ERR patch move failed: " + $_.Exception.Message + " ; rolling back")
    Move-Item -LiteralPath $BAK -Destination $APP -Force
    exit 1
}

$len = (Get-Item -LiteralPath $APP).Length
if ($len -ne $EXPECT) {
    Log ("ERR size check failed: " + $len + " != " + $EXPECT + " ; rolling back")
    Remove-Item -LiteralPath $APP -Force
    Move-Item -LiteralPath $BAK -Destination $APP -Force
    exit 1
}
Log ("STEP3 verified size " + $len)

try { Start-Process -FilePath $EXE; Log "STEP4 DSH relaunched" }
catch { Log ("WARN relaunch failed: " + $_.Exception.Message) }

Log ("DONE backup kept at " + $BAK)
schtasks /delete /tn $TASK /f 2>$null | Out-Null
