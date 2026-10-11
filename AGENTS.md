# dsh-mobile-android 工程规则

## 弹窗制作规范 —— 强制（与玻璃 SOP 同级）

**所有弹窗必须是玻璃弹窗**：统一走 `glass/GlassSheetOverlay` + `glass/GlassSheetState`
（`GlassChatView#showSheetList / showSheetForm`）。
**禁止新增** `android.app.AlertDialog` / `Ui.dialog(...)` / `ui/ModelSheet.*`，
禁止自绘"圆角白底卡片 + 两个按钮"；弹窗**必须挂 Activity 级全屏宿主**，不许挂在抽屉等窄容器里。

### 弹窗归属（用户 2026-10-12 定调 · 与上一条同级）

**每个界面各自有一个玻璃弹窗宿主，弹窗必须挂在"该界面自己那一层"的最上层。**

| 界面 | 自己那套 |
|---|---|
| 设备页/首页 | `GlassDeviceState.sheet` + `GlassDeviceScreen` 根 Box 末尾的 overlay（采本页 `backdrop`）|
| 设置页 | `GlassSettingsState.sheet` + `GlassSettingsScreen` 根 Box 末尾的 overlay（采本页 `pageBackdrop`）|
| 对话页 | `GlassChatState.sheet` + `GlassChatScreen` 里的 overlay |
| 侧抽屉 | `MainActivity.drawerSheetState` + `DrawerHost` 里那个独立全屏宿主 |

**入口一律走路由**：`MainActivity.currentSheetState()` / `glassInScreenList` / `glassInScreenForm` / `glassInScreenConfirm`。

- ❌ **禁止**把某界面的弹窗写到**对话页**状态上（`((GlassChatView) convo).showSheetForm(...)` 用在设备页/设置页入口）。
  用户停在那些页面时**对话页没被组合** ⇒ 弹窗**没人渲染** ⇒ 真机表现"**透明**"或"**跑到对话窗口去了**"。
  这条**已踩两次**，用户原话：「每个界面的玻璃弹窗都应该在对应界面的位置最上层……为什么都给我搞到了对话窗口」。
- ❌ **禁止**用"某个视图存在"当玻璃门的判据，例如 `if (convo != null) { 走玻璃 } 旧弹窗` ✗。
  `convo`（`GlassChatView`）是**懒建**的，设备页/设置页上它是 **null** ⇒ 整个玻璃尝试被跳过 ⇒ 直接落旧弹窗 ✗
  （真机现象：**第一次打开是旧弹窗，进对话再回来才变玻璃**）。路由 `currentSheetState()` **不需要 `convo`**，
  直接调包装好的 `glassInScreenList/Form/Confirm` 即可。已审计：全工程 0 处此类残留。

### 排错纪律（2026-10-12 用血的教训换来）

同一现象**反复出现**、且**连续两轮靠读代码的猜测都没命中**时 ⇒ **先建立运行时取证，再改代码**：
- 本机真机 `adb logcat` 取回的是**加密块**，读不到应用日志 ✗；
- 用 `getExternalFilesDir(null)/xxx.log` 落文件 + `adb shell cat` 拉回，是这台机器上**可靠**的取证方式
  （`MainActivity.diag()` 就是这个用途，确认问题解决后连同调用点一起删）。
- 绝不允许"再猜一次试试" —— 那次猜错的代价是用户对整轮工作的信任。

规范原文（含四种形态、宿主规则、迁移清单）：`docs/GlassUI/GlassUI_SPEC_DIALOG.md`。

## 液态玻璃 UI —— 强制 SOP（优先级高于任何临时需求）

改玻璃相关代码前**先加载 skill `glass-ui-sop`**；规范原文 `docs/GlassUI/GlassUI_SPEC.md`。

- **组件本体锁定**：`src/com/dsh/mobile/proto/Frost.java`、`FrostedPanelView.java` 的内部绘制/捕获/折射逻辑**禁止重构**。
- **参数分离**：质感参数只在 `Ui.GLASS_*` / `GLASS_DS` / `FrostedPanelView.RIM_*` / `GRAIN_ALPHA` / `lensH|lensAmt`；布局参数（corner/padding/尺寸）只改调用处入参。
- **一次 ≤2 个质感数值**，只给最小 diff。
- **验收只看真机截图**：`adb -s A7QK023224004563`（PGT_AN10）；模拟器仅作参考。
- **每轮产出迭代报告** → `docs/GlassUI/GlassUI_Report_Vx.x.md`（含前后基线快照），下轮先读它。

## 工程速查

- 构建：**用 Gradle（`build.ps1` 已失效，不要再用）**：
  ```powershell
  . .\keystore.local.ps1; Remove-Item Env:DSH_KS -ErrorAction SilentlyContinue
  $env:DSH_KS_PASS = $KsPass; $env:JAVA_HOME='D:\AndroidStudio\jbr'; $env:ANDROID_HOME='F:\AI\程序开发\android-sdk'
  & "F:\AI\程序开发\.tools\gradle-9.6.0\bin\gradle.bat" :app:assembleRelease --console=plain
  ```
  产物 `app/build/outputs/apk/release/app-release.apk` ⇒ 复制到 `dist/dsh-mobile.apk` + 写 `.sha256`
- 真机：`adb -s A7QK023224004563`（PGT_AN10）**可 screencap/install，但不吃注入点击**（要点击得你自己上手）；
  模拟器 `emulator-5554` 可注入点击，但没有真实数据 ⇒ **验布局请用真机截图，验交互可用模拟器**
- 产物：`dist/dsh-mobile.apk`；选型/自测页：`proto/GlassAlignActivity.java`（manifest 标签「玻璃对齐自测」）
- 交接单：`docs/液态玻璃-进度与交接.md`