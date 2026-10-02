// RoutePolicy（自动 / 只用内网 / 只用公网 的线路选择）的 JVM 断言。
//
// 为什么要这个：这段逻辑是"用户规则"的唯一实现处，而它偏偏是纯逻辑 + 组合多
// （WiFi/移动数据/未识别 × 三个档位 × 有没有可用内网地址 × 内网退避），
// 真机上每次都要开关 WiFi 才能试一种，穷举不现实。这里在 JVM 上把组合跑一遍。
//
// 运行（仓库根目录，Windows；输出目录用仓库外，不要落进仓库）：
//   javac -encoding UTF-8 -d "$env:TEMP\route-test"
//         src/com/dsh/mobile/net/RoutePolicy.java src/com/dsh/mobile/net/LanAddress.java
//         harness/src/RoutePolicyTest.java
//   java -Dstdout.encoding=UTF-8 -cp "$env:TEMP\route-test" RoutePolicyTest
// 期望输出末行：通过 52 项，失败 0 项
//
// 它断言的是**仓库里的真实源码**（src/com/dsh/mobile/net/RoutePolicy.java），不是副本。

import com.dsh.mobile.net.RoutePolicy;

public final class RoutePolicyTest {

    private static int pass = 0;
    private static int fail = 0;

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

    /** 常用合成地址（**不是**任何真实地址，只是格式正确的样例）。 */
    private static final String LAN_OK = "ws://192.168.1.100:3091/ws/mobile";
    private static final String LAN_OK_10 = "ws://10.0.0.7:3091/ws/mobile";
    /** 172.30.x = 电脑上虚拟网卡的地址：手机永远连不上，自动档必须当没有内网。 */
    private static final String LAN_VIRTUAL = "ws://172.30.16.1:3091/ws/mobile";
    /** 企业内网手填地址：LanAddress 判为不可用，但手动档必须尊重用户（不自动档）。 */
    private static final String LAN_ENTERPRISE = "ws://172.16.8.9:3091/ws/mobile";
    private static final String WAN_OK = "wss://example.invalid/ws/mobile";

