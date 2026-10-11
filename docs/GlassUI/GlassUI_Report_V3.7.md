# GlassUI 迭代报告 V3.7 | 2026-10-08

> 触发：「**对话、抽屉、对话选择列表，也要弄成玻璃质感的；各类弹窗都要统一成这种风格；连接设置也是，所有都要替换掉**」
> 上轮：V3.6（App 级全面屏适配）

---

## 1. 本轮做的：③ 会话列表 + ④ 抽屉（同一个面）

抽屉的**外壳**（遮罩 / 左侧面板 / 滑入滑出 / 宽度 / 安全区）本来就是 `DrawerHost` 的真玻璃 + 动画，
工作正常 ⇒ 本轮只把它**面板里的内容**从旧的 `SessionListView` 换成 Compose 版。

### 1.1 新增 `glass/GlassSessionList.kt`
- `GlassSessionState`：`rows`（真对象：`WorkspaceGroup` 分组头 / `SessionInfo` 会话行）+ `currentId` + `status` + `themeRev`
- `GlassSessionHost`：openSession / newChat / settings / devices / refresh / rename / archive /
  toggleChildren / workspaceMenu
- `GlassSessionListScreen`：大标题「对话」+ **刷新/设备/设置三个圆钮** + 分组头 + 会话卡

### 1.2 会话卡内容（全部读真字段）
| 元素 | 来源 |
|---|---|
| 标题（空则「（未命名会话）」） | `SessionInfo.title` |
| 「待处理」徽标 | `SessionInfo.pending > 0` |
| 运行中小圆点 | `SessionInfo.running` |
| 「子会话 N / 收起 N」 | `SessionInfo.childCount / expanded` → `host.toggleChildren` |
| 子会话缩进 | `SessionInfo.childDepth` |
| 副标题（运行中 · 目录名 · 时间） | `running / cwd / updatedAt` |
| 点整卡进入 | `host.openSession` |
| 右侧「⋯」→ 重命名 / 归档 | `host.rename / archive` |
| 分组头「⋯」 | `host.workspaceMenu` |
| 当前会话高亮 | `currentId` |

### 1.3 数据桥（**不新建映射层**，与对话页同一套路）
`MainActivity` 的 `listScreen` 照旧被完整喂着（回退落点），另加三个镜像入口
`setSessionRows / setSessionStatus / setSessionCurrent`（旧视图 + Compose 状态双写），
并把 `listScreen.setRows(buildRows())` 等 **10+ 处调用点批量换成镜像入口**。
抽屉里挂的就是 ComposeView（`USE_GLASS_DRAWER` 一行回退）。

## 2. 踩坑（已进错题本）

**批量替换把 helper 自己内部的调用也替换了** → `setSessionStatus` 里变成 `setSessionStatus(s)` 自递归 →
点抽屉即 `StackOverflowError`（真机/模拟器都崩，前台回桌面）。
教训：**给"包装/镜像"方法做批量替换时，必须把 helper 自身的定义体排除掉**
（或在替换后再单独核对 helper 三行）。已修并在方法内加了显式注释防止复发。

## 3. 验收（模拟器）

| 取证件 | 结果 |
|---|---|
| `evidence/v370/02_glass_drawer_fixed.png` | 抽屉内容是 **Compose 版**：大标题「对话」+ 刷新/设备/设置三个圆钮 + **真实状态行「正在连接电脑…」**（来自 `setStatus` 镜像）+ 空列表；抽屉玻璃外壳不变；无崩溃 |

**真机**：⏳ 仍需你在手机上点一次系统「安装」确认（`adb install` / `pm install` 都卡在 `PackageInstallerActivity`）。

## 4. 你这次要求的剩余部分（下一步顺序）

| # | 项 | 状态 |
|---|---|---|
| ③ 会话列表 | ✅ 本轮（内容已 Compose；**整屏空态**（图标+「点右下角 ＋ 给 Agent 派个任务」）还没搬） | 基本完成 |
| ④ 抽屉 | ✅ 本轮（外壳本来就是玻璃） | 完成 |
| ⑤ **连接设置页**（`SettingsView`：地址/令牌/主题/配色/通知…） | ⏳ 下一轮（Compose 设置页已有一版，需要把它补成正式版设置的全部真实项） | 待做 |
| ⑥ **各类弹窗统一**：添加设备选择、附件选择、模型/思考/权限/生成物/用量/工作区面板、审批/提问、重命名/归档、诊断弹窗… | ⏳ 待做（目前宽泛的 dialog/sheet 仍是旧样式） | 待做 |
| 扫码页 / 交付物预览页 | ⏳ 待做 | 待做 |

> ⑥ 建议做法：先抽一个**统一的 Compose 弹窗骨架**（底部 sheet / 居中卡两种形态 + 玻璃卡 + 圆钮 + 列表行），
> 再逐个把旧 sheet 的内容搬进去 —— 一次性风格统一，比一处处改旧 View 划算。
