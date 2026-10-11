package com.dsh.mobile.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 视觉常量、圆角工具、以及轻量 Markdown 渲染。整体对齐 DSH 桌面版配色。 */
public final class Ui {

    // ============================================================ 主题色板（iOS 语义色）
    //
    // 全 App **只从这里取色**。下面每个字段都是「当前生效的那一档颜色」：启动时和每次
    // 切换主题 / 系统深浅色变化时，由 applyTheme(dark) 用浅色/深色两套同名色整体重写。
    //
    // 视图里绝不要再写 0xAARRGGBB 字面量 —— 写死的那一处切主题时不会跟着变。
    //
    // 命名对照 iOS Human Interface Guidelines 的语义色（不是 DSH 桌面版配色了）：
    //   姓名    token         浅色 / 深色         iOS 语义
    //   正文    INK           #000000 / #FFFFFF   label
    //   次要    INK_SUB       #8E8E93 / #98989F   secondaryLabel（≈ rgba(60,60,67,.6)）
    //   弱化    INK_FAINT     #AEAEB2 / #7C7C80   tertiaryLabel / placeholderText
    //   主色    BRAND         #0A84FF / #0A84FF   systemBlue（文字、描边、图标）
    //   主色实心 BRAND_FILL   #0A84FF / #0A84FF   systemBlue（填充块，配 ON_BRAND）
    //   主色深  BRAND_DEEP    #0A84FF / #0A84FF   选中态标题
    //   主色淡底 BRAND_SOFT   #E9F2FF / #0A2540   systemBlue 12% 淡底
    //   背景    BG            #F2F2F7 / #000000   systemGroupedBackground
    //   卡片    SURFACE       #FFFFFF / #1C1C1E   secondarySystemGroupedBackground
    //   卡片2   SURFACE_2     #FFFFFF / #2C2C2E   tertiarySystemGroupedBackground（组内嵌套）
    //   浅描边  LINE          #E5E5EA / #2C2C2E   卡片极细描边 / 次按钮边（**很浅**）
    //   分隔线  SEP           #C6C6C8 / #38383A   opaqueSeparator（iOS 组内 inset 分隔线）
    //   按下去  PRESS         #D1D1D6 / #3A3A3C   列表行按下高亮
    //   成功    OK            #34C759 / #30D158   systemGreen
    //   错误    ERR           #FF3B30 / #FF453A   systemRed
    //   警告    WARN          #FF9500 / #FF9F0A   systemOrange
    //   按钮字  ON_BRAND      #FFFFFF / #FFFFFF
    //   警告字  ON_WARN       #FFFFFF / #FFFFFF
    //   输入框底 FIELD_BG     #F2F2F7 / #2C2C2E   systemGray6（白卡里的内嵌输入框）
    //   输入条底 FIELD_ALT_BG #F2F2F7 / #2C2C2E
    //   圆形按钮底 CHIP_BG    #E9E9EB / #2C2C2E   systemGray5
    //   停止按钮底 STOP_BG    #E5E5EA / #3A3A3C   systemGray4
    //   提要底  PLAN_BG       #F2F2F7 / #1C1C1E
    //   分段底  SEG_BG        #E9E9EB / #2C2C2E   分段控件底槽（systemGray5）
    //   分段块  SEG_THUMB     #FFFFFF / #636366   分段控件选中的白色滑块
    //   开关关  SWITCH_OFF    #E9E9EA / #39393D
    //   开关开  SWITCH_ON     #34C759 / #30D158
    //   横幅-警告 BANNER_WARN_BG/FG  #FFF8E6/#8A5300 / #3A2E12/#FFD60A
    //   横幅-错误 BANNER_ERR_BG/FG   #FFEEED/#C1271E / #3A1D1D/#FF9F9A
    //   语义描边（都调成"淡色发丝线"，iOS 不用粗彩边）
    //     LINE_AGENT #B9D9FF / #2C4A6E · LINE_APPROVAL #FFD8A8 / #7A5A20
    //     LINE_DANGER #FFC9C5 / #7F3A3A · LINE_QUESTION #B9D9FF / #2C4A6E
    //     LINE_OK #B7E4C7 / #2F5A44 · LINE_SELECTED #B9D9FF / #3D4E85
    //   徽标底-在线 BADGE_OK_BG  #E4F8E9 / #16351F
    //   徽标底-离线 BADGE_OFF_BG #E9E9EB / #26282C
    //   下载成功    LINK_OK      #34C759 / #30D158
    //   代码块底    CODE_BG      #F2F2F7 / #2C2C2E
    //   扫码页（相机取景，两套主题下都保持深底，这是取景页的正确做法）
    //     SCAN_BG #000000 / SCAN_TIP_BG #99000000 / SCAN_PANEL_BG #E6101010

    public static int BG             = 0xFFE6E4F0;
    public static int SURFACE        = 0xFFFFFFFF;
    public static int SURFACE_2      = 0xFFFFFFFF;
    public static int BRAND          = 0xFF5444C8;
    public static int BRAND_FILL     = 0xFF5444C8;
    public static int BRAND_DEEP     = 0xFF453AB8;
    public static int BRAND_SOFT     = 0xFFE9F2FF;
    public static int INK            = 0xFF000000;
    // 2026-10-03 对比度机检：#8E8E93 压奶白卡只有 3.2:1（<4.5）。INK_SUB=#5F5F66 对页面底
    // #E6E4F0 是 5.0:1、对卡底 #FBFAFE 是 6.1:1（双底都过线）；INK_FAINT=#7A7A82 对奶白卡
    // 4.5:1（只用于脚注/提示级小字）。
    public static int INK_SUB        = 0xFF5F5F66;
    public static int INK_FAINT      = 0xFF5C5C64;
    public static int LINE           = 0x0F000000;
    public static int SEP            = 0xFFC6C6C8;
    public static int PRESS          = 0xFFD1D1D6;
    public static int OK             = 0xFF34C759;
    public static int ERR            = 0xFFFF3B30;
    public static int WARN           = 0xFFFF9500;
    public static int ON_BRAND       = 0xFFFFFFFF;
    public static int ON_WARN        = 0xFFFFFFFF;
    public static int FIELD_BG       = 0xFFEDEBF5;
    public static int FIELD_ALT_BG   = 0xFFF2F2F7;
    public static int CHIP_BG        = 0xFFE9E9EB;
    public static int STOP_BG        = 0xFFE5E5EA;
    public static int PLAN_BG        = 0xFFF2F2F7;
    public static int SEG_BG         = 0xFFE9E9EB;
    public static int SEG_THUMB      = 0xFFFFFFFF;
    public static int SWITCH_OFF     = 0xFFE9E9EA;
    public static int SWITCH_ON      = 0xFF34C759;
    public static int BANNER_WARN_BG = 0xFFFFF8E6;
    public static int BANNER_WARN_FG = 0xFF8A5300;
    public static int BANNER_ERR_BG  = 0xFFFFEEED;
    public static int BANNER_ERR_FG  = 0xFFC1271E;
    public static int LINE_AGENT     = 0xFFB9D9FF;
    public static int LINE_APPROVAL  = 0xFFFFD8A8;
    public static int LINE_DANGER    = 0xFFFFC9C5;
    public static int LINE_QUESTION  = 0xFFB9D9FF;
    public static int LINE_OK        = 0xFFB7E4C7;
    public static int LINE_SELECTED  = 0xFFB9D9FF;
    public static int BADGE_OK_BG    = 0x3834C759;   // [毛玻璃 v3] demo `.badge`：绿 22% 淡底
    public static int BADGE_OFF_BG   = 0xFFE9E9EB;
    /** [毛玻璃 v3] demo `.badge` 的字色：浅色 #0f7a35 / 深色 #6ff0a0（绿底上必须用深绿才够对比）。 */
    public static int BADGE_OK_FG    = 0xFF0F7A35;
    /** [毛玻璃 v3] demo `.badge.now`：品牌淡底 + 深字。 */
    public static int BADGE_NOW_BG   = 0x245444C8;
    public static int BADGE_NOW_FG   = 0xFF3A2FA8;
    public static int LINK_OK        = 0xFF34C759;
    public static int CODE_BG        = 0xFFF2F2F7;
    /** 扫码页固定深色（相机取景页，两个主题下都不该变白）。 */
    public static int SCAN_BG        = 0xFF000000;
    public static int SCAN_TIP_BG    = 0x99000000;
    public static int SCAN_PANEL_BG  = 0xE6101010;

    // ---- 高级感三件套（2026-10-02「看着并不高级」返工）
    //
    // HAIRLINE：卡片 1px 极低对比描边。它替掉了原来的 LINE 用作卡片描边 —— 浅色下是
    //           #0000000F（约 6% 黑），深色下是 #FFFFFF14（约 8% 白）。iOS 的卡片从不
    //           用「看得见的灰边」，靠的就是这种几乎看不见、但让边缘不发虚的发丝线。
    // SHADOW  ：卡片柔和阴影的基色（只给 elevation 用；自绘阴影成本高，交给系统）。
    // BRAND_G1/G2：主按钮的细腻纵向渐变（#0A84FF → #0071E3）。纯色实心按钮一眼就是
    //           「系统默认控件」，一段极窄的同色系渐变就能把它从"土"里拉出来。
    public static int HAIRLINE       = 0x0F000000;
    public static int SHADOW         = 0x14000000;
    public static int BRAND_G1       = 0xFF7C4DFF;
    public static int BRAND_G2       = 0xFF448AFF;

    // ---- 毛玻璃 v3（2026-10-06 同步「DSH 掌上通 · 毛玻璃质感 Demo v3」）
    //
    // demo 的真模糊档是 .46（卡）/.60（抽屉）/.38（顶栏）/.42（输入胶囊）；但原生 Android
    // **没有 CSS backdrop-filter**：RenderEffect 只能糊 View 自身，applyWindowBlur 只对
    // 独立窗口（Dialog）有效。所以面板一律走 demo 的**真模糊档**（不是仿玻璃档）——
    // 借 demo 另外三件（135° 斜射高光 / 内发光 / 双层投影）+ 彩色环境背景补质感；
    // 仿玻璃档在不能做真模糊的前提下只会让卡面变成奶白塑料，丢掉 demo 的"有颜色感的玻璃"。
    //
    // **像素级对照（2026-10-06 凌晨模拟器取证）**：原仿玻璃档卡面 RGB≈(247,247,255)，
    // demo 同位 RGB≈(215,212,252)，差约 +32 偏白 —— 正好是 alpha 从 .46 升到 .66 多出的
    // 20%×255≈51 单位。第一版收敛到 demo 真模糊档（见各 token 注释）。
    //
    // 这一版同时换掉了「页面底」：底色从纯色 Ui.BG 改为**彩色环境背景**
    // （AmbientDrawable，见下）—— 玻璃必须透出有颜色的下层才有意义，
    // 透一片均匀灰底 = 白板（demo 原话）。
    public static int GLASS        = 0x2EFFFFFF;   // 卡片体（浅 18% 白 / 深 44% 紫黑）——原生无 blur，白多必发灰，降白让背景色透出
    public static int GLASS_SHEET  = 0x47FFFFFF;   // 抽屉 / 弹窗体（28% / 62%）
    public static int GLASS_BAR    = 0x26FFFFFF;   // 顶部栏（15% / 44%）
    public static int GLASS_INPUT  = 0x7AFFFFFF;   // 输入胶囊（48% / 62%：比 demo .42 厚一档——
                                                 //   原版 .42 太透导致消息重影，加深到 55%，
                                                 //   配合渐隐末端 95% 兜底）
    public static int GLASS_RIM    = 0xF2FFFFFF;   // 顶部棱光（浅色 95% 白 / 深色 40% 白）——提亮提体积感
    public static int GLASS_LO     = 0x29000000;   // 底部暗棱（浅 16% 黑 / 深 14% 白）——"厚度感"来源
    /** 玻璃件的发丝描边：**白色**（demo `--hairline: rgba(255,255,255,.50)`），不是黑边。 */
    public static int GLASS_HAIRLINE = 0x80FFFFFF;
    /** 内发光：`inset 0 0 26px` 的淡白雾（demo `--inner-glow`），让玻璃"厚"起来。 */
    public static int GLASS_INNER   = 0x14FFFFFF;
    /** 磨砂噪点的浓度（demo `.grain` 用 `opacity .07` + `mix-blend-mode: overlay`）。 */
    public static int GLASS_GRAIN   = 0x0AFFFFFF;
    /**
     * [毛玻璃 v3] 悬浮投影色（画在形状**之外**，见 {@link #shadowed}）。
     * demo 是 `rgba(26,16,66,.22)` 远影 + `rgba(26,16,66,.12)` 近影；这里取中间偏重的一档，
     * 因为用户明确要求"阴影加重点，现在看不到阴影"。
     */
    public static int GLASS_SHADOW  = 0x3D1A1042;
    /** 斜射高光（135°）三段：30% → 7% → 全透明（demo `--sheen` 中心区几乎为 0）。 */
    public static int SHEEN_1       = 0x4DFFFFFF;
    public static int SHEEN_2       = 0x12FFFFFF;
    /** 抽屉遮罩：不要死黑，25~35% 才"柔和"。 */
    public static int SCRIM        = 0x2E000000;   // #000 18%（浅色默认；深色档在 applyTheme 里改 45%）

    // ---- 环境背景（玻璃的「下层内容」）6 套配色
    //
    // 逐值抄自存档 demo 的 `body[data-palette=...]` 与 `.ambient i` 两段：
    // 底是一条 178°（≈上→下）三段渐变，上面再压四枚柔边光斑（左上 a / 右上 b / 左下 c / 右下 d）。
    // 光斑用径向渐变画出柔边 —— 于是"面板后面透出来的本来就糊"，
    // 这是原生没有真·背景模糊时最接近 demo 观感的做法。
    public static final class Palette {
        public final String name;
        public final int[] bg;
        public final int a, b, c, d;
        public final float oa, ob, oc, od;
        public final boolean mono;
        Palette(String name, int[] bg, int a, int b, int c, int d,
                float oa, float ob, float oc, float od, boolean mono) {
            this.name = name; this.bg = bg;
            this.a = a; this.b = b; this.c = c; this.d = d;
            this.oa = oa; this.ob = ob; this.oc = oc; this.od = od;
            this.mono = mono;
        }
    }

    public static final Palette[] PALETTES = {
        new Palette("极光", new int[] { 0xFFA892FF, 0xFF8FB0FF, 0xFFFFB0D4 },
                0xFF7A4DFF, 0xFF2F7DFF, 0xFFFF5AA8, 0xFF3FD8B0,
                0.55f, 0.48f, 0.40f, 0.32f, false),
        new Palette("深海", new int[] { 0xFF1F5FD8, 0xFF2F9FE0, 0xFF57E0D8 },
                0xFF2F4DFF, 0xFF00B8FF, 0xFF28E0C8, 0xFF7F6DFF,
                0.55f, 0.48f, 0.40f, 0.32f, false),
        new Palette("暮色", new int[] { 0xFFFF9A5A, 0xFFFF6F9C, 0xFF9A6BFF },
                0xFFFF6A00, 0xFFFF3D7F, 0xFFB06BFF, 0xFFFFD45A,
                0.55f, 0.48f, 0.40f, 0.32f, false),
        new Palette("森林", new int[] { 0xFF2F9E78, 0xFF57C99A, 0xFFA8E6C8 },
                0xFF0F9A6A, 0xFF2FD1A0, 0xFF8FE36F, 0xFF2F9EC9,
                0.55f, 0.48f, 0.40f, 0.32f, false),
        new Palette("紫罗兰", new int[] { 0xFF6A3CFF, 0xFF8B5CF6, 0xFFD18BFF },
                0xFF4A1FD8, 0xFF8B5CF6, 0xFFE07BFF, 0xFF5F7DFF,
                0.55f, 0.48f, 0.40f, 0.32f, false),
        new Palette("素雅", new int[] { 0xFFF2F1F8, 0xFFE8E6F2, 0xFFF4F0F7 },
                0xFF8B7BD8, 0xFF7FA0D8, 0xFFD8A0C0, 0xFF9FD8C8,
                0.20f, 0.20f, 0.20f, 0.20f, true),
    };

    /** 当前配色档（0 = 极光，demo 默认档）。 */
    private static int ambientPalette = 0;

    public static void setAmbientPalette(int i) {
        int n = PALETTES.length;
        ambientPalette = ((i % n) + n) % n;
    }

    public static int ambientPalette() { return ambientPalette; }

    /**
     * [2026-10-07 用户定版] **磨砂度 4 档**（= 玻璃降采样倍数 downscale）。
     *
     * <p>用户实测结论："新装的都很差，**只改变了磨砂度**"。给了 4 档让他横比，
     * 他选 **④ ds=8**，并说"这四个都不错，你留着弄成 4 档" → 做成设置页可选项，默认第 4 档。
     * <pre>
     *   ① ds=4 最轻（背景细节最清楚）  ② ds=5 Demo/规范值
     *   ③ ds=6 更糊                    ④ ds=8 最糊（默认）
     * </pre>
     * 注意：`captureSoftware` 里 `downscale>=6` 会多跑一遍模糊，所以 ③④ 明显更磨砂。
     */
    public static final int[] GLASS_DS = { 4, 5, 6, 8 };
    private static int glassLevel = 1;   // [V0.7] 默认回 Demo 的 5（标准档）；四档仍可在设置里切

    public static void setGlassLevel(int i) {
        glassLevel = Math.max(0, Math.min(GLASS_DS.length - 1, i));
        // 整页共享抓取的降采样必须与面板同档，否则"取块"比例不符（会错位/拉伸）
        com.dsh.mobile.proto.Frost.SHARED_DS = GLASS_DS[glassLevel];
    }

    public static int glassLevel() { return glassLevel; }
    public static int glassDownscale() { return GLASS_DS[glassLevel]; }

    // ---------------------------------------------------------------- 玻璃模式（V3.27）
    /** 液态玻璃：带折射（"液态"的主要线索），视觉最好、开销最大。 */
    public static final String GLASS_MODE_LIQUID = "liquid";
    /** 磨砂玻璃：**无折射**（只模糊 + 受光环），省硬件资源；[V3.27] 用户指定为**默认**。 */
    public static final String GLASS_MODE_FROST = "frost";
    private static String glassMode = GLASS_MODE_LIQUID;   // [V3.52] 默认改成液态（用户要求）

    /**
     * [V3.27 · 用户定版] 切换玻璃模式。
     *
     * <p>[V3.52 · 用户「**磨砂玻璃那个，把现在所有玻璃组件取消折射这些耗资源的，整体替换成
     * 80% 白色的微透磨砂玻璃**」] 现在这一步**同时接管 Compose 那批玻璃件**：
     * <ul>
     *   <li>旧引擎（Java `BackdropBlurDrawable`）：折射幅度 `lensAmt` 液态 38 / 磨砂 0（原有行为不变）；</li>
     *   <li>新玻璃件（Compose `RefractionWindow`）：把 [RefractionWindowTokens] 的 `frostMode` 置真 ⇒
     *       **整个屏的所有玻璃件**都不再走 `lens`（逐像素位移采样，最耗资源那笔）、体色改成
     *       **80% 白**（深色档 80% `#14121C`），只留模糊 + 受光环 —— 也就是用户说的"微透磨砂玻璃"。
     *       （此前这一步**只管旧引擎**，Compose 那批纹丝不动 —— 用户反馈的正是这个。）
     * </ul>
     *
     * <p>⚠️ 折射幅度是在**创建玻璃 drawable 时**烘进去的 ⇒ 切模式后要重绘各屏（宿主调 applyTheme）。
     */
    public static void setGlassMode(String mode) {
        glassMode = GLASS_MODE_LIQUID.equals(mode) ? GLASS_MODE_LIQUID : GLASS_MODE_FROST;
        BackdropBlurDrawable.LENS_AMT_PX = GLASS_MODE_LIQUID.equals(glassMode) ? 38f : 0f;
        // [V3.52] 让 Compose 那批玻璃件真正跟着切（它是 snapshot state ⇒ 会就地重组）
        com.dsh.mobile.glass.RefractionWindowTokens.INSTANCE.setFrostMode(GLASS_MODE_FROST.equals(glassMode));
    }

    public static String glassMode() { return glassMode; }
    public static boolean isLiquidGlass() { return GLASS_MODE_LIQUID.equals(glassMode); }

    public static Palette palette() { return PALETTES[ambientPalette]; }

    /**
     * [2026-10-07] **极光档（0）的环境底图**：与隔离 Demo 同一张 `aurora_bg`。
     *
     * <p>为什么要它：玻璃的"质感"来自**背后有高频细节**可糊。纯渐变糊出来是一片平色 ——
     * 用户拿隔离 Demo 对比后点名「那个玻璃质感就是对的，现在的比那个差」。Demo 好看是因为
     * 背后是那张星空极光图（星点 / 山脊 / 水面全在糊出来的纹理里）。
     * 其余 5 档仍是纯渐变配色（用户要"炫彩"，极光档直接上真图）。
     */
    private static android.graphics.Bitmap ambientImage;
    public static void setAmbientImage(android.graphics.Bitmap b) { ambientImage = b; }
    public static boolean hasAmbientImage() {
        return ambientImage != null && !ambientImage.isRecycled();
    }

    /** 极光底图顶部平均色（系统栏用）；缓存一次。 */
    private static int ambientImageBarColor = 0;

    private static int sampleAmbientImageTop() {
        if (ambientImageBarColor != 0) return ambientImageBarColor;
        try {
            android.graphics.Bitmap b = ambientImage;
            int bw = b.getWidth(), bh = b.getHeight();
            long r = 0, g = 0, bl = 0;
            int n = 0;
            int stepX = Math.max(1, bw / 12);
            for (int y = 0; y < Math.min(40, bh); y += 4) {
                for (int x = 0; x < bw; x += stepX) {
                    int c = b.getPixel(x, y);
                    r += (c >> 16) & 0xFF;
                    g += (c >> 8) & 0xFF;
                    bl += c & 0xFF;
                    n++;
                }
            }
            if (n == 0) return 0;
            ambientImageBarColor = 0xFF000000 | ((int) (r / n) << 16)
                    | ((int) (g / n) << 8) | (int) (bl / n);
        } catch (Throwable t) {
            return 0;
        }
        return ambientImageBarColor;
    }

    /**
     * 状态栏 / 导航栏用的近似底色：渐变顶端与左上光斑按透明度混合的结果。
     * （系统栏在根容器 padding 之外，拿不到环境背景，只能给一个近似的纯色。）
     */
    public static int ambientBarColor() {
        // [2026-10-07] 极光档换成真图后，系统栏不能再取"渐变顶端色"（那是纯渐变档的算法）：
        // 直接采图片顶部的平均色，深浅档同口径压一下。
        if (hasAmbientImage()) {
            int c = sampleAmbientImageTop();
            if (c != 0) {
                if (dark) {
                    float s = 0.42f;
                    c = 0xFF000000 | (Math.round(android.graphics.Color.red(c) * s) << 16)
                            | (Math.round(android.graphics.Color.green(c) * s) << 8)
                            | Math.round(android.graphics.Color.blue(c) * s);
                }
                return c;
            }
        }
        Palette p = palette();
        int c = p.bg[0];
        float o = dark ? (p.mono ? 0.28f : p.oa) : p.oa;
        int a = p.a;
        if (dark) {
            // 深色档：环境背景整体被 brightness(.42) saturate(1.25) 压过，系统栏同口径压暗
            float s = p.mono ? 0.30f : 0.42f;
            c = 0xFF000000 | (Math.round(android.graphics.Color.red(c) * s) << 16)
                    | (Math.round(android.graphics.Color.green(c) * s) << 8)
                    | Math.round(android.graphics.Color.blue(c) * s);
            a = 0xFF000000 | (Math.round(android.graphics.Color.red(a) * s) << 16)
                    | (Math.round(android.graphics.Color.green(a) * s) << 8)
                    | Math.round(android.graphics.Color.blue(a) * s);
        }
        return mixOpaque(c, a, o);
    }

