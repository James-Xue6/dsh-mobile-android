package com.dsh.mobile.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/**
 * 右侧「问题刻度尺」（v6，按用户逐条反馈重做）。
 *
 * <p>用户原话的四条要求，逐条落实：
 * <ol>
 *   <li><b>只留 10 个刻度</b>：不是"多少问题画多少刻度"（65 个问题画 65 格 = 挤成一团、很难看 ✗）。
 *       这里是**固定的 10 格窗口**：问题在这 10 格里滚动，中间那格 = 当前选中，按住继续滑就继续跳问题；</li>
 *   <li><b>半透明灰底</b>：刻度后面垫一块半透明灰底（v5 重写时丢了，用户点名要回来）；</li>
 *   <li><b>方向</b>：**上翻 = 往期问题，下翻 = 最新问题**（之前是反的，用户明确纠正）；</li>
 *   <li><b>预览要像完整聊天气泡</b>：品牌渐变 + 白字 + 圆角、最多 8 行、跟着手指走 ——
 *       不再画那个"小提示条"（真机上被聊天气泡压掉一半，用户说"一直显示不全"）。</li>
 * </ol>
 */
public final class MessageScrubber extends View {

    public interface OnPick { void onPick(int index); }

    /** 右缘可触摸带宽度。 */
    private static final float STRIP_DP = 40f;
    /** 固定 10 格。 */
    private static final int SLOTS = 10;
    /** 格距（也是"滑多少像素换一格"的灵敏度，1:1 跟手）。 */
    private static final float SLOT_STEP_DP = 13f;
    private static final float TICK_W_DP = 13f;
    private static final float TICK_H_DP = 2.6f;
    private static final float SEL_W_DP = 22f;
    private static final float SEL_H_DP = 3.6f;
    private static final float RAIL_W_DP = 30f;
    /** 预览气泡。 */
    private static final float BUBBLE_W_DP = 300f;
    private static final float BUBBLE_MAX_H_DP = 236f;
    private static final int BUBBLE_MAX_LINES = 8;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

    private java.util.List<String> labels = new java.util.ArrayList<>();
    private OnPick onPick;
    private boolean dragging;
    /** 滚轮位置（浮点：四舍五入 = 选中的下标；小数部分用于滚动中的错位感）。 */
    private float posF;
    private int sel;
    private float lastY;
    /** 手指当前纵向位置（预览气泡跟着它走）。 */
    private float fingerY;

    public MessageScrubber(Context c) {
        super(c);
        setWillNotDraw(false);
    }

    public void setLabels(java.util.List<String> l) {
        labels = (l == null) ? new java.util.ArrayList<String>() : l;
        if (sel >= labels.size()) sel = Math.max(0, labels.size() - 1);
        posF = sel;
        invalidate();
    }

    public void setOnPick(OnPick o) { onPick = o; }

    private float dp(float v) { return Ui.dp(getContext(), v); }

    private boolean dark() { return Ui.isDark(); }

    private boolean inStrip(float x) { return x >= getWidth() - dp(STRIP_DP); }

    private boolean inBottomZone(float y) { return y > getHeight() - dp(170f); }

    /** 10 格窗口的垂直中心（列表区正中偏上，避开底部输入区）。 */
    private float centerY() {
        return (getHeight() - dp(170f)) / 2f + dp(40f);
    }

    /** 第 k 格（0..SLOTS-1）的 y：固定 10 格 + 滚动错位（posF 的小数部分）。 */
    private float slotY(int k) {
        float frac = posF - Math.round(posF);
        return centerY() + (k - (SLOTS - 1) / 2f) * dp(SLOT_STEP_DP) + frac * dp(SLOT_STEP_DP);
    }

