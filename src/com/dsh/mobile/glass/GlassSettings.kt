package com.dsh.mobile.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.R
import com.dsh.mobile.ui.Ui
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 【设置页 · Compose 版（V3.8）】把正式版 `ui/SettingsView` 的**全部真实项**搬过来。
 *
 * ## 项清单（逐条对齐 SettingsView）
 * | 分区 | 项 | 真值来源 / 落点 |
 * |---|---|---|
 * | 连接 | 内网地址 / 公网地址 / 令牌 / 设备名 | `Store.lanUrl/wanUrl/token/deviceName`（可编辑） |
 * | 连接 | 连接方式三分段（自动/只用内网/只用公网） | `Store.netMode` → `RoutePolicy` |
 * | 连接 | 保存并连接 / 扫码配对 / 粘贴配对 / 断开 | `Host.onConnect/onScanQr/onPastePairing/onDisconnect` |
 * | 外观 | 深色模式三分段（跟随系统/浅色/深色） | `Store.themeMode` + `Ui.applyTheme` |
 * | 外观 | 配色 6 档（极光…素雅） | `Store.ambientPalette` + `Ui.setAmbientPalette` |
 * | 外观 | 玻璃磨砂度 4 档 | `Store.glassLevel` + `Ui.setGlassLevel` |
 * | 行为 | 显示模式（完整 / 简洁） | `Store.displayMode` |
 * | 行为 | 允许截屏开关 | `Store.allowScreenshot` |
 * | 行为 | 允许自签名证书开关 | `Store.insecureTls` |
 * | 关于 | 关于文案 / 检查更新 / 反馈 / 诊断 / 状态行 | `Host.onCheckUpdate/onOpenFeedback` + 宿主推的文本 |
 *
 * ## 结构（沿用 V3.2 定下的规则）
 * 标题/分区卡是**玻璃件**（层外）；分区内部的行用**半透明卡/胶囊**；
 * 连接字段用 `BasicTextField`（自绘，不吃 Material 主题）。
 */
class GlassSettingsState {
    // ---- 连接
    var lan by mutableStateOf("")
    var wan by mutableStateOf("")
    var token by mutableStateOf("")
    var deviceName by mutableStateOf("")
    /** RoutePolicy.AUTO / LAN / WAN */
    var netMode by mutableStateOf("auto")
    // ---- 外观
    var themeMode by mutableStateOf("system")
    /** [V3.81] 当前字号比例（0.9 小 / 1.0 中 / 1.12 大），只用于三段的高亮。 */
    var fontScale by mutableStateOf(1f)
    var palette by mutableStateOf(0)
    var glassLevel by mutableStateOf(1)
    /** [V3.27 · 用户] 玻璃模式：`liquid`（液态，带折射）/ `frost`（磨砂，无折射、省资源）。[V3.52] **默认改液态**。 */
    var glassMode by mutableStateOf("liquid")
    // ---- 行为
    // 初值 = `Store.displayMode()` 的新默认（简洁 ⇒ 思考链默认收起）；真机由宿主灌入。
    var displayMode by mutableStateOf("compact")
    var allowScreenshot by mutableStateOf(true)
    var insecureTls by mutableStateOf(false)
    // ---- 关于 / 状态
    var about by mutableStateOf("")
    var updateHint by mutableStateOf("")
    var feedbackHint by mutableStateOf("")
    var diagnostics by mutableStateOf("")
    var status by mutableStateOf("")
    var statusError by mutableStateOf(false)
    /** 宿主能力：没有宿主（独立切片）时，连接类动作只能提示。 */
    var hasHost by mutableStateOf(false)

    /**
     * [V3.115 · 用户硬规则「**每个界面的玻璃弹窗都应该在对应界面的位置最上层**」]
     *
     * 设置页**自己那一套**弹窗状态与宿主（与设备页 `GlassDeviceState.sheet`、对话页、抽屉同构）。
     * 以前设置页没有自己的宿主 ⇒ 「粘贴配对串」被写到**对话页**的状态上，而用户停在设置页时
     * 对话页根本没被组合 ⇒ 弹窗**没人渲染**（表现成"透明"或"跑到对话窗口去了"）✗。
     * 现在设置页自己渲染自己那套 ⇒ 位置天然在**设置页内容之上** ✓、材质采**设置页的取样层** ✓。
     */
    val sheet = GlassSheetState()

