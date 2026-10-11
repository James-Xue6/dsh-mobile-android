package com.dsh.mobile.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.ui.Ui

/**
 * 【统一弹窗骨架（V3.10 · 目标 ⑥）】底部 sheet 形态。
 *
 * ## 为什么弹窗必须画在**各自屏的同一棵 Compose 树**里
 * 玻璃要取样"背后的页面"，而 kyant 的 `layerBackdrop` 只能录到**同一组合/同一窗口层**的内容。
 * 如果把弹窗做成 MainActivity 里另挂的 `ComposeView`，它取样不到页面 ⇒ 只能糊出一层白纱（V3.9.3 已证）。
 * 因此：**每屏在自己的 `GlassDeviceScreen/GlassChatScreen/...` 里渲染这个 overlay**，
 * 并且**放在取样层之外**（与页面上其它玻璃件一致）。
 *
 * ## 形态（与页面同一套材质语言）
 * 遮罩（点击关闭）→ 底部圆角玻璃面板（`RefractionWindow`）→ 抓杆 → 标题 → 菜单项（半透明卡 + 图标 + 说明）。
 *
 * ⚠️ **V3.19 的"缩小 + 到处加阴影"是未经真机验证的推测改动**（用户反馈"都出问题了"）⇒ 已整段回退。
 * 现阶段尺寸由下方 **单一系数 [SheetK]** 统一控制（V3.21 起），改它即整体缩放。
 */
/**
 * 【V3.21 · 弹窗整体缩放系数】
 *
 * 用户反馈「添加设备弹窗框有点大，整体缩小点，等比例」。
 * 模拟器实测（`evidence/v3250/`）：面板高 **286dp**，且**是内容自适应**（上下边界 825/1577px，
 * 底下没有空腔）⇒ 想缩小只能缩**内部**尺寸，因此把它们全部收成一个系数。
 *
 * 规则：
 * 1. **只改这一个数**即可整体缩放（改完必须立刻编译 + 模拟器出对比图，不许连着改别的）；
 * 2. 面板本体必须保持**通栏**（V3.15 的成果），本系数不动宽度，只动内部 padding/尺寸/圆角；
 * 3. 触控下限：取消 / 确认这类按钮不小于 [SheetMinButton]（低于它点不准）；
 * 4. ⚠️ **字号**：项目约定「字号归全局类型比例管」（`Ui.java:3115`），所以 [Ui.S_HEAD] 等全局常量
 *    **一行未动**；本 sheet 内的字号是否也按系数缩，见 [SheetScaleType]（默认 false = 只缩布局）。
 */
private const val SheetK = 0.8f

/** sheet 内字号是否也按 [SheetK] 缩放（false = 遵守"字号归全局管"）。 */
private const val SheetScaleType = false

/** 按钮高度下限（触控目标），避免等比例缩到点不准。 */
private val SheetMinButton = 40.dp

/** 统一取尺寸：`SheetDp(14f)` = 14dp × [SheetK]。 */
private fun SheetDp(v: Float) = (v * SheetK).dp

/** 统一取字号：按 [SheetScaleType] 决定是否随系数缩放（默认不缩，用全局字号）。 */
private fun SheetSp(v: Float) = (if (SheetScaleType) v * SheetK else v).sp

/**
 * 行高。
 *
 * ⚠️ **实测坑（V3.21）**：Material3 默认行高是**固定 24sp**，只改 `fontSize` **不会**改行高 ——
 * 模拟器实测 15.5sp 标题与 11.5sp 说明文字**都是 24dp 行**。所以"等比例缩小"如果只改字号，
 * 字变窄了、行高纹丝不动，面板高度**一点都不降**（V3.21 第一版就是这样，286.9→254.9dp 全是布局那点钱）。
 * 行高属布局量，故这里**始终**按 [SheetK] 缩。
 */
private fun SheetLineSp() = (24f * SheetK).sp

