package com.dsh.mobile.net;

/**
 * 线路选择：**该连内网还是公网、以及这个结论是从哪条规则来的**。
 *
 * <p>刻意做成纯逻辑（零 Android 依赖、零 I/O），因为它是最容易出错、也最需要断言的一段：
 * JVM 上可以直接穷举「WiFi/移动数据/没网 × 三种手动档 × 有没有可用内网地址」的组合，
 * 断言见 {@code harness/src/RoutePolicyTest.java}。
 *
 * <p>用户定的规则（2026-10-02，原话：「连接 wifi 的时候就采用内网。5G 或者未连接 WiFi
 * 的时候就外网，剩下留给他们手动切换」）：
 * <pre>
 *   ① 已连 WiFi            -> 内网。用 {@link LanAddress} 过滤后**真的连得上**的那条；
 *                             没有可用内网地址（虚拟网卡 / APIPA / 回环 / 没扫到）就当没有内网 -> 公网。
 *   ② 移动数据 / 未连 WiFi  -> 公网。
 *   ③ 手动（只用内网 / 只用公网）-> 完全按用户的选择，跳过 ①②（手动永远优先）。
 * </pre>
 *
 * <p>自动档的第二道保险（不在这里，在 {@code MainActivity}）：连着 WiFi 但不是家里那个网时，
 * 内网会连不上 —— 5~8 秒超时后由调用方记一次「内网刚失败」，把 {@code lanBackoff} 置真，
 * 本类于是改走公网并保持 60 秒（滞回/退避），避免每次重连都白等一次超时（防乒乓）。
 */
public final class RoutePolicy {

    /** 自动（推荐）：按当前网络自动选内网/公网。 */
    public static final String AUTO = "auto";
    /** 只用内网：不因为是移动数据就改走公网。 */
    public static final String LAN = "lan";
    /** 只用公网：即使连着 WiFi 也走公网。 */
    public static final String WAN = "wan";

    private RoutePolicy() { }

    /** 归一化：只认 auto/lan/wan，其余（含 null、空串、旧数据里的怪值）一律当 auto。 */
    public static String normalizeMode(String m) {
        if (LAN.equals(m)) return LAN;
        if (WAN.equals(m)) return WAN;
        return AUTO;
    }

    /** 一次选择的完整结果。**只含「走哪条 + 规则来源」，绝不携带地址**（卡片要能截图分享）。 */
    public static final class Pick {
        /** 这次该连的地址；空 = 按当前规则没有可用地址。 */
        public String url = "";
        public String mode = AUTO;
        /** true = 走公网。 */
        public boolean wan = false;
        /** true = 走的是自动规则（false = 用户在设置里手动指定的档位）。 */
        public boolean auto = true;
        /** 规则来源，如「自动 · 已连 WiFi」「手动 · 只用公网」。 */
        public String source = "";
        /** 结论，如「走内网」「走公网」「没有可用地址」。 */
        public String line = "";
    }

    /** 合成一句话（卡片 / 顶栏用）：走内网（自动 · 已连 WiFi）。 */
    public static String label(Pick p) {
        if (p == null) return "";
        return p.line + "（" + p.source + "）";
    }

    public static Pick decide(String mode, String lanUrl, String wanUrl,
                              boolean wifi, boolean wifiKnown, boolean lanBackoff) {
        return decide(mode, lanUrl, wanUrl, wifi, wifiKnown, lanBackoff, false);
    }

    /**
     * 做出这次的选择（带「当前有没有默认网络」的完整版）。
     *
     * @param mode       设备保存的连接方式（auto/lan/wan，见 {@link #normalizeMode}）
     * @param lanUrl     该设备保存的内网地址（可空）
     * @param wanUrl     该设备保存的公网地址（可空）
     * @param wifi       当前默认网络是不是 WiFi（{@link NetStatus#wifi()}）
     * @param wifiKnown  有没有真的判定过（false 只影响文案：「网络未识别」）
     * @param lanBackoff true = 内网刚刚连不上，处于退避窗口（自动档此时不再首选内网）
     * @param netDown    当前完全没有默认网络（飞行模式 / 没网）：只影响文案，别把"没网"说成"移动数据"
     */
    public static Pick decide(String mode, String lanUrl, String wanUrl,
                              boolean wifi, boolean wifiKnown, boolean lanBackoff, boolean netDown) {
        Pick p = new Pick();
        p.mode = normalizeMode(mode);
        String lan = lanUrl == null ? "" : lanUrl.trim();
        String wan = wanUrl == null ? "" : wanUrl.trim();
        boolean usableLan = LanAddress.isUsableLanUrl(lan);

        // ---- 手动优先：用户显式选过的档位，一律跳过自动规则
        if (WAN.equals(p.mode)) {
            p.auto = false;
            p.wan = true;
            if (wan.isEmpty()) {
                p.source = "手动 · 只用公网（还没填公网地址）";
                p.line = "没有可用地址";
                return p;
            }
            p.source = "手动 · 只用公网";
            p.line = "走公网";
            p.url = wan;
            return p;
        }
        if (LAN.equals(p.mode)) {
            p.auto = false;
            if (lan.isEmpty()) {
                p.source = "手动 · 只用内网（还没填内网地址）";
                p.line = "没有可用地址";
                return p;
            }
            // 手填的地址就是用户的显式决定（例如企业里的 172.16 网段），不做 LanAddress 可用性过滤
            p.source = "手动 · 只用内网";
            p.line = "走内网";
            p.url = lan;
            return p;
        }

        // ---- 自动：WiFi -> 内网，其余 -> 公网
        p.auto = true;
        if (wifi && usableLan) {
            if (lanBackoff && !wan.isEmpty()) {
                p.wan = true;
                p.source = "自动 · 已连 WiFi（内网刚连不上，暂用公网）";
                p.line = "走公网";
                p.url = wan;
                return p;
            }
            p.source = "自动 · 已连 WiFi";
            p.line = "走内网";
            p.url = lan;
            return p;
        }
        if (wifi) {
            // 连着 WiFi 却没有可用内网地址（虚拟网卡 / APIPA / 回环 / 没扫到）-> 视为没有内网
            p.source = "自动 · 已连 WiFi（没有可用内网地址）";
            if (!wan.isEmpty()) {
                p.wan = true;
                p.line = "走公网";
                p.url = wan;
            } else {
                p.line = "没有可用地址";
            }
            return p;
        }
        p.source = !wifiKnown ? "自动 · 网络未识别"
                : (netDown ? "自动 · 未连 WiFi（当前无网络）" : "自动 · 未连 WiFi（移动数据）");
        if (!wan.isEmpty()) {
            p.wan = true;
            p.line = "走公网";
            p.url = wan;
            return p;
        }
        if (usableLan) {
            // 没填公网地址又没连 WiFi：能用内网总比完全连不上好（用户规则里这一步本来就该有公网）
            p.source = "自动 · 未连 WiFi（没有公网地址，暂用内网）";
            p.line = "走内网";
            p.url = lan;
            return p;
        }
        p.line = "没有可用地址";
        return p;
    }
}