    /** 两个不透明色按 t 混合（0=全取 base，1=全取 over）。 */
    private static int mixOpaque(int base, int over, float t) {
        float u = 1f - t;
        return 0xFF000000
                | (Math.round(android.graphics.Color.red(base) * u + android.graphics.Color.red(over) * t) << 16)
                | (Math.round(android.graphics.Color.green(base) * u + android.graphics.Color.green(over) * t) << 8)
                | Math.round(android.graphics.Color.blue(base) * u + android.graphics.Color.blue(over) * t);
    }

    // ---- 浅灰选中胶囊（2026-10-03 对齐 iOS 健康页参考图 ref-ios-health-cards.png）
    //
    // 参考图里侧栏选中行是一条**浅灰圆角胶囊**（≈#0000000F），**不是**蓝底白字。
    // 蓝底白字会把"选中"做成一个高饱和色块，整页的重心被它拽走；浅灰胶囊只做最低限度的
    // "我在这里"，彩色留给真正要强调的东西（图标、主按钮）—— 这正是参考图"克制用色"的来源。
    //
    // 深色档取 14% 白：纯黑底上 8% 白几乎看不见，14% 才刚好浮起来一档。
    public static int SELECT_BG     = 0x0F000000;   // 浅色 6% 黑 / 深色 14% 白
    public static int SELECT_BG_HI  = 0x14000000;   // 更明确一档（按下 / 强调项）

    // ---- Sadees 视觉（2026-10-03，对齐 ref-sadees/*.WEBP）
    //
    // 参考图的设计语言：**层级全靠色差，不靠描边**——薰衣草灰紫页面底上浮一张奶白渐变卡；
    // 深色胶囊坞里选中项反白；紫→蓝渐变是稀缺资源，只点亮"当前激活"的元素。
    //
    // SURFACE_G1/G2：卡片体的奶白渐变对（左上 → 右下）。**不透明**（2026-10-03 终局返工：
    //           半透明玻璃透出纯色页面底 = 用户点名的"透明弄得太敷衍了"；Sadees 参考图的真实
    //           做法是奶白实体卡浮在灰紫底上，靠阴影和色差分层，不是透）。
    //           浅色 #FBFAFE→#F2F0FA，深色 #1C1B22→#1A1922。CardBg 拿它铺 LinearGradient。
    // SEG_DOCK_*：分段控件的「胶囊坞」——浅色档改 **浅灰坞 + 白滑块**（iOS 原生分段样式；
    //           旧版浅色也是纯黑坞，正是用户点名的"白色状态很多地方是黑色的"）；
    //           深色档保持深坞（黑底上深坞不刺眼），选中 = 白底胶囊 + 深色字不变。
    // BTN_SOFT：次按钮的浅紫灰底（浅 #EDEBF5 / 深 #26242E），配 INK 深字。
    // QUOTE   ：审批卡/提问卡左上角那枚淡色大引号（#C9C4E8 / 深色一档暗紫）。
    public static int SURFACE_G1     = 0xFFFBFAFE;
    public static int SURFACE_G2     = 0xFFF2F0FA;
    /**
     * **列表内卡片**（消息气泡 / 工具行 / 交付物卡…）的玻璃层色：比 {@link #SURFACE_G1}
     * 薄得多，让底下那层真模糊的**炫彩环境背景**透出来。
     *
     * <p>[2026-10-07 用户反馈「所有的卡片内部背景为什么是灰色的」] 原来卡片用的是
     * {@code SURFACE_G1}(=GLASS 白 46%)：46% 白压在炫彩背景上会把颜色**洗掉**（实测卡内
     * `#C8CFFF`，而同位置背景是 `#91ADFF`）—— 看着就是一片灰白。降到 25%/18% 后卡面
     * 仍是清楚的玻璃（棱光 + 发丝线 + 真模糊都在），但颜色跟着背景走。
     */
    public static int GLASS_CARD     = 0x42FFFFFF;
    public static int GLASS_CARD_2   = 0x4DFFFFFF;
    public static int SEG_DOCK_BG    = 0xFFDCD9E8;
    public static int SEG_DOCK_FG    = 0xFF5F5F66;
    public static int SEG_DOCK_ON    = 0xFFFFFFFF;
    public static int SEG_DOCK_ON_FG = 0xFF1C1C1E;
    public static int BTN_SOFT       = 0xFFEDEBF5;
    public static int QUOTE          = 0xFFC9C4E8;

    // ---- 大圆角渐变卡（对齐 ref-ios-health-cards.png 的「彩色分类卡」）
    //
    // 参考图：圆角 ≈24dp、**横向**饱和渐变（左亮右深）、白字、左上小图标、无描边无阴影。
    // 六个色对取自参考图的六张分类卡，饱和度对齐（不荧光、也不灰）。
    // 渐变与主题无关：白字压在饱和色上，浅色/深色两档对比度都够，所以不进 applyTheme。
    // 2026-10-03 终局返工：①回滚**不透明**（0xFF）—— 半透明渐变透出页面底色，卡面白字被
    // 底下透上来的颜色稀释（用户：「白色字体白底看不到」）；②整机检校准 —— 六组渐变
    // **全端**对纯白 ≥4.5:1（WCAG AA），旧值亮端只有 2.2~2.7:1。
    public static final int[] GRAD_ORANGE = { 0xFFC64A18, 0xFFAE3A10 };
    public static final int[] GRAD_PURPLE = { 0xFFA842C4, 0xFF8424A6 };
    public static final int[] GRAD_INDIGO = { 0xFF5C66D4, 0xFF333DB2 };
    public static final int[] GRAD_BLUE   = { 0xFF2076B0, 0xFF155898 };
    public static final int[] GRAD_ROSE   = { 0xFFD24456, 0xFFB2263D };
    public static final int[] GRAD_TEAL   = { 0xFF1F7E8C, 0xFF146272 };
    /** 主色渐变（入口卡专用）：与系统蓝同族，比 {@link #BRAND_FILL} 更有"光"。 */
    public static final int[] GRAD_BRAND  = { 0xFF5E33DC, 0xFF2F62D8 };
    /** 全部渐变（{@link #gradientFor(String)} 按 key 稳定取一组）。 */
    public static final int[][] GRADIENTS = {
            GRAD_ORANGE, GRAD_PURPLE, GRAD_INDIGO, GRAD_BLUE, GRAD_ROSE, GRAD_TEAL };

    /**
     * 压在「**永远是白底**的胶囊」上的字色（渐变卡里的主按钮）。
     *
     * <p>不能用 {@link #INK}：INK 在深色档是白色，而这类胶囊的底固定是纯白（它要压在被
     * 渐变染色的卡面上），白字压白底就消失了。所以它是一个**不随主题变**的固定深色。
     */
    public static final int INK_ON_WHITE = 0xFF1C1C1E;

    /** 当前生效的是不是深色色板。 */
    private static boolean dark = false;

    public static boolean isDark() { return dark; }

    /**
     * 切换整套色板（浅色 / 深色同名色）。
     *
     * 调用时机：
     *   ① MainActivity.onCreate 最开头（必须在创建任何 View 之前）；
     *   ② 用户在设置页切「主题」；
     *   ③ 跟随系统模式下系统深浅色变化（onConfigurationChanged）。
     * ②③ 之后调用方还要把**已经建好**的界面重绘一遍（各 View 的 applyTheme()）。
     */
    public static void applyTheme(boolean useDark) {
        dark = useDark;
        if (useDark) {
            // ---- 深色：iOS systemGroupedBackground 纯黑 + #1C1C1E 卡片 + #0A84FF 主色
            BG             = 0xFF0E0D12;   // 分组背景：近黑带紫
            SURFACE        = 0xFF1C1B22;   // 卡片 / 顶部栏：比背景亮一档
            SURFACE_2      = 0xFF282633;   // 组内嵌套卡片（更亮一层）
            BRAND          = 0xFF9F8FFF;   // systemBlue 深色档：比浅色档提亮一档（深底上更通透）
            BRAND_FILL     = 0xFF8B7CFF;
            BRAND_DEEP     = 0xFFB3A6FF;
            BRAND_SOFT     = 0xFF241E44;   // 蓝色 12% 的深色淡底
            INK            = 0xFFFFFFFF;
            INK_SUB        = 0xFF98989F;
            INK_FAINT      = 0xFF8E8E93;
            LINE           = 0x14FFFFFF;   // 卡片发丝线：#FFFFFF14（与 HAIRLINE 对齐；深色下唯一能"立起卡片"的东西）
            SEP            = 0xFF38383A;   // iOS opaqueSeparator
            PRESS          = 0xFF3A3A3C;
            OK             = 0xFF30D158;
            ERR            = 0xFFFF453A;
            WARN           = 0xFFFF9F0A;
            ON_BRAND       = 0xFFFFFFFF;
            ON_WARN        = 0xFFFFFFFF;
            FIELD_BG       = 0x24FFFFFF;   // [毛玻璃 v3] 白 14%
            FIELD_ALT_BG   = 0x24FFFFFF;
            CHIP_BG        = 0x1AFFFFFF;   // [毛玻璃 v3] 圆形按钮底：白 10%（demo 深色 `.icon-btn`）
            STOP_BG        = 0x26FFFFFF;
            PLAN_BG        = 0xFF1C1B22;
            SEG_BG         = 0x1FFFFFFF;   // [毛玻璃 v3] 白 12%
            SEG_THUMB      = 0x4A4680;
            SWITCH_OFF     = 0xFF39393D;
            SWITCH_ON      = 0xFF30D158;
            BANNER_WARN_BG = 0xFF3A2E12;
            BANNER_WARN_FG = 0xFFFFD60A;
            BANNER_ERR_BG  = 0xFF3A1D1D;
            BANNER_ERR_FG  = 0xFFFF9F9A;
            LINE_AGENT     = 0xFF2C4A6E;
            LINE_APPROVAL  = 0xFF7A5A20;
            LINE_DANGER    = 0xFF7F3A3A;
            LINE_QUESTION  = 0xFF2C4A6E;
            LINE_OK        = 0xFF2F5A44;
            LINE_SELECTED  = 0xFF3D4E85;
            BADGE_OK_BG    = 0x3D34C759;   // [毛玻璃 v3] 深色 `.badge`：绿 24%
            BADGE_OFF_BG   = 0xFF2A2835;
            BADGE_OK_FG    = 0xFF6FF0A0;   // 深色 `.badge` 字色
            BADGE_NOW_BG   = 0x3D8B7DFF;   // 深色 `.badge.now`：品牌 24%
            BADGE_NOW_FG   = 0xFFC3B8FF;
            LINK_OK        = 0xFF30D158;
            CODE_BG        = 0xFF2A2835;
            SCAN_BG        = 0xFF000000;
            SCAN_TIP_BG    = 0x99000000;
            SCAN_PANEL_BG  = 0xE6101010;
            HAIRLINE       = 0x14FFFFFF;   // 深色卡片发丝线：#FFFFFF14（用户指定的深色描边）
            SHADOW         = 0x33000000;   // 纯黑底上阴影不可见，留着只为代码一致
            BRAND_G1       = 0xFF9D6BFF;
            BRAND_G2       = 0xFF5C9DFF;
            // 毛玻璃 v3（深色）：demo 的仿玻璃回退档（见文件顶部注释），
            GLASS          = 0x7029273D;   // rgba(41,39,61,.44) 深色卡：44%
            GLASS_SHEET    = 0x9E1E1C28;   // rgba(28,26,36,.62) 抽屉
            GLASS_BAR      = 0x70181620;   // rgba(24,22,32,.44) 顶栏
            GLASS_INPUT    = 0x9E1E1C28;   // 62% 深色输入胶囊（比浅色更厚：浅字压深底透出太显眼）
            GLASS_RIM      = 0x66FFFFFF;   // 白 40%（[毛玻璃 v3] 高光提亮：30% 立体感出不来）
            GLASS_LO       = 0x24FFFFFF;   // 白 14%（下沿暗棱：深色档用"略亮的白"当棱）
            GLASS_HAIRLINE = 0x33FFFFFF;   // 白 20%（发丝线加强）
            GLASS_INNER    = 0x0FFFFFFF;   // 白 6%
            GLASS_GRAIN    = 0x0AFFFFFF;   // 磨砂噪点：白 4%（深底上噪点太强会显脏）
            GLASS_SHADOW   = 0x66000000;   // 深色档：纯黑 40%（深底上紫影看不出来，用黑）
            SHEEN_1        = 0x22FFFFFF;   // 斜射高光（深色更弱：33%）
            SHEEN_2        = 0x08FFFFFF;   // 8%
            SCRIM          = 0x73000000;   // 深色遮罩 45%
            SELECT_BG      = 0x14FFFFFF;   // 深色选中胶囊：白 8%（纯黑底上要 14% 才浮得起来）
            SELECT_BG_HI   = 0x33FFFFFF;   // 深色强调一档：白 20%
            // 卡体同浅色档：半透明玻璃体（毛玻璃 v3），高光/内发光由 CardBg 叠。
            SURFACE_G1     = GLASS;
            SURFACE_G2     = GLASS;
            GLASS_CARD     = 0x4D14121C;   // 深色：30% 深卡体（同样让背景透出来）
            GLASS_CARD_2   = 0x3D14121C;   // 24%
            SEG_DOCK_BG    = 0xFF26242E;
            SEG_DOCK_FG    = 0xFF9A96AE;
            SEG_DOCK_ON    = 0xFFF2F1FA;
            SEG_DOCK_ON_FG = 0xFF17161C;
            BTN_SOFT       = 0xFF26242E;
            QUOTE          = 0xFF5A5478;
        } else {
            // ---- 浅色：iOS systemGroupedBackground #F2F2F7 + 纯白卡片 + #0A84FF 主色
            BG             = 0xFFE6E4F0;
            SURFACE        = 0xFFFDFCFE;
            SURFACE_2      = 0xFFFFFFFF;
            BRAND          = 0xFF5444C8;
            BRAND_FILL     = 0xFF5444C8;
            BRAND_DEEP     = 0xFF453AB8;
            BRAND_SOFT     = 0xFFEAE6FA;
            INK            = 0xFF000000;
            INK_SUB        = 0xFF5F5F66;
            INK_FAINT      = 0xFF5C5C64;
            LINE           = 0x0F000000;   // 卡片发丝线：6% 黑（几乎看不见，但边缘不发虚）
            SEP            = 0xFFC6C6C8;
            PRESS          = 0xFFD1D1D6;
            OK             = 0xFF34C759;
            ERR            = 0xFFFF3B30;
            WARN           = 0xFFFF9500;
            ON_BRAND       = 0xFFFFFFFF;
            ON_WARN        = 0xFFFFFFFF;
            FIELD_BG       = 0x73FFFFFF;   // [毛玻璃 v3] 卡内嵌输入框：白 45% 玻璃（不再是实色块）
            FIELD_ALT_BG   = 0x73FFFFFF;
            CHIP_BG        = 0x6BFFFFFF;   // [毛玻璃 v3] 圆形按钮底：白 42%（demo `.icon-btn`）
            STOP_BG        = 0x59FFFFFF;
            PLAN_BG        = 0xFFEDEBF5;
            SEG_BG         = 0x59FFFFFF;   // [毛玻璃 v3] 分段控件底槽：白 35% 玻璃
            SEG_THUMB      = 0xFFFFFFFF;
            SWITCH_OFF     = 0xFFD9D6E6;
            SWITCH_ON      = 0xFF34C759;
            BANNER_WARN_BG = 0xFFFFF8E6;
            BANNER_WARN_FG = 0xFF8A5300;
            BANNER_ERR_BG  = 0xFFFFEEED;
            BANNER_ERR_FG  = 0xFFC1271E;
            LINE_AGENT     = 0xFFB9D9FF;
            LINE_APPROVAL  = 0xFFFFD8A8;
            LINE_DANGER    = 0xFFFFC9C5;
            LINE_QUESTION  = 0xFFB9D9FF;
            LINE_OK        = 0xFFB7E4C7;
            LINE_SELECTED  = 0xFFB9D9FF;
            BADGE_OK_BG    = 0x3834C759;   // [毛玻璃 v3] 浅色 `.badge`：绿 22%
            BADGE_OFF_BG   = 0xFFE9E9EB;
            BADGE_OK_FG    = 0xFF0F7A35;
            BADGE_NOW_BG   = 0x245444C8;
            BADGE_NOW_FG   = 0xFF3A2FA8;
            LINK_OK        = 0xFF34C759;
            CODE_BG        = 0xFFE9E6F2;
            SCAN_BG        = 0xFF000000;
            SCAN_TIP_BG    = 0x99000000;
            SCAN_PANEL_BG  = 0xE6101010;
            HAIRLINE       = 0x0F000000;   // 浅色卡片发丝线：#0000000F
            SHADOW         = 0x14000000;   // 浅色卡片柔和阴影
            BRAND_G1       = 0xFF7C4DFF;
            BRAND_G2       = 0xFF448AFF;
            // 毛玻璃 v3（浅色）：直接对齐 **demo 的真模糊档**（不是仿玻璃档）。
            //
            // 为什么不能用「仿玻璃回退档」（.66/.76/.60/.72）：那一档是为"完全做不到模糊"准备的，
            // 叠上去卡面实测 RGB≈(247,247,255) 是奶白塑料；demo 同位实测 (215,212,252) 是有颜色感的玻璃
            // ——差了约 32 个色阶。**结论：不要自己发明中间值，直接抄 demo 的四个档位**
            // （.46 / .60 / .38 / .42）——蒙层决定可读性，这是 BlurView 那篇文章反复强调的一点。
            // 可读性由**真模糊 + 渐隐层末端 95%** 兜底（见 ConversationView），不靠加厚玻璃。
            GLASS          = 0x75FFFFFF;   // 白 46%（卡体）= demo `--glass-card: .46`，逐值对齐
            GLASS_SHEET    = 0x99FFFFFF;   // 白 60%（抽屉 / 弹窗）= demo `--glass-sheet: .60`
            GLASS_BAR      = 0x61FFFFFF;   // 白 38%（顶栏 = demo `--glass-bar: .38`；这一档有真模糊兜底）
            GLASS_INPUT    = 0x7AFFFFFF;   // 白 48%（输入胶囊：demo 是 .42，取 48% 兼顾可读性）
            GLASS_RIM      = 0xF2FFFFFF;   // 白 95%（[毛玻璃 v3] 高光提亮：85% 仍偏弱）
            GLASS_LO       = 0x29000000;   // 黑 16%（下沿暗棱 —— 上亮下暗才有厚度）
            GLASS_HAIRLINE = 0x99FFFFFF;   // 白 60%（第二篇：1px 亮色边框是高级玻璃感的关键）
            GLASS_INNER    = 0x14FFFFFF;   // 白 8%（内发光：三圈合计 0.69，不要把白刷到卡心）
            GLASS_GRAIN    = 0x0AFFFFFF;   // 磨砂噪点：白 4%
            GLASS_SHADOW   = 0x3D1A1042;   // 悬浮投影：rgba(26,16,66,.24)（≈ demo 的 .22）
            SHEEN_1        = 0x4DFFFFFF;   // 斜射高光 30%@TL
            SHEEN_2        = 0x12FFFFFF;   // 7%
        SCRIM          = 0x2E000000;   // 浅色遮罩 18%（35% 压在薰衣草底上发黑 = 用户报的黑窗口）
            SELECT_BG      = 0x0F000000;   // 浅色选中胶囊：黑 6%（参考图侧栏选中行的浅灰胶囊）
            SELECT_BG_HI   = 0x14000000;   // 浅色强调一档：黑 8%
            // 卡体改为**半透明玻璃体**（毛玻璃 v3）：卡片的"光"不再来自不透明奶白渐变，
            // 而来自 CardBg 新加的 135° 斜射高光层 + 内发光；卡体本身只负责"透出环境背景"。
            SURFACE_G1     = GLASS;
            SURFACE_G2     = GLASS;
            GLASS_CARD     = 0x99FFFFFF;   // 浅色：白 60%（环境底换成真图后要够白，深色文字才读得清；demo 的卡片本身也是 ~82% 不透明）
            GLASS_CARD_2   = 0x2EFFFFFF;   // 白 18%
            SEG_DOCK_BG    = 0xFFDCD9E8;
            SEG_DOCK_FG    = 0xFF5F5F66;
            SEG_DOCK_ON    = 0xFFFFFFFF;
            SEG_DOCK_ON_FG = 0xFF1C1C1E;
            BTN_SOFT       = 0xFFEDEBF5;
            QUOTE          = 0xFFC9C4E8;
        }
    }

    // ============================================================ 字号 / 行高 / 圆角（iOS 类型比例）
    //
    // **全 App 的字号只有这一处**：视图里不要再写 15.5f / 12.5f 这类字面量，层级一散，
    // "iOS 感"就没了（旧版就是散在各页，才出现同一档文字大小不一）。
    //
    // 2026-10-03 回归调整：上一轮 iOS 风格重做把字号整体调大（大标题 34 / 正文 17 / 次要 15 /
    // 组标题 13），用户反馈"字体太大，不如之前效果好"。这里按约 -10%~-15% 整体下调，
    // 并对照改版前的实测值（大标题 27、气泡 15.5/14.5、列表标题 15.5、按钮 14.5、
    // 说明 12.5/11.5）取"比改版前略大一点点"的中间值 —— 既回到原来的观感，又保留 iOS 层级。
    // 层级比例保持不变：大标题 29 > 标题 19/17.5 > 正文 15.5 > 次要 13.5 > 脚注 12.5 > 说明 11.5/10.5。
    // [V3.81 · 用户「设置里面再增加一个**字体大小**，这样有人喜欢内容显示多点，可以把字调小点，
    //   弄个**大 / 中 / 小**三个档就行，目前这个是标准档（中）」]
    //   ⇒ 十个字号**从 final 改成可调**：基准值不动，统一乘一个比例 ⇒ 所有页面（对话/设置/抽屉/设备…）
    //     一起变，层级比例保持不变。
    public static final float S_LARGE_BASE   = 29f;   // Large Title（我的设备 / 对话）
    public static final float S_TITLE2_BASE  = 19f;   // Title2
    public static final float S_TITLE3_BASE  = 17.5f; // Title3
    public static final float S_HEAD_BASE    = 15.5f; // Headline / Body
    public static final float S_BODY_BASE    = 15.5f; // Body
    public static final float S_CALLOUT_BASE = 14.5f; // Callout（按钮文字）
    public static final float S_SUB_BASE     = 13.5f; // Subheadline
    public static final float S_FOOT_BASE    = 12.5f; // Footnote（组标题、说明）
    public static final float S_CAP1_BASE    = 11.5f; // Caption1
    public static final float S_CAP2_BASE    = 10.5f; // Caption2（徽标）

    public static float S_LARGE   = S_LARGE_BASE;
    public static float S_TITLE2  = S_TITLE2_BASE;
    public static float S_TITLE3  = S_TITLE3_BASE;
    public static float S_HEAD    = S_HEAD_BASE;
    public static float S_BODY    = S_BODY_BASE;
    public static float S_CALLOUT = S_CALLOUT_BASE;
    public static float S_SUB     = S_SUB_BASE;
    public static float S_FOOT    = S_FOOT_BASE;
    public static float S_CAP1    = S_CAP1_BASE;
    public static float S_CAP2    = S_CAP2_BASE;

