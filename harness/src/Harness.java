import com.dsh.mobile.net.GatewayClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * dsh-mobile-v1 联调 harness。
 *
 * 直接复用 App 的 net 层源码（WsClient + GatewayClient），在 JVM 上跑完整流程：
 *   阶段1 用一次性配对码配对 -> 拿长期设备 token
 *   阶段2 断开，改用设备 token 重连（App 日常走的路径）
 *   阶段3 拉会话列表 -> 订阅（assistantStream）-> 发消息 -> 观察实时流与持久事件
 *
 * 用法: java Harness <pairing.txt 路径>
 */
public final class Harness implements GatewayClient.Listener {

    private static final long START = System.currentTimeMillis();

    private static GatewayClient gw;
    private static String token = "";
    private static String deviceId = "jvm-harness-0001";
    private static boolean phase2Started = false;
    private static boolean messageSent = false;
    private static String sessionId = "";

    private static int chunkCount = 0;
    private static int eventCount = 0;
    private static int toolCalls = 0;
    private static int errors = 0;
    private static int approvals = 0;
    private static int questions = 0;
    private static final StringBuilder streamText = new StringBuilder();
    private static String lastEventType = "";

    private static String prompt = "只回复四个字：联调成功";
    private static boolean autoApprove = false;

    private static void log(String s) {
        System.out.println("[" + (System.currentTimeMillis() - START) + "ms] " + s);
    }

