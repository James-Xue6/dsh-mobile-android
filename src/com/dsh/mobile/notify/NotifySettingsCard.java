package com.dsh.mobile.notify;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
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

        // ---- 后台可达性：前台服务
        Ui.Switch keep = new Ui.Switch(ctx);
        keep.setChecked(store.keepAlive());
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
            TextView go = Ui.text(ctx, permHint, 13f, Ui.BRAND, false);
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
        TextView t = Ui.text(ctx, s, 12.5f, Ui.INK_SUB, false);
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
