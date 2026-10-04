package com.dsh.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.dsh.mobile.net.LanAddress;
import com.dsh.mobile.net.NetStatus;
import com.dsh.mobile.net.RoutePolicy;

/**
 * 本地持久化：网关地址、设备 token、安装级设备 ID、设备名。
 * token 只保存在应用私有 SharedPreferences 中，不落日志。
 *
 * <p>线路选择（内网 / 公网）也在这里收口：{@link #url()} 与 {@link Device#activeUrl()}
 * 不再直接「内网优先」，而是走 {@link RoutePolicy}（WiFi -&gt; 内网、移动数据 -&gt; 公网、
 * 手动档优先，规则与判据见 {@code net/RoutePolicy.java}、{@code net/NetStatus.java}）。
 */
public final class Store {

    private static final String FILE = "dsh_mobile";
    private static final String K_URL = "server_url";
    private static final String K_TOKEN = "device_token";
    private static final String K_DEVICE_ID = "device_id";
    private static final String K_DEVICE_NAME = "device_name";
    private static final String K_GATEWAY_ID = "gateway_id";
    private static final String K_GATEWAY_NAME = "gateway_name";
    private static final String K_LAN = "lan_url";
    private static final String K_WAN = "wan_url";
    /**
     * 「当前生效设备怎么选线路」的旧字段：auto（自动，默认）/ lan（只用内网）/ wan（只用公网）。
     * **它是真身**，设备表里的 {@link Device#netMode} 是它的镜像 —— 与 {@link #K_LAN}/{@link #K_WAN}
     * 同一套设计（写入落旧字段，再由 {@link #syncLegacyIntoActive()} 同步进当前生效的那台）。
     * 设备表为空（全新安装还没配过对）时它就是唯一的档位来源。
     */
    private static final String K_NET_MODE = "net_mode";
    /** 一次性清洗标记：把历史上存成「内网地址」的虚拟网卡地址（172.16/12 等）清掉，只做一次。 */
    private static final String K_LAN_CLEANED = "lan_url_sanitized_v1";
    /**
     * 旧版本的「用公网」布尔。现在只作为**降级兼容**镜像写入：
     * 老版本 App 读它决定走哪条，新版本读了会按 netMode 归位（true -&gt; wan，false -&gt; auto）。
     */
    private static final String K_USE_WAN = "use_wan";
    private static final String K_INSECURE_TLS = "insecure_tls";
    private static final String K_FEEDBACK = "feedback_log";
    private static final String K_RISK_ACK = "risk_ack";
    private static final String K_SKIP_VERSION = "skip_version";
    private static final String K_LAST_UPDATE_CHECK = "last_update_check";
    private static final String K_FEEDBACK_CFG = "feedback_cfg";
    /** 「我的设备」列表（JSON 数组）与当前选中的那台。 */
    private static final String K_DEVICES = "devices_json";
    private static final String K_ACTIVE_DEVICE = "active_device_id";
    /** 是否允许截屏（默认 true）。 */
    private static final String K_ALLOW_SCREENSHOT = "allow_screenshot";
    /**
     * 主题：system（跟随系统，默认） / light（浅色） / dark（深色）。
     * 存字符串而不是布尔，是为了以后再加"护眼/纯黑"这类档位时不用做数据迁移。
     */
    private static final String K_THEME_MODE = "theme_mode";
    /**
     * 通知总开关（默认开）。关掉后一条系统通知都不发，App 照常用（降级）。
     */
    private static final String K_NOTIFY_ENABLED = "notify_enabled";
    /**
     * 「仅需处理时提醒」（默认关）：开着就只推审批/提问，回合完成与进行中都不打扰。
     */
    private static final String K_NOTIFY_ONLY_PENDING = "notify_only_pending";
    /**
     * 通知里是否显示会话标题（默认开）。关掉只写一句概括 —— 锁屏/锁屏通知中心
     * 上就看不出用户在跑什么内容（隐私）。正文永远不会进通知，无论这个开关。
     */
    private static final String K_NOTIFY_DETAIL = "notify_detail";
    /**
     * 后台保持接收（前台服务，默认开）：切后台后进程不被挂起，才收得到事件、发得出通知。
     * 关掉后 App 一切后台就可能收不到通知（用户自己选）。
     */
    private static final String K_KEEPALIVE = "keepalive";
    /** 通知权限是否已经申请过（Android 13+ 只主动问一次，被拒后改为设置页引导）。 */
    private static final String K_NOTIF_ASKED = "notify_perm_asked";

    private final SharedPreferences sp;
    /**
     * 上次所在的会话 id（连接门用）。
     *
     * <p>用户要求「退出重进后连上就回到上次那条会话」，所以必须落盘 ——
     * 进程被杀（{@code am force-stop} / 系统回收）后内存里的 currentSessionId 是空的。
     */
    private static final String K_LAST_SESSION = "last_session_id";

    /** 会话标题缓存：网关的 sessions 列表不含 title，标题从历史里的 session/title 事件抽取后落盘。 */
    private final SharedPreferences titles;

    /**
     * 内网刚连不上的时刻（进程级、不落盘）：自动档在 {@link #LAN_FAIL_BACKOFF_MS} 内不再首选内网，
     * 避免「连着别人家的 WiFi」时每次重连都白等一次 5~8 秒超时（防乒乓）。
     */
    private static volatile long sLanFailAt = 0L;
    /** 内网失败后的退避窗口。换网卡种类（WiFi↔移动数据）、用户手动重连或重新选档位时清零。 */
    private static final long LAN_FAIL_BACKOFF_MS = 60_000L;

