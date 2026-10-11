package com.dsh.glassdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.dsh.glassdemo.UiGlass;

import java.util.Random;

public class FrostedPanelView extends android.widget.FrameLayout implements Frost.ShadowSuppressible {

    private final View target;
    private float corner = 0f;
    private int tint = 0x59FFFFFF;      // [可调] 玻璃体半透明白（色彩基调）
    private int hairline = 0x80FFFFFF;  // [可调] 发丝白棱（本项目多用 0x1EFFFFFF）
    private int downscale = 6;          // [可调] 降采样倍数（Demo 定版 5；4 更细腻但模糊稍淡）
    private boolean lensEnabled = false;
    private float lensH = 28f;          // [可调] 折射环带高(dp)：形变区多宽
    private float lensAmt = 38f;        // [可调] 折射幅度(dp)：形变强不强（弱→加大）
    private int overlayA = 0, overlayB = 0;   // [可调] 自身带色玻璃（⑥）：0 = 无色玻璃

    private volatile Bitmap lastBitmap;

    private final LinearLayout content;
    public LinearLayout content() { return content; }

    public FrostedPanelView(Context c, View target) {
        super(c);
        this.target = target;
        setWillNotDraw(false);
        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        Frost.registerPanel(this);   // 注册进册：整页共享抓取时统一隐藏，避免"玻璃把自己糊进去"
    }

    public FrostedPanelView corner(float r) { this.corner = r; return this; }
    public FrostedPanelView tint(int c) { this.tint = c; return this; }
    public FrostedPanelView hairline(int c) { this.hairline = c; return this; }
    public FrostedPanelView downscale(int s) { this.downscale = s; return this; }
    public FrostedPanelView lens() { this.lensEnabled = true; return this; }
    public FrostedPanelView lens(float h, float amt) {
        this.lensEnabled = true;
        this.lensH = h;
        this.lensAmt = amt;
        return this;
    }

    /** [demo 扩展] ⑥ 带色玻璃：两块渐变色叠在折射层之上（蓝紫 / 微红），空 = 纯白玻璃。 */
    public FrostedPanelView setColorOverlay(int a, int b) {
        this.overlayA = a;
        this.overlayB = b;
        invalidate();
        return this;
    }

    // ==================== [demo 扩展] 运行时调参（参数实验室用） ====================
    // 生产代码没有这套 setter：它对玻璃参数的策略是"改常量 + 重编"。
    // Demo 需要"拖动即时看效果"，所以补一组 setter，并在变更时让缓存失效。

    public void setLens(float hDp, float amtDp) {
        // 只设参数，**不改开关状态**。
        // 曾经的写法是同时 this.lensEnabled = true —— 结果滑块回调/参数初始化会把"关折射"
        // 悄悄顶开，A/B 对照失效（实测两次踩到：玻璃实验室、Demo 的 --ei lens 0）。
        // 开关一律只由 setLensEnabled() 决定，语义单一。
        this.lensH = hDp;
        this.lensAmt = amtDp;
        invalidateCapture();
    }

    public void setLensEnabled(boolean on) {
        this.lensEnabled = on;
        invalidateCapture();
    }

    public void setDownScale(int ds) {
        this.downscale = Math.max(2, ds);
        invalidateCapture();
    }

    public void setTint(int c) {
        this.tint = c;
        invalidate();
    }

    /** 参数变了：作废当前快照与阴影，并**解开 30ms 节流**（拉动滑块时不能丢帧）。 */
    private void invalidateCapture() {
        Bitmap old = lastBitmap;
        lastBitmap = null;
        if (old != null && !old.isRecycled()) old.recycle();
        shadowBmp = null;
        unthrottle();
        invalidate();
    }

    /** 解开节流：让下一次 refreshFrost 立即执行（调参实时预览用）。 */
    public void unthrottle() {
        lastFrost = 0L;
    }

