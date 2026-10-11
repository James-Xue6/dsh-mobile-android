# GlassUI 迭代报告 V3.20 | 2026-10-09

> **触发**：用户「读 `GlassUI_换会话交接_20261009`，然后重新修改」+「按顺序一个一个来，先在模拟器上测试，我定好再实施」。
> **本轮 A 类质感数值改动 = 0**，**未改任何玻璃代码**；本轮只做两件事：**修正模拟器启动配方** + **§6 第 1 条的现状量化取证**。

---

## 1. 模拟器：我上一轮判断错了（已纠正）

### 1.1 我踩的坑（错误结论的来源）

| 我做的 | 结果 | 正解 |
|---|---|---|
| 用 `F:\AI\程序开发\android-sdk\emulator\emulator.exe` 启动 | 写 `C:\Users\Administrator\.android\*.lock` 报 `error 5` → **无限重试自旋**，QEMU 客户机 CPU 冻在 0.4s、console 端口不监听、adb 永不注册 | 用 **`C:\Users\Administrator\emu_copy\emulator\emulator.exe`** |
| 自己新建 AVD（Glass37/Glass35）与 emulator home（`.emu-home`） | 同样不起 | 用 **`Pixel_7_API35`**（默认 `.android\avd`） |
| 加 `-no-window` / `-gpu off` 等绕 | 部分组合还会 `0x40000015 STATUS_FATAL_APP_EXIT` | **不要** `-no-window`、**不要** `-gpu off` |

> 这个坑 **2026-10-03 就已经被查清并写进报告**：`_dsh-mobile-evidence/emulator10/README-结论.md`
> 「根因是 `ANDROID_EMULATOR_HOME` 默认目录建 `*.lock` 失败(error 5) + 无限重试自旋」；
> 且 `docs/液态玻璃-进度与交接.md` §2.1《模拟器启动（**必须照做**）》已给出确切路径。
> **我开工时没有先读这两份文档，属重复劳动 + 走错路，记在本报告与本轮错题本。**

### 1.2 已验证的正确配方（2026-10-09 实测，30s 内 `boot_completed=1`）

```powershell
$adb='C:\Users\Administrator\emu_copy\adb\adb.exe'
$env:TEMP='F:\AI\程序开发\.tmp'; $env:TMP=$env:TEMP        # 否则 adb 守护进程写不了 %TEMP%\adb.log
$env:ANDROID_SDK_ROOT='F:\AI\程序开发\android-sdk'
& 'C:\Users\Administrator\emu_copy\emulator\emulator.exe' -avd Pixel_7_API35 -no-snapshot -gpu host
# 约 30~70s 后：& $adb -s emulator-5554 shell getprop sys.boot_completed  → 1
```

- 本次实测：`emulator-5554 device`，`boot_completed=1`（30s）；物理 1080x2400 @420dpi（= 411dp × 914dp）。
- 我上一轮造的临时 AVD（`.emu-home`，逻辑 17GB）**已删除**；F 盘可用空间 10.5GB → 18.8GB。

---

## 2. §6 第 1 条现状量化（设备页 + 添加设备）

装机版本 `0.86.9`（`app-debug.apk`，构建于 2026-10-09 01:38:07，晚于全部源码 → 即 V3.18 基线，**未重编**）。
量纲：density 420 ⇒ 1dp = 2.625px。

### 2.1 玻璃「添加设备」菜单面板（正常，无多余空腔）

| 项 | 实测值 |
|---|---|
| 面板水平范围 | x 0 → 1080 = **通栏 411dp**（左右无内缩 ✓ 与 V3.15「通栏」一致） |
| 面板垂直范围 | y **825 → 1577** = 752px = **286dp** |
| 圆角 | 30dp |
| 标题「添加设备」 | 文字 y 903→966（24dp 高），x 从 48 |
| 行 1「扫码添加」/ 行 2「手动添加」 | 文字 y 1024→1087 / 1235→1298 |
| 行卡 | 横向 padding 13dp、纵向 12dp、行图标 34dp、行间距 8dp |
| 取消按钮 | 文字 y 1448→1511，按钮高 46dp |
| 抓手 | 38×4dp |

