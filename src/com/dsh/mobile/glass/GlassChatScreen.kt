package com.dsh.mobile.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn   // [V3.47] 目标胶囊"与子智能体条同高"
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloat
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.wrapContentWidth
// [V3.33] 刻度条"预览气泡"修复用：wrapContentSize(unbounded) 让子树按自身宽度量，requiredWidth 不受父约束夹取
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.roundToInt
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged   // [V3.27] 头块浮层按实测头高让位
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer   // [V3.49] 长按选择/复制
import androidx.compose.ui.input.pointer.PointerEventPass      // [V3.135] 长按在 Initial 通道观察
import androidx.compose.foundation.gestures.awaitEachGesture      // [V3.130] 长按识别（不依赖未消费的 DOWN）
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.asImageBitmap   // [V3.75] 待发附件缩略图
import androidx.compose.ui.graphics.graphicsLayer  // [V3.88] 全屏看图的缩放/平移
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.R
import com.dsh.mobile.model.ChatItem
import com.dsh.mobile.ui.Ui
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 【对话页 · 可托管版（V3.3）】把对话页接到**真实 `ChatItem` 数据**上。
 *
 * ## 与 V3.2 的关系
 * V3.2 定了**结构规则**（取样层 = 背景 + 滚动内容；玻璃件留层外；层内消息用半透明卡），
 * 本轮把消息内容从"写死的示范文案"换成**真正从 [ChatItem] 渲染** —— 也就是 `ChatAdapter.buildRow`
 * 那 9 类行（USER / ASSISTANT / TOOL / APPROVAL / QUESTION / FILES / AGENT / STEP / 系统条 + 时间分隔条）
 * 的 Compose 版。
 *
 * ## 数据来源（为什么直接吃 ChatItem 而不是再抄一层模型）
 * `ChatItem` 就是 `MainActivity` 内存里那份 `List<ChatItem>` 的元素类型（网关帧喂进来的）。
 * Compose 直接读它的公共字段即可，**不需要中间映射层**；宿主每次 commit 换一个**新的 list 实例**
 * （`setItems(new ArrayList<>(items))`）就能触发重组，语义与原 ListView 的 notifyDataSetChanged 一致。
 *
 * ## 动作
 * 全部经 [GlassChatHost] 回调宿主：发送/停止/附件/任务列表/更多/子智能体/加载更早/审批/提问提交/
 * 交付物下载/复制路径/计划展开。宿主（`MainActivity`）本来就实现了这些流程，接过去即可，功能零回归。
 */
class GlassChatState {
    /** 消息行（宿主推 `new ArrayList<>(items)`；字段可以原地改，换实例即重组）。 */
    var items by mutableStateOf<List<ChatItem>>(emptyList())
    /** [V3.57 诊断 · 验完即删] 原始列表条数 / 旧视图过滤结果条数 / 头块高度(dp)。 */
    var debugRaw by mutableStateOf(0)
    var debugVis by mutableStateOf(0)
    var debugHead by mutableStateOf(0f)
    /** [V3.61 诊断 · 验完即删] 快照事件映射时的异常 / 事件条数。 */
    var debugErr by mutableStateOf("")
    var title by mutableStateOf("对话")
    var subtitle by mutableStateOf("")
    /** 回合运行中（右钮在"有字=发送(排队) / 无字=停止"之间切换）。 */
    var running by mutableStateOf(false)
    var runningHint by mutableStateOf("")
    /** 状态横幅（连接/错误提示）。 */
    var banner by mutableStateOf("")
    var bannerError by mutableStateOf(false)
    /** 目标 / 任务提要（正式版 planView）。 */
    var planGoal by mutableStateOf("")
    var planTodos by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var planExpanded by mutableStateOf(false)
    /** 「N 子智能体」入口（0 = 不显示）。 */
    var subagentCount by mutableStateOf(0)
    /** 分页状态行（正在加载更早 / 失败可重试）。 */
    var moreStatus by mutableStateOf("")
    var moreStatusError by mutableStateOf(false)
    /** 只读说明（子会话回看等场景）。 */
    var readOnly by mutableStateOf(false)
    var readOnlyReason by mutableStateOf("")
    /** 待发送队列（正式版 pendingBar）。 */
    var pendingSends by mutableStateOf<List<String>>(emptyList())
    /**
     * [V3.75 · 用户「**左侧加号选文件/相册/拍照，选择文件之后没出现**，然后也没有之前弄得
     * **放到输入等待和文字一起发送**」]
     *
     * ⚠️ 根因：这里原来只是**一个数字（个数）**，而界面上**从来没渲染过它** ⇒ 用户选完看不到任何东西。
     * ⇒ 改成**附件列表**（每项编码 `kind\u0001uri\u0001name`，宿主侧 `PendingAttachment` 的镜像），
     * 并在输入区上方渲染**缩略图条**（图片显缩略图、文件显名字），条上一行字说明"会随文字一起发送"。
     */
    var pendingAttachments by mutableStateOf<List<String>>(emptyList())
    /**
     * [V3.81 · 用户「设置里面**完整和精简，为什么没有区别了**；完整是思考调用这些都展开的，
     * 精简就直接默认思考调用是折叠起来的」]
     *
     * `true` = 精简（思考/工具行**默认收起**）；`false` = 完整（**默认展开**）。
     * 由宿主 `GlassChatView.setCompact()` 镜像（`MainActivity` 换会话/改设置时都会调）。
     */
    var compact by mutableStateOf(false)
    /** 输入条上方的能力 chip（正式版：权限/模型/思考/用量/项目…，空则不显示）。 */
    var chips by mutableStateOf<List<String>>(emptyList())
    /** 输入框草稿 —— **双向**：宿主发送时要读它、发完要清它。 */
    var draft by mutableStateOf("")
    /** 目标卡的进度文案（正式版 `setTasksProgress`）。 */
    var planProgress by mutableStateOf("")
    /**
     * 「滚到底」信号：宿主调 `scrollToBottom()/refreshNow()` 时 +1 → 列表滚到最新一条。
     * Compose 侧用 `LazyColumn` 的 `animateScrollToItem` 实现（旧实现是 ListView.setSelection）。
     */
    var scrollToBottomTick by mutableStateOf(0)
    /** 主题版本（宿主切深浅色时 +1 → 整屏重组）。 */
    var themeRev by mutableStateOf(0)

    /**
     * [V3.24 · 交接 §6 第 3/6 条] 本屏自己的弹窗（统一弹窗骨架，见 [GlassSheetOverlay]）。
     *
     * <p>与设备屏同款：**渲染在取样层之外**才能真正糊到背后的页面。
     * 有了它，选择模型 / 思考等级 / 权限 / 项目 / 用量 / 生成物 / 任务 这些面板
     * 才能从旧的 Java 白面板逐个搬进玻璃骨架。
     */
    val sheet = GlassSheetState()

    fun bumpTheme() { themeRev++ }
    fun bumpScrollBottom() { scrollToBottomTick++ }
}

/** 对话页的全部动作（宿主实现；正式 App 里就是 `MainActivity` 的真实流程）。 */
interface GlassChatHost {
    fun send(text: String)
    /** 草稿变了（宿主侧 `convo.draftText()` 必须跟着走，语音/粘贴等流程依赖它）。 */
    fun draftChanged(text: String)

    /**
     * [V3.127 · 用户「输入框本身，长按调用本地的粘贴/选择那些一条按钮，**现在长按只有自动填充**」]
     *
     * 长按**空**输入框时交给宿主弹自己的玻璃菜单（粘贴 / 复制全部 / 清空）。
     * 为什么不用系统那条：Compose 的 `BasicTextField` 在**空内容**下长按不会给出系统「粘贴/全选」条
     * （被系统自动填充/输入法接管了）✗；而**有文字**时长按仍走系统选择，不抢 ✓。
     */
    fun draftLongPress() { }
    fun stopTurn()
    fun attachPick()
    fun openTasks()
    fun menu()
    fun openSubagents()
    fun loadMore()
    fun togglePlan()
    /**
     * [V3.23 · 交接 §6 第 3 条①] 能力 chip 被点了（用户报「chip 点不动」）。
     *
     * <p>传的是 chip 的**显示文案**（如 `模型 · Deepseek-V4.1-Flash` / `权限 · 完全访问` / `思考 · low`），
     * 由宿主按前缀路由到正式版已有的面板（`onProjectTap/onPickModel/onPickPermission/onArtifacts/onPickEffort/onUsageTap/onTasksTap`）。
     */
    fun chipTap(label: String)
    /** 审批：outcome 与正式版一致（"allowOnce" / "deny"）。 */
    fun approve(item: ChatItem, outcome: String)
    fun questionSubmit(item: ChatItem, answers: org.json.JSONArray)
    fun downloadFile(item: ChatItem, path: String)
    /**
     * [V3.76 · 用户「交付物能不能**不是点击下载**？能不能**先以打开为主**，打开后增加一个**保存**的按钮」]
     * 打开（预览）：把文件落到 App 私有预览缓存并打开预览页（预览页里再给「保存到手机」）。
     * 与 `downloadFile`（直接存进手机下载目录）分开，两条路宿主都已经实现。
     */
    fun previewFile(item: ChatItem, path: String)
    /**
     * [V3.78 · 用户「选好的图片，**待发送之后没法删除了**」] 移除一个待发附件（下标 = 附件条的序号）。
     * 宿主侧 `MainActivity.onRemoveAttachment` 早就实现了（旧视图的 × 就走它），这里只是把它接到 Compose。
     */
    fun removeAttachment(index: Int)
    /**
     * [V3.82 · 用户纠正]「**我让你改的是对话界面长按复制**，怎么成交付物了？！」
     *
     * ⇒ 对话消息（用户气泡 / 助手正文）**长按 = 复制这条消息的文本**。
     * （V3.57 移除 `SelectionContainer` 后，长按选中文本的能力没了 ⇒ 这里补一个"整条复制"。）
     */
    fun copyText(text: String)
    /**
     * [V3.85 · 用户「待发送这个栏不能**选修改、立即发送和删除**了」]
     * 点「待发送」条上的一条 ⇒ 宿主弹出「立即插入 / 修改 / 删除」（itemId 来自网关队列）。
     */
    fun queueItemAction(itemId: String, text: String)
    /**
     * [V3.89 · 用户「生成的报告 `GlassUI_Report_V3.86.md` 在聊天对话里面**还是无法直接点击**」]
     * 点消息里的**文件链接/路径** ⇒ 走"打开预览"（相对路径由宿主兜底，见 `startDownload` 的回退分支）。
     */
    fun openPath(path: String, label: String)
    fun copyPath(path: String)
}

/**
 * 对话页本体（纯 UI：只读 [state]、只调 [host]）。
 *
 * @param applyInsets 见 [GlassDeviceScreen] 的同名参数（挂进 MainActivity 时传 false，避免双倍留边）。
 */