    public Store(Context ctx) {
        this.sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        this.titles = ctx.getApplicationContext().getSharedPreferences("dsh_mobile_titles", Context.MODE_PRIVATE);
        // 启动时把「允许截屏」策略同步进进程级镜像：Dialog/扫码等窗口创建时读它决定要不要设 FLAG_SECURE
        com.dsh.mobile.ui.Ui.setAllowScreenshot(sp.getBoolean(K_ALLOW_SCREENSHOT, true));
        // 启动时先问一次「是不是 WiFi」：线路选择在任何一次连接/画卡片之前就要有答案
        // （NetworkCallback 要等 Activity 起来才注册，不能只靠它）
        try { NetStatus.refresh(ctx.getApplicationContext()); } catch (Throwable ignored) { }
        // 老版本可能把虚拟网卡地址（172.16/12 等）当成「内网地址」存了下来 —— 一次迁移清掉
        sanitizeStoredLan();
    }

    /** 记一次「内网这条连不上」（自动档因此暂走公网，见 {@link #LAN_FAIL_BACKOFF_MS}）。 */
    public static void noteLanFailure() { sLanFailAt = System.currentTimeMillis(); }

    /** 清掉内网退避：换网卡种类、用户手动重连/重新选档位时调用，让自动档重新评估。 */
    public static void clearLanFailure() { sLanFailAt = 0L; }

    private static boolean lanBackoff() {
        return System.currentTimeMillis() - sLanFailAt < LAN_FAIL_BACKOFF_MS;
    }

    /**
     * 一次性清洗历史数据：设备表与旧字段里存的「内网地址」如果手机根本连不上
     * （虚拟网卡 172.16/12、APIPA 169.254、回环 127、CGNAT 100.64、公网隧道…），
     * 立刻清掉 —— 不清的话用户会永远卡在「在家也走公网」，
     * 而且设备卡上还挂着「内网 · 固定」这个假标签。
     *
     * <p>清掉的地址若能当公网地址用（不是私网段），就顺手挪进公网槽位，不白扔。
     * 只跑一次（{@link #K_LAN_CLEANED}）；跑过之后用户在设置里手填的企业 172 内网地址
     * 依然会被保存（手填是用户的显式决定，见 {@link #setLanUrl(String)}）。
     */
    private void sanitizeStoredLan() {
        if (sp.getBoolean(K_LAN_CLEANED, false)) return;
        boolean changed = false;
        try {
            java.util.List<Device> list = devices();   // 顺带完成「旧字段 -> 设备表」的迁移
            for (Device d : list) {
                String lan = d.lanUrl == null ? "" : d.lanUrl.trim();
                if (lan.isEmpty() || LanAddress.isUsableLanUrl(lan)) continue;
                if (!isPrivateUrl(lan) && (d.wanUrl == null || d.wanUrl.isEmpty())) d.wanUrl = lan;
                d.lanUrl = "";
                changed = true;
            }
            if (changed) {
                saveDevices(list);
                Device act = activeDevice();
                if (act != null) writeLegacy(act);     // 旧字段（K_LAN/K_URL）是当前设备的镜像，一起刷新
            }
        } catch (Throwable ignored) {
            // 清洗失败绝不能影响启动：留着老数据总比崩在启动页好
        }
        sp.edit().putBoolean(K_LAN_CLEANED, true).apply();
    }

    public String cachedTitle(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return "";
        return titles.getString(sessionId, "");
    }

    public void cacheTitle(String sessionId, String title) {
        if (sessionId == null || sessionId.isEmpty()) return;
        if (title == null || title.trim().isEmpty()) return;
        titles.edit().putString(sessionId, title.trim()).apply();
    }

    /**
     * 上次所在的会话 id（空 = 没有记录）。
     *
     * <p>写入时机：用户真正打开某条会话时（{@code MainActivity.onOpenSession}）。
     * 读取时机：连接门判定「连上了 → 回到上次那条会话」。会话已被删除/归档时
     * 由调用方退回「最近一条会话」，这里不做校验（列表只有连上之后才有）。
     */
    public String lastSessionId() { return sp.getString(K_LAST_SESSION, ""); }

    public void setLastSessionId(String sessionId) {
        sp.edit().putString(K_LAST_SESSION, sessionId == null ? "" : sessionId.trim()).apply();
    }

    public String lanUrl() { return sp.getString(K_LAN, ""); }
    public void setLanUrl(String v) {
        sp.edit().putString(K_LAN, v == null ? "" : v.trim()).apply();
        syncLegacyIntoActive();
    }

    public String wanUrl() { return sp.getString(K_WAN, ""); }
    public void setWanUrl(String v) {
        sp.edit().putString(K_WAN, v == null ? "" : v.trim()).apply();
        syncLegacyIntoActive();
    }

    /** 自建反代自签名证书时允许不校验证书。 */
    public boolean insecureTls() { return sp.getBoolean(K_INSECURE_TLS, false); }
    public void setInsecureTls(boolean v) { sp.edit().putBoolean(K_INSECURE_TLS, v).apply(); }

    /**
     * 是否允许截屏 / 录屏 / 系统「最近任务」缩略图，**默认 true**。
     *
     * 语义：true = 不设 FLAG_SECURE（用户能截图，缩略图正常）；
     * 只有用户显式关掉这个开关，才重新设上 FLAG_SECURE。
     * 令牌本身仍始终掩码显示，与本开关无关（取消 FLAG_SECURE 不等于明文暴露令牌）。
     */
    public boolean allowScreenshot() { return sp.getBoolean(K_ALLOW_SCREENSHOT, true); }

    public void setAllowScreenshot(boolean v) {
        sp.edit().putBoolean(K_ALLOW_SCREENSHOT, v).apply();
        // 同步给进程级镜像：Dialog 等窗口没有 Store 实例，也能通过 Ui 读到同一份策略
        com.dsh.mobile.ui.Ui.setAllowScreenshot(v);
    }

