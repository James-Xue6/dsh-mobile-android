package com.dsh.mobile.proto;

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

import com.dsh.mobile.ui.Ui;

import java.util.Random;

public class FrostedPanelView extends android.widget.FrameLayout {

    private final View target;
    private float corner = 0f;
    private int tint = 0x59FFFFFF;      // [可调] 玻璃体半透明白（色彩基调）
    private int hairline = 0x80FFFFFF;  // [可调] 发丝白棱（本项目多用 0x1EFFFFFF）
    private int downscale = 6;          // [可调] 降采样倍数（Demo 定版 5；4 更细腻但模糊稍淡）
    private boolean lensEnabled = false;
    private float lensH = 28f;          // [可调] 折射环带高(dp)：形变区多宽
    private float lensAmt = 38f;        // [可调] 折射幅度(dp)：形变强不强（弱→加大）

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
        try {
            if (getParent() instanceof Frost.ShadowSuppressible) {
                ((Frost.ShadowSuppressible) getParent()).setShadowSuppressed(true);
            }
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
            if (getParent() instanceof Frost.ShadowSuppressible) {
                ((Frost.ShadowSuppressible) getParent()).setShadowSuppressed(false);
            }
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
        Bitmap b = lastBitmap;
        if (b == null || b.isRecycled()) {
            int v = getVisibility();
            int[] pl = new int[2];
            getLocationInWindow(pl);
            setVisibility(View.INVISIBLE);
            try {
                if (getParent() instanceof Frost.ShadowSuppressible) {
                    ((Frost.ShadowSuppressible) getParent()).setShadowSuppressed(true);
                }
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
                if (getParent() instanceof Frost.ShadowSuppressible) {
                    ((Frost.ShadowSuppressible) getParent()).setShadowSuppressed(false);
                }
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
            cv.drawBitmap(b, -pad / ds, -pad / ds, base);
            cv.scale(1f / ds, 1f / ds);
        }
        // 玻璃体：半透明白 tint
        Paint tp = new Paint();
        tp.setColor(tint);
        cv.drawRect(0, 0, w, h, tp);
        // 边缘受光：整圈圆角环 + 对角渐变（左上受光→右下受光），极细柔和
        float eInset = Ui.dp(getContext(), 1f);
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

    // [可调] 边缘受光三色标：左上受光→中部最淡→右下受光
    private static final int RIM_1 = 0x7AFFFFFF;
    private static final int RIM_2 = 0x0CFFFFFF;
    private static final int RIM_3 = 0x50FFFFFF;
    // [可调] 磨砂噪点强度
    private static final int GRAIN_ALPHA = 10;

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

    public Bitmap shadowBitmap() {
        if (shadowBmp != null) return shadowBmp;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return null;
        float pad = Ui.dp(getContext(), 23f);   // [可调] 卡阴影 pad 18+5dp
        int W = (int) (w + pad * 2), H = (int) (h + pad * 2);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x33000000);   // [可调] 阴影色：0x33=20%黑（太黑太实则调低如 0x22）
        p.setMaskFilter(new android.graphics.BlurMaskFilter(Ui.dp(getContext(), 11f), android.graphics.BlurMaskFilter.Blur.NORMAL));   // [可调] blur 11dp（大=更柔）
        float off = Ui.dp(getContext(), 5f);
        c.drawRoundRect(pad, pad + off + Ui.dp(getContext(), 3f), pad + w, pad + h + off - Ui.dp(getContext(), 3f), corner, corner, p);   // [可调] off 5dp、ins 3dp
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
                    int v = r.nextInt(60);
                    int a = Math.min(26, v);
                    sNoise.setPixel(x, y, Color.argb(a, 255, 255, 255));
                }
            }
            return sNoise;
        }
    }
}
