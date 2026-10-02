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

        /**
         * 带 retrying 的版本（新调用方实现这个，优先回调它）：retrying=true 表示网关只是
         * 瞬时中断，follower 会自动重开流并在 1s 后推新 snapshot（lib/index.mjs:2874 带
         * retrying / session-follower.mjs:123），不能按终态处理（评审 P1-5）。
         *
         * 上面 3 参重载保留为兼容桥：harness 要用同一份 Harness.java 编译新旧两版 net
         * 源码（harness/snapshot/ce7afd8 与工作区 src），只实现 3 参版本仍然可用。
         */
        default void onStreamReset(String sessionId, String code, String message, boolean retrying) {
            onStreamReset(sessionId, code, message);
        }
    }

    private static final String PROTO = "dsh-mobile-v1";
    /** 本 App 实现的 hello.protocol。对端宣告别的值就是版本错配（评审 P1-16）。 */
    private static final int SUPPORTED_PROTOCOL = 3;
    /** App 依赖的能力 -> 给用户看的功能名（缺了就在横幅里点名，评审 P1-16）。 */
    private static final String[][] REQUIRED_CAPS = {
            {"images", "发图片"},
            {"file-downloads", "文件下载"},
            {"session-create", "新建对话"},
            {"session-rename", "重命名对话"},
            {"session-archive", "归档对话"},
            {"commands", "斜杠命令"},
            {"tasks", "任务面板"},
            {"goals", "目标面板"},
            {"session-cancel", "停止回合"},
    };
    private static final long PING_INTERVAL_MS = 25_000L;
    /**
     * 假连接判定阈值：连续 3 个 ping 周期（75s）没有收到任何入站帧（含 pong / 服务端 ping）
     * 就认为链路已死。手机侧仍显示"已连接"但对端早就不在了，是评审 P0-1 的核心症状。
     */
    private static final long STALE_INBOUND_MS = 75_000L;
    /**
     * 假连接判定的容差。lastInboundAt 已改到「握手成功后」置位（见 WsClient.run），
     * 它与 ping 的固定 25s 网格之间只剩毫秒级先后差，而收到 hello 时还会再刷新一次 ——
     * 于是第 3 个 ping 周期上的判定正好落在 75000ms 边界外侧，实测拖到第 4 个周期
     * （100.8s）才断开（对照：旧代码 75.8s）。留 1s 容差把边界收回第 3 个周期内；
     * 1s 远小于一个 ping 周期，不会把活着的连接误判成假连接（第 2 个周期才 50s）。
     */
    private static final long STALE_INBOUND_TOLERANCE_MS = 1_000L;
    /**
     * 握手看门狗：服务端接受 Upgrade 后可能永不发 hello，但仍然会回 pong ——
     * 这时 lastInboundAt 被 pong 持续刷新，75s 假连接判定永远不触发，UI 就永远停在
     * 「已连接，等待握手」（评审 P0-1 覆盖不全的缺口）。进入 AUTHENTICATING 起计时，
     * 超时未收到 hello 即主动按正常关闭断开并走既有重连。
     */
    private static final long HANDSHAKE_TIMEOUT_MS = 10_000L;
    /**
     * 退避顶格的判定档位。delay = min(15000, 800 * 2^min(n,4))：n=4 起就到 12800ms 封顶，
     * 所以 n>=4 视为"退避已顶格"，此时网络恢复值得让网络回调立刻补一次重连。
     */
    private static final int BACKOFF_MAX_ATTEMPTS = 4;
    /**
     * 503「网关没开」时的重连间隔：5 分钟。
     * 这个场景不该停止重连（"先开 App、后开网关"是常态），但也没必要每 15s 撞一次 ——
     * 所以间隔拉长、wantConnected 保持 true（评审 P1-4 回归修复）。
     */
    private static final long GATEWAY_OFF_RETRY_MS = 300_000L;

    private volatile Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** 代际：每次 open() 递增；旧连接的迟到回调据此丢弃，避免重连风暴。 */
    private volatile int generation = 0;
    /** 最近一次 hello 暴露的协议/能力问题（空 = 正常）。用于横幅与设置页诊断（评审 P1-16）。 */
    private volatile String helloWarning = "";
    /** 最近一次 hello 宣告的能力集合；null = 对端没发该字段（视为未知，不做收敛，保持旧行为）。 */
    private volatile java.util.Set<String> capabilities = null;

    public void setListener(Listener l) { this.listener = l; }
    public Listener listener() { return listener; }
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);

    /**
     * hello 到了不等于连接稳定（评审 P1-2）。网关"接受连接后立刻关"时每轮都会发 hello，
     * 旧写法在 dispatch("hello") 里无条件 reconnectAttempt.set(0)，退避被永久钉在最小值，
     * 形成 ~1.6s 一轮的无限热重连（历史日志里连续出现过 85 次），也让 backoffAtMax() 那道
     * 网络回调闸门形同虚设。改为 READY 连续存活 ≥ READY_STABLE_MS 才清零。
     */
    private static final long READY_STABLE_MS = 120_000L;
    /** 存活不足这个时长的连接算"短命"，按次数罚时（退避上限 BACKOFF_CEILING_MS）。 */
    private static final long SHORT_LIVED_MS = 5_000L;
    /** 短命连接的退避上限：60s（普通失败仍按 15s 封顶，不牵连正常重连）。 */
    private static final long BACKOFF_CEILING_MS = 60_000L;
    /** 短命连接累计次数；READY 稳定存活 READY_STABLE_MS 后清零。 */
    private final AtomicInteger shortLivedCount = new AtomicInteger(0);
    /** "READY 稳定存活"计时任务（到点才清零退避）。 */
    private Runnable readyStableTask;
    /** onOpen 成功时刻 / 收到 hello 时刻，用于判定"短命连接"。 */
    private long openedAtMs;
    private long helloAtMs;
    /** guardCleartext 写入拒绝原因的时刻：短窗口内不被旧连接的 DISCONNECTED 覆盖。 */
    private volatile long cleartextDeniedAt;
    private static final long DENY_HOLD_MS = 4_000L;

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

    /** 握手看门狗任务：进了 AUTHENTICATING 后 10s 内没等到 hello 就断开重连。 */
    private Runnable handshakeWatchdog;

    private boolean trustAllCerts = false;

    public GatewayClient(Listener listener) { this.listener = listener; }

    /** 自建反代的证书自签时置 true（不校验证书）。 */
    public void setTrustAllCerts(boolean value) { this.trustAllCerts = value; }

    public boolean trustAllCerts() { return trustAllCerts; }

    public State state() { return state; }
    public String url() { return url; }

    /**
     * hello 暴露的协议/能力问题（空字符串 = 正常）。UI 据此在 READY 状态也挂出醒目横幅 ——
     * 只写进设置页诊断的话，普通用户永远看不到，"照单全收"就没被真正修掉（评审 P1-16）。
     */
    public String helloWarning() { return helloWarning; }

    /** 对端是否宣告了某个能力。对端根本没发 capabilities 字段时返回 true（保持旧行为，不乱收敛）。 */
    public boolean hasCapability(String cap) {
        java.util.Set<String> c = capabilities;
        return c == null || c.contains(cap);
    }

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
        shortLivedCount.set(0);   // 用户主动重连：清掉上一轮攒下的短命罚分
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
        shortLivedCount.set(0);   // 用户主动重连：清掉上一轮攒下的短命罚分
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
        stopHandshakeWatchdog();
        stopReadyStable();
        main.removeCallbacks(reconnectTask);
        WsClient c = ws;
        ws = null;
        if (c != null) c.close(1000, "cleartext denied");
        // 旧连接的 onClosed 是异步的：它会走 !wantConnected 分支写 DISCONNECTED「已断开」，
        // 把这里刚设的"公网明文被拒"原因覆盖掉 → 用户只看到 Toast 看不到理由（N-E）。
        // 记下时刻，短窗口内由 holdDenyState() 挡住那次覆盖。
        cleartextDeniedAt = System.currentTimeMillis();
        setState(State.FAILED, problem);
        return false;
    }

    /**
     * 刚因明文被拒置了 FAILED 时，返回 true 表示旧连接迟到的 onClosed 不该把状态
     * 覆盖成 DISCONNECTED「已断开」（N-E）。窗口很小（DENY_HOLD_MS），
     * 用户随后重新连接会正常走 CONNECTING → READY，不受影响。
     */
    private boolean holdDenyState() {
        if (state != State.FAILED) return false;
        return System.currentTimeMillis() - cleartextDeniedAt < DENY_HOLD_MS;
    }

    /**
     * 「链路还活着」的判据：最近一次收到任何入站帧距今不超过这么久。
     * 正常连接每 25s 一个 ping 周期必有 pong 回来，30s 足够宽松；
     * 但电脑端关掉网卡时 TCP 不会立刻 FIN，state 最长 75s 都还是 READY，
     * 光看 state 会把帧送进黑洞（评审 P0-3 / P1 盲区收口）。
     */
    private static final long CAN_SEND_FRESH_MS = 30_000L;

    /**
     * 当前连接是否真的能把帧发出去（UI 在「操作成功」前判断，避免断网时谎报成功）。
     * 除了 ws 非空且未关闭，还要求已经握手完成（READY）：重连窗口里 ws 可能已存在但
     * 还没拿回 hello，这时 sendText 会被静默丢进未就绪的写队列 —— 正是要修的那种"谎报"。
     * 另外要求「最近 CAN_SEND_FRESH_MS 内有入站帧」：READY 只说明握手成功过，
     * 半开链路（对端网卡关掉、隧道断掉）在 75s 假连接判定触发之前仍是 READY。
     */
    public boolean canSend() {
        WsClient c = ws;
        if (c == null || c.isClosed() || state != State.READY) return false;
        long last = c.lastInboundAt();
        return last > 0 && System.currentTimeMillis() - last <= CAN_SEND_FRESH_MS;
    }

    /** 用户是否还希望保持连接（disconnect() 之后为 false）；供网络变化时判断要不要补一次重连。 */
    public boolean wantConnected() { return wantConnected; }

    /**
     * 自动重连的退避是否已经爬到顶。网络回调（尤其 onCapabilitiesChanged，来得很频繁）
     * 不该在退避计时器还在正常工作时插一脚 —— connect() 会把 reconnectAttempt 清零，
     * 抖动时退避就永远是最小值。调用方据此只在"已经失败"或"退避已顶格"时才补一次。
     */
    public boolean backoffAtMax() { return reconnectAttempt.get() >= BACKOFF_MAX_ATTEMPTS; }

    /**
     * 网络变化时补一次重连，**不重置退避**（评审 P1-9）。
     *
     * 不能直接用 connect()：它会 reconnectAttempt.set(0)，网络一抖动（onCapabilitiesChanged
     * 来得很频繁）退避就永远停在最小值，等于把自动退避废掉。
     *
     * state==READY 时也允许调用，因为 WiFi→4G 之后旧 socket 看着还活着（TCP 不会立刻
     * FIN），实际已经发不出帧，只能干等 75s 假连接判定；主动重开一条能立刻恢复。
     * 调用方负责节流（MainActivity 用 lastNetReconnectAt）。
     */
    public void retryNow() {
        if (!wantConnected || manualClose) return;
        if (url.isEmpty()) return;
        if (!guardCleartext(url)) return;   // 与 connect() 同一道明文闸门
        open();
    }

    public void disconnect() {
        wantConnected = false;
        manualClose = true;
        generation++;
        main.removeCallbacks(reconnectTask);
        stopPing();
        stopHandshakeWatchdog();
        stopReadyStable();
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
        stopHandshakeWatchdog();
        stopReadyStable();
        main.removeCallbacks(reconnectTask);
        openedAtMs = System.currentTimeMillis();
        helloAtMs = 0;
        final int gen = ++generation;
        // 每代只收尾一次：WsClient 在"异常断开"时会先 onFailure 再在 finally 里 onClosed，
        // 两者都调 scheduleReconnect 会把退避直接翻倍并弹两次提示（评审 P1-3）。
        // 用 AtomicBoolean 而不是 boolean[1]：harness 的 Handler 垫片是多线程池，
        // 两个回调有可能并发进入，CAS 才能保证"每代只收尾一次"这条不变量。
        final java.util.concurrent.atomic.AtomicBoolean settled =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        WsClient old = ws;
        ws = null;
        if (old != null) old.close(1000, "reconnect");

        // 横幅只给"人话"：真实地址（内网 IP / 公网隧道域名）属于技术细节，
        // 会跟着状态一路显示在聊天页顶部横幅和会话列表标题上，对非技术用户是纯噪音，
        // 还把公网域名暴露在锁屏/截屏之外的用户视线里。地址改记进 trace（设置页诊断区）。
        rec("连接 " + url);
        setState(State.CONNECTING, "正在连接电脑…");

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
            // 匿名监听器里要用到"这条连接"本身（握手看门狗到点要 close 它），而
            // 局部变量 client 在 new 的那一刻还没完成赋值 —— 直接引用会编译不过
            // （variable client might not have been initialized），故用单元素数组过渡。
            final WsClient[] clientRef = new WsClient[1];
            final WsClient client = new WsClient(url, protos, headers, new WsClient.Listener() {
                @Override public void onOpen() {
                    if (gen != generation) return;
                    openedAtMs = System.currentTimeMillis();
                    setState(State.AUTHENTICATING, "已连接，等待握手");
                    startPing();
                    // 服务端接受 Upgrade 却永不发 hello（但会回 pong）时，只有看门狗能救场
                    armHandshakeWatchdog(gen, clientRef[0]);
                }

                @Override public void onText(String text) {
                    if (gen != generation) return;
                    handleFrame(text);
                }

                @Override public void onClosed(int code, String reason) {
                    if (gen != generation) return;
                    if (!settled.compareAndSet(false, true)) return;   // 已收尾过，避免二次调度重连（P1-3）
                    stopHandshakeWatchdog();
                    stopReadyStable();
                    stopPing();
                    if (manualClose || !wantConnected) {
                        // 明文被拒时刚置的 FAILED 不能被这次迟到回调覆盖成「已断开」（N-E）
                        if (!holdDenyState()) setState(State.DISCONNECTED, "已断开");
                        return;
                    }
                    if (code == 4004) { wantConnected = false; setState(State.GATEWAY_OFF, "网关已关闭（请在电脑端开启移动网关）"); return; }
                    if (code == 4003) {
                        // 4003 = 服务端拒绝这条已鉴权连接，现有 token 作废：必须重新配对，
                        // 无脑重连每 15s 撞一次没有意义（评审 P1-4）。wantConnected 一并置 false，
                        // 否则网络变化回调还会把这条注定失败的连接再拉起来。
                        //
                        // 4003 有**两种**成因（真实网关 lib/index.mjs:2742/2788 分别发
                        // "authentication enabled" 与 "device revoked"），改前两种都显示
                        // "服务端已重新开启鉴权"—— 设备被移除的用户会照着"重新开启鉴权"去电脑端
                        // 找开关，永远找不到。这里按 reason 分流成两条可执行的指引。
                        wantConnected = false;
                        String why = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
                        rec("! 4003 close reason=" + reason);
                        String msg;
                        if (why.contains("revoked")) {
                            msg = "这台设备已被电脑端移除授权，请在电脑端重新生成配对二维码后扫码配对";
                        } else if (why.contains("authentication")) {
                            msg = "电脑端已开启鉴权，请在电脑端重新生成配对二维码后扫码配对";
                        } else {
                            msg = "电脑端拒绝了这次连接（设备凭证已失效），请重新扫码配对";
                        }
                        setState(State.UNAUTHORIZED, msg);
                        return;
                    }
                    String detail = (reason == null || reason.isEmpty() || "connection lost".equals(reason))
                            ? ("连接断开(" + code + ")") : reason;
                    noteShortLived();   // 存活 <5s 记一次短命罚分（P1-2）
                    scheduleReconnect(detail + " · 准备重连");
                }

                @Override public void onFailure(Throwable error) {
                    if (gen != generation) return;
                    if (!settled.compareAndSet(false, true)) return;  // 已收尾过，避免二次调度重连（P1-3）
                    stopHandshakeWatchdog();
                    stopReadyStable();
                    stopPing();
                    if (manualClose || !wantConnected) return;
                    String msg = error == null ? "未知错误" : String.valueOf(error.getMessage());
                    if (msg.contains("401")) {
                        wantConnected = false;
                        rec("! 401 " + msg);
                        setState(State.UNAUTHORIZED, "电脑端拒绝了连接：设备令牌无效，请重新扫码配对");
                        return;
                    }
                    if (msg.contains("503")) {
                        // 503 = 电脑端网关还没开。真实场景就是"先开 App、后开网关"：
                        // 把 wantConnected 置 false 会让这个冷启动场景彻底失去自愈能力
                        // ——连网络回调里的 retryIfWanted() 也会因 !wantConnected() 直接返回
                        // （评审 P1-4 的回归）。保留 wantConnected=true，只把重连间隔拉长到
                        // 5 分钟；同时把退避标成"顶格"，让网络变化回调可以立刻补一次
                        // （用户开完网关常伴随网络抖动，不必干等 5 分钟）。
                        reconnectAttempt.set(Math.max(reconnectAttempt.get(), BACKOFF_MAX_ATTEMPTS));
                        scheduleReconnect("网关未开启(503)，稍后自动重试",
                                "电脑端的移动网关还没开启，稍后自动重试", GATEWAY_OFF_RETRY_MS);
                        // 放在 scheduleReconnect 之后：两帧都 post 到主线程，后一帧胜出，
                        // 用户看到的是明确的"网关没开"红字而不是含糊的"正在连接"。
                        rec("! 503 " + msg);
                        setState(State.GATEWAY_OFF, "电脑端的「移动网关」没有开启，请先在电脑上开启后重试");
                        return;
                    }
                    noteShortLived();   // 存活 <5s 记一次短命罚分（P1-2）
                    scheduleReconnect(msg, null, 0L);
                }
            }, trustAllCerts);
            clientRef[0] = client;
            ws = client;
            client.connect();
        } catch (IOException e) {
            wantConnected = false;
            rec("! 建立连接失败 " + e.getMessage());
            setState(State.FAILED, "连不上这个电脑地址，请检查地址与网络后重试");
        }
    }

    private void scheduleReconnect(String detail) { scheduleReconnect(detail, null, 0L); }

    /**
     * @param minDelayMs 本次重连的最短间隔（0 = 只用指数退避）。
     *        503「网关没开」用它把间隔拉长到 5 分钟。
     */
    private void scheduleReconnect(String detail, long minDelayMs) { scheduleReconnect(detail, null, minDelayMs); }

    /**
     * @param detail  技术原因（异常消息 / 关闭原因）。只进 trace 与 onReconnectScheduled，
     *                因为端点故障切换要靠它识别 "404"/隧道域名；绝不直接显示给用户。
     * @param userText 给用户看的重连原因；null/空 = 用通用文案。
     * @param minDelayMs 本次重连的最短间隔（0 = 只用指数退避）。
     */
    private void scheduleReconnect(String detail, String userText, long minDelayMs) {
        if (!wantConnected || manualClose) return;
        final Listener l2 = listener;
        if (l2 != null) main.post(() -> l2.onReconnectScheduled(detail));
        int n = reconnectAttempt.incrementAndGet();
        long delay = Math.min(15000L, 800L * (1L << Math.min(n, 4)));
        if (delay < minDelayMs) delay = minDelayMs;
        // 短命连接罚时（评审 P1-2）：网关"接受连接后立刻关"时，光靠 hello 不清零还不够
        // —— 指数只爬到 12.8s 就封顶。这里按累计短命次数继续放大，封顶 60s，
        // 让病态网关的轮询彻底降温；普通失败路径不受影响（shortLivedCount 为 0）。
        int shortLived = shortLivedCount.get();
        if (shortLived > 0) {
            long penalty = Math.min(BACKOFF_CEILING_MS, 800L * (1L << Math.min(n + shortLived, 7)));
            if (penalty > delay) delay = penalty;
        }
        // 改前这里是 detail + " · Ns 后重试"，detail 直接来自异常消息 / 关闭原因，
        // 真机上就出现过 "rim-country-gets-photos.trycloudflare.com · 51s 后重试" ——
        // 公网隧道域名 + 英文异常对用户是噪音，也把内部拓扑写在了屏幕上。
        // 改后只给"第几次、多少秒"，技术原因进 trace（设置页诊断区）。
        String human = (userText == null || userText.isEmpty()) ? "和电脑断开了，正在重连…" : userText;
        rec("重连 #" + n + " · " + detail + " · " + (delay / 1000) + "s");
        setState(State.CONNECTING, human + "（第 " + n + " 次，" + (delay / 1000) + "s 后重试）");
        main.removeCallbacks(reconnectTask);
        main.postDelayed(reconnectTask, delay);
    }

    /**
     * 连接存活不足 SHORT_LIVED_MS 就断开 = 病态网关的"接受后立刻关"：累计一次罚分。
     * 判据用收到 hello 的时刻（有 hello 才说明它确实"接受"过这次连接），
     * 没收到 hello 就用 socket onOpen 的时刻兜底。READY 稳定存活 30s 后清零（见 armReadyStable）。
     */
    private void noteShortLived() {
        long since = helloAtMs > 0 ? helloAtMs : openedAtMs;
        helloAtMs = 0;
        if (since > 0 && System.currentTimeMillis() - since < SHORT_LIVED_MS) {
            shortLivedCount.incrementAndGet();
        }
    }

    /**
     * hello 到了才开始计时：只有这条连接保持 READY 满 READY_STABLE_MS，
     * 才认为它"稳"，这时才清零退避与短命罚分（评审 P1-2）。
     * 只记时限、不马上清零是本次修复的核心：去掉旧的无条件 set(0)。
     */
    private void armReadyStable(final WsClient conn) {
        stopReadyStable();
        readyStableTask = new Runnable() {
            @Override public void run() {
                readyStableTask = null;
                if (conn == null || ws != conn || conn.isClosed()) return;
                if (state != State.READY) return;
                reconnectAttempt.set(0);
                shortLivedCount.set(0);
            }
        };
        main.postDelayed(readyStableTask, READY_STABLE_MS);
    }

    private void stopReadyStable() {
        Runnable r = readyStableTask;
        readyStableTask = null;
        if (r != null) main.removeCallbacks(r);
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

    /**
     * 起一个 10s 的握手看门狗：到点还没 READY（仍是 AUTHENTICATING）就按「正常关闭」断开，
     * 由 onClosed 接管重连。之所以不用 75s 的假连接判定替代：服务端若在回 pong，
     * lastInboundAt 会一直被刷新，那条判定永远不会触发。
     */
    private void armHandshakeWatchdog(final int gen, final WsClient client) {
        stopHandshakeWatchdog();
        handshakeWatchdog = new Runnable() {
            @Override public void run() {
                if (gen != generation) return;          // 已经换代
                if (ws != client) return;               // 已经不是这条连接
                if (state != State.AUTHENTICATING) return;   // 已收到 hello / 已断开
                rec("! " + (HANDSHAKE_TIMEOUT_MS / 1000) + "s 未收到 hello，判定握手超时");
                setState(State.CONNECTING, "握手超时，正在重连");
                // 1000 = 正常关闭。RFC6455 §7.4.1 禁止在 Close 帧里出现 1005/1006/1015
                client.close(1000, "握手超时");
            }
        };
        main.postDelayed(handshakeWatchdog, HANDSHAKE_TIMEOUT_MS);
    }

    private void stopHandshakeWatchdog() {
        Runnable r = handshakeWatchdog;
        handshakeWatchdog = null;
        if (r != null) main.removeCallbacks(r);
    }

    private final Runnable pingRunnable = new Runnable() {
        @Override public void run() {
            WsClient c = ws;
            // 假连接防护：ping 只发不验 pong 时，电脑休眠/路由重启/隧道断掉都不会有 FIN，
            // 界面会永远显示"已连接"而消息静默丢失。这里用"最近一次收到任何帧的时间"判定，
            // 不用 setSoTimeout（会与阻塞读循环冲突），超时即主动正常关闭走既有重连逻辑。
            // 关闭码必须是 RFC6455 定义且非保留的码：1005/1006/1015 是保留码，禁止出现在
            // Close 帧里。旧代码写 1006，对端 node `ws` 收到会抛未捕获的
            // RangeError: Invalid WebSocket frame: invalid status code 1006（网关只注册了
            // message/close，没有 error 处理器，靠宿主兜底才没崩）。这里用 1011（服务端
            // 遭遇意外情况），而不是 1000：这是失败驱动的关闭，不该被对端读成"正常结束"。
            if (c != null && !c.isClosed()) {
                long last = c.lastInboundAt();
                if (last > 0 && System.currentTimeMillis() - last > STALE_INBOUND_MS - STALE_INBOUND_TOLERANCE_MS) {
                    rec("! " + (STALE_INBOUND_MS / 1000) + "s 无入站帧，判定连接已失效");
                    setState(State.CONNECTING, "连接已失效，正在重连");
                    c.close(1011, "连接已失效，正在重连");
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
        // hello 一到就撤握手看门狗：放在 listener 判空之前，
        // 否则没有监听者时看门狗会误杀一条已经握手成功的连接。
        if ("hello".equals(kind)) {
            stopHandshakeWatchdog();
            helloAtMs = System.currentTimeMillis();
            // 不再无条件 reconnectAttempt.set(0)（评审 P1-2，那会造成 1.6s 一轮的无限热重连）：
            // 改为 READY 稳定存活 READY_STABLE_MS 后才清零。
            armReadyStable(ws);
            checkHello(f);   // 协议版本 / 能力校验：先算清楚再决定 READY 挂什么文案（评审 P1-16）
        }
        if (l == null) return;
        switch (kind) {
            case "hello":
                setState(State.READY, helloWarning.isEmpty() ? "已连接" : helloWarning);
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
                // 网关在流被中断时发这个帧；不处理的话流式气泡会永远转圈、计时不停（评审 P1-5）。
                // retrying=true 表示 follower 会自动重开流（lib/index.mjs:2874 / session-follower.mjs:123），
                // 交给上层按"瞬时抖动"处理，而不是当终态摘气泡。
                l.onStreamReset(f.optString("sessionId", ""), f.optString("code", ""),
                        f.optString("message", ""), f.optBoolean("retrying", false));
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

    /**
     * hello 的协议版本与能力校验（评审 P1-16）。
     *
     * 改前是"照单全收"：网关宣告 protocol=4（或能力大缺）时 App 一声不响地按 v3 语义继续收发，
     * 用户只会在后续遇到莫名行为。改后把问题显式化：
     *   - protocol 字段存在且不是 3 → 明确说明是哪一侧需要升级；
     *   - capabilities 存在但缺了 App 依赖的项 → 点名受影响的功能。
     * 结论写进 helloWarning（横幅 + 设置页诊断），并 rec() 落进 trace。
     *
     * 刻意**不**中断连接、不据此拒绝收发：
     *   1) 协议字段缺失的老网关必须继续可用（只在字段存在时判定）；
     *   2) v3→v4 是否向后兼容由网关决定，App 无权替用户判定"不能用"。
     * 若日后要求硬拒绝，只需在这里改成 setState(UNAUTHORIZED, ...) 并让本方法返回是否致命。
     */
    private void checkHello(JSONObject f) {
        StringBuilder w = new StringBuilder();
        if (f.has("protocol")) {
            int p = f.optInt("protocol", SUPPORTED_PROTOCOL);
            if (p != SUPPORTED_PROTOCOL) {
                w.append(p > SUPPORTED_PROTOCOL
                        ? "App 需要升级：电脑端网关协议版本 " + p + "，本 App 只支持 " + SUPPORTED_PROTOCOL
                        : "电脑端网关版本过旧：协议版本 " + p + "，本 App 需要 " + SUPPORTED_PROTOCOL);
                rec("! hello.protocol=" + p + " ≠ " + SUPPORTED_PROTOCOL);
            }
        }
        java.util.Set<String> caps = null;
        JSONArray arr = f.optJSONArray("capabilities");
        if (arr != null) {
            caps = new java.util.HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                String c = arr.optString(i, "");
                if (c != null && !c.isEmpty()) caps.add(c);
            }
            List<String> missing = new ArrayList<>();
            for (String[] r : REQUIRED_CAPS) {
                if (!caps.contains(r[0])) missing.add(r[1]);
            }
            if (!missing.isEmpty()) {
                if (w.length() > 0) w.append("；");
                w.append("电脑端网关能力不完整，以下功能可能不可用：").append(joinCn(missing));
                rec("! hello.capabilities 缺少 " + missing.size() + " 项: " + missing);
            }
        }
        capabilities = caps;
        helloWarning = w.toString();
    }

    private static String joinCn(List<String> xs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < xs.size(); i++) {
            if (i > 0) sb.append('、');
            sb.append(xs.get(i));
        }
        return sb.toString();
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
