# GlassUI 迭代报告 V1.9 | 2026-10-08

> 路线：**A（Compose + 官方 `io.github.kyant0:backdrop:2.0.1`）**，不改。
> 本轮触发：用户四条报障 ——「已经上模拟器的界面丢失了很多数据，一些按钮没有了 / 设备卡片圆角过小 /
> 磨砂过大，看不到玻璃变形和色散了 / 背景换回正式版 app 背景」。
> 验证口径：**模拟器（emulator-5554）**——用户本轮明确要求「先在模拟器上测试验证」。
> 真机 `A7QK023224004563` 全程未接入 `adb devices`（只有 emulator-5554），故本轮结论标注为
> **「模拟器已验证 / 真机 ⏳ 未验证」**，这与工程铁律「真机截图唯一验收」冲突，冲突点见 §6。

---

## 1. 基线（上一轮 V1.8 的取值，改动前）

| 项 | 基线值 | 来源 |
|---|---|---|
| `RefractionWindowTokens.BlurRadius` | **12.dp** | V1.0 锁定 |
| `RefractionWindowTokens.RefractionAmount` | **22.dp** | V1.0 锁定 |
| `RefractionWindowTokens.RefractionHeight` | 14.dp | 不动 |
| `Tint` | White 20% | 不动 |
| 环境背景 | Compose 页铺 `R.drawable.aurora_bg` **照片**；MainActivity 在 `onCreate` 注入同一张图当 `Ui.ambientImage` | 10-07 临时物 |
| 设备卡圆角 | **18.dp** | 骨架期照抄 `DeviceHubView.glassCard()` |
| 设备页内容 | 硬编码演示：一行副标题 + 一张假设备卡 + 一个胶囊 | V1.7 骨架 |

---

## 2. 量化差异清单（问题 / 当前值 / 目标值 / A·B·C 分类）

| # | 用户原话 | 当前值 | 目标值 | 分类 | 处置 |
|---|---|---|---|---|---|
| 1 | 界面丢失了很多数据，一些按钮没有了 | 设备页只有 1 张写死的假卡 + 1 个胶囊；**缺**大标题/齿轮/副标题/状态行/空态/页脚/线路行/「当前」徽标/三按钮文案逻辑 | 与正式版 `DeviceHubView` 元素一一对应 | **功能缺口**（非质感） | 补齐 + 接真实 `Store` 数据（§3-④） |
| 2 | 设备卡片圆角过小 | `radius = 18.dp` | **28.dp**（= 正式版 `Ui.R_CARD`，本 App 标准卡圆角档） | **B 布局** | 只改调用处入参（§3-②） |
| 3 | 磨砂过大，看不到玻璃变形和色散 | `blur 12dp`（≈ 折射幅度的一半，形变与彩边被糊平） | **6dp** | **A 质感** | §3-① |
| 3b | 同上（形变强度不足） | `refractionAmount 22dp` | **28dp**（环带:幅度 = 1:2.0） | **A 质感** | §3-①（与 3 合计 **正好 2 个质感数值**，符合 SOP 上限） |
| 4 | 背景换回正式版 app 背景 | 星空极光**照片**（`aurora_bg`） | 正式版 `Ui.AmbientDrawable`：配色档三色纵向渐变 + 四枚径向光斑 | **C 环境 + 装配** | §3-③ |

### 2.1 「正式版背景」的判定依据（这条最容易搞错，先摆证据）

用户说「换回正式版背景」，必须先把「正式版」钉死。取证四条，互相印证：

1. **归档正式包没有这张图**：`dist/archive/dsh-mobile-首页-毛玻璃46-无投影-按钮等高.apk`（2026-10-06）
   `tar -tf` 共 **31 个条目，无 `res/drawable-nodpi*/aurora_bg`**；
   当前 dev 包 `dist/dsh-mobile.apk`（2026-10-08）**36 个条目、含 `aurora_bg.jpg`**。
2. **极光图不在版本库里**：`git status` 显示 `?? res/drawable-nodpi/`（未跟踪）；
   `git show HEAD:src/com/dsh/mobile/MainActivity.java` **没有** `setAmbientImage` / aurora 字样。
   → 极光是 10-07 玻璃改造引入的**未提交临时物**。
