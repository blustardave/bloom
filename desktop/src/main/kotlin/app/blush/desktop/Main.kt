package app.blush.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.blush.tv.ui.theme.Backdrops
import app.blush.tv.ui.theme.BlushTheme

/** The version shown in the window: set by Bloom.app's launcher, else read from the jar. */
val bloomVersion: String
    get() = System.getProperty("bloom.version") ?: Main::class.java.`package`?.implementationVersion ?: "dev"

private object Main

fun main() = application {
    val state = rememberWindowState(size = DpSize(1180.dp, 780.dp), position = WindowPosition(Alignment.Center))
    Window(onCloseRequest = ::exitApplication, state = state, title = "Bloom") {
        remember {
            // Paint the window itself in Bloom's background colour. Otherwise macOS fills newly uncovered space with white
            // while the window is being resized, before Bloom has drawn it (the "trailing white border").
            val bg = Backdrops.Graphite.background.toArgb().let { java.awt.Color(it, true) }
            window.background = bg
            window.contentPane.background = bg
            window.rootPane.background = bg
            // On the Mac: draw under a see-through title bar (the traffic lights float over Bloom's own background),
            // like Apple TV and Music. Ignored on other systems.
            window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            window.minimumSize = java.awt.Dimension(900, 600)
        }
        MenuBar {
            Menu("View") {
                CheckboxItem(
                    "Show performance",
                    checked = Perf.shown,
                    shortcut = KeyShortcut(Key.P, meta = true, alt = true),
                    onCheckedChange = { Perf.shown = it },
                )
            }
        }
        Box(Modifier.fillMaxSize().perfMeasured()) {
            BlushTheme {
                WelcomeScreen(bloomVersion)
            }
            PerfOverlay()
        }
    }
}
