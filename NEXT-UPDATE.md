# 下一步计划（NEXT-UPDATE）

> 状态标记：`[x]` 已完成并验证 / `[~]` 部分完成 / `[ ]` 待办。
> 最近更新：2026-10-05 晚（四项优化 + 通用文件附件已实现，并**在 Android 模拟器上端到端实测通过**）。

## 一、已完成（保留记录，避免重复讨论）

- [x] **跨会话/后台通知** —— 正解在**服务端**：网关插件新增 `hasAnyInteractionClient()`，放宽提问/审批两道 waterfall 门；
  并让被 `filterSessionId` 过滤的连接照样收到（带 `global:true`，字段最小化）。**已发版 v0.86.4，用户实测通过。**
  - 作废的三条客户端 hack（勿重提）：第二条 WS 连接（握手永远超时）/ `unsubscribe` 窗口（网关不推新交互）/ 周期强制重连（会毁输入与回答）。
- [x] **前台服务不再断线自杀** —— `KeepAliveService.sync()` 去掉 `Notifier.isConnected()` 判据。
- [x] **无界面时帧不再被静默丢弃** —— `GatewayClient.bgInteractionHook`（进程级通知钩子）。
- [x] **通知渠道** —— `dsh_pending_v2`(HIGH) + 独立 `dsh_service`；通知失败可诊断（`lastPostError`/`lastPostAt`）；
  设置页三行自查 + **渠道级深链**。
- [x] **通知 id 槽位** 200 → 4000；`approvalKey/questionKey` 收成唯一实现。
- [x] **卡片灰框** —— `Ui.CardBg` 自绘渐变在长卡片上整片压暗约 26% → 提问/审批卡改用框架 `Ui.cardGrad()`（像素证据）。
- [x] **卡片底部按钮可达** —— 按钮移出滚动区；**卡片内部滚动位置**跨重绑保留（`ChatItem.cardScrollY`）。
- [x] **提问卡内输入框草稿丢失** —— TextWatcher 边打边写回 `it.typed`。
- [x] **点输入框后卡片乱滚** —— 内容区高度改固定值 + 键盘弹起时**只最小幅度滚外层列表**、不碰卡片内部滚动。
- [x] **独立输入页方案** —— 曾实现，按用户要求**已撤回**（勿重提）。
- [x] **交付纪律** —— 未验证透不发版；先贴 APK 给用户验，通过后再 `release.ps1`。
- [x] **版本命名倒挂** —— v0.86.4 = versionCode 21，已按"只增不减"继续；后续一律走 `release.ps1`。

## 二、待办（按优先级）

### P0 · 网关插件补丁的工程化（**2026-10-05 晚：主体已做完，只剩启动自检**）
- [x] **4 个补丁脚本现在都由 `install.ps1` 统一重放**（此前只跑面板补丁，其余是手工打的）：
  1. **`patch-gateway-crosssession.ps1`（2026-10-05 新增）** —— 跨会话提醒：放宽两道 waterfall 门 +
     跨会话下发带 `global: true`。**此前仓库里根本没有这个脚本**，改动只活在线上；
     回退用**反向替换**（不靠备份还原，否则会把后来那几个补丁一起抹掉）。
     已沙箱验证：`node --check` 通过 / 幂等 / 3 处 `hasAnyInteractionClient` / 旧门 0 残留 / `-Revert` 逐字节还原。
  2. `patch-gateway-hello-publicurl.ps1` —— hello 帧带上电脑当前公网(隧道)地址；
  3. `patch-gateway-route-broadcast.ps1` —— 地址变化 / 启动就绪后主动广播；
  4. **`patch-gateway-file-upload.ps1`** —— `message.files[]` + `hello.capabilities: file-uploads`。
  - `pc-plugin/dsh-mobile-access/package.json` 版本 `1.0.2` → `1.0.3`。
  - ⚠️ `patch-gateway-subagent-address.ps1` **已回滚**（线上无 `parentAddress` 标记），**不要**接进 install。
