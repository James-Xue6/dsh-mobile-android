package com.dsh.mobile.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight   // [V3.49] 抽屉玻璃卡"高度随内容"
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex   // [V3.43] 树内浮层要压在列表之上
import com.dsh.mobile.R
import com.dsh.mobile.model.SessionInfo
import com.dsh.mobile.model.WorkspaceGroup
import com.dsh.mobile.ui.Ui
import com.kyant.backdrop.backdrops.layerBackdrop          // [V3.48] 抽屉里给"列表内容"记一层取样
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 【抽屉 / 会话选择列表 · Compose 版（V3.7）】
 *
 * ## 与正式版的关系
 * 抽屉的**外壳**（遮罩、左侧面板、滑入滑出、宽度、安全区）仍由 `ui/DrawerHost` 负责 ——
 * 那一层本来就是真玻璃 + 动画，工作正常；本轮只把**面板里的内容**从旧的 `SessionListView`
 * 换成这一屏：分组头（[WorkspaceGroup]）+ 会话卡（[SessionInfo]）。
 *
 * ## 数据面（和对话页同一套路：直接吃真对象，不做映射层）
 * `MainActivity.buildRows()` 产出的就是 `List<Object>`：`WorkspaceGroup` 分组头 + 摊平后的
 * `SessionInfo`（子会话按 `childDepth` 缩进）。本屏 `when (row)` 直接渲染，宿主换一份新 list 即重组。
 *
 * ## 为什么这里的卡片是"半透明卡"而不是真玻璃
 * 抽屉面板本身就是玻璃（`DrawerHost.applyDrawerBg`，Java Drawable 画的、Compose 取样不到），
 * 面板内部再放真玻璃会取不到样（渲染成一层白纱）。所以沿用 V3.2 定的结构规则：
 * **背景/外壳是玻璃，层内内容用半透明卡**（正式版会话行也是 `Ui.CardBg` 半透明卡）。
 */
class GlassSessionState {
    /** 分组头 / 会话行（真对象：WorkspaceGroup / SessionInfo）。 */
    var rows by mutableStateOf<List<Any>>(emptyList())
    var currentId by mutableStateOf("")
    var status by mutableStateOf("")
    var title by mutableStateOf("对话")
    /**
     * [V3.53 · 用户「抽屉『三个点』加磨砂 —— 你把这个界面整体配置**完全按照项目、模型这些按钮
     * 点开的页面状态**！」] ⇒ 抽屉这两处菜单**不再自绘**，改喂进**对话页同一个** `GlassSheetState`，
     * 交给**同一个** `GlassSheetOverlay` 渲染（= 模型/项目/权限/用量面板那套组件与参数）。
     */
    val sheet = GlassSheetState()
    var themeRev by mutableStateOf(0)
    fun bumpTheme() { themeRev++ }
}

/** 抽屉里的动作 —— 全部转发给宿主（正式 App 里就是 `MainActivity` 的 `SessionListView.Host`）。 */
interface GlassSessionHost {
    fun openSession(s: SessionInfo)
    fun newChat()
    fun settings()
    fun devices()
    fun refresh()
    fun rename(s: SessionInfo)
    fun archive(s: SessionInfo)
    fun toggleChildren(s: SessionInfo)
    /**
     * [工作区折叠] 点工作区分组表头：展开/折叠该工作区名下的会话行。
     *
     * 与 [toggleChildren] 是**两层不同的折叠**（这一层按工作区、那一层按父会话），
     * 账本也各存各的 —— 展开工作区不该顺手把某个父会话的子会话也弹开，反之亦然。
     */
    fun toggleWorkspace(g: WorkspaceGroup)
    fun workspaceMenu(g: WorkspaceGroup)
    /** [V3.31] 会话卡的 ⋯ 菜单（宿主用 Java 对话框实现 ⇒ 不开 Compose 新窗口）。 */
    fun sessionMenu(s: SessionInfo)
    /** [V3.27] 「在此工作区新建任务」（原 `ui/WorkspaceSheet` 的动作，搬进玻璃观感浮层后由屏直接调）。 */
    fun newTaskHere(g: WorkspaceGroup)
}

/**
 * [V3.103 · 用户「我让你**抽屉内容的弹窗在抽屉之上**」+ 认可方案 A]
 *
 * 抽屉自己的取样层（见 `GlassSessionListScreen` 里的 `backdrop`）放在这里，
 * 供**独立的全屏弹窗宿主** [createGlassSheetHost] 复用 ⇒ 弹窗的玻璃能采到抽屉内容。
 */
