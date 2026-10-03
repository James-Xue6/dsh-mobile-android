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
        /**
         * 点「进入对话」/「重连」：在线时直接进对话页；离线时**先重连、连上了才进**，
         * 连不上就留在本页并明确提示（绝不静默进入）。等待期间调 {@link #setConnecting(boolean)}。
         */
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
    /** 大标题「我的设备」（applyTheme 重刷字色；见 applyTheme 注释）。 */
    private TextView titleView;
    /** 副标题「管理你的电脑…」（applyTheme 重刷字色）。 */
    private TextView subTitle;
    /** 设备卡片容器（每次刷新整体重建，卡片数量很少）。 */
    private LinearLayout cards;
    /** 顶部状态行：当前这台到底连上没有。 */
    private TextView status;
    /** 一张设备都没有时的提示（图标 + 一句话，不是光秃秃一行"空"）。 */
    private LinearLayout emptyView;
    /**
     * 用户点了「重连」、App 正在等这台电脑握手成功。
     *
     * 离线时按钮**不能**还叫「进入对话」：那会让人以为"点了就进去"，
     * 而实际能不能进取决于连接是否真的 READY。等待期间按钮显示「重连中…」并置灰，
     * 连接结果由宿主（MainActivity.onOpenDevice 的等待/超时逻辑）决定后才切页。
     */
    private boolean connecting = false;

    public DeviceHubView(Context ctx, Host host) {
        super(ctx);
        this.ctx = ctx;
        this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(Ui.BG);

        // ---- 顶部：iOS 大标题（34sp 粗体）+ 副标题 + 高级入口（齿轮）
        LinearLayout head = Ui.col(ctx);
        this.head = head;
        head.setBackgroundColor(Ui.BG);
        head.setPadding(Ui.dp(ctx, Ui.M_SIDE), Ui.dp(ctx, 10), Ui.dp(ctx, 12), Ui.dp(ctx, 4));

        LinearLayout top = Ui.row(ctx);
        // 大标题留引用：applyTheme() 要重刷字色（旧版漏了 → 切深色后标题仍是黑字压黑底）。
        titleView = Ui.text(ctx, "我的设备", Ui.S_LARGE, Ui.INK, true);
        TextView title = titleView;
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tlp);
        top.addView(title);

        TextView gear = Ui.circleIconButton(ctx, com.dsh.mobile.R.drawable.ic_sliders,
                Ui.CHIP_BG, Ui.INK_SUB);
        gear.setContentDescription("连接设置");
        gear.setOnClickListener(v -> host.onOpenSettings());
        top.addView(gear);
        head.addView(top);

        TextView sub = Ui.text(ctx, "管理你的电脑 · 连接后进入对话", Ui.S_SUB, Ui.INK_SUB, false);
        sub.setPadding(0, Ui.dp(ctx, Ui.G_TITLE_SUB), 0, 0);
        head.addView(sub);
        subTitle = sub;

        status = Ui.text(ctx, "", Ui.S_FOOT, Ui.INK_FAINT, false);
        status.setPadding(0, Ui.dp(ctx, Ui.G_SECTION), 0, 0);
        head.addView(status);
        addView(head, Ui.fill());

        // ---- 设备卡片区（可滚动）
        ScrollView scroll = new ScrollView(ctx);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = Ui.col(ctx);
        this.body = body;
        // iOS 分组列表：左右外边距 16dp（全 App 统一 M_SIDE），上下只留必要的一点点
        body.setPadding(Ui.dp(ctx, Ui.M_SIDE), Ui.dp(ctx, 10), Ui.dp(ctx, Ui.M_SIDE), Ui.dp(ctx, 20));
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

        // ---- 空态：一个安静的大图标 + 一句灰字（旧版是一整段多行说明，像报错）
        LinearLayout empty = Ui.col(ctx);
        emptyView = empty;
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, 0, 0, Ui.dp(ctx, 16));
        empty.setVisibility(GONE);
        // 空态图标：一枚**玻璃方砖**（不是光秃秃一个字形）—— 玻璃层次在空页面上也得有一处落点
        TextView emptyIcon = Ui.iconBox(ctx, com.dsh.mobile.R.drawable.ic_monitor,
                Ui.alpha(Ui.INK_SUB, 0.10f), Ui.alpha(Ui.INK_SUB, 0.85f), 72f, 22f, 32f);
        emptyIcon.setLayoutParams(new LinearLayout.LayoutParams(
                Ui.dp(ctx, 72), Ui.dp(ctx, 72)));
        empty.addView(emptyIcon);
        // Sadees 数字感：空态正文用小号灰字（S_FOOT），说明更小（S_CAP1）——层级靠字号对比
        TextView emptyText = Ui.text(ctx, "还没有添加设备", Ui.S_FOOT, Ui.INK_SUB, false);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        empty.addView(emptyText);
        TextView emptyHint = Ui.text(ctx, "电脑端 DSH 打开「移动设备」面板，扫码即可添加",
                Ui.S_CAP1, Ui.INK_FAINT, false);
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 4), Ui.dp(ctx, 16), 0);
        emptyHint.setLineSpacing(Ui.dp(ctx, 3), 1.15f);
        empty.addView(emptyHint);
        // 空态**吃掉一半剩余高度**（gravity=CENTER）：一个设备都没有时，图标/文案/入口卡
        // 落在页面偏上的视觉重心上，而不是挤在顶端、下半屏空一大片（用户点名的"留白比例不对"）。
        // 有设备时 empty 是 GONE（权重 0），下面的 spacer 拿走全部剩余高度，页脚照旧贴底。
        body.addView(empty, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        body.addView(addCard());

        // ---- 页脚：把"下半屏一大片空白"收住（内容少时靠 weight 把它压到底部）
        View spacer = new View(ctx);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = Ui.dp(ctx, 4);
        spacer.setLayoutParams(slp);
        body.addView(spacer);
        body.addView(Ui.footer(ctx, "DSH 掌上通 · 与电脑端 DSH 直连\n数据只在你的设备之间流转"));
    }

    /**
     * 主题切换：整页按新色板重画。
     * 卡片容器与虚线卡都重建（配色烘在创建时）；调用方随后会 refreshDevices() 重填卡片。
     */
    public void applyTheme() {
        setBackgroundColor(Ui.BG);
        if (head != null) head.setBackgroundColor(Ui.BG);
        // 大标题/副标题/状态行字色必须跟着刷（漏了就是深色黑标题 bug）
        if (titleView != null) titleView.setTextColor(Ui.INK);
        if (subTitle != null) subTitle.setTextColor(Ui.INK_SUB);
        if (status != null) status.setTextColor(Ui.INK_FAINT);
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

    /**
     * 正在等这台设备重连：按钮变「重连中…」并置灰（点了不会重复触发）。
     * 必须在下一次 {@link #setDevices} 之前调用，卡片是重建的，状态在重建时读。
     */
    public void setConnecting(boolean on) {
        this.connecting = on;
    }

    public void setStatus(String s, boolean error) {
        status.setText(s == null ? "" : s);
        status.setTextColor(error ? Ui.WARN : Ui.INK_FAINT);
    }

    // ------------------------------------------------------------ 卡片

    /**
     * 设备卡：**24dp 大圆角渐变卡**（对齐 ref-ios-health-cards.png 的彩色分类卡）。
     *
     * <p>参考图给的三条硬规格这里全落上了：圆角 24dp、**横向**饱和渐变、白字 + 小图标，
     * 并且**无描边、无阴影** —— 渐变卡本身就是画面里的重色块，再描边/投影只会脏。
     * 渐变按设备 id 稳定取（{@link Ui#gradientFor(String)}），同一台设备每次进来颜色一样，
     * 不会"每次刷新换一个色"。
     *
     * <p>卡里所有文字都收进白色系（纯白 / 白 78% / 白 88%）：卡面被染成饱和色之后，
     * 原来的黑字灰字压上去是脏的。唯一例外是主按钮 —— **纯白胶囊 + 固定深字**，
     * 在彩色卡面上它就是"最该被点到"的那个（参考图的 Edit 按钮正是这个做法）。
     */
    private LinearLayout deviceCard(final Store.Device d, boolean isActive, boolean online) {
        final int white = 0xFFFFFFFF;
        final int w78   = Ui.alpha(white, 0.92f);   // 次要文字：0.78 压橙底不足 4.5
        final int w88   = 0xFFFFFFFF;                // 状态/线路行：机检后提到纯白（0.88 压橙底只有 4.1:1）
        // 卡面上的半透明底（图标底 / 标签底 / 当前徽标）：**黑 25%** 而不是白 22% ——
        // 2026-10-03 对比度机检：白 22% 压橙渐变后白字只剩 3.4:1（<4.5）；
        // 黑 25% 压暗卡面后白字 ≥7:1，任何渐变色对都达标。
        final int wOn   = Ui.alpha(0x000000, 0.25f);

        LinearLayout card = Ui.col(ctx);
        card.setBackground(Ui.featureFill(ctx, Ui.gradientFor(d.id == null ? d.displayName() : d.id)));
        // 渐变卡也补 3dp 阴影：用户点名「对话卡片没有阴影」——不透明卡体后 elevation 安全
        card.setElevation(Ui.dp(ctx, 3f));
        card.setPadding(Ui.dp(ctx, Ui.M_CARD_PAD), Ui.dp(ctx, 15), Ui.dp(ctx, Ui.M_CARD_PAD), Ui.dp(ctx, 15));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = Ui.dp(ctx, Ui.M_GAP);          // 分组卡片之间 14dp（12~16 取中）
        card.setLayoutParams(clp);

        // 第一行：图标 + 设备名 + 在线状态
        LinearLayout row1 = Ui.row(ctx);
        row1.setMinimumHeight(Ui.dp(ctx, 44));
        // 图标 ≈ 行高：设备名 15.5sp（行高 ≈21dp），字形 Ui.I_BODY；底 40dp、圆角 12dp
        TextView icon = Ui.iconBox(ctx, com.dsh.mobile.R.drawable.ic_monitor,
                wOn, white, 40f, 12f, Ui.I_BODY);
        row1.addView(icon);

        LinearLayout names = Ui.col(ctx);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = Ui.dp(ctx, 12);
        names.setLayoutParams(nlp);
        TextView name = Ui.text(ctx, d.displayName(), Ui.S_BODY, white, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        names.addView(name);

        TextView plat = Ui.text(ctx, platformLabel(d), Ui.S_FOOT, w78, false);
        plat.setPadding(0, Ui.dp(ctx, 2), 0, 0);
        names.addView(plat);
        row1.addView(names);

        // 在线状态：小圆点 + 白字。**不用绿/灰**：卡面本身是饱和色，绿点和灰字都读不清，
        // 在线/离线的区别交给"点实/点虚 + 文案"承担。
        row1.addView(Ui.dotLabel(ctx, 8f, online ? white : w78,
                online ? "在线" : "离线", Ui.S_FOOT, w88));

        // 当前生效的那台：一枚半透明白胶囊。
        // 旧版是左侧 3dp 蓝条 —— 渐变卡上那条蓝条与卡面撞色，什么也说明不了。
        if (isActive) {
            TextView cur = Ui.text(ctx, "当前", Ui.S_CAP1, white, true);
            cur.setTypeface(Ui.medium());
            cur.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 3), Ui.dp(ctx, 8), Ui.dp(ctx, 3));
            cur.setBackground(Ui.pill(wOn));
            LinearLayout.LayoutParams c2 = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            c2.leftMargin = Ui.dp(ctx, 8);
            cur.setLayoutParams(c2);
            row1.addView(cur);
        }
        card.addView(row1);

        // 第二行：标签（内网·固定 / 公网 / 版本号）—— 统一"白字 + 22% 白底"
        LinearLayout tags = Ui.row(ctx);
        tags.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        // 只要手机真的连得上才算「内网 · 固定」：虚拟网卡（172.16/12）等假内网地址不给这个标签
        if (d.hasUsableLan()) tags.addView(tag("内网 · 固定"));
        if (d.wanUrl != null && !d.wanUrl.isEmpty()) tags.addView(tag("公网"));
        if (d.dshVersion != null && !d.dshVersion.isEmpty()) tags.addView(tag("DSH " + d.dshVersion));
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
        TextView addr = Ui.text(ctx, addrLine, Ui.S_FOOT, w88, false);
        addr.setPadding(0, Ui.dp(ctx, 8), 0, 0);
        card.addView(addr);

        // 第四行：进入/重连 + 修改名称 + 删除
        //
        // 文案必须和"在线/离线"一致，不能骗人：只有真的 READY（online）才写「进入对话」；
        // 离线时写「重连」，点下去由宿主先连、连上了才切页（连接失败会明确提示）。
        LinearLayout btns = Ui.row(ctx);
        btns.setPadding(0, Ui.dp(ctx, 12), 0, 0);

        boolean busy = isActive && connecting && !online;
        TextView enter = Ui.pillButton(ctx, online ? "进入对话" : (busy ? "重连中…" : (isActive ? "重连" : "连接")),
                white, Ui.INK_ON_WHITE);
        if (busy) Ui.setButtonEnabled(enter, false);   // 等待期间置灰，防重复点
        enter.setContentDescription(online ? "进入对话" : "重连");
        enter.setOnClickListener(v -> host.onOpenDevice(d));
        btns.addView(enter, weight(1.35f, 0));

        TextView rename = Ui.pillButton(ctx, "修改名称", wOn, white);
        rename.setOnClickListener(v -> host.onRenameDevice(d));
        btns.addView(rename, weight(1f, 10));

        // 危险操作：卡面上用白字（红字压在橙/紫渐变上等于看不清），权重靠"无底色"压住
        TextView del = Ui.textButton(ctx, "删除", w88);
        del.setOnClickListener(v -> host.onDeleteDevice(d));
        btns.addView(del, weight(0.62f, 2));
        card.addView(btns);
        return card;
    }

    /** 平台/类型标签：优先用网关给的平台名，没有就统一说「桌面端」。 */
    private String platformLabel(Store.Device d) {
        String p = d.platform == null ? "" : d.platform.trim();
        if (!p.isEmpty()) return p + " · 桌面端";
        return "桌面端";
    }

    /** 渐变卡上的小标签：**白字 + 25% 黑底胶囊**（黑底压暗卡面，白字 ≥7:1；白底只有 3.4:1）。 */
    private TextView tag(String s) {
        TextView t = Ui.text(ctx, s, Ui.S_CAP1, 0xFFFFFFFF, false);
        t.setTypeface(Ui.medium());
        t.setPadding(Ui.dp(ctx, 9), Ui.dp(ctx, 4), Ui.dp(ctx, 9), Ui.dp(ctx, 4));
        t.setBackground(Ui.pill(Ui.alpha(0x000000, 0.25f)));
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
     * 「＋ 添加设备」卡片：**一张整体卡片**，一行两列 —— 左图标 / 左对齐的两行文字 / 右细箭头。
     *
     * <p>2026-10-03 返工（用户原话「灰卡里套了个白方框，文字还不对齐」）：旧版把主文字居中、
     * 副文字左对齐，两种对齐混在一张卡里；再加上玻璃卡挂了 elevation，阴影从半透明体下透出来
     * 画出一圈灰环 + 白心，看上去就是个"坏掉的白方框"。现在：
     * <ul>
     *   <li>整卡就是一个 {@link Ui#entryCard} —— 图标 + 主文字「添加设备」+ 次文字 + 细箭头；</li>
     *   <li>没有内嵌白框（那圈"白框"本来就是 elevation 阴影透出来的假象）；</li>
     *   <li>主/次两行文字都左对齐到同一条基线，图标固定 36dp 列宽。</li>
     * </ul>
     */
    private View addCard() {
        LinearLayout card = Ui.entryCardGradient(ctx, com.dsh.mobile.R.drawable.ic_plus,
                Ui.GRAD_BRAND, "添加设备", "扫码或输入设备连接");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(ctx, 4);
        card.setLayoutParams(lp);
        card.setClickable(true);
        card.setContentDescription("添加设备");
        card.setOnClickListener(v -> showAddSheet());
        Ui.tap(card, 0.98f);
        return card;
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

        // 顶部小横条（iOS 抓手："可以往下拖 / 点外面关闭"的视觉暗示）
        box.addView(Ui.grabber(ctx));

        TextView sheetTitle = Ui.text(ctx, "添加设备", Ui.S_TITLE3, Ui.INK, true);
        box.addView(sheetTitle);
        TextView sub = Ui.text(ctx, "选择一种设备连接方式", Ui.S_FOOT, Ui.INK_SUB, false);
        sub.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 10));
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
            // 液态玻璃：底部弹窗背后真模糊（API 31+，不支持则静默回退到"只有遮罩"）
            Ui.applyWindowBlur(w, 24f);
        }
        dlg.show();
    }

    /** 弹窗里的一行选择项：iOS 的做法 —— 卡片色的行、按下整行高亮、右侧 "›" 箭头。 */
    private LinearLayout option(String title, String desc, final Dialog dlg, final boolean scan) {
        LinearLayout row = Ui.row(ctx);
        row.setMinimumHeight(Ui.dp(ctx, 56));
        row.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 11), Ui.dp(ctx, 10), Ui.dp(ctx, 11));
        Ui.tapRow(row, Ui.round(Ui.dp(ctx, 14), Ui.FIELD_BG), Ui.PRESS);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(ctx, 6);
        row.setLayoutParams(lp);

        // 字形 22→20dp（Ui.I_BODY）、底 24→22dp：旁边是 15.5sp 的标题行（行高 ≈ 21dp）。
        TextView glyph = Ui.iconBox(ctx,
                scan ? com.dsh.mobile.R.drawable.ic_qr : com.dsh.mobile.R.drawable.ic_keyboard,
                0x00000000, scan ? Ui.BRAND : Ui.INK_SUB, 22f, 0f, Ui.I_BODY);
        row.addView(glyph);

        LinearLayout texts = Ui.col(ctx);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dp(ctx, 12);
        texts.setLayoutParams(tlp);
        texts.addView(Ui.text(ctx, title, Ui.S_BODY, Ui.INK, false));
        TextView d = Ui.text(ctx, desc, Ui.S_FOOT, Ui.INK_SUB, false);
        d.setPadding(0, Ui.dp(ctx, 3), 0, 0);
        texts.addView(d);
        row.addView(texts);
        row.addView(Ui.chevron(ctx));

        row.setClickable(true);
        row.setOnClickListener(v -> {
            dlg.dismiss();
            if (scan) host.onScanAdd();
            else host.onManualAdd();
        });
        return row;
    }
}
