# GlassUI 迭代报告 V3.9.3 | 2026-10-08

> 触发：继续追查「抽屉那条边」（V3.9.2 已排除 Compose 迁移）
> 本轮 **A 类质感数值改动 = 0**，纯诊断（两个低成本排除实验）

---

## 1. 两个排除实验（都做完了）

| 实验 | 做法 | 结果 | 排除掉的假设 |
|---|---|---|---|
| ① 换抽屉内容 | `USE_GLASS_DRAWER=false`（内容换回旧 `SessionListView`） | 描边**同位置同形状** | 「Compose 会话列表画出来的」❌ |
| ② 换背后页面 | 抽屉强制拉开，但背后从**对话页**换成**设备页** | 描边**一模一样** | 「透过磨砂看到的背后内容」❌ |

证据：`evidence/v392/edge_top_old.png`（实验①）、`evidence/v393/crop_top_over_device.png`（实验②）。

## 2. 结论：描边来自**抽屉面板自己的玻璃**，而且是**旧引擎**画的

`DrawerHost.applyDrawerBg()`：
```java
Ui.blurredGlassRoot(drawer, /*blurDp=*/34f, /*cornerDp=*/0f, Ui.round(0, Ui.GLASS_SHEET))
```
- 刚查到的签名：`blurredGlassRoot(View panel, float blurDp, float cornerDp, Drawable glass)` ⇒ 抽屉玻璃
  **cornerDp=0、内层也是 round(0)** ⇒ **不是圆角参数画的**那条圆角描边。
- 两个排除实验又证明它不是内容——⇒ 只能是**旧玻璃引擎（`BackdropBlurDrawable` 那条路径）在面板内画的
  形状/高光，按 `padFor`（模糊半径与环带留白，见 SOP §2）从面板边缘内缩**画出来的那一圈。
  面板外圈因此没被玻璃覆盖 ⇒ 右缘看起来"硬切、没棱线"，顶部看起来多了一圈圆角描边。

## 3. 为什么不在本轮直接改（SOP 约束）

- 改内缩量要动的是**旧引擎本体**（`BackdropBlurDrawable`/`blurredGlassRoot` 的 padFor 与绘制顺序）
  ⇒ SOP 红线：**禁止重构组件本体**；且调用处那 2 个入参（blurDp/cornerDp）**都不控制内缩** ⇒ 调用处无解。
- ⇒ 正解与目标本身一致：**把抽屉面板的背景也换成路线 A 的 Compose 玻璃**（`RefractionWindow`/`GlassSurface`），
  这样外壳玻璃与页面内玻璃是同一套实现、边缘行为一致。这是**下一轮的专项**（会同时把那圈内缩描边去掉）。

## 4. 本轮状态

| 项 | 状态 | 证据 |
|---|---|---|
| 抽屉"边"根因 | ✅ **完全确证**（两次排除实验） | `v392/edge_top_old.png`、`v393/crop_top_over_device.png` |
| 修该边 | ⏳ 下一轮：抽屉面板背景换 Compose 玻璃（路线 A） | — |
| 真机安装 | ⛔ 第 7 次尝试仍被 HONOR 安装确认框拦住（`lastUpdateTime` 仍 23:24:51） | — |
| 顶栏下移 | ✅ 已实现（模拟器已验），真机 ⏳ | `v392/04_emu_chat_nudged.png` |

## 5. 下一步（目标 ⑥ 之前的插队项）
1. **抽屉面板玻璃 Compose 化**（去掉那圈内缩描边，同时让抽屉与页面同一套玻璃）。
2. ⑥ 扫码页 / 交付物页 / 各弹窗统一（统一 Compose 弹窗骨架）。
3. 真机复验（需用户点一次「继续」）。
