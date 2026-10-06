# DSH 掌上通 · 交接单（Handoff）

> 写给**下一个会话的自己**。读完这份就能接着干，不用重新摸一遍。
> **当前版本：v0.86.42（versionCode 59）** · 最后更新：2026-10-06 01:30

---

## 0. 一句话现状

Android 端「DSH 掌上通」已完成 **40+ 版迭代**（v0.86.1 → v0.86.42），附件、交互、毛玻璃 UI、问题定位条全部做完。
**更新链路已实测跑通** ✓（App 内「检查更新」能读清单 → 下载 → 校验 sha256 → 安装 ✓）。
**上一版挂着的「待做 3 件」本次全部做完** ✓：① 会话搜索 ②「＋」拍照 ③ 产物应用内预览 —— 做法实录见第 5 节（下次要改这三块，先看那一节，别重新摸一遍）。
**当前没有挂着的待办** ✗ 下一件事从用户的新需求开始 ✓

---

## 1. 代码与仓库

| 项 | 值 |
|---|---|
| **工作目录** | `L:\DSH日常问题\dsh-mobile-android` |
| **Git 远程** | Gitee `tang-changxu/dsh-mobile-android`（**公开镜像**，已在 README 标注第三方 ✓）|
| **上游** | GitHub `James-Xue6/dsh-mobile-android` —— **只读参考** ✗ 任何代码/清单都**不许指回去** ✗（见第 6 节的坑）|
| **App 包名** | `com.dsh.mobile` |
| **当前版本** | versionName `0.86.41` / versionCode `58` |
| **源码** | `src/com/dsh/mobile/`（`MainActivity.java` 7000+ 行是主体）|
| **构建** | `build.ps1`（**无 Gradle** ✗ 手工管线：aapt2 → javac → d8 → zipalign → apksigner）|
| **发版** | `release.ps1 -Version X.Y.Z -Notes "..."` |
| **更新日志** | **`CHANGELOG.md`** ← 每次改动都要往里补一行（用户明确要求：**做了的活要让人看见** ✓）|

### 控件核心文件
| 文件 | 干什么 |
|---|---|
| `ui/Ui.java` | **全 App 色板/字号/圆角唯一来源** + `md()` Markdown 渲染（表格/链接/代码块）+ `CardBg` 玻璃绘制 + `AmbientBg` 环境光晕底 |
| `ui/ConversationView.java` | 对话页：消息列表、输入区、挂载条、附件预览条、刻度尺挂载、**滚动锚点与补齐历史** |
| `ui/ChatAdapter.java` | 每一行怎么画（气泡/工具卡/交付物卡/时间分隔）|
| `ui/MessageScrubber.java` | 右侧「问题刻度尺」（10 格滚动 + 灰底 + 完整气泡预览）|
| `ui/SessionListView.java` | **抽屉里的会话列表**（顶部**搜索框**在这里，过滤收在 `applyFilter()` 一处 ✓）（注意：它是**抽屉内容** ✗ 背景必须透明 ✗ 见第 6 节）|
| `tools/make-readme-shots.ps1` | **README 首页配图出图**：无头 Chrome 渲染 `design/ui-demo-glass.html?shot=1&scene=readme` → 压成 `screenshots/*.jpg` ✓ 改了 UI 想换首页配图就跑它 ✓ |
| `ui/ArtifactActivity.java` | **交付物应用内预览**（WebView：HTML / 图片 / 文本；看不了的给说明卡）|
| `ShareProvider.java` | **拍照用的极简 FileProvider**（本工程无 AndroidX ✗）：只暴露 `cache/dsh-share/`，一次性 Uri 授权给相机 App |
| `model/ChatItem.java` | 一行数据（`kind` 常量 + `time` + `images` + `files`）|

---

## 2. 发版流程

```powershell
cd L:\DSH日常问题\dsh-mobile-android
powershell -NoProfile -ExecutionPolicy Bypass -File .\release.ps1 -Version 0.86.42 -Notes "本次改了什么"
```
→ 手机：**设置 → 关于 → 检查更新 → 立即更新**（链路已实测可用 ✓）

**两条铁律** ✓
1. **更新说明用中文引号「」** ✗ 英文引号会**撑破 version.json** ✗（v0.86.41 已加 `Assert-Json` 自检兜底 ✓ 但别去踩 ✓）
2. **发完版补 `CHANGELOG.md`** ✓（用户要求"做了要让人看见" ✓）

