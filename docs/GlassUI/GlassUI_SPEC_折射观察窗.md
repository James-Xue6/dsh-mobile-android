# GlassUI_SPEC_折射观察窗.md —— 「折射观察窗」玻璃件锁定规范

> **状态：已锁定 V1.0（2026-10-08）**
> 适用范围：凡要在 App 里复刻"那一块折射观察窗"的观感，**一律按本文件执行**；本文件优先级高于任何临时需求。
> 与 `GlassUI_SPEC.md`（GlassCapsule 通用 SOP）的关系：那份是**通用迭代流程**，本份是**这一处效果的参数与方法冻结**，两份互补。
> 实测证据：`F:\AI\程序开发\液态玻璃引擎Demo\shots\official_02.png`（模拟器实跑）、`official_zoom.png`（放大）
> 参考实现源码：`F:\AI\程序开发\液态玻璃引擎Demo\official-api\MainActivity.kt`

---

## 0. 为什么锁定的是"这一处"

同一套玻璃参数，放在不同背景/不同尺寸上观感差别极大。**折射观察窗是整套 Demo 里唯一"三要素同时达标"的位置**：

| 要素 | 观察窗里的表现 |
|---|---|
| ② 折射形变 | 背后的白网格线被**推着弯**（不是整块平移），边缘强、向中衰减 |
| ① 背景实时模糊 | 糊得住但仍透出色彩层次（不是死白、也不是"能读字"的透明片） |
| ③+④ 受光与厚度 | 整圈细亮边（左上受光 → 右下受光）+ 内侧一条极淡暗棱（厚度感） |

**唯一验收基准：真机截图**（沿用本工程铁律，模拟器/IDE 预览只作参考）。

---

## 1. 锁定的 Token（改任何一项都必须出一份迭代报告）

### 1.1 质感 Token（对应 `GlassQualityTokens`，**只有这一组允许调**）

| Token | **锁定值** | 管什么 | 为什么锁这个值（实测依据） |
|---|---|---|---|
| `glassShapeRadius` | **26.dp** | 圆角（布局参数，调用处传） | 圆角 < 短边一半；26dp 在 168dp 方块上四角圆润且不"胖" |
| `glassBoxSize` | **168 × 168 dp** | 观察窗尺寸 | 正方形最能看出四角形变；小于 120dp 时环带占比过大，形变看着"发毛" |
| `glassTint` | **White 20%**（`0x33FFFFFF`） | 玻璃体白度 | 10% → 像透明塑料片；40% → 死白丢色彩（见 `diag/质感对照.html` F 档） |
| `glassBlurRadius` | **12.dp** | 磨砂（背景模糊半径） | 官方 README 起步值 `2dp` **几乎没磨砂**；12dp 是"糊得住又留住形变"的档位；>20dp 开始丢色彩层次 |
| `lensRefractionHeight` | **14.dp** | 折射环带高（多宽的边缘参与形变） | 太小 → 只有一条硬边；太大 → 整块都在扭、看着假 |
| `lensRefractionAmount` | **22.dp** | 折射幅度 | 与 height 约 **1.5 : 1**（官方示例也是这个比例关系） |
| `lensChromaticAberration` | **true** | 色散（RGB 分离的彩边） | 官方内置能力，白送的真实感；关掉会明显更"数码感" |
| `lensDepthEffect` | **false** | 是否叠加"向心"法线 | 观察窗要的是**边缘弯曲**而非球面放大，故关 |
| `highlight` | **Highlight.Default** | 边缘受光 | 官方默认就是"左上受光 → 右下受光"的对角亮边 |
| `shadow` | **Shadow.Default** | 悬浮阴影 | 官方默认即柔和外投影（radius 24dp / offset ≈4dp / 黑 10%） |
| `innerShadow` | **InnerShadow.Default** | 内侧暗棱（厚度） | **只有亮边没有暗棱 = 像贴纸**；这是"玻璃块"与"贴纸"的分界 |
| **effects 顺序** | **`blur` → `vibrancy` → `lens`** | 三层处理的先后 | **顺序锁死**：先糊后弯才是玻璃；反过来形变会被糊掉 |
| `vibrancy` | **必开** | 模糊后的亮度/饱和补偿 | 模糊会把背景洗灰，真玻璃反而更干净透亮；不补就"蒙了一层灰" |

