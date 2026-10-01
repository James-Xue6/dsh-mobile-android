# DSH 掌上通（DSH Mobile for Android）

用手机遥控电脑上的 **DeepSeek Harness**。原生 Android 客户端，界面按豆包 / Trae 这类对话产品的形态做：
会话列表 → 气泡对话 → 实时流式输出 → 内联审批 / 提问卡片；内网扫码配对，外网填你反代的地址。

<p align="center">
  <img src="dist/evidence-conversation-v2.png" width="250" alt="会话（完整模式）">
  <img src="dist/evidence-tasks-deliverables.png" width="250" alt="任务提要与交付物卡片">
  <img src="dist/evidence-question-image.png" width="250" alt="提问卡片与图片">
  <img src="dist/evidence-settings-collapsed.png" width="250" alt="折叠式设置">
</p>

<p align="center">
  <b>会话列表 · 任务与交付物 · 提问卡片 · 折叠式设置</b>（全部为荣耀 PGT-AN10 真机截图）
</p>

> **当前进度**：Android 13 项功能已真机验证；PC 接入插件（Cordis/DSH）宿主与面板均已实测；
> 公网访问走网关内置的 Cloudflare 快速隧道（无需开端口/自建反代）。详见文末各节。

---

## 一、为什么要自建客户端

| 已有方案 | 形态 | 为什么不直接用 |
|---|---|---|
| `dsh-pocket` | 反向代理 + 二维码，**把桌面版 Web UI 原样搬到手机** | 桌面布局缩到手机上显示不全、操作别扭 |
| `dsh-im-connect` | IM 机器人桥（微信 / 飞书 / QQ…） | 富交互弱，看不到工具轨迹与审批卡 |
| `Clarklevis1995/dsh-mobile` | **iOS SwiftUI 原生客户端** | 只有 iOS；Android 客户端是生态空白 |

结论：**宿主侧网关不必重写，客户端要重写。**

## 二、架构

```
┌────────────────────┐        dsh-mobile-v1 (WebSocket + JSON)
│  Android App       │  ────────────────────────────────────────┐
│  com.dsh.mobile    │                                          │
│  · 会话列表/气泡    │        ws://<电脑IP>:3091/ws/mobile       │
│  · 流式输出        │        wss://<你的域名>/ws/mobile         │
│  · 审批/提问卡片    │                                          ▼
└────────────────────┘                          ┌──────────────────────────────┐
                                                │ dsh-plugin-mobile-gateway    │
                                                │ v0.9.0（MIT，第三方，复用）   │
                                                │  · 设备配对 / token 鉴权      │
                                                │  · 会话与实时事件桥           │
                                                │  · Human-in-the-loop 投影     │
                                                └──────────────┬───────────────┘
                                                               │ Host Adapter
                                                               ▼
                                                ┌──────────────────────────────┐
                                                │ DSH Host 0.2.0-rc.2          │
                                                │ (desktop profile / Electron) │
                                                └──────────────────────────────┘
```

- 网关插件基线正好是 **DSH 0.2.0-rc.2 / Session format 4**，与本机版本一致。
- App 只实现协议，不碰 DSH 内部：**协议层零逆向风险**。
- 内网走局域网端点；外网由你自己反代成 `wss://域名/ws/mobile`，App 里手填即可。

## 三、目录结构

```
dsh-mobile-android/
├── AndroidManifest.xml
├── build.ps1                      # 免 Gradle 的 APK 构建脚本
├── dist/dsh-mobile.apk            # 产物
├── libs/core-3.5.3.jar            # zxing（扫码），纯 Java 无 native
├── res/                           # 最小资源集（主题/图标/明文网络策略）
├── src/com/dsh/mobile/
│   ├── MainActivity.java          # 单 Activity 三屏路由 + 事件归并
│   ├── Store.java                 # 本地持久化（地址/token/设备 ID）
│   ├── model/{SessionInfo,ChatItem}.java
│   ├── net/WsClient.java          # 自研 RFC6455 WebSocket（无三方依赖）
│   ├── net/GatewayClient.java     # dsh-mobile-v1 协议客户端
│   └── ui/{Ui,ChatAdapter,ConversationView,SessionListView,SettingsView,QrScanActivity}.java
└── harness/                       # JVM 联调 harness（复用上面的 net 层）
```

## 四、构建

```powershell
pwsh -File .\build.ps1
```

依赖：JDK 21、Android SDK（`build-tools;36.0.0` + `platforms;android-36`）。

**两个已知坑，脚本已内置处理：**

