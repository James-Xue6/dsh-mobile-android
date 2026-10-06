// AddressSync（「连上内网时同步公网地址」的取舍规则）的 JVM 断言。
//
// 为什么值得单独测：这段决定了「会不会动用户手里的地址」。做错的两种后果都不轻 ——
//   ① 该更新时不更新：电脑重启后出门，手机拿着死地址连不上，还得回家扫码；
//   ② 不该更新时更新：用户自己填的固定地址（Tailscale / 自建 wss）被悄悄覆盖，
//      表现成「昨天还能用，今天莫名其妙连不上」，且极难排查。
// 真机上每试一种组合都要重启电脑或开关 WiFi，只能靠这里穷举。
//
// 运行（仓库根目录，Windows；输出目录用仓库外，不要落进仓库）：
//   javac -encoding UTF-8 -d "$env:TEMP\addrsync-test"
//         src/com/dsh/mobile/net/AddressSync.java src/com/dsh/mobile/net/LanAddress.java
//         harness/src/AddressSyncTest.java
//   java -Dstdout.encoding=UTF-8 -cp "$env:TEMP\addrsync-test" AddressSyncTest
// 期望输出末行：通过 34 项，失败 0 项
//
// 它断言的是**仓库里的真实源码**（src/com/dsh/mobile/net/AddressSync.java），不是副本。

import com.dsh.mobile.net.AddressSync;
import com.dsh.mobile.net.LanAddress;
import java.util.Arrays;

public final class AddressSyncTest {

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

    // 合成地址（不是任何真实地址，只是格式正确的样例）
    private static final String LAN_HOME = "ws://192.168.1.14:3091/ws/mobile";
    private static final String LAN_HOME_OLD = "ws://192.168.1.9:3091/ws/mobile";
    private static final String LAN_OFFICE = "ws://10.0.0.7:3091/ws/mobile";
    private static final String LAN_VIRTUAL = "ws://172.30.16.1:3091/ws/mobile";
    private static final String LAN_APIPA = "ws://169.254.212.74:3091/ws/mobile";
    private static final String WAN_OLD_TUNNEL = "wss://old-name-abc.trycloudflare.com/ws/mobile";
    private static final String WAN_NEW_TUNNEL = "wss://new-name-xyz.trycloudflare.com/ws/mobile";
    private static final String WAN_CUSTOM = "wss://gateway.example.invalid/ws/mobile";
    private static final String WAN_TAILSCALE = "ws://100.101.102.103:3091/ws/mobile";
    private static final String LOCAL_LOOPBACK = "ws://127.0.0.1:19387/ws/mobile";
    private static final String GW = "81e5ed88-3f1b-4206-8433-1edb5cf38ab4";

