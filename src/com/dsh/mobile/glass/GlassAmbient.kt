// ============================================================================
//  GlassAmbient.kt —— 「正式版环境背景」+「自适应高度的玻璃面」
//  规范：docs/GlassUI/GlassUI_SPEC_折射观察窗.md   报告：GlassUI_Report_V1.9.md
// ----------------------------------------------------------------------------
//  两个小组件，都是为了让 Compose 切片能**照抄正式版**，而不是自己另起一套：
//
//  1) AmbientBackground —— 直接挂官方 `Ui.AmbientDrawable`（与 MainActivity 根容器
//     同一份绘制代码：配色档三色纵向渐变 + 四枚径向光斑）。
//     为什么不在 Compose 里用画笔重画：重画必然和正式版漂移（色标位置、光斑半径、
//     深浅色压暗矩阵都得抄一遍）。挂官方 Drawable 之后，官方改了本页跟着改，
//     而且在正式版里换「配色」档，本页只要跟着 setAmbientPalette 就同步。
//
//  2) GlassSurface —— **高度随内容**的玻璃面。
//     RefractionWindow 内部是 `BoxWithConstraints` + `fillMaxSize()`，只在**尺寸确定**的
//     容器里成立；直接给它 wrap-content 的高度，内部 fillMaxSize 会拿到无界约束。
//     所以不要在组件本体里改测量逻辑（规范：组件本体锁定），而是在调用处用
//     `Box { 玻璃.matchParentSize(); 内容 }` 把高度交给内容决定 —— 等价于正式版
//     `FrostedPanelView.content()` 的语义（内容在玻璃之上、玻璃跟随内容尺寸）。
// ============================================================================
package com.dsh.mobile.glass

import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize   // [V3.52] 自定义底图铺满
import androidx.compose.foundation.background            // [V3.52] 自定义底图的压色罩
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.roundToInt   // [V3.33] 阴影位图半分辨率：放大回原尺寸时的落点取整
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dsh.mobile.ui.Ui
import com.kyant.backdrop.Backdrop

/**
 * 正式版环境背景：配色档（极光/深海/暮色/森林/紫罗兰/素雅）三色纵向渐变 + 四枚光斑。
 *
 * 使用前请先按当前设置调 [Ui.setAmbientPalette]，否则永远画第 0 档。
 */
/**
 * [V3.52 · 用户「配色适当增加一个选项**自己上传图片**，然后可以采用自己上传的图片当底配色」]
 *
 * 自定义底图：存的是**用户选中的图片 URI**（空串 = 用内置 6 套配色）。
 * 做成 Compose 可观察状态 ⇒ 选完**立刻全屏刷新**，不需要重启（与 `state.themeRev` 同一套思路）。
 */
object GlassAmbientImage {
    private val uriState = androidx.compose.runtime.mutableStateOf("")
    var uri: String
        get() = uriState.value
        set(value) { uriState.value = value }

    /**
     * [V3.91 · 用户「对话框上方的渐变遮罩，切换成上传的图片之后，上面显示的**别的颜色**的遮罩，
     * 能不能**从上传图片提取**？」]
     *
     * 顶栏渐变**从图片顶部提取的代表色**（ARGB；`0` = 还没提取到/没有自定义图）。
     * 背景：顶栏渐变原来一律用 `Ui.ambientBarColor()`（内置配色的顶色）⇒ 换成自定义底图后
     * 颜色对不上 ✗。这里由 {@link AmbientBackground} 解码图片时算出来，顶栏优先用它。
     */
    private val topColorState = androidx.compose.runtime.mutableStateOf(0)
    var topColor: Int
        get() = topColorState.value
        set(value) { topColorState.value = value }
}

/**
 * [V3.91] 顶栏渐变/遮罩该用的颜色：**有自定义底图就取图片顶部的颜色**，否则用配色的顶色。
 * 页面里凡是要"和顶部环境同色"的地方都该用它（而不是直接 `Ui.ambientBarColor()`）。
 */
@Composable
fun ambientTopColor(): Color =
    GlassAmbientImage.topColor.takeIf { it != 0 }?.let { Color(it) }
        ?: Color(com.dsh.mobile.ui.Ui.ambientBarColor())

/**
 * [V3.91] 图片**顶部约 12% 区域**的平均色（忽略透明像素）；取不到返回 `0`。
 *
 * 采样是稀疏的（每行隔若干列取一个，最多 48 行）⇒ 几千像素级别，不会卡主线程。
 */
