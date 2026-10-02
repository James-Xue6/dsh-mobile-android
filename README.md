# DSH 掌上通（DSH Mobile for Android）

用手机遥控电脑上的 **DeepSeek Harness**。原生 Android 客户端，界面按豆包 / Trae 这类对话产品做：
会话列表 → 气泡对话 → 实时流式输出 → 内联审批 / 提问卡片。

[English](README.en.md) | **简体中文**

---

## 装它（约 3 分钟）

### ① 电脑：装 PC 插件

在电脑上执行（需要 PowerShell 7 / `pwsh`）：

```powershell
git clone https://github.com/James-Xue6/dsh-mobile-android.git
cd dsh-mobile-android
pwsh -File .\pc-plugin\install.ps1
```

脚本会：把插件复制到 `~/.dsh/local-plugins/`、把 APK 放进插件目录、在 DSH profile 里登记依赖。

> 用 SSH 的话，第一条命令换成 `git clone git@github.com:James-Xue6/dsh-mobile-android.git`。

### ② 重启一次 DSH 桌面版

然后点左侧边栏底部的 **「移动设备」** 按钮 —— PC 端只有这一个入口。

### ③ 手机：装 App

抽屉里第一屏就是「手机接入」卡片，点 **「下载 App」**：
让**手机连同一个 WiFi** 扫弹出的二维码，直接下载安装（安装包由你这台电脑发出，局域网 8099 端口，不经网盘或 CDN）。

不方便扫码也可以直接从公开地址下载：

- CDN（国内通常更快）：`https://cdn.jsdelivr.net/gh/James-Xue6/dsh-mobile-android@v0.8/dist/dsh-mobile.apk`
- GitHub：`https://github.com/James-Xue6/dsh-mobile-android/raw/v0.8/dist/dsh-mobile.apk`

### ④ 扫码配对

在同一张卡片上点 **「生成内网二维码」**（在家用）或 **「生成公网二维码」**（出门用），用 App 扫它。
**内网地址与公网地址会一次填好**，之后在家用内网、出门自动切公网，都不用手输。

> 电脑端需要 `dsh-plugin-mobile-gateway`（协议层依赖）。安装脚本会自动登记；没装的话从 DSH 插件市场装一次。

---

## 怎么用

1. **选会话** —— 打开 App 就是会话列表（按工作区分组），点一个进去，或新建。
2. **发消息** —— 直接输入发送。助手回复流式逐字出现，工具调用折叠成一行工具条。
3. **回答它** —— 需要你决定时会出现卡片：
   - **审批卡**：`批准一次` / `拒绝`；
   - **提问卡**：选项（可多选）+ 自定义输入；
   - **交付物卡**：点一下把文件下载到手机，长按复制路径。
4. 会话顶部显示当前**目标**与**任务清单**；右下角 `↓` 一键回到最新消息。

---

## 出问题了

**手机连不上电脑**
- 确认手机与电脑在同一个 WiFi；电脑端「移动设备」抽屉里，下面「高级设置」中的网关是开启状态。
- App 右上角 ⚙ →「当前状态」：看 `state / gen / ws` 与最近 40 条收发帧，能直接看出卡在哪一步。
- 提示「公网地址可能已失效」：公网隧道是临时域名，**电脑每次重启都会变**，重新扫一次二维码即可。

**扫码扫不出 / 点扫码相机一闪而过**
- 给 App 相机权限（系统设置 → 应用 → DSH 掌上通 → 权限）。
- 也可以点「复制配对串」，在 App 里手动粘贴。

**重启 DSH 后找不到「移动设备」按钮**
- 插件装完必须**重启一次 DSH 桌面版**才生效。
- 还不行就确认 `dsh-plugin-mobile-gateway` 已装（DSH 插件市场里搜一下）。

**覆盖安装报「应用未安装」**
- 说明签名和旧版不一致。只能用原来的签名密钥库（`%USERPROFILE%\.dsh-mobile-keys\dshmobile.jks`）重新打包；
  该文件丢了就只能先卸载再重装。**请离线备份这个 .jks。**

**想固定公网地址（不想每次重启都重新扫码）**
- 手机与电脑加入同一个 Tailscale 网络，App 里填 `ws://100.x.x.x:3091/ws/mobile`
  （App 已把 `100.64/10` 与 `*.ts.net` 视为内网，不受明文限制、也不暴露公网）。
- 或者把域名的 NS 迁到 Cloudflare，建命名隧道后在面板里配置 `mode=named`。

---

## 开发者

| 想做什么 | 命令 |
|---|---|
| 构建 APK | `pwsh -File .\build.ps1` |
| 跑网络层端到端测试 | `pwsh -File .\harness\build.ps1`，再 `node tools\run-e2e.mjs` |
| 逐档判定分析 | `node tools\e2e-assert.mjs` |
| 模拟器 + 装机（验 UI） | `tools\启动模拟器并装机.bat` |
| 发版 | `pwsh -File .\release.ps1 -Version 0.5 -Notes "改了什么"` |

- **构建依赖**：JDK 21 + Android SDK（`build-tools;36.0.0` 与 `platforms;android-36`）。
  源码路径**不能含中文**（原生工具在 936 代码页打不开非 ASCII 路径），脚本会自动暂存到 `C:\dshstage` 再构建。
- **签名**：固定密钥库 `%USERPROFILE%\.dsh-mobile-keys\dshmobile.jks`；
  口令走环境变量 `DSH_KS_PASS` 或仓库根目录的 `keystore.local.ps1`（已 gitignore，**绝不入库**）。
- **更新提醒**：App 启动时从 `@main` 拉 `dist/version.json` 比对版本；发版脚本会一并刷新它。
- 测试脚手架的用法与踩坑见 [tools/使用说明.md](tools/使用说明.md)；
  PC 插件细节见 [pc-plugin/dsh-mobile-access/README.md](pc-plugin/dsh-mobile-access/README.md)。

### 目录

```
dsh-mobile-android/
├── AndroidManifest.xml
├── build.ps1 / release.ps1      # 免 Gradle 构建 / 一键发版
├── dist/                        # 分发的 APK、校验值与更新清单
├── libs/core-3.5.3.jar          # zxing 扫码，纯 Java 无 native
├── res/                         # 主题 / 图标 / 网络策略
├── src/com/dsh/mobile/          # App：MainActivity + model/ + net/ + ui/
├── harness/                     # JVM 联调：复用 App 真实 net 层源码
├── pc-plugin/                   # PC 端「手机接入」插件 + 安装脚本
└── tools/                       # 假网关、e2e 脚本、模拟器装机
```

### 架构

App 只实现 `dsh-mobile-v1` 协议（WebSocket + JSON），不碰 DSH 内部；
协议层由第三方 MIT 插件 `dsh-plugin-mobile-gateway` 承担，本仓库的 `pc-plugin/` 只做接入编排与界面。

```
Android App ──ws/wss──> dsh-plugin-mobile-gateway ──> DSH Host (desktop profile)
```

---

## 许可

本仓库尚未附 `LICENSE` 文件。在二次分发或商用前，请先与作者确认授权方式。
