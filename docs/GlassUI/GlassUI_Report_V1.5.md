# GlassUI迭代报告 | 版本号：V1.5（路线 A 迁移完成：Gradle 构建链首次跑通）
日期：2026-10-08 · 阶段：**路线 A 第 3 步 —— 构建链迁移成功**

【基线快照（本轮开始前）】
免 Gradle 管线（build.ps1）；无 Compose 依赖；玻璃 = FrostedPanelView（CPU 软件 lens）

【本轮原始问题描述】
> （承接目标）把工程迁到路线 A（Gradle + AGP 9.4.1 + Compose + 官方 backdrop 2.0.1）

【结果】
✅ **`:app:assembleDebug` BUILD SUCCESSFUL**（9 秒），产物 `app/build/outputs/apk/debug/app-debug.apk`（12492 KB）
✅ **48 个既有 Java 源全部通过 Gradle 编译**（业务代码一行未改）
✅ 免 Gradle 管线（build.ps1）同时保持可用（删 uses-sdk 前已核实它自带 aapt2 SDK 版本参数）

【本轮清掉的 5 个迁移路障（按出现顺序）】
| # | 报错 | 真因 | 处置 |
|---|---|---|---|
| 1 | `Your project path contains non-ASCII characters`（AGP 拒绝继续） | 工程在 `F:\AI\程序开发\` 中文路径下 | `gradle.properties` 加 `android.overridePathCheck=true` |
| 2 | `mainManifest specifies … app\AndroidManifest.xml which doesn't exist` + `compileDebugJavaWithJavac NO-SOURCE` | `sourceSets` 的相对路径是相对**模块目录 `app/`** 解析的 | 全改 `../AndroidManifest.xml`、`../src`、`../res`、`../libs` |
| 3 | `compileDebugJavaWithJavac FAILED`（报错全在 QrScanActivity：`程序包 com.google.zxing 不存在`） | `libs/core-3.5.3.jar` 在 Gradle 下未进类路径（`jniLibs` 不是 jar 类路径） | `implementation(files("../libs/core-3.5.3.jar"))` |
| 4 | `Manifest merger failed : <uses-sdk> … no longer allowed`（AGP 9 新规） | manifest 不许再控制 SDK 版本 | 删除 `<uses-sdk>`（**先核实** build.ps1 显式传 aapt2 版本参数） |
| 5 | `Error parsing AndroidManifest.xml`（**我方失误**） | 我插入的 XML 注释里含 `--min-sdk-version`，而 XML 注释不允许连续两个减号 | 改写注释 + **加 XML 合法性自检**（`[xml]` 解析过关才继续） |

【本轮代码/配置变更】
- 新增：`settings.gradle.kts` / `build.gradle.kts` / `gradle.properties` / `app/build.gradle.kts` / `local.properties`
- 修改：`AndroidManifest.xml`（删 `<uses-sdk>`，注释避开 `--`）
- 业务源码：**零改动**
- 环境：Gradle 9.6.0（下载 134MB 解压到 `.tools\`）、JDK = Android Studio JBR 25、`ANDROID_HOME` 指向 `F:\AI\程序开发\android-sdk`

【验收】
⏳ 真机未接入（A7QK023224004563 不在 adb devices）；本轮的"验收"= **构建成功 + 产物存在**（视觉验收要等 RefractionWindow 取证页）
⏳ 尚未安装该 APK 到设备（app-debug.apk 是 Gradle 侧产物；生产链仍走 build.ps1 的 `dist/dsh-mobile.apk`）

【重要存档备注】
1. 构建命令模板（已验证可跑，含环境变量）：
   `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; $env:ANDROID_HOME='F:\AI\程序开发\android-sdk'`
   `F:\AI\程序开发\.tools\gradle-9.6.0\bin\gradle.bat :app:assembleDebug --no-daemon --console=plain`
2. 版本组合（照 §2.1 实测）：Gradle 9.6.0 + AGP 9.4.1 + kotlin.plugin.compose 2.4.20 + Compose BOM 2026.09.00 + backdrop 2.0.1；compileSdk 37 / minSdk 26(A2) / targetSdk 36。
3. 踩坑新增：**改 XML 注释后必须做 XML 语法自检**（`--` 是禁区）。

【下一轮（goal 9/12）】
1. 加**只放 `RefractionWindow` 的取证页**：Compose + `rememberLayerBackdrop()` + 背景层 `layerBackdrop` 记录（白网格 10dp 粗/160dp 间距 + 柔边光斑，按 §1.2）
2. 编译 → 安装模拟器 → 截图 → 按 §5 的 8 条自检清单核对 → 出 V1.6 报告
3. 通过后开始逐页替换：设备页 → 对话页 → 抽屉

【下一轮基线快照】
构建链：**Gradle 路线 A 已跑通**；玻璃组件：待接入官方 `backdrop` 的 `RefractionWindow`