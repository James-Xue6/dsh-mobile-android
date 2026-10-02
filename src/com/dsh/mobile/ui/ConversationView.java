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
    /** 顶栏的玻璃底（主题切换要换色，所以留引用）。 */
    private android.graphics.drawable.GradientDrawable barBg;
    /** 顶栏下沿的发丝线。 */
    private View barLine;
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

    // ---- 数据改动 + 通知的唯一收口（见 commitData）
    /** 已经排了一次"合并提交"（60ms 窗口），防重入。 */
    private boolean commitScheduled = false;
    /** 惯性滑动期间被挂起的提交：滑停后再落地（见 onScrollStateChanged）。 */
    private boolean commitDeferred = false;
    /** 列表是否正在惯性滑动（fling）。 */
    private boolean listFlinging = false;
    /** refreshNow() 要求"这一次必须立刻提交"，绕过 fling 挂起。 */
    private boolean forceCommit = false;
    /** 合并提交的落点：commitData 是 private，用方法引用做稳定的 Runnable 便于取消。 */
    private final Runnable commitTask = this::commitData;
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

        // ---- 顶部栏（iOS 导航栏：44dp、细箭头返回、标题 17sp 粗体、底部一条发丝线）
        // 2026-10-03 液态玻璃：底色从纯 SURFACE 换成半透明 GLASS_BAR（浅色白 80% /
        // 深色黑 60%），下沿的发丝线由 barLine 画（HAIRLINE，不再是明显的灰 SEP）。
        LinearLayout bar = Ui.row(ctx);
        barRow = bar;
        barBg = Ui.glassBar();
        bar.setBackground(barBg);
        bar.setMinimumHeight(Ui.dp(ctx, 44));
        bar.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 5), Ui.dp(ctx, 8), Ui.dp(ctx, 5));

        // 左上角箭头：保留箭头样式，但行为改成「打开左侧任务列表」（豆包式两级导航）
        // 2026-10-02：字符 "‹" 换成手写矢量（1.75dp 线宽、圆角端点），字符箭头的粗细/角度
        // 跟着字体跑，放大就是"字符拼的"，是"不高级"最直接的来源之一。
        TextView back = Ui.circleIconButton(ctx, com.dsh.mobile.R.drawable.ic_chevron_left,
                android.graphics.Color.TRANSPARENT, Ui.BRAND, 20f, 36f);
        backBtn = back;
        back.setContentDescription("任务列表");
        back.setOnClickListener(v -> host.onOpenTasks());
        bar.addView(back);

        LinearLayout titles = Ui.col(ctx);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titles.setLayoutParams(tlp);
        title = Ui.text(ctx, "对话", Ui.S_HEAD, Ui.INK, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitle = Ui.text(ctx, "", Ui.S_CAP1, Ui.INK_FAINT, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(title);
        titles.addView(subtitle);
        bar.addView(titles);

        TextView menu = Ui.circleIconButton(ctx, com.dsh.mobile.R.drawable.ic_more,
                android.graphics.Color.TRANSPARENT, Ui.INK_SUB, 20f, 36f);
        menuBtn = menu;
        menu.setContentDescription("更多");
        menu.setOnClickListener(v -> host.onMenu());
        bar.addView(menu);
        addView(bar, Ui.fill());
        View barLine = new View(ctx);
        barLine.setBackgroundColor(Ui.HAIRLINE);
        this.barLine = barLine;
        barLine.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(ctx, 0.5f))));
        // 同 SettingsView：Ui.fill() 高度是 WRAP_CONTENT，普通 View 在 AT_MOST 下会吃满
        // 剩余高度 —— 会话页会因此只剩标题栏（正文/输入条全被挤掉）。
        addView(barLine);

        // ---- 状态横幅（可点时整条可点）
        banner = Ui.text(ctx, "", Ui.S_FOOT, Ui.BANNER_WARN_FG, false);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 9), Ui.dp(ctx, 16), Ui.dp(ctx, 9));
        banner.setBackgroundColor(Ui.BANNER_WARN_BG);
        banner.setVisibility(GONE);
        addView(banner, Ui.fill());

        // ---- 目标 / 任务提要（有目标或任务时才显示）
        // 2026-10-02：从"整条底色条"改成一张真正的卡片 —— 左侧 3dp 主色条 + 发丝描边。
        // 旧版浅色档用 PLAN_BG(#F2F2F7) 铺在 BG(#F2F2F7) 上，等于没有背景，提要像是浮在页面上。
        planView = Ui.text(ctx, "", Ui.S_FOOT, Ui.INK_SUB, false);
        planView.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 11), Ui.dp(ctx, 14), Ui.dp(ctx, 11));
        planView.setBackground(new Ui.CardBg(Ui.dp(ctx, Ui.R_CARD), Ui.GLASS, Ui.dp(ctx, 1f),
                Ui.LINE, Ui.BRAND, Ui.dp(ctx, 3f)));
        planView.setMaxLines(8);
        planView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        planView.setVisibility(GONE);
        LinearLayout.LayoutParams planLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        planLp.leftMargin = Ui.dp(ctx, Ui.M_SIDE);
        planLp.rightMargin = Ui.dp(ctx, Ui.M_SIDE);
        planLp.topMargin = Ui.dp(ctx, 6);
        planView.setLayoutParams(planLp);
        addView(planView);

        // ---- 分页状态行：正在加载更早 / 加载失败可重试（点一下 = 重新请求）
        moreStatus = Ui.text(ctx, "", Ui.S_FOOT, Ui.INK_SUB, false);
        moreStatus.setGravity(Gravity.CENTER);
        moreStatus.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 9), Ui.dp(ctx, 16), Ui.dp(ctx, 9));
        moreStatus.setBackgroundColor(Ui.PLAN_BG);
        moreStatus.setVisibility(GONE);
        moreStatus.setOnClickListener(v -> host.onLoadMore());
        addView(moreStatus, Ui.fill());

        // ---- 消息列表（iOS 列表左右留白 16dp）
        adapter = new ChatAdapter(ctx, this);
        list = new ListView(ctx);
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setCacheColorHint(0);
        list.setPadding(Ui.dp(ctx, Ui.M_SIDE), Ui.dp(ctx, 8), Ui.dp(ctx, Ui.M_SIDE), Ui.dp(ctx, 8));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setSelector(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        list.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int scrollState) {
                // 惯性滑动（fling）期间**不改数据也不通知**：FlingRunnable 每一帧都在
                // layoutChildren，任何"改了还没通知"的中间态都会在那里炸成
                // 「The content of the adapter has changed but ListView did not receive a
                // notification」（真机 dropbox 实证栈就是这个）。
                // 滑停后再把挂起的改动一次性落地（改 + notify 同一次消息，见 commitData）。
                listFlinging = scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING;
                if (!listFlinging && commitDeferred) ui.post(commitTask);
            }
            @Override public void onScroll(AbsListView view, int first, int visible, int total) {
                atBottom = computeAtBottom();
                if (toBottom != null) toBottom.setVisibility(atBottom ? GONE : VISIBLE);
                // 触顶就请求更早的一页。`canScrollVertically(-1)` 只在"第一条被顶出去一截"
                // 时为真 —— 恰好停在最顶端、再往下拽时它是 false，那时就什么都不会加载
                // （用户报的"往上看不到更早的内容"有一部分就是这个）。
                // 所以补一条：**手指还按在列表上**时到顶也算触顶（userTouching）。
                // 不加 userTouching 的裸 first==0 会被布局/补页引起的 onScroll 反复触发，
                // 变成"停在顶部就把整段历史一路拉完"。
                if (first == 0 && total > 0
                        && (view.canScrollVertically(-1) || userTouching)) host.onLoadMore();
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

        toBottom = Ui.circleIconButton(ctx, com.dsh.mobile.R.drawable.ic_arrow_down,
                Ui.BRAND_FILL, Ui.ON_BRAND, 20f, 44f);
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
        preInput.setBackground(Ui.glassBar());

        subEntry = Ui.text(ctx, "", Ui.S_FOOT, Ui.BRAND, true);
        subEntry.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 9), Ui.dp(ctx, 16), Ui.dp(ctx, 9));
        subEntry.setBackground(Ui.pill(Ui.BRAND_SOFT));
        subEntry.setGravity(Gravity.CENTER);
        // 双人图标改用手写矢量（旧版是 👥 emoji —— 彩色 emoji 是"看着不高级"的直接来源）
        Ui.setLeadingIcon(subEntry, com.dsh.mobile.R.drawable.ic_people, Ui.BRAND, 15f, 6f);
        subEntry.setClickable(true);
        subEntry.setOnClickListener(v -> host.onOpenSubagents());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(ctx, Ui.M_SIDE);
        slp.rightMargin = Ui.dp(ctx, Ui.M_SIDE);
        slp.topMargin = Ui.dp(ctx, 6);
        subEntry.setLayoutParams(slp);
        subEntry.setVisibility(GONE);
        preInput.addView(subEntry);

        readOnlyNote = Ui.text(ctx, "", Ui.S_CAP1, Ui.INK_SUB, false);
        readOnlyNote.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 6), Ui.dp(ctx, 16), Ui.dp(ctx, 4));
        readOnlyNote.setVisibility(GONE);
        preInput.addView(readOnlyNote);

        // 两个子 View 都是 GONE 时这层高度为 0：不需要额外开关（避免"父层被藏住、
        // 子 View 以为自己是显示状态"这种自查不出来的形态）。
        addView(preInput, Ui.fill());

        // ---- 输入条（iOS iMessage 制式：灰底胶囊输入框 + 圆形发送键）
        //
        // 2026-10-03 液态玻璃：整条从"贴着屏幕底的白色通栏"改成**悬浮玻璃胶囊**——
        //   · 外层 12dp 左右边距 + 12dp 下边距，与内容之间留出呼吸（规范要 12~16dp）；
        //   · 底 = GLASS_BAR 半透明玻璃 + 棱光/顶部高光（CardBg 的圆角 999 = 高/2，真胶囊）；
        //   · 输入框自己的灰底撤掉（透明）—— 胶囊本身就是输入框的底，两层灰底会"脏"。
        LinearLayout inputBar = Ui.row(ctx);
        inputBar.setBackground(new Ui.CardBg(999f, Ui.GLASS_BAR, Ui.dp(ctx, 1f),
                Ui.LINE, 0x00000000, 0f));
        // **不给玻璃挂 elevation**（2026-10-03 模拟器实测）：半透明填充会把系统阴影从底下
        // 透出来，在控件内部画出一圈灰环 + 一块白心。深色感改由棱光上边 + 下棱微暗承担。
        inputBar.setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 6));

        input = new EditText(ctx);
        input.setHint("给 Agent 派个任务…");
        input.setTextSize(Ui.S_BODY);
        input.setHintTextColor(Ui.INK_FAINT);
        input.setTextColor(Ui.INK);
        input.setBackground(Ui.round(Ui.dp(ctx, 19), 0x00000000));   // 透明：胶囊是唯一的底
        input.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 11), Ui.dp(ctx, 8), Ui.dp(ctx, 11));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        input.setMaxLines(5);
        input.setMinLines(1);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        input.setLayoutParams(ilp);

        pick = Ui.circleIconButton(ctx, com.dsh.mobile.R.drawable.ic_plus,
                Ui.CHIP_BG, Ui.INK_SUB, 18f, 34f);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(ctx, 34), Ui.dp(ctx, 34));
        plp.rightMargin = Ui.dp(ctx, 6);
        pick.setLayoutParams(plp);
        pick.setOnClickListener(v -> host.onPickImage());
        inputBar.addView(pick);
        inputBar.addView(input);

        action = Ui.circleIconButton(ctx, com.dsh.mobile.R.drawable.ic_arrow_up,
                Ui.BRAND_FILL, Ui.ON_BRAND, 20f, 36f);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dp(ctx, 36), Ui.dp(ctx, 36));
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
        // 悬浮：左右 16dp（与全 App 的 M_SIDE 对齐）、下方 12dp
        LinearLayout.LayoutParams ibLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ibLp.leftMargin = Ui.dp(ctx, Ui.M_SIDE);
        ibLp.rightMargin = Ui.dp(ctx, Ui.M_SIDE);
        ibLp.topMargin = Ui.dp(ctx, 6);
        ibLp.bottomMargin = Ui.dp(ctx, 12);
        addView(inputBar, ibLp);
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
                ? count + " 子智能体 · 点这里切换"
                : count + " 子智能体");
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

    public void setRunning(final boolean value, final String hint) {
        // 运行态会改变**可见行数**（简洁模式：回合在跑时尾行要补一条「正在运行…」摘要行），
        // 所以这里只更新状态 + 排一次提交，真正的"换数据 + notify"由 commitData 收口。
        // 旧实现在这里直接 applyFilter() 换数据却**不通知** —— 真机 fling 崩溃的两条路径之一。
        postOnUi(() -> {
            running = value;
            runningHint = hint == null ? "" : hint;
            adapter.setRunningHint(runningHint);
            Ui.setIconBg(action,
                    value ? com.dsh.mobile.R.drawable.ic_stop : com.dsh.mobile.R.drawable.ic_arrow_up,
                    value ? Ui.INK : Ui.ON_BRAND,
                    value ? Ui.STOP_BG : Ui.BRAND_FILL,
                    42f, value ? 17f : 20f);
            // 只读子会话里停止按钮同样禁用（宿主也只会用 subagents.interruptByParent 停子会话）
            if (readOnly) Ui.setButtonEnabled(action, false);
            scheduleRefresh();
        });
    }

    public void setItems(final List<ChatItem> items) {
        final List<ChatItem> next = items == null ? new ArrayList<ChatItem>() : items;
        postOnUi(() -> {
            full = next;
            scheduleRefresh();
        });
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
        // 模式切换会改变可见行数（工具/系统行被压成一条摘要行）→ 走收口重算 + 通知
        scheduleRefresh();
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

    // ============================================================ 数据改动 + 通知的唯一收口
    //
    // 真机 dropbox 硬崩溃（2026-10-02 20:58:20，v8，Foreground=Yes）：
    //   java.lang.IllegalStateException: The content of the adapter has changed but
    //   ListView did not receive a notification.
    //     at android.widget.ListView.layoutChildren(ListView.java:1873)
    //     at android.widget.AbsListView$FlingRunnable.run(AbsListView.java:5930)
    //
    // 机制：ListView 只在 notifyDataSetChanged() 时才把缓存的 mItemCount 与适配器对齐。
    // 旧实现存在两个"数据已改、通知未到"的中间态：
    //   ① setItems() 立刻 applyFilter() 换掉适配器数据，而通知要等 refresh() 的 60ms
    //      合并窗口 —— 流式输出每个 chunk 都走这条路，窗口里随便一帧布局（fling 每帧都布局）
    //      就会抛上面那个异常；
    //   ② setRunning() 内部也 applyFilter()（简洁模式会增删"正在运行…"摘要行、改变行数），
    //      这条路径**完全没有通知**。
    // 现在：数据改动本身推迟到主线程的一次消息里，并在**同一次消息内**立刻通知。
    // ListView 不可能再观察到"改了没通知"的中间态。

    /** 所有数据改动与通知都在主线程收口；非主线程调用自动切回主线程（顺序不变）。 */
    private void postOnUi(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else ui.post(r);
    }

    /**
     * 唯一的数据改动 + 通知落点：applyFilter() 换数据，紧接着在同一次主线程消息里
     * adapter.notifyChanged()。二者不可分 —— 这就是"绝不让 ListView 看到中间态"的保证。
     */
    private void commitData() {
        if (Looper.myLooper() != Looper.getMainLooper()) { ui.post(commitTask); return; }
        commitScheduled = false;
        boolean force = forceCommit;
        forceCommit = false;
        // 惯性滑动中不落地数据改动：等滑停（onScrollStateChanged 会再排一次）。
        // 结构性刷新（refreshNow：快照重建/审批卡/回滚）必须立刻可见，不受此限。
        if (listFlinging && !force) { commitDeferred = true; return; }
        commitDeferred = false;
        applyFilter();
        adapter.notifyChanged();
        if (atBottom && !userTouching) scrollToBottom();
    }

    /** 合并高频刷新（流式输出每 chunk 一次）：数据改动也一并推迟到这一帧。 */
    private void scheduleRefresh() {
        if (commitScheduled) return;
        commitScheduled = true;
        ui.postDelayed(commitTask, 60);
    }

    /**
     * 合并高频刷新，避免流式输出时每 token 重绘。
     * 注意：它只"排一次提交"，真正换数据发生在 {@link #commitData()} 里 —— 于是
     * "数据改了但还没通知"的窗口从根上不存在（旧实现正是在这个窗口里崩的）。
     */
    public void refresh() { postOnUi(this::scheduleRefresh); }

    /** 像素级判断是否真的贴底：比 first+visible 更准，免得残留一行也算贴底。 */
    private boolean computeAtBottom() {
        int count = list.getCount();
        if (count == 0) return true;
        if (list.getLastVisiblePosition() < count - 1) return false;
        android.view.View last = list.getChildAt(list.getChildCount() - 1);
        if (last == null) return true;
        return last.getBottom() <= list.getHeight() + Ui.dp(ctx, 4);
    }

    /**
     * 立刻提交（不等 60ms 合并窗口）：快照整体重建、审批/提问卡、回滚这类**结构性**变化
     * 必须当场可见。同样走 {@link #commitData()} —— 换数据与 notify 依然不可分。
     */
    public void refreshNow() {
        postOnUi(() -> {
            ui.removeCallbacks(commitTask);
            commitScheduled = false;
            forceCommit = true;
            commitData();
        });
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
     * 深链：把消息列表滚到 key 对应的那一张卡（点「有操作等你批准 / 有提问等你回答」通知
     * 进来后的落点——必须让人一进来就看到那张待选的卡）。
     *
     * @return true = 这张卡此刻就在显示的列表里，已排定滚动；
     *         false = 还没有这张卡（会话刚切过去、网关重放的卡还没到），调用方稍后重试。
     *
     * 只认 key（审批卡是 approval:xx、提问卡是 question:xx），与 MainActivity 建档用的是
     * 同一把键，所以不依赖"用户点的是第几条"这种会随重放变化的序号。
     */
    public boolean scrollToKey(String key) {
        if (key == null || key.isEmpty()) return false;
        java.util.List<ChatItem> shown = adapter.items();
        if (shown == null) return false;
        for (int i = 0; i < shown.size(); i++) {
            ChatItem it = shown.get(i);
            if (it == null || it.key == null || !key.equals(it.key)) continue;
            final int pos = i;
            // 这是"去看某一条"，不是"跟到最后"：显式退出贴底态，
            // 否则流式刷新（refresh 里的 atBottom 分支）会立刻把列表拽回底部。
            atBottom = false;
            list.post(() -> {
                try { list.setSelection(pos); } catch (Throwable ignored) { }
            });
            return true;
        }
        return false;
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
        refreshNow();
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
        if (barRow != null) barBg = Ui.topBarGlass(barRow, barLine);
        if (backBtn != null) Ui.setIcon(backBtn, com.dsh.mobile.R.drawable.ic_chevron_left, Ui.BRAND);
        if (menuBtn != null) Ui.setIcon(menuBtn, com.dsh.mobile.R.drawable.ic_more, Ui.INK_SUB);
        if (title != null) title.setTextColor(Ui.INK);
        if (subtitle != null) subtitle.setTextColor(Ui.INK_FAINT);
        if (planView != null) {
            planView.setTextColor(Ui.INK_SUB);
            // 必须与构造函数里那一份**逐参一致**（圆角 R_CARD + GLASS）：
            // 旧版这里写的是 12dp + SURFACE，切一次主题提要卡就悄悄变小、还从玻璃变成实心。
            planView.setBackground(new Ui.CardBg(Ui.dp(ctx, Ui.R_CARD), Ui.GLASS, Ui.dp(ctx, 1f),
                    Ui.LINE, Ui.BRAND, Ui.dp(ctx, 3f)));
        }
        if (preInputRow != null) preInputRow.setBackground(Ui.glassBar());
        if (subEntry != null) {
            subEntry.setTextColor(Ui.BRAND);
            subEntry.setBackground(Ui.pill(Ui.BRAND_SOFT));
            Ui.setLeadingIcon(subEntry, com.dsh.mobile.R.drawable.ic_people, Ui.BRAND, 15f, 6f);
        }
        if (readOnlyNote != null) readOnlyNote.setTextColor(Ui.INK_SUB);
        if (input != null) {
            input.setTextColor(Ui.INK);
            input.setHintTextColor(Ui.INK_FAINT);
            input.setBackground(Ui.round(Ui.dp(ctx, 19), 0x00000000));   // 透明：胶囊是唯一的底
        }
        if (pick != null) Ui.setIcon(pick, com.dsh.mobile.R.drawable.ic_plus, Ui.INK_SUB);
        if (toBottom != null) Ui.setIconBg(toBottom, com.dsh.mobile.R.drawable.ic_arrow_down,
                Ui.ON_BRAND, Ui.BRAND_FILL, 44f, 20f);
        setRunning(running, runningHint);   // 重画 ↑ / ■ 的底色与字色（同时保住运行态）
        setBanner(bannerText, bannerError, bannerActionable, bannerOnClick);   // 横幅按原语义重画
        refreshNow();                       // 收口重算 + 通知（不再裸调 notifyDataSetChanged）
        requestLayout();
    }

    // ---- ChatAdapter.Host 转发
    @Override public void onDownloadFile(ChatItem item, String path) { host.onDownloadFile(item, path); }
    @Override public void onCopyPath(String path) { host.onCopyPath(path); }
    @Override public void onApprove(ChatItem item, String outcome) { host.onApprove(item, outcome); }
    @Override public void onQuestionSubmit(ChatItem item, JSONArray answers) { host.onQuestionSubmit(item, answers); }
    @Override public void onQuestionCancel(ChatItem item) { host.onQuestionCancel(item); }
}