**在 DSH 沙箱里发版的一个坑** ✗（2026-10-06 真遇到 ✗）：`release.ps1` 第 5 步要把 APK 同步到**工作区之外**的
`%USERPROFILE%\.dsh\local-plugins\dsh-mobile-access\app\`（面板「下载 App」和局域网直发用），
沙箱默认策略（workspace-write）会把它**拦下来** → 脚本在这一步中止 ✗ **后面 git commit/tag/push 都没跑** ✗
→ 以更宽权限**重跑**即可 ✓；但重跑会**再 bump 一次 versionCode** ✗（本次 58 → 59 → 60 ✗），
想保持版本号干净就**先把 AndroidManifest 的 versionCode/versionName 改回去**再重跑 ✓。
发完记得**线上核验**一次（清单 + APK + sha256 对不上就是白发了 ✓）：

```powershell
node tools\verify-live.js     # 拉 App 实际读的那条清单 → 主/备两条线路各下一遍 APK → 比 sha256
```
> 它跑的是 `UPDATE_MANIFEST_GITEE`（main 分支的 `dist/version.json`）✓ 别拿本地文件当"线上没问题" ✓
> ✗ **别用 PowerShell 的 `Invoke-WebRequest` / `curl.exe` 去核验** ✗ —— 在 DSH 沙箱里它们会报
> 「schannel 没有可用凭证」，看着像线上挂了，其实是环境 ✗（用 Node 自带 https ✓ 见脚本注释）

---

## 3. 用数据线装机（能大幅提速 ✓✓）

```powershell
$adb = 'C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb devices                      # 要看到 device（不是 offline / unauthorized）
& $adb install -r .\dist\dsh-mobile.apk
```

### ⚠️ 华为手机要过**两层**确认框（必须用 ADB 自动点 ✗ 否则 install 会一直挂着不返回 ✗）
```powershell
& $adb shell input keyevent KEYCODE_WAKEUP     # 先唤醒：息屏时华为会暂停安装 ✗
Start-Sleep -Milliseconds 700
# 把 install 放后台 job 起，然后 sleep 13 秒，再依次点下面三下：
& $adb shell input tap 895 2606                # ① 风险提示页 → 「继续安装」
Start-Sleep -Seconds 4
& $adb shell input tap 90 2310                 # ② 应用市场页 → 勾选「已了解…」
Start-Sleep -Milliseconds 900
& $adb shell input tap 630 2570                # ③ 应用市场页 → 「继续安装」
```
> 这套坐标是实机试出来的 ✓ 直接照抄 ✓
> 装完检查：`& $adb shell dumpsys package com.dsh.mobile | Select-String versionName=`

### 连接异常处理
| 症状 | 处理 |
|---|---|
| `offline` | `& $adb kill-server; Start-Sleep 2; & $adb start-server` ✓（屡试屡灵）|
| `unauthorized` | **必须在手机上点「允许 USB 调试」** ✗ 你点不了 ✗ 请用户点 ✓ |
| `no devices` | USB 掉了 ✗ 请用户重插 ✓ |

---

## 4. 自验方法（本会话最大的方法论收获 ✓✓）

**遇到"动画/抖动/没效果"类问题，不要猜** ✗ —— **连拍 + 逐像素对比** ✓：

```powershell
$dir = 'L:\DSH日常问题\_test\shots\jitterX'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
for ($i = 1; $i -le 6; $i++) {                       # 连拍 6 张，每 1.5 秒
  & $adb shell screencap -p "/sdcard/n$i.png"
  & $adb pull "/sdcard/n$i.png" "$dir\n$i.png" | Out-Null
  & $adb shell rm "/sdcard/n$i.png"
  Start-Sleep -Milliseconds 1500
}
& 'C:\Python314\python.exe' 'L:\DSH日常问题\_test\diff-shots.py' $dir
```
输出：相邻两张的**差异采样点数** + **差异包围盒** + **逐段分布** ✓
→ 一眼看出"哪一块在动" ✓（实测：抽搐问题从 **50318** 采样点降到 **514**、且全落在状态栏时钟 ✓）

其他自验手段：
- **抓崩溃**：`& $adb logcat -c` → 操作 → `& $adb logcat -d -t 500 | Select-String 'FATAL EXCEPTION|E AndroidRuntime'`
- **按住中截图**（看拖动/浮层效果）：`& $adb shell input motionevent DOWN x y` → `MOVE x y` → 截图 → `UP x y`

---

## 5. ✅ 上一版挂着的三件（v0.86.42 已做完 ✓ 这里是做法实录）

### ① 会话搜索（用户最想要 ✓ 114 个对话找不动 ✗）—— 已完成 ✓
- 落点：`ui/SessionListView.java`（**抽屉内容层**，背景必须透明 ✗ 见第 6 节）
- 做法：头部（大标题下面）加一个 `Ui.field()` 搜索框 + 左侧放大镜（`res/drawable/ic_search.xml`）+ 右侧 ✕（`ic_close.xml`）
- **过滤收在 `applyFilter()` 一个入口** ✓：`setRows(allRows)` 只存全量，显示的是过滤结果。
  MainActivity 那十几处 `listScreen.setRows(buildRows())` **一处都没改** ✓（这是关键：改宿主侧必然漏改某一处）
- 命中判据：标题 / `displayForList()` / cwd（含末段目录名）/ 预设名 / 分组标题，大小写不敏感
- 状态行：搜索时显示「找到 N 条 / 共 M 条」（宿主设的「已连接…」存在 `lastStatus`，清空后还原）
- 空态分两种：「还没有对话」vs「没有找到「关键词」」
- **子会话要展开**：宿主实现 `onSearchActive(active)` → `searchExpandChildren` → 重算行时把所有父会话展开 ✓
  （不展开的话，命中折在父会话里的子智能体会话**搜不出来** ✗）
- 抽屉关掉时 `onDrawerClosed()` 清词 + 收键盘 ✓（MainActivity.closeDrawer 里调）

### ② 「＋」加拍照 —— 已完成 ✓
- 落点：`MainActivity.onPickImage()` 现在先弹「从相册选择 / 拍照」；相册那段原样搬进 `pickFromGallery()`
- **要自己实现 FileProvider** ✗（本工程无 AndroidX ✗ `libs/core-3.5.3.jar` 是 zxing 不是 core ✗）
  → 新建 `src/com/dsh/mobile/ShareProvider.java`（约 180 行）+ 清单里 `<provider authorities="com.dsh.mobile.share" exported="false" grantUriPermissions="true">`
- 拍照落点：`cacheDir/dsh-share/shot-<时间戳>.jpg`（App 私有，不需要任何存储权限 ✓ 拍前顺手删 24 小时前的）
- **两个必须做对的点** ✗（错一个就是"点了没反应 / 拿到糊图"）：
  1. 清单里声明了 CAMERA 就必须先拿**运行时**授权 ✗（Android 11+ 未授权时系统直接拒绝 `ACTION_IMAGE_CAPTURE` ✗ 不报错、最难查）→ `REQ_CAM_PERM` 拿到后再拍
  2. 必须给 `EXTRA_OUTPUT` + `FLAG_GRANT_WRITE_URI_PERMISSION`（不给就只有缩略图 ✗）
- 成败判据是**文件**（`exists() && length() > 0`）✗ 不是 `resultCode` ✗（部分 ROM 取消也回 OK）
- 拍完直接走现成的 `attachImage(Uri.fromFile(f))` ✓（待发那一整套复用，没动 ✗）

### ③ 产物应用内预览 —— 已完成 ✓
- 新建 `src/com/dsh/mobile/ui/ArtifactActivity.java` + 清单里一个 `<activity>`（`exported=false`）
- 点交付物行 = **看**；长按 = 菜单（下载到手机 / 复制路径）—— 菜单在宿主侧弹 ✓（`onFileMenu`）
- 下载**复用**现成的那条链路 ✓：`startTransfer(item, path, preview)` 只多一个落点开关
  （`preview=true` → `cache/artifact/<原名>`；`false` → 原来那套 MediaStore「下载」目录）
- WebView 三件套：`setAllowFileAccess(true)`（**Android 11 起 file:// 默认关着** ✗）、JS 关闭、外链不在本页打开
- 能看 HTML / 图片 / 纯文本；**看不了的不硬撑** ✓（docx/pdf/zip → 说明卡 + 复制路径）
- 坑：**文件名要保留扩展名** ✗（WebView 按扩展名认类型，`.part` 会把 HTML 当纯文本吐出来 ✗）

---

## 6. 踩过的坑（**别再踩** ✗ 全都写死在源码注释里了）

| 坑 | 结论 |
|---|---|
| **PS 5.1 读无 BOM 的 UTF-8 `.ps1`** | 会乱码 ✗ **改完 `.ps1` 必须跑 `tools/ensure-ps1-bom.ps1`** ✓ |
| **PowerShell 里插 Java 代码** | 英文引号会和 PS 字符串打架 ✗ 一律用中文引号「」做注释 ✓ |
| **d8 命令行过长** | 类一多就 `The input line is too long` ✗ → 已改成走 `@响应文件` ✓ |
| **`version.json` 被撑破** | `-Notes` 里混英文引号会让 JSON 结构破 ✗ → `url` 字段被说明文字挤占 ✗ → **App 更新必失败** ✗（用户实测过 ✗）。已加 `Assert-Json` 自检 ✓ |
| **更新源指向上游** | 代码里 jsDelivr / GitHub raw 两条**写死上游作者仓库** ✗ → 检查更新永远说"已是最新" ✗ 已停用 ✓；`release.ps1` 的 `mirror` 也已改为本镜像 ✓ |
| **`adb install` 卡死** | 是华为的确认框 ✗ 不是 bug ✗ 按第 3 节点掉 ✓ |
| **抽屉里不能有背景色** | `SessionListView` 是**抽屉内容** ✗ 背景必须**透明** ✗ 否则把抽屉玻璃整块盖住 ✗（用户为此报了三次 ✗）|
| **刻度尺 View 宽度** | 必须 `MATCH_PARENT` ✗ 只给 230dp 会把预览气泡裁成「左边直角右边圆角」✗（气泡要 300dp ✗）|
| **ListView 抖动** | 别手动 `setSelectionFromTop` 补偿前插 ✗ 会和 ListView 排版时机错开 → 来回弹 ✗；用 `TRANSCRIPT_MODE_ALWAYS_SCROLL` ✓ 但**只在补历史期间开** ✗（常开会让流式输出的高频提交每秒滚底十几次 → 旧会话抽搐 ✗）|
| **语音** | 手机系统 ASR 在国产 ROM 上不可用 ✗ 定案：**只关联输入法转文字** ✓ 不做 App 内录音识别 ✗ |
| **Android 11+ 的 CAMERA 权限** | 清单里声明过 CAMERA，却**没有运行时授权**时，系统会**直接拒绝** `ACTION_IMAGE_CAPTURE` ✗ 不报错、点了没反应、最难查 ✗ → 拍前先要权限 ✓ |
| **WebView 读 `file://`** | Android 11 起 `setAllowFileAccess` 默认 **false** ✗ 不开就是白屏 ✗；且 WebView **按扩展名**认类型 ✗ → 预览文件必须**保留原名**（`.part` 会把 HTML 当纯文本吐出来 ✗）|
| **FileProvider** | 本工程**没有 AndroidX** ✗（`libs/core-3.5.3.jar` 是 zxing ✗ 不是 androidx.core ✗）→ 用不了 `androidx.core.content.FileProvider` ✗ 自己写 `ShareProvider` ✓（只暴露 cache 一个目录 + 单段文件名 + canonical 复核 + 一次性 Uri 授权）|
| **交付物行点了就下载** | 已改 ✗：**点 = 应用内看**、**长按 = 菜单**（下载到手机 / 复制路径）✓ 改这块先看 `ChatAdapter.filesCard()` 与 `onPreviewFile/onFileMenu` ✓ |
| **`downloadState` 是每「条消息」一个** | 一条消息挂多个交付物时，状态文案会**同时显示在所有行**上 ✗（历史遗留 ✗ 本次没动 ✗ 要改得给每个文件单独的状态键）|
| **ADB `no devices`** | `kill-server` 之后还是空的 = **线掉了** ✗ 脚本怎么弄都没用 ✗ 必须请用户重插 ✓（本次真实遇到 ✗）|
| **Gitee 把 README 判违规** | 仓库主页 README 区被换成「内容可能含有违规信息」+ `raw` 取 README 返回 **451** ✗（同仓库其它文件全 200 ✓）。**先别怀疑 push** ✓：`node tools/check-gitee-visibility.mjs` 一眼判定（已实测：线上 README 与本地**逐字节一致** ✗ 推送没问题 ✗）。实测结论：**只改几行没用** ✗、逐段切片反而全放行 ✗、**整篇实质重写才放开** ✓ → 遇到就重写那篇文档；重写无效再去 Gitee 官方反馈仓库 `oschina/git-osc` 提 Issue ✓ 记录见 `ORIGIN-AND-LICENSE.md` 第四节 |
| **README 首页配图** | **别用真机截图** ✗（含设备名 / 内网地址 / 令牌尾号 ✗ `.gitignore` 也挡着 `*.png` ✓）→ 跑 `tools/make-readme-shots.ps1` ✓ 出的是「按 App 现有实现绘制」的界面示意（文案/路径全占位符 ✓）；要改成别的场景就改 HTML 里 `scene=readme` 的那段文字映射 ✓ |
| **动画/抖动/没效果** | **不要猜** ✗ 用第 4 节的连拍 + 逐像素对比 ✓ |

