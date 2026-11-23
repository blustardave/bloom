package app.blush.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Bloom's colours, shared by the TV app and the Mac app. Fonts and text sizes stay with each app for now (they load
// the font file differently).

/** One selectable accent colour. The whole app reads the accent from here, so themes are a single switch. */
@Immutable
data class Accent(val id: String, val name: String, val color: Color, val onColor: Color)

object Accents {
    val Blush = Accent("blush", "Blush", Color(0xFFF4A6B8), Color(0xFF2A121B))
    val Mint = Accent("mint", "Mint", Color(0xFF8FD3C7), Color(0xFF0F2E2A))
    val Apricot = Accent("apricot", "Apricot", Color(0xFFF2C38B), Color(0xFF3A230B))
    val Periwinkle = Accent("periwinkle", "Periwinkle", Color(0xFFA9B8FF), Color(0xFF131A40))
    val Snow = Accent("snow", "Snow", Color(0xFFF5F5F7), Color(0xFF17181F))
    val all = listOf(Blush, Mint, Apricot, Periwinkle, Snow)
    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: Blush
}

/** One selectable background theme: the page colour and the two raised colours drawn on it. */
@Immutable
data class Backdrop(val id: String, val name: String, val background: Color, val surface: Color, val surfaceRaised: Color)

object Backdrops {
    val Graphite = Backdrop("graphite", "Graphite", Color(0xFF161618), Color(0xFF252528), Color(0xFF303034))
    val Midnight = Backdrop("midnight", "Midnight", Color(0xFF0C0D12), Color(0xFF1B1D25), Color(0xFF262833))
    val Black = Backdrop("black", "Black", Color(0xFF000000), Color(0xFF141416), Color(0xFF1F1F22))
    val Navy = Backdrop("navy", "Navy", Color(0xFF0E1322), Color(0xFF1B2236), Color(0xFF262E45))
    val Plum = Backdrop("plum", "Plum", Color(0xFF150F1A), Color(0xFF241B2B), Color(0xFF30253A))
    val Espresso = Backdrop("espresso", "Espresso", Color(0xFF17120F), Color(0xFF261F1A), Color(0xFF322924))
    val Forest = Backdrop("forest", "Forest", Color(0xFF0F1512), Color(0xFF1A231E), Color(0xFF24302A))
    val all = listOf(Graphite, Midnight, Black, Navy, Plum, Espresso, Forest)
    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: Graphite
}

@Immutable
data class BlushColors(
    val background: Color = Backdrops.Graphite.background,
    val surface: Color = Backdrops.Graphite.surface,
    val surfaceRaised: Color = Backdrops.Graphite.surfaceRaised,
    val text: Color = Color(0xFFF5F5F7),
    val textSoft: Color = Color(0xFFD5D7DE),
    val textMuted: Color = Color(0xFFAEB1BB),
    val textDim: Color = Color(0xFF868A96),
    val focus: Color = Color(0xFFF5F5F7),
    val scrim: Color = Backdrops.Graphite.background,
    val accent: Accent = Accents.Blush,
)

val LocalBlushColors = staticCompositionLocalOf { BlushColors() }

object Blush {
    val colors: BlushColors
        @Composable get() = LocalBlushColors.current
}

@Composable
fun BlushTheme(accent: Accent = Accents.Blush, backdrop: Backdrop = Backdrops.Graphite, content: @Composable () -> Unit) {
    val colors = BlushColors(
        background = backdrop.background, surface = backdrop.surface, surfaceRaised = backdrop.surfaceRaised,
        scrim = backdrop.background, accent = accent,
    )
    CompositionLocalProvider(LocalBlushColors provides colors, content = content)
}
