package com.dsh.mobile.ui;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Locale;

/**
 * 交付物（present 产出的文件）的**应用内预览**。
 *
 * <p>为什么要有这一屏：原来点交付物只会"下载到手机"，下完还得离开 App、去文件管理里找、
 * 交给别的应用打开 —— 用户要的只是"看一眼这个 HTML 长什么样"。现在点一下就在这里看。
 *
 * <p>三种能看的（覆盖绝大多数产物）：
 * <ul>
 *   <li><b>HTML</b>：直接 {@code file://} 交给 WebView（产物多是自包含的单文件页面）；</li>
 *   <li><b>图片</b>：同一套 WebView，可双指放大看清细节；</li>
 *   <li><b>纯文本</b>（txt/md/json/log/csv…）：读出来转义后用 {@code <pre>} 包一层，
 *       跟着 App 的深浅色走（不转义的话正文里的 {@code <} 会把页面吃出半截）。</li>
 * </ul>
 * 其余类型（docx / xlsx / pdf / zip…）**不硬撑**：给一张说明卡 + 「复制路径」，
 * 并告诉用户长按对话里的文件名可以下载到手机、用别的应用打开 —— 装作能看才是坑。
 *
 * <p>安全：WebView 关掉 JavaScript / DOM storage / 内容提供者访问，只允许 {@code file://}；
 * 页面里的外链一律不在本页打开（要跳也只跳系统浏览器，见 {@link #shouldOverrideUrlLoading}）。
 * 这里**不需要** FileProvider —— WebView 在同一个进程里，读自己 cache 目录的 file:// 就行。
 */
public final class ArtifactActivity extends Activity {

    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_NAME = "name";

    /** 文本预览上限：再大就只显示前一段（避免几 MB 的 log 把主线程卡住）。 */
    private static final int MAX_TEXT_BYTES = 512 * 1024;
    /** 要看的那个本地文件（由 MainActivity 下载到 cache/artifact 后把绝对路径传进来）。 */
    private File file;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        String path = getIntent() == null ? "" : safe(getIntent().getStringExtra(EXTRA_PATH));
        String name = getIntent() == null ? "" : safe(getIntent().getStringExtra(EXTRA_NAME));
        if (name.isEmpty()) name = lastSeg(path);
        if (name.isEmpty()) name = "交付物";
        file = path.isEmpty() ? null : new File(path);