---

## 7. 用户偏好（很重要 ✓ 决定做事方式）

1. **能简约最好、上手快、不麻烦** ✓ —— 加控件前先想能不能用现有手势/元素解决 ✓
2. **宁可说"没做完"，也不塞半成品** ✓（他明确认可这条 ✓）
3. **改完要能自证** ✓ —— 他常自己装自己试 ✓ 所以每次发版都要能验证 ✓
4. **他要"真的有效果"** ✗ 不是"代码改了" ✗ —— 踩过：以为改了层级 ✗ 其实是被裁剪 ✗
   → **遇到"没效果"先想办法量化**（截图/连拍/日志 ✓）
5. **做了的活要让人看见** ✓ —— 每次改动补 `CHANGELOG.md` ✓（他明确要求过 ✓）
6. 中文交流 ✓ 回复别太长 ✓ 重点用表格 ✓

---

## 8. 设计要点（改 UI 前看一眼 ✓）

- **玻璃层级**：卡片 `GLASS` 65% ／ 顶栏 `GLASS_BAR` 72% ／ 输入 `GLASS_INPUT` 77% ／ 弹窗 `GLASS_SHEET` 90% ／ 抽屉 `GLASS_DRAWER` 66%（真模糊生效时）
- **环境底**：`Ui.AmbientBg`（三段线性渐变 + 4 个径向光晕）；6 套配色的 id 在 `Ui.PALETTE_IDS`（设置 → 主题 → 背景配色 ✓）
- **真模糊在这台 ROM 上不生效** ✗ → 用「内容缩到 1/6 再放大」的廉价模糊替代 ✓（见 `DrawerHost.snapshotContent()`）
- **所有正文都走 `Ui.md()` 一个入口** ✓ 改渲染只改这一处 ✓（表格/链接/代码块都在里面 ✓）
- **定位条**：固定 10 格窗滚动 ✓ 刻度以灰底中线居中 ✓ 预览气泡与刻度尺**共用 `centerY()`** ✓

