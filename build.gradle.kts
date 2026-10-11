// 根构建：组合照 D:\glass-compose 实测（报告 V1.0 §2.1）
//   Compose 1.12.x 要求 AGP≥9.1；AGP 9.4.1 要求 Gradle≥9.6.0；AGP 9 起 Kotlin 内置，
//   禁用 kotlin.android 但**必须**加 kotlin.plugin.compose。
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}