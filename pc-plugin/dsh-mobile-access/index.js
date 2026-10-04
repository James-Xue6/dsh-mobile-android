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
 * 单位是「这个 APK 自己的 versionName」，用于面板显示，不再拿来拼公网镜像地址（见 PUBLIC_REF）。
 */
function appVersion() {
  try {
    const v = fs.readFileSync(path.join(PLUGIN_DIR, 'app', 'version.txt'), 'utf8').trim()
    if (v) return v
  } catch { /* 没这个文件就退回下面的兜底 */ }
  return '0.2'
}
/**
 * 公网镜像用哪个 git ref。
 *
 * 为什么不再用「v + versionName」：tag 只有跑 release.ps1 发版时才新增。
 * 实测 2026-10-02：最新 tag 是 v0.8，那里的安装包还是 0.8 的旧构建，
 * 而工作区 dist/ 里已经构建出更新的包 —— 用 tag 拼出来的地址会一直指向旧包
 * （把 version.txt 改成 0.81-test 后更糟：v0.81-test 这个 tag 根本不存在，直接 404）。
 * 改成跟 App 自身更新检查同一个 ref（src/com/dsh/mobile/MainActivity.java 读的就是
 * @main 下的 dist/version.json），以后再推新构建，这两个镜像地址自动就是最新的那份。
 * 要临时钉回某个 tag：设环境变量 DSH_MOBILE_PUBLIC_REF=v0.8。
 */
