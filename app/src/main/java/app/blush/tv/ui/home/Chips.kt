package app.blush.tv.ui.home

import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Column
import app.blush.tv.ui.theme.BlushType
import androidx.compose.ui.unit.sp
import app.blush.tv.data.Repo
import app.blush.tv.ui.CategorySpec
import app.blush.tv.ui.components.FocusSurface
import app.blush.tv.ui.components.ProgressLine
import app.blush.tv.ui.components.RemoteImage
import app.blush.tv.ui.displayTitle
import app.blush.tv.ui.progress
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.Text
import org.jellyfin.sdk.model.api.BaseItemDto

/** The fixed size of a chip. Every chip is the same size, so a chip row slides exactly like a row of tiles. */
internal val ChipWidth = 150.dp
internal val ChipHeight = 56.dp

/** One chip: its words and (when "View all" pages return) the list it opens. */
class Chip(val label: String, val spec: CategorySpec? = null)

/**
 * How a chip's words are set. Each genre has lettering of its own; the typeface is one of the system's families, with
 * its weight and slant already in it.
 */
private class ChipLook(
    val family: String,
    val style: Int = Typeface.NORMAL,
    val caps: Boolean = false,
    val tracking: Float = 0f,
    val size: Float = 16f,
    /** Shown instead of the genre's full name. */
    val text: String? = null,
)

private fun lookFor(label: String): ChipLook = when (label.lowercase()) {
    "action" -> ChipLook("sans-serif-black", Typeface.ITALIC, caps = true, tracking = 1f, size = 15f)
    "adventure" -> ChipLook("serif", Typeface.BOLD, caps = true, tracking = 2.5f, size = 12.5f)
    "animation" -> ChipLook("casual", Typeface.BOLD, size = 20f)
    "comedy" -> ChipLook("cursive", Typeface.BOLD, size = 22f)
    "crime" -> ChipLook("sans-serif-condensed", Typeface.NORMAL, caps = true, tracking = 4f, size = 12.5f)
    "documentary" -> ChipLook("sans-serif-light", Typeface.NORMAL, caps = true, tracking = 3f, size = 10.5f)
    "drama" -> ChipLook("serif", Typeface.NORMAL, size = 20f)
    "family" -> ChipLook("sans-serif-smallcaps", Typeface.BOLD, tracking = 0.5f, size = 18f)
    "fantasy" -> ChipLook("serif", Typeface.BOLD_ITALIC, tracking = 0.5f, size = 19f)
    "history" -> ChipLook("serif-monospace", Typeface.NORMAL, size = 16f)
    "horror" -> ChipLook("sans-serif-thin", Typeface.ITALIC, caps = true, tracking = 4.5f, size = 13f)
    "music", "musical" -> ChipLook("cursive", Typeface.ITALIC, size = 22f)
    "mystery" -> ChipLook("monospace", Typeface.NORMAL, caps = true, tracking = 2.5f, size = 12f)
    "romance" -> ChipLook("casual", Typeface.ITALIC, size = 20f)
    "science fiction", "sci-fi" -> ChipLook("monospace", Typeface.BOLD, caps = true, tracking = 1.5f, size = 12f, text = "Sci-Fi")
    "thriller" -> ChipLook("sans-serif-condensed", Typeface.BOLD_ITALIC, caps = true, tracking = 2f, size = 15f)
    "war" -> ChipLook("serif", Typeface.BOLD, caps = true, tracking = 4f, size = 14f)
    "western" -> ChipLook("serif-monospace", Typeface.BOLD, caps = true, tracking = 2f, size = 13f)
    else -> when ((label.hashCode() and 0x7fffffff) % 3) {
        0 -> ChipLook("sans-serif-medium", Typeface.NORMAL, size = 17f)
        1 -> ChipLook("serif", Typeface.NORMAL, size = 18f)
        else -> ChipLook("sans-serif-light", Typeface.NORMAL, caps = true, tracking = 2.5f, size = 12f)
    }
}

private val ChipShape = RoundedCornerShape(12.dp)
private val ChipRest = listOf(Color(0xFF3A3D48), Color(0xFF22242B))
private val ChipLit = listOf(Color(0xFF4B4E5C), Color(0xFF2E3039))

/**
 * A squared-off grey chip with a soft diagonal gradient and the genre set in its own lettering. Every line is the same height
 * and centred on its glyphs, so the words all sit on one level across the row. Plain on purpose: no pictures, nothing animated.
 */
