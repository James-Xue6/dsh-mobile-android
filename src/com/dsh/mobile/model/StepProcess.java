package com.dsh.mobile.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「简洁模式」的回合摘要：把一串工具调用压成 PC 端工作台同款的**一句话**。
 *
 * 为什么要有这个类：手机切到「简洁」后仍然显示一大堆（思考块、工具参数、
 * 每条命令回显各占一行），而桌面端工作台把整个回合的过程折叠成一行
 * 「执行了命令」「已读取文件，执行了命令」这样的摘要。这里的判据与文案
 * 逐条对齐桌面端 bundle（`app.asar`，Electron 客户端），依据：
 *
 *  - 工具名 -> 类别：`lib/types/client/conversation-nodes/process-activity.js`
 *    的 `activity(name)`（app.asar:399154-399177）
 *  - 类别 -> 文案：i18n `message.stepProcess.*`（app.asar:394026-394070，中文）
 *  - 多类别合成一句话：`processTitle(summary, t)`（app.asar:390480-390496）
 *
 * 纯字符串逻辑、不依赖 Android，可像 {@link MessageSource} 一样在 JVM 上回归。
 */
public final class StepProcess {

    // 类别常量：与桌面端 activity() 的返回值一一对应
    public static final String THINKING  = "thinking";
    public static final String READ      = "read";
    public static final String READ_IMAGE = "readImage";
    public static final String SEARCH    = "search";
    public static final String WRITE     = "write";
    public static final String EDIT      = "edit";
    public static final String COMMANDS  = "commands";
    public static final String CODE      = "code";
    public static final String WEB_SEARCH = "webSearch";
    public static final String WEB_FETCH  = "webFetch";
    public static final String SUBAGENTS  = "subagents";
    public static final String PLAN       = "plan";
    public static final String QUESTIONS  = "questions";
    public static final String TOOLS      = "tools";

    private StepProcess() { }

    /**
     * 工具名 -> 类别。逐条照抄桌面端 `activity()`（app.asar:399154-399177），
     * 顺序保持与桌面端一致（先判 read，再 grep/glob，最后兜底 tools）。
     */
    public static String activity(String name) {
        if (name == null) return TOOLS;
        String n = name.trim();
        if (n.isEmpty()) return TOOLS;
        if ("read".equals(n)) return READ;
        if ("read_image".equals(n)) return READ_IMAGE;
        if ("grep".equals(n) || "glob".equals(n) || n.endsWith("_inspect")) return SEARCH;
        if ("write".equals(n)) return WRITE;
        if ("edit".equals(n) || "apply_patch".equals(n)) return EDIT;
        if ("bash".equals(n) || "pwsh".equals(n) || "exec_command".equals(n)
                || "write_stdin".equals(n) || n.startsWith("terminal_")) return COMMANDS;
        if ("run_code".equals(n)) return CODE;
        if ("web_search".equals(n)) return WEB_SEARCH;
        if ("web_fetch".equals(n)) return WEB_FETCH;
        if ("subagent".equals(n) || n.startsWith("subagent_")) return SUBAGENTS;
        if ("todo_write".equals(n) || "create_goal".equals(n)
                || "update_goal".equals(n) || "get_goal".equals(n)) return PLAN;
        if ("ask_user_question".equals(n) || "request_user_input".equals(n)) return QUESTIONS;
        return TOOLS;
    }

    /** 已完成文案：i18n `message.stepProcess.done.*`（app.asar:394053-394066）。 */
    public static String doneLabel(String kind) {
        if (READ.equals(kind)) return "已读取文件";
        if (READ_IMAGE.equals(kind)) return "已读取图片";
        if (SEARCH.equals(kind)) return "已搜索代码";
        if (WRITE.equals(kind)) return "已写入文件";
        if (EDIT.equals(kind)) return "修改了文件";
        if (COMMANDS.equals(kind)) return "执行了命令";
        if (CODE.equals(kind)) return "运行了代码";
        if (WEB_SEARCH.equals(kind)) return "已搜索网页";
        if (WEB_FETCH.equals(kind)) return "已访问网页";
        if (SUBAGENTS.equals(kind)) return "已协调子智能体";
        if (PLAN.equals(kind)) return "更新了计划";
        if (QUESTIONS.equals(kind)) return "向用户提出了问题";
        if (TOOLS.equals(kind)) return "已调用工具";
        return "已完成分析";
    }

    /** 进行中文案：i18n `message.stepProcess.*`（app.asar:394026-394039）。 */
    public static String runningLabel(String kind) {
        if (READ.equals(kind)) return "正在读取文件";
        if (READ_IMAGE.equals(kind)) return "正在读取图片";
        if (SEARCH.equals(kind)) return "正在搜索代码";
        if (WRITE.equals(kind)) return "正在写入文件";
        if (EDIT.equals(kind)) return "正在编辑文件";
        if (COMMANDS.equals(kind)) return "正在运行命令";
        if (CODE.equals(kind)) return "正在运行代码";
        if (WEB_SEARCH.equals(kind)) return "正在搜索网页";
        if (WEB_FETCH.equals(kind)) return "正在访问网页";
        if (SUBAGENTS.equals(kind)) return "正在协调子智能体";
        if (PLAN.equals(kind)) return "正在更新计划";
        if (QUESTIONS.equals(kind)) return "等待你的操作";
        if (TOOLS.equals(kind)) return "正在调用工具";
        return "正在分析请求";
    }

    /**
     * 把一组工具（按出现顺序的类别）压成一句话，对齐桌面端 `processTitle`：
     * 类别按「出现的次数」降序、次数相同按「首次出现的先后」；
     * 取前 3 类拼成 `已读取文件，执行了命令`，超过 3 类末尾加「等」。
     */
    public static String title(List<String> kindsInOrder) {
        if (kindsInOrder == null || kindsInOrder.isEmpty()) return "";
        List<String> order = new ArrayList<>();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (String k : kindsInOrder) {
            if (k == null) continue;
            if (!counts.containsKey(k)) order.add(k);
            Integer c = counts.get(k);
            counts.put(k, c == null ? 1 : c + 1);
        }
        if (order.isEmpty()) return "";
        // 稳定排序：先按次数降序，次数相同保持 first-appearance（order 的下标）
        List<String> ranked = new ArrayList<>(order);
        java.util.Collections.sort(ranked, new java.util.Comparator<String>() {
            @Override public int compare(String a, String b) {
                int d = counts.get(b) - counts.get(a);
                if (d != 0) return d;
                return order.indexOf(a) - order.indexOf(b);
            }
        });
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < ranked.size() && i < 3; i++) labels.add(doneLabel(ranked.get(i)));
        String first = labels.get(0);
        if (labels.size() == 1) return first;
        // joinTwo：桌面端英文用 " and "，中文用「并」；两侧都以「已」开头时第二个去掉「已」
        if (labels.size() == 2) {
            String second = labels.get(1);
            if (first.startsWith("已") && second.startsWith("已")) second = second.substring(1);
            return first + "并" + second;
        }
        StringBuilder sb = new StringBuilder(first);
        for (int i = 1; i < labels.size(); i++) sb.append('，').append(labels.get(i));
        return ranked.size() > 3 ? sb.append('等').toString() : sb.toString();
    }
}
