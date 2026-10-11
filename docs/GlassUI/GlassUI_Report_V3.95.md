# GlassUI 迭代报告 V3.95 | 2026-10-11（待发送菜单换成新玻璃弹窗 · 队列重开消失：**根因彻底定位**）

> **触发**：用户「还有**待发送的内容点击后弹窗是老的、未优化成新的玻璃弹窗**；刚才你说的退出之后那个
> **我复现了**，现在就是。然后我发现**再发一条之后，会再显示出来**」。

---

## 1. 待发送菜单 → **新玻璃弹窗** ✅

`MainActivity#onQueueItemAction` 原来走 `ModelSheet.showOptions(...)`（**旧的系统弹窗** ✗）。

**修**：与「生成物」面板**同一口径** ⇒ 走聊天页的**玻璃 sheet**
（`GlassChatView#showSheetList`，全屏、真玻璃、可点可关）：

| 项 | 副文案 |
|---|---|
| 立即插入当前回答 | 打断当前回合，把这条插进去 |
| 修改这条内容 | — |
| 删除这条 | 从队列移除，不再发送（危险色） |

行为不变（仍调 `gw.queueUpdate(..., "steer"/"remove")` 与 `promptEditQueue`）；
**非玻璃模式**保留老弹窗作为回退落点 ✓。

## 2. 「重开软件后待发送消失、再发一条又出现」——**根因彻底定位** ✅🎯

你的复现信息（"再发一条之后会再显示出来"）直接指向了**数据源**，我顺着查到网关：

```js
// index.mjs:3130  连接时发全量快照……
if (ws.mobileChannel !== 'conversation' && sessionQueues !== null) {
  ws.send(stringifyWireFrame({ kind: 'session-queues', queues: Object.fromEntries(sessionQueues) }))
}
// index.mjs:2269  sessionQueues 初始就是 null（懒加载缓存）
let sessionQueues = null
// index.mjs:3385  只有"队列发生变化"时才会被赋值
sessionQueues = next
```

⇒ **连接时缓存还是 `null` ⇒ 网关什么都不发** ✗；
而协议里**没有"主动读队列"的请求**（只有 `queue-update` 用于改队列；App 侧只有两个接收回调）✗
⇒ **只有等队列变化（= 你再发一条）才会广播** ⇒ 待发送条这时才出现 ✓✓ **与你实测完全一致** ✓。

**结论**：这不是 App 的 bug，是**网关少了一次"连接时加载队列基线"**。

**修法（需你点头，因为它动的是桌面端网关插件）**：
写一个补丁脚本（沿用现有 `pc-plugin/patches/*.ps1` 的形态，可 `-Revert`），
在**连接分支**里当 `sessionQueues === null` 时先调**已有的基线加载逻辑**
（`index.mjs:3352 queueItemsFromInbox` + `3378-3388` 那段 baseline 处理）再发快照 ⇒ 重开软件即可恢复 ✓。
⚠️ 生效需要**重启 DSH**（网关插件重新加载）。

## 3. 项目明细

| 条目 | 状态 | 证据 | 未成功的原因 | 下一步 |
|---|---|---|---|---|
| 待发送菜单换新玻璃弹窗 | ✅ 已实现+装机 | `showSheetList("queue", …)` | — | 真机点一条待发送看材质 |
| 队列重开消失：根因 | ✅ **彻底定位（网关侧）** | `index.mjs:2269 / 3130 / 3385` | — | 见下 |
| 队列重开消失：修复 | ⏳ **待你确认** | 需改**桌面端网关插件** + 重启 DSH | 涉及你电脑上的插件文件 | 你点头我就写补丁脚本（可一键回滚） |
| 产物 | `dist/dsh-mobile.apk`（sha `72345f83…`） | 真机 + 模拟器均已安装 | — | — |
