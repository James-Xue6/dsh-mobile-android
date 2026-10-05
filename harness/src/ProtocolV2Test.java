import com.dsh.mobile.net.GatewayClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 2026-10-05 四项优化的**协议层端到端验证**（主理人写）。
 *
 * <p>为什么要有它：本轮新增了 4 个请求方法（requestFileList / requestPermissionOptions /
 * selectPermission / sendMessageWithImages），它们只在 App 里被调用，而模拟器在本机
 * DSH 会话内起不来（见 NEXT-UPDATE「本轮环境限制」）⇒ UI 无法自动取证。
 * 但**协议层可以用真实 App 源码在 JVM 上直接跑**（harness 既有能力），
 * 于是把"App 会不会把帧发对、回帧能不能解析"这件事**真跑一遍**。
 *
 * <p>用法：
 * <pre>
 *   javac -encoding UTF-8 -cp harness/lib/json-20240303.jar -d out \
 *       harness/shim/android/os/Handler.java harness/shim/android/os/Looper.java \
 *       harness/shim/android/util/Base64.java src/com/dsh/mobile/net/WsClient.java \
 *       src/com/dsh/mobile/net/GatewayClient.java harness/src/ProtocolV2Test.java
 *   java -cp "out;harness/lib/json-20240303.jar" ProtocolV2Test &lt;qrPayload.txt&gt;
 * </pre>
 *
 * <p>只读为主：权限只**查询**不写入；sendMessageWithImages 故意发到**不存在的会话**，
 * 用它来证明"帧能被网关的 schema 接受"（若字段名/类型写错，网关会在解析前就拒），
 * 且**不会创建会话、不会触发模型**。全部结论打到 stdout。
 */
public final class ProtocolV2Test implements GatewayClient.Listener {

    private static final long START = System.currentTimeMillis();
    private static GatewayClient gw;
    private static String token = "";
    private static final String DEVICE_ID = "jvm-proto-v2-0001";
    private static volatile boolean reconnected = false;
    private static volatile boolean phase3 = false;
    private static int fails = 0;
    /** 已收到的结果数（B/C/D 三条），集齐才收尾 —— 否则先到的错误会提前 exit。 */
    private static final java.util.concurrent.atomic.AtomicInteger got =
            new java.util.concurrent.atomic.AtomicInteger();

    private static void done() {
        if (got.incrementAndGet() >= 3) finish();
    }

    private static void log(String s) {
        System.out.println("[" + (System.currentTimeMillis() - START) + "ms] " + s);
    }

    public static void main(String[] args) throws Exception {
        String pairingFile = args.length > 0 ? args[0] : "F:/AI/程序开发/.tmp/qrPayload.txt";
        String raw = new String(Files.readAllBytes(Paths.get(pairingFile)), StandardCharsets.US_ASCII).trim();
        JSONObject payload = new JSONObject(new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8));
        String url = payload.getString("publicUrl");
        String code = payload.getString("pairingCode");
        log("配对载荷解码成功 publicUrl=" + url + " code=" + code.substring(0, 6) + "…");

        gw = new GatewayClient(new ProtocolV2Test());