object GlassDrawerBackdrop {
    /** [V3.117] 同 `GlassChatBackdrop`：必须是**可观察状态**，否则宿主只会在组合那一刻读一次 ⇒ 材质时好时坏。 */
    var layer: com.kyant.backdrop.Backdrop? by androidx.compose.runtime.mutableStateOf(null)
}

/**
 * **独立的全屏弹窗宿主**（由 `MainActivity` 加进 `DrawerHost` 的**最后一个子 View**）。
 *
 * <p>为什么要这么做：抽屉面板（`DrawerHost` 里那个 View）本身就压在抽屉的 ComposeView 之上
 * ⇒ "在抽屉自己的 ComposeView 里弹 sheet"永远**被困在面板里** ✗；
 * 而这个宿主是 `DrawerHost` 的**最后一个子 View** ⇒ **天然在面板之上**、且是**全屏**的
 * ⇒ 弹窗出现在**屏幕正中**、又在**抽屉之上** ✓，同时**完全不动聊天层**
 * （不会再出现 V3.102 那种把聊天层提到抽屉之上、结果整屏模糊的改坏）。
 *
 * <p>`GlassSheetOverlay` 在 `state.visible == false` 时**直接返回**（`GlassSheet.kt:304`）
 * ⇒ 空闲时这个宿主不画任何东西、也不拦触摸 ✓（可以安全常驻）。
 */
fun createGlassSheetHost(
    ctx: android.content.Context,
    state: GlassSheetState,
    themeRev: () -> Int,
): android.view.View = androidx.compose.ui.platform.ComposeView(ctx).apply {
    setContent {
        @Suppress("UNUSED_EXPRESSION") themeRev()
        GlassAppTheme(themeRev()) {
            val fallback = rememberLayerBackdrop()
            // [V3.113 · 用户「粘贴配对串点开跑到对话界面、成透明的了；首页修改名称也不弹出跑到对话位置了」]
            //   ⚠️ 症状根因：这个宿主是**全屏且常驻**的，如果聊天页当时没被组合（用户停在设置/设备页），
            //   它的取样层就是**空的/过期的** ⇒ 渲染出**透明**的弹窗 ✗。
            //   ⇒ 两条约束：
            //     ① **只在聊天页无法自己渲染时**才接管（`GlassChatBackdrop.layer == null`）⇒ 避免同一个状态被渲染两次；
            //     ② 取样层**必须是有效的那一层**（这里优先抽屉自己那层），拿不到就**什么都不画** ——
            //        宁可让调用方回退旧弹窗，也不要画一个透明的玻璃 ✗。
            // [V3.116 · 用户「侧抽屉 → 会话卡 ⋯ 玻璃卡片**未修复玻璃质感**」]
            //   ⚠️ 取层优先级**回到 V3.104**（那一版用户明确确认"质感调过去了"）：**优先聊天页主取样层**。
            //   理由：这个宿主是**整屏**浮层，它背后就是"聊天页 + 抽屉"⇒ 采聊天页那层才是正确的玻璃观感；
            //   `GlassDrawerBackdrop`（抽屉自己那层）只是**兜底**，材质明显更弱。
            //   V3.113 真正要修的是"**过期层**"（聊天页切走后层还在、采样出来是空的 ⇒ 透明 ✗）——
            //   那个已经由 `GlassChatScreen` 的 `onDispose` 清空修掉了 ✓，**优先级不该跟着改** ✗。
            //   仍然保留"**拿不到任何有效层就什么都不画**"（宁可让调用方回退旧弹窗，也不画透明玻璃）。
            // [V3.130 · 真机诊断] 抽屉里的会话 ⋯→「重命名」把表单写进了**抽屉态**（route=drawer ✓），
            //   但宿主**没有重组合去画它** ✗。原写法是 `if (layer != null) { GlassSheetOverlay(...) }` ——
            //   一旦取层表达式在某一刻为 null（聊天页刚切走/刚回来），整个 overlay **就被移出组合** ✗，
            //   之后再写状态也**不会**被它感知 ⇒ 弹窗"写了但没人画"。
            //   ⇒ 改成**永远组合** overlay，取层用一个稳定的兜底层（`rememberLayerBackdrop`）：
            //     面板一定画得出来（最差是弱材质），但**绝不会漏画** ✓
            val layer = GlassChatBackdrop.layer ?: GlassDrawerBackdrop.layer ?: fallback
            GlassSheetOverlay(
                    backdrop = layer,
                    state = state,
                    // [V3.126 · 真机诊断定案] 必须**先收起、再回调**：回调里可能又打开新面板
                    //   （抽屉 ⋯→「重命名」⇒ 玻璃表单）⇒ 顺序反了会把刚打开的新面板立刻关掉 ⇒ "点了没反应" ✗
                    onPick = { idx ->
                        state.hide()
                        state.onListPick?.invoke(idx)
                    },
                    onDismiss = { state.hide() },
                    settleFrames = 2,   // [V3.118] 等 2 帧：先让取样层记录完再画面板，消除"首次打开材质不对、关掉再开才对"
                )
        }
    }
}

