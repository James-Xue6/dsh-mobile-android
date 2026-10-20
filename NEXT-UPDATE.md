# 下一步计划（NEXT-UPDATE）

> 状态标记：`[x]` 已完成并验证 / `[~]` 部分完成 / `[ ]` 待办。
> 最近更新：2026-10-20（**协议层 vendor 进本插件，插件数量 2 → 1**：dsh-mobile-access v2.0.0，内置 vendor/gateway，不再依赖第三方包、不再给 node_modules 打补丁）。

## 零、2026-10-20：合并成一个插件（dsh-mobile-access v2.0.0）

> 用户诉求：「现在好像是 2 个插件，能不能整合成一个？只用它这个公网的协议逻辑」。
> 结论：能。做法是把 `dsh-plugin-mobile-gateway@0.9.0`（MIT）**整包 vendor 进本插件**，
> 由本插件的 `apply()` 用 `ctx.plugin()` 作为子插件挂载 —— 对外一个插件、一条补丁行。

- [x] `vendor/gateway/`：网关 lib 全量（index.mjs / adapter / 隧道 / 设备 / 插件管理 / session-follower / wire-json / web-restart）+ LICENSE + PROTOCOL.md。
  三处 vendor 改动：① `../package.json` → `../../package.json`（版本号读取）；② `devices.js` → `devices.cjs`（本包 type=module，否则 CJS 被当 ESM 解析）；③ 头部注释。
- [x] 宿主：`index.js` 增加 `import gatewayPlugin, { Config } from './vendor/gateway/index.mjs'`，`Config` 直接复用网关 schema；`apply(ctx, config)` 末尾 `ctx.plugin(gatewayPlugin, config)`。冒烟测试确认 `ctx.plugin` 收到 `mobile-gateway` 与配置。
- [x] 依赖：`link:` 依赖不会安装自身依赖（实测 ERR_MODULE_NOT_FOUND），故把 `ws / qrcode(+pngjs,dijkstrajs) / @deepseek-ai/schemastery(+@standard-schema/spec,+cosmokit)` **随包 vendor 到 `node_modules/`**（约 1.5MB，含各自 package.json/LICENSE），保证 local-plugins 目录内可直接解析。
- [x] 客户端：两份 client bundle 机械合并成一份（A 段包 IIFE、B 段原位），删掉 `window.__DSH_MOBILE_ACCESS__` 跨 bundle 握手与轮询，改为同作用域直接渲染；`apply` 一次注册 3 个槽位（设置跳转行 + 侧边栏按钮 + 浮层面板）。合并脚本做过字节级保真与假 React 冒烟测试。
- [x] `cordis.patch.yml` 收敛成一行（本包），config 随行携带（lanPort 3091 等）。
- [x] `install.ps1`：不再登记第三方依赖、不再跑 6 个 node_modules 补丁脚本；新增迁移（摘依赖 + 摘 `mobile-gateway` 行）与 **[验收] 六项**（vendor / peer / adapter / 合并版 client / 无旧依赖 / 无旧行）。本机实跑六项全 OK。
- [x] 文档：README（安装流程去掉「跑第二遍」）、插件 README、`THIRD-PARTY-NOTICES.md`（MIT 声明 + 上游对齐清单）。
- ⏳ **未验证**：`ctx.plugin` 在真实 DSH 里的挂载结果（本机重启后验证 panel + 探针）；弹窗观感仍未在浏览器里看过。
- ⚠️ **已知取舍**：上游 0.10.x 不再自动跟随，需要人工按清单对齐 `vendor/gateway/`。

## 零、2026-10-20：修「别的电脑装完，界面和本机不一样」（已推 Gitee/GitHub，未发版）

