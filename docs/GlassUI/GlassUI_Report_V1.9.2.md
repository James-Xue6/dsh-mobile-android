# GlassUI 迭代报告 V1.9.2 | 2026-10-08

> 触发：用户报 ——「你这截图挺好，**为啥我一动模拟器就变了？**」
> 上轮：`GlassUI_Report_V1.9.1.md`（白棱 0.35 定档）
> 验证口径：模拟器 `emulator-5554`（真机 ⏳ 仍未接入）

---

## 1. 结论：不是"变了"，是**系统水波纹在方角节点上画出的灰方块**

静止帧好看（= 我给的截图），**一按下**就在玻璃件上冒出方块：

| 取证件（`evidence/v190/`） | 现象 |
|---|---|
| `91_gear_pressed.png` | 按住右上角**玻璃圆齿轮** → 圆外出现一块**灰色方角块** |
| `92_addcard_pressed.png` | 按住「＋ 添加设备」**玻璃胶囊** → 胶囊内部出现**深灰方角矩形**（棱角比玻璃还方） |
| `104_addcard_abc.png` | 上=静止 / 中=修复前按下 / 下=修复后按下（三格并排，定案证据） |

**根因（架构层面，不是调参能解决的）**
`Modifier.clickable` 默认带 Material `indication`（水波纹），它是画在**节点的矩形边界**上的；
而玻璃的圆角是 `drawBackdrop(shape = …)` 在**绘制期**画的形状 —— **布局期节点仍是方角**。
两者不一致 ⇒ 按下时 ripple 露出一块方角灰矩形。

**对照正式版**：`DeviceHubView` 全程用 `Ui.tap(g, 0.98f)`（按下缩到 0.98 的缩放反馈），
`gearGlass` 只挂 `setOnClickListener` —— **正式版根本没有水波纹**。
所以这是我这边引入的偏差（V1.9 首写设备页时直接用了 Compose 默认 `clickable`），不是原设计。

---

## 2. 量化差异清单

| 项 | 基线（V1.9.1） | 目标 | 分类 |
|---|---|---|---|
| 玻璃件按压反馈 | Compose 默认 ripple（画在方角矩形上） | 无 ripple + 按下缩放 0.98（= 正式版 `Ui.tap` 口径） | **交互/反馈**（非质感数值） |

---

## 3. 最小 diff

**新增** `src/com/dsh/mobile/glass/GlassAmbient.kt`：`Modifier.glassTap(scale = 0.98f, onClick)`
```kotlin
val src = remember { MutableInteractionSource() }
val pressed by src.collectIsPressedAsState()
return this
    .graphicsLayer { val s = if (pressed) scale else 1f; scaleX = s; scaleY = s }
    .clickable(interactionSource = src, indication = null, onClick = onClick)   // ← indication = null 关掉水波纹
```

**替换** `src/com/dsh/mobile/glass/GlassDevicePageActivity.kt` 里 3 处裸 `clickable`：

| 位置 | 改法 | 说明 |
|---|---|---|
| 齿轮（36dp 玻璃圆） | `Modifier.size(36.dp).glassTap { … }` | 反馈挂在**玻璃圆本体**上 |
| 「＋ 添加设备」卡 | 从内层 `Row.clickable` 移到 **`GlassSurface` 整体**：`.fillMaxWidth().glassTap { showAdd = true }` | 与正式版 `Ui.tap(card, 0.98f)` 一致（整卡缩放，不是只缩内层） |
| 三按钮（进入对话/修改名称/删除） | 从内层渐变 `Box.clickable` 移到**整只 `RefractionCapsule`** | 否则按下只缩那块渐变、玻璃胶囊不动，看着像"内容掉进去" |

其余质感参数（Highlight 0.35 / blur 6dp / refraction 14·28dp / tint 20%）**一行未动**（本轮不占质感额度）。

---

## 4. 验证取证（按压态，用 `input motionevent DOWN` 保持按住再截屏）

| 区域 | 按下时变化像素（通道差 >40）修复前 | 修复后 | 整屏 mean\|Δ\| 修复后 |
|---|---|---|---|
| 添加设备卡 | **59826** / 181800（mean 54.9） | 4692 / 181800（mean 6.1） | **0.59** |
| 右上角齿轮 | 明显灰方块 | 319 / 30600（mean 1.7） | 0.02 |
| 重连按钮 | — | 1011 / 137200（mean 1.4） | 0.11 |

> 修复后残留的变化就是 **0.98 缩放**（2% 收缩使轮廓位移 1~2px），正是正式版那套反馈，不是灰块。
> 证据：`100_rest_fixed.png`、`101_gear_fixed.png`、`102_addcard_fixed.png`、`103_button_fixed.png`、
> `104_addcard_abc.png`、`105_gear_abc.png`。

---

## 5. 写进规范/交接的一条硬规则（建议）

> **本工程 Compose 侧的玻璃件，一律用 `Modifier.glassTap { }`，禁止裸 `Modifier.clickable`。**
> 原因：玻璃圆角是绘制期形状、节点是方角，任何"按节点边界绘制"的 indication 都会露出方角灰块。

（我**没有擅自改** `docs/GlassUI/GlassUI_SPEC_折射观察窗.md` 正文，先放交接总表；要回写规范请点头。）

---

## 6. 仍未完成（与上轮一致）

在线判据 / 扫码配对 / 正式版会话页 / 对话页数据 / 抽屉 ⏳ 未接管；设备页是否挂进 MainActivity 宿主 🔲 待拍板；
背景三选一 🔲 待拍板；**真机 ⏳ 未验证**；图标砖 tint 与图标尺寸差异仍挂账。
