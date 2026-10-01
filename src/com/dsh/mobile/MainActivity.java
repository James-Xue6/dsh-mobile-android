package com.dsh.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.dsh.mobile.model.ChatItem;
import com.dsh.mobile.model.SessionInfo;
import com.dsh.mobile.net.GatewayClient;
import com.dsh.mobile.ui.ConversationView;
import com.dsh.mobile.ui.QrScanActivity;
import com.dsh.mobile.ui.SessionListView;
import com.dsh.mobile.ui.SettingsView;
import com.dsh.mobile.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 单 Activity 架构：会话列表 / 对话 / 连接设置 三屏切换。 */
public final class MainActivity extends Activity implements
        GatewayClient.Listener,
        SessionListView.Host,
        ConversationView.Host,
        SettingsView.Host {

    private static final int REQ_QR = 1001;
    private static final int REQ_VOICE = 1002;
    private static final int REQ_IMAGE = 1003;

    /**
     * 是否允许用外部 Intent 的 pairing extra 直接触发配对（自动化测试/脚本联调用）：
     *   adb shell am start -n com.dsh.mobile/.MainActivity -e pairing "<Base64URL 配对串>"
     *
     * 必须保持 false：MainActivity 是 exported="true" 的，本机任意 App 都能用这条
     * 命令把手机悄悄指向它自己的网关并覆盖令牌，而用户以为在连自己的电脑（评审 P0-4）。
     * 将来要联调时只改这一行为 true 再出调试包，正式包不要开。
     */
    private static final boolean ALLOW_TEST_PAIRING_INTENT = false;

    private enum Screen { LIST, CHAT, SETTINGS }
    private Screen screen = Screen.LIST;

    /** 进程级共享的网关客户端：Activity 重建不应打断连接。 */
    private static GatewayClient SHARED_GW;

    private Store store;
    private GatewayClient gw;

    private FrameLayout root;
    private SessionListView listScreen;
    private ConversationView convo;
    private SettingsView settingsView;

    private final List<SessionInfo> sessions = new ArrayList<>();
    private final Set<String> archivedIds = new HashSet<>();

    private final List<ChatItem> items = new ArrayList<>();
    private final Map<String, ChatItem> byKey = new HashMap<>();
    private final Set<Long> seenSeq = new HashSet<>();

    private String currentSessionId = "";
    private String currentTitle = "";
    private String currentCwd = "";
    private int historyFormatVersion = 4;
    private Long nextBeforeSeq = null;
    private boolean hasMore = false;
    private boolean running = false;
    private long turnStartedAt = 0L;
    /** 上一次 onState 的状态：重连时要区分"此前确实断线"与重复 READY（评审 N1）。 */
    private GatewayClient.State lastGatewayState = GatewayClient.State.DISCONNECTED;
    /**
     * 重连时因"不敢断言回合已结束"而保守保留了 running：等网关的 sessions 权威标志
     * 到达后再补判一次（评审 N1），否则停止按钮可能永久卡 ■。
     */
    private boolean runningUncertain = false;
    private String pendingUserText = null;
    private String streamAttemptKey = null;
    private String lastStateText = "";
    /** 非当前会话的待处理交互：sessionId -> 1 提问 / 2 审批 */
    private final Map<String, Integer> pendingBySession = new HashMap<>();
    /** 反馈草稿：发完不清空，方便继续补充。 */
    private String lastFeedbackDraft = "";
    /** 自动切换端点只用一次，连接成功或手动切换后复位。 */
    private boolean failoverUsed = false;
    /** READY 稳定存活计时任务：只有真的稳住了才解禁端点故障切换（评审 P1-16）。 */
    private Runnable failoverResetTask;
    /** READY 连续存活满这么久，才允许下一次「内网/公网」自动切换。 */
    private static final long FAILOVER_RESET_MS = 60_000L;
    /** 非空表示正在配对；失败会换成下一个候选地址重试。 */
    private String pendingPairCode = null;
    private java.util.List<String> pairCandidates = new java.util.ArrayList<>();
    private int pairIndex = 0;

    // 当前会话的目标 / 任务提要
    private String planGoal = "";
    private String planPhase = "";
    private String planTodos = "";

    // 标题懒加载队列（网关不返回 title，只能逐个从历史里抽）
    private final java.util.ArrayDeque<String> titleQueue = new java.util.ArrayDeque<>();
    private final Set<String> titleRequested = new HashSet<>();
    private final Set<String> titleAwaiting = new HashSet<>();
    private int titleInFlight = 0;
    private static final int TITLE_MAX_INFLIGHT = 2;

    // ============================================================ 生命周期

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // 防截屏 / 防最近任务缩略图泄漏（安全评审 P1-10 必修项）：
        // 这个界面会出现设备令牌（掩码后仍有末 4 位）、网关地址，以及全部聊天正文与工具输出。
        // 不设这个标志时：任意 App 可截屏、系统「最近任务」缩略图会把聊天内容留在后台快照里。
        // FLAG_SECURE 同时关掉两者，代价是用户自己也无法截屏（本 App 没有分享截图的需求）。
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        store = new Store(this);
        if (SHARED_GW == null) SHARED_GW = new GatewayClient(this);
        else SHARED_GW.setListener(this);
        gw = SHARED_GW;

        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        // targetSdk 35+ 强制 edge-to-edge：把系统栏内边距加到根容器，各屏不再自己留状态栏高度
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();

        // 仅当 ALLOW_TEST_PAIRING_INTENT 打开时才接受外部 Intent 里的配对串（默认关闭，见常量注释）
        final String pairFromIntent = getIntent() == null ? null : getIntent().getStringExtra("pairing");
        if (ALLOW_TEST_PAIRING_INTENT && pairFromIntent != null && !pairFromIntent.trim().isEmpty()) {
            final String pv = pairFromIntent.trim();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> startPairing(pv), 1200);
        }

        listScreen = new SessionListView(this, this);
        root.addView(listScreen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (store.paired()) {
            GatewayClient.State st = gw.state();
            if (st == GatewayClient.State.READY || st == GatewayClient.State.CONNECTING
                    || st == GatewayClient.State.AUTHENTICATING) {
                gw.requestSessions();
                refreshListStatus();
            } else {
                gw.setTrustAllCerts(store.insecureTls());
        checkUpdate(false);   // 启动时后台查一次新版本（6 小时内不重复）
        gw.connect(store.url(), store.token(), store.deviceId(), store.deviceName());
            }
        } else {
            showSettings();
        }
        refreshListStatus();
        registerNetworkCallback();
    }

    // ---- 网络变化感知（评审 P0-1 第三条）
    // 手机从 WiFi 切到移动数据、或断网恢复时，原来的长连接不会自己知道；
    // 这里在默认网络可用/切换时补一次重连（wantConnected 且当前非 READY 才动），
    // 不用等退避计时器到点。
    private android.net.ConnectivityManager.NetworkCallback netCallback;
    private long lastNetReconnectAt = 0L;
    /** 上一次看到的默认网络句柄：只有它真的变了，才认为"网卡换了"（WiFi→4G）。 */
    private String lastNetworkKey = "";

    private void registerNetworkCallback() {
        if (netCallback != null) return;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getSystemService(android.net.ConnectivityManager.class);
            if (cm == null) return;
            netCallback = new android.net.ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(android.net.Network network) {
                    onNetworkChanged(network);
                }
                @Override public void onCapabilitiesChanged(android.net.Network network,
                                                            android.net.NetworkCapabilities caps) {
                    if (caps != null && caps.hasCapability(
                            android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        onNetworkChanged(network);
                    }
                }
            };
            cm.registerDefaultNetworkCallback(netCallback);
        } catch (Throwable ignored) { /* 没有权限/老系统：不影响主流程 */ }
    }

    /**
     * 网络回调统一入口：先判断"是不是真的换了一张网卡"。
     * onCapabilitiesChanged 触发极其频繁（信号强弱、带宽变化都会来），
     * 用它去重连会变成网络一抖就重开连接；而且 READY 时旧 socket 看着还活着，
     * 只有句柄真的变了（WiFi→4G）才需要主动重开（评审 P1-9）。
     */
    private void onNetworkChanged(android.net.Network network) {
        String key = network == null ? "" : String.valueOf(network);
        boolean handleChanged = !key.equals(lastNetworkKey);
        lastNetworkKey = key;
        retryIfWanted(handleChanged);
    }

    /**
     * 网络变了：用户还希望连着 → 补一次重连。
     *
     * 分两条路：
     *  - 非 READY：只在「已经失败」或「自动重连的退避已经顶格」时才补。照旧写法
     *    （无条件 connect()）网络一抖动退避就永远停在最小值，重连风暴反而是自己制造的。
     *  - READY 且**网络句柄真的换了**：旧 socket 看着还活着（TCP 不会立刻 FIN），
     *    实际已发不出帧，不补就要干等 75s 假连接判定（评审 P1-9）。
     * 两条路都走 retryNow()（保留退避），不再用 connect()。
     */
    private void retryIfWanted(boolean networkHandleChanged) {
        if (gw == null || !store.paired()) return;
        if (!gw.wantConnected()) return;
        if (store.url().isEmpty()) return;
        GatewayClient.State st = gw.state();
        long now = System.currentTimeMillis();
        if (st == GatewayClient.State.READY) {
            // 只有网卡真的换了才值得断开重连；单纯的能力/信号变化不能算（10s 节流兜底）。
            if (!networkHandleChanged) return;
            if (now - lastNetReconnectAt < 10000L) return;
            lastNetReconnectAt = now;
            gw.retryNow();
            return;
        }
        if (st != GatewayClient.State.FAILED && !gw.backoffAtMax()) return;
        if (now - lastNetReconnectAt < 3000L) return;
        lastNetReconnectAt = now;
        // 用 retryNow() 而不是 connect()：后者会把 reconnectAttempt 清零，
        // 网络抖动时退避就退化成固定 3s 重连（评审 P1-9）。
        // 明文校验在 retryNow() 内部统一拦截，这里不必重复。
        gw.retryNow();
    }

    @Override
    protected void onDestroy() {
        // 连接是进程级的：Activity 销毁（重建/任务切换）只解绑监听，不断开连接。
        if (gw != null && gw.listener() == this) gw.setListener(null);
        if (netCallback != null) {
            try {
                android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                        getSystemService(android.net.ConnectivityManager.class);
                if (cm != null) cm.unregisterNetworkCallback(netCallback);
            } catch (Throwable ignored) { }
            netCallback = null;
        }
        super.onDestroy();
    }

    private long lastBackAt = 0L;

    /**
     * 返回键处理。走两条路：
     *  - dispatchKeyEvent 兜住 KEYCODE_BACK（清单已关闭预测式返回，保证按键会送到这里）
     *  - onBackPressed 作为老版本回退
     * 两条路对同一次按键可能都触发，用时间窗去重。
     */
    private void handleBackKey() {
        long now = System.currentTimeMillis();
        if (now - lastBackAt < 400L) return;
        lastBackAt = now;
        if (screen == Screen.LIST) {
            moveTaskToBack(true);
            return;
        }
        showList();
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) handleBackKey();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** 已在运行时再次收到带 pairing 的 intent（am start 复用一个实例时会走这里）。 */
    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String pv = intent == null ? null : intent.getStringExtra("pairing");
        if (ALLOW_TEST_PAIRING_INTENT && pv != null && !pv.trim().isEmpty()) {
            final String v = pv.trim();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> startPairing(v), 500);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleBackKey();
    }

    // ============================================================ 屏幕路由

    private void clearRoot() {
        root.removeAllViews();
    }

    private void showList() {
        screen = Screen.LIST;
        clearRoot();
        root.addView(listScreen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        listScreen.setRows(buildRows());
        refreshListStatus();
    }

    private void showChat() {
        screen = Screen.CHAT;
        clearRoot();
        if (convo == null) convo = new ConversationView(this, this);
        root.addView(convo, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        convo.setCompact("compact".equals(store.displayMode()));
        convo.setItems(items);
        convo.setTitleText(currentTitle.isEmpty() ? "对话" : currentTitle);
        convo.setSubtitleText(currentCwd);
        convo.setRunning(running, runningHint());
        refreshPlan();
        convo.refreshNow();
        convo.scrollToBottom();
    }

    private void showSettings() {
        screen = Screen.SETTINGS;
        clearRoot();
        if (settingsView == null) settingsView = new SettingsView(this, this);
        root.addView(settingsView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        settingsView.setFields(store.lanUrl(), store.wanUrl(), store.token(),
                store.deviceName(), store.useWan());
        settingsView.setDisplayMode(store.displayMode());
        settingsView.setInsecureTls(store.insecureTls());
        refreshAbout();
        settingsView.setUpdateHint(lastUpdateHint);
        refreshFeedbackHint();
        settingsView.setStatus(lastStateText.isEmpty() ? "未连接" : lastStateText, false);
        settingsView.setDiagnostics(gw.debugState() + "\n" + gw.traceText());
    }

    private List<SessionInfo> visibleSessions() {
        List<SessionInfo> out = new ArrayList<>();
        // blank = 创建过但从未发过消息的空会话：没内容也没标题，桌面端也不平铺展示，这里同样过滤。
        for (SessionInfo s : sessions) {
            if (archivedIds.contains(s.id)) continue;
            if (s.blank) continue;
            out.add(s);
        }
        Collections.sort(out, new Comparator<SessionInfo>() {
            @Override public int compare(SessionInfo a, SessionInfo b) {
                return Long.compare(b.updatedAt, a.updatedAt);
            }
        });
        return out;
    }

    /** 按工作区分组：rows = [「工作区 · N」, 会话, 会话, 「工作区 · N」, ...] */
    private List<Object> buildRows() {
        List<SessionInfo> vis = visibleSessions();
        java.util.LinkedHashMap<String, List<SessionInfo>> groups = new java.util.LinkedHashMap<>();
        for (SessionInfo s : vis) {
            String key = workspaceLabel(s);
            List<SessionInfo> g = groups.get(key);
            if (g == null) { g = new ArrayList<>(); groups.put(key, g); }
            g.add(s);
        }
        List<String> keys = new ArrayList<>(groups.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                return Long.compare(latestAt(groups.get(b)), latestAt(groups.get(a)));
            }
        });
        List<Object> rows = new ArrayList<>();
        for (String k : keys) {
            List<SessionInfo> g = groups.get(k);
            rows.add(k + "  ·  " + g.size());
            Collections.sort(g, new Comparator<SessionInfo>() {
                @Override public int compare(SessionInfo a, SessionInfo b) {
                    return Long.compare(b.updatedAt, a.updatedAt);
                }
            });
            rows.addAll(g);
        }
        return rows;
    }

    private static long latestAt(List<SessionInfo> l) {
        long m = 0;
        for (SessionInfo s : l) if (s.updatedAt > m) m = s.updatedAt;
        return m;
    }

    private static String workspaceLabel(SessionInfo s) {
        if (s.id != null && s.id.startsWith("im:")) return "IM 会话";
        String c = s.cwd == null ? "" : s.cwd.replace('\\', '/');
        if (c.isEmpty()) return "其他";
        int i = c.lastIndexOf('/');
        String tail = i >= 0 ? c.substring(i + 1) : c;
        return tail.isEmpty() ? "其他" : tail;
    }

    /** 目标 / 任务 -> 顶部提要文本 */
    /**
     * 别处的提问/审批不能静默丢掉：在会话列表上打角标并 Toast 提示；
     * 进入该会话时网关会重放待处理交互，卡片自然出现。
     */
    private void notifyPending(String sessionId, int kind, String label) {
        Integer cur = pendingBySession.get(sessionId);
        if (cur != null && cur == kind) return;
        pendingBySession.put(sessionId, kind);
        String name = sessionId;
        for (SessionInfo s : sessions) {
            if (!s.id.equals(sessionId)) continue;
            s.pending = kind;
            name = s.display();
        }
        if (listScreen != null) listScreen.setRows(buildRows());
        Toast.makeText(this, "「" + name + "」" + label, Toast.LENGTH_LONG).show();
    }

    /** 「关于」：版本 / 协议 / 设备 / 网关 / 令牌掩码。 */
    private void refreshAbout() {
        if (settingsView == null) return;
        String ver = "?";
        String installed = "—";
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = pi.versionName;
            installed = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
                    .format(new java.util.Date(pi.lastUpdateTime));
        } catch (Throwable ignored) { }
        StringBuilder sb = new StringBuilder();
        sb.append("应用　DSH 掌上通  v").append(ver).append('\n');
        sb.append("更新　").append(installed).append('\n');
        sb.append("协议　dsh-mobile-v1 · protocol 3\n");
        sb.append("设备　").append(store.deviceName()).append('\n');
        String did = store.deviceId();
        sb.append("标识　").append(did.length() > 8 ? did.substring(0, 8) + "…" : did).append('\n');
        String gname = store.gatewayName();
        String gid = store.gatewayId();
        sb.append("网关　").append(gname.isEmpty() ? "—" : gname);
        if (!gid.isEmpty()) sb.append("（").append(gid.length() > 8 ? gid.substring(0, 8) + "…" : gid).append("）");
        sb.append('\n');
        String tk = store.token();
        sb.append("令牌　").append(tk.isEmpty() ? "未配对"
                : "****" + tk.substring(Math.max(0, tk.length() - 4)));
        sb.append("　当前走").append(store.useWan() ? "公网" : "内网");
        sb.append("\n\n元君谦制作");
        settingsView.setAbout(sb.toString());
    }

    /** 正在补拉尾部历史（避免重复触发）。 */
    private boolean tailRefetchPending = false;

    /** 交付物事件实时帧没有 data，用一小段尾部历史补齐；不影响分页状态。 */
    private void refetchTailForDeliverables() {
        if (tailRefetchPending || currentSessionId.isEmpty()) return;
        tailRefetchPending = true;
        gw.requestRecentHistory(currentSessionId, 40);
    }

    private void refreshPlan() {
        if (convo == null) return;
        StringBuilder sb = new StringBuilder();
        if (!planGoal.isEmpty()) {
            sb.append("目标：").append(planGoal);
            if (!planPhase.isEmpty()) sb.append("（").append(planPhase).append("）");
        }
        if (!planTodos.isEmpty()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(planTodos);
        }
        convo.setPlan(sb.toString());
    }

    /** 兼容 todo/write 事件与 tasks 查询（都是 {todos:[...]}）。 */
    private void applyTodos(JSONArray todos) {
        StringBuilder sb = new StringBuilder();
        if (todos != null) {
            for (int i = 0; i < todos.length(); i++) {
                JSONObject o = todos.optJSONObject(i);
                if (o == null) continue;
                String st = o.optString("status", "pending");
                String mark = "completed".equals(st) ? "\u2611"
                        : "in_progress".equals(st) ? "\u25D0" : "\u2610";
                if (sb.length() > 0) sb.append('\n');
                sb.append(mark).append(' ').append(o.optString("content", ""));
            }
        }
        planTodos = sb.toString();
        refreshPlan();
    }

    /**
     * 兼容两种目标形态：
     *   goal/change 事件 -> {goal:{objective,phase,...}}
     *   goal 查询响应    -> {goal:{goal:{objective,...},roundsStarted}}
     */
    private void applyGoal(JSONObject g) {
        if (g == null) {
            planGoal = "";
            planPhase = "";
            refreshPlan();
            return;
        }
        JSONObject cur = g;
        for (int i = 0; i < 3; i++) {
            if (!cur.optString("objective", "").isEmpty()) break;
            JSONObject next = cur.optJSONObject("goal");
            if (next == null) break;
            cur = next;
        }
        String phase = cur.optString("phase", "");
        if ("complete".equals(phase) || "cleared".equals(phase)) {
            planGoal = "";
            planPhase = "";
        } else {
            planGoal = cur.optString("objective", "");
            planPhase = phase;
        }
        refreshPlan();
    }

    private void refreshListStatus() {
        if (listScreen == null) return;
        String s;
        if (gw.state() == GatewayClient.State.READY) {
            int visible = visibleSessions().size();
            s = visible + " 个对话 · " + (store.gatewayName().isEmpty() ? hostOf(store.url()) : store.gatewayName());
        } else if (!store.paired()) {
            s = "还没配对 · 点右上角齿轮设置";
        } else {
            s = lastStateText.isEmpty() ? "连接中…" : lastStateText;
        }
        listScreen.setStatus(s);
    }

    private static String hostOf(String url) {
        if (url == null) return "";
        String u = url.replace("ws://", "").replace("wss://", "");
        int i = u.indexOf('/');
        return i > 0 ? u.substring(0, i) : u;
    }

    private String runningHint() {
        if (!running) return "";
        if (turnStartedAt <= 0) return "深度求索中…";
        long s = Math.max(0, (System.currentTimeMillis() - turnStartedAt) / 1000);
        return "深度求索中 · 用时 " + s + " 秒";
    }

    // ============================================================ GatewayClient.Listener

    @Override
    public void onState(GatewayClient.State st, String detail) {
        lastStateText = detail == null ? "" : detail;
        GatewayClient.State prevState = lastGatewayState;
        lastGatewayState = st;
        boolean err = (st == GatewayClient.State.UNAUTHORIZED
                || st == GatewayClient.State.FAILED
                || st == GatewayClient.State.GATEWAY_OFF);
        // 断线（进入任何非 READY 状态）时把在途下载判失败：dlByRequest/dlByTransfer 只在
        // 完成/失败时清理，断线后这两张表再也没人来收，卡片永远停在「下载中 x%」（评审 P1-15）。
        if (st != GatewayClient.State.READY) {
            failInFlightDownloads("连接已断开");
            cancelFailoverReset();   // 掉线就撤掉"稳定计时"，别让它替新连接解禁（评审 P1-16）
        }
        if (st == GatewayClient.State.READY) {
            // 连上了就允许下一次网络抖动再自动切一次端点，否则一次抖动会把用户永久钉在
            // 公网或内网（评审 P1-1）。但不能一 READY 就解禁（评审 P1-16）：
            // 连接反复"连上就被断"时会在内网/公网之间来回切，每轮都白失败一次。
            // 改为 READY 稳定存活 FAILOVER_RESET_MS 后才解禁。
            armFailoverReset();
            // 重连后看门狗要撤（避免按钮永久卡 ■），但"运行中"标记不能无条件复位（评审 N1）：
            // 长工具执行中重连时，快照可能不含 assistantStream.activeAttempt，
            // 旧写法无条件 setRunning(false) 会把「运行中」和停止按钮一起抹掉，
            // 而后续 tool/* 事件不会重新置 running。仅在"此前确实断线"且能确认回合已结束时复位。
            cancelSendWatchdog();
            if (running && prevState != GatewayClient.State.READY) {
                if (turnRunningOnGateway()) {
                    // 会话仍标着 running / 列表里还没有这个会话：先不够条件复位，
                    // 交给 onSessions() 用权威 running 标志补判（否则按钮会永久卡 ■）。
                    runningUncertain = true;
                } else {
                    setRunning(false);
                }
            }
            gw.requestSessions();
            // 断线会丢掉网关侧的订阅，重连后必须重新订阅，否则当前会话不再实时更新
            if (!currentSessionId.isEmpty()) {
                gw.subscribe(currentSessionId);
                gw.requestTasks(currentSessionId);
                gw.requestGoal(currentSessionId);
            }
        }
        if (screen == Screen.CHAT && convo != null) {
            convo.setBanner(st == GatewayClient.State.READY ? null : detail, err);
        }
        if (screen == Screen.SETTINGS && settingsView != null) {
            settingsView.setStatus(detail, err);
            settingsView.setDiagnostics(gw.debugState() + "\n" + gw.traceText());
        }
        refreshListStatus();
    }

    /**
     * 断线重连后判断当前会话的回合是否还在跑（评审 N1）。
     * 用 sessions 列表里当前会话的 running 兜底；列表里还没有这个会话时返回 true
     * （"不敢断言已结束"）—— 宁可多留一次停止按钮，也不要把正在跑的长工具抹掉。
     * 只有当前根本没绑定会话（新对话）时才返回 false，允许按期复位。
     */
    private boolean turnRunningOnGateway() {
        if (currentSessionId.isEmpty()) return false;
        for (SessionInfo s : sessions) {
            if (s != null && currentSessionId.equals(s.id)) return s.running;
        }
        return true;
    }

    @Override
    public void onHello(JSONObject hello) {
        try {
            String gid = hello.optString("gatewayId", "");
            if (!gid.isEmpty()) store.setGatewayId(gid);
            String gname = hello.optString("gatewayName", "");
            if (!gname.isEmpty()) store.setGatewayName(gname);
            historyFormatVersion = hello.optInt("historyFormatVersion", 4);
        } catch (Throwable ignored) { }
        gw.requestSessions();
        refreshListStatus();
    }

    @Override
    public void onPaired(JSONObject paired) {
        pendingPairCode = null;   // 配对完成，停止候选重试
        pairCandidates = new java.util.ArrayList<>();
        pairIndex = 0;
        String token = paired.optString("token", "");
        if (!token.isEmpty()) store.setToken(token);
        JSONObject dev = paired.optJSONObject("device");
        if (dev != null) {
            String n = dev.optString("name", "");
            if (!n.isEmpty()) store.setDeviceName(n);
        }
        Toast.makeText(this, "配对成功", Toast.LENGTH_SHORT).show();
        gw.requestSessions();
        showList();
    }

    @Override
    public void onSessions(JSONArray arr, JSONObject raw) {
        sessions.clear();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                SessionInfo s = new SessionInfo();
                s.id = o.optString("sessionId", o.optString("id", ""));
                s.title = o.optString("title", "");
                s.cwd = o.optString("cwd", "");
                s.agentPreset = o.optString("agentPreset", "");
                s.updatedAt = o.optLong("updatedAt", 0L);
                s.running = o.optBoolean("running", false);
                s.blank = o.optBoolean("blank", false);
                s.raw = o;
                if (!s.id.isEmpty()) sessions.add(s);
            }
        }
        for (SessionInfo s : sessions) {
            s.title = store.cachedTitle(s.id);
            if (s.title.isEmpty() && !s.blank && !titleRequested.contains(s.id)) {
                titleRequested.add(s.id);
                titleQueue.add(s.id);
            }
        }
        pumpTitleQueue();

        JSONArray arch = raw.optJSONArray("archivedSessionIds");
        if (arch != null) {
            archivedIds.clear();
            for (int i = 0; i < arch.length(); i++) archivedIds.add(arch.optString(i));
        }
        if (listScreen != null) listScreen.setRows(buildRows());
        refreshListStatus();
        // 重连时保守保留的"运行中"，用会话说里的权威 running 补判一次（评审 N1）：
        // 快照的历史窗口可能不含 turn/end，只靠快照回放会漏掉"回合已在断线期间结束"，
        // 那样停止按钮会一直卡着。这里只在网关明确说该会话已不在跑时才复位。
        if (runningUncertain && !turnRunningOnGateway()) setRunning(false);
    }

    @Override
    public void onHistory(String sessionId, JSONArray events, JSONObject meta) {
        String found = extractTitle(events);
        if (!found.isEmpty()) {
            store.cacheTitle(sessionId, found);
            for (SessionInfo si : sessions) if (si.id.equals(sessionId)) si.title = found;
            if (sessionId.equals(currentSessionId)) {
                currentTitle = found;
                if (convo != null) convo.setTitleText(found);
            }
            if (screen == Screen.LIST && listScreen != null) listScreen.setRows(buildRows());
        }
        if (titleAwaiting.remove(sessionId)) {
            if (titleInFlight > 0) titleInFlight--;
            pumpTitleQueue();
        }
        if (!sessionId.equals(currentSessionId)) return;
        cancelSendWatchdog();   // 该会话的历史回来了 = 连接与回合都是活的

        if (tailRefetchPending) {
            tailRefetchPending = false;
            if (events != null) {
                for (int i = 0; i < events.length(); i++) {
                    JSONObject e = events.optJSONObject(i);
                    if (e == null) continue;
                    String ty = e.optString("type", "");
                    if ("deliverables/presented".equals(ty) || "todo/write".equals(ty)
                            || "goal/change".equals(ty)) {
                        applyEvent(ty, e.optJSONObject("data"), e.opt("seq"), e.opt("time"), true);
                    }
                }
            }
            rebuildOrder();
            if (convo != null) { convo.setItems(items); convo.refresh(); }
            return; // 不触碰 hasMore / nextBeforeSeq
        }

        historyFormatVersion = meta.optInt("historyFormatVersion", historyFormatVersion);
        hasMore = meta.optBoolean("hasMore", false);
        nextBeforeSeq = meta.has("nextBeforeSeq") ? meta.optLong("nextBeforeSeq") : null;
        if (events != null) {
            for (int i = 0; i < events.length(); i++) {
                JSONObject e = events.optJSONObject(i);
                if (e == null) continue;
                applyEvent(e.optString("type", ""), e.optJSONObject("data"),
                        e.opt("seq"), e.opt("time"), true);
            }
        }
        rebuildOrder();
        if (convo != null) { convo.setItems(items); convo.refresh(); }
    }

    @Override
    public void onSnapshot(String sessionId, JSONObject snap) {
        if (!sessionId.equals(currentSessionId)) return;
        cancelSendWatchdog();
        historyFormatVersion = snap.optInt("historyFormatVersion", historyFormatVersion);
        hasMore = snap.optBoolean("hasMore", false);
        nextBeforeSeq = snap.has("nextBeforeSeq") ? snap.optLong("nextBeforeSeq") : null;

        items.clear();
        byKey.clear();
        seenSeq.clear();
        streamAttemptKey = null;

        JSONArray events = snap.optJSONArray("events");
        if (events != null) {
            for (int i = 0; i < events.length(); i++) {
                JSONObject e = events.optJSONObject(i);
                if (e == null) continue;
                applyEvent(e.optString("type", ""), e.optJSONObject("data"),
                        e.opt("seq"), e.opt("time"), true);
            }
        }
        // 恢复进行中的临时输出
        JSONObject as = snap.optJSONObject("assistantStream");
        if (as != null) {
            JSONObject active = as.optJSONObject("activeAttempt");
            if (active != null) {
                // 快照里还有进行中的输出：重新裁定为"运行中"（重连/onState(READY) 复位过 running）
                setRunning(true);
                String attemptId = active.optString("attemptId", "active");
                String text = streamTextOf(active);
                if (!text.isEmpty()) {
                    ChatItem it = ChatItem.of(ChatItem.ASSISTANT, "stream:" + attemptId, text);
                    it.streaming = true;
                    byKey.put(it.key, it);
                    items.add(it);
                    streamAttemptKey = it.key;
                }
            }
        }
        rebuildOrder();
        // items/byKey 刚刚整体重建：把"等在回执上"的卡片状态迁移到新对象（评审 P0-3）
        migratePendingInteractions();
        if (convo != null) { convo.setItems(items); convo.refreshNow(); convo.scrollToBottom(); }
    }

    @Override
    public void onAssistantStream(JSONObject frame) {
        if (!frame.optString("sessionId", "").equals(currentSessionId)) return;
        cancelSendWatchdog();
        JSONObject f = frame.optJSONObject("frame");
        if (f == null) return;
        String type = f.optString("type", "");
        String attemptId = f.optString("attemptId", "active");
        String key = "stream:" + attemptId;

        if ("start".equals(type)) {
            streamAttemptKey = key;
            if (!byKey.containsKey(key)) {
                ChatItem it = ChatItem.of(ChatItem.ASSISTANT, key, "");
                it.streaming = true;
                byKey.put(key, it);
                items.add(it);
            }
            setRunning(true);
            if (convo != null) { convo.setItems(items); convo.refresh(); }
            return;
        }
        if ("chunk".equals(type)) {
            JSONObject chunk = f.optJSONObject("chunk");
            if (chunk == null) return;
            ChatItem it = byKey.get(key);
            if (it == null) {
                it = ChatItem.of(ChatItem.ASSISTANT, key, "");
                it.streaming = true;
                byKey.put(key, it);
                items.add(it);
            }
            String ct = chunk.optString("type", "");
            String text = chunk.optString("text", "");
            if (ct.contains("reason")) {
                it.reasoning = it.reasoning + text;
            } else if (ct.contains("text") || ct.contains("delta")) {
                it.text = it.text + text;
            }
            it.streaming = true;
            streamAttemptKey = key;
            setRunning(true);
            if (convo != null) { convo.setItems(items); convo.refresh(); }
            return;
        }
        if ("end".equals(type)) {
            JSONObject outcome = f.optJSONObject("outcome");
            boolean committed = outcome != null && "committed".equals(outcome.optString("kind", ""));
            ChatItem it = byKey.get(key);
            if (it != null) {
                if (committed) {
                    // 持久 assistant/message 会到达并接管，先摘掉临时条目避免重复
                    byKey.remove(key);
                    items.remove(it);
                } else {
                    it.streaming = false;
                    if (it.text.trim().isEmpty()) {
                        byKey.remove(key);
                        items.remove(it);
                    }
                }
            }
            streamAttemptKey = null;
            setRunning(false);
            if (convo != null) { convo.setItems(items); convo.refresh(); }
        }
    }

    @Override
    public void onEvent(String sessionId, JSONObject event, Object seq, Object time) {
        if (!sessionId.equals(currentSessionId) || event == null) return;
        cancelSendWatchdog();   // 该会话有动静 = 这次发送有着落
        applyEvent(event.optString("type", ""), event, seq, time, false);
        rebuildOrder();
        if (convo != null) { convo.setItems(items); convo.refresh(); }
        if (listScreen != null) listScreen.setRows(buildRows());
    }

    @Override
    public void onApprovalRequested(JSONObject frame) {
        String sid = frame.optString("sessionId", "");
        if (!sid.equals(currentSessionId)) {
            notifyPending(sid, 2, "有待审批");
            return;
        }
        cancelSendWatchdog();   // 网关还能推审批 = 连接是活的
        String rpcId = frame.optString("rpcId", "");
        String approvalId = frame.optString("approvalId", "");
        // 与历史事件 approval/asked 用同一把键（approvalId），避免同一条审批出现两张卡
        String key = "approval:" + (approvalId.isEmpty() ? rpcId : approvalId);
        ChatItem it = byKey.get(key);
        if (it == null) {
            it = ChatItem.of(ChatItem.APPROVAL, key, "");
            byKey.put(key, it);
            items.add(it);
        }
        it.rpcId = rpcId;
        it.resolved = false;
        it.resolvedOutcome = "";
        it.pendingConfirm = false;   // 网关重新推送 = 电脑端还在等：撤掉"等待确认"，按钮重新出现
        it.sendError = "";   // 网关重新推送 = 仍未处理，清掉上次"没发出去"的提示
        cancelInteractionWatchdog(key);   // 卡片已被网关重新推送 = 这次决策没落地，撤掉待确认看门狗
        it.approvalId = approvalId;
        it.callId = frame.optString("callId", "");
        it.toolName = frame.optString("toolName", "");
        JSONObject dr = frame.optJSONObject("displayReason");
        if (dr != null) it.reason = dr.optString("zh-CN", frame.optString("reason", ""));
        if (it.reason == null || it.reason.isEmpty()) it.reason = frame.optString("reason", "");
        setRunning(true);
        rebuildOrder();
        if (convo != null) { convo.setItems(items); convo.refreshNow(); convo.scrollToBottom(); }
    }

    @Override
    public void onQuestionRequested(JSONObject frame) {
        String sid = frame.optString("sessionId", "");
        if (!sid.equals(currentSessionId)) {
            notifyPending(sid, 1, "有提问待回答");
            return;
        }
        String rpcId = frame.optString("rpcId", "");
        String key = "question:" + rpcId;
        ChatItem existing = byKey.get(key);
        if (existing != null) {
            // 网关重放提问 = 电脑端仍在等这次回答。必须和审批卡一样无条件复位 resolved：
            // 半开连接窗口里被乐观标成「✓ 已回答」的卡片，如果只清 sendError 不复位，
            // 重连 + 网关重放之后仍显示已回答，而电脑端还在等 → 回合僵死（评审 P0-3）。
            existing.sendError = "";
            existing.resolved = false;
            existing.resolvedOutcome = "";
            existing.pendingConfirm = false;   // 网关重放 = 电脑端还在等，撤掉"等待确认"（评审 P0-3）
            cancelInteractionWatchdog(key);    // 决策没落地，撤掉待确认看门狗，避免超时误报
            if (convo != null) { convo.setItems(items); convo.refreshNow(); }
            return;
        }
        cancelSendWatchdog();   // 网关还能推提问 = 连接是活的
        ChatItem it = ChatItem.of(ChatItem.QUESTION, key, "");
        it.rpcId = rpcId;
        it.questions = frame.optJSONArray("questions");
        byKey.put(key, it);
        items.add(it);
        setRunning(true);
        rebuildOrder();
        if (convo != null) { convo.setItems(items); convo.refreshNow(); convo.scrollToBottom(); }
    }

    @Override
    public void onInteractionResolved(JSONObject frame) {
        String rpcId = frame.optString("rpcId", "");
        String kind = frame.optString("kind", "");
        // 审批卡是按 approvalId 建档的（与历史 approval/asked 同键），这里不能用 rpcId 去找
        String approvalId = frame.optString("approvalId", "");
        ChatItem it = kind.startsWith("question")
                ? byKey.get("question:" + rpcId)
                : approvalCard(rpcId, approvalId);
        if (it == null) return;
        it.sendError = "";
        it.pendingConfirm = false;   // 回执到了：撤掉「已发送，等待电脑确认…」
        cancelSendWatchdog();
        // 回执到了：按卡片 key 撤看门狗（只撤这一张卡，别误撤别人正在等的那张）。
        // 必须按 key 而不是对象身份：onSnapshot/subscribeCurrent 会 items.clear()+byKey.clear()
        // 重建 ChatItem，对象身份必然失配 → 回执撤不掉看门狗 → 误报"电脑端没有确认"（评审 P0-3）。
        cancelInteractionWatchdog(it.key);
        if ("question-response".equals(kind) || "approval-response".equals(kind)) {
            if (frame.optBoolean("accepted", true)) {
                // 已受理：这时才把卡片标成完成（评审 P0-3：此前只处理了 accepted=false，
                // 因为旧实现是"先乐观 ✓"，现在改成等回执，这里必须补上落定）。
                it.resolved = true;
                if (it.resolvedOutcome == null || it.resolvedOutcome.isEmpty()) {
                    it.resolvedOutcome = "question-response".equals(kind) ? "answered" : "";
                }
            } else {
                it.resolved = true;
                it.resolvedOutcome = frame.optString("reason", "not-pending");
            }
            if (convo != null) { convo.setItems(items); convo.refreshNow(); }
            return;
        }
        it.resolved = true;
        // 网关带了权威结论（outcome）就覆盖；没带则保留提交时记下的本地结论
        // （allowed-once / answered / cancelled）—— 否则卡片会从「✓ 已批准」退化成
        // 「已由其他端处理」，看起来像是别人处理的。
        String authoritative = frame.optString("outcome", "");
        if (!authoritative.isEmpty()) it.resolvedOutcome = authoritative;
        if (convo != null) { convo.setItems(items); convo.refreshNow(); }
    }

    @Override
    public void onSent(String sessionId, JSONObject raw) {
        // 电脑端已回执：这次发送确实出去了，撤掉看门狗
        cancelSendWatchdog();
        if (currentSessionId.isEmpty() && sessionId != null && !sessionId.isEmpty()) {
            currentSessionId = sessionId;
            subscribeCurrent();
        }
    }

    /**
     * 网关中断了会话流（session-stream-reset）。不处理的话流式气泡会永远转圈、
     * 计时不停，用户以为卡死（评审 P1-5）。
     *
     * retrying=true 表示这只是瞬时中断：网关侧 follower 会自动重开流并在 1s 后推新
     * snapshot（lib/index.mjs:2874 带 retrying / session-follower.mjs:123）。这时摘气泡 +
     * setRunning(false) + Toast「输出被中断了」是误报，用户会看到气泡闪一下又回来，
     * 所以只做轻提示、保持现状等新 snapshot 覆盖。
     */
    @Override
    public void onStreamReset(String sessionId, String code, String message, boolean retrying) {
        if (sessionId != null && !sessionId.isEmpty() && !sessionId.equals(currentSessionId)) return;
        if (retrying) {
            Toast.makeText(this, "连接抖动，正在恢复…", Toast.LENGTH_SHORT).show();
            return;
        }
        cancelSendWatchdog();
        // 保留用户已经看到的输出：只把流式气泡"定型"（停转圈、去掉进行中标记），
        // 不能整段删除 —— 旧写法把 streamAttemptKey 指向的气泡从 items 里摘掉，
        // 半截回答直接消失（评审 P1-5）。只有完全没吐出任何内容时才摘掉这个空壳。
        ChatItem stream = streamAttemptKey == null ? null : byKey.get(streamAttemptKey);
        if (stream != null) {
            stream.streaming = false;
            if (stream.text.trim().isEmpty() && stream.reasoning.trim().isEmpty()) {
                byKey.remove(streamAttemptKey);
                items.remove(stream);
            }
        }
        streamAttemptKey = null;
        for (ChatItem ci : items) {
            if (ci.kind == ChatItem.ASSISTANT && ci.streaming) ci.streaming = false;
        }
        setRunning(false);
        // 流被中断不代表订阅还在：补一次 subscribe 让这个会话的实时流自己恢复，
        // 不然用户得手动切走再切回来才有新消息（评审 P1-5）。
        // 网关会回一条 replace 语义的 session-snapshot，把列表按权威历史重建。
        if (!currentSessionId.isEmpty() && gw.wantConnected()) {
            gw.subscribe(currentSessionId);
        }
        if (convo != null) { convo.setItems(items); convo.refreshNow(); }
        String why = (message == null || message.isEmpty())
                ? (code == null || code.isEmpty() ? "" : code) : message;
        Toast.makeText(this, why.isEmpty() ? "输出被中断了" : ("输出被中断了：" + why),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onProtocolError(String code, String message, String requestType, String sessionId) {
        // 网关用 {kind:'error'} 拒绝（而不是回 sent）时，这次发送其实已经有结论了：
        // 撤掉 30s 发送看门狗，否则错误提示之后还会再叠一句矛盾的"可能没发出去"（评审 P1-8）。
        cancelSendWatchdog();
        if ("history-format-mismatch".equals(code)) {
            historyFormatVersion = 4;
            nextBeforeSeq = null;
            hasMore = false;
            return;
        }
        String text = (message == null || message.isEmpty()) ? code : message;
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    // ============================================================ 文件下载

    /** 一次下载的进行态。 */
    private static final class Dl {
        String requestId, transferId, relPath, name, mediaType, itemKey;
        java.io.File temp;
        long size, received;
        java.security.MessageDigest md;
    }

    private final Map<String, Dl> dlByRequest = new HashMap<>();
    private final Map<String, Dl> dlByTransfer = new HashMap<>();
    private final java.util.concurrent.ExecutorService dlExec =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "dl-io");
                t.setDaemon(true);
                return t;
            });

    @Override
    public void onCopyPath(String path) {
        try {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("path", path));
            Toast.makeText(this, "路径已复制", Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) { }
    }

    @Override
    public void onDownloadFile(ChatItem item, String path) {
        startDownload(item, path);
    }

    /** 当前会话的工作目录（网关只允许下载工作目录内的文件）。 */
    private String sessionCwd(String sessionId) {
        for (SessionInfo s : sessions) if (s.id.equals(sessionId)) return s.cwd == null ? "" : s.cwd;
        return "";
    }

    /** 绝对路径 -> 相对会话工作目录的路径；不在目录内返回 null。 */
    private static String relativize(String cwd, String abs) {
        if (cwd == null || cwd.isEmpty() || abs == null || abs.isEmpty()) return null;
        String c = cwd.replace('/', '\\');
        while (c.endsWith("\\")) c = c.substring(0, c.length() - 1);
        String a = abs.replace('/', '\\');
        if (a.length() <= c.length() + 1) return null;
        if (!a.regionMatches(true, 0, c, 0, c.length())) return null;
        if (a.charAt(c.length()) != '\\') return null;
        String rel = a.substring(c.length() + 1);
        if (rel.isEmpty() || rel.startsWith("..")) return null;
        return rel.replace('\\', '/');
    }

    private void startDownload(ChatItem item, String absPath) {
        if (gw.state() != GatewayClient.State.READY) {
            Toast.makeText(this, "还没连上电脑端", Toast.LENGTH_SHORT).show();
            return;
        }
        String rel = relativize(sessionCwd(currentSessionId), absPath);
        if (rel == null) {
            Toast.makeText(this, "该文件不在这个会话的工作目录内，网关不允许下载",
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Dl d = new Dl();
            d.requestId = "dl-" + System.currentTimeMillis();
            d.relPath = rel;
            d.itemKey = item.key;
            d.temp = java.io.File.createTempFile("dl-", ".part", getCacheDir());
            d.md = java.security.MessageDigest.getInstance("SHA-256");
            dlByRequest.put(d.requestId, d);
            setDownloadState(d, "请求中…");
            gw.fileDownloadOpen(currentSessionId, rel, d.requestId);
        } catch (Throwable t) {
            Toast.makeText(this, "开始下载失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDownload(String kind, JSONObject frame) {
        if ("file-download-opened".equals(kind)) {
            Dl d = dlByRequest.get(frame.optString("requestId", ""));
            if (d == null) return;
            d.transferId = frame.optString("transferId", "");
            d.name = frame.optString("name", "file");
            d.mediaType = frame.optString("mediaType", "application/octet-stream");
            d.size = frame.optLong("size", 0);
            dlByTransfer.put(d.transferId, d);
            setDownloadState(d, d.size > 0 ? "下载中 0%" : "下载中…");
            gw.fileDownloadRead(d.transferId, 0);
            return;
        }
        if ("file-download-chunk".equals(kind)) {
            final Dl d = dlByTransfer.get(frame.optString("transferId", ""));
            if (d == null) return;
            final byte[] data = decodeB64(frame.optString("data", ""));
            final long offset = frame.optLong("offset", d.received);
            final boolean eof = frame.optBoolean("eof", false);
            final String sha = frame.optString("sha256", "");
            dlExec.execute(() -> {
                boolean ok = true;
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(d.temp, true)) {
                    fos.write(data);
                    d.md.update(data);
                } catch (Throwable t) {
                    ok = false;
                }
                d.received = offset + data.length;
                final boolean fok = ok;
                runOnUiThread(() -> {
                    if (!fok) { failDownload(d, "写入失败"); return; }
                    if (!eof) {
                        if (d.size > 0) {
                            setDownloadState(d, "下载中 " + (d.received * 100 / d.size) + "%");
                        }
                        gw.fileDownloadRead(d.transferId, d.received);
                    } else {
                        finishDownload(d, sha);
                    }
                });
            });
            return;
        }
        if ("file-download-cancelled".equals(kind) || "file-download-closed".equals(kind)) {
            Dl d = dlByTransfer.get(frame.optString("transferId", ""));
            if (d != null) failDownload(d, "传输已关闭");
        }
    }

    private void finishDownload(final Dl d, final String expectedSha) {
        dlExec.execute(() -> {
            String actual = hex(d.md.digest());
            boolean match = expectedSha.isEmpty() || expectedSha.equalsIgnoreCase(actual);
            String saved = match ? saveToDownloads(d) : null;
            runOnUiThread(() -> {
                if (!match) { failDownload(d, "校验失败（sha256 不一致）"); return; }
                if (saved == null) { failDownload(d, "保存失败"); return; }
                setDownloadState(d, "已下载 ✓ " + saved);
                Toast.makeText(this, "已保存到 " + saved, Toast.LENGTH_LONG).show();
                dlByRequest.remove(d.requestId);
                dlByTransfer.remove(d.transferId);
                try { d.temp.delete(); } catch (Throwable ignored) { }
            });
        });
    }

    /** API 29+ 走 MediaStore「下载」目录（在文件管理里可见）；更低版本存应用目录。 */
    private String saveToDownloads(Dl d) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, d.name);
                cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, d.mediaType);
                cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS + "/DSH 掌上通");
                android.net.Uri uri = getContentResolver()
                        .insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return null;
                try (java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                     java.io.FileInputStream fis = new java.io.FileInputStream(d.temp)) {
                    if (os == null) return null;
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = fis.read(buf)) > 0) os.write(buf, 0, n);
                }
                return "下载/DSH 掌上通/" + d.name;
            }
            java.io.File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) dir = getFilesDir();
            java.io.File out = new java.io.File(dir, d.name);
            try (java.io.FileInputStream fis = new java.io.FileInputStream(d.temp);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = fis.read(buf)) > 0) fos.write(buf, 0, n);
            }
            return out.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }

    private void setDownloadState(Dl d, String text) {
        for (ChatItem it : items) {
            if (it.key.equals(d.itemKey)) { it.downloadState = text; break; }
        }
        if (convo != null) { convo.setItems(items); convo.refresh(); }
    }

    private void failDownload(Dl d, String why) {
        setDownloadState(d, "下载失败：" + why);
        Toast.makeText(this, "下载失败：" + why, Toast.LENGTH_LONG).show();
        dlByRequest.remove(d.requestId);
        dlByTransfer.remove(d.transferId);
        try { d.temp.delete(); } catch (Throwable ignored) { }
    }

    /**
     * 断线时清理在途下载（评审 P1-15）。dlByRequest/dlByTransfer 只在完成/失败时清理，
     * 连接一断就再也没人来收这两张表 —— 卡片永远停在「下载中 x%」，用户只能干等。
     * 断线后 transferId 已失效、续传也不可能，所以直接判失败并删掉半截临时文件。
     */
    private void failInFlightDownloads(String why) {
        if (dlByRequest.isEmpty() && dlByTransfer.isEmpty()) return;
        java.util.Set<Dl> pending = new java.util.LinkedHashSet<>();
        pending.addAll(dlByRequest.values());
        pending.addAll(dlByTransfer.values());
        dlByRequest.clear();
        dlByTransfer.clear();
        for (Dl d : pending) {
            setDownloadState(d, "下载失败：" + why);
            try { if (d.temp != null) d.temp.delete(); } catch (Throwable ignored) { }
        }
        Toast.makeText(this, "下载已中断：" + why, Toast.LENGTH_LONG).show();
    }

    /**
     * READY 稳定存活 FAILOVER_RESET_MS 之后才解禁端点故障切换（评审 P1-16）。
     * 旧写法每次收到 hello（READY）就 failoverUsed=false：连接反复"连上就被断"时，
     * 每一轮都会在内网/公网之间来回切一次，两个端点轮流白失败。
     */
    private void armFailoverReset() {
        cancelFailoverReset();
        failoverResetTask = new Runnable() {
            @Override public void run() {
                failoverResetTask = null;
                // 到点时还必须是 READY：中间掉过线就不算"稳定存活"
                if (gw != null && gw.state() == GatewayClient.State.READY) failoverUsed = false;
            }
        };
        uiHandler.postDelayed(failoverResetTask, FAILOVER_RESET_MS);
    }

    private void cancelFailoverReset() {
        Runnable r = failoverResetTask;
        failoverResetTask = null;
        if (r != null) uiHandler.removeCallbacks(r);
    }

    private static byte[] decodeB64(String s) {
        try {
            return android.util.Base64.decode(s, android.util.Base64.DEFAULT);
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                .append(Character.forDigit(b & 0xF, 16));
        return sb.toString();
    }

    @Override
    public void onAttachment(String sessionId, String attachmentId, String mediaType, String base64) {
        if (!sessionId.equals(currentSessionId) || base64.isEmpty()) return;
        for (ChatItem it : items) {
            if (!it.attachmentIds.contains(attachmentId)) continue;
            if (it.images.size() >= it.attachmentIds.size()) break;
            new Thread(() -> {
                try {
                    byte[] raw = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
                    if (bmp != null) {
                        final android.graphics.Bitmap small = scaleDown(bmp, 1080);
                        runOnUiThread(() -> {
                            it.images.add(small);
                            if (convo != null) { convo.setItems(items); convo.refresh(); }
                        });
                    }
                } catch (Throwable ignored) { }
            }, "img-decode").start();
            break;
        }
    }

    /** 大图下采样，避免糊爆内存。 */
    private static android.graphics.Bitmap scaleDown(android.graphics.Bitmap src, int maxSide) {
        int w = src.getWidth(), h = src.getHeight();
        int longer = Math.max(w, h);
        if (longer <= maxSide) return src;
        float r = (float) maxSide / longer;
        return android.graphics.Bitmap.createScaledBitmap(src, Math.round(w * r), Math.round(h * r), true);
    }

    @Override
    public void onOther(String kind, JSONObject frame) {
        if ("tasks".equals(kind) || "tasks-updated".equals(kind)) {
            if (frame.optString("sessionId", currentSessionId).equals(currentSessionId)) {
                applyTodos(frame.optJSONArray("todos"));
            }
            return;
        }
        if ("goal".equals(kind) || "goal-updated".equals(kind)) {
            if (frame.optString("sessionId", currentSessionId).equals(currentSessionId)) {
                applyGoal(frame.optJSONObject("goal"));
            }
            return;
        }
        if ("session-archives".equals(kind)) {
            JSONArray arr = frame.optJSONArray("archivedSessionIds");
            archivedIds.clear();
            if (arr != null) for (int i = 0; i < arr.length(); i++) archivedIds.add(arr.optString(i));
            if (listScreen != null) listScreen.setRows(buildRows());
        } else if ("session-title-changed".equals(kind)) {
            String sid = frame.optString("sessionId", "");
            String title = frame.optString("title", "");
            for (SessionInfo s : sessions) if (s.id.equals(sid)) s.title = title;
            if (sid.equals(currentSessionId)) {
                currentTitle = title;
                if (convo != null) convo.setTitleText(title);
            }
            if (listScreen != null) listScreen.setRows(buildRows());
        }
    }

    // ============================================================ 事件归并

    private void applyEvent(String type, JSONObject payload, Object seq, Object time, boolean historical) {
        long t = 0;
        try { if (time != null) t = Long.parseLong(String.valueOf(time)) * 1000L; } catch (Throwable ignored) { }
        if (t <= 0) t = System.currentTimeMillis();

        Long seqNum = null;
        try { if (seq != null) seqNum = Long.parseLong(String.valueOf(seq)); } catch (Throwable ignored) { }

        if (type == null) return;

        // 网关对「白名单外」的事件只给 type、不给 data（buildWireEvent 的 default 分支）。
        // 这类事件改走投影查询或尾部补拉，否则会静默丢失。
        if (payload == null) {
            if ("todo/write".equals(type)) {
                if (!currentSessionId.isEmpty()) gw.requestTasks(currentSessionId);
            } else if ("goal/change".equals(type)) {
                if (!currentSessionId.isEmpty()) gw.requestGoal(currentSessionId);
            } else if ("deliverables/presented".equals(type)) {
                refetchTailForDeliverables();
            }
            return;
        }

        switch (type) {
            case "user/message": {
                String text = textOfMessage(payload);
                if (isInjectedContext(payload, text)) return;
                if (pendingUserText != null
                        && (pendingUserText.trim().equals(text.trim()) || text.trim().isEmpty())) {
                    ChatItem pend = byKey.remove("pending-user");
                    if (pend != null) items.remove(pend);
                    pendingUserText = null;
                }
                String key = seqNum != null ? "u:" + seqNum : "u:" + t + ":" + text.hashCode();
                if (byKey.containsKey(key)) return;
                if (seqNum != null && !seenSeq.add(seqNum)) return;
                ChatItem it = ChatItem.of(ChatItem.USER, key, text);
                it.time = t;
                collectAttachmentIds(payload, it);
                byKey.put(key, it);
                items.add(it);
                if (!it.attachmentIds.isEmpty() && !currentSessionId.isEmpty()) {
                    for (String aid : it.attachmentIds) gw.requestAttachment(currentSessionId, aid);
                }
                break;
            }
            case "assistant/message": {
                String text = textFromBlocks(payload, "text");
                String reasoning = textFromBlocks(payload, "reasoning");
                String key = payload != null && payload.has("turn")
                        ? "a:" + payload.optInt("turn") + ":" + payload.optInt("step")
                        : (seqNum != null ? "a:" + seqNum : "a:" + t);
                ChatItem it = byKey.get(key);
                if (text.trim().isEmpty()) {
                    // 纯工具调用的回合在历史里没有正文：桌面端也不显示空气泡，这里同样不建
                    if (it != null && it.text.trim().isEmpty()) {
                        byKey.remove(key);
                        items.remove(it);
                    }
                } else {
                    if (it == null) {
                        it = ChatItem.of(ChatItem.ASSISTANT, key, "");
                        byKey.put(key, it);
                        items.add(it);
                    }
                    it.text = text;
                    if (!reasoning.isEmpty()) it.reasoning = reasoning;
                    it.streaming = false;
                    it.time = t;
                }
                // 持久消息接管后清掉同 turn 的临时流
                if (streamAttemptKey != null) {
                    ChatItem tmp = byKey.remove(streamAttemptKey);
                    if (tmp != null) items.remove(tmp);
                    streamAttemptKey = null;
                }
                setRunning(false);
                break;
            }
            case "assistant/attempt": {
                break;
            }
            case "command/run": {
                if (payload == null) break;
                String cid = payload.optString("commandId", String.valueOf(t));
                String key = "cmd:" + cid;
                if (byKey.containsKey(key)) break;
                String name = payload.optString("name", "");
                String args = payload.optString("args", "").trim();
                ChatItem it = ChatItem.of(ChatItem.SYSTEM, key, "\u25B8 /" + name + (args.isEmpty() ? "" : " " + args));
                it.time = t;
                byKey.put(key, it);
                items.add(it);
                break;
            }
            case "command/done": {
                if (payload == null) break;
                String cid = payload.optString("commandId", "");
                ChatItem it = byKey.get("cmd:" + cid);
                if (it == null) break;
                String kind = payload.optString("kind", "");
                String text = payload.optString("text", "");
                it.text = ("success".equals(kind) ? "\u2713 " : "\u2715 ") + text;
                break;
            }
            case "deliverables/presented": {
                if (payload == null) break;
                JSONArray fs = payload.optJSONArray("files");
                if (fs == null || fs.length() == 0) break;
                String cid = payload.optString("callId", String.valueOf(t));
                String key = "files:" + cid;
                if (byKey.containsKey(key)) break;
                ChatItem it = ChatItem.of(ChatItem.FILES, key, "");
                it.files = fs;
                it.time = t;
                byKey.put(key, it);
                items.add(it);
                break;
            }
            case "approval/asked": {
                if (payload == null) break;
                String aid = payload.optString("id", "");
                if (aid.isEmpty()) break;
                String key = "approval:" + aid;
                ChatItem it = byKey.get(key);
                if (it == null) {
                    it = ChatItem.of(ChatItem.APPROVAL, key, "");
                    byKey.put(key, it);
                    items.add(it);
                }
                it.approvalId = aid;
                it.callId = payload.optString("callId", "");
                it.toolName = payload.optString("toolName", "");
                it.reason = payload.optString("reason", "");
                it.time = t;
                break;
            }
            case "approval/decided": {
                if (payload == null) break;
                String aid = payload.optString("id", "");
                ChatItem it = byKey.get("approval:" + aid);
                if (it == null) break;
                it.resolved = true;
                it.resolvedOutcome = payload.optString("outcome", "");
                break;
            }
            case "todo/write": {
                if (payload != null) applyTodos(payload.optJSONArray("todos"));
                break;
            }
            case "goal/change": {
                if (payload != null) applyGoal(payload.optJSONObject("goal"));
                break;
            }
            case "tool/call": {
                if (payload == null) break;
                String callId = payload.optString("callId", "");
                String key = "tool:" + callId;
                ChatItem it = byKey.get(key);
                if (it == null) {
                    it = ChatItem.of(ChatItem.TOOL, key, "");
                    it.callId = callId;
                    byKey.put(key, it);
                    items.add(it);
                }
                it.toolName = payload.optString("name", "tool");
                it.toolRunning = true;
                it.toolError = false;
                it.time = t;
                it.toolPreview = prettifyArgs(payload.optString("arguments", ""));
                break;
            }
            case "tool/result": {
                if (payload == null) break;
                String callId = payload.optString("callId", "");
                if (callId.isEmpty()) {
                    JSONObject tm = payload.optJSONObject("message");
                    if (tm != null) {
                        JSONObject tsrc = tm.optJSONObject("source");
                        if (tsrc != null) callId = tsrc.optString("callId", "");
                        if (callId.isEmpty()) callId = tm.optString("toolCallId", "");
                    }
                }
                if (callId.isEmpty()) callId = payload.optString("toolCallId", "");
                ChatItem it = byKey.get("tool:" + callId);
                if (it == null) break;
                it.toolRunning = false;
                boolean err = payload.optBoolean("isError", false);
                JSONObject rmsg = payload.optJSONObject("message");
                if (rmsg != null && rmsg.optBoolean("isError", false)) err = true;
                it.toolError = err;
                // live 形态给 preview；history 形态把结果放在 message.content[]
                String preview = payload.optString("preview", "");
                if (preview.isEmpty()) preview = textFromBlocks(payload, "text");
                if (!preview.isEmpty()) {
                    if (preview.length() > 400) preview = preview.substring(0, 400) + "…";
                    it.toolPreview = preview;
                }
                break;
            }
            case "turn/start": {
                turnStartedAt = t;
                setRunning(true);
                break;
            }
            case "turn/end": {
                setRunning(false);
                turnStartedAt = 0;
                // 某些历史窗口里 tool/result 不在其中，避免工具条永远停在「运行中」
                for (ChatItem ci : items) {
                    if (ci.kind == ChatItem.TOOL && ci.toolRunning) ci.toolRunning = false;
                }
                break;
            }
            case "session/title": {
                String title = payload == null ? "" : payload.optString("title", "");
                if (!title.isEmpty()) {
                    currentTitle = title;
                    store.cacheTitle(currentSessionId, title);
                    for (SessionInfo si : sessions) if (si.id.equals(currentSessionId)) si.title = title;
                    if (convo != null) convo.setTitleText(title);
                }
                break;
            }
            default:
                break;
        }
    }

    /**
     * DSH 会把运行时上下文、系统提醒、技能清单等作为 user/message 注入。
     * 桌面端不展示这些；手机上若不滤掉，就是一屏巨大的蓝色用户气泡。
     */
    private static boolean isInjectedContext(JSONObject payload, String text) {
        // 根上的判据：真正的用户输入 source.kind 一定是 "user"。
        // DSH 会把运行时上下文、系统提醒、审批策略变更等也作为 user/message 写进日志，
        // 桌面端不展示这些；手机上若不过滤就是一屏巨大的假用户气泡。
        if (payload != null) {
            JSONObject src = payload.optJSONObject("source");
            if (src != null) {
                String kind = src.optString("kind", "");
                if (!kind.isEmpty() && !"user".equals(kind)) return true;
            }
        }
        if (text == null) return false;
        String t = text.trim();
        if (t.startsWith("<")) return true;
        return t.startsWith("Current runtime context")
                || t.startsWith("Time sampled while preparing")
                || t.startsWith("Browser time zone for this request")
                || t.startsWith("Elapsed since")
                || t.startsWith("The approval policy changed");
    }

    /**
     * 抽取助手消息的正文 / 思考，兼容两种形态：
     *   live event 帧：{ turn, step, text, reasoning, toolCalls }
     *   history 原始 ：{ turn, step, message:{ role, content:[{type:'text'|'reasoning', text}] }, usage }
     * 早先只读 data.text —— 历史事件里那是空的，于是满屏「(空响应)」。
     */
    /** 把工具参数字符串整理成人能读的样子；不是 JSON 就原样截断。 */
    private static String prettifyArgs(String args) {
        if (args == null) return "";
        try {
            JSONObject a = new JSONObject(args);
            StringBuilder sb = new StringBuilder();
            String desc = a.optString("description", "");
            String cmd = a.optString("command", "");
            String path = a.optString("file_path", a.optString("path", a.optString("pattern", "")));
            if (!desc.isEmpty()) sb.append(desc);
            if (!cmd.isEmpty()) { if (sb.length() > 0) sb.append('\n'); sb.append(cmd); }
            if (!path.isEmpty()) { if (sb.length() > 0) sb.append('\n'); sb.append(path); }
            if (sb.length() > 0) args = sb.toString();
        } catch (Throwable ignored) { }
        return args.length() > 400 ? args.substring(0, 400) + "…" : args;
    }

    /** 从消息体里收集图片附件 id（历史形态：content[].attachment.attachmentId）。 */
    private static void collectAttachmentIds(JSONObject payload, ChatItem into) {
        if (payload == null) return;
        JSONObject msg = payload.optJSONObject("message");
        JSONArray content = payload.optJSONArray("content");
        if (content == null && msg != null) content = msg.optJSONArray("content");
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject b = content.optJSONObject(i);
                if (b == null || !"image".equals(b.optString("type", ""))) continue;
                JSONObject att = b.optJSONObject("attachment");
                String aid = att == null ? "" : att.optString("attachmentId", "");
                if (!aid.isEmpty() && !into.attachmentIds.contains(aid)) into.attachmentIds.add(aid);
            }
        }
        JSONArray imgs = payload.optJSONArray("images");
        if (imgs != null) {
            for (int i = 0; i < imgs.length(); i++) {
                JSONObject a = imgs.optJSONObject(i);
                String aid = a == null ? "" : a.optString("attachmentId", "");
                if (!aid.isEmpty() && !into.attachmentIds.contains(aid)) into.attachmentIds.add(aid);
            }
        }
    }

    private static String textFromBlocks(JSONObject payload, String blockType) {
        if (payload == null) return "";
        String direct = payload.optString(blockType, "");
        if (!direct.isEmpty()) return direct;
        JSONObject msg = payload.optJSONObject("message");
        if (msg == null) return "";
        JSONArray content = msg.optJSONArray("content");
        if (content == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject b = content.optJSONObject(i);
            if (b == null || !blockType.equals(b.optString("type", ""))) continue;
            String seg = b.optString("text", "");
            if (seg.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(seg);
        }
        return sb.toString();
    }

    /** 兼容 live event（带 text）与 history 原始事件（data.content[]）。 */
    private static String textOfMessage(JSONObject payload) {
        if (payload == null) return "";
        String direct = payload.optString("text", "");
        if (!direct.isEmpty()) return direct;
        JSONArray content = payload.optJSONArray("content");
        if (content == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject b = content.optJSONObject(i);
            if (b == null) continue;
            if ("text".equals(b.optString("type", ""))) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(b.optString("text", ""));
            } else if ("image".equals(b.optString("type", ""))) {
                if (sb.length() > 0) sb.append('\n');
                sb.append("[图片]");
            }
        }
        return sb.toString();
    }

    private static String streamTextOf(JSONObject attempt) {
        StringBuilder sb = new StringBuilder();
        JSONArray stream = attempt.optJSONArray("stream");
        if (stream == null) return "";
        for (int i = 0; i < stream.length(); i++) {
            JSONObject s = stream.optJSONObject(i);
            if (s == null) continue;
            JSONArray texts = s.optJSONArray("texts");
            if (texts == null) continue;
            for (int j = 0; j < texts.length(); j++) sb.append(texts.optString(j, ""));
        }
        return sb.toString();
    }

    /** 从历史事件里取最后一条非空的 session/title。 */
    private static String extractTitle(JSONArray events) {
        if (events == null) return "";
        String best = "";
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null || !"session/title".equals(e.optString("type", ""))) continue;
            JSONObject d = e.optJSONObject("data");
            if (d == null) continue;
            String t = d.optString("title", "");
            if (!t.trim().isEmpty()) best = t.trim();
        }
        return best;
    }

    private void pumpTitleQueue() {
        while (titleInFlight < TITLE_MAX_INFLIGHT && !titleQueue.isEmpty()) {
            String sid = titleQueue.poll();
            if (sid == null || sid.isEmpty()) continue;
            if (!store.cachedTitle(sid).isEmpty()) continue;
            titleInFlight++;
            titleAwaiting.add(sid);
            gw.requestSessionTitle(sid);
        }
    }

    private void rebuildOrder() {
        // 保序：按加入顺序即事件到达顺序，但把尚未定型的流式条目挪到最后
        if (streamAttemptKey == null) return;
        ChatItem stream = byKey.get(streamAttemptKey);
        if (stream == null) return;
        int idx = items.indexOf(stream);
        if (idx >= 0 && idx != items.size() - 1) {
            items.remove(idx);
            items.add(stream);
        }
    }

    private void setRunning(boolean value) {
        running = value;
        runningUncertain = false;   // 有明确结论了，清掉"待补判"标记（评审 N1）
        if (convo != null) {
            convo.setRunning(value, runningHint());
        }
        if (!value) turnStartedAt = 0;
    }

    // ---- 发送确认看门狗（评审 P0-2）
    // 现状：onSend 之后只有少数事件会复位 running，断线/丢包时按钮会永久变 ■，
    // 消息发不出去且切会话/重连都不解除。这里发送后起一个 30s 看门狗，
    // 到期仍未收到该会话任何响应 → 复位并明确告诉用户"可能没发出去，可以重试"。

    private static final long SEND_WATCHDOG_MS = 30_000L;
    /**
     * 图片发送的看门狗时长。单帧 base64 可达 ~4MB（3MB 图片编码后约 4MB），
     * 慢速上行 30s 内发不完 —— 用 30s 会误报"没发出去"并诱导用户重复发送（评审 P1-9）。
     * 按负载放大到 120s。
     */
    private static final long IMAGE_WATCHDOG_MS = 120_000L;
    private final android.os.Handler uiHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    /** 最近一次从输入框发出的原文：看门狗超时时回填（评审 P1-10）。 */
    private String lastSentText = "";

    private final Runnable sendWatchdog = new Runnable() {
        @Override public void run() {
            if (!running) return;
            setRunning(false);
            // ConversationView 是先 input.setText("") 再 host.onSend(text) 的，所以提示
            // "可以重试"的那一刻输入框已经空了。这里把原文回填回去（只在输入框仍为空时填，
            // 不覆盖用户已经敲的新内容）。
            if (convo != null && lastSentText != null && !lastSentText.isEmpty()
                    && convo.draftText().trim().isEmpty()) {
                convo.setDraft(lastSentText);
                convo.focusInput();
            }
            Toast.makeText(MainActivity.this, "没收到电脑的回应，可能没发出去，可以重试",
                    Toast.LENGTH_LONG).show();
        }
    };

    private void armSendWatchdog() { armSendWatchdog(SEND_WATCHDOG_MS); }

    private void armSendWatchdog(long ms) {
        uiHandler.removeCallbacks(sendWatchdog);
        uiHandler.postDelayed(sendWatchdog, ms);
    }

    private void cancelSendWatchdog() {
        uiHandler.removeCallbacks(sendWatchdog);
    }

    // ---- 审批/提问的"回执看门狗"（评审 P0-3 补齐）
    // canSend() 只保证 state==READY；电脑端关掉网卡时 TCP 不会立刻 FIN，state 最长 75s
    // 都还是 READY → 卡片被乐观标成「✓ 已批准」，帧其实进了黑洞。
    // 网关对 approval-response / question-answer 都有回执帧（approval-resolved /
    // question-response，见 tools/mock-gateway.mjs 的对应分支），所以等不到回执就把卡片
    // 回滚为未处理（按钮重新出现）并提示重试。

    private static final long INTERACTION_WATCHDOG_MS = 6_000L;
    /**
     * 正在等回执的卡片：按卡片 key 一一对应（评审 P0-3）。
     * 原来是单槽 pendingInteraction，先点「批准」再在 6s 内点「提交」时，
     * armInteractionWatchdog 会 cancel 前一个 → 批准帧若丢在黑洞里，那张卡就永远停在
     * 「✓ 已批准」。改成 map 后每张待确认的卡各自一个看门狗。
     */
    private final Map<String, ChatItem> pendingInteractions = new HashMap<>();
    /** key -> 该卡片的看门狗任务，用于按 key 精确取消。 */
    private final Map<String, Runnable> interactionWatchdogs = new HashMap<>();

    /**
     * 到期回滚：按 key 取"当前在列表里的那张卡"再回滚。
     * 不持有 arm 时的对象引用 —— 重建后（onSnapshot/subscribeCurrent）旧对象已不在 items 里，
     * 回滚它会没人看得见，还会误弹"电脑端没有确认这次操作"（实际已批准）。
     */
    private void rollbackInteraction(final String key) {
        interactionWatchdogs.remove(key);
        ChatItem armed = pendingInteractions.remove(key);
        if (armed == null) return;
        ChatItem live = byKey.get(key);
        if (live == null) return;                     // 卡片已不在列表：没有可回滚的对象
        if (live != armed) {                          // 重建后迁移迟到：先把待确认状态搬到当前这张
            live.resolved = armed.resolved;
            live.resolvedOutcome = armed.resolvedOutcome;
            live.pendingConfirm = armed.pendingConfirm;
            live.sendError = armed.sendError;
        }
        // 只有"确实还在等回执"才回滚：
        //  - 已收到回执 → pendingConfirm 已置 false，不动；
        //  - 网关重放把卡片复位成了新的待处理卡 → pendingConfirm 也已置 false，不动；
        //  - 还在等 → 回滚为未处理，按钮重新出现，用户可以再点一次。
        if (!live.pendingConfirm) return;
        live.pendingConfirm = false;
        live.resolved = false;                        // 回滚：按钮重新出现，用户可再点一次
        live.resolvedOutcome = "";
        live.sendError = "未确认，请重试";
        if (convo != null) { convo.setItems(items); convo.refreshNow(); }
        Toast.makeText(MainActivity.this, "电脑端没有确认这次操作，请重试", Toast.LENGTH_LONG).show();
    }

    private void armInteractionWatchdog(final ChatItem item) {
        if (item == null || item.key == null || item.key.isEmpty()) return;
        final String key = item.key;
        cancelInteractionWatchdog(key);
        Runnable task = new Runnable() {
            @Override public void run() { rollbackInteraction(key); }
        };
        pendingInteractions.put(key, item);
        interactionWatchdogs.put(key, task);
        uiHandler.postDelayed(task, INTERACTION_WATCHDOG_MS);
    }

    private void cancelInteractionWatchdog(String key) {
        if (key == null || key.isEmpty()) return;
        Runnable task = interactionWatchdogs.remove(key);
        if (task != null) uiHandler.removeCallbacks(task);
        pendingInteractions.remove(key);
    }

    /**
     * 卡片重建后迁移待确认状态（评审 P0-3）。
     * onSnapshot 会 items.clear()+byKey.clear() 重建 ChatItem，旧对象从此不在列表里；
     * 这里把乐观的「已批准/已回答」搬到新建的同 key 卡片上，并更新看门狗的落点：
     *   - 新卡上已有 resolved（网关历史里存在 approval/decided 等）= 回执等价物 → 撤看门狗；
     *   - 否则把旧的乐观状态复制过去，让「✓ 已批准」在重建后不闪回按钮；
     *   - 新卡不存在时保留条目：看门狗到期会按 key 找不到对象而安静退出，不会误报。
     */
    private void migratePendingInteractions() {
        if (pendingInteractions.isEmpty()) return;
        java.util.List<String> received = new java.util.ArrayList<>();
        for (Map.Entry<String, ChatItem> e : pendingInteractions.entrySet()) {
            ChatItem fresh = byKey.get(e.getKey());
            if (fresh == null || fresh == e.getValue()) continue;
            if (fresh.resolved) { received.add(e.getKey()); continue; }
            ChatItem old = e.getValue();
            fresh.resolved = old.resolved;
            fresh.resolvedOutcome = old.resolvedOutcome;
            fresh.pendingConfirm = old.pendingConfirm;
            fresh.sendError = old.sendError;
            e.setValue(fresh);
        }
        for (String key : received) cancelInteractionWatchdog(key);
    }

    /**
     * 找审批卡：优先按 approvalId（真实网关的 approval-resolved 一定带，见
     * dsh-plugin-mobile-gateway/lib/index.mjs:2454），缺失时退回 rpcId。
     * 回执匹配不上就会被回执看门狗误判成"没确认"而回滚，所以这里宁松勿严。
     */
    private ChatItem approvalCard(String rpcId, String approvalId) {
        ChatItem it = approvalId.isEmpty() ? null : byKey.get("approval:" + approvalId);
        if (it == null && !rpcId.isEmpty()) it = byKey.get("approval:" + rpcId);
        return it;
    }

    private void subscribeCurrent() {
        if (currentSessionId.isEmpty()) return;
        items.clear();
        byKey.clear();
        seenSeq.clear();
        streamAttemptKey = null;
        hasMore = false;
        nextBeforeSeq = null;
        planGoal = ""; planPhase = ""; planTodos = "";
        // 重订阅是把列表清空、等回 snapshot 再重建（重建在 onSnapshot 里完成）。
        // 此刻 byKey 为空，migratePendingInteractions() 只做"保留待确认条目"这件事：
        // 卡片不会丢状态，等 snapshot 到达后再按 key 迁移到新对象（评审 P0-3）。
        migratePendingInteractions();
        gw.subscribe(currentSessionId);
        gw.requestTasks(currentSessionId);
        gw.requestGoal(currentSessionId);
        if (screen != Screen.CHAT) showChat();
        if (convo != null) { convo.setItems(items); convo.refreshNow(); }
    }

    // ============================================================ SessionListView.Host

    @Override
    public void onOpenSession(SessionInfo s) {
        pendingBySession.remove(s.id);
        s.pending = 0;
        currentSessionId = s.id;
        currentTitle = s.display();
        currentCwd = s.cwd;
        subscribeCurrent();
    }

    @Override
    public void onNewChat() {
        currentSessionId = "";
        currentTitle = "新对话";
        currentCwd = "";
        items.clear();
        byKey.clear();
        seenSeq.clear();
        streamAttemptKey = null;
        hasMore = false;
        nextBeforeSeq = null;
        setRunning(false);
        showChat();
    }

    @Override
    public void onVoiceInput() {
        try {
            Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
            i.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "说点什么…");
            startActivityForResult(i, REQ_VOICE);
        } catch (Throwable t) {
            // 部分国产 ROM（如荣耀）不提供 AOSP 的 RECOGNIZE_SPEECH Activity，
            // 只能退化为提示。Toast 容易被错过，这里同时写进会话顶部横幅。
            if (convo != null) {
                convo.setBanner("这台设备没有系统语音识别服务；可长按输入框重试，或直接用键盘自带的麦克风", false);
            }
            Toast.makeText(this, "这台设备没有可用的系统语音识别；可以用键盘自带的麦克风输入",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onPickImage() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "选择要发送的图片"), REQ_IMAGE);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开相册/文件选择器", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onSettings() { showSettings(); }

    @Override
    public void onRefresh() {
        gw.requestSessions();
        Toast.makeText(this, "已刷新", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRename(SessionInfo s) {
        final EditText input = new EditText(this);
        input.setText(s.display());
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("重命名对话")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    String t = input.getText().toString().trim();
                    if (t.isEmpty()) return;
                    // 断网时 sendRaw 是静默失败的：先确认能发再发，别让用户以为改好了（评审 P1-12）
                    if (!gw.canSend()) {
                        Toast.makeText(this, "还没连上电脑端，重命名没有发出去；连上后请重试",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    gw.renameSession(s.id, t);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onArchive(SessionInfo s) {
        // 断网时 sendRaw 静默失败：既不能谎报成功，也不能动本地的 archivedIds
        // ——旧写法断网也把行删掉，重启后它又回来（评审 P1-12）。
        if (!gw.canSend()) {
            Toast.makeText(this, "还没连上电脑端，这次归档没有发出去；连上后请重试",
                    Toast.LENGTH_LONG).show();
            return;
        }
        gw.archiveSession(s.id);
        archivedIds.add(s.id);
        if (listScreen != null) listScreen.setRows(buildRows());
    }

    // ============================================================ ConversationView.Host

    @Override
    public void onBack() {
        if (screen == Screen.SETTINGS) showList();
        else showList();
    }

    @Override
    public void onSend(String text) {
        if (convo != null) convo.setBanner(null, false);
        if (gw.state() != GatewayClient.State.READY) {
            Toast.makeText(this, "还没连上电脑端，请先在设置里配对/连接", Toast.LENGTH_LONG).show();
            return;
        }
        lastSentText = text == null ? "" : text;
        if (currentSessionId.isEmpty()) {
            // 新会话：先本地回显，等 sent 回来拿 sessionId
            pendingUserText = text;
            ChatItem pend = ChatItem.of(ChatItem.USER, "pending-user", text);
            byKey.put("pending-user", pend);
            items.add(pend);
            if (convo != null) { convo.setItems(items); convo.refreshNow(); convo.scrollToBottom(); }
            gw.sendMessage("", text);
        } else {
            gw.sendMessage(currentSessionId, text);
        }
        setRunning(true);
        if (convo != null) convo.setRunning(true, runningHint());
        armSendWatchdog();
    }

    @Override
    public void onStop() {
        if (currentSessionId.isEmpty()) return;
        // 断网时"已请求停止"是谎报：停止帧进黑洞，用户以为停了，实际回合还在跑（评审 P1-12）
        if (!gw.canSend()) {
            Toast.makeText(this, "还没连上电脑端，停止请求没有发出去；连上后请重试",
                    Toast.LENGTH_LONG).show();
            return;
        }
        gw.stopSession(currentSessionId);
        Toast.makeText(this, "已请求停止", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLoadMore() {
        if (!hasMore || nextBeforeSeq == null || currentSessionId.isEmpty()) return;
        gw.requestHistory(currentSessionId, nextBeforeSeq, historyFormatVersion);
        hasMore = false;
    }

    @Override
    public void onSetDisplayMode(String mode) {
        store.setDisplayMode(mode);
        if (convo != null) {
            convo.setCompact("compact".equals(mode));
            convo.refreshNow();
        }
    }

    @Override
    public void onMenu() {
        final String[] opts = currentSessionId.isEmpty()
                ? new String[] { "连接设置" }
                : new String[] { "重命名", "停止当前回合", "连接设置" };
        new AlertDialog.Builder(this).setItems(opts, (d, which) -> {
            if ("重命名".equals(opts[which])) {
                for (SessionInfo s : sessions) {
                    if (s.id.equals(currentSessionId)) { onRename(s); return; }
                }
            } else if ("停止当前回合".equals(opts[which])) {
                onStop();
            } else {
                showSettings();
            }
        }).show();
    }

    @Override
    public void onApprove(ChatItem item, String outcome) {
        // 断网时 sendRaw 是静默失败的：先确认真的能发，再把卡片置为已处理，
        // 否则卡片显示"✓ 已批准"而电脑端永远收不到，回合僵死（评审 P0-3）。
        if (!gw.canSend()) {
            item.sendError = "未发送（未连接），恢复后请重试";
            if (convo != null) { convo.setItems(items); convo.refreshNow(); }
            Toast.makeText(this, "还没连上电脑端，这次批准没有发出去；连上后请再点一次",
                    Toast.LENGTH_LONG).show();
            return;
        }
        item.sendError = "";
        gw.approvalResponse(item.rpcId, currentSessionId, item.approvalId, outcome);
        // 先显示「已发送，等待电脑确认…」：收到 approval-resolved / approval-response 回执
        // 才显示 ✓；6s 内收不到就回滚为未处理。不做乐观 ✓（回执没到就可能是进了黑洞）（评审 P0-3）。
        item.pendingConfirm = true;
        item.resolved = false;
        item.resolvedOutcome = outcome;
        if (convo != null) { convo.setItems(items); convo.refreshNow(); }
        armInteractionWatchdog(item);   // 等 approval-resolved / approval-response 回执，超时回滚
    }

    @Override
    public void onQuestionSubmit(ChatItem item, JSONArray answers) {
        if (!gw.canSend()) {
            item.sendError = "未发送（未连接），恢复后请重试";
            if (convo != null) { convo.setItems(items); convo.refreshNow(); }
            Toast.makeText(this, "还没连上电脑端，这次回答没有发出去；连上后请再点一次提交",
                    Toast.LENGTH_LONG).show();
            return;
        }
        item.sendError = "";
        gw.questionAnswer(item.rpcId, currentSessionId, answers);
        // 同审批卡：等 question-response(accepted:true) 回执才显示 ✓（评审 P0-3）
        item.pendingConfirm = true;
        item.resolved = false;
        item.resolvedOutcome = "answered";
        if (convo != null) { convo.setItems(items); convo.refreshNow(); }
        armInteractionWatchdog(item);   // 等 question-response 回执，超时回滚
    }

    @Override
    public void onQuestionCancel(ChatItem item) {
        if (!gw.canSend()) {
            item.sendError = "未发送（未连接），恢复后请重试";
            if (convo != null) { convo.setItems(items); convo.refreshNow(); }
            Toast.makeText(this, "还没连上电脑端，这次跳过没有发出去；连上后请再点一次",
                    Toast.LENGTH_LONG).show();
            return;
        }
        item.sendError = "";
        gw.questionCancel(item.rpcId, currentSessionId);
        // 同审批卡：等 question-response 回执才落定（评审 P0-3）
        item.pendingConfirm = true;
        item.resolved = false;
        item.resolvedOutcome = "cancelled";
        if (convo != null) { convo.setItems(items); convo.refreshNow(); }
        armInteractionWatchdog(item);   // 等 question-response 回执，超时回滚
    }

    // ============================================================ SettingsView.Host

    @Override
    public void onScanQr() {
        startActivityForResult(new Intent(this, QrScanActivity.class), REQ_QR);
    }

    @Override
    public void onConnect(String lan, String wan, boolean useWan, String token, String deviceName) {
        if (lan.isEmpty() && wan.isEmpty()) {
            Toast.makeText(this, "请先填写内网地址或公网地址", Toast.LENGTH_SHORT).show();
            return;
        }
        String active = (useWan && !wan.isEmpty()) ? wan : (lan.isEmpty() ? wan : lan);
        String problem = GatewayClient.cleartextProblem(active);
        if (problem != null) {
            Toast.makeText(this, problem, Toast.LENGTH_LONG).show();
            return;
        }
        if (!deviceName.isEmpty()) store.setDeviceName(deviceName);
        store.setLanUrl(lan);
        store.setWanUrl(wan);
        store.setUseWan(useWan);
        store.setToken(token);
        store.setUrl(active);
        if (token.isEmpty()) {
            Toast.makeText(this, "没有设备令牌，请点「扫码配对」或「粘贴配对串」", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, (useWan ? "连接公网" : "连接内网") + "：" + hostOf(active), Toast.LENGTH_SHORT).show();
        gw.connect(store.url(), token, store.deviceId(), store.deviceName());
        showList();
    }

    @Override
    public void onOpenFeedback() {
        final android.widget.EditText ed = new android.widget.EditText(this);
        ed.setHint("哪里别扭、想要什么功能、哪里报错…");
        ed.setMinLines(4);
        ed.setMaxLines(10);
        ed.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        ed.setText(lastFeedbackDraft);
        android.widget.LinearLayout box = Ui.col(this);
        int pad = Ui.dp(this, 18);
        box.setPadding(pad, Ui.dp(this, 6), pad, 0);
        box.addView(ed);
        android.widget.TextView env = Ui.text(this, "会自动附上：" + feedbackEnv(), 11.5f, Ui.INK_FAINT, false);
        env.setPadding(0, Ui.dp(this, 8), 0, 0);
        box.addView(env);
        final boolean toAuthor = feedbackChannel() != null;
        if (toAuthor) {
            android.widget.TextView hint = Ui.text(this,
                    "点「发给作者」会用浏览器/邮件打开，把内容发给开发这个 App 的人。",
                    11.5f, Ui.INK_FAINT, false);
            hint.setPadding(0, Ui.dp(this, 6), 0, 0);
            box.addView(hint);
        }

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this)
                .setTitle("意见反馈")
                .setView(box);
        if (toAuthor) {
            b.setPositiveButton("发给作者", (d, w) -> sendToAuthor(ed.getText().toString()))
                    .setNeutralButton("复制", (d, w) -> {
                        copyFeedback(feedbackText(ed.getText().toString()));
                        Toast.makeText(this, "已复制，可粘到任意地方", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("发到我的电脑", (d, w) -> sendFeedback(ed.getText().toString()));
        } else {
            b.setPositiveButton("发到电脑", (d, w) -> sendFeedback(ed.getText().toString()))
                    .setNeutralButton("复制", (d, w) -> {
                        copyFeedback(feedbackText(ed.getText().toString()));
                        Toast.makeText(this, "已复制，可粘到任意地方", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null);
        }
        b.show();
    }

    /**
     * 作者在 dist/version.json 的 feedback 段里配置的反馈通道。
     * 之前的做法是把反馈发进「用户自己电脑上的 DSH」，那只对作者本人有意义；
     * 发给别人用的 App 必须把反馈送回作者，所以这里走公开通道（GitHub Issue 或邮件）。
     */
    private JSONObject feedbackChannel() {
        String cfg = store.feedbackCfg();
        if (cfg.isEmpty()) return null;
        try { return new JSONObject(cfg); } catch (Throwable t) { return null; }
    }

    private String buildFeedbackUrl(String text) {
        JSONObject fb = feedbackChannel();
        if (fb == null) return "";
        String issues = fb.optString("issues", "");
        String email = fb.optString("email", "");
        String title = "【DSH 掌上通】意见反馈 v" + myVersionName();
        try {
            // 邮箱优先（作者指定），其次 GitHub Issue
            if (!email.isEmpty()) {
                return "mailto:" + email
                        + "?subject=" + android.net.Uri.encode(title)
                        + "&body=" + android.net.Uri.encode(text);
            }
            if (!issues.isEmpty()) {
                String labels = fb.optString("labels", "");
                return issues + (issues.contains("?") ? "&" : "?")
                        + "title=" + android.net.Uri.encode(title)
                        + "&body=" + android.net.Uri.encode(text)
                        + (labels.isEmpty() ? "" : "&labels=" + android.net.Uri.encode(labels));
            }
        } catch (Throwable ignored) { }
        return "";
    }

    private void sendToAuthor(String body) {
        String t = body == null ? "" : body.trim();
        if (t.isEmpty()) {
            Toast.makeText(this, "先写点内容吧", Toast.LENGTH_SHORT).show();
            return;
        }
        store.addFeedback(t);
        final String full = feedbackText(t);
        copyFeedback(full);   // 先复制一份，任何通道失败都不丢内容
        JSONObject fb = feedbackChannel();
        String webhook = fb == null ? "" : fb.optString("webhook", "").trim();
        if (!webhook.isEmpty()) {
            // 后台通道：App 直接 POST，用户什么都不用装、不用配置
            postFeedback(webhook, fb.optString("webhookKind", "generic"), full);
            return;
        }
        String url = buildFeedbackUrl(full);
        if (url.isEmpty()) {
            Toast.makeText(this, "没有取到作者的反馈通道，已复制到剪贴板", Toast.LENGTH_LONG).show();
            refreshFeedbackHint();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
            refreshFeedbackHint();
        } catch (Throwable e) {
            // 手机没装邮件 App 时 mailto 会打不开：改开 GitHub Issue 页（浏览器一定有）
            String issues = fb == null ? "" : fb.optString("issues", "");
            if (url.startsWith("mailto:") && !issues.isEmpty()) {
                try {
                    String q = issues + (issues.contains("?") ? "&" : "?")
                            + "title=" + android.net.Uri.encode("【DSH 掌上通】意见反馈 v" + myVersionName())
                            + "&body=" + android.net.Uri.encode(full);
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(q)));
                    Toast.makeText(this, "本机没有邮件 App，已改用网页提交（内容也已复制）",
                            Toast.LENGTH_LONG).show();
                    refreshFeedbackHint();
                    return;
                } catch (Throwable ignored2) { /* 继续走复制兜底 */ }
            }
            Toast.makeText(this, "打不开反馈通道，已复制到剪贴板：" + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 把反馈 POST 到作者配置的 webhook。不同服务要的 JSON 形状不同，这里按 kind 适配：
     *   wecom / dingtalk 企业微信、钉钉群机器人
     *   feishu           飞书群机器人
     *   serverchan       Server酱（key 在 URL 里，用 title/desp）
     *   generic          通用（Formspree 之类的转发服务，会把所有字段转发成邮件）
     */
    private void postFeedback(final String url, final String kind, final String text) {
        final String shortText = text.length() > 3000 ? text.substring(0, 3000) : text;
        Toast.makeText(this, "正在发送…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String err = null;
            java.net.HttpURLConnection c = null;
            try {
                JSONObject payload = new JSONObject();
                if ("wecom".equals(kind) || "dingtalk".equals(kind)) {
                    payload.put("msgtype", "text");
                    JSONObject inner = new JSONObject();
                    inner.put("content", shortText);
                    payload.put("text", inner);
                } else if ("feishu".equals(kind)) {
                    payload.put("msg_type", "text");
                    JSONObject inner = new JSONObject();
                    inner.put("text", shortText);
                    payload.put("content", inner);
                } else if ("serverchan".equals(kind)) {
                    payload.put("title", "DSH 掌上通 意见反馈 v" + myVersionName());
                    payload.put("desp", shortText);
                } else {
                    // 通用转发服务（FormSubmit / Formspree 之类）：把内容当表单字段发过去，
                    // 服务端再转成邮件发给作者。_ 开头的字段是 FormSubmit 的专用开关。
                    payload.put("subject", "DSH 掌上通 意见反馈 v" + myVersionName());
                    payload.put("message", shortText);
                    payload.put("text", shortText);
                    payload.put("version", myVersionName());
                    payload.put("device", android.os.Build.MODEL);
                    payload.put("_subject", "DSH 掌上通 意见反馈 v" + myVersionName());
                    payload.put("_template", "table");
                    payload.put("_captcha", "false");
                }
                byte[] data = payload.toString().getBytes("UTF-8");
                c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setRequestProperty("User-Agent", "DSH-Mobile-Android");
                // FormSubmit 这类「网页表单」服务要求带 Origin/Referer，否则拒收；
                // App 是原生请求、默认没有这两个头，这里补上作者仓库地址。
                c.setRequestProperty("Origin", "https://github.com");
                c.setRequestProperty("Referer", "https://github.com/James-Xue6/dsh-mobile-android");
                c.setRequestProperty("Accept", "application/json");
                c.setFixedLengthStreamingMode(data.length);
                java.io.OutputStream os = c.getOutputStream();
                os.write(data);
                os.flush();
                os.close();
                int code = c.getResponseCode();
                String respBody = "";
                try {
                    java.io.InputStream is = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
                    if (is != null) {
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[2048];
                        int n;
                        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                        is.close();
                        respBody = new String(bos.toByteArray(), "UTF-8");
                    }
                } catch (Throwable ignored) { }
                if (code < 200 || code >= 300) {
                    err = "HTTP " + code;
                } else if (respBody.contains("\"success\":\"false\"") || respBody.contains("\"success\": false")) {
                    // FormSubmit 之类会用 200 + success:false 表达「还没激活」等
                    err = "服务端未接受：" + (respBody.length() > 160 ? respBody.substring(0, 160) : respBody);
                }
            } catch (Throwable e) {
                err = e.getMessage() == null ? String.valueOf(e) : e.getMessage();
            } finally {
                if (c != null) try { c.disconnect(); } catch (Throwable ignored) { }
            }
            final String e = err;
            runOnUiThread(() -> {
                Toast.makeText(this, e == null ? "已发送给作者，谢谢反馈！" : "发送失败（内容已复制）：" + e,
                        Toast.LENGTH_LONG).show();
                refreshFeedbackHint();
            });
        }, "feedback-post").start();
    }

    /** 反馈时附上的环境信息，方便定位。 */
    private String feedbackEnv() {
        String ver = "?";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Throwable ignored) { }
        return "v" + ver + " · " + (store.useWan() ? "公网" : "内网") + " · " + gw.debugState();
    }

    private String feedbackText(String body) {
        String t = body == null ? "" : body.trim();
        if (t.isEmpty()) t = "(未填写文字)";
        return "【DSH 掌上通 · 意见反馈】\n" + t + "\n\n-- 环境 --\n" + feedbackEnv()
                + "\n地址 " + store.url();
    }

    private void copyFeedback(String text) {
        try {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("feedback", text));
        } catch (Throwable ignored) { }
    }

    private void sendFeedback(String body) {
        String t = body == null ? "" : body.trim();
        if (t.isEmpty()) {
            Toast.makeText(this, "先写点内容吧", Toast.LENGTH_SHORT).show();
            return;
        }
        store.addFeedback(t);
        lastFeedbackDraft = "";
        String full = feedbackText(t);
        copyFeedback(full);
        if (gw.state() != GatewayClient.State.READY) {
            Toast.makeText(this, "还没连上电脑端，已复制到剪贴板，可先粘给我", Toast.LENGTH_LONG).show();
            refreshFeedbackHint();
            return;
        }
        gw.sendMessage(currentSessionId, full);
        Toast.makeText(this, "已发出，电脑端会收到这条反馈", Toast.LENGTH_LONG).show();
        refreshFeedbackHint();
    }

    /** 首次开启公网前的安全免责声明（勾选后才继续）。 */
    private void confirmPublicAccess(final String lan, final String wan) {
        android.widget.LinearLayout box = Ui.col(this);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        box.addView(Ui.text(this,
                "\u26A0 安全免责声明\n\n"
                        + "开启公网 = 把这台电脑上的 DSH 暴露到互联网。DSH 能执行代码、读写文件，"
                        + "任何人拿到公网地址和设备令牌，都可能访问甚至操作你的电脑。\n\n"
                        + "请确认：\n"
                        + "① 妥善保管设备令牌，别把配对二维码或配对串发给别人；\n"
                        + "② 不用时及时「关闭公网隧道」，或在手机上切回「用内网」；\n"
                        + "③ 隧道域名每次重启电脑都会变，变了重新扫一次码即可；\n"
                        + "④ 公司/涉密网络请先确认合规。",
                13f, Ui.INK, false));
        final android.widget.CheckBox cb = new android.widget.CheckBox(this);
        cb.setText("我已知情，同意开启");
        cb.setTextSize(14f);
        cb.setPadding(0, Ui.dp(this, 14), 0, 0);
        box.addView(cb);

        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
                .setView(box)
                .setPositiveButton("我已知情，同意开启", null)
                .setNegativeButton("取消", null)
                .create();
        dlg.show();
        dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!cb.isChecked()) {
                Toast.makeText(this, "请先勾选「我已知情」", Toast.LENGTH_SHORT).show();
                return;
            }
            store.setRiskAck(true);
            dlg.dismiss();
            applyEndpoint(lan, wan, true);
        });
    }

    /**
     * 从配对载荷挑出手机可能连得上的地址。
     * 网关给的 publicUrl 常常是电脑本机地址（ws://127.0.0.1:…），手机永远连不上；
     * endpoints 里才有隧道 / 局域网等真实可用地址。
     */
    /**
     * 手机当前所在网段的前缀（例如 WiFi 是 192.168.2.51 则返回 "192.168.2."）；拿不到返回 ""。
     * 只认 wlan 接口，避免把 rmnet（移动数据）或虚拟网卡当成本地网段。
     */
    private static String localSubnetPrefix() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> nis =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (nis != null && nis.hasMoreElements()) {
                java.net.NetworkInterface ni = nis.nextElement();
                if (ni == null || !ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName() == null ? "" : ni.getName();
                if (!name.startsWith("wlan")) continue;
                for (java.net.InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    java.net.InetAddress a = ia.getAddress();
                    if (a == null || a.isLoopbackAddress()) continue;
                    String ip = a.getHostAddress();
                    if (ip == null || ip.indexOf(':') >= 0) continue;   // 只要 IPv4
                    int dot = ip.lastIndexOf('.');
                    if (dot > 0) return ip.substring(0, dot + 1);
                }
            }
        } catch (Throwable ignored) { }
        return "";
    }

    private static java.util.List<String> buildPairCandidates(JSONObject payload, String primary) {
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>();
        if (primary != null && !primary.trim().isEmpty()) all.add(primary.trim());
        JSONArray eps = payload.optJSONArray("endpoints");
        if (eps != null) {
            for (int i = 0; i < eps.length(); i++) {
                String e = eps.optString(i, "").trim();
                if (!e.isEmpty()) all.add(e);
            }
        }
        // 分三档：① 和手机同网段（在家必通、且最快）② 其它内网（可能是虚拟网卡，多半连不上）
        //        ③ 公网隧道（人在外面时用）。之前不分档，先撞虚拟网卡要白等十几秒超时。
        final String subnet = localSubnetPrefix();
        java.util.List<String> sameNet = new java.util.ArrayList<>();
        java.util.List<String> priv = new java.util.ArrayList<>();
        java.util.List<String> pub = new java.util.ArrayList<>();
        for (String u : all) {
            if (isLoopbackUrl(u)) continue;
            if (!Store.isPrivateUrl(u)) { pub.add(u); continue; }
            String host = hostOf(u);
            if (!subnet.isEmpty() && host.startsWith(subnet)) sameNet.add(u);
            else priv.add(u);
        }
        sameNet.addAll(priv);
        sameNet.addAll(pub);
        return sameNet;
    }

    private static boolean isLoopbackUrl(String url) {
        String s = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
        return s.contains("://127.0.0.1") || s.contains("://localhost")
                || s.contains("://[::1]") || s.contains("://0.0.0.0");
    }

    /** 用下一个候选地址继续配对；都用完则明确报错。 */
    private void pairWithNextCandidate() {
        if (pendingPairCode == null) return;
        while (pairIndex < pairCandidates.size()) {
            String target = pairCandidates.get(pairIndex++);
            if (GatewayClient.cleartextProblem(target) != null) continue;
            store.setUrl(target);
            if (settingsView != null) {
                settingsView.setStatus("正在配对 " + hostOf(target)
                        + "（第 " + pairIndex + "/" + pairCandidates.size() + " 个地址）…", false);
            }
            // 配对可能要依次试几个地址，给个可见反馈，别让人以为点了没反应
            Toast.makeText(this, "正在连接 " + hostOf(target)
                    + "（第 " + pairIndex + "/" + pairCandidates.size() + " 个）", Toast.LENGTH_SHORT).show();
            gw.pair(target, pendingPairCode, store.deviceId(), store.deviceName());
            return;
        }
        pendingPairCode = null;
        Toast.makeText(this, "配对失败：配对码里的地址都连不上。\n"
                + "请确认手机能上网；或在电脑面板重新生成二维码后重扫。", Toast.LENGTH_LONG).show();
    }

    /** 连不上时自动在「内网 / 公网」之间切一次（只切一次，避免来回跳）。 */
    private boolean tryFailover() {
        if (failoverUsed) return false;
        String lan = store.lanUrl(), wan = store.wanUrl();
        if (lan.isEmpty() || wan.isEmpty()) return false;
        boolean toWan = !store.useWan();
        // 明文校验已经在 GatewayClient.connect() 内部统一拦截；这里再挡一层，
        // 不合法就不切，免得把用户钉在一个注定被拒绝的端点上（评审 P0-6 ②）。
        String problem = GatewayClient.cleartextProblem(toWan ? wan : lan);
        if (problem != null) {
            failoverUsed = true;
            Toast.makeText(this, problem, Toast.LENGTH_LONG).show();
            return false;
        }
        failoverUsed = true;
        store.setUseWan(toWan);
        if (settingsView != null) settingsView.setUseWan(toWan);
        Toast.makeText(this, toWan ? "内网连不上，自动改用公网…" : "公网连不上，自动改用内网…",
                Toast.LENGTH_SHORT).show();
        gw.connect(store.url(), store.token(), store.deviceId(), store.deviceName());
        return true;
    }

    @Override
    public void onReconnectScheduled(String reason) {
        // 配对阶段失败：换下一个候选地址继续配对（而不是去连一个还没配对的 token）
        if (pendingPairCode != null) { pairWithNextCandidate(); return; }
        if (tryFailover()) return;
        String active = store.url();
        if (active.contains("trycloudflare.com") || reason.contains("404")) {
            Toast.makeText(this, "公网地址可能已失效（隧道域名每次重启电脑都会变）\n"
                    + "请在电脑面板重新「生成配对二维码」扫一次", Toast.LENGTH_LONG).show();
        }
    }

    // ============================================================ 版本更新提醒
    //
    // 清单放在仓库的 dist/version.json（随每次发版更新）：
    //   { versionCode, versionName, notes, url, mirror }
    // 先走 CDN（国内通常更快），失败再走 GitHub raw。
    private static final String UPDATE_MANIFEST_CDN =
            "https://cdn.jsdelivr.net/gh/James-Xue6/dsh-mobile-android@main/dist/version.json";
    private static final String UPDATE_MANIFEST_GH =
            "https://raw.githubusercontent.com/James-Xue6/dsh-mobile-android/main/dist/version.json";
    /** 自动检查的间隔（手动点「检查更新」不受限制）。 */
    private static final long UPDATE_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000;

    private int myVersionCode() {
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return android.os.Build.VERSION.SDK_INT >= 28 ? (int) pi.getLongVersionCode() : pi.versionCode;
        } catch (Throwable t) {
            return 0;
        }
    }

    private String myVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "?";
        }
    }

    /** 后台拉取版本清单；manual=true 表示用户主动点的（失败/已最新都会提示）。 */
    private void checkUpdate(final boolean manual) {
        // 不再做「6 小时节流」：这个清单同时承载作者的反馈通道（webhook 等），
        // 每次启动都拉一次（几百字节）才能保证反馈通道是最新的；
        // 而更新弹窗另有「同一版本不再弹」的去重，不会因为这里变频繁而打扰用户。
        if (manual) updateHint("正在检查…");
        new Thread(() -> {
            JSONObject m = fetchJson(UPDATE_MANIFEST_CDN);
            if (m == null) m = fetchJson(UPDATE_MANIFEST_GH);
            final JSONObject manifest = m;
            runOnUiThread(() -> {
                store.setLastUpdateCheck(System.currentTimeMillis());
                handleUpdateResult(manifest, manual);
            });
        }, "update-check").start();
    }

    private static JSONObject fetchJson(String url) {
        java.net.HttpURLConnection c = null;
        try {
            java.net.URL u = new java.net.URL(url);
            c = (java.net.HttpURLConnection) u.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "DSH-Mobile-Android");
            if (c.getResponseCode() != 200) return null;
            java.io.InputStream is = c.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            return new JSONObject(new String(bos.toByteArray(), "UTF-8"));
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) try { c.disconnect(); } catch (Throwable ignored) { }
        }
    }

    /** 最近一次更新检查的结论（设置页还没创建时先存着，创建后回填）。 */
    private String lastUpdateHint = "";

    private void updateHint(String text) {
        lastUpdateHint = text == null ? "" : text;
        if (settingsView != null) settingsView.setUpdateHint(lastUpdateHint);
    }

    private void handleUpdateResult(JSONObject m, boolean manual) {
        int mine = myVersionCode();
        if (m == null) {
            updateHint("检查失败：网络不可用");
            if (manual) Toast.makeText(this, "检查更新失败，请确认网络可用", Toast.LENGTH_LONG).show();
            return;
        }
        final int latest = m.optInt("versionCode", 0);
        final String name = m.optString("versionName", "?");
        final String notes = m.optString("notes", "");
        final String url = m.optString("url", "");
        final String mirror = m.optString("mirror", "");
        // 顺手把作者配置的反馈通道存下来（离线也能用）
        JSONObject fb = m.optJSONObject("feedback");
        if (fb != null) store.setFeedbackCfg(fb.toString());
        if (latest <= mine) {
            String txt = "已是最新版本（v" + myVersionName() + "）";
            updateHint(txt);
            if (manual) Toast.makeText(this, txt, Toast.LENGTH_SHORT).show();
            return;
        }
        updateHint("有新版本 v" + name + "（当前 v" + myVersionName() + "）");
        // 用户对同一版本点过「以后再说」就不再自动弹（手动点仍会弹）
        if (!manual && name.equals(store.skipVersion())) return;

        new android.app.AlertDialog.Builder(this)
                .setTitle("发现新版本 v" + name)
                .setMessage((notes.isEmpty() ? "有新版本可用。" : notes)
                        + "\n\n当前版本 v" + myVersionName()
                        + "\n下载后覆盖安装即可，无需卸载。")
                .setPositiveButton("立即更新", (d, w) -> {
                    String target = url.isEmpty() ? mirror : url;
                    if (target.isEmpty()) {
                        Toast.makeText(this, "清单里没有下载地址", Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target)));
                    } catch (Throwable t) {
                        Toast.makeText(this, "打不开下载页：" + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("复制链接", (d, w) -> {
                    String target = url.isEmpty() ? mirror : url;
                    try {
                        android.content.ClipboardManager cm =
                                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("apk", target));
                        Toast.makeText(this, "下载链接已复制", Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignored) { }
                })
                .setNegativeButton("以后再说", (d, w) -> store.setSkipVersion(name))
                .show();
    }

    private void refreshFeedbackHint() {
        if (settingsView == null) return;
        int n = store.feedbackCount();
        String log = store.feedbackLog();
        String last = "";
        if (!log.isEmpty()) {
            String first = log.split("\n")[0];
            last = first.length() > 24 ? first.substring(0, 24) + "…" : first;
        }
        settingsView.setFeedbackHint(n == 0 ? "还没有提交过反馈"
                : ("已记录 " + n + " 条 · 最近：" + last));
    }

    @Override
    public void onCheckUpdate() {
        checkUpdate(true);
    }

    @Override
    public void onToggleInsecureTls(boolean on) {
        store.setInsecureTls(on);
        if (settingsView != null) settingsView.setInsecureTls(on);
        gw.setTrustAllCerts(on);
        Toast.makeText(this, on ? "已允许自签名证书，正在重连…" : "已恢复证书校验，正在重连…",
                Toast.LENGTH_SHORT).show();
        if (!store.url().isEmpty() && !store.token().isEmpty()) {
            gw.connect(store.url(), store.token(), store.deviceId(), store.deviceName());
        }
    }

    @Override
    public void onSwitchEndpoint(String lan, String wan, boolean useWan) {
        if (useWan && wan.isEmpty()) {
            Toast.makeText(this, "还没填公网地址，请先在「公网地址」里填写", Toast.LENGTH_LONG).show();
            return;
        }
        if (useWan && !store.riskAck()) { confirmPublicAccess(lan, wan); return; }
        applyEndpoint(lan, wan, useWan);
    }

    /** 安全声明确认后（或切到内网时）真正执行切换。 */
    private void applyEndpoint(String lan, String wan, boolean useWan) {
        store.setLanUrl(lan);
        store.setWanUrl(wan);
        store.setUseWan(useWan);
        if (settingsView != null) settingsView.setUseWan(useWan);
        if (store.token().isEmpty()) {
            Toast.makeText(this, "还没配对（设备令牌是空的）。\n"
                    + "地址本身没问题、内网地址也不会变，只差一次配对：\n"
                    + "扫一下电脑面板的「生成配对二维码」，配对后内网/公网地址与令牌会一起填好。",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String active = store.url();
        if (active.isEmpty()) {
            Toast.makeText(this, (useWan ? "公网地址" : "内网地址") + "还是空的。\n"
                    + "扫一下电脑面板的「生成配对二维码」，两个地址会自动填好，不用手输。",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String problem = GatewayClient.cleartextProblem(active);
        if (problem != null) {
            Toast.makeText(this, problem, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, (useWan ? "已切到公网：" : "已切到内网：") + hostOf(active),
                Toast.LENGTH_SHORT).show();
        gw.connect(active, store.token(), store.deviceId(), store.deviceName());
    }

    @Override
    public void onPastePairing() {
        final EditText input = new EditText(this);
        input.setHint("粘贴电脑端生成的配对串（Base64URL）");
        input.setMinLines(3);
        new AlertDialog.Builder(this)
                .setTitle("粘贴配对串")
                .setView(input)
                .setPositiveButton("配对", (d, w) -> startPairing(input.getText().toString()))
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onDisconnect() {
        store.clearPairing();
        gw.disconnect();
        settingsView.setStatus("已清除配对", false);
        Toast.makeText(this, "已清除配对", Toast.LENGTH_SHORT).show();
    }

    // ============================================================ 扫码结果

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQ_QR && result == RESULT_OK && data != null) {
            startPairing(data.getStringExtra(QrScanActivity.EXTRA_RAW));
        }
        if (request == REQ_IMAGE && result == RESULT_OK && data != null && data.getData() != null) {
            sendImage(data.getData());
        }
        if (request == REQ_VOICE && result == RESULT_OK && data != null && convo != null) {
            java.util.ArrayList<String> r =
                    data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) {
                String cur = convo.draftText();
                convo.setDraft((cur == null ? "" : cur) + r.get(0));
                convo.focusInput();
            }
        }
    }

    /**
     * 读图片 -> 标准 Base64 -> 走 message 的 images[] 发出去。
     * 读文件与编码都在后台线程（IO 不能上主线程），完成后回主线程发送。
     */
    private void sendImage(final android.net.Uri uri) {
        new Thread(() -> {
            try {
                android.content.ContentResolver cr = getContentResolver();
                String type = cr.getType(uri);
                if (type == null || !type.startsWith("image/")) type = "image/jpeg";
                java.io.InputStream is = cr.openInputStream(uri);
                if (is == null) throw new java.io.IOException("打不开该图片");
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                is.close();
                byte[] bytes = bos.toByteArray();
                if (bytes.length == 0) throw new java.io.IOException("图片为空");
                if (bytes.length > 3 * 1024 * 1024) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "图片太大（" + (bytes.length / 1024 / 1024) + "MB），请选 3MB 以内的", Toast.LENGTH_LONG).show());
                    return;
                }
                final String b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                final String mt = type;
                final String name = "image-" + System.currentTimeMillis()
                        + (type.contains("png") ? ".png" : type.contains("webp") ? ".webp" : ".jpg");
                runOnUiThread(() -> {
                    if (gw.state() != GatewayClient.State.READY) {
                        Toast.makeText(MainActivity.this, "还没连上电脑端", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (currentSessionId.isEmpty()) pendingUserText = "[图片]";
                    gw.sendMessageWithImage(currentSessionId, "", mt, b64, name);
                    setRunning(true);
                    if (convo != null) convo.setRunning(true, runningHint());
                    // 大负载（base64 单帧可达 ~4MB）：看门狗按负载放大到 120s，
                    // 别用文本的 30s 去误判"没发出去"（评审 P1-9）。
                    armSendWatchdog(IMAGE_WATCHDOG_MS);
                });
            } catch (Throwable e) {
                final String msg = e.getMessage() == null ? String.valueOf(e) : e.getMessage();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "读取图片失败：" + msg, Toast.LENGTH_LONG).show());
            }
        }, "img-send").start();
    }

    private void startPairing(String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        // 面板里有两张二维码：安装包下载链接、配对码。扫错的时候要讲清楚。
        String scanned = raw.trim();
        if (scanned.startsWith("http://") || scanned.startsWith("https://")) {
            Toast.makeText(this, "这是「安装包下载链接」，不是配对码。\n"
                    + "请用手机浏览器打开它下载安装 App；\n"
                    + "配对请扫电脑面板里「生成配对二维码」那一张。", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            JSONObject payload = decodePairing(raw.trim());
            int version = payload.optInt("version", 0);
            if (version != 2) {
                Toast.makeText(this, "配对信息版本不支持：" + version, Toast.LENGTH_LONG).show();
                return;
            }
            long expires = payload.optLong("expiresAt", 0L);
            if (expires > 0 && System.currentTimeMillis() > expires) {
                Toast.makeText(this, "配对码已过期，请在电脑端重新生成", Toast.LENGTH_LONG).show();
                return;
            }
            String url = payload.optString("publicUrl", "");
            String code = payload.optString("pairingCode", "");
            if (url.isEmpty() || code.isEmpty()) {
                Toast.makeText(this, "配对信息不完整", Toast.LENGTH_LONG).show();
                return;
            }
            // 网关给的 publicUrl 可能只对电脑本机有效（例如 ws://127.0.0.1:19387/…），
            // 真正可用的是 payload.endpoints —— 它带着隧道 / 局域网等全部地址。
            // 这里排成候选列表逐个试：私有网段优先（在家最快），不行再走公网。
            pairCandidates = buildPairCandidates(payload, url);
            if (pairCandidates.isEmpty()) {
                Toast.makeText(this, "配对码里只有电脑本机地址（127.0.0.1 / localhost），手机连不上。\n"
                        + "请在电脑面板重新点「生成配对二维码」，"
                        + "并把「配对连接方式」选成「自动选择 · 优先外网」；\n"
                        + "或者在 App 设置里手动填「公网地址」。", Toast.LENGTH_LONG).show();
                return;
            }
            store.setUrl(pairCandidates.get(0));
            // 网关把可用地址都放在 endpoints：私有网段进「内网」，公网/隧道进「公网」。
              // 扫一次码就把两个地址都填好，不用手输（隧道域名每次重启会变，重扫即可）。
              String lan = "", wan = "";
              JSONArray eps = payload.optJSONArray("endpoints");
              if (eps != null) {
                  for (int i = 0; i < eps.length(); i++) {
                      String e = eps.optString(i, "").trim();
                      if (e.isEmpty()) continue;
                      if (Store.isPrivateUrl(e)) { if (lan.isEmpty()) lan = e; }
                      else if (wan.isEmpty()) wan = e;
                  }
              }
              if (Store.isPrivateUrl(url)) { if (lan.isEmpty()) lan = url; }
              else if (wan.isEmpty()) wan = url;
              if (!lan.isEmpty()) store.setLanUrl(lan);
              if (!wan.isEmpty()) store.setWanUrl(wan);
              store.setUseWan(lan.isEmpty() && !wan.isEmpty());
              failoverUsed = false;
              pendingPairCode = code;
              pairIndex = 0;
              pairWithNextCandidate();
        } catch (Throwable t) {
            Toast.makeText(this, "配对串无法解析：" + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 严格 Base64URL（无 padding）解码，内容为 {version, publicUrl, pairingCode, expiresAt}。 */
    private static JSONObject decodePairing(String raw) throws Exception {
        String s = raw.trim();
        if (s.startsWith("{")) return new JSONObject(s);
        s = s.replace("\n", "").replace("\r", "").replace(" ", "");
        if (s.indexOf('+') >= 0 || s.indexOf('/') >= 0 || s.indexOf('=') >= 0) {
            throw new IllegalArgumentException("不是合法的 Base64URL 配对串");
        }
        byte[] bytes = Base64.decode(s, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        return new JSONObject(new String(bytes, "UTF-8"));
    }
}