1. **路径不能含中文** —— `aapt2`/`d8`/`zipalign` 是原生工具，在 ANSI 代码页 936 的 Windows 上打不开非 ASCII 路径。脚本先把源码暂存到 `C:\dshbuild` 再构建，最后把产物拷回 `dist\`。
2. **lambdas 需要 `core-lambda-stubs.jar`** —— `android.jar` 不含 `LambdaMetafactory`，必须把 build-tools 里的 `core-lambda-stubs.jar` 一起放进 `-bootclasspath`。

签名用固定密钥库 `%USERPROFILE%\.dsh-mobile-keys\dshmobile.jks`（口令 `ROTATED-PASSWORD`）。
**换签名会导致无法覆盖安装**，请离线备份该文件。

## 五、安装与配对

1. 手机安装 `dist/dsh-mobile.apk`（允许「未知来源」即可，无需 adb）。
2. 电脑端 DSH 重启后，打开 **「移动设备」** 面板 → 运行模式选 **常驻开启** → 填设备名 → **生成配对二维码**。
3. 手机 App → 右上角 ⚙ → **扫码配对**，扫那个二维码。
4. 外网：把你的反代指向电脑上的 `3091` 端口并开启 TLS，然后在 App 里手填 `wss://你的域名/ws/mobile` 保存。

> 局域网明文 `ws://` 只允许私有网段 / 回环 / `.local`（`GatewayClient.cleartextProblem()` 强制校验）；公网必须 `wss://`。

## 六、已实现的协议能力

| 能力 | 状态 |
|---|---|
| 配对（Base64URL 载荷 → 一次性配对码 → 长期设备 token） | ✅ |
| token 鉴权连接（子协议 + `Authorization` 双通道） | ✅ |
| 心跳 / 断线指数退避重连 / close 4003·4004 语义 | ✅ |
| 会话列表、自动建会话、重命名、归档 | ✅ |
| 订阅 + `session-snapshot` 原子基线 | ✅ |
| 持久 `event` 流（turn/step、user/assistant message、tool call/result、session/title） | ✅ |
| 独立 `assistant-stream` 增量（`start` / `chunk` / `end(committed|abandoned)`） | ✅ |
| 审批：`approval-requested` → `approval-response` → `approval-resolved` | ✅ |
| 提问：`question-requested` / `question-answer` / `question-cancel`（含多选与自定义输入） | ✅ |
| 停止当前回合（`session-cancel`） | ✅ |
| 历史分页（`beforeSeq` + `historyFormatVersion`） | ✅ |

**v1 未做**（明确留白）：图片/文件上传、文件下载、命令与技能菜单、任务与目标（todos/goal）面板、模型与权限切换、
多网关管理、Cloudflare 隧道一键开关（用网关面板操作即可）。

## 七、验证记录（实测）

隔离 DSH 实例（`DSH_HOME=C:\dshverify\home`，`mgw` profile）+ 真实网关插件 0.9.0，用
`harness/` 直接复用 App 的 `WsClient` + `GatewayClient` 源码跑通：

- 网关启动日志：`applying: version=0.9.0 ... path=/ws/mobile, webServer.port=3999`
- `HELLO protocol=3 dshVersion=0.2.0-rc.2 historyFormatVersion=4 authenticated=true`
- 省略 `sessionId` 发消息 → `mobile message created new session session-ec54…` → `sent`
- `subscribe(assistantStream:true)` → `subscribed` + `session-snapshot(events=6, cursor=5)`
- 真实模型回合：`turn/start → step/start → user/message → assistant/message「联调成功」→ step/end → turn/end`
- **审批闭环**：`approval/asked` → `approval-requested(pwsh, escalate sandbox)` →
  `approval-response(allowed-once)` → `approval-resolved(allowed-once)` + `accepted:true`
  → 命令真实执行，`assistant/message「命令已执行成功…hello-from-dsh」`
- **设备 token 重连**（App 日常路径）复验通过，重连后仍能看到 `running=true` 的会话

APK 侧：`minSdk 26 / targetSdk 36`、无 native-code（单一 APK 通吃所有 ABI）、
v2+v3 签名校验通过、`classes.dex` 528.8 KB、整包 668.6 KB。

## 八、回滚

- 安装前已打手动快照：`C:\Users\Administrator\.dsh\undo-snapshots\manual\20261001-010451-5d2d`
- 磁盘改动只有三处：`profiles/desktop/package.json`（deps + bundles）、
  `profiles/desktop/cordis.patch.yml`（末尾追加 overlay）、`profiles/desktop/node_modules/dsh-plugin-mobile-gateway*/`