3. **代码里留着用户上一次的同款要求**：`src/com/dsh/mobile/ui/DeviceHubView.java:237-241`
   「[2026-10-07 用户定版 · **背景换回彩色配色**] 这里原来临时铺了一张硬编码的「星空极光」位图
   （…注释写着"[临时看液态玻璃]"），它把 MainActivity 根容器上那套**彩色环境背景**
   （Ui.AmbientDrawable + 设置页「配色」6 档）整片盖住了 —— 于是设置里换配色在本页毫无反应…
   按用户要求删掉，恢复由环境背景驱动的那套炫彩底。」
4. **模拟器像素对照（排除法）**：`evidence/v190/00_current.png`（Compose 设备页，旧）
   vs `02_official_after_grant.png`（正式版设备页）逐列比较：
   `mean|Δ| = 0.2 / max|Δ| = 1.1`，最佳纵向偏移 **0px** → 两者背景**同一张极光图、同一裁切**。
   既然「已上模拟器的界面」与模拟器上的正式版背景一模一样，用户说的「正式版」就**不可能是**这个
   dev 包，只能是手机上那个正式包（= 上面第 1 条的归档包）。

**结论**：正式版背景 = `Ui.AmbientDrawable`（配色档渐变 + 光斑），极光照片是偏离项 → 删。

---

## 3. 最小 diff（本轮全部改动，共 5 个文件）

### ① A 类质感（2 个数值，SOP 上限）
`src/com/dsh/mobile/glass/RefractionWindow.kt`
```diff
-    val BlurRadius: Dp = 12.dp
+    val BlurRadius: Dp = 6.dp
-    val RefractionAmount: Dp = 22.dp
+    val RefractionAmount: Dp = 28.dp
```
组件本体（`Frost.java` / `FrostedPanelView.java` / `RefractionWindow` 内部 blur→vibrancy→lens 顺序与
`drawBackdrop` 装配）**一行未动**。

### ② B 类布局
`src/com/dsh/mobile/glass/GlassDevicePageActivity.kt`
```diff
+/** 设备卡圆角（B 类布局参数）：正式版标准卡圆角 28dp，不再是骨架期的 18dp。 */
+private val CardRadius = Ui.R_CARD.dp
...
-            radius = 18.dp,
+            radius = CardRadius,
```

### ③ C 类环境背景（背景回归正式版）
`src/com/dsh/mobile/MainActivity.java` —— 删除极光底图注入（**只删不加**）
```diff
-        if (!com.dsh.mobile.ui.Ui.hasAmbientImage()) {
-            try {
-                com.dsh.mobile.ui.Ui.setAmbientImage(android.graphics.BitmapFactory.decodeResource(
-                        getResources(), com.dsh.mobile.R.drawable.aurora_bg));
-            } catch (Throwable ignored) { }
-        }
```
`res/drawable-nodpi/aurora_bg.jpg` **保留**（`proto/GlassHomeActivity`、`GlassCompareActivity` 仍引用）；
一行即可恢复（`Ui.setAmbientImage(decodeResource(R.drawable.aurora_bg))`），退路完整。

新增 `src/com/dsh/mobile/glass/GlassAmbient.kt`：
- `AmbientBackground()`：挂**官方 `Ui.AmbientDrawable`**（与 MainActivity 根容器同一份绘制代码），
  所以「配色」6 档、深浅色压暗都对得上；不用 Compose 画笔重画（重画必然与正式版漂移）。
- `GlassSurface()`：**高度随内容**的玻璃面 —— 调用处 `Box { 玻璃.matchParentSize(); 内容 }`。
  为什么需要它：`RefractionWindow` 内部是 `BoxWithConstraints + fillMaxSize()`，只在尺寸确定的
  容器里成立；wrap-content 高度会给它无界约束。**不去改组件本体的测量逻辑**（规范：组件本体锁定），
  改由调用处把高度交给内容决定 —— 等价于正式版 `FrostedPanelView.content()` 的语义。

### ④ 设备页内容补齐（用户第 1 条）
`src/com/dsh/mobile/glass/GlassDevicePageActivity.kt` 重写，元素逐条对齐
`ui/DeviceHubView.java`：

