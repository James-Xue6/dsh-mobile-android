# 给第三方网关插件 dsh-plugin-mobile-gateway@0.9.0 叠加本仓库的补丁。
#
# 用法（PowerShell 7 / pwsh 或 Windows PowerShell 5.1 均可）：
#     pwsh -File .\pc-plugin\patches\restore-gateway-panel-fix.ps1
#     powershell -File .\pc-plugin\patches\restore-gateway-panel-fix.ps1
#
# 本脚本是「整文件覆盖」，所以：
#   · 只在网关版本 == 基线版本时执行，版本不符直接报错退出（绝不降级插件）；
#   · 覆盖前把原文备份成 *.bak-<版本>-<时间戳>；覆盖后自检，不过就自动回滚；
#   · 已经是补丁版就跳过，重复执行不会反复覆盖。
#
# 两处补丁：
#  1) lib/client.js —— 面板修复
#     · 下拉弹出层透明穿透 → 新增 styles.select / styles.option 实底样式
#     · 「手机接入」（dsh-mobile-access）主面板置顶，网关设置收进「高级设置」
#     · 配对地址「自动选择」不再死等公网：Quick Tunnel 没就绪时回退局域网地址
#  2) lib/cloudflared-binary.mjs —— cloudflared 下载源加国内镜像回退
#     · 镜像在前、官方在后；每个候选都按官方 sha256 摘要校验，校验不过换下一个
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$expectedVersion = '0.9.0'
$gatewayDir = Join-Path $env:USERPROFILE '.dsh\profiles\desktop\node_modules\dsh-plugin-mobile-gateway'
$pkgPath = Join-Path $gatewayDir 'package.json'

# 文本一律按 UTF-8（无 BOM）读写。
# 为什么不用 Get-Content / Set-Content：Windows PowerShell 5.1 的 Get-Content 默认走
# ANSI 代码页，会把 UTF-8 中文读成乱码；Set-Content -Encoding utf8 又会写出 BOM。
# 本脚本要读的既有中文标记、也有 profile 里的中文路径，必须显式指定编码。
$utf8 = New-Object System.Text.UTF8Encoding($false)
function Read-Text([string]$path) { return [System.IO.File]::ReadAllText($path, $utf8) }

$patches = @(
  @{
    title   = '面板：下拉实底 + 手机接入置顶 + 高级设置折叠 + 配对回退局域网'
    srcName = 'dsh-plugin-mobile-gateway.client.js.patched'
    relPath = 'lib\client.js'
    must    = @(
      'styles\.select',
      '__DSH_MOBILE_ACCESS__',
      '__DSH_MOBILE_GATEWAY__',
      '三个按钮：先下载 App',
      '高级设置（一般用不到）',
      'setAdvancedOpen',
      'tunnelUrl\) return tunnelUrl'
    )
    mustNot = @(
      '第一次使用 · 按两步走',
      'accessOpen'
    )
  },
  @{
    title   = 'cloudflared：国内镜像回退（镜像在前、官方在后，逐个校验官方摘要）'
    srcName = 'dsh-plugin-mobile-gateway.cloudflared-binary.mjs.patched'
    relPath = 'lib\cloudflared-binary.mjs'
    must    = @(
      'MIRROR_PREFIXES',
      'gh-proxy\.com',
      'ghproxy\.net',
      'DOWNLOAD_TIMEOUT_MS',
      'Cloudflare binary download failed \(tried'
    )
    mustNot = @(
      'const expectedUrl = '
    )
  }
)

if (-not (Test-Path $pkgPath)) {
  throw "找不到网关插件：$pkgPath`n先让 DSH 装好 dsh-plugin-mobile-gateway（重启一次）再跑本脚本。"
}
$ver = (Read-Text $pkgPath | ConvertFrom-Json).version
Write-Host "网关插件版本: $ver（本补丁基线: $expectedVersion）"
if ($ver -ne $expectedVersion) {
  $msg = "网关插件版本是 $ver，与本补丁基线 $expectedVersion 不符。整文件覆盖会降级插件 —— "
  $msg += "请按本脚本头部注释手动移植，或更新同目录下的 .patched 基线后重跑。"
  throw $msg
}

$stamp = Get-Date -Format yyyyMMdd-HHmmss
$applied = @()
$skipped = @()

foreach ($patch in $patches) {
  $dst = Join-Path $gatewayDir $patch.relPath
  $src = Join-Path $PSScriptRoot $patch.srcName
  Write-Host ""
  Write-Host "== $($patch.title) =="
  if (-not (Test-Path $dst)) { throw "找不到目标文件: $dst" }
  if (-not (Test-Path $src)) { throw "找不到补丁文件: $src" }

  # 已经是补丁版就跳过（幂等）：重复跑不会层层覆盖、也不产生垃圾备份
  $before = Read-Text $dst
  $already = $true
  foreach ($pattern in $patch.must) { if ($before -notmatch $pattern) { $already = $false; break } }
  if ($already) {
    foreach ($pattern in $patch.mustNot) { if ($before -match $pattern) { $already = $false; break } }
  }
  if ($already) {
    Write-Host "  已是补丁版，跳过"
    $skipped += $patch.relPath
    continue
  }

  # 打补丁前的原文必须留一份可回退的备份（带时间戳，不覆盖历史备份）
  $bak = "$dst.bak-$expectedVersion-$stamp"
  Copy-Item $dst $bak -Force
  Write-Host "  已备份原文: $bak"

  Copy-Item $src $dst -Force

  # 落地后自检：缺任何一个标记都说明文件不完整，宁可回滚也不留一个残缺的插件
  $after = Read-Text $dst
  $problems = @()
  foreach ($pattern in $patch.must) { if ($after -notmatch $pattern) { $problems += "缺少标记 /$pattern/" } }
  foreach ($pattern in $patch.mustNot) { if ($after -match $pattern) { $problems += "残留旧内容 /$pattern/" } }
  if ($problems.Count -gt 0) {
    Copy-Item $bak $dst -Force
    throw "自检失败，已回滚原文件。问题: $($problems -join '；')"
  }
  Write-Host "  已覆盖并通过自检（$([Math]::Round((Get-Item $dst).Length / 1KB, 1)) KB）"
  $applied += $patch.relPath
}

Write-Host ""
Write-Host "=== 网关补丁处理完成 ===" -ForegroundColor Green
if ($applied.Count -gt 0) { Write-Host ("  本次覆盖: " + ($applied -join '、')) }
if ($skipped.Count -gt 0) { Write-Host ("  已是补丁版: " + ($skipped -join '、')) }
Write-Host "重启 DSH 桌面版后生效。"