> ⚠️ **先纠正一个我差点写错的结论**：像素比对一度显示 y=1900~2390 比关屏时"整体偏亮 15 级"，疑似"面板铺到屏底、底下 313dp 空面板"。
> 做了**同相位开/关对拍**（`04_page_closed.png` vs `05_sheet_open.png`）后证伪：diff 在 **y=825 突增、y=1577 回落**，
> 上下残留的 ~7–14 级是 **34% 黑色遮罩 + 环境背景动画漂移**。
> ⇒ **面板是内容自适应的 286dp，没有空腔**。这条很重要：**"框大"不能用"缩小内边距"来治**（V3.19 就是这么改的，属方向不明）。

### 2.2 旧「手动添加设备」对话框（= 待办第 1 条的后半段，**确认仍是旧界面**）

点「手动添加」→ 弹出 **不透明白底 Java 对话框**（`MainActivity.showManualAddDialog()`，`MainActivity.java:7325`），**完全不是玻璃材质**：

| 项 | 实测值 |
|---|---|
| 卡片水平范围 | x 32 → 1048 = **387dp**（左右各 12dp 边距；与玻璃面板的"通栏"不是一套语言） |
| 卡片垂直范围 | y **849 → 2305** = 1456px = **555dp** |
| 材质 | 浅色主题下 **纯白不透明**、无模糊/无折射/无白棱，非玻璃 |
| 内容 | 内网地址 / 公网地址 / 设备令牌 / 设备名称（4 行，各带标签+输入框+提示）+ 取消 / 保存并连接 / 扫码添加 |
| 文本 inner padding | 16dp（文本 x 74 起） |

### 2.3 结论（待用户定目标值）

- 第 1 条的两个问题**都已复现并量化**，但"框大"指向哪个面**尚未确认**：
  - 若指**玻璃菜单面板**（286dp）：它是内容驱动的，缩小只能靠减少行内边距/字号/间距，**或**根本是"行卡太厚"观感问题 ⇒ 需要你点一下"是高度、还是每行的厚度"。
  - 若指**旧对话框**（555dp）：那它连同"非玻璃"一起，应由新增的**表单形态 `showForm(...)`** 整体替换 —— 尺寸自然重做，不必单独调。
- 按 SOP：**先由你定目标值，我再动手**；动手时也 **一次只改一处**、改完立即在模拟器出对比图。

---

## 3. 取证件（本轮，全部在模拟器 emulator-5554）

| 文件 | 内容 |
|---|---|
| `evidence/v3250/01_device_page.png` | 设备页（无设备态）基线 |
| `evidence/v3250/02_add_sheet.png` / `.xml` | 玻璃「添加设备」菜单面板 + 控件 bounds |
| `evidence/v3250/03_manual_add.png` / `.xml` | **旧 Java「手动添加设备」对话框**（非玻璃）+ bounds |
| `evidence/v3250/04_page_closed.png` | 关屏同相位对照 |
| `evidence/v3250/05_sheet_open.png` | 开屏同相位对照（用于定面板上下边） |
| `F:\AI\程序开发\.tmp\emu-pixel35.log` | 本次启动日志 |

## 4. 环境/命令（本轮新增/纠正）

- **模拟器（纠正）**：见 §1.2 —— `emu_copy` 二进制 + `Pixel_7_API35` + `-no-snapshot -gpu host`，**不要** `-no-window`/`-gpu off`。
- **adb**：用 `C:\Users\Administrator\emu_copy\adb\adb.exe`；调用前必须 `TEMP/TMP=F:\AI\程序开发\.tmp`。
- **装机**：`adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk` → `Success`。
- **进屏调试钩子**：`am start -n com.dsh.mobile/.MainActivity --ez glass_force_device true`。
- **模拟器可注入点击**（`input tap`），真机不行 ⇒ 模拟器上可以把弹窗点开取证。

## 5. 状态

| 项 | 状态 |
|---|---|
| 模拟器可用 | ✅ 已修好（配方纠正） |
| 第 1 条基线量化 | ✅ 已出（§2） |
| 第 1 条实施 | ⏳ **等用户定目标值** |
| 其余 §6 待办（2–8 + 追加） | ⏳ 未动 |
| A 类质感数值改动 | **0**（本轮未改任何代码） |
