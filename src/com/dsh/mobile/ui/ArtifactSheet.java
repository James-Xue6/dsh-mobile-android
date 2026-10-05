package com.dsh.mobile.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dsh.mobile.model.ArtifactEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「生成物」面板（2026-10-05 任务①）：列本会话工作目录里的产出文件，点一下打开/下载。
 *
 * <p><b>⚠️ 当前未被使用（2026-10-05 主理人核对）：</b>wire 层先一步在
 * {@code MainActivity.showArtifactsPanel/renderArtifacts} 里实现了同一个面板，
 * 而且更完整（路径面包屑 + 「← 返回上级」+ 下载进度回填 + 55% 高度封顶），
 * 所以线上走的是那一份，本类目前是**备用实现、没有任何调用点**。
 * 保留它是因为它已通过编译、且提供 {@link #setRowState} 这种按行回报进度的能力，
 * 将来若要统一到 ui 层可以直接替换；**不要**误以为它是生效路径。
 * 若确定不用，删掉本文件即可（不影响其它任何文件）。
 *
 * <p>数据来自网关 {@code file-list} 帧（见 {@link ArtifactEntry} 的注释与
 * {@code dsh-plugin-mobile-gateway/PROTOCOL.md} §「文件下载」）。本面板**只负责渲染与回调**：
 * 真正的下载链路（{@code file-download-open/read/cancel}）已经在 wire 里实现，本类不碰。
 *
 * <p>安全边界：列表项只来自网关对"该会话工作目录"的列举结果；面板不提供任何"手输路径"
 * 入口，也不做路径拼接 —— 下载能不能成功最终由网关的工作目录校验决定（wire 的
 * {@code relativize} 那一层也再挡一次）。
 *
 * <p>样式与 {@link ModelSheet} 同一套：{@link Ui#sheetCard} + 抓柄 + 底部弹出 + 遮罩，
 * 不挂窗口背景模糊（见 {@link Ui#sheetCard} 的真机结论）。
 */
public final class ArtifactSheet {

    /** 点一个条目（文件 = 打开/下载；目录 = 由 wire 决定是否再列一层）。 */
    public interface OnPick {
        void onPick(ArtifactEntry e);
    }

    private final Context ctx;
    private final Dialog dlg;
    private final LinearLayout rows;
    private final OnPick onPick;
    private final Runnable onRefresh;
    /** path -> 该行的「下载状态」小字（wire 用它回报进度）。 */
    private final Map<String, TextView> stateViews = new HashMap<>();
    private List<ArtifactEntry> entries = new ArrayList<>();

    private ArtifactSheet(Context ctx, Dialog dlg, LinearLayout rows,
                          OnPick onPick, Runnable onRefresh) {
        this.ctx = ctx;
        this.dlg = dlg;
        this.rows = rows;
        this.onPick = onPick;
        this.onRefresh = onRefresh;
    }

    /**
     * 弹面板。
     *
     * @param entries   首次拿到的列表（可为空 = 显示空态）
     * @param onPick    点条目回调
     * @param onRefresh 点「刷新」回调（wire 在这里重新 requestFileList）
     * @return 面板实例（wire 用 {@link #setEntries} / {@link #setRowState} 回灌）；ctx 为空返回 null
     */
    public static ArtifactSheet show(Context ctx, String title, List<ArtifactEntry> entries,
                                     OnPick onPick, Runnable onRefresh) {
        if (ctx == null) return null;
        final Dialog dlg = new Dialog(ctx);

        LinearLayout box = Ui.sheetCard(ctx);
        box.addView(Ui.grabber(ctx));

        // 标题行：标题 + 右侧「刷新」（列表来自网关，刷一下就能看到新产出的文件）
        LinearLayout head = Ui.row(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.text(ctx, title == null || title.isEmpty() ? "生成物" : title,
                Ui.S_TITLE3, Ui.INK, true);
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(t);
        TextView refresh = Ui.textButton(ctx, "刷新", Ui.BRAND);
        refresh.setOnClickListener(v -> { Ui.haptic(v); if (onRefresh != null) onRefresh.run(); });
        head.addView(refresh);
        box.addView(head);

        TextView sub = Ui.text(ctx, "这个会话工作目录里的文件 · 点一下下载到手机",
                Ui.S_FOOT, Ui.INK_SUB, false);
        sub.setPadding(0, Ui.dp(ctx, 2), 0, Ui.dp(ctx, 8));
        box.addView(sub);

        LinearLayout rows = Ui.col(ctx);
        rows.setLayoutParams(Ui.fill());
        ScrollView sc = new MaxHeightScrollView(ctx);
        sc.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        sc.addView(rows);
        box.addView(sc);

        TextView close = Ui.secondaryButton(ctx, "关闭");
        close.setOnClickListener(v -> dlg.dismiss());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(ctx, 10);
        close.setLayoutParams(clp);
        box.addView(close);

        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(box);
        dlg.setCanceledOnTouchOutside(true);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            Ui.applyScreenshotPolicy(w);
            w.setDimAmount(0.35f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        ArtifactSheet sheet = new ArtifactSheet(ctx, dlg, rows, onPick, onRefresh);
        sheet.setEntries(entries);
        dlg.show();
        return sheet;
    }

    /** 换一整份列表（首次回帧 / 点刷新后 / 换会话）。空列表 → 空态文案。 */
    public void setEntries(List<ArtifactEntry> list) {
        if (rows == null) return;
        entries = list == null ? new ArrayList<ArtifactEntry>() : new ArrayList<>(list);
        stateViews.clear();
        rows.removeAllViews();
        if (entries.isEmpty()) {
            TextView empty = Ui.text(ctx, "这个会话还没有产出文件。\n让 Agent 做完一个任务后再来看。",
                    Ui.S_FOOT, Ui.INK_FAINT, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(ctx, 22), 0, Ui.dp(ctx, 22));
            rows.addView(empty);
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            rows.addView(row(entries.get(i)));
            if (i < entries.size() - 1) rows.addView(Ui.cardDivider(ctx));
        }
    }

    /**
     * 更新某一行的状态小字（wire 的下载进度/结果写在这里）。
     *
     * @param ok true = 成功配色（{@link Ui#LINK_OK}），false = 普通灰字
     */
    public void setRowState(String path, String stateText, boolean ok) {
        if (path == null) return;
        TextView v = stateViews.get(path);
        if (v == null) return;
        if (stateText == null || stateText.trim().isEmpty()) {
            v.setVisibility(TextView.GONE);
            return;
        }
        v.setText(stateText);
        v.setTextColor(ok ? Ui.LINK_OK : Ui.INK_SUB);
        v.setVisibility(TextView.VISIBLE);
    }

    public boolean isShowing() { return dlg != null && dlg.isShowing(); }

    public void dismiss() {
        try { if (dlg != null) dlg.dismiss(); } catch (Throwable ignored) { }
    }

    /** 一行条目：图标 + 文件名 + 元信息（类型/大小/时间）+ 下载状态小字。 */
    private LinearLayout row(final ArtifactEntry e) {
        LinearLayout r = Ui.col(ctx);
        r.setPadding(Ui.dp(ctx, 13), Ui.dp(ctx, 11), Ui.dp(ctx, 13), Ui.dp(ctx, 11));
        r.setMinimumHeight(Ui.dp(ctx, 56));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(ctx, 3);
        r.setLayoutParams(lp);
        Ui.tapRow(r, Ui.round(Ui.dp(ctx, 12), Ui.FIELD_BG), Ui.PRESS);

        LinearLayout line = Ui.row(ctx);
        line.setGravity(Gravity.CENTER_VERTICAL);
        int icon = (e.mediaType != null && e.mediaType.startsWith("image/"))
                ? com.dsh.mobile.R.drawable.ic_image
                : com.dsh.mobile.R.drawable.ic_folder;
        line.addView(Ui.iconBox(ctx, icon, Ui.BRAND_SOFT, Ui.BRAND, 34f, 10f, 18f));

        LinearLayout texts = Ui.col(ctx);
        texts.setPadding(Ui.dp(ctx, 11), 0, 0, 0);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = Ui.text(ctx, e.name.isEmpty() ? "(未命名)" : e.name,
                Ui.S_BODY, Ui.BRAND, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        texts.addView(name);

        String meta = metaLine(e);
        if (!meta.isEmpty()) {
            TextView m = Ui.text(ctx, meta, Ui.S_CAP1, Ui.INK_SUB, false);
            m.setSingleLine(true);
            m.setEllipsize(android.text.TextUtils.TruncateAt.END);
            m.setPadding(0, Ui.dp(ctx, 3), 0, 0);
            texts.addView(m);
        }

        // 下载状态小字（默认隐藏；wire 用 setRowState 写）
        TextView state = Ui.text(ctx, "", Ui.S_CAP1, Ui.INK_SUB, false);
        state.setSingleLine(true);
        state.setEllipsize(android.text.TextUtils.TruncateAt.END);
        state.setPadding(0, Ui.dp(ctx, 3), 0, 0);
        state.setVisibility(TextView.GONE);
        texts.addView(state);
        stateViews.put(e.path, state);

        line.addView(texts);
        r.addView(line);

        r.setClickable(true);
        r.setOnClickListener(v -> {
            Ui.haptic(v);
            if (onPick != null) onPick.onPick(e);
        });
        return r;
    }

    /** 元信息一行：「文件夹 / 文件 · 1.2 MB · 3 分钟前」。 */
    private static String metaLine(ArtifactEntry e) {
        StringBuilder sb = new StringBuilder(e.isDirectory() ? "文件夹 · 点开查看" : "文件");
        String size = e.sizeLabel();
        if (!size.isEmpty()) sb.append(" · ").append(size);
        String ago = Ui.ago(e.modifiedAt);
        if (!ago.isEmpty()) sb.append(" · ").append(ago);
        return sb.toString();
    }

    /**
     * 高度封顶的 ScrollView（与 {@link ModelSheet} 同款）：文件可能几十个，
     * 不封顶会把面板顶出屏幕、连「关闭」都点不到。
     */
    private static final class MaxHeightScrollView extends ScrollView {
        MaxHeightScrollView(Context c) {
            super(c);
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
