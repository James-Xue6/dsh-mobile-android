# restore-gateway-panel-fix.ps1 的沙箱自测：
#   · 用假家目录 + 假网关包，跑真实的补丁脚本，验证两个补丁都能装上去
#   · 验证中文标记自检在 Windows PowerShell 5.1 下真的能配对（不是「乱码对乱码」的假通过）
#   · 验证幂等（第二次跑应跳过、不产生新备份）与版本守卫（版本不符必须拒绝执行）
# 跑法：
#     pwsh   -File .\tools\test-gateway-patch-script.ps1
#     powershell -File .\tools\test-gateway-patch-script.ps1
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$patchScript = Join-Path $repoRoot 'pc-plugin\patches\restore-gateway-panel-fix.ps1'
$utf8 = New-Object System.Text.UTF8Encoding($false)
function Read-Text([string]$p) { return [System.IO.File]::ReadAllText($p, $utf8) }

$passed = 0
$failed = @()
function Assert-That([string]$name, [bool]$ok, [string]$detail = '') {
  if ($ok) { $script:passed++; Write-Host "  OK  $name" -ForegroundColor Green }
  else { $script:failed += $name; Write-Host "  FAIL $name $(if ($detail) { "— $detail" })" -ForegroundColor Red }
}

# ── 找「未打补丁」的原始文件当夹具 ──────────────────────────────────────────
$installedDir = Join-Path $env:USERPROFILE '.dsh\profiles\desktop\node_modules\dsh-plugin-mobile-gateway'
$realPkg = Join-Path $installedDir 'package.json'
$libDir = Join-Path $installedDir 'lib'

# [2026-10-07 合并适配] 原文件把「本机备份」写死成朋友那台机器的文件名
# （client.js.bak-0.9.0-20261004-230615）—— 换台机器就永远找不到夹具而**静默跳过**。
# 改成：优先 npm 包解压出来的原版 → 其次本机**任意** .bak 备份（时间倒序）→ 最后才用
# 当前安装的 lib 文件；并且逐个检查「确实未打补丁」（已经是补丁版的跳过）。
function Select-Pristine([string[]]$cands, [string]$marker) {
  foreach ($c in $cands) {
    if (-not $c -or -not (Test-Path $c)) { continue }
    if ((Read-Text $c) -match $marker) { continue }   # 已经是补丁版，测不出东西
    return $c
  }
  return $null
}

$pristineClient = Select-Pristine (@(
  (Join-Path $repoRoot '..\_downloads\gw-extract\package\lib\client.js')
) + @(Get-ChildItem $libDir -Filter 'client.js.bak-*' -ErrorAction SilentlyContinue |
      Sort-Object LastWriteTime -Descending | ForEach-Object { $_.FullName }) + @(
  (Join-Path $libDir 'client.js')
)) '__DSH_MOBILE_ACCESS__'

# mjs 夹具同理：npm 解压原版 → 本机 .bak → 当前安装的文件
$pristineMjs = Select-Pristine (@(
  (Join-Path $repoRoot '..\_downloads\gw-extract\package\lib\cloudflared-binary.mjs')
) + @(Get-ChildItem $libDir -Filter 'cloudflared-binary.mjs.bak-*' -ErrorAction SilentlyContinue |
      Sort-Object LastWriteTime -Descending | ForEach-Object { $_.FullName }) + @(
  (Join-Path $libDir 'cloudflared-binary.mjs')
)) 'MIRROR_PREFIXES'

if (-not $pristineClient) { Write-Host '找不到未打补丁的 client.js 夹具，跳过（需要 _downloads\gw-extract 或本机 .bak 备份）' -ForegroundColor Yellow; exit 0 }
if (-not $pristineMjs) { Write-Host '找不到未打补丁的 cloudflared-binary.mjs 夹具，跳过' -ForegroundColor Yellow; exit 0 }

# 夹具必须确实是「未打补丁」的（Select-Pristine 已保证，这里再兜一道）
$pristineClientText = Read-Text $pristineClient
if ($pristineClientText -match '__DSH_MOBILE_ACCESS__') {
  Write-Host "夹具 client.js 已经是补丁版（$pristineClient），跳过" -ForegroundColor Yellow
  exit 0
}
if ((Read-Text $pristineMjs) -match 'MIRROR_PREFIXES') {
  Write-Host '夹具 cloudflared-binary.mjs 已经是补丁版，跳过' -ForegroundColor Yellow
  exit 0
}

