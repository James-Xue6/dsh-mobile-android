# 一起维护这个项目（新手向 · 全是"点哪里"）

> 这份文档面向**不熟悉 git 的人**。三条路任选一条，路 3 最省事。
> 前提：你已经有一个 GitHub 账号（没有就去 https://github.com/signup 注册，1 分钟）。
>
> 📌 **上传 / 版本号 / 签名的硬规则在 [`HANDOFF.md`](HANDOFF.md)** —— 那份是共创的正式规则，
> 本文是"点哪里"的新手引导；两者冲突时**以 `HANDOFF.md` 为准**。

---

## 先说结论：选哪条

| 你的情况 | 建议走 |
|---|---|
| 只想反馈 bug / 想要个功能 | **路 3**：发帖说清楚就行 |
| 偶尔改一处代码，想直接给我 | **路 2**：Fork + Pull Request |
| 长期一起干（我会给你写权限） | **路 1**：先让我把你加成协作者，再走路 2 |

---

## 路 1 · 让作者把你加成「协作者」（这条路要**作者**操作）

**作者要做**（在 GitHub 网页上，不用命令行）：

1. 打开 <https://github.com/James-Xue6/dsh-mobile-android>
2. 点上方 **Settings**
3. 左侧点 **Collaborators**（有的界面叫 **Collaborators and teams**）
4. 点绿色按钮 **Add people**
5. 输入你的 **GitHub 用户名**（不是昵称，是网址里那个），选中后点 **Add**
6. 权限选 **Write**（够用了，不需要 Admin）
7. 你的邮箱会收到邀请，**点邮件里的接受链接**（或打开 `https://github.com/James-Xue6/dsh-mobile-android/invitations` 接受）

**接受之后，你就能**：直接在网页上改文件、开分支、提 Pull Request。

> ⚠️ 即使加了协作者，**默认仍然不能直接改 `main`**——正常做法是改到自己的分支，再发 PR 给作者点合并。

---

## 路 2 · Fork + Pull Request（最标准，随时可用）

> 不需要任何人给你权限，任何 GitHub 账号都能做。

**第 1 步 · 复制一份到你自己的账号**

1. 打开 <https://github.com/James-Xue6/dsh-mobile-android>
2. 右上角点 **Fork** → 点 **Create fork**
3. 现在你有了 `https://github.com/<你的用户名>/dsh-mobile-android`

**第 2 步 · 改代码（两种方式，选一种）**

- **网页改（只适合改一两个文件）**：在你的 fork 里点进文件 → 右上铅笔图标 → 改 → 拉到底点 **Commit changes**
- **本地改（推荐，能编译能测）**：

  ```powershell
  git clone https://github.com/<你的用户名>/dsh-mobile-android.git
  cd dsh-mobile-android
  # 改你的代码……
  git add -A
  git commit -m "说清楚你改了什么"
  git push
  ```

**第 3 步 · 发给作者**

1. 回到你的 fork，点 **Contribute** → **Open pull request**
2. 标题写清楚改了什么，说明里写"改了什么 / 为什么 / 怎么验证的"
3. 点 **Create pull request**

**第 4 步 · 作者审核**

作者在网页上看到 PR → 看一眼 → 点 **Merge pull request**。合并后代码就进主线了。

> **注意**：合并 ≠ 发版。要让所有人的 App 更新，得**作者**跑 `release.ps1`。

---

## 路 3 · 完全不碰 git（最省事，推荐给不熟 git 的人）

你只要把下面这些发给作者就行：

- **bug**：现象 + 复现步骤 + 手机型号 + App 版本号（**设置 → 关于**里看）
- **想要的功能**：你想干什么、现在是怎么个不好用
- **改好的文件**：把文件直接发过来（作者会代你提交）
- 截图 / 录屏：比文字有用得多

作者的 AI 助手会把你的内容变成代码改动 → 提交 → 发版。

---

## 不管你走哪条路：改代码的几条规矩

> 这些是这个项目的**硬约定**，不遵守的改动会被打回。

1. **改完必须能编译**：仓库根目录跑
   ```
   pwsh -File .\build.ps1
   ```
   末尾要看到「证书 SHA-256 指纹与期望一致」和产物路径，否则别说改好了。
2. **动了 `src\com\dsh\mobile\net\` 就跑协议回归**：
   ```
   pwsh -File .\harness\run-protocol-v2-test.ps1
   ```
   要 10 条断言全绿（exit 0）。
3. **动了 UI 且有模拟器/真机就顺手跑**：
   ```
   pwsh -File .\tools\verify-4features.ps1
   ```
4. **绝对不要动版本号**：`AndroidManifest.xml` 里的 `versionCode` / `versionName`
   由作者跑 `release.ps1` 统一管，你改了会打架。
5. **不要提交密钥**：`keystore.local.ps1`、任何 `.jks` / token / 配对串，一律不要进仓库。
6. **不要臆造接口**：DSH / 网关的 API、字段名、配置键都必须先在源码或
   `docs/PROTO-FINDINGS.md` 里核实，不许"我觉得应该是"。
7. **给电脑端网关打补丁必须写成脚本**：放进 `pc-plugin/patches/`，
   要**幂等**（重复跑没事）+ 支持 `-Revert`，并在 `install.ps1` 里挂上。
   （原因：网关升级会覆盖 `node_modules` 里的改动，只有脚本能重放。）
8. **不许加第三方依赖**：这是个"手搓 View 树 + javac 直编"的项目，
   刻意不引 androidx / Gradle，视觉只能从 `ui/Ui.java` 取值。

---

## 提 Issue 时带上这些（会快很多）

```
App 版本：设置 → 关于 里看（例如 0.86.9 / versionCode 26）
手机型号：例如 荣耀 Magic5 / Android 14
PC 端：DSH 版本（例如 0.2.0-rc.2）+ 网关插件版本
现象：我点了什么 → 期望什么 → 实际什么
复现：能不能每次都复现（必现 / 偶发）
截图/录屏
```

---

## 有问题？

直接在项目群问，或开个 Issue：<https://github.com/James-Xue6/dsh-mobile-android/issues>
