// DSH 掌上通 · 子会话（subagent）寻址协议级探针
// ------------------------------------------------------------------
// 目的：不依赖手机、不依赖 App，直接用一次性配对凭证连真实网关，
//       对「同一个子会话」发 history + subscribe，把回帧打出来。
//       用来做 A/B：改前 `session/agent-busy` + 0 帧 snapshot；
//       改后 history 有事件 + subscribe 收到 session-snapshot。
//
// 用法（工作区根目录）：
//   node tools\subagent-address-probe.mjs                 # 自动挑一个子会话
//   node tools\subagent-address-probe.mjs --session <id>  # 指定会话（父/子都行，用于回归）
//   node tools\subagent-address-probe.mjs --wait 8000     # subscribe 采样时长
//
// 安全：不往任何被跟踪文件写配对串 / 设备令牌 / 会话 id；
//       只打印帧类型计数与最短摘要。跑完默认吊销本次探针设备。
//
// 依赖：ws（优先网关自身的 node_modules，其次 profile 的 node_modules）。

import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

const argv = process.argv.slice(2)
const arg = (name, fallback) => {
  const i = argv.indexOf(`--${name}`)
  return i >= 0 && argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[i + 1] : fallback
}
const has = (name) => argv.includes(`--${name}`)

const WEB_PORT = Number(arg('web-port', process.env.DSH_MOBILE_WEB_PORT || 19387))
const WS_URL = arg('ws-url', `ws://127.0.0.1:3091/ws/mobile`)
const WAIT_MS = Number(arg('wait', 7000))
const HISTORY_WAIT_MS = Number(arg('history-wait', 12000))
const WANT_SESSION = arg('session', '')
const DEVICE_ID = arg('device-id', 'probe-subagent-address')
const KEEP_DEVICE = has('keep-device')
const MGMT = `http://127.0.0.1:${WEB_PORT}`

async function loadWebSocket() {
  const candidates = [
    process.env.DSH_MOBILE_GATEWAY_WS,
    path.join(os.homedir(), '.dsh', 'profiles', 'desktop', 'node_modules', 'ws', 'index.js'),
    path.join(os.homedir(), '.dsh', 'profiles', 'desktop', 'node_modules', 'dsh-plugin-mobile-gateway', 'node_modules', 'ws', 'index.js'),
  ].filter(Boolean)
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      const mod = await import(pathToFileURL(candidate).href)
      const WS = mod.WebSocket || mod.default
      if (typeof WS === 'function') return WS
    }
  }
  throw new Error('找不到 ws 模块；用 DSH_MOBILE_GATEWAY_WS=<ws/index.js 绝对路径> 指定')
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

// 线上帧可能是 { kind:'event', event:{ type, ... } }，也可能是裸 DSH 事件 { type, seq, time, data }。
const bodyOf = (entry) => (entry && typeof entry === 'object' && entry.event && typeof entry.event === 'object' ? entry.event : entry)
const eventType = (entry) => (bodyOf(entry) || {}).type || (entry && entry.kind) || 'unknown'
const oneLine = (value, max = 100) => String(value ?? '').replace(/\s+/g, ' ').trim().slice(0, max)
const blocksText = (blocks) => (Array.isArray(blocks)
  ? blocks.filter((b) => b && b.type === 'text' && typeof b.text === 'string').map((b) => b.text).join(' ')
  : '')
const blocksToolNames = (blocks) => (Array.isArray(blocks)
  ? blocks.filter((b) => b && b.type === 'tool-call').map((b) => b.name).filter(Boolean).join(',')
  : '')