private fun topAverageColor(b: android.graphics.Bitmap?): Int {
    if (b == null || b.width <= 0 || b.height <= 0) return 0
    val rows = (b.height * 0.12f).toInt().coerceIn(1, 48)
    val step = (b.width / 12).coerceAtLeast(1)
    var r = 0L
    var g = 0L
    var bl = 0L
    var n = 0L
    var y = 0
    while (y < rows) {
        var x = 0
        while (x < b.width) {
            val p = runCatching { b.getPixel(x, y) }.getOrDefault(0)
            if (android.graphics.Color.alpha(p) > 8) {
                r += android.graphics.Color.red(p)
                g += android.graphics.Color.green(p)
                bl += android.graphics.Color.blue(p)
                n++
            }
            x += step
        }
        y++
    }
    if (n == 0L) return 0
    return android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
}

@Composable
fun AmbientBackground(modifier: Modifier = Modifier, rev: Int = 0) {
    val imgUri = GlassAmbientImage.uri
    // [V3.93 · 用户「上一步修改的渐变遮罩出问题了，**我一切换又不行了**，我切换别的颜色的
    //   渐变色**还停留在刚才**」]
    //   ⚠️ 根因：`topColor` 只在"解码图片成功"时写入，**换配色/取消自定义底图时从不清零** ✗
    //   ⇒ 顶栏一直停在上一张图的颜色。这里在**任何 URI 变化**时先清零：
    //   有图 ⇒ 随后解码会重新写入；没图 ⇒ 就回落到 `Ui.ambientBarColor()`（配色的顶色）✓。
    androidx.compose.runtime.LaunchedEffect(imgUri) {
        GlassAmbientImage.topColor = 0
    }
    if (imgUri.isNotEmpty()) {
        // [V3.52] 自定义底图：把用户选的图片**解码成 Bitmap 铺满**（Crop），
        //   上面压一层很淡的同色罩（浅色压白、深色压黑）⇒ 前景的玻璃件与文字仍然读得清。
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, imgUri) {
            val decoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(android.net.Uri.parse(imgUri))?.use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()
            }
            // [V3.91] 顺手提取**顶部代表色** ⇒ 顶栏渐变用它对齐（用户报"遮罩颜色是别的颜色"）
            GlassAmbientImage.topColor = topAverageColor(decoded)
            value = decoded
        }
        androidx.compose.foundation.layout.Box(modifier) {
            bmp?.let { b ->
                androidx.compose.foundation.Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            androidx.compose.foundation.layout.Box(
                Modifier.background(
                    if (Ui.isDark()) Color.Black.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.18f)),
            )
        }
        return
    }
    // [V3.27 · 用户「设置里配色切换点击其他配色未立即切换」] ⚠️ 根因：原来 key 里用的是
    //   `Ui.ambientPalette()` —— 它是**普通 Java 静态方法、不是 Compose 可观察状态**
    //   ⇒ 换配色时这个 key **永远不变** ⇒ 背景 Drawable 不重建 ⇒ 要等重进页面才更新。
    //   修法：调用方把**主题版本号** `state.themeRev`（Compose 状态，换配色时各屏都会 bump）传进来。
    // 配色档 / 深浅色变了要**重建**这个 View：它挂的是 Java `Drawable`，Drawable 自己不会通知
    // Compose 重画（SettingsView 那套是手写 View 树所以能自己 invalidate，Compose 侧必须重建）。
    key(rev, Ui.isDark()) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                View(ctx).apply {
                    background = Ui.AmbientDrawable()
                }
            },
        )
    }
}

/**
 * 把 [GlassDeviceScreen] 包成宿主可直接 `addView` 的 View（`MainActivity` 用）。
 *
 * <p>主题版本要在**最外层**订阅：`GlassAppTheme` 里的 Material3 配色是在组合时读 `Ui.isDark()`
 * 烘死的，只有从 setContent 根节点重组才能连它一起换掉（否则切深浅色后弹窗配色会留在上一套）。
 */
