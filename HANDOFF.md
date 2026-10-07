# HANDOFF · 共创上传规则（DSH 掌上通 / dsh-mobile-android）

> **这份文件给谁看**：一起维护本项目的共创者、共创者的 AI 助手，以及下一个接手本仓库的会话。
> **它解决什么问题**：防止上传（push / 发版 / 换包）时把仓库结构、版本号、签名弄坏。
>
> **一句话铁律**：**版本号、签名、`dist/` 里的生成物，只有 `release.ps1` 能动；
> 共创者只提 PR 或只交代码 —— 不发版、不打 tag、不碰密钥。**

| 项 | 值 |
|---|---|
| 仓库 | `F:\AI\程序开发\dsh-mobile-android`（分支 `main`） |
| 包名 | `com.dsh.mobile` |
| 当前版本 | `versionName 0.86.9` / `versionCode 26`（2026-10-07 实测） |
| 构建 | `build.ps1`（免 Gradle：aapt2 → javac → d8 → zipalign → apksigner） |
| 发版 | `release.ps1 -Version X.Y[.Z] -Notes "…"` |
| 同步 Gitee | `tools/push-gitee.ps1 -GiteeUser <用户名>` |
| 发版后核验 | `node tools/verify-live.js` |

---

## 0. 和其它文档的分工（别搞混）

| 文档 | 管什么 | 入库 |
|---|---|---|
| `CONTRIBUTING.md` | 新手怎么提（网页点哪里 / fork / PR） | ✓ |
| **`HANDOFF.md`（本文件）** | **上传、版本号、签名的硬规则** | ✓ |
| `NEXT-UPDATE.md` | 下一步计划 + 已完成记录 | ✓ |
| `docs/friend-fork/HANDOFF.md` | 对方 fork 那一侧的**历史**交接单 —— 是材料，不是规则 | ✓ |
| `docs/*.md`（`docs/` 根目录下的） | 协议核实 / 契约 / 任务明细 | ✗ **不入库**（见第 2 节） |

---

## 1. 三条远程：谁能推，谁只能读

| 远程 | 地址 | 用途 | 能不能推 |
|---|---|---|---|
| `origin` | `git@github.com:James-Xue6/dsh-mobile-android.git` | 主仓库（主线） | ✓ 只有作者推 |
| `gitee` | `https://gitee.com/yuan-junqian/dsh-mobile-android.git` | 国内镜像（**App 更新清单 + APK 主线路走它**） | ✓ 只有作者推 |
| `friend` | `https://gitee.com/tang-changxu/dsh-mobile-android.git` | 共创者的 fork | ✗ **只读参考** |

**三条铁规矩**：

1. **只往 `origin` 和 `gitee` 推**，永远不推 `friend`。
2. **禁止 `force push`、禁止 rebase/amend 已经推出去的提交、禁止删 tag。** 要回退就发一个新提交。
3. 任何代码或配置里的**下载/清单地址都不许指向 `friend`**（指过去 = 用户拿到的是别人签名的包，`sha256` 必然对不上）。

---

## 2. 结构地图：谁是权威源，谁是生成物，谁是禁区

### 2.1 权威源（要改就改这里）

| 路径 | 是什么 | 谁能改 |
|---|---|---|
| `src/com/dsh/mobile/**` | App 全部 Java 源码（44 个文件 / 约 2.57 万行；`MainActivity.java` 是主体） | 改代码可以，**改完必须能编译** |
| `res/**` | 资源（`drawable` / `drawable-nodpi` / `mipmap-*` / `values` / `xml`） | 同上 |
| `AndroidManifest.xml` | 清单 | 可改**内容**，但 `versionCode` / `versionName` **只有 `release.ps1` 能改**（见第 4 节） |
| `libs/core-3.5.3.jar` | **全工程唯一的第三方库**（zxing，用于扫码） | 禁止再加任何 jar |
| `build.ps1` / `release.ps1` | 构建与发版的**唯一出口** | 改它要极其小心，改完必须全流程跑一遍 |
| `tools/**` | 验证 / 运维脚本 | 改脚本必须**幂等**（重复跑没事） |
| `harness/**` | 协议层回归脚手架 | 动了 `src/com/dsh/mobile/net/**` 就要跑它 |
| `pc-plugin/**` | 电脑端插件 + 网关补丁脚本 | 补丁必须脚本化（见第 3 节第 8 条） |
| `design/ui-demo-glass.html` | UI 设计稿（出 README 配图用） | 可改 |
| `docs/friend-fork/**` | **唯一会被提交的文档目录** | 可改 |