@Composable
fun GlassSessionListScreen(
    state: GlassSessionState,
    host: GlassSessionHost,
    applyInsets: Boolean = true,
) {
    @Suppress("UNUSED_EXPRESSION") state.themeRev

    val density = androidx.compose.ui.platform.LocalDensity.current
    val topInsetDp = if (applyInsets) topSafeDp(density) else 0.dp
    val bottomInsetDp = if (applyInsets) with(density) {
        WindowInsets.navigationBars.getBottom(density).toDp()
    } else 0.dp

    val ink = Color(Ui.INK)
    val inkSub = Color(Ui.INK_SUB)
    val inkFaint = Color(Ui.INK_FAINT)
    val brand = Color(Ui.BRAND_FILL)

    // [V3.43] 屏根改成 Box：两处 ⋯ 的菜单要作为**树内浮层**渲染（屏根 + zIndex），不能用 Dialog
    // [V3.48] 菜单要做成**真玻璃** ⇒ 先给"列表内容"记一层取样（层里只有观感版卡，不会自取样）——
    //   与对话页同一条规则：**取样层 = 内容，玻璃件留在层外**。
    val backdrop = rememberLayerBackdrop()
    // [V3.103 · 方案 A] 把这个取样层暴露给**独立的全屏弹窗宿主**（见文件末尾 `createGlassSheetHost`）
    androidx.compose.runtime.SideEffect { GlassDrawerBackdrop.layer = backdrop }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
        Column(Modifier.fillMaxSize().padding(top = topInsetDp)) {
            // ---- 头部：大标题 + 刷新/设备/设置三个圆钮（照正式版抽屉头部）
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(state.title, color = ink, fontSize = Ui.S_LARGE.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                RoundIconButton(R.drawable.ic_refresh, inkSub) { host.refresh() }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(R.drawable.ic_monitor, inkSub) { host.devices() }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(R.drawable.ic_sliders, inkSub) { host.settings() }
            }
            if (state.status.isNotEmpty()) {
                Text(state.status, color = inkFaint, fontSize = Ui.S_CAP1.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp))
            }

            // ---- 会话列表：分组头 + 会话卡
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, bottom = bottomInsetDp + 96.dp),
            ) {
                itemsIndexed(state.rows) { _, row ->
                    when (row) {
                        is WorkspaceGroup -> WorkspaceHeader(row, inkSub, inkFaint, host, state)
                        is SessionInfo ->
                            SessionCard(row, row.id == state.currentId, ink, inkSub, inkFaint, host, state)
                        else -> Unit
                    }
                }
            }
        }
        }

        // ================= 树内浮层：两处 ⋯ 的**玻璃**菜单 =================
        // [V3.43 · 补完 V3.32 §2.1「抽屉两处 ⋯ 的玻璃观感」]
        //   ⚠️ 必须渲染在**同一棵 Compose 树**里（屏根 Box + `zIndex`）。**绝不能用 Compose `Dialog`**：
        //   Dialog 会开**新窗口**，而 `MainActivity` 是纯 `android.app.Activity` ⇒ 新窗口里的 ComposeView
        //   拿不到 `ViewTreeLifecycleOwner` ⇒ 真机 dropbox 里 **10 次** `IllegalStateException`（V3.31 §1.3）。
        //   抽屉**面板外壳**本身是真玻璃（Java `DrawerHost.applyDrawerBg`，Compose 取样不到它）⇒
        // [V3.108 · 发布前清理] 这里原本还有一段"抽屉自己弹 sheet"的死代码
        //   （`state.wsFor / state.menuFor` + 本面板内的 `GlassSheetOverlay`）：
        //   自 V3.99 起，两处 ⋯ 都改为**回调宿主**（`host.sessionMenu / host.workspaceMenu`），
        //   由 `MainActivity` 在 `DrawerHost` 的**最后一个子 View**（独立全屏弹窗宿主）上渲染，
        //   `wsFor / menuFor` 再也不会被赋值 ⇒ 那一段永远不会执行 ✗。
        //   死代码已删除（含状态字段与其注释）。弹窗统一走 `GlassUI_SPEC_DIALOG.md` 那一条路。
    }
}

