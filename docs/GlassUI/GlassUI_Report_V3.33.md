# GlassUI 迭代报告 V3.33 | 2026-10-10 晚（接手 V3.32）

> **触发**：用户「按最新的 `GlassUI_Report_V3.32.md` + `GlassUI_待办_20261009_真机6条.md` 执行」。
> **真机策略**：用户明确选「**先改代码 + 模拟器验证，真机稍后补**」⇒ 本报告所有结论的证据均为**模拟器**，
> 真机项一律标 `⏳ 未验证（需真机）`，**不当作已验收**。
> **基线**：`GlassUI_Report_V3.32.md`（20:43:45 那版）。
> **设备**：模拟器 `emulator-5554`（Pixel_7_API35，1080×2400，**420dpi ⇒ 1dp = 2.625px**，注意别用真机的 3.5 算）。
> **产物**：`dist/dsh-mobile.apk`，`sha256 = d565bab5e10123b9bf9db80a9bbb292cb44c270325fb9cff4529fa416de3f628`（官方签名 release）。
> **证据目录**：`evidence/v3330/*`（57 个文件）。

---

## 0. 结论速览（对照 V3.32 §2 + 6 条待办）

| 条目 | 状态 | 一句话 |
|---|---|---|
| V3.32 §2.2 刻度尺"预览气泡" | ✅ **新缺陷，已定位+已修+已验** | 气泡**从来就没画出来过**（宽被父约束夹成 0），不是"手感差"；修后模拟器拖拽帧可见 |
| V3.32 §2.2 刻度条"不能用" | ✅ 已复验（V3.39 的修法成立） | 触摸带只占 42dp，带内吃手势、带外正常滚动 |
| V3.32 §2.4① 阴影位图半分辨率 | ✅ **已实施 + 同包 A/B 验证** | 像素/上传 ≈ 1/4，**同包对照像素差 0.076**（肉眼无差） |
| V3.32 §2.4④ 导轨脉动动画 | 🚫 **过时项**（据实核减） | 全 `src` 已无 `rememberInfiniteTransition`/`infiniteRepeatable`，该优化无从下手 |
| 6 条待办 **E**：按压双影其余位置 | ✅ 已修 7 处 + 脚本审计 | 其中有 5 处**真违规**被漏掉了（含一处死代码里的） |
| V3.32 §2.1 抽屉 ⋯ 玻璃浮层 | ⏳ 未做 | 见 §6；已发现这就是 `SheetLikeOverlay`（现为死代码，坯子在） |
| V3.32 §2.3 目标胶囊玻璃 | ⏳ 未做 | 铁律：不能新增 `RefractionWindow`（会 native 崩）⇒ 得复用同层已有玻璃子树 |
| V3.32 §2.4②③ 滚动跳过阴影 / 行内改描边 | ⏳ 未做 | 需把 `isScrollInProgress` 接到消息行，改动面比①大，留下一轮 |
| 卡顿数字复测（jank 9.63%） | ⏳ 未验证（需真机） | 模拟器**没有真实会话数据**可滚 ⇒ 测不出有意义的 `gfxinfo` |

---

## 1. 基线快照（开工时实测，写下来免得下轮重推）

