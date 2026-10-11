# GlassUI 真机验收报告 V3.45 | 2026-10-10 深夜（真机第一轮）

> **触发**：用户「插上了」。
> **设备**：`A7QK023224004563`（HONOR PGT-AN10，1312×2848 @560dpi，Android 16）。
> **产物**：`dist/dsh-mobile.apk`，`sha256 = 0d523e88bf2c634839d681e863a54d09c731bbe08a05b00a8bbaa0d2605ba771`
> （签名已核 = 官方发布密钥；手机上 `lastUpdateTime = 23:34:15` 即本包）。
> **证据**：`evidence/phone-20261010-night/*`；模拟器侧证据见 V3.33–V3.44。

---

## 0. 一句话结论

装包与冷启动**干净**（dropbox 里**没有**本次的任何崩溃），四屏渲染正常；
**抓到一个真机才暴露的 bug 并已修复+复验**（目标卡压状态栏）；
另外确认了这条 ROM 的硬约束：**注入手势完全无效** ⇒ 交互类验收只能人工上手（清单见 §4）。

---

## 1. 自动能做的部分（已完成）

| # | 检查 | 结果 | 证据 |
|---|---|---|---|
| 1 | 装包 | `Success`（**没弹系统确认框**；按清单先 `am force-stop com.android.packageinstaller`） | 命令输出 |
| 2 | 冷启动（MainActivity） | 前台 ✓；`logcat -b crash` 只有荣耀加密头 `(HKS)…(HKE)`（读不到内容，符合既有记录） | `500_cold_start.png` |
| 3 | **崩溃兜底：dropbox** | 最新条目仍是 **19:47**（上一会话那批 `data_app_native_crash`）⇒ **本次装包/启动/四屏切换 0 崩溃** | `dumpsys dropbox --print` |
| 4 | 对话页（真机会话） | 思考过程卡（`▸ 思考过程 2006 字`）、目标卡、chips（项目/模型/权限）、输入三件式、刻度横条 全部正常 | `501` / `520` |
| 5 | 设备页 | 设备卡（DESKTOP-VEM3215 / 在线 / 当前 / 内网·固定 / DSH 0.2.0-rc.2）、进入对话/修改名称/删除、添加设备 全正常 | `502_device.png` |
| 6 | 抽屉（**真实数据 68 对话**） | 头部三圆钮、分组头（程序开发 8）、会话卡（运行中点/子会话胶囊/⋯）、卡片阴影 全正常 | `503_drawer.png` |
| 7 | 设置页 | 四个折叠区 + 日志按钮 + 顶栏胶囊 正常 | `504_settings.png` |
| 8 | **刻度横条**（真机放大） | **只有品牌色横条、没有白色底** ⇒ V3.42「只留横条」在真机成立；横条**水平居中**在轨道位置（x≈1207 与按 3.5px/dp 算出的 railCx 一致） | `523_rail_zoom2.png` |

---

## 2. 🐛 真机抓到的 bug + 修复 + 复验

### 2.1 现象（`501_chat.png`）
顶栏那一行被**目标卡**压住：`目标（目标文本未到达：objective 为空）` 直接画在屏幕最上沿，
**和状态栏的 23:30 叠在一起**。

### 2.2 根因（读代码即可定位）
```kotlin
val hasHead = state.banner.isNotEmpty() || state.planGoal.isNotEmpty()   // ← 让位判断只认这两条
...
if (hasHead) Spacer(Modifier.height(headInset))                          // ← 让位
...
if (state.planGoal.isNotBlank() || state.planTodos.isNotEmpty() || state.planProgress.isNotEmpty()) {
    MsgCard(...)      // ← 目标卡的**渲染条件更宽**（V3.30 加的"自诊断文案"）
}
if (state.moreStatus.isNotEmpty()) { Text(...) }                          // ← 同样没算进 hasHead
```
⇒ 当 `planGoal` 为空、但 `planTodos/planProgress` 非空时，卡片会渲染、`hasHead` 却是 `false`
⇒ **头块不让位**，卡片顶到屏幕最上沿。（模拟器上没暴露，是因为演示数据的 `planGoal` 非空。）

### 2.3 修复
```diff
- val hasHead = state.banner.isNotEmpty() || state.planGoal.isNotEmpty()
+ val hasHead = state.banner.isNotEmpty() ||
+     state.planGoal.isNotBlank() || state.planTodos.isNotEmpty() || state.planProgress.isNotEmpty() ||
+     state.moreStatus.isNotEmpty()
```
即：**"让位判断"必须与"头块实际会渲染什么"逐条一致**。

### 2.4 复验（真机）
| | 目标卡顶边 y |
|---|---|
| 修复前 `501_chat.png` | **≈75 px**（压在状态栏 23:30 上） |
| 修复后 `520_chat_after_headfix.png` | **≈310 px**（状态栏与顶栏都让开了） |

且复验后 dropbox 仍停在 19:47（无新崩溃）。

---

