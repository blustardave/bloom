plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL; showStackTraces = true } }
        }
    }
    namespace = "app.blush.tv"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.blush.tv"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Each automatic build gets a higher number, so updates install over the top.
        // The build number: the GitHub Actions run number plus 236, because the repository was recreated on 23 Nov 2026 and its
        // run numbers started again at 1 while the old repository had reached 236 (CI sets BLOOM_BUILD; see build.yaml).
        val build = System.getenv("BLOOM_BUILD")?.toIntOrNull() ?: 1
        versionCode = build
        // Shown as e.g. "0.2.3": 0.2 is the Bloom series, and the last number counts the builds since it began (run 49
        // = 0.2.0). Start a new series by bumping SERIES and setting SERIES_START to that build's run number.
        val series = "0.3"
        val seriesStart = 83
        versionName = "$series.${(build - seriesStart).coerceAtLeast(0)}"
        // Fire TV and Android TV devices are ARM; leaving out the x86 copies of the FFmpeg decoder makes the APK smaller.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // One fixed signing key for every build. Android refuses to update an app signed with a
    // different key, and each GitHub build would otherwise make a new one.
    signingConfigs {
        create("blush") {
            storeFile = file("blush.keystore")
            storePassword = "blushblush"
            keyAlias = "blush"
            keyPassword = "blushblush"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("blush")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("blush")
        }
    }

    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }

    lint {
        abortOnError = false
    }
}

base.archivesName.set("blush")

dependencies {
    // Code shared with the Mac app (theme colours, subtitles, languages, Surprise me options...).
    implementation(project(":shared"))
    implementation(libs.jellyfin.sdk)
    // The Jellyfin SDK logs through SLF4J, which the app has to supply.
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.nop)

    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core)
    // Installs the baseline profile (src/main/baseline-prof.txt) on first launch, so the app starts compiled instead of interpreted.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    
    implementation(libs.androidx.media3.ui)
    implementation(libs.jellyfin.media3.ffmpeg.decoder)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Screenshot test of the player prompts (src/test, PromptShots): renders them to PNGs on CI so they can be checked by eye.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    debugImplementation("androidx.compose.ui:ui-test-manifest:${libs.versions.androidx.compose.get()}")
}
