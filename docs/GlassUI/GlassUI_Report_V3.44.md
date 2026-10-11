# GlassUI 迭代报告 V3.44 | 2026-10-10 深夜（把"不依赖真机"的两条做完）

> **触发**：用户「那你继续啊，继续完成，然后我实机测试」⇒ 按 `GlassUI_真机验收清单_V3.43.md` §2 的序号，
> 把**不需要真机**的两条做完：**① 抽屉两处 ⋯ 的玻璃观感**、**③ 卡顿优化②（滚动时跳过行阴影）**。
> **上一轮**：`GlassUI_Report_V3.43.md`（设置页取样层）。
> **设备**：模拟器 `emulator-5554`（1080×2400 @420dpi）。真机仍未插。
> **产物**：`dist/dsh-mobile.apk`，`sha256 = f5ed2ecb0c8e82a3cba968339e5c76206e5287fd66d5407f9cc2072568ae9686`（签名已核：官方发布密钥）。
> **证据**：`evidence/v3440/*`。

---

## 1. ①抽屉两处 ⋯ 的玻璃观感（V3.32 §2.1，**完成并验证**）

### 1.1 为什么之前是 Java 菜单
`GlassSessionState` 里 `menuFor/wsFor` 两个字段和"移到屏根渲染"的注释**上一轮就留好了**，
但 `GlassSessionListScreen` **根本没渲染任何浮层** ⇒ 两处 ⋯ 只好继续调宿主 Java 菜单。

### 1.2 做法：**树内浮层**（屏根 `Box` + `zIndex`），不碰 `Dialog`
```diff
- Column(Modifier.fillMaxSize().padding(top = topInsetDp)) { …列表… }
+ Box(Modifier.fillMaxSize()) {
+     Column(…) { …列表… }
+     // 屏根浮层：暗遮罩（点外部关闭）+ 玻璃观感卡
+     if (state.wsFor != null || state.menuFor != null) {
+         Box(Modifier.fillMaxSize().zIndex(2f)) {
+             Box(Modifier.fillMaxSize().background(Black 30%)   // scale=1/dim=0 ⇒ 只当点击区
+                 .glassTap(corner = 0.dp, scale = 1f, dim = 0f) { 清空两个状态 })
+             SheetLikeOverlay(标题 / 路径或会话名 / 动作项 / 「关闭」)
+         }
+     }
+ }
```
- ⋯ 只做一件事：`state.wsFor = g` / `state.menuFor = s`（**记录状态**），由屏根统一渲染；
- 工作区菜单 = 「在此工作区新建任务」；会话菜单 = 「重命名 / 归档（危险色） / 关闭」；
- 复用一直没被调用的 `SheetLikeOverlay`（半透明卡 + 统一卡片阴影 + 发丝边 = 抽屉内做不了真模糊时的既定语言）。

> ⚠️ **绝不能用 Compose `Dialog`**：它开**新窗口**，而 `MainActivity` 是纯 `android.app.Activity`
> ⇒ 新窗口里的 ComposeView 拿不到 `ViewTreeLifecycleOwner` ⇒ 真机 dropbox 里 **10 次** `IllegalStateException`（V3.31 §1.3）。

### 1.3 顺带解决"验不了"的老问题：新增**抽屉演示切片**
抽屉的数据来自 `MainActivity.buildRows()`（要**网关 + 已配对设备**才有会话）⇒ 模拟器上抽屉**永远是空的**，
V3.33/V3.42 都卡在"需抽屉里有会话"。

新增 `GlassSessionPageActivity`：用**真的 `WorkspaceGroup`/`SessionInfo` 对象**拼演示数据（2 工作区 + 3 会话 + 1 子会话 + 运行中/待处理），
并且**刻意用纯 `android.app.Activity` + `GlassComposeHostOwner`**（与 `MainActivity` 同构）
⇒ 它同时是"谁再往抽屉里用 Compose Dialog 就直接在这里崩"的**回归哨兵**。

### 1.4 验证（全部通过，crash=0）

| 检查 | 结果 | 证据 |
|---|---|---|
| 列表渲染（分组头/会话卡/子会话缩进/待办徽标/运行点） | ✅ | `400_drawer_demo_list.png` |
| 工作区 ⋯ → **树内玻璃浮层** | ✅ 标题「程序开发」/ 路径 `F:\AI\程序开发` / 「在此工作区新建任务」/「关闭」 | `401_ws_menu_overlay.png` |
| 点遮罩关闭 | ✅ 回到纯列表（与 400 基本一致） | `402_after_scrim_close.png` |
| 会话卡 ⋯ → **树内玻璃浮层** | ✅ 「会话操作」/ 会话名 /「重命名」/「归档」（危险色）/「关闭」 | `403_session_menu_overlay.png` |
| 动作**真的回调到宿主** | ✅ 点「重命名」弹出宿主 toast「重命名「GlassUI 刻度尺对齐」」 | `404` / `405_toast_zoom.png` |
| 崩溃 | ✅ `logcat -b crash` = 0 条 | — |

外观与上一轮"曾实测通过"的那版（`evidence/v3270/383_ws_menu.png`、`385_sess_menu.png`）一致。

---

## 2. ③卡顿优化②：**列表滚动时跳过行阴影**（V3.32 §2.4②，完成并验证）

`MsgCard` 的卡片阴影要**现建模糊位图**（UI 线程箱式模糊）+ **上传 GPU**，而滚动时行不停复用/重建
⇒ 这两笔正是 `gfxinfo` 里 jank 的两个最大头（`Slow issue draw commands 395` / `Slow bitmap uploads 123`）。