- [ ] **还剩：启动自检**。现在靠"记得重跑 install.ps1"；应加一处启动时核对（每个补丁的功能标记在不在），
  缺了就打印明确指引（"跑 pwsh -File pc-plugin\install.ps1"）。
  - 手动复核判据：`lib/index.mjs` 里应有 `hasAnyInteractionClient` / `'file-uploads'` / `dsh-mobile:hello-publicurl`
    / `dsh-mobile:route-broadcast`；`lib/dsh-host-adapter.mjs` 里应有 `uploadFile:`。
  - 补日志便于定位：`interaction replay` 打印 `session=`；门未通过时打印 `skipped (no mobile client)`。

### P1 · 通知在荣耀/华为上的顽固点
- [~] **渠道降级**：App 申请的 `IMPORTANCE_HIGH` 被 ROM 降为 DEFAULT（`mOriginalImp=4` → `mImportance=3`，`mUserLockedFields=0`）。
  - 已做：换新渠道 id + 设置页读出实况 + 渠道级深链；
  - 待做：自查行给出**可执行步骤**（"点这里 → 找到「需要处理」→ 打开横幅与响铃"）；
  - 待做：降低对系统横幅的依赖 —— App 内常驻待处理栏/角标（已有 `pendingBanner` / `pendingJumpTarget` 可复用）。
  - 结论：**换台荣耀/华为会重演**，只能引导 + 兜底，不能靠代码强行提权。
- [ ] **不建议 `setFullScreenIntent`**：Android 14+ 默认只授通话/闹钟类；且只在锁屏/息屏全屏，覆盖不了"正在用手机但没看该会话"的主场景。

### P1 · 仍缺真机验证的两项（需能造出"进程活着但无界面"）
- [ ] V2：界面被系统回收后，M1 钩子路径是否仍弹通知。本机 `always_finish_activities` **不销毁 Activity**（实测）。
  - 可选：临时调试包提供"主动 finish()"入口（**发布包必须保持不可达**），或找 ROM 上只杀 Activity 不杀进程的办法。
- [ ] V3 手机侧：断网/重连后 pending 提醒是否重新出现（**网关侧已验证**会补发：`interaction replay … questions=1`）。

### P2 · 产品功能（用户已点名）
- [x] **底部「生成物」按钮**：列本次会话产出文件 + 直接打开/下载。**2026-10-05 实现并模拟器实测通过**
  （chip 行「生成物」→ `file-list` 下钻 + 空态 + 下载进度原地更新 + 下载后 `ACTION_VIEW`）。
  - **真网关实测**：`file-list` 回帧 `entries[].path` 是**相对 cwd** 的路径（`path:"."` 为根），
    直接拿去开下载、**不要再 relativize**。
- [x] **加号 → 文件 / 相册 / 拍照，附件先暂存、与文字或语音一起发** —— 见「四」表 ②。
- [x] **通用文件附件（PDF / Office / 压缩包）** —— 见「四」表 ⑤；网关补丁 `patch-gateway-file-upload.ps1`。
- [x] **消息文本「点一次不能立即选范围」** —— 见「四」表 ③。
- [x] **chip 行「权限」（对齐 PC 端 permission presets）** —— 见「四」表 ④。
- [ ] 助手消息里的 **markdown 表格**未渲染（当前按纯文本显示）。
- [ ] 大文件（>24MB）走 `uploadStream` 分块上传（**未实现**；当前单帧 base64 上限单文件 24MB / 合计 48MB，**未压测**）。

## 四、2026-10-05 四项优化 + 通用文件附件（本轮 · **已全部实测通过**）

> 契约：`docs/CONTRACT-20261005.md`；协议核实：`docs/PROTO-FINDINGS.md`；逐条交接单：`docs/任务明细-20261005.md`。
> **状态口径**：✅ = 已实现 + 构建通过 + **在 Android 模拟器上端到端实测通过**。

