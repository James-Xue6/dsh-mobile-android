package com.dsh.mobile.glass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.R
import com.dsh.mobile.Store
import com.dsh.mobile.net.LanAddress
import com.dsh.mobile.net.RoutePolicy
import com.dsh.mobile.ui.Theme
import com.dsh.mobile.ui.Ui
import com.kyant.backdrop.Backdrop

/**
 * 【设备页 · 独立切片页】逐页替换第 1 页的**独立入口**（桌面图标「掌上通·新界面」).
 *
 * V3.0 起这一屏的 UI 本体搬到 `GlassDeviceScreen.kt`（可被宿主驱动的那一版），
 * 本文件只剩两件事：
 * 1. **独立宿主的装配**：自己从 `Store` 读数据、自己弹 Compose 对话框、自己处理点击；
 * 2. **共享玻璃件**（[DeviceCard] / [GearButton] / [GlassPillButton] / [Badge] / [Kv]）——
 *    `internal` 可见，给 [GlassDeviceScreen] 与宿主版复用，避免两套 UI 漂移。
 *
 * 正式 App 里这一屏由 `MainActivity` 托管（宿主回调走它的真实流程），见 `GlassDeviceScreen`。
 */
class GlassDevicePageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 调试钩子：`am start … --ez glass_force_press true` 强制按下态（见 GlassDebug 说明）
        GlassDebug.forcePressed = intent?.getBooleanExtra("glass_force_press", false) ?: false
        val store = Store(this)
        Ui.applyTheme(Theme.resolveDark(this, store.themeMode()))
        Ui.setAmbientPalette(store.ambientPalette())
        setContent { GlassAppTheme { StandaloneDevicePage(store) } }
    }
}

