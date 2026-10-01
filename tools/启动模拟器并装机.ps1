<#
  DSH 掌上通 · 一键起模拟器 + 装机 + 打开 App
  ------------------------------------------------------------------
  在你自己的桌面会话里跑（不要交给 DSH 的 pwsh 沙箱，沙箱里模拟器起不来）：
      pwsh -NoProfile -ExecutionPolicy Bypass -File tools\启动模拟器并装机.ps1
  或直接双击 tools\启动模拟器并装机.bat

  参数：
    -Avd dshphone         AVD 名（默认 dshphone）
    -Apk <路径>           要装的 APK（默认 dist\dsh-mobile.apk）
    -SdkRoot <路径>       Android SDK（默认 %ANDROID_SDK_ROOT%，否则 F:\AI\程序开发\android-sdk）
    -Windowed             带窗口启动（默认真；加 -Headless 则无窗口）
    -WipeData             先清空 userdata（覆盖安装出问题时用，会丢已配对状态）
    -SkipInstall          只起模拟器，不装包
    -TimeoutSec 600       等开机最长秒数
#>
[CmdletBinding()]
param(
  [string]$Avd = 'dshphone',
  [string]$Apk = '',
  [string]$SdkRoot = '',
  [switch]$Headless,
  [switch]$WipeData,
  [switch]$SkipInstall,
  [int]$TimeoutSec = 600
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $here

function Say($m)  { Write-Host $m }
function OK($m)   { Write-Host "[OK] $m"   -ForegroundColor Green }
function Warn($m) { Write-Host "[警告] $m" -ForegroundColor Yellow }
function Die($m)  { Write-Host "[错误] $m" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------- 路径
if ([string]::IsNullOrWhiteSpace($SdkRoot)) {
  $SdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { 'F:\AI\程序开发\android-sdk' }
}
$emulator = Join-Path $SdkRoot 'emulator\emulator.exe'
$adb      = Join-Path $SdkRoot 'platform-tools\adb.exe'
if (-not (Test-Path $emulator)) { Die "找不到 emulator.exe：$emulator（用 -SdkRoot 指定 SDK）" }
if (-not (Test-Path $adb))      { Die "找不到 adb.exe：$adb" }
if ([string]::IsNullOrWhiteSpace($Apk)) { $Apk = Join-Path $root 'dist\dsh-mobile.apk' }

# AVD / 用户目录：本机上它们都在工作区内（不是默认的 %USERPROFILE%\.android）
$avdHome  = Join-Path $root '..\.android-avd'
$userHome = Join-Path $root '..\.android-user'
if (-not $env:ANDROID_AVD_HOME)   { if (Test-Path $avdHome)  { $env:ANDROID_AVD_HOME   = (Resolve-Path $avdHome).Path } }
if (-not $env:ANDROID_USER_HOME)  { if (Test-Path $userHome) { $env:ANDROID_USER_HOME  = (Resolve-Path $userHome).Path } }
if (-not $env:ANDROID_SDK_ROOT)  { $env:ANDROID_SDK_ROOT = $SdkRoot }
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT

Say "================================================================"
Say " DSH 掌上通 · 起模拟器 + 装机"
Say " AVD      : $Avd"
Say " AVD 目录 : $($env:ANDROID_AVD_HOME)"
Say " SDK      : $SdkRoot"
Say " APK      : $Apk"
Say "================================================================"

# ---------------------------------------------------------------- 起模拟器
$already = & $adb devices | Select-String 'emulator-\d+\s+device'
if ($already) {
  OK "已有模拟器在跑：$($already.ToString().Trim())"
} else {
  $emuArgs = @('-avd', $Avd, '-no-boot-anim', '-no-snapshot', '-no-metrics')
  if ($Headless) { $emuArgs += @('-no-window', '-no-audio') }
  if ($WipeData) { $emuArgs += '-wipe-data'; Warn "已指定 -WipeData：会清空模拟器数据（配对状态会丢）" }
  Say "`n启动模拟器：emulator.exe $($emuArgs -join ' ')"
  Start-Process -FilePath $emulator -ArgumentList $emuArgs | Out-Null
}

# ---------------------------------------------------------------- 等开机
Say "`n等待设备上线（adb wait-for-device）…"
& $adb start-server | Out-Null
& $adb wait-for-device
$serial = (& $adb devices | Select-String 'emulator-\d+\s' | Select-Object -First 1)
$serial = ($serial -split '\s+')[0]
if (-not $serial) { Die "没有检测到模拟器设备，检查上面模拟器窗口是否有报错" }
OK "设备序列号：$serial"

Say "等待系统开机完成（sys.boot_completed=1，最多 $TimeoutSec 秒）…"
$sw = [Diagnostics.Stopwatch]::StartNew()
$booted = $false
while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
  $b = (& $adb -s $serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
  if ($b -eq '1') { $booted = $true; break }
  Start-Sleep -Seconds 3
  Write-Host ("   … {0:N0}s" -f $sw.Elapsed.TotalSeconds) -NoNewline "`r"
}
if (-not $booted) { Die "等了 $TimeoutSec 秒仍未开机完成（sys.boot_completed 不是 1）" }
OK ("开机完成，用时 {0:N0}s" -f $sw.Elapsed.TotalSeconds)

# 顺手把屏幕保持常亮，免得测到一半锁屏
& $adb -s $serial shell svc power stayon true 2>$null | Out-Null

# ---------------------------------------------------------------- 装包
if ($SkipInstall) {
  Warn "已指定 -SkipInstall，跳过安装"
} else {
  if (-not (Test-Path $Apk)) { Die "找不到 APK：$Apk（先在仓库根跑 build.ps1）" }
  $fi = Get-Item $Apk
  Say "`n安装 $($fi.Name)（$([math]::Round($fi.Length/1KB,1)) KB，$($fi.LastWriteTime)）"
  Say "用 -r 覆盖安装（不卸载，保留已配对状态）…"
  $out = & $adb -s $serial install -r $Apk 2>&1
  $out | ForEach-Object { Write-Host "   $_" }
  if ($out -match 'Success') {
    OK "安装成功"
  } elseif ($out -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
    Warn "签名不一致 —— 这台设备上装的旧包不是同一把密钥签的。"
    Warn "要么换回原密钥重新构建，要么卸载重装（注意：卸载会丢掉配对状态，需重新扫码）。"
    Warn "卸载重装命令：adb -s $serial uninstall com.dsh.mobile"
  } else {
    Die "安装失败，见上面的 adb 输出"
  }

  # 回读版本号，确认装的是不是新包
  $vi = & $adb -s $serial shell dumpsys package com.dsh.mobile 2>$null | Select-String 'versionName|versionCode'
  if ($vi) { Say "`n设备上当前版本："; $vi | ForEach-Object { Write-Host "   $($_.ToString().Trim())" } }
  Say "   ↑ 明早要确认这里是 0.81-test；如果还是 0.8，就是装错包了。"
}

# ---------------------------------------------------------------- 打开 App
Say "`n打开 App…"
& $adb -s $serial shell am start -n com.dsh.mobile/.MainActivity | ForEach-Object { Write-Host "   $_" }
Start-Sleep -Seconds 2
$top = (& $adb -s $serial shell dumpsys activity activities 2>$null | Select-String 'topResumedActivity|ResumedActivity' | Select-Object -First 1)
if ($top) { OK "前台 Activity：$($top.ToString().Trim())" }

Say @"

===============================================================
 下一步（手工）
   1. 另开一个窗口跑：tools\启动物联网关.bat
      它会把配对串写到 tools\pairing.txt
   2. 在 App 里「扫码连接电脑」或用设置页粘贴配对串
      （注意：ce7afd8 已关掉 pairing intent 后门，
        `am start -e pairing ...` 这种自动化注入方式不再生效）
   3. 发一条消息，看《联调用例》里的 7 档故障切换
 注意：模拟器里访问本机网关要用 10.0.2.2，不是 127.0.0.1
===============================================================
"@
