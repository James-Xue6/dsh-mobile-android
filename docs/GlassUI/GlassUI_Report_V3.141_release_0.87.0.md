# 发布报告 V3.141 · 0.87.0 | 2026-10-12（**已发布**：GitHub + Gitee 同步）

## 一、发布事实（全部实测）

| 项 | 值 |
|---|---|
| 版本 | **0.87.0**（`versionCode=27`） |
| APK sha256 | `46ba8215c77f7f543fb93e04a984c7641de1dcdd30a2d5e8be2d906018e4b45d` |
| 大小 | `9628786` 字节 |
| GitHub | commit `092a2e4` + tag **`v0.87.0`** ✓ |
| Gitee | `HEAD:main` `092a2e4` + tag **`v0.87.0`** ✓（**单推**，未 `--tags` 盲推 ✓）|
| 线上自证 | Gitee 与 jsDelivr 的 `version.json` 均 = `27 / 9628786 / 46ba8215…` ⇒ **与本地逐字一致** ✓ |

## 二、本次交付内容（用户可见）

| # | 改动 | 状态 |
|---|---|---|
| 1 | 弹窗全面玻璃化 + **归位到各自界面**（设备页/设置页/对话页/抽屉各一套宿主）| ✅ |
| 2 | 修复 **更新链路版本号不前进**（Gradle 硬编码 ⇒ APK 永不前进 ⇒ 永久更新循环）| ✅ **端到端验证**：manifest 27 ⇒ APK 实测 27 |
| 3 | 修复 **返回键关不掉弹窗**（只查对话页 ⇒ 改走路由）| ✅ |
| 4 | 修复 **待发送「修改」与菜单叠在一起**（`showForm` 漏了反向清理 `listOptions`）| ✅ |
| 5 | 修复 **抽屉 ⋯→重命名** 没反应（宿主被移出组合 ⇒ 改为永远组合 + 稳定兜底层）| ✅ |
| 6 | 修复 **对话页跳动**（我自己埋的诊断在主线程热路径写盘 ⇒ 已撤）| ✅ |
| 7 | 输入框改为**平台控件**（长按走系统那套）—— 外观逐项对齐 | ⏳ **长按仍不出系统「粘贴/全选」** ⇒ **未解决** |
| 8 | 移除全部临时诊断埋点（K4）| ✅ |
| 9 | 清理本地 34 个对方 tag（V1）| ✅ 24 个自己的 tag 保留 |
| 10 | `release.ps1`：构建步骤切 Gradle + **版本号硬断言** | ✅ 干跑通过 |

## 三、未解决（已与用户约定下次处理）

- **输入框长按调不出系统「粘贴 / 全选 / 选择手柄」** ✗
  已排除：Compose 三种手势写法（`detectTapGestures` / `awaitLongPressOrCancellation` / `Initial` 通道）；
  已换平台 `EditText` 并关闭 `importantForAutofill`（针对"长按只有自动填充"的现象）⇒ **仍不出现**。
  下一步（下次会话）：在输入框的 `AndroidView` 层加取证，确认长按事件到底有没有到达 `EditText`，再动手。

## 四、发布链路（本次建立、以后照做）

1. 清本地非 main 历史的 tag（`git tag | Where-Object { git merge-base --is-ancestor $_ main; … }`）；
2. `release.ps1 -Version X.Y.Z -Notes "…"`（`-SkipPush` 先干跑）；
3. 复验四项：aapt2 的真实版本号 / manifest vs version.json / size+sha256 / `git status` 干净；
4. `git push origin main` + `git push origin vX.Y.Z`；
   `git push gitee HEAD:main` + **`git push gitee vX.Y.Z`**（**禁 `--tags`** ✗）；
5. 上传后自证：从 **Gitee** 与 **jsDelivr** 各取一次 `version.json`，与本地 `sha256/size/versionCode` 对齐。
