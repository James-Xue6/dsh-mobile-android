# GlassUI 迭代报告 V3.24 | 2026-10-09

> **触发**：用户选「先把这些面板搬进 GlassSheetOverlay」。
> 本轮给骨架加**「列表」形态**并把**「选择模型」**搬过来（§6 第 3/6 条的第一块）。
> **A 类质感数值改动 = 0**。

---

## 1. 骨架新增「列表」形态（`GlassSheet.kt`）

与已有的「菜单 / 确认 / 表单」三形态并列的第四种：

| 新增 | 说明 |
|---|---|
| `class GlassSheetOption(label, sub, selected, group)` | 候选项：主文案 + 副文案 + 是否当前项 + **分组标题**（同组只画一次） |
| `GlassSheetState.showList(tag, title, message, options, closeLabel, onPick)` | 单选列表；点一项 → 回调下标 → **自动收起** |
| `SheetOptionRow(...)` | 一行：当前项**加粗 + 打勾**、底色用 `Ui.BRAND_SOFT` 区分；同 [SheetRow] 一套尺寸/行高系数 |
| 收口按钮 | 列表形态显示 `closeLabel`（默认「关闭」），其余形态仍是「取消」 |
| 高度封顶 | `.heightIn(max = 列表高上限).verticalScroll(...)`，上限 = **面板可用高 × 0.55**（与旧 `ui/ModelSheet.MaxHeightScrollView` 同比例） |

⚠️ 两个坑（都已按本项目既有教训处理）：
1. `maxHeight` 只能在 `BoxWithConstraints` 作用域里取出来用（进到内层 Column 的 content lambda 后**不能再隐式访问**，编译期直接报错）→ 先 `val listMaxHeight = maxHeight * 0.55f`。
2. 列表行的 `glassTap` **排在 `clip/background` 之前**（V3.21 按压双影教训）。

## 2. 对话屏补上弹窗骨架

原来**只有设备屏**有 `GlassSheetState` + `GlassSheetOverlay`；对话屏压根没有 ⇒ 面板无处可弹。
本轮：
- `GlassChatState` 新增 `val sheet = GlassSheetState()`；
- `GlassChatScreen` 在**取样层之外**渲染 `GlassSheetOverlay`（与设备屏同款，否则糊不到背后页面）。

## 3. 第一块面板落地：「选择模型」

`MainActivity.onModels(frame)` 改为优先走玻璃列表（`glassModelList(frame)`）：
- 数据映射与旧 `ui/ModelSheet.show(...)` **逐字段对齐**：分组标题 = provider 名；副文案 = 「思考等级 Off / Low / High …」；
  当前项 = `model==current.model && provider==current.provider`；
- 选中 → 同一件事：`gw.selectModel(sessionId, provider, model, "")`；
- **搬不动就回退**：`GlassChatView` 未启用 / 目录为空 ⇒ 仍走旧 `ModelSheet`（白面板）。
- `GlassChatView` 新增 `showSheetList(...)`（开关关掉时返回 false，调用方回退）。

### 实测（模拟器已配对真实网关）
| | |
|---|---|
| 点 `模型 · Deepseek-V4.1-Flash` | ✅ 弹出**玻璃**列表面板：标题「选择模型」+ 副标题「切换只影响这条会话」+ 分组「wb」+ 8 个模型（每个带「思考等级 …」副行）+ 当前项 **Deepseek-V4.1-Flash 高亮打勾** + 「关闭」 |
| 列表封顶/滚动 | ✅ 底部 `Deepseek-V4-Pro` 被裁在面板内、可滚动（55% 上限生效） |
| 崩溃 | ✅ `logcat -b crash` 0 条 |

**对比取证**：`evidence/v3260/16_model_panel.png`（改前·不透明白面板） vs `22_model_glass.png`（改后·玻璃列表）。