@Composable
fun GlassChatScreen(
    state: GlassChatState,
    host: GlassChatHost,
    applyInsets: Boolean = true,
) {
    @Suppress("UNUSED_EXPRESSION") state.themeRev

    val backdrop = rememberLayerBackdrop()
    // [V3.104] 暴露给抽屉里的独立弹窗宿主 ⇒ 抽屉弹窗与模型/权限/生成物**采同一层、材质一致**
    androidx.compose.runtime.SideEffect { GlassChatBackdrop.layer = backdrop }
    // [V3.113 · 用户「粘贴配对串点开跑到对话界面、**成透明的了**」]
    //   ⚠️ 必须**销毁时清空**：这个 `backdrop` 只在聊天页被组合时才有效。切到设置/设备页后聊天页不再组合，
    //   而那个全局引用还留着 ⇒ 抽屉的全屏弹窗宿主会拿**一个已经没人绘制的层**去采样 ⇒ 出来就是**透明的** ✗。
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (GlassChatBackdrop.layer === backdrop) GlassChatBackdrop.layer = null }
    }
    // [V3.46 · V3.32 §2.3] 给**列表内容单独记一层**，专供"目标胶囊"取（见目标卡处的注释）：
    //   头块在取样层内部 ⇒ 直接用 `backdrop` 会自取样（"一片平白"）；这一层里只有观感版卡，安全。
    val contentBackdrop = rememberLayerBackdrop()
    val ink = Color(Ui.INK)
    val inkSub = Color(Ui.INK_SUB)
    val inkFaint = Color(Ui.INK_FAINT)

    // 行内动作（审批/提问/交付物）走 HostHolder，这里登记/注销当前宿主
    androidx.compose.runtime.SideEffect { HostHolder.host = host }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (HostHolder.host === host) HostHolder.host = null }
    }

    // 顶部安全区 / 底部手势区高度（dp）——**不是**一刀切 systemBarsPadding：
    // 用户要求「对话滑动一直穿到底、不要小白条、做成同材质」，所以背景与消息要画到屏幕最下沿，
    // 只有"头部内容/玻璃件"各自让开，列表用 contentPadding 让出底部手势区。
    val density = androidx.compose.ui.platform.LocalDensity.current
    // [V3.5 app-wide 全面屏] 顶部用 topSafeDp（systemBars ∪ **挖孔** ∪ safeDrawing，含 40dp 下限 ——
    // 真机（HONOR PGT-AN10）上报的状态栏 inset 偏小≈18dp，直接用会顶到状态栏/前置摄像头）；
    // 底部取 max(手势条, 键盘)，让输入条随键盘抬起。
    val topInsetDp = if (applyInsets) topSafeDp(density) else 0.dp
    val bottomInsetDp = if (applyInsets) with(density) {
        maxOf(
            androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(density),
            androidx.compose.foundation.layout.WindowInsets.ime.getBottom(density),
        ).toDp()
    } else 0.dp

    Box(Modifier.fillMaxSize()) {

        // ① 取样层：环境背景 + 可滚动消息（内容进层，玻璃才糊得到/推得开）
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            // [V3.18] 列表状态提升到本层：右侧滚动指示条要用它（原来在 Column 内，这里取不到）
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            AmbientBackground(Modifier.fillMaxSize(), rev = state.themeRev)
            // [V3.16] 再让开悬浮顶栏的高度（76dp）：否则目标卡/任务条会被顶栏压住（真机实测）
            // [V3.26 · 用户 #4]「对话胶囊上滑应在胶囊**顶端上方**消失，而不是下方」
            //   原实现把「让开悬浮顶栏 76dp」的内边距放在**列容器**上 ⇒ 列表视口从顶栏**下方**才开始，
            //   内容永远滑不到胶囊底下去，只能在顶栏下方被**硬切一刀**（用户看到的"在下方消失"）。
            //   代码注释里原本就写着期望效果：「对话先穿过玻璃的胶囊，然后逐渐消失被渐变盖住」——
            //   改法：让位内边距进**列表内容内边距**（视口到顶）⇒ 内容能穿到胶囊底下，
            //   由**顶部渐变罩**（罩子底边正好在胶囊顶端）完成"滑到胶囊顶端就淡掉"。
            val headInset = topInsetDp + 76.dp
            // [V3.45 · **真机实测修**] 「让位判断」必须与「头块实际会渲染什么」**逐条一致**。
            //   V3.30 给目标卡加了**自诊断文案**（`planGoal` 空但 `planTodos/planProgress` 非空时**也渲染**），
            //   却没同步这里的 `hasHead` ⇒ 那种情况下头块**没有让位 Spacer**，目标卡直接顶到屏幕最上沿、
            //   压在状态栏与顶栏胶囊下面（真机证据 `evidence/phone-20261010-night/501_chat.png`：
            //   「目标（目标文本未到达：objective 为空）」压在 23:30 那一行上）。`moreStatus` 同理。
            val hasHead = state.banner.isNotEmpty() ||
                state.planGoal.isNotBlank() || state.planTodos.isNotEmpty() ||
                state.moreStatus.isNotEmpty()
            // [V3.29 · 重做「带目标对话：内容穿过顶栏、不截断」]（上一版把**列表**也包进被测量的 Column
            //   ⇒ 量到整列高度 ⇒ 列表被整段推下去，已回退）。这次：
            //   ① 头块**单独包一层并只量它自己**（`onSizeChanged`），② 头块用 `zIndex(1f)` **画在列表之上**，
            //   ③ 列表 `offset(y = -headDp)` 上移到顶 + `contentPadding.top = headDp + 2dp` 把头高补回来
            //   ⇒ **视口到顶**（消息可从头块与顶栏底下**穿过去**），静止时首条消息正好落在头块下方（不重叠）。
            var headPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
            // [V3.72 · 用户「**点不开**」] 上一版把卡片塞进 `Box(height = 50.dp)` 里想让头块高度恒定，
            //   但那样会把卡片的**命中区域**限制在 50dp 内 ⇒ 点头部没反应（真机实测"点不开"）。
            //   改法：**卡片不限高**（保持可点 ✓），改为在**列表内边距**里"减掉卡片高度、再加固定值"
            //   ⇒ 展开/收起时列表内边距恒定 ⇒ **下方对话不动** ✓，而卡片本身照常可点 ✓。
            var planCardPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
            val headDp = with(density) { headPx.toDp() }
            // [V3.79] 删掉遗留诊断（原 V3.57 的 `LaunchedEffect(headDp) { state.debugHead = ... }`）：
            //   它在**每次头块测量变化**时都写一次状态 ⇒ 无谓重组，正是"抖动"的放大器之一。
            // [V3.32 · 修用户报"下方把对话截断"] ⚠️ 上一版用 `offset(-headDp)` 把列表整体上移，
            //   结果**列表底边也抬高了 headDp** ⇒ 底部内容被切。正确结构：父容器用 **Box**，
            //   列表**占满全屏**（底边不动），头块用 `zIndex(1f)` 浮在上面 ⇒ 既不截断、又能穿过去。
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().zIndex(1f).onSizeChanged { headPx = it.height }) {
                // [V3.62] 屏上诊断读数已撤（V3.57 起临时挂的 `DBG items/raw/vis/head/err`，
                //   本轮用它定位到"状态永远为 0"的真根因后即删；字段 debugXxx 一并无用，可随后清理）。
                if (hasHead) Spacer(Modifier.height(headInset))
                // 横幅
                if (state.banner.isNotEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(if (state.bannerError) Ui.BANNER_ERR_BG else Ui.BANNER_WARN_BG)),
                    ) {
                        Text(
                            state.banner,
                            color = Color(if (state.bannerError) Ui.BANNER_ERR_FG else Ui.BANNER_WARN_FG),
                            fontSize = Ui.S_FOOT.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
                // 目标 / 任务提要（点一下就地展开）
                // [V3.30 · 用户「目标还是没看到」的定位] 生产实测：卡片在，但渲染成「目标」+空+「0/0」
                //   ⇒ `planGoal` 是空/空白（`objective` 没到达）。这里：① 空白时也显示卡片（内容换成
                //   **自诊断文案**，一眼看出是"数据没到"还是"没渲染"）；② 有 todo/进度时同样显示。
                // [V3.48 · 用户「**目标没有的项目不需要显示，有的再显示**」] 卡片只在**确实有内容**时出现
                //   （有目标文本、或有任务条）；V3.30 那条"自诊断占位文案"（「目标文本未到达：objective 为空」）
                //   **去掉** —— 那是"数据没到"的诊断、不是内容，用户明确不要它。
                if (state.planGoal.isNotBlank() || state.planTodos.isNotEmpty()) {
                    // [V3.57 · 诊断] 目标卡**改回纯绘制（观感版）**：V3.50 我把它换成真玻璃 `GlassSurface`，
                    //   而 `RefractionWindow` 内部是 `BoxWithConstraints + fillMaxSize()` —— 一旦它把头块撑成
                    //   巨大高度，`contentPadding.top = 头块高` 就会把**整屏消息推到屏幕外**（真机表现：
                    //   "所有会话全空、且没有任何报错"）。这里先用纯绘制版验证这个假设。
                    // [V3.68 · 用户「**目标这个磨砂再加强点，现在看不太清楚内容**」+「**点击收不回去**」]
                    //   ⚠️ 收不回去的原因：上一版把 `GlassDebug.forcePlanExpanded` 写成 `planOpen = 本地 || force`
                    //   ⇒ 只要启动时带了取证钩子，就**永远是展开**、点也收不回（我自己启动时带了钩子，真机实测就是这现象）。
                    //   ⇒ 改成：**钩子只决定"初始值"**，之后点击自由切换。
                    //   磨砂：`blurRadius` 6dp → **14dp**、体色 55% → **72%**（两个质感数值）⇒ 文字压在正文上也看得清。
                    var planOpenLocal by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(false)
                    }
                    // [V3.68] 取证钩子只"开场展开一次"：写成初值会在组合时序上不稳（真机实测有时仍是收起），
                    //   写成 `本地 || force` 又会永远收不回。用一次性 effect 最稳，且之后点击可自由收/放。
                    androidx.compose.runtime.LaunchedEffect(GlassDebug.forcePlanExpanded) {
                        if (GlassDebug.forcePlanExpanded) planOpenLocal = true
                    }
                    val planOpen = planOpenLocal
                    // [V3.69 · 用户「**目标和任务这个胶囊，点击之后成两层了**」]
                    //   ⚠️ 这次的"两层"不是两张卡（那张已经修掉了），而是：**卡底下聊天正文透过来** ✗
                    //   —— 卡是真玻璃、取样的是**列表内容层**，而它正压在**密集正文**上，
                    //   玻璃再磨砂也会把字透出来 ⇒ 看起来就是上下叠了两层。
                    //   （设置页那几张卡干净，是因为它们背后是**干净的背景**，不是正文。）
                    //   ⇒ 体色回到与 chips 同档的 **白 55%**（层里已无正文 ⇒ 不需要靠不透明度硬压），
                    //     磨砂保留 14dp；背后是**干净渐变** ⇒ 既干净、又和 chips 同一套玻璃语言。
                    // [V3.73 · 用户「目标这个胶囊**效果太差**，能不能再提升下玻璃质感…**整体质感差点**」]
                    //   ⇒ 体色从"偏白 68%"改成**浅灰磨砂体**：浅 `#EFEFEF` 86% / 深 `#1C1B22` 86%
                    //   （压在正文上不再"洗白"，而像一块实心磨砂玻璃）。磨砂保留 18dp。
                    val planTint = if (Ui.isDark()) Color(0xFF1C1B22).copy(alpha = 0.86f)
                    else Color(0xFFEFEFEF).copy(alpha = 0.86f)
                    // [V3.70 · 用户「点击这个目录任务，**下方对话怎么跟着动**？应该不影响，是**单独的层**」
                    //   +「现在的**质感纯白色**了，完全不是磨砂质感的」]
                    //   ① "下方跟着动"的根因：卡片在头块里，而列表 `contentPadding.top = 头块实测高度`
                    //      ⇒ 卡片一长高，列表就被整体推下去。修：给它**固定占位 50dp**，超出部分**溢出绘制**
                    //      在上层（Compose 的 Box 默认不裁剪）⇒ 头块高度**恒定** ⇒ 展开时下方对话一动不动 ✓。
                    //   ② "纯白没磨砂"的根因：上一轮为防"透字"把取样层换成了**纯环境背景**（层里没有内容可糊）
                    //      ⇒ 玻璃没东西可模糊，自然是一块平白。修：**改回内容层** `contentBackdrop`（有正文可糊）
                    //      ⇒ 恢复真正的磨砂；磨砂 18dp + 体色 68% ⇒ 压在正文上也读得清。
                    Box(Modifier.fillMaxWidth()) {
                    GlassSurface(
                        backdrop = contentBackdrop,
                        radius = 28.dp,
                        tint = planTint,
                        blurRadius = 18.dp,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .onSizeChanged { planCardPx = it.height }
                            // [V3.73 · 用户「点击之后**还是两圈，一圈小的灰色，一圈没动的**」]
                            //   真凶：点击手势原来只挂在**头部那一行**上 ⇒ 它的按压变暗层比卡片**小一圈**，
                            //   于是屏幕上出现"卡片一圈 + 小灰圈一圈"两圈。修：把点击**移到卡片本体**上
                            //   （与玻璃同形同尺寸）⇒ 只剩一圈。
                            .glassTap(corner = 28.dp) { planOpenLocal = !planOpenLocal }
                            .glassButtonShadow(RoundedCornerShape(28.dp)),
                    ) {
                        // [V3.74 · 用户「**玻璃质感还是有点差**…如果不能做真玻璃，做个**假玻璃稍微仿一仿**也行，
                        //   但是现在**纯这种半透明完全不行**」]
                        //   ⇒ 真玻璃打底之上，再叠**工程里已验证的"观感玻璃"三件套**：
                        //     ① 斜向受光渐变（左上更亮 → 右下更透）② **白色发丝边**（= 白棱，玻璃最可读的线索）
                        //     ⇒ 一眼就"像玻璃"，不再是"纯半透明"。
                        Box(
                            Modifier.matchParentSize()
                                .clip(RoundedCornerShape(28.dp))
                                .background(Brush.linearGradient(listOf(
                                    Color.White.copy(alpha = if (Ui.isDark()) 0.16f else 0.42f),
                                    Color.White.copy(alpha = if (Ui.isDark()) 0.02f else 0.06f),
                                )))
                                .border(0.5.dp,
                                    Color.White.copy(alpha = if (Ui.isDark()) 0.22f else 0.55f),
                                    RoundedCornerShape(28.dp)),
                        )
                        Column(Modifier.padding(
                            horizontal = 16.dp,
                            // [V3.73 · 用户「这个目标任务**有点大，上下再小一点点**」] 上下内边距收一档
                            vertical = if (planOpen) 10.dp else 7.dp,
                        )) {
                            // [V3.73] 点击已移到**卡片本体**（见上面 modifier）⇒ 这里不再挂 glassTap，
                            //   否则"头部一行"的按压层会再画一圈小灰圈（用户报的"两圈"）。
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (state.planGoal.isNotBlank()) {
                                    Text("目标", color = Color(Ui.INK_SUB), fontSize = Ui.S_CAP1.sp,
                                        fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(8.dp))
                                    Text(state.planGoal, color = ink, fontSize = Ui.S_FOOT.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f))
                                    if (state.planProgress.isNotEmpty()) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(state.planProgress, color = Color(Ui.BRAND_FILL),
                                            fontSize = Ui.S_CAP1.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text("目标 · 任务", color = ink, fontSize = Ui.S_FOOT.sp,
                                        modifier = Modifier.weight(1f))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(if (planOpen) "▾" else "▸", color = Color(Ui.INK_SUB),
                                    fontSize = Ui.S_CAP1.sp)
                            }
                            if (planOpen && state.planTodos.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                state.planTodos.forEachIndexed { i, pair ->
                                    val (text, status) = pair
                                    Box(
                                        Modifier.fillMaxWidth()
                                            .padding(top = if (i == 0) 0.dp else 5.dp)
                                            .glassButtonShadow(RoundedCornerShape(999.dp))
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Brush.linearGradient(listOf(
                                                Color.White.copy(alpha = 0.34f),
                                                Color.White.copy(alpha = 0.12f))))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.35f),
                                                RoundedCornerShape(999.dp)),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                Modifier.size(7.dp).clip(RoundedCornerShape(999.dp))
                                                    .background(
                                                        if (status.contains("完成")) Color(Ui.BADGE_OK_FG)
                                                        else Color(Ui.BRAND_FILL)),
                                            )
                                            Spacer(Modifier.width(9.dp))
                                            Text(text, color = ink, fontSize = Ui.S_FOOT.sp,
                                                modifier = Modifier.weight(1f))
                                            if (status.isNotEmpty()) {
                                                Spacer(Modifier.width(8.dp))
                                                Text(status, color = inkFaint, fontSize = Ui.S_CAP1.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }   // [V3.70] 固定占位 Box 收尾：卡片超出部分**溢出绘制**、不参与头块测量
                }
                if (state.moreStatus.isNotEmpty()) {
                    Text(
                        state.moreStatus,
                        color = Color(if (state.moreStatusError) Ui.WARN else Ui.INK_SUB),
                        fontSize = Ui.S_FOOT.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    )
                }

                }   // [V3.29] 头块测量层收尾（zIndex 1 ⇒ 画在列表之上）
                // 消息列表：LazyColumn 省内存（长会话不再一次性建满）

                // [V3.48 · 真机会话报「**历史信息刷不出来**」—— 这是个真 bug，不是观感问题]
                //   宿主接口里**有 `loadMore()`，但整屏从来没调用过它**（`grep loadMore` 在 GlassChatScreen 里
                //   只有声明、没有调用点）⇒ 滚到顶上永远不会去取更早的消息。这里补上：
                //   **滚到列表最顶端**就请求一次更早的；`askedMore` 防重复请求，条目数变化或滑离顶部时复位。
                var askedMore by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                androidx.compose.runtime.LaunchedEffect(
                    listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, state.items.size,
                ) {
                    if (state.items.isNotEmpty() && listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset == 0
                    ) {
                        if (!askedMore) {
                            askedMore = true
                            runCatching { host.loadMore() }
                        }
                    } else if (listState.firstVisibleItemIndex > 0) {
                        askedMore = false
                    }
                }

                // 新消息到达：**只在已经贴底时**跟随（否则会把正在翻历史的用户拽下去，与正式版 computeAtBottom 同口径）
                // [V3.79] 用**瞬时** `scrollToItem`，不用 `animateScrollToItem`：输出时条目数频繁变化，
                //   动画会互相叠加/回弹 ⇒ 观感"来回跳"。瞬时定位 + 只在贴底时执行 ⇒ 稳定。
                androidx.compose.runtime.LaunchedEffect(state.items.size) {
                    if (state.items.isNotEmpty() &&
                        runCatching { !listState.canScrollForward }.getOrDefault(true)
                    ) {
                        runCatching { listState.scrollToItem(state.items.lastIndex) }
                    }
                }
                // 宿主显式要求滚到底（refreshNow/scrollToBottom）
                androidx.compose.runtime.LaunchedEffect(state.scrollToBottomTick) {
                    if (state.scrollToBottomTick > 0 && state.items.isNotEmpty()) {
                        runCatching { listState.animateScrollToItem(state.items.lastIndex) }
                    }
                }
                // [V3.49 · 用户「检测滑动速度，**高速滑动时临时降低**液态玻璃的模糊和折射强度，
                //   滑动结束立刻恢复；慢速滑动保持完整效果」]
                //   做法：拿"首可见项的序号变化 / 时间"当速度（够粗但够用），只改 `RefractionWindowTokens.quality`
                //   这一档 —— 玻璃件本体不动。停下**立刻**回满档。
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    var lastIdx = listState.firstVisibleItemIndex
                    var lastMs = 0L
                    androidx.compose.runtime.snapshotFlow {
                        listState.firstVisibleItemIndex to listState.isScrollInProgress
                    }.collect { (idx, scrolling) ->
                        val now = System.currentTimeMillis()
                        if (!scrolling) {
                            RefractionWindowTokens.quality = 1f          // 停下：立刻恢复完整效果
                        } else if (false) {                              // [测量开关] true=启用降档驱动
                            val dt = (now - lastMs).coerceAtLeast(1L)
                            val speed = kotlin.math.abs(idx - lastIdx) * 16f / dt   // 每 16ms 翻过几项
                            RefractionWindowTokens.quality = when {
                                speed >= 1.5f -> 0.35f    // 高速：模糊/折射降到 ~1/3，色散关掉
                                speed >= 0.6f -> 0.65f
                                else -> 1f                // 慢速：完整效果
                            }
                        }
                        lastIdx = idx; lastMs = now
                    }
                }

                androidx.compose.runtime.CompositionLocalProvider(
                    // [V3.44 · V3.32 §2.4②] 滚动中标记：让列表行（`MsgCard`）跳过自绘阴影。
                    //   变值只会让**读它的** `MsgCard` 重组（读不到的地方不变），不会把整个列表重组一遍。
                    LocalGlassScrolling provides (listState.isScrollInProgress || GlassDebug.forceScrolling),
                ) {
                    // [V3.50] 目标卡又回到真玻璃 ⇒ **恢复这一层"内容取样"**（层里只有观感版卡，不会自取样）
                    Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
                    // [V3.57 · 诊断] **暂时撤掉 `SelectionContainer`**：V3.48 真机有内容，V3.49 我加了
                    //   `layerBackdrop{ SelectionContainer{ LazyColumn } }` 之后全空 ⇒ 先验证是不是这一层。
                    //   （长按选择这个功能稍后用"只包每条文字"的方式补回来，见 V3.49 报告。）
                    LazyColumn(
                        // [V3.29] 上移一个"头高" + 内容内边距补回来 ⇒ 视口到顶、消息能从头块底下穿过
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp,
                            // [V3.26 #4] 无横幅/目标卡时，让位高度落在**列表内容内边距**上（视口到顶）
                            // [V3.52 · 用户报「对话内容是通上去的，但**没有顶边定位**，第一条消息直接发到
                            //   状态栏那个渐变的位置遮住」] 真凶：`headDp` 是**头块实测高度** —— 没有横幅/目标卡时
                            //   它是 0 ⇒ 内边距只剩 2dp ⇒ 第一条消息顶在屏幕最上沿。这里取
                            //   `max(头高, 头块本该让开的量)`，两种情况下都保证首条消息落在**可视窗口内**。
                            // [V3.79 · 用户「聊天对话界面为啥**输出过程中一直来回跳**」]
                            //   ⚠️ 根因：原来让位高度 = `headDp − planCardPx + 50dp`，而 `headDp` 是**头块实测高度**
                            //   —— 输出时「N 子智能体」这条会反复出现/消失、「正在加载更早…」也会闪进闪出
                            //   ⇒ 让位高度跟着变 ⇒ **整列消息上下平移**，观感就是"一直来回跳"。
                            //   ⇒ 改成**常量**：顶栏高度 + 目标卡收起态(50dp) +（有横幅时才加横幅高度）。
                            //     目标卡展开时按设计浮在消息之上（V3.70 起就是这个行为），不再推动列表。
                            top = headInset + 50.dp + (if (state.banner.isNotEmpty()) 44.dp else 0.dp) + 2.dp,
                            bottom = bottomInsetDp + 132.dp),
                    ) {
                        // [V3.63 · 用户「每次打开对话、然后加载历史记录为啥画面在闪烁，能不能不影响拖动？默默在上面加载？」]
                        //   根因：这里原来**没给 key** ⇒ Compose 只能按"下标"锚定滚动位置
                        //   ⇒ 往上**预付**（prepend）老消息时，整个可视区内容**整体位移** ⇒ 用户看到"闪一下"。
                        //   ⇒ 用 `ChatItem.key`（宿主侧本就按 key 唯一管理）做 LazyColumn 的 key，
                        //     预付后 Compose 会把**同一个 item** 锚住 ⇒ 画面不动、静默把历史挂到上面。
                        itemsIndexed(
                            state.items,
                            key = { index, item -> item.key.ifEmpty { "idx" + index } },
                        ) { index, item ->
                            if (index > 0) {
                                val prev = state.items[index - 1]
                                if (item.time > 0L && prev.time > 0L &&
                                    item.time - prev.time >= 5 * 60 * 1000L
                                ) {
                                    TimeDivider(timeLabel(item.time), inkFaint)
                                }
                            }
                            // [V3.49] 工具行**回来了**（可折叠，见 ToolCard），"N 个工具调用"占位行撤掉
                            // [V3.81] `verbose`：完整模式 ⇒ 思考/工具行**默认展开**；精简 ⇒ 默认收起
                            ChatRow(item, ink, inkSub, inkFaint, verbose = !state.compact)
                        }
                    }
                    }   // [V3.50] 内容取样层收尾（目标卡的真玻璃取它当底）
                }
            }
            // 顶部渐变罩（**同背景材质**：取 Ui.ambientBarColor()）：顶端实心 → 往下透明。
            // 用户要求：「对话先穿过玻璃的胶囊，然后逐渐消失被渐变盖住」。
            // [V3.18] 右侧滚动指示条 → [V3.28] 滑轮 → [V3.29 · 用户指正] **改用桌面 DSH 的回合导轨口径**
            //   （`dsh-chat-rail`/`turn-rail`：一列短横条，每个回合一枚、当前回合加长变主色、可点跳转）
            // [V3.34] 空会话（一条消息都没有）**不显示**这条刻度尺：没有任何可滚动内容时它只是凭空
            //   浮着一条没用的刻度条（空页面实测 `evidence/v3330/001_chat_nocall.png`），看着就像"没做好"。
            if (state.items.isNotEmpty())
            TurnRail(
                state = listState,
                // 回合 = 每条 USER 消息的第一条；没有 USER 时给一枚（整段算一个回合）
                turns = androidx.compose.runtime.remember(state.items.size) {
                    val t = ArrayList<Int>()
                    state.items.forEachIndexed { i, it -> if (it.kind == ChatItem.USER) t.add(i) }
                    if (t.isEmpty()) t.add(0)
                    t
                },
                // [V3.34 · 对齐参照] 预览气泡要显示**问题原文** ⇒ 必须把每个回合的文本喂进来
                //   （与上面的 turns **同一套过滤 + 同一个 remember key** ⇒ 下标一一对应）。
                //   原来调用处**没传 `labels`** ⇒ 恒为空 ⇒ 气泡永远只显示"第 N/M 个问题"这句占位，
                //   看不到正文 —— 参照实现是"标题行 + 正文最多 8 行"（`MessageScrubber:146-188`）。
                labels = androidx.compose.runtime.remember(state.items.size) {
                    val l = ArrayList<String>()
                    state.items.forEach { if (it.kind == ChatItem.USER) l.add(it.text) }
                    if (l.isEmpty()) l.add(state.items.firstOrNull()?.text ?: "")
                    l
                },
                running = state.running,
                activeColor = Color(Ui.BRAND_FILL),
                // [V3.34 · 对齐参照 `MessageScrubber:133`] 未选中刻度：深色档白 70% / 浅色档黑 60%
                idleColor = if (Ui.isDark()) Color(0xB3FFFFFF) else Color(0x99000000),
                modifier = Modifier.align(Alignment.CenterEnd)
                    .fillMaxHeight(0.62f)
                    // [V3.39 · 用户「右侧这个刻度条不能用」] 真凶：刻度条的 `Canvas` 用了 `fillMaxSize()`，
                    //   而这里**没限宽** ⇒ 触摸层铺满整屏、把滚动/点击手势全吃掉了（所以"不能用"）。
                    //   限宽成 48dp，触摸只落在右缘那一条带里（与朋友 `MessageScrubber` 的 40dp 触摸带同口径）。
                    .width(48.dp)
                    .padding(top = topInsetDp + 84.dp, bottom = bottomInsetDp + 140.dp, end = 6.dp),
            )
            // [V3.46 · 用户「↓按钮真玻璃」] 改回**工程规定的液态玻璃件** `GlassRoundButton`
            //   （= 顶栏 ‹/⋯ 同款 `RefractionWindow`，体色走 `RefractionWindowTokens.Tint`）。
            //   ⚠️ 这条路径 V3.40 崩过一次（新增第 10 个 `RefractionWindow` → HWUI `computeTransformImpl`
            //   递归爆栈）⇒ **必须真机冷启动验证**；一旦复现就回退（已知可用包
            //   `dist/archive/dsh-mobile-good-0d523e88.apk`）。
            //   另：真机**不接受注入手势**、进会话又默认落在底部 ⇒ 这个按钮平时根本不渲染、没法测，
            //   所以加了 `--ez glass_force_jump true` 强制渲染钩子（只加一个 if 条件，不改变出货行为）。
            if ((listState.canScrollForward || GlassDebug.forceJumpButton) && state.items.isNotEmpty()) {
                val jumpScope = androidx.compose.runtime.rememberCoroutineScope()
                // [V3.46 · **真机实测结论：这条路仍然崩，已回退**] 换成真玻璃 `GlassRoundButton`
                //   （= 第 11 个 `RefractionWindow`）后，真机 dropbox 两次出现同源
                //   `data_app_native_crash`：`SIGSEGV / stack pointer is not in a rw map` + `computeTransformImpl`
                //   递归爆栈（00:12:40 / 23:59:46）。连"去掉多余定位层"也没救 ⇒ **上限是"同屏玻璃件个数"**。
                //   ⇒ 按 V3.32 §0 铁律回退成"非玻璃观感版"：体色仍取自 `RefractionWindowTokens.Tint`
                //   （与 ‹/⋯/＋/↑/输入框**同一个值**），但**不新增玻璃节点**。
                //   要真玻璃只有一条路：**先把同屏玻璃件数减下来**（这屏的 chip 行是 4 个独立胶囊，
                //   合并成 1 个就能腾出 3 个名额）再加它 —— 见报告 §3。
                Box(
                    Modifier.align(Alignment.BottomEnd)
                        // [V3.42 · 用户「位置应该再往下点」] 有「N 子智能体」条 204→**168dp**；没有 146→**116dp**
                        .padding(end = 14.dp,
                            bottom = bottomInsetDp + (if (state.subagentCount > 0) 168.dp else 116.dp)),
                ) {
                    Box(
                        Modifier.size(46.dp)
                            .glassTap(corner = 23.dp) {
                                jumpScope.launch {
                                    runCatching { listState.animateScrollToItem(state.items.lastIndex) }
                                }
                            }
                            .glassButtonShadow(androidx.compose.foundation.shape.CircleShape)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            // [V3.47 · 用户「↓ 不能用玻璃件的话，**用下面项目这些的同配置**可以吗」]
                            //   ⇒ **可以**：直接抄 chip 行那档体色 `Color.White.copy(alpha = 0.55f)`
                            //   （与 chip **完全同值**、浅/深同值），代价只是**没有真模糊** ——
                            //   那一档的真玻璃本身就是新增 `RefractionWindow` ⇒ 真机必崩（见本文件 V3.46 记录）。
                            //   原来的工程默认 token 是白 20%（显得"虚"），换成 55% 后与下面那排 chip 同材质。
                            //   同时去掉自绘受光弧，向 chip 的"平面半透明白"观感对齐。
                            // [V3.48 · 用户标记点③「看起来比下方项目、模型的小按钮还是透，要统一」]
                            //   再把体色提一档：浅/深都用 **白 70%**（比 chip 的 55% 更实）——
                            //   因为没有真模糊，只有靠体色把"背景色透上来"这件事压住，才和 chip 那种
                            //   "糊过的白"看起来一致。若还偏透，下一次直接抬到 0.8。
                            .background(Color.White.copy(alpha = 0.70f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.35f),
                                androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_down), null,
                            tint = Color(Ui.BRAND_FILL), modifier = Modifier.size(22.dp))
                    }
                }
            }
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(topInsetDp + 18.dp)
                    .background(Brush.verticalGradient(
                        // [V3.29 · 交接 §6 #4「顶部与内容分割过于明显」+ 用户 #4「在胶囊顶端上方消失」]
                        //   原来 3 段 stop（1.0 → .86 → 0）在 .86 处有明显折点 ⇒ 看起来"切得硬"。
                        //   改为 **5 段平滑衰减**（1.0 / .96 / .72 / .34 / 0）：在胶囊顶端那条线上自然归零。
                        listOf(
                            // [V3.91 · 用户「切换成上传的图片之后，上面显示的**别的颜色**的遮罩，
                            //   能不能**从上传图片提取**？」]
                            //   ⇒ 改用 `ambientTopColor()`：有自定义底图时取**图片顶部代表色**，
                            //     没有才回落到内置配色的顶色（原来一律用配色 ⇒ 换图后颜色对不上）。
                            ambientTopColor(),
                            ambientTopColor().copy(alpha = 0.96f),
                            ambientTopColor().copy(alpha = 0.72f),
                            ambientTopColor().copy(alpha = 0.34f),
                            ambientTopColor().copy(alpha = 0f),
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    )),
            )
        }

        // ② 悬浮玻璃层（层外）：输入三件 + 入口胶囊 + 顶部三件
        Box(Modifier.fillMaxSize()) {
            if (state.readOnly) {
                Text(
                    state.readOnlyReason.ifBlank { "只读会话" },
                    color = Color.White, fontSize = Ui.S_CAP1.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
                )
            } else {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = bottomInsetDp + 10.dp),
                ) {
                    // [V3.75 · 用户「选文件之后**没出现**，也没有**放到输入等待、和文字一起发送**」]
                    //   ⇒ 在输入区上方渲染**待发附件条**（图片显缩略图 / 文件显名字）+ 一句"将与文字一起发送"。
                    if (state.pendingAttachments.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            state.pendingAttachments.take(6).forEachIndexed { i, enc ->
                                // [V3.78 · 用户「选好的图片，**待发送之后没法删除了**」]
                                //   每张右上角一个 ×：调宿主既有的 `onRemoveAttachment`（旧视图的 × 就一直走它）。
                                Box {
                                    AttachmentThumb(enc, inkSub)
                                    Box(
                                        Modifier.align(Alignment.TopEnd).size(18.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(Color.White.copy(alpha = 0.92f))
                                            .glassTap(corner = 9.dp) { host.removeAttachment(i) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("×", color = Color(Ui.INK_SUB), fontSize = 12.sp)
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                "将与文字一起发送",
                                color = inkSub, fontSize = Ui.S_CAP1.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // 待发送队列（正式版 pendingBar）
                    // [V3.85 · 用户「待发送的内容手机端现在显示的是一串**字符**，不是输入的内容」
                    //   +「待发送这个栏不能**选修改、立即发送和删除**了」]
                    //   ① 编码是 `itemId\u0001text`（`GlassChatView` 写入）⇒ 这里取 text 显示；
                    //   ② 每条**可点** ⇒ 交给宿主弹「立即插入 / 修改 / 删除」（`onQueueItemAction`）。
                    state.pendingSends.forEach { raw ->
                        val qid = raw.substringBefore('\u0001')
                        val qtext = raw.substringAfter('\u0001').ifBlank { raw }
                        RefractionCapsule(
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).height(34.dp)
                                .glassTap(corner = 999.dp) { host.queueItemAction(qid, qtext) },
                        ) {
                            Text("待发送 · $qtext", color = inkSub, fontSize = Ui.S_CAP1.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (state.subagentCount > 0) {
                        SubagentChip(backdrop, Color(Ui.BRAND_FILL), state.subagentCount) { host.openSubagents() }
                        Spacer(Modifier.height(8.dp))
                    }
                    // 能力 chip（权限/模型/思考/用量/项目）—— 有才显示
                    if (state.chips.isNotEmpty()) {
                        // [V3.24 修] 与旧视图对齐：chip 行是**可横向滚动**的（旧实现是 HorizontalScrollView）。
                        //   原来是普通 Row + 居中，chip 一多（加上「权限」后常态 4 枚）最后一枚会被**裁掉**
                        //   （实测 `思考 · low` 只剩 10px）。
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            state.chips.forEach { c ->
                                // [V3.25 · 用户「这一排按钮看不太清楚，**加磨砂、白一些**」]
                                //   从"平色渐变凑观感"换成**真玻璃胶囊**（真磨砂 + 更白）。
                                //   V3.17 当年套真玻璃会变成"三条通栏互相叠住"，根因与小元件同款 ——
                                //   `RefractionWindow` 内层 `fillMaxSize()` 在**宽度有界**时会撑满父宽；
                                //   现在用 `wrapContentWidth(unbounded = true)`（V3.23 学到的）即可按内容自适应。
                                //   小元件把折射压到 6/8dp（V3.17 结论：默认 14/28 会把标签推挤变形），
                                //   磨砂 8dp + 白度 55%（用户要的"更白"），文字从 inkSub 提到 INK（"看不太清楚"）。
                                RefractionCapsule(
                                    backdrop = backdrop,
                                    modifier = Modifier.padding(horizontal = 3.dp)
                                        .wrapContentWidth(unbounded = true)
                                        .height(30.dp)
                                        .glassTap { host.chipTap(c) }                     // [V3.26] 排到阴影之前
                                        .glassButtonShadow(RoundedCornerShape(999.dp)),   // [V3.26] 统一阴影模板
                                    blurRadius = 8.dp,
                                    refractionHeight = 6.dp,
                                    refractionAmount = 8.dp,
                                    tint = Color.White.copy(alpha = 0.55f),
                                ) {
                                    Text(c, color = Color(Ui.INK), fontSize = Ui.S_CAP1.sp,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 12.dp))
                                }
                            }
                        }
                    }
                    ChatInputRow(
                        backdrop = backdrop,
                        state = state,
                        host = host,
                        ink = ink,
                        inkFaint = inkFaint,
                    )
                }
            }

            // 顶栏：与底部对称的**三件独立玻璃**（用户要求「按钮和下方对应，也是三个独立的，
            // 左右是圆的中间一个独立的」）：左圆玻璃「‹」＋ 中间独立玻璃条（标题/副标题）＋ 右圆玻璃「⋯」。
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = topInsetDp + 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassRoundButton(backdrop, 46.dp, R.drawable.ic_chevron_left,
                    Color(Ui.BRAND_FILL), Color.Transparent) { host.openTasks() }
                Spacer(Modifier.width(8.dp))
                RefractionWindow(
                    backdrop = backdrop,
                    radius = 22.dp,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(state.title, color = ink, fontSize = Ui.S_HEAD.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(state.subtitle, color = inkFaint, fontSize = Ui.S_CAP1.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                GlassRoundButton(backdrop, 46.dp, R.drawable.ic_more, inkSub, Color.Transparent) { host.menu() }
            }

            // [V3.88 · 用户「生成的图片还有上传的图片，**点不开**，最好能点开放大看，看这清楚点」]
            //   全屏看图：覆盖在最上层（点一下关闭、双指放大）。**不用 Compose Dialog** ——
            //   本工程在纯 Activity 骨架里用 Dialog 出过 `ViewTreeLifecycleOwner` 崩溃。
            GlassImageViewer.zoom?.let { bmp ->
                ZoomOverlay(bmp) { GlassImageViewer.zoom = null }
            }

            // ---- [V3.24] 统一弹窗骨架：渲染在**取样层之外**（与页面其它玻璃件一致），才能糊到背后的页面
            // [V3.25] **菜单形态**：点一下要**先收起**再回调 —— 旧 `AttachSheet` 就是这么做的，
            //   否则面板会盖住刚打开的相机/选择器，用户以为"点了没反应"。
            // ⚠️ [V3.129 · 教训] 这里曾放过一行 `sheetDiag(...)` 做诊断 —— **必须撤掉** ✗：
            //   这是 Composable 函数体，**每次重组都会执行** ⇒ 流式输出时每秒几十次主线程写盘 ⇒
            //   真机表现"对话界面又开始跳动（下面输出、上面也抖）" ✓。诊断只允许放在**低频**路径
            //   （如 `GlassSheetOverlay` 内部，仅在弹窗可见时）⇒ 高频 Composable 里禁止任何 IO。
            GlassSheetOverlay(
                backdrop = backdrop,
                state = state.sheet,
                // [V3.126 · 真机诊断定案：用户「点修改和重命名没有任何变化」]
                //   ⚠️ 原来顺序是"先回调、后 hide()" ✗：回调里若**又打开一个新面板**
                //   （待发送→修改 ⇒ `queue-edit`；更多→重命名 ⇒ `renamesess`），紧接着的
                //   `hide()` 会把**刚打开那个**面板一并关掉 ⇒ 真机表现"点了没反应"
                //   （诊断铁证：tag 已变成 queue-edit，但 visible 仍是 false）。
                //   旧路径弹的是**独立系统 AlertDialog**（另一个窗口）⇒ hide() 关不到它，所以以前没事。
                //   ⇒ 换成"**先收起、再回调**"：既保留"面板不挡相机/选择器"，又不会误关新面板 ✓
                onPick = { index ->
                    state.sheet.hide()
                    state.sheet.onListPick?.invoke(index)
                },
                onDismiss = { state.sheet.hide() },
                settleFrames = 2,   // [V3.118] 等 2 帧：先让取样层记录完再画面板，消除"首次打开材质不对、关掉再开才对"
            )
        }
    }
}

// ------------------------------------------------------------------ 9 类消息行（照 ChatAdapter.buildRow）

/**
 * [V3.75] 待发附件缩略图（编码 `kind\u0001uri\u0001name`，由 `GlassChatView.setPendingAttachments` 写入）。
 * 图片：解码成缩略图（失败/文件则退回文字块）；文件：显示名字前几个字。
 */
@Composable
private fun AttachmentThumb(enc: String, inkSub: Color) {
    val kind = enc.substringBefore('\u0001')
    val rest = enc.substringAfter('\u0001')
    val uri = rest.substringBefore('\u0001')
    val name = rest.substringAfter('\u0001')
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, uri) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                if (uri.startsWith("file://")) {
                    android.graphics.BitmapFactory.decodeFile(uri.removePrefix("file://"))
                } else if (uri.isNotEmpty()) {
                    ctx.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    }
                } else null
            }.getOrNull()
        }
    }
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
            .background(Color(Ui.SELECT_BG))
            .border(0.5.dp, Color(Ui.HAIRLINE), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            androidx.compose.foundation.Image(
                bitmap = b.asImageBitmap(), contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
            )
        } else {
            Text(
                if (kind == "image") "图片" else name.take(4).ifBlank { "文件" },
                color = inkSub, fontSize = Ui.S_CAP1.sp, maxLines = 1,
            )
        }
    }
}

