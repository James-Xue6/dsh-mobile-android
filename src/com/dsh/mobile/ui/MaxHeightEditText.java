package com.dsh.mobile.ui;

import android.content.Context;
import android.widget.EditText;

/**
 * 高度封顶的 EditText：按**行数上限**夹住测量高度，超出部分由 EditText 内部滚动。
 *
 * <p>为什么要有它（P0 真机 bug）：用户报「输入字符一多，键盘上方那个一圈灰色的胶囊
 * 就被撑成一个巨大的灰框、占掉大半个屏幕」。{@code setMaxLines(n)} 只约束"行数"，
 * 一旦布局链路里任何一环（父容器 wrap_content + 权重、IME adjustResize、软换行）
 * 把行数算歪，胶囊就会跟着长。这里在 {@code onMeasure} 里直接夹住测量高度。
 *
 * <p><b>上限在每次测量时现算</b>（[P2 修复]）：改前是构造时算好一个固定像素值，
 * 而 manifest 声明了 {@code fontScale} 不重建 Activity —— 用户改系统字体大小后，
 * 旧像素值就与新字号脱节（可能只显示 3 行或 7 行）。现在用当次的
 * {@code getPaint().getFontMetricsInt()} + 行距 + includeFontPadding 现算：
 * 字号/密度/行距怎么变，上限都恰好是 {@code maxLinesCap} 行文字的高度。
 *
 * <p>只封顶、不抬升：内容短时仍按 wrap_content 正常收缩（{@code setMeasuredDimension}
 * 只在超过上限时调用，且每次测量都重算，清空文字后高度自动回落）。
 */
public final class MaxHeightEditText extends EditText {

    /** 最多显示的行数；<=0 表示不封顶。 */
    private int maxLinesCap = 0;

    public MaxHeightEditText(Context ctx) {
        super(ctx);
    }

    /** 设置"最多显示多少行"（超出由 EditText 内部滚动）。 */
    public void setMaxHeightLines(int lines) {
        if (maxLinesCap != lines) {
            maxLinesCap = lines;
            requestLayout();
        }
    }

    /** 当前字号/行距下，{@code maxLinesCap} 行文字 + 上下内边距的高度（<=0 = 不封顶）。 */
    private int capPx() {
        if (maxLinesCap <= 0) return 0;
        android.graphics.Paint.FontMetricsInt fm = getPaint().getFontMetricsInt();
        int linePx = fm.descent - fm.ascent;
        if (getIncludeFontPadding()) {
            // includeFontPadding 会在行盒上下各加一段额外留白（top-ascent / bottom-descent）
            linePx += (fm.top - fm.ascent) + (fm.bottom - fm.descent);
        }
        linePx += (int) Math.ceil(getLineSpacingExtra());
        if (linePx <= 0) return 0;
        return maxLinesCap * linePx + getPaddingTop() + getPaddingBottom();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int cap = capPx();
        if (cap > 0 && getMeasuredHeight() > cap) {
            setMeasuredDimension(getMeasuredWidth(), cap);
        }
    }
}
