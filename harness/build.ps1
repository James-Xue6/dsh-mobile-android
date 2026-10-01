# 编译 JVM harness（复用 App 真实 net 层源码 + Android 垫片），不需要 Gradle/Android SDK
# 用法:
#   pwsh -File harness/build.ps1                      # 默认用冻结快照 harness/snapshot/ce7afd8（P0 批次）
#   pwsh -File harness/build.ps1 -NetRoot ..\src       # 用工作区当前 src（会被并行编辑，谨慎）
param(
  [string]$NetRoot = '',
  [string]$OutName = 'out-ce7afd8'
)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $here

$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'F:\AI\程序开发\tools\jdk-21.0.12.1+1' }
$javac = Join-Path $javaHome 'bin\javac.exe'
$java = Join-Path $javaHome 'bin\java.exe'
if (-not (Test-Path $javac)) { throw "找不到 javac：$javac（请设置 JAVA_HOME）" }

# 源码根：默认用冻结快照，避免并行编辑中的 src/ 让测试结果不可复现
if ([string]::IsNullOrWhiteSpace($NetRoot)) {
  $netBase = Join-Path $here 'snapshot\ce7afd8'
  $tag = 'snapshot/ce7afd8'
} else {
  $netBase = Join-Path $root $NetRoot
  $tag = $NetRoot
}
$gw = Join-Path $netBase 'com\dsh\mobile\net\GatewayClient.java'
$ws = Join-Path $netBase 'com\dsh\mobile\net\WsClient.java'
if (-not (Test-Path $gw) -or -not (Test-Path $ws)) { throw "找不到 net 源码：$netBase" }

$outDir = Join-Path $here $OutName
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$sources = @(
  (Join-Path $here 'shim\android\os\Handler.java'),
  (Join-Path $here 'shim\android\os\Looper.java'),
  (Join-Path $here 'shim\android\util\Base64.java'),
  $ws,
  $gw,
  (Join-Path $here 'src\Harness.java')
)

& $javac -encoding UTF-8 -nowarn -d $outDir -cp (Join-Path $here 'lib\json-20240303.jar') @sources
if ($LASTEXITCODE -ne 0) { throw "harness 编译失败（exit $LASTEXITCODE）" }
Write-Host "harness 编译完成 → $outDir   [源码: $tag]"
Write-Host "运行: `"$java`" -cp `"$outDir;$here\lib\json-20240303.jar`" Harness <pairing.txt>"
