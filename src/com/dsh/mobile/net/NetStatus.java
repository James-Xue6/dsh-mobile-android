package com.dsh.mobile.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * 「现在是不是在用 WiFi」的判定与缓存。
 *
 * <p>用户定的规则（2026-10-02）：
 * <pre>
 *   已连 WiFi            -> 走内网
 *   移动数据 / 未连 WiFi  -> 走公网
 * </pre>
 * 所以「是不是 WiFi」是自动选路唯一的网络输入。判据用系统自己的权威口径：
 * {@code ConnectivityManager.getActiveNetwork()} + {@code NetworkCapabilities.hasTransport(TRANSPORT_WIFI)}，
 * 不用 {@code NetworkInterface} 名字猜（老写法按 wlan 前缀，USB 网卡 / 热点共享时报不准）。
 *
 * <p>权限：{@code ACCESS_NETWORK_STATE}（普通权限，安装即授予、不弹窗；AndroidManifest 里已有）。
 * 取不到 / 没权限 / 老系统一律返回 false（= 按「不是 WiFi」处理，走公网），与规则一致，
 * 绝不因为判不出来就把用户钉在连不上的内网地址上。
 *
 * <p>缓存理由：{@code onCapabilitiesChanged} 触发极频繁（信号强弱、带宽变化都会来），
 * 而「选哪条线路」在每次重连、每张卡片重画时都要问一次，所以结果缓存在进程里，
 * 由 {@link com.dsh.mobile.MainActivity} 的 NetworkCallback 负责刷新。
 */
public final class NetStatus {

    private static volatile boolean known = false;
    private static volatile boolean wifi = false;
    private static volatile boolean online = false;

    private NetStatus() { }

    /** 是否已经有过一次判定（没判定过时 {@link #wifi()} 恒为 false）。 */
    public static boolean known() { return known; }

    /** 最近一次判定：当前默认网络是不是 WiFi。**没判定过时返回 false（按非 WiFi 处理）**。 */
    public static boolean wifi() { return known && wifi; }

    /**
     * 当前有没有可用的默认网络（默认网络存在且带 INTERNET 能力）。
     * 只用于把「未连 WiFi」说清是移动数据还是干脆没网 —— 没网时说成「移动数据」会让人困惑。
     */
    public static boolean online() { return online; }

    /** 从 NetworkCallback 拿到的能力直接更新（免一次系统查询；caps 是默认网络的）。 */
    public static void set(boolean onWifi, boolean hasInternet) {
        wifi = onWifi;
        online = hasInternet;
        known = true;
    }

    /** 直接问系统一次（不写缓存；同时刷新 {@link #online()}）。 */
    public static boolean query(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getApplicationContext()
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) { online = false; return false; }
            Network n = cm.getActiveNetwork();
            if (n == null) { online = false; return false; }
            NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            if (caps == null) { online = false; return false; }
            online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Throwable t) {
            online = false;
            return false;
        }
    }

    /** 重新查询并更新缓存；返回「WiFi / 非 WiFi 这个种类是否变了」（变了就要清掉内网退避、重画状态）。 */
    public static boolean refresh(Context ctx) {
        boolean before = wifi;
        set(query(ctx), online);
        return before != wifi;
    }
}