@Composable
internal fun ChipTile(chip: Chip, onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val look = remember(chip.label) { lookFor(chip.label) }
    val style = remember(chip.label) {
        TextStyle(
            fontFamily = FontFamily(Typeface.create(look.family, look.style)),
            fontWeight = FontWeight.Normal,
            fontSize = look.size.sp,
            letterSpacing = look.tracking.sp,
            textAlign = TextAlign.Center,
            // One line height for everything, centred on the letters, with no font padding: this is what puts every chip's
            // words at the same height whatever the typeface.
            lineHeight = 26.sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    }
    val shown = remember(chip.label) { (look.text ?: chip.label).let { if (look.caps) it.uppercase() else it } }
    FocusSurface(
        onClick = onClick,
        onFocused = onFocused,
        modifier = modifier.width(ChipWidth).height(ChipHeight),
        shape = ChipShape,
        focusedScale = 1f,
        background = Color.Transparent,
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(Brush.linearGradient(if (focused) ChipLit else ChipRest, start = Offset.Zero, end = Offset(size.width, size.height)))
                }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                shown,
                style = style,
                color = if (focused) Color.White else Color(0xFFDCDDE3),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
            )
        }
    }
}

/** The numbered row's poster, larger than a normal one. */
internal val NumPosterWidth = 150.dp
internal val NumPosterHeight = NumPosterWidth * 1.5f
/** The part of the number that sits left of the poster (the rest is laid over the poster's edge). */
private val NumberAreaWidth = 22.dp
/** The small lines of detail under each poster. */
private val NumberedInfoHeight = 18.dp
/** One place in the row: the number's room plus the poster. */
internal val NumberedWidth = NumberAreaWidth + NumPosterWidth

/**
 * One place in the numbered row, set like Netflix's: a big number in the page's own colour with a thin light outline, bottom
 * left, laid over the poster's left edge (so it reads as a number standing in front of the poster); under the poster, the
 * score and a line of detail in small quiet text. The number's left edge is the row's left edge, in line with the heading. The
 * numbers are plain text drawn once; only the poster takes the remote's ring, and nothing grows or plays, so this row is as
 * light as any other. [blockLeft] (the first place) makes Left open the side bar: the poster's edge sits to the right of the
 * banner's buttons, which would otherwise be found "to the left".
 */
@Composable
internal fun NumberedTile(
    repo: Repo,
    item: BaseItemDto,
    rank: Int,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    blockLeft: Boolean = false,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    val size = if (rank >= 10) 78 else 100
    fun numberStyle(draw: androidx.compose.ui.graphics.drawscope.DrawStyle) = TextStyle(
        fontFamily = FontFamily(Typeface.create("sans-serif-black", Typeface.NORMAL)),
        fontSize = size.sp,
        letterSpacing = (-4).sp,
        lineHeight = size.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        drawStyle = draw,
    )
    val fill = remember(rank) { numberStyle(androidx.compose.ui.graphics.drawscope.Fill) }
    val outline = remember(rank) { numberStyle(androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f)) }
    val info = remember(item.id) {
        val score = item.communityRating?.let { String.format(java.util.Locale.US, "%.1f", it) }
        val rest = listOfNotNull(item.productionYear?.toString(), item.genres.orEmpty().take(3).joinToString(", ").ifEmpty { null }).joinToString(" · ")
        score to rest
    }
    Box(Modifier.width(NumberedWidth)) {
        androidx.compose.foundation.layout.Column {
            FocusSurface(
                onClick = onClick,
                onFocused = onFocused,
                onLongPress = onLongPress,
                modifier = modifier
                    .padding(start = NumberAreaWidth)
                    .width(NumPosterWidth)
                    .height(NumPosterHeight)
                    .then(if (blockLeft) Modifier.focusProperties { left = FocusRequester.Cancel } else Modifier),
                focusedScale = 1f,
                background = Blush.colors.surface,
            ) {
                val poster = repo.posterUrl(item)
                if (poster == null) Text(item.displayTitle(), modifier = Modifier.align(Alignment.BottomStart).padding(10.dp), maxLines = 3)
                RemoteImage(poster, Modifier.fillMaxSize(), crossfade = false)
                ProgressLine(item.progress())
            }
            // Score, then year and first genre, under the poster's own left edge.
            // One line: score, year and genres. It may run a little past the poster's right edge (there is nothing beside it down here).
            androidx.compose.foundation.layout.Row(
                Modifier.padding(start = NumberAreaWidth, top = 6.dp).width(NumPosterWidth + 34.dp).height(NumberedInfoHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (info.first != null) Text("\u2605 ${info.first}", style = BlushType.caption, color = Color(0xFFE6E7EC), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Clip)
                if (info.second.isNotEmpty()) Text(
                    (if (info.first != null) "  ·  " else "") + info.second,
                    style = BlushType.caption, color = Blush.colors.textMuted, maxLines = 1, modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        // The number, in front of the poster (above even the ringed one), starting at the row's left edge and standing a little
        // above the poster's bottom.
        val numberModifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = if (rank == 1) (-3).dp else (-1).dp, y = (NumPosterHeight - 57.dp) - (size / 2f).dp)
            .zIndex(2f)
        Text(rank.toString(), style = fill, color = Blush.colors.background, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Visible, modifier = numberModifier)
        Text(rank.toString(), style = outline, color = Color(0xFFC4C6CF), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Visible, modifier = numberModifier)
    }
}
