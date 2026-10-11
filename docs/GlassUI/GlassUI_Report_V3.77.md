# GlassUI 迭代报告 V3.77 | 2026-10-11（发的图片不显示 + 图片裁剪页【方案 A】上线）

> **触发**：用户「新 bug 2（图片裁剪界面）…**这个我选 a**，然后**我发送的图片在我发送的聊天栏没显示**」。

---

## 1. "我发送的图片在聊天栏没显示" ✅（根因 + 修复）

| 事实 | 证据 |
|---|---|
| 宿主**早就把图片解码好**了：`MainActivity.onAttachment` → `BitmapFactory.decodeByteArray` → `scaleDown(bmp,1080)` → `ChatItem.images.add(small)` → `convo.setItems()` + `refresh()` | `MainActivity.java:6166-6186` |
| `ChatItem.images` 是 `List<Bitmap>`，状态一直在推 | `ChatItem.java:80` |
| **但 Compose 侧从来没渲染它**：`ChatItem.USER -> UserBubble(item.text)`（只传文字） | `GlassChatScreen.kt:858`（改前） |

**修**：新增 `ItemImages(list)`（最多 4 张、靠右、圆角、限高 320dp），挂在**用户消息**和**助手消息**上方。

## 2. 图片裁剪页（**方案 A：自由裁剪**）✅ 已实现

新增 `src/com/dsh/mobile/glass/CropActivity.kt`（自绘，不依赖系统裁剪）：

- 原图居中显示，**裁剪框可整体拖动**，**四角手柄可拉**（各 40dp 命中区）；
- 顶栏：`取消` / `拖框体 / 拉四角` / `完成`；
- 确认后按框裁剪并**保持原分辨率**，写成 `cacheDir/crop-*.jpg`，用 `file://` 回给宿主；
- **坐标口径**（写错就全歪）：框的四元组一律是"图片显示区像素"，回原图只除以同一个 `scale = src.width / dw`。

**接线**（`MainActivity`）：
- 新增 `REQ_CROP = 1012` + `startCrop(uri)`；
- `REQ_IMAGE`（相册选图）与 `REQ_CAMERA`（拍照成功）⇒ **先进裁剪页**；
- `REQ_CROP` 返回 ⇒ `stageAttachment(out, KIND_IMAGE)`（仍走"暂存 → 与文字一起发送"）；
- 裁剪页起不来（异常）⇒ **退回原行为**直接暂存原图（增强项不该拖垮主流程）。
- `AndroidManifest.xml` 注册 `CropActivity`（`exported="false"`，内部页）。

## 3. 取证（模拟器 · 与真机同一份代码）

| 项 | 结果 |
|---|---|
| 构建 + 装机（模拟器 / 真机） | ✅ `sha256 = a59b38d4…` |
| 裁剪页能被拉起 | ✅ logcat 显示 `CropActivity` 打开（splash → CLOSE） |
| **裁剪页 UI 截图** | ❌ **没拿到** —— 我用的测试 uri 是 `file:///sdcard/croptest.png`，App **没有存储读权限**（设计如此）⇒ 走了我写的"读不到这张图片"分支退出（**说明该分支在跑**，但也说明我没看到界面） |
| 图片在气泡里渲染 | ⏳ **未验证** —— 演示数据里没有图片 |

## 4. 项目明细（对照本轮要求逐条）

| 条目 | 状态 | 证据 | 未成功的原因 | 解决办法 / 下一步 |
|---|---|---|---|---|
| 发的图片在聊天栏显示 | ✅ 已实现＋装机 | `ItemImages` + `ChatRow` 改动 | — | 你发一张图即可确认 |
| 图片裁剪页（方案 A 自由裁剪） | ✅ 已实现＋装机 | `CropActivity.kt`、`REQ_CROP` 接线、manifest | — | 你点 ＋→相册→选图，应直接进裁剪页 |
| 裁剪页 UI 视觉验收 | ⏳ 未验证 | logcat 有启动记录 | 测试 uri 无读权限，拿不到界面截图 | 真机走真实选择器（`content://` 有授权）即可；我下轮也可用"导出验证版"再截一次 |
| 图片气泡渲染验收 | ⏳ 未验证 | 代码已接 | 演示数据无图片 | 你发一张图；或我下轮给切片加一张程序生成的演示图 |
