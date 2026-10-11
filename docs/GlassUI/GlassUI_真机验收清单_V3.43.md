# GlassUI 真机验收清单（截至 V3.43 · 2026-10-10）

> 🚨 **V3.46 硬约束（新增）：对话页同屏 `RefractionWindow` 上限 ≈10 个，第 11 个真机必崩**
> （`data_app_native_crash` / `SIGSEGV stack pointer is not in a rw map` / `computeTransformImpl` 递归爆栈；
> 两次真机实证 23:59:46、00:12:40）。**任何"新增玻璃件"的改动，必须真机冷启动验证**（模拟器不复现）。
> 现状 = 10 个（目标胶囊 + chip 行 4 + ‹ + 标题 + ⋯ + ＋ + 输入框 + ↑）。
> 已知可用回退包：`dist/archive/dsh-mobile-good-0d523e88.apk`。
> 新钩子 `--ez glass_force_jump true`：真机上强制渲染「↓ 回到底部」钮（平时落在底部时它不渲染 ⇒ 测不到崩溃）。

> ⚠️ **V3.45 实证：这台 ROM 上 `input tap` / `input swipe` 对普通应用完全无效**
> （连发两次注入后截图与之前**逐字节相同**，差异 0.00）⇒ **"点了/拖了会怎样"的验收只能人工上手**。
> 已自动跑完的部分与**人工清单（约 3 分钟）**见 `GlassUI_真机报告_V3.45.md` §1/§4。

> 用途：**手机一插上就照着跑**，不用再想"该看哪"。
> 待装产物：`dist/dsh-mobile.apk`，`sha256 = d0c76367f83cc8b60cae338850eae084a43ea8728e9530dd353d796d4642a155`
> 签名已核对：`SHA-256 = 2e518d75…8ece8e`（**工程官方发布密钥** ⇒ 可覆盖安装，不会 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`）。
> 设备：`A7QK023224004563`（HONOR PGT-AN10，1312×2848 @560dpi，Android 16）。

---

## 0. 插机后第一步：装 + 冷启动体检

```powershell
$adb = "C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe"
& $adb devices -l                       # 必须看到 A7QK023224004563 且状态 device

# HONOR 会弹系统安装确认框、且会拦住注入触摸 ⇒ 已验证可行的顺序：
& $adb -s A7QK023224004563 shell am force-stop com.android.packageinstaller
& $adb -s A7QK023224004563 install -r dist\dsh-mobile.apk
# 若仍弹框：人工点一次「继续」（或打开系统里类似「通过 USB 安装应用」的开关）

