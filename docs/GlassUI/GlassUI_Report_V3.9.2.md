# GlassUI 迭代报告 V3.9.2 | 2026-10-08

> 触发：追查用户反馈「对话那个**侧抽屉界面那个边有点问题**」
> 上轮：V3.9.1（顶栏下移 + 抽屉双倍内缩）
> 本轮 **A 类质感数值改动 = 0**（只做诊断，不改任何绘制/参数）

---

## 1. 方法：把截图裁切放大 + 差分实验

1. 把模拟器抽屉截图裁切放大（右缘 / 顶部 / 左缘三块）⇒ **定位到问题**：
   抽屉面板内部有一条**圆角描边**（约 x=50..880、y≈12 起，圆角≈30px），
   而面板本身是 x=0..890 的整块 —— 也就是**玻璃的棱线/形状比面板内缩了一圈**，
   右缘因此呈现"硬切直线、没有棱线"。
2. **差分实验**（关键）：把 `USE_GLASS_DRAWER` 临时置 false（抽屉内容换回旧 `SessionListView`），
   重编重装重拍 ⇒ **同一条圆角描边、同一位置、同一形状，一模一样**。
   ⇒ **结论：这不是本轮 Compose 迁移造成的，是 `DrawerHost` 外壳那块玻璃自身的既有产物。**

## 2. 责任定位（按 SOP 分类）

`DrawerHost.applyDrawerBg()`：
```java
Ui.blurredGlassRoot(drawer, 34f, 0f, Ui.round(0, Ui.GLASS_SHEET))   // ② sheen ③ 右缘发丝线
```
- 玻璃的 **棱线/形状按 `padFor`（模糊半径/环带留白）内缩**绘制 ⇒ 面板外圈那一圈没被玻璃覆盖，
  于是出现"内缩的圆角描边 + 右缘硬切"。
- 按 SOP 分类：这是**质感/参数**层面的问题（不是布局 corner/padding），
  但**不能重构组件本体** ⇒ 应作为**一次专门的 SOP 迭代**处理：
  **≤2 个参数**、只改调用处入参或 `Ui`/`FrostedPanelView` 的既有 token，并**以真机截图验收**。

## 3. 本轮状态

| 项 | 状态 |
|---|---|
| 抽屉"边"根因 | ✅ **已确证**（差分实验证据：`05_old_drawer_shell.png` 与 Compose 版完全一致） |
| 修该边 | ⏳ **下一轮专门做**（SOP 参数迭代 + 真机验收）；**需你确认这就是你说的那条边** |
| 顶栏下移 | ✅ 已实现（模拟器 `v392/04_emu_chat_nudged.png`），真机 ⏳ |
| 真机安装 | ⛔ 第 6 次尝试仍被 HONOR 安装确认框拦住（`lastUpdateTime` 仍 23:24:51） |

## 4. 证据清单
- `evidence/v392/edge_right.png` / `edge_top.png` / `edge_left.png`：面板右缘/顶部/左缘放大图（描边可见）
- `evidence/v392/05_old_drawer_shell.png` + `edge_top_old.png`：**差分实验**（旧抽屉内容，描边同样存在）
- `evidence/v392/03_emu_drawer_fixed.png` / `06_final_drawer.png`：Compose 抽屉（双倍内缩已修）
- `evidence/v392/04_emu_chat_nudged.png`：顶栏下移后的对话页
- 四个开关已复核为 `true`（`USE_GLASS_DEVICE/CHAT/DRAWER/SETTINGS`），差分实验态**未外泄**

## 5. 下一步
1. **抽屉棱线内缩**：专门一轮 SOP 迭代修（先量 `padFor` 与面板尺寸的关系，再只动 ≤2 个参数），真机验收。
2. 真机安装：需用户在手机上点一次「继续」/ 打开类似「通过 USB 安装应用」的开关。
3. ⑥ 扫码页 / 交付物页 / 各弹窗统一（目标最后一项）。
