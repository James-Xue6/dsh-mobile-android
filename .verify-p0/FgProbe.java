import com.dsh.mobile.net.GatewayClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * P0-2 离线验证探针：切回前台时的存活检查到底多久能恢复连接。
 *
 * <p>场景复刻：手机切后台 → 系统把 socket 掐死（对端不再有任何回包）→ 用户切回 App。
 * 模拟网关用 {@code --fault silent}（发完 hello 后完全静默，不回 pong），
 * 于是 App 侧 {@code lastInboundAt} 停在 hello 那一刻 —— 正是"界面写着已连接、其实已死"。
 *
 * <p>探针在 READY 之后 3s 调一次 {@link GatewayClient#foregroundLivenessCheck()}（用反射调，
 * 这样同一份探针也能编到旧版源码上做对照：旧版没有这个方法，反射会抛 NoSuchMethodException，
 * 等于"切回前台什么都不做"）。然后看模拟网关日志里第二条 upgrade 的时间戳。
 *
 * <p>用法: java -Dprobe.maxMs=30000 -cp <out>;<json.jar> FgProbe <pairing.txt>
 */
public final class FgProbe implements GatewayClient.Listener {

    private static GatewayClient gw;
    private static volatile boolean readyOnce = false;
    private static final long START = System.currentTimeMillis();

    private static void log(String s) {
        System.out.println("[+" + (System.currentTimeMillis() - START) + "ms wall="
                + System.currentTimeMillis() + "] " + s);
    }

    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.US_ASCII).trim();
        JSONObject p = new JSONObject(new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8));
        long maxMs = Long.getLong("probe.maxMs", 30_000L);

        Thread wd = new Thread(() -> {
            try { Thread.sleep(maxMs); } catch (InterruptedException ignored) { }
            log("PROBE-END");
            System.exit(0);
        });
        wd.setDaemon(false);
        wd.start();

        gw = new GatewayClient(new FgProbe());
        log("PROBE-START url=" + p.getString("publicUrl"));
        gw.pair(p.getString("publicUrl"), p.getString("pairingCode"), "jvm-fg-probe", "FG Probe");
    }

    @Override
    public void onState(GatewayClient.State st, String detail) {
        log("state=" + st + " · " + detail);
        if (st == GatewayClient.State.READY && !readyOnce) {
            readyOnce = true;
            // linkDead() 是 P0-1「死 / 慢」分界的判据，这里直接把它在不同时刻的取值打出来：
            //   t=1s  链路新鲜        → linkDead(30s) 应为 false
            //   t=35s 静默 35s（>30s）→ linkDead(30s) 应为 true、linkDead(60s) 应为 false（证明阈值真的在起作用）
            //   t=46s 客户端已判死、state 非 READY → linkDead 应为 true
            Thread ld = new Thread(() -> {
                long[] marks = { 1000L, 35000L, 46000L };
                for (long m : marks) {
                    long wait = m - (System.currentTimeMillis() - START);
                    if (wait > 0) { try { Thread.sleep(wait); } catch (InterruptedException ignored) { return; } }
                    log("LINKDED t=" + m + "ms state=" + gw.state()
                            + " lastInboundAgoMs=" + gw.lastInboundAgoMs()
                            + " linkDead(30s)=" + gw.linkDead(30_000L)
                            + " linkDead(60s)=" + gw.linkDead(60_000L));
                }
            }, "link-dead");
            ld.setDaemon(true);
            ld.start();
            Thread t = new Thread(() -> {
                try { Thread.sleep(3000); } catch (InterruptedException ignored) { }
                long callAt = System.currentTimeMillis();
                // -Dprobe.skipFg=true：模拟"改前"——切回前台什么都不做，只靠 25s 一个周期的
                // pingRunnable 去撞 75s 的假连接判定。用同一份二进制做对照，排除编译差异。
                if (Boolean.getBoolean("probe.skipFg")) {
                    log("FG-CHECK callAt=" + callAt + " skipped=true（模拟改前：切回前台不做任何存活检查）");
                    return;
                }
                try {
                    Method m = GatewayClient.class.getMethod("foregroundLivenessCheck");
                    Object r = m.invoke(gw);
                    log("FG-CHECK callAt=" + callAt + " invoked=true returned=" + r);
                } catch (NoSuchMethodException e) {
                    log("FG-CHECK callAt=" + callAt + " invoked=false 旧版没有 foregroundLivenessCheck（切回前台什么都不做）");
                } catch (Throwable t2) {
                    log("FG-CHECK callAt=" + callAt + " error=" + t2);
                }
            }, "fg-check");
            t.setDaemon(true);
            t.start();
        }
    }

    @Override public void onHello(JSONObject hello) { log("hello caps=" + hello.optJSONArray("capabilities")); }
    @Override public void onPaired(JSONObject paired) { log("paired"); }
    @Override public void onSessions(JSONArray items, JSONObject raw) { }
    @Override public void onHistory(String sessionId, JSONArray events, JSONObject meta) { }
    @Override public void onSnapshot(String sessionId, JSONObject snapshot) { }
    @Override public void onAssistantStream(JSONObject frame) { }
    @Override public void onEvent(String sessionId, JSONObject event, Object seq, Object time) { }
    @Override public void onApprovalRequested(JSONObject frame) { }
    @Override public void onQuestionRequested(JSONObject frame) { }
    @Override public void onInteractionResolved(JSONObject frame) { }
    @Override public void onSent(String sessionId, JSONObject raw) { }
    @Override public void onProtocolError(String code, String message, String requestType, String sessionId) { }
    @Override public void onOther(String kind, JSONObject frame) { }
    @Override public void onAttachment(String sessionId, String attachmentId, String mediaType, String base64) { }
    @Override public void onDownload(String kind, JSONObject frame) { }
}
