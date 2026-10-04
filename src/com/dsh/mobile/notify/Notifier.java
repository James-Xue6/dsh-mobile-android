package com.dsh.mobile.notify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.dsh.mobile.R;
import com.dsh.mobile.Store;

/**
 * 通知中心：三个渠道 + 三类通知 + 「点进去就能选」的深链。
 *
 * <p>四件事对应：
 * <ol>
 *   <li><b>任务完成</b> —— 会话的回合结束（turn/end 或会话列表里 running 由真变假）；</li>
 *   <li><b>需要处理</b> —— 网关推来审批/提问（高优先级、可弹横幅、有声音/震动）；</li>
 *   <li><b>进行中</b> —— 会话在跑，且人不在看它（低优先级、静默、同一会话更新同一条）；</li>
 *   <li><b>后台可达</b> —— {@link KeepAliveService} 用同一条「进行中」渠道挂常驻通知。</li>
 * </ol>
 *
 * <h3>三条硬规则（踩过的坑）</h3>
 * <ul>
 *   <li><b>正文绝不出现在通知里</b>：通知只给一句「会话名 / 点开查看详情」。
 *       锁屏上不该出现用户正在跟 Agent 聊什么 —— 需要更严就关掉设置里的「通知显示内容」，
 *       那时连会话标题都不写。无论哪个档，消息正文都不会进通知
 *       （也顺带绕开了「通知文本被别的 App 读取」这条面）。</li>
 *   <li><b>正在看那个会话就不打扰</b>：只在 {@code !(App 在前台 && 通知的会话 == 正在看的会话)}
 *       时才发。进行中（低优先级）更严：只有 App 不在前台才发。</li>
 *   <li><b>通知权限是系统层的运行时权限</b>（Android 13+）：用户没给就一条都发不出去，
 *       这里每一处都先查 {@link #notificationsAllowed}，查不到就安静地降级（App 照常用）。</li>
 * </ul>
 *
 * <p>通知 id 按「类型 + 会话」确定性生成（固定 id）：同一会话的同类通知反复发只是更新
 * 同一条，不会在通知栏刷屏。
 */
public final class Notifier {

    // ---------------------------------------------------------------- 渠道
    /** 「需要处理」：审批 / 提问。高优先级 —— 会弹横幅（heads-up）、有声音/震动。 */
    // [P0] 换新渠道 id：Android 语义下"渠道重要性创建后只能由用户改"，
    // 真机实测 dsh_pending 的 mImportance 已被降为 3(DEFAULT) → 不弹横幅（用户看不到）。
    // 新 id 会以 IMPORTANCE_HIGH 重新创建，横幅/响铃恢复；旧渠道留着不影响。
    public static final String CH_PENDING = "dsh_pending_v2";
    /** 常驻"保持后台接收"用：独立渠道，避免用户关掉"进行中"时连带失去后台保活。 */
    public static final String CH_SERVICE = "dsh_service";
    /** 「任务完成」：回合结束 / 任务跑完。默认优先级 —— 有声音但不弹横幅。 */
    public static final String CH_DONE = "dsh_done";
    /** 「进行中」：正在执行的状态 + 前台服务常驻通知。低优先级、静默。 */
    public static final String CH_RUNNING = "dsh_running";

    /** 前台服务常驻通知的固定 id。 */
    public static final int ID_SERVICE = 9001;

    // 三类通知的 id 基数：彼此拉开，避免同一会话的三类通知互相顶掉
    private static final int ID_DONE_BASE = 9100;
    private static final int ID_PENDING_BASE = 9400;
    private static final int ID_RUNNING_BASE = 9700;

    /**
     * 深链 extra：点通知要进入的会话、以及要滚到的那张卡（审批/提问卡的 key）。
     *
     * <p>这两个值只活在内存与 Intent 里：**不落盘、不进任何被跟踪的文件、不进日志**
     * （会话 id 与卡 key 都属于"用户此刻在干什么"，见仓库的隐私约定）。
     */
    public static final String EXTRA_SESSION = "dsh_notify_session";
    public static final String EXTRA_KEY = "dsh_notify_item_key";

    // ---------------------------------------------------------------- 状态（进程级、不落盘）
    private static boolean channelsReady = false;
    /** App 是否在前台可见（MainActivity 的 onStart/onStop 维护）。 */
    private static boolean foreground = false;
    /** 人此刻正在看的会话 id（空 = 没在看任何会话）。 */
    private static String viewedSession = "";
    /** 网关是否 READY（决定常驻通知写"已连接"还是"等待连接"）。 */
    private static boolean connected = false;

