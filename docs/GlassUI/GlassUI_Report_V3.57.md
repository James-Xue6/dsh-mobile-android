# GlassUI 迭代报告 V3.57 | 2026-10-11 凌晨（**真机"所有会话全空"的根因与修复**）

> **触发**：用户「你修这个的时候消息不显示了」——**这句话就是决定性证据**：
> 全空是我 **V3.50 修"吐字一顿一顿"** 那次改动引入的。

---

## 1. 根因（已由真机对照实验锁定）

`GlassChatView` 的镜像路径里有**两次**写 Compose 状态：

```java
state.setItems(new ArrayList<>(items));          // ① 立刻：原始列表（对）
pullVisibleLater();                               // ② 120ms 后：用 legacy.visibleItems() 覆盖
```

② 是**无条件覆盖**：旧视图的 `applyFilter()`（60ms 延时）**还没算出结果时会返回空表**，
于是 `state.items` 被覆盖成 **[]**；而初始加载之后**不会再有新的 `setItems`** ⇒ **永久空**
（且这条路径**不报错**，所以既没有横幅、也不崩）。

V3.50 我把 ① 改成 70ms 节流、并把 ② 的多次排队改成"只保留一个待执行"，
**改变了②的触发时机**，从此每次会话打开都会踩到"过滤未就绪"的空表 ⇒ 全空。

### 真机对照（决定性）
| 构建 | `USE_GLASS_CHAT` | 真机 `uiautomator dump` 结果 |
|---|---|---|
| V3.53 | `true`（Compose） | 只有顶栏/chips/输入框，**0 条消息**（720/710） |
| V3.57 诊断版 | `false`（旧视图） | **消息全在**：「全都是空的」「全空」「执行了命令 · pwsh」「已读取文件，修改了文件，执行了命令」 |
| V3.57 | `true` + 去掉 `SelectionContainer` | 头块回来了（`目标 … 0/0`、`1 子智能体`），**消息仍空**（721） ⇒ 锁定到②那一行 |

## 2. 修复（两处，均已编译通过）

```java
// GlassChatView.pullVisibleNow()
List<ChatItem> v = legacy.visibleItems();
// [V3.57] 过滤结果为空、而**原始列表非空** ⇒ 丢弃这次空结果（保留原始列表）
if (v.isEmpty() && !lastRawMirror.isEmpty()) return;   // ← 新增
pendingMirror = new ArrayList<>(v);
legacy.removeCallbacks(mirrorFlush);
mirrorFlushNow();
```
- 新增字段 `lastRawMirror`（`setItems` 里记录原始列表）用于判定"假空"；
- 过滤结果**非空**时照旧覆盖 ⇒ 简洁模式的过滤行为不变。

**附带**：撤掉 V3.49 我加的那层 `SelectionContainer`（它**整个包住 LazyColumn**，
不仅消息空，连**头块（目标卡）都被弄没**）⇒ **长按选择暂时关闭**，
之后要用"只包每条文字"的方式补回来（V3.49 报告里有当时踩到的性能数据）。

## 3. ⚠️ 未验证（卡在设备）

修复版已编译、`dist/dsh-mobile.apk` 已刷新（sha `8ec36a3e…`），但
**手机整条 USB 掉线**（`adb devices` 为空）、**模拟器也已退出** ⇒ **无法在本轮完成真机验证**。
需要的动作：把手机重新连上（USB 调试打开）或让我把模拟器当测试台重新拉起并配对，
我就能一条命令装上去 + `uiautomator dump` 确认"消息回来了"。

## 4. 教训（已写进错题本，severity=blocker）

改**"覆盖式同步"**逻辑时必须先问："这个源可能为空吗？为空是否合法？"
纯性能改动一旦落到**数据写入路径**，必须立刻在真机上看一眼结果——
不能只看"编译通过 + 测量台数字"（这次正是被测量台的数字吸引，漏掉了这条路径）。
