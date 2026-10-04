package com.dsh.mobile.notify;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import com.dsh.mobile.Store;

/**
 * 后台保持接收（前台服务）。
 *
 * <h3>为什么必须有它</h3>
 * App 一退到后台，Android 会把这台手机的 App 进程当成"可回收 + 网络可挂起"来处理：
 * 系统看不到你在跑什么，就会冻结进程、掐掉套接字。而本 App 的价值正是「电脑上的会话
 * 跑完了 / 有审批等着你 → 手机提醒你」—— 没有常驻前台服务，事件根本收不到，
 * 通知自然发不出来（这就是"切后台就再也收不到提醒"的根因）。
 *
 * <h3>它不做什么</h3>
 * 只挂一条**低优先级、静默**的常驻通知说明"我正在后台替你收消息"，自己**不碰网络、
 * 不碰会话**（连接仍由 MainActivity 里那个进程级 GatewayClient 负责）。这样即使
 * 服务被系统回收，也只是失去"后台提醒"这项能力，不会影响已有的会话状态。
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
        // START_STICKY：被系统回收后自动重启（onStartCommand 拿到 null intent，按启动处理）
        return START_STICKY;
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