---

## 9. 当前验证状态

| 项 | 状态 |
|---|---|
| App 启动 | ✓ 无 FATAL（logcat 实测）|
| 更新链路 | ✓ 端到端实测（清单合法 → 下载 1018KB HTTP 200 → sha256 一致）|
| v0.86.42 线上核验 | ✓ 从 Gitee 实拉：清单 HTTP 200（versionCode 59）→ APK 1038.2 KB HTTP 200 → **sha256 与清单一致** ✓（脚本 `_test/verify-live.js`）|
| 画面稳定性 | ✓ 连拍逐像素验证（内容区零变化）|
| 构建 | ✓ `build.ps1` 全绿（v0.86.42：1038 KB，签名指纹与基线一致）|
| 包内容自检 | ✓ 反查 APK：`<provider>`（authorities + grantUriPermissions）✓、`ArtifactActivity` ✓、`ic_search/ic_close/ic_file` ✓、dex 里 5 个新符号 ✓ |
| **待用户验证** | 会话搜索 / 拍照 / 产物预览 —— 三件都在 v0.86.42 里 ✓ **但真机运行时还没验过** ✗（发版时 USB 掉了 ✗ 用户选择先发版 ）：装完点一下就知道；有崩溃先 `adb logcat -d -t 500 \| Select-String 'FATAL EXCEPTION\|E AndroidRuntime'` ✓ |