> 用户实测：另一台电脑装完插件，界面与开发机不一致。查到**两个独立原因**，都已修：
>
> 1. **面板补丁的「已打过」判据被我改坏**。`restore-gateway-panel-fix.ps1` 原来靠 `must` 里列
>    `高级设置（一般用不到）` / `setAdvancedOpen` 判断「已经是补丁版」；而 v2 面板恰恰把这两样**删掉了**
>    ⇒ 老机器（旧 fork 面板）被判成「已打过，跳过」，永远停在旧界面；同时 `must` 里还留着
>    `styles.select`（v2 已无引用）⇒ 就算真覆盖也会因自检失败回滚。
> 2. **全新机器必然错过补丁**。`install.ps1` 只登记依赖，`dsh-plugin-mobile-gateway` 是 DSH **下次启动**
>    才由 pnpm 装上；等用户重启完，补丁步骤早已跑过 ⇒ 拿到上游原版面板（右侧整栏、没有「手机接入」卡片），
>    且新内核上缺 peer ⇒ 手机报 `reading 'signal'`。
- [x] `.patched` 头部加版本标记 `[pc-plugin patch] dsh-mobile-access panel v2`；幂等判据改成**只认这行标记**（改面板必须同步改版本号，脚本头部已写明）。
- [x] `must` / `mustNot` 按 v2 实际内容重写：`mustNot` 收录旧面板特征（`高级设置（一般用不到）`、`setAdvancedOpen`、`justifyContent: 'flex-end'`），老机器会被正确覆盖。
- [x] `restore-gateway-panel-fix.ps1` 增加 `-GatewayDir`（自测用），与 peer 补丁脚本保持一致。
- [x] `install.ps1`：网关未落盘时把后果说清（补丁会跳过、需重启后再跑一次）；结尾新增 **[验收]** 四行检查（面板 v2 / peer ×2 / cloudflared），不全 OK 就红字给出「重启 → 再跑 → 再刷新」三步。
- [x] README 新增 **①′ 装完重启后再跑一次 install.ps1**，并写明 `[验收]` 必须四行 OK。
- [x] 四种场景在 `D:\dsh-tmp\fakehome`（假 USERPROFILE）里全跑过：① 无网关（正确报缺）② 旧 fork 补丁版（正确升级到 v2 + peer）③ 重跑（全幂等跳过）④ 上游原版 0.9.0（正确覆盖并通过自检）。
- [x] 插件版本 `1.0.4 → 1.0.5`。
- ⏳ **未验证**：弹窗在真浏览器里的观感仍没看过；另一台电脑需用户重跑一次脚本才能确认。

## 零、2026-10-19：PC 插件适配 DSH 0.2.1-rc.2 + 面板收敛（已推 Gitee/GitHub，未发版）

> 触发：DSH 桌面端被自动升级到 **0.2.1-rc.2**，手机端出现「能连上、能鉴权、hello 正常，但 0 个对话 + 弹窗
> `Cannot read properties of undefined (reading 'signal')`」。

- [x] **真因**：新内核 `typertGateway.invoke()/stream()` 入口先 `assertPeerLive(request.peer)`；网关 0.9.0
  按旧协议调用（不带 `peer`）⇒ 读 `undefined.signal` 抛错。asar 内实测存在 `createPeer` / `assertPeerLive`。
- [x] **不走 0.10.3 的原因**：上游 0.10.3 确实修了这一点，但它是**上游原版**，本仓库叠的 5 套 fork 补丁
  （跨会话提醒 / hello 带公网地址 / 地址广播 / 文件上传 / 子智能体寻址）它都没有，升上去等于全丢。
  改为**最小回填**：`createDshHostAdapter(gateway, peer)` + invoke / 首帧 stream / session-follow /
  session-control / workspace-follow 全部透传 peer；`index.mjs` 里 `ctx.connection.createPeer({principal:{kind:'owner'}, interactions:'none'})`，卸载时 `peer.dispose()`。
- [x] **遗漏点（第一轮重启后暴露）**：`workspace.list()` 里那个 `firstStreamFrame(..., 'workspace', 'follow', {})`
  没传 peer ⇒ sessions 活了但 workspaces 仍报同一个错。补上后写了 mock 审计脚本逐个调用 adapter 出口，
  30+ 内核出口全部带 peer 才算通过。
- [x] **固化**：`patches/restore-gateway-connection-peer.ps1`（整文件替换两个 `.patched` 基线；版本闸门 0.9.0；
  自带备份/幂等/`-Revert`/`node --check` 复核），已挂进 `install.ps1` 的补丁清单最后一项。
- [x] **面板收敛（用户点名）**：①「移动设备」从右侧整栏改为**居中弹窗**（560px、圆角、遮罩、最高 `100vh-48px`）；
  ②删掉面板底部那张与「手机接入」卡片重复的**旧高级设置**（220 行，`advancedOpen` 已无引用）。
