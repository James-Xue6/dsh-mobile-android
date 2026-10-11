# GlassUI 迭代报告 V3.0 | 2026-10-08

> 触发：用户「你新装的这个很好，然后现在只有两个展示页，你能不能按照现在这个逻辑开始优化其他界面？」
> 本轮 = **把设备页真正挂进 `MainActivity`（"优化其他界面"的第 ① 步，也是后面每一屏的地基）**
> 验收口径：**真机 PGT-AN10（A7QK023224004563，1312×2848@560dpi）**

---

## 1. 本轮做了什么

「按现在这个逻辑优化其他界面」= 把剩下的屏逐个换成 Compose + 官方 kyant 玻璃。
但**新页面以前只存在于独立 Activity 里**（桌面上的「掌上通·新界面」）——用户正常点图标进的还是旧页。
所以第 ① 步必须是：**把「设备页」挂进 `MainActivity` 宿主**，否则后面的屏做了也看不到。

### 1.1 架构：State + Host 分离（同一份 UI，两个宿主）

```
GlassDeviceScreen.kt
├─ class GlassDeviceState    宿主往里推的**快照**（设备表/当前设备/在线/状态行/线路/连接门/主题版本）
├─ interface GlassDeviceHost 这一屏的**全部动作**回调给宿主
└─ @Composable GlassDeviceScreen(state, host, applyInsets)  纯 UI：只读 state、只调 host
```

| 宿主 | 数据从哪来 | 动作落到哪 |
|---|---|---|
| **`MainActivity`（正式 App）** | `refreshDevices()` 里把算好的东西推进 state | **本类已有的真实流程**：`onOpenDevice / onRenameDevice / onDeleteDevice / onScanAdd / onManualAdd / onOpenSettings / onRetryConnect / onRescanPair / onSwitchRoute / routeOf` —— 一行没重写 |
| `GlassDevicePageActivity`（独立切片） | 自己读 `Store` | 自己的 Compose 对话框 |

⇒ **功能零回归**：连接门（重新连接/切换线路/重新扫码配对）、在线判据、线路来源、改名/删除/扫码/手动/设置
全部还是原来那套代码在跑，只是把「画」换成了 Compose。

### 1.2 一行回退开关

`MainActivity.USE_GLASS_DEVICE`（默认 true）。关掉即回到自研 `DeviceHubView`：
**旧代码一行没删**，`deviceHub` 照样创建、照样被 `refreshDevices()` 更新，只是不挂进视图树。

### 1.3 新增/修改

| 文件 | 改动 |
|---|---|
| `glass/GlassDeviceScreen.kt` | **新增**：State + Host + 整屏 UI + 连接门卡 |
| `glass/GlassComposeHostOwner.java` | **新增**：给"非 ComponentActivity 宿主"补 Compose 三件套 Owner |
| `glass/GlassDevicePageActivity.kt` | 抽出共享玻璃件（internal），独立切片改为薄壳 |
| `glass/GlassAmbient.kt` | `AmbientBackground` 按 `配色/深浅` 做 `key()` 重建；新增 `createGlassDeviceView()` |
| `MainActivity.java` | 宿主桥（9 个回调）+ `showDevices()` 换挂载 + `refreshDevices()` 推状态 + 主题 bump + 生命周期驱动（+4 处小改） |
| `app/build.gradle.kts` | 补 3 个 lifecycle/savedstate 基座依赖（见 §2.3） |

---

## 2. 真机踩的四个坑（都进错题本了）

### 2.1 第一崩：`ViewTreeLifecycleOwner not found`
`MainActivity` 是 `extends android.app.Activity`（**不是** `ComponentActivity`），而只有
`ComponentActivity.setContentView()` 会往视图树挂 Compose 需要的三件套 Owner ⇒ 直接 addView
`ComposeView` 会在 attach 时抛 `IllegalStateException`。
处置：新增 `GlassComposeHostOwner`（实现 LifecycleOwner + ViewModelStoreOwner + SavedStateRegistryOwner，
由宿主在 `onResume/onStop/onDestroy` 驱动），**基类一行不动**（改基类会动到 `onBackPressed` 的分发语义，风险大）。

### 2.2 第二崩：Owner 挂错节点（**必须挂祖先**）
`install(composeView)` 仍然崩 —— 栈里报的是 `FrameLayout{0,0-0,0}`（= MainActivity 的 `root`）：
**Compose 是从父节点向上找 owner 的**，挂在 ComposeView 自己身上找不到。
⇒ 改成 `install(root)`（祖先）+ 顺带 `install(view)`。**这是本轮最关键的一行**。

### 2.3 编译期坑：`ViewTree*Owner` 是 KMP 的 **androidMain-only** 类
用 Kotlin 写这个补丁会报 `Unresolved reference 'ViewTreeLifecycleOwner'`：Kotlin 按 common metadata
解析，而这三个类在 android 源集（缓存里能看到 `ViewTreeLifecycleOwner.android.kt`）。
**javac 能看到 android 变体** ⇒ 该文件改用 **Java** 写，一次通过。
另：`compose-ui` 把 lifecycle 当**实现依赖**，补丁要在 `app/build.gradle.kts` 显式声明三个**非 ktx 基座**
（`lifecycle-runtime` / `lifecycle-viewmodel` / `savedstate`；实测 ktx 变体不把它们当 api 暴露）。

### 2.4 诊断通道坑：华为/荣耀的 logcat 是**加密**的
`adb logcat` 读不到第三方应用的栈（输出只有一行 `(HKS)...` 密文）。
**真机崩溃栈的正确入口是 `adb shell dumpsys dropbox --print`**（本项目注释里早就记过，这次又验证了一遍）。

---

## 3. 验收（真机）

| 项 | 结果 | 证据 |
|---|---|---|
| 正常点图标（MainActivity）→ 新玻璃设备页 | ✅ | `evidence/v300/07_main_hosted.png`（调试包）、`08_final_hosted.png`（最终非调试包） |
| 无新崩溃 | ✅ | `dumpsys dropbox --print`：最后一次 `data_app_crash` 停在 **21:22:47**（修好之前）；此后多次冷启动无新记录 |
| 最终包 | ✅ | `assembleRelease` + 官方密钥签名 + `isDebuggable=false` + `install -r` Success |
| 独立切片页仍可用 | ✅ | 桌面「掌上通·新界面」照旧 |

---

## 4. 下一步（按同一逻辑继续）

| 顺序 | 屏 | 关键工作 | 状态 |
|---|---|---|---|
| ① | **DEVICE** | 见本轮 | ✅ **已完成（真机验证）** |
| ② | **CHAT** | 把 `List<ChatItem>` 的 9 类行（USER/ASSISTANT/TOOL/APPROVAL/QUESTION/FILES/AGENT/STEP/系统条）在 Compose 渲染；顶栏/底栏/待发送/附件条/计划卡 | ⏭ 下一个 |
| ③ | 会话列表 | `SessionInfo` 列表 + 刷新/设备/设置三按钮 | 待做 |
| ④ | 抽屉 | 左侧任务列表 + 遮罩 | 待做 |
| ⑤ | 设置页补全 | 把 `SettingsView` 其余真实项搬进 Compose 设置页 | 待做 |
| ⑥ | 扫码页 / 交付物页 / 各 sheet（附件、模型、子智能体、工作区） | 相机预览与文件预览要保留原生件 | 待做 |

> ② 之后每屏的套路与本轮完全一致：**State + Host + Compose UI + 旧 View 保留一行回退**。
