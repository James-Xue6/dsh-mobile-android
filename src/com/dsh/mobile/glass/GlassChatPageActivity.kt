package com.dsh.mobile.glass

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.dsh.mobile.Store
import com.dsh.mobile.model.ChatItem
import com.dsh.mobile.ui.Theme
import com.dsh.mobile.ui.Ui
import org.json.JSONArray
import org.json.JSONObject

/**
 * 【对话页 · 独立切片页】逐页替换第 2 页的独立入口（桌面「掌上通·新界面」里能走到）。
 *
 * V3.3 起这一屏的 UI 本体搬到 `GlassChatScreen.kt`（可被宿主驱动的那一版），本文件只做两件事：
 * 1. **独立宿主的装配**：自己拼一份演示数据（但用的是**真的 [ChatItem] 对象**，走的就是正式版那条渲染路径）、
 *    自己实现 [GlassChatHost]（动作一律明确提示，不假装）；
 * 2. 标题/副标题从 `Store` 读**真值**。
 *
 * ⚠️ 消息本身仍是**示范内容**：正式版消息来自 `MainActivity` 内存里的 `List<ChatItem>`（网关实时喂），
 * 进程级没有静态入口 ⇒ 独立切片拿不到。真消息要等「把这一屏挂进 MainActivity 宿主」（目标 ② 的下一步）。
 */
class GlassChatPageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GlassDebug.forcePressed = intent?.getBooleanExtra("glass_force_press", false) ?: false
        // [V3.33] 阴影精度 A/B 钩子：默认半分辨率（出货）；`--ez glass_shadow_fullres true` 走旧的全分辨率实现
        GlassDebug.shadowHalfRes = intent?.getBooleanExtra("glass_shadow_fullres", false) != true
        // [V3.44] 滚动中"跳过行阴影"的 A/B 钩子：`--ez glass_force_scrolling true` 把滚动标记强制置真
        GlassDebug.forceScrolling = intent?.getBooleanExtra("glass_force_scrolling", false) ?: false
        // [V3.46] 真机上让「↓ 回到底部」圆钮**一定渲染**（真机不吃注入手势，落地在底部时它本来不出现）
        GlassDebug.forceJumpButton = intent?.getBooleanExtra("glass_force_jump", false) ?: false
        // [V3.51] 真机上强制**展开目标卡的任务列表**（平时要点一下卡片；真机点不了）
        GlassDebug.forcePlanExpanded = intent?.getBooleanExtra("glass_force_plan", false) ?: false
        val store = Store(this)
        Ui.applyTheme(Theme.resolveDark(this, store.themeMode()))
        // [V3.81] 字号档位也要在建视图前定下来（`Ui.S_*` 全局比例）
        Ui.applyFontScale(runCatching { store.fontScale() }.getOrDefault(1f))
        Ui.setAmbientPalette(store.ambientPalette())
        setContent { GlassAppTheme { ChatSlice(store) } }
    }
}

