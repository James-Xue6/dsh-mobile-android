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
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 液态玻璃引擎 Demo（对齐《液态玻璃引擎Demo制作文档》第 0 节 6 条验收标准）
 * ===========================================================================
 * 引擎链路（复用本项目已验收的 Frost 捕获引擎）：
 *   内容层(ScrollView) → 离屏捕获(含 2.35×环带 的 pad 溢出区) → 降采样 → 高斯模糊
 *   → SDF 圆角折射 lens（边缘强、向中 smoothstep 渐变、法线推挤 + 切向弧弯）
 *   → 圆角裁剪 → tint 玻璃底色 → 极细边缘受光环 → 磨砂噪点 → 外部柔和悬浮阴影
 *
 * 6 条验收对照：
 *   ① 背景实时模糊 → Frost 捕获 + 降采样模糊（ds=8，磨砂适中，不糊死）
 *   ② 折射形变     → Frost.applyLens（负折射/向内收，边缘强、向中衰减、非均匀）
 *   ③ 连贯边缘受光 → 圆角环 + 对角渐变（左上白高光 → 中部最淡 → 右下受光，eInset 1dp）
 *   ④ 柔和悬浮阴影 → shadowBitmap（上小下大、柔和不实黑）
 *   ⑤ 滚动背景同步 → 滚动即重抓 + Frost 的 scrollY 补偿（帧同步）
 *   ⑥ 带色玻璃按钮 → 蓝紫渐变 / 微红玻璃（自身带色 + 折射 + 悬浮阴影）
 */
public class GlassEngineDemoActivity extends Activity {

    /** 默认磨砂档。实测对比（见 shots/android_lv0..3.png）：ds=8/6/5 会把白网格线糊平，
     *  折射形变肉眼不可辨；ds=4 是"既能看出磨砂、又能看出形变"的临界档，故选它。
     *  生产中若要更磨砂又要保形变，应改回 1 遍高斯 + 更小降采样，而不是加大模糊半径。 */
    private static final int DEFAULT_LEVEL = 1;   // 1 → ds=5（本项目"磨砂适中"的已验收档）

