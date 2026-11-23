package app.blush.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blush.tv.data.Repo
import app.blush.tv.ui.ageRating
import app.blush.tv.ui.durationText
import app.blush.tv.ui.scoreText
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.theme.Text
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.math.roundToInt

/** Shared by the home banner and the details page, so moving from one to the other keeps everything in place. */
val BannerInfoWidth = 350.dp
val BannerLogoHeight = 120.dp
val BannerDescriptionHeight = 32.dp
/** The description under the title, on Home and on the details page. */
val BannerDescriptionStyle get() = BlushType.body.copy(fontSize = 11.sp, lineHeight = 15.sp)
val BannerInfoGap = 9.dp
/** Gap between the description and the buttons below it. */
val BannerButtonsGap = 14.dp
/** How far the bottom of the banner buttons sits from the top of the screen. */
val BannerButtonsBottom = 404.dp

/**
 * Logo (or name), details line and a short description, sized like Disney+'s banner. Every part has a fixed
 * height, so one title fades into the next without anything sliding about. [idle] (0–1) fades the description
 * out and lowers the logo and details into its place, for when the remote hasn't been touched for a while.
 * [extra] adds things at the end of the details line (e.g. 4K / HDR badges on the details page).
 */
@Composable
fun BannerTitleInfo(
    repo: Repo,
    item: BaseItemDto,
    idle: () -> Float = { 0f },
    extra: @Composable RowScope.() -> Unit = {},
    /** The details page's fuller line: year, length, three genres, score, then the age rating and [extra]. */
    detailed: Boolean = false,
    /** Replaces the plain two-line description (the details page makes it open out). */
    description: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    val drop = with(density) { (BannerDescriptionHeight + BannerInfoGap).roundToPx() }
    Column(Modifier.width(BannerInfoWidth), verticalArrangement = Arrangement.spacedBy(BannerInfoGap)) {
        Column(
            Modifier.offset { IntOffset(0, (drop * idle()).roundToInt()) },
            verticalArrangement = Arrangement.spacedBy(BannerInfoGap),
        ) {
            Box(Modifier.width(BannerInfoWidth).height(BannerLogoHeight), contentAlignment = Alignment.BottomStart) {
                val logo = repo.logoUrl(item)
                if (logo != null) {
                    // A larger area with roomier limits, so wide and tall logos come out a similar, decent size.
                    LogoImage(logo, area = 22000f, maxWidth = 340.dp, maxHeight = BannerLogoHeight)
                } else {
                    Text(item.name.orEmpty(), style = BlushType.display, maxLines = 2)
                }
            }
            // One line, as on Home. The details page adds the score, and puts the age rating after the genres, before the picture badges.
            Row(
                (if (detailed) Modifier.wrapContentWidth(Alignment.Start, unbounded = true) else Modifier).height(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    buildList {
                        item.productionYear?.let { add(it.toString()) }
                        when (item.type) {
                            BaseItemKind.SERIES -> item.childCount?.let { add(if (it == 1) "1 Season" else "$it Seasons") }
                            else -> durationText(item.runTimeTicks)?.let { add(it) }
                        }
                        item.genres?.take(2)?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
                        if (detailed) item.scoreText()?.let { add(it) }
                    }.joinToString(" • "),
                    style = BlushType.caption.copy(fontWeight = FontWeight.W500),
                    color = androidx.compose.ui.graphics.Color(0xFF9B9EA8),
                    maxLines = 1,
                )
                if (detailed) item.ageRating()?.let { Badge(it) }
                extra()
            }
        }
        if (description != null) description() else
        Text(
            item.overview.orEmpty(),
            style = BannerDescriptionStyle,
            color = Blush.colors.textSoft,
            maxLines = 2,
            modifier = Modifier.height(BannerDescriptionHeight).graphicsLayer { alpha = 1f - idle() },
        )
    }
}