/**
 * [V3.77 · 用户「**我发送的图片在我发送的聊天栏没显示**」] 消息里的图片。
 *
 * 数据来自宿主已经解码好的 `ChatItem.images`（`MainActivity.onAttachment` 里 `scaleDown(bmp, 1080)`
 * 之后塞进去、并 `convo.setItems()` 推过状态）；**Compose 侧原来完全没渲染** ⇒ 只看到文字。
 * 这里把它画出来：最多 4 张、靠右铺、圆角裁切、限高。
 */
@Composable
private fun ItemImages(list: List<android.graphics.Bitmap>) {
    if (list.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        list.take(4).forEach { b ->
            androidx.compose.foundation.Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .fillMaxWidth(0.66f)
                    .heightIn(max = 320.dp)
                    // [V3.88] 点一下 = 全屏放大看（用户「点不开，最好能点开放大看」）
                    .glassTap(corner = 14.dp) { GlassImageViewer.zoom = b }
                    .clip(RoundedCornerShape(14.dp)),
            )
        }
    }
}

/**
 * [V3.88 · 用户「生成的图片还有上传的图片，**点不开**，最好能点开放大看，看这清楚点」]
 *
 * 全屏看图的**模块级状态**：任何一处缩略图点一下就把图塞进来，页面最上层渲染覆盖层。
 * 为什么不用 Compose `Dialog`：本工程在纯 Activity 骨架里用 Dialog 出过
 * `ViewTreeLifecycleOwner` 崩溃（历史事故），覆盖层最稳。
 */
