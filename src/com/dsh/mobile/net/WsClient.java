package com.dsh.mobile.net;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * 极简 RFC6455 WebSocket 客户端（仅文本帧 + ping/pong/close + 分片重组）。
 * 不依赖第三方库：DSH Mobile Gateway 的帧全部是 JSON 文本帧。
 */
public final class WsClient {

    public interface Listener {
        void onOpen();
        void onText(String text);
        void onClosed(int code, String reason);
        void onFailure(Throwable error);
    }

    private static final SecureRandom RNG = new SecureRandom();
    private static final int MAX_FRAME = 96 * 1024 * 1024;

    private final String host;
    private final int port;
    private final String path;
    private final boolean tls;
    private final List<String> subprotocols;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 单线程写队列。Android 禁止在主线程做网络 I/O，而协议请求是在主线程（收到 hello 等
     * 回调）里发出的，直接 write 会抛 NetworkOnMainThreadException。所有写都走这个队列，
     * 既避开主线程限制，又天然保证帧顺序。
     */
    private final java.util.concurrent.ExecutorService writer =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ws-writer");
                t.setDaemon(true);
                return t;
            });

    private void submit(Runnable r) {
        try { writer.execute(r); } catch (Throwable ignored) { }
    }

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Thread reader;
    private volatile boolean closing;
    private volatile int lastCloseCode = 1006;
    private volatile String lastCloseReason = "connection lost";
    /** 自建反向代理常用自签名证书，开启后不校验（默认关闭）。 */
    private final boolean trustAll;
    private volatile Throwable failure;
    /**
     * 最近一次收到「完整帧」的时刻（毫秒）。用于识别"看起来已连接、实际已死"的假连接：
     * 电脑休眠 / 路由重启 / 隧道断开都不会发 FIN，阻塞读会一直挂着。
     * 任何帧都算（文本 / ping / pong / close），0 表示还没收到过。
     */
    private volatile long lastInboundAt = 0L;

    /** 最近一次收到完整帧的时刻；配合 GatewayClient 的 ping 做假连接判定。 */
    public long lastInboundAt() { return lastInboundAt; }

    public String failureReason() { return failure == null ? null : describe(failure); }

    /** 把网络异常翻译成用户能照着做的说明。 */
    private static String describe(Throwable t) {
        if (t == null) return "连接失败";
        String n = t.getClass().getName();
        String m = t.getMessage() == null ? "" : t.getMessage();
        String all = n + " " + m;
        if (all.contains("SSL") || all.contains("Cert") || all.contains("Trust")
                || all.contains("anchor") || all.contains("certificate")) {
            return "证书不受信任（自建反代多为自签名）→ 请在设置里打开「允许自签名证书」";
        }
        if (n.contains("UnknownHost") || all.contains("Unable to resolve")) return "域名解析失败，检查「公网地址」是否拼错";
        if (n.contains("ConnectException") || all.contains("ECONNREFUSED")) return "端口拒绝连接：反代没在监听，或没指向网关端口";
        if (n.contains("SocketTimeout")) return "连接超时：网络不通或反代不可达";
        if (all.contains("404")) return "路径不存在(404)：反代没有把 /ws/mobile 转发到网关（常见于未开启 WebSocket 转发）";
        if (all.contains("401") || all.contains("403")) return "被反代拒绝鉴权，请检查反代的访问控制";
        if (all.contains("502") || all.contains("503")) return "后端网关不可达或未开启，请在电脑端开启网关";
        return m.isEmpty() ? ("连接失败：" + n) : ("连接失败：" + m);
    }

    public WsClient(String url, List<String> subprotocols, Map<String, String> extraHeaders, Listener listener)
            throws IOException {
        this(url, subprotocols, extraHeaders, listener, false);
    }

    public WsClient(String url, List<String> subprotocols, Map<String, String> extraHeaders, Listener listener,
                    boolean trustAllCerts)
            throws IOException {
        this.listener = listener;
        this.trustAll = trustAllCerts;
        this.subprotocols = subprotocols == null ? new ArrayList<String>() : new ArrayList<>(subprotocols);
        if (extraHeaders != null) headers.putAll(extraHeaders);

        String raw = url == null ? "" : url.trim();
        if (raw.regionMatches(true, 0, "http://", 0, 7)) raw = "ws://" + raw.substring(7);
        else if (raw.regionMatches(true, 0, "https://", 0, 8)) raw = "wss://" + raw.substring(8);

        URI u;
        try {
            u = new URI(raw);
        } catch (Exception e) {
            throw new IOException("地址格式不正确：" + url, e);
        }
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        if ("ws".equals(scheme)) tls = false;
        else if ("wss".equals(scheme)) tls = true;
        else throw new IOException("只支持 ws:// 或 wss://，收到：" + scheme);

        if (u.getHost() == null || u.getHost().isEmpty()) throw new IOException("地址缺少主机名：" + url);
        host = u.getHost();
        port = u.getPort() > 0 ? u.getPort() : (tls ? 443 : 80);
        String p = u.getRawPath();
        path = (p == null || p.isEmpty()) ? "/ws/mobile" : p;
    }

    public String host() { return host; }
    public int port() { return port; }

    /** 信任任意服务端证书（用于自建反代的自签名证书）。 */
    private static SSLSocketFactory trustAllFactory() {
        try {
            javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
            ctx.init(null, new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        @Override public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) { }
                        @Override public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) { }
                        @Override public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[0];
                        }
                    }
            }, new SecureRandom());
            return ctx.getSocketFactory();
        } catch (Throwable t) {
            return (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
    }

    public void connect() {
        reader = new Thread(this::run, "ws-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void run() {
        try {
            lastInboundAt = System.currentTimeMillis();
            if (tls) {
                SSLSocketFactory f = trustAll
                        ? trustAllFactory()
                        : (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket s = (SSLSocket) f.createSocket();
                try {
                    SSLParameters params = s.getSSLParameters();
                    params.setServerNames(Collections.singletonList(new SNIHostName(host)));
                    // 信任模式之外，强制做证书链 + 主机名校验
                    if (!trustAll) params.setEndpointIdentificationAlgorithm("HTTPS");
                    s.setSSLParameters(params);
                } catch (Throwable ignored) { /* 老设备不支持 SNI 扩展，忽略 */ }
                socket = s;
            } else {
                socket = new Socket();
            }
            socket.connect(new InetSocketAddress(host, port), 12000);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(0);
            if (tls) ((SSLSocket) socket).startHandshake();

            in = socket.getInputStream();
            out = socket.getOutputStream();

            handshake();
            post(listener::onOpen);
            loop();

            if (closing) { lastCloseCode = 1000; lastCloseReason = "closed"; }
        } catch (Throwable t) {
            if (!closing) {
                // 关键：finally 里的 onClosed 会覆盖状态，这里先把真实原因写进去
                lastCloseCode = 1006;
                lastCloseReason = describe(t);
                failure = t;
                post(() -> listener.onFailure(t));
            }
        } finally {
            closeQuietly();
            final int code = lastCloseCode;
            final String reason = lastCloseReason;
            post(() -> listener.onClosed(code, reason));
        }
    }

    private void handshake() throws IOException {
        byte[] key = new byte[16];
        RNG.nextBytes(key);
        String b64key = Base64.encodeToString(key, Base64.NO_WRAP);

        StringBuilder sb = new StringBuilder(512);
        sb.append("GET ").append(path).append(" HTTP/1.1\r\n");
        sb.append("Host: ").append(host);
        if (port != (tls ? 443 : 80)) sb.append(':').append(port);
        sb.append("\r\n");
        sb.append("Upgrade: websocket\r\n");
        sb.append("Connection: Upgrade\r\n");
        sb.append("Sec-WebSocket-Key: ").append(b64key).append("\r\n");
        sb.append("Sec-WebSocket-Version: 13\r\n");
        if (!subprotocols.isEmpty()) {
            StringBuilder p = new StringBuilder();
            for (int i = 0; i < subprotocols.size(); i++) {
                if (i > 0) p.append(", ");
                p.append(subprotocols.get(i));
            }
            sb.append("Sec-WebSocket-Protocol: ").append(p).append("\r\n");
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
        }
        sb.append("\r\n");

        out.write(sb.toString().getBytes("UTF-8"));
        out.flush();

        // 读取响应头
        ByteArrayOutputStream head = new ByteArrayOutputStream(1024);
        int matched = 0;
        while (head.size() < 64 * 1024) {
            int c = in.read();
            if (c < 0) throw new EOFException("握手期间连接被关闭");
            head.write(c);
            if (c == '\r' && (matched == 0 || matched == 2)) matched++;
            else if (c == '\n' && (matched == 1 || matched == 3)) {
                matched++;
                if (matched == 4) break;
            } else matched = 0;
        }
        String resp = new String(head.toByteArray(), "UTF-8");
        String first = resp;
        int nl = resp.indexOf("\r\n");
        if (nl > 0) first = resp.substring(0, nl);
        String[] parts = first.split(" ");
        int status = 0;
        if (parts.length >= 2) {
            try { status = Integer.parseInt(parts[1].trim()); } catch (Exception ignored) { }
        }
        if (status != 101) {
            String hint;
            if (status == 401) hint = "鉴权失败(401)：配对已失效或 token 无效，请重新扫码配对";
            else if (status == 403) hint = "被拒绝(403)";
            else if (status == 404) hint = "路径不存在(404)：确认地址以 /ws/mobile 结尾；若是反向代理，请开启 WebSocket 转发";
            else if (status == 503) hint = "网关未开启(503)：请在电脑 DSH 的「移动设备」面板开启网关";
            else hint = "握手失败：HTTP " + status;
            throw new IOException(hint);
        }
    }

    // ------------------------------------------------------------ 收发

    /** 异步发送文本帧。返回后不代表已写出，但顺序保证。 */
    public void sendText(String text) {
        if (closed.get() || text == null) return;
        byte[] payload;
        try { payload = text.getBytes("UTF-8"); } catch (Throwable e) { return; }
        submit(() -> {
            try {
                if (out != null) writeFrame(0x1, payload);
            } catch (Throwable ignored) { }
        });
    }

    public void close(int code, String reason) {
        if (!closed.compareAndSet(false, true)) return;
        closing = true;
        lastCloseCode = code;
        lastCloseReason = reason == null ? "" : reason;
        byte[] r;
        try { r = reason == null ? new byte[0] : reason.getBytes("UTF-8"); }
        catch (Throwable e) { r = new byte[0]; }
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        p.write((code >>> 8) & 0xFF);
        p.write(code & 0xFF);
        p.write(r, 0, Math.min(r.length, 120));
        final byte[] frame = p.toByteArray();
        submit(() -> {
            try { if (out != null) writeFrame(0x8, frame); } catch (Throwable ignored) { }
            closeQuietly();
        });
    }

    public boolean isClosed() { return closed.get(); }

    private void closeQuietly() {
        try { if (socket != null) socket.close(); } catch (Throwable ignored) { }
    }

    private void writeFrame(int opcode, byte[] payload) throws IOException {
        OutputStream o = out;
        if (o == null) throw new IOException("尚未连接");
        int len = payload.length;
        ByteArrayOutputStream b = new ByteArrayOutputStream(len + 14);
        b.write(0x80 | opcode);
        if (len < 126) {
            b.write(0x80 | len);
        } else if (len <= 0xFFFF) {
            b.write(0x80 | 126);
            b.write((len >>> 8) & 0xFF);
            b.write(len & 0xFF);
        } else {
            b.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) b.write((int) (((long) len >>> (8 * i)) & 0xFF));
        }
        byte[] mask = new byte[4];
        RNG.nextBytes(mask);
        b.write(mask, 0, 4);
        byte[] masked = new byte[len];
        for (int i = 0; i < len; i++) masked[i] = (byte) (payload[i] ^ mask[i & 3]);
        b.write(masked, 0, len);

        synchronized (this) {
            o.write(b.toByteArray());
            o.flush();
        }
    }

    private void loop() throws IOException {
        ByteArrayOutputStream frag = new ByteArrayOutputStream(8192);
        int fragOpcode = 0;

        while (!closing) {
            int b0 = in.read();
            if (b0 < 0) throw new EOFException("连接已被对端关闭");
            int b1 = in.read();
            if (b1 < 0) throw new EOFException("连接已被对端关闭");

            boolean fin = (b0 & 0x80) != 0;
            int opcode = b0 & 0x0F;
            boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7F;
            if (len == 126) {
                len = ((long) readByte()) << 8 | readByte();
            } else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) len = (len << 8) | readByte();
            }
            if (len < 0 || len > MAX_FRAME) throw new IOException("帧长度非法：" + len);

            byte[] maskKey = masked ? readN(4) : null;
            byte[] data = readN((int) len);
            if (maskKey != null) {
                for (int i = 0; i < data.length; i++) data[i] ^= maskKey[i & 3];
            }
            // 一个完整帧已落袋：刷新存活时间戳（假连接防护用，见 lastInboundAt()）
            lastInboundAt = System.currentTimeMillis();

            switch (opcode) {
                case 0x9: // ping
                    try { writeFrame(0xA, data); } catch (Throwable ignored) { }
                    break;
                case 0xA: // pong
                    break;
                case 0x8: { // close
                    int code = 1000;
                    String reason = "";
                    if (data.length >= 2) {
                        code = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                        if (data.length > 2) reason = new String(data, 2, data.length - 2, "UTF-8");
                    }
                    lastCloseCode = code;
                    lastCloseReason = reason;
                    closing = true;
                    try { writeFrame(0x8, new byte[] { (byte) (code >>> 8), (byte) code }); } catch (Throwable ignored) { }
                    break;
                }
                case 0x1:
                case 0x2:
                    frag.reset();
                    fragOpcode = opcode;
                    frag.write(data, 0, data.length);
                    if (fin && fragOpcode == 0x1) dispatch(new String(frag.toByteArray(), "UTF-8"));
                    break;
                case 0x0:
                    frag.write(data, 0, data.length);
                    if (fin && fragOpcode == 0x1) dispatch(new String(frag.toByteArray(), "UTF-8"));
                    break;
                default:
                    break;
            }
            if (closing) break;
        }
    }

    private void dispatch(String text) {
        post(() -> listener.onText(text));
    }

    private int readByte() throws IOException {
        int c = in.read();
        if (c < 0) throw new EOFException("连接已被对端关闭");
        return c;
    }

    private byte[] readN(int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r < 0) throw new EOFException("连接已被对端关闭");
            off += r;
        }
        return buf;
    }

    private void post(Runnable r) {
        main.post(r);
    }
}