- 撤销：让 DSH 用 `undo_restore` 回滚快照，或手动删掉上述 `node_modules` 目录与两处文本改动。

---

## 九、真机验证记录（2026-10-01 凌晨，荣耀 PGT-AN10 / Android 16）

插 USB、`adb` 授权后实测，**端到端全部跑通**，网关侧日志为证：

```
18:10:30 message accepted: text="Run the shell command echo phone-to-pc-ok …"
18:10:34 approval requested: tool=pwsh
18:10:47 approval response: outcome=allowed-once  accepted=true
```

手机屏幕上：用户气泡 → 工具条 `● pwsh 完成` → 审批卡（`批准一次` / `拒绝`）→ 点批准后变 `✓ 已批准`
→ 助手回复完整渲染（含 Markdown 行内代码）：`命令已执行。命令：echo phone-to-pc-ok 输出：phone-to-pc-ok`。

### 这轮在真机上暴露并修掉的 4 个 bug

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | 连上了但**永远没有会话列表**、任何请求都发不出去 | `WsClient.sendText()` 是同步 `out.write()`，却被主线程（收到 hello 的回调里）调用 → `NetworkOnMainThreadException`，又被 `catch (Throwable ignored)` 吞掉 | `WsClient` 增加**单线程写队列**，所有帧异步发送（同时天然保证帧序） |
| 2 | 点「扫码配对」**打不开相机**（一闪而过） | `Camera.setPreviewDisplay()` 在 `onResume` 里就调用，此时 Surface 还没创建 → 抛异常 → `catch(Throwable){finish();}` 静默退出 | 相机句柄只在 `hasSurface` 为真时打开；失败改为**在界面上显示原因**而不是退出 |
| 3 | 连接每隔几十秒自我拆建一轮 | `open()` 主动关掉旧连接，旧连接的 `onClosed` 又触发 `scheduleReconnect` → 互相踢 | 引入**代际 generation**，旧连接的迟到回调一律丢弃；重连任务去重；`onDestroy` 不再断开（客户端改为进程级共享） |
| 4 | 系统返回键**直接退出 App** | 没重写 `onBackPressed()` | 会话/设置页返回列表，列表页退到后台 |

### 顺带加的两个实用能力

- **App 内置协议诊断**：设置页底部显示 `state / gen / ws` 与最近 40 条收发帧（`→ sessions`、`← hello`…）。
  正是靠它一眼定位到 `NetworkOnMainThreadException`——因为这台荣耀的 `adb logcat` 取不到内容。
- **注入上下文过滤**：DSH 会把 `<system-reminder>`、`Current runtime context` 等作为 `user/message` 注入，
  桌面端不显示；手机端若不过滤就是一屏巨大蓝色气泡，现已过滤。

### 调试手法备查（荣耀机型）

- uiautomator 取精确坐标：`adb shell uiautomator dump /sdcard/u.xml` + `adb pull`
  **不要猜坐标**；输入框聚焦后软键盘/多行输入会让输入条位移。
- 安装被静默拒绝（`INSTALL_FAILED_ABORTED: User rejected permissions`）：
  `adb shell settings put global verifier_verify_adb_installs 0` 即可。
- 这台机器的 `adb logcat` 基本取不到内容，改用「App 内诊断」+ 网关日志双通道。
- 长时间无人值守调试：`adb shell svc power stayon true` + `locksettings set-disabled true`，
  收尾要恢复并 `input keyevent 223` 关屏（OLED 防烧屏）。

### 更正（2026-10-01 03:50）：返回键问题不是「没重写 onBackPressed」

首次真机测试时按返回键仍直接退出 App。**只重写 `onBackPressed()` 不够**：Android 15+ 对
targetSdk 35+ 的应用默认走**预测式返回（predictive back）**，系统不会再调用 `onBackPressed()`，
直接执行默认的「结束 Activity」。

最终修法是两条腿走路：

1. 清单里 `<application android:enableOnBackInvokedCallback="false">` —— 强制走传统返回路径；
2. `dispatchKeyEvent` 兜住 `KEYCODE_BACK` 作为主路径，保留 `onBackPressed()` 作回退，
   两条路对同一次按键可能都触发，用 400ms 时间窗去重。

已随 APK 出包并在 APK 内清单确认 `enableOnBackInvokedCallback=false`；
**该项尚未在真机上复测**（当时手机已锁屏、需要 PIN，无法继续自动化）。

---

