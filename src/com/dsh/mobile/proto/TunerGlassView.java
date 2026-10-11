package com.dsh.mobile.proto;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/** 手动调参玻璃：所有液态玻璃参数用滑条实时调，方便在模拟器上定位最终质感。 */
public class TunerGlassView extends View {

    // —— 可调参数（滑条直接改这些字段）——
    public int blurPasses = 1;       // 模糊次数 0..4（0=不糊）[推荐 blur12dp→较轻]
    public int refrH = 10;           // 折射高度 dp 0..60 [AndroidLiquidGlass 推荐 10]
    public int refrAmt = 14;         // 折射量 dp 0..80 [AndroidLiquidGlass 推荐 14]
    public int tintAlpha = 0x42;     // 玻璃底色不透明度 0..255（0x42=66）
    public int corner = 24;          // 圆角 dp 0..48
    public int downscale = 5;        // 背景降采样 2..6（越大越糊+越细腻）
    public int hairlineAlpha = 0x1E; // 边缘亮线 alpha 0..255（0=关）
    public int saturation = 100;     // 彩度 % 50..200（100=原）
    public int chromatic = 6;        // 色散量 px 0..12 [推荐开→6px]

    private Bitmap bg;
    private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tintP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hairP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    public TunerGlassView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
    }

    public void setBg(Bitmap b) {
        bg = b;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bg == null) return;
        float ds = density;
        int vw = getWidth(), vh = getHeight();
        // 玻璃：垂直中央偏上，给下方滑条留空间
        int gw = (int) (Math.min(240 * ds, vw * 0.6f)), gh = (int) (gw * 0.85f);
        int gx = (vw - gw) / 2, gy = (vh - gh) / 2 - (int) (40 * ds);

        // 1) 底图平铺整个视图
        canvas.drawBitmap(bg, null, new Rect(0, 0, vw, vh), bp);

        // 2) 捕获玻璃区域 + 溢出带
        int padPx = (int) Math.ceil(Math.max(20 * ds, refrH * ds * 2.35f));
        int W = gw + padPx * 2, H = gh + padPx * 2;
        int bw = Math.max(1, W / downscale), bh = Math.max(1, H / downscale);
        Bitmap small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas cc = new Canvas(small);
        cc.scale(1f / downscale, 1f / downscale);
        cc.drawBitmap(bg, new Rect(0, 0, vw, vh),
                new Rect(gx - padPx, gy - padPx, gx + gw + padPx, gy + gh + padPx), bp);
        int[] px = new int[bw * bh];
        small.getPixels(px, 0, bw, 0, 0, bw, bh);

        // 3) 模糊
        for (int i = 0; i < Math.max(0, blurPasses); i++) Frost.gaussianBlur(px, bw, bh);

        // 4) 折射
        if (refrAmt > 0) {
            Frost.applyLens(px, bw, bh, corner * ds / downscale, refrH * ds / downscale,
                    refrAmt * ds / downscale, Math.max(1, padPx / downscale));
        }

        // 5) 彩度 + 色散（软件 RGB 通道处理）
        if (saturation != 100) applySaturation(px);
        if (chromatic > 0) applyChromatic(px, bw, bh, Math.max(1, chromatic));

        small.setPixels(px, 0, bw, 0, 0, bw, bh);

        // 6) 圆角裁剪 + 画玻璃 + tint
        Path clip = roundRectPath(gx, gy, gw, gh, corner * ds);
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawBitmap(small, null,
                new Rect(gx - padPx, gy - padPx, gx + gw + padPx, gy + gh + padPx), null);
        if (tintAlpha > 0) {
            tintP.setColor((Math.min(255, tintAlpha) << 24) | 0x00FFFFFF);
            canvas.drawRect(gx, gy, gx + gw, gy + gh, tintP);
        }
        canvas.restore();

        // 7) 边缘亮线（圆角描边）
        if (hairlineAlpha > 0) {
            hairP.setStyle(Paint.Style.STROKE);
            hairP.setStrokeWidth(1.5f * ds);
            hairP.setColor((Math.min(255, hairlineAlpha) << 24) | 0x00FFFFFF);
            canvas.drawPath(clip, hairP);
        }
    }

    private Path roundRectPath(float x, float y, float w, float h, float r) {
        Path p = new Path();
        float rr = Math.min(r, Math.min(w, h) / 2f);
        p.addRoundRect(new RectF(x, y, x + w, y + h), rr, rr, Path.Direction.CW);
        return p;
    }

    private void applySaturation(int[] px) {
        float sat = saturation / 100f;
        for (int i = 0; i < px.length; i++) {
            int a = px[i] >>> 24, r = (px[i] >>> 16) & 255, g = (px[i] >>> 8) & 255, b = px[i] & 255;
            float lum = 0.299f * r + 0.587f * g + 0.114f * b;
            int r2 = clamp(lum + (r - lum) * sat);
            int g2 = clamp(lum + (g - lum) * sat);
            int b2 = clamp(lum + (b - lum) * sat);
            px[i] = (a << 24) | (r2 << 16) | (g2 << 8) | b2;
        }
    }

    /** 色散：R/B 通道沿 x 各偏 amt 像素（轴向分离，模拟 RGB 色散）。 */
    private void applyChromatic(int[] px, int w, int h, int amt) {
        int[] copy = px.clone();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int r = (copy[idx(clamp(x - amt, 0, w - 1), y, w, h)] >>> 16) & 255;
                int g = (copy[i] >>> 8) & 255;
                int b = copy[idx(clamp(x + amt, 0, w - 1), y, w, h)] & 255;
                int a = px[i] >>> 24;
                px[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
    }

    private int idx(int x, int y, int w, int h) {
        return (y < 0 ? 0 : y > h - 1 ? h - 1 : y) * w + x;
    }

    private int clamp(int v, int lo, int hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    private int clamp(float v) {
        int i = (int) v;
        return i < 0 ? 0 : i > 255 ? 255 : i;
    }
}
