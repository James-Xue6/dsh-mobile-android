package com.dsh.mobile.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.dsh.mobile.model.ChatItem;
import com.dsh.mobile.model.StepProcess;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** 会话界面：顶部栏 + 状态横幅 + 消息列表 + 输入条。 */
public final class ConversationView extends LinearLayout implements ChatAdapter.Host {

    public interface Host extends ChatAdapter.Host {
        /** 左上角 ‹ 的行为：打开左侧任务列表抽屉（豆包式），不再是全屏返回列表页。 */
        void onOpenTasks();
        void onSend(String text);
        /**
         * 点输入条右侧的 ■：请求停止当前回合。
         *
         * 方法名<b>不能叫 onStop</b>：Activity 生命周期里已有 {@code onStop()}，
         * 宿主（MainActivity）既实现本接口、又覆写生命周期，签名撞车后接口实现
         * 就成了 Activity.onStop 的实现 —— 一旦漏了 super.onStop()，切后台/锁屏
         * 就抛 SuperNotCalledException 崩溃（真机 logcat 实测：PID 698）。
         * 所以这里统一用 onStopTurn（语义：停止当前回合）。
         */
        void onStopTurn();
        void onLoadMore();
        void onMenu();
        /** 点「👥 N 子智能体」：打开子智能体列表（底部弹窗）。 */
        void onOpenSubagents();
        void onVoiceInput();
        void onPickImage();
        void onDownloadFile(ChatItem item, String path);
        void onCopyPath(String path);
    }

    private final Context ctx;
    private final Host host;
    private final Handler ui = new Handler(Looper.getMainLooper());

    /** 顶部栏（主题切换时要重刷底色/文字色，所以留引用）。 */
    private final LinearLayout barRow;
    /** 输入框上方那一层（子智能体入口 + 只读说明）。 */
    private final LinearLayout preInputRow;
    private final TextView title;
    private final TextView subtitle;
    private final TextView banner;
    private final ListView list;
    private final ChatAdapter adapter;
    private final EditText input;
    private final TextView action;
    /** 输入框左侧的「＋」（选图）：子会话只读时要一起禁用。 */
    private final TextView pick;
    /** 顶部栏两个圆形按钮（主题切换要改图标颜色）。 */
    private final TextView backBtn;
    private final TextView menuBtn;
    /** 悬浮「回到底部」按钮：滚上去看历史时出现 */
    private TextView toBottom;
    /** 顶部「目标 / 任务」提要条 */
    private TextView planView;
    /**
     * 消息列表上方那一行「正在加载更早…」/「加载更早失败，点这里重试」。
     * 分页在途/失败必须有可见状态：静默失败会让用户以为"这个会话就这么长"，
     * 这也正是"看不到一个对话之前内容"被反复报上来的原因。
     */
    private TextView moreStatus;
    /**
     * 输入框上方那行「👥 N 子智能体」入口（N=0 时隐藏，避免噪音）。
     * 点开 = 底部弹窗列出当前主智能体的全部子会话（见 SubagentSheet）。
     */
    private TextView subEntry;
    /** 子会话只读时在输入条上方给出的原因（可见的说明，不是默默失效）。 */
    private TextView readOnlyNote;

    private boolean compact;
    private List<ChatItem> full = new ArrayList<>();
    /** 顶部提要的原始文本（简洁模式要按模式重新压行，不能只存压好的结果）。 */
    private String planText = "";
    private boolean atBottom = true;
    /**
     * 手指是否按在列表上。流式输出时内容每 60ms 刷新一次，
     * 若刷新时仍按 atBottom 自动滚到底，会和用户的上滑手势互相打架
     * （手指刚按下、还没产生滚动事件时 atBottom 仍是 true，于是被硬拽回底部）。
     */
    private boolean userTouching = false;
    private boolean refreshPending;
    private boolean running;
    private String runningHint = "";
    /** 当前是不是只读的子会话（子智能体 / 专家团子会话）。 */
    private boolean readOnly = false;
    /** 顶部横幅的原始语义（主题重画时要按同样语义还原，不能靠颜色反推）。 */
    private String bannerText = "";
    private boolean bannerError = false;
    private boolean bannerActionable = false;
    private Runnable bannerOnClick;

