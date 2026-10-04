package com.dsh.mobile.ui;

import android.content.Context;
import android.widget.LinearLayout;

/**
 * 高度封顶的 LinearLayout：高度不超过**父容器**高度的给定比例。
 *
 * <p>为什么要有它（P2 修复）：对话页的底部悬浮层（子智能体入口 + 待发送条 + chip 行 +
 * 输入胶囊）是 wrap_content，待发送条一多就会一路长高。改前只有"列表底部留白"被封了 45%
 * ——**封错了对象**：悬浮层本身还是超高，于是最后一条消息被永久压在悬浮层下面（留白不够，
 * 又滚不上来）。现在直接封悬浮层本身。
 *
 * <p>配合 {@code gravity=BOTTOM} + 默认的 {@code clipChildren=true}：内容超高时**裁掉顶部**
 * （待发送条 / 子智能体入口先让位），底部输入胶囊永远留在屏幕内。
 */
public final class MaxHeightLinearLayout extends LinearLayout {

    /** 相对父容器高度的比例上限；<=0 表示不封顶。 */
    private float maxRatio = 0f;

    public MaxHeightLinearLayout(Context ctx) {
        super(ctx);
    }

    /** 设置高度上限 = 父容器高度 × ratio（例如 0.45f）。 */
    public void setMaxHeightRatio(float ratio) {
        if (maxRatio != ratio) {
            maxRatio = ratio;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (maxRatio <= 0f) return;
        android.view.ViewParent p = getParent();
        if (!(p instanceof android.view.View)) return;
        int parentH = ((android.view.View) p).getHeight();
        if (parentH <= 0) return;
        int cap = (int) (parentH * maxRatio);
        if (getMeasuredHeight() > cap) {
            setMeasuredDimension(getMeasuredWidth(), cap);
        }
    }
}
