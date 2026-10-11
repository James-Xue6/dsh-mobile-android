# GlassUI 迭代报告 V3.10 | 2026-10-08

> 目标轮次：**⑥ 开头 —— 建"统一弹窗骨架"并转换第一个真实弹窗**
> 上轮：V3.9.4（抽屉那条边修复）

---

## 1. 关键约束（V3.9.3 已用实验证明）

玻璃要取样"背后的页面"，而 kyant 的 `layerBackdrop` **只能录到同一组合/同一窗口层**的内容。
⇒ 如果把弹窗做成 `MainActivity` 里另挂的 `ComposeView`，它取样不到页面，只能糊出一层白纱。
⇒ **结论：每屏在自己的 `GlassXxxScreen` 里渲染弹窗**，并**放在取样层之外**（与页面上其它玻璃件一致）。

## 2. 新增 `glass/GlassSheet.kt`（统一弹窗骨架）

- `GlassSheetItem(icon, label, desc)`
- `GlassSheetState { visible, title, message, items }` + `show(title, items)` / `hide()`
- `GlassSheetOverlay(backdrop, state, onPick, onDismiss)`：
  遮罩（点击关闭）→ **底部圆角玻璃面板**（`RefractionWindow`，radius 30dp）→ 抓杆 → 标题 → 菜单项
  （半透明卡 + 圆形图标 + 说明 + 右侧箭头）→ 「取消」。

## 3. 接第一个真实弹窗：设备页「＋ 添加设备」

- `GlassDeviceState` 增 `val sheet = GlassSheetState()`；`GlassDeviceHost` 增 `sheetPick(index)`
- `GlassDeviceScreen` 末尾（取样层之外）渲染 `GlassSheetOverlay(...)`
- `MainActivity.glassAddChooser()`：走本页玻璃 sheet（**`USE_GLASS_SHEET` 一行回退**到原系统 `AlertDialog`，
  旧代码保留）；`sheetPick`：0 → `onScanAdd()`，1 → `onManualAdd()`（**真实流程**）
- 顺手加了调试钩子 `--ez glass_force_device true`（启动即进设备页，便于验收）

## 4. 验收（模拟器）

| 取证件 | 结果 |
|---|---|
| `evidence/v3100/03_sheet.png` | 点「＋ 添加设备」→ **底部玻璃 sheet**：抓杆 + 标题「添加设备」+ 两项（扫码添加/手动添加，带图标与说明）+「取消」；遮罩压暗设备页且**能透过玻璃看到背后页面**；无崩溃 |
| `evidence/v3100/01_device.png` / `02_sheet.png` | 设备页基线 + 第一次点偏的对照 |
| 真机 | ⛔ 仍卡 HONOR 安装确认框（`lastUpdateTime` 23:24:51）⇒ 需用户点一次「继续」 |

## 5. ⑥ 剩余（后续轮次，逐屏搬）

| 待搬 | 归属屏 |
|---|---|
| 附件选择 / 图片选择 / 语音 | 对话页 |
| 模型 / 思考强度 / 权限 / 生成物 / 用量 / 工作区面板 | 对话页 |
| 审批、提问（现已画在消息卡里，交互待接） | 对话页 |
| 重命名 / 归档 / 工作区分组菜单 | 会话列表 |
| 诊断弹窗 | 设置页 |
| 扫码页、交付物预览页 | 独立页（需各自 Compose 屏） |

> 做法固定为：**状态里带一个 `GlassSheetState` → 屏内在取样层之外渲染 `GlassSheetOverlay` → 宿主把 index 映射到真实动作**。
