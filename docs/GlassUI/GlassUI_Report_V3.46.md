# GlassUI 迭代报告 V3.46 | 2026-10-11 凌晨（卡顿·目标胶囊·↓真玻璃 三件事）

> **触发**：用户「继续优化卡顿，目标胶囊修复；↓按钮真玻璃，这三个继续」。
> **设备**：真机 `A7QK023224004563`（PGT-AN10）+ 模拟器 `emulator-5554`。
> **产物**：`dist/dsh-mobile.apk`，`sha256 = 6d03004b050bc5fe5a55e1de7cd765ed5d6f8c0803ae629f7401b40b19cbb62d`
> （真机 00:15:39 全新启动复核：前台正常、dropbox 无新条目）。
> **已知可用回退包**：`dist/archive/dsh-mobile-good-0d523e88.apk`（V3.45，无目标胶囊）。
> **证据**：`evidence/v3460/*`（模拟器）、`evidence/phone-20261010-night/5xx`（真机）。

---

## 0. 三件事的结论

| # | 事 | 结论 |
|---|---|---|
| ① | **继续优化卡顿** | ✅ **实测大改善**：janky 11.56% → **7.64%**，P99 81ms → **38ms**，Slow draw commands 25 → **13**，Missed Vsync 5 → **0**；画面**等价**（差异只在边界 1–2px 抗锯齿） |
| ② | **目标胶囊修复** | ✅ **真玻璃**（`GlassSurface` + 单独一层取样），真机生产对话页验证通过：背后「思考过程」卡**透得到**、不崩 |
| ③ | **↓ 按钮真玻璃** | ❌ **真机两次复现同源 native 崩溃 ⇒ 按铁律回退**（现状仍是"观感版"，体色与下方同值）。**上限 = 同屏玻璃件个数 ≈ 10**；要真玻璃必须**先腾出名额**（见 §3） |

---

## 1. ①卡顿：把"挖形状内部"从**绘制期**搬到**位图生成期**（零画面变化）

### 1.1 先建测量台（否则没法谈优化）
新增 `tools/jank-harness.ps1`：**固定手势序列 + gfxinfo 前后对比**（模拟器能吃注入手势，真机不能 ⇒ 测量台在模拟器上）。

基线（演示对话页，8 个来回）：`Janky 34 (11.56%) / P99 81ms / Slow draw commands 25 / Missed Vsync 5`，
且直方图在 **16–17ms 有明显凸起**（一批帧刚好错过 16.67ms）。

### 1.2 改动
`glassShadow` 原来在**绘制期**用非矩形 `clipPath(Difference)` 把"形状内部"裁掉 —— HWUI 里这种裁剪要靠
stencil/`saveLayer`，而一屏有 ~10 个阴影件 ⇒ **每帧每个阴影件一次裁剪**。

改成：**位图生成期**就把内部用 `BlendMode.Clear` 清掉（填色 → 模糊 → 清内部），
绘制期只剩一次**无裁剪**的 `drawImage`。**画面等价**：原来"裁掉**未位移**的形状"，现在"清掉**未位移**的形状"。

### 1.3 实测（同一测量台、同样 8 个来回）

| 指标 | 改前 | 改后 | 变化 |
|---|---|---|---|
| Total frames | 294 | 301 | — |
| **Janky** | 34 (**11.56%**) | 23 (**7.64%**) | **−34%** |
| **P99** | 81ms | **38ms** | **−53%** |
| **Slow draw commands** | 25 | **13** | **−48%** |
| Slow UI thread | 16 | 9 | −44% |
| Slow bitmap uploads | 4 | 2 | −50% |
| **Missed Vsync** | 5 | **0** | 清零 |

### 1.4 画面等价的取证
同一启动+滚动序列下与 V3.44 截图比：平均差 **0.111**；差异热力图（`601_noclip_diff_heatmap.png`）
显示差异**只在形状边界的 1–2px 抗锯齿**上（那里本来就压着一条 0.5dp 发丝边）。

---

## 2. ②目标胶囊：真玻璃（并顺手排掉两个坑）

### 2.1 关键约束与解法
- 目标卡在**取样层内部** ⇒ 直接用 `backdrop` 会**自取样**（"一片平白"）。
  ⇒ 给**列表内容单独记一层** `contentBackdrop`（层里只有观感版卡，安全），目标卡取它
  ⇒ 卡片里能看到"从它底下滑过去的消息"被模糊/折射。
- 用 **`GlassSurface`**（不是裸 `RefractionWindow`）：后者内部是 `BoxWithConstraints + fillMaxSize()`，
  直接放进 Column **会撑满可用高度**（模拟器实测 `610`：玻璃比内容高一截）。
  `GlassSurface` 是"外层 Box + 玻璃 `matchParentSize`，高度由内容决定"的既定入口，
  且体色默认值就是 `RefractionWindowTokens.Tint`（与 ‹/⋯/＋/↑/输入框同一个值）⇒ 天然"同一材质"。

### 2.2 真机验证
`evidence/phone-20261010-night/540_prod_chat_goalglass.png`（**生产**对话页、4 个 chip 的最紧情形）：
目标胶囊是真玻璃，背后「► 思考过程 5984 字」卡**透过来**；进程存活、dropbox 无新条目 ✓。

---

## 3. ③↓ 按钮真玻璃：**真机两次崩溃 ⇒ 回退**（这条是硬结论）