## 3. 这条 ROM 的硬约束（本轮实证）

`input tap` / `input swipe` 在**普通应用**上**完全无效**：连发两次注入后截图与之前
**逐字节相同**（平均差 **0.00**、最大 0；脚本 `evidence/v3430/check_topbar_reacts.py`）：

| 操作 | 结果 |
|---|---|
| `input tap 540 486`（点「连接」展开） | 截图与操作前 **0.00** |
| `input swipe 540 1800 540 900 400`（上滑） | 截图与操作前 **0.00** |

⇒ **凡是"点了/拖了会怎样"的验收，adb 都做不了**，只能：
① 人工上手（§4 清单）；或 ② 走"启动即到某屏"的调试钩子（`glass_force_chat/device/drawer/press`）+ 静态截图。

---

## 4. ⚠️ 需要你上手的一遍（约 3 分钟）

> 每条只用回答"√ / 有问题（什么样）"即可，我好继续。

| # | 操作 | 应该看到 |
|---|---|---|
| 1 | **抽屉两处 ⋯**：点「程序开发」右侧 ⋯，再点任一会话卡的 ⋯ | 弹出的是**玻璃观感卡**（不是系统灰对话框）；工作区菜单有「在此工作区新建任务」，会话菜单有「重命名/归档」；点卡片外能关；**不闪退** |
| 2 | **刻度横条拖动**：手指按右侧那条横条上下拖 | ①1:1 跟手 ②松手跳到对应回合（准）③**每换一格有轻"咔"** ④拖动时左侧出现气泡、显示**问题原文**，且不挡手指 |
| 3 | **↓ 回到底部按钮**：先在对话里往上滑，让 ↓ 出现，再点它 | 回到最新；位置是否顺手；它的质感与左下 ＋ / 右下 ↑ 是否**看着同一材质** |
| 4 | **设置页顶栏玻璃**（V3.43 修的那处）：进设置 → 点「连接」展开 → **上下滑**让内容从顶栏下面过去 | 顶栏胶囊里**能看到滑过去的内容被模糊/折射**（这是本轮修的重点） |
| 5 | **滚动时行阴影**（V3.44 新做）：列表**快速上下滑** | 滑动中卡片阴影**消失**、松手立即恢复。**观感能接受吗？**（觉得"滑起来发飘"我就关掉） |
| 6 | **深色主题**：系统切深色 → 看对话页/设置/抽屉 | 刻度横条、顶栏胶囊、卡片是否正常（模拟器没截过深色） |
| 7 | **卡顿**：在长会话里**连续滚 10 秒**，然后告诉我一声 | 我去读 `dumpsys gfxinfo` 与基线（janky 9.63% / P99 93ms）对比 |

---

## 5. 项目明细（交接单）

| 条目 | 状态 | 证据 | 未成功原因 | 下一步 |
|---|---|---|---|---|
| 装包 + 冷启动 | ✅ 成功 | dropbox 无新条目 | — | — |
| 四屏渲染 | ✅ 成功 | `501`–`504` | — | 人工看观感 |
| 刻度横条（无白底/居中） | ✅ 成功 | `523` | — | 人工摸手感 |
| **目标卡压状态栏** | ✅ **已修 + 真机复验** | `501`(y≈75) → `520`(y≈310) | — | — |
| 注入手势 | 🚫 **ROM 不允许** | 两次注入后截图 0.00 差异 | 系统限制 | 走 §4 人工清单 |
| 设置页顶栏玻璃（V3.43） | ⏳ 只能人工 | 模拟器侧已决定性取证（0→75.8%） | 需滚动，adb 做不到 | §4 第 4 条 |
| 抽屉两处 ⋯ 浮层（V3.44） | ⏳ 只能人工 | 模拟器侧已验（`evidence/v3440/400`–`405`） | 需点击 | §4 第 1 条 |
| 刻度手感/触感、↓ 钮、深色、卡顿 | ⏳ 只能人工 | — | 同上 | §4 |

---

## 6. 复现命令

```powershell
$adb = "C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe"
$d   = "A7QK023224004563"
# 装包（HONOR 会弹确认框；实测这次没弹）
& $adb -s $d shell am force-stop com.android.packageinstaller
& $adb -s $d install -r dist\dsh-mobile.apk
# 冷启动体检：logcat 是加密的，必须看 dropbox
& $adb -s $d shell am force-stop com.dsh.mobile
& $adb -s $d logcat -b crash -c
& $adb -s $d shell am start -n com.dsh.mobile/.MainActivity
& $adb -s $d shell dumpsys dropbox --print | Select-String "^\d{4}-\d{2}-\d{2} " | Select -Last 5
# 逐屏（钩子）
& $adb -s $d shell am start -n com.dsh.mobile/.MainActivity --ez glass_force_chat true
& $adb -s $d shell am start -n com.dsh.mobile/.MainActivity --ez glass_force_device true
& $adb -s $d shell am start -n com.dsh.mobile/.MainActivity --ez glass_force_drawer true
```
