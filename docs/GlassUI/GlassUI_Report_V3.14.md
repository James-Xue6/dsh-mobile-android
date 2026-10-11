# GlassUI 迭代报告 V3.14 | 2026-10-09

> 触发：用户真机实测报 **5 个 bug**
> 本轮：**修好第 4 个（功能性）并复现+验证；其余 4 个给出定因进度与下一步**
> 本轮 **A 类质感数值改动 = 0**（改的是重组作用域，不动质感）

---

## 1. ✅ Bug 4：设置里切「浅色 / 配色 / 磨砂度」不生效 —— 已修并验证

### 1.1 复现（有效）
模拟器打开设置页 → 滚到「深色模式」→ 点「浅色」：
**修复前**：全屏 **0.01%** 像素变化（≡ 完全没反应）。

### 1.2 根因（代码分析）
`GlassXxxScreen` 里的 `@Suppress state.themeRev` 只能让**这一屏自己**重组，
而真正决定"配色/材质"的两处在它**外层作用域**：
- `GlassAppTheme { … }`（Material3 配色桥，创建时读 `Ui.isDark()` 烘死）
- `AmbientBackground` 里的 `key(Ui.ambientPalette(), Ui.isDark())`（在更外层被创建）

⇒ 换个主题时外层不重建 ⇒ **文字色变了、背景没变**，正是用户描述的"只有字体变色"。

### 1.3 最小 diff
`GlassAmbient.kt` 四个 `createGlassXxxView()` 里，把
`GlassAppTheme { GlassXxxScreen(...) }`
包成 `androidx.compose.runtime.key(state.themeRev) { GlassAppTheme { GlassXxxScreen(...) } }`（4/4 处）。

### 1.4 验证（模拟器）
| 动作 | 结果 |
|---|---|
| 点「浅色」 | 全屏均值 **66.1 → 160.0**，**99.60%** 像素变化；`shared_prefs` 里 `theme_mode=light` ✓ |
| 点配色「素雅」 | 全屏均值 160.0 → 156.9，**35.66%** 像素变化 ⇒ 配色同样生效 ✓ |

## 2. ⏳ 其余 4 个（本轮未做，已定因或已定位，下一步照此改）

### Bug 1 —— 添加设备弹窗"左右两侧有多余的框"
- 已知：`GlassSheetOverlay` 的面板是 `RefractionWindow`，它内部用**官方 `Shadow.Default`**
  （`RefractionWindow.kt` 文档 §"highlight / shadow / innerShadow 的取用"：Shadow 用官方默认、未改）
  ⇒ 面板两侧/下方会有**外阴影带**；叠加我在面板内又垫了一层 `matchParentSize` 半透明底，
  看起来就像"页面背景被缩小、四边多出框"。
- 下一步：给 `RefractionWindow` 加一个 `shadow: Boolean = true`（默认不变），弹窗面板传 `false`，
  并去掉内层那块多余的底色（只留玻璃自身的 tint）。

### Bug 2 —— 「手动添加」进去还是老界面
- 已知：`onManualAdd()` 走的是**旧 Java 对话框**（`ManualAddDialog`），不是 Compose。
- 下一步：给统一弹窗骨架加**表单形态**（标题 + 4 个输入：内网/公网/令牌/设备名 + 保存/取消），
  用 `showForm(tag, title, fields, primaryLabel)` 触发；`sheetPick` 回传各字段值。

### Bug 3 —— 首页右上角设置按钮没有按动反馈
- 已知：`ic_sliders` 这个齿轮目前只在**独立切片** `GlassDevicePageActivity.kt` 里；
  托管版设备页顶栏那颗按钮的落点还没找到（grep 未命中）。
- 下一步：先定位托管版顶栏按钮 → 换成 `Modifier.glassTap { … }`（与其它玻璃按钮同一套按压反馈）。

### Bug 5 —— 顶栏/设置导航栏滑动没玻璃质感 + 玻璃按钮阴影偏弱
- 已知（代码确证）：`GlassSettings.kt` 的**取样层里只有 `AmbientBackground`**
  ⇒ 设置页的分区卡/顶栏只能采到静态背景，滑动内容不参与 ⇒ 当然"没有玻璃质感"。
- 下一步：① 把**可滚动内容挪进取样层**、分区卡从 `GlassSurface`（真玻璃）换成**半透明卡**
  （V3.2 的层内规则）；② 玻璃按钮阴影略弱 ⇒ 与 Bug 1 的阴影一起做**一次受控的阴影调参**
  （SOP 允许单次 ≤2 个质感数值）。

## 3. 状态
| Bug | 状态 | 证据 |
|---|---|---|
| 4 主题/配色不生效 | ✅ **已修并验证** | `evidence/v3211/01_before.png` → `02_after_light.png`（99.6%）、`03_after_palette.png`（35.66%） |
| 1 弹窗多余的框 | ⏳ 已定因 | `v3100/03_sheet.png`（面板外有阴影带） |
| 2 手动添加还是老界面 | ⏳ 已定位 | `onManualAdd()` |
| 3 齿轮无按动反馈 | ⏳ 待定位按钮 | grep 未命中托管版 |
| 5 导航栏无玻璃 + 阴影偏弱 | ⏳ 已确证 | `GlassSettings.kt` 取样层只有背景 |