### 2.2 生成物（禁止手工替换）

| 路径 | 谁生成 | 规则 |
|---|---|---|
| `dist/dsh-mobile.apk` | `build.ps1` / `release.ps1` | ✗ **不许手工拷一个包进去** |
| `dist/dsh-mobile.apk.sha256` | 同上（小写，格式 `<hash>  dsh-mobile.apk`） | ✗ 不许手工改 |
| `dist/version.json` | **只有 `release.ps1`** | ✗ 不许手工改（它是 App「检查更新」读的清单） |

> **为什么这么严**：App 下载 APK 后会**实算 `sha256` 与清单比对**，不一致直接丢弃。
> 手工换包 = 用户永远更新不了，而且报错看不出原因。

### 2.3 本地产物 / 禁区（永远不入库）

`.gitignore` 已经挡住下面这些，**别用 `git add -f` 硬塞**：

| 类别 | 具体 |
|---|---|
| 构建中间产物 | `.buildstage/`、`build/`、`out4/`、`*.class`、`*.dex`、`harness/out*/`、`harness/snapshot/` |
| 密钥与本地配置 | `keystore.local.ps1`、`*.jks`、`*.keystore`、`*.p12`、`local.properties`、`.tools/` |
| 运行产物 | `tools/runs/`、`tools/logs/`、`harness/*.log` |
| 取证与临时 dump | `evidence/`、`.verify-step/`、`.verify-e2e/`、`*.dump.xml`、`ui-*.xml` |
| 真机截图 | **任何位置的 `*.png`**（只放行 `res/**/*.png`）—— 截图含设备名 / 内网地址 / 令牌尾号 |
| 本地备份 | `.glass-backup-*/`、`AndroidManifest.xml.bak*`、`hs_err_pid*.log`、`dist/archive/` |

> ⚠️ **`docs/*.md` 被 `.gitignore` 第 36 行吃掉了** —— 放在 `docs/` **根目录**的文档
> **不会被提交**，对方在仓库里根本看不到（例如 `docs/PROTO-FINDINGS.md`）。
> 要给别人看的文档：**放仓库根目录**，或放进 `docs/friend-fork/`。

---

## 3. 上传规则（硬规则 · 逐条）

1. **只推 `origin` 和 `gitee`**；`friend` 只读。禁止 force push / rebase 已推提交 / 删 tag。
2. **分支规则**：`main` 只由作者合并。共创者一律开 `feat/<短描述>` 分支 → 提 PR；
   不熟 git 就直接把改好的文件交给作者（作者代提交）。
3. **禁止手工改**：`versionCode`、`versionName`、`dist/version.json`、任何 `v*` tag。
4. **禁止提交密钥**：`keystore.local.ps1`、任何 `.jks` / `.keystore` / `.p12`、
   `DSH_KS_PASS`、配对串、token。
5. **禁止提交**第 2.3 节列出的任何东西（截图、取证、运行日志、本地备份、崩溃日志）。
6. **禁止加第三方依赖**：这是个「手搓 View 树 + javac 直编」的工程，**刻意不引 androidx / Gradle**；
   视觉只能从 `ui/Ui.java` 取值。
