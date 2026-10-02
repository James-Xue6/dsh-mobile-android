// PairingText（配对串健壮化 / 解码 / 诊断描述）的 JVM 断言。
//
// 为什么要这个：问题 2 的表现是"adb intent 传配对串能配对、相机扫码却报错"，
// 差别只可能在"扫到的字节"上。真机上没法复现，但 PairingText 是纯字符串处理，
// 可以在 JVM 上把常见包裹形态穷举一遍。
//
// 运行（仓库根目录，Windows；注意 Java 里注释中的反斜杠+u 会被当 Unicode 转义，
// 所以下面路径统一用正斜杠写；输出目录用仓库内的 .verify-e2e/ 是 rc 自带的 gitignore）：
//   javac -encoding UTF-8 -cp harness/lib/json-20240303.jar -d .verify-e2e/out-pair
//         src/com/dsh/mobile/PairingText.java harness/shim/android/util/Base64.java
//         harness/src/PairingTextTest.java
//   java -Dstdout.encoding=UTF-8 -cp ".verify-e2e/out-pair;harness/lib/json-20240303.jar" PairingTextTest
// 期望输出末行：通过 57 项，失败 0 项
//
// 它断言的是**仓库里的真实源码**（src/com/dsh/mobile/PairingText.java），不是副本。

import com.dsh.mobile.PairingText;

import org.json.JSONObject;

public final class PairingTextTest {

    private static int pass = 0;
    private static int fail = 0;

