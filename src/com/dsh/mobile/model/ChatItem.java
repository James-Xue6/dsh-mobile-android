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
    /**
     * 专家团成员 / 子代理回传的正文卡片。
     * 这类正文是 user/message（source.kind = team-message / agent-message），
     * 但说话的不是用户，不能渲染成右侧蓝色用户气泡。
     */
    public static final int AGENT = 7;
    /**
     * 简洁模式下的「过程摘要」行：把同一段里的一串工具调用压成一句话
     * （PC 端工作台同款，例如「执行了命令」「已读取文件，执行了命令」）。
     * 这一行由 {@link com.dsh.mobile.ui.ConversationView} 在简洁模式里现算，
     * 完整模式永远不会出现（完整模式仍然逐条显示工具参数）。
     */
    public static final int STEP = 8;

    public int kind;
    public String key = "";
    public String text = "";
    /** AGENT 卡片的发言人（专家团成员名 / 子代理会话 id）。 */
    public String agentName = "";
    public String reasoning = "";
    public String toolName = "";
    public String toolPreview = "";
    public boolean toolError;
    public boolean toolRunning;
    /** STEP 摘要行：这一段里还有工具在跑（摘要行要显示「正在运行命令 · pwsh…」）。 */
    public boolean stepRunning;
    /** STEP 摘要行：这一段里有工具失败了（摘要行按错误配色显示，不能被简洁模式吞掉）。 */
    public boolean stepError;
    public long time;
    public boolean streaming;

    /** APPROVAL / QUESTION 卡片载荷。 */
    public String rpcId = "";
    public String approvalId = "";
    public String callId = "";
    public String reason = "";
    public boolean resolved;
    public String resolvedOutcome = "";
    /**
     * 已发出、正在等电脑端回执（评审 P0-3）。
     * 这段窗口里卡片显示「已发送，等待电脑确认…」且不出现按钮：既不能显示 ✓
     * （回执没到 = 可能进了黑洞），也不能让用户重复点。回执到了置 false 并 resolved=true；
     * 看门狗超时则回滚为未处理。
     */
    public boolean pendingConfirm;
    /**
     * 非空表示这次操作根本没发出去（断线时点了批准/提交）。
     * 卡片会显示这条提示但保留按钮，用户恢复连接后可重试（评审 P0-3）。
     */
    public String sendError = "";
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