## 4. 余下 6 块（同一模式，逐个搬）
思考等级 / 权限 / 项目 / 用量 / 生成物 / 任务 —— 数据源与旧面板：
`MainActivity.onPickEffort→(内联列表)`、`onPermissionOptions→(内联列表)`、`onProjectTap→WorkspaceSheet(项目)`、
`onUsageTap`、`onArtifacts→WorkspaceSheet`、`onTasksTap`。**都可用「列表」形态（有的要加网格/多行）**。

## 5. 补丁（同日）：选中行「变成实的了」

**用户报**：「还行，但是**选择后怎么变成实的了**」。

**定位**：`SheetOptionRow` 的选中行原用了 `Ui.BRAND_SOFT` = **`0xFFE9F2FF`（完全不透明）**
⇒ 当前项是一块**实心方块**，与旁边能透出背景的行格格不入
（放大取证 `evidence/v3260/31_rows_zoom_before.png`：选中行纯淡紫不透明 / 未选中行透出绿色）。

**修法**（一处）：改用项目既有的**半透明**选中态 **`Ui.SELECT_BG`**（浅色 = 黑 6% / 深色 = 白 14%）
—— 与旧 `ui/ModelSheet` 的「当前项」**同一套语言**；「当前项」仍靠**加粗 + 打勾**表达。

**实测**（选中行 vs 相邻未选中行，同一 x 区间）：
| | 选中行均值 | 未选中行均值 | 差 |
|---|---|---|---|
| 修前 | 237.7 | 215.2 | **+22.5**（实心偏亮） |
| 修后 | 188.9 | 215.2 | **−26.2**（半透明暗洗，背景照旧透出） |

取证：`evidence/v3260/31_rows_zoom_before.png` → `34_rows_zoom_after.png`。

> 📌 **给后续搬其余 6 块面板的自已**：列表/选项的"选中态"一律用 **`Ui.SELECT_BG`**（要更强调用 `SELECT_BG_HI`），
> **不要**用 `BRAND_SOFT`/任何 `0xFF…` 不透明色 —— 那样一定"变实"。

## 6. 追加（同日·续）：又搬了 2 块面板 + 修掉 2 个 chip 差异

### 6.1 「思考等级」搬进玻璃（已验）
`onPickEffort` 里先用骨架的列表形态渲染（`glassOptionList(...)`，标签=档位名、当前项=`lastEffort`），
选中走原逻辑 `lastEffort=id; convo.setEffortLabel(id); gw.selectModel(sid, provider, model, id)`；
玻璃路由不可用才回退 `ModelSheet.showOptions`。
实测：`思考等级 / 当前模型：Deepseek-V4.1-Flash / Off / Low / High / Max / 关闭`（`evidence/v3260/42`）。

### 6.2 「权限」搬进玻璃（已验）
`onPermissionOptions` 里同样先走玻璃列表（标签=`name` 或 `permissionLabel(value)`；副文案=`permissionDetail(value)`；
当前项=网关给的 `cur`），选中仍走 `gw.selectPermission(sid, value)`（含 `canSend()` 守卫）；
不可用才回退。
实测：`权限 / 只影响这条对话；权限越大，Agent 能改的文件越多 / read-only（只能看…）/ workspace-write（只能改…）/
danger-full-access（整个电脑都能改，谨慎使用）✓ / 关闭`（`evidence/v3260/45`）。

### 6.3 顺手修：**「权限」chip 在 Compose 侧压根不显示**（差异 bug）
旧 `ConversationView` **构造时就固定有「权限」这枚 chip**（初值「权限」），而镜像原来只在标签非空时才加
⇒ 权限帧没回来时 Compose 侧少一枚 chip、**用户点不到权限面板**。
修：`GlassChatView.rebuildChips()` **永远包含权限这枚**（缺省文案「权限」），并在构造时就先铺一次 chip 行。

### 6.4 顺手修：chip 一多就被**裁掉**
chip 行原来是普通 `Row` + 居中；加上「权限」后常态 4 枚，实测最后一枚 `思考 · low` **只剩 10px**（被裁）。
旧视图用的是 **HorizontalScrollView** ⇒ 改为 `Modifier.horizontalScroll(...)` + `Arrangement.Start`（与旧行为对齐）。
实测：4 枚 chip 完整可见（`evidence/v3260/44`）。

