package com.dsh.mobile.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dsh.mobile.model.SessionInfo;

import java.util.ArrayList;
import java.util.List;

/** 会话列表（豆包式卡片列表 + 悬浮新建按钮）。 */
public final class SessionListView extends FrameLayout {

    public interface Host {
        void onOpenSession(SessionInfo s);
        void onNewChat();
        void onSettings();
        /** 回「我的设备」启动页（抽屉头部的设备入口）。 */
        void onDevices();
        void onRefresh();
        void onRename(SessionInfo s);
        void onArchive(SessionInfo s);
        /** 展开/折叠某父会话名下的子会话（子智能体 / 专家团）。 */
        void onToggleChildren(SessionInfo s);
    }

    private final Context ctx;
    private final Host host;
    private final TextView statusLine;
    private final SessionAdapter adapter;
    /** 空态提示（抽屉里一个会话都没有时显示，比如"还没有对话"）。 */
    private final TextView emptyView;
    /** 头部区与其上的圆形入口（主题切换要逐个重刷底色/字色）。 */
    private final LinearLayout header;
    private final TextView headerTitle;
    private final TextView refreshBtn;
    private final TextView devicesBtn;
    private final TextView gearBtn;
    /** 悬浮「＋ 新建」。 */
    private final TextView fab;
    /** 当前正在看的会话 id：抽屉里对应卡片高亮，一眼看出"我现在在这个任务里"。 */
    private String currentId = "";

