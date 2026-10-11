# GlassUI迭代报告 | 版本号：V1.0

日期：**2026-10-08**
调试组件：**折射观察窗（RefractionWindow）**——官方 API 版（Compose + `io.github.kyant0:backdrop:2.0.1`）
配套规范：`docs/GlassUI/GlassUI_SPEC_折射观察窗.md`（本报告的执行依据，已锁定 V1.0）
证据截图：`F:\AI\程序开发\液态玻璃引擎Demo\shots\official_02.png`（实跑）、`official_zoom.png`（放大）

---

## 【基线快照（本轮开始前的原始质感参数）】

**本工程（Java/Frost 软件引擎）基线**
- 磨砂档 downscale = `Ui.GLASS_DS[Ui.glassLevel()]`（默认第 4 档 **ds=8**；本轮对比时用过 ds=4/5/6/8）
- 玻璃白度 tint = **0x42FFFFFF**（26%）
- 白棱 hairline = 0x1EFFFFFF
- 折射环带 refrHeight = **28dp**（本项目定版，固定值）
- 折射幅度 refrAmount = **38**
- 受光环 RIM = 0x7AFFFFFF / 0x0CFFFFFF / 0x50FFFFFF（三停）
- 噪点 GRAIN_ALPHA = **10**（噪声图 alpha ≤26 → 实际≈0.4% 不透明度）
- 阴影 = 色 0x33000000 / blur 11dp / off 5dp / ins 3dp / pad 18+5dp
- 背景网格 = 90px 间距 / 4px 粗

**本轮"目标形态"基线（官方 API 版，本轮新引入）**
- 官方库 `io.github.kyant0:backdrop:2.0.1`（= AndroidLiquidGlass 的底层库）
- 官方 README 起步值：`blur(2.dp)` + `refraction(height = 8.dp.toPx(), amount = size.minDimension)` + `drawHighlight()`

---

## 【本轮原始问题描述】

> 用户输入（原样）：
> ①「你对比下参数，为什么没有玻璃质感？」
> ②「看起来不错，你那个折射观察窗里面的效果很不错，我需要你把这个位置的做法锁定出来，按照昨天我给你的方式存储下来输出给我，我要修改app的界面」

**问题拆解**
1. ②是主诉求：把"折射观察窗"这一处的**做法与参数冻结成规范**，供改 App 界面时直接照做（而不是继续调参）。
2. ①留下的结论必须写进规范：**"没有玻璃质感"不是折射不够，而是缺少"表面处理"这一整组参数**。

---

## 【差异分类 & 量化差异清单】

| # | 分类 | 项 | 原值 | 目标值 | 依据 |
|---|---|---|---|---|---|
| 1 | **A质感** | 磨砂（模糊半径） | 本工程 ds=4（≈4–5px 等效）；官方起步值 `blur(2.dp)` | **官方 12dp**；本工程回到 **ds=5**（已验收档） | ds≥6 会把白网格线糊平、形变肉眼不可辨；`blur(2.dp)` 几乎没磨砂 |
| 2 | **A质感** | 玻璃白度 tint | 本工程 26%（0x42FFFFFF） | 官方形态 **20%（0x33FFFFFF）** | 26% 在本 Demo 偏白；10% 像透明塑料（对照页 A/F 档） |
| 3 | **A质感** | 亮度/饱和补偿（vibrancy） | **三端都没有**（本工程/Flutter 一点没补） | 官方 `vibrancy()`；本工程补 饱和×1.45 / 亮度×1.10 | 模糊会把背景**洗灰**，真玻璃反而更干净透亮 |
| 4 | **A质感** | 内侧暗棱（厚度） | **三端都没有** | 官方 `innerShadow = { InnerShadow.Default }` | 只有亮边没有暗棱 = **像贴纸**，没有块感 |
| 5 | **A质感** | 镜面高光 | 本工程 **完全没有**；Web 仅 13% | 官方 `Highlight.Default`（本工程补斜向高光带） | "一眼看出是玻璃"最强的线索 |
| 6 | **A质感** | 磨砂噪点 | 本工程实际 ≈**0.4%** 不透明度（10/255 × alpha≤26） | 噪点本体 0–96 + 强度 46；官方版不需要（Surface 自带） | 原值等于"没画"，表面缺雾面层次 |
| 7 | **B布局** | — | 无（观察窗尺寸/圆角为布局参数，调用处传） | 圆角 26dp / 尺寸 168dp | 正方形最能看出四角形变 |
| 8 | **C环境** | 背景网格 | 90px 间距 / 4px 粗 | 官方版 **160dp 间距 / 10dp 粗**；本工程 150px/10px | 细线在正常磨砂下被糊平 → 会诱使"调弱磨砂"，进而失去玻璃感（**本轮最大方法论结论**） |