## 7. 追加（同日·再续）：搬「项目」+ chip 行 parity 补齐

### 7.1 「项目」搬进玻璃（已验）
`onProjectTap` 先走 `glassOptionList("project", "切换项目", "选中即在那个工作区新建对话", …)`；
当前项 = `curKey`；选中仍走原逻辑 `onNewTaskHere(g)`；不可用回退旧面板。
实测：程序开发(7 个对话) / default-workspace(12) / desktop(2) / 测试(1) / 书籍分析(1) / 工作台设计(19) …（`evidence/v3260/51`）。

### 7.2 chip 行与旧视图**逐项对齐**
`GlassChatView.rebuildChips()` 补上旧视图里另外两枚：
- **生成物**（旧视图构造时就固定存在）—— 常驻；
- **任务 · d/t**（旧视图 `setTasksProgress`：`total≤0` 整枚隐藏）—— `setTasksProgress` 里同步刷新。
顺序与旧视图一致：项目 / 模型 / 权限 / 生成物 / 思考 / 用量 / 任务。
实测：滑动后可见 `模型 / 权限 / 生成物 / 思考 · low / 用量 · 61%`（`evidence/v3260/54`）。

### 7.3 ⚠️「任务」面板**代码已改但本轮未验**
当前会话没有任务列表 ⇒ 按旧视图规则那枚 chip **整枚隐藏**、点不到 ⇒ 面板无从验。
**状态：⏳ 未验证**（等一条真有 todo 的会话再验）。其余逻辑与「思考/权限/项目」同一套模板。

### 7.4 顺带发现（待定）：Compose 弹窗**不响应系统返回键**
旧 `ModelSheet` 等是 `Dialog`（按返回即关）；新骨架是屏内 overlay，**按返回不会关**（实测：面板开着按返回，面板仍在）。
选项：加返回键处理（`BackHandler` 需要 `OnBackPressedDispatcherOwner`，本工程是纯 `android.app.Activity`，要另想办法）或维持现状。**未改，待用户定。**

## 9. 追加（同日·三续）：搬「生成物」+ 骨架支持长按

### 9.1 先纠正一个错判
交接/我上一轮把「生成物」当成 `WorkspaceSheet`（工作区文件浏览）—— **不对**：
`ui/WorkspaceSheet` 是**工作区「⋯」**（"在此工作区新建任务"）；
真正的「生成物」在 `MainActivity.onArtifacts()` + `showArtifactsPanel()/renderArtifacts()`，
且 **2026-10-06 已按用户要求删掉目录下钻** ⇒ 它就是**扁平列表**（点=应用内预览、长按=下载/复制路径）。

### 9.2 骨架新增：**可选长按**
`glassTap(...)` 增加 `onLongClick: (() -> Unit)? = null`（非 null 时走 `combinedClickable`，null 时仍走原来的
`clickable` ⇒ **其余 ~30 个调用点行为一字不变**）；列表形态随之支持 `onListLongPick`。
> ⚠️ **参数顺序坑（本轮实测）**：`onLongClick` **必须排在 `onClick` 之前**。
> Kotlin 尾随 lambda 绑到**最后一个**参数，放末尾会让所有 `.glassTap { … }` 老写法报
> `No value passed for parameter 'onClick'`。

### 9.3 骨架新增：**就地改某行副文案**
`GlassSheetState.updateOptionSub(index, sub)`（旧面板是拿 TextView 引用原地 setText，这里等价地对
`listOptions` 赋新值触发重组）——「生成物」的下载进度靠它。

### 9.4 「生成物」搬进玻璃（空态已验；行交互 ⏳ 未验证）
`onArtifacts()` 优先走 `glassArtifacts()`：点=应用内预览（进度写到该行副文案）；长按=旧二级弹窗
（下载到手机/复制路径）；不在工作目录内 ⇒ 如实写"手机端看不了/下不了"；空态也走玻璃面板。
实测（`evidence/v3260/60·61`）：玻璃面板、空态文案、「关闭」都正确，crash 0。
**⏳ 未验证**：本会话没有产出文件 ⇒ **行渲染 / 点击预览 / 长按二级弹窗都没跑到**（等有 FILES 交付的会话再补验）。

