# dsh-mobile-access（DSH 掌上通 · PC 接入插件）

给 **DSH 掌上通** App 用的 PC 端接入面板。协议层**一行不重写** —— 全部复用
[`dsh-plugin-mobile-gateway`](https://www.npmjs.com/package/dsh-plugin-mobile-gateway)（MIT）。

## 一键安装

在仓库根目录执行：

```powershell
pwsh -File .\pc-plugin\install.ps1
```

脚本会：复制插件到 `~/.dsh/local-plugins/`、把 `dist/dsh-mobile.apk` 放进插件目录、
在 profile 的 `package.json` 登记依赖与 bundles、补一段 `mobile-gateway` 配置（lanPort 3091）。
**改完重启一次 DSH 生效。**

## 面板能做什么

| 区块 | 作用 |
|---|---|
| 接入状态 | 网关开关状态、局域网监听、在线设备数、网关版本 |
| 网关运行模式 | 常驻 / 临时 / 关闭 三态切换 |
| **手机 App 安装包** | **手机连同一 WiFi 扫码即下载安装 APK**（由插件内置的静态服务从电脑直发） |
| 生成配对二维码 | 扫码 / 配对串接入；隧道在线时二维码自动带公网地址 |
| 已配对设备 | 列出设备并可撤销（令牌立即失效） |
| 公网访问（Cloudflare 隧道） | 一键起随机域名隧道；无需开端口、无需自建反代；开启前有安全声明弹窗 |
| 意见反馈 | —（在 App 的「设置 → 意见反馈」里） |

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