private object GlassImageViewer {
    var zoom: android.graphics.Bitmap? by androidx.compose.runtime.mutableStateOf(null)
}

/**
 * [V3.104 · 用户「弹窗没问题，但是**质感和模型、生成物那些的不一样了**，把质感调过去」]
 *
 * 把聊天页那个**主取样层**暴露给抽屉里的独立弹窗宿主 ⇒ 抽屉里点出来的弹窗与
 * 「模型 / 权限 / 生成物 / 任务」**采同一层、材质完全一致** ✓
 * （那些面板都渲染在聊天页这棵树里、用的是同一个 `backdrop`）。
 */
object GlassChatBackdrop {
    /**
     * [V3.117 · 用户「**刚才好了，但是我点其他地方，再回来又变回来了**」]
     *
     * ⚠️ 必须用 **Compose 可观察状态**（`mutableStateOf`），不能用普通 `var` ✗：
     * 普通变量只在**宿主组合那一刻**被读一次 —— 若那时它还是空的（刚导航回来、聊天页刚重组还没写回），
     * 抽屉宿主就会**一直**退用兜底那层 ⇒ 材质时好时坏（正是用户看到的"再回来又变回来了"）。
     * 做成可观察后：层出现/被清空都会让宿主**自动重组合** ⇒ 材质稳定 ✓
     */
    var layer: com.kyant.backdrop.Backdrop? by androidx.compose.runtime.mutableStateOf(null)
}

