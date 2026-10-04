# 下一步计划（NEXT-UPDATE）

> 状态标记：`[x]` 已完成并验证 / `[~]` 部分完成 / `[ ]` 待办。
> 最近更新：2026-10-04（v0.86.4 发版后核对）。

## 一、已完成（保留记录，避免重复讨论）

- [x] **跨会话/后台通知** —— 正解在**服务端**：网关插件新增 `hasAnyInteractionClient()`，放宽提问/审批两道 waterfall 门；
  并让被 `filterSessionId` 过滤的连接照样收到（带 `global:true`，字段最小化）。**已发版 v0.86.4，用户实测通过。**
  - 作废的三条客户端 hack（勿重提）：第二条 WS 连接（握手永远超时）/ `unsubscribe` 窗口（网关不推新交互）/ 周期强制重连（会毁输入与回答）。
- [x] **前台服务不再断线自杀** —— `KeepAliveService.sync()` 去掉 `Notifier.isConnected()` 判据。
- [x] **无界面时帧不再被静默丢弃** —— `GatewayClient.bgInteractionHook`（进程级通知钩子）。
- [x] **通知渠道** —— `dsh_pending_v2`(HIGH) + 独立 `dsh_service`；通知失败可诊断（`lastPostError`/`lastPostAt`）；
  设置页三行自查 + **渠道级深链**。
- [x] **通知 id 槽位** 200 → 4000；`approvalKey/questionKey` 收成唯一实现。
- [x] **卡片灰框** —— `Ui.CardBg` 自绘渐变在长卡片上整片压暗约 26% → 提问/审批卡改用框架 `Ui.cardGrad()`（像素证据）。
- [x] **卡片底部按钮可达** —— 按钮移出滚动区；**卡片内部滚动位置**跨重绑保留（`ChatItem.cardScrollY`）。
- [x] **提问卡内输入框草稿丢失** —— TextWatcher 边打边写回 `it.typed`。
- [x] **点输入框后卡片乱滚** —— 内容区高度改固定值 + 键盘弹起时**只最小幅度滚外层列表**、不碰卡片内部滚动。
- [x] **独立输入页方案** —— 曾实现，按用户要求**已撤回**（勿重提）。
- [x] **交付纪律** —— 未验证透不发版；先贴 APK 给用户验，通过后再 `release.ps1`。
- [x] **版本命名倒挂** —— v0.86.4 = versionCode 21，已按"只增不减"继续；后续一律走 `release.ps1`。

## 二、待办（按优先级）

### P0 · 网关插件补丁的工程化（最紧急，会静默失效）
- [ ] 两处改动（放宽门 + 跨会话广播）**只活在 `node_modules`**，网关升级/重装即失效。
  - 固化：`pc-plugin/patches/` 下加幂等 `patch-gateway-crosssession.ps1`（字符串锚点、支持 `--revert`），并让 `install.ps1` 调用；
  - 备份现位于 `lib\index.mjs.bak-crosssession`；
  - 补日志便于定位：`interaction replay` 打印 `session=`；门未通过时打印 `skipped (no mobile client)`。

### P1 · 通知在荣耀/华为上的顽固点
- [~] **渠道降级**：App 申请的 `IMPORTANCE_HIGH` 被 ROM 降为 DEFAULT（`mOriginalImp=4` → `mImportance=3`，`mUserLockedFields=0`）。
  - 已做：换新渠道 id + 设置页读出实况 + 渠道级深链；
  - 待做：自查行给出**可执行步骤**（"点这里 → 找到「需要处理」→ 打开横幅与响铃"）；
  - 待做：降低对系统横幅的依赖 —— App 内常驻待处理栏/角标（已有 `pendingBanner` / `pendingJumpTarget` 可复用）。
  - 结论：**换台荣耀/华为会重演**，只能引导 + 兜底，不能靠代码强行提权。
- [ ] **不建议 `setFullScreenIntent`**：Android 14+ 默认只授通话/闹钟类；且只在锁屏/息屏全屏，覆盖不了"正在用手机但没看该会话"的主场景。