    /** 与网关 index.mjs 一致的标准形态：UTF-8 JSON -> 无 padding Base64URL。 */
    private static String gatewayPayload(String json) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void eq(String what, Object actual, Object expected) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            pass++;
        } else {
            fail++;
            System.out.println("  FAIL " + what + "\n       期望: " + expected + "\n       实际: " + actual);
        }
    }

    private static void ok(String what, boolean cond) {
        if (cond) pass++;
        else { fail++; System.out.println("  FAIL " + what); }
    }

    public static void main(String[] args) throws Exception {
        String json = "{\"version\":2,\"publicUrl\":\"ws://192.168.1.100:3091/ws/mobile\","
                + "\"pairingCode\":\"abcdef0123456789\",\"expiresAt\":4102416000000,"
                + "\"endpoints\":[\"ws://192.168.1.100:3091/ws/mobile\"]}";
        String canonical = gatewayPayload(json);

        System.out.println("[1] 标准形态（网关原样输出）");
        ok("canonical 是紧凑 QR 载荷（>100 字符、无 padding）",
                canonical.length() > 100 && canonical.indexOf('=') < 0 && canonical.indexOf('+') < 0);
        eq("sanitize 幂等", PairingText.sanitize(canonical), canonical);
        eq("decode 出 pairingCode", PairingText.decode(canonical).optString("pairingCode"), "abcdef0123456789");
        eq("decode 出 version", PairingText.decode(canonical).optInt("version"), 2);

        System.out.println("[2] 相机扫码/粘贴常见的包裹形态都应被吃掉");
        String[][] wraps = new String[][] {
            { "首尾空白", "  \t " + canonical + " \n " },
            { "换行插在中间", canonical.substring(0, 40) + "\n" + canonical.substring(40) },
            { "BOM 开头", "\uFEFF" + canonical },
            { "零宽空格", "\u200B" + canonical + "\u200D" },
            { "软连字符", canonical.substring(0, 10) + "\u00AD" + canonical.substring(10) },
            { "不间断空格", "\u00A0" + canonical + "\u3000" },
            { "英文双引号包裹", "\"" + canonical + "\"" },
            { "英文单引号包裹", "'" + canonical + "'" },
            { "中文双引号包裹", "\u201C" + canonical + "\u201D" },
            { "中文单引号包裹", "\u2018" + canonical + "\u2019" },
            { "反引号包裹", "`" + canonical + "`" },
            { "协议前缀", "dsh-mobile-v1:" + canonical },
            { "协议前缀(大写)", "DSH-MOBILE-V1:" + canonical },
            { "协议前缀+斜杠", "dsh-mobile-v1://" + canonical },
            { "前缀+引号+空白", "  \u201C dsh-mobile-v1:" + canonical + " \u201D " },
        };
        for (String[] w : wraps) {
            String cleaned = PairingText.sanitize(w[1]);
            eq("sanitize[" + w[0] + "]", cleaned, canonical);
            eq("decode[" + w[0] + "]", PairingText.decode(cleaned).optString("pairingCode"), "abcdef0123456789");
        }

        System.out.println("[3] 旧的严格 Base64URL 会拒绝、现在必须能吃下的形态");
        // 带 padding 的 Base64URL
        String paddedUrl = java.util.Base64.getUrlEncoder()
                .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ok("带 padding 的 Base64URL 可解", PairingText.decode(paddedUrl).optInt("version") == 2);
        // 标准 Base64（含 + / =）——旧实现直接抛"不是合法的 Base64URL"
        String std = java.util.Base64.getEncoder()
                .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ok("标准 Base64 可解", PairingText.decode(std).optInt("version") == 2);
        // 明文 JSON
        ok("明文 JSON 可解", PairingText.decode("  " + json + "  ").optInt("version") == 2);
        // 解不出 JSON 时必须报错（而不是返回半截），且不得回显内容
        try {
            PairingText.decode(gatewayPayload("not json at all"));
            fail++; System.out.println("  FAIL 非 JSON 载荷应当抛错");
        } catch (Exception e) {
            pass++;
            ok("报错信息不泄漏原文", !e.getMessage().contains("not json"));
        }

        System.out.println("[4] 安装包二维码（http 链接）必须被识别出来");
        String[] urls = new String[] {
            "http://192.168.1.100:8099/app.apk",
            "https://cdn.jsdelivr.net/gh/James-Xue6/dsh-mobile-android@v0.8/dist/dsh-mobile.apk",
            "HTTPS://Example.COM/app.apk",
            "\u201Chttp://192.168.1.100:8099/app.apk\u201D",
        };
        for (String u : urls) {
            ok("识别为 http 链接: " + u.substring(0, Math.min(24, u.length())),
                    PairingText.looksLikeHttpUrl(PairingText.sanitize(u)));
        }
        ok("配对串不应被误判成链接", !PairingText.looksLikeHttpUrl(canonical));
        // 旧实现是 scanned.startsWith("http://") || startsWith("https://")：大小写敏感。
        ok("大写 HTTPS:// 旧写法会漏判（对比用）", !"HTTPS://Example.COM/app.apk".startsWith("http"));
        ok("大写 HTTPS:// 新写法能识别", PairingText.looksLikeHttpUrl("HTTPS://Example.COM/app.apk"));

        System.out.println("[5] 诊断描述的脱敏");
        String d1 = PairingText.describe(canonical);
        ok("配对串只留前 4 位", d1.contains(canonical.substring(0, 4) + "…"));
        ok("配对串其余部分不出现", !d1.contains(canonical.substring(4, 20)));
        ok("诊断里有总长度", d1.contains(String.valueOf(canonical.length())));
        ok("诊断里标出 Base64URL", d1.contains("Base64URL"));

        String urlWithToken = "http://192.168.1.100:8099/app.apk?token=SECRETTOKENVALUE&x=1";
        String d2 = PairingText.describe(urlWithToken);
        ok("URL 里 token 只留前 4 位", d2.contains("token=SECR****"));
        ok("URL 里 token 其余部分不出现", !d2.contains("SECRETTOKENVALUE"));
        ok("URL 判定为链接", d2.contains("http(s) 链接"));

        String d3 = PairingText.describe(PairingText.sanitize("{\"pairingCode\":\"zzzz9999\"}"));
        ok("明文 JSON 只留前 4 位", d3.contains("{\"pa…"));
        ok("JSON 其余部分不出现", !d3.contains("zzzz9999"));
        eq("空内容", PairingText.describe(""), "（空内容）");

        // 前 60 字符的规则：长 URL 只截前 60
        StringBuilder longUrl = new StringBuilder("http://192.168.1.100:8099/app.apk?");
        for (int i = 0; i < 40; i++) longUrl.append("k").append(i).append("=v&");
        String d4 = PairingText.describe(longUrl.toString());
        ok("长 URL 描述会截断（带省略号）", d4.endsWith("…"));

        System.out.println();
        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) System.exit(1);
    }
}
