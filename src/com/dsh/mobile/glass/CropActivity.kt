package com.dsh.mobile.glass

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.mobile.ui.Ui
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * [V3.77 · 用户「图片裁剪界面 —— 我选 **A（自由裁剪）**」]
 *
 * 独立裁剪页：原图居中显示 + 一个**可整体拖动、四角可拉**的裁剪框；确认后按框裁剪，
 * **保持原分辨率**，结果写进 `cacheDir/crop-*.jpg`，把 `file://` Uri 通过 [EXTRA_OUT] 回给宿主，
 * 由宿主 `stageAttachment(uri, KIND_IMAGE)` 走既有的"暂存 → 与文字一起发送"流程。
 *
 * <p>为什么自绘而不用系统裁剪：`com.android.camera.action.CROP` 早已不是标准 API，
 * 各 ROM 行为不一（真机实测常见"点完没反应"）。自绘只依赖 Compose，行为可控。
 *
 * <p>坐标口径（**容易写错的地方**）：裁剪框的四元组 `x/y/w/h` 一律是
 * **"图片显示区"的像素坐标**（图片按 `scale = min(区宽/原宽, 区高/原高)` 缩放后的 dw×dh）——
 * 回原图时只需除以同一个 `scale`，不做居中偏移运算。
 */
class CropActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SRC = "src"   // 源图（content:// 或 file://）
        const val EXTRA_OUT = "out"   // 结果（file://）
    }

    private var loaded: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.getStringExtra(EXTRA_SRC) ?: ""
        // [V3.107 · 发布前检查 P1] 原实现在**主线程**整图解码 ✗ ⇒ 大图（含相机原图）会卡住/ANR。
        //   ⇒ 解码挪到 IO 线程；解码与 UI 都通过 runOnUiThread 回主线程。
        Thread({
            val bmp = runCatching {
                if (uri.startsWith("file://")) BitmapFactory.decodeFile(uri.removePrefix("file://"))
                else contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            runOnUiThread {
                if (bmp == null) {
                    Toast.makeText(this, "读不到这张图片", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    loaded = bmp
                    setContent { CropScreen(bmp) { out -> done(out) } }
                }
            }
        }, "crop-decode").start()
    }

    private fun done(cropped: Bitmap?) {
        if (cropped == null) { setResult(Activity.RESULT_CANCELED); finish(); return }
        val f = File(cacheDir, "crop-${System.currentTimeMillis()}.jpg")
        // [V3.107 · 发布前检查 P1] JPEG 压缩同样**不能**在主线程（1080 图约几十毫秒、大图更久）
        Thread({
            val ok = runCatching {
                FileOutputStream(f).use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            }.isSuccess
            runOnUiThread {
                if (ok) {
                    setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_OUT, "file://" + f.absolutePath))
                } else {
                    setResult(Activity.RESULT_CANCELED)
                }
                finish()
            }
        }, "crop-encode").start()
    }
}

