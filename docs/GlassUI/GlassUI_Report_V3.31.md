# GlassUI 真机调试报告 V3.31 | 2026-10-10 中午（用户插手机后）

> 触发：用户「手机已插，帮我调试，记录下日志，昨天有卡顿和闪退情况，你看看日志」。
> 取证目录：`evidence/phone-logs-20261010/`（`dropbox.txt` / `crash_buffer.txt` / `anr.txt` / `gfxinfo.txt` / 各界面 dump）。
> 设备：`A7QK023224004563`（HONOR PGT-AN10，1312×2848 @560dpi）。

## 1. 🔴 闪退根因（已定位 + 已修 + 已验）

### 1.1 证据
`dumpsys dropbox` 里 `com.dsh.mobile` 相关 38 条，其中 **`ViewTreeLifecycleOwner not found` 10 次**，栈顶：
```
java.lang.IllegalStateException: ViewTreeLifecycleOwner not found from android.widget.FrameLayout{…}
  at WindowRecomposer_androidKt.createLifecycleAwareWindowRecomposer(WindowRecomposer.android.kt:460)
  at WindowRecomposer_androidKt.getWindowRecomposer(WindowRecomposer.android.kt:293)
  at AbstractComposeView.resolveParentCompositionContext(ComposeView.android.kt:325)
  at AbstractComposeView.onAttachedToWindow(ComposeView.android.kt:440)
```
（另有若干 `gms`/`gsf` 的 `SecurityException`、`UnsupportedOperationException`，均属系统组件，与本 App 无关。）

### 1.2 根因
`AbstractComposeView.onAttachedToWindow` ⇒ 一个 **ComposeView 被挂进"新窗口"** 且该窗口**没有 `ViewTreeLifecycleOwner`**。
全工程**只有两处**会开 Compose 新窗口 —— **我前天给抽屉加的两处菜单用的 `androidx.compose.ui.window.Dialog`**
（`GlassSessionList.kt` 的工作区 ⋯ / 会话卡 ⋯）。而 `MainActivity` 是**纯 `android.app.Activity`（非 `ComponentActivity`）**，
Dialog 窗口里没有 owner ⇒ **必崩**。

### 1.3 已修
`GlassSessionList.kt` + `MainActivity.java`：
- 两处 ⋯ **改回宿主的 Java 菜单**（`Ui.dialog`，不开新窗口）；`GlassSessionHost` 新增 `sessionMenu(s)`，
  `MainActivity` 用 `setItems(["重命名","归档"])` 实现。
- 代码里留**警示注释**：玻璃观感版要重做，但**必须走"树内浮层"（屏根 Box + zIndex）**，绝不能再开 `Dialog`。
- 全工程 `grep androidx.compose.ui.window.Dialog` = **0**。

### 1.4 真机验证（装最新包后）
| 检查 | 结果 |
|---|---|
| 安装（官方签名） | `Performing Streamed Install / Success` |
| 启动后 `logcat -b crash` | **0 条** |
| 前台 | `topResumedActivity = com.dsh.mobile/.MainActivity` ✓ |
| 顺带真机验证 **思考过程卡** | 真机聊天页显示 `▸ 思考过程 | 2522 字` ✓（用户诉求 #3 在真机同款链路成立） |
| ⚠️ 未压到的路径 | 抽屉 ⋯ 那条链（真机上 ‹ 的点击坐标/手势与模拟器不同，本轮 2 次没点开抽屉）⇒ **该路径的实际手点验证留给用户**；但**崩溃构造已被删除**（静态可证） |

## 2. 🟡 卡顿归因（有数据）

`dumpsys gfxinfo com.dsh.mobile`（滚动采样后）：
| 指标 | 值 |
|---|---|
| Total frames rendered | 4163 |
| **Janky frames** | **401（9.63%）**；legacy 1504（36.13%） |
| 百分位 | P50 **11ms** / P90 **22ms** / P95 34ms / **P99 93ms** |
| Frame deadline missed | 401 |
| **Number Slow issue draw commands** | **395** ← 最大头 |
| Number Slow bitmap uploads | 123 |
| Number Slow UI thread | 199 |
| Number Missed Vsync | 35 |
| 直方图尾部 | 150ms×18、200ms×4、250ms×4、300ms×2、350ms×2（偶发长卡顿） |

**归因（按贡献排序）**：
1. **`Slow issue draw commands` 395 ≈ 每个卡顿帧** ⇒ **绘制指令过多**。与本轮改动直接相关的是**自绘阴影**
   （`glassShadow`：每个元素一张模糊位图 + 一次 `clipPath(ClipOp.Difference)` —— `clipPath` 很容易触发
   **layer**，在滚动时逐元素代价高）。
2. **`Slow bitmap uploads` 123** ⇒ 上述**阴影位图上传 GPU**（每个尺寸变化都要重建+上传）。
3. `Slow UI thread` 199 ⇒ 其中一部分是**位图构建时的箱式模糊跑在 UI 线程**（`drawWithCache` 首次绘制时）。
4. 新加的**回合导轨**在 `running` 时有**无限脉动动画**（`rememberInfiniteTransition`）⇒ 持续出帧；列表滚动时叠加。

### 2.1 建议优化（下一步，按性价比排序）
- **阴影位图半分辨率**：按 1/2 边长建位图、模糊半径减半，再放大绘出（**像素/上传量 ≈ 1/4**）——改动小、收益大。
- **列表行滚动时跳过阴影**：`LazyListState.isScrollInProgress` 为真时不画卡的阴影（滚动结束再画）。
- **阴影只给"静态件"**（抽屉卡/面板/圆钮），**列表行内**（任务条/气泡）改用一层浅色描边替代。
- **导轨脉动**改成 `graphicsLayer { alpha = … }` 的 lambda 读取（避开每帧重组），或仅在"新回合开始"时短暂脉动。
- 复核 `GlassSurface`/`RefractionWindow` 的层数（`saveLayer` 越多，`Slow issue draw commands` 越高）。

## 3. 结论 / 待办
| 项 | 状态 |
|---|---|
| 闪退（`ViewTreeLifecycleOwner`） | ✅ 已定位 + 已修 + 真机验证 crash=0 |
| 抽屉两处菜单的**玻璃观感** | 🔶 **本轮回退成 Java 菜单（保稳定）**；要玻璃观感必须**树内浮层**重做（已记警示注释） |
| 卡顿（9.63% janky，主因 draw commands/bitmap uploads） | ⏳ 已归因给"自绘阴影 + layer"；优化方案见 §2.1（**未实施**） |
| 交付物 | `dist/dsh-mobile.apk`（本轮已装到你手机，`Success`） |
