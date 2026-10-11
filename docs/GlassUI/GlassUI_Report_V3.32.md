# GlassUI 真机迭代报告 V3.32（2026-10-10 晚 · 完整归档 / 交接版）

> 本文件**取代** `GlassUI_Report_V3.31.md`（13:07 那版只写到"闪退根因+卡顿归因"，之后的所有改动都没进）。
> V3.29/V3.30/V3.31 作为历史轮次保留，**以本文件为准**。
> 真机：`A7QK023224004563`（HONOR PGT-AN10，1312×2848 @560dpi，Android 16）。
> 本轮产物：`dist/dsh-mobile.apk`（最新 sha256 `235366f74accb265…`，官方签名，已装机 `Success`）。

---

## 0. ⚠️ 铁律（今天用两次真机崩溃换来的）

**对话页（`GlassChatScreen`）不能新增 `RefractionWindow` 玻璃件 —— 会 native 崩溃。**

两次崩溃栈**完全同源**（dropbox `data_app_native_crash`）：
```
signal 11 (SIGSEGV)  Cause: stack pointer is not in a rw map; likely due to stack overflow
512 total frames（截断）
#00…#259+  /system/lib64/libhwui.so
           android::uirenderer::computeTransformImpl(DirtyStack const*, Matrix4*)+56   ← 递归爆栈
```
| 次 | 触发改动 | 结果 |
|---|---|---|
| ① | 下拉刷新…不，是 **右下"回到底部"圆钮用 `GlassRoundButton`** + 同批又改了导轨结构 | 启动 1.5s 即崩（12:59，3 条记录）→ **整批回退** |
| ② | ↓ 圆钮**单独**换回 `GlassRoundButton`（与顶栏 ‹/⋯ 同款） | 复现同样栈 → **回退为非玻璃观感版** |

与工程既有记录同源：`Ui.java:2113`「**RenderNode + RenderEffect 在本工程必崩**（RenderThread SIGSEGV）」。
⇒ 想给新按钮"真玻璃"，**唯一安全路**：**复用同层已有玻璃子树**（例如 chips 的 `RefractionCapsule` 那一族），
且**只改一处 + 只验"冷启动"**（两次崩都在启动/首次滚动即发生，这个测试就能筛掉）。

---

## 1. 今天已完成并验证（按用户原话逐条）

