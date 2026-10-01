package com.dsh.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * 本地持久化：网关地址、设备 token、安装级设备 ID、设备名。
 * token 只保存在应用私有 SharedPreferences 中，不落日志。
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
    private static final String K_USE_WAN = "use_wan";
    private static final String K_INSECURE_TLS = "insecure_tls";
    private static final String K_FEEDBACK = "feedback_log";
    private static final String K_RISK_ACK = "risk_ack";
    private static final String K_SKIP_VERSION = "skip_version";
    private static final String K_LAST_UPDATE_CHECK = "last_update_check";

    private final SharedPreferences sp;
    /** 会话标题缓存：网关的 sessions 列表不含 title，标题从历史里的 session/title 事件抽取后落盘。 */
    private final SharedPreferences titles;

    public Store(Context ctx) {
        this.sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        this.titles = ctx.getApplicationContext().getSharedPreferences("dsh_mobile_titles", Context.MODE_PRIVATE);
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

    public String lanUrl() { return sp.getString(K_LAN, ""); }
    public void setLanUrl(String v) { sp.edit().putString(K_LAN, v == null ? "" : v.trim()).apply(); }

    public String wanUrl() { return sp.getString(K_WAN, ""); }
    public void setWanUrl(String v) { sp.edit().putString(K_WAN, v == null ? "" : v.trim()).apply(); }

    /** 自建反代自签名证书时允许不校验证书。 */
    public boolean insecureTls() { return sp.getBoolean(K_INSECURE_TLS, false); }
    public void setInsecureTls(boolean v) { sp.edit().putBoolean(K_INSECURE_TLS, v).apply(); }

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

    public boolean useWan() { return sp.getBoolean(K_USE_WAN, false); }
    public void setUseWan(boolean v) { sp.edit().putBoolean(K_USE_WAN, v).apply(); }

    /** 当前生效的地址：选了公网且填了公网就用公网，否则用内网（兜底旧字段）。 */
    public String url() {
        String lan = lanUrl();
        String wan = wanUrl();
        if (useWan() && !wan.isEmpty()) return wan;
        if (!lan.isEmpty()) return lan;
        if (!wan.isEmpty()) return wan;
        return sp.getString(K_URL, "");
    }

    /** 配对/手填时按地址性质归位到内网或公网槽位。 */
    public void setUrl(String v) {
        String u = v == null ? "" : v.trim();
        sp.edit().putString(K_URL, u).apply();
        if (u.isEmpty()) return;
        if (isPrivateUrl(u)) setLanUrl(u);
        else setWanUrl(u);
    }

    /** 私有地址判定：无点的主机名、10./127./169.254./192.168./172.16-31、.local/.lan。 */
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
        else { int c = host.indexOf(':'); if (c >= 0) host = host.substring(0, c); }
        host = host.toLowerCase(java.util.Locale.ROOT);
        if (host.isEmpty()) return true;
        if ("localhost".equals(host) || host.endsWith(".local") || host.endsWith(".lan")
                || host.endsWith(".ts.net")) return true;
        if (host.indexOf('.') < 0) return true;
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
        return false;
    }

    public String token() { return sp.getString(K_TOKEN, ""); }
    public void setToken(String v) { sp.edit().putString(K_TOKEN, v == null ? "" : v).apply(); }

    public String gatewayId() { return sp.getString(K_GATEWAY_ID, ""); }
    public void setGatewayId(String v) { sp.edit().putString(K_GATEWAY_ID, v == null ? "" : v).apply(); }

    public String gatewayName() { return sp.getString(K_GATEWAY_NAME, ""); }
    public void setGatewayName(String v) { sp.edit().putString(K_GATEWAY_NAME, v == null ? "" : v).apply(); }

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

    /** 解绑：清掉 token 与网关身份，保留地址方便重新配对。 */
    public void clearPairing() {
        sp.edit().remove(K_TOKEN).remove(K_GATEWAY_ID).remove(K_GATEWAY_NAME).apply();
    }
}
