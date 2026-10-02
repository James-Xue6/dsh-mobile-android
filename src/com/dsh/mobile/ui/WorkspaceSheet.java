package com.dsh.mobile.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dsh.mobile.model.WorkspaceGroup;

/**
 * 工作区「⋯」底部弹窗（手搓，与 {@link SubagentSheet} 同一套样式与窗口参数）。
 *
 * 用户诉求：「在每个工作区后面加三个点，点开可以添加（新任务），和电脑版一样」。
 * 电脑版是在工作区分组标题上直接给一个「＋/新建会话」入口
 * （app.asar uiWorkspace.startSession(workspaceId) → sessions.create({ workspaceId })），
 * 手机端空间窄，所以收进「⋯」里 —— 但落到协议上的动作完全一致：
 * 新会话带着这个工作区的 workspaceId（取不到时用完整 cwd）创建。
 *
 * 刻意只放一条动作 + 一条只读信息：用户偏好简洁，多余的「刷新/重命名」只会分散注意力。
 */
public final class WorkspaceSheet {

    public interface Host {
        /** 「在此工作区新建任务」：把新会话派进这个工作区。 */
        void onNewTaskHere(WorkspaceGroup g);
    }

    private WorkspaceSheet() { }

    public static void show(Context ctx, final WorkspaceGroup g, final Host host) {
        final Dialog dlg = new Dialog(ctx);

        LinearLayout box = Ui.sheetCard(ctx);
        box.addView(Ui.grabber(ctx));

        // 标题就是工作区名 —— 用户点开时必须一眼看清"我在操作哪个工作区"
        box.addView(Ui.text(ctx, g.label, Ui.S_TITLE3, Ui.INK, true));

        // 完整路径作为副标题：工作区重名时这是唯一的区分依据（也顺带满足"查看工作区路径"）
        if (!g.path.isEmpty()) {
            TextView p = Ui.text(ctx, g.path, Ui.S_FOOT, Ui.INK_SUB, false);
            p.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 10));
            p.setSingleLine(true);
            p.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            box.addView(p);
        } else {
            box.addView(Ui.text(ctx, g.workspaceId, Ui.S_FOOT, Ui.INK_SUB, false));
        }

        // 说明文案必须如实：有 workspaceId 时新会话会真正**挂进**这个工作区；
        // 只有目录路径时，宿主只把 cwd 设过去、不会建立工作区成员关系
        // （app.asar groupByWorkspace 严格按 workspace.sessionIds 分组），
        // 电脑端会把它算作「未分组」。不能把这种情况说成"就在这个工作区里"。
        String note;
        if (!g.workspaceId.isEmpty()) {
            note = g.count > 0 ? "该工作区已有 " + g.count + " 个任务" : "该工作区还没有任务";
        } else {
            note = "未取到工作区注册信息，按目录新建";
        }
        LinearLayout action = item(ctx, "在此工作区新建任务", note);
        action.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dlg.dismiss();
                if (host != null) host.onNewTaskHere(g);
            }
        });
        box.addView(action);

        TextView cancel = Ui.secondaryButton(ctx, "关闭");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(ctx, 10);
        cancel.setLayoutParams(clp);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlg.dismiss(); }
        });
        box.addView(cancel);

        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(box);
        dlg.setCanceledOnTouchOutside(true);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            // 弹窗里会出现工作区路径：与主窗口共用同一条「允许截屏」策略
            Ui.applyScreenshotPolicy(w);
            w.setDimAmount(0.35f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dlg.show();
    }

    /** 一行可点动作：主文案 + 右侧说明。 */
    private static LinearLayout item(Context ctx, String title, String note) {
        LinearLayout row = Ui.row(ctx);
        row.setMinimumHeight(Ui.dp(ctx, 56));
        row.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12));
        Ui.tapRow(row, Ui.round(Ui.dp(ctx, 12), Ui.FIELD_BG), Ui.PRESS);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(ctx, 8);
        row.setLayoutParams(lp);

        TextView t = Ui.text(ctx, title, Ui.S_BODY, Ui.BRAND, true);
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);
        row.addView(Ui.text(ctx, note, Ui.S_FOOT, Ui.INK_FAINT, false));
        return row;
    }
}