    /** 当前字号档位的比例（1.0 = 标准档「中」）。 */
    public static float FONT_SCALE = 1f;

    /**
     * 应用字号档位：小 = 0.9、中 = 1.0（默认）、大 = 1.12。
     *
     * <p>调用方：宿主启动时 + 设置里切换时各调一次，然后**触发一次重建/重组**让界面吃到新值
     * （Compose 侧读的是这些静态常量，靠 `themeRev` 这类状态触发重组）。
     */
    public static void applyFontScale(float scale) {
        FONT_SCALE = scale <= 0f ? 1f : scale;
        S_LARGE   = S_LARGE_BASE   * FONT_SCALE;
        S_TITLE2  = S_TITLE2_BASE  * FONT_SCALE;
        S_TITLE3  = S_TITLE3_BASE  * FONT_SCALE;
        S_HEAD    = S_HEAD_BASE    * FONT_SCALE;
        S_BODY    = S_BODY_BASE    * FONT_SCALE;
        S_CALLOUT = S_CALLOUT_BASE * FONT_SCALE;
        S_SUB     = S_SUB_BASE     * FONT_SCALE;
        S_FOOT    = S_FOOT_BASE    * FONT_SCALE;
        S_CAP1    = S_CAP1_BASE    * FONT_SCALE;
        S_CAP2    = S_CAP2_BASE    * FONT_SCALE;
    }

    /** iOS 分组卡片圆角。 */
    public static final float R_CARD  = 28f;
    /**
     * 大圆角「特征卡」圆角（对齐 ref-ios-health-cards.png：那张彩色分类卡的圆角）。
     * 只给渐变卡/首屏主卡用 —— 普通信息卡仍是 {@link #R_CARD}，圆角档一共就 20/24/26 三档。
     */
    public static final float R_CARD_BIG = 30f;
    /** iOS 弹窗（bottom sheet）顶部圆角。 */
    public static final float R_SHEET = 32f;
    /** 按钮圆角（胶囊）。 */
    public static final float R_PILL  = 999f;
    /** iOS 列表行最小高度。 */
    public static final float H_ROW   = 52f;

    // ============================================================ 间距节奏（2026-10-03「边缘过大」返工）
    //
    // 用户原话：「还有边缘过大的问题」。上一轮把外边距写成了 24~40dp 的一堆散值，
    // 页面主体被挤成中间一条，留白比例完全不对。这里按参考图（iOS 健康页 / 设置页）
    // 把全 App 的横向节奏收敛成**三个数**，视图里只许引用它们，不许再写字面量：
    //
    //   M_SIDE     16dp —— 屏幕左右安全边距（页面级）
    //   M_CARD_PAD 16dp —— 卡片内左右内边距（卡片级）
    //   M_GAP      14dp —— 卡片之间的垂直间距（12~16dp 之间取中）
    //
    // 垂直方向同理，用「大标题 → 副标题 → 内容」三段固定间距，空态/分组标题一律套用：
    //   G_TITLE_SUB  5dp  —— 大标题到副标题（4~6dp）
    //   G_SUB_BODY  18dp  —— 副标题到第一块内容（16~20dp）
    //   G_SECTION    6dp  —— 组标题到组内容
    public static final float M_SIDE     = 16f;
    public static final float M_CARD_PAD = 16f;
    public static final float M_GAP      = 8f;   // [2026-10-07 用户] 卡片间距 14→8dp：省空间
    public static final float G_TITLE_SUB = 5f;
    public static final float G_SUB_BODY  = 18f;
    public static final float G_SECTION   = 6f;

    /**
     * 系统 AlertDialog 的统一入口。
     *
     * 为什么不能直接 {@code new AlertDialog.Builder(activity)}：AlertDialog 的外观来自
     * **Theme**（这里是 AppTheme，Light 系），深色模式下会弹出一张刺眼的白底对话框，
     * 和手搓的深色界面完全脱节。这里按当前主题套一层深/浅对话框主题再建 Builder，
     * 不引入任何新依赖（只用框架自带的 Theme.Material.*.Dialog.Alert）。
     */
    public static android.app.AlertDialog.Builder dialog(Context c) {
        return new android.app.AlertDialog.Builder(dialogContext(c));
    }

    /**
     * 与 {@link #dialog(Context)} 同一套主题的 Context。
     *
     * 对话框里自建的控件（EditText / CheckBox 这种用系统默认配色的）**必须**用这个
     * Context 创建：用 Activity 建出来的控件会拿到 AppTheme（Light 系）的默认文字色，
     * 摆进深色对话框里就是"深色字压深色底"，等于看不见。
     */
    public static android.content.Context dialogContext(Context c) {
        int style = dark ? com.dsh.mobile.R.style.DshDialog_Dark : com.dsh.mobile.R.style.DshDialog;
        return new android.view.ContextThemeWrapper(c, style);
    }

    private Ui() { }

    /**
     * 「允许截屏」策略的进程级镜像（默认 true）。
     *
     * 主窗口的 FLAG_SECURE 由 MainActivity 设/清；但 Dialog（添加设备弹窗、手动添加表单）
     * 是独立窗口，创建它们的地方拿不到 Store，于是统一读这里 —— 策略只有一份，不会两处不一致。
     * Store 在构造和 setAllowScreenshot 时同步这里（见 Store）。
     */
    private static boolean allowScreenshot = true;

    public static void setAllowScreenshot(boolean on) { allowScreenshot = on; }

    public static boolean allowScreenshot() { return allowScreenshot; }

