package com.dsh.mobile.glass

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.dsh.mobile.Store
import com.dsh.mobile.ui.Theme
import com.dsh.mobile.ui.Ui

/**
 * 【设置页 · 独立切片入口】UI 本体在 `GlassSettings.kt`（可被宿主驱动的那一版），
 * 本文件只做"自己当宿主"的装配：真值从 `Store` 读、能自己写的设置**真写**，
 * 需要宿主（连接/扫码/更新/反馈）的动作**明确提示**而不是假装生效。
 */
class GlassSettingsActivity : ComponentActivity() {
    /** [V3.52] 自定义底图：图片选择器的请求码。 */
    private val reqAmbientImage = 9001

    /** [V3.52] 选中图片 → 持久化授权 + 落库 + **立刻**换底（`GlassAmbientImage.uri` 是可观察状态）。 */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != reqAmbientImage || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { Store(this).setAmbientImage(uri.toString()) }
        GlassAmbientImage.uri = uri.toString()
        Toast.makeText(this, "已把这张图设为「配色底图」", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GlassDebug.forcePressed = intent?.getBooleanExtra("glass_force_press", false) ?: false
        val store = Store(this)
        Ui.applyTheme(Theme.resolveDark(this, store.themeMode()))
        // [V3.81] 字号档位也要在建视图前定下来（`Ui.S_*` 全局比例）
        Ui.applyFontScale(runCatching { store.fontScale() }.getOrDefault(1f))
        Ui.setAmbientPalette(store.ambientPalette())
        setContent { SettingsSlice(store) }
    }
}

