rootProject.name = "blush"

include(":app")
// Code shared by the TV app and the Mac app (Kotlin Multiplatform: Android + desktop JVM).
include(":shared")
// The Mac app (Compose for Desktop), packaged as Bloom.app on CI.
include(":desktop")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