/**
 * 顶部安全区（dp）：**取 systemBars ∪ displayCutout ∪ safeDrawing 的最大值，并设 40dp 下限**。
 *
 * <p>为什么要这么算（真机实证 2026-10-08，HONOR PGT-AN10 / 1312x2848 @560dpi、fontScale 0.85）：
 * 该机上报的状态栏 inset 只有 ~62px（≈18dp），比状态栏/挖孔的实际视觉高度小 ⇒ 直接拿它当留白，
 * 顶栏会**顶到状态栏与前置摄像头**。加 40dp 下限后各机型都能稳稳让开。
 */
@androidx.compose.runtime.Composable
internal fun topSafeDp(density: androidx.compose.ui.unit.Density): androidx.compose.ui.unit.Dp {
    val px = maxOf(
        androidx.compose.foundation.layout.WindowInsets.systemBars.getTop(density),
        androidx.compose.foundation.layout.WindowInsets.displayCutout.getTop(density),
        androidx.compose.foundation.layout.WindowInsets.safeDrawing.getTop(density),
        with(density) { 40.dp.roundToPx() },
    )
    return with(density) { px.toDp() }
}

fun createGlassDeviceView(    ctx: android.content.Context,
    state: GlassDeviceState,
    host: GlassDeviceHost,
): View = AndroidViewHolder(ctx, state, host).view

/** 同上，对话页版本（宿主 = `MainActivity`；见 [GlassChatScreen] 的类注释）。 */
fun createGlassChatView(
    ctx: android.content.Context,
    state: GlassChatState,
    host: GlassChatHost,
): View = androidx.compose.ui.platform.ComposeView(ctx).apply {
    setContent {
        @Suppress("UNUSED_EXPRESSION") state.themeRev
        // [V3.5] applyInsets = true：同上，系统栏内边距由屏幕自己按"头部/玻璃件/列表"分别留
        GlassAppTheme(state.themeRev) { GlassChatScreen(state, host, applyInsets = true) }
    }
}

/** 设置页（宿主 = MainActivity，见 [GlassSettingsScreen] 的类注释）。 */
fun createGlassSettingsView(
    ctx: android.content.Context,
    state: GlassSettingsState,
    host: GlassSettingsHost,
): View = androidx.compose.ui.platform.ComposeView(ctx).apply {
    setContent {
        @Suppress("UNUSED_EXPRESSION") state.themeRev
        GlassAppTheme(state.themeRev) { GlassSettingsScreen(state, host, applyInsets = true) }
    }
}

/** 抽屉里的会话列表（外壳仍是 DrawerHost，见 [GlassSessionListScreen] 的类注释）。 */fun createGlassSessionView(
    ctx: android.content.Context,
    state: GlassSessionState,
    host: GlassSessionHost,
): View = androidx.compose.ui.platform.ComposeView(ctx).apply {
    setContent {
        @Suppress("UNUSED_EXPRESSION") state.themeRev
        // 【注意】applyInsets = **false**：抽屉外壳 `DrawerHost` 已经用 `applySafeInsets()`
        // 让开状态栏/手势条了（见该方法的注释），这里再让一次就是**双倍内缩**（真机表现：
        // 会话列表整体被推下去一大截、顶部空出一条）。安全区只由外壳负责。
        GlassAppTheme(state.themeRev) { GlassSessionListScreen(state, host, applyInsets = false) }
    }
}

private class AndroidViewHolder(
    ctx: android.content.Context,
    state: GlassDeviceState,
    host: GlassDeviceHost,
) {
    val view: View = androidx.compose.ui.platform.ComposeView(ctx).apply {
        setContent {
            @Suppress("UNUSED_EXPRESSION") state.themeRev
            // [V3.5] applyInsets = true：MainActivity 对 Compose 屏**不再**替它留系统栏内边距
            // （见 MainActivity.applyRootInsets），所以由屏幕自己留。
            GlassAppTheme(state.themeRev) { GlassDeviceScreen(state, host, applyInsets = true) }
        }
    }
}

/**
 * 高度随内容自适应的玻璃面（卡片 / 面板用）。
 *
 * 与 [RefractionWindow] 的关系：同一个组件，只是调用约定不同 ——
 * 外层 Box 的尺寸由 [content] 决定，玻璃用 `matchParentSize()` 贴着它画。
 */
