# GlassUI 迭代报告 V3.108 | 2026-10-12（发布前清理 + 非玻璃弹窗清单 + **发版脚本失效**）

> **触发**：用户「列出来 23 处非玻璃弹窗」+「抽屉旧弹窗路径的死代码、`dist/archive/` 旧包、
> `HANDOFF.md` 里过期的 `build.ps1` 说明 **清理掉**」。

---

## 1. 清理（3/3 完成）

| 项 | 处置 | 证据 |
|---|---|---|
| 抽屉旧弹窗路径的死代码 | 删除 `GlassSessionList.kt` 的 `state.wsFor / state.menuFor` + 本面板内 `GlassSheetOverlay` 整段（自 V3.99 起两处 ⋯ 都回调宿主，那两个字段再也不会被赋值 ⇒ 永不执行）| 删后**构建通过**；`menuFor/wsFor` 声明一并移除 |
| `dist/archive/` 旧包 | **已删除**（先做路径校验：确认是 `F:\AI\程序开发\dsh-mobile-android\dist\archive`）| 删掉 2 个旧 APK 共 **10.17 MB** |
| `HANDOFF.md` 过期的 `build.ps1` 说明 | 文首加**醒目变更说明**（统一解释下文 14 处 `build.ps1` 提及）+ 表格两行更正 + 补上 gitee `--tags` 盲推警告 | 见 §3 |

## 2. ⚠️ 新发现的**发版阻塞**：`build.ps1` 已跑不动，`release.ps1` 依赖它

实测（`pwsh -File build.ps1`）：**200 个 javac 错误**，`GlassSheetOption`、`kotlin.*` 全部找不到 ——
**它只走 `javac`，不编译 Kotlin 源**，而本工程现在是 **Java + Kotlin/Compose 混合** ✗。

而 `release.ps1` 的构建步骤**透传给 `build.ps1`**（`release.ps1:47`）⇒ **`release.ps1` 目前跑不通** ✗✗
（HANDOFF 规定"版本号/签名/`dist/` 生成物只有它能动"⇒ 它是发版的唯一出口）。

**修复方案（发版前必须做，约 20 行改动）**：把 `release.ps1` 的构建步骤从 `build.ps1` 换成 Gradle
（`gradle.bat :app:assembleRelease`）＋ 从 `app/build/outputs/apk/release/` 取包；其余（bump versionCode、
写 `dist/version.json`、`git add -A`、commit、`tag -a v0.87.0`、push、同步 gitee）**逻辑都保留**。
改完用 `-SkipPush` **干跑一次**验证，再正式发。

## 3. 非玻璃弹窗清单（我按代码实测列出，与专家的"23 处"口径基本吻合）

### A. **应迁移**到玻璃弹窗（用户可见，按 `GlassUI_SPEC_DIALOG.md` 四形态选）

| # | 位置 | 用途 | 目标形态 |
|---|---|---|---|
| 1 | `MainActivity.java:2154` | 添加设备 | `showForm` |
| 2 | `MainActivity.java:3912` | 思考等级 | `showList` |
| 3 | `MainActivity.java:4236` | 切换项目 | `showList` |
| 4 | `MainActivity.java:4296` | 任务列表 | `showList` |
| 5 | `MainActivity.java:4384` | 目标 / 任务（旧弹窗；玻璃模式已 early-return，**非玻璃模式仍用**）| `showForm`/文本 |
| 6 | `MainActivity.java:5499` | 这个生成物（生成物面板的长按二级菜单）| `showList` |
| 7 | `MainActivity.java:5789` | 这个生成物（旧面板同款）| `showList` |
| 8 | `MainActivity.java:5919` | 权限 | `showList` |
| 9 | `MainActivity.java:7621` | 重命名对话 | `showForm` |
| 10 | `MainActivity.java:7985` | ⋯ 菜单（设备/其它）| `showList` |
| 11 | `MainActivity.java:8202` | 修改名称 | `showForm` |
| 12 | `MainActivity.java:8246` | 删除「…」 | `showConfirm(danger)` |
| 13 | `MainActivity.java:8687` | 意见反馈 | `showForm` |
| 14 | `MainActivity.java:8979` | 手动添加设备（旧）| `showForm` |
| 15 | `MainActivity.java:9867` | 发现新版本 v… | `showConfirm` |
| 16 | `MainActivity.java:9922` | 更新包已下载好 | `showConfirm` |
| 17 | `MainActivity.java:9984` | 应用内更新 | `showConfirm` |
| 18 | `MainActivity.java:10022` | 下载失败 | `showConfirm` |
| 19 | `MainActivity.java:10121` | 更新包校验失败 | `showConfirm` |
| 20 | `MainActivity.java:10135` | 安装更新 v… | `showConfirm` |
| 21 | `MainActivity.java:10153` | 还差一步：允许安装应用 | `showConfirm` |
| 22 | `MainActivity.java:10332` | 用剪贴板里的配对串配对 | `showConfirm` |
| 23 | `MainActivity.java:10390` | **粘贴配对串**（专家实测为系统白底框）| `showForm` |
| 24 | `SessionListView.java:389` | 旧列表页的一处弹窗 | 视语义 |

### B. **有意保留**的回退落点（不要迁）

- `MainActivity.java:2054` —— 抽屉会话 ⋯ 的**非玻璃模式**回退（`showGlassMenu` 返回 false 时才走）
- `MainActivity.java:4426 / 4515` —— 待发送菜单 / 修改排队内容的**非玻璃模式**回退

> 为什么保留：这三处在玻璃模式下**永远不会执行**（前面已 early-return / 已走玻璃 sheet），
> 删掉会让"关掉玻璃开关"的兜底路径缺失 ⇒ 按 `GlassUI_SPEC_DIALOG.md` 的精神**留作回退**。

### C. 非调用点（注释/文档提及，无需处理）

`MainActivity.java:3691 / 3701 / 5026 / 5038`（注释里引用 `ModelSheet`）、`Ui.java:737-743`（`Ui.dialog` 的**定义**本身）。

---

## 4. 当前交付状态

- 构建通过；真机已装并重启（sha `1825c8bd…` 之后这一版会刷新，见下）
- **等你测完**（弹窗右条 + 返回键两条关闭路）之后，发版前还差**一件必做**：把 `release.ps1` 的构建步骤切到 Gradle（§2）
