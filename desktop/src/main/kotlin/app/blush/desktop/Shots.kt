@file:OptIn(ExperimentalComposeUiApi::class)

package app.blush.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import app.blush.tv.ui.theme.BlushTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Screenshot harness for the Mac app (run by `./gradlew :desktop:shots` on CI, published to the `shots` branch as
 * desktop-*.png). Renders at 2x like a Retina screen, and drives a real mouse pointer over the controls, so hover and
 * click states are checked too.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/shots").apply { mkdirs() }
    val scale = 2f
    val w = 1180; val h = 780
    val scene = ImageComposeScene((w * scale).toInt(), (h * scale).toInt(), Density(scale)) {
        BlushTheme { WelcomeScreen(System.getProperty("bloom.version") ?: "dev") }
    }
    var clock = 0L
    fun frame(): org.jetbrains.skia.Image {
        // Several frames 50 ms apart so fades and hover animations settle.
        repeat(12) { clock += 50_000_000L; scene.render(clock) }
        return scene.render(clock)
    }
    fun save(name: String) {
        val data = frame().encodeToData(EncodedImageFormat.PNG) ?: error("could not encode $name")
        File(out, name).writeBytes(data.bytes)
        println("wrote ${File(out, name)}")
    }
    fun centre(probe: String): Offset = PointerProbe.bounds[probe]?.center ?: error("no control named $probe")

    save("desktop-welcome.png")

    // Pointer resting on "Outline" (hover state), nothing clicked.
    scene.sendPointerEvent(PointerEventType.Move, centre("Outline"))
    save("desktop-hover.png")

    // Click "Thick outline" and "Dark", then leave the pointer over "Light".
    for (name in listOf("Thick outline", "Dark")) {
        val at = centre(name)
        scene.sendPointerEvent(PointerEventType.Move, at)
        scene.sendPointerEvent(PointerEventType.Press, at, button = PointerButton.Primary)
        scene.sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary)
    }
    scene.sendPointerEvent(PointerEventType.Move, centre("Light"))
    save("desktop-clicked.png")
    scene.close()
}