    var themeRev by mutableStateOf(0)
    fun bumpTheme() { themeRev++ }

    /**
     * [V3.26 · 用户 #5「设置界面各板块可以折叠，目前显示过多」] 分区展开状态（key = 分区标题）。
     * **默认全部收起**（只留 4 个标题，一屏看完）；状态放在 state 里 ⇒
     * 换主题/配色导致的子树重建（`key(themeRev)`）**不会把它重置**。
     */
    val sectionOpen = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    // ---------------------------------------------------------------- 日志页（V3.26 · 用户 #6）
    /**
     * [V3.26 · 用户 #6]「关于里的诊断要**并进日志**，下方加一个**日志按钮**，收纳诊断 +
     * 后期故障及报错，便于维修检索」。
     *
     * 这里只存**行**（由宿主填充，见 [GlassSettingsHost.openLog]）；上限 500 行防内存膨胀。
     * 任何 `setStatus(..., error = true)` 也会自动追加进来 ⇒ 以后的故障/报错天然留痕。
     */
    var logLines by mutableStateOf(listOf<String>())
    var logOpen by mutableStateOf(false)
    /** 累积的故障/报错（宿主用 [logAdd] 追加）；打开日志页时由宿主把它拼进 [logLines]。 */
    private var errLines = listOf<String>()
    fun logAdd(line: String) { errLines = (errLines + line).takeLast(200) }
    fun errSnapshot(): List<String> = errLines
    fun logSet(lines: List<String>) { logLines = lines.takeLast(500) }
}

/** 设置页动作 —— 全部转发宿主（正式 App 里就是 `MainActivity` 的 `SettingsView.Host`）。 */
interface GlassSettingsHost {
    fun back()
    fun scanQr()
    fun connect(lan: String, wan: String, netMode: String, token: String, deviceName: String)
    fun pastePairing()
    fun disconnect()
    fun toggleInsecureTls(on: Boolean)
    fun checkUpdate()
    fun openFeedback()
    // 注意：这三个**故意不叫 setXxx** —— 包装类里 `setDisplayMode/setThemeMode/setPalette` 是
    // 「宿主推状态给界面」的方向，若同名会与「用户点了要落库」的动作合并 ⇒ 形成回环。
    fun pickDisplayMode(mode: String)
    fun toggleAllowScreenshot(on: Boolean)
    fun pickThemeMode(mode: String)

    /**
     * [V3.81 · 用户「设置里再增加一个**字体大小**，弄个**大 / 中 / 小**三个档就行，目前这个是标准档（中）」]
     *
     * [scale]：`0.9` = 小 / `1.0` = 中（标准）/ `1.12` = 大。宿主落库 + `Ui.applyFontScale` + 刷新界面。
     */
    fun pickFontScale(scale: Float)

    /**
     * [V3.89 · 用户「设置里面的字体调整之后**还是不切换**…那个设置选项**一直停留在标准那个档**」]
     *
     * 直接**从库里读**当前字号比例 —— 不走"宿主推送"那条路（推送时机依赖页面构建顺序，
     * 之前实测就是它导致高亮一直停在标准档）。设置页打开时读一次即可。
     */
    fun fontScale(): Float
    fun pickPalette(index: Int)
    fun setGlassLevel(level: Int)
    /** [V3.27] 玻璃模式二选一（液态/磨砂）。 */
    fun pickGlassMode(mode: String)

    /**
     * [V3.52 · 用户「配色增加一个选项：**自己上传图片**当底配色」]
     * 宿主拉起系统图片选择器；选中后自己落库 + 调 [GlassAmbientImage].uri ⇒ 全屏立刻换底。
     */
    fun pickAmbientImage()

    /**
     * [V3.26 · 用户 #6] 打开日志页。宿主在这里把要检索的内容**灌进** `state.logLines`
     * （诊断 + 事件轨迹 + 报错），然后 `state.logOpen = true`。
     */
    fun openLog()
}

