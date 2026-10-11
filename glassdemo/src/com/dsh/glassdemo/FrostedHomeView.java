package com.dsh.glassdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 毛玻璃首页宿主：把"内容层 + 毛玻璃面板"放进同一个容器，在
 * dispatchDraw 里做到**帧同步**——
 *   1) 先画内容层（当前帧的内容）
 *   2) 同一帧立刻刷新每个玻璃面板的捕获（基于当前帧内容）
 *   3) 再画玻璃面板（用刚刷新的快照）
 *
 * 这样玻璃的模糊严格跟界面滚动走，不会滞后一帧、也不会"补一帧"。
 */
public class FrostedHomeView extends FrameLayout {

    private final View content;                       // 内容层（被模糊的目标）
    private final List<FrostedPanelView> glasses = new ArrayList<>();

    public FrostedHomeView(Context c, View content) {
        super(c);
        this.content = content;
        if (content != null) {
            content.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            addView(content);                            // 内容层在最底，玻璃盖其上
        }
    }

    /** 添加一块毛玻璃面板（盖在内容层之上）。 */
    public void addFrost(FrostedPanelView glass) {
        glasses.add(glass);
        addView(glass);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        long t = getDrawingTime();
        // 1) 先画内容层（当前帧）
        if (content != null) drawChild(canvas, content, t);
        // 2) 同一帧：内容画完立即刷新玻璃捕获，并强制把玻璃画到画布上。
        //    不依赖子 View 自己是否被重绘 —— 滚动时输入胶囊可能不被触发重绘，
        //    这里强制每帧重画，保证顶栏和输入胶囊都严格跟滚动帧帧同步。
        for (FrostedPanelView g : glasses) {
            g.refreshFrost();
            int sc = canvas.save();
            canvas.translate(g.getLeft(), g.getTop());
            // 先铺柔和外投影（玻璃悬浮感的来源），再画玻璃本体
            Bitmap sh = g.shadowBitmap();
            if (sh != null) canvas.drawBitmap(sh, -g.shadowPad(), -g.shadowPad(), null);
            g.draw(canvas);      // 用刚刷新的快照重画玻璃本体 + 其子内容
            canvas.restoreToCount(sc);
        }
        // 3) 其余非玻璃、非内容的子视图按 z 顺序正常画
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch == content || ch instanceof FrostedPanelView) continue;
            drawChild(canvas, ch, t);
        }
    }
}
