package com.dsh.mobile.net;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

/**
 * 「手机在同一个 WiFi 下真的连得上」的局域网地址判据。
 *
 * <p>与 {@link com.dsh.mobile.Store#isPrivateUrl(String)} 的分工（**不要混用**）：
 * <ul>
 *   <li>{@code isPrivateUrl()} 判的是<b>地址性质</b>：172.30.x 确实属于私网段，语义没错，
 *       但它是电脑上虚拟网卡（VirtualBox / Hyper-V / WSL / 部分 VPN）的地址，
 *       只在宿主机内部有意义，手机即使同一 WiFi 也路由不过去。</li>
 *   <li>本类判的是<b>可用性</b>：手机拿着这个地址能不能真的连上电脑。虚拟网卡、APIPA、
 *       CGNAT、回环、以及公网地址一律不可用。</li>
 * </ul>
 *
 * <p>真机实测（2026-10-02）：网关配对载荷 {@code endpoints} 里同时含虚拟网卡地址与真实内网
 * 地址，App 取地址时没有过滤、把虚拟网卡地址写进了 {@code Store.Device.lanUrl}，
 * 于是「在家走内网」必然失败、永远只能走公网。判据与 PC 面板侧
 * （{@code pc-plugin/dsh-mobile-access/index.js} 的 {@code isUsableLanAddress()}）保持一致。
 *
 * <p>判据表（IPv4）：
 * <pre>
 *   192.168.0.0/16   可用（首选，家用/办公最常见）
 *   10.0.0.0/8       可用（次选）
 *   172.16.0.0/12    拒绝 —— 虚拟网卡重灾区（含 172.30.x），只在宿主机内部有意义
 *   169.254.0.0/16   拒绝 —— APIPA，DHCP 没拿到地址时的自分配地址，打不通
 *   100.64.0.0/10    拒绝 —— 运营商级 NAT（CGNAT），不是本机内网地址
 *   127.0.0.0/8      拒绝 —— 本机回环
 *   0.0.0.0/8、>=224 拒绝 —— 未指定 / 组播 / 保留
 *   其它（公网 IP 等） 拒绝 —— 不是局域网地址，不该占「内网」这个槽位
 * </pre>
 * IPv6 只接受 {@code fc00::/7}（ULA）与 {@code fe80::/10}（链路本地）；回环 {@code ::1}
 * 与其余字面量一律拒绝。写法与 {@code Store.isPrivateIpv6()} 对齐，只是额外拒掉回环。
 */
public final class LanAddress {

    private LanAddress() { }

    /** 该 IPv4/IPv6 字面量是不是「手机连得上」的局域网地址（可用性判据，见类注释）。 */
    public static boolean isUsableLanAddress(String ip) {
        String s = ip == null ? "" : ip.trim();
        if (s.length() >= 2 && s.charAt(0) == '[') {
            int e = s.indexOf(']');
            if (e > 0) s = s.substring(1, e).trim();     // ws://[fe80::1]:3091 这种带方括号的写法
        }
        int pct = s.indexOf('%');
        if (pct >= 0) s = s.substring(0, pct).trim();    // fe80::1%wlan0 的 scope id
        if (s.isEmpty()) return false;
        if (s.indexOf(':') >= 0) return isUsableLanIpv6(s);
        return isUsableLanIpv4(s);
    }

