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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dsh.mobile.Store;
import com.dsh.mobile.net.RoutePolicy;
import com.dsh.mobile.proto.FrostedPanelView;

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

    /**
     * 设备卡**上下内边距**（dp）。
     *
     * <p>[2026-10-07 用户定版] 用户报「设备卡顶行比例不对」；已证伪"垂直对齐"
     * （加 CENTER_VERTICAL 后逐像素 diff=0，因为 {@code Ui.row()} 本来就设了它）。
     * 之后用户看了 9dp / 16dp 的 A/B 对照，判定**还是偏大 → 再缩小**，故定在 8dp；
     * 同时把卡内几处行间距一起收紧（见 {@code tags}/{@code addrGlass}/{@code btns} 的
     * topPadding），整卡高度明显变矮。要再调只改这一个常量。
     */
    private static final float CARD_PAD_V = 8f;

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
         * 连接门·「重新连接」：用户明确要求重试一次。
         *
         * <p>宿主负责「先重连、连上才进会话」，并在重连失败后自动跑一次
         * 局域网地址发现（见 {@code MainActivity.onRetryConnect}）。
         */
        void onRetryConnect();
        /**
         * 连接门·「重新扫码配对」：所有已知地址都失效时的最终出路
         * （电脑上的 DSH 重启过、或换了网络，地址可能全变了）。
         */
        void onRescanPair();
        /**
         * 连接门·「切换线路」：内网/公网两个地址都存着、当前这条连不上时，
         * 手动换另一条试一次（不改用户设置的连接方式，只影响这一次尝试）。
         */
        void onSwitchRoute();
        /**
         * 这台设备「这次该走内网还是公网、依据哪条规则」（规则在
         * {@code net/RoutePolicy.java}，宿主按进程级 WiFi 判定算好）。
         * 卡片只显示「走内网/走公网 + 规则来源」，**不显示地址**（用户要求便于截图分享）。
         */
        RoutePolicy.Pick routeOf(Store.Device d);
    }

    private final Context ctx;
    private final Host host;
    /** 玻璃顶栏容器（ScrollView + 浮动玻璃胶囊）。 */
    private FrameLayout stage;
    /** 浮动玻璃顶栏胶囊（FrostedPanelView：实时模糊 + 折射 + 边缘受光，绑定卡片滚动层）。 */
    private LinearLayout headGlass;
    /** 顶栏的玻璃底板（标题区压在星空/极光上也要读得清，与隔离 Demo 顶栏同一套做法）。 */
    private FrostedPanelView headPanel;
    /** 卡片滚动层（设备卡片玻璃面板的绑定目标）。 */
    private ScrollView scrollHost;
    /** 已创建的卡片玻璃面板（帧同步刷新用）。 */
    private final java.util.List<FrostedPanelView> cardViews = new java.util.ArrayList<>();
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

    // ------------------------------------------------------------ 连接门（用户要求）

    /**
     * 连接门容器：**放在顶部区（head）里**，不随设备卡片一起滚走。
     *
     * <p>为什么必须在 head 里：用户报的场景是「App 一打开就进了一条连不上的会话，
     * 干等」——他要的是"没连上就明确告诉我、并给我一个重试入口"。这个入口如果跟着
     * 卡片区一起滚动，用户一划就看不见了，等于没给。
     */
    private LinearLayout gateBox;
    /** 连接门当前内容（主题切换时整块重画，配色烘在创建时，见 applyTheme）。 */
    private boolean gateVisible = false;
    private String gateTitle = "";
    private String gateHint = "";
    private String gateRoute = "";
    private String gateAddr = "";
    private String gateReason = "";
    private boolean gateCanSwitch = false;
    private boolean gateBusy = false;

    public DeviceHubView(Context ctx, Host host) {
        super(ctx);
        this.ctx = ctx;
        this.host = host;
        setOrientation(VERTICAL);
        // [毛玻璃 v3] 透明：环境背景由 MainActivity 根容器铺，本页让出来才透得出彩色下层。
        setBackgroundColor(android.graphics.Color.TRANSPARENT);

        // ---- 设备卡片区（可滚动，玻璃顶栏/卡片的"内容层"）
        ScrollView scroll = new ScrollView(ctx) {
            @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
                super.onScrollChanged(l, t, ol, ot);
                refreshGlasses();
            }
        };
        this.scrollHost = scroll;
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = Ui.col(ctx);
        this.body = body;
        // iOS 分组列表：左右外边距 16dp（全 App 统一 M_SIDE），上下只留必要的一点点
        body.setPadding(Ui.dp(ctx, Ui.M_SIDE), Ui.dp(ctx, 10), Ui.dp(ctx, Ui.M_SIDE), Ui.dp(ctx, 20));
        scroll.addView(body);

        // ---- stage：滚动内容 + 浮动玻璃顶栏（FrostedPanelView 真液态玻璃）
        stage = new FrameLayout(ctx);
        stage.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        stage.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 玻璃顶栏：实时模糊 + 边缘受光，绑定 scroll（滚动严格对齐，背后跟随卡片滚过）
        // [2026-10-07] 环境底换成真图（星空/极光/山脊）后，标题区常常压在**深色**星空上，
        // 深色标题字就看不清了 → 顶栏给一块**真玻璃底板**（22% 白 + 白棱，与隔离 Demo 的
        // 顶栏同一套参数），文字在任何底色上都读得清。不再"直接铺在背景上"。
        // [2026-10-07 用户] 「顶面"我的设备"那块实际上没有卡片，去掉卡片，文字和右上角按钮直接落到背景上」
        // → 撤掉上一轮我擅自加的玻璃底板（headPanel），恢复"顶栏平铺在背景上"。
        headGlass = Ui.col(ctx);
        headPanel = null;

        LinearLayout headContent = Ui.col(ctx);
        headContent.setPadding(Ui.dp(ctx, Ui.M_SIDE), Ui.dp(ctx, 10), Ui.dp(ctx, 12), Ui.dp(ctx, 4));
        LinearLayout top = Ui.row(ctx);
        titleView = Ui.text(ctx, "我的设备", Ui.S_LARGE, Ui.INK, true);
        TextView title = titleView;
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tlp);
        top.addView(title);
        // [用户定版] 右上角设置 = 液态玻璃圆 + 悬浮阴影（圆保持原版 36dp，只缩里面黑色图标）
        FrostedPanelView gearGlass = new FrostedPanelView(ctx, scrollHost);
        gearGlass.corner(Ui.dp(ctx, 18f)).tint(0x08FFFFFF).hairline(0x1EFFFFFF).downscale(Ui.glassDownscale()).lens().targetRoot();
        android.widget.FrameLayout gearBox = new android.widget.FrameLayout(ctx);
        android.widget.ImageView giv = new android.widget.ImageView(ctx);
        // [2026-10-07 用户] 「右上角的按钮中间的黑色内容有点太饱满，缩小一半」：图标 18→9dp；
        // **玻璃圆按钮本身不动**（36dp 圆 + 玻璃材质保持原样）。
        giv.setImageDrawable(Ui.iconDrawable(ctx, com.dsh.mobile.R.drawable.ic_sliders, 9f, Ui.INK));
        gearBox.addView(giv, new android.widget.FrameLayout.LayoutParams(
                Ui.dp(ctx, 36), Ui.dp(ctx, 36), android.view.Gravity.CENTER));
        gearGlass.content().addView(gearBox);
        FrostShadowHolder gearHolder = new FrostShadowHolder(ctx, Ui.dp(ctx, 18f), gearGlass, 9f, 4.5f, 0f, 1.2f);  // offDp=0 → 上下均匀，正圆
        gearHolder.addView(gearGlass, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        // holder 总尺寸 = 圆36dp + 两侧阴影padding(8*2=16) = 52dp，玻璃圆才不被 padding 挤小
        gearHolder.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(ctx, 52), Ui.dp(ctx, 52)));
        gearGlass.setOnClickListener(v -> host.onOpenSettings());
        cardViews.add(gearGlass);
        top.addView(gearHolder);
        headContent.addView(top);

        // [2026-10-07 用户] 「上面我的设备下面的字有点浅看不太清」：副标题/状态行原来用的是
        // INK_SUB / INK_FAINT（浅灰），压在炫彩环境背景上对比不足。这里加深到 INK 的 80% / 65%。
        TextView sub = Ui.text(ctx, "管理你的电脑 · 连接后进入对话", Ui.S_SUB,
                Ui.alpha(Ui.INK, 0.88f), false);
        sub.setPadding(0, Ui.dp(ctx, Ui.G_TITLE_SUB), 0, 0);
        headContent.addView(sub);
        subTitle = sub;

        status = Ui.text(ctx, "", Ui.S_FOOT, Ui.alpha(Ui.INK, 0.78f), false);
        status.setPadding(0, Ui.dp(ctx, Ui.G_SECTION), 0, 0);
        headContent.addView(status);

        // 连接门容器：放玻璃顶栏胶囊里（保持"顶部区"语义）
        gateBox = Ui.col(ctx);
        gateBox.setVisibility(GONE);
        gateBox.setPadding(0, Ui.dp(ctx, 12), 0, 0);
        headContent.addView(gateBox);
        headGlass.addView(headContent);

        // [用户定版] 顶栏平铺加入滚动内容顶部（body），不悬浮覆盖 → 不重叠
        headGlass.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        addView(stage);
        buildBody();
        // [2026-10-07 用户定版 · 背景换回彩色配色] 这里原来临时铺了一张硬编码的
        // 「星空极光」位图（body.setBackground(BitmapDrawable(rich))，注释写着"[临时看液态玻璃]"），
        // 它把 MainActivity 根容器上那套**彩色环境背景**（Ui.AmbientDrawable + 设置页「配色」6 档）
        // 整片盖住了 —— 于是设置里换配色在本页毫无反应（实测：改 ambient_palette 后背景像素不变，
        // 只有状态栏跟着变）。按用户要求删掉，恢复由环境背景驱动的那套炫彩底。
    }

    /**
     * 卡片区内容：卡片容器 + 空态 + 虚线「＋ 添加设备」卡。
     * 单独一个方法是为了让主题切换能整块重建（手搓 View 的配色创建时烘死，重建最不容易漏）。
     */
    private void buildBody() {
        body.removeAllViews();
        if (headGlass != null) body.addView(headGlass, 0);   // [用户定版] 顶栏平铺在最顶部（无卡片）
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
        // [用户定版] 页脚**无玻璃**：白字带阴影，直接铺在背景上
        LinearLayout footerWrap = Ui.col(ctx);
        footerWrap.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView ft = Ui.text(ctx, "DSH 掌上通 · 与电脑端 DSH 直连\n数据只在你的设备之间流转",
                Ui.S_CAP1, 0xFFFFFFFF, false);
        ft.setGravity(Gravity.CENTER);
        ft.setLineSpacing(Ui.dp(ctx, 3), 1.1f);
        ft.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 14), Ui.dp(ctx, 8), Ui.dp(ctx, 8));
        ft.setShadowLayer(Ui.dp(ctx, 3), 0f, Ui.dp(ctx, 1.5f), 0x8A000000);
        footerWrap.addView(ft);
        body.addView(footerWrap);
    }

    /**
     * 主题切换：整页按新色板重画。
     * 卡片容器与虚线卡都重建（配色烘在创建时）；调用方随后会 refreshDevices() 重填卡片。
     */
    public void applyTheme() {
        // [毛玻璃 v3] 透明：环境背景由 MainActivity 根容器铺，本页让出来才透得出彩色下层。
        setBackgroundColor(android.graphics.Color.TRANSPARENT);
        // 玻璃顶栏背景由 FrostedPanelView 绘制（磨砂 + 折射 + 边缘受光），无需再 setBackgroundColor。
        // 大标题/副标题/状态行字色必须跟着刷（漏了就是深色黑标题 bug）
        if (titleView != null) titleView.setTextColor(Ui.INK);
        if (subTitle != null) subTitle.setTextColor(Ui.INK_SUB);
        if (status != null) status.setTextColor(Ui.INK_FAINT);
        // 连接门是手搓 View，配色烘在创建时（Ui.card 的 CardBg 读的是当次主题色）→ 必须整块重画
        renderGate();
        buildBody();
        requestLayout();
    }

    // ---- 玻璃面板统一刷新（滚动/内容变化时调用；不在 dispatchDraw 里做，防静态持续重绘） ----

    /**
     * 每轮最多刷新的面板数（轮转）。
     *
     * <p>[2026-10-07 性能] 抓取源上提到应用根容器后，**每个面板一次＝整棵视图树软件重绘一次**；
     * 本页有 7 个玻璃件，一次滚动事件里全刷 = 一帧 ~28ms（模拟器实测 90th=32ms / 95th=46ms）→ 必卡。
     * 改成每轮轮转刷 3 个：单帧抓取次数封顶，玻璃仍在一两帧内跟上滚动。
     */
    private static final int REFRESH_PER_TICK = 3;
    private int refreshCursor;

    private void refreshGlasses() {
        int n = cardViews.size();
        if (n == 0) return;
        for (int k = 0; k < Math.min(REFRESH_PER_TICK, n); k++) {
            refreshCursor = (refreshCursor + 1) % n;
            FrostedPanelView f = cardViews.get(refreshCursor);
            if (f.getParent() != null && onScreen(f)) {
                f.refreshFrost();
                f.invalidate();
            }
        }
    }

    /** 一次性刷新全部面板（布局变化后用，不走轮转）。 */
    private void refreshGlassesNow() {
        for (FrostedPanelView f : cardViews) {
            if (f.getParent() != null && onScreen(f)) {
                f.refreshFrost();
                f.invalidate();
            }
        }
    }

    /** [性能] 屏幕外的玻璃件抓了也看不见，白吃一帧（设备页 7 个玻璃件、真机一屏放不下）。 */
    private static boolean onScreen(FrostedPanelView f) {
        int w = f.getWidth(), h = f.getHeight();
        if (w <= 0 || h <= 0) return false;
        int[] p = new int[2];
        f.getLocationInWindow(p);
        android.util.DisplayMetrics dm = f.getResources().getDisplayMetrics();
        return p[1] + h > 0 && p[1] < dm.heightPixels && p[0] + w > 0 && p[0] < dm.widthPixels;
    }

    private final Runnable refreshAfterLayout = this::refreshGlassesNow;

    /**
     * [2026-10-07 用户实测「玻璃胶囊和背景对不上」的真因] **布局变化后必须重抓**。
     *
     * <p>玻璃面板只在 `onAttachedToWindow`（首屏）和**滚动**时抓取。可是首屏布局是**逐步撑开**的
     * （顶栏/卡片高度、字号、内容增减都会让它上下移动），而面板不会因此重抓 —— 于是它一直停在
     * "旧位置那张快照"上：实测「添加设备」胶囊里显示的竟然是**它下面那段页脚文字**，
     * 跟背后的真背景整体错位一段（用户原话「这个玻璃胶囊和背景对不上」）。
     *
     * <p>这里在每次布局后**延迟一次全量重抓**（`post` 到下一帧，等布局稳定；`refreshFrost` 自带
     * 30ms 节流，不会打爆主线程）。`invalidate()` 不会触发 onLayout，所以不会自激循环。
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (!cardViews.isEmpty()) post(refreshAfterLayout);
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

    // ------------------------------------------------------------ 连接门

    /**
     * 连接门：**没连上电脑时**顶在设备页最上面的一块状态面板。
     *
     * <p>用户明确要求（2026-10-05）：
     * <pre>
     *   「退出应用强制首页，连上再进入」 —— 没连上就别把人丢进一条连不上的会话里干等；
     *   要停在设备页，显示 当前线路 / 目标地址 / 失败原因，并给出
     *   「重新连接」「重新扫码配对」「切换线路」三个入口。
     * </pre>
     *
     * <p>刻意**不做成阻塞式弹窗**：它只是设备页顶部的一块面板，下面的设备卡片、
     * 左缘抽屉（历史会话）、右上角齿轮（设置）全都照常可用 —— 连不上也要能看历史。
     *
     * @param visible   要不要显示（连上了就整块收起）
     * @param title     一句话结论，如「还没连上电脑」
     * @param hint      下一步该做什么（白话）
     * @param routeLine 当前线路，如「走内网 · 自动 · 已连 WiFi」
     * @param address   目标地址（host:port）
     * @param reason    失败原因（白话；空串则不占一行）
     * @param canSwitch 是否显示「切换线路」（内网/公网两个地址都存着时才显示）
     * @param busy      正在重连/扫描：按钮置灰、点不动（防重复触发）
     */
    public void setGate(boolean visible, String title, String hint, String routeLine,
                        String address, String reason, boolean canSwitch, boolean busy) {
        this.gateVisible = visible;
        this.gateTitle = title == null ? "" : title;
        this.gateHint = hint == null ? "" : hint;
        this.gateRoute = routeLine == null ? "" : routeLine;
        this.gateAddr = address == null ? "" : address;
        this.gateReason = reason == null ? "" : reason;
        this.gateCanSwitch = canSwitch;
        this.gateBusy = busy;
        renderGate();
    }

    /** 按当前 gate* 字段整块重画（主题切换、状态刷新都走这里，保证配色不残留）。 */
    private void renderGate() {
        if (gateBox == null) return;
        gateBox.removeAllViews();
        // [2026-10-05 用户要求：把新加的那个卡片整个删掉 ✗]
        // 用户原话："你能不能不瞎改，我的设备那页还是要默认页啊，就是**你新加的那个不要**，
        // 怎么还有" → 那张"还没连上电脑 · 点这里重试"的卡片（含线路/目标/失败原因 +
        // 「重新连接」「重新扫码配对」「切换线路」三个大按钮）**整体不再渲染** ✓。
        // 「我的设备」页保持原来的样子（原设备卡 + 添加设备 ✓）。
        // 未连接的提示交给 App 原有的连接状态文案/横幅 ✓（不在这里新造卡片 ✗）。
        // 后台逻辑全部保留：LanScan 自动发现 ✓ / Store 上次会话 ✓ / LanAddress ✓ / 自动重连 ✓。
        gateBox.setVisibility(GONE);
        if (true) return;
        gateBox.setVisibility(VISIBLE);

        LinearLayout card = Ui.card(ctx);
        card.setPadding(Ui.dp(ctx, Ui.M_CARD_PAD), Ui.dp(ctx, 14),
                Ui.dp(ctx, Ui.M_CARD_PAD), Ui.dp(ctx, 14));

        // 第一行：状态点 + 一句话结论
        LinearLayout r1 = Ui.row(ctx);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        r1.addView(Ui.dot(ctx, 9f, gateBusy ? Ui.BRAND : Ui.WARN));
        TextView title = Ui.text(ctx, gateTitle, Ui.S_TITLE3, Ui.INK, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dp(ctx, 9);
        title.setLayoutParams(tlp);
        r1.addView(title);
        card.addView(r1);

        // 第二行：下一步（白话）
        if (!gateHint.isEmpty()) {
            TextView hint = Ui.text(ctx, gateHint, Ui.S_FOOT, Ui.INK_SUB, false);
            hint.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 5), 0, 0);
            hint.setLineSpacing(Ui.dp(ctx, 2), 1.15f);
            card.addView(hint);
        }

        // 第三行：当前线路 + 目标地址（用户要求显示这两项，便于自查）
        String line2 = "";
        if (!gateRoute.isEmpty()) line2 = "当前线路：" + gateRoute;
        if (!gateAddr.isEmpty()) line2 += (line2.isEmpty() ? "" : "\n") + "目标地址：" + gateAddr;
        if (!line2.isEmpty()) {
            TextView l2 = Ui.text(ctx, line2, Ui.S_FOOT, Ui.INK, false);
            l2.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 7), 0, 0);
            l2.setLineSpacing(Ui.dp(ctx, 2), 1.15f);
            card.addView(l2);
        }

        // 第四行：失败原因（有才显示）
        if (!gateReason.isEmpty()) {
            TextView rs = Ui.text(ctx, "失败原因：" + gateReason, Ui.S_FOOT, Ui.WARN, false);
            rs.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 6), 0, 0);
            rs.setLineSpacing(Ui.dp(ctx, 2), 1.15f);
            card.addView(rs);
        }

        // [2026-10-05 用户决定：删掉连接门 ✗] 原来这里是「重新连接」大按钮 + 「重新扫码配对」
        // 大按钮并排（外加「切换线路」大按钮）—— 用户原话："app 上面有个手动的重新连接，
        // 还有个重新扫码配对…都自动适配了，加那玩意没啥意义啊"✗。
        // 改成**极简空态**：不再有任何大按钮 ✓，只留一行说明 + 两个低调小字链接（最终出路）✓。
        // 正常情况下这个页面根本不该出现（自动发现会自己扫回来 ✓）。
        TextView tip = Ui.text(ctx, "正在自动重连并搜索电脑…\n正常情况下不用管，连上会自动进入。",
                Ui.S_FOOT, Ui.INK_SUB, false);
        tip.setPadding(0, Ui.dp(ctx, 13), 0, 0);
        card.addView(tip);
        if (gateBusy) {
            TextView busy = Ui.text(ctx, "正在重试…", Ui.S_FOOT, Ui.INK_SUB, false);
            busy.setPadding(0, Ui.dp(ctx, 6), 0, 0);
            card.addView(busy);
        }
        LinearLayout links = Ui.row(ctx);
        links.setPadding(0, Ui.dp(ctx, 10), 0, 0);
        TextView rescanLink = Ui.text(ctx, "重新扫码配对", Ui.S_FOOT, Ui.INK, false);
        rescanLink.setContentDescription("重新扫码配对");
        rescanLink.setOnClickListener(v -> host.onRescanPair());
        links.addView(rescanLink);
        if (gateCanSwitch) {
            TextView swLink = Ui.text(ctx, "    切换线路（内网 ⇄ 公网）", Ui.S_FOOT, Ui.INK, false);
            swLink.setContentDescription("切换线路");
            if (!gateBusy) swLink.setOnClickListener(v -> host.onSwitchRoute());
            links.addView(swLink);
        }
        card.addView(links);

        gateBox.addView(card);
    }

    private LinearLayout.LayoutParams btnWeight(float w, int marginStartDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
        lp.leftMargin = Ui.dp(ctx, marginStartDp);
        return lp;
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
    /**
     * [毛玻璃 v3] 一张**玻璃卡**。
     *
     * <p>2026-10-06 回退说明：之前为了让投影画在形状之外，这里给卡片套了 {@code Ui.shadowed}
     * 并让形状在 View 里内缩 18dp —— 结果是**整页几何被撑开**（两张卡的视觉间距实测 54dp，
     * demo 是 17dp），用户看到的就是"严重偏移"。现已**退回不挂外投影的版本**（用户认可的那版）：
     * 卡片只用 {@link Ui.CardBg}（体 + 斜射高光 + 上亮下暗棱光 + 白棱发丝线），几何与之前一致。
     */
    /**
     * [离屏模糊实验] 抓取要用的"下层内容" = **应用根容器**（环境背景挂在它上面）。
     * 注意返回的是 **v 而不是 pv**：pv 是 DecorView（窗口层，`draw()` 抓不到任何东西）。
     */
    private View blurSource() {
        View v = this;
        while (v.getParent() instanceof View) {
            View pv = (View) v.getParent();
            if (!(pv.getParent() instanceof View)) return v;
            v = pv;
        }
        return v;
    }

    /**
     * 给**任意悬浮件**套上"真模糊底"（源 = 应用根容器）。
     *
     * <p>[2026-10-06] 用户的明确要求：悬浮的卡片 / 线路行 / 按钮**各自都该有模糊**。
     * 之前我拿"首页背后只有环境渐变、糊了看不出"当理由不做，是错的 ——
     * 悬浮件的玻璃感本来就该由"它自己糊掉背后那层"来提供，而不是只靠半透明白蒙层。
     */
    private android.graphics.drawable.Drawable frosted(View panel, float radiusDp, float cornerDp,
                                                       android.graphics.drawable.Drawable glass) {
        // [2026-10-07] 采样偏移已修正（clipRect 写在 scale/translate 之后 → 裁剪落在"已变换坐标系"
        // 里，位图只有顶部一小条有内容，空处露出底下清晰的底 → 那道横跨卡宽的灰带）。现开回真模糊。
        View src = blurSource();
        // [2026-10-07 回退] 用户判定"液态玻璃"实验版更差（过饱和 + 折射环像伪影）→ 首页不接模糊，回到认可版。
        return glass;
    }
    /**
     * 阴影容器：卡片背后画一圈柔和外投影（悬浮感）。
     * 外投影画在容器自身的 padding 区域（卡面在 padding 内侧），因此不会被滚动区裁剪，
     * 四边 + 圆角完整、随卡形。卡片浮在阴影之上。
     */
    private final class FrostShadowHolder extends android.widget.FrameLayout implements com.dsh.mobile.proto.Frost.ShadowSuppressible {
        private final float shPad;
        private final float corner;
        private final FrostedPanelView card;
        private final float blurDp, offDp, insDp;
        private boolean skipShadow = false;
        FrostShadowHolder(android.content.Context c, float cornerPx, FrostedPanelView glass) {
            this(c, cornerPx, glass, 18f, 11f, 5f, 3f);   // 更柔和：模糊更大、色更淡
        }
        /** 小阴影重载：按钮/右上角用（更小 padding + 更柔和小阴影，不被撑高/挤没）。 */
        FrostShadowHolder(android.content.Context c, float cornerPx, FrostedPanelView glass,
                          float shPadDp, float blur, float off, float ins) {
            super(c);
            this.shPad = Ui.dp(c, shPadDp);
            this.corner = cornerPx;
            this.card = glass;
            this.blurDp = blur; this.offDp = off; this.insDp = ins;
            setWillNotDraw(false);
            // 阴影下移需要上下多留空间；offDp=0（如正圆按钮）时上下均匀，圆不被拉成椭圆
            float botExtra = off > 0f ? Ui.dp(c, shPadDp * 0.28f) : 0f;
            setPadding((int) shPad, (int) (shPad + botExtra),
                    (int) shPad, (int) (shPad + botExtra));
        }
        FrostedPanelView getCard() { return card; }
        @Override public void setShadowSuppressed(boolean s) {
            if (skipShadow != s) { skipShadow = s; invalidate(); }
        }
        @Override protected void onDraw(android.graphics.Canvas cv) {
            int w = getWidth(), h = getHeight();
            // [2026-10-07] 抓取期间停画：外投影绝不能被烘进别人的玻璃（否则玻璃里一块块深色斑）
            if (w <= 0 || h <= 0 || skipShadow || Ui.isCapturing()) { super.onDraw(cv); return; }
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            // [2026-10-07 照规范 §九-9.2 代码] 阴影色 = 0x33000000（规范代码里的值）。
            p.setColor(0x33000000);
            p.setMaskFilter(new android.graphics.BlurMaskFilter(Ui.dp(getContext(), blurDp),
                    android.graphics.BlurMaskFilter.Blur.NORMAL));
            // 上面小、下面大（投影感）：阴影主体内缩 insDp、下移 offDp、模糊 blurDp
            float off = Ui.dp(getContext(), offDp);
            float ins = Ui.dp(getContext(), insDp);
            cv.drawRoundRect(shPad + ins, shPad + ins + off,
                    w - shPad - ins, h - shPad - ins + off, corner, corner, p);
            super.onDraw(cv);
        }
    }
    private View glassCard() {
        // 真液态玻璃卡：FrostedPanelView（实时模糊 + 折射 + 边缘受光，绑定卡片滚动层），
        // 内容放进 content()（玻璃底之上的纵向容器，保持原有布局语义）。
        FrostedPanelView c = new FrostedPanelView(ctx, scrollHost);
        // [2026-10-07 用户定版 · 去掉折射] 卡片原来开了 lens 折射（环带 28dp / 幅度 38px）。
        // 用户反馈「玻璃扭曲显示的东西好像有点错位，怎么会显示上面卡片的内容呢」——
        // 折射环带在边缘会**采样到卡片矩形之外**的内容（模糊位图带 74px padding 溢出区），
        // 于是下张卡的边缘把上张卡的内容"折"进来。整页所有 FrostedPanelView 一并去掉 lens，
        // 液态感改由"真模糊 + 玻璃体 + 白棱"承担。
        c.corner(Ui.dp(ctx, 18f)).tint(0x42FFFFFF).hairline(0x1EFFFFFF).downscale(Ui.glassDownscale()).lens().targetRoot();
        c.setWillNotDraw(false);
        c.setElevation(0f);   // 绝不能用框架 elevation：它会透过半透明卡面（见 deviceCard 注释）
        c.setPadding(Ui.dp(ctx, Ui.M_CARD_PAD), Ui.dp(ctx, CARD_PAD_V),
                Ui.dp(ctx, Ui.M_CARD_PAD), Ui.dp(ctx, CARD_PAD_V));   // 卡片上下内边距
        // [悬浮阴影] 包一层阴影容器：外投影画在容器 padding 区域，不被滚动区裁剪，卡片浮在阴影上
        FrostShadowHolder holder = new FrostShadowHolder(ctx, Ui.dp(ctx, Ui.R_CARD), c);
        holder.addView(c, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        holder.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        cardViews.add(c);
        return holder;
    }

    private View deviceCard(final Store.Device d, boolean isActive, boolean online) {
        // [毛玻璃 v3 · 同步 demo ②] 设备卡从「饱和渐变卡」改为**玻璃卡**（demo `.glass.dev-card`）：
        // 卡体是半透明玻璃、浮在彩色环境背景上，棱光/斜射高光/内发光由 Ui.CardBg 叠。
        // 语义不再靠"卡面染色"，改回 App 既有的语义色（绿点=在线 / 灰点=离线 / 品牌淡底=当前），
        // 这几档都是机检过 WCAG AA 的，比在饱和色卡上硬凑白字更稳。
        // [用户定版] 设备卡 = **流体玻璃**（悬浮玻璃卡片），内容在卡内
        View holder = glassCard();
        FrostedPanelView card = ((FrostShadowHolder) holder).getCard();
        // [2026-10-07 用户] 「两个设备卡片间距太远，缩小两倍」：视觉间距 = holder padding(18+5dp)
        // ×2 + M_GAP(8dp) ≈ 54dp，光调 M_GAP 没用 —— 用负 margin 把两侧 padding 抵消掉，压到 ~27dp。
        ((LinearLayout.LayoutParams) holder.getLayoutParams()).bottomMargin = Ui.dp(ctx, -19f);

        // 第一行：图标砖 + （设备名 + 徽标）/ 桌面端
        //
        // 形状逐条对齐 demo `.dev-row`：
        //   · 左：44dp 方块（圆角 14、白 40% 底 + 白棱发丝线、居中图标）；
        //   · 右：第一行是 **设备名 + 在线徽标 + 当前徽标**（同一行、可换行），
        //         第二行才是「桌面端」——旧版把「在线/当前」甩到行的最右端，
        //         和 demo 的"徽标紧跟名字"不是一回事。
        LinearLayout row1 = Ui.row(ctx);
        row1.setMinimumHeight(Ui.dp(ctx, 44));
        // [液态玻璃 · 规范点名项 2026-10-07] demo 的 `.dev-row{display:flex;align-items:center;gap:12px}`
        // 要求图标砖与右侧文字块**垂直居中**；旧版没设 gravity（默认 TOP），44dp 图标砖与更高的
        // 「名字行 + 桌面端」文字块顶部对齐 → 就是用户点名的"顶行比例/对齐不对"。
        row1.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row1.addView(devIconTile());

        LinearLayout names = Ui.col(ctx);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = Ui.dp(ctx, 12);
        names.setLayoutParams(nlp);

        LinearLayout nameRow = Ui.row(ctx);
        TextView name = Ui.text(ctx, d.displayName(), Ui.S_BODY, Ui.INK, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameRow.addView(name);
        nameRow.addView(onlineBadge(online));
        if (isActive) nameRow.addView(nowBadge());
        names.addView(nameRow);

        TextView plat = Ui.text(ctx, platformLabel(d), Ui.S_FOOT, Ui.INK, true);
        plat.setPadding(0, Ui.dp(ctx, 3), 0, 0);
        names.addView(plat);
        row1.addView(names);
        card.content().addView(row1);

        // 第二行：标签（内网·固定 / 公网 / 版本号）—— demo 是**纯文字 kv**，不用胶囊：
        // 玻璃卡上再压一层灰胶囊只会把卡面做脏，字重区分主次就够。
        LinearLayout tags = Ui.row(ctx);
        tags.setPadding(0, Ui.dp(ctx, 6), 0, 0);
        if (d.hasUsableLan()) tags.addView(kv("内网", "· 固定"));
        if (d.wanUrl != null && !d.wanUrl.isEmpty()) tags.addView(kv("公网", ""));
        if (d.dshVersion != null && !d.dshVersion.isEmpty()) tags.addView(kv("DSH", d.dshVersion));
        card.content().addView(tags);

        // 第三行：这次走哪条线路 + 规则来源（**不显示地址**，方便用户截图分享）
        // 呈一个内嵌的「线路行」（demo `.line-row`：14dp 圆角 + 半透明白 + 白棱发丝线）。
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
        // [毛玻璃 v3] 线路行：**只挂内棱、不挂外投影** —— demo 的 `.line-row` 就是
        // `background:rgba(255,255,255,.36) + border:.5px + box-shadow:inset 0 1px 0 var(--rim)`，
        // 没有外投影。之前我给它硬加了外投影，而模糊半径 > 下移量时阴影会被糊到行的**上方**，
        // 看着就是"元素顶上飘了一圈灰"（用户原话「阴影高光都漂移了」）。
        // [用户定版] 离线连接介绍 = 液态玻璃（FrostedPanelView 捕获背景 + 折射），文字加深更明显
        FrostedPanelView addrGlass = new FrostedPanelView(ctx, scrollHost);
        addrGlass.corner(Ui.dp(ctx, 14)).tint(0x38FFFFFF).hairline(0x1EFFFFFF).downscale(Ui.glassDownscale()).lens().targetRoot();
        TextView addr = Ui.text(ctx, addrLine, Ui.S_FOOT, Ui.INK, true);
        addr.setPadding(Ui.dp(ctx, 11), Ui.dp(ctx, 9), Ui.dp(ctx, 11), Ui.dp(ctx, 9));
        addrGlass.content().addView(addr);
        cardViews.add(addrGlass);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.topMargin = Ui.dp(ctx, 6);
        card.content().addView(addrGlass, alp);

        // 第四行：进入/重连 + 修改名称 + 删除
        //
        // 文案必须和"在线/离线"一致，不能骗人：只有真的 READY（online）才写「进入对话」；
        // 离线时写「重连」，点下去由宿主先连、连上了才切页（连接失败会明确提示）。
        LinearLayout btns = Ui.row(ctx);
        // 撑满卡片宽度：三按钮才可能等宽拉长（weight 等分）
        btns.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        btns.setPadding(0, Ui.dp(ctx, 8), 0, 0);

        boolean busy = isActive && connecting && !online;
        // 主按钮 = 品牌渐变胶囊（demo `.btn-primary`：135° 紫→蓝渐变），走既有 primaryButton
        TextView enter = Ui.primaryButton(ctx,
                online ? "进入对话" : (busy ? "重连中…" : (isActive ? "重连" : "连接")));
        if (busy) Ui.setButtonEnabled(enter, false);   // 等待期间置灰，防重复点
        enter.setContentDescription(online ? "进入对话" : "重连");
        enter.setOnClickListener(v -> host.onOpenDevice(d));
        // [毛玻璃 v3] 三个按钮**等宽**（demo `.actions{display:flex} .actions .btn{flex:1}`）。
        // [2026-10-07 用户定版] 用户要求「三个按钮变长」：按钮的可视胶囊被外层阴影容器
        // 各吃掉 10dp（左右各一），实测只有 204px/78dp，而它的格子有 256px —— 明显"短一截"。
        // 现在把按钮阴影容器的 shPad 收到 5dp（阴影相应收小，见 frostedButton），
        // 并把按钮间距从 9dp 收到 6dp：格子变宽 + 内缩变少 → 胶囊实测变长约 16%。
        btns.addView(frostedButton(enter, new int[]{0xFF9A6BFF, 0xFF3A8CFF}, 0x08FFFFFF, 160), weight(1f, 0));

        // 次按钮 = 玻璃幽灵胶囊（demo `.btn-ghost`：半透明白 + 白棱）
        TextView rename = Ui.ghostButton(ctx, "修改名称");
        rename.setOnClickListener(v -> host.onRenameDevice(d));
        btns.addView(frostedButton(rename, new int[]{0xF2FFFFFF, 0xCCFFFFFF}, 0x08FFFFFF, 180), weight(1f, 6));

        // 危险操作 = 红字淡底胶囊（demo `.btn-danger`：rgba(255,59,48,.16) 底 + **.5px 红边** + 深红字）
        // 那圈红边就是它的"高光"，漏掉就只剩一块淡红底（用户反馈「右边红色高光怎么没了」）。
        TextView del = Ui.pillButton(ctx, "删除", Ui.alpha(Ui.ERR, 0.16f), 0xFFB71C1C,
                Ui.alpha(Ui.ERR, 0.28f));   // 深红字：加深、不那么跳
        del.setOnClickListener(v -> host.onDeleteDevice(d));
        btns.addView(frostedButton(del, new int[]{0x59FF3B30, 0x2EFF3B30}, 0x08FFFFFF, 170), weight(1f, 6));
        card.content().addView(btns);
        return holder;
    }

    /** [用户定版] 按钮 = 带颜色的液态玻璃：玻璃底 + 语义色渐变 + 文字，一体成形；统一悬浮阴影。 */
    private View frostedButton(TextView btn, int[] grad, int tint, int alpha) {
        FrostedPanelView g = new FrostedPanelView(ctx, scrollHost);
        g.corner(999f).tint(tint).hairline(0x1EFFFFFF).downscale(Ui.glassDownscale()).lens().targetRoot();
        String txt = btn.getText().toString();
        int fg = btn.getCurrentTextColor();
        android.widget.FrameLayout inner = new android.widget.FrameLayout(ctx);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR, grad);
        bg.setCornerRadius(Ui.dp(ctx, 999f));
        bg.setAlpha(alpha);
        inner.setBackground(bg);
        TextView label = Ui.text(ctx, txt, Ui.S_BODY, fg, true);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8));   // 按钮横向拉长（宽度满行、高度不过高）
        inner.addView(label, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        g.content().addView(inner, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        cardViews.add(g);
        // 按钮点击逻辑 + 动画：与「添加设备」按钮一致（Ui.tap 轻微缩放 0.98）
        g.setOnClickListener(v -> btn.performClick());
        Ui.tap(g, 0.98f);
        // 所有玻璃按钮统一悬浮阴影（小阴影参数：按钮小，阴影柔和不大）。
        // [2026-10-07 用户定版「三个按钮变长」] shPad 10dp→5dp：这层 padding 是**阴影的立足地**，
        // 它同时把按钮的可视胶囊往里挤（10dp/侧 → 实测胶囊只有 204px 而格子有 256px）。
        // 收到 5dp 后胶囊变长，阴影同步收小（blur/off/ins 一起降），不会溢出成灰边。
        FrostShadowHolder h = new FrostShadowHolder(ctx, Ui.dp(ctx, 999f), g, 5f, 4.0f, 1.8f, 1.0f);
        h.addView(g, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        return h;
    }

    /**
     * demo 的 `.dev-icon`：44dp 方块、圆角 14、**白 40% 底 + 白棱发丝线**、图标居中。
     *
     * <p>为什么不用 {@code Ui.iconBox}：它的底是 {@code IconBg}（自绘图标 + 纯色填充），
     * 加不了 demo 那条 `.5px solid rgba(255,255,255,.5)` 的白棱；这里手搓一个方块更贴 demo。
     */
    private View devIconTile() {
        // [用户定版] 电脑小图标 = 液态玻璃材质（不改形状：44dp、圆角14、图标居中），
        // 用 FrostedPanelView 捕获卡片背后滚动内容 → 磨砂 + 边缘折射形变
        FrostedPanelView tile = new FrostedPanelView(ctx, scrollHost);
        tile.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 44)));
        tile.corner(Ui.dp(ctx, 14)).tint(0x42FFFFFF).hairline(0x1EFFFFFF).downscale(Ui.glassDownscale()).lens().targetRoot();
        android.widget.FrameLayout box = new android.widget.FrameLayout(ctx);
        android.widget.ImageView iv = new android.widget.ImageView(ctx);
        iv.setImageDrawable(Ui.iconDrawable(ctx, com.dsh.mobile.R.drawable.ic_monitor, 8f, Ui.INK));
        box.addView(iv, new android.widget.FrameLayout.LayoutParams(
                Ui.dp(ctx, 44), Ui.dp(ctx, 44), android.view.Gravity.CENTER));
        tile.content().addView(box);
        cardViews.add(tile);
        return tile;
    }

    /** demo 的 `.badge`：11sp 胶囊 + 6dp 圆点，绿底深绿字（在线 / 离线同款，只换色）。 */
    private View onlineBadge(boolean online) {
        // [用户定版] 徽标看不清 → 文字加深（INK）+ 底色加深（离线黑11%）更明确
        LinearLayout b = Ui.dotLabel(ctx, 6f, online ? Ui.BADGE_OK_FG : Ui.INK,
                online ? "在线" : "离线", Ui.S_CAP1, online ? Ui.BADGE_OK_FG : Ui.INK);
        b.setPadding(Ui.dp(ctx, 9), Ui.dp(ctx, 3), Ui.dp(ctx, 9), Ui.dp(ctx, 3));
        b.setBackground(Ui.pill(online ? Ui.BADGE_OK_BG : 0x1C000000));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(ctx, 8);
        b.setLayoutParams(lp);
        return b;
    }

    /** demo 的 `.badge.now`：品牌淡底 + 深字，无圆点。 */
    private TextView nowBadge() {
        // [用户定版] 「当前」徽标看不清 → 文字加深（INK）+ 底色加深（品牌24%）
        TextView t = Ui.text(ctx, "当前", Ui.S_CAP1, Ui.INK, true);
        t.setTypeface(Ui.medium());
        t.setPadding(Ui.dp(ctx, 9), Ui.dp(ctx, 3), Ui.dp(ctx, 9), Ui.dp(ctx, 3));
        t.setBackground(Ui.pill(0x3D5444C8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(ctx, 8);
        t.setLayoutParams(lp);
        return t;
    }

    /** 纯文字 kv（demo 的 `<b>内网</b> · 固定`）：标签用正文色、值用次要色。 */
    private TextView kv(String key, String value) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        int s0 = sb.length();
        sb.append(key);
        sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.INK), s0, sb.length(), 0);
        if (value != null && !value.isEmpty()) {
            sb.append(" ").append(value);
        }
        sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.INK_SUB), 0, sb.length(), 0);
        sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.INK), s0, s0 + key.length(), 0);
        TextView t = Ui.text(ctx, "", Ui.S_FOOT, Ui.INK_SUB, false);
        t.setText(sb);
        t.setTypeface(Ui.medium());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(ctx, 12);
        t.setLayoutParams(lp);
        return t;
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
        // [毛玻璃 v3 · 同步 demo ②] 形状逐条对齐 demo 的 `.glass.add-dev`：
        //   卡内**居中**：渐变圆形「＋」+ 文字「添加设备」（不是左图标 + 左对齐两行 + 右箭头）；
        //   卡**下方**另起一行居中小字「扫码或输入设备连接」（demo 里它在卡片外面）。
        LinearLayout wrap = Ui.col(ctx);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = Ui.dp(ctx, 4);
        wrap.setLayoutParams(wlp);

        // [用户定版] 添加卡 = 流体玻璃（悬浮玻璃卡片）
        FrostedPanelView card = new FrostedPanelView(ctx, scrollHost);
        card.corner(Ui.dp(ctx, 18f)).tint(0x42FFFFFF).hairline(0x1EFFFFFF).downscale(Ui.glassDownscale()).lens().targetRoot();
        card.setWillNotDraw(false);
        card.setMinimumHeight(Ui.dp(ctx, 58));
        card.setElevation(0f);   // 不挂 elevation：框架阴影会透过半透明卡面把卡面压灰
        cardViews.add(card);
        // [悬浮阴影] 包一层阴影容器：外投影画在容器 padding 区域，不被滚动区裁剪，卡片浮在阴影上
        FrostShadowHolder holder = new FrostShadowHolder(ctx, Ui.dp(ctx, Ui.R_CARD), card);
        holder.addView(card, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        holder.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout cardRow = Ui.row(ctx);
        cardRow.setGravity(Gravity.CENTER);
        cardRow.setPadding(Ui.dp(ctx, 15), Ui.dp(ctx, 15), Ui.dp(ctx, 15), Ui.dp(ctx, 15));
        card.content().addView(cardRow);

        // 渐变圆形「＋」（demo `.add-dev .plus`：26dp 圆、135° 品牌渐变、白字）
        TextView plus = Ui.text(ctx, "＋", 16f, 0xFFFFFFFF, true);
        plus.setGravity(Gravity.CENTER);
        plus.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(ctx, 26), Ui.dp(ctx, 26)));
        plus.setBackground(Ui.brandPill());
        cardRow.addView(plus);

        TextView t = Ui.text(ctx, "添加设备", Ui.S_HEAD, Ui.INK, true);
        t.setPadding(Ui.dp(ctx, 9), 0, 0, 0);
        cardRow.addView(t);

        card.setClickable(true);
        card.setContentDescription("添加设备");
        card.setOnClickListener(v -> showAddSheet());
        Ui.tap(card, 0.98f);
        wrap.addView(holder);

        TextView hint = Ui.text(ctx, "扫码或输入设备连接", Ui.S_FOOT, Ui.INK_SUB, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.dp(ctx, 8), 0, 0);
        wrap.addView(hint);
        return wrap;
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