    /**
     * 按当前策略给一个窗口设/清 FLAG_SECURE（主窗口与 Dialog 共用这一条）。
     * 允许截屏（默认）→ 清掉标志，截图/录屏/最近任务缩略图都正常；
     * 用户关掉开关 → 加上标志，窗口内容截图变黑。
     */
    public static void applyScreenshotPolicy(android.view.Window w) {
        if (w == null) return;
        if (allowScreenshot) {
            w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable round(float radius, int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radius);
        d.setColor(fill);
        return d;
    }

    public static GradientDrawable roundStroke(float radius, int fill, float strokeWidth, int stroke) {
        GradientDrawable d = round(radius, fill);
        d.setStroke(Math.max(1, Math.round(strokeWidth)), stroke);
        return d;
    }

    public static GradientDrawable pill(int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(999f);
        d.setColor(fill);
        return d;
    }

    // ------------------------------------------------------------ 参考图精修：渐变卡 / 选中胶囊

    /**
     * 大圆角渐变卡的底：{@link #R_CARD_BIG}（24dp）圆角 + **横向**饱和渐变。
     *
     * <p>方向对齐参考图：左端更亮更饱和、右端沉下去，卡片于是有了"光从左上打过来"的实体感。
     * 只用 {@code GradientDrawable} 自带的 {@code LEFT_RIGHT}，零依赖。
     * 参考图里的渐变卡**没有描边也没有阴影** —— 所以这里不套 {@link CardBg}。
     */
    public static GradientDrawable featureFill(Context c, int[] colors) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(c, R_CARD_BIG));
        return d;
    }

    /**
     * 按 key 稳定取一组渐变（{@link #GRADIENTS} 里挑一个）。
     *
     * <p>为什么用 key 而不是随机：同一台设备每次进「我的设备」颜色必须一样，
     * 否则每次刷新卡片都换色，看着像"页面在闪"。key 用设备 id 即可稳定。
     */
    public static int[] gradientFor(String key) {
        int h = 0;
        if (key != null) {
            for (int i = 0; i < key.length(); i++) h = h * 31 + key.charAt(i);
        }
        return GRADIENTS[(h & 0x7fffffff) % GRADIENTS.length];
    }

    /** 卡片内的分隔线：1px 发丝线（{@link #HAIRLINE}），**不是** SEP 那种看得见的灰线。 */
    public static View cardDivider(Context c) {
        return barHairline(c);
    }

    // ------------------------------------------------------------ 液态玻璃：顶栏 / 输入条 / 真模糊

    /**
     * 顶部栏 / 输入条的玻璃底（**不带棱光**，只有半透明体）。
     *
     * <p>为什么顶栏不套 {@link CardBg}：卡片那套棱光是"四周一圈、上亮下暗"，套在一条
     * 通栏的顶栏上会在**下沿**留下一道亮线 —— 而 iOS 导航栏的下沿是一条发丝线，不是高光。
     * 顶栏的"玻璃感"来自"内容从下面透过去"，所以这里只给半透明体，下沿交给
     * {@link #barHairline} 画的 1px。
     */
    public static GradientDrawable glassBar() {
        // [毛玻璃 v3] 顶栏玻璃 = 体 + 135° 斜射高光。一层 GradientDrawable 就能表达：
        // 高光压在同色体上，等价于"同一色相、更高不透明度"的 TL→BR 三段渐变
        // （原生 GradientDrawable 不支持叠层，见 over()）。
        return glassBar(0f);
    }

    /** 同上，但带圆角（demo 的顶栏是**浮起来的胶囊**：左右 8dp、圆角 22dp）。 */
    public static GradientDrawable glassBar(float radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] { over(GLASS_BAR, SHEEN_1), over(GLASS_BAR, SHEEN_2), GLASS_BAR });
        d.setCornerRadius(radiusDp);
        // demo 的 `.glass-bar` 带 `border: .5px solid rgba(255,255,255,.5)`：玻璃胶囊必须有
        // 一圈白棱才"立"得起来，否则和背景糊在一起。
        d.setStroke(1, GLASS_HAIRLINE);
        return d;
    }

    /**
     * 玻璃件底 = **真模糊底图** + 原来的玻璃层。
     *
     * <p>叠法：模糊层在下（提供"后面真的糊了"的内容），玻璃层在上（提供 tint / 斜射高光 /
     * 内发光 / 白棱发丝线）。两层圆角必须一致（{@code cornerDp}），否则模糊会在圆角外露方角。
     *
     * @param source  被模糊的"下层"View（与面板不必同父）
     * @param panel   面板自身（用来算偏移与圆角）
     */
    public static android.graphics.drawable.Drawable blurredGlass(View source, View panel,
                                                                  float blurDp, float cornerDp,
                                                                  android.graphics.drawable.Drawable glass) {
        android.graphics.drawable.Drawable blur =
                new BackdropBlurDrawable(source, panel, blurDp, 0x00000000, cornerDp);
        return new BlurredGlassDrawable(blur, glass);
    }

    /**
     * 面板的玻璃底 = 真·背景模糊 + 玻璃层，**模糊源在绘制期解析**为
     * 「panel 所在的应用根容器」（含环境背景 → 糊出来是不透明的底）。
     *
     * <p>为什么必须抓整页而不是"列表 / 内容层本身"：列表（ListView）自己的背景是透明的，
     * 只抓它糊出来的仍是半透明层，底下**清晰**的正文照样透上来 —— 看起来就是"没糊"。
     * 2026-10-07 模拟器实测（`evidence/liquid-20261007/L1a-topbar-zoom.png`）：对话页顶栏
     * 背后的日志文字逐字可读，即源取 `list` 时真模糊**没有生效**（代码与
     * {@link #applyBottomShelf} 上方的设计注释本就写着"源取应用根容器"，属回归）。
     *
     * <p>又因为构造期 View 还没 attach（那时拿不到根容器），解析必须推迟到绘制期 ——
     * 所以这条 API 不收 source，只收 panel。
     */
    public static android.graphics.drawable.Drawable blurredGlassRoot(
            View panel, float blurDp, float cornerDp, android.graphics.drawable.Drawable glass) {
        // 用 5 参构造 = 折射幅度取类内默认 LENS_AMT_PX（38px），行为与 V3.21 完全一致
        android.graphics.drawable.Drawable blur =
                new BackdropBlurDrawable(null, panel, blurDp, 0x00000000, cornerDp);
        return new BlurredGlassDrawable(blur, glass);
    }

    /**
     * 同上，但**按调用处指定折射幅度**（px）。
     *
     * <p>给"整屏高的大面板"用：抽屉传 `0f` —— 理由见 {@link BackdropBlurDrawable#LENS_AMT_PX}
     * （默认 38px 会在抽屉内侧 ≈11dp 处留一条竖向色阶缝）。**默认重载不受影响**。
     */
    public static android.graphics.drawable.Drawable blurredGlassRoot(
            View panel, float blurDp, float cornerDp, android.graphics.drawable.Drawable glass,
            float lensAmtPx) {
        android.graphics.drawable.Drawable blur =
                new BackdropBlurDrawable(null, panel, blurDp, 0x00000000, cornerDp, lensAmtPx);
        return new BlurredGlassDrawable(blur, glass);
    }

    /**
     * 此刻是否有玻璃件正在**离屏抓取**。
     *
     * <p>抓取会把目标层软件重画一遍，这一遍里别的元素的**外投影**也会被画进去 ——
     * 糊完就是玻璃里一块块深色斑（用户报「玻璃卡片后面怎么还是有深色的阴影」）。
     * 所以所有"外投影容器"在自己的 onDraw 里先问这一句，抓取期间停画。
     */
    public static boolean isCapturing() {
        return BackdropBlurDrawable.capturingGlobal || com.dsh.mobile.proto.Frost.capturing;
    }

    /**
     * 把一个**已经有底**的 View（按钮 / 卡片 / 气泡）就地升级成真·液态玻璃：
     * 保留它原来那层底当「玻璃层」，下面再垫一层真模糊（源＝应用根容器）。
     *
     * <p>为什么这样包：全工程的卡片/按钮底色是几十处 {@code CardBg} / {@code cardGrad} /
     * {@code secondaryButton} 造出来的，逐个重写既容易漏、又会丢掉各处的语义色
     * （品牌渐变、危险红、强调条…）。包一层即可原样保留玻璃层，只把"假磨砂"换成真模糊。
     *
     * @param cornerDp 模糊底图的圆角，**必须与玻璃层一致**，否则圆角外会露方角
     */
    public static <T extends View> T glassBacked(T v, float blurDp, float cornerDp) {
        if (v == null) return null;
        android.graphics.drawable.Drawable inner = v.getBackground();
        if (inner != null) v.setBackground(blurredGlassRoot(v, blurDp, cornerDp, inner));
        return v;
    }

    /**
     * **列表内**玻璃件（消息卡 / 工具行 / 气泡）的底：**静态环境底模糊**。
     *
     * <p>为什么不逐帧真抓取：这类件一屏十几个，每个都去离屏抓一次整页
     * （{@code Frost.capture} 会把整棵视图树软件重绘一遍）—— 实测滚动
     * {@code dumpsys gfxinfo}：Janky 66%、90th=133ms、95th=200ms，不能上线。
     * 而它们背后**只有环境背景**（列表自身透明、卡片互不重叠），环境背景是静态的
     * → 它的模糊也是静态的：全屏只算一次并缓存，每个件按自己的**窗口坐标**去取那一块，
     * 位置天然对齐、滚动时跟着走、零逐帧成本。
     */
    public static <T extends View> T ambientBacked(T v, float cornerDp) {
        if (v == null) return null;
        android.graphics.drawable.Drawable inner = v.getBackground();
        if (inner != null) v.setBackground(new AmbientGlassDrawable(v, cornerDp, inner));
        return v;
    }

    /** 环境底模糊图的降采样倍数（整屏一张，1/4 足够）。 */
    private static final int AMB_DS = 4;
    private static Bitmap ambBlur;
    private static int ambW, ambH, ambPal = -1;
    private static boolean ambDarkCache;

    /** 环境底（模糊后）整屏缓存；配色 / 主题 / 尺寸变了才重算（几毫秒，一次性）。 */
    private static Bitmap ambientBlur(int screenW, int screenH) {
        if (ambBlur != null && !ambBlur.isRecycled() && ambW == screenW && ambH == screenH
                && ambPal == ambientPalette && ambDarkCache == dark) {
            return ambBlur;
        }
        try {
            int bw = Math.max(1, screenW / AMB_DS), bh = Math.max(1, screenH / AMB_DS);
            Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(b);
            c.scale(1f / AMB_DS, 1f / AMB_DS);
            AmbientDrawable amb = new AmbientDrawable();
            amb.setBounds(0, 0, screenW, screenH);
            amb.draw(c);
            int[] px = new int[bw * bh];
            b.getPixels(px, 0, bw, 0, 0, bw, bh);
            float dens = android.content.res.Resources.getSystem().getDisplayMetrics().density;
            boxBlur(px, bw, bh, Math.max(1, Math.round(26f * dens / AMB_DS)));
            b.setPixels(px, 0, bw, 0, 0, bw, bh);
            Bitmap old = ambBlur;
            ambBlur = b;
            ambW = screenW; ambH = screenH; ambPal = ambientPalette; ambDarkCache = dark;
            if (old != null && old != b && !old.isRecycled()) old.recycle();
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 静态环境底 + 玻璃层（含按下态转发，与 {@link BlurredGlassDrawable} 同契约）。 */
    public static final class AmbientGlassDrawable extends android.graphics.drawable.Drawable {
        private final View panel;
        private final float cornerDp;
        private final android.graphics.drawable.Drawable glass;
        private final android.graphics.Paint paint =
                new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        private final android.graphics.Path clipPath = new android.graphics.Path();
        private final android.graphics.RectF clipRf = new android.graphics.RectF();
        private final int[] loc = new int[2];
        private final android.graphics.Rect src = new android.graphics.Rect();
        private final android.graphics.Rect dst = new android.graphics.Rect();

        AmbientGlassDrawable(View panel, float cornerDp, android.graphics.drawable.Drawable glass) {
            this.panel = panel;
            this.cornerDp = cornerDp;
            this.glass = glass;
        }

        @Override public void draw(android.graphics.Canvas cv) {
            android.graphics.Rect b = getBounds();
            int w = b.width(), h = b.height();
            if (w <= 0 || h <= 0) return;
            float dens = panel.getResources().getDisplayMetrics().density;
            int sw = panel.getResources().getDisplayMetrics().widthPixels;
            int sh = panel.getResources().getDisplayMetrics().heightPixels;
            Bitmap amb = ambientBlur(sw, sh);
            // 整块面板统一裁剪到圆角内：底图 / 玻璃体 / 受光环 / 噪点都在同一形状里
            int save = cv.save();
            if (cornerDp > 0f) {
                clipRf.set(0f, 0f, w, h);
                clipPath.reset();
                clipPath.addRoundRect(clipRf, cornerDp * dens, cornerDp * dens,
                        android.graphics.Path.Direction.CW);
                cv.clipPath(clipPath);
            }
            if (amb != null && !amb.isRecycled()) {
                panel.getLocationInWindow(loc);
                src.set(loc[0] / AMB_DS, loc[1] / AMB_DS,
                        (loc[0] + w) / AMB_DS, (loc[1] + h) / AMB_DS);
                dst.set(0, 0, w, h);
                // [2026-10-07 用户] 「液态玻璃那种边缘扭曲和质感咋没了」：这里原来直接 drawBitmap，
                // 一点折射都没有（列表内卡片为了性能走的是"静态环境底"，没走 Frost 的 lens）。
                // 现在把这一块取出来，**用同一套 CPU lens 做边缘折射**再画回去 —— 成本只是
                // 本卡区域（1/4 采样后 ~1 万像素），零逐帧抓取，性能不受影响。
                int bw = Math.max(1, w / AMB_DS), bh = Math.max(1, h / AMB_DS);
                int sx = Math.max(0, Math.min(amb.getWidth() - 1, src.left));
                int sy = Math.max(0, Math.min(amb.getHeight() - 1, src.top));
                bw = Math.max(1, Math.min(bw, amb.getWidth() - sx));
                bh = Math.max(1, Math.min(bh, amb.getHeight() - sy));
                if (lensPx == null || lensPx.length != bw * bh) {
                    lensPx = new int[bw * bh];
                    if (lensBmp != null && !lensBmp.isRecycled()) lensBmp.recycle();
                    lensBmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                }
                amb.getPixels(lensPx, 0, bw, sx, sy, bw, bh);
                com.dsh.mobile.proto.Frost.applyLens(lensPx, bw, bh,
                        cornerDp * dens / AMB_DS,
                        28f * dens / AMB_DS,      // 环带 28dp（规范值）
                        38f / AMB_DS,             // 幅度 38px（规范值）
                        0);
                lensBmp.setPixels(lensPx, 0, bw, 0, 0, bw, bh);
                cv.drawBitmap(lensBmp, null, dst, paint);
            }
            if (glass != null) {
                glass.setBounds(b);
                glass.draw(cv);
            }
            // [2026-10-07 用户] 「只有 Demo 的中间方形 / 底部胶囊那种质感才是正确的」——
            // 那两块的解剖结构是「底图 → 玻璃体 → **边缘受光环** → **磨砂噪点**」，
            // 而消息卡原来只有前两件套，所以看着是一块平的半透明白。这里补齐后两件套，
            // 全工程玻璃的解剖结构就一致了（与 FrostedPanelView 共用同一套实现）。
            com.dsh.mobile.proto.FrostedPanelView.rim(cv, dens, w, h, cornerDp * dens);
            com.dsh.mobile.proto.FrostedPanelView.grain(cv, w, h, 12);
            cv.restoreToCount(save);
        }

        /** 复用的折射缓冲（每块卡各一份，尺寸随卡变化）。 */
        private int[] lensPx;
        private Bitmap lensBmp;

        @Override public void setAlpha(int a) {
            paint.setAlpha(a);
            if (glass != null) glass.setAlpha(a);
        }
        @Override public void setColorFilter(android.graphics.ColorFilter f) {
            if (glass != null) glass.setColorFilter(f);
        }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        @Override public boolean isStateful() { return glass != null && glass.isStateful(); }
        @Override protected boolean onStateChange(int[] state) {
            return glass != null && glass.setState(state);
        }
        @Override public void setHotspot(float x, float y) {
            if (glass != null) glass.setHotspot(x, y);
        }
        @Override public boolean setVisible(boolean visible, boolean restart) {
            return glass != null && glass.setVisible(visible, restart);
        }
        @Override public void getOutline(android.graphics.Outline o) {
            if (glass != null) glass.getOutline(o);
        }
        @Override public boolean getPadding(android.graphics.Rect p) {
            return glass != null && glass.getPadding(p);
        }
    }

    /**
     * 玻璃件的底 = 模糊层 + 玻璃层，**并且在抓取期间整体不绘制**。
     *
     * <p>为什么必须"整体不绘制"：抓取时会把根容器重画一遍，如果这一层（连同它盖住的玻璃色）
     * 也被画进去，就形成**自我反馈** —— 每帧把上一帧的自己再糊一次，越叠越白。
     * 与 BlurView 的 {@code BlurViewCanvas} 守卫是同一个目的。
     */
    public static final class BlurredGlassDrawable extends android.graphics.drawable.Drawable {
        private final android.graphics.drawable.Drawable blur;
        private final android.graphics.drawable.Drawable glass;

        BlurredGlassDrawable(android.graphics.drawable.Drawable blur,
                             android.graphics.drawable.Drawable glass) {
            this.blur = blur;
            this.glass = glass;
        }

        @Override public void draw(android.graphics.Canvas cv) {
            if (BackdropBlurDrawable.capturingGlobal) return;
            android.graphics.Rect b = getBounds();
            if (blur != null) {
                blur.setBounds(b);
                blur.draw(cv);
            }
            if (glass != null) {
                glass.setBounds(b);
                glass.draw(cv);
            }
        }

        @Override public void setAlpha(int a) {
            if (blur != null) blur.setAlpha(a);
            if (glass != null) glass.setAlpha(a);
        }

        @Override public void setColorFilter(android.graphics.ColorFilter f) {
            if (blur != null) blur.setColorFilter(f);
            if (glass != null) glass.setColorFilter(f);
        }

        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }

        // ---- 状态/热点/内衬全部转发给内层玻璃层。
        // 少了这段，把「已有的按钮底」包进来当玻璃层时，按下态（StateListDrawable）
        // 与圆角 outline 会全部失效 —— 按钮点了没反馈。
        @Override public boolean isStateful() {
            return (blur != null && blur.isStateful()) || (glass != null && glass.isStateful());
        }

        @Override protected boolean onStateChange(int[] state) {
            boolean a = blur != null && blur.setState(state);
            boolean b = glass != null && glass.setState(state);
            return a || b;
        }

        @Override public void setHotspot(float x, float y) {
            if (blur != null) blur.setHotspot(x, y);
            if (glass != null) glass.setHotspot(x, y);
        }

        @Override public boolean setVisible(boolean visible, boolean restart) {
            boolean a = blur != null && blur.setVisible(visible, restart);
            boolean b = glass != null && glass.setVisible(visible, restart);
            return a || b;
        }

        @Override public void getOutline(android.graphics.Outline o) {
            if (glass != null) glass.getOutline(o);
        }

        @Override public boolean getPadding(android.graphics.Rect p) {
            return glass != null && glass.getPadding(p);
        }
    }

    /**
     * 玻璃卡容器：**抓取期间不画自己的子 View**。
     *
     * <p>不这么做的话，卡里的文字/徽标会被烘进模糊层，卡面上就出现自己文字的模糊重影
     * （2026-10-06 首页实测踩到）。BlurView 没这个问题，是因为它的 BlurView 是**空 View**；
     * 我们的"面板"是有内容的容器，所以要在抓取那一刻把自己整个摘出去。
     */
    public static final class GlassCardLayout extends LinearLayout {
        public GlassCardLayout(Context c) { super(c); }

        @Override protected void dispatchDraw(android.graphics.Canvas cv) {
            if (BackdropBlurDrawable.capturingGlobal) return;
            super.dispatchDraw(cv);
        }
    }

    /**
     * 全应用统一用的 TextView：**抓取期间不画自己的文字**。
     *
     * <p>悬浮件（卡片 / 线路行 / 按钮）都要各自的真模糊，而抓取时会把整页重画一遍 ——
     * 如果这些件自己的文字也画进去，就会把自己的字烘进自己的模糊层，形成重影
     * （2026-10-06 首页实测踩到）。BlurView 没这问题是因为它的 BlurView 是**空 View**；
     * 我们这里是"有内容的悬浮件"，所以统一在抓取那一刻把文字摘出去。
     */
    public static final class GlassTextView extends TextView {
        public GlassTextView(Context c) { super(c); }

        @Override protected void onDraw(android.graphics.Canvas cv) {
            if (BackdropBlurDrawable.capturingGlobal) return;
            super.onDraw(cv);
        }
    }

    /** 建一个"抓取期间不画子 View"的玻璃卡容器。 */
    public static GlassCardLayout glassCardLayout(Context c) {
        GlassCardLayout v = new GlassCardLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    /**
     * 两色叠加后的等效色：{@code top} 画在 {@code base} 之上。
     *
     * <p>为什么要它：demo 的玻璃是 `noise + sheen + tint` 三层背景图，
     * 而原生 {@code GradientDrawable} 只能是一层 —— 把"体 + 斜射高光"先算成一个颜色，
     * 才能用一条三段渐变表达出同样的观感（round2 的顶栏就是这么做的）。
     */
    public static int over(int base, int top) {
        float ab = android.graphics.Color.alpha(base) / 255f;
        float at = android.graphics.Color.alpha(top) / 255f;
        float ao = at + ab * (1f - at);
        if (ao <= 0f) return 0;
        int r = Math.round((android.graphics.Color.red(top) * at
                + android.graphics.Color.red(base) * ab * (1f - at)) / ao);
        int g = Math.round((android.graphics.Color.green(top) * at
                + android.graphics.Color.green(base) * ab * (1f - at)) / ao);
        int b = Math.round((android.graphics.Color.blue(top) * at
                + android.graphics.Color.blue(base) * ab * (1f - at)) / ao);
        return (Math.round(ao * 255f) << 24) | (r << 16) | (g << 8) | b;
    }

    /** 顶栏下沿的发丝线（1px、浅色 #00000014 / 深色 #FFFFFF1A）。不要用 SEP 那种明显的灰线。 */
    public static View barHairline(Context c) {
        View v = new View(c);
        v.setBackgroundColor(HAIRLINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.5f))));
        return v;
    }

    /**
     * 把一个顶栏刷成玻璃：bar 换半透明体，line（下沿发丝线）换发丝色。
     *
     * @return bar 的玻璃底 drawable；调用方可以 {@code setAlpha} 做"滚动时逐渐变玻璃"
     */
    public static GradientDrawable topBarGlass(View bar, View line) {
        GradientDrawable bg = glassBar();
        if (bar != null) bar.setBackground(bg);
        if (line != null) line.setBackgroundColor(HAIRLINE);
        return bg;
    }

    /** 系统是否支持真正的窗口/视图模糊（Android 12 / API 31 起）。 */
    public static boolean blurSupported() {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S;
    }

    /**
     * 给一个 View **背后的内容**上真模糊（用于同窗口内的抽屉：内容是兄弟 View）。
     *
     * <p>{@code RenderEffect} 是框架自带的（API 31），不需要任何第三方模糊库。
     * 必须同时切到硬件层：模糊结果会被缓存成一张离屏贴图，抽屉滑动时不再逐帧重算。
     *
     * <p>不支持（API < 31）或设备拒绝时**静默跳过** —— 调用方本来就有遮罩兜底，
     * 绝不能因为模糊不可用就崩或留白。
     *
     * @return true = 模糊真的挂上了
     */
    public static boolean setBackdropBlur(View v, float radiusDp) {
        if (v == null || !blurSupported()) return false;
        try {
            if (!v.isHardwareAccelerated()) return false;
            float r = Math.max(1f, dp(v.getContext(), radiusDp));
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            v.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                    r, r, android.graphics.Shader.TileMode.CLAMP));
            return true;
        } catch (Throwable t) {
            try { v.setRenderEffect(null); } catch (Throwable ignored) { }
            return false;
        }
    }

    /** 撤掉 {@link #setBackdropBlur} 挂上的模糊与硬件层（关抽屉时调，别一直占着离屏贴图）。 */
    public static void clearBackdropBlur(View v) {
        if (v == null) return;
        try { v.setRenderEffect(null); } catch (Throwable ignored) { }
        try { v.setLayerType(View.LAYER_TYPE_NONE, null); } catch (Throwable ignored) { }
    }

    /**
     * 独立窗口（Dialog）的真模糊：系统把**窗口背后的界面**模糊后当作窗口背景画出来。
     *
     * <p>前提是窗口背景本身是透明的（调用方已经 {@code setBackgroundDrawable(TRANSPARENT)}），
     * 否则模糊被不透明背景盖住，等于没做。ROM 关掉模糊（windowBlurEnabled=false）时
     * 什么都不会发生，窗口照旧显示自己的玻璃面板 —— 这就是回退。
     *
     * <p>**故意不给 AlertDialog 用**：AlertDialog 的"面板"就是 windowBackground，
     * 改成透明会让面板整个消失，代价远大于收益。
     *
     * @return true = 模糊真的挂上了
     */
    public static boolean applyWindowBlur(android.view.Window w, float radiusDp) {
        if (w == null || !blurSupported()) return false;
        try {
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            w.setBackgroundBlurRadius(Math.max(1, dp(w.getContext(), radiusDp)));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 顶部安全区（状态栏）高度。 */
    public static int statusBar(Context c) {
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? c.getResources().getDimensionPixelSize(id) : dp(c, 24);
    }

    public static int navBar(Context c) {
        int id = c.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? c.getResources().getDimensionPixelSize(id) : dp(c, 24);
    }

    public static TextView text(Context c, String s, float sizeSp, int color, boolean bold) {
        TextView t = new GlassTextView(c);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        // 行高随字号一起收：字号调小后仍按固定 3dp 加行距会显得松散"空"，
        // 这里按档给额外行距（大标题 3 / 正文 2 / 小字 1），比例与字号同步。
        t.setLineSpacing(dp(c, sizeSp >= 24f ? 3f : (sizeSp >= 14f ? 2f : 1f)), 1.06f);
        t.setIncludeFontPadding(false);
        // 「加粗」分两档（2026-10-02 高级感返工）：
        //   · 大标题（≥24sp）才用真 Bold —— 那是唯一需要"压得住画面"的地方；
        //   · 其余一律 medium。中文字形笔画密，DEFAULT_BOLD 在 15sp 上会把字糊成一团，
        //     这正是"看着不精致"的一个主要来源；medium 有分量又不糊。
        if (bold) t.setTypeface(sizeSp >= 24f ? Typeface.DEFAULT_BOLD : medium());
        // 大标题收紧字距（-0.02em），是 iOS Large Title 的关键细节：字大 + 字距松 = 廉价。
        // 阈值跟着 S_LARGE 走（29sp）：写死 30 的话字号一调小就悄悄失效了。
        if (sizeSp >= 28f) t.setLetterSpacing(-0.02f);
        return t;
    }

    /** 中文正文/按钮/标题统一用的 medium 字重（sans-serif-medium，系统自带，无依赖）。 */
    private static Typeface MEDIUM;

    public static Typeface medium() {
        if (MEDIUM == null) MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        return MEDIUM;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    /**
     * 框架版「渐变卡底」：圆角 + 奶白对角渐变 + 发丝描边（不用自绘 Path）。
     *
     * <p><b>为什么需要它（2026-10-04 真机实测，荣耀 PGT-AN10，逐像素取证）</b>：
     * 自绘 {@link CardBg} 的**渐变卡体**在**超高卡片**上会把整片卡体渲染成灰色 ——
     * 设计色 {@code SURFACE_G1 #FBFAFE} 只在卡片最外侧露出约 8px 窄带，
     * 卡片内部整片被压成 ≈(185,183,193)（-26% 亮度）且呈自上而下平滑渐变；
     * 底部渐隐层边界处无颜色跳变（已排除 bottomFade）。换成框架 GradientDrawable
     * （本方法）后同一坐标实测 (251,250,254) ✓。短卡片上 CardBg 正常，
     * 因此本方法只用于"内容可能被撑很长"的卡片（提问卡 / 审批卡）。
     *
     * <p>与 CardBg 的差别：不含左侧 3dp 强调条与顶部棱光/底部暗线（那些同样依赖自绘 Path）。
     */
    public static android.graphics.drawable.Drawable cardGrad(int radiusPx, int strokePx, int lineColor) {
        return cardGrad(radiusPx, strokePx, lineColor, GLASS);
    }

    /**
     * {@link #cardGrad} 的**薄卡体**版：卡体色换成 {@link #GLASS_CARD}（白 25%），
     * 用在已接真模糊的列表内长卡（提问卡 / 审批卡）—— 卡体太厚会把底下的炫彩背景洗成灰白。
     */
    public static android.graphics.drawable.Drawable cardGradThin(int radiusPx, int strokePx,
                                                                  int lineColor) {
        return cardGrad(radiusPx, strokePx, lineColor, GLASS_CARD);
    }

    private static android.graphics.drawable.Drawable cardGrad(int radiusPx, int strokePx,
                                                               int lineColor, int bodyColor) {
        android.graphics.drawable.GradientDrawable body =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                        new int[] { bodyColor, bodyColor });
        body.setCornerRadius(radiusPx);
        if (strokePx > 0) {
            body.setStroke(strokePx, android.graphics.Color.alpha(bodyColor) < 250
                    ? GLASS_HAIRLINE : lineColor);
        }
        android.graphics.drawable.GradientDrawable sheen =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                        new int[] { SHEEN_1, SHEEN_2, 0x00FFFFFF });
        sheen.setCornerRadius(radiusPx);
        return new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[] { body, sheen });
    }

    /**
     * 顶部栏的圆形图标按钮：36dp 圆形触区 + 居中字形，带 iOS 按压反馈
     * （按下整颗变淡缩一点，抬起回弹）。
     */
    public static TextView circleButton(Context c, String glyph, int fill, int fg) {
        TextView t = new GlassTextView(c);
        t.setText(glyph);
        t.setTextSize(S_BODY);
        t.setTextColor(fg);
        t.setGravity(Gravity.CENTER);
        int s = dp(c, 36);
        t.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        t.setBackground(pill(fill));
        t.setClickable(true);
        tap(t, 0.92f);
        return t;
    }

    /** 全宽分隔线（旧样式，卡片之间用）。 */
    public static View divider(Context c) {
        return hairline(c, SEP, 0);
    }

    /**
     * iOS 的 **inset separator**：1px、颜色比卡片描边深一档、**从文字左缘开始**（左缩进）。
     * iOS 设置那种"组内条目用细分隔线、左端对齐文字"的观感就靠它。
     *
     * @param insetLeftDp 左缩进 dp（一般传组内水平内边距 16）
     */
    public static View insetDivider(Context c, int insetLeftDp) {
        return hairline(c, SEP, insetLeftDp);
    }

    private static View hairline(Context c, int color, int insetLeftDp) {
        View v = new View(c);
        v.setBackgroundColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.5f)));
        lp.leftMargin = dp(c, insetLeftDp);
        v.setLayoutParams(lp);
        return v;
    }

    // ------------------------------------------------------------ iOS 交互反馈

    /**
     * 按压反馈（iOS 的"按下去变淡缩一点"）。
     *
     * 用 OnTouchListener 而不是 StateListAnimator：后者只对 elevation 生效，
     * 而本 App 的卡片用的是自绘 GradientDrawable（无 elevation 语义）。
     * 关键点：onTouch **返回 false** —— 只做视觉，不消费事件，点击照旧走 OnClickListener。
     *
     * @param scale 按下时缩到多少（按钮 0.97，列表行 1.0 = 只压暗不缩放）
     */
    public static void tap(final View v, final float scale) {
        if (v == null) return;
        if (Boolean.TRUE.equals(v.getTag(com.dsh.mobile.R.id.tag_press))) return;   // 防重复挂
        v.setTag(com.dsh.mobile.R.id.tag_press, Boolean.TRUE);
        v.setOnTouchListener(new View.OnTouchListener() {
            private boolean down;

            @Override
            public boolean onTouch(View view, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        down = true;
                        view.animate().cancel();
                        view.animate().alpha(0.72f)
                                .scaleX(scale).scaleY(scale).setDuration(90).start();
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        if (down) {
                            down = false;
                            view.animate().cancel();
                            view.animate().alpha(1f).scaleX(1f).scaleY(1f)
                                    .setDuration(160).start();
                        }
                        break;
                    default:
                        break;
                }
                return false;   // 不消费：click / longClick 全按原样走
            }
        });
    }

    /** 按压反馈（按钮默认 0.97 的缩放）。 */
    public static void tap(View v) { tap(v, 0.97f); }

    /**
     * 轻触觉反馈（2026-10-04 用户要求「后退加一下震动，增加使用质感」）。
     *
     * <p>用框架自带的 {@link android.view.HapticFeedbackConstants#VIRTUAL_KEY}，不引入
     * Vibrator 权限也不需要任何第三方库。**故意不带 FLAG_IGNORE_GLOBAL_SETTING**：
     * 用户在系统里关掉"触摸振动"时就该安静 —— 尊重系统设置比"一定要震"重要。
     * 设备没有振动器 / 系统拒绝时静默跳过，绝不影响点击本身。
     */
    public static void haptic(View v) {
        if (v == null) return;
        try {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        } catch (Throwable ignored) { }
    }

    /** 每个父容器上的 {@link TouchDelegateGroup}（键是父容器本身；弱引用，容器回收后自动消失）。 */
    private static final java.util.WeakHashMap<android.view.ViewGroup, TouchDelegateGroup>
            TOUCH_DELEGATES = new java.util.WeakHashMap<>();

    /**
     * 把 {@code v} 的**可点区域**撑到至少 {@code minDp} —— 视觉尺寸一点不动。
     *
     * <p>为什么需要：这一轮把图标/按钮的**视觉**尺寸调小了（图标 ≈ 正文行高），
     * 但手指没变小 —— 视觉一缩就点不准了。这里在**父容器**上装一个
     * {@link android.view.TouchDelegate}，把子 View 的触区向外扩：
     * 点"按钮旁边一点点"也算点按钮。
     *
     * <p>两个限制，调用方要知道：
     * <ol>
     *   <li>外扩矩形最多到**父容器的边界**（父容器不够大时能扩多少扩多少）——
     *       所以顶栏那种一行只有 36dp 高的容器里，实际触区是 36dp 而不是 48dp；</li>
     *   <li>用 post 延迟到布局完成后再装（那时才有真实的宽高与父坐标）。</li>
     * </ol>
     *
     * <p>父容器可能有多个这样的子 View，而框架的 {@code ViewGroup.setTouchDelegate}
     * 一个父容器只认一个 —— 所以按父容器存一个 {@link TouchDelegateGroup}。
     */
    public static void expandTouch(final View v, final float minDp) {
        if (v == null) return;
        v.post(new Runnable() {
            @Override public void run() {
                if (v.getWidth() <= 0 || v.getHeight() <= 0) return;
                android.view.ViewParent vp = v.getParent();
                if (!(vp instanceof android.view.ViewGroup)) return;
                android.view.ViewGroup parent = (android.view.ViewGroup) vp;
                int min = dp(v.getContext(), minDp);
                int dx = Math.max(0, (min - v.getWidth()) / 2);
                int dy = Math.max(0, (min - v.getHeight()) / 2);
                if (dx == 0 && dy == 0) return;   // 本来就够大
                // 矩形必须是**父坐标系**的（getHitRect 给的就是子 View 在父里的位置），
                // 再夹进父容器边界：越界的部分父容器根本收不到事件，白扩。
                android.graphics.Rect r = new android.graphics.Rect();
                v.getHitRect(r);
                r.left   = Math.max(0, r.left - dx);
                r.top    = Math.max(0, r.top - dy);
                r.right  = Math.min(parent.getWidth(), r.right + dx);
                r.bottom = Math.min(parent.getHeight(), r.bottom + dy);
                if (r.width() <= v.getWidth() && r.height() <= v.getHeight()) return;
                // 父容器自己可点（被 Ui.tap/tapRow 挂过触摸监听）时**不接管**：
                // 框架没有公开的 getOnTouchListener()，接管就会把它的按压反馈顶掉。
                // 目前所有调用点（顶栏行 / 输入条）都是不可点的普通容器，这条守卫是保险。
                if (parent.isClickable()) return;
                TouchDelegateGroup g = TOUCH_DELEGATES.get(parent);
                if (g == null) {
                    g = new TouchDelegateGroup();
                    TOUCH_DELEGATES.put(parent, g);
                    parent.setOnTouchListener(g);
                }
                g.add(new android.view.TouchDelegate(r, v));
            }
        });
    }

    /**
     * 一个父容器上挂多个 {@link android.view.TouchDelegate} 的合集。
     *
     * <p>为什么需要它：{@code ViewGroup.setTouchDelegate} 只能挂一个，一行里三颗按钮
     * 就会互相覆盖。这里自己转发：父容器**没被子 View 吃掉**的事件（也就是落在按钮
     * 外扩区里的那些）逐个交给各按钮的 delegate 试一遍。
     *
     * <p>不消费事件时返回 false —— 父容器自己的 OnClickListener 照旧生效。
     * 父容器**原本**的 OnTouchListener 无法读取（框架没公开 getOnTouchListener），
     * 所以 {@link #expandTouch} 只在父容器不可点时才接管它。
     */
    private static final class TouchDelegateGroup implements View.OnTouchListener {
        private final List<android.view.TouchDelegate> delegates = new ArrayList<>();

        void add(android.view.TouchDelegate d) { delegates.add(d); }

        @Override public boolean onTouch(View v, android.view.MotionEvent e) {
            for (int i = 0; i < delegates.size(); i++) {
                if (delegates.get(i).onTouchEvent(e)) return true;
            }
            return false;
        }
    }

    /**
     * 列表行的按下高亮：正常态用传进来的 drawable，按下时换成 PRESS 灰底。
     * iOS 的行是按**整行底色**给反馈，不是只变文字色。
     *
     * 用 OnTouchListener 手动换背景（而不是 StateListDrawable）：ListView 的子 View
     * 不一定能收到 pressed 状态（选择器是画在列表层的），手动换色在任何容器里都成立。
     * 仍然返回 false，点击/长按照旧。
     */
    public static void tapRow(final View v, GradientDrawable normal, int pressFill) {
        tapRow(v, normal, pressFill, 0f);
    }

    /**
     * 同上，但行底也走**真·液态玻璃**（背景模糊 + 边缘折射）。
     *
     * @param glassCornerDp &gt; 0 = 把正常态/按下态两层都包上真模糊，圆角传行的圆角；
     *                      0 = 老行为（纯色行底）。抽屉的会话行用它。
     */
    public static void tapRow(final View v, GradientDrawable normal, int pressFill,
                              float glassCornerDp) {
        if (v == null || normal == null) return;
        final GradientDrawable pressed = normal.getConstantState() == null
                ? round(normal.getCornerRadius(), pressFill)
                : (GradientDrawable) normal.getConstantState().newDrawable().mutate();
        pressed.setColor(pressFill);
        final android.graphics.drawable.Drawable normalD = glassCornerDp > 0f
                ? blurredGlassRoot(v, 26f, glassCornerDp, normal) : normal;
        final android.graphics.drawable.Drawable pressedD = glassCornerDp > 0f
                ? blurredGlassRoot(v, 26f, glassCornerDp, pressed) : pressed;
        v.setBackground(normalD);
        // 安全网（2026-10-04 用户报「子智能体弹窗里一滑动就变色」）：手势被父容器
        // （ScrollView / ListView）抢走时，子 View **不保证**收到 ACTION_CANCEL ——
        // 那时按下态就永远停在灰底上，看起来像"滑动把行染色了"。这里挂一个延时还原，
        // 无论走哪条路（UP / CANCEL / 被抢走），灰底最长只存活 RESTORE_MS。
        final Runnable restore = new Runnable() {
            @Override public void run() {
                v.setBackground(normalD);
                if (v.getBackground() != normalD) v.setBackground(normalD);
            }
        };
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        view.setBackground(pressedD);
                        view.removeCallbacks(restore);
                        view.postDelayed(restore, PRESS_RESTORE_MS);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                    case android.view.MotionEvent.ACTION_OUTSIDE:
                        view.removeCallbacks(restore);
                        view.setBackground(normalD);
                        break;
                    default:
                        break;
                }
                return false;   // 不消费：click / longClick 全按原样走
            }
        });
    }

    /** {@link #tapRow} 按下态的最长存活时间：手势被父容器抢走时的兜底还原。 */
    private static final long PRESS_RESTORE_MS = 500L;

    // ------------------------------------------------------------ iOS 开关（自绘，无依赖）

    /**
     * iOS 风格开关：51x31dp 胶囊轨道 + 白色圆钮，开=绿/关=灰，点一下带滑动动画。
     *
     * 为什么自绘：框架只有 Switch/ToggleButton，它们的轨道尺寸/留白/配色都不是 iOS 的比例，
     * 用 tint 也改不动"轨道粗细 + 圆钮边距"。这里纯 Canvas 画，不引入任何依赖。
     */
    public static final class Switch extends View {
        public interface OnChange { void onChanged(boolean on); }

        private final android.graphics.Paint track = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint knob = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final float wPx, hPx, knobPx, padPx;
        private boolean checked;
        private float pos;                       // 0=关 1=开（动画中间值）
        private OnChange listener;

        public Switch(Context c) {
            super(c);
            wPx = dp(c, 51);
            hPx = dp(c, 31);
            knobPx = dp(c, 27);
            padPx = dp(c, 2);
            knob.setColor(0xFFFFFFFF);
            knob.setShadowLayer(dp(c, 1), 0, dp(c, 0.8f), 0x40000000);
            setLayoutParams(new LinearLayout.LayoutParams(Math.round(wPx), Math.round(hPx)));
        }

        public boolean isChecked() { return checked; }

        public void setChecked(boolean on) { setChecked(on, false); }

        public void setChecked(boolean on, boolean animate) {
            checked = on;
            float target = on ? 1f : 0f;
            if (animate) {
                android.animation.ValueAnimator a =
                        android.animation.ValueAnimator.ofFloat(pos, target);
                a.setDuration(160L);
                a.addUpdateListener(an -> {
                    pos = ((Float) an.getAnimatedValue()).floatValue();
                    invalidate();
                });
                a.start();
            } else {
                pos = target;
                invalidate();
            }
        }

        public void setOnChange(OnChange l) { listener = l; }

        @Override
        protected void onMeasure(int ws, int hs) {
            int w = Math.round(wPx);
            int h = Math.round(hPx);
            // 父容器给的空间不够时**收缩**（iOS 开关不该把行挤爆）；EXACTLY/AT_MOST 都认
            int wm = MeasureSpec.getMode(ws);
            if (wm == MeasureSpec.AT_MOST || wm == MeasureSpec.EXACTLY) {
                w = Math.min(w, MeasureSpec.getSize(ws));
            }
            int hm = MeasureSpec.getMode(hs);
            if (hm == MeasureSpec.AT_MOST || hm == MeasureSpec.EXACTLY) {
                h = Math.min(h, MeasureSpec.getSize(hs));
            }
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onDraw(android.graphics.Canvas cv) {
            int w = getWidth() > 0 ? getWidth() : Math.round(wPx);
            int h = getHeight() > 0 ? getHeight() : Math.round(hPx);
            float r = h / 2f;
            track.setColor(blend(SWITCH_OFF, SWITCH_ON, pos));
            cv.drawRoundRect(0, 0, w, h, r, r, track);
            float kn = Math.min(knobPx, h - padPx * 2f);
            float cx = padPx + kn / 2f + (w - kn - padPx * 2f) * pos;
            cv.drawCircle(cx, h / 2f, kn / 2f, knob);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    setAlpha(0.75f);
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    setAlpha(1f);
                    checked = !checked;
                    setChecked(checked, true);
                    if (listener != null) listener.onChanged(checked);
                    performClick();
                    return true;
                case android.view.MotionEvent.ACTION_CANCEL:
                    setAlpha(1f);
                    return true;
                default:
                    return super.onTouchEvent(e);
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    /** 两个 ARGB 颜色按 t(0..1) 线性混合（开关轨道从灰滑到绿）。 */
    private static int blend(int from, int to, float t) {
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        return Color.argb(
                Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t),
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    // ------------------------------------------------------------ iOS 分段控件 / 列表结构

    /**
     * 分段控件的底槽（Sadees「深色胶囊坞」：整条 #17161C 深色胶囊，内衬 3dp）。
     * 往里加 {@link #segmentItem}；选中态用 {@link #paintSegment} —— 选中项 = 白底胶囊反色。
     */
    public static LinearLayout segmentTrack(Context c) {
        LinearLayout l = row(c);
        l.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        l.setBackground(pill(SEG_DOCK_BG));
        l.setPadding(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
        return l;
    }

    /** 分段控件的一项（默认未选中；选中态用 {@link #paintSegment}）。 */
    public static TextView segmentItem(Context c, String s) {
        TextView t = text(c, s, S_FOOT, SEG_DOCK_FG, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 4), dp(c, 7), dp(c, 4), dp(c, 7));
        t.setClickable(true);
        paintSegment(t, false);
        return t;
    }

    /**
     * 画分段项（Sadees 胶囊坞选中态）：选中 = 白底胶囊 + 深色字，未选中 = 透明 + 灰字。
     * 圆角 999 = 胶囊（参考图的选中项是一条白胶囊，不是小圆角方块）。
     */
    public static void paintSegment(TextView t, boolean on) {
        if (t == null) return;
        t.setTextColor(on ? SEG_DOCK_ON_FG : SEG_DOCK_FG);
        t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setBackground(on ? pill(SEG_DOCK_ON) : round(0, 0x00000000));
    }

    /** 组标题：iOS 的 13sp 灰色小字（"分组列表"上方那一行）。 */
    public static TextView groupTitle(Context c, String s) {
        TextView t = text(c, s, S_FOOT, INK_SUB, false);
        t.setPadding(dp(c, 16), dp(c, 6), dp(c, 16), dp(c, 6));
        return t;
    }

    /**
     * 一张 iOS 分组卡片：纯白/深灰底、14dp 圆角、**1px 极低对比**发丝描边 + 一层极浅阴影。
     *
     * <p>2026-10-02 高级感返工：旧版只有「白底 + 灰边」，卡片是**平贴**在灰底上的，
     * 用户的原话就是"看着并不高级，一点也没"。现在改成本项目的层次公式：
     * <pre>
     *   背景 #F2F2F7  +  卡片 #FFFFFF  +  1px #0000000F 描边  +  elevation 2dp 柔和阴影
     * </pre>
     * 阴影交给系统 elevation（硬件模糊，质量比自绘的同心圆假阴影高得多）；
     * {@link CardBg#getOutline} 把外轮廓交给系统，所以阴影严格贴着 14dp 圆角走，
     * 不会出现"方角影子 + 圆角卡片"那种廉价错位。
     *
     * <p>深色档不靠阴影（黑底上阴影本来也看不见），靠 #1C1C1E 卡片 + #FFFFFF14 描边。
     */
    public static LinearLayout card(Context c) {
        return card(c, 0x00000000);
    }

    /**
     * 带左侧强调条的卡片：3dp 主色条贴在卡片左缘（圆角内裁剪）。
     *
     * <p>替代旧版的「整卡一圈蓝框」—— 那是用户点名的"土"元素。一整圈彩边会抢走内容的注意力，
     * 左侧一条 3dp 的强调条只提示"这是当前项"，安静得多。
     *
     * @param accent 强调条颜色；{@code 0x00000000} = 不画条（等价于 {@link #card}）
     */
    public static LinearLayout accentCard(Context c, int accent) {
        return card(c, accent);
    }

    private static LinearLayout card(Context c, int accent) {
        LinearLayout c0 = col(c);
        // Sadees 奶白实体渐变卡：SURFACE_G1 → SURFACE_G2 对角渐变 + 发丝描边 + **真阴影**。
        //
        // 2026-10-03 终局返工（用户点名「对话卡片没有阴影」）：卡体已是不透明奶白渐变，
        // elevation 可以安全使用（旧版禁用是因为半透明体会把阴影透成灰环）。浅色 3dp 明显，
        // 深色 2dp（黑底上阴影本就不可见，只留一致性）；CardBg.getOutline 已把外轮廓交给
        // 系统，阴影严格贴圆角走。
        //
        // **真机 bug（2026-10-03 晚，用户手机实拍）**：设置页折叠卡也是 card()——渐变体
        // 扫过大面积展开区时，内嵌的 FIELD_BG（#EDEBF5）说明块与渐变端色（#F2F0FA）几乎
        // 同亮度，整块内容看着"卡里套了个脏灰框、文字贴在灰底上"。修法：card() 分两种——
        // **大面积可展开容器用 flatCard（纯色白）**，独立信息卡才用渐变体。
        if (accent == 0x00000000) {
            // 折叠卡/大面积容器：厚玻璃（GLASS_SHEET 76%）+ 发丝描边 + 阴影（无强调条）。
            // 用比卡片更厚的一档：这类卡里嵌着不透明的 FIELD_BG 说明块，太透会显得"脏"。
            c0.setBackground(new CardBg(dp(c, R_CARD), GLASS_SHEET, dp(c, 1f), LINE, 0, dp(c, 3f)));
            c0.setElevation(0f);   // [液态玻璃 2026-10-07] 半透明卡面不能挂框架 elevation：阴影会透过卡面变灰环
        } else {
            c0.setBackground(new CardBg(dp(c, R_CARD),
                    new int[] { SURFACE_G1, SURFACE_G2 }, dp(c, 1f), LINE, accent, dp(c, 3f)));
            // [液态玻璃 2026-10-07] 强调条卡同样是半透明卡面 → 不能挂框架 elevation
            // （阴影透上来把卡面压灰 + 边缘黑边）。阴影一律走 Ui.shadowed（画在形状之外）。
            c0.setElevation(0f);
        }
        c0.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return c0;
    }

    /**
     * 纯色大卡（无渐变无高光棱）：给「面积大、内嵌表单/说明块」的容器用——
     * 渐变体扫过大面积时会和内嵌 FIELD_BG 同亮度混成一片灰（真机实拍 bug）。
     */
    public static LinearLayout flatCard(Context c) {
        return card(c, 0x00000000);
    }

    /**
     * 「入口卡」：**一张整体卡片** = 左侧圆角小图标 + 两行文字（主/次）+ 右侧细箭头。
     *
     * <p>2026-10-03 修用户点名的坏卡片：旧版「＋ 添加设备」把「＋ 添加设备」做成一整行
     * **居中**的文字，下面再挂一行左对齐的说明 —— 居中和左对齐混在一张卡里，看起来就像
     * "灰卡里套了个白方框、字还没对齐"。现在统一成参考图那种**一行两列**的入口行：
     * 图标列固定宽、文字列左对齐、箭头贴右缘，主次两行永远左对齐到同一条基线。
     *
     * <p>卡片本身仍是玻璃（{@link #card}），没有任何内嵌白框。
     *
     * @param iconRes 左侧矢量图标（18~20dp 的 {@link #I_BODY} 档）
     * @param tint    图标色（一般 {@link #BRAND}）
     */
    public static LinearLayout entryCard(Context c, int iconRes, int tint, String title, String sub) {
        return entryCard(c, null, iconRes, tint, tintSoft(tint), INK, INK_SUB, INK_FAINT, title, sub);
    }

    /**
     * 渐变版入口卡：整卡就是一张 {@link #featureFill} 大圆角渐变卡（参考图 B 的制式），
     * 文字全白、图标底 22% 白、箭头 85% 白。
     *
     * <p>用它的是「需要突出的入口」——本 App 目前只有「＋ 添加设备」这一处：
     * 一个设备都没有时它就是整页唯一的主操作，白灰卡会让页面看起来"没做完"，
     * 一张渐变卡则给了页面重心，也把参考图里那套彩色卡真正用上（用户点名缺这个）。
     */
    public static LinearLayout entryCardGradient(Context c, int iconRes, int[] grad,
                                                 String title, String sub) {
        final int white = 0xFFFFFFFF;
        // 图标底改 25% 黑（对比度机检：白 22% 底上的白字形 ≥4.5 不达标，黑 25% ≥7:1）
        return entryCard(c, grad, iconRes, white, alpha(0x000000, 0.25f),
                white, alpha(white, 0.82f), alpha(white, 0.85f), title, sub);
    }

    /**
     * 入口卡的内核：一张卡 + 一行三列（图标 / 左对齐两行文字 / 右箭头）。
     *
     * @param grad {@code null} = 玻璃卡；非 null = 大圆角渐变卡（无描边无阴影）
     */
    private static LinearLayout entryCard(Context c, int[] grad, int iconRes, int tint, int tileFill,
                                          int titleColor, int subColor, int chevColor,
                                          String title, String sub) {
        LinearLayout card = col(c);
        // Sadees 奶白实体渐变卡（与 Ui.card 同一套渐变体，入口卡 4dp 阴影更浮一层）。
        card.setBackground(grad == null
                ? new CardBg(dp(c, R_CARD), new int[] { SURFACE_G1, SURFACE_G2 },
                        dp(c, 1f), LINE, 0x00000000, dp(c, 3f))
                : featureFill(c, grad));
        // [液态玻璃 2026-10-07] 半透明卡面不挂框架 elevation（阴影透上来 → 卡面发灰 + 黑边）
        card.setElevation(0f);
        card.setPadding(dp(c, M_CARD_PAD), dp(c, 13), dp(c, 12), dp(c, 13));
        card.setMinimumHeight(dp(c, 64));

        LinearLayout row = row(c);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(iconBox(c, iconRes, tileFill, tint, 36f, 11f, I_BODY));

        LinearLayout texts = col(c);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(c, 12);
        texts.setLayoutParams(tlp);
        TextView t = text(c, title, S_BODY, titleColor, false);
        t.setTypeface(medium());
        texts.addView(t);
        if (sub != null && !sub.isEmpty()) {
            TextView s = text(c, sub, S_FOOT, subColor, false);
            s.setPadding(0, dp(c, 3), 0, 0);
            texts.addView(s);
        }
        row.addView(texts);
        row.addView(iconBox(c, com.dsh.mobile.R.drawable.ic_chevron_right,
                0x00000000, chevColor, 18f, 0f, 16f));
        card.addView(row);
        return card;
    }

    /** 图标底：把强调色稀释成 12% 的淡底（浅色/深色两档都成立，不引入新色板项）。 */
    public static int tintSoft(int color) {
        return alpha(color, 0.12f);
    }

    /**
     * 环境背景：毛玻璃的「下层内容」。
     *
     * <p>为什么非有它不可（demo 原话）：<i>「没有下层内容，玻璃只是白板」</i>。
     * 旧版的页面底是均匀的 #E6E4F0，玻璃再透也只是透出同一块灰 —— 这一版把整页底换成
     * 彩色渐变 + 四枚柔边光斑，玻璃才真的有东西可透。
     *
     * <p>逐值抄自存档 demo 的 `.ambient` / `body[data-palette=...]` 两段：
     * 底是一条 178°（≈上→下）三段渐变；四枚光斑几何（-90/-70 左上 300、right -80/top 150 260、
     * left -70/bottom -50 320、right -60/bottom 130 240）与 demo 完全一致，
     * 只是把 CSS 的 `filter: blur(42px)` 换成**径向渐变柔边**——原生没有 backdrop-filter，
     * 但这样一来"玻璃后面透出的本来就糊"，观感与真模糊一致。
     *
     * <p>深色档按 demo 的口径整体压暗：`brightness(.42) saturate(1.25)`（素雅档 .30 / 1.1）。
     */
    /**
     * [2026-10-07 整合] 兼容 shim —— 朋友 fork 的 API 名。
     *
     * <p>朋友那边是 {@code Ui.setAmbientBackground(View)} + 内部类 {@code AmbientBg}；
     * 我们这边功能等价物是 {@link AmbientDrawable}（同一套彩色环境底，且额外支持
     * 我们的 {@link #setAmbientPalette(int)} 索引配色档）。所以这里**不搬他的实现**，
     * 只补一个同名入口：朋友那边写法的调用点（DrawerHost / ArtifactActivity 等）
     * 可以零改动直接用，两套代码合流时不至于因为"没有这个方法"而编译不过。
     */
    public static void setAmbientBackground(android.view.View v) {
        if (v == null) return;
        v.setBackground(new AmbientDrawable());
    }

    public static final class AmbientDrawable extends android.graphics.drawable.Drawable {

        private final android.graphics.Paint basePaint =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint[] blobPaints = new android.graphics.Paint[4];
        private final float[] bx = new float[4], by = new float[4], br = new float[4];
        /** 极光档：把 aurora_bg 中心裁切铺满（不拉伸），保持比例。 */
        private final android.graphics.Paint imgPaint =
                new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        private final android.graphics.RectF imgDst = new android.graphics.RectF();
        /** 深色档的压暗滤镜（图/渐变共用）。 */
        private android.graphics.ColorFilter darkFilter;
        private int cacheW = -1, cacheH = -1, cachePal = -1;
        private boolean cacheDark;

        public AmbientDrawable() {
            for (int i = 0; i < 4; i++) {
                blobPaints[i] = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            }
        }

        private static float dp(float v) {
            return v * android.content.res.Resources.getSystem().getDisplayMetrics().density;
        }

        private void ensure(int w, int h) {
            if (cacheW == w && cacheH == h && cachePal == ambientPalette && cacheDark == dark) return;
            cacheW = w;
            cacheH = h;
            cachePal = ambientPalette;
            cacheDark = dark;
            Palette pal = palette();
            basePaint.setShader(new android.graphics.LinearGradient(0f, 0f, 0f, Math.max(1, h),
                    pal.bg, new float[] { 0f, 0.46f, 1f },
                    android.graphics.Shader.TileMode.CLAMP));

            android.graphics.ColorFilter filter = null;
            if (dark) {
                android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
                cm.setSaturation(pal.mono ? 1.1f : 1.25f);
                float s = pal.mono ? 0.30f : 0.42f;
                android.graphics.ColorMatrix bm = new android.graphics.ColorMatrix();
                bm.setScale(s, s, s, 1f);   // 亮度：ColorMatrix 没有 postScale，用 postConcat
                cm.postConcat(bm);
                filter = new android.graphics.ColorMatrixColorFilter(cm);
            }
            basePaint.setColorFilter(filter);
            darkFilter = filter;
            imgPaint.setColorFilter(filter);

            // 四枚光斑：demo 用「固定尺寸的圆 + blur(42px)」，这里换算成圆心/半径 + 径向柔边。
            float[] r = { dp(150f), dp(130f), dp(160f), dp(120f) };
            bx[0] = -dp(90f) + r[0];      by[0] = -dp(70f) + r[0];
            bx[1] = w + dp(80f) - r[1];   by[1] = dp(150f) + r[1];
            bx[2] = -dp(70f) + r[2];      by[2] = h + dp(50f) - r[2];
            bx[3] = w + dp(60f) - r[3];   by[3] = h - dp(130f) - r[3];
            int[] colors = { pal.a, pal.b, pal.c, pal.d };
            float[] op = { pal.oa, pal.ob, pal.oc, pal.od };
            for (int i = 0; i < 4; i++) {
                br[i] = r[i];
                float o = (pal.mono && dark) ? 0.28f : op[i];
                int solid = (colors[i] & 0x00FFFFFF) | (Math.round(255f * o) << 24);
                int clear = colors[i] & 0x00FFFFFF;
                blobPaints[i].setShader(new android.graphics.RadialGradient(
                        bx[i], by[i], Math.max(1f, r[i]),
                        new int[] { solid, solid, clear },
                        new float[] { 0f, 0.72f, 1f },
                        android.graphics.Shader.TileMode.CLAMP));
                blobPaints[i].setColorFilter(filter);
            }
        }

        @Override
        public void draw(android.graphics.Canvas cv) {
            android.graphics.Rect b = getBounds();
            int w = b.width(), h = b.height();
            if (w <= 0 || h <= 0) return;
            ensure(w, h);
            cv.save();
            cv.translate(b.left, b.top);
            // [2026-10-07 用户明确指出：「和背景完全没有关系，就是玻璃来回瞎搞」→ 背景不再动]
            // 保持"有图就用图"（极光图当底）。
            android.graphics.Bitmap img = ambientImage;
            if (img != null && !img.isRecycled()) {
                // 极光档：中心裁切铺满（保持比例，不拉伸）
                float sw = img.getWidth(), sh = img.getHeight();
                if (sw > 0 && sh > 0) {
                    float scale = Math.max(w / sw, h / sh);
                    float dw = sw * scale, dh = sh * scale;
                    imgDst.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f);
                    imgPaint.setColorFilter(dark ? darkFilter : null);
                    cv.drawBitmap(img, null, imgDst, imgPaint);
                }
            } else {
                cv.drawRect(0f, 0f, w, h, basePaint);
                for (int i = 0; i < 4; i++) {
                    cv.drawCircle(bx[i], by[i], br[i], blobPaints[i]);
                }
            }
            cv.restore();
        }

        @Override public void setAlpha(int a) { basePaint.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { /* 环境背景自管滤镜 */ }
        @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
    }

    /**
     * 真·背景模糊（自己实现，不依赖不存在的 API）。
     *
     * <p><b>为什么要有它</b>：Android 确实没有 CSS 的 {@code backdrop-filter}，也没有
     * "模糊兄弟 View" 的现成 API —— 但**不代表做不到**。可以自己把面板背后那块内容
     * 渲染到离屏缓冲、模糊它、再当面板底图铺回去。这才是 demo 毛玻璃观感的真正来源：
     * 面板后面是**真的糊掉的内容**，而不是一块半透明白（后者一眼假）。
     *
     * <p>[2026-10-07 定版] 抓取**统一走 {@link com.dsh.mobile.proto.Frost}** —— 隔离 Demo
     * 里已验证可用的那一套（降采样 + 5-tap 近高斯 + CPU 版 lens 折射 + 四周 padding）。
     * 本类原先自写的两条路都已删除：
     * <ul>
     *   <li>RenderNode + RenderEffect：在本工程**必崩**（模拟器实测 RenderThread SIGSEGV，
     *       见 2026-10-07 诊断记录），不能开；</li>
     *   <li>自写"缩略位图 + 盒式模糊"：实测**抓不到内容**（直接抓 ListView 全透明）。</li>
     * </ul>
     *
     * <p>坐标系：{@code source} 与 {@code panel} 可以在任意层级（用
     * {@code getLocationInWindow} 求偏移），所以"输入胶囊在 bottomStack 里、列表在 stage 里"
     * 这种不同父容器的组合也成立。
     *
     * <p><b>已知限制</b>（与 BlurView 相同）：离屏渲染拿不到 {@code SurfaceView} /
     * {@code GLSurfaceView} / 部分视频层的内容 —— 它们不走普通 View 绘制流程。
     */
    public static final class BackdropBlurDrawable extends android.graphics.drawable.Drawable {
        /** 抓取间隔：60ms ≈ 16fps，滚动时糊的内容跟着走，但不会每帧重算。 */
        private static final long REFRESH_MS = 60L;
        /** 降采样倍数（与 Demo 的 FrostedPanelView 同档）：1/4。 */
        private static final int DOWNSCALE = 4;

        private final View source;
        private final View panel;
        private final float radiusDp;
        private final int tint;
        private final float cornerDp;
        private final android.graphics.Path clipPath = new android.graphics.Path();
        private final android.graphics.RectF clipRf = new android.graphics.RectF();
        private final android.graphics.Paint paint =
                new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        private final android.graphics.Paint tintPaint = new android.graphics.Paint();
        private final int[] panLoc = new int[2];
        private long lastCapture;
        /**
         * 全局递归保护（**静态**，不是实例级）：一次抓取会重画整页，而页面上可能挂着**多个**
         * 玻璃件；实例级标记只挡得住自己，挡不住"卡 A 抓取时画到卡 B、卡 B 又开始抓取"的嵌套。
         * 与 BlurView 用专用 {@code BlurViewCanvas} 子类做递归守卫是同一个目的。
         */
        private static boolean capturingGlobal;

        public BackdropBlurDrawable(View source, View panel, float radiusDp, int tintColor) {
            this(source, panel, radiusDp, tintColor, 0f);
        }

        /**
         * @param cornerDp 底图圆角（0 = 直角）。必须与盖在它上面的玻璃层圆角一致，
         *                 否则模糊底图会在圆角外露方角。
         */
        public BackdropBlurDrawable(View source, View panel, float radiusDp, int tintColor,
                                    float cornerDp) {
            this(source, panel, radiusDp, tintColor, cornerDp, LENS_AMT_PX);
        }

        /**
         * @param lensAmtPx 折射幅度（px）。**按调用处传**：大面板（抽屉）传 0 —— 见
         *                  {@link #LENS_AMT_PX} 的注释（V3.22 实测 drawer 右缘内侧 11dp 那条色阶缝）。
         */
        public BackdropBlurDrawable(View source, View panel, float radiusDp, int tintColor,
                                    float cornerDp, float lensAmtPx) {
            this.source = source;
            this.panel = panel;
            this.radiusDp = radiusDp;
            this.tint = tintColor;
            this.cornerDp = cornerDp;
            this.lensAmtPx = lensAmtPx;
        }

        /**
         * 绘制期解析模糊源。
         *
         * <p>{@code source} 为 null 时（见 {@link Ui#blurredGlassRoot}）解析成
         * **panel 所在的应用根容器**：从 panel 一路向上，停在"父 View 的父不是 View"的那一层
         * —— 也就是 DecorView 的子 View（应用根容器）。**不能直接抓 DecorView**：它是窗口层，
         * 抓取恒为 0（{@code ConversationView.blurSource()} 的注释里记着这个结论）。
         */
        private View resolveSource() {
            if (source != null && source.isAttachedToWindow()) return source;
            View v = panel;
            while (v != null && v.getParent() instanceof View) {
                View pv = (View) v.getParent();
                if (!(pv.getParent() instanceof View)) return v;
                v = pv;
            }
            return v;
        }

        /**
         * [2026-10-07 换实现] 捕获交给 {@link com.dsh.mobile.proto.Frost}（隔离 Demo 里
         * **已验证可用**的那一套：降采样 + 5-tap 近高斯 + CPU 版 lens 折射 + 四周 padding）。
         *
         * <p>为什么换：本类原来的"缩略位图 + 盒式模糊"实测在本工程**抓不到任何子 View 内容**
         * —— 抓整页只有根容器自己的背景（不透明壁纸），抓 ListView 干脆全透明
         * （逐像素实测 avgAlpha=0），而 Demo 的同一原理（Frost）能抓到卡片与文字。
         * 统一到 Frost 后，两处的磨砂行为一致，也不再各自维护两份折射代码。
         */
        private void capture(int w, int h, View src) {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastCapture < REFRESH_MS) return;
            lastCapture = now;
            try {
                panel.getLocationInWindow(panLoc);
                float dens = panel.getResources().getDisplayMetrics().density;
                int refrH = Math.round(LENS_H_DP * dens);
                int refrAmt = Math.round(lensAmtPx);
                frostPad = com.dsh.mobile.proto.Frost.padFor(
                        com.dsh.mobile.proto.Frost.BLUR_RADIUS, refrH);
                // 抓取期间把面板自身置为全透明：源可能包含本面板（整页根容器），
                // 不透明化的话会把面板自己的 tint 烘进模糊层（自我反馈、越叠越白）。
                float oldAlpha = panel.getAlpha();
                panel.setAlpha(0f);
                Bitmap b;
                try {
                    b = com.dsh.mobile.proto.Frost.capture(src, panLoc[0], panLoc[1], w, h,
                            glassDownscale(), cornerDp * dens, refrH, refrAmt);
                } finally {
                    panel.setAlpha(oldAlpha);
                }
                if (b == null || b.isRecycled()) return;
                Bitmap old = frostBmp;
                frostBmp = b;
                if (old != null && old != b && !old.isRecycled()) old.recycle();
            } catch (Throwable ignored) {
                // 抓取失败就画不到模糊（下面仍会铺 tint），绝不因此崩
            }
        }

        /** Frost 抓回来的图（含四周 padding 溢出区）。 */
        private Bitmap frostBmp;
        private int frostPad;
        /** 本实例的折射幅度（px）。默认 {@link #LENS_AMT_PX}；大面板可由调用处传 0。 */
        private float lensAmtPx = LENS_AMT_PX;

        @Override
        public void draw(android.graphics.Canvas cv) {
            android.graphics.Rect b = getBounds();
            int w = b.width(), h = b.height();
            if (w <= 0 || h <= 0) return;
            View src = panel == null ? null : resolveSource();
            if (src != null && !capturingGlobal) {
                capturingGlobal = true;
                capture(w, h, src);              // Frost：降采样 + 近高斯 + CPU lens
                Bitmap fb = frostBmp;
                if (fb != null && !fb.isRecycled()) {
                    // 位图含四周 padding 溢出区：玻璃区域在 (pad,pad) 处（同 FrostedPanelView）
                    float ds = glassDownscale();
                    // [2026-10-07 修「屏幕左缘一条怪竖带」] **必须把模糊层裁进面板自己的矩形**：
                    // padding 溢出区（74px）是给"边缘采样到真实内容"用的，但它同时会画到面板**外面**。
                    // 抽屉关着时整体在屏外（translationX=-886），它的溢出区就落在屏幕 x0..74 —— 那条
                    // 20% 暗的竖带就是这么来的（实测：把抽屉底置空 → 竖带消失）。
                    // 普通面板四边都被父容器裁掉所以看不出来，抽屉这种"父容器不裁"的就露了。
                    int save = cv.save();
                    cv.clipRect(0, 0, w, h);
                    if (cornerDp > 0f) {
                        clipForCorner(w, h);
                        cv.clipPath(clipPath);
                    }
                    cv.scale(ds, ds);
                    cv.drawBitmap(fb, -frostPad / ds, -frostPad / ds, paint);
                    cv.restoreToCount(save);
                }
                capturingGlobal = false;
            }
            if (android.graphics.Color.alpha(tint) > 0) {
                tintPaint.setColor(tint);
                cv.drawRect(0, 0, w, h, tintPaint);
            }
        }

        /** [规范定版] 折射环带高 28dp、折射幅度 38px（交给 Frost 的 lens 用）。 */
        private static final float LENS_H_DP = 28f;
        /**
         * 默认折射幅度（px）。**顶栏 / 输入条 / 底部条**用这个值（它们的父容器会裁掉四边，
         * 折射是"液态"的主要线索，不能丢）。
         *
         * <p>[V3.22 抽屉那条竖条的根因] 抽屉底是**整屏高的大面板、且父容器不裁边**：
         * CPU lens 的位移被 `pad*0.7` 夹取，在近均匀底色上会在**面板内侧 ≈11dp 处留下一条竖向色阶缝**
         * （实测列均值 213 → 205 → 216，缝中心距右缘 28px=10.7dp；把本值置 0 则该缝完全消失）。
         * 所以抽屉走**按调用处传参**：{@code blurredGlassRoot(..., 0f)}，不动这里的全局默认。
         */
        /**
         * [V3.27 · 用户「玻璃磨砂程度改成『液态玻璃 / 磨砂玻璃』两种，把现在液态玻璃的直接换成磨砂玻璃，
         * 主要减小硬件资源损耗」] **改为可切换的默认值**：由 {@link Ui#setGlassMode(String)} 置 0 或 38。
         *
         * <p>只动这一个"折射幅度默认值"（SOP 明确把 `lensAmt` 列为允许调整的质感参数），
         * **不动**捕获 / 降采样 / 模糊 / 着色顺序 ⇒ 本体逻辑不变。
         */
        private static float LENS_AMT_PX = 38f;

        /** 圆角裁剪路径（模糊底图必须与盖在它上面的玻璃层同圆角）。 */
        private void clipForCorner(float w, float h) {
            float r = cornerDp * panel.getResources().getDisplayMetrics().density;
            clipRf.set(0f, 0f, w, h);
            clipPath.reset();
            clipPath.addRoundRect(clipRf, r, r, android.graphics.Path.Direction.CW);
        }

        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { paint.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** 按钮悬浮投影的内缩量（dp）：见 {@link #shadowed}。 */
    public static final float BTN_SHADOW_PAD = 6f;
    /** 卡片 / 线路行的悬浮投影内缩量（dp）—— 越大影子铺得越开、越柔。 */
    public static final float CARD_SHADOW_PAD = 18f;

    /**
     * 悬浮投影：把一个 drawable 套上"浮起来"的柔和阴影。
     *
     * <p><b>为什么不能用 {@code View.setElevation}</b>：框架阴影画在 View **底下**，
     * 而玻璃件是**半透明**的 —— 阴影会透上来把卡面压灰（2026-10-06 逐像素反推：
     * 卡面被额外叠了约 24% 的黑，用户原话「都是灰底，完全没有毛玻璃的效果」）。
     * 所以阴影必须画在**形状之外**。
     *
     * <p>做法：形状在 View 里**内缩 {@code padDp}**，那一圈留给阴影 —— 由外向内画
     * {@link ShadowedDrawable#STEPS} 圈圆角矩形，越往外越淡、整体下移 {@code dyDp}
     * （对应 demo 的 `0 18px 44px rgba(26,16,66,.22)` + `0 3px 10px rgba(26,16,66,.12)`）。
     * 调用方要把 {@code padDp} 补进 padding / 用负 margin 抵消，视觉尺寸才不变。
     */
    public static android.graphics.drawable.Drawable shadowed(
            android.graphics.drawable.Drawable inner, float radiusDp, float padDp, float dyDp) {
        return new ShadowedDrawable(inner, radiusDp, padDp, dyDp, padDp * 0.85f);
    }

    /** 可分离盒式模糊：横一遍、竖一遍（对缩略图足够，且是 O(n)）。 */
    private static void boxBlur(int[] p, int w, int h, int r) {
        int[] tmp = new int[p.length];
        for (int y = 0; y < h; y++) {                     // 横向
            int base = y * w;
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int x = Math.min(w - 1, Math.max(0, i));
                int c = p[base + x];
                sa += (c >>> 24); sr += (c >> 16) & 0xFF; sg += (c >> 8) & 0xFF; sb += c & 0xFF;
            }
            int n = 2 * r + 1;
            for (int x = 0; x < w; x++) {
                tmp[base + x] = ((sa / n) << 24) | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
                int out = Math.min(w - 1, Math.max(0, x - r));
                int in = Math.min(w - 1, Math.max(0, x + r + 1));
                int co = p[base + out], ci = p[base + in];
                sa += (ci >>> 24) - (co >>> 24);
                sr += ((ci >> 16) & 0xFF) - ((co >> 16) & 0xFF);
                sg += ((ci >> 8) & 0xFF) - ((co >> 8) & 0xFF);
                sb += (ci & 0xFF) - (co & 0xFF);
            }
        }
        for (int x = 0; x < w; x++) {                     // 纵向
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int y = Math.min(h - 1, Math.max(0, i));
                int c = tmp[y * w + x];
                sa += (c >>> 24); sr += (c >> 16) & 0xFF; sg += (c >> 8) & 0xFF; sb += c & 0xFF;
            }
            int n = 2 * r + 1;
            for (int y = 0; y < h; y++) {
                p[y * w + x] = ((sa / n) << 24) | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
                int out = Math.min(h - 1, Math.max(0, y - r));
                int in = Math.min(h - 1, Math.max(0, y + r + 1));
                int co = tmp[out * w + x], ci = tmp[in * w + x];
                sa += (ci >>> 24) - (co >>> 24);
                sr += ((ci >> 16) & 0xFF) - ((co >> 16) & 0xFF);
                sg += ((ci >> 8) & 0xFF) - ((co >> 8) & 0xFF);
                sb += (ci & 0xFF) - (co & 0xFF);
            }
        }
    }

    /**
     * 给任意圆角底加一条**上沿亮棱**。
     *
     * <p>demo 里**每一个**玻璃件都带 `box-shadow: inset 0 1px 0 var(--rim)` ——
     * 线路行、图标砖、次按钮、会话行、工具行、气泡、输入胶囊……一个不落。
     * 这条 1px 亮棱就是"玻璃有厚度"的来源（第二篇也把它列为四要素里的**边缘高光**）。
     *
     * <p>做法：把画布裁到**顶部一条**，再沿圆角矩形描一圈 —— 于是只有上沿那一小段亮，
     * 且天然跟着圆角走（不是一条直角亮线）。
     */
    public static android.graphics.drawable.Drawable rimmed(
            android.graphics.drawable.Drawable base, float radiusDp) {
        return new RimmedDrawable(base, radiusDp, GLASS_RIM);
    }

    /** 同上，但可指定内棱颜色（demo 里主按钮的内棱是 35% 白，比幽灵按钮弱）。 */
    public static android.graphics.drawable.Drawable rimmed(
            android.graphics.drawable.Drawable base, float radiusDp, int rimColor) {
        return new RimmedDrawable(base, radiusDp, rimColor);
    }

    public static final class RimmedDrawable extends android.graphics.drawable.Drawable {
        private final android.graphics.drawable.Drawable base;
        private final float radiusDp;
        private final int rimColor;
        private final android.graphics.Paint p =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rf = new android.graphics.RectF();

        RimmedDrawable(android.graphics.drawable.Drawable base, float radiusDp, int rimColor) {
            this.base = base;
            this.radiusDp = radiusDp;
            this.rimColor = rimColor;
        }

        @Override public void draw(android.graphics.Canvas cv) {
            android.graphics.Rect b = getBounds();
            int w = b.width(), h = b.height();
            if (w <= 0 || h <= 0 || base == null) return;
            base.setBounds(b);
            base.draw(cv);
            int rim = android.graphics.Color.alpha(rimColor);
            if (rim <= 0) return;
            float d = android.content.res.Resources.getSystem().getDisplayMetrics().density;
            float rimH = Math.max(1f, d * 1.6f);          // ≈1.6dp 的亮棱（1.2dp 偏细，提体积感）
            float inset = rimH / 2f;
            float r = radiusDp * d;
            // **必须用 bounds 的原点**：本 drawable 常被套在 InsetDrawable / ShadowedDrawable
            // 里面，那时 bounds 的 left/top 不是 0 —— 写死 (0,0) 会把亮棱画到整个 View 的顶部
            // （表现就是一条白线飘在元素上方，2026-10-06 实测踩到）。
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(rimH);
            rf.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
            // 上沿亮棱
            cv.save();
            cv.clipRect(b.left, b.top, b.right, b.top + rimH);
            p.setColor(rimColor);
            cv.drawRoundRect(rf, r, r, p);
            cv.restore();
            // 下沿暗棱：与卡片同一套"上亮下暗"配方 —— 只有上亮会显得薄，补下暗才有体积感
            if (android.graphics.Color.alpha(GLASS_LO) > 0) {
                cv.save();
                cv.clipRect(b.left, b.bottom - rimH, b.right, b.bottom);
                p.setColor(GLASS_LO);
                cv.drawRoundRect(rf, r, r, p);
                cv.restore();
            }
        }

        @Override public void setAlpha(int a) { base.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { base.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    public static final class ShadowedDrawable extends android.graphics.drawable.Drawable {
        /** 缩略图比例：阴影是低频信息，1/4 采样足够，模糊成本也降 16 倍。 */
        private static final int DOWNSCALE = 4;
        private final android.graphics.drawable.Drawable inner;
        private final float radiusDp, padDp, dyDp, blurDp;
        private final android.graphics.Paint bmpPaint =
                new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        private final android.graphics.Paint fillPaint =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint clearPaint =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rf = new android.graphics.RectF();
        private Bitmap bmp;
        private int[] px;
        private int bw = -1, bh = -1;
        private boolean built;

        ShadowedDrawable(android.graphics.drawable.Drawable inner,
                         float radiusDp, float padDp, float dyDp, float blurDp) {
            this.inner = inner;
            this.radiusDp = radiusDp;
            this.padDp = padDp;
            this.dyDp = dyDp;
            this.blurDp = blurDp;
        }

        private static float density() {
            return android.content.res.Resources.getSystem().getDisplayMetrics().density;
        }

        @Override public void draw(android.graphics.Canvas cv) {
            android.graphics.Rect b = getBounds();
            int w = b.width(), h = b.height();
            if (w <= 0 || h <= 0 || inner == null) return;
            float d = density();
            float pad = padDp * d, dy = dyDp * d, r = radiusDp * d, blur = blurDp * d;
            int lw = Math.max(1, w / DOWNSCALE), lh = Math.max(1, h / DOWNSCALE);
            if (bmp == null || bw != lw || bh != lh) {
                if (bmp != null) bmp.recycle();
                bmp = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888);
                px = new int[lw * lh];
                bw = lw;
                bh = lh;
                built = false;
            }
            if (!built) {
                // 阴影形状只跟尺寸有关 → 只算一次（每帧重算没必要）
                built = true;
                bmp.eraseColor(0);
                android.graphics.Canvas c = new android.graphics.Canvas(bmp);
                float s = 1f / DOWNSCALE;
                // ① 阴影本体：内缩 pad、整体下移 dy
                fillPaint.setColor(GLASS_SHADOW);
                fillPaint.setXfermode(null);
                rf.set(pad * s, pad * s + dy * s, (w - pad) * s, (h - pad) * s + dy * s);
                c.drawRoundRect(rf, r * s, r * s, fillPaint);
                // ② 模糊
                bmp.getPixels(px, 0, bw, 0, 0, bw, bh);
                boxBlur(px, bw, bh, Math.max(1, Math.round(blur * s)));
                bmp.setPixels(px, 0, bw, 0, 0, bw, bh);
                // ③ **把形状本体那一块从阴影里抠掉**（PorterDuff.CLEAR）。
                //    不抠的话，阴影会从**半透明**的形状下面透上来，把整块面压暗 ——
                //    2026-10-06 逐像素实测：卡面因此比理论值暗约 5~7%、且明显偏灰
                //    （用户原话「这些色块都不对了」）。真实投影本来就被物体挡住，抠掉才对。
                clearPaint.setColor(0);
                clearPaint.setXfermode(new android.graphics.PorterDuffXfermode(
                        android.graphics.PorterDuff.Mode.CLEAR));
                // ③a 形状**顶边以上一律不留阴影**：模糊半径比下移量大时，阴影会被糊到
                //     形状上方（表现就是"元素顶上飘着一圈灰"）。CSS 的 box-shadow 也有这个
                //     现象，但真实投影不该长在物体上面 —— 直接裁掉。
                c.drawRect(0f, 0f, w * s, pad * s, clearPaint);
                rf.set(pad * s, pad * s, (w - pad) * s, (h - pad) * s);
                c.drawRoundRect(rf, r * s, r * s, clearPaint);
                clearPaint.setXfermode(null);
            }
            cv.drawBitmap(bmp, null, new android.graphics.RectF(0, 0, w, h), bmpPaint);
            inner.setBounds((int) pad, (int) pad, (int) (w - pad), (int) (h - pad));
            inner.draw(cv);
        }

        @Override public void setAlpha(int a) { inner.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { inner.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /**
     * 卡片底：圆角填充 + 1px 发丝描边 + 可选左侧 3dp 强调条。
     *
     * <p>为什么要自绘而不是 {@code LayerDrawable}：强调条要"被卡片圆角裁掉"才不露方角，
     * 而 LayerDrawable 的层内缩是**创建时**按像素定死的 —— 手搓 View 树里卡片宽度要等
     * 测量后才知道，创建时根本拿不到。自绘可以在 draw() 里用当帧的真实 bounds 裁剪。
     */
    public static final class CardBg extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint p =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path shape = new android.graphics.Path();
        private final android.graphics.RectF rf = new android.graphics.RectF();
        private final float radius, stroke, barW;
        private final int fill, line, accent;
        /** 非空 = 用「左上 → 右下」奶白渐变当卡体（Sadees），fill 只作为兜底色。 */
        private final int[] grad;
        /** true = 只圆上两角（底部弹窗那种"贴着屏幕下缘"的玻璃）。 */
        private boolean topOnly;
        /** 顶部高光带的像素高（1.5dp），由构造时的 density 决定。 */
        private final float hiH;

        public CardBg(float radius, int fill, float stroke, int line, int accent, float barW) {
            this.radius = radius;
            this.fill = fill;
            this.grad = null;
            this.stroke = Math.max(1f, stroke);
            this.line = line;
            this.accent = accent;
            this.barW = barW;
            this.hiH = Math.max(1f, stroke * 1.5f);
        }

        /**
         * 渐变卡体（Sadees 奶白卡）：{@code gradColors} 左上 → 右下对角渐变。
         * 为什么不用 GLASS 半透明体：参考图的层级是"奶白卡浮在薰衣草底上"，靠色差分层；
         * 半透明玻璃在纯色底上只会透出同一块底色，分不出层。
         */
        public CardBg(float radius, int[] gradColors, float stroke, int line, int accent, float barW) {
            this.radius = radius;
            this.fill = gradColors[0];
            this.grad = gradColors;
            this.stroke = Math.max(1f, stroke);
            this.line = line;
            this.accent = accent;
            this.barW = barW;
            this.hiH = Math.max(1f, stroke * 1.5f);
        }

        /** 只圆上两角（底部弹窗 / sheet 用）。返回 this 便于链式写。 */
        public CardBg topOnly(boolean v) { this.topOnly = v; return this; }

        // [灰化根治·2026-10-05] 框架渐变卡体（按尺寸缓存重建）。
        //
        // 背景：自绘 `drawPath` + `LinearGradient` 在**超高卡片**上会被整片压暗成灰色 ——
        // 真机逐像素证据（荣耀 PGT-AN10）：设计色 #FBFAFE = (251,250,254)，
        // 实测卡片内部 (185,183,193)（-26%），且只在最外侧露 ~8px 正常色带；
        // 同一坐标改用框架 GradientDrawable 后回到 (251,250,254) ✓。
        // 上一轮只把提问卡/审批卡换成 Ui.cardGrad()，其它 CardBg 使用者（子智能体卡、
        // 文件卡、工具卡、sheet、chip 胶囊…）仍会灰化 —— 这里**从组件层根治**，
        // 所有调用点自动受益，无需逐个改。
        private android.graphics.drawable.GradientDrawable gd;
        private float gdW = -1f, gdH = -1f;

        // ---- 毛玻璃 v3：斜射高光（135°）+ 内发光
        //
        // 这两层是 demo 里"玻璃 vs 磨砂塑料"的第一区别：
        //   · 斜射高光 —— 从左上方打一束 135° 白色渐变（42% → 12% @30% → 0 @58%）；
        //   · 内发光   —— 边缘一圈 26px 的淡白雾，让玻璃"厚"起来。
        // 都用自绘（`LinearGradient` / 逐级描边），不引入任何依赖。
        private final android.graphics.Paint sheenPaint =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private android.graphics.Shader sheenShader;
        private int sheenW = -1, sheenH = -1;

        /** 半透明卡（玻璃）用**白色**发丝线；不透明卡仍用调用方传的深色发丝线。 */
        private int lineColor() {
            return android.graphics.Color.alpha(fill) < 250 ? GLASS_HAIRLINE : line;
        }

        private boolean glassy() {
            return android.graphics.Color.alpha(fill) < 250;
        }

        private void ensureSheen(float w, float h) {
            if (sheenShader != null && sheenW == (int) w && sheenH == (int) h) return;
            sheenShader = new android.graphics.LinearGradient(0f, 0f, w, h,
                    new int[] { SHEEN_1, SHEEN_2, 0x00FFFFFF },
                    new float[] { 0f, 0.30f, 0.58f },
                    android.graphics.Shader.TileMode.CLAMP);
            sheenPaint.setShader(sheenShader);
            sheenW = (int) w;
            sheenH = (int) h;
        }

        /** 内发光：沿边缘画三圈逐级变淡的描边（近似 CSS `inset 0 0 26px`）。 */
        private void drawInnerGlow(android.graphics.Canvas cv, float w, float h) {
            int glow = android.graphics.Color.alpha(GLASS_INNER);
            if (glow <= 0) return;
            float band = Math.max(1f, stroke * 1.6f);
            float rad = Math.max(0f, radius - stroke);
            p.setShader(null);
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(band * 2f);
            // 三圈总 alpha 控制在 ≈0.7（上一版 0.85+0.42+0.18=1.45 偏白，把白刷到卡面中央）
            float[] f = { 0.45f, 0.18f, 0.06f };
            for (int i = 0; i < f.length; i++) {
                float in = Math.max(0f, stroke + i * band * 1.6f);
                if (in * 2f >= w || in * 2f >= h) break;
                int a = Math.round(glow * f[i]);
                p.setColor((GLASS_INNER & 0x00FFFFFF) | (a << 24));
                cv.drawRoundRect(in, in, w - in, h - in, rad, rad, p);
            }
            p.setStyle(android.graphics.Paint.Style.FILL);
        }

        // ---- 磨砂噪点（demo 的 `fractalNoise` + `soft-light`）
        //
        // demo 明确把噪点列为「玻璃 vs 磨砂塑料」的第二区别（v3 打磨清单 ②）：
        // 表面有颗粒感，不再像纯色贴片。原生没有 feTurbulence，用**两层伪随机平铺**近似：
        //   · 细层（每像素随机）—— 对应 baseFrequency .9 的高频；
        //   · 粗层（4px 块随机）—— 对应 numOctaves 4 叠出来的低频起伏。
        // 两张 Bitmap 进程内各生成一次、全局复用；绘制时以 GLASS_GRAIN 的 alpha 平铺。
        private static android.graphics.Bitmap grainFine;
        private static android.graphics.Bitmap grainCoarse;
        private static android.graphics.Shader grainShader;
        private final android.graphics.Paint grainPaint =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        private static void ensureGrain() {
            if (grainFine != null && grainCoarse != null && grainShader != null) return;
            java.util.Random rnd = new java.util.Random(20261006L);   // 固定种子：每台设备纹理一致
            // 细层：100x100 逐像素随机灰度
            grainFine = android.graphics.Bitmap.createBitmap(100, 100,
                    android.graphics.Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 100; y++) {
                for (int x = 0; x < 100; x++) {
                    int v = rnd.nextInt(256);
                    grainFine.setPixel(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
                }
            }
            // 粗层：100x100 的 4px 块（低频起伏）
            grainCoarse = android.graphics.Bitmap.createBitmap(100, 100,
                    android.graphics.Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 100; y++) {
                for (int x = 0; x < 100; x++) {
                    int v = 128 + (rnd.nextInt(2) == 0 ? -1 : 1) * rnd.nextInt(64);
                    grainCoarse.setPixel(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
                }
            }
            // 合成：把两层各画进一张 100x100，粗层半透明压在细层上 —— 有粗有细才像磨砂
            android.graphics.Bitmap merged = android.graphics.Bitmap.createBitmap(100, 100,
                    android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas mc = new android.graphics.Canvas(merged);
            android.graphics.Paint mp = new android.graphics.Paint();
            mc.drawBitmap(grainFine, 0f, 0f, mp);
            mp.setAlpha(110);   // 粗层占 ~43%
            mc.drawBitmap(grainCoarse, 0f, 0f, mp);
            grainShader = new android.graphics.BitmapShader(merged,
                    android.graphics.Shader.TileMode.REPEAT,
                    android.graphics.Shader.TileMode.REPEAT);
        }

        /** 磨砂噪点：以 {@link #GLASS_GRAIN} 的 alpha 平铺在卡面上。 */
        private void drawGrain(android.graphics.Canvas cv, float w, float h) {
            int a = android.graphics.Color.alpha(GLASS_GRAIN);
            if (a <= 0) return;
            ensureGrain();
            grainPaint.setShader(grainShader);
            grainPaint.setColor(GLASS_GRAIN);
            grainPaint.setAlpha(a);
            // [毛玻璃 v3] demo 的 `.grain` 用的是 `mix-blend-mode: overlay` —— 暗处压暗、
            // 亮处提亮，**平均值中性**。若用普通 srcOver 叠一张随机灰（均值 127），
            // 在浅色卡面上等于整体压暗几个色阶（卡面会"发灰"）。
            // API 29+ 直接用 PorterDuff.OVERLAY 复刻；低版本退回普通叠加（有 4% 上限，影响有限）。
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                grainPaint.setXfermode(new android.graphics.PorterDuffXfermode(
                        android.graphics.PorterDuff.Mode.OVERLAY));
            }
            cv.drawRect(0f, 0f, w, h, grainPaint);
            if (android.os.Build.VERSION.SDK_INT >= 29) grainPaint.setXfermode(null);
        }

        private void ensureGd(float w, float h) {
            if (gd != null && gdW == w && gdH == h) return;
            android.graphics.drawable.GradientDrawable g =
                    new android.graphics.drawable.GradientDrawable();
            if (topOnly) {
                g.setCornerRadii(new float[] { radius, radius, radius, radius, 0f, 0f, 0f, 0f });
            } else {
                g.setCornerRadius(radius);
            }
            if (grad != null && grad.length >= 2) {
                g.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TL_BR);
                g.setColors(grad);
            } else {
                g.setColor(fill);
            }
            g.setStroke(Math.max(1, (int) Math.ceil(stroke)), lineColor());
            gd = g;
            gdW = w;
            gdH = h;
        }

        @Override
        public void draw(android.graphics.Canvas cv) {
            android.graphics.Rect b = getBounds();
            float w = b.width(), h = b.height();
            float inset = stroke / 2f;
            rf.set(inset, inset, w - inset, h - inset);
            shape.reset();
            // 上两角圆 / 下两角直角（topOnly）——圆角顺序：左上、右上、右下、左下
            shape.addRoundRect(rf, topOnly
                    ? new float[] { radius, radius, radius, radius, 0f, 0f, 0f, 0f }
                    : new float[] { radius, radius, radius, radius, radius, radius, radius, radius },
                    android.graphics.Path.Direction.CW);

            // ① 卡体 + 发丝描边：**交给框架 GradientDrawable**（[灰化根治·2026-10-05]）
            //    自绘 drawPath+shader 在超高卡片上会整片压暗成灰（见 ensureGd 注释）。
            //    shape 仍保留，供 ②④ 的边缘叠层裁剪使用。
            ensureGd(w, h);
            gd.setBounds(b.left + (int) inset, b.top + (int) inset,
                    b.right - (int) inset, b.bottom - (int) inset);
            gd.draw(cv);

            // ①.5 毛玻璃 v3：斜射高光（135°）+ 内发光 + 磨砂噪点 —— 只有半透明体才叠。
            if (glassy()) {
                cv.save();
                cv.clipPath(shape);
                ensureSheen(w, h);
                cv.drawRect(0f, 0f, w, h, sheenPaint);
                // 磨砂噪点（demo 的 fractalNoise）：让玻璃表面"有颗粒"，不是塑料贴片
                drawGrain(cv, w, h);
                cv.restore();
                drawInnerGlow(cv, w, h);
            }

            // ② 左侧 3dp 强调条（被卡片圆角裁掉才不露方角）
            if (android.graphics.Color.alpha(accent) != 0 && barW > 0f) {
                cv.save();
                cv.clipPath(shape);
                p.setColor(accent);
                cv.drawRect(0f, 0f, barW, h, p);
                cv.restore();
            }

            // ③ 发丝描边：已由 ensureGd 里的 setStroke 负责（原来在这里自绘一遍）。

            // ④ 顶部高光边 + 底部微暗（液态玻璃的关键：玻璃的"反光棱"）。
            //
            // **真机教训（2026-10-03，PGT-AN10）**：这里原来用 LinearGradient 描一条
            // 纵向渐变棱 + 一条横向渐变高光带，逻辑上没问题，但真机逐像素采样发现
            // **两者一个像素都没画出来**（卡片最上面一行 = 玻璃体自身亮度 251，没有任何
            // 更亮的一行）。所以改成**纯色叠层**：把高光做成 4 条逐级变淡的实心细带
            // （1.0 / 0.5 / 0.25 / 0.10 的 GLASS_RIM）—— 不用 shader，肉眼就是一条柔和
            // 的顶光，且与已经验证能画出来的发丝线走同一条绘制路径。
            //
            // **深色光影错位修复（2026-10-03 终局返工，用户点名「黑色界面光影边框偏移」）**：
            // 大圆角 + 高 density 下，直边高光带从 y=0 一直铺到左右两端，会盖住上缘描边、
            // 又在圆角处被 clip 斜切——直边段"亮带压着描边"、圆角段"只剩描边"，两段错位。
            // 修法（沿顶部圆角内切）：高光带从**描边内侧**（y ≥ stroke）开始，水平方向
            // 两端各收进 radius*0.6 避开圆角区；圆角的棱光由 ③ 的发丝描边补（描边沿
            // shape 走，天然跟着圆角）。底棱同理。
            // ④ 棱光：**沿圆角描边画**，用一条贯穿全高的 5 段纵向渐变 ——
            //    上沿最亮（高光）→ 12% 处减半 → 45% 起全透明 → 80% 仍透明 → 下沿一条暗棱。
            //
            // 为什么是"上亮 + 下暗"：第二篇（CSDN 文库《Android 里怎么做出那种通透带高光的
            // 毛玻璃效果》）把**边缘高光**列为玻璃四要素之一，并点明"1px 亮色边框是升级成
            // 高级玻璃感的关键"。只提亮上沿仍显平；补一条下沿暗棱，玻璃才有"厚度"、才立体。
            // 描边沿圆角矩形走，所以高光天然跟着圆角（旧版 4 条矩形在圆角处会断成直角）。
            int rim = GLASS_RIM;
            if (android.graphics.Color.alpha(rim) != 0) {
                cv.save();
                cv.clipPath(shape);
                float in = stroke;
                float rad = Math.max(0f, radius - in);
                float top = in, bottom = Math.max(in + 1f, h - in);
                p.setStyle(android.graphics.Paint.Style.STROKE);
                // 描边加粗到 2×：高光要"看得见"才谈得上立体感（原来 1.4× 偏弱）
                p.setStrokeWidth(Math.max(1f, stroke * 2f));
                p.setShader(new android.graphics.LinearGradient(0f, top, 0f, bottom,
                        new int[] { rim, alpha(rim, 0.45f), 0x00FFFFFF, 0x00FFFFFF, GLASS_LO },
                        new float[] { 0f, 0.12f, 0.45f, 0.80f, 1f },
                        android.graphics.Shader.TileMode.CLAMP));
                cv.drawRoundRect(in, in, w - in, h - in, rad, rad, p);
                p.setShader(null);
                p.setStyle(android.graphics.Paint.Style.FILL);
                cv.restore();
            }
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }

        /** 外轮廓 = 那张圆角矩形，系统 elevation 的阴影就贴着它画。 */
        @Override public void getOutline(android.graphics.Outline o) {
            android.graphics.Rect b = getBounds();
            float inset = stroke / 2f;
            o.setRoundRect(Math.round(inset), Math.round(inset),
                    Math.round(b.width() - inset), Math.round(b.height() - inset), radius);
        }
    }

    /** 卡片里的一行：最小 52dp 高、左右 16dp 内边距（iOS 的列表行规格）。 */
    public static LinearLayout cardRow(Context c) {
        LinearLayout r = row(c);
        r.setMinimumHeight(dp(c, H_ROW));
        r.setPadding(dp(c, M_CARD_PAD), dp(c, 8), dp(c, M_CARD_PAD), dp(c, 8));
        r.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return r;
    }

    /**
     * iOS 的 "›" 细箭头（列表行右侧的"可以进去"暗示）。
     *
     * <p>2026-10-02：不再是 "›" 这个字符。字符箭头的粗细/角度由字体决定，不同机器上
     * 还不一样，放大就是"字符拼的"。现在换成手写矢量
     * {@code res/drawable/ic_chevron_right.xml}（1.75dp 线宽、圆角端点）。
     */
    public static TextView chevron(Context c) {
        return iconBox(c, com.dsh.mobile.R.drawable.ic_chevron_right, 0x00000000, INK_FAINT,
                18f, 0f, 16f);
    }

    // ------------------------------------------------------------ 图标 / 按钮的尺寸比例（2026-10-03 调校）

    // **比例规则**：图标的视觉尺寸 ≈ 相邻正文行高的 0.9~1.1 倍。
    //
    // 字号是基准，图标必须跟着字号走 —— 上一轮把字号整体调小 10~15% 之后图标没跟着缩，
    // 相对就"长大"了，这正是用户说的"图标也不要太大，整体适配文字就行"。
    //     正文 15.5sp（行高 ≈ 21dp）→ 图标 20dp（I_BODY）
    //     小字 12.5~13.5sp（行高 ≈ 17dp）→ 图标 15dp（各页现在就是 14~16dp，本轮不动）
    //     大标题 29sp 旁的圆形按钮 → 图标 18dp + 圆底 36dp（I_TITLE / B_ICON）
    // 两类例外（不套上式）：
    //     · 方向细箭头（chevron）：本就是"比文字小一号"的指示符，15~16dp；
    //     · 空态插图（"大图标 + 一句话"）：是排版元素，44dp（I_EMPTY）。
    //
    // 容器随图标同步缩：IconBg 的圆底 / iconBox 的圆角方块，边长 ≈ 图标 × 1.9~2.1 ——
    // 只缩字形不缩底，看着还是"图标很大"。
    //
    // **热区与视觉解耦**：视觉可以变小，可点区域一律 ≥48dp（见 {@link #expandTouch}）。

    /** 图标尺寸：配正文/按钮（15.5sp，行高 ≈ 21dp）。 */
    public static final float I_BODY  = 20f;
    /** 图标尺寸：配大标题（29sp）旁的圆形按钮。 */
    public static final float I_TITLE = 18f;
    /** 图标底（圆形按钮 / 圆角方块）边长：≈ 图标 × 2。 */
    public static final float B_ICON  = 36f;
    /** 空态插图尺寸（唯一允许超出正文比例的图标）。 */
    public static final float I_EMPTY = 44f;

    // ------------------------------------------------------------ 矢量图标（手写，无依赖）

    /**
     * 取一个矢量图标并按 {@code color} 上色（{@code res/drawable/ic_*.xml}，统一 1.75dp 线宽）。
     *
     * <p>为什么要自己写矢量而不是用字符：显示器 🖥、齿轮 ⚙ 这类 emoji/符号在不同 ROM 上
     * 字形完全不同，有的还是彩色 emoji —— 那是"看着不高级"最直接的来源。矢量图标尺寸、
     * 线宽、端点全部可控，放大缩小都锐利。
     */
    public static android.graphics.drawable.Drawable iconDrawable(Context c, int resId,
                                                                  float sizeDp, int color) {
        if (c == null || resId == 0) return null;
        android.graphics.drawable.Drawable d;
        try {
            d = c.getResources().getDrawable(resId, c.getTheme());
        } catch (Throwable t) {
            return null;
        }
        if (d == null) return null;
        d = d.mutate();
        d.setTint(color);
        int s = dp(c, sizeDp);
        d.setBounds(0, 0, s, s);
        return d;
    }

    /**
     * 「圆角底 + 居中矢量图标」的合成底。
     *
     * <p>为什么做成 Drawable 而不是 {@code ImageView}：本 App 的圆形按钮字段全是
     * {@code TextView}（主题切换时各 View 直接改它们的字色/底色）。把它换成 ImageView
     * 会牵动一批字段类型和 applyTheme 分支；做成**背景**就能保持字段类型不变 ——
     * 文本留空，图形由背景画，{@link #setIcon} 负责换图换色。
     */
    public static final class IconBg extends android.graphics.drawable.Drawable {
        private final Context ctx;
        private final GradientDrawable box;
        private final int fill;
        private float radiusDp, iconDp;
        private android.graphics.drawable.Drawable glyph;

        public IconBg(Context c, int resId, int color, int fill, float radiusDp, float iconDp) {
            this.ctx = c;
            this.fill = fill;
            this.radiusDp = radiusDp;
            this.iconDp = iconDp;
            // Sadees：主色实心圆钮（fill == BRAND_FILL，即发送↑ / FAB / 回到底部这类
            // "当前激活"的圆钮）铺「紫 → 蓝」纵向渐变（BRAND_G1 → BRAND_G2）——
            // 渐变只点亮激活元素，其余底色照旧纯色。
            //
            // **真机 bug（2026-10-04 用户实拍）**：这里原来写 `brandGradient(radiusDp)`，
            // 而 brandGradient 的入参是**像素**（ChatAdapter 那条路传的是 dp(c,18)）。
            // 于是圆钮的半径被当成 px 用 —— 420dpi 真机上 21dp 的半径只剩 8dp，
            // 「↑ 发送键 / ↓ 回到底部 / FAB」全变成了**圆角方块**。这里补上 dp→px。
            if (fill == BRAND_FILL) {
                box = brandGradient(dp(c, radiusDp));
            } else {
                box = round(dp(c, radiusDp), fill);
            }
            glyph = iconDrawable(c, resId, iconDp, color);
        }

        /** 换图形与颜色（底色、尺寸沿用创建时的参数）。 */
        public void setGlyph(int resId, int color) {
            glyph = iconDrawable(ctx, resId, iconDp, color);
            invalidateSelf();
        }

        @Override public void draw(android.graphics.Canvas cv) {
            android.graphics.Rect b = getBounds();
            box.setBounds(b);
            box.draw(cv);
            if (glyph == null) return;
            int s = Math.min(dp(ctx, iconDp), Math.min(b.width(), b.height()));
            int cx = b.centerX(), cy = b.centerY();
            glyph.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
            glyph.draw(cv);
        }

        @Override public void setAlpha(int a) { box.setAlpha(a); if (glyph != null) glyph.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) {
            box.setColorFilter(f);
            if (glyph != null) glyph.setColorFilter(f);
        }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        @Override public void getOutline(android.graphics.Outline o) { box.getOutline(o); }
    }

    /**
     * 圆形图标按钮（默认 36dp 圆底 + 18dp 图标；**可点区域外扩到 48dp**），
     * 替代原来的 {@link #circleButton} 字符版。
     * 返回的仍然是 {@code TextView}（空文本），字段类型与 applyTheme 分支都不用改。
     *
     * <p>2026-10-03：图标从 19dp 收到 18dp（I_TITLE）—— 字号调小后图标要跟着缩；
     * 圆底 36dp 保持不变（它就是触区的载体，缩了会让手指更难点）。
     */
    public static TextView circleIconButton(Context c, int resId, int fill, int fg) {
        return circleIconButton(c, resId, fill, fg, I_TITLE, B_ICON);
    }

    public static TextView circleIconButton(Context c, int resId, int fill, int fg,
                                            float iconDp, float boxDp) {
        TextView t = new GlassTextView(c);
        t.setGravity(Gravity.CENTER);
        t.setBackground(new IconBg(c, resId, fg, fill, boxDp / 2f, iconDp));
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, boxDp), dp(c, boxDp)));
        t.setClickable(true);
        tap(t, 0.92f);
        // 圆形图标按钮天生比 48dp 小（36dp 才好看）：视觉不动，触区外扩到 48dp。
        expandTouch(t, 48f);
        return t;
    }

    /**
     * 圆角方块图标（如设备卡左侧那个 42dp 图标底）：圆角 {@code radiusDp}、
     * 底色 {@code fill}、图标 {@code iconDp} 居中。
     *
     * @param radiusDp 圆角；{@code 0} = 直角，{@code boxDp/2} = 圆形
     */
    public static TextView iconBox(Context c, int resId, int fill, int fg,
                                   float boxDp, float radiusDp, float iconDp) {
        TextView t = new GlassTextView(c);
        t.setGravity(Gravity.CENTER);
        t.setBackground(new IconBg(c, resId, fg, fill, radiusDp, iconDp));
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, boxDp), dp(c, boxDp)));
        return t;
    }

    /** 换掉 {@link #circleIconButton} / {@link #iconBox} 的图形与颜色（尺寸、底色沿用）。 */
    public static void setIcon(TextView v, int resId, int color) {
        if (v == null) return;
        android.graphics.drawable.Drawable d = v.getBackground();
        if (d instanceof IconBg) {
            ((IconBg) d).setGlyph(resId, color);
            v.invalidate();
        }
    }

    /**
     * 连**底色**一起换掉的版本（运行中的发送键：↑ 蓝底 → ■ 灰底）。
     *
     * <p>{@link IconBg} 的底色是创建时烘进去的，换底色只能重建一个底。
     * 尺寸参数（{@code boxDp}/{@code iconDp}）由调用方按原样传回，避免出现"换个图标就缩水"。
     */
    public static void setIconBg(TextView v, int resId, int color, int fill,
                                 float boxDp, float iconDp) {
        if (v == null) return;
        v.setBackground(new IconBg(v.getContext(), resId, color, fill, boxDp / 2f, iconDp));
        v.invalidate();
    }

    /**
     * 把任意 drawable 包成「固定尺寸」的。
     *
     * <p>为什么必须包：{@code TextView.setCompoundDrawablesWithIntrinsicBounds} 会用
     * drawable 的 **intrinsic** 尺寸去 setBounds（矢量 XML 里写的是 24dp），
     * 也就是说"传进去的尺寸参数"会被它覆盖掉 —— 想按 14dp 显示就必须让 intrinsic 也是 14dp。
     */
    private static final class FixedSizeDrawable extends android.graphics.drawable.Drawable {
        private final android.graphics.drawable.Drawable inner;
        private final int size;

        FixedSizeDrawable(android.graphics.drawable.Drawable inner, int size) {
            this.inner = inner;
            this.size = size;
        }

        @Override public void draw(android.graphics.Canvas cv) {
            inner.setBounds(getBounds());
            inner.draw(cv);
        }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int a) { inner.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { inner.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** 指定尺寸的矢量图标（用于 compound drawable，见 {@link FixedSizeDrawable}）。 */
    private static android.graphics.drawable.Drawable sizedIcon(Context c, int resId,
                                                                int color, float sizeDp) {
        android.graphics.drawable.Drawable d = iconDrawable(c, resId, sizeDp, color);
        return d == null ? null : new FixedSizeDrawable(d, dp(c, sizeDp));
    }

    /** 给文字加一个左侧矢量小图标（如「N 子智能体」前面的双人图标）。 */
    public static void setLeadingIcon(TextView t, int resId, int color, float sizeDp, float gapDp) {
        if (t == null) return;
        t.setCompoundDrawablesWithIntrinsicBounds(
                sizedIcon(t.getContext(), resId, color, sizeDp), null, null, null);
        t.setCompoundDrawablePadding(dp(t.getContext(), gapDp));
    }

    /** 给文字加一个**上方**矢量图标（空态那种"大图标 + 一句话"的排版）。 */
    public static void setTopIcon(TextView t, int resId, int color, float sizeDp, float gapDp) {
        if (t == null) return;
        t.setCompoundDrawablesWithIntrinsicBounds(
                null, sizedIcon(t.getContext(), resId, color, sizeDp), null, null);
        t.setCompoundDrawablePadding(dp(t.getContext(), gapDp));
    }

    /**
     * 小圆点（8dp）：在线状态、工具状态用的"●"。字符点在不同字重下大小不一，
     * 而且会被行高带偏；一个真 View 的圆点尺寸绝对可控、垂直居中永远正确。
     */
    public static View dot(Context c, float sizeDp, int color) {
        View v = new View(c);
        v.setBackground(pill(color));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** 圆点 + 灰字 的状态组合（在线/离线、运行中/已完成）。 */
    public static LinearLayout dotLabel(Context c, float dotDp, int dotColor,
                                        String text, float sizeSp, int textColor) {
        LinearLayout r = row(c);
        r.addView(dot(c, dotDp, dotColor));
        TextView t = text(c, text, sizeSp, textColor, false);
        t.setPadding(dp(c, 7), 0, 0, 0);
        r.addView(t);
        return r;
    }

    /**
     * 页脚一行低对比小字（版本号 / 一句提示）。
     *
     * <p>「我的设备」页在只有一张卡时下半屏是一大片空白，页面会显得"没做完"。
     * iOS 的做法是在内容后面留一句安静的灰字把页面收住，而不是硬撑内容。
     */
    public static TextView footer(Context c, String s) {
        TextView t = text(c, s, S_CAP1, INK_FAINT, false);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(dp(c, 3), 1.1f);
        t.setLetterSpacing(0.01f);
        t.setPadding(dp(c, 8), dp(c, 14), dp(c, 8), dp(c, 8));
        return t;
    }

    /**
     * iOS「分组内嵌列表」里某一行的背景：整组共用一张卡，靠 top/bottom 决定哪几个角是圆的
     * （第一行圆上两角、最后一行圆下两角、中间直角）—— 组内条目之间再用
     * {@link #insetDivider} 画细分隔线，就是系统设置那张列表。
     */
    public static GradientDrawable rowBg(Context c, int fill, boolean top, boolean bottom) {
        GradientDrawable d = round(0, fill);
        float r = dp(c, R_CARD);
        d.setCornerRadii(new float[] {
                top ? r : 0f, top ? r : 0f,          // 左上
                top ? r : 0f, top ? r : 0f,          // 右上
                bottom ? r : 0f, bottom ? r : 0f,    // 右下
                bottom ? r : 0f, bottom ? r : 0f }); // 左下
        return d;
    }

    // ------------------------------------------------------------ 表单控件（全 App 一套样式）

    /** 字段上方的小号灰标签。 */
    public static TextView fieldLabel(Context c, String s) {
        TextView t = text(c, s, S_FOOT, INK_SUB, false);
        t.setPadding(0, dp(c, 10), 0, dp(c, 4));
        return t;
    }

    /** 字段下方的说明 / 内联错误小字（错误时调用方把颜色改成 ERR 即可）。 */
    public static TextView fieldHint(Context c, String s) {
        TextView t = text(c, s, S_CAP1, INK_FAINT, false);
        t.setPadding(dp(c, 2), dp(c, 6), dp(c, 2), 0);
        return t;
    }

    /**
     * 统一的圆角输入框（iOS 的"浅灰内嵌输入框"）：无边框、灰底、10dp 圆角、17sp 文字。
     * 设置页、手动添加等所有表单都用这一个，别各自再画一套。
     */
    public static EditText field(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(S_CALLOUT);
        e.setHintTextColor(INK_FAINT);
        e.setTextColor(INK);
        e.setBackground(round(dp(c, 10), FIELD_BG));
        e.setPadding(dp(c, 14), dp(c, 11), dp(c, 14), dp(c, 11));
        e.setSingleLine(true);
        return e;
    }

    /**
     * 主按钮：**真胶囊**（圆角 = 高度一半）+ 细腻的蓝色纵向渐变 + 白字 17sp medium。
     *
     * <p>2026-10-02 高级感返工：旧版是 14dp 圆角的纯色实心块 —— 圆角不是胶囊、
     * 纯色无光感，观感就是"一个系统默认按钮"。现在两处改：
     * ① {@link #brandPill()} 把圆角设成 999（无论多高都是胶囊）；
     * ② 用 {@code GradientDrawable.setColors} 铺一层 #0A84FF → #0071E3 的极窄渐变，
     *    上沿微亮、下沿微沉，按钮立刻有了"实体"的光感。
     * <p>2026-10-03「按钮太胖、字显得小」调校：用户要的是**按钮变小**，不是把字放大。
     * 旧版 ≈51dp 高（minHeight 50 + 上下各 15dp 内边距），一行里并排三个就像三块砖。
     * 现在视觉高度 42dp、水平内边距 20→18dp；字号 S_HEAD **不动**（字号归全局类型比例管）。
     *
     * <p>但**可点区域不许跟着缩**：视图仍是 48dp（iOS 最小触区），多出的 3dp 上下
     * 用 {@link #insetV} 把胶囊缩进去 —— 视觉变小、热区不变。
     */
    public static TextView primaryButton(Context c, String s) {
        TextView t = text(c, s, S_HEAD, ON_BRAND, false);
        t.setTypeface(medium());
        t.setGravity(Gravity.CENTER);
        // [毛玻璃 v3] 视图高度回到 48dp（触区不变）：**不挂自绘外投影** ——
        // 那需要给 View 内缩留边，会把按钮和整张卡的高度撑高（demo 的 box-shadow 不占布局）。
        // 立体感改由"上亮下暗"的棱光承担（见 rimmed）。
        t.setMinHeight(dp(c, B_BTN_TOUCH));
        t.setPadding(dp(c, 16), dp(c, 9), dp(c, 16), dp(c, 9));
        t.setBackground(insetV(c, rimmed(brandPill(), 999f, BTN_RIM_PRIMARY), (B_BTN_TOUCH - B_BTN_H) / 2f));
        t.setClickable(true);
        tap(t);
        return t;
    }

    /**
     * 玻璃次按钮底（demo 的 `.btn-ghost`）：半透明白玻璃 + 白棱发丝线。
     *
     * <p>用在玻璃卡上 —— 卡体本身已经是玻璃，按钮再套不透明的 {@link #BTN_SOFT}
     * 就是在玻璃上贴一块死色块（"脏"的来源）。与 {@link #pillButton} 的尺寸/热区完全一致，
     * 只有底色不同。
     */
    public static GradientDrawable ghostPill() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { over(GLASS, SHEEN_1), GLASS });
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(999f);
        d.setStroke(1, GLASS_HAIRLINE);
        return d;
    }

    /** 玻璃次按钮：底色 {@link #ghostPill()}，尺寸/热区与 {@link #primaryButton} 完全一致。 */
    public static TextView ghostButton(Context c, String s) {
        TextView t = text(c, s, S_HEAD, INK, false);
        t.setTypeface(medium());
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, B_BTN_TOUCH));
        t.setPadding(dp(c, 14), dp(c, 9), dp(c, 14), dp(c, 9));
        // [毛玻璃 v3] **不挂外投影**：demo 的 `.btn-ghost` 只有 `inset 0 1px 0 var(--rim)`。
        t.setBackground(insetV(c, rimmed(ghostPill(), 999f), (B_BTN_TOUCH - B_BTN_H) / 2f));
        t.setClickable(true);
        tap(t);
        return t;
    }

    /**
     * 主色渐变填充（上 #0A84FF → 下 #0071E3）。
     *
     * @param radiusDp 圆角；传 {@code 999} = 胶囊（按钮）。**气泡不能传 999**：
     *                 圆角会被夹到 min(宽,高)/2，多行气泡会变成"体育场形"。
     */
    public static GradientDrawable brandGradient(float radiusDp) {
        // [毛玻璃 v3] 渐变方向 TOP_BOTTOM → **TL_BR（135°）**：demo 里用户气泡与主按钮
        // 都是 `linear-gradient(135deg, --brand-g1, --brand-g2)` —— 光从左上来，
        // 与玻璃件的斜射高光是同一个光源方向（同一个光源，画面才"成立"）。
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[] { BRAND_G1, BRAND_G2 });
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radiusDp);
        // [毛玻璃 v3] 亮边：第二篇把"1px 亮色边框"列为高级玻璃感的关键细节；
        // 主按钮同样吃这一条 —— 一圈 25% 白让它在玻璃卡上"立"起来，不再是一块平色块。
        d.setStroke(1, 0x40FFFFFF);
        return d;
    }

    /** 主按钮的胶囊底：细腻蓝色渐变（上 #0A84FF → 下 #0071E3），圆角 = 高度一半。 */
    public static GradientDrawable brandPill() {
        return brandGradient(999f);
    }

    /**
     * 次按钮（Sadees）：**浅紫灰底 + 深字**（浅 #EDEBF5 / 深 #26242E），无边框胶囊，带按压反馈。
     * 尺寸与 {@link #primaryButton} 完全一致（同一排并排时不能一高一矮）。
     */
    public static TextView secondaryButton(Context c, String s) {
        TextView t = text(c, s, S_HEAD, INK, false);
        t.setTypeface(medium());
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, B_BTN_TOUCH));
        t.setPadding(dp(c, 16), dp(c, 9), dp(c, 16), dp(c, 9));
        t.setBackground(insetV(c, pill(BTN_SOFT), (B_BTN_TOUCH - B_BTN_H) / 2f));
        t.setClickable(true);
        tap(t);
        return t;
    }

    /**
     * 任意底色的胶囊按钮：**与 {@link #primaryButton} 完全同一套尺寸与热区**
     * （视觉高 {@link #B_BTN_H}、可点高 {@link #B_BTN_TOUCH}、同一段按压反馈）。
     *
     * <p>为什么要它：渐变卡（见 {@link #featureFill}）上的按钮不能再用蓝色主按钮 ——
     * 蓝胶囊压在一张饱和色卡上是"两块重色打架"。但尺寸/热区必须与别处一致，
     * 所以把「配色」抽成参数，把「规格」留在这里唯一一份。
     *
     * @param fill 胶囊底色（渐变卡上用纯白或 22% 白）
     * @param fg   文字色（纯白底配 {@link #INK_ON_WHITE}）
     */
    public static TextView pillButton(Context c, String s, int fill, int fg) {
        return pillButton(c, s, fill, fg, 0);
    }

    /**
     * 任意底色 + **可选描边**的胶囊按钮（尺寸/热区与 {@link #primaryButton} 完全一致）。
     *
     * <p>[毛玻璃 v3] 为什么要描边：demo 的 `.btn-danger` 是
     * `background:rgba(255,59,48,.16); border:.5px solid rgba(255,59,48,.28)` ——
     * **那圈红边就是它的"高光"**。旧版 pillButton 没有描边参数，删除按钮只剩一块淡红底，
     * 用户反馈「右边红色高光怎么没了」。
     */
    public static TextView pillButton(Context c, String s, int fill, int fg, int strokeColor) {
        TextView t = text(c, s, S_HEAD, fg, false);
        t.setTypeface(medium());
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, B_BTN_TOUCH));
        t.setPadding(dp(c, 14), dp(c, 9), dp(c, 14), dp(c, 9));
        GradientDrawable d = pill(fill);
        if (android.graphics.Color.alpha(strokeColor) > 0) {
            d.setStroke(Math.max(1, dp(c, 0.5f)), strokeColor);
        }
        // [毛玻璃 v3] **不给内棱**：demo 的 `.btn-danger` 只有 background + border，没有内棱。
        t.setBackground(insetV(c, d, (B_BTN_TOUCH - B_BTN_H) / 2f));
        t.setClickable(true);
        tap(t);
        return t;
    }

    /**
     * 纯文字按钮（**无底色**）：取消、以及删除这类危险操作。
     *
     * <p>危险操作绝不能做成一块大红底 —— 那是"土"的另一半来源。iOS 的删除入口就是
     * 一行红字，视觉权重低但语义明确，不会把整个页面的注意力拽过去。
     *
     * <p>无底色 = 没有"视觉高度"，所以这里只把**可点区域**统一到 48dp
     * （旧版 46dp 高、内边距 14/13；与同排的胶囊按钮并排时看着更矮，现在齐平）。
     *
     * @param color 文字色（危险操作用 {@link #ERR}）
     */
    public static TextView textButton(Context c, String s, int color) {
        TextView t = text(c, s, S_HEAD, color, false);
        t.setTypeface(medium());
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, B_BTN_TOUCH));
        t.setPadding(dp(c, 12), dp(c, 9), dp(c, 12), dp(c, 9));
        t.setBackground(pill(0x00000000));
        t.setClickable(true);
        tap(t);
        return t;
    }

    /**
     * 按钮的**视觉**高度（dp）：胶囊真正画出来的高度。
     * 视图高度是 {@link #B_BTN_TOUCH}（48dp，热区），多出来的部分由 {@link #insetV} 缩掉。
     *
     * <p>2026-10-03「边缘/留白返工」：42 → 36dp。参考图里 iOS 26 的胶囊控件（Save /
     * Translate / 6:49）都是 32~36dp 的**细胶囊**，42dp 的按钮并排两个就像两块砖，
     * 也是"边缘过大"的一部分。热区仍由 {@link #B_BTN_TOUCH} 48dp 保证。
     */
    public static final float B_BTN_H = 36f;
    /**
     * [毛玻璃 v3] 主按钮内棱的强度（demo `.btn-primary` 是 `inset 0 1px 0 rgba(255,255,255,.35)`）。
     * 注意：**三个按钮的内棱强度按 demo 各不相同** —— 主按钮 .35（弱）、幽灵按钮 var(--rim)（强）、
     * 危险按钮**没有内棱**。之前我给三个都套同一条 95% 白棱，用户反馈「高光也和其他的不一个质感」。
     */
    public static final int BTN_RIM_PRIMARY = 0x73FFFFFF;
    /** 按钮的**可点**高度（dp）：iOS 最小触区，视觉再小也不许低于它。 */
    public static final float B_BTN_TOUCH = 48f;

    /** 把 drawable 在**上下**各缩进 {@code vDp}：视觉变小、控件本身（=热区）不变。 */
    private static android.graphics.drawable.Drawable insetV(
            Context c, android.graphics.drawable.Drawable d, float vDp) {
        int v = dp(c, vDp);
        return new android.graphics.drawable.InsetDrawable(d, 0, v, 0, v);
    }

    /** 按钮置灰：字段没填全时主按钮不可点（半透明 + 不吃点击）。 */
    public static void setButtonEnabled(TextView t, boolean on) {
        if (t == null) return;
        t.setEnabled(on);
        t.setClickable(on);
        t.setAlpha(on ? 1f : 0.45f);
    }

    /**
     * 手搓底部弹窗的上圆角卡片（不引入 Material BottomSheet 依赖）。
     * iOS bottom sheet：**26dp 上圆角**、顶部 10dp 留给抓手。
     *
     * <p>2026-10-03 液态玻璃：底不再是纯色 SURFACE，而是 {@link #GLASS_SHEET}
     * （88~90% 不透明，弹窗里全是文字，可读性优先）+ 同一条 26dp 上圆角的**棱光**
     * （顶部纯白/15% 白 1px、底部微暗）—— 玻璃的"厚度感"就来自这道上棱。
     * 用 CardBg 而不是 GradientDrawable，是为了让它自动带上棱光与顶部高光边。
     */
    public static LinearLayout sheetCard(Context c) {
        LinearLayout box = col(c);
        // **2026-10-04 真机实测修「滑动后弹窗变半透明」**（用户三次反馈，numpy 差分定位）：
        // 旧版用自绘 CardBg 当面板底，在带 FLAG_BLUR_BEHIND 的 Dialog 窗口里，滑动使窗口内容
        // 重绘后**面板底那一层整个没被画出来** —— 只剩行卡片和按钮，背后聊天文字整片透上来。
        // 换成框架标准的 GradientDrawable（纯色不透明 + 上两角圆角），走系统优化路径，
        // 不再出现"父层底丢失"。颜色用 SURFACE（各主题档里的不透明卡片色）。
        // [液态玻璃 2026-10-07] 实测 4 个弹窗**都已不再使用 FLAG_BLUR_BEHIND**（只剩 FLAG_DIM_BEHIND），
        // 而"自绘玻璃底丢层"那条历史结论正是由 FLAG_BLUR_BEHIND 引起的 → 换真玻璃值得实测。
        // 背后内容取 Activity 的 content 视图（弹窗窗口与 Activity 窗口都是全屏，窗口坐标可直接对齐）。
        android.view.View behind = activityContent(c);
        android.graphics.drawable.Drawable sheetGlass = sheetBg(c);
        box.setBackground(behind == null ? sheetGlass : blurredGlass(behind, box, 26f, R_SHEET, sheetGlass));
        box.setPadding(dp(c, M_CARD_PAD), dp(c, 8), dp(c, M_CARD_PAD), dp(c, 14));
        return box;
    }

    /**
     * 取 Activity 的 content 视图 —— 弹窗要糊的"背后那层"。
     *
     * <p>弹窗（Dialog）自带窗口，`getContext()` 往往是 ContextWrapper，需要逐层解包到 Activity；
     * 弹窗窗口与 Activity 窗口都是全屏，因此两边的"窗口坐标"可直接相减对齐。
     */
    private static android.view.View activityContent(Context c) {
        Context x = c;
        while (x instanceof android.content.ContextWrapper) {
            if (x instanceof android.app.Activity) {
                return ((android.app.Activity) x).findViewById(android.R.id.content);
            }
            x = ((android.content.ContextWrapper) x).getBaseContext();
        }
        return null;
    }

    /**
     * 底部弹窗的底：**纯色不透明** + 只圆上两角（iOS bottom sheet 制式）。
     *
     * <p>用系统 {@link GradientDrawable} 而不是自绘 {@link CardBg}：后者在这个
     * 「透明窗口 + 背景模糊 + 内容滚动」的组合下被实测出"底丢失"（见 {@link #sheetCard}）。
     */
    public static GradientDrawable sheetBg(Context c) {
        float r = dp(c, R_SHEET);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(SURFACE);
        // 顺序：左上x,y 右上x,y 右下x,y 左下x,y —— 下两角为 0（贴着屏幕下缘）
        g.setCornerRadii(new float[] { r, r, r, r, 0f, 0f, 0f, 0f });
        return g;
    }

    /**
     * 底部弹窗顶部的"抓手"横条（iOS 的 grabber）：36x5dp 圆角灰条、水平居中。
     * 加在 {@link #sheetCard} 的第一个子 View，视觉上就是一张可以下拉的 iOS 面板。
     */
    public static View grabber(Context c) {
        View v = new View(c);
        v.setBackground(pill(alpha(INK, 0.18f)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 36), dp(c, 5));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(c, 10);
        v.setLayoutParams(lp);
        return v;
    }

    // ------------------------------------------------------------ Sadees：引用卡的引号装饰

    /**
     * 引用卡（审批卡/提问卡）左上角的「"」淡色大引号装饰 —— Sadees 引用卡的标志性元素。
     * 一个 TextView 即可：36sp 的衬线引号，色 {@link #QUOTE}，负下边距把正文拉上来。
     * 纯排版装饰：不可点、不给无障碍读出来。
     */
    public static TextView quoteMark(Context c) {
        TextView t = new GlassTextView(c);
        t.setText("\u201C");
        t.setTextSize(36f);
        t.setTextColor(QUOTE);
        t.setIncludeFontPadding(false);
        t.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 2);
        t.setLayoutParams(lp);
        return t;
    }

    // ------------------------------------------------------------ 轻量 Markdown

    // 代码块底色 CODE_BG 同样并入上面的主题色板（浅色 #F1F3F9 / 深色 #26282E）。

    /**
     * 把模型输出的 Markdown 渲染成带样式的文本：
     * ``` 代码块 ```、`行内代码`、**加粗**、# 标题。
     */
    public static CharSequence md(Context c, String src) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        if (src == null) src = "";
        String[] lines = src.split("\n", -1);
        boolean inCode = false;
        StringBuilder codeBuf = new StringBuilder();
        // 代码块的字符范围：行内标记在代码块里必须原样保留，不能删也不能上样式
        List<int[]> codeRanges = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                if (inCode) {
                    int[] r = appendCodeBlock(out, codeBuf.toString());
                    if (r != null) codeRanges.add(r);
                    codeBuf.setLength(0);
                    inCode = false;
                } else {
                    inCode = true;
                }
                continue;
            }
            if (inCode) {
                if (codeBuf.length() > 0) codeBuf.append('\n');
                codeBuf.append(line);
                continue;
            }
            int start = out.length();
            if (trimmed.startsWith("#")) {
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') level++;
                String body = trimmed.substring(Math.min(level, trimmed.length())).trim();
                out.append(body);
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new RelativeSizeSpan(1.10f), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                out.append(line);
            }
            if (i < lines.length - 1) out.append('\n');
        }
        if (inCode && codeBuf.length() > 0) {
            int[] r = appendCodeBlock(out, codeBuf.toString());
            if (r != null) codeRanges.add(r);
        }

        inline(out, codeRanges);
        return out;
    }

    /** 追加一个代码块，返回它的 [start, end) 字符范围（空块返回 null）。 */
    private static int[] appendCodeBlock(SpannableStringBuilder out, String code) {
        if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
        int start = out.length();
        out.append(code);
        int end = out.length();
        if (end > start) {
            out.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new BackgroundColorSpan(CODE_BG), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new RelativeSizeSpan(0.92f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        out.append('\n');
        return end > start ? new int[]{start, end} : null;
    }

    /** [from, to) 是否与任一受保护范围（代码块）相交。 */
    private static boolean overlapsAny(List<int[]> ranges, int from, int to) {
        for (int[] r : ranges) {
            if (from < r[1] && to > r[0]) return true;
        }
        return false;
    }

    /**
     * 行内反引号与 ** 加粗：既上样式，也把标记符本身删掉。
     * 以前只上样式不删标记，用户会满屏看到 `**` 和反引号（评审 P1-13）。
     * 删除必须倒序（先删靠后的），否则前面的下标会错位；SpannableStringBuilder
     * 会自动平移已经设置好的 Span，所以删除后样式范围依然正确。
     */
    private static void inline(SpannableStringBuilder sb, List<int[]> codeRanges) {
        String s = sb.toString();
        List<int[]> cuts = new ArrayList<>();      // 每个待删除标记符的 [start, end)
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '`') {
                int close = s.indexOf('`', i + 1);
                int nl = s.indexOf('\n', i + 1);
                if (close > i && (nl < 0 || close < nl) && !overlapsAny(codeRanges, i, close + 1)) {
                    sb.setSpan(new TypefaceSpan("monospace"), i + 1, close, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new BackgroundColorSpan(CODE_BG), i + 1, close, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    cuts.add(new int[]{i, i + 1});
                    cuts.add(new int[]{close, close + 1});
                    i = close + 1;
                    continue;
                }
            } else if (ch == '*' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                int close = s.indexOf("**", i + 2);
                if (close > i && !overlapsAny(codeRanges, i, close + 2)) {
                    sb.setSpan(new StyleSpan(Typeface.BOLD), i + 2, close, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    cuts.add(new int[]{i, i + 2});
                    cuts.add(new int[]{close, close + 2});
                    i = close + 2;
                    continue;
                }
            }
            i++;
        }
        for (int k = cuts.size() - 1; k >= 0; k--) {
            int[] r = cuts.get(k);
            sb.delete(r[0], r[1]);
        }
    }

    public static int alpha(int color, float a) {
        return Color.argb(Math.round(255 * a), Color.red(color), Color.green(color), Color.blue(color));
    }

    /** 把时间戳渲染成「刚刚 / 3 分钟前 / 昨天 14:05 / 09-28 14:05」。 */
    public static String ago(long millis) {
        if (millis <= 0) return "";
        long now = System.currentTimeMillis();
        long d = now - millis;
        if (d < 60_000L) return "刚刚";
        if (d < 3600_000L) return (d / 60_000L) + " 分钟前";
        if (d < 86400_000L) return (d / 3600_000L) + " 小时前";
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA);
        return f.format(new java.util.Date(millis));
    }
}














