package com.dsh.mobile.ui;

import android.content.Context;
import android.content.res.Configuration;

/**
 * 主题模式解析（浅色 / 深色两套**具体颜色**在 {@link Ui#applyTheme(boolean)} 里）。
 *
 * 这里只负责一件事：把「设置项 + 系统当前深浅色」翻译成一个布尔值
 * —— 这一次到底该用深色色板还是浅色色板。
 *
 * 为什么单独一个类：解析逻辑要在三个地方用（启动时、用户点设置时、系统 uiMode 变化时），
 * 而 AndroidManifest 里 MainActivity 声明了 {@code configChanges="…|uiMode|…"}，
 * 系统切深色**不会重建 Activity**，所以第三处必须自己算一次，不能指望框架。
 */
public final class Theme {

    /** 跟随系统（默认）。 */
    public static final String MODE_SYSTEM = "system";
    /** 始终浅色。 */
    public static final String MODE_LIGHT = "light";
    /** 始终深色。 */
    public static final String MODE_DARK = "dark";

    /** 未知/空值一律归一成「跟随系统」，老用户升级后不会突然被锁成某一档。 */
    public static String normalize(String mode) {
        if (MODE_LIGHT.equals(mode)) return MODE_LIGHT;
        if (MODE_DARK.equals(mode)) return MODE_DARK;
        return MODE_SYSTEM;
    }

    /** 系统此刻是不是深色（读 Configuration.uiMode 的 NIGHT 位）。 */
    public static boolean systemDark(Context c) {
        if (c == null) return false;
        int m = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 设置项 + 系统状态 → 这一刻用不用深色色板。 */
    public static boolean resolveDark(Context c, String mode) {
        switch (normalize(mode)) {
            case MODE_LIGHT: return false;
            case MODE_DARK:  return true;
            default:         return systemDark(c);
        }
    }

    /**
     * 「把 App 自己的 uiMode 摆到设置选的那一档」用的覆盖配置（只在 Activity 创建之前调用一次）。
     *
     * <h3>为什么需要它</h3>
     * 本 App 的 View 树全是手搓的，颜色来自 {@link Ui} 的字面量色板，所以界面本身能靠
     * {@link Ui#applyTheme(boolean)} 自己变深浅。但**系统提供的那些窗口/控件**不看我们：
     * AlertDialog 的默认底、Toast、下拉选择器、权限弹窗、系统栏、输入法的部分配色
     * 都按 {@code Configuration.uiMode} 的 night 位渲染。
     *
     * <p>清单里 MainActivity 只声明了 {@code configChanges="…|uiMode|…"}（旋转/切深色不重建
     * Activity，免得丢掉正在跑的对话），Activity 的 Configuration 就一直带着**系统的** night 位 ——
     * 于是「系统深色 + App 选浅色」时，手搓界面是浅的，系统件却是深色/被强制深色，
     * 表现就是用户报的「界面会有一部分显示为黑色」。
     *
     * <p>这里在 {@code attachBaseContext}（任何资源被取用之前）把 night 位改成 App 的选择，
     * 系统件就跟着 App 走了。
     *
     * <h3>为什么「跟随系统」返回 null</h3>
     * 跟随系统时必须**保留**系统的 night 位，否则系统切深色时 {@link #systemDark(Context)}
     * 读到的永远是我们覆盖后的值，{@code onConfigurationChanged} 里的自动跟随就失效了。
     *
     * @return 需要覆盖的 Configuration；{@code null} = 不覆盖（跟随系统档）
     */
    public static Configuration nightOverride(Context base, String mode) {
        if (base == null) return null;
        String m = normalize(mode);
        if (MODE_SYSTEM.equals(m)) return null;
        int want = MODE_DARK.equals(m) ? Configuration.UI_MODE_NIGHT_YES
                                       : Configuration.UI_MODE_NIGHT_NO;
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | want;
        return c;
    }

    /** 设置项的中文文案（设置页三项分段用）。 */
    public static String label(String mode) {
        switch (normalize(mode)) {
            case MODE_LIGHT: return "浅色";
            case MODE_DARK:  return "深色";
            default:         return "跟随系统";
        }
    }

    private Theme() { }
}
