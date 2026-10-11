# GlassUI 迭代报告 V3.66 | 2026-10-11（任务胶囊**纠正定版**：不是弹窗，是"点一下放大、再点缩回"的就地卡片）

> **触发（用户批注）**：「这个不对，**你这是弹窗**，我说的是点击目标那个胶囊，
> **像设置里面的卡片一样，点一下放大再点下缩回来**」。
> ⇒ 我 V3.65 理解错了（做成了 `GlassSheetOverlay` 弹窗 ✗），本轮按设置页 `SectionCard` 的模式重做。

---

## 1. 参照物（直接读设置页的实现）

`GlassSettings.kt:506 SectionCard`：
```kotlin
Column(Modifier.padding(top = 10.dp)) {
    GlassSurface(backdrop, Modifier.fillMaxWidth(), radius = 28.dp) {     // ← 一张真玻璃卡
        Column(padding(h = 16, v = if (open) 16 else 12)) {
            Row(fillMaxWidth().glassTap(corner = 28.dp) { onToggle() }) { // ← 整行可点
                Text(title, Bold, weight(1f)); Text(if (open) "▾" else "▸")
            }
            if (open) { Spacer(12.dp); content() }                        // ← 展开 = 同一张卡长高
        }
    }
}
```
**收起时高度小 ⇒ `radius 28dp` 被"短边/2"夹住 ⇒ 端头恰好是半圆 = 长胶囊**；
展开时同一张卡长高 ⇒ 天然就是"玻璃卡片"。

## 2. 本轮实现（`GlassChatScreen` 目标卡）

- **一张 `GlassSurface`**：`radius = 28.dp` + 体色 **白 55%**（与 chips 同档）+ `glassButtonShadow(28dp)`；
  取样层沿用 `contentBackdrop`（头块在 ① 取样层内部，取 `backdrop` 会自取样）；
- 头部整行 `glassTap` ⇒ **点了就在原地展开/收起**（`planOpenLocal` 本地状态，另同步一次宿主侧旧视图状态）；
  头部右侧带 **▸ / ▾**（与设置页同款指示）；
- **展开内容** = 4 条任务条（胶囊 + 白渐变 + 发丝边 + 状态圆点 + 右对齐状态）；
- **弹窗那套已删除**（不再用 `GlassSheetOverlay`）；取证钩子 `--ez glass_force_plan true`
  改为**把这张卡初始设为展开**（真机点不了卡片时取证用）。

## 3. ✅ 真机取证（两个状态）

| 状态 | 证据 |
|---|---|
| **收起 = 长胶囊**（`目标 … 0/0 ▸`，同一张玻璃卡） | `evidence/phone-20261011/820_inplace_collapsed.png` |
| **点开 = 就地长高成玻璃卡片**（目标行 + `▾` + 4 条任务胶囊 `完成/完成/本轮完成/待做`） | `evidence/phone-20261011/821_inplace_expanded.png`（放大：`822_expanded_zoom.png`） |

## 4. 交接单

| 条目 | 状态 | 证据 |
|---|---|---|
| 纠正"弹窗"→"就地放大/缩回" | ✅ 已改并真机取证 | 820 / 821 / 822 |
| 收起态与 chips 同一套玻璃语言 | ✅（同档体色白 55% + 真玻璃件） | 820 |
| 展开态 = 设置页卡片同款（▾ + 卡片长高） | ✅ | 822 |
| 弹窗实现移除 | ✅ | 代码 |
| 胶囊与 chips **逐像素同源**（取样层一致） | ⏳ 可选打磨（要把胶囊移进悬浮层） | — |
| 产物 | `dist/dsh-mobile.apk` | 已装真机 |
