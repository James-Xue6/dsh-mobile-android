# GlassUI 迭代报告 V3.21 | 2026-10-09

> **触发**：用户「读 `GlassUI_换会话交接_20261009`，然后重新修改」→「§6 按顺序一条一条来，先在模拟器上测试，我定好再实施」
> → 选定「添加设备弹窗框大」= **指玻璃菜单面板**；追加指令：**「左右也缩小啊」**。
> **A 类质感数值改动 = 0**（`RefractionWindow` 的 7 个质感 Token 一行未动）；只改**布局**，且收在**一个系数**上。

---

## 0. ⚠️ 先更正上一版报告的结论（重要）

本报告初版曾写「面板是内容自适应的，286.9dp，底下没有空腔」。**那是错的** —— 我量到的是**内容块**的上下边界，
不是面板边界。用**面板内/外取点比对**才查明真相：

> **玻璃面板一直是"整屏高"的**（`RefractionWindow` 内部 `Box(fillMaxSize())` 在高度约束**有界**时撑满全屏），
> 内容只是被 `contentAlignment = Center` **垂直居中**。通栏时看不出来；**一加左右内缩就暴露成一条整屏高的玻璃柱**。

**这才是用户说"框大"的真正根因**，比"缩 padding"重要得多。本报告以下内容均为更正后的实测。

## 1. 根因与修法（`GlassSheet.kt`，调用处，不动组件内部）

| 现象 | 根因 | 修法 |
|---|---|---|
| 面板"整屏高" | `RefractionWindow` 内层 `Box(fillMaxSize())`：父级高度约束有界 ⇒ 撑满 | 调用处加 **`.wrapContentHeight(unbounded = true)`** ⇒ 子级高度无界 ⇒ `fillMaxSize()` 对高度失效 ⇒ 回到内容高度 |
| 左右不收窄 | 面板原本 `fillMaxWidth()`（通栏，V3.15 的成果） | 外层 Column **用 `padding(horizontal = maxWidth ×(1−K)/2)`** 内缩 |
| 高度不降 | **M3 默认行高固定 24sp，只改 `fontSize` 不改行高**（15.5sp 与 11.5sp 实测都是 24dp 行） | 行高 `24 × K`；字号**不动**（`Ui.S_*` 一行未改） |

### 1.1 两个**不能**用的写法（都实测踩了）
- ❌ **`Modifier.fillMaxWidth(0.8f)`**：把宽度约束改成有限值后，内层 `fillMaxSize()` 同样撑满 ⇒ **面板变整屏高、内容垂直居中**（`evidence/v3250/18_F_sheet.png` 就是这个故障现场）。
- ❌ **`Modifier.height(IntrinsicSize.Min)`**：`BoxWithConstraints` 是 `SubcomposeLayout`，查询内在尺寸直接抛
  `IllegalStateException: Asking for intrinsic measurements of SubcomposeLayout layouts is not supported` ⇒ **进程崩溃**（logcat 已取证）。

### 1.2 唯一旋钮
`private const val SheetK = 0.8f`（`GlassSheet.kt`）：同时管**内部尺寸**、**行高**、**左右内缩比例**。
`SheetScaleType`（默认 false）控制"字号是否也按 K 缩"。
按钮触控下限 `SheetMinButton = 40dp`（取消/确认不随比例跌破触控目标）。

## 2. 实测对比（模拟器 `emulator-5554`，1080×2400 @420dpi = 411×914dp）

| 版本 | 宽度 | 高度 | 说明 |
|---|---|---|---|
| 基线 V3.18 | **411dp（通栏）** | **整屏高（玻璃面撑满）**，内容块 753px | 用户："框有点大" |
| 只缩布局（K=0.8） | 411dp | 撑满，内容块 254.9dp | ✗ 高度没真降 |
| C：布局+行高 | 411dp 通栏 | 撑满，内容块 241.1dp | 仍是"整屏玻璃柱"，只是内容更紧 |
| E：`fillMaxWidth(0.8f)` | 329dp | ✗ 撑满 | 故障现场，已废弃 |
| **F：padding 内缩** | 329dp | ✗ 撑满 | 宽度对了、高度还错 |
| **H（最终）** | **329dp（左右各 41dp）** | **241.1dp（内容高度，贴底居中）** | ✅ **真浮动卡** |

- **侧边阴影复检**：`y=2100` 处卡片外 `x=60→100` 亮度 114→111（仅差 3 级）；卡片边缘 `x=108` 处 111→208。
  ⇒ **V3.15 的"两侧阴影冒充边框"没有复发**，不需要给 `RefractionWindow` 加 `shadow` 开关。
- **崩溃复检**：`IntrinsicSize` 那版崩溃后已修，最终版 `topResumedActivity=com.dsh.mobile/.MainActivity`（未崩）。

## 3. 取证件

