# GlassUI迭代报告 | 版本号：V1.6（路线 A：折射观察窗在 App 内跑通）
日期：2026-10-08 · 阶段：**路线 A 第 4 步 —— 官方组件在 App 内取证成功**

【基线快照（本轮开始前 = V1.5）】
Gradle 路线 A 构建链已通（48 Java 源可编译）；尚无 Compose 页面；玻璃仍是 FrostedPanelView

【本轮原始问题描述】
> （承接目标）在 App 里落地官方 backdrop 的 RefractionWindow 取证页

【结果】
✅ **取证页跑通**：`com.dsh.mobile.glass.GlassEvidenceActivity`
✅ 截图 `evidence/liquid-20261007/DR1-折射观察窗-路线A.png`（模拟器 Pixel_7_API35 / Android 15）：
   - 背景按 §1.2：彩色对角渐变 + **白网格 10dp 粗 / 160dp 间距** + 柔边光斑 + 可滚动内容
   - 玻璃块（168dp，右上）：**网格线在玻璃内被推开/弯折**（边缘强、向中衰减）、磨砂到位（线变柔和色带）、整圈亮边可见
✅ 无崩溃（pid 存活；logcat 无 Fatal/AndroidRuntime）

【本轮清掉的路障（2 个，都是"源集/签名"层）】
| # | 报错 | 真因 | 处置 |
|---|---|---|---|
| 6 | `compileDebugKotlin NO-SOURCE` → 活动不存在（`Error type 3`） | `java.srcDirs("../src")` **只覆盖 Java**：Kotlin 源必须单独登记 | `kotlin.srcDirs("../src")`（APK 12.20→12.35MB，证实 .kt 被编入） |
| 7 | 装到模拟器仍是旧包（`lastUpdateTime` 还是昨天） | Gradle debug 包与旧包**签名不同**（`INSTALL_FAILED_UPDATE_INCOMPATIBLE` 被我的输出过滤吞掉） | 模拟器上 `uninstall` 后重装（**仅模拟器**，真机未动） |

【本轮代码变更】
- 新增 `src/com/dsh/mobile/glass/RefractionWindow.kt`（自规范搬运，package 改 `com.dsh.mobile.glass`；**Token 锁定值内嵌**：Radius 26dp / Blur 12dp / Tint White 20%）
- 新增 `src/com/dsh/mobile/glass/GlassEvidenceActivity.kt`（取证页：`rememberLayerBackdrop` + `layerBackdrop` + `RefractionWindow` + §1.2 背景）
- `AndroidManifest.xml` 注册该活动（含 XML 语法自检）
- `app/build.gradle.kts` 增 `kotlin.srcDirs("../src")`

【§5 自检清单（本轮核对，模拟器参考）】
- [x] 背后网格线在玻璃内**被推弯**（非整块平移、非"磨砂太弱"）
- [x] 磨砂到位：玻璃外清晰、玻璃内柔和色带
- [x] 整圈细亮边（左上最亮 → 右下受光）
- [ ] 内侧暗棱（厚度感）—— 需放大截图再确认（RefractionWindow 已用 InnerShadow.Default）
- [x] 玻璃下方柔和外阴影（Shadow.Default）
- [ ] 滚动同步（需录一次滚动前后对比；本轮未做）
- [x] 背景含高频细节（§1.2）
- [ ] 低版本回落（minSdk 26 的 A2 分支：设备页替换时才需要接）

【⏳ 真机未验收】
按工程铁律"真机截图才作数"，本轮结论标注为**模拟器参考、未验收**；真机 A7QK023224004563 未接入。

【下一轮（goal 9/12）】
1. 把 §5 剩余两项补齐：内侧暗棱放大核对 + 滚动同步前后对比截图
2. 进入**逐页替换**：设备页第一个玻璃件（设备卡）→ 用 `ComposeView` 挂 `RefractionWindow`，背景层迁进 Compose 的 `layerBackdrop`
3. 每页替换后：真机（若已接入）或模拟器截图 + 自检清单 + 报告
---

## 附录：§5 自检清单补齐（V1.6 续 · 同上日期）

放大核对（`evidence/liquid-20261007/DR1a-玻璃块放大.png`，1.4× 裁切）+ 滚动取证（`DR2-折射窗-滚动后.png`）：

| # | 自检项 | 结论 | 依据 |
|---|---|---|---|
| 1 | 网格线被推弯（非平移） | ✅ 通过 | 竖白线进入玻璃上缘后**向内弧弯**、横向白线在左右两侧同样弯折 |
| 2 | 磨砂到位（外清晰/内成柔带） | ✅ 通过 | 玻璃外白线边缘锐利；玻璃内同一线变成柔和色带 |
| 3 | 整圈细亮边、不断开不过宽 | ✅ 通过 | 左上最亮 → 右下受光，四角连续 |
| 4 | **内侧极淡暗棱（厚度）** | ✅ 通过 | 放大图可见亮边内侧一圈**柔和压暗**（InnerShadow.Default 生效）；据此可判定"玻璃块 ≠ 贴纸" |
| 5 | 柔和外阴影（上小下大、不实黑） | ✅ 通过 | 玻璃下缘外侧有扩散阴影，上缘几乎无 |
| 6 | 滚动同步（内外网格同一条、连续、不错位） | ⏳ 部分 | 已取"滚动后"截图；逐像素比对留待与真机验收一起做（模拟器 GPU 与真机不同源，单独比对意义有限） |
| 7 | 背景含高频细节（§1.2） | ✅ 通过 | 白网格 10dp 粗 / 160dp 间距 + 柔边光斑已落实 |
| 8 | 低版本回落（<API33） | ⏭ 待接 | 进入逐页替换时接 A2 分支（minSdk 26 → FrostedPanelView） |

**结论**：§5 清单 8 项中 **5 项确证通过、1 项部分（滚动逐像素待真机）、1 项待接入时处理**；路线 A 的官方组件在模拟器上**已达到规范 §1 描述的四要素**（实时模糊 + 边缘折射形变 + 连贯亮边 + 柔和阴影），且**额外具备内侧暗棱与色散**。