### 9.5 顺手修：空列表时收口按钮退化成「取消」
原来用 `listOptions.isNotEmpty()` 判断 ⇒「生成物」空态（列表形态但 0 项）被当成非列表形态、显示「取消」。
改为**显式标志** `listActive`（`showList` 置 true，`clearForm` 置 false）。

## 11. 追加（同日·四续）：搬「用量」——7 块面板全部搬完

### 11.1 骨架新增：列表项**右对齐数值**
`GlassSheetOption(..., trailing)` → 行末右对齐、加粗（键值型面板用它才像"明细"，否则只剩一堆标题）；
`updateOptionSub` 重建行时保留该字段。

### 11.2 「用量」搬进玻璃（已验）
- `onUsageTap` 优先 `glassUsagePanel(lastUsage)`（先拿手上那份**秒出**，再拉 `sessionStats`）。
- **数据推导只留一处**：把旧 `renderUsage` 的行推导抽成 **`usagePairs(frame)`**（返回 `{label,value,strong}`），
  旧面板与玻璃面板**共用**，免得两边慢慢分叉；空态文案抽成 `usageEmptyHint(frame)`。
- **就地刷新**：`onSessionStats` 里若玻璃面板正开着（`isSheetTagVisible("usage")`）就 `setSheetOptions(...)`
  整套替换 ⇒ 数据到达后明细自己更新。
- 实测（`evidence/v3260/72`）：合计 289.8M tok / 缓存命中 99% / 未缓存输入 2.9M tok / 缓存读取 286.3M tok /
  输出 555.5k tok / 上下文占用 347.9k / 1.0M（35%），**数值右对齐**，crash 0。

### 11.3 ⚠️ 踩坑：Kotlin 默认参数对 Java **不可见**
给 `GlassSheetOption` 加第 5 个参数（带默认值）后，Java 侧 `new GlassSheetOption(a,b,c,d)` **全部编译失败**
（Kotlin 默认值不生成 Java 重载）。修法：构造器加 **`@JvmOverloads`**（一次修好，比改 6 处调用点干净）。

## 12. 状态
| 项 | 状态 |
|---|---|
| 骨架「列表」形态 | ✅ 已加 |
| 对话屏弹窗骨架 | ✅ 已挂 |
| 「选择模型」搬进玻璃 | ✅ 已改 + 已验（真实数据） |
| 「思考等级」搬进玻璃 | ✅ 已改 + 已验（`evidence/v3260/42`） |
| 「权限」搬进玻璃 | ✅ 已改 + 已验（`evidence/v3260/45`） |
| 权限 chip 缺失 + chip 被裁 | ✅ 已修（镜像 parity + 可横滑，`evidence/v3260/44`） |
| 「项目」搬进玻璃 | ✅ 已改 + 已验（`evidence/v3260/51`） |
| 「任务」面板 | ⏳ **代码已改、未验**（当前会话无 todo ⇒ chip 整枚隐藏） |
| 「生成物」搬进玻璃 | ✅ 面板/空态已验；**行点击/长按 ⏳ 未验证**（本会话无产出文件，`evidence/v3260/60·61`） |
| 骨架：可选长按 + 就地改行副文案 | ✅ 已加（`glassTap(onLongClick=…)` / `updateOptionSub`） |
| 「用量」搬进玻璃 | ✅ 已改 + 已验（键值明细 + 数值右对齐 + 新帧就地刷新，`evidence/v3260/72`） |
| Compose 弹窗不响应返回键 | ⏳ 已发现，待定是否修 |
| §6 第 6 条 ⋯ 菜单本体 | ⏳ 待改（现在是 `Ui.dialog` 的 3 项菜单：重命名/停止当前回合/连接设置） |
| §6 第 3 条 ②③（子智能体折射 / 加号·子智能体弹窗） | ⏳ 未动 |
| A 类质感数值改动 | **0** |
