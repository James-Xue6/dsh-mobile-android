package com.dsh.mobile.proto;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Random;

/** 液态玻璃手动调参：9 个参数全部暴露成滑条，实时看效果，用来在模拟器上定位最终质感参数。 */
public class GlassTunerActivity extends Activity {

    private TunerGlassView glass;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // 顶栏：底图 + 玻璃预览（weight 大）
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        glass = new TunerGlassView(this);
        glass.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.62f));
        root.addView(glass);

        // 参数滑条区
        ScrollView sc = new ScrollView(this);
        sc.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.38f));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(6), dp(10), dp(20));
        sc.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sc);

        setContentView(root);

        // 花哨底图（渐变 + 柔边光斑 + 网格线），检验折射/形变
        Bitmap bg = flowerBg(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels);
        glass.setBg(bg);

        // 每条滑条：标签 + 当前值 + SeekBar
        addSeek(list, "模糊次数 blurPasses", 0, 4, glass.blurPasses, v -> glass.blurPasses = v);
        addSeek(list, "折射高度 refrH(dp)", 0, 60, glass.refrH, v -> glass.refrH = v);
        addSeek(list, "折射量 refrAmt(dp)", 0, 80, glass.refrAmt, v -> glass.refrAmt = v);
        addSeek(list, "不透明度 tintAlpha", 0, 255, glass.tintAlpha, v -> glass.tintAlpha = v);
        addSeek(list, "圆角 corner(dp)", 0, 48, glass.corner, v -> glass.corner = v);
        addSeek(list, "降采样 downscale", 2, 6, glass.downscale, v -> glass.downscale = v);
        addSeek(list, "边缘亮线 hairline", 0, 255, glass.hairlineAlpha, v -> glass.hairlineAlpha = v);
        addSeek(list, "彩度 saturation(%)", 50, 200, glass.saturation, v -> glass.saturation = v);
        addSeek(list, "色散 chromatic(px)", 0, 12, glass.chromatic, v -> glass.chromatic = v);
    }

    private void addSeek(LinearLayout list, String label, int min, int max, int init,
                         java.util.function.IntConsumer setter) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        TextView tv = new TextView(this);
        tv.setText(label + "  = " + init);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(13);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(150), dp(28));
        tlp.setMargins(0, 0, dp(8), 0);
        tv.setLayoutParams(tlp);
        row.addView(tv);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(init - min);
        sb.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = min + p;
                setter.accept(v);
                tv.setText(label + "  = " + v);
                glass.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        row.addView(sb);
        list.addView(row);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static Bitmap flowerBg(int W, int H) {
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
        return bm;
    }
}
