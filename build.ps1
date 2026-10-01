# =====================================================================
#  DSH Mobile — 免 Gradle 的 APK 构建脚本
#  管线: 暂存到 ASCII 路径 -> aapt2 compile/link -> javac -> d8 -> 打包 -> zipalign -> apksigner
#
#  为什么要暂存：工作区路径含中文，而 aapt2/d8/zipalign/apksigner 是原生工具，
#  在 ANSI 代码页 936 的 Windows 上无法打开非 ASCII 路径。因此整条管线在
#  C:\dshstage 下运行，最后把产物拷回工作区 dist\。
# =====================================================================
[CmdletBinding()]
param(
  [string]$Jdk        = 'F:\AI\程序开发\tools\jdk-21.0.12.1+1',
  [string]$Sdk        = 'C:\Program Files (x86)\Android\android-sdk',
  [string]$BuildTools = '36.0.0',
  [string]$Platform   = 'android-36',
  [string]$Stage      = 'C:\dshstage',
  [int]   $MinSdk     = 26,
  [int]   $TargetSdk  = 36,
  [switch]$NoOptimize
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$env:JAVA_HOME = $Jdk

$bt         = Join-Path $Sdk "build-tools\$BuildTools"
$androidJar = Join-Path $Sdk "platforms\$Platform\android.jar"
$javac      = Join-Path $Jdk 'bin\javac.exe'
$keytool    = Join-Path $Jdk 'bin\keytool.exe'

foreach ($p in @($bt, $androidJar, $javac, $keytool)) {
  if (-not (Test-Path $p)) { throw "缺少构建依赖: $p" }
}

function Step($n, $msg) { Write-Host "`n[$n] $msg" -ForegroundColor Cyan }
function Assert-Ok($what) { if ($LASTEXITCODE -ne 0) { throw "$what 失败 (exit $LASTEXITCODE)" } }

# 原生工具的错误走 stderr，必须显式捕获才能看到诊断
function Invoke-Tool($exe, $argList, $what) {
  $out = & $exe @argList 2>&1
  $code = $LASTEXITCODE
  if ($out) { $out | ForEach-Object { Write-Host "    $_" } }
  if ($code -ne 0) { throw "$what 失败 (exit $code)" }
  return $code
}

# ---------------------------------------------------------------- 0. 暂存
Step '0/7' "暂存源码到 ASCII 路径 $Stage"
# ASCII 暂存根必须是「指向工作区内的 junction」：aapt2 需要纯 ASCII 路径，
# 而沙箱只允许 javac/d8 这类子进程写工作区内的文件（直接写 C:\ 会被拒绝）。
$realStage = Join-Path $root '.buildstage'
if (-not (Test-Path $Stage)) {
  New-Item -ItemType Directory -Force -Path $realStage | Out-Null
  New-Item -ItemType Junction -Path $Stage -Target $realStage | Out-Null
  Write-Host "    已建立 junction $Stage -> $realStage"
}

$app = Join-Path $Stage 'app'
Remove-Item $app -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $app | Out-Null
Copy-Item (Join-Path $root 'AndroidManifest.xml') $app -Force
Copy-Item (Join-Path $root 'res') $app -Recurse -Force
Copy-Item (Join-Path $root 'src') $app -Recurse -Force
New-Item -ItemType Directory -Force -Path (Join-Path $app 'libs') | Out-Null
Get-ChildItem (Join-Path $root 'libs') -Filter *.jar -ErrorAction SilentlyContinue |
  ForEach-Object { Copy-Item $_.FullName (Join-Path $app 'libs') -Force }
Write-Host "    已暂存 $( (Get-ChildItem (Join-Path $app 'src') -Recurse -Filter *.java).Count ) 个 Java 文件"

$gen  = Join-Path $app 'build\gen'
$cls  = Join-Path $app 'build\classes'
$dexd = Join-Path $app 'build\dex'
$out  = Join-Path $app 'build\out'
New-Item -ItemType Directory -Force -Path $gen, $cls, $dexd, $out | Out-Null

# ---------------------------------------------------------------- 1. 资源编译
Step '1/7' 'aapt2 compile — 编译资源'
Invoke-Tool "$bt\aapt2.exe" @('compile', '--dir', (Join-Path $app 'res'), '-o', (Join-Path $app 'build\res.zip')) 'aapt2 compile' | Out-Null

# ---------------------------------------------------------------- 2. 资源链接
Step '2/7' 'aapt2 link — 生成基础 APK 与 R.java'
Invoke-Tool "$bt\aapt2.exe" @(
  'link',
  '-o', (Join-Path $app 'build\base.apk'),
  '-I', $androidJar,
  '--manifest', (Join-Path $app 'AndroidManifest.xml'),
  '--java', $gen,
  '--min-sdk-version', "$MinSdk",
  '--target-sdk-version', "$TargetSdk",
  '--auto-add-overlay',
  '--no-version-vectors',
  (Join-Path $app 'build\res.zip')
) 'aapt2 link' | Out-Null

# ---------------------------------------------------------------- 3. Java 编译
Step '3/7' 'javac — 编译 Java 源码'
$sources  = @()
$sources += (Get-ChildItem $gen -Recurse -Filter *.java -ErrorAction SilentlyContinue | ForEach-Object FullName)
$sources += (Get-ChildItem (Join-Path $app 'src') -Recurse -Filter *.java -ErrorAction SilentlyContinue | ForEach-Object FullName)
if ($sources.Count -eq 0) { throw '没有找到任何 Java 源文件' }

$libs = @(Get-ChildItem (Join-Path $app 'libs') -Filter *.jar -ErrorAction SilentlyContinue | ForEach-Object FullName)

# android.jar 不含 LambdaMetafactory，build-tools 自带 core-lambda-stubs.jar 补齐
$lambdaStubs = Join-Path $bt 'core-lambda-stubs.jar'
$bcp = $androidJar
if (Test-Path $lambdaStubs) { $bcp = "$androidJar;$lambdaStubs" }

$javacArgs = @(
  '-J-Duser.language=en',
  '-encoding', 'UTF-8',
  '-source', '8', '-target', '8',
  '-nowarn',
  '-bootclasspath', $bcp,
  '-d', $cls
)
if ($libs.Count -gt 0) { $javacArgs += @('-cp', ($libs -join ';')) }
$javacArgs += $sources

Invoke-Tool $javac $javacArgs 'javac' | Out-Null

# ---------------------------------------------------------------- 4. dex
Step '4/7' 'd8 — 生成 classes.dex'
$classFiles = @(Get-ChildItem $cls -Recurse -Filter *.class | ForEach-Object FullName)
$d8Args = @('--lib', $androidJar, '--min-api', "$MinSdk", '--output', $dexd)
if (-not $NoOptimize) { $d8Args += '--release' }
$d8Args += $classFiles
if ($libs.Count -gt 0) { $d8Args += $libs }

Invoke-Tool "$bt\d8.bat" $d8Args 'd8' | Out-Null

# ---------------------------------------------------------------- 5. 打包 dex
Step '5/7' '打包 — 把 classes.dex 写入 APK'
$unsigned = Join-Path $app 'build\app.unsigned.apk'
Copy-Item (Join-Path $app 'build\base.apk') $unsigned -Force
Add-Type -AssemblyName System.IO.Compression | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem | Out-Null

$zip = [System.IO.Compression.ZipFile]::Open($unsigned, [System.IO.Compression.ZipArchiveMode]::Update)
try {
  foreach ($dexFile in (Get-ChildItem $dexd -Filter *.dex)) {
    $entry = $zip.CreateEntry($dexFile.Name, [System.IO.Compression.CompressionLevel]::NoCompression)
    $o = $entry.Open()
    $i = [System.IO.File]::OpenRead($dexFile.FullName)
    try { $i.CopyTo($o) } finally { $i.Dispose(); $o.Dispose() }
    Write-Host "    + $($dexFile.Name) ($([math]::Round($dexFile.Length/1KB,1)) KB)"
  }
} finally { $zip.Dispose() }

# ---------------------------------------------------------------- 6. 对齐
Step '6/7' 'zipalign — 4 字节对齐'
$aligned = Join-Path $app 'build\app.aligned.apk'
Invoke-Tool "$bt\zipalign.exe" @('-f', '-p', '4', $unsigned, $aligned) 'zipalign' | Out-Null

# ---------------------------------------------------------------- 7. 签名
Step '7/7' 'apksigner — 签名并校验'
$ksDir = Join-Path $env:USERPROFILE '.dsh-mobile-keys'
New-Item -ItemType Directory -Force -Path $ksDir | Out-Null
$ks = Join-Path $ksDir 'dshmobile.jks'

# 签名口令绝不写进代码：优先环境变量 DSH_KS_PASS，
# 其次仓库根目录下的 keystore.local.ps1（内含 $KsPass = '...'，已被 .gitignore 忽略）
$ksPass = $env:DSH_KS_PASS
$ksLocal = Join-Path $root 'keystore.local.ps1'
if ((-not $ksPass) -and (Test-Path $ksLocal)) {
  . $ksLocal
  $ksPass = $KsPass
}
if (-not $ksPass) {
  throw "缺少签名口令：请设置环境变量 DSH_KS_PASS，或创建 $ksLocal 写入 `$KsPass = '你的口令'"
}

if (-not (Test-Path $ks)) {
  Write-Host "    生成固定签名密钥库 $ks"
  & $keytool -genkeypair `
    -keystore $ks -alias dshmobile -keyalg RSA -keysize 2048 -validity 10950 `
    -storepass $ksPass -keypass $ksPass `
    -dname 'CN=DSH Mobile, OU=Personal, O=DSH Mobile, L=Local, ST=Local, C=CN' | Out-Null
  Assert-Ok 'keytool'
}

$signed = Join-Path $out 'dsh-mobile.apk'
& "$bt\apksigner.bat" sign `
  --ks $ks --ks-key-alias dshmobile `
  --ks-pass "pass:$ksPass" --key-pass "pass:$ksPass" `
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
  --out $signed $aligned
Assert-Ok 'apksigner sign'

Write-Host "`n===== 签名校验 =====" -ForegroundColor Green
& "$bt\apksigner.bat" verify --verbose --print-certs $signed | Select-Object -First 12

Write-Host "`n===== APK 信息 =====" -ForegroundColor Green
& "$bt\aapt2.exe" dump badging $signed |
  Select-String -Pattern "^(package|sdkVersion|targetSdkVersion|application-label|launchable-activity|uses-permission)"

# ---------------------------------------------------------------- 回拷产物
$dist = Join-Path $root 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$final = Join-Path $dist 'dsh-mobile.apk'
Copy-Item $signed $final -Force

$size = (Get-Item $final).Length
Write-Host "`n产物: $final  ($([math]::Round($size/1KB,1)) KB)" -ForegroundColor Green
