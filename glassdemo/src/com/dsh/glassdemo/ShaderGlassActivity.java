package com.dsh.glassdemo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import java.util.Random;

/**
 * 【路线 B 实证】不用 Compose，把官方 AGSL 折射着色器直接画在现有 View 上。
 * ===========================================================================
 * 为什么要做这个页面：官方库（backdrop / AndroidLiquidGlass）走的是 Compose。
 * 本 App（dsh-mobile-android）是 48 个 Java、0 个 Kotlin、免 Gradle 构建，
 * 为了用一块玻璃把整页改成 Compose + Gradle 代价太大。
 *
 * 路线 B 的思路：**库里的折射是 AGSL 着色器字符串**，而 `android.graphics.RuntimeShader`
 * 本身就是 `Shader` 的子类 → 可以直接交给 `RenderNode.setRenderEffect()` 使用，
 * 完全不依赖 Compose。效果与官方同源（同一段 shader），代码只依赖 API 33+。
 *
 * 本页把两种实现放在**同一张背景、同一份模糊、同样的白度/亮边**下同屏对照：
 *   左：路线 B —— 官方 AGSL 着色器（含色散）
 *   右：现有方案 —— 本项目软件 lens 引擎（Frost.applyLens）
 * 两者只差"折射怎么做"，其余全部相同 → 差异就是折射实现的差异。
 *
 * 锁定参数见 docs/GlassUI/GlassUI_SPEC_折射观察窗.md（圆角26dp/磨砂12dp/白度20%/折射14dp+22dp/开色散）
 */