| 项 | 实测值 |
|---|---|
| 上一轮的 APK | `dist/dsh-mobile.apk`（20:09:07），报告只到 V3.32 |
| 构建方式 | ⚠️ `build.ps1`（免 Gradle 老脚本）**已失效**（只编 Java、不认 Kotlin/Compose）⇒ 一律 `gradle :app:assembleRelease` |
| 签名 | 口令在 `keystore.local.ps1` 的 `$KsPass` → `$env:DSH_KS_PASS`；**必须先 `Remove-Item Env:DSH_KS`**（空串会让 `storeFile=""`） |
| JDK / SDK | `JAVA_HOME=D:\AndroidStudio\jbr`、`ANDROID_HOME=F:\AI\程序开发\android-sdk` |
| adb | **不在 PATH**，实际位置 `C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe` |
| 模拟器启动 | `C:\Users\Administrator\emu_copy\emulator\emulator.exe -avd Pixel_7_API35 -no-snapshot -gpu host` |
| 装机后首启 | 会弹**运行时权限框**（CAMERA）⇒ `pm grant com.dsh.mobile android.permission.CAMERA`；换签名包要先 `uninstall`（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`） |
| 演示数据入口 | `com.dsh.mobile.glass.GlassChatPageActivity`（`demoItems()`，**真的 `ChatItem`**）、`GlassDevicePageActivity`、`GlassSettingsActivity` |
| 冷启动基线 | `MainActivity` 前台 ✓、`logcat -b crash` = 0 条（`evidence/v3330/080_reg_main.png`） |

---

## 2. T2 —— 刻度尺「预览气泡」根本画不出来（本轮最重要的新发现）

### 2.1 现象（V3.32 §2.2 只说"待真机复验手感"，其实是功能压根不存在）

模拟器上按住刻度条拖动，**连抓 7 帧全部字节相同**（`009_dragframe_0..6.png`，SHA256 全为 `AAB6FA93…`）
⇒ 拖动过程中**屏幕上没有任何东西变化**。而拖动本身是生效的（`onDrag` 里会 `scrollToItem`，
实测列表被跳回 `turns[0]`，`010_before_rail_swipe.png` → `011_after_rail_swipe.png` 两图不同）。

### 2.2 根因（量算出来的，不是猜的）

先纠正一个**量算坑**：模拟器是 420dpi（**1dp = 2.625px**），不是真机的 3.5。
按 2.625 换算后，刻度条实测几何与代码**完全吻合**（`012_rail_zoom.png` 放大图）：

- 灰底宽 **30dp = 78.8px**（实测 78px）✓
- 选中格 **22dp = 57.8px**（实测 58px）✓
- ⇒ `TurnRail` 的外盒 = `Modifier.width(48.dp)` + `padding(end = 6.dp)` ⇒ **内容约束 maxWidth = 42dp = 110px**

而预览气泡写的是：

```kotlin
Modifier.align(Alignment.CenterEnd).padding(end = 46.dp).width(240.dp)
```

`padding(end = 46dp)` 作用在 42dp 宽的父盒上 ⇒ 子内容拿到 `maxWidth = 42 − 46 < 0 ⇒ 0`；
`Modifier.width()` 默认 `enforceIncoming = true`，还会被父约束**再夹一次** ⇒ 最终 **宽 0、不可见**。
所以这不是"气泡不好看/挡手势"，是**气泡根本没被画出来**。

### 2.3 最小 diff（只动这一个气泡，**不碰**刻度条本体的 42dp 触摸带）

`src/com/dsh/mobile/glass/GlassChatScreen.kt`

```diff
+import androidx.compose.foundation.layout.wrapContentSize
+import androidx.compose.foundation.layout.requiredWidth
...
             androidx.compose.material3.Surface(
-                modifier = Modifier.align(Alignment.CenterEnd)
-                    .padding(end = 46.dp).width(240.dp),
+                modifier = Modifier.align(Alignment.CenterEnd)
+                    .wrapContentSize(Alignment.CenterEnd, unbounded = true)
+                    .padding(end = 46.dp)
+                    .requiredWidth(240.dp),
```

`wrapContentSize(unbounded = true)` 让子树**按自身 240dp 量**（父盒仍是 42dp ⇒ 触摸带不变），
`requiredWidth` 不受父约束夹取 ⇒ 气泡从轨道**左侧溢出**画出来。

### 2.4 验证

| 检查 | 结果 |
|---|---|
| 修后拖拽中连续帧 | **帧不再相同**（`020_bubble_after_fix_0..6.png`，第 1–5 帧同一新状态）⇒ 拖动中确实多画了东西 |
| 气泡外观 | `020_bubble_after_fix_2.png`：品牌渐变气泡 + 白字「第 1/1 个问题」 |
| 气泡右缘 | x ≈ 943px = 距父盒右缘 46dp ⇒ **停在轨道左侧，不盖住触摸带**（"气泡不挡手势"✓） |
| 出货包复验 | `090_final_bubble_0..4.png`：第 1–4 帧同为新状态 ✓ |
| 跳转正确性 | 拖一下 ⇒ 列表回到 `turns[0]`（= 顶部），`010`/`011` 对照 ✓ |
| 触摸带宽度 | 起点在带内（x=1005）**不吃列表滚动**；带外（x=540/860）正常滚动 ✓ |

> ⚠️ 仍**未验**：真机 1:1 跟手手感、多回合（n>1）时的跳转落点 —— 演示数据只有 **1 条 USER**（`n=1`），
> 结构上无法测"跳准"。要测必须给 `demoItems()` 加几条 USER 回合，或上真机。

---

## 3. T5 —— 按压双影：剩余 7 处模序（6 条待办 E）

### 3.1 规则与审计方法

规则（`GlassSheet.kt` V3.21 注释）：**`glassTap` 必须排在 `clip/background` 之前**
（Compose 修饰符"外侧先画"；否则 `graphicsLayer{scale}` 只包住内容层，原尺寸的底留在下面 = "缩小 + 两层"）。

写了个脚本 `evidence/v3330/check_tap_order.py` 扫全 `glass/` 目录：逐条找 `.glassTap`，检查**其上一行**
是否含 `clip(`/`background(`/`border(`/`.shadow(`。第一遍扫出 **8 处**：

| # | 位置 | 性质 |
|---|---|---|
| 1 | `GlassSessionList.kt` `SheetLikeOverlay` 菜单行 | 真违规（**且该函数全工程只定义、没调用** ⇒ 死代码，是 §2.1 树内浮层的坯子） |
| 2 | `GlassSessionList.kt` `SheetLikeOverlay`「关闭」行 | 真违规（同上，死代码） |
| 3 | `GlassSessionList.kt` `SmallPill`（抽屉 ⋯ / 子会话 N） | 真违规 |
| 4 | `GlassChatScreen.kt` 提问卡**选项行** | 真违规 |
| 5 | `GlassChatScreen.kt` `MsgPill`（允许/拒绝） | 真违规 |
| 6 | `GlassSettings.kt` 配色**圆点** | 真违规 |
| 7 | `GlassSettings.kt` `Segmented` 分段项 | 真违规 |
| 8 | `GlassSettings.kt` `LogOverlay` 全屏"点外部关闭"层 | **不算违规**：它 `corner=0, scale=1f, dim=0f` ⇒ 本来就无视觉反馈 |

其余 20 余处（会话卡 / 玻璃弹窗行 / 圆钮 / chip / ↓ 按钮 / 任务条 / `MsgCard` 家族 / `GlassSurface`）**均已合规**
（`GlassSurface`、`RefractionCapsule`/`RefractionWindow` 的 `modifier` 都加在最外层 `Box`/`BoxWithConstraints` 上，顺序天然正确）。

### 3.2 最小 diff

**7 处**都只是把 `.glassTap{…}` 这一段**前移**到 `clip/background` 之前，无第二处改动：
`GlassSessionList.kt`(2 处 + `SmallPill`)、`GlassChatScreen.kt`(2 处)、`GlassSettings.kt`(2 处)。

### 3.3 验证

| 检查 | 结果 |
|---|---|
| 复扫脚本 | 第二遍只剩 #8（已判定为"故意无反馈"）⇒ 无残留真违规 |
| 编译 | `BUILD SUCCESSFUL`，0 error |
| 代表性 A/B（同一屏、同一点击坐标、只差 `glass_force_press`） | `050_settings_appearance_normal.png` vs `051_settings_appearance_pressed.png`；对 `Segmented`（7 号改动）出差异热力图 `052_diff_segmented.png`：**整块药丸含边缘都在变**，看不到"外圈原亮度环" ⇒ 底色跟着一起缩+暗 |
| 崩溃 | `logcat -b crash` = 0 条 |

> ⚠️ 诚实说明：**1/3/4/5/6 号**（抽屉菜单行、`SmallPill`、提问卡选项行、`MsgPill`、配色圆点）
> 本轮**只做了静态审计 + 编译验证**，没有逐个人工点按取证 —— 判据是"与 7 号同一条规则、同一种改法"。
> 其中 **1/2 号在死代码里**，模拟器无法触达。真机/抽屉有会话时才能逐个复验。

---

## 4. T3 —— 卡顿优化①：阴影位图半分辨率（V3.32 §2.4①）

### 4.1 改了什么

`src/com/dsh/mobile/glass/GlassAmbient.kt` → `Modifier.glassShadow()`（Compose 侧**阴影唯一出处**）：

- 位图按 **1/2 边长**建（`bwFull*0.5`、`bhFull*0.5`）；
- 画形状时 `cc.scale(ss, ss)` 再 `cc.translate(padPx, padPx+dy)`（矩阵**后调用者先作用** ⇒ p ↦ (p+pad)·ss）；
- 箱式模糊半径**同比例减半**；
- 绘制时用 `drawImage(srcSize/dstOffset/dstSize, filterQuality = Low)` **放大回原逻辑尺寸**，落点与旧实现一致（左上角 `-pad`）。

**观感参数一个没动**（pad / 模糊系数 0.9 / 颜色 / 偏移量全部原样）——只改采样精度，符合"一次 ≤2 个质感数值"（实际 0 个）。

### 4.2 验证：**同一份 APK** 内 A/B（这招是这轮新加的）

跨构建截图对比**不可靠**（对话页首帧有滚动竞态，见 §6，两次启动的滚动位可能不同 —— 本轮就被坑过一次，
`060` vs `062` 差 81% 像素）。所以给 `GlassDebug` 加了一个**只影响阴影精度的开关**（默认 `true` = 出货行为）：

```
adb shell am start -n com.dsh.mobile/com.dsh.mobile.glass.GlassChatPageActivity                                  # 半分辨率
adb shell am start -n com.dsh.mobile/com.dsh.mobile.glass.GlassChatPageActivity --ez glass_shadow_fullres true     # 旧实现（全分辨率）
```

两次走**完全一样的启动+滚动序列**，只差这一个变量：

| 区域 | 平均绝对像素差 | 差 >2 的像素 | 差 >8 | 最大差 |
|---|---|---|---|---|
| 全图（1080×2400） | **0.076** | 2118（0.082%） | 282（0.011%） | 93 |
| 目标卡+任务条+气泡区（1000×1640，阴影最密） | **0.081** | 1882（0.115%） | 108（0.007%） | 93 |

⇒ **阴影位图降到 1/4 分辨率后，画面与全分辨率实现几乎逐像素相同**（残留差异集中在时钟/时间分隔条等动态文字上）。
证据：`070_shadow_fullres.png` / `071_shadow_halfres.png` / `diff_stats.py`。

> 反例留档：`061` vs `063`（设备页前后）差 **0.004** —— 但设备页**根本没用 `glassShadow`**，
> 所以那张对照**不能**当作阴影的证据，只说明设备页无回归。

### 4.3 收益（静态可证）+ 待测

- 阴影位图像素量、`boxBlurBitmap` 的计算量、**GPU 上传字节数** ≈ **降到 1/4**（边长各半 ⇒ 面积 1/4）。
- 对应 V3.32 归因的两个最大头：`Slow issue draw commands 395`、`Slow bitmap uploads 123`。
- ⏳ **jank 数字复测必须上真机**：模拟器没有真实会话数据，`dumpsys gfxinfo` 采不到有意义的滚动样本。

---

## 5. 据实核减的过时项（免得下轮照着做无用功）

1. **V3.32 §2.4④「导轨脉动改 `graphicsLayer` lambda」** ⇒ **该动画已不存在**。
   全 `src` 搜 `rememberInfiniteTransition` / `infiniteRepeatable` / `rememberInfinite` = **0 命中**；
   `TurnRail` 现为**纯 Canvas 静态绘制**（无动画、无逐帧重组）。这条待办**不要再做**。
2. **V3.32 §2.4③「列表行阴影改浅描边」** ⇒ 与①/②是同一族取舍，但会**改观感**（用户看得见），
   应排在①之后、并等用户点头，本轮未做。
3. **`SheetLikeOverlay`（`GlassSessionList.kt:249`）是死代码**：全工程只有定义、无调用（V3.31 回退 Compose 菜单的遗留）。
   **本轮没有删** —— 它正是 §2.1「抽屉 ⋯ 玻璃观感必须走树内浮层」要用的坯子，已顺手把它的模序修好待用。

---

## 6. 本轮新发现、**未修**（登记，别丢）

**对话页首帧可能自己滚走（疑似竞态）**

```kotlin
LaunchedEffect(state.items.size) {
    if (state.items.isNotEmpty() && runCatching { !listState.canScrollForward }.getOrDefault(true)) {
        animateScrollToItem(state.items.lastIndex)
    }
}
```

`LaunchedEffect` 在**列表尚未测量**时就会跑，此时 `canScrollForward` 为 `false`（而非"还有更多"），
于是即使不该贴底也会 `animateScrollToItem(last)`。本轮实测到**同一入口两次冷启动的滚动位不一致**
（`060` 滚到中段 / `062` 停在顶部），就是它引起的。

- 影响：正式版"进入会话是否贴底"可能不稳；也**污染任何跨启动的截图对比**。
- 建议修法：先等一帧（`withFrameNanos`）或改用 `listState.layoutInfo.totalItemsCount > 0` 作为前置条件。
- 状态：⏳ 未修（本轮不在用户点名的清单里，且改动会触及"进会话贴底"的产品行为，需用户确认口径）。

---

## 7. 量化差异清单（A/B/C 分类）

| 现象 | 判类 | 当前值 → 目标值 | 处理 |
|---|---|---|---|
| 刻度尺预览气泡不可见 | **B 布局** | 气泡宽 `0dp`（被父约束夹掉）→ `240dp` | 已改（`wrapContentSize` + `requiredWidth`），触摸带 42dp 不动 |
| 按压"缩小+两层"（7 处） | **B 布局**（模序） | `clip→background→glassTap` → `glassTap→clip→background` | 已改 7 处 |
| 阴影位图像素/上传量 | 性能（**非质感值**） | 全分辨率 → 1/4 | 已改（观感参数 0 改动） |
| 对话页数字：无背景层导致观感变化 | **C 环境** | — | 本轮无 |

> 本项目 **A 类（玻璃质感数值）改动**：累计仍为 **0**（与 V3.32 口径一致）。

---

## 8. 项目明细（交接单：逐条对照原任务清单）

| 条目 | 状态 | 证据 | 未成功原因 | 解决办法 / 下一步 |
|---|---|---|---|---|
| T1 建基线 | ✅ 成功 | `080_reg_main.png`、`logcat -b crash`=0、`adb devices`、构建方式核对 | — | 换会话直接照 §1 复现 |
| T2 刻度尺气泡 | ✅ 成功（模拟器） | 修前 `009_dragframe_*` 全同图；修后 `020_*`/`090_*` 帧变化 + 气泡截图 | 演示数据 n=1 ⇒ 测不了"跳准" | 给 `demoItems()` 补几条 USER 回合，或真机复验 |
| T2' 刻度条触摸带 | ✅ 成功 | `005`(带内不吃)/`006`(带外正常)/`007`(带内吃)/`010`→`011`(跳转生效) | — | — |
| T3 阴影半分辨率 | ✅ 成功（视觉）/ ⏳ 性能未测 | 同包 A/B 像素差 0.076（`070`/`071`） | 模拟器无真实会话 ⇒ `gfxinfo` 无意义 | 真机滚动采样复测 jank |
| T4 核减 §2.4④ | ✅ 成功（核减） | 全 `src` grep = 0 命中 | — | 从待办里划掉 |
| T5 按压双影 7 处 | ✅ 成功（编译+静态+1 处动态取证） | `check_tap_order.py` 复扫 0 违规；`052_diff_segmented.png` | 死代码 2 处 + 需抽屉有会话的 3 处，模拟器触达不到 | 抽屉有会话/真机时逐个点按复验 |
| T6 抽屉 ⋯ 树内浮层玻璃 | ⏳ 未做 | — | 本轮先做 T2/T3/T5；且铁律"不能新增 `RefractionWindow`（会 native 崩）" | 下一轮：复用**同层已有玻璃子树**（chips 的 `RefractionCapsule` 族）+ `SheetLikeOverlay` 坯子，**只验冷启动** |
| T7 回归 | ✅ 成功（模拟器） | `080/081/082` 三屏 + `logcat -b crash`=0 | 真机未插 | 真机冷启动 + 抽屉/对话各一轮 |
| T8 归档 | ✅ 成功 | 本文件 + `GlassUI_待办_20261009_真机6条.md` 追加状态段 | — | — |
| §2.1 / §2.3 / §2.4②③ | ⏳ 未做 | — | 见 §0 表 | 见各自行 |

---

## 9. 未验证 / 风险（**不得当已验收**）

1. ⏳ **全部真机项**：用户本轮选"模拟器先验、真机稍后补"。V3.40 那个 **RenderThread `computeTransformImpl` 爆栈 native 崩溃**
   （新增 `RefractionWindow` 触发）在模拟器上**复现不出来** ⇒ 任何"新增玻璃件"的改动**必须真机冷启动复验**。
2. ⏳ jank 数字（V3.32：janky 9.63%、P99 93ms）**本轮没有复测**，也没有"改后必然下降"的实测证据；
   §4.3 只是**静态算术**（像素/上传 ≈ 1/4）。
3. ⏳ `SmallPill` / 抽屉菜单行 / 提问卡选项行 / `MsgPill` / 配色圆点：只做了同规则静态审计。
4. ⚠️ `051` 那次"点外观"实际点到了「连接」（展开后布局位移）⇒ 设置页的**外观组**本轮没截到图；
   但阴差阳错把同组件的 `Segmented` 拍进了同一屏，T5 的代表性取证成立（已在 §3.3 说明）。
5. ⚠️ `evidence/v3330/040/041/042/043` 是**失败的 A/B**（两次滚动位不同），**已作废**，留档只为说明坑，勿引用为证据。

---

## 10. 复现命令（照着抄）

```powershell
# 构建（build.ps1 已失效）
cd F:\AI\程序开发\dsh-mobile-android
. .\keystore.local.ps1; Remove-Item Env:DSH_KS -ErrorAction SilentlyContinue
$env:DSH_KS_PASS = $KsPass; $env:JAVA_HOME='D:\AndroidStudio\jbr'; $env:ANDROID_HOME='F:\AI\程序开发\android-sdk'
& "F:\AI\程序开发\.tools\gradle-9.6.0\bin\gradle.bat" :app:assembleRelease --console=plain
Copy-Item app\build\outputs\apk\release\app-release.apk dist\dsh-mobile.apk -Force

# 装机（模拟器）
$adb = "C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe"
& $adb -s emulator-5554 install -r dist\dsh-mobile.apk

# 刻度尺气泡取证（拖拽中连抓帧；注意 screencap 会阻塞，要并发抓）
& $adb -s emulator-5554 shell am start -n com.dsh.mobile/com.dsh.mobile.glass.GlassChatPageActivity
# 后台起：input swipe 1005 1300 1005 1000 4000；同时循环 screencap → 帧哈希不再相同即气泡在画

# 阴影精度 A/B（同一份 APK）
& $adb -s emulator-5554 shell am start -n com.dsh.mobile/com.dsh.mobile.glass.GlassChatPageActivity --ez glass_shadow_fullres true
```

**取证/分析脚本**（都在 `evidence/v3330/`）：`measure_rail.py`（刻度条矩形）、`crop_rail.py`（放大看形状）、
`check_tap_order.py`（模序审计）、`diff_stats.py`（像素差统计）、`diff_heatmap.py`（差异热力图）。

---

## 11. 踩坑（写给下一个会话）

1. **模拟器不是真机的密度**：Pixel_7_API35 是 **420dpi（1dp=2.625px）**；用真机的 3.5 去算几何会全部对不上。
2. **`adb exec-out screencap -p > file` 在 PowerShell 里会写坏 PNG**（不可解码）⇒ 用
   `adb shell screencap -p /sdcard/x.png` + `adb pull`。
3. **`input motionevent DOWN/MOVE` 分进程调用会让 App 收到 CANCEL**：`onDragStart` 响了、`onDrag` 也跑了
   （列表确实跳了），但截不到"拖动中"的态。要抓拖动中 ⇒ 用 `input swipe` 放**后台 Job**，主线程并发 `screencap`。
4. **`adb shell "cmd &"` 起后台任务会被 shell 退出带走** ⇒ 后台化要放在**本机 PowerShell 的 Job** 里，别放进设备 shell。
5. **跨启动截图对比要当心**：对话页首帧有滚动竞态（§6），两次启动的滚动位可能不同 ⇒ 交叉构建的 A/B 会得出假结论。
   要么**同一份 APK 内做 A/B**（本轮加的 `glass_shadow_fullres` 开关），要么先显式滚到同一确定位置。
6. **"待办里写的优化项"要先核实前提还在不在**（§5.1 的导轨脉动动画早就没了）——照抄待办会白干一轮。
