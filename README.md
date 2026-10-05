# DSH 掌上通（DSH Mobile for Android）

用手机遥控电脑上的 **DeepSeek Harness**。原生 Android 客户端，界面按豆包 / Trae 这类对话产品做：
会话列表 → 气泡对话 → 实时流式输出 → 内联审批 / 提问卡片。

[English](README.en.md) | **简体中文**

---

## 装它（约 3 分钟）

### ① 电脑：装 PC 插件

在电脑上执行（需要 PowerShell 7 / `pwsh`）：

```powershell
git clone https://github.com/James-Xue6/dsh-mobile-android.git
cd dsh-mobile-android
pwsh -File .\pc-plugin\install.ps1
```

脚本会：把插件复制到 `~/.dsh/local-plugins/`、把 APK 放进插件目录、在 DSH profile 里登记依赖与 bundle。
**只装这一个插件就够了** —— 协议层 `dsh-plugin-mobile-gateway` 已声明为本插件的依赖，
由脚本一并登记；它的行也由本插件自己的组合声明挂载，不用你另外装第二个插件。

> 用 SSH 的话，第一条命令换成 `git clone git@github.com:James-Xue6/dsh-mobile-android.git`。

### ② 重启一次 DSH 桌面版

然后点左侧边栏底部的 **「移动设备」** 按钮 —— PC 端只有这一个入口。

### ③ 手机：装 App

抽屉里第一屏就是「手机接入」卡片，点 **「下载 App」**：
让**手机连同一个 WiFi** 扫弹出的二维码，直接下载安装（安装包由你这台电脑发出，局域网 8099 端口，不经网盘或 CDN）。

不方便扫码也可以直接从公开地址下载（**这两个链接永远指向最新版**）：

- CDN（国内通常更快）：`https://cdn.jsdelivr.net/gh/James-Xue6/dsh-mobile-android@main/dist/dsh-mobile.apk`
- GitHub：`https://github.com/James-Xue6/dsh-mobile-android/raw/main/dist/dsh-mobile.apk`

> 想要固定版本就把 `@main` 换成 tag，例如 `@v0.86.9`。
> 当前最新版号可以读 `https://cdn.jsdelivr.net/gh/James-Xue6/dsh-mobile-android@main/dist/version.json`（`versionCode` 越大越新）。
> 每次发版脚本都会刷新 `@main` 的 CDN 缓存，所以上面两条**不会滞后**。

### ④ 扫码配对

在同一张卡片上点 **「生成内网二维码」**（在家用）或 **「生成公网二维码」**（出门用），用 App 扫它。
**内网地址与公网地址会一次填好**，之后在家用内网、出门自动切公网，都不用手输。

> 电脑端的协议层是第三方 MIT 插件 `dsh-plugin-mobile-gateway`，它**已声明为本插件的依赖**：
> 安装脚本会一并登记，挂载也由本插件的组合声明负责 —— 不需要你单独装第二个插件。

### ⑤ 更新（已经装过的怎么升）

**手机 App** —— 打开就会提示新版本（启动时查一次，**6 小时内只查一次**）；
没弹就去 **设置 → 关于 → 检查更新** 手动点一次。装完**不用重新扫码、不用重新配对**。

**电脑端插件** —— 拉一下仓库再重跑安装脚本，然后重启一次 DSH：

```powershell
cd dsh-mobile-android
git pull
pwsh -File .\pc-plugin\install.ps1
```

> 为什么必须重跑：这些补丁改的是第三方网关包的 `lib/*.mjs`（在 `node_modules` 里），
> **网关升级/重装会把它们覆盖掉**。脚本幂等，重复跑没有副作用 ——
> 它会自动重放全部 4 个网关补丁：跨会话提醒 / hello 带公网地址 / 地址变化广播 / 通用文件附件。

---

## 怎么用

1. **选会话** —— 打开 App 就是会话列表（按工作区分组），点一个进去，或新建。
   - 右下角 **＋** = 在**默认/当前**工作区新建任务；
   - 每个工作区分组标题右侧的 **⋯** = 弹出该工作区菜单，选「在此工作区新建任务」，
     新会话就会建在**这个**工作区里（与电脑版一致）。
2. **发消息** —— 直接输入发送。助手回复流式逐字出现，工具调用折叠成一行工具条。
3. **回答它** —— 需要你决定时会出现卡片：
   - **审批卡**：`批准一次` / `拒绝`；
   - **提问卡**：选项（可多选）+ 自定义输入；
   - **交付物卡**：点一下把文件下载到手机，长按复制路径。