    @Override
    protected void onDraw(Canvas cv) {
        int m = labels.size();
        if (getWidth() <= 0 || getHeight() <= 0 || m <= 0) return;

        // 灰底带的中线：**刻度和灰底都以此为中心**（用户反馈"刻度没在灰底中间、偏右"）
        float railCx = getWidth() - dp(9f) - dp(RAIL_W_DP) / 2f;
        float right = railCx + dp(RAIL_W_DP) / 2f;      // 灰底右缘
        float top = slotY(0) - dp(SLOT_STEP_DP) / 2f;
        float bot = slotY(SLOTS - 1) + dp(SLOT_STEP_DP) / 2f;

        // ---- ① 半透明灰底（用户点名要回来的"底图"）
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        // **只留一点点透**（2026-10-05 用户反馈：太透会和下层聊天内容重叠、两层字糊在一起 ✗）：
        // 浅色档 92% 近白、深色档 92% 近黑 —— 既压住背后的内容，又保留一丝玻璃感。
        p.setColor(dark() ? 0xEB1C1B22 : 0xEBF7F6FB);
        float railR = dp(RAIL_W_DP / 2f);
        rf.set(right - dp(RAIL_W_DP), top, right, bot);
        cv.drawRoundRect(rf, railR, railR, p);
        // 发丝描边：让这块面板和背后的内容**明确分开**（不然边界还是"糊"的）
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, dp(0.8f)));
        p.setColor(Ui.HAIRLINE);
        cv.drawRoundRect(rf, railR, railR, p);
        p.setStyle(Paint.Style.FILL);

        // ---- ② 中间那格的"定位槽"（模拟波轮卡槽，先画在刻度下面）
        int mid = SLOTS / 2;
        float cy = slotY(mid);
        p.setColor(dark() ? 0x26FFFFFF : 0x0A000000);
        rf.set(railCx - dp(SEL_W_DP) / 2f - dp(3f), cy - dp(SEL_H_DP) * 1.5f,
                railCx + dp(SEL_W_DP) / 2f + dp(3f), cy + dp(SEL_H_DP) * 1.5f);
        cv.drawRoundRect(rf, dp(6f), dp(6f), p);

        // ---- ③ 固定 10 格刻度：中间那格 = 当前选中
        for (int k = 0; k < SLOTS; k++) {
            float y = slotY(k);
            if (y < top - dp(2f) || y > bot + dp(2f)) continue;
            boolean cur = (k == mid);
            float w = cur ? dp(SEL_W_DP) : dp(TICK_W_DP);
            float half = cur ? dp(SEL_H_DP) / 2f : dp(TICK_H_DP) / 2f;
            p.setColor(cur ? Ui.BRAND : (dark() ? 0xB3FFFFFF : 0x99000000));
            // **以灰底中线为中心**画刻度（左右对称），不再右对齐
            rf.set(railCx - w / 2f, y - half, railCx + w / 2f, y + half);
            cv.drawRoundRect(rf, half, half, p);
        }

        if (dragging && sel >= 0 && sel < m) drawBubble(cv, m);
    }

    /**
     * 预览气泡：**和对话里用户气泡同款**（品牌渐变 + 白字 + 圆角），内容尽量完整（最多 8 行）。
     * 用户要求"显示完整、要跟聊天气泡一样"，所以这里不做单行截断。
     */
    private void drawBubble(Canvas cv, int m) {
        String txt = labels.get(sel) == null ? "" : labels.get(sel).trim();
        if (txt.isEmpty()) txt = "(空消息)";

        float w = dp(BUBBLE_W_DP);
        float maxH = dp(BUBBLE_MAX_H_DP);
        float padL = dp(14f), padR = dp(14f), padT = dp(26f), padB = dp(14f);
        float lineH = dp(19f);

        // 先按行数定高度：内容短就小气泡，长就高一点（不超上限）
        java.util.List<String> lines = wrap(txt, w - padL - padR, BUBBLE_MAX_LINES);
        float h = padT + lines.size() * lineH + padB;
        if (h > maxH) h = maxH;

        float right = getWidth() - dp(STRIP_DP) - dp(4f);
        float left = right - w;
        // **固定在画面中间**（用户要求）：原来跟着手指纵向跑，滑起来气泡上下乱跳 ✗；
        // 现在与刻度尺灰底/刻度**共用同一根中轴**（centerY），三者对齐看着才整齐。
        float top = centerY() - h / 2f;

        // 气泡本体（与对话里用户气泡同一套渐变）
        p.setShader(new LinearGradient(left, top, left + w, top + h,
                Ui.BRAND_G1, Ui.BRAND_G2, Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL);
        rf.set(left, top, right, top + h);
        cv.drawRoundRect(rf, dp(16f), dp(16f), p);
        p.setShader(null);

        // 第一行：第 N / M 条
        p.setColor(0xCCFFFFFF);
        p.setTextSize(dp(11f));
        cv.drawText("第 " + (sel + 1) + " / " + m + " 条", left + padL, top + dp(17f), p);

        // 正文：白色、多行
        p.setColor(0xFFFFFFFF);
        p.setTextSize(dp(13.5f));
        float y = top + padT + dp(12f);
        for (String ln : lines) {
            if (y > top + h - dp(4f)) break;      // 超出气泡高度就停（h 已封顶）
            cv.drawText(ln, left + padL, y, p);
            y += lineH;
        }
    }

    /** 按宽度折行（最多 maxLines 行；最后一行加省略号）。 */
    private java.util.List<String> wrap(String s, float maxW, int maxLines) {
        java.util.List<String> out = new java.util.ArrayList<>();
        p.setTextSize(dp(13.5f));
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '\n') {
                out.add(cur.toString());
                cur.setLength(0);
                i++;
                if (out.size() >= maxLines) break;
                continue;
            }
            if (cur.length() > 0 && p.measureText(cur.toString() + ch) > maxW) {
                if (out.size() == maxLines - 1) {
                    while (cur.length() > 0 && p.measureText(cur + "…") > maxW) {
                        cur.deleteCharAt(cur.length() - 1);
                    }
                    out.add(cur + "…");
                    return out;
                }
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(ch);
            i++;
        }
        if (cur.length() > 0 && out.size() < maxLines) out.add(cur.toString());
        return out;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (!inStrip(e.getX()) || inBottomZone(e.getY())) return false;   // 放手给列表
                int m = labels.size();
                if (m < 1) return false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                dragging = true;
                lastY = e.getY();
                fingerY = e.getY();
                invalidate();
                Ui.haptic(this);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) return false;
                int m = labels.size();
                if (m <= 0) return false;
                float dy = e.getY() - lastY;
                lastY = e.getY();
                fingerY = e.getY();
                // **方向**（用户明确纠正）：上翻 = 往期（下标变小）；下翻 = 最新（下标变大）。
                // 手指下滑 dy>0 → posF 增大 → 更新的一条 —— 与"往下滚看到更新的内容"一致。
                posF += dy / Math.max(1f, dp(SLOT_STEP_DP));
                if (posF < 0f) posF = 0f;
                if (posF > m - 1f) posF = m - 1f;
                int idx = Math.round(posF);
                if (idx != sel) {
                    sel = idx;
                    Ui.haptic(this);      // 一格一"咔"
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!dragging) return false;
                dragging = false;
                int idx = sel;
                boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                invalidate();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                if (up && idx >= 0 && onPick != null) onPick.onPick(idx);
                return true;
            }
            default:
                return dragging;
        }
    }
}
