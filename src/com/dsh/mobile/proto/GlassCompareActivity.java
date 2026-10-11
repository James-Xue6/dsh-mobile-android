package com.dsh.mobile.proto;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dsh.mobile.ui.Ui;

import java.util.Random;

/**
 * 【玻璃参数对比测试页】花哨彩色底图 + 8 个不同参数版本的液态玻璃，
 * 每块标注参数，用于定位"最终想要的玻璃质感"对应哪组参数。
 * 实现完全复用 Frost/FrostedPanelView/FrostedHomeView（真液态玻璃那套）。
 */
public class GlassCompareActivity extends Activity {

    private static Bitmap sFlower;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        // ---- 内容层：可滚动，背景 = 花哨彩色底图（检验模糊 + 折射形变） ----
        final ScrollView sc = new ScrollView(this);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackground(new BitmapDrawable(getResources(), flowerBg()));
        // 撑高到能容纳 8 块玻璃（每块 120dp + 间距）
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.dp(this, 8 * 146)));
        list.addView(spacer);
        sc.addView(list);

        // ---- 帧同步宿主：内容层(sc) + 玻璃面板同容器 ----
        final FrostedHomeView host = new FrostedHomeView(this, sc);
        host.setBackgroundColor(Color.BLACK);

        // 沉浸式（玻璃避开状态栏）
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        int sb = statusBarHeight();

        // ---- 8 个参数变体：{tint, downscale, lensH, lensAmt}，lensH=0 表示不折射 ----
        int[][] vars = {
            {0x42FFFFFF, 5, 20, 28},   // v1 中间值折射(refrH20/refrAmt28)——有折射且边缘不过度
            {0x42FFFFFF, 5, 40, 55},   // v2 形变强
            {0x42FFFFFF, 4, 28, 38},   // v3 更细腻
            {0x38FFFFFF, 5, 28, 38},   // v4 更透
            {0x59FFFFFF, 5, 28, 38},   // v5 更白(糊)
            {0x42FFFFFF, 5, 0, 0},     // v6 磨砂对照(无折射)
            {0x42FFFFFF, 5, 20, 24},   // v7 形变弱
            {0x42FFFFFF, 5, 28, 38},   // v8 亮边版(hairline 0x80)
        };
        int[] hair = {0x1EFFFFFF, 0x1EFFFFFF, 0x1EFFFFFF, 0x1EFFFFFF,
                      0x1EFFFFFF, 0x1EFFFFFF, 0x1EFFFFFF, 0x80FFFFFF};
        String[] names = {"v1 正确", "v2 形变强", "v3 细腻", "v4 透",
                          "v5 白/糊", "v6 磨砂", "v7 形变弱", "v8 亮边"};

        for (int i = 0; i < vars.length; i++) {
            int[] v = vars[i];
            final FrostedPanelView g = new FrostedPanelView(this, sc);
            g.corner(Ui.dp(this, 24)).tint(v[0]).hairline(hair[i]).downscale(v[1]);
            if (v[2] > 0) g.lens(v[2], v[3]);
            // 标注参数（白字+阴影，压在多花背景上仍可读）
            TextView label = Ui.text(this,
                    names[i] + "  tint0x" + Integer.toHexString(v[0] & 0xFFFFFF)
                    + " ds" + v[1]
                    + (v[2] > 0 ? " lens" + v[2] + "/" + v[3] : " 无lens")
                    + (i == 7 ? " hair0x80" : ""),
                    Ui.S_FOOT, 0xFFFFFFFF, true);
            label.setShadowLayer(Ui.dp(this, 2), 0, Ui.dp(this, 1), 0xAA000000);
            g.content().addView(label);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 120), Gravity.TOP);
            lp.leftMargin = Ui.dp(this, 12);
            lp.rightMargin = Ui.dp(this, 12);
            lp.topMargin = sb + Ui.dp(this, 12 + i * 146);
            g.setLayoutParams(lp);
            host.addFrost(g);
        }

        setContentView(host);
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : Ui.dp(this, 24);
    }

    /** 花哨彩色底图：多彩对角渐变 + 彩色柔边光斑 + 清晰网格线（检验形变）。 */
    private Bitmap flowerBg() {
        if (sFlower != null) return sFlower;
        int W = 1080, H = 2400;
        Bitmap bm = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint base = new Paint();
        base.setShader(new LinearGradient(0, 0, W, H,
                new int[]{0xFF8A2BE2, 0xFF2196F3, 0xFF00BCD4, 0xFF76FF03, 0xFFFFEB3B, 0xFFFF9800, 0xFFE91E63},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, base);
        Random r = new Random(7);
        int[] cols = {0xFFFF4081, 0xFFFFEB3B, 0xFF76FF03, 0xFF40C4FF, 0xFFFF6E40, 0xFFB388FF, 0xFFEAFF7F, 0xFFFF1744};
        for (int i = 0; i < 80; i++) {
            int x = r.nextInt(W), y = r.nextInt(H);
            int rad = 40 + r.nextInt(150);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(cols[r.nextInt(cols.length)]);
            p.setAlpha(70 + r.nextInt(120));
            p.setMaskFilter(new BlurMaskFilter(rad * 0.7f, BlurMaskFilter.Blur.NORMAL));
            c.drawCircle(x, y, rad, p);
        }
        Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        grid.setColor(0x66FFFFFF);
        grid.setStrokeWidth(4);
        for (int x = 0; x <= W; x += 90) c.drawLine(x, 0, x, H, grid);
        for (int y = 0; y <= H; y += 90) c.drawLine(0, y, W, y, grid);
        sFlower = bm;
        return bm;
    }
}