| 正式版元素 | 本轮 Compose 版 | 数据源 |
|---|---|---|
| 大标题「我的设备」`S_LARGE`/INK/Bold | ✅ | 常量 |
| 右上角玻璃齿轮（36dp 圆 + 图标 9dp） | ✅ `RefractionWindow(radius=18dp)` | 点击 → 正式版 MainActivity（设置页尚未接管） |
| 副标题「管理你的电脑 · 连接后进入对话」 | ✅ INK 88% | 常量 |
| 状态行 | ✅ 无设备「还没有设备 · 点下面的「＋ 添加设备」」/ 有设备「离线 · 还没连上」(WARN) | 按正式版 `refreshDevices()` 文案 |
| 空态（玻璃方砖 72dp/圆角22 + 两行文案） | ✅ | 常量 |
| 设备卡：图标砖 44dp/圆角14 + 设备名 + 在线/离线徽标（带圆点）+「当前」徽标 + 平台行 | ✅ | **`Store.devices()` / `displayName()` / `platform` / `activeDeviceId()` / `hasUsableLan()`** |
| 标签行 `内网 · 固定 / 公网 / DSH <版本>` | ✅ | `lanUrl` / `wanUrl` / `dshVersion` |
| 玻璃线路行 | ✅ `GlassSurface(radius=14dp)` | **`Store.pickOf(d)`** → `line`/`source`/`url` |
| 三按钮 进入对话|重连|连接 / 修改名称 / 删除（等宽、带色液态玻璃） | ✅ `RefractionCapsule` + 语义渐变（0x9A6BFF→0x3A8CFF / 白 / 红，alpha 160·180·170） | 按钮文案逻辑照抄正式版 `online ? 进入对话 : busy ? 重连中… : isActive ? 重连 : 连接` |
| 「＋ 添加设备」玻璃卡 + 卡下小字 | ✅ | 手动添加**真写库**（`Store.upsertDevice`，地址按 `LanAddress.isUsableLanUrl` 分内网/公网槽） |
| 页脚（无玻璃、白字带阴影） | ✅ 贴底 | 常量 |

**改名 / 删除 / 手动添加是真操作**（`Store.renameDevice` / `removeDevice` / `upsertDevice` + 重新读表），
不是演示态。截图 `21_devcard_v19.png` 就是**通过 UI 手动添加一台设备**后真实渲染出来的卡片。

### ⑤ 顺带修掉的版式缺陷
`src/com/dsh/mobile/glass/GlassChatPageActivity.kt`：玻璃顶栏/输入胶囊原来压在状态栏上
（旧截图里系统时间落在玻璃里），现把「内容 + 玻璃」整体放进 `systemBarsPadding()` 安全区，
背景层留在外面铺满整屏。背景同样换成 `AmbientBackground()`。

---

## 4. 验证取证（模拟器 emulator-5554，1080×2400）

| 文件（`evidence/v190/`） | 验证点 | 结果 |
|---|---|---|
| `10_devpage_v19.png` | 背景=配色渐变；标题/齿轮/副标题/状态行/空态/添加卡/页脚齐 | ✅ |
| `11_official_v19.png` | **正式版 MainActivity 设备页背景已回配色底**（不再是极光照片） | ✅ |
| `12_chat_v19.png` | 对话页背景=配色底、玻璃仍正常（顶栏取样可见） | ✅ |
| `13~19_*.png` | 添加设备对话框（真写库链路）逐屏 | ✅ |
| `20/21_devcard_v19.png` | **真实设备卡**：`DESKTOP-TEST` / 离线徽标 / 当前徽标 / 桌面端 / 内网 · 固定 / 「离线 · 走内网 · 自动 · 未连 WiFi（没有公网地址，暂用内网）」 / 重连·修改名称·删除 | ✅ |
| `30_devpage_final.png` | 最终态（页脚已贴底） | ✅ |
| `31_chat_final.png` | 对话页 insets 修好（系统时间不再落在玻璃里） | ✅ |
| `32_official_final.png` | 正式版页最终态 | ✅ |
| `33_evidence_tex.png` | **质感 A/B 取证**：高频网格底上，折射观察窗左边缘的网格线被**明显推开/弯折**，玻璃内网格仍可辨（blur 6dp 未糊死） | ✅ |
| `zoom_v19_refr_edge.png` | 上者 2× 放大：角部**亮弧（Highlight.Default）+ 网格形变**清楚可见 | ✅ |
| `zoom_v19_devcard_TL.png` | 设备卡左上角 2× 放大：**大圆角 + 白色受光棱 + 玻璃体**（圆角明显大于旧 18dp） | ✅ |
| `zoom_v19_addcard.png` | 添加设备卡 2× 放大：大圆角 + 边缘受光 | ✅ |
| 文件体积旁证 | 极光底截屏 **2.7~3.1MB**，配色底截屏 **0.45~0.51MB**（低频底） | ✅ |