@Composable
private fun WorkspaceHeader(
    g: WorkspaceGroup, inkSub: Color, inkFaint: Color,
    host: GlassSessionHost, state: GlassSessionState,
) {
    // [V3.43 · 补完 V3.32 §2.1] 这里原来弹的是**宿主 Java 菜单**（V3.31 为避 Compose Dialog 闪退回退的）。
    //   现在改成：只把"要开哪个菜单"记进屏状态（`state.wsFor`），由**屏根**渲染树内浮层
    //   ⇒ 既有玻璃观感，又不开新窗口（不会触发 `ViewTreeLifecycleOwner` 那条真机闪退）。
    //
    // [工作区折叠 · 用户「我要的父级是工作区。工作区可以展开查看工作区一下的子对话」]
    //   整行 = 折叠开关（`toggleWorkspace`），左侧给 ▸/▾ 指示当前是收着还是开着。
    //   ⚠️ 右侧「⋯」是**嵌套**的可点元素：Compose 内层 `clickable` 命中区在它自己的边界内
    //   ⇒ 点 ⋯ 只开菜单、不会连带把这一组折叠掉（两件事不会互相触发）。
    //   收起时右侧计数照常显示 —— 收起来的组也要能一眼看出里面有多少条。
    Row(
        Modifier.fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
            .glassTap(corner = 14.dp) { host.toggleWorkspace(g) }
            .padding(start = 6.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (g.expanded) "▾" else "▸", color = inkFaint, fontSize = Ui.S_CAP1.sp,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            g.label.ifBlank { "会话" }, color = inkSub, fontSize = Ui.S_CAP1.sp,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("${g.count}", color = inkFaint, fontSize = Ui.S_CAP1.sp)
        Spacer(Modifier.width(6.dp))
        // [V3.99] 同上：工作区 ⋯ 也回调宿主（不再在本面板里弹）
        SmallPill("⋯", inkFaint) { host.workspaceMenu(g) }
    }
}

@Composable
private fun SessionCard(
    s: SessionInfo,
    current: Boolean,
    ink: Color,
    inkSub: Color,
    inkFaint: Color,
    host: GlassSessionHost,
    state: GlassSessionState,
) {
    // 子会话按 childDepth 缩进；当前会话用品牌淡底描边
    val bg = if (current) Color(Ui.BRAND_SOFT)
    else if (Ui.isDark()) Color(0xFF14121C).copy(alpha = 0.30f) else Color.White.copy(alpha = 0.26f)
    val shape = RoundedCornerShape(18.dp)
    // [V3.31] 本地 menu 状态已废除（改走宿主 Java 菜单，避免 Compose Dialog 新窗口闪退）

    Box(
        Modifier.fillMaxWidth()
            // [V3.26 用户「间距扩大一倍，把阴影空间留出来」] 卡间距 2dp → **8dp**：
            //   卡片阴影是"只画在形状之外"的模糊投影（外扩 7dp + 下移 ~3dp）⇒ 间距小于 ~10dp 时
            //   影子会被**下一张卡盖住**（看起来像没阴影）。8dp 是"看得见影、又不散"的实践值。
            .padding(start = (s.childDepth * 14).dp, top = 8.dp, bottom = 8.dp)
            // [V3.26 · 用户「加上」（卡片阴影）] 卡片走统一模板的**卡片级**（18dp，= Java 侧 Ui.CARD_SHADOW_PAD）
            // [V3.26 用户第 6 轮] `glassTap` 排到阴影之前 ⇒ 按住时阴影跟着一起缩放（不再"缩小+两层"）
            .glassTap(corner = 18.dp) { host.openSession(s) }
            .glassCardShadow(shape)
            .clip(shape).background(bg).border(0.5.dp, Color(Ui.HAIRLINE), shape),
    ) {
        // [V3.26 用户「对话胶囊有点高，整体再压扁 10%」] 行内上下内边距 11dp → **8dp**
        // [V3.26 用户「再压扁一点」] 行内上下内边距 8dp → **6dp**
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (s.title.isBlank()) "（未命名会话）" else s.title,
                        color = ink, fontSize = Ui.S_BODY.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (s.pending > 0) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(Color(Ui.BRAND_FILL))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) { Text("待处理", color = Color.White, fontSize = Ui.S_CAP1.sp) }
                    }
                    if (s.running) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp))
                            .background(Color(Ui.BADGE_OK_FG)))
                    }
                    if (s.childCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        SmallPill(if (s.expanded) "收起 ${s.childCount}" else "子会话 ${s.childCount}", inkFaint) {
                            host.toggleChildren(s)
                        }
                    }
                }
                Text(
                    subLabel(s), color = inkFaint, fontSize = Ui.S_CAP1.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            // [V3.99 · 用户「会话操作这个弹窗完全没改」的真根因]
            //   抽屉的 ⋯ 原来只是 `state.menuFor = s` ⇒ 在**本面板的 Compose 树**里弹 sheet
            //   ⇒ 永远居中在抽屉面板里、宿主的全屏路由**根本走不到** ✗。
            //   按 `GlassUI_SPEC_DIALOG.md` §3：抽屉里的菜单**一律回调宿主**，由 Activity 级全屏宿主渲染。
            SmallPill("⋯", inkFaint) { host.sessionMenu(s) }
        }
    }

    // [V3.43] 会话卡 ⋯ 也改走**树内浮层**（屏根渲染 `state.menuFor`）；同样是"记录状态、不开新窗口"
}