### 1.2 环境 Token（C 类：**背景不满足就复刻不出来**，且不许靠改玻璃参数去迁就）

| 要求 | 锁定值 | 说明 |
|---|---|---|
| 背景高频细节 | 白网格 **10dp 粗 / 160dp 间距** + 若干柔边光斑 | **玻璃只有在有高频细节的背景上才"读得出来"**：纯渐变背景被模糊后还是纯渐变，折射也无物可弯 → 玻璃看上去只是"一块平色块"（本项目两次踩到这个坑） |
| 背景色彩 | 彩色对角渐变（紫→蓝→青→绿→黄→橙→粉） | 检验"磨砂是否丢色" |
| 背景内容 | 可滚动（列表/卡片） | 检验⑤滚动同步：玻璃内外的网格线必须同一条、连续、不错位 |

> **红线**：如果玻璃看起来"不像玻璃"，**先查背景是否有高频细节**，不许直接把磨砂调弱来"让形变可见" —— 那会把玻璃变成透明塑料片（本项目第二轮返工的根因）。

---

## 2. 官方 API 配方（**卡死，照抄**）

### 2.1 依赖与版本矩阵（本机实测可用组合，勿随手改）

```kotlin
// settings.gradle.kts —— 注意 repo 顺序（本机 repo.maven.apache.org 会卡）
dependencyResolutionManagement {
    repositories {
        maven("https://repo1.maven.org/maven2")
        maven("https://maven.aliyun.com/repository/public")
        google()
        maven("https://jitpack.io")   // AndroidLiquidGlass 本体走这里
    }
}
```
```kotlin
// 根 build.gradle.kts
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
```
```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")   // AGP 9 起 Kotlin 内置，但 compose 必须显式开
}
android {
    compileSdk = 37        // Compose 1.12.x 要求 ≥37（SDK 平台目录名是 android-37.0，需 junction android-37）
    defaultConfig { minSdk = 33; targetSdk = 36 }   // 官方库要求 Android 13+（AGSL RuntimeShader）
}
dependencies {
    implementation("io.github.kyant0:backdrop:2.0.1")          // ★ 官方库（Maven Central）
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
}
```
```properties
# gradle.properties —— Gradle 默认 HTTP 无超时，一次连接卡住会"假装在跑"（最长 27 分钟零输出）
systemProp.org.gradle.internal.http.connectionTimeout=20000
systemProp.org.gradle.internal.http.socketTimeout=60000
```

**版本矩阵为什么必须是这一组**（都是实测报错倒推出来的，不是猜）：

| 约束 | 来源 |
|---|---|
| Compose 1.12.x 的 androidx 产物要求 **AGP ≥ 9.1** | Gradle 报错原文 |
| **AGP 9.4.1 要求 Gradle ≥ 9.6.0**（9.3.1 直接拒绝） | 报错原文 |
| AGP 9 起 **Kotlin 内置**：禁用 `org.jetbrains.kotlin.android`；但**必须**加 `org.jetbrains.kotlin.plugin.compose` | 报错原文两条 |
| **AGP 8.x 与 Gradle 9.6 不兼容**（9.6 移除了它依赖的内部 API，官方提示用 9.5） | 报错原文 |
| Gradle 8.14.3 只支持到 **Java 24**；本机 JBR 是 **Java 25**；本机 JDK21 **残缺**（缺 `lib/modules`） | 实测 |

### 2.2 组件代码（**内部绘制逻辑锁死，禁止重构**）

```kotlin
/**
 * 折射观察窗 = 官方 backdrop 的 drawBackdrop + effects DSL。
 * 【锁定】effects 顺序 blur → vibrancy → lens；形状/白度/折射量按 §1.1 的 Token。
 */
@Composable
fun RefractionWindow(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    radius: Dp = 26.dp,              // 布局参数：调用处可传
    tint: Color = Color.White.copy(alpha = 0.20f),
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(radius) },
                effects = {
                    blur(12.dp.toPx())                    // ① 磨砂
                    vibrancy()                            // ② 糊后补亮度/饱和（防发灰）
                    lens(                                 // ③ 折射（边缘强、向中衰减）
                        refractionHeight = 14.dp.toPx(),
                        refractionAmount = 22.dp.toPx(),
                        chromaticAberration = true,       // 色散
                    )
                },
                highlight = { Highlight.Default },        // 边缘受光
                shadow = { Shadow.Default },              // 悬浮阴影
                innerShadow = { InnerShadow.Default },    // 内侧暗棱（厚度）
            )
            .background(tint, RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) { content() }
}
```

