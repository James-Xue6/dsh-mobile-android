# 出 README 用的界面示意图：无头 Chrome 渲染设计稿 → 压成适合网页的 JPEG。
# 用法：pwsh -File .\tools\make-readme-shots.ps1
# 说明：图来自 design/ui-demo-glass.html 的 ?shot=1&scene=readme 模式（只出手机画面、
# 文案与路径已换成占位符），不是真机截图 —— 真机截图含设备名/内网地址，不入库。
$ErrorActionPreference = 'Stop'
# [2026-10-07 合并适配] 原文件写死朋友那台机器的路径（L:\DSH日常问题\…）；
# 改成按脚本自身位置推导，谁克隆下来都能跑。
$root = Split-Path $PSScriptRoot -Parent
$design = Join-Path $root 'design\ui-demo-glass.html'
$outDir = Join-Path $root 'screenshots'
$chrome = @(
  "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
  "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
  "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe",
  "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $chrome) { throw '找不到 Chrome / Edge（出图需要）' }

# 让 Chrome 能读本机文件：file:/// + 路径里的中文要百分号编码
$enc = [System.Uri]::EscapeUriString(($design -replace '\\', '/'))
$base = 'file:///' + $enc
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$tmp = Join-Path $env:TEMP ('dsh-shots-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

# 压缩用 Pillow（本机 Python）：优先用 DSH 自带 runtime 的 python（一定带 Pillow），
# 其次 C:\Python314，最后 LOCALAPPDATA 下的用户级安装。
$python = @(
  "$env:USERPROFILE\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe",
  'C:\Python314\python.exe',
  "$env:LOCALAPPDATA\Programs\Python\Python313\python.exe",
  "$env:LOCALAPPDATA\Programs\Python\Python312\python.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1

# 原生工具（chrome / python）会把「已写入 N 字节」这类提示写进 stderr，而 PowerShell 5.1
# 在 $ErrorActionPreference='Stop' 下会把它当致命错误 —— 调用期间局部放宽，只看退出码。
# （与 build.ps1 里 Invoke-Tool 同一招，2026-10-06 出图时又踩了一次）
function Invoke-Native([string]$exe, [string[]]$argList, [string]$what) {
  $prev = $ErrorActionPreference
  $code = 1
  try {
    $ErrorActionPreference = 'Continue'
    & $exe @argList 2>&1 | Out-Null
    $code = $LASTEXITCODE
  } finally { $ErrorActionPreference = $prev }
  if ($code -ne 0) { throw "$what 失败 (exit $code)" }
}

try {
  foreach ($v in @(
      @{ n = 'app-dark';  q = 'shot=1&scene=readme&theme=dark&palette=aurora' },
      @{ n = 'app-light'; q = 'shot=1&scene=readme&theme=light&palette=aurora' })) {
    $png = Join-Path $tmp ($v.n + '.png')
    Invoke-Native $chrome @(
      '--headless=new', '--disable-gpu', '--hide-scrollbars', '--no-first-run', '--no-default-browser-check',
      "--user-data-dir=$tmp\prof", '--force-device-scale-factor=2', '--window-size=1400,900',
      '--virtual-time-budget=5000', "--screenshot=$png", "$base`?$($v.q)"
    ) "渲染 $($v.n)"
    if (-not (Test-Path $png)) { throw "$($v.n) 没出图（Chrome 渲染失败）" }
    $kb = [math]::Round((Get-Item $png).Length / 1KB, 1)
    Write-Host ("  渲染 {0}: {1} KB（2x）" -f $v.n, $kb)
  }

  # 压成网页友好的尺寸与体积（README 上显示宽度 ~880px，1760px 够清晰）
  if (Test-Path $python) {
    $py = @'
import sys, os
from PIL import Image
src, dst = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB")
if im.width > 1760:
    im = im.resize((1760, round(im.height * 1760 / im.width)), Image.LANCZOS)
im.save(dst, "JPEG", quality=90, optimize=True, progressive=True)
print("  ->", os.path.basename(dst), round(os.path.getsize(dst)/1024, 1), "KB", im.size)
'@
    $pyFile = Join-Path $tmp 'shrink.py'
    [System.IO.File]::WriteAllText($pyFile, $py, (New-Object System.Text.UTF8Encoding($false)))
    foreach ($n in @('app-dark', 'app-light')) {
      Invoke-Native $python @($pyFile, (Join-Path $tmp ($n + '.png')), (Join-Path $outDir ($n + '.jpg'))) "压缩 $n"
      Write-Host ("  -> {0}.jpg {1} KB" -f $n, [math]::Round((Get-Item (Join-Path $outDir ($n + '.jpg'))).Length / 1KB, 1))
    }
  } else {
    Write-Warning "没找到 Python/Pillow：直接把 2x PNG 拷过去（体积偏大）"
    foreach ($n in @('app-dark', 'app-light')) { Copy-Item (Join-Path $tmp ($n + '.png')) $outDir -Force }
  }
} finally {
  Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
}
Write-Host "`n出图完成：" -ForegroundColor Green
Get-ChildItem $outDir | ForEach-Object { "  {0}  {1} KB" -f $_.Name, [math]::Round($_.Length / 1KB, 1) }
