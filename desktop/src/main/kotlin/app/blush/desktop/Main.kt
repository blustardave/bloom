package app.blush.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.blush.tv.ui.theme.Backdrops
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushTheme
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.crossfade

/** The version shown in the window: set by Bloom.app's launcher, else read from the jar. */
val bloomVersion: String
    get() = System.getProperty("bloom.version") ?: Main::class.java.`package`?.implementationVersion ?: "dev"

private object Main

fun main() {
    // Pictures (posters, profile pictures) are kept on disk between launches.
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .diskCache { DiskCache.Builder().directory(Places.cache.resolve("images")).maxSizeBytes(500L * 1024 * 1024).build() }
            .crossfade(true)
            .build()
    }
    application {
        val scope = rememberCoroutineScope()
        val session = remember { Session(scope).also { it.start() } }
        // A quiet check at launch: say so when a newer Mac app is on the release page.
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(4_000)
            val latest = runCatching { Updater.latestVersion() }.getOrNull()
            if (latest != null && Updater.isNewer(latest, bloomVersion)) session.toast("Bloom $latest is ready: Help → Check for Updates")
        }
        val state = rememberWindowState(size = DpSize(1180.dp, 780.dp), position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = "Bloom",
            // Esc goes back (⌘[ is in the Go menu).
            onPreviewKeyEvent = { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape && session.canGoBack) { session.back(); true } else false
            },
        ) {
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
                Menu("Window") {
                    // Bloom has one window, so closing it quits, as in most single-window Mac apps (⌘Q also quits).
                    Item("Close Window", shortcut = KeyShortcut(Key.W, meta = true), onClick = ::exitApplication)
                    Item("Minimise", shortcut = KeyShortcut(Key.M, meta = true)) { state.isMinimized = true }
                }
                Menu("Go") {
                    Item("Back", shortcut = KeyShortcut(Key.LeftBracket, meta = true), enabled = session.canGoBack, onClick = session::back)
                    Item("Home", shortcut = KeyShortcut(Key.H, meta = true, shift = true), enabled = session.repo.currentUser != null) {
                        session.reset(Screen.Home)
                    }
                    Item("Search", shortcut = KeyShortcut(Key.F, meta = true), enabled = session.repo.currentUser != null) {
                        if (session.screen != Screen.Search) session.go(Screen.Search)
                    }
                    Item("Libraries", shortcut = KeyShortcut(Key.L, meta = true, shift = true), enabled = session.repo.currentUser != null) {
                        session.reset(Screen.Libraries)
                    }
                }
                Menu("Help") {
                    Item("Check for Updates…") {
                        session.toast("Checking for updates…")
                        session.launch {
                            when (val r = Updater.update { session.toast(it) }) {
                                Updater.Result.UpToDate -> session.toast("Bloom $bloomVersion is the newest version.")
                                is Updater.Result.Failed -> session.toast(r.why)
                                is Updater.Result.Installing -> {
                                    session.toast("Restarting into Bloom ${r.version}…")
                                    kotlinx.coroutines.delay(800)
                                    exitApplication()
                                }
                            }
                        }
                    }
                }
                Menu("View") {
                    CheckboxItem(
                        "Show performance",
                        checked = Perf.shown,
                        shortcut = KeyShortcut(Key.P, meta = true, alt = true),
                        onCheckedChange = { Perf.shown = it },
                    )
                }
            }
            CompositionLocalProvider(LocalDragArea provides { m -> WindowDraggableArea(m) }) {
                Box(
                    Modifier.fillMaxSize().perfMeasured()
                        // The mouse's own Back button.
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val e = awaitPointerEvent(PointerEventPass.Initial)
                                    if (e.type == PointerEventType.Press && e.buttons.isBackPressed && session.canGoBack) session.back()
                                }
                            }
                        },
                ) {
                    BlushTheme {
                        // A strip along the top to drag the window by, under everything else.
                        LocalDragArea.current(Modifier.fillMaxWidth().height(28.dp))
                        Screens(session)
                    }
                    BlushTheme { Toast(session.toastText) }
                    PerfOverlay()
                }
            }
        }
    }
}

@Composable
private fun Screens(session: Session) {
    when (val s = session.screen) {
        Screen.Starting -> Box(Modifier.fillMaxSize().background(Blush.colors.background))
        Screen.Server -> ServerRoute(session)
        Screen.Profiles -> ProfilesRoute(session)
        is Screen.Password -> key(s) { PasswordRoute(session, s.profile) }
        Screen.Home -> Shell(session, "Home", underBar = true) { HomeRoute(session) }
        Screen.Libraries -> Shell(session, "Libraries") { LibrariesRoute(session) }
        is Screen.Library -> Shell(session, if (session.store.railPins(session.store.server?.id, session.repo.currentUser?.userId).any { it.libraryId == s.library.id.toString() }) "pin-${s.library.id}" else "Libraries") { key(s.library.id) { LibraryRoute(session, s.library) } }
        is Screen.Details -> Shell(session, "", underBar = true) { key(s.id) { DetailsRoute(session, s.id, s.preview) } }
        Screen.Search -> Shell(session, "Search") { SearchRoute(session) }
        Screen.Watchlist -> Shell(session, "Watchlist") { WatchlistRoute(session) }
    }
}
