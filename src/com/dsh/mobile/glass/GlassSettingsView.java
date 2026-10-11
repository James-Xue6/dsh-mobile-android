package com.dsh.mobile.glass;

import android.app.Activity;
import android.view.View;

import com.dsh.mobile.ui.SettingsView;

/**
 * 【设置页双写宿主（V3.9 · ⑤ 收尾）】把 `MainActivity` 对设置页的调用**同时**喂给
 * 旧 `SettingsView` 与 Compose 版 [GlassSettingsScreen]。
 *
 * <p>`SettingsView` 是 **final** 类（不能继承覆写），且 `MainActivity` 对它有一整套调用
 * （applyTheme / setDisplayMode / setThemeMode / setPalette / setFields / setInsecureTls /
 * setAllowScreenshot / setAbout / setUpdateHint / setFeedbackHint / setDiagnostics /
 * setStatus / setNetMode / requestLayout）⇒ 本类**同名转发**，调用点一行不用改，只换字段类型与挂载点。
 *
 * <p>动作侧：本类实现 [GlassSettingsHost]，**全部转发给同一个 `SettingsView.Host`（就是 `MainActivity`）**
 * ⇒ 「保存并连接 / 扫码配对 / 粘贴配对 / 断开 / 检查更新 / 反馈 / 主题 / 配色 / 磨砂度 / 显示模式 / 截屏 / 自签证书」
 * 走的都是**原有真实流程**。
 */
public final class GlassSettingsView implements GlassSettingsHost {

    private final SettingsView legacy;
    private final SettingsView.Host host;
    private final GlassSettingsState state = new GlassSettingsState();
    private final boolean enabled;
    private final View composeView;

    public GlassSettingsView(Activity act, SettingsView.Host host, boolean enabled) {
        this.host = host;
        this.enabled = enabled;
        this.legacy = new SettingsView(act, host);
        this.composeView = enabled ? GlassAmbientKt.createGlassSettingsView(act, state, this) : null;
        state.setHasHost(true);
    }

    /** 挂到 `MainActivity.setContent(...)` 的那个 View（开关关掉时就是旧视图）。 */
    public View view() {
        return enabled && composeView != null ? composeView : legacy;
    }

    public SettingsView legacy() { return legacy; }
    public GlassSettingsState state() { return state; }

    // ------------------------------------------------------------ 与 SettingsView 同名的方法

    public void applyTheme() {
        legacy.applyTheme();
        state.bumpTheme();
    }

    public void setDisplayMode(String mode) {
        legacy.setDisplayMode(mode);
        state.setDisplayMode(mode == null ? "full" : mode);
    }

    /**
     * [V3.83 · 用户「选了小，进入设置看还是中」] 字号档位的**回显**：
     * 宿主启动/改档后把库里存的值推过来，设置页那段三分段才会高亮正确的那一档。
     */
    public void setFontScale(float scale) { state.setFontScale(scale); }

    public void setThemeMode(String mode) {
        legacy.setThemeMode(mode);
        state.setThemeMode(mode == null ? "system" : mode);
    }

    /** [V3.27] 宿主推「玻璃模式」给界面（与 setPalette 同向：宿主→界面）。 */
    public void setGlassMode(String mode) {
        state.setGlassMode(mode == null ? "liquid" : mode);
        state.bumpTheme();
    }

    public void setPalette(int index) {
        legacy.setPalette(index);
        state.setPalette(index);
        // [V3.27] 配色变了 ⇒ bump 主题版本，让 Compose 侧（背景 Drawable 等）**立即重建**
        state.bumpTheme();
    }

    public void setNetMode(String mode) {
        legacy.setNetMode(mode);
        state.setNetMode(mode == null ? "auto" : mode);
    }

    public void setFields(String lan, String wan, String token, String name, String netMode) {
        legacy.setFields(lan, wan, token, name, netMode);
        state.setLan(lan == null ? "" : lan);
        state.setWan(wan == null ? "" : wan);
        state.setToken(token == null ? "" : token);
        state.setDeviceName(name == null ? "" : name);
        state.setNetMode(netMode == null ? "auto" : netMode);
    }

    public void setInsecureTls(boolean on) {
        legacy.setInsecureTls(on);
        state.setInsecureTls(on);
    }

    public void setAllowScreenshot(boolean on) {
        legacy.setAllowScreenshot(on);
        state.setAllowScreenshot(on);
    }

    public void setAbout(String text) {
        legacy.setAbout(text);
        state.setAbout(text == null ? "" : text);
    }

    public void setUpdateHint(String text) {
        legacy.setUpdateHint(text);
        state.setUpdateHint(text == null ? "" : text);
    }

    public void setFeedbackHint(String text) {
        legacy.setFeedbackHint(text);
        state.setFeedbackHint(text == null ? "" : text);
    }

