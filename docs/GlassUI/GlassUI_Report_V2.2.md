# GlassUI 迭代报告 V2.2 | 2026-10-08

> 触发：用户「我打开的界面和你的截图不一样是咋回事，你截图的质感好的多」

---

## 1. 诊断：两件事叠在一起

### 1.1 你看的是**正式版旧页**，我截的是 **Compose 新页** —— 两套玻璃引擎

真机取证（同一台 PGT-AN10）：

| | 取证件 | 玻璃卡内**细节量**(Laplacian) | 卡内-卡外亮度差 |
|---|---|---|---|
| **你打开的**：正式版页面（`Frost` 自研引擎，软件抓取+降采样） | `30_now.png`（当时前台 `MainActivity`，显示的是 `ConversationView` 对话页） | **0.13**（近乎一块平色板） | **+0.3**（几乎看不出玻璃） |
| **我截图的**：Compose 切片页（官方 `kyant backdrop` 引擎：真模糊 + vibrancy + lens + Highlight） | `31_compose_devpage_now.png` | **2.87（约 20 倍）** | **+12.3** |

> 旧页的玻璃观感本身就"发平、发白"（这正是用户从 2026-10-07 起一直在抱怨、也是当初决定改走
> 「路线 A：Compose + 官方 backdrop」的原因）。所以"我截图的质感好得多"**不是错觉，是两套实现的差距**。

### 1.2 更关键：**新页面根本不在你桌面图标里**

```
adb shell cmd package query-activities -a MAIN -c LAUNCHER com.dsh.mobile
修复前： 1 activities found  →  com.dsh.mobile/.MainActivity        （只有旧页）
修复后： 2 activities found  →  MainActivity + glass.GlassDevicePageActivity
```
manifest 里只有 `MainActivity` 挂了 `MAIN/LAUNCHER`，其余 4 个 Compose 页只是 `exported=true`、
**没有 launcher 入口** → 用户点图标永远进旧页，只能靠 adb 才能打开新页。

## 2. 处置：给新界面加一个独立桌面入口（不动正式版）

`AndroidManifest.xml`：给 `GlassDevicePageActivity` 加 `MAIN/LAUNCHER`，标签 **「掌上通·新界面」**。
- 正式版 `MainActivity` 入口**原样不动** → 网关连接 / 保活服务 / 通知深链 / 二维码等全部既有链路照旧；
- 新界面里可以走到：设备页 →（进入对话）Compose 对话页 →（齿轮）Compose 设置页；
- 这是一个**临时入口**，等真正把页面替换进 MainActivity 之后就该删掉。

已验证：`Success` 安装 + `query-activities` 返回 **2 个入口**。

## 3. 下一步（这才是你要的"让 App 看起来像截图"）

**真正的替换 = 把 Compose 页挂进 `MainActivity` 宿主**（我前面已两次挂账待拍板，现在明确建议做）：

| 顺序 | 换哪一屏 | 成本 | 换完你会看到 |
|---|---|---|---|
| 1 | **DEVICE（设备页）** | 中：给 Compose 页加 Host 回调（进入对话/改名/删除/扫码/手动/设置/线路），MainActivity 把 `deviceHub` 换成 `ComposeView` | 打开 App 首页就是截图那套质感 |
| 2 | CHAT（对话页） | 大：要把 `List<ChatItem>` 的 9 类行在 Compose 里逐个渲染（真消息） | 对话页也跟上 |
| 3 | 抽屉 / 设置 / 其余 | 中 | 全 App 统一 |

风险控制：**旧的 `DeviceHubView` 代码保留不删**，用一行开关切回（万一真机出问题能立刻回退）。

## 4. 本轮变更

| 文件 | 改动 |
|---|---|
| `AndroidManifest.xml` | `GlassDevicePageActivity` 加 `MAIN/LAUNCHER` + 标签改「掌上通·新界面」 |
| — | 无质感 Token 改动（0.35 / blur 6dp / tint 两套 全部未动） |

## 5. 仍未完成（与 V2.1 相同）

挂进宿主（本次待拍板 → 建议按 §3 顺序做）、对话页真实消息、抽屉页、「⋯」更多面板、
设备页在线判据 / 扫码配对、图标砖 tint 与图标尺寸、正式版真机 `Frost` 偏移；
累积 **5 条待签字回写规范**；真机暗色终验（EMUI 拦 adb，需用户手动切）。
