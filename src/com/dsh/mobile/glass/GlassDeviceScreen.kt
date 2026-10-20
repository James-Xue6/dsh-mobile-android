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
    /**
     * [P1 · 多设备] `deviceId -> 这条连接是否 READY`：每台设备一条 WebSocket，
     * 所以两张卡可以**同时**显示「在线」。旧字段 [online] 只代表"当前选中那台"。
     */
    var onlineById by mutableStateOf<Map<String, Boolean>>(emptyMap())
    /**
     * [2026-10-20] 非当前设备的可达性：`deviceId -> 是否 TCP 连得上`（值缺失 = 还不知道）。
     *
     * 为什么要它：同一时刻只能连一台电脑，旧版把"没连它"画成「离线」，
     * 用户两台电脑都开着却看到两张卡都写离线。有了这张表，非当前设备能老实显示
     * 「在线」（电脑开着，但当前没连它）或「离线」（真连不上）。
     */
    var reachable by mutableStateOf<Map<String, Boolean>>(emptyMap())
    /**
     * [2026-10-20] `deviceId -> 与它共用同一份地址的另一台设备名`。
     * 非空说明这条记录很可能被"跨设备地址同步"写串了：卡片上直说"请重新扫码配对"，
     * 而不是让用户对着「配对信息失效」猜。**只标出来，绝不自动删用户的地址**。
     */
    var addressConflict by mutableStateOf<Map<String, String>>(emptyMap())
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
            // ---- 顶栏（正式版定版：平铺背景、无玻璃底板）
            //
            // [2026-10-20 · 用户「设备选择界面右上角不应该显示设置」] 这里原来有个齿轮按钮。
            // 设备页是"选设备 / 连设备"的地方，右上角再放设置入口既抢焦点、又让人误以为
            // 已经进了设置页 ⇒ 去掉（设置页仍可从左侧抽屉进）。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "我的设备", color = ink, fontSize = Ui.S_LARGE.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
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
                    online = state.onlineById[d.id] ?: (state.online && isActive),
                    // 非当前设备：电脑到底在不在（null = 还没探到）；当前设备不用它（online 已经说明）
                    reachable = state.reachable[d.id],
                    onEnter = { host.open(d) },
                    onRename = { host.rename(d) },
                    onDelete = { host.delete(d) },
                    // [2026-10-20] 线路判据必须走宿主（MainActivity.routeOf）：
                    // 默认那条 `Store.pickOf(it)` **带上了全局的"内网刚失败"退避**，
                    // 于是从没连过的设备也会写「内网刚连不上，暂用公网」——跨设备串味。
                    routeOf = { host.routeOf(it) },
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
            // ⚠️ [0.87.2 · 真机 bug「点『去设置』直接进了扫码取景页」] 这里原来**无条件**转发给宿主：
            //   `onPick = { host.sheetPick(it) }` ✗ —— 宿主 `sheetPick` 只认识本页**自己的**菜单
            //   （tag=`add` 二选一 / `delete:<设备id>`），其余一律按「index 0 = 扫码添加」处理。
            //   于是**别的流程在本页弹的确认框**（更新链路「还差一步：允许安装应用」⇒ 去设置 /
            //   「更新包已下载好」⇒ 立即安装 …）按下去都变成「添加设备 → 扫码添加」⇒ 直接跳相机取景页，
            //   `去设置` 的回调根本没跑（0.87.1 真机实测）。
            //   ⇒ 与抽屉 / 设置页 / 对话页**同一条纪律**：先收起，再按"这张弹窗是谁写的"分派 ——
            //     通用确认/列表（`onListPick` 有值，源码见 `MainActivity.glassInScreenConfirm`）
            //     交还给它自己；只有本页自有菜单才落给宿主。
            onPick = { idx ->
                val tag = state.sheet.tag
                state.sheet.hide()          // 先收起：回调里可能又要开新面板（V3.126 同一条教训）
                if (tag == "add" || tag.startsWith("delete:")) host.sheetPick(idx)
                else state.sheet.onListPick?.invoke(idx) ?: host.sheetPick(idx)
            },
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
