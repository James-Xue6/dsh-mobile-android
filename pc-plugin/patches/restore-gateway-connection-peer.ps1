<#
  DSH 掌上通 · 网关补丁：DSH 0.2.1-rc.2 的 Connection Peer（2026-10-19）
  ==================================================================
  用法：
      pwsh -File pc-plugin\patches\restore-gateway-connection-peer.ps1
      pwsh -File pc-plugin\patches\restore-gateway-connection-peer.ps1 -Revert
      pwsh -File pc-plugin\patches\restore-gateway-connection-peer.ps1 -Profile desktop

  ⚠️ 打完补丁**需要用户重启一次 DSH** 才生效（本脚本不重启、不杀进程）。

  问题：
      DSH 桌面端内核升级到 0.2.1-rc.2 之后，typertGateway.invoke()/stream() 入口会先
      assertPeerLive(request.peer)。网关 0.9.0 仍按旧协议调用（不带 peer），于是
      抛 Cannot read properties of undefined (reading 'signal')：
        · 手机能连上、能鉴权、hello 正常
        · 但 sessions / workspaces 一律返回 error internal
        · 手机端表现为 0 个对话 + 弹窗报错

  为什么不升级到上游 0.10.3：
      0.10.3 确实为正对新内核改过（createPeer + peer 透传），但它是**上游原版**，
      本仓库在 0.9.0 上叠的 5 套 fork 补丁（跨会话提醒 / hello 带公网地址 / 地址广播 /
      文件上传 / 子智能体寻址）它都没有。升上去等于把这些功能全丢回 0.9.0 之前。

  这个补丁做什么：
      把「peer 支持」按上游 0.10.3 的写法**最小回填**进 0.9.0：
        · dsh-host-adapter.mjs：createDshHostAdapter(gateway, peer) + 校验 peer.signal；
          invoke / 首帧 stream / session-follow / session-control / workspace-follow 全部透传 peer
        · index.mjs：inject 加 connection；apply 里 ctx.connection.createPeer({principal:{kind:'owner'}, interactions:'none'})；
          卸载时 peer.dispose()；DSH_VERSION 常量更正为 0.2.1-rc.2

  为什么用「整文件替换」而不是锚点插入：
      这两个文件已经被前面 4 个补丁改得面目全非，锚点很脆。这里直接落一份
      「0.9.0 + 全部 fork 补丁 + peer」的已验收完整文件（*.patched），
      版本不符就报错退出，绝不把插件降级、也绝不盲改。
      代价：**以后再有网关补丁，必须基于这两个 .patched 文件重新导出一次。**

  幂等：两个目标文件里都有 'local patch: DSH 0.2.1-rc.2' 标记就跳过。
#>
param(
  [string]$Profile = 'desktop',
  # 仅用于自测：直接指定网关包目录（默认取 profile 的 node_modules）
  [string]$GatewayRoot = '',
  [switch]$Revert
)

$ErrorActionPreference = 'Stop'

if (-not $GatewayRoot) {
  $GatewayRoot = Join-Path $env:USERPROFILE ".dsh\profiles\$Profile\node_modules\dsh-plugin-mobile-gateway"
}
$lib = Join-Path $GatewayRoot 'lib'
$pkgFile = Join-Path $GatewayRoot 'package.json'
$marker = 'local patch: DSH 0.2.1-rc.2'

if (-not (Test-Path $pkgFile)) { throw "找不到网关包：$GatewayRoot" }
$pkg = Get-Content $pkgFile -Raw -Encoding UTF8 | ConvertFrom-Json
Write-Host "网关目录：$GatewayRoot"
Write-Host "网关版本：$($pkg.version)"

$files = @(
  @{ Name = 'dsh-host-adapter.mjs'; Src = Join-Path $PSScriptRoot 'dsh-plugin-mobile-gateway.dsh-host-adapter.mjs.patched' },
  @{ Name = 'index.mjs';           Src = Join-Path $PSScriptRoot 'dsh-plugin-mobile-gateway.index.mjs.patched' }
)