    // ------------------------------------------------------------ 通知（见 com.dsh.mobile.notify.Notifier）

    /**
     * 通知总开关，**默认开**。
     *
     * 关掉只是「不发系统通知」，不影响会话本身：审批/提问卡照旧出现在对话页里，
     * 只在通知中心里安静下来（被系统关掉通知权限时是这个开关的等价位，见 Notifier 的降级）。
     */
    public boolean notifyEnabled() { return sp.getBoolean(K_NOTIFY_ENABLED, true); }
    public void setNotifyEnabled(boolean v) { sp.edit().putBoolean(K_NOTIFY_ENABLED, v).apply(); }

    /** 「仅需处理时提醒」，默认关：开着只推审批/提问，任务完成与进行中都不发。 */
    public boolean notifyOnlyPending() { return sp.getBoolean(K_NOTIFY_ONLY_PENDING, false); }
    public void setNotifyOnlyPending(boolean v) { sp.edit().putBoolean(K_NOTIFY_ONLY_PENDING, v).apply(); }

    /** 通知里是否显示会话标题（默认开）；关掉锁屏上就只看到一句「点开查看详情」。 */
    public boolean notifyShowDetail() { return sp.getBoolean(K_NOTIFY_DETAIL, true); }
    public void setNotifyShowDetail(boolean v) { sp.edit().putBoolean(K_NOTIFY_DETAIL, v).apply(); }

    /** 后台保持接收（前台服务），默认开：这是「切后台后还收得到通知」的前提。 */
    public boolean keepAlive() { return sp.getBoolean(K_KEEPALIVE, true); }
    public void setKeepAlive(boolean v) { sp.edit().putBoolean(K_KEEPALIVE, v).apply(); }

    /** 通知权限是否已经主动申请过（Android 13+ 只问一次，之后走设置页引导）。 */
    public boolean notifPermAsked() { return sp.getBoolean(K_NOTIF_ASKED, false); }
    public void setNotifPermAsked(boolean v) { sp.edit().putBoolean(K_NOTIF_ASKED, v).apply(); }

    /** 本地留一份反馈记录（最新在前，用 --- 分隔）。 */
    public void addFeedback(String text) {
        String old = sp.getString(K_FEEDBACK, "");
        String rec = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                .format(new java.util.Date()) + "  " + (text == null ? "" : text.trim());
        String all = old.isEmpty() ? rec : (rec + "\n---\n" + old);
        if (all.length() > 20000) all = all.substring(0, 20000);
        sp.edit().putString(K_FEEDBACK, all).apply();
    }

    public String feedbackLog() { return sp.getString(K_FEEDBACK, ""); }

    public int feedbackCount() {
        String all = feedbackLog();
        if (all.isEmpty()) return 0;
        int n = 1;
        int i = 0;
        while ((i = all.indexOf("\n---\n", i)) >= 0) { n++; i += 5; }
        return n;
    }

    /** 是否已确认过「开启公网」的安全声明。 */
    public boolean riskAck() { return sp.getBoolean(K_RISK_ACK, false); }
    public void setRiskAck(boolean v) { sp.edit().putBoolean(K_RISK_ACK, v).apply(); }

    /** 用户点过「以后再说」的版本号，同一个版本不再反复弹。 */
    public String skipVersion() { return sp.getString(K_SKIP_VERSION, ""); }
    public void setSkipVersion(String v) { sp.edit().putString(K_SKIP_VERSION, v == null ? "" : v).apply(); }

    public long lastUpdateCheck() { return sp.getLong(K_LAST_UPDATE_CHECK, 0L); }
    public void setLastUpdateCheck(long v) { sp.edit().putLong(K_LAST_UPDATE_CHECK, v).apply(); }

    /** 作者配置的反馈通道（update 清单里的 feedback 段，JSON 原文）；没配过为空。 */
    public String feedbackCfg() { return sp.getString(K_FEEDBACK_CFG, ""); }
    public void setFeedbackCfg(String v) { sp.edit().putString(K_FEEDBACK_CFG, v == null ? "" : v).apply(); }

    /**
     * ⚠️ 兼容旧接口，App 内部已不再使用：{@code false} 现在等于「自动」，**不是**「内网优先」
     * （老版本只有这一个布尔，写死 false 的语义已经由 {@link RoutePolicy} 的自动档取代）。
     * 落盘的 {@link #K_USE_WAN} 仍按「是不是手动只用公网」写，供老版本降级读取。
     */
    public boolean useWan() { return RoutePolicy.WAN.equals(netMode()); }
    public void setUseWan(boolean v) { setNetMode(v ? RoutePolicy.WAN : RoutePolicy.AUTO); }

    /**
     * 当前生效设备的连接方式：{@link RoutePolicy#AUTO}（默认）/ {@link RoutePolicy#LAN} / {@link RoutePolicy#WAN}。
     *
     * <p>⚠️ 真身是**旧字段 K_NET_MODE**，设备表里的 {@code Device.netMode} 是它的镜像 —— 与
     * {@link #lanUrl()}/{@link #wanUrl()} 完全同一套设计（写入落旧字段，再由
     * {@link #syncLegacyIntoActive()} 同步进当前生效的那台）。
     * 真机踩过（2026-10-02，Android TV 盒子）：这里若反过来优先读设备字段，
     * {@link #setNetMode(String)} 写进 K_NET_MODE 后会被 syncLegacyIntoActive 用
     * 设备里的旧值覆盖，**设置页点「自动」根本没生效**（顶栏一直显示「手动 · 只用公网」）。
     */
    public String netMode() {
        String m = sp.getString(K_NET_MODE, "");
        if (m != null && !m.isEmpty()) return RoutePolicy.normalizeMode(m);
        Device d = activeDevice();
        if (d != null) return RoutePolicy.normalizeMode(d.netMode);
        return RoutePolicy.normalizeMode(sp.getBoolean(K_USE_WAN, false) ? RoutePolicy.WAN : RoutePolicy.AUTO);
    }

