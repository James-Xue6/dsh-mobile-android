#!/usr/bin/env node
/**
 * 多设备串台回归 · 假网关（两个"电脑"，各带一组同名不同内容的会话）
 *
 * 用途：真机上复现/验证「两台设备同时在线 → 进 A 却看到 B 的对话 + 反复弹错」这类串台问题，
 * 不需要真的开两台电脑。App 用 ws://10.0.2.2:<port>/ws/mobile 连（模拟器里 10.0.2.2 = 宿主机）。
 *
 *   node harness/fake-gateway-multidev.mjs            # 默认 3097 = FAKE-A，3098 = FAKE-B
 *   node harness/fake-gateway-multidev.mjs 3197 3198
 *
 * 每个网关的会话标题带网关字母（A-1 / B-1…），一眼就能看出界面上的列表属于哪台电脑。
 * 鉴权不做校验（任意 token 都放行）：本脚本只用于本机联调，不要用于任何真实环境。
 */
import { createRequire } from 'node:module'

const require = createRequire('C:/Users/Administrator/.dsh/profiles/desktop/node_modules/')
const { WebSocketServer } = require('ws')

const PORT_A = Number(process.argv[2] || 3097)
const PORT_B = Number(process.argv[3] || 3098)

const PROTO = 'dsh-mobile-v1'

/** 造一组会话：标题里带网关字母，便于肉眼区分。 */
function sessionsFor(tag, n = 3) {
  const now = Date.now()
  const items = []
  for (let i = 1; i <= n; i++) {
    items.push({
      sessionId: `${tag.toLowerCase()}-session-${i}`,
      title: `${tag}-${i} 这台电脑的会话`,
      cwd: `C:\\fake\\${tag}`,
      updatedAt: now - i * 60_000,
      running: false,
      blank: false,
      parentId: '',
      origin: '',
    })
  }
  return items
}

function startGateway({ port, tag, gatewayName }) {
  const gatewayId = `fake-gateway-${tag.toLowerCase()}`
  const items = sessionsFor(tag)
  const wss = new WebSocketServer({
    port,
    path: '/ws/mobile',
    handleProtocols: () => PROTO,
    verifyClient: (info, done) => done(true),
  })

  wss.on('connection', (ws) => {
    const send = (obj) => { try { ws.send(JSON.stringify(obj)) } catch { /* 已关 */ } }
    console.log(`[${tag}] 客户端接入 :${port}`)

    // 1) hello：App 收到它才会进入 READY
    send({
      kind: 'hello',
      protocol: 3,
      // 与 App 的 GatewayClient.REQUIRED_CAPS 对齐，否则会挂「网关能力不完整」横幅
      capabilities: ['split-channels', 'images', 'commands', 'tasks', 'goals',
        'session-create', 'session-cancel', 'queue-control', 'session-archive',
        'session-rename', 'file-downloads'],
      authenticated: true,
      device: { id: `dev-${tag.toLowerCase()}`, name: `Pixel(${tag})` },
      port,
      clients: 1,
      gatewayId,
      gatewayName,
      dshVersion: `0.0.0-${tag}`,
    })

    ws.on('message', (raw) => {
      let m
      try { m = JSON.parse(raw.toString()) } catch { return }
      const sid = m.sessionId || items[0].sessionId
      switch (m.type) {
        case 'ping':
          send({ kind: 'pong', at: Date.now() })
          break
        case 'sessions':
          send({ kind: 'sessions', items, archivedSessionIds: [] })
          break
        case 'subscribe':
          send({ kind: 'subscribed', sessionId: sid })
          send({
            kind: 'session-snapshot', sessionId: sid, cursor: 0,
            events: [{
              type: 'user/message', seq: 1, time: Math.floor(Date.now() / 1000),
              data: { content: [{ type: 'text', text: `这是 ${tag} 电脑的会话 ${sid}` }] },
            }],
          })
          break
        case 'history':
          send({
            kind: 'history', sessionId: sid, events: [], historyFormatVersion: 4,
            cursor: 0, bytes: 2, view: m.view || '', hasMore: false,
          })
          break
        case 'tasks':
          send({ kind: 'tasks', sessionId: sid, todos: [] })
          break
        case 'goal':
          send({ kind: 'goal', sessionId: sid, goal: null })
          break
        case 'models':
          send({ kind: 'models', sessionId: sid, presets: [], current: '' })
          break
        case 'session-stats':
          send({ kind: 'session-stats', sessionId: sid, stats: {} })
          break
        case 'context-usage':
          send({ kind: 'context-usage', sessionId: sid, usage: {} })
          break
        case 'queue-list':
          send({ kind: 'queue', sessionId: sid, items: [] })
          break
        case 'workspaces':
          send({ kind: 'workspaces', items: [], archivedSessionIds: [], pinnedSessionIds: [] })
          break
        default:
          // 其它请求一律静默（不影响本回归要看的现象）
          break
      }
    })

    ws.on('close', () => console.log(`[${tag}] 客户端断开`))
  })

  console.log(`[${tag}] 假网关已启动：ws://127.0.0.1:${port}/ws/mobile  网关名=${gatewayName}`)
  return wss
}

startGateway({ port: PORT_A, tag: 'A', gatewayName: '假电脑 A' })
startGateway({ port: PORT_B, tag: 'B', gatewayName: '假电脑 B' })