    /** 折射开关（--ei lens 0 可关，做 A/B 对照）。 */
    private boolean lensOn = true;
    private final List<FrostedPanelView> panels = new ArrayList<>();
    private ScrollView sc;
    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : UiGlass.dp(this, 24);
    }


    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        // 磨砂档：默认第 4 档（ds=8，规范定版）；可用 adb 指定以便对比验证：
        //   adb shell am start -n com.dsh.glassdemo/.GlassEngineDemoActivity --ei level 1
        //   level: 0→ds4(最清晰) 1→ds5 2→ds6 3→ds8(最糊)
        int level = getIntent().getIntExtra("level", DEFAULT_LEVEL);
        UiGlass.setGlassLevel(level);
        // --ei lens 0：所有玻璃件关折射（A/B 对照，用来证明折射真的在起作用）
        lensOn = getIntent().getIntExtra("lens", 1) != 0;

        final float dens = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        // ① 花哨底图：彩色对角渐变 + 柔边光斑 + 白色网格线（网格线是折射形变的标尺）
        root.setBackground(new BitmapDrawable(getResources(), flowerBg()));

        // ---------------- 内容层：可滚动列表（玻璃背后必须有东西可糊） ----------------
        sc = new ScrollView(this);
        LinearLayout col = UiGlass.col(this);
        col.setPadding(UiGlass.dp(this, 16), statusBarHeight() + UiGlass.dp(this, 122),
                UiGlass.dp(this, 16), UiGlass.dp(this, 128));
        col.setBackgroundColor(Color.TRANSPARENT);

        col.addView(headline());
        col.addView(hourStrip());
        col.addView(buttonRow());
        col.addView(card("② 折射形变",
                "看玻璃里的白网格线：靠近边缘被推得最强，越往中间越平（smoothstep 缓入缓出）；"
                        + "外侧直线进入玻璃后应弯成弧线，而不是整块平移。", 0xFF2B6CFF));
        col.addView(card("③ 连贯边缘受光",
                "整圈一条极细亮边：左上角白高光最强 → 两侧最淡 → 右下角受光；不断开、不过宽（1dp）。",
                0xFF00A98F));
        col.addView(card("④ 柔和悬浮阴影",
                "玻璃下方有柔和投影，上部小、下部略大、柔和不实黑 —— 这是\"悬浮感\"的来源。",
                0xFFFF7043));
        col.addView(card("① 背景实时模糊",
                "背后是彩色渐变 + 柔边光斑 + 白网格线：磨砂后仍透出色彩与轮廓，不是死白。",
                0xFF8E24AA));
        for (int i = 1; i <= 8; i++) {
            col.addView(card("消息 " + i,
                    "内容层滚动时，玻璃里的背景必须跟着一起动（帧同步捕获 + 滚动补偿）。",
                    BUBBLES[i % BUBBLES.length]));
        }
        col.addView(card("⑤ 滚动背景同步",
                "在玻璃下面滑动：玻璃内的网格线必须与外面对齐、连续、不错位。", 0xFF00838F));
        sc.addView(col);
        root.addView(sc, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // ---------------- 上层：玻璃件（固定悬浮，浮在内容之上） ----------------
        // 沉浸式：背景延伸到状态栏下（必须在建玻璃件之前设置 —— 顶栏要按状态栏高度下移，
        // 否则玻璃顶栏会压住状态栏的时钟/电量，放大镜一样把系统图标也糊进去）
        android.view.Window win = getWindow();
        win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        win.setStatusBarColor(Color.TRANSPARENT);
        win.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        // 顶栏（纯磨砂，不加 lens：顶栏贴边，加 lens 容易在边缘产生杂边）
        FrostedPanelView top = new FrostedPanelView(this, sc);
        top.setLensEnabled(lensOn);
        
        top.corner(UiGlass.dp(this, 20)).tint(0x38FFFFFF).hairline(0x1EFFFFFF)
           .downscale(UiGlass.glassDownscale()).targetRoot();
        top.setPadding(UiGlass.dp(this, 20), UiGlass.dp(this, 13), UiGlass.dp(this, 20), UiGlass.dp(this, 13));
        LinearLayout topInner = UiGlass.col(this);
        topInner.addView(UiGlass.text(this, "液态玻璃引擎 · Demo", UiGlass.S_BODY, 0xFF101018, true));
        topInner.addView(UiGlass.text(this, "真背景模糊 + 折射形变 + 边缘受光", UiGlass.S_FOOT, 0xFF35353D, false));
        top.content().addView(topInner);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiGlass.dp(this, 96), Gravity.TOP);
        tlp.leftMargin = UiGlass.dp(this, 12);
        tlp.rightMargin = UiGlass.dp(this, 12);
        tlp.topMargin = statusBarHeight() + UiGlass.dp(this, 8);   // 避开状态栏（否则压住时钟/电量）
        top.setLayoutParams(tlp);
        root.addView(top);
        panels.add(top);

        // 中部"折射观察窗"：②③④ 的主要观察对象
        // 位置：放在右上（彩色渐变区），不要压在深色卡片上 —— 压在暗底上形变看不见，会误判"折射没生效"
        FrostedPanelView probe = new FrostedPanelView(this, sc);
        probe.setLensEnabled(lensOn);
        probe.setLens(30, 52);      // 演示用：环带 30dp / 幅度 52dp（比生产定版 28/38 更强，形变更易看清）
        
        probe.corner(UiGlass.dp(this, 24)).tint(0x42FFFFFF).hairline(0x1EFFFFFF)
             .downscale(UiGlass.glassDownscale()).targetRoot();
        TextView probeT = UiGlass.text(this, "折射观察窗", UiGlass.S_BODY, 0xFF1B1B24, true);
        probeT.setGravity(Gravity.CENTER);
        probe.content().addView(probeT);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                UiGlass.dp(this, 150), UiGlass.dp(this, 150), Gravity.TOP | Gravity.END);
        plp.topMargin = statusBarHeight() + UiGlass.dp(this, 104);
        plp.rightMargin = UiGlass.dp(this, 14);
        probe.setLayoutParams(plp);
        root.addView(probe);
        panels.add(probe);

        // 左下第二块观察窗：压在彩色卡片上，用来对照"暗底 vs 亮底"下的形变可见度
        FrostedPanelView probe2 = new FrostedPanelView(this, sc);
        probe2.setLensEnabled(lensOn);
        probe2.setLens(30, 52);
        
        probe2.corner(UiGlass.dp(this, 24)).tint(0x42FFFFFF).hairline(0x1EFFFFFF)
              .downscale(UiGlass.glassDownscale()).targetRoot();
        TextView probe2T = UiGlass.text(this, "观察窗 2", UiGlass.S_FOOT, 0xFF1B1B24, true);
        probe2T.setGravity(Gravity.CENTER);
        probe2.content().addView(probe2T);
        FrameLayout.LayoutParams p2lp = new FrameLayout.LayoutParams(
                UiGlass.dp(this, 112), UiGlass.dp(this, 112), Gravity.BOTTOM | Gravity.START);
        p2lp.bottomMargin = UiGlass.dp(this, 92);
        p2lp.leftMargin = UiGlass.dp(this, 14);
        probe2.setLayoutParams(p2lp);
        root.addView(probe2);
        panels.add(probe2);

        // 底部输入胶囊：⑥ 带色玻璃 + ② 折射
        FrostedPanelView input = new FrostedPanelView(this, sc);
        input.setLensEnabled(lensOn);
        input.setLens(28, 44);
        
        input.corner(UiGlass.dp(this, 28)).tint(0x42FFFFFF).hairline(0x1EFFFFFF)
             .downscale(UiGlass.glassDownscale()).targetRoot();
        TextView hint = UiGlass.text(this, "给 Agent 派个任务…", UiGlass.S_BODY, 0xFF2E2E36, false);
        hint.setGravity(Gravity.CENTER);
        input.content().addView(hint);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiGlass.dp(this, 56), Gravity.BOTTOM);
        ilp.leftMargin = UiGlass.dp(this, 12);
        ilp.rightMargin = UiGlass.dp(this, 12);
        ilp.bottomMargin = UiGlass.dp(this, 14);
        input.setLayoutParams(ilp);
        root.addView(input);
        panels.add(input);

        setContentView(root);


        // ---------------- ⑤ 滚动 → 重抓 + 重绘（帧同步机制） ----------------
        final Runnable refrost = () -> {
            for (FrostedPanelView p : panels) {
                p.refreshFrost();
                p.invalidate();
            }
        };
        sc.setOnScrollChangeListener((v, sx, sy, osx, osy) -> refrost.run());
        sc.post(refrost);
        sc.postDelayed(refrost, 120);
        sc.postDelayed(refrost, 400);
    }

    private static final int[] BUBBLES = {
            0xFF3949AB, 0xFF7C4DFF, 0xFF00BFA5, 0xFFEF5350, 0xFF42A5F5, 0xFFFFA726, 0xFFAB47BC, 0xFF26C6DA};

    // ============================ 内容块 ============================

    private View headline() {
        LinearLayout l = UiGlass.col(this);
        l.setPadding(UiGlass.dp(this, 6), 0, UiGlass.dp(this, 6), UiGlass.dp(this, 14));
        TextView t1 = UiGlass.text(this, "液态玻璃", 32f, 0xFFFFFFFF, true);
        t1.setShadowLayer(14f, 0f, 2f, 0x66000000);
        TextView t2 = UiGlass.text(this, "真背景模糊 · 折射形变 · 边缘受光", UiGlass.S_FOOT, 0xFFFFFFFF, false);
        t2.setShadowLayer(8f, 0f, 1f, 0x88000000);
        l.addView(t1);
        l.addView(t2);
        return l;
    }

    /** 逐小时玻璃小方：检验小尺寸下的折射与受光（⑥ 带色玻璃的一种）。 */
    private View hourStrip() {
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = UiGlass.row(this);
        int[] temps = {23, 25, 26, 24, 21, 19};
        String[] tags = {"现在", "14时", "15时", "16时", "17时", "18时"};
        for (int i = 0; i < tags.length; i++) {
            FrostedPanelView glass = new FrostedPanelView(this, sc);
        glass.setLensEnabled(lensOn);
        
            glass.corner(UiGlass.dp(this, 21)).tint(0x40FFFFFF).hairline(0x1EFFFFFF)
                 .downscale(UiGlass.glassDownscale()).targetRoot();
            LinearLayout inner = UiGlass.col(this);
            inner.setGravity(Gravity.CENTER);
            TextView a = UiGlass.text(this, tags[i], 10.5f, 0xFF1B1B24, true);
            a.setGravity(Gravity.CENTER);
            TextView c = UiGlass.text(this, temps[i] + "°", 13f, 0xFF101018, true);
            c.setGravity(Gravity.CENTER);
            inner.addView(a);
            inner.addView(c);
            glass.content().addView(inner);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    UiGlass.dp(this, 62), UiGlass.dp(this, 76));
            lp.rightMargin = UiGlass.dp(this, 9);
            lp.bottomMargin = UiGlass.dp(this, 18);
            glass.setLayoutParams(lp);
            row.addView(glass);
            panels.add(glass);
        }
        hs.addView(row);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = UiGlass.dp(this, 10);
        hs.setLayoutParams(hlp);
        return hs;
    }

    /** ⑥ 带色玻璃按钮：蓝紫渐变 / 微红（自身带色 + 折射 + 悬浮阴影）。 */
    private View buttonRow() {
        LinearLayout row = UiGlass.row(this);
        row.addView(colorButton("蓝紫渐变玻璃", 0xD96084FF, 0xD0965CFF));
        row.addView(colorButton("微红玻璃", 0xC0FF6E8E, 0xC0FF4878));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiGlass.dp(this, 18);
        row.setLayoutParams(lp);
        return row;
    }

    private View colorButton(String label, int c1, int c2) {
        FrostedPanelView g = new FrostedPanelView(this, sc);
        g.setLensEnabled(lensOn);
        g.setLens(20, 26);          // 按钮小：幅度小一些，避免边缘发毛
        
        g.corner(UiGlass.dp(this, 100)).tint(0x33FFFFFF).hairline(0x1EFFFFFF)
         .downscale(UiGlass.glassDownscale()).targetRoot()
         .setColorOverlay(c1, c2);
        TextView t = UiGlass.text(this, label, 13f, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        t.setShadowLayer(6f, 0f, 1f, 0x55000000);
        g.content().addView(t);
        g.setPadding(UiGlass.dp(this, 22), 0, UiGlass.dp(this, 22), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, UiGlass.dp(this, 48));
        lp.rightMargin = UiGlass.dp(this, 10);
        g.setLayoutParams(lp);
        return g;
    }

    private View card(String title, String body, int accent) {
        LinearLayout c = UiGlass.col(this);
        // 卡片 ≈82% 不透明：让花哨背景透出来，玻璃糊到的色彩才丰富
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

    // ============ ① 花哨底图：彩色对角渐变 + 柔边光斑 + 白色网格线 ============
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

        Random r = new Random(7);
        int[] cols = {0xFFFF4081, 0xFFFFEB3B, 0xFF76FF03, 0xFF40C4FF, 0xFFFF6E40, 0xFFB388FF, 0xFFEAFF7F, 0xFFFF1744};
        for (int i = 0; i < 80; i++) {
            int x = r.nextInt(W), y = r.nextInt(H);
            int rad = 40 + r.nextInt(150);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new RadialGradient(x, y, rad * 1.6f,
                    new int[]{(cols[r.nextInt(cols.length)] & 0x00FFFFFF) | 0xB0000000, 0x00000000},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rad * 1.6f, p);
        }
        // 白色网格线：玻璃里必须跟着弯 —— 这是检验折射的标尺。
        // 【重要·别为了看见形变去削弱磨砂】线条要"粗且疏"（150px 间距 / 10px 粗）：
        // 细线（4px）在正常磨砂档（ds=5~8）下会被直接糊平 → 形变看不见 →
        // 于是误把磨砂调弱来"让形变可见"，结果玻璃就不像玻璃了（本 Demo 踩过这个坑）。
        Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        grid.setColor(0x8AFFFFFF);
        grid.setStrokeWidth(10);
        for (int x = 0; x <= W; x += 150) c.drawLine(x, 0, x, H, grid);
        for (int y = 0; y <= H; y += 150) c.drawLine(0, y, W, y, grid);
        sFlower = bm;
        return bm;
    }
}
