package com.dsh.glassdemo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

public final class Frost {

    public interface ShadowSuppressible {
        void setShadowSuppressed(boolean suppressed);
    }

    // AGSL lens 折射 shader（复刻 AndroidLiquidGlass RoundedRectRefractionShaderString）
    private static final String LENS_SRC =
            "uniform shader content;\n" +
            "uniform vec2 size;\n" +
            "uniform vec2 offset;\n" +
            "uniform vec4 cornerRadii;\n" +
            "uniform float refractionHeight;\n" +
            "uniform float refractionAmount;\n" +
            "uniform float depthEffect;\n" +
            "float radiusAt(vec2 coord, vec4 radii) {\n" +
            "  if (coord.x >= 0.0) {\n" +
            "    if (coord.y <= 0.0) return radii.y;\n" +
            "    else return radii.z;\n" +
            "  } else {\n" +
            "    if (coord.y <= 0.0) return radii.x;\n" +
            "    else return radii.w;\n" +
            "  }\n" +
            "}\n" +
            "float sdRoundedRect(vec2 coord, vec2 halfSize, float radius) {\n" +
            "  vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));\n" +
            "  float outside = length(max(cornerCoord, vec2(0.0))) - radius;\n" +
            "  float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);\n" +
            "  return outside + inside;\n" +
            "}\n" +
            "vec2 gradSdRoundedRect(vec2 coord, vec2 halfSize, float radius) {\n" +
            "  vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));\n" +
            "  if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n" +
            "    return sign(coord) * normalize(max(cornerCoord, vec2(0.0)));\n" +
            "  } else {\n" +
            "    float gradX = step(cornerCoord.y, cornerCoord.x);\n" +
            "    return sign(coord) * vec2(gradX, 1.0 - gradX);\n" +
            "  }\n" +
            "}\n" +
            "float circleMap(float x) {\n" +
            "  return 1.0 - sqrt(1.0 - x * x);\n" +
            "}\n" +
            "half4 main(vec2 coord) {\n" +
            "  vec2 halfSize = size * 0.5;\n" +
            "  vec2 centeredCoord = (coord + offset) - halfSize;\n" +
            "  float radius = radiusAt(coord, cornerRadii);\n" +
            "  float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n" +
            "  if (-sd >= refractionHeight) return content.eval(coord);\n" +
            "  sd = min(sd, 0.0);\n" +
            "  float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;\n" +
            "  float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n" +
            "  vec2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));\n" +
            "  vec2 refractedCoord = coord + d * grad;\n" +
            "  return content.eval(refractedCoord);\n" +
            "}\n";

    /** [可调] 模糊半径(dp)。调大=更糊更柔；调小=磨砂更弱、背后轮廓更清楚。 */
    public static final float BLUR_RADIUS = 20f;

    /**
     * 捕获用的四周溢出区（padding）。
     *
     * <p>[2026-10-07 用户「边缘的过渡没了，很硬的变形边缘」] 真因：折射位移最大 = refrAmt
     * （= 1.35 × 环带高），而这里原来只留 `max(模糊半径, 环带)` —— **位移比溢出区还大**，
     * 采样一越界就回退成原像素，边缘于是变成一条硬边、没有过渡。
     * 现在按 `环带 + 位移 ≈ 2.35 × 环带` 留（模糊半径取其大者），过渡就回来了。
     */
    public static int padFor(float radius, float refrHeight) {
        return (int) Math.ceil(Math.max(radius, refrHeight * 2.35f));
    }

    public static Bitmap capture(View target, int winLeft, int winTop, int w, int h, int downscale,
                                 float cornerPx, float refrHeight, float refrAmount) {
        return capture(target, winLeft, winTop, w, h, downscale, cornerPx, refrHeight, refrAmount,
                target == null ? 0 : target.getScrollY());
    }

    /** 滚动补偿单独给（scrollComp = 真正滚动层的 getScrollY()）。 */
    public static Bitmap capture(View target, int winLeft, int winTop, int w, int h, int downscale,
                                 float cornerPx, float refrHeight, float refrAmount, int scrollComp) {
        if (target == null || w <= 0 || h <= 0) return null;
        int[] tl = new int[2];
        target.getLocationInWindow(tl);
        int left = winLeft - tl[0];
        // [滚动同步修复·实测] target.draw() 绘制 ScrollView 时【不会】自动应用 getScrollY() 偏移，
        // 必须手动补回滚动偏移：top = winTop - targetTop + scrollY（【加】）。
        // ⚠️ 减(winTop-tl[1]-scrollY) 会导致反向错位（症状：往下滑玻璃里的背景往上）；0 则不跟随。
        // 抓取源=根容器(targetRoot)时 scrollY 恒为 0，加 0 无影响。
        int top = winTop - tl[1] + scrollComp;
        return captureSoftware(target, left, top, w, h, downscale, cornerPx, refrHeight, refrAmount);
    }

