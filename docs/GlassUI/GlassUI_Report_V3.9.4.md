# GlassUI 迭代报告 V3.9.4 | 2026-10-08

> 触发：修「抽屉那条边」（V3.9.2/V3.9.3 已排除 Compose 内容与背后内容）
> 本轮 **A 类质感数值改动 = 0**（只改容器结构：把玻璃底从面板身上挪到独立子 View）

---

## 1. 根因（终于落地）

V3.5 我给抽屉外壳加了 `DrawerHost.applySafeInsets()`：`drawer.setPadding(0, top, 0, bottom)`。
**View 的背景是按 padding 盒绘制的** ⇒ 挂在 `drawer` 上的那层玻璃底
（`applyDrawerBg()` 设的 LayerDrawable：模糊玻璃 + 135° 斜射高光 + 右缘发丝线）
被**裁短 / 形状错位** ⇒ 面板内部多出一圈"边"、右缘看起来"硬切没棱线"。

> 代码里原本就有一条相关记录（2026-10-03）：「系统 elevation 阴影会从半透明体下面透出来，
> 在面板内部画出一圈**灰环**」—— 用户看到的与它是同一类现象，来源却是 padding 盒。

## 2. 最小 diff（3 处）

1. `DrawerHost` 新增字段 `private final View bg;`（玻璃底独立子 View，`elevation=0`）；
2. 构造顺序：建 `drawer` → 建 `bg` 并 `addView(bg)` → `addView(drawer)`（`drawer.setBackground(null)`）
   → **最后** `applyDrawerBg()`；`applyDrawerBg()` 改为把 LayerDrawable 设到 `bg` 上
   （`Ui.blurredGlassRoot(bg, 34f, 0f, …)`）；
3. `onMeasure` 里让 `bg` 与 `drawer` **同宽**（否则玻璃底会铺满整个宿主）；
   `applyTx` 里 `bg` 与 `drawer` 一起平移。

⚠️ **踩坑**：第一版把 `applyDrawerBg()` 留在原位置（早于 `bg` 赋值）⇒ 运行期 NPE，
一点开抽屉就闪退（前台回桌面）。已按"先建 bg、最后 applyDrawerBg"修好。

## 3. 验收（模拟器）

| 取证件 | 结果 |
|---|---|
| `evidence/v394/02_drawer_glassfix.png` + `crop_top2.png` | **那圈圆角描边消失**；面板顶部干净、右缘恢复为正常的棱线/发丝线；抽屉内容（标题/圆钮/状态行）正常；**无崩溃**（`mCurrentFocus` = MainActivity） |
| `evidence/v394/01_drawer_glassfix.png` | 修复前那版（NPE 闪退）留档，用于对照 |

**真机**：⛔ 仍卡 HONOR 安装确认框（第 8 次尝试），`lastUpdateTime` 仍 23:24:51 ⇒ 需用户点一次「继续」。

## 4. 下一步
1. 真机复验本轮抽屉修复 + V3.9.1 的顶栏下移（等用户点「继续」）。
2. ⑥ 扫码页 / 交付物页 / 各弹窗统一（目标最后一项）。
3. 真机深色主题复验（需用户手动切深色，EMUI 拦 adb 强切）。
