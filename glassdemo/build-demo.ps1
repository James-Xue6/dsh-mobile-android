# =====================================================================
#  液态玻璃引擎 Demo — 独立 APK 构建脚本（免 Gradle）
#  管线: 暂存到 ASCII 路径 -> aapt2 compile/link -> javac -> d8 -> 打包 -> zipalign -> apksigner
#
#  为什么不用 Gradle：本机 ~/.gradle 里只有一个**未下完**的 gradle-9.6.0-bin(.part)，
#  且 F: 盘余量紧张；dsh-mobile 工程已验证过"用 build-tools 直编"这条路可用且快。
#
#  为什么要暂存到 C:\glassstage：aapt2 / d8 / zipalign / apksigner 是原生工具，
#  在 ANSI 代码页 936 的 Windows 上打不开含中文的路径（本工程路径含"程序开发"）。
#
#  与生产包的隔离：不同包名(com.dsh.glassdemo) + 独立密钥库 + 独立产物路径。
# =====================================================================
[CmdletBinding()]
param(
    [string]$Jdk  = $(if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) { $env:JAVA_HOME } else { $p = @('D:\AndroidStudio\jbr','C:\Program Files\Android\Android Studio\jbr','F:\AI\程序开发\tools\jdk-21.0.12.1+1') | Where-Object { Test-Path (Join-Path $_ 'bin\javac.exe') }; $p | Select-Object -First 1 }),
    [string]$Sdk        = 'F:\AI\程序开发\android-sdk',
    [string]$BuildTools = '36.0.0',
    [string]$Platform   = 'android-36',
    [string]$Stage      = 'C:\glassstage',
    [int]   $MinSdk     = 26,
    [int]   $TargetSdk  = 36
)

$ErrorActionPreference = 'Continue'
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
function Invoke-Tool($exe, $argList, $what) {
    $out = & $exe @argList 2>&1
    $code = $LASTEXITCODE
    if ($out) { $out | ForEach-Object { Write-Host "    $_" } }
    if ($code -ne 0) { throw "$what 失败 (exit $code)" }
    return $code
}

# ---------------------------------------------------------------- 0. 暂存
Step '0/7' "暂存源码到 ASCII 路径 $Stage（原生工具打不开中文路径）"
$realStage = Join-Path $root '.buildstage'
if (Test-Path -LiteralPath $Stage) {
    $it = Get-Item -LiteralPath $Stage -Force -ErrorAction SilentlyContinue
    $isLink = $it -and (($it.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
    if ($isLink) {
        $t = @($it.Target) | Where-Object { $_ } | Select-Object -First 1
        $ok = $t -and [System.IO.Directory]::Exists([string]$t) -and
              ([System.IO.Path]::GetFullPath([string]$t).TrimEnd('\') -ieq [System.IO.Path]::GetFullPath($realStage).TrimEnd('\'))
        if (-not $ok) { & cmd.exe /c rmdir "$Stage" | Out-Null }
    }
}
if (-not (Test-Path -LiteralPath $Stage)) {
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
Write-Host "    已暂存 $((Get-ChildItem (Join-Path $app 'src') -Recurse -Filter *.java).Count) 个 Java 文件"

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
Write-Host "    源文件 $($sources.Count) 个"

# android.jar 不含 LambdaMetafactory，用 build-tools 自带的 core-lambda-stubs.jar 补齐
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
$javacArgs += $sources
Invoke-Tool $javac $javacArgs 'javac' | Out-Null

# ---------------------------------------------------------------- 4. dex
Step '4/7' 'd8 — 生成 classes.dex'
$tmpJar = Join-Path $cls 'tmp-classes.jar'
$jarExe = Join-Path $Jdk 'bin\jar.exe'
if (-not (Test-Path $jarExe)) { $jarExe = 'jar' }
Invoke-Tool $jarExe @('cf', $tmpJar, '-C', $cls, '.') 'jar' | Out-Null

$d8Jar = Join-Path $bt 'lib\d8.jar'
if (-not (Test-Path $d8Jar)) { $d8Jar = Join-Path $bt 'd8.jar' }
if (-not (Test-Path $d8Jar)) { throw "缺少 d8.jar（找过 $bt\lib 与 $bt）" }
$javaExe = Join-Path $Jdk 'bin\java.exe'
Invoke-Tool $javaExe @('-Xmx768M', '-Xss1m', '-cp', $d8Jar, 'com.android.tools.r8.D8',
    '--lib', $androidJar, '--min-api', "$MinSdk", '--output', $dexd, $tmpJar) 'd8' | Out-Null
Remove-Item $tmpJar -Force -ErrorAction SilentlyContinue

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
$ksDir = Join-Path $env:USERPROFILE '.glassdemo-keys'
New-Item -ItemType Directory -Force -Path $ksDir | Out-Null
$ks = Join-Path $ksDir 'glassdemo.jks'
$ksPass = 'glassdemo-local'      # 本机演示用密钥：不用于分发，故口令写死在脚本里

if (-not (Test-Path $ks)) {
    Write-Host "    生成本机演示密钥 $ks"
    & $keytool -genkeypair -keystore $ks -alias glassdemo -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $ksPass -keypass $ksPass `
        -dname "CN=Glass Engine Demo, OU=demo, O=local, L=local, ST=local, C=CN"
    if ($LASTEXITCODE -ne 0) { throw "生成签名密钥失败 (exit $LASTEXITCODE)" }
}

$signed = Join-Path $out 'glass-demo.apk'
Invoke-Tool "$bt\apksigner.bat" @('sign',
    '--ks', $ks, '--ks-key-alias', 'glassdemo',
    '--ks-pass', "pass:$ksPass", '--key-pass', "pass:$ksPass",
    '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true',
    '--out', $signed, $aligned) 'apksigner sign' | Out-Null

Write-Host "`n===== 签名校验 =====" -ForegroundColor Green
$certOut = & "$bt\apksigner.bat" verify --verbose --print-certs $signed 2>&1
if ($LASTEXITCODE -ne 0) { throw "apksigner verify 失败 (exit $LASTEXITCODE)" }
$certOut | Select-Object -First 8 | ForEach-Object { Write-Host "    $_" }

Write-Host "`n===== APK 信息 =====" -ForegroundColor Green
& "$bt\aapt2.exe" dump badging $signed |
    Select-String -Pattern "^(package|sdkVersion|targetSdkVersion|application-label|launchable-activity)"

# ---------------------------------------------------------------- 回拷产物
$dist = Join-Path $root 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$final = Join-Path $dist 'glass-demo.apk'
Copy-Item $signed $final -Force
$size = (Get-Item $final).Length
Write-Host "`n产物: $final  ($([math]::Round($size/1KB,1)) KB)" -ForegroundColor Green
$apkHash = (Get-FileHash -LiteralPath $final -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath "$final.sha256" -Value "$apkHash  glass-demo.apk" -Encoding ASCII
Write-Host "校验值: $apkHash" -ForegroundColor Green
