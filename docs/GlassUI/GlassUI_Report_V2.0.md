# GlassUI 迭代报告 V2.0 | 2026-10-08

> 触发：用户「我手机插上了那继续下一页，你先修改，然后我看看你的成果」
> 本轮 = **真机接入 + 对话页切片 + 真机暴露的暗色问题**
> 上轮：V1.9.2（水波纹灰块）
> **验收口径：真机 PGT-AN10（A7QK023224004563，Android 16，1312×2848 @560dpi ≈375×814dp）**
> —— 从本轮起按工程铁律以真机截图为准。

---

## 1. 真机接入（先解决"装不上"）

**症状**：`adb install -r app-debug.apk` → `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.dsh.mobile signatures do not match`。
**真因**：手机上是**官方发布密钥**签的（工程 `~/.dsh-mobile-keys/dshmobile.jks`，SHA-256 `2E518D75…8ECE8E`，
与 `build.ps1` 的 `$expectedFp` 完全一致）；我出的是 debug 包。

**处置（未动用户数据）**：给 `app/build.gradle.kts` 加 `signingConfigs.officialRelease`（口令走环境变量
`DSH_KS_PASS`，从 gitignore 的 `keystore.local.ps1` 读；**不写进版本库**），`buildTypes.release` 用它 →
`gradle :app:assembleRelease` 产出的包与手机同签名 → `install -r` **Success 且保留数据**。

> ⚠️ 副作用：`gradle assembleRelease` 现在需要 `DSH_KS_PASS`（不设会失败）。`release.ps1` 走的是原有
> `build.ps1` 管线，不受影响。
> ⚠️ `build.ps1`（免 Gradle）**不含 Kotlin/Compose 编译**，所以 Compose 切片页只能由 Gradle 出包。

---

## 2. 对话页 Compose 切片（本轮新做）

**元素清单逐条对齐** `ui/ConversationView.java`（2099 行）+ `ui/ChatAdapter.java`：

| 正式版 | 本切片 | 状态 |
|---|---|---|
| 顶栏**浮动玻璃胶囊**（`applyBarGlass`：blur 34dp / **圆角 22dp**） | `RefractionWindow(radius = 22.dp)` | ✅ |
| ‹ 36dp 圆钮（开任务列表） / ⋯ 36dp 圆钮（更多） | 同尺寸玻璃圆 | ✅ 形态；动作=明确提示（抽屉/更多面板未接管） |
| 标题 `S_HEAD`/INK + 副标题 `S_CAP1`/INK_FAINT（1 行省略） | 同 | ✅ **标题取 `Store.cachedTitle(lastSessionId)`（真值）**，副标题取 `Store.routeLine/routeSource`（真值） |
| **底部磨砂板**（`applyBottomShelf`：blur 26 / **圆角 0**） | `GlassSurface(radius = 0.dp)` | ✅ |
| 「N 子智能体」入口胶囊（ic_people + BRAND 字 + BRAND_SOFT 底） | 同 | ✅（N 用演示值 2 以暴露形态） |
| 输入胶囊（圆角 999）+ ＋ 圆钮 34dp + ↑ 圆钮 36dp（品牌实心 88%） | `RefractionCapsule` + 两个玻璃圆；输入框可真打字 | ✅ |
| 消息 9 类：USER / ASSISTANT / TOOL / APPROVAL / QUESTION / FILES / AGENT / STEP / 系统条 + 时间分隔条 | 9 类逐个复刻 | ✅ |

### 2.1 ⚠️ 诚实说明：这一页**只有标题/线路是真数据**

正式版的消息来自 `MainActivity` 内存里的 `List<ChatItem>`（网关 WebSocket 帧实时喂入）。
**进程级没有静态入口**（实证：`GatewayClient` 的 static 只有 `appCtx` / `bgInteractionHook`，无静态实例；
`KeepAliveService` 只存 Listener）。⇒ 独立切片**拿不到真实消息**，本轮消息是**示范内容**。
真消息必须等「把本页挂进 MainActivity 宿主」（一次接回真实消息 + 全部回调）。

---

## 3. 真机暴露的问题与修复（本轮最大收获）

### 3.1 🔴 暗色主题下 Compose 玻璃被"烧白"（真机 + 模拟器复现）

**真机实测（系统暗色，添加设备玻璃卡）**：

| | 卡内 | 卡外 | 差 |
|---|---|---|---|
| **我的（V1.9.2）** | **192.4** | 83.4 | **+109** ← 几乎不透明白、白字融进底色 |
| 正式版同位置 | 127.1 | 83.4 | +44 |
| 模拟器（浅色） | 188.3 | 183.6 | +4.6 ← 一直没问题 |

**根因**：`RefractionWindowTokens.Tint` 写死 `White 20%` —— 那是**只在浅色主题下调出来的值**。
而**正式版的玻璃体色本来就是主题两套**（`Ui.GLASS_CARD`：浅色 `0x42FFFFFF` 白 26% /
**暗色 `0x4D14121C` 深色 30%**；`GLASS_BAR` 白 15%/深色 44%；`GLASS_INPUT` 白 48%/深色 62%）。
暗色下必须把"白体"换成"深体"。