@Composable
private fun CropScreen(src: Bitmap, onDone: (Bitmap?) -> Unit) {
    // 裁剪框（单位：图片显示区像素）
    var dw by remember { mutableStateOf(0f) }
    var dh by remember { mutableStateOf(0f) }
    var fx by remember { mutableStateOf(0f) }
    var fy by remember { mutableStateOf(0f) }
    var fw by remember { mutableStateOf(0f) }
    var fh by remember { mutableStateOf(0f) }

    Column(Modifier.fillMaxSize().background(Color(0xFF101014))) {
        Row(
            // [V3.80 · 用户「上方**取消和完成太高了，到手机状态栏上面了**，往上挪挪」]
            //   ⇒ edge-to-edge 下内容会画到状态栏底下，这里显式让开状态栏（+ 底部手势条）。
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Btn("取消") { onDone(null) }
            Spacer(Modifier.width(10.dp))
            Text("拖框体 / 拉四角", color = Color.White.copy(alpha = 0.75f), fontSize = Ui.S_CAP1.sp)
            Spacer(Modifier.weight(1f))
            Btn("完成") {
                if (dw <= 0f || dh <= 0f || fw <= 1f || fh <= 1f) { onDone(null); return@Btn }
                val s = src.width / dw                       // 显示 → 原图 的比例（宽高同比例）
                val cx = (fx / s).roundToInt().coerceIn(0, src.width - 1)
                val cy = (fy / s).roundToInt().coerceIn(0, src.height - 1)
                val cw = (fw / s).roundToInt().coerceIn(1, src.width - cx)
                val ch = (fh / s).roundToInt().coerceIn(1, src.height - cy)
                onDone(runCatching { Bitmap.createBitmap(src, cx, cy, cw, ch) }.getOrNull())
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val availW = with(density) { maxWidth.toPx() }
            val availH = with(density) { maxHeight.toPx() }
            val scale = min(availW / src.width, availH / src.height)
            val w = src.width * scale
            val h = src.height * scale
            // 首次进入：把框设成图片中央 80%
            if (dw == 0f) {
                dw = w; dh = h
                fw = w * 0.8f; fh = h * 0.8f
                fx = (w - fw) / 2f; fy = (h - fh) / 2f
            }
            Box(
                Modifier.width(with(density) { w.toDp() }).height(with(density) { h.toDp() }),
            ) {
                Image(
                    bitmap = src.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
                // [V3.80 · 用户「裁剪建议**选框外变成灰色**，这样对比明显些」]
                //   框外压暗（上/下/左/右四块，随框实时变化）⇒ 选框内外对比明显。
                Box(Modifier.offsetPx(0f, 0f).size(with(density) { dw.toDp() }, with(density) { fy.toDp() })
                    .background(Color.Black.copy(alpha = 0.55f)))
                Box(Modifier.offsetPx(0f, fy + fh)
                    .size(with(density) { dw.toDp() }, with(density) { max(0f, dh - fy - fh).toDp() })
                    .background(Color.Black.copy(alpha = 0.55f)))
                Box(Modifier.offsetPx(0f, fy)
                    .size(with(density) { fx.toDp() }, with(density) { fh.toDp() })
                    .background(Color.Black.copy(alpha = 0.55f)))
                Box(Modifier.offsetPx(fx + fw, fy)
                    .size(with(density) { max(0f, dw - fx - fw).toDp() }, with(density) { fh.toDp() })
                    .background(Color.Black.copy(alpha = 0.55f)))
                // 框体：整体拖动
                Box(
                    Modifier
                        .offsetPx(fx, fy)
                        .size(with(density) { fw.toDp() }, with(density) { fh.toDp() })
                        .border(2.dp, Color.White, RoundedCornerShape(4.dp))
                        .pointerInput(Unit) {
                            detectDragGestures { c, d ->
                                c.consume()
                                fx = (fx + d.x).coerceIn(0f, max(0f, dw - fw))
                                fy = (fy + d.y).coerceIn(0f, max(0f, dh - fh))
                            }
                        },
                ) {
                    Handle(Alignment.TopStart) { dx, dy ->
                        val nx = (fx + dx).coerceIn(0f, fx + fw - 48f)
                        val ny = (fy + dy).coerceIn(0f, fy + fh - 48f)
                        fw -= (nx - fx); fh -= (ny - fy); fx = nx; fy = ny
                    }
                    Handle(Alignment.TopEnd) { dx, dy ->
                        fw = (fw + dx).coerceAtMost(dw - fx)
                        val ny = (fy + dy).coerceIn(0f, fy + fh - 48f)
                        fh -= (ny - fy); fy = ny
                    }
                    Handle(Alignment.BottomStart) { dx, dy ->
                        val nx = (fx + dx).coerceIn(0f, fx + fw - 48f)
                        fw -= (nx - fx); fx = nx
                        fh = (fh + dy).coerceAtMost(dh - fy)
                    }
                    Handle(Alignment.BottomEnd) { dx, dy ->
                        fw = (fw + dx).coerceAtMost(dw - fx)
                        fh = (fh + dy).coerceAtMost(dh - fy)
                    }
                }
            }
        }
    }
}

/** 把 dp 像素位移直接落到真实像素（裁剪框用像素坐标算，避免再换算一次）。 */
private fun Modifier.offsetPx(x: Float, y: Float): Modifier =
    this.offset { androidx.compose.ui.unit.IntOffset(x.roundToInt(), y.roundToInt()) }

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Handle(
    where: Alignment,
    onDrag: (Float, Float) -> Unit,
) {
    Box(
        Modifier.align(where).size(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.30f))
            .border(0.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                detectDragGestures { c, d -> c.consume(); onDrag(d.x, d.y) }
            },
    )
}

@Composable
private fun Btn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(38.dp).clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .border(0.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = Ui.S_BODY.sp,
            modifier = Modifier.padding(horizontal = 18.dp))
    }
}
