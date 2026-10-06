<#
  DSH 掌上通 · 网关补丁：跨会话提醒（放宽两道 waterfall 门 + 跨会话下发）
  ==================================================================
  用法（任意位置）：
      pwsh -File pc-plugin\patches\patch-gateway-crosssession.ps1
      pwsh -File pc-plugin\patches\patch-gateway-crosssession.ps1 -Revert
      pwsh -File pc-plugin\patches\patch-gateway-crosssession.ps1 -Profile desktop
      pwsh -File pc-plugin\patches\patch-gateway-crosssession.ps1 -Target <index.mjs 路径>   # 离线验证用

  ⚠️ 打完补丁**需要用户重启一次 DSH** 才生效（本脚本不重启、不杀进程）。

  问题（真机根因，2026-10-04 定位）：
      手机停在会话 A 时，会话 B 的**提问/审批**会被整条拦掉 —— 不建档、不生成帧、
      连 `question requested` 日志都没有。原因是网关的两道 waterfall 门用了
      `hasInteractionClient(sessionId)`：它要求"有连接**未订阅**或恰好订阅了该会话"，
      而手机此刻正订阅着 A ⇒ 门不过。用户表现：**跨会话的卡片和通知都收不到**。

  改动（4 处，都在 lib/index.mjs）：
      A1. 跨会话下发：`filterSessionId` 不匹配的连接**不再跳过**，改为照样下发，
          但给帧打 `global: true`（客户端据此只做提醒、不把它当当前会话的卡片）。
      A2. 新增放宽版判据 `hasAnyInteractionClient()`：只要有"非 conversation 通道"的
          已连接客户端即可，不要求它未订阅。
      A3. 提问门：`hasInteractionClient(sessionId)` → `hasAnyInteractionClient()`
      A4. 审批门：同上

  ⚠️ 与其它补丁的关系：本脚本**不用备份还原做回退**，而是把 4 处替换反向做回去。
      因为 `lib/index.mjs.bak-crosssession` 是**最早**那份原文，直接还原会把
      hello-publicurl / route-broadcast / file-upload 几个补丁一起抹掉。
#>
[CmdletBinding()]
param(
  [string]$Profile = 'desktop',
  [switch]$Revert,
  [string]$Target = ''
)

$ErrorActionPreference = 'Stop'

# 目标文件是 LF-only；锚点与插入都必须用 LF，混进 CRLF 会匹配不上（其它补丁同理）。
$LF = [string][char]10
$marker = 'dsh-mobile:crosssession'
$funcMarker = 'hasAnyInteractionClient'

if ([string]::IsNullOrWhiteSpace($Target)) {
  $Target = Join-Path $env:USERPROFILE ".dsh\profiles\$Profile\node_modules\dsh-plugin-mobile-gateway\lib\index.mjs"
}
if (-not (Test-Path $Target)) { throw "找不到网关文件：$Target" }

$text = Get-Content $Target -Raw -Encoding UTF8
$hasFunc = $text -match [regex]::Escape($funcMarker)

# ---------------------------------------------------------------- 原文锚点（各必须唯一）
$a1 = '        if (client.filterSessionId && client.filterSessionId !== frame.sessionId) continue' + $LF +
      '        if (client.readyState === 1) client.send(wire)'
$a2 = '    const hasInteractionClient = (sessionId) => [...clients].some((client) => ('
$a3 = '      if (!sessionId || !Array.isArray(request.questions) || !hasInteractionClient(sessionId)) return next()'
$a4 = "      if (!sessionId || typeof request.toolName !== 'string' || !hasInteractionClient(sessionId)) return next()"

# ---------------------------------------------------------------- 替换后的文本
$r1 = (@(
  '        // [跨会话提醒] 原实现把"订阅了别的会话"的连接直接跳过，导致手机上永远看不到',
  '        // 别的会话的提问/审批（真机实测：卡片与通知都收不到）。这里改为**照样下发**，',
  '        // 但打上 global 标记；客户端据此只做提醒、不把它当当前会话的卡片。',
  ('        // ' + $marker),
  '        const otherSession = client.filterSessionId && client.filterSessionId !== frame.sessionId',
  '        if (client.readyState === 1) {',
  '          client.send(otherSession ? stringifyWireFrame({ ...frame, global: true }) : wire)',
  '        }'
)) -join $LF