| 用户原话 | 做法 | 状态 / 证据 |
|---|---|---|
| 「对话界面思考过程卡片可以点击缩小」 | 关键：数据一直在（Java `model/ChatItem:41 reasoning`、`MainActivity:6144` 在填），**只是 Compose 没渲染** ⇒ 新增 `ThinkingCard`（默认 `▸ 思考过程 / N 字`，点一下展开/收起，复用 `MsgCard` 自带阴影+质感） | ✅ 模拟器 `v3270/390`(收)→`391`(展)；**真机实测** `▸ 思考过程 | 2522 字` |
| 「抽屉点项目/卡片⋯ 界面未优化」 | 两处 ⋯ 曾改玻璃浮层（后因崩溃根因改回宿主 Java 菜单，见 §2.1） | ✅ 曾实测通过：工作区 ⋯ `v3270/383`、会话卡 ⋯ `v3270/385`（Java 版仍需真机复验） |
| 「玻璃弹窗按返回键没反应」（遗留 B） | `GlassSheet.kt` 加 `BackHandler`，⚠️ 首版**直接崩**（`No OnBackPressedDispatcherOwner`，因浮层挂在纯 Activity 的 ComposeView 上）⇒ 改为**先判 owner 存在再挂** | ✅ `crash=0` |
| 交接 §6 #4「顶部与内容分割太硬」 | 顶部渐变罩 3 段（1.0/.86/0，折点明显）→ **5 段平滑**（1.0/.96/.72/.34/0） | ✅ |
| 「卡中卡阴影吞内容」 | 根因：我在**原生 canvas** 上 `save/clipOutPath/restoreToCount` 只画形状外 ⇒ 嵌套绘制泄漏状态、该节点内容整条不绘制。改用 **Compose `clipPath(ClipOp.Difference)`** | ✅ 任务条 4 条全部正常 + 阴影恢复 `v3270/430` |
| 「带目标对话：内容穿过顶栏不截断」 | 头块**单独测量**（只包头）+ `zIndex(1f)` 画在列表之上 + 列表 `offset(-headDp)` + `contentPadding.top=headDp+2dp` | ✅ `v3270/370·372·430`，crash=0 |
| 「下方把对话截断了」（我引入的回归） | 回归根因：`offset(-headDp)` 把**列表底边也抬高**了 ⇒ 底部被切。父容器 `Column`→**`Box`**，列表 `fillMaxSize()` 占满全屏、头块 `zIndex` 浮上 | ✅ 真机截图 `v3270/500`：最后一条气泡完整、输入区完好 |
| 「侧面的滚轮/滚动条一直没配好」 | **找对参照物**：不是你截图那版"虚线+滑块"，也不是桌面 turn-rail 短横条 —— 是**你朋友 fork 的 `ui/MessageScrubber.java`**（注释：「右侧**问题刻度尺**」）。按其规格移植到 Compose：**固定 10 格**、格距 13dp（1:1 跟手）、刻度 13×2.6dp、**选中格 22×3.6dp**（主色）、**30dp 半透明灰底**（浅 `0xEBF7F6FB`/深 `0xEB1C1B22`）、上翻=往期/下翻=最新、拖动时出**预览气泡** | 🔶 已移植；**真机待复验**（见 §2.2） |
| 「右侧这个刻度条不能用」 | 致命 bug：刻度条 `Canvas(fillMaxSize())` 外层**没限宽** ⇒ 触摸层**铺满整屏**、把所有滚动手势吃掉 ⇒ 页面也点不动。修：**限宽 48dp**（与朋友 40dp 触摸带同口径） | ✅ 已装；真机待复验 |
| 「往下的按钮…和子智能体条重叠，往上点，加玻璃质感」 | 位置**自适应**：无「N 子智能体」条 ⇒ `+146dp`、有 ⇒ `+204dp`；玻璃：`GlassRoundButton` 试过**崩**（§0②）⇒ 最终用**非玻璃观感版**：4 段白色纵向渐变（顶 .64→.34→.16→底部品牌淡染 `#EDEBFF .26`）+ `0.6dp` 白 `.62` 发丝边 + **顶部受光弧** + **底部内阴影**（全部 `drawBehind` 纯绘制 ⇒ 不增加节点） | ✅ 已装 `sha256 235366f7…`；真机待复验 |
| 「目标看不到」 | **已定论（数据侧）**：日志页 98 行里**没有任何「目标帧」记录** ⇒ `applyGoal()` **从未被调用** ⇒ **网关没给该会话推 goal 帧**；同时 `tasks 帧 sid=… n=null` ⇒ 任务帧无 todos。App 侧已加**自证留痕**：`applyGoal()` 里写 `目标帧 keys=[…] objective.len=… phase=… 头24码元=…` → 事件流 → **日志页** | 🔶 App 侧已就绪；**等你打开日志页看有没有那行**即可定性 |

### 1.1 交付包 / 鉴权
- `dist/dsh-mobile.apk`（**官方签名 release**）：最新 `sha256=235366f74accb265…`；签名指纹 `2e518d75…8ece8e` ✓（可覆盖安装）。
- ⚠️ `build.ps1`（免 Gradle 老脚本）**已失效**：只编 Java、不认 Kotlin/Compose（166 错）。
  正式包一律 `gradle :app:assembleRelease`；签名口令来自 `keystore.local.ps1` 的 **`$KsPass`** → `$env:DSH_KS_PASS`，
  且**必须先 `Remove-Item Env:DSH_KS`**（它是空串 ⇒ `storeFile=""` ⇒ `Keystore file not set` / `validateSigningRelease` 失败）。

---

## 2. 未完成（下个会话从这里接）

