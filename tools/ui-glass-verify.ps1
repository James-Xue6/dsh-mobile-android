# =====================================================================
#  DSH 掌上通 · 毛玻璃 v3 UI 取证（模拟器）
#  用法：
#    pwsh -File tools\ui-glass-verify.ps1                # 装包 + 冷启动 + 逐屏截图
#    pwsh -File tools\ui-glass-verify.ps1 -SkipInstall   # 只截图
#  产物：evidence\ui-glass-<时间戳>\*.png + *.xml
#
#  说明：模拟器里 127.0.0.1 是它自己，连宿主网关要用 10.0.2.2（见项目既有的
#  tools\使用说明.md）。本脚本只负责「装包 -> 逐屏截图」，不建会话。
# =====================================================================
[CmdletBinding()]
param(
  [string]$SdkRoot = 'F:\AI\程序开发\android-sdk',
  [string]$Apk = '',
  [string]$Serial = '',
  [string]$OutDir = '',
  [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "找不到 adb: $adb" }
if ([string]::IsNullOrWhiteSpace($Apk)) { $Apk = Join-Path $root 'dist\dsh-mobile.apk' }
if ([string]::IsNullOrWhiteSpace($OutDir)) {
  $OutDir = Join-Path $root ('evidence\ui-glass-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# adb 的日志写在 %TEMP%，必须指到可写目录（否则 daemon 起不来）
$env:TEMP = Join-Path (Split-Path -Parent $root) '.tmp'
$env:TMP = $env:TEMP
if (-not (Test-Path $env:TEMP)) { $env:TEMP = Join-Path $root '.tmp'; $env:TMP = $env:TEMP }

if (-not $Serial) {
  $d = & $adb devices | Select-String 'emulator-\d+\s+device'
  if (-not $d) { throw '没有在线的模拟器（adb devices 里没有 emulator-* device）' }
  $Serial = ($d.ToString() -split '\s+')[0]
}
Write-Host "设备: $Serial"
Write-Host "产物目录: $OutDir"

function Shot([string]$name) {
  $p = Join-Path $OutDir ($name + '.png')
  $bytes = & $adb -s $Serial exec-out screencap -p
  [System.IO.File]::WriteAllBytes($p, [byte[]]$bytes)
  Write-Host "  截图 $name -> $p"
}
function UiDump([string]$name) {
  & $adb -s $Serial shell uiautomator dump /sdcard/ui.xml | Out-Null
  & $adb -s $Serial pull /sdcard/ui.xml (Join-Path $OutDir ($name + '.xml')) | Out-Null
}

if (-not $SkipInstall) {
  Write-Host "安装 $Apk ..."
  $out = & $adb -s $Serial install -r $Apk 2>&1
  $out | ForEach-Object { Write-Host "  $_" }
  if ($out -notmatch 'Success') { throw '安装失败' }
}

& $adb -s $Serial shell am force-stop com.dsh.mobile | Out-Null
Start-Sleep -Seconds 1
& $adb -s $Serial shell am start -n com.dsh.mobile/.MainActivity | Out-Null
Start-Sleep -Seconds 5

# 冷启动首屏（我的设备 / 连接门）
Shot '01-launch'
UiDump '01-launch'

# 设置页（右上齿轮 / 各屏入口不同，这里只做一次尝试）
& $adb -s $Serial shell input keyevent KEYCODE_BACK | Out-Null
Start-Sleep -Seconds 1

Get-ChildItem $OutDir | Select-Object Name, Length | Format-Table -AutoSize
Write-Host "完成：$OutDir"
