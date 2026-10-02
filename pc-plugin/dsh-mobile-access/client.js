// 手机接入（dsh-mobile-access）—— 浏览器端面板。
//
// 入口：设置 → 通用 → 「手机接入」
// 只做界面与编排，真正的协议层是 dsh-plugin-mobile-gateway；本插件通过宿主侧的
// /api/dsh-mobile-access/* 代理访问它的 /mgw/* 管理接口。
window.__ModuleLoader__.load({
  id: 'dsh-mobile-access',
  factory: (require) => {
    var module = { exports: {} }
    var exports = module.exports
    Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' })

    var React = require('react')

    var NAME = 'dsh-mobile-access'
    var API = '/dsh-mobile-access'
    var STYLE_TAG_ID = NAME + '/styles'
    var SETTINGS_ID = 'mobile-access'
    var DEVICE_NAME_KEY = 'dsh-mobile-access:device-name'

    // ------------------------------------------------------------------ 样式
    var CSS = [
      '.dsma-root{display:block;}',
      '.dsma-title{font-size:15px;font-weight:600;color:var(--dsw-alias-label-primary);}',
      '.dsma-desc{margin-top:3px;max-width:62ch;font-size:13px;line-height:20px;color:var(--dsw-alias-label-tertiary);}',
      '.dsma-card{margin-top:12px;padding:12px 14px;border-radius:12px;border:1px solid var(--dsw-alias-border-l1);background:var(--dsw-alias-bg-layer-2);}',
      '.dsma-card-title{font-size:14px;font-weight:600;color:var(--dsw-alias-label-primary);}',
      '.dsma-line{margin-top:7px;font-size:13px;line-height:20px;color:var(--dsw-alias-label-secondary);}',
      '.dsma-row{margin-top:10px;display:flex;align-items:center;gap:8px;flex-wrap:wrap;}',
      '.dsma-btn{padding:6px 14px;font-size:13px;line-height:18px;border-radius:8px;cursor:pointer;',
      '  border:1px solid var(--dsw-alias-border-l2);background:var(--dsw-alias-bg-layer-2);',
      '  color:var(--dsw-alias-label-primary);}',
      '.dsma-btn:hover{border-color:var(--dsw-alias-label-secondary);}',
      '.dsma-btn:disabled{opacity:.45;cursor:default;}',
      '/* 品牌色必须用字面色：实测 var(--dsw-alias-brand-primary) 在本环境解析失败，',
      '   background 会回退成浅色而文字是白色，按钮就变成「空白方块」 */',
      '.dsma-btn-primary{border-color:#4d6bfe;background:#4d6bfe;color:#ffffff;font-weight:600;}',
      '.dsma-btn-primary:hover{background:#3a57e8;border-color:#3a57e8;}',
      '.dsma-btn-danger{color:#d93026;border-color:#f3c9c6;}',
      '.dsma-field{margin-top:10px;display:block;}',
      '.dsma-input{display:block;width:100%;box-sizing:border-box;height:34px;padding:0 11px;font-size:13px;',
      '  border-radius:8px;border:1px solid var(--dsw-alias-border-l2);background:var(--dsw-alias-bg-layer-1);',
      '  color:var(--dsw-alias-label-primary);}',
      '.dsma-chip{display:inline-flex;align-items:center;gap:6px;padding:3px 9px;border-radius:999px;font-size:12px;',
      '  border:1px solid var(--dsw-alias-border-l1);color:var(--dsw-alias-label-secondary);}',
      '.dsma-dot{width:7px;height:7px;border-radius:50%;display:inline-block;}',
      '.dsma-qr{margin-top:10px;padding:12px;border-radius:12px;background:#fff;display:inline-block;line-height:0;}',
      '/* 配对串是 552 字符上下 Base64URL，二维码要做成 89x89 模块，画得越大越好扫：',
      '   220px 时一个模块只有 2.4px，手机常见扫不动/扫错；放到 300px 且等比缩放。 */',
      '.dsma-qr svg{display:block;width:300px;max-width:100%;height:auto;}',
      '/* 二维码只在弹窗里出现：页面里常驻两张码正是用户「看不明白」的原因 */',
      '.dsma-modal-backdrop{position:fixed;inset:0;z-index:9999;background:rgba(0,0,0,.45);display:flex;',
      '  align-items:center;justify-content:center;padding:20px;}',
      '.dsma-modal{width:100%;max-width:380px;max-height:calc(100vh - 40px);overflow:auto;box-sizing:border-box;',
      '  padding:18px 20px;border-radius:14px;text-align:center;background:var(--dsw-alias-bg-layer-1);',
      '  color:var(--dsw-alias-label-primary);box-shadow:0 12px 40px rgba(0,0,0,.35);}',
      '.dsma-modal-title{font-size:15px;font-weight:600;}',
      '.dsma-modal-step{margin-top:6px;font-size:13px;line-height:20px;color:var(--dsw-alias-label-secondary);}',
      '.dsma-tabs{margin-top:10px;display:flex;gap:6px;justify-content:center;flex-wrap:wrap;}',
      '.dsma-tab{padding:4px 12px;font-size:12px;border-radius:999px;cursor:pointer;',
      '  border:1px solid var(--dsw-alias-border-l2);background:transparent;color:var(--dsw-alias-label-secondary);}',
      '.dsma-tab-on{border-color:#4d6bfe;background:rgba(77,107,254,.12);color:#4d6bfe;font-weight:600;}',
      '.dsma-link{margin-top:8px;font-size:12px;line-height:18px;word-break:break-all;color:var(--dsw-alias-label-tertiary);}',
      '.dsma-modal-actions{margin-top:14px;display:flex;gap:8px;justify-content:center;flex-wrap:wrap;}',
      '.dsma-sec{margin-top:14px;padding-top:12px;border-top:1px solid var(--dsw-alias-border-l1);}',
      '.dsma-token{margin-top:10px;padding:9px 11px;border-radius:8px;font-family:monospace;',
      '  font-size:12px;line-height:18px;word-break:break-all;background:var(--dsw-alias-bg-layer-1);',
      '  border:1px solid var(--dsw-alias-border-l1);color:var(--dsw-alias-label-secondary);}',
      '.dsma-dev{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:7px 0;',
      '  border-top:1px solid var(--dsw-alias-border-l1);}',
      '.dsma-dev:first-child{border-top:none;}',
      '.dsma-dev-name{font-size:13px;color:var(--dsw-alias-label-primary);}',
      '.dsma-dev-meta{font-size:12px;color:var(--dsw-alias-label-tertiary);}',
      '.dsma-error{margin-top:9px;font-size:13px;color:var(--dsw-alias-label-error,#d93026);}',
      '.dsma-ok{margin-top:9px;font-size:13px;color:var(--dsw-alias-label-secondary);}',
      ''
    ].join('\n')

    function adoptStyles() {
      if (typeof document === 'undefined') return { remove: function () {} }
      var selector = 'style[data-plugin-css="' + STYLE_TAG_ID + '"]'
      var tag = document.querySelector(selector)
      if (tag === null) {
        tag = document.createElement('style')
        tag.dataset.plugin = NAME
        tag.dataset.pluginCss = STYLE_TAG_ID
        tag.textContent = CSS
        document.head.appendChild(tag)
      }
      return {
        remove: function () {
          if (tag.parentNode !== null) tag.parentNode.removeChild(tag)
        }
      }
    }

    // ------------------------------------------------------------------ 网络
    function request(method, path, body) {
      var options = { method: method, headers: { 'content-type': 'application/json' }, cache: 'no-store' }
      if (body !== undefined && body !== null) options.body = JSON.stringify(body)
      return fetch(API + path, options).then(function (res) {
        return res.text().then(function (text) {
          var data = null
          try { data = text === '' ? null : JSON.parse(text) } catch (e) { data = null }
          if (!res.ok) {
            var msg = data && data.message ? data.message : ('HTTP ' + res.status)
            throw new Error(msg)
          }
          return data
        })
      })
    }

    function fmtTime(ms) {
      if (!ms) return '从未'
      var d = new Date(ms)
      var pad = function (n) { return n < 10 ? '0' + n : String(n) }
      return pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
    }

    function readDeviceName() {
      try { return window.localStorage.getItem(DEVICE_NAME_KEY) || '' } catch (e) { return '' }
    }

    function writeDeviceName(value) {
      try { window.localStorage.setItem(DEVICE_NAME_KEY, value) } catch (e) { /* ignore */ }
    }

    /** 从 ws://ip:port/ws/mobile 里拆出 IP 与端口（内网弹窗的默认值要用） */
    function splitLanUrl(url) {
      var m = /^wss?:\/\/([^/:\s]+)(?::(\d+))?/.exec(String(url || ''))
      return { host: m ? m[1] : '', port: m && m[2] ? m[2] : '' }
    }

    /**
     * 面板侧只用「手机真连得上」的内网地址（与 index.js 的 isUsableLanAddress 同一套规则）。
     *
     * 为什么面板也要过滤：网关自己（dsh-plugin-mobile-gateway 的 status.lan.urls）会把
     * 虚拟网卡地址一起列出来，而且虚拟网卡常常排在真网卡前面 —— 内网二维码默认就填了它，
     * 手机扫了连不上。排除依据：
     *   · 169.254.x.x —— APIPA，没拿到 DHCP
     *   · 172.16.0.0/12 —— 虚拟网卡重灾区（VirtualBox / Hyper-V / WSL 常在 172.30.x 之类）
     *   · 100.64.0.0/10 —— 运营商级 NAT
     *   · 127.x —— 本机回环
     */
    function isUsableLanHost(host) {
      var parts = String(host || '').split('.')
      if (parts.length !== 4) return false
      var nums = []
      for (var i = 0; i < parts.length; i++) {
        if (!/^\d{1,3}$/.test(parts[i])) return false
        var n = Number(parts[i])
        if (n > 255) return false
        nums.push(n)
      }
      if (nums[0] === 127) return false
      if (nums[0] === 169 && nums[1] === 254) return false
      if (nums[0] === 172 && nums[1] >= 16 && nums[1] <= 31) return false
      if (nums[0] === 100 && nums[1] >= 64 && nums[1] <= 127) return false
      return true
    }

    /** 真实家用网段优先（192.168.x > 10.x > 其它） */
    function lanHostRank(host) {
      if (/^192\.168\./.test(host)) return 0
      if (/^10\./.test(host)) return 1
      return 2
    }

    /**
     * 校验 /pair 返回的配对串：必须是非空、长度合理、能解出 version=2 的 Base64URL(JSON)。
     *
     * 为什么出码前要自检：二维码一旦画出来用户就会去扫，手机端对「不是配对串的内容」
     * 只会报一句「不是可用的配对码」。所以宁可这里先验一次：不通过就**不画二维码**，
     * 直接告诉用户「配对串获取失败，请重试」，而不是画一张内容不对的码。
     * 返回 '' 表示通过，否则返回原因（给用户看的短句）。
     */
    function pairPayloadProblem(value) {
      if (typeof value !== 'string' || value.trim() === '') return '服务端没有返回配对串'
      var text = value.trim()
      if (text.length <= 100) return '配对串只有 ' + text.length + ' 个字符，明显不完整'
      var b64 = text.replace(/-/g, '+').replace(/_/g, '/').replace(/\s+/g, '')
      var pad = b64.length % 4
      if (pad === 1) return '配对串不是合法的 Base64URL（长度 ' + text.length + '）'
      if (pad === 2) b64 += '=='
      else if (pad === 3) b64 += '='
      var binary
      try { binary = atob(b64) } catch (e) { return '配对串不是合法的 Base64URL' }
      var json = binary
      try {
        // atob 给的是 latin1 字符串；配对串里可能有中文（设备名），按 UTF-8 还原再看 JSON
        json = decodeURIComponent(binary.split('').map(function (c) {
          return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
        }).join(''))
      } catch (e) { /* 还原失败就按 latin1 继续，JSON 结构本身是 ASCII */ }
      if (json.charAt(0) !== '{') return '配对串解出来不是 JSON'
      var obj = null
      try { obj = JSON.parse(json) } catch (e) { return '配对串不是合法 JSON' }
      if (obj.version !== 2) return '配对串版本不是 2（收到 ' + obj.version + '）'
      if (!obj.publicUrl || !obj.pairingCode) return '配对串里缺少连接地址或配对码'
      return ''
    }

    // ------------------------------------------------------------------ 组件
    /**
     * 「手机接入」主面板 —— 重设计后的交互。
     *
     * 原则（用户反馈「东西一大堆、看不明白」）：
     *   · 打开面板只看到 3 个按钮 + 一句说明，不超过一屏：
     *       ① 下载 App       → 点一下弹二维码（局域网直发 / 公网镜像 两条线路）
     *       ② 公网连接       → 点一下先弹风险确认，同意后弹二维码
     *       ③ 内网连接       → 点一下先弹「只改 IP/端口」的小框，再弹二维码
     *   · 二维码只在弹窗里出现，页面里不再常驻两张码。
     *   · 网关模式 / 隧道细节 / 设备列表与撤销 / 手动地址 / 安装包细节
     *     全部收进「高级设置（一般用不到）」，默认收起，功能一个不少。
     */
    function MobileAccessRow() {
      var statusState = React.useState(null)
      var status = statusState[0]
      var setStatus = statusState[1]
      var devState = React.useState([])
      var devices = devState[0]
      var setDevices = devState[1]
      var errState = React.useState('')
      var error = errState[0]
      var setError = errState[1]
      var msgState = React.useState('')
      var message = msgState[0]
      var setMessage = msgState[1]
      var busyState = React.useState('')
      var busy = busyState[0]
      var setBusy = busyState[1]
      var nameState = React.useState(readDeviceName())
      var deviceName = nameState[0]
      var setDeviceName = nameState[1]
      var urlState = React.useState('')
      var manualUrl = urlState[0]
      var setManualUrl = urlState[1]
      // 二维码弹窗：公网 / 内网 / 下载 App 共用同一个弹窗
      var qrState = React.useState(null)
      var qrView = qrState[0]
      var setQrView = qrState[1]
      // 公网前的风险确认
      var riskState = React.useState(false)
      var riskOpen = riskState[0]
      var setRiskOpen = riskState[1]
      var riskOkState = React.useState(false)
      var riskOk = riskOkState[0]
      var setRiskOk = riskOkState[1]
      // 内网：只让改 IP / 端口的小弹窗
      var lanState = React.useState(false)
      var lanOpen = lanState[0]
      var setLanOpen = lanState[1]
      var lanIpState = React.useState('')
      var lanIp = lanIpState[0]
      var setLanIp = lanIpState[1]
      var lanPortState = React.useState('')
      var lanPort = lanPortState[0]
      var setLanPort = lanPortState[1]
      // 下载 App 弹窗里的线路：局域网直发 / 公网镜像
      var appRouteState = React.useState('lan')
      var appRoute = appRouteState[0]
      var setAppRoute = appRouteState[1]
      var appState = React.useState(null)
      var app = appState[0]
      var setApp = appState[1]
      // 高级设置默认收起
      var advState = React.useState(false)
      var advOpen = advState[0]
      var setAdvOpen = advState[1]

      function loadApp() {
        return request('GET', '/app').then(function (d) { setApp(d) }).catch(function () { /* 读不到就不显示 */ })
      }

      function loadStatus(silent) {
        return request('GET', '/status')
          .then(function (data) { setStatus(data); if (!silent) setError('') })
          .catch(function (e) { if (!silent) setError(e.message) })
      }

      function loadDevices() {
        return request('GET', '/devices')
          .then(function (data) { setDevices((data && data.devices) || []) })
          .catch(function (e) { setError(e.message) })
      }

      React.useEffect(function () {
        loadStatus(false)
        loadDevices()
        loadApp()
        var timer = setInterval(function () { loadStatus(true) }, 5000)
        return function () { clearInterval(timer) }
      }, [])

      function setMode(mode) {
        setBusy('mode'); setError(''); setMessage('')
        request('POST', '/gateway', { mode: mode })
          .then(function () {
            setMessage(mode === 'persistent' ? '已开启：常驻模式' : mode === 'temporary' ? '已开启：临时模式' : '已关闭')
            return loadStatus(false)
          })
          .catch(function (e) { setError(e.message) })
          .then(function () { setBusy('') })
      }

      function tunnel(action, confirmed) {
        if (action === 'on' && !confirmed) { setRiskOk(false); setRiskOpen(true); return }
        setBusy('tunnel'); setError(''); setMessage('')
        var path = action === 'restart' ? '/tunnel/restart' : '/tunnel'
        var body = action === 'on' ? { enabled: true, mode: 'quick' }
          : action === 'off' ? { enabled: false } : {}
        request('POST', path, body)
          .then(function () {
            setMessage(action === 'on'
              ? '正在开启公网隧道；首次运行需先下载 cloudflared（约 20-50MB），通常 1-2 分钟'
              : (action === 'off' ? '已关闭公网隧道' : '正在重启隧道'))
            return loadStatus(true)
          })
          .catch(function (e) { setError(e.message) })
          .then(function () { setBusy('') })
      }

      /** 生成配对码并弹出二维码弹窗；url 为空则由服务端按当前环境挑一条 */
      function pairWith(url, label) {
        setBusy('pair'); setError(''); setMessage('')
        var name = (deviceName || '').trim() || '我的手机'
        writeDeviceName(name)
        var body = { name: name }
        if (url) body.publicUrl = url
        // 网关没开时顺手以「临时模式」开启：用户点一下就该出码，不该再去找开关
        var guard = (status && status.gatewayEnabled)
          ? Promise.resolve()
          : request('POST', '/gateway', { mode: 'temporary' })
        guard
          .then(function () { return request('POST', '/pair', body) })
          .then(function (data) {
            // 服务端给的 svg 就是 qrPayload 画出来的二维码（网关 lib/index.mjs 里
            // QRCode.toString(qrPayload) 直接用同一个串）；url 只是旁边给人看的文字，
            // 任何时候都不会画进二维码。这里再验一次配对串，拿不到就不画码。
            var payload = data && typeof data.qrPayload === 'string' ? data.qrPayload.trim() : ''
            var problem = pairPayloadProblem(payload)
            setQrView({
              kind: 'pair',
              title: '扫码连接 · ' + (label || '本机'),
              svg: problem ? null : (data && data.svg),
              payload: problem ? '' : payload,
              payloadError: problem,
              url: (data && data.payload && data.payload.publicUrl) || url || '',
              expiresAt: data && data.payload && data.payload.expiresAt,
            })
            return loadDevices()
          })
          .catch(function (e) { setError(e.message) })
          .then(function () { setBusy('') })
      }

      /** 内网连接用的 ws 地址（网关已经在监听的地址，跳过虚拟网卡） */
      function lanWsUrl() {
        var urls = ((status && status.lan && status.lan.urls) || []).filter(function (u) {
          return isUsableLanHost(splitLanUrl(u).host)
        })
        if (!urls.length) return ''
        return urls.slice().sort(function (a, b) {
          return lanHostRank(splitLanUrl(a).host) - lanHostRank(splitLanUrl(b).host)
        })[0]
      }

      /** 高级区里用：按当前已知地址挑一条生成配对码 */
      function makePairing() {
        var tunnelUrl = status && status.cloudflare && status.cloudflare.publicUrl
          ? status.cloudflare.publicUrl : ''
        return pairWith(manualUrl.trim() || tunnelUrl || lanWsUrl(), '手动地址')
      }

      /** 探测到的本机内网 IP 候选（网关监听到的 + 发安装包用的那几个；滤掉虚拟网卡） */
      function lanCandidates() {
        var out = []
        var push = function (url) {
          var host = splitLanUrl(url).host
          if (host && isUsableLanHost(host) && out.indexOf(host) < 0) out.push(host)
        }
        ;((status && status.lan && status.lan.urls) || []).forEach(push)
        ;((app && app.lanUrls) || []).forEach(function (url) {
          // app.lanUrls 是 http://…/app.apk，不是 ws://，单独取一次 host
          var m = /^https?:\/\/([^/:\s]+)/.exec(String(url || ''))
          if (m) push('ws://' + m[1])
        })
        return out.sort(function (a, b) { return lanHostRank(a) - lanHostRank(b) })
      }

      function openLanDialog() {
        var parts = splitLanUrl(lanWsUrl())
        setLanIp(parts.host)
        setLanPort(parts.port || String((status && status.lan && status.lan.port) || ''))
        setLanOpen(true); setError(''); setMessage('')
      }

      function confirmLan() {
        var ip = String(lanIp || '').trim().replace(/^wss?:\/\//, '').replace(/\/.*$/, '')
        if (!ip) { setError('请填写这台电脑的局域网 IP'); return }
        var port = String(lanPort || '').trim() || '3091'
        var wsPath = (status && status.wsPath) || '/ws/mobile'
        setLanOpen(false)
        pairWith('ws://' + ip + ':' + port + wsPath, '内网')
      }

      /** 公网：先弹风险确认（每次都确认，不记住） */
      function askPublic() {
        setRiskOk(false); setError(''); setMessage(''); setRiskOpen(true)
      }

      /** 等隧道拿到公网地址；首次要下载组件，可能等 1-2 分钟 */
      function waitTunnelUrl(deadline) {
        return request('GET', '/status').then(function (data) {
          setStatus(data)
          var url = data && data.cloudflare && data.cloudflare.publicUrl
          if (url) return url
          if (Date.now() > deadline) {
            throw new Error('公网隧道还在准备中（第一次用要先下载组件，约 1-2 分钟）。过一会儿再点一次「生成公网二维码」即可。')
          }
          return new Promise(function (resolve) { setTimeout(resolve, 2000) })
            .then(function () { return waitTunnelUrl(deadline) })
        })
      }

      function confirmRisk() {
        if (!riskOk) { setError('请勾选「我已知情」后再开启公网'); return }
        setRiskOpen(false); setRiskOk(false)
        setBusy('tunnel'); setError(''); setMessage('')
        var cf = status && status.cloudflare
        var ensure = cf && cf.enabled
          ? Promise.resolve()
          : request('POST', '/tunnel', { enabled: true, mode: 'quick' })
              .then(function () { return loadStatus(true) })
        ensure
          .then(function () { return waitTunnelUrl(Date.now() + 150000) })
          .then(function (url) { setBusy(''); return pairWith(url, '公网') })
          .catch(function (e) { setError(e.message); setBusy('') })
      }

      function revoke(id, name) {
        setBusy('revoke:' + id); setError(''); setMessage('')
        request('POST', '/devices/' + encodeURIComponent(id) + '/revoke')
          .then(function () {
            setMessage('已撤销设备：' + name)
            return loadDevices()
          })
          .catch(function (e) { setError(e.message) })
          .then(function () { setBusy('') })
      }

      function copy(text, what) {
        try {
          if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(
              function () { setMessage('已复制' + what) },
              function () { setError('复制失败，请手动选中') },
            )
            return
          }
        } catch (e) { /* fall through */ }
        setError('当前环境不支持自动复制，请手动选中')
      }

      var enabled = status && status.gatewayEnabled
      var mode = status ? status.gatewayMode : ''
      var lanUrls = (status && status.lan && status.lan.urls) || []
      var lanOk = status && status.lan && status.lan.listening
      var cf = (status && status.cloudflare) || null

      var children = []

      children.push(React.createElement('div', { className: 'dsma-title', key: 'title' }, '手机接入'))
      children.push(React.createElement('div', { className: 'dsma-desc', key: 'desc' },
        '点一下出码、用手机扫就行。第一次用请从①开始；②里两个按钮按手机在哪选一个。'))

      // ---- ① 下载 App（下载安装包用的码；扫了不会连上电脑）
      children.push(React.createElement('div', { className: 'dsma-card', key: 'c1' },
        React.createElement('div', { className: 'dsma-card-title' }, '① 下载 App'),
        React.createElement('div', { className: 'dsma-line' },
          '手机还没装「DSH 掌上通」就点这里；这张码只下载安装包，不会连上电脑。'),
        React.createElement('div', { className: 'dsma-row' },
          React.createElement('button', {
            type: 'button', className: 'dsma-btn dsma-btn-primary',
            onClick: function () {
              setAppRoute(app && app.available && app.qrLanSvg ? 'lan' : 'net')
              setQrView({ kind: 'app' })
            },
          }, '下载 App')),
        app === null
          ? React.createElement('div', { className: 'dsma-dev-meta', style: { marginTop: '6px' } }, '读取中…')
          : null))

      // ---- ② 扫码连接（公网 / 内网，二选一）
      children.push(React.createElement('div', { className: 'dsma-card', key: 'c2' },
        React.createElement('div', { className: 'dsma-card-title' }, '② 扫码连接'),
        React.createElement('div', { className: 'dsma-line' },
          'App 装好后点这里出码，在 App 里点「扫码配对」扫一下，手机就连上这台电脑了。'),
        React.createElement('div', { className: 'dsma-row' },
          React.createElement('button', {
            type: 'button', className: 'dsma-btn dsma-btn-primary',
            disabled: busy !== '', onClick: askPublic,
          }, busy === 'tunnel' ? '正在准备公网…' : '生成公网二维码')),
        React.createElement('div', { className: 'dsma-dev-meta' },
          '人在外面、手机用流量时选这个；手机在任何网络都能连。'),
        React.createElement('div', { className: 'dsma-row' },
          React.createElement('button', {
            type: 'button', className: 'dsma-btn',
            disabled: busy !== '', onClick: openLanDialog,
          }, '生成内网二维码')),
        React.createElement('div', { className: 'dsma-dev-meta' },
          '手机和电脑连同一个 WiFi 时选这个；同一网络下最快。'),
        !enabled
          ? React.createElement('div', { className: 'dsma-dev-meta', style: { marginTop: '6px' } },
              '（网关现在是关的；点上面任一按钮会自动帮你开。）')
          : null))

      // ---- 高级设置：默认收起，功能一个不少
      var adv = []
      adv.push(React.createElement('div', { className: 'dsma-row', style: { marginTop: 0 }, key: 'toggle' },
        React.createElement('button', {
          type: 'button', className: 'dsma-btn',
          'aria-expanded': advOpen,
          onClick: function () { setAdvOpen(!advOpen) },
        }, advOpen ? '收起高级设置' : '高级设置（一般用不到）'),
        React.createElement('span', { className: 'dsma-dev-meta' },
          advOpen ? '' : '网关开关、隧道细节、已配对设备、手动填地址')))
      if (advOpen) {
        // 接入状态
        adv.push(React.createElement('div', { className: 'dsma-sec', key: 'st' },
          React.createElement('div', { className: 'dsma-card-title' }, '接入状态'),
          status === null
            ? React.createElement('div', { className: 'dsma-line' }, error ? ('读取失败：' + error) : '读取中…')
            : React.createElement(React.Fragment, null,
                React.createElement('div', { className: 'dsma-row' },
                  React.createElement('span', { className: 'dsma-chip' },
                    React.createElement('i', { className: 'dsma-dot', style: { background: enabled ? '#16a34a' : '#9ca3af' } }),
                    enabled ? ('网关已开启 · ' + (mode === 'persistent' ? '常驻' : mode === 'temporary' ? '临时' : mode)) : '网关已关闭'),
                  React.createElement('span', { className: 'dsma-chip' },
                    React.createElement('i', { className: 'dsma-dot', style: { background: lanOk ? '#16a34a' : '#f59e0b' } }),
                    lanOk ? ('局域网监听 :' + (status.lan && status.lan.port)) : '局域网未监听'),
                  React.createElement('span', { className: 'dsma-chip' }, '在线设备 ' + (status.connectedClients || 0)),
                  React.createElement('span', { className: 'dsma-chip' }, '网关 v' + (status.version || '?'))),
                status.lan && status.lan.error
                  ? React.createElement('div', { className: 'dsma-error' }, '局域网监听异常：' + status.lan.error)
                  : null,
                React.createElement('div', { className: 'dsma-row' },
                  React.createElement('button', {
                    type: 'button', className: 'dsma-btn' + (enabled && mode === 'persistent' ? ' dsma-btn-primary' : ''),
                    disabled: busy !== '', onClick: function () { setMode('persistent') },
                  }, '常驻开启'),
                  React.createElement('button', {
                    type: 'button', className: 'dsma-btn' + (enabled && mode === 'temporary' ? ' dsma-btn-primary' : ''),
                    disabled: busy !== '', onClick: function () { setMode('temporary') },
                  }, '临时开启'),
                  React.createElement('button', {
                    type: 'button', className: 'dsma-btn', disabled: busy !== '', onClick: function () { setMode('disabled') },
                  }, '关闭网关'),
                  React.createElement('button', {
                    type: 'button', className: 'dsma-btn', disabled: busy !== '',
                    onClick: function () { loadStatus(false); loadDevices(); loadApp() },
                  }, '刷新')),
                lanUrls.length > 0
                  ? React.createElement('div', { className: 'dsma-line' }, '手机可达地址：' + lanUrls.join('、'))
                  : null)))

        // 公网隧道细节
        var tz = []
        tz.push(React.createElement('div', { className: 'dsma-card-title', key: 'h' }, '公网隧道（Cloudflare）'))
        if (cf) {
          var tState = { disabled: '未开启', preparing: '准备中', starting: '启动中', online: '已在线', error: '出错' }[cf.state] || String(cf.state || '未知')
          tz.push(React.createElement('div', { className: 'dsma-row', key: 's' },
            React.createElement('span', { className: 'dsma-chip' },
              React.createElement('span', { className: 'dsma-dot', style: { background: cf.state === 'online' ? '#2fb344' : '#e8a33d' } }),
              '隧道 ' + tState),
            React.createElement('span', { className: 'dsma-chip' },
              '模式 ' + (cf.mode === 'named' ? '自定义域名' : '随机域名'))))
          if (cf.publicUrl) {
            tz.push(React.createElement('div', { className: 'dsma-token', key: 'u' }, cf.publicUrl))
            tz.push(React.createElement('div', { className: 'dsma-row', key: 'ub' },
              React.createElement('button', { type: 'button', className: 'dsma-btn', disabled: busy !== '',
                onClick: function () { copy(cf.publicUrl, '公网地址') } }, '复制地址'),
              React.createElement('button', { type: 'button', className: 'dsma-btn', disabled: busy !== '',
                onClick: function () { tunnel('restart') } }, '重启隧道')))
          } else {
            tz.push(React.createElement('div', { className: 'dsma-line', key: 'u2' },
              '尚未取得公网地址。开启后若长时间停在「准备中」，多半是在下载 cloudflared。'))
          }
          if (cf.error) tz.push(React.createElement('div', { className: 'dsma-error', key: 'e' }, String(cf.error)))
          tz.push(React.createElement('div', { className: 'dsma-row', key: 'b' },
            React.createElement('button', {
              type: 'button',
              className: 'dsma-btn ' + (cf.enabled ? 'dsma-btn-danger' : 'dsma-btn-primary'),
              disabled: busy !== '', onClick: function () { tunnel(cf.enabled ? 'off' : 'on') },
            }, busy === 'tunnel' ? '处理中…' : (cf.enabled ? '关闭公网隧道' : '开启公网隧道'))))
        } else {
          tz.push(React.createElement('div', { className: 'dsma-line', key: 'n' },
            '当前网关未启用 Cloudflare 能力（需网关插件 0.9.0 及以上）。'))
        }
        adv.push(React.createElement('div', { className: 'dsma-sec', key: 'tz' }, tz))

        // 手动地址 / 设备名
        var mz = []
        mz.push(React.createElement('div', { className: 'dsma-card-title', key: 'h' }, '手动填地址生成配对码'))
        mz.push(React.createElement('div', { className: 'dsma-field', key: 'f1' },
          React.createElement('div', { className: 'dsma-dev-meta' }, '设备名'),
          React.createElement('input', {
            className: 'dsma-input', value: deviceName, placeholder: '例如：我的手机',
            onChange: function (e) { setDeviceName(e.target.value) },
          })))
        mz.push(React.createElement('div', { className: 'dsma-field', key: 'f2' },
          React.createElement('div', { className: 'dsma-dev-meta' }, '地址（留空则自动挑一条当前可用的）'),
          React.createElement('input', {
            className: 'dsma-input', value: manualUrl,
            placeholder: 'ws://192.168.1.100:3091/ws/mobile',
            onChange: function (e) { setManualUrl(e.target.value) },
          })))
        mz.push(React.createElement('div', { className: 'dsma-row', key: 'f3' },
          React.createElement('button', {
            type: 'button', className: 'dsma-btn',
            disabled: busy !== '', onClick: makePairing,
          }, busy === 'pair' ? '生成中…' : '生成二维码')))
        adv.push(React.createElement('div', { className: 'dsma-sec', key: 'mz' }, mz))

        // 已配对设备
        var dv = []
        dv.push(React.createElement('div', { className: 'dsma-card-title', key: 'h' }, '已配对设备（' + devices.length + '）'))
        if (devices.length === 0) {
          dv.push(React.createElement('div', { className: 'dsma-line', key: 'empty' }, '还没有设备配对过。'))
        } else {
          devices.forEach(function (d) {
            dv.push(React.createElement('div', { className: 'dsma-dev', key: d.id },
              React.createElement('div', null,
                React.createElement('div', { className: 'dsma-dev-name' }, d.name || '(未命名)'),
                React.createElement('div', { className: 'dsma-dev-meta' },
                  '最近连接 ' + fmtTime(d.lastSeenAt) + (d.online ? '　● 在线' : ''))),
              React.createElement('button', {
                type: 'button', className: 'dsma-btn dsma-btn-danger',
                disabled: busy !== '', onClick: function () { revoke(d.id, d.name || d.id) },
              }, '撤销')))
          })
        }
        adv.push(React.createElement('div', { className: 'dsma-sec', key: 'dv' }, dv))

        // 安装包细节 / 备用下载线路
        var az = []
        az.push(React.createElement('div', { className: 'dsma-card-title', key: 'h' }, '安装包信息与备用线路'))
        if (app && app.available) {
          az.push(React.createElement('div', { className: 'dsma-line', key: 'meta' },
            'v' + (app.version || '?') + ' · ' + (app.size / 1024).toFixed(1) + ' KB · ' + app.name))
          az.push(React.createElement('div', { className: 'dsma-dev-meta', key: 'ref' },
            '局域网直发用的是这台电脑上的最新构建；公网镜像跟的是 ' + (app.publicRef || 'main')
            + ' 这个 ref（发版才会另打 tag）。'))
          az.push(React.createElement('div', { className: 'dsma-row', key: 'r1' },
            React.createElement('button', {
              type: 'button', className: 'dsma-btn',
              disabled: !app.lanUrls || !app.lanUrls.length,
              onClick: function () { if (app.lanUrls && app.lanUrls.length) copy(app.lanUrls[0], '局域网地址') },
            }, '复制局域网直发地址')))
          if (app.lanUrls && app.lanUrls.length) {
            az.push(React.createElement('div', { className: 'dsma-dev-meta', key: 'lan' }, '局域网直发：' + app.lanUrls[0]))
          }
          if (app.mirrors && app.mirrors.length) {
            az.push(React.createElement('div', { className: 'dsma-line', key: 'mh' },
              '公网线路（哪个通用哪个，点一下复制）：'))
            az.push(React.createElement('div', { className: 'dsma-row', key: 'mr' },
              app.mirrors.map(function (m) {
                return React.createElement('button', {
                  key: m.name, type: 'button', className: 'dsma-btn',
                  onClick: function () { copy(m.url, m.name) },
                }, m.name)
              })))
          }
        } else if (app) {
          az.push(React.createElement('div', { className: 'dsma-line', key: 'na' },
            '插件目录里还没有安装包。把 dsh-mobile.apk 放到：' + app.apkPath))
          az.push(React.createElement('div', { className: 'dsma-dev-meta', key: 'pub2' },
            '也可以直接用外网直链：' + app.publicUrl))
        } else {
          az.push(React.createElement('div', { className: 'dsma-line', key: 'ld' }, '读取中…'))
        }
        adv.push(React.createElement('div', { className: 'dsma-sec', key: 'az' }, az))
      }
      children.push(React.createElement('div', { className: 'dsma-card', key: 'adv' }, adv))

      if (message) children.push(React.createElement('div', { className: 'dsma-ok', key: 'msg' }, message))
      if (error) children.push(React.createElement('div', { className: 'dsma-error', key: 'err' }, error))

      // ---- 二维码弹窗（公网 / 内网 / 下载 App 共用）
      if (qrView) {
        var qz = []
        var qTitle = '扫这张码'
        var qStep = ''
        var qSvg = qrView.svg
        var qUrl = qrView.url || ''
        if (qrView.kind === 'app') {
          qz.push(React.createElement('div', { className: 'dsma-tabs' },
            React.createElement('button', {
              type: 'button', className: 'dsma-tab' + (appRoute === 'lan' ? ' dsma-tab-on' : ''),
              onClick: function () { setAppRoute('lan') },
            }, '局域网直发'),
            React.createElement('button', {
              type: 'button', className: 'dsma-tab' + (appRoute === 'net' ? ' dsma-tab-on' : ''),
              onClick: function () { setAppRoute('net') },
            }, '公网镜像')))
          if (appRoute === 'lan') {
            qTitle = '下载 App · 局域网直发'
            qSvg = app && app.qrLanSvg
            qUrl = (app && app.qrLanUrl) || ''
            qStep = '手机和电脑连同一个 WiFi：用手机相机扫一下 → 打开下载页 → 点「下载 APK」→ 安装。'
            if (!qSvg) qStep = '没探测到局域网地址，切到上面的「公网镜像」下载。'
          } else {
            qTitle = '下载 App · 公网镜像'
            qSvg = app && app.qrSvg
            qUrl = (app && app.qrUrl) || ''
            qStep = '不在同一个 WiFi 也能用：用手机相机扫一下 → 下载安装包 → 点安装（提示未知来源时允许即可）。'
          }
        } else {
          qTitle = qrView.title || '扫码连接'
          if (qrView.payloadError) {
            // 配对串没拿到 / 不合法：不画二维码，直接说清楚（画一张内容不对的码
            // 只会让手机报「不是可用的配对码」，比这里难查得多）
            qStep = '配对串获取失败，请重试。'
          } else {
            qStep = '打开手机上的「DSH 掌上通」→ 点「扫码配对」→ 扫这张码。'
              + (qrView.expiresAt ? '（' + fmtTime(qrView.expiresAt) + ' 前有效，只能用一次）' : '')
              + '扫不出来就点下面的「复制配对串」，在 App 里粘贴也可以。'
          }
        }
        qz.push(React.createElement('div', { className: 'dsma-modal-title' }, qTitle))
        qz.push(React.createElement('div', { className: 'dsma-modal-step' }, qStep))
        if (qrView.payloadError) {
          qz.push(React.createElement('div', { className: 'dsma-error' },
            qrView.payloadError + '（没有画出二维码：这张码里必须是配对串，拿不到就不画）'))
        }
        if (qSvg) {
          qz.push(React.createElement('div', {
            className: 'dsma-qr', dangerouslySetInnerHTML: { __html: qSvg },
          }))
        } else if (qrView.kind === 'app') {
          qz.push(React.createElement('div', { className: 'dsma-line' },
            app === null ? '正在读取安装包信息…' : '这台电脑上还没有安装包，换「公网镜像」试试。'))
        } else if (!qrView.payloadError) {
          qz.push(React.createElement('div', { className: 'dsma-line' },
            busy === 'pair' ? '正在生成…' : '二维码没生成出来，关掉再点一次试试。'))
        }
        if (qUrl) {
          qz.push(React.createElement('div', { className: 'dsma-link' },
            (qrView.kind === 'app' ? '下载地址：' : '这张码里的地址：') + qUrl))
        }
        var qActions = []
        if (qUrl) {
          qActions.push(React.createElement('button', {
            type: 'button', className: 'dsma-btn',
            onClick: function () { copy(qUrl, '链接') },
          }, '复制链接'))
        }
        if (qrView.payload) {
          qActions.push(React.createElement('button', {
            type: 'button', className: 'dsma-btn',
            onClick: function () { copy(qrView.payload, '配对串') },
          }, '复制配对串'))
        }
        qActions.push(React.createElement('button', {
          type: 'button', className: 'dsma-btn dsma-btn-primary',
          onClick: function () { setQrView(null) },
        }, '关闭'))
        qz.push(React.createElement('div', { className: 'dsma-modal-actions' }, qActions))
        children.push(React.createElement('div', {
          className: 'dsma-modal-backdrop', key: 'qrmodal',
          onMouseDown: function () { setQrView(null) },
        }, React.createElement('div', {
          className: 'dsma-modal',
          onMouseDown: function (e) { e.stopPropagation() },
        }, qz)))
      }

      // ---- 内网：只改 IP / 端口的小弹窗
      if (lanOpen) {
        var lz = []
        lz.push(React.createElement('div', { className: 'dsma-modal-title' }, '内网连接'))
        lz.push(React.createElement('div', { className: 'dsma-modal-step' },
          '手机和这台电脑连同一个 WiFi。一般不用改，默认就是本机地址。'))
        var cands = lanCandidates()
        if (!cands.length) {
          lz.push(React.createElement('div', { className: 'dsma-dev-meta', style: { marginTop: '10px' } },
            '没探测到可用的内网地址（169.254.x、172.16-31.x 这类虚拟网卡/自分配地址都会被忽略）。'
            + '请在手机或电脑的 WiFi 设置里确认这台电脑的局域网 IP，填到下面。'))
        }
        if (cands.length > 1) {
          lz.push(React.createElement('div', { className: 'dsma-dev-meta', style: { marginTop: '10px' } }, '探测到多个地址，选一个：'))
          lz.push(React.createElement('div', { className: 'dsma-tabs' }, cands.map(function (host) {
            return React.createElement('button', {
              key: host, type: 'button',
              className: 'dsma-tab' + (lanIp === host ? ' dsma-tab-on' : ''),
              onClick: function () { setLanIp(host) },
            }, host)
          })))
        }
        lz.push(React.createElement('div', { className: 'dsma-field' },
          React.createElement('div', { className: 'dsma-dev-meta' }, '电脑的局域网 IP'),
          React.createElement('input', {
            className: 'dsma-input', value: lanIp, placeholder: '192.168.1.100',
            onChange: function (e) { setLanIp(e.target.value) },
          })))
        lz.push(React.createElement('div', { className: 'dsma-field' },
          React.createElement('div', { className: 'dsma-dev-meta' }, '端口（不知道就别改）'),
          React.createElement('input', {
            className: 'dsma-input', value: lanPort, placeholder: '3091',
            onChange: function (e) { setLanPort(e.target.value) },
          })))
        lz.push(React.createElement('div', { className: 'dsma-modal-actions' },
          React.createElement('button', {
            type: 'button', className: 'dsma-btn',
            onClick: function () { setLanOpen(false) },
          }, '取消'),
          React.createElement('button', {
            type: 'button', className: 'dsma-btn dsma-btn-primary', disabled: busy !== '',
            onClick: confirmLan,
          }, busy === 'pair' ? '生成中…' : '生成二维码')))
        children.push(React.createElement('div', {
          className: 'dsma-modal-backdrop', key: 'lanmodal',
          onMouseDown: function () { setLanOpen(false) },
        }, React.createElement('div', {
          className: 'dsma-modal',
          onMouseDown: function (e) { e.stopPropagation() },
        }, lz)))
      }

      // ---- 公网前的风险确认（每次都要勾选，和 dsh-pocket 一致）
      if (riskOpen) {
        children.push(React.createElement('div', {
          className: 'dsma-modal-backdrop', key: 'risk',
          onMouseDown: function () { setRiskOpen(false) },
        },
          React.createElement('div', {
            className: 'dsma-modal',
            style: { textAlign: 'left', maxWidth: '440px' },
            onMouseDown: function (e) { e.stopPropagation() },
          },
            React.createElement('div', { className: 'dsma-modal-title' }, '生成公网二维码前，请先确认'),
            React.createElement('div', { className: 'dsma-modal-step' },
              '这一步会把这台电脑上的 DSH 暴露到互联网。DSH 能执行代码、读写文件，'
              + '任何人拿到公网地址和设备令牌，都可能访问甚至操作你的电脑。'),
            React.createElement('div', { className: 'dsma-modal-step', style: { marginTop: '10px' } },
              '请确认：① 别把二维码/配对串发给别人；② 不用时在「高级设置」里关掉公网隧道；'
              + '③ 隧道地址每次重启电脑都可能变，变了在手机上重新扫一次码；④ 公司/涉密网络请先确认合规。'),
            React.createElement('label', {
              style: { display: 'flex', alignItems: 'center', gap: '8px', marginTop: '14px',
                fontSize: '13px', cursor: 'pointer' },
            },
              React.createElement('input', {
                type: 'checkbox', checked: riskOk,
                onChange: function (e) { setRiskOk(e.target.checked) },
              }),
              '我已知情，同意开启'),
            React.createElement('div', { className: 'dsma-modal-actions', style: { justifyContent: 'flex-end' } },
              React.createElement('button', {
                type: 'button', className: 'dsma-btn',
                onClick: function () { setRiskOpen(false); setRiskOk(false) },
              }, '取消'),
              React.createElement('button', {
                type: 'button', className: 'dsma-btn dsma-btn-primary',
                disabled: busy !== '', onClick: confirmRisk,
              }, '我已知情，生成二维码')))))
      }

      return React.createElement('div', { className: 'dsma-root' }, children)
    }

    // ------------------------------------------------------------------ 设置页的跳转入口
    /**
     * 「手机接入」原来的完整面板已经并入侧边栏的「移动设备」抽屉（见下方 apply 里的
     * 跨 bundle 桥 + pc-plugin/patches/restore-gateway-panel-fix.ps1）。
     * 设置页里保留的这条只做跳转，不再渲染第二份功能，避免同一件事有两个入口。
     */
    function MobileAccessRedirect() {
      var tipState = React.useState('')
      var tip = tipState[0]
      var setTip = tipState[1]

      function openPanel() {
        var bridge = (typeof window === 'undefined') ? null : window.__DSH_MOBILE_GATEWAY__
        if (bridge && typeof bridge.open === 'function') {
          setTip('')
          bridge.open()
          return
        }
        setTip('没找到「移动设备」面板：请确认 dsh-plugin-mobile-gateway 已安装、'
          + '并已重启过 DSH；也可以直接点左侧边栏底部的手机图标打开。')
      }

      return React.createElement('div', { className: 'dsma-root' },
        React.createElement('div', { className: 'dsma-title' }, '手机接入'),
        React.createElement('div', { className: 'dsma-desc' },
          '手机接入的全部功能（下载 App、公网/内网二维码、已配对设备）'
          + '都已并入侧边栏的「移动设备」面板，这里不再重复一份。'),
        React.createElement('div', { className: 'dsma-row' },
          React.createElement('button', {
            type: 'button', className: 'dsma-btn dsma-btn-primary', onClick: openPanel,
          }, '打开「移动设备」面板')),
        tip ? React.createElement('div', { className: 'dsma-error' }, tip) : null,
      )
    }

    // ------------------------------------------------------------------ 插件体
    function apply(ctx) {
      try {
        var styles = adoptStyles()
        ctx.effect(function () {
          return function () { styles.remove() }
        }, NAME + ': styles')

        // 跨 bundle 桥：「移动设备」面板（第三方网关插件，由补丁脚本叠加）会渲染
        // 本插件的主面板组件，于是两个入口合成一个。
        // 两个 bundle 都 require('react')，拿到同一个 React 实例，组件可以直接传。
        // 网关面板是用户点击后才挂载的，那时本插件早已 apply；这里再补一个事件广播，
        // 覆盖「面板先挂载、插件后 apply」的极端顺序（对方还会做兜底轮询）。
        try {
          window.__DSH_MOBILE_ACCESS__ = {
            version: 1,
            Row: MobileAccessRow,
            Redirect: MobileAccessRedirect,
          }
          window.dispatchEvent(new Event('dsh-mobile-access-ready'))
        } catch (e) { /* 非浏览器环境：忽略 */ }

        // 设置里的入口改成「跳转」，真正的面板在「移动设备」里。
        ctx.slots.inject('settings.general.item', function () {
          return ctx.slots.register(
            {
              name: 'settings.general.item',
              id: SETTINGS_ID,
              order: 14,
              label: '手机接入',
              inject: function () { return {} },
            },
            MobileAccessRedirect,
          )
        })
      } catch (error) {
        console.error('[' + NAME + '] client apply failed:', error)
      }
    }

    exports.name = NAME
    exports.inject = ['slots']
    exports.apply = apply
    exports.MobileAccessRow = MobileAccessRow
    exports.MobileAccessRedirect = MobileAccessRedirect
    exports.__test = { API: API, SETTINGS_ID: SETTINGS_ID, fmtTime: fmtTime }
    return module.exports
  },
})
