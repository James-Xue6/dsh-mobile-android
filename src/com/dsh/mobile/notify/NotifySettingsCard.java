package com.dsh.mobile.notify;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dsh.mobile.Store;
import com.dsh.mobile.ui.Ui;

/**
 * 设置页里的「通知」分组（内容部分）。
 *
 * <p>为什么单独一个文件：设置页正被另一条线（iOS 风格 UI 重做）改它的视觉层，
 * 这里把「通知」这一组的全部控件与逻辑收在一处，设置页那边只留一行
 * {@code NotifySettingsCard.attach(...)} —— 冲突面最小，改视觉也动不到通知逻辑。
 *
 * <p>开关本身不经过宿主回调：它们的真身就是 {@link Store} 里的四个布尔，
 * 通知发出前由 {@link Notifier} 每次现读；唯一的副作用是「通知提醒 / 后台保持接收」
 * 需要立刻同步前台服务的起停（{@link KeepAliveService#sync}）。
 */
public final class NotifySettingsCard {

    private NotifySettingsCard() { }

    /** 往设置页的分组容器里铺满「通知」这一组的内容。 */
    public static void attach(LinearLayout box, Context ctx, Store store) {
        if (box == null || ctx == null || store == null) return;

        // ---- 后台可达性的开关先建出来：顶部自查行"点一下就打开"时要同步这一枚开关的外观
        Ui.Switch keep = new Ui.Switch(ctx);
        keep.setChecked(store.keepAlive());

        // ---- [收尾2] 三行自查（放在分组最顶部：用户"收不到提醒"时第一眼就该看出卡在哪一环）
        addSelfCheck(box, ctx, store, keep);

        box.addView(hint(ctx, "电脑上的会话跑完、或者有审批/提问等你决定时，手机会响你一下。"
                + "通知里只会写一句会话名，不会出现对话正文；锁屏上连会话名都不显示。"));
        box.addView(hint(ctx, "「进行中」是正在执行时的低优先级静默状态；正在看那条会话时不会打扰你。"));

        // ---- 总开关（关掉后一条通知都不发，App 照常用；前台服务也一起停）
        Ui.Switch master = new Ui.Switch(ctx);
        master.setChecked(store.notifyEnabled());
        box.addView(switchRow(ctx, "通知提醒", master, on -> {
            store.setNotifyEnabled(on);
            KeepAliveService.sync(ctx);          // 关掉通知就不再需要常驻服务
        }));

        // ---- 分级：仅需处理时提醒
        Ui.Switch onlyPending = new Ui.Switch(ctx);
        onlyPending.setChecked(store.notifyOnlyPending());
        box.addView(switchRow(ctx, "仅需处理时提醒", onlyPending,
                on -> store.setNotifyOnlyPending(on)));
        box.addView(hint(ctx, "打开后只管审批/提问这类必须你决定的；「任务完成」「进行中」都不再打扰。"));

        // ---- 隐私：通知里是否显示会话名
        Ui.Switch detail = new Ui.Switch(ctx);
        detail.setChecked(store.notifyShowDetail());
        box.addView(switchRow(ctx, "通知显示内容", detail,
                on -> store.setNotifyShowDetail(on)));
        box.addView(hint(ctx, "关掉后通知里只写「点开查看详情」，旁边有人也看不出你在跑什么。"
                + "无论开关如何，对话正文都不会进通知。"));

        // ---- 后台可达性：前台服务（开关本体在上面建，这里只挂行）
        box.addView(switchRow(ctx, "后台保持接收", keep, on -> {
            store.setKeepAlive(on);
            KeepAliveService.sync(ctx);
        }));
        box.addView(hint(ctx, "打开后通知栏会常驻一条「保持后台接收」（低优先级、不吵）。"
                + "这是「切到后台还收得到提醒」的前提：关掉它，Android 会挂起 App，"
                + "任务完成/待审批的提醒就可能收不到了。"));

        // ---- 权限（没给才显示）
        String permHint = Notifier.permissionHint(ctx);
        if (permHint != null) {
            TextView go = Ui.text(ctx, permHint, Ui.S_SUB, Ui.BRAND, false);
            go.setPadding(0, Ui.dp(ctx, 12), 0, 0);
            go.setClickable(true);
            go.setOnClickListener(v -> openSystemNotificationSettings(ctx));
            box.addView(go);
        }

        // ---- 自检入口：不打开系统设置也能确认"通知到底是通的"
        TextView test = Ui.secondaryButton(ctx, "发一条测试通知");
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.topMargin = Ui.dp(ctx, 12);
        test.setLayoutParams(tp);
        test.setOnClickListener(v -> Notifier.test(ctx, store));
        box.addView(test);
        box.addView(hint(ctx, "点上面的按钮应该立刻收到一条「测试通知」；收不到就说明系统还没放行"
                + "（通知权限、免打扰、通道被关），按上面的引导去系统设置里打开。"));
    }