## 十、真机复测修掉的第二批 bug（2026-10-01 下午）

应用已更名为 **DSH 掌上通**。

### 1. 满屏「(空响应)」/ 简洁模式看不到正文 —— 一个根因

**根因**：历史与快照事件（`session-snapshot` / `history`）里的助手正文**不在 `data.text`**，
而在 `data.message.content[]` 的 `{"type":"text"}` 块里；`data.text` 只存在于**实时** `event` 帧。
我原来只读 `data.text`，于是历史加载全是空串：完整模式显示「(空响应)」，简洁模式又把空助手气泡过滤掉 → 正文整片消失。

**修法**：新增 `textFromBlocks(payload, type)`，同时兼容
`{text,reasoning}`（实时）与 `{message:{content:[{type:'text'|'reasoning',text}]}}`（历史）；
并且**只在正文非空时才建气泡** + 过滤层对所有模式都跳过空正文助手气泡（双保险）。

实测：70 条 `assistant/message` 中 63 条能正确抽出正文，7 条纯工具回合不再生成空壳。

### 2. 工具条永远显示不出结果

同源问题：`tool/result` 的结果文本在 `data.message.content[]`，历史上**没有 `isError` 字段**
（那是实时帧的形态）。现在两条路径都兼容，并截断到 400 字符。

### 3. 注入上下文过滤改为以 `source.kind` 为准

原先用文字前缀猜，漏过 `Time sampled while preparing…`。实测各来源的 `source.kind`：

```
用户自己发的（含手机端）:  user                ← 保留
注入的:                  agent-instructions / runtime-context / skill-catalog /
                        time-context / tool-jobs / user-approval      ← 全部过滤
```

网关源码（`lib/index.mjs:3270`）本身也用 `message.source.kind === 'user'` 判定用户消息，
所以这个判据是协议级的、不是启发式。

### 4. 进会话停在顶部 + 需要一键回底

- **进会话停在顶部**：`setItems()` 之后立刻 `setSelection()` 时 ListView 还没重新布局。
  改为 `list.post(() -> list.setSelection(n-1))`。
- **新增右下角悬浮「↓」按钮**：不在底部时出现，点击**瞬时跳到底**并自动收起
  （原先用 `smoothScrollToPosition`，超长会话要滚很久，真机上看起来像没反应）。

### 事件类型全量审计（2377 条真实事件 / 6 个会话）

| 已正确处理 | 按设计忽略 | 暂未处理（v1 边界） |
|---|---|---|
| `user/message`、`assistant/message`、`tool/call`、`tool/result`、`turn/start`、`turn/end`、`session/title` | `step/start`、`step/end`、`agent/inbox/spliced`、`permission/preset`、`sandbox/mode`、`approval/policy`、`session/title-llm-request`、`developer/message`、`session/end-seed`、`subagent/catalog`、`workspace/changes` | `approval/asked+decided`（历史审批卡）、`command/run+done`（斜杠命令）、`deliverables/presented`（交付物卡）、`todo/write`、`goal/change` |

### 荣耀机型安装提示

`adb install` 可能依次弹出：①安装确认 → ②「未查询到 ICP 核准信息」→ ③应用信息页（需先勾选「已了解…」再点「继续安装」）→ ④**指纹验证**。
只要触发过一轮，后续同源安装会记住授权，一般不再弹指纹 —— 所以**调试期应把改动攒成一次安装**。

---

## 十一、功能补全与第二轮自审（2026-10-01 傍晚）

这轮不再等真机暴露问题，改为**先用真实事件流把形态审清、再动手**，并对现有代码做交叉自审。

### 新增功能

| 功能 | 依据（真实事件/响应形态） |
|---|---|
| **斜杠命令记录** | `command/run {commandId,name,args}` + `command/done {commandId,kind,text}`（此前完全不可见） |
| **交付物卡片** | `deliverables/presented {callId,files:[{description,path}]}`，文件名+说明，点一下复制路径 |
| **历史审批可见** | `approval/asked {id,toolName,reason,callId}` + `approval/decided {id,outcome}`；重开会话也能看到批准过什么 |
| **目标 / 任务提要** | `goal/change`、`todo/write` 事件 + `tasks`/`goal` 查询 + `tasks-updated`/`goal-updated` 推送，在会话顶部显示当前目标与任务清单（已完成的目标自动隐藏） |
| **图片发送** | 输入条「＋」选图 → 后台读字节 → 标准 Base64 → `message.images[]`（不带 `data:` 前缀） |
| **图片显示** | 历史里 `content[].attachment.attachmentId` 自动收集 → `attachment` 请求取回 → 后台解码 → 气泡上方显示（按 1080px 下采样） |