7. **不要臆造接口**：DSH / 网关的 API、字段名、配置键，必须先在源码里核实（别"我觉得应该是"）。
8. **给电脑端网关打补丁必须写成脚本**：放进 `pc-plugin/patches/`，
   要**幂等**（重复跑没事）+ 支持 `-Revert`，并在 `pc-plugin/install.ps1` 里挂上。
   （原因：网关升级会覆盖 `node_modules` 里的改动，只有脚本能重放。）
9. **上传前 `git status` 必须干净**。`release.ps1` 用的是 `git add -A` ——
   工作区里任何未跟踪的脏文件（备份目录、`.bak`、崩溃日志、临时脚本）都会被**一起提交**。
10. **上传前必须自证**：`build.ps1` 全绿（末尾要看到「证书 SHA-256 指纹与期望一致」）；
    动了 `src/com/dsh/mobile/net/**` 还要跑协议回归 10/10 全绿。
11. **提交信息说清楚**：`feat(范围): 做了什么` / `fix(范围): 修了什么`，
    并在 `NEXT-UPDATE.md` 里补一行（本项目要求「做了的活要让人看见」）。

---

## 4. 版本号规则

### 4.1 唯一出口是 `release.ps1`

```powershell
pwsh -File .\release.ps1 -Version 0.86.10 -Notes "这次改了什么"
pwsh -File .\release.ps1 -Version 0.86.10 -Notes "…" -SkipPush    # 只做本地，不推
```

它依次做：

1. 校验版本号格式（`^\d+(\.\d+)+$`，即 `0.81` / `0.8.1` 都行）并与 `AndroidManifest.xml` 比对
2. **`versionCode` 自动 +1**、`versionName` 改成你给的值
3. 跑 `build.ps1` 出包
4. 写 `dist/version.json`（**写完立刻解析回来自检**，坏 JSON 直接中止发版）
5. 把 APK 同步到本机插件目录 `~/.dsh/local-plugins/dsh-mobile-access/app/`（局域网直发用）
6. `git add -A` → `git commit` → `git tag -a v<版本>` → push 分支 + push tag（GitHub）
7. 若配了 `gitee` 远程，一并同步（`HEAD:main` + tag）
8. 刷新 jsDelivr 对 `version.json` / APK 的缓存

### 4.2 硬规则

| 规则 | 说明 |
|---|---|
| **`versionCode` 只增不减** | 它才是 App 判断新旧的依据（`versionName` 只是给人看的） |
| **tag 名 = `v` + `versionName`** | 且**只能由 `release.ps1` 打** |
| **`dist/version.json` 的 `versionCode` 必须与 APK 里的 `versionCode` 一致** | 不一致 = 更新链路必坏 |
| **`-Notes` 里用中文引号「」** | 英文引号会撑破 `version.json`（脚本有 `Assert-Json` 兜底，但别去踩） |
| **攒够改动再发版** | 每次发版都会让所有已装用户收到更新提示 |

### 4.3 `dist/version.json` 字段

| 字段 | 含义 |
|---|---|
| `versionCode` / `versionName` | 版本号（App 比 `versionCode` 决定要不要提示更新） |
| `notes` | 更新说明（App 里展示） |
| `url` | **主线路**：按远程推导，优先 Gitee（国内直连最快） |
| `mirror` | **备用线路**：jsDelivr CDN |
| `sha256` / `size` | App 下载后做完整性校验用 |
| `feedback` / `page` | 手工配置段，发版时**原样保留** |

### 4.4 ⚠️ 当前版本血脉冲突（**必须按此口径处理**）

实测（2026-10-07）：

| 对象 | versionName | versionCode | 说明 |
|---|---|---|---|
| **本仓库 `main`** | `0.86.9` | `26` | 主线，**以此为准** |
| 共创者 fork（`friend/main`） | `0.86.42` | `59` | 对方自己的私有版本线 |

本仓库本地有 57 个 tag，其中 **33 个（`v0.86.10` ~ `v0.86.42`）不属于本仓库历史**，
是对方 fork 的版本线（`git merge-base --is-ancestor` 可验证）。

**定案口径**：