4. 会话顶部显示当前**目标**与**任务清单**；右下角 `↓` 一键回到最新消息。

---

## 出问题了

**手机连不上电脑**
- 确认手机与电脑在同一个 WiFi；电脑端「移动设备」抽屉里，下面「高级设置」中的网关是开启状态。
- App 右上角 ⚙ →「当前状态」：看 `state / gen / ws` 与最近 40 条收发帧，能直接看出卡在哪一步。
- 提示「公网地址可能已失效」：公网隧道是临时域名，**电脑每次重启都会变**，重新扫一次二维码即可。

**扫码扫不出 / 点扫码相机一闪而过**
- 给 App 相机权限（系统设置 → 应用 → DSH 掌上通 → 权限）。
- 也可以点「复制配对串」，在 App 里手动粘贴。

**重启 DSH 后找不到「移动设备」按钮**
- 插件装完必须**重启一次 DSH 桌面版**才生效。
- 还不行就确认协议层依赖 `dsh-plugin-mobile-gateway` 已随本插件装好（profile 的 `package.json` 依赖里应有它）。

**覆盖安装报「应用未安装」**
- 说明签名和旧版不一致。只能用原来的签名密钥库（`%USERPROFILE%\.dsh-mobile-keys\dshmobile.jks`）重新打包；
  该文件丢了就只能先卸载再重装。**请离线备份这个 .jks。**

**想固定公网地址（不想每次重启都重新扫码）**
- 手机与电脑加入同一个 Tailscale 网络，App 里填 `ws://100.x.x.x:3091/ws/mobile`
  （App 已把 `100.64/10` 与 `*.ts.net` 视为内网，不受明文限制、也不暴露公网）。
- 或者把域名的 NS 迁到 Cloudflare，建命名隧道后在面板里配置 `mode=named`。

---

## 开发者

| 想做什么 | 命令 |
|---|---|
| 构建 APK | `pwsh -File .\build.ps1` |
| 跑网络层端到端测试 | `pwsh -File .\harness\build.ps1`，再 `node tools\run-e2e.mjs` |
| 逐档判定分析 | `node tools\e2e-assert.mjs` |
| 模拟器 + 装机（验 UI） | `tools\启动模拟器并装机.bat` |
| 发版 | `pwsh -File .\release.ps1 -Version 0.5 -Notes "改了什么"` |

- **构建依赖**：JDK 21 + Android SDK（`build-tools;36.0.0` 与 `platforms;android-36`）。
  源码路径**不能含中文**（原生工具在 936 代码页打不开非 ASCII 路径），脚本会自动暂存到 `C:\dshstage` 再构建。
- **签名**：固定密钥库 `%USERPROFILE%\.dsh-mobile-keys\dshmobile.jks`；
  口令走环境变量 `DSH_KS_PASS` 或仓库根目录的 `keystore.local.ps1`（已 gitignore，**绝不入库**）。
- **更新提醒**：App 启动时从 `@main` 拉 `dist/version.json` 比对版本；发版脚本会一并刷新它。
- 测试脚手架的用法与踩坑见 [tools/使用说明.md](tools/使用说明.md)；
  PC 插件细节见 [pc-plugin/dsh-mobile-access/README.md](pc-plugin/dsh-mobile-access/README.md)。

### 目录

```
dsh-mobile-android/
├── AndroidManifest.xml
├── build.ps1 / release.ps1      # 免 Gradle 构建 / 一键发版
├── dist/                        # 分发的 APK、校验值与更新清单
├── libs/core-3.5.3.jar          # zxing 扫码，纯 Java 无 native
├── res/                         # 主题 / 图标 / 网络策略
├── src/com/dsh/mobile/          # App：MainActivity + model/ + net/ + ui/
├── harness/                     # JVM 联调：复用 App 真实 net 层源码
├── pc-plugin/                   # PC 端「手机接入」插件 + 安装脚本
└── tools/                       # 假网关、e2e 脚本、模拟器装机
```

### 架构

App 只实现 `dsh-mobile-v1` 协议（WebSocket + JSON），不碰 DSH 内部；
协议层由第三方 MIT 插件 `dsh-plugin-mobile-gateway` 承担，本仓库的 `pc-plugin/` 只做接入编排与界面。
安装面只有 `dsh-mobile-access` 一个包：它声明依赖网关，并在自己的 `cordis.patch.yml` 里挂载网关的行。

