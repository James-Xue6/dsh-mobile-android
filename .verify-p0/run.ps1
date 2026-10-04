# P0-2 离线验证 runner：真实 net 层源码（GatewayClient+WsClient）+ 模拟网关
# 用法:
#   pwsh -File .verify-p0\run.ps1 -OutDir .verify-p0\out-new -MaxMs 30000 -Tag new
#   pwsh -File .verify-p0\run.ps1 -OutDir .verify-p0\out-old -MaxMs 95000 -Tag old
param(
  [string]$OutDir = '.verify-p0\out-new',
  [int]$Port     = 3391,
  [string]$Fault  = 'silent',
  [int]$MaxMs    = 30000,
  [string]$Tag    = 'new',
  [string]$SkipFg = 'false',
  [string]$Jdk    = 'D:\AndroidStudio\jbr'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$java = Join-Path $Jdk 'bin\java.exe'
$node = 'C:\Users\Administrator\AppData\Local\hermes\node\node.exe'
$jsonJar = Join-Path $root 'harness\lib\json-20240303.jar'
$mock = Join-Path $root 'tools\mock-gateway.mjs'
$out  = Join-Path $root $OutDir
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDir = Join-Path $PSScriptRoot "runs\$Tag-$stamp"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$gwLog = Join-Path $runDir 'gateway.log'
$gwErr = Join-Path $runDir 'gateway.log.err'
$hsLog = Join-Path $runDir 'probe.log'

Write-Host "== P0-2 验证 tag=$Tag fault=$Fault out=$out =="
$gw = Start-Process -FilePath $node -ArgumentList @($mock, '--port', "$Port", '--fault', $Fault) `
      -WorkingDirectory $runDir -RedirectStandardOutput $gwLog -RedirectStandardError $gwErr `
      -PassThru -WindowStyle Hidden
try {
  $pairing = $null
  for ($i = 0; $i -lt 125 -and -not $pairing; $i++) {
    Start-Sleep -Milliseconds 120
    if (Test-Path $gwLog) {
      $txt = [string](Get-Content $gwLog -Raw -ErrorAction SilentlyContinue)
      if ($txt) {
        $m = [regex]::Match($txt, 'PAIRING_STRING=(\S+)')
        if ($m.Success) { $pairing = $m.Groups[1].Value }
      }
    }
  }
  if (-not $pairing) { throw "模拟网关没有打印 PAIRING_STRING（见 $gwLog）" }
  $pairingFile = Join-Path $runDir 'pairing.txt'
  Set-Content -LiteralPath $pairingFile -Value $pairing -Encoding ascii -NoNewline

  # 用 JAVA_TOOL_OPTIONS 传 -Dfile.encoding：直接写在参数里会被 PowerShell/启动器拆开
  # （实测 java 收到 ".encoding=UTF-8" 当成主类名而 ClassNotFoundException）。
  try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
  $env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8'
  $jargs = @("-Dprobe.maxMs=$MaxMs", "-Dprobe.skipFg=$SkipFg", '-cp', "$out;$jsonJar", 'FgProbe', $pairingFile)
  & $java @jargs 2>&1 | Tee-Object -FilePath $hsLog
} finally {
  if ($gw -and -not $gw.HasExited) { Stop-Process -Id $gw.Id -Force -ErrorAction SilentlyContinue }
  Start-Sleep -Milliseconds 500
}

Write-Host "`n----- 模拟网关看到的连接（每条 upgrade = 一次 client connected）-----"
$jsonl = Join-Path $runDir 'mock-gateway.jsonl'
if (Test-Path $jsonl) {
  Get-Content $jsonl | Where-Object { $_ -match '"dir":"upgrade"' } | ForEach-Object {
    $o = $_ | ConvertFrom-Json
    Write-Host ("  upgrade  t={0}" -f $o.t)
  }
} else { Write-Host '  (没有 jsonl)' }
Write-Host "runDir=$runDir"
