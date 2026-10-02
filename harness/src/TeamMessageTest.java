import com.dsh.mobile.model.MessageSource;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 「专家团输出在 App 里点开看不到内容」的判据回归。
 *
 * 输入是真实会话日志里导出的事件（real-events.json），两种线上形态都造出来：
 *   live 帧  —— 网关 buildWireEvent 白名单内，source 是字符串（网关只透传 d.source.kind，index.mjs:289）
 *   history  —— 会话快照/历史，source 是对象（宿主原始事件，index.mjs:1145 不过白名单）
 * 分别喂给「改前的判据」（git HEAD 原样抄）与「改后的判据」（src 里真正发布的那份 MessageSource）。
 *
 * 判定：
 *   delegation（team-message / agent-message）—— 改后两种形态都必须「显示」，且改前 history 形态必须「丢弃」（证明 bug 真实存在）
 *   注入类（runtime-context / tool-jobs / skill-catalog）—— 必须两种形态都「丢弃」
 *
 * 运行（args[0]=事件 JSON，args[1]=报告输出路径；事件 JSON 从自己的会话日志导出，不要入库）：
 *   javac -encoding UTF-8 -d out -cp harness/lib/json-20240303.jar \
 *         harness/src/TeamMessageTest.java src/com/dsh/mobile/model/MessageSource.java
 *   java -cp "out;harness/lib/json-20240303.jar" TeamMessageTest events.json report.txt
 */
public final class TeamMessageTest {

