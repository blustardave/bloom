// The Mac app. Built on the Linux CI runner into one jar (bloom.jar) and wrapped as Bloom.app by desktop/mac/make-app.sh.
plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.compose)
}

// Which machine's graphics library (Skia) goes into the jar: the CI's own (linux-x64) for screenshots, or
// macos-arm64 for Bloom.app (passed as -PdesktopTarget=macos-arm64).
val desktopTarget = providers.gradleProperty("desktopTarget").getOrElse("linux-x64")
val bloomVersion = (System.getenv("BLOOM_BUILD")?.toIntOrNull() ?: 83).let { "0.3.${(it - 83).coerceAtLeast(0)}" }

dependencies {
    implementation(project(":shared"))
    implementation(libs.cmp.desktop)
    runtimeOnly("org.jetbrains.compose.desktop:desktop-jvm-$desktopTarget:${libs.versions.compose.multiplatform.get()}")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    // Pictures (posters, profile pictures), the same library as the TV app.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // The Jellyfin SDK logs through SLF4J, which the app has to supply (as on the TV).
    runtimeOnly(libs.slf4j.nop)
}

// Everything in one jar, started by Bloom.app's launcher with the bundled Java runtime.
val appJar by tasks.registering(Jar::class) {
    group = "build"
    description = "The Mac app in one jar (bloom.jar)."
    archiveFileName.set("bloom.jar")
    isZip64 = true
    manifest { attributes("Main-Class" to "app.blush.desktop.MainKt", "Implementation-Version" to bloomVersion) }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC", "module-info.class", "META-INF/versions/*/module-info.class")
}

// Renders the desktop screens to PNGs (desktop/build/shots), like the TV app's PromptShots, so they can be checked by eye.
val shots by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Renders the Mac app's screens to PNG files."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("app.blush.desktop.ShotsKt")
    val out = layout.buildDirectory.dir("shots")
    args(out.get().asFile.absolutePath)
    systemProperty("bloom.version", bloomVersion)
    systemProperty("java.awt.headless", "true")
}