### 自审抓到的两个真 bug

1. **断线重连后不再订阅当前会话** —— 网关断开时订阅会丢失，而重连后只重新拉了会话列表。
   后果：网络抖动恢复后，正在看的会话**不再实时更新**（看着像「卡住了」）。
   修：`onState(READY)` 时若当前会话非空，重新 `subscribe` + 拉 tasks/goal。
2. **审批结果找不到卡片** —— 为了和历史事件 `approval/asked` 同键，审批卡改按 `approvalId` 建档，
   但 `onInteractionResolved` 仍在用 `rpcId` 查找 → 审批结果永远落空（卡片停在「待批准」）。
   修：两边统一键；并做了 `byKey` 全量交叉检查（`cmd:` / `approval:` / `tool:` / `question:` / `stream:` / `pending-user`）确认全部同键。

其他小修：工具参数从原始 JSON 抽成可读的「描述 / 命令 / 路径」；纯图片消息的本地回显去重。

### 端到端验证（无真机，用同一套协议帧直连网关）

**图片链路全通**（这是新增功能的完整回归）：

```
① 带图消息被接收            session-c0588e3e-…
② 历史里的图片块形态         {"type":"image","attachment":{"attachmentId":"sha256:c414…",
                            "mediaType":"image/png","width":1,"height":1,"bytes":70,"name":"test.png"}}
③ 取回附件                  70 字节，PNG 签名正确，字节完全一致 ✅
```

**任务/目标查询形态确认**（我原先的兼容逻辑是对的）：

```
tasks → {"todos": null}                                  ← todos 可为 null，已兼容
goal  → {"goal": {"goal": {objective, phase, …}}}        ← 确实两层嵌套，已兼容
         phase:"complete"                                ← 已完成目标不再占提要条
```

### v1 仍未做（明确边界）

文件下载（`file-download-open/read`）、斜杠命令与技能的**输入补全菜单**、模型与权限切换、会话 fork、会话全文搜索
（本机 `search` 通道因存在 v2 格式老会话而索引失败，属宿主侧问题）。

### 测试残留

调试期间用协议直接建了几个会话，其中 2 个因回合仍在运行无法立即归档；
它们会在回合结束后可归档，或在 App 里长按 → 「归档」即可隐藏。

---

## 十二、PC 端「手机接入」插件（dsh-mobile-access）

按方案 A 实现：**协议层一行不重写**（继续用 `dsh-plugin-mobile-gateway`），本插件只做接入编排与界面。

- 源码：`~/.dsh/local-plugins/dsh-mobile-access/`（`index.js` 宿主代理 + `client.js` 面板 + `cordis.patch.yml`）
- 装配：desktop profile 的 `package.json` 里加 `link:` 依赖 + `dsh.profile.bundles`

### 架构

```
浏览器面板 (client.js)  --fetch-->  /dsh-mobile-access/*
宿主代理 (index.js)     --loopback--> http://127.0.0.1:<webPort>/mgw/*
dsh-plugin-mobile-gateway            （协议层，第三方 MIT）
DSH Host 0.2.0-rc.2
```

**路由为何不放 `/api` 下**：DSH 的 `/api/*` 被浏览器信任围栏拦住（未知路径返回 401），
现成网关也因此放在 `/mgw`。实测对照：

```
/api/dsh-mobile-access/status  -> 401   （被围栏拦）
/dsh-mobile-access/status      -> 404→200（可达；重启后即为 200）
/mgw/status                    -> 200
```

### 实测验证（2026-10-01 18:0x，重启桌面版之后）

| 面板动作 | 代理路由 | 结果 |
|---|---|---|
| 生成配对二维码 | `POST /dsh-mobile-access/pair` | ✅ HTTP 201，SVG 10338 字符、配对串 420 字符 |
| 网关三态开关 | `POST /dsh-mobile-access/gateway` | ✅ persistent ↔ temporary 切换正常 |
| 设备列表 | `GET /dsh-mobile-access/devices` | ✅ 返回设备数组 |
| 撤销设备 | `POST /dsh-mobile-access/devices/<id>/revoke` | ✅ 清掉 28 个调试设备，仅保留手机 |

面板入口（设置 → 通用 →「手机接入」）已确认正常出现。

### 附带成果

桌面版重启后，**网关加载进了用户真正的 desktop profile** ——
即手机 App 现在连的已经是用户自己的 DSH（地址不变：`ws://192.168.1.100:3091/ws/mobile`），
不再依赖任何验证实例。

