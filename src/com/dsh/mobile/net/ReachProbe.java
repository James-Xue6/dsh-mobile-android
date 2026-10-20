package com.dsh.mobile.net;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 设备页的「这台电脑现在开着没有」探测：只做一次 TCP 握手，不握手协议、不发令牌。
 *
 * <p>为什么需要它：App 进程里只有一条 WebSocket，同一时刻只能连着**一台**电脑。
 * 旧版把卡片徽标写成「在线/离线」，非当前设备一律「离线」—— 而它的真实含义只是
 * "我现在没连它"。用户两台电脑都开着，看到两张卡都写「离线」，自然以为 App 坏了。
 *
 * <p>纪律：
 * <ul>
 *   <li>**不读业务数据**：只 TCP connect 到 host:port 就关，连 HTTP/WS 都不发；</li>
 *   <li>**不碰令牌**：这一步发生在认证之前，用来回答"有没有人在听"；</li>
 *   <li>**有上限**：调用方必须传超时（建议 1.5s），并且只在设备页可见时跑。</li>
 * </ul>
 */
public final class ReachProbe {

    private ReachProbe() { }

    /** 没有可用地址（空 / 解析不出 host）。 */
    public static final int NO_ADDRESS = -1;
    /** 连不上（超时 / 拒绝 / 域名失效）。 */
    public static final int UNREACHABLE = 0;
    /** 端口有人在听（≈ 这台电脑的网关开着）。 */
    public static final int REACHABLE = 1;

    /**
     * 多选题版本：把候选地址**依次**试一遍，任一条通就算"这台电脑开着"。
     *
     * <p>为什么要多条：非当前设备的卡片上写的是"预测这次会走哪条"（自动档在 WiFi 下预测内网），
     * 但真正能不能连上可能得走公网。只探一条会把"内网不通但公网好好的电脑"判成离线。
     * 顺序 = 先按预测线路、再另一条；每条各自超时，最多两条。
     */
    public static int probeAny(String[] urls, int timeoutMs) {
        if (urls == null || urls.length == 0) return NO_ADDRESS;
        boolean anyAddress = false;
        for (String u : urls) {
            if (u == null || u.trim().isEmpty()) continue;
            anyAddress = true;
            if (probe(u, timeoutMs) == REACHABLE) return REACHABLE;
        }
        return anyAddress ? UNREACHABLE : NO_ADDRESS;
    }

    /**
     * @param url       ws:// / wss:// / http(s):// 任一形态（走 {@link LanAddress} 的同一套解析）
     * @param timeoutMs TCP 连接超时（毫秒）
     */
    public static int probe(String url, int timeoutMs) {
        String host = LanAddress.hostOf(url);
        if (host == null || host.trim().isEmpty()) return NO_ADDRESS;
        int port = defaultPort(url);
        String p = LanAddress.portOf(url);
        if (p != null && !p.isEmpty()) {
            try {
                port = Integer.parseInt(p);
            } catch (Throwable ignored) {
                return NO_ADDRESS;
            }
        }
        if (port <= 0 || port > 65535) return NO_ADDRESS;
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), Math.max(200, timeoutMs));
            return REACHABLE;
        } catch (Throwable t) {
            return UNREACHABLE;
        } finally {
            try { s.close(); } catch (Throwable ignored) { }
        }
    }

    private static int defaultPort(String url) {
        String u = url == null ? "" : url.trim().toLowerCase(java.util.Locale.ROOT);
        if (u.startsWith("wss://") || u.startsWith("https://")) return 443;
        return 80;
    }
}
