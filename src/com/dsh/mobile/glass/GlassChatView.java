package com.dsh.mobile.glass;

import android.app.Activity;
import android.view.View;

import com.dsh.mobile.model.ChatItem;
import com.dsh.mobile.model.PendingAttachment;
import com.dsh.mobile.ui.ConversationView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * 【对话页双写宿主（V3.3 · 目标 ②c）】把 `MainActivity` 对对话页的调用**同时**喂给
 * 旧 `ConversationView` 与 Compose 版 [GlassChatScreen]。
 *
 * <h3>为什么是"双写"而不是"替换"</h3>
 * `ConversationView`（2099 行）是 **final** 类，不能继承覆写；而 `MainActivity` 对它有 **36 个方法**的调用
 * （setItems/setTitleText/setRunning/setBanner/setPlanRich/setSubagentEntry/setMoreStatus/setReadOnly/
 * addPendingSend/setPendingItems/clearPendingSends/setPendingAttachments/setPermissionChip/setModel|Effort|
 * Usage|ProjectLabel/setCompact/setDraft/draftText/hasDraft/focusInput/refresh/refreshNow/scrollToBottom/
 * scrollToKey/planFullText/projectPath/applyTheme/onConfigChanged/onWindowInsetsChanged/setTasksProgress…）。
 * 本类**同名转发**这些方法 ⇒ `MainActivity` 里那 36 个调用点**一行都不用改**，只换了字段类型与一处挂载。
 *
 * <p>旧视图继续被完整喂着 ⇒ ①它仍是**简洁模式的过滤引擎**（`applyFilter` 把一串工具压成 STEP 摘要行，
 * 本类靠只读的 {@link ConversationView#visibleItems()} 把它捞过来）；②它是**一行回退**的落点
 * （`USE_GLASS_CHAT=false` 时 {@link #view()} 直接返回它）。
 *
 * <p>动作侧：本类实现 [GlassChatHost]，**全部转发给同一个 `ConversationView.Host`（就是 `MainActivity`）**
 * ⇒ 发送/停止/附件/任务列表/更多/子智能体/加载更早/审批/提问/交付物下载/复制路径 走的都是**原有真实流程**。
 */
public final class GlassChatView implements GlassChatHost {

    private final ConversationView legacy;
    private final ConversationView.Host host;
    private final GlassChatState state = new GlassChatState();
    private final boolean enabled;
    private final View composeView;

    /** 能力 chip 的原始标签（MainActivity 传来的是"裸值"，前缀在这里统一拼）。 */
    private String chipProject = "", chipModel = "", chipPermission = "", chipEffort = "", chipUsage = "";
    /** 「任务 · d/t」（旧视图：total≤0 时整枚隐藏 ⇒ 这里空串即不加）。 */
    private String chipTasks = "";

    public GlassChatView(Activity act, ConversationView.Host host, boolean enabled) {
        this.host = host;
        this.enabled = enabled;
        this.legacy = new ConversationView(act, host);
        this.composeView = enabled ? GlassAmbientKt.createGlassChatView(act, state, this) : null;
        // [V3.50] 节流用到的两个 Runnable 在**构造函数**里建（字段初始化式里引用 final 字段编译不过）
        this.mirrorFlush = this::mirrorFlushNow;
        this.pendingVisPull = this::pullVisibleNow;
        // [V3.24] 构造即铺一次 chip 行：`权限` 这枚与旧视图一样**从一开始就在**（否则在权限帧回来前，
        // 用户根本点不到权限面板）。其余 chip 仍由各自的 setXxxLabel 追加。
        rebuildChips();
    }

    /** 挂到 `MainActivity.setContent(...)` 的那个 View（开关关掉时就是旧视图）。 */
    public View view() {
        return enabled && composeView != null ? composeView : legacy;
    }

    public ConversationView legacy() { return legacy; }
    public GlassChatState state() { return state; }

    /**
     * [V3.24 · 交接 §6 第 3/6 条] 用**玻璃骨架的「列表」形态**弹一块面板
     * （选择模型 / 思考等级 / 权限 / 项目 / 用量 / 生成物 / 任务 …）。
     *
     * <p>这些面板原来都是 `ui/ModelSheet` 之类的**旧 Java 不透明白面板**，与本 App 玻璃语言不一致；
     * 骨架加了列表形态后，宿主可以逐个搬过来。**开关关掉时返回 false**，调用方回退旧面板。
     *
     * @param onPick 选中回调（下标对应 options），返回 `kotlin.Unit.INSTANCE` 即可
     */
    public boolean showSheetList(String tag, String title, String message,
                                 java.util.List<com.dsh.mobile.glass.GlassSheetOption> options,
                                 String closeLabel,
                                 kotlin.jvm.functions.Function1<Integer, kotlin.Unit> onPick) {
        return showSheetList(tag, title, message, options, closeLabel, onPick, null);
    }

    /**
     * 同上，另带**长按**回调（「生成物」那一行：点=应用内预览、长按=下载/复制路径）。
     * `onLongPick` 为 null 时行为与上面那个完全一致。
     */
    /**
     * [V3.113 · 用户「粘贴配对串点开**跑到对话界面、成透明的了**；首页修改名称也**跑到对话位置**弹了」]
     *
     * 玻璃 sheet 的**前置守卫**：必须至少有一层**正在绘制的**取样层，否则面板采样一个空层 ⇒ **透明** ✗。
     *
     * <p>两层来源：
     * ① 聊天页的 `GlassChatBackdrop`（聊天页被组合时有效；切走时会 `onDispose` 清空）；
     * ② 抽屉的 `GlassDrawerBackdrop`（抽屉内容被组合时有效）。
     * 两者都拿不到（例如停在设置页且抽屉也没绘制）⇒ **返回 false**，调用方回退旧弹窗 —— 宁可弹一个
     * 旧的实心对话框，也不要弹一块透明玻璃 ✗。
     *
     * <p>[V3.114 · 自审修正] ⚠️ **只认聊天页那层**：这个类写的是**聊天页的状态**，而它只由聊天页自己渲染
     * （抽屉宿主从 V3.114 起只渲染**抽屉自己的状态**）。所以"抽屉层存在"对这里的 sheet **毫无帮助** ——
     * 认了它反而会让设置页/设备页的弹窗被写进没人渲染的状态 ⇒ **什么都不出现** ✗。
     */
    private boolean sheetBackdropReady() {
        return GlassChatBackdrop.INSTANCE.getLayer() != null;
    }

    public boolean showSheetList(String tag, String title, String message,
                                 java.util.List<com.dsh.mobile.glass.GlassSheetOption> options,
                                 String closeLabel,
                                 kotlin.jvm.functions.Function1<Integer, kotlin.Unit> onPick,
                                 kotlin.jvm.functions.Function1<Integer, kotlin.Unit> onLongPick) {
        if (!enabled || !sheetBackdropReady()) return false;
        state.getSheet().showList(tag, title, message, options, closeLabel, onPick, onLongPick);
        return true;
    }

    /** [V3.103 · 方案 A] 把弹窗状态交给**独立的全屏弹窗宿主**（由 MainActivity 加进 DrawerHost）。 */
    public com.dsh.mobile.glass.GlassSheetState sheetState() {
        return state.getSheet();
    }

    /** [V3.101] 弹窗关闭后回调（宿主用它还原 Activity 的视图层级）。 */
    public void setSheetHideCallback(Runnable r) {
        if (!enabled) return;
        state.getSheet().setOnHide(r == null ? null : () -> {
            r.run();
            return kotlin.Unit.INSTANCE;
        });
    }

    /** [V3.24] 就地改列表某一行的副文案（下载进度那条"正在下载 42%"）。 */
    public void updateSheetOptionSub(int index, String sub) {
        if (!enabled) return;
        state.getSheet().updateOptionSub(index, sub);
    }

    /**
     * [V3.96 · 用户「修复后的待发送玻璃弹窗，点中间的**修改**之后**还是普通弹窗**」
     *   +「能不能配置个**制作规范**：弹窗必须统一成玻璃弹窗风格」]
     *
     * 玻璃**表单**形态（标题 + 说明 + N 个输入框 + 主按钮）—— 本骨架早就有
     * （`GlassSheetState#showForm`），这里补一个 Java 入口，好让 `MainActivity` 里那些
     * "旧 AlertDialog 输入框"一条条迁过来（见 `docs/GlassUI/GlassUI_SPEC_DIALOG.md` 的迁移清单）。
     *
     * @param onSubmit 返回 null/空串 = 成功（自动收起）；返回文案 = 内联报错并**保持打开**
     */
    public boolean showSheetForm(String tag, String title, String message,
                                 java.util.List<com.dsh.mobile.glass.GlassSheetField> fields,
                                 String submitLabel,
                                 kotlin.jvm.functions.Function1<java.util.List<String>, String> onSubmit) {
        if (!enabled || !sheetBackdropReady()) return false;
        state.getSheet().showForm(tag, title, message, fields, submitLabel, onSubmit);
        return true;
    }

    /**
     * [V3.110 · 用户「弹窗**一会是玻璃的，一会又变成半透明了**」= 迁移到一半]
     *
     * 玻璃**确认**形态的 drop-in：骨架里"主按钮"走的是 `onPick(0)`（见 `GlassSheet.kt` 的确认渲染），
     * 所以这里挂上 `onListPick` 即可，调用方只需给一个 `Runnable`。
     */
    public boolean showSheetConfirm(String tag, String title, String message,
                                    String confirmLabel, boolean danger, Runnable onConfirm) {
        if (!enabled || !sheetBackdropReady()) return false;
        state.getSheet().showConfirm(tag, title, message, confirmLabel, danger);
        state.getSheet().setOnListPick(ignored -> {
            if (onConfirm != null) onConfirm.run();
            return kotlin.Unit.INSTANCE;
        });
        return true;
    }

    /** [V3.24] 整套替换列表内容（「用量」面板收到新帧后就地刷新）。 */    public void setSheetOptions(java.util.List<com.dsh.mobile.glass.GlassSheetOption> opts) {
        if (!enabled) return;
        state.getSheet().setListOptions(opts);
    }

    /** [V3.24] 当前弹窗是否正是某个形态且展开着（用于判断"要不要就地刷新"）。 */
    public boolean isSheetTagVisible(String tag) {
        return enabled && state.getSheet().getVisible() && tag.equals(state.getSheet().getTag());
    }

    /**
     * [V3.25 · 交接 §6 第 3 条③] 用**玻璃骨架的「菜单」形态**弹一块面板（加号 → 添加附件：文件/相册/拍照）。
     *
     * <p>骨架的菜单形态本来就没有回调入口（设备屏是走 host 的 `sheetPick`），这里**借 `onListPick`**
     * 承载选中回调；`GlassChatScreen` 的 overlay 会在回调后**自动收起**（旧 AttachSheet 同款行为：
     * 先收面板再打开相机/选择器）。
     */
    public boolean showSheetMenu(String tag, String title,
                                 java.util.List<com.dsh.mobile.glass.GlassSheetItem> items,
                                 kotlin.jvm.functions.Function1<Integer, kotlin.Unit> onPick) {
        if (!enabled) return false;
        state.getSheet().showMenu(tag, title, items);
        state.getSheet().setOnListPick(onPick);   // 必须在 showMenu 之后：showMenu 内部会 clearForm()
        return true;
    }

    // ------------------------------------------------------------ 数据镜像

    /**
     * 简洁/完整模式的**可见行**由旧视图的 `applyFilter()` 算（延迟 60ms），
     * 所以先镜像原始列表让 UI 立刻更新，再延迟把"过滤后的可见行"捞过来覆盖。
     *
     * <p>[V3.50 · 真机报「**消息收到卡卡的、流式吐字一顿一顿**」] 原来有**两处**会随每个 token 放大：
     * <ol>
     *   <li>{@link #setItems} 里**每次调用都** `state.setItems` 一次（立刻）+ 再 `postDelayed(120ms)`
     *       拉一次过滤结果 ⇒ 每个 token **两次全表状态更新**（两次重组 + 两次重绘）；</li>
     *   <li>那个 120ms 的延时任务**每次都新 post 一个**、不取消旧的 ⇒ 吐字期间会**堆积**一串待执行任务，
     *       每个又各写一次状态。</li>
     * </ol>
     * 修法：① 立刻那次改成**节流**（`MIRROR_THROTTLE_MS`，末尾必发一次，不丢最后一帧）；
     * ② 延时拉取改成**只保留一个待执行任务**。
     */
    private static final long MIRROR_THROTTLE_MS = 70L;

    private List<ChatItem> pendingMirror;
    /** [V3.57] 最近一次"原始列表"（不是过滤结果）；用于判定"过滤结果为空"是不是假空。 */
    private List<ChatItem> lastRawMirror = new ArrayList<>();
    private boolean mirrorScheduled;
    /**
     * [V3.62 · **真机"所有会话全空"的最终根因与修复**]
     *
     * ⚠️ 延时任务**必须挂主线程 Handler，不能挂 `legacy`**：玻璃模式下真正在窗口里的是 Compose 视图，
     * 旧视图 `legacy` **从未被 attach** —— `View.postDelayed` 对未挂载的 View 只是把任务塞进它自己的
     * RunQueue，**要等挂载才执行** ⇒ 那些任务**永远不跑**。
     *
     * 真机证据链（手机屏上读数，非推测）：
     * <pre>
     *   snap#1 ev=57 itemsN=17   ⇒ App **成功映射出 17 条消息**（映射没问题）
     *   而 Compose 侧一直 items=0 raw=0 ⇒ 这 17 条**从没写进 UI 状态**
     * </pre>
     * 原因：V3.50 把"立刻写状态"从**直接调用**改成 `legacy.postDelayed(...)` ⇒ 永不执行；
     * V3.58 的"假空保护"又把 pull 那条路 `return` 掉 ⇒ 状态永远是 0。
     * （V3.57 我曾怀疑过这一点、但当时"只加保护就好了"⇒ 误判为不必要而回退 —— 是错的，现在坐实。）
     */
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    /** ⚠️ 必须在**构造函数里**建：Java 不允许"字段初始化式的 lambda"引用尚未确定的 final 字段。 */
    private final Runnable mirrorFlush;
    private final Runnable pendingVisPull;

    private void mirrorFlushNow() {
        mirrorScheduled = false;
        List<ChatItem> v = pendingMirror;
        pendingMirror = null;
        if (v != null && enabled) {
            state.setItems(v);
            // [V3.57 诊断 · 验完即删]
            state.setDebugRaw(lastRawMirror.size());
            state.setDebugVis(legacy.visibleItems().size());
        }
    }

    /**
     * [V3.57 · 真机 bug「**所有会话全空、且没有任何报错**」的修复]
     *
     * <p>⚠️ 这里原来会**无条件**用"旧视图过滤后的可见行"覆盖 Compose 状态：
     * <pre>
     *   state.setItems(new ArrayList&lt;&gt;(legacy.visibleItems()));
     * </pre>
     * 一旦过滤引擎（旧视图的 `applyFilter`，60ms 延时）**还没算出结果**、或某些条件下返回空表，
     * 这一行就会把**整屏消息抹掉**（用户看到的就是"全空 + 无任何提示"）。
     *
     * <p>真机取证（V3.57）：同一台机器、同一个会话，把 `USE_GLASS_CHAT` 切回旧视图 ⇒ **消息全在**
     * （`uiautomator dump` 能看到全部消息文本）⇒ 数据是好的，**是这一步把状态覆盖空了**。
     *
     * <p>修法：**过滤结果为空、而原始列表非空 ⇒ 丢弃这次空结果**（保留原始列表）；
     * 过滤结果非空才覆盖（简洁模式该有的过滤照旧生效）。
     */
    private void pullVisibleNow() {
        if (!enabled) return;
        List<ChatItem> v = legacy.visibleItems();
        if (v.isEmpty() && !lastRawMirror.isEmpty()) return;
        pendingMirror = new ArrayList<>(v);
        // 过滤结果**立刻**上屏（不再等节流），否则吐字末尾会慢一拍
        ui.removeCallbacks(mirrorFlush);
        mirrorFlushNow();
    }

    private void pullVisibleLater() {
        if (!enabled) return;
        ui.removeCallbacks(pendingVisPull);          // [V3.50] 只保留一个待执行（原来会堆积）
        ui.postDelayed(pendingVisPull, 120L);        // [V3.62] 主线程 Handler（`legacy` 未挂载 ⇒ 挂它会永不执行）
    }

    private void rebuildChips() {
        List<String> c = new ArrayList<>();
        // [V3.24] 顺序与旧视图**逐项对齐**：项目 / 模型 / 权限 / 生成物 / 思考 / 用量 / 任务。
        //   其中「权限」和「生成物」在旧视图里是**构造时就固定存在**的（不是等数据才出现），
        //   漏了它们会出现"少一枚 chip、对应面板点不到"的差异（权限那枚就是这么漏的）。
        if (!chipProject.isEmpty()) c.add("项目 · " + chipProject);
        if (!chipModel.isEmpty()) c.add("模型 · " + chipModel);
        c.add(chipPermission.isEmpty() ? "权限" : chipPermission);
        c.add("生成物");
        if (!chipEffort.isEmpty()) c.add("思考 · " + chipEffort);
        if (!chipUsage.isEmpty()) c.add("用量 · " + chipUsage);
        if (!chipTasks.isEmpty()) c.add(chipTasks);
        state.setChips(c);
    }

    // ------------------------------------------------------------ 与 ConversationView 同名的方法

    public void setItems(List<ChatItem> items) {
        legacy.setItems(items);          // 旧视图照旧**每次都喂**（它才是"简洁模式"的过滤引擎）
        if (!enabled) return;
        // [V3.50] 立刻那次镜像改成**节流**（原来是每个 token 都整表刷新一次）
        pendingMirror = items == null ? new ArrayList<ChatItem>() : new ArrayList<>(items);
        lastRawMirror = pendingMirror;   // [V3.57] 记下"原始列表"，给 pullVisibleNow 判定"假空"用
        if (!mirrorScheduled) {
            mirrorScheduled = true;
            ui.postDelayed(mirrorFlush, MIRROR_THROTTLE_MS);   // [V3.62] 主线程 Handler（见字段处说明）
        }
        pullVisibleLater();
    }

    public void setTitleText(String t) {
        legacy.setTitleText(t);
        state.setTitle(t == null ? "" : t);
    }

    public void setSubtitleText(String t) {
        legacy.setSubtitleText(t);
        state.setSubtitle(t == null ? "" : t);
    }

    public void setRunning(boolean value, String hint) {
        legacy.setRunning(value, hint);
        state.setRunning(value);
        state.setRunningHint(hint == null ? "" : hint);
    }

    public void setBanner(String text, boolean error) {
        legacy.setBanner(text, error);
        state.setBanner(text == null ? "" : text);
        state.setBannerError(error);
    }

    public void setBanner(String text, boolean error, boolean actionable, Runnable onClick) {
        legacy.setBanner(text, error, actionable, onClick);
        state.setBanner(text == null ? "" : text);
        state.setBannerError(error);
    }

    public void setPlan(String text) {
        legacy.setPlan(text);
        state.setPlanGoal(text == null ? "" : text);
    }

    public void setPlanRich(String goal, List<String[]> todos) {
        legacy.setPlanRich(goal, todos);
        state.setPlanGoal(goal == null ? "" : goal);
        List<kotlin.Pair<String, String>> rows = new ArrayList<>();
        if (todos != null) {
            for (String[] r : todos) {
                if (r == null) continue;
                String text = r.length > 0 && r[0] != null ? r[0] : "";
                String st = r.length > 1 && r[1] != null ? r[1] : "";
                rows.add(new kotlin.Pair<>(text, st));
            }
        }
        state.setPlanTodos(rows);
    }

    public void setTasksProgress(int done, int total) {
        legacy.setTasksProgress(done, total);
        state.setPlanProgress(done + "/" + total);
        // [V3.24] 与旧视图同口径：total≤0 ⇒ 整枚隐藏（不加这枚 chip）
        chipTasks = total > 0 ? ("任务 · " + done + "/" + total) : "";
        rebuildChips();
    }

    public void setSubagentEntry(int count, boolean inChild) {
        legacy.setSubagentEntry(count, inChild);
        state.setSubagentCount(count);
    }

    public void setMoreStatus(String text, boolean error) {
        legacy.setMoreStatus(text, error);
        state.setMoreStatus(text == null ? "" : text);
        state.setMoreStatusError(error);
    }

    public void setReadOnly(boolean value, String reason) {
        legacy.setReadOnly(value, reason);
        state.setReadOnly(value);
        state.setReadOnlyReason(reason == null ? "" : reason);
    }

    /** 正式版这是「别的会话有待办 · 点这里过去」那条可点横幅；Compose 侧暂用同一根 banner 承载。 */
    public void setPendingBanner(String text) {
        legacy.setPendingBanner(text);
        state.setBanner(text == null ? "" : text);
        state.setBannerError(false);
    }

    public void setPendingItems(List<String[]> items) {
        legacy.setPendingItems(items);
        // [V3.85 · 用户「待发送的内容手机端现在显示的是一串**字符**，不是输入的内容」]
        //   ⚠️ 每项是 `{itemId, text, placement}`（见 `ConversationView#setPendingItems` 的契约），
        //   原来取的是 **a[0] = itemId** ⇒ Compose 侧显示的就是一串 ID 字符 ✗。这里改成取 **a[1] = 文本**，
        //   并把 itemId 一起编码进去（点击那条要用它调 `onQueueItemAction`）。
        List<String> texts = new ArrayList<>();
        if (items != null) {
            for (String[] a : items) {
                if (a == null) continue;
                String id = a.length > 0 && a[0] != null ? a[0] : "";
                String text = a.length > 1 && a[1] != null ? a[1] : "";
                texts.add(id + "\u0001" + text);
            }
        }
        state.setPendingSends(texts);
    }

    public void addPendingSend(String text) {
        legacy.addPendingSend(text);
        List<String> cur = new ArrayList<>(state.getPendingSends());
        // [V3.85] 与 `setPendingItems` 同一编码（`itemId\u0001text`）；本入口没有 itemId ⇒ 留空。
        cur.add("\u0001" + (text == null ? "" : text));
        state.setPendingSends(cur);
    }

    public void clearPendingSends() {
        legacy.clearPendingSends();
        state.setPendingSends(new ArrayList<String>());
    }

    /** [V3.78] 最新一份待发附件（供附件条的 × 按下标取回对象）。 */
    private List<PendingAttachment> lastAttachments = new ArrayList<>();

    public void setPendingAttachments(List<PendingAttachment> list) {
        legacy.setPendingAttachments(list);
        // [V3.78] 留一份最新列表：附件条上的 × 需要按**下标**换回真正的 `PendingAttachment`
        //   （Compsoe 侧只拿得到编码后的字符串列表，而宿主接口 `onRemoveAttachment` 收的是对象）。
        lastAttachments = list == null ? new ArrayList<>() : new ArrayList<>(list);
        // [V3.75] 镜像**列表**（原来只镜像个数 ⇒ 界面无从渲染 ⇒ 用户"选完没反应"）。
        //   每项编码 `kind\u0001uri\u0001name`（图片显缩略图、文件显名字）。
        List<String> enc = new ArrayList<>();
        if (list != null) {
            for (PendingAttachment a : list) {
                if (a == null) continue;
                String uri = a.uri == null ? "" : a.uri.toString();
                if (uri.isEmpty() && a.localPath != null && !a.localPath.isEmpty()) {
                    uri = "file://" + a.localPath;
                }
                enc.add(a.kind + "\u0001" + uri + "\u0001" + a.name);
            }
        }
        state.setPendingAttachments(enc);
    }

    public void setPermissionChip(String label) {
        legacy.setPermissionChip(label);
        // [V3.23] 显示文案必须与旧视图一致：ConversationView 自己会拼「权限 · 」前缀，
        // 而传进来的 label 是**裸值**（如「完全访问」）⇒ Compose 侧 chip 原来显示成「完全访问」，
        // 既少前缀、也让 chipTap 的前缀路由失配。这里补回同一套拼法。
        chipPermission = (label == null || label.trim().isEmpty())
                ? "权限" : ("权限 · " + label.trim());
        rebuildChips();
    }

    public void setModelLabel(String label) {
        legacy.setModelLabel(label);
        chipModel = label == null ? "" : label;
        rebuildChips();
    }

    public void setEffortLabel(String label) {
        legacy.setEffortLabel(label);
        chipEffort = label == null ? "" : label;
        rebuildChips();
    }

    public void setUsageLabel(String label) {
        legacy.setUsageLabel(label);
        chipUsage = label == null ? "" : label;
        rebuildChips();
    }

    public void setProjectLabel(String name, String fullPath) {
        legacy.setProjectLabel(name, fullPath);
        chipProject = name == null ? "" : name;
        rebuildChips();
    }

    public void setCompact(boolean value) {
        legacy.setCompact(value);
        state.setCompact(value);     // [V3.81] 镜像给 Compose：完整=思考/工具默认展开，精简=默认收起
        pullVisibleLater();          // 模式切换会改变可见行（STEP 摘要行）
    }

    public void setDraft(String s) {
        legacy.setDraft(s);
        state.setDraft(s == null ? "" : s);
    }

    public void applyTheme() {
        legacy.applyTheme();
        state.bumpTheme();
    }

    public void onConfigChanged() {
        legacy.onConfigChanged();
    }

    public void onWindowInsetsChanged() {
        legacy.onWindowInsetsChanged();
    }

    public String draftText() { return legacy.draftText(); }

    public boolean hasDraft() { return legacy.hasDraft(); }

    public void focusInput() { legacy.focusInput(); }

    public void refresh() {
        legacy.refresh();
        pullVisibleLater();
    }

    public void refreshNow() {
        legacy.refreshNow();
        pullVisibleLater();
        state.bumpScrollBottom();
    }

    public void scrollToBottom() {
        legacy.scrollToBottom();
        state.bumpScrollBottom();
    }

    /** 跳到某条消息：旧视图返回"找没找到"；Compose 侧的定位暂未接管（见报告待办）。 */
    public boolean scrollToKey(String key) { return legacy.scrollToKey(key); }

    public String planFullText() { return legacy.planFullText(); }

    public String projectPath() { return legacy.projectPath(); }

    // ------------------------------------------------------------ GlassChatHost：全部转发真实流程

    @Override public void send(String text) { host.onSend(text); }

    /** 草稿是双向的：Compose 输入框的每次改动都要回写旧视图，`MainActivity` 的发送/语音流程读的是它。 */
    @Override public void draftChanged(String text) { legacy.setDraft(text); }

    @Override public void stopTurn() { host.onStopTurn(); }
    @Override public void attachPick() { host.onAttachPick(); }
    @Override public void openTasks() { host.onOpenTasks(); }
    @Override public void menu() { host.onMenu(); }
    @Override public void openSubagents() { host.onOpenSubagents(); }
    /**
     * [V3.23 · 交接 §6 第 3 条①] 能力 chip 点击 → 按**显示文案前缀**路由到正式版原有的面板。
     *
     * <p>文案由 {@link #rebuildChips()} 统一拼（前缀 + 「 · 」+ 值），故按前缀匹配即可；
     * 对不上任何前缀就什么都不做（绝不误触发别的流程）。
     */
    @Override public void chipTap(String label) {
        String l = label == null ? "" : label;
        if (l.startsWith("项目")) host.onProjectTap();
        else if (l.startsWith("模型")) host.onPickModel();
        else if (l.startsWith("权限")) host.onPickPermission();
        else if (l.startsWith("生成物")) host.onArtifacts();
        else if (l.startsWith("思考")) host.onPickEffort();
        else if (l.startsWith("用量")) host.onUsageTap();
        else if (l.startsWith("任务")) host.onTasksTap();
    }
    @Override public void loadMore() { host.onLoadMore(); }
    @Override public void togglePlan() { host.onOpenPlan(); }
    @Override public void approve(ChatItem item, String outcome) { host.onApprove(item, outcome); }
    @Override public void questionSubmit(ChatItem item, JSONArray answers) { host.onQuestionSubmit(item, answers); }
    @Override public void downloadFile(ChatItem item, String path) { host.onDownloadFile(item, path); }
    // [V3.76] 交付物：点击 = 先打开预览（宿主落私有缓存 + 打开 `ArtifactActivity`，页内有「保存」）
    @Override public void previewFile(ChatItem item, String path) { host.onPreviewFile(item, path); }
    // [V3.78] 附件条的 ×：按下标取回对象再交给宿主（宿主 `MainActivity.onRemoveAttachment` 早已实现）
    @Override public void removeAttachment(int index) {
        if (index < 0 || index >= lastAttachments.size()) return;
        host.onRemoveAttachment(lastAttachments.get(index));
    }
    @Override public void copyPath(String path) { host.onCopyPath(path); }
    /**
     * [V3.82 · 用户纠正]「**我让你改的是对话界面长按复制**」—— 长按一条对话消息就复制它的文本。
     *
     * <p>直接在视图里做（不绕宿主接口）：剪贴板是纯本地能力，宿主那一层没有额外语义要加。
     */
    /** [V3.89] 消息里的文件链接 ⇒ 打开预览（相对路径由宿主兜底）。 */
    @Override public void openPath(String path, String label) {
        host.onOpenPath(path == null ? "" : path, label == null ? "" : label);
    }

    /** [V3.85] 「待发送」条点一条 ⇒ 宿主弹「立即插入 / 修改 / 删除」。 */
    @Override public void queueItemAction(String itemId, String text) {
        host.onQueueItemAction(itemId == null ? "" : itemId, text == null ? "" : text);
    }

    @Override public void copyText(String text) {        if (text == null || text.isEmpty()) return;
        android.content.Context ctx = legacy == null ? null : legacy.getContext();
        if (ctx == null) return;
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("text", text));
            android.widget.Toast.makeText(ctx, "已复制这条消息", android.widget.Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) { }
    }
}