# 装机后**冷启动**体检（V3.40 那个 native 崩都是冷启动/首次滚动就炸）
& $adb -s A7QK023224004563 shell am force-stop com.dsh.mobile
& $adb -s A7QK023224004563 logcat -b crash -c
& $adb -s A7QK023224004563 shell am start -n com.dsh.mobile/.MainActivity
# 等 8 秒
& $adb -s A7QK023224004563 shell dumpsys activity activities | Select-String topResumedActivity
& $adb -s A7QK023224004563 logcat -b crash -d            # 期望 0 条
& $adb -s A7QK023224004563 shell dumpsys dropbox --print | Select-String "com.dsh.mobile"   # HONOR 的 native 栈在这里
```

> 真机**不接受注入点击**（`input tap` 实测无效）⇒ 逐屏验收靠"启动即到某屏"的调试钩子：
> `--ez glass_force_chat true` / `glass_force_device true` / `glass_force_drawer true` / `glass_force_press true`
> ⚠️ `glass_force_press` 用完必须重启 App，否则停在"全按下"态。
>
> **V3.44 新增两个入口**（手机上也一样能跑，用于"抽屉有数据/没数据都能验"）：
> - `& $adb -s A7QK023224004563 shell am start -n com.dsh.mobile/com.dsh.mobile.glass.GlassSessionPageActivity`
>   —— 抽屉/会话列表**演示切片**（真 `SessionInfo`/`WorkspaceGroup` 对象 + 真渲染路径），专门用来点两处 ⋯ 的树内浮层；
> - `--ez glass_force_scrolling true` —— 把"滚动中"标记强制置真，用来在同位置看"行阴影关掉"的样子。

---

## 1. 必须真机才能定论（模拟器量不出来的）

| # | 项 | 怎么验 | 判据 |
|---|---|---|---|
| 1 | **本轮改动的玻璃观感**（V3.42/V3.43） | 逐屏看：设置页顶栏胶囊 / 对话页顶栏与底栏 / 设备页 / 抽屉 | 顶栏能"糊到从它底下滑过去的内容"；**设置页顶栏是 V3.43 刚修的取样层，优先看这一处** |
| 2 | **刻度尺手感**（V3.42） | 手指在右缘拖动 | ①1:1 跟手 ②落点准（跳到对应回合）③每换一格有"咔"的触感 ④气泡不盖住手指/不挡手势 |
| 3 | **↓ 回到底部圆钮** | 上滑后出现，点它 | 位置是否顺手（V3.42 下移了 36dp）、体色与 ＋/↑ 是否"同一材质" |
| 4 | **卡顿是否真降**（V3.33 改的半分辨率阴影） | 滚动采样后 `dumpsys gfxinfo com.dsh.mobile` | 对比基线 janky **9.63%** / P99 **93ms** / `Slow issue draw commands 395`：看是否下降 |
| 5 | **深色主题** | 手动切系统深色（EMUI 拦 adb 强切） | 刻度横条、顶栏胶囊、卡片在深色下是否正常（模拟器**没截过深色**） |
| 6 | **抽屉 ⋯ 菜单**（Java 版） | 手点抽屉里工作区 ⋯ / 会话卡 ⋯ | V3.31 回退成 Java 菜单后**真机从没点过**；能正常弹出/执行即可 |
| 7 | **按压双影**的 4 处 | 抽屉有会话时按住：会话卡 ⋯ / 提问卡选项 / 允许·拒绝 / 设置页配色圆点 | 按下时**整块**一起缩+暗，没有"原亮度的一圈" |
| 8 | **触感反馈** | 刻度每换一格 | 有轻"咔"（模拟器无触感） |
| 9 | **抽屉两处 ⋯ 的树内玻璃浮层**（V3.44 新做） | 手点工作区头右侧 ⋯ / 会话卡右侧 ⋯ | ① 弹出的是**玻璃观感卡**（不是系统灰对话框）②「在此工作区新建任务」/「重命名 / 归档」能执行 ③ 点卡片外关闭 ④ **不闪退**（这条以前崩过 10 次） |
| 10 | **滚动时行阴影消失**（V3.44 新做，卡顿优化②） | 列表快速上下滑，看卡片边缘 | 滑动中卡片阴影**消失**、**松手立即恢复**；观感是否可接受（若觉得"滑起来发飘"，说一声我关掉） |

---

## 2. 还没做的活（不需要真机也能开工，改完要真机验）

| # | 项 | 出处 | 备注 / 铁律 |
|---|---|---|---|
| 1 | 抽屉两处 ⋯ 的**玻璃观感**（当前是宿主 Java 菜单） | V3.32 §2.1 | ✅ **V3.44 已做完**（树内浮层；见 `GlassUI_Report_V3.44.md`）—— 真机手点复核即可 |
| 2 | 目标胶囊的**规定玻璃质感** | V3.32 §2.3 | 铁律：**不能新增 `RefractionWindow`**（真机必崩）⇒ 只能复用同层已有玻璃子树 |
| 3 | 卡顿优化 **②**：列表滚动时跳过行阴影 / **③**：列表行阴影改浅描边 | V3.32 §2.4 | ①半分辨率已做；②**V3.44 已做完**（`LocalGlassScrolling`，同一位置 A/B 验证）；③**未做**（观感项，等你点头） |
| 4 | ↓ 圆钮要不要**真玻璃** | V3.42 §4 | 路径：**先减一个现有玻璃节点**（如顶栏 ⋯ 改观感版）再加它，保持节点数不增 + **真机冷启动**验证。**需要你拍板** |
| 5 | 对话页首帧 **`canScrollForward` 竞态** | V3.33 §6 | 列表尚未测量时它就为 false ⇒ 进会话可能"莫名滚走"，也污染截图对比。改动会触及"进会话是否贴底"的**产品口径** ⇒ 需你定 |
| 6 | V3.42 §4 的三个口味项 | V3.42 §4 | ①没白底后横条够不够显眼 ②拖动"每格跳一下" vs 平滑跟 ③↓ 钮真玻璃 |
| 7 | 设备页若以后加"悬浮玻璃条" | V3.43 §3 | 设备页取样层**也只有背景**，但它当前没有悬浮玻璃件 ⇒ 暂时不影响；一旦加，必须同样做两层取样 |

---

## 3. 卡在 App 之外 / 等你一句话

| # | 项 | 需要你做什么 |
|---|---|---|
| 1 | **目标展示列表**（数据侧） | 打开「设置 → 日志」，看有没有 `目标帧 keys=… objective.len=…` 那一行。**有** ⇒ App 侧收到了、问题在渲染；**没有** ⇒ 网关没推 goal 帧（V3.32 §1 的定论） |
| 2 | 「弹窗内容与实际有些缺失」 | 举**一个**例子（哪个面板、缺什么），我才能补 |
| 3 | 「任务」面板 | 给一条**有 todos 的真实会话**（网关当前 `tasks 帧 n=null`） |
| 4 | Compose 弹窗**返回键** | V3.32 已加 `BackHandler`；确认口径即可 |
| 5 | 桌面端 **v2 会话迁移补丁** | **关掉 DSH 后**双击 `tools/dsh-asar-patch/安装补丁-关闭DSH后双击.bat` |
| 6 | 顶栏**高光对比**（交接 §6 #8） | 要动**组件本体**（SOP 锁定）⇒ 需你点头 |
| 7 | 能力 chip 深色档 / 底部渐变收口（接手须知 §7） | 要不要做，你定 |

---

## 4. 拍照/取证命令速查

```powershell
# 截图（真机；exec-out 重定向在 PowerShell 里会写坏 PNG，必须用设备文件 + pull）
& $adb -s A7QK023224004563 shell screencap -p /sdcard/s.png
& $adb -s A7QK023224004563 pull /sdcard/s.png evidence\phone-YYYYMMDD\x.png

# 卡顿
& $adb -s A7QK023224004563 shell dumpsys gfxinfo com.dsh.mobile
# 崩溃
& $adb -s A7QK023224004563 shell dumpsys dropbox --print
```
