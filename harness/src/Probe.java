import com.dsh.mobile.net.GatewayClient;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 纯 JVM 探针：独立核验 ce7afd8 的 P0-6（明文闸门 / IPv6 判定）与 P0-3 的前置原语 canSend()。
 * 不连真网关：只看 connect()/pair() 之后状态机与 canSend() 的取值。
 *
 * 用法: java -cp "harness/out-ce7afd8;harness/lib/json-20240303.jar" Probe
 */
public final class Probe {

    private static int pass = 0, fail = 0;

    private static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("  [PASS] " + name + (detail.isEmpty() ? "" : "  · " + detail)); }
        else { fail++; System.out.println("  [FAIL] " + name + "  · " + detail); }
    }

    /** 记录最近一次状态，供断言用。 */
    static final class L implements GatewayClient.Listener {
        volatile GatewayClient.State state = null;
        volatile String detail = "";
        /** shim 的 Handler 是 2 线程池，两次 post 的落地顺序不保证 → 必须收集全部状态，不能只看最后一次。 */
        final java.util.List<String> seen = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
        @Override public void onState(GatewayClient.State s, String d) {
            state = s; detail = d == null ? "" : d;
            seen.add(s + "·" + detail);
        }
        @Override public void onHello(JSONObject h) { }
        @Override public void onPaired(JSONObject p) { }
        @Override public void onSessions(JSONArray i, JSONObject r) { }
        @Override public void onHistory(String s, JSONArray e, JSONObject m) { }
        @Override public void onSnapshot(String s, JSONObject j) { }
        @Override public void onAssistantStream(JSONObject f) { }
        @Override public void onEvent(String s, JSONObject e, Object q, Object t) { }
        @Override public void onApprovalRequested(JSONObject f) { }
        @Override public void onQuestionRequested(JSONObject f) { }
        @Override public void onInteractionResolved(JSONObject f) { }
        @Override public void onSent(String s, JSONObject r) { }
        @Override public void onProtocolError(String c, String m, String r, String s) { }
        @Override public void onOther(String k, JSONObject f) { }
        @Override public void onAttachment(String s, String a, String m, String b) { }
        @Override public void onDownload(String k, JSONObject f) { }
    }

    /** 起一个连接尝试，等 400ms 让状态回调落地（shim 的 Handler 是异步线程池）。 */
    private static L tryConnect(String url) {
        L l = new L();
        GatewayClient gw = new GatewayClient(l);
        gw.connect(url, "tok", "probe", "probe");
        try { Thread.sleep(400); } catch (InterruptedException ignored) { }
        gw.disconnect();
        return l;
    }

    private static L tryPair(String url) {
        L l = new L();
        GatewayClient gw = new GatewayClient(l);
        gw.pair(url, "code0001", "probe", "probe");
        try { Thread.sleep(400); } catch (InterruptedException ignored) { }
        gw.disconnect();
        return l;
    }

    /** 是否在整段状态序列里出现过"被明文闸门拒绝"（不能只看最后一次：后面还有 disconnect 的 DISCONNECTED）。 */
    private static boolean denied(L l) {
        synchronized (l.seen) {
            for (String s : l.seen) if (s.startsWith("FAILED") && s.contains("明文")) return true;
        }
        return false;
    }

    private static String seq(L l) {
        synchronized (l.seen) { return String.join(" | ", l.seen); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== P0-6 明文闸门（公网 IPv6 必须拒；内网必须放行）===");

        // 1. 公网 IPv6 字面量（带方括号）—— 评审 A2 的原始反例
        L a = tryConnect("ws://[2001:4860:4860::8888]:3191/ws/mobile");
        check("ws://[2001:4860:4860::8888] 被明文闸门拒绝", denied(a),
                "状态序列=" + seq(a));

        // 2. 公网 IPv6（不带方括号，含多个冒号）—— 修复前会被截成 "2001" 单标签主机名放行
        L b = tryConnect("ws://2001:4860:4860::8888:3191/ws/mobile");
        check("无括号 IPv6 不被截断成单标签主机名放行", denied(b),
                "状态序列=" + seq(b));

        // 3. IPv6 回环 ::1 —— 必须仍放行（内网）
        L c = tryConnect("ws://[::1]:3999/ws/mobile");
        check("ws://[::1] 仍放行（内网）", !denied(c), "state=" + c.state + " detail=" + c.detail);

        // 4. IPv6 唯一本地 fc00::/7
        L d = tryConnect("ws://[fd00::1]:3999/ws/mobile");
        check("ws://[fd00::1] 仍放行（内网 fc00::/7）", !denied(d), "state=" + d.state);

        // 5. IPv6 链路本地 fe80::/10
        L e = tryConnect("ws://[fe80::1]:3999/ws/mobile");
        check("ws://[fe80::1] 仍放行（内网 fe80::/10）", !denied(e), "state=" + e.state);

        // 6. IPv4 内网 10.0.2.2（模拟器访问宿主机）
        L f = tryConnect("ws://10.0.2.2:3999/ws/mobile");
        check("ws://10.0.2.2 放行（模拟器->宿主机）", !denied(f), "state=" + f.state);

        // 7. IPv4 公网 8.8.8.8
        L g = tryConnect("ws://8.8.8.8:3999/ws/mobile");
        check("ws://8.8.8.8 被拒（公网明文）", denied(g), "state=" + g.state);

        // 8. 内网 C 段
        L h = tryConnect("ws://192.168.1.10:3999/ws/mobile");
        check("ws://192.168.1.10 放行（内网）", !denied(h), "state=" + h.state);

        // 9. wss:// 公网必须放行（加密传输合法）
        L i = tryConnect("wss://example.com/ws/mobile");
        check("wss://example.com 放行（加密）", !denied(i), "state=" + i.state);

        // 10. pair() 入口同样过闸门（评审 P0-6 的"下沉到 GatewayClient"）
        L j = tryPair("ws://[2606:4700:4700::1111]:3191/ws/mobile");
        check("pair() 也过明文闸门（公网 IPv6 被拒）", denied(j), "状态序列=" + seq(j));

        System.out.println("\n=== P0-3 前置原语 canSend()（断线时不得谎报可发送）===");
        L k = new L();
        GatewayClient gw = new GatewayClient(k);
        check("未连接时 canSend()=false", !gw.canSend(), "canSend=" + gw.canSend());
        gw.connect("ws://[2001:4860:4860::8888]:3191/ws/mobile", "tok", "probe", "probe");
        Thread.sleep(300);
        check("明文被拒后 canSend() 仍=false", !gw.canSend(), "canSend=" + gw.canSend());
        gw.disconnect();
        Thread.sleep(200);
        check("disconnect() 后 canSend()=false", !gw.canSend(), "canSend=" + gw.canSend());

        System.out.println("\n=== 结果: " + pass + " 通过 / " + fail + " 失败 ===");
        System.exit(fail == 0 ? 0 : 1);
    }
}

