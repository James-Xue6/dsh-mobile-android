# GlassUI迭代报告 | 版本号：V1.2（路线 B 组件落地）
日期：2026-10-08 · 调试组件：**GlassRefractionView（新增，生产组件）**

【基线快照（本轮开始前 = V1.1：路线已定 B，但生产侧无组件）】
目标形态 Token（规范 §1.1）：radius 26dp · tint 0x33FFFFFF · blur 12dp · refrH 14dp · refrAmt 22dp · CA on · depthEffect off

【本轮原始问题描述】
> （承接目标）把路线 B 落成生产组件，替代 CPU 软件 lens。

【差异分类 & 量化差异清单】
- A质感：折射实现 CPU 软件 lens → **官方 AGSL 着色器（含色散）**；其余 Token 不变。
- B布局：无（radius/尺寸由调用处传）。
- C环境：无（背景仍按 §1.2 要求）。

【本轮代码变更摘要（最小新增，未触碰任何现有页面）】
- **新增** `src/com/dsh/mobile/proto/GlassRefractionView.java`（约 220 行）：
  · 内嵌官方 AGSL 折射着色器原文（Apache-2.0 · Copyright 2025 Kyant，**许可声明已随源码保留**，见类注释）
  · `RenderNode` + `RenderEffect.createRuntimeShaderEffect(shader, "content")` 驱动
  · 抓取：复用 `Frost.capture(...)`，**refrAmount=0 → 只做 blur（+vibrancy）**，等价于规范的 `blur → vibrancy → lens` 顺序
  · 抓取边距 = `ceil(refrAmount_dp × density) + 2`，保证边缘向外采样取到真实内容（不出现空区/白带）
  · 抓取源上提到**应用根容器**（与 `FrostedPanelView` 同约定，保证糊到挂在根上的环境背景）
  · 绘制：`drawRenderNode` + tint 20% + **受光环 `FrostedPanelView.rim()`** + 噪点 `grain(46)`（与本工程其它玻璃同一套）
  · **`SDK_INT < 33` → `isSupported()==false` 回落 `FrostedPanelView`**（规范 §5 自检第 8 条）；运行期异常也自动降级
- 未改 `Frost.java` / `FrostedPanelView.java`（组件本体锁定）

【验证】
✅ 编译通过（`build.ps1`，Platform=android-36，含 RuntimeShader API）；产物 `dist/dsh-mobile.apk` 1590.6KB
⏳ 未接入任何页面 → 尚无该组件的渲染截图（下一轮接入设备页首个玻璃件后取证）
⏳ 真机未接入（A7QK023224004563 不在 adb devices）

【重要存档备注】
1. 构建平台是 **android-36**，因此 AGSL/RuntimeShader 可直接编译；`minSdk=26` 只需运行时分支（本类已做）。
2. 新增组件与既有 `FrostedPanelView` **共用** `Frost`（抓取/模糊）与 `rim/grain`（受光/噪点），所以"同一套玻璃"这点在代码层成立。
3. 下一轮：把 `GlassRefractionView` 接到设备页**第一个**玻璃件（设备卡）做 A/B，按规范 §5 八条自检 → 再全量替换。

【下一轮基线快照】
Token 同 V1.0；新增生产组件 `GlassRefractionView`（AGSL，API33+ / <33 回落）