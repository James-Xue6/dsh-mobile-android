# GlassUI 迭代报告 V3.6 | 2026-10-08

> 触发：「**应用要适配全面屏，背景要通到顶面状态栏和底下的小白条**」
> 上轮：V3.5（对话页第二轮打磨）

---

## 1. 结论：改成 **App 级** 边到边（不再只有两个 Compose 屏）

### 1.1 系统栏一律透明（关键一步）
`MainActivity.applySystemBars()`：状态栏 / 导航栏 / 窗口底**全部改成透明**（原来是实色 `Ui.ambientBarColor()`）。

为什么这一条就够了：`Ui.AmbientDrawable` 挂在**根容器**上，而 View 的背景画在**完整 bounds**（不受 padding 影响）；
targetSdk 35+ 又强制 edge-to-edge（内容框本来就铺满整个窗口）。
⇒ 系统栏一透明，环境背景就**从屏幕最顶端一路通到最下端**（含状态栏 / 手势条区域），
不再有上下两条"对不上"的实色带。

> 图标明暗仍按**环境背景顶端色**判（系统栏已透明，不能再拿它当底）。

### 1.2 内容各屏自己让开安全区
| 屏 | 谁留安全区 |
|---|---|
| **Compose 屏**（设备页 / 对话页） | 屏幕自己：`MainActivity.setContent()` 识别 `ComposeView` → 根容器内边距清零（`applyRootInsets()`）；屏内顶部用 `safeDrawing`（系统栏 ∪ **刘海挖孔**）、底部用 `max(手势条, **键盘**)` |
| **旧 View 屏**（会话列表 / 设置…） | 根容器照旧按 insets 留白（`applyRootInsets()` 对非 Compose 屏沿用实测值）——**内容**让开，**背景**透到边 |
| **抽屉**（盖在 Compose 屏之上的悬浮层） | 新增 `DrawerHost.applySafeInsets()`：抽屉**自己**让开状态栏/手势条（只内缩 `drawer` 的 padding，玻璃背景仍铺满整高） |

### 1.3 顶部 insets 补上刘海挖孔
`MainActivity` 的 insets 监听里 `top = max(systemBars.top, displayCutout.top)`（本机 PGT-AN10 顶部是药丸挖孔）。

---

## 2. 验收（模拟器）

| 取证件 | 结果 |
|---|---|
| `evidence/v360/02_old_drawer.png` | **旧抽屉屏**：环境背景**通到状态栏与手势条**（上下都没有实色条）✓ |
| `evidence/v360/03_old_drawer_fixed.png` | 同上 + 抽屉标题/按钮**让开状态栏**（`applySafeInsets` 生效），玻璃背景仍铺满整高 ✓ |
| `evidence/v360/01_chat_slice.png` | 对话页：背景铺到最上/最下沿 ✓ |
| `evidence/v350/03_emu_hosted.png` | 托管版（MainActivity）对话屏：三件式顶/底栏 + 顶部渐变罩 + 无 double insets ✓ |

**真机**：⏳ 手机上仍停在系统 `PackageInstallerActivity`（安装确认框），`adb install` / `pm install` 都过不去 ——
**需要你在手机上点一次「安装」**（或先手动卸载不需要，同一把官方密钥，数据会保留）。

---

## 3. 仍未做 / 待确认

1. 真机复验（等你点安装）。
2. 能力 chip（权限/模型/思考/用量/项目）仍是平色胶囊（正式版同款）；要"每件都玻璃"我下轮换 `RefractionCapsule`。
3. 目标 ② 遗留：真实网关消息实测（需配对设备）、`scrollToKey/focusInput` 细节、深色主题复验。
4. 后续页：③ 会话列表 → ④ 抽屉 → ⑤ 设置补全 → ⑥ 扫码/交付物/各 sheet。