| 文件 | 内容 |
|---|---|
| `evidence/v3250/04·05` | 基线（V3.18）开/关对拍 |
| `evidence/v3250/10·11` | C 档（只缩内部） |
| `evidence/v3250/18_F_sheet.png` | **❌ 故障现场**：`fillMaxWidth(0.8f)` → 整屏高的玻璃柱 |
| `evidence/v3250/21_H_closed.png` / `22_H_sheet.png` | **✅ 最终态**（329×241dp 浮动卡） |
| `evidence/v3250/16_E_sheet.png` | 同故障（E 档） |
| `evidence/v3250/03_manual_add.png` | 旧 Java「手动添加设备」对话框（§6 第 1 条后半的目标） |

## 4. 回退
- 尺寸：**只改 `SheetK`**（1.0f = 完全回 V3.18）。
- 若要去掉"整屏高"的修复：删 `wrapContentHeight(unbounded = true)`（**不建议**，那会把面板打回整屏高）。

## 5. 遗留 / 待定
1. **K=0.8 是否合适**（当前 329×241dp）—— 等用户看图定档。
2. **底部是否也留边距**：现在是贴底（`padding(bottom = 0.dp)`），圆角 24dp ⇒ 底部两角会露出页面；
   若要"悬浮卡"观感，可再加 `padding(bottom = SheetDp(...))`。
3. §6 第 1 条**后半**：`onManualAdd()` 换玻璃表单形态 `showForm(...)`（用户已定方向）。

## 6. 续：面板改**屏幕正中**（用户追加指令）

用户：「大小看着还行，但是**位置是不是应该在屏幕中间**」⇒ 一处改动：
`Modifier.align(Alignment.BottomCenter)` → **`Alignment.Center`**（并去掉 `padding(bottom = 0.dp)`）。

- 实测：卡片 y 883→1516，**卡片中心 1199 ≈ 屏中心 1200** ✓；宽度仍 329dp（x 108→972）。
- 取证：`evidence/v3250/25_center_sheet.png`。

## 7. 续：§6 第 1 条**后半**——「手动添加」换**玻璃表单形态**（已完成+已验）

### 7.1 做法
1. `GlassSheet.kt` 新增**第三种形态**（与菜单/确认三选一）：
   - `class GlassSheetField(label, value, hint, password)`；
   - `GlassSheetState.showForm(tag, title, message, fields, submitLabel, onSubmit)`；
   - `onSubmit: (List<String>) -> String?` —— **返回 null = 成功并自动收起；返回文案 = 内联红字报错且保持打开**；
   - 渲染：字段名（小字）+ `BasicTextField` + 占位提示；令牌类字段用 `PasswordVisualTransformation` 掩码；
     主按钮用品牌色（与确认形态同一套）——全部复用同一个 `SheetK` 尺寸与行高系数。
2. `MainActivity`：新增 `glassManualAddForm()`，`sheetPick()` 里「手动添加」改走它；
   **写库/连接完全复用既有 `addManualDevice(l, w, t, alias)`**，校验文案沿用原来那三条
   （地址二选一 / 令牌必填 / `GatewayClient.cleartextProblem` 明文拦截）。
   旧 `showManualAddDialog()` **保留**：`USE_GLASS_SHEET=false` 或状态缺失时自动回退。

### 7.2 实测（模拟器 `emulator-5554`，全部截图在 `evidence/v3250/`）
| 检查项 | 结果 |
|---|---|
| 表单渲染 | ✅ 玻璃卡居中、4 段字段卡 + 「保存并连接」+「取消」（`26_manual_form.png`） |
| 空值提交校验 | ✅ 内联红字「请先填内网地址或公网地址——两个都空就连不上电脑。」且**弹窗保持打开**（`27_form_error.png`） |
| 字段聚焦 + 键盘 | ✅ 光标进入字段、IME 弹出（`29_field_tap.png`） |
| **键盘不挡按钮** | ✅ 给 overlay 加 **`.imePadding()`** 后卡片自动上移，「保存并连接/取消」完整可见（`28` 未加时按钮**正好被键盘压住** → `30_form_ime.png` 修复后） |
| 令牌掩码 | ✅ 输入 `testtoken123` 显示为 `••••••••••••`（`35_step_filled2.xml`） |
| **提交真写入** | ✅ 点保存 → 弹窗收起、**建出设备卡**「我的电脑 / 离线 / 地址：example.com / 线路：走公网」+「重新连接 / 重新扫码配对」（`36_after_submit.png`） |
| 崩溃 | ✅ `logcat -b crash` 0 条 |

> ⚠️ 遗留：模拟器上因此多了一台测试设备（example.com），下一轮截图前可清掉。

## 8. 补丁（同日）：点**卡片内空白**会误关弹窗

**用户报**：「为什么点击上面的空隙会消失，不应该点卡片外或者点取消才消失吗？」

**复现**：面板开着时点 `(540,920)` = 面板 y883–1516 之内的空白处（标题右侧）→ **弹窗消失**（`evidence/v3250/42_tap_inside_panel.png`）。