    // ===== 改前判据：git HEAD src/com/dsh/mobile/MainActivity.java 原样 =====
    static boolean oldIsInjectedContext(JSONObject payload, String text) {
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

    // ===== 改后判据：App 里跑的 MessageSource（MainActivity.isInjectedContext 转发过去）=====
    static boolean newIsInjectedContext(JSONObject payload, String text) {
        return MessageSource.isInjected(MessageSource.kindOf(payload), text);
    }

    /** 网关 buildWireEvent 对 user/message 的 live 形态（网关 lib/index.mjs:283-294）。 */
    static JSONObject liveWire(JSONObject data) {
        JSONObject w = new JSONObject();
        w.put("type", "user/message");
        w.put("text", textOf(data));
        JSONObject src = data.optJSONObject("source");
        if (src != null && src.has("kind")) w.put("source", src.optString("kind"));
        if (data.has("id")) {
            JSONObject raw = new JSONObject();
            raw.put("id", data.optString("id"));
            w.put("raw", raw);
        }
        return w;
    }

    static String textOf(JSONObject data) {
        JSONArray content = data.optJSONArray("content");
        if (content == null) return data.optString("text", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject b = content.optJSONObject(i);
            if (b == null) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(b.optString("text", ""));
        }
        return sb.toString();
    }

    static String hostKind(JSONObject data) {
        JSONObject src = data.optJSONObject("source");
        return src == null ? "" : src.optString("kind", "");
    }

    static PrintWriter out;
    static int fail = 0;

    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        JSONObject all = new JSONObject(raw);
        out = new PrintWriter(Files.newBufferedWriter(Paths.get(args[1]), StandardCharsets.UTF_8));

        p("== user/message：改前 vs 改后（样本取自真实 session.v4.jsonl.zstd）==");
        p("");
        p(String.format("%-24s %-12s %-10s %-10s %s", "真实事件 source.kind", "到达形态", "改前", "改后", "判定"));
        p(String.format("%-24s %-12s %-10s %-10s %s", "--------------------", "--------", "----", "----", "----"));

        String[] keys = {"teamUserMessage", "agentUserMessage",
                "injectedRuntimeContext", "injectedToolJobs", "injectedSkillCatalog"};
        for (String k : keys) {
            JSONObject ev = all.optJSONObject(k);
            if (ev == null) { p("(缺少样本 " + k + ")"); continue; }
            JSONObject data = ev.optJSONObject("data");
            String kind = hostKind(data);
            boolean delegation = MessageSource.isDelegation(kind);

            boolean oldH = oldIsInjectedContext(data, textOf(data));                 // true = 会被丢弃
            boolean newH = newIsInjectedContext(data, textOf(data));
            JSONObject wire = liveWire(data);
            String wireText = wire.optString("text", "");
            boolean oldL = oldIsInjectedContext(wire, wireText);
            boolean newL = newIsInjectedContext(wire, wireText);

            boolean ok;
            String note;
            if (delegation) {
                ok = !newH && !newL && oldH;
                note = ok ? "OK：改前 history 丢 / 改后两种形态都显示" : "!! 不符合预期";
            } else {
                ok = newH && newL;
                note = ok ? "OK：注入项两种形态都丢" : "!! 不符合预期";
            }
            if (!ok) fail++;

            p(String.format("%-24s %-12s %-10s %-10s %s", kind, "history", yn(oldH), yn(newH), note));
            p(String.format("%-24s %-12s %-10s %-10s %s", "", "live 帧", yn(oldL), yn(newL),
                    (oldL != newL ? "改前 live 漏网 → 改后一并丢掉（一致性修复）" : "")));
        }

        // ---- 去重：同一份内容，team/message/queued 与 user/message(live) 必须认出同一个 messageId ----
        p("");
        p("== 去重键：同一份内容的三种到达方式必须算同一条 ==");
        JSONObject queued = all.optJSONObject("teamMessageQueued");
        JSONObject tm = all.optJSONObject("teamUserMessage");
        JSONObject qmsg = queued.optJSONObject("data").optJSONObject("message");
        JSONObject tdata = tm.optJSONObject("data");

        String idFromQueued = qmsg.optString("id", "");
        String tText = textOf(tdata);
        String idFromHistory = MessageSource.messageId(tdata.optJSONObject("source"), tText);
        String idFromLive = MessageSource.messageId(null, liveWire(tdata).optString("text", ""));
        p("team/message/queued   messageId = " + idFromQueued);
        p("user/message(history) messageId = " + idFromHistory);
        p("user/message(live 帧) messageId = " + idFromLive);
        boolean same = idFromQueued.equals(idFromHistory) && idFromQueued.equals(idFromLive);
        p("三者一致 = " + same);
        if (!same) fail++;

        // ---- 正文清洗：机器前缀必须剥掉，且不能把正文吃掉 ----
        p("");
        p("== 正文清洗（live 形态最脏：前缀里带 messageId 和发送者）==");
        JSONObject wireTm = liveWire(tdata);
        String liveBody = MessageSource.body(wireTm.optString("text", ""));
        String liveSender = MessageSource.sender(null, wireTm.optString("text", ""));
        String histSender = MessageSource.sender(tdata.optJSONObject("source"), tText);
        p("live    sender = " + liveSender);
        p("history sender = " + histSender);
        p("live   body 前 60 字 = " + head(liveBody, 60));
        p("原始正文长度 " + tText.length() + " -> 清洗后 " + liveBody.length() + "（剥掉前缀，正文没被吃掉）");
        if (liveBody.startsWith("Team message")) fail++;
        if (liveBody.length() < 100) fail++;
        if (!liveSender.equals(histSender)) fail++;

        // ---- agent-message 也走同一套 ----
        JSONObject am = all.optJSONObject("agentUserMessage").optJSONObject("data");
        String amText = textOf(am);
        p("");
        p("== 子代理回传（source.kind=agent-message）同样处理 ==");
        p("sender = " + MessageSource.sender(am.optJSONObject("source"), amText));
        p("body 前 60 字 = " + head(MessageSource.body(amText), 60));
        if (MessageSource.body(amText).startsWith("Agent ")) fail++;

        // ---- team/member 的就位文案 ----
        JSONObject member = all.optJSONObject("teamMember").optJSONObject("data").optJSONObject("member");
        p("");
        p("== team/member（只有历史形态才拿得到 data）==");
        p("id = " + member.optString("id", ""));
        p("就位文案 = 专家团成员就位：" + member.optString("description", "")
                + "  (phase=" + member.optString("phase", "") + ")");

        // ---- team/* 实时帧：data 被白名单丢掉 → 走尾部补拉 ----
        p("");
        p("== team/* 实时帧（网关白名单外，data 被 buildWireEvent default 分支丢掉）==");
        for (String t : new String[]{"team/task", "team/member", "team/message/queued"}) {
            JSONObject wire = new JSONObject();
            wire.put("type", t);
            p(t + "  实际字段数 = " + wire.length() + " → 命中 payload.length()<=1 && isTeamEvent → 尾部补拉");
        }

        p("");
        p(fail == 0 ? "RESULT: PASS" : ("RESULT: FAIL (" + fail + ")"));
        out.flush();
        out.close();
        System.out.println(fail == 0 ? "RESULT: PASS" : ("RESULT: FAIL (" + fail + ")"));
    }

    static String yn(boolean injected) { return injected ? "丢弃" : "显示"; }

    static void p(String s) { out.println(s); }

    static String head(String s, int n) {
        String one = s.replace('\n', ' ');
        return one.length() > n ? one.substring(0, n) + "…" : one;
    }
}
