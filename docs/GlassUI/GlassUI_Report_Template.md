# GlassUI迭代报告 | 版本号：V0.2
日期：
调试组件：FrostedPanelView（本项目对应 GlassCapsule）

【基线快照（本轮开始前的原始质感参数）】
- 磨砂档 downscale = （Ui.GLASS_DS[Ui.glassLevel()]）
- 玻璃白度 tint = 0x__________
- 白棱 hairline = 0x__________
- 折射环带 refrHeight = max(28dp, 短边 × ____%)
- 折射幅度 refrAmount = max(38px, 环带 × 1.35)
- 受光环 RIM = 0x7AFFFFFF / 0x0CFFFFFF / 0x50FFFFFF
- 噪点 GRAIN_ALPHA = 10
- 阴影 = 色 0x__________ / blur 11dp / off 5dp / ins 3dp / pad 18+5dp

【本轮原始问题描述】
> 用户输入：

【差异分类 & 量化差异清单】
1. A质感 / B布局 / C环境： 原=xxx → 目标=xxx
2. A质感 / B布局 / C环境： 原=xxx → 目标=xxx

【本轮代码变更摘要】
- 修改文件：
- 修改内容（最小变更，≤2 个质感数值）：

【真机验收结果】（真机截图为准，IDE/模拟器仅参考）
☐ 通过 ✅ / ☐ 未通过
验收结论：

【重要存档备注（用于下一轮记忆）】
1. 环境背景：当前调试背景描述（极光图 / 纯色 / 滚动内容）
2. 平台信息：Android 版本、测试设备（本机真机 A7QK023224004563 · PGT_AN10）
3. 重要约束/踩坑记录：
4. 待解决遗留问题：

【下一轮基线快照（本轮修改完成后的最终质感参数）】
- 磨砂档 downscale =
- 玻璃白度 tint =
- 白棱 hairline =
- 折射环带 / 幅度 =
- 阴影 =