**根因**：Compose 里「有背景但没有点击节点」的区域**不消费触摸事件**，于是卡内空白处的点击
**穿透到背后的遮罩**，被遮罩的 `glassTap` 当成"点卡片外" ⇒ 关弹窗。**面板自身从没消费过触摸。**

**修法**（`GlassSheetOverlay` 的面板 Column，一处）：
```kotlin
.pointerInput(Unit) { detectTapGestures { /* 吞掉：卡内空白不关闭弹窗 */ } }
```
用 `pointerInput` 而非 `clickable`：只消费事件，不引入点击语义 / 涟漪 / 按压动画。

**实测（`evidence/v3250/43~49`）**

| 行为 | 修前 | 修后 |
|---|---|---|
| 点卡内空白（标题上方 / 行与行之间） | ❌ 关闭 | ✅ **不关闭** |
| 点卡片外遮罩 `(540,500)` | 关闭 | ✅ 关闭（保留） |
| 点「取消」 | 关闭 | ✅ 关闭 |
| 点「扫码添加」行 | 响应 | ✅ 响应（打开了扫码页） |
| **按钮回归**：表单「保存并连接」 | 响应 | ✅ 响应（空值→内联报错、表单保持打开，`52~54`） |
| **确认形态回归**：删除设备的确认按钮 | 响应 | ✅ 响应（测试设备已删掉、页面回到"还没有设备"） |
| 崩溃 | — | ✅ 0 条 |

> 若用户希望**连"点卡片外"也不关**（只能按取消），那是另一种产品选择：把遮罩的 `glassTap` 去掉即可 —— **待确认**。

## 9. 补丁（同日）：按压反馈出现**双影**（"灰色缩小的一块 + 原本的还在下面"）

**用户报**：「扫码添加 / 手动添加 / 取消 这几个按钮，点击之后灰色缩小，原本的还在下面」，
「进入手动添加里面的按钮也是这样」。

**取证**（用调试钩子强制按下态，`GlassDebug.forcePressed`）：`evidence/v3250/60_pressed_before.png`
—— 每颗按钮都能看到**两个矩形**：外圈原尺寸的浅色胶囊 + 内圈缩小变暗的一块。

**定量**（y=1090 横向剖面，亮度三层）：
```
修前： 183(面板底) → 213(原尺寸胶囊) → 187(缩小内层)      ← 三层 = 双影
修后： 183(面板底) → 187(缩小后胶囊)                      ← 两层 = 单颗
```

**根因**：Compose 修饰符是**外侧先画**。`glassTap` 的实现是
`Modifier.graphicsLayer{scale}.drawWithContent{ 内容之上再刷一层黑 }`，它只包住**它右侧**的部分。
而本文件的写法是 `.clip().background().glassTap()` —— `background` 在 glassTap **左边**，
于是：背景按**原尺寸**画（不进缩放层）→ 缩放+压暗的只是内容层 → 视觉上就是双影。

**修法**：把 `.glassTap(...)` 挪到 `.clip()/.background()` **之前**（本文件 4 处：菜单行 / 表单提交 / 确认按钮 / 取消按钮）。
遮罩那一处额外传 `scale = 1f, dim = 0f`：黑幕是"点卡片外关掉"的判定区，**不该有按压反馈**
（缩放会把整屏黑幕缩小、露出未压暗的四边；压暗层则会在点空白时再叠一层黑）。

**实测**
| 检查项 | 结果 |
|---|---|
| 强制按下态：菜单 sheet 三颗按钮 | ✅ 单颗（`61_pressed_after.png` + 上面那组剖面数字） |
| 强制按下态：表单「保存并连接 / 取消」 | ✅ 单颗（`62_pressed_form.png`） |
| 正常态回归：打开菜单 → 手动添加 → 表单 | ✅ 正常（`63~64_reg_*`） |
| 正常态回归：空值点「保存并连接」 | ✅ 仍内联报错、表单保持打开（`65_reg_error.xml`） |
| 崩溃 | ✅ 0 条 |

> ⚠️ **同类写法（`.background()` 在 `.glassTap()` 之前）还在这些文件里**，会出同样的双影，**本轮未动**：
> `GlassSessionList.kt:177-178`（会话卡）、`:230`/`:233`（会话菜单项）；
> `GlassChatScreen.kt:568-569`（对话页某卡）；`GlassSettings.kt:401`（设置页某项）。
> 修法相同（一行调序），但都属别的屏 ⇒ 单独一轮，逐屏截图验收。

## 10. 状态
| 项 | 状态 |
|---|---|
| §6 第 1 条·前半（缩小 + 左右缩 + 整屏高真根因修复 + 居中） | ✅ 已改+已验 |
| §6 第 1 条·后半（手动添加 → 玻璃表单形态） | ✅ 已改+已验（含真实写入） |
| 其余 §6 待办（2–8 + 追加「浅底加阴影」） | ⏳ 未动 |
| A 类质感数值改动 | **0** |
| 真机验收 | ⏳ 需用户插真机（SOP：真机截图才是唯一验收标准） |