    // ======== 整页共享抓取（性能）========
    private static final java.util.List<android.view.View> ALL_PANELS = new java.util.ArrayList<>();
    public static void registerPanel(android.view.View p) {
        synchronized (ALL_PANELS) {
            if (!ALL_PANELS.contains(p)) ALL_PANELS.add(p);
        }
    }

    /**
     * 抓取期间抑制"阴影/特 EFFECT"类绘制。
     *
     * <p>跨组件缺陷修复：{@code capture()} 是把**应用根容器**整棵画进离屏位图。此刻若
     * 其它玻璃件仍可见，它们会被一起糊进这张位图 —— 现象就是"玻璃里出现一块块深斑/叠影"
     * （本项目 2026-10-07 实锤）。生产代码里这件事由调用方手写、且只在 DeviceHubView 生效，
     * 多玻璃件页面（Demo）必然踩坑。这里把"隐藏在册的其它玻璃件"收进引擎内部：
     * 抓取期间只隐藏**除当前刷新者以外**的玻璃件（当前者被隐藏会让自己的捕获变成一片空白）。
     */
    public static void suppressAllShadows(boolean suppressed) {
        java.util.List<android.view.View> panels;
        synchronized (ALL_PANELS) {
            panels = new java.util.ArrayList<>(ALL_PANELS);
        }
        for (android.view.View v : panels) {
            if (v instanceof ShadowSuppressible) {
                ((ShadowSuppressible) v).setShadowSuppressed(suppressed);
            }
        }
    }

    /** 正在刷新的玻璃件（抓取时要把它排除在"隐藏名单"之外）。 */
    public static final ThreadLocal<android.view.View> CURR = new ThreadLocal<>();

    private static void hideOtherPanels(android.view.View except) {
        java.util.List<android.view.View> panels;
        synchronized (ALL_PANELS) {
            panels = new java.util.ArrayList<>(ALL_PANELS);
        }
        for (android.view.View v : panels) {
            if (v == except || v.getVisibility() != android.view.View.VISIBLE) continue;
            v.setTag(TAG_ORIG_VIS, android.view.View.VISIBLE);
            v.setVisibility(android.view.View.INVISIBLE);
        }
    }

    private static void restoreOtherPanels(android.view.View except) {
        java.util.List<android.view.View> panels;
        synchronized (ALL_PANELS) {
            panels = new java.util.ArrayList<>(ALL_PANELS);
        }
        for (android.view.View v : panels) {
            if (v == except) continue;
            Object o = v.getTag(TAG_ORIG_VIS);
            if (o != null) {
                v.setVisibility((Integer) o);
                v.setTag(TAG_ORIG_VIS, null);
            }
        }
    }

    /** 兜底：万一有路径漏了恢复，下一次抓取前把所有玻璃件复原，保证不会留下"永久隐形"的面板。 */
    private static void restoreAllPanels() {
        java.util.List<android.view.View> panels;
        synchronized (ALL_PANELS) {
            panels = new java.util.ArrayList<>(ALL_PANELS);
        }
        for (android.view.View v : panels) {
            Object o = v.getTag(TAG_ORIG_VIS);
            if (o != null) {
                v.setVisibility((Integer) o);
                v.setTag(TAG_ORIG_VIS, null);
            }
        }
    }

    private static final int TAG_ORIG_VIS = 0x7f0f0001;

    public static int SHARED_DS = 5;           // [可调] 共享抓取降采样（UiGlass.setGlassLevel 会同步成当前磨砂档）
    private static final long SHARED_FRAME_MS = 32L;
    private static Bitmap sharedFull;
    private static android.view.View sharedFullTarget;
    private static long sharedFullAt;

