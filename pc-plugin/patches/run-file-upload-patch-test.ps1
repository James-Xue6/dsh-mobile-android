<#
  DSH 掌上通 · 网关文件附件补丁的「沙箱验证器」
  ==================================================================
  用法：
      pwsh -File pc-plugin\patches\run-file-upload-patch-test.ps1
      pwsh -File pc-plugin\patches\run-file-upload-patch-test.ps1 -Profile desktop -Keep

  它做什么（**全程不碰真网关目录**）：
    1. 在 %TEMP% 下建一个沙箱，把真网关的 lib/*.mjs 拷进去；
       ws / qrcode / @deepseek-ai 用 junction 指向真身（供 ESM 解析）。
    2. 把沙箱伪装成 USERPROFILE 下的 profile 路径，**在沙箱里**跑
       patch-gateway-file-upload.ps1（应用 → 幂等 → 回退）。
    3. 用打过补丁的两份文件跑 node test-file-upload-patch.mjs（28 条断言）。
    4. 回退后按 SHA-256 比对，确认与真网关文件**逐字节一致**。

  退出码：0 = 全部通过；非 0 = 有失败。
  ⚠️ 本脚本不会重启 DSH、不会改动真网关，也不替代真机端到端验证
     （见 docs/PROTO-FINDINGS.md「未验证项」）。
#>
param(
  [string]$Profile = 'desktop',
  [switch]$Keep
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)   # pc-plugin/patches -> repo root
$patchScript = Join-Path $PSScriptRoot 'patch-gateway-file-upload.ps1'
$testScript  = Join-Path $PSScriptRoot 'test-file-upload-patch.mjs'

$realLib = Join-Path $env:USERPROFILE ".dsh\profiles\$Profile\node_modules\dsh-plugin-mobile-gateway\lib"
$realNm  = Join-Path $env:USERPROFILE ".dsh\profiles\$Profile\node_modules"

foreach ($p in @($patchScript, $testScript, $realLib)) {
  if (-not (Test-Path $p)) { throw "找不到：$p" }
}

$node = (Get-Command node -ErrorAction SilentlyContinue).Source
if (-not $node) { throw 'PATH 里没有 node，无法跑断言' }

$sandbox = Join-Path $env:TEMP ('gwfileupload-test-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
$nm = Join-Path $sandbox 'node_modules'
$plug = Join-Path $nm 'dsh-plugin-mobile-gateway'
$plugLib = Join-Path $plug 'lib'
$fakeHome = Join-Path $sandbox 'home'
$fakeLib = Join-Path $fakeHome ".dsh\profiles\$Profile\node_modules\dsh-plugin-mobile-gateway\lib"

Write-Host "沙箱：$sandbox" -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $plugLib, $fakeLib | Out-Null

# 1) 拷 lib（index.mjs 有多个相对 import，必须整目录）
Copy-Item (Join-Path $realLib '*.mjs') $plugLib -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $realLib '*.js')  $plugLib -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $realLib '*.mjs') $fakeLib -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $realLib '*.js')  $fakeLib -Force -ErrorAction SilentlyContinue

# 2) 依赖 junction 指向真身
foreach ($pkg in @('ws', 'qrcode', '@deepseek-ai')) {
  $target = Join-Path $realNm $pkg
  if (Test-Path $target) {
    New-Item -ItemType Junction -Path (Join-Path $nm $pkg) -Target $target | Out-Null
  }
}

$results = [ordered]@{}
try {
  # 3) 应用
  Write-Host "`n[1/5] 应用补丁（沙箱内）" -ForegroundColor Cyan
  $env:USERPROFILE = $fakeHome
  & pwsh -NoProfile -File $patchScript -Profile $Profile
  if ($LASTEXITCODE -ne 0) { throw "应用补丁失败 (exit $LASTEXITCODE)" }

  # 4) 幂等
  Write-Host "`n[2/5] 重复应用（幂等）" -ForegroundColor Cyan
  $idem = & pwsh -NoProfile -File $patchScript -Profile $Profile
  if ($LASTEXITCODE -ne 0) { throw "幂等重跑失败 (exit $LASTEXITCODE)" }
  if (($idem -join '') -notmatch '已经打过') { throw '幂等分支没有命中（脚本可能重复插入了）' }

  # 5) 语法检查 + 断言
  Copy-Item (Join-Path $fakeLib 'index.mjs') $plugLib -Force
  Copy-Item (Join-Path $fakeLib 'dsh-host-adapter.mjs') $plugLib -Force

  Write-Host "`n[3/5] node --check" -ForegroundColor Cyan
  & $node --check (Join-Path $plugLib 'index.mjs');           if ($LASTEXITCODE -ne 0) { throw 'index.mjs 语法检查失败' }
  & $node --check (Join-Path $plugLib 'dsh-host-adapter.mjs'); if ($LASTEXITCODE -ne 0) { throw 'dsh-host-adapter.mjs 语法检查失败' }
  Write-Host '  两份文件语法 OK'

  Write-Host "`n[4/5] 运行时断言" -ForegroundColor Cyan
  & $node $testScript $sandbox
  $testExit = $LASTEXITCODE
  if ($testExit -ne 0) { throw "运行时断言失败 (exit $testExit)" }

  # 6) 回退并逐字节比对
  Write-Host "`n[5/5] 回退 + 逐字节比对" -ForegroundColor Cyan
  & pwsh -NoProfile -File $patchScript -Profile $Profile -Revert
  if ($LASTEXITCODE -ne 0) { throw "回退失败 (exit $LASTEXITCODE)" }
  foreach ($f in @('index.mjs', 'dsh-host-adapter.mjs')) {
    $a = (Get-FileHash (Join-Path $fakeLib $f) -Algorithm SHA256).Hash
    $b = (Get-FileHash (Join-Path $realLib $f) -Algorithm SHA256).Hash
    if ($a -ne $b) { throw "回退后 $f 与真网关不一致（$a vs $b）" }
    Write-Host "  $f 回退后与真网关一致"
  }

  Write-Host "`n===== 全部通过 =====" -ForegroundColor Green
  Write-Host '  ✓ 应用 / 幂等 / node --check / 28 条断言 / 回退逐字节一致'
  Write-Host '  ⚠️ 这不等于真机端到端验证；fileUploads/upload 在真宿主上的 agentId 解析仍未实测'
  exit 0
}
finally {
  if (-not $Keep -and (Test-Path $sandbox)) {
    # junction 只能用非递归删除，先摘掉再删目录
    foreach ($pkg in @('ws', 'qrcode', '@deepseek-ai')) {
      $j = Join-Path $nm $pkg
      if (Test-Path -LiteralPath $j) { & cmd.exe /c rmdir "$j" | Out-Null }
    }
    Remove-Item $sandbox -Recurse -Force -ErrorAction SilentlyContinue
  } elseif ($Keep) {
    Write-Host "`n沙箱保留在：$sandbox" -ForegroundColor Yellow
  }
}
