package com.dsh.mobile.model;

import org.json.JSONObject;

/**
 * 会话工作目录里的一个条目（网关 {@code file-list} 帧的一行）。
 *
 * <p>协议真源：{@code dsh-plugin-mobile-gateway/PROTOCOL.md} §「文件下载」：
 * <pre>
 * { "kind":"file-list", "requestId":"files-1", "sessionId":"…", "path":".",
 *   "entries":[ { "name":"builds", "path":"builds", "kind":"directory" },
 *               { "name":"app.ipa", "path":"app.ipa", "kind":"file",
 *                 "bytes":123456, "modifiedAt":1787111700000,
 *                 "mediaType":"application/octet-stream" } ] }
 * </pre>
 *
 * <p>「生成物窗口」（任务①）用它渲染列表；{@link #path} 是相对会话工作目录的
 * 相对路径，直接交给 {@code file-download-open}。本类由主理人冻结。
 */
public final class ArtifactEntry {

    public final String name;
    /** 相对会话 cwd 的相对路径，用 {@code /} 分隔。 */
    public final String path;
    /** "file" | "directory"。 */
    public final String kind;
    public final String mediaType;
    public final long bytes;
    public final long modifiedAt;

    public ArtifactEntry(String name, String path, String kind, String mediaType,
                         long bytes, long modifiedAt) {
        this.name = name == null ? "" : name;
        this.path = path == null ? "" : path;
        this.kind = kind == null ? "file" : kind;
        this.mediaType = mediaType == null ? "" : mediaType;
        this.bytes = Math.max(0L, bytes);
        this.modifiedAt = Math.max(0L, modifiedAt);
    }

    /** 解析 entries[] 的一行；不是对象则返回 null。 */
    public static ArtifactEntry from(JSONObject o) {
        if (o == null) return null;
        return new ArtifactEntry(
                o.optString("name", ""),
                o.optString("path", ""),
                o.optString("kind", "file"),
                o.optString("mediaType", ""),
                o.optLong("bytes", 0L),
                o.optLong("modifiedAt", 0L));
    }

    public boolean isDirectory() {
        return "directory".equals(kind);
    }

    /** 「1.2 MB」这类人类可读大小；目录或未知返回空串。 */
    public String sizeLabel() {
        if (isDirectory() || bytes <= 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    @Override
    public String toString() {
        return "ArtifactEntry{" + kind + " " + name + " " + sizeLabel() + "}";
    }
}
