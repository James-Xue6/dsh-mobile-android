# 编译 JVM harness（复用 App 真实 net 层源码 + Android 垫片），不需要 Gradle/Android SDK
# 用法:
#   pwsh -File harness/build.ps1                      # 默认用工作区当前源码 src/ -> harness/out
#   pwsh -File harness/build.ps1 -NetRoot src -OutName out-ab   # 换输出目录（做新旧对照时用）
#
# 重要：默认必须是「当前源码」，否则 e2e 结果测的不是你现在这份代码、结论无效。
param(
  [string]$NetRoot = 'src',
  [string]$OutName = ''
)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $here

$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'F:\AI\程序开发\tools\jdk-21.0.12.1+1' }
$javac = Join-Path $javaHome 'bin\javac.exe'
$java = Join-Path $javaHome 'bin\java.exe'
if (-not (Test-Path $javac)) { throw "找不到 javac：$javac（请设置 JAVA_HOME）" }

# 源码根：默认 src（当前源码）；传别的源码根可编出对照产物（输出目录见上面 OutName）。
if ([string]::IsNullOrWhiteSpace($NetRoot)) { $NetRoot = 'src' }
$netBase = Join-Path $root $NetRoot
$tag = $NetRoot
if (-not (Test-Path $netBase)) { throw "找不到源码根：$netBase（相对仓库根 $root）" }

# 输出目录：src -> out；其他（快照）-> out-<叶子名>，保持"同一次运行同时保留新旧产物"的对照能力。
if ([string]::IsNullOrWhiteSpace($OutName)) {
  $leaf = Split-Path -Leaf $NetRoot
  $OutName = if ($leaf -eq 'src') { 'out' } else { 'out-' + $leaf }
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