class GlassSheetItem(
    val icon: Int,
    val label: String,
    val desc: String = "",
)

/**
 * 【V3.21 表单形态的字段】`GlassSheetState.showForm(...)` 用。
 *
 * @param label 字段名（画在输入框上方）
 * @param value 初始值
 * @param hint  占位提示（为空时显示）
 * @param password 是否掩码显示（设备令牌这类长期凭证必须 true，别让截屏带走）
 */
class GlassSheetField(
    val label: String,
    val value: String = "",
    val hint: String = "",
    val password: Boolean = false,
)

/**
 * 【V3.24 列表形态的候选项】`GlassSheetState.showList(...)` 用（选择模型 / 思考等级 / 权限 / 项目 …）。
 *
 * @param label    主文案
 * @param sub      副文案（如「思考等级 Off / Low / High / Max」，空则不显示）
 * @param selected 当前项：加粗 + 打勾
 * @param group    非空则在这一项**之前**画一个分组标题（同一组只画一次）
 */
class GlassSheetOption @JvmOverloads constructor(
    val label: String,
    val sub: String = "",
    val selected: Boolean = false,
    val group: String = "",
    /**
     * [V3.24] 右对齐的**数值/尾注**（如「用量」面板的 `12.3k tok` / `62%`）。空则不占位。
     * 键值型面板（用量）用它才像"明细"，否则只剩一堆标题。
     */
    val trailing: String = "",
)

class GlassSheetState {    var visible by mutableStateOf(false)
    var title by mutableStateOf("")
    var message by mutableStateOf("")
    var items by mutableStateOf<List<GlassSheetItem>>(emptyList())
    /** 宿主用它区分"当前这张 sheet 是哪一种"（如 `add` / `delete:<设备id>`），onPick 的 index 才不歧义。 */
    var tag by mutableStateOf("")
    /** 「确认」形态：非空时改画 标题 + 说明 + 一个确认按钮（危险操作用 danger=true 变红）。 */
    var confirmLabel by mutableStateOf("")
    var danger by mutableStateOf(false)

    // ---------------------------------------------------------------- 表单形态（V3.21）
    /** 「表单」形态：非空时画 标题 + 说明 + N 个输入框 + 主按钮（与菜单/确认三选一）。 */
    var formFields by mutableStateOf<List<GlassSheetField>>(emptyList())
    var formSubmitLabel by mutableStateOf("")
    /** 提交前/后的内联报错（非空则红字显示在按钮上方）。 */
    var formError by mutableStateOf("")
    /** 提交回调：返回 null/空串 = 成功（自动收起）；返回文案 = 内联报错并**保持打开**。 */
    var onSubmit: ((List<String>) -> String?)? = null

    // ---------------------------------------------------------------- 列表形态（V3.24）
    /** 「列表」形态：非空时画 标题 + 说明 + 分组候选项（单选，当前项打勾）+ 关闭。 */
    var listOptions by mutableStateOf<List<GlassSheetOption>>(emptyList())
    /** 列表形态的收口按钮文案（默认「关闭」）。 */
    var listCloseLabel by mutableStateOf("关闭")
    /**
     * 当前是否是**列表形态**。
     *
     * ⚠️ 收口按钮文案必须看这个标志，**不能**用 `listOptions.isNotEmpty()`：
     * 「生成物」空态就是"列表形态但 0 项"，那样会退回成「取消」（本轮实测踩到）。
     */
    var listActive by mutableStateOf(false)
    /** 选中回调（下标 = listOptions 的下标）；选完自动收起。 */
    var onListPick: ((Int) -> Unit)? = null
    /** 可选**长按**回调（「生成物」那一行需要：长按=下载/复制路径）。 */
    var onListLongPick: ((Int) -> Unit)? = null
    /**
     * [V3.24] 就地改一行的副文案（下载进度："正在下载 42%"）。
     * 旧面板是拿到那个 TextView 的引用原地 setText，这里等价地对 listOptions 赋新值触发重组。
     */
    fun updateOptionSub(index: Int, sub: String) {
        if (index < 0 || index >= listOptions.size) return
        val next = listOptions.toMutableList()
        val o = next[index]
        next[index] = GlassSheetOption(o.label, sub, o.selected, o.group, o.trailing)
        listOptions = next
    }

