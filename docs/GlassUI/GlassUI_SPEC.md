# GlassUI_SPEC.md
```markdown
# GlassCapsule 液态玻璃组件开发&迭代规范 SOP
> 适用：Jetpack Compose Android 液态玻璃胶囊UI，交给AI Agent执行，所有任务必须遵守本文档，优先级高于任何临时需求。

## 0. 核心原则
1. **组件本体锁定原则**：GlassCapsule 内部绘制、图层顺序、蒙版、backdropBlur、高光绘制逻辑【永久锁定，禁止重构、禁止修改】。
2. **参数分离原则**：
   - 质感参数（模糊半径、高光粗细、透明度、阴影）：统一放在 `GlassQualityTokens.kt`，全局复用。
   - 布局参数（圆角、内边距、尺寸）：通过函数入参传入 GlassCapsule，不在组件内部硬编码。
3. **差异分类原则**
   - A类【质感差异】：高光粗细、blur强度、阴影、透明度 → 修改 GlassQualityTokens.kt
   - B类【布局差异】：圆角、padding、卡片大小 → 修改组件调用处入参，不动组件本体、不动Token
   - C类【环境差异】：背景图层变化导致玻璃观感改变 → **不修改任何代码**，只输出文字说明，告知用户属于backdropBlur原生特性。
4. **评估基准**：**以Android真机截图作为唯一判定标准，Android Studio预览仅作参考，不作为视觉验收依据**。
5. **最小变更原则**：单次迭代最多修改2个Token数值；输出最小diff片段，禁止重写整个组件源码。
6. **迭代存档原则**：每一轮调试必须生成《GlassUI迭代报告》，保存基线参数；新迭代开始前，必须读取上一轮报告基线，禁止无依据重置参数。

## 1. 项目文件结构约定
```
ui/
├─ tokens/
│  └─ GlassQualityTokens.kt   # 【唯一允许Agent修改的质感参数文件】
└─ components/
   └─ GlassCapsule.kt         # 【锁定！禁止修改内部绘制逻辑】
```

### GlassQualityTokens.kt 示例（质感固定参数组）
```kotlin
package com.xxx.ui.tokens
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GlassQualityTokens {
    // 液态玻璃基础模糊
    val glassBlurRadius: Dp = 28.dp
    // 顶部高光描边
    val highlightStrokeWidth: Dp = 1.2.dp
    val highlightAlpha: Float = 0.22f
    // 底部阴影
    val shadowBlur: Dp = 16.dp
    val shadowAlpha: Float = 0.12f
}
```

### GlassCapsule.kt 接口约定（内部实现锁死，仅入参可调整）
```kotlin
package com.xxx.ui.components
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun GlassCapsule(
    modifier: Modifier,
    radius: Dp,       // 布局参数：圆角，调用方传入
    content: @Composable () -> Unit
) {
    // ========= 【内部绘制代码锁定，禁止改动！】 =========
    // clip → backdropBlur → drawBehind高光 → shadow 顺序固定，不能调换
    // 内部读取 GlassQualityTokens 里面的质感参数
    // =================================================
}
```

## 2. 标准迭代工作流（每一轮修改严格按顺序）
### Step0 前置读取（每轮开始）
读取上一轮《GlassUI迭代报告》，加载基线Token参数，读取历史备注、踩坑记录。

### Step1 素材准备（由用户提供）
1. 局部组件参考截图（只截取当前调试组件，不要整张页面，减少干扰）
2. 当前真机渲染截图（**必须连同组件背后的背景一起截图**）
3. 相关源码：GlassQualityTokens.kt + GlassCapsule.kt + 当前页面调用代码
4. 用户量化问题描述（禁止“不好看/质感差”，必须量化，示例：高光从1.2dp改为0.7dp，alpha从0.22调整到0.16）

### Step2 Agent分析阶段
1. 并排对比【参考图】和【当前真机截图】
2. 区分A/B/C三类差异，输出《量化差异清单》
   - 清单内容：问题描述、当前值、目标值、差异类型(A/B/C)
3. C类环境差异直接写说明，**不生成任何代码改动**

### Step3 代码变更
- A类质感问题：仅修改 `GlassQualityTokens.kt`
- B类布局问题：仅修改页面**调用GlassCapsule的入参**，不碰GlassCapsule本体、不修改Token
- C类环境差异：无代码变更，输出说明

### Step4 交付物（每次迭代必须输出这4样）
1. 《量化差异清单》
2. 最小代码diff（只输出改动片段，禁止完整重写大段代码）
3. 验证提示：告知用户编译、真机截图，再次进入下一轮迭代
4. 《GlassUI迭代报告》存档文档

## 3. 跨页面复用专项流程（解决：组件单独效果好，放到其他页面效果崩）
当把GlassCapsule复用到新页面时，Agent必须执行下面校验：
1. 对比新页面背景 和 原组件调试背景。背景不一致，提前预警：`背景图层改变，backdropBlur玻璃观感会发生变化，属于平台原生特性，不修改质感Token`
2. 校验Modifier顺序：`clip → backdropBlur → drawBehind高光 → shadow`，顺序一旦颠倒效果直接失效
3. 检查zIndex、外层clip，确认没有被其他图层遮挡
4. 核对传入的radius、modifier入参，和原型保持一致
5. 生成调用代码，**严禁修改GlassCapsule本体、GlassQualityTokens**
6. 输出提示：编译后真机截图对比验收

## 4. 禁止行为清单（红线，违反直接终止本轮任务）
❌ 禁止重构GlassCapsule内部绘制逻辑、图层、蒙版、backdropBlur实现
❌ 禁止在页面调用代码里写死blur/alpha/shadow这类质感硬编码数字
❌ 禁止单次迭代修改超过2个Token参数
❌ 禁止以IDE预览截图作为视觉验收标准
❌ 禁止为了适配不同背景，盲目调整质感Token
❌ 禁止一次性生成整页UI，必须按独立组件逐个调试
❌ 禁止无视历史迭代报告基线，擅自重置Token全部数值
❌ 禁止省略迭代报告，不保存本轮调试基线

## 5. 迭代报告规范 & 记忆存档机制
### 5.1 规则
1. **每一轮迭代，必须输出一份《GlassUI迭代报告》**，作为本轮交付物，和diff、差异清单一起输出，不能省略。
2. 报告独立保存，版本号递增；每次新开对话，优先将上一轮报告提供给Agent，作为基线记忆。
3. Agent本轮工作前，必须阅读历史迭代报告，读取基线参数、历史踩坑、验收结论，禁止无视历史直接修改。
4. 基线快照记录本轮前后Token数值，后续所有修改基于这份基线，不能凭空重置。

### 5.2 《GlassUI迭代报告》固定模板
```markdown
# GlassUI迭代报告 | 版本号：Vx.x
日期：
调试组件：GlassCapsule
【基线快照（本轮开始前的原始Token）】
val glassBlurRadius: Dp =
val highlightStrokeWidth: Dp =
val highlightAlpha: Float =
val shadowBlur: Dp =
val shadowAlpha: Float =