    /** 设置连接方式（手动档 = 用户显式决定，自动档会按 WiFi/移动数据实时挑）。 */
    public void setNetMode(String mode) {
        String m = RoutePolicy.normalizeMode(mode);
        sp.edit()
                .putString(K_NET_MODE, m)
                .putBoolean(K_USE_WAN, RoutePolicy.WAN.equals(m))   // 降级兼容：老版本只认这个布尔
                .apply();
        syncLegacyIntoActive();
    }

    /** 是不是自动档（手动档下绝不自动切线路 —— 「手动永远优先」）。 */
    public boolean isAutoMode() { return RoutePolicy.AUTO.equals(netMode()); }

    /**
     * 给某台设备算这次该连哪条（纯函数 + 进程内缓存的 WiFi 判定，不发系统调用、不落盘）。
     * 卡片文案、连接地址、诊断信息全部走这里，保证「显示的」和「连的」是同一个结论。
     */
    public static RoutePolicy.Pick pickOf(Device d) {
        if (d == null) return RoutePolicy.decide(RoutePolicy.AUTO, "", "", NetStatus.wifi(), NetStatus.known(), false);
        return RoutePolicy.decide(d.netMode, d.lanUrl, d.wanUrl,
                NetStatus.wifi(), NetStatus.known(), lanBackoff(), !NetStatus.online());
    }

    /** 当前生效设备的选择结果。 */
    public RoutePolicy.Pick pickActive() {
        Device d = activeDevice();
        if (d != null) {
            // 档位取 netMode()（旧字段真身）而不是设备字段的镜像：万一两者短暂不同步，
            // 也保证「设置页显示的档位」和「实际走哪条」是同一个结论
            return RoutePolicy.decide(netMode(), d.lanUrl, d.wanUrl,
                    NetStatus.wifi(), NetStatus.known(), lanBackoff(), !NetStatus.online());
        }
        return RoutePolicy.decide(netMode(), lanUrl(), wanUrl(),
                NetStatus.wifi(), NetStatus.known(), lanBackoff(), !NetStatus.online());
    }

    /** 「走内网 / 走公网 / 没有可用地址」。 */
    public String routeLine() { return pickActive().line; }

    /** 规则来源：「自动 · 已连 WiFi」「自动 · 未连 WiFi（移动数据）」「手动 · 只用公网」… */
    public String routeSource() { return pickActive().source; }

    /** 现在这条线路是不是公网。 */
    public boolean usingWan() { return pickActive().wan; }

    /**
     * 当前生效的地址：按线路规则（WiFi -&gt; 内网、移动数据/未连 WiFi -&gt; 公网、手动档优先）算出来。
     *
     * <p>为什么收口在这里：全 App 连的都是 {@code store.url()}，把规则放在这一个函数里，
     * 「显示的线路」和「实际的线路」就不会各算各的。旧字段 K_URL 只在两个槽位都空时兜底
     * （全新安装手填地址、设备表迁移前的中间态）。
     */
    public String url() {
        String u = pickActive().url;
        if (!u.isEmpty()) return u;
        if (lanUrl().isEmpty() && wanUrl().isEmpty()) return sp.getString(K_URL, "");
        return "";
    }

    /** 配对/手填时按地址性质归位到内网或公网槽位。 */
    public void setUrl(String v) {
        String u = v == null ? "" : v.trim();
        sp.edit().putString(K_URL, u).apply();
        if (u.isEmpty()) { syncLegacyIntoActive(); return; }
        if (isPrivateUrl(u)) setLanUrl(u);
        else setWanUrl(u);
        syncLegacyIntoActive();
    }

    /** 私有地址判定：localhost/.local/.lan/.ts.net、10./127./169.254./192.168./172.16-31、
     *  100.64/10，以及 IPv6 里的 ::1、fc00::/7、fe80::/10；其余 IPv6 与公网 IPv4/域名都不是。 */
    public static boolean isPrivateUrl(String url) {
        String s = url == null ? "" : url.trim();
        String rest;
        if (s.regionMatches(true, 0, "wss://", 0, 6)) rest = s.substring(6);
        else if (s.regionMatches(true, 0, "ws://", 0, 5)) rest = s.substring(5);
        else if (s.regionMatches(true, 0, "https://", 0, 8)) rest = s.substring(8);
        else if (s.regionMatches(true, 0, "http://", 0, 7)) rest = s.substring(7);
        else rest = s;
        int slash = rest.indexOf('/');
        String hostPort = slash >= 0 ? rest.substring(0, slash) : rest;
        int at = hostPort.lastIndexOf('@');
        if (at >= 0) hostPort = hostPort.substring(at + 1);
        String host = hostPort;
        if (host.startsWith("[")) { int e = host.indexOf(']'); if (e > 0) host = host.substring(1, e); }
        else {
            int c = host.indexOf(':');
            // 同 GatewayClient：没有方括号的 IPv6 字面量不能被截成单标签主机名放行
            if (c >= 0 && c == host.lastIndexOf(':')) host = host.substring(0, c);
        }
        host = host.toLowerCase(java.util.Locale.ROOT).trim();
        if (host.isEmpty()) return true;
        if ("localhost".equals(host) || host.endsWith(".local") || host.endsWith(".lan")
                || host.endsWith(".ts.net")) return true;
        // 同 GatewayClient：IPv6 必须先单独判定，否则「无点号即内网」会放过公网 IPv6 字面量
        if (host.indexOf(':') >= 0) return isPrivateIpv6(host);
        String[] p = host.split("\\.");
        if (p.length == 4) {
            try {
                int a = Integer.parseInt(p[0]), b = Integer.parseInt(p[1]);
                if (a == 127 || a == 10) return true;
                if (a == 192 && b == 168) return true;
                if (a == 172 && b >= 16 && b <= 31) return true;
                if (a == 169 && b == 254) return true;
                if (a == 100 && b >= 64 && b <= 127) return true; // CGNAT / Tailscale
                return false;
            } catch (NumberFormatException e) { return false; }
        }
        if (host.indexOf('.') < 0) return true;   // 单标签主机名，保持原有放行行为
        return false;
    }