- [x] 顺手把只存在于 `.patched` 基线的 **Quick Tunnel 未就绪时地址框留空** 的修带进运行时。
- [x] 插件版本 `1.0.3 → 1.0.4`。
- ⏳ **未验证**：弹窗实际观感（宽度/圆角/遮罩）尚未在浏览器里看过，只做了语法与结构校验。
- ⚠️ **已知取舍**：删掉旧高级设置后，面板里没有「设备鉴权开关」和「命名 Tunnel 的域名/Token」输入框了；
  命名隧道改用 `POST /mgw/cloudflare`（`{mode:'named', hostname, token, enabled:true}`）。需要 GUI 入口时再挪进 设置 → 手机接入。
- [ ] **未做（用户决定先搁置）**：公网「一直连」方案——地址簿（免费 Workers 上报/查询）+ Quick Tunnel 自动跟随，见对话记录。

## 零、2026-10-17：合并朋友 fork `feat/session-tree`（7 个提交）

> 来源：`friend` = `gitee.com/tang-changxu/dsh-mobile-android` 分支 `feat/session-tree`（父提交正好是本仓库 `6590ef7` ⇒ 直接 fast-forward）。
> 处置：逐条审阅 → FF 合并进 `main` → Gradle `:app:assembleRelease` 通过 → 推 `gitee` + `origin`（**未发版**，`versionCode` 仍 27 / `0.87.0`）。

- [x] **抽屉父子层级修复（真 bug）** —— 宿主 `session.list` 出网的字段是 **`parentId`**
  （在 `app.asar` 里实测到 `...entry.parentSessionId !== void 0 ? { parentId: entry.parentSessionId } : {}`），
  而 App 一直读 `parentSessionId` ⇒ 恒空 ⇒ 抽屉里每条会话都被当顶层、平铺看不出父子。现两个名字都读。
- [x] **工作区层可折叠** —— 分组表头恒在（它同时是"有哪些工作区"的唯一来源，折叠只省掉名下会话行）；
  默认折叠，只有**当前会话所在的工作区**默认展开；用户显式展开/折叠各一套账本，落盘
  `Store.drawer_ws_expanded/collapsed`，与子会话折叠（`expandedParents`）互不影响。
- [x] **思维链一键开关** —— 对话页状态胶囊行加一枚玻璃胶囊「收起/展开思考过程」，
  **复用既有显示模式**（`Store.displayMode()` ⇄ `state.compact`），不新增第二套隐藏机制；
  同时修 `ThinkingCard/ToolCard` 的 `remember(startOpen)` 缺 key（不带 key 时全局切换对已在屏上的卡片无效）。
- [x] **默认改「简洁」** —— `Store.displayMode()` 新增 `display_mode_v3` 做**一次性迁移**（老装机也生效），此后以 v3 为准。
- [x] **构建自证** —— Gradle 9.6 `:app:assembleRelease` = BUILD SUCCESSFUL；`apksigner` 证书 SHA-256
  `2e518d75…d8ece8e` 与 HANDOFF §5 的期望指纹一致。
- ⏳ **未做**：真机逐项验收（当时 adb 无设备；模拟器启动即报
  `emu-last-feature-flags.protobuf.lock (error: 5)`，起不来）；**未发版**，`dist/version.json` 仍指向 0.87.0。

## 零（上一轮）：毛玻璃 v3 UI 同步（2026-10-06 · 已实测）

> 需求：用户给了一份界面 demo（存档 `F:\AI\程序开发\.tmp\demo_d.html`），要求"同步优化 App UI"，
> 并明确 **只做 UI 优化、不发版**。逐条口径、执行结果、未做项见 `docs/UI-毛玻璃-任务清单.md`。

- [x] **环境背景**：页面底从纯色 `Ui.BG` 换成 `Ui.AmbientDrawable`（三段渐变 + 四枚柔边光斑，
  几何与配色逐值抄 demo），6 套配色 + 深色 `brightness .42 saturate 1.25`。
