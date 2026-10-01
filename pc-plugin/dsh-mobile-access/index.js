// 手机接入（dsh-mobile-access）—— host 侧半面。
//
// 职责：只做一件事——把 dsh-plugin-mobile-gateway 的 loopback 管理接口 /mgw/*
// 代理成一条同源路径 /api/dsh-mobile-access/*，供本插件的浏览器面板调用。
//
// 为什么不直接在浏览器里 fetch('/mgw/*')：
//   /mgw 带 adminLoopbackOnly + isSameOrigin 校验，且桌面端渲染进程的来源地址
//   不一定稳定。由 host 侧以 127.0.0.1 发起请求最稳，也把鉴权边界留在宿主。
//
// 协议层完全不重写：本文件只是转发。

import http from 'node:http'

export const name = 'dsh-mobile-access'
export const inject = ['webServer']

const PREFIX = '/dsh-mobile-access'

/** 前端可用的路由 -> /mgw 上的真实路由 */
const ROUTES = [
  { method: 'GET', local: '/status', target: '/mgw/status' },
  { method: 'GET', local: '/devices', target: '/mgw/devices' },
  { method: 'POST', local: '/gateway', target: '/mgw/gateway' },
  { method: 'POST', local: '/pair', target: '/mgw/pair' },
  { method: 'POST', local: '/auth', target: '/mgw/auth' },
  // 公网访问：直接复用网关内置的 Cloudflare 隧道（随机域名 / 自定义域名）
  { method: 'POST', local: '/tunnel', target: '/mgw/cloudflare' },
  { method: 'POST', local: '/tunnel/restart', target: '/mgw/cloudflare/restart' },
]

function sendJson(res, status, payload) {
  const body = Buffer.from(JSON.stringify(payload), 'utf8')
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': body.length,
    'cache-control': 'no-store',
  })
  res.end(body)
}

function isLoopback(req) {
  const a = (req.socket && req.socket.remoteAddress) || ''
  return a === '127.0.0.1' || a === '::1' || a === '::ffff:127.0.0.1'
}

function readBody(req, limit = 1 << 20) {
  return new Promise((resolve, reject) => {
    let size = 0
    const chunks = []
    req.on('data', (chunk) => {
      size += chunk.length
      if (size > limit) {
        reject(new Error('request body too large'))
        try { req.destroy() } catch { /* ignore */ }
        return
      }
      chunks.push(chunk)
    })
    req.on('end', () => resolve(Buffer.concat(chunks)))
    req.on('error', reject)
  })
}

/** 以 loopback 身份把请求打到网关的 /mgw，并原样回传状态码与响应体。 */
function callGateway(port, method, targetPath, body) {
  return new Promise((resolve, reject) => {
    const headers = {
      host: '127.0.0.1:' + port,
      origin: 'http://127.0.0.1:' + port,
      'content-type': 'application/json',
    }
    if (body && body.length > 0) headers['content-length'] = body.length
    const req = http.request(
      { host: '127.0.0.1', port, method, path: targetPath, headers, timeout: 30000 },
      (res) => {
        const chunks = []
        res.on('data', (chunk) => chunks.push(chunk))
        res.on('end', () => resolve({ status: res.statusCode || 500, body: Buffer.concat(chunks) }))
      },
    )
    req.on('timeout', () => { try { req.destroy(new Error('gateway timeout')) } catch { /* ignore */ } })
    req.on('error', reject)
    if (body && body.length > 0) req.write(body)
    req.end()
  })
}

export function apply(ctx) {
  const webServer = ctx.webServer
  const port = webServer && webServer.port ? webServer.port : 0

  ctx.effect(() => {
    const dispose = webServer.register({
      kind: 'prefix',
      path: PREFIX,
      handler: async (req, res) => {
        try {
          if (!isLoopback(req)) {
            sendJson(res, 403, { error: 'forbidden', message: '仅允许本机访问' })
            return
          }
          const url = new URL(req.url || '/', 'http://x')
          const local = url.pathname.slice(PREFIX.length) || '/'

          // 需要在中间做一层解析的路由：撤销设备
          const revoke = /^\/devices\/([^/]+)\/revoke$/.exec(local)
          if (revoke && req.method === 'POST') {
            const result = await callGateway(
              port, 'POST', '/mgw/devices/' + encodeURIComponent(decodeURIComponent(revoke[1])) + '/revoke', null,
            )
            res.writeHead(result.status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' })
            res.end(result.body)
            return
          }

          const route = ROUTES.find((r) => r.local === local)
          if (!route) {
            sendJson(res, 404, { error: 'not-found', message: '未知接口：' + local })
            return
          }
          if (req.method !== route.method) {
            sendJson(res, 405, { error: 'method-not-allowed', message: '请用 ' + route.method })
            return
          }

          let body = null
          if (route.method === 'POST') {
            const raw = await readBody(req)
            const text = raw.toString('utf8').trim()
            if (text === '') body = Buffer.from('{}', 'utf8')
            else {
              try { JSON.parse(text) } catch { sendJson(res, 400, { error: 'bad-request', message: '请求体不是合法 JSON' }); return }
              body = Buffer.from(text, 'utf8')
            }
          }

          const result = await callGateway(port, route.method, route.target, body)
          res.writeHead(result.status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' })
          res.end(result.body)
        } catch (error) {
          const message = error && error.message ? error.message : String(error)
          const hint = /ECONNREFUSED|socket hang up|gateway timeout/i.test(message)
            ? '移动网关插件（dsh-plugin-mobile-gateway）没有响应，请确认它已安装并已重启过 DSH'
            : message
          sendJson(res, 502, { error: 'gateway-unavailable', message: hint })
        }
      },
    })
    return () => { try { dispose() } catch { /* ignore */ } }
  }, 'mobile-access.routes')
}

export default { name, inject, apply }
