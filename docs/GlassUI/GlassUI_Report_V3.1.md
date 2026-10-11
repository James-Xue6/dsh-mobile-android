# GlassUI 迭代报告 V3.1 | 2026-10-08

> 触发：用户「还有按钮需要增加动效，按动的时候没有效果呢现在，按下去增加一个**变暗然后缩小**的动画吧」
> 上轮：V3.0（设备页挂进 MainActivity）　**验收：模拟器取证 + 真机复装**

---

## 1. 现状诊断（先查清"为什么没效果"）

| 项 | 事实 |
|---|---|
| 原实现 | `glassTap` 只有 `graphicsLayer { scale = 0.98 }` —— **2% 缩放**，玻璃件还要重采样背景，位移基本被"吃掉" |
| 能否用 adb 验证按下态 | ❌ **不能**：真机（荣耀/EMUI）与模拟器上，`adb shell input motionevent DOWN` / `input swipe x y x y 900` 都**驱动不了 Compose 的按下态** —— 按住 700ms 截屏，目标区域变化像素 **0/178500**（只有时钟在变） |
| 由此产生的误判 | V1.9.2 我量到的「真机按下 0 像素变化」被我当成"缩放无效"，其实那是**假阴性**（按下态压根没产生）。本轮改用**强制渲染**才拿到真证据 |

## 2. 变更（`glass/GlassAmbient.kt` 的 `glassTap`）

```kotlin
fun Modifier.glassTap(corner: Dp = 999.dp, scale: Float = 0.96f, dim: Float = 0.12f, onClick)
```
按下时两条腿一起走：

1. **变暗**：内容之上再刷一层**黑 12%**，用 `drawRoundRect` 按 `corner` 圆角裁剪 ——
   默认 999dp 由绘制期 `min(corner, 短边/2)` **夹成胶囊/正圆**；方形卡片必须传自己的圆角
   （**否则会重演 V1.9.2 那次"方角灰块"事故**）；
2. **缩小**：**0.96**（4%，比 2% 明显），由 `animateFloatAsState` 驱动（按下 70ms / 回弹 140ms）——
   动画值变化触发**重组**、生成新的 Modifier，不依赖 `graphicsLayer` 内部的状态观察，更稳。

调用点共 11 处全部继承新行为；其中 2 处显式传圆角：**添加设备卡 28dp**、**对话页问题选项 12dp**。

## 3. 验证（关键：加了一个调试钩子才验得出来）

因为 adb 驱动不了按下态，本轮加了 `GlassDebug.forcePressed`：
`adb shell am start -n <activity> --ez glass_force_press true` 可**强制把玻璃件画成按下态**截屏取证
（默认 false，不影响正常逻辑；`GlassDevicePageActivity` 与 `MainActivity` 都读这个 extra）。

**取证结果（模拟器，同一页面、同一位置）**

| | 结果 |
|---|---|
| 静止 vs 强制按下：全屏变化像素 | **221100** |
| 添加卡区域变化像素 | **95159 / 134400**（原先 adb 真实按住：**0**） |
| 卡内平均亮度 | 静止 **177.7** → 按下 **158.3**（**变暗 −19.4**） |
| 观感 | `evidence/v310/32_press_ab.png`：上=静止（亮玻璃）、下=按下（**明显更暗 + 整卡缩小**） |

**真机**：最终 release（官方密钥签名、`isDebuggable=false`）已装机，`MainActivity` 前台正常，
`dumpsys dropbox` 最后一次 `data_app_crash` 仍停在 **21:22:47**（V3.0 修好之前）→ 本轮无新崩溃。

## 4. 想微调的两个数（都在 `glassTap` 的默认参数里）

| 想要 | 怎么改 |
|---|---|
| 更狠一点 | `dim = 0.18f`、`scale = 0.94f` |
| 更含蓄 | `dim = 0.08f`、`scale = 0.98f` |

## 5. 待办（与 V3.0 一致，未变）

② CHAT（真实 `ChatItem` 9 类行）→ ③ 会话列表 → ④ 抽屉 → ⑤ 设置页补全 → ⑥ 扫码/交付物/各 sheet；
真机暗色验收（EMUI 拦 adb 强制，需用户手动切深色）；**累积 7 条规范回写待签字**。
