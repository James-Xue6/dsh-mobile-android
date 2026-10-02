package com.dsh.mobile.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dsh.mobile.Store;
import com.dsh.mobile.net.RoutePolicy;

import java.util.List;

/**
 * 「我的设备」启动页：先在这里看到自己添加过的电脑（在线/离线一目了然），
 * 选一台「连接/进入」才进对话页；下面一张虚线卡片用来添加新设备。
 *
 * 纯手搓 View 树（与项目其余页面一致），不引入任何新依赖。
 * 在线/离线的**唯一判据在 MainActivity/DeviceHubView 的调用方**：
 * 与那台电脑的 WebSocket 是否真的握手成功并处于 READY（见 MainActivity.isOnline()），
 * 不是"配过对就算在线"，也不是写死的。
 */
public final class DeviceHubView extends LinearLayout {

    public interface Host {
        /** 连接并进入这台设备（对话页）。 */
        void onOpenDevice(Store.Device d);
        /** 修改设备显示名。 */
        void onRenameDevice(Store.Device d);
        /** 删除设备（清除它的地址与令牌）。 */
        void onDeleteDevice(Store.Device d);
        /** 扫码添加：复用现有扫码页扫电脑面板的配对二维码。 */
        void onScanAdd();
        /** 手动添加：输入地址 + 令牌。 */
        void onManualAdd();
        /** 高级入口：原来的连接设置页（地址/令牌字段仍然保留）。 */
        void onOpenSettings();
        /**
         * 这台设备「这次该走内网还是公网、依据哪条规则」（规则在
         * {@code net/RoutePolicy.java}，宿主按进程级 WiFi 判定算好）。
         * 卡片只显示「走内网/走公网 + 规则来源」，**不显示地址**（用户要求便于截图分享）。
         */
        RoutePolicy.Pick routeOf(Store.Device d);
    }

    private final Context ctx;
    private final Host host;
    /** 顶部区（标题 + 齿轮 + 副标题 + 状态行）。 */
    private LinearLayout head;
    /** 卡片区的滚动内容容器（主题切换时整块重建）。 */
    private LinearLayout body;
    /** 设备卡片容器（每次刷新整体重建，卡片数量很少）。 */
    private LinearLayout cards;
    /** 顶部状态行：当前这台到底连上没有。 */
    private TextView status;
    /** 一张设备都没有时的提示。 */
    private TextView emptyView;

