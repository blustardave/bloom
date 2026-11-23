package app.blush.tv.ui.player

import android.graphics.Typeface
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import app.blush.tv.data.Store
import app.blush.tv.ui.theme.Manrope

/**
 * One consistent look for every film's subtitles, whatever styling the file itself carries. Chosen in Settings (and the size
 * also from the player's panel). Each list is name → value; the names are what's saved.
 */
object SubtitleLook {
    // The lists themselves are shared with the Mac app (shared module, SubtitleOptions).
    val sizes = SubtitleOptions.sizes
    val fonts = SubtitleOptions.fonts
    val weights = SubtitleOptions.weights
    val colours = SubtitleOptions.colours
    val edges = SubtitleOptions.edges
    val backgrounds = SubtitleOptions.backgrounds
    val positions = SubtitleOptions.positions

    private fun <T> pick(list: List<Pair<String, T>>, name: String, fallback: Int) = SubtitleOptions.pick(list, name, fallback)

    fun fontFamily(name: String): FontFamily = when (name) {
        "Classic" -> FontFamily.SansSerif
        "Serif" -> FontFamily.Serif
        "Condensed" -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
        "Typewriter" -> FontFamily.Monospace
        else -> Manrope
    }

    fun of(store: Store) = SubtitleStyle(
        size = pick(sizes, store.subtitleSize, 2),
        font = fontFamily(store.subtitleFont),
        weight = pick(weights, store.subtitleWeight, 1),
        colour = Color(pick(colours, store.subtitleColour, 0)),
        edge = store.subtitleEdge.takeIf { it in edges } ?: edges[0],
        backgroundAlpha = pick(backgrounds, store.subtitleBackground, 0),
        bottom = pick(positions, store.subtitlePosition, 1),
    )

    /** Picture subtitles are drawn by the player: only their height on screen follows Settings. */
    @OptIn(UnstableApi::class)
    fun applyPicture(view: SubtitleView, store: Store) {
        view.setApplyEmbeddedStyles(true)
        view.setBottomPaddingFraction(pick(positions, store.subtitlePosition, 1))
    }
}
