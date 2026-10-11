# GlassUI 迭代报告 V3.52 | 2026-10-11 凌晨（默认液态 · 磨砂整体替换 · 配色自定义底图 · 首条消息顶边）

> **触发**：用户 —— ①「设置里**默认为液态玻璃**；然后磨砂玻璃那个，**把现在所有玻璃组件取消折射这些耗资源的，
> 改成 80% 白色的微透磨砂玻璃，整体替换**」 ②「配色适当增加一个选项**自己上传图片**，采用自己上传的图片当底配色」
> ③「对话内容是通上去的，但**没有顶边定位**，第一条消息会直接发到状态栏那个渐变的位置遮住」。
> **产物**：`dist/dsh-mobile.apk`，`sha256 = 3d18726a23065db194d9e825827befcb6aee7615797770766aad50af9ebfc3e7`（真机 01:55 装机，无新崩溃）。
> **证据**：`evidence/v3520/1000–1012`。

---

## 1. 默认改为「液态玻璃」

改了 5 处默认值：`Store.glassMode()`（缺省 liquid）、`Ui.glassMode` 初值、`GlassSettingsState.glassMode`、
`GlassSettingsActivity`、`GlassSettingsView`、以及 `MainActivity` 建视图前的兜底。
⚠️ **已存过选择的机器仍以存值为准**（模拟器上就是之前点过"磨砂"所以还显示磨砂）——这是"尊重用户选择"，不是没改。

## 2. 磨砂档**整体替换**（关键：以前只管旧引擎）

**根因**：`Ui.setGlassMode()` 原来只改 Java 侧 `BackdropBlurDrawable.LENS_AMT_PX`（38↔0）
⇒ **Compose 那批玻璃件（顶栏/底栏/chips/目标卡/抽屉片…）根本没受影响**。

**现在**：同一步同时把 `RefractionWindowTokens.frostMode`（Compose snapshot state）置真 ⇒
**整屏所有玻璃件**：
- **整段不做 `lens`**（逐像素位移采样，最耗资源那笔；色散也随之关掉）；
- 体色强制 **80% 白**（深色档 80% `#14121C`）；
- 只留模糊 + 受光环 = 用户要的"**微透磨砂玻璃**"。

**验证**（模拟器·设置页）：点「磨砂玻璃」后**整页玻璃一起变白变实**，
顶栏胶囊内部亮度 **169.7 → 209.0**（+39），且是 snapshot state ⇒ **立刻生效、不用重启**。

## 3. 配色 → **自己上传图片当底**（新功能）

| 层 | 改动 |
|---|---|
| `Store` | `ambientImage()` / `setAmbientImage(uri)`（存图片 URI 字符串，空串=用内置 6 色） |
| `GlassAmbient.kt` | 新增 `GlassAmbientImage`（Compose 可观察）＋ `AmbientBackground` 在 uri 非空时**解码铺满**（Crop）+ 一层淡压色罩（浅色压白/深色压黑，保证前景可读） |
| `GlassSettings.kt` | 配色行**第 7 枚「上传图片」**（+ 号圆钮；选中后显示「自定义 ✓」）＋ 宿主接口 `pickAmbientImage()` |
| `SettingsView.Host` + `GlassSettingsView.java` | 新增 `onPickAmbientImage()` / `pickAmbientImage()` |
| `MainActivity` | `onPickAmbientImage()` 拉起 `ACTION_OPEN_DOCUMENT`（image/*）＋ `REQ_AMBIENT_IMG` 回调里：取持久化读权限 → 落库 → 喂给 Compose → `applyThemeEverywhere()`；启动时也会把存量 uri 喂进去；**点内置配色会清掉自定义底图**（避免图片一直压着新配色） |

**验证**：模拟器上第 7 枚「上传图片」已就位（`v3520/1010`）、点击有切片提示（`1012`）；
真机装上不崩（`3d18726a…`）。
⏳ **未验证**：真机点它→系统相册→选图→全屏换底这整条链（**真机不接受注入点击，只能你手点**）。

## 4. 首条消息顶到状态栏（bug）

**根因**：列表 `contentPadding.top = headDp + 2.dp`，而 `headDp` 是**头块实测高度** ——
没有横幅/目标卡时它是 **0** ⇒ 内边距只剩 2dp ⇒ 第一条消息顶在屏幕最上沿、被状态栏渐变罩住。

**修**：`top = max(headDp, headInset) + 2.dp`（`headInset = topInset + 76dp`，正是头块本该让开的量）
⇒ 有没有头块，首条消息都落在**可视窗口内**。⚠️ 我这边没有"无头块"的会话可截，**请你滑到顶复核**。

## 5. 交接单

| 条目 | 状态 | 证据 |
|---|---|---|
| ①默认液态 | ✅ 已装 | 5 处默认值 |
| ①磨砂整体替换（80% 白、无折射） | ✅ 已装并验证 | `v3520/1002` + 亮度 169.7→209.0 |
| ②配色上传图片 | ✅ 代码全链 + 入口已验证；⏳ 真机选图链待你手点 | `v3520/1010·1012` |
| ③首条消息顶边 | ✅ 代码级修复；⏳ 待你复核 | — |
| 仍未装 | ⚠️ **桌面 v2 会话补丁**（"消息不全/历史加载不出来"的根因） | 见 V3.51 §4 |