**调用处（位置与尺寸按需改，质感 Token 不许改）**
```kotlin
val backdrop = rememberLayerBackdrop()          // 整页一个即可

Box(Modifier.fillMaxSize()) {
    // 背景内容层：这一层被玻璃取样（官方 Modifier.layerBackdrop）
    Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
        ColorfulBackground()                    // 需要高频细节，见 §1.2
        ScrollableContent()                     // 可滚动 → 自动满足⑤滚动同步
    }
    // 玻璃层
    RefractionWindow(
        backdrop = backdrop,
        modifier = Modifier.align(Alignment.TopEnd)
            .padding(top = 300.dp, end = 16.dp)
            .size(168.dp),
    ) { Text("折射观察窗", fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14141C)) }
}
```

---

## 3. 落到本 App（**重要：现状与采用的落地方式**）

**本 App 现状（已核对）**：`F:\AI\程序开发\dsh-mobile-android`
- 源码 **48 个 `.java`、0 个 `.kt`**；`libs/` 只有 `core-3.5.3.jar`；**没有任何 Compose 依赖**
- 构建走 **免 Gradle 管线**（`build.ps1`：aapt2 → javac → d8 → zipalign → apksigner）
- `minSdk = 26`（AGSL RuntimeShader 需要 **API 33+**）
- 现有玻璃件：`proto/Frost.java`（软件捕获+降采样高斯+SDF 折射）、`proto/FrostedPanelView.java`（tint/受光环/噪点/阴影）

> ⚠️ 注意：`GlassUI_SPEC.md` 里写的是 Compose 版 `GlassCapsule.kt` + `GlassQualityTokens.kt`，
> 与工程实际（Java/View）**不一致**；以本文件 §3 的现状核对为准。

### 路线 A：官方 Compose `backdrop` API（**唯一采用**；用户已认可该效果）

**A1 —— 整页 Compose（新页面推荐）**
1. 给工程引入 Gradle + AGP + Compose（版本矩阵见 §2.1；`libs/`、`res/`、`src/` 可直接复用）
2. 背景内容层用 `Modifier.layerBackdrop(backdrop)`，玻璃用 `RefractionWindow(...)`（§2.2）
3. 代价：整页背景要挪进 Compose 的 `layerBackdrop`；构建链从 `build.ps1` 换成 Gradle。

**A2 —— `ComposeView` + `rememberCanvasBackdrop`（**★ 零整页迁移，推荐给现有 Java 页面**）**

关键：官方库提供了 `rememberCanvasBackdrop { ... }`（`backdrops/CanvasBackdrop.kt`，`isCoordinatesDependent = false`）——
**背景不必由 Compose 记录，可以自己把画面画进去**。于是现有 Java/View 页面**一个字不用改**，只嵌一个小 `ComposeView` 放玻璃：

```kotlin
// 只在"玻璃区域"嵌一个 ComposeView；页面的其余部分仍是原 Java/View 代码
val glassX = 640f; val glassY = 720f          // 玻璃在屏幕上的位置（px）
val snapshot: ImageBitmap = /* 把现有页面内容抓成位图：
       可复用现有 Frost 的抓取（view.draw(Canvas(bitmap))），或按帧重抓 */ 

setContent {
    val backdrop = rememberCanvasBackdrop {
        // 把"玻璃背后的画面"按屏幕对齐画进 backdrop 层：
        // 与路线 B 实测用过的同一套平移对齐（节点局部(0,0) == 屏幕(glassX,glassY)）
        drawImage(snapshot, dstOffset = IntOffset(-glassX.toInt(), -glassY.toInt()))
    }
    RefractionWindow(backdrop = backdrop, modifier = Modifier.size(168.dp)) { /* … */ }
}
```
- **好处**：磨砂 / vibrancy / 折射 / 色散 / 受光 / 阴影**全部走官方 GPU 实现**（只抓图是 CPU）；
  比现有软件 `Frost`（CPU 做模糊+折射）更省，也不需要把背景层搬进 Compose。