foreach ($f in $files) { if (-not (Test-Path $f.Src)) { throw "找不到补丁文件：$($f.Src)" } }

function Find-Node {
  $candidates = @(
    'D:\AI\DSH\resources\runtime\primary-runtime\dependencies\node\bin\node.exe',
    (Join-Path $env:ProgramFiles 'nodejs\node.exe')
  )
  foreach ($c in $candidates) { if ($c -and (Test-Path $c)) { return $c } }
  $cmd = Get-Command node -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  return $null
}

if ($Revert) {
  $restored = 0
  foreach ($f in $files) {
    $target = Join-Path $lib $f.Name
    $bak = "$target.bak-connection-peer"
    if (-not (Test-Path $bak)) { Write-Host "  · $($f.Name)：没有 .bak-connection-peer，跳过"; continue }
    Copy-Item $bak $target -Force
    $text = Get-Content $target -Raw -Encoding UTF8
    if ($text -match [regex]::Escape($marker)) { throw "回退失败：$($f.Name) 里仍有 peer 标记" }
    Write-Host "  ✓ $($f.Name) 已从 .bak-connection-peer 恢复"
    $restored++
  }
  Write-Host "  ⚠️ 需重启一次 DSH 生效"
  exit 0
}

# 版本闸门：只对 0.9.0 生效（0.10.x 上游自带 peer 支持，不该被这里的旧文件覆盖）
if ($pkg.version -ne '0.9.0') {
  throw "网关版本是 $($pkg.version)，本补丁只适用于 0.9.0。版本不对请先确认这个 .patched 是否已经过时（0.10.x 上游自带 peer 支持，不需要本补丁）。"
}

$pending = @()
foreach ($f in $files) {
  $target = Join-Path $lib $f.Name
  if (-not (Test-Path $target)) { throw "找不到目标文件：$target" }
  $text = Get-Content $target -Raw -Encoding UTF8
  if ($text -match [regex]::Escape($marker)) { Write-Host "  ✓ $($f.Name) 已含 peer 补丁（幂等，跳过）"; continue }
  $pending += @{ File = $f; Target = $target }
}

if ($pending.Count -eq 0) {
  Write-Host '  ✓ 全部就位，无需改动'
  exit 0
}

# 先备份（只备份一次，-Revert 靠它）
foreach ($p in $pending) {
  $bak = "$($p.Target).bak-connection-peer"
  if (-not (Test-Path $bak)) { Copy-Item $p.Target $bak -Force; Write-Host "  已备份：$bak" }
}

# 落盘
foreach ($p in $pending) {
  Copy-Item $p.File.Src $p.Target -Force
  Write-Host "  ✓ 已写入 $($p.File.Name)"
}

# 落盘后复核：不看退出码，看内容与语法
$node = Find-Node
foreach ($p in $pending) {
  $text = Get-Content $p.Target -Raw -Encoding UTF8
  if ($text -notmatch [regex]::Escape($marker)) { throw "复核失败：$($p.File.Name) 里没有 peer 标记" }
  if ($node) {
    & $node --check $p.Target
    if ($LASTEXITCODE -ne 0) { throw "语法检查失败：$($p.File.Name)（node --check 退出码 $LASTEXITCODE）" }
    Write-Host "  ✓ $($p.File.Name) 语法检查通过"
  } else {
    Write-Host "  ! 没找到 node，跳过语法检查（请手动核对）"
  }
}

Write-Host ''
Write-Host '  ✓ Connection Peer 支持已回填（invoke/stream 全部透传 peer）'
Write-Host '  ⚠️ 需用户重启一次 DSH 生效（本脚本不重启、不杀进程）'
Write-Host '  验收：重启后手机应恢复「对话列表 + 不弹 reading ''signal''」'
