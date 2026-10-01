package com.dsh.mobile.model;

/** 对话里的一行（用户 / 助手 / 工具调用 / 系统提示）。 */
public final class ChatItem {

    public static final int USER = 0;
    public static final int ASSISTANT = 1;
    public static final int TOOL = 2;
    public static final int SYSTEM = 3;
    /** 需要用户决策的交互卡片（审批 / 提问）。 */
    public static final int APPROVAL = 4;
    public static final int QUESTION = 5;
    /** 交付物卡片（agent 的 present 工具产出的文件列表）。 */
    public static final int FILES = 6;

    public int kind;
    public String key = "";
    public String text = "";
    public String reasoning = "";
    public String toolName = "";
    public String toolPreview = "";
    public boolean toolError;
    public boolean toolRunning;
    public long time;
    public boolean streaming;

    /** APPROVAL / QUESTION 卡片载荷。 */
    public String rpcId = "";
    public String approvalId = "";
    public String callId = "";
    public String reason = "";
    public boolean resolved;
    public String resolvedOutcome = "";
    public org.json.JSONArray questions;
    /** FILES 卡片的文件列表：[{description, path}] */
    public org.json.JSONArray files;
    /** FILES 卡片里某个文件的下载状态文案（null 表示未下载）。 */
    public String downloadState;
    /** 本条消息引用的图片附件 id（protocol: content[].attachment.attachmentId）。 */
    public final java.util.List<String> attachmentIds = new java.util.ArrayList<>();
    /** 已拉取并解码好的图片（按 attachmentId 顺序）。 */
    public final java.util.List<android.graphics.Bitmap> images = new java.util.ArrayList<>();
    /** 提问卡片的用户选择状态（跨 ListView 回收保留）。 */
    public final java.util.HashMap<String, java.util.LinkedHashSet<String>> picked = new java.util.HashMap<>();
    public final java.util.HashMap<String, String> typed = new java.util.HashMap<>();

    public static ChatItem of(int kind, String key, String text) {
        ChatItem it = new ChatItem();
        it.kind = kind;
        it.key = key;
        it.text = text == null ? "" : text;
        it.time = System.currentTimeMillis();
        return it;
    }
}
