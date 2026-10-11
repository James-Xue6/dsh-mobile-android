# GlassUI 迭代报告 V3.25 | 2026-10-09

> **触发**：用户「继续 + 刚才那几个按钮（项目/模型/权限…那一排 chip）**看不太清楚，加磨砂、白一些**」。
> **A 类质感数值改动 = 2**（chip：`blur 0 → 8dp`、`tint 渐变 → 白 55%`），外加文字色 inkSub → INK。

---

## 1. chip 那一排：从"平色渐变凑观感"换成**真玻璃胶囊**

### 1.1 为什么以前不敢上真玻璃（V3.17）
V3.17 记录：给 chip 套 `RefractionWindow` 会在**只给高度约束**时撑满父宽 ⇒ 三个 chip 变成三条通栏互相叠住。
根因与小元件同款 —— 组件内层是 `Box(fillMaxSize())`，**宽度约束有界就撑满**。

### 1.2 现在为什么可以
V3.23 在弹窗上验证过同一件事：给调用处加 **`wrapContentWidth(unbounded = true)`** 即可按内容自适应。

### 1.3 改法（一处，`GlassChatScreen.kt` 的 chip 行）
```kotlin
RefractionCapsule(                                   // ← 真玻璃（真磨砂 + 折射 + 受光棱）
    backdrop = backdrop,
    modifier = Modifier.padding(horizontal = 3.dp)
        .wrapContentWidth(unbounded = true)          // ← 关键：内容自适应，防"通栏"
        .height(30.dp)
        .glassTap { host.chipTap(c) },
    blurRadius = 8.dp,                               // 磨砂（原来是 0：纯渐变，没有模糊）
    refractionHeight = 6.dp, refractionAmount = 8.dp,// 小元件压小折射（V3.17 结论：默认 14/28 会推挤标签）
    tint = Color.White.copy(alpha = 0.55f),          // 白度（原来是白 30%→10% 的渐变，偏灰）
) { Text(c, color = Color(Ui.INK), fontSize = Ui.S_CAP1.sp, …) }   // 文字 inkSub → INK（"看不太清楚"）
```
- 平滑标题/说明 chip 的 3dp 间距、30dp 高度、12dp 内边距**都没变**（只换材质）；
- `text color inkSub → INK` 是**可读性**改动，不计入质感数值。

### 1.4 实测
| 检查项 | 结果 |
|---|---|
| **宽度仍按内容**（V3.17 的通栏不复现） | ✅ `项目 · 程序开发` 214px / `模型 · …` 394px / `权限` 63px / `生成物` 65px（`evidence/v3260/80`） |
| 视觉 | ✅ 白透胶囊 + 受光棱、文字清晰（放大图 `82/84_chips_zoom.png`） |
| 崩溃 | ✅ `logcat -b crash` 0 条 |

> 注：本轮模拟器背景换成了紫色环境色（另一条会话的配色），所以"改前/改后"不在同一底色上；
> 材质与文字色两组数值已按 §1.3 明确记录，便于复核。

## 2. 「加号 → 添加附件」搬进**玻璃菜单形态**（§6 第 3 条③ 的第一半）

- `MainActivity.onAttachPick()` 优先走玻璃：`GlassChatView.showSheetMenu("attach", "添加附件", items, onPick)`
  （骨架的**菜单形态**本来就有；这里借 `onListPick` 承载选中回调，`GlassChatView` 里**必须在 `showMenu` 之后设置**
  —— `showMenu` 内部会 `clearForm()` 把它清掉）；不可用回退旧 `ui/AttachSheet`。
- **顺带修**：`GlassChatScreen` 的 overlay 在菜单形态下点选**不会收起**（列表形态自己会收）。
  按旧 `AttachSheet` 的行为补上：**先收起，再回调**（否则面板会盖住刚打开的相机/选择器，看起来像"点了没反应"）。

### 实测（`evidence/v3260/92·93`）
| 检查项 | 结果 |
|---|---|
| 加号 → 面板 | ✅ 玻璃菜单：`添加附件 / 文件（文档、压缩包，任意格式）/ 相册（从相册里选图片）/ 拍照（现在拍一张）/ 取消` |
| 点「相册」 | ✅ **面板先收起** → 打开系统照片选择器（`PhotoPickerGetContentActivity`，前台包已核对） |
| 崩溃 | ✅ 0 条 |