- 对方的 `0.86.10 ~ 0.86.42`（`versionCode` 27~59）是**它 fork 自己的私有版本号**，
  **不并入主线编号**，主线也不回填这些号。
- 主线继续从 `versionCode 26 / 0.86.9` 往上走，下一版由作者跑 `release.ps1` 递增。
- **对方不发版、不打 tag**（这是本文件第 0 节那条铁律的由来）。

**为什么不能让对方直接推**（三条都会真的坏）：

1. **`versionCode` 倒退**：对方是 59，主线是 26 —— 一旦对方的包成了主线，已装对方版本的用户
   永远收不到"更新"（新包的 `versionCode` 比手上小）。
2. **tag 撞车**：两边共用 `v0.86.x` 命名空间，同名 tag 一推就冲突或被拒。
3. **签名不同 → 覆盖安装失败**：不同密钥签的包，老用户装新包一律报「应用未安装」（见第 5 节）。

---

## 5. 签名规则

| 项 | 值 |
|---|---|
| 官方密钥库 | `%USERPROFILE%\.dsh-mobile-keys\dshmobile.jks` |
| alias | `dshmobile` |
| **期望证书 SHA-256 指纹** | `2E518D756794EB72864B5A7C21849EA39FD27754EED0B60B74B2C7E12D8ECE8E` |
| 证书有效期至 | 2056-09-23 |
| 签名档位 | v1 + v2 + v3 全开（`apksigner`） |
| 口令来源 | 环境变量 `DSH_KS_PASS`，或仓库根 `keystore.local.ps1` 里的 `$KsPass` |

### 5.1 硬规则

1. **密钥库和口令永不入库**。`keystore.local.ps1` 已被 `.gitignore` 忽略 —— **不要用 `-f` 硬塞**。
2. **密钥库缺失时，`build.ps1` 拒绝自动重建**（这是故意的）：换新密钥 = 所有已装旧版的用户
   覆盖安装一律报「应用未安装」。请从备份恢复 `.jks`。
3. **指纹校验不许绕过**：`build.ps1` 默认会在 `apksigner verify` 后比对指纹，
   不符直接**终止构建**。只有显式 `-AllowLocalKey` 时才降级为醒目警告。
4. **`-AllowLocalKey` 与发版推送互斥**：`release.ps1` 里 `-AllowLocalKey` **必须**同时带 `-SkipPush`
   （非官方密钥的包推给用户 = 灾难）。

### 5.2 共创者本机构建（推荐做法）

```powershell
# 没有官方密钥时：用本机专用密钥构建（只能自己装，别分发）
pwsh -File .\build.ps1 -AllowLocalKey
```

想**以后继续用这把本机密钥并保持严格校验**，就把构建输出里的实际指纹写进
`keystore.local.ps1`（变量名必须是 `$KsExpectedFp`）：

```powershell
$KsPass = '你的口令'
$KsExpectedFp = '实际构建输出的那串大写无冒号指纹'
```

> ⚠️ 变量名**不能**写成 `$ExpectedFp` —— PowerShell 变量名大小写不敏感，
> 会和 `build.ps1` 内部的 `$expectedFp` 撞成同一个变量，dot-source 后立刻被覆盖。

---

## 6. 上传后必须自证（三步，缺一不可）

```powershell
# ① 线上核验：拉 App 实际读的那条清单 → 主/备两条线路各下一遍 APK → 比 sha256
node tools\verify-live.js

# ② Gitee 侧一致性：main 与全部 tag 是否都到位
pwsh -File .\tools\push-gitee.ps1 -GiteeUser yuan-junqian

# ③ 装机核对（有设备时）
& $adb shell dumpsys package com.dsh.mobile | Select-String versionName=
```

**要点**：

- `verify-live.js` 读的清单地址必须与 App 里的 `MainActivity.UPDATE_MANIFEST_GITEE` **一致**
  （当前 = `https://gitee.com/yuan-junqian/dsh-mobile-android/raw/main/dist/version.json`）。
  两处不一致 = 核验的是另一条线，白验。
