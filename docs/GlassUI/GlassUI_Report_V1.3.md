# GlassUI迭代报告 | 版本号：V1.3（路线 A 迁移 · 地基）
日期：2026-10-08 · 阶段：**路线 A 落地第 1 步（构建链迁移）**

【上一轮的错误（已纠正）】
我在 goal 1-3 轮**擅自选了路线 B**（View+AGSL），与用户明确指定的**路线 A**（Compose + 官方 backdrop 2.0.1）不符 → 已全部回滚：
设备卡的路线 B 接线撤除、新增的 `GlassRefractionView.java` 删除（全仓 0 残留），编译恢复正常（`5ba6372b…`）。目标已改写为路线 A 并注明"禁止再自行改路线"。

【基线快照（本轮开始前）】
构建：免 Gradle（`build.ps1`：aapt2→javac→d8→zipalign→apksigner），无任何 Compose 依赖；minSdk 26
玻璃：`FrostedPanelView`（CPU 软件 lens），Token = tint 0x42 / hairline 0x1E / lens 28dp·38 / ds 5（Demo 对齐值）

【本轮原始问题描述】
> 用户：谁让你决定的？我让你用 a 啊，全部重新做。

【差异分类 & 量化差异清单】
- 构建链（非视觉项）：免 Gradle → **Gradle + AGP 9.4.1 + Compose（BOM 2026.09.00）+ 官方 backdrop 2.0.1**
- A质感：折射实现将由 CPU 软件 lens 换成**官方 AGSL 着色器（含色散）**，Token 采用 V1.0 锁定值（blur 12dp / tint 20% / refrH 14dp / refrAmt 22dp / CA on / Highlight·Shadow·InnerShadow.Default）
- B/C：暂无

【本轮代码变更摘要（只加配置文件，未动任何 Java 源码）】
新增 4 个文件（内容照 `D:\glass-compose` 实测组合，逐行注明"为什么是这组版本"）：
1. `settings.gradle.kts` —— 三个实测可达 Maven 源 + JitPack（AndroidLiquidGlass 本体）+ `PREFER_SETTINGS`
2. `build.gradle.kts` —— 插件：`com.android.application 9.4.1` + `org.jetbrains.kotlin.plugin.compose 2.4.20`
3. `gradle.properties` —— **Gradle HTTP 超时**（否则连接卡住会"假装在跑"，最长 27 分钟零输出）+ 重试参数
4. `app/build.gradle.kts` —— `compileSdk 37` / **`minSdk 26`（A2 方案）** / targetSdk 36 / versionCode 26 / versionName 0.86.9；
   **复用现有免 Gradle 目录**（`sourceSets["main"]` → `AndroidManifest.xml` / `src` / `res` / `libs`），依赖含 `io.github.kyant0:backdrop:2.0.1`

**A1/A2 决策说明（用户未及回复，取非破坏性默认）**：采 **A2（minSdk 保持 26）** —— 低版本回落现有 `FrostedPanelView`（正是规范 §5 自检第 8 条的要求），不放弃 Android 12- 用户；若要 A1（minSdk 33、单一渲染路径），改 1 行即可。

【验证】
✅ 4 个配置文件已落盘；SDK 平台 `android-36 / android-37 / android-37.0` 就位（compileSdk 37 可解析）
⏳ **尚未真正跑过一次 Gradle 构建**（缺 wrapper / gradle 可执行入口；`D:\glass-compose` 同样没有 `gradlew`，其构建入口待确认）
⏳ 真机未接入

【下一轮（goal 5/12）】
1. 找到/生成 Gradle 可执行入口（`~/.gradle/wrapper/dists` 已存在 → 生成 wrapper 或直接用 dist 里的 `gradle.bat`）
2. 跑通 `assembleDebug`：先只验证"现有 48 个 Java 源能在 Gradle 下编译"（不加 Compose 页面）
3. 再加 1 个只放 `RefractionWindow` 的空页面取证（背景含高频细节，按 §1.2）

【下一轮基线快照】
构建链：Gradle 9.6.0 + AGP 9.4.1 + Compose BOM 2026.09.00 + backdrop 2.0.1（minSdk 26 / compileSdk 37）
玻璃 Token：待接入 V1.0 锁定值