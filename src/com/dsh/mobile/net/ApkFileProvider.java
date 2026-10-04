package com.dsh.mobile.net;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;

/**
 * 极简「APK 只读提供者」：把应用内更新下载好的 APK 以 {@code content://} 暴露给系统安装器。
 *
 * <p>为什么自己写而不是用 androidx 的 FileProvider：本项目**不依赖 androidx**
 * （libs 里只有 zxing，构建是 javac 直编），引入 FileProvider 会带进一整条 androidx.core 依赖。
 * 这里只做安装器需要的那一件事 —— {@link #openFile} 返回只读 fd。
 *
 * <p>安全边界（评审要求：不能变成任意文件读取口子）：
 * <ul>
 *   <li>只允许访问 {@link #updateDir(Context)} 目录下的文件，且文件名不含路径分隔符；</li>
 *   <li>provider 声明为 {@code exported="false" + grantUriPermissions="true"} ——
 *       只有拿到 {@code FLAG_GRANT_READ_URI_PERMISSION} 的接收方（系统安装器）能读一次。</li>
 * </ul>
 */
public final class ApkFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.dsh.mobile.apk";

    /** 应用内更新的下载目录（App 私有外部目录，无需任何存储权限）。 */
    public static File updateDir(Context ctx) {
        File d = new File(ctx.getExternalFilesDir(null), "updates");
        if (!d.exists()) {
            //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        }
        return d;
    }

    /** 把该目录下的文件转成可分享给安装器的 content:// URI。 */
    public static Uri uriFor(File f) {
        return Uri.parse("content://" + AUTHORITY + "/" + f.getName());
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        Context ctx = getContext();
        if (ctx == null) throw new java.io.FileNotFoundException("no context");
        String name = uri == null ? null : uri.getLastPathSegment();
        if (name == null || name.isEmpty()
                || name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new java.io.FileNotFoundException("bad name: " + name);
        }
        File f = new File(updateDir(ctx), name);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
