package com.dsh.mobile.proto;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dsh.mobile.ui.Ui;

import java.util.Random;

/**
 * 【另起原型】首页 · 真毛玻璃效果 demo
 *
 * 目标：顶栏 + 输入胶囊是真·背景模糊 —— 消息从玻璃下面滚过会被磨砂糊掉
 * （iOS 那种），而不是现在的"半透明白板"。
 *
 * 实现 = BlurView 思路：内容层(ScrollView)在顶栏/输入胶囊背后的区域
 * 离屏渲染 → 降采样 + 盒式模糊 → 放大铺成玻璃背景。滚动时跟随刷新。
 */
public class GlassHomeActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        // ---- 内容层：可滚动消息列表（首页信息流）
        final ScrollView sc = new ScrollView(this);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        // 上下留出顶栏/输入胶囊的空间，让内容能滚到玻璃底下
        list.setPadding(Ui.dp(this, 16), Ui.dp(this, 128), Ui.dp(this, 16), Ui.dp(this, 52));
        // 好看背景：真实极光插画图（有清晰山脉剪影/星点轮廓，玻璃感+形变才看得见）
        list.setBackground(new BitmapDrawable(getResources(),
                android.graphics.BitmapFactory.decodeResource(getResources(), com.dsh.mobile.R.drawable.aurora_bg)));

        final Object[][] items = {
            {"「待做」的三件做完", "三件都做完了√ ①抽屉里加了会话搜索 ②输入区的+能拍照了 ③交付物点一下就在应用内预览", 0xFF90CAF9},
            {"pwsh · 构建 APK", "完成。已打新包 0.86.42，校验值与签名指纹一致。", 0xFFA5D6A7},
            {"好，发版吧", "收到，这就把 0.86.42 发出去。", 0xFFFFCC80},
            {"已发版 v0.86.42", "App 里「设置→关于→检查更新」就能收到。", 0xFFF48FB1},
            {"adb · 安装到手机", "完成。已装到目标设备。", 0xFFB39DDB},
            {"点交付物就能预览", "应用内直接看 HTML / 图片 / 文本，长按可下载。", 0xFFFFE082},
            {"这次在手机上就全干完了", "不用开电脑👍", 0xFF80DEEA},
            {"模型用量提示", "Deepseek-V4.1-Flash · 用量 55%", 0xFF9FA8DA},
            {"新会话：L:\\DSH自制插件", "热更新功能实测 · 今天 01:06", 0xFFEF9A9A},
            {"会话搜索上线", "抽屉里现在可以按关键词搜会话了。", 0xFFFFD180},
            {"拍照能力", "输入区的 + 现在能调起相机拍照并作为附件发送。", 0xFFBCAAA4},
            {"交付物应用内预览", "点一下即可在应用内查看，长按可下载到手机。", 0xFF80CBC4},
        };
        for (Object[] it : items) {
            list.addView(card((String) it[0], (String) it[1], ((Integer) it[2]).intValue()));
        }
        sc.addView(list);

        // ---- 帧同步宿主：内容层(sc) + 毛玻璃面板同容器，玻璃跟滚动帧帧同步
        // （FrostedHomeView 构造时已把 sc 作为内容层加入，无需再 addView(sc)）
        final FrostedHomeView root = new FrostedHomeView(this, sc);
        // 环境背景：紫粉渐变（demo 极光档）
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFA892FF, 0xFF8FB0FF, 0xFFFFB0D4}));

        // ---- 沉浸式：状态栏透明，背景延伸到底；但让顶栏玻璃避开状态栏区域，
        // 否则系统状态栏的时间/图标(如"4:20")被玻璃盖住、又不在磨砂 target 里，
        // 会透出来变成顶栏左上角那个"缩小的数字块"（用户反复反馈的错乱）。
        android.view.Window win = getWindow();
        win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        win.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        win.getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        int sbH = statusBarHeight();
        root.setPadding(0, sbH, 0, 0);

        // ---- 顶栏：真毛玻璃（绑定内容层 sc；帧同步由宿主驱动）
        final FrostedPanelView top = new FrostedPanelView(this, sc);
        // 顶栏：纯磨砂（iOS 顶栏无折射，避免 lens 在顶栏边缘产生紫边框"缩小块"）
        top.corner(Ui.dp(this, 20)).tint(0x38FFFFFF).hairline(0x1EFFFFFF).downscale(5);
        top.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));
        LinearLayout topInner = Ui.col(this);
        TextView t1 = Ui.text(this, "D:\\projects\\demo", Ui.S_BODY, 0xFF000000, true);
        TextView t2 = Ui.text(this, "走内网 · 自动 · 已连 WiFi", Ui.S_FOOT, 0xFF35353D, false);
        topInner.addView(t1);
        topInner.addView(t2);
        top.addView(topInner, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        top.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 92), Gravity.TOP));
        // 悬浮顶栏：左右留边距，让圆角+金属描边四周可见（参考灵动岛控件的"优化边缘"）
        FrameLayout.LayoutParams tlp = (FrameLayout.LayoutParams) top.getLayoutParams();
        tlp.leftMargin = Ui.dp(this, 12);
        tlp.rightMargin = Ui.dp(this, 12);
        top.setLayoutParams(tlp);
        root.addFrost(top);

        // ---- 输入胶囊：真毛玻璃（绑定内容层 sc）
        final FrostedPanelView input = new FrostedPanelView(this, sc);
        input.corner(Ui.dp(this, 28)).tint(0x42FFFFFF).hairline(0x1EFFFFFF).downscale(5).lens();
        FrameLayout inputInner = new FrameLayout(this);
        inputInner.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        TextView hint = Ui.text(this, "给 Agent 派个任务…", Ui.S_BODY, 0xFF2E2E36, false);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        inputInner.addView(hint, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 52), Gravity.CENTER_VERTICAL));
        input.addView(inputInner, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 56), Gravity.BOTTOM);
        ilp.leftMargin = Ui.dp(this, 12);
        ilp.rightMargin = Ui.dp(this, 12);
        ilp.bottomMargin = Ui.dp(this, 14);
        input.setLayoutParams(ilp);
        root.addFrost(input);

        // ---- 中间方形悬浮小胶囊（比输入胶囊小一半，验证真机玻璃效果）
        final FrostedPanelView sq = new FrostedPanelView(this, sc);
        sq.corner(Ui.dp(this, 24)).tint(0x42FFFFFF).hairline(0x1EFFFFFF).downscale(5).lens();
        int sqs = Ui.dp(this, 72);
        sq.setLayoutParams(new FrameLayout.LayoutParams(sqs, sqs, Gravity.CENTER));
        root.addFrost(sq);

        setContentView(root);
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : Ui.dp(this, 24);
    }

    private View card(String title, String body, int color) {
        LinearLayout c = Ui.col(this);
        // 卡片调半透明(≈82%)，让花哨背景透出来 → 玻璃糊的内容色彩更丰富
        c.setBackground(Ui.round(Ui.dp(this, 18), (color & 0x00FFFFFF) | 0xD1000000));
        c.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 10);
        c.setLayoutParams(lp);
        TextView t = Ui.text(this, title, Ui.S_BODY, 0xFF000000, true);
        TextView b = Ui.text(this, body, Ui.S_FOOT, 0xFF5F5F66, false);
        b.setPadding(0, Ui.dp(this, 3), 0, 0);
        c.addView(t);
        c.addView(b);
        return c;
    }

    // ---- 生成一张"花哨"背景位图（多彩渐变 + 彩色柔边光斑 + 斜纹细节）
    private static Bitmap sFlower;

    private Bitmap flowerBg() {
        if (sFlower != null) return sFlower;
        int W = 1080, H = 2400;
        Bitmap bm = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        // 多彩对角渐变底（紫→蓝→青→绿→黄→橙→粉）
        Paint base = new Paint();
        base.setShader(new LinearGradient(0, 0, W, H,
                new int[]{0xFF8A2BE2, 0xFF2196F3, 0xFF00BCD4, 0xFF76FF03, 0xFFFFEB3B, 0xFFFF9800, 0xFFE91E63},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, base);
        // 彩色柔边光斑（模拟花朵/光斑）
        Random r = new Random(7);
        int[] cols = {0xFFFF4081, 0xFFFFEB3B, 0xFF76FF03, 0xFF40C4FF, 0xFFFF6E40, 0xFFB388FF, 0xFFEAFF7F, 0xFFFF1744};
        for (int i = 0; i < 80; i++) {
            int x = r.nextInt(W), y = r.nextInt(H);
            int rad = 40 + r.nextInt(150);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(cols[r.nextInt(cols.length)]);
            p.setAlpha(70 + r.nextInt(120));
            p.setMaskFilter(new BlurMaskFilter(rad * 0.7f, BlurMaskFilter.Blur.NORMAL));
            c.drawCircle(x, y, rad, p);
        }
        // 清晰网格线：白色横竖线，用来"检验形变"——玻璃的凸透镜畸变会把直线弯成弧线
        Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        grid.setColor(0x66FFFFFF);
        grid.setStrokeWidth(4);
        for (int x = 0; x <= W; x += 90) c.drawLine(x, 0, x, H, grid);
        for (int y = 0; y <= H; y += 90) c.drawLine(0, y, W, y, grid);
        sFlower = bm;
        return bm;
    }
}
