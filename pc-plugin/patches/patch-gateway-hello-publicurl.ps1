<#
  DSH 掌上通 · 网关补丁：hello 帧带上「电脑当前的公网(隧道)地址」
  ==================================================================
  用法（工作区根目录，或任意位置）：
      pwsh -File pc-plugin\patches\patch-gateway-hello-publicurl.ps1
      pwsh -File pc-plugin\patches\patch-gateway-hello-publicurl.ps1 -Revert
      pwsh -File pc-plugin\patches\patch-gateway-hello-publicurl.ps1 -Profile desktop

  ⚠️ 打完补丁**需要用户重启一次 DSH** 才生效（本脚本不会、也不许去杀 DSH 进程）。

  问题（方案 5 · A 第 1 层）：
      手机端的公网地址是配对时写死的；Cloudflare 快速隧道**每次重启都会换域名** ✗
      → 用户出门就连不上，只能回家重新扫码 ✗
      用户要的是：「DSH 每次启动发一次，手机一连上就同步」✓

  现状（已查证，不是猜的）：
      dsh-plugin-mobile-gateway 的 `hello` 帧只带
        { gatewayId, gatewayName, protocol, dshVersion, historyFormatVersion, capabilities }
      （lib/index.mjs:3011-3034 + gatewayIdentity 定义在 :2191）
      —— **不含** publicUrl ✗；而 /mgw/status 里有（lib/index.mjs:2659-2661）：
        publicUrl: tunnel?.snapshot().enabled && tunnel.snapshot().mode === 'quick'
          ? tunnel.snapshot().publicUrl
          : tunnel?.snapshot().publicUrl || configuredPublicUrl() || null

  补丁做什么：
      在 hello 帧里插入同一个 publicUrl 表达式（与 /mgw/status **同源** ✓）。
      → 手机**每次连上**（无论内网/公网、无论何时）握手时就能拿到电脑当前的公网地址
      → App 侧 onHello 里对比 store.wanUrl()，不一致就更新 ✓
      → 「DSH 启动后手机一连上就自动同步」天然成立 ✓（不需要推送、不需要轮询）

  幂等：文件里已有标记行就不重复插入；-Revert 按标记整段摘除。
#>
param(
  [string]$Profile = 'desktop',
  [switch]$Revert
)

$ErrorActionPreference = 'Stop'

$marker = 'dsh-mobile:hello-publicurl'
$target = Join-Path $env:USERPROFILE ".dsh\profiles\$Profile\node_modules\dsh-plugin-mobile-gateway\lib\index.mjs"

if (-not (Test-Path $target)) { throw "找不到网关文件：$target" }
Write-Host "网关文件：$target"

$text = Get-Content $target -Raw -Encoding UTF8

# 与 /mgw/status 完全同一套取值（含 quick 模式的特殊处理），保证两处永远一致
$expr = @"
            // $marker
            publicUrl: tunnel?.snapshot().enabled && tunnel.snapshot().mode === 'quick'
              ? tunnel.snapshot().publicUrl
              : tunnel?.snapshot().publicUrl || configuredPublicUrl() || null,
"@

if ($Revert) {
  if ($text -notmatch [regex]::Escape($marker)) {
    Write-Host '  没有找到标记，无需回退（文件未被本补丁改过）'
    exit 0
  }
  $bak = "$target.bak-hello-publicurl"
  if (-not (Test-Path $bak)) { throw "找不到备份 $bak，拒绝盲目回退（请手工核对）" }
  Copy-Item $bak $target -Force
  $after = Get-Content $target -Raw -Encoding UTF8
  if ($after -match [regex]::Escape($marker)) { throw '回退失败：标记仍在' }
  Write-Host '  ✓ 已回退到补丁前（从 .bak-hello-publicurl 恢复）'
  Write-Host '  ⚠️ 需重启一次 DSH 生效'
  exit 0
}

if ($text -match [regex]::Escape($marker)) {
  Write-Host '  ✓ 已经打过这个补丁（幂等，未重复插入）'
  exit 0
}

# 锚点：hello 帧的 kind 行（全库唯一）
$anchor = "kind: 'hello',"
$count = ([regex]::Matches($text, [regex]::Escape($anchor))).Count
if ($count -ne 1) { throw "锚点 'kind: ''hello'',' 出现 $count 次（期望 1 次），拒绝盲改" }

# 先备份（只备份一次；回退靠它）
$bak = "$target.bak-hello-publicurl"
if (-not (Test-Path $bak)) { Copy-Item $target $bak -Force; Write-Host "  已备份：$bak" }

$new = $text.Replace($anchor, $anchor + "`r`n" + $expr)
if ($new -eq $text) { throw '替换没有生效（锚点未变？）' }
Set-Content -Path $target -Value $new -Encoding UTF8 -NoNewline

# 落盘后复核：只看"没报错"不算验证
$verify = Get-Content $target -Raw -Encoding UTF8
if ($verify -notmatch [regex]::Escape($marker)) { throw '复核失败：标记没写进去' }
if (([regex]::Matches($verify, [regex]::Escape($anchor))).Count -ne 1) { throw '复核失败：锚点数量异常' }
Write-Host '  ✓ 已在 hello 帧插入 publicUrl（与 /mgw/status 同源）'
Write-Host '  ⚠️ 需用户重启一次 DSH 生效（本脚本不重启、不杀进程）'
Write-Host ''
Write-Host '  App 侧行为：onHello 里对比 store.wanUrl()，不一致就更新 + 轻提示 + 诊断轨迹'
