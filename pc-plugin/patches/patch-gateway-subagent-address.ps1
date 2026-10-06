<#
  DSH 掌上通 · 网关补丁：子会话（subagent）耐久寻址
  ==================================================================
  用法（工作区根目录）：
      pwsh -File pc-plugin\patches\patch-gateway-subagent-address.ps1
      pwsh -File pc-plugin\patches\patch-gateway-subagent-address.ps1 -Profile desktop

  问题：手机端打开一个子代理会话时——
      · history 回 { kind:'error', code:'session/agent-busy',
                     message:'subagent Sessions require their durable parent address' }
      · subscribe 只回一帧 subscribed，之后每 ~2s 一帧 session-stream-reset
        （retrying:true），永远收不到 session-snapshot
  同一连接换普通父会话完全正常。

  根因：dsh-plugin-mobile-gateway@0.9.0 的 lib/dsh-host-adapter.mjs 把
  「会话」一律编码成 { kind:'session', sessionId }：
      lib/dsh-host-adapter.mjs:106  history
      lib/dsh-host-adapter.mjs:149  sessionPreset（经 sessionSnapshot）
      lib/dsh-host-adapter.mjs:227  models（经 sessionSnapshot）
      lib/dsh-host-adapter.mjs:367  openSessionStream（subscribe 的流）
  而宿主（app.asar）对 origin==='subagent' 的会话直接拒绝这种地址：
      app.asar 第 338922 行 validateAddress：
        if (address.kind === 'session') {
          if (header.origin === 'subagent') throw new RemoteError(
            'session/agent-busy', 'subagent Sessions require their durable parent address', ...)
  宿主接受的 SessionAddress 只有两种（app.asar 第 331252 行声明）：
      { kind:'session', sessionId }
    | { kind:'subagent', parentSessionId, childSessionId,
        mode: 'one-shot' | 'continuable' | 'unknown' }
  其中 mode:'unknown' 是**只读**判别值：validateAddress 只对 'unknown'
  跳过 mode 比对（app.asar 第 327629 行），仍然强制 child 属于给定 parent、
  且 descriptor 可用 —— 正好是「按 id 读子会话」需要的语义。

  补丁做什么：在 createDshHostAdapter 内部加一个地址解析器，
  用 session/list（只读存储头，不打开冷会话体）建 sessionId ->
  { kind:'subagent', parentSessionId, childSessionId, mode:'unknown' } 映射：
      · 列表里 origin==='subagent' 且有 parentSessionId → 用子会话地址
      · 父会话 / 未知 id / 查不到 parent / 任何异常 → 原样回退
        { kind:'session', sessionId }（父会话路径一字不改）
  另外把 sessions.list 的返回值顺手喂进缓存，避免额外查询。

  性质：
    · 幂等（重复跑会提示已打过并退出）
    · 版本校验：网关 package.json 版本 ≠ $expectedVersion 直接报错退出，
      绝不瞎打（升级会覆盖本补丁，这是已知代价，重跑本脚本即可重放）
    · 打补丁前备份原文，文件名带时间戳
    · 落地后自检标记，缺任何一个就报错并提示用 revert 脚本恢复

  恢复：pwsh -File pc-plugin\patches\revert-gateway-subagent-address.ps1
#>
[CmdletBinding()]
param(
  [string]$Profile = 'desktop'
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
  throw "网关插件版本是 $ver，与本补丁基线 $expectedVersion 不符。本补丁是按 0.9.0 的源码行锚定的，版本变了必须先人工比对再改脚本，不能瞎打。"
}

$original = (Get-Content $target -Raw) -replace "`r`n", "`n"   # 锚点按 LF 写，检出成 CRLF 也能打
if ($original -match 'subagent durable addressing \(pc-plugin/patches\)') {
  Write-Host '目标文件已是本补丁版本，无需重复打补丁。' -ForegroundColor Yellow
  exit 0
}

# ------------------------------------------------------------------ 新增代码块
$helperBlock = @'
  // --- local patch: subagent durable addressing (pc-plugin/patches) -------
  // A Session whose header origin is 'subagent' rejects a plain session
  // address with session/agent-busy; it must be addressed by its durable
  // direct parent. Host SessionAddress:
  //   { kind: 'session', sessionId }
  // | { kind: 'subagent', parentSessionId, childSessionId,
  //     mode: 'one-shot' | 'continuable' | 'unknown' }
  // 'unknown' is the read-only discriminator: the host's validateAddress
  // skips its mode comparison for it and still enforces direct-parent
  // ownership. Parent sessions, unknown ids, and every lookup failure keep
  // the original session address, so the parent path never changes.
  const addressSessionId = address => (address && address.kind === 'subagent'
    ? address.childSessionId
    : address && address.sessionId)
  const CHILD_ADDRESS_TTL_MS = 2_000
  const childAddresses = new Map()
  let childAddressAt = 0
  let childAddressLookup
  const rememberSessionAddresses = result => {
    if (!result || !Array.isArray(result.items)) return
    for (const item of result.items) {
      if (!item || typeof item.sessionId !== 'string') continue
      if (item.origin === 'subagent' && typeof item.parentSessionId === 'string' && item.parentSessionId) {
        childAddresses.set(item.sessionId, {
          kind: 'subagent',
          parentSessionId: item.parentSessionId,
          childSessionId: item.sessionId,
          mode: 'unknown',
        })
      } else {
        childAddresses.delete(item.sessionId)
      }
    }
  }
  const refreshSessionAddresses = async () => {
    if (childAddressLookup) { await childAddressLookup.catch(() => {}); return }
    if (Date.now() - childAddressAt < CHILD_ADDRESS_TTL_MS) return
    childAddressAt = Date.now() // rate-limit failures too
    const pending = invoke('session', 'list', sessionListArgs({}), undefined)
    childAddressLookup = pending
    try {
      rememberSessionAddresses(await pending)
    } finally {
      if (childAddressLookup === pending) childAddressLookup = undefined
    }
  }
  // Never throws: an unresolvable child falls back to the original address.
  const sessionAddress = async sessionId => {
    const fallback = { kind: 'session', sessionId }
    if (typeof sessionId !== 'string' || !sessionId) return fallback
    try {
      if (!childAddresses.has(sessionId)) await refreshSessionAddresses()
      return childAddresses.get(sessionId) ?? fallback
    } catch {
      return fallback
    }
  }
  // ------------------------------------------------------------------------

'@

$replacements = @(
  @{
    Name = 'sessionSnapshot 用 addressSessionId 校验快照'
    From = "    return readSessionSnapshot(frame, request.address.sessionId)"
    To   = "    return readSessionSnapshot(frame, addressSessionId(request.address))"
  },
  @{
    Name = 'history 用子会话地址'
    From = @'
    const request = {
      address: { kind: 'session', sessionId: payload.sessionId },
'@
    To   = @'
    const request = {
      address: await sessionAddress(payload.sessionId),
'@
  },
  @{
    Name = 'sessionPreset 用子会话地址'
    From = "    const snapshot = await sessionSnapshot({ address: { kind: 'session', sessionId }, maxMessages: 1 }, signal)"
    To   = "    const snapshot = await sessionSnapshot({ address: await sessionAddress(sessionId), maxMessages: 1 }, signal)"
  },
  @{
    Name = 'models 用子会话地址'
    From = "          sessionSnapshot({ address: { kind: 'session', sessionId: payload.sessionId }, maxMessages: 1 }, signal),"
    To   = "          sessionSnapshot({ address: await sessionAddress(payload.sessionId), maxMessages: 1 }, signal),"
  },
  @{
    Name = 'sessions.list 顺手喂地址缓存'
    From = "      list: (payload = {}, signal) => invoke('session', 'list', sessionListArgs(payload), signal),"
    To   = @'
      list: async (payload = {}, signal) => {
        const result = await invoke('session', 'list', sessionListArgs(payload), signal)
        // Warm the subagent address cache from the list the client just asked for.
        rememberSessionAddresses(result)
        return result
      },
'@
  },
  @{
    Name = 'openSessionStream 用子会话地址（subscribe）'
    From = @'
    openSessionStream(sessionId, signal) {
      return typertGateway.stream({
        namespace: 'session', method: 'follow',
        // Bound the opening window at the Host; older records remain available via session.page.
        args: requestArgs({ address: { kind: 'session', sessionId }, maxMessages: 12, assistantStream: true }),
        signal,
      })
    },
'@
    To   = @'
    async openSessionStream(sessionId, signal) {
      return typertGateway.stream({
        namespace: 'session', method: 'follow',
        // Bound the opening window at the Host; older records remain available via session.page.
        args: requestArgs({ address: await sessionAddress(sessionId), maxMessages: 12, assistantStream: true }),
        signal,
      })
    },
'@
  }
)

# 先把新增代码块插到 sessionSnapshot 定义之前
$insertAnchor = "  const sessionSnapshot = async (request, signal) => {"
$hits = ([regex]::Matches($original, [regex]::Escape($insertAnchor))).Count
if ($hits -ne 1) { throw "插入锚点出现 $hits 次（应为 1 次），源码已变，停止打补丁。" }
$helperBlock = $helperBlock -replace "`r`n", "`n"
$patched = $original.Replace($insertAnchor, $helperBlock + $insertAnchor)

foreach ($item in $replacements) {
  $from = $item.From -replace "`r`n", "`n"
  $to = $item.To -replace "`r`n", "`n"
  $count = ([regex]::Matches($patched, [regex]::Escape($from))).Count
  if ($count -ne 1) {
    throw "锚点「$($item.Name)」在源码里出现 $count 次（应为 1 次），源码已变，停止打补丁。"
  }
  $patched = $patched.Replace($from, $to)
}

if ($patched -eq $original) { throw '补丁没有产生任何改动，异常，停止。' }

# ------------------------------------------------------------------ 备份 + 落盘
$bak = "$target.bak-subagent-addr-$expectedVersion-$(Get-Date -Format yyyyMMdd-HHmmss)"
Copy-Item $target $bak -Force
Write-Host "已备份原文（带时间戳）: $bak"

# 原子替换，避免半截写入
$tmp = "$target.tmp-$PID"
Set-Content -Path $tmp -Value $patched -NoNewline -Encoding utf8
Move-Item $tmp $target -Force

# ------------------------------------------------------------------ 自检
$after = Get-Content $target -Raw
$missing = @()
foreach ($marker in @(
  'subagent durable addressing \(pc-plugin/patches\)',
  'const sessionAddress = async sessionId =>',
  'address: await sessionAddress\(payload\.sessionId\),',
  'address: await sessionAddress\(sessionId\), maxMessages: 1',
  'address: await sessionAddress\(sessionId\), maxMessages: 12, assistantStream: true',
  'readSessionSnapshot\(frame, addressSessionId\(request\.address\)\)',
  'async openSessionStream\(sessionId, signal\)'
)) {
  if ($after -notmatch $marker) { $missing += $marker }
}
if ($after -match "sessionId: payload\.sessionId \}") { $missing += '残留的旧 sessionId 地址构造' }
if ($missing.Count -gt 0) {
  throw "打补丁后自检失败，缺: $($missing -join '、')。原文备份在 $bak，用 revert 脚本恢复。"
}

Write-Host '✅ 子会话耐久寻址补丁已就位（history / subscribe / sessionPreset / models 四处地址构造）。' -ForegroundColor Green
Write-Host '   需要重启 DSH 桌面版（或让插件热重载）后生效；升级网关插件会覆盖本补丁，重跑本脚本即可重放。'
