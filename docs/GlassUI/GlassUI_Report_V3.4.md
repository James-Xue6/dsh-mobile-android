# GlassUI 迭代报告 V3.4 | 2026-10-08

> 目标轮次：**② Chat 页 —— 挂进 MainActivity 宿主（②b 数据桥 + ②c 换挂）**
> 上轮：V3.3（9 类行接真实 `ChatItem`）　**验收：模拟器 + 真机**

---

## 1. 关键约束：`ConversationView` 是 **final 类**，不能继承覆写

`MainActivity` 对它有 **36 个方法**的调用（setItems / setTitleText / setSubtitleText / setRunning /
setBanner×2 / setPlan / setPlanRich / setTasksProgress / setSubagentEntry / setMoreStatus / setReadOnly /
setPendingBanner / setPendingItems / addPendingSend / clearPendingSends / setPendingAttachments /
setPermissionChip / setModelLabel / setEffortLabel / setUsageLabel / setProjectLabel / setCompact / setDraft /
draftText / hasDraft / focusInput / refresh / refreshNow / scrollToBottom / scrollToKey / planFullText /
projectPath / applyTheme / onConfigChanged / onWindowInsetsChanged）。

**做法：同名「双写包装」** —— 新增 `glass/GlassChatView.java`（组合，不继承）：
- 每个同名字段方法：**先转发给旧 `ConversationView`，再把值镜像进 `GlassChatState`**；
- `MainActivity` 侧只改了 **3 处**：字段类型（`ConversationView` → `GlassChatView`）、
  创建处（`new GlassChatView(this, this, USE_GLASS_CHAT)`）、挂载处（`setContent(convo.view())`）；
  **36 个调用点一行未改**。
- 动作侧：`GlassChatView implements GlassChatHost`，**全部转发给同一个 `ConversationView.Host`（就是
  `MainActivity` 自己）** ⇒ 发送 / 停止 / 附件 / 任务列表 / 更多 / 子智能体 / 加载更早 / 审批 / 提问 /
  交付物下载 / 复制路径 走的都是**原有真实流程**。

## 2. 旧视图为什么必须继续被喂着（两个理由）

1. **它是「简洁模式」的过滤引擎**：`ConversationView.applyFilter()` 会把一串工具调用压成一条 STEP 摘要行
   （`setCompact(true)` 时），这个变换只在旧视图里。为此给 `ConversationView` 加了**一个只读 getter**
   `visibleItems()`（返回 adapter 当前的可见行，**纯新增、零行为变化**），包装类在 120ms 后把它捞过来覆盖。
2. **它是「一行回退」的落点**：`USE_GLASS_CHAT=false` 时 `view()` 直接返回旧视图（旧代码一行没删）。

## 3. 其它配套改动

| 项 | 说明 |
|---|---|
| `GlassComposeHostOwner` 挂到 **`root`**（onCreate 里一次性） | 设备页/对话页两个 `ComposeView` 都被覆盖；不必等某个屏幕先显示 |
| 状态新增 `planProgress` / `scrollToBottomTick` | 对应 `setTasksProgress` 与 `refreshNow/scrollToBottom`（Compose 侧用 `animateScrollToItem`） |
| `GlassChatHost.draftChanged(text)` | **草稿双向**：Compose 输入框每次改动回写 `legacy.setDraft`，因为 `MainActivity` 的发送/语音流程读的是 `convo.draftText()` |
| 列表换 `LazyColumn` + 贴底跟随 | 只在已贴底时跟随新消息（与旧 `computeAtBottom` 同口径） |
| 调试钩子 `--ez glass_force_chat true` | 没有配对网关时也能验收托管后的对话屏（会复张两次，避免被启动逻辑顶掉） |

## 4. 验收

| 取证件 | 结果 |
|---|---|
| `evidence/v340/01_emu_hosted_chat.png` | **模拟器**：MainActivity 的对话屏 = Compose 新版；顶栏副标题显示的是「走内网 · 自动 · 未连 WiFi（没有可用内网…」——**这正是 `MainActivity.subtitleText()` 的真值**，证明双写镜像生效 |
| `evidence/v340/02_emu_drawer_from_compose.png` | 点顶栏「‹」→ **旧抽屉真的滑出来了** ⇒ 动作链路 `GlassChatHost.openTasks() → Host.onOpenTasks() → MainActivity` 打通（抽屉本身是下一步 ④） |
| `evidence/v340/12_real_hosted_chat3.png` | **真机**：同上（无会话时列表空、副标题空 = 真实值）；`mCurrentFocus` = `MainActivity` |
| 崩溃 | 真机 `dumpsys dropbox` 最后一次 `data_app_crash` 仍停在 **21:22:47**（V3.0 之前）→ 本轮无新崩溃；模拟器 dropbox 最后一条是 **2026-10-06** 的 proto 页旧记录 |

> 真机安装两次遇到 `INSTALL_FAILED_ABORTED: User rejected permissions`（手机在用时系统安装器弹确认框），
> 先 `keyevent 3` 回桌面再装即 Success —— 记录备查。

## 5. 目标 ② 结论与遗留

| 步 | 内容 | 状态 |
|---|---|---|
| ②a | 9 类行接真实 `ChatItem` | ✅ V3.3 |
| ②b | 36 个调用点接进 `GlassChatState` | ✅ 本轮 |
| ②c | `showChat()` 换挂 Compose（一行回退） | ✅ 本轮 |
| ②d | 真机 + 浅色验收 | ✅ 本轮；**深色待用户切** |

**遗留（不阻塞 ③）**：
1. **真实网关消息未实测**——需要一台配对好的电脑（真机当前无配对设备、模拟器只有个假设备）。
   数据面已接通（`MainActivity` 的 `List<ChatItem>` → `setItems` → state → Compose），等有会话即生效。
2. `scrollToKey(深链定位)`、`focusInput()`、`setPendingBanner` 与 `banner` 的合并显示：Compose 侧暂为"转发但不接管"，
   已在报告与交接里挂账。