/** 独立切片：把 Store 的数据灌进 [GlassDeviceState]，动作全部本地处理。 */
@Composable
private fun StandaloneDevicePage(store: Store) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val state = remember { GlassDeviceState() }
    var renameTarget by remember { mutableStateOf<Store.Device?>(null) }
    var deleteTarget by remember { mutableStateOf<Store.Device?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    fun reload() {
        state.devices = store.devices()
        state.activeId = store.activeDeviceId()
        state.online = false            // 独立切片拿不到连接：见 GlassDeviceScreen 的说明
        state.statusText = if (state.devices.isEmpty()) {
            "还没有设备 · 点下面的「＋ 添加设备」"
        } else {
            "离线 · 还没连上"
        }
        state.statusWarn = state.devices.isNotEmpty()
        state.routeLabel = runCatching { store.routeLine() + " · " + store.routeSource() }.getOrDefault("")
    }
    remember { reload() }

    val host = remember {
        object : GlassDeviceHost {
            override fun routeOf(d: Store.Device): RoutePolicy.Pick? = Store.pickOf(d)
            override fun open(d: Store.Device) {
                ctx.startActivity(android.content.Intent(ctx, GlassChatPageActivity::class.java))
            }
            override fun addDevice() { showAdd = true }
            override fun sheetPick(index: Int) {
                // [V3.10] 统一弹窗骨架：切片里只演示（正式版走 MainActivity 的 onScanAdd/onManualAdd）
                android.widget.Toast.makeText(
                    ctx,
                    if (index == 0) "扫码添加（切片未接管）" else "手动添加（切片未接管）",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
            override fun rename(d: Store.Device) { renameTarget = d }
            override fun delete(d: Store.Device) { deleteTarget = d }
            override fun openSettings() {
                ctx.startActivity(android.content.Intent(ctx, GlassSettingsActivity::class.java))
            }
            override fun retryConnect() {
                android.widget.Toast.makeText(ctx, "独立切片未接入网关：请用正式版重连", android.widget.Toast.LENGTH_SHORT).show()
            }
            override fun rescanPair() {
                android.widget.Toast.makeText(ctx, "扫码配对在正式版里（切片未接管）", android.widget.Toast.LENGTH_SHORT).show()
            }
            override fun switchRoute() {
                android.widget.Toast.makeText(ctx, "切换线路在正式版里（切片未接管）", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    GlassDeviceScreen(state, host)

    renameTarget?.let { d ->
        RenameDialog(
            initial = d.displayName(),
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                store.renameDevice(d.id, name)
                renameTarget = null
                reload()
            },
        )
    }
    deleteTarget?.let { d ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color(Ui.SURFACE_2),
            title = { Text("删除设备", color = Color(Ui.INK)) },
            text = { Text("删除「${d.displayName()}」？它的地址与令牌会一并清掉。", color = Color(Ui.INK_SUB)) },
            confirmButton = {
                TextButton(onClick = {
                    store.removeDevice(d.id)
                    deleteTarget = null
                    reload()
                }) { Text("删除", color = Color(0xFFFF3B30)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
    if (showAdd) {
        ManualAddDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, addr, token ->
                val dev = Store.Device()
                dev.id = store.newDeviceId()
                dev.name = name
                if (LanAddress.isUsableLanUrl(addr)) dev.lanUrl = addr else dev.wanUrl = addr
                dev.token = token
                store.upsertDevice(dev, true)
                showAdd = false
                reload()
            },
        )
    }
}

// ------------------------------------------------------------------ 共享玻璃件（internal，宿主版复用）

/** 设备卡圆角（B 类布局参数）：正式版标准卡圆角 28dp。 */
internal val CardRadius = Ui.R_CARD.dp

/** 右上角设置：36dp 玻璃圆 + 图标 9dp（正式版 2026-10-07 用户定版「图标缩小一半」）。 */
@Composable
internal fun GearButton(backdrop: Backdrop, ink: Color, onClick: () -> Unit) {
    RefractionWindow(
        backdrop = backdrop,
        radius = 18.dp,
        // [V3.15] 36dp 里塞 9dp 图标太小，按动缩放几乎看不见（用户报"没有动画反馈"）→ 44dp/20dp
        modifier = Modifier.size(44.dp).glassTap(corner = 22.dp) { onClick() },
    ) {
        Icon(
            painterResource(R.drawable.ic_sliders),
            contentDescription = "设置",
            tint = ink,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 一张设备卡。内容排布逐行照 `DeviceHubView.deviceCard()`；
 * 线路行取 [routeOf]（宿主版传 `MainActivity.routeOf`，切片版传 `Store.pickOf`），
 * 保证"走内网/走公网 + 规则来源"和正式版同源。
 */
@Composable
internal fun DeviceCard(
    backdrop: Backdrop,
    d: Store.Device,
    isActive: Boolean,
    online: Boolean,
    /**
     * [2026-10-20] 非当前设备"电脑在不在"的探测结果：
     * `true` = 开着（只是当前没连它）、`false` = 真连不上、`null` = 还没探到/不适用。
     *
     * 为什么加这个：同一时刻只能连一台电脑，旧版把"没连它"写成「离线」，
     * 用户两台都开着却看到两张卡都写离线。现在非当前设备可达 → 徽标仍是「在线」。
     */
    reachable: Boolean? = null,
    onEnter: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    routeOf: (Store.Device) -> RoutePolicy.Pick? = { Store.pickOf(it) },
) {
    val ink = Color(Ui.INK)
    val inkSub = Color(Ui.INK_SUB)
    // 三态：已连接 / 电脑开着但没连它 / 连不上（或未知）
    val reachableOnly = !online && !isActive && reachable == true
    val badgeText = when {
        online -> "在线"
        reachableOnly -> "在线"
        isActive -> "离线"
        reachable == false -> "离线"
        else -> "未连接"
    }

    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        radius = CardRadius,
    ) {
        Column(Modifier.padding(Ui.M_CARD_PAD.dp)) {
            // 第一行：图标砖 + 名字/徽标 + 平台
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassSurface(backdrop, Modifier.size(44.dp), radius = 14.dp) {
                    Icon(
                        painterResource(R.drawable.ic_monitor),
                        contentDescription = null,
                        tint = ink,
                        modifier = Modifier.align(Alignment.Center).size(18.dp),
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            d.displayName(), color = ink, fontSize = Ui.S_BODY.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Badge(
                            text = badgeText,
                            // 「在线但没连它」用淡一档的绿：既说清"电脑开着"，又不冒充"已连接"
                            bg = Color(
                                when {
                                    online -> Ui.BADGE_OK_BG
                                    reachableOnly -> Ui.alpha(Ui.BADGE_OK_BG, 0.55f)
                                    else -> 0x1C000000
                                }
                            ),
                            fg = Color(if (online || reachableOnly) Ui.BADGE_OK_FG else Ui.INK),
                            dot = Color(if (online || reachableOnly) Ui.BADGE_OK_FG else Ui.INK),
                        )
                        if (isActive) Badge(text = "当前", bg = Color(0x3D5444C8), fg = ink)
                    }
                    Text(
                        platformLabel(d), color = ink, fontSize = Ui.S_FOOT.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }

            // 第二行：标签（内网 · 固定 / 公网 / DSH 版本）
            Row(Modifier.padding(top = 6.dp)) {
                if (d.hasUsableLan()) Kv("内网", "· 固定", ink, inkSub)
                if (!d.wanUrl.isNullOrEmpty()) Kv("公网", "", ink, inkSub)
                if (!d.dshVersion.isNullOrEmpty()) Kv("DSH", d.dshVersion, ink, inkSub)
            }

            // 第三行：这次走哪条线路 + 规则来源（不显示地址，便于截图分享）
            val route: RoutePolicy.Pick? = routeOf(d)
            val addrLine = when {
                route == null || route.url.isNullOrEmpty() ->
                    "还没有可用地址 · 重新扫码或手动添加"
                // [2026-10-20] 前缀必须和徽标同义：没连它 ≠ 它离线
                else -> (
                    when {
                        online && isActive -> "已连接 · "
                        // [P1] 后台也连着：说清楚它在线、只是界面没停在它这儿
                        online -> "已连接（后台）· "
                        reachableOnly -> "可连接 · "
                        isActive || reachable == false -> "离线 · "
                        else -> "未连接 · "
                    }
                    ) + route.line + " · " + route.source
            }
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                radius = 14.dp,
            ) {
                Text(
                    addrLine, color = ink, fontSize = Ui.S_FOOT.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                )
            }

            // 第四行：三按钮等宽
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                GlassPillButton(
                    backdrop, if (online) "进入对话" else (if (isActive) "重连" else "连接"),
                    Modifier.weight(1f),
                    listOf(Color(0xFF9A6BFF), Color(0xFF3A8CFF)), 160f / 255f, Color.White, onEnter,
                )
                Spacer(Modifier.width(6.dp))
                GlassPillButton(
                    backdrop, "修改名称", Modifier.weight(1f),
                    listOf(Color(0xF2FFFFFF), Color(0xCCFFFFFF)), 180f / 255f, ink, onRename,
                )
                Spacer(Modifier.width(6.dp))
                GlassPillButton(
                    backdrop, "删除", Modifier.weight(1f),
                    listOf(Color(0x59FF3B30), Color(0x2EFF3B30)), 170f / 255f,
                    Color(0xFFB71C1C), onDelete,
                )
            }
        }
    }
}

/** 带颜色的液态玻璃胶囊按钮（正式版 `frostedButton`：玻璃 + 语义色渐变 + 文字，一体成形）。 */
@Composable
internal fun GlassPillButton(
    backdrop: Backdrop,
    label: String,
    modifier: Modifier = Modifier,
    grad: List<Color>,
    gradAlpha: Float,
    fg: Color,
    onClick: () -> Unit,
) {
    RefractionCapsule(
        backdrop = backdrop,
        // 按压反馈挂在**整只胶囊**上：否则按下只缩那块渐变、玻璃不动，看着像"内容掉进去了"。
        modifier = modifier.height(42.dp).glassTap(onClick = onClick),
    ) {
        Box(
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape(999.dp))
                .background(Brush.linearGradient(grad.map { it.copy(alpha = gradAlpha) })),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = fg, fontSize = Ui.S_BODY.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** demo 的 `.badge`：11sp 胶囊 +（可选）圆点。 */
@Composable
internal fun Badge(text: String, bg: Color, fg: Color, dot: Color? = null) {
    Row(
        Modifier.padding(start = 8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(999.dp)).background(dot))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = fg, fontSize = Ui.S_CAP1.sp, fontWeight = FontWeight.Medium)
    }
}

/** 纯文字 kv（`内网 · 固定`）：键用正文色、值用次要色。 */
@Composable
internal fun Kv(key: String, value: String, ink: Color, inkSub: Color) {
    Row(Modifier.padding(end = 12.dp)) {
        Text(key, color = ink, fontSize = Ui.S_FOOT.sp, fontWeight = FontWeight.Medium)
        if (value.isNotEmpty()) {
            Text(" $value", color = inkSub, fontSize = Ui.S_FOOT.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** 平台标签：优先用网关给的平台名，没有就统一说「桌面端」。 */
internal fun platformLabel(d: Store.Device): String {
    val p = d.platform?.trim().orEmpty()
    return if (p.isEmpty()) "桌面端" else "$p · 桌面端"
}

// ------------------------------------------------------------------ 对话框

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(Ui.SURFACE_2),
        title = { Text("修改名称", color = Color(Ui.INK)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("显示名称") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 手动添加（真实写库）：名称 / 地址 / 令牌。地址按 `LanAddress.isUsableLanUrl` 分内网/公网槽。 */
@Composable
private fun ManualAddDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var addr by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(Ui.SURFACE_2),
        title = { Text("手动添加设备", color = Color(Ui.INK)) },
        text = {
            Column {
                Text(
                    "扫码添加仍在正式版设备页（配对流程尚未接管）。",
                    fontSize = 12.sp, color = Color(Ui.INK_SUB),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(name, { name = it }, singleLine = true,
                    label = { Text("设备名（可空）") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(addr, { addr = it }, singleLine = true,
                    label = { Text("地址 ws://…") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(token, { token = it }, singleLine = true,
                    label = { Text("令牌") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, addr.trim(), token.trim()) },
                enabled = addr.isNotBlank() && token.isNotBlank(),
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