- [x] **玻璃件**：`Ui.CardBg` 增加 135° 斜射高光 + 内发光 + 白棱发丝线 + 磨砂噪点；`cardGrad` 改
  `LayerDrawable(体+光)`；token 对齐 demo 的**真模糊档**（浅色卡 36% / 抽屉 52% / 顶栏 30% / 胶囊 60%）。
  ⚠️ 注意：这些 token 必须改 `applyTheme()` 的**浅色/深色分支**，改类字段默认值无效（分支会覆盖）。
- [x] **三层屏**：①对话（**浮动玻璃胶囊顶栏** + 玻璃气泡 + 玻璃胶囊 + 底部磨砂底板）
  ②我的设备（**饱和渐变卡 → 玻璃卡**，语义色徽标 + 纯文字 kv + 内嵌线路行 + 三按钮）
  ③会话抽屉（厚玻璃 + 白缘线 + 玻璃会话行）。
- [x] **真·背景模糊（自己实现）**：`Ui.BackdropBlurDrawable` —— 面板背后的内容按 1/4 缩略渲染 →
  可分离盒式模糊 → 当底图铺回（60ms 节流 + 缓存）。已用于顶栏胶囊（34dp）与底部磨砂底板（26dp）。
  **修正上一版结论**：原先写"原生做不到"是错的 —— 没有现成 API ≠ 做不到。
- [x] **实测修掉的问题**：① 输入区"重影"（渐隐末端 → 95%）② 深色胶囊过透
  ③ 顶栏原来是**通栏灰条**（一眼假）→ 改成浮动玻璃胶囊 ④ 顶栏下方误显示的白硬边（topFade 初始 GONE）
  ⑤ chip 缝隙露出正文 → 底部加磨砂底板。
- [x] **设置页「配色」6 套**（用户"剩余问题自行决定"授权）：`Store.ambientPalette` + 色块行；
  默认**极光**，另备**素雅**（最接近改版前观感）。
- [x] 证据：`evidence/ui-glass-20261006/`（浅/深 × 三屏 + 设置 + 配色选择器 + 真模糊对照 `G*/H*.png`）。
- ⚠️ **已知取舍**：底部磨砂底板的模糊源是消息列表，抓取节流 60ms —— 快速滑动时糊的内容会比
  文字晚一帧左右（肉眼可接受，且真机比模拟器软件渲染更跟手）。
- [ ] 待验收后再定：顶栏是否改成 demo 那种"浮动胶囊"、是否补磨砂噪点层。

## 一、已完成（保留记录，避免重复讨论）

- [x] **升级后每次启动还提示安装旧包（0.87.3 已修）** —— 用户装到 0.87.2 后，之前下载的 0.87.1 包
  **每次开 App 都弹「立即安装」**。根因：`maybePromptPendingUpdate()` 原来拿**文件名**跟当前版本比，
  升级成功后上一版的包永远不等于当前版本；去重变量 `pendingUpdatePrompted` 只在进程内有效，重启就忘。
  修法（`pickNewestPendingApk` + `apkInfo` / `versionCodeOf`）：按 **APK 里真实的 versionCode**
  （`PackageManager.getPackageArchiveInfo`，与文件名无关）分类 ——
  ① ≤ 当前版本（旧包/已装上的）⇒ **直接删**；② 比当前新 ⇒ **只留最新那个**，其余更新的包也删；
  ③ 读不出信息（半截包）⇒ 不删，避免误删正在下载的包。
  安装完成后进程重启，`onStart/onResume` 再跑一遍就把装过的包清掉（Android 没有可靠的"安装成功"回调）。