---

## 十三、新功能全量真机回归（2026-10-01 19:0x，荣耀 PGT-AN10）

APK 一次安装（696.6 KB，`DSH 掌上通`），逐项实测：

| 功能 | 真机结果 | 证据 |
|---|---|---|
| 会话列表按工作区分组 | ✅ | `程序开发 · 2` / `default-workspace · 2` / `desktop · 6` … |
| 真实标题（懒加载） | ✅ | 「手机App联动dsh控制电脑」「安装 Excel 表格处理技能」「功能验证-任务与交付物」 |
| 完整模式助手正文 | ✅ | 0 处「(空响应)」 |
| 简洁模式 | ✅ | `● 正在执行：pwsh`（不铺开参数） |
| 工具条 + 结果 + 参数美化 | ✅ | `● todo_write 完成 → Updated todo list: 1 pending, 1 in progress, 1 completed.` |
| 打开会话停在最新消息 | ✅ | 到底部，↓ 自动隐藏 |
| 右下角 ↓ 一键回底 | ✅ | 上滑后出现，点击瞬时到底并收起 |
| 系统返回键 | ✅ | 会话→列表；列表→退后台 |
| **任务提要（todo）** | ✅ | 顶部 `☑ 检查交付物卡片 / ◐ 检查任务提要 / ☐ 检查图片显示` |
| **交付物卡片** | ✅ | 「交付物」+ README.md + dsh-mobile.apk + 说明 + 「点此复制路径」×2 |
| **提问卡片** | ✅ | `Agent 在等你的回答` + header「颜色确认」+ 问题 + 选项（含描述）+ 自定义输入 + 提交/跳过 |
| **提问提交闭环** | ✅ | 手机上点「要」→ 提交 → 卡片变 `✓ 已回答`；**agent 回执确认收到「要」** |
| **图片显示** | ✅ | 界面出现 ImageView 节点；agent 采样确认真实像素 RGB(220,60,60) |
| 审批卡片（实时） | ✅ | `需要你的批准` → 点「批准一次」→ 命令真实执行 |

### 调试经验补充

- `adb install` 的三道门：**① 必须解锁屏幕**（锁屏时荣耀直接 `INSTALL_FAILED_ABORTED`，确认框都弹不出来）；
  ② `verifier_verify_adb_installs` 会被重置，装前重设一次；③ 指纹授权一旦通过会被记住一段时间。
- 手机前台可能被别的 App 占用（本次是微信），自动化点击前**必须先确认前台是本 App**，否则点击会打在别人身上。
- **写 Node 测试脚本要用 `-Encoding utf8`** —— 用 `ascii` 会把脚本里的中文变成 `?`，
  导致 agent 收到「整条被 ? 替换」的消息而误解指令（这个坑我踩过一次）。

---

## 十四、公网访问彻底跑通（2026-10-01 21:0x）

### 结论：不用开端口、不用自建反代、不用证书开关

**根因是三层叠加**（都做了实测取证）：

| # | 问题 | 证据 |
|---|---|---|
| 1 | 用户现有公网端口**从外网不可达** | 手机 5G `ping 203.0.113.10` 通，但 TCP `:16888` 超时；电脑却能连上 → 走的是**路由器回流(hairpin)**，外网其实关着 |
| 2 | 用户的反代(Lucky)**不转发 WebSocket** | 带 `Upgrade` 头打 `/ /ws /mobile /ws/mobile /mgw` **全部**返回代理自己的 `404 not found`；直连 3091/19387 均为 `101 Switching Protocols` |
| 3 | 反代证书是**自签名** | `颁发给: Lucky`，`UNABLE_TO_VERIFY_LEAF_SIGNATURE`；等效模拟复现 `DEPTH_ZERO_SELF_SIGNED_CERT` |

### 采用的方案：**复用网关内置的 Cloudflare 隧道**

关键发现（功劳归 `dsh-pocket`，正是用户截图里那个插件）：

```
dsh-plugin-mobile-gateway/lib/cloudflared-binary.mjs  自动下载 cloudflared（带 SHA256 校验）
dsh-plugin-mobile-gateway/lib/cloudflare-tunnel.mjs   支持 named / quick 两种模式，默认 quick
dsh-pocket/lib/tunnel.mjs                             startQuickTunnel —— 同一套思路
```

网关本就有这个能力，只是没打开。开启方式（纯 HTTP API，无需账号）：