    public void setDiagnostics(String text) {
        legacy.setDiagnostics(text);
        state.setDiagnostics(text == null ? "" : text);
    }

    public void setStatus(String text, boolean error) {
        legacy.setStatus(text, error);
        state.setStatus(text == null ? "" : text);
        state.setStatusError(error);
        // [V3.26 · 用户 #6] 故障/报错**自动留痕**：以后出问题，日志页里能直接检索到
        if (error && text != null && !text.isEmpty()) state.logAdd("状态(错误)：" + text);
    }

    /**
     * [V3.26 · 用户 #6] 日志页：把"可检索的内容"灌进 `state.logLines` 再打开。
     *
     * <p>内容 = ① 快照头（时间/主题/配色/磨砂度）② 连接摘要（**令牌只记长度，不记明文**）
     * ③ 当前状态 ④ 宿主推进来的**诊断** ⑤ **累积的报错**（`logAdd` 进来的）。
     * 行由 Compose 侧 `LogOverlay` 渲染，支持「复制全部」。
     */
    @Override public void openLog() {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("—— DSH 掌上通 · 日志快照 ——");
        out.add("时间：" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .format(new java.util.Date()));
        out.add("主题=" + state.getThemeMode() + "  配色=" + state.getPalette()
                + "  磨砂度=" + state.getGlassLevel());
        out.add("连接方式=" + state.getNetMode()
                + "  内网=" + (state.getLan().isEmpty() ? "（空）" : state.getLan())
                + "  公网=" + (state.getWan().isEmpty() ? "（空）" : state.getWan())
                + "  令牌=" + (state.getToken().isEmpty()
                        ? "（未配对）" : ("已配对(" + state.getToken().length() + " 字符，不记明文)")));
        if (!state.getStatus().isEmpty()) {
            out.add("状态" + (state.getStatusError() ? "（错误）" : "") + "：" + state.getStatus());
        }
        if (!state.getDiagnostics().isEmpty()) {
            out.add("—— 诊断 ——");
            for (String l : state.getDiagnostics().split("\n")) out.add(l);
        }
        java.util.List<String> errs = state.errSnapshot();
        if (!errs.isEmpty()) {
            out.add("—— 故障 / 报错（" + errs.size() + " 条）——");
            out.addAll(errs);
        }
        state.logSet(out);
        state.setLogOpen(true);
    }

    /** 旧页用它重排（切主题整树重建后）——转发即可。 */
    public void requestLayout() {
        legacy.requestLayout();
    }

    // ------------------------------------------------------------ GlassSettingsHost：全部转发真实流程

    @Override public void back() { host.onBack(); }
    @Override public void scanQr() { host.onScanQr(); }
    @Override public void connect(String lan, String wan, String netMode, String token, String deviceName) {
        host.onConnect(lan, wan, netMode, token, deviceName);
    }
    @Override public void pastePairing() { host.onPastePairing(); }
    @Override public void disconnect() { host.onDisconnect(); }
    @Override public void toggleInsecureTls(boolean on) { host.onToggleInsecureTls(on); }
    @Override public void checkUpdate() { host.onCheckUpdate(); }
    @Override public void openFeedback() { host.onOpenFeedback(); }
    // 注意：动作侧的这三个**故意与上面同名的转发方法区分开**（pickXxx = 用户点了要落库；
    // setXxx = 宿主推状态给界面）。同名会形成"推状态→又触发落库"的回环。
    @Override public void pickDisplayMode(String mode) { host.onSetDisplayMode(mode); }
    @Override public void toggleAllowScreenshot(boolean on) { host.onToggleAllowScreenshot(on); }
    @Override public void pickThemeMode(String mode) { host.onSetThemeMode(mode); }
    // [V3.81] 字号三档：小 0.9 / 中 1.0 / 大 1.12（宿主落库 + 全局应用 + 重建界面）
    @Override public void pickFontScale(float scale) { host.onSetFontScale(scale); }
    /**
     * [V3.89 · 用户「字号调整后设置里那个选项**一直停留在标准档**」]
     * **现读现用**：直接从库里读当前比例，不再依赖宿主的推送时机（那条路实测会漏）。
     */
    @Override public float fontScale() {
        try {
            android.content.Context c = legacy.getContext();
            return c == null ? 1f : new com.dsh.mobile.Store(c).fontScale();
        } catch (Throwable t) {
            return 1f;
        }
    }
    @Override public void pickPalette(int index) { host.onSetPalette(index); }
    @Override public void setGlassLevel(int level) { host.onSetGlassLevel(level); }
    @Override public void pickGlassMode(String mode) { host.onSetGlassMode(mode); }   // [V3.27]
    @Override public void pickAmbientImage() { host.onPickAmbientImage(); }           // [V3.52]
}