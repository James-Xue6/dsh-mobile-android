# GlassUI 迭代报告 V3.61 | 2026-10-11（"聊天记录全空"的**最后一层**：App 映射不了 v4 事件）

> **触发**：用户「你刚才改的聊天记录又没了」「重启了」「这个时候修好了，然后下一轮又修坏了」。

---

## 1. 网关补丁**已生效**（诊断日志自证）

`%USERPROFILE%\.dsh\mobile-gateway-diag.log`（重启后开始写）：
```
09:03:52 snapshot sid=session-1e1c526b-… v=4 records=827 hasMore=true
09:03:55 snapshot sid=session-8ff54952-… v=4 records=3   hasMore=false
… （会话列表探针，每条 3~6 条记录）
```
⇒ 网关**正常返回记录**（827 条的会话也有 ✓），**没有** `skipped-non-event` 行 ⇒ ①非 event 抛错**不是**本次主因（但仍是隐患，已修 ✓）。

## 2. 🎯 决定性读数（App 屏上诊断，非推测）

```
DBG items=0 raw=0 vis=0 head=43.14 ev=53
    types=assistant/message,tool/call,tool/result,step/end,step/start,user/message,
```
| 观察 | 结论 |
|---|---|
| `ev=53` | 快照**确实到了 53 条事件** ✓ |
| `types=assistant/message, tool/call, tool/result, user/message, step/*` | **类型与 App `applyEvent` 的 switch 完全对得上** ✓（不是命名不一致） |
| **无** "抛在#…" | 映射过程**没有异常** ✓ |
| `items=0 raw=0` | **53 条一条都没生成消息** ✗ |

⇒ **`applyEvent` 里读的字段在 v4 结构下取到空值，于是每条都静默 return**（不抛、不报、不显示）——
这就是"所有会话打开都空、且无任何提示"的直接原因；也解释了为什么"目标/计划"能显示（走 projections，不走事件）。

## 3. 我自己的工具链也有个坑（记录备查）

`tools/zstd-frames.mjs` 对**会话文件**切帧不完整：实测某些文件有 **2498 个帧头**、我只解出 261B
⇒ 之前"182 文件 / 64.5MB / 4195 帧"是**偏低**的。真正可靠的解码在网关侧（它已经解出来了 ✓），
**下轮不要再用我那个切帧器下结论**（除非修好它）。

## 4. 现在"哪些是好的、哪些没好"

| 层 | 内容 | 状态 |
|---|---|---|
| ① 桌面 asar | v2 会话 `instruction-hint` 白名单 | ✅ 已装（`app.asar`=121348401，备份在） |
| ② App Compose | 我 V3.50 引入的"空表覆盖"回归 | ✅ 已修 + 真机验证过一次 |
| ③ 网关 | history 非 event 抛错 / 缺 maxMessages | ✅ 已修（补丁生效、日志在写） |
| ④ **App 映射 v4 事件** | 53 条事件一条都不生成消息 | ⚠️ **本轮定位完成，修复待做** |

## 5. 下一步（已收敛到一步之遥）

1. 让 App 打出**一条真实 `user/message` 事件的完整结构**（最后一条诊断，已加代码、待取到读数）；
2. 按 v4 实际字段名改 `applyEvent`（**兼容 v2/v3 与 v4 两种 shape**，不再静默 return）；
3. 真机复验：`items>0` + 屏上能看到消息 = 才算修好；
4. 清掉屏上的 `DBG …` 临时读数。

**这一条修好后，"聊天记录全空"才算真正闭环**；在那之前我不会说"已修复"。