@Composable
fun GlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    radius: Dp = RefractionWindowTokens.Radius,
    /**
     * [V3.47] 体色**可覆盖**（默认仍走工程 token，行为不变）。
     * 给"要和某一族玻璃件**同一材质**"的调用处用 —— 例如目标胶囊要跟「N 子智能体」条一致。
     */
    tint: Color = RefractionWindowTokens.Tint,
    /**
     * [V3.68] 磨砂半径**可覆盖**（默认仍走工程 token，行为不变）。
     * 给"这块内容要看得更清楚"的调用处用 —— 例如目标卡文字压在正文上，需要更实的磨砂。
     */
    blurRadius: Dp = RefractionWindowTokens.BlurRadius,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        RefractionWindow(
            backdrop = backdrop,
            modifier = Modifier.matchParentSize(),
            radius = radius,
            tint = tint,
            blurRadius = blurRadius,
        )
        content()
    }
}

/**
 * 把本 App 自己的配色（`Ui.*`，本来就分浅/深两套）桥接给 Material3。
 *
 * <p>**为什么必须有它**（2026-10-08 真机实测报障「点添加设备弹出来的不一样」）：
 * 我们用 Material3 的 `AlertDialog` / `OutlinedTextField` / `TextButton`，但**从没挂过
 * `MaterialTheme`** → 它们一直吃 Material3 的**默认浅色** colorScheme。于是 App 处于深色时，
 * 弹窗仍是**一张厚白卡**（取证：`evidence/v210/04_add_dialog.png`），与全局暗色玻璃完全脱节。
 * 在浅色主题下它"碰巧对"，所以模拟器阶段一直没发现。
 *
 * <p>只映射长期稳定的角色（primary/background/surface/surfaceVariant/outline/…），
 * 容器类角色由 `darkColorScheme()`/`lightColorScheme()` 自带，避免依赖新版才有的参数。
 */