```
POST /mgw/cloudflare   {"enabled":true,"mode":"quick"}   → 拉起随机域名隧道
POST /mgw/cloudflare/restart                              → 重启
GET  /mgw/status       → cloudflare.{state,mode,publicUrl}
```

**踩到的坑**：`cloudflared-binary.mjs` 会复用缓存二进制，但条件是 `cacheDir` 下
`<版本>-<平台>-<架构>.exe` 的 sha256 匹配。第一次下载超时后它**复用了失败的 promise**，
所以放好文件后要「关闭隧道 → 再开启」才会重新准备。
（本机放置路径：`~/.dsh/cloudflared-bin/2026.9.3-win32-x64.exe`，sha256 `f096265e…` 与官方发布一致。）

### 实测结果

```
网关 endpoints:  wss://example-tunnel.trycloudflare.com/ws/mobile   ← 第一顺位
                 ws://192.168.1.100:3091/ws/mobile
publicUrl:       wss://example-tunnel.trycloudflare.com/ws/mobile
cloudflare:      state=online  port=3082

手机（5G，移动数据，未连 WiFi）：
  connectedClients = 1
  HONOR PGT-AN10  online=True  connections=1
  应用内 status: state=READY     ← 真证书，未打开「允许自签名证书」
```

**隧道在线时 `publicUrl` 就是隧道地址**，所以「生成配对二维码」自动带上公网地址 —— 手机扫码即得外网访问。

### 面板侧新增

`dsh-mobile-access` 加了「公网访问（Cloudflare 隧道）」卡片与三条代理路由：

```
POST /dsh-mobile-access/tunnel          → /mgw/cloudflare
POST /dsh-mobile-access/tunnel/restart  → /mgw/cloudflare/restart
```

界面显示隧道状态（未开启/准备中/启动中/已在线/出错）、模式、公网地址（一键复制）、开启/关闭/重启。
并且**配对时优先使用隧道公网地址**（`makePairing` 里 `manualUrl || tunnelUrl || lanUrl`）。

> 注：客户端 bundle 是 DSH 启动时打包的，**面板这个新卡片要重启桌面版才看得到**。

### 稳定性说明

quick 模式的域名**每次重启都会变**。要固定域名有两条路：
1. **命名隧道**：域名 NS 迁到 Cloudflare → 建 Tunnel 拿 token → 面板/网关配 `mode=named` + hostname + token（用户域名当前托管在阿里云 `hichina.com`，需先迁 NS）；
2. **Tailscale**：手机装 Tailscale，用 `ws://100.x.x.x:3091/ws/mobile`（应用已把 100.64/10 与 `*.ts.net` 视为内网，不受明文限制）。

---

## 十五、意见反馈与折叠式设置（2026-10-01）

### 意见反馈（设置里的一个可点开项）

- 设置 → 「意见反馈」→「写反馈」→ 弹出反馈窗口：输入框 + **发送到电脑** / **复制** / 取消
- 自动附带环境信息：`v1.0 · 公网/内网 · state=READY gen=1 ws=open` + 当前地址
- 「发送到电脑」把反馈作为一条消息发进当前会话（未连接时自动降级为复制到剪贴板）
- 本地留档（SharedPreferences，最新在前），设置页显示「已记录 N 条 · 最近：…」

实测：窗口标题、输入框、自动附带的环境行、三个按钮全部正常；`state=READY` 说明当时正通过隧道连接。

### 折叠式设置

设置项变多后全部铺开太吵，改成**只有标题、点开才显示内容**：

```
连接设置 ▾   ← 默认展开
对话显示 ▸
关于     ▸
当前状态 ▸
怎么连？ ▸
意见反馈 ▸
```

实现：`SettingsView.section(parent, title, expanded)` 返回内容容器，标题行点击切换
内容与分隔线可见性，并把箭头在 ▾/▸ 间切换。

---

## 十六、交付物一键下载到手机（2026-10-01 稍晚）

### 协议（来自 PROTOCOL.md，先审后用）

```
→ {"type":"file-download-open","requestId":"d1","sessionId":"…","path":"相对工作目录的路径"}
← {"kind":"file-download-opened","requestId":"d1","transferId":"…","name":"…","size":733751,"chunkBytes":524288}
→ {"type":"file-download-read","transferId":"…","offset":0}
← {"kind":"file-download-chunk","transferId":"…","offset":0,"data":"<base64>","eof":false}
← 最后一块 eof:true 且带 sha256
```

