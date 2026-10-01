package com.dsh.mobile.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 配对与连接设置。 */
public final class SettingsView extends LinearLayout {

    public interface Host {
        void onBack();
        void onScanQr();
        void onConnect(String lan, String wan, boolean useWan, String token, String deviceName);
        void onSwitchEndpoint(String lan, String wan, boolean useWan);
        void onToggleInsecureTls(boolean on);
        void onCheckUpdate();
        void onOpenFeedback();
        void onPastePairing();
        void onDisconnect();
        void onSetDisplayMode(String mode);
    }

    private final Context ctx;
    private final Host host;
    private final EditText lanField;
    private final EditText wanField;
    private TextView useLanBtn;
    private TextView useWanBtn;
    private TextView aboutText;
    private TextView updateHint;
    private boolean useWanState;
    private TextView insecureBtn;
    private boolean insecureState;
    private TextView feedbackCount;
    private final EditText tokenField;
    private final EditText nameField;
    private final TextView status;
    private final TextView diag;
    private TextView modeFull;
    private TextView modeCompact;

    public SettingsView(Context ctx, Host host) {
        super(ctx);
        this.ctx = ctx;
        this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(Ui.BG);

        // 顶部栏
        LinearLayout bar = Ui.row(ctx);
        bar.setBackgroundColor(Ui.SURFACE);
        bar.setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8));
        TextView back = Ui.circleButton(ctx, "‹", 0x00000000, Ui.INK);
        back.setTextSize(26f);
        back.setOnClickListener(v -> host.onBack());
        bar.addView(back);
        bar.addView(Ui.text(ctx, "连接设置", 16.5f, Ui.INK, true));
        addView(bar, Ui.fill());

        ScrollView scroll = new ScrollView(ctx);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout body = Ui.col(ctx);
        body.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 30));
        scroll.addView(body);
        addView(scroll);

        // ---- 连接卡片
        LinearLayout card = section(body, "连接设置", true);
        card.addView(hint("电脑端 DSH 安装并开启「移动设备」网关后，用这里的地址连过来。"));

        card.addView(label("内网地址（同一个 WiFi）"));
        lanField = field("扫码后自动填 · ws://192.168.x.x:3091/ws/mobile", false);
        card.addView(lanField);

        card.addView(label("公网地址（反代之后填这里，可留空）"));
        wanField = field("扫码后自动填 · wss://你的域名/ws/mobile", false);
        card.addView(wanField);

        LinearLayout epRow = Ui.row(ctx);
        epRow.setLayoutParams(Ui.fill());
        epRow.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        useLanBtn = segment("用内网");
        useWanBtn = segment("用公网");
        useLanBtn.setOnClickListener(v -> host.onSwitchEndpoint(
                lanField.getText().toString().trim(), wanField.getText().toString().trim(), false));
        useWanBtn.setOnClickListener(v -> host.onSwitchEndpoint(
                lanField.getText().toString().trim(), wanField.getText().toString().trim(), true));
        epRow.addView(useLanBtn, weight(1f, 0));
        epRow.addView(useWanBtn, weight(1f, 8));
        card.addView(epRow);

        LinearLayout tlsRow = Ui.row(ctx);
        tlsRow.setLayoutParams(Ui.fill());
        tlsRow.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        insecureBtn = segment("允许自签名证书：已关闭");
        insecureBtn.setOnClickListener(v -> host.onToggleInsecureTls(!insecureState));
        tlsRow.addView(insecureBtn, weight(1f, 0));
        card.addView(tlsRow);
        card.addView(hint("自己搭的反向代理（Lucky / 宝塔 / nginx 自签）常带自签名证书，"
                + "Android 默认不信任、会直接报证书错误；打开此项后改用 wss:// 但不校验证书。"));

        card.addView(label("设备令牌"));
        tokenField = field("扫码配对后自动填入", false);
        card.addView(tokenField);

        card.addView(label("设备名称"));
        nameField = field("这台手机的名称", false);
        card.addView(nameField);

        LinearLayout row1 = Ui.row(ctx);
        row1.setPadding(0, Ui.dp(ctx, 14), 0, 0);
        TextView scan = primary("扫码配对");
        scan.setOnClickListener(v -> host.onScanQr());
        row1.addView(scan, weight(1f, 8));

        TextView save = secondary("保存并连接");
        save.setOnClickListener(v -> host.onConnect(
                lanField.getText().toString().trim(),
                wanField.getText().toString().trim(),
                useWanState,
                tokenField.getText().toString().trim(),
                nameField.getText().toString().trim()));
        row1.addView(save, weight(1f, 0));
        card.addView(row1);

        LinearLayout row2 = Ui.row(ctx);
        row2.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        TextView paste = secondary("粘贴配对串");
        paste.setOnClickListener(v -> host.onPastePairing());
        row2.addView(paste, weight(1f, 8));

        TextView clear = secondary("清除配对");
        clear.setOnClickListener(v -> host.onDisconnect());
        row2.addView(clear, weight(1f, 0));
        card.addView(row2);


        // ---- 对话显示模式
        LinearLayout disp = section(body, "对话显示", false);
        disp.addView(hint("「简洁」只显示正在运行什么，不铺开每条命令的细节，类似桌面端。"));
        LinearLayout seg = Ui.row(ctx);
        seg.setLayoutParams(Ui.fill());
        seg.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        modeFull = segment("完整");
        modeCompact = segment("简洁");
        modeFull.setOnClickListener(v -> { host.onSetDisplayMode("full"); paintModes("full"); });
        modeCompact.setOnClickListener(v -> { host.onSetDisplayMode("compact"); paintModes("compact"); });
        seg.addView(modeFull, weight(1f, 0));
        seg.addView(modeCompact, weight(1f, 8));
        disp.addView(seg);


        // ---- 关于（放在诊断之前，免得被又长又吵的日志埋掉）
        LinearLayout about = section(body, "关于", false);
        aboutText = Ui.text(ctx, "", 12.5f, Ui.INK_SUB, false);
        aboutText.setPadding(0, Ui.dp(ctx, 6), 0, 0);
        aboutText.setTextIsSelectable(true);
        about.addView(aboutText);

        // 版本更新：一键检查 + 结果提示（有新版本时启动也会自动弹窗）
        LinearLayout upRow = Ui.row(ctx);
        upRow.setLayoutParams(Ui.fill());
        upRow.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        TextView upBtn = secondary("检查更新");
        upBtn.setOnClickListener(v -> host.onCheckUpdate());
        upRow.addView(upBtn, weight(1f, 0));
        about.addView(upRow);
        updateHint = Ui.text(ctx, "", 12f, Ui.INK_FAINT, false);
        updateHint.setPadding(0, Ui.dp(ctx, 8), 0, 0);
        about.addView(updateHint);


        // ---- 状态卡片
        LinearLayout st = section(body, "当前状态", false);
        status = Ui.text(ctx, "未连接", 13.5f, Ui.INK_SUB, false);
        status.setPadding(0, Ui.dp(ctx, 6), 0, 0);
        st.addView(status);

        diag = Ui.text(ctx, "", 11.5f, Ui.INK_FAINT, false);
        diag.setTypeface(android.graphics.Typeface.MONOSPACE);
        diag.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        diag.setTextIsSelectable(true);
        st.addView(diag);


        // ---- 说明卡片
        LinearLayout help = section(body, "怎么连？", false);
        help.addView(hint("• 同一个 WiFi：电脑端 DSH →「移动设备」→ 生成配对二维码，手机点「扫码配对」扫它即可。"));
        help.addView(hint("• 外网：把电脑上的网关端口用反向代理暴露成 wss:// 域名，填进上面「公网地址」，之后点「用公网」就能随时切换，不用再改内网地址。"));
        help.addView(hint("• 一个地址只用一次配对；换地址后重新扫码。"));
        // ---- 意见反馈（点开是反馈窗口）
        LinearLayout fb = section(body, "意见反馈", false);
        fb.addView(hint("用着哪里别扭、想要什么功能、哪里报错，都写在这里发给我，我会照着改。"
                + "发送时会自动附上版本、连接方式与当前状态，方便定位问题。"));
        LinearLayout fbRow = Ui.row(ctx);
        fbRow.setLayoutParams(Ui.fill());
        fbRow.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        TextView fbBtn = segment("写反馈");
        fbBtn.setOnClickListener(v -> host.onOpenFeedback());
        fbRow.addView(fbBtn, weight(1f, 0));
        fb.addView(fbRow);
        feedbackCount = Ui.text(ctx, "", 12f, Ui.INK_FAINT, false);
        feedbackCount.setPadding(0, Ui.dp(ctx, 8), 0, 0);
        fb.addView(feedbackCount);
    }

    private TextView segment(String label) {
        TextView t = Ui.text(ctx, label, 14.5f, Ui.INK, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(ctx, 11), 0, Ui.dp(ctx, 11));
        return t;
    }

    private void paintModes(String mode) {
        boolean full = !"compact".equals(mode);
        styleSeg(modeFull, full);
        styleSeg(modeCompact, !full);
    }

    private void styleSeg(TextView t, boolean on) {
        if (t == null) return;
        t.setTextColor(on ? 0xFFFFFFFF : Ui.INK);
        t.setTypeface(on ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        t.setBackground(on ? Ui.round(Ui.dp(ctx, 999), Ui.BRAND)
                : Ui.roundStroke(Ui.dp(ctx, 999), Ui.SURFACE, Ui.dp(ctx, 1f), Ui.LINE));
    }

    public void setDisplayMode(String mode) { paintModes(mode); }

    public void setFields(String lan, String wan, String token, String name, boolean useWan) {
        if (lan != null && !lan.isEmpty()) lanField.setText(lan);
        if (wan != null && !wan.isEmpty()) wanField.setText(wan);
        if (token != null && !token.isEmpty()) tokenField.setText(token);
        if (name != null && !name.isEmpty()) nameField.setText(name);
        paintEndpoints(useWan);
    }

    public void setFeedbackHint(String text) {
        if (feedbackCount != null) feedbackCount.setText(text == null ? "" : text);
    }

    public void setInsecureTls(boolean on) {
        insecureState = on;
        if (insecureBtn != null) {
            insecureBtn.setText(on ? "允许自签名证书：已开启" : "允许自签名证书：已关闭");
            styleSeg(insecureBtn, on);
        }
    }

    public void setAbout(String text) {
        if (aboutText != null) aboutText.setText(text == null ? "" : text);
    }

    /** 版本更新检查的结果提示（「已是最新」「有新版本 v0.4」「检查失败」…）。 */
    public void setUpdateHint(String text) {
        if (updateHint != null) updateHint.setText(text == null ? "" : text);
    }

    private void paintEndpoints(boolean useWan) {
        useWanState = useWan;
        styleSeg(useLanBtn, !useWan);
        styleSeg(useWanBtn, useWan);
    }

    /** 外部切换后同步按钮状态。 */
    public void setUseWan(boolean useWan) { paintEndpoints(useWan); }

    public void setDiagnostics(String s) {
        if (diag != null) diag.setText(s == null ? "" : s);
    }

    public void setStatus(String s, boolean error) {
        status.setText(s == null ? "" : s);
        status.setTextColor(error ? Ui.ERR : Ui.INK_SUB);
    }

    // ------------------------------------------------------------ 小工具

    private LinearLayout card() {
        LinearLayout c = Ui.col(ctx);
        c.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 15), Ui.dp(ctx, 16), Ui.dp(ctx, 16));
        c.setBackground(Ui.roundStroke(Ui.dp(ctx, 16), Ui.SURFACE, Ui.dp(ctx, 0.8f), Ui.LINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(ctx, 12);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView sectionTitle(String s) { return Ui.text(ctx, s, 15.5f, Ui.INK, true); }

    /**
     * 可折叠分区：返回内容容器，往里加控件；标题行点击展开/收起。
     * 设置项一多，全部铺开太吵，所以只默认展开第一项。
     */
    private LinearLayout section(LinearLayout parent, String title, boolean expanded) {
        LinearLayout wrap = Ui.col(ctx);
        wrap.setBackground(Ui.roundStroke(Ui.dp(ctx, 16), Ui.SURFACE, Ui.dp(ctx, 0.8f), Ui.LINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(ctx, 12);
        wrap.setLayoutParams(lp);

        LinearLayout head = Ui.row(ctx);
        head.setLayoutParams(Ui.fill());
        head.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 15), Ui.dp(ctx, 16), Ui.dp(ctx, 15));
        head.setClickable(true);
        TextView t = Ui.text(ctx, title, 15.5f, Ui.INK, true);
        head.addView(t, weight(1f, 0));
        final TextView arrow = Ui.text(ctx, expanded ? "\u25BE" : "\u25B8", 14f, Ui.INK_SUB, false);
        head.addView(arrow);

        final android.view.View line = new android.view.View(ctx);
        line.setBackgroundColor(Ui.LINE);
        line.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 0.8f)));
        line.setVisibility(expanded ? android.view.View.VISIBLE : android.view.View.GONE);

        final LinearLayout box = Ui.col(ctx);
        box.setLayoutParams(Ui.fill());
        box.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 12), Ui.dp(ctx, 16), Ui.dp(ctx, 16));
        box.setVisibility(expanded ? android.view.View.VISIBLE : android.view.View.GONE);

        head.setOnClickListener(v -> {
            boolean show = box.getVisibility() != android.view.View.VISIBLE;
            box.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
            line.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
            arrow.setText(show ? "\u25BE" : "\u25B8");
        });

        wrap.addView(head);
        wrap.addView(line);
        wrap.addView(box);
        parent.addView(wrap);
        return box;
    }

    private TextView label(String s) {
        TextView t = Ui.text(ctx, s, 12.5f, Ui.INK_SUB, false);
        t.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 4));
        return t;
    }

    private TextView hint(String s) {
        TextView t = Ui.text(ctx, s, 12.5f, Ui.INK_SUB, false);
        t.setPadding(0, Ui.dp(ctx, 5), 0, 0);
        return t;
    }

    private EditText field(String hintText, boolean multiline) {
        EditText e = new EditText(ctx);
        e.setHint(hintText);
        e.setTextSize(14f);
        e.setHintTextColor(Ui.INK_FAINT);
        e.setTextColor(Ui.INK);
        e.setBackground(Ui.roundStroke(Ui.dp(ctx, 12), 0xFFF7F8FC, Ui.dp(ctx, 0.8f), Ui.LINE));
        e.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 10), Ui.dp(ctx, 12), Ui.dp(ctx, 10));
        e.setInputType(InputType.TYPE_CLASS_TEXT
                | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (!multiline) e.setSingleLine(false);
        return e;
    }

    private TextView primary(String s) {
        TextView t = Ui.text(ctx, s, 14.5f, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12));
        t.setBackground(Ui.round(Ui.dp(ctx, 999), Ui.BRAND));
        return t;
    }

    private TextView secondary(String s) {
        TextView t = Ui.text(ctx, s, 14.5f, Ui.INK, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12));
        t.setBackground(Ui.roundStroke(Ui.dp(ctx, 999), Ui.SURFACE, Ui.dp(ctx, 1f), Ui.LINE));
        return t;
    }

    private LinearLayout.LayoutParams weight(float w, int marginStartDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
        lp.leftMargin = Ui.dp(ctx, marginStartDp);
        return lp;
    }
}
