<#
[V3.60] 手机接入网关补丁：**history/snapshot 静默为空** 的处置 + 诊断。

## 症状（真机 + App 屏上诊断读数取证）
- App 侧 `items=0 raw=0 vis=0` ⇒ **数据根本没到 App**（不是渲染问题）；
- 但同一个会话的"目标/计划"等其它镜像**能到** ⇒ 只有 history/snapshot 这一路空；
- 全程**不报错**（没有横幅、不崩）。

## 已定位的两个可疑点（都在网关 `lib/dsh-host-adapter.mjs`）
1. `readHistoryRecords()`：`if (value.type !== 'event') throw ...`
   ⇒ 快照/分页里只要混进**一条非 event 记录**，整页 history 直接抛错 ⇒ 上层只看到"空"。
2. `history()`：不带 `beforeSeq`、也没显式 `maxMessages` 时，**请求里没有 maxMessages**
   ⇒ 核心可能返回**不含 records 的基线快照** ⇒ 一样是空。

## 本脚本做三件事（幂等、先备份、改完自检语法）
1. `readHistoryRecords` 改成**跳过非 event 记录**（并把它记进诊断日志），不再整页抛错；
2. `history()` 在"无 beforeSeq 且无 maxMessages"时**补 `maxMessages: 200`**（要尾部一页，而不是基线）；
3. 每次 snapshot 往 `~/.dsh/mobile-gateway-diag.log` 追加一行：
   `时间 会话 版本 records条数 hasMore`（**只记结构，不记内容**）。

## 用法
```powershell
pwsh -File pc-plugin\patches\patch-gateway-history-diag.ps1          # 应用
pwsh -File pc-plugin\patches\patch-gateway-history-diag.ps1 -Revert  # 回滚
```
改完**需要重启一次 DSH** 才生效。
#>
[CmdletBinding()]
param(
  [string]$Profile = 'desktop',
  [switch]$Revert
)

$ErrorActionPreference = 'Stop'
$gw = Join-Path $env:USERPROFILE ".dsh\profiles\$Profile\node_modules\dsh-plugin-mobile-gateway\lib\dsh-host-adapter.mjs"
if (-not (Test-Path -LiteralPath $gw)) { throw "找不到网关文件：$gw" }
$bak = "$gw.bak-history-diag"

if ($Revert) {
  if (-not (Test-Path -LiteralPath $bak)) { throw "没有备份可回滚：$bak" }
  Copy-Item -LiteralPath $bak -Destination $gw -Force
  Write-Host "[OK] 已回滚（从 $bak）"
  exit 0
}

# 幂等：已经打过就直接退出
$src = [System.IO.File]::ReadAllText($gw)
if ($src -match 'V3\.60 history/snapshot diag') { Write-Host "[i] 已经打过这个补丁，无需重复"; exit 0 }

if (-not (Test-Path -LiteralPath $bak)) { Copy-Item -LiteralPath $gw -Destination $bak -Force }

# ---- ① readHistoryRecords：跳过非 event 记录 + 记录诊断
$old1 = @'
export function readHistoryRecords(records) {
  return requireArray(records, 'session history').map(record => {
    const value = requireRecord(record, 'session history record')
    if (value.type !== 'event') throw new Error('DSH Session V4 history requires event records')
    const event = requireRecord(value.event, 'session history event')
    if (!Number.isSafeInteger(event.seq) || event.seq < 0) throw new Error('invalid history sequence')
    return event
  })
}
'@
$new1 = @'
export function readHistoryRecords(records) {
  // [V3.60 history/snapshot diag] 原来遇到非 event 记录**整页抛错** ⇒ 上层只看到"空"、且不报错。
  // 改成：**跳过**非 event 记录，并把"跳过几条 + 都是什么 type"记进诊断日志（只记结构，不记内容）。
  const list = requireArray(records, 'session history')
  const skipped = []
  const out = []
  for (const record of list) {
    const value = requireRecord(record, 'session history record')
    if (value.type !== 'event') { skipped.push(String(value.type)); continue }
    const event = requireRecord(value.event, 'session history event')
    if (!Number.isSafeInteger(event.seq) || event.seq < 0) throw new Error('invalid history sequence')
    out.push(event)
  }
  if (skipped.length > 0) {
    try {
      appendFileSync(diagLogPath(), `${new Date().toISOString()} skipped-non-event total=${list.length} events=${out.length} types=${skipped.join(',')}\n`)
    } catch { /* 诊断失败不影响主流程 */ }
  }
  return out
}
'@
if (-not $src.Contains($old1.TrimEnd())) { throw "① 未匹配到 readHistoryRecords 原文（网关版本可能不同）" }
$src = $src.Replace($old1.TrimEnd(), $new1.TrimEnd())

