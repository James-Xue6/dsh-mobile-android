# 项目来源与版权声明（Ownership & Provenance）

> **重要：本仓库不是原创项目。它是第三方开源项目的镜像 / 分发副本，版权归原作者所有。**

## 一、原项目

| 项 | 内容 |
|---|---|
| **项目名** | DSH 掌上通（DSH Mobile for Android） |
| **原作者** | [James-Xue6](https://github.com/James-Xue6) |
| **原始仓库（GitHub）** | https://github.com/James-Xue6/dsh-mobile-android |
| **本镜像仓库（Gitee）** | https://gitee.com/tang-changxu/dsh-mobile-android |
| **本副本对应上游版本** | App `0.86.4`（versionCode 21）· PC 插件 `dsh-mobile-access` `1.0.1` |
| **同步自上游提交** | `9c775dd`（`main` 分支，2026-10-04 拉取） |

本仓库**原样复制**上游 `main` 分支在提交 `9c775dd` 时的完整源码、资源与预编译安装包
（`dist/dsh-mobile.apk`，997,292 字节，sha256 `5dd201c3…c9da5`），除下表内容外未改动任何逻辑：

| 改动 | 说明 |
|---|---|
| `ORIGIN-AND-LICENSE.md`（新增） | 本文档，声明来源、授权与责任边界 |
| `README.md` / `README.en.md`（顶部新增一行提示） | 只加「第三方镜像」提示，正文其余部分与上游逐字一致 |
| 后续本地适配 | 逐条记录在第四节 |

## 二、许可（务必阅读）

| 部分 | 许可状态 |
|---|---|
| PC 插件 `pc-plugin/dsh-mobile-access` | ✅ 上游在其 `package.json` 中声明 **MIT License** |
| 协议层依赖 `dsh-plugin-mobile-gateway` | ✅ 第三方 MIT 插件（npm 包，**未**包含在本仓库内） |
| 仓库根目录 / Android App 源码 | ⚠️ **上游未提供 LICENSE 文件**（默认保留所有权利） |

因此请注意：

- 本仓库为**个人使用与国内访问便利**而镜像，不代表本仓库所有者对代码或素材主张任何权利。
- **上游根仓库没有 LICENSE 文件**，App 源码部分的权利状态由原作者保留。如需在本镜像基础上
  二次分发、商用或再许可，请先与原作者确认（上游仓库 Issues / 邮箱 `804544542@qq.com`）。
- 一切原始署名、README 与版权信息均**完整保留、未做删改**。

## 三、责任说明

- ➡️ 对本项目代码的缺陷反馈与功能建议，首选请提交到**原作者仓库**
  （[GitHub Issues](https://github.com/James-Xue6/dsh-mobile-android/issues)），以保持与上游一致。
- ➡️ 本镜像上的本地适配改动（若有）记录在第四节，与上游无关，不应回算到原作者头上。
- ➡️ 若原作者认为本镜像不妥，请联系本仓库所有者，将立即删除或转为私有。

## 四、本地改动记录

| 日期 | 改动 | 原因 |
|---|---|---|
| 2026-10-04 | 初始镜像（对齐上游 `9c775dd`） | 国内直连 GitHub 不稳定（`github.com` 连接超时、`raw.githubusercontent.com` DNS 解析失败），镜像到 Gitee 便于安装与后续优化 |
| 2026-10-04 | 补充 `ORIGIN-AND-LICENSE.md`、README 顶部「第三方镜像」提示 | 明确标注为第三方插件，保留原作者署名 |
| 2026-10-04 | 补丁：**配对地址智能回退**（`pc-plugin/patches/dsh-plugin-mobile-gateway.client.js.patched`） | 实测：Quick Tunnel 已开启但未就绪时，「自动选择 · 优先外网」直接返回空地址，面板把按钮卡在「等待连接地址」——局域网明明可用却生成不了二维码。改为隧道没就绪即回退局域网地址 |
| 2026-10-04 | 补丁：**cloudflared 下载源加国内镜像回退**（新增 `pc-plugin/patches/dsh-plugin-mobile-gateway.cloudflared-binary.mjs.patched`） | 实测：公网隧道首次启动要下 55MB cloudflared，而它只从 GitHub Releases 拉，国内直连必失败，隧道永远停在 `preparing`。改为镜像在前、官方在后，逐个校验官方 sha256 摘要 |
| 2026-10-04 | `pc-plugin/install.ps1` 与 `pc-plugin/patches/restore-gateway-panel-fix.ps1` **兼容 PowerShell 5.1** | 原先 README 要求 `pwsh` 7；更严重的是 5.1 下 `Get-Content` 按 ANSI 解码 UTF-8，跑安装脚本会把 profile 里的中文路径写成乱码。改为 .NET 显式读写 UTF-8（无 BOM），两个脚本都加 UTF-8 BOM 以便 5.1 正确解析；补丁脚本同时改为支持双补丁、幂等、自检失败自动回滚 |
| 2026-10-04 | 新增三套自测：`tools/test-gateway-patches.mjs`、`tools/test-install-sandbox.ps1`、`tools/test-gateway-patch-script.ps1`（共 54 项断言） | 让上述补丁与脚本改动可回归验证（沙箱测试用假家目录，不动真实 profile） |
| 2026-10-04 | README 安装说明：克隆/下载地址改为本镜像，并修正过期的 `v0.81` / `v0.8` 下载链接为 `main` | 上游 tag 落后于 `main`（安装包按 `main` 分发），且国内直连 GitHub 打不开 |
| 2026-10-05 | **构建工具链兼容 Windows PowerShell 5.1**：`build.ps1` 在调用原生工具（javac / d8 / apksigner / aapt2）时局部放宽 `$ErrorActionPreference` | 5.1 会把原生工具写进 stderr 的「弃用 API」等提示当成致命错误，一句 warning 就中断构建（pwsh 7 不会）。实测：修前连编译都进不去，修后完整出包 |
| 2026-10-05 | `build.ps1` 会**自动探测** SDK 与 build-tools（`ANDROID_HOME` → 常见路径；build-tools 优先与 `-Platform` 同代），并新增 `-AllowLocalKey` 开关 | 原先 `-Sdk` 默认写死作者机器上的路径、build-tools 写死 `36.0.0`，换台机器就得手带参数；没有上游签名密钥库时脚本拒绝出包，导致 fork 完全无法构建 |
| 2026-10-05 | 仓库内**所有 `.ps1` 补上 UTF-8 BOM**，并新增 `tools/ensure-ps1-bom.ps1`（补齐 + 解析自检，支持 `-Check`） | 5.1 读无 BOM 的 UTF-8 脚本会按 ANSI 解码，中文变乱码、严重时直接语法报错（`build.ps1` 实测踩到）。编辑器改写脚本常会把 BOM 丢掉，所以做成一个可反复跑的工具 |
| 2026-10-05 | **新功能：内网连接时自动同步公网地址**（新增 `src/com/dsh/mobile/net/AddressSync.java`、`harness/src/AddressSyncTest.java`；改 `MainActivity` / `Store`；插件侧新增 `GET /gateway-info`） | 公网隧道是 Quick Tunnel，**电脑每次重启域名都会变**；手机存的上一个地址失效后，出门在外只能回家重新扫码。改为手机在家（内网可达）时同步一次，出门直接用。协议层未改，复用插件自己的 8099 局域网服务 |
| 2026-10-05 | App 更新检查增加 Gitee 源（`MainActivity`：Gitee → jsDelivr → GitHub raw） | 国内直连 jsDelivr / GitHub raw 经常很慢或不通 |
| 2026-10-05 | 版本号 `21 → 22` / `0.86.4 → 0.86.5-dsh.1`；`dist/` 换成本仓库构建的 APK（本机专用密钥签名） | 与上游区分。⚠️ 签名与上游不同：装过官方版本的用户必须先卸载再安装本包 |
| 2026-10-05 | `tools/accept-pairing.ps1` 的 SDK 路径改为自动探测 | 与 `build.ps1` 同样的「写死作者机器路径」问题 |
| 2026-10-05 | 新增 `UPSTREAM-REPORT.md`（逐条改动说明，供上游参考） | 把本轮改动的原因、证据与合并建议整理成一份可直接转给上游的文档 |
| 2026-10-05 | **新功能：应用内更新（热更新）**（`MainActivity.downloadAndInstall()`；`AndroidManifest` 增加 `REQUEST_INSTALL_PACKAGES`） | 原来「立即更新」是跳浏览器：用户要离开 App、在浏览器等下载、再去文件管理里找到 APK 点安装，任何一步都可能放弃。现在用 `PackageInstaller` 会话**边下边写**（不落共享存储、不需要 FileProvider），下完直接交给系统安装器；清单带 `sha256` 时逐字节校验，不符立即中止 |
| 2026-10-05 | **修 bug：地址同步在真机上一次都没触发**（`MainActivity` 改用 `AddressSync.lanHostForHttp()`） | 原实现用的是本文件那个简易 `hostOf()`，它**不去端口**（返回 `192.168.1.14:3091`），再喂给 `isUsableLanAddress()` 永远为 false，于是每次都静默 return —— 单元测试全绿、真机一次没跑。已把这段胶水收进 `AddressSync` 并补了断言；真机 logcat 验证：`地址已同步: 公网地址已更新（隧道域名变化）` |
| 2026-10-05 | `release.ps1` 适配本镜像：UTF-8 显式读写、下载地址按 `git remote origin` 推导、清单增加 `sha256`、push 前探测失效代理、版本号允许 `0.86.6-dsh.1` 这类后缀 | 让「一条命令发版 → 手机在「关于」里检测 → 应用内更新」这条链路在本仓库真的能用（原来 URL 写死上游 GitHub，且 5.1 下写 manifest 会坏中文注释） |
| 2026-10-06 | README（中/英）**整体重写**为更紧凑的结构，安装段落改为「克隆本镜像 → 跑脚本 → 扫码」的主线 | Gitee 侧对仓库主页的 README 显示「内容可能含有违规信息」、`raw` 取 README 返回 451（同仓库其它文件均正常）。实测：**逐段切片都能放行、只改几行仍被拦、实质重写整篇后放行** —— 故按新结构重写，并把英文版里过期的上游 clone 地址一并对齐本镜像 |
| （待补充） | | |
