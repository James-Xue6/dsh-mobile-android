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

# 断链 junction 自愈（评审 P0-5 ①）：
#   旧写法用 Test-Path 判「路径是否存在」，而 .buildstage 被 git clean -xfd 清掉后
#   残留的 C:\dshstage 断链 junction 仍会让 Test-Path 返回 True —— 于是跳过重建，
#   随后在 $Stage\app 上报 "Could not find a part of the path" 且永不自愈。
#   实测（Windows + .NET）：断链 junction 上 Test-Path 与 [IO.Directory]::Exists 都是 True，
#   只有「链接目标本身是否存在」以及「$Stage\子路径是否可达」才是可靠的判据，故在此显式
#   解析 junction 的 Target 并检查目标可达性。
function Test-StageLink($path, $target) {
  $it = Get-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
  if ($null -eq $it) { return $false }                               # 路径本身不存在
  $isLink = (($it.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)
  if (-not $isLink) { return [System.IO.Directory]::Exists($path) }   # 实体目录：沿用旧行为
  $t = @($it.Target) | Where-Object { $_ } | Select-Object -First 1
  if (-not $t) { return $false }
  # 断链：junction 还在，但目标目录已被删除
  if (-not [System.IO.Directory]::Exists([string]$t)) { return $false }
  return ([System.IO.Path]::GetFullPath([string]$t).TrimEnd('\') -ieq
          [System.IO.Path]::GetFullPath($target).TrimEnd('\'))
}

if (-not (Test-StageLink $Stage $realStage)) {
  if (Test-Path -LiteralPath $Stage) {
    # 清理断链/错链：junction 只能用非递归的 rmdir，Remove-Item -Recurse 会跟进链接删到目标里去
    & cmd.exe /c rmdir "$Stage" | Out-Null
    if (Test-Path -LiteralPath $Stage) { throw "暂存路径 $Stage 残留且无法清理，请手动删除后重试" }
    Write-Host "    已清理断链/失效的暂存路径 $Stage"
  }
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

# 期望的证书指纹（SHA-256，大写、无冒号）。换密钥对老用户是灾难性事件：
# 新密钥签名后覆盖安装一律报「应用未安装」，因此密钥缺失时绝不自动重建。
$expectedFp = '2E518D756794EB72864B5A7C21849EA39FD27754EED0B60B74B2C7E12D8ECE8E'

if (-not (Test-Path $ks)) {
  Write-Host "`n[!] 签名密钥库缺失: $ks" -ForegroundColor Red
  Write-Host "    alias                 : dshmobile"
  Write-Host "    期望证书 SHA-256 指纹 : $expectedFp"
  Write-Host "    证书有效期至          : 2056-09-23"
  Write-Host "    本脚本拒绝自动生成新密钥（评审 P0-5 ②）：换新密钥后，所有已装旧版本的用户"
  Write-Host "    覆盖安装都会报「应用未安装」。请从备份恢复该 .jks（口令见 keystore.local.ps1"
  Write-Host "    或环境变量 DSH_KS_PASS）后重试。"
  throw "签名密钥库缺失，已终止构建（绝不自动重签）"
}

$signed = Join-Path $out 'dsh-mobile.apk'
& "$bt\apksigner.bat" sign `
  --ks $ks --ks-key-alias dshmobile `
  --ks-pass "pass:$ksPass" --key-pass "pass:$ksPass" `
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
  --out $signed $aligned
Assert-Ok 'apksigner sign'

Write-Host "`n===== 签名校验 =====" -ForegroundColor Green
$certOut = & "$bt\apksigner.bat" verify --verbose --print-certs $signed 2>&1
if ($LASTEXITCODE -ne 0) { throw "apksigner verify 失败 (exit $LASTEXITCODE)" }
$certOut | Select-Object -First 12 | ForEach-Object { Write-Host "    $_" }

# 指纹必须与期望值一致：换了密钥却把包发出去，比构建失败严重得多
$fpMatch = [regex]::Match(($certOut -join "`n"), 'SHA-256 digest:\s*([0-9a-fA-F:]+)')
if (-not $fpMatch.Success) { throw '无法从 apksigner verify 输出中读到证书指纹，拒绝产出未验证的包' }
$fp = $fpMatch.Groups[1].Value.Replace(':', '').ToUpperInvariant()
if ($fp -ne $expectedFp) {
  throw "签名证书 SHA-256 指纹不符！`n  实际: $fp`n  期望: $expectedFp`n该包不能分发给老用户（覆盖安装会失败），请用正确密钥库重新签名。"
}
Write-Host "    证书 SHA-256 指纹与期望一致: $fp" -ForegroundColor Green

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

# 分发一致性：给用户一份可核对的 SHA-256（评审 P0-5 ③）
$apkHash = (Get-FileHash -LiteralPath $final -Algorithm SHA256).Hash.ToLowerInvariant()
$hashFile = "$final.sha256"
Set-Content -LiteralPath $hashFile -Value "$apkHash  dsh-mobile.apk" -Encoding ASCII
Write-Host "校验值: $hashFile  ($apkHash)" -ForegroundColor Green