@Composable
private fun ChatSlice(store: Store) {
    val ctx = LocalContext.current
    val state = remember { GlassChatState() }

    remember {
        val title = runCatching { store.cachedTitle(store.lastSessionId()) }.getOrDefault("")
        state.title = title.ifBlank { "对话" }
        state.subtitle = runCatching { store.routeLine() + " · " + store.routeSource() }.getOrDefault("未连接")
        state.subagentCount = 2                       // 演示值（正式版 N=0 时不显示）
        state.chips = listOf("权限：自动", "模型：演示", "思考：中")
        state.planGoal = "把对话页做成与设备页同一套液态玻璃质感"
        state.planExpanded = true
        state.planTodos = listOf(
            "取样层装「背景 + 滚动内容」" to "完成",
            "底部改三件式玻璃" to "完成",
            "9 类消息行接真实 ChatItem" to "本轮完成",
            "挂进 MainActivity 宿主" to "待做",
        )
        state.items = demoItems()
        state.banner = ""
    }

    val host = remember {
        object : GlassChatHost {
            override fun send(text: String) {
                toast(ctx, "对话未接网关：发送要等宿主接管（本轮只做渲染）")
            }
            override fun draftChanged(text: String) {
                // 独立切片里草稿的唯一来源就是 Compose 输入框，没有第二个读者，无需回写
            }
            override fun stopTurn() { toast(ctx, "停止回合（切片未接管）") }
            // [V3.76] 交付物：点击=先打开预览（正式版的预览页里有「保存」按钮）
            override fun previewFile(item: com.dsh.mobile.model.ChatItem, path: String) {
                toast(ctx, "打开预览（切片未接管）：${path.substringAfterLast('/')}")
            }
            // [V3.89] 消息里的文件链接：切片里只提示
            override fun openPath(path: String, label: String) {
                toast(ctx, "打开文件（切片未接管）：$label")
            }
            // [V3.85] 待发送条点一条（切片里只提示）
            override fun queueItemAction(itemId: String, text: String) {
                toast(ctx, "待发送菜单（演示）：立即插入 / 修改 / 删除 · ${text.take(10)}")
            }
            // [V3.82] 对话消息长按复制（切片里只提示）
            override fun copyText(text: String) {
                toast(ctx, "已复制这条消息（演示）：${text.take(12)}…")
            }
            override fun removeAttachment(index: Int) {
                state.pendingAttachments = state.pendingAttachments.filterIndexed { i, _ -> i != index }
                toast(ctx, "已移除第 ${index + 1} 个待发附件（演示）")
            }
            // [V3.75] 切片：塞两个"演示附件"用于验证**待发附件条**（真机里由宿主 `stageAttachment` 暂存）。
            override fun attachPick() {
                state.pendingAttachments = listOf(
                    "image\u0001\u0001演示图片.png",
                    "file\u0001\u0001设计稿.pdf",
                )
                toast(ctx, "已暂存 2 个附件（演示）—— 将随文字一起发送")
            }
            override fun openTasks() { toast(ctx, "左侧任务列表（抽屉页未接管）") }
            override fun menu() { toast(ctx, "更多（模型/权限/项目面板未接管）") }
            override fun openSubagents() { toast(ctx, "子智能体列表（切片未接管）") }
            override fun chipTap(label: String) { toast(ctx, "$label 面板（切片未接管）") }
            override fun loadMore() { toast(ctx, "加载更早（切片未接管）") }
            override fun togglePlan() { state.planExpanded = !state.planExpanded }
            override fun approve(item: ChatItem, outcome: String) {
                toast(ctx, "审批 ${if (outcome.startsWith("allow")) "允许" else "拒绝"}（切片未接网关）")
            }
            override fun questionSubmit(item: ChatItem, answers: JSONArray) {
                toast(ctx, "提交回答（切片未接网关）")
            }
            override fun downloadFile(item: ChatItem, path: String) {
                toast(ctx, "下载 $path（切片未接管）")
            }
            override fun copyPath(path: String) {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("path", path))
                toast(ctx, "已复制路径")
            }
        }
    }

    GlassChatScreen(state, host)
}