    public static void main(String[] args) throws Exception {
        String pairingFile = args.length > 0 ? args[0] : "C:/dshverify/pairing.txt";
        if (args.length > 1) prompt = args[1];
        if (args.length > 2) autoApprove = "approve".equalsIgnoreCase(args[2]);
        String raw = new String(Files.readAllBytes(Paths.get(pairingFile)), StandardCharsets.US_ASCII).trim();

        byte[] decoded = Base64.getUrlDecoder().decode(raw);
        JSONObject payload = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
        String url = payload.getString("publicUrl");
        String code = payload.getString("pairingCode");

        log("配对串解码成功: version=" + payload.optInt("version")
                + " publicUrl=" + url
                + " code=" + code.substring(0, 8) + "..."
                + " gatewayId=" + payload.optString("gatewayId", "-"));

        gw = new GatewayClient(new Harness());

        // 看门狗：到点收尾并输出统计。必须是非 daemon，
        // 否则 main() 返回后 JVM 会因只剩 daemon 线程而直接退出。
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(150_000L); } catch (InterruptedException ignored) { }
            summary();
            System.exit(errors > 0 ? 1 : 0);
        });
        watchdog.start();

        log("阶段1：用配对码建立连接并配对");
        gw.pair(url, code, deviceId, "JVM Harness");
    }

    private static void summary() {
        System.out.println();
        System.out.println("================ 联调结果 ================");
        System.out.println("assistant-stream 增量帧 : " + chunkCount);
        System.out.println("持久 event 帧          : " + eventCount + " (最后: " + lastEventType + ")");
        System.out.println("tool/call 次数         : " + toolCalls);
        System.out.println("审批请求               : " + approvals);
        System.out.println("提问请求               : " + questions);
        System.out.println("协议错误               : " + errors);
        System.out.println("设备 token             : " + (token.isEmpty() ? "(未取得)" : token.substring(0, 8) + "..."));
        System.out.println("会话 ID                : " + (sessionId.isEmpty() ? "(未建立)" : sessionId));
        String t = streamText.toString();
        if (t.length() > 400) t = t.substring(0, 400) + "...";
        System.out.println("流式文本片段           : " + (t.isEmpty() ? "(无)" : t.replace("\n", "\\n")));
        System.out.println("==========================================");
    }

    // ============================================================ 回调

    @Override
    public void onState(GatewayClient.State state, String detail) {
        log("state=" + state + " · " + detail);
    }

    @Override
    public void onHello(JSONObject hello) {
        log("HELLO protocol=" + hello.optInt("protocol")
                + " dshVersion=" + hello.optString("dshVersion", "-")
                + " historyFormatVersion=" + hello.optInt("historyFormatVersion", -1)
                + " authenticated=" + hello.optBoolean("authenticated")
                + " port=" + hello.optInt("port"));
        JSONArray caps = hello.optJSONArray("capabilities");
        StringBuilder sb = new StringBuilder();
        if (caps != null) for (int i = 0; i < caps.length(); i++) { if (sb.length() > 0) sb.append(','); sb.append(caps.optString(i)); }
        log("capabilities: " + sb);
        JSONObject dev = hello.optJSONObject("device");
        if (dev != null) log("device: id=" + dev.optString("id") + " name=" + dev.optString("name"));
        gw.requestSessions();
    }

    @Override
    public void onPaired(JSONObject paired) {
        token = paired.optString("token", "");
        log("PAIRED 取得长期 token=" + (token.isEmpty() ? "(空!)" : token.substring(0, 8) + "...")
                + " device=" + paired.optJSONObject("device"));
        if (token.isEmpty()) { errors++; return; }

        // 阶段2：断开后用 token 重连，验证 App 日常鉴权路径
        if (!phase2Started) {
            phase2Started = true;
            new Thread(() -> {
                sleep(600);
                log("阶段2：断开，改用设备 token 重连");
                gw.disconnect();
                sleep(600);
                gw.connect(gw.url(), token, deviceId, "JVM Harness");
            }).start();
        }
    }

    @Override
    public void onSessions(JSONArray items, JSONObject raw) {
        int n = items == null ? 0 : items.length();
        log("SESSIONS 共 " + n + " 个");
        if (items != null) {
            for (int i = 0; i < Math.min(n, 5); i++) {
                JSONObject s = items.optJSONObject(i);
                if (s == null) continue;
                log("  · " + s.optString("sessionId") + "  title=" + s.optString("title", "-")
                        + " cwd=" + s.optString("cwd", "-")
                        + " running=" + s.optBoolean("running") + " blank=" + s.optBoolean("blank"));
            }
        }
        if (!messageSent) {
            messageSent = true;
            log("阶段3：发消息（省略 sessionId → 自动创建新会话）: " + prompt);
            gw.sendMessage("", prompt);
        }
    }

    @Override
    public void onHistory(String sid, JSONArray events, JSONObject meta) {
        log("HISTORY session=" + sid + " events=" + (events == null ? 0 : events.length())
                + " hasMore=" + meta.optBoolean("hasMore") + " fmt=" + meta.optInt("historyFormatVersion", -1));
    }

    @Override
    public void onSnapshot(String sid, JSONObject snap) {
        JSONArray ev = snap.optJSONArray("events");
        log("SNAPSHOT session=" + sid + " events=" + (ev == null ? 0 : ev.length())
                + " cursor=" + snap.opt("cursor") + " replace=" + snap.optBoolean("replace")
                + " hasMore=" + snap.optBoolean("hasMore")
                + " streamId=" + snap.optString("streamId", "-"));
        JSONObject stream = snap.optJSONObject("assistantStream");
        if (stream != null && stream.optJSONObject("activeAttempt") != null) {
            log("  活动 attempt 已恢复 (revision=" + stream.optInt("revision") + ")");
        }
    }

    @Override
    public void onAssistantStream(JSONObject frame) {
        chunkCount++;
        JSONObject f = frame.optJSONObject("frame");
        if (f == null) return;
        String type = f.optString("type", "");
        if ("chunk".equals(type)) {
            JSONObject chunk = f.optJSONObject("chunk");
            if (chunk != null) {
                String ct = chunk.optString("type", "");
                String tx = chunk.optString("text", "");
                if (tx != null && !tx.isEmpty()) streamText.append(tx);
                if (chunkCount <= 6) log("  流增量 #" + chunkCount + " " + ct + " => " + abbreviate(tx));
            }
        } else {
            log("  流帧 " + type + " outcome=" + f.opt("outcome"));
        }
    }

    @Override
    public void onEvent(String sid, JSONObject event, Object seq, Object time) {
        eventCount++;
        String type = event == null ? "?" : event.optString("type", "?");
        lastEventType = type;
        if ("tool/call".equals(type)) toolCalls++;
        if (eventCount <= 25 || "assistant/message".equals(type) || "turn/end".equals(type)) {
            String extra = "";
            if (event != null) {
                if (event.has("text")) extra = abbreviate(event.optString("text", ""));
                else if ("tool/call".equals(type)) extra = event.optString("name", "");
                else if ("tool/result".equals(type)) extra = "isError=" + event.optBoolean("isError");
                else if ("user/message".equals(type)) extra = abbreviate(event.optString("text", ""));
            }
            log("  event[" + seq + "] " + type + " " + extra);
        }
    }

    @Override
    public void onApprovalRequested(JSONObject frame) {
        approvals++;
        String rpcId = frame.optString("rpcId");
        String sid = frame.optString("sessionId");
        String approvalId = frame.optString("approvalId");
        log("!! APPROVAL 请求 tool=" + frame.optString("toolName")
                + " reason=" + frame.optString("reason")
                + " approvalId=" + approvalId + " rpcId=" + rpcId);
        if (autoApprove) {
            log("   → 提交 approval-response outcome=allowed-once（App 审批卡片的路径）");
            gw.approvalResponse(rpcId, sid, approvalId, "allowed-once");
        }
    }

    @Override
    public void onQuestionRequested(JSONObject frame) {
        questions++;
        log("!! QUESTION 请求 rpcId=" + frame.optString("rpcId")
                + " questions=" + frame.optJSONArray("questions"));
    }

    @Override
    public void onInteractionResolved(JSONObject frame) {
        log("  交互帧 " + frame.optString("kind") + " rpcId=" + frame.optString("rpcId")
                + " outcome=" + frame.optString("outcome") + " accepted=" + frame.opt("accepted"));
    }

    @Override
    public void onSent(String sid, JSONObject raw) {
        sessionId = sid;
        log("SENT sessionId=" + sid + " mode=" + raw.optString("mode"));
        log("阶段3b：订阅该会话（assistantStream=true）");
        gw.subscribe(sid);
    }

    @Override
    public void onProtocolError(String code, String message, String requestType, String sid) {
        errors++;
        log("ERROR code=" + code + " message=" + message + " requestType=" + requestType + " sessionId=" + sid);
    }

    @Override
    public void onAttachment(String sessionId, String attachmentId, String mediaType, String base64) {
        log("ATTACHMENT " + attachmentId + " " + mediaType);
    }

    @Override
    public void onDownload(String kind, JSONObject frame) {
        log("DOWNLOAD " + kind + " " + frame);
    }

    @Override
    public void onOther(String kind, JSONObject frame) {
        if ("subscribed".equals(kind)) {
            log("SUBSCRIBED session=" + frame.optString("sessionId")
                    + " assistantStream=" + frame.opt("assistantStream")
                    + " subscriptionId=" + frame.optString("subscriptionId"));
            return;
        }
        if ("projection-baseline".equals(kind)) return;
        if ("pong".equals(kind)) return;
        log("  其他帧 " + kind + " " + abbreviate(frame.toString()));
    }

    // ============================================================ 工具

    private static String abbreviate(String s) {
        if (s == null) return "";
        s = s.replace("\n", "\\n");
        return s.length() > 90 ? s.substring(0, 90) + "..." : s;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
