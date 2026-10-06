# 本仓库相对上游的改动说明（可直接转给上游作者）

> 基准：上游 [`James-Xue6/dsh-mobile-android`](https://github.com/James-Xue6/dsh-mobile-android) 的 `main` 分支，提交 `9c775dd`（App `0.86.4` / versionCode 21）。
> 本仓库是第三方 Gitee 镜像（见 [ORIGIN-AND-LICENSE.md](ORIGIN-AND-LICENSE.md)），下列改动**均为在本地实测中发现问题后所做**，逐条附了复现条件与验证方式，供上游参考取舍。

---

## 一、纯缺陷修复（建议可直接合）

### 1. 配对地址「自动选择」在隧道未就绪时不出码

**文件**：`pc-plugin/patches/dsh-plugin-mobile-gateway.client.js.patched`（网关面板的补丁基线）

**现象**：面板配对方式为「自动选择 · 优先外网」且 Quick Tunnel 已开启时，若隧道尚未拿到地址（首次启动要下载 cloudflared，国内常失败），地址框一直空着、按钮永远停在**「等待连接地址」**且不可点 —— 局域网入口明明已经监听可用，却一张二维码都生成不出来。

**根因**：`pairingUrlFor()` 里

```js
if (status.cloudflare?.enabled && status.cloudflare.mode === 'quick') return tunnelUrl   // tunnelUrl 可能是空串
return tunnelUrl || status.publicUrl || lanUrl || ...                                    // 于是永远走不到这里
```

**改法**：加一个「地址确实拿到了」的判据，隧道没就绪就继续往下走回退局域网：

```js
if (status.cloudflare?.enabled && status.cloudflare.mode === 'quick' && tunnelUrl) return tunnelUrl
```

**验证**：`node tools/test-gateway-patches.mjs`（18 项断言，覆盖「隧道未就绪 → 回退局域网」「隧道在线 → 优先公网」「跳过 169.254.x」等组合）

### 2. cloudflared 下载源加国内镜像回退

**文件**：`pc-plugin/patches/dsh-plugin-mobile-gateway.cloudflared-binary.mjs.patched`

**现象**：公网隧道首次启动需要下载 55 MB 的 `cloudflared`，而它只从 `github.com/.../releases/download/...` 拉取。国内直连该域名普遍超时（实测 `github.com:443` 直接连不上），隧道**永远停在 `preparing`**，公网二维码也就永远出不来。

**改法**：候选源列表「镜像在前、官方在后」，逐个尝试；**每个候选的响应体仍按官方 sha256 摘要校验**，校验不过自动换下一个（安全性不打折，只是把「下不动」变成「下得动」）：

```js
const MIRROR_PREFIXES = Object.freeze(['https://gh-proxy.com/', 'https://ghproxy.net/'])
const candidates = [...MIRROR_PREFIXES.map((p) => p + releaseUrl), releaseUrl]
```

**验证**：同上测试文件（覆盖「候选顺序」「坏字节被拒且逐个试完」「摘要不符的缓存文件不被信任」「真实字节只试第一个候选」等）

### 3. Windows PowerShell 5.1 兼容（脚本会写坏 profile 的中文路径）

**文件**：`pc-plugin/install.ps1`、`pc-plugin/patches/restore-gateway-panel-fix.ps1`

**现象**：README 原先要求 PowerShell 7；在 Windows 自带的 PowerShell 5.1 下，`Get-Content -Raw` 默认按 ANSI 代码页解码，会把 profile 的 `package.json` / `cordis.patch.yml` 里的中文（例如 `link:L:/DSH自制插件/...`）读成乱码，再写回去就**把用户的中文路径写坏了**；`Set-Content -Encoding utf8` 还会写出 BOM。

**改法**：统一改用 .NET 显式读写 UTF-8（无 BOM）；脚本自身加 UTF-8 BOM（5.1 靠 BOM 才能正确解析含中文的 .ps1）；补丁脚本同时升级为「双补丁 + 幂等 + 自检失败自动回滚」。

**验证**：`tools/test-install-sandbox.ps1`（23 项断言，用**假家目录**跑完整安装流程，专门断言「中文路径未被写坏」「JSON/YAML 无 BOM 且合法」）、`tools/test-gateway-patch-script.ps1`（13 项，含幂等与版本守卫）

---

## 二、开发体验（Windows 用户不必再装 pwsh 7）

### 4. `build.ps1` 能在 PowerShell 5.1 下完整出包

**问题 A（阻断性）**：`Invoke-Tool` 用 `$out = & $exe @argList 2>&1` 调用 native 工具，而脚本头部是 `$ErrorActionPreference = 'Stop'`。PowerShell 5.1 会把 native 工具写到 stderr 的内容当成致命错误 —— javac 的一句「Note: Some input files use or override a deprecated API.」就足以让整个构建中断（pwsh 7 不会）。
**改法**：调用期间局部放宽 EAP，只依据退出码判断成败。

**问题 B（可移植性）**：`-Sdk` 默认写死作者机器路径、`-BuildTools` 写死 `36.0.0`。换台机器就得手带参数。
**改法**：SDK 自动探测（`ANDROID_HOME` / `ANDROID_SDK_ROOT` → `%LOCALAPPDATA%\Android\Sdk`），build-tools 自动挑「与 `-Platform` 同代」的最高版本。

**问题 C**：没有上游签名密钥库时脚本直接拒绝出包（设计上是对的：换密钥会让老用户覆盖安装失败），但 fork / 二次开发就完全无法构建。
**改法**：新增 `-AllowLocalKey` 开关，显式传入时生成本机专用密钥并在签名校验处降级为醒目警告（默认行为不变，仍然拒绝自动重签）。

**验证**：本机 Windows PowerShell 5.1 + JDK 25 + build-tools 36.1.0 + platform android-36 完整出包成功（0.86.4，973.9 KB）。

### 5. 仓库内所有 `.ps1` 补 UTF-8 BOM

**原因**：5.1 读「无 BOM 的 UTF-8」脚本按 ANSI 解码 → 中文乱码 → 严重时直接语法报错（`build.ps1` 实测：`throw '没有找到任何 Java 源文件'` 乱码后连引号都配不上）。编辑器改写脚本常把 BOM 丢掉，属于「改完不跑就复发」的坑。
**改法**：新增 `tools/ensure-ps1-bom.ps1`（补齐 BOM + 全量解析自检，带 `-Check` 可当提交前门禁）。

---

## 三、新功能：内网连接时自动同步公网地址（免重扫）

**要解决的问题**：公网隧道是 Cloudflare **Quick Tunnel，电脑每次重启域名都会变**。手机里存着上一个公网地址，出门时内网连不上、切到公网又是死地址 —— 而那时人往往已经不在电脑边上了。现有行为是弹一句「公网地址可能已失效，请重新生成二维码扫一次」。

**做法（协议层一行未改）**：

| 侧 | 文件 | 内容 |
|---|---|---|
| PC | `pc-plugin/dsh-mobile-access/index.js` | 在插件自带的 **8099 局域网服务**上新增 `GET /gateway-info`：内部以 loopback 转调网关 `/mgw/status`，返回 `{ version, gatewayId, publicUrl, endpoints, wsPath }`。**只有地址与网关 id，不含任何凭证**；配对/鉴权仍由网关照旧把关 |
| App | `src/com/dsh/mobile/net/AddressSync.java`（新增） | 纯逻辑：给定「网关报的地址」与「当前存的地址」，算出该不该更新。零 Android 依赖、零 I/O，可在 JVM 穷举断言 |
| App | `src/com/dsh/mobile/MainActivity.java` | `onHello` 后，若当前连的确实是内网地址，后台 GET 一次 `/gateway-info`（60 秒节流、失败静默），按 `AddressSync` 的结论落盘 |
| App | `src/com/dsh/mobile/Store.java` | 新增 `updateActiveAddresses()`：写进当前设备并保持旧字段镜像一致 |

**为什么不用网关协议**：`PROTOCOL.md` 明确 `endpoints` 只在配对载荷与 `GET /mgw/status` 返回（后者限 loopback），`hello` / `paired` 不带；与其改第三方协议帧，不如复用本插件自己已经在用的 8099 局域网服务（装 App 的二维码走的就是它）。

**取舍规则（宁可少更新，也不打扰用户）**：

- 公网槽位：**只在「空着」或「存的是 `*.trycloudflare.com` 临时域名」时更新**；用户自己填的固定地址（Tailscale 的 `ws://100.x`、自建 `wss://`、企业内网）一律不动
- 内网槽位：只装 `LanAddress` 判据认定「手机真连得上」的地址（虚拟网卡 / APIPA / CGNAT / 回环一律排除）
- 网关身份：两侧 gatewayId 都非空且不一致时，一个字段都不改
- 网关给的 `publicUrl` 若其实是本机地址（`ws://127.0.0.1:…`，对手机无用），改用 `endpoints` 里的真实地址

**验证**：`harness/src/AddressSyncTest.java` —— 39 项断言，覆盖「隧道域名变化需更新」「自定义地址绝不被覆盖」「Tailscale 地址不被顶掉」「虚拟网卡不被当内网」「gatewayId 不一致则不动」「空输入不崩」等。

**运行方式**（与 `RoutePolicyTest` 一致，纯 JVM、不依赖真机）：

```powershell
javac -encoding UTF-8 -d "$env:TEMP\addrsync-test" `
      src\com\dsh\mobile\net\AddressSync.java src\com\dsh\mobile\net\LanAddress.java `
      harness\src\AddressSyncTest.java
java -Dstdout.encoding=UTF-8 -cp "$env:TEMP\addrsync-test" AddressSyncTest
```

### 6. 更新检查增加 Gitee 源

**文件**：`src/com/dsh/mobile/MainActivity.java`
**改动**：`UPDATE_MANIFEST_GITEE`（本镜像 raw）排在最前，其后 jsDelivr，最后 GitHub raw —— 国内直连 jsDelivr / GitHub raw 经常很慢或不通。

### 7. 应用内更新（热更新）：把「立即更新」从跳浏览器改成应用内下载并安装

**文件**：`src/com/dsh/mobile/MainActivity.java`、`AndroidManifest.xml`、`release.ps1`

**原行为**：发现新版本后点「立即更新」= `ACTION_VIEW` 跳浏览器 → 用户在浏览器里等下载 → 再去「文件管理」找到 APK → 手动点安装。中间任何一步都可能放弃（而「检查更新」入口本身在 设置 → 关于 里已经有了）。

**改法**：用 `PackageInstaller` 会话**边下边写**：

- 不需要存储权限、不需要 FileProvider（本工程无 AndroidX，另做 Provider 反而更重）
- 下载完成 `session.commit()`，由系统弹安装确认（收到 `STATUS_PENDING_USER_ACTION` 时由 App 把系统给的确认界面拉起来）
- Android 8+ 的「安装未知应用」授权：`canRequestPackageInstalls()` 为 false 时引导去系统设置（只引导一次，之后不再打扰）
- 清单新增 `sha256` 字段：边下边算 SHA-256，不符立即中止并 `abandonSession()` —— 下载源可能是第三方镜像（Gitee / jsDelivr），这一步能挡住「下到半截」或「被替换」的包
- 下载失败时给「用浏览器下载」兜底按钮，不让用户卡死

**manifest 需要**：`android.permission.REQUEST_INSTALL_PACKAGES`（非运行时权限，但属「特殊权限」，用户需在系统设置里放行一次）。

**配套**：`release.ps1` 更新为「改版本号 → 构建 → 刷新 `dist/version.json`（含 url/mirror/sha256）→ 同步进本机插件目录（局域网直发）→ 提交打 tag → 推送」，一条命令完成发版。

### 8. 真机发现的一个坑（值得上游知道）

地址同步第一版在真机上**一次都没触发**，而单元测试全绿：问题出在「取地址」的胶水层 —— `MainActivity` 里那个简易 `hostOf()` **不去端口**，对 `ws://192.168.1.14:3091/ws/mobile` 返回 `192.168.1.14:3091`，再喂给 `LanAddress.isUsableLanAddress()` 永远为 false。
修法是把这层胶水也收进纯逻辑类（`AddressSync.lanHostForHttp`）并补上断言。**教训**：纯逻辑测到了 `decide()`，但调用点的地址解析不在测试范围内 —— 这类「接线错误」只有真机跑一遍才暴露。

---

## 四、需要上游注意的两点

1. **本仓库 `dist/` 里的 APK 是本地构建**（`versionCode 22` / `versionName 0.86.5-dsh.1`），用**本机专用密钥**签名 —— 与上游签名不同，装过官方版本的用户**必须先卸载**才能安装本包。上游若合并代码，请用自己的密钥重新构建后再发版。
2. 第 1、4、5 条与上游 `0.9.0` 网关基线绑定（补丁脚本自带版本校验，版本不符会拒绝执行而不是降级）。网关升级时，`pc-plugin/patches/*.patched` 需要按新版本基线更新。

---

## 五、本地自测一览（都不联网，纯本地断言）

| 命令 | 断言数 | 覆盖 |
|---|---|---|
| `node tools/test-gateway-patches.mjs` | 18 | 配对地址回退 + cloudflared 镜像与摘要校验 |
| `powershell -File tools/test-install-sandbox.ps1` | 23 | 安装脚本沙箱（中文路径不被写坏、无 BOM、登记正确） |
| `powershell -File tools/test-gateway-patch-script.ps1` | 13 | 补丁脚本沙箱（中文标记自检、幂等、版本守卫、自检失败回滚） |
| `javac … AddressSyncTest.java && java AddressSyncTest` | 39 | 公网地址同步取舍 |
| **合计** | **93** | |