    private static synchronized Bitmap fullFrame(android.view.View target) {
        long now = android.os.SystemClock.uptimeMillis();
        if (sharedFull != null && !sharedFull.isRecycled() && sharedFullTarget == target
                && now - sharedFullAt < SHARED_FRAME_MS) return sharedFull;
        int W = target.getWidth(), H = target.getHeight();
        if (W <= 0 || H <= 0) return null;
        int bw = Math.max(1, W / SHARED_DS), bh = Math.max(1, H / SHARED_DS);
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        int save = c.save();
        c.scale(1f / SHARED_DS, 1f / SHARED_DS);
        int[] tl = new int[2];
        target.getLocationInWindow(tl);
        c.translate(-tl[0], -tl[1]);
        boolean oldCap = capturing;
        capturing = true;
        java.util.List<android.view.View> panels;
        synchronized (ALL_PANELS) { panels = new java.util.ArrayList<>(ALL_PANELS); }
        int[] vis = new int[panels.size()];
        for (int i = 0; i < panels.size(); i++) {
            vis[i] = panels.get(i).getVisibility();
            panels.get(i).setVisibility(android.view.View.INVISIBLE);
        }
        try {
            target.draw(c);
        } catch (Throwable ignored) {
        } finally {
            for (int i = 0; i < panels.size(); i++) panels.get(i).setVisibility(vis[i]);
            capturing = oldCap;
        }
        c.restoreToCount(save);
        int[] px = new int[bw * bh];
        b.getPixels(px, 0, bw, 0, 0, bw, bh);
        gaussianBlur(px, bw, bh);
        b.setPixels(px, 0, bw, 0, 0, bw, bh);
        Bitmap old = sharedFull;
        sharedFull = b;
        sharedFullTarget = target;
        sharedFullAt = now;
        if (old != null && old != b && !old.isRecycled()) old.recycle();
        return b;
    }

