# GlassUI迭代报告 | 版本号：V0.2
日期：2026-10-07
调试组件：FrostedPanelView（对应规范里的 GlassCapsule）

【基线快照（本轮开始前的原始质感参数）】
- 磨砂档 downscale = 8（Ui.GLASS_DS={4,5,6,8}，glass_level 默认 3）
- 玻璃白度 tint = 0x42FFFFFF（设备卡/添加卡/图标砖/对话卡体；规范默认曾是 0x59）
- 白棱 hairline = 0x1EFFFFFF
- 折射环带 refrHeight = max(28dp, 面板短边 × 22%)
- 折射幅度 refrAmount = max(38px, 环带 × 1.35)
- 受光环 RIM = 0x7AFFFFFF / 0x0CFFFFFF / 0x50FFFFFF
- 噪点 GRAIN_ALPHA = 10
- 阴影 = 色 0x33000000 / blur 11dp / off 5dp / ins 3dp / pad 18+5dp
- 抓取 padding = max(20dp, 环带 × 2.35)

【本轮原始问题描述】
> 用户：玻璃质感有点差，边缘过渡变形应该跟着框变大，不然很假；随后：边缘的过渡没了，很硬的变形边缘。

【差异分类 & 量化差异清单】
1. A质感：折射环带 **固定 28dp → max(28dp, 短边×22%)**（幅度同比例 ×1.35）。原因：72dp 小件占 39% 好看，230dp 大卡只占 12% 显假。
2. A质感：抓取 padding **max(模糊, 环带) → max(模糊, 环带×2.35)**。原因：位移(1.35×环带)超过溢出区 → 采样越界硬回退 → 边缘一条硬边、没有过渡。
3. A质感：材质一致性 —— 对比页用的是 tint 0x42，App 里落的是 0x59（规范默认）→ 已统一为 0x42（待用户最终拍板 A/B/C/D）。

【本轮代码变更摘要】
- 修改文件：`proto/FrostedPanelView.java`（lensRefrHeight/lensRefrAmount）、`proto/Frost.java`（padFor）、`ui/DeviceHubView.java` + `ui/Ui.java`（tint 统一 0x42）
- 修改内容（最小变更）：见上 3 条，各自都是单点数值/公式改动，组件绘制顺序未动。

【真机验收结果】（真机截图为准）
☐ 通过 ✅ / ☐ 未通过
验收结论：⏳ 待用户在真机确认（本机真机 3 次 `adb install` 被 `INSTALL_FAILED_ABORTED: User rejected permissions` 拦下，需用户在手机弹框点「允许」）

【重要存档备注】
1. 环境背景：极光底图（`res/drawable-nodpi/aurora_bg.jpg`，由 `Ui.AmbientDrawable` 铺在根容器）；测试页另有"多彩对话栏 + 上层固定玻璃"结构用于横比。
2. 平台信息：真机 PGT_AN10（A7QK023224004563）；模拟器 Pixel_7_API35。
3. 重要约束/踩坑：① 抓取源必须 targetRoot 且补偿=0；② 整页共享抓取必须隐藏全部在册玻璃件；③ padding 必须容下 1.35× 环带位移；④ 滚动要显式 invalidate 悬浮件；⑤ 抓取期间停画外投影。
4. 待解决遗留问题：① 白度 A/B/C/D 待用户拍板；② 22% 环带比例是否合适待确认；③ "背景残留"现象未定位（等用户指明是滞后/边缘飘色/整页旧背景）。

【下一轮基线快照】
- 磨砂档 downscale = 8
- 玻璃白度 tint = 0x42FFFFFF
- 白棱 hairline = 0x1EFFFFFF
- 折射环带 / 幅度 = max(28dp, 短边×22%) / ×1.35
- 阴影 = 0x33000000 · blur 11 · off 5 · ins 3 · pad 18+5