$helper = (@(
  '    // [跨会话提醒] 只给两道 waterfall 门用的放宽版：只要有"非 conversation 通道"的已连接客户端即可，',
  '    // 不要求它未订阅或恰好订阅了该会话 —— 否则手机订阅了别的会话时，提问/审批会被整条拦掉',
  '    // （真机根因：手机停在会话 A、提问发生在会话 B → 门不过 → 不建档、不生成帧、无 question requested 日志）。',
  ('    // ' + $marker + ' (helper)'),
  '    const hasAnyInteractionClient = () => [...clients].some((client) => (',
  "      client.readyState === 1 && client.mobileChannel !== 'conversation'",
  '    ))',
  ''
)) -join $LF

$r3 = '      if (!sessionId || !Array.isArray(request.questions) || !hasAnyInteractionClient()) return next()'
$r4 = "      if (!sessionId || typeof request.toolName !== 'string' || !hasAnyInteractionClient()) return next()"

# ---------------------------------------------------------------- 回退（反向替换，不动备份）
if ($Revert) {
  if (-not $hasFunc) {
    Write-Host '  没有找到功能标记（hasAnyInteractionClient），无需回退'
    exit 0
  }
  $back = $text.Replace($r1, $a1).Replace($r3, $a3).Replace($r4, $a4).Replace($helper + $a2, $a2)
  if ($back -eq $text) { throw '回退没有生效（替换文本没匹配上？请手工核对）' }
  Set-Content -Path $Target -Value $back -Encoding UTF8 -NoNewline
  $chk = Get-Content $Target -Raw -Encoding UTF8
  if ($chk -match [regex]::Escape($funcMarker)) { throw '回退失败：hasAnyInteractionClient 仍在' }
  Write-Host '  ✓ 已回退跨会话补丁（反向替换，未动其它补丁）'
  Write-Host '  ⚠️ 需重启一次 DSH 生效'
  exit 0
}

# ---------------------------------------------------------------- 幂等
if ($hasFunc) {
  Write-Host '  ✓ 跨会话补丁已在位（hasAnyInteractionClient 已存在），跳过'
  exit 0
}

# ---------------------------------------------------------------- 预检：锚点必须唯一
foreach ($a in @(
  @('A1 跨会话下发', $text, $a1),
  @('A2 helper 插入点', $text, $a2),
  @('A3 提问门', $text, $a3),
  @('A4 审批门', $text, $a4)
)) {
  $n = ([regex]::Matches($a[1], [regex]::Escape($a[2]))).Count
  if ($n -ne 1) { throw "锚点 $($a[0]) 出现 $n 次（期望 1 次），拒绝盲改：`n$a[2]" }
}

# ---------------------------------------------------------------- 应用
$new = $text.Replace($a1, $r1)
$new = $new.Replace($a3, $r3)
$new = $new.Replace($a4, $r4)
$new = $new.Replace($a2, $helper + $a2)

if ($new -eq $text) { throw '替换没有生效（锚点未变？）' }
Set-Content -Path $Target -Value $new -Encoding UTF8 -NoNewline

# ---------------------------------------------------------------- 复核（只看"没报错"不算验证）
$v = Get-Content $Target -Raw -Encoding UTF8
if (([regex]::Matches($v, [regex]::Escape($funcMarker))).Count -ne 3) {
  throw "复核失败：hasAnyInteractionClient 应出现 3 次（1 处定义 + 2 处调用），实际 $(([regex]::Matches($v, [regex]::Escape($funcMarker))).Count) 次"
}
if (([regex]::Matches($v, [regex]::Escape('hasInteractionClient(sessionId)) return next()'))).Count -ne 0) {
  throw '复核失败：旧的两道门仍在（hasInteractionClient(sessionId)）'
}
if (([regex]::Matches($v, [regex]::Escape($marker))).Count -lt 2) { throw '复核失败：标记没写进去' }

Write-Host '  ✓ 已给网关加跨会话提醒（放宽两道门 + 跨会话下发带 global 标记）'
Write-Host '  ⚠️ 需用户重启一次 DSH 生效（本脚本不重启、不杀进程）'
Write-Host '  ⚠️ 回退用 -Revert（反向替换，不会动其它补丁）'