### P1 · 仍缺真机验证的两项（需能造出"进程活着但无界面"）
- [ ] V2：界面被系统回收后，M1 钩子路径是否仍弹通知。本机 `always_finish_activities` **不销毁 Activity**（实测）。
  - 可选：临时调试包提供"主动 finish()"入口（**发布包必须保持不可达**），或找 ROM 上只杀 Activity 不杀进程的办法。
- [ ] V3 手机侧：断网/重连后 pending 提醒是否重新出现（**网关侧已验证**会补发：`interaction replay … questions=1`）。

### P2 · 产品功能（用户已点名）
- [ ] **底部「生成物」按钮**：列本次会话产出文件 + 直接打开/下载；**只允许下载生成物，其他不下**。
  - 协议已具备：`file-list` / `file-download-open` / `file-download-read`；"打开"走 `FileProvider` + `ACTION_VIEW`；
  - UI 复用现有安全 sheet 配方（不透明面板底 + 抓柄 + 可滚动）。
- [ ] 助手消息里的 **markdown 表格**未渲染（当前按纯文本显示）。

### P2 · 性能与数据一致性
- [ ] 网关日志 `query ok: models` 每秒数十次 → 排查客户端是否在轮询模型列表。
- [ ] 任务项渲染仍缺真实数据：宿主对移动端的 `tasks` 查询返回 `todos:null`（网关 `projection forwarded: key=todos` 在推事件）。

## 三、交付与运维提醒

- **发版流程**：`pwsh -File .\release.ps1 -Version X.Y[.Z] -Notes "…"`（自动 +1 versionCode、写 `dist/version.json`、打 tag、刷 CDN）。
- **网关升级后**：先检查 `lib\index.mjs` 是否还含 `hasAnyInteractionClient`；没有就重放补丁（P0 项落地后应自动）。
- **未验证不得写成已修复**；环境造不出条件时如实标注"未验证"。
---

## 方案 5 · 局域网自动重配（零成本，对所有用户成立）【待做 · 用户已确认记住】

**问题**：Cloudflare Quick Tunnel 的域名每次重启都会变 ✗ → 手机必须重新扫码 ✗

**思路**：隧道地址会变，但**局域网地址是固定的**（如 `192.168.2.29:3091`）✓
→ 让 App **回到家（同一 WiFi）时自动走局域网重配一次** ✓，顺便拿到电脑当前的最新公网地址并保存 ✓
→ 出门时用新地址连 ✓；隧道再变，回家一次就自动更新 ✓ **不用再扫码** ✓

**落地要点**：
- 电脑端插件：新增一个"下发当前公网地址"的接口（网关侧已有隧道地址信息 ✓）；
- App 端：检测到能连局域网时，自动拉一次该地址并持久化 ✓；连不上时依次尝试「内网 → 已存公网地址 → 重新发现」✓；
- 对外部服务**零依赖** ✓、零账号 ✓、对所有用户成立 ✓（每人自己的电脑发自己的地址 ✓）。

**验收**：在家连一次 WiFi → 出门断 WiFi → 用移动数据能直接连上（不需要重新扫码）✓。

## 方案 3 · Tailscale（每人独立、地址永久固定）【备选 · 待用户决定】

- 每人各装各的 Tailscale（PC + 手机）✓ 免费 ✓ → 各拿到一个**永不变**的地址（`100.x.y.z`）✓
- App 直接连 `ws://100.x.y.z:3091` ✓ → **扫一次永久有效** ✓，跨网络（在家/在外）都通 ✓
- 代价：手机要装 Tailscale ✗，且**会占用 VPN 通道** ✗（与其它代理/VPN 冲突 ✗）

## 其它方案的适用边界（结论）

- **方案 1（Cloudflare 命名隧道 + 域名）**：对"所有人"**不成立** ✗（每人得有自己域名；若由作者提供子域则需长期维护分配服务 ✗）
- **方案 2（ngrok 免费固定域名）**：对所有人成立 ✓ 但每人需注册 ngrok + 一次性配置 ✗
- **方案 4（会合点自动发现）**：对"所有人"**不成立** ✗（需要一个公网可写的固定位置；每人各自建 Gist 又要存 token ✗）