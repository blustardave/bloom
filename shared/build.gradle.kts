// Code shared by the TV app (Android) and the Mac app (desktop JVM). Only plain Kotlin and Compose go in commonMain;
// anything that needs Android (ExoPlayer, SharedPreferences, resources) stays in :app for now.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "app.blush.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(libs.jellyfin.model)
            api(libs.cmp.runtime)
            api(libs.cmp.foundation)
            api(libs.cmp.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
