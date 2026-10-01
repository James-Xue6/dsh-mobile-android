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
      '.dsma-qr svg{width:220px;height:220px;}',
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

    // ------------------------------------------------------------------ 组件
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
      var pairState = React.useState(null)
      var pairing = pairState[0]
      var setPairing = pairState[1]
      var nameState = React.useState(readDeviceName())
      var deviceName = nameState[0]
      var setDeviceName = nameState[1]
      var urlState = React.useState('')
    var riskState = React.useState(false)
    var riskOkState = React.useState(false)
        var appState = React.useState(null)
      var manualUrl = urlState[0]
      var setManualUrl = urlState[1]

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
                ? '正在开启隧道；首次运行需先下载 cloudflared（约 20-50MB），通常 1-2 分钟'
                : (action === 'off' ? '已关闭公网隧道' : '正在重启隧道'))
              return loadStatus(true)
            })
            .catch(function (e) { setError(e.message) })
            .then(function () { setBusy('') })
        }

        function makePairing() {
        setBusy('pair'); setError(''); setMessage('')
        var name = deviceName.trim() || '我的手机'
        writeDeviceName(name)
        var lanUrl = status && status.lan && status.lan.urls && status.lan.urls.length
          ? status.lan.urls[0] : ''
        var body = { name: name }
        var tunnelUrl = status && status.cloudflare && status.cloudflare.publicUrl
            ? status.cloudflare.publicUrl : ''
          // 隧道在线时优先用公网地址：手机扫码拿到的就是能在外网用的地址
          var chosen = manualUrl.trim() || tunnelUrl || lanUrl
        if (chosen) body.publicUrl = chosen
        request('POST', '/pair', body)
          .then(function (data) {
            setPairing(data)
            setMessage('二维码已生成，有效期 5 分钟，仅可使用一次')
            return loadDevices()
          })
          .catch(function (e) { setError(e.message) })
          .then(function () { setBusy('') })
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

      var children = []

      children.push(React.createElement('div', { className: 'dsma-title', key: 'title' }, '手机接入'))
      children.push(React.createElement(
        'div', { className: 'dsma-desc', key: 'desc' },
        '用手机遥控这台电脑上的 DSH。开启网关后生成二维码，用「DSH 掌上通」App 扫码即可接入；'
        + '外网可自行反代后填域名。协议层由 dsh-plugin-mobile-gateway 提供，本面板只负责接入编排。',
      ))

      // ---- 状态
      var st = []
      st.push(React.createElement('div', { className: 'dsma-card-title', key: 'h' }, '接入状态'))
      if (status === null) {
        st.push(React.createElement('div', { className: 'dsma-line', key: 'l' }, error ? ('读取失败：' + error) : '读取中…'))
      } else {
        st.push(React.createElement('div', { className: 'dsma-row', key: 'chips' },
          React.createElement('span', { className: 'dsma-chip' },
            React.createElement('i', { className: 'dsma-dot', style: { background: enabled ? '#16a34a' : '#9ca3af' } }),
            enabled ? ('网关已开启 · ' + (mode === 'persistent' ? '常驻' : mode === 'temporary' ? '临时' : mode)) : '网关已关闭'),
          React.createElement('span', { className: 'dsma-chip' },
            React.createElement('i', { className: 'dsma-dot', style: { background: lanOk ? '#16a34a' : '#f59e0b' } }),
            lanOk ? ('局域网监听 :' + (status.lan && status.lan.port)) : '局域网未监听'),
          React.createElement('span', { className: 'dsma-chip' }, '在线设备 ' + (status.connectedClients || 0)),
          React.createElement('span', { className: 'dsma-chip' }, '网关 v' + (status.version || '?')),
        ))
        if (status.lan && status.lan.error) {
          st.push(React.createElement('div', { className: 'dsma-error', key: 'lanerr' }, '局域网监听异常：' + status.lan.error))
        }
        st.push(React.createElement('div', { className: 'dsma-row', key: 'actions' },
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
          }, '刷新'),
        ))
        if (lanUrls.length > 0) {
          st.push(React.createElement('div', { className: 'dsma-line', key: 'urls' }, '手机可达地址：' + lanUrls.join('、')))
        }
      }
      children.push(React.createElement('div', { className: 'dsma-card', key: 'status' }, st))

        // ---- 公网访问（网关内置 Cloudflare 隧道）
        var cf = (status && status.cloudflare) || null
        var tz = []
        tz.push(React.createElement('div', { className: 'dsma-card-title', key: 'h' }, '公网访问（Cloudflare 隧道）'))
        tz.push(React.createElement('div', { className: 'dsma-desc', key: 'd' },
          '不用开端口、不用自建反代：开启后由网关拉起 Cloudflare 隧道，手机在任何网络（含 5G）都能连，'
          + '拿到的是受信任的真证书，不必打开「信任自签名证书」。隧道在线时，配对二维码会自动带上公网地址。'))
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
        children.push(React.createElement('div', { className: 'dsma-card', key: 'tunnel' }, tz))

      // ---- 配对
      var pr = []
      pr.push(React.createElement('div', { className: 'dsma-card-title', key: 'h' }, '生成配对二维码'))
      pr.push(React.createElement('div', { className: 'dsma-field', key: 'f1' },
        React.createElement('div', { className: 'dsma-dev-meta' }, '设备名'),
        React.createElement('input', {
          className: 'dsma-input', value: deviceName, placeholder: '例如：我的手机',
          onChange: function (e) { setDeviceName(e.target.value) },
        }),
      ))
      pr.push(React.createElement('div', { className: 'dsma-field', key: 'f2' },
        React.createElement('div', { className: 'dsma-dev-meta' }, '地址（留空则用上面的局域网地址）'),
        React.createElement('input', {
          className: 'dsma-input', value: manualUrl,
          placeholder: 'ws://192.168.1.100:3091/ws/mobile 或 wss://你的域名/ws/mobile',
          onChange: function (e) { setManualUrl(e.target.value) },
        }),
      ))
      pr.push(React.createElement('div', { className: 'dsma-row', key: 'f3' },
        React.createElement('button', {
          type: 'button', className: 'dsma-btn dsma-btn-primary',
          disabled: busy !== '' || !enabled, onClick: makePairing,
        }, busy === 'pair' ? '生成中…' : '生成二维码'),
      ))
      if (!enabled) {
        pr.push(React.createElement('div', { className: 'dsma-line', key: 'need' }, '请先在上方开启网关。'))
      }
      if (pairing && pairing.svg) {
        pr.push(React.createElement('div', { className: 'dsma-qr', key: 'qr', dangerouslySetInnerHTML: { __html: pairing.svg } }))
        if (pairing.payload) {
          pr.push(React.createElement('div', { className: 'dsma-line', key: 'info' },
            '配对给到：' + pairing.payload.publicUrl + '　有效期至 ' + fmtTime(pairing.payload.expiresAt)))
        }
        if (pairing.qrPayload) {
          pr.push(React.createElement('div', { className: 'dsma-token', key: 'tok' }, pairing.qrPayload))
          pr.push(React.createElement('div', { className: 'dsma-row', key: 'copy' },
            React.createElement('button', {
              type: 'button', className: 'dsma-btn', onClick: function () { copy(pairing.qrPayload, '配对串') },
            }, '复制配对串'),
            React.createElement('span', { className: 'dsma-dev-meta' }, 'App 里也可用「粘贴配对串」直接接入，不必扫码。'),
          ))
        }
      }
      // ---- 手机 App 安装包：扫码即下载（走本机局域网发文件）
        var ap = appState[0]
        var az = []
        az.push(React.createElement('div', { className: 'dsma-card-title', key: 'h' }, '手机 App 安装包'))
        az.push(React.createElement('div', { className: 'dsma-desc', key: 'd' },
          '手机扫码下载安装包（从公开仓库/CDN 取，不依赖任何人的本机网络）；'
          + '手机和电脑在同一 WiFi 时可改用下面的「局域网直发」秒下。装好后回到上面「生成配对二维码」扫一次即可接入。'))
        if (ap && ap.available) {
          if (ap.qrSvg) {
            az.push(React.createElement('div', {
              key: 'qr', className: 'dsma-qr',
              dangerouslySetInnerHTML: { __html: ap.qrSvg },
            }))
          }
          if (ap.qrUrl) az.push(React.createElement('div', { className: 'dsma-token', key: 'u' }, ap.qrUrl))
          az.push(React.createElement('div', { className: 'dsma-row', key: 'r1' },
            React.createElement('button', {
              type: 'button', className: 'dsma-btn', disabled: !ap.qrUrl,
              onClick: function () { copy(ap.qrUrl, 'CDN 地址') },
            }, '复制 CDN 链接'),
            React.createElement('button', {
              type: 'button', className: 'dsma-btn', disabled: !ap.githubUrl,
              onClick: function () { copy(ap.githubUrl, 'GitHub 地址') },
            }, '复制 GitHub 直链')))
          if (ap.lanUrls && ap.lanUrls.length) {
            az.push(React.createElement('div', { className: 'dsma-row', key: 'r2' },
              React.createElement('button', {
                type: 'button', className: 'dsma-btn', disabled: !ap.lanUrls[0],
                onClick: function () { copy(ap.lanUrls[0], '局域网地址') },
              }, '复制局域网直发地址')))
            az.push(React.createElement('div', { className: 'dsma-dev-meta', key: 'lan' },
              '局域网直发（同一 WiFi 秒下）：' + ap.lanUrls[0]))
          }
          az.push(React.createElement('div', { className: 'dsma-line', key: 'meta' },
            'v' + (ap.version || '?') + ' · ' + (ap.size / 1024).toFixed(1) + ' KB · ' + ap.name
            + '（扫码 → 浏览器下载 → 点安装，允许未知来源即可）'))
          az.push(React.createElement('div', { className: 'dsma-dev-meta', key: 'gh' },
            'GitHub 直链（CDN 不通时用）：' + (ap.githubUrl || '')))
        } else if (ap) {
          az.push(React.createElement('div', { className: 'dsma-line', key: 'na' },
            '插件目录里还没有安装包。把 dsh-mobile.apk 放到：' + ap.apkPath))
          az.push(React.createElement('div', { className: 'dsma-dev-meta', key: 'pub2' },
            '也可以直接用外网直链：' + ap.publicUrl))
        } else {
          az.push(React.createElement('div', { className: 'dsma-line', key: 'ld' }, '读取中…'))
        }
        children.push(React.createElement('div', { className: 'dsma-card', key: 'app' }, az))

        children.push(React.createElement('div', { className: 'dsma-card', key: 'pair' }, pr))

      // ---- 设备
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
                '最近连接 ' + fmtTime(d.lastSeenAt) + (d.online ? '　● 在线' : '')),
            ),
            React.createElement('button', {
              type: 'button', className: 'dsma-btn dsma-btn-danger',
              disabled: busy !== '', onClick: function () { revoke(d.id, d.name || d.id) },
            }, '撤销'),
          ))
        })
      }
      children.push(React.createElement('div', { className: 'dsma-card', key: 'devices' }, dv))

      if (message) children.push(React.createElement('div', { className: 'dsma-ok', key: 'msg' }, message))
      if (error) children.push(React.createElement('div', { className: 'dsma-error', key: 'err' }, error))

      // ---- 开启公网前的安全免责声明（每次都要勾选，和 dsh-pocket 一致）
        if (riskOpen) {
          children.push(React.createElement('div', {
            key: 'risk',
            style: { position: 'fixed', inset: '0', background: 'rgba(0,0,0,.45)', zIndex: 9999,
              display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '24px' },
          },
            React.createElement('div', {
              style: { background: 'var(--dsw-alias-bg-layer-1)', color: 'var(--dsw-alias-label-primary)',
                borderRadius: '14px', padding: '20px 22px', maxWidth: '560px', width: '100%',
                boxShadow: '0 12px 40px rgba(0,0,0,.35)' },
            },
              React.createElement('div', { style: { fontSize: '15px', fontWeight: 600, marginBottom: '10px' } },
                '⚠ 安全免责声明'),
              React.createElement('div', { style: { fontSize: '13px', lineHeight: '21px' } },
                '开启公网 = 把这台电脑上的 DSH 暴露到互联网。DSH 能执行代码、读写文件，'
                + '任何人拿到公网地址和设备令牌，都可能访问甚至操作你的电脑。'),
              React.createElement('div', { style: { fontSize: '13px', lineHeight: '21px', marginTop: '10px' } },
                '请确认：① 妥善保管设备令牌，别把配对二维码/配对串发给别人；② 不用时立即「关闭公网隧道」；'
                + '③ 隧道域名每次重启电脑都会变，变了在手机上重新扫一次码；④ 公司/涉密网络请先确认合规。'),
              React.createElement('label', {
                style: { display: 'flex', alignItems: 'center', gap: '8px', marginTop: '14px',
                  fontSize: '13px', cursor: 'pointer' },
              },
                React.createElement('input', {
                  type: 'checkbox', checked: riskOk,
                  onChange: function (e) { setRiskOk(e.target.checked) },
                }),
                '我已知情，同意开启'),
              React.createElement('div', {
                style: { display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '16px' },
              },
                React.createElement('button', {
                  type: 'button', className: 'dsma-btn',
                  onClick: function () { setRiskOpen(false); setRiskOk(false) },
                }, '取消'),
                React.createElement('button', {
                  type: 'button', className: 'dsma-btn dsma-btn-primary',
                  onClick: function () {
                    if (!riskOk) { setError('请勾选「我已知情」后再开启公网'); return }
                    setRiskOpen(false); setRiskOk(false); tunnel('on', true)
                  },
                }, '我已知情，同意开启')))))
        }
        return React.createElement('div', { className: 'dsma-root' }, children)
    }

    // ------------------------------------------------------------------ 插件体
    function apply(ctx) {
      try {
        var styles = adoptStyles()
        ctx.effect(function () {
          return function () { styles.remove() }
        }, NAME + ': styles')

        ctx.slots.inject('settings.general.item', function () {
          return ctx.slots.register(
            {
              name: 'settings.general.item',
              id: SETTINGS_ID,
              order: 14,
              label: '手机接入',
              inject: function () { return {} },
            },
            MobileAccessRow,
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
    exports.__test = { API: API, SETTINGS_ID: SETTINGS_ID, fmtTime: fmtTime }
    return module.exports
  },
})