**量化验收（目标形态）**：折射观察窗内，背后白网格线在**边缘被推开最大、向中衰减**；环带外原样透出。
本 Demo 的 Web 端做过像素级 A/B：玻璃区 **50% 像素**发生结构性位移；Android 端差异**集中在边缘行/列**。

---

## 【本轮代码变更摘要】

**新增（规范/文档）**
- `docs/GlassUI/GlassUI_SPEC_折射观察窗.md` —— **本轮的正式交付物**：Token 冻结表 + 官方 API 配方 + 两条落地路线 + AGSL 源码 + 自检清单 + 踩坑

**新增（独立工程，不触碰生产链）**
- `F:\AI\程序开发\dsh-mobile-android\glassdemo\` —— 自研引擎（`Frost`）的独立 Demo 工程 + 参数实验室（免 Gradle 构建，包名 `com.dsh.glassdemo`）
- `D:\glass-compose\` —— **官方 API 版**（Compose + backdrop 2.0.1，包名 `com.dsh.glasscompose`）
- `F:\AI\程序开发\液态玻璃引擎Demo\official-api\` —— 官方版源码与 APK 存档

**最小变更（本工程自研引擎，仅 5 处质感数值/逻辑，均在独立 Demo 副本内）**
1. `FrostedPanelView.onDraw()` 位图绘制加 **vibrancy ColorMatrix**（饱和×1.45 / 亮度×1.10）
2. 新增 **镜面高光**斜向渐变层（6 停，左上偏强 → 中归零 → 右下再起）
3. 新增 **内侧暗棱**圆角环（`BEVEL = 0x1FFFFFFF`，宽 = 亮边 ×2.6）
4. 噪点修复：`noise()` alpha 上限 26 → **96**；`GRAIN_ALPHA` 10 → **46**
5. 受光环改 **7 停**（0x9C→0x4A→0x26→0x1A→0x24→0x52→0x8C，宽 1.2dp）；阴影 0x40/15dp/off 6dp

---

## 【真机验收结果】（真机截图为准，IDE/模拟器仅参考）

☐ 通过　☑ **未通过（未验）** —— **⏳ 真机未验证**

- **模拟器**：✅ 通过。Pixel_7_API35（Android 15，1080×2400，`skiagl` + emulation GPU），`shots/official_02.png` 可见白网格线在玻璃内被推移/弯折、整圈亮边、内侧暗棱。
- **真机**：**尚未验证**（本机真机 A7QK023224004563 本次未接入）。按工程铁律，**真机截图才作数**，故本轮标记为"未通过（未验）"，不得当作已验收。
- 待真机核对的项：① 磨砂 12dp 在 1080p 真机是否偏糊；② `chromaticAberration` 色散是否过艳；③ 60/120Hz 下 `drawBackdrop` 的掉帧情况。

---

## 【重要存档备注（用于下一轮记忆）】

1. **环境背景**：彩色对角渐变（紫→蓝→青→绿→黄→橙→粉）+ 柔边光斑 + **白网格（粗且疏）**；背景含可滚动卡片列表（用于检验⑤滚动同步）。
2. **平台信息**：模拟器 `emulator-5554` = Pixel_7_API35 / Android 15；构建 JBR **Java 25**；Gradle **9.6.0**；AGP **9.4.1**；Compose BOM **2026.09.00**；`backdrop 2.0.1`；compileSdk **37** / minSdk **33**。
3. **重要约束 / 踩坑**：
   - **背景必须有高频细节**，否则磨砂无物可糊、折射无物可弯 → 玻璃渲染成"平色块"（本轮为此返工一次，误判为"模拟器不支持 AGSL"，实为我把背景画错）。
   - **官方 API 早已内置** vibrancy / Highlight / Shadow / **InnerShadow** / `lens(chromaticAberration)` —— 上一轮"手搓"的正是这些，属于没先读官方 API 的返工。
   - Gradle **默认 HTTP 无超时**，一次连接卡住会"假装在跑"（最长 27 分钟零输出）→ 必须设 `systemProp.org.gradle.internal.http.*`。
   - **官方坐标**：AndroidLiquidGlass 本体在 **JitPack**（`com.github.Kyant0:AndroidLiquidGlass`）；底层库 `io.github.kyant0:backdrop` 在 **Maven Central**（`io.github.meyouoss` 不存在）。
   - AGSL 源码为 **Apache-2.0（Copyright 2025 Kyant）**，移植进 App 必须保留许可声明。
4. **待解决遗留问题**：
   - ~~路线 B（View + `RuntimeShader` 零迁移移植）**未实测**，仅有设计依据~~ → **已于同日实测跑通，见文末「补充验证」**
   - 引擎 A（Flutter）玻璃底部有**镜像文字残影**，三组对照已排除"自身 child 被采样"，真因未定。
   - 官方版 APK 未 strip（`llvm-strip` 报 Permission denied），28636 KB；发布需 `--split-per-abi` 或换构建环境。
   - 本工程若要上 Compose，需从 `build.ps1` 免 Gradle 管线迁到 Gradle（版本矩阵见规范 §2.1）。

---

## 【下一轮基线快照（本轮修改完成后的最终质感参数）】

**目标形态（折射观察窗，已锁定）**
- 形状半径 = **26dp**；尺寸 = **168×168dp**
- 磨砂 blur = **12dp**；玻璃白度 tint = **White 20%（0x33FFFFFF）**
- 折射环带 `refractionHeight` = **14dp**；折射幅度 `refractionAmount` = **22dp**（≈1.5:1）
- 色散 `chromaticAberration` = **true**；`depthEffect` = **false**
- effects 顺序 = **blur → vibrancy → lens**（锁死）
- highlight = **Highlight.Default**；shadow = **Shadow.Default**；innerShadow = **InnerShadow.Default**
- 背景要求 = 白网格 **10dp 粗 / 160dp 间距** + 柔边光斑

**本工程自研引擎（同步基线）**
- 磨砂档 downscale = **5**（`GLASS_DS[1]`）
- 玻璃白度 tint = 0x42FFFFFF（保持）
- 受光环 = **7 停**（0x9C/0x4A/0x26/0x1A/0x24/0x52/0x8C），宽 **1.2dp**；内侧暗棱 = 0x1FFFFFFF / 宽 ×2.6
- 镜面高光 = 6 停（0x3A→0x12→0→0→0x0E→0x24）
- 噪点 = 本体 alpha 0–96 / `GRAIN_ALPHA` **46**
- 阴影 = 色 0x40000000 / blur **15dp** / off **6dp** / ins 3dp / pad 23dp
- 背景网格 = **150px 间距 / 10px 粗**

---

## 【补充验证（2026-10-08 同日）】路线 B 实测 → **用户验收不通过，已否决**

**用户要求**：「b路线是用另一种方式吗？没看懂，你可以也试下我看看效果」→ 遂落地实测。

**做了什么**：新增 `glassdemo/src/com/dsh/glassdemo/ShaderGlassActivity.java`（**仍走 `build.ps1` 免 Gradle 管线**，包名 `com.dsh.glassdemo`，APK 56.4 KB）。
页面把两种实现放在**同一背景、同一份模糊、同一白度与亮边**下同屏 A/B，**只差折射实现**：
- 左：**路线 B** —— 官方 AGSL 着色器（`RoundedRectRefractionWithDispersionShaderString` 原文），经 `RenderNode.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"))` 生效
- 右：**现有方案** —— 本项目软件 lens（`Frost.applyLens`）

