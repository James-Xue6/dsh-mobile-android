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