### 3.1 两次真机崩溃（同源栈）
| 次 | 改动 | 结果 |
|---|---|---|
| ① | ↓ 换 `GlassRoundButton`（外面仍套一层定位 Box） | **23:59:46** `data_app_native_crash`，切片 Activity 当场死、掉回 MainActivity |
| ② | ↓ 换 `GlassRoundButton`，**并把定位交给它的 modifier（去掉多余那一层）** | **00:12:40** 同样崩，这次直接退回桌面 |

栈完全同源：
```
signal 11 (SIGSEGV)  Cause: stack pointer is not in a rw map; likely due to stack overflow
#00..#05+  /system/lib64/libhwui.so  android::uirenderer::computeTransformImpl(DirtyStack const*, Matrix4*)+56  ← 递归
```
⇒ **"减少树深度"没救 ⇒ 上限是"同屏玻璃件个数"**，与 V3.32 §0 / V3.40 的记录一致。

### 3.2 数额（数出来的）
对话页同时渲染的 `RefractionWindow` 实例：目标胶囊 + chip 行（**生产 4 个**）+ ‹ + 标题胶囊 + ⋯ + ＋ + 输入框 + ↑
（子智能体会话再 +1）= **10 个**。加第 11 个（↓）⇒ 崩。
- V3.45（无目标胶囊）：9–10 ✓
- V3.46 目标胶囊版：10 ✓（真机已验证）
- +↓：11 ✗（两次崩）

### 3.3 给 ↓ 真玻璃的三条路（**需要你选**）
| 方案 | 代价 | 效果 |
|---|---|---|
| **A. 把 chip 行的 4 个独立玻璃胶囊合并成 1 个**（腾 3 个名额） | chip 的观感会变（从"4 枚独立玻璃胶囊"变成"一条玻璃条里 4 枚 pill"） | ↓ 可以真玻璃，且还剩 2 个名额余量 |
| B. 把**目标胶囊**退回观感版，把名额让给 ↓ | 失去刚做好的目标胶囊玻璃 | ↓ 真玻璃（总数仍 10） |
| C. 维持现状 | ↓ 仍是"观感版"（体色 `RefractionWindowTokens.Tint` 与下方同值、无模糊） | 最稳 |

> 我**没有**擅自做 A —— 那一排 chip 是你在 V3.25/V3.26 亲自调过的（「看不太清楚，加磨砂、白一些」），
> 改观感得你点头。**说一声我就做 A（推荐，一次到位）。**

---

## 4. 本轮新增的测试设施（下一轮直接用）

| 设施 | 用途 |
|---|---|
| `tools/jank-harness.ps1` | **可复现的卡顿测量台**：固定手势序列 + `gfxinfo` 前后对比（模拟器） |
| `--ez glass_force_jump true` | 真机上强制渲染「↓ 回到底部」钮（否则进会话落在底部、它压根不渲染 ⇒ 没法测崩溃） |
| `dist/archive/dsh-mobile-good-0d523e88.apk` | **已知可用回退包**（玻璃件数量更少的那版） |
| （沿用）`glass_force_scrolling` / `glass_shadow_fullres` / `glass_force_press` / `glass_force_chat/device/drawer` | — |

---

## 5. 项目明细（交接单）

| 条目 | 状态 | 证据 | 未成功原因 | 下一步 |
|---|---|---|---|---|
| ① 卡顿：clip-free 阴影 | ✅ 成功（可复现测量） | janky 11.56%→7.64%、P99 81→38ms、`draw commands` 25→13、Vsync miss 5→0 | 真机 jank 需你滚 10s 我再采 | 真机 `gfxinfo` 复采 |
| ② 目标胶囊真玻璃 | ✅ 成功（真机生产页） | `phone.../540` | — | 你上手看观感 |
| ③ ↓ 真玻璃 | ❌ **真机两次崩，已回退** | 23:59:46 / 00:12:40 dropbox + 同源栈 | **同屏玻璃件上限 ≈10** | **选 §3.3 的 A/B/C** |
| 手机状态 | ✅ **已恢复到稳定包** | 00:15:39 全新启动、dropbox 无新条目 | — | — |
| 回归 | ✅ 四入口 + 生产对话页 | `541_final_stable.png` | — | — |

---

## 6. 复现

```powershell
# 卡顿测量台（模拟器）
& tools\jank-harness.ps1 -Serial emulator-5554 -Swipes 8 -Restart
# 真机：看有没有崩（logcat 加密，必须 dropbox）
& $adb -s A7QK023224004563 shell dumpsys dropbox --print | Select-String "^\d{4}-\d{2}-\d{2} " | Select -Last 3
# 真机：强制显示 ↓ 钮（测它会不会崩）
& $adb -s A7QK023224004563 shell am start -n com.dsh.mobile/com.dsh.mobile.glass.GlassChatPageActivity --ez glass_force_jump true
```

## 7. 踩坑（新增）

1. **一次加了两处玻璃件 ⇒ 崩了没法归因**（我先同时加了目标胶囊 + ↓ 真玻璃）。
   教训重申：**新增玻璃件必须一次一个、且必须真机冷启动验证**（模拟器不复现这个爆栈）。
2. **演示切片的数据和生产不一样**（3 个 chip vs 4 个）⇒ 玻璃件**数量上限**这类问题，
   **必须在生产页上验**（本轮差点漏掉：目标胶囊在切片上安全，在生产才接近上限）。
3. **`RefractionWindow` 不能直接塞进 Column 取 wrap-content 高度**（会被 `fillMaxSize` 撑满）⇒ 用 `GlassSurface`。
4. **真机不接受注入手势** ⇒ 凡是"点了/拖了才出现"的元素，必须**加一个"强制渲染"调试钩子**才能测（本轮 `glass_force_jump`）。
