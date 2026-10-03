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

    public static int BG             = 0xFFE6E4F0;
    public static int SURFACE        = 0xFFFFFFFF;
    public static int SURFACE_2      = 0xFFFFFFFF;
    public static int BRAND          = 0xFF0A84FF;
    public static int BRAND_FILL     = 0xFF6C5CE7;
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
    public static int BRAND_G1       = 0xFF7C4DFF;
    public static int BRAND_G2       = 0xFF448AFF;

    // ---- 液态玻璃（Liquid Glass，2026-10-03）
    //
    // 玻璃的「体」：半透明填充。为什么浅色取 60% 而不是更透：本 App 的页面底是均匀的
    // #F2F2F7，玻璃再透也只是透出同一块灰底 —— 真正的通透感来自**面板比页面亮一点点 +
    // 一条纯白的上棱**（见 CardBg 的棱光层）。60% 白落在 #F2F2F7 上约 #FAFAFC，
    // 与页面底刚好差一档，棱光才"有东西可衬"。
    //
    // GLASS_SHEET 比 GLASS 更不透明（88~90%）：底部弹窗里全是文字，玻璃再漂亮也不能
    // 牺牲可读性；iOS 的 sheet 本来就是"厚玻璃"，不是薄玻璃。
    public static int GLASS        = 0x99FFFFFF;   // 卡片体（浅色 60% 白 / 深色 56% 黑）
    public static int GLASS_SHEET  = 0xE0FFFFFF;   // 弹窗 / 抽屉体（88% / 90%）
    public static int GLASS_BAR    = 0xCCFFFFFF;   // 顶部栏 / 输入条（80% / 60%）
    public static int GLASS_RIM    = 0xFFFFFFFF;   // 棱光顶部（浅色纯白 1px，深色 15% 白）
    public static int GLASS_LO     = 0x0F000000;   // 棱光底部（浅色 6% 黑，深色 5% 白）
    /** 抽屉遮罩：不要死黑，25~35% 才"柔和"。 */
    public static int SCRIM        = 0x59000000;   // #000 35%

    // ---- 浅灰选中胶囊（2026-10-03 对齐 iOS 健康页参考图 ref-ios-health-cards.png）
    //
    // 参考图里侧栏选中行是一条**浅灰圆角胶囊**（≈#0000000F），**不是**蓝底白字。
    // 蓝底白字会把"选中"做成一个高饱和色块，整页的重心被它拽走；浅灰胶囊只做最低限度的
    // "我在这里"，彩色留给真正要强调的东西（图标、主按钮）—— 这正是参考图"克制用色"的来源。
    //
    // 深色档取 14% 白：纯黑底上 8% 白几乎看不见，14% 才刚好浮起来一档。
    public static int SELECT_BG     = 0x0F000000;   // 浅色 6% 黑 / 深色 14% 白
    public static int SELECT_BG_HI  = 0x14000000;   // 更明确一档（按下 / 强调项）

    // ---- 大圆角渐变卡（对齐 ref-ios-health-cards.png 的「彩色分类卡」）
    //
    // 参考图：圆角 ≈24dp、**横向**饱和渐变（左亮右深）、白字、左上小图标、无描边无阴影。
    // 六个色对取自参考图的六张分类卡，饱和度对齐（不荧光、也不灰）。
    // 渐变与主题无关：白字压在饱和色上，浅色/深色两档对比度都够，所以不进 applyTheme。
    public static final int[] GRAD_ORANGE = { 0xFFF0803C, 0xFFE9502B };
    public static final int[] GRAD_PURPLE = { 0xFFC46BE0, 0xFFA63BD1 };
    public static final int[] GRAD_INDIGO = { 0xFF7B86E8, 0xFF4A5AD6 };
    public static final int[] GRAD_BLUE   = { 0xFF63BDF0, 0xFF3D93DD };
    public static final int[] GRAD_ROSE   = { 0xFFF06A7A, 0xFFD93F55 };
    public static final int[] GRAD_TEAL   = { 0xFF4FBFC9, 0xFF2E9AAD };
    /** 主色渐变（入口卡专用）：与系统蓝同族，比 {@link #BRAND_FILL} 更有"光"。 */
    public static final int[] GRAD_BRAND  = { 0xFF7C4DFF, 0xFF448AFF };
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
            INK_FAINT      = 0xFF7C7C80;
            LINE           = 0x14FFFFFF;   // 卡片发丝线：#FFFFFF14（与 HAIRLINE 对齐；深色下唯一能"立起卡片"的东西）
            SEP            = 0xFF38383A;   // iOS opaqueSeparator
            PRESS          = 0xFF3A3A3C;
            OK             = 0xFF30D158;
            ERR            = 0xFFFF453A;
            WARN           = 0xFFFF9F0A;
            ON_BRAND       = 0xFFFFFFFF;
            ON_WARN        = 0xFFFFFFFF;
            FIELD_BG       = 0xFF2A2835;
            FIELD_ALT_BG   = 0xFF2A2835;
            CHIP_BG        = 0xFF2A2835;
            STOP_BG        = 0xFF383546;
            PLAN_BG        = 0xFF1C1B22;
            SEG_BG         = 0xFF2A2835;
            SEG_THUMB      = 0xFF4A4680;
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
            BADGE_OFF_BG   = 0xFF2A2835;
            LINK_OK        = 0xFF30D158;
            CODE_BG        = 0xFF2A2835;
            SCAN_BG        = 0xFF000000;
            SCAN_TIP_BG    = 0x99000000;
            SCAN_PANEL_BG  = 0xE6101010;
            HAIRLINE       = 0x14FFFFFF;   // 深色卡片发丝线：#FFFFFF14（用户指定的深色描边）
            SHADOW         = 0x33000000;   // 纯黑底上阴影不可见，留着只为代码一致
            BRAND_G1       = 0xFF9D6BFF;
            BRAND_G2       = 0xFF5C9DFF;
            // 液态玻璃（深色）：黑 45~60% 的玻璃体；纯黑底上白棱才看得见，所以深色档
            // 严格按规范的 15% 白（浅色档相反，见下）。
            GLASS          = 0xE61C1B22;   // #1C1C1E 90%（与 sheet 同档：深色档太透会把黑底"洗灰"）
            GLASS_SHEET    = 0xF21C1B22;   // 95%
            GLASS_BAR      = 0x99000000;   // #000 60%
            GLASS_RIM      = 0x26FFFFFF;   // 白 15%
            GLASS_LO       = 0x0DFFFFFF;   // 白 5%（深色玻璃的下棱略亮，不是黑）
            SCRIM          = 0x59000000;   // 遮罩不随主题变
            SELECT_BG      = 0x14FFFFFF;   // 深色选中胶囊：白 8%（纯黑底上要 14% 才浮得起来）
            SELECT_BG_HI   = 0x33FFFFFF;   // 深色强调一档：白 20%
        } else {
            // ---- 浅色：iOS systemGroupedBackground #F2F2F7 + 纯白卡片 + #0A84FF 主色
            BG             = 0xFFE6E4F0;
            SURFACE        = 0xFFFDFCFE;
            SURFACE_2      = 0xFFFFFFFF;
            BRAND          = 0xFF6C5CE7;
            BRAND_FILL     = 0xFF6C5CE7;
            BRAND_DEEP     = 0xFF5A4BD6;
            BRAND_SOFT     = 0xFFEAE6FA;
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
            FIELD_BG       = 0xFFEDEBF5;
            FIELD_ALT_BG   = 0xFFEDEBF5;
            CHIP_BG        = 0xFFE0DDEB;
            STOP_BG        = 0xFFDCD9EA;
            PLAN_BG        = 0xFFEDEBF5;
            SEG_BG         = 0xFFDBD8E8;
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
            BADGE_OK_BG    = 0xFFE4F8E9;
            BADGE_OFF_BG   = 0xFFE9E9EB;
            LINK_OK        = 0xFF34C759;
            CODE_BG        = 0xFFE9E6F2;
            SCAN_BG        = 0xFF000000;
            SCAN_TIP_BG    = 0x99000000;
            SCAN_PANEL_BG  = 0xE6101010;
            HAIRLINE       = 0x0F000000;   // 浅色卡片发丝线：#0000000F
            SHADOW         = 0x14000000;   // 浅色卡片柔和阴影
            BRAND_G1       = 0xFF7C4DFF;
            BRAND_G2       = 0xFF448AFF;
            // 液态玻璃（浅色）：卡片体 88% 白 → 落在 #F2F2F7 上约 #FDFDFE，**和 iOS 的纯白卡片同档**。
            // 2026-10-03 从 60% 提到 88%：60% 落在 #F2F2F7 上只有 #FAFAFC，卡片和页面底只差 8 级，
            // 截图上看就是"一整块灰，没有卡片"——这正是"不是 iOS 26、太素"的直接原因。
            // 玻璃感不再靠"透"，改由**上棱 1px 纯白 + 下棱微暗 + 发丝描边**承担（见 CardBg）。
            GLASS          = 0xE0FFFFFF;   // 白 88%
            // GLASS_SHEET 提到 95%：弹窗/抽屉里全是文字，本 ROM 的真模糊不生效（实测），
            // 88% 时背后的对话正文会以 12% 透上来（子智能体弹窗里能读出一行行"鬼影"），
            // 可读性优先 → 直接当"厚玻璃"用。这就是规范里"模糊不可用时的回退"。
            GLASS_SHEET    = 0xF2FFFFFF;   // 白 95%
            GLASS_BAR      = 0xCCFFFFFF;   // 白 80%
            GLASS_RIM      = 0xFFFFFFFF;   // 纯白 1px
            GLASS_LO       = 0x0F000000;   // 黑 6%（下棱微暗）
            SCRIM          = 0x59000000;   // 遮罩不随主题变
            SELECT_BG      = 0x0F000000;   // 浅色选中胶囊：黑 6%（参考图侧栏选中行的浅灰胶囊）
            SELECT_BG_HI   = 0x14000000;   // 浅色强调一档：黑 8%
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
    public static final float S_LARGE   = 29f;   // Large Title（我的设备 / 对话） 改前 34
    public static final float S_TITLE2  = 19f;   // Title2                        改前 22
    public static final float S_TITLE3  = 17.5f; // Title3                        改前 20
    public static final float S_HEAD    = 15.5f; // Headline / Body（设置行标题、气泡正文）改前 17
    public static final float S_BODY    = 15.5f; // Body                          改前 17
    public static final float S_CALLOUT = 14.5f; // Callout（按钮文字）            改前 16
    public static final float S_SUB     = 13.5f; // Subheadline                   改前 15
    public static final float S_FOOT    = 12.5f; // Footnote（组标题、说明）        改前 13
    public static final float S_CAP1    = 11.5f; // Caption1                      改前 12
    public static final float S_CAP2    = 10.5f; // Caption2（徽标）               改前 11

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
    public static final float M_GAP      = 14f;
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
        return round(0, GLASS_BAR);
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
        TextView t = new TextView(c);
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
        t.setPadding(dp(c, 4), dp(c, 6), dp(c, 4), dp(c, 6));
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
        // 玻璃卡：GLASS 半透明体 + 发丝描边 + 顶部棱光/高光（都在 CardBg 里）。
        //
        // **不要再给玻璃卡 setElevation（2026-10-03 模拟器实测）**：卡片填充是半透明的，
        // 系统的 elevation 阴影会从半透明体**下面透出来**，在卡内画出一圈灰环 + 中间一块
        // 白心（模拟器截图 1080x1920 逐像素采样：卡内 18dp 一圈 = #E5E5E7，正中 = #FAFAFC，
        // 看上去就是"灰卡里套了个白方框"——正是用户报的那个坏卡片）。
        // 对照实验：同一次构建把填充换成不透明品红 → 环消失；只去掉 elevation → 环也消失。
        // 结论：**半透明 = 不能挂 elevation**。深度感改由「上棱高光 + 下棱微暗 + 发丝描边」承担。
        c0.setBackground(new CardBg(dp(c, R_CARD), GLASS, dp(c, 1f), LINE, accent, dp(c, 3f)));
        c0.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return c0;
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
        return entryCard(c, grad, iconRes, white, alpha(white, 0.22f),
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
        card.setBackground(grad == null
                ? new CardBg(dp(c, R_CARD), GLASS, dp(c, 1f), LINE, 0x00000000, dp(c, 3f))
                : featureFill(c, grad));
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
        /** true = 只圆上两角（底部弹窗那种"贴着屏幕下缘"的玻璃）。 */
        private boolean topOnly;
        /** 顶部高光带的像素高（1.5dp），由构造时的 density 决定。 */
        private final float hiH;

        public CardBg(float radius, int fill, float stroke, int line, int accent, float barW) {
            this.radius = radius;
            this.fill = fill;
            this.stroke = Math.max(1f, stroke);
            this.line = line;
            this.accent = accent;
            this.barW = barW;
            this.hiH = Math.max(1f, stroke * 1.5f);
        }

        /** 只圆上两角（底部弹窗 / sheet 用）。返回 this 便于链式写。 */
        public CardBg topOnly(boolean v) { this.topOnly = v; return this; }

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

            // ① 玻璃体（半透明填充）
            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setShader(null);
            p.setColor(fill);
            cv.drawPath(shape, p);

            // ② 左侧 3dp 强调条（被卡片圆角裁掉才不露方角）
            if (android.graphics.Color.alpha(accent) != 0 && barW > 0f) {
                cv.save();
                cv.clipPath(shape);
                p.setColor(accent);
                cv.drawRect(0f, 0f, barW, h, p);
                cv.restore();
            }

            // ③ 发丝描边（原有能力：让边缘不发虚）
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setShader(null);
            p.setColor(line);
            cv.drawPath(shape, p);

            // ④ 顶部高光边 + 底部微暗（液态玻璃的关键：玻璃的"反光棱"）。
            //
            // **真机教训（2026-10-03，PGT-AN10）**：这里原来用 LinearGradient 描一条
            // 纵向渐变棱 + 一条横向渐变高光带，逻辑上没问题，但真机逐像素采样发现
            // **两者一个像素都没画出来**（卡片最上面一行 = 玻璃体自身亮度 251，没有任何
            // 更亮的一行）。所以改成**纯色叠层**：把高光做成 4 条逐级变淡的实心细带
            // （1.0 / 0.5 / 0.25 / 0.10 的 GLASS_RIM）—— 不用 shader，肉眼就是一条柔和
            // 的顶光，且与已经验证能画出来的发丝线走同一条绘制路径。
            cv.save();
            cv.clipPath(shape);
            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setShader(null);
            float band = Math.max(1f, stroke * 0.6f);
            float[] bandAlpha = { 1f, 0.5f, 0.25f, 0.10f };
            for (int i = 0; i < bandAlpha.length; i++) {
                p.setColor(alpha(GLASS_RIM, bandAlpha[i]));
                cv.drawRect(0f, band * i, w, band * (i + 1), p);
            }
            // 底棱：一条极淡的暗线，让玻璃"有厚度"（浅色 6% 黑 / 深色 5% 白）
            p.setColor(GLASS_LO);
            cv.drawRect(0f, h - Math.max(1f, stroke), w, h, p);
            cv.restore();
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
        TextView t = new TextView(c);
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
        t.setMinHeight(dp(c, B_BTN_TOUCH));
        t.setPadding(dp(c, 16), dp(c, 9), dp(c, 16), dp(c, 9));
        t.setBackground(insetV(c, brandPill(), (B_BTN_TOUCH - B_BTN_H) / 2f));
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

    /**
     * 次按钮：iOS 的"灰底蓝字"（取消 / 扫码这类辅助操作），**无边框**胶囊，带按压反馈。
     * 尺寸与 {@link #primaryButton} 完全一致（同一排并排时不能一高一矮）。
     */
    public static TextView secondaryButton(Context c, String s) {
        TextView t = text(c, s, S_HEAD, BRAND, false);
        t.setTypeface(medium());
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, B_BTN_TOUCH));
        t.setPadding(dp(c, 16), dp(c, 9), dp(c, 16), dp(c, 9));
        t.setBackground(insetV(c, pill(CHIP_BG), (B_BTN_TOUCH - B_BTN_H) / 2f));
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
        TextView t = text(c, s, S_HEAD, fg, false);
        t.setTypeface(medium());
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, B_BTN_TOUCH));
        t.setPadding(dp(c, 14), dp(c, 9), dp(c, 14), dp(c, 9));
        t.setBackground(insetV(c, pill(fill), (B_BTN_TOUCH - B_BTN_H) / 2f));
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
        box.setBackground(new CardBg(dp(c, R_SHEET), GLASS_SHEET, dp(c, 1f), LINE,
                0x00000000, 0f).topOnly(true));
        box.setPadding(dp(c, M_CARD_PAD), dp(c, 8), dp(c, M_CARD_PAD), dp(c, 14));
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