    /** IPv4 可用性判据（真值表见类注释）。非严格点分四段一律拒绝。 */
    private static boolean isUsableLanIpv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) return false;
        int[] n = new int[4];
        for (int i = 0; i < 4; i++) {
            String p = parts[i];
            if (p.isEmpty() || p.length() > 3) return false;
            for (int k = 0; k < p.length(); k++) {
                char c = p.charAt(k);
                if (c < '0' || c > '9') return false;    // 拒绝 "0x1f"、"1e2"、" 1" 这类怪写法
            }
            n[i] = Integer.parseInt(p);
            if (n[i] > 255) return false;
        }
        int a = n[0], b = n[1];
        if (a == 127) return false;                          // 回环
        if (a == 169 && b == 254) return false;              // APIPA
        if (a == 172 && b >= 16 && b <= 31) return false;    // 虚拟网卡 / VBox / Hyper-V / WSL
        if (a == 100 && b >= 64 && b <= 127) return false;   // CGNAT
        if (a == 0 || a >= 224) return false;                // 未指定 / 组播 / 保留
        // 只认真正的家用/办公内网段。PC 面板那一版作用在「本机网卡列表」上（拿到的本来就是
        // 本机地址），安卓这边作用在「网关给的候选地址列表」上，里面混着公网隧道地址，
        // 所以这里必须把非 RFC1918 的地址也挡住，不能只做「排除法」。
        return a == 10 || (a == 192 && b == 168);
    }

    /** IPv6 只接受 fc00::/7（ULA）与 fe80::/10（链路本地）；::1 是回环，手机连不上。 */
    private static boolean isUsableLanIpv6(String s) {
        if ("::1".equals(s) || "0:0:0:0:0:0:0:1".equals(s)) return false;
        String head = s;
        int firstColon = head.indexOf(':');
        if (firstColon >= 0) head = head.substring(0, firstColon);
        if (head.length() < 2) return false;
        String two = head.substring(0, 2).toLowerCase(java.util.Locale.ROOT);
        if ("fc".equals(two) || "fd".equals(two)) return true;          // fc00::/7
        if (head.length() >= 4 && head.startsWith("fe")) {              // fe80::/10 => fe80..febf
            try {
                int x = Integer.parseInt(head.substring(2, 4), 16);
                if (x >= 0x80 && x <= 0xbf) return true;
            } catch (NumberFormatException ignored) { }
        }
        return false;
    }

    /**
     * 从 URL 里抠出主机（去 scheme、去用户名口令、去路径、IPv6 去方括号）。
     * 与 {@code MainActivity.hostOf()} 的宽松写法不同，这里要带方括号处理，
     * 否则 {@code ws://[fc00::1]:3091/…} 会连冒号端口一起被当成「主机」。
     */
    public static String hostOf(String url) {
        String s = url == null ? "" : url.trim();
        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        int at = s.lastIndexOf('@');
        if (at >= 0) s = s.substring(at + 1);
        if (s.startsWith("[")) {
            int e = s.indexOf(']');
            if (e > 0) return s.substring(1, e).trim();
        }
        int colon = s.indexOf(':');
        if (colon >= 0 && colon == s.lastIndexOf(':')) s = s.substring(0, colon);
        return s.trim();
    }

    /** 这个地址（URL 或裸主机）是不是手机可用的局域网地址。 */
    public static boolean isUsableLanUrl(String url) {
        String host = hostOf(url);
        return !host.isEmpty() && isUsableLanAddress(host);
    }

    /**
     * 从 URL 里抠出**显式**端口；没写端口返回空串（不要拿 80/443 之类的默认值糊弄调用方）。
     * 与 {@link #hostOf(String)} 同一套宽松写法：去 scheme、去用户名口令、去路径。
     */
    public static String portOf(String url) {
        String s = url == null ? "" : url.trim();
        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        int at = s.lastIndexOf('@');
        if (at >= 0) s = s.substring(at + 1);
        if (s.startsWith("[")) {                       // IPv6 字面量：端口在 ] 之后
            int e = s.indexOf(']');
            if (e < 0) return "";
            int colon = s.indexOf(':', e);
            return colon >= 0 ? s.substring(colon + 1).trim() : "";
        }
        int colon = s.indexOf(':');
        if (colon < 0 || colon != s.lastIndexOf(':')) return "";   // 无端口 / IPv6 裸写
        return s.substring(colon + 1).trim();
    }

    /** 从 URL 里抠出路径（含查询串）；没有路径返回空串。 */
    public static String pathOf(String url) {
        String s = url == null ? "" : url.trim();
        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);
        int slash = s.indexOf('/');
        return slash >= 0 ? s.substring(slash).trim() : "";
    }

    /**
     * 电脑端移动网关的**局域网**监听端口默认值。
     *
     * <p>本项目 profile 的 {@code cordis.patch.yml} 把网关 {@code lanPort} 覆盖成 3091
     * （避开 dsh-pocket 占用的 3081），所以模板缺失时按 3091 拼。
     * 这只是**兜底猜测**：面板 {@code /public-url} 返回的 {@code lanUrls} 才是权威值，
     * 调用方应当优先用它（见 {@code MainActivity.startAddressDiscovery}）。
     */
    public static final String DEFAULT_GATEWAY_PORT = "3091";

    /**
     * 用扫描到的内网 IP 拼一条「走内网」的 WebSocket 地址（方案 B：地址变了也能自己找回来）。
     *
     * <p>端口与路径**只从已存的内网地址模板**沿用 —— 绝不能拿公网模板的端口去推：
     * 电脑的局域网监听端口（网关 lanPort）与隧道侧的端口/路径是两回事，
     * 拿 {@code wss://域名/ws/mobile}（无端口）去推只会拼出一条连不上的地址。
     * 模板缺失/残缺时退回 {@code ws://<ip>:3091/ws/mobile}。
     */
    public static String lanUrlFor(String ip, String lanTemplateUrl) {
        String host = ip == null ? "" : ip.trim();
        if (host.isEmpty()) return "";
        String port = portOf(lanTemplateUrl);
        String path = pathOf(lanTemplateUrl);
        if (port.isEmpty()) port = DEFAULT_GATEWAY_PORT;
        if (path.isEmpty()) path = "/ws/mobile";
        return "ws://" + host + ":" + port + path;
    }

    /** 排序权重：真实家用网段（192.168.x、10.x）排在其它网段前面，与 PC 面板一致。 */
    public static int lanAddressRank(String ip) {
        String s = ip == null ? "" : ip.trim();
        if (s.startsWith("192.168.")) return 0;
        if (s.startsWith("10.")) return 1;
        return 2;
    }

    /**
     * 从候选地址里挑最合适的一个（192.168.x &gt; 10.x &gt; 其它），没有一个可用时返回空串。
     * 返回空串时调用方必须存空、并且不要拿任何地址去连 —— 「宁可不填，不给假地址」。
     */
    public static String pickLanUrl(Collection<String> urls) {
        List<String> ok = usableLanUrls(urls);
        return ok.isEmpty() ? "" : ok.get(0);
    }

    /** 过滤出可用内网地址并按其权重排序（保留权重相同的原始输入顺序，稳定）。 */
    public static List<String> usableLanUrls(Collection<String> urls) {
        List<String> out = new ArrayList<>();
        if (urls == null) return out;
        for (String u : urls) {
            if (u == null) continue;
            String t = u.trim();
            if (t.isEmpty() || out.contains(t)) continue;
            if (!isUsableLanUrl(t)) continue;
            out.add(t);
        }
        // 稳定插入排序：候选通常只有个位数，不值得引入额外依赖
        for (int i = 1; i < out.size(); i++) {
            String cur = out.get(i);
            int ri = lanAddressRank(hostOf(cur));
            int j = i - 1;
            while (j >= 0 && lanAddressRank(hostOf(out.get(j))) > ri) {
                out.set(j + 1, out.get(j));
                j--;
            }
            out.set(j + 1, cur);
        }
        return out;
    }
}