    /** IPv6 只有 ::1 / fc00::/7 / fe80::/10 算内网，其余一律不是。 */
    private static boolean isPrivateIpv6(String h) {
        String s = h;
        int pct = s.indexOf('%');
        if (pct >= 0) s = s.substring(0, pct);
        if ("::1".equals(s) || "0:0:0:0:0:0:0:1".equals(s)) return true;
        String head = s;
        int firstColon = head.indexOf(':');
        if (firstColon >= 0) head = head.substring(0, firstColon);
        if (head.length() < 2) return false;
        String two = head.substring(0, 2);
        if ("fc".equals(two) || "fd".equals(two)) return true;
        if (head.length() >= 4 && head.startsWith("fe")) {
            try {
                int b = Integer.parseInt(head.substring(2, 4), 16);
                if (b >= 0x80 && b <= 0xbf) return true;
            } catch (NumberFormatException ignored) { }
        }
        return false;
    }

    public String token() { return sp.getString(K_TOKEN, ""); }
    public void setToken(String v) {
        sp.edit().putString(K_TOKEN, v == null ? "" : v).apply();
        syncLegacyIntoActive();
    }

    public String gatewayId() { return sp.getString(K_GATEWAY_ID, ""); }
    public void setGatewayId(String v) {
        sp.edit().putString(K_GATEWAY_ID, v == null ? "" : v).apply();
        syncLegacyIntoActive();
    }

    public String gatewayName() { return sp.getString(K_GATEWAY_NAME, ""); }
    public void setGatewayName(String v) {
        sp.edit().putString(K_GATEWAY_NAME, v == null ? "" : v).apply();
        syncLegacyIntoActive();
    }

    /** 安装级稳定 UUID，用于配对时复用可信设备记录。 */
    public String deviceId() {
        String v = sp.getString(K_DEVICE_ID, "");
        if (v == null || v.isEmpty()) {
            v = java.util.UUID.randomUUID().toString();
            sp.edit().putString(K_DEVICE_ID, v).apply();
        }
        return v;
    }

    public String deviceName() {
        String v = sp.getString(K_DEVICE_NAME, "");
        if (v == null || v.isEmpty()) {
            String model = Build.MANUFACTURER == null ? "Android" : Build.MANUFACTURER;
            String m = Build.MODEL == null ? "" : Build.MODEL;
            v = (model + " " + m).trim();
            if (v.length() > 40) v = v.substring(0, 40);
            if (v.isEmpty()) v = "Android";
        }
        return v;
    }

    public void setDeviceName(String v) { sp.edit().putString(K_DEVICE_NAME, v == null ? "" : v.trim()).apply(); }

    public boolean paired() { return !token().isEmpty() && !url().isEmpty(); }

    /** 对话显示模式：full（完整，默认） / compact（简洁，只显示正在运行什么）。 */
    public String displayMode() {
        String v = sp.getString("display_mode", "full");
        return "compact".equals(v) ? "compact" : "full";
    }

    public void setDisplayMode(String mode) {
        sp.edit().putString("display_mode", "compact".equals(mode) ? "compact" : "full").apply();
    }

    /**
     * 主题模式：{@code system}（跟随系统，默认） / {@code light}（浅色） / {@code dark}（深色）。
     * 只负责存取；「这一刻该用哪套色」由 {@code com.dsh.mobile.ui.Theme.resolveDark} 算。
     */
    public String themeMode() {
        return com.dsh.mobile.ui.Theme.normalize(sp.getString(K_THEME_MODE,
                com.dsh.mobile.ui.Theme.MODE_SYSTEM));
    }

    /**
     * 只读一次主题档位，**不构造 Store**。
     *
     * <p>{@code MainActivity.attachBaseContext} 要用它决定 App 自己的 uiMode（见
     * {@link com.dsh.mobile.ui.Theme#nightOverride}），而那个时刻越轻越好：构造 Store 会顺带
     * 刷一次网络状态、清洗一遍历史地址，那些是 onCreate 的活，不该在一个"只为读一个字符串"
     * 的调用里再做第二遍（还会多写一次磁盘）。
     */
    public static String themeModeOf(Context ctx) {
        if (ctx == null) return com.dsh.mobile.ui.Theme.MODE_SYSTEM;
        return com.dsh.mobile.ui.Theme.normalize(ctx.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(K_THEME_MODE, com.dsh.mobile.ui.Theme.MODE_SYSTEM));
    }

    public void setThemeMode(String mode) {
        sp.edit().putString(K_THEME_MODE, com.dsh.mobile.ui.Theme.normalize(mode)).apply();
    }

