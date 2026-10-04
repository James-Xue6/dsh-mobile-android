// 自测工具：**不装进 DSH、不碰线上插件目录**，直接以桩 ctx 跑
// pc-plugin/dsh-mobile-access/index.js 的 apply()，把它的局域网 app 服务
// （默认 8099，这里用 DSH_MOBILE_APP_PORT 换成 18099）拉起来，然后真打一次
// /public-url，核对返回里是否有新加的 lanUrls / gatewayId 字段。
//
// 为什么值得单独有这个工具：/public-url 是「手机在局域网里自己把地址找回来」
// 的唯一数据源（方案 B）。它一旦 500/少字段，App 侧只会静默降级成"请重新扫码"，
// 在真机上很难定位 —— 这里能在改完插件后立刻验一次。
//
// 用法：
//   node pc-plugin/test-public-url.mjs            # 需要 DSH 正在跑（网关 /mgw/status 可达）
//   DSH_MOBILE_APP_PORT=18099 node pc-plugin/test-public-url.mjs
//
// 退出码 0 = 通过；1 = 失败（打印原因）。

import http from 'node:http'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const APP_PORT = Number(process.env.DSH_MOBILE_APP_PORT || 18099)
const WEB_PORT = Number(process.env.DSH_WEB_PORT || 19387)
const TOKEN = process.env.DSH_TEST_TOKEN || 'self-test-token'

const mod = await import('./dsh-mobile-access/index.js')

const disposers = []
const ctx = {
  webServer: { port: WEB_PORT, register: () => () => {} },
  effect: (fn) => { const d = fn(); if (typeof d === 'function') disposers.push(d); return d },
}

function fail(msg) {
  console.error('[FAIL] ' + msg)
  for (const d of disposers) { try { d() } catch { /* ignore */ } }
  process.exit(1)
}

function get(pathname, headers = {}) {
  return new Promise((resolve, reject) => {
    const req = http.request(
      { host: '127.0.0.1', port: APP_PORT, method: 'GET', path: pathname, headers, timeout: 8000 },
      (res) => {
        const chunks = []
        res.on('data', (c) => chunks.push(c))
        res.on('end', () => resolve({ status: res.statusCode, text: Buffer.concat(chunks).toString('utf8') }))
      },
    )
    req.on('timeout', () => req.destroy(new Error('timeout')))
    req.on('error', reject)
    req.end()
  })
}

try {
  mod.apply(ctx)
} catch (e) {
  fail('apply() 抛异常：' + (e && e.message ? e.message : String(e)))
}

await new Promise((r) => setTimeout(r, 400))

// ① 无令牌必须 401（安全边界没被这次改动打开）
try {
  const r = await get('/public-url')
  if (r.status !== 401) fail('无令牌访问 /public-url 应 401，实际 ' + r.status)
  console.log('[ok] 无令牌 → 401（鉴权闸门仍在）')
} catch (e) {
  fail('无令牌请求失败：' + e.message)
}

// ② 带令牌：必须是 200 + 合法 JSON + 含 publicUrl / lanUrls 键
try {
  const r = await get('/public-url', { 'X-DSH-Token': TOKEN })
  if (r.status !== 200) fail('带令牌应 200，实际 ' + r.status + ' body=' + r.text.slice(0, 300))
  let o
  try { o = JSON.parse(r.text) } catch { fail('响应不是 JSON：' + r.text.slice(0, 300)) }
  if (!Object.prototype.hasOwnProperty.call(o, 'publicUrl')) fail('缺少 publicUrl 键：' + r.text.slice(0, 300))
  if (!Array.isArray(o.lanUrls)) fail('缺少 lanUrls 数组（本次改动没生效）：' + r.text.slice(0, 300))
  console.log('[ok] /public-url 200 · publicUrl=' + (o.publicUrl ? '有' : '无')
    + ' · lanUrls=' + JSON.stringify(o.lanUrls) + ' · gatewayId=' + (o.gatewayId ? '有' : '无'))
  if (o.lanUrls.length === 0) {
    console.log('[warn] lanUrls 为空：网关 lan 监听可能没起来（App 会退回"扫到的 IP + 猜端口"）')
  }
} catch (e) {
  fail('带令牌请求失败：' + e.message)
}

// ③ 首页仍要在（App 靠它认"这台 IP 上有 DSH 面板"）
try {
  const r = await get('/')
  if (r.status !== 200 || !r.text.includes('DSH 掌上通')) {
    fail('面板首页丢了或没有身份标记：status=' + r.status)
  }
  console.log('[ok] 面板首页含身份标记「DSH 掌上通」（局域网扫描靠它认面板）')
} catch (e) {
  fail('首页请求失败：' + e.message)
}

for (const d of disposers) { try { d() } catch { /* ignore */ } }
console.log('\n全部通过 ✅')
process.exit(0)