/**
 * 抽屉里的菜单浮层。
 *
 * [V3.27 起] 抽屉内 Compose 取不到外壳玻璃 ⇒ 当时用了"玻璃观感"（半透明卡 + 阴影 + 发丝边）。
 * [V3.48 · 用户「**材质应跟模型/项目这些打开一种材质**」] 改成**真玻璃**：
 * 抽屉里现在有了自己的**内容取样层**（`GlassSessionListScreen` 里的 `backdrop`），
 * 于是这里可以直接用 `GlassSurface`（= 对话页的模型/权限/项目面板同一套 `RefractionWindow`）。
 */
@Composable
private fun SheetLikeOverlay(
    backdrop: com.kyant.backdrop.Backdrop,
    title: String,
    subtitle: String,
    items: List<Pair<String, () -> Unit>>,
    dangerIndex: Int,
    ink: Color,
    inkSub: Color,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    // [V3.49 · 用户「抽屉展示的这个玻璃空间**太透明，看不清楚了**」]
    //   ⇒ 照抄对话页「模型 / 项目」面板的材质（那套是**看得清**的，见 `GlassSheet.kt:342-366`）：
    //   磨砂 **10dp** + 卡片自身再垫一层**同色半透明底**（浅 `White 38%` / 深 `#14121C 36%`）；
    //   并沿用它的 `wrapContentHeight(unbounded = true)` —— 否则 `RefractionWindow` 内层
    //   `fillMaxSize()` 会**撑满可用高度**（整屏高的玻璃柱）。
    Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        RefractionWindow(
            backdrop = backdrop, radius = 28.dp,
            // [V3.51 · 用户「侧抽屉的三个点里面的还是**有点透，增加一点磨砂**」]
            //   磨砂 10dp → **16dp**（就是用户要的"加磨砂"），垫底的同色半透明底
            //   浅 38% → **46%** / 深 36% → **44%**（两个数值，符合 SOP 的"一次 ≤2 个质感数值"）。
            blurRadius = 16.dp,
            modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true)
                .glassCardShadow(shape),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.matchParentSize().clip(shape).background(
                        if (Ui.isDark()) Color(0xFF14121C).copy(alpha = 0.44f)
                        else Color.White.copy(alpha = 0.46f)),
                )
                Column(Modifier.padding(16.dp)) {
        Text(title, color = ink, fontSize = Ui.S_TITLE3.sp, fontWeight = FontWeight.Bold)
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = inkSub, fontSize = Ui.S_CAP1.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        items.forEachIndexed { i, item ->
            Box(
                Modifier.fillMaxWidth()
                    // [V3.33 · 待办 E「按压双影其余位置」] `glassTap` 提到 `clip/background` **之前**：
                    //   Compose 修饰符"外侧先画"，写在 background 之后 ⇒ 按下时缩小+压暗的只有内容层，
                    //   原尺寸的底留在下面 ⇒ 用户看到"两层"。规则出处见 `GlassSheet.kt` 的 V3.21 注释。
                    .glassTap(corner = 14.dp) { onClose(); item.second() }
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (Ui.isDark()) Color.White.copy(alpha = 0.06f) else Color(Ui.SELECT_BG))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(item.first, color = if (i == dangerIndex) Color(Ui.ERR) else ink,
                    fontSize = Ui.S_BODY.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(
            "关闭", color = inkSub, fontSize = Ui.S_FOOT.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            // [V3.33 · 待办 E] 同上层：`glassTap` 必须在 `clip/background` 之前（否则按下"双影"）
            modifier = Modifier.fillMaxWidth()
                .glassTap(corner = 14.dp) { onClose() }
                .clip(RoundedCornerShape(14.dp))
                .background(Color(Ui.FIELD_BG))
                .padding(vertical = 11.dp),
        )
                }
            }
        }
    }
}

