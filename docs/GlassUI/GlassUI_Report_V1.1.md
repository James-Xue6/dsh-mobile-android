# GlassUI迭代报告 | 版本号：V1.1（路线 B 落地第 1 轮）
日期：2026-10-08 · 调试组件：ShderGlassActivity.GlassAbView → 待抽成可复用组件

【基线快照（本轮开始前 = 图纸 V1.0 锁定的目标形态）】
radius 26dp · size 168dp · blur 12dp · tint White 20%(0x33) · refrH 14dp · refrAmt 22dp
chromaticAberration=true · depthEffect=false · effects 顺序 blur→vibrancy→lens
highlight/shadow/innerShadow = *.Default · 背景 = 白网格 10dp 粗/160dp 间距 + 光斑

【本轮原始问题描述】
> 用户：我已经测试出来控件的玻璃质感了，把 app 的 ui 按照这个液态玻璃的做法整体重做吧。

【本轮结论（关键，改变了执行路线）】
1. **路线 B 已被你们实证跑通**：`glassdemo/src/com/dsh/glassdemo/ShaderGlassActivity.java` 里就是官方 AGSL 折射着色器（含色散），
   用 `RenderNode.setRenderEffect(RuntimeShader)` 直接画在现有 View 上 —— **零 Compose / 零 Gradle 迁移**。
   本轮我在模拟器（Android 15 / SDK 35）重跑并取证：`DQ1-路线B实证.png`。
   A/B 同屏对照结论（页面自带文案）：**同背景 / 同模糊 / 同白度与亮边，只差折射实现**；
   左（AGSL）边缘更平滑、四角有色散彩边；右（现有软件 lens）边缘更早出现波浪状斑块。
2. 因此不必走路线 A（Compose 迁移 + 换构建链），**以路线 B 为量产路线**：
   把 `GlassAbView` 的路线 B 部分抽成通用 `GlassRefractionView`（API 33+ 用 AGSL；
   `SDK_INT < 33` 回落现有 `FrostedPanelView` 软件路径，满足规范 §5 自检项 8）。
3. 许可（规范 §3 强制）：AGSL 源码 Apache-2.0 / Copyright 2025 Kyant，移植进 App **必须保留许可声明**。

【差异分类 & 量化差异清单】
- A质感：折射实现从「CPU 软件 lens」换为「官方 AGSL 着色器（含色散）」——这是本轮唯一实质差异（blur/tint/亮边/暗棱/阴影沿用 V1.0 锁定值）
- B布局：无（各元素圆角/尺寸仍由调用处传）
- C环境：无（背景按 §1.2 要求具备高频细节）

【本轮代码变更摘要】
- 仅取证，无生产代码变更（生产侧尚未接入选型组件）
- 新增证据：`evidence/liquid-20261007/DQ1-路线B实证.png`；`glassdemo/dist/glass-demo.apk` 已装模拟器

【真机验收结果】
☐ 通过 / ☐ 未通过 —— ⏳ 真机未接入（A7QK023224004563 不在 adb devices）；模拟器已跑通（SDK 35 ≥ 33）

【重要存档备注】
1. 路线 B 的调用要点（从 demo 读出）：`RuntimeShader(shaderSrc)` + `setFloatUniform(size/offset/cornerRadii/refractionHeight/refractionAmount/depthEffect/chromaticAberration)` + `setInputShader("content", BitmapShader(已模糊的抓取位图))`。
2. 抓取位图（content）由现有 `Frost` 提供（降采样 + 高斯 + vibrancy ColorMatrix），**顺序等价**于规范的 blur→vibrancy→lens。
3. 待办（下一轮）：抽组件 → 在 `GlassTunerActivity`/`glassdemo` 里对照自检清单 8 条 → 再逐元素替换设备页。

【下一轮基线快照】
同 V1.0 目标形态（Token 不变），折射实现 = AGSL（API 33+）/ Frost 软件（<33）