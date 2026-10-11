# GlassUI 迭代报告 V3.115 | 2026-10-12（弹窗归属改造：**按界面各自那层**渲染 + 规则入库）

> **触发**：用户（激烈）「**每个界面的玻璃弹窗都应该在对应界面的位置最上层**，按你之前的逻辑给每个界面建一个独立的
> 弹窗宿主……**首页修改的那个也是这样，设置的那个粘贴配对串的弹窗也这样**！你都应该是对应界面弹窗，
> 为什么都给我搞到了对话窗口……**还有玻璃质感也给我恢复回来**。你要**记下来这个调改逻辑**」。

---

## 1. 我错在哪（根因不是手误，是**规范本身就写错了**）

`docs/GlassUI/GlassUI_SPEC_DIALOG.md` §3 原来写的是：

> 弹窗**必须挂在 Activity 级、全屏的 Compose 宿主**上（例如主界面那个 `GlassChatView`）。

⇒ 我按这句话把所有弹窗都指向**对话页**那个宿主 ✗。于是用户停在**设备页/设置页**时，对话页**没被组合** ⇒
弹窗**没人渲染** ⇒ 真机表现就是"**透明**"或"**跑到对话窗口去了**" ✓（V3.111 迁的「修改名称」「粘贴配对串」正是如此）。

## 2. 按用户规则改造（V3.115）

| 改动 | 内容 |
|---|---|
| **设置页补上"自己那套"** | `GlassSettingsState.sheet`（新）+ `GlassSettingsScreen` 根 Box **最后一层**的 `GlassSheetOverlay`（采本页 `pageBackdrop`）⇒ 弹窗位置天然在**设置页内容之上** ✓ |
| **入口统一路由** | 新增 `MainActivity.currentSheetState()`：抽屉开着 ⇒ 抽屉那套；设置页 ⇒ 设置页那套；对话页 ⇒ 对话页那套；其余 ⇒ 设备页那套。包装 `glassInScreenList / glassInScreenForm / glassInScreenConfirm` |
| **既有助手改为走路由** | `glassOptionList / glassConfirm / glassItems` 不再固定写对话页状态 |
| **三处被写错的直连改回路由** | 「修改名称」⇒ 落**设备页自己那层** ✓；「用剪贴板配对串」+「粘贴配对串」⇒ 落**设置页自己那层** ✓ |
| **抽屉** | 保持 V3.114 的"抽屉自己那套"（`drawerSheetState` + `DrawerHost` 内独立全屏宿主）⇒ 在抽屉面板之上 ✓ |
| **材质** | 各界面弹窗采**各自界面**的取样层 ⇒ **恢复玻璃质感** ✓（不再因为"拿不到对话页层"而退回旧实心对话框 ✗）|

## 3. 规则入库（用户要求"记下来"）

1. **长期记忆**：`hindsight_ingest_document("规则：dsh-mobile-android 弹窗必须挂在「对应界面自己的那一层」")` ✓
2. **规范原文**：`docs/GlassUI/GlassUI_SPEC_DIALOG.md` §3 **整节重写**（附四界面状态/宿主对照表 + 两条 ❌ 禁止）✓
3. **项目 AGENTS**：`dsh-mobile-android/AGENTS.md` 的弹窗规范里加「弹窗归属」子节（每次会话必读）✓

## 4. 项目明细

| 条目 | 状态 | 证据 | 下一步 |
|---|---|---|---|
| 设置页自己那套宿主 | ✅ 已实现 | `GlassSettings.kt`（state.sheet + 根 Box 末尾 overlay）| ⏳ 真机验收 |
| 入口统一路由 | ✅ 已实现 | `MainActivity.currentSheetState()` + 3 个包装 | ⏳ 真机验收 |
| 修改名称 → 设备页那层 | ✅ 已改 | `onRenameDevice` 走 `glassInScreenForm` | ⏳ 真机验收 |
| 粘贴配对串/配对确认 → 设置页那层 | ✅ 已改 | `showPairInputDialog` / `showPairConfirmDialog` 走路由 | ⏳ 真机验收 |
| 抽屉 ⋯ → 抽屉那层 | ✅ 已改（V3.114）| `drawerSheetState` + `DrawerHost` 内宿主 | ⏳ 真机验收 |
| 规则写入记忆/规范/AGENTS | ✅ 三处都写 | 见 §3 | — |
| 构建 + 装机 | ✅ | 真机 + 模拟器已装（sha `c96b0699…`）| — |

> ⚠️ **未验证说明**：模拟器的长按手势没能触发「设备页 ⋯ ⇒ 修改名称」，所以上述"按界面渲染"**我只做到代码级 + 编译级验证**，
> **真机效果待用户点一次确认**（不许当成已修复 ✗）。
