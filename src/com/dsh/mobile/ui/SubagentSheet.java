package com.dsh.mobile.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dsh.mobile.model.SessionInfo;

import java.util.List;

/**
 * 子智能体列表底部弹窗（手搓，不引入 Material BottomSheet 依赖）。
 *
 * 与 PC 端同语义：进主会话后，输入框上方那行「👥 N 子智能体」点开就是这里 ——
 * 第一行永远是**主智能体**（可点回主），下面按更新时间倒序列出它的全部子会话，
 * 当前正在看的那一项高亮。点任意一项 = 切到那条对话（走 MainActivity.onOpenSession，
 * 与抽屉里点会话卡片是同一条路）。
 *
 * 样式全部复用 Ui.*（sheetCard / pill / roundStroke / ago），与其他底部弹窗一致。
 */
public final class SubagentSheet {

    public interface Host {
        /** 选中一项（主智能体或某个子会话）后切过去。 */
        void onPickSession(SessionInfo s);
    }

    private SubagentSheet() { }

    /**
     * @param parent     当前「主智能体」（子会话在它名下）；为 null 时第一行退化成占位行
     * @param children   该主智能体的子会话，已按更新时间倒序
     * @param currentId  当前正在看的会话 id（用于高亮）
     */
    public static void show(Context ctx, SessionInfo parent, List<SessionInfo> children,
                            String currentId, final Host host) {
        final Dialog dlg = new Dialog(ctx);

        LinearLayout box = Ui.sheetCard(ctx);

        // 顶部抓手（iOS grabber）
        box.addView(Ui.grabber(ctx));

        int n = children == null ? 0 : children.size();
        box.addView(Ui.text(ctx, "子智能体", Ui.S_TITLE3, Ui.INK, true));
        TextView sub = Ui.text(ctx, n + " 个子会话 · 点一项切过去看它在做什么",
                Ui.S_FOOT, Ui.INK_SUB, false);
        sub.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 8));
        box.addView(sub);

        LinearLayout rows = Ui.col(ctx);

        // ① 主智能体单独一行在最上面：任何时候都能点回主
        if (parent != null) {
            boolean isCurrent = parent.id != null && parent.id.equals(currentId);
            rows.addView(entry(ctx, dlg, "主智能体", parent.display(), "主",
                    parent.running ? "● 运行中" : "○ 已完成",
                    parent.running ? Ui.WARN : Ui.INK_FAINT,
                    parent.updatedAt, isCurrent, true, parent, host));
        }

        // ② 子会话，按传入顺序（更新时间倒序）
        for (int i = 0; i < n; i++) {
            SessionInfo s = children.get(i);
            boolean isCurrent = s.id != null && s.id.equals(currentId);
            String status;
            int statusColor;
            if (s.pending == 1) { status = "❓ 待回答"; statusColor = Ui.BRAND; }
            else if (s.pending == 2) { status = "⚠ 待批准"; statusColor = Ui.WARN; }
            else if (s.running) { status = "● 运行中"; statusColor = Ui.WARN; }
            else { status = "○ 已完成"; statusColor = Ui.INK_FAINT; }
            rows.addView(entry(ctx, dlg, "子智能体 " + (i + 1), s.display(), "子",
                    status, statusColor, s.updatedAt, isCurrent, false, s, host));
        }

        if (n == 0) {
            TextView empty = Ui.text(ctx, "这条会话名下还没有子智能体", Ui.S_FOOT, Ui.INK_FAINT, false);
            empty.setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 12), Ui.dp(ctx, 4), Ui.dp(ctx, 12));
            rows.addView(empty);
        }

        ScrollView sc = new MaxHeightScrollView(ctx);
        sc.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        sc.setVerticalScrollBarEnabled(false);
        sc.addView(rows);
        box.addView(sc);

        TextView cancel = Ui.secondaryButton(ctx, "关闭");
        cancel.setOnClickListener(v -> dlg.dismiss());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(ctx, 10);
        cancel.setLayoutParams(clp);
        box.addView(cancel);

        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(box);
        dlg.setCanceledOnTouchOutside(true);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            // 弹窗里会出现会话标题等会话内容：与主窗口共用同一条「允许截屏」策略
            Ui.applyScreenshotPolicy(w);
            w.setDimAmount(0.35f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            // **不要给这个弹窗挂窗口背景模糊（2026-10-04 真机实测）**：
            // 旧版加了 FLAG_BLUR_BEHIND + setBackgroundBlurRadius，配合"透明窗口底 + 面板自绘底"
            // 在滑动后会出现**面板底整层丢失**（背后聊天文字透上来、看起来"一滑动就变色"）。
            // 去掉模糊后遮罩仍在，视觉几乎无差别，但不再触发那条有问题的窗口合成路径。
            // 面板本身是不透明的（Ui.sheetCard），可读性不受影响。
        }
        dlg.show();
    }

    /** 一行：左「主/子」标记 + 标题 + 状态·时间·工作区。当前项高亮（iOS 内嵌列表行）。 */
    private static LinearLayout entry(Context ctx, final Dialog dlg, String kind, String title,
                                      String glyph, String status, int statusColor, long updatedAt,
                                      boolean current, boolean isParent, final SessionInfo target,
                                      final Host host) {
        LinearLayout row = Ui.row(ctx);
        row.setMinimumHeight(Ui.dp(ctx, 58));
        row.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12));
        // 选中 = 浅灰圆角胶囊（对齐参考图侧栏选中行），**不是**蓝底白字
        android.graphics.drawable.GradientDrawable bg = Ui.round(Ui.dp(ctx, 12),
                (current || isParent) ? Ui.SELECT_BG : Ui.FIELD_BG);
        Ui.tapRow(row, bg, Ui.PRESS);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(ctx, 8);
        row.setLayoutParams(lp);

        TextView tag = Ui.text(ctx, glyph, Ui.S_CAP1, current ? Ui.ON_BRAND : Ui.INK_SUB, true);
        tag.setGravity(Gravity.CENTER);
        tag.setBackground(Ui.round(Ui.dp(ctx, 7), current ? Ui.BRAND_FILL : Ui.CHIP_BG));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Ui.dp(ctx, 26), Ui.dp(ctx, 26));
        tlp.rightMargin = Ui.dp(ctx, 10);
        tag.setLayoutParams(tlp);
        row.addView(tag);

        LinearLayout texts = Ui.col(ctx);
        LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        texts.setLayoutParams(xlp);

        TextView t = Ui.text(ctx, kind + " · " + (title == null || title.isEmpty() ? "未命名会话" : title),
                Ui.S_BODY, Ui.INK, current);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(t);

        // 状态里的 "●/○" 字符换成真圆点：矢量圆点不受字体/字重影响，垂直位置永远正确
        String statusText = status == null ? "" : status;
        int dotColor = 0;
        if (statusText.startsWith("● ")) {
            dotColor = statusColor;
            statusText = statusText.substring(2);
        } else if (statusText.startsWith("○ ")) {
            dotColor = Ui.alpha(Ui.INK_FAINT, 0.9f);
            statusText = statusText.substring(2);
        }

        StringBuilder meta = new StringBuilder(statusText);
        if (updatedAt > 0) {
            String ago = Ui.ago(updatedAt);
            if (!ago.isEmpty()) meta.append(" · ").append(ago);
        }
        String cwd = target.cwd == null ? "" : target.cwd.replace('\\', '/');
        if (!cwd.isEmpty()) {
            int i = cwd.lastIndexOf('/');
            String tail = i >= 0 ? cwd.substring(i + 1) : cwd;
            if (!tail.isEmpty()) meta.append(" · ").append(tail);
        }
        if (target.agentPreset != null && !target.agentPreset.isEmpty()) {
            meta.append(" · ").append(target.agentPreset);
        }
        TextView m = Ui.text(ctx, meta.toString(), Ui.S_FOOT, statusColor, false);
        m.setSingleLine(true);
        m.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout metaRow = Ui.row(ctx);
        metaRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        metaRow.setPadding(0, Ui.dp(ctx, 3), 0, 0);
        if (dotColor != 0) {
            View d = Ui.dot(ctx, 7f, dotColor);
            LinearLayout.LayoutParams dlp = (LinearLayout.LayoutParams) d.getLayoutParams();
            dlp.rightMargin = Ui.dp(ctx, 6);
            metaRow.addView(d);
        }
        m.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        metaRow.addView(m);
        texts.addView(metaRow);
        row.addView(texts);

        if (current) {
            // 选中标记 = 一枚蓝色对勾（**无底色**）。参考图里选中行只有「浅灰胶囊 + 蓝色小图标」，
            // 不再叠一个蓝底白字的「在看」胶囊 —— 那正是用户点名要去掉的"蓝底高亮"。
            row.addView(Ui.iconBox(ctx, com.dsh.mobile.R.drawable.ic_check,
                    0x00000000, Ui.BRAND, 20f, 0f, 18f));
        } else {
            row.addView(Ui.chevron(ctx));
        }

        row.setClickable(true);
        row.setOnClickListener(v -> {
            dlg.dismiss();
            if (host != null) host.onPickSession(target);
        });
        return row;
    }

    /**
     * 高度封顶的 ScrollView：子智能体可能几十个，横屏时屏高更矮 ——
     * 不封顶会让弹窗顶出屏幕、连「关闭」都点不到。上限取窗口高度的 55%。
     */
    private static final class MaxHeightScrollView extends ScrollView {
        MaxHeightScrollView(Context c) {
            super(c);
            // **真机 bug（2026-10-04 用户报「滑动就会变色」）**：内容不满一屏时，任何滑动都
            // 立刻越界，Android 默认会画一圈 **overscroll 光晕**（颜色取 colorPrimary，
            // 本 App 是紫蓝）—— 看起来就是"一滑动弹窗就变色"。iOS 没有这种光晕，直接关掉。
            setOverScrollMode(OVER_SCROLL_NEVER);
            setVerticalScrollBarEnabled(false);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int cap = Math.round(getResources().getDisplayMetrics().heightPixels * 0.55f);
            int mode = MeasureSpec.getMode(heightSpec);
            int size = MeasureSpec.getSize(heightSpec);
            if (mode == MeasureSpec.UNSPECIFIED || size > cap) {
                heightSpec = MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST);
            }
            super.onMeasure(widthSpec, heightSpec);
        }
    }
}
