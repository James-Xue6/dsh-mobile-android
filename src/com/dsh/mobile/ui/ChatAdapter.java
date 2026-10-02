package com.dsh.mobile.ui;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dsh.mobile.R;
import com.dsh.mobile.model.ChatItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 对话行渲染：用户气泡 / 助手气泡 / 工具条 / 系统条 / 审批卡 / 提问卡。 */
public final class ChatAdapter extends BaseAdapter {

    public interface Host {
        void onApprove(ChatItem item, String outcome);
        void onQuestionSubmit(ChatItem item, JSONArray answers);
        void onQuestionCancel(ChatItem item);

        /** 点交付物行：下载到手机（由宿主实现）。 */
        void onDownloadFile(ChatItem item, String path);
        /** 长按交付物行：复制路径。 */
        void onCopyPath(String path);
    }

    private final Context ctx;
    private final Host host;
    /**
     * 气泡最大宽度：按**当前**屏宽算，**不是**构造时的死值。
     *
     * 旧实现只在构造函数里算一次，而清单声明了 configChanges，旋转时 Activity 不重建 →
     * 横屏后气泡仍然只按竖屏宽度排版（屏宽 1080→1920 时明显偏窄）。
     * 现在由 {@link #refreshMetrics()} 现算，MainActivity.onConfigurationChanged 会再调一次。
     */
    private int maxBubble;
    private List<ChatItem> items = new ArrayList<>();
    private String runningHint;
    private boolean compact;

    public void setCompact(boolean value) { this.compact = value; }

    public ChatAdapter(Context ctx, Host host) {
        this.ctx = ctx;
        this.host = host;
        refreshMetrics();
    }

    /**
     * 按当前屏宽重算气泡/图片的最大宽度（宽度的 80%）。
     * 尺寸**现取** getResources().getDisplayMetrics()，不缓存旧值。
     */
    public void refreshMetrics() {
        int w = ctx.getResources().getDisplayMetrics().widthPixels;
        this.maxBubble = (int) (w * 0.80f);
    }

    public void setItems(List<ChatItem> list) { this.items = list; }
    public List<ChatItem> items() { return items; }
    public void setRunningHint(String hint) { this.runningHint = hint; }

    @Override public int getCount() { return items.size(); }
    @Override public Object getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override public int getViewTypeCount() { return 9; }

    @Override public int getItemViewType(int position) { return items.get(position).kind; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ChatItem it = items.get(position);
        switch (it.kind) {
            case ChatItem.USER:      return userBubble(it);
            case ChatItem.ASSISTANT: return assistantBubble(it);
            case ChatItem.TOOL:      return toolRow(it);
            case ChatItem.APPROVAL:  return approvalCard(it);
            case ChatItem.QUESTION:  return questionCard(it);
            case ChatItem.FILES:     return filesCard(it);
            case ChatItem.AGENT:     return agentCard(it);
            case ChatItem.STEP:      return stepRow(it);
            default:                 return systemRow(it);
        }
    }

    // ------------------------------------------------------------ 用户

