// Code shared by the TV app (Android) and the Mac app (desktop JVM).
// - src/commonMain: plain Kotlin and Compose only.
// - src/jvmShared: code that also needs Java's own library (files, UUIDs...) and the Jellyfin SDK: the server calls (Repo),
//   settings (Store), caches. Both apps run on Java, so this folder is compiled into each of them.
// Anything that needs Android itself (ExoPlayer, SharedPreferences, resources) stays in :app.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
        for (name in listOf("androidMain", "desktopMain")) getByName(name) {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies {
                api(libs.jellyfin.sdk)
                api(libs.kotlinx.serialization.json)
            }
        }
    }
}

// The TV app compiles for Java 11 bytecode (Android's default), and an `inline` function from here (e.g. `logged`) can only be
// copied into it if this side uses the same level. Without this the Android half of shared defaults to the build's Java 21.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>()
    .matching { it.name.contains("Android", ignoreCase = true) }
    .configureEach { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