**修复（1 个质感数值，A 类）**：
```kotlin
val Tint: Color get() =
    if (com.dsh.mobile.ui.Ui.isDark()) Color(0xFF14121C).copy(alpha = 0.30f)   // = 正式版 GLASS_CARD 暗色档
    else Color.White.copy(alpha = 0.20f)                                       // 浅色沿用锁定值
```
**验证**：模拟器切 `cmd uimode night yes` 复现 → 修复后 **卡内-卡外 +43.4 → +21.5**；
浅色**无回归**（172.2 → 172.9）。取证：`20_emu_dark_devpage.png`（改前）/`21_emu_dark_fixed.png`（改后）/
`22_emu_light_fixed.png`（浅色回归）。

### 3.2 🟠 对话页"一进来就滚到底"（输入框抢焦点）

**症状**：页面首屏停在列表底部，顶部几条消息看不到。
**真因**：整页第一个"可获焦"节点是输入框，Android 开窗把焦点给它 → Compose 把它 bring-into-view → 自动滚底。
**修复**：根容器先拿焦点（`FocusRequester` + `focusable()`），输入框不再被自动选中。
**验证**：`23_emu_chat_toptest.png` / `31_real_chatpage_fixed.png` —— 首屏从「今天 20:07」时间条开始 ✅。

### 3.3 📌 真机上发现的两条**正式版自身**问题（记录，未改）

1. **正式版的玻璃卡在真机上糊到了"上方内容"**：`04_official_real.png` 的添加卡内部能看见被糊掉的
   空态图标/文案（来源在卡片上方 ~650px）→ 疑似 `Frost.capture` 在真机上的坐标/补偿偏移。
   模拟器上不复现。属正式版既有行为，按 SOP 不动 `Frost`，**留给用户决定是否单独开一轮**。
2. **EMUI 的深色无法用 adb 强制**：`cmd uimode night yes` 与 `settings put secure ui_night_mode 2`
   都回报"已深色"，但系统 UI 与 App 仍渲染浅色 → **真机暗色复验只能请用户手动切深色**。

---

## 4. 真机验收结果（浅色，2026-10-08 20:30）

| 页面 | 取证件 | 结果 |
|---|---|---|
| 设备页（Compose） | `30_real_devpage_fixed.png` | ✅ 标题/齿轮/副标题/状态行/空态玻璃砖/添加卡/页脚齐全；背景=正式版配色底；**无白线、无灰块** |
| 对话页（Compose） | `31_real_chatpage_fixed.png` | ✅ 顶栏玻璃胶囊 + 9 类消息 + 底栏磨砂板/输入胶囊/＋↑；首屏从顶部开始 |
| 取证页（折射观察窗） | `32_real_evidence_fixed.png` | ✅ 渲染正常 |
| 正式版设备页（对照） | `04`/`35_real_dark_official.png` | 基准 |
| **按压态**（添加卡按住） | `33_real_addcard_pressed.png` | ✅ **卡区变化像素 0 / 243950**（灰块彻底消失） |

---

## 5. 规范/Token 变更（需用户签字）

| 项 | 原 | 现 | 说明 |
|---|---|---|---|
| `HighlightAlpha`（V1.9.1） | 官方默认（等效白≈0.86） | `0.35f` | 待回写 |
| `RefractionWindowTokens.Tint`（V2.0） | 写死 `White 20%` | **浅色 White 20% / 暗色 `#14121C` 30%** | 对齐正式版 `GLASS_CARD` 两套 |
| 工程配置 | — | `app/build.gradle.kts` 加 `signingConfigs.officialRelease` | 真机安装必需；口令走 `DSH_KS_PASS` |

---

## 6. 仍未完成（诚实清单）

| # | 项 | 状态 |
|---|---|---|
| 1 | 对话页**真实消息** | ⏳ 拿不到（无静态入口）→ 必须挂进 MainActivity 宿主 |
| 2 | 抽屉页（左侧任务列表） | ⏳ 未做；顶栏 ‹ 目前只给提示 |
| 3 | ⋯ 更多面板（模型/权限/项目/用量） | ⏳ 未接管 |
| 4 | **真机暗色复验** | ⏳ 待用户切深色（EMUI 拦 adb） |
| 5 | 设备页在线判据 / 扫码配对 / 正式版会话页 | ⏳ 同前，需宿主 |
| 6 | 图标砖 tint / 图标尺寸（V1.9 §8 挂账） | ⏳ 待 V2.x 质感额度 |
| 7 | **把页面挂进 MainActivity 宿主** | 🔲 **待拍板**（一次接回真实消息 + 在线判据 + 全部回调） |
| 8 | 背景三选一（§V1.9 §5） | 🔲 待拍板（默认保持正式版配色底） |