// 人类可读摘要：输入 / 输出 / Tool 名（只截前若干条，避免刷屏与整段内容外泄）
function digest(events, limit = 12) {
  const lines = []
  for (const entry of events.slice(0, limit)) {
    const e = bodyOf(entry) || {}
    const d = e.data || e
    if (e.type === 'user/message') {
      lines.push(`user: ${oneLine(blocksText(d.content) || d.text, 80)}`)
    } else if (e.type === 'assistant/message') {
      const text = d.text !== undefined ? d.text : blocksText(d.message && d.message.content)
      const tools = d.toolCalls ? d.toolCalls.map((t) => t && t.name).filter(Boolean).join(',')
        : blocksToolNames(d.message && d.message.content)
      lines.push(`assistant: ${oneLine(text, 70)}${tools ? ` [tool-call: ${tools}]` : ''}`)
    } else if (e.type === 'tool/call') {
      lines.push(`tool/call: ${oneLine(d.name, 60)}`)
    } else if (e.type === 'tool/result') {
      const text = blocksText(d.message && d.message.content) || (typeof d.text === 'string' ? d.text : '')
      lines.push(`tool/result: ${oneLine(text, 70)}${d.error ? ` [error: ${oneLine(d.error.code || d.error.name, 30)}]` : ''}`)
    } else lines.push(eventType(entry))
  }
  return lines
}

