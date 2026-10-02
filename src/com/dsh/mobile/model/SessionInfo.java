package com.dsh.mobile.model;

import org.json.JSONObject;

/** 会话列表条目（对应协议 sessions.items[]）。 */
public final class SessionInfo {
    public String id = "";
    public String title = "";
    public String cwd = "";
    public String agentPreset = "";
    public long updatedAt;
    public boolean running;
    public boolean blank;
    public boolean archived;
    /**
     * 宿主列表项里的父子关系（网关原样透传，见 session_list_result 契约里的
     * parentSessionId / origin）。子智能体与专家团成员的会话会指向父会话。
     */
    public String parentSessionId = "";
    /** "subagent" = 子智能体 / 专家团派生会话；普通会话为空。 */
    public String origin = "";
    /** 0=无 1=有提问待回答 2=有待审批 */
    public int pending = 0;

    // ---- 列表展示用的派生字段（由 MainActivity.buildRows 每轮重算，不参与持久化）
    /** 缩进层级：0=顶层会话，1+=子会话（挂在父会话下面）。 */
    public int childDepth = 0;
    /** 该会话名下的子会话数量（仅顶层行有意义，0 表示没有）。 */
    public int childCount = 0;
    /** 子会话当前是否展开。 */
    public boolean expanded = false;
    /** 同工作区里有多个「未命名会话」时，用它编号区分；0 表示不编号。 */
    public int untitledSeq = 0;

    public JSONObject raw = new JSONObject();

    /** 这是子智能体/专家团派生会话吗。 */
    public boolean isSubagent() {
        return "subagent".equals(origin);
    }

    public String display() {
        if (title != null && !title.trim().isEmpty()) return title.trim();
        // 兜底绝不拿工作区名冒充标题：真机上那会让一整列会话看起来都叫工作区名，
        // 用户根本分不清哪条是哪条。标题没加载出来时就说「未命名会话」。
        return "未命名会话";
    }

    /** 列表卡片上的标题（未命名时带序号，便于同工作区多条区分）。 */
    public String displayForList() {
        String base = display();
        return untitledSeq > 0 ? base + " " + untitledSeq : base;
    }
}
