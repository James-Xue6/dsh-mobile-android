package com.dsh.mobile.glass

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.dsh.mobile.Store
import com.dsh.mobile.model.SessionInfo
import com.dsh.mobile.model.WorkspaceGroup
import com.dsh.mobile.ui.Theme
import com.dsh.mobile.ui.Ui

/**
 * 【抽屉 / 会话列表 · 独立切片页】
 *
 * 为什么要有它：抽屉那一屏的数据来自 `MainActivity.buildRows()`（需要**网关 + 已配对设备**才有会话），
 * 所以模拟器上抽屉**永远是空的** ⇒ 两处 ⋯ 菜单、按压反馈、子会话缩进这些都**验不了**
 * （V3.33/V3.42 都卡在"需抽屉里有会话"）。本页用**真的 `WorkspaceGroup` / `SessionInfo` 对象**
 * 拼一份演示数据，走的就是正式版那条渲染路径。
 *
 * ⚠️ **刻意用纯 `android.app.Activity`**（不是 `ComponentActivity`）+ [GlassComposeHostOwner]：
 * 这与正式版 `MainActivity` **同构**。V3.31 那次真机闪退（dropbox 10 次 `ViewTreeLifecycleOwner not found`）
 * 只在"纯 Activity 上开 Compose 新窗口"时发生 ⇒ 本页是那条路径的**回归哨兵**：
 * 谁再往抽屉里用 Compose `Dialog`，跑这个页面就会当场崩。
 */
class GlassSessionPageActivity : android.app.Activity() {

    private lateinit var owner: GlassComposeHostOwner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GlassDebug.forcePressed = intent?.getBooleanExtra("glass_force_press", false) ?: false

        val store = Store(this)
        Ui.applyTheme(Theme.resolveDark(this, store.themeMode()))
        Ui.setAmbientPalette(store.ambientPalette())

        val state = GlassSessionState().apply {
            title = "对话"
            status = "演示数据 · 3 个会话/工作区（切片未接网关）"
            currentId = "s2"
            rows = demoRows()
        }

        val host = object : GlassSessionHost {
            override fun openSession(s: SessionInfo) = toast("打开会话 ${s.displayForList()}")
            override fun newChat() = toast("新建对话")
            override fun settings() = toast("设置")
            override fun devices() = toast("我的设备")
            override fun refresh() = toast("刷新")
            override fun rename(s: SessionInfo) = toast("重命名「${s.displayForList()}」")
            override fun archive(s: SessionInfo) = toast("归档「${s.displayForList()}」")
            override fun toggleChildren(s: SessionInfo) = toast("展开/收起子会话")
            override fun workspaceMenu(g: WorkspaceGroup) = toast("工作区菜单（应走树内浮层，不该调到这里）")
            override fun sessionMenu(s: SessionInfo) = toast("会话菜单（应走树内浮层，不该调到这里）")
            override fun newTaskHere(g: WorkspaceGroup) = toast("在「${g.label}」新建任务")
        }

        val view = androidx.compose.ui.platform.ComposeView(this).apply {
            setContent {
                @Suppress("UNUSED_EXPRESSION") state.themeRev
                GlassAppTheme(state.themeRev) {
                    // 抽屉面板的外壳（Java `DrawerHost` 的真玻璃）Compose 取样不到 ⇒
                    // 切片里用环境背景当"底"，内容仍是那套半透明卡（与正式版层级一致）。
                    Box(Modifier.fillMaxSize()) {
                        AmbientBackground(Modifier.fillMaxSize(), rev = state.themeRev)
                        GlassSessionListScreen(state, host, applyInsets = true)
                    }
                }
            }
        }
        owner = GlassComposeHostOwner()
        owner.install(view)          // 必须在 attach 之前挂三件套（否则 ComposeView 一挂上去就炸）
        setContentView(view)
    }

    override fun onResume() { super.onResume(); owner.resume() }
    override fun onStop() { owner.pause(); super.onStop() }
    override fun onDestroy() { owner.destroy(); super.onDestroy() }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}

/**
 * 演示数据 —— 用**真的** `WorkspaceGroup` / `SessionInfo`（渲染路径与正式版完全一致）：
 * 两个工作区 + 3 个会话 + 1 个子会话（验缩进）+ 一个"运行中"、一个"待处理 N"。
 */
private fun demoRows(): List<Any> {
    val out = ArrayList<Any>()
    out += WorkspaceGroup("程序开发", "F:\\AI\\程序开发", "ws-1", 3)
    out += demoSession("s1", "GlassUI 刻度尺对齐", running = true, childCount = 1)
    out += demoSession("s1c", "子会话：按朋友的 MessageScrubber 对齐", depth = 1)
    out += demoSession("s2", "修设置页顶栏玻璃感")
    out += demoSession("s3", "验证目标展示列表", pending = 2)
    out += WorkspaceGroup("default-workspace", "C:\\Users\\Administrator\\.dsh", "ws-2", 1)
    out += demoSession("s4", "Gitee 插件自动同步检查")
    return out
}

private fun demoSession(
    id: String,
    title: String,
    running: Boolean = false,
    pending: Int = 0,
    childCount: Int = 0,
    depth: Int = 0,
): SessionInfo = SessionInfo().apply {
    this.id = id
    this.title = title
    this.cwd = "F:\\AI\\程序开发\\dsh-mobile-android"
    this.running = running
    this.pending = pending
    this.childCount = childCount
    this.childDepth = depth
    this.updatedAt = System.currentTimeMillis() - (id.hashCode().toLong() and 0x3FFFFFFF)
}