| # | 需求 | 状态 | 落点 |
|---|---|---|---|
| ① | 生成物窗口（列本会话产出文件 + 下载） | ✅ | `MainActivity.onArtifacts/onFileList/showArtifactsPanel/renderArtifacts` |
| ② | 加号 →「文件 / 相册 / 拍照」三选项，附件**先暂存**、与文字/语音一起发 | ✅ | `ui/AttachSheet.java`（已接线）、`MainActivity.onAttachPick/stageAttachment/sendStagedAttachments` |
| ③ | 消息文本「点一次不能立即选范围」不流畅 | ✅ | `ui/ChatAdapter.makeSelectable/selectWholeText`、`ConversationView.commitData` 选字让路 |
| ④ | chip 行新增「权限」（对齐 PC 端 permission presets） | ✅ | `ConversationView` 权限 chip、`MainActivity.onPickPermission/onPermissionOptions/onPermission` |
| ⑤ | **通用文件附件（PDF / Office / 压缩包）** | ✅ | `net/GatewayClient.sendMessageWithAttachments` + 网关补丁 `patch-gateway-file-upload.ps1` |

### 怎么复现验收（设备一到位，两条命令）
```
pwsh -File tools\verify-4features.ps1        # 四项：权限 / 加号 / 生成物 / 消息文本
pwsh -File harness\run-protocol-v2-test.ps1  # 协议层回归（10/10）
```
证据：`evidence/ui-4features-manual/`（22 个文件）、`evidence/t9-file-upload/`（39 个文件）。

### 集成期 + 设备验收期抓到的真 bug（共 7 个，全部已修）
| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | 编译不过 | `TextView` **没有** `setSelection(int,int)` | 改 `android.text.Selection.setSelection(Spannable,…)` |
| 2 | 权限 chip / 面板显示错的当前值 | 当前值在 **`sessionPermissions.currentValue`**，原实现漏了 | 兜底键表把 `currentValue` 排第一 |
| 3 | `AttachSheet` 交付后无人调用 | wire 先写完时该类还没落盘 | 按契约接线 |
| 4 | chip 文案 `权限 · 权限 完全访问` | `MainActivity` 与 `ConversationView` 都拼了前缀 | 只在 `ConversationView` 拼一次 |
| 5 | 单击只出高亮、不弹复制工具条 | `performLongClick()` 对 selectable TextView 不启动 ActionMode | 改**合成一次长按手势**（DOWN→900ms→UP） |
| 6 | **每一条消息都被网关拒** | `clientTimeZone` 非 IANA 名（`GMT` / `GMT+08:00`）→ 网关整条拒绝；App 已清空输入框 = **静默失败** | 新增 `safeClientTimeZone()`，不合法就**不带该字段**；4 个发送路径统一 |
| 7 | 发送被拒后附件被清掉 | "发完就清空"但成功与否是异步的 | 留一份 `lastSentAttachments`，`onProtocolError(message)` 里回填 |

### 本轮**实测**到的事实（直连真网关 + 模拟器，非推断）
- `permission-options`（带 sessionId）回帧：`options:[{value,name}]`、`defaultOptions`、`defaultPreset`，
  **当前值在 `sessionPermissions.currentValue`**；实测 `currentValue=danger-full-access` 而
  `defaultPreset=workspace-write` ⇒ 只读 `defaultPreset` 会**显示错误**的当前权限（已修）。
- `file-list` 回帧 `entries[].path` 是**相对 cwd** 的路径（`path:"."` 为根）⇒ 直接拿去开下载，**不要再 relativize**。
- 三条 preset 取值实测为 `read-only` / `workspace-write` / `danger-full-access`。
- **通用文件通道已打通**（端到端两次）：客户端 `message.files[]` → 网关调宿主 `fileUploads/upload`
  （**纯字符串 sessionId 当 agentId 可解析** —— 这是补丁原先唯一没验证的一环）→ 会话历史里出现
  `{"type":"file","attachment":{"attachmentId":"sha256:…","name":…,"bytes":…}}`。
