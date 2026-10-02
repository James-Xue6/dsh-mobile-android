package com.dsh.mobile.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dsh.mobile.net.RoutePolicy;

/** 配对与连接设置。 */
public final class SettingsView extends LinearLayout {

    public interface Host {
        void onBack();
        void onScanQr();
        /** netMode: {@link RoutePolicy#AUTO} / {@link RoutePolicy#LAN} / {@link RoutePolicy#WAN}。 */
        void onConnect(String lan, String wan, String netMode, String token, String deviceName);
        void onSwitchEndpoint(String lan, String wan, String netMode);
        void onToggleInsecureTls(boolean on);
        void onCheckUpdate();
        void onOpenFeedback();
        void onPastePairing();
        void onDisconnect();
        void onSetDisplayMode(String mode);
        /** 「允许截屏」开关（默认开）。关掉后本 App 内截图/录屏会变黑。 */
        void onToggleAllowScreenshot(boolean on);
        /** 主题三选一：system（跟随系统，默认） / light（浅色） / dark（深色）。 */
        void onSetThemeMode(String mode);
    }

    private final Context ctx;
    private final Host host;
    private EditText lanField;
    private EditText wanField;
    /** 连接方式三分段：自动（推荐）/ 只用内网 / 只用公网。 */
    private TextView modeAutoBtn;
    private TextView modeLanBtn;
    private TextView modeWanBtn;
    private String netModeState = RoutePolicy.AUTO;
    private TextView aboutText;
    private TextView updateHint;
    /** 「允许自签名证书」的 iOS 开关（自绘，见 Ui.Switch）。 */
    private Ui.Switch insecureSwitch;
    private boolean insecureState;
    private TextView feedbackCount;
    private EditText tokenField;
    private EditText nameField;
    private TextView status;
    private TextView diag;
    private TextView modeFull;
    private TextView modeCompact;
    /** 「允许截屏」的 iOS 开关（与「允许自签名证书」同一套开关样式）。 */
    private Ui.Switch shotSwitch;
    private boolean shotState = true;
    /** 主题三分段：跟随系统 / 浅色 / 深色。 */
    private TextView themeSystem;
    private TextView themeLight;
    private TextView themeDark;
    private String themeMode = Theme.MODE_SYSTEM;
    /**
     * 分区的展开状态。主题切换会整棵树重建（配色是创建时烘进去的），
     * 重建时靠这张表把用户已经展开/收起的区块原样还原，不至于"一切主题全折叠"。
     */
    private final java.util.Map<String, Boolean> sectionOpen = new java.util.HashMap<>();
    /** 页面滚动容器：重建后要按原位还原滚动位置（否则一切主题就跳回顶部）。 */
    private ScrollView scrollHost;

    public SettingsView(Context ctx, Host host) {
        super(ctx);
        this.ctx = ctx;
        this.host = host;
        build();
    }

