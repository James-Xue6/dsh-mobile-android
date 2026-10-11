# GlassUI 迭代报告 V3.60 | 2026-10-11（"聊天记录又没了" 的**真正**归属：网关 history 静默为空）

> **触发**：用户「你刚才改的聊天记录又没了」。
> **结论**：**不是我这轮改的 App 代码**——App 自己的诊断读数给出 `items=0 raw=0 vis=0`
> （`raw=0` = 宿主**从未喂过**非空列表）⇒ **数据根本没到 App**。
> 已定位到**网关**两处会把整页 history 丢成空、且**不报错**的地方，并**在插件侧修掉 + 加诊断**。

---

## 1. 取证（App 屏上诊断读数，非推测）

| 时刻 | 读数 | 含义 |
|---|---|---|
| 打开会话 10s / 25s / 45s | `items=0 raw=0 vis=0 head=188.86` | 三次完全一致 ⇒ **宿主从没喂过非空列表** |
| 同一台机器 · 演示切片 | `items=13` | 渲染链路本身完全正常 ✓ |

⇒ 排除"Compose 显示空"，也排除我这轮的自动翻页（`raw` 早就是 0）。

## 2. 根因（网关 `dsh-plugin-mobile-gateway/lib/dsh-host-adapter.mjs`）

```js
export function readHistoryRecords(records) {
  return requireArray(records, 'session history').map(record => {
    const value = requireRecord(record, 'session history record')
    if (value.type !== 'event') throw new Error('DSH Session V4 history requires event records')
    ...
```
| # | 问题 | 后果 |
|---|---|---|
| ① | 快照/分页里**只要混进一条非 `event` 记录**就 `throw`（整个函数抛错） | 整页 history 拿不到 ⇒ App 侧"空"，且上层看不到明确错误 |
| ② | `history()` 在"无 `beforeSeq` 且无 `maxMessages`"时，**请求里不带 `maxMessages`** | 核心可能返回**不含 records 的基线快照** ⇒ 同样是空 |

这两条都**只在网关侧**，与 App 渲染无关 —— 也解释了为什么同一会话的"目标/计划"等其它镜像**能到**（走的是别的帧）。

## 3. 处置：**在插件侧修 + 加诊断**（可随插件更新分发）

新增 `pc-plugin/patches/patch-gateway-history-diag.ps1`（备份 / 幂等 / **改完 node --check 自检**，失败自动回滚）：

1. `readHistoryRecords`：非 `event` 记录**跳过**（不再整页抛错），并把"跳过几条、都是什么 type"记进日志；
2. `history()`：无 `beforeSeq` 且无 `maxMessages` 时**补 `maxMessages: 200`**（要尾部一页而不是基线）；
3. 每次 snapshot 追加一行诊断到 `%USERPROFILE%\.dsh\mobile-gateway-diag.log`：
   `时间 会话 版本 records条数 hasMore` —— **只记结构，不记内容**。

**执行结果**：`[OK] 语法自检通过` ✓；备份 `dsh-host-adapter.mjs.bak-history-diag`（20632 B）✓；
改动点已逐行复核（16/18/25/39/185/188 行）。**需要重启一次 DSH 才生效**（网关已被进程加载）。

## 4. 交接单

| 条目 | 状态 | 证据 |
|---|---|---|
| "聊天记录没了"归属 | ✅ 已定性：**App 侧 raw=0** ⇒ 数据没到 | 屏上 `DBG` 读数三连测 |
| 网关 ①非 event 抛错 | ✅ 已修（跳过 + 记日志） | 补丁 §3 |
| 网关 ②缺 maxMessages | ✅ 已修（补 200） | 补丁 §3 |
| 生效 | ⏳ **待重启 DSH** | 重启后 `mobile-gateway-diag.log` 会写出来 |
| 历史自动拉全（轮数） | ✅ 已装（App 侧） | V3.59 §2 |
| 目标卡两态胶囊 | ✅ 已装并取证 | V3.59 §1（740/741） |
| 屏上 DBG 读数 | 临时（验完删） | — |
