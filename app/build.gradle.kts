plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dsh.mobile"
    compileSdk = 37   // Compose 1.12.x 要求 ≥37（SDK 目录 android-37 已建 junction）

    defaultConfig {
        applicationId = "com.dsh.mobile"
        minSdk = 26          // 【A2】保留 26：低版本走现有 FrostedPanelView 软件路径（规范 §5 自检第 8 条）
        targetSdk = 36
        // [V3.123 · 验收 K1 修复] 版本号**单一来源 = 仓库根的 AndroidManifest.xml**
        //   ⚠️ 原来这里硬编码 `26 / "0.86.9"`：而 `release.ps1` 改的是 **manifest** ⇒
        //   AGP 以 Gradle 为准 ⇒ 发版后 **APK 的 versionCode 永不前进**、
        //   而 `dist/version.json` 却写了新号 ⇒ 老用户被**反复提示更新、装完版本不变** = **永久更新循环** ✗。
        //   （实测铁证：发版后 `packageRelease UP-TO-DATE`、aapt2 读出的仍是 26。）
        //   现在从 manifest 读 —— 与 `release.ps1` 的写入点**同一个文件**，不可能再分叉。
        // 注：默认 DocumentBuilderFactory 非命名空间感知，直接用带前缀的字面属性名取即可。
        val manifestDoc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(file("../AndroidManifest.xml"))
        val mfRoot = manifestDoc.documentElement
        versionCode = mfRoot.getAttribute("android:versionCode").trim().toInt()
        versionName = mfRoot.getAttribute("android:versionName").trim().trim('"')
    }

    // 复用原有免 Gradle 目录布局：src/ res/ libs/ AndroidManifest.xml 都在仓库根
    // 复用原有免 Gradle 目录布局（源码/资源都在**仓库根**，而本模块目录是 app/）
    // 【实测报错】相对路径是相对本模块目录解析的 → 必须用 ../ 指回仓库根：
    //   否则 manifest 会去找 app\AndroidManifest.xml（不存在）、Java 源 NO-SOURCE。
    sourceSets["main"].apply {
        manifest.srcFile("../AndroidManifest.xml")
        java.srcDirs("../src")
        res.srcDirs("../res")
        jniLibs.srcDirs("../libs")
        // 【实测报错】`java.srcDirs` 只覆盖 Java：Kotlin 源（.kt）必须单独登记，
        // 否则 compileDebugKotlin 报 NO-SOURCE，Compose 页面不会被编进包（运行时报 Error type 3）。
        kotlin.srcDirs("../src")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // [2026-10-08 V2.0 · 真机验收] 装到用户真机上必须用**工程官方发布密钥**
    // （用户手机上是发布包签的，debug 包会 INSTALL_FAILED_UPDATE_INCOMPATIBLE）。
    // 口令不写进本文件：从环境变量 DSH_KS_PASS 读（值在仓库根 keystore.local.ps1，已 gitignore），
    // 密钥库固定 ~/.dsh-mobile-keys/dshmobile.jks（与 build.ps1 同一把，指纹 2E518D75…8ECE8E）。
    // 注意：必须声明在 buildTypes **之前**，否则 release 里 getByName 取不到。
    signingConfigs {
        create("officialRelease") {
            val ksPath = System.getenv("DSH_KS")
                ?: (System.getProperty("user.home") + "/.dsh-mobile-keys/dshmobile.jks")
            storeFile = file(ksPath)
            storePassword = System.getenv("DSH_KS_PASS")
            keyAlias = "dshmobile"
            keyPassword = System.getenv("DSH_KS_PASS")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // [V3.0 定版] debuggable 关掉：华为/荣耀 ROM 的真机崩溃栈用
            // `adb shell dumpsys dropbox --print` 就能拿到（不需要 debuggable），
            // 而 debuggable 会拖慢渲染 —— 只在定位问题时临时开。
            isDebuggable = false
            signingConfig = signingConfigs.getByName("officialRelease")
        }
    }
    buildFeatures { compose = true }
}

dependencies {
    // 原有本地 jar（免 Gradle 时代直接进 classpath；Gradle 里必须显式声明，
    // 否则 QrScanActivity 报「程序包 com.google.zxing 不存在」）
    implementation(files("../libs/core-3.5.3.jar"))
    // ★ 官方库（AndroidLiquidGlass 底层：blur / vibrancy / lens(色散) / Highlight / Shadow / InnerShadow）
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    // [V3.0] 把 Compose 挂进 `MainActivity`（纯 android.app.Activity）需要自己补
    // ViewTreeLifecycleOwner / ViewModelStoreOwner / SavedStateRegistryOwner 三件套：
    // compose-ui 把 lifecycle 当**实现依赖**，不显式声明的话这三个类在编译期不可见。
    // 注意必须用**非 ktx 基座**（实测 lifecycle-runtime-ktx / savedstate-ktx 不把这三个类当 api 暴露，
    // 三个类各自所在：lifecycle-runtime / lifecycle-viewmodel / savedstate）。
    implementation("androidx.lifecycle:lifecycle-runtime:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.4")
    implementation("androidx.savedstate:savedstate:1.4.0")
}