// ============================================================================
//  RefractionWindow.kt —— 「折射观察窗」玻璃件（已锁定 V1.0 / 2026-10-08）
//  规范：docs/GlassUI/GlassUI_SPEC_折射观察窗.md
//  报告：docs/GlassUI/GlassUI_Report_V1.0.md
//  实测：F:\AI\程序开发\液态玻璃引擎Demo\shots\official_02.png（模拟器实跑；真机 ⏳ 未验）
// ----------------------------------------------------------------------------
//  【使用须知】
//  1) 依赖：io.github.kyant0:backdrop:2.0.1（Maven Central）。
//     AndroidLiquidGlass 本体在 JitPack：com.github.Kyant0:AndroidLiquidGlass
//  2) 要求 Android 13+（AGSL RuntimeShader）；本文件只在 API 33+ 生效，
//     低版本请自行回落（本 App 现有 Frost 软件路径）。
//  3) **背景必须由 Modifier.layerBackdrop(backdrop) 记录**，即"玻璃背后的内容"
//     要和玻璃在同一个 backdrop 作用域里；否则取不到样。
//  4) **背景必须有高频细节**（粗白网格/光斑）。纯渐变背景上，磨砂与折射都看不出来，
//     玻璃会渲染成"一块平色块"——本项目为此返工过，不要再踩。
//  5) 参数已锁定：改任何一项都必须出一份《GlassUI迭代报告》。
// ============================================================================
package com.dsh.mobile.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

// ---------------------------------------------------------------- 锁定 Token
/** 玻璃件质感 Token（**唯一允许调整的一组数值**；改动即需迭代报告）。 */
object RefractionWindowTokens {
    /** 圆角。布局参数，调用处可覆盖；26dp 是锁定形态。 */
    val Radius: Dp = 26.dp

    /**
     * 磨砂半径。
     *
     * [V1.9 · 用户报「磨砂过大，看不到玻璃变形和色散」] 12dp → **6dp**。
     * 12dp 时模糊半径已经大于折射幅度的一半，边缘的形变与 RGB 彩边被糊平成一片白雾 ——
     * 观感就是"一块半透明白板"，玻璃最要紧的两条线索（形变 / 色散）全丢了。
     * 6dp 保留可辨认的磨砂，同时把形变与彩边放出来。
     */
    val BlurRadius: Dp = 6.dp

    /** 折射环带高（多宽的边缘参与形变）。 */
    val RefractionHeight: Dp = 14.dp

    /**
     * 折射幅度。
     *
     * [V1.9] 22dp → **28dp**（环带:幅度 ≈ 1 : 2.0）。同一批调整里只有 blur + 本值两个质感数值，
     * 目的是把"玻璃在弯"这件事显示出来；色散强度由官方 shader 绑定在这个几何量上
     * （`lens(…, chromaticAberration = true)` 只有开关、没有独立强度参数），所以只能这样给。
     */
    val RefractionAmount: Dp = 28.dp

    /** 色散（RGB 分离彩边）。官方内置能力，关掉会明显更"数码感"。 */
    const val ChromaticAberration: Boolean = true

    /** 向心法线叠加。观察窗要的是边缘弯曲而非球面放大，故关。 */
    const val DepthEffect: Boolean = false

    /**
     * [V3.49 · 用户「检测滑动速度，**高速滑动时临时降低**液态玻璃的模糊和折射强度，滑动结束立刻恢复；
     * 慢速滑动保持完整效果，用户无感知」]
     *
     * **效果档**：`1f` = 完整（默认，**不改变任何既有观感**）；`< 1f` = 临时降档。
     * 只缩放两笔最重的开销 —— ① 模糊半径、② 折射幅度（lens）；并在明显降档时**关掉色散**
     * （`chromaticAberration` 是另一笔重开销，也是"边缘 RGB 分离"的来源）。
     *
     * 由**页面**驱动（见 `GlassChatScreen` 里按滚动速度改这一档），玻璃件本体一行逻辑没动。
     */
    private val qualityState = androidx.compose.runtime.mutableStateOf(1f)
    var quality: Float
        get() = qualityState.value
        set(value) { qualityState.value = value }

    /**
     * [V3.52 · 用户「**磨砂玻璃那个，把现在所有玻璃组件取消折射这些比较耗资源的，
     * 然后改成 80% 白色的微透磨砂玻璃就行，整体替换**」]
     *
     * `true` = 磨砂档：`RefractionWindow` **整个不走 `lens`**（逐像素位移采样，最耗资源那笔），
     * 体色强制 **80% 白**（深色档 80% `#14121C`），只留模糊 + 受光环 ⇒ 就是用户要的"微透磨砂玻璃"。
     *
     * 由 `Ui.setGlassMode()` 设置（Java → `RefractionWindowTokens.INSTANCE.setFrostMode(...)`），
     * 是 **snapshot state** ⇒ 切换时所有玻璃件**就地重组**，不需要重启。
     */
    private val frostState = androidx.compose.runtime.mutableStateOf(false)
    var frostMode: Boolean
        get() = frostState.value
        set(value) { frostState.value = value }

