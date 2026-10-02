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
import com.dsh.mobile.model.WorkspaceGroup;

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
        /** 点某工作区分组标题右侧的「⋯」：弹出该工作区的操作菜单（新建任务等）。 */
        void onWorkspaceMenu(WorkspaceGroup g);
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

        // ---- 头部（iOS 大标题）
        header = Ui.col(ctx);
        header.setBackgroundColor(Ui.BG);
        header.setPadding(Ui.dp(ctx, Ui.M_SIDE), Ui.dp(ctx, 10), Ui.dp(ctx, 10), Ui.dp(ctx, 4));

        LinearLayout top = Ui.row(ctx);
        TextView t = Ui.text(ctx, "对话", Ui.S_LARGE, Ui.INK, true);
        headerTitle = t;
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        t.setLayoutParams(tlp);
        top.addView(t);

        TextView refresh = Ui.circleIconButton(ctx, com.dsh.mobile.R.drawable.ic_refresh,
                Ui.CHIP_BG, Ui.INK_SUB);
        refreshBtn = refresh;
        refresh.setContentDescription("刷新会话");
        refresh.setOnClickListener(v -> host.onRefresh());
        top.addView(refresh);

        // 「我的设备」入口：启动页是设备页，这里给对话页一个随时回去看在线状态的入口
        TextView devices = Ui.circleIconButton(ctx, com.dsh.mobile.R.drawable.ic_monitor,
                Ui.CHIP_BG, Ui.INK_SUB);
        devicesBtn = devices;
        devices.setContentDescription("我的设备");
        devices.setOnClickListener(v -> host.onDevices());
        top.addView(devices);

        TextView gear = Ui.circleIconButton(ctx, com.dsh.mobile.R.drawable.ic_sliders,
                Ui.CHIP_BG, Ui.INK_SUB);
        gearBtn = gear;
        gear.setContentDescription("连接设置");
        gear.setOnClickListener(v -> host.onSettings());
        top.addView(gear);
        header.addView(top);

        statusLine = Ui.text(ctx, "", Ui.S_FOOT, Ui.INK_FAINT, false);
        statusLine.setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 4), 0, Ui.dp(ctx, 6));
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
        // iOS 分组列表：左右外边距 16dp（全 App 统一 M_SIDE；组内条目自己画圆角与分隔线）
        list.setPadding(Ui.dp(ctx, Ui.M_SIDE), 0, Ui.dp(ctx, Ui.M_SIDE), Ui.dp(ctx, 90));
        list.setClipToPadding(false);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        list.setLayoutParams(llp);
        root.addView(list);

        // ---- 空态：一个安静的图标 + 一句灰字（不是干巴巴一行"空"）
        emptyView = Ui.text(ctx, "还没有对话\n点右下角 ＋ 给 Agent 派个任务", Ui.S_SUB, Ui.INK_FAINT, false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setLineSpacing(Ui.dp(ctx, 6), 1.1f);
        paintEmptyIcon();
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        elp.gravity = Gravity.CENTER;
        emptyView.setLayoutParams(elp);
        emptyView.setVisibility(GONE);
        addView(emptyView);

        // ---- 悬浮新建
        // 比例检查：FAB 56dp 是 iOS 的标准尺寸（触区本来就 ≥48dp，不缩），
        // 但里面的 ＋ 从 26dp 收到 24dp —— 26dp 是"图标比标准还大一号"，没有文字跟它配。
        TextView fab = Ui.circleIconButton(ctx, com.dsh.mobile.R.drawable.ic_plus,
                Ui.BRAND_FILL, Ui.ON_BRAND, 24f, 56f);
        this.fab = fab;
        fab.setElevation(Ui.dp(ctx, 6));
        fab.setContentDescription("新建对话");
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(ctx, 56), Ui.dp(ctx, 56));
        flp.gravity = Gravity.BOTTOM | Gravity.END;
        flp.rightMargin = Ui.dp(ctx, 18);
        flp.bottomMargin = Ui.dp(ctx, 26);
        fab.setLayoutParams(flp);
        fab.setOnClickListener(v -> host.onNewChat());
        addView(fab);
    }

    /** 空态图标：矢量显示器 + 一点透明度，颜色跟随当前主题。 */
    private void paintEmptyIcon() {
        if (emptyView == null) return;
        Ui.setTopIcon(emptyView, com.dsh.mobile.R.drawable.ic_monitor,
                Ui.alpha(Ui.INK_FAINT, 0.85f), Ui.I_EMPTY, 18f);
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
        if (refreshBtn != null) Ui.setIcon(refreshBtn, com.dsh.mobile.R.drawable.ic_refresh, Ui.INK_SUB);
        if (devicesBtn != null) Ui.setIcon(devicesBtn, com.dsh.mobile.R.drawable.ic_monitor, Ui.INK_SUB);
        if (gearBtn != null) Ui.setIcon(gearBtn, com.dsh.mobile.R.drawable.ic_sliders, Ui.INK_SUB);
        if (statusLine != null) statusLine.setTextColor(Ui.INK_FAINT);
        if (emptyView != null) {
            emptyView.setTextColor(Ui.INK_FAINT);
            paintEmptyIcon();
        }
        if (fab != null) Ui.setIcon(fab, com.dsh.mobile.R.drawable.ic_plus, Ui.ON_BRAND);
        adapter.notifyDataSetChanged();
        requestLayout();
    }

    /** rows 元素为 WorkspaceGroup（工作区标题）或 SessionInfo（会话卡片）。 */
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
            return data.get(position) instanceof WorkspaceGroup ? 0 : 1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Object row = data.get(position);
            if (row instanceof WorkspaceGroup) {
                // 工作区分组标题：iOS 组标题的规格（13sp 灰字 + 一点字间距），
                // 右侧挂一个「⋯」—— 用户要的就是"点开能在这个工作区里新建任务"。
                WorkspaceGroup g = (WorkspaceGroup) row;
                LinearLayout head = Ui.row(ctx);
                head.setGravity(Gravity.CENTER_VERTICAL);
                head.setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 10), 0, Ui.dp(ctx, 2));

                TextView gt = Ui.text(ctx, g.label, Ui.S_FOOT, Ui.INK_SUB, false);
                gt.setLetterSpacing(0.06f);
                gt.setSingleLine(true);
                gt.setEllipsize(android.text.TextUtils.TruncateAt.END);
                gt.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                head.addView(gt);

                // 没有工作目录的分组（「其他」/ IM 会话）不给「⋯」：点了也无处可派，
                // 挂一个摆设只会误导。整行的点击仍留给下面的会话卡片。
                if (g.canCreateSession()) {
                    TextView more = Ui.circleIconButton(ctx, com.dsh.mobile.R.drawable.ic_more,
                            android.graphics.Color.TRANSPARENT, Ui.INK_SUB, 15f, 28f);
                    more.setContentDescription("工作区选项：" + g.label);
                    more.setOnClickListener(v -> host.onWorkspaceMenu(g));
                    head.addView(more);
                }
                return head;
            }
            SessionInfo s = (SessionInfo) row;
            // 当前正在看的那个会话：**浅灰胶囊** + 行尾蓝色对勾（对齐参考图侧栏选中行）
            boolean current = s.id != null && !s.id.isEmpty() && s.id.equals(currentId);

            // 整组共用一张卡：只有组内第一行圆上角、最后一行圆下角（iOS 内嵌列表）
            boolean top = cardTop(position);
            boolean bottom = cardBottom(position);
            // 选中 = 浅灰圆角胶囊（SELECT_BG），**不是**蓝底白字 —— 蓝色只留给行尾那一枚对勾
            int fill = current ? Ui.SELECT_BG : Ui.GLASS;
            android.graphics.drawable.GradientDrawable bg = Ui.rowBg(ctx, fill, top, bottom);

            int depth = Math.max(0, s.childDepth);
            LinearLayout card = Ui.row(ctx);
            card.setMinimumHeight(Ui.dp(ctx, 52));
            card.setPadding(Ui.dp(ctx, Ui.M_CARD_PAD) + Ui.dp(ctx, 18) * depth,
                    Ui.dp(ctx, 10), Ui.dp(ctx, 12), Ui.dp(ctx, 10));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            card.setLayoutParams(rlp);
            Ui.tapRow(card, bg, Ui.PRESS);

            // 子会话左侧画一条竖向导引线：一眼看出它挂在上面那个父会话下面
            if (depth > 0) {
                View guide = new View(ctx);
                guide.setBackgroundColor(Ui.LINE);
                LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                        Math.max(1, Ui.dp(ctx, 1.5f)), Ui.dp(ctx, 34));
                glp.rightMargin = Ui.dp(ctx, 10);
                guide.setLayoutParams(glp);
                card.addView(guide);
            }

            LinearLayout texts = Ui.col(ctx);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            texts.setLayoutParams(tlp);

            String shownTitle = s.displayForList();
            if (s.childDepth > 0) shownTitle = "└ " + shownTitle;
            // 标题一律用正文色：选中已经由"浅灰胶囊 + 蓝色对勾"表达了，再染蓝就是三重强调
            TextView title = Ui.text(ctx, shownTitle, Ui.S_BODY, Ui.INK, current);
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
                TextView st = Ui.text(ctx, sub.toString(), Ui.S_FOOT, Ui.INK_FAINT, false);
                st.setSingleLine(true);
                st.setEllipsize(android.text.TextUtils.TruncateAt.END);
                st.setPadding(0, Ui.dp(ctx, 3), 0, 0);
                texts.addView(st);
            }
            card.addView(texts);

            // 折叠开关：父会话名下挂着子智能体/专家团会话时给一个可点的入口（默认折叠）
            if (s.childCount > 0) {
                TextView tog = Ui.text(ctx, (s.expanded ? "▾ " : "▸ ") + s.childCount + " 子会话",
                        Ui.S_CAP1, Ui.BRAND, true);
                tog.setPadding(Ui.dp(ctx, 9), Ui.dp(ctx, 4), Ui.dp(ctx, 9), Ui.dp(ctx, 4));
                tog.setBackground(Ui.pill(Ui.BRAND_SOFT));
                LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                glp.rightMargin = Ui.dp(ctx, 6);
                tog.setLayoutParams(glp);
                tog.setOnClickListener(v -> host.onToggleChildren(s));
                card.addView(tog);
            }

            // 选中行的蓝色对勾（参考图：选中行 = 浅灰胶囊 + 一枚蓝色小图标 + 深色文字）
            if (current) {
                TextView ck = Ui.iconBox(ctx, com.dsh.mobile.R.drawable.ic_check,
                        0x00000000, Ui.BRAND, 18f, 0f, 16f);
                LinearLayout.LayoutParams cklp = new LinearLayout.LayoutParams(
                        Ui.dp(ctx, 18), Ui.dp(ctx, 18));
                cklp.rightMargin = Ui.dp(ctx, 6);
                ck.setLayoutParams(cklp);
                card.addView(ck);
            }

            if (s.pending > 0) {
                TextView badge = Ui.text(ctx, s.pending == 1 ? "❓ 待回答" : "⚠ 待批准",
                        Ui.S_CAP1, s.pending == 1 ? Ui.ON_BRAND : Ui.ON_WARN, true);
                badge.setPadding(Ui.dp(ctx, 9), Ui.dp(ctx, 4), Ui.dp(ctx, 9), Ui.dp(ctx, 4));
                badge.setBackground(Ui.pill(s.pending == 1 ? Ui.BRAND_FILL : Ui.WARN));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                blp.rightMargin = Ui.dp(ctx, 6);
                badge.setLayoutParams(blp);
                card.addView(badge);
            } else if (s.running) {
                LinearLayout run = Ui.dotLabel(ctx, 8f, Ui.WARN, "运行中", Ui.S_CAP1, Ui.WARN);
                run.setPadding(Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4), 0);
                card.addView(run);
            } else {
                card.addView(Ui.chevron(ctx));
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

            LinearLayout outer = Ui.col(ctx);
            outer.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                    android.widget.AbsListView.LayoutParams.MATCH_PARENT,
                    android.widget.AbsListView.LayoutParams.WRAP_CONTENT));
            outer.addView(card);
            // 组内细分隔线：从文字左缘开始（iOS 的 inset separator），最后一行不画
            if (!bottom) outer.addView(Ui.insetDivider(ctx, 16));
            return outer;
        }

        /** 这一项是不是所在分组卡的**第一行**（上一项是工作区标题，或就是列表开头）。 */
        private boolean cardTop(int pos) {
            if (pos <= 0) return true;
            return data.get(pos - 1) instanceof WorkspaceGroup;
        }

        /** 这一项是不是所在分组卡的**最后一行**（下一项是工作区标题，或就到列表末尾）。 */
        private boolean cardBottom(int pos) {
            if (pos >= data.size() - 1) return true;
            return data.get(pos + 1) instanceof WorkspaceGroup;
        }
    }
}