```
Android App ──ws/wss──> dsh-plugin-mobile-gateway ──> DSH Host (desktop profile)
```

---

## 下一步计划

见 [NEXT-UPDATE.md](NEXT-UPDATE.md)：网关补丁工程化（P0）、大文件 `uploadStream` 分块、markdown 表格渲染、交付物落点规则、跨会话提醒的最终方案等。

## 更新日志

### v0.86.9（开发中，未发布）

**四项优化（用户点名）**
- **生成物窗口**：底部 chip 行新增「生成物」——列本会话工作目录（`file-list`），可下钻目录 / 返回上级，
  点文件下载到手机（进度原地更新），完成后尝试 `ACTION_VIEW` 打开
- **加号 → 文件 / 相册 / 拍照**：选中的东西**先暂存在输入框上方**（缩略图 / 文件名 / ×），
  与文字或语音**一起发送**，不再"选完立即发"
- **修「消息文本点一次不能立即选范围」**：单击即整段选中 + 选区手柄 + 系统「复制/分享」工具条
  （`setSelection` 后合成一次长按手势触发 ActionMode）；选字期间列表刷新让路（**封顶 4s**，不冻结流式输出）
- **权限 chip**：chip 行新增「权限」，对齐电脑端 permission presets
  （`read-only` / `workspace-write` / `danger-full-access`），当前值高亮、可切换

**新增：通用文件附件（PDF / Office / 压缩包）**
- 电脑端网关补丁 `pc-plugin/patches/patch-gateway-file-upload.ps1`：`message` 帧新增 `files[]`，
  经宿主 `fileUploads` 换 receiptId，拼进 prompt content 的 `{type:'file',receiptId}`
- 网关会在 `hello.capabilities` 宣告 **`file-uploads`**；客户端按能力门决定"发 / 明确提示暂不支持"
  （**不盲发** —— 未打补丁的网关会静默忽略 `files[]`，文件会悄悄丢掉）

**修掉的真 bug（都是端到端实测才暴露的）**
- **`clientTimeZone` 非 IANA 名会让整条消息被网关拒绝**（`must be UTC or a valid IANA Area/Location name`）：
  部分 ROM / 模拟器给的是 `GMT`、`GMT+08:00`，而 App 已清空输入框 → **用户以为发出去了，其实一条都没发**。
  现在只在匹配 `Area/Location` 时才带该字段；4 个发送路径统一
- **发送被网关拒绝后附件被清掉** → 改为失败回填（附件放回待发送）
- `permission-options` 的当前值字段是 **`sessionPermissions.currentValue`**（原实现漏了 → 会显示错误的当前权限）
- `TextView` **没有** `setSelection(int,int)`（改用 `android.text.Selection`）
- 权限 chip 文案前缀重复（`权限 · 权限 完全访问`）
- `harness/build.ps1` 原先**编不过当前 `src/`**（垫片缺 `Context` / `Log` / `Notifier`）

**验证**
- 四项 + 文件附件全部在 Android 模拟器上**实测通过**（证据 `evidence/ui-4features-manual/`、`evidence/t9-file-upload/`）
- 协议层一键回归：`pwsh -File harness/run-protocol-v2-test.ps1`（**10/10**）
- 设备一到位就能一键验收：`pwsh -File tools/verify-4features.ps1`

**PC 端（`pc-plugin`）**
- **`install.ps1` 现在会重放全部 4 个网关协议补丁**（跨会话提醒 / hello 带公网地址 / 地址变化广播 /
  通用文件附件）。以前它**只跑面板补丁**，其余几个都是手工打的 ⇒ **只活在 `node_modules`**，
  网关升级/重装就静默失效（手机表现为：收不到跨会话提醒、出门连不上、发不了文件）
- 新增 `pc-plugin/patches/patch-gateway-crosssession.ps1` —— 此前**仓库里根本没有这个脚本**，
  改动只在线上。内容：放宽两道 waterfall 门 + 跨会话下发带 `global: true`；
  回退用**反向替换**（不靠备份还原，避免把后来那几个补丁一起抹掉）
- 插件版本 `1.0.2` → `1.0.3`

**已知未覆盖**
- 大文件未压测（单文件上限 24MB / 合计 48MB）；更大的要走 `uploadStream` 分块（未实现）
- 网关补丁改的是 `node_modules`，**网关升级/重装后需重跑 `install.ps1`**（启动自检尚未做，见 `NEXT-UPDATE.md` P0）

### v0.86.4