- **代价**：仍需引入 Gradle + Compose（版本矩阵 §2.1）；抓图要按帧刷新（滚动跟随）。
- **机制来源**：`CanvasBackdrop` 与 `Backdrop` 接口均**读库源码核对**；"平移对齐"这一步在路线 B 已实测验证。
  ⏳ **本 A2 组合尚未端到端实测**（未做"Java 页面 + ComposeView + CanvasBackdrop"的联调），落地时先做一版验证页。

> **用户决定（2026-10-08）**：折射效果一律走**路线 A（官方 Compose API）**。下表路线 B 仅供存档追溯，**禁止采用**。

### ~~路线 B：View + RuntimeShader 移植~~ —— ❌ **已被用户否决（2026-10-08，禁止采用）**

**否决理由**：用户实测观感**很差**（同屏 A/B 后明确表示"不要用这个方案，还是刚才那个方案"）。
虽然技术上新奇有效（AGSL 着色器可挂 `RenderNode` 在纯 View 上跑通，见下方存档），但**观感不达标**，故不采用。

<details><summary>存档：路线 B 的技术结论（仅追溯用，勿采用）</summary>

**实测结论**：不用 Compose、不改构建链（仍走 `build.ps1` 免 Gradle 管线），官方那两个 AGSL 着色器**可以直接画在现有 View 上**。
实证页面：`glassdemo/src/com/dsh/glassdemo/ShaderGlassActivity.java`（包名 `com.dsh.glassdemo`）
证据截图：`F:\AI\程序开发\液态玻璃引擎Demo\shots\routeB_02.png`、`routeB_zoom.png`（2× 放大 A/B）
实测表现（同背景/同模糊/同白度亮边，只差折射实现）：
- **左（官方 AGSL）**：网格线被平滑推开并弯曲、四角出现**色散彩边**
- **右（现有软件 lens）**：形变明显更弱、线条基本直穿，且无色散
- 页内状态行显示 `RuntimeShader 就绪`（`RuntimeShader` 构造成功、无异常）

**可用写法（实测通过的最小实现）**
```java
// ① 官方着色器字符串（§4，带色散那个）
RuntimeShader shader = new RuntimeShader(AGSL_REFRACTION_DISPERSION);
shader.setFloatUniform("size", tileW, tileH);            // 玻璃尺寸（节点局部坐标）
shader.setFloatUniform("offset", 0f, 0f);                // 内容已按玻璃位置平移 → 这里给 0
shader.setFloatUniform("cornerRadii", r, r, r, r);
shader.setFloatUniform("refractionHeight", 14f * density);
shader.setFloatUniform("refractionAmount", 22f * density);
shader.setFloatUniform("depthEffect", 0f);
shader.setFloatUniform("chromaticAberration", 1f);

// ② 把着色器挂到 RenderNode 上：content 自动绑定为"节点内容"（= 玻璃背后的画面）
RenderNode node = new RenderNode("glassB");
node.setPosition(0, 0, tileW, tileH);
node.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"));
RecordingCanvas rc = node.beginRecording(tileW, tileH);
rc.drawBitmap(blurredFullScreenBitmap, -tileLeft, -tileTop, paint);   // 平移对齐：节点局部(0,0) == 屏幕(tileLeft,tileTop)
rc.endRecording();

// ③ 绘制
canvas.save(); canvas.translate(tileLeft, tileTop); canvas.drawRenderNode(node); canvas.restore();
```
- **前置**：`blurredFullScreenBitmap` = 全屏背景 → 降采样 → 2 遍 `Frost.gaussianBlur` → 放大时叠 **vibrancy ColorMatrix**（饱和 ×1.45 / 亮度 ×1.10），即官方 `blur + vibrancy` 的等价物，且与路线 A 的顺序一致。
- **表面处理完全共用**：白度 tint(20%) / 边缘受光环（7 停对角渐变）/ 内侧暗棱 / 外阴影 —— 与折射实现无关，直接沿用现有 `FrostedPanelView` 的画法。
- **回落**：整段必须用 `Build.VERSION.SDK_INT >= 33` 包起来（AGSL 需 API 33+，`RenderNode.setRenderEffect` 需 API 31+）；低版本回落现有软件 `Frost` 路径。
- **许可（必读）**：`§4` 的 AGSL 源码来自 **backdrop**（Apache-2.0，Copyright 2025 Kyant）。上面的 `ShaderGlassActivity.java` 已把版权与许可声明写在源码头部 —— 移植进 App 时**必须一并保留**（或在 `NOTICE`／关于页注明）。
</details>