### 2.1 抽屉两处菜单的**玻璃观感**（当前是宿主 Java 菜单）
为保稳定，崩溃排查期间把工作区 ⋯ / 会话卡 ⋯ **回退成宿主 Java 菜单**（`GlassSessionHost.sessionMenu(s)` → `MainActivity` 用 `Ui.dialog`）。
要玻璃观感必须走**树内浮层**（屏根 `Box` + `zIndex`，状态提升到 `GlassSessionState.menuFor/wsFor`），**绝不能再开 `Compose Dialog`**
（`Dialog` 会开新窗口，纯 `Activity` 的 ComposeView 拿不到 `ViewTreeLifecycleOwner` ⇒ 真机 dropbox 里 **10 次** `IllegalStateException` 崩溃）。

### 2.2 刻度条手感（下一步，优先级最高）
已按 `MessageScrubber` 移植 + 限宽 48dp。**待办**：真机复验"拖动 1:1 跟手 / 跳转准确 / 气泡不挡手势"；
若仍不跟手，按朋友文件里的 `slotY()/posF` 逻辑逐行对齐（他的 `frac` 错位感、`OnPick(index)` 回调时机）。

### 2.3 目标胶囊"规定玻璃质感"
按 §0 铁律：**复用同屏已有玻璃子树**（chips 的 `RefractionCapsule` 族），只改这一处，**只验冷启动**通过再装机。

### 2.4 卡顿优化（已归因，未实施）
`dumpsys gfxinfo`（真机、滚动采样）：**Janky 9.63%（401/4163）**，P50 11ms / P90 22ms / **P99 93ms**；
`Slow issue draw commands 395`（最大头）、`Slow bitmap uploads 123`、`Slow UI thread 199`。
主因 = **我加的自绘阴影**（每元素一张模糊位图 + `clipPath` 易触发 layer）+ 位图上传 + 导轨脉动动画。
**方案（未做）**：① 阴影位图**半分辨率**（≈1/4 像素/上传）；② **滚动中跳过列表行阴影**（`isScrollInProgress`）；③ 列表行阴影改浅描边；④ 导轨脉动改 `graphicsLayer` lambda 避免每帧重组。

### 2.5 其它挂起
- 交接 §6 **#8 顶栏高光对比**：折射已按 #4 与输入框统一为 14/28；再提高"高光/受光棱"要动**组件本体**（SOP 锁定）⇒ **需用户点头**。
- 弹窗「内容与实际有些缺失」：需用户举一例。
- 任务面板补验：需一条**有 todos 的真实会话**（网关当前 `tasks 帧 n=null`）。
- v2 会话迁移补丁：关 DSH 后双击 `tools/dsh-asar-patch/安装补丁-关闭DSH后双击.bat`。

---

## 3. 取证与流程备忘
- **崩溃**：`adb -s A7QK… shell dumpsys dropbox --print`（HONOR ROM 的 native 栈在这里；`logcat -b crash` 常只有 "(HKS)…" 加密头）。
- **卡顿**：`adb -s A7QK… shell dumpsys gfxinfo com.dsh.mobile`（看 `Janky frames` / `Slow issue draw commands` / `HISTOGRAM`）。
- **模拟器**（仅参考）：`C:\Users\Administrator\emu_copy\emulator\emulator.exe -avd Pixel_7_API35 -no-snapshot -gpu host`；
  调试钩子 `--ez glass_force_chat/glass_force_drawer/glass_force_device/glass_force_press true`（**用完必须重启 App**，否则停在"全按下"态）。
- **证据目录**：`evidence/v3270/*`（模拟器）；`evidence/phone-logs-20261010/*`（真机 dropbox / gfxinfo / 各界面 dump）。

## 4. 我的教训（本轮踩的，写给下一个会话）
1. **PowerShell 单引号里 `` `n `` 不展开** —— 已经被坑 4 次。改用 **here-string（`@'…'@`）** 或直接用 `edit` 工具做精确替换。
2. **长命令会因超长导致 adb 调用失败**（`文件名或扩展名太长`）—— 安装/构建拆开单独跑。
3. **每次改完必须"编译 + 装机 + 冷启动"验证**，且**一次只装一处改动**（本轮把新玻璃件和结构改动混在一批，崩了只能整批回退、无法定位）。
4. 我本轮出现过"改动把 `padding`/括号弄丢"的低级失误，均当场修复并重新验证 —— **但仍然不该让你陪我试错**。