- `hello.capabilities` 现在含 **`file-uploads`**（网关补丁宣告）；客户端按它决定"发 / 拦"，**不盲发**。

### ⑤ 的实现要点（避免以后重踩）
- 网关 `message` 帧**原生只接受图片**（png/jpeg/webp/gif）；通用文件要靠补丁新增的 `files[]`：
  每项 `{data:"<标准Base64无前缀>", name:"a.pdf"}`。
- 客户端上限：单文件 24MB / 带文件时合计 48MB / 纯图片仍 9MB（**未压测**）；再大要走 `uploadStream` 分块（未实现）。
- 未打补丁的网关会**静默忽略** `files[]`（只读 images/text）⇒ 所以客户端必须先查 `file-uploads` 能力，
  没有就明确拦下并提示，**不能盲发**。

### 本轮环境限制（如实记录 · 已查到真因）
- **真因 1（决定性）：QEMU 子进程找不到 DLL，以 `0xC0000135`（STATUS_DLL_NOT_FOUND）退出。**
  `emulator.exe` 只是启动器，真正跑的是 `qemu\windows-x86_64\qemu-system-x86_64-headless.exe`；
  启动器把 `emulator\lib64`（含 `lib64\vulkan`、`lib64\gles_swiftshader`）只加进**自己进程**的 DLL
  搜索路径，**子进程不继承**。QEMU 死在 `main()` 之前，stdout/stderr **全空**，
  外部只看到"启动器跑了、设备一直 offline" —— 极易误判成"沙箱不让起模拟器"。
  - 实测：直接跑 `qemu-system-x86_64-headless.exe` → 退出码 `-1073741515` = `0xC0000135`；
    **把 `emulator\lib64` 加进 PATH 后 QEMU 正常起来，adb 首次看到 `emulator-5554`**。
  - 已把这条 PATH 修复写进 `tools\启动模拟器并装机.ps1`（纯增量）。
- **真因 2（已解决）：AVD 必须放在可写目录。** 表现为 QEMU 满核空转但永不注册 adb。
  日志刷的是 `Unexpected error while creating: …\Pixel_7_API35.avd\snapshot.lock.lock (error: 5)`
  —— `error 5` = **ACCESS_DENIED**：那个 AVD 在 `C:\Users\...\.android\avd` 下，沙箱写不进去，
  模拟器就无限重试。项目脚本注释里那句"避开 `C:\Users\...\.android` 的 error 5"说的就是它。
  - **修法**：用 `avdmanager` 在**工作区可写目录**新建 AVD（`dsh35`，API35 google_apis x86_64），
    并把 `ANDROID_AVD_HOME` 指过去 ⇒ 起来并跑通（`emulator-5554 device`）。
  - 另：**模拟器里 `127.0.0.1` 是它自己**，配对载荷要带
    `endpoints:["ws://10.0.2.2:19387/ws/mobile"]` 才能连上宿主网关。
