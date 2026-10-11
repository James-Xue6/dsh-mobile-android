// 【本文件与 App 内实现同步】App 侧路径：src/com/dsh/mobile/glass/RefractionWindow.kt
// 2026-10-08 加固：radius 自动夹取到 短边/2 + 新增 RefractionCapsule()（踩坑见规范 §6）
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

    /** 磨砂半径。官方 README 起步值 2dp 几乎没磨砂；12dp 是"糊得住又留住形变"的档位。 */
    val BlurRadius: Dp = 12.dp

    /** 折射环带高（多宽的边缘参与形变）。 */
    val RefractionHeight: Dp = 14.dp

    /** 折射幅度，与环带高约 1.5 : 1。 */
    val RefractionAmount: Dp = 22.dp

    /** 色散（RGB 分离彩边）。官方内置能力，关掉会明显更"数码感"。 */
    const val ChromaticAberration: Boolean = true

    /** 向心法线叠加。观察窗要的是边缘弯曲而非球面放大，故关。 */
    const val DepthEffect: Boolean = false

    /** 玻璃体白度：10% 像透明塑料，40% 死白丢色；20% 为锁定值。 */
    val Tint: Color = Color.White.copy(alpha = 0.20f)

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
 * highlight / shadow / innerShadow 全部使用官方默认：
 *  - Highlight.Default  → 边缘受光（左上受光 → 右下受光，整圈连贯）
 *  - Shadow.Default     → 柔和悬浮外阴影
 *  - InnerShadow.Default→ 内侧暗棱（"厚度感"；去掉它就变成贴纸）
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
        Box(
            Modifier.fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        blur(blurRadius.toPx())                 // ① 磨砂
                        vibrancy()                              // ② 糊后补亮度/饱和（防发灰）
                        lens(                                   // ③ 折射：边缘强、向中衰减
                            refractionHeight = refractionHeight.toPx(),
                            refractionAmount = refractionAmount.toPx(),
                            depthEffect = RefractionWindowTokens.DepthEffect,
                            chromaticAberration = RefractionWindowTokens.ChromaticAberration,
                        )
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow.Default },
                    innerShadow = { InnerShadow.Default },
                )
                .background(tint, shape),
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
