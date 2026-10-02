package com.dsh.mobile.ui;

import android.content.Context;
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

    public static int BG             = 0xFFF2F2F7;
    public static int SURFACE        = 0xFFFFFFFF;
    public static int SURFACE_2      = 0xFFFFFFFF;
    public static int BRAND          = 0xFF0A84FF;
    public static int BRAND_FILL     = 0xFF0A84FF;
    public static int BRAND_DEEP     = 0xFF0A84FF;
    public static int BRAND_SOFT     = 0xFFE9F2FF;
    public static int INK            = 0xFF000000;
    public static int INK_SUB        = 0xFF8E8E93;
    public static int INK_FAINT      = 0xFFAEAEB2;
    public static int LINE           = 0x0F000000;
    public static int SEP            = 0xFFC6C6C8;
    public static int PRESS          = 0xFFD1D1D6;
    public static int OK             = 0xFF34C759;
    public static int ERR            = 0xFFFF3B30;
    public static int WARN           = 0xFFFF9500;
    public static int ON_BRAND       = 0xFFFFFFFF;
    public static int ON_WARN        = 0xFFFFFFFF;
    public static int FIELD_BG       = 0xFFF2F2F7;
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
    public static int BADGE_OK_BG    = 0xFFE4F8E9;
    public static int BADGE_OFF_BG   = 0xFFE9E9EB;
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
    public static int BRAND_G1       = 0xFF0A84FF;
    public static int BRAND_G2       = 0xFF0071E3;

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
            BG             = 0xFF000000;   // 分组背景：纯黑
            SURFACE        = 0xFF1C1C1E;   // 卡片 / 顶部栏：比背景亮一档
            SURFACE_2      = 0xFF2C2C2E;   // 组内嵌套卡片（更亮一层）
            BRAND          = 0xFF4CA2FF;   // systemBlue 深色档：比浅色档提亮一档（深底上更通透）
            BRAND_FILL     = 0xFF0A84FF;
            BRAND_DEEP     = 0xFF6FB8FF;
            BRAND_SOFT     = 0xFF0A2540;   // 蓝色 12% 的深色淡底
            INK            = 0xFFFFFFFF;
            INK_SUB        = 0xFF98989F;
            INK_FAINT      = 0xFF7C7C80;
            LINE           = 0x1FFFFFFF;   // 卡片发丝线：8% 白（深色下唯一能"立起卡片"的东西）
            SEP            = 0xFF38383A;   // iOS opaqueSeparator
            PRESS          = 0xFF3A3A3C;
            OK             = 0xFF30D158;
            ERR            = 0xFFFF453A;
            WARN           = 0xFFFF9F0A;
            ON_BRAND       = 0xFFFFFFFF;
            ON_WARN        = 0xFFFFFFFF;
            FIELD_BG       = 0xFF2C2C2E;
            FIELD_ALT_BG   = 0xFF2C2C2E;
            CHIP_BG        = 0xFF2C2C2E;
            STOP_BG        = 0xFF3A3A3C;
            PLAN_BG        = 0xFF1C1C1E;
            SEG_BG         = 0xFF2C2C2E;
            SEG_THUMB      = 0xFF636366;
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
            BADGE_OK_BG    = 0xFF16351F;
            BADGE_OFF_BG   = 0xFF26282C;
            LINK_OK        = 0xFF30D158;
            CODE_BG        = 0xFF2C2C2E;
            SCAN_BG        = 0xFF000000;
            SCAN_TIP_BG    = 0x99000000;
            SCAN_PANEL_BG  = 0xE6101010;
            HAIRLINE       = 0x14FFFFFF;   // 深色卡片发丝线：#FFFFFF14（用户指定的深色描边）
            SHADOW         = 0x33000000;   // 纯黑底上阴影不可见，留着只为代码一致
            BRAND_G1       = 0xFF0A84FF;   // 渐变填充与白字对比度与浅色档一致，不随主题变
            BRAND_G2       = 0xFF0071E3;
        } else {
            // ---- 浅色：iOS systemGroupedBackground #F2F2F7 + 纯白卡片 + #0A84FF 主色
            BG             = 0xFFF2F2F7;
            SURFACE        = 0xFFFFFFFF;
            SURFACE_2      = 0xFFFFFFFF;
            BRAND          = 0xFF0A84FF;
            BRAND_FILL     = 0xFF0A84FF;
            BRAND_DEEP     = 0xFF0A84FF;
            BRAND_SOFT     = 0xFFE9F2FF;
            INK            = 0xFF000000;
            INK_SUB        = 0xFF8E8E93;
            INK_FAINT      = 0xFFAEAEB2;
            LINE           = 0x0F000000;   // 卡片发丝线：6% 黑（几乎看不见，但边缘不发虚）
            SEP            = 0xFFC6C6C8;
            PRESS          = 0xFFD1D1D6;
            OK             = 0xFF34C759;
            ERR            = 0xFFFF3B30;
            WARN           = 0xFFFF9500;
            ON_BRAND       = 0xFFFFFFFF;
            ON_WARN        = 0xFFFFFFFF;
            FIELD_BG       = 0xFFF2F2F7;
            FIELD_ALT_BG   = 0xFFF2F2F7;
            CHIP_BG        = 0xFFE9E9EB;
            STOP_BG        = 0xFFE5E5EA;
            PLAN_BG        = 0xFFF2F2F7;
            SEG_BG         = 0xFFE9E9EB;
            SEG_THUMB      = 0xFFFFFFFF;
            SWITCH_OFF     = 0xFFE9E9EA;
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
            BADGE_OK_BG    = 0xFFE4F8E9;
            BADGE_OFF_BG   = 0xFFE9E9EB;
            LINK_OK        = 0xFF34C759;
            CODE_BG        = 0xFFF2F2F7;
            SCAN_BG        = 0xFF000000;
            SCAN_TIP_BG    = 0x99000000;
            SCAN_PANEL_BG  = 0xE6101010;
            HAIRLINE       = 0x0F000000;   // 浅色卡片发丝线：#0000000F
            SHADOW         = 0x14000000;   // 浅色卡片柔和阴影
            BRAND_G1       = 0xFF0A84FF;
            BRAND_G2       = 0xFF0071E3;
        }
    }

    // ============================================================ 字号 / 行高 / 圆角（iOS 类型比例）
    //
    // 与系统设置同款层级：大标题 34 · 标题 22 · 正文 17 · 次要 15 · 脚注 13 · 说明 11~12。
    // 视图里尽量用这些常量，别再各写一个 15.5f/12.5f —— 层级一散，"iOS 感"就没了。
    public static final float S_LARGE   = 34f;   // Large Title（我的设备 / 对话）
    public static final float S_TITLE2  = 22f;   // Title2
    public static final float S_TITLE3  = 20f;   // Title3
    public static final float S_HEAD    = 17f;   // Headline / Body（设置行标题、气泡正文）
    public static final float S_BODY    = 17f;
    public static final float S_CALLOUT = 16f;   // Callout（按钮文字）
    public static final float S_SUB     = 15f;   // Subheadline
    public static final float S_FOOT    = 13f;   // Footnote（组标题、说明）
    public static final float S_CAP1    = 12f;   // Caption1
    public static final float S_CAP2    = 11f;   // Caption2（徽标）

    /** iOS 分组卡片圆角。 */
    public static final float R_CARD  = 14f;
    /** iOS 弹窗（bottom sheet）顶部圆角。 */
    public static final float R_SHEET = 22f;
    /** 按钮圆角（胶囊）。 */
    public static final float R_PILL  = 999f;
    /** iOS 列表行最小高度。 */
    public static final float H_ROW   = 52f;

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
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setLineSpacing(dp(c, 3), 1.06f);
        t.setIncludeFontPadding(false);
        // 「加粗」分两档（2026-10-02 高级感返工）：
        //   · 大标题（≥24sp）才用真 Bold —— 那是唯一需要"压得住画面"的地方；
        //   · 其余一律 medium。中文字形笔画密，DEFAULT_BOLD 在 17sp 上会把字糊成一团，
        //     这正是"看着不精致"的一个主要来源；medium 有分量又不糊。
        if (bold) t.setTypeface(sizeSp >= 24f ? Typeface.DEFAULT_BOLD : medium());
        // 大标题收紧字距（-0.02em），是 iOS Large Title 的关键细节：字大 + 字距松 = 廉价
        if (sizeSp >= 30f) t.setLetterSpacing(-0.02f);
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
     * 顶部栏的圆形图标按钮：36dp 圆形触区 + 居中字形，带 iOS 按压反馈
     * （按下整颗变淡缩一点，抬起回弹）。
     */
    public static TextView circleButton(Context c, String glyph, int fill, int fg) {
        TextView t = new TextView(c);
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
     * 列表行的按下高亮：正常态用传进来的 drawable，按下时换成 PRESS 灰底。
     * iOS 的行是按**整行底色**给反馈，不是只变文字色。
     *
     * 用 OnTouchListener 手动换背景（而不是 StateListDrawable）：ListView 的子 View
     * 不一定能收到 pressed 状态（选择器是画在列表层的），手动换色在任何容器里都成立。
     * 仍然返回 false，点击/长按照旧。
     */
    public static void tapRow(final View v, GradientDrawable normal, int pressFill) {
        if (v == null || normal == null) return;
        final GradientDrawable pressed = normal.getConstantState() == null
                ? round(normal.getCornerRadius(), pressFill)
                : (GradientDrawable) normal.getConstantState().newDrawable().mutate();
        pressed.setColor(pressFill);
        v.setBackground(normal);
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        view.setBackground(pressed);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        view.setBackground(normal);
                        break;
                    default:
                        break;
                }
                return false;   // 不消费：click / longClick 全按原样走
            }
        });
    }

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

    /** 分段控件的底槽（圆角灰底，内衬 2dp）。往里加 {@link #segmentItem}。 */
    public static LinearLayout segmentTrack(Context c) {
        LinearLayout l = row(c);
        l.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        l.setBackground(round(dp(c, 9), SEG_BG));
        l.setPadding(dp(c, 2), dp(c, 2), dp(c, 2), dp(c, 2));
        return l;
    }

    /** 分段控件的一项（默认未选中；选中态用 {@link #paintSegment}）。 */
    public static TextView segmentItem(Context c, String s) {
        TextView t = text(c, s, S_FOOT, INK, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 4), dp(c, 7), dp(c, 4), dp(c, 7));
        t.setClickable(true);
        paintSegment(t, false);
        return t;
    }

    /** 画分段项：选中 = 白滑块 + 深字，未选中 = 透明 + 灰字。 */
    public static void paintSegment(TextView t, boolean on) {
        if (t == null) return;
        t.setTextColor(on ? INK : INK_SUB);
        t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setBackground(on ? round(dp(t.getContext(), 7), SEG_THUMB) : round(0, 0x00000000));
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
        c0.setBackground(new CardBg(dp(c, R_CARD), SURFACE, dp(c, 1f), LINE, accent, dp(c, 3f)));
        c0.setElevation(dp(c, 2f));
        c0.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return c0;
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
        private final float radius, stroke, barW;
        private final int fill, line, accent;

        public CardBg(float radius, int fill, float stroke, int line, int accent, float barW) {
            this.radius = radius;
            this.fill = fill;
            this.stroke = Math.max(1f, stroke);
            this.line = line;
            this.accent = accent;
            this.barW = barW;
        }

        @Override
        public void draw(android.graphics.Canvas cv) {
            android.graphics.Rect b = getBounds();
            float w = b.width(), h = b.height();
            float inset = stroke / 2f;
            shape.reset();
            shape.addRoundRect(new android.graphics.RectF(inset, inset, w - inset, h - inset),
                    radius, radius, android.graphics.Path.Direction.CW);

            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setColor(fill);
            cv.drawPath(shape, p);

            if (android.graphics.Color.alpha(accent) != 0 && barW > 0f) {
                cv.save();
                cv.clipPath(shape);
                p.setColor(accent);
                cv.drawRect(0f, 0f, barW, h, p);
                cv.restore();
            }

            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setColor(line);
            cv.drawPath(shape, p);
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
        r.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
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
            box = round(dp(c, radiusDp), fill);
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
     * 圆形图标按钮（默认 36dp 触区 + 19dp 图标），替代原来的 {@link #circleButton} 字符版。
     * 返回的仍然是 {@code TextView}（空文本），字段类型与 applyTheme 分支都不用改。
     */
    public static TextView circleIconButton(Context c, int resId, int fill, int fg) {
        return circleIconButton(c, resId, fill, fg, 19f, 36f);
    }

    public static TextView circleIconButton(Context c, int resId, int fill, int fg,
                                            float iconDp, float boxDp) {
        TextView t = new TextView(c);
        t.setGravity(Gravity.CENTER);
        t.setBackground(new IconBg(c, resId, fg, fill, boxDp / 2f, iconDp));
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, boxDp), dp(c, boxDp)));
        t.setClickable(true);
        tap(t, 0.92f);
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
        TextView t = new TextView(c);
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
        t.setPadding(dp(c, 8), dp(c, 28), dp(c, 8), dp(c, 8));
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
        t.setPadding(0, dp(c, 12), 0, dp(c, 5));
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
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
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
     */
    public static TextView primaryButton(Context c, String s) {
        TextView t = text(c, s, S_HEAD, ON_BRAND, false);
        t.setTypeface(medium());
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 50));
        t.setPadding(dp(c, 20), dp(c, 15), dp(c, 20), dp(c, 15));
        t.setBackground(brandPill());
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
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[] { BRAND_G1, BRAND_G2 });
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radiusDp);
        return d;
    }

    /** 主按钮的胶囊底：细腻蓝色渐变（上 #0A84FF → 下 #0071E3），圆角 = 高度一半。 */
    public static GradientDrawable brandPill() {
        return brandGradient(999f);
    }

    /** 次按钮：iOS 的"灰底蓝字"（取消 / 扫码这类辅助操作），**无边框**胶囊，带按压反馈。 */
    public static TextView secondaryButton(Context c, String s) {
        TextView t = text(c, s, S_HEAD, BRAND, false);
        t.setTypeface(medium());
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 50));
        t.setPadding(dp(c, 20), dp(c, 15), dp(c, 20), dp(c, 15));
        t.setBackground(pill(CHIP_BG));
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
     * @param color 文字色（危险操作用 {@link #ERR}）
     */
    public static TextView textButton(Context c, String s, int color) {
        TextView t = text(c, s, S_HEAD, color, false);
        t.setTypeface(medium());
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 46));
        t.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        t.setBackground(pill(0x00000000));
        t.setClickable(true);
        tap(t);
        return t;
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
     * iOS bottom sheet：**22dp 上圆角**、卡片色底、顶部 10dp 留给抓手。
     */
    public static LinearLayout sheetCard(Context c) {
        LinearLayout box = col(c);
        GradientDrawable bg = round(0, SURFACE);
        int r = dp(c, R_SHEET);
        bg.setCornerRadii(new float[] { r, r, r, r, 0f, 0f, 0f, 0f });
        box.setBackground(bg);
        box.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 16));
        return box;
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
