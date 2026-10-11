package com.dsh.mobile.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.R
import com.dsh.mobile.Store
import com.dsh.mobile.net.RoutePolicy
import com.dsh.mobile.ui.Ui
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 【设备页 · 可托管版（V3.0）】把「设备页」从"独立切片"升级成**能被宿主驱动**的一屏。
 *
 * ## 为什么要拆成 State + Host
 * 独立切片（[GlassDevicePageActivity]）只能自己读 `Store`、自己弹 Compose 对话框 —— 它拿不到
 * `MainActivity` 里的**实时在线判据**、**线路选择**、**连接门**和那些真实流程。
 * 托管版把这两件事分开：
 * - [GlassDeviceState]：宿主往里推的**不可变快照**（设备表 / 当前设备 / 在线 / 状态行 / 连接门 / 主题版本）；
 * - [GlassDeviceHost]：这一屏上的动作**全部回调给宿主**（宿主就是 `MainActivity`，它本来就实现了
 *   `onOpenDevice / onRenameDevice / onDeleteDevice / onScanAdd / onManualAdd / onOpenSettings /
 *   onRetryConnect / onRescanPair / onSwitchRoute / routeOf` —— 直接复用，功能零回归）。
 *
 * 这样同一份 UI 有两个宿主：正式 App（`MainActivity`）与独立切片页。
 */
class GlassDeviceState {
    var devices by mutableStateOf<List<Store.Device>>(emptyList())
    var activeId by mutableStateOf("")
    var online by mutableStateOf(false)
    var connecting by mutableStateOf(false)
    /** 状态行文案（正式版 `refreshDevices()` 算好的那句）+ 是否报警色。 */
    var statusText by mutableStateOf("")
    var statusWarn by mutableStateOf(false)
    /** 线路标签（正式版 `routeLabel()`），只显示"走内网/走公网 + 依据"，不显示地址。 */
    var routeLabel by mutableStateOf("")
    /** 连接门（null = 不显示）。 */
    var gate by mutableStateOf<GateInfo?>(null)

    /**
     * 主题版本号 +1 会触发整屏重组。
     *
     * <p>为什么要它：文本/主题色读的是 `Ui.INK` 这类**静态字段**，它们不属于 Compose 的可观察状态，
     * 用户在设置页切了深浅色之后这一屏不会自己重画。宿主切主题时把它 +1 即可。
     */
    var themeRev by mutableStateOf(0)
    /** [V3.10] 本屏自己的弹窗（统一弹窗骨架，见 [GlassSheetOverlay]）。 */
    val sheet = GlassSheetState()

    fun bumpTheme() { themeRev++ }
}

/** 连接门内容（对应正式版 `deviceHub.setGate(...)` 的一串参数）。 */
data class GateInfo(
    val title: String,
    val hint: String,
    val route: String,
    val addr: String,
    val reason: String,
    val canSwitch: Boolean,
    val busy: Boolean,
)

/** 设备页上的全部动作 —— 由宿主实现（正式 App 里就是 `MainActivity` 的真实流程）。 */
interface GlassDeviceHost {
    fun routeOf(d: Store.Device): RoutePolicy.Pick?
    fun open(d: Store.Device)
    /** 点「＋ 添加设备」：宿主自己决定弹什么（正式版是扫码/手动二选一）。 */
    fun addDevice()
    /** [V3.10] 统一弹窗里点了第 index 项（宿主把 index 映射到真实动作）。 */
    fun sheetPick(index: Int)
    fun rename(d: Store.Device)
    fun delete(d: Store.Device)
    fun openSettings()
    /** 连接门三个动作。 */
    fun retryConnect()
    fun rescanPair()
    fun switchRoute()
}

/**
 * 设备页本体（纯 UI：只读 [state]、只调 [host]）。
 *
 * @param applyInsets 是否自己加系统栏内边距。**独立 Activity 必须 true**（它没有宿主帮忙留边），
 *   而挂进 `MainActivity` 时宿主已给根容器加过 `setPadding(0, top, 0, bottom)`，再自己加一次会**双倍留边** → false。
 */