**通知（本轮主线）**
- 修 **跨会话/后台收不到通知**：网关插件新增 `hasAnyInteractionClient()`，放宽提问/审批的两道 waterfall 门
  （原门按会话作用域过滤：手机订阅会话 A 时，会话 B 的提问**不建档、不生成帧、连日志都不打**）
- 修 **前台服务断线即自杀**：`KeepAliveService.sync()` 去掉 `Notifier.isConnected()` 判据，断线期间保持常驻
- 修 **无界面时帧被静默丢弃**：`GatewayClient` 新增进程级 `bgInteractionHook`，`listener==null` 时仍弹提醒
- 通知渠道 `dsh_pending` → **`dsh_pending_v2`(HIGH)**；新增独立 `dsh_service` 渠道保护后台保活
- 通知失败可诊断：`lastPostError`/`lastPostAt` + 设置页三行自查 + **渠道级深链**
- 通知 id 槽位 200 → 4000；`approvalKey/questionKey` 收成唯一实现（去重与深链一致）

**卡片与输入**
- 修 **卡片灰框**：`Ui.CardBg` 自绘渐变在长卡片上整片压暗约 26%（设计色只在最外侧露 ~8px）
  → 提问/审批卡改用框架 `Ui.cardGrad()`（对角渐变 + 圆角 + 描边）
- 修 **卡片底部按钮不可达**：按钮移出滚动区，常驻卡片底部
- 修 **卡片内部滚动位置每次刷新归零**：`ChatItem.cardScrollY` 跨重绑保留
- 修 **提问卡内输入框草稿丢失**：TextWatcher 边打边写回 `it.typed`
- 修 **点输入框后卡片乱滚/输入框看不见**：内容区高度改固定值（不再跟随键盘），键盘弹起时**只最小幅度滚外层列表**
- 新增 `MaxHeightEditText` / `MaxHeightScrollView` / `MaxHeightLinearLayout`
- 底部渐隐改为跟随悬浮层实测高度（原固定 132dp）；顶部**无卡时不显示**

**已知未验证 / 环境限制**
- V2「界面被系统销毁后仍弹通知」、V3 手机侧「断网重连补发」：本机荣耀不销毁 Activity，造不出验证条件
  （V3 网关侧已验证：`interaction replay … questions=1`）
- V1 渠道：荣耀会把 App 申请的 HIGH 降为 DEFAULT，**需用户手动把「需要处理」设为横幅**

### v0.85（开发中，未发布）
**新增**
- 顶部「待处理交互」提示栏：别的会话有待回答的提问/审批时露出，点它切过去

**修复**
- **提问/审批卡「切过去闪一下就消失」**：切会话重订阅时 `session-snapshot` 整体重建列表
  （`items.clear()`），而提问/审批卡是本地实时建的、不在服务端历史里 → 被冲掉。
  现改为重建时**保留未处理的交互卡**（真机验证：切过去卡片稳定显示且可作答 ✓）
- 跨会话提醒的第二条连接改为**后台线程建连 + 周期重连**，并把状态做进 App 内诊断

**已知未解决**
- 第二条连接在**公网隧道**下握手无法完成（`[control 通道]` 停在"已发起连接（等握手）"，
  网关侧无第二条 `client connected`）→ 跨会话提醒在公网下仍不可用；需在**内网直连**下复验

### v0.85
**修复**
- 目标卡「点标题行没反应 / 永远只占一节」：展开状态被计划刷新重置（planExpanded 优先）
- 目标卡「灰底 + 一圈黑框」：改纯色圆角底 + 去阴影、移除会覆盖底色的按压换底
- 顶部渐隐：**没有目标卡时不显示**（原先会把首行文字挡住），高度 132dp -> 64dp
- 历史翻页：重连导致的 `session-snapshot` 不再重置分页（保留 hasMore / nextBeforeSeq）
- 带图片消息的文字被挤成竖排单字一列（用户气泡外层应为竖排）
- 排队修改内容「点了保存没反应」（网关强制要求 action=edit，协议文档漏写）
- 助手消息图片不显示（卡片补渲染 + 助手回合补收集附件）

**新增**
- 目标卡改为**顶部浮动层**：与输入框同侧边距、点开就地向下展开、顶部渐隐与底部对称
- 任务项状态渲染（完成=打钩+删除线置灰 / 进行中=旋转指示 / 待办=空心圈）
- 跨会话提醒：周期重连以获取"连接建立时的全量不限会话重放"，走**系统通知栏**
- 设置页「当前状态」新增 `[control 通道]` `[全局视图]` `[最近一次队列操作]` `[事件流]` 诊断

