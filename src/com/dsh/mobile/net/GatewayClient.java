package com.dsh.mobile.net;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * dsh-mobile-v1 协议客户端。
 * 负责：配对 / 鉴权连接 / 心跳 / 自动重连 / 请求-推送分发。
 * 协议参考：vendor/mgw/package/PROTOCOL.md
 */
public final class GatewayClient {

    public enum State { DISCONNECTED, CONNECTING, AUTHENTICATING, READY, UNAUTHORIZED, GATEWAY_OFF, FAILED }

    public interface Listener {
        void onState(State state, String detail);
        void onHello(JSONObject hello);
        void onPaired(JSONObject paired);
        void onSessions(JSONArray items, JSONObject raw);
        void onHistory(String sessionId, JSONArray events, JSONObject meta);
        void onSnapshot(String sessionId, JSONObject snapshot);
        void onAssistantStream(JSONObject frame);
        void onEvent(String sessionId, JSONObject event, Object seq, Object time);
        void onApprovalRequested(JSONObject frame);
        void onQuestionRequested(JSONObject frame);
        void onInteractionResolved(JSONObject frame);
        void onSent(String sessionId, JSONObject raw);
        void onProtocolError(String code, String message, String requestType, String sessionId);
        void onOther(String kind, JSONObject frame);
        void onAttachment(String sessionId, String attachmentId, String mediaType, String base64);

        /** 文件下载事件：kind 为 file-download-opened / file-download-chunk / file-download-cancelled。 */
        void onDownload(String kind, JSONObject frame);

        /** 因失败而安排重连时回调（用于自动切换内网/公网）。 */
        default void onReconnectScheduled(String reason) { }

        /** 网关中断了正在进行的会话流（session-stream-reset）：摘掉流式气泡并复位"运行中"。 */
        default void onStreamReset(String sessionId, String code, String message) { }
    }

    private static final String PROTO = "dsh-mobile-v1";
    private static final long PING_INTERVAL_MS = 25_000L;
    /**
     * 假连接判定阈值：连续 3 个 ping 周期（75s）没有收到任何入站帧（含 pong / 服务端 ping）
     * 就认为链路已死。手机侧仍显示"已连接"但对端早就不在了，是评审 P0-1 的核心症状。
     */
    private static final long STALE_INBOUND_MS = 75_000L;

    private volatile Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** 代际：每次 open() 递增；旧连接的迟到回调据此丢弃，避免重连风暴。 */
    private volatile int generation = 0;

