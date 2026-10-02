<#
  DSH 掌上通 · 网关补丁回退：撤销「子会话耐久寻址」补丁
  ==================================================================
  用法（工作区根目录）：
      pwsh -File pc-plugin\patches\revert-gateway-subagent-address.ps1
      pwsh -File pc-plugin\patches\revert-gateway-subagent-address.ps1 -Backup "<指定备份路径>"

  行为：从 patch-gateway-subagent-address.ps1 留下的、带时间戳的备份里，
  把 lib/dsh-host-adapter.mjs 恢复成原样；仍然是版本校验 + 落地自检。
  没打过补丁时直接提示并退出（不动文件）。
  回退前会把当前（已打补丁的）文件另存一份 .bak-patched-<时间戳>，便于再回到补丁态。
#>
[CmdletBinding()]
param(
  [string]$Profile = 'desktop',
  [string]$Backup = ''
)

$ErrorActionPreference = 'Stop'
$expectedVersion = '0.9.0'

$pluginDir = Join-Path $env:USERPROFILE ".dsh\profiles\$Profile\node_modules\dsh-plugin-mobile-gateway"
$target = Join-Path $pluginDir 'lib\dsh-host-adapter.mjs'
$pkg = Join-Path $pluginDir 'package.json'

if (-not (Test-Path $target)) { throw "找不到目标文件: $target" }
if (-not (Test-Path $pkg)) { throw "找不到网关插件的 package.json: $pkg" }

$ver = (Get-Content $pkg -Raw | ConvertFrom-Json).version
Write-Host "网关插件版本: $ver（本补丁基线: $expectedVersion）"
if ($ver -ne $expectedVersion) {
  throw "网关插件版本是 $ver，与本补丁基线 $expectedVersion 不符。回退脚本按 0.9.0 备份命名匹配，版本变了请用 -Backup 显式指定备份文件。"
}

$current = Get-Content $target -Raw
if ($current -notmatch 'subagent durable addressing \(pc-plugin/patches\)') {
  Write-Host '目标文件没有本补丁的标记，无需回退。' -ForegroundColor Yellow
  exit 0
}

if (-not $Backup) {
  $candidates = @(Get-ChildItem "$target.bak-subagent-addr-$expectedVersion-*" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending)
  if ($candidates.Count -eq 0) {
    throw "找不到备份文件（$target.bak-subagent-addr-$expectedVersion-*）。无法回退，请手工处理或用 -Backup 指定。"
  }
  $Backup = $candidates[0].FullName
}
if (-not (Test-Path $Backup)) { throw "指定/选中的备份不存在: $Backup" }

$restored = (Get-Content $Backup -Raw) -replace "`r`n", "`n"
if ($restored -match 'subagent durable addressing \(pc-plugin/patches\)') {
  throw "备份 $Backup 本身就是补丁版本，不能用来回退。请换一个更早的备份。"
}
if ($restored -notmatch 'address: \{ kind: ''session'', sessionId: payload\.sessionId \},') {
  throw "备份 $Backup 不像 0.9.0 的原文（缺少原始 address 构造），拒绝回退。"
}

$baked = "$target.bak-patched-$(Get-Date -Format yyyyMMdd-HHmmss)"
Copy-Item $target $baked -Force
Write-Host "已另存当前补丁态（想再切回来用它）: $baked"

$tmp = "$target.tmp-$PID"
Set-Content -Path $tmp -Value $restored -NoNewline -Encoding utf8
Move-Item $tmp $target -Force

$after = Get-Content $target -Raw
if ($after -match 'subagent durable addressing \(pc-plugin/patches\)') { throw '回退后仍带补丁标记，异常，请手工检查。' }
if ($after -notmatch 'address: \{ kind: ''session'', sessionId: payload\.sessionId \},') { throw '回退后找不到原始 address 构造，异常，请手工检查。' }

Write-Host "✅ 已回退到原文（来源: $Backup）。重启 DSH 桌面版后生效。" -ForegroundColor Green