## 4. 官方 AGSL 着色器原文（移植用，Apache-2.0 / Copyright 2025 Kyant）

> 取自 `backdrop-android-2.0.1-sources.jar` → `com/kyant/backdrop/internal/Shaders.kt`，一字未改。

**共享 SDF（两个 shader 都要拼进去）**
```glsl
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}
float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
```

**带色散的折射（本规范用这一个）**
```glsl
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;
// …（拼入上方 SDF）…
float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) { return content.eval(coord); }      // 环带外：原样
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius)
                            + depthEffect * normalize(centeredCoord));
    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;
    half4 color = half4(0.0);
    half4 red    = content.eval(refractedCoord + dispersedCoord);              color.r += red.r/3.5;    color.a += red.a/7.0;
    half4 orange = content.eval(refractedCoord + dispersedCoord*(2.0/3.0));    color.r += orange.r/3.5; color.g += orange.g/7.0; color.a += orange.a/7.0;
    half4 yellow = content.eval(refractedCoord + dispersedCoord*(1.0/3.0));    color.r += yellow.r/3.5; color.g += yellow.g/3.5; color.a += yellow.a/7.0;
    half4 green  = content.eval(refractedCoord);                               color.g += green.g/3.5;  color.a += green.a/7.0;
    half4 cyan   = content.eval(refractedCoord - dispersedCoord*(1.0/3.0));    color.g += cyan.g/3.5;   color.b += cyan.b/3.0; color.a += cyan.a/7.0;
    half4 blue   = content.eval(refractedCoord - dispersedCoord*(2.0/3.0));    color.b += blue.b/3.0;   color.a += blue.a/7.0;
    half4 purple = content.eval(refractedCoord - dispersedCoord);              color.r += purple.r/7.0; color.b += purple.b/3.0; color.a += purple.a/7.0;
    return color;
}
```

**边缘受光（Highlight.Default 用的就是它）**
```glsl
uniform float2 size; uniform float4 cornerRadii;
layout(color) uniform half4 color; uniform float angle; uniform float falloff;
// …（拼入上方 SDF）…
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float d = dot(grad, normal);
    return color * pow(abs(d), falloff);
}
```

---

## 5. 自检清单（改完 App 逐条打勾，真机截图为准）

- [ ] 背后的白网格线在玻璃内**被推弯**（不是整块平移、不是因为"磨砂太弱"而清晰可读）
- [ ] 磨砂到位：玻璃外能看清的条纹，玻璃内变成**柔和的色带**
- [ ] 整圈有细亮边（左上最亮 → 右下受光），**不断开、不过宽**
- [ ] 内侧有一条极淡暗棱（厚度感）—— 去掉它应能立刻看出"变贴纸"
- [ ] 玻璃下方有柔和外阴影，上部小、下部略大、柔和不实黑
- [ ] 滚动时玻璃内的网格线与玻璃外**同一条、连续、不错位**
- [ ] 背景无高频细节时，**先补背景**（§1.2），不许调弱磨砂
- [ ] Android 13 以下设备有回落路径（官方库折射依赖 AGSL，需 **API 33+**；低版本回落现有软件 `Frost` 路径）

---

## 6. 踩坑记录（不要重踩）

| 坑 | 现象 | 正解 |
|---|---|---|
| 背景无高频细节 | 玻璃=平色块，误以为"官方库不生效" | 先补粗网格/光斑（§1.2）。本项目为此返工一次 |
| 为"看见形变"调弱磨砂 | 变成透明塑料片，用户直接否 | 磨砂归磨砂，形变靠"背景线条加粗加疏"保 |
| effects 顺序颠倒 | 形变被糊掉，怎么看都在"轻微模糊" | 必须 `blur → vibrancy → lens` |
| 官方 README 的 `blur(2.dp)` 照抄 | 几乎没磨砂 | 用 12dp（§1.1） |
| 只画亮边不画内侧暗棱 | 像贴纸，没有块感 | `innerShadow = { InnerShadow.Default }` |
| Gradle 下载"卡住" | 无输出、像在跑其实死了（最长 27 分钟） | 设 HTTP 超时（§2.1）+ 只用可达 repo |
| AGP/Compose 版本随手升 | 连环报错（Kotlin 内置、compileSdk 37、Gradle ≥9.6） | 照抄 §2.1 的实测组合 |
| **圆角 > 短边/2** | 形状退化成极限态，**玻璃效果外溢到整块节点矩形** → 看着像"背景也被虚化/变形" | 组件已内置夹取；胶囊一律用 `RefractionCapsule()`，**别写 `radius = 999.dp`**（详见 §7.6） |