- [x] **更新链路三个真机 bug（0.87.2 已修 · ⏳ 待真机复验）**
  用户 2026-10-17 实测：`关于`里查到新版本**没有任何提示**；`立即更新`下载完点「去设置」授权
  「安装未知应用」，**直接跳进扫码取景页**，安装始终没发生。
  - ① **设备页弹窗按钮串线（根因）**：`GlassDeviceScreen` 的 `onPick` 原来**无条件**转给宿主
    `host.sheetPick(it)`，而宿主 `sheetPick` 只认本页自有菜单（tag=`add` 二选一 / `delete:<id>`），
    其余一律按「index 0 = 扫码添加」处理 ⇒ 更新链路在本页弹的**确认框**（去设置 / 立即安装 / 重试）
    按下去都变成「添加设备 → 扫码添加」⇒ 跳相机取景页，`去设置` 的回调根本没跑。
    修法：与抽屉/设置页/对话页同一条纪律 —— 先收起，再按 `state.sheet.onListPick` 分派，
    只有本页自有菜单才落给宿主。
  - ② **更新提示写死弹到对话页**：`handleUpdateResult()` 里 `((GlassChatView) convo).showSheetList(...)`
    ⇒ 人在设置页/设备页时对话页没显示，弹窗没人渲染 ⇒「查到了但毫无提示」。
    修法：改走 `glassInScreenList()`（`currentSheetState()` 按当前界面路由）。
  - ③ **授权后不续装**：`maybePromptPendingUpdate()` 有"同一版本只提示一次"去重，授权回来提示额度已用完
    ⇒ 什么都不发生。修法：新增 `pendingInstallApk` + `maybeContinuePendingInstall()`，回前台发现授权已给就续上安装器。
  - 发版核验：`node tools/verify-live.js` 主/备两条线路 sha256 一致 ✓（备用线路首次 `ECONNRESET` 是
    jsDelivr 抖动，重跑即通过）。

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

### P0 · 网关插件补丁的工程化（**2026-10-05 晚：主体已做完，只剩启动自检**）
- [x] **4 个补丁脚本现在都由 `install.ps1` 统一重放**（此前只跑面板补丁，其余是手工打的）：
  1. **`patch-gateway-crosssession.ps1`（2026-10-05 新增）** —— 跨会话提醒：放宽两道 waterfall 门 +
     跨会话下发带 `global: true`。**此前仓库里根本没有这个脚本**，改动只活在线上；
     回退用**反向替换**（不靠备份还原，否则会把后来那几个补丁一起抹掉）。
     已沙箱验证：`node --check` 通过 / 幂等 / 3 处 `hasAnyInteractionClient` / 旧门 0 残留 / `-Revert` 逐字节还原。
  2. `patch-gateway-hello-publicurl.ps1` —— hello 帧带上电脑当前公网(隧道)地址；
  3. `patch-gateway-route-broadcast.ps1` —— 地址变化 / 启动就绪后主动广播；
  4. **`patch-gateway-file-upload.ps1`** —— `message.files[]` + `hello.capabilities: file-uploads`。
  - `pc-plugin/dsh-mobile-access/package.json` 版本 `1.0.2` → `1.0.3`。
  - ⚠️ `patch-gateway-subagent-address.ps1` **已回滚**（线上无 `parentAddress` 标记），**不要**接进 install。
- [ ] **还剩：启动自检**。现在靠"记得重跑 install.ps1"；应加一处启动时核对（每个补丁的功能标记在不在），
  缺了就打印明确指引（"跑 pwsh -File pc-plugin\install.ps1"）。
  - 手动复核判据：`lib/index.mjs` 里应有 `hasAnyInteractionClient` / `'file-uploads'` / `dsh-mobile:hello-publicurl`
    / `dsh-mobile:route-broadcast`；`lib/dsh-host-adapter.mjs` 里应有 `uploadFile:`。
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
- [x] **底部「生成物」按钮**：列本次会话产出文件 + 直接打开/下载。
  - **2026-10-05 初版**：用 `file-list` 列**会话工作目录**（可下钻）。已发版 v0.86.9。
  - **2026-10-06 需求修正（用户点名）**：用户原话「生成物点开这个目录内容有问题，不要显示这么多
    就显示这次项目生成的这个输出文件即可，怎么还能点上一页啥的，这些不要了」。
    ⇒ 面板改为**只列本会话产出的文件**：数据源从 `file-list`（工作目录）换成
    `deliverables/presented` 事件里的 `{path, description}`（App 里早就有，见 `ChatItem.files`），
    并**删掉**目录下钻、「← 返回上级」、面包屑与目录行。
    - 好处：不用等网络（不再有「正在读取…」）、不会把 `.android` / `src` 这种堆目录倒给用户。
    - **下载仍要做 relativize**：presented 的 `path` 是**绝对路径**，要先相对化到会话工作目录
      （旧版 file-list 给的是相对路径、不用转，这一点两版相反，改动时容易踩）。
    - `ui/ArtifactSheet.java`（旧方案的备用面板）**已删除**：无调用点且依赖旧的目录型 `ArtifactEntry`。
    - `GatewayClient.requestFileList` / `onFileList` **保留但不被 App 调用**（协议能力 +
      harness 回归覆盖），注释里已标明"别误当生效路径"。
