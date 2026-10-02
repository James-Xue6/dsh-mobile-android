package com.dsh.mobile.model;

import org.json.JSONObject;

/**
 * user/message 的 source 分类，以及「专家团成员 / 子代理回传」正文的识别与清洗。
 *
 * 为什么单独成类：这段判据原先只在 MainActivity.isInjectedContext 里，规则是
 * 「source.kind 不是 user 就当作注入上下文丢掉」。专家团成员与子代理回传的正文
 * 也是 user/message，它们的 source.kind 是 team-message / agent-message，
 * 于是被一并丢掉 —— 而且只在历史形态下丢：
 *   live 帧（网关 buildWireEvent 白名单内）：source = "team-message"（字符串）
 *     -> optJSONObject("source") 为 null，过滤器不生效，实时能看到；
 *   history / session-snapshot：事件是宿主原始记录，source = {kind:"team-message"}（对象）
 *     -> 过滤器生效，整段消失。
 * 表现就是「团队跑的时候看得见，一重开会话就什么都没有」。
 *
 * 判据是纯字符串逻辑，放这里可以在 JVM 上直接对真实事件 JSON 回归，不必上真机。
 */
public final class MessageSource {

    /** 专家团成员回传（宿主 user/message 的 source.kind）。 */
    public static final String TEAM = "team-message";
    /** 子代理回传（宿主 user/message 的 source.kind）。 */
    public static final String AGENT = "agent-message";

    /** 宿主给的真实用户输入。 */
    private static final String USER = "user";

    private MessageSource() { }

    /**
     * 取出 source.kind，兼容两种线上形态：
     *   live 帧：source 是字符串（网关只透传 d.source.kind，见网关 lib/index.mjs:289）
     *   history / snapshot：source 是对象
     * 没有 source 或形态不认识时返回空串。
     */
    public static String kindOf(JSONObject payload) {
        if (payload == null) return "";
        Object s = payload.opt("source");
        if (s instanceof JSONObject) return ((JSONObject) s).optString("kind", "");
        if (s instanceof String) return (String) s;
        return "";
    }

    /** 专家团成员 / 子代理回传的正文：这是内容，不是注入上下文，必须展示。 */
    public static boolean isDelegation(String kind) {
        return TEAM.equals(kind) || AGENT.equals(kind);
    }

    /**
     * 是否属于「DSH 注入的上下文 / 系统提醒」，手机上不展示。
     *
     * 规则：有 source.kind 时，只有 user / team-message / agent-message 是真正的正文，
     * 其余（time-context、runtime-context、skill-catalog、plugin:*、tool-jobs、goal、
     * user-approval、subagent-settled …）都是注入项，一律丢掉。
     * 没有 kind 时退回正文前缀判据（老网关不保证带 source）。
     */
    public static boolean isInjected(String kind, String text) {
        if (kind != null && !kind.isEmpty() && !USER.equals(kind) && !isDelegation(kind)) {
            return true;
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

    /**
     * 这条回传的 messageId（用于去重：同一份内容既可能以 team/message/queued 到达，
     * 也可能以 user/message(source.kind=team-message) 到达）。
     * 历史形态从 source.messageId 取；live 形态只能从正文前缀里认。
     */
    public static String messageId(JSONObject source, String text) {
        if (source != null) {
            String id = source.optString("messageId", "");
            if (!id.isEmpty()) return id;
        }
        return prefixField(TEAM_PREFIX, text, 1);
    }

    /**
     * 发送方显示名：历史形态用 source.senderName / senderSessionId，
     * live 形态从正文前缀里认。
     */
    public static String sender(JSONObject source, String text) {
        if (source != null) {
            String name = source.optString("senderName", "");
            if (!name.isEmpty()) return name;
            String sid = source.optString("senderSessionId", "");
            if (!sid.isEmpty()) return sid;
        }
        String team = prefixField(TEAM_PREFIX, text, 2);
        if (!team.isEmpty()) return team;
        return prefixField(AGENT_PREFIX, text, 1);
    }

    /** 去掉 "Team message <id> from <name>:" / "Agent <id> sent a message: " 机器前缀。 */
    public static String body(String text) {
        if (text == null) return "";
        String s = TEAM_PREFIX.matcher(text).replaceFirst("");
        if (s.equals(text)) s = AGENT_PREFIX.matcher(text).replaceFirst("");
        return s.trim();
    }

    // ------------------------------------------------------------ 正文前缀

    private static final java.util.regex.Pattern TEAM_PREFIX =
            java.util.regex.Pattern.compile("^Team message (\\S+) from ([^:：]+)\\s*[:：]\\s*");
    private static final java.util.regex.Pattern AGENT_PREFIX =
            java.util.regex.Pattern.compile("^Agent (\\S+) sent a message\\s*[:：]\\s*");

    private static String prefixField(java.util.regex.Pattern p, String text, int group) {
        if (text == null) return "";
        java.util.regex.Matcher m = p.matcher(text);
        return m.find() ? m.group(group) : "";
    }
}
