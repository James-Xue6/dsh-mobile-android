package com.dsh.mobile;

import android.util.Base64;

import org.json.JSONObject;

import java.util.Locale;

/**
 * 配对串的「健壮化预处理 + 解码 + 诊断描述」（问题 2 的核心逻辑）。
 *
 * <p>单独拆成一个类的原因有两个：
 * <ol>
 *   <li>这里全是纯字符串处理，不碰任何 Android UI —— 可以脱离真机，用 JVM 直接跑断言
 *       （见 {@code harness/src/PairingTextTest.java}）。相机扫码这条路径在真机上
 *       很难复现"扫到的字节和 intent 传进来的字节不一样"，但在这里可以穷举。</li>
 *   <li>入口 {@code MainActivity.startPairing()} 只负责报错与配对编排，不再夹带一屏解析规则。</li>
 * </ol>
 *
 * <p>只依赖 {@code android.util.Base64} 与 {@code org.json}，两者在 JVM 联调里都有 shim。
 */
public final class PairingText {

    private PairingText() { }

    /**
     * 扫码 / 粘贴入口的统一预处理。
     *
     * <p>真实世界里的二维码内容经常被包一层：首尾空白、换行、BOM、零宽字符、
     * 聊天工具加的中英文引号、甚至把协议前缀一起编进去。这些字符肉眼不可见，
     * 却足以让"看着一样"的两个串一个能配、一个报错 ——
     * 相机扫码与 adb intent 的差别往往就在这里。
     */
    public static String sanitize(String raw) {
        if (raw == null) return "";
        String s = raw;
        // BOM / 零宽空格·连接符·不连字 / 软连字符 / 不间断空格 / 全角空格
        s = s.replace("\uFEFF", "").replace("\u200B", "").replace("\u200C", "")
                .replace("\u200D", "").replace("\u00AD", "").replace("\u00A0", "")
                .replace("\u3000", "");
        s = s.replace("\r", "").replace("\n", "").replace("\t", "").trim();
        // 首尾成对的引号（最多剥两层，容忍「“'xxxx'”」这种嵌套包裹）
        for (int i = 0; i < 2; i++) {
            s = stripWrapping(s, "\"", "\"").trim();
            s = stripWrapping(s, "'", "'").trim();
            s = stripWrapping(s, "\u201C", "\u201D").trim();   // 中文双引号
            s = stripWrapping(s, "\u2018", "\u2019").trim();   // 中文单引号
            s = stripWrapping(s, "`", "`").trim();
        }
        // 外部包裹的协议前缀（有些环节会把它们一起编进二维码）
        String lower = s.toLowerCase(Locale.ROOT);
        for (String p : new String[] { "dsh-mobile-v1:", "dsh-mobile:", "dshmobile:" }) {
            if (lower.startsWith(p)) {
                s = s.substring(p.length()).trim();
                while (s.startsWith("/")) s = s.substring(1);   // 容忍 dsh-mobile-v1://…
                break;
            }
        }
        return s.trim();
    }

    /** s 若被 open/close 成对包住就去掉一层，否则原样返回。 */
    public static String stripWrapping(String s, String open, String close) {
        if (s.length() >= 2 && s.startsWith(open) && s.endsWith(close)) {
            return s.substring(open.length(), s.length() - close.length());
        }
        return s;
    }

    /** 是否 http(s) 链接（大小写不敏感）。 */
    public static boolean looksLikeHttpUrl(String s) {
        String t = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("http://") || t.startsWith("https://");
    }

    /**
     * 解码配对串：网关的标准形态是「无 padding 的 Base64URL」，这里容错地也接受
     * 标准 Base64 / 带 padding 的写法，以及明文 JSON。
     *
     * <p>旧写法遇到 {@code + / =} 直接抛「不是合法的 Base64URL 配对串」，
     * 任何一处编码形态差异都会变成一句无从定位的报错。现在统一归一化后再解，
     * 真正解不出来时才报错（且不把解出来的内容回显，避免把凭证写进提示里）。
     */
    public static JSONObject decode(String raw) throws Exception {
        String s = raw.trim();
        if (s.startsWith("{")) return new JSONObject(s);
        // base64 里不该出现的空白（含全角空格）
        s = s.replaceAll("[\\s\u3000]", "");
        // URL-safe 的 '-' '_' 归一到标准表，两种都吃
        String std = s.replace('-', '+').replace('_', '/');
        int pad = std.length() % 4;
        if (pad == 2) std = std + "==";
        else if (pad == 3) std = std + "=";
        else if (pad == 1) {
            throw new IllegalArgumentException("长度 " + s.length() + " 不是合法的 Base64 配对串");
        }
        byte[] bytes = Base64.decode(std, Base64.DEFAULT);
        String text = new String(bytes, "UTF-8").trim();
        if (!text.startsWith("{")) {
            throw new IllegalArgumentException("配对串解出来不是 JSON");
        }
        return new JSONObject(text);
    }

    /**
     * 诊断描述：说明"最近一次扫到了什么"，但不泄露内容。
     *
     * <p>脱敏规则：http(s) 链接给出前 60 字符（链接本身不是凭证，但 query 里的
     * token / 配对码参数值会被掩码）；配对串（Base64URL 或明文 JSON）整体就是凭证，
     * 只保留前 4 个字符，并给出总长度 —— 长度与是否 JSON 对定位问题已经足够。
     */
    public static String describe(String cleaned) {
        if (cleaned == null || cleaned.isEmpty()) return "（空内容）";
        int n = cleaned.length();
        String kind;
        String shown;
        if (looksLikeHttpUrl(cleaned)) {
            kind = "http(s) 链接";
            String head = n > 60 ? cleaned.substring(0, 60) : cleaned;
            shown = maskUrlSecret(head) + (n > 60 ? "…" : "");
        } else if (cleaned.startsWith("{")) {
            kind = "明文 JSON 配对信息";
            shown = cleaned.substring(0, Math.min(4, n)) + "…（仅留前 4 位）";
        } else {
            kind = "Base64URL 配对串";
            shown = cleaned.substring(0, Math.min(4, n)) + "…（仅留前 4 位）";
        }
        return "最近一次扫码/粘贴：共 " + n + " 字符 · 判定为 " + kind + " · " + shown;
    }

    /** 把 URL 里 token / 配对码一类的查询参数值掩码成前 4 位。 */
    public static String maskUrlSecret(String url) {
        return url.replaceAll("(?i)([?&](?:token|pairingcode|pairing_code|code|key)=)([^&\\s]{0,4})[^&\\s]*",
                "$1$2****");
    }
}