    public static void main(String[] args) {
        // ---------- 档位归一化
        eq("normalize(null)", RoutePolicy.normalizeMode(null), RoutePolicy.AUTO);
        eq("normalize(\"\")", RoutePolicy.normalizeMode(""), RoutePolicy.AUTO);
        eq("normalize(bogus)", RoutePolicy.normalizeMode("WAN "), RoutePolicy.AUTO);
        eq("normalize(lan)", RoutePolicy.normalizeMode("lan"), RoutePolicy.LAN);
        eq("normalize(wan)", RoutePolicy.normalizeMode("wan"), RoutePolicy.WAN);

        // ---------- 自动档：用户规则「WiFi -> 内网」
        RoutePolicy.Pick p = RoutePolicy.decide(RoutePolicy.AUTO, LAN_OK, WAN_OK, true, true, false);
        eq("自动+WiFi 走内网 url", p.url, LAN_OK);
        eq("自动+WiFi 走内网 line", p.line, "走内网");
        eq("自动+WiFi 走内网 source", p.source, "自动 · 已连 WiFi");
        ok("自动+WiFi 标记为自动", p.auto);
        ok("自动+WiFi 不是公网", !p.wan);

        // ---------- 自动档：用户规则「移动数据 / 未连 WiFi -> 公网」
        p = RoutePolicy.decide(RoutePolicy.AUTO, LAN_OK, WAN_OK, false, true, false);
        eq("自动+移动数据 走公网 url", p.url, WAN_OK);
        eq("自动+移动数据 line", p.line, "走公网");
        eq("自动+移动数据 source", p.source, "自动 · 未连 WiFi（移动数据）");
        ok("自动+移动数据 标记公网", p.wan);

        // 10.x 内网地址同样是可用内网
        p = RoutePolicy.decide(RoutePolicy.AUTO, LAN_OK_10, WAN_OK, true, true, false);
        eq("自动+WiFi 10.x 走内网", p.url, LAN_OK_10);

        // ---------- 自动档：WiFi 但没有可用内网地址 -> 当没内网，走公网
        p = RoutePolicy.decide(RoutePolicy.AUTO, LAN_VIRTUAL, WAN_OK, true, true, false);
        eq("自动+WiFi+虚拟网卡 走公网 url", p.url, WAN_OK);
        eq("自动+WiFi+虚拟网卡 line", p.line, "走公网");
        ok("自动+WiFi+虚拟网卡 source 说明原因", p.source.contains("没有可用内网地址"));
        ok("自动+WiFi+虚拟网卡 标记公网", p.wan);

        p = RoutePolicy.decide(RoutePolicy.AUTO, LAN_VIRTUAL, "", true, true, false);
        eq("自动+WiFi+无内网无公网 url 空", p.url, "");
        eq("自动+WiFi+无内网无公网 line", p.line, "没有可用地址");

        p = RoutePolicy.decide(RoutePolicy.AUTO, "", "", true, true, false);
        eq("自动+WiFi+两个地址都空", p.url, "");

        // ---------- 自动档：没连 WiFi 又没填公网地址（总比完全连不上好）
        p = RoutePolicy.decide(RoutePolicy.AUTO, LAN_OK, "", false, true, false);
        eq("自动+非WiFi+无公网 暂用内网", p.url, LAN_OK);
        eq("自动+非WiFi+无公网 line", p.line, "走内网");
        ok("自动+非WiFi+无公网 source 说明", p.source.contains("没有公网地址"));

        // ---------- 自动档：网络判不出来（无权限 / 老系统）
        p = RoutePolicy.decide(RoutePolicy.AUTO, LAN_OK, WAN_OK, false, false, false);
        eq("自动+网络未识别 走公网", p.url, WAN_OK);
        eq("自动+网络未识别 source", p.source, "自动 · 网络未识别");

        // ---------- 自动档 + 内网退避（第二道保险 / 防乒乓）
        p = RoutePolicy.decide(RoutePolicy.AUTO, LAN_OK, WAN_OK, true, true, true);
        eq("自动+WiFi+退避 走公网", p.url, WAN_OK);
        ok("自动+WiFi+退避 source 说明", p.source.contains("暂用公网"));

        p = RoutePolicy.decide(RoutePolicy.AUTO, LAN_OK, "", true, true, true);
        eq("自动+WiFi+退避但没公网 仍走内网", p.url, LAN_OK);
        ok("自动+WiFi+退避但没公网 source", p.source.contains("已连 WiFi"));

        // 退避不该影响非 WiFi 场景（那些场景本来就该走公网）
        p = RoutePolicy.decide(RoutePolicy.AUTO, LAN_OK, WAN_OK, false, true, true);
        eq("自动+非WiFi+退避 仍走公网", p.url, WAN_OK);

        // ---------- 手动「只用内网」：手动永远优先
        p = RoutePolicy.decide(RoutePolicy.LAN, LAN_OK, WAN_OK, false, true, false);
        eq("只用内网+移动数据 仍走内网", p.url, LAN_OK);
        eq("只用内网 line", p.line, "走内网");
        eq("只用内网 source", p.source, "手动 · 只用内网");
        ok("只用内网 不是自动", !p.auto);
        ok("只用内网 不是公网", !p.wan);

        p = RoutePolicy.decide(RoutePolicy.LAN, LAN_ENTERPRISE, WAN_OK, true, true, false);
        eq("只用内网+手填 172.16 尊重用户", p.url, LAN_ENTERPRISE);

        p = RoutePolicy.decide(RoutePolicy.LAN, "", WAN_OK, true, true, false);
        eq("只用内网+没填内网 url 空（不偷偷走公网）", p.url, "");
        eq("只用内网+没填内网 line", p.line, "没有可用地址");
        ok("只用内网+没填内网 source 说明", p.source.contains("还没填内网地址"));

        // ---------- 手动「只用公网」：即使连着 WiFi 也走公网
        p = RoutePolicy.decide(RoutePolicy.WAN, LAN_OK, WAN_OK, true, true, false);
        eq("只用公网+WiFi 走公网", p.url, WAN_OK);
        eq("只用公网 line", p.line, "走公网");
        eq("只用公网 source", p.source, "手动 · 只用公网");
        ok("只用公网 标记公网", p.wan);
        ok("只用公网 不是自动", !p.auto);

        p = RoutePolicy.decide(RoutePolicy.WAN, LAN_OK, "", true, true, false);
        eq("只用公网+没填公网 url 空", p.url, "");
        ok("只用公网+没填公网 source 说明", p.source.contains("还没填公网地址"));

        // ---------- label()：卡片/顶栏文案，且**绝不能含地址**
        RoutePolicy.Pick lp = RoutePolicy.decide(RoutePolicy.AUTO, LAN_OK, WAN_OK, true, true, false);
        eq("label 合成", RoutePolicy.label(lp), "走内网（自动 · 已连 WiFi）");
        ok("label 不含地址", RoutePolicy.label(lp).indexOf("192.168") < 0
                && RoutePolicy.label(lp).indexOf("ws://") < 0);
        eq("label(null)", RoutePolicy.label(null), "");
        RoutePolicy.Pick wp = RoutePolicy.decide(RoutePolicy.WAN, LAN_OK, WAN_OK, true, true, false);
        ok("label 手动档不含地址", RoutePolicy.label(wp).indexOf("wss://") < 0);

        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) System.exit(1);
    }
}
