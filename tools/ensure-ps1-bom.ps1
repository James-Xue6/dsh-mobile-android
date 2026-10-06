# 给仓库里所有 .ps1 补齐 UTF-8 BOM（缺了就加，已有则不动）。
#
# 为什么需要：Windows PowerShell 5.1 读「无 BOM 的 UTF-8」脚本时按 ANSI 代码页解码，
# 中文会变乱码，严重时连引号都配不上、直接语法错误（build.ps1 就踩过这个）。
# 加了 BOM 之后，PowerShell 5.1 与 7 都能正确识别为 UTF-8。
#
# 什么时候跑：改完任何 .ps1 之后（编辑器/工具可能会顺手把 BOM 去掉）、提交之前。
#     powershell -File .\tools\ensure-ps1-bom.ps1
#     powershell -File .\tools\ensure-ps1-bom.ps1 -Check    # 只检查，不改（CI/提交前用）
[CmdletBinding()]
param(
  [switch]$Check
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$utf8Bom = New-Object System.Text.UTF8Encoding($true)

$files = @(Get-ChildItem $repoRoot -Recurse -File -Filter '*.ps1' |
  Where-Object { $_.FullName -notmatch '\\node_modules\\|\\.buildstage\\' })

$missing = @()
$already = 0
foreach ($file in $files) {
  $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
  $hasBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
  $rel = $file.FullName.Substring($repoRoot.Length + 1)
  if ($hasBom) { $already++; continue }
  $missing += $rel
  if (-not $Check) {
    $text = [System.IO.File]::ReadAllText($file.FullName, $utf8NoBom)
    [System.IO.File]::WriteAllText($file.FullName, $text, $utf8Bom)
    Write-Host "  已补 BOM: $rel"
  }
}

Write-Host ""
Write-Host "扫描 $($files.Count) 个 .ps1：已有 BOM $already 个，缺 $($missing.Count) 个"
if ($missing.Count -gt 0 -and $Check) {
  Write-Host "以下文件缺少 UTF-8 BOM（Windows PowerShell 5.1 会解析出错）：" -ForegroundColor Red
  $missing | ForEach-Object { Write-Host "  · $_" -ForegroundColor Red }
  Write-Host "跑一次不带 -Check 的同一脚本即可修复。" -ForegroundColor Yellow
  exit 1
}

# 加完 BOM 顺手做一次解析自检：能解析 = 5.1 至少不会因为编码而报语法错
$parseFailed = @()
foreach ($file in $files) {
  $tokens = $null
  $errors = $null
  $null = [System.Management.Automation.Language.Parser]::ParseFile($file.FullName, [ref]$tokens, [ref]$errors)
  if ($errors.Count -gt 0) { $parseFailed += ($file.Name + '（' + $errors.Count + ' 处）') }
}
if ($parseFailed.Count -gt 0) {
  Write-Host "解析自检未通过：$($parseFailed -join '、')" -ForegroundColor Red
  exit 1
}
Write-Host "解析自检通过（当前 shell $($PSVersionTable.PSVersion)）" -ForegroundColor Green
