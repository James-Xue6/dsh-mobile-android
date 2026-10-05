package com.dsh.mobile.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 「添加附件」底部面板：文件 / 相册 / 拍照（2026-10-05 用户要求）。
 *
 * <p>用户原话：「对话框按钮左侧加号点击应弹出文件，相册，拍照这三个选项，然后添加进来之后
 * <b>不是立即发送</b>而是和输入的文字或语音内容一起发送」。
 *
 * <p>本面板只负责**问用户要哪一种来源**，选中的东西由 wire（MainActivity）解析成
 * {@code PendingAttachment} 再回灌给
 * {@link ConversationView#setPendingAttachments(java.util.List)}。
 *
 * <p>样式与 {@link ModelSheet} / {@link SubagentSheet} 同一套：{@link Ui#sheetCard}
 * （不透明面板底）+ 抓柄 + 底部弹出 + 遮罩。<b>故意不挂窗口背景模糊</b> —— 那条路径
 * 被真机实测出"滑动后面板底整层丢失"（见 {@link Ui#sheetCard} 的注释）。
 */
public final class AttachSheet {

    /** 三选一。用自建接口而不是 java.util.function.Consumer：minSdk 26 上 Consumer 要 desugar，本项目不用。 */
    public interface Host {
        void onFile();
        void onAlbum();
        void onCamera();
    }

    private AttachSheet() { }

    /** 便捷重载：三个 Runnable（wire 只想要回调时用这个）。 */
    public static void show(Context ctx, final Runnable onFile, final Runnable onAlbum,
                            final Runnable onCamera) {
        show(ctx, new Host() {
            @Override public void onFile() { if (onFile != null) onFile.run(); }
            @Override public void onAlbum() { if (onAlbum != null) onAlbum.run(); }
            @Override public void onCamera() { if (onCamera != null) onCamera.run(); }
        });
    }

    public static void show(Context ctx, final Host host) {
        if (ctx == null) return;
        final Dialog dlg = new Dialog(ctx);

        LinearLayout box = Ui.sheetCard(ctx);
        box.addView(Ui.grabber(ctx));
        box.addView(Ui.text(ctx, "添加附件", Ui.S_TITLE3, Ui.INK, true));
        // 文案要把"不是立即发送"讲清楚：否则用户会以为选完就发出去了（这正是旧版的行为）
        TextView sub = Ui.text(ctx, "选中的内容先放在输入框上方，和文字或语音一起发送",
                Ui.S_FOOT, Ui.INK_SUB, false);
        sub.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 8));
        box.addView(sub);

        box.addView(row(ctx, dlg, com.dsh.mobile.R.drawable.ic_folder, "文件",
                "文档、压缩包，任意格式", () -> { if (host != null) host.onFile(); }));
        box.addView(row(ctx, dlg, com.dsh.mobile.R.drawable.ic_image, "相册",
                "从相册里选图片", () -> { if (host != null) host.onAlbum(); }));
        box.addView(row(ctx, dlg, com.dsh.mobile.R.drawable.ic_camera, "拍照",
                "现在拍一张", () -> { if (host != null) host.onCamera(); }));

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
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            Ui.applyScreenshotPolicy(w);
            w.setDimAmount(0.35f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dlg.show();
    }

    /** 一行来源：图标 + 标题 + 说明。点一下先收面板，再回调（避免面板盖住刚打开的相机/选择器）。 */
    private static LinearLayout row(Context ctx, final Dialog dlg, int icon,
                                    String title, String sub, final Runnable onPick) {
        LinearLayout r = Ui.row(ctx);
        r.setMinimumHeight(Ui.dp(ctx, 56));
        r.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(ctx, 3);
        r.setLayoutParams(lp);
        // 按下反馈由 tapRow 管（背景保持我们给的底，不会被按压底色顶掉）
        Ui.tapRow(r, Ui.round(Ui.dp(ctx, 12), Ui.FIELD_BG), Ui.PRESS);

        r.addView(Ui.iconBox(ctx, icon, Ui.BRAND_SOFT, Ui.BRAND, 34f, 10f, 18f));

        LinearLayout texts = Ui.col(ctx);
        texts.setPadding(Ui.dp(ctx, 11), 0, 0, 0);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        texts.addView(Ui.text(ctx, title, Ui.S_BODY, Ui.INK, true));
        if (sub != null && !sub.isEmpty()) {
            texts.addView(Ui.text(ctx, sub, Ui.S_CAP1, Ui.INK_SUB, false));
        }
        r.addView(texts);

        r.setClickable(true);
        r.setOnClickListener(v -> {
            Ui.haptic(v);
            dlg.dismiss();
            if (onPick != null) onPick.run();
        });
        return r;
    }
}