- [x] **加号 → 文件 / 相册 / 拍照，附件先暂存、与文字或语音一起发** —— 见「四」表 ②。
- [x] **通用文件附件（PDF / Office / 压缩包）** —— 见「四」表 ⑤；网关补丁 `patch-gateway-file-upload.ps1`。
- [x] **消息文本「点一次不能立即选范围」** —— 见「四」表 ③。
- [x] **chip 行「权限」（对齐 PC 端 permission presets）** —— 见「四」表 ④。
- [x] **「用量」面板（2026-10-06 用户点名）**：原来点「用量」只弹个 Toast，而且读的字段
  (`tokenUsage.totals.inputTokens` / `contextPressure.surfaceTokens`) **在真实回帧里不存在** ⇒ 恒显示 0。
  现在点开是完整面板（合计 / 缓存命中 / 未缓存输入 / 缓存读取 / 缓存写入 / 输出 / 上下文占用 /
  轮数·步数 / 解码速率 / 耗时 / 首 token）。
  - **公式与 PC 端同源**：`billedInput = uncachedInputTokens + cacheReadTokens + cacheWriteTokens`、
    `total = billedInput + outputTokens`、`缓存命中 = cacheReadTokens / billedInput`
    （抄自 DSH 客户端 `StatsPills` 的 `billedInputTokens` / `cacheHitPercent`）。
  - **真实回帧形状（实测 2026-10-06，很关键）**：`tokenUsage` 是**扁平**的
    （`{uncachedInputTokens, outputTokens, cacheReadTokens, cacheWriteTokens}`），
    **没有 `totals` 包装**、**没有 `inputTokens`**；`contextPressure` 只有
    `{contextWindow, pressureTokens, projectedTokens}`，**没有 `surfaceTokens`**。
    协议文档 PROTOCOL.md 里的示例是**旧的**。代码两种都兼容（见 `usageTotals()`）。
  - 数据源：`session-stats`（比 `context-usage` 多 `sessionStats`：轮数/步数/耗时/解码速率）。
  - 数值反验：`2,599,058 + 233,278,208 + 436,079 = 236,313,345`、命中 99%，与 PC 端逐位一致；
    本会话实测 267 tok/s，与 PC 端底部条一致。
- [ ] 助手消息里的 **markdown 表格**未渲染（当前按纯文本显示）。
- [ ] 大文件（>24MB）走 `uploadStream` 分块上传（**未实现**；当前单帧 base64 上限单文件 24MB / 合计 48MB，**未压测**）。

## 四、2026-10-05 四项优化 + 通用文件附件（本轮 · **已全部实测通过**）

> 契约：`docs/CONTRACT-20261005.md`；协议核实：`docs/PROTO-FINDINGS.md`；逐条交接单：`docs/任务明细-20261005.md`。
> **状态口径**：✅ = 已实现 + 构建通过 + **在 Android 模拟器上端到端实测通过**。

| # | 需求 | 状态 | 落点 |
|---|---|---|---|
| ① | 生成物窗口（列本会话产出文件 + 下载） | ✅ | `MainActivity.onArtifacts/onFileList/showArtifactsPanel/renderArtifacts` |
| ② | 加号 →「文件 / 相册 / 拍照」三选项，附件**先暂存**、与文字/语音一起发 | ✅ | `ui/AttachSheet.java`（已接线）、`MainActivity.onAttachPick/stageAttachment/sendStagedAttachments` |
| ③ | 消息文本「点一次不能立即选范围」不流畅 | ✅ | `ui/ChatAdapter.makeSelectable/selectWholeText`、`ConversationView.commitData` 选字让路 |
| ④ | chip 行新增「权限」（对齐 PC 端 permission presets） | ✅ | `ConversationView` 权限 chip、`MainActivity.onPickPermission/onPermissionOptions/onPermission` |
| ⑤ | **通用文件附件（PDF / Office / 压缩包）** | ✅ | `net/GatewayClient.sendMessageWithAttachments` + 网关补丁 `patch-gateway-file-upload.ps1` |

