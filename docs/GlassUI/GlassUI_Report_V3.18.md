# GlassUI 迭代报告 V3.18 | 2026-10-09

> 触发（用户报功能缺失）：「右侧之前合并的滚动条消失了未恢复」
> 本轮 **A 类质感数值改动 = 0**（新增一个自绘组件 + 提升一个状态）

---

## 1. 先查清楚"之前有没有"

全工程检索：**没有任何自定义滚动条代码**（`ScrollBar`/`indicator`/`scrollThumb` 全无匹配），
而旧对话页反而**显式关掉了** ListView 的原生滚动条：

```java
// ConversationView.java:414
list.setVerticalScrollBarEnabled(false);
```

⇒ 真实情况是：**新的 Compose 对话页压根没有任何滚动指示**（长列表滑到哪没有视觉锚点），
所以"右侧滚动条"确实属于**缺失**，需要补。

## 2. 实现 `GlassScrollIndicator`（`GlassChatScreen.kt` 末尾）

- 取 `LazyListState.layoutInfo`：`totalItemsCount` + 可见首尾 index；
- 拇指高度 = 可见比例（夹在 0.10…1.0），位置 = 首条 index / 总数；
- **滚动时显形、停下 900ms 后 220ms 淡出**（`state.isScrollInProgress` + `LaunchedEffect` + `animateFloatAsState`）；
- 与玻璃同材质：`Color(Ui.BRAND_FILL) @ 55%`、宽 3dp、胶囊圆角；贴右缘、纵向覆盖列表可视区。

配套改动：把 `rememberLazyListState()` 从 `Column` 内部**提升到取样层这一级**
（原来在 Column 内，层级的指示条取不到它）；补 `offset` / `fillMaxHeight` 两个 import。

## 3. 验收（模拟器）
| 取证件 | 结果 |
|---|---|
| `evidence/v3240/01_scrollbar.png` | 滑动中在**右缘可见细紫指示条**（纵向覆盖列表可视区、位置与列表进度一致）✓ |

`assembleDebug` + `assembleRelease` 均 BUILD SUCCESSFUL。

## 4. 本轮踩坑（两次把文件改坏，已记）

1. **先读后追加导致覆盖**：`Get-Content -Raw` 读到内存 → `Add-Content` 追加 helper → 再用内存里的旧内容 `Set-Content` ⇒ **追加的内容被覆盖掉**（构建报 `Unresolved reference`）。
   ⇒ 教训：**追加必须放在所有重写之后**。
2. **作用域**：把指示条放在取样层这一级，但 `listState` 原本声明在 `Column` 内 ⇒ 取不到（`Unresolved reference`）⇒ 需要先提升状态。
3. **漏 import**：`offset` / `fillMaxHeight` 是扩展函数，全限定名写法也救不了 ⇒ 补 import。

## 5. 状态
| 项 | 状态 |
|---|---|
| 右侧滚动指示条（对话页） | ✅ 已恢复（`v3240/01_scrollbar.png`） |
| 会话列表 / 设置页是否也加 | ⏳ 同一组件可直接复用，等你一句话 |
| Bug 5（设置导航栏无玻璃）/ Bug 2（手动添加老界面） | ⏳ 仍待做（V3.15 §3） |
| 真机安装 | ⛔ `adb install` 停系统确认框，**需用户点一次「继续」** |
