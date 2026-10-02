package com.dsh.mobile.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * 左侧抽屉容器（豆包式两级导航）。
 *
 * 三层结构（自下而上）：
 *   0 content —— 内容层（对话页 / 设置页）。抽屉打开时它**不重挂、不移动**，
 *                屏幕右侧露出的那一条就是它，被遮罩压暗 + 缩淡 = 用户要的
 *                「最右侧漏一点点的虚化对话窗口」；
 *   1 scrim   —— 全屏遮罩，alpha 跟手；完全关闭时 GONE，不吃掉内容层的触摸；
 *   2 drawer  —— 会话列表。宽度 = 屏宽 * widthFraction（竖屏 82%，横屏 52%），
 *                关闭时整条平移到屏幕左缘之外，打开时滑回 x = 0。
 *
 * 手势（不用 Material/DrawerLayout，纯手搓）：
 *   ① 关闭时从屏幕左缘往右拖  -> 拉出抽屉（跟手）；
 *   ② 打开时点/拖右侧露出的一条 -> 收起；
 *   ③ 其它触摸一律不拦，原样交给子 View（会话列表滚动、输入框 = 原逻辑）。
 */
public final class DrawerHost extends FrameLayout {

    /** 抽屉宽度占屏宽比例：竖屏（用户要求 78%~85%）。 */
    private static final float WIDTH_FRACTION_PORTRAIT = 0.82f;
    /**
     * 横屏时的抽屉宽度比例：横屏屏宽是竖屏的 1.7 倍以上，沿用 82% 会让抽屉占掉
     * 1920*0.82 = 1574px，内容层只剩 346px（连一个字都放不下）——所以收到 52%，
     * 既够放会话卡片（约 1000px，比竖屏抽屉还宽），右侧也能看见内容层。
     */
    private static final float WIDTH_FRACTION_LANDSCAPE = 0.52f;
    /** 遮罩最深不透明度：右侧那条对话要"看得见但被压暗"。 */
    private static final float SCRIM_ALPHA = 0.45f;
    /** 开关动画时长。 */
    private static final long ANIM_MS = 220L;

    private final FrameLayout content;
    private final View scrim;
    private final FrameLayout drawer;

    /** 抽屉当前平移量：0 = 全开，-drawerWidth = 全关。 */
    private float tx;
    private int drawerWidth;
    private boolean open;

    private final int touchSlop;
    /** 左缘起手区：只有从这里开始的右滑才认作"拉出抽屉"（避免和列表横滑打架）。 */
    private final int edgeZone;

    private boolean dragging;
    private boolean moved;
    private float downX;
    private float downY;
    private float startTx;

    private android.animation.ValueAnimator anim;

    public DrawerHost(Context ctx) {
        super(ctx);
        setClipChildren(false);

        content = new FrameLayout(ctx);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        scrim = new View(ctx);
        scrim.setBackgroundColor(0xFF000000);
        scrim.setAlpha(0f);
        scrim.setVisibility(GONE);
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        drawer = new FrameLayout(ctx);
        drawer.setBackgroundColor(Ui.BG);
        drawer.setElevation(Ui.dp(ctx, 10));
        // 宽度在 onMeasure 里按屏宽比例写进 LayoutParams；先丢到屏幕外，避免首帧闪一下
        drawer.setTranslationX(-100000f);
        addView(drawer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        ViewConfiguration vc = ViewConfiguration.get(ctx);
        touchSlop = vc.getScaledTouchSlop();
        edgeZone = Ui.dp(ctx, 22);
    }

    /** 内容层：对话页 / 设置页放这里。 */
    public FrameLayout content() { return content; }

    /** 抽屉层：会话列表放这里。 */
    public FrameLayout drawer() { return drawer; }

    public boolean isOpen() { return open; }

    public void openDrawer(boolean animate) {
        open = true;
        if (!animate || drawerWidth <= 0) { anim = null; applyTx(0f); return; }
        animateTo(0f);
    }

    public void closeDrawer(boolean animate) {
        open = false;
        if (!animate || drawerWidth <= 0) { anim = null; applyTx(-drawerWidth); return; }
        animateTo(-drawerWidth);
    }

    // ------------------------------------------------------------ 尺寸 / 摆位

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        int dw = Math.max(1, Math.round(w * fractionFor(w, h)));
        LayoutParams lp = (LayoutParams) drawer.getLayoutParams();
        if (lp.width != dw) lp.width = dw;
        drawerWidth = dw;
        super.onMeasure(widthSpec, heightSpec);
    }

