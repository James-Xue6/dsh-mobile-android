package com.dsh.mobile.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.EnumMap;
import java.util.Map;

/**
 * 扫码配对：Camera 预览 + ZXing 解码。返回 Intent 结果 {"raw": "<配对串>"}。
 *
 * 生命周期要点（第一版踩过的坑）：Camera.setPreviewDisplay() 必须在 Surface 已创建之后调用。
 * 早先版本在 onResume 里就 setPreviewDisplay + startPreview，此时 Surface 往往还没就绪，
 * 抛 IOException 后被 catch(Throwable){ finish(); } 静默吞掉 —— 表现就是「点了打不开相机」。
 * 现在：相机句柄只在 hasSurface 为真时打开；任何失败都显示在界面上，而不是直接退出。
 */
public final class QrScanActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    public static final String EXTRA_RAW = "raw";
    private static final int REQ_CAMERA = 21;

    private final MultiFormatReader reader = new MultiFormatReader();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Camera camera;
    private SurfaceView surface;
    private TextView errorView;

    private volatile boolean hasSurface;
    private volatile boolean previewing;
    private volatile boolean opening;
    private volatile boolean decoded;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        surface = new SurfaceView(this);
        surface.getHolder().addCallback(this);
        root.addView(surface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView tip = new TextView(this);
        tip.setText("把电脑端「移动设备」里的配对二维码放进框内");
        tip.setTextColor(0xFFFFFFFF);
        tip.setTextSize(13.5f);
        tip.setGravity(Gravity.CENTER);
        tip.setBackgroundColor(0x99000000);
        tip.setPadding(24, 22, 24, 22);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.TOP;
        tip.setLayoutParams(tlp);
        root.addView(tip);

        // 失败时的可见提示（旧版是静默 finish，排查无从下手）
        errorView = new TextView(this);
        errorView.setTextColor(0xFFFFFFFF);
        errorView.setTextSize(14f);
        errorView.setGravity(Gravity.CENTER);
        errorView.setBackgroundColor(0xE6101010);
        errorView.setPadding(40, 40, 40, 40);
        errorView.setVisibility(TextView.GONE);
        root.addView(errorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView cancel = new TextView(this);
        cancel.setText("返回");
        cancel.setTextColor(0xFFFFFFFF);
        cancel.setTextSize(15f);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(Ui.pill(0x66000000));
        cancel.setPadding(48, 22, 48, 22);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        clp.bottomMargin = 90;
        cancel.setLayoutParams(clp);
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel);

        setContentView(root);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.CAMERA }, REQ_CAMERA);
        } else {
            tryOpen();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        release();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code != REQ_CAMERA) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            tryOpen();
        } else {
            showError("没有相机权限，无法扫码。\n\n返回上一页用「粘贴配对串」也可以完成配对。");
        }
    }

    // ------------------------------------------------------------ Surface 生命周期

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        hasSurface = true;
        tryOpen();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (camera != null && !previewing) startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
        release();
    }

    // ------------------------------------------------------------ 相机

    private void tryOpen() {
        if (camera != null || opening || !hasSurface) return;
        opening = true;
        try {
            if (Camera.getNumberOfCameras() == 0) {
                showError("这台设备没有可用相机。\n\n返回上一页用「粘贴配对串」完成配对。");
                return;
            }
            camera = Camera.open(0);
            Camera.Parameters p = camera.getParameters();
            try {
                for (String m : p.getSupportedFocusModes()) {
                    if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(m)) {
                        p.setFocusMode(m);
                        break;
                    }
                }
            } catch (Throwable ignored) { }
            try {
                Camera.Size best = null;
                for (Camera.Size s : p.getSupportedPreviewSizes()) {
                    if (s.width >= 960 && s.height >= 720) {
                        if (best == null || Math.abs(s.width - 1280) < Math.abs(best.width - 1280)) best = s;
                    }
                }
                if (best != null) p.setPreviewSize(best.width, best.height);
            } catch (Throwable ignored) { }
            try { p.setPreviewFormat(ImageFormat.NV21); } catch (Throwable ignored) { }
            camera.setParameters(p);

            camera.setErrorCallback((error, cam) -> showError("相机报错 (code " + error
                    + ")。\n\n可以用「粘贴配对串」代替扫码。"));

            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(surface.getHolder());
            camera.setPreviewCallback(this);
            startPreview();
        } catch (Throwable t) {
            release();
            showError("相机打开失败：" + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : " — " + t.getMessage())
                    + "\n\n可以返回后用「粘贴配对串」完成配对。");
        } finally {
            opening = false;
        }
    }

    private void startPreview() {
        try {
            if (camera != null) {
                camera.startPreview();
                previewing = true;
            }
        } catch (Throwable t) {
            showError("相机预览启动失败：" + t.getMessage());
        }
    }

    private void release() {
        previewing = false;
        if (camera != null) {
            try { camera.setPreviewCallback(null); } catch (Throwable ignored) { }
            try { camera.stopPreview(); } catch (Throwable ignored) { }
            try { camera.release(); } catch (Throwable ignored) { }
            camera = null;
        }
    }

    private void showError(final String msg) {
        ui.post(() -> {
            if (isFinishing()) return;
            errorView.setText(msg);
            errorView.setVisibility(TextView.VISIBLE);
        });
    }

    // ------------------------------------------------------------ 解码

    @Override
    public void onPreviewFrame(byte[] data, Camera cam) {
        if (decoded || data == null || cam == null) return;
        int w, h;
        try {
            Camera.Size size = cam.getParameters().getPreviewSize();
            w = size.width;
            h = size.height;
        } catch (Throwable t) {
            return;
        }
        String found = decode(data, w, h, false);
        if (found == null) found = decode(data, w, h, true);
        if (found == null) return;

        decoded = true;
        final String text = found;
        ui.post(() -> {
            Intent out = new Intent();
            out.putExtra(EXTRA_RAW, text);
            setResult(RESULT_OK, out);
            finish();
        });
    }

    private String decode(byte[] data, int width, int height, boolean rotate) {
        try {
            byte[] gray = rotate ? rotate90(data, width, height) : data;
            int w = rotate ? height : width;
            int h = rotate ? width : height;
            PlanarYUVLuminanceSource source =
                    new PlanarYUVLuminanceSource(gray, w, h, 0, 0, w, h, false);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result r = reader.decodeWithState(bitmap);
            reader.reset();
            return r == null ? null : r.getText();
        } catch (Throwable t) {
            reader.reset();
            return null;
        }
    }

    private static byte[] rotate90(byte[] src, int w, int h) {
        byte[] dst = new byte[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dst[x * h + (h - 1 - y)] = src[y * w + x];
            }
        }
        return dst;
    }
}