    // ================================================================ [收尾2] 三行自查

    /**
     * 「收不到提醒」的三行自查 + 一行白话引导（分组顶部）。
     *
     * <p>为什么要有它：用户报的"收不到"有且只有三个真实原因 —— 系统通知权限被关、
     * 「需要处理」渠道被系统降级（不弹横幅）、后台保活被关/被 ROM 清理。
     * 三行按顺序写出来，异常的那一行**整行可点**，点一下直接落到能修它的那个系统页面，
     * 不用用户在设置里自己找。跳转只用系统公开的 Settings Intent，不需要新权限。
     *
     * <p>这一块在每次进入设置页时重建，所以读到的都是**当下实况**（不是缓存）。
     */
    private static void addSelfCheck(LinearLayout box, Context ctx, Store store, final Ui.Switch keepSwitch) {
        Notifier.init(ctx);   // 渠道必须先建好，否则下面读不到「需要处理」的重要性

        // ① 系统通知权限：没开的话后面两行再对也没用
        boolean allowed = Notifier.notificationsAllowed(ctx);
        View.OnClickListener fixPerm = allowed ? null : v -> openSystemNotificationSettings(ctx);
        statusRow(box, ctx,
                "系统通知权限：" + (allowed ? "已开启" : "手机不让发通知，你看不到任何提醒"),
                allowed, fixPerm);

        // ② 提醒方式 =「需要处理」渠道的重要性。低于 HIGH 就不会弹横幅/响铃 ——
        //    真机实测 dsh_pending 曾被系统降到 3(DEFAULT)，这正是"一条都收不到"的头号原因。
        int imp = Notifier.pendingChannelImportance(ctx);
        boolean canPop = imp >= NotificationManager.IMPORTANCE_HIGH;
        View.OnClickListener fixImp = canPop ? null : v -> openChannelSettings(ctx, Notifier.CH_PENDING);
        statusRow(box, ctx,
                "提醒方式：" + (canPop ? "有新任务会弹出来提醒你"
                        : "系统把它降成了「只在通知栏」——不会弹出来"),
                canPop, fixImp);

        // ③ 后台保活：关掉它 Android 就会挂起 App，切后台后收不到任何事件。
        //    未开时点一下**就地打开**（同时把常驻服务拉起来），不让用户再翻一个开关。
        final TextView[] keepRow = new TextView[1];
        boolean keep = store.keepAlive();
        View.OnClickListener fixKeep = keep ? null : v -> {
            store.setKeepAlive(true);
            KeepAliveService.sync(ctx);
            if (keepSwitch != null) keepSwitch.setChecked(true, true);   // 同步下方那枚开关的外观
            paintStatus(keepRow[0], "后台保活：已开启", true);
            v.setClickable(false);
        };
        keepRow[0] = statusRow(box, ctx,
                "后台保活：" + (keep ? "已开启" : "关掉 App 后就收不到提醒了"),
                keep, fixKeep);

        // ④ 最近一次发送的实况（Notifier 的进程级探针）：失败原因直接摆出来，不再"静默收不到"
        box.addView(hint(ctx, lastPostLine()));

        // ⑤ 白话引导：荣耀的后台清理是"收不到"的第二大原因，点这里进应用详情页
        TextView guide = hint(ctx, "荣耀手机会在后台清理 App：设置 → 应用 → DSH 掌上通 → "
                + "耗电管理 → 允许后台运行；并把电池优化设为不优化。点这里进应用详情。");
        guide.setTextColor(Ui.BRAND);
        guide.setClickable(true);
        guide.setOnClickListener(v -> openAppDetailsSettings(ctx));
        box.addView(guide);
    }