### 怎么复现验收（设备一到位，两条命令）
```
pwsh -File tools\verify-4features.ps1        # 四项：权限 / 加号 / 生成物 / 消息文本
pwsh -File harness\run-protocol-v2-test.ps1  # 协议层回归（10/10）
```
证据：`evidence/ui-4features-manual/`（22 个文件）、`evidence/t9-file-upload/`（39 个文件）。

### 集成期 + 设备验收期抓到的真 bug（共 7 个，全部已修）
| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | 编译不过 | `TextView` **没有** `setSelection(int,int)` | 改 `android.text.Selection.setSelection(Spannable,…)` |
| 2 | 权限 chip / 面板显示错的当前值 | 当前值在 **`sessionPermissions.currentValue`**，原实现漏了 | 兜底键表把 `currentValue` 排第一 |
| 3 | `AttachSheet` 交付后无人调用 | wire 先写完时该类还没落盘 | 按契约接线 |
| 4 | chip 文案 `权限 · 权限 完全访问` | `MainActivity` 与 `ConversationView` 都拼了前缀 | 只在 `ConversationView` 拼一次 |
| 5 | 单击只出高亮、不弹复制工具条 | `performLongClick()` 对 selectable TextView 不启动 ActionMode | 改**合成一次长按手势**（DOWN→900ms→UP） |
| 6 | **每一条消息都被网关拒** | `clientTimeZone` 非 IANA 名（`GMT` / `GMT+08:00`）→ 网关整条拒绝；App 已清空输入框 = **静默失败** | 新增 `safeClientTimeZone()`，不合法就**不带该字段**；4 个发送路径统一 |
| 7 | 发送被拒后附件被清掉 | "发完就清空"但成功与否是异步的 | 留一份 `lastSentAttachments`，`onProtocolError(message)` 里回填 |

### 本轮**实测**到的事实（直连真网关 + 模拟器，非推断）
- `permission-options`（带 sessionId）回帧：`options:[{value,name}]`、`defaultOptions`、`defaultPreset`，
  **当前值在 `sessionPermissions.currentValue`**；实测 `currentValue=danger-full-access` 而
  `defaultPreset=workspace-write` ⇒ 只读 `defaultPreset` 会**显示错误**的当前权限（已修）。
- `file-list` 回帧 `entries[].path` 是**相对 cwd** 的路径（`path:"."` 为根）⇒ 直接拿去开下载，**不要再 relativize**。
- 三条 preset 取值实测为 `read-only` / `workspace-write` / `danger-full-access`。
- **通用文件通道已打通**（端到端两次）：客户端 `message.files[]` → 网关调宿主 `fileUploads/upload`
  （**纯字符串 sessionId 当 agentId 可解析** —— 这是补丁原先唯一没验证的一环）→ 会话历史里出现
  `{"type":"file","attachment":{"attachmentId":"sha256:…","name":…,"bytes":…}}`。
- `hello.capabilities` 现在含 **`file-uploads`**（网关补丁宣告）；客户端按它决定"发 / 拦"，**不盲发**。

### ⑤ 的实现要点（避免以后重踩）
- 网关 `message` 帧**原生只接受图片**（png/jpeg/webp/gif）；通用文件要靠补丁新增的 `files[]`：
  每项 `{data:"<标准Base64无前缀>", name:"a.pdf"}`。
- 客户端上限：单文件 24MB / 带文件时合计 48MB / 纯图片仍 9MB（**未压测**）；再大要走 `uploadStream` 分块（未实现）。
- 未打补丁的网关会**静默忽略** `files[]`（只读 images/text）⇒ 所以客户端必须先查 `file-uploads` 能力，
  没有就明确拦下并提示，**不能盲发**。

