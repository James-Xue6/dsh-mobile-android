// dsh-mobile-android —— 路线 A 迁移（Compose + 官方 backdrop）
// 版本与源全部照 D:\glass-compose 实测组合，勿随手改。
pluginManagement {
    repositories {
        maven("https://repo1.maven.org/maven2")
        maven("https://maven.aliyun.com/repository/public")
        google()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven("https://repo1.maven.org/maven2")
        maven("https://maven.aliyun.com/repository/public")
        google()
        maven("https://jitpack.io")   // AndroidLiquidGlass 本体；底层库 backdrop 在 Maven Central
    }
}
rootProject.name = "dsh-mobile-android"
include(":app")