# ── 搭假家目录里的假网关包 ──────────────────────────────────────────────────
$sandbox = Join-Path $env:TEMP ("dsh-gwpatch-sandbox-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$fakeHome = Join-Path $sandbox 'home'
$gwDir = Join-Path $fakeHome '.dsh\profiles\desktop\node_modules\dsh-plugin-mobile-gateway'
New-Item -ItemType Directory -Force -Path (Join-Path $gwDir 'lib') | Out-Null
Copy-Item $realPkg (Join-Path $gwDir 'package.json') -Force
Copy-Item $pristineClient (Join-Path $gwDir 'lib\client.js') -Force
Copy-Item $pristineMjs (Join-Path $gwDir 'lib\cloudflared-binary.mjs') -Force
Write-Host "沙箱: $sandbox"

function Invoke-PatchScript([string]$fakeUserHome) {
  $previousHome = $env:USERPROFILE
  $previousEap = $ErrorActionPreference
  try {
    $env:USERPROFILE = $fakeUserHome
    # 子进程用 throw 报错时会往 stderr 写；EAP=Stop 会把它当成本测试的致命错误，
    # 「版本不符」这条用例还没断言就被中断。先放成 Continue 收下输出。
    $ErrorActionPreference = 'Continue'
    $out = & powershell -NoProfile -ExecutionPolicy Bypass -File $patchScript 2>&1
    return @{ output = $out; exit = $LASTEXITCODE }
  } finally {
    $env:USERPROFILE = $previousHome
    $ErrorActionPreference = $previousEap
  }
}

Write-Host "`n== 第一轮：未打补丁 → 应全部装上 ==" -ForegroundColor Cyan
$first = Invoke-PatchScript $fakeHome
Write-Host ($first.output | Out-String)
Assert-That '脚本正常结束' ($first.exit -eq 0) "exit=$($first.exit)"

$clientDst = Join-Path $gwDir 'lib\client.js'
$mjsDst = Join-Path $gwDir 'lib\cloudflared-binary.mjs'
$clientText = Read-Text $clientDst
$mjsText = Read-Text $mjsDst

Assert-That 'client.js 已含手机接入桥' ($clientText -match '__DSH_MOBILE_ACCESS__')
Assert-That 'client.js 已含中文标记「三个按钮：先下载 App」（说明中文自检真能配对）' ($clientText -match '三个按钮：先下载 App')
Assert-That 'client.js 已含配对回退修复' ($clientText -match 'tunnelUrl\) return tunnelUrl')
Assert-That 'cloudflared-binary.mjs 已含镜像回退' ($mjsText -match 'MIRROR_PREFIXES' -and $mjsText -match 'gh-proxy\.com')
Assert-That '两个原始文件都已备份' ((Test-Path "$clientDst.bak-0.9.0-*") -or ((Get-ChildItem (Join-Path $gwDir 'lib') -Filter '*.bak-*' | Measure-Object).Count -ge 2))

$backupCountAfterFirst = (Get-ChildItem (Join-Path $gwDir 'lib') -Filter '*.bak-*' | Measure-Object).Count
Assert-That '备份数量为 2（两个文件各一份）' ($backupCountAfterFirst -eq 2) "实际 $backupCountAfterFirst"

Write-Host "`n== 第二轮：已是补丁版 → 应跳过且不产生新备份 ==" -ForegroundColor Cyan
$second = Invoke-PatchScript $fakeHome
Write-Host ($second.output | Out-String)
Assert-That '第二次仍正常结束' ($second.exit -eq 0) "exit=$($second.exit)"
$backupCountAfterSecond = (Get-ChildItem (Join-Path $gwDir 'lib') -Filter '*.bak-*' | Measure-Object).Count
Assert-That '幂等：备份数量没变' ($backupCountAfterSecond -eq $backupCountAfterFirst) "$backupCountAfterFirst → $backupCountAfterSecond"
Assert-That '第二次报告了「已是补丁版」' (($second.output | Out-String) -match '已是补丁版')

Write-Host "`n== 第三轮：网关版本不符 → 必须拒绝执行 ==" -ForegroundColor Cyan
$pkgPath = Join-Path $gwDir 'package.json'
$pkg = Read-Text $pkgPath | ConvertFrom-Json
$pkg.version = '9.9.9-test'
[System.IO.File]::WriteAllText($pkgPath, ($pkg | ConvertTo-Json -Depth 10), $utf8)
$third = Invoke-PatchScript $fakeHome
$thirdText = $third.output | Out-String
Assert-That '版本不符时非零退出' ($third.exit -ne 0) "exit=$($third.exit)"
Assert-That '版本不符时给出明确提示' ($thirdText -match '9\.9\.9-test' -or $thirdText -match '基线')
Assert-That '版本不符时没有覆盖文件' ((Read-Text $clientDst) -match '__DSH_MOBILE_ACCESS__')

Write-Host ""
Write-Host "────────────────────────────"
if ($failed.Count -eq 0) {
  Write-Host "沙箱测试全部通过：$passed 项断言" -ForegroundColor Green
} else {
  Write-Host "失败 $($failed.Count) 项 / 共 $($passed + $failed.Count) 项：" -ForegroundColor Red
  $failed | ForEach-Object { Write-Host "  · $_" }
}
Write-Host "沙箱目录保留以便排查: $sandbox"
if ($failed.Count -gt 0) { exit 1 }