    /** 按**当前**尺寸选抽屉比例：宽 > 高 = 横屏，收窄（否则内容层被压成一条）。 */
    private static float fractionFor(int w, int h) {
        if (w > 0 && h > 0 && w > h) return WIDTH_FRACTION_LANDSCAPE;
        return WIDTH_FRACTION_PORTRAIT;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        onConfigChanged();
    }

    /**
     * 方向 / 窗口尺寸变化后按**新**尺寸重算抽屉宽度并重新摆位。
     *
     * 旋转时 Activity 不重建（清单声明了 configChanges），onSizeChanged 通常也会到，
     * 但那条路依赖"父容器真的重新量了"；这里做成显式入口，由
     * MainActivity.onConfigurationChanged → relayoutForConfig() 主动调用，绝不依赖时序。
     * 动画或手指拖动中不抢位置。
     */
    public void onConfigChanged() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;          // 还没量过：交给 onMeasure
        int dw = Math.max(1, Math.round(w * fractionFor(w, h)));
        LayoutParams lp = (LayoutParams) drawer.getLayoutParams();
        boolean widthChanged = lp.width != dw;
        if (widthChanged) {
            lp.width = dw;
            drawer.setLayoutParams(lp);
        }
        drawerWidth = dw;
        if (anim == null && !dragging) applyTx(open ? 0f : -drawerWidth);
        if (widthChanged) requestLayout();
    }

    /** 平移抽屉并把遮罩 alpha 按同一进度跟手。 */
    private void applyTx(float t) {
        float min = -drawerWidth;
        if (t > 0f) t = 0f;
        if (t < min) t = min;
        tx = t;
        drawer.setTranslationX(t);
        float p = drawerWidth <= 0 ? 0f : (1f + t / drawerWidth);   // 0=关 1=开
        if (p < 0f) p = 0f;
        if (p > 1f) p = 1f;
        scrim.setAlpha(SCRIM_ALPHA * p);
        if (p <= 0.002f) {
            scrim.setVisibility(GONE);
        } else {
            scrim.setVisibility(VISIBLE);
            scrim.setClickable(p > 0.5f);
        }
    }

    private void animateTo(float target) {
        cancelAnim();
        final float from = tx;
        android.animation.ValueAnimator a =
                android.animation.ValueAnimator.ofFloat(from, target);
        a.setDuration(ANIM_MS);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(anim2 -> applyTx(((Float) anim2.getAnimatedValue()).floatValue()));
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                anim = null;
                applyTx(target);
            }
        });
        anim = a;
        a.start();
    }

    private void cancelAnim() {
        android.animation.ValueAnimator a = anim;
        anim = null;
        if (a != null) {
            a.removeAllUpdateListeners();
            a.removeAllListeners();
            a.cancel();
        }
    }

    // ------------------------------------------------------------ 手势

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = false;
                moved = false;
                downX = ev.getX();
                downY = ev.getY();
                startTx = tx;
                // 打开状态下右侧露出的一条：点/拖都归抽屉容器处理（收起）
                if (drawerWidth > 0 && open && downX > drawerWidth) {
                    dragging = true;
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (dragging) return true;
                if (drawerWidth <= 0) return false;
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (Math.abs(dx) <= touchSlop || Math.abs(dx) <= Math.abs(dy)) return false;
                if (!open && downX <= edgeZone) {           // 左缘往右滑 = 拉出抽屉
                    dragging = true;
                    return true;
                }
                if (open && downX > drawerWidth) {          // 右侧露出条往左滑 = 收起
                    dragging = true;
                    return true;
                }
                return false;
            }
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                moved = false;
                downX = ev.getX();
                downY = ev.getY();
                startTx = tx;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = ev.getX() - downX;
                if (Math.abs(dx) > touchSlop) moved = true;
                applyTx(startTx + dx);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragging = false;
                if (!moved) {                       // 轻点：开着就关，关着（边缘误触）不动
                    if (open) closeDrawer(true);
                    return true;
                }
                float p = drawerWidth <= 0 ? 0f : (1f + tx / drawerWidth);
                if (p > 0.5f) openDrawer(true);
                else closeDrawer(true);
                return true;
            }
            default:
                return true;
        }
    }
}