public class ShaderGlassActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(new GlassAbView(this));
    }

    // ========================================================================
    //  官方 AGSL 着色器（原文取自 backdrop-android-2.0.1-sources.jar 的 Shaders.kt）
    //  Apache-2.0 · Copyright 2025 Kyant —— 移植必须保留此版权与许可声明
    // ========================================================================
    private static final String SDF = ""
            + "float radiusAt(float2 coord, float4 radii) {\n"
            + "  if (coord.x >= 0.0) {\n"
            + "    if (coord.y <= 0.0) return radii.y;\n"
            + "    else return radii.z;\n"
            + "  } else {\n"
            + "    if (coord.y <= 0.0) return radii.x;\n"
            + "    else return radii.w;\n"
            + "  }\n"
            + "}\n"
            + "float sdRoundedRect(float2 coord, float2 halfSize, float radius) {\n"
            + "  float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n"
            + "  float outside = length(max(cornerCoord, 0.0)) - radius;\n"
            + "  float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);\n"
            + "  return outside + inside;\n"
            + "}\n"
            + "float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {\n"
            + "  float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n"
            + "  if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n"
            + "    return sign(coord) * normalize(max(cornerCoord, 0.0));\n"
            + "  } else {\n"
            + "    float gradX = step(cornerCoord.y, cornerCoord.x);\n"
            + "    return sign(coord) * float2(gradX, 1.0 - gradX);\n"
            + "  }\n"
            + "}\n";

    /** 官方「带色散的圆角矩形折射」着色器（RoundedRectRefractionWithDispersionShaderString）。 */
    private static final String AGSL_REFRACTION_DISPERSION = ""
            + "uniform shader content;\n"
            + "uniform float2 size;\n"
            + "uniform float2 offset;\n"
            + "uniform float4 cornerRadii;\n"
            + "uniform float refractionHeight;\n"
            + "uniform float refractionAmount;\n"
            + "uniform float depthEffect;\n"
            + "uniform float chromaticAberration;\n"
            + SDF
            + "float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }\n"
            + "half4 main(float2 coord) {\n"
            + "  float2 halfSize = size * 0.5;\n"
            + "  float2 centeredCoord = (coord + offset) - halfSize;\n"
            + "  float radius = radiusAt(coord, cornerRadii);\n"
            + "  float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n"
            + "  if (-sd >= refractionHeight) { return content.eval(coord); }\n"
            + "  sd = min(sd, 0.0);\n"
            + "  float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;\n"
            + "  float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n"
            + "  float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius)\n"
            + "                          + depthEffect * normalize(centeredCoord));\n"
            + "  float2 refractedCoord = coord + d * grad;\n"
            + "  float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));\n"
            + "  float2 dispersedCoord = d * grad * dispersionIntensity;\n"
            + "  half4 color = half4(0.0);\n"
            + "  half4 red    = content.eval(refractedCoord + dispersedCoord);            color.r += red.r/3.5;    color.a += red.a/7.0;\n"
            + "  half4 orange = content.eval(refractedCoord + dispersedCoord*(2.0/3.0));  color.r += orange.r/3.5; color.g += orange.g/7.0; color.a += orange.a/7.0;\n"
            + "  half4 yellow = content.eval(refractedCoord + dispersedCoord*(1.0/3.0));  color.r += yellow.r/3.5; color.g += yellow.g/3.5; color.a += yellow.a/7.0;\n"
            + "  half4 green  = content.eval(refractedCoord);                             color.g += green.g/3.5;  color.a += green.a/7.0;\n"
            + "  half4 cyan   = content.eval(refractedCoord - dispersedCoord*(1.0/3.0));  color.g += cyan.g/3.5;   color.b += cyan.b/3.0; color.a += cyan.a/7.0;\n"
            + "  half4 blue   = content.eval(refractedCoord - dispersedCoord*(2.0/3.0));  color.b += blue.b/3.0;   color.a += blue.a/7.0;\n"
            + "  half4 purple = content.eval(refractedCoord - dispersedCoord);            color.r += purple.r/7.0; color.b += purple.b/3.0; color.a += purple.a/7.0;\n"
            + "  return color;\n"
            + "}\n";

    // ========================================================================
    //  A/B 同屏对照视图
    // ========================================================================
    static class GlassAbView extends View {

        // ---------- 锁定 Token（见 GlassUI_SPEC_折射观察窗.md §1.1）----------
        private static final float DP_RADIUS = 26f;        // 圆角
        private static final float DP_TILE = 168f;         // 玻璃块边长
        private static final float DP_BLUR = 12f;          // 磨砂
        private static final int TINT_ALPHA = 51;          // White 20% (0xFF*0.20)
        private static final float DP_REFR_H = 14f;        // 折射环带高
        private static final float DP_REFR_AMT = 22f;      // 折射幅度

        private final float d;
        private Bitmap bg;         // 原始背景（锐利，含粗白网格）
        private Bitmap blurred;    // 全屏模糊（两条路线共用的取样源）
        private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

        private RectF leftTile, rightTile;
        private float cornerPx;

        // 路线 B（官方 AGSL 着色器）
        private RuntimeShader shader;
        private RenderNode leftNode;
        private String routeBStatus = "未初始化";

        // 现有方案（软件 lens）
        private Bitmap rightLensBmp;
        private final Rect rightLensSrc = new Rect();

        GlassAbView(android.content.Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            build(w, h);
        }

        private void build(int w, int h) {
            cornerPx = DP_RADIUS * d;
            float tile = DP_TILE * d;
            float gap = 16 * d;
            float cx = w * 0.5f, cy = h * 0.44f;
            leftTile = new RectF(cx - gap * 0.5f - tile, cy - tile * 0.5f, cx - gap * 0.5f, cy + tile * 0.5f);
            rightTile = new RectF(cx + gap * 0.5f, cy - tile * 0.5f, cx + gap * 0.5f + tile, cy + tile * 0.5f);

            bg = makeBackground(w, h);
            blurred = makeBlurred(bg, 0.35f);

            buildRouteB((int) tile);
            buildRouteSoftware();
            invalidate();
        }

        // ---------------- 路线 B：官方 AGSL 着色器 ----------------
        private void buildRouteB(int tile) {
            if (Build.VERSION.SDK_INT < 33) {
                routeBStatus = "需要 Android 13+（API 33）";
                return;
            }
            try {
                shader = new RuntimeShader(AGSL_REFRACTION_DISPERSION);
                shader.setFloatUniform("size", tile, tile);
                shader.setFloatUniform("offset", 0f, 0f);              // 内容已按玻璃位置平移，故 0
                shader.setFloatUniform("cornerRadii", cornerPx, cornerPx, cornerPx, cornerPx);
                shader.setFloatUniform("refractionHeight", DP_REFR_H * d);
                shader.setFloatUniform("refractionAmount", DP_REFR_AMT * d);
                shader.setFloatUniform("depthEffect", 0f);
                shader.setFloatUniform("chromaticAberration", 1f);      // 开色散（官方能力）

                leftNode = new RenderNode("glassB");
                leftNode.setPosition(0, 0, tile, tile);
                // 关键：把官方着色器挂在 RenderNode 上，`content` 自动绑定为节点内容（= 玻璃背后的画面）
                leftNode.setRenderEffect(
                        RenderEffect.createRuntimeShaderEffect(shader, "content"));

                RenderNode rc = leftNode;
                android.graphics.RecordingCanvas c = rc.beginRecording(tile, tile);
                // 节点局部 (0,0) == 屏幕 (leftTile.left, leftTile.top) → 用平移把背景对齐进来
                c.drawBitmap(blurred, -leftTile.left, -leftTile.top, bmpPaint);
                rc.endRecording();
                routeBStatus = "RuntimeShader 就绪";
            } catch (Throwable t) {
                routeBStatus = "不可用: " + t.getClass().getSimpleName();
                shader = null;
                leftNode = null;
            }
        }

        // ---------------- 现有方案：软件 lens ----------------
        private void buildRouteSoftware() {
            int pad = Frost.padFor(Frost.BLUR_RADIUS, DP_REFR_H * d);
            int tw = (int) rightTile.width(), th = (int) rightTile.height();
            int x0 = (int) rightTile.left - pad, y0 = (int) rightTile.top - pad;
            int bw = tw + pad * 2, bh = th + pad * 2;
            if (x0 < 0 || y0 < 0 || x0 + bw > blurred.getWidth() || y0 + bh > blurred.getHeight()) return;
            int[] px = new int[bw * bh];
            blurred.getPixels(px, 0, bw, x0, y0, bw, bh);
            // 与本项目 FrostedPanelView 完全相同的折射参数（环带 28dp 是项目定版；这里用 14dp 与路线B对齐）
            Frost.applyLens(px, bw, bh, cornerPx, DP_REFR_H * d, DP_REFR_AMT * d, pad);
            rightLensBmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            rightLensBmp.setPixels(px, 0, bw, 0, 0, bw, bh);
            rightLensSrc.set(x0, y0, x0 + bw, y0 + bh);
        }

        // ---------------- 绘制 ----------------
        @Override
        protected void onDraw(Canvas cv) {
            if (bg == null) return;
            cv.drawBitmap(bg, 0, 0, bmpPaint);          // 锐利背景（玻璃外应保持清晰）

            // 左：路线 B
            if (leftNode != null) {
                int save = cv.save();
                cv.translate(leftTile.left, leftTile.top);
                cv.drawRenderNode(leftNode);
                cv.restoreToCount(save);
            }
            drawGlassOverlay(cv, leftTile);

            // 右：软件 lens
            if (rightLensBmp != null) {
                int save = cv.save();
                Path clip = new Path();
                clip.addRoundRect(rightTile, cornerPx, cornerPx, Path.Direction.CW);
                cv.clipPath(clip);
                cv.drawBitmap(rightLensBmp, rightLensSrc, rightTile, bmpPaint);
                cv.restoreToCount(save);
            }
            drawGlassOverlay(cv, rightTile);

            drawLabels(cv);
        }

        /** 两条路线**完全共用**的表面处理：白度 + 边缘受光 + 内侧暗棱 + 外阴影。 */
        private void drawGlassOverlay(Canvas cv, RectF r) {
            // 外阴影（柔和不实黑）
            Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
            sh.setColor(0x40000000);
            sh.setMaskFilter(new BlurMaskFilter(15 * d, BlurMaskFilter.Blur.NORMAL));
            cv.drawRoundRect(new RectF(r.left + 2 * d, r.top + 6 * d, r.right - 2 * d, r.bottom + 7 * d),
                    cornerPx, cornerPx, sh);
            // 玻璃白度
            Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
            tp.setColor(Color.argb(TINT_ALPHA, 255, 255, 255));
            cv.drawRoundRect(r, cornerPx, cornerPx, tp);
            // 边缘受光：整圈圆角环 + 对角渐变（左上白高光 → 中部最淡 → 右下受光）
            float eIn = 1.2f * d;
            Path out = new Path();
            out.addRoundRect(r, cornerPx, cornerPx, Path.Direction.CW);
            Path in = new Path();
            in.addRoundRect(new RectF(r.left + eIn, r.top + eIn, r.right - eIn, r.bottom - eIn),
                    Math.max(0, cornerPx - eIn), Math.max(0, cornerPx - eIn), Path.Direction.CW);
            out.op(in, Path.Op.DIFFERENCE);
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                    new int[]{0x9CFFFFFF, 0x4AFFFFFF, 0x26FFFFFF, 0x1AFFFFFF, 0x24FFFFFF, 0x52FFFFFF, 0x8CFFFFFF},
                    new float[]{0f, .16f, .36f, .5f, .64f, .84f, 1f}, Shader.TileMode.CLAMP));
            cv.drawPath(out, ring);
            // 内侧暗棱（厚度）
            float bIn = eIn * 2.6f;
            Path bo = new Path();
            bo.addRoundRect(new RectF(r.left + eIn, r.top + eIn, r.right - eIn, r.bottom - eIn),
                    Math.max(0, cornerPx - eIn), Math.max(0, cornerPx - eIn), Path.Direction.CW);
            Path bi = new Path();
            bi.addRoundRect(new RectF(r.left + bIn, r.top + bIn, r.right - bIn, r.bottom - bIn),
                    Math.max(0, cornerPx - bIn), Math.max(0, cornerPx - bIn), Path.Direction.CW);
            bo.op(bi, Path.Op.DIFFERENCE);
            Paint bev = new Paint(Paint.ANTI_ALIAS_FLAG);
            bev.setColor(0x1FFFFFFF);
            cv.drawPath(bo, bev);
        }

        private void drawLabels(Canvas cv) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xF2FFFFFF);
            p.setTextSize(12 * d);
            p.setTextAlign(Paint.Align.CENTER);
            p.setShadowLayer(6 * d, 0, 1 * d, 0xCC000000);
            Paint ps = new Paint(p);
            ps.setTextSize(10.5f * d);
            ps.setColor(0xD0FFFFFF);

            float y = rightTile.bottom + 32 * d;
            float mid = (leftTile.left + rightTile.right) * 0.5f;
            // 每条标签各自居中在自己的玻璃块下方，避免互相压字
            cv.drawText("路线 B · 官方 AGSL 着色器（含色散）", leftTile.centerX(), y, p);
            cv.drawText("现有方案 · 软件 lens", rightTile.centerX(), y, p);
            cv.drawText(routeBStatus, leftTile.centerX(), y + 18 * d, ps);
            cv.drawText("同一背景 / 同一模糊 / 同白度与亮边 —— 只差折射实现", mid, y + 50 * d, p);
            cv.drawText("左：边缘更平滑、四角有色散彩边　右：边缘更早出现波浪状斑块", mid, y + 68 * d, ps);
            if (Build.VERSION.SDK_INT < 33) {
                cv.drawText("本机 API < 33：左侧不可用（需回落软件路径）", mid, y + 86 * d, ps);
            }
        }

        // ---------------- 素材：背景 + 模糊 ----------------
        /** 背景：彩色对角渐变 + 柔边光斑 + **粗白网格**（10px 粗 / 150px 间距）。 */
        private Bitmap makeBackground(int w, int h) {
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            Paint base = new Paint();
            base.setShader(new LinearGradient(0, 0, w * 0.9f, h,
                    new int[]{0xFF8A2BE2, 0xFF2196F3, 0xFF00BCD4, 0xFF76FF03, 0xFFFFEB3B, 0xFFFF9800, 0xFFE91E63},
                    null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, base);
            Random r = new Random(7);
            int[] cols = {0xFFFF4081, 0xFFFFEB3B, 0xFF76FF03, 0xFF40C4FF, 0xFFFF6E40, 0xFFB388FF};
            for (int i = 0; i < 40; i++) {
                float x = r.nextInt(w), y = r.nextInt(h), rad = 90 + r.nextInt(220);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setShader(new RadialGradient(x, y, rad,
                        new int[]{(cols[r.nextInt(cols.length)] & 0x00FFFFFF) | 0x8C000000, 0x00000000},
                        new float[]{0f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(x, y, rad, p);
            }
            Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
            grid.setColor(0x8AFFFFFF);
            grid.setStrokeWidth(10);
            for (int x = 0; x <= w; x += 150) c.drawLine(x, 0, x, h, grid);
            for (int y = 0; y <= h; y += 150) c.drawLine(0, y, w, y, grid);
            return bm;
        }

        /** 全屏磨砂：降采样 → 两遍盒式/二项式模糊（复用 Frost.gaussianBlur）→ 放大 + vibrancy 补色。 */
        private Bitmap makeBlurred(Bitmap src, float scale) {
            int sw = src.getWidth(), sh = src.getHeight();
            int w = Math.max(8, (int) (sw * scale)), h = Math.max(8, (int) (sh * scale));
            Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
            int[] px = new int[w * h];
            small.getPixels(px, 0, w, 0, 0, w, h);
            // 12dp 的磨砂 ≈ 在 0.35 降采样上做 2 遍二项式模糊
            Frost.gaussianBlur(px, w, h);
            Frost.gaussianBlur(px, w, h);
            small.setPixels(px, 0, w, 0, 0, w, h);

            Bitmap out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            p.setColorFilter(new ColorMatrixColorFilter(vibrancy()));   // 糊后补饱和/亮度，防发灰
            c.drawBitmap(small, null, new Rect(0, 0, sw, sh), p);
            small.recycle();
            return out;
        }

        /** vibrancy：饱和 ×1.45、亮度 ×1.10（与官方 vibrancy() 同思路）。 */
        private static ColorMatrix vibrancy() {
            float sat = 1.45f, bright = 1.10f;
            float rw = 0.2126f, gw = 0.7152f, bw = 0.0722f, inv = 1f - sat;
            return new ColorMatrix(new float[]{
                    (rw * inv + sat) * bright, gw * inv * bright, bw * inv * bright, 0, 0,
                    rw * inv * bright, (gw * inv + sat) * bright, bw * inv * bright, 0, 0,
                    rw * inv * bright, gw * inv * bright, (bw * inv + sat) * bright, 0, 0,
                    0, 0, 0, 1, 0});
        }
    }
}