@Composable
fun GlassSettingsScreen(
    state: GlassSettingsState,
    host: GlassSettingsHost,
    applyInsets: Boolean = true,
    /** [V3.26 用户 #5] 滚动状态由调用方（`key(themeRev)` **之外**）持有 ⇒ 换主题/配色不再弹回顶部。 */
    scrollState: ScrollState = rememberScrollState(),
) {
    @Suppress("UNUSED_EXPRESSION") state.themeRev

    // [V3.26 · 用户 #5] 分区折叠的读写助手（状态在 state 里 ⇒ 换主题重建也不丢）
    val isOpen: (String) -> Boolean = { t -> state.sectionOpen[t] ?: false }
    val toggle: (String) -> Unit = { t -> state.sectionOpen[t] = !(state.sectionOpen[t] ?: false) }

    val backdrop = rememberLayerBackdrop()
    // [V3.43 · 修用户批注①「这两个玻璃胶囊的玻璃感咋没了」]
    //   第二层取样：**背景 + 可滚动内容**，只给**层外**的悬浮件（顶栏胶囊 / 日志浮层）用。
    //   原来的 `backdrop` 层里只有 `AmbientBackground`（一块平滑渐变）⇒ 悬浮玻璃糊到的还是它自己，
    //   看不出模糊/折射，只剩体色 + 亮边 —— 而对话页的取样层是"背景 + LazyColumn"，所以那边顶栏有玻璃感。
    val pageBackdrop = rememberLayerBackdrop()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val topInsetDp = if (applyInsets) topSafeDp(density) else 0.dp
    val bottomInsetDp = if (applyInsets) with(density) {
        maxOf(
            WindowInsets.navigationBars.getBottom(density),
            WindowInsets.ime.getBottom(density),
        ).toDp()
    } else 0.dp

    val ink = Color(Ui.INK)
    val inkSub = Color(Ui.INK_SUB)
    val inkFaint = Color(Ui.INK_FAINT)
    val brand = Color(Ui.BRAND_FILL)

    Box(Modifier.fillMaxSize()) {
        // 取样层（只有背景）：玻璃件在层外取样
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            AmbientBackground(Modifier.fillMaxSize(), rev = state.themeRev)
        }

        // [V3.43] 内容层**也做成取样层**（`pageBackdrop` = 背景 + 内容）：
        //   悬浮玻璃要能"糊到从它底下滑过去的内容"才像玻璃（交接须知 §1 第 1 条）。
        //   ⚠️ 内容里的卡片本身是真玻璃（`SectionCard` / `FieldRow` / `GhostButton` …），
        //   放进**同一个**层会自取样（"一片平白"）⇒ 所以是**两层**：
        //     · `backdrop`（只有背景）—— 内容里的玻璃卡片照旧用它，卡片代码一行都不用改；
        //     · `pageBackdrop`（背景 + 内容）—— 只给**层外**的悬浮件（顶栏 / 日志浮层）用。
        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp,
                        top = topInsetDp + 74.dp, bottom = bottomInsetDp + 32.dp),
            ) {
                // ---------------- 连接
                SectionCard(backdrop, "连接", open = isOpen("连接"), onToggle = { toggle("连接") }) {
                    FieldRow(backdrop, "内网地址（同一 WiFi）", state.lan, ink, inkFaint) { state.lan = it }
                    Spacer(Modifier.height(8.dp))
                    FieldRow(backdrop, "公网地址（人在外面）", state.wan, ink, inkFaint) { state.wan = it }
                    Spacer(Modifier.height(8.dp))
                    FieldRow(backdrop, "令牌", state.token, ink, inkFaint) { state.token = it }
                    Spacer(Modifier.height(8.dp))
                    FieldRow(backdrop, "设备名", state.deviceName, ink, inkFaint) { state.deviceName = it }

                    Spacer(Modifier.height(14.dp))
                    Label("连接方式", ink)
                    Spacer(Modifier.height(8.dp))
                    Segmented(
                        backdrop,
                        listOf("自动（推荐）" to "auto", "只用内网" to "lan", "只用公网" to "wan"),
                        state.netMode, ink,
                    ) { mode -> state.netMode = mode }

                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(backdrop, "保存并连接") {
                        host.connect(state.lan.trim(), state.wan.trim(), state.netMode,
                            state.token.trim(), state.deviceName.trim())
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        GhostButton(backdrop, "扫码配对", Modifier.weight(1f), ink) { host.scanQr() }
                        Spacer(Modifier.width(8.dp))
                        GhostButton(backdrop, "粘贴配对", Modifier.weight(1f), ink) { host.pastePairing() }
                        Spacer(Modifier.width(8.dp))
                        GhostButton(backdrop, "断开", Modifier.weight(1f), Color(0xFFB71C1C)) { host.disconnect() }
                    }
                }

                // ---------------- 外观
                SectionCard(backdrop, "外观", open = isOpen("外观"), onToggle = { toggle("外观") }) {
                    Label("深色模式", ink)
                    Spacer(Modifier.height(8.dp))
                    Segmented(
                        backdrop,
                        listOf("跟随系统" to "system", "浅色" to "light", "深色" to "dark"),
                        state.themeMode, ink,
                    ) { mode -> state.themeMode = mode; host.pickThemeMode(mode) }

                    // [V3.81 · 用户「设置里再增加一个**字体大小**，弄个**大 / 中 / 小**三个档就行，
                    //   目前这个是标准档（中）」]
                    // [V3.83 · 用户「感觉现在还可以加个**最小**，再现在小的基础上再小一点」]
                    //   ⇒ 四档：最小 0.80 / 小 0.90 / 中（标准）1.00 / 大 1.12。
                    Spacer(Modifier.height(16.dp))
                    Label("字体大小", ink)
                    Spacer(Modifier.height(8.dp))
                    // [V3.89 · 用户「字号调整后那个选项**一直停在标准档**」]
                    //   **现读现用**：设置页打开时直接问宿主取库里存的值（不再依赖推送时机）。
                    androidx.compose.runtime.LaunchedEffect(Unit) { state.fontScale = host.fontScale() }
                    Segmented(
                        backdrop,
                        listOf("最小" to "0.8", "小" to "0.9", "中（标准）" to "1.0", "大" to "1.12"),
                        when {
                            state.fontScale < 0.85f -> "0.8"
                            state.fontScale < 0.95f -> "0.9"
                            state.fontScale > 1.05f -> "1.12"
                            else -> "1.0"
                        },
                        ink,
                    ) { v ->
                        val s = v.toFloatOrNull() ?: 1f
                        state.fontScale = s
                        host.pickFontScale(s)
                    }

                    Spacer(Modifier.height(16.dp))
                    Label("配色（玻璃背后的环境底）", ink)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Ui.PALETTES.forEachIndexed { i, p ->
                            val on = i == state.palette
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(42.dp)
                                        // [V3.33 · 待办 E] `glassTap` 必须在 `clip/background` 之前
                                        //   （外侧先画 ⇒ 按下时色块与白点一起缩、一起暗）
                                        .glassTap {
                                            state.palette = i
                                            host.pickPalette(i)
                                        }
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(
                                            listOf(Color(p.bg[0]), Color(p.bg[1])))),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (on) Box(Modifier.size(14.dp).clip(CircleShape)
                                        .background(Color.White))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    PaletteNames.getOrElse(i) { "" },
                                    color = if (on) ink else inkSub, fontSize = Ui.S_CAP1.sp,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                        // [V3.52 · 用户「配色适当增加一个选项**自己上传图片**，然后可以采用自己上传的图片当底配色」]
                        //   第 7 枚：**自定义底图**。点它 → 宿主拉起系统图片选择器 → 选中后立刻全屏生效。
                        run {
                            val customOn = GlassAmbientImage.uri.isNotEmpty()
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(42.dp)
                                        .glassTap { host.pickAmbientImage() }
                                        .clip(CircleShape)
                                        .background(if (customOn) Color(Ui.BRAND_FILL) else Color(Ui.SELECT_BG)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(painterResource(R.drawable.ic_plus), null,
                                        tint = if (customOn) Color.White else inkSub,
                                        modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (customOn) "自定义 ✓" else "上传图片",
                                    color = if (customOn) ink else inkSub, fontSize = Ui.S_CAP1.sp,
                                    fontWeight = if (customOn) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    // [V3.27 · 用户] 由「磨砂度 4 档」改为**二选一**：
                    //   液态玻璃（带折射，好看更费）/ 磨砂玻璃（**无折射、省资源**）。
                    //   用户原话「把现在液态玻璃的直接换成磨砂玻璃就行，主要减小硬件资源损耗」⇒ 默认磨砂。
                    Label("玻璃模式", ink)
                    Spacer(Modifier.height(8.dp))
                    Segmented(
                        backdrop,
                        listOf("液态玻璃" to "liquid", "磨砂玻璃" to "frost"),
                        state.glassMode, ink,
                    ) { m -> state.glassMode = m; host.pickGlassMode(m) }
                    Spacer(Modifier.height(6.dp))
                    Text("磨砂 = 不做折射（省电省 CPU）；液态 = 带折射（更\"液态\"但更费）",
                        color = inkFaint, fontSize = Ui.S_CAP1.sp)
                }

                // ---------------- 行为
                SectionCard(backdrop, "行为", open = isOpen("行为"), onToggle = { toggle("行为") }) {
                    Label("显示模式", ink)
                    Spacer(Modifier.height(8.dp))
                    Segmented(
                        backdrop,
                        listOf("完整" to "full", "简洁" to "compact"),
                        state.displayMode, ink,
                    ) { mode -> state.displayMode = mode; host.pickDisplayMode(mode) }

                    Spacer(Modifier.height(14.dp))
                    SwitchRow("允许截屏", "关掉后 App 内截图/录屏会变黑", state.allowScreenshot, ink, inkSub) { on ->
                        state.allowScreenshot = on
                        host.toggleAllowScreenshot(on)
                    }
                    Spacer(Modifier.height(10.dp))
                    SwitchRow("允许自签名证书", "自建网关用自签证书时打开", state.insecureTls, ink, inkSub) { on ->
                        state.insecureTls = on
                        host.toggleInsecureTls(on)
                    }
                }

                // ---------------- 关于
                SectionCard(backdrop, "关于", open = isOpen("关于"), onToggle = { toggle("关于") }) {
                    if (state.about.isNotEmpty()) {
                        Text(state.about, color = inkSub, fontSize = Ui.S_FOOT.sp)
                        Spacer(Modifier.height(10.dp))
                    }
                    if (state.updateHint.isNotEmpty()) {
                        Text(state.updateHint, color = inkFaint, fontSize = Ui.S_CAP1.sp)
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        GhostButton(backdrop, "检查更新", Modifier.weight(1f), ink) { host.checkUpdate() }
                        Spacer(Modifier.width(8.dp))
                        GhostButton(backdrop, "反馈" + state.feedbackHint, Modifier.weight(1f), ink) {
                            host.openFeedback()
                        }
                    }
                    if (state.diagnostics.isNotEmpty()) {
                        // [V3.26 · 用户 #6] 诊断**不再内联展示**（已并入日志页），这里只留一句指引，
                        //   免得「关于」越堆越长（就是用户说的"显示过多"）。
                        Spacer(Modifier.height(10.dp))
                        Text("诊断与报错已并入「日志」（下方按钮打开）",
                            color = inkFaint, fontSize = Ui.S_CAP1.sp)
                    }
                }

                // [V3.26 · 用户 #6] **日志按钮**（下方）：诊断 + 事件轨迹 + 后期故障/报错，便于维修检索
                GhostButton(backdrop, "日志（诊断 / 报错 / 轨迹）",
                    Modifier.fillMaxWidth().padding(top = 10.dp), ink) { host.openLog() }

                if (state.status.isNotEmpty()) {
                    Text(
                        state.status,
                        color = if (state.statusError) Color(Ui.WARN) else inkFaint,
                        fontSize = Ui.S_FOOT.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
                if (!state.hasHost) {
                    Text(
                        "（当前是独立切片：连接类动作只会提示，真正保存/重连在正式版宿主里）",
                        color = inkFaint, fontSize = Ui.S_CAP1.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
        }   // [V3.43] 内容层收尾（它带 pageBackdrop ⇒ 层外的顶栏能糊到从它底下滑过去的内容）

        // 顶栏：与对话页同款的三件式（左圆返回 + 中间玻璃条 + 右侧留白保持对称）
        // [V3.43] **必须在内容层之外**、并取 `pageBackdrop`：放进层里会自取样（"一片平白"）。
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = topInsetDp + 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RefractionWindow(
                backdrop = pageBackdrop, radius = 23.dp,
                modifier = Modifier.size(46.dp).glassTap { host.back() },
            ) {
                Icon(painterResource(R.drawable.ic_chevron_left), null, tint = brand,
                    modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
            RefractionWindow(
                backdrop = pageBackdrop, radius = 22.dp,
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center) {
                    Text("设置", color = ink, fontSize = Ui.S_HEAD.sp, fontWeight = FontWeight.Bold)
                    Text("连接 · 外观 · 行为 · 关于", color = inkFaint, fontSize = Ui.S_CAP1.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Spacer(Modifier.size(46.dp))   // 占位，保持标题居中对称
        }

        // [V3.26 · 用户 #6] **日志页**：整屏玻璃面板（诊断 / 报错 / 事件轨迹），可滚动 + 可复制
        // [V3.43] 同样取 `pageBackdrop`（它整屏盖在内容之上，要能糊到内容）
        if (state.logOpen) {
            LogOverlay(pageBackdrop, state.logLines, ink, inkFaint) { state.logOpen = false }
        }

        // [V3.115 · 用户硬规则「**每个界面的玻璃弹窗都应该在对应界面的位置最上层**」]
        //   设置页**自己那一套**弹窗宿主：挂在根 Box 的**最后一层** ⇒ 位置天然在**设置页所有内容之上** ✓；
        //   材质采 `pageBackdrop`（= 本屏内容那层）⇒ 与设备页弹窗同构，**不再借对话页的宿主** ✗
        //   （曾经借对话页 ⇒ 用户停在设置页时对话页没被组合 ⇒ 弹窗没人渲染：透明/跑到对话窗口 ✗）。
        GlassSheetOverlay(
            backdrop = pageBackdrop,
            state = state.sheet,
            // [V3.126 · 真机诊断定案] 必须**先收起、再回调**：回调里可能又打开新面板
            //   （如「粘贴配对」⇒ 玻璃表单）⇒ 顺序反了会把刚打开的新面板立刻关掉 ⇒ "点了没反应" ✗
            onPick = { idx ->
                state.sheet.hide()
                state.sheet.onListPick?.invoke(idx)
            },
            onDismiss = { state.sheet.hide() },
            settleFrames = 2,   // [V3.118] 等 2 帧：先让取样层记录完再画面板，消除"首次打开材质不对、关掉再开才对"
        )
    }
}

internal val PaletteNames = listOf("极光", "深海", "暮色", "森林", "紫罗兰", "素雅")

/**
 * [V3.26 · 用户 #6] **日志页**：整屏玻璃面板，行由宿主灌入（`GlassSettingsState.logLines`）。
 *
 * 用途（用户原话）：「收纳诊断 + 后期故障及报错，方便维修检索」⇒
 * 行按时间顺序排列、可滚动；顶部一行统计、底部「复制全部 / 关闭」。
 * 复制走 `LocalClipboardManager`（复制整段文本，便于贴到聊天/工单里）。
 */
@Composable
private fun LogOverlay(
    backdrop: com.kyant.backdrop.Backdrop,
    lines: List<String>,
    ink: Color,
    inkFaint: Color,
    onClose: () -> Unit,
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val scroll = rememberScrollState()
    Box(
        Modifier.fillMaxSize()
            .background(Color(Ui.ambientBarColor()).copy(alpha = 0.55f))
            .glassTap(corner = 0.dp, scale = 1f, dim = 0f) { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            backdrop,
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).fillMaxHeight(0.86f),
            radius = 28.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("日志", color = ink, fontSize = Ui.S_HEAD.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${lines.size} 行 · 供维修检索",
                        color = inkFaint, fontSize = Ui.S_CAP1.sp)
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth().weight(1f)
                        .verticalScroll(scroll)
                        .pointerInput(Unit) { detectTapGestures { } },   // 面板内点空白不关面板
                ) {
                    if (lines.isEmpty()) {
                        Text("（暂无日志）", color = inkFaint, fontSize = Ui.S_FOOT.sp)
                    } else {
                        lines.forEach { l ->
                            Text(l, color = if (l.contains("错误") || l.contains("失败"))
                                Color(Ui.WARN) else inkFaint,
                                fontSize = Ui.S_CAP1.sp,
                                modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    GhostButton(backdrop, "复制全部", Modifier.weight(1f), ink) {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(lines.joinToString("\n")))
                    }
                    Spacer(Modifier.width(8.dp))
                    GhostButton(backdrop, "关闭", Modifier.weight(1f), ink) { onClose() }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 小件

@Composable
private fun SectionCard(
    backdrop: com.kyant.backdrop.Backdrop,
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    // [V3.26 · 用户 #5 第 2 版] **名字进胶囊**：
    //   收起 = 一条圆角长玻璃胶囊（只有名字 + ▸）；打开 = **同一个胶囊长高**，名字在上、内容在下。
    Column(Modifier.padding(top = 10.dp)) {
        GlassSurface(backdrop, Modifier.fillMaxWidth(), radius = 28.dp) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = if (open) 16.dp else 12.dp)) {
                Row(
                    Modifier.fillMaxWidth().glassTap(corner = 28.dp) { onToggle() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, color = Color(Ui.INK), fontSize = Ui.S_BODY.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(if (open) "▾" else "▸", color = Color(Ui.INK_SUB), fontSize = Ui.S_CAP1.sp)
                }
                if (open) {
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun Label(text: String, ink: Color) {
    Text(text, color = ink, fontSize = Ui.S_BODY.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun FieldRow(
    backdrop: com.kyant.backdrop.Backdrop,
    label: String,
    value: String,
    ink: Color,
    faint: Color,
    onChange: (String) -> Unit,
) {
    GlassSurface(backdrop, Modifier.fillMaxWidth(), radius = 14.dp) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = ink, fontSize = Ui.S_FOOT.sp),
                cursorBrush = SolidColor(Color(Ui.BRAND_FILL)),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(label, color = faint, fontSize = Ui.S_FOOT.sp)
                        inner()
                    }
                },
            )
        }
    }
}

@Composable
private fun Segmented(
    backdrop: com.kyant.backdrop.Backdrop,
    options: List<Pair<String, String>>,
    selected: String,
    ink: Color,
    onPick: (String) -> Unit,
) {
    GlassSurface(backdrop, Modifier.fillMaxWidth(), radius = 22.dp) {
        Row(Modifier.fillMaxWidth().padding(3.dp)) {
            options.forEach { (label, value) ->
                val on = value == selected
                Box(
                    Modifier.weight(1f).height(36.dp)
                        // [V3.33 · 待办 E] `glassTap` 必须在 `clip/background` 之前（外侧先画）
                        .glassTap { onPick(value) }
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (on) Color(Ui.BRAND_FILL) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (on) Color.White else ink, fontSize = Ui.S_CAP1.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    on: Boolean,
    ink: Color,
    sub: Color,
    onToggle: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().glassTap { onToggle(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontSize = Ui.S_BODY.sp)
            if (desc.isNotEmpty()) {
                Text(desc, color = sub, fontSize = Ui.S_CAP1.sp)
            }
        }
        Box(
            Modifier.width(46.dp).height(28.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (on) Color(Ui.BRAND_FILL) else Color(Ui.CHIP_BG)),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.padding(horizontal = 3.dp).size(22.dp).clip(CircleShape)
                .background(Color.White))
        }
    }
}

@Composable
private fun PrimaryButton(
    backdrop: com.kyant.backdrop.Backdrop,
    label: String,
    onClick: () -> Unit,
) {
    RefractionCapsule(backdrop = backdrop, modifier = Modifier.fillMaxWidth().height(46.dp).glassTap { onClick() }) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(
                listOf(Color(0xFF9A6BFF).copy(alpha = 160f / 255f),
                    Color(0xFF3A8CFF).copy(alpha = 160f / 255f)))),
            contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = Ui.S_BODY.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GhostButton(
    backdrop: com.kyant.backdrop.Backdrop,
    label: String,
    modifier: Modifier = Modifier,
    fg: Color,
    onClick: () -> Unit,
) {
    RefractionCapsule(backdrop = backdrop, modifier = modifier.height(42.dp).glassTap { onClick() }) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 180f / 255f)),
            contentAlignment = Alignment.Center) {
            Text(label, color = fg, fontSize = Ui.S_CAP1.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}