    /**
     * 一台已添加的电脑（「我的设备」页的一张卡片）。
     *
     * 为什么要有这个结构：旧版本只有"一台设备"的扁平字段（lan/wan/token…），
     * 而用户要的是"内网一次添加后长期保存 + 公网单独一个地址 + 能放多台"。
     * 现在设备表以 JSON 存在 SharedPreferences 里，**旧字段继续保留并镜像当前生效的那台**，
     * 于是 App 其余部分（Store.url()/token()/paired()）一行都不用改，老用户升级也不丢配对。
     */
    public static final class Device {
        /** 稳定 id；旧数据迁移过来时固定为 "legacy"，保证只迁移一次。 */
        public String id = "";
        /** 用户改过的显示名；空 = 用网关告诉的名字（{@link #displayName()}）。 */
        public String name = "";
        /** 平台/类型标签，如「Windows」「桌面端」；没有就留空、由 UI 兜底。 */
        public String platform = "";
        /** 内网地址：同一 WiFi 下用，一次添加后长期保存（网关内网口固定）。 */
        public String lanUrl = "";
        /** 公网地址：人在外面时用；隧道域名会变，失效时重新扫码更新。 */
        public String wanUrl = "";
        /**
         * 连接方式：{@link RoutePolicy#AUTO}（默认，按 WiFi/移动数据自动挑）/
         * {@link RoutePolicy#LAN}（只用内网）/ {@link RoutePolicy#WAN}（只用公网）。
         * 旧数据里的 useWan 布尔会在读取时归位（true -&gt; wan，false -&gt; auto）。
         */
        public String netMode = RoutePolicy.AUTO;
        public String token = "";
        public String gatewayId = "";
        /** 网关 hello/配对载荷里的 gatewayName，作为默认设备名。 */
        public String gatewayName = "";
        /** 网关 hello 里的 DSH 版本，连上过一次就记住，离线也能显示版本标签。 */
        public String dshVersion = "";
        /** 最近一次确认在线的时刻（用于卡片上的"上次在线"）。 */
        public long lastSeenAt = 0L;

        public String displayName() {
            if (name != null && !name.trim().isEmpty()) return name.trim();
            if (gatewayName != null && !gatewayName.trim().isEmpty()) return gatewayName.trim();
            return "我的电脑";
        }

        /**
         * 该设备当前该用的地址。判据与「显示的线路」共用 {@link Store#pickOf}：
         * 自动档下 WiFi 走（可用的）内网、移动数据/未连 WiFi 走公网，手动档按用户的显式选择。
         */
        public String activeUrl() {
            return Store.pickOf(this).url;
        }

        public boolean pairedReady() {
            return token != null && !token.isEmpty() && !activeUrl().isEmpty();
        }

        /**
         * 这台设备的「内网地址」是不是手机真的连得上（虚拟网卡 / APIPA / 回环 / 公网隧道都不算）。
         * 界面上的「内网 · 固定」标签、以及是否需要提示用户重扫，都按这个判定。
         */
        public boolean hasUsableLan() {
            return com.dsh.mobile.net.LanAddress.isUsableLanUrl(lanUrl);
        }
    }