    public static void main(String[] args) {
        System.out.println("[AddressSync] 公网地址同步取舍");

        // ---------- isEphemeralWan：只有临时隧道域名才可被替换
        ok("trycloudflare 判为临时", AddressSync.isEphemeralWan(WAN_OLD_TUNNEL));
        ok("大小写不敏感", AddressSync.isEphemeralWan("wss://X.TryCloudFlare.com/ws/mobile"));
        ok("固定域名不是临时", !AddressSync.isEphemeralWan(WAN_CUSTOM));
        ok("空值不是临时", !AddressSync.isEphemeralWan(null) && !AddressSync.isEphemeralWan(""));

        // ---------- isPublicUrl：公网侧判据
        ok("域名是公网", AddressSync.isPublicUrl(WAN_CUSTOM));
        ok("隧道域名是公网", AddressSync.isPublicUrl(WAN_NEW_TUNNEL));
        ok("公网 IP 是公网", AddressSync.isPublicUrl("wss://8.8.8.8:3091/ws/mobile"));
        ok("192.168 不是公网", !AddressSync.isPublicUrl(LAN_HOME));
        ok("10.x 不是公网", !AddressSync.isPublicUrl(LAN_OFFICE));
        ok("172.30 虚拟网卡两边都不是", !AddressSync.isPublicUrl(LAN_VIRTUAL));
        ok("APIPA 不是公网", !AddressSync.isPublicUrl(LAN_APIPA));
        ok("CGNAT(100.64/10) 不是公网", !AddressSync.isPublicUrl(WAN_TAILSCALE));
        ok("回环不是公网", !AddressSync.isPublicUrl(LOCAL_LOOPBACK));
        ok("localhost 不是公网", !AddressSync.isPublicUrl("ws://localhost:3091/ws/mobile"));
        ok("空值不是公网", !AddressSync.isPublicUrl("") && !AddressSync.isPublicUrl(null));

        // ---------- 主用例 ①：电脑重启后隧道域名变了，在家连内网时把它同步回来
        AddressSync.Result r = AddressSync.decide(GW, GW, LAN_HOME, WAN_OLD_TUNNEL,
                Arrays.asList(LAN_HOME, WAN_NEW_TUNNEL), WAN_NEW_TUNNEL);
        ok("① 该更新：标记 changed", r.changed);
        eq("① 该更新：公网地址换成新域名", r.wanUrl, WAN_NEW_TUNNEL);
        eq("① 该更新：内网地址保持不变", r.lanUrl, LAN_HOME);
        ok("① 该更新：说明里写明是隧道域名变化", r.note.contains("隧道域名变化"));

        // ---------- 主用例 ②：用户自己填的固定地址 —— 一行都不许动
        r = AddressSync.decide(GW, GW, LAN_HOME, WAN_CUSTOM,
                Arrays.asList(LAN_HOME, WAN_NEW_TUNNEL), WAN_NEW_TUNNEL);
        ok("② 自定义公网地址：changed 为 false", !r.changed);
        eq("② 自定义公网地址：原样保留", r.wanUrl, WAN_CUSTOM);

        // Tailscale 地址（100.64/10）同样属于用户自己的决定，不能被隧道地址顶掉
        r = AddressSync.decide(GW, GW, LAN_HOME, WAN_TAILSCALE,
                Arrays.asList(LAN_HOME, WAN_NEW_TUNNEL), WAN_NEW_TUNNEL);
        ok("② Tailscale 地址不被覆盖", !r.changed && WAN_TAILSCALE.equals(r.wanUrl));

        // ---------- ③：公网槽位空着 —— 补上
        r = AddressSync.decide(GW, GW, LAN_HOME, "",
                Arrays.asList(LAN_HOME, WAN_NEW_TUNNEL), WAN_NEW_TUNNEL);
        ok("③ 空槽位：changed", r.changed);
        eq("③ 空槽位：补上公网地址", r.wanUrl, WAN_NEW_TUNNEL);
        ok("③ 空槽位：说明写「补充」", r.note.contains("补充"));

        // ---------- ④：电脑换了网段（家里 192.168 → 公司 10.x）
        r = AddressSync.decide(GW, GW, LAN_HOME_OLD, WAN_CUSTOM,
                Arrays.asList(LAN_OFFICE, WAN_CUSTOM), WAN_CUSTOM);
        ok("④ 内网地址跟随电脑变化", r.changed);
        eq("④ 内网地址换成公司网段", r.lanUrl, LAN_OFFICE);
        eq("④ 自定义公网地址不动", r.wanUrl, WAN_CUSTOM);

        // ---------- ⑤：网关只给了虚拟网卡地址 —— 宁可不填，也不给假地址
        r = AddressSync.decide(GW, GW, LAN_HOME_OLD, WAN_CUSTOM,
                Arrays.asList(LAN_VIRTUAL, WAN_CUSTOM), WAN_CUSTOM);
        ok("⑤ 虚拟网卡地址不被当成内网", !r.changed && LAN_HOME_OLD.equals(r.lanUrl));

        // ---------- ⑥：网关报的 publicUrl 是本机地址（只对电脑自己有效）
        r = AddressSync.decide(GW, GW, LAN_HOME, "",
                Arrays.asList(LAN_HOME, WAN_NEW_TUNNEL), LOCAL_LOOPBACK);
        eq("⑥ 跳过没用的本机 publicUrl，改用 endpoints 里的真地址", r.wanUrl, WAN_NEW_TUNNEL);

        // …而 endpoints 里也没有公网地址时，就不填
        r = AddressSync.decide(GW, GW, LAN_HOME, "",
                Arrays.asList(LAN_HOME), LOCAL_LOOPBACK);
        ok("⑥ 没有可用公网地址时不乱填", !r.changed && "".equals(r.wanUrl));

        // ---------- ⑦：换了另一台电脑（gatewayId 不同）—— 一个字段都不改
        r = AddressSync.decide("11111111-2222-3333-4444-555555555555", GW,
                LAN_HOME_OLD, WAN_OLD_TUNNEL,
                Arrays.asList(LAN_OFFICE, WAN_NEW_TUNNEL), WAN_NEW_TUNNEL);
        ok("⑦ 网关身份不一致：不更新", !r.changed);
        eq("⑦ 网关身份不一致：内网保持原值", r.lanUrl, LAN_HOME_OLD);
        eq("⑦ 网关身份不一致：公网保持原值", r.wanUrl, WAN_OLD_TUNNEL);

        // ---------- ⑧：老数据没有 gatewayId —— 不能因为"没法核对身份"就罢工
        r = AddressSync.decide("", GW, LAN_HOME, WAN_OLD_TUNNEL,
                Arrays.asList(LAN_HOME, WAN_NEW_TUNNEL), WAN_NEW_TUNNEL);
        ok("⑧ 一侧 id 为空时照常同步", r.changed && WAN_NEW_TUNNEL.equals(r.wanUrl));

        // ---------- ⑨：什么都没变 → 不写存储
        r = AddressSync.decide(GW, GW, LAN_HOME, WAN_NEW_TUNNEL,
                Arrays.asList(LAN_HOME, WAN_NEW_TUNNEL), WAN_NEW_TUNNEL);
        ok("⑨ 全都一致：changed 为 false", !r.changed);
        eq("⑨ 全都一致：说明为空", r.note, "");

        // ---------- ⑩：空输入不炸
        r = AddressSync.decide(null, null, null, null, (String[]) null, null);
        ok("⑩ 全是 null：不崩且不更新", !r.changed && "".equals(r.lanUrl) && "".equals(r.wanUrl));
        r = AddressSync.decide(GW, GW, "", "", new String[0], "");
        ok("⑩ 全是空串：不崩且不更新", !r.changed);

        // ---------- ⑪ lanHostForHttp：取「同一台电脑的局域网 IP」这层胶水（含端口陷阱）
        eq("⑪ 带端口的 ws 地址 → 取到纯 IP", AddressSync.lanHostForHttp(LAN_HOME), "192.168.1.14");
        eq("⑪ 隧道域名 → 取不到内网 IP", AddressSync.lanHostForHttp(WAN_NEW_TUNNEL), "");
        eq("⑪ 虚拟网卡地址 → 不算可用内网", AddressSync.lanHostForHttp(LAN_VIRTUAL), "");
        eq("⑪ 空地址 → 空串", AddressSync.lanHostForHttp(""), "");
        // 这一条专盯踩过的真机坑（2026-10-05）：带端口的字符串喂给可用性判据必须是 false，
        // 所以调用方一定要先过 lanHostForHttp / LanAddress.hostOf 把端口剥掉。
        ok("⑪ 踩过的坑：带端口的字符串不是可用 IP（故必须先剥端口）",
                !LanAddress.isUsableLanAddress("192.168.1.14:3091"));

        System.out.println();
        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) System.exit(1);
    }
}
