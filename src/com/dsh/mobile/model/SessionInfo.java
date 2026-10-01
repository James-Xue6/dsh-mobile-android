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
    /** 0=无 1=有提问待回答 2=有待审批 */
    public int pending = 0;
    public JSONObject raw = new JSONObject();

    public String display() {
        if (title != null && !title.trim().isEmpty()) return title.trim();
        if (cwd != null && !cwd.trim().isEmpty()) {
            String c = cwd.replace('\\', '/');
            int i = c.lastIndexOf('/');
            String tail = i >= 0 ? c.substring(i + 1) : c;
            if (!tail.isEmpty()) return tail;
        }
        return "新对话";
    }
}
