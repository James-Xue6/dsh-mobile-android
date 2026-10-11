package com.dsh.glassdemo;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 玻璃 Demo 自带的极简 UI 工具集。
 *
 * <p>为什么不用 dsh-mobile 的 {@code com.dsh.mobile.ui.Ui}：那是 192KB 的生产 UI 层，
 * 本 Demo 只需要它里面 6 个方法与 6 个常量。独立成一份可让 Demo 完全解耦，
 * 不随生产 UI 变动而破坏（也避免把 Demo 的改动混进生产工程）。
 */
public final class UiGlass {

    /** 字号（sp）——与生产层保持一致，保证观感可对齐。 */
    public static final float S_TITLE2 = 19f;
    public static final float S_TITLE3 = 17.5f;
    public static final float S_BODY   = 15.5f;
    public static final float S_FOOT   = 12.5f;

    /** 磨砂档位（降采样倍数）：越大越糊。默认第 3 档 ds=8。 */
    public static final int[] GLASS_DS = {4, 5, 6, 8};

    private static int glassLevel = 3;

    public static void setGlassLevel(int i) {
        glassLevel = Math.max(0, Math.min(GLASS_DS.length - 1, i));
        Frost.SHARED_DS = GLASS_DS[glassLevel];
    }

    public static int glassDownscale() {
        return GLASS_DS[glassLevel];
    }

    private static volatile boolean capturing = false;

    public static boolean isCapturing() {
        return capturing;
    }

    public static void setCapturing(boolean v) {
        capturing = v;
    }

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static GradientDrawable round(float radius, int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radius);
        d.setColor(fill);
        return d;
    }

    public static TextView text(Context c, String s, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private UiGlass() {
    }
}