**技术结果：✅ 可行（模拟器）** —— 页内状态行 `RuntimeShader 就绪`，左侧折射平滑、四角有色散彩边。

**❗ 用户验收结果：❌ 不通过（2026-10-08）**
> 用户原话：「效果很差，不要用这个方案，还是刚才那个方案」

**处置**：
1. **路线 B 全线弃用**（规范 §3 已把该节改为 `❌ 已被用户否决`，内容折叠为存档，禁止采用）。
2. 折射效果**一律走路线 A（官方 Compose `backdrop` API）** —— 即用户认可的"折射观察窗"那套实现。
3. 规范 §3 新增 **A2 落地方式**：`ComposeView` + `rememberCanvasBackdrop`（**零整页迁移**，把抓到的画面喂给官方玻璃）——
   机制来自库源码核对（`CanvasBackdrop` / `Backdrop.isCoordinatesDependent = false`），"平移对齐"一步在路线 B 已实测；
   ⏳ 但 **A2 组合本身尚未端到端实测**，落地前先做一版验证页。
4. `ShaderGlassActivity` 与 `routeB_*.png` 保留为**追溯存档**，不作为推荐路径。

**新增待办**
- 路线 A 落地 App 的两件事：① 引入 Gradle + Compose（版本矩阵见规范 §2.1）② 抓图按帧刷新（滚动跟随）。
- A2 联调验证页（Java 页面 + ComposeView + CanvasBackdrop）。

