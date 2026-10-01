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
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

export const name = 'dsh-mobile-access'
export const inject = ['webServer']

const PREFIX = '/dsh-mobile-access'
const PLUGIN_DIR = path.dirname(fileURLToPath(import.meta.url))
/** 随插件一起分发的安装包：手机扫面板二维码直接下载 */
const APK_PATH = path.join(PLUGIN_DIR, 'app', 'dsh-mobile.apk')
/** 局域网发安装包的端口（只发这一个文件，不做目录服务） */
const APP_PORT = Number(process.env.DSH_MOBILE_APP_PORT || 8099)
const REPO_SLUG = 'James-Xue6/dsh-mobile-android'
/**
 * App 版本：优先读插件目录里的 app/version.txt（与 APK 一起更新），
 * 这样换版本只要替换文件、不用重启 DSH。
 */
function appVersion() {
  try {
    const v = fs.readFileSync(path.join(PLUGIN_DIR, 'app', 'version.txt'), 'utf8').trim()
    if (v) return v
  } catch { /* 没这个文件就退回下面的兜底 */ }
  return '0.2'
}
/** 公开发布地址：给出多条线路，手机在哪个网络都能挑到通的那条 */
function publicUrls() {
  const v = appVersion()
  const gh = 'https://github.com/' + REPO_SLUG + '/raw/v' + v + '/dist/dsh-mobile.apk'
  const cdn = 'https://cdn.jsdelivr.net/gh/' + REPO_SLUG + '@v' + v + '/dist/dsh-mobile.apk'
  return {
    version: v,
    cdn,
    github: gh,
    // 国内直连 GitHub 常常打不开；这两个是常用的 GitHub 加速镜像
    mirrors: [
      { name: 'jsDelivr CDN', url: cdn },
      { name: 'ghproxy 镜像', url: 'https://ghproxy.net/' + gh },
      { name: 'gh-proxy 镜像', url: 'https://gh-proxy.com/' + gh },
      { name: 'GitHub 原始', url: gh },
    ],
  }
}

/** 复用网关依赖里的 qrcode 生成二维码；解析不到就退化为纯链接（面板自动降级） */
function loadQrCode() {
  const profiles = process.env.DSH_PROFILE_DIR
    ? [path.join(process.env.DSH_PROFILE_DIR, 'package.json')]
    : [path.join(os.homedir(), '.dsh', 'profiles', 'desktop', 'package.json'),
       path.join(os.homedir(), '.dsh', 'package.json')]
  for (const base of profiles) {
    try { return createRequire(base)('qrcode') } catch { /* 试下一个 */ }
  }
  return null
}

function lanIPv4() {
  const out = []
  const ifaces = os.networkInterfaces()
  for (const name of Object.keys(ifaces)) {
    for (const ni of ifaces[name] || []) {
      if (ni.family === 'IPv4' && !ni.internal) out.push(ni.address)
    }
  }
  return out
}

function apkInfo() {
  try {
    const st = fs.statSync(APK_PATH)
    return { available: true, size: st.size, mtime: st.mtimeMs, name: path.basename(APK_PATH) }
  } catch {
    return { available: false, size: 0, mtime: 0, name: 'dsh-mobile.apk' }
  }
}

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

  // ---------------- 局域网发安装包：手机扫码即下载 ----------------
  // 跑在 DSH 宿主进程内，防火墙是按程序放行的，所以局域网可达；
  // 只回应 GET / 与 GET /app.apk，其他一律 404，避免变成通用文件服务。
  let appServer = null
  ctx.effect(() => {
    appServer = http.createServer((req, res) => {
      const p = (req.url || '/').split('?')[0]
      if (req.method !== 'GET' && req.method !== 'HEAD') {
        res.writeHead(405, { 'content-type': 'text/plain; charset=utf-8' })
        res.end('method not allowed')
        return
      }
      if (p === '/' ) {
        const info = apkInfo()
        const body = Buffer.from(
          '<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">'
          + '<title>DSH 掌上通 · 安装包</title>'
          + '<body style="font:15px system-ui;padding:24px;line-height:1.7">'
          + '<h2>DSH 掌上通</h2>'
          + (info.available
            ? '<p>安装包 ' + (info.size / 1024).toFixed(1) + ' KB</p>'
              + '<p><a href="/app.apk" style="font-size:17px">⬇ 点击下载 APK</a></p>'
              + '<p style="color:#888">下载后在手机上点安装；若提示未知来源，允许本浏览器安装即可。</p>'
            : '<p>插件目录里还没有安装包：' + APK_PATH + '</p>'),
          'utf8')
        res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'content-length': body.length, 'cache-control': 'no-store' })
        res.end(req.method === 'HEAD' ? undefined : body)
        return
      }
      if (p === '/app.apk') {
        let st
        try { st = fs.statSync(APK_PATH) } catch {
          res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' })
          res.end('apk not found')
          return
        }
        res.writeHead(200, {
          'content-type': 'application/vnd.android.package-archive',
          'content-length': st.size,
          'content-disposition': 'attachment; filename="dsh-mobile.apk"',
          'cache-control': 'no-store',
        })
        if (req.method === 'HEAD') { res.end(); return }
        fs.createReadStream(APK_PATH).pipe(res)
        return
      }
      res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' })
      res.end('not found')
    })
    appServer.on('error', () => { /* 端口被占等：面板会显示不可用 */ })
    appServer.listen(APP_PORT, '0.0.0.0')
    return () => { try { appServer && appServer.close() } catch { /* ignore */ } }
  }, 'mobile-access.app-server')

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

          // 面板用来渲染「扫码装 App」卡片
          if (local === '/app' && req.method === 'GET') {
            const info = apkInfo()
            const lan = lanIPv4()
            const urls = lan.map((ip) => 'http://' + ip + ':' + APP_PORT + '/app.apk')
            const pub = publicUrls()
            const QRCode = loadQrCode()
            const mkQr = async (text) => {
              if (!QRCode || !text) return null
              try { return await QRCode.toString(text, { type: 'svg', margin: 1, width: 200 }) } catch { return null }
            }
            // 两个二维码：
            //   局域网直发 —— 手机和电脑同一 WiFi 时必通，安装包从本机直接发出
            //   公网镜像   —— 人在外面时用（jsDelivr 常被墙，面板里还能挑 ghproxy / gh-proxy）
            const lanUrl = urls.length ? urls[0] : ''
            sendJson(res, 200, {
              version: pub.version,
              available: info.available, size: info.size, name: info.name, port: APP_PORT,
              lanUrls: urls, lanPage: lanUrl,
              publicUrl: pub.cdn, githubUrl: pub.github, mirrors: pub.mirrors,
              qrUrl: pub.cdn, qrSvg: await mkQr(pub.cdn),
              qrLanUrl: lanUrl, qrLanSvg: await mkQr(lanUrl),
              apkPath: APK_PATH,
            })
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