        LinearLayout root = Ui.col(this);
        // [2026-10-07 合并适配] 本仓库 Ui 没有 setAmbientBackground()，等价写法是给根容器设
        // AmbientDrawable（与 MainActivity 各屏完全同一套环境底，换配色时一起变）。
        root.setBackground(new Ui.AmbientDrawable());
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            if (v.getPaddingTop() != top || v.getPaddingBottom() != bottom) {
                v.setPadding(0, top, 0, bottom);
            }
            return insets;
        });

        root.addView(buildBar(name), Ui.fill());
        View hair = new View(this);
        hair.setBackgroundColor(Ui.HAIRLINE);
        hair.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 0.5f))));
        root.addView(hair);

        root.addView(buildContent(name), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        root.requestApplyInsets();
        applySystemBars();
        Ui.applyScreenshotPolicy(getWindow());
    }

    /** 顶栏：‹ 返回 + 文件名 + 一行说明（类型 · 大小）。 */
    private View buildBar(String name) {
        LinearLayout bar = Ui.row(this);
        bar.setBackground(Ui.glassBar());
        bar.setMinimumHeight(Ui.dp(this, 44));
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 5), Ui.dp(this, 8), Ui.dp(this, 5));

        TextView back = Ui.circleIconButton(this, com.dsh.mobile.R.drawable.ic_chevron_left,
                android.graphics.Color.TRANSPARENT, Ui.BRAND, 20f, 36f);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> { Ui.haptic(v); finish(); });
        bar.addView(back);

        LinearLayout titles = Ui.col(this);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = Ui.text(this, name, Ui.S_HEAD, Ui.INK, true);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        titles.addView(t);
        TextView sub = Ui.text(this, subLine(name), Ui.S_CAP1, Ui.INK_FAINT, false);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(sub);
        bar.addView(titles);
        return bar;
    }

    /** 第二行小字：类型 + 大小（"HTML · 12.3 KB"）。 */
    private String subLine(String name) {
        if (file == null || !file.exists()) return "文件不在本机";
        String kind = kindOf(name);
        String type = "html".equals(kind) ? "HTML"
                : "image".equals(kind) ? "图片"
                : "text".equals(kind) ? "文本"
                : extOf(name).isEmpty() ? "文件" : extOf(name).toUpperCase(Locale.ROOT);
        return "应用内预览 · " + type + " · " + human(file.length());
    }

    /** 内容区：能看的交给 WebView，看不了的给一张说明卡。 */
    private View buildContent(String name) {
        if (file == null || !file.exists() || !file.isFile()) {
            return fallback("文件不在了", "预览用的临时文件可能已被系统清理。\n回到对话里点一下文件名可以重新下载。");
        }
        String kind = kindOf(name);
        if ("html".equals(kind) || "image".equals(kind)) {
            WebView w = newWeb();
            w.loadUrl(android.net.Uri.fromFile(file).toString());
            return w;
        }
        if ("text".equals(kind)) {
            WebView w = newWeb();
            w.loadDataWithBaseURL(null, textHtml(readText()), "text/html", "utf-8", null);
            return w;
        }
        String ext = extOf(name);
        return fallback("这个类型应用内看不了",
                (ext.isEmpty() ? "这个文件" : "." + ext + " 这类文件")
                        + "没法在应用内打开。\n长按对话里的文件名 → 「下载到手机」，再用别的应用打开。");
    }

    /** WebView：只放开 file:// 访问，其它一律收紧（产物是静态页面，不需要 JS）。 */
    private WebView newWeb() {
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(false);
        s.setDomStorageEnabled(false);
        s.setDatabaseEnabled(false);
        // Android 11 起 file:// 默认不可访问，必须显式打开（本页只读自己 cache 里的产物）
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);      // 屏幕上那两个 +/- 按钮很丑，用双指缩放就够
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setDefaultTextEncodingName("utf-8");
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        w.setBackgroundColor(0x00000000);
        w.setVerticalScrollBarEnabled(false);
        w.setHorizontalScrollBarEnabled(false);
        w.setOverScrollMode(View.OVER_SCROLL_NEVER);
        w.setWebViewClient(new WebViewClient() {
            // 只在本页里加载本地产物；外链不外跳、也不在本页替换（本页没有地址栏，
            // 一旦被替换成别的页面，用户就"回不到刚才那个产物"了）
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return handleUrl(req == null || req.getUrl() == null ? "" : req.getUrl().toString());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url == null ? "" : url);
            }
        });
        return w;
    }

    private boolean handleUrl(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        if (u.startsWith("file://") || u.startsWith("about:") || u.startsWith("data:")
                || u.startsWith("blob:")) {
            return false;      // 本地产物：在本页里正常加载
        }
        Toast.makeText(this, "外部链接不在应用内打开", Toast.LENGTH_SHORT).show();
        return true;
    }

    /** 纯文本 → 一个跟着主题走的 {@code <pre>}（转义后再塞进去）。 */
    private String textHtml(String body) {
        String fg = hex(Ui.INK);
        String bg = hex(Ui.SURFACE_G1);
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;padding:0;background:transparent;}"
                + "pre{white-space:pre-wrap;word-wrap:break-word;margin:12px;padding:14px;"
                + "border-radius:14px;font-family:monospace;font-size:13px;line-height:1.5;"
                + "color:" + fg + ";background:" + bg + ";}</style></head><body><pre>"
                + escape(body) + "</pre></body></html>";
    }

    /** 读文本：最多 {@link #MAX_TEXT_BYTES}，超了截断并注明（不假装全读完了）。 */
    private String readText() {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            int total = 0;
            boolean cut = false;
            while ((n = in.read(buf)) > 0) {
                int room = MAX_TEXT_BYTES - total;
                if (n > room) { bos.write(buf, 0, room); cut = true; break; }
                bos.write(buf, 0, n);
                total += n;
            }
            String s = new String(bos.toByteArray(), "UTF-8");
            return cut ? s + "\n\n…（文件较大，只显示了前 " + (MAX_TEXT_BYTES / 1024) + " KB）" : s;
        } catch (Throwable t) {
            return "读取失败：" + (t.getMessage() == null ? String.valueOf(t) : t.getMessage());
        }
    }

    /** 看不了 / 文件不在时的说明卡（居中，不硬撑）。 */
    private View fallback(String title, String detail) {
        LinearLayout wrap = Ui.col(this);
        wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32));

        TextView icon = Ui.iconBox(this, com.dsh.mobile.R.drawable.ic_file,
                android.graphics.Color.TRANSPARENT, Ui.alpha(Ui.INK_FAINT, 0.9f), 56f, 0f, 40f);
        icon.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
        wrap.addView(icon);

        TextView t = Ui.text(this, title, Ui.S_HEAD, Ui.INK, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 14), 0, 0);
        wrap.addView(t);

        TextView d = Ui.text(this, detail, Ui.S_FOOT, Ui.INK_SUB, false);
        d.setGravity(Gravity.CENTER);
        d.setLineSpacing(Ui.dp(this, 4), 1.05f);
        d.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 6));
        wrap.addView(d);

        TextView copy = Ui.textButton(this, "复制路径", Ui.BRAND);
        copy.setOnClickListener(v -> {
            try {
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null && file != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("path", file.getAbsolutePath()));
                    Toast.makeText(this, "路径已复制", Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable ignored) { }
        });
        wrap.addView(copy);
        return wrap;
    }

    /** 状态栏 / 导航栏跟着主题走（与 MainActivity 同一条规则，深色底要配浅色图标）。 */
    private void applySystemBars() {
        Window w = getWindow();
        if (w == null) return;
        int bg = Ui.BG;
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bg));
        View dv = w.getDecorView();
        int flags = dv.getSystemUiVisibility();
        if (Ui.isDark()) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 27) {
            if (Ui.isDark()) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        dv.setSystemUiVisibility(flags);
    }

    // ---------------------------------------------------------------- 小工具

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    private static String lastSeg(String path) {
        String p = path == null ? "" : path.replace('\\', '/');
        int i = p.lastIndexOf('/');
        return i >= 0 && i + 1 < p.length() ? p.substring(i + 1) : p;
    }

    private static String extOf(String name) {
        int i = name == null ? -1 : name.lastIndexOf('.');
        return i > 0 && i + 1 < name.length() ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "";
    }

    /** 能不能在 WebView 里看一眼（其余类型走说明卡，不硬撑）。 */
    private static String kindOf(String name) {
        String e = extOf(name);
        if ("html".equals(e) || "htm".equals(e) || "xhtml".equals(e)) return "html";
        if ("png".equals(e) || "jpg".equals(e) || "jpeg".equals(e) || "gif".equals(e)
                || "webp".equals(e) || "bmp".equals(e) || "svg".equals(e)) return "image";
        for (String t : TEXT_EXT) if (t.equals(e)) return "text";
        return "other";
    }

    private static final String[] TEXT_EXT = {
            "txt", "text", "md", "markdown", "json", "jsonl", "xml", "log", "csv", "tsv",
            "yml", "yaml", "ini", "conf", "cfg", "toml", "properties", "env",
            "js", "mjs", "cjs", "ts", "tsx", "jsx", "css", "scss", "less", "vue", "svelte",
            "sql", "py", "java", "kt", "kts", "go", "rs", "rb", "php", "c", "h", "cpp", "hpp",
            "cs", "swift", "sh", "bash", "zsh", "ps1", "bat", "cmd", "diff", "patch", "gitignore"
    };

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&') sb.append("&amp;");
            else if (c == '<') sb.append("&lt;");
            else if (c == '>') sb.append("&gt;");
            else sb.append(c);
        }
        return sb.toString();
    }

    private static String hex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    private static String human(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", n / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", n / 1024.0 / 1024.0);
    }
}