@Composable
fun GlassDeviceScreen(
    state: GlassDeviceState,
    host: GlassDeviceHost,
    applyInsets: Boolean = true,
) {
    // 订阅主题版本：切深浅色后宿主 bumpTheme() → 这一屏跟着重画
    val themeRev = state.themeRev
    @Suppress("UNUSED_EXPRESSION") themeRev

    val backdrop = rememberLayerBackdrop()
    val ink = Color(Ui.INK)
    val inkSub = Color(Ui.INK_SUB)
    val inkFaint = Color(Ui.INK_FAINT)

    Box(Modifier.fillMaxSize()) {
        // ① 取样层：正式版环境背景（静态全屏）
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            AmbientBackground(Modifier.fillMaxSize(), rev = state.themeRev)
        }

        // ② 内容层（可滚动）。玻璃件在层外，从 ① 取样。
        Column(
            Modifier.fillMaxSize()
                .then(if (applyInsets) Modifier.systemBarsPadding() else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 76.dp),
        ) {
            // ---- 顶栏（正式版定版：平铺背景、无玻璃底板；只有齿轮是玻璃圆）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "我的设备", color = ink, fontSize = Ui.S_LARGE.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                GearButton(backdrop, ink) { host.openSettings() }
            }
            Text(
                "管理你的电脑 · 连接后进入对话",
                color = Color(Ui.alpha(Ui.INK, 0.88f)), fontSize = Ui.S_SUB.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
            if (state.statusText.isNotEmpty()) {
                Text(
                    state.statusText,
                    color = if (state.statusWarn) Color(Ui.WARN) else Color(Ui.alpha(Ui.INK, 0.78f)),
                    fontSize = Ui.S_FOOT.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // ---- 连接门（正式版要求：没连上就顶在最上面说清楚 + 给重试入口）
            state.gate?.let { g -> GateCard(backdrop, g, host) }

            // ---- 空态
            if (state.devices.isEmpty() && state.gate == null) {
                Spacer(Modifier.height(120.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    GlassSurface(backdrop, Modifier.size(72.dp), radius = 22.dp) {
                        Icon(
                            painterResource(R.drawable.ic_monitor), null,
                            tint = Color(Ui.alpha(Ui.INK_SUB, 0.85f)),
                            modifier = Modifier.align(Alignment.Center).size(32.dp),
                        )
                    }
                    Text("还没有添加设备", color = inkSub, fontSize = Ui.S_FOOT.sp,
                        modifier = Modifier.padding(top = 10.dp))
                    Text(
                        "电脑端 DSH 打开「移动设备」面板，扫码即可添加",
                        color = inkFaint, fontSize = Ui.S_CAP1.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp),
                    )
                }
            }

            // ---- 设备卡（真实数据）
            state.devices.forEach { d ->
                val isActive = d.id == state.activeId
                DeviceCard(
                    backdrop = backdrop,
                    d = d,
                    isActive = isActive,
                    online = state.online && isActive,
                    onEnter = { host.open(d) },
                    onRename = { host.rename(d) },
                    onDelete = { host.delete(d) },
                )
            }

            // ---- 「＋ 添加设备」玻璃卡 + 卡下小字
            Spacer(Modifier.height(4.dp))
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().glassTap(corner = CardRadius) { host.addDevice() },
                radius = CardRadius,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(15.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(26.dp).clip(RoundedCornerShape(999.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF9A6BFF), Color(0xFF3A8CFF)))),
                        contentAlignment = Alignment.Center,
                    ) { Text("＋", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    Text(
                        "添加设备", color = ink, fontSize = Ui.S_HEAD.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 9.dp),
                    )
                }
            }
            Text(
                "扫码或输入设备连接", color = inkSub, fontSize = Ui.S_FOOT.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        // ---- 页脚贴底
        Text(
            "DSH 掌上通 · 与电脑端 DSH 直连\n数据只在你的设备之间流转",
            style = androidx.compose.ui.text.TextStyle(
                color = Color.White, fontSize = Ui.S_CAP1.sp, textAlign = TextAlign.Center,
                shadow = androidx.compose.ui.graphics.Shadow(
                    Color(0x8A000000), androidx.compose.ui.geometry.Offset(0f, 1.5f), 3f),
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 14.dp, start = 8.dp, end = 8.dp),
        )

        // ---- [V3.10] 统一弹窗骨架：渲染在**取样层之外**（与页面其它玻璃件一致），才能糊到背后的页面
        GlassSheetOverlay(
            backdrop = backdrop,
            state = state.sheet,
            onPick = { host.sheetPick(it) },
            onDismiss = { state.sheet.hide() },
            settleFrames = 2,   // [V3.118] 等 2 帧：先让取样层记录完再画面板，消除"首次打开材质不对、关掉再开才对"
        )
    }
}

/** 连接门卡：标题/说明/线路/地址/原因 + 重试入口（正式版 `DeviceHubView.renderGate`）。 */
@Composable
private fun GateCard(
    backdrop: com.kyant.backdrop.Backdrop,
    g: GateInfo,
    host: GlassDeviceHost,
) {
    val ink = Color(Ui.INK)
    val inkSub = Color(Ui.INK_SUB)
    val inkFaint = Color(Ui.INK_FAINT)
    GlassSurface(backdrop, Modifier.fillMaxWidth().padding(top = 10.dp), radius = 28.dp) {
        Column(Modifier.padding(16.dp)) {
            Text(g.title, color = ink, fontSize = Ui.S_BODY.sp, fontWeight = FontWeight.Bold)
            if (g.hint.isNotEmpty()) {
                Text(g.hint, color = inkSub, fontSize = Ui.S_FOOT.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (g.route.isNotEmpty()) {
                Text("线路：${g.route}", color = inkFaint, fontSize = Ui.S_CAP1.sp,
                    modifier = Modifier.padding(top = 8.dp))
            }
            if (g.addr.isNotEmpty()) {
                Text("地址：${g.addr}", color = inkFaint, fontSize = Ui.S_CAP1.sp,
                    modifier = Modifier.padding(top = 2.dp))
            }
            if (g.reason.isNotEmpty()) {
                Text(g.reason, color = inkFaint, fontSize = Ui.S_CAP1.sp,
                    modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.height(12.dp))
            GlassPillButton(
                backdrop, if (g.busy) "重连中…" else "重新连接", Modifier.fillMaxWidth(),
                listOf(Color(0xFF9A6BFF), Color(0xFF3A8CFF)), 160f / 255f, Color.White,
            ) { if (!g.busy) host.retryConnect() }
            if (g.canSwitch) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    GlassPillButton(
                        backdrop, "切换线路", Modifier.weight(1f),
                        listOf(Color(0xF2FFFFFF), Color(0xCCFFFFFF)), 180f / 255f, ink,
                    ) { host.switchRoute() }
                    Spacer(Modifier.width(8.dp))
                    GlassPillButton(
                        backdrop, "重新扫码配对", Modifier.weight(1f),
                        listOf(Color(0x59FF3B30), Color(0x2EFF3B30)), 170f / 255f, Color(0xFFB71C1C),
                    ) { host.rescanPair() }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                GlassPillButton(
                    backdrop, "重新扫码配对", Modifier.fillMaxWidth(),
                    listOf(Color(0xF2FFFFFF), Color(0xCCFFFFFF)), 180f / 255f, ink,
                ) { host.rescanPair() }
            }
        }
    }
}