async function main() {
  const WebSocket = await loadWebSocket()

  const status = await (await fetch(`${MGMT}/mgw/status`)).json()
  console.log(`网关: version=${status.version} requireAuth=${status.requireAuth} mode=${status.gatewayMode} 已连客户端=${status.connectedClients}`)

  // 一次性配对凭证（单次使用；探针设备 id 固定 -> 只轮换凭证不新增行）
  // --no-pair：目标网关 requireAuth:false 时跳过配对（隔离验收 profile 用）
  let pairingCode = ''
  if (has('no-pair')) {
    console.log('跳过配对（--no-pair）')
  } else {
    const pairRes = await fetch(`${MGMT}/mgw/pair`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ name: 'subagent-address-probe' }),
    })
    if (!pairRes.ok) throw new Error(`配对请求失败: HTTP ${pairRes.status}`)
    const pair = await pairRes.json()
    pairingCode = pair?.payload?.pairingCode
    if (!pairingCode) throw new Error('配对响应里没有 pairingCode')
  }

  const frames = []
  const url = pairingCode
    ? `${WS_URL}${WS_URL.includes('?') ? '&' : '?'}pairingCode=${encodeURIComponent(pairingCode)}`
    : WS_URL
  const ws = new WebSocket(url, {
    headers: { 'X-DSH-Device-ID': DEVICE_ID },
  })
  let deviceId = ''
  ws.on('message', (data) => {
    let frame
    try { frame = JSON.parse(data.toString()) } catch { return }
    frames.push(frame)
    if (frame.kind === 'paired' && frame.device) deviceId = frame.device.id
  })

  await new Promise((resolve, reject) => {
    ws.on('open', resolve)
    ws.on('error', reject)
    setTimeout(() => reject(new Error('ws 连接超时')), 15000)
  })
  console.log(`已连接 ${WS_URL}`)

  const waitFrame = async (predicate, timeoutMs, label, fromIndex = 0) => {
    const deadline = Date.now() + timeoutMs
    let seen = fromIndex
    while (Date.now() < deadline) {
      for (let i = seen; i < frames.length; i += 1) {
        if (predicate(frames[i])) return frames[i]
      }
      seen = frames.length
      await sleep(120)
    }
    throw new Error(`等待 ${label} 超时（${timeoutMs}ms）`)
  }

  await waitFrame((f) => f.kind === 'hello' || f.kind === 'paired', 8000, 'hello')

  // 1) 列会话，找子会话
  ws.send(JSON.stringify({ type: 'sessions' }))
  const sessionsFrame = await waitFrame((f) => f.kind === 'sessions', 20000, 'sessions')
  const items = Array.isArray(sessionsFrame.items) ? sessionsFrame.items : []
  const children = items.filter((it) => it && it.origin === 'subagent' && typeof it.parentSessionId === 'string')
  console.log(`会话总数=${items.length} 其中 origin=subagent 的子会话=${children.length}`)

  const target = WANT_SESSION
    ? items.find((it) => it && String(it.sessionId) === WANT_SESSION)
    : has('parent')
      ? (items.find((it) => it && it.origin !== 'subagent' && it.blank === false) || items.find((it) => it && it.origin !== 'subagent'))
      : (children.find((it) => it.running === false) || children[0])
  if (!target) throw new Error(WANT_SESSION ? `会话列表里没有 ${WANT_SESSION}` : '会话列表里没有可用的会话')
  const isChild = target.origin === 'subagent' && typeof target.parentSessionId === 'string'
  console.log(`目标会话: kind=${isChild ? 'subagent' : 'session'} running=${target.running} blank=${target.blank}（id 不打印）`)
  if (!WANT_SESSION && !has('parent') && children.length === 0) throw new Error('没有可用的子会话')

  // 2) history
  const marker = frames.length
  ws.send(JSON.stringify({ type: 'history', sessionId: target.sessionId, maxMessages: 40 }))
  let historyFrame
  try {
    historyFrame = await waitFrame((f) => (f.kind === 'history' || f.kind === 'error') && frames.indexOf(f) >= marker, HISTORY_WAIT_MS, 'history')
  } catch (error) {
    console.log(`history: ✗ ${error.message}`)
  }
  if (historyFrame) {
    if (historyFrame.kind === 'error') {
      console.log(`history: ✗ kind=error code=${historyFrame.code} message=${historyFrame.message}`)
    } else {
      const events = Array.isArray(historyFrame.events) ? historyFrame.events : []
      const kinds = {}
      for (const entry of events) kinds[eventType(entry)] = (kinds[eventType(entry)] || 0) + 1
      console.log(`history: ✓ kind=history 事件数=${events.length} 类型分布=${JSON.stringify(kinds)}`)
      if (has('digest')) for (const line of digest(events)) console.log(`  ${line}`)
    }
  }

  // 3) subscribe（复现 2s 重试 reset 的关键）
  const subMarker = frames.length
  ws.send(JSON.stringify({ type: 'subscribe', sessionId: target.sessionId, assistantStream: true }))
  const subscribed = await waitFrame((f) => f.kind === 'subscribed', 8000, 'subscribed')
  console.log(`subscribe: 收到 subscribed（sessionId=${subscribed.sessionId ? '有' : 'null'}）`)
  await sleep(WAIT_MS)

  const after = frames.slice(subMarker)
  const count = (kind) => after.filter((f) => f.kind === kind).length
  const snapshots = after.filter((f) => f.kind === 'session-snapshot')
  const resets = after.filter((f) => f.kind === 'session-stream-reset')
  const events = after.filter((f) => f.kind === 'event' || f.kind === 'wire-event' || f.kind === 'session-event')
  const eventCount = snapshots.reduce((sum, s) => sum + (Array.isArray(s.events) ? s.events.length : 0), 0)

  console.log(`subscribe 采样 ${WAIT_MS}ms：session-snapshot=${count('session-snapshot')}（含事件 ${eventCount} 条）`
    + ` session-stream-reset=${count('session-stream-reset')} 流式帧=${count('assistant-stream')} error=${count('error')}`)
  for (const reset of resets.slice(0, 4)) {
    console.log(`  reset 帧: code=${reset.code} retrying=${reset.retrying === true} message=${String(reset.message || '').slice(0, 120)}`)
  }
  if (snapshots.length > 0) {
    const kinds = {}
    for (const entry of snapshots[0].events || []) kinds[eventType(entry)] = (kinds[eventType(entry)] || 0) + 1
    console.log(`  首帧 snapshot 事件类型分布=${JSON.stringify(kinds)}`)
    if (has('digest')) for (const line of digest(snapshots[0].events || [])) console.log(`  ${line}`)
  }

  const verdict = []
  if (historyFrame?.kind === 'history') verdict.push('history=OK')
  else verdict.push(`history=${historyFrame?.code || 'TIMEOUT'}`)
  verdict.push(`snapshot=${count('session-snapshot')}`)
  verdict.push(`reset=${count('session-stream-reset')}`)
  console.log(`判定: ${verdict.join(' ')}`)

  ws.close()
  await sleep(400)

  if (!KEEP_DEVICE && deviceId) {
    try {
      const r = await fetch(`${MGMT}/mgw/devices/${encodeURIComponent(deviceId)}/revoke`, { method: 'POST' })
      console.log(`探针设备已吊销: ${r.ok}`)
    } catch (error) {
      console.log(`探针设备吊销失败（可手动在面板里删）: ${error.message}`)
    }
  }
}

main().catch((error) => {
  console.error(`探针失败: ${error.message}`)
  process.exitCode = 1
})