---

## 【增补（2026-10-08 同日）】参数调整通道落地

**用户要求**：「你有没有规范各种参数，后期如果涉及到玻璃胶囊质感调整，磨砂度，圆角啥的这些优化有没有途径调整」

**回答**：参数已规范（规范 §1.1 Token 表 / §1.2 环境 Token / §7.1 三层边界），但**调整通道缺一块** —— 官方 API 版此前没有实时调参页（现工程只有自研引擎的 `GlassLabActivity`）。本轮补齐：

1. **新增实时调参页**：`D:\glass-compose\app\src\main\java\com\dsh\glasscompose\GlassTunerActivity.kt`
   （包名 `com.dsh.glasscompose`，入口 `adb shell am start -n com.dsh.glasscompose/.GlassTunerActivity`）
   - 预览：方形观察窗 + 玻璃胶囊两块，拖滑杆**即时**生效
   - **14 个旋钮**：圆角 / 磨砂 / 白度 / 折射环带 / 折射幅度 / 色彩补偿(饱和·亮度) / 亮边宽度·强度 / 外阴影强度 / 内侧暗棱强度 / 色散 / 向心法线 / 内侧暗棱开关
   - 底部「当前参数（照抄即可）」等宽文本 → 直接抄回 `RefractionWindowTokens`
   - 验收：✅ 模拟器实跑通过（`shots/official_tuner.png`）
2. **规范新增 §7「参数调整通道」**：三层边界表 + **症状→旋钮**对照表（含建议范围与副作用）+ 两条途径 + 五步流程
3. **两个官方实现细节写进规范 §7.5**：
   - `vibrancy()` == `colorControls(saturation = 1.5f)` 的固定版 → 要**调**色彩补偿强度必须改用 `colorControls(...)`
   - effects 顺序不可换：`blur → colorControls/vibrancy → lens`

**下一轮基线快照（不变）**：即本轮报告的锁定 Token（圆角 26dp / 磨砂 12dp / 白度 20% / 折射 14+22dp / 色散 on / 内侧暗棱 on）。

---

## 【缺陷修复（2026-10-08 同日）】大圆角导致"背景被虚化"（用户实测报障）

**用户原话**：「现在模拟器上是你弄得这些测试吗？我发现一个问题，我开大圆角然后你弄得假背景就出来了，背景为啥也直接变形虚化了！！」

**定位**：前台是 App 自己的 `com.dsh.mobile/.glass.GlassChatPageActivity`（路线 A 已落地的页面）。
根因在**调用处**：底部输入胶囊写成 `RefractionWindow(radius = 999.dp)` —— 非法圆角（合法上限 = 短边/2 = 28dp）。

**复现对照**（同一背景、同 blur=30，我的官方版调参页）：
| radius | 结果 |
|---|---|
| 26dp（合法） | 玻璃外背景**锐利**，网格线清晰贴到玻璃边 |
| 90dp（> 短边/2） | 玻璃周围出现**柔化区**，节点矩形内网格线被洗掉 → 就是用户看到的"背景被虚化" |

**机制**：圆角半径被官方库作为 `cornerRadii` uniform 传给 AGSL；`r > 短边/2` 时圆角矩形退化成极限态，
**形状不再覆盖玻璃节点** → 模糊/折射**外溢到整块节点矩形**。（不是背景图层被改。）

**修复（3 处，已编译并在模拟器验证）**
1. `RefractionWindow.kt`：内置 `BoxWithConstraints` **自动夹取 radius ≤ 短边/2**（写多大都安全）
2. `RefractionWindow.kt`：新增 **`RefractionCapsule()`**（圆角自动 = 短边/2，即真胶囊；高度变更不失效）
3. `GlassChatPageActivity.kt`：底部胶囊 `radius = 999.dp` → **`RefractionCapsule(...)`**

**验收（模拟器，App 实跑）**：✅ 通过 —— `shots/user_chatpage_fixed.png`
顶栏玻璃正常折射/磨砂；**底部胶囊形态正确，且其周围极光背景完全未被虚化变形**。

**新增待办**
- 其它页面（`GlassDevicePageActivity` / `GlassEvidenceActivity`）需同样排查是否还有 `radius` 超限的调用（建议全局搜 `radius =`）。
- 真机验证仍待做。