---

## 7. 参数调整通道（后期优化怎么改、怎么验）

> 回答"以后要调磨砂度/圆角/质感，有没有途径"——**有：三层边界 + 两条途径 + 五步流程**。

### 7.1 三层边界（先判断要改的东西属于哪层）

| 层 | 内容 | 能不能改 | 改哪里 |
|---|---|---|---|
| **组件本体** | `RefractionWindow` 内部的 `drawBackdrop` 结构、effects 顺序（blur → 色彩补偿 → lens）、highlight/shadow/innerShadow 挂法 | ❌ **锁死，禁止重构** | —（确需改先出报告说明理由） |
| **质感 Token** | 磨砂 `BlurRadius`、白度 `Tint`、折射 `RefractionHeight/Amount`、色散、受光 `Highlight.*`、`Shadow.alpha`、`InnerShadow.alpha`、色彩补偿 `saturation/brightness` | ✅ 可调（受 SOP 约束） | `RefractionWindowTokens` / 调参页 |
| **布局参数** | **圆角** `radius`、尺寸、位置、间距 | ✅ 可调 | **调用处入参**（不要写进组件内部） |

### 7.2 症状 → 拧哪个旋钮（调参时唯一该看的东西）

| 想要的效果 / 问题 | 拧这个 | 锁定值 | 建议范围 | 副作用警告 |
|---|---|---|---|---|
| 更磨砂 | `blur(radius)` | 12dp | 8 → 20dp | >20dp 开始**丢色彩层次**；<6dp 变"透明塑料片" |
| 更通透 | 同上往小调 | — | ≥8dp | 太低就失去玻璃感（本项目返工根因） |
| **胶囊圆角** / 更方 | `radius`（布局参数） | 26dp | **0 → 短边/2（硬上限）** | 胶囊请用 `RefractionCapsule()`（自动 = 短边/2）；**超过短边/2 会退化并让效果外溢**（§7.6） |
| 更白更厚 | `Tint` alpha | 20% | 15% → 30% | >40% **死白**丢色（对照页 F 档） |
| 更透 | 同上往小调 | — | ≥12% | <10% 像透明塑料（A 档） |
| 边缘形变更强 | `refractionHeight` + `refractionAmount` | 14 / 22dp | 10–20 / 16–30dp（保持 ≈1:1.5） | height 过大 → 整块都在扭；amount 过大 → 边缘"撕裂" |
| 边缘更"硬" | 同上往小调 | — | ≥8dp | 太小 → 只剩一条硬边、无过渡 |
| 色散彩边 | `chromaticAberration` | true | 开/关 | 关掉更"数码感"；强背景上开久会腻，可只部分玻璃开 |
| 球面放大感 | `depthEffect` | false | 开/关 | 开启后方形件会显得**鼓起来**（本规范要求关） |
| 玻璃"发灰"没精神 | `colorControls(saturation)` | 1.5 | 1.2 → 1.8 | <1.0 明显发灰；>2.0 过艳像滤镜 |
| 整体偏暗/偏亮 | `colorControls(brightness)` | 0f | −0.1 → +0.15 | 与 saturation 同时大改会互相掩盖，**一次只动一个** |
| 亮边太细/太粗 | `Highlight(width)` | 0.5dp | 0.3 → 1.5dp | >2dp 变成"描边"，不再是受光 |
| 亮边太抢/看不见 | `Highlight(alpha)` | 1f | 0 → 1 | <0.5 会明显变廉价 |
| 悬浮感不足 | `Shadow(radius)`+`offset` | 24dp /(0,4) | 16–32dp | 偏移太大像贴纸浮空；颜色别用纯黑 |
| 玻璃"没有厚度" | `InnerShadow(alpha)`/`radius` | 1f / 24dp | 保持开；0.6 → 1 | **关掉立刻变贴纸**（厚度来源） |
| 内部不够"实" | `InnerShadow(radius)` 调小 | 24dp | 12 → 30dp | 太小 → 像一圈描边 |

