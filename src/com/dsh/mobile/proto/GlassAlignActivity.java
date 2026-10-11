package com.dsh.mobile.proto;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dsh.mobile.ui.Ui;

/**
 * 【玻璃选型页 · 非产品界面】按用户纠正后的结构搭建：
 *
 * <pre>
 *   下层 = 一条很长、很多彩色的"对话栏"（= 内容，可滚动）
 *   上层 = 4 块候选玻璃（= 固定悬浮件，像聊天页顶栏/输入胶囊那样浮在内容之上）
 * </pre>
 *
 * 用户滚动下层内容时，**彩色块会从 4 块玻璃底下穿过** —— 玻璃里的模糊背景随之变色，
 * 一眼就能同时判断 ① 玻璃块质感 ② 背景是否跟着变（这正是前几轮一直没解决的两件事）。
 * 用户选定其中一块后，全 App 的玻璃件统一换成那一款。
 *
 * <p>4 块候选只有 `tint`（白度）不同：0x33 / 0x42 / 0x4D / 0x59；
 * **磨砂度固定 = 用户已选的第 4 档**（`Ui.glassDownscale()`），白棱用 Demo 参照值 `0x1E`。
 */
public class GlassAlignActivity extends Activity {

    private static final int[] TINTS = { 0x33FFFFFF, 0x42FFFFFF, 0x4DFFFFFF, 0x59FFFFFF };
    private static final String[] LABELS = {
            "A  tint 0x33（20% 白·最透）",
            "B  tint 0x42（26%·Demo 的值）",
            "C  tint 0x4D（30%）",
            "D  tint 0x59（35%·规范默认）",
    };
    private static final int[] BUBBLES = {
            0xFF7C4DFF, 0xFF00BFA5, 0xFFEF5350, 0xFF42A5F5, 0xFFFFA726, 0xFFAB47BC,
            0xFF26C6DA, 0xFF66BB6A, 0xFFEC407A, 0xFF5C6BC0, 0xFF8D6E63, 0xFF29B6F6,
            0xFF101010, 0xFFD4E157, 0xFF00ACC1,
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final float dens = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);

        // ── 下层：很长的多彩"对话栏"（内容层，可滚动）
        final ScrollView sc = new ScrollView(this);
        android.graphics.drawable.GradientDrawable pageBg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{ 0xFF7C4DFF, 0xFF00BFA5, 0xFFFFA726, 0xFFEC407A });
        root.setBackground(pageBg);   // [V0.8] 彩色底：边距处不再是深色"黑条"

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) (26 * dens), (int) (20 * dens), (int) (26 * dens), (int) (40 * dens));   // [V0.9] 留 26dp ≥ 折射采样距离(≈14dp)：最外圈也有内容可采，形变才能贴到边
        for (int i = 0; i < BUBBLES.length; i++) {
            TextView t = new TextView(this);
            t.setText("彩色气泡 #" + (i + 1) + " —— 滚动时它会从上面的玻璃底下穿过");
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            t.setTextColor(0xE6FFFFFF);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding((int) (18 * dens), 0, (int) (18 * dens), 0);
            t.setBackgroundColor(BUBBLES[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (260 * dens));
            lp.bottomMargin = (int) (10 * dens);
            t.setLayoutParams(lp);
            col.addView(t);
        }
        sc.addView(col);
        root.addView(sc, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // ── 上层：4 块候选玻璃（固定悬浮，浮在内容之上）
        final FrostedPanelView[] panels = new FrostedPanelView[TINTS.length];
        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding((int) (26 * dens), (int) (60 * dens), (int) (26 * dens), 0);   // [V0.9] 与色块同宽
        for (int i = 0; i < TINTS.length; i++) {
            FrostedPanelView p = new FrostedPanelView(this, sc);
            p.corner(Ui.dp(this, 18f)).tint(TINTS[i]).hairline(0x1EFFFFFF)
                    .downscale(Ui.glassDownscale()).lens().targetRoot();
            LinearLayout inner = Ui.col(this);
            inner.setPadding((int) (16 * dens), (int) (10 * dens), (int) (16 * dens), (int) (10 * dens));
            TextView t1 = new TextView(this);
            t1.setText(LABELS[i]);
            t1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            t1.setTextColor(0xFF0E1016);
            inner.addView(t1);
            TextView t2 = new TextView(this);
            t2.setText("滚动看：我背后的彩色会跟着变");
            t2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            t2.setTextColor(0xFF23273A);
            inner.addView(t2);
            p.content().addView(inner);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (132 * dens));
            lp.bottomMargin = (int) (10 * dens);
            p.setLayoutParams(lp);
            overlay.addView(p);
            panels[i] = p;
        }
        root.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        // 内容滚动 → 玻璃重新抓取（与真实 App 同一机制）
        sc.setOnScrollChangeListener((v, sx, sy, osx, osy) -> {
            for (FrostedPanelView p : panels) { p.refreshFrost(); p.invalidate(); }
        });
        sc.post(() -> {
            for (FrostedPanelView p : panels) { p.refreshFrost(); p.invalidate(); }
        });
    }
}
