// LanScan / LanAddress 纯逻辑断言（JVM 直跑真实源码，不依赖 Android SDK）。
//
// 覆盖「地址自动重新发现」这条链上最容易错、又最难在真机上定位的三段纯逻辑：
//   ① 扫哪些 IP（网段枚举：前缀收窄、排除网络号/广播号/自己、数量上限）；
//   ② 认不认这台 IP 是 DSH 面板（身份标记判据）；
//   ③ 拿扫描到的 IP 拼新的内网 WebSocket 地址（端口/路径只沿用内网模板，绝不拿公网模板推）。
//
// 用法（必须用 Android Studio 的 JBR，仓库里另一个 JDK 是坏的）：
//   $env:JAVA_HOME='D:\AndroidStudio\jbr'; pwsh -File harness/run-lanscan-test.ps1
//
// 加 DSH_LANSCAN_LIVE=1 会额外对 192.168.2.29:8099 做一次真探测（需要电脑端 DSH 在跑）。

import com.dsh.mobile.net.LanAddress;
import com.dsh.mobile.net.LanScan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LanScanTest {

    private static int failed = 0;
    private static int passed = 0;

    private static void ok(String what, boolean cond) {
        if (cond) { passed++; System.out.println("  [ok]   " + what); }
        else { failed++; System.out.println("  [FAIL] " + what); }
    }

    private static void eq(String what, Object want, Object got) {
        boolean same = want == null ? got == null : want.equals(got);
        if (same) { passed++; System.out.println("  [ok]   " + what + " = " + got); }
        else { failed++; System.out.println("  [FAIL] " + what + "  期望=" + want + " 实际=" + got); }
    }

    public static void main(String[] args) {
        System.out.println("== ① 网段枚举 candidates() ==");
        List<String> c24 = LanScan.candidates(new LanScan.LocalNet("192.168.2.51", 24));
        eq("/24 候选数（254 个地址去掉自己）", 253, c24.size());
        ok("/24 含电脑 192.168.2.29", c24.contains("192.168.2.29"));
        ok("/24 不含自己 192.168.2.51", !c24.contains("192.168.2.51"));
        ok("/24 不含网络号 192.168.2.0", !c24.contains("192.168.2.0"));
        ok("/24 不含广播号 192.168.2.255", !c24.contains("192.168.2.255"));
        ok("/24 首个是 .1", "192.168.2.1".equals(c24.get(0)));
        ok("/24 末个是 .254", "192.168.2.254".equals(c24.get(c24.size() - 1)));

        List<String> c16 = LanScan.candidates(new LanScan.LocalNet("10.1.2.3", 16));
        eq("/16 收窄成自己那一段 /24（数量）", 253, c16.size());
        ok("/16 全部落在 10.1.2.*", allStartWith(c16, "10.1.2."));
        ok("/16 不含自己 10.1.2.3", !c16.contains("10.1.2.3"));

        List<String> c25 = LanScan.candidates(new LanScan.LocalNet("192.168.2.51", 25));
        eq("/25 候选数（.0~.127 去掉自己与网络/广播）", 125, c25.size());
        ok("/25 不含 .128 起的地址", !c25.contains("192.168.2.128"));
        ok("/25 含 .126", c25.contains("192.168.2.126"));

        List<String> c30 = LanScan.candidates(new LanScan.LocalNet("192.168.2.51", 30));
        eq("/30 候选数（.49/.50；.51 是自己）", 2, c30.size());
        ok("/30 = [.49, .50]", c30.contains("192.168.2.49") && c30.contains("192.168.2.50"));

        eq("非法 IP 的候选表为空", 0, LanScan.candidates(new LanScan.LocalNet("abc", 24)).size());
        eq("null 网段返回空表", 0, LanScan.candidates(null).size());

        List<LanScan.LocalNet> two = Arrays.asList(
                new LanScan.LocalNet("192.168.2.51", 24),
                new LanScan.LocalNet("192.168.2.51", 24));   // 故意重复
        eq("candidatesOf 跨网段去重", 253, LanScan.candidatesOf(two).size());

        System.out.println("\n== ② 哪些本机地址值得扫 isScannableLocalIpv4() ==");
        ok("192.168.2.51 可扫", LanScan.isScannableLocalIpv4("192.168.2.51"));
        ok("10.0.0.7 可扫", LanScan.isScannableLocalIpv4("10.0.0.7"));
        // 与 LanAddress 的分工：那个判据要挡掉「电脑上的虚拟网卡 172.16/12」，
        // 而手机自己真待在 172.20.x 办公网时那段是合法可扫的 —— 这里必须放行。
        ok("172.30.224.204 可扫（手机自己在办公网段时也要能扫）", LanScan.isScannableLocalIpv4("172.30.224.204"));
        ok("169.254.1.1 不可扫（APIPA）", !LanScan.isScannableLocalIpv4("169.254.1.1"));
        ok("127.0.0.1 不可扫（回环）", !LanScan.isScannableLocalIpv4("127.0.0.1"));
        ok("0.0.0.0 不可扫", !LanScan.isScannableLocalIpv4("0.0.0.0"));
        ok("224.0.0.1 不可扫（组播）", !LanScan.isScannableLocalIpv4("224.0.0.1"));
        ok("'abc' 不可扫", !LanScan.isScannableLocalIpv4("abc"));
        ok("null 不可扫", !LanScan.isScannableLocalIpv4(null));

        System.out.println("\n== ③ 面板身份判据 looksLikePanel() ==");
        ok("真实面板页命中", LanScan.looksLikePanel(
                "<!doctype html><title>DSH 掌上通 · 安装包</title><a href=\"/app.apk\">下载</a>"));
        ok("只有 /app.apk 也命中", LanScan.looksLikePanel("<a href=\"/app.apk\">x</a>"));
        ok("路由器登录页不命中", !LanScan.looksLikePanel("<html><title>TP-LINK</title></html>"));
        ok("空/null 不命中", !LanScan.looksLikePanel("") && !LanScan.looksLikePanel(null));

        System.out.println("\n== ④ IP 数值互转 parseIpv4/ipv4ToLong/longToIpv4 ==");
        eq("parseIpv4 正常", 4, LanScan.parseIpv4("192.168.2.29").length);
        ok("parseIpv4 拒绝三段", LanScan.parseIpv4("1.2.3") == null);
        ok("parseIpv4 拒绝 256", LanScan.parseIpv4("1.2.3.256") == null);
        ok("parseIpv4 拒绝 0x1f", LanScan.parseIpv4("0x1f.2.3.4") == null);
        eq("ipv4ToLong(192.168.2.29)", 3232236061L, LanScan.ipv4ToLong("192.168.2.29"));
        eq("longToIpv4 往返", "192.168.2.29", LanScan.longToIpv4(LanScan.ipv4ToLong("192.168.2.29")));
        eq("非法 IP -> -1", -1L, LanScan.ipv4ToLong("nope"));

        System.out.println("\n== ⑤ 端口/路径抠取 LanAddress.portOf/pathOf ==");
        eq("内网模板的端口", "3091", LanAddress.portOf("ws://192.168.2.29:3091/ws/mobile"));
        eq("公网模板没有显式端口", "", LanAddress.portOf("wss://abc.trycloudflare.com/ws/mobile"));
        eq("IPv6 字面量的端口", "3091", LanAddress.portOf("ws://[fe80::1]:3091/ws/mobile"));
        eq("路径", "/ws/mobile", LanAddress.pathOf("wss://abc.trycloudflare.com/ws/mobile"));
        eq("没有路径时为空", "", LanAddress.pathOf("wss://abc.trycloudflare.com"));

        System.out.println("\n== ⑥ 用扫到的 IP 拼新内网地址 lanUrlFor() ==");
        eq("沿用内网模板的端口与路径",
                "ws://192.168.2.30:3091/ws/mobile",
                LanAddress.lanUrlFor("192.168.2.30", "ws://192.168.2.29:3091/ws/mobile"));
        // 关键反例：公网模板（wss + 无端口）绝不能把 443 或 wss 带进内网地址
        eq("公网模板不参与推端口（退回默认 3091 + ws）",
                "ws://192.168.2.30:3091/ws/mobile",
                LanAddress.lanUrlFor("192.168.2.30", "wss://abc.trycloudflare.com/ws/mobile"));
        eq("模板为空时用默认值",
                "ws://10.0.0.5:3091/ws/mobile",
                LanAddress.lanUrlFor("10.0.0.5", ""));
        eq("自定义内网端口要沿用",
                "ws://192.168.2.30:4100/ws/mobile",
                LanAddress.lanUrlFor("192.168.2.30", "ws://192.168.2.29:4100/ws/mobile"));
        eq("IP 为空返回空串", "", LanAddress.lanUrlFor("", "ws://192.168.2.29:3091/ws/mobile"));

        System.out.println("\n== ⑦ 网关上报的 lanUrls 要过滤掉虚拟网卡 ==");
        List<String> fromGateway = Arrays.asList(
                "ws://172.30.224.204:3091/ws/mobile",     // 电脑上的虚拟网卡：手机路由不过去
                "ws://192.168.2.29:3091/ws/mobile");
        eq("pickLanUrl 挑到真实内网地址",
                "ws://192.168.2.29:3091/ws/mobile", LanAddress.pickLanUrl(fromGateway));
        eq("全是虚拟网卡时挑不出（返回空串，宁可不填）",
                "", LanAddress.pickLanUrl(Arrays.asList("ws://172.30.224.204:3091/ws/mobile")));

        String live = System.getenv("DSH_LANSCAN_LIVE");
        if ("1".equals(live)) {
            System.out.println("\n== ⑧ 真探测（DSH_LANSCAN_LIVE=1）：192.168.2.29:8099 ==");
            String body = LanScan.probePanel("192.168.2.29", LanScan.PANEL_PORT, 800);
            ok("探到 DSH 面板页", LanScan.looksLikePanel(body));
            String none = LanScan.probePanel("192.168.2.240", LanScan.PANEL_PORT, 300);
            ok("空 IP 探不到（不误判）", !LanScan.looksLikePanel(none));
        }

        System.out.println("\n通过 " + passed + " 项，失败 " + failed + " 项。");
        if (failed > 0) System.exit(1);
        System.out.println("全部通过 ✅");
    }

    private static boolean allStartWith(List<String> list, String prefix) {
        for (String s : list) if (!s.startsWith(prefix)) return false;
        return true;
    }
}
