package com.dsh.mobile.net;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 局域网里「哪台电脑开着 DSH 掌上通面板」的主动发现（方案 B 的治本那一步）。
 *
 * <p>为什么需要它：DSH 重启后 Cloudflare 隧道地址会变，**电脑的局域网 IP 也可能变**
 * （换了网口 / DHCP 续租到别的地址）。此时 App 里存的内网地址和公网地址**双双失效**，
 * 而旧实现只在「已经能用内网连上」时才去刷新公网地址（见 {@code maybeRefreshWanUrl}）——
 * 于是永远刷不了，用户只能手动去电脑面板首页点「重新连接」。
 *
 * <p>本类只做一件事：**扫当前子网，找出哪些 IP 上跑着 DSH 掌上通面板**。
 * 找到之后由调用方（{@code MainActivity}）去面板拉最新地址（{@code /public-url}）并重连。
 *
 * <h3>为什么不在这类里发设备令牌</h3>
 * 探测用的是面板的**首页** {@code http://<ip>:8099/}，它不含任何凭证、也不需要令牌。
 * 令牌只在「确认对方确实是 DSH 面板」之后、由调用方单独发一次（见 MainActivity），
 * 绝不对着 254 个陌生 IP 广播设备令牌。
 *
 * <h3>性能纪律（用户要求：别把手机卡住）</h3>
 * <ul>
 *   <li>并发上限 {@value #DEFAULT_CONCURRENCY}；每个请求连接/读取各 {@value #DEFAULT_TIMEOUT_MS}ms；</li>
 *   <li>总预算 {@value #DEFAULT_BUDGET_MS}ms（到点就停，剩下的 IP 不扫）；</li>
 *   <li>只扫**自己所在的子网**（前缀 &lt; 24 时收窄成自己那一段 /24），最多 {@value #MAX_HOSTS} 个地址；</li>
 *   <li>全部线程都是 daemon，随时可 {@code cancel}，绝不在后台常驻（调用方只在前台/用户点重试时跑）。</li>
 * </ul>
 *
 * <p>纯逻辑 + 标准库 HTTP，不依赖任何 Android API（除 {@link LanAddress} 的纯判据），
 * 因此可以在 JVM 上直接断言（见 {@code harness/src/LanScanTest.java}）。
 */
public final class LanScan {

    /** 电脑端「移动设备」面板在局域网里的端口（pc-plugin/dsh-mobile-access 的 APP_PORT）。 */
    public static final int PANEL_PORT = 8099;

    /**
     * 面板首页里的身份标记。命中即认为「这个 IP 上跑着 DSH 掌上通面板」。
     *
     * <p>两个标记任一命中即可：{@code DSH 掌上通} 是页面标题里的产品名，
     * {@code /app.apk} 是下载链接。**不匹配就当作不是**（宁可不认，也不乱发令牌）。
     */
    public static final String PANEL_MARKER = "DSH 掌上通";
    public static final String PANEL_MARKER_ALT = "/app.apk";

    /** 单次扫描最多探测的地址数（/24 去掉网络号、广播号与自己，最多 253 个）。 */
    public static final int MAX_HOSTS = 254;

    public static final int DEFAULT_CONCURRENCY = 32;
    public static final int DEFAULT_TIMEOUT_MS = 300;
    public static final long DEFAULT_BUDGET_MS = 8_000L;

    private LanScan() { }

    /** 手机自己的一段局域网：IP + 前缀长度。 */
    public static final class LocalNet {
        public final String ip;
        public final int prefix;

        public LocalNet(String ip, int prefix) {
            this.ip = ip == null ? "" : ip.trim();
            this.prefix = prefix;
        }

        @Override public String toString() { return ip + "/" + prefix; }
    }

    /** 扫描进度（**在工作线程回调**，调用方自己切主线程）。 */
    public interface Progress {
        /** 已探测 / 总数。 */
        void onProgress(int tried, int total);
        /** 找到一个面板（该 IP 的 8099 返回了 DSH 面板页）。 */
        void onFound(String ip);
    }

    // ------------------------------------------------------------ 本机网段

    /**
     * 手机自己当前的局域网 IPv4（含前缀长度）；取不到就返回空表。
     *
     * <p>判据比 {@link LanAddress#isUsableLanAddress(String)} 宽松一档：那个函数是给
     * 「电脑上报的候选地址」用的（要挡掉电脑上的虚拟网卡 172.16/12），而这里是**手机自己的
     * 网卡地址** —— 手机真的待在 172.20.x 的办公网里时，那段是合法可扫的。
     * 只挡掉回环、APIPA（169.254，DHCP 没拿到地址）、0.x 与组播/保留段。
     */
    public static List<LocalNet> localNets() {
        List<LocalNet> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                try {
                    if (ni.isLoopback() || !ni.isUp()) continue;
                } catch (Throwable ignored) { continue; }
                for (java.net.InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress a = ia == null ? null : ia.getAddress();
                    if (!(a instanceof Inet4Address)) continue;
                    String ip = a.getHostAddress();
                    if (!isScannableLocalIpv4(ip)) continue;
                    int prefix = ia.getNetworkPrefixLength();
                    if (prefix <= 0 || prefix > 32) prefix = 24;
                    out.add(new LocalNet(ip, prefix));
                }
            }
        } catch (Throwable ignored) { /* 取不到就返回空表，调用方会给出明确提示 */ }
        return out;
    }

    /**
     * 这个 IPv4 值不值得拿去扫本机子网。
     *
     * <p>刻意**不**复用 {@link LanAddress#isUsableLanAddress}：那个判据会连
     * 172.16/12（虚拟网卡重灾区）一起拒掉，而手机自己可能真的在 172.16/12 的办公网里。
     * 这里只排除「扫了也没意义」的四类：回环 / APIPA / 未指定 / 组播保留。
     */
    public static boolean isScannableLocalIpv4(String ip) {
        int[] n = parseIpv4(ip);
        if (n == null) return false;
        int a = n[0], b = n[1];
        if (a == 127) return false;                          // 回环
        if (a == 169 && b == 254) return false;              // APIPA：DHCP 没拿到地址
        if (a == 0) return false;                            // 未指定
        if (a >= 224) return false;                          // 组播 / 保留
        return true;
    }

    // ------------------------------------------------------------ 候选地址

    /**
     * 该网段里要探测的 IP 列表（不含网络号、广播号与自己）。
     *
     * <p>前缀小于 24 时**收窄成自己所在的 /24**：一个 /16 有 6 万多个地址，
     * 全扫既没意义也会把手机卡住；而电脑几乎必然与手机在同一个 /24 里。
     * 前缀大于 30 时按 /30 处理（再窄就只剩两三个地址，扫它没意义）。
     */
    public static List<String> candidates(LocalNet net) {
        List<String> out = new ArrayList<>();
        if (net == null) return out;
        long ip = ipv4ToLong(net.ip);
        if (ip < 0) return out;
        int p = net.prefix;
        if (p < 24) p = 24;
        if (p > 30) p = 30;
        long mask = (0xFFFFFFFFL << (32 - p)) & 0xFFFFFFFFL;
        long network = ip & mask;
        long broadcast = network | (~mask & 0xFFFFFFFFL);
        for (long x = network + 1; x < broadcast && out.size() < MAX_HOSTS; x++) {
            if (x == ip) continue;
            int last = (int) (x & 0xFFL);
            if (last == 0 || last == 255) continue;          // 网络号 / 广播号（含子网内的）
            out.add(longToIpv4(x));
        }
        return out;
    }

    /** 把多段网段的候选地址合成一张去重后的列表。 */
    public static List<String> candidatesOf(List<LocalNet> nets) {
        List<String> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (nets == null) return all;
        for (LocalNet n : nets) {
            for (String ip : candidates(n)) {
                if (ip != null && seen.add(ip)) all.add(ip);
            }
        }
        return all;
    }

    // ------------------------------------------------------------ 探测

    /**
     * 探测一个 IP 上有没有 DSH 面板页。返回页面正文（用于判据 / 诊断），不是面板就返回 null。
     * 只读首页、不发令牌、最多读 4KB。
     */
    public static String probePanel(String ip, int port, int timeoutMs) {
        if (ip == null || ip.isEmpty()) return null;
        HttpURLConnection c = null;
        try {
            URL u = new URL("http://" + ip + ":" + port + "/");
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "DSH-Mobile-Android");
            int code = c.getResponseCode();
            if (code < 200 || code >= 400) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            int total = 0;
            while (total < 4096 && (n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                total += n;
            }
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;      // 连不上 / 超时 / 端口没人听：都只是"不是面板"，不是错误
        } finally {
            if (c != null) try { c.disconnect(); } catch (Throwable ignored) { }
        }
    }

    /** 页面正文是不是 DSH 掌上通面板页。 */
    public static boolean looksLikePanel(String body) {
        if (body == null || body.isEmpty()) return false;
        return body.contains(PANEL_MARKER) || body.contains(PANEL_MARKER_ALT);
    }

    /**
     * 并发扫描一组网段，返回**找到了面板**的 IP 列表（按发现顺序）。
     *
     * <p>短超时 + 限并发 + 总预算：到点就停，不把手机卡住。可被 {@code cancel} 打断。
     *
     * @param nets        要扫的网段（{@link #localNets()} 的结果）
     * @param port        面板端口（一般 {@link #PANEL_PORT}）
     * @param concurrency 并发上限（至少 1）
     * @param timeoutMs   单请求连接/读取超时
     * @param budgetMs    本次扫描总预算（毫秒）
     * @param progress    进度回调（工作线程调用，可为 null）
     * @param cancel      取消标志（可为 null）
     */
    public static List<String> scan(List<LocalNet> nets, int port, int concurrency, int timeoutMs,
                                   long budgetMs, final Progress progress, final AtomicBoolean cancel) {
        final List<String> hosts = Collections.synchronizedList(new ArrayList<String>());
        final List<String> all = candidatesOf(nets);
        final int total = all.size();
        if (total == 0) return hosts;

        final int threads = Math.max(1, Math.min(Math.max(1, concurrency), total));
        final int tmo = Math.max(50, timeoutMs);
        final long budget = Math.max(500L, budgetMs);
        final long deadline = System.currentTimeMillis() + budget;
        final AtomicInteger next = new AtomicInteger(0);
        final AtomicInteger done = new AtomicInteger(0);
        final CountDownLatch latch = new CountDownLatch(threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads, new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "lan-scan");
                t.setDaemon(true);
                return t;
            }
        });
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(new Runnable() {
                    @Override public void run() {
                        try {
                            while (true) {
                                if (cancel != null && cancel.get()) return;
                                if (System.currentTimeMillis() > deadline) return;
                                int idx = next.getAndIncrement();
                                if (idx >= total) return;
                                String ip = all.get(idx);
                                if (looksLikePanel(probePanel(ip, port, tmo))) {
                                    hosts.add(ip);
                                    if (progress != null) progress.onFound(ip);
                                }
                                int d = done.incrementAndGet();
                                if (progress != null && (d % 8 == 0 || d >= total)) {
                                    progress.onProgress(d, total);
                                }
                            }
                        } finally {
                            latch.countDown();
                        }
                    }
                });
            }
            try { latch.await(budget + 4000L, TimeUnit.MILLISECONDS); } catch (Throwable ignored) { }
        } catch (Throwable ignored) {
        } finally {
            try { pool.shutdownNow(); } catch (Throwable ignored) { }
        }
        return new ArrayList<>(hosts);
    }

    // ------------------------------------------------------------ 小工具（纯函数，可断言）

    /** 严格点分四段 -> int[4]；不是合法 IPv4 返回 null。 */
    public static int[] parseIpv4(String s) {
        String v = s == null ? "" : s.trim();
        String[] parts = v.split("\\.", -1);
        if (parts.length != 4) return null;
        int[] n = new int[4];
        for (int i = 0; i < 4; i++) {
            String p = parts[i];
            if (p.isEmpty() || p.length() > 3) return null;
            for (int k = 0; k < p.length(); k++) {
                char c = p.charAt(k);
                if (c < '0' || c > '9') return null;
            }
            n[i] = Integer.parseInt(p);
            if (n[i] > 255) return null;
        }
        return n;
    }

    /** 点分 IPv4 -> 32 位无符号值；非法返回 -1。 */
    public static long ipv4ToLong(String s) {
        int[] n = parseIpv4(s);
        if (n == null) return -1L;
        return ((long) n[0] << 24) | ((long) n[1] << 16) | ((long) n[2] << 8) | (long) n[3];
    }

    /** 32 位无符号值 -> 点分 IPv4。 */
    public static String longToIpv4(long v) {
        return ((v >> 24) & 0xFF) + "." + ((v >> 16) & 0xFF) + "." + ((v >> 8) & 0xFF) + "." + (v & 0xFF);
    }
}