    /**
     * 按**当前**主题把整棵设置页建出来。
     *
     * 手搓 View 的配色是创建时烘进每个控件的，主题一变只有重建才不会有漏网之鱼；
     * 重建前由 {@link #applyTheme()} 负责保住用户草稿与分区展开状态。
     */
    private void build() {
        removeAllViews();
        setOrientation(VERTICAL);
        setBackgroundColor(Ui.BG);

        // 顶部栏：iOS 导航栏（44dp 高、细箭头返回、标题 17sp 粗体、底部一条发丝线）
        LinearLayout bar = Ui.row(ctx);
        bar.setBackgroundColor(Ui.SURFACE);
        bar.setMinimumHeight(Ui.dp(ctx, 44));
        bar.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 12), Ui.dp(ctx, 6));
        TextView back = Ui.circleButton(ctx, "‹", android.graphics.Color.TRANSPARENT, Ui.BRAND);
        back.setTextSize(28f);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> host.onBack());
        bar.addView(back);
        TextView barTitle = Ui.text(ctx, "连接设置", Ui.S_HEAD, Ui.INK, true);
        barTitle.setPadding(Ui.dp(ctx, 4), 0, 0, 0);
        bar.addView(barTitle);
        addView(bar, Ui.fill());
        View barLine = new View(ctx);
        barLine.setBackgroundColor(Ui.SEP);
        barLine.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(ctx, 0.5f))));
        // 必须用上面那条 1px 的 LayoutParams：Ui.fill() 是 WRAP_CONTENT，而普通 View 在
        // AT_MOST 下会直接吃满剩余高度，这条发丝线就会撑掉整个设置页（整页只剩一块底色，
        // 深色档看起来就是用户报的「一块黑」）。见 F01-settings-fixed 真机证据。
        addView(barLine);

        ScrollView scroll = new ScrollView(ctx);
        scrollHost = scroll;
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = Ui.col(ctx);
        // iOS 分组列表：左右外边距 16dp，卡片之间 24dp 留白
        body.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 12), Ui.dp(ctx, 16), Ui.dp(ctx, 32));
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

        card.addView(label("连接方式"));
        LinearLayout epRow = Ui.segmentTrack(ctx);
        LinearLayout.LayoutParams epLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        epLp.topMargin = Ui.dp(ctx, 10);
        epRow.setLayoutParams(epLp);
        modeAutoBtn = segment("自动（推荐）");
        modeLanBtn = segment("只用内网");
        modeWanBtn = segment("只用公网");
        modeAutoBtn.setOnClickListener(v -> host.onSwitchEndpoint(
                lanField.getText().toString().trim(), wanField.getText().toString().trim(), RoutePolicy.AUTO));
        modeLanBtn.setOnClickListener(v -> host.onSwitchEndpoint(
                lanField.getText().toString().trim(), wanField.getText().toString().trim(), RoutePolicy.LAN));
        modeWanBtn.setOnClickListener(v -> host.onSwitchEndpoint(
                lanField.getText().toString().trim(), wanField.getText().toString().trim(), RoutePolicy.WAN));
        epRow.addView(modeAutoBtn, weight(1f, 0));
        epRow.addView(modeLanBtn, weight(1f, 8));
        epRow.addView(modeWanBtn, weight(1f, 8));
        card.addView(epRow);
        card.addView(hint("自动（推荐）：连着 WiFi 就走上面那条内网地址，用移动数据（5G/4G）"
                + "或没连 WiFi 就走公网地址；连的 WiFi 不是家里那个、内网几秒没连上，"
                + "会自动改用公网。\n只用内网 / 只用公网：完全按你的选择走，不再自动判断（手动永远优先）。"));

        // iOS 开关行：左标题 + 右自绘开关（绿/灰），整行可点
        LinearLayout tlsRow = Ui.row(ctx);
        tlsRow.setMinimumHeight(Ui.dp(ctx, 44));
        tlsRow.setPadding(0, Ui.dp(ctx, 8), 0, 0);
        TextView tlsLabel = Ui.text(ctx, "允许自签名证书", Ui.S_BODY, Ui.INK, false);
        tlsLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tlsRow.addView(tlsLabel);
        insecureSwitch = new Ui.Switch(ctx);
        tlsRow.addView(insecureSwitch);
        tlsRow.setClickable(true);
        tlsRow.setOnClickListener(v -> host.onToggleInsecureTls(!insecureState));
        insecureSwitch.setOnChange(on -> host.onToggleInsecureTls(on));
        card.addView(tlsRow);
        card.addView(hint("自己搭的反向代理（Lucky / 宝塔 / nginx 自签）常带自签名证书，"
                + "Android 默认不信任、会直接报证书错误；打开此项后改用 wss:// 但不校验证书。"));

        card.addView(label("设备令牌"));
        tokenField = field("扫码配对后自动填入", false);
        // 令牌是长期凭证：明文渲染等于把它摆在任何一张截屏里。
        // 这里始终掩码（本页没有「显示」开关，就不做明暗切换）。
        tokenField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
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
                netModeState,
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
        disp.addView(hint("「简洁」= 和桌面端一致的回合摘要：每个回合只留一条人能读懂的过程行"
                + "（例如「执行了命令 · pwsh」「已读取文件，执行了命令」），不铺开工具参数/输出，"
                + "也不显示思考过程；「完整」保留全部细节。"));
        LinearLayout seg = Ui.segmentTrack(ctx);
        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        segLp.topMargin = Ui.dp(ctx, 10);
        seg.setLayoutParams(segLp);
        modeFull = segment("完整");
        modeCompact = segment("简洁");
        modeFull.setOnClickListener(v -> { host.onSetDisplayMode("full"); paintModes("full"); });
        modeCompact.setOnClickListener(v -> { host.onSetDisplayMode("compact"); paintModes("compact"); });
        seg.addView(modeFull, weight(1f, 0));
        seg.addView(modeCompact, weight(1f, 8));
        disp.addView(seg);


        // ---- 主题（浅色 / 深色 / 跟随系统）
        LinearLayout themeSec = section(body, "主题", false);
        themeSec.addView(hint("「跟随系统」= 跟着手机的深色模式走：手机开深色，App 自己也变深色"
                + "（系统切换时不用重启 App，这里会立刻跟着变）。"
                + "「浅色」「深色」则锁死这一档，不受手机设置影响。"));
        LinearLayout themeSeg = Ui.segmentTrack(ctx);
        LinearLayout.LayoutParams themeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        themeLp.topMargin = Ui.dp(ctx, 10);
        themeSeg.setLayoutParams(themeLp);
        themeSystem = segment("跟随系统");
        themeLight = segment("浅色");
        themeDark = segment("深色");
        themeSystem.setOnClickListener(v -> {
            host.onSetThemeMode(Theme.MODE_SYSTEM);
            setThemeMode(Theme.MODE_SYSTEM);
        });
        themeLight.setOnClickListener(v -> {
            host.onSetThemeMode(Theme.MODE_LIGHT);
            setThemeMode(Theme.MODE_LIGHT);
        });
        themeDark.setOnClickListener(v -> {
            host.onSetThemeMode(Theme.MODE_DARK);
            setThemeMode(Theme.MODE_DARK);
        });
        themeSeg.addView(themeSystem, weight(1f, 0));
        themeSeg.addView(themeLight, weight(1f, 6));
        themeSeg.addView(themeDark, weight(1f, 6));
        themeSec.addView(themeSeg);
        paintTheme();


        // ---- 隐私（高级项，默认折叠；与「允许自签名证书」同一套开关样式）
        LinearLayout privacy = section(body, "隐私", false);
        privacy.addView(hint("关掉「允许截屏」后，本 App 内的截图/录屏会变成黑屏，"
                + "系统「最近任务」里的缩略图同样会变黑；打开（默认）则一切正常。"
                + "无论开关如何，设备令牌都只显示末 4 位。"));
        // iOS 开关行：与「允许自签名证书」完全同款
        LinearLayout shotRow = Ui.row(ctx);
        shotRow.setMinimumHeight(Ui.dp(ctx, 44));
        shotRow.setPadding(0, Ui.dp(ctx, 8), 0, 0);
        TextView shotLabel = Ui.text(ctx, "允许截屏", Ui.S_BODY, Ui.INK, false);
        shotLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        shotRow.addView(shotLabel);
        shotSwitch = new Ui.Switch(ctx);
        shotRow.addView(shotSwitch);
        shotRow.setClickable(true);
        shotRow.setOnClickListener(v -> host.onToggleAllowScreenshot(!shotState));
        shotSwitch.setOnChange(on -> host.onToggleAllowScreenshot(on));
        privacy.addView(shotRow);


        // ---- 通知（任务完成 / 需要处理 / 进行中 的提醒开关与分级）
        //
        // 这一组的控件与逻辑全部在 com.dsh.mobile.notify.NotifySettingsCard 里，这里只挂进来：
        // 设置页正在被 iOS 风格 UI 重做覆盖，把通知那部分收进独立文件可以把冲突面压到这一行。
        // 它自己 new 一个 Store 读写四个开关（默认值即默认档位），不经过 Host 回调 ——
        // 于是设置页的 Host 接口完全不用动。
        LinearLayout notifySec = section(body, "通知", false);
        com.dsh.mobile.notify.NotifySettingsCard.attach(notifySec, ctx, new com.dsh.mobile.Store(ctx));


        // ---- 关于（放在诊断之前，免得被又长又吵的日志埋掉）
        LinearLayout about = section(body, "关于", false);
        aboutText = Ui.text(ctx, "", Ui.S_FOOT, Ui.INK_SUB, false);
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
        updateHint = Ui.text(ctx, "", Ui.S_CAP1, Ui.INK_FAINT, false);
        updateHint.setPadding(0, Ui.dp(ctx, 8), 0, 0);
        about.addView(updateHint);


        // ---- 状态卡片
        LinearLayout st = section(body, "当前状态", false);
        status = Ui.text(ctx, "未连接", Ui.S_SUB, Ui.INK_SUB, false);
        status.setPadding(0, Ui.dp(ctx, 6), 0, 0);
        st.addView(status);

        diag = Ui.text(ctx, "", Ui.S_CAP2, Ui.INK_FAINT, false);
        diag.setTypeface(android.graphics.Typeface.MONOSPACE);
        diag.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        diag.setTextIsSelectable(true);
        st.addView(diag);


        // ---- 说明卡片
        LinearLayout help = section(body, "怎么连？", false);
        help.addView(hint("• 同一个 WiFi：电脑端 DSH →「移动设备」→ 生成配对二维码，手机点「扫码配对」扫它即可。"));
        help.addView(hint("• 外网：把电脑上的网关端口用反向代理暴露成 wss:// 域名，填进上面「公网地址」。"
                + "之后「连接方式」选「自动（推荐）」就不用管了：在家走内网、在外面走公网。"));
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
        feedbackCount = Ui.text(ctx, "", Ui.S_CAP1, Ui.INK_FAINT, false);
        feedbackCount.setPadding(0, Ui.dp(ctx, 8), 0, 0);
        fb.addView(feedbackCount);
    }

    /** 分段控件的一项（iOS 白滑块/灰槽的制式由 Ui.segmentItem 统一）。 */
    private TextView segment(String label) {
        return Ui.segmentItem(ctx, label);
    }

    private void paintModes(String mode) {
        boolean full = !"compact".equals(mode);
        styleSeg(modeFull, full);
        styleSeg(modeCompact, !full);
    }

    /** 选中态 = 白滑块 + 深字（iOS 分段控件），未选中 = 透明 + 灰字。 */
    private void styleSeg(TextView t, boolean on) {
        Ui.paintSegment(t, on);
    }

    public void setDisplayMode(String mode) { paintModes(mode); }

    // ------------------------------------------------------------ 主题

    private void paintTheme() {
        styleSeg(themeSystem, Theme.MODE_SYSTEM.equals(themeMode));
        styleSeg(themeLight, Theme.MODE_LIGHT.equals(themeMode));
        styleSeg(themeDark, Theme.MODE_DARK.equals(themeMode));
    }

    /** 外部（宿主）同步当前主题档位。 */
    public void setThemeMode(String mode) {
        themeMode = Theme.normalize(mode);
        paintTheme();
    }

    /**
     * 主题变了：用新色板重建整棵树。
     *
     * 重建会丢两样东西，这里显式保住：
     *   ① 用户正在输入但还没保存的草稿（地址 / 令牌 / 名称）；
     *   ② 各分区的展开 / 收起状态（靠 sectionOpen 表，见 section()）。
     * 其余状态（开关、分段、状态文字）由 build() 之后宿主重放一遍。
     */
    public void applyTheme() {
        String lan = lanField == null ? "" : lanField.getText().toString();
        String wan = wanField == null ? "" : wanField.getText().toString();
        String token = tokenField == null ? "" : tokenField.getText().toString();
        String name = nameField == null ? "" : nameField.getText().toString();
        String about = aboutText == null ? "" : aboutText.getText().toString();
        String update = updateHint == null ? "" : updateHint.getText().toString();
        String st = status == null ? "" : status.getText().toString();
        String diagnostics = diag == null ? "" : diag.getText().toString();
        String fbHint = feedbackCount == null ? "" : feedbackCount.getText().toString();
        final int scrollY = scrollHost == null ? 0 : scrollHost.getScrollY();
        boolean stError = false;   // 状态行的错误态由宿主重放，这里不猜
        build();
        if (!lan.isEmpty()) lanField.setText(lan);
        if (!wan.isEmpty()) wanField.setText(wan);
        if (!token.isEmpty()) tokenField.setText(token);
        if (!name.isEmpty()) nameField.setText(name);
        if (aboutText != null) aboutText.setText(about);
        if (updateHint != null) updateHint.setText(update);
        if (status != null) setStatus(st, stError);
        if (diag != null) diag.setText(diagnostics);
        if (feedbackCount != null) feedbackCount.setText(fbHint);
        // 重放开关/分段：这些值本来就在本对象的字段里，重建后自己画回去
        paintNetMode(netModeState);
        setInsecureTls(insecureState);
        setAllowScreenshot(shotState);
        paintModes(null);   // 显示模式由宿主随后 setDisplayMode() 覆盖
        paintTheme();
    }

    public void setFields(String lan, String wan, String token, String name, String netMode) {
        if (lan != null && !lan.isEmpty()) lanField.setText(lan);
        if (wan != null && !wan.isEmpty()) wanField.setText(wan);
        if (token != null && !token.isEmpty()) tokenField.setText(token);
        if (name != null && !name.isEmpty()) nameField.setText(name);
        paintNetMode(netMode);
    }

    public void setFeedbackHint(String text) {
        if (feedbackCount != null) feedbackCount.setText(text == null ? "" : text);
    }

    public void setInsecureTls(boolean on) {
        insecureState = on;
        if (insecureSwitch != null) insecureSwitch.setChecked(on);
    }

    /** 「允许截屏」开关状态（样式与「允许自签名证书」完全一致，都是 Ui.Switch）。 */
    public void setAllowScreenshot(boolean on) {
        shotState = on;
        if (shotSwitch != null) shotSwitch.setChecked(on);
    }

    public void setAbout(String text) {
        if (aboutText != null) aboutText.setText(text == null ? "" : text);
    }

    /** 版本更新检查的结果提示（「已是最新」「有新版本 v0.4」「检查失败」…）。 */
    public void setUpdateHint(String text) {
        if (updateHint != null) updateHint.setText(text == null ? "" : text);
    }

    /** 连接方式三分段的选中态（与显示模式/主题同一套 styleSeg 样式）。 */
    private void paintNetMode(String mode) {
        netModeState = RoutePolicy.normalizeMode(mode);
        styleSeg(modeAutoBtn, RoutePolicy.AUTO.equals(netModeState));
        styleSeg(modeLanBtn, RoutePolicy.LAN.equals(netModeState));
        styleSeg(modeWanBtn, RoutePolicy.WAN.equals(netModeState));
    }

    /** 外部（宿主）同步当前连接方式档位。 */
    public void setNetMode(String mode) { paintNetMode(mode); }

    public void setDiagnostics(String s) {
        if (diag != null) diag.setText(s == null ? "" : s);
    }

    public void setStatus(String s, boolean error) {
        status.setText(s == null ? "" : s);
        status.setTextColor(error ? Ui.ERR : Ui.INK_SUB);
    }

    // ------------------------------------------------------------ 小工具

    /** iOS 分组卡片：纯卡片色 + 14dp 圆角 + 极细发丝描边（卡片之间 24dp）。 */
    private LinearLayout card() {
        LinearLayout c = Ui.card(ctx);
        c.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 14), Ui.dp(ctx, 16), Ui.dp(ctx, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(ctx, 24);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView sectionTitle(String s) { return Ui.text(ctx, s, Ui.S_HEAD, Ui.INK, true); }

    /**
     * 可折叠分区：返回内容容器，往里加控件；标题行点击展开/收起。
     * 设置项一多，全部铺开太吵，所以只默认展开第一项。
     */
    private LinearLayout section(LinearLayout parent, String title, boolean expanded) {
        // 主题切换会整棵树重建；展开状态记在 sectionOpen 里，重建后原样还原
        Boolean remembered = sectionOpen.get(title);
        final boolean open0 = remembered != null ? remembered : expanded;
        LinearLayout wrap = Ui.card(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(ctx, 24);
        wrap.setLayoutParams(lp);

        LinearLayout head = Ui.row(ctx);
        head.setLayoutParams(Ui.fill());
        head.setMinimumHeight(Ui.dp(ctx, 46));
        head.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 12), Ui.dp(ctx, 16), Ui.dp(ctx, 12));
        head.setClickable(true);
        TextView t = Ui.text(ctx, title, Ui.S_FOOT, Ui.INK_SUB, false);
        t.setLetterSpacing(0.06f);   // 汉字也吃一点字间距 = iOS 组标题的"大写感"
        head.addView(t, weight(1f, 0));
        final TextView arrow = Ui.text(ctx, open0 ? "\u25BE" : "\u25B8", Ui.S_FOOT, Ui.INK_FAINT, false);
        head.addView(arrow);

        final android.view.View line = Ui.divider(ctx);
        line.setVisibility(open0 ? android.view.View.VISIBLE : android.view.View.GONE);

        final LinearLayout box = Ui.col(ctx);
        box.setLayoutParams(Ui.fill());
        box.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 2), Ui.dp(ctx, 16), Ui.dp(ctx, 16));
        box.setVisibility(open0 ? android.view.View.VISIBLE : android.view.View.GONE);

        head.setOnClickListener(v -> {
            boolean show = box.getVisibility() != android.view.View.VISIBLE;
            box.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
            line.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
            arrow.setText(show ? "\u25BE" : "\u25B8");
            sectionOpen.put(title, show);
        });

        wrap.addView(head);
        wrap.addView(line);
        wrap.addView(box);
        parent.addView(wrap);
        return box;
    }

    private TextView label(String s) {
        return Ui.fieldLabel(ctx, s);
    }

    private TextView hint(String s) {
        TextView t = Ui.text(ctx, s, Ui.S_FOOT, Ui.INK_SUB, false);
        t.setPadding(0, Ui.dp(ctx, 6), 0, 0);
        return t;
    }

    /** 统一圆角输入框（样式在 Ui.field，手动添加表单共用同一套）。 */
    private EditText field(String hintText, boolean multiline) {
        EditText e = Ui.field(ctx, hintText);
        e.setInputType(InputType.TYPE_CLASS_TEXT
                | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (!multiline) e.setSingleLine(false);
        return e;
    }

    private TextView primary(String s) {
        return Ui.primaryButton(ctx, s);
    }

    private TextView secondary(String s) {
        return Ui.secondaryButton(ctx, s);
    }

    private LinearLayout.LayoutParams weight(float w, int marginStartDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
        lp.leftMargin = Ui.dp(ctx, marginStartDp);
        return lp;
    }
}
