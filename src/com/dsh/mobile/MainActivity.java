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
    private String pendingUserText = null;
    private String streamAttemptKey = null;
    private String lastStateText = "";
    /** 非当前会话的待处理交互：sessionId -> 1 提问 / 2 审批 */
    private final Map<String, Integer> pendingBySession = new HashMap<>();
    /** 反馈草稿：发完不清空，方便继续补充。 */
    private String lastFeedbackDraft = "";
    /** 自动切换端点只用一次，连接成功或手动切换后复位。 */
    private boolean failoverUsed = false;

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
        gw.connect(store.url(), store.token(), store.deviceId(), store.deviceName());
            }
        } else {
            showSettings();
        }
        refreshListStatus();
    }

    @Override
    protected void onDestroy() {
        // 连接是进程级的：Activity 销毁（重建/任务切换）只解绑监听，不断开连接。
        if (gw != null && gw.listener() == this) gw.setListener(null);
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
        boolean err = (st == GatewayClient.State.UNAUTHORIZED
                || st == GatewayClient.State.FAILED
                || st == GatewayClient.State.GATEWAY_OFF);
        if (st == GatewayClient.State.READY) {
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
        if (convo != null) { convo.setItems(items); convo.refreshNow(); convo.scrollToBottom(); }
    }

    @Override
    public void onAssistantStream(JSONObject frame) {
        if (!frame.optString("sessionId", "").equals(currentSessionId)) return;
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
        if (byKey.containsKey(key)) return;
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
                : byKey.get("approval:" + (approvalId.isEmpty() ? rpcId : approvalId));
        if (it == null) return;
        if ("question-response".equals(kind) || "approval-response".equals(kind)) {
            if (!frame.optBoolean("accepted", true)) {
                it.resolved = true;
                it.resolvedOutcome = frame.optString("reason", "not-pending");
            }
            if (convo != null) { convo.setItems(items); convo.refreshNow(); }
            return;
        }
        it.resolved = true;
        it.resolvedOutcome = frame.optString("outcome", "");
        if (convo != null) { convo.setItems(items); convo.refreshNow(); }
    }

    @Override
    public void onSent(String sessionId, JSONObject raw) {
        if (currentSessionId.isEmpty() && sessionId != null && !sessionId.isEmpty()) {
            currentSessionId = sessionId;
            subscribeCurrent();
        }
    }

    @Override
    public void onProtocolError(String code, String message, String requestType, String sessionId) {
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
        if (convo != null) {
            convo.setRunning(value, runningHint());
        }
        if (!value) turnStartedAt = 0;
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
                    if (!t.isEmpty()) gw.renameSession(s.id, t);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onArchive(SessionInfo s) {
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
    }

    @Override
    public void onStop() {
        if (currentSessionId.isEmpty()) return;
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
        gw.approvalResponse(item.rpcId, currentSessionId, item.approvalId, outcome);
        item.resolved = true;
        item.resolvedOutcome = outcome;
        if (convo != null) { convo.setItems(items); convo.refreshNow(); }
    }

    @Override
    public void onQuestionSubmit(ChatItem item, JSONArray answers) {
        gw.questionAnswer(item.rpcId, currentSessionId, answers);
        item.resolved = true;
        item.resolvedOutcome = "answered";
        if (convo != null) { convo.setItems(items); convo.refreshNow(); }
    }

    @Override
    public void onQuestionCancel(ChatItem item) {
        gw.questionCancel(item.rpcId, currentSessionId);
        item.resolved = true;
        item.resolvedOutcome = "cancelled";
        if (convo != null) { convo.setItems(items); convo.refreshNow(); }
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

        new android.app.AlertDialog.Builder(this)
                .setTitle("意见反馈")
                .setView(box)
                .setPositiveButton("发送到电脑", (d, w) -> sendFeedback(ed.getText().toString()))
                .setNeutralButton("复制", (d, w) -> {
                    copyFeedback(feedbackText(ed.getText().toString()));
                    Toast.makeText(this, "已复制，可直接粘给我", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
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

    /** 连不上时自动在「内网 / 公网」之间切一次（只切一次，避免来回跳）。 */
    private boolean tryFailover() {
        if (failoverUsed) return false;
        String lan = store.lanUrl(), wan = store.wanUrl();
        if (lan.isEmpty() || wan.isEmpty()) return false;
        failoverUsed = true;
        boolean toWan = !store.useWan();
        store.setUseWan(toWan);
        if (settingsView != null) settingsView.setUseWan(toWan);
        Toast.makeText(this, toWan ? "内网连不上，自动改用公网…" : "公网连不上，自动改用内网…",
                Toast.LENGTH_SHORT).show();
        gw.connect(store.url(), store.token(), store.deviceId(), store.deviceName());
        return true;
    }

    @Override
    public void onReconnectScheduled(String reason) {
        if (tryFailover()) return;
        String active = store.url();
        if (active.contains("trycloudflare.com") || reason.contains("404")) {
            Toast.makeText(this, "公网地址可能已失效（隧道域名每次重启电脑都会变）\n"
                    + "请在电脑面板重新「生成配对二维码」扫一次", Toast.LENGTH_LONG).show();
        }
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
            Toast.makeText(this, "还没配对，请先「扫码配对」或「粘贴配对串」再切换", Toast.LENGTH_LONG).show();
            return;
        }
        String active = store.url();
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
                });
            } catch (Throwable e) {
                final String msg = e.getMessage() == null ? String.valueOf(e) : e.getMessage();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "读取图片失败：" + msg, Toast.LENGTH_LONG).show());
            }
        }, "img-send").start();
    }

    private void startPairing(String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
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
            String problem = GatewayClient.cleartextProblem(url);
            if (problem != null) {
                Toast.makeText(this, problem, Toast.LENGTH_LONG).show();
                return;
            }
            store.setUrl(url);
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
              gw.pair(url, code, store.deviceId(), store.deviceName());
            settingsView.setStatus("正在配对 " + hostOf(url) + " …", false);
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
