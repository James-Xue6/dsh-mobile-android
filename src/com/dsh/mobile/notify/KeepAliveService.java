package com.dsh.mobile.notify;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import com.dsh.mobile.MainActivity;
import com.dsh.mobile.Store;
import com.dsh.mobile.net.GatewayClient;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 后台保持接收（前台服务）。
 *
 * <h3>为什么必须有它</h3>
 * App 一退到后台，Android 会把这台手机的 App 进程当成"可回收 + 网络可挂起"来处理：
 * 系统看不到你在跑什么，就会冻结进程、掐掉套接字。而本 App 的价值正是「电脑上的会话
 * 跑完了 / 有审批等着你 → 手机提醒你」—— 没有常驻前台服务，事件根本收不到，
 * 通知自然发不出来（这就是"切后台就再也收不到提醒"的根因）。
 *
 * <h3>它做什么</h3>
 * ① 挂一条**低优先级、静默**的常驻通知说明"我正在后台替你收消息"；
 * ② [M2] **没有界面时自己把网关连接建起来**（见 {@link #ensureGateway()}）——
 *    否则进程被系统回收后由 START_STICKY 拉起来的服务里没有任何连接，
 *    后台就永久静默了（连接原来只由 MainActivity.onCreate 创建）。
 *
 * <h3>前台服务类型（Android 14+ 的硬要求）</h3>
 * 清单里声明了 {@code connectedDevice|specialUse}：
 *   - connectedDevice 是语义上最贴的一个（连到用户自己的电脑）；
 *   - 但它在 Android 14+ 有运行时前置条件（蓝牙/ NFC/USB 之类），本 App 没有那些权限，
 *     系统可能直接拒绝 → 于是用 specialUse 兜底（它没有前置条件，只是需要清单里的
 *     PROPERTY_SPECIAL_USE_FGS_SUBTYPE 说明用途）。
 * 三种调用逐级降级、全程 try/catch：**宁可没有常驻通知，也绝不让 App 崩在启动服务上**。
 */
public final class KeepAliveService extends Service {

    private static final String ACTION_START = "com.dsh.mobile.notify.action.START";
    private static final String ACTION_STOP = "com.dsh.mobile.notify.action.STOP";

    /** 进程级：服务此刻是否在跑（用来避免重复 start / 无谓 stop）。 */
    private static volatile boolean running = false;

    /**
     * 按当前设置与连接状态同步：该开就开，该关就关。
     * 调用点 = 设置页开关、连接状态变化（{@link Notifier#onGatewayState}）。
     */
    public static void sync(Context ctx) {
        if (ctx == null) return;
        boolean want;
        try {
            Store store = new Store(ctx);
            // 通知总开关关掉后，常驻服务只是白占一条通知栏：一并停掉
            // [M3] 原判据含 Notifier.isConnected() → 连接一进非 READY 就 stopService，进程随即被
        // 冻结；恢复时 Android 12+ 禁止后台 startForegroundService（异常被吞）→ FGS 永久消失、
        // 后台彻底收不到提醒（测试工程师实测结论 A，是"偶发成功、多数失败"的主因）。
        // 改为只看"用户是否希望后台接收"：断线期间保持常驻（文案走"等待连接/正在重连"分支）。
        want = store.keepAlive() && store.notifyEnabled();
        } catch (Throwable t) {
            want = false;
        }
        if (want) start(ctx);
        else stop(ctx);
    }

    /** 启动（已在跑就只刷新一次常驻通知的文案）。 */
    public static void start(Context ctx) {
        if (ctx == null) return;
        try {
            Intent i = new Intent(ctx, KeepAliveService.class).setAction(ACTION_START);
            // minSdk 26，startForegroundService 一直可用；从后台调会被系统拒绝（Android 12+），
            // 那种情况下抛异常，我们安静放弃 —— 用户回到 App 时还会再 sync 一次。
            ctx.startForegroundService(i);
        } catch (Throwable ignored) { }
    }

    /** 停止：断开连接 / 用户关掉"后台保持接收"时调用。 */
    public static void stop(Context ctx) {
        if (ctx == null) return;
        try {
            ctx.stopService(new Intent(ctx, KeepAliveService.class));
        } catch (Throwable ignored) { }
    }

    public static boolean isRunning() { return running; }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            running = false;
            stopSelf();
            return START_NOT_STICKY;
        }
        Notification n = Notifier.serviceNotification(this);
        if (!foreground(n)) {
            // 三种类型都失败（极老/极严的 ROM）：干脆不挂常驻通知，别让服务空转
            running = false;
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        // [M2] 服务起来之后（无论是不是被 START_STICKY 拉起来的）都确认一次连接：
        // 没有界面时这是唯一的连接发起者。
        ensureGateway();
        // START_STICKY：被系统回收后自动重启（onStartCommand 拿到 null intent，按启动处理）
        return START_STICKY;
    }

    // ================================================================ [M2] 无界面时的连接兜底

    /**
     * 后台监听者：没有界面时接管「连接状态」与「待处理交互」。
     *
     * <p>刻意做成 **static + application context**：监听者被装进进程级 GatewayClient，
     * 一旦捕获 Service 实例就会把它钉在进程里（Service 明明已经销毁）。
     */
    private static GatewayClient.Listener BG_LISTENER;

    /**
     * 后台监听者：只做两件事 ——
     * <ol>
     *   <li>连接状态 → 刷新常驻通知的文案（"已连接电脑" / "等待连接"）；</li>
     *   <li>提问 / 审批 → 直接弹「需要处理」高优先级通知。
     *       界面不在时它的监听者已被解绑（MainActivity.onDestroy），这里就是唯一看得到
     *       这两个帧的人（另一条兜底是 GatewayClient.bgInteractionHook，两者不会同时生效：
     *       监听到位就走监听者，监听者缺席才走钩子）。</li>
     * </ol>
     * 其余帧（会话列表 / 历史 / 流式）一律丢弃：没有界面，没人消费它们。
     */
    private static GatewayClient.Listener bgListener(final Context appCtx) {
        if (BG_LISTENER != null) return BG_LISTENER;
        BG_LISTENER = new GatewayClient.Listener() {
            @Override public void onState(GatewayClient.State state, String detail) {
                // connected 标志 + 常驻通知文案都跟着走（sync 内部是幂等的）
                Notifier.onGatewayState(appCtx, state == GatewayClient.State.READY);
            }
            @Override public void onApprovalRequested(JSONObject frame) { pending(appCtx, frame, false); }
            @Override public void onQuestionRequested(JSONObject frame) { pending(appCtx, frame, true); }

            // ---- 以下都与"没有界面"无关：安静丢弃
            @Override public void onHello(JSONObject hello) { }
            @Override public void onPaired(JSONObject paired) { }
            @Override public void onSessions(JSONArray items, JSONObject raw) { }
            @Override public void onHistory(String sessionId, JSONArray events, JSONObject meta) { }
            @Override public void onSnapshot(String sessionId, JSONObject snapshot) { }
            @Override public void onAssistantStream(JSONObject frame) { }
            @Override public void onEvent(String sessionId, JSONObject event, Object seq, Object time) { }
            @Override public void onInteractionResolved(JSONObject frame) { }
            @Override public void onSent(String sessionId, JSONObject raw) { }
            @Override public void onProtocolError(String code, String message, String requestType, String sessionId) { }
            @Override public void onOther(String kind, JSONObject frame) { }
            @Override public void onAttachment(String sessionId, String attachmentId, String mediaType, String base64) { }
            @Override public void onDownload(String kind, JSONObject frame) { }
        };
        return BG_LISTENER;
    }

    /** 后台收到提问/审批 → 弹「需要处理」通知。key 的算法与界面侧完全一致，点进去才滚得到那张卡。 */
    private static void pending(Context appCtx, JSONObject frame, boolean question) {
        try {
            if (frame == null) return;
            String sid = frame.optString("sessionId", "");
            if (sid.isEmpty()) return;
            // [P1 修复] key 不再就地拼：与界面建档、历史事件、深链共用 Notifier 的唯一实现
            //（旧写法 approvalId="" 时会算出与界面不同的 key）。
            String key = question ? Notifier.questionKey(frame) : Notifier.approvalKey(frame);
            // 会话名取不到（没有界面就没有会话列表）：交给 Notifier 用中性文案，正文绝不猜。
            Notifier.pending(appCtx, new Store(appCtx), sid, key, "", question);
        } catch (Throwable ignored) { }
    }

    /**
     * [M2] 没有界面时把网关连接建起来 / 恢复回来。
     *
     * <p>纪律三条：
     *   - **有界面就不插手**：连接由 Activity 负责（{@link MainActivity#hasLiveActivity()}）；
     *   - **不重复创建、不打断退避**：只复用 {@link MainActivity#sharedGateway} 那个进程级实例，
     *     且 {@code wantConnected()} 为真（正在连 / 已连上 / 正在退避重试）时直接返回；
     *   - **不重复连接参数**：地址与令牌的取法走 {@link MainActivity#connectFromStore}，
     *     与界面里的 {@code connectNow()} 是同一个方法。
     *
     * <p>线程：{@code onStartCommand} 本来就在主线程，GatewayClient 的状态回调也投递到主线程，
     * 所以这里同步调用即可，不需要额外的 Handler。
     */
    private void ensureGateway() {
        try {
            // [P1 修复] 灌入应用级 Context：没有监听者时 GatewayClient.setState 靠它把
            // 连接状态转给 Notifier（常驻通知文案 / 前台服务开关）。
            GatewayClient.setAppContext(getApplicationContext());
            if (MainActivity.hasLiveActivity()) return;
            GatewayClient g = MainActivity.sharedGateway(bgListener(getApplicationContext()), false);
            if (g.wantConnected()) return;
            MainActivity.connectFromStore(getApplicationContext(), g);
        } catch (Throwable ignored) { }
    }

    /** 逐级降级地调 startForeground；返回是否成功。 */
    private boolean foreground(Notification n) {
        // ① 语义最贴的 connectedDevice（Android 10+ 才有这个类型常量）；
        //    但它带运行时前置条件，本 App 没那些权限，Android 14+ 上可能被拒。
        if (Build.VERSION.SDK_INT >= 29
                && tryForeground(n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)) {
            return true;
        }
        // ② specialUse 兜底：没有前置条件，清单里已按系统要求写清用途（PROPERTY_SPECIAL_USE_FGS_SUBTYPE）。
        if (Build.VERSION.SDK_INT >= 34
                && tryForeground(n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)) {
            return true;
        }
        // ③ 最后退到不带类型的两参调用（Android 9 及以下本来就不认类型）
        try {
            startForeground(Notifier.ID_SERVICE, n);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean tryForeground(Notification n, int type) {
        try {
            startForeground(Notifier.ID_SERVICE, n, type);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        try {
            stopForeground(true);
        } catch (Throwable ignored) { }
        super.onDestroy();
    }
}
