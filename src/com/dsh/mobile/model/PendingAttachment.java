package com.dsh.mobile.model;

import android.net.Uri;

/**
 * 待发送附件（2026-10-05 用户要求）。
 *
 * <p>用户原话：「加号点击应弹出文件，相册，拍照这三个选项，然后添加进来之后
 * <b>不是立即发送</b>而是和输入的文字或语音内容一起发送」。
 *
 * <p>因此选中/拍到的附件先以本对象暂存在输入条上方的「附件条」里，等用户点发送
 * （或语音转写回填后点发送）才和文字一起走 {@code GatewayClient}。
 *
 * <p><b>只描述「要发什么」，不持有字节</b>：真正发送时才从 {@link #uri} / {@link #localPath}
 * 读字节，避免大文件常驻内存（评审安全项）。
 *
 * <p>本类由主理人冻结，UI 层（ui/**）与接线层（MainActivity）都只读不改字段。
 */
public final class PendingAttachment {

    public static final String KIND_IMAGE = "image";
    public static final String KIND_FILE = "file";

    /** {@link #KIND_IMAGE} 或 {@link #KIND_FILE}。 */
    public final String kind;
    /** 内容来源（相册 / 文件选择器 / 相机的输出 Uri）。可为 null（此时用 {@link #localPath}）。 */
    public final Uri uri;
    /** 展示名，已净化、非空（净化由接线层负责，见 safeDownloadName 同款规则）。 */
    public final String name;
    /** MIME，如 image/jpeg、application/pdf；空串表示未知。 */
    public final String mediaType;
    /** 字节数；0 = 未知（发送前再解析）。 */
    public final long bytes;
    /** 图片的像素宽高；非图片为 0。 */
    public final int width;
    public final int height;
    /** 已落到本机的绝对路径（相机输出 / 文件缓存副本）；可为空串。 */
    public final String localPath;

    public PendingAttachment(String kind, Uri uri, String name, String mediaType,
                             long bytes, int width, int height, String localPath) {
        this.kind = KIND_IMAGE.equals(kind) ? KIND_IMAGE : KIND_FILE;
        this.uri = uri;
        this.name = name == null ? "" : name;
        this.mediaType = mediaType == null ? "" : mediaType;
        this.bytes = Math.max(0L, bytes);
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.localPath = localPath == null ? "" : localPath;
    }

    public static PendingAttachment image(Uri uri, String name, String mediaType,
                                          long bytes, int width, int height, String localPath) {
        return new PendingAttachment(KIND_IMAGE, uri, name, mediaType, bytes, width, height, localPath);
    }

    public static PendingAttachment file(Uri uri, String name, String mediaType,
                                         long bytes, String localPath) {
        return new PendingAttachment(KIND_FILE, uri, name, mediaType, bytes, 0, 0, localPath);
    }

    public boolean isImage() {
        return KIND_IMAGE.equals(kind);
    }

    public String displayName() {
        if (!name.isEmpty()) return name;
        return isImage() ? "图片" : "文件";
    }

    /** 「1.2 MB」这类人类可读大小；未知返回空串。 */
    public String sizeLabel() {
        if (bytes <= 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    /** 稳定去重键：同一个来源不重复入列。 */
    public String key() {
        String base = uri != null ? uri.toString() : localPath;
        return kind + "|" + base + "|" + name;
    }

    @Override
    public String toString() {
        return "PendingAttachment{" + kind + " " + displayName() + " " + sizeLabel() + "}";
    }
}