    /**
     * 一行自查状态：正常用次要色、不可点；异常用醒目色、整行可点（点一下去修）。
     * 返回那一行里的文字控件，调用方在动作后可**就地**改文案（不必重建整页）。
     */
    private static TextView statusRow(LinearLayout box, Context ctx, String text,
                                      boolean ok, View.OnClickListener tap) {
        LinearLayout row = Ui.row(ctx);
        row.setMinimumHeight(Ui.dp(ctx, 34));
        row.setPadding(0, Ui.dp(ctx, 6), 0, 0);
        TextView t = Ui.text(ctx, text, Ui.S_BODY, ok ? Ui.INK_SUB : Ui.WARN, false);
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);
        if (tap != null) {
            row.addView(Ui.chevron(ctx));
            row.setClickable(true);
            row.setOnClickListener(tap);
        }
        box.addView(row);
        return t;
    }

    /** 就地刷新一行自查的文案与配色。 */
    private static void paintStatus(TextView t, String text, boolean ok) {
        if (t == null) return;
        t.setText(text);
        t.setTextColor(ok ? Ui.INK_SUB : Ui.WARN);
    }

    /** 「最近一次提醒」那一行：有失败优先说失败，其次说时间，都没有就直说"今天还没发过"。 */
    private static String lastPostLine() {
        String err = Notifier.lastPostError();
        if (err != null && !err.isEmpty()) return "最近一次发送失败：" + err;
        long at = Notifier.lastPostAt();
        if (at <= 0) return "今天还没发出过任何提醒";
        return "最近一次提醒：" + new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                .format(new java.util.Date(at));
    }

    // ================================================================ 深链（都不需要新权限）

    /**
     * **渠道级**深链：直接落到「需要处理」这一条渠道的页面。
     *
     * <p>比应用通知总页精确：用户要改的正是这一条渠道的重要性/横幅/声音，
     * 落到总页他还得再点一层（很多人就在这里放弃了）。
     */
    public static void openChannelSettings(Context ctx, String channelId) {
        try {
            Intent i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.getPackageName());
            i.putExtra(Settings.EXTRA_CHANNEL_ID, channelId);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Throwable t) {
            // 个别 ROM 没有渠道页：退到应用通知总页
            openSystemNotificationSettings(ctx);
        }
    }

    /** 应用详情页：荣耀的「耗电管理 / 电池优化」入口就在这里。 */
    public static void openAppDetailsSettings(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(android.net.Uri.parse("package:" + ctx.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Throwable ignored) { }
    }

    /** iOS 开关行：左标题 + 右自绘开关，整行可点（与设置页其它开关同一套样式）。 */
    private static LinearLayout switchRow(Context ctx, String label, Ui.Switch sw,
                                          final Ui.Switch.OnChange onChange) {
        LinearLayout row = Ui.row(ctx);
        row.setMinimumHeight(Ui.dp(ctx, 44));
        row.setPadding(0, Ui.dp(ctx, 8), 0, 0);
        TextView t = Ui.text(ctx, label, Ui.S_BODY, Ui.INK, false);
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);
        row.addView(sw);
        row.setClickable(true);
        // 点整行 = 点开关：现读开关自己的状态取反，不缓存副本
        row.setOnClickListener(v -> {
            boolean next = !sw.isChecked();
            sw.setChecked(next, true);
            onChange.onChanged(next);
        });
        sw.setOnChange(onChange);
        return row;
    }

    private static TextView hint(Context ctx, String s) {
        TextView t = Ui.text(ctx, s, Ui.S_FOOT, Ui.INK_SUB, false);
        t.setPadding(0, Ui.dp(ctx, 5), 0, 0);
        return t;
    }

    /** 打开本 App 的系统通知设置页（用户可以在那里开权限 / 调通道重要性）。 */
    public static void openSystemNotificationSettings(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.getPackageName());
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Throwable t) {
            // 个别 ROM 没有这个页面：退到应用详情页
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(android.net.Uri.parse("package:" + ctx.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Throwable ignored) { }
        }
    }
}
