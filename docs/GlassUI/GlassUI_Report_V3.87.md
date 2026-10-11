# GlassUI 迭代报告 V3.87 | 2026-10-11（"我写的报告手机端完全看不到" —— 接上工作目录文件源）

> **触发**：用户「比如你现在每次都生成的报告 `GlassUI_Report_V3.86.md`，**手机端完全看不到**」。

---

## 1. 根因（一句话）

`ChatItem.FILES` 这类"交付物卡"**只有一个来源** —— `deliverables/presented` 事件
（`MainActivity` 的 case 分支）。而 **Agent 用 `write` / `edit` 工具写出来的文件根本不会触发它** ✗
⇒ 所以我每轮写的 `docs/GlassUI/GlassUI_Report_Vx.x.md` 在手机上：
**对话里没有卡、`生成物` 面板里也没有行** ⇒ 用户"完全看不到" ✓。

顺带说明：这也解释了上一轮「看不到生成物」——**不是面板坏了，是它只有那一个数据源**。

## 2. 修：接上网关的 **`file-list`** 源 ✅

工程里**早就有**这条协议能力，而且**注释明确写着"当前 App 里没有调用点"**：

> `file-list` 回帧：`{kind:"file-list", sessionId, path, entries:[{name,path,kind,bytes?,modifiedAt?,mediaType?}]}`；
> 请求用 `requestFileList(sessionId, path, requestId)` …… **⚠️ 当前 App 里没有调用点（2026-10-06）**
> ——`GatewayClient.java:64-78`

**本轮把它接上了**：

| 位置 | 改动 |
|---|---|
| `glassArtifacts()`（打开「生成物」面板） | 先 `gw.requestFileList(currentSessionId, "", "artifacts-…")`，并记下打开时刻 |
| `onFileList(frame)`（新增 override） | 解析 `entries` ⇒ 转成 `ArtifactEntry`（`path` = `cwd/相对路径`，与现有下载链路一致）⇒ **按 `modifiedAt` 倒序**、上限 40 条 ⇒ 存 `workspaceFiles.get(sessionId)` |
| 异步回帧 | 若面板**还在 15 秒窗口内开着** ⇒ `runOnUiThread(this::onArtifacts)` **重画一遍**，把目录文件补进去（否则用户会看到"空"然后不再刷新） |
| `collectProducedFiles()` | 末尾**补上工作目录文件**（按 `ArtifactEntry.key()` 去重，已 present 的优先保留） |

**效果**：点开「生成物」⇒ 先出已交付的文件，随后**补上工作目录里的文件（最近改动排最前）**
⇒ 刚写出来的 `GlassUI_Report_V3.87.md` 就在最上面 ✓；点它 = 打开预览（V3.85 已修好相对路径回退），
长按 = 下载到手机 / 复制路径。

## 3. 项目明细

| 条目 | 状态 | 证据 | 未成功的原因 | 下一步 |
|---|---|---|---|---|
| 定位"我看不到自己写的报告" | ✅ 根因明确 | `ChatItem.FILES` 只来自 `deliverables/presented`；`write/edit` 不触发 | — | — |
| 接上 `file-list`（工作目录文件） | ✅ 已实现+装机 | `file-list` 请求 + `onFileList` + 合并 | — | 点「生成物」应出现**目录里的文件**（最近改动在前） |
| 异步回帧后面板自动刷新 | ✅ 已实现 | 15 秒窗口内 `onArtifacts()` 重画 | — | — |
| **画面验收** | ⏳ 未验证 | 构建通过；真机+模拟器已装（sha `46d638b9…`） | 需要真实会话（有工作目录）才能看到 `file-list` 回帧 | 你点开「生成物」看一眼；若仍为空，我下一轮把 `file-list` 的回帧打到 App 内日志里定位 |

> 备注：`file-list` 按协议列的是**整个会话工作目录**（不只"产出的"）。本轮先把它按**修改时间倒序**排，
> 让"刚写出来的"排最前；如果你确认了，下一轮可以按扩展名/MIME 过滤（只留文档与代码），避免刷屏太多。
