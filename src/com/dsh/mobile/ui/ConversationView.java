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

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** 会话界面：顶部栏 + 状态横幅 + 消息列表 + 输入条。 */
public final class ConversationView extends LinearLayout implements ChatAdapter.Host {

    public interface Host extends ChatAdapter.Host {
        void onBack();
        void onSend(String text);
        void onStop();
        void onLoadMore();
        void onMenu();
        void onVoiceInput();
        void onPickImage();
        void onDownloadFile(ChatItem item, String path);
        void onCopyPath(String path);
    }

    private final Context ctx;
    private final Host host;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final TextView title;
    private final TextView subtitle;
    private final TextView banner;
    private final ListView list;
    private final ChatAdapter adapter;
    private final EditText input;
    private final TextView action;
    /** 悬浮「回到底部」按钮：滚上去看历史时出现 */
    private TextView toBottom;
    /** 顶部「目标 / 任务」提要条 */
    private TextView planView;

    private boolean compact;
    private List<ChatItem> full = new ArrayList<>();
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

    public ConversationView(Context ctx, Host host) {
        super(ctx);
        this.ctx = ctx;
        this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(Ui.BG);

        // ---- 顶部栏
        LinearLayout bar = Ui.row(ctx);
        bar.setBackgroundColor(Ui.SURFACE);
        bar.setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8));

        TextView back = Ui.circleButton(ctx, "‹", 0x00000000, Ui.INK);
        back.setTextSize(26f);
        back.setOnClickListener(v -> host.onBack());
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

        TextView menu = Ui.circleButton(ctx, "⋮", 0x00000000, Ui.INK_SUB);
        menu.setTextSize(22f);
        menu.setOnClickListener(v -> host.onMenu());
        bar.addView(menu);
        addView(bar, Ui.fill());

        // ---- 状态横幅
        banner = Ui.text(ctx, "", 12f, 0xFF92400E, false);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 7), Ui.dp(ctx, 12), Ui.dp(ctx, 7));
        banner.setBackgroundColor(0xFFFFF7E6);
        banner.setVisibility(GONE);
        addView(banner, Ui.fill());

        // ---- 目标 / 任务提要（有目标或任务时才显示）
        planView = Ui.text(ctx, "", 12f, Ui.INK_SUB, false);
        planView.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 8), Ui.dp(ctx, 14), Ui.dp(ctx, 8));
        planView.setBackgroundColor(0xFFF5F8FF);
        planView.setMaxLines(8);
        planView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        planView.setVisibility(GONE);
        addView(planView, Ui.fill());

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
        list.setSelector(new android.graphics.drawable.ColorDrawable(0x00000000));
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
        toBottom.setTextColor(0xFFFFFFFF);
        toBottom.setGravity(Gravity.CENTER);
        toBottom.setBackground(Ui.pill(Ui.BRAND));
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

        // ---- 输入条
        LinearLayout inputBar = Ui.row(ctx);
        inputBar.setBackgroundColor(Ui.SURFACE);
        inputBar.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 8), Ui.dp(ctx, 10), Ui.dp(ctx, 8));

        input = new EditText(ctx);
        input.setHint("给 Agent 派个任务…");
        input.setTextSize(15f);
        input.setHintTextColor(Ui.INK_FAINT);
        input.setTextColor(Ui.INK);
        input.setBackground(Ui.roundStroke(Ui.dp(ctx, 20), 0xFFF5F6FA, Ui.dp(ctx, 0.8f), Ui.LINE));
        input.setPadding(Ui.dp(ctx, 15), Ui.dp(ctx, 10), Ui.dp(ctx, 15), Ui.dp(ctx, 10));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        input.setMaxLines(5);
        input.setMinLines(1);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        input.setLayoutParams(ilp);

        TextView pick = Ui.circleButton(ctx, "＋", 0xFFF1F3F9, Ui.INK_SUB);
        pick.setTextSize(19f);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(ctx, 38), Ui.dp(ctx, 38));
        plp.rightMargin = Ui.dp(ctx, 6);
        pick.setLayoutParams(plp);
        pick.setOnClickListener(v -> host.onPickImage());
        inputBar.addView(pick);
        inputBar.addView(input);

        action = Ui.circleButton(ctx, "↑", Ui.BRAND, 0xFFFFFFFF);
        action.setTextSize(20f);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dp(ctx, 42), Ui.dp(ctx, 42));
        alp.leftMargin = Ui.dp(ctx, 8);
        action.setLayoutParams(alp);
        action.setOnClickListener(v -> {
            if (running) { host.onStop(); return; }
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

    public void setTitleText(String t) {
        title.setText(t == null || t.isEmpty() ? "对话" : t);
    }

    public void setSubtitleText(String s) {
        subtitle.setText(s == null ? "" : s);
        subtitle.setVisibility(s == null || s.isEmpty() ? GONE : VISIBLE);
    }

    public void setBanner(String text, boolean error) {
        if (text == null || text.isEmpty()) { banner.setVisibility(GONE); return; }
        banner.setVisibility(VISIBLE);
        banner.setText(text);
        banner.setTextColor(error ? 0xFF991B1B : 0xFF92400E);
        banner.setBackgroundColor(error ? 0xFFFEF2F2 : 0xFFFFF7E6);
    }

    /** 顶部提要：当前目标 + 任务清单（空则隐藏）。 */
    public void setPlan(String text) {
        if (planView == null) return;
        if (text == null || text.trim().isEmpty()) {
            planView.setVisibility(GONE);
            return;
        }
        planView.setText(text);
        planView.setVisibility(VISIBLE);
    }

    public void setRunning(boolean value, String hint) {
        this.running = value;
        this.runningHint = hint == null ? "" : hint;
        adapter.setRunningHint(this.runningHint);
        action.setText(value ? "■" : "↑");
        action.setTextSize(value ? 16f : 20f);
        action.setBackground(Ui.pill(value ? 0xFFE5E9F5 : Ui.BRAND));
        action.setTextColor(value ? Ui.INK : 0xFFFFFFFF);
    }

    public void setItems(List<ChatItem> items) {
        this.full = items == null ? new ArrayList<ChatItem>() : items;
        applyFilter();
    }

    /** 简洁模式：完成的命令不显示，只保留"正在运行"；空响应气泡也不显示。 */
    public void setCompact(boolean value) {
        this.compact = value;
        adapter.setCompact(value);
        applyFilter();
    }

    private void applyFilter() {
        List<ChatItem> view = new ArrayList<>();
        for (ChatItem it : full) {
            // 没有正文的助手气泡一律不显示（只有思考、或工具回合的空壳）——两种模式都适用
            if (it.kind == ChatItem.ASSISTANT && it.text.trim().isEmpty() && !it.streaming) continue;
            if (compact && it.kind == ChatItem.TOOL && !it.toolRunning) continue;
            view.add(it);
        }
        adapter.setItems(view);
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

    // ---- ChatAdapter.Host 转发
    @Override public void onDownloadFile(ChatItem item, String path) { host.onDownloadFile(item, path); }
    @Override public void onCopyPath(String path) { host.onCopyPath(path); }
    @Override public void onApprove(ChatItem item, String outcome) { host.onApprove(item, outcome); }
    @Override public void onQuestionSubmit(ChatItem item, JSONArray answers) { host.onQuestionSubmit(item, answers); }
    @Override public void onQuestionCancel(ChatItem item) { host.onQuestionCancel(item); }
}
