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
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 「选择模型」底部弹窗（PROTOCOL §8 的 models 目录）。
 *
 * <p>数据来自网关返回的 {@code models} 帧：
 * <pre>
 * { "kind":"models", "current":{"provider":"deepseek","model":"deepseek-chat"},
 *   "routable":true,
 *   "groups":[ { "id":"deepseek", "name":"DeepSeek",
 *                "models":[ { "id":"deepseek-chat", "name":"DeepSeek Chat",
 *                             "reasoning":{ "efforts":[{"id":"low","name":"Low"},…] } } ] } ],
 *   "failures":[] }
 * </pre>
 *
 * <p>面板样式与 {@link SubagentSheet} 同一套：{@link Ui#sheetCard}（**不透明**面板）
 * + 遮罩。**故意不挂窗口背景模糊** —— 那条路径被真机实测出「滑动后面板底整层丢失」
 * （画面像变色），见 Ui.sheetCard 的注释。
 */
public final class ModelSheet {

    /** 选中一枚模型：provider = 分组 id，model = 模型 id。 */
    public interface Host {
        void onPickModel(String provider, String model, String reasoningEffort);
    }

    private ModelSheet() { }

    public static void show(Context ctx, JSONObject models, Host host) {
        if (ctx == null || models == null) return;
        final Dialog dlg = new Dialog(ctx);

        LinearLayout box = Ui.sheetCard(ctx);
        box.addView(Ui.grabber(ctx));

        box.addView(Ui.text(ctx, "选择模型", Ui.S_TITLE3, Ui.INK, true));

        JSONObject current = models.optJSONObject("current");
        String curProvider = current == null ? "" : current.optString("provider", "");
        String curModel = current == null ? "" : current.optString("model", "");
        boolean routable = models.optBoolean("routable", true);

        String subText = routable ? "切换只影响这条会话" : "当前不可路由（网关未就绪）";
        TextView sub = Ui.text(ctx, subText, Ui.S_FOOT, Ui.INK_SUB, false);
        sub.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 10));
        box.addView(sub);

        LinearLayout rows = Ui.col(ctx);
        JSONArray groups = models.optJSONArray("groups");
        int shown = 0;
        if (groups != null) {
            for (int i = 0; i < groups.length(); i++) {
                JSONObject g = groups.optJSONObject(i);
                if (g == null) continue;
                String gid = g.optString("id", "");
                JSONArray ms = g.optJSONArray("models");
                if (ms == null || ms.length() == 0) continue;

                // 分组标题：provider 名（同 deepseek）+ 小字
                String gname = g.optString("name", gid);
                TextView gt = Ui.text(ctx, gname, Ui.S_CAP1, Ui.INK_FAINT, true);
                gt.setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 10), Ui.dp(ctx, 4), Ui.dp(ctx, 4));
                rows.addView(gt);

                for (int j = 0; j < ms.length(); j++) {
                    JSONObject m = ms.optJSONObject(j);
                    if (m == null) continue;
                    String mid = m.optString("id", "");
                    String mname = m.optString("name", mid);
                    boolean isCur = mid.equals(curModel)
                            && (curProvider.isEmpty() || gid.equals(curProvider));

                    String detail = "";
                    JSONObject r = m.optJSONObject("reasoning");
                    if (r != null) {
                        JSONArray efforts = r.optJSONArray("efforts");
                        List<String> names = new ArrayList<>();
                        if (efforts != null) {
                            for (int k = 0; k < efforts.length(); k++) {
                                JSONObject e = efforts.optJSONObject(k);
                                if (e != null) names.add(e.optString("name", e.optString("id", "")));
                            }
                        }
                        if (!names.isEmpty()) {
                            StringBuilder sb = new StringBuilder("思考等级 ");
                            for (int k = 0; k < names.size(); k++) {
                                if (k > 0) sb.append(" / ");
                                sb.append(names.get(k));
                            }
                            detail = sb.toString();
                        }
                    }

                    rows.addView(row(ctx, dlg, mname, detail, isCur, gid, mid, host));
                    shown++;
                }
            }
        }

        if (shown == 0) {
            JSONArray failures = models.optJSONArray("failures");
            String why = failures == null || failures.length() == 0 ? "" : failures.optString(0, "");
            TextView empty = Ui.text(ctx, why.isEmpty() ? "网关没有返回可用模型" : ("模型不可用：" + why),
                    Ui.S_FOOT, Ui.INK_FAINT, false);
            empty.setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 14), Ui.dp(ctx, 4), Ui.dp(ctx, 14));
            rows.addView(empty);
        }

        ScrollView sc = new MaxHeightScrollView(ctx);
        sc.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        sc.addView(rows);
        box.addView(sc);

        TextView cancel = Ui.secondaryButton(ctx, "关闭");
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
            Ui.applyScreenshotPolicy(w);
            w.setDimAmount(0.35f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dlg.show();
    }

    /** 一行模型；当前项高亮 + 打勾。 */
    private static LinearLayout row(Context ctx, final Dialog dlg, String name, String detail,
                                    boolean current, final String provider, final String modelId,
                                    final Host host) {
        LinearLayout r = Ui.row(ctx);
        r.setMinimumHeight(Ui.dp(ctx, 56));
        r.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12));
        android.graphics.drawable.GradientDrawable bg = Ui.round(Ui.dp(ctx, 12),
                current ? Ui.SELECT_BG : Ui.FIELD_BG);
        Ui.tapRow(r, bg, Ui.PRESS);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(ctx, 3);
        r.setLayoutParams(lp);

        LinearLayout texts = Ui.col(ctx);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = Ui.text(ctx, name, Ui.S_BODY, Ui.INK, current);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(t);

        if (detail != null && !detail.isEmpty()) {
            TextView d = Ui.text(ctx, detail, Ui.S_CAP1, Ui.INK_SUB, false);
            d.setSingleLine(true);
            d.setEllipsize(android.text.TextUtils.TruncateAt.END);
            d.setPadding(0, Ui.dp(ctx, 3), 0, 0);
            texts.addView(d);
        }
        r.addView(texts);

        if (current) {
            r.addView(Ui.iconBox(ctx, com.dsh.mobile.R.drawable.ic_check,
                    0x00000000, Ui.BRAND, 20f, 0f, 18f));
        }
        r.setClickable(true);
        r.setOnClickListener(v -> {
            Ui.haptic(v);
            dlg.dismiss();
            if (host != null) host.onPickModel(provider, modelId, "");
        });
        return r;
    }

    /**
     * 高度封顶的 ScrollView（与 {@link SubagentSheet} 同款）：模型可能几十个，
     * 不封顶会把弹窗顶出屏幕、连「关闭」都点不到。
     */
    private static final class MaxHeightScrollView extends ScrollView {
        MaxHeightScrollView(Context c) {
            super(c);
            setOverScrollMode(OVER_SCROLL_NEVER);   // 关掉 Android 越界光晕（用户报过"一滑动就变色"）
            setVerticalScrollBarEnabled(false);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int cap = Math.round(getResources().getDisplayMetrics().heightPixels * 0.55f);
            int mode = MeasureSpec.getMode(heightSpec);
            int size = MeasureSpec.getSize(heightSpec);
            if (mode == MeasureSpec.UNSPECIFIED || size > cap) {
                heightSpec = MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST);
            }
            super.onMeasure(widthSpec, heightSpec);
        }
    }
}