---

## 5. ⚠️ C 类结论：为什么「背景回正式版」会让形变/色散**更**难看见

这是本轮必须讲清的一处**内在冲突**，不是没调好：

- 官方 `lens()` 的色散**没有独立强度参数**（`javap` 实证：
  `lens(scope, float refractionHeight, float refractionAmount, boolean depthEffect, boolean chromaticAberration)`
  —— `chromaticAberration` 只有开/关），RGB 分离幅度**绑在折射几何量上**；
- 折射的效果强弱，取决于背景**有没有可被推开的高频细节**。极光照片有星点/山脊/水面纹理 →
  形变一目了然；正式版配色底是**平滑三色渐变 + 四枚柔边光斑** → 玻璃把它们推开也几乎看不出来
  （`GlassUI_SPEC_折射观察窗.md` 早写过这条：纯渐变底上，磨砂与折射都看不出来）。
- 本轮已把 blur 12→6dp 让形变**在取证页（高频网格底）**明确可见（`33`、`zoom_v19_refr_edge.png`）；
  但在正式版配色底上，观感仍然偏"干净"。

**给用户的三个选项（请挑一个，我不擅自决定）**
1. **保持现状**：正式版配色底 + blur 6dp（形变在细节底上可见，在配色底上含蓄）；
2. **极光照片回来**：一行恢复 `Ui.setAmbientImage(...)`（`Dist` 增加 455KB，设置里「配色」6 档除极光档外不受影响）；
3. **折中**：给环境底叠加**极低对比度噪点/星点纹理**（不改 `AmbientDrawable` 的配色语义，只为折射提供可弯曲的细节）。

---

## 6. 未完成 / 风险 / 下一步（诚实清单，别当成已完成）

| # | 项 | 状态 | 说明 |
|---|---|---|---|
| 1 | 设备页**内容与按钮**对齐正式版 | ✅ 已完成 | 真实数据 + 真操作，见 §3-④ |
| 2 | 圆角 28dp | ✅ 已验证 | `zoom_v19_devcard_TL.png` |
| 3 | 磨砂/形变 2 个质感值 | ✅ 已验证 | 取证页形变可见；配色底上属 C 类（§5） |
| 4 | 背景回正式版 | ✅ 已验证 | 归档包无 aurora + 像素对照，见 §2.1 |
| 5 | **在线判据** | ⏳ 未接管 | 正式版 `MainActivity.isOnline()` 是私有方法，切片拿不到那条连接 → 本页**一律按离线渲染**（「离线 · 还没连上」），**不假报在线**。要接必须把设备页挂进 MainActivity 宿主（下一条）。 |
| 6 | **扫码配对** | ⏳ 未接管 | 配对链在 `MainActivity.startPairing()` + 地址同步；切片只给入口文字说明，没有做「点了没反应」的假按钮。 |
| 7 | **进入对话** | ⏳ 半成品 | 目前进的是 Compose 对话**切片**（假气泡），不是正式版会话页。 |
| 8 | 对话页数据/按钮 | ⏳ 未做 | 本轮只换了背景 + 修 insets；气泡仍是演示数据（与用户报的"丢失数据"同类，属下一片）。 |
| 9 | 抽屉（第 3 页） | ⏳ 未做 | 骨架都没有。 |
| 10 | 真机验收 | ⏳ **未验证** | `adb devices` 只有 `emulator-5554`；`A7QK023224004563` 全程未接入。**本轮所有视觉结论仅模拟器有效**。 |
| 11 | 「把设备页换进 MainActivity」 | 🔲 **待用户决定** | 这是"真正替换整个 app"的关键一步：需把 `Screen.DEVICE` 整体换成 Compose（背景+内容+玻璃同层，否则 `layerBackdrop` 取不到样），并把 `Host` 回调（onOpenDevice/onRename/onDelete/onScanAdd/onManualAdd/onOpenSettings/routeOf）从 Java 传进 Compose。会改动 `MainActivity.java` 的页面结构，按交接总表第七条「不要自行决定影响工程结构的事」→ **先问**。 |