# ---- ② history()：无 beforeSeq 且无 maxMessages 时补 maxMessages=200
$old2 = @'
      ...(payload.beforeSeq !== undefined ? { maxMessages: 1 }
        : payload.maxMessages === undefined ? {} : { maxMessages: payload.maxMessages }),
'@
$new2 = @'
      // [V3.60 history/snapshot diag] 原来"无 beforeSeq 且无 maxMessages"时**请求里没有 maxMessages**
      // ⇒ 核心可能回**不含 records 的基线快照** ⇒ App 侧表现为"整屏空、且不报错"。这里补一页尾部。
      ...(payload.beforeSeq !== undefined ? { maxMessages: 1 }
        : payload.maxMessages === undefined ? { maxMessages: 200 } : { maxMessages: payload.maxMessages }),
'@
if (-not $src.Contains($old2.TrimEnd())) { throw "② 未匹配到 history() 原文" }
$src = $src.Replace($old2.TrimEnd(), $new2.TrimEnd())

# ---- ③ 诊断函数 + snapshot 记录
$anchor = "export const DSH_VERSION = '0.2.0-rc.2'"
if (-not $src.Contains($anchor)) { throw "③ 未找到插入锚点" }
$diag = @'
// [V3.60 history/snapshot diag] 只记结构（版本/条数/hasMore），不记内容。
function diagLogPath() {
  return (process.env.DSH_HOME ? process.env.DSH_HOME : (os.homedir() + '\\' + '.dsh')) + '\\mobile-gateway-diag.log'
}
'@
$src = $src.Replace($anchor, $diag.TrimEnd() + "`n" + $anchor)

$old3 = "export function readHistoryRecords(records) {"
$src = $src.Replace($old3, "import { appendFileSync } from 'node:fs'`nexport function readHistoryRecords(records) {")

$old4 = @'
  return {
    events: readHistoryRecords(frame.records).map(event => ({ event })),
'@
$new4 = @'
  try {
    appendFileSync(diagLogPath(), `${new Date().toISOString()} snapshot sid=${sessionId} v=${frame.header.version} records=${Array.isArray(frame.records) ? frame.records.length : -1} hasMore=${frame.hasMore === true}\n`)
  } catch { /* 诊断失败不影响主流程 */ }
  return {
    events: readHistoryRecords(frame.records).map(event => ({ event })),
'@
if (-not $src.Contains($old4.TrimEnd())) { throw "④ 未匹配到 readSessionSnapshot 的 return" }
$src = $src.Replace($old4.TrimEnd(), $new4.TrimEnd())

# ---- 写回 + 语法自检
[System.IO.File]::WriteAllText($gw, $src, (New-Object System.Text.UTF8Encoding($false)))
$node = 'C:\Users\Administrator\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe'
if (Test-Path $node) {
  & $node --check $gw 2>&1 | Out-Null
  if ($LASTEXITCODE -ne 0) {
    Copy-Item -LiteralPath $bak -Destination $gw -Force
    throw "语法自检失败，已自动回滚。请把上面的 node --check 输出发我。"
  }
  Write-Host "[OK] 语法自检通过"
}
Write-Host "[OK] 已打补丁：$gw"
Write-Host "     备份：$bak"
Write-Host "     **需要重启一次 DSH 才生效**；之后诊断日志会写在 %USERPROFILE%\.dsh\mobile-gateway-diag.log"