    public ConversationView(Context ctx, Host host) {
        super(ctx);
        this.ctx = ctx;
        this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(Ui.BG);

        // ---- 顶部栏
        LinearLayout bar = Ui.row(ctx);
        barRow = bar;
        bar.setBackgroundColor(Ui.SURFACE);
        bar.setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8));

        // 左上角箭头：保留箭头样式，但行为改成「打开左侧任务列表」（豆包式两级导航）
        TextView back = Ui.circleButton(ctx, "‹", android.graphics.Color.TRANSPARENT, Ui.INK);
        backBtn = back;
        back.setTextSize(26f);
        back.setOnClickListener(v -> host.onOpenTasks());
        bar.addView(back);

        LinearLayout titles = Ui.col(ctx);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titles.setLayoutParams(tlp);
        title = Ui.text(ctx, "对话", 16.5f, Ui.INK, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitle = Ui.text(ctx, "", 11.5f, Ui.INK_FAINT, false);
        subtitle.setSingleLine(true);
        titles.addView(title);
        titles.addView(subtitle);
        bar.addView(titles);

        TextView menu = Ui.circleButton(ctx, "⋮", android.graphics.Color.TRANSPARENT, Ui.INK_SUB);
        menuBtn = menu;
        menu.setTextSize(22f);
        menu.setOnClickListener(v -> host.onMenu());
        bar.addView(menu);
        addView(bar, Ui.fill());

        // ---- 状态横幅
        banner = Ui.text(ctx, "", 12f, Ui.BANNER_WARN_FG, false);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 7), Ui.dp(ctx, 12), Ui.dp(ctx, 7));
        banner.setBackgroundColor(Ui.BANNER_WARN_BG);
        banner.setVisibility(GONE);
        addView(banner, Ui.fill());

        // ---- 目标 / 任务提要（有目标或任务时才显示）
        planView = Ui.text(ctx, "", 12f, Ui.INK_SUB, false);
        planView.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 8), Ui.dp(ctx, 14), Ui.dp(ctx, 8));
        planView.setBackgroundColor(Ui.PLAN_BG);
        planView.setMaxLines(8);
        planView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        planView.setVisibility(GONE);
        addView(planView, Ui.fill());

        // ---- 分页状态行：正在加载更早 / 加载失败可重试（点一下 = 重新请求）
        moreStatus = Ui.text(ctx, "", 12f, Ui.INK_SUB, false);
        moreStatus.setGravity(Gravity.CENTER);
        moreStatus.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8));
        moreStatus.setBackgroundColor(Ui.PLAN_BG);
        moreStatus.setVisibility(GONE);
        moreStatus.setOnClickListener(v -> host.onLoadMore());
        addView(moreStatus, Ui.fill());

        // ---- 消息列表
        adapter = new ChatAdapter(ctx, this);
        list = new ListView(ctx);
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setCacheColorHint(0);
        list.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setSelector(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        list.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int scrollState) { }
            @Override public void onScroll(AbsListView view, int first, int visible, int total) {
                atBottom = computeAtBottom();
                if (toBottom != null) toBottom.setVisibility(atBottom ? GONE : VISIBLE);
                if (first == 0 && total > 0 && view.canScrollVertically(-1)) host.onLoadMore();
            }
        });

        // 手指按住期间不自动滚动；抬手时重新判断是否贴底（决定按钮显隐）
        list.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    userTouching = true;
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    userTouching = false;
                    list.post(() -> {
                        atBottom = computeAtBottom();
                        if (toBottom != null) toBottom.setVisibility(atBottom ? GONE : VISIBLE);
                    });
                    break;
                default:
                    break;
            }
            return false; // 不消费事件，滚动仍交给 ListView 自己处理
        });

        // 套一层 FrameLayout，用来悬浮「回到底部」按钮
        FrameLayout listWrap = new FrameLayout(ctx);
        listWrap.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        listWrap.addView(list);

        toBottom = new TextView(ctx);
        toBottom.setText("↓");
        toBottom.setTextSize(19f);
        toBottom.setTextColor(Ui.ON_BRAND);
        toBottom.setGravity(Gravity.CENTER);
        toBottom.setBackground(Ui.pill(Ui.BRAND_FILL));
        toBottom.setElevation(Ui.dp(ctx, 6));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 44));
        flp.gravity = Gravity.BOTTOM | Gravity.END;
        flp.rightMargin = Ui.dp(ctx, 14);
        flp.bottomMargin = Ui.dp(ctx, 12);
        toBottom.setLayoutParams(flp);
        toBottom.setVisibility(GONE);
        toBottom.setOnClickListener(v -> {
            // 超长会话上 smoothScroll 要滚很久，这里直接跳到底，并立刻收起按钮
            scrollToBottom();
            atBottom = true;
            toBottom.setVisibility(GONE);
        });
        listWrap.addView(toBottom);
        addView(listWrap);

        // ---- 输入框上方：子智能体入口 + 只读说明
        //
        // 与 PC 端同位置同语义：进主会话后，输入框上方一行「👥 N 子智能体」（N=0 不显示，
        // 避免噪音），点开是底部弹窗列出这条会话的全部子会话，点一项即切过去。
        LinearLayout preInput = Ui.col(ctx);
        preInputRow = preInput;
        preInput.setBackgroundColor(Ui.SURFACE);

        subEntry = Ui.text(ctx, "", 12.5f, Ui.BRAND, true);
        subEntry.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 8));
        subEntry.setBackground(Ui.pill(Ui.BRAND_SOFT));
        subEntry.setGravity(Gravity.CENTER);
        subEntry.setClickable(true);
        subEntry.setOnClickListener(v -> host.onOpenSubagents());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(ctx, 10);
        slp.rightMargin = Ui.dp(ctx, 10);
        slp.topMargin = Ui.dp(ctx, 6);
        subEntry.setLayoutParams(slp);
        subEntry.setVisibility(GONE);
        preInput.addView(subEntry);

        readOnlyNote = Ui.text(ctx, "", 11.5f, Ui.INK_SUB, false);
        readOnlyNote.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 5), Ui.dp(ctx, 14), Ui.dp(ctx, 3));
        readOnlyNote.setVisibility(GONE);
        preInput.addView(readOnlyNote);

        // 两个子 View 都是 GONE 时这层高度为 0：不需要额外开关（避免"父层被藏住、
        // 子 View 以为自己是显示状态"这种自查不出来的形态）。
        addView(preInput, Ui.fill());

        // ---- 输入条
        LinearLayout inputBar = Ui.row(ctx);
        inputBar.setBackgroundColor(Ui.SURFACE);
        inputBar.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 8), Ui.dp(ctx, 10), Ui.dp(ctx, 8));

        input = new EditText(ctx);
        input.setHint("给 Agent 派个任务…");
        input.setTextSize(15f);
        input.setHintTextColor(Ui.INK_FAINT);
        input.setTextColor(Ui.INK);
        input.setBackground(Ui.roundStroke(Ui.dp(ctx, 20), Ui.FIELD_ALT_BG, Ui.dp(ctx, 0.8f), Ui.LINE));
        input.setPadding(Ui.dp(ctx, 15), Ui.dp(ctx, 10), Ui.dp(ctx, 15), Ui.dp(ctx, 10));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        input.setMaxLines(5);
        input.setMinLines(1);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        input.setLayoutParams(ilp);

        pick = Ui.circleButton(ctx, "＋", Ui.CHIP_BG, Ui.INK_SUB);
        pick.setTextSize(19f);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(ctx, 38), Ui.dp(ctx, 38));
        plp.rightMargin = Ui.dp(ctx, 6);
        pick.setLayoutParams(plp);
        pick.setOnClickListener(v -> host.onPickImage());
        inputBar.addView(pick);
        inputBar.addView(input);

        action = Ui.circleButton(ctx, "↑", Ui.BRAND_FILL, Ui.ON_BRAND);
        action.setTextSize(20f);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dp(ctx, 42), Ui.dp(ctx, 42));
        alp.leftMargin = Ui.dp(ctx, 8);
        action.setLayoutParams(alp);
        action.setOnClickListener(v -> {
            if (running) { host.onStopTurn(); return; }
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            input.setText("");
            host.onSend(text);
        });
        input.setOnLongClickListener(v -> { host.onVoiceInput(); return true; });
        inputBar.addView(action);
        addView(inputBar, Ui.fill());
    }

    public String draftText() { return input.getText().toString(); }
    public void setDraft(String s) { input.setText(s == null ? "" : s); }
    public void focusInput() { input.requestFocus(); }

    /**
     * 输入框上方的子智能体入口：显示「👥 N 子智能体」，N=0 时隐藏。
     *
     * @param count   当前「主智能体」名下的子会话数量
     * @param inChild 人此刻是否就在某个子会话里（是的话文案补一句"点这里切换"，
     *                因为这时候列表还要承担"从子会话切到兄弟会话"的职责）
     */
    public void setSubagentEntry(int count, boolean inChild) {
        if (subEntry == null) return;
        if (count <= 0) {
            subEntry.setVisibility(GONE);
            return;
        }
        subEntry.setText(inChild
                ? "👥 " + count + " 子智能体 · 点这里切换"
                : "👥 " + count + " 子智能体");
        subEntry.setVisibility(VISIBLE);
    }

    /**
     * 子会话只读态。
     *
     * 宿主对 origin=subagent 的会话只接受 durable parent 只读寻址
     * （app.asar：SessionAddress.mode 'unknown' 是只读判别值；SessionController.prompt →
     * resolveAgent → session/agent-busy「owned by subagent routing」），所以子会话里
     * **发不出去**。这里显式禁用输入并给出可见原因 —— 绝不让人发了没反应。
     */
    public void setReadOnly(boolean value, String reason) {
        this.readOnly = value;
        input.setEnabled(!value);
        input.setHint(value ? "子会话只读（回主智能体才能发消息）" : "给 Agent 派个任务…");
        pick.setEnabled(!value);
        pick.setAlpha(value ? 0.45f : 1f);
        Ui.setButtonEnabled(action, !value);
        if (readOnlyNote != null) {
            readOnlyNote.setText(reason == null ? "" : reason);
            readOnlyNote.setVisibility(value && reason != null && !reason.isEmpty() ? VISIBLE : GONE);
        }
    }

    public void setTitleText(String t) {
        title.setText(t == null || t.isEmpty() ? "对话" : t);
    }

    public void setSubtitleText(String s) {
        subtitle.setText(s == null ? "" : s);
        subtitle.setVisibility(s == null || s.isEmpty() ? GONE : VISIBLE);
    }

    public void setBanner(String text, boolean error) {
        setBanner(text, error, false, null);
    }

    /**
     * 顶部状态条。
     *
     * actionable=true 时整条可点：用于「连接抖动…还没接上，点这里重新连接」这类
     * 「持续不恢复、必须给个出口」的提示（点一下 = 重新订阅 + 必要时重开连接）。
     * 不可点时显式清掉监听器，避免复用同一个 TextView 时残留上一次的点击行为。
     */
    public void setBanner(String text, boolean error, boolean actionable, Runnable onClick) {
        bannerText = text == null ? "" : text;
        bannerError = error;
        bannerActionable = actionable;
        bannerOnClick = onClick;
        if (bannerText.isEmpty()) {
            banner.setVisibility(GONE);
            banner.setClickable(false);
            banner.setOnClickListener(null);
            return;
        }
        banner.setVisibility(VISIBLE);
        banner.setText(bannerText);
        banner.setTextColor(error ? Ui.BANNER_ERR_FG : Ui.BANNER_WARN_FG);
        banner.setBackgroundColor(error ? Ui.BANNER_ERR_BG : Ui.BANNER_WARN_BG);
        banner.setClickable(actionable);
        banner.setOnClickListener(actionable && onClick != null ? v -> onClick.run() : null);
    }

    /**
     * 顶部提要：当前目标 + 任务清单（空则隐藏）。
     * 简洁模式只留「目标」一行 + 任务完成进度（任务 3/7）—— 一列待办本身
     * 也是用户报的「简洁了还显示一大堆」的一部分；完整模式照旧全铺。
     */
    public void setPlan(String text) {
        this.planText = text == null ? "" : text;
        renderPlan();
    }

    private void renderPlan() {
        if (planView == null) return;
        if (planText.trim().isEmpty()) {
            planView.setVisibility(GONE);
            return;
        }
        planView.setMaxLines(compact ? 1 : 8);
        planView.setText(compact ? compactPlan(planText) : planText);
        planView.setVisibility(VISIBLE);
    }

    /** 把「目标 + 待办清单」压成一行：目标（截断）+「任务 已完成/总数」。 */
    private static String compactPlan(String text) {
        String[] lines = text.split("\n", -1);
        String goal = "";
        int done = 0;
        int total = 0;
        for (String raw : lines) {
            String s = raw.trim();
            if (s.isEmpty()) continue;
            char c0 = s.charAt(0);
            if (c0 == '\u2611' || c0 == '\u25D0' || c0 == '\u2610') {
                total++;
                if (c0 == '\u2611') done++;
            } else if (goal.isEmpty()) {
                goal = s;
            }
        }
        if (goal.length() > 60) goal = goal.substring(0, 60) + "…";
        StringBuilder sb = new StringBuilder(goal);
        if (total > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("任务 ").append(done).append('/').append(total);
        }
        return sb.length() == 0 ? text : sb.toString();
    }

    public void setRunning(boolean value, String hint) {
        this.running = value;
        this.runningHint = hint == null ? "" : hint;
        adapter.setRunningHint(this.runningHint);
        // 简洁模式的摘要行文案取决于"还在不在跑"（正在运行命令… / 执行了命令），
        // 所以运行状态一变必须重算一次；完整模式只是多一个提示，重算也无害。
        applyFilter();
        action.setText(value ? "■" : "↑");
        action.setTextSize(value ? 16f : 20f);
        action.setBackground(Ui.pill(value ? Ui.STOP_BG : Ui.BRAND_FILL));
        action.setTextColor(value ? Ui.INK : Ui.ON_BRAND);
        // 只读子会话里停止按钮同样禁用（宿主也只会用 subagents.interruptByParent 停子会话）
        if (readOnly) Ui.setButtonEnabled(action, false);
    }

    public void setItems(List<ChatItem> items) {
        this.full = items == null ? new ArrayList<ChatItem>() : items;
        applyFilter();
    }

    /**
     * 分页状态行（消息列表正上方）：text 为空就整行收起，不占高度。
     * error=true 用警示配色——这一行是可点的「加载更早失败，点这里重试」。
     */
    public void setMoreStatus(String text, boolean error) {
        if (moreStatus == null) return;
        if (text == null || text.trim().isEmpty()) {
            moreStatus.setVisibility(GONE);
            return;
        }
        moreStatus.setText(text);
        moreStatus.setTextColor(error ? Ui.BANNER_WARN_FG : Ui.INK_SUB);
        moreStatus.setVisibility(VISIBLE);
    }

    /** 简洁模式：每个回合只留一条 PC 端工作台同款的过程摘要（见 applyFilter）。 */
    public void setCompact(boolean value) {
        this.compact = value;
        adapter.setCompact(value);
        renderPlan();
        applyFilter();
    }

    /**
     * 列表最终显示什么，取决于模式：
     *
     *  完整模式（compact=false）：逐条显示 —— 工具参数/输出、思考块都在。
     *  简洁模式（compact=true）：目标是「和 PC 端工作台一致的摘要」——
     *     · 用户气泡、助手正文、审批/提问/交付物/专家团回传卡：保留（这是内容与决策）；
     *     · 一串工具调用（TOOL）与命令回显（SYSTEM）：压成**一条**摘要行，
     *       文案与桌面端 `message.stepProcess.*` 对齐（StepProcess）；
     *     · 思考块（助手气泡上的「▸ 思考过程」）：不显示（ChatAdapter 按 compact 判断）；
     *     · 注入项（runtime-context / skill-catalog / tool-jobs…）：早就在
     *       MainActivity.isInjectedContext 里丢掉了，两种模式都不会出现。
     * 运行中必须有可见提示：这一段里还有工具在跑时摘要行会写「正在运行命令 · pwsh…」，
     * 连工具都还没发出来（正在分析）时补一条「深度求索中…」，免得用户以为卡住。
     */
    private void applyFilter() {
        List<ChatItem> view = new ArrayList<>();
        if (!compact) {
            for (ChatItem it : full) {
                // 没有正文的助手气泡一律不显示（只有思考、或工具回合的空壳）
                if (it.kind == ChatItem.ASSISTANT && it.text.trim().isEmpty() && !it.streaming) continue;
                view.add(it);
            }
            adapter.setItems(view);
            return;
        }

        List<ChatItem> run = new ArrayList<>();
        for (ChatItem it : full) {
            switch (it.kind) {
                case ChatItem.TOOL:
                case ChatItem.SYSTEM:
                    // 过程项：先攒着，遇到"内容行"再把它们压成一条摘要
                    run.add(it);
                    break;
                case ChatItem.ASSISTANT:
                    if (it.text.trim().isEmpty() && !it.streaming) break;
                    flushStep(run, view);
                    view.add(it);
                    break;
                case ChatItem.USER:
                case ChatItem.APPROVAL:
                case ChatItem.QUESTION:
                case ChatItem.FILES:
                case ChatItem.AGENT:
                    flushStep(run, view);
                    view.add(it);
                    break;
                default:
                    break;
            }
        }
        flushStep(run, view);

        // 回合在跑但尾行不是"正在运行…"（例如刚发出、工具还没调用）→ 补一条可见提示
        if (running && (view.isEmpty() || view.get(view.size() - 1).kind != ChatItem.STEP
                || !view.get(view.size() - 1).stepRunning)) {
            ChatItem hint = ChatItem.of(ChatItem.STEP, "compact:running", "");
            hint.text = runningHint == null || runningHint.trim().isEmpty() ? "深度求索中…" : runningHint;
            hint.stepRunning = true;
            view.add(hint);
        }
        adapter.setItems(view);
    }

    /**
     * 把攒下的一段「过程项」压成一条摘要行并追加到 view；空段不产生行。
     * 只有命令回显（SYSTEM）、没有任何工具调用时整段丢弃 —— 简洁模式要给的是
     * 「做了什么」的一句话，不是每条斜杠命令的回显。
     */
    private void flushStep(List<ChatItem> run, List<ChatItem> view) {
        if (run.isEmpty()) return;
        List<String> kinds = new ArrayList<>();
        List<String> names = new ArrayList<>();
        ChatItem lastRunningTool = null;
        boolean anyTool = false;
        boolean anyError = false;
        for (ChatItem it : run) {
            if (it.kind != ChatItem.TOOL) continue;
            anyTool = true;
            kinds.add(StepProcess.activity(it.toolName));
            names.add(it.toolName == null || it.toolName.isEmpty() ? "工具" : it.toolName);
            if (it.toolError) anyError = true;
            // toolRunning 可能因为历史窗口里没有 turn/end 而残留在 true：
            // 只有整个回合确实还在跑时才算"正在运行"，否则按已完成处理。
            if (running && it.toolRunning) lastRunningTool = it;
        }
        run.clear();
        if (!anyTool) return;

        ChatItem row = ChatItem.of(ChatItem.STEP, "compact:" + view.size() + ":" + kinds.size(), "");
        StringBuilder sb = new StringBuilder();
        if (lastRunningTool != null) {
            sb.append(StepProcess.runningLabel(StepProcess.activity(lastRunningTool.toolName)))
                    .append(" · ").append(lastRunningTool.toolName.isEmpty() ? "工具" : lastRunningTool.toolName)
                    .append('…');
            row.stepRunning = true;
        } else {
            sb.append(StepProcess.title(kinds));
            // 只有一次调用时把工具名带上，让「一句话」本身就能读懂做了什么
            if (names.size() == 1) sb.append(" · ").append(names.get(0));
        }
        if (anyError) sb.append("（有失败）");
        row.text = sb.toString();
        row.stepError = anyError;
        // 错误必须看得见：简洁模式不能把失败整段吞掉
        if (anyError) row.text = "⚠ " + row.text;
        view.add(row);
    }

    /** 合并高频刷新，避免流式输出时每 token 重绘。 */
    public void refresh() {
        if (refreshPending) return;
        refreshPending = true;
        ui.postDelayed(() -> {
            refreshPending = false;
            applyFilter();
            adapter.notifyDataSetChanged();
            if (atBottom && !userTouching) scrollToBottom();
        }, 60);
    }

    /** 像素级判断是否真的贴底：比 first+visible 更准，免得残留一行也算贴底。 */
    private boolean computeAtBottom() {
        int count = list.getCount();
        if (count == 0) return true;
        if (list.getLastVisiblePosition() < count - 1) return false;
        android.view.View last = list.getChildAt(list.getChildCount() - 1);
        if (last == null) return true;
        return last.getBottom() <= list.getHeight() + Ui.dp(ctx, 4);
    }

    public void refreshNow() {
        adapter.notifyDataSetChanged();
    }

    /**
     * 滚到底。必须 post —— setItems 之后立刻 setSelection 时 ListView 还没重新布局，
     * 结果是停在顶部（真机上就是这个现象）。
     */
    public void scrollToBottom() {
        final int n = adapter.getCount();
        if (n <= 0) return;
        list.post(() -> {
            try { list.setSelection(n - 1); } catch (Throwable ignored) { }
        });
    }

    public void scrollToBottomSmooth() {
        final int n = adapter.getCount();
        if (n <= 0) return;
        list.post(() -> {
            try { list.smoothScrollToPosition(n - 1); } catch (Throwable ignored) { }
        });
    }

    /**
     * 系统窗口内边距变化（典型场景：输入法弹起/收起，MainActivity 的
     * OnApplyWindowInsetsListener 会把键盘高度加到根容器底部）。
     *
     * 可视区一变矮，ListView 不会自己保持"贴底"，最后几条消息会被顶出屏幕；
     * 这里在"原本就贴底"时补一次滚动。用户手动翻看历史（atBottom=false）时不打扰。
     */
    public void onWindowInsetsChanged() {
        if (!atBottom) return;
        scrollToBottom();
    }

    /**
     * 方向 / 窗口尺寸变化（由 MainActivity.onConfigurationChanged 调用）。
     *
     * 旋转时 Activity 不重建，而气泡最大宽度是按屏宽算的（ChatAdapter.maxBubble），
     * 所以这里必须按**当前**宽度重算一次并整表重画，否则横屏后气泡仍是竖屏的窄宽度。
     */
    public void onConfigChanged() {
        adapter.refreshMetrics();
        adapter.notifyDataSetChanged();
        requestLayout();
        if (atBottom) scrollToBottom();
    }

    /**
     * 主题切换后按**新**色板重刷本页所有颜色，并强制消息列表整表重画。
     *
     * 为什么不做「整页重建」：对话页握着会话状态（items、草稿、滚动位置、翻页窗口），
     * 重建要全部重放，风险远大于收益。这里逐项刷色即可 —— 消息行由
     * ChatAdapter.getView **每次都新建**（从不复用 convertView），
     * 所以 notifyDataSetChanged() 就足以让所有可见气泡换到新配色。
     */
    public void applyTheme() {
        setBackgroundColor(Ui.BG);
        if (barRow != null) barRow.setBackgroundColor(Ui.SURFACE);
        if (backBtn != null) backBtn.setTextColor(Ui.INK);
        if (menuBtn != null) menuBtn.setTextColor(Ui.INK_SUB);
        if (title != null) title.setTextColor(Ui.INK);
        if (subtitle != null) subtitle.setTextColor(Ui.INK_FAINT);
        if (planView != null) {
            planView.setTextColor(Ui.INK_SUB);
            planView.setBackgroundColor(Ui.PLAN_BG);
        }
        if (preInputRow != null) preInputRow.setBackgroundColor(Ui.SURFACE);
        if (subEntry != null) {
            subEntry.setTextColor(Ui.BRAND);
            subEntry.setBackground(Ui.pill(Ui.BRAND_SOFT));
        }
        if (readOnlyNote != null) readOnlyNote.setTextColor(Ui.INK_SUB);
        if (input != null) {
            input.setTextColor(Ui.INK);
            input.setHintTextColor(Ui.INK_FAINT);
            input.setBackground(Ui.roundStroke(Ui.dp(ctx, 20), Ui.FIELD_ALT_BG, Ui.dp(ctx, 0.8f), Ui.LINE));
        }
        if (pick != null) {
            pick.setTextColor(Ui.INK_SUB);
            pick.setBackground(Ui.pill(Ui.CHIP_BG));
        }
        if (toBottom != null) {
            toBottom.setTextColor(Ui.ON_BRAND);
            toBottom.setBackground(Ui.pill(Ui.BRAND_FILL));
        }
        setRunning(running, runningHint);   // 重画 ↑ / ■ 的底色与字色（同时保住运行态）
        setBanner(bannerText, bannerError, bannerActionable, bannerOnClick);   // 横幅按原语义重画
        adapter.notifyDataSetChanged();
        requestLayout();
    }

    // ---- ChatAdapter.Host 转发
    @Override public void onDownloadFile(ChatItem item, String path) { host.onDownloadFile(item, path); }
    @Override public void onCopyPath(String path) { host.onCopyPath(path); }
    @Override public void onApprove(ChatItem item, String outcome) { host.onApprove(item, outcome); }
    @Override public void onQuestionSubmit(ChatItem item, JSONArray answers) { host.onQuestionSubmit(item, answers); }
    @Override public void onQuestionCancel(ChatItem item) { host.onQuestionCancel(item); }
}