    private Notifier() { }

    // ---------------------------------------------------------------- 渠道与权限

    /**
     * 建三个渠道（幂等）。Android 8+ 没有渠道就发不出通知。
     *
     * 注意：createNotificationChannel 对**已存在**的同 id 渠道只更新 App 侧属性，
     * 用户改过的「重要性/声音」不会被我们覆盖 —— 这是系统语义，也正是我们想要的
     * （用户把「需要处理」调静音了，App 不该自己改回去）。
     */
    public static void init(Context ctx) {
        if (channelsReady) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel pending = new NotificationChannel(
                CH_PENDING, "需要处理", NotificationManager.IMPORTANCE_HIGH);
        pending.setDescription("有审批或提问等你决定，会响铃并可弹横幅");
        pending.enableVibration(true);
        pending.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        NotificationChannel done = new NotificationChannel(
                CH_DONE, "任务完成", NotificationManager.IMPORTANCE_DEFAULT);
        done.setDescription("一个回合 / 一项任务跑完了");
        done.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        // 进行中：LOW 本身就是"不出声、不弹横幅"，这里连震动也显式关掉
        NotificationChannel running = new NotificationChannel(
                CH_RUNNING, "进行中", NotificationManager.IMPORTANCE_LOW);
        running.setDescription("正在执行的状态，以及保持后台接收的常驻通知（静默）");
        running.setSound(null, null);
        running.enableVibration(false);

        nm.createNotificationChannel(pending);
        nm.createNotificationChannel(new NotificationChannel(
                CH_SERVICE, "后台保持接收", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(done);
        nm.createNotificationChannel(running);
        channelsReady = true;
    }

    /**
     * 系统层面能不能发通知：Android 13+ 要看 POST_NOTIFICATIONS 运行时权限，
     * 低版本看用户在系统设置里有没有把本 App 的通知关掉。
     *
     * 这是**每一条通知**发之前的唯一判据（被拒时 App 一切照常，只是安静）。
     */
    public static boolean notificationsAllowed(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.areNotificationsEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 供设置页显示：「系统通知权限没开」时的引导文案（null = 一切正常）。 */
    public static String permissionHint(Context ctx) {
        return notificationsAllowed(ctx)
                ? null
                : "系统通知权限未开启，点这里去系统设置打开（不打开就没有任何提醒）";
    }

    // ---------------------------------------------------------------- 宿主传进来的状态

    /** App 前台可见性（MainActivity 的 onStart / onStop 调用）。 */
    public static void setForeground(Context ctx, boolean value) {
        foreground = value;
        if (value) {
            // 回前台 = 用户就在 App 里，通知栏里的「进行中 / 任务完成」已经没意义了
            clearRunning(ctx);
        }
    }

    /** 人此刻在看哪条会话（MainActivity 切会话时调用；空串 = 设备页/设置页看别的）。 */
    public static void setViewedSession(Context ctx, String sessionId) {
        viewedSession = sessionId == null ? "" : sessionId;
    }

    public static boolean isForeground() { return foreground; }

    /** 网关 READY 状态变化：常驻通知的文案与前台服务的开关都跟着它走。 */
    public static void onGatewayState(Context ctx, boolean ready) {
        connected = ready;
        KeepAliveService.sync(ctx);
    }

    public static boolean isConnected() { return connected; }

    // ---------------------------------------------------------------- 三类通知

    /**
     * 「需要处理」（高优先级）：审批 / 提问。
     *
     * @param sessionId  哪条会话在等（点通知就进它）
     * @param itemKey    那张卡的 key（approval:xxx / question:xxx），点进去滚到它
     * @param sessionName 通知里显示的会话名（null/空 用中性文案）
     */
    public static void pending(Context ctx, Store store, String sessionId,
                               String itemKey, String sessionName, boolean question) {
        if (!canPost(ctx, store, sessionId, true)) return;
        init(ctx);
        int id = idOf(ID_PENDING_BASE, sessionId);
        String title = question ? "有提问等你回答" : "有操作等你批准";
        String text = textOf(store, sessionName, "点开即可选择");
        // 同一条审批/提问可能被网关重放（重连、重新订阅都会补推一次）。
        // 这张卡已经响过一次就不再响第二次 —— 但通知本身仍然刷新/保留（不然用户会以为它没了）。
        String alertKey = sessionId + "|" + itemKey;
        boolean alreadyAlerted = !alertedPending.add(alertKey);
        post(ctx, id, CH_PENDING, title, text, PUBLIC_LINE,
                tap(ctx, sessionId, itemKey, id), false, true, alreadyAlerted);
    }

    /**
     * 「任务完成」：一个回合结束。
     *
     * @param sessionId 哪条会话跑完了
     */
    public static void done(Context ctx, Store store, String sessionId, String sessionName) {
        if (!canPost(ctx, store, sessionId, false)) return;
        init(ctx);
        int id = idOf(ID_DONE_BASE, sessionId);
        String text = textOf(store, sessionName, "点开查看结果");
        // 完成通知不设 onlyAlertOnce：同一个会话再跑完一次是**新的一次完成**，该响就响
        post(ctx, id, CH_DONE, "任务完成", text, PUBLIC_LINE,
                tap(ctx, sessionId, "", id), false, true, false);
    }

    /**
     * 「进行中」（低优先级、静默）：会话在跑，人不在看它。
     *
     * 只有 App **不在前台**时才发（否则用户正刷着手机，通知栏一直挂一条没意义的状态）。
     * 同一会话反复发只是更新同一条（id 固定），不会刷屏。
     */
    public static void running(Context ctx, Store store, String sessionId, String sessionName) {
        if (foreground) return;                      // 人在 App 里：不打扰
        if (!canPost(ctx, store, sessionId, false)) return;
        init(ctx);
        int id = idOf(ID_RUNNING_BASE, sessionId);
        String text = textOf(store, sessionName, "正在执行");
        runningPosted.add(sessionId);
        post(ctx, id, CH_RUNNING, "正在执行", text, null,
                tap(ctx, sessionId, "", id), true, false, true);
    }

    /** 撤掉某条会话的「进行中」通知（回合结束 / 回前台 / 断线时）。 */
    public static void clearRunning(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        for (String sid : runningPosted) nm.cancel(idOf(ID_RUNNING_BASE, sid));
        runningPosted.clear();
    }

    /** 撤掉某条会话的全部临时通知（用户已经进这条会话了，通知栏就不该再留着它）。 */
    public static void clearSession(Context ctx, String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.cancel(idOf(ID_DONE_BASE, sessionId));
        nm.cancel(idOf(ID_PENDING_BASE, sessionId));
        nm.cancel(idOf(ID_RUNNING_BASE, sessionId));
        runningPosted.remove(sessionId);
        // 这张卡既然已经撤了，下次真是"新的待处理"时应该重新响一次
        String prefix = sessionId + "|";
        java.util.Iterator<String> it = alertedPending.iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) it.remove();
        }
    }

    /** 设置页的「发一条测试通知」：验证渠道/权限/深链是否真的通了。 */
    public static void test(Context ctx, Store store) {
        init(ctx);
        int id = ID_PENDING_BASE + 999;
        post(ctx, id, CH_PENDING, "测试通知",
                "能看到这条、点一下能进 App，就说明通知是通的",
                null, tap(ctx, "", "", id), false, true, false);
    }

    /** 常驻通知（前台服务用，见 KeepAliveService）：低优先级、不可划掉。 */
    public static Notification serviceNotification(Context ctx) {
        init(ctx);
        String text = connected ? "已连接电脑 · 保持后台接收" : "等待连接 · 保持后台接收";
        return new Notification.Builder(ctx, CH_RUNNING)
                .setSmallIcon(R.drawable.ic_notify_dsh)
                .setContentTitle("DSH 掌上通")
                .setContentText(text)
                .setContentIntent(tap(ctx, viewedSession, "", ID_SERVICE))
                .setOngoing(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
    }

    // ---------------------------------------------------------------- 内部

    /** 已经发出去的「进行中」通知对应的会话（回前台 / 回合结束时按它精确撤销）。 */
    private static final java.util.Set<String> runningPosted = new java.util.HashSet<>();

    /**
     * 已经**响过**的待处理卡（sessionId|itemKey）。
     *
     * 网关会重放待处理交互（重连、重新订阅都补推一次），同一张卡不该每次都响铃/弹横幅；
     * 但通知本身仍然刷新（{@code setOnlyAlertOnce}），用户不会觉得"卡没了"。
     * 卡片被处理掉、或用户进了那条会话，就从这里移除 —— 下次新的待处理应重新响。
     */
    private static final java.util.Set<String> alertedPending = new java.util.HashSet<>();

    /**
     * 这一条该不该发。
     *
     * @param transactional true = 需要处理（审批/提问）：不受「仅需处理时提醒」影响，
     *                      但仍然遵守「正在看这条会话就不打扰」。
     */
    private static boolean canPost(Context ctx, Store store, String sessionId, boolean transactional) {
        if (store == null || !store.notifyEnabled()) return false;
        if (!notificationsAllowed(ctx)) return false;
        if (!transactional && store.notifyOnlyPending()) return false;
        // 人正在看这条会话 → 不发（卡片就摆在眼前，通知是纯打扰）
        // **提问/审批是高优先级**：即使用户正看着这条会话也要弹通知栏 ——
        // 用户实测反馈："我就停在这一条对话里，是靠通知才知道有提问的"。
        // 只有低优先级的静默通知（进行中等）才做"人在看就不打扰"的抑制。
        if (transactional) return true;
        return !(foreground && sessionId != null && sessionId.equals(viewedSession));
    }

    /** 通知正文：详细档给会话名（一句摘要），隐私档只给一句概括。正文永远不进通知。 */
    private static String textOf(Store store, String sessionName, String fallback) {
        if (store != null && store.notifyShowDetail()
                && sessionName != null && !sessionName.trim().isEmpty()) {
            return sessionName.trim();
        }
        return fallback;
    }

    /**
     * 锁屏（public version）上的那一行：**永远是中性文案**。
     *
     * 即使用户开着「通知显示内容」，锁屏上也不摆会话标题 —— 手机放桌上时
     * 通知栏内容是谁都能看见的，而会话标题往往就是用户让 Agent 干的那件事。
     */
    private static final String PUBLIC_LINE = "点开查看详情";

    private static void post(Context ctx, int id, String channel, String title, String text,
                             String publicText, PendingIntent pi, boolean ongoing, boolean autoCancel,
                             boolean onlyAlertOnce) {
        try {
            Notification.Builder b = new Notification.Builder(ctx, channel)
                    .setSmallIcon(R.drawable.ic_notify_dsh)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(pi)
                    .setAutoCancel(autoCancel)
                    .setOngoing(ongoing)
                    .setOnlyAlertOnce(onlyAlertOnce)
                    .setVisibility(Notification.VISIBILITY_PRIVATE);
            if (publicText != null) {
                b.setPublicVersion(new Notification.Builder(ctx, channel)
                        .setSmallIcon(R.drawable.ic_notify_dsh)
                        .setContentTitle(title)
                        .setContentText(publicText)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .build());
            }
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(id, b.build());
        } catch (Throwable ignored) {
            // 通知失败绝不影响主流程（例如个别 ROM 把渠道禁了 / 图标资源异常）
        }
    }

    /**
     * 点一下通知要干的事：打开 MainActivity，并告诉它「进哪条会话、滚到哪张卡」。
     *
     * requestCode 用通知 id：PendingIntent 的"是不是同一个"只看 requestCode + Intent
     * 结构，**不看 extra**；不同通知共用 0 号 requestCode 会让后一条静默复用前一条的
     * extra（点进去跑到别的会话），所以必须按通知 id 分开。
     */
    private static PendingIntent tap(Context ctx, String sessionId, String itemKey, int id) {
        Intent i = new Intent(ctx, com.dsh.mobile.MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (sessionId != null && !sessionId.isEmpty()) i.putExtra(EXTRA_SESSION, sessionId);
        if (itemKey != null && !itemKey.isEmpty()) i.putExtra(EXTRA_KEY, itemKey);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        try {
            return PendingIntent.getActivity(ctx, id, i, flags | PendingIntent.FLAG_IMMUTABLE);
        } catch (Throwable t) {
            return PendingIntent.getActivity(ctx, id, i, flags);
        }
    }

    /** 类型基数 + 会话哈希 → 固定 id（同一会话的同类通知永远是同一条）。 */
    private static int idOf(int base, String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return base;
        int h = sessionId.hashCode() & 0x7FFFFFFF;
        return base + (h % 200);
    }
}