    fun showMenu(tag: String, title: String, items: List<GlassSheetItem>) {
        this.tag = tag
        this.title = title
        this.message = ""
        this.confirmLabel = ""
        this.danger = false
        this.items = items
        clearForm()
        this.visible = true
    }

    fun showConfirm(tag: String, title: String, message: String, confirmLabel: String, danger: Boolean) {
        this.tag = tag
        this.title = title
        this.message = message
        this.confirmLabel = confirmLabel
        this.danger = danger
        this.items = emptyList()
        clearForm()
        this.visible = true
    }

    /**
     * 表单形态（V3.21 · 交接 §6 第 1 条后半）：
     * 「手动添加设备」原来是 `MainActivity.showManualAddDialog()` 的**旧 Java 不透明白底对话框**，
     * 与本页玻璃语言不符；改用本骨架渲染玻璃表单，写库/连接仍走原流程（`addManualDevice`）。
     */
    fun showForm(
        tag: String,
        title: String,
        message: String,
        fields: List<GlassSheetField>,
        submitLabel: String,
        onSubmit: (List<String>) -> String?,
    ) {
        this.tag = tag
        this.title = title
        this.message = message
        this.items = emptyList()
        this.confirmLabel = ""
        this.danger = false
        // [V3.134 · 用户「待发送的修改**和现有的弹窗放到一块了**，这不合理」] ⚠️ 必须**反向清掉列表态**：
        //   渲染器里「列表」与「表单」是**两个独立 if** ⇒ 从列表菜单点进来的表单会和**列表内容画进同一个面板** ✗
        //   （`showList` 里有 `clearForm()`，而 `showForm` 之前**没有**对应的清理 ⇒ 漏了这一半）。
        this.listOptions = emptyList()
        this.onListPick = null
        this.onListLongPick = null
        this.formFields = fields
        this.formSubmitLabel = submitLabel
        this.formError = ""
        this.onSubmit = onSubmit
        this.visible = true
    }

    private fun clearForm() {
        formFields = emptyList()
        formSubmitLabel = ""
        formError = ""
        onSubmit = null
        listOptions = emptyList()
        listCloseLabel = "关闭"
        listActive = false
        onListPick = null
        onListLongPick = null
    }

    /**
     * 列表形态（V3.24）：**单选**候选项，当前项打勾；点一项即回调并收起。
     *
     * <p>为什么加这个：交接 §6 第 3/6 条里那些面板（选择模型 / 思考等级 / 权限 / 项目 / 用量 /
     * 生成物 / 任务）原来都是 `ui/ModelSheet` 等**旧 Java 不透明白面板**，与本 App 的玻璃语言不一致。
     * 有了这一形态，它们可以逐个搬进本骨架（同一套材质与尺寸系数）。
     */
    fun showList(
        tag: String,
        title: String,
        message: String,
        options: List<GlassSheetOption>,
        closeLabel: String = "关闭",
        onPick: (Int) -> Unit,
        onLongPick: ((Int) -> Unit)? = null,
    ) {
        this.tag = tag
        this.title = title
        this.message = message
        this.items = emptyList()
        this.confirmLabel = ""
        this.danger = false
        clearForm()
        this.listOptions = options
        this.listCloseLabel = closeLabel
        this.listActive = true
        this.onListPick = onPick
        this.onListLongPick = onLongPick
        this.visible = true
    }