    /** 折射环带高（dp）：**规范 §二-48 定版 28dp，固定值**（曾擅自改成"按面板短边缩放"→ 跑偏，已回位）。 */
    private float lensRefrHeight() { return lensEnabled ? lensH : 0f; }

    /** 折射幅度：**规范 §二-48 定版 38，固定值**（同上回位）。
     *  注意：V0.3 实测发现规范代码里传的是 `-refrAmount`（负号），会让边缘**向内**采样
     *  → 边缘放到纯黑背景上也不显示黑。已在 `Frost.java` 两处改为**正号**（向外采样），
     *  这是本工程对规范的唯一修正，需回写规范。 */
    private float lensRefrAmount() { return lensEnabled ? lensAmt : 0f; }
    private boolean useAppRoot;

    /** [关键] 抓取源用应用根容器：环境背景挂在 MainActivity 根容器上，滚动层背景透明，
     *  只抓 target 会抓到"空层"→ 糊完一片平白。必须开。 */
    public FrostedPanelView targetRoot() { this.useAppRoot = true; return this; }

    /** [关键·滚动补偿] 抓取源=内容层(ScrollView) → 补偿 getScrollY()（减）；
     *  抓取源=应用根容器(targetRoot) → 根容器不滚动，补偿必须 0。 */
    private int scrollComp() {
        int s = target == null ? 0 : target.getScrollY();
        if (compMode == 0) return 0;
        if (compMode == 1) return s;
        if (compMode == 2) return -s;
        return useAppRoot ? 0 : s;
    }

    private int compMode = -1;
    public FrostedPanelView debugComp(int m) { this.compMode = m; return this; }

    private android.view.View srcView() {
        if (!useAppRoot) return target;
        android.view.View v = this;
        while (v.getParent() instanceof android.view.View) {
            android.view.View pv = (android.view.View) v.getParent();
            if (!(pv.getParent() instanceof android.view.View)) return v;
            v = pv;
        }
        return target;
    }