**下一步优先级建议**
1. 要真机验收 → 把 `A7QK023224004563` 接上（USB 或 `adb connect`），我对三页各截一次；
2. 决定上面的**背景三选一**（§5）；
3. 决定**第 11 条**（设备页挂进 MainActivity 宿主）→ 一次把在线判据/路由/全部回调接回真实链路；
4. 之后按 对话页 → 抽屉 的顺序逐个切片。

---

## 7. 工程副作用记录（踩坑/机制）

- **Compose `AndroidView` 能被 `layerBackdrop` 取样**：`AmbientBackground()` 用 `AndroidView` 挂
  Java `Drawable`，玻璃取样正常（`10/30` 截图中卡片内部随背景渐变变化，且能取到 `Ui.AmbientDrawable`
  的四枚光斑）→ 不必用 Compose 画笔重画背景。**这是本轮的一个新增实证**。
- **玻璃的高度不能靠 `RefractionWindow` 自适应**：见 §3-③ 的 `GlassSurface()`。
- **模拟器上 `am start` 会把已有 task 顶到前台而不弹栈**：
  `Warning: Activity not started, intent has been delivered to currently running top-most instance.`
  → 要在三个切片页之间切换取证，必须 `am force-stop com.dsh.mobile` 后再 `am start`，
  否则会串到栈里的其它页（本轮踩过：按 BACK 回到上一轮遗留的 `GlassTunerActivity`）。
- **`adb shell input` 填中文/特殊字符表单**：先 `ime disable` 或每步截图定位；
  对话框被键盘顶起后坐标会变，盲点会误触到 scrim 把对话框关掉（本轮踩过一次）。
- **交叉验证到一条真实链路**：在 **Compose 切片**里手动添加的设备，打开**正式版 MainActivity
  设备页**（`32_official_final.png`）也能看到同一张卡 → 说明 `Store` 写入是真库、两套 UI 共用同一份数据。

---

## 8. 记录一条**故意不改**的 A 类差异（留给 V2.0，本轮 2 个质感额度已用完）

设备卡里的「图标砖」质感与正式版不一致，但**本轮预算不允许改**，先量化挂账：

| 项 | 正式版（`FrostedPanelView` + `tint 0x42FFFFFF`） | 本轮 Compose（`RefractionWindow`，Token tint = 白 20% + vibrancy） |
|---|---|---|
| 砖体可见度 | 在这套配色底上**几乎看不出砖**，只见黑图标 | **看得见一块偏白的圆角砖 + 白棱** |
| 图标 glyph 实测包围盒 | **98×85px ≈ 37.3×32.4dp**（`measure_icon.py`） | **39×35px ≈ 14.9×13.3dp**（Icon 18dp × vector 0.78 内容比） |

关于图标尺寸为何差这么大：正式版源码写的是
`Ui.iconDrawable(ctx, R.drawable.ic_monitor, 8f, Ui.INK)`（**意图是 8dp**），但该 Drawable 交给
`ImageView(44dp)` 后 `configureBounds()` 覆盖了我们 `setBounds(8dp)` 的结果，实测被画成 ~37dp
（≈ 填满 44dp 砖）——**这更像正式版的一处历史遗留，不是"正确的样子"**。
另外用户在 2026-10-07 明确说过「黑色的内容有点太饱满，缩小一半」（当时针对齿轮图标），
**用户的偏好是"别太饱满"** → 本轮**保持 18dp 不动**，等 V2.0 连同砖体 tint 一起定夺。

