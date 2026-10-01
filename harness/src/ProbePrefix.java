import com.dsh.mobile.net.GatewayClient;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 修复前/后对照探针：只做「明文闸门」判定（不用 canSend，才能编到 49c0c81 的旧代码上）。
 * 用法: java -cp "<快照目录>;harness/lib/json-20240303.jar" ProbePrefix
 * 判定口径与 Probe.java 一致：状态序列里出现过 FAILED + "明文" 即为被拒。
 */
public final class ProbePrefix {

    static final class L implements GatewayClient.Listener {
        final java.util.List<String> seen = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
        @Override public void onState(GatewayClient.State s, String d) { seen.add(s + "·" + (d == null ? "" : d)); }
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

    private static String run(String url) {
        L l = new L();
        GatewayClient gw = new GatewayClient(l);
        gw.connect(url, "tok", "probe", "probe");
        try { Thread.sleep(350); } catch (InterruptedException ignored) { }
        gw.disconnect();
        try { Thread.sleep(150); } catch (InterruptedException ignored) { }
        synchronized (l.seen) {
            for (String s : l.seen) if (s.startsWith("FAILED") && s.contains("明文")) return "REJECT";
        }
        return "ALLOW";
    }

    public static void main(String[] args) {
        String[] urls = {
                "ws://[2001:4860:4860::8888]:3191/ws/mobile",
                "ws://2001:4860:4860::8888:3191/ws/mobile",
                "ws://[::1]:3999/ws/mobile",
                "ws://8.8.8.8:3999/ws/mobile",
                "ws://10.0.2.2:3999/ws/mobile",
        };
        System.out.println("url\t判定");
        for (String u : urls) System.out.println(u + "\t" + run(u));
    }
}
