package com.dsh.glassdemo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 玻璃参数实验室（Demo 自带，非产品页）
 * ---------------------------------------------------------------------------
 * 目的：把《液态玻璃引擎Demo制作文档》里的参数表变成**可实时拖动**的实验台，
 * 让"参数 → 观感"的因果关系一眼可见，避免离线猜参数。
 *
 *   - 折射环带高 lensH（dp）  —— 形变区多宽
 *   - 折射幅度   lensAmt（dp）—— 形变强不强
 *   - 磨砂档位   downscale    —— 越大越糊（4 / 5 / 6 / 8）
 *   - 玻璃白度   tint         —— 太高必发白发假
 *   - "关折射"开关            —— A/B 对照，直接证明折射是否真的在起作用
 *
 * 附标准值按钮：一键回到已验收定版（28/38/ds8/0x42FFFFFF）。
 */
public class GlassLabActivity extends Activity {

    private FrostedPanelView glass;
    private ScrollView sc;
    private SeekBar sbH, sbAmt, sbDs, sbTint;
    private TextView tvH, tvAmt, tvDs, tvTint;
    private int tintAlpha = 0x42;
    private boolean lensOn = true;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // adb 可指定：--ei level 0..3（磨砂档）  --ei lens 0（关折射，做 A/B 对照）
        int level = getIntent().getIntExtra("level", 0);
        boolean lensOn0 = getIntent().getIntExtra("lens", 1) != 0;
        UiGlass.setGlassLevel(level);
        lensOn = lensOn0;
        final float dens = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new BitmapDrawable(getResources(), flowerBg()));

        // ---------- 内容层：可滚动色块（拖动时玻璃背后要有东西在动） ----------
        sc = new ScrollView(this);
        LinearLayout col = UiGlass.col(this);
        col.setPadding(UiGlass.dp(this, 16), UiGlass.dp(this, 30),
                UiGlass.dp(this, 16), UiGlass.dp(this, 40));
        col.addView(card("参数实验室", "拖动下面的滑块：玻璃效果即时变化。\n"
                + "重点看 ② 折射：玻璃里的白网格线越靠边弯得越厉害。", 0xFF2B6CFF));
        for (int i = 0; i < 14; i++) {
            col.addView(card("彩色内容 " + (i + 1),
                    "滚动我 —— 玻璃背后的颜色与网格应随之变化（⑤ 滚动同步）。",
                    BUBBLES[i % BUBBLES.length]));
        }
        sc.addView(col);
        root.addView(sc, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // ---------- 玻璃件：可调参数的那一块（吸顶，随时对照） ----------
        glass = new FrostedPanelView(this, sc);
        glass.corner(UiGlass.dp(this, 24)).tint(0x42FFFFFF).hairline(0x1EFFFFFF)
             .downscale(UiGlass.glassDownscale()).targetRoot();
        glass.setLensEnabled(lensOn);
        TextView gl = UiGlass.text(this, "可调玻璃块（看网格线弯曲）", UiGlass.S_FOOT, 0xFF14141C, true);
        gl.setGravity(Gravity.CENTER);
        glass.content().addView(gl);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiGlass.dp(this, 120), Gravity.TOP);
        glp.leftMargin = UiGlass.dp(this, 12);
        glp.rightMargin = UiGlass.dp(this, 12);
        glp.topMargin = UiGlass.dp(this, 12);
        glass.setLayoutParams(glp);
        root.addView(glass);

        // ---------- 控制面板（底部，半透明深色 + 圆角） ----------
        LinearLayout panel = UiGlass.col(this);
        panel.setBackground(UiGlass.round(UiGlass.dp(this, 18), 0xE61A1C28));
        panel.setPadding(UiGlass.dp(this, 16), UiGlass.dp(this, 12), UiGlass.dp(this, 16), UiGlass.dp(this, 14));

        sbH   = addSlider(panel, "折射环带高 lensH (dp)", 8, 60, 28);
        tvH   = lastLabel;
        sbAmt = addSlider(panel, "折射幅度 lensAmt (dp)", 0, 80, 38);
        tvAmt = lastLabel;
        sbDs  = addSlider(panel, "磨砂档位 downscale", 4, 8, 8);
        tvDs  = lastLabel;
        sbTint = addSlider(panel, "玻璃白度 tint (%)", 5, 60, 0x42 * 100 / 255);
        tvTint = lastLabel;

        // 开关行
        LinearLayout btnRow = UiGlass.row(this);
        btnRow.setPadding(0, UiGlass.dp(this, 10), 0, 0);
        final TextView toggle = button("关折射（A/B 对照）");
        final TextView reset  = button("回到标准值 28/38/ds8");
        btnRow.addView(toggle);
        btnRow.addView(reset);
        panel.addView(btnRow);

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        plp.leftMargin = UiGlass.dp(this, 10);
        plp.rightMargin = UiGlass.dp(this, 10);
        plp.bottomMargin = UiGlass.dp(this, 10);
        panel.setLayoutParams(plp);
        root.addView(panel);

        setContentView(root);
        android.view.Window win = getWindow();
        win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        win.setStatusBarColor(Color.TRANSPARENT);
        win.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        // ---------- 事件 ----------
        SeekBar.OnSeekBarChangeListener lis = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) { apply(); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        };
        sbH.setOnSeekBarChangeListener(lis);
        sbAmt.setOnSeekBarChangeListener(lis);
        sbDs.setOnSeekBarChangeListener(lis);
        sbTint.setOnSeekBarChangeListener(lis);

        toggle.setOnClickListener(v -> {
            lensOn = !lensOn;
            toggle.setText(lensOn ? "关折射（A/B 对照）" : "开折射");
            glass.setLensEnabled(lensOn);
            refrost();
        });
        toggle.setText(lensOn ? "关折射（A/B 对照）" : "开折射");
        reset.setOnClickListener(v -> {
            sbH.setProgress(28);
            sbAmt.setProgress(38);
            sbDs.setProgress(8);
            sbTint.setProgress(0x42 * 100 / 255);
            glass.setLensEnabled(true);
            apply();
        });

        final Runnable refrost = this::refrost;
        sc.setOnScrollChangeListener((v, sx, sy, osx, osy) -> refrost.run());
        // 让滑块初值与实际初始状态一致（level 由 intent 指定），并立刻应用一次
        sbDs.setProgress(UiGlass.GLASS_DS[level] - 4);
        apply();
        sc.post(refrost);
        sc.postDelayed(refrost, 200);
        sc.postDelayed(refrost, 500);
    }

    private TextView lastLabel;

    private SeekBar addSlider(LinearLayout parent, String name, int min, int max, int def) {
        LinearLayout row = UiGlass.row(this);
        row.setPadding(0, UiGlass.dp(this, 8), 0, 0);
        TextView t = UiGlass.text(this, name, 11.5f, 0xFFE8E8F0, false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(t, tlp);
        TextView val = UiGlass.text(this, String.valueOf(def), 11.5f, 0xFF9FD0FF, true);
        lastLabel = val;
        row.addView(val);
        parent.addView(row);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(def - min);
        sb.setTag(min);
        parent.addView(sb);
        return sb;
    }

    private int val(SeekBar sb) {
        return sb.getProgress() + (Integer) sb.getTag();
    }
private TextView button(String label) {
        final TextView t = UiGlass.text(this, label, 11.5f, 0xFFE8E8F0, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = UiGlass.round(UiGlass.dp(this, 10), 0x33FFFFFF);
        bg.setStroke(UiGlass.dp(this, 1), 0x55FFFFFF);
        t.setBackground(bg);
        t.setPadding(UiGlass.dp(this, 12), UiGlass.dp(this, 9), UiGlass.dp(this, 12), UiGlass.dp(this, 9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = UiGlass.dp(this, 8);
        t.setLayoutParams(lp);
        return t;
    }

    private void apply() {
        int h = val(sbH), amt = val(sbAmt), ds = val(sbDs), tint = val(sbTint);
        tvH.setText(h + "dp");
        tvAmt.setText(amt + "dp");
        tvDs.setText(String.valueOf(ds));
        tvTint.setText(tint + "%");
        // 磨砂档 = 降采样倍数（同时要同步 Frost.SHARED_DS，整页共享抓取才知道用哪个倍数）
        Frost.SHARED_DS = ds;
        tintAlpha = Math.round(tint * 255f / 100f);
        glass.setTint((tintAlpha << 24) | 0x00FFFFFF);
        glass.setDownScale(ds);
        glass.setLens(h, amt);
        // setLens() 语义是"设置参数并启用折射"，会把开关顶开 → 这里按当前开关状态校正，
        // 否则"关折射"档会被滑块回调悄悄重新打开（实测：A/B 两张图只差按钮文字，就是这个原因）。
        glass.setLensEnabled(lensOn);
        refrost();
    }

    private void refrost() {
        glass.refreshFrost();
        glass.invalidate();
    }

    private View card(String title, String body, int accent) {
        LinearLayout c = UiGlass.col(this);
        c.setBackground(UiGlass.round(UiGlass.dp(this, 18), (accent & 0x00FFFFFF) | 0xD1000000));
        c.setPadding(UiGlass.dp(this, 14), UiGlass.dp(this, 12), UiGlass.dp(this, 14), UiGlass.dp(this, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiGlass.dp(this, 10);
        c.setLayoutParams(lp);
        TextView t = UiGlass.text(this, title, UiGlass.S_BODY, 0xFFFFFFFF, true);
        TextView bd = UiGlass.text(this, body, UiGlass.S_FOOT, 0xFFEDEDF2, false);
        bd.setPadding(0, UiGlass.dp(this, 4), 0, 0);
        c.addView(t);
        c.addView(bd);
        return c;
    }

    private static final int[] BUBBLES = {
            0xFF7C4DFF, 0xFF00BFA5, 0xFFEF5350, 0xFF42A5F5, 0xFFFFA726, 0xFFAB47BC, 0xFF26C6DA, 0xFF66BB6A};

    private static Bitmap sFlower;

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
        Random r = new Random(11);
        int[] cols = {0xFFFF4081, 0xFFFFEB3B, 0xFF76FF03, 0xFF40C4FF, 0xFFFF6E40, 0xFFB388FF};
        for (int i = 0; i < 60; i++) {
            int x = r.nextInt(W), y = r.nextInt(H), rad = 60 + r.nextInt(160);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new RadialGradient(x, y, rad * 1.6f,
                    new int[]{(cols[r.nextInt(cols.length)] & 0x00FFFFFF) | 0xA0000000, 0x00000000},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rad * 1.6f, p);
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