    private long lastFrost;
    /** 宿主在内容层画完的同一帧调用。 [可调] 30ms 节流。 */
    public void refreshFrost() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastFrost < 30L) return;
        lastFrost = now;
        Bitmap old = lastBitmap;
        int v = getVisibility();
        int[] pl = new int[2];
        getLocationInWindow(pl);   // 必须在 INVISIBLE 前取，否则位置错乱→背景错位
        setVisibility(View.INVISIBLE);
        Frost.CURR.set(this);      // 告知引擎"是谁在抓"，抓取时才能把自己排除在隐藏名单之外
        try {
            Frost.suppressAllShadows(true);
            Bitmap nb = null;
            if (useAppRoot && downscale == Frost.SHARED_DS) {
                nb = Frost.captureShared(srcView(), pl[0], pl[1], getWidth(), getHeight(),
                        corner, lensRefrHeight(), lensRefrAmount());
            }
            if (nb == null) {
                nb = Frost.capture(srcView(), pl[0], pl[1], getWidth(), getHeight(),
                        downscale, corner, lensRefrHeight(), lensRefrAmount(), scrollComp());
            }
            lastBitmap = nb;
        } finally {
            Frost.CURR.remove();
            Frost.suppressAllShadows(false);
            setVisibility(v);
        }
        if (old != null && old != lastBitmap && !old.isRecycled()) old.recycle();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(() -> {
            if (lastBitmap == null || lastBitmap.isRecycled()) refreshFrost();
            invalidate();
        });
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        // 抓取期（别的玻璃件正在把整棵树画进离屏位图）：此时不画玻璃本体，
        // 只让背景透出去 —— 否则会把"上一次的玻璃快照"再糊进别人的玻璃里（叠影）。
        if (Frost.capturing) return;
        Bitmap b = lastBitmap;
        if (b == null || b.isRecycled()) {
            int v = getVisibility();
            int[] pl = new int[2];
            getLocationInWindow(pl);
            setVisibility(View.INVISIBLE);
            Frost.CURR.set(this);
            try {
                Frost.suppressAllShadows(true);
                if (useAppRoot && downscale == Frost.SHARED_DS) {
                    b = Frost.captureShared(srcView(), pl[0], pl[1], w, h,
                            corner, lensRefrHeight(), lensRefrAmount());
                }
                if (b == null) {
                    b = Frost.capture(srcView(), pl[0], pl[1], w, h, downscale, corner, lensRefrHeight(), lensRefrAmount(),
                            scrollComp());
                }
                lastBitmap = b;
            } finally {
                Frost.CURR.remove();
                Frost.suppressAllShadows(false);
                setVisibility(v);
            }
        }
        int pad = (int) Frost.padFor(Frost.BLUR_RADIUS, lensRefrHeight());
        int save = cv.save();
        if (corner > 0) {
            Path clip = new Path();
            clip.addRoundRect(0, 0, w, h, corner, corner, Path.Direction.CW);
            cv.clipPath(clip);
        }
        if (b != null) {
            float ds = (float) downscale;
            cv.scale(ds, ds);
            Paint base = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            // 【玻璃质感补强 · 关键】vibrancy：模糊会把背景洗淡变灰，真玻璃却是"更干净更透亮"。
            // 不补这一步，玻璃看起来就是"蒙了一层灰的透明片"（实测观感差异非常明显）。
            base.setColorFilter(new android.graphics.ColorMatrixColorFilter(VIBRANCY));
            cv.drawBitmap(b, -pad / ds, -pad / ds, base);
            cv.scale(1f / ds, 1f / ds);
        }
        // 玻璃体：半透明白 tint
        Paint tp = new Paint();
        tp.setColor(tint);
        cv.drawRect(0, 0, w, h, tp);
        // ⑥ 带色玻璃：自身渐变（蓝紫 / 微红）叠在折射背景之上、受光环之下
        if (overlayA != 0 || overlayB != 0) {
            Paint op = new Paint(Paint.ANTI_ALIAS_FLAG);
            op.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{overlayA, overlayB}, null, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, op);
            // 顶部内高光：带色玻璃的"厚度感"（否则彩色像贴纸）
            Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
            hl.setShader(new LinearGradient(0, 0, 0, h * 0.55f,
                    new int[]{0x59FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, hl);
        }
        // ④ 镜面高光（specular）：斜向柔光带 —— "一眼看出是玻璃"最强的线索，此前完全缺失。
        //    左上偏强、中段归零、右下再起一点，与光源方向一致，避免像"蒙塑料膜"。
        if (SPECULAR > 0) {
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setShader(new LinearGradient(0, 0, w * 0.85f, h,
                    new int[]{0x3AFFFFFF, 0x12FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x0EFFFFFF, 0x24FFFFFF},
                    new float[]{0f, 0.16f, 0.40f, 0.62f, 0.86f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, sp);
        }
        // ③ 边缘受光：整圈圆角环 + 对角多层渐变
        //   左上受光白高光（最强）→ 两侧最淡（但不断开）→ 右下受光（次强）
        //   注：仍是"极细"（1.2dp），此前的三色停法在中段掉到 5% 白，实机几乎看不见 → 抬到有感知的档位
        float eInset = UiGlass.dp(getContext(), 1.2f);
        Path ringOut = new Path();
        ringOut.addRoundRect(0, 0, w, h, corner, corner, Path.Direction.CW);
        Path ringIn = new Path();
        float er = Math.max(0f, corner - eInset);
        ringIn.addRoundRect(eInset, eInset, w - eInset, h - eInset, er, er, Path.Direction.CW);
        ringOut.op(ringIn, Path.Op.DIFFERENCE);
        Paint ringP = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringP.setShader(new LinearGradient(0, 0, w, h, RIM_STOPS, RIM_POS, Shader.TileMode.CLAMP));
        cv.drawPath(ringOut, ringP);
        // ⑥' 内侧暗棱（bevel）：亮边再往里一条极淡的暗环 —— 玻璃"厚度"的来源。
        //     只有亮边没有暗棱时，玻璃看起来像"贴纸"而不是"一块有厚度的玻璃"。
        if (BEVEL > 0) {
            float bIn = eInset * 2.6f;
            Path bevOut = new Path();
            float brO = Math.max(0f, corner - eInset);
            bevOut.addRoundRect(eInset, eInset, w - eInset, h - eInset, brO, brO, Path.Direction.CW);
            Path bevIn = new Path();
            float brI = Math.max(0f, corner - bIn);
            bevIn.addRoundRect(bIn, bIn, w - bIn, h - bIn, brI, brI, Path.Direction.CW);
            bevOut.op(bevIn, Path.Op.DIFFERENCE);
            Paint bevP = new Paint(Paint.ANTI_ALIAS_FLAG);
            bevP.setColor(BEVEL);
            cv.drawPath(bevOut, bevP);
        }
        grain(cv, w, h, GRAIN_ALPHA);
        cv.restore();
    }

    public static void rim(android.graphics.Canvas cv, float density, int w, int h, float corner) {
        if (w <= 0 || h <= 0) return;
        float eInset = Math.max(1f, density);
        Path ringOut = new Path();
        ringOut.addRoundRect(0, 0, w, h, corner, corner, Path.Direction.CW);
        Path ringIn = new Path();
        float er = Math.max(0f, corner - eInset);
        ringIn.addRoundRect(eInset, eInset, w - eInset, h - eInset, er, er, Path.Direction.CW);
        ringOut.op(ringIn, Path.Op.DIFFERENCE);
        Paint ringP = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringP.setShader(new LinearGradient(0, 0, w, h,
                new int[]{RIM_1, RIM_2, RIM_3},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        cv.drawPath(ringOut, ringP);
    }

    // [可调] 边缘受光：左上白高光 → 两侧最淡（但不断开）→ 右下受光
    //   —— 用 7 停渐变而非旧的三停（旧法中段 0x0C = 5% 白，实机上完全看不出"整圈连贯细亮边"）
    private static final int[] RIM_STOPS = {
            0x9CFFFFFF, 0x4AFFFFFF, 0x26FFFFFF, 0x1AFFFFFF, 0x24FFFFFF, 0x52FFFFFF, 0x8CFFFFFF};
    private static final float[] RIM_POS = {0f, 0.16f, 0.36f, 0.5f, 0.64f, 0.84f, 1f};
    private static final int RIM_1 = 0x9CFFFFFF;
    private static final int RIM_2 = 0x1AFFFFFF;
    private static final int RIM_3 = 0x8CFFFFFF;

    /** [可调] 内侧暗棱透明度：0 = 关闭。玻璃"厚度"的来源。 */
    private static final int BEVEL = 0x1FFFFFFF;
    /** [可调] 镜面高光强度倍率：0 = 关闭；>1 偏"塑料膜"，建议 0.8~1.2。 */
    private static final float SPECULAR = 1.0f;
    /** [可调] 磨砂噪点强度。 */
    private static final int GRAIN_ALPHA = 46;

    /**
     * [关键·玻璃质感] vibrancy：饱和度 ×1.45、亮度 ×1.10。
     * <p>为什么必须有：降采样 + 高斯模糊会把背景"洗淡发灰"，而真实玻璃的观感是
     * **更干净、更透亮**（iOS 的 material 同样带 vibrancy）。本工程此前只做模糊不补色，
     * 结果就是"蒙了一层灰的透明片" —— 这是"没有玻璃质感"的主因之一。
     * <p>系数取法：只动饱和度/亮度，不改色相；亮度走矩阵标量、饱和度走标准的
     * luminance-preserving 混合（0.2126/0.7152/0.0722）。
     */
    private static final android.graphics.ColorMatrix VIBRANCY = buildVibrancy(1.45f, 1.10f);

    private static android.graphics.ColorMatrix buildVibrancy(float sat, float bright) {
        final float rw = 0.2126f, gw = 0.7152f, bw = 0.0722f;
        final float inv = 1f - sat;
        float sr = rw * inv * bright, sg = gw * inv * bright, sb = bw * inv * bright;
        return new android.graphics.ColorMatrix(new float[]{
                sr + sat * bright, sg, sb, 0, 0,
                sr, sg + sat * bright, sb, 0, 0,
                sr, sg, sb + sat * bright, 0, 0,
                0, 0, 0, 1, 0});
    }

    public static void grain(android.graphics.Canvas cv, int w, int h, int maxAlpha) {
        Bitmap n = noise();
        if (n == null || w <= 0 || h <= 0) return;
        int save = cv.save();
        cv.scale(w / (float) n.getWidth(), h / (float) n.getHeight());
        Paint g = new Paint();
        g.setAlpha(maxAlpha);
        cv.drawBitmap(n, 0, 0, g);
        cv.restoreToCount(save);
    }

    // ---- 柔和外投影：悬浮感的来源 ----
    private Bitmap shadowBmp;
    private float shadowPad = -1f;
    private volatile boolean shadowSuppressed = false;

    /**
     * 抓取期间由 {@link Frost#suppressAllShadows(boolean)} 统一置位。
     * 为什么必须挂进引擎而不是各自为政：抓取是"把整棵根容器画进离屏位图"，
     * 此刻任何仍在场的投影都会被烘进别人的玻璃里（现象=玻璃内一块块深斑）。
     */
    @Override
    public void setShadowSuppressed(boolean suppressed) {
        if (shadowSuppressed != suppressed) {
            shadowSuppressed = suppressed;
            invalidate();
        }
    }

    public Bitmap shadowBitmap() {
        if (shadowSuppressed) return null;
        if (shadowBmp != null) return shadowBmp;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return null;
        float pad = UiGlass.dp(getContext(), 23f);   // [可调] 卡阴影 pad 18+5dp
        int W = (int) (w + pad * 2), H = (int) (h + pad * 2);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x40000000);   // [可调] 阴影色：0x40=25% 黑（"柔和不实黑"的上限，再黑就假）
        p.setMaskFilter(new android.graphics.BlurMaskFilter(UiGlass.dp(getContext(), 15f), android.graphics.BlurMaskFilter.Blur.NORMAL));   // [可调] blur 15dp（大=更柔）
        float off = UiGlass.dp(getContext(), 6f);
        c.drawRoundRect(pad, pad + off + UiGlass.dp(getContext(), 3f), pad + w, pad + h + off - UiGlass.dp(getContext(), 3f), corner, corner, p);   // [可调] off 6dp、ins 3dp
        shadowBmp = bmp;
        shadowPad = pad;
        return bmp;
    }

    public float shadowPad() { return shadowPad; }

    private static Bitmap sNoise;
    private static final Object sNoiseLock = new Object();

    private static Bitmap noise() {
        if (sNoise != null) return sNoise;
        synchronized (sNoiseLock) {
            if (sNoise != null) return sNoise;
            int N = 64;
            sNoise = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888);
            Random r = new Random(20261006L);
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    // 【修复】原实现 alpha 上限只有 26（再乘 GRAIN_ALPHA=10/255 ≈ 4%）
                    // → 实际叠加约 0.4% 不透明度，等于"没有噪点"，玻璃表面缺"雾面"层次。
                    // 现在噪点本体给足（0~96），强度由 GRAIN_ALPHA 统一控制。
                    int a = r.nextInt(96);
                    sNoise.setPixel(x, y, Color.argb(a, 255, 255, 255));
                }
            }
            return sNoise;
        }
    }
}
