# =====================================================================
#  DSH 掌上通 · 协议层端到端验证（2026-10-05 四项优化）
#
#  跑什么：用**App 真实的 net 层源码**（src\com\dsh\mobile\net\*）在 JVM 上直连真网关，
#          实跑本轮新增的 4 个协议方法：
#            requestFileList / requestPermissionOptions / selectPermission(仅帧形状) /
#            sendMessageWithImages
#  为什么：模拟器在本机 DSH 会话内起不来（见 NEXT-UPDATE「本轮环境限制」），
#          于是把"帧发得对不对、回帧解析得对不对"这层用真实代码真跑一遍。
#
#  只读为主：权限只查询不写入；sendMessageWithImages 故意发到**不存在的会话**，
#            用回执 code=session/not-found 证明"帧被网关 schema 接受"，不创建会话、不触发模型。
#
#  用法（仓库根目录）：
#    pwsh -File harness\run-protocol-v2-test.ps1
#    pwsh -File harness\run-protocol-v2-test.ps1 -Gateway http://127.0.0.1:19387 -NoRevoke
#
#  退出码：0 = 全部断言通过；1 = 有断言失败；2 = 超时/环境问题。
# =====================================================================
[CmdletBinding()]
param(
  [string]$Gateway = 'http://127.0.0.1:19387',
  [string]$Jdk = '',
  [switch]$NoRevoke
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $here

if ([string]::IsNullOrWhiteSpace($Jdk)) {
  $Jdk = @('D:\AndroidStudio\jbr', 'C:\Program Files\Android\Android Studio\jbr',
           (Join-Path $root 'tools\jdk-21.0.12.1+1')) |
         Where-Object { Test-Path (Join-Path $_ 'bin\javac.exe') } | Select-Object -First 1
}
if (-not $Jdk) { throw "找不到 JDK（-Jdk 指定）" }

$javac = Join-Path $Jdk 'bin\javac.exe'
$java  = Join-Path $Jdk 'bin\java.exe'
$json  = Join-Path $here 'lib\json-20240303.jar'
$outDir = Join-Path $env:TEMP 'dsh-proto-v2-out'
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Step($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }

Step "1/4 编译（真实 net 源码 + 垫片 + 测试）"
$srcs = @(
  (Join-Path $here 'shim\android\os\Handler.java'),
  (Join-Path $here 'shim\android\os\Looper.java'),
  (Join-Path $here 'shim\android\util\Base64.java'),
  (Join-Path $here 'shim\android\content\Context.java'),
  (Join-Path $here 'shim\android\util\Log.java'),
  (Join-Path $here 'shim\com\dsh\mobile\notify\Notifier.java'),
  (Join-Path $root 'src\com\dsh\mobile\net\WsClient.java'),
  (Join-Path $root 'src\com\dsh\mobile\net\GatewayClient.java'),
  (Join-Path $here 'src\ProtocolV2Test.java')
)
& $javac -encoding UTF-8 -nowarn -d $outDir -cp $json @srcs
if ($LASTEXITCODE -ne 0) { throw "javac 失败（exit $LASTEXITCODE）" }

Step "2/4 向网关要一次性配对载荷"
$pair = Invoke-RestMethod -Uri "$Gateway/mgw/pair" -Method Post -ContentType 'application/json' -Body '{}'
if ([string]::IsNullOrWhiteSpace($pair.qrPayload)) { throw "网关没有返回 qrPayload（网关没开？）" }
$pf = Join-Path $env:TEMP 'dsh_proto_v2_payload.txt'
Set-Content -Path $pf -Value $pair.qrPayload -NoNewline -Encoding ascii
Write-Host ("   载荷长度 = {0}（只打印长度）" -f $pair.qrPayload.Length)

Step "3/4 运行 ProtocolV2Test"
$before = @()
try { $before = @((Invoke-RestMethod -Uri "$Gateway/mgw/devices" -TimeoutSec 5).devices | ForEach-Object { $_.id }) } catch { }

& $java '-Dstdout.encoding=UTF-8' '-Dfile.encoding=UTF-8' -cp "$outDir;$json" ProtocolV2Test $pf
$code = $LASTEXITCODE

if (-not $NoRevoke) {
  Step "4/4 吊销本次验证新建的临时设备（不留垃圾）"
  try {
    $after = @((Invoke-RestMethod -Uri "$Gateway/mgw/devices" -TimeoutSec 5).devices)
    foreach ($d in $after) {
      if ($before -notcontains $d.id) {
        Invoke-WebRequest -Uri "$Gateway/mgw/devices/$($d.id)/revoke" -Method POST -ContentType 'application/json' -Body '{}' -TimeoutSec 5 -UseBasicParsing | Out-Null
        Write-Host "   已吊销 $($d.id)"
      }
    }
  } catch { Write-Host "   （吊销失败，可手动处理）$($_.Exception.Message)" -ForegroundColor Yellow }
}

Write-Host ""
if ($code -eq 0) { Write-Host "✅ 协议层验证通过" -ForegroundColor Green }
else { Write-Host "❌ 协议层验证未通过（exit $code）" -ForegroundColor Red }
exit $code
