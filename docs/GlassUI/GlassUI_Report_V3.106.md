# GlassUI 迭代报告 V3.106 | 2026-10-11（发布前阻塞项修复 + 0.87.0 发版方案）

> **触发**：用户「测试完没问题的话，给我**热推送**吧，PC 端移动插件啥的有更新一起更新下，
> **github 和 gitee 两个库同步更新**，并按之前的 **handoff 规则**还有**签名**啥的仓库规则，
> 同步更新下个**版本号 0.87.0**」。

---

## 1. 阻塞项修复（先满足"测试完没问题"这个前提）

| # | 问题 | 修复 | 复验 |
|---|---|---|---|
| **B1** | 发布包**两个桌面图标** | 删掉原型页 `GlassDevicePageActivity` 的 launcher intent-filter（保留 `exported=true`，adb/自动化仍可 `am start`） | ✅ **已验证**：`aapt2 dump badging` 现在只有 **1 条** `launchable-activity`（`MainActivity`）|
| **B2** | **返回键关不掉弹窗**（关的是抽屉） | `handleBackKey()` 最前面加"弹窗可见 ⇒ 只关弹窗"分支 | ⏳ 待真机/模拟器点一次 |
| **B3** | 点面板右侧露出条**关错对象** | `DrawerHost` 加 `setSheetOnTop(boolean)`（**默认 false = 原行为**，符合"既有件加可选参数"规则）；弹窗显示期间跳过右条拦截；`onHide` 时复位 | ⏳ 同上 |
| **B4** | `dist/version.json` 元数据与实际产物不符 | **不手改** —— 交给 `release.ps1` 重新生成（见 §2）| ⏳ 发版时 |
| **B5** | 大量源文件未入库 | `release.ps1` 会 `git add -A`（见 §2）| ⏳ 发版时 |

另：模拟器上启动**未发现崩溃**（logcat 无 FATAL），App 正常连上真实网关（专家配对仍在）。

## 2. 0.87.0 发版方案（严格按 `HANDOFF.md`）

**唯一入口**：`release.ps1`（铁律：**版本号 / 签名 / `dist/` 生成物只有它能动**）

```
pwsh -File release.ps1 -Version 0.87.0 -Notes "<本次变更摘要>"
# 习惯项：-SkipPush（先本地出包+清单，人工查后再推）；-AllowLocalKey 仅非官方密钥时用（与 -SkipPush 互斥）
```

它按脚本内 7 步走：校验版本号格式并与 `AndroidManifest.xml` 比对 → **自动 bump `versionCode`** → 构建/签名
→ 写 `dist/` + `dist/version.json` → `git add -A` + `commit` + `tag -a v0.87.0` → push 分支与 tag（GitHub）
→ 有 `gitee` 远程则同步（`HEAD:main` + tag）✓

### ⚠️ 我在流程里发现的两个风险（发版前必须处理）

| 风险 | 依据 | 处置 |
|---|---|---|
| **`tools/push-gitee.ps1` 用的是 `git push gitee --tags`（盲推）** | `HANDOFF.md:200-221`：本地残留对方 fork 的 **33 个 tag**（`v0.86.10~v0.86.42`），盲推会把它们**灌进 Gitee 公开仓库** | **做法 A（HANDOFF 推荐）**：不跑那个脚本，只单推本次 tag：`git push gitee v0.87.0` + `git push gitee HEAD:main` |
| **`release.ps1` 的构建步骤透传给 `build.ps1`** | `release.ps1:47`「透传给 build.ps1」；但本仓库这两个脚本的可用性存疑（本轮全部构建走的是 Gradle 9.6） | **先干跑**：用 `-SkipPush` 跑一次，确认它真的能出包；**若它的构建步骤失败**，则改为"Gradle 出包 + 手工按脚本的产物清单写 `dist/`（相当于用脚本同款规则）"，并把这一步如实记进报告 |

### 版本号口径

`HANDOFF.md:171-186` 已定案：主线以本仓库编号为准（当前 `versionCode 26 / 0.86.9`）；
对方 fork 的 `0.86.10~0.86.42（27~59）`**不并入主线**。⇒ `0.87.0` 由 `release.ps1` 自动取下一个 `versionCode`（预期 **27**）✓

## 3. PC 端插件同步

需要一起核对/同步的三份（都在 `pc-plugin/` 与网关侧）：

| 件 | 现状 | 动作 |
|---|---|---|
| `pc-plugin/patches/patch-gateway-history-diag.ps1` | 已应用且生效（网关历史诊断日志）| 比对源码版本与线上是否一致，一致则**无需重推** |
| 桌面 `app.asar` 的 `instruction-hint` 补丁 | 已安装（`tools/dsh-asar-patch/`）| 同上 |
| **队列基线补丁（待你点头）** | **尚未写**：`index.mjs:2269/3130/3385` 的懒加载导致"重开软件待发送消失" | 要不要随本次发版一起做？ |

## 4. 我停在这里的原因（需要你一句话）

你给的前提是「**测试完没问题的话**」。目前：
- ✅ B1 已复验；
- ⏳ **B2 / B3 需要一次真实点击**（真机不吃注入点击，模拟器要另外导航到对话页）。

⇒ **麻烦你在手机上点一次抽屉 ⋯，然后：**
1. 点弹窗**右侧那条空白区** ⇒ 应该是**关掉弹窗**（不是关抽屉）；
2. 按一次**返回键** ⇒ 也应该是**关掉弹窗**。

这两条一过，我就按 §2 跑 `release.ps1 -Version 0.87.0`（先 `-SkipPush` 出包自检，再按做法 A 推 GitHub + Gitee 单 tag），
并把 PC 端插件一起核对同步。
