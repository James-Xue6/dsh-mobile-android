package com.dsh.mobile.net;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 「连上内网时，顺手把（可能已经变了的）公网地址同步回来」的纯逻辑。
 *
 * <p>要解决的问题（真机实测 2026-10-04）：公网隧道用的是 Cloudflare Quick Tunnel，
 * <b>每次重启电脑域名都会变</b>。手机里存着上一次的公网地址，出门时内网连不上、切到公网
 * 又是死地址，只能回电脑前重新扫码 —— 而这时候人往往已经不在电脑边上了。
 *
 * <p>做法：在家（内网连得上）时向电脑问一次「你现在的公网地址是什么」，直接更新本地记录。
 * 出门时存的就已经是最新地址，不用再扫码。
 *
 * <p>为什么单独成类、且刻意零 Android 依赖、零 I/O：与 {@link RoutePolicy} 同因 ——
 * 「该不该覆盖用户手里的地址」是最容易做错、又最需要穷举的一段，而在真机上每试一种组合
 * 都要重启电脑或开关 WiFi。判据放在这里，JVM 上直接跑断言（见
 * {@code harness/src/AddressSyncTest.java}）。
 *
 * <p><b>核心取舍：宁可少更新，也不打扰用户。</b>
 * 用户可能自己填了固定地址（Tailscale 的 {@code ws://100.x}、自建 {@code wss://}、企业内网），
 * 这类地址一旦被自动覆盖，用户会莫名其妙地「昨天还能用、今天连不上」。所以：
 * <ul>
 *   <li>内网槽位：只装「手机真连得上」的私有地址（{@link LanAddress} 那套判据），
 *       电脑换了网段时自动纠正 —— 这个槽位本来就是留给"当前网络下的电脑"的，用户不会手填出花样。</li>
 *   <li>公网槽位：**只在空着、或存的是临时隧道域名（{@code *.trycloudflare.com}）时才更新**；
 *       其它一律不动，那是用户自己的决定。</li>
 *   <li>网关身份：两边都报出了 gatewayId 且不一致时，一个字段都不改 —— 说明这不是同一台电脑。</li>
 * </ul>
 */
public final class AddressSync {

    private AddressSync() { }

    /** 一次同步的结论：该存成什么 + 是否真的变了 + 一句诊断说明。 */
    public static final class Result {
        /** 该存的内网地址（没变化时就是原值）。 */
        public final String lanUrl;
        /** 该存的公网地址（没变化时就是原值）。 */
        public final String wanUrl;
        /** 是否与传入的现值不同。false 时调用方不该写存储。 */
        public final boolean changed;
        /** 诊断用说明（例如「公网地址已更新」）；不直接展示给用户。 */
        public final String note;

        Result(String lanUrl, String wanUrl, boolean changed, String note) {
            this.lanUrl = lanUrl;
            this.wanUrl = wanUrl;
            this.changed = changed;
            this.note = note;
        }
    }

    /**
     * 是不是「临时隧道域名」：Quick Tunnel 每次重启电脑都会换，属于可以安全替换的地址。
     * 命名隧道（固定域名）不含这个后缀，因此不会被自动覆盖。
     */
    public static boolean isEphemeralWan(String url) {
        String s = url == null ? "" : url.trim().toLowerCase(Locale.ROOT);
        return s.contains("trycloudflare.com");
    }

    /**
     * 从候选地址里挑公网地址：优先用网关明确报出的 {@code publicUrl}（若它确实是公网地址），
     * 否则在 {@code endpoints} 里找第一个公网地址。
     *
     * <p>为什么要过滤：网关的 {@code publicUrl} 有时是电脑本机的
     * {@code ws://127.0.0.1:19387/ws/mobile}（只对电脑自己有效），直接存下来手机永远连不上 ——
     * 这正是 {@code endpoints} 存在的原因。
     */
    public static String pickWanUrl(String reportedPublicUrl, List<String> endpoints) {
        String pub = reportedPublicUrl == null ? "" : reportedPublicUrl.trim();
        if (!pub.isEmpty() && isPublicUrl(pub)) return pub;
        if (endpoints != null) {
            for (String url : endpoints) {
                String t = url == null ? "" : url.trim();
                if (!t.isEmpty() && isPublicUrl(t)) return t;
            }
        }
        return "";
    }

    /**
     * 做出本次同步的结论。
     *
     * @param storedGatewayId   设备记录里存的网关 id（可能为空：老数据/手填地址）
     * @param reportedGatewayId 网关这次报的 id（可能为空：老网关）
     * @param currentLanUrl     当前存的内网地址
     * @param currentWanUrl     当前存的公网地址
     * @param endpoints         网关报的全部可用地址
     * @param reportedPublicUrl 网关报的公网地址（可能为空，也可能是个没用的本机地址）
     */
    public static Result decide(String storedGatewayId, String reportedGatewayId,
                                String currentLanUrl, String currentWanUrl,
                                List<String> endpoints, String reportedPublicUrl) {
        String curLan = currentLanUrl == null ? "" : currentLanUrl.trim();
        String curWan = currentWanUrl == null ? "" : currentWanUrl.trim();

        // 网关身份不一致：这不是同一台电脑，一个字段都别改
        String sg = storedGatewayId == null ? "" : storedGatewayId.trim();
        String rg = reportedGatewayId == null ? "" : reportedGatewayId.trim();
        if (!sg.isEmpty() && !rg.isEmpty() && !sg.equals(rg)) {
            return new Result(curLan, curWan, false, "网关身份不一致，跳过");
        }

        List<String> list = new ArrayList<>();
        if (endpoints != null) {
            for (String u : endpoints) if (u != null && !u.trim().isEmpty()) list.add(u.trim());
        }

        // ---- 内网槽位：只认「手机真连得上」的地址，电脑换网段时自动纠正
        String newLan = LanAddress.pickLanUrl(list);
        boolean lanChanged = !newLan.isEmpty() && !newLan.equals(curLan);

        // ---- 公网槽位：只在空着 / 存的是临时隧道域名时更新
        String candWan = pickWanUrl(reportedPublicUrl, list);
        boolean wanReplaceable = curWan.isEmpty() || isEphemeralWan(curWan);
        boolean wanChanged = !candWan.isEmpty() && !candWan.equals(curWan) && wanReplaceable;

        String lan = lanChanged ? newLan : curLan;
        String wan = wanChanged ? candWan : curWan;
        if (!lanChanged && !wanChanged) {
            return new Result(lan, wan, false, "")
                    ;
        }

        StringBuilder note = new StringBuilder();
        if (lanChanged) note.append("内网地址已更新");
        if (wanChanged) {
            if (note.length() > 0) note.append("；");
            note.append(isEphemeralWan(curWan) ? "公网地址已更新（隧道域名变化）" : "公网地址已补充");
        }
        return new Result(lan, wan, true, note.toString());
    }

    /** 便捷重载：把候选地址按数组传入（调用方从 JSON 数组拷出来时省一次装箱）。 */
    public static Result decide(String storedGatewayId, String reportedGatewayId,
                                String currentLanUrl, String currentWanUrl,
                                String[] endpoints, String reportedPublicUrl) {
        return decide(storedGatewayId, reportedGatewayId, currentLanUrl, currentWanUrl,
                endpoints == null ? null : Arrays.asList(endpoints), reportedPublicUrl);
    }

    // ---------------------------------------------------------------- 地址判据

    /**
     * 从「当前连接地址」里取出这台电脑的局域网 IP，供**在内网时直连它的 HTTP 服务**用；取不到返回空串。
     *
     * <p>别看这一行简单，它踩过一次真机坑（2026-10-05）：{@code MainActivity} 里那个简易的
     * {@code hostOf()} **不去端口**，对 {@code ws://192.168.1.14:3091/ws/mobile} 返回的是
     * {@code 192.168.1.14:3091}；再喂给 {@link LanAddress#isUsableLanAddress(String)} 会被判成
     * 「不是 IP」而返回 false —— 于是地址同步在真机上**一次都没触发过**，偏偏单元测试全绿
     * （纯逻辑只测到 {@link #decide}，问题出在「取地址」这层胶水上）。
     *
     * <p>所以这段胶水也收进本类，并在测试里盯着：必须用 {@link LanAddress#hostOf(String)}
     * （会去端口），**不要**用 {@code MainActivity.hostOf()}。
     */
    public static String lanHostForHttp(String activeUrl) {
        String host = LanAddress.hostOf(activeUrl);
        return LanAddress.isUsableLanAddress(host) ? host : "";
    }

    /**
     * 这个地址是不是「手机上用于公网连接」的地址。
     *
     * <p>与 {@link LanAddress#isUsableLanUrl(String)} 互补：那边判「能不能当内网用」，
     * 这边判「是不是真的在公网侧」。注意不能简单取反 —— 172.30.x（电脑上的虚拟网卡）
     * 两边都不是：既连不上，也不该占公网槽位。
     */
    public static boolean isPublicUrl(String url) {
        String host = LanAddress.hostOf(url);
        if (host.isEmpty()) return false;
        if (LanAddress.isUsableLanAddress(host)) return false;          // 可用内网地址
        if (host.indexOf(':') >= 0) {                                   // IPv6 字面量
            String h = host.toLowerCase(Locale.ROOT);
            return !("::1".equals(h) || "0:0:0:0:0:0:0:1".equals(h));
        }
        if (host.indexOf('.') < 0) return false;                        // localhost 这类裸名
        int[] ip = ipv4OrNull(host);
        if (ip == null) return true;                                    // 域名 → 视为公网可达
        int a = ip[0], b = ip[1];
        if (a == 0 || a == 127 || a >= 224) return false;               // 未指定 / 回环 / 组播保留
        if (a == 10 || (a == 192 && b == 168)) return false;            // 私有（=可用内网，已在上面挡过）
        if (a == 172 && b >= 16 && b <= 31) return false;               // 私有（虚拟网卡重灾区）
        if (a == 169 && b == 254) return false;                         // APIPA
        if (a == 100 && b >= 64 && b <= 127) return false;              // CGNAT
        return true;                                                    // 公网 IPv4
    }

    /** 严格解析点分四段 IPv4；不是字面量就返回 null。 */
    private static int[] ipv4OrNull(String s) {
        String[] parts = s.split("\\.", -1);
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
}