    public String newDeviceId() {
        return "dev-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /** 设备表（首次调用时把旧的单设备字段迁移成一台设备，只做一次）。 */
    public java.util.List<Device> devices() {
        java.util.List<Device> list = loadDeviceList();
        if (list.isEmpty()) {
            Device legacy = legacyDevice();
            if (legacy != null) {
                list.add(legacy);
                saveDevices(list);
                sp.edit().putString(K_ACTIVE_DEVICE, legacy.id).apply();
            }
        }
        return list;
    }

    private java.util.List<Device> loadDeviceList() {
        java.util.List<Device> list = new java.util.ArrayList<>();
        String raw = sp.getString(K_DEVICES, "");
        if (raw == null || raw.isEmpty()) return list;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Device d = deviceFromJson(o);
                if (!d.id.isEmpty()) list.add(d);
            }
        } catch (Throwable ignored) { /* 坏了就当作空表，绝不让它崩掉启动页 */ }
        return list;
    }

    private static Device deviceFromJson(org.json.JSONObject o) {
        Device d = new Device();
        d.id = o.optString("id", "");
        d.name = o.optString("name", "");
        d.platform = o.optString("platform", "");
        d.lanUrl = o.optString("lan", "");
        d.wanUrl = o.optString("wan", "");
        // 旧数据只有 useWan 布尔：true 是「只用公网」，false 是老的「内网优先」= 新的自动档
        String m = o.optString("netMode", "");
        if (m == null || m.isEmpty()) m = o.optBoolean("useWan", false) ? RoutePolicy.WAN : RoutePolicy.AUTO;
        d.netMode = RoutePolicy.normalizeMode(m);
        d.token = o.optString("token", "");
        d.gatewayId = o.optString("gatewayId", "");
        d.gatewayName = o.optString("gatewayName", "");
        d.dshVersion = o.optString("dshVersion", "");
        d.lastSeenAt = o.optLong("lastSeenAt", 0L);
        return d;
    }

    private static org.json.JSONObject deviceToJson(Device d) {
        org.json.JSONObject o = new org.json.JSONObject();
        try {
            o.put("id", d.id);
            o.put("name", d.name == null ? "" : d.name);
            o.put("platform", d.platform == null ? "" : d.platform);
            o.put("lan", d.lanUrl == null ? "" : d.lanUrl);
            o.put("wan", d.wanUrl == null ? "" : d.wanUrl);
            o.put("netMode", RoutePolicy.normalizeMode(d.netMode));
            // 降级兼容：老版本只认 useWan 这个布尔，继续按「是不是手动只用公网」写一份
            o.put("useWan", RoutePolicy.WAN.equals(RoutePolicy.normalizeMode(d.netMode)));
            o.put("token", d.token == null ? "" : d.token);
            o.put("gatewayId", d.gatewayId == null ? "" : d.gatewayId);
            o.put("gatewayName", d.gatewayName == null ? "" : d.gatewayName);
            o.put("dshVersion", d.dshVersion == null ? "" : d.dshVersion);
            o.put("lastSeenAt", d.lastSeenAt);
        } catch (Throwable ignored) { }
        return o;
    }

    private void saveDevices(java.util.List<Device> list) {
        org.json.JSONArray arr = new org.json.JSONArray();
        if (list != null) for (Device d : list) arr.put(deviceToJson(d));
        // 设备表里含令牌，但和 K_TOKEN 一样只落在应用私有 SharedPreferences（不进日志、不进仓库）
        sp.edit().putString(K_DEVICES, arr.toString()).apply();
    }

    /** 旧版本的单设备字段 -> 一台设备；完全没有配过对时返回 null（不凭空造设备）。 */
    private Device legacyDevice() {
        String tk = sp.getString(K_TOKEN, "");
        String lan = sp.getString(K_LAN, "");
        String wan = sp.getString(K_WAN, "");
        String legacy = sp.getString(K_URL, "");
        if (tk.isEmpty() && lan.isEmpty() && wan.isEmpty() && legacy.isEmpty()) return null;
        Device d = new Device();
        d.id = "legacy";
        d.lanUrl = lan;
        d.wanUrl = wan;
        d.token = tk;
        d.netMode = RoutePolicy.normalizeMode(sp.getString(K_NET_MODE,
                sp.getBoolean(K_USE_WAN, false) ? RoutePolicy.WAN : RoutePolicy.AUTO));
        d.gatewayId = sp.getString(K_GATEWAY_ID, "");
        d.gatewayName = sp.getString(K_GATEWAY_NAME, "");
        // 更早的版本只写 K_URL：按地址性质归到内网/公网槽位，避免升级后地址消失
        if (!legacy.isEmpty()) {
            if (isPrivateUrl(legacy)) { if (d.lanUrl.isEmpty()) d.lanUrl = legacy; }
            else if (d.wanUrl.isEmpty()) d.wanUrl = legacy;
        }
        return d;
    }

    public String activeDeviceId() { return sp.getString(K_ACTIVE_DEVICE, ""); }

    public Device device(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Device d : loadDeviceList()) if (id.equals(d.id)) return d;
        return null;
    }

    /**
     * 当前生效的那台设备。设备表非空但 active 缺失/失效时（例如刚迁移、或选中的那台被删）
     * 自动落到第一台，避免"有设备却没有一台在用"。
     */
    public Device activeDevice() {
        java.util.List<Device> list = devices();
        if (list.isEmpty()) return null;
        String id = activeDeviceId();
        for (Device d : list) if (d.id.equals(id)) return d;
        Device first = list.get(0);
        setActiveDevice(first.id);
        return first;
    }

    /** 把某台设备设为当前生效：同时镜像进旧字段，让 Store.url()/token()/paired() 保持正确。 */
    public void setActiveDevice(String id) {
        Device d = device(id);
        if (d == null) return;
        sp.edit().putString(K_ACTIVE_DEVICE, d.id).apply();
        writeLegacy(d);
    }

    /** 直接写旧字段（**不**回调 syncLegacyIntoActive，避免与 setXxx 形成回环）。 */
    private void writeLegacy(Device d) {
        String active = d.activeUrl();
        String mode = RoutePolicy.normalizeMode(d.netMode);
        sp.edit()
                .putString(K_LAN, d.lanUrl == null ? "" : d.lanUrl)
                .putString(K_WAN, d.wanUrl == null ? "" : d.wanUrl)
                .putString(K_NET_MODE, mode)
                .putBoolean(K_USE_WAN, RoutePolicy.WAN.equals(mode))
                .putString(K_TOKEN, d.token == null ? "" : d.token)
                .putString(K_GATEWAY_ID, d.gatewayId == null ? "" : d.gatewayId)
                .putString(K_GATEWAY_NAME, d.gatewayName == null ? "" : d.gatewayName)
                .putString(K_URL, active)
                .apply();
    }

    /**
     * 开始一次扫码/粘贴配对：决定这次配对写进哪台设备，并把它设为当前生效。
     *
     *   同一台电脑（配对载荷里的 gatewayId 相同）重扫 = **更新它的地址**：
     *   公网隧道域名每次重启电脑都会变，重扫一次就更新，不会多出一张重复卡片；
     *   新电脑 = 新建一张卡片（旧的那台原样留着，两台都在设备表里）。
     *
     * 调用点必须在写入新地址之前，这样后续 setUrl/setLanUrl/setToken 都会落到这台设备上。
     */
    public String beginPairing(String gatewayId, String gatewayName) {
        java.util.List<Device> list = devices();
        boolean hasGw = gatewayId != null && !gatewayId.isEmpty();
        if (hasGw) {
            for (Device d : list) {
                if (gatewayId.equals(d.gatewayId)) {
                    if (gatewayName != null && !gatewayName.isEmpty()) d.gatewayName = gatewayName;
                    saveDevices(list);
                    sp.edit().putString(K_ACTIVE_DEVICE, d.id).apply();
                    writeLegacy(d);
                    return d.id;
                }
            }
        }
        Device d = new Device();
        d.id = newDeviceId();
        d.gatewayId = hasGw ? gatewayId : "";
        d.gatewayName = gatewayName == null ? "" : gatewayName;
        if (!hasGw && list.size() == 1) {
            // 老网关的配对载荷里没有 gatewayId：只有一台设备时按"重扫=更新它的地址"处理
            // （用户按提示重新扫码更新公网地址是常见路径，不该多出一张重复卡片）
            Device only = list.get(0);
            only.gatewayName = d.gatewayName.isEmpty() ? only.gatewayName : d.gatewayName;
            saveDevices(list);
            sp.edit().putString(K_ACTIVE_DEVICE, only.id).apply();
            writeLegacy(only);
            return only.id;
        }
        list.add(d);
        saveDevices(list);
        sp.edit().putString(K_ACTIVE_DEVICE, d.id).apply();
        writeLegacy(d);
        return d.id;
    }

    /** 新增或更新一台设备；makeActive=true 时顺带设为当前生效并镜像旧字段。 */
    public void upsertDevice(Device d, boolean makeActive) {
        if (d == null) return;
        if (d.id == null || d.id.isEmpty()) d.id = newDeviceId();
        java.util.List<Device> list = devices();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(d.id)) { list.set(i, d); replaced = true; break; }
        }
        if (!replaced) list.add(d);
        saveDevices(list);
        if (makeActive) {
            sp.edit().putString(K_ACTIVE_DEVICE, d.id).apply();
            writeLegacy(d);
        }
    }

    /**
     * 删除一台设备 = 清掉它的地址与令牌。
     * 删掉的是当前生效的那台时：还有别的设备就切到第一台（不自动重连，卡片上会显示离线，
     * 由用户点「连接」），否则把旧字段一并清空（回到"还没有设备"的空态）。
     */
    public void removeDevice(String id) {
        if (id == null || id.isEmpty()) return;
        java.util.List<Device> list = devices();
        Device removed = null;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) { removed = list.remove(i); break; }
        }
        if (removed == null) return;
        saveDevices(list);
        if (!id.equals(activeDeviceId())) return;
        if (list.isEmpty()) {
            sp.edit().remove(K_ACTIVE_DEVICE)
                    .remove(K_TOKEN).remove(K_GATEWAY_ID).remove(K_GATEWAY_NAME)
                    .remove(K_LAN).remove(K_WAN).remove(K_URL)
                    .putBoolean(K_USE_WAN, false).putString(K_NET_MODE, RoutePolicy.AUTO)
                    .apply();
        } else {
            setActiveDevice(list.get(0).id);
        }
    }

    /** 改设备显示名（空 = 恢复用网关告诉的名字）。 */
    public void renameDevice(String id, String name) {
        java.util.List<Device> list = devices();
        for (Device d : list) {
            if (d.id.equals(id)) {
                d.name = name == null ? "" : name.trim();
                saveDevices(list);
                return;
            }
        }
    }

    /**
     * 把网关 hello 告诉我们的身份/版本落进当前生效的设备。
     * dshVersion 记下来之后，离线状态下卡片也能显示版本标签。
     */
    public void updateActiveMeta(String gatewayId, String gatewayName, String dshVersion) {
        String id = activeDeviceId();
        if (id.isEmpty()) {
            // 还没迁移过：先按旧字段落成一台设备，再写元信息
            java.util.List<Device> list = devices();
            if (list.isEmpty()) return;
            id = activeDeviceId();
        }
        java.util.List<Device> list = devices();
        for (Device d : list) {
            if (!d.id.equals(id)) continue;
            boolean changed = false;
            if (gatewayId != null && !gatewayId.isEmpty() && !gatewayId.equals(d.gatewayId)) { d.gatewayId = gatewayId; changed = true; }
            if (gatewayName != null && !gatewayName.isEmpty() && !gatewayName.equals(d.gatewayName)) { d.gatewayName = gatewayName; changed = true; }
            if (dshVersion != null && !dshVersion.isEmpty() && !dshVersion.equals(d.dshVersion)) { d.dshVersion = dshVersion; changed = true; }
            if (changed) saveDevices(list);
            return;
        }
    }

    /** 记一次"当前这台确实在线"（卡片上的上次在线时间）。 */
    public void touchActiveSeen() {
        String id = activeDeviceId();
        if (id.isEmpty()) return;
        java.util.List<Device> list = devices();
        boolean changed = false;
        for (Device d : list) {
            if (!d.id.equals(id)) continue;
            d.lastSeenAt = System.currentTimeMillis();
            changed = true;
            break;
        }
        if (changed) saveDevices(list);
    }

    /**
     * 旧字段被别处改动（配对成功写入令牌、hello 写入网关名…）后，同步进当前生效的设备。
     * 旧字段是"当前生效设备"的镜像，两边必须一致，否则重启后设备卡片会显示旧值。
     */
    private void syncLegacyIntoActive() {
        String id = activeDeviceId();
        if (id.isEmpty()) {
            // 还没有"当前设备"（全新安装直接手填地址、或旧数据迁移之前的写入）：
            // 先让 devices() 把旧字段迁移成一台设备，迁移会顺手把 active 选上。
            java.util.List<Device> list = devices();
            if (list.isEmpty()) return;
            id = activeDeviceId();
            if (id.isEmpty()) { id = list.get(0).id; sp.edit().putString(K_ACTIVE_DEVICE, id).apply(); }
        }
        java.util.List<Device> list = loadDeviceList();
        if (list.isEmpty()) return;
        for (Device d : list) {
            if (!d.id.equals(id)) continue;
            d.lanUrl = lanUrl();
            d.wanUrl = wanUrl();
            d.netMode = netMode();
            d.token = token();
            d.gatewayId = gatewayId();
            d.gatewayName = gatewayName();
            saveDevices(list);
            return;
        }
    }

    /** 解绑：清掉 token 与网关身份，保留地址方便重新配对。 */
    public void clearPairing() {
        sp.edit().remove(K_TOKEN).remove(K_GATEWAY_ID).remove(K_GATEWAY_NAME).apply();
        syncLegacyIntoActive();
    }
}
