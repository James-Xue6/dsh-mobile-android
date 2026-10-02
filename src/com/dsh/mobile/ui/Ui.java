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
    public static int LINE           = 0xFFE5E5EA;
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
            BRAND          = 0xFF0A84FF;   // systemBlue（深色下**不**提亮，iOS 就是这个值）
            BRAND_FILL     = 0xFF0A84FF;
            BRAND_DEEP     = 0xFF0A84FF;
            BRAND_SOFT     = 0xFF0A2540;   // 蓝色 12% 的深色淡底
            INK            = 0xFFFFFFFF;
            INK_SUB        = 0xFF98989F;
            INK_FAINT      = 0xFF7C7C80;
            LINE           = 0xFF2C2C2E;   // 卡片发丝线：很淡，只用来"分界"
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
            LINE           = 0xFFE5E5EA;
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
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
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
                        view.animate().alpha(0.55f)
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
     * 一张 iOS 分组卡片：纯白/深灰底、14dp 圆角、**极细**发丝描边（不是粗彩边）。
     * 卡片靠"底色与分组背景的色差"立起来（iOS 的做法），不靠 elevation 阴影
     * —— Android 的 elevation 在自绘背景上表现很差，会把浅色卡片画成一坨脏阴影。
     */
    public static LinearLayout card(Context c) {
        LinearLayout c0 = col(c);
        c0.setBackground(roundStroke(dp(c, R_CARD), SURFACE, dp(c, 0.5f), LINE));
        c0.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return c0;
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

    /** iOS 的 "›" 细箭头（列表行右侧的"可以进去"暗示）。 */
    public static TextView chevron(Context c) {
        TextView t = text(c, "›", S_TITLE3, INK_FAINT, false);
        t.setPadding(dp(c, 6), 0, 0, 0);
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

    /** 主按钮：主色实心圆角（保存 / 连接这类正向操作），带 iOS 按压反馈。 */
    public static TextView primaryButton(Context c, String s) {
        TextView t = text(c, s, S_CALLOUT, ON_BRAND, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14));
        t.setBackground(round(dp(c, 14), BRAND_FILL));
        t.setClickable(true);
        tap(t);
        return t;
    }

    /** 次按钮：iOS 的"灰底蓝字"（取消 / 扫码这类辅助操作），无边框，带按压反馈。 */
    public static TextView secondaryButton(Context c, String s) {
        TextView t = text(c, s, S_CALLOUT, BRAND, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14));
        t.setBackground(round(dp(c, 14), CHIP_BG));
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