    /**
     * 玻璃体色（"体色"= 压在糊掉背景之上的一层半透明色）。
     *
     * [V2.0 · 真机暗色主题实测] 原为写死 `White 20%` —— 那是**只在浅色主题下调出来的值**。
     * 2026-10-08 装到真机（PGT-AN10，系统暗色）实测「添加设备」玻璃卡：
     * <pre>
     *   卡内亮度 192.4 / 卡外 83.4 → 差 **+109**（几乎不透明白，白字融进底色）
     *   同一位置正式版正式页：    127.1 / 83.4 → 差   +44（正常玻璃）
     *   模拟器（浅色）：          188.3 / 183.6 → 差  +4.6（一直没问题 → 前几轮全漏了暗色）
     * </pre>
     * 根因：**正式版的玻璃体色本来就是主题两套**（`Ui.GLASS_CARD`：浅色 `0x42FFFFFF` 白 26% /
     * 暗色 `0x4D14121C` **深色 30%**；`GLASS_BAR` 白 15% / 深色 44%；`GLASS_INPUT` 白 48% / 深色 62%），
     * 暗色下把"白体"换成"深体"才能既透出背景又不烧白。本 Token 写死白体 → 暗色必烧。
     * 现值按正式版口径对齐：**浅色 White 20%（沿用锁定值）/ 暗色 #14121C 30%（= 正式版 GLASS_CARD 暗色档）**。
     */
    val Tint: Color
        get() = if (com.dsh.mobile.ui.Ui.isDark()) {
            Color(0xFF14121C).copy(alpha = 0.30f)
        } else {
            Color.White.copy(alpha = 0.20f)
        }

    /**
     * 边缘受光（白棱）的强度。
     *
     * [V1.9.1 · 用户报「白线跟着玻璃跑」] 原为官方 [Highlight.Default]（实测等效 **白 ≈60%**）。
     * 2026-10-08 在模拟器上量化（alpha 合成模型 `out = bg + a × (255 − bg)`，两处独立取样互相印证）：
     * <pre>
     *   Compose 玻璃（取证页，bg=116）→ 棱峰 199  ⇒ a ≈ 0.60
     *   Compose 玻璃卡（设备页，bg=181）→ 棱峰 224  ⇒ a ≈ 0.58
     *   正式版 FrostedPanelView 的 `hairline(0x1EFFFFFF)`（bg=190）→ 棱峰 226  ⇒ a ≈ 0.12
     * </pre>
     * 也就是说：**Compose 的白棱比 App 自己的玻璃语言强约 5 倍**。背景换成正式版配色底
     * （低频、无细节）之后，屏幕上唯一像"线"的东西就只剩这条棱 —— 观感成了"一条白线沿玻璃形状跑"。
     * 这里把它压到 **0.35**：0.20 试过，用户实测反馈「受光边变弱了这么多，加强点」——
     * 即 0.60（官方默认）太强成描边、0.20 又太弱丢了受光，0.35 取中（见三档对照取证）。
     *
     * <p>⚠️ 规范 §1.1 原写的是"Highlight 用官方默认" —— 本次属**受控偏离**，
     * 需用户签字后回写规范（见 `docs/GlassUI/GlassUI_Report_V1.9.1.md` §规范回写）。
     */
    const val HighlightAlpha: Float = 0.35f

    /** 预置：正方形观察窗边长。 */
    val SquareSize: Dp = 168.dp

    /**
     * 胶囊圆角（占位值）。**故意给一个超大数**，由组件按"短边/2"自动夹取 →
     * 结果就是真胶囊（半圆端），且不会出现"半径过大"的退化问题。
     * 输入框 / 药丸按钮用它，别自己写 999.dp。
     */
    val CapsuleRadius: Dp = 9999.dp
}

/**
 * 折射观察窗 / 通用玻璃面。
 *
 * **内部处理顺序锁死：blur → vibrancy → lens**（先糊后弯才是玻璃；反过来形变会被糊掉）。
 * highlight / shadow / innerShadow 的取用（**V1.9.1 起 highlight 有受控偏离，见下**）：
 *  - Highlight.Default.**copy(alpha = 0.20f)** → 边缘受光（左上受光 → 右下受光，整圈连贯）；
 *    官方默认的 alpha 实测等效白 ≈60%，在低频配色底上呈"一条白线沿玻璃跑"，故压到 0.20
 *    （理由与量化数据见 [RefractionWindowTokens.HighlightAlpha]）
 *  - Shadow.Default     → 柔和悬浮外阴影（官方默认，未改）
 *  - InnerShadow.Default→ 内侧暗棱（"厚度感"；去掉它就变成贴纸。官方默认，未改）
 *
 * ### ⚠️ 圆角红线（实测踩坑，务必看）
 * `radius` **必须 ≤ 短边/2**。一旦超过，圆角矩形会退化成极限态，
 * **形状不再覆盖玻璃节点 → 模糊/折射会外溢到整块节点矩形**，
 * 现象是"玻璃外面的背景也被虚化/变形了"（用户实测反馈的正是这个）。
 * 本组件已内置 `BoxWithConstraints` 自动夹取，调用处写多大都安全；
 * 想要胶囊请用 [RefractionCapsule]，不要自己传 999.dp。
 */
