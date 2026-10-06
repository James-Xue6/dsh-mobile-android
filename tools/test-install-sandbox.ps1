# install.ps1 的沙箱自测：用一个「假的家目录」跑完整安装流程，验证：
#   · 不会写坏 profile 里的中文路径（PS 5.1 默认按 ANSI 读 UTF-8 的老问题）
#   · 写出的 JSON / YAML / version.txt 都是合法且不带 BOM 的
#   · 依赖与 bundles 登记正确
# 跑法（PowerShell 7 或 Windows PowerShell 5.1 均可）：
#     pwsh   -File .\tools\test-install-sandbox.ps1
#     powershell -File .\tools\test-install-sandbox.ps1
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$installScript = Join-Path $repoRoot 'pc-plugin\install.ps1'
$utf8 = New-Object System.Text.UTF8Encoding($false)
function Read-Text([string]$p) { return [System.IO.File]::ReadAllText($p, $utf8) }
function Test-Bom([string]$p) {
  $b = [System.IO.File]::ReadAllBytes($p)
  return ($b.Length -ge 3 -and $b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF)
}

$passed = 0
$failed = @()
function Assert-That([string]$name, [bool]$ok, [string]$detail = '') {
  if ($ok) { $script:passed++; Write-Host "  OK  $name" -ForegroundColor Green }
  else { $script:failed += $name; Write-Host "  FAIL $name $(if ($detail) { "— $detail" })" -ForegroundColor Red }
}

# ── 准备假家目录 ────────────────────────────────────────────────────────────
$sandbox = Join-Path $env:TEMP ("dsh-install-sandbox-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$fakeHome = Join-Path $sandbox 'home'
$profileDir = Join-Path $fakeHome '.dsh\profiles\test'
New-Item -ItemType Directory -Force -Path $profileDir | Out-Null

# 假 profile 的 package.json：故意放中文路径（就是真实 profile 里那种 link:L:/DSH自制插件/...）
$seedPkg = @'
{
  "name": "dsh-profile-test",
  "private": true,
  "dependencies": {
    "dsh-chat-bubble": "link:L:/DSH自制插件/dsh-chat-bubble",
    "dsh-中文插件-测试": "link:L:/DSH自制插件/中文目录名"
  },
  "dsh": {
    "profile": {
      "bundles": [
        "@deepseek-ai/dsh-base",
        "dsh-chat-bubble"
      ]
    }
  }
}
'@
[System.IO.File]::WriteAllText((Join-Path $profileDir 'package.json'), $seedPkg, $utf8)

$seedPatch = @'
# 测试用 patch 层（含中文注释，验证追加时不会破坏原文）
- id: ui-theme
  config:
    preference: dark
'@
[System.IO.File]::WriteAllText((Join-Path $profileDir 'cordis.patch.yml'), $seedPatch, $utf8)

Write-Host "沙箱: $sandbox"
Write-Host "`n== 运行 install.ps1（模拟用户实际调用方式）==" -ForegroundColor Cyan

$previousHome = $env:USERPROFILE
try {
  $env:USERPROFILE = $fakeHome
  $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $installScript -Profile test 2>&1
  $exit = $LASTEXITCODE
} finally {
  $env:USERPROFILE = $previousHome
}
Write-Host ($output | Out-String)

Write-Host "== 断言 ==" -ForegroundColor Cyan
Assert-That '安装脚本正常结束（退出码 0）' ($exit -eq 0) "exit=$exit"

$pkgPath = Join-Path $profileDir 'package.json'
$patchPath = Join-Path $profileDir 'cordis.patch.yml'
$verPath = Join-Path $fakeHome '.dsh\local-plugins\dsh-mobile-access\app\version.txt'

Assert-That 'package.json 仍存在' (Test-Path $pkgPath)
Assert-That 'cordis.patch.yml 仍存在' (Test-Path $patchPath)
Assert-That '插件已复制到假家目录' (Test-Path (Join-Path $fakeHome '.dsh\local-plugins\dsh-mobile-access\index.js'))

if (Test-Path $pkgPath) {
  $rawPkg = Read-Text $pkgPath
  Assert-That 'package.json 无 BOM' (-not (Test-Bom $pkgPath))
  Assert-That '中文路径原样保留在文件里（未被写成 \uXXXX）' ($rawPkg -match 'DSH自制插件' -and $rawPkg -notmatch '\\u81ea')
  $pkg = $null
  try { $pkg = $rawPkg | ConvertFrom-Json; Assert-That 'package.json 是合法 JSON' $true }
  catch { Assert-That 'package.json 是合法 JSON' $false $_.Exception.Message }
  if ($pkg) {
    Assert-That '原有中文依赖值未被写坏' ($pkg.dependencies.'dsh-chat-bubble' -eq 'link:L:/DSH自制插件/dsh-chat-bubble') "实际: $($pkg.dependencies.'dsh-chat-bubble')"
    Assert-That '原有中文「目录名」依赖未被写坏' ($pkg.dependencies.'dsh-中文插件-测试' -eq 'link:L:/DSH自制插件/中文目录名') "实际: $($pkg.dependencies.'dsh-中文插件-测试')"
    Assert-That '登记了 dsh-mobile-access 依赖' ($pkg.dependencies.'dsh-mobile-access' -like 'link:*local-plugins/dsh-mobile-access') "实际: $($pkg.dependencies.'dsh-mobile-access')"
    Assert-That '登记了网关依赖 0.9.0' ($pkg.dependencies.'dsh-plugin-mobile-gateway' -eq '0.9.0')
    Assert-That 'bundles 里加入了 dsh-mobile-access' (@($pkg.dsh.profile.bundles) -contains 'dsh-mobile-access')
    Assert-That 'bundles 里的网关残留被摘掉' (-not (@($pkg.dsh.profile.bundles) -contains 'dsh-plugin-mobile-gateway'))
    Assert-That '原有 bundle 保留' (@($pkg.dsh.profile.bundles) -contains 'dsh-chat-bubble')
  }
}

if (Test-Path $patchPath) {
  $rawPatch = Read-Text $patchPath
  Assert-That 'cordis.patch.yml 无 BOM' (-not (Test-Bom $patchPath))
  Assert-That '原有中文注释未被破坏' ($rawPatch -match '测试用 patch 层（含中文注释')
  Assert-That '已追加 mobile-gateway 配置' ($rawPatch -match 'id:\s*mobile-gateway')
  Assert-That '追加的 lanPort 为 3091' ($rawPatch -match 'lanPort:\s*3091')
  Assert-That '追加块的中文注释可读' ($rawPatch -match 'DSH 掌上通：手机端网关')
}

Assert-That 'version.txt 已写入' (Test-Path $verPath)
if (Test-Path $verPath) {
  $ver = Read-Text $verPath
  Assert-That 'version.txt 无 BOM' (-not (Test-Bom $verPath))
  # 期望版本号从 AndroidManifest 现读（install.ps1 就是照它写 version.txt 的）。
  # 不写死：发版改版本号时这里跟着对，而不是变成一条假失败。
  $manifestPath = Join-Path $repoRoot 'AndroidManifest.xml'
  $expectVer = [regex]::Match((Read-Text $manifestPath), 'android:versionName="([^"]+)"').Groups[1].Value.Trim()
  Assert-That "version.txt 内容与 APK 版本一致（$expectVer）" ($ver.Trim() -eq $expectVer) "实际: '$ver'"
  Assert-That 'version.txt 无多余空白/换行' ($ver -eq $ver.Trim())
}

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
