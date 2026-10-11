# GlassUI 迭代报告 V2.1 | 2026-10-08

> 触发：用户「界面有点有点问题呢？0.35 那个你做的很好，怎么装上变样了呢？」→ 随后定位到两条具体报障：
> **①「点添加设备为啥不一样了」②「点右上角的设置就进入不对的首页页面了」**
> 上轮：V2.0（对话页切片 + 真机接入 + 暗色烧白修复）
> 验收口径：**真机 PGT-AN10（A7QK023224004563，1312×2848@560dpi）**

---

## 1. 报障①「点添加设备弹出来的不一样」= Compose 从没挂 MaterialTheme

**现象**（真机暗色，取证 `evidence/v210/04_add_dialog.png`）：App 全局是暗色玻璃，但弹窗是一张
**厚白卡**（`#F5F2F8` 底、黑字），与整体完全脱节。

**根因**：三个切片页用了 Material3 的 `AlertDialog` / `OutlinedTextField` / `TextButton`，
但**从来没有提供 `MaterialTheme`** → 它们一直吃 Material3 的**默认浅色** colorScheme。
浅色主题下"碰巧对"，所以模拟器阶段（一直是浅色）完全没发现。

**修复（1 处新增 + 3 个弹窗）**
1. 新增 `GlassAppTheme`（`GlassAmbient.kt`）：把本 App 自己的 `Ui.*` 主题两套桥给 Material3
   （`primary/background/onBackground/surface/onSurface/surfaceVariant/onSurfaceVariant/outline/error`，
   容器类角色交给 `darkColorScheme()/lightColorScheme()` 自带，避免依赖新版才有的参数）；
   三个切片页的 `setContent` 全部套上。
2. 三个弹窗显式给 **`containerColor = Ui.SURFACE_2`** + **`shape = RoundedCornerShape(28.dp)`**（= 正式版 R_CARD）
   + 标题/正文显式取 `Ui.INK / Ui.INK_SUB`。

**真机验证**：`22_add_dialog_dark_fixed.png` —— 暗色下弹窗变成**深色卡**（深底 + 灰描边输入框 + 白字），
与全局一致 ✅

## 2. 报障②「点齿轮进入不对的首页」= 上轮图省事接到了 MainActivity

**根因**：V1.9 设备页的齿轮写的是 `startActivity(Intent(ctx, MainActivity::class.java))` →
一点就跳进**正式版那个旧首页**。

**修复：新做「设置页 · Compose 切片」**（`GlassSettingsActivity` + manifest 注册），齿轮改指本页。
本页里**真正能用**的部分：

| 项 | 动作 | 真实性 |
|---|---|---|
| **深色模式**：跟随系统 / 浅色 / 深色 | `Store.setThemeMode` → `Ui.applyTheme(resolveDark)` → `recreate()` | ✅ 真写库、立即生效 |
| **配色 6 档**：极光/深海/暮色/森林/紫罗兰/素雅 | `Store.setAmbientPalette` + `Ui.setAmbientPalette` → `recreate()` | ✅ 真写库、背景实时换 |
| 玻璃质感 4 项（Highlight 0.35 / blur 6dp / 折射 14·28dp / 体色） | **只读展示** | 锁定 Token，改它们要出迭代报告，故不做成滑条 |
| 网关 / 设备配对 / 检查更新 / 关于 | 明确标注「这些链路还在正式版里」+ 一个写清「**打开正式版**」的按钮 | 不做"点了没反应"的假入口 |

**真机验证**：
| 取证件 | 内容 | 结果 |
|---|---|---|
| `10_settings_page.png` | 设置页首屏（浅色） | ✅ 顺带确认：用户 App 主题设置是**显式「浅色」**（所以系统深色它也不跟） |
| `21_settings_after_tap.png` | 点「深色」后 | ✅ 整页切深色，且「玻璃体色」一行显示 **暗色 #14121C 30%**（= V2.0 的暗色修复在真机生效） |
| `23_gear_dest.png` | 设备页点齿轮后的前台 | ✅ `mCurrentFocus = GlassSettingsActivity`（不再是 MainActivity） |
| `24_chat_dark.png` | 暗色下的对话页（9 类消息形态） | ✅ 深色玻璃统一、无烧白 |
| `25_settings_restored.png` | 还原用户原设置后 | ✅ 已把主题改回 **浅色**（原值），文件大小与 `20_settings_before.png` 一致 |

## 3. 顺带核实的一条旧结论

V2.0 §3.3 记的「正式版的玻璃卡在真机上会糊到**上方内容**」—— 本轮又拍到一张
（`11_settings_dark.png`，那其实是正式版设备页，不是我的页）：
卡片内部有**被糊掉的空态图标/文案**。**我的 Compose 页在真机上没有这个现象**（`30`/`12`/`22` 都干净），
所以这条确实是**正式版 `Frost.capture` 自身**的真机坐标/补偿偏移，按 SOP 不动 `Frost`，继续挂账。

## 4. 变更清单（需签字回写规范的项累积到 5 条）

| # | 项 | 原 | 现 |
|---|---|---|---|
| 1 | `HighlightAlpha`（V1.9.1） | 官方默认（等效白≈0.86） | `0.35f` |
| 2 | `RefractionWindowTokens.Tint`（V2.0） | 写死 `White 20%` | 主题两套：浅色白 20% / 暗色 `#14121C` 30% |
| 3 | Compose 玻璃按压反馈（V1.9.2） | `Modifier.clickable`（水波纹） | 一律 `Modifier.glassTap`（indication=null + 0.98 缩放） |
| 4 | Compose 主题桥（V2.1） | 无 `MaterialTheme` | 一律套 `GlassAppTheme` |
| 5 | 工程配置 | 无发布签名 | `app/build.gradle.kts` 加 `signingConfigs.officialRelease`（口令走 `DSH_KS_PASS`） |

## 5. 仍未完成（与 V2.0 一致，未变）

对话页真实消息 / 抽屉页 / 「⋯」更多面板 / 设备页在线判据 / 扫码配对 / 正式版会话页 **⏳ 未接管**；
**「把页面挂进 MainActivity 宿主」与「背景三选一」🔲 待拍板**；
图标砖 tint 与图标尺寸差异仍挂账；**正式版真机 Glass 偏移**待用户决定是否单开一轮。