    public static Bitmap captureShared(android.view.View target, int winLeft, int winTop,
                                       int w, int h, float cornerPx, float refrHeight, float refrAmount) {
        Bitmap full = fullFrame(target);
        if (full == null) return null;
        int[] tl = new int[2];
        target.getLocationInWindow(tl);
        int ds = SHARED_DS;
        int pad = padFor(BLUR_RADIUS, refrHeight);
        int bx = Math.round((winLeft - tl[0] - pad) / (float) ds);
        // [滚动同步修复] fullFrame 抓的是 scroll 当前布局内容(target.draw 不自动滚动)，
        // 玻璃背后须加回滚动偏移 target.getScrollY()，否则滚动时背景不跟随。
        int by = Math.round((winTop - tl[1] + target.getScrollY() - pad) / (float) ds);
        int bw = Math.max(4, Math.round((w + 2 * pad) / (float) ds));
        int bh = Math.max(4, Math.round((h + 2 * pad) / (float) ds));
        if (bx < 0 || by < 0 || bx + bw > full.getWidth() || by + bh > full.getHeight()) return null;
        int[] px = new int[bw * bh];
        full.getPixels(px, 0, bw, bx, by, bw, bh);
        if (refrAmount > 0f && bw > 8 && bh > 8) {
            applyLens(px, bw, bh, cornerPx / ds, refrHeight / ds, refrAmount / ds, Math.max(1, pad / ds));
        }
        Bitmap out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, bw, 0, 0, bw, bh);
        return out;
    }

    public static volatile boolean capturing;

    private static Bitmap captureSoftware(View target, int left, int top, int w, int h, int downscale,
                                          float cornerPx, float refrHeight, float refrAmount) {
        if (downscale < 2) downscale = 2;
        int pad = padFor(BLUR_RADIUS, refrHeight);
        int W = w + pad * 2, H = h + pad * 2;
        int bw = Math.max(1, Math.round(W / (float) downscale));
        int bh = Math.max(1, Math.round(H / (float) downscale));
        Bitmap small;
        try {
            small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            return null;
        }
        Canvas c = new Canvas(small);
        int save = c.save();
        c.scale(1f / downscale, 1f / downscale);
        c.translate(-(left - pad), -(top - pad));
        boolean oldCap = capturing;
        capturing = true;
        restoreAllPanels();                 // 兜底：先复原上一次漏恢复的面板
        hideOtherPanels(CURR.get());        // 抓取期间藏掉别的玻璃件（否则它们会被糊进玻璃里）
        try {
            target.draw(c);
        } catch (Throwable ignored) {
        } finally {
            restoreOtherPanels(CURR.get());
            capturing = oldCap;
        }
        c.restore();
        int[] px = new int[bw * bh];
        small.getPixels(px, 0, bw, 0, 0, bw, bh);
        gaussianBlur(px, bw, bh);
        if (downscale >= 6) gaussianBlur(px, bw, bh);
        if (refrAmount > 0f && bw > 8 && bh > 8) {
            applyLens(px, bw, bh, cornerPx / downscale, refrHeight / downscale, refrAmount / downscale,
                    Math.max(1, pad / downscale));
        }
        small.setPixels(px, 0, bw, 0, 0, bw, bh);
        return small;
    }

    /** 软件 lens 折射（SDF 圆角 + 边缘环带平滑折射）。
     *  [可调] refrAmt=形变强不强（弱→加大）、refrH=环带多宽。 */
    public static void applyLens(int[] in, int W, int H,
                                 float corner, float refrH, float refrAmt, int pad) {
        if (refrAmt == 0f || pad < 0) return;
        int[] out = new int[W * H];
        float cw = W - 2f * pad, ch = H - 2f * pad;
        if (cw < 4f || ch < 4f) return;
        float halfW = cw * 0.5f, halfH = ch * 0.5f;
        float radius = Math.min(corner, Math.min(halfW, halfH));
        for (int y = 0; y < H; y++) {
            int row = y * W;
            for (int x = 0; x < W; x++) {
                float ccx = (x - pad) - halfW;
                float ccy = (y - pad) - halfH;
                float ax = Math.abs(ccx) - (halfW - radius);
                float ay = Math.abs(ccy) - (halfH - radius);
                float mx = Math.max(ax, 0f), my = Math.max(ay, 0f);
                float outside = (float) Math.sqrt(mx * mx + my * my) - radius;
                float inside = Math.min(Math.max(ax, ay), 0f);
                float sd = outside + inside;
                if (-sd >= refrH) { out[row + x] = in[row + x]; continue; }
                sd = Math.min(sd, 0f);
                float t = 1f - (-sd) / refrH;
                // smoothstep 缓入缓出：边缘形变过渡自然无生硬
                float d = (float) (t * t * (3f - 2f * t)) * refrAmt;
                float nx, ny;
                if (mx >= 0f || my >= 0f) {
                    float len = (float) Math.sqrt(mx * mx + my * my);
                    nx = len > 0 ? (ccx >= 0 ? 1 : -1) * mx / len : (ccx >= 0 ? 1 : -1);
                    ny = len > 0 ? (ccy >= 0 ? 1 : -1) * my / len : (ccy >= 0 ? 1 : -1);
                } else {
                    nx = ccy >= ccx ? 0 : 1;
                    ny = ccy >= ccx ? 1 : 0;
                }
                // 折射：法线推挤 + 切向弧线弯曲（圆柱玻璃体积感）
                float tx = -ny, ty = nx;
                float bx = ccx / (halfW + 1e-4f);
                float by = ccy / (halfH + 1e-4f);
                float fx, fy;
                if (mx > 0f && my <= 0f) {
                    // [V1.2 直边适中] 3× 过度推开 → 边缘背景被拉出深色/突兀边(用户实测黑条)。
                    // 回退适中 2×：保留边缘压缩体积感但不突兀，竖直弧线调小避免不对称。
                    float arc2 = d * 0.25f * (0.5f + by * 0.4f);
                    fx = x + nx * Math.min(d * 2f, pad * 0.7f);
                    fy = y + bx * arc2;
                } else {
                    float arc = d * 0.28f * (bx + by) * 0.5f;
                    fx = x + nx * d + tx * arc;
                    fy = y + ny * d + ty * arc;
                }
                // [V0.8 · 用户实测「左右两条发白竖带 = 变回卡片颜色」] 真因：面板离屏幕边太近时，
                // 折射往外采样会**越过已捕获位图的范围**（越界处是未初始化的透明像素），
                // 叠上玻璃自身的白 tint 就成了两条白带。修法：**夹进位图范围**取最近的真实像素
                // （不要回退原像素 —— 那会形成一条硬边；这正是 V0.3「很硬的变形边缘」的成因之一）。
                if (fx < 0f) fx = 0f; else if (fx > W - 1f) fx = W - 1f;
                if (fy < 0f) fy = 0f; else if (fy > H - 1f) fy = H - 1f;
                int x0 = (int) fx, y0 = (int) fy;
                if (x0 >= W - 1) x0 = W - 2;
                if (y0 >= H - 1) y0 = H - 2;
                float frx = fx - x0, fry = fy - y0;
                int i00 = y0 * W + x0, i10 = i00 + 1, i01 = i00 + W, i11 = i01 + 1;
                int p00 = in[i00], p10 = in[i10], p01 = in[i01], p11 = in[i11];
                float w00 = (1 - frx) * (1 - fry), w10 = frx * (1 - fry), w01 = (1 - frx) * fry, w11 = frx * fry;
                int a = (int) (((p00 >>> 24 & 255) * w00 + (p10 >>> 24 & 255) * w10 + (p01 >>> 24 & 255) * w01 + (p11 >>> 24 & 255) * w11));
                int r = (int) (((p00 >>> 16 & 255) * w00 + (p10 >>> 16 & 255) * w10 + (p01 >>> 16 & 255) * w01 + (p11 >>> 16 & 255) * w11));
                int g = (int) (((p00 >>> 8 & 255) * w00 + (p10 >>> 8 & 255) * w10 + (p01 >>> 8 & 255) * w01 + (p11 >>> 8 & 255) * w11));
                int b = (int) (((p00 & 255) * w00 + (p10 & 255) * w10 + (p01 & 255) * w01 + (p11 & 255) * w11));
                out[row + x] = ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
            }
        }
        System.arraycopy(out, 0, in, 0, W * H);
    }

    public static void gaussianBlur(int[] in, int w, int h) {
        if (w < 3 || h < 3) return;
        int[] tmp = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int p0 = in[i - (x > 0 ? 1 : 0)];
                int p1 = in[i];
                int p2 = in[i + (x < w - 1 ? 1 : 0)];
                int c0 = x > 1 ? in[i - 2] : p0, c4 = x < w - 2 ? in[i + 2] : p2;
                int a0 = ((p0 >>> 24) & 0xff) * 4 + ((p1 >>> 24) & 0xff) * 6 + ((p2 >>> 24) & 0xff) * 4 + ((c0 >>> 24) & 0xff) + ((c4 >>> 24) & 0xff);
                int r0 = ((p0 >>> 16) & 0xff) * 4 + ((p1 >>> 16) & 0xff) * 6 + ((p2 >>> 16) & 0xff) * 4 + ((c0 >>> 16) & 0xff) + ((c4 >>> 16) & 0xff);
                int g0 = ((p0 >>> 8) & 0xff) * 4 + ((p1 >>> 8) & 0xff) * 6 + ((p2 >>> 8) & 0xff) * 4 + ((c0 >>> 8) & 0xff) + ((c4 >>> 8) & 0xff);
                int b0 = (p0 & 0xff) * 4 + (p1 & 0xff) * 6 + (p2 & 0xff) * 4 + (c0 & 0xff) + (c4 & 0xff);
                tmp[i] = (clamp255(a0 / 16) << 24) | (clamp255(r0 / 16) << 16)
                        | (clamp255(g0 / 16) << 8) | clamp255(b0 / 16);
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int i = y * w + x;
                int p0 = tmp[i - (y > 0 ? w : 0)];
                int p1 = tmp[i];
                int p2 = tmp[i + (y < h - 1 ? w : 0)];
                int c0 = y > 1 ? tmp[i - 2 * w] : p0, c4 = y < h - 2 ? tmp[i + 2 * w] : p2;
                int a0 = ((p0 >>> 24) & 0xff) * 4 + ((p1 >>> 24) & 0xff) * 6 + ((p2 >>> 24) & 0xff) * 4 + ((c0 >>> 24) & 0xff) + ((c4 >>> 24) & 0xff);
                int r0 = ((p0 >>> 16) & 0xff) * 4 + ((p1 >>> 16) & 0xff) * 6 + ((p2 >>> 16) & 0xff) * 4 + ((c0 >>> 16) & 0xff) + ((c4 >>> 16) & 0xff);
                int g0 = ((p0 >>> 8) & 0xff) * 4 + ((p1 >>> 8) & 0xff) * 6 + ((p2 >>> 8) & 0xff) * 4 + ((c0 >>> 8) & 0xff) + ((c4 >>> 8) & 0xff);
                int b0 = (p0 & 0xff) * 4 + (p1 & 0xff) * 6 + (p2 & 0xff) * 4 + (c0 & 0xff) + (c4 & 0xff);
                in[i] = (clamp255(a0 / 16) << 24) | (clamp255(r0 / 16) << 16)
                        | (clamp255(g0 / 16) << 8) | clamp255(b0 / 16);
            }
        }
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private Frost() {
    }
}
