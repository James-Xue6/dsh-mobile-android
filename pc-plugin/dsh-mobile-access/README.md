# dsh-mobile-access（DSH 掌上通 · PC 接入插件）

给 **DSH 掌上通** App 用的 PC 端接入面板。协议层**一行不重写** —— 全部复用
[`dsh-plugin-mobile-gateway`](https://www.npmjs.com/package/dsh-plugin-mobile-gateway)（MIT）。

## 一键安装

在仓库根目录执行：

```powershell
pwsh -File .\pc-plugin\install.ps1
```

脚本会：复制插件到 `~/.dsh/local-plugins/`、把 `dist/dsh-mobile.apk` 放进插件目录
（并把 `app/version.txt` 写成该 APK 的 `versionName`，面板显示的就是这个包的真实版本）、
在 profile 的 `package.json` 登记依赖与 bundles、补一段 `mobile-gateway` 配置（lanPort 3091）。
**改完重启一次 DSH 生效。**

## 二维码里编的到底是什么（别改错）

本插件**不自己画二维码**：只把配对串 `qrPayload` 交给网关，由网关的
`QRCode.toString(qrPayload)` 生成 SVG（网关 0.9.0 的 `lib/index.mjs` 里 `/mgw/pair`），
面板原样渲染这张 SVG。也就是说：

- **配对二维码里是配对串**（UTF-8 JSON → 无 padding Base64URL，约 550 字符），
  **不是**隧道地址、也不是 `ws://…` 那种 URL。地址只以文字形式显示在二维码下方
  （「这张码里的地址：…」），连同「复制配对串」按钮作为扫码失败时的兜底。
- 出码前 `client.js` 会再验一次这个串（非空、长度 > 100、能解出 `version=2` 的 JSON、
  且带 `publicUrl`/`pairingCode`）；**验不过就不画二维码**，直接提示
  「配对串获取失败，请重试」——绝不画一张内容不对的码让手机去报「不是可用的配对码」。
- 配对串约 550 字符 → 二维码是 89×89 模块，所以 `.dsma-qr svg` 按 300px 等比显示
  （220px 时单模块只有 2.4px，手机容易扫不出/扫错）。

## 局域网地址只给「手机真连得上」的

`index.js` 的 `lanIPv4()` 和 `client.js` 的 `isUsableLanHost()` 用同一套规则过滤，
再按 `192.168.x` > `10.x` > 其它排序：

| 网段 | 处理 | 原因 |
|---|---|---|
| `169.254.0.0/16` | 排除 | APIPA：没拿到 DHCP 的自分配地址 |
| `172.16.0.0/12` | 排除 | 虚拟网卡重灾区（VirtualBox / Hyper-V / WSL，如 `172.30.x`），手机路由不过去 |
| `100.64.0.0/10` | 排除 | 运营商级 NAT（CGNAT） |
| `127.0.0.0/8` | 排除 | 本机回环 |

一个可用地址都没有时：`/app` 的 `lanUrls` 为空、不生成局域网二维码，面板显示
「没探测到局域网地址，切到上面的『公网镜像』下载」；内网配对弹窗则提示手填 IP，
而不是给一张注定连不上的码。

## 公网镜像跟哪个 ref

`publicUrls()` 不再用 `v + versionName` 拼 tag（tag 只有跑 `release.ps1` 发版时才新增，
未发版时会一直指向旧包）。默认跟 **`main`**，也就是 App 自身更新检查用的同一个 ref；
要临时钉回某个 tag：设环境变量 `DSH_MOBILE_PUBLIC_REF=v0.8`。

## 面板在哪

**PC 端只有一个入口：左侧边栏底部的「移动设备」按钮**，点开就是抽屉面板。

本插件的主面板**就渲染在该抽屉的第一屏**（由
`pc-plugin/patches/restore-gateway-panel-fix.ps1` 叠加成「手机接入」卡片），只放三件事：

| 区块 | 作用 |
|---|---|
| **① 下载 App** | **手机连同一 WiFi 扫码即下载安装 APK**（由插件内置的静态服务从电脑直发） |
| **② 扫码连接** | 生成「公网二维码」/「内网二维码」，扫码即接入；隧道在线时公网码自动带公网地址 |
| 高级设置（默认收起） | 接入状态、网关三态开关、公网隧道（Cloudflare）、手动填地址生成配对码、已配对设备与吊销 |

原来它单独挂在 `设置 → 通用 →「手机接入」`，与「移动设备」是两个入口、用起来要来回找，所以并到了一起。

实现方式：本插件 `client.js` 在 apply 时把主面板组件挂到 `window.__DSH_MOBILE_ACCESS__`
并广播 `dsh-mobile-access-ready`；网关面板（由上面的补丁脚本叠加）直接渲染它。
`设置 → 通用 →「手机接入」`那一行保留但**只做跳转**
（按钮会调 `window.__DSH_MOBILE_GATEWAY__.open()` 把抽屉打开），不重复渲染功能。

> 网关是第三方包，升级会被覆盖，所以那半边改动走**打补丁 + 可重放脚本**：
> `pwsh -File .\pc-plugin\patches\restore-gateway-panel-fix.ps1`
> （脚本会校验网关版本为 `0.9.0`，不符就报错退出，不会把插件降级。）

## 它是怎么工作的

```
浏览器面板 (client.js)  --fetch-->  /dsh-mobile-access/*        （DSH web 端口，宿主代理）
宿主代理   (index.js)   --loopback--> http://127.0.0.1:<webPort>/mgw/*
dsh-plugin-mobile-gateway               （协议层：dsh-mobile-v1 / WebSocket）
DSH Host
```

另外 `index.js` 会在局域网另起一个**只发安装包**的小 HTTP 服务（默认 `8099`）：

```
GET  http://<本机局域网IP>:8099/app.apk   # 安装包，MIME 为 application/vnd.android.package-archive
GET  http://<本机局域网IP>:8099/          # 一个只有下载链接的极简页
```

它跑在 DSH 宿主进程内，所以走的是「按程序放行」的防火墙规则，手机在同一 WiFi 下直接可达；
端口可用环境变量 `DSH_MOBILE_APP_PORT` 改。**只回应这两个路径**，不做目录服务。

## 为什么路由不放 `/api` 下

DSH 的 `/api/*` 有浏览器信任围栏（未知路径 401）；实测：

| 路径 | 结果 |
|---|---|
| `/api/dsh-mobile-access/status` | 401（被围栏拦） |
| `/dsh-mobile-access/status` | 200 |
| `/mgw/status` | 200（网关自己也因此挂在 `/mgw`） |

## 依赖

- `dsh-plugin-mobile-gateway` 0.9.0 及以上（提供全部协议能力）
- 生成「安装包/配对」二维码时复用网关依赖里的 `qrcode`；解析不到会优雅降级为纯链接