@Composable
fun RefractionWindow(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    radius: Dp = RefractionWindowTokens.Radius,
    blurRadius: Dp = RefractionWindowTokens.BlurRadius,
    refractionHeight: Dp = RefractionWindowTokens.RefractionHeight,
    refractionAmount: Dp = RefractionWindowTokens.RefractionAmount,
    tint: Color = RefractionWindowTokens.Tint,
    content: @Composable () -> Unit = {},
) {
    BoxWithConstraints(modifier) {
        // 【加固】半径自动夹取到 短边/2 —— 杜绝"半径过大 → 形状退化 → 效果外溢"
        val w = maxWidth.value
        val h = maxHeight.value
        val maxR = if (w.isFinite() && h.isFinite() && w > 0f && h > 0f) {
            minOf(w, h) / 2f
        } else {
            radius.value
        }
        val r = radius.value.coerceIn(0f, maxR)
        val shape = RoundedCornerShape(r.dp)
        // [V3.52 · 用户「磨砂玻璃那个…取消折射…改成 80% 白色的微透磨砂玻璃，**整体替换**」]
        //   磨砂档：**整屏所有玻璃件**都不走 lens，体色强制 80% 白 ⇒ 只剩"模糊 + 受光环"。
        val frost = RefractionWindowTokens.frostMode
        val effTint = if (frost) {
            if (com.dsh.mobile.ui.Ui.isDark()) Color(0xFF14121C).copy(alpha = 0.80f)
            else Color.White.copy(alpha = 0.80f)
        } else tint
        Box(
            Modifier.fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        // [V3.49] 按"效果档"缩放两笔最重的开销（默认档 = 1f ⇒ 与以前完全一致）
                        val q = RefractionWindowTokens.quality.coerceIn(0.2f, 1f)
                        blur(blurRadius.toPx() * q)             // ① 磨砂
                        vibrancy()                              // ② 糊后补亮度/饱和（防发灰）
                        if (!frost) {                           // ③ 折射（磨砂档整段不做）
                            lens(
                                refractionHeight = refractionHeight.toPx() * q,
                                refractionAmount = refractionAmount.toPx() * q,
                                depthEffect = RefractionWindowTokens.DepthEffect,
                                // 色散只在完整档开（降档时省掉这笔重开销）
                                chromaticAberration = RefractionWindowTokens.ChromaticAberration && q > 0.95f,
                            )
                        }
                    },
                    highlight = { Highlight.Default.copy(alpha = RefractionWindowTokens.HighlightAlpha) },
                    shadow = { Shadow.Default },
                    innerShadow = { InnerShadow.Default },
                )
                .background(effTint, shape),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/**
 * 真·胶囊（输入框 / 药丸按钮）：圆角自动 = 短边/2（半圆端）。
 * 等价于 `RefractionWindow(radius = 短边/2)`，但**不用你自己算圆角**，
 * 高度变了也不会失效（这是踩过 `radius = 999.dp` 坑之后的推荐入口）。
 */
@Composable
fun RefractionCapsule(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    blurRadius: Dp = RefractionWindowTokens.BlurRadius,
    refractionHeight: Dp = RefractionWindowTokens.RefractionHeight,
    refractionAmount: Dp = RefractionWindowTokens.RefractionAmount,
    tint: Color = RefractionWindowTokens.Tint,
    content: @Composable () -> Unit = {},
) = RefractionWindow(
    backdrop = backdrop,
    modifier = modifier,
    radius = RefractionWindowTokens.CapsuleRadius,   // 交由组件夹取 = 短边/2
    blurRadius = blurRadius,
    refractionHeight = refractionHeight,
    refractionAmount = refractionAmount,
    tint = tint,
    content = content,
)

// ---------------------------------------------------------------- 用法示例
/*
@Composable
fun SamplePage() {
    val backdrop = rememberLayerBackdrop()          // 整页一个即可

    Box(Modifier.fillMaxSize()) {
        // 【必须】背景内容层：这一层被玻璃取样
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            HighFrequencyBackground()               // 粗白网格 + 光斑（见规范 §1.2）
            ScrollableContent()                     // 可滚动 → 自动满足⑤滚动同步
        }

        // 折射观察窗（位置/尺寸是布局参数，可改；质感 Token 不许改）
        RefractionWindow(
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 300.dp, end = 16.dp)
                .size(RefractionWindowTokens.SquareSize),
        ) {
            Text("折射观察窗", fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14141C))
        }
    }
}
*/
