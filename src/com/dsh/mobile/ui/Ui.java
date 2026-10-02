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

    // ============================================================ 主题色板
    //
    // 全 App **只从这里取色**。下面每个字段都是「当前生效的那一档颜色」：启动时和每次
    // 切换主题 / 系统深浅色变化时，由 applyTheme(dark) 用浅色/深色两套同名色整体重写。
    //
    // 视图里绝不要再写 0xAARRGGBB 字面量 —— 写死的那一处切主题时不会跟着变。
    //
    // 语义表（浅色 / 深色）：
    //   正文    INK            #17181C / #E6E6E6      页面主要文字
    //   次要    INK_SUB        #6B7280 / #A8ADB8      说明、副标题
    //   弱化    INK_FAINT      #9CA3AF / #868C99      占位符、时间、空态
    //   品牌    BRAND          #4D6BFE / #8FA6FF      品牌色**文字与描边**（要压在深底上）
    //   品牌实心 BRAND_FILL    #4D6BFE / #3D5AF0      品牌色**填充块**（配 ON_BRAND 文字）
    //   品牌深  BRAND_DEEP     #3A57E8 / #A9BCFF      选中态标题（压在 BRAND_SOFT 上）
    //   品牌淡底 BRAND_SOFT    #EEF2FF / #1E2A4D      品牌色浅底（工具卡、当前会话卡）
    //   背景    BG             #F4F6FB / #121212      页面底色
    //   卡片    SURFACE        #FFFFFF / #1E1E1E      卡片、气泡、顶部栏底色
    //   描边    LINE           #E8EBF2 / #2A2C31      分隔线、卡片描边
    //   成功    OK             #16A34A / #4ADE80
    //   错误    ERR            #DC2626 / #F87171
    //   警告    WARN           #D97706 / #FBBF24
    //   品牌上的字 ON_BRAND   #FFFFFF / #FFFFFF
    //   警告上的字 ON_WARN    #FFFFFF / #201A00      深色下琥珀底必须配深字，白字看不清
    //   输入框底 FIELD_BG     #F7F8FC / #232529
    //   输入条底 FIELD_ALT_BG #F5F6FA / #232529
    //   圆形小按钮底 CHIP_BG  #F1F3F9 / #26282D
    //   停止按钮底 STOP_BG    #E5E9F5 / #2E3340
    //   任务提要底 PLAN_BG    #F5F8FF / #1B2233
    //   横幅-警告 BANNER_WARN_BG/FG  #FFF7E6/#92400E / #3A2E12/#FCD34D
    //   横幅-错误 BANNER_ERR_BG/FG   #FEF2F2/#991B1B / #3A1D1D/#FCA5A5
    //   卡片描边-专家团 LINE_AGENT     #C7D2FE / #3F5090
    //   卡片描边-审批   LINE_APPROVAL  #FDE68A / #7A6420
    //   卡片描边-拒绝   LINE_DANGER    #FCA5A5 / #7F3A3A
    //   卡片描边-提问   LINE_QUESTION  #BFD2FF / #3F5A99
    //   卡片描边-交付物 LINE_OK        #BFE7CC / #2F5A44
    //   选项选中描边    LINE_SELECTED  #C7D3FF / #3D4E85
    //   徽标底-在线 BADGE_OK_BG  #E7F7EC / #16351F
    //   徽标底-离线 BADGE_OFF_BG #F1F3F7 / #26282C
    //   下载成功    LINK_OK      #2FB344 / #4ADE80
    //   代码块底    CODE_BG      #F1F3F9 / #26282E
    //   扫码页（相机取景，两套主题下都保持深底，这是取景页的正确做法）
    //     SCAN_BG #000000 / SCAN_TIP_BG #99000000 / SCAN_PANEL_BG #E6101010

    public static int BG             = 0xFFF4F6FB;
    public static int SURFACE        = 0xFFFFFFFF;
    public static int BRAND          = 0xFF4D6BFE;
    public static int BRAND_FILL     = 0xFF4D6BFE;
    public static int BRAND_DEEP     = 0xFF3A57E8;
    public static int BRAND_SOFT     = 0xFFEEF2FF;
    public static int INK            = 0xFF17181C;
    public static int INK_SUB        = 0xFF6B7280;
    public static int INK_FAINT      = 0xFF9CA3AF;
    public static int LINE           = 0xFFE8EBF2;
    public static int OK             = 0xFF16A34A;
    public static int ERR            = 0xFFDC2626;
    public static int WARN           = 0xFFD97706;
    public static int ON_BRAND       = 0xFFFFFFFF;
    public static int ON_WARN        = 0xFFFFFFFF;
    public static int FIELD_BG       = 0xFFF7F8FC;
    public static int FIELD_ALT_BG   = 0xFFF5F6FA;
    public static int CHIP_BG        = 0xFFF1F3F9;
    public static int STOP_BG        = 0xFFE5E9F5;
    public static int PLAN_BG        = 0xFFF5F8FF;
    public static int BANNER_WARN_BG = 0xFFFFF7E6;
    public static int BANNER_WARN_FG = 0xFF92400E;
    public static int BANNER_ERR_BG  = 0xFFFEF2F2;
    public static int BANNER_ERR_FG  = 0xFF991B1B;
    public static int LINE_AGENT     = 0xFFC7D2FE;
    public static int LINE_APPROVAL  = 0xFFFDE68A;
    public static int LINE_DANGER    = 0xFFFCA5A5;
    public static int LINE_QUESTION  = 0xFFBFD2FF;
    public static int LINE_OK        = 0xFFBFE7CC;
    public static int LINE_SELECTED  = 0xFFC7D3FF;
    public static int BADGE_OK_BG    = 0xFFE7F7EC;
    public static int BADGE_OFF_BG   = 0xFFF1F3F7;
    public static int LINK_OK        = 0xFF2FB344;
    public static int CODE_BG        = 0xFFF1F3F9;
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
            BG             = 0xFF121212;   // 背景：接近纯黑的一档
            SURFACE        = 0xFF1E1E1E;   // 卡片：比背景略亮
            BRAND          = 0xFF8FA6FF;   // 品牌色（文字/描边）提亮，才压得住深底
            BRAND_FILL     = 0xFF3D5AF0;   // 品牌实心块保持深一档，白字才够对比
            BRAND_DEEP     = 0xFFA9BCFF;
            BRAND_SOFT     = 0xFF1E2A4D;
            INK            = 0xFFE6E6E6;
            INK_SUB        = 0xFFA8ADB8;
            INK_FAINT      = 0xFF868C99;
            LINE           = 0xFF2A2C31;
            OK             = 0xFF4ADE80;
            ERR            = 0xFFF87171;
            WARN           = 0xFFFBBF24;
            ON_BRAND       = 0xFFFFFFFF;
            ON_WARN        = 0xFF201A00;
            FIELD_BG       = 0xFF232529;
            FIELD_ALT_BG   = 0xFF232529;
            CHIP_BG        = 0xFF26282D;
            STOP_BG        = 0xFF2E3340;
            PLAN_BG        = 0xFF1B2233;
            BANNER_WARN_BG = 0xFF3A2E12;
            BANNER_WARN_FG = 0xFFFCD34D;
            BANNER_ERR_BG  = 0xFF3A1D1D;
            BANNER_ERR_FG  = 0xFFFCA5A5;
            LINE_AGENT     = 0xFF3F5090;
            LINE_APPROVAL  = 0xFF7A6420;
            LINE_DANGER    = 0xFF7F3A3A;
            LINE_QUESTION  = 0xFF3F5A99;
            LINE_OK        = 0xFF2F5A44;
            LINE_SELECTED  = 0xFF3D4E85;
            BADGE_OK_BG    = 0xFF16351F;
            BADGE_OFF_BG   = 0xFF26282C;
            LINK_OK        = 0xFF4ADE80;
            CODE_BG        = 0xFF26282E;
            SCAN_BG        = 0xFF000000;
            SCAN_TIP_BG    = 0x99000000;
            SCAN_PANEL_BG  = 0xE6101010;
        } else {
            BG             = 0xFFF4F6FB;
            SURFACE        = 0xFFFFFFFF;
            BRAND          = 0xFF4D6BFE;
            BRAND_FILL     = 0xFF4D6BFE;
            BRAND_DEEP     = 0xFF3A57E8;
            BRAND_SOFT     = 0xFFEEF2FF;
            INK            = 0xFF17181C;
            INK_SUB        = 0xFF6B7280;
            INK_FAINT      = 0xFF9CA3AF;
            LINE           = 0xFFE8EBF2;
            OK             = 0xFF16A34A;
            ERR            = 0xFFDC2626;
            WARN           = 0xFFD97706;
            ON_BRAND       = 0xFFFFFFFF;
            ON_WARN        = 0xFFFFFFFF;
            FIELD_BG       = 0xFFF7F8FC;
            FIELD_ALT_BG   = 0xFFF5F6FA;
            CHIP_BG        = 0xFFF1F3F9;
            STOP_BG        = 0xFFE5E9F5;
            PLAN_BG        = 0xFFF5F8FF;
            BANNER_WARN_BG = 0xFFFFF7E6;
            BANNER_WARN_FG = 0xFF92400E;
            BANNER_ERR_BG  = 0xFFFEF2F2;
            BANNER_ERR_FG  = 0xFF991B1B;
            LINE_AGENT     = 0xFFC7D2FE;
            LINE_APPROVAL  = 0xFFFDE68A;
            LINE_DANGER    = 0xFFFCA5A5;
            LINE_QUESTION  = 0xFFBFD2FF;
            LINE_OK        = 0xFFBFE7CC;
            LINE_SELECTED  = 0xFFC7D3FF;
            BADGE_OK_BG    = 0xFFE7F7EC;
            BADGE_OFF_BG   = 0xFFF1F3F7;
            LINK_OK        = 0xFF2FB344;
            CODE_BG        = 0xFFF1F3F9;
            SCAN_BG        = 0xFF000000;
            SCAN_TIP_BG    = 0x99000000;
            SCAN_PANEL_BG  = 0xE6101010;
        }
    }

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

    public static TextView circleButton(Context c, String glyph, int fill, int fg) {
        TextView t = new TextView(c);
        t.setText(glyph);
        t.setTextSize(17f);
        t.setTextColor(fg);
        t.setGravity(Gravity.CENTER);
        int s = dp(c, 40);
        t.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        t.setBackground(pill(fill));
        t.setClickable(true);
        return t;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.6f))));
        return v;
    }

    // ------------------------------------------------------------ 表单控件（全 App 一套样式）

    // 输入框底色 FIELD_BG 已并入上面的主题色板（浅色 #F7F8FC / 深色 #232529）：
    // 比卡片底色深一点，12dp 圆角 + 0.8dp 描边才看得出来。

    /** 字段上方的小号灰标签。 */
    public static TextView fieldLabel(Context c, String s) {
        TextView t = text(c, s, 12.5f, INK_SUB, false);
        t.setPadding(0, dp(c, 12), 0, dp(c, 5));
        return t;
    }

    /** 字段下方的说明 / 内联错误小字（错误时调用方把颜色改成 ERR 即可）。 */
    public static TextView fieldHint(Context c, String s) {
        TextView t = text(c, s, 11.5f, INK_FAINT, false);
        t.setPadding(dp(c, 2), dp(c, 6), dp(c, 2), 0);
        return t;
    }

    /**
     * 统一的圆角输入框：系统默认的下划线输入框跟卡片风格不搭，
     * 这里统一成「浅底 + 12dp 圆角 + 0.8dp 描边 + 12dp 内边距」。
     * 设置页、手动添加等所有表单都用这一个，别各自再画一套。
     */
    public static EditText field(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(14f);
        e.setHintTextColor(INK_FAINT);
        e.setTextColor(INK);
        e.setBackground(roundStroke(dp(c, 12), FIELD_BG, dp(c, 0.8f), LINE));
        e.setPadding(dp(c, 12), dp(c, 11), dp(c, 12), dp(c, 11));
        e.setSingleLine(true);
        return e;
    }

    /** 主按钮：品牌色实心圆角（保存 / 连接这类正向操作）。 */
    public static TextView primaryButton(Context c, String s) {
        TextView t = text(c, s, 14.5f, ON_BRAND, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(c, 12), 0, dp(c, 12));
        t.setBackground(round(dp(c, 999), BRAND_FILL));
        t.setClickable(true);
        return t;
    }

    /** 次按钮：白底浅描边圆角（取消 / 扫码这类辅助操作）。 */
    public static TextView secondaryButton(Context c, String s) {
        TextView t = text(c, s, 14.5f, INK, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(c, 12), 0, dp(c, 12));
        t.setBackground(roundStroke(dp(c, 999), SURFACE, dp(c, 1f), LINE));
        t.setClickable(true);
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
     * 手搓底部弹窗的上圆角白卡片（不引入 Material BottomSheet 依赖）。
     * 半径 / 内边距与「添加设备」弹窗、设置页卡片保持一致。
     */
    public static LinearLayout sheetCard(Context c) {
        LinearLayout box = col(c);
        GradientDrawable bg = round(0, SURFACE);
        int r = dp(c, 20);
        bg.setCornerRadii(new float[] { r, r, r, r, 0f, 0f, 0f, 0f });
        box.setBackground(bg);
        box.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 16));
        return box;
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
