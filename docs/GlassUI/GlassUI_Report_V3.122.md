# GlassUI 迭代报告 V3.122 | 2026-10-12（**铁证定案**：弹窗"第一次是老弹窗"根因 = `convo == null` 当门）

> **触发**：用户「**还是不行，还是我说的这样**」（前 4 轮我都在猜取样层/懒建宿主，全猜错）。
> 本轮改用**运行时取证**（真机 logcat 抓不到，改成写文件）⇒ 一次定案。

---

## 1. 取证方法（为什么这次能定案）

真机 `adb logcat` 取回的是**加密块**，读不到应用日志 ✗ ⇒ 改成 `MainActivity.diag(msg)` 把关键判断**落文件**：

```
/sdcard/Android/data/com.dsh.mobile/files/sheet-diag.log
```

在 `setContent(View)`、`currentSheetState()`、`onRenameDevice()` 三处写入。

## 2. 文件里的铁证（用户点「修改名称」后的原始记录）

```
01:07:07.810  setContent ComposeView
01:07:07.819  setContent ComposeView
01:07:08.836  onRenameDevice convo=null screen=DEVICE
01:07:09.992  onRenameDevice convo=null screen=DEVICE
```

⇒ **`convo == null`** ✓✓（`screen=DEVICE`）

## 3. 根因（终于对上了用户两次描述）

我 V3.111 迁移时写的是：

```java
if (convo != null) {          // ✗ 大错：拿"聊天视图存在"当"玻璃可用"的判据
    ... glassInScreenForm("renamedev", ...) ...
}
... 旧弹窗 ...                 // ← convo==null ⇒ 直接落到这里
```

- 设备页上 `convo`（聊天视图）**还没创建**（它是懒建的）⇒ **整个玻璃尝试被跳过** ⇒ **旧弹窗** ✓
- 用户"**进入对话再退回来**"⇒ `convo` 被建出来了 ⇒ 再打开就**走玻璃** ✓

⇒ **"第一次是老弹窗、进去再出来才变玻璃" 与这条逻辑逐字一致** ✓✓（前几轮我盯着取样层/懒建宿主，全是错的 ✗）

## 4. 修复（V3.122）

| 站点 | 改动 |
|---|---|
| 修改名称（`onRenameDevice`）| **删掉 `if (convo != null)` 门** ⇒ 直接走 `glassInScreenForm`（路由不需要 `convo` ✓）|
| 粘贴配对串（`showPairInputDialog`）| 同上 ⇒ `glassInScreenForm` |
| 用剪贴板配对串（`showPairConfirmDialog`）| 同上 ⇒ `glassInScreenList` |

**同类审计**：扫描"`if (convo != null)` 块内出现 glass 调用"的地方 ⇒ **0 处残留** ✓
（另有 58 处 `if (convo != null)` 是正常的空指针防护，**不是**玻璃门 ✓）

## 5. 项目明细

| 条目 | 状态 | 证据 |
|---|---|---|
| "第一次老弹窗、逛一圈才玻璃" | ✅ **已修**（删掉错误的 `convo` 门）| 诊断文件 `convo=null` + §4 |
| 同类错误门全工程审计 | ✅ 0 残留 | 脚本扫描 |
| 之前几轮的真修复（仍有效）| ✅ | 可观察取样层(V3.117) / settleFrames(V3.118) / 懒建宿主(V3.119) / 同层材质(V3.116) |
| 构建 + 装机 | ✅ | 真机重启完成，sha `4e258c3b…` |
| 诊断文件 | ⏳ 暂留（确认后删）| `sheet-diag.log`（含调用点）|
| 真机验收 | ⏳ **待你点** | 冷启动 → 首页 → 修改名称，**第一次**即应为玻璃 |
