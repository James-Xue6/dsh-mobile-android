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

        // 顶部抓手
        View bar = new View(ctx);
        bar.setBackground(Ui.pill(Ui.LINE));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(ctx, 40), Ui.dp(ctx, 4));
        blp.gravity = Gravity.CENTER_HORIZONTAL;
        blp.bottomMargin = Ui.dp(ctx, 10);
        bar.setLayoutParams(blp);
        box.addView(bar);

        int n = children == null ? 0 : children.size();
        box.addView(Ui.text(ctx, "子智能体", 17f, Ui.INK, true));
        TextView sub = Ui.text(ctx, n + " 个子会话 · 点一项切过去看它在做什么", 12.5f, Ui.INK_SUB, false);
        sub.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 6));
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
            TextView empty = Ui.text(ctx, "这条会话名下还没有子智能体", 13f, Ui.INK_FAINT, false);
            empty.setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 10), Ui.dp(ctx, 4), Ui.dp(ctx, 10));
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
        }
        dlg.show();
    }

    /** 一行：左「主/子」标记 + 标题 + 状态·时间·工作区。当前项高亮（与抽屉卡片同一套配色）。 */
    private static LinearLayout entry(Context ctx, final Dialog dlg, String kind, String title,
                                      String glyph, String status, int statusColor, long updatedAt,
                                      boolean current, boolean isParent, final SessionInfo target,
                                      final Host host) {
        LinearLayout row = Ui.row(ctx);
        row.setPadding(Ui.dp(ctx, 13), Ui.dp(ctx, 12), Ui.dp(ctx, 13), Ui.dp(ctx, 12));
        row.setBackground(Ui.roundStroke(Ui.dp(ctx, 14),
                (current || isParent) ? Ui.BRAND_SOFT : Ui.FIELD_BG,
                Ui.dp(ctx, current ? 1.6f : 0.8f), current ? Ui.BRAND : Ui.LINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(ctx, 6);
        row.setLayoutParams(lp);

        TextView tag = Ui.text(ctx, glyph, 12f, current ? Ui.ON_BRAND : Ui.INK_SUB, true);
        tag.setGravity(Gravity.CENTER);
        tag.setBackground(Ui.pill(current ? Ui.BRAND_FILL : Ui.LINE));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Ui.dp(ctx, 26), Ui.dp(ctx, 26));
        tlp.rightMargin = Ui.dp(ctx, 10);
        tag.setLayoutParams(tlp);
        row.addView(tag);

        LinearLayout texts = Ui.col(ctx);
        LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        texts.setLayoutParams(xlp);

        TextView t = Ui.text(ctx, kind + " · " + (title == null || title.isEmpty() ? "未命名会话" : title),
                14.5f, current ? Ui.BRAND_DEEP : Ui.INK, true);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(t);

        StringBuilder meta = new StringBuilder(status);
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
        TextView m = Ui.text(ctx, meta.toString(), 12f, statusColor, false);
        m.setSingleLine(true);
        m.setEllipsize(android.text.TextUtils.TruncateAt.END);
        m.setPadding(0, Ui.dp(ctx, 3), 0, 0);
        texts.addView(m);
        row.addView(texts);

        if (current) {
            TextView now = Ui.text(ctx, "在看", 11.5f, Ui.ON_BRAND, true);
            now.setPadding(Ui.dp(ctx, 9), Ui.dp(ctx, 4), Ui.dp(ctx, 9), Ui.dp(ctx, 4));
            now.setBackground(Ui.pill(Ui.BRAND_FILL));
            row.addView(now);
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
        MaxHeightScrollView(Context c) { super(c); }

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