做法（**作用域刻意最小**）：
- 新增 `LocalGlassScrolling`（`CompositionLocal`，默认 `false`）；
- `GlassChatScreen` 里**只包住 `LazyColumn`**：`LocalGlassScrolling provides (listState.isScrollInProgress || GlassDebug.forceScrolling)`；
- `MsgCard` 读它 ⇒ 滚动中**不挂** `glassCardShadow`，松手立刻恢复。

> `CompositionLocalProvider` 变值只会让**读它的** `MsgCard` 重组（读不到的地方不变），不会把整个列表重组一遍。

### 2.1 验证：同位置 A/B（新加调试钩子 `--ez glass_force_scrolling true`）

`isScrollInProgress` 只在拖动的那些帧为真、静态截图基本抓不到 ⇒ 加了强制钩子，在**同一个滚动位置**做 A/B：

| 指标 | 值 |
|---|---|
| 全图平均绝对差 | **0.362**，最大 23 |
| 差 >2 的像素 | 84928（**3.277%**）—— 全部落在卡片边缘的阴影带上 |

差异热力图（`412_shadow_diff_heatmap.png`，×5 放大）里是一圈**沿卡片圆角矩形轮廓的亮边** = "阴影没了"的签名；
**关键作用域证据**：
- **在列表里的 `MsgCard`（助手卡）** ⇒ 阴影带出现差异 ✅
- **用户气泡**（不是 `MsgCard`，另有渲染路径）⇒ 一点没变 ✅
- **浮动的目标卡**（在 provider 作用域之外，是"头块"不是"列表行"）⇒ 一点没变 ✅

即"只跳过**列表行**的阴影"，与 V3.32 §2.4② 的原话一致。

---

## 3. 本轮新增的测试设施（下一轮直接用）

| 设施 | 用途 |
|---|---|
| `GlassSessionPageActivity`（纯 Activity + `GlassComposeHostOwner`） | 抽屉列表/两处 ⋯ 菜单/子会话缩进的**可测入口**；也是 Compose `Dialog` 回归的哨兵 |
| `--ez glass_force_scrolling true` | 把"滚动中"标记强制置真 ⇒ 同位置 A/B 行阴影开/关 |
| （沿用）`glass_shadow_fullres` / `glass_force_press` / `glass_force_chat/drawer/device` | 见 `GlassUI_真机验收清单` |

---

## 4. 明确**没做**的（以及为什么）

| 项 | 为什么不做 |
|---|---|
| 卡顿优化**③**（列表行阴影改浅描边） | **观感改动**（用户看得见），要你点头再动 |
| V3.32 §2.3 目标胶囊"规定玻璃质感" | 需要**新增/复用玻璃件** ⇒ 触碰"新增 `RefractionWindow` 真机必崩"铁律 ⇒ **必须真机冷启动验**，等你插机 |
| ↓ 圆钮要不要真玻璃 | 同上（路径是"先减一个节点再加它"），需你拍板 |
| 对话页首帧 `canScrollForward` 竞态 | 改动会触及"进会话是否贴底"的**产品口径**，需你定 |
| 深色主题复核 | 需要真机/手动切系统深色（EMUI 拦 adb 强切） |

---

## 5. 项目明细（交接单）

| 条目 | 状态 | 证据 | 未成功原因 | 下一步 |
|---|---|---|---|---|
| 抽屉两处 ⋯ 玻璃观感 | ✅ 完成（模拟器） | `400`–`405` | 真机/抽屉真实数据未验 | 真机手点两处 ⋯ |
| 新增抽屉演示切片 | ✅ | `400` | — | 以后抽屉相关的验证都走它 |
| 卡顿②滚动跳过行阴影 | ✅ 完成（同位置 A/B） | `410`/`411`/`412` | jank 数字仍要真机复测 | `dumpsys gfxinfo` 对比基线 |
| 四个入口冷启动 | ✅ crash=0 | `MainActivity` / 抽屉切片 / 设置 / 对话 | 真机未插 | 真机按清单 §0 体检 |
| ③ + §2.3 + ↓真玻璃 + 首帧竞态 | ⏳ 未做 | — | 见 §4（分别卡在"观感/真机/拍板/口径"） | 等你定 |

---

## 6. 复现

```powershell
# 抽屉演示切片（列表 + 两处 ⋯ 树内浮层）
& $adb shell am start -n com.dsh.mobile/com.dsh.mobile.glass.GlassSessionPageActivity
#   ⋯ 在工作区头右侧 / 会话卡右侧；点遮罩任意处关闭；动作会 toast 出宿主回调

# 行阴影 A/B（同一滚动位置，只差一个变量）
& $adb shell am start -n com.dsh.mobile/com.dsh.mobile.glass.GlassChatPageActivity                          # 阴影 ON
& $adb shell am start -n com.dsh.mobile/com.dsh.mobile.glass.GlassChatPageActivity --ez glass_force_scrolling true  # 阴影 OFF
evidence\v3330\diff_stats.py <on.png> <off.png>
evidence\v3330\diff_heatmap.py <on.png> <off.png> 30,300,1050,1500 5 out.png
```

## 7. 踩坑（新增）

1. **`GlassSessionState` 里备了字段、屏里没渲染** —— "注释说移到屏根"不等于"真的移了"。
   验证一个功能是否存在，先 grep **调用点**，别信注释。
2. **没有测试入口的功能就是验不了的功能**：抽屉那一屏因为数据依赖网关，两轮都被卡住；
   补一个"纯 Activity 同构"的切片后，既验了本轮，又把那条**闪退路径**变成可回归的哨兵。