const PUBLIC_REF = String(process.env.DSH_MOBILE_PUBLIC_REF || 'main')
  .trim()
  .replace(/^refs\/(heads|tags)\//, '')
  .replace(/^\/+|\/+$/g, '') || 'main'
/** 公开发布地址：给出多条线路，手机在哪个网络都能挑到通的那条 */
function publicUrls() {
  const v = appVersion()
  const ref = PUBLIC_REF
  const gh = 'https://github.com/' + REPO_SLUG + '/raw/' + ref + '/dist/dsh-mobile.apk'
  const cdn = 'https://cdn.jsdelivr.net/gh/' + REPO_SLUG + '@' + ref + '/dist/dsh-mobile.apk'
  return {
    version: v,
    ref,
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

/**
 * 这个地址是不是「手机在同一 WiFi 下真的连得上」的局域网地址。
 *
 * 判据（2026-10-02 在本机实测后写死）：
 *   · 169.254.0.0/16 —— APIPA：DHCP 没拿到地址时的自分配地址，打不通。
 *   · 172.16.0.0/12 —— 虚拟网卡重灾区。VirtualBox / Hyper-V / WSL / 部分 VPN 都在
 *     这一段里给宿主机内部地址（例如 172.30.x），它只在宿主机内部有意义，
 *     手机即使同一 WiFi 也路由不过去。旧版直接拿 os.networkInterfaces() 的第一条
 *     当「局域网直发」地址，于是二维码指向了 172.30.x（虚拟网卡），手机扫码必超时。
 *   · 100.64.0.0/10 —— 运营商级 NAT（CGNAT），不是本机内网地址。
 *   · 127.x —— 本机回环。
 * 真实家用/办公内网基本落在 192.168.x 或 10.x；真要是 172 内网，本函数会一个地址都不返回，
 * 面板就显示「没探测到可用内网地址」并让用户手填，而不是给一张连不上的码。
 */
function isUsableLanAddress(ip) {
  const parts = String(ip).split('.')
  if (parts.length !== 4) return false
  const nums = parts.map((p) => (/^\d{1,3}$/.test(p) ? Number(p) : NaN))
  if (nums.some((n) => !Number.isInteger(n) || n < 0 || n > 255)) return false
  const [a, b] = nums
  if (a === 127) return false                          // 本机回环
  if (a === 169 && b === 254) return false             // APIPA
  if (a === 172 && b >= 16 && b <= 31) return false    // 虚拟网卡 / VBox / Hyper-V / WSL
  if (a === 100 && b >= 64 && b <= 127) return false   // CGNAT
  return true
}

/** 排序权重：真实家用网段（192.168.x、10.x）排在其它网段前面 */
function lanAddressRank(ip) {
  if (ip.startsWith('192.168.')) return 0
  if (ip.startsWith('10.')) return 1
  return 2
}

function lanIPv4() {
  const out = []
  const ifaces = os.networkInterfaces()
  for (const name of Object.keys(ifaces)) {
    for (const ni of ifaces[name] || []) {
      if (ni.family !== 'IPv4' || ni.internal) continue
      if (!isUsableLanAddress(ni.address)) continue
      if (out.includes(ni.address)) continue
      out.push(ni.address)
    }
  }
  return out.sort((x, y) => (lanAddressRank(x) - lanAddressRank(y)) || x.localeCompare(y))
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

/**
 * 盲 CSRF 闸门（安全评审 B6）。
 *
 * 网关 /mgw/* 自己的防护是「只允许 loopback + 写请求要同源」，但本代理是以
 * 127.0.0.1 身份转发的，而且**自己补了 `origin: http://127.0.0.1:<port>`**
 * 去满足网关的 isSameOrigin 检查 —— 这层转发网络关的同源校验失效了。
 * 于是同一台电脑的浏览器里，任意网页（含 https 的恶意站）只要用**简单请求**
 * POST 到 http://127.0.0.1:<webPort>/dsh-mobile-access/<path>，就能盲打 /mgw 的写接口：
 * 开启 Cloudflare 隧道（把用户 DSH 直接暴露到公网）、关闭设备鉴权、生成配对码等。
 * 浏览器不给读响应，但副作用已经发生。
 *
 * 关键闸门：简单请求只允许三种 Content-Type，想带 application/json 必须**先发 CORS 预检**，
 * 而本服务从不回预检（无 Access-Control-* 头）→ 浏览器直接拦掉真实请求。
 * 面板自身是同源 fetch，一直用 application/json，不受影响。
 * 另外拒绝「带了 Origin 且与 Host 不符」的写请求，作为第二道闸（防预检被宿主放行的场景）。
 */
const JSON_CONTENT_TYPE = 'application/json'

function isTrustedMutation(req) {
  const ct = String(req.headers['content-type'] || '').split(';')[0].trim().toLowerCase()
  if (ct !== JSON_CONTENT_TYPE) return false
  const origin = req.headers.origin
  // origin 缺失 = 非浏览器客户端（curl 等）；'null' = file:// / 沙箱 iframe。
  // 这两种都过不了上面的 Content-Type 闸，所以这里不再额外拒绝，避免误伤面板。
  if (typeof origin === 'string' && origin !== 'null') {
    const host = req.headers.host
    if (typeof host !== 'string') return false
    try {
      if (new URL(origin).host !== host) return false
    } catch {
      return false
    }
  }
  return true
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
      // [方案5·局域网自动重配] 手机在局域网里连上后，用它取"电脑当前最新的公网(隧道)地址"。
      //
      // 为什么放在这里：网关的 /mgw/status 带 adminLoopbackOnly（手机永远打不到），
      // 而本插件本来就在电脑上、能走 loopback 代理到 /mgw —— 于是**不需要改网关插件**。
      // 鉴权：要求 X-DSH-Token（手机本来就持有设备令牌）；本服务只在局域网可达，
      // 返回的是隧道地址（不是凭证），并且**不打印任何令牌**。
      if (p === '/public-url') {
        const token = String(req.headers['x-dsh-token'] || '').trim()
        if (!token) {
          const body = Buffer.from(JSON.stringify({
            error: 'unauthorized', message: '缺少设备令牌（X-DSH-Token）',
          }), 'utf8')
          res.writeHead(401, { 'content-type': 'application/json; charset=utf-8', 'content-length': body.length, 'cache-control': 'no-store' })
          res.end(body)
          return
        }
        callGateway(port, 'GET', '/mgw/status', null).then((result) => {
          let publicUrl = ''
          let lanUrls = []
          let gatewayId = ''
          let gatewayName = ''
          try {
            const parsed = JSON.parse(result.body)
            publicUrl = String((parsed && parsed.publicUrl) || '')
            // [连接门·地址自动重新发现] 电脑自己的**权威**内网地址列表（含真实网关端口）。
            //
            // 为什么必须由电脑给：手机扫子网只能知道"面板在哪台 IP 上"，
            // 拼 WebSocket 地址时端口得猜（网关 lanPort 可被 profile 覆盖，本项目是 3091
            // 而不是上游默认的 3081）。这里直接把网关自己上报的 lan.urls 透出去，
            // 手机拿到的就是电脑当前真正在听的地址 —— 换了网口 / DHCP 换了 IP 也一样准。
            const lan = parsed && parsed.lan
            if (lan && Array.isArray(lan.urls)) {
              lanUrls = lan.urls
                .filter((u) => typeof u === 'string' && u.trim())
                .map((u) => u.trim())
            }
            gatewayId = String((parsed && parsed.gatewayId) || '')
            gatewayName = String((parsed && parsed.gatewayName) || '')
          } catch { publicUrl = '' }
          // 拿不到就明确说"无公网地址"，绝不返回空串糊弄（App 侧据此保持原地址不变）
          const body = Buffer.from(JSON.stringify({
            publicUrl,
            available: !!publicUrl,
            reason: publicUrl ? '' : '无公网地址（隧道未开启或未就绪）',
            // 老 App 不认识这两个字段，直接忽略；新 App 据此把内网地址更新成电脑当前值
            lanUrls,
            gatewayId,
            gatewayName,
            at: Date.now(),
          }), 'utf8')
          res.writeHead(200, { 'content-type': 'application/json; charset=utf-8', 'content-length': body.length, 'cache-control': 'no-store' })
          res.end(body)
        }).catch(() => {
          const body = Buffer.from(JSON.stringify({
            publicUrl: '', available: false, reason: '网关管理接口不可达', lanUrls: [],
          }), 'utf8')
          res.writeHead(200, { 'content-type': 'application/json; charset=utf-8', 'content-length': body.length, 'cache-control': 'no-store' })
          res.end(body)
        })
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
          // 写请求必须是同源面板发出的 JSON 请求（见 isTrustedMutation 的说明）。
          if (req.method !== 'GET' && req.method !== 'HEAD' && !isTrustedMutation(req)) {
            sendJson(res, 403, {
              error: 'forbidden',
              message: '跨站写请求已拒绝（只接受同源 application/json）',
            })
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
              // 公网镜像跟的是哪个 ref（面板「备用线路」里会显示，便于核对拿到的是哪一版）
              publicRef: pub.ref,
              available: info.available, size: info.size, name: info.name, port: APP_PORT,
              lanUrls: urls, lanPage: lanUrl,
              // 没探测到可用内网地址时 lanUrls 为空、qrLanUrl 为 ''：面板据此显示
              //「没探测到局域网地址，切到上面的「公网镜像」下载」，不再给一张连不上的码。
              lanAvailable: urls.length > 0,
              publicUrl: pub.cdn, githubUrl: pub.github, mirrors: pub.mirrors,
              qrUrl: pub.cdn, qrSvg: await mkQr(pub.cdn),
              qrLanUrl: lanUrl, qrLanSvg: lanUrl ? await mkQr(lanUrl) : null,
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
// 导出内部判据供自测用（tools/test-lan-filter.mjs 直接跑真源码，不另抄一份规则）
export { lanIPv4, isUsableLanAddress, lanAddressRank, publicUrls, appVersion, PUBLIC_REF }