**已知未完成**
- 任务项渲染缺真实数据验证（宿主对本会话的 tasks 查询返回 todos:null）
- 助手消息里的 markdown 表格未渲染（按纯文本显示）
- 第二条 WS 连接握手建不起来（14s 超时）——跨会话即时推送改走重连方案

### v0.84
**修复**
- **提问/审批卡「切过去闪一下就消失」**（用户实测报出）：切会话会重订阅 → 网关重放提问（卡片出现）
  → 紧接着 `session-snapshot` 整体重建列表（`items.clear()`），而提问/审批卡是**本地实时建的、
  不在服务端历史里** → 被这次替换冲掉。现改为重建时**保留未处理的交互卡**。
  真机验证：切过去后卡片稳定显示且可作答 ✓
- 跨会话提醒：第二条连接改为**不带 `X-DSH-Channel` 且永不 subscribe**（协议：不订阅 = 接收所有会话），
  绕开该自定义头被 Cloudflare 隧道剥掉的问题；顶部新增**待处理提示栏**，点它切到对应会话

### v0.83
**修复（重要）**
- **修改排队内容「点了保存没反应」**：根因是网关**强制要求 `action` 字段**
  （`queue-update` 必须是 `itemId` + `action ∈ edit/remove/steer`），而协议文档里「改文本」的
  例子漏写了该字段 —— 照文档实现会被静默拒绝。现在改文本显式发 `action:"edit"`。
  真机验证：`待发送 · EDIT-A` → 点修改 → 改 `EDIT-B` → 保存 → 条上变为 `EDIT-B`，
  网关侧出现 `query ok: queue-update` ✓
- **带图片的消息不再显示成一大串文字**：助手/子智能体卡片原先完全不渲染图片，且助手回合
  从不收集附件 id —— 两边都已补上
- 编辑弹窗被键盘挡住「保存 / 取消」
- 排队内容显示成 JSON 原文（`content` 是内容块数组，需按块解析）

**新增**
- 设置页「当前状态」新增 `[control 通道]`、`[最近一次队列操作]` 诊断

### v0.82
**修复**
- **带图片的消息不再显示成一大串文字**：助手/子智能体卡片原先完全不渲染图片（只有用户气泡会画），
  且助手回合从不收集附件 id —— 现已两边都补上
- 修改排队内容「点了保存没反应」：补上 30 秒入站帧门禁（半开连接时明确提示并自动重连，不再静默丢帧）
- 编辑弹窗被键盘挡住「保存 / 取消」按钮
- 排队内容显示成 JSON 原文（content 是内容块数组，需按块解析）

**新增**
- 设置页「当前状态」新增 `[control 通道]` 与 `[最近一次队列操作]` 诊断

### v0.81
**新增**
- 底部可横滑 chip 行：**项目 / 模型 / 思考 / 用量 / 任务**
  - 模型：列出全部模型及思考档位，选中即切换（对齐豆包）
  - 思考：Off / Low / High / Max 可选
  - 用量：上下文占用百分比，点开看 token 明细
  - 任务：展开该会话的任务列表（只读）
  - 项目：选工作区并直接在新工作区开对话
- 回合运行中也能发送：排队，输入框上方显示「待发送」条；每条可**立即插入 / 修改 / 删除**
- 设置页「当前状态」新增事件流诊断（便于自查连接与消息接收）

**修复**
- 修改排队内容时显示一堆 JSON 字符（content 是内容块数组，被当成原文显示）
- 修改排队「点了保存没反应」：半开连接静默丢帧 → 现在明确提示并自动重连
- 编辑弹窗被键盘挡住「保存 / 取消」按钮
- 模型名显示不更新（进会话与回合结束都会重新拉取）
- 「N 子智能体」入口遮挡「回到底部」按钮
- 底部弹窗滑动后底色丢失（看起来像"一滑动就变色"）

## 许可

见 [LICENSE](LICENSE)。一句话：**公开源码供学习与群内使用，但不是 OSI 自由软件**——
商用、对外二次分发、改名换皮发布需要事先取得作者书面许可。
提交 Issue / Pull Request 参与改进是欢迎的。

## 一起维护

想一起改这个项目？看 **[CONTRIBUTING.md](CONTRIBUTING.md)**——面向不熟 git 的人写的，
全是"点哪里"，三条路任选（加协作者 / Fork + Pull Request / 直接发给我）。