    /**
     * [V3.101 · 用户「弹窗**不能调整到最上层吗**？点三个点→侧抽屉收回去→再弹窗，太多此一举了」]
     *
     * 面板**关闭**时的回调：宿主用它把 Activity 的**视图层级还原**（弹窗为了压住抽屉会临时
     * `bringToFront` 把自己提到最上层；关掉后要把抽屉那层放回去，否则抽屉"打开了却看不见"）。
     */
    var onHide: (() -> Unit)? = null

    fun hide() {
        visible = false
        onHide?.invoke()
    }
}

@Composable
fun GlassSheetOverlay(
    backdrop: com.kyant.backdrop.Backdrop,
    state: GlassSheetState,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    /**
     * [V3.118 · 用户「**修改名称为啥又变回去了，进去再出来又好了？**……是不是所有玻璃弹窗都有这个 bug」]
     *
     * 等多少帧再画玻璃面板。**默认 0 = 与改动前逐字一致**（共享组件不为单一场景改行为）。
     *
     * <p>为什么需要：`backdrop` 那层取样内容是在**绘制阶段**记录的，而面板进来是**同一帧**就采样 ⇒
     * 首次打开可能采到"**空/上一帧**"的层 ⇒ 材质不对；关掉再打开（层已记录好）就正常了 ——
     * 这正是用户看到的"**进去再出来又好了**"。传 `≥1` ⇒ 面板延后一帧开始采样，**首次即正确** ✓。
     */
    settleFrames: Int = 0,
) {
    if (!state.visible) return

    // 首帧就位闸门：只对显式传参（≥1）的站点生效 ⇒ 默认路径逐字不变
    if (settleFrames > 0) {
        var ready by androidx.compose.runtime.remember(state.tag, state.visible) {
            androidx.compose.runtime.mutableStateOf(false)
        }
        if (!ready) {
            androidx.compose.runtime.LaunchedEffect(state.tag, state.visible) {
                repeat(settleFrames) { androidx.compose.runtime.withFrameNanos { } }
                ready = true
            }
            return
        }
    }

    val radius = SheetDp(30f)

    // 用 BoxWithConstraints 才能拿到 maxWidth 来算左右内缩（见下方面板的说明）
    // [V3.21] `.imePadding()`：表单展开、点输入框弹键盘时，把可用区缩到键盘之上 ⇒ 居中的卡片自动上移，
    //   「保存并连接 / 取消」不会被键盘盖住（实测未加时按钮正好被键盘压住）。
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        // 列表形态的高度上限（0.55 = 旧 `ui/ModelSheet.MaxHeightScrollView` 的同款比例）。
        // ⚠️ 必须在 BoxWithConstraints 作用域内先取出来：进到 Column 的 content lambda 后 maxHeight 不再可隐式访问。
        val listMaxHeight = maxHeight * 0.55f
        // 遮罩：压暗页面 + 点它关闭（不透明底由玻璃面板承担）
        Box(
            Modifier.fillMaxSize()
                // 遮罩只是「点卡片外关掉」的判定区，**不该有按压反馈**：缩放会把整屏黑幕缩小、
                // 露出未压暗的四边；压暗层则会在点空白时再叠一层黑（同「按压反馈双影」同源问题）。
                .glassTap(corner = 0.dp, scale = 1f, dim = 0f) { onDismiss() }
                .background(Color.Black.copy(alpha = 0.34f)),
        )

        // 玻璃面板（**屏幕正中**的浮动卡）
        // [V3.21] 用户：「左右也缩小」+「位置应该在屏幕中间」⇒ 居中 + 左右各内缩 屏宽×(1−SheetK)/2
        //   （411dp 屏上 ≈41dp，面板 329dp）。
        // ⚠️ **不能用 `fillMaxWidth(SheetK)`**：那会把宽度约束改成有限值，而 `RefractionWindow` 内部是
        //   `BoxWithConstraints` + `Box(fillMaxSize())`，**只有高度无界时才 wrap 内容**；一旦约束有限，
        //   内层 `fillMaxSize()` 就撑满 ⇒ 面板变**整屏高**（本轮实测 E/F 档即此故障）。
        //   故用 `padding(horizontal = …)` 内缩（V3.15 的历史做法）。
        // ⚠️ 已复检 V3.15 的老 Bug 1（内缩时 `Shadow.Default` 外阴影在两侧显形像"多出一圈框"）：
        //   实测卡片外仅差 3 级亮度 ⇒ **未复发**，不需要给 `RefractionWindow` 加 `shadow` 开关。
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth()
                .padding(horizontal = maxWidth * ((1f - SheetK) / 2f))
                // ⚠️【必须】面板自身**吞掉点击**：Compose 里「有背景但没有点击节点」的区域不消费触摸事件，
                //   于是点在**卡内空白**（标题上方、行与行之间、卡片内边距）会**穿透到背后的遮罩**，
                //   被遮罩的 glassTap 当成"点卡片外"，把弹窗关掉 —— 用户实测报的正是这个
                //   （2026-10-09：面板开着时点 (540,920) 卡内空白 → 弹窗消失）。
                //   用 pointerInput 而非 clickable：只消费事件，不引入点击语义 / 涟漪 / 按压动画。
                .pointerInput(Unit) { detectTapGestures { /* 吞掉：卡内空白不关闭弹窗 */ } },
        ) {
            RefractionWindow(
                backdrop = backdrop,
                radius = radius,
                // ⚠️【V3.21 真根因】必须加 `.wrapContentHeight(unbounded = true)`：
                //   `RefractionWindow` 内部是 `BoxWithConstraints(modifier) { Box(Modifier.fillMaxSize()) }`，
                //   父级高度约束**有界**时内层 `fillMaxSize()` 就**撑满整屏** ⇒ 面板其实是"整屏高的玻璃面"，
                //   内容只是被垂直居中（**通栏时看不出来，一加左右内缩就暴露成一条整屏高的玻璃柱**）。
                //   这正是用户说的"框大"。给子级**无界高度** ⇒ `fillMaxSize()` 对高度失效 ⇒ 面板回到内容高度。
                //   ❌ 不能用 `.height(IntrinsicSize.Min)`：`BoxWithConstraints` 是 SubcomposeLayout，
                //      查询内在尺寸会抛 `IllegalStateException`（本轮实测崩过一次）。
                modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true),
                // [V3.26 · 用户报③「对话界面所有的对话框加白一点、玻璃质感再强点」]
                //   磨砂档 默认 6dp → **10dp**（质感更强；面板是大面积件，磨砂感此前几乎看不出来）
                //
                // ⚠️【V3.98 回退 · 严重教训】V3.96 我在这里加了 `tint = 白 58% / 深 66%`，
                //   本意是修"抽屉里这张 sheet 太透、背后文字读得出来"，但这个文件是**所有弹窗共用**的
                //   ——权限 / 模型 / 生成物 / 任务…**全部**被我刷成了白底 ✗✗（用户当场发现：
                //   「权限、模型、生成物的弹窗应该都是玻璃质感的，现在都变成白色了」）。
                //   ⇒ 已经**整行删除**，恢复默认玻璃体色。教训：**共用组件不许为了单一场景改材质**
                //     （要分场景就加**可选参数**，且默认值必须等于原行为）。见 `GlassUI_SPEC_DIALOG.md` §4。
                blurRadius = 10.dp,
            ) {
                // [V3.29 · 遗留 B] 系统返回键关面板（旧 Dialog 会关，Compose 浮层原来不会 ⇒ 用户按返回像"没反应"）
            // [V3.29 · 遗留 B] 系统返回键关面板 —— ⚠️ 必须**先判 owner 是否在**：
            //   嵌在 MainActivity 的 ComposeView 里时**没有** `OnBackPressedDispatcherOwner`，
            //   直接调 `BackHandler` 会抛 `IllegalStateException: No OnBackPressedDispatcherOwner…`
            //   （实测崩溃：GlassSheet.kt:348）。所以：有 owner 才挂，没有就退化为"只能点空白/关闭"。
            val backOwner = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
            if (backOwner != null) {
                androidx.activity.compose.BackHandler(enabled = true) { onDismiss() }
            }
            Box(Modifier.fillMaxWidth()) {
                    // 面板自身再垫一层同色半透明底（保证选项区文字对比度）
                    // [V3.26 同一条] 白度 浅 26%→**38%** / 深 30%→**36%**（"加白一点"）
                    Box(
                        Modifier.matchParentSize()
                            .clip(RoundedCornerShape(radius))
                            .background(
                                if (Ui.isDark()) Color(0xFF14121C).copy(alpha = 0.36f)
                                else Color.White.copy(alpha = 0.38f)
                            ),
                    )
                    Column(Modifier.fillMaxWidth().padding(SheetDp(14f))) {
                        Box(
                            Modifier.align(Alignment.CenterHorizontally)
                                .width(SheetDp(38f)).height(SheetDp(4f))
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(Ui.INK_FAINT).copy(alpha = 0.5f)),
                        )
                        Spacer(Modifier.height(SheetDp(12f)))
                        if (state.title.isNotEmpty()) {
                            Text(
                                state.title, color = Color(Ui.INK), fontSize = SheetSp(Ui.S_HEAD), lineHeight = SheetLineSp(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = SheetDp(4f), bottom = SheetDp(10f)),
                            )
                        }
                        if (state.message.isNotEmpty()) {
                            Text(
                                state.message, color = Color(Ui.INK_SUB), fontSize = SheetSp(Ui.S_FOOT), lineHeight = SheetLineSp(),
                                modifier = Modifier.padding(start = SheetDp(4f), bottom = SheetDp(10f)),
                            )
                        }
                        state.items.forEachIndexed { i, it ->
                            SheetRow(it) { onPick(i) }
                            if (i != state.items.lastIndex) Spacer(Modifier.height(SheetDp(8f)))
                        }
                        // 「列表」形态（V3.24）：分组标题 + 单选候选项（当前项加粗打勾）；高度封顶 55% 后滚动
                        //   （旧 `ui/ModelSheet` 的 MaxHeightScrollView 是同一个 0.55 比例，别改）
                        if (state.listOptions.isNotEmpty()) {
                            Column(
                                Modifier.fillMaxWidth()
                                    .heightIn(max = listMaxHeight)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                var lastGroup = ""
                                state.listOptions.forEachIndexed { i, o ->
                                    if (o.group.isNotEmpty() && o.group != lastGroup) {
                                        lastGroup = o.group
                                        Text(
                                            o.group, color = Color(Ui.INK_FAINT),
                                            fontSize = SheetSp(Ui.S_CAP1), lineHeight = SheetLineSp(),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(
                                                start = SheetDp(4f), top = SheetDp(8f), bottom = SheetDp(4f)),
                                        )
                                    }
                                    SheetOptionRow(o, state.onListLongPick?.let { lp -> { lp(i) } }) {
                                        state.onListPick?.invoke(i)
                                        state.hide()
                                    }
                                    Spacer(Modifier.height(SheetDp(8f)))
                                }
                            }
                        }
                        // 「表单」形态（V3.21）：N 个输入框 + 主按钮；值由本组件自己持有（每次展开重置）
                        if (state.formFields.isNotEmpty()) {
                            val values = remember {
                                mutableStateListOf<String>().also { l ->
                                    l.addAll(state.formFields.map { it.value })
                                }
                            }
                            state.formFields.forEachIndexed { i, f ->
                                SheetFieldCard(f, values.getOrElse(i) { "" }) { nv ->
                                    if (i < values.size) values[i] = nv
                                    if (state.formError.isNotEmpty()) state.formError = ""   // 一改就撤掉上次的报错
                                }
                                if (i != state.formFields.lastIndex) Spacer(Modifier.height(SheetDp(8f)))
                            }
                            if (state.formError.isNotEmpty()) {
                                Text(
                                    state.formError, color = Color(Ui.ERR),
                                    fontSize = SheetSp(Ui.S_FOOT), lineHeight = SheetLineSp(),
                                    modifier = Modifier.padding(start = SheetDp(4f), top = SheetDp(8f)),
                                )
                            }
                            Spacer(Modifier.height(SheetDp(10f)))
                            // ⚠️【V3.21 修 · 按压反馈双影】`glassTap` 必须排在 `clip/background` **之前**。
                            //   Compose 修饰符"外侧先画"：glassTap 的 `graphicsLayer(scale)` + 压暗层
                            //   只包住它**右侧**的部分；写在 background 之后 → 缩小/变暗的只是内容层，
                            //   而**原尺寸的背景留在底下**，看起来就是"按下去出现一块灰色缩小的，原有的一直在下面"
                            //   （用户 2026-10-09 实测报的正是这个；本文件下面每个可点件都按此规则）。
                            Box(
                                Modifier.fillMaxWidth()
                                    .height((48f * SheetK).dp.coerceAtLeast(SheetMinButton))
                                    .glassTap {
                                        val err = state.onSubmit?.invoke(values.toList())
                                        if (err.isNullOrEmpty()) state.hide() else state.formError = err
                                    }
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(Ui.BRAND_FILL).copy(alpha = 0.92f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(state.formSubmitLabel.ifEmpty { "保存" }, color = Color.White,
                                    fontSize = SheetSp(Ui.S_BODY), lineHeight = SheetLineSp(),
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                        // 「确认」形态：一个主按钮（危险操作用红）
                        if (state.confirmLabel.isNotEmpty()) {
                            Box(
                                Modifier.fillMaxWidth()
                                    .height((48f * SheetK).dp.coerceAtLeast(SheetMinButton))
                                    .glassTap { onPick(0) }
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (state.danger) Color(0xFFE5484D).copy(alpha = 0.92f)
                                        else Color(Ui.BRAND_FILL).copy(alpha = 0.92f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(state.confirmLabel, color = Color.White, fontSize = SheetSp(Ui.S_BODY), lineHeight = SheetLineSp(),
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(SheetDp(10f)))
                        Box(
                            Modifier.fillMaxWidth()
                                .height((46f * SheetK).dp.coerceAtLeast(SheetMinButton))
                                .glassTap { onDismiss() }
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(Ui.CHIP_BG)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (state.listActive) state.listCloseLabel else "取消",
                                color = Color(Ui.INK_SUB), fontSize = SheetSp(Ui.S_BODY), lineHeight = SheetLineSp(),
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(item: GlassSheetItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(SheetDp(20f))
    Row(
        Modifier.fillMaxWidth()
            .glassTap(corner = SheetDp(20f)) { onClick() }   // ← 必须在 clip/background 之前（见上「按压反馈双影」）
            .clip(shape)
            .background(Color(Ui.CHIP_BG))
            .padding(horizontal = SheetDp(13f), vertical = SheetDp(12f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.icon != 0) {
            Box(
                Modifier.size(SheetDp(34f)).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(
                        Color(0xFF9A6BFF).copy(alpha = 0.9f),
                        Color(0xFF3A8CFF).copy(alpha = 0.9f)))),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(item.icon), null, tint = Color.White, modifier = Modifier.size(SheetDp(19f))) }
            Spacer(Modifier.width(SheetDp(12f)))
        }
        Column(Modifier.weight(1f)) {
            Text(item.label, color = Color(Ui.INK), fontSize = SheetSp(Ui.S_BODY), lineHeight = SheetLineSp(), fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.desc.isNotEmpty()) {
                Text(item.desc, color = Color(Ui.INK_SUB), fontSize = SheetSp(Ui.S_CAP1), lineHeight = SheetLineSp(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(painterResource(com.dsh.mobile.R.drawable.ic_chevron_left), null,
            tint = Color(Ui.INK_FAINT), modifier = Modifier.size(SheetDp(16f)))
    }
}

/**
 * 【V3.24 列表形态的一行】主文案（当前项加粗）+ 副文案 + 当前项打勾。
 *
 * 与 [SheetRow] 同一套语言（半透明卡 `Ui.CHIP_BG` + 同系数尺寸与行高）；选中项底色略深一点作区分。
 */
@Composable
private fun SheetOptionRow(o: GlassSheetOption, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(SheetDp(20f))
    Row(
        Modifier.fillMaxWidth()
            .glassTap(corner = SheetDp(20f), onClick = onClick, onLongClick = onLong)   // ← 必须在 clip/background 之前
            .clip(shape)
            // ⚠️ [V3.24 修 · 用户报「选择后怎么变成实的了」] 选中行**必须是半透明叠加色**：
            //   原来错用 `Ui.BRAND_SOFT`（`0xFFE9F2FF` **不透明**）⇒ 当前项变成一块实心方块，
            //   与旁边能透出背景的行格格不入（`evidence/v3260/31_rows_zoom_before.png` 可见）。
            //   改用项目既有的**半透明**选中态 `Ui.SELECT_BG`（浅色黑 6% / 深色白 14%）
            //   —— 与旧 `ui/ModelSheet` 的当前项**同一套语言**。
            .background(if (o.selected) Color(Ui.SELECT_BG) else Color(Ui.CHIP_BG))
            .padding(horizontal = SheetDp(13f), vertical = SheetDp(11f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(o.label, color = Color(Ui.INK), fontSize = SheetSp(Ui.S_BODY), lineHeight = SheetLineSp(),
                fontWeight = if (o.selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (o.sub.isNotEmpty()) {
                Text(o.sub, color = Color(Ui.INK_SUB), fontSize = SheetSp(Ui.S_CAP1), lineHeight = SheetLineSp(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (o.trailing.isNotEmpty()) {
            Text(o.trailing, color = Color(Ui.INK), fontSize = SheetSp(Ui.S_BODY), lineHeight = SheetLineSp(),
                fontWeight = FontWeight.Bold, maxLines = 1,
                modifier = Modifier.padding(start = SheetDp(8f)))
        }
        if (o.selected) {
            Icon(painterResource(com.dsh.mobile.R.drawable.ic_check), null,
                tint = Color(Ui.BRAND), modifier = Modifier.size(SheetDp(18f)))
        }
    }
}

/**
 * 【V3.21 表单字段】玻璃观感输入框：字段名（小字）+ 输入行 + 占位提示。
 *
 * 与 [SheetRow] 同一套语言（半透明卡 `Ui.CHIP_BG` + 圆角 + 同系数尺寸），**不是**真模糊玻璃 ——
 * 面板内拿不到取样（见文件头 §"为什么弹窗必须画在同一棵 Compose 树里"）。
 */
@Composable
private fun SheetFieldCard(f: GlassSheetField, value: String, onValueChange: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SheetDp(20f)))
            .background(Color(Ui.CHIP_BG))
            .padding(horizontal = SheetDp(13f), vertical = SheetDp(10f)),
    ) {
        Text(f.label, color = Color(Ui.INK_SUB), fontSize = SheetSp(Ui.S_CAP1), lineHeight = SheetLineSp())
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color(Ui.INK),
                fontSize = SheetSp(Ui.S_BODY),
                lineHeight = SheetLineSp(),
            ),
            cursorBrush = SolidColor(Color(Ui.BRAND_FILL)),
            visualTransformation = if (f.password) PasswordVisualTransformation()
            else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(f.hint, color = Color(Ui.INK_FAINT),
                            fontSize = SheetSp(Ui.S_BODY), lineHeight = SheetLineSp())
                    }
                    inner()
                }
            },
        )
    }
}
