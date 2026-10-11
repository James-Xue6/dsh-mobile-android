# GlassUI 迭代报告 V3.85 | 2026-10-11（待发送显示 ID · 待发送菜单 · 对话框内生成物打不开）

> **触发**：用户「1、**对话框内的生成物，点击应该可以打开啊，现在怎么也打不开**。
> 2、**待发送的内容手机端现在显示的是一串字符**，不是输入的内容。还有**待发送的这个栏不能选修改、立即发送和删除了**」。

---

## 1. 「一串字符」：取错了数组下标 ✅（根因明确）

`ConversationView#setPendingItems` 的契约是**每项 `{itemId, text, placement}`**（见其 javadoc）。
而 `GlassChatView.setPendingItems` 镜像给 Compose 时取的是 **`a[0]` = itemId** ✗
⇒ 屏幕上自然就是"一串字符"（队列 ID）✓。

**修**：改取 **`a[1]` = 文本**；并把 `itemId` 一起编码（`itemId\u0001text`）供点击那一步用；
`addPendingSend`（没有 itemId 的入口）同样按这个编码写入，保持一致。

## 2. 「待发送栏不能选修改/立即发送/删除」✅

**宿主这一侧本来就有**：`ConversationView.Host#onQueueItemAction(itemId, text)`
（javadoc 写着「弹出 立即插入 / 编辑 / 删除」），`MainActivity:4333` 也实现了它。
**缺的是 Compose 侧的入口** ✗（我把那一行渲染成了纯文字、没有点击）。

**修**：`GlassChatHost#queueItemAction(itemId, text)` → `GlassChatView` → 宿主；
「待发送」那条胶囊**整条可点** ⇒ 弹出「立即插入 / 修改 / 删除」。

## 3. 「对话框内的生成物点击打不开」✅（根因明确）

交付物行的点击 = `previewFile` → `startDownload(item, path, preview=true)`，
里面先做 `relativize(sessionCwd, path)`；**而 `deliverables/presented` 的 `path` 按契约本来就是
相对会话工作目录的**（见 `model/ArtifactEntry` 的注释：「本来就是相对路径（契约 §2.1）」）
⇒ 对相对路径再 `relativize()` **必然得到 null** ⇒ 一直弹「不在会话工作目录内」、**永远打不开** ✗。

**修**：补上回退分支 —— 当路径本来就是相对路径（不含盘符/`..`/`file:`）
就直接当相对路径用（与「生成物」面板同一条口径，那条一直是对的）。

## 4. 项目明细

| 条目 | 状态 | 证据 | 未成功的原因 | 下一步 |
|---|---|---|---|---|
| 待发送显示真实文本（不再是一串 ID） | ✅ 根因定位+已修+装机 | `setPendingItems` 取 `a[1]` | — | 真机排一条待发送看文字 |
| 待发送可点：修改/立即插入/删除 | ✅ 已实现+装机 | `queueItemAction` → `onQueueItemAction` | — | 点那条胶囊应弹菜单 |
| 对话框内生成物点击可打开 | ✅ 根因定位+已修+装机 | `startDownload` 增加相对路径回退 | — | 点交付物卡应能打开预览 |
| 三条的**画面验收** | ⏳ 未验证 | 构建通过；真机+模拟器均已安装（sha `4594f048…`） | 演示切片里没有"待发送队列"数据；生成物要真实会话才有 | 你在真机上试这三处 |
