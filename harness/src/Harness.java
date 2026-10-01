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
    /** 已用设备 token 重新发起连接（阶段3 必须等这一步之后才开始，否则消息会被阶段2 的断开打断）。 */
    private static volatile boolean phase2Reconnected = false;
    private static volatile boolean phase3Started = false;
    private static boolean messageSent = false;
    private static String sessionId = "";

    private static int chunkCount = 0;
    private static int eventCount = 0;
    private static int toolCalls = 0;
    private static int errors = 0;
    private static int approvals = 0;
    private static int questions = 0;
    private static int helloCount = 0;
    private static int reconnectScheduled = 0;
    private static int streamResets = 0;
    private static int streamResetsRetrying = 0;
    private static int streamResetsTerminal = 0;
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
        // 时长可用 -Dharness.maxMs=45000 覆盖（故障注入时逐档调整，避免每档都等 150s）。
        long maxMs = Long.getLong("harness.maxMs", 150_000L);
        log("看门狗 " + (maxMs / 1000) + "s");
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(maxMs); } catch (InterruptedException ignored) { }
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
        System.out.println("hello 次数             : " + helloCount);
        System.out.println("安排重连次数           : " + reconnectScheduled);
        System.out.println("session-stream-reset   : " + streamResets
                + " (retrying=true " + streamResetsRetrying + " / 其余 " + streamResetsTerminal + ")");
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
        helloCount++;
        log("HELLO#" + helloCount + " protocol=" + hello.optInt("protocol")
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
        // 阶段2：配对成功后立刻断开、改用设备 token 重连，验证 App 日常走的鉴权路径。
        // 阶段3（订阅/发消息）必须等 token 连接握手完成后才开始。
        if (!phase2Reconnected) {
            log("  （配对连接的首个 hello：先不进入阶段3，等阶段2 用 token 重连）");
            return;
        }
        if (phase3Started) return;
        phase3Started = true;
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
                sleep(200);
                log("阶段2：断开，改用设备 token 重连");
                gw.disconnect();
                sleep(500);
                phase2Reconnected = true;
                gw.connect(gw.url(), token, deviceId, "JVM Harness");
            }).start();
        }
    }

    /**
     * 兼容桥：旧版 net 源码（harness/snapshot/ce7afd8）的 Listener 只有这个 3 参方法。
     * 故意不加 @Override —— 这样同一份 Harness 既能编当前 src（4 参是真覆盖），
     * 也能编旧快照（这里的 4 参只是多出来的普通方法，不会编译失败）。
     */
    @Override
    public void onStreamReset(String sid, String code, String message) {
        streamResets++;
        streamResetsTerminal++;
        log("!! STREAM-RESET session=" + sid + " code=" + code + " message=" + message
                + " retrying=(旧签名/未提供)");
    }

    /**
     * 带 retrying 的 4 参重载（当前 src 的 dispatch 走这条）。retrying=true 表示网关只是
     * 瞬时中断、follower 会自动重开流；false 才是终态。两条分支都必须被真正测到。
     */
    public void onStreamReset(String sid, String code, String message, boolean retrying) {
        streamResets++;
        if (retrying) streamResetsRetrying++; else streamResetsTerminal++;
        log("!! STREAM-RESET session=" + sid + " code=" + code + " message=" + message
                + " retrying=" + retrying);
    }

    @Override
    public void onReconnectScheduled(String reason) {
        reconnectScheduled++;
        log(">> 安排重连 #" + reconnectScheduled + " · " + reason);
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
        if (streamResets > 0) log("  （断流后）流帧 #" + chunkCount + " " + type
                + " attemptId=" + f.optString("attemptId", "-"));
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
        if (autoApprove) {
            JSONArray answers = new JSONArray();
            JSONObject a = new JSONObject();
            try {
                a.put("id", "q1");
                a.put("selected", new JSONArray().put("A"));
            } catch (Exception ignored) { }
            answers.put(a);
            log("   → 提交 question-answer（App 提问卡片的路径）selected=[A]");
            gw.questionAnswer(frame.optString("rpcId"), frame.optString("sessionId"), answers);
        }
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
        log("阶段4：请求文件下载（file-download-open → read 循环 → eof+sha256）");
        gw.fileDownloadOpen(sid, "/mock/big.bin", "req-mock-1");
    }

    // ============================================================ 文件下载链路
    private static final java.io.ByteArrayOutputStream dlBuf = new java.io.ByteArrayOutputStream();
    private static String dlTransferId = "";
    private static String dlExpectedSha = null;
    private static int dlChunks = 0;
    private static boolean dlDone = false;

    @Override
    public void onDownload(String kind, JSONObject frame) {
        switch (kind) {
            case "file-download-opened":
                dlTransferId = frame.optString("transferId");
                log("DOWNLOAD opened transferId=" + dlTransferId
                        + " name=" + frame.optString("name")
                        + " size=" + frame.optLong("size")
                        + " chunkBytes=" + frame.optLong("chunkBytes"));
                gw.fileDownloadRead(dlTransferId, 0);
                break;
            case "file-download-chunk": {
                dlChunks++;
                try {
                    byte[] b = java.util.Base64.getDecoder().decode(frame.optString("data", ""));
                    dlBuf.write(b);
                } catch (Exception e) {
                    log("!! DOWNLOAD 分块 base64 解码失败: " + e);
                }
                long offset = frame.optLong("offset");
                if (dlChunks <= 3 || frame.optBoolean("eof")) {
                    log("DOWNLOAD chunk#" + dlChunks + " offset=" + offset
                            + " bytes=" + dlBuf.size() + " eof=" + frame.optBoolean("eof"));
                }
                if (frame.optBoolean("eof")) {
                    dlDone = true;
                    dlExpectedSha = frame.optString("sha256", null);
                    String actual = sha256(dlBuf.toByteArray());
                    boolean ok = dlExpectedSha == null || dlExpectedSha.isEmpty() || dlExpectedSha.equalsIgnoreCase(actual);
                    log("DOWNLOAD 完成 共 " + dlChunks + " 块 / " + dlBuf.size() + " 字节 · sha256 校验="
                            + (dlExpectedSha == null || dlExpectedSha.isEmpty() ? "无期望值(跳过)" : (ok ? "通过" : "失败")));
                    if (!ok) { errors++; log("!! 期望 " + dlExpectedSha + " 实际 " + actual); }
                } else {
                    gw.fileDownloadRead(dlTransferId, offset + 0);
                }
                break;
            }
            case "file-download-cancelled":
                log("DOWNLOAD 已取消 transferId=" + frame.optString("transferId"));
                break;
            default:
                log("DOWNLOAD " + kind + " " + frame);
        }
    }

    private static String sha256(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(data)) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
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