【本轮原始问题描述】
> 用户输入：

【差异分类 & 量化差异清单】
1. A质感 / B布局 / C环境： 原=xxx → 目标=xxx
2. A质感 / B布局 / C环境： 原=xxx → 目标=xxx

【本轮代码变更摘要】
- 修改文件：
- 修改内容（最小变更）：

【真机验收结果】
☐ 通过 ✅ / ☐ 未通过
验收结论：

【重要存档备注（关键！用于下一轮记忆）】
1. 环境背景：当前调试背景描述（背景图片/纯色/滚动内容）
2. 平台信息：Android版本、测试设备
3. 重要约束/踩坑记录：
4. 待解决遗留问题：

【下一轮基线快照（本轮修改完成后的最终Token）】
val glassBlurRadius: Dp =
val highlightStrokeWidth: Dp =
val highlightAlpha: Float =
val shadowBlur: Dp =
val shadowAlpha: Float =
```

### 5.3 版本命名规则
V0.1：初始基线（组件刚完成，第一次调好的基准参数）
V0.2：第1次参数微调
V0.3：第2次微调
V1.0：组件定稿，质感Token冻结，仅做页面组装

## 6. 给Agent的前置固定提示（每次对话开头自动加载）
> 你现在启用【GlassCapsule液态玻璃组件SOP】，严格遵守本规范所有条款，优先级高于任何临时需求。
> 第一步读取历史迭代报告，以报告基线快照作为当前Token基准；区分A/B/C三类视觉差异；GlassCapsule内部代码锁定，仅允许修改GlassQualityTokens.kt质感参数，布局参数通过入参传入；输出量化差异清单+最小diff+迭代存档报告；以真机截图作为唯一验收标准。跨页面复用前必须校验背景、modifier顺序、图层遮挡。
```

---
## 使用方法
1. 全选上面全部文本，复制
2. 在电脑新建文本文档，粘贴，保存，重命名为 `GlassUI_SPEC.md`
3. 每次开启Agent对话，**先粘贴这份SOP，再粘贴上一轮迭代报告，最后发截图+代码+需求**

> 后续每一轮调试，Agent输出的迭代报告，单独新建md文件保存，命名：`GlassUI_Report_shturl.`、`GlassUI_Report_V0.2.md` 递增。

需要我单独把【迭代报告模板】单独拆成一份独立的 `GlassUI_Report_Template.md` 给你吗？