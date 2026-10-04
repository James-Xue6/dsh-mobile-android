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
import android.widget.HorizontalScrollView;
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
        /**
         * 点底部 chip 行的「模型」：向网关拉该会话的模型目录（PROTOCOL §8 models）。
         * 目录回来后由宿主弹选择面板，选中再走 select-model。
         */
        void onPickModel();
        /** 点「思考」chip：在当前模型的思考档位里挑一个（select-model 带 reasoningEffort）。 */
        void onPickEffort();
        /** 点「用量」chip：看 token/上下文占用详情（context-usage）。 */
        void onUsageTap();
        /** 点「项目」chip：列出可创建会话的工作区，选中即在新工作区开对话（豆包同逻辑）。 */
        void onProjectTap();
        /** 点「任务」chip：展开该会话的 todo 列表。 */
        void onTasksTap();
        /** 点顶部提示栏：切到那条有待回答提问/审批的会话。 */
        void onOpenPendingSession();
        /** 点顶部「目标 / 任务」卡片：看全文（卡片本身只显示截断版）。 */
        void onOpenPlan();
        /** 点「待发送」条上的一条：弹出 立即插入 / 编辑 / 删除（itemId 来自网关队列）。 */
        void onQueueItemAction(String itemId, String text);
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
    /** 输入条胶囊的 CardBg（主题切换要重建——旧版不刷导致深色下输入条还是浅白）。 */
    private android.graphics.drawable.Drawable inputBarBg;
    /** 输入条容器（主题切换要重刷底）。 */
    private LinearLayout inputBarHost;
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
    /** 列表底部渐隐层：让消息文字在接近悬浮输入条前淡出（避免两层字重叠） */
    private View bottomFade;
    /** 底部 chip 行的「模型」chip（显示当前模型名；点它拉模型目录）。 */
    private TextView modelChip;
    /** 「思考」chip（当前模型的思考等级）。 */
    private TextView effortChip;
    /** 「用量」chip（上下文占用百分比）。 */
    private TextView usageChip;
    /** 「项目」chip（当前会话的工作区/项目名）。 */
    private TextView projectChip;
    /** 顶部「待处理交互」提示栏（别的会话有提问/审批待答时显示）。 */
    private TextView pendingBar;
    /** 「任务」chip（该会话 todo 进度；无任务时隐藏）。 */
    private TextView taskChip;
    /** 当前模型名（网关确认后写入；空 = 还没拿到）。 */
    private String modelLabel = "";
    private String effortLabel = "";
    private String usageLabel = "";
    private String projectLabel = "";
    private String projectFullPath = "";

    /** chip 的统一外观（胶囊 + 主题色在 applyTheme 里复刷）。 */
    private TextView chip(String label, View.OnClickListener onClick) {
        TextView t = Ui.text(ctx, label, Ui.S_CAP1, Ui.INK_SUB, true);
        t.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 7), Ui.dp(ctx, 12), Ui.dp(ctx, 7));
        t.setBackground(Ui.pill(Ui.CHIP_BG));
        t.setClickable(true);
        Ui.tap(t);
        t.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(ctx, 8);   // chip 之间留 8dp，横滑时彼此分得开
        t.setLayoutParams(lp);
        return t;
    }
    /** 底部渐隐层高度（dp）：够覆盖输入胶囊 + 一点呼吸区 */
    private static final float FADE_H = 132f;
    /** 列表底部留白的"呼吸量"（叠在悬浮层高度之上，见 bottomStack 的布局监听） */
    private int listBasePadBottom;
    /** 「回到底部」按钮的布局参数（下边距要跟悬浮层高度走）。 */
    private FrameLayout.LayoutParams toBottomFlp;
    /** 顶部渐隐层（让消息文字在浮动的目标卡下方淡出，与底部对称）。 */
    private View topFade;
    /** 列表顶部留白基数（叠在目标卡高度之上）。 */
    private int listBasePadTop = 0;
    /** 「待发送」条容器（运行中排队的消息在这里显形；空则隐藏）。 */
    private LinearLayout pendingBox;
    /** 顶部「目标 / 任务」提要条 */
    private TextView planView;
    /** 目标 / 任务的**全文**（planView 只显示截断版，点开看这个）。 */
    private String planFullText = "";
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
        // 2026-10-04 修用户手机实拍：GLASS_BAR（白 80%）在浅薰衣草底上渲染成"实心白条"，
        // 和聊天区割裂。顶栏改回**透明 + 页面同底**，下沿只留一条发丝线（iOS 大标题页制式：
        // 标题与内容同底）。preInput 同理。
        LinearLayout bar = Ui.row(ctx);
        barRow = bar;
        barBg = Ui.glassBar();
        bar.setBackground(barBg);
        bar.setBackground(null);   // 透明：与页面同底，白色玻璃条在浅底上=白横带（bug）
        bar.setMinimumHeight(Ui.dp(ctx, 44));
        bar.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 5), Ui.dp(ctx, 8), Ui.dp(ctx, 5));

        // 左上角箭头：保留箭头样式，但行为改成「打开左侧任务列表」（豆包式两级导航）
        // 2026-10-02：字符 "‹" 换成手写矢量（1.75dp 线宽、圆角端点），字符箭头的粗细/角度
        // 跟着字体跑，放大就是"字符拼的"，是"不高级"最直接的来源之一。
        TextView back = Ui.circleIconButton(ctx, com.dsh.mobile.R.drawable.ic_chevron_left,
                android.graphics.Color.TRANSPARENT, Ui.BRAND, 20f, 36f);
        backBtn = back;
        back.setContentDescription("任务列表");
        back.setOnClickListener(v -> {
            Ui.haptic(v);          // 后退给一次轻震动（用户要求的质感反馈）
            host.onOpenTasks();
        });
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
        // **改用纯色圆角底**（不再用 CardBg + elevation）：真机实测那一套在目标卡上渲染成
        // "灰底 + 一圈深色描边"（用户报"一圈黑框啥玩意"），与其它白色卡片明显不一致。
        // 纯色 SURFACE + 无阴影 = 与卡片同色、干净。
        planView.setBackground(Ui.round(Ui.dp(ctx, Ui.R_CARD), Ui.SURFACE));
        planView.setElevation(0f);
        planView.setMaxLines(1);
        planView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        planView.setVisibility(GONE);
        LinearLayout.LayoutParams planLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        planLp.leftMargin = Ui.dp(ctx, Ui.M_SIDE);
        planLp.rightMargin = Ui.dp(ctx, Ui.M_SIDE);
        planLp.topMargin = Ui.dp(ctx, 6);
        planView.setLayoutParams(planLp);
        // 可点开看全文（原先 8 行封顶 + 省略号且不可点，用户报"显示有问题、点不开看全部"）
        planView.setClickable(true);
        // **不要用 Ui.tap()**：它会把 CardBg 的奶白渐变底**替换**成按压底色，
        // 停按后未必复原 —— 真机表现为目标卡常年挂着一圈灰黑底（用户报"一圈黑框啥玩意"）。
        // 这里保留卡片自己的底，只保留点击响应即可。
        // **就地向下展开**（点一下放大、再点收起）——不是底部弹窗、也不是系统弹窗
        planView.setOnClickListener(v -> { Ui.haptic(v); togglePlanExpand(); });
        // 不再加进布局流：改挂到 stage 顶部浮动层（与底部输入条对称）

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
        // 底部先给 92dp 占位：输入区是**悬浮层**（盖在列表上），最终留白由 bottomStack 的
        // 布局监听按它的实际高度动态覆盖（见下方 addOnLayoutChangeListener）——「N 子智能体」
        // 入口出现时悬浮层会变高，固定留白不够会把最后一条消息压住。
        list.setPadding(Ui.dp(ctx, Ui.M_SIDE), Ui.dp(ctx, 8), Ui.dp(ctx, Ui.M_SIDE), Ui.dp(ctx, 92));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        // iOS 没有 overscroll 光晕（Android 默认会画一圈 colorPrimary 紫蓝光）：滑动时
        // 那道光就是用户报的"一滑动就变色"的来源之一（弹窗里最明显，列表同理）。
        list.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
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
        listWrap.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        listWrap.addView(list);

        toBottom = Ui.circleIconButton(ctx, com.dsh.mobile.R.drawable.ic_arrow_down,
                Ui.BRAND_FILL, Ui.ON_BRAND, 20f, 44f);
        toBottom.setElevation(Ui.dp(ctx, 6));
        toBottomFlp = new FrameLayout.LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 44));
        toBottomFlp.gravity = Gravity.BOTTOM | Gravity.END;
        toBottomFlp.rightMargin = Ui.dp(ctx, 14);
        // 下边距在「悬浮层高度确定后」由布局监听写成 stackH + 12dp ——
        // 否则「N 子智能体」入口会把按钮盖住（2026-10-04 用户报的遮挡）。
        toBottomFlp.bottomMargin = Ui.dp(ctx, 92);
        toBottom.setLayoutParams(toBottomFlp);
        toBottom.setVisibility(GONE);
        toBottom.setOnClickListener(v -> {
            // 超长会话上 smoothScroll 要滚很久，这里直接跳到底，并立刻收起按钮
            scrollToBottom();
            atBottom = true;
            toBottom.setVisibility(GONE);
        });

        // ---- 悬浮舞台（2026-10-04 用户要求：输入条做成悬浮，文字从它后面穿过去）
        //
        // 旧结构是垂直 LinearLayout：列表 → 输入区，两者**不重叠**，于是列表底部被一条
        // 硬边截断（用户看到的"一块同色的挡住了字"）。现在改成 FrameLayout：
        //   · 列表铺满整个舞台（一直画到屏幕底部）；
        //   · 输入区（子智能体入口 + 输入胶囊）作为**悬浮层**贴在舞台底部；
        //   · 列表加 92dp 底部内边距，最后一条消息仍能滚到胶囊上方读全。
        FrameLayout stage = new FrameLayout(ctx);
        stage.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        stage.addView(listWrap);
        // 「回到底部」按钮改挂到舞台层（列表之上、悬浮层之下按添加顺序 == 悬浮层之上），
        // 这样它的位置能跟着悬浮层高度走，不会被「N 子智能体」入口盖住。
        stage.addView(toBottom, toBottomFlp);

        // 底部渐隐（2026-10-04 用户报「后面和前面重叠看不清楚」）：
        // 悬浮输入条下方必须让内容**淡出**，否则消息文字硬撞胶囊、两层字糊在一起。
        // 这是原生能做到的"磨砂替身"——真·背景模糊只有独立窗口才能做（Dialog 那条路），
        // 普通 View 无 API 可用。渐隐层在列表之上、输入区之下。
        if (listBasePadTop == 0) listBasePadTop = Ui.dp(ctx, 12);
        bottomFade = new View(ctx);
        bottomFade.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Ui.dp(ctx, FADE_H)));
        bottomFade.setBackground(buildFade(ctx));
        FrameLayout.LayoutParams ffLp = (FrameLayout.LayoutParams) bottomFade.getLayoutParams();
        ffLp.gravity = Gravity.BOTTOM;
        stage.addView(bottomFade, ffLp);

        // ---- 顶部「待处理交互」提示栏（2026-10-04 用户要求「顶部给一个信息栏提示」）：
        // 别的会话里有待回答的**提问/审批**时，这条栏会露出来；点它切到那条会话 ——
        // 切过去时 App 会 subscribe，网关随即**重放**那条仍未回答的提问（实测日志
        // `interaction replay: trigger=subscribe … questions=1`），卡片就出现了 ✓
        pendingBar = Ui.text(ctx, "", Ui.S_CAP2, Ui.INK, true);
        pendingBar.setPadding(Ui.dp(ctx, Ui.M_SIDE), Ui.dp(ctx, 10), Ui.dp(ctx, Ui.M_SIDE), Ui.dp(ctx, 10));
        pendingBar.setBackground(Ui.round(0, Ui.SELECT_BG));
        pendingBar.setVisibility(GONE);
        pendingBar.setClickable(true);
        Ui.tap(pendingBar);
        pendingBar.setOnClickListener(v -> { Ui.haptic(v); host.onOpenPendingSession(); });
        addView(pendingBar);

        addView(stage);

        // ---- 顶部浮动层（与底部输入条对称）：目标卡浮在列表之上，
        //      列表顶部按其高度留白，且加一层**顶部渐隐**让文字"穿过去淡出"。
        topFade = new View(ctx);
        FrameLayout.LayoutParams tfLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Ui.dp(ctx, FADE_H));
        tfLp.gravity = Gravity.TOP;
        topFade.setLayoutParams(tfLp);
        topFade.setBackground(buildTopFade(ctx));
        stage.addView(topFade);

        FrameLayout.LayoutParams planTopLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        planTopLp.gravity = Gravity.TOP;
        // 与底部输入框同侧边距（M_SIDE=16dp），不要通到两边
        planTopLp.leftMargin = Ui.dp(ctx, Ui.M_SIDE);
        planTopLp.rightMargin = Ui.dp(ctx, Ui.M_SIDE);
        planTopLp.topMargin = Ui.dp(ctx, 6);
        stage.addView(planView, planTopLp);
        planView.addOnLayoutChangeListener((v, l, tt, r, b, ol, ot, or, ob) -> {
            int h = v.getHeight();
            if (h <= 0) return;
            int want = h + listBasePadTop;
            if (list.getPaddingTop() != want) {
                list.setPadding(list.getPaddingLeft(), want, list.getPaddingRight(), list.getPaddingBottom());
                list.setClipToPadding(false);
            }
        });

        // 底部悬浮区（子智能体入口 + 输入胶囊）——必须排在 listWrap / 渐隐之后，绘在最上层
        LinearLayout bottomStack = Ui.col(ctx);
        FrameLayout.LayoutParams bsLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        bsLp.gravity = Gravity.BOTTOM;
        stage.addView(bottomStack, bsLp);

        // **底部留白按悬浮层实际高度算（2026-10-04 用户报「子智能体这条把最下面的字挡住了」）**：
        // 固定 92dp 只够输入胶囊；「N 子智能体」入口出现时悬浮层会高出一截，最后一条消息
        // 就被压在入口条下面。这里监听悬浮层高度，动态把列表底部内边距跟上去（+12dp 呼吸）。
        listBasePadBottom = Ui.dp(ctx, 12);
        bottomStack.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = v.getHeight();
            if (h <= 0) return;
            int want = h + listBasePadBottom;
            if (want != list.getPaddingBottom()) {
                list.setPadding(list.getPaddingLeft(), list.getPaddingTop(),
                        list.getPaddingRight(), want);
                list.setClipToPadding(false);
            }
            // 「回到底部」按钮也跟着抬到悬浮层上方（否则被「N 子智能体」入口盖住）
            if (toBottomFlp != null) {
                int mb = h + Ui.dp(ctx, 10);
                if (toBottomFlp.bottomMargin != mb) {
                    toBottomFlp.bottomMargin = mb;
                    if (toBottom != null) toBottom.setLayoutParams(toBottomFlp);
                }
            }
        });

        // ---- 输入框上方：子智能体入口 + 只读说明
        //
        // 与 PC 端同位置同语义：进主会话后，输入框上方一行「👥 N 子智能体」（N=0 不显示，
        // 避免噪音），点开是底部弹窗列出这条会话的全部子会话，点一项即切过去。
        LinearLayout preInput = Ui.col(ctx);
        preInputRow = preInput;
        preInput.setBackground(null);   // 透明：与页面同底（白玻璃条 bug，同顶栏）

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
        bottomStack.addView(preInput, Ui.fill());

        // ---- 输入条（iOS iMessage 制式：灰底胶囊输入框 + 圆形发送键）
        //
        // 2026-10-03 液态玻璃：整条从"贴着屏幕底的白色通栏"改成**悬浮玻璃胶囊**——
        //   · 外层 12dp 左右边距 + 12dp 下边距，与内容之间留出呼吸（规范要 12~16dp）；
        //   · 底 = GLASS_INPUT 半透明玻璃 + 棱光/顶部高光（CardBg 的圆角 999 = 高/2，真胶囊）；
        //     用 GLASS_INPUT（比 GLASS_BAR 更透）：文字从胶囊下面滚过时能隐约看见 ——
        //     这就是用户要的"悬浮、后面的字直接穿过去"。原生没有真·背景模糊
        //     （RenderEffect 只能糊 View 自身），所以靠"更透 + 描边"表达悬浮。
        //   · 输入框自己的灰底撤掉（透明）—— 胶囊本身就是输入框的底，两层灰底会"脏"。
        LinearLayout inputBar = Ui.row(ctx);
        inputBarHost = inputBar;
        inputBarBg = new Ui.CardBg(999f, Ui.GLASS_INPUT, Ui.dp(ctx, 1f),
                Ui.LINE, 0x00000000, 0f);
        inputBar.setBackground(inputBarBg);
        inputBar.setElevation(Ui.dp(ctx, 3f));   // 悬浮输入条也该有影子（不透明胶囊，安全）
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
            String text = input.getText().toString().trim();
            // **2026-10-04 与电脑端对齐（用户反馈）**：回合运行中不再是"只能停止"——
            //   · 草稿非空 → 照样发送。网关的 message 帧本来带 mode:"queue"（见
            //     GatewayClient.sendMessage），宿主会把这条排进队列，当前回合结束后自动发出；
            //   · 草稿为空 → 才是"停止当前回合"。
            // 这与电脑端/豆包一致：运行中右钮在"有字=发送(排队) / 无字=停止"之间切换。
            if (running && text.isEmpty()) { host.onStopTurn(); return; }
            if (text.isEmpty()) return;
            input.setText("");
            host.onSend(text);
        });
        // 草稿变化要重刷右钮图标（运行中：有字=↑发送 / 无字=■停止）
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) { refreshActionIcon(); }
        });
        input.setOnLongClickListener(v -> { host.onVoiceInput(); return true; });
        inputBar.addView(action);

        // ---- 底部 chip 行（2026-10-04 用户要求「能改模型」，对齐豆包输入框上方那一条）
        //
        // 豆包的样式：输入框上方一条**可横向滑动**的胶囊 chip 组（项目 / 模型 / 用量）。
        // 本 App 先落「模型」这一枚（协议侧 models + select-model 已就绪），
        // 行本身用 HorizontalScrollView，后续加「项目 / 用量」不用改布局。
        HorizontalScrollView chipScroll = new HorizontalScrollView(ctx);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);   // 与全 App 一致：不要 Android 越界光晕
        LinearLayout chipRow = Ui.row(ctx);
        chipRow.setPadding(Ui.dp(ctx, 0), 0, Ui.dp(ctx, 0), 0);
        // 项目（只读展示当前会话的工作区/项目名；点一下看完整路径）
        projectChip = chip("项目", v -> { Ui.haptic(v); host.onProjectTap(); });
        // 模型（点开模型目录）
        modelChip = chip("模型", v -> { Ui.haptic(v); host.onPickModel(); });
        // 思考等级（点开当前模型的思考档位）
        effortChip = chip("思考", v -> { Ui.haptic(v); host.onPickEffort(); });
        // 用量（上下文占用；点一下看详情）
        usageChip = chip("用量", v -> { Ui.haptic(v); host.onUsageTap(); });
        // 任务（该会话的 todo 列表；没有任务时整枚隐藏）
        taskChip = chip("任务", v -> { Ui.haptic(v); host.onTasksTap(); });
        chipRow.addView(projectChip);
        chipRow.addView(modelChip);
        chipRow.addView(effortChip);
        chipRow.addView(usageChip);
        chipRow.addView(taskChip);
        chipScroll.addView(chipRow);
        LinearLayout.LayoutParams csLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        csLp.leftMargin = Ui.dp(ctx, Ui.M_SIDE);
        csLp.rightMargin = Ui.dp(ctx, Ui.M_SIDE);
        csLp.topMargin = Ui.dp(ctx, 4);
        chipScroll.setLayoutParams(csLp);
        bottomStack.addView(chipScroll);

        // 悬浮：左右 16dp（与全 App 的 M_SIDE 对齐）、下方 12dp
        LinearLayout.LayoutParams ibLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ibLp.leftMargin = Ui.dp(ctx, Ui.M_SIDE);
        ibLp.rightMargin = Ui.dp(ctx, Ui.M_SIDE);
        ibLp.topMargin = Ui.dp(ctx, 4);
        ibLp.bottomMargin = Ui.dp(ctx, 12);
        bottomStack.addView(inputBar, ibLp);   // 加在悬浮层里（不再是根布局的兄弟节点）

        // ---- 「待发送」条（2026-10-04 用户要求）：回合运行中把消息排进队列后，
        // 桌面版会在输入框上方留一条待发送提示；App 原先只有一条 Toast，发完就"看不见了"，
        // 用户无法确认自己那条到底排上没有。这里补上同一件事：
        // 每排一条就加一行「待发送 · <内容>」，回合结束（队列被宿主消费）时整块收起。
        //
        // 插到 index 0 = 悬浮层最上面（就在输入区上方），与桌面版位置一致。
        // 注：这是"本机记得的待发送"，不是宿主队列的权威快照 —— 权威快照要靠 control 连接的
        // session-queue 帧（协议 §「排队消息同步」），那条连线另做。
        pendingBox = Ui.col(ctx);
        pendingBox.setVisibility(View.GONE);
        bottomStack.addView(pendingBox, 0);
    }

    /**
     * 追加一条「待发送」提示（运行中排队发送时调）。
     *
     * @param text 排队的内容（单行显示，过长省略）
     */
    public void addPendingSend(String text) {
        if (text == null || text.trim().isEmpty()) return;
        postOnUi(() -> {
            if (pendingBox == null) return;
            TextView row = Ui.text(ctx, "待发送 · " + text.trim(), Ui.S_CAP1, Ui.INK_SUB, false);
            row.setSingleLine(true);
            row.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 7), Ui.dp(ctx, 12), Ui.dp(ctx, 7));
            row.setBackground(Ui.round(Ui.dp(ctx, 12), Ui.CHIP_BG));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.dp(ctx, Ui.M_SIDE);
            lp.rightMargin = Ui.dp(ctx, Ui.M_SIDE);
            lp.topMargin = Ui.dp(ctx, 4);
            row.setLayoutParams(lp);
            pendingBox.addView(row);
            pendingBox.setVisibility(View.VISIBLE);
        });
    }

    /**
     * 用**网关队列**渲染「待发送」条（权威版本；条目可点，弹 立即插入 / 编辑 / 删除）。
     *
     * @param items 每项 {itemId, text, placement}；空 = 队列空了，整块收起
     */
    public void setPendingItems(final java.util.List<String[]> items) {
        postOnUi(() -> {
            if (pendingBox == null) return;
            pendingBox.removeAllViews();
            if (items == null || items.isEmpty()) { pendingBox.setVisibility(View.GONE); return; }
            for (String[] it : items) {
                if (it == null || it.length < 2) continue;
                final String itemId = it[0];
                final String text = it[1];
                String place = it.length > 2 ? it[2] : "";
                String label = "steering".equals(place) ? "即将插入" : "待发送";
                TextView row = Ui.text(ctx, label + " · " + text, Ui.S_CAP1, Ui.INK_SUB, false);
                row.setSingleLine(true);
                row.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 7), Ui.dp(ctx, 12), Ui.dp(ctx, 7));
                row.setBackground(Ui.round(Ui.dp(ctx, 12), Ui.CHIP_BG));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = Ui.dp(ctx, Ui.M_SIDE);
                lp.rightMargin = Ui.dp(ctx, Ui.M_SIDE);
                lp.topMargin = Ui.dp(ctx, 4);
                row.setLayoutParams(lp);
                row.setClickable(true);
                Ui.tap(row);
                row.setOnClickListener(v -> { Ui.haptic(v); host.onQueueItemAction(itemId, text); });
                pendingBox.addView(row);
            }
            pendingBox.setVisibility(View.VISIBLE);
        });
    }

    /** 清空「待发送」条（回合结束 / 切会话 / 队列已被消费时调）。 */
    public void clearPendingSends() {
        postOnUi(() -> {
            if (pendingBox == null) return;
            pendingBox.removeAllViews();
            pendingBox.setVisibility(View.GONE);
        });
    }

    /** 更新底部「模型」chip 的文案（网关确认选中后调用；label 为空则回落到「模型」）。 */
    public void setModelLabel(String label) {
        modelLabel = label == null ? "" : label;
        if (modelChip != null) modelChip.setText(modelLabel.isEmpty() ? "模型" : ("模型 · " + modelLabel));
    }

    /** 「思考」chip：当前思考等级（如 medium）。 */
    public void setEffortLabel(String label) {
        effortLabel = label == null ? "" : label;
        if (effortChip != null) effortChip.setText(effortLabel.isEmpty() ? "思考" : ("思考 · " + effortLabel));
    }

    /** 「用量」chip：上下文占用（如 12%）。 */
    public void setUsageLabel(String label) {
        usageLabel = label == null ? "" : label;
        if (usageChip != null) usageChip.setText(usageLabel.isEmpty() ? "用量" : ("用量 · " + usageLabel));
    }

    /** 「项目」chip：当前会话的工作区/项目名（完整路径留着点开后展示）。 */
    public void setProjectLabel(String name, String fullPath) {
        projectLabel = name == null ? "" : name;
        projectFullPath = fullPath == null ? "" : fullPath;
        if (projectChip != null) {
            projectChip.setText(projectLabel.isEmpty() ? "项目" : ("项目 · " + projectLabel));
        }
    }

    /**
     * 顶部提示栏：别的会话有待回答的提问/审批时露出来（点它由宿主切过去）。
     *
     * @param text 为空则整条收起
     */
    public void setPendingBanner(String text) {
        postOnUi(() -> {
            if (pendingBar == null) return;
            boolean show = text != null && !text.trim().isEmpty();
            pendingBar.setText(show ? text : "");
            pendingBar.setVisibility(show ? View.VISIBLE : View.GONE);
        });
    }

    /** 目标 / 任务的渲染数据（结构化，供"完成划掉、进行中转圈"用）。 */
    private String planGoalText = "";
    private String[] planTaskContents = new String[0];
    private String[] planTaskStatuses = new String[0];
    private int planSpinFrame = 0;
    private static final String[] PLAN_SPIN = { "\u25D0", "\u25D3", "\u25D1", "\u25D2" };
    private final android.os.Handler planSpinHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable planSpinTick = new Runnable() {
        @Override public void run() {
            planSpinFrame = (planSpinFrame + 1) % PLAN_SPIN.length;
            renderPlanRich();
            if (hasPlanInProgress()) planSpinHandler.postDelayed(this, 320L);
        }
    };
    private boolean hasPlanInProgress() {
        for (String s : planTaskStatuses) if ("in_progress".equals(s)) return true;
        return false;
    }

    /** 结构化设置目标 / 任务：完成=打钩+删除线置灰，进行中=旋转指示，待办=空心圈。 */
    public void setPlanRich(final String goal, final java.util.List<String[]> todos) {
        postOnUi(() -> {
            java.util.ArrayList<String> cs = new java.util.ArrayList<>();
            java.util.ArrayList<String> ss = new java.util.ArrayList<>();
            if (todos != null) for (String[] td : todos) {
                if (td == null || td.length < 2) continue;
                cs.add(td[0]); ss.add(td[1] == null ? "pending" : td[1]);
            }
            planGoalText = goal == null ? "" : goal;
            planTaskContents = cs.toArray(new String[0]);
            planTaskStatuses = ss.toArray(new String[0]);
            renderPlanRich();
            planSpinHandler.removeCallbacks(planSpinTick);
            if (hasPlanInProgress()) planSpinHandler.postDelayed(planSpinTick, 320L);
        });
    }

    /** 按状态拼装卡片文本（用 Spannable 给完成项加删除线+置灰）。 */
    private void renderPlanRich() {
        if (planView == null) return;
        if (planGoalText.isEmpty() && planTaskContents.length == 0) {
            planView.setVisibility(View.GONE);
            return;
        }
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        if (!planGoalText.isEmpty()) sb.append("\u76EE\u6807\uFF1A").append(planGoalText);
        for (int i = 0; i < planTaskContents.length; i++) {
            if (sb.length() > 0) sb.append((char) 10);
            String st = planTaskStatuses[i];
            boolean done = "completed".equals(st);
            String icon = done ? "\u2713 " : ("in_progress".equals(st) ? (PLAN_SPIN[planSpinFrame] + " ") : "\u25CB ");
            int start = sb.length();
            sb.append(icon).append(planTaskContents[i] == null ? "" : planTaskContents[i]);
            if (done) {
                sb.setSpan(new android.text.style.StrikethroughSpan(), start, sb.length(), 0);
                sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.INK_FAINT), start, sb.length(), 0);
            }
        }
        planFullText = sb.toString();
        planView.setText(sb);
        planView.setVisibility(View.VISIBLE);
    }

    /** 目标卡是否处于"展开（完整列表）"状态。 */
    private boolean planExpanded = false;
    /** 宿主当前的简洁模式（收起时用它决定留 1 行还是 8 行）。 */
    private boolean planCompactMode = false;

    /** 就地展开 / 收起目标卡：展开时显示完整任务列表（往下长出来），收起时回到 1 行摘要。 */
    public void togglePlanExpand() {
        if (planView == null) return;
        planExpanded = !planExpanded;
        planView.setMaxLines(planExpanded ? 8 : 1);
        planView.setEllipsize(planExpanded ? null : android.text.TextUtils.TruncateAt.END);
        planView.setText(planFullText);
        if (planView.getParent() instanceof android.view.View) {
            ((android.view.View) planView.getParent()).requestLayout();
        }
    }

    /** 顶部渐隐：BG → 半透明 → 全透明（从上往下），与底部渐隐镜像。 */
    private android.graphics.drawable.Drawable buildTopFade(Context c) {
        int a = Ui.BG & 0x00FFFFFF;
        return new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { Ui.BG, a | 0xAA000000, a });
    }

    /** 点「目标 / 任务」卡片时用：取全文。 */
    public String planFullText() { return planFullText; }

    /** 供宿主在用户点「项目」时取完整路径。 */
    public String projectPath() { return projectFullPath; }

    /**
     * 「任务」chip：传该会话的 todo 进度（已完成/总数）。
     *
     * @param done  已完成条数
     * @param total 总条数；{@code 0} = 该会话没有任务列表 → 整枚隐藏（不占横滑空间）
     */
    public void setTasksProgress(int done, int total) {
        if (taskChip == null) return;
        if (total <= 0) {
            taskChip.setVisibility(View.GONE);
            return;
        }
        taskChip.setVisibility(View.VISIBLE);
        taskChip.setText("任务 · " + done + "/" + total);
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
        // **展开状态优先**：否则每次计划刷新（setPlanText）都会把用户点开的卡片压回 1 行，
        // 表现为"点开没反应、卡片永远只占一节"。
        planView.setMaxLines(planExpanded ? 8 : 1);
        planFullText = planText == null ? "" : planText;
        planCompactMode = compact;
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
            refreshActionIcon();
            // 只读子会话里停止按钮同样禁用（宿主也只会用 subagents.interruptByParent 停子会话）
            if (readOnly) Ui.setButtonEnabled(action, false);
            scheduleRefresh();
        });
    }

    /**
     * 刷右钮图标：**运行中「有草稿 = ↑发送（排队）／无草稿 = ■停止」**，空闲恒为 ↑。
     *
     * <p>2026-10-04 与电脑端对齐：豆包/PC 在回合进行时仍然允许把消息排进队列，
     * 只有当输入框是空的、那颗钮才表示"停止"。草稿变化（TextWatcher）与运行态变化都会调到这。
     */
    private void refreshActionIcon() {
        if (action == null) return;
        boolean stopping = running && input != null
                && input.getText().toString().trim().isEmpty();
        Ui.setIconBg(action,
                stopping ? com.dsh.mobile.R.drawable.ic_stop : com.dsh.mobile.R.drawable.ic_arrow_up,
                stopping ? Ui.INK : Ui.ON_BRAND,
                stopping ? Ui.STOP_BG : Ui.BRAND_FILL,
                42f, stopping ? 17f : 20f);
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
    /**
     * 底部渐隐：透明 → 页面底色（三档，比两档更接近 iOS 的柔和曲线）。
     *
     * <p>为什么需要它：输入条是**悬浮**的，消息文字会和胶囊里的文字叠在一起（用户原话
     * 「后面和前面重叠看不清楚了」）。原生没有"给普通 View 做背景模糊"的 API
     * （{@code RenderEffect} 只能模糊控件自身；{@code applyWindowBlur} 只对独立窗口有效），
     * 所以内容的"淡出"就是能做到的磨砂替身：越接近输入条越透明，不产生两层硬字。
     */
    private static android.graphics.drawable.Drawable buildFade(Context c) {
        int clear = Ui.BG & 0x00FFFFFF;          // 同色、全透明
        int half = clear | 0x99000000;           // 同色、60% —— 中段就开始压
        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[] { clear, half, Ui.BG });
        g.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        return g;
    }

    public void applyTheme() {
        setBackgroundColor(Ui.BG);
        if (bottomFade != null) bottomFade.setBackground(buildFade(ctx));   // 渐隐色随主题
        if (topFade != null) topFade.setBackground(buildTopFade(ctx));
        if (modelChip != null) {
            for (TextView c : new TextView[] { projectChip, modelChip, effortChip, usageChip, taskChip }) {
                if (c == null) continue;
                c.setTextColor(Ui.INK_SUB);
                c.setBackground(Ui.pill(Ui.CHIP_BG));   // chip 底色随主题
            }
        }
        // 顶栏/preInput 透明（白玻璃条 bug 修复后不再挂玻璃底），只刷下沿发丝线
        if (barRow != null) barRow.setBackground(null);
        if (barLine != null) barLine.setBackgroundColor(Ui.HAIRLINE);
        if (backBtn != null) Ui.setIcon(backBtn, com.dsh.mobile.R.drawable.ic_chevron_left, Ui.BRAND);
        if (menuBtn != null) Ui.setIcon(menuBtn, com.dsh.mobile.R.drawable.ic_more, Ui.INK_SUB);
        if (title != null) title.setTextColor(Ui.INK);
        if (subtitle != null) subtitle.setTextColor(Ui.INK_FAINT);
        if (planView != null) {
            planView.setTextColor(Ui.INK_SUB);
            // 必须与构造函数里那一份**逐参一致**（圆角 R_CARD + 奶白渐变体）：
            // 旧版这里写的是 12dp + SURFACE，切一次主题提要卡就悄悄变小、还从玻璃变成实心。
            planView.setBackground(Ui.round(Ui.dp(ctx, Ui.R_CARD), Ui.SURFACE));
            planView.setElevation(0f);
        }
        if (preInputRow != null) preInputRow.setBackground(null);   // 透明（同顶栏修复）
        if (inputBarBg != null) {
            // 主题切换必须重建输入条胶囊（旧版漏了这条 → 深色下输入条还是浅白底）
            inputBarBg = new Ui.CardBg(999f, Ui.GLASS_INPUT, Ui.dp(ctx, 1f),
                    Ui.LINE, 0x00000000, 0f);
            inputBarHost.setBackground(inputBarBg);
        }
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
        if (pick != null) {
            // 圆钮底色（IconBg）创建时烘死：只 setIcon 会留下深色档的黑底（实测 round2）
            Ui.setIconBg(pick, com.dsh.mobile.R.drawable.ic_plus, Ui.INK_SUB, Ui.CHIP_BG, 34f, 18f);
        }
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