/** 会话行的副标题：跑动/待办优先，其次工作目录名，最后时间。 */
private fun subLabel(s: SessionInfo): String {
    val parts = ArrayList<String>()
    if (s.running) parts.add("运行中")
    s.cwd?.trim()?.takeIf { it.isNotEmpty() }?.let {
        parts.add(it.substringAfterLast('/').substringAfterLast('\\'))
    }
    if (s.updatedAt > 0L) parts.add(ago(s.updatedAt))
    return parts.joinToString(" · ")
}

private fun ago(ms: Long): String {
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000L -> "刚刚"
        d < 3_600_000L -> "${d / 60_000L} 分钟前"
        d < 86_400_000L -> "${d / 3_600_000L} 小时前"
        else -> "${d / 86_400_000L} 天前"
    }
}

/** 小圆玻璃感按钮（半透明底 + 图标）——面板内部不能放真玻璃，见类注释。 */
@Composable
private fun RoundIconButton(iconRes: Int, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp)
            // [V3.26 · 用户报②] 阴影走**统一模板** `glassButtonShadow`（紫调 Ui.GLASS_SHADOW、外扩 6dp），
            //   排在 clip 之前。原来手写的 `.shadow(3.dp, clip=false)` 用的是 Compose 默认**黑**投影
            //   ⇒ 在浅色玻璃底上就是一圈脏黑边（用户报①）。
            // [V3.26 用户第 6 轮「按下去又出现缩小且两层」] **`glassTap` 必须排在阴影之前**：
            //   `glassTap` 里是 `graphicsLayer{scale}`，排在它**里面**（后面）的东西才会跟着缩放；
            //   原来阴影排在它外面 ⇒ 按下时元素缩小、阴影留在原尺寸 ⇒ 看起来"缩小 + 两层"。
            .glassTap { onClick() }
            .glassButtonShadow(RoundedCornerShape(999.dp))
            .clip(RoundedCornerShape(999.dp))
            // [V3.16 用户报「上方三个按钮未玻璃化」] 面板内拿不到真模糊（Compose 取样不到外壳的
            // 旧引擎玻璃，见接手须知 §1.2），只能强化「玻璃观感」三件套。
            // [V3.26 用户三轮] 白度定 **55% → 14%**：上一轮是 55%→26%，用户报「这块现在玻璃质感
            //   不强了」—— 原因是**下端太厚、不透**。上端保持 55% 受光、下端收到 14% ⇒ 渐变更陡、更像玻璃。
            .background(Brush.linearGradient(listOf(
                Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.14f))))
            .border(0.5.dp, Color(Ui.HAIRLINE), RoundedCornerShape(999.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(999.dp))
            .background(Brush.verticalGradient(listOf(
                Color.White.copy(alpha = 0.18f), Color.Transparent))))
        Icon(painterResource(iconRes), null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

/** 极小的胶囊文字按钮（⋯ / 子会话 N）。 */
@Composable
private fun SmallPill(label: String, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            // [V3.33 · 待办 E] `glassTap` 必须在 `clip/background` 之前（外侧先画 ⇒ 按下整块一起缩+暗）
            .glassTap { onClick() }
            .clip(RoundedCornerShape(999.dp)).background(Color(Ui.CHIP_BG))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) { Text(label, color = fg, fontSize = Ui.S_CAP1.sp) }
}
