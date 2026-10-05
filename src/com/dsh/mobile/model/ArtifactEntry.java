package com.dsh.mobile.model;

import org.json.JSONObject;

/**
 * 本会话「产出的文件」一条（2026-10-06 用户需求修正）。
 *
 * <p><b>用户原话</b>：「生成物点开这个目录内容有问题，不要显示这么多就显示这次项目生成的这个输出文件即可，
 * 怎么还能点上一页啥的，这些不要了」。
 *
 * <p>所以本类**不再表示"工作目录里的一项"**（那是旧的 {@code file-list} 目录浏览方案，已废弃、
 * 连同「← 返回上级」一起删掉），而是表示 Agent 通过 {@code deliverables/presented} 事件
 * <b>明确交付出来的一个文件</b>。数据来源就是那个事件的 {@code files[]}：
 * <pre>
 * { "callId": "…", "files": [ { "path": "F:\\…\\报告.xlsx", "description": "…" } ] }
 * </pre>
 *
 * <p>注意 {@code path} 是**绝对路径**（宿主交付时的落点），下载前要相对化到会话工作目录
 * （见 {@code MainActivity.relativize}）。本类由主理人冻结。
 */
public final class ArtifactEntry {

    /** 展示名：从 {@link #path} 取末段。 */
    public final String name;
    /** 原始路径（通常是绝对路径，由宿主交付时给出）。 */
    public final String path;
    /** 交付说明（可为空）。 */
    public final String description;

    public ArtifactEntry(String name, String path, String description) {
        this.path = path == null ? "" : path;
        this.name = (name == null || name.isEmpty()) ? lastSegment(this.path) : name;
        this.description = description == null ? "" : description;
    }

    /**
     * 解析 {@code deliverables/presented} 的 {@code files[]} 一行；{@code path} 为空返回 null。
     */
    public static ArtifactEntry fromPresented(JSONObject o) {
        if (o == null) return null;
        String path = o.optString("path", "");
        if (path.isEmpty()) return null;
        return new ArtifactEntry(lastSegment(path), path, o.optString("description", ""));
    }

    /** 取路径末段（同时兼容 {@code \} 与 {@code /}）。 */
    private static String lastSegment(String p) {
        if (p == null) return "";
        String s = p.replace('/', '\\');
        while (s.endsWith("\\")) s = s.substring(0, s.length() - 1);
        int cut = s.lastIndexOf('\\');
        String name = cut >= 0 && cut + 1 < s.length() ? s.substring(cut + 1) : s;
        return name.isEmpty() ? p : name;
    }

    /** 同一路径视为同一个文件（去重判据）。 */
    public String key() {
        return path;
    }

    @Override
    public String toString() {
        return "ArtifactEntry{" + name + " <- " + path + "}";
    }
}