@Composable
fun GlassAppTheme(themeRev: Int = 0, content: @Composable () -> Unit) {
    // [V3.26 · 用户 #5] 显式读一下主题版本号 ⇒ 换主题/配色时**本组件就地重组**、重建 colorScheme。
    //   以前调用方用 `key(state.themeRev) { GlassAppTheme { … } }` 强制重建整棵子树：
    //   那样**滚动节点会被重建**，而重建首帧内容高度为 0 ⇒ `ScrollState.value` 被夹到 0
    //   ⇒ 用户报的「点外观里的按钮画面弹回顶部」。各屏自己已经读 `themeRev`，不需要这一层 key。
    @Suppress("UNUSED_EXPRESSION") themeRev
    val scheme = if (Ui.isDark()) {
        darkColorScheme(
            primary = Color(Ui.BRAND_FILL), onPrimary = Color.White,
            secondary = Color(Ui.BRAND_FILL), onSecondary = Color.White,
            background = Color(Ui.BG), onBackground = Color(Ui.INK),
            surface = Color(Ui.SURFACE_2), onSurface = Color(Ui.INK),
            surfaceVariant = Color(Ui.SURFACE_2), onSurfaceVariant = Color(Ui.INK_SUB),
            outline = Color(Ui.HAIRLINE), outlineVariant = Color(Ui.LINE),
            error = Color(Ui.ERR), onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = Color(Ui.BRAND_FILL), onPrimary = Color.White,
            secondary = Color(Ui.BRAND_FILL), onSecondary = Color.White,
            background = Color(Ui.BG), onBackground = Color(Ui.INK),
            surface = Color(Ui.SURFACE), onSurface = Color(Ui.INK),
            surfaceVariant = Color(Ui.SURFACE), onSurfaceVariant = Color(Ui.INK_SUB),
            outline = Color(Ui.HAIRLINE), outlineVariant = Color(Ui.LINE),
            error = Color(Ui.ERR), onError = Color.White,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * 调试钩子：强制把玻璃件画成"按下"态。
 *
 * <p>为什么需要它：华为/荣耀 ROM 上 `adb shell input motionevent DOWN` / `input swipe` **驱动不了
 * Compose 的按下态**（实测按住 700ms 截屏，整块区域变化像素 = 0；同一手法在模拟器上也不可靠），
 * 于是"按压反馈到底渲染成什么样"没法用 adb 验证。用
 * `adb shell am start -n <activity> --ez glass_force_press true` 即可强制按下态截屏取证。
 * **默认 false，不改变任何正常逻辑。**
 */
object GlassDebug {
    @JvmField
    var forcePressed: Boolean = false
    /**
     * [V3.51] 强制把目标卡的**任务列表展开**（它平时要**点一下卡片**才展开）。
     *
     * <p>为什么要它：真机**不接受注入点击** ⇒ "点开后任务条是什么形状/质感"没法用 adb 取到，
     * 而用户就是在这一步反馈"任务胶囊没做好"。有了它，`--ez glass_force_plan true` 就能直接截到展开态。
     */
    @JvmField
    var forcePlanExpanded: Boolean = false
    /**
     * 阴影位图是否用**半分辨率**（[V3.33] 的性能开关，**默认 true = 出货行为**）。
     *
     * <p>为什么要留这个开关：`glassShadow` 的实现改动（全分辨率 ↔ 半分辨率）只看代码没法证明
     * "肉眼无差"，而两次独立启动的截图**连滚动位置都不一定一致**（对话页首帧有空列表竞态），
     * 跨构建对比根本站不住。有了它就能**同一份 APK** 跑两次、只差这一个变量：
     * `adb shell am start -n … --ez glass_shadow_fullres true` 走全分辨率（旧实现），否则半分辨率。
     */
    @JvmField
    var shadowHalfRes: Boolean = true
    /**
     * [V3.44] 强制把 [LocalGlassScrolling] 置真。
     *
     * 为什么要它：`isScrollInProgress` 只在**拖动的那些帧**为真，`adb` 抓到的静态图基本抓不到；
     * 有了它就能在**同一个滚动位置**做 A/B（阴影开 ↔ 关），差异图里应当**只剩卡片边缘的阴影带**。
     */
    @JvmField
    var forceScrolling: Boolean = false
    /**
     * [V3.46] 强制渲染「↓ 回到底部」圆钮（它平时只在 `canScrollForward` 时出现）。
     *
     * 为什么必须留这个口子：真机**不接受注入手势**（`input swipe` 无效），而进会话默认落在底部 ⇒
     * `canScrollForward == false` ⇒ **这个按钮根本不渲染** ⇒ 它的"新增玻璃件会不会崩"就**没法在真机测**。
     */
    @JvmField
    var forceJumpButton: Boolean = false
}

/**
 * [V3.44 · V3.32 §2.4②] **列表是否正在滚动**（CompositionLocal）。
 *
 * 用途：滚动进行中让**列表行**跳过"自绘阴影"——那张阴影位图要**现建**（UI 线程箱式模糊）再上传 GPU，
 * 而滚动时行在不停复用/重建 ⇒ 这两笔正是本项目真机 jank 的两个最大头
 * （`dumpsys gfxinfo`：`Slow issue draw commands 395` / `Slow bitmap uploads 123`）。**停手即恢复**。
 *
 * 只提供 / 读取它的地方受影响：`CompositionLocalProvider` 变值只让**读它的 composable**（这里就是
 * `MsgCard`）重组，不会把整个列表重组一遍。默认 `false` ⇒ 没提供它的屏行为完全不变。
 */
val LocalGlassScrolling = androidx.compose.runtime.compositionLocalOf { false }

/**
 * 【V3.26 · 用户报②「按钮的阴影未补全，建议先创建模板调试无误，再给所有玻璃按钮增加」】
 *
 * 玻璃件**悬浮阴影的唯一出处**（Compose 侧）。口径与 Java 侧 {@link com.dsh.mobile.ui.Ui#shadowed}
 * **完全对齐**：
 *
 * 1. **颜色**：`Ui.GLASS_SHADOW`（`0x3D1A1042` = rgba(26,16,66) ≈24%，**紫调、不是黑**）——
 *    V3.26 之前抽屉圆钮那句 `.shadow(3.dp, clip=false)` 用的是 Compose 默认**黑**投影，
 *    在浅色玻璃底上就是一圈"脏黑边"（用户原话「有一圈黑边」）。
 * 2. **两级内缩**：按钮 6dp（= `Ui.BTN_SHADOW_PAD`）/ 卡片 18dp（= `Ui.CARD_SHADOW_PAD`）。
 * 3. Java 侧是"在形状外画多圈"的实现；Compose 侧用 `Modifier.shadow` 的自定义 ambient/spot 色等价
 *    （API 28+；低于 28 时 Android 会退回系统灰，可接受）。
 *
 * ⚠️ **用法**：阴影必须排在 `clip/background` **之前**（同 V3.21 的按压双影教训：修饰符外侧先画）。
 */
object GlassShadow {
    /**
     * 按钮/胶囊类（圆钮、chip、发送键…）：阴影外扩 **5dp**。
     * [V3.26 用户三轮] 初版 6dp →「可以调大点」→ 10dp →「整体阴影过大**缩小一半**」⇒ 回到 **5dp**。
     */
    val ButtonPad: Dp = 5.dp
    /**
     * 卡片/大面板类：阴影外扩 **10dp**。
     * [V3.26 用户] 曾按 Java 口径给 18dp，用户看后「阴影扩散过大」⇒ 砍半到 10dp。
     */
    val CardPad: Dp = 7.dp

    /** 投影色（与 Java 侧同源；深色主题下由 Ui 切成深色档）。 */
    val color: Color get() = Color(com.dsh.mobile.ui.Ui.GLASS_SHADOW)

    /**
     * **柔和档**投影色：[V3.26 用户「整体要柔和些不要太强」] = 基色的 70%。
     * 模板默认用它；将来若要更重，改这一处即可（配合 [GlassShadow.ButtonPad] / [CardPad]）。
     */
    val softColor: Color
        get() = Color(com.dsh.mobile.ui.Ui.GLASS_SHADOW).let {
            it.copy(alpha = it.alpha * 0.7f)
        }
}

/**
 * 阴影实现（**唯一处**）：**自己生成模糊投影位图 + `clipOutPath` 只画在形状外**
 * —— 这是 Java 侧 `Ui.ShadowedDrawable` 的忠实移植。
 *
 * ## 五轮试错留下的结论（改之前务必读完，别再走回头路）
 * | 方案 | 跟随形状 | 不被节点裁 | 不进内部 | 用户实际反馈 |
 * |---|---|---|---|---|
 * | `Modifier.shadow`（大 elevation） | ✓ | ✓ | ✗ | "下缘深灰带" |
 * | 形状外**多圈描边** | ✓ | ✓ | ✓ | "**双层框**"（紧贴形状那圈最重） |
 * | `Modifier.dropShadow`（真模糊） | ✓ | ✓ | ✗ | "**内部也有影响**" |
 * | `dropShadow` + 离屏层 + `Clear` | ✓ | **✗** | ✓ | "**边缘裁剪 / 方形切角**"（离屏层尺寸=节点尺寸） |
 * | `Modifier.shadow`（小 pad + 低 alpha） | ✓ | ✓ | ✗ | 平台投影对圆角是 **N 边形近似** ⇒ "内部有很淡的**8 边形**阴影边缘" |
 * | ✅ **自绘模糊位图 + `clipOutPath`（本实现）** | ✓ | ✓ | ✓ | ①位图可画到节点外（**没有层，不会裁**）；②`clipOutPath` 把绘制范围精确限制在**形状外** ⇒ 内部一点都沾不到 |
 *
 * 关键点：`clipPath/clipOutPath` 是**绘制裁剪**，不需要离屏层 ⇒ 前面几版的矛盾自然消失。
 */
private fun Modifier.glassShadow(shape: Shape, pad: Dp): Modifier = this.drawWithCache {
    val padPx = pad.toPx()
    val dy = padPx * 0.42f                       // 影子往下投（左上受光）
    val w = size.width
    val h = size.height
    val bwFull = (w + padPx * 2f).toInt().coerceAtLeast(1)
    val bhFull = (h + padPx * 2f + dy).toInt().coerceAtLeast(1)
    // [V3.33 · V3.32 §2.4①（低成本高收益那条）] 阴影位图**半分辨率**：
    //   按 1/2 边长建位图、模糊半径同比例减半，绘制时用 `dstSize` 放大回去。
    //   收益：**像素量与 GPU 上传量 ≈ 1/4**。本项目真机 jank 的最大两个头正是这张逐元素位图
    //   （`dumpsys gfxinfo`：`Slow issue draw commands 395` + `Slow bitmap uploads 123`）。
    //   安全性：模糊是低频信号 ⇒ 放大回来看不出差（对照 `evidence/v3330/060 vs 062`）。
    //   ⚠️ 只改采样精度，**不动** pad / 模糊系数 / 颜色 —— 观感参数一个没动（SOP：一次 ≤2 个质感数值）。
    val ss = if (GlassDebug.shadowHalfRes) 0.5f else 1f   // 1f = 旧实现（全分辨率），用于同包 A/B 取证
    val bw = (bwFull * ss).toInt().coerceAtLeast(1)
    val bh = (bhFull * ss).toInt().coerceAtLeast(1)
    val bmp = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
    val outline = shape.createOutline(size, layoutDirection, this)
    val cPath = Path().apply { addOutline(outline) }
    val aPath = cPath.asAndroidPath()

    // ① 把"形状（下移 dy）"填上投影色 —— 放进带 pad 留白的位图里
    run {
        val cc = androidx.compose.ui.graphics.Canvas(bmp.asImageBitmap())
        val filler = androidx.compose.ui.graphics.Paint().apply { color = GlassShadow.softColor }
        cc.save()
        // 先 scale 后 translate（Compose/Android 的矩阵是**后调用者先作用**）⇒ p ↦ (p + pad) * ss
        cc.scale(ss, ss)
        cc.translate(padPx, padPx + dy)
        cc.drawPath(cPath, filler)
        cc.restore()
    }
    // ② 模糊（可分箱式模糊，同 Java 侧 boxBlur）；半径随位图等比缩小
    boxBlurBitmap(bmp, (padPx * 0.9f * ss).toInt().coerceAtLeast(1))   // [V3.26 用户「过渡再自然点」] 模糊加大 ⇒ 过渡更柔

    // ③ **把"形状内部"从位图里挖掉**（在**位图生成期**做一次）⇒ **绘制期不再需要任何 `clipPath`**。
    //   [V3.46 · 卡顿] 非矩形 `clipPath` 在 HWUI 里要靠 stencil / `saveLayer` 实现，而对话页有 ~10 个阴影件
    //   ⇒ 原实现是**每帧每个阴影件一次裁剪**。搬到生成期后，绘制只剩一次**无裁剪**的 `drawImage`。
    //   画面**完全等价**（顺序：填色 → 模糊 → 清内部）：原来"绘制期裁掉**未位移**的形状"，
    //   现在是"位图里清掉**未位移**的形状"，可见结果相同；模糊往形状内的溢散也一并被清掉。
    run {
        val cc = androidx.compose.ui.graphics.Canvas(bmp.asImageBitmap())
        val clear = androidx.compose.ui.graphics.Paint().apply {
            color = androidx.compose.ui.graphics.Color.Transparent
            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
        }
        cc.save()
        cc.scale(ss, ss)
        cc.translate(padPx, padPx)     // ⚠️ **不加 dy**：要清掉的是"形状本身"，不是下移后的那一份
        cc.drawPath(cPath, clear)
        cc.restore()
    }

    val shadowImg = bmp.asImageBitmap()
    onDrawBehind {
        // 半分辨率位图 → 用 dstSize 放大到"原始逻辑尺寸"，落点与旧实现一致（左上角 -pad）
        // [V3.46] **没有 clipPath**：内部已经在生成期被清空
        drawImage(
            image = shadowImg,
            srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
            srcSize = androidx.compose.ui.unit.IntSize(bmp.width, bmp.height),
            dstOffset = androidx.compose.ui.unit.IntOffset(
                (-padPx).roundToInt(), (-padPx).roundToInt()),
            dstSize = androidx.compose.ui.unit.IntSize(bwFull, bhFull),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
        )
    }
}

/** 可分箱式模糊：横一遍、竖一遍（与 Java 侧 `Ui.boxBlur` 同一算法）。 */
private fun boxBlurBitmap(bmp: android.graphics.Bitmap, r: Int) {
    if (r <= 0) return
    val w = bmp.width
    val h = bmp.height
    if (w <= 0 || h <= 0) return
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    val tmp = IntArray(w * h)
    val n = 2 * r + 1
    for (y in 0 until h) {                       // 横向
        val base = y * w
        var sa = 0; var sr = 0; var sg = 0; var sb = 0
        for (i in -r..r) {
            val c = px[base + i.coerceIn(0, w - 1)]
            sa += c ushr 24; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
        }
        for (x in 0 until w) {
            tmp[base + x] = ((sa / n) shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
            val out = px[base + (x - r).coerceIn(0, w - 1)]
            val inn = px[base + (x + r + 1).coerceIn(0, w - 1)]
            sa += (inn ushr 24) - (out ushr 24)
            sr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
            sg += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
            sb += (inn and 0xFF) - (out and 0xFF)
        }
    }
    for (x in 0 until w) {                       // 纵向
        var sa = 0; var sr = 0; var sg = 0; var sb = 0
        for (i in -r..r) {
            val c = tmp[i.coerceIn(0, h - 1) * w + x]
            sa += c ushr 24; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
        }
        for (y in 0 until h) {
            px[y * w + x] = ((sa / n) shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
            val out = tmp[(y - r).coerceIn(0, h - 1) * w + x]
            val inn = tmp[(y + r + 1).coerceIn(0, h - 1) * w + x]
            sa += (inn ushr 24) - (out ushr 24)
            sr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
            sg += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
            sb += (inn and 0xFF) - (out and 0xFF)
        }
    }
    bmp.setPixels(px, 0, w, 0, 0, w, h)
}

/** 玻璃**按钮**阴影（模板；见 [GlassShadow] 的说明）。`shape` 必须与按钮自身形状一致。 */
fun Modifier.glassButtonShadow(shape: Shape): Modifier = glassShadow(shape, GlassShadow.ButtonPad)

/** 玻璃**卡片**阴影（模板；内缩更大、更柔）。 */
fun Modifier.glassCardShadow(shape: Shape): Modifier = glassShadow(shape, GlassShadow.CardPad)

/**
 * 玻璃件的**按压反馈**：按下「**变暗 + 缩小**」，松手回弹。
 *
 * ## 为什么要有这一版（2026-10-08 用户报「按钮按下去没有效果」）
 * 上一版只有 `graphicsLayer { scale = 0.98 }` 一条路：
 * 真机实测**按下的整块区域变化像素 = 0**（`evidence/v210/33_real_addcard_pressed.png` vs `30_…`），
 * 也就是说 2% 缩放这件事在真机玻璃件上基本看不出来（玻璃还要重采样背景，位移被"吃掉"了）。
 * 现在改成两条腿一起走：
 * 1. **变暗**：在内容之上再刷一层黑（默认 12%），**按 [corner] 圆角裁剪** —— 不会被当成
 *    上一轮那个"方角灰块"（`glassTap` 说明里记的那次事故）；
 * 2. **缩小**：默认 **0.96**（4%，比 2% 明显），并且用 `animateFloatAsState` 过渡 ——
 *    动画值变化会触发重组、生成新的 Modifier，不依赖 `graphicsLayer` 内部的状态观察。
 *
 * @param corner 压暗层的圆角。默认 999dp → 由绘制期 `min(corner, 短边/2)` 夹成胶囊/正圆；
 *   方形卡片请传它自己的圆角（例如设备卡传 28dp），否则压暗层会在圆角外露出方角。
 */
@Composable
fun Modifier.glassTap(
    corner: Dp = 999.dp,
    scale: Float = 0.96f,
    dim: Float = 0.12f,
    /**
     * [V3.24] 可选**长按**回调（默认 null = 行为与以前**完全一致**）。
     * 加它是因为「生成物」那一行的旧交互是"点=应用内预览、长按=下载/复制路径"，
     * 搬进玻璃列表后不能把这个入口丢掉。
     *
     * ⚠️ **必须排在 [onClick] 之前**：Kotlin 的尾随 lambda 会绑到**最后一个**参数，
     * 若把 `onLongClick` 放末尾，所有 `.glassTap { … }` 的老写法都会报
     * `No value passed for parameter 'onClick'`（本轮实测踩到）。
     */
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed0 by src.collectIsPressedAsState()
    val pressed = pressed0 || GlassDebug.forcePressed
    // 按下快、回弹稍慢，手感更"有回应"
    val f by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = if (pressed) 70 else 140),
        label = "glassPress",
    )
    val cornerPx = with(LocalDensity.current) { corner.toPx() }
    return this
        .graphicsLayer {
            val s = 1f - (1f - scale) * f
            scaleX = s
            scaleY = s
        }
        .drawWithContent {
            drawContent()
            if (f > 0.01f) {
                val r = minOf(cornerPx, size.minDimension / 2f)
                drawRoundRect(
                    color = Color.Black.copy(alpha = dim * f),
                    cornerRadius = CornerRadius(r, r),
                    size = size,
                )
            }
        }
        .let { m ->
            if (onLongClick != null) {
                m.combinedClickable(
                    interactionSource = src, indication = null,
                    onClick = onClick, onLongClick = onLongClick,
                )
            } else {
                m.clickable(interactionSource = src, indication = null, onClick = onClick)
            }
        }
}