- **一键取证脚本已跑通**：`tools\verify-4features.ps1`（装包 → 进对话页 → 逐项点
  「权限 / 加号 / 生成物 / 消息文本」，把 uiautomator XML 存到 `evidence\ui-4features-<时间戳>\`）。
  - **坑（写脚本时踩过）**：uiautomator 的坐标会**异步漂移**（chip 文案随权限/模型回帧变长、整行右移），
    必须**每次点击前即时重 dump**，否则会点到隔壁控件（实测把「权限」点到「模型」上）。
  - 另一个：**浮动 ActionMode 工具条在独立窗口层，uiautomator 看不到**，只能用截图确认。
- 其它可用的替代取证：`tools\accept-pairing.ps1`（项目自带）+ `harness/run-protocol-v2-test.ps1`（协议层一键回归，已 10/10 绿）。


### P2 · 性能与数据一致性
- [ ] 网关日志 `query ok: models` 每秒数十次 → 排查客户端是否在轮询模型列表。
- [ ] 任务项渲染仍缺真实数据：宿主对移动端的 `tasks` 查询返回 `todos:null`（网关 `projection forwarded: key=todos` 在推事件）。

## 三、交付与运维提醒

- **发版流程**：`pwsh -File .\release.ps1 -Version X.Y[.Z] -Notes "…"`（自动 +1 versionCode、写 `dist/version.json`、打 tag、刷 CDN）。
- **网关升级后**：先检查 `lib\index.mjs` 是否还含 `hasAnyInteractionClient`；没有就重放补丁（P0 项落地后应自动）。
- **未验证不得写成已修复**；环境造不出条件时如实标注"未验证"。
- **改完 `net/**` 先跑一次协议层回归**（2026-10-05 新增，一键）：
  ```
  pwsh -File harness\run-protocol-v2-test.ps1
  ```
  它用**App 真实 net 源码**在 JVM 上直连真网关，实跑 `requestFileList` / `requestPermissionOptions` /
  `sendMessageWithImages`（发到不存在的会话，只验证帧被 schema 接受，不建会话不触发模型），
  跑完自动吊销本次的临时设备。10 条断言全绿 = exit 0。证据样例见
  `evidence/protocol-v2-20261005/`。
  - 顺带修了一个既有问题：`harness/build.ps1` 的垫片没跟上 `GatewayClient` 后来引入的
    `android.content.Context` / `android.util.Log` / `Notifier.onGatewayState`，**本脚本原先编不过当前 src**；
    已把这 3 个垫片从 `.verify-p0\shim` 复制进 `harness\shim\` 并加进源码表。

---

## 方案 5 · 局域网自动重配（零成本，对所有用户成立）【待做 · 用户已确认记住】

**问题**：Cloudflare Quick Tunnel 的域名每次重启都会变 ✗ → 手机必须重新扫码 ✗

**思路**：隧道地址会变，但**局域网地址是固定的**（如 `192.168.2.29:3091`）✓
→ 让 App **回到家（同一 WiFi）时自动走局域网重配一次** ✓，顺便拿到电脑当前的最新公网地址并保存 ✓
→ 出门时用新地址连 ✓；隧道再变，回家一次就自动更新 ✓ **不用再扫码** ✓

**落地要点**：
- 电脑端插件：新增一个"下发当前公网地址"的接口（网关侧已有隧道地址信息 ✓）；
- App 端：检测到能连局域网时，自动拉一次该地址并持久化 ✓；连不上时依次尝试「内网 → 已存公网地址 → 重新发现」✓；
- 对外部服务**零依赖** ✓、零账号 ✓、对所有用户成立 ✓（每人自己的电脑发自己的地址 ✓）。

**验收**：在家连一次 WiFi → 出门断 WiFi → 用移动数据能直接连上（不需要重新扫码）✓。

## 方案 3 · Tailscale（每人独立、地址永久固定）【备选 · 待用户决定】

- 每人各装各的 Tailscale（PC + 手机）✓ 免费 ✓ → 各拿到一个**永不变**的地址（`100.x.y.z`）✓
- App 直接连 `ws://100.x.y.z:3091` ✓ → **扫一次永久有效** ✓，跨网络（在家/在外）都通 ✓
- 代价：手机要装 Tailscale ✗，且**会占用 VPN 通道** ✗（与其它代理/VPN 冲突 ✗）

## 其它方案的适用边界（结论）

- **方案 1（Cloudflare 命名隧道 + 域名）**：对"所有人"**不成立** ✗（每人得有自己域名；若由作者提供子域则需长期维护分配服务 ✗）
- **方案 2（ngrok 免费固定域名）**：对所有人成立 ✓ 但每人需注册 ngrok + 一次性配置 ✗
- **方案 4（会合点自动发现）**：对"所有人"**不成立** ✗（需要一个公网可写的固定位置；每人各自建 Gist 又要存 token ✗）