@Composable
private fun SettingsSlice(store: Store) {
    val ctx = LocalContext.current
    val state = remember { GlassSettingsState() }
    // [V3.26 · 用户 #5] 滚动状态建在 `key(themeRev)` **之外** ⇒ 换主题/配色时不再弹回顶部
    val scroll = androidx.compose.foundation.rememberScrollState()

    remember {
        state.hasHost = false
        state.lan = runCatching { store.lanUrl() }.getOrDefault("")
        state.wan = runCatching { store.wanUrl() }.getOrDefault("")
        state.token = runCatching { store.token() }.getOrDefault("")
        state.deviceName = runCatching { store.deviceName() }.getOrDefault("")
        state.netMode = runCatching { store.netMode() }.getOrDefault("auto")
        state.themeMode = runCatching { store.themeMode() }.getOrDefault("system")
        state.fontScale = runCatching { store.fontScale() }.getOrDefault(1f)   // [V3.81] 字号档位
        state.palette = runCatching { store.ambientPalette() }.getOrDefault(0)
        state.glassLevel = runCatching { store.glassLevel() }.getOrDefault(1)
        state.glassMode = runCatching { store.glassMode() }.getOrDefault("liquid")   // [V3.27] · [V3.52] 默认液态
        state.displayMode = runCatching { store.displayMode() }.getOrDefault("full")
        state.allowScreenshot = runCatching { store.allowScreenshot() }.getOrDefault(true)
        state.insecureTls = runCatching { store.insecureTls() }.getOrDefault(false)
        state.status = "独立切片：连接类动作仅提示，真正保存/重连在正式版宿主里"
        state.about = "DSH 掌上通 · 与电脑端 DSH 直连\n数据只在你的设备之间流转"
    }

    // [V3.26 · 用户 #5「配色调整不立马变化」「点外观内部按钮画面会弹到顶部」]
    //   **两条同一个根因**：原来 `rebuild()` 用的是 `recreate()`（重建整个 Activity）——
    //   配色要等重建后才生效（看起来不即时），而重建必然丢掉滚动位置（弹回顶部）。
    //   改成"就地重组"：`bumpTheme()` 让 `key(themeRev)` 换 key ⇒ `GlassAppTheme` 重新按新配色
    //   构建主题、整页重组（立即生效）；滚动状态在 key 之外 ⇒ 不跳顶。
    fun rebuild() { state.bumpTheme() }

    val host = remember {
        object : GlassSettingsHost {
            override fun back() { (ctx as? android.app.Activity)?.finish() }
            override fun scanQr() { toast(ctx, "扫码配对在正式版里（切片未接管）") }
            override fun pastePairing() { toast(ctx, "粘贴配对在正式版里（切片未接管）") }
            override fun disconnect() { toast(ctx, "断开连接在正式版里（切片未接管）") }
            override fun connect(lan: String, wan: String, netMode: String, token: String, deviceName: String) {
                toast(ctx, "保存并连接要宿主才能真的连（切片只渲染）")
            }
            override fun toggleInsecureTls(on: Boolean) {
                runCatching { store.setInsecureTls(on) }
                toast(ctx, if (on) "已允许自签名证书（下次连接生效）" else "已关闭自签名证书放行")
            }
            override fun checkUpdate() { toast(ctx, "检查更新在正式版里（切片未接管）") }
            override fun openFeedback() { toast(ctx, "反馈在正式版里（切片未接管）") }
            override fun pickDisplayMode(mode: String) {
                runCatching { store.setDisplayMode(mode) }
                toast(ctx, if (mode == "compact") "已切到简洁模式" else "已切到完整模式")
            }
            override fun toggleAllowScreenshot(on: Boolean) {
                runCatching { store.setAllowScreenshot(on) }
                toast(ctx, if (on) "已允许截屏" else "已禁止截屏（App 内截图会变黑）")
            }
            override fun pickThemeMode(mode: String) {
                runCatching { store.setThemeMode(mode) }
                Ui.applyTheme(Theme.resolveDark(ctx, mode))
                rebuild()
            }
            // [V3.89] 字号档位现读现用（设置页高亮用）
            override fun fontScale(): Float = runCatching { store.fontScale() }.getOrDefault(1f)
            // [V3.81] 字号四档：落库 + 全局应用（`Ui.S_*` 全部按比例缩放）
            // [V3.83] 改用 `recreate()`：`Ui.S_*` 是**静态常量**，Compose 只在**组合时**读一次，
            //   就地 `rebuild()` 不能保证所有已组合的节点都重读 ⇒ 重建 Activity 才一处不漏
            //   （真机上用户报"选了不变"就是这个原因）。
            override fun pickFontScale(scale: Float) {
                runCatching { store.setFontScale(scale) }
                Ui.applyFontScale(runCatching { store.fontScale() }.getOrDefault(1f))
                (ctx as? android.app.Activity)?.recreate()            }
            override fun pickPalette(index: Int) {
                runCatching { store.setAmbientPalette(index) }
                Ui.setAmbientPalette(index)
                rebuild()
            }
            override fun pickGlassMode(mode: String) {   // [V3.27] 立即落库 + 切 Ui + 就地重组
                runCatching { store.setGlassMode(mode) }
                Ui.setGlassMode(mode)
                rebuild()
                toast(ctx, if (Ui.isLiquidGlass()) "已切「液态玻璃」（带折射，更费）" else "已切「磨砂玻璃」（无折射，省资源）")
            }
            // [V3.52 · 用户「配色增加一个选项：**自己上传图片**当底配色」]（切片版：与"连接类动作"一致，只提示）
            override fun pickAmbientImage() {
                toast(ctx, "上传底图需要宿主的图片选择器（切片未接管；正式版里会拉起系统相册）")
            }
            override fun setGlassLevel(level: Int) {                runCatching { store.setGlassLevel(level) }
                toast(ctx, "磨砂度已存（对旧 Frost 引擎生效；Compose 玻璃用固定 6dp）")
            }

            // [V3.26 · 用户 #6] 日志页（切片版）：灌入切片能拿到的真实信息 + 累积报错
            override fun openLog() {
                val t = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date())
                val out = ArrayList<String>()
                out.add("—— DSH 掌上通 · 日志快照（独立切片）——")
                out.add("时间：$t")
                out.add("主题=${state.themeMode}  配色=${state.palette}  磨砂度=${state.glassLevel}")
                out.add("连接方式=${state.netMode}  内网=${state.lan.ifEmpty { "（空）" }}  公网=${state.wan.ifEmpty { "（空）" }}"
                    + "  令牌=" + if (state.token.isEmpty()) "（未配对）" else "已配对(${state.token.length} 字符，不记明文)")
                out.add("显示模式=${state.displayMode}  截屏白名单=${state.allowScreenshot}  自签证书=${state.insecureTls}")
                if (state.status.isNotEmpty()) out.add("状态：${state.status}")
                val errs = state.errSnapshot()
                if (errs.isNotEmpty()) { out.add("—— 故障 / 报错（${errs.size} 条）——"); out.addAll(errs) }
                out.add("（切片：连接类动作只提示；正式版里这一页会带宿主的完整事件轨迹）")
                state.logSet(out)
                state.logOpen = true
            }
        }
    }

    // [V3.26 #5] `key(state.themeRev)`：换配色/主题时重建主题包装（立即生效）；scroll 在 key 外
    GlassAppTheme(state.themeRev) { GlassSettingsScreen(state, host, scrollState = scroll) }
}

private fun toast(ctx: android.content.Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}
