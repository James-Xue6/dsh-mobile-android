# GlassUI 迭代报告 V3.11 | 2026-10-09

> 目标轮次：**⑥ 继续 —— 统一弹窗骨架加「确认」形态 + 接设备页删除确认**
> 上轮：V3.10（骨架 + 添加设备弹窗）

---

## 1. 骨架扩展（`glass/GlassSheet.kt`）

`GlassSheetState` 增三个字段 + 两种 show：
- `tag`：宿主用它区分"当前是哪张 sheet"（如 `add` / `delete:<设备id>`），这样 `onPick(index)` 不歧义；
- `confirmLabel` + `danger`：**「确认」形态**（非空时画 标题 + 说明 + 一个主按钮；`danger=true` 变红）；
- `showMenu(tag, title, items)` / `showConfirm(tag, title, message, confirmLabel, danger)`。

## 2. 接第二个真实弹窗：设备页「删除设备」

- `MainActivity.onDeleteDevice(d)`：`USE_GLASS_SHEET` 时走本页玻璃 sheet
  （`showConfirm("delete:"+d.id, …)`，开关关掉回退原系统对话框，**旧代码保留**）；
- 抽出 `doDeleteDevice(d)`（统一弹窗与系统对话框两条路共用，与旧对话框里的代码同义）；
- `sheetPick(index)`：先读 `tag` → `delete:<id>` 时按 id 找到设备执行删除；否则按 `add` 的 0/1 分派。

## 3. 验收（模拟器）

| 取证件 | 结果 |
|---|---|
| `evidence/v3101/01_delete_confirm.png` | 点设备卡「删除」→ **确认形态玻璃弹窗**：抓杆 + 标题「删除「DESKTOP-TEST」」+ 说明 + **红色「删除」** + 「取消」；遮罩压暗设备页；无崩溃 |
| `evidence/v3101/02_after_delete.png` | ⚠️ 点了红色「删除」后**弹窗仍在、设备未删除** ⇒ **动作没触发** |

### 3.1 已排除
- **id 映射没问题**：`MainActivity` 推给 `glassDeviceState.setDevices(...)` 的就是 `store.devices()` 的同一份
  （L2065→L2074），`sheetPick` 里按 id 反查同源；
- **无崩溃**：模拟器 dropbox 最后一条 `data_app_crash` 仍是 2026-10-08 15:52（本轮之前）。

### 3.2 嫌疑（下一轮第一步）
点击是否**落到弹窗内的按钮**。诊断法：先点「添加设备」弹窗里的**菜单项**，
若菜单项也不响应 ⇒ 是 overlay 内的点击没生效（层级/手势问题）；
若菜单项响应 ⇒ 是删除分派逻辑（tag/index）问题。

## 4. 状态

| 项 | 状态 |
|---|---|
| 骨架（菜单形态） | ✅ V3.10 已验 |
| 骨架（确认形态） | ✅ 渲染已验 |
| 添加设备弹窗（走真实 onScanAdd/onManualAdd） | 🟡 渲染已验；**点选未验** |
| 删除确认弹窗（走真实删除） | ❌ **动作未生效** ⏳ 下一轮定位 |
| 真机 | ⛔ 仍卡 HONOR 安装确认框（需用户点一次「继续」） |