    public DeviceHubView(Context ctx, Host host) {
        super(ctx);
        this.ctx = ctx;
        this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(Ui.BG);

        // ---- 顶部：标题 + 副标题 + 高级入口（齿轮）
        LinearLayout head = Ui.col(ctx);
        this.head = head;
        head.setBackgroundColor(Ui.BG);
        head.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 18), Ui.dp(ctx, 18), Ui.dp(ctx, 6));

        LinearLayout top = Ui.row(ctx);
        TextView title = Ui.text(ctx, "我的设备", 27f, Ui.INK, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tlp);
        top.addView(title);

        TextView gear = Ui.circleButton(ctx, "⚙", android.graphics.Color.TRANSPARENT, Ui.INK_SUB);
        gear.setTextSize(19f);
        gear.setContentDescription("连接设置");
        gear.setOnClickListener(v -> host.onOpenSettings());
        top.addView(gear);
        head.addView(top);

        TextView sub = Ui.text(ctx, "管理你的电脑 · 连接后进入对话", 13f, Ui.INK_SUB, false);
        sub.setPadding(0, Ui.dp(ctx, 4), 0, 0);
        head.addView(sub);

        status = Ui.text(ctx, "", 12.5f, Ui.INK_FAINT, false);
        status.setPadding(0, Ui.dp(ctx, 6), 0, 0);
        head.addView(status);
        addView(head, Ui.fill());

        // ---- 设备卡片区（可滚动）
        ScrollView scroll = new ScrollView(ctx);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setFillViewport(true);
        LinearLayout body = Ui.col(ctx);
        this.body = body;
        body.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 4), Ui.dp(ctx, 14), Ui.dp(ctx, 28));
        scroll.addView(body);
        addView(scroll);
        buildBody();
    }

    /**
     * 卡片区内容：卡片容器 + 空态 + 虚线「＋ 添加设备」卡。
     * 单独一个方法是为了让主题切换能整块重建（手搓 View 的配色创建时烘死，重建最不容易漏）。
     */
    private void buildBody() {
        body.removeAllViews();
        cards = Ui.col(ctx);
        cards.setLayoutParams(Ui.fill());
        body.addView(cards);

        emptyView = Ui.text(ctx,
                "还没有添加设备\n\n用同一 WiFi 下的电脑，在电脑端 DSH 打开「移动设备」面板，\n"
                        + "点下面的「＋ 添加设备」扫码或手动添加。",
                13.5f, Ui.INK_FAINT, false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, Ui.dp(ctx, 26), 0, Ui.dp(ctx, 10));
        emptyView.setVisibility(GONE);
        body.addView(emptyView);

        body.addView(addCard());
    }

    /**
     * 主题切换：整页按新色板重画。
     * 卡片容器与虚线卡都重建（配色烘在创建时）；调用方随后会 refreshDevices() 重填卡片。
     */
    public void applyTheme() {
        setBackgroundColor(Ui.BG);
        if (head != null) head.setBackgroundColor(Ui.BG);
        buildBody();
        requestLayout();
    }

    // ------------------------------------------------------------ 刷新

    /**
     * @param list     设备表（可能为空）
     * @param activeId 当前生效的那台设备 id（空 = 没有一台在用）
     * @param online   当前生效的那台**是否真的在线**（WebSocket READY）
     */
    public void setDevices(List<Store.Device> list, String activeId, boolean online) {
        cards.removeAllViews();
        boolean has = list != null && !list.isEmpty();
        emptyView.setVisibility(has ? GONE : VISIBLE);
        if (has) {
            for (Store.Device d : list) {
                boolean isActive = d.id != null && d.id.equals(activeId);
                cards.addView(deviceCard(d, isActive, isActive && online));
            }
        }
    }

    public void setStatus(String s, boolean error) {
        status.setText(s == null ? "" : s);
        status.setTextColor(error ? Ui.WARN : Ui.INK_FAINT);
    }

    // ------------------------------------------------------------ 卡片

    private LinearLayout deviceCard(final Store.Device d, boolean isActive, boolean online) {
        LinearLayout card = Ui.col(ctx);
        card.setPadding(Ui.dp(ctx, 15), Ui.dp(ctx, 14), Ui.dp(ctx, 15), Ui.dp(ctx, 14));
        card.setBackground(Ui.roundStroke(Ui.dp(ctx, 16),
                isActive ? Ui.BRAND_SOFT : Ui.SURFACE,
                Ui.dp(ctx, isActive ? 1.4f : 0.8f), isActive ? Ui.BRAND : Ui.LINE));
        card.setElevation(Ui.dp(ctx, isActive ? 1.5f : 0.5f));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = Ui.dp(ctx, 12);
        card.setLayoutParams(clp);

        // 第一行：图标 + 设备名 + 在线/离线徽标
        LinearLayout row1 = Ui.row(ctx);
        TextView icon = Ui.text(ctx, "🖥", 22f, Ui.BRAND, false);
        row1.addView(icon);

        LinearLayout names = Ui.col(ctx);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = Ui.dp(ctx, 12);
        names.setLayoutParams(nlp);
        TextView name = Ui.text(ctx, d.displayName(), 17f, Ui.INK, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        names.addView(name);

        TextView plat = Ui.text(ctx, platformLabel(d), 12.5f, Ui.INK_SUB, false);
        plat.setPadding(0, Ui.dp(ctx, 3), 0, 0);
        names.addView(plat);
        row1.addView(names);

        TextView badge = Ui.text(ctx, online ? "在线" : "离线", 11.5f,
                online ? Ui.OK : Ui.INK_FAINT, true);
        badge.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 4), Ui.dp(ctx, 10), Ui.dp(ctx, 4));
        badge.setBackground(Ui.pill(online ? Ui.BADGE_OK_BG : Ui.BADGE_OFF_BG));
        row1.addView(badge);
        card.addView(row1);

        // 第二行：标签（内网·固定 / 公网 / 桌面端 / 版本号）
        LinearLayout tags = Ui.row(ctx);
        tags.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        // 只要手机真的连得上才算「内网 · 固定」：虚拟网卡（172.16/12）等假内网地址不给这个标签
        if (d.hasUsableLan()) tags.addView(tag("内网 · 固定", Ui.BRAND));
        if (d.wanUrl != null && !d.wanUrl.isEmpty()) tags.addView(tag("公网", Ui.WARN));
        if (d.dshVersion != null && !d.dshVersion.isEmpty()) tags.addView(tag("DSH " + d.dshVersion, Ui.INK_SUB));
        card.addView(tags);

        // 第三行：这次走哪条线路 + 规则来源（**不显示地址**，方便用户截图分享）
        RoutePolicy.Pick route = host.routeOf(d);
        String url = route == null ? "" : route.url;
        String addrLine;
        if (url.isEmpty()) {
            addrLine = "还没有可用地址 · 重新扫码或手动添加";
        } else {
            String kind = route.line + " · " + route.source;
            addrLine = (online ? "已连接 · " : "离线 · ") + kind;
            if (!online && isActive && d.lastSeenAt > 0) {
                addrLine += " · 上次在线 " + Ui.ago(d.lastSeenAt);
            }
        }
        TextView addr = Ui.text(ctx, addrLine, 12.5f, online ? Ui.OK : Ui.INK_SUB, false);
        addr.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        card.addView(addr);

        // 第四行：连接/进入 + 修改名称 + 删除
        LinearLayout btns = Ui.row(ctx);
        btns.setPadding(0, Ui.dp(ctx, 12), 0, 0);

        TextView enter = solid(online ? "进入对话" : "连接");
        enter.setOnClickListener(v -> host.onOpenDevice(d));
        btns.addView(enter, weight(1f, 0));

        TextView rename = outline("修改名称");
        rename.setOnClickListener(v -> host.onRenameDevice(d));
        btns.addView(rename, weight(1f, 8));

        TextView del = outline("删除");
        del.setTextColor(Ui.ERR);
        del.setOnClickListener(v -> host.onDeleteDevice(d));
        btns.addView(del, weight(1f, 8));
        card.addView(btns);
        return card;
    }

    /** 平台/类型标签：优先用网关给的平台名，没有就统一说「桌面端」。 */
    private String platformLabel(Store.Device d) {
        String p = d.platform == null ? "" : d.platform.trim();
        if (!p.isEmpty()) return p + " · 桌面端";
        return "桌面端";
    }

    private TextView tag(String s, int color) {
        TextView t = Ui.text(ctx, s, 11.5f, color, false);
        t.setPadding(Ui.dp(ctx, 9), Ui.dp(ctx, 3), Ui.dp(ctx, 9), Ui.dp(ctx, 3));
        t.setBackground(Ui.pill(Ui.alpha(color, 0.10f)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(ctx, 6);
        t.setLayoutParams(lp);
        return t;
    }

    private static String hostOf(String url) {
        if (url == null) return "";
        String u = url.replace("ws://", "").replace("wss://", "")
                .replace("http://", "").replace("https://", "");
        int i = u.indexOf('/');
        return i > 0 ? u.substring(0, i) : u;
    }

    /**
     * 虚线「＋ 添加设备」卡片。虚线用 GradientDrawable.setStroke(宽, 色, 虚线段长, 间隔)，
     * 不引入任何 drawable 资源文件。
     */
    private View addCard() {
        LinearLayout card = Ui.col(ctx);
        card.setGravity(Gravity.CENTER);
        card.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 22), Ui.dp(ctx, 18), Ui.dp(ctx, 22));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(Ui.dp(ctx, 16));
        bg.setColor(android.graphics.Color.TRANSPARENT);
        bg.setStroke(Math.max(1, Ui.dp(ctx, 1f)), Ui.BRAND, Ui.dp(ctx, 6), Ui.dp(ctx, 5));
        card.setBackground(bg);

        TextView t = Ui.text(ctx, "＋ 添加设备", 16f, Ui.BRAND, true);
        t.setContentDescription("添加设备");
        card.addView(t);

        TextView s = Ui.text(ctx, "扫码或输入设备连接", 12.5f, Ui.INK_SUB, false);
        s.setPadding(0, Ui.dp(ctx, 5), 0, 0);
        card.addView(s);

        card.setClickable(true);
        card.setOnClickListener(v -> showAddSheet());
        return card;
    }

    /** 主按钮：与设置页同一套品牌色实心圆角（样式在 Ui.primaryButton）。 */
    private TextView solid(String s) {
        return Ui.primaryButton(ctx, s);
    }

    /** 次按钮：与设置页同一套浅色描边圆角（样式在 Ui.secondaryButton）。 */
    private TextView outline(String s) {
        return Ui.secondaryButton(ctx, s);
    }

    private LinearLayout.LayoutParams weight(float w, int marginStartDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
        lp.leftMargin = Ui.dp(ctx, marginStartDp);
        return lp;
    }

    // ------------------------------------------------------------ 底部弹窗：选择添加方式

    /**
     * 底部弹窗（手搓 Dialog 贴底 + 上圆角，不用 Material BottomSheet，避免新依赖）：
     * 「添加设备 · 选择一种设备连接方式」+ 扫码添加 / 手动添加。
     */
    private void showAddSheet() {
        // 上圆角白卡片：与「手动添加设备」表单、设置页卡片同一套圆角与内边距
        LinearLayout box = Ui.sheetCard(ctx);

        // 顶部小横条（"可以往下拖/点外面关闭"的视觉暗示）
        View bar = new View(ctx);
        bar.setBackground(Ui.pill(Ui.LINE));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(ctx, 40), Ui.dp(ctx, 4));
        blp.gravity = Gravity.CENTER_HORIZONTAL;
        blp.bottomMargin = Ui.dp(ctx, 10);
        bar.setLayoutParams(blp);
        box.addView(bar);

        box.addView(Ui.text(ctx, "添加设备", 17f, Ui.INK, true));
        TextView sub = Ui.text(ctx, "选择一种设备连接方式", 12.5f, Ui.INK_SUB, false);
        sub.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 8));
        box.addView(sub);

        final Dialog dlg = new Dialog(ctx);
        box.addView(option("扫码添加", "扫描电脑端「移动设备」面板生成的局域网二维码", dlg, true));
        box.addView(option("手动添加", "输入局域网地址和设备令牌", dlg, false));

        TextView cancel = Ui.secondaryButton(ctx, "取消");
        cancel.setOnClickListener(v -> dlg.dismiss());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(ctx, 10);
        cancel.setLayoutParams(clp);
        box.addView(cancel);

        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(box);
        dlg.setCanceledOnTouchOutside(true);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            // 弹窗里会出现设备令牌等敏感输入：按当前的「允许截屏」策略决定要不要设 FLAG_SECURE
            // （默认允许截屏 → 不设；用户关掉开关 → 这个弹窗的截图同样变黑）
            Ui.applyScreenshotPolicy(w);
            w.setDimAmount(0.35f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dlg.show();
    }

    private LinearLayout option(String title, String desc, final Dialog dlg, final boolean scan) {
        LinearLayout row = Ui.row(ctx);
        row.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 13), Ui.dp(ctx, 14), Ui.dp(ctx, 13));
        row.setBackground(Ui.roundStroke(Ui.dp(ctx, 14),
                scan ? Ui.BRAND_SOFT : Ui.FIELD_BG, Ui.dp(ctx, 0.8f), scan ? Ui.BRAND : Ui.LINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(ctx, 8);
        row.setLayoutParams(lp);

        TextView glyph = Ui.text(ctx, scan ? "⛶" : "⌨", 19f, scan ? Ui.BRAND : Ui.INK_SUB, false);
        row.addView(glyph);

        LinearLayout texts = Ui.col(ctx);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dp(ctx, 12);
        texts.setLayoutParams(tlp);
        texts.addView(Ui.text(ctx, title, 15f, Ui.INK, true));
        TextView d = Ui.text(ctx, desc, 12f, Ui.INK_SUB, false);
        d.setPadding(0, Ui.dp(ctx, 3), 0, 0);
        texts.addView(d);
        row.addView(texts);

        row.setClickable(true);
        row.setOnClickListener(v -> {
            dlg.dismiss();
            if (scan) host.onScanAdd();
            else host.onManualAdd();
        });
        return row;
    }
}