    public SessionListView(Context ctx, Host host) {
        super(ctx);
        this.ctx = ctx;
        this.host = host;
        setBackgroundColor(Ui.BG);

        LinearLayout root = Ui.col(ctx);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        addView(root);

        // ---- 头部
        header = Ui.col(ctx);
        header.setBackgroundColor(Ui.BG);
        header.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 16), Ui.dp(ctx, 18), Ui.dp(ctx, 6));

        LinearLayout top = Ui.row(ctx);
        TextView t = Ui.text(ctx, "对话", 27f, Ui.INK, true);
        headerTitle = t;
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        t.setLayoutParams(tlp);
        top.addView(t);

        TextView refresh = Ui.circleButton(ctx, "↻", android.graphics.Color.TRANSPARENT, Ui.INK_SUB);
        refreshBtn = refresh;
        refresh.setTextSize(19f);
        refresh.setOnClickListener(v -> host.onRefresh());
        top.addView(refresh);

        // 「我的设备」入口：启动页是设备页，这里给对话页一个随时回去看在线状态的入口
        TextView devices = Ui.circleButton(ctx, "🖥", android.graphics.Color.TRANSPARENT, Ui.INK_SUB);
        devicesBtn = devices;
        devices.setTextSize(17f);
        devices.setContentDescription("我的设备");
        devices.setOnClickListener(v -> host.onDevices());
        top.addView(devices);

        TextView gear = Ui.circleButton(ctx, "⚙", android.graphics.Color.TRANSPARENT, Ui.INK_SUB);
        gearBtn = gear;
        gear.setTextSize(19f);
        gear.setOnClickListener(v -> host.onSettings());
        top.addView(gear);
        header.addView(top);

        statusLine = Ui.text(ctx, "", 12.5f, Ui.INK_FAINT, false);
        statusLine.setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 2), 0, Ui.dp(ctx, 8));
        header.addView(statusLine);
        root.addView(header, Ui.fill());

        // ---- 列表
        adapter = new SessionAdapter();
        android.widget.ListView list = new android.widget.ListView(ctx);
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        list.setVerticalScrollBarEnabled(false);
        list.setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), Ui.dp(ctx, 90));
        list.setClipToPadding(false);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        list.setLayoutParams(llp);
        root.addView(list);

        // ---- 空态：一个会话都没有时给一句话，别让抽屉看起来像坏了
        emptyView = Ui.text(ctx, "还没有对话\n点右下角 ＋ 给 Agent 派个任务", 14f, Ui.INK_FAINT, false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setLineSpacing(Ui.dp(ctx, 6), 1.1f);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        elp.gravity = Gravity.CENTER;
        emptyView.setLayoutParams(elp);
        emptyView.setVisibility(GONE);
        addView(emptyView);

        // ---- 悬浮新建
        TextView fab = new TextView(ctx);
        this.fab = fab;
        fab.setText("＋");
        fab.setTextSize(24f);
        fab.setTextColor(Ui.ON_BRAND);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(Ui.pill(Ui.BRAND_FILL));
        fab.setElevation(Ui.dp(ctx, 6));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(ctx, 56), Ui.dp(ctx, 56));
        flp.gravity = Gravity.BOTTOM | Gravity.END;
        flp.rightMargin = Ui.dp(ctx, 18);
        flp.bottomMargin = Ui.dp(ctx, 26);
        fab.setLayoutParams(flp);
        fab.setOnClickListener(v -> host.onNewChat());
        addView(fab);
    }

    public void setStatus(String s) {
        statusLine.setText(s == null ? "" : s);
    }

    /**
     * 主题切换：按新色板重刷抽屉整页 + 强制会话卡片整表重画。
     * 卡片行由 SessionAdapter.getView **每次新建**（不复用 convertView），
     * 所以一次 notifyDataSetChanged() 就能让所有可见卡片换色。
     */
    public void applyTheme() {
        setBackgroundColor(Ui.BG);
        if (header != null) header.setBackgroundColor(Ui.BG);
        if (headerTitle != null) headerTitle.setTextColor(Ui.INK);
        if (refreshBtn != null) refreshBtn.setTextColor(Ui.INK_SUB);
        if (devicesBtn != null) devicesBtn.setTextColor(Ui.INK_SUB);
        if (gearBtn != null) gearBtn.setTextColor(Ui.INK_SUB);
        if (statusLine != null) statusLine.setTextColor(Ui.INK_FAINT);
        if (emptyView != null) emptyView.setTextColor(Ui.INK_FAINT);
        if (fab != null) {
            fab.setTextColor(Ui.ON_BRAND);
            fab.setBackground(Ui.pill(Ui.BRAND_FILL));
        }
        adapter.notifyDataSetChanged();
        requestLayout();
    }

    /** rows 元素为 String（工作区标题）或 SessionInfo（会话卡片）。 */
    public void setRows(List<Object> rows) {
        adapter.set(rows);
        boolean hasSession = false;
        if (rows != null) {
            for (Object r : rows) {
                if (r instanceof SessionInfo) { hasSession = true; break; }
            }
        }
        emptyView.setVisibility(hasSession ? GONE : VISIBLE);
    }

    /** 当前正在查看的会话 id（抽屉里对应卡片高亮）。 */
    public void setCurrentSession(String id) {
        String v = id == null ? "" : id;
        if (v.equals(currentId)) return;
        currentId = v;
        adapter.notifyDataSetChanged();
    }

    private final class SessionAdapter extends BaseAdapter {
        private List<Object> data = new ArrayList<>();

        void set(List<Object> l) {
            data = l == null ? new ArrayList<Object>() : l;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int position) { return data.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) {
            return data.get(position) instanceof String ? 0 : 1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Object row = data.get(position);
            if (row instanceof String) {
                LinearLayout head = Ui.col(ctx);
                head.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 16), 0, Ui.dp(ctx, 2));
                head.addView(Ui.text(ctx, (String) row, 12.5f, Ui.INK_SUB, true));
                return head;
            }
            SessionInfo s = (SessionInfo) row;
            // 当前正在看的那个会话：实心淡蓝底 + 品牌色描边，在抽屉里一眼认出来
            boolean current = s.id != null && !s.id.isEmpty() && s.id.equals(currentId);

            LinearLayout outer = Ui.col(ctx);
            // 子会话缩进 + 淡底：一眼看出它挂在上面那个父会话下面，而不是一条独立对话
            outer.setPadding(Ui.dp(ctx, 22) * s.childDepth, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4));

            LinearLayout card = Ui.row(ctx);
            card.setPadding(Ui.dp(ctx, 15), Ui.dp(ctx, 13), Ui.dp(ctx, 13), Ui.dp(ctx, 13));
            card.setBackground(Ui.roundStroke(Ui.dp(ctx, 16),
                    (current || s.childDepth > 0) ? Ui.BRAND_SOFT : Ui.SURFACE,
                    Ui.dp(ctx, current ? 1.6f : 0.8f), current ? Ui.BRAND : Ui.LINE));
            card.setElevation(Ui.dp(ctx, current ? 1.5f : 0.5f));

            LinearLayout texts = Ui.col(ctx);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            texts.setLayoutParams(tlp);

            String shownTitle = s.displayForList();
            if (s.childDepth > 0) shownTitle = "└ " + shownTitle;
            TextView title = Ui.text(ctx, shownTitle, 15.5f, current ? Ui.BRAND_DEEP : Ui.INK, true);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(title);

            StringBuilder sub = new StringBuilder();
            if (s.cwd != null && !s.cwd.isEmpty()) {
                String c = s.cwd.replace('\\', '/');
                int i = c.lastIndexOf('/');
                sub.append(i >= 0 ? c.substring(i + 1) : c);
            }
            if (s.updatedAt > 0) {
                if (sub.length() > 0) sub.append(" · ");
                sub.append(Ui.ago(s.updatedAt));
            }
            if (s.agentPreset != null && !s.agentPreset.isEmpty()) {
                if (sub.length() > 0) sub.append(" · ");
                sub.append(s.agentPreset);
            }
            if (sub.length() > 0) {
                TextView st = Ui.text(ctx, sub.toString(), 12f, Ui.INK_FAINT, false);
                st.setSingleLine(true);
                st.setEllipsize(android.text.TextUtils.TruncateAt.END);
                st.setPadding(0, Ui.dp(ctx, 3), 0, 0);
                texts.addView(st);
            }
            card.addView(texts);

            // 折叠开关：父会话名下挂着子智能体/专家团会话时给一个可点的入口（默认折叠）
            if (s.childCount > 0) {
                TextView tog = Ui.text(ctx, (s.expanded ? "▾ " : "▸ ") + s.childCount + " 子会话",
                        11.5f, Ui.BRAND, true);
                tog.setPadding(Ui.dp(ctx, 9), Ui.dp(ctx, 4), Ui.dp(ctx, 9), Ui.dp(ctx, 4));
                tog.setBackground(Ui.pill(Ui.BRAND_SOFT));
                LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                glp.rightMargin = Ui.dp(ctx, 6);
                tog.setLayoutParams(glp);
                tog.setOnClickListener(v -> host.onToggleChildren(s));
                card.addView(tog);
            }

            if (s.pending > 0) {
                TextView badge = Ui.text(ctx, s.pending == 1 ? "❓ 待回答" : "⚠ 待批准",
                        11.5f, s.pending == 1 ? Ui.ON_BRAND : Ui.ON_WARN, true);
                badge.setPadding(Ui.dp(ctx, 9), Ui.dp(ctx, 4), Ui.dp(ctx, 9), Ui.dp(ctx, 4));
                badge.setBackground(Ui.pill(s.pending == 1 ? Ui.BRAND_FILL : Ui.WARN));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                blp.rightMargin = Ui.dp(ctx, 6);
                badge.setLayoutParams(blp);
                card.addView(badge);
            } else if (s.running) {
                TextView dot = Ui.text(ctx, "● 运行中", 11.5f, Ui.WARN, false);
                dot.setPadding(Ui.dp(ctx, 6), 0, Ui.dp(ctx, 4), 0);
                card.addView(dot);
            } else {
                TextView chev = Ui.text(ctx, "›", 20f, Ui.INK_FAINT, false);
                chev.setPadding(Ui.dp(ctx, 6), 0, Ui.dp(ctx, 2), 0);
                card.addView(chev);
            }

            card.setOnClickListener(v -> host.onOpenSession(s));
            card.setOnLongClickListener(v -> {
                final String[] options = { "重命名", "归档（隐藏，不删除）" };
                Ui.dialog(ctx)
                        .setItems(options, (d, which) -> {
                            if (which == 0) host.onRename(s);
                            else host.onArchive(s);
                        })
                        .show();
                return true;
            });

            outer.addView(card);
            return outer;
        }
    }
}