/** 覆盖层：黑底 + 双指放大/拖动 + 点一下关闭。 */
@Composable
private fun ZoomOverlay(bmp: android.graphics.Bitmap, onClose: () -> Unit) {
    var scale by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(1f)
    }
    var offX by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(0f)
    }
    var offY by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(0f)
    }
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 8f)
                    if (scale <= 1.02f) {
                        offX = 0f; offY = 0f
                    } else {
                        offX += pan.x; offY += pan.y
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { onClose() }
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(
                scaleX = scale, scaleY = scale, translationX = offX, translationY = offY,
            ),
        )
        Text(
            "点一下关闭 · 双指可放大",
            color = Color.White.copy(alpha = 0.7f), fontSize = Ui.S_CAP1.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
        )
    }
}

@Composable
private fun ChatRow(item: ChatItem, ink: Color, inkSub: Color, inkFaint: Color, verbose: Boolean = false) {
    when (item.kind) {
        // [V3.77 · 用户「**我发送的图片在我发送的聊天栏没显示**」]
        //   根因：宿主（`MainActivity.onAttachment`）其实已经把图片解码进 `ChatItem.images`
        //   （`List<Bitmap>`，并且 `convo.setItems()` 推过状态），但**Compose 这一侧从来没渲染它** ✗
        //   ⇒ 用户看到的只有文字。这里补上：用户消息上方先把图片按宽度铺出来（最多 4 张）。
        ChatItem.USER -> Column(Modifier.fillMaxWidth()) {
            ItemImages(item.images)
            UserBubble(item.text)
        }
        // [V3.30 · 用户「对话界面思考过程卡片可以点击缩小」] 传上 reasoning（旧视图 ChatAdapter:307 早就在用同一个字段）
        ChatItem.ASSISTANT -> Column(Modifier.fillMaxWidth()) {
            ItemImages(item.images)
            AssistantCard(item.text, item.reasoning, ink, verbose)
        }
        // [V3.49 · 用户「**我让你折叠，没让你直接消失掉**…我需要你找回来」]
        //   工具调用行恢复渲染；**折叠交给 `ToolCard` 自己**（默认收起成一行、点一下展开正文）。
        ChatItem.TOOL -> ToolCard(
            // [V3.78 · 用户「这些…**一会英文一会中文**」] 工具标题也走中文映射（原来直接把原始英文名摊出来）
            title = com.dsh.mobile.model.StepProcess.toolNameCn(item.toolName)
                    + if (item.toolRunning) " · 运行中…" else "",
            body = item.toolPreview,
            error = item.toolError, ink = ink, sub = inkSub,
            startOpen = verbose,          // [V3.81] 完整=展开 / 精简=收起
        )
        ChatItem.APPROVAL -> ApprovalCard(item, ink, inkSub)
        ChatItem.QUESTION -> QuestionCard(item, ink, inkSub)
        ChatItem.FILES -> FilesCard(item, ink, inkSub)
        ChatItem.AGENT -> AgentCard(
            name = item.agentName.ifBlank { "子智能体" }, text = item.text, ink = ink, sub = inkSub,
        )
        ChatItem.STEP -> StepCard(
            text = item.text,
            running = item.stepRunning, error = item.stepError, ink = ink, sub = inkSub,
        )
        else -> SystemRow(item.text, inkFaint)
    }
}

@Composable
private fun TimeDivider(label: String, faint: Color) {
    Text(label, color = faint, fontSize = Ui.S_CAP1.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
}

/** [V3.48 曾用]「N 个工具调用」占位行 —— [V3.49] 工具行已改为**可折叠**（`ToolCard`），本行撤掉。 */

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier.fillMaxWidth(0.82f).padding(vertical = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(
                    Color(0xFF9A6BFF).copy(alpha = 224f / 255f),
                    Color(0xFF3A8CFF).copy(alpha = 224f / 255f)))),
        ) {
            // [V3.83 · 用户「长按然后直接全复制？**没法调出手机自带的可以全选、可以选取的那种吗**」]
            //   ⇒ 用 `SelectionContainer` 恢复**原生文本选择**（长按出选择手柄 + 系统「复制/全选」菜单）。
            //   ⚠️ 只包**这一条消息的文字**，不包列表 —— V3.57 就是把它包在 LazyColumn 上，
            //      结果杀掉头块与消息（历史事故），这次只在 item 内部用。
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(text, color = Color.White, fontSize = Ui.S_BODY.sp,
                    modifier = Modifier.padding(horizontal = fsDp(14f), vertical = fsDp(10f)))
            }
        }
    }
}

