# GlassUI 发布前检查 | V3.105 · 2026-10-11（验收测试工程师审计 + 主理人自查）

> **触发**：用户「**@移动端测试工程师 可以了，这次成了，你整体检索下，看看还有没有明显的 bug，发布前的检查**」。
> 由「验收测试工程师」执行整体审计，主理人（本会话）对关键结论逐条自查后交付。

---

## 1. 上线判定：**不通过**（5 项阻塞）

工程/产物本身**可构建、可安装、签名与分发哈希一致**，但存在 5 项**发布级别**的缺陷。

## 2. 用户本轮关心的抽屉 ⋯ 弹窗：**核心通过**

| 检查项 | 结论 | 证据 |
|---|---|---|
| 弹窗在**抽屉面板之上** | ✅ 通过 | `MainActivity.java:7484` 加为 `DrawerHost` 最后一个子 View |
| **屏幕正中** | ✅ 通过 | dump：面板 `[108,868][972,1532]` → 中心 (540,1200) = 屏幕正中 |
| 与模型/生成物**同一取样层** | ✅ 通过 | `GlassSessionList.kt:140` → `GlassChatBackdrop.layer`；像素 σ 8.74 vs 10.02（同档）|
| 空闲**不画东西 / 不拦触摸** | ✅ 通过（实测） | `GlassSheet.kt:304` 提前 return；宿主存在前后列表区均值 205.17→205.93 |
| 关闭路①「关闭」按钮 | ✅ 通过 | 实测 弹窗=False、抽屉=True |
| 关闭路②**点遮罩** | ❌ **不通过** | 面板右侧那条约 40px 露出区点下去**关的是抽屉**（`DrawerHost.java:358` 抢事件）⇒ **B3** |
| 关闭路③**返回键** | ❌ **不通过** | `MainActivity.handleBackKey()` 没有弹窗分支 ⇒ 返回键关的是抽屉/什么都不关 ⇒ **B2** |
| 抽屉保持打开、点错可重选 | ✅ 通过 | 三态抽屉恒为 True；关弹窗后立刻再点 ⋯ 仍可弹 |

## 3. 五项阻塞项（主理人已自查确认）

| # | 阻塞项 | 证据（我自查） | 最小修复 |
|---|---|---|---|
| **B1** | **发布包有两个桌面图标** | `aapt2 dump badging`：`MainActivity` + `GlassDevicePageActivity`（「掌上通·新界面」原型页）| 删该页的 launcher intent-filter（`AndroidManifest.xml:120-123`）|
| **B2** | **返回键关不掉弹窗** | `handleBackKey()` 无弹窗分支 | 最前面加：`if (sheetState().getVisible()) { hide(); return; }` |
| **B3** | **点面板右侧关错对象**（关掉抽屉、弹窗还在） | `DrawerHost.java:349-362` 右条抢事件 | `DrawerHost` 加 `setSheetOnTop(boolean)`（默认 false = 原行为），弹窗期间跳过右条拦截 |
| **B4** | **更新链路元数据与实际产物不符** | `dist/version.json`：sha256 `34ae3280…` / size **1083595**，实际 `461e6b9f…` / **9612426** | **跑 `release.ps1`** 重新出元数据（禁手改）|
| **B5** | **仓库无法复现发布包** | `git status --porcelain` **42 条未提交**，含 `src/com`（glass 源码）| 发版前 `git add -A`（`release.ps1` 会做）|

## 4. 其余问题（P1/P2，不阻塞但建议修）

- **P1** `CropActivity.kt:80/97` **主线程全尺寸解码＋压缩**（ANR/OOM 风险；与本工程"不解码整图"的自述矛盾）
- **P1** `MainActivity.java:7475-7487` 弹窗宿主创建 `catch(Throwable ignored)` 且**先置标志位** ⇒ 一旦失败，⋯ **永久失效且无任何提示**
- **P2** `ArtifactActivity` WebView 无 `destroy()`；`GlassChatBackdrop/GlassDrawerBackdrop` 全局持有无清理；空 catch 约 144~151 处
- **P2** 抽屉内**旧弹窗路径已成死代码**（`GlassSessionList.kt:223-276`）；**仍有 23 处非玻璃弹窗**（实测「粘贴配对串」是系统白底框 ✗，违反 `GlassUI_SPEC_DIALOG.md`）
- **P2** `MainActivity.java:5444` 文案漏出 `**`；`dist/archive/` 旧包；`HANDOFF.md` 仍要求用**已失效的 `build.ps1`** 自证

## 5. 已验证通过（抽样）

构建 UP-TO-DATE 且无源码新于 APK；`dist` ↔ `.sha256` ↔ `app-release.apk` 三者哈希一致；
签名指纹与 `HANDOFF.md:231` 逐字一致；`targetSdk 36 / minSdk 26`；keystore 未入库；
`postDelayed` 全部挂在主线程 Handler（历史事故已修）；`GatewayClient` 用 volatile/synchronized；
Markdown/思考过程渲染正常；模型/权限/生成物面板正常；进会话不再自动弹权限面板；本轮无新增崩溃。

## 6. 未验证清单（需要用户动手）

1. 真机点一次抽屉 ⋯（真机不吃注入点击）；
2. 图片全屏查看（需含图片的消息）；
3. 待发送队列菜单（需真实排队消息）；
4. 应用内更新全链路（需先 `release.ps1` 发一版）；
5. 字号四档 / 完整-精简 / 上传底图取色（会改用户设置）；
6. 长会话反复归档/重命名（旧构建曾 `Key "f1" was already used`，静态看新版已不可能，未实测）。

> 完整逐条证据与复验脚本：`evidence/release-audit/PRE_RELEASE_CHECK.md`（专家产出）

## 7. 副作用声明

专家为做真机链路验证，**把模拟器配对进了本机网关**（设备数 17→18，名字形如 `sdk_gphone64_x86_64`）。
不需要时请在 DSH「移动设备」面板删除。未改动任何业务代码。
