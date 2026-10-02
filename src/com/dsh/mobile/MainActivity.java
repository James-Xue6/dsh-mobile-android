package com.dsh.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.dsh.mobile.model.ChatItem;
import com.dsh.mobile.model.MessageSource;
import com.dsh.mobile.model.SessionInfo;
import com.dsh.mobile.net.GatewayClient;
import com.dsh.mobile.net.LanAddress;
import com.dsh.mobile.ui.ConversationView;
import com.dsh.mobile.ui.DeviceHubView;
import com.dsh.mobile.ui.DrawerHost;
import com.dsh.mobile.ui.QrScanActivity;
import com.dsh.mobile.ui.SessionListView;
import com.dsh.mobile.ui.SettingsView;
import com.dsh.mobile.ui.SubagentSheet;
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

/**
 * 单 Activity 架构：我的设备（启动页）/ 对话页 / 连接设置三屏；
 * 会话列表是对话页上滑出的左侧抽屉（豆包式）。
 *
 * 启动落位：「我的设备」页（先看见自己添加过的电脑，在线/离线写在卡片上），
 * 点某台设备的「连接/进入」才进对话页。设置页作为高级入口保留在右上角齿轮里。
 */
public final class MainActivity extends Activity implements
        GatewayClient.Listener,
        SessionListView.Host,
        ConversationView.Host,
        SettingsView.Host,
        DeviceHubView.Host {

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

    /**
     * 三级屏：我的设备（启动页）/ 对话页 / 设置页。
     * 会话列表不再是独立的第三块全屏页 —— 它是对话页上从左侧滑出的抽屉（豆包式），
     * 所以没有 LIST 这个"屏"了（旧代码的 Screen.LIST 已去掉）。
     */
    private enum Screen { DEVICE, CHAT, SETTINGS }
    private Screen screen = Screen.DEVICE;

    /** 进程级共享的网关客户端：Activity 重建不应打断连接。 */
    private static GatewayClient SHARED_GW;

    private Store store;
    private GatewayClient gw;

    private FrameLayout root;
    /** 左侧任务抽屉宿主：内容层 + 遮罩 + 抽屉层（见 DrawerHost）。 */
    private DrawerHost drawerHost;
    /** 内容层：对话页 / 设置页在这里互切，不再整屏重挂 root。 */
    private FrameLayout contentHost;
    private SessionListView listScreen;
    private ConversationView convo;
    private SettingsView settingsView;
    /** 「我的设备」启动页。 */
    private DeviceHubView deviceHub;
    /**
     * 主题档位：system（跟随系统，默认）/ light / dark。
     * 与 Store 同步；这里存一份是为了在 onConfigurationChanged 里**不解磁盘**就能判断。
     */
    private String themeMode = com.dsh.mobile.ui.Theme.MODE_SYSTEM;
    /** 上一次真正套用的色板是不是深色：用来判断系统深浅色变化后要不要重绘。 */
    private boolean themedDark = false;
    /** 冷启动只自动决定一次：进最近一条会话，或拉开抽屉提示"还没有对话"。 */
    private boolean autoEntered = false;

    private final List<SessionInfo> sessions = new ArrayList<>();
    private final Set<String> archivedIds = new HashSet<>();

    private final List<ChatItem> items = new ArrayList<>();
    private final Map<String, ChatItem> byKey = new HashMap<>();
    private final Set<Long> seenSeq = new HashSet<>();

    private String currentSessionId = "";
    private String currentTitle = "";
    private String currentCwd = "";
    /**
     * 当前会话的父会话 id：**非空 = 人此刻就在某个子智能体 / 专家团子会话里**。
     *
     * 判定与抽屉折叠同源（buildRows / emitSession 用的就是 sessions 条目上的
     * parentSessionId）：父会话在列表里能查到才置位，查不到就当作顶层，
     * 免得返回键走进一条不存在的父会话。
     */
    private String currentParentId = "";
    private int historyFormatVersion = 4;
    private Long nextBeforeSeq = null;
    private boolean hasMore = false;
    /**
     * 「加载更早历史」是否还在途。**在途时必须忽略新的上滑触发**，否则一次上滑会连发
     * 好几个 history 请求；响应到达 / 失败 / 超时三者任一，都要复位它。
     */
    private boolean loadingMore = false;
    /**
     * 上一次「加载更早」失败了（含超时、未连接）。失败**不清 hasMore**，
     * 列表顶部换成可点的「加载更早失败，点这里重试」，不再静默。
     */
    private boolean loadMoreFailed = false;
    /** 在途「加载更早」的超时兜底任务（响应丢了也要复位，不能卡死）。 */
    private Runnable loadMoreTimeout;
    /**
     * 单次「加载更早」的等待上限。一次 history 分页最多 4 MiB（网关 HISTORY_DEFAULT_MAX_BYTES），
     * 慢网 + 大分页要留出余量，但不能久到用户以为卡住：15s 就复位并给可重试的失败态。
     */
    private static final long LOAD_MORE_TIMEOUT_MS = 15_000L;
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
    /**
     * 最近一次扫码/粘贴内容的脱敏描述（问题 2-d），显示在设置页诊断区。
     * 只放在内存里：它是给"这次连不上"现场定位用的，不落盘、不进任何被跟踪的文件。
     */
    private String lastPairDebug = "";
    private java.util.List<String> pairCandidates = new java.util.ArrayList<>();
    private int pairIndex = 0;
    /**
     * 本次配对的全过程轨迹（"点了没反应/报错但不知道错在哪"必须能一锤定音）。
     *
     * 只记「长度 / 字段有无 / 主机名 / 每一步的结果」，**绝不记 pairingCode / token 明文**；
     * 只活在内存里（不落盘、不进任何被跟踪的文件），显示在设置页诊断区。
     */
    private final java.util.List<String> pairTrace = new java.util.ArrayList<>();

    // ---------------------------------------------------------------- 会话流中断的内联状态条
    //
    // 旧行为：每一帧 retrying 的 session-stream-reset 都弹一次 Toast「连接抖动，正在恢复…」。
    // 网关的会话跟随器失败后会自己退避重试（session-follower.mjs：1s→2s→…→30s），所以只要
    // 某个会话长期接不上，这个 Toast 就会一直弹。真机实测：打开一个子会话（宿主侧以
    // session/agent-busy 拒绝，见「会话流中断」诊断），9 秒内来了 4 帧 retrying 中断，
    // 用户看到的就是「一直弹」。
    //
    // 现在改成对话页顶部的一条内联横幅：
    //   ① 同一会话 30 秒内最多提示一次（节流，退避重试的每一帧不再各弹一次）；
    //   ② 收到该会话任何新帧 / 快照即自动消失（说明真的接上了）；
    //   ③ 连续 30 秒仍未恢复 → 横幅变成可点的「还没接上，点这里重新连接」。
    private static final long STREAM_NOTICE_THROTTLE_MS = 30_000L;
    /** 从第一次中断起算，超过这么久还没恢复就把横幅变成可点的重连入口。 */
    private static final long STREAM_NOTICE_ACTION_MS = 30_000L;
    /** 设置页诊断区最多保留几条流中断记录。 */
    private static final int STREAM_RESET_LOG_MAX = 10;

    /** 当前横幅属于哪个会话（空 = 没有横幅）。 */
    private String streamNoticeSession = "";
    private String streamNoticeText = "";
    private boolean streamNoticeError = false;
    private boolean streamNoticeActionable = false;
    /** 上一次「已显示」提示的时刻与所属会话，用来做 30 秒节流。 */
    private String streamNoticeLastSession = "";
    private long streamNoticeLastAt = 0L;
    /** 把「还没接上」翻出来的定时任务（null = 没排）。 */
    private Runnable streamNoticeActionTask;

    /**
     * 最近几次会话流中断：**时间 / code / retrying** 三样，只留最近 STREAM_RESET_LOG_MAX 条。
     *
     * 目的只有一个：下次再遇到「一直在抖」时，能一眼看出是**偶发**（零散几条）还是
     * **持续**（同一 code 连续刷屏）。因此**只记 code，不记 message 明文**（正文可能带
     * 宿主内部细节，也不是用户要看的东西）；只活在内存里，不落盘、不进任何被跟踪的文件。
     */
    private final java.util.ArrayDeque<String> streamResetLog = new java.util.ArrayDeque<>();

    /**
     * onState 想挂的横幅（连接状态 / hello 告警），与流中断横幅合成后再画。
     * 连接本身就不正常时优先显示连接问题——流中断往往只是它的后果。
     */
    private String stateBannerText = "";
    private boolean stateBannerError = false;

    // 当前会话的目标 / 任务提要
    private String planGoal = "";
    private String planPhase = "";
    private String planTodos = "";

    // 标题懒加载队列（网关不返回 title，只能逐个从历史的**开头小窗**里抽）
    private final java.util.ArrayDeque<String> titleQueue = new java.util.ArrayDeque<>();
    /**
     * 已入队 / 在途的会话：只用来去重，**不是**"进过就永不重试"的黑洞。
     * 拿到标题、超时用尽、或断线重连时都会从这里放行，允许重新探测。
     */
    private final Set<String> titleRequested = new HashSet<>();
    private final Set<String> titleAwaiting = new HashSet<>();
    /** 每个会话已发出的探针次数（超时重试上限）。 */
    private final java.util.Map<String, Integer> titleAttempts = new java.util.HashMap<>();
    /** 在途探针的截止时刻：到点还没回就当丢了，重入队。 */
    private final java.util.Map<String, Long> titleDeadline = new java.util.HashMap<>();
    /** 探针回了但没抽到标题（标题事件可能还没生成），到这个时刻再补一次。 */
    private final java.util.Map<String, Long> titleDeferredUntil = new java.util.HashMap<>();
    private int titleInFlight = 0;
    private static final int TITLE_MAX_INFLIGHT = 2;
    /** 单会话最多探测次数（首次 + 2 次重试）。 */
    private static final int TITLE_MAX_TRIES = 3;
    /** 探针超时：10 秒没回就重入队（断线/丢帧后仍能自愈）。 */
    private static final long TITLE_TIMEOUT_MS = 10_000L;
    /** 空结果后的补探间隔：新会话的标题常常晚一拍才生成。 */
    private static final long TITLE_EMPTY_RETRY_MS = 8_000L;
    /** 标题重试扫描任务的当前排程（null = 没排）。 */
    private Runnable titleSweep;

    /** 已展开的子会话分组（父会话 id）。默认折叠：子智能体/专家团会话不铺在主列表里。 */
    private final Set<String> expandedParents = new HashSet<>();

    // ============================================================ 生命周期

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // 截屏策略改为**用户可关的开关**（Store.allowScreenshot，默认 true）：
        //   - 默认不设 FLAG_SECURE：用户能截图/录屏，系统「最近任务」缩略图正常；
        //   - 只有用户在设置页关掉「允许截屏」时才设上（此时本 App 内容截图变黑）。
        // 令牌的掩码显示与这个开关无关：令牌仍只显示末 4 位，取消 FLAG_SECURE 不等于明文暴露它。
        store = new Store(this);
        // 主题必须**在创建任何 View 之前**定下来：手搓 View 的配色是创建时从 Ui 取当前值
        // 烘进每个控件的，晚一步就会得到一棵浅色骨架。这里顺带把系统栏也刷成同色。
        themeMode = store.themeMode();
        applyThemeEverywhere();
        applyScreenshotPolicy();
        if (SHARED_GW == null) SHARED_GW = new GatewayClient(this);
        else SHARED_GW.setListener(this);
        gw = SHARED_GW;

        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        // targetSdk 35+ 强制 edge-to-edge：把系统栏内边距加到根容器，各屏不再自己留状态栏高度
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                // 键盘（IME）也必须算进来：targetSdk 35+ 强制 edge-to-edge，系统不会替我们
                // 缩小窗口，而 systemBars() 只给导航栏高度。真机实测（某国产 ROM /
                // 手势导航 + 中文输入法）：只消费 systemBars() 时输入条被键盘整个盖住，
                // 用户看不到自己在输入什么（问题 1）。
                // 底部留白取 max(导航栏, 键盘)：键盘弹起时跟着上移，收起时退回导航栏高度。
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                android.graphics.Insets ime = insets.getInsets(android.view.WindowInsets.Type.ime());
                top = bars.top;
                bottom = Math.max(bars.bottom, ime.bottom);
            } else {
                // API < 30：ADJUST_RESIZE 会真的缩窗口，这里拿到的 bottom 已经是键盘高度
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            if (v.getPaddingTop() != top || v.getPaddingBottom() != bottom) {
                v.setPadding(0, top, 0, bottom);
                // 可视区变矮（键盘弹起）时，原本贴底的会话要重新贴底，
                // 否则最后几条消息会被顶出可视区，看起来像"内容被键盘盖住了"。
                if (screen == Screen.CHAT && convo != null) convo.onWindowInsetsChanged();
            }
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

        // 左侧任务抽屉：内容层（对话/设置）+ 遮罩 + 抽屉层（会话列表）
        drawerHost = new DrawerHost(this);
        root.addView(drawerHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        contentHost = drawerHost.content();
        listScreen = new SessionListView(this, this);
        drawerHost.drawer().addView(listScreen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 冷启动落在「我的设备」页：先看见自己添加过的电脑（在线/离线写在卡片上），
        // 点某台的「连接/进入」才进对话页（用户要求：我的设备在对话页之前）。
        showDevices();
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
        }
        refreshListStatus();
        registerNetworkCallback();
        registerBackInvoked();
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

    /**
     * Activity 生命周期：切后台 / 锁屏 / 切任务时走到这里。
     *
     * 历史 bug：ConversationView.Host 里原有一个同名的 {@code onStop()} 回调
     * （「停止当前回合」），和 Activity 生命周期方法签名撞车，于是那个回调被当成
     * Activity.onStop() 的覆写、又没调 super.onStop()，一切后台就抛
     * SuperNotCalledException 崩溃（真机 logcat 实测，进程 PID 698）。
     * 现在那个回调已改名为 {@code onStopTurn()}，本方法只保留真正的生命周期语义。
     *
     * ⚠️ 这里**只能做收尾**，绝不能发「停止当前回合」：用户切后台不等于停回合，
     * 真正的回合状态由网关的 session/agent 事件驱动。所以这里不碰 running、
     * 不发 gw.stopSession()，也不动发送看门狗（那是"发出去没回音"的兜底，切后台后仍要生效）。
     */
    @Override
    protected void onStop() {
        super.onStop();
        // 标题重试扫描是 1s 一次的循环定时器，后台没人看结果，先撤掉。
        // 必须把 titleSweep 置回 null：不然回前台时 armTitleSweep() 会被
        // "titleSweep != null 就 return" 的防重入判断挡住，标题重试再也起不来。
        if (titleSweep != null) {
            uiHandler.removeCallbacks(titleSweep);
            titleSweep = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从设置页 / 别处切回来时对齐一次截屏策略：用户在设置里一改就立即生效，不需要重启 App
        applyScreenshotPolicy();
        // 回前台重新武装标题重试扫描（onStop 里把它撤了）；没有待办时 armTitleSweep 自己会空转返回。
        armTitleSweep();
    }

    /**
     * 截屏策略落地：默认（allowScreenshot=true）**不设** FLAG_SECURE，用户能截图/录屏；
     * 只有用户关掉「允许截屏」时才设上（本 App 内截图/录屏变黑、最近任务缩略图变黑）。
     *
     * clearFlags / setFlags 是窗口级即时生效的，切换后**不需要重启 App**。
     * 同时把策略同步进 Ui 的进程级镜像，让添加设备弹窗、手动添加表单这些独立 Dialog
     * 窗口走同一条策略（见 Ui.applyScreenshotPolicy）。
     */
    private void applyScreenshotPolicy() {
        if (store == null) return;
        boolean allow = store.allowScreenshot();
        Ui.setAllowScreenshot(allow);
        Ui.applyScreenshotPolicy(getWindow());
    }

    /**
     * 方向 / 窗口尺寸变化：清单里声明了
     * {@code configChanges="orientation|screenSize|screenLayout|smallestScreenSize|...}"}，
     * 所以旋转时 Activity **不会重建**（会话状态全在内存里，重建会丢掉正在跑的对话）。
     *
     * 代价是：手搓的 View 树会保留竖屏（旧尺寸）的测量结果 → 控件跑到屏幕外。
     * 因此这里必须主动按**新**尺寸重新布置三个屏。尺寸一律现取
     * （{@link #relayoutForConfig()} 内部用 getResources()/getWidth() 现算），绝不缓存旧值。
     */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 这里也要处理**系统深浅色变化**：清单声明了 uiMode，系统切深色时 Activity
        // 不会重建、AppTheme 也不会重新解析，主题必须自己跟上（跟随系统模式下）。
        syncThemeIfSystemChanged();
        relayoutForConfig();
    }

    /** 跟随系统模式下系统深浅色变了就换色板重绘；锁死浅色/深色时不跟随。 */
    private void syncThemeIfSystemChanged() {
        boolean want = com.dsh.mobile.ui.Theme.resolveDark(this, themeMode);
        if (want != themedDark) applyThemeEverywhere();
    }

    /**
     * 把当前主题档位套用到整棵界面 + 系统栏。
     *
     * 调用时机：① onCreate（建任何 View 之前）；② 用户点设置里的主题分段；
     * ③ 跟随系统模式下系统深浅色变化（onConfigurationChanged）。
     *
     * 重绘策略：各屏自己实现 applyTheme()。对话页/抽屉/设备页都是"逐项刷色 + 整表重画"，
     * 设置页则是整棵树重建（它的内容纯由内存字段推导，重建最不容易漏色）。
     */
    private void applyThemeEverywhere() {
        boolean dark = com.dsh.mobile.ui.Theme.resolveDark(this, themeMode);
        com.dsh.mobile.ui.Ui.applyTheme(dark);
        themedDark = dark;
        applySystemBars();
        if (root != null) root.setBackgroundColor(com.dsh.mobile.ui.Ui.BG);
        if (drawerHost != null) drawerHost.applyTheme();
        if (listScreen != null) listScreen.applyTheme();
        if (convo != null) convo.applyTheme();
        if (settingsView != null) {
            settingsView.applyTheme();                                  // 整树重建
            settingsView.setDisplayMode(store.displayMode());
            settingsView.setThemeMode(themeMode);
        }
        if (deviceHub != null) {
            deviceHub.applyTheme();
            refreshDevices();                                           // 卡片内容按新色板重填
        }
        if (convo != null && screen == Screen.CHAT) paintBanner();       // 横幅按当前语义重画
    }

    /**
     * 状态栏 / 导航栏底色跟着主题走，并校正**图标明暗**：
     * 深底必须配浅色图标（清掉 LIGHT_* 标志），否则深底上的深色图标等于看不见。
     * AppTheme 里写死的 windowLightStatusBar=true 是浅色时代的，这里运行时覆盖它。
     */
    private void applySystemBars() {
        android.view.Window w = getWindow();
        if (w == null) return;
        int bg = com.dsh.mobile.ui.Ui.BG;
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bg));
        android.view.View dv = w.getDecorView();
        int flags = dv.getSystemUiVisibility();
        boolean dark = com.dsh.mobile.ui.Ui.isDark();
        if (dark) flags &= ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else flags |= android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            // LIGHT_NAVIGATION_BAR 是 API 27 才有的常量
            if (dark) flags &= ~android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else flags |= android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        dv.setSystemUiVisibility(flags);
    }

    /** 设置页「主题」三项分段：立即生效（重绘所有已建界面，不重启 App）。 */
    @Override
    public void onSetThemeMode(String mode) {
        themeMode = com.dsh.mobile.ui.Theme.normalize(mode);
        store.setThemeMode(themeMode);
        // 先让设置页自己记住新档位，随后它会被整树重建，重建时才会画出正确的选中态
        if (settingsView != null) settingsView.setThemeMode(themeMode);
        applyThemeEverywhere();
    }

    /**
     * 按当前（新）尺寸重新布置三屏 + 抽屉。
     *
     * 只做"重新测量/重挂"，不动任何会话状态（不发请求、不清 items、不重置分页）：
     *   ① 根容器重新申请内边距（横竖屏的系统栏高度不同，导航栏可能从底部跑到侧边）；
     *   ② 抽屉宽度按新屏宽重算（横屏收窄到 ~52%，见 DrawerHost.fractionFor）；
     *   ③ 对话气泡最大宽度重算（ChatAdapter.maxBubble 原来是构造时算一次的死值）；
     *   ④ 当前那一屏重挂/重画一次，强制用新尺寸重新测量（三个屏都是手搓 View 树）。
     */
    private void relayoutForConfig() {
        if (root != null) {
            root.requestApplyInsets();   // 系统栏/键盘内边距随方向变化
            root.requestLayout();
        }
        if (drawerHost != null) drawerHost.onConfigChanged();
        if (listScreen != null) listScreen.requestLayout();
        if (convo != null) convo.onConfigChanged();
        if (settingsView != null) settingsView.requestLayout();
        if (deviceHub != null) deviceHub.requestLayout();
        // 设置页不重挂：重挂会重新 setFields() 把用户正在输入的内容覆盖回 store 里的旧值。
        // 对话页 / 设备页的内容全部由内存状态推导，重挂是安全且最彻底的"用新尺寸重新测量"。
        if (screen == Screen.CHAT) showChat();
        else if (screen == Screen.DEVICE) showDevices();
    }

    private long lastBackAt = 0L;

    /**
     * 「我的设备」是不是盖在对话页上面打开的（抽屉里的 🖥 入口）。
     *
     * 冷启动时它是**根页**，再按返回就该退到后台；但从抽屉点进来时它只是
     * 对话页上面的一层，按返回应当回对话页。两种情形屏号相同，只能靠这个标记区分。
     */
    private boolean deviceOverChat = false;

    /**
     * 抽屉是不是「被返回键按出来的」。
     *
     * 豆包式两级导航：对话页按返回 ⇒ 拉开左侧任务列表（抽屉 = 外层）。
     * 用 ‹ 箭头手动拉开的抽屉不是"外层"，按返回只把它关掉。
     */
    private boolean drawerAsParent = false;

    /**
     * 「已经关过一次"由返回键按出来的抽屉"」的时刻。
     *
     * 关掉它之后的这一小段时间里再按返回 = 用户明确要退出（用户口径第 4 条
     * 「抽屉在对话页已经是最外层 → 再按返回才退到后台」）；过了这段时间再按返回，
     * 仍然按第 3 条重新拉开任务列表，不会把用户"锁"在必须退出的状态里。
     */
    private long backExitArmedAt = 0L;
    /** 「再按一次返回即退到后台」的有效窗口。 */
    private static final long BACK_EXIT_WINDOW_MS = 2500L;

    /**
     * 返回键处理。走两条路：
     *  - dispatchKeyEvent 兜住 KEYCODE_BACK（清单已关闭预测式返回，保证按键会送到这里）
     *  - onBackPressed 作为老版本回退
     * 两条路对同一次按键可能都触发，用时间窗去重。
     *
     * 返回栈（用户口径，逐级）：
     *   ⓪ 人在子会话（子智能体 / 专家团子会话）→ 回它的主智能体（父会话）；
     *   ① 抽屉开着 → 关抽屉；
     *   ② 设置页 / 从抽屉进的设备页 → 回对话页；
     *   ③ 对话页 → 拉开任务列表抽屉（**不是**退回「我的设备」）；
     *   ④ 抽屉在对话页已是最外层 → 再按返回退到系统后台（moveTaskToBack）。
     * 「我的设备」不再是返回栈的一级 —— 它只是抽屉里的一个入口（见 onDevices）。
     */
    private void handleBackKey() {
        long now = System.currentTimeMillis();
        if (now - lastBackAt < 400L) return;
        lastBackAt = now;

        if (drawerHost != null && drawerHost.isOpen()) {
            // ①：抽屉开着，返回 = 关抽屉
            boolean wasParent = drawerAsParent;
            closeDrawer();
            drawerAsParent = false;
            if (wasParent) backExitArmedAt = now;   // ④：刚从"任务列表"退回，再按一次就退出
            return;
        }
        if (screen == Screen.SETTINGS) {
            showChat();                             // ②：设置页 → 对话页
            return;
        }
        if (screen == Screen.CHAT) {
            // ⓪：在子会话里 → 上一级是它的主智能体（父会话），不是抽屉、更不是设备页
            if (!currentParentId.isEmpty()) {
                SessionInfo p = findSession(currentParentId);
                if (p != null) {
                    onOpenSession(p);
                    return;
                }
                currentParentId = "";               // 父会话已不在列表里：退化成顶层行为
            }
            if (now - backExitArmedAt < BACK_EXIT_WINDOW_MS) {
                moveTaskToBack(true);               // ④：抽屉已是最外层 → 退到后台
                return;
            }
            openDrawerByBack();                     // ③：对话页 → 任务列表抽屉
            return;
        }
        if (deviceOverChat) {
            showChat();                             // ②：设备页（从抽屉进的）→ 对话页
            return;
        }
        // 冷启动落在「我的设备」= 根页：再返回就退到系统后台
        moveTaskToBack(true);
    }

    /** 按 ‹ 箭头拉开抽屉：这是"看一眼任务列表"，返回键只负责关掉它。 */
    private void openDrawer() {
        drawerAsParent = false;
        if (drawerHost == null) return;
        if (listScreen != null) {
            listScreen.setCurrentSession(currentSessionId);
            listScreen.setRows(buildRows());
        }
        refreshListStatus();
        drawerHost.openDrawer(true);
    }

    /** 返回键拉开抽屉：抽屉即"最外层"，再按返回退到后台。 */
    private void openDrawerByBack() {
        openDrawer();
        drawerAsParent = true;
    }

    /**
     * 系统侧滑 / 预测式返回（Android 13+）。
     *
     * 清单里是 `enableOnBackInvokedCallback="false"`：系统把**手势返回**也当成传统的
     * KEYCODE_BACK 派发下来，由上面的 dispatchKeyEvent 接管 —— 两条路（按键 / 侧滑）
     * 因此走的是同一个 handleBackKey()，返回栈完全一致。
     *
     * 这里再把 OnBackInvokedCallback 也注册上，作为"万一开关被打开"的兜底：
     * 一旦有人把清单开关改成 true，手势返回仍然落在同一套逻辑里，
     * 而不会退化成系统默认的"直接关掉 Activity"（那样又会绕过返回栈）。
     */
    private void registerBackInvoked() {
        if (Build.VERSION.SDK_INT < 33) return;
        try {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBackKey);
        } catch (Throwable ignored) { /* 老系统 / 无 dispatcher：按键那条路依然在 */ }
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

    /** 内容层只挂当前这一屏；抽屉（会话列表）常驻 root，不参与这里的切换。 */
    private void setContent(View v) {
        contentHost.removeAllViews();
        contentHost.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void closeDrawer() {
        if (drawerHost != null) drawerHost.closeDrawer(true);
    }

    private void showChat() {
        screen = Screen.CHAT;
        deviceOverChat = false;   // 已经回到对话页，"设备页盖在对话上"这一层就没了
        if (convo == null) convo = new ConversationView(this, this);
        setContent(convo);
        convo.setCompact("compact".equals(store.displayMode()));
        convo.setItems(items);
        convo.setTitleText(titleForDisplay());
        convo.setSubtitleText(currentCwd);
        convo.setRunning(running, runningHint());
        refreshPlan();
        refreshSubagentEntry();   // 子会话进来要禁用输入并给出原因；主会话要还原
        refreshMoreStatus();   // 回到对话页时把分页状态行按当前状态重画
        paintBanner();   // 从设置页切回来时把横幅按当前状态重画（连接告警 / 流中断）
        convo.refreshNow();
        convo.scrollToBottom();
        if (listScreen != null) listScreen.setCurrentSession(currentSessionId);
    }

    private void showSettings() {
        screen = Screen.SETTINGS;
        if (settingsView == null) settingsView = new SettingsView(this, this);
        setContent(settingsView);
        settingsView.setFields(store.lanUrl(), store.wanUrl(), store.token(),
                store.deviceName(), store.useWan());
        settingsView.setDisplayMode(store.displayMode());
        // 主题档位可能与内存里那份不同（例如从别处改了 Store），以 Store 为准并同步回来
        themeMode = store.themeMode();
        settingsView.setThemeMode(themeMode);
        settingsView.setInsecureTls(store.insecureTls());
        settingsView.setAllowScreenshot(store.allowScreenshot());
        refreshAbout();
        settingsView.setUpdateHint(lastUpdateHint);
        refreshFeedbackHint();
        settingsView.setStatus(lastStateText.isEmpty() ? "未连接" : lastStateText, false);
        refreshDiagnostics();
    }

    // ============================================================ 我的设备（启动页）

    /** 进「我的设备」页并重画卡片（在线状态以当前 WebSocket 实况为准）。 */
    private void showDevices() {
        screen = Screen.DEVICE;
        if (deviceHub == null) deviceHub = new DeviceHubView(this, this);
        setContent(deviceHub);
        refreshDevices();
    }

    /**
     * 在线/离线的判定依据（**唯一**判据，不写死、不靠"配过对"就算在线）：
     * App 与那台电脑的 WebSocket 是否真的握手成功并处于 READY
     * （{@link GatewayClient.State#READY}）。断线 / 从未配对 / 电脑端网关被关掉 => 离线。
     *
     * 这里额外要求 canSend()（最近 30s 内收到过入站帧）：
     * 半开链路（对端网卡关掉、隧道断掉）在 75s 假连接判定触发前 state 仍是 READY，
     * 只看 state 会把"其实已经发不出帧"显示成在线 —— 那正是用户明确不要的假在线。
     * 正常连接每 25s 一个 ping/pong（网关 lib/index.mjs:2896 收到 ping 回 pong），
     * 30s 窗口稳得住。
     */
    private boolean isOnline() {
        return gw != null && gw.state() == GatewayClient.State.READY && gw.canSend();
    }

    /** 重画设备卡片：设备表 + 当前生效的那台 + 实况在线状态。 */
    private void refreshDevices() {
        if (deviceHub == null) return;
        java.util.List<Store.Device> list = store.devices();
        Store.Device active = store.activeDevice();
        boolean online = active != null && isOnline();
        deviceHub.setDevices(list, active == null ? "" : active.id, online);
        if (list.isEmpty()) {
            deviceHub.setStatus("还没有设备 · 点下面的「＋ 添加设备」", false);
            return;
        }
        if (online) {
            deviceHub.setStatus("已连接 " + active.displayName()
                    + (active.useWan ? " · 走公网" : " · 走内网"), false);
        } else {
            String detail = lastStateText.isEmpty() ? "还没连上" : lastStateText;
            deviceHub.setStatus("离线 · " + detail, true);
        }
    }

    /** 从设备卡片进对话页：让"冷启动自动落位"逻辑补跑一次（列表可能早就到了）。 */
    private void enterChat() {
        showChat();
        autoEntered = false;
        maybeAutoEnter();
    }

    /** 按当前生效设备（已镜像进 store 的旧字段）重开连接，并刷新设备卡片。 */
    private void gatewayReconnect() {
        String url = store.url();
        String token = store.token();
        if (url.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "这台设备还没有地址或令牌，请重新扫码或手动添加", Toast.LENGTH_LONG).show();
            refreshDevices();
            return;
        }
        String problem = GatewayClient.cleartextProblem(url);
        if (problem != null) {
            Toast.makeText(this, problem, Toast.LENGTH_LONG).show();
            refreshDevices();
            return;
        }
        failoverUsed = false;
        gw.setTrustAllCerts(store.insecureTls());
        gw.connect(url, token, store.deviceId(), store.deviceName());
        refreshDevices();
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

    private static final Comparator<SessionInfo> BY_UPDATED_DESC = new Comparator<SessionInfo>() {
        @Override public int compare(SessionInfo a, SessionInfo b) {
            return Long.compare(b.updatedAt, a.updatedAt);
        }
    };

    /**
     * 按工作区分组：rows = [「工作区 · N」, 顶层会话, (展开时)缩进子会话, ...]。
     *
     * 子智能体 / 专家团成员的会话（origin=subagent）不再平铺在主列表里，而是缩进挂在
     * 父会话下面、默认折叠——保留可发现性，又不会把主列表淹掉。父会话不可见时子会话
     * 自己升为顶层，绝不会凭空消失。
     */
    private List<Object> buildRows() {
        List<SessionInfo> vis = visibleSessions();
        java.util.Map<String, SessionInfo> byId = new java.util.HashMap<>();
        for (SessionInfo s : vis) byId.put(s.id, s);

        java.util.LinkedHashMap<String, List<SessionInfo>> childrenOf = new java.util.LinkedHashMap<>();
        List<SessionInfo> roots = new ArrayList<>();
        for (SessionInfo s : vis) {
            String p = s.parentSessionId == null ? "" : s.parentSessionId;
            SessionInfo parent = (p.isEmpty() || p.equals(s.id)) ? null : byId.get(p);
            if (parent == null) { roots.add(s); continue; }
            List<SessionInfo> g = childrenOf.get(p);
            if (g == null) { g = new ArrayList<>(); childrenOf.put(p, g); }
            g.add(s);
        }

        java.util.LinkedHashMap<String, List<SessionInfo>> groups = new java.util.LinkedHashMap<>();
        for (SessionInfo s : roots) {
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
            Collections.sort(g, BY_UPDATED_DESC);
            List<SessionInfo> flat = new ArrayList<>();
            HashSet<String> chain = new HashSet<>();
            for (SessionInfo s : g) emitSession(s, 0, childrenOf, flat, chain);
            renumberUntitled(flat);
            rows.add(k + "  ·  " + g.size());
            rows.addAll(flat);
        }
        return rows;
    }

    /** 深度优先摊平：父会话在前，展开时紧跟缩进后的子会话。 */
    private void emitSession(SessionInfo s, int depth,
                             java.util.Map<String, List<SessionInfo>> childrenOf,
                             List<SessionInfo> out, Set<String> chain) {
        // chain 只装"当前这条祖先链"，用来防父子链成环；出栈就删，
        // 免得把 id 相同的重复条目也一并吞掉（那样列表会凭空少几行）。
        if (s == null || depth > 16 || !chain.add(s.id)) return;
        s.childDepth = Math.min(depth, 4);
        List<SessionInfo> kids = childrenOf.get(s.id);
        s.childCount = kids == null ? 0 : kids.size();
        s.expanded = s.childCount > 0 && expandedParents.contains(s.id);
        out.add(s);
        if (s.expanded) {
            List<SessionInfo> sorted = new ArrayList<>(kids);
            Collections.sort(sorted, BY_UPDATED_DESC);
            for (SessionInfo c : sorted) emitSession(c, depth + 1, childrenOf, out, chain);
        }
        chain.remove(s.id);
    }

    /** 同一分组里有多个「未命名会话」时才编号，免得几条长得一模一样分不清。 */
    private static void renumberUntitled(List<SessionInfo> emitted) {
        int n = 0;
        for (SessionInfo s : emitted) if (s.title == null || s.title.trim().isEmpty()) n++;
        int i = 0;
        for (SessionInfo s : emitted) {
            if (n >= 2 && (s.title == null || s.title.trim().isEmpty())) s.untitledSeq = ++i;
            else s.untitledSeq = 0;
        }
    }

    /** 顶层会话数：子会话折叠在父会话下面，不单独算一条。 */
    private int topLevelSessionCount() {
        List<SessionInfo> vis = visibleSessions();
        Set<String> ids = new HashSet<>();
        for (SessionInfo s : vis) ids.add(s.id);
        int n = 0;
        for (SessionInfo s : vis) {
            String p = s.parentSessionId == null ? "" : s.parentSessionId;
            if (p.isEmpty() || p.equals(s.id) || !ids.contains(p)) n++;
        }
        return n;
    }

    /**
     * 冷启动自动落位（只做一次）：
     *   有会话 -> 直接进最近一条顶层会话（对话页，不再先过一遍列表页）；
     *   没会话 -> 直接拉开左侧抽屉，由列表的空态提示"还没有对话"。
     * 用户已经手动切到设置页时不打扰他。
     */
    private void maybeAutoEnter() {
        if (autoEntered || !store.paired()) return;
        autoEntered = true;
        if (!currentSessionId.isEmpty()) return;
        if (screen != Screen.CHAT) return;
        List<SessionInfo> vis = visibleSessions();
        Set<String> ids = new HashSet<>();
        for (SessionInfo s : vis) ids.add(s.id);
        for (SessionInfo s : vis) {
            String p = s.parentSessionId == null ? "" : s.parentSessionId;
            if (p.isEmpty() || p.equals(s.id) || !ids.contains(p)) {   // 最近的一条顶层会话
                onOpenSession(s);
                return;
            }
        }
        openDrawer();
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
    /** 上一次团队尾部补拉时刻（节流用）。 */
    private long lastTeamTailRefetchAt = 0L;

    /** 交付物事件实时帧没有 data，用一小段尾部历史补齐；不影响分页状态。 */
    private void refetchTailForDeliverables() {
        if (tailRefetchPending || currentSessionId.isEmpty()) return;
        tailRefetchPending = true;
        gw.requestRecentHistory(currentSessionId, 40);
    }

    /**
     * 团队事件（team/member、team/message/queued…）实时帧同样没有 data。
     * 一次团队奔跑会连发几十条，靠 tailRefetchPending 合并成一次补拉；两次补拉之间
     * 留 3s 间隔，避免在移动网络上反复重取历史。
     */
    private void refetchTailForTeam() {
        long now = System.currentTimeMillis();
        if (now - lastTeamTailRefetchAt < 3_000L) return;
        lastTeamTailRefetchAt = now;
        if (!tailRefetchPending) refetchTailForDeliverables();
    }

    /** team/* 事件族。 */
    private static boolean isTeamEvent(String type) {
        return type != null && type.startsWith("team/");
    }

    /** 已就位的专家团成员（避免同一成员的多条 phase 变更各占一行）。 */
    private final Set<String> teamMemberIds = new HashSet<>();

    /**
     * 专家团成员 / 子代理回传落到对话里（user/message 形态）。
     * payload 的 source 可能是对象（history/snapshot）或字符串（live 帧），
     * 两种形态都交给 MessageSource 统一解析。
     */
    private void appendDelegation(JSONObject payload, String text, Long seqNum, long t) {
        JSONObject src = payload.optJSONObject("source");
        appendDelegationText(MessageSource.messageId(src, text), MessageSource.sender(src, text),
                MessageSource.body(text), seqNum, t);
    }

    /**
     * 回传正文去重后建 AGENT 卡片。
     * 同一份内容既可能以 team/message/queued 到达，也可能以
     * user/message(source.kind=team-message) 到达 —— 用 messageId 认成同一条。
     */
    private void appendDelegationText(String messageId, String sender, String text, Long seqNum, long t) {
        if (text == null) return;
        String body = text.trim();
        if (body.isEmpty()) return;
        String key;
        if (messageId != null && !messageId.isEmpty()) key = "tm:" + messageId;
        else if (seqNum != null) key = "tm:s" + seqNum;
        else key = "tm:" + body.hashCode();
        if (byKey.containsKey(key)) return;
        ChatItem it = ChatItem.of(ChatItem.AGENT, key, body);
        it.agentName = sender == null ? "" : sender;
        it.time = t;
        byKey.put(key, it);
        items.add(it);
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
            int visible = topLevelSessionCount();
            s = visible + " 个对话 · " + (store.gatewayName().isEmpty() ? hostOf(store.url()) : store.gatewayName());
        } else if (!store.paired()) {
            s = "还没添加设备 · 回「我的设备」添加";
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
            // 掉线时在途的分页请求的响应永远不会来了：必须复位在途标记，
            // 否则重连后上滑加载更早历史会被 loadingMore 永久挡住。
            clearLoadMoreInFlight();
            refreshMoreStatus();
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
            // 重连后重新补标题：断线期间丢掉的探针不该让某个会话永远没有标题
            // （旧实现里 titleRequested 只进不出，丢了就再也不会重试）。
            resetTitleProbesForRetry();
            gw.requestSessions();
            // 断线会丢掉网关侧的订阅，重连后必须重新订阅，否则当前会话不再实时更新
            if (!currentSessionId.isEmpty()) {
                gw.subscribe(currentSessionId);
                gw.requestTasks(currentSessionId);
                gw.requestGoal(currentSessionId);
            }
        }
        // READY 默认不挂横幅（连接正常不该常驻一条提示）；但 hello 暴露了协议/能力问题时
        // 必须挂出来（评审 P1-16）—— 否则这条告警只活在设置页诊断里，普通用户看不到，
        // "照单全收"等于没修。用 error 配色让它醒目。
        String helloWarn = gw.helloWarning();
        boolean warnReady = st == GatewayClient.State.READY && helloWarn != null && !helloWarn.isEmpty();
        // 先落字段再画：屏幕不是 CHAT 时也要更新，否则切回对话页会画出上一次的旧横幅。
        stateBannerText = st == GatewayClient.State.READY ? (warnReady ? helloWarn : null) : detail;
        stateBannerError = err || warnReady;
        paintBanner();
        if (screen == Screen.SETTINGS && settingsView != null) {
            settingsView.setStatus(detail, err);
            refreshDiagnostics();
        }
        refreshListStatus();
        // 设备卡片上的「在线/离线」必须跟着实况走：状态一变就重画（READY 时顺带记一次"上次在线"）
        if (st == GatewayClient.State.READY) store.touchActiveSeen();
        refreshDevices();
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
        String gid = "", gname = "", dshVer = "";
        try {
            gid = hello.optString("gatewayId", "");
            gname = hello.optString("gatewayName", "");
            dshVer = hello.optString("dshVersion", "");
            if (!gid.isEmpty()) store.setGatewayId(gid);
            if (!gname.isEmpty()) store.setGatewayName(gname);
            historyFormatVersion = hello.optInt("historyFormatVersion", 4);
        } catch (Throwable ignored) { }
        // 网关告诉我们的身份/版本落进"当前这台设备"：离线时卡片也能显示名字与版本标签
        store.updateActiveMeta(gid, gname, dshVer);
        gw.requestSessions();
        refreshListStatus();
        refreshDevices();
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
        // 配对成功也要落进轨迹：这样"成功"与"失败"在诊断区里是同一份逐条记录，
        // 用户报"时好时坏"时能直接对比（token 只记长度，不记内容）
        pairTraceAdd("✓ 配对成功：已拿到设备令牌（" + token.length() + " 字符，不记内容）");
        if (settingsView != null) settingsView.setStatus("配对成功", false);
        refreshDiagnostics();
        refreshDevices();   // 设备卡片上补上刚配好的名字/地址/令牌
        gw.requestSessions();
        showChat();
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
                s.title = titleFromItem(o);
                s.cwd = o.optString("cwd", "");
                s.agentPreset = o.optString("agentPreset", "");
                s.updatedAt = o.optLong("updatedAt", 0L);
                s.running = o.optBoolean("running", false);
                s.blank = o.optBoolean("blank", false);
                // 子智能体 / 专家团识别字段：网关原样透传宿主 session.list 的同名字段
                s.parentSessionId = o.optString("parentSessionId", "");
                s.origin = o.optString("origin", "");
                s.raw = o;
                // 拿到标题就落缓存（投影缓存命中时列表本身带 title，省掉一次历史请求）
                if (!s.title.isEmpty()) store.cacheTitle(s.id, s.title);
                if (!s.id.isEmpty()) sessions.add(s);
            }
        }
        enqueueMissingTitles();

        JSONArray arch = raw.optJSONArray("archivedSessionIds");
        if (arch != null) {
            archivedIds.clear();
            for (int i = 0; i < arch.length(); i++) archivedIds.add(arch.optString(i));
        }
        if (listScreen != null) listScreen.setRows(buildRows());
        refreshListStatus();
        // 会话列表一变，子智能体数量与"父会话还在不在"都可能变：
        // 父会话被归档/删掉时，「人在子会话里」这一层就没有上一级可回了，就地退回顶层。
        if (!currentParentId.isEmpty() && findSession(currentParentId) == null) currentParentId = "";
        refreshSubagentEntry();
        maybeAutoEnter();
        // 重连时保守保留的"运行中"，用会话说里的权威 running 补判一次（评审 N1）：
        // 快照的历史窗口可能不含 turn/end，只靠快照回放会漏掉"回合已在断线期间结束"，
        // 那样停止按钮会一直卡着。这里只在网关明确说该会话已不在跑时才复位。
        if (runningUncertain && !turnRunningOnGateway()) setRunning(false);
    }

    @Override
    public void onHistory(String sessionId, JSONArray events, JSONObject meta) {
        // 标题探针的请求不带 view=conversation，响应也就没有 view 字段；
        // 会话历史页一定带。用这个把两者分开，探针响应绝不写进对话。
        boolean isTitleProbe = !"conversation".equals(meta == null ? "" : meta.optString("view", ""));

        String found = extractTitle(events);
        boolean wasAwaiting = titleAwaiting.remove(sessionId);
        if (wasAwaiting) {
            titleDeadline.remove(sessionId);
            if (titleInFlight > 0) titleInFlight--;
        }
        if (!found.isEmpty()) {
            store.cacheTitle(sessionId, found);
            titleRequested.remove(sessionId);
            titleAttempts.remove(sessionId);
            titleDeferredUntil.remove(sessionId);
            for (SessionInfo si : sessions) if (si.id.equals(sessionId)) si.title = found;
            if (sessionId.equals(currentSessionId)) {
                currentTitle = found;
                if (convo != null) convo.setTitleText(titleForDisplay());
            }
            if (listScreen != null) listScreen.setRows(buildRows());
        } else if (wasAwaiting) {
            // 探针回来了但没抽到标题：可能是标题事件还没生成（新会话），
            // 过一会儿再补一次；次数用完就等重连（resetTitleProbesForRetry）再来。
            if (attemptsOf(sessionId) < TITLE_MAX_TRIES) {
                titleDeferredUntil.put(sessionId, System.currentTimeMillis() + TITLE_EMPTY_RETRY_MS);
            } else {
                titleRequested.remove(sessionId);
            }
        }
        if (wasAwaiting) pumpTitleQueue();

        if (!sessionId.equals(currentSessionId)) return;
        if (isTitleProbe) return;   // 标题探针：只更新标题，不碰会话历史
        cancelSendWatchdog();   // 该会话的历史回来了 = 连接与回合都是活的

        if (tailRefetchPending) {
            tailRefetchPending = false;
            if (events != null) {
                for (int i = 0; i < events.length(); i++) {
                    JSONObject e = events.optJSONObject(i);
                    if (e == null) continue;
                    String ty = e.optString("type", "");
                    if ("deliverables/presented".equals(ty) || "todo/write".equals(ty)
                            || "goal/change".equals(ty) || ty.startsWith("team/")) {
                        applyEvent(ty, e.optJSONObject("data"), e.opt("seq"), e.opt("time"), true);
                    }
                }
            }
            rebuildOrder();
            if (convo != null) { convo.setItems(items); convo.refresh(); }
            return; // 不触碰 hasMore / nextBeforeSeq
        }

        // 这一页回来了 = 在途结束：先收掉在途标记与超时任务，再看响应怎么描述"还有没有更早"。
        clearLoadMoreInFlight();
        historyFormatVersion = meta.optInt("historyFormatVersion", historyFormatVersion);
        // 只有响应**明确**给出 hasMore 时才改它：字段缺失必须保持原值。
        // 旧写法 optBoolean("hasMore", false) 把"字段缺失"也当成"没有更多"，
        // 于是漏一次字段就能把整个会话钉死在"看不到更早内容"。
        if (meta.has("hasMore")) hasMore = meta.optBoolean("hasMore", false);
        if (meta.has("nextBeforeSeq")) {
            long nb = meta.optLong("nextBeforeSeq", -1L);
            nextBeforeSeq = nb > 0 ? Long.valueOf(nb) : null;
        }
        // 明确"没有更多"（hasMore=false 或 nextBeforeSeq 缺失/非法）时才清游标、关分页。
        if (!hasMore) nextBeforeSeq = null;
        refreshMoreStatus();
        if (events != null) {
            // 走到这里的一定是「加载更早」那一页（requestHistory 只在 onLoadMore 里带
            // beforeSeq 发出；尾部补拉在上面已经 return，标题探针也在更上面 return）。
            // items 的顺序是"加入顺序"：旧页后到，不搬到最前面就会挂到对话末尾 —— 历史顺序颠倒。
            Set<ChatItem> had = new HashSet<>(items);
            for (int i = 0; i < events.length(); i++) {
                JSONObject e = events.optJSONObject(i);
                if (e == null) continue;
                applyEvent(e.optString("type", ""), e.optJSONObject("data"),
                        e.opt("seq"), e.opt("time"), true);
            }
            prependNewItems(had);
        }
        rebuildOrder();
        if (convo != null) { convo.setItems(items); convo.refresh(); }
    }

    @Override
    public void onSnapshot(String sessionId, JSONObject snap) {
        if (!sessionId.equals(currentSessionId)) return;
        clearStreamNotice();   // 快照到了 = 这个会话的流已经接上（快照 0 条事件时也走这里）
        cancelSendWatchdog();
        historyFormatVersion = snap.optInt("historyFormatVersion", historyFormatVersion);
        hasMore = snap.optBoolean("hasMore", false);
        nextBeforeSeq = snap.has("nextBeforeSeq") ? snap.optLong("nextBeforeSeq") : null;
        // 快照 = 会话重置成新基线：在途的那一页已经没有意义，一起复位，
        // 否则 loadingMore 会残留成"永久挡住上滑加载更早历史"。
        resetLoadMore();

        items.clear();
        byKey.clear();
        seenSeq.clear();
        teamMemberIds.clear();   // 与 items 一起重建，否则重开会话后「成员就位」不再补回
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
        clearStreamNotice();   // 流式增量回来了 = 这个会话的流已经接上
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
     * retrying=true 表示网关侧的会话跟随器还会自己重开（session-follower.mjs:123
     * onError({..., retrying: !permanent})），退避 1s→2s→…→30s。旧实现在这里**每帧弹一次
     * Toast**，只要长期不恢复就是用户报的「一直弹」（真机实测 9 秒 4 帧）。
     *
     * 现在统一走顶部内联状态条（见 streamNotice* 字段）：节流 30s、恢复即消失、
     * 超过 30s 变成可点的「还没接上，点这里重新连接」。**retrying=true 时仍然不停
     * 「运行中」、不摘气泡**（评审 P1-5），这条约束不变。
     */
    @Override
    public void onStreamReset(String sessionId, String code, String message, boolean retrying) {
        // 诊断先记：每一次都记，不受节流影响（用户要的就是「偶发还是持续」一眼可辨）
        logStreamReset(code, retrying);
        if (sessionId != null && !sessionId.isEmpty() && !sessionId.equals(currentSessionId)) return;
        if (retrying) {
            // 只挂/刷新内联横幅，绝不 Toast：网关还在自己重试，这里给一条安静的、可自动消失的提示。
            showStreamNotice(false, streamNoticeTextOf(code), sessionId);
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
        // 终止性中断也走内联横幅：它是「真的断了」这一结论，横幅比一闪而过的 Toast 更该常驻，
        // 同样在收到该会话任何新帧时自动消失，超过 30 秒则给出重连入口。
        showStreamNotice(true, why.isEmpty() ? "输出被中断了" : ("输出被中断了：" + why), sessionId);
    }

    // ---------------------------------------------------------------- 会话流中断：提示与诊断

    /** retrying 中断的横幅文案；带 code 时附在后面，方便与设置页诊断对上。 */
    private static String streamNoticeTextOf(String code) {
        if (code == null || code.isEmpty()) return "连接抖动，正在恢复…";
        return "连接抖动，正在恢复…（" + code + "）";
    }

    /**
     * 挂/刷新顶部内联横幅。节流规则：
     *   - 同一个会话已经挂着提示 → 直接返回（退避重试的后续帧不再刷新，避免闪）；
     *   - 同一个会话 30 秒内刚提示过 → 也返回（「同一会话 30 秒内最多提示一次」）。
     * 第一次显示时排一个 30 秒后的任务，把横幅翻成可点的重连入口（持续抖动要有出口）。
     */
    private void showStreamNotice(boolean error, String text, String sessionId) {
        String sid = sessionId == null ? "" : sessionId;
        if (text == null || text.isEmpty()) return;
        if (!streamNoticeText.isEmpty() && streamNoticeSession.equals(sid)) return;
        long now = System.currentTimeMillis();
        if (sid.equals(streamNoticeLastSession) && now - streamNoticeLastAt < STREAM_NOTICE_THROTTLE_MS) return;

        streamNoticeLastSession = sid;
        streamNoticeLastAt = now;
        streamNoticeSession = sid;
        streamNoticeText = text;
        streamNoticeError = error;
        streamNoticeActionable = false;

        if (streamNoticeActionTask != null) uiHandler.removeCallbacks(streamNoticeActionTask);
        streamNoticeActionTask = new Runnable() {
            @Override public void run() {
                streamNoticeActionTask = null;
                if (streamNoticeText.isEmpty()) return;
                streamNoticeActionable = true;
                paintBanner();
            }
        };
        uiHandler.postDelayed(streamNoticeActionTask, STREAM_NOTICE_ACTION_MS);
        paintBanner();
    }

    /**
     * 收到该会话任何新帧 / 快照 = 已经接上了：撤掉提示，并停掉「持续恢复中」的计时。
     * 调用点在 applyEvent（实时帧 / 历史 / 快照的唯一归并口）与 onAssistantStream。
     */
    private void clearStreamNotice() {
        if (streamNoticeActionTask != null) {
            uiHandler.removeCallbacks(streamNoticeActionTask);
            streamNoticeActionTask = null;
        }
        if (streamNoticeText.isEmpty()) return;
        streamNoticeText = "";
        streamNoticeSession = "";
        streamNoticeError = false;
        streamNoticeActionable = false;
        paintBanner();
    }

    /**
     * 画对话页顶部那一条横幅。两类信息共用一条：
     *   ① 连接状态（onState 的 detail / hello 告警）——优先级更高；
     *   ② 会话流中断（「连接抖动，正在恢复…」/「输出被中断了…」）。
     */
    private void paintBanner() {
        if (screen != Screen.CHAT || convo == null) return;
        if (stateBannerText != null && !stateBannerText.isEmpty()) {
            convo.setBanner(stateBannerText, stateBannerError, false, null);
            return;
        }
        if (streamNoticeText.isEmpty()) {
            convo.setBanner(null, false);
            return;
        }
        convo.setBanner(
                streamNoticeActionable
                        ? (streamNoticeText + "　还没接上，点这里重新连接")
                        : streamNoticeText,
                streamNoticeError,
                streamNoticeActionable,
                streamNoticeActionable ? this::reconnectCurrentSession : null);
    }

    /**
     * 内联横幅「点这里重新连接」的动作：重新 subscribe，必要时重开连接。
     *
     *   ① 用户已经明确要重试 → 先清掉旧提示（不该再挂着上一次的结论）；
     *   ② wantConnected=false（此前已断开）→ 用已存配置重新 connect；
     *      还想要连接但已经发不出帧（canSend=false，半开链路 / 重连窗口）→ retryNow() 重开一条；
     *   ③ 无论如何都补一次 subscribe，让这个会话的实时流重新申请一次。
     */
    private void reconnectCurrentSession() {
        clearStreamNotice();
        if (currentSessionId.isEmpty()) return;
        if (!gw.wantConnected()) {
            String url = store.url();
            if (!url.isEmpty()) gw.connect(url, store.token(), store.deviceId(), store.deviceName());
        } else if (!gw.canSend()) {
            gw.retryNow();
        }
        gw.subscribe(currentSessionId);
        gw.requestTasks(currentSessionId);
        cancelSendWatchdog();
        refreshDiagnostics();
    }

    /**
     * 记一条会话流中断到设置页诊断区：`时间 / code / retrying`，只留最近 10 条。
     * **不记 message 明文**——这条日志的用途是分辨「偶发」还是「持续」，正文没有用。
     */
    private void logStreamReset(String code, boolean retrying) {
        String t = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(new java.util.Date());
        String line = t + "  " + (code == null || code.isEmpty() ? "(无 code)" : code)
                + "  retrying=" + (retrying ? "true" : "false");
        streamResetLog.addLast(line);
        while (streamResetLog.size() > STREAM_RESET_LOG_MAX) streamResetLog.removeFirst();
        refreshDiagnostics();
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
            resetLoadMore();   // 请求被网关拒了：在途标记必须一起收掉
            return;
        }
        // 「这个会话读不了」这类网关侧拒绝（典型：子会话在宿主侧必须带 durable parent
        // address，而网关的 host-adapter 只发 {kind:'session'} → session/agent-busy）改成走
        // 顶部内联横幅，不再弹一条英文错误 Toast。横幅里已经带了 code，而且会一直挂到真的
        // 接上为止；用户报的「一直弹」里也包含这一条。
        if ("session/agent-busy".equals(code)) {
            if (sessionId == null || sessionId.isEmpty() || sessionId.equals(currentSessionId)) {
                showStreamNotice(false,
                        "这个会话暂时读不到内容（" + code + "），还在重试…",
                        sessionId == null ? "" : sessionId);
            }
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
            d.name = safeDownloadName(frame.optString("name", "file"));
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

    /**
     * 净化服务端帧给的文件名（安全评审 B5）。
     *
     * name 来自网关的 file-download-opened 帧，会被直接当成 MediaStore 的 DISPLAY_NAME，
     * 在 API&lt;29 上还会拼进 new File(dir, name) —— 一旦含 ".." 或路径分隔符，就能越出
     * 「下载」目录把内容写到任意可写位置（例如 /sdcard 根或应用私有目录）。
     * 这里只保留最后一段基本名，并剔除控制字符与 Windows 保留字符。
     */
    private static String safeDownloadName(String raw) {
        String n = raw == null ? "" : raw.trim();
        // 只取最后一段：同时干掉 ".."、"/" 与 "\"（Windows 上 File 把两者都当分隔符）
        int cut = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (cut >= 0) n = n.substring(cut + 1);
        // 控制字符与 Windows 保留字符：避免非法文件名与隐藏/特殊名
        n = n.replaceAll("[\\x00-\\x1f\\x7f<>:\"|?*]", "_").trim();
        // 开头的点去掉：".", "..", ".nomedia" 这类要么无意义要么是特殊名
        while (n.startsWith(".")) n = n.substring(1);
        if (n.isEmpty()) return "file";
        if (n.length() > 120) {
            int dot = n.lastIndexOf('.');
            String ext = (dot > 0 && n.length() - dot <= 12) ? n.substring(dot) : "";
            n = n.substring(0, Math.min(dot > 0 ? dot : n.length(), 120 - ext.length())) + ext;
        }
        return n;
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
            if (!title.trim().isEmpty()) {
                // 宿主/桌面端改了标题：立刻落缓存，并停掉这个会话的标题探针
                store.cacheTitle(sid, title);
                titleRequested.remove(sid);
                titleAttempts.remove(sid);
                titleDeferredUntil.remove(sid);
                titleQueue.remove(sid);
            }
            for (SessionInfo s : sessions) if (s.id.equals(sid)) s.title = title;
            if (sid.equals(currentSessionId)) {
                currentTitle = title;
                if (convo != null) convo.setTitleText(titleForDisplay());
            }
            if (listScreen != null) listScreen.setRows(buildRows());
        }
    }

    // ============================================================ 事件归并

    private void applyEvent(String type, JSONObject payload, Object seq, Object time, boolean historical) {
        // 走到这里 = 当前会话确实有东西进来了（实时帧 / 历史 / 快照都归并到这一个口）。
        // 「连接抖动，正在恢复…」的前提已经不成立，立刻撤掉内联提示。
        clearStreamNotice();
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

        // team/* 同样不在网关白名单里：实时帧只有 {type}（data 被 buildWireEvent 的
        // default 分支整个丢掉，见网关 lib/index.mjs:408-409），所以补拉一次尾部历史 ——
        // 历史帧走 historyPage()，用的是宿主原始事件，data 是完整的。
        // 判据「除 type 外没有字段」，避免误伤正常帧。
        if (payload.length() <= 1 && isTeamEvent(type)) {
            refetchTailForTeam();
            return;
        }

        switch (type) {
            case "user/message": {
                String text = textOfMessage(payload);
                if (isInjectedContext(payload, text)) return;
                // 专家团成员 / 子代理回传也是 user/message（source.kind = team-message /
                // agent-message）：说话的不是用户，单独成卡，否则既会伪装成用户气泡、
                // 又会在历史形态下被注入过滤器整段丢掉（判据见 MessageSource）。
                if (MessageSource.isDelegation(MessageSource.kindOf(payload))) {
                    appendDelegation(payload, text, seqNum, t);
                    break;
                }
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
            // ------------------------------------------------- 专家团（team/*）
            // 这些事件不在网关白名单里，实时帧只剩 {type}；带 data 的形态来自上面
            // isTeamEvent 那条分支补拉的历史（宿主原始事件）。
            case "team/member": {
                JSONObject m = payload.optJSONObject("member");
                if (m == null) break;
                String mid = m.optString("id", "");
                String label = m.optString("description", "");
                if (label.isEmpty()) label = m.optString("name", "");
                if (mid.isEmpty() || label.isEmpty() || !teamMemberIds.add(mid)) break;
                ChatItem it = ChatItem.of(ChatItem.SYSTEM, "tmm:" + mid, "👥 专家团成员就位：" + label);
                it.time = t;
                byKey.put(it.key, it);
                items.add(it);
                break;
            }
            case "team/message/queued": {
                JSONObject msg = payload.optJSONObject("message");
                if (msg == null) break;
                StringBuilder sb = new StringBuilder();
                JSONArray parts = msg.optJSONArray("content");
                if (parts != null) {
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject p = parts.optJSONObject(i);
                        if (p != null) sb.append(p.optString("text", ""));
                    }
                }
                appendDelegationText(msg.optString("id", ""), msg.optString("senderName", ""),
                        sb.toString(), seqNum, t);
                break;
            }
            case "team/task":
            case "team/message/delivered":
                // 任务条目与投递回执本身没有新的正文，不单独占一行。
                break;
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
                    if (convo != null) convo.setTitleText(titleForDisplay());
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
     *
     * 但「非 user 即丢弃」曾把专家团成员（team-message）与子代理（agent-message）
     * 回传的正文一起丢掉，且只在历史形态下丢（那时 source 是对象，实时帧里是字符串）。
     * 判据已抽到 MessageSource，这里只做转发。
     */
    private static boolean isInjectedContext(JSONObject payload, String text) {
        return MessageSource.isInjected(MessageSource.kindOf(payload), text);
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

    /**
     * 列表项自带的标题。sessions 契约里 item 本身没有 title 字段（宿主没下发），
     * 但 item.projections.values.title 在投影缓存命中时会有——有就白拿，省掉一次历史请求。
     */
    private static String titleFromItem(JSONObject o) {
        if (o == null) return "";
        String t = o.optString("title", "");
        if (t != null && !t.trim().isEmpty()) return t.trim();
        JSONObject proj = o.optJSONObject("projections");
        if (proj == null) return "";
        JSONObject values = proj.optJSONObject("values");
        if (values == null) return "";
        String pt = values.optString("title", "");
        return pt == null ? "" : pt.trim();
    }

    /**
     * 给所有「还没有标题、也不空」的会话排队探测标题。
     *
     * 每次 onSessions（含断线重连后的刷新）都会重新走一遍：已经拿到的走缓存，
     * 还没拿到的只要没超过 TITLE_MAX_TRIES 就继续排——不会再出现"某次请求丢了，
     * 这个会话就永远没有标题"。
     */
    private void enqueueMissingTitles() {
        for (SessionInfo s : sessions) {
            String cached = store.cachedTitle(s.id);
            if (!cached.isEmpty()) { s.title = cached; continue; }
            if (s.blank) continue;
            if (titleRequested.contains(s.id)) continue;
            if (attemptsOf(s.id) >= TITLE_MAX_TRIES) continue;
            titleRequested.add(s.id);
            titleQueue.add(s.id);
        }
        pumpTitleQueue();
    }

    private int attemptsOf(String sid) {
        Integer n = titleAttempts.get(sid);
        return n == null ? 0 : n;
    }

    private void pumpTitleQueue() {
        while (titleInFlight < TITLE_MAX_INFLIGHT && !titleQueue.isEmpty()) {
            String sid = titleQueue.poll();
            if (sid == null || sid.isEmpty()) continue;
            if (!store.cachedTitle(sid).isEmpty()) { titleRequested.remove(sid); continue; }
            if (!titleRequested.contains(sid)) titleRequested.add(sid);
            titleInFlight++;
            titleAwaiting.add(sid);
            titleAttempts.put(sid, attemptsOf(sid) + 1);
            titleDeadline.put(sid, System.currentTimeMillis() + TITLE_TIMEOUT_MS);
            gw.requestSessionTitle(sid, historyFormatVersion);
        }
        armTitleSweep();
    }

    /**
     * 排一次标题重试扫描。只在真有"在途"或"待补"探针时才排，扫完自动续期；
     * 都空了就停，不常驻定时器。
     */
    private void armTitleSweep() {
        if (titleSweep != null) return;
        if (titleAwaiting.isEmpty() && titleDeferredUntil.isEmpty()) return;
        titleSweep = new Runnable() {
            @Override public void run() {
                titleSweep = null;
                titleSweepOnce();
                if (!titleAwaiting.isEmpty() || !titleDeferredUntil.isEmpty()) armTitleSweep();
            }
        };
        uiHandler.postDelayed(titleSweep, 1_000L);
    }

    /** 一次扫描：超时未回的探针重入队；空结果的到点补探。 */
    private void titleSweepOnce() {
        long now = System.currentTimeMillis();
        for (String sid : new ArrayList<>(titleAwaiting)) {
            Long dl = titleDeadline.get(sid);
            if (dl == null || now < dl.longValue()) continue;
            titleAwaiting.remove(sid);
            titleDeadline.remove(sid);
            if (titleInFlight > 0) titleInFlight--;
            if (attemptsOf(sid) < TITLE_MAX_TRIES) {
                titleQueue.add(sid);            // titleRequested 保留，拿到标题或放弃时才放开
            } else {
                titleRequested.remove(sid);     // 用尽次数：这一轮放弃，等重连再试
            }
        }
        for (String sid : new ArrayList<>(titleDeferredUntil.keySet())) {
            Long at = titleDeferredUntil.get(sid);
            if (at == null || now < at.longValue()) continue;
            titleDeferredUntil.remove(sid);
            if (attemptsOf(sid) < TITLE_MAX_TRIES && store.cachedTitle(sid).isEmpty()) {
                titleRequested.add(sid);
                titleQueue.add(sid);
            }
        }
        pumpTitleQueue();
    }

    /**
     * 断线重连后清掉"这一轮放弃"的记录，让还没标题的会话重新排队补标题。
     * 在途的探针要保留 titleRequested，避免同一个会话被重复探测。
     */
    private void resetTitleProbesForRetry() {
        titleQueue.clear();
        titleDeferredUntil.clear();
        titleAttempts.clear();
        titleRequested.clear();
        for (String sid : titleAwaiting) titleRequested.add(sid);
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
        // 换会话就把上一条流中断提示撤掉：它描述的是上一个会话的流，留着会误导
        // （尤其是刚从一个长期连不上的子会话切到一个正常会话时）。
        clearStreamNotice();
        items.clear();
        byKey.clear();
        seenSeq.clear();
        teamMemberIds.clear();   // 与 items 一起重建，否则重开会话后「成员就位」不再补回
        streamAttemptKey = null;
        hasMore = false;
        nextBeforeSeq = null;
        resetLoadMore();   // 换会话：在途分页作废，别让上一页的失败/在途状态残留到新会话
        planGoal = ""; planPhase = ""; planTodos = "";
        // 重订阅是把列表清空、等回 snapshot 再重建（重建在 onSnapshot 里完成）。
        // 此刻 byKey 为空，migratePendingInteractions() 只做"保留待确认条目"这件事：
        // 卡片不会丢状态，等 snapshot 到达后再按 key 迁移到新对象（评审 P0-3）。
        migratePendingInteractions();
        gw.subscribe(currentSessionId);
        gw.requestTasks(currentSessionId);
        gw.requestGoal(currentSessionId);
        if (screen != Screen.CHAT) {
            showChat();
        } else if (convo != null) {
            // 在抽屉里点另一条任务时不会走 showChat()（人已经在对话页），
            // 但标题/副标题必须跟着换，否则会出现"内容换了、标题还是上一条"。
            convo.setTitleText(titleForDisplay());
            convo.setSubtitleText(currentCwd);
        }
        if (convo != null) {
            convo.setItems(items);
            convo.refreshNow();
            refreshSubagentEntry();   // 换了会话：「👥 N 子智能体」入口与只读态随之重算
        }
    }

    // ============================================================ SessionListView.Host

    @Override
    public void onOpenSession(SessionInfo s) {
        pendingBySession.remove(s.id);
        s.pending = 0;
        currentSessionId = s.id;
        currentTitle = s.display();
        currentCwd = s.cwd;
        String p = s.parentSessionId == null ? "" : s.parentSessionId;
        // 只有父会话确实在列表里（且不是自己）才算「人在子会话里」，返回键才有确定的上一级
        currentParentId = (!p.isEmpty() && !p.equals(s.id) && findSession(p) != null) ? p : "";
        closeDrawer();          // 选中任务 -> 抽屉收起 -> 右侧对话扩大并进入该任务
        if (listScreen != null) listScreen.setCurrentSession(currentSessionId);
        subscribeCurrent();
    }

    /** 展开/折叠某父会话名下的子会话（子智能体 / 专家团）。 */
    @Override
    public void onToggleChildren(SessionInfo s) {
        if (s == null || s.id == null || s.id.isEmpty()) return;
        if (!expandedParents.remove(s.id)) expandedParents.add(s.id);
        if (listScreen != null) listScreen.setRows(buildRows());
    }

    // ============================================================ 子智能体（子会话）导航

    /** 按 id 找一个「列表里真的看得见」的会话（含子会话）：与 visibleSessions 同口径。 */
    private SessionInfo findSession(String id) {
        if (id == null || id.isEmpty()) return null;
        for (SessionInfo s : sessions) {
            if (id.equals(s.id) && !archivedIds.contains(s.id) && !s.blank) return s;
        }
        return null;
    }

    /**
     * 某条会话名下的**直接**子会话，按更新时间倒序。
     *
     * 判据与抽屉折叠完全同源（见 buildRows / emitSession 用的 sessions 条目
     * parentSessionId）：抽屉那行「▸ N 子会话」数出来的就是这一批，
     * 两边显示的 N 永远一致。空会话（blank）与已归档的和抽屉一样不算。
     */
    private List<SessionInfo> childrenOf(String parentId) {
        List<SessionInfo> out = new ArrayList<>();
        if (parentId == null || parentId.isEmpty()) return out;
        for (SessionInfo s : sessions) {
            if (archivedIds.contains(s.id) || s.blank) continue;
            if (s.id.equals(parentId)) continue;
            String p = s.parentSessionId == null ? "" : s.parentSessionId;
            if (p.isEmpty() || p.equals(s.id)) continue;
            if (!parentId.equals(p)) continue;
            out.add(s);
        }
        Collections.sort(out, BY_UPDATED_DESC);
        return out;
    }

    /** 子智能体入口/弹窗围绕哪条会话展开：在子会话里时用它的父会话（于是能切兄弟会话），否则就是当前会话。 */
    private String subagentRootId() {
        return currentParentId.isEmpty() ? currentSessionId : currentParentId;
    }

    /**
     * 标题栏文案：子会话里带上上级 —— `主标题 › 子代号`。
     * 一眼看出"人在某个子会话里"，而不是以为内容串了。
     */
    private String titleForDisplay() {
        if (currentParentId.isEmpty()) return currentTitle.isEmpty() ? "对话" : currentTitle;
        SessionInfo p = findSession(currentParentId);
        String base = p == null ? "" : p.display();
        String child = currentTitle.isEmpty() ? "子会话" : currentTitle;
        return (base.isEmpty() ? "子智能体" : base) + " › " + child;
    }

    /**
     * 按当前会话重画「👥 N 子智能体」入口与只读态。
     *
     * 状态判据就用 sessions 条目上的权威字段：running（宿主 session.list 原样透传，
     * onSessions 里 optBoolean("running")）+ pending（本机内存里记的待回答/待批准）。
     * 不猜、不靠历史里的 turn/end 反推。
     */
    private void refreshSubagentEntry() {
        if (convo == null) return;
        boolean inChild = !currentParentId.isEmpty();
        convo.setSubagentEntry(childrenOf(subagentRootId()).size(), inChild);
        if (inChild) {
            convo.setReadOnly(true,
                    "子会话只读：内容由父智能体驱动，往这里发消息会被宿主拒绝（session/agent-busy）。"
                            + "点上方「子智能体」可切换，返回可回主智能体继续对话。");
        } else {
            convo.setReadOnly(false, "");
        }
    }

    @Override
    public void onNewChat() {
        currentSessionId = "";
        currentTitle = "新对话";
        currentCwd = "";
        currentParentId = "";
        items.clear();
        byKey.clear();
        seenSeq.clear();
        teamMemberIds.clear();   // 与 items 一起重建，否则重开会话后「成员就位」不再补回
        streamAttemptKey = null;
        hasMore = false;
        nextBeforeSeq = null;
        resetLoadMore();   // 新建会话：分页状态归零
        setRunning(false);
        closeDrawer();          // 新建/派任务 -> 抽屉收起，进入空白对话
        if (listScreen != null) listScreen.setCurrentSession("");
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
    public void onSettings() {
        closeDrawer();          // 设置入口就在抽屉里：点完先收抽屉再进设置
        showSettings();
    }

    /** 抽屉里的「我的设备」入口：收起抽屉，进设备页看在线状态；返回键回对话页（不是根页）。 */
    @Override
    public void onDevices() {
        closeDrawer();
        deviceOverChat = true;
        showDevices();
    }

    @Override
    public void onRefresh() {
        gw.requestSessions();
        Toast.makeText(this, "已刷新", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRename(SessionInfo s) {
        // 用对话框自己的主题 Context 建输入框：用 Activity 建的话拿到的是 Light 主题的
        // 默认文字色，深色对话框里就成了深字压深底（看不见）。
        final EditText input = new EditText(Ui.dialogContext(this));
        input.setText(s.display());
        input.setSelectAllOnFocus(true);
        Ui.dialog(this)
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

    /** 设置页左上角 ‹：回对话页（设置页是从抽屉进来的，返回栈上一级是对话）。 */
    @Override
    public void onBack() {
        showChat();
    }

    /** 对话页左上角 ‹：打开左侧任务列表抽屉（豆包式：箭头 = 进任务列表）。 */
    @Override
    public void onOpenTasks() {
        openDrawer();
    }

    /** 点输入框上方的「👥 N 子智能体」：底部弹窗列出当前会话的全部子智能体。 */
    @Override
    public void onOpenSubagents() {
        openSubagentSheet();
    }

    /**
     * 子智能体底部弹窗。
     *
     * 第一行永远是**主智能体**（可点回主），下面按更新时间倒序列出全部子会话，
     * 当前正在看的那一项高亮。点任意一项都走 onOpenSession —— 与抽屉里点会话卡片
     * 是同一条路（订阅 / 拉历史 / 标题都会跟着换），不另造一套切换逻辑。
     */
    private void openSubagentSheet() {
        String rootId = subagentRootId();
        if (rootId.isEmpty()) {
            Toast.makeText(this, "还没有选中会话", Toast.LENGTH_SHORT).show();
            return;
        }
        SessionInfo root = findSession(rootId);
        if (root == null) {
            // 会话列表还没到位（或它已不在列表里）：用内存里现有的信息拼一个，弹窗照样能用
            root = new SessionInfo();
            root.id = rootId;
            if (rootId.equals(currentSessionId)) {
                root.title = currentTitle;
                root.cwd = currentCwd;
            } else {
                root.title = "主智能体";
            }
        }
        SubagentSheet.show(this, root, childrenOf(rootId), currentSessionId, s -> onOpenSession(s));
    }

    @Override
    public void onSend(String text) {
        if (convo != null) convo.setBanner(null, false);
        // 子会话（origin=subagent）在宿主侧是只读寻址：SessionController.prompt →
        // resolveAgent → session/agent-busy「owned by subagent routing」。输入框已经禁用，
        // 这里再兜一层，确保不会静默发进黑洞。
        if (!currentParentId.isEmpty()) {
            Toast.makeText(this, "子会话只读：回主智能体才能发消息", Toast.LENGTH_LONG).show();
            return;
        }
        if (gw.state() != GatewayClient.State.READY) {
            Toast.makeText(this, "还没连上电脑端，回「我的设备」点连接", Toast.LENGTH_LONG).show();
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

    /**
     * ConversationView.Host 的「停止当前回合」回调。
     *
     * 名字从 {@code onStop()} 改过来的：那是 Activity 生命周期方法名，两者签名撞车，
     * 会诱导这个回调变成 Activity.onStop 的实现并漏掉 super.onStop()，
     * 切后台/锁屏即抛 SuperNotCalledException（真机 logcat 实测崩溃）。
     * 生命周期 onStop 见本文件下面的 {@code protected void onStop()}。
     */
    @Override
    public void onStopTurn() {
        if (currentSessionId.isEmpty()) return;
        // 子会话同理由宿主拒绝（停它要走 subagents.interruptByParent，本网关没有这条通道）
        if (!currentParentId.isEmpty()) {
            Toast.makeText(this, "子会话只读：要停请回主智能体", Toast.LENGTH_LONG).show();
            return;
        }
        // 断网时"已请求停止"是谎报：停止帧进黑洞，用户以为停了，实际回合还在跑（评审 P1-12）
        if (!gw.canSend()) {
            Toast.makeText(this, "还没连上电脑端，停止请求没有发出去；连上后请重试",
                    Toast.LENGTH_LONG).show();
            return;
        }
        gw.stopSession(currentSessionId);
        Toast.makeText(this, "已请求停止", Toast.LENGTH_SHORT).show();
    }

    /** 分页状态 -> 消息列表正上方那一行（在途 / 失败可重试 / 收起）。 */
    private void refreshMoreStatus() {
        if (convo == null) return;
        if (loadingMore) {
            convo.setMoreStatus("正在加载更早…", false);
        } else if (loadMoreFailed) {
            convo.setMoreStatus("加载更早失败，点这里重试", true);
        } else {
            convo.setMoreStatus("", false);
        }
    }

    /** 收掉在途标记与超时任务（响应到达 / 失败 / 换会话 / 重连 / 新建都走这里）。 */
    private void clearLoadMoreInFlight() {
        loadingMore = false;
        if (loadMoreTimeout != null) {
            uiHandler.removeCallbacks(loadMoreTimeout);
            loadMoreTimeout = null;
        }
    }

    /** 复位整个分页状态：在途标记、超时任务、失败提示（避免残留卡住上滑）。 */
    private void resetLoadMore() {
        clearLoadMoreInFlight();
        loadMoreFailed = false;
        refreshMoreStatus();
    }

    /**
     * 把 `had` 之后新加入 items 的条目整体挪到列表最前面，保持页内原有先后。
     *
     * 为什么需要它：「加载更早」返回的是**更旧**的事件，而 items 的物理顺序是"加入顺序"，
     * 旧页总是后到 —— 不搬就会整段挂到对话末尾，历史顺序彻底颠倒（看到的像乱序）。
     */
    private void prependNewItems(Set<ChatItem> had) {
        List<ChatItem> fresh = new ArrayList<>();
        for (ChatItem it : items) if (!had.contains(it)) fresh.add(it);
        if (fresh.isEmpty()) return;
        Set<ChatItem> freshSet = new HashSet<>(fresh);
        List<ChatItem> reordered = new ArrayList<>(items.size());
        reordered.addAll(fresh);
        for (ChatItem it : items) if (!freshSet.contains(it)) reordered.add(it);
        items.clear();
        items.addAll(reordered);
    }

    @Override
    public void onLoadMore() {
        // 在途时忽略新的上滑触发：ListView 在 first==0 时每个滚动帧都会回调，
        // 不挡住就会对同一页连发好几次 history 请求。
        if (loadingMore) return;
        if (!hasMore || nextBeforeSeq == null || currentSessionId.isEmpty()) return;
        // 没连上就发出去等于进黑洞：直接给可重试的失败态，别让用户以为"正在加载"。
        if (!gw.canSend()) {
            loadMoreFailed = true;
            refreshMoreStatus();
            return;
        }
        loadMoreFailed = false;
        loadingMore = true;
        refreshMoreStatus();
        // **不再预置 hasMore = false**：只有响应明确说"没有更多"时才置 false（见 onHistory）。
        // 旧写法一发出请求就把 hasMore 关掉，响应一旦丢帧/遇重连/超时，这个会话就永久
        // 不能再上滑加载更早历史 —— 用户看到的就是"只能看到最近一部分内容"。
        gw.requestHistory(currentSessionId, nextBeforeSeq, historyFormatVersion);
        // 超时兜底：响应丢了也必须复位在途标记，否则上滑永久失效（不是卡死，只是不给超时）。
        if (loadMoreTimeout != null) uiHandler.removeCallbacks(loadMoreTimeout);
        loadMoreTimeout = () -> {
            loadMoreTimeout = null;
            if (!loadingMore) return;
            loadingMore = false;
            loadMoreFailed = true;
            refreshMoreStatus();
        };
        uiHandler.postDelayed(loadMoreTimeout, LOAD_MORE_TIMEOUT_MS);
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
        Ui.dialog(this).setItems(opts, (d, which) -> {
            if ("重命名".equals(opts[which])) {
                for (SessionInfo s : sessions) {
                    if (s.id.equals(currentSessionId)) { onRename(s); return; }
                }
            } else if ("停止当前回合".equals(opts[which])) {
                onStopTurn();
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

    // ============================================================ DeviceHubView.Host（我的设备）

    /** 「连接/进入」：切到这台设备（不是当前生效的那台就先切过去）并连上，然后进对话页。 */
    @Override
    public void onOpenDevice(Store.Device d) {
        if (d == null) return;
        if (!d.pairedReady()) {
            Toast.makeText(this, "这台设备还没有地址或令牌，请重新扫码或手动添加",
                    Toast.LENGTH_LONG).show();
            refreshDevices();
            return;
        }
        if (!d.id.equals(store.activeDeviceId())) store.setActiveDevice(d.id);
        if (!isOnline()) gatewayReconnect();
        enterChat();
    }

    /** 「修改名称」：改的是本机这张卡片的显示名，电脑端不受影响。 */
    @Override
    public void onRenameDevice(final Store.Device d) {
        if (d == null) return;
        final EditText input = new EditText(Ui.dialogContext(this));   // 同上：必须用对话框主题 Context
        input.setSingleLine(true);
        input.setText(d.displayName());
        input.setSelection(input.getText().length());
        input.setHint("给这台电脑起个名字");
        int pad = Ui.dp(this, 18);
        input.setPadding(pad, Ui.dp(this, 10), pad, Ui.dp(this, 10));
        Ui.dialog(this)
                .setTitle("修改名称")
                .setView(input)
                .setPositiveButton("保存", (dlg, w) -> {
                    String v = input.getText().toString().trim();
                    if (v.isEmpty()) {
                        Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    store.renameDevice(d.id, v);
                    refreshDevices();
                    Toast.makeText(this, "已改名为「" + v + "」", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 「删除」= 清除这台设备的地址与令牌（电脑端不受影响，重新扫码即可再添加）。 */
    @Override
    public void onDeleteDevice(final Store.Device d) {
        if (d == null) return;
        Ui.dialog(this)
                .setTitle("删除「" + d.displayName() + "」")
                .setMessage("会清除这台设备在本机保存的地址与设备令牌。\n"
                        + "电脑端不受影响，之后重新扫码就能再添加。")
                .setPositiveButton("删除", (dlg, w) -> {
                    boolean wasActive = d.id.equals(store.activeDeviceId());
                    store.removeDevice(d.id);
                    if (wasActive) {
                        gw.disconnect();
                        // 还有别的设备就切过去并重连（删掉的是正在用的那台时）
                        Store.Device next = store.activeDevice();
                        if (next != null && next.pairedReady()) gatewayReconnect();
                    }
                    refreshDevices();
                    Toast.makeText(this, "已删除「" + d.displayName() + "」", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 「扫码添加」：复用现有扫码页（扫电脑面板的配对二维码）。 */
    @Override
    public void onScanAdd() {
        onScanQr();
    }

    /** 「手动添加」：输入地址 + 令牌（等价于设置页那几个字段，但走设备卡片这条正常流程）。 */
    @Override
    public void onManualAdd() {
        showManualAddDialog();
    }

    /** 右上角齿轮：原来的连接设置页（地址/令牌字段保留，作为高级入口）。 */
    @Override
    public void onOpenSettings() {
        showSettings();
    }

    /**
     * 手动添加设备：手搓底部圆角卡片（不用 AlertDialog 的系统默认外观，也不引入任何新依赖）。
     * 字段 = 小号灰标签 + {@link Ui#field} 的统一圆角输入框；主按钮「保存并连接」用品牌色实心，
     * 次按钮「取消」「扫码添加」用浅色描边——与设置页、设备卡片同一套样式。
     * 校验失败时把原因内联显示在字段下方（不再只用 Toast），没填全时保存按钮置灰。
     */
    private void showManualAddDialog() {
        final EditText lan = Ui.field(this, "ws://192.168.1.100:3091/ws/mobile");
        final EditText wan = Ui.field(this, "wss://你的域名/ws/mobile");
        final EditText tk = Ui.field(this, "电脑端配对后给出的那串");
        // 令牌是长期凭证：和设置页一样始终掩码，不摆在任何一张截屏里
        tk.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText alias = Ui.field(this, "给这台电脑起个名字（可留空）");

        final LinearLayout card = Ui.sheetCard(this);

        // 头部（小横条 + 标题 + 副标题）：高度不随字段区变化，用来算"还能留给字段区多少"
        final LinearLayout head = Ui.col(this);
        head.setLayoutParams(Ui.fill());
        android.view.View bar = new android.view.View(this);
        bar.setBackground(Ui.pill(Ui.LINE));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                Ui.dp(this, 40), Ui.dp(this, 4));
        blp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        blp.bottomMargin = Ui.dp(this, 12);
        bar.setLayoutParams(blp);
        head.addView(bar);

        head.addView(Ui.text(this, "手动添加设备", 17f, Ui.INK, true));
        TextView sub = Ui.text(this, "电脑端 DSH 打开「移动设备」面板，照着那边的信息填过来",
                12.5f, Ui.INK_SUB, false);
        sub.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 2));
        head.addView(sub);
        card.addView(head);

        // 字段区：统一「标签在上、输入框在下」的排版；错误提示内联在字段下方
        LinearLayout form = Ui.col(this);
        form.addView(Ui.fieldLabel(this, "内网地址"));
        form.addView(lan);

        form.addView(Ui.fieldLabel(this, "公网地址（可留空）"));
        form.addView(wan);
        form.addView(Ui.fieldHint(this, "在外面时用；隧道域名会变，失效后重新扫码更新。"));

        form.addView(Ui.fieldLabel(this, "设备令牌"));
        form.addView(tk);
        form.addView(Ui.fieldHint(this, "电脑端配对后给出；只存在这台手机上。"));

        form.addView(Ui.fieldLabel(this, "设备名称（可留空）"));
        form.addView(alias);

        final TextView err = Ui.text(this, "", 12f, Ui.ERR, false);
        err.setPadding(Ui.dp(this, 2), Ui.dp(this, 10), Ui.dp(this, 2), 0);
        err.setVisibility(android.view.View.GONE);
        form.addView(err);

        // 字段区可滚（限高），按钮区固定在卡片底部 → 键盘弹起也不会盖住「保存并连接」
        final FieldScroll scroller = new FieldScroll(this, Ui.dp(this, 460));
        scroller.setFillViewport(false);
        scroller.addView(form, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(this, 4);
        scroller.setLayoutParams(slp);
        card.addView(scroller);

        final android.app.Dialog dlg = new android.app.Dialog(this);

        // 底部（按钮区）：也不参与滚动，永远贴在卡片底部
        final LinearLayout foot = Ui.col(this);
        foot.setLayoutParams(Ui.fill());

        LinearLayout btns = Ui.row(this);
        btns.setPadding(0, Ui.dp(this, 14), 0, 0);
        TextView cancel = Ui.secondaryButton(this, "取消");
        cancel.setOnClickListener(v -> dlg.dismiss());
        btns.addView(cancel, manualWeight(1f, 0));
        final TextView save = Ui.primaryButton(this, "保存并连接");
        btns.addView(save, manualWeight(1.4f, 10));
        foot.addView(btns);

        TextView scan = Ui.secondaryButton(this, "扫码添加（电脑端有二维码时更快）");
        scan.setLayoutParams(manualFull(10));
        scan.setOnClickListener(v -> {
            dlg.dismiss();
            onScanAdd();
        });
        foot.addView(scan);
        card.addView(foot);

        // 按钮区高度固定，量一次就够：把它从"字段区可用高度"里扣掉，
        // 输入法把窗口改矮时 FieldScroll 会在 onMeasure 里自己重算（不依赖布局回调）。
        final Runnable reserve = () -> scroller.setReserved(
                foot.getHeight() + card.getPaddingTop() + card.getPaddingBottom() + Ui.dp(this, 6));
        foot.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> reserve.run());
        foot.post(reserve);

        // 置灰：内网/公网都空，或令牌没填 → 「保存并连接」不可点
        final Runnable sync = () -> {
            boolean ok = !(lan.getText().toString().trim().isEmpty()
                    && wan.getText().toString().trim().isEmpty())
                    && !tk.getText().toString().trim().isEmpty();
            Ui.setButtonEnabled(save, ok);
        };
        android.text.TextWatcher watch = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                err.setVisibility(android.view.View.GONE);   // 一改就撤掉上一次的内联错误
                sync.run();
            }
        };
        lan.addTextChangedListener(watch);
        wan.addTextChangedListener(watch);
        tk.addTextChangedListener(watch);
        sync.run();

        save.setOnClickListener(v -> {
            String l = lan.getText().toString().trim();
            String w = wan.getText().toString().trim();
            String t = tk.getText().toString().trim();
            if (l.isEmpty() && w.isEmpty()) {
                inlineError(err, "请先填内网地址或公网地址——两个都空就连不上电脑。");
                return;
            }
            if (t.isEmpty()) {
                inlineError(err, "还差设备令牌：电脑端配对后会给出一串。");
                return;
            }
            String problem = GatewayClient.cleartextProblem(l.isEmpty() ? w : l);
            if (problem != null) {
                inlineError(err, problem);
                return;
            }
            dlg.dismiss();
            addManualDevice(l, w, t, alias.getText().toString());
        });

        android.widget.FrameLayout host = new android.widget.FrameLayout(this);
        host.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 12));
        host.addView(card, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM));
        // 窗口占满屏：卡片贴底。刻意不做"点卡片外面就关掉"——真机上手抖点到空白处
        // 会把已经填了一半的地址/令牌全丢掉；关闭只走「取消」按钮和返回键。
        card.setClickable(true);

        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dlg.setContentView(host);
        dlg.setCanceledOnTouchOutside(false);
        android.view.Window win = dlg.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
            win.setGravity(android.view.Gravity.BOTTOM);
            // 窗口高度必须占满可用区：这样键盘压矮窗口时才能算准"还剩多少给字段区"，
            // 否则 wrap_content 窗口会随卡片长高而"越长越高"，把底部按钮顶出屏幕
            win.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            // 同「粘贴配对串」：输入法弹起时缩内容，而不是把按钮盖在下面
            win.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
            // 这个窗口里会出现设备令牌：按当前的「允许截屏」策略决定要不要设 FLAG_SECURE
            // （默认允许截屏 → 不设；用户关掉开关 → 这个弹窗的截图同样变黑）
            Ui.applyScreenshotPolicy(win);
            win.setDimAmount(0.35f);
            win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dlg.show();
    }

    /**
     * 字段滚动区：高度上限 = 「父容器这次给的可用高度 − 底部按钮区高度」。
     * 输入法把窗口改矮时，父容器给的可用高度会跟着变小，而 onMeasure 每次布局都会重跑，
     * 所以「保存并连接」永远不会被键盘顶出屏幕——比挂布局回调可靠（真机上回调未必来）。
     * 内容少时按内容高，最多长到 maxPx，卡片仍然是一张浮起来的小卡片而不是整屏。
     */
    private static final class FieldScroll extends android.widget.ScrollView {
        private final int maxPx;
        private int reservedPx;

        FieldScroll(android.content.Context c, int maxPx) {
            super(c);
            this.maxPx = maxPx;
        }

        void setReserved(int px) {
            if (px != reservedPx) {
                reservedPx = px;
                requestLayout();
            }
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int avail = android.view.View.MeasureSpec.getSize(heightSpec) - reservedPx;
            int cap = Math.min(maxPx, Math.max(0, avail));
            super.onMeasure(widthSpec, android.view.View.MeasureSpec.makeMeasureSpec(
                    cap, android.view.View.MeasureSpec.AT_MOST));
        }
    }

    /** 内联错误：显示在字段下方（不再只用 Toast，用户一眼能看出是哪一栏的问题）。 */
    private void inlineError(TextView err, String msg) {
        err.setText(msg);
        err.setVisibility(android.view.View.VISIBLE);
    }

    private LinearLayout.LayoutParams manualWeight(float w, int marginStartDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
        lp.leftMargin = Ui.dp(this, marginStartDp);
        return lp;
    }

    private LinearLayout.LayoutParams manualFull(int marginTopDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, marginTopDp);
        return lp;
    }

    /** 手动添加落地：新建一张设备卡片、设为当前生效并连接。 */
    private void addManualDevice(String lan, String wan, String token, String alias) {
        Store.Device d = new Store.Device();
        d.id = store.newDeviceId();
        d.lanUrl = lan;
        d.wanUrl = wan;
        d.useWan = lan.isEmpty() && !wan.isEmpty();
        d.token = token;
        d.name = alias == null ? "" : alias.trim();
        store.upsertDevice(d, true);   // 顺带把地址/令牌镜像进旧字段
        Toast.makeText(this, "已添加：" + d.displayName(), Toast.LENGTH_SHORT).show();
        gatewayReconnect();
        refreshDevices();
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
        showChat();
    }

    @Override
    public void onOpenFeedback() {
        final android.widget.EditText ed = new android.widget.EditText(Ui.dialogContext(this));
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

        android.app.AlertDialog.Builder b = Ui.dialog(this)
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

    /**
     * 反馈 webhook 的主机白名单（安全评审 P1-17）。
     *
     * webhook 地址来自**远端未签名的清单**（仓库 dist/version.json 的 feedback 段，
     * 经 cdn.jsdelivr.net 或 raw.githubusercontent.com 拉取后存进本地偏好）。
     * 清单或 CDN 任一处被篡改，App 就会在用户点「发送」时把反馈正文连同环境信息
     * （版本 / 连接方式 / 网关地址 gw.debugState() / store.url()）POST 到攻击者服务器。
     *
     * 这里只放行已知反馈服务商的 https 主机；白名单外一律忽略，自动退回
     * 「GitHub Issue / 邮件」通道 —— 那条路要用户自己看着页面确认提交，不会被静默外发。
     * 作者更换服务商时在这里补一个域名即可。
     */
    private static final String[] FEEDBACK_WEBHOOK_HOSTS = {
            "formsubmit.co", "formspree.io", "getform.io", "web3forms.com",
            "sctapi.ftqq.com", "pushplus.plus",
            "qyapi.weixin.qq.com", "oapi.dingtalk.com", "open.feishu.cn",
            "hooks.slack.com", "discord.com", "api.telegram.org",
    };

    /** 白名单内返回原地址，否则返回空串（调用方据此退回其它通道）。 */
    private static String allowedFeedbackWebhook(String webhook) {
        if (webhook == null || webhook.trim().isEmpty()) return "";
        String w = webhook.trim();
        try {
            java.net.URL u = new java.net.URL(w);
            if (!"https".equalsIgnoreCase(u.getProtocol())) return "";
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(java.util.Locale.ROOT);
            if (host.isEmpty()) return "";
            for (String allowed : FEEDBACK_WEBHOOK_HOSTS) {
                if (host.equals(allowed) || host.endsWith("." + allowed)) return w;
            }
        } catch (Throwable ignored) { /* 解析不了就按不允许处理 */ }
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
        // 远端清单给的 webhook 必须过白名单（见 allowedFeedbackWebhook）；不在白名单里
        // 就当没配，退回下面的 Issue/邮件通道，避免被篡改的清单把反馈外发到任意主机。
        String webhook = fb == null ? "" : allowedFeedbackWebhook(fb.optString("webhook", "").trim());
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
        final android.widget.CheckBox cb = new android.widget.CheckBox(Ui.dialogContext(this));
        cb.setText("我已知情，同意开启");
        cb.setTextSize(14f);
        cb.setPadding(0, Ui.dp(this, 14), 0, 0);
        box.addView(cb);

        final android.app.AlertDialog dlg = Ui.dialog(this)
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
        // 分档：① 和手机同网段（在家必通、且最快）② 其它可用内网（10.x 等）
        //      ③ 公网隧道（人在外面时用）④ 「假内网」地址（虚拟网卡 172.16/12、APIPA…）
        // 之前不分档、也不过滤，先撞虚拟网卡要白等十几秒超时；④ 排在最后只当兜底。
        final String subnet = localSubnetPrefix();
        java.util.List<String> sameNet = new java.util.ArrayList<>();
        java.util.List<String> priv = new java.util.ArrayList<>();
        java.util.List<String> pub = new java.util.ArrayList<>();
        java.util.List<String> bogus = new java.util.ArrayList<>();
        for (String u : all) {
            if (isLoopbackUrl(u)) continue;
            if (LanAddress.isUsableLanUrl(u)) {
                // 「私有网段」≠「手机连得上」：172.30.x 也是私网，但那是电脑上的虚拟网卡
                String host = hostOf(u);
                if (!subnet.isEmpty() && host.startsWith(subnet)) sameNet.add(u);
                else priv.add(u);
            } else if (Store.isPrivateUrl(u)) {
                bogus.add(u);   // 虚拟网卡 / APIPA / CGNAT / 回环：手机路由不过去
            } else {
                pub.add(u);
            }
        }
        sameNet.addAll(priv);
        sameNet.addAll(pub);
        sameNet.addAll(bogus);
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
        int total = pairCandidates.size();
        int skipped = 0;
        while (pairIndex < total) {
            String target = pairCandidates.get(pairIndex++);
            String denied = GatewayClient.cleartextProblem(target);
            if (denied != null) {
                skipped++;
                pairTraceAdd("· 跳过 " + hostOf(target) + "（安全策略：" + denied + "）");
                continue;
            }
            // 虚拟网卡这类「假内网」地址只拿来试连，**不**写进设备的内网地址槽位：
            // 否则试完失败后卡片上会一直挂着「内网 · 固定」，用户以为有内网其实永远连不上。
            if (LanAddress.isUsableLanUrl(target) || !Store.isPrivateUrl(target)) store.setUrl(target);
            pairTraceAdd("· 尝试 " + pairIndex + "/" + total + "：" + hostOf(target));
            if (settingsView != null) {
                settingsView.setStatus("正在配对 " + hostOf(target)
                        + "（第 " + pairIndex + "/" + pairCandidates.size() + " 个地址）…", false);
            }
            // 配对可能要依次试几个地址，给个可见反馈，别让人以为点了没反应
            Toast.makeText(this, "正在连接 " + hostOf(target)
                    + "（第 " + pairIndex + "/" + pairCandidates.size() + " 个）", Toast.LENGTH_SHORT).show();
            gw.pair(target, pendingPairCode, store.deviceId(), store.deviceName());
            refreshDiagnostics();
            return;
        }
        pendingPairCode = null;
        // 走到这里说明每个地址都被拒/试完：必须给出可见且可执行的结论，不能静默返回
        pairFail("配对失败：配对码里的 " + total + " 个地址都连不上"
                + (skipped > 0 ? "（其中 " + skipped + " 个被安全策略拒绝）" : "") + "。\n"
                + "请确认手机和电脑在同一 WiFi、或手机能上网；\n"
                + "也可以在电脑面板重新生成二维码后重扫。");
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

        Ui.dialog(this)
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

    /**
     * 「允许截屏」开关：写盘 + 立即重设窗口标志（不重启 App 就生效）。
     *
     * 关掉时本 App 的截图/录屏会变黑、最近任务缩略图也会变黑；
     * 打开时恢复可截图。令牌仍然只显示末 4 位，与这个开关无关。
     */
    @Override
    public void onToggleAllowScreenshot(boolean on) {
        store.setAllowScreenshot(on);
        applyScreenshotPolicy();
        if (settingsView != null) settingsView.setAllowScreenshot(on);
        Toast.makeText(this, on ? "已允许截屏" : "已禁止截屏：本 App 内截图/录屏会变黑",
                Toast.LENGTH_SHORT).show();
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

    /**
     * 「粘贴配对串」。
     *
     * 真机实测（2026-10-02，某国产 ROM / 1312x2848 / 手势导航 + 中文输入法）：
     * 旧实现在对话框里放一个多行 EditText，把 550+ 字符的配对串粘进去后输入框会长到
     * ~1300px，把「配对」按钮顶到 y=1653..1842；而输入法窗口的可触区从 y=1716 开始 ——
     * 按钮**中心点正好落在键盘上**，点它等于点键盘：对话框不关、不报错、没有 Toast、
     * 设备列表也不动，表现就是用户说的「点了毫无反应」。
     *
     * 治本做法是**根本不要手输**：配对串本来就该是复制粘贴的，
     * 所以这里先读系统剪贴板 —— 读到就直接用一个没有输入框的确认框（不会弹输入法，
     * 按钮永远点得到）。读不到才走手输，且手输框做了三重保险（限高 + 置顶 + 输入法弹起时缩内容）。
     */
    @Override
    public void onPastePairing() {
        String clip = clipboardPairingCandidate();
        if (!clip.isEmpty()) {
            showPairConfirmDialog(clip);
            return;
        }
        showPairInputDialog("");
    }

    /**
     * 剪贴板里那段文本是不是一段"看起来就是配对串"的内容。
     *
     * 只认「够长、且不是 http 链接」的内容：宁可退回手输框，也不要拿剪贴板里
     * 不相干的文本去配对（那只会得到一句莫名其妙的失败原因）。
     */
    private String clipboardPairingCandidate() {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return "";
            android.content.ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() <= 0) return "";
            CharSequence cs = clip.getItemAt(0).coerceToText(this);
            if (cs == null) return "";
            String s = PairingText.sanitize(cs.toString());
            if (s.length() < 100 || PairingText.looksLikeHttpUrl(s)) return "";
            return s;
        } catch (Throwable t) {
            return "";
        }
    }

    /** 确认框：没有输入框 -> 不弹输入法 -> 「配对」按钮永远在键盘上方点得到。 */
    private void showPairConfirmDialog(final String text) {
        final android.app.AlertDialog dlg = Ui.dialog(this)
                .setTitle("用剪贴板里的配对串配对")
                .setMessage("读到 " + text.length() + " 个字符的配对串。\n"
                        + PairingText.describe(text) + "\n\n"
                        + "点「配对」后这台手机会连上电脑；不想要这段内容就点「手动输入」。")
                .setPositiveButton("配对", null)
                .setNeutralButton("手动输入", null)
                .setNegativeButton("取消", null)
                .create();
        dlg.setOnShowListener(x -> {
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> { dlg.dismiss(); startPairing(text); });
            dlg.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(v -> { dlg.dismiss(); showPairInputDialog(""); });
        });
        dlg.show();
    }

    /**
     * 手输/粘贴框。
     *
     * 三重保险，保证「配对」按钮不会被输入法盖住（旧实现的 bug 就在这里）：
     *   ① 输入框高度封顶（4 行 + 外层 ScrollView 限高）：配对串再长也不撑大对话框；
     *   ② 对话框整体贴屏幕顶部（gravity=TOP）：按钮区远离底部键盘；
     *   ③ window 用 ADJUST_RESIZE：输入法弹起时缩内容而不是压住。
     * 另外「配对」的回调自己接管：解析失败时**不关对话框、不清输入**，把原因写在框里。
     */
    private void showPairInputDialog(String initial) {
        final android.widget.EditText input = new android.widget.EditText(Ui.dialogContext(this));
        input.setHint("粘贴电脑端生成的配对串（Base64URL）");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMaxLines(4);
        input.setText(initial == null ? "" : initial);
        input.setSelection(input.getText().length());

        android.widget.ScrollView scroller = new android.widget.ScrollView(this);
        scroller.addView(input, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        scroller.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 92)));

        final android.widget.TextView msg = Ui.text(this, "", 12.5f, Ui.INK_SUB, false);
        msg.setPadding(0, Ui.dp(this, 8), 0, 0);

        android.widget.LinearLayout box = Ui.col(this);
        int pad = (int) Ui.dp(this, 20);
        box.setPadding(pad, (int) Ui.dp(this, 6), pad, 0);
        box.addView(scroller);
        box.addView(msg);

        final String clipHint = clipboardPairingCandidate().isEmpty()
                ? "剪贴板里现在没有配对串" : "剪贴板里有配对串，点「读剪贴板」直接填进来";
        msg.setText("配对串有 550 个字符左右，推荐在电脑面板点「复制配对串」后回来点「读剪贴板」。\n"
                + clipHint);

        final android.app.AlertDialog dlg = Ui.dialog(this)
                .setTitle("粘贴配对串")
                .setView(box)
                .setPositiveButton("配对", null)
                .setNeutralButton("读剪贴板", null)
                .setNegativeButton("取消", null)
                .create();
        dlg.setOnShowListener(x -> {
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String problem = startPairing(input.getText().toString());
                if (problem == null) {
                    dlg.dismiss();      // 解析通过、配对已启动
                } else {
                    msg.setTextColor(Ui.ERR);
                    msg.setText(problem);   // 失败：留在原界面，输入一个字都不丢
                }
            });
            dlg.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String clip = clipboardPairingCandidate();
                if (clip.isEmpty()) {
                    msg.setTextColor(Ui.ERR);
                    msg.setText("剪贴板里没有可用的配对串。请在电脑面板点「复制配对串」再来点这里。");
                    return;
                }
                input.setText(clip);
                input.setSelection(clip.length());
                msg.setTextColor(Ui.OK);
                msg.setText("已从剪贴板读入 " + clip.length() + " 个字符，点「配对」继续。");
            });
        });
        dlg.show();
        // ② 贴顶：按钮区远离底部键盘（旧实现的按钮 y=1653..1842，键盘可触区从 1716 起）
        android.view.Window w = dlg.getWindow();
        if (w != null) {
            // ③ 输入法弹起时缩内容，而不是把它盖在按钮上
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
            lp.y = (int) Ui.dp(this, 56);
            w.setAttributes(lp);
        }
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
        // 扫码页里点「粘贴/手输」：相机这条路走不通时，给它一条一定走得通的路
        if (request == REQ_QR && result == RESULT_FIRST_USER) {
            onPastePairing();
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

    /**
     * 扫码 / 粘贴的统一入口。
     *
     * 分三段，每段的失败必须给出**不同**的原因（旧写法把整条流程塞进一个 try，
     * 于是"连接层抛的任何异常"都会被报成「不是可用的配对码」—— 用户拿着正确的
     * 配对串也会以为码不对，完全无从下手，这就是"我感觉一直不行"的来源）：
     *   ① 预处理 + 解码 —— 只有这一段失败才是"内容不是配对码"；
     *   ② 字段与地址提取 —— 版本不支持 / 已过期 / 缺字段 / 地址连不上；
     *   ③ 启动配对 —— 网络与状态错误，与配对串内容无关。
     *
     * @return null 表示解析通过、配对已启动；否则是给用户看的中文原因
     *         （调用方决定显示在哪：对话框内联 + Toast 都已经在 {@link #pairFail} 里做了）
     */
    private String startPairing(String raw) {
        pairTrace.clear();
        final String input = raw == null ? "" : raw;
        pairTraceAdd("① 收到 " + input.length() + " 字符");
        final String scanned = PairingText.sanitize(input);
        pairTraceAdd("② 预处理后 " + scanned.length() + " 字符（清掉 "
                + Math.max(0, input.length() - scanned.length()) + " 个空白/不可见字符或外层包裹）");
        // 问题 2-d：把"到底扫到了什么"记进设置页诊断区（脱敏，见 PairingText.describe）
        lastPairDebug = PairingText.describe(scanned);
        refreshDiagnostics();
        if (scanned.isEmpty()) {
            return pairFail("配对串是空的。请重新扫一次，或把电脑面板上的配对串复制后粘贴进来。");
        }
        // 问题 2-b：面板里有两张二维码 —— ① 安装包下载链接 ② 配对码。
        // 扫到 ① 时绝不能笼统报"配对失败"，要直接说清它是什么、该扫哪一张。
        if (PairingText.looksLikeHttpUrl(scanned)) {
            return pairFail("这是「安装包下载链接」，不是配对码。\n"
                    + "请用手机浏览器打开它下载安装 App；\n"
                    + "配对请扫电脑面板里「生成配对二维码」那一张。");
        }

        // ---- ① 解码（只有这里失败才叫"不是可用的配对码"）
        final JSONObject payload;
        try {
            payload = PairingText.decode(scanned);
        } catch (Throwable t) {
            String why = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            pairTraceAdd("✗ 解码失败：" + why);
            return pairFail("这段内容不是可用的配对码（解码失败：" + why + "）。\n"
                    + "请确认扫的是电脑面板里「生成配对二维码」那一张；\n"
                    + "微信/相册里的截图二维码也常被压缩到扫错，可以用「粘贴配对串」代替。");
        }
        pairTraceAdd("③ 解码成功（Base64URL/JSON 已还原）");

        // ---- ② 字段与地址
        final int version = payload.optInt("version", 0);
        final long expires = payload.optLong("expiresAt", 0L);
        final String url = payload.optString("publicUrl", "");
        final String code = payload.optString("pairingCode", "");
        final JSONArray eps = payload.optJSONArray("endpoints");
        final boolean expired = expires > 0 && System.currentTimeMillis() > expires;
        pairTraceAdd("④ 字段：version=" + version
                + " · publicUrl=" + (url.isEmpty() ? "无" : "有")
                + " · pairingCode=" + (code.isEmpty() ? "无（只记有无，不记内容）" : "有（只记有无，不记内容）")
                + " · endpoints=" + (eps == null ? 0 : eps.length()) + " 个"
                + " · " + (expires > 0 ? (expired ? "已过期" : "未过期") : "无有效期"));
        if (version != 2) {
            return pairFail("配对信息版本不支持：" + version + "。请在电脑端把「移动设备」插件更新到最新版。");
        }
        if (expired) {
            return pairFail("配对码已过期，请在电脑端重新生成二维码后重扫。");
        }
        if (url.isEmpty() || code.isEmpty()) {
            return pairFail("配对信息不完整（缺少连接地址或配对码）。请在电脑端重新生成二维码。");
        }
        // 网关给的 publicUrl 可能只对电脑本机有效（例如 ws://127.0.0.1:19387/…），
        // 真正可用的是 payload.endpoints —— 它带着隧道 / 局域网等全部地址。
        // 这里排成候选列表逐个试：私有网段优先（在家最快），不行再走公网。
        pairCandidates = buildPairCandidates(payload, url);
        if (pairCandidates.isEmpty()) {
            pairTraceAdd("✗ 候选地址 0 个（publicUrl 与 endpoints 都是 127.0.0.1/localhost）");
            // 问题 2-c：说清"扫到的确实是配对码，问题出在里面的地址"。
            return pairFail("扫到了配对码，但里面的地址手机连不上"
                    + "（只有 127.0.0.1 / localhost 这类电脑本机地址）。\n"
                    + "请在电脑面板重新点「生成配对二维码」，"
                    + "并把「配对连接方式」选成「自动选择 · 优先外网」；\n"
                    + "或者在 App 设置里手动填「公网地址」。");
        }
        StringBuilder hosts = new StringBuilder();
        for (int i = 0; i < pairCandidates.size(); i++) {
            if (i > 0) hosts.append(" → ");
            hosts.append(hostOf(pairCandidates.get(i)));
        }
        pairTraceAdd("⑤ 候选地址 " + pairCandidates.size() + " 个：" + hosts);

        // ---- ③ 编排：这里的异常与"配对串内容"无关，绝不能报成"不是可用的配对码"
        try {
            // 先决定这次配对写进哪台设备（同一台电脑重扫 = 更新它的地址，不会多出重复卡片），
            // 再写地址/令牌，后续 setUrl/setLanUrl/setToken 才会落到这台设备上。
            store.beginPairing(payload.optString("gatewayId", ""), payload.optString("gatewayName", ""));
            store.setUrl(pairCandidates.get(0));
            // 网关把可用地址都放在 endpoints：可用内网进「内网」，公网/隧道进「公网」。
            // 扫一次码就把两个地址都填好，不用手输（隧道域名每次重启会变，重扫即可）。
            //
            // ⚠️ 这里必须用 LanAddress.isUsableLanUrl()（可用性判据），**不能**用
            // Store.isPrivateUrl()（地址性质判据）：endpoints 里同时含电脑上虚拟网卡的
            // 172.30.x 地址与真实内网地址，只按「私网」挑就会把虚拟网卡地址存成内网地址，
            // 手机永远连不上（真机实测：lanUrl 存成了 ws://172.30.x.x:3091/…）。
            String lan = "", wan = "";
            if (eps != null) {
                for (int i = 0; i < eps.length(); i++) {
                    String e = eps.optString(i, "").trim();
                    if (e.isEmpty()) continue;
                    if (LanAddress.isUsableLanUrl(e)) { if (lan.isEmpty()) lan = e; }
                    else if (!Store.isPrivateUrl(e) && wan.isEmpty()) wan = e;
                    // 其余（虚拟网卡 / APIPA / 回环的私网地址）两边都不进：不拿假地址当内网
                }
            }
            if (LanAddress.isUsableLanUrl(url)) { if (lan.isEmpty()) lan = url; }
            else if (!Store.isPrivateUrl(url) && wan.isEmpty()) wan = url;
            if (!lan.isEmpty()) {
                store.setLanUrl(lan);
            } else if (!LanAddress.isUsableLanUrl(store.lanUrl())) {
                // 这次配对没有可用内网地址（或刚被 setUrl(candidate) 写进了虚拟网卡地址）：
                // 清空内网槽位 —— 界面上不显示「内网 · 固定」，也不拿假地址去连
                store.setLanUrl("");
                pairTraceAdd("· 没有可用内网地址（虚拟网卡/APIPA/回环已排除），内网槽位留空");
            }
            if (!wan.isEmpty()) store.setWanUrl(wan);
            store.setUseWan(store.lanUrl().isEmpty() && !wan.isEmpty());
            failoverUsed = false;
            pendingPairCode = code;
            pairIndex = 0;
            pairTraceAdd("⑥ 内网=" + (store.lanUrl().isEmpty() ? "无" : hostOf(store.lanUrl()))
                    + " · 公网=" + (wan.isEmpty() ? "无" : hostOf(wan))
                    + " · 先走" + (store.useWan() ? "公网" : "内网"));
            refreshDiagnostics();
            pairWithNextCandidate();
            refreshDiagnostics();
            return null;
        } catch (Throwable t) {
            String why = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            pairTraceAdd("✗ 启动配对时出错：" + why);
            pendingPairCode = null;
            return pairFail("配对串本身没问题，但启动配对时出错了：" + why
                    + "\n请再点一次；若一直这样，把设置页底部的诊断信息发给作者。");
        }
    }

    // ============================================================ 配对串健壮化 / 诊断
    // 解析规则本身在 com.dsh.mobile.PairingText 里（纯字符串处理，可脱离真机跑 JVM 断言：
    // harness/src/PairingTextTest.java）。这里只留诊断与统一的失败出口。

    /** 记一行配对轨迹（脱敏；调用后刷新设置页诊断区）。 */
    private void pairTraceAdd(String line) {
        pairTrace.add(line);
        if (pairTrace.size() > 40) pairTrace.remove(0);
    }

    /**
     * 配对失败的**唯一**出口：Toast + 设置页状态 + 诊断轨迹，一处都不能少。
     *
     * 为什么要有这个函数：旧代码里失败提示散落在各分支，稍有遗漏就是"点了没反应"。
     * 现在所有失败路径都必须经过这里，保证「任何失败都有可见反馈」。
     */
    private String pairFail(String reason) {
        String flat = reason == null ? "" : reason.replace('\n', ' ');
        pairTraceAdd("✗ " + flat);
        Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
        if (settingsView != null) settingsView.setStatus(flat, true);
        refreshDiagnostics();
        return reason;
    }

    /** 刷新设置页诊断区（状态 / 轨迹 / 最近一次扫码特征 / 本次配对每一步 / 会话流中断）。 */
    private void refreshDiagnostics() {
        if (settingsView == null) return;
        StringBuilder sb = new StringBuilder();
        if (gw != null) sb.append(gw.debugState()).append('\n').append(gw.traceText());
        if (!lastPairDebug.isEmpty()) sb.append("\n\n[扫码诊断] ").append(lastPairDebug);
        if (!pairTrace.isEmpty()) {
            sb.append("\n\n[配对诊断] 本次配对的每一步（不含配对码/令牌明文）：");
            for (String line : pairTrace) sb.append('\n').append("  ").append(line);
        }
        if (!streamResetLog.isEmpty()) {
            // 目的：下次再遇到「一直在抖」时，一眼看出是偶发（零散几条）还是持续（同一 code 连续刷屏）。
            // 只记 时间 / code / retrying，不含内容明文（最新在上）。
            sb.append("\n\n[会话流中断] 最近 ").append(streamResetLog.size())
                    .append(" 次（时间 / code / retrying，不含内容明文）：");
            java.util.Iterator<String> it = streamResetLog.descendingIterator();
            while (it.hasNext()) sb.append('\n').append("  ").append(it.next());
        }
        settingsView.setDiagnostics(sb.toString());
    }
}
