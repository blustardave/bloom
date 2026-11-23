package app.blush.tv.ui

import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.theme.Text
import app.blush.tv.ui.components.PrimaryButton
import app.blush.tv.ui.components.GentleScrolling
import app.blush.tv.CrashLog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.blush.tv.ui.auth.ProfilesScreen
import app.blush.tv.ui.auth.ServerScreen
import app.blush.tv.ui.auth.SignInScreen
import app.blush.tv.ui.auth.StartupScreen
import app.blush.tv.ui.auth.SplashOverlay
import app.blush.tv.ui.details.DetailsScreen
import app.blush.tv.ui.details.PersonScreen
import app.blush.tv.ui.home.HomeScreen
import app.blush.tv.ui.search.SearchScreen
import app.blush.tv.ui.browse.CategoryScreen
import app.blush.tv.ui.browse.LibrariesScreen
import app.blush.tv.ui.browse.LibraryScreen
import app.blush.tv.ui.browse.WatchlistScreen
import app.blush.tv.ui.settings.SettingsScreen
import app.blush.tv.ui.player.PlayerScreen
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushTheme

@Composable
fun BlushApp(app: AppState, onExit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var crash by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(CrashLog.read(context)) }
    BlushTheme(accent = app.accent, backdrop = app.backdrop) {
        if (crash != null) {
            CrashScreen(crash!!) {
                CrashLog.clear(context)
                crash = null
            }
            return@BlushTheme
        }
        // The player handles back itself (to hide controls first).
        BackHandler(enabled = app.current !is Route.Player) {
            if (!app.back()) onExit()
        }
        val nav = androidx.compose.runtime.remember { NavBar() }
        Box(Modifier.fillMaxSize().background(Blush.colors.background)) {
            androidx.compose.runtime.CompositionLocalProvider(LocalNavBar provides nav) { AnimatedContent(
                targetState = app.current,
                // The new screen fades in over the old one, which stays put until it's covered, so changing screen
                // never dips through the dark background (the black flash on "More info").
                // Lite mode swaps screens at once: two full screens drawn see-through on top of each other is the dearest
                // moment there is for a weak stick.
                transitionSpec = { if (Lite.on) fadeIn(tween(0)) togetherWith fadeOut(tween(0)) else fadeIn(tween(240)) togetherWith fadeOut(tween(1, delayMillis = 240)) },
                contentKey = { route ->
                    // Keep the same player when moving on to the next episode.
                    if (route is Route.Player) "player" else route
                },
                label = "screens",
            ) { route ->
                GentleScrolling { Screen(app, route) }
            } }
            // The navigation bar: one bar above every screen, so it stays put when the screen changes.
            NavBarHost(app, nav, Modifier.align(androidx.compose.ui.Alignment.TopStart))
            if (!app.splashDone) SplashOverlay(app)
            PerfOverlay()
        }
        // The statistics panel is switched off for now (the Settings choice is kept, but not used).
        androidx.compose.runtime.LaunchedEffect(Unit) {
            Perf.overlay = app.repo.store.showStats
            Perf.enabled = Perf.overlay
            Lite.on = app.repo.store.liteMode
        }
    }
}

@Composable
private fun Screen(app: AppState, route: Route) {
    when (route) {
        Route.Startup -> StartupScreen(app)
        Route.Server -> ServerScreen(app)
        Route.Profiles -> ProfilesScreen(app)
        is Route.SignIn -> SignInScreen(app, route)
        Route.Home -> {
            val model = app.home
            if (model != null) WithRail(app, Route.Home, model.railReturn) { HomeScreen(app, model) }
        }
        Route.Search -> WithRail(app, Route.Search) { SearchScreen(app) }
        Route.Libraries -> WithRail(app, Route.Libraries) { LibrariesScreen(app) }
        Route.Watchlist -> WithRail(app, Route.Watchlist) { WatchlistScreen(app) }
        Route.Settings -> WithRail(app, Route.Settings) { SettingsScreen(app) }
        is Route.Library -> WithRail(app, Route.Libraries, selectedLibrary = route.library.id, backOpensRail = true) { LibraryScreen(app, route.library) }
        is Route.Category -> WithRail(app, Route.Home) { CategoryScreen(app, route.key) }
        is Route.Details -> DetailsScreen(app, route.itemId, route.preview)
        is Route.Person -> PersonScreen(app, route.personId)
        is Route.Player -> PlayerScreen(app, route)
        // History and the category pages are still out of the app (see docs/STRIPPED.md).
        else -> Unit
    }
}

@Composable
private fun CrashScreen(log: String, onDismiss: () -> Unit) {
    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxSize().background(Blush.colors.background).padding(horizontal = 48.dp, vertical = 30.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        Text("Bloom crashed last time", style = BlushType.headline)
        Text(
            "Take a photo or screenshot of this and send it over so it can be fixed.",
            style = BlushType.body,
            color = Blush.colors.textMuted,
        )
        PrimaryButton("Continue", null, onClick = onDismiss, modifier = Modifier.focusRequester(focus))
        val lines = log.lines()
        val important = (lines.take(4) + lines.filter { it.contains("app.blush") || it.startsWith("Caused by") }.take(14)).distinct()
        Text(
            important.joinToString("\n"),
            style = BlushType.caption.copy(fontSize = 10.sp, lineHeight = 13.sp),
            color = Blush.colors.textSoft,
        )
    }
}
