# GlassUI 迭代报告 V3.3 | 2026-10-08

> 目标轮次：**② Chat 页（真实 `ChatItem` 9 类行）** 的**数据层 + 渲染层**
> 上轮：V3.2（对话页结构：内容进取样层 + 底部三件式）　**验收：模拟器 + 真机**

---

## 1. 本轮做了什么

V3.2 解决的是"**看起来像不像玻璃**"，本轮解决"**画的是不是真数据**"。

### 1.1 结构（与设备页同一套：State + Host + 屏幕）

```
GlassChatScreen.kt
├─ class GlassChatState    宿主推的快照：items/messages + 标题/副标题 + running + banner
│                          + 目标·任务提要 + 子智能体数 + 分页状态 + 只读 + 待发送 + 附件数
│                          + 能力 chip + draft（双向）+ themeRev
├─ interface GlassChatHost 全部动作回调宿主：send/stopTurn/attachPick/openTasks/menu/
│                          openSubagents/loadMore/togglePlan/approve/questionSubmit/
│                          downloadFile/copyPath
└─ @Composable GlassChatScreen(state, host, applyInsets)
```

### 1.2 9 类行**直接吃 `ChatItem`**（不另做映射层）

`ChatItem` 就是 `MainActivity` 内存里 `List<ChatItem>` 的元素类型。Compose 直接读它的公共字段，
宿主每次 commit 换一个**新的 list 实例**即可触发重组（语义等同原 ListView 的 notifyDataSetChanged）。

| kind | 取哪些字段 | 渲染 |
|---|---|---|
| USER | `text` | 右侧品牌渐变气泡（alpha 224，圆角 18） |
| ASSISTANT | `text` | 左侧半透明卡（`Ui.GLASS_CARD` 口径，圆角 18） |
| TOOL | `toolName` / `toolPreview` / `toolRunning` / `toolError` | 圆角 28 卡；运行中 → 标题带「· 运行中…」；错误 → 红标题 |
| STEP | `text` / `stepRunning` / `stepError` | 单行摘要；运行中品牌色、错误红、否则次要色 |
| APPROVAL | `text` / `reason` / `resolved` / `resolvedOutcome` / `pendingConfirm` / `sendError` | 允许/拒绝；已处理→「已允许/已拒绝」；待确认→「已发送，等待电脑确认…」 |
| QUESTION | `questions[{id,header,question,detail,multiSelect,options[{label,description}]}]` + `picked`/`typed` | 逐题渲染 + 选项行（选中态品牌淡底；单选/多选都支持，选择写回 `picked`） |
| FILES | `files[{path,description}]` / `downloadState` | 「交付物」+ 文件名/描述/下载态；点 → 下载，长按 → 复制路径 |
| AGENT | `agentName` / `text` | 带人图标的卡（发言人名字，不显示裸 id） |
| 系统条 | `text` | 居中弱色小字 |
| 时间分隔条 | 相邻两条 `time` 差 ≥ 5 分钟 | 居中 `HH:mm`（与正式版同粒度） |

**列表换成 `LazyColumn`**：长会话不再一次性建满（原 ListView 的等价物）。

### 1.3 独立切片同步改造
`GlassChatPageActivity` 变成薄壳：**演示数据也刻意用真的 `ChatItem` 对象**
（`ChatItem.of(...)` + 直接写字段 + 真 `JSONArray`）—— 这样渲染路径与正式版**完全一致**，
宿主把真 `List<ChatItem>` 推过来时 UI 一行都不用改。

---

## 2. 验收（模拟器 + 真机）

| 取证件 | 结果 |
|---|---|
| `evidence/v330/01_emu_chat.png` | 9 类行**全部由真 `ChatItem` 字段渲染**；时间分隔条「14:02」（≥5min 判定生效）；顶栏玻璃糊着背后内容；底部三件式 + 子智能体胶囊 + 能力 chip 行 |
| `evidence/v330/02_emu_chat_scrolled.png` | 滚动后玻璃仍在糊内容（V3.2 的结构保持） |
| `evidence/v330/11_real_chat_v33.png` | **真机**：同类渲染、顶栏/底部玻璃、时间条「22:04」；`dumpsys window` 前台 = `GlassChatPageActivity` |
| 崩溃 | `dumpsys dropbox --print` 最后一次 `data_app_crash` 仍停在 **21:22:47**（V3.0 修好之前）→ 本轮无新崩溃 |

> 真机安装有一次 `INSTALL_FAILED_ABORTED: User rejected permissions`（手机在用时系统安装器弹了确认框），
> 回到桌面再装即 Success —— 记录备查。

---

## 3. 目标 ② 还差什么（下一轮）

| 步 | 内容 | 状态 |
|---|---|---|
| ②a | 9 类行接真实 `ChatItem` + State/Host/屏幕三件套 | ✅ **本轮完成** |
| ②b | **把 `ConversationView` 的调用点接到 `GlassChatState`**：`setItems/setTitleText/setSubtitleText/setRunning/setBanner/setPlanRich/setSubagentEntry/setMoreStatus/setReadOnly/addPendingSend/setPendingItems/clearPendingSends/setPendingAttachments/setPermissionChip/setModelLabel/setEffortLabel/setUsageLabel/setProjectLabel/draftText/setDraft/focusInput/hasDraft/refresh/scrollToBottom/onWindowInsetsChanged` 等（约 25 个） | ⏳ 下一轮 |
| ②c | `showChat()` 里把 `convo` 换成 `ComposeView`（`USE_GLASS_CHAT` 一行回退，旧 `ConversationView` 不删） | ⏳ 下一轮 |
| ②d | 真机 + 浅/深两主题验收 | ⏳ 下一轮 |

> ⚠️ ②b/②c 必须**一起**上线才能不回归（否则发送/审批/提问/附件/待发送/计划这些流程会断）。
> 所以本轮只做数据层与渲染层，界面上仍由「掌上通·新界面」入口进入，正式版首页与对话屏未动。
