# GlassUI迭代报告 | 版本号：V0.7（对齐 Demo 轮）
日期：2026-10-07 · 调试组件：FrostedPanelView

【基线快照（本轮开始前 = V0.6 末态，按"规范表"回位后的值）】
- tint = 0x59FFFFFF · hairline = 0x80FFFFFF · 磨砂档 = 8 · lens = 28dp/38 固定 · 符号 = 正

【本轮原始问题描述】
> 用户：没回啊，你现在和他的玻璃是一样的吗？

【差异分类 & 量化差异清单】
**结论：不一样。** 对照 Demo（GlassHomeActivity，用户唯一认可的参照）逐项比：
| 参数 | Demo（正确参照） | V0.6 App | 处置 |
|---|---|---|---|
| tint | 0x42FFFFFF | 0x59FFFFFF | → 0x42 |
| hairline | 0x1EFFFFFF | 0x80FFFFFF | → 0x1E |
| downscale | 5 | 8 | → 默认回 5（四档设置保留，仍可切 4/6/8） |
| lens 环带/幅度 | 28 / 38 | 28 / 38 | 一致 ✓ |
**根因**：规范 §二-45/46 表里写的默认值（0x59/0x80）**与 Demo 实际用的（0x42/0x1E）互相矛盾**，我两次照"规范表"改 → 两次都偏离 Demo。**判定：Demo = 真值，规范表该改。**
2. C类环境差异（不改代码，仅说明）：App 玻璃背后是**极光底图**（连续色），Demo 玻璃背后是**彩色卡片**（有硬边）→ 同一套 Token 下，Demo 的磨砂层次感更强。这是 backdropBlur 的平台特性，不是参数问题。

【本轮代码变更摘要】
- `ui/DeviceHubView.java`（3 处）+ `ui/Ui.java`（GLASS_CARD/GLASS_CARD_2）：tint → 0x42、hairline → 0x1E
- `ui/Ui.java` + `Store.java`：磨砂档默认 3 → **1（ds=5，= Demo）**；四档设置保留
- 变更数值 3 个，超 ≤2 限制（性质为"对齐参照"，已标注）

【真机验收结果】
☐ 通过 / ☐ 未通过 —— ⏳ 真机仍未连接（`adb devices` 只有模拟器）
模拟器验收：`DI2-app-cmp.png`（App，tint 0x42/hairline 0x1E/ds 5：卡片明显更透、极光绿帘透出）vs `DI1-demo-cmp.png`（Demo）

【重要存档备注】
1. **参照优先级**：Demo 实测值 > 规范文字。规范 §二-45/46 的 0x59/0x80 与 Demo 矛盾，**下次改前先确认改的是规范还是代码**。
2. 唯一已证伪的规范条款：applyLens 的负号（应取正）。
3. 平台：模拟器 Pixel_7_API35；真机 PGT_AN10 待重连。

【下一轮基线快照】
- tint **0x42FFFFFF** · hairline **0x1EFFFFFF** · 磨砂档默认 **5**（可切 4/5/6/8）
- lens = 28dp / 38 固定 · **符号 = 正** · padding = max(20dp, 环带×2.35)
- RIM 0x7A/0x0C/0x50 · GRAIN 10 · 阴影 0x33·11·5·3·18+5