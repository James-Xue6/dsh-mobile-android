# GlassUI 迭代报告 V3.101 | 2026-10-11（弹窗层级：提**错了一层**，这次提到与抽屉同级的那层）

> **触发**：用户「**没改啊？弹窗还是在下面**」+ 上一条「点三个点→侧抽屉收回去→再弹窗，**太多此一举**；
> 弹窗**不能调整到最上层**吗？点错了还得重新开抽屉」。

---

## 1. 为什么上一版 `bringToFront` 没生效（真原因）

`MainActivity` 的视图结构是：

```
root (FrameLayout)
├── contentHost     ← 当前这一屏（聊天页的 ComposeView 就挂在这里面）
└── drawerHost      ← 抽屉（DrawerHost），**contentHost 的兄弟**，所以天然盖在聊天层之上
```

V3.101 我先调的是 `chatView.bringToFront()`（**ComposeView 自己**）——
`bringToFront()` 只在**自己的父容器**里把它挪到最后一个子 View ✗，
而它上方还隔着 `contentHost` ⇒ **层级一点没变** ✗（所以你看到"还是在下面"）。

## 2. 这次改对了

```java
if (drawerHost != null && drawerHost.isOpen()) {
    contentHost.bringToFront();                                  // ← 提**与抽屉同级**的那层
    gv.setSheetHideCallback(() -> drawerHost.bringToFront());     // ← 弹窗关掉后还原
}
```

外加 `GlassSheetState.onHide`（`hide()` 时回调）⇒ 关闭弹窗时把抽屉那层放回去 ✓。

**效果**：点 ⋯ ⇒ **抽屉不收**（仍在下面待命），弹窗提到最上层、屏幕正中；
点空白/关闭 ⇒ 抽屉还在开着，**可以立刻重新点** ✓（正是你要的）。

## 3. 项目明细

| 条目 | 状态 | 证据 | 下一步 |
|---|---|---|---|
| 弹窗被抽屉盖住 | ✅ 根因=提错层级；已改 `contentHost.bringToFront()` | 视图树：`root[contentHost, drawerHost]` | 你点一次 ⋯ 确认 |
| 不再收起抽屉（去掉多余一步） | ✅ 已实现 | `showGlassMenu` | — |
| 关闭弹窗后抽屉保持打开 | ✅ 已实现 | `onHide` → `drawerHost.bringToFront()` | 点空白关掉试试 |
| 全站弹窗恢复玻璃 / 抽屉底色 / ⋯ 走宿主 | ✅ 前几轮已修 | — | — |
| 队列重开消失（网关补丁） | ⏳ 等你点头 | `index.mjs:2269/3130/3385` | 你确认我写补丁（可一键回滚）|

产物已装真机并重启（sha `ece9db4d…`）。