        // 看门狗必须是**非 daemon**：main() 返回后若只剩 daemon 线程，JVM 会直接退出，
        // 什么结果都打不出来（harness/Harness.java 的注释里记过这个坑）。
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(60_000); } catch (InterruptedException ignored) { }
            log("!! 看门狗超时（60s）");
            System.exit(2);
        });
        watchdog.setDaemon(false);
        watchdog.start();

        gw.pair(url, code, DEVICE_ID, "JVM ProtocolV2");
    }

    private static void ok(String what, boolean pass, String detail) {
        if (!pass) fails++;
        System.out.println((pass ? "  ✅ " : "  ❌ ") + what + (detail == null || detail.isEmpty() ? "" : " → " + detail));
    }

    private static void finish() {
        System.out.println();
        System.out.println("================ ProtocolV2 结果 ================");
        System.out.println(fails == 0 ? "全部断言通过（fails=0）" : ("失败断言数 = " + fails));
        System.out.println("================================================");
        System.exit(fails == 0 ? 0 : 1);
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { } }

    // ============================================================ 回调

    @Override public void onState(GatewayClient.State state, String detail) {
        log("state=" + state + " · " + detail);
    }

    @Override public void onPaired(JSONObject paired) {
        token = paired.optString("token", "");
        log("PAIRED token=" + (token.isEmpty() ? "(空!)" : token.substring(0, 6) + "…"));
        if (token.isEmpty()) { fails++; finish(); return; }
        new Thread(() -> {
            sleep(300);
            log("改用设备 token 重连（走 App 日常鉴权路径）");
            gw.disconnect();
            sleep(600);
            reconnected = true;
            gw.connect(gw.url(), token, DEVICE_ID, "JVM ProtocolV2");
        }).start();
    }

    @Override public void onHello(JSONObject hello) {
        log("HELLO protocol=" + hello.optInt("protocol") + " dsh=" + hello.optString("dshVersion", "-"));
        JSONArray caps = hello.optJSONArray("capabilities");
        boolean hasFileDownloads = false, hasUpload = false;
        if (caps != null) for (int i = 0; i < caps.length(); i++) {
            String c = caps.optString(i);
            if ("file-downloads".equals(c)) hasFileDownloads = true;
            // 只看 upload 字样：不能拿 "file-" 当判据 —— "file-downloads" 也含 "file-"，
            // 那样会把自己的下载能力误判成上传能力（第一版就这么写错了）。
            if ("file-uploads".equals(c)) hasUpload = true;
        }
        if (!reconnected) { log("  （配对连接的 hello：等 token 重连后再进入查询阶段）"); return; }
        if (phase3) return;
        phase3 = true;

        System.out.println();
        System.out.println("---- 断言 A：hello 能力宣告 ----");
        ok("hello.capabilities 含 file-downloads（生成物窗口的下载依赖它）", hasFileDownloads, "caps=" + caps);
        ok("hello.capabilities 含 file-uploads（通用文件附件通道已由网关补丁开启）",
                hasUpload, "patch-gateway-file-upload.ps1 会宣告它；客户端据此决定发/拦");
        gw.requestSessions();
    }

    @Override public void onSessions(JSONArray items, JSONObject raw) {
        if (items == null || items.length() == 0) { log("没有会话可查"); finish(); return; }
        String sid = "";
        for (int i = 0; i < items.length(); i++) {
            JSONObject s = items.optJSONObject(i);
            if (s == null) continue;
            String id = s.optString("sessionId", s.optString("id", ""));
            if (!id.isEmpty() && !s.optBoolean("blank", false)) { sid = id; break; }
        }
        if (sid.isEmpty()) sid = items.optJSONObject(0).optString("sessionId", "");
        log("会话数=" + items.length() + " 选用=" + sid);
        log("发 requestPermissionOptions / requestFileList …");
        gw.requestPermissionOptions(sid);
        gw.requestFileList(sid, "", "art-proto-v2");
        // 帧形状探针：发到**不存在的会话**。字段名/类型写错会在 schema 阶段就被拒；
        // 会话不存在则网关会回 session 相关的错误 —— 两者可区分，且不会创建任何会话。
        JSONArray imgs = new JSONArray();
        JSONObject img = new JSONObject();
        img.put("mediaType", "image/jpeg");
        img.put("data", "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==");
        img.put("name", "probe.jpg");
        imgs.put(img);
        gw.sendMessageWithImages("00000000-0000-0000-0000-000000000000", "proto-probe", imgs);
    }

    @Override public void onPermissionOptions(JSONObject f) {
        System.out.println();
        System.out.println("---- 断言 B：permission-options（任务④ 的数据源）----");
        JSONArray opts = f.optJSONArray("options");
        ok("回帧 kind=permission-options", "permission-options".equals(f.optString("kind")), f.optString("kind"));
        ok("options 非空", opts != null && opts.length() > 0, opts == null ? "null" : String.valueOf(opts.length()));
        boolean shapeOk = opts != null && opts.length() > 0;
        if (shapeOk) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < opts.length(); i++) {
                JSONObject o = opts.optJSONObject(i);
                if (o == null || o.optString("value", "").isEmpty()) shapeOk = false;
                if (sb.length() > 0) sb.append(',');
                sb.append(o == null ? "?" : (o.optString("value") + "/" + o.optString("name")));
            }
            ok("每个 option 都有 value（客户端按 value 当 id）", shapeOk, sb.toString());
        }
        JSONObject sp = f.optJSONObject("sessionPermissions");
        String cur = sp == null ? "" : sp.optString("currentValue", "");
        ok("当前值在 sessionPermissions.currentValue（客户端若只读 defaultPreset 会显示错）",
                sp != null && !cur.isEmpty(), "currentValue=" + cur + " defaultPreset=" + f.optString("defaultPreset"));
        done();
    }

    @Override public void onFileList(JSONObject f) {
        System.out.println();
        System.out.println("---- 断言 C：file-list（任务① 的数据源）----");
        ok("回帧 kind=file-list", "file-list".equals(f.optString("kind")), f.optString("kind"));
        JSONArray es = f.optJSONArray("entries");
        ok("entries 是数组", es != null, es == null ? "null" : String.valueOf(es.length()));
        boolean rel = true;
        if (es != null && es.length() > 0) {
            for (int i = 0; i < es.length(); i++) {
                JSONObject e = es.optJSONObject(i);
                if (e == null) continue;
                String p = e.optString("path", "");
                // 相对路径：不含盘符、不以 / 或 \ 开头
                if (p.startsWith("/") || p.startsWith("\\") || p.matches("^[A-Za-z]:.*")) rel = false;
                if (!e.has("kind")) rel = false;
            }
        }
        ok("entries[].path 是相对 cwd 的路径（客户端可直接拿去下载，不要再 relativize）", rel, "path=" + f.optString("path"));
        String first = es != null && es.length() > 0 ? es.optJSONObject(0).optString("name") : "(空)";
        log("  样例首项=" + first);
        done();
    }

    @Override public void onPermission(JSONObject f) {
        log("onPermission: " + f);
    }

    @Override public void onProtocolError(String code, String message, String requestType, String sessionId) {
        // 帧形状探针的回执：把 probe 用的全零 sessionId 认出来。
        // 期望 code=session/not-found —— 说明网关**先把帧解析过了**才去找会话；
        // 若字段名/类型写错，会停在 schema 阶段（bad-request / gateway/arguments-invalid）。
        boolean isProbe = String.valueOf(message).contains("00000000-0000-0000-0000-000000000000")
                || "message".equals(requestType);
        if (isProbe) {
            System.out.println();
            System.out.println("---- 断言 D：sendMessageWithImages 帧形状探针（发到不存在的会话）----");
            boolean frameAccepted = !"bad-request".equals(code)
                    && !String.valueOf(message).contains("images")
                    && !String.valueOf(message).contains("arguments")
                    && !String.valueOf(message).contains("invalid");
            ok("帧被网关 schema 接受（错误来自会话不存在，而非字段非法）", frameAccepted,
                    "code=" + code + " requestType=" + requestType + " msg=" + message);
            done();
            return;
        }
        log("协议错误: code=" + code + " requestType=" + requestType + " msg=" + message);
    }

    // ---- 其余回调：本轮用不到，保持空实现（接口要求）----

    @Override public void onHistory(String sessionId, JSONArray events, JSONObject meta) { }
    @Override public void onSnapshot(String sessionId, JSONObject snapshot) { }
    @Override public void onAssistantStream(JSONObject frame) { }
    @Override public void onEvent(String sessionId, JSONObject event, Object seq, Object time) { }
    @Override public void onApprovalRequested(JSONObject frame) { }
    @Override public void onQuestionRequested(JSONObject frame) { }
    @Override public void onInteractionResolved(JSONObject frame) { }
    @Override public void onSent(String sessionId, JSONObject raw) { }
    @Override public void onOther(String kind, JSONObject frame) { }
    @Override public void onAttachment(String sessionId, String attachmentId, String mediaType, String base64) { }
    @Override public void onDownload(String kind, JSONObject frame) { }
}