## 4. 补丁（用户报「这个弹窗总是弹」）：会话读取失败别再刷 Toast

### 4.1 定位（证据链）
1. **字符串来源 = 电脑端宿主**（`D:\AI\DSH\resources\app.asar`）：
   `new SessionQueryError('failed to observe session "…": …', "SESSION_QUERY_PERSISTENCE_FAILED", …)`。
2. **底层原因**（模拟器 logcat 原文，含 raw 路径）：
   ```
   text=failed to observe session "session-2e276486-…": cannot safely transform unclassified message source;
        source v2 artifact remains unchanged
        (raw log: C:\Users\Administrator\.dsh\sessions\--F-AI-工作台设计--\session-2e276486-…\session.v2.jsonl.zstd)
   ```
   ⇒ 宿主侧**某些会话的 v2 日志无法安全转换**（数据层问题，不是 App 的 UI 问题）。
3. **为什么"总是弹"**：`MainActivity.onProtocolError` 的兜底分支**把原始英文 message 直接 Toast**；
   而每次**后台刷会话列表**都会再回一帧同样的错误 ⇒ 反复弹，甚至触发系统配额：
   ```
   Package com.dsh.mobile is above allowed toast quota, the following toast was blocked and discarded: …
   ```
   而且失败的多是**别的会话**（日志里两个 id 都不是当前会话）——用户根本没在操作它。

### 4.2 修法（`onProtocolError`，一处）
与项目里已有的 `session/agent-busy` 先例**同一套处置**：
- **当前会话** → 顶部**内联横幅**（中文说明 + code、会一直挂到恢复），不再弹 Toast；
- **非当前会话** → **完全静默**，只写 `evtLogAdd`（「设置 → 当前状态」可查，不打扰用户）。
覆盖 `SESSION_QUERY_PERSISTENCE_FAILED` / `session/query-failed` / `session/persistence-failed`。

### 4.3 实测
| 检查项 | 修前 | 修后 |
|---|---|---|
| 45 秒内"toast 超配额被拦"条数 | 反复出现 | **0** |
| 45 秒内 `failed to observe` toast 尝试 | 反复出现 | **0** |
| 错误可见性 | 英文 Toast（反复、还会被系统吞） | ✅ 顶部横幅「这个会话暂时读不到内容（SESSION_QUERY_PERSISTENCE_FAILED），还在重试…」（`evidence/v3260/97·98`） |
| 崩溃 | — | 0 条 |

### 4.4 ⏳ 真正的根因在电脑端（未动，待用户定）
那两三条会话（在 `--F-AI-工作台设计--` 工作区下）的 **v2 会话日志转换失败**。
这是宿主/数据层的事：要修得先查它们为什么无法安全转换（内容块类型未知？被并发写入？），
或把这两个 session 目录**隔离**让宿主不再尝试观察。**需要用户点头再动宿主/数据。**

## 5. 追加（同日·续）：「子智能体」弹窗 + 「⋯ 菜单」都搬进玻璃

### 5.1 「子智能体」→ 玻璃列表（§6 第 3 条③ 的另一半，已验）
`openSubagentSheet()` 优先 `glassSubagentList(root, kids)`：
- 行文案与旧 `ui/SubagentSheet.entry` **逐字段对齐**：标题 = `主智能体/子智能体 N · 标题`；
  副文案 = 状态（❓待回答 / ⚠待批准 / ●运行中 / ○已完成）· 相对时间（`Ui.ago`）· 工作目录尾段 · 智能体预设；
- **主智能体永远第一行**（可点回主）；**当前会话高亮打勾**（旧面板也是 `SELECT_BG` + ✓）；
- 点任意一项走 `onOpenSession`（与抽屉里点会话卡同一条路，不另造切换逻辑）；空态 = 「这条会话名下还没有子智能体」。
实测：`子智能体 / 8 个子会话 · 点一项切过去看它在做什么 / 主智能体 · dsh App 四项优化安排 ✓ / 子智能体 1..7 …`（`evidence/v3260/102`），crash 0。

