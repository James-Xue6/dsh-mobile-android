package com.dsh.mobile.net;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;

/**
 * 「拍照输出」专用 FileProvider：只把**应用私有缓存目录** {@code <cacheDir>/capture/}
 * 下的文件以 {@code content://} 暴露给系统相机。
 *
 * <p>为什么需要它：{@code MediaStore.ACTION_IMAGE_CAPTURE} 在 Android 7（API 24）起
 * 禁止把 {@code file://} 放进 {@code EXTRA_OUTPUT}（FileUriExposedException），
 * 必须给相机一个 {@code content://} 写入口。所以拍照前先由
 * {@link #newCaptureUri(Context)} 在私有缓存里造一个空文件、返回它的 content URI。
 *
 * <p>为什么不复用 {@link ApkFileProvider}：那个只读、且只暴露
 * {@code getExternalFilesDir(null)/updates}（给系统安装器装 APK）。语义与暴露目录都不同，
 * 按契约 §1 的分区也不该改它（它是既有功能，别的改动不碰）。故另建一个，边界各自独立。
 *
 * <p>安全边界（与 ApkFileProvider 同一套纪律）：
 * <ul>
 *   <li>只允许访问 {@code <cacheDir>/capture/} 下的文件，且文件名不含路径分隔符 ——
 *       不构成任意文件读取口子；</li>
 *   <li>provider 声明为 {@code exported="false" + grantUriPermissions="true"}：
 *       只有拿到 {@code FLAG_GRANT_*_URI_PERMISSION} 的接收方（系统相机）能读写这一次；</li>
 *   <li>文件名由本类自造（{@code IMG_<ts>.jpg}），**不接受外部传入** ——
 *       调用方拿不到拼路径的机会。</li>
 * </ul>
 *
 * <p>不需要 {@code res/xml/*_paths.xml}，也不声明
 * {@code android.support.FILE_PROVIDER_PATHS} 的 meta-data：那套是 androidx FileProvider
 * 的路径白名单机制，本项目不依赖 androidx，路径约束直接写在本类的 {@link #captureDir} +
 * {@link #openFile} 里。
 */
public final class CaptureFileProvider extends ContentProvider {

    /** 与 AndroidManifest 里 {@code android:authorities} 必须逐字一致。 */
    public static final String AUTHORITY = "com.dsh.mobile.capture";

    /** 私有缓存里的拍照子目录；路径约束的唯一来源。 */
    private static final String DIR_NAME = "capture";

    /** 拍照文件的统一扩展名（jpg）。 */
    private static final String FILE_PREFIX = "IMG_";
    private static final String FILE_SUFFIX = ".jpg";

    /** 应用私有缓存下的拍照目录（无需任何存储权限）。 */
    public static File captureDir(Context ctx) {
        File d = new File(ctx.getCacheDir(), DIR_NAME);
        if (!d.exists()) {
            //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        }
        return d;
    }

    /**
     * 造一个本次拍照的 content URI（契约 §3.4 冻结签名）。
     *
     * <p>为什么先建空文件再返回 URI：{@code EXTRA_OUTPUT} 的目标文件**必须已经存在**
     * 且可写，部分相机实现只往这个 URI 里写、不会自己创建；先建好能避免「拍完拿到 0 字节」。
     *
     * @return {@code content://com.dsh.mobile.capture/IMG_<ts>.jpg}
     */
    public static Uri newCaptureUri(Context c) {
        File f = new File(captureDir(c), FILE_PREFIX + System.currentTimeMillis() + FILE_SUFFIX);
        try {
            //noinspection ResultOfMethodCallIgnored
            f.createNewFile();
        } catch (java.io.IOException ignored) {
            // 建不出来也不抛：相机自己会按 URI 创建（部分实现），openFile 仍能提供 fd。
        }
        return uriFor(f);
    }

    /** 把该目录下的文件转成 content:// URI（只取文件名，不做路径拼接）。 */
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
        // 与 ApkFileProvider 同一套文件名校验：拒绝空名、路径分隔符与 ".."
        if (name == null || name.isEmpty()
                || name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new java.io.FileNotFoundException("bad name: " + name);
        }
        File f = new File(captureDir(ctx), name);
        // 相机 App 是**写**方（EXTRA_OUTPUT 指向这个 URI），所以模式必须照传进来的 mode 解析：
        // ParcelFileDescriptor.parseMode 会把 "r"/"w"/"rw"/"rwt" 翻成正确的 flags
        // （"w" 自带 CREATE|TRUNCATE —— 拍照就是整文件覆盖写）。
        // 不能写死 MODE_READ_ONLY，否则相机打开就报 Permission denied / EROFS。
        // mode 为空时按只读兜底（ContentProvider 契约里 mode 一般非空）。
        String m = (mode == null || mode.isEmpty()) ? "r" : mode;
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(m));
    }

    @Override
    public String getType(Uri uri) {
        return "image/jpeg";
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
