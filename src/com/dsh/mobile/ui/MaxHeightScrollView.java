package com.dsh.mobile.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.ScrollView;

/**
 * 高度封顶的 ScrollView：内容超过上限就内部滚动，不再把卡片撑高。
 *
 * <p>为什么要有它（P0 真机 bug）：提问卡 / 审批卡的**内容区**（问题、选项、补充输入）是
 * wrap_content，选项/问题一多就被内容一路撑高，真机上表现为"一圈灰色描边的容器变成一大块、
 * 占掉大半个屏幕，把对话内容都盖住"。这里把内容区包一层 ScrollView 并夹住测量高度：
 * 内容区最高不超过屏幕可用高的 55%，超出的部分在卡片内部滚动（内容不丢，仍可读可点）。
 *
 * <p><b>按钮不在这里面</b>：卡片底部的「批准一次 / 拒绝」「提交 / 跳过」挂在 ScrollView
 * <b>之外</b>（见 ChatAdapter.addScrollableBody 的调用方）。真机实测过反例：把整个卡片
 * （含按钮）塞进 ScrollView 时，外层 ListView 会抢走竖向滑动 → 内容超高时按钮被推出
 * 可视区且滚不出来，用户根本无法批准。
 *
 * <p>只封顶、不抬升：内容本来就矮时测量高度不变，视觉与改动前一致。
 */
public final class MaxHeightScrollView extends ScrollView {

    /** <=0 表示不封顶。 */
    private int maxHeightPx = 0;
    /**
     * 相对**所在 ListView 实测高度**的比例上限（<=0 = 不用这条）。
     *
     * <p>[P0 修复·2026-10-04] 只按"屏高 55%"封顶是不够的：键盘弹起后窗口变矮，
     * 卡片仍按整屏算 → 卡片比"列表可见高度"还高，用户滚到列表底部也看不到内容区底部
     * （用户报「看不到输入的内容」）。这里再加一道**跟随 ListView 实时高度**的上限，
     * 每次测量现算，键盘弹起/收起时自动收紧或放宽。
     */
    private float listRatioCap = 0f;

    public MaxHeightScrollView(Context ctx) {
        super(ctx);
    }

    /** 设置像素级高度上限（按屏高算的"天花板"）。 */
    public void setMaxHeightPx(int px) {
        if (maxHeightPx != px) {
            maxHeightPx = px;
            requestLayout();
        }
    }

    /** 追加"不超过所在 ListView 高度的 ratio 倍"这条上限。 */
    public void setListRatioCap(float ratio) {
        if (listRatioCap != ratio) {
            listRatioCap = ratio;
            requestLayout();
        }
    }

    /** 卡片里"不参与滚动、但必须一起露出来"的部分（底部按钮行）占的高度。 */
    private int reservedBottomPx = 0;

    public void setReservedBottomPx(int px) {
        if (reservedBottomPx != px) {
            reservedBottomPx = px;
            requestLayout();
        }
    }

    /**
     * 上限 = **用户真正看得见的区域** − 按钮行高度。
     *
     * <p>[P0 修复·2026-10-04] 用户报「点输入框、键盘弹起后，卡片窗滑到最上面、看不到下面选项，
     * 要手动滑下来」。根因：ListView 一直铺到屏幕底、被底部悬浮层（输入胶囊那一条）盖住，
     * 而卡片只按"ListView 高度"算上限 → 卡片整张比"看得见的区域"还高，
     * 键盘弹起后输入框和按钮就都被挤到悬浮层下面。
     * 这里改成按 `ConversationView.usableCardHeightPx()`（列表高 − 悬浮层高）来封顶，
     * 保证"内容区 + 按钮行"整张卡都落在看得见的区域里。
     */
    private int usableCapPx() {
        try {
            android.view.ViewParent p = getParent();
            while (p != null) {
                if (p instanceof ConversationView) {
                    int usable = ((ConversationView) p).usableCardHeightPx();
                    if (usable > 0) return Math.max(0, usable - reservedBottomPx);
                    return 0;
                }
                p = p.getParent();
            }
        } catch (Throwable ignored) { }
        return 0;
    }

    /** 沿父链找承载本卡的 ListView（找不到返回 null）。 */
    private android.view.View listAncestor() {
        android.view.ViewParent p = getParent();
        while (p != null) {
            if (p instanceof android.widget.AbsListView) return (android.view.View) p;
            p = p.getParent();
        }
        return null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int cap = maxHeightPx;
        if (listRatioCap > 0f) {
            android.view.View list = listAncestor();
            if (list != null && list.getHeight() > 0) {
                int byList = (int) (list.getHeight() * listRatioCap);
                if (cap <= 0 || byList < cap) cap = byList;
            }
        }
        // [用户点名·2026-10-04] 已撤掉"按看得见的区域动态封顶"：
        // 内容区高度必须稳定（只按屏高封顶），否则键盘弹起时内容区变矮，
        // 用户刚滚到的输入框又被挤出可视区（"乱滚回去"）。
        // 卡片可见性改由外层列表负责（ConversationView.onWindowInsetsChanged）。
        if (cap > 0 && getMeasuredHeight() > cap) {
            setMeasuredDimension(getMeasuredWidth(), cap);
        }
    }

    /**
     * 内容超出时把"父容器不要拦截触摸"的旗标立起来。
     *
     * <p>卡片内容区活在原生 ListView 的 item 里，竖向手势默认被 ListView 抢走 —— 只封顶
     * 还不够，用户得**能滚**到被裁掉的那部分内容（否则内容看不见也摸不到）。
     * 内容装得下时（canScrollVertically 为假）不做任何事，列表滚动行为与改动前完全一致。
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (canScrollVertically(1) || canScrollVertically(-1)) {
            android.view.ViewParent p = getParent();
            if (p != null) p.requestDisallowInterceptTouchEvent(true);
        }
        return super.onInterceptTouchEvent(e);
    }
}