    private View userBubble(ChatItem it) {
        LinearLayout wrap = Ui.row(ctx);
        wrap.setPadding(0, Ui.dp(ctx, 5), 0, Ui.dp(ctx, 5));
        wrap.setGravity(Gravity.END);

        TextView bubble = Ui.text(ctx, it.text, 15.5f, 0xFFFFFFFF, false);
        bubble.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 10), Ui.dp(ctx, 14), Ui.dp(ctx, 10));
        bubble.setMaxWidth(maxBubble);
        android.graphics.drawable.GradientDrawable bg = Ui.round(Ui.dp(ctx, 18), Ui.BRAND);
        bubble.setBackground(bg);
        bubble.setTextIsSelectable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bubble.setLayoutParams(lp);

        // 图片贴在气泡上方（历史里的图片块原先只显示「[图片]」）
        if (it.images != null && !it.images.isEmpty()) {
            for (android.graphics.Bitmap bmp : it.images) {
                android.widget.ImageView iv = new android.widget.ImageView(ctx);
                iv.setImageBitmap(bmp);
                iv.setAdjustViewBounds(true);
                iv.setMaxWidth(maxBubble);
                iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                ilp.bottomMargin = Ui.dp(ctx, 6);
                iv.setLayoutParams(ilp);
                wrap.addView(iv);
            }
        }
        wrap.addView(bubble);
        return wrap;
    }

    // ------------------------------------------------------------ 助手

    private View assistantBubble(ChatItem it) {
        LinearLayout wrap = Ui.col(ctx);
        wrap.setPadding(0, Ui.dp(ctx, 5), 0, Ui.dp(ctx, 5));

        String body = it.text == null ? "" : it.text;
        boolean empty = body.trim().isEmpty();
        if (empty) {
            body = it.streaming ? "正在思考…" : "(空响应)";
        }
        if (it.streaming && !empty) body = body + " ▌";

        TextView bubble = new TextView(ctx);
        bubble.setText(Ui.md(ctx, body));
        bubble.setTextSize(15.5f);
        bubble.setTextColor(Ui.INK);
        bubble.setLineSpacing(Ui.dp(ctx, 4), 1.08f);
        bubble.setIncludeFontPadding(false);
        bubble.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 11), Ui.dp(ctx, 14), Ui.dp(ctx, 11));
        bubble.setMaxWidth(maxBubble);
        bubble.setBackground(Ui.roundStroke(Ui.dp(ctx, 18), Ui.SURFACE, Ui.dp(ctx, 0.8f), Ui.LINE));
        bubble.setTextIsSelectable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.START;
        bubble.setLayoutParams(lp);
        wrap.addView(bubble);

        // 思考过程折叠展示：**完整模式专属**。简洁模式要的是"和 PC 端一致的一句话摘要"，
        // 每个助手气泡后面再挂一条「▸ 思考过程」正是用户报的"显示了一大堆"。
        if (!compact && it.reasoning != null && !it.reasoning.trim().isEmpty()) {
            TextView r = Ui.text(ctx, "▸ 思考过程", 12.5f, Ui.INK_FAINT, false);
            r.setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 4), 0, 0);
            r.setTag(it.reasoning);
            r.setOnClickListener(v -> {
                boolean shown = v.getTag(R.id.tag_reason) != null;
                TextView tv = (TextView) v;
                if (shown) { tv.setText("▸ 思考过程"); v.setTag(R.id.tag_reason, null); }
                else { tv.setText((String) v.getTag()); v.setTag(R.id.tag_reason, Boolean.TRUE); }
            });
            wrap.addView(r);
        }
        return wrap;
    }

    // ------------------------------------------------------------ 专家团 / 子代理回传

    /**
     * 专家团成员或子代理回传的正文。说话的不是用户，所以不做成右侧蓝色气泡；
     * 顶部给一行「谁说的」摘要，正文照样可选中复制。
     */
    private View agentCard(ChatItem it) {
        LinearLayout wrap = Ui.col(ctx);
        wrap.setPadding(0, Ui.dp(ctx, 5), 0, Ui.dp(ctx, 5));

        LinearLayout card = Ui.col(ctx);
        card.setPadding(Ui.dp(ctx, 13), Ui.dp(ctx, 9), Ui.dp(ctx, 13), Ui.dp(ctx, 10));
        card.setBackground(Ui.roundStroke(Ui.dp(ctx, 16), Ui.SURFACE, Ui.dp(ctx, 0.8f), 0xFFC7D2FE));

        TextView head = Ui.text(ctx, "👥 " + (it.agentName.isEmpty() ? "专家团回传" : it.agentName),
                12.5f, Ui.BRAND, true);
        card.addView(head);

        String body = it.text == null ? "" : it.text;
        if (body.trim().isEmpty()) body = "(空回传)";
        TextView bubble = Ui.text(ctx, body, 14.5f, Ui.INK, false);
        bubble.setPadding(0, Ui.dp(ctx, 5), 0, 0);
        bubble.setMaxWidth(maxBubble);
        bubble.setTextIsSelectable(true);
        card.addView(bubble);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.START;
        card.setLayoutParams(lp);
        wrap.addView(card);
        return wrap;
    }

    // ------------------------------------------------------------ 工具

    private View toolRow(ChatItem it) {
        if (compact) {
            // 简洁模式：一行说清"正在执行什么"，不展示命令参数
            LinearLayout one = Ui.row(ctx);
            one.setPadding(0, Ui.dp(ctx, 3), 0, Ui.dp(ctx, 3));
            TextView dot = Ui.text(ctx, "●", 10f, it.toolError ? Ui.ERR : Ui.WARN, false);
            one.addView(dot);
            TextView line = Ui.text(ctx, "正在执行：" + (it.toolName.isEmpty() ? "工具" : it.toolName),
                    13f, Ui.INK_SUB, false);
            line.setPadding(Ui.dp(ctx, 6), 0, 0, 0);
            one.addView(line);
            return one;
        }
        LinearLayout wrap = Ui.row(ctx);
        wrap.setPadding(0, Ui.dp(ctx, 2), 0, Ui.dp(ctx, 2));

        LinearLayout card = Ui.col(ctx);
        card.setPadding(Ui.dp(ctx, 11), Ui.dp(ctx, 8), Ui.dp(ctx, 11), Ui.dp(ctx, 8));
        card.setBackground(Ui.round(Ui.dp(ctx, 13), Ui.BRAND_SOFT));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout head = Ui.row(ctx);
        String status = it.toolError ? "失败" : (it.toolRunning ? "运行中" : "完成");
        int dotColor = it.toolError ? Ui.ERR : (it.toolRunning ? Ui.WARN : Ui.OK);

        TextView dot = Ui.text(ctx, "●", 10f, dotColor, false);
        head.addView(dot);

        TextView name = Ui.text(ctx, it.toolName.isEmpty() ? "工具" : it.toolName, 13f, Ui.INK, true);
        name.setPadding(Ui.dp(ctx, 6), 0, Ui.dp(ctx, 8), 0);
        head.addView(name);

        head.addView(Ui.text(ctx, status, 12f, Ui.INK_SUB, false));
        card.addView(head);

        if (it.toolPreview != null && !it.toolPreview.trim().isEmpty()) {
            TextView p = Ui.text(ctx, it.toolPreview, 12f, Ui.INK_SUB, false);
            p.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 3), 0, 0);
            p.setMaxLines(4);
            p.setMaxWidth(maxBubble);
            p.setEllipsize(android.text.TextUtils.TruncateAt.END);
            p.setTypeface(android.graphics.Typeface.MONOSPACE);
            card.addView(p);
        }
        wrap.addView(card);
        return wrap;
    }

    // ------------------------------------------------------------ 简洁模式：过程摘要行

    /**
     * 「简洁」模式下每个回合只有这一行：PC 端工作台同款的过程摘要
     * （例如「执行了命令 · pwsh」「已读取文件，执行了命令」）。
     *
     * 这里**只显示摘要文本**：不显示工具参数、不显示工具输出，也不显示思考块 ——
     * 那正是用户报的「手机上显示了一大堆」。想看细节切回完整模式。
     * 正在运行 / 失败仍然要看得出来，否则用户会以为卡住了。
     */
    private View stepRow(ChatItem it) {
        LinearLayout wrap = Ui.row(ctx);
        wrap.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4));

        int color = it.stepError ? Ui.ERR : (it.stepRunning ? Ui.WARN : Ui.INK_SUB);
        TextView dot = Ui.text(ctx, it.stepError ? "⚠" : (it.stepRunning ? "◐" : "·"),
                11.5f, color, false);
        wrap.addView(dot);

        TextView line = Ui.text(ctx, it.text == null ? "" : it.text, 12.5f, color, false);
        line.setPadding(Ui.dp(ctx, 6), 0, 0, 0);
        line.setTextIsSelectable(true);
        wrap.addView(line);
        return wrap;
    }

    // ------------------------------------------------------------ 系统

    private View systemRow(ChatItem it) {
        String s = it.text;
        if (s == null || s.trim().isEmpty()) s = runningHint;
        LinearLayout wrap = Ui.col(ctx);
        wrap.setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 6));
        TextView t = Ui.text(ctx, s == null ? "" : s, 12.5f, Ui.INK_FAINT, false);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        wrap.addView(t);
        return wrap;
    }

    // ------------------------------------------------------------ 审批卡

    private View approvalCard(ChatItem it) {
        LinearLayout wrap = Ui.col(ctx);
        wrap.setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 6));

        LinearLayout card = Ui.col(ctx);
        card.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12));
        card.setBackground(Ui.roundStroke(Ui.dp(ctx, 16), Ui.SURFACE, Ui.dp(ctx, 1.2f), 0xFFFDE68A));
        card.setLayoutParams(Ui.fill());

        TextView title = Ui.text(ctx, "需要你的批准", 14.5f, Ui.INK, true);
        card.addView(title);

        TextView tool = Ui.text(ctx, "工具：" + (it.toolName.isEmpty() ? "未知" : it.toolName), 13f, Ui.BRAND, false);
        tool.setPadding(0, Ui.dp(ctx, 5), 0, 0);
        tool.setTypeface(android.graphics.Typeface.MONOSPACE);
        card.addView(tool);

        if (it.reason != null && !it.reason.trim().isEmpty()) {
            TextView r = Ui.text(ctx, it.reason, 13f, Ui.INK_SUB, false);
            r.setPadding(0, Ui.dp(ctx, 5), 0, 0);
            card.addView(r);
        }

        if (it.sendError != null && !it.sendError.isEmpty()) {
            TextView warn = Ui.text(ctx, "⚠ " + it.sendError, 12.5f, Ui.ERR, false);
            warn.setPadding(0, Ui.dp(ctx, 8), 0, 0);
            card.addView(warn);
        }

        if (it.resolved) {
            String label;
            if ("allowed-once".equals(it.resolvedOutcome)) label = "✓ 已批准";
            else if ("rejected".equals(it.resolvedOutcome)) label = "✕ 已拒绝";
            else if ("cancelled".equals(it.resolvedOutcome)) label = "已取消";
            else label = "已由其他端处理";
            TextView done = Ui.text(ctx, label, 13f, Ui.INK_SUB, true);
            done.setPadding(0, Ui.dp(ctx, 9), 0, 0);
            card.addView(done);
        } else if (it.pendingConfirm) {
            // 已发出、还没等到电脑端回执：不显示 ✓（回执没到就可能是进了黑洞），
            // 也不显示按钮（避免重复提交）（评审 P0-3）。
            TextView waiting = Ui.text(ctx, "已发送，等待电脑确认…", 13f, Ui.INK_SUB, true);
            waiting.setPadding(0, Ui.dp(ctx, 9), 0, 0);
            card.addView(waiting);
        } else {
            LinearLayout actions = Ui.row(ctx);
            actions.setLayoutParams(Ui.fill());
            actions.setPadding(0, Ui.dp(ctx, 11), 0, 0);

            TextView allow = actionButton("批准一次", Ui.BRAND, 0xFFFFFFFF);
            allow.setOnClickListener(v -> host.onApprove(it, "allowed-once"));
            LinearLayout.LayoutParams a1 = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            a1.rightMargin = Ui.dp(ctx, 8);
            allow.setLayoutParams(a1);
            actions.addView(allow);

            TextView deny = actionButton("拒绝", Ui.SURFACE, Ui.ERR);
            deny.setBackground(Ui.roundStroke(Ui.dp(ctx, 999), Ui.SURFACE, Ui.dp(ctx, 1.2f), 0xFFFCA5A5));
            deny.setOnClickListener(v -> host.onApprove(it, "rejected"));
            deny.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            actions.addView(deny);

            card.addView(actions);
        }
        wrap.addView(card);
        return wrap;
    }

    // ------------------------------------------------------------ 提问卡

    private View questionCard(ChatItem it) {
        LinearLayout wrap = Ui.col(ctx);
        wrap.setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 6));

        LinearLayout card = Ui.col(ctx);
        card.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12));
        card.setBackground(Ui.roundStroke(Ui.dp(ctx, 16), Ui.SURFACE, Ui.dp(ctx, 1.2f), 0xFFBFD2FF));
        card.setLayoutParams(Ui.fill());

        card.addView(Ui.text(ctx, "Agent 在等你的回答", 14.5f, Ui.INK, true));

        final JSONArray qs = it.questions == null ? new JSONArray() : it.questions;
        List<String> ids = new ArrayList<>();
        List<EditText> customs = new ArrayList<>();
        List<LinearLayout> optionHosts = new ArrayList<>();

        for (int i = 0; i < qs.length(); i++) {
            JSONObject q = qs.optJSONObject(i);
            if (q == null) continue;
            String qid = q.optString("id", "q" + i);
            ids.add(qid);

            String header = q.optString("header", "");
            if (!header.isEmpty()) {
                TextView h = Ui.text(ctx, header, 12.5f, Ui.INK_FAINT, false);
                h.setPadding(0, Ui.dp(ctx, 8), 0, 0);
                card.addView(h);
            }
            TextView qt = Ui.text(ctx, q.optString("question", ""), 14.5f, Ui.INK, true);
            qt.setPadding(0, Ui.dp(ctx, 4), 0, 0);
            card.addView(qt);

            String detail = q.optString("detail", "");
            if (!detail.isEmpty()) {
                TextView d = Ui.text(ctx, detail, 12.5f, Ui.INK_SUB, false);
                d.setPadding(0, Ui.dp(ctx, 2), 0, 0);
                card.addView(d);
            }

            boolean multi = q.optBoolean("multiSelect", false);
            LinearLayout options = Ui.col(ctx);
            options.setLayoutParams(Ui.fill());
            options.setPadding(0, Ui.dp(ctx, 6), 0, 0);
            optionHosts.add(options);

            JSONArray opts = q.optJSONArray("options");
            if (opts != null) {
                LinkedHashSet<String> selected = it.picked.get(qid);
                if (selected == null) { selected = new LinkedHashSet<>(); it.picked.put(qid, selected); }
                for (int j = 0; j < opts.length(); j++) {
                    JSONObject o = opts.optJSONObject(j);
                    if (o == null) continue;
                    String label = o.optString("label", "");
                    String desc = o.optString("description", "");
                    options.addView(optionRow(it, qid, label, desc, multi, options));
                }
            }
            card.addView(options);

            EditText custom = new EditText(ctx);
            custom.setHint(multi ? "也可补充输入…" : "或直接输入回答…");
            custom.setTextSize(14f);
            custom.setHintTextColor(Ui.INK_FAINT);
            custom.setTextColor(Ui.INK);
            custom.setBackground(Ui.roundStroke(Ui.dp(ctx, 12), 0xFFF7F8FC, Ui.dp(ctx, 0.8f), Ui.LINE));
            custom.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 9), Ui.dp(ctx, 12), Ui.dp(ctx, 9));
            custom.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            custom.setMinLines(1);
            custom.setMaxLines(4);
            String t = it.typed.get(qid);
            if (t != null) custom.setText(t);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.topMargin = Ui.dp(ctx, 6);
            custom.setLayoutParams(clp);
            customs.add(custom);
            card.addView(custom);
        }

        if (it.sendError != null && !it.sendError.isEmpty()) {
            TextView warn = Ui.text(ctx, "⚠ " + it.sendError, 12.5f, Ui.ERR, false);
            warn.setPadding(0, Ui.dp(ctx, 8), 0, 0);
            card.addView(warn);
        }

        if (it.resolved) {
            String label;
            if ("answered".equals(it.resolvedOutcome)) label = "✓ 已回答";
            else label = "已取消 / 已由其他端处理";
            TextView done = Ui.text(ctx, label, 13f, Ui.INK_SUB, true);
            done.setPadding(0, Ui.dp(ctx, 10), 0, 0);
            card.addView(done);
        } else if (it.pendingConfirm) {
            // 与审批卡一致：回执没到就不显示 ✓、也不显示按钮（评审 P0-3）。
            TextView waiting = Ui.text(ctx, "已发送，等待电脑确认…", 13f, Ui.INK_SUB, true);
            waiting.setPadding(0, Ui.dp(ctx, 10), 0, 0);
            card.addView(waiting);
        } else {
            LinearLayout actions = Ui.row(ctx);
            actions.setLayoutParams(Ui.fill());
            actions.setPadding(0, Ui.dp(ctx, 11), 0, 0);

            TextView submit = actionButton("提交", Ui.BRAND, 0xFFFFFFFF);
            submit.setOnClickListener(v -> {
                // 网关会拒绝空答案（"custom answers must be non-empty strings"），先本地校验
                for (int i = 0; i < ids.size(); i++) {
                    String qid = ids.get(i);
                    LinkedHashSet<String> picked0 = it.picked.get(qid);
                    String typed0 = i < customs.size() ? customs.get(i).getText().toString().trim() : "";
                    if ((picked0 == null || picked0.isEmpty()) && typed0.isEmpty()) {
                        android.widget.Toast.makeText(ctx, "请先回答第 " + (i + 1) + " 个问题",
                                android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                JSONArray answers = new JSONArray();
                for (int i = 0; i < ids.size(); i++) {
                    String qid = ids.get(i);
                    LinkedHashSet<String> sel = it.picked.get(qid);
                    String custom = i < customs.size() ? customs.get(i).getText().toString().trim() : "";
                    it.typed.put(qid, custom);
                    JSONObject a = new JSONObject();
                    try {
                        a.put("id", qid);
                        JSONArray s = new JSONArray();
                        boolean multi = false;
                        for (int k = 0; k < qs.length(); k++) {
                            JSONObject q = qs.optJSONObject(k);
                            if (q != null && qid.equals(q.optString("id", "q" + k))) {
                                multi = q.optBoolean("multiSelect", false);
                                break;
                            }
                        }
                        boolean hasSel = sel != null && !sel.isEmpty();
                        if (!multi && !custom.isEmpty()) {
                            a.put("selected", new JSONArray());
                            a.put("custom", custom);
                        } else {
                            if (hasSel) for (String s0 : sel) s.put(s0);
                            a.put("selected", s);
                            if (!custom.isEmpty()) a.put("custom", custom);
                        }
                    } catch (Throwable ignored) { }
                    answers.put(a);
                }
                host.onQuestionSubmit(it, answers);
            });
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            sp.rightMargin = Ui.dp(ctx, 8);
            submit.setLayoutParams(sp);
            actions.addView(submit);

            TextView skip = actionButton("跳过", Ui.SURFACE, Ui.INK_SUB);
            skip.setBackground(Ui.roundStroke(Ui.dp(ctx, 999), Ui.SURFACE, Ui.dp(ctx, 1.2f), Ui.LINE));
            skip.setOnClickListener(v -> host.onQuestionCancel(it));
            skip.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            actions.addView(skip);

            card.addView(actions);
        }
        wrap.addView(card);
        return wrap;
    }

    private View optionRow(ChatItem it, String qid, String label, String desc,
                           boolean multi, LinearLayout parent) {
        LinearLayout row = Ui.row(ctx);
        row.setPadding(Ui.dp(ctx, 11), Ui.dp(ctx, 9), Ui.dp(ctx, 11), Ui.dp(ctx, 9));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(ctx, 5);
        row.setLayoutParams(rlp);

        TextView mark = Ui.text(ctx, "○", 15f, Ui.INK_FAINT, false);
        row.addView(mark);

        LinearLayout texts = Ui.col(ctx);
        texts.setPadding(Ui.dp(ctx, 9), 0, 0, 0);
        texts.addView(Ui.text(ctx, label, 14f, Ui.INK, false));
        if (desc != null && !desc.isEmpty()) {
            texts.addView(Ui.text(ctx, desc, 12f, Ui.INK_SUB, false));
        }
        row.addView(texts);

        LinkedHashSet<String> current = it.picked.get(qid);
        if (current == null) { current = new LinkedHashSet<>(); it.picked.put(qid, current); }
        final LinkedHashSet<String> sel = current;

        final boolean[] refresh = {false};
        Runnable paint = () -> {
            boolean on = sel.contains(label);
            mark.setText(on ? (multi ? "☑" : "◉") : (multi ? "☐" : "○"));
            mark.setTextColor(on ? Ui.BRAND : Ui.INK_FAINT);
            row.setBackground(Ui.roundStroke(Ui.dp(ctx, 12),
                    on ? Ui.BRAND_SOFT : Ui.SURFACE,
                    Ui.dp(ctx, 1.0f), on ? 0xFFC7D3FF : Ui.LINE));
        };
        paint.run();

        row.setOnClickListener(v -> {
            if (multi) {
                if (sel.contains(label)) sel.remove(label); else sel.add(label);
            } else {
                sel.clear();
                sel.add(label);
                int n = parent.getChildCount();
                for (int i = 0; i < n; i++) {
                    View child = parent.getChildAt(i);
                    if (child.getTag(R.id.tag_paint) instanceof Runnable) {
                        ((Runnable) child.getTag(R.id.tag_paint)).run();
                    }
                }
            }
            paint.run();
        });
        row.setTag(R.id.tag_paint, paint);
        return row;
    }

    /** 交付物卡片：文件名 + 说明 + 完整路径（点一下复制路径）。 */
    private View filesCard(ChatItem it) {
        LinearLayout wrap = Ui.col(ctx);
        wrap.setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 6));

        LinearLayout card = Ui.col(ctx);
        card.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12));
        card.setBackground(Ui.roundStroke(Ui.dp(ctx, 16), Ui.SURFACE, Ui.dp(ctx, 1.2f), 0xFFBFE7CC));
        card.setLayoutParams(Ui.fill());
        card.addView(Ui.text(ctx, "交付物", 14.5f, Ui.INK, true));

        JSONArray fs = it.files;
        if (fs == null || fs.length() == 0) {
            card.addView(Ui.text(ctx, "(无文件)", 12.5f, Ui.INK_FAINT, false));
        } else {
            for (int i = 0; i < fs.length(); i++) {
                JSONObject f = fs.optJSONObject(i);
                if (f == null) continue;
                final String path = f.optString("path", "");
                String desc = f.optString("description", "");
                String name = path;
                int cut = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                if (cut >= 0 && cut + 1 < path.length()) name = path.substring(cut + 1);

                LinearLayout row = Ui.col(ctx);
                row.setPadding(0, Ui.dp(ctx, 8), 0, 0);
                row.addView(Ui.text(ctx, name.isEmpty() ? "(未命名)" : name, 13.5f, Ui.BRAND, true));
                if (!desc.isEmpty()) row.addView(Ui.text(ctx, desc, 12f, Ui.INK_SUB, false));
                if (!path.isEmpty()) {
                    String ds = it.downloadState;
                    TextView p = Ui.text(ctx,
                            ds != null && !ds.isEmpty() ? ds : "点此下载到手机 · 长按复制路径",
                            11f,
                            ds != null && ds.startsWith("已下载") ? 0xFF2FB344 : Ui.INK_FAINT,
                            false);
                    row.addView(p);
                    row.setLongClickable(true);
                    row.setOnClickListener(v -> host.onDownloadFile(it, path));
                    row.setOnLongClickListener(v -> { host.onCopyPath(path); return true; });
                }
                card.addView(row);
                if (i < fs.length() - 1) card.addView(Ui.divider(ctx));
            }
        }
        wrap.addView(card);
        return wrap;
    }

    private void copyToClipboard(String text) {
        try {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("path", text));
            android.widget.Toast.makeText(ctx, "路径已复制", android.widget.Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) { }
    }

    private TextView actionButton(String label, int fill, int fg) {
        TextView t = Ui.text(ctx, label, 14f, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(ctx, 11), 0, Ui.dp(ctx, 11));
        t.setBackground(Ui.round(Ui.dp(ctx, 999), fill));
        return t;
    }
}