- **别用 PowerShell 的 `Invoke-WebRequest` / `curl` 去核验**：在受限环境里会报
  「schannel 没有可用凭证」，看着像线上挂了，其实是环境问题。用 Node 自带的 `https`（脚本已这么做）。
- 本地构建成功 ≠ 用户能更新。链路上还有三处会失败：清单没推到 `main`（App 读的是 `main`，
  不是 tag）/ 清单被写坏 / APK 传坏或传成上一版。`verify-live.js` 就是把这三件事一次验完。

---

## 7. 上传前自检清单（照着勾）

- [ ] `git status --short` **干净**（没有 `.bak`、备份目录、崩溃日志、临时脚本）
- [ ] 没有提交任何密钥 / 口令 / 配对串 / token
- [ ] 没有提交真机截图（`*.png`）或 `evidence/` 内容
- [ ] 没有手工改 `versionCode` / `versionName` / `dist/version.json` / tag
- [ ] 没有新增第三方依赖（`libs/` 里仍然只有 `core-3.5.3.jar`）
- [ ] `pwsh -File .\build.ps1` 全绿，末尾看到「证书 SHA-256 指纹与期望一致」
- [ ] 动了 `src/com/dsh/mobile/net/**` → `harness\run-protocol-v2-test.ps1` 10/10 全绿
- [ ] 网关补丁写成了脚本（幂等 + `-Revert`）并挂进 `install.ps1`
- [ ] 新文档放对了位置（**不是** `docs/` 根目录）
- [ ] 发版后跑了 `node tools\verify-live.js` 并通过

---

## 8. 常见「把结构弄坏」的做法 → 正确做法

| 会弄坏的做法 | 后果 | 正确做法 |
|---|---|---|
| 直接 `git push origin main` | main 被覆盖或被拒，绕过评审 | 开 `feat/*` 分支提 PR |
| 手改 `AndroidManifest.xml` 的版本号 | 版本倒挂，更新检测失效 | 不碰，交给 `release.ps1` |
| 手工 `git tag v0.86.43` | tag 与 `dist/version.json` 不一致 | 不碰 tag，交给 `release.ps1` |
| 用自己构建的 APK 覆盖 `dist/dsh-mobile.apk` | `sha256` 与清单不符 → 用户装不上 | 只交源码，包由作者构建 |
| 用本机密钥的包对外分发 | 老用户覆盖安装失败 | `-AllowLocalKey` 只能配 `-SkipPush`，只自己装 |
| 提交 `keystore.local.ps1` / `*.jks` | 密钥泄露 = 签名权交出去 | 永不入库 |
| 提交真机截图 | 泄露设备名 / 内网地址 / 令牌尾号 | 跑 `tools/make-readme-shots.ps1` 出图 |
| 把规则文档放进 `docs/` 根目录 | 被 `.gitignore` 吃掉，对方看不到 | 放仓库根目录，或 `docs/friend-fork/` |
| 往 `libs/` 加 jar / 引 androidx | 无 Gradle 管线编不过 | 不加依赖，视觉从 `ui/Ui.java` 取值 |
| 直接改网关 `node_modules` 里的文件 | 网关一升级改动就蒸发 | 写成 `pc-plugin/patches/*.ps1` 并在 `install.ps1` 里重放 |
| 发版前工作区有脏文件 | `release.ps1` 用 `git add -A`，一起被提交 | 发版前 `git status` 必须干净 |
| `force push` / rebase 已推的 `main` | 别人的工作被抹掉 | 禁止；要回退就发新提交 |
| 把清单/下载地址指回 `friend` | 用户拿到别人签名的包 | 一律指本仓库（`gitee` / `origin`） |

---

## 9. 命令速查（复制即用）

