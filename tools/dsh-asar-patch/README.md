# DSH 本体补丁：修 v2 会话迁移白名单（`instruction-hint`）

> 背景与完整证据：`docs/会话v2迁移失败-根因与处置.md`
> 症状：手机端反复弹 `failed to observe session "…": cannot safely transform unclassified message source`；
> 根因：宿主 v2→v4 迁移的 `SOURCE_KINDS` 白名单漏了 **`instruction-hint`**（29 条 `user/message` 记录、
> 16/18 个 v2-only 会话中招 ⇒ 整条会话读不出来）。

## 这个补丁做了什么

在 `app.asar` 里**只改 2 个文件**（外科式字节补丁，其余 12965 个条目字节级不变、1497 个解包文件不动）：

| 文件 | 改动 |
|---|---|
| `dsh/node_modules/@deepseek-ai/dsh-session-format-v2-to-v3/lib/index.js` | `SOURCE_KINDS` 加一项 `"instruction-hint"` |
| `dsh/node_modules/@deepseek-ai/dsh-session-persistence-jsonl/lib/worker.cjs` | 同上（第二份拷贝） |

同时**重算这两个文件的 `integrity`（SHA256 + 4MB 分块）**，并重排后续文件偏移。
（已确认 `EnableEmbeddedAsarIntegrityValidation` fuse = **Disabled**，但仍按规范更新 integrity。）

## 怎么用

1. **生成补丁**（DSH 开着也能跑，只写 `app.asar.patched`，不动原文件）：
   ```powershell
   $node='C:\Users\Administrator\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe'
   & $node tools\dsh-asar-patch\patch-asar.mjs
   ```
   脚本会**自校验**：逐条重读新档案并比对（除补丁的 2 个文件外必须完全一致）+ 打印 integrity 对账。

2. **安装**（必须在 DSH **完全退出**后）：双击
   `tools\dsh-asar-patch\安装补丁-关闭DSH后双击.bat`
   —— 它会：备份 `app.asar` → 装入补丁 → 重新启动 DSH；失败会自动还原。

3. **验证**：重启后打开任意一条原先读不出来的会话（如 `工作台设计` 里的「你现在能用吗」），
   若其目录里出现 `session.v4.jsonl.zstd` ⇒ 迁移成功。也可直接跑：
   ```powershell
   & $node tools\dsh-v2-kinds\scope.mjs 'C:\Users\Administrator\.dsh\sessions\--F-AI-工作台设计--'
   ```
   （注：`scope.mjs` 统计的是"v2-only"，迁移成功后 v2-only 会减少。）

4. **回滚**：把 `resources\app.asar.bak-<时间戳>` 改回 `app.asar` 即可。

## ⚠️ 重要：DSH 升级后补丁会丢

`app.asar` 随升级被覆盖 ⇒ 补丁失效（症状会回来）。升级后**重跑第 1、2 步**即可。
如果上游已把这件事修好（白名单含 `instruction-hint`），就不要装了 —— 先跑：

```powershell
Select-String -Path 'D:\AI\DSH\resources\app.asar' -Pattern 'instruction-hint' | Select-Object -First 3
```

## 已知边界

- 本补丁只解决**已确认的那一个阻塞 kind**（`instruction-hint`）。若某条会话迁移时**还**撞上别的断言
  （`assertSource` 每个 kind 另有键校验，迁移还有别的检查），那条会话仍会失败 —— 届时按同样流程加证据再补。
- `fallback` / `provider` 是**标题来源** kind（`session/title`），`assertSource` **不覆盖**这些事件 ⇒ 无需进白名单。
