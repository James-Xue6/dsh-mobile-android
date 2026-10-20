# net/ —— 我们自己的网络层（自维护分支基线）

本目录是 **DSH 掌上通 PC 插件的网络层**：WebSocket 网关、设备配对与鉴权、会话/工作区/历史/发消息、
审批与提问桥接、命令、附件收下发、定时任务、插件管理、Cloudflare 隧道与公网自检、局域网监听。

它 fork 自 MIT 许可的 [`dsh-plugin-mobile-gateway`](https://github.com/Clarklevis1995/dsh-plugin-mobile-gateway)
**0.9.0**，从 v2.0.0 起由本仓库自己维护（等于我们把上游拉成自己的分支线），**不再**依赖上游插件包，
也**不再**跟随上游的发版节奏。原始许可证与版权见同目录 `LICENSE`；协议说明见 `PROTOCOL.md`。

## 我们保留了什么 / 不要什么

| 上游的东西 | 我们的处理 | 原因 |
|---|---|---|
| `lib/index.mjs`、`dsh-host-adapter.mjs`、`devices`、`cloudflare-tunnel`、`cloudflared-binary`、`session-follower`、`plugin-management`、`gateway-state`、`gateway-tools`、`web-restart`、`wire-json` | ✅ 保留 | 手机端能用到的全部协议能力 |
| `lib/client.js`（上游的面板壳/UI） | ❌ 丢弃 | 面板由本插件自己实现（合并后的 `client.js`），不再背两份 UI |
| 0.10.1 官方语音输入 `speech-input-v1` | ❌ 不移植 | App 没有录音/转写 UI，单边适配是死代码 |
| 0.10.2 文件卡片 / 正文文件引用 | ❌ 不移植 | App 不渲染文件卡片与预览 |
| 0.10.0 面板重构（响应式窗口/主题/QR 模态） | ❌ 不跟随 | 我们已自己做居中弹窗；上游面板壳也已丢弃 |

## 已经叠加的改动（相对上游 0.9.0）

1. **DSH 0.2.1-rc.2 的 Connection Peer**：`index.mjs` 用 `ctx.connection.createPeer()` 建 peer、
   卸载时 `dispose`；`dsh-host-adapter.mjs` 的 invoke / 首帧 stream / session-follow / session-control /
   workspace-follow 全部透传 `peer`。缺了会报 `Cannot read properties of undefined (reading 'signal')`。
2. **从 0.10.3 移植：公网入口自检**（`cloudflare-tunnel.mjs` + `public-endpoint-check.mjs` +
   `gateway-probe.mjs`）。隧道 `online` 只代表 cloudflared 连上了 Cloudflare，不代表公网真能解析/握手/路由；
   自检跑 DNS → TLS → WebSocket 三段，401 时靠随机 nonce 确认「回 401 的确实是本网关」。
   面板里对应「检测公网入口」按钮，接口 `POST /mgw/cloudflare/check`。
3. **fork 期补丁（原 install.ps1 的 6 个补丁，已并入本目录）**：跨会话提醒、`hello` 帧带当前公网地址、
   地址变化广播、通用文件附件（`file-uploads`，上游 0.10.x 没有）、子智能体寻址。
4. **vendor 适配**：`devices.js` → `devices.cjs`（本包 `type=module`，否则 CJS 被当 ESM 解析）；
   版本号读取从 `../package.json` 改为 `../../package.json`（目录深了两级）。

## 以后怎么跟上游（维护流程）

上游出新版时**不要整包覆盖**，按这个顺序：

1. `npm pack dsh-plugin-mobile-gateway@<新版本>` 解包到临时目录，只拿 `lib/` 与我们保留的文件对比；
2. 逐文件 diff，判断改动属于：① 协议/网络（可考虑吸收）② UI 壳（丢弃）③ App 专属能力（丢弃）；
3. 吸收的改动改到 `net/`，**不要**把上游 `client.js` 带回来；
4. 改完必须过两道验收：
   - `node --check` 全部 `.mjs`；
   - 本仓库探针：真配对 → `{type:'sessions'}` / `{type:'workspaces'}` 要有真实条数、`errors` 为空；
     外加 `POST /mgw/cloudflare/check` 的 `endpointHealth.state` 正常；
5. 在 `THIRD-PARTY-NOTICES.md` 与本文件同步记录「这次吸收了哪些、丢了哪些」。

## 边界约束

- `net/` 只放**网络/协议**代码：不许出现界面组件、App 渲染逻辑、或只有上游 App 才消费的协议字段；
- 新增能力必须满足「我们的 App 能真的用上」，否则不做（语音/文件卡片就是先例）；
- `net/` 里的文件不再有「上游一键更新」这回事——我们就是这条线的维护者。
