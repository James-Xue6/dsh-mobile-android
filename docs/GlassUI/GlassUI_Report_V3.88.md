# GlassUI 迭代报告 V3.88 | 2026-10-11（图片点不开 → 全屏看图：可放大）

> **触发**：用户「还有**生成的图片还有上传的图片，点不开**，最好能**点开放大**看，看这清楚点」。

---

## 1. 现状（为什么点不开）

`ItemImages`（V3.77 加的消息图片渲染）只是把 `ChatItem.images` 里的图**画出来**，
**没有任何点击处理** ⇒ 点了没反应 ✗（上传的图走 `onAttachment` 解码 ✓，生成的图同样落在 `ChatItem.images` ✓，
所以两者是同一个缺口）。

## 2. 修：**全屏看图**（点一下打开、双指放大、点一下关闭）✅

| 件 | 做法 |
|---|---|
| 状态 | 模块级 `GlassImageViewer.zoom: Bitmap?`（任何缩略图点一下塞进来，页面最上层渲染） |
| 缩略图 | `ItemImages` 每张加 `.glassTap(corner = 14.dp) { GlassImageViewer.zoom = b }` |
| 覆盖层 | `ZoomOverlay`：黑底 95% + `ContentScale.Fit` + **双指缩放 1~8×** + 放大后可拖动 + **点一下关闭**；底部一行提示「点一下关闭 · 双指可放大」 |
| 位置 | 聊天页**②悬浮层的最里层**（在顶栏之后、弹窗骨架之前）⇒ 盖住所有内容 |
| 为什么不用 `Dialog` | 本工程在纯 Activity 骨架里用 Compose `Dialog` 出过 `ViewTreeLifecycleOwner` 崩溃（历史事故）⇒ 覆盖层最稳 |

缩放/平移用 `graphicsLayer`（不动布局）⇒ 不触发重组风暴，也不影响玻璃取样层的关系。

## 3. 项目明细

| 条目 | 状态 | 证据 | 未成功的原因 | 下一步 |
|---|---|---|---|---|
| 消息里的图（上传+生成）点开全屏 | ✅ 已实现+装机 | `GlassImageViewer` + `ZoomOverlay` + `ItemImages` 点击 | — | 点消息里任意一张图应全屏；双指可放大 |
| 全屏后能放大看清楚 | ✅ 已实现 | `detectTransformGestures` 1~8× + 拖动 | — | — |
| 关闭 | ✅ 已实现 | 点一下关闭（底部有提示） | — | — |
| **画面验收** | ⏳ 未验证 | 构建/装机通过（sha `c53be53e…`） | 演示切片里没有图片数据；需你在真实会话点一张图 | 你点一下就知道 |
| 「生成物」面板里的**图片文件**放大 | ⏳ 未做 | 那条路走 `ArtifactActivity`（WebView 渲染） | 本轮只做了**消息里的图** | 若你要"生成物面板里的图也能放大"，我下一轮给 `ArtifactActivity` 的图片分支也加缩放 |

> 备注：`ArtifactActivity` 目前对 `image/html` 一律用 WebView 渲染 —— 能看、但**不能双指放大**。
> 这就是上面最后一行"下一轮"要做的事（改成图片分支用 Compose 的 `ZoomOverlay` 同款）。
