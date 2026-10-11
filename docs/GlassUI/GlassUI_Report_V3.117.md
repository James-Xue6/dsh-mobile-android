# GlassUI 迭代报告 V3.117 | 2026-10-12（材质"时好时坏"的根因：**普通变量不是 Compose 可观察状态**）

> **触发**：用户「**刚才好了，但是我点其他地方，再回来又变回来了？**」

---

## 1. 根因（这次是确定性的，不是猜）

`GlassChatBackdrop.layer` / `GlassDrawerBackdrop.layer` 之前是**普通 `var`** ✗：

```kotlin
object GlassChatBackdrop { var layer: Backdrop? = null }      // ✗ 不可观察
```

而抽屉那个全屏宿主是在 **Compose 组合里**读它：

```kotlin
val layer = GlassChatBackdrop.layer ?: GlassDrawerBackdrop.layer   // 只在组合那一刻读一次
```

⇒ **读一次就定死**：
- 用户第一次打开（聊天页已经组合过、层已写好）⇒ 读到**聊天页主取样层** ⇒ **材质正确** ✓（"刚才好了"）
- 之后**导航走了再回来**：聊天页那棵树经历 dispose/重组，读取发生在"层还没写回"的时刻 ⇒ 拿到 null
  ⇒ 退用 `GlassDrawerBackdrop`（抽屉自己那层，材质明显更弱）⇒ **而且不会自愈**（变量不可观察 ⇒ 宿主不重组合）
  ⇒ "**再回来又变回来了**" ✓✓

## 2. 修复（V3.117 · 两行）

```kotlin
var layer: com.kyant.backdrop.Backdrop? by androidx.compose.runtime.mutableStateOf(null)   // ✓ 可观察
```

`GlassChatBackdrop` 与 `GlassDrawerBackdrop` **两个都改** ⇒ 层出现/被清空时宿主**自动重组合** ⇒ 材质稳定 ✓。
（同时保留 V3.116 的取层优先级：**聊天页主取样层优先**，抽屉层兜底；两者都为空时**不画**。）

顺带核查：全工程只有这两个 Backdrop 持有对象，**没有**其它同类"普通变量"写法 ✓。

## 3. 项目明细

| 条目 | 状态 | 证据 |
|---|---|---|
| 材质"时好时坏" | ✅ 已修（改为可观察状态）| `GlassChatScreen.kt:994`、`GlassSessionList.kt:109` |
| 取层优先级（与模型/权限同层）| ✅ 保持 V3.116 | `createGlassSheetHost` |
| 空层保护（不画透明玻璃）| ✅ 保持 | `if (layer != null)` + `onDispose` 清空 |
| 其它同类隐患 | ✅ 已核查，无 | 全工程仅这两个 Backdrop 持有对象 |
| 构建 + 装机 | ✅ | 真机重启完成，sha `0d9825b3…` |
| 真机观感（多次来回切换后）| ⏳ **未验证** | 需你**反复切换几次**再开抽屉 ⋯ 看材质是否稳定 |
