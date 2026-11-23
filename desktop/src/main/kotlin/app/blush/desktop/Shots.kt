@file:OptIn(ExperimentalComposeUiApi::class)

package app.blush.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.blush.tv.data.SavedServer
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Screenshot harness for the Mac app (run by `./gradlew :desktop:shots` on CI, published to the `shots` branch as
 * desktop-*.png). Renders at 2x like a Retina screen from made-up data (no server), and moves a real mouse pointer over
 * controls so hover states are checked too. Names and titles here are invented.
 */
private const val W = 1180
private const val H = 780
private const val Scale = 2f

private class Shot(private val out: File, content: @Composable () -> Unit) {
    private val scene = ImageComposeScene((W * Scale).toInt(), (H * Scale).toInt(), Density(Scale)) {
        BlushTheme { content() }
    }
    private var clock = 0L

    private fun settle() = repeat(12) { clock += 50_000_000L; scene.render(clock) }

    fun hover(probe: String) = apply { settle(); scene.sendPointerEvent(PointerEventType.Move, centre(probe)) }

    fun click(probe: String) = apply {
        settle()
        val at = centre(probe)
        scene.sendPointerEvent(PointerEventType.Move, at)
        scene.sendPointerEvent(PointerEventType.Press, at, button = PointerButton.Primary)
        scene.sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary)
    }

    fun save(name: String) {
        settle()
        val data = scene.render(clock).encodeToData(EncodedImageFormat.PNG) ?: error("could not encode $name")
        File(out, name).writeBytes(data.bytes)
        println("wrote ${File(out, name)}")
        scene.close()
    }

    private fun centre(probe: String): Offset = PointerProbe.bounds[probe]?.center ?: error("no control named $probe")
}

private val profiles = listOf(
    Profile("Alex", "1", null, null, true),
    Profile("Sam", "2", null, null, true),
    Profile("Kids", "3", null, null, false),
)

private val libraries = listOf(
    LibraryCard("Films", null, 812), LibraryCard("TV Shows", null, 146), LibraryCard("Documentaries", null, 64),
    LibraryCard("Collections", null, 38), LibraryCard("Home Videos", null, 1),
)

private val posters = listOf(
    "The Long Harbour" to 2019, "Paper Lanterns" to 2004, "Northern Static" to 2021, "A Quiet Engine" to 1998,
    "Glasshouse" to 2016, "Saltwater Kings" to 2011, "The Ninth Orchard" to 2023, "Midnight Ferry" to 1987,
    "Copper Sky" to 2008, "Lowlands" to 2014, "Small Hours" to 2020, "The Cartographer" to 2001,
    "Tin Garden" to 1993, "Harbour Lights II" to 2022, "Wild Acre" to 2017, "Second Summer" to 2010,
).map { (n, y) -> PosterCard(n, y, null, null) }

/** The top bar over a page, as after sign-in (without a session). */
@Composable
private fun ShellLook(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Blush.colors.background)) {
        Box(Modifier.fillMaxSize().padding(top = 56.dp)) { content() }
        TopBar("Alex", null, {}, {}, {}, {})
    }
}

fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/shots").apply { mkdirs() }

    Shot(out) {
        ServerScreen(
            "192.168.1.20:8096", {}, {}, busy = false, error = null,
            found = listOf(SavedServer("a", "Living Room", "http://192.168.1.20:8096"), SavedServer("b", "Studio", "http://192.168.1.31:8096")),
            searching = false, onPick = {},
        )
    }.hover("Living Room").save("desktop-server.png")

    Shot(out) { ProfilesScreen("Living Room", profiles, null, null, {}, {}, {}) }.hover("Sam").save("desktop-profiles.png")

    Shot(out) {
        PasswordScreen(profiles[0], "Alex", {}, "secret", {}, busy = false, error = "That name or password wasn't right.", {}, {})
    }.save("desktop-password.png")

    Shot(out) { ShellLook { LibrariesScreen(libraries, null) {} } }.hover("Films").save("desktop-libraries.png")

    Shot(out) { ShellLook { LibraryScreen("Films", 812, posters, null, {}, {}) } }.save("desktop-library.png")

    Shot(out) { ShellLook { LibrariesScreen(libraries, null) {} } }.click("profile-menu").save("desktop-menu.png")

    // How long a hover takes to draw on the CI machine (software drawing, so slower than a Mac's graphics chip).
    run {
        val scene = ImageComposeScene((W * Scale).toInt(), (H * Scale).toInt(), Density(Scale)) {
            BlushTheme { ProfilesScreen("Living Room", profiles, null, null, {}, {}, {}) }
        }
        var clock = 0L
        repeat(30) { clock += 16_000_000L; scene.render(clock) }
        val a = PointerProbe.bounds["Alex"]!!.center
        val b = PointerProbe.bounds["Sam"]!!.center
        val times = (1..40).map { i ->
            scene.sendPointerEvent(PointerEventType.Move, if (i % 2 == 0) a else b)
            val t = System.nanoTime(); clock += 16_000_000L; scene.render(clock); (System.nanoTime() - t) / 1e6
        }.sorted()
        println("Hover frame on CI: median %.1f ms, worst %.1f ms".format(times[times.size / 2], times.last()))
        scene.close()
    }
}