```powershell
# 0) 看工作区干不干净（发版前必须干净）
git status --short

# 1) 只构建（共创者本机：会生成/使用本机专用密钥）
pwsh -File .\build.ps1 -AllowLocalKey

# 2) 只构建（作者：用官方密钥，含指纹校验）
pwsh -File .\build.ps1

# 3) 协议层回归（动了 src\com\dsh\mobile\net\ 就跑；10 条断言全绿 = exit 0）
pwsh -File .\harness\run-protocol-v2-test.ps1

# 4) 四项功能自验（有模拟器/真机时）
pwsh -File .\tools\verify-4features.ps1

# 5) 发版（作者；自动 +1 versionCode、写清单、打 tag、推送、刷 CDN）
pwsh -File .\release.ps1 -Version 0.86.10 -Notes "这次改了什么"
pwsh -File .\release.ps1 -Version 0.86.10 -Notes "…" -SkipPush     # 只做本地

# 6) 同步 Gitee（main + 全部 tag）+ 一致性校验
pwsh -File .\tools\push-gitee.ps1 -GiteeUser yuan-junqian

# 7) 发版后线上核验（必做）
node tools\verify-live.js

# 8) Gitee 首页被误判违规时先判定（别怀疑 push）
node tools\check-gitee-visibility.mjs

# 9) 改了 .ps1 之后（PS 5.1 读无 BOM 的 UTF-8 脚本会乱码）
pwsh -File .\tools\ensure-ps1-bom.ps1
```

---

## 10. 本项目实测过的环境坑（少走弯路）

| 坑 | 结论 |
|---|---|
| 工作区路径含中文 | `build.ps1` 会先把源码暂存到 `C:\dshstage`（junction 指向 `.buildstage`），因为 aapt2 / d8 是原生工具，打不开非 ASCII 路径 |
| 构建依赖 | 无 Gradle；需要 JDK（`JAVA_HOME` 或 `D:\AndroidStudio\jbr` 等）、SDK `F:\AI\程序开发\android-sdk`、build-tools `36.0.0`、platform `android-36` |
| `release.ps1` 第 5 步要写工作区**外**的目录 | 权限不够会被拦，**脚本在这一步中止 → 后面的 commit / tag / push 都没跑**。以更宽权限重跑会**再 bump 一次 `versionCode`** → 想保持版本号干净，先把 `AndroidManifest.xml` 的版本号改回去再重跑 |
| `git push` 报连不上 GitHub | 多半是配置里留着已失效的代理；`release.ps1` 会探测并**本次绕过**它。手动推时可临时 `git -c http.proxy= push` |
| 没配提交身份 | `git commit` 会以「Author identity unknown」失败。先跑一次 `git config user.name` / `git config user.email` |
| 更新说明混进英文引号 | 撑破 `version.json` → `url` 字段被挤占 → App 更新必失败。一律用「」 |
| `adb install` 卡住不返回 | 多半是手机厂商的二次确认框，不是 bug；配合 `adb shell input tap` 点掉 |
| 动画/抖动/"没效果"类问题 | **不要猜** —— 连拍截图 + 逐像素对比定位 |

---

## 11. 出问题怎么办

| 情况 | 处理 |
|---|---|
| 上传后发现结构被弄坏 | 不要 force push；发一个修复提交，并在 PR 里写清楚 |
| 发版后用户更新不了 | 跑 `node tools\verify-live.js` 定位是哪一环（清单 / 线路 / `sha256`） |
| 误提交了密钥 | **立刻**在 Gitee / GitHub 撤销该提交并把密钥作废重建（历史里的密钥视为已泄露） |
| 版本号已经乱了 | 以 `AndroidManifest.xml` 里**当前实际值**为准，只增不减，继续往上走；不要试图"改小对齐" |
| 不知道该问谁 | 在项目群问，或开 Issue：<https://github.com/James-Xue6/dsh-mobile-android/issues> |

---

*最后更新：2026-10-07 · 本文件描述的规则与实际脚本（`build.ps1` / `release.ps1` / `tools/push-gitee.ps1` / `tools/verify-live.js` / `.gitignore`）一致；脚本改了请同步改这里。*
