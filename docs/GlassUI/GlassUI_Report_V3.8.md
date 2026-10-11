# GlassUI 迭代报告 V3.8 | 2026-10-08

> 目标轮次：**⑤ 设置页补全**（把正式版 `ui/SettingsView` 的全部真实项搬进 Compose 设置页）
> 上轮：V3.7（抽屉/会话列表 Compose 化）　**验收：模拟器**

---

## 1. 本轮做了什么

把 `GlassSettingsActivity` 从"只有深色模式 + 配色"的半成品，补成**与正式版设置页同项**的完整页；
UI 本体抽到新文件 `glass/GlassSettings.kt`（`GlassSettingsState` + `GlassSettingsHost` + `GlassSettingsScreen`），
Activity 退化成薄壳（自己当宿主）。

### 1.1 项清单（逐条对齐 `SettingsView`）
| 分区 | 项 | 真值来源 / 落点 |
|---|---|---|
| 连接 | 内网地址 / 公网地址 / 令牌 / 设备名 | `Store.lanUrl/wanUrl/token/deviceName`（**真读**，可编辑） |
| 连接 | 连接方式三分段（自动/只用内网/只用公网） | `Store.netMode`（`RoutePolicy`） |
| 连接 | 保存并连接 / 扫码配对 / 粘贴配对 / 断开 | `Host.onConnect/onScanQr/onPastePairing/onDisconnect` |
| 外观 | 深色模式三分段（跟随系统/浅色/深色） | `Store.themeMode` + `Ui.applyTheme` → **真写 + 立即生效** |
| 外观 | 配色 6 档（极光…素雅） | `Store.ambientPalette` + `Ui.setAmbientPalette` → **真写 + 立即生效** |
| 外观 | 玻璃磨砂度 4 档 | `Store.glassLevel` + `Ui.setGlassLevel` → **真写** |
| 行为 | 显示模式（完整 / 简洁） | `Store.displayMode` → **真写** |
| 行为 | 允许截屏开关 | `Store.allowScreenshot` → **真写** |
| 行为 | 允许自签名证书开关 | `Store.insecureTls` → **真写** |
| 关于 | 关于文案 / 更新提示 / 检查更新 / 反馈 / 诊断 / 状态行 | 宿主推文本 + `Host.onCheckUpdate/onOpenFeedback` |

小件：`FieldRow`（自绘 `BasicTextField`，不吃 Material 主题）、`Segmented`（玻璃底三分段）、
`SwitchRow`（自绘 iOS 开关）、`PrimaryButton`/`GhostButton`（胶囊玻璃 + 语义渐变）、`SectionCard`（玻璃卡）。

### 1.2 独立切片的诚实边界
`state.hasHost=false` 时页面底部会**明说**：「当前是独立切片：连接类动作只会提示，真正保存/重连在正式版宿主里」，
且 `保存并连接/扫码/粘贴/断开/检查更新/反馈` 一律给**明确提示**（不假装生效）；
能自己写的（主题/配色/磨砂度/显示模式/截屏/自签证书）**全都真写 Store**。

## 2. 验收（模拟器）

| 取证件 | 结果 |
|---|---|
| `evidence/v380/01_settings_top.png` | 顶栏三件式（‹圆 + 玻璃条「设置 / 连接·外观·行为·关于」）；**连接分区四项全是真值**（`ws://192.168.1.50:8765` / `testtok123` / 设备名 `Google sdk_gphone64_x86_64`）、连接方式分段、「保存并连接」+ 三颗胶囊按钮；外观分区：深色模式分段 + **配色 6 档**（极光选中）+ 磨砂度 4 档冷启动可见 |
| `evidence/v380/02_settings_bottom.png` | 滚动到下半（行为：显示模式/两个开关；关于：检查更新/反馈/诊断/状态行） |

## 3. ⑤ 剩余 / 下一步

1. **把设置页挂进 MainActivity 宿主**（像设备页/对话页那样）：`SettingsView` 是 `final` 类，按其 API
   （13 个 setter + `Host` 的 15 个动作）做**同名双写包装**，`USE_GLASS_SETTINGS` 一行回退。
   接上之后 `保存并连接 / 扫码配对 / 粘贴配对 / 断开 / 检查更新 / 反馈 / 诊断` 就**全都是真流程**了。
2. ⑥ **各类弹窗统一**：建议先抽一个统一的 Compose 弹窗骨架（底部 sheet / 居中卡 + 玻璃卡 + 圆钮 + 列表行），
   再逐个搬旧 sheet（添加设备选择、附件选择、模型/思考/权限/生成物/用量/工作区面板、审批/提问、重命名/归档、诊断弹窗）。
3. 扫码页 / 交付物预览页。
4. 真机复验（仍卡在系统安装确认框，需用户点一次「安装」）。