### 本轮环境限制（如实记录 · 已查到真因）
- **真因 1（决定性）：QEMU 子进程找不到 DLL，以 `0xC0000135`（STATUS_DLL_NOT_FOUND）退出。**
  `emulator.exe` 只是启动器，真正跑的是 `qemu\windows-x86_64\qemu-system-x86_64-headless.exe`；
  启动器把 `emulator\lib64`（含 `lib64\vulkan`、`lib64\gles_swiftshader`）只加进**自己进程**的 DLL
  搜索路径，**子进程不继承**。QEMU 死在 `main()` 之前，stdout/stderr **全空**，
  外部只看到"启动器跑了、设备一直 offline" —— 极易误判成"沙箱不让起模拟器"。
  - 实测：直接跑 `qemu-system-x86_64-headless.exe` → 退出码 `-1073741515` = `0xC0000135`；
    **把 `emulator\lib64` 加进 PATH 后 QEMU 正常起来，adb 首次看到 `emulator-5554`**。
  - 已把这条 PATH 修复写进 `tools\启动模拟器并装机.ps1`（纯增量）。
- **真因 2（已解决）：AVD 必须放在可写目录。** 表现为 QEMU 满核空转但永不注册 adb。
  日志刷的是 `Unexpected error while creating: …\Pixel_7_API35.avd\snapshot.lock.lock (error: 5)`
  —— `error 5` = **ACCESS_DENIED**：那个 AVD 在 `C:\Users\...\.android\avd` 下，沙箱写不进去，
  模拟器就无限重试。项目脚本注释里那句"避开 `C:\Users\...\.android` 的 error 5"说的就是它。
  - **修法**：用 `avdmanager` 在**工作区可写目录**新建 AVD（`dsh35`，API35 google_apis x86_64），
    并把 `ANDROID_AVD_HOME` 指过去 ⇒ 起来并跑通（`emulator-5554 device`）。
  - 另：**模拟器里 `127.0.0.1` 是它自己**，配对载荷要带
    `endpoints:["ws://10.0.2.2:19387/ws/mobile"]` 才能连上宿主网关。
- **一键取证脚本已跑通**：`tools\verify-4features.ps1`（装包 → 进对话页 → 逐项点
  「权限 / 加号 / 生成物 / 消息文本」，把 uiautomator XML 存到 `evidence\ui-4features-<时间戳>\`）。
  - **坑（写脚本时踩过）**：uiautomator 的坐标会**异步漂移**（chip 文案随权限/模型回帧变长、整行右移），
    必须**每次点击前即时重 dump**，否则会点到隔壁控件（实测把「权限」点到「模型」上）。
  - 另一个：**浮动 ActionMode 工具条在独立窗口层，uiautomator 看不到**，只能用截图确认。
- 其它可用的替代取证：`tools\accept-pairing.ps1`（项目自带）+ `harness/run-protocol-v2-test.ps1`（协议层一键回归，已 10/10 绿）。


### P2 · 性能与数据一致性
- [ ] 网关日志 `query ok: models` 每秒数十次 → 排查客户端是否在轮询模型列表。
- [ ] 任务项渲染仍缺真实数据：宿主对移动端的 `tasks` 查询返回 `todos:null`（网关 `projection forwarded: key=todos` 在推事件）。

## 三、交付与运维提醒

- **发版流程**：`pwsh -File .\release.ps1 -Version X.Y[.Z] -Notes "…"`（自动 +1 versionCode、写 `dist/version.json`、打 tag、刷 CDN）。
- **网关升级后**：先检查 `lib\index.mjs` 是否还含 `hasAnyInteractionClient`；没有就重放补丁（P0 项落地后应自动）。
- **未验证不得写成已修复**；环境造不出条件时如实标注"未验证"。
- **改完 `net/**` 先跑一次协议层回归**（2026-10-05 新增，一键）：
  ```
  pwsh -File harness\run-protocol-v2-test.ps1
  ```
  它用**App 真实 net 源码**在 JVM 上直连真网关，实跑 `requestFileList` / `requestPermissionOptions` /
  `sendMessageWithImages`（发到不存在的会话，只验证帧被 schema 接受，不建会话不触发模型），
  跑完自动吊销本次的临时设备。10 条断言全绿 = exit 0。证据样例见
  `evidence/protocol-v2-20261005/`。
  - 顺带修了一个既有问题：`harness/build.ps1` 的垫片没跟上 `GatewayClient` 后来引入的
    `android.content.Context` / `android.util.Log` / `Notifier.onGatewayState`，**本脚本原先编不过当前 src**；
    已把这 3 个垫片从 `.verify-p0\shim` 复制进 `harness\shim\` 并加进源码表。

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