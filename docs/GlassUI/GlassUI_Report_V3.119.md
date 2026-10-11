# GlassUI 迭代报告 V3.119 | 2026-10-12（"第一次是老弹窗、逛一圈才变玻璃"的根因与修复）

> **触发**：用户「**未修复**，刚打开修改名称**就是老弹窗**，然后进入对话再退回来再进入**就成玻璃卡片了**」。

---

## 1. 根因（代码级确认，和用户两次观察逐字对上）

`Screen` 枚举只有三个：`{ DEVICE, CHAT, SETTINGS }`；
**设备页那套宿主是"懒建"的**：

```java
private android.view.View glassDeviceView() {
    if (glassDeviceView == null) {
        if (glassDeviceState == null) glassDeviceState = new GlassDeviceState();   // ← 到这里才 new
        ...
    }
    return glassDeviceView;
}
```

而我 V3.115 的路由是这样问的：

```java
if (glassDeviceState != null) return glassDeviceState.getSheet();   // 没建 ⇒ null
return null;                                                        // ⇒ 调用方回退旧弹窗 ✗
```

⇒ **第一次**打开修改名称时，设备页宿主**还没被创建** ⇒ 路由返回 null ⇒ 走**回退旧弹窗** ✗（"刚打开就是老弹窗"）
⇒ 用户逛一圈（设备页 view 建好了）⇒ 路由能拿到 ⇒ **玻璃卡片** ✓（"进去再出来就成玻璃卡片了"）✓
**两个观察逐字对上** ✓

## 2. 修复（V3.119）

路由里做两件事：

1. **先确保懒建宿主存在**：调一次幂等的 `glassDeviceView()`（它就是"state + Compose 设备页"的创建点）；
2. **只认"渲染器真的挂在视图树上"的宿主**：`view().getParent() != null`（三屏都加）
   ⇒ 避免"写进一个没人渲染的状态"（那种情况弹窗会**不出现** ✗），拿不到就**老实回退旧弹窗** ✓。

## 3. 项目明细

| 条目 | 状态 | 证据 |
|---|---|---|
| "第一次老弹窗、逛一圈才玻璃" | ✅ 已修（确保懒建 + 只认已挂载的宿主）| `MainActivity.currentSheetState()` |
| 路由只写"有人渲染"的状态 | ✅ 已实现 | `settingsView.view().getParent()` / `chatView.view().getParent()` / `glassDeviceView.getParent()` |
| 首次材质正确（等层记录）| ✅ 已实现（V3.118 `settleFrames = 2` × 4 站点）| `GlassSheet.kt` 新参数 |
| 抽屉材质稳定（层可观察）| ✅ 已实现（V3.117）| `GlassChatBackdrop/GlassDrawerBackdrop` |
| 取层优先级（与模型/权限同层）| ✅ 保持（V3.116）| `createGlassSheetHost` |
| 构建 + 装机 | ✅ | 真机重启完成，sha `c4219f12…`（构建失败时**不再装旧包**，已加护栏）|
| 真机验收 | ⏳ **未验证** | 需你**首次**打开「修改名称」确认就是玻璃 |