### 5.2 「⋯ 菜单」→ 玻璃菜单（§6 第 6 条）
`onMenu()` 优先 `showSheetMenu("menu", "更多", …)`；三选一动作抽成 **`menuPick(what)`**（新旧两条路共用一份，
免得以后分叉）。**顺便更正交接描述**：⋯ 的现状是**3 项**（重命名 / 停止当前回合 / 连接设置），
不是交接里写的那 6 项（那只是设想）。
实测：`更多 / 重命名 / 停止当前回合 / 连接设置 / 取消`（`evidence/v3260/103`），crash 0。

### 5.3 桌面端「会话读不到」根因：**已推进但停在这里（等工具链）**
- 拿到宿主的白名单（`SOURCE_KINDS`）：`user / plugin / model / tool / agent-instructions / session-reference /
  team-message / goal / skill-invocation / skill-catalog …`；坏会话里必有一条 `source.kind` 不在白名单。
- 坏的会话都在 `~/.dsh/sessions/--F-AI-工作台设计--/`，且都是 **9 月的 v2 老会话**（没有 `session.v4.jsonl.zstd`；
  同目录里已迁移的那些都有 v4）。
- **卡点**：要定位到具体那条消息，得解开 `session.v2.jsonl.zstd` —— 目前**没有可用的 zstd 解压**：
  `app.asar` 只能 grep（read 会报 `Cannot mix BigInt`）、bsdtar 不解**裸 zstd 流**、
  `app.asar.unpacked` 里没有 zstd 原生模块。**下一步**：找 `7z`（7-Zip 21+ 支持 zstd）或用宿主的 node 侧 zstd 模块解一次。

## 7. 追加（同日·三续）：子智能体胶囊 → 与**输入文本框**同一折射档位 + 更白（用户指定）

**用户原话**：「子智能体最好跟下方**发送的那个文本框**一个折射档位，然后可以稍微再白点」
（先说了"发送按钮"，随即更正为"发送的那个**文本框**" ＝ 输入胶囊）。

**改前**：`SubagentChip` 用 V3.17 为小元件压低的档位（环带 6dp / 幅度 8dp；tint 用全局默认 20%）。
**输入胶囊**（`ChatInputRow` 里的 `RefractionWindow`）用的是**默认档位**：环带 **14dp** / 幅度 **28dp**。

| 项 | 改前 | 改后 |
|---|---|---|
| 折射档位 | 6dp / 8dp（V3.17 小元件档） | **`RefractionWindowTokens.RefractionHeight/Amount`（14dp / 28dp）** —— 显式引用 tokens，与输入框同档，以后全局默认变它也跟 |
| 白度 tint | 默认（浅 20% / 深 #14121C 30%） | **浅 32% / 深 #14121C 22%**（深色＝把深体色减淡 ⇒ 也更亮更白） |

**实测**（同一帧对比：切片页 `GlassChatPageActivity` 自带 `subagentCount=2` + 输入框）：
- 两端**同样出现镜头弯折**（档位一致）✓；标签居中**没有被推挤变形**（14dp 环带在 38dp 高胶囊上仍不碰中间文字）✓；
- 胶囊体色比输入框**略亮**（32% vs 20%）✓；崩溃 0。
- 取证：`evidence/v3260/115_slice_chat.png`、`116_chip_vs_input.png`（上下并排放大）。

> ✅ 顺带把 **§6 第 3 条②「子智能体折射档位」** 按用户指定方向落地（不再是"需用户给方向"）。

## 8. 状态
| 项 | 状态 |
|---|---|
| chip 真玻璃化（磨砂 + 白度 + 文字色） | ✅ 已改 + 已验 |
| 「加号/附件选择」弹窗玻璃化 | ✅ 已改 + 已验（行点击 → 系统选择器，路径通） |
| 「子智能体」弹窗玻璃化 | ✅ 已改 + 已验（`evidence/v3260/102`） |
| 「⋯ 菜单」玻璃化（3 项） | ✅ 已改 + 已验（`evidence/v3260/103`） |
| §6 #3 ② 子智能体折射档位 | ✅ 已按用户指定落地：与**输入文本框**同档（14/28dp）+ 白度 32%（`evidence/v3260/116`） |
| 任务面板 / ⋯ 菜单 / 弹窗返回键 | ⏳ 未做/待定 |