private fun toast(ctx: android.content.Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

/**
 * 演示数据 —— **刻意用真的 `ChatItem`**（而不是自己发明的行模型）：
 * 这样渲染路径与正式版完全一致，等宿主把真实 `List<ChatItem>` 推进来时不用改一行 UI。
 */
private fun demoItems(): List<ChatItem> {
    val now = System.currentTimeMillis()
    val out = ArrayList<ChatItem>()

    out += ChatItem.of(ChatItem.USER, "u1", "把对话页也做成液态玻璃，跟设备页同一套质感。")
        .apply { time = now - 10 * 60_000L }
    out += ChatItem.of(ChatItem.ASSISTANT, "a1",
        "现在顶栏和底部输入区都是**独立玻璃件**了；内容从它们下面滑过会被糊掉、边缘还会把线条推开。")
        .apply { time = now - 9 * 60_000L }

    // [V3.34] 追加两个回合：刻度尺的"方向/跳准/只选中一格/气泡标题行"**都需要 n>1** 才验得出来
    //   （原来演示数据只有 1 条 USER ⇒ n=1 ⇒ 这些项在模拟器上永远测不到）。
    out += ChatItem.of(ChatItem.USER, "u2", "侧面那条滚动条一直不对，按朋友的 MessageScrubber 对齐一下。")
        .apply { time = now - 8 * 60_000L }
    out += ChatItem.of(ChatItem.ASSISTANT, "a2", "好。我先把刻度居中、方向修对，再让气泡显示问题原文。")
        .apply { time = now - 7 * 60_000L }
    out += ChatItem.of(ChatItem.USER, "u3", "气泡别用全屏那么重，跟聊天气泡一样就行。")
        .apply { time = now - 6 * 60_000L }
    out += ChatItem.of(ChatItem.ASSISTANT, "a3", "收到，气泡 300dp、最多 8 行，跟参照一致。")
        .apply { time = now - 5 * 60_000L }

    // [V3.76] 追加一条**带 Markdown 结构**的消息：用于验收"标题/加粗/行内代码/列表/表格/引用/代码块"
    //   在**手机宽度下的排版**（用户报"结构部分显示太长"，两列表格已改成键值行）。
    out += ChatItem.of(ChatItem.ASSISTANT, "a4",
        "### 取样层次（两列键值）\n" +
            "| 层 | 内容 |\n|---|---|\n" +
            "| ① 取样层 | 环境背景 + 滚动内容（玻璃件在层外） |\n" +
            "| ② 悬浮层 | 顶栏 / chips / 输入区，取 `backdrop` |\n" +
            "\n" +
            "### 三列表格（走紧凑行）\n" +
            "| 参数 | 旧 | 新 |\n|---|---|---|\n" +
            "| 磨砂 | 6dp | 14dp |\n" +
            "| 体色 | 白 20% | 白 55% |\n" +
            "\n" +
            "- 无序项一：`RefractionCapsule` 参数与 chips 对齐\n" +
            "- 无序项二：**加粗**与行内代码混排\n" +
            "\n" +
            "1. 有序项：先看**排版**\n" +
            "2. 再看滑动是否受影响\n" +
            "\n" +
            "> 引用：目标是「关闭状态就是长胶囊，点开就是玻璃卡片」。\n" +
            "\n" +
            "```\n" +
            "fun topSafeDp(...)\n" +
            "```\n")
        .apply { time = now - 4 * 60_000L }

    // [V3.80] 加一张**交付物卡**：用于验收「打开 / 复制路径」两个明面按钮（用户报"长按复制没用"）。
    out += ChatItem.of(ChatItem.FILES, "f1", "").apply {
        files = org.json.JSONArray().put(
            org.json.JSONObject()
                .put("path", "dist/dsh-mobile.apk")
                .put("description", "安卓安装包（演示）")
        )
        time = now - 3 * 60_000L
    }

    out += ChatItem.of(ChatItem.ASSISTANT, "a5",
        "本轮报告已写好：报告 [GlassUI_Report_V3.86.md](docs/GlassUI/GlassUI_Report_V3.86.md)。" +
            "\n\n点下面的胶囊即可打开预览。")
        .apply { time = now - 2 * 60_000L }

    out += ChatItem.of(ChatItem.STEP, "s1", "重做对话页渲染 · 9 类消息行接真实 ChatItem")
        .apply { stepRunning = false; time = now }
    out += ChatItem.of(ChatItem.TOOL, "t1", "").apply {
        toolName = "grep · layerBackdrop"
        toolPreview = "取样层 = 背景 + 滚动内容；玻璃件留在层外（避免自取样）"
        time = now
    }
    out += ChatItem.of(ChatItem.AGENT, "g1", "顶栏与底部的取样已对齐，滚动时能看到形变。").apply {
        agentName = "frontend-developer"
        time = now
    }
    out += ChatItem.of(ChatItem.APPROVAL, "ap1", "这套三件式输入区（圆＋／长条／圆↑）可以吗？").apply {
        reason = "输入区形态确认"
        time = now
    }

    val q = JSONArray().put(
        JSONObject().put("id", "q1")
            .put("header", "下一步")
            .put("question", "先做哪个？")
            .put("multiSelect", false)
            .put("options", JSONArray()
                .put(JSONObject().put("label", "挂对话页进宿主").put("description", "接真实消息与全部回调"))
                .put(JSONObject().put("label", "继续做会话列表").put("description", ""))),
    )
    out += ChatItem.of(ChatItem.QUESTION, "q1_row", "需要你选一个方向").apply {
        questions = q
        time = now
    }

    val fs = JSONArray()
        .put(JSONObject().put("path", "docs/GlassUI/GlassUI_Report_V3.3.md").put("description", "本轮报告"))
        .put(JSONObject().put("path", "src/com/dsh/mobile/glass/GlassChatScreen.kt").put("description", "对话页可托管版"))
    out += ChatItem.of(ChatItem.FILES, "f1", "").apply { files = fs; time = now }
    out += ChatItem.of(ChatItem.SYSTEM, "sy1", "上下文已压缩（compact）").apply { time = now }
    return out
}
