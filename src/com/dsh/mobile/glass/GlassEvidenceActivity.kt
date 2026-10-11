package com.dsh.mobile.glass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 【折射观察窗 · 取证页】按《GlassUI_SPEC_折射观察窗.md》V1.0 搭建，用于逐条核对 §5 自检清单。
 *
 * 背景严格照 §1.2：彩色对角渐变 + 白网格（10dp 粗 / 160dp 间距）+ 柔边光斑，
 * 且内容可滚动（检验"玻璃内外网格线同一条、连续、不错位"）。
 */
class GlassEvidenceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Evidence() }
    }
}

@Composable
private fun Evidence() {
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        // 背景内容层：被玻璃取样（layerBackdrop 记录）
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            GridBackground()
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(top = 120.dp, bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(14) { i ->
                    Text(
                        "可滚动内容 #${i + 1} —— 滚动时玻璃内外的网格线应同一条且连续",
                        color = Color(0xFF14141C),
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 22.dp),
                    )
                }
            }
        }
        // 玻璃层：折射观察窗（Token 全部走 RefractionWindow 的默认值 = §1.1 锁定值）
        RefractionWindow(
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(top = 300.dp, end = 16.dp)
                .size(168.dp),
        ) {
            Text("折射观察窗", fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14141C))
        }
    }
}

/** §1.2 背景：彩色对角渐变 + 白网格 10dp 粗 / 160dp 间距 + 柔边光斑。 */
@Composable
private fun GridBackground() {
    val colors = listOf(
        Color(0xFF7C4DFF), Color(0xFF3A8CFF), Color(0xFF00BFA5),
        Color(0xFF7CB342), Color(0xFFFFC107), Color(0xFFFF7043), Color(0xFFEC407A),
    )
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.linearGradient(colors, start = Offset.Zero, end = Offset(size.width, size.height)))
            // 柔边光斑
            listOf(
                Triple(0.22f, 0.18f, Color(0x66FFFFFF)),
                Triple(0.78f, 0.42f, Color(0x55FFEB3B)),
                Triple(0.35f, 0.72f, Color(0x5540C4FF)),
            ).forEach { (fx, fy, c) ->
                drawCircle(c, radius = size.minDimension * 0.18f,
                    center = Offset(size.width * fx, size.height * fy))
            }
            // 白网格：10dp 粗 / 160dp 间距（高频细节 —— 玻璃"读得出来"的前提）
            val step = 160.dp.toPx()
            val w = 10.dp.toPx()
            var x = 0f
            while (x <= size.width) { drawRect(Color(0xE6FFFFFF), Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height)); x += step }
            var y = 0f
            while (y <= size.height) { drawRect(Color(0xE6FFFFFF), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, w)); y += step }
        }
    )
}