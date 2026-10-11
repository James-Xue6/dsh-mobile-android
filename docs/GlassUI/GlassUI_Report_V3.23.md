# GlassUI 迭代报告 V3.23 | 2026-10-09

> **触发**：交接 §6 第 3 条。该条含 4 件事，本轮**只做第 ① 件（chip 点不动）**并把它验到底。
> **A 类质感数值改动 = 0**。

---

## 0. ⭐ 本轮先把"能不能验"解决掉：模拟器接上宿主网关（重要能力解锁）

之前几轮模拟器**没有配对**，所以 `state.chips` 恒为空 —— 能力 chip 一个都不显示，
#3 的①②③ 与 #6（⋯ 菜单）**在模拟器上根本没法验**。本轮按 `docs/液态玻璃-进度与交接.md` §2.2 打通：

1. `POST http://127.0.0.1:19387/mgw/pair`（循环回环、不带 Origin）→ **HTTP 201**，`qrPayload` **554 字符**；
2. App → 设置 →「粘贴配对」→ 点输入框 → `adb shell input text <payload>` → 「配对」；
3. **成功判据（logcat）**：`DSH-Control: hello caps=[images, file-uploads, split-channels, …, goals]`；
4. 设备页随即显示 **「DESKTOP-VEM32I5 · 在线」**，点「进入对话」→ chip 出现：
   `项目 · 程序开发` / `模型 · Deepseek-V4.1-Flash` / `思考 · low`。

> ⚠️ 配对串 TTL = 5 分钟；`cmd clipboard set-text` 该镜像没实现；`am start -e pairing` 被
> `ALLOW_TEST_PAIRING_INTENT=false` 封掉（**不要**为取证去打开）。文案/坐标见 `evidence/v3260/10~12`。

## 1. §6 第 3 条①：chip 加点击（已完成 + 已验）

### 1.1 改了什么
| 文件 | 改动 |
|---|---|
| `GlassChatScreen.kt` | `GlassChatHost` 新增 **`chipTap(label: String)`**；chip 的 `Box` 加 **`.glassTap { host.chipTap(c) }`**（⚠️ 排在 `clip/background` **之前** —— V3.21 的按压双影教训） |
| `GlassChatView.java` | 新增 `chipTap()`：**按显示文案前缀**路由到正式版原有面板（`项目/模型/权限/生成物/思考/用量/任务` → `onProjectTap/onPickModel/onPickPermission/onArtifacts/onPickEffort/onUsageTap/onTasksTap`）；对不上前缀则**什么都不做** |
| `GlassChatView.java` | 顺手修：`setPermissionChip` 传入的是**裸值**（如「完全访问」），而旧视图自己拼「权限 · 」前缀 ⇒ Compose chip 原来显示成「完全访问」、且前缀路由会失配。现补回同一套拼法 |
| `GlassChatPageActivity.kt` | 切片里的匿名 host 补上 `chipTap`（只 toast，切片行为不变） |

### 1.2 实测（模拟器已配对真实网关）
| chip | 点开结果 |
|---|---|
| `模型 · Deepseek-V4.1-Flash` | ✅ **选择模型**面板：wb / Hy4 preview / Hy3 / **Deepseek-V4.1-Flash ✓** / GLM-5.3 / GLM-5.3-Flash / MiniMax-M3 / Kimi-K3（各带「思考等级 …」副行） |
| `思考 · low` | ✅ **思考等级**面板：`当前模型：Deepseek-V4.1-Flash` / Off / Low … |
| `项目 · 程序开发` | ✅ **切换项目**面板：`选中即在那个工作区新建对话` / 程序开发 · 7 个对话 |

取证件：`evidence/v3260/13~18`（`16_model_panel.png` / `17_effort_panel.xml` / `18_project_panel.xml`）。

### 1.3 ⚠️ 现状：这些面板**还是旧 Java 白面板**
`16_model_panel.png` 可见：面板是**不透明白底 + 圆角**，与全 App 玻璃语言不一致（和 §6 第 1 条
「手动添加」改造前是同一类问题）。这三块面板（模型/思考/项目）+ 权限/用量/生成物/任务 **共 7 块**，
正是 §6 第 6 条要的那套 —— 建议**统一搬进 `GlassSheetOverlay`**（骨架已具备"菜单/确认/表单"三形态，
再加一个**列表单选/多选**形态即可一次覆盖 7 块）。

## 2. §6 第 3 条的其余三件（本轮未动，待排）
| # | 事 | 现状/要点 |
|---|---|---|
| ② | 子智能体按钮质感与之前不符 | V3.17 把它的折射压到 6/8dp（原意是防"小元件位置变形"）⇒ 要按用户观感回调，**属主观档位，需用户给方向** |
| ③ | 左下加号质感消失 / 加号弹窗·子智能体弹窗不是玻璃 | 加号：V3.19 试过"去掉平色底"被整段回退；两个弹窗改用 `GlassSheetOverlay`（附件选择 / 子智能体列表） |
| — | 面板玻璃化（1.3 说的 7 块） | 与 §6 第 6 条同源，可合并做 |

## 3. 状态
| 项 | 状态 |
|---|---|
| 模拟器 ↔ 宿主网关配对 | ✅ 打通（后续 #3/#6 可真验） |
| §6 第 3 条①（chip 点不动） | ✅ 已改 + 已验（3 个 chip → 3 块真实面板） |
| §6 第 3 条②③ | ⏳ 待排（② 需用户给观感档位） |
| §6 第 3 条附带的"面板玻璃化"（7 块） | ⏳ 与 §6 #6 同源，建议一并做 |
| A 类质感数值改动 | **0** |