/**
 * [V3.30 · 用户「对话界面思考过程卡片可以点击缩小」] **思考过程卡**（可点击收起/展开）。
 *
 * 口径与旧视图一致（`ui/ChatAdapter.java:305-318`：`▸ 思考过程` 一行，点开显示全文；
 * 旧视图只在"完整模式"显示，这里是 Compose 页 ⇒ 一律显示，用折叠控制占用）。
 * 用同一套 `MsgCard`（带卡片阴影 + 玻璃质感），默认**收起**成一行。
 */
/**
 * [V3.86 · 用户「**字体变小后，胶囊怎么没跟着变小呢**」]
 *
 * 字号档位换算：`Ui.FONT_SCALE` 只改字，**胶囊的内边距/高度是写死的 dp** ⇒ 字小了、胶囊没小，
 * 观感就不协调。消息级的胶囊（气泡、思考/调用/交付物卡）统一用这个函数算内边距。
 */
private fun fsDp(v: Float): androidx.compose.ui.unit.Dp =
    (v * com.dsh.mobile.ui.Ui.FONT_SCALE).dp

/** [V3.86] 思考 / 调用 / 步骤三类行**统一**的卡片形状（用户「要统一大小，现在不一样」）。 */
private val STEP_CARD_RADIUS = 22.dp

@Composable
private fun ThinkingCard(reasoning: String, ink: Color, startOpen: Boolean = false) {
    // [V3.81] `startOpen`：完整模式开场即展开；精简模式开场收起（用户之后仍可自己点开）
    var open by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(startOpen)
    }
    MsgCard(STEP_CARD_RADIUS, Modifier.fillMaxWidth().padding(vertical = 4.dp)
        .glassTap(corner = STEP_CARD_RADIUS) { open = !open }) {
        Column(Modifier.padding(horizontal = fsDp(14f), vertical = fsDp(7f))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (open) "▾ 思考过程" else "▸ 思考过程",
                    color = Color(Ui.INK_SUB), fontSize = Ui.S_CAP1.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                Text("${reasoning.length} 字", color = Color(Ui.INK_FAINT), fontSize = Ui.S_CAP1.sp)
            }
            if (open) {
                Spacer(Modifier.height(6.dp))
                // [V3.84 · 用户「展开后的**字体比正常输出内容小一点**，做个区分；**色号浅灰一点**；
                //   **文字间距也紧一点**」] ⇒ 正文(S_BODY 15.5) → 说明(CAP1 11.5) + INK_FAINT + 行高 1.28×
                Text(reasoning, color = Color(Ui.INK_FAINT), fontSize = Ui.S_CAP1.sp,
                    lineHeight = (Ui.S_CAP1 * 1.28f).sp)
            }
        }
    }
}
@Composable
private fun AssistantCard(text: String, reasoning: String, ink: Color, verbose: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        // [V3.30 · 用户「思考过程卡片可以点击缩小」] 与旧视图（`ChatAdapter:305-318`，只在完整模式显示）
        //   同口径：**默认收起**成一行「▸ 思考过程」，点一下展开全文（再点收起）。
        if (reasoning.isNotBlank()) ThinkingCard(reasoning, ink, startOpen = verbose)


    // [V3.81 · 用户「**对话内容不要能折叠**，只有思考调用这些是带折叠的」]
    //   ⇒ 助手正文**永远全展开**：不做 3 行摘要、不给「收起/展开」按钮、也不整卡可点收放。
    //     折叠只留给「思考过程」与「工具调用」这两类行（它们各自有自己的开合状态）。
    MsgCard(18.dp, Modifier.fillMaxWidth(0.92f).padding(vertical = 8.dp),
        frostier = true) {   // [V3.92] 对话输出卡的磨砂底加厚（思考/调用行不传，保持原样）
        Column(Modifier.padding(horizontal = fsDp(14f), vertical = fsDp(12f))) {
            // [V3.74 · 用户「按你说的配置 **Markdown 渲染（标题/加粗/行内代码/列表/表格/引用）**」]
            //   助手正文走 Markdown 渲染（本工程该子集由 `MarkdownText.kt` 自绘，不引第三方库）。
            // [V3.83 · 用户] 同样包一层 `SelectionContainer` ⇒ 长按可原生选取/全选/复制（只包这一条）。
            androidx.compose.foundation.text.selection.SelectionContainer {
                MarkdownText(
                    text = text,
                    ink = ink,
                    sub = Color(Ui.INK_SUB),
                    brand = Color(Ui.BRAND_FILL),
                    codeBg = Color(Ui.SELECT_BG),
                )
            }
        }
    }
    }
}

@Composable
private fun StepCard(text: String, running: Boolean, error: Boolean, ink: Color, sub: Color) {
    val fg = when {
        error -> Color(Ui.ERR)
        running -> Color(Ui.BRAND_FILL)
        else -> sub
    }
    MsgCard(STEP_CARD_RADIUS, Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text, color = fg, fontSize = Ui.S_FOOT.sp,
            modifier = Modifier.padding(horizontal = fsDp(14f), vertical = fsDp(7f)))
    }
}

/**
 * 工具调用行。
 *
 * [V3.47 曾整行隐藏 → V3.49 改回**可折叠**] 用户原话：「**我让你折叠，没让你直接消失掉**，
 * 现在完整显示和简单折叠显示的逻辑都没有了，我需要你找回来」。
 * 行为：**默认收起**成一行（`▸ 工具名 · 运行中…`），点一下**展开预览正文**、再点收起。
 */