### 7.3 两条调整途径

**途径 1（推荐）：实时调参页** —— `com.dsh.glasscompose/.GlassTunerActivity`（本轮新增，**已实测**）
```powershell
adb shell am start -n com.dsh.glasscompose/.GlassTunerActivity
```
- 顶部**两块预览**（方形观察窗 + 玻璃胶囊），拖滑杆**即时**看效果
- 覆盖 **14 个旋钮**：圆角 / 磨砂 / 白度 / 折射环带 / 折射幅度 / 色彩补偿(饱和·亮度) / 亮边宽度·强度 / 外阴影强度 / 内侧暗棱强度 / 色散 / 向心法线 / 内侧暗棱开关
- 底部有 **「当前参数（照抄即可）」** 等宽文本块 —— 拖满意后直接抄进 Token 文件，不用反推数值
- 证据：`F:\AI\程序开发\液态玻璃引擎Demo\shots\official_tuner.png`

**途径 2：直接改 Token** —— `docs/GlassUI/RefractionWindow.kt` 的 `RefractionWindowTokens`（改数值即生效，无需碰组件本体）

### 7.4 五步调参流程（SOP，不许跳步）

1. **读基线**：看最近一份报告的「下一轮基线快照」，**禁止无依据重置参数**
2. **量化描述**：写成"磨砂 12dp → 15dp"（禁止"不好看"）
3. **分类**：A 质感 → 改 Token；B 布局 → 改调用处入参；C 环境（背景变了）→ **不改代码，只写说明**
4. **最小变更**：**单次最多改 2 个质感数值**；在调参页验证 → 抄回 Token
5. **存档 + 验收**：出《GlassUI迭代报告》；**以真机截图为准**

### 7.5 两个必须知道的官方实现细节

- **`vibrancy()` 是固定版**：等价于 `colorControls(saturation = 1.5f)`（库源码 `ColorFilter.kt`）。
  想**调**色彩补偿强度就别用 `vibrancy()`，改用 `colorControls(brightness, contrast, saturation)`。
- **effects 顺序不可换**：`blur` → `colorControls`(或 `vibrancy`) → `lens`。
  先 lens 后 blur 会把形变糊掉；先 colorControls 后 blur 会让色彩补偿被模糊稀释。

### 7.6 圆角红线（用户实测踩坑，务必遵守）

**现象**（用户 2026-10-08 反馈）：
> 「我开大圆角然后你弄得假背景就出来了，背景为啥也直接变形虚化了！！」

**实测定位**：
- 页面里写的是 `RefractionWindow(radius = 999.dp)`（56dp 高的输入胶囊）—— **非法圆角**（合法上限 = 短边/2 = 28dp）。
- 复现对照（同一背景、同一 blur=30）：半径 26dp 时玻璃外背景**锐利**；半径 90dp（> 短边/2）时玻璃周围出现**一圈柔化区**，节点矩形内的网格线被"洗掉"。
- **不是背景被改了**：玻璃内部本来就是"被磨砂+折射的背景"（这就是玻璃）；出错的是**效果外溢**。

**机制（实测推断）**：`RoundedCornerShape(r)` 只在 Compose 侧按"短边/2"夹取，而官方库把圆角半径作为 `cornerRadii` uniform 传给 AGSL；
当 `r > 短边/2` 时圆角矩形退化成极限态，**形状不再覆盖整个玻璃节点**，于是模糊/折射/受光的外溢落到节点矩形上。

**规定动作**：
1. **方形件的圆角 ≤ 短边/2**，且建议 **≤45% 短边**（≈67dp@150dp 方块）以保留四角；
2. **胶囊/药丸一律用 `RefractionCapsule()`**（组件自动 = 短边/2；高度改了也不失效），**禁止手写 `999.dp` 之类的大值**；
3. `RefractionWindow` 已内置 `BoxWithConstraints` **自动夹取**，调用处写多大都安全——但**不要依赖它来"实现胶囊"**，语义要用 `RefractionCapsule` 表达。

---

*本规范由「液态玻璃引擎 Demo（三引擎）」项目产出：Demo 目录 `F:\AI\程序开发\液态玻璃引擎Demo\`；官方 API 版工程 `D:\glass-compose\`。*