**关键约束**：`path` **必须是会话工作目录内的相对路径**，绝对路径直接 `bad-request`（实测确认）。
所以交付物卡片里的绝对路径要先按会话 `cwd` 折算；不在目录内则明确提示，而不是发一个必失败的请求。

### 实现

- 协议层：`file-download-open` / `-read` / `-cancel` + `file-download-opened|chunk|cancelled|closed` 分发
- 下载流程：**分块串行拉取**（严格用「上一块 offset + 已解码字节数」作下一次 offset），
  写入临时文件并同步累积 SHA-256；`eof` 后校验整文件摘要，再落盘、删临时文件。
  文件 IO 走单独线程（`dl-io`），不占主线程
- 落盘：**API 29+ 走 MediaStore 的「下载/DSH 掌上通/」**（文件管理里直接可见，无需权限）；
  更低版本退到应用外部下载目录
- 交互：交付物行 **点击=下载到手机，长按=复制路径**；行内实时显示「请求中/下载中 N%/已下载 ✓ 路径/下载失败：原因」

### 实测

先纯协议验证（Node，与 App 同构的帧）：

```
② opened: name=dsh-mobile.apk size=733751 chunk=524288
③ 收到 733751 字节 / 声明 733751
   服务端 sha256 == 我算的 == 本地文件 sha256   ✅
绝对路径 → bad-request "file-download-open requires a relative workspace path"  ✅（约束确认）
```

再真机（点交付物卡片里的 apk）：

```
App: 已下载 ✓ 下载/DSH 掌上通/dsh-mobile.apk
手机: 733751 字节  → 与源文件完全一致
```

### 已知限制

- 只能下载**会话工作目录内**的文件（网关侧硬约束）。手机新建的会话 `cwd` 是 profile 目录，
  而 agent 的交付物常在项目目录 → 这类会提示「不在这个会话的工作目录内」。
- 实时推送的 `deliverables/presented` 事件在会话历史里能看到（重开会话会渲染卡片），
  但**当次实时**没有渲染出卡片 —— 待查是宿主未实时广播还是客户端漏处理。

---

## 十七、隧道域名轮换问题与扫码自动填充（2026-10-01 深夜）

### 用户反馈的 bug：公网连不上

**根因不是代码错，而是 quick 隧道的域名每次重启都会变**：

```
重启前（App 里存的）: condo-walnut-palestinian-did.trycloudflare.com   ← 已失效
重启后（网关在线）:   weblogs-impose-park-corpus.trycloudflare.com
（实测新地址握手正常：hello / auth=true）
```

App 里存着旧域名，自然连不上，`connectedClients = 0`。

### 为什么不能让 App 自己去问当前公网地址

查了协议（PROTOCOL.md）：

- `endpoints` **只在「配对载荷」和本机管理接口 `GET /mgw/status` 里给**，
  `hello` / `paired` **不含** endpoints；
- `/mgw/*` 受 `adminLoopbackOnly` 限制，**手机连不到**。

→ 所以 **配对载荷（二维码 / 配对串）是 App 拿到当前公网地址的唯一自动通道**，
「扫码自动填」不是可选优化，而是唯一正确路径。

### 已实现的四处改动

| 改动 | 说明 |
|---|---|
| **扫码一次填好两个地址** | 解析配对载荷的 `endpoints[]`：私有网段写「内网」槽、公网/隧道写「公网」槽；只有公网时自动切到「用公网」 |
| **连不上自动切换** | 连接失败时在内网/公网之间**自动切一次**（只切一次，避免来回跳），新增 `GatewayClient.Listener.onReconnectScheduled` |
| **失效明确提示** | 地址含 `trycloudflare.com` 或错误含 404 时，直接提示「公网地址可能已失效（隧道域名每次重启会变）→ 重新扫码」 |
| **安全免责声明** | 手机端：首次切「用公网」弹窗 + 勾选「我已知情」才放行（存一次性确认）；面板端：**每次**开启公网隧道都弹窗（对齐 dsh-pocket） |

### 想让地址永久固定？

quick 隧道做不到（协议里它是随机域名）。两条路：

1. **命名隧道**：把 `3260571.xyz` 的 NS 从阿里云（`hichina.com`）迁到 Cloudflare，
   建 Tunnel 拿 token/hostname，再 `POST /mgw/cloudflare {"enabled":true,"mode":"named","hostname":…,"token":…}`
2. **Tailscale**：手机与电脑加入同一 tailnet，App 填 `ws://100.x.x.x:3091/ws/mobile`
   （App 已把 100.64/10 与 `*.ts.net` 视为内网，不受明文限制；地址永不变、且不暴露公网）
