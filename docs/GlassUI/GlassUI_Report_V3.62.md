# GlassUI 迭代报告 V3.62 | 2026-10-11（**"所有会话全空"彻底修好**：真根因 = 延时任务挂在未挂载的旧视图上）

> **触发**：用户「继续吧，现在报错了，还是没有消息」（那句"报错"其实是我挂的红色诊断读数）。

---

## 1. 🎯 真根因（有机制、有数字、有真机复验）

玻璃模式下**真正在窗口里的是 Compose 视图**，旧的 `ConversationView`（`legacy`）**从未被 attach**。
而 `View.postDelayed` 对**未挂载**的 View 只是把任务塞进它自己的 RunQueue、**要等挂载才执行**
⇒ 挂在它上面的延时任务**永远不跑**。

| 时间 | 我做了什么 | 后果 |
|---|---|---|
| V3.50 | 为修"吐字一顿一顿"，把"**立刻写 UI 状态**"从**直接调用**改成 `legacy.postDelayed(mirrorFlush, 70)` | **写状态这条路彻底断掉** ⇒ 会话打开永远空 |
| V3.58 | 加了"**假空保护**"（过滤结果为空就 `return`） | 又把 pull 那条路 `return` 掉 ⇒ **两条路都断** ⇒ 状态永远 0、且**不报任何错** |
| V3.57 | 我曾怀疑"`legacy` 未挂载"，但当时"只加保护看起来好了"⇒ **误判为不必要而回退** | 反复的根源之一 |

**真机证据链（手机屏上读数，非推测）**：
```
snap#1 ev=57 itemsN=17 [assistant/message tool/call tool/result …]   ← 映射出 17 条 ✓
而 Compose 侧一直 items=0 raw=0                                      ← 从没写进 UI 状态 ✗
```

## 2. 修复（`GlassChatView`，3 处）

把两个延时任务（节流 flush / 120ms 过滤拉取）从 `legacy.postDelayed` 改到**主线程 Handler**：

```java
private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
… ui.postDelayed(mirrorFlush, MIRROR_THROTTLE_MS);
… ui.postDelayed(pendingVisPull, 120L);
… ui.removeCallbacks(mirrorFlush);
```
（V3.58 的"假空保护"保留 —— 它防的是另一件事，两处并存才是正解。）

## 3. ✅ 真机复验（uiautomator dump 原文）

```
⚠ 修改了文件，已读取文件，已搜索代码等（有失败）   21:21
▸ 思考过程 334 字
🎉 **消息回来了！** UI dump 读到真实内容（…）
已读取图片 · read_image                          02:40
▸ 思考过程 2264 字
已读取文件，修改了文件，执行了命令
```
⇒ **消息、思考过程、工具调用行、时间分隔、右侧刻度条全部正常** ✓；
屏上红色诊断读数**已撤除** ✓；`dist/dsh-mobile.apk` sha `f5201b7c…`（已装真机 ✓，无新增 App 崩溃）。

## 4. 这轮同时确认的其它结论

| 结论 | 证据 |
|---|---|
| 网关 history/snapshot **正常返回数据** | 诊断日志：`records=827 hasMore=true`（补丁生效 ✓） |
| App 的**事件映射本身没问题** | `itemsN=17`（`user/message`/`assistant/message`/`tool/*` 都映射成功 ✓） |
| 「目标/计划」一直正常是因为走 projections（另一条帧） | 与我先前观察一致 ✓ |
| ⚠️ 我自己的 zstd 切帧工具不可靠 | 某文件 2498 个帧头只解出 261B ⇒ 旧数字偏低，已记录 |

## 5. 交接单

| 条目 | 状态 | 证据 |
|---|---|---|
| "所有会话全空" | ✅ **已修 + 真机复验** | 上表 dump 原文 + `791_v362_clean.png` |
| 网关 history 隐患（非 event 抛错 / 缺 maxMessages） | ✅ 已修（补丁生效、日志在写） | `mobile-gateway-diag.log` |
| 桌面 asar（v2 会话白名单） | ✅ 已装 | `app.asar`=121348401 + 备份 |
| 目标卡收起/展开均为胶囊 | ✅ 已装并取证 | 740 / 741 |
| 历史按轮数自动拉全 | ✅ 已装（待你在会话里复看轮数） | `MainActivity.autoPageStep` |
| 屏上诊断读数 | ✅ 已撤除 | 本轮 |
| 代码里 `debugRaw/debugVis/debugHead/debugErr` 字段 | ⏳ 待清理（不影响功能） | — |
| 任务胶囊"质感"方向 / 「顶部弹窗」指哪个 | ⏳ 等你一句话 | — |