    public void setListener(Listener l) { this.listener = l; }
    public Listener listener() { return listener; }
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);

    private WsClient ws;
    private String url = "";
    private String token = "";
    private String pairingCode = "";
    private String deviceId = "";
    private String deviceName = "";
    private boolean wantConnected;
    private boolean manualClose;
    private long pingTimer;

    private volatile State state = State.DISCONNECTED;

    private boolean trustAllCerts = false;

    public GatewayClient(Listener listener) { this.listener = listener; }

    /** 自建反代的证书自签时置 true（不校验证书）。 */
    public void setTrustAllCerts(boolean value) { this.trustAllCerts = value; }

    public boolean trustAllCerts() { return trustAllCerts; }

    public State state() { return state; }
    public String url() { return url; }

    // ------------------------------------------------------------ 连接

    /** 使用长期设备 token 建立已鉴权连接。 */
    public void connect(String serverUrl, String deviceToken, String devId, String devName) {
        this.url = serverUrl == null ? "" : serverUrl.trim();
        this.token = deviceToken == null ? "" : deviceToken;
        this.pairingCode = "";
        this.deviceId = devId == null ? "" : devId;
        this.deviceName = devName == null ? "" : devName;
        this.manualClose = false;
        this.wantConnected = false;
        reconnectAttempt.set(0);
        if (!guardCleartext(this.url)) return;
        this.wantConnected = true;
        open();
    }

    /** 用一次性配对码配对（成功后服务端下发长期 token）。 */
    public void pair(String serverUrl, String code, String devId, String devName) {
        this.url = serverUrl == null ? "" : serverUrl.trim();
        this.token = "";
        this.pairingCode = code == null ? "" : code;
        this.deviceId = devId == null ? "" : devId;
        this.deviceName = devName == null ? "" : devName;
        this.manualClose = false;
        this.wantConnected = false;
        reconnectAttempt.set(0);
        if (!guardCleartext(this.url)) return;
        this.wantConnected = true;
        open();
    }

    /**
     * 明文放行的统一闸门：所有连接入口（手动保存、端点切换、故障切换、配对候选）
     * 都要经过这里。不合法就不连、也不安排自动重连，只把原因写进状态给 UI。
     * 正常的内网 ws://192.168.x / ws://localhost 仍会被 cleartextProblem 放行。
     */
    private boolean guardCleartext(String target) {
        String problem = cleartextProblem(target);
        if (problem == null) return true;
        stopPing();
        main.removeCallbacks(reconnectTask);
        WsClient c = ws;
        ws = null;
        if (c != null) c.close(1000, "cleartext denied");
        setState(State.FAILED, problem);
        return false;
    }

    /**
     * 当前连接是否真的能把帧发出去（UI 在「操作成功」前判断，避免断网时谎报成功）。
     * 除了 ws 非空且未关闭，还要求已经握手完成（READY）：重连窗口里 ws 可能已存在但
     * 还没拿回 hello，这时 sendText 会被静默丢进未就绪的写队列 —— 正是要修的那种"谎报"。
     */
    public boolean canSend() {
        WsClient c = ws;
        return c != null && !c.isClosed() && state == State.READY;
    }

    /** 用户是否还希望保持连接（disconnect() 之后为 false）；供网络变化时判断要不要补一次重连。 */
    public boolean wantConnected() { return wantConnected; }

    public void disconnect() {
        wantConnected = false;
        manualClose = true;
        generation++;
        main.removeCallbacks(reconnectTask);
        stopPing();
        WsClient c = ws;
        ws = null;
        if (c != null) c.close(1000, "bye");
        setState(State.DISCONNECTED, "已断开");
    }

    private final Runnable reconnectTask = () -> {
        if (wantConnected && !manualClose) open();
    };

    private void open() {
        stopPing();
        main.removeCallbacks(reconnectTask);
        final int gen = ++generation;
        WsClient old = ws;
        ws = null;
        if (old != null) old.close(1000, "reconnect");

        setState(State.CONNECTING, "正在连接 " + url);

        List<String> protos = new ArrayList<>();
        protos.add(PROTO);
        Map<String, String> headers = new HashMap<>();
        if (!deviceId.isEmpty()) headers.put("X-DSH-Device-ID", deviceId);

        if (!pairingCode.isEmpty()) {
            protos.add("dsh-pair." + pairingCode);
        } else if (!token.isEmpty()) {
            protos.add("dsh-auth." + token);
            headers.put("Authorization", "Bearer " + token);
        } else {
            setState(State.UNAUTHORIZED, "缺少设备凭证，请先配对");
            return;
        }

        try {
            final WsClient client = new WsClient(url, protos, headers, new WsClient.Listener() {
                @Override public void onOpen() {
                    if (gen != generation) return;
                    setState(State.AUTHENTICATING, "已连接，等待握手");
                    startPing();
                }

                @Override public void onText(String text) {
                    if (gen != generation) return;
                    handleFrame(text);
                }

                @Override public void onClosed(int code, String reason) {
                    if (gen != generation) return;
                    stopPing();
                    if (manualClose || !wantConnected) { setState(State.DISCONNECTED, "已断开"); return; }
                    if (code == 4004) { wantConnected = false; setState(State.GATEWAY_OFF, "网关已关闭（请在电脑端开启移动网关）"); return; }
                    if (code == 4003) {
                        // 4003 = 服务端重新开启了鉴权，现有 token 已作废：必须重新配对，
                        // 无脑重连每 15s 撞一次没有意义（评审 P1-4）。wantConnected 一并置 false，
                        // 否则网络变化回调还会把这条注定失败的连接再拉起来。
                        wantConnected = false;
                        setState(State.UNAUTHORIZED, "服务端已重新开启鉴权，请重新连接");
                        return;
                    }
                    String detail = (reason == null || reason.isEmpty() || "connection lost".equals(reason))
                            ? ("连接断开(" + code + ")") : reason;
                    scheduleReconnect(detail + " · 准备重连");
                }

                @Override public void onFailure(Throwable error) {
                    if (gen != generation) return;
                    stopPing();
                    if (manualClose || !wantConnected) return;
                    String msg = error == null ? "未知错误" : String.valueOf(error.getMessage());
                    if (msg.contains("401")) { wantConnected = false; setState(State.UNAUTHORIZED, msg); return; }
                    if (msg.contains("503")) {
                        // 网关没开：每 15s 重试一次只会耗电刷日志，明确文案后停下（评审 P1-4）
                        wantConnected = false;
                        setState(State.GATEWAY_OFF, msg);
                        return;
                    }
                    scheduleReconnect(msg);
                }
            }, trustAllCerts);
            ws = client;
            client.connect();
        } catch (IOException e) {
            wantConnected = false;
            setState(State.FAILED, e.getMessage());
        }
    }

    private void scheduleReconnect(String detail) {
        if (!wantConnected || manualClose) return;
        final Listener l2 = listener;
        if (l2 != null) main.post(() -> l2.onReconnectScheduled(detail));
        int n = reconnectAttempt.incrementAndGet();
        long delay = Math.min(15000L, 800L * (1L << Math.min(n, 4)));
        setState(State.CONNECTING, detail + " · " + (delay / 1000) + "s 后重试");
        main.removeCallbacks(reconnectTask);
        main.postDelayed(reconnectTask, delay);
    }

    private void startPing() {
        stopPing();
        pingTimer = System.currentTimeMillis();
        main.postDelayed(pingRunnable, PING_INTERVAL_MS);
    }

    private void stopPing() {
        pingTimer = 0;
        main.removeCallbacks(pingRunnable);
    }

    private final Runnable pingRunnable = new Runnable() {
        @Override public void run() {
            WsClient c = ws;
            // 假连接防护：ping 只发不验 pong 时，电脑休眠/路由重启/隧道断掉都不会有 FIN，
            // 界面会永远显示"已连接"而消息静默丢失。这里用"最近一次收到任何帧的时间"判定，
            // 不用 setSoTimeout（会与阻塞读循环冲突），超时即主动 close(1006) 走既有重连逻辑。
            if (c != null && !c.isClosed()) {
                long last = c.lastInboundAt();
                if (last > 0 && System.currentTimeMillis() - last > STALE_INBOUND_MS) {
                    rec("! " + (STALE_INBOUND_MS / 1000) + "s 无入站帧，判定连接已失效");
                    setState(State.CONNECTING, "连接已失效，正在重连");
                    c.close(1006, "连接已失效，正在重连");
                    return;   // 断开由 onClosed 接管并安排重连，ping 由 stopPing 停掉
                }
            }
            try {
                JSONObject o = new JSONObject();
                o.put("type", "ping");
                sendRaw(o);
            } catch (Throwable ignored) { }
            if (pingTimer != 0) main.postDelayed(this, PING_INTERVAL_MS);
        }
    };

    private void setState(State s, String detail) {
        state = s;
        final Listener l = listener;
        if (l == null) return;
        main.post(() -> {
            Listener cur = listener;
            if (cur == l) cur.onState(s, detail);
        });
    }

    // ------------------------------------------------------------ 发送

    // ------------------------------------------------------------ 诊断跟踪

    private final java.util.ArrayDeque<String> trace = new java.util.ArrayDeque<>();

    private void rec(String s) {
        synchronized (trace) {
            trace.addLast(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                    .format(new java.util.Date()) + " " + s);
            while (trace.size() > 40) trace.removeFirst();
        }
    }

    public String traceText() {
        StringBuilder sb = new StringBuilder();
        synchronized (trace) { for (String s : trace) sb.append(s).append('\n'); }
        return sb.toString();
    }

    public String debugState() {
        WsClient c = ws;
        return "state=" + state + "  gen=" + generation
                + "  ws=" + (c == null ? "null" : (c.isClosed() ? "closed" : "open"));
    }

    public void sendRaw(JSONObject o) {
        final String t = o.optString("type", "?");
        WsClient c = ws;
        if (c == null) { rec("→ " + t + "   [丢弃 ws=null]"); return; }
        if (c.isClosed()) { rec("→ " + t + "   [丢弃 ws 已关闭]"); return; }
        try {
            c.sendText(o.toString());
            rec("→ " + t);
        } catch (Throwable e) {
            rec("→ " + t + "   [发送失败 " + e.getClass().getSimpleName() + ": " + e.getMessage() + "]");
        }
    }

    private JSONObject base(String type) {
        JSONObject o = new JSONObject();
        try { o.put("type", type); } catch (Throwable ignored) { }
        return o;
    }

    public void requestSessions() { sendRaw(base("sessions")); }
    public void requestWorkspaces() { sendRaw(base("workspaces")); }
    public void requestHost() { sendRaw(base("host")); }

    public void subscribe(String sessionId) {
        try {
            JSONObject o = base("subscribe");
            o.put("sessionId", sessionId);
            o.put("assistantStream", true);
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    public void unsubscribe() { sendRaw(base("unsubscribe")); }

    public void requestHistory(String sessionId, Long beforeSeq, int historyFormatVersion) {
        try {
            JSONObject o = base("history");
            o.put("sessionId", sessionId);
            o.put("view", "conversation");
            if (beforeSeq != null) {
                o.put("beforeSeq", beforeSeq.longValue());
                o.put("historyFormatVersion", historyFormatVersion);
            }
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    /** sessionId 为空表示新建会话。 */
    public void sendMessage(String sessionId, String text) {
        try {
            JSONObject o = base("message");
            if (sessionId != null && !sessionId.isEmpty()) o.put("sessionId", sessionId);
            o.put("text", text == null ? "" : text);
            o.put("mode", "queue");
            o.put("clientTimeZone", TimeZone.getDefault().getID());
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    /** 带图片发送（protocol：images[] 里放标准 Base64，不带 data: 前缀）。 */
    public void sendMessageWithImage(String sessionId, String text, String mediaType,
                                     String base64, String name) {
        try {
            JSONObject o = base("message");
            if (sessionId != null && !sessionId.isEmpty()) o.put("sessionId", sessionId);
            o.put("text", text == null ? "" : text);
            o.put("mode", "queue");
            o.put("clientTimeZone", TimeZone.getDefault().getID());
            JSONArray images = new JSONArray();
            JSONObject img = new JSONObject();
            img.put("mediaType", mediaType);
            img.put("data", base64);
            if (name != null && !name.isEmpty()) img.put("name", name);
            images.put(img);
            o.put("images", images);
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    public void stopSession(String sessionId) {
        try {
            JSONObject o = base("session-cancel");
            o.put("sessionId", sessionId);
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    public void renameSession(String sessionId, String title) {
        try {
            JSONObject o = base("session-rename");
            o.put("sessionId", sessionId);
            o.put("title", title);
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    public void archiveSession(String sessionId) {
        try {
            JSONObject o = base("session-archive");
            o.put("sessionId", sessionId);
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    /**
     * 拉一个会话的历史，仅用于从 session/title 事件里抽标题。
     * 网关的 sessions 列表不带 title（宿主侧就没给），只能这样拿。
     */
    public void requestSessionTitle(String sessionId) {
        try {
            JSONObject o = base("history");
            o.put("sessionId", sessionId);
            o.put("view", "conversation");
            o.put("maxMessages", 500);
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    /** 拉取历史里某张图片的字节（Base64）。 */
    public void requestAttachment(String sessionId, String attachmentId) {
        try {
            JSONObject o = base("attachment");
            o.put("sessionId", sessionId);
            o.put("attachmentId", attachmentId);
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    /** 打开会话工作目录内的一个文件下载（path 必须是相对工作目录的路径）。 */
    public void fileDownloadOpen(String sessionId, String path, String requestId) {
        try {
            JSONObject o = base("file-download-open");
            o.put("sessionId", sessionId);
            o.put("path", path);
            o.put("requestId", requestId);
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    /** 拉取下一块；offset 必须用「上一块的 offset + 已解码字节数」。 */
    public void fileDownloadRead(String transferId, long offset) {
        try {
            JSONObject o = base("file-download-read");
            o.put("transferId", transferId);
            o.put("offset", offset);
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    public void fileDownloadCancel(String transferId) {
        try {
            JSONObject o = base("file-download-cancel");
            o.put("transferId", transferId);
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    /** 拉一小段尾部历史（用于补网关实时帧里没带 data 的事件）。 */
    public void requestRecentHistory(String sessionId, int maxMessages) {
        try {
            JSONObject o = base("history");
            o.put("sessionId", sessionId);
            o.put("view", "conversation");
            o.put("maxMessages", maxMessages);
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    public void requestTasks(String sessionId) {
        try {
            JSONObject o = base("tasks");
            o.put("sessionId", sessionId);
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    public void requestGoal(String sessionId) {
        try {
            JSONObject o = base("goal");
            o.put("sessionId", sessionId);
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    public void approvalResponse(String rpcId, String sessionId, String approvalId, String outcome) {
        try {
            JSONObject o = base("approval-response");
            o.put("rpcId", rpcId);
            o.put("sessionId", sessionId);
            o.put("approvalId", approvalId);
            o.put("outcome", outcome);
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    public void questionAnswer(String rpcId, String sessionId, JSONArray answers) {
        try {
            JSONObject o = base("question-answer");
            o.put("rpcId", rpcId);
            o.put("sessionId", sessionId);
            o.put("answers", answers);
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    public void questionCancel(String rpcId, String sessionId) {
        try {
            JSONObject o = base("question-cancel");
            o.put("rpcId", rpcId);
            o.put("sessionId", sessionId);
            sendRaw(o);
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------ 接收

    private void handleFrame(String text) {
        JSONObject f;
        try {
            f = new JSONObject(text);
        } catch (Throwable t) {
            return;
        }
        final String kind = f.optString("kind", "");
        main.post(() -> dispatch(kind, f));
    }

    private void dispatch(String kind, JSONObject f) {
        final Listener l = listener;
        rec("← " + kind + (l == null ? "   [无监听，丢弃]" : ""));
        if (l == null) return;
        switch (kind) {
            case "hello":
                reconnectAttempt.set(0);
                setState(State.READY, "已连接");
                l.onHello(f);
                break;
            case "paired":
                l.onPaired(f);
                break;
            case "sessions":
                l.onSessions(f.optJSONArray("items"), f);
                break;
            case "history":
                l.onHistory(f.optString("sessionId"), f.optJSONArray("events"), f);
                break;
            case "session-snapshot":
                l.onSnapshot(f.optString("sessionId"), f);
                break;
            case "assistant-stream":
                l.onAssistantStream(f);
                break;
            case "session-stream-reset":
                // 网关在流被中断时发这个帧；不处理的话流式气泡会永远转圈、计时不停（评审 P1-5）
                l.onStreamReset(f.optString("sessionId", ""), f.optString("code", ""),
                        f.optString("message", ""));
                break;
            case "event":
                l.onEvent(f.optString("sessionId"), f.optJSONObject("event"),
                        f.opt("seq"), f.opt("time"));
                break;
            case "approval-requested":
                l.onApprovalRequested(f);
                break;
            case "question-requested":
                l.onQuestionRequested(f);
                break;
            case "approval-resolved":
            case "question-resolved":
            case "question-response":
            case "approval-response":
                l.onInteractionResolved(f);
                break;
            case "sent":
                l.onSent(f.optString("sessionId"), f);
                break;
            case "file-download-opened":
            case "file-download-chunk":
            case "file-download-cancelled":
            case "file-download-closed":
                l.onDownload(kind, f);
                break;
            case "attachment": {
                JSONObject att = f.optJSONObject("attachment");
                String aid = att == null ? "" : att.optString("attachmentId", "");
                String mt = att == null ? "image/jpeg" : att.optString("mediaType", "image/jpeg");
                l.onAttachment(f.optString("sessionId"), aid, mt, f.optString("data", ""));
                break;
            }
            case "error":
                l.onProtocolError(f.optString("code"), f.optString("message"),
                        f.optString("requestType"), f.optString("sessionId"));
                break;
            default:
                l.onOther(kind, f);
                break;
        }
    }

    // ------------------------------------------------------------ 明文放行白名单

    /**
     * 明文 ws:// 只允许私有/回环/链路本地地址、.local 域名或单标签主机名。
     * 公网必须使用 wss://。返回 null 表示允许；否则返回给用户看的拒绝原因。
     */
    public static String cleartextProblem(String url) {
        if (url == null || url.trim().isEmpty()) return "地址为空";
        String s = url.trim();
        if (s.regionMatches(true, 0, "wss://", 0, 6)) return null;
        if (s.regionMatches(true, 0, "https://", 0, 8)) return null;
        String rest;
        if (s.regionMatches(true, 0, "ws://", 0, 5)) rest = s.substring(5);
        else if (s.regionMatches(true, 0, "http://", 0, 7)) rest = s.substring(7);
        else return null;

        int slash = rest.indexOf('/');
        String hostPort = slash >= 0 ? rest.substring(0, slash) : rest;
        int at = hostPort.lastIndexOf('@');
        if (at >= 0) hostPort = hostPort.substring(at + 1);

        String host = hostPort;
        if (host.startsWith("[")) {
            int e = host.indexOf(']');
            if (e > 0) host = host.substring(1, e);
        } else {
            int c = host.indexOf(':');
            // 没有方括号却含多个冒号 = 写成 IPv6 字面量：整体按 IPv6 判定，
            // 不能截成 "2001" 这种单标签主机名而被当成内网放行
            if (c >= 0 && c == host.lastIndexOf(':')) host = host.substring(0, c);
        }
        host = host.trim().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) return null;
        if (isPrivateHost(host)) return null;
        return "明文连接只允许局域网地址；公网请用 wss://（当前：" + host + "）";
    }

    private static boolean isPrivateHost(String host) {
        if (host == null) return false;
        String h = host.trim().toLowerCase(Locale.ROOT);
        // 地址里的 IPv6 常带方括号（ws://[2001:db8::1]:3091/…），先脱掉再判定
        if (h.startsWith("[")) {
            int e = h.indexOf(']');
            if (e > 0) h = h.substring(1, e).trim();
        }
        if (h.isEmpty()) return false;
        if ("localhost".equals(h) || h.endsWith(".local") || h.endsWith(".lan")
                || h.endsWith(".ts.net")) return true;
        // IPv6 必须单独判：旧写法「无点号即内网」会把公网 IPv6 字面量当成内网放行（评审 P0-6）
        if (h.indexOf(':') >= 0) return isPrivateIpv6(h);
        String[] p = h.split("\\.");
        if (p.length == 4) {
            try {
                int a = Integer.parseInt(p[0]);
                int b = Integer.parseInt(p[1]);
                if (a == 127 || a == 10) return true;
                if (a == 192 && b == 168) return true;
                if (a == 172 && b >= 16 && b <= 31) return true;
                if (a == 169 && b == 254) return true;
                if (a == 100 && b >= 64 && b <= 127) return true; // CGNAT / Tailscale
                return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        // 无点号又不是 IPv6：单标签主机名（家庭路由器名之类），保持原有放行行为
        if (h.indexOf('.') < 0) return true;
        return false;
    }

    /**
     * IPv6 里只有回环 ::1、唯一本地 fc00::/7（fc/fd 开头）、链路本地 fe80::/10 算内网；
     * 其余 IPv6 字面量（如公网 2001:db8::1）一律不是内网 —— 明文 ws:// 不得放行。
     */
    private static boolean isPrivateIpv6(String h) {
        String s = h;
        int pct = s.indexOf('%');            // fe80::1%wlan0 这种带 scope id 的写法
        if (pct >= 0) s = s.substring(0, pct);
        if ("::1".equals(s) || "0:0:0:0:0:0:0:1".equals(s)) return true;
        String head = s;
        int firstColon = head.indexOf(':');
        if (firstColon >= 0) head = head.substring(0, firstColon);
        if (head.length() < 2) return false;
        String two = head.substring(0, 2);
        if ("fc".equals(two) || "fd".equals(two)) return true;   // fc00::/7
        if (head.length() >= 4 && head.startsWith("fe")) {        // fe80::/10 => fe80..febf
            try {
                int b = Integer.parseInt(head.substring(2, 4), 16);
                if (b >= 0x80 && b <= 0xbf) return true;
            } catch (NumberFormatException ignored) { }
        }
        return false;
    }
}
