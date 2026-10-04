# 跑 LanScan / LanAddress 纯逻辑断言（JVM 直编真实源码，不用 Gradle / Android SDK）。
#
# 为什么单独一个脚本：harness/build.ps1 只编 GatewayClient+WsClient+Harness，
# 而「地址自动重新发现」这条链上的纯逻辑（网段枚举 / 面板识别 / 地址重建）在
# net/LanScan.java 与 net/LanAddress.java 里，两个类都零 Android 依赖，可以直接编。
#
# 用法：
#   $env:JAVA_HOME='D:\AndroidStudio\jbr'; pwsh -File harness/run-lanscan-test.ps1
#   $env:JAVA_HOME='D:\AndroidStudio\jbr'; $env:DSH_LANSCAN_LIVE='1'; pwsh -File harness/run-lanscan-test.ps1
[CmdletBinding()]
param(
    [string]$Jdk = $(if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) { $env:JAVA_HOME } else { @('D:\AndroidStudio\jbr') | Where-Object { Test-Path (Join-Path $_ 'bin\javac.exe') } | Select-Object -First 1 })
)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $here

$javac = Join-Path $Jdk 'bin\javac.exe'
$java = Join-Path $Jdk 'bin\java.exe'
if (-not (Test-Path $javac)) { throw "找不到 javac：$javac（请设 JAVA_HOME=D:\AndroidStudio\jbr）" }

$outDir = Join-Path $here 'out-lanscan'
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$sources = @(
    (Join-Path $root 'src\com\dsh\mobile\net\LanScan.java'),
    (Join-Path $root 'src\com\dsh\mobile\net\LanAddress.java'),
    (Join-Path $here 'src\LanScanTest.java')
)

& $javac -encoding UTF-8 -nowarn -d $outDir @sources
if ($LASTEXITCODE -ne 0) { throw "编译失败（exit $LASTEXITCODE）" }

& $java '-Dfile.encoding=UTF-8' -cp $outDir LanScanTest
if ($LASTEXITCODE -ne 0) { throw "断言失败（exit $LASTEXITCODE）" }