@Composable
private fun ToolCard(title: String, body: String, error: Boolean, ink: Color, sub: Color,
                     startOpen: Boolean = false) {
    // [V3.81] `startOpen`：完整模式开场即展开；精简模式开场收起（用户之后仍可自己点开）
    var open by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(startOpen)
    }
    MsgCard(STEP_CARD_RADIUS, Modifier.fillMaxWidth().padding(vertical = 4.dp)
        .glassTap(corner = STEP_CARD_RADIUS) { open = !open }) {
        Column(Modifier.padding(horizontal = fsDp(14f), vertical = fsDp(7f))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (open) "▾ " else "▸ ", color = Color(Ui.INK_FAINT), fontSize = Ui.S_CAP1.sp)
                Text(title, color = if (error) Color(Ui.ERR) else sub, fontSize = Ui.S_CAP1.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
            }
            if (open && body.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                // [V3.84 · 用户「展开后的字体比正常输出内容小一点、浅灰、行距紧一点」] 与思考卡同一口径
                Text(body, color = Color(Ui.INK_FAINT), fontSize = Ui.S_CAP1.sp,
                    lineHeight = (Ui.S_CAP1 * 1.28f).sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun ApprovalCard(item: ChatItem, ink: Color, sub: Color) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    MsgCard(28.dp, Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("需要你批准", color = sub, fontSize = Ui.S_CAP1.sp, fontWeight = FontWeight.Bold)
            if (item.reason.isNotEmpty()) {
                Text(item.reason, color = sub, fontSize = Ui.S_CAP1.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Text(item.text, color = ink, fontSize = Ui.S_FOOT.sp, modifier = Modifier.padding(top = 5.dp))
            when {
                item.resolved -> Text(
                    if (item.resolvedOutcome.startsWith("allow")) "已允许" else "已拒绝",
                    color = sub, fontSize = Ui.S_CAP1.sp, modifier = Modifier.padding(top = 8.dp))
                item.pendingConfirm -> Text("已发送，等待电脑确认…", color = sub, fontSize = Ui.S_CAP1.sp,
                    modifier = Modifier.padding(top = 8.dp))
                else -> {
                    if (item.sendError.isNotEmpty()) {
                        Text(item.sendError, color = Color(Ui.ERR), fontSize = Ui.S_CAP1.sp,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                    Row(Modifier.padding(top = 10.dp)) {
                        MsgPill("允许", Color(0xFF34C759), Color.White) {
                            HostHolder.host?.approve(item, "allowOnce")
                        }
                        Spacer(Modifier.width(8.dp))
                        MsgPill("拒绝", Color(Ui.ERR).copy(alpha = 0.16f), Color(0xFFB71C1C)) {
                            HostHolder.host?.approve(item, "deny")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(item: ChatItem, ink: Color, sub: Color) {
    val qs = item.questions
    // 选择状态存在 ChatItem.picked 里（原地改），所以用一个本地版本号把它"推"成可观察的
    var rev by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") rev
    MsgCard(28.dp, Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(item.text.ifBlank { "需要你回答" }, color = ink, fontSize = Ui.S_FOOT.sp,
                fontWeight = FontWeight.Bold)
            if (qs != null) {
                for (i in 0 until qs.length()) {
                    val q = qs.optJSONObject(i) ?: continue
                    val qid = q.optString("id", "q$i")
                    val header = q.optString("header", "")
                    if (header.isNotEmpty()) {
                        Text(header, color = Color(Ui.INK_FAINT), fontSize = Ui.S_CAP1.sp,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                    Text(q.optString("question", ""), color = ink, fontSize = Ui.S_BODY.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    val detail = q.optString("detail", "")
                    if (detail.isNotEmpty()) {
                        Text(detail, color = sub, fontSize = Ui.S_FOOT.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    val opts = q.optJSONArray("options")
                    if (opts != null) {
                        for (j in 0 until opts.length()) {
                            val o = opts.optJSONObject(j) ?: continue
                            val label = o.optString("label", "")
                            val desc = o.optString("description", "")
                            val selected = item.picked[qid]?.contains(label) == true
                            Box(
                                Modifier.fillMaxWidth().padding(top = 8.dp)
                                    // [V3.33 · 待办 E] `glassTap` 必须在 `clip/background` 之前（外侧先画，
                                    //   否则按下只缩内容层、底色不动 ⇒ 双影）
                                    .glassTap(corner = 12.dp) {
                                        val set = item.picked.getOrPut(qid) { LinkedHashSet() }
                                        if (q.optBoolean("multiSelect", false)) {
                                            if (!set.add(label)) set.remove(label)
                                        } else {
                                            set.clear(); set.add(label)
                                        }
                                        item.typed[qid] = label
                                        rev++
                                    }
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) Color(Ui.BRAND_SOFT) else Color(Ui.CHIP_BG)),
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                    Text(label, color = ink, fontSize = Ui.S_FOOT.sp)
                                    if (desc.isNotEmpty()) {
                                        Text(desc, color = sub, fontSize = Ui.S_CAP1.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * [V3.80 · 用户「**还是不能长按复制呢**…长按会变小？成按钮了，但是也没法复制」]
 * 交付物行上的**小胶囊按钮**：把"打开 / 复制路径"做成看得见、点得到的入口，
 * 不再依赖"长按"这种用户发现不了的隐式手势。
 */
@Composable
private fun SmallPill(label: String, ink: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.30f))
            .border(0.5.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .glassTap(corner = 999.dp, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink, fontSize = Ui.S_CAP1.sp,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 5.dp))
    }
}

@Composable
private fun FilesCard(item: ChatItem, ink: Color, sub: Color) {
    val fs = item.files
    MsgCard(28.dp, Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("交付物", color = sub, fontSize = Ui.S_CAP1.sp, fontWeight = FontWeight.Bold)
            if (fs == null || fs.length() == 0) {
                Text("(无文件)", color = Color(Ui.INK_FAINT), fontSize = Ui.S_FOOT.sp)
            } else {
                for (i in 0 until fs.length()) {
                    val f = fs.optJSONObject(i) ?: continue
                    val path = f.optString("path", "")
                    val desc = f.optString("description", "")
                    val name = path.substringAfterLast('/').substringAfterLast('\\').ifBlank { "(未命名)" }
                    Column(
                        // [V3.82 · 用户纠正]「交付物**聊天界面点击就是打开就行了**，具体的到生成物里面，
                        //   点击能选下载和打开就行」⇒ 这里回到**单一动作：点击=打开**（两个按钮撤掉）。
                        Modifier.fillMaxWidth().padding(top = 8.dp)
                            .glassTap(corner = 12.dp) {
                                HostHolder.host?.previewFile(item, path)
                            },
                    ) {
                        Text(name, color = Color(Ui.BRAND_FILL), fontSize = Ui.S_SUB.sp,
                            fontWeight = FontWeight.Bold)
                        if (desc.isNotEmpty()) {
                            Text(desc, color = sub, fontSize = Ui.S_FOOT.sp)
                        }
                        if (path.isNotEmpty()) {
                            val st = item.downloadState ?: ""
                            Text(
                                if (st.isNotEmpty()) st else "点一下打开（下载/复制路径在「生成物」里）",
                                color = if (st.startsWith("已下载")) Color(Ui.LINK_OK) else Color(Ui.INK_FAINT),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentCard(name: String, text: String, ink: Color, sub: Color) {
    MsgCard(28.dp, Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_people), null, tint = Color(Ui.BRAND_FILL),
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(name, color = ink, fontSize = Ui.S_FOOT.sp, fontWeight = FontWeight.Bold)
            }
            if (text.isNotEmpty()) {
                Text(text, color = ink, fontSize = Ui.S_FOOT.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun SystemRow(text: String, faint: Color) {
    Text(text, color = faint, fontSize = Ui.S_CAP1.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
}

// ------------------------------------------------------------------ 悬浮件

@Composable
private fun ChatInputRow(
    backdrop: com.kyant.backdrop.Backdrop,
    state: GlassChatState,
    host: GlassChatHost,
    ink: Color,
    inkFaint: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // [V3.19 用户报"左下角加号键玻璃质感消失"] 去掉平色底，直接用玻璃本体
        GlassRoundButton(backdrop, 38.dp, R.drawable.ic_plus, Color(Ui.INK_SUB), Color.Transparent) {
            host.attachPick()
        }
        Spacer(Modifier.width(8.dp))
        RefractionWindow(
            backdrop = backdrop, radius = 22.dp,
            modifier = Modifier.weight(1f).height(46.dp),
        ) {
            // [V3.139 · 用户定调：「**在这个输入框换成平台的，但是效果不能变**」]
            //   ⇒ 用平台的 `EditText`（长按/粘贴/全选/选择手柄走**系统官方**那套 ✓），
            //     并**逐项对齐**原来的 Compose 观感：
            //       ① 文字颜色/字号 = 项目 `ink` / `Ui.S_BODY` ✓
            //       ② **垂直居中**（`gravity=CENTER_VERTICAL`）—— 上一版漏了这条，看起来就不一样 ✗
            //       ③ 占位文字与颜色 = 原来的「给 Agent 派个任务…」/ `inkFaint` ✓
            //       ④ 无背景（去掉 EditText 自带下划线）✓、内边距 0（外层的 16dp 仍在）✓
            //       ⑤ 光标颜色 = 项目品牌色（API 29+ 支持 `textCursorDrawable`；低版本保持系统默认）✓
            //       ⑥ 单行 + 文本键盘（与 `singleLine = true` 行为一致）✓
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    val dens = ctx.resources.displayMetrics.density
                    android.widget.EditText(ctx).apply {
                        background = null
                        setSingleLine(true)
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(0, 0, 0, 0)
                        textSize = Ui.S_BODY
                        setTextColor(android.graphics.Color.argb(
                            (ink.alpha * 255f).toInt(), (ink.red * 255f).toInt(),
                            (ink.green * 255f).toInt(), (ink.blue * 255f).toInt()))
                        setHintTextColor(android.graphics.Color.argb(
                            (inkFaint.alpha * 255f).toInt(), (inkFaint.red * 255f).toInt(),
                            (inkFaint.green * 255f).toInt(), (inkFaint.blue * 255f).toInt()))
                        hint = "给 Agent 派个任务…"
                        inputType = android.text.InputType.TYPE_CLASS_TEXT
                        // [V3.140 · 用户「**还是没有复制粘贴全选呢**」+ 早先那句「长按**只有自动填充**」]
                        //   ⚠️ 病根：系统 **Autofill（自动填充）把长按抢走了** ⇒ `EditText` 自己的
                        //   「粘贴 / 全选 / 选择手柄」被压住不弹 ✗。对这个输入框**关掉自动填充** ✓
                        //   ⇒ 长按回到文本选择，系统那条工具栏就出来了 ✓（API 26+ 可用 ✓）
                        importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                            val cur = android.graphics.drawable.GradientDrawable()
                            cur.setSize((1.5f * dens).toInt(), (20f * dens).toInt())
                            cur.setColor(android.graphics.Color.argb(
                                (Color(Ui.BRAND_FILL).alpha * 255f).toInt(),
                                (Color(Ui.BRAND_FILL).red * 255f).toInt(),
                                (Color(Ui.BRAND_FILL).green * 255f).toInt(),
                                (Color(Ui.BRAND_FILL).blue * 255f).toInt()))
                            textCursorDrawable = cur
                        }
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { }
                            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { }
                            override fun afterTextChanged(s: android.text.Editable?) {
                                val t = s?.toString() ?: ""
                                if (t != state.draft) { state.draft = t; host.draftChanged(t) }
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                update = { et ->
                    // 外部改草稿（粘贴 / 语音 / `setDraft`）时回灌，并把光标放到末尾
                    if (et.text.toString() != state.draft) {
                        et.setText(state.draft)
                        et.setSelection(et.text.length)
                    }
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        GlassRoundButton(
            backdrop, 38.dp,
            if (state.running && state.draft.isBlank()) R.drawable.ic_stop else R.drawable.ic_arrow_up,
            Color.White, Color(Ui.BRAND_FILL).copy(alpha = 224f / 255f),
        ) {
            if (state.running && state.draft.isBlank()) {
                host.stopTurn()
            } else if (state.draft.isNotBlank()) {
                val t = state.draft
                state.draft = ""
                host.send(t)
            }
        }
    }
}

/** 玻璃圆钮（‹ / ⋯ / ＋ / ↑ / ■）。 */
@Composable
private fun GlassRoundButton(
    backdrop: com.kyant.backdrop.Backdrop,
    size: androidx.compose.ui.unit.Dp,
    iconRes: Int,
    iconTint: Color,
    fill: Color,
    /** [V3.46] 让调用处能把**定位**（align/padding）直接交给它 ⇒ 少一层包装 Box（HWUI 树少一层深度）。 */
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    RefractionWindow(
        backdrop = backdrop, radius = size / 2,
        // [V3.26 · 用户 #4「这三个组件玻璃质感需要和下方文字输入框一个材质」]
        //   下方输入胶囊走的是**默认档**（环带 14dp / 幅度 28dp）⇒ 这里显式引用 tokens 对齐同档
        //   （V3.17 曾为小元件压到 6/8dp；用户看过输入框那档后要求统一，故回调）。
        refractionHeight = RefractionWindowTokens.RefractionHeight,
        refractionAmount = RefractionWindowTokens.RefractionAmount,
        // [V3.26 · 用户报②] 阴影走统一模板（紫调、外扩 6dp），排在 clip/背景之前
        modifier = modifier.size(size)
            // [V3.26 用户第 6 轮] `glassTap` 排在阴影之前 ⇒ 按住时阴影跟着缩放
            .glassTap { onClick() }
            .glassButtonShadow(androidx.compose.foundation.shape.CircleShape),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (fill.alpha > 0f) {
                Box(Modifier.fillMaxSize()
                    .clip(androidx.compose.foundation.shape.CircleShape).background(fill))
            }
            Icon(painterResource(iconRes), null, tint = iconTint, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/** 「N 子智能体」入口：独立玻璃胶囊。 */
@Composable
private fun SubagentChip(
    backdrop: com.kyant.backdrop.Backdrop,
    brand: Color,
    count: Int,
    onClick: () -> Unit,
) {
    RefractionCapsule(
        backdrop = backdrop,
        // [V3.26 用户] 「子智能体最好跟下方**发送的那个文本框**一个折射档位，然后稍微再白点」
        //   下方输入框胶囊用的是**默认档位**（`RefractionWindow` 不带参数 = 环带 14dp / 幅度 28dp），
        //   这里就显式对齐到同一档（引用 tokens，以后全局默认改了它也跟着走）。
        //   V3.17 曾把它压到 6/8dp（防小元件标签被推挤）—— 用户看过输入框那档后要求改成一致，故回调。
        refractionHeight = RefractionWindowTokens.RefractionHeight,
        refractionAmount = RefractionWindowTokens.RefractionAmount,
        // 「稍微再白点」：浅色 20% → **32%**；深色是把深体色**减淡**（30% → 22%，即更亮更白）
        tint = if (Ui.isDark()) Color(0xFF14121C).copy(alpha = 0.22f)
        else Color.White.copy(alpha = 0.32f),
        modifier = Modifier.fillMaxWidth().height(38.dp)
            .glassTap { onClick() }                            // [V3.26] 排到阴影之前
            .glassButtonShadow(RoundedCornerShape(999.dp)),    // [V3.26] 统一阴影模板
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_people), null, tint = brand, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("$count 子智能体", color = brand, fontSize = Ui.S_FOOT.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MsgPill(label: String, fill: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.width(96.dp).height(38.dp)
            // [V3.33 · 待办 E] `glassTap` 必须在 `clip/background` 之前（外侧先画 ⇒ 按下整块一起缩）
            .glassTap { onClick() }
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(listOf(fill, fill))),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontSize = Ui.S_BODY.sp, fontWeight = FontWeight.Medium) }
}

/** 消息用的**半透明卡**（正式版 `Ui.GLASS_CARD` 口径；层内不能放假玻璃，见 V3.2 报告 §1.3）。 */
@Composable
private fun MsgCard(radius: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier,
                    frostier: Boolean = false,
                    content: @Composable () -> Unit) {
    // [V3.27 · 用户「优化对话的对话玻璃胶囊卡片：**增加阴影及玻璃质感**」]
    //   ① 阴影：走统一模板的卡片级（自绘模糊投影 + clipOutPath，只画形状外）；
    //   ② 质感：体色略加厚（浅 26%→30% / 深 30%→34%）+ 内层顶部高光（受光）⇒ 更像"玻璃卡"而不是平色块。
    //   注意：消息卡在**取样层内**，它的阴影会被玻璃件采到 —— 这是"页面的一部分"，无害。
    //
    // [V3.92 · 用户「所有**对话输出**的胶囊（不含调用/思考/修改那些）把**白色的磨砂底再做磨砂点**」]
    //   这些卡在取样层内、做不了真模糊 ⇒ "更磨砂"的可控旋钮就是**体色厚度**：
    //   `frostier = true`（只给助手输出卡）在原有基础上再加 15% ⇒ 白 30% → **45%**
    //   （深色 34% → 49%）。思考/调用/步骤行不传它 ⇒ 保持原样，两类卡一眼可分。
    val baseA = if (Ui.isDark()) 0.34f else 0.30f
    val bg = if (Ui.isDark()) Color(0xFF14121C).copy(alpha = baseA + if (frostier) 0.15f else 0f)
    else Color.White.copy(alpha = baseA + if (frostier) 0.15f else 0f)
    val shape = RoundedCornerShape(radius)
    // [V3.44 · V3.32 §2.4②] **滚动中跳过自绘阴影**：阴影要现建模糊位图（UI 线程箱式模糊）+ 上传 GPU，
    //   而滚动时行不停复用/重建 ⇒ 这两笔正是 jank 的两个最大头（`Slow issue draw commands` /
    //   `Slow bitmap uploads`）。松手立刻恢复；不提供 Local 的地方（其它屏）行为不变。
    val scrolling = LocalGlassScrolling.current
    Box(
        modifier.then(if (scrolling) Modifier else Modifier.glassCardShadow(shape))
            .clip(shape).background(bg)
            .border(0.5.dp, Color(Ui.HAIRLINE), shape),
    ) {
        Box(Modifier.matchParentSize().clip(shape).background(Brush.verticalGradient(listOf(
            Color.White.copy(alpha = 0.10f), Color.Transparent))))
        content()
    }
}

/**
 * 审批/提问/交付物这类**行内动作**要回调宿主，而 `ChatRow` 的签名不想带一串回调
 * （9 类行的可读性优先），所以用一个"当前宿主"的进程级引用：
 * **前提是同一时刻只有一个对话页在显示** —— 对单 Activity 的 App 成立；宿主换成多实例时必须改掉。
 */
internal object HostHolder {
    @Volatile
    var host: GlassChatHost? = null
}

/** 时间分隔条的文案（`HH:mm`，与正式版同粒度）。 */
private fun timeLabel(ms: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = ms
    return String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
}


/**
 * [V3.28 · 用户「不是那个细指示条，是**截图这种滚动条滑轮**」]
 *
 * 虚线**轨道** + 粗**圆角滑块**的滚动条（用户给的参照：右侧一条虚线竖轨 + 一个白色厚滑块）。
 * 之前那版 `GlassScrollIndicator` 是"细长进度条"（3~4dp 通体半透明），不是用户要的"滑轮"。
 *
 * - 轨道：竖直**虚线**（`PathEffect.dashPathEffect`），贯穿可见区 ⇒ 明确的"可滚动"暗示；
 * - 滑块：**厚圆角条**（高 54dp / 宽 6dp），位置按 `首可见项 / 总数` 走；
 * - **常显**（不随滚动淡出）—— 用户要的是"看得见的滑轮"。
 */
@Composable
fun TurnRail(
    state: androidx.compose.foundation.lazy.LazyListState,
    turns: List<Int>,
    running: Boolean,
    modifier: Modifier = Modifier,
    activeColor: androidx.compose.ui.graphics.Color,
    idleColor: androidx.compose.ui.graphics.Color,
    labels: List<String> = emptyList(),
) {
    // [V3.36 · 用户「你这也不是啊！！！」+「看他的代码」] 按**朋友 fork 的 `ui/MessageScrubber.java`
    //   （右侧「问题刻度尺」）规格移植到 Compose：
    //   · 固定 **10 格**窗口 + 格距 13dp（1:1 跟手）；刻度 13×2.6dp、**选中格 22×3.6dp**（主色）；
    //   · 30dp 半透明灰底（浅 0xEBF7F6FB / 深 0xEB1C1B22，92% 压住背景）；
    //   · 上翻=往期、下翻=最新；中间格=当前；拖动/点按都跳转到该回合；
    //   · 拖动时在左侧显示**预览气泡**（品牌渐变 + 白字，最多 4 行截断）。
    //   ⚠️ 纯 Canvas 绘制 + Compose Text 气泡 ⇒ 不新增玻璃件（避开 RenderEffect 那条 native 崩溃路径）。
    val n = turns.size
    if (n <= 0) return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current   // [V3.34] 对齐参照：按下/换格各一次轻触感
    val firstUser = turns.indexOfLast { it <= state.firstVisibleItemIndex }.coerceAtLeast(0)
    var posF by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(firstUser.toFloat())
    }
    var dragging by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    androidx.compose.runtime.LaunchedEffect(firstUser, dragging) {
        if (!dragging) posF = firstUser.toFloat()
    }
    val sel = posF.roundToInt().coerceIn(0, n - 1)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val h = maxHeight
        val slots = 10
        val step = 13.dp
        val centerY = h / 2
        val frac = posF - posF.roundToInt()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val stepPx = with(density) { step.toPx() }
        val centerPx = with(density) { centerY.toPx() }
        val railW = with(density) { 30.dp.toPx() }
        val rightPad = with(density) { 9.dp.toPx() }
        // ① 灰底 + 10 格刻度（纯 Canvas）
        androidx.compose.foundation.Canvas(
            Modifier.fillMaxSize().pointerInput(n, stepPx) {
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        haptic.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    },
                    // [V3.34 · 对齐参照 `MessageScrubber:259-268`] **跳转只在抬手时发生一次**。
                    //   上一版在 `onDrag` 里每个 MOVE 都 `scrollToItem` ⇒ 列表跟着手指打架、且每个 MOVE 一次滚动。
                    //   ⚠️ 必须**从 `posF` 现读**选中的下标：`pointerInput(n, stepPx)` 的 key 里没有 posF，
                    //   它的 lambda 只在 key 变化时重建 ⇒ 闭包里捕获的 `sel`（普通 val）是**首次组合时的值**，
                    //   永远停在 0（实测：抬手后 `after` 截图与拖前逐字节相同 = 每次都跳回 `turns[0]`）。
                    //   `posF` 是 `mutableStateOf` 的委托，闭包里读到的才是当前值。
                    onDragEnd = {
                        dragging = false
                        val idx = posF.roundToInt().coerceIn(0, n - 1)
                        scope.launch {
                            runCatching { state.scrollToItem(turns[idx]) }
                        }
                    },
                    onDragCancel = { dragging = false },
                    onDrag = { change, dy ->
                        change.consume()
                        // [V3.34 · 修方向反了] 参照 `MessageScrubber:246-248` 是 `posF += dy / step`：
                        //   手指**下滑**(dy>0) ⇒ posF 增大 ⇒ 更新的一条（= 下翻看最新）。
                        //   上一版写的是 `posF - dy/step`（负号），**与其自己的注释和参照都相反** ⇒ 拖起来是反的。
                        val before = posF.roundToInt()
                        posF = (posF + dy.y / stepPx).coerceIn(0f, (n - 1).toFloat())
                        val now = posF.roundToInt()
                        if (now != before) {
                            // [V3.42 · 用户「优化**滑动手感**」] 每**换一格**滚一次（不是每个 MOVE 都滚）：
                            //   手指下方的内容跟着走 ⇒ 拖动有即时回应；一格最多一次 ⇒ 不会像 V3.33 之前
                            //   那种"每个 MOVE 一次 scrollToItem"把列表和手指拖成打架。
                            //   抬手时 `onDragEnd` 再精确落一次，保证落点准。
                            scope.launch { runCatching { state.scrollToItem(turns[now]) } }
                            haptic.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        }
                    },
                )
            },
        ) {
            val railCx = size.width - rightPad - railW / 2f
            val mid = slots / 2                                   // 5 = 中间那一格（参照 SLOTS/2）
            // [V3.42 · 用户「**建议不要后面的白色底，只要这个横条**」（批注② 框的就是那块白底）]
            //   删掉上一版照参照搬来的三样：30dp 半透明灰底、灰底发丝描边、中间格"波轮定位槽"。
            //   它们在参照里的用途是"压住背后的聊天内容"，但落到这屏就变成一整块**白板**漂在消息上面。
            //   现在**只画横条** ⇒ 回到 V3.29 的桌面回合导轨口径（"一列短横条，每个回合一枚、
            //   当前回合加长变主色、可点跳转"）：**一条横条 = 一个回合**。
            //   缺少白底后横条必须自己"立"起来 ⇒ 用主题自适应的颜色（浅色深条 / 深色白条）
            //   + **上亮下暗的极小渐变**（真玻璃受光的读感），当前回合用品牌双色渐变并加长。
            for (k in 0 until slots) {
                // [V3.51 · 用户「右侧的滚动条怎么**只显示三条**」] 真凶：这一版按"**有没有回合**"跳过了空格
                //   （`if (idx < 0 || idx >= n) continue`）⇒ 只有 3 条用户消息时就只剩 3 根条。
                //   参照实现 `MessageScrubber` 是**固定 10 格全画**（它只拿数据算气泡文案）⇒ 这里同样**恒画 10 格**；
                //   没有对应回合的格子画淡一档（仍然占位，保持"固定 10 格窗口"的观感）。
                val idx = sel + (k - mid)
                val hasTurn = idx >= 0 && idx < n
                val cur = k == mid
                val cy = centerPx + (k - 4.5f) * stepPx + frac * stepPx
                val w = with(density) { (if (cur) 22f else 13f).dp.toPx() }
                val hh = with(density) { (if (cur) 3.6f else 2.6f).dp.toPx() }
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = if (cur) {
                            listOf(androidx.compose.ui.graphics.Color(Ui.BRAND_G1),
                                   androidx.compose.ui.graphics.Color(Ui.BRAND_G2))
                        } else {
                            val base = if (hasTurn) idleColor
                            else idleColor.copy(alpha = idleColor.alpha * 0.45f)
                            listOf(base, base.copy(alpha = base.alpha * 0.78f))
                        },
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(railCx - w / 2f, cy - hh / 2f),
                    size = androidx.compose.ui.geometry.Size(w, hh),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(hh / 2f, hh / 2f),
                )
            }
        }
        // ② 拖动时的预览气泡 —— **与参照同规格**（`MessageScrubber:41-43, 146-188`）：
        //   300dp 宽、最多 8 行、**带「第 N / M 条」标题行 + 消息正文**、与刻度尺**共用同一根中轴**（居中）。
        //   [V3.33 修] 气泡原来**一个字都画不出来**（模拟器 4s 连续拖拽抓 7 帧 SHA256 全同，
        //   `evidence/v3330/009_dragframe_*.png`）：本组件外盒只有 48dp 宽（`width(48.dp)` + end padding 6dp
        //   ⇒ 内容约束 maxWidth = 42dp），而气泡写 `padding(end = 46.dp)` ⇒ 子内容 maxWidth = 42−46 < 0 ⇒ 夹成 0；
        //   `Modifier.width()` 默认 `enforceIncoming = true` 又被父约束夹回去 ⇒ 0 宽不可见。
        //   修法：`wrapContentSize(unbounded = true)` 让子树按自身尺寸量（父盒仍 42dp、触摸带不变）
        //   + `requiredWidth` 锁宽（required 系列不受父约束夹取）。
        //   [V3.34 修] 规格从"240dp / 4 行 / 只有一句占位文案"提到参照的 300dp / 8 行 / 标题行+正文；
        //   并且**调用处开始传 `labels`**（原来没传 ⇒ `labels` 恒为空 ⇒ 永远只显示占位句，看不到问题原文）。
        if (dragging) {
            val txt = labels.getOrNull(sel)?.trim().orEmpty()
            androidx.compose.material3.Surface(
                modifier = Modifier.align(Alignment.CenterEnd)
                    .wrapContentSize(Alignment.CenterEnd, unbounded = true)
                    .padding(end = 38.dp)          // = 参照的 STRIP(40dp) + 4dp，气泡右缘不压住触摸带
                    .requiredWidth(300.dp),
                color = androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(
                            listOf(androidx.compose.ui.graphics.Color(Ui.BRAND_G1),
                                   androidx.compose.ui.graphics.Color(Ui.BRAND_G2)))),
                ) {
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 12.dp)) {
                        Text(
                            "第 ${sel + 1} / $n 条",
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.80f),
                            fontSize = 11.sp,
                        )
                        if (txt.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                txt,
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
/**
 * 【右侧滚动指示条（V3.18 用户报"右侧之前合并的滚动条消失了未恢复"）】
 *
 * 旧对话页显式关掉了 ListView 的原生滚动条（`setVerticalScrollBarEnabled(false)`），
 * 新的 Compose 页**压根没有滚动条** ⇒ 长列表滑到哪没有视觉锚点。
 * 本组件补一条与玻璃同材质的细指示条：滚动时显形、停下 900ms 后淡出。
 * 用全限定名写，避免动 import。
 */
@androidx.compose.runtime.Composable
fun GlassScrollIndicator(
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    color: androidx.compose.ui.graphics.Color,
    width: androidx.compose.ui.unit.Dp = 4.dp,   // [V3.27] 3→4dp 更易看见
) {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    if (total <= 0) return
    val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
    val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
    val frac = ((last - first + 1).toFloat() / total).coerceIn(0.10f, 1f)
    val pos = (first.toFloat() / total).coerceIn(0f, (1f - frac).coerceAtLeast(0f))
    var show by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(state.isScrollInProgress, state.firstVisibleItemIndex) {
        if (state.isScrollInProgress) { show = true } else { kotlinx.coroutines.delay(900L); show = false }
    }
    // [V3.27 · 用户「之前侧边滚动条未修复」] 原来**静止时 alpha=0**（只有滑动那 900ms 里看得见）
    //   ⇒ 长列表停下后没有任何位置锚点，等于"看不见/没修"。改成：**常驻微亮（0.34）**、
    //   滑动时提到 1.0；宽度默认 3dp→4dp，更容易看见。
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (show) 1f else 0.5f,   // [V3.27] 静止 0.34 → 0.5（配上不透明色 ⇒ 真看得见）
        animationSpec = androidx.compose.animation.core.tween(220),
    )
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val h = maxHeight
        Box(
            Modifier.align(Alignment.TopEnd).offset(y = h * pos).width(width)
                .fillMaxHeight(frac).clip(RoundedCornerShape(999.dp))
                .background(color.copy(alpha = color.alpha * alpha)),
        )
    }
}
