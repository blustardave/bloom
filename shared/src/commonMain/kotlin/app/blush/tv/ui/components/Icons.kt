package app.blush.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Line icons drawn on a 24×24 grid, matching the mockups. */
object Icons {
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"
    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0 1 $r,$r v${h - 2 * r}a$r,$r 0 0 1 ${-r},$r h${-(w - 2 * r)}a$r,$r 0 0 1 ${-r},${-r} v${-(h - 2 * r)}a$r,$r 0 0 1 $r,${-r}z"

    private fun line(name: String, vararg paths: String, width: Float = 2f) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach {
            addPath(
                pathData = addPathNodes(it),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

    private fun solid(name: String, vararg paths: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.White)) }
    }.build()

    /** An icon made of stroked lines and filled shapes; [evenOdd] lets later shapes cut holes in earlier ones. */
    private fun shapes(name: String, strokes: List<String>, fills: List<String>, evenOdd: Boolean = false, width: Float = 2f) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            if (fills.isNotEmpty()) {
                addPath(
                    pathData = addPathNodes(fills.joinToString(" ")),
                    fill = SolidColor(Color.White),
                    pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                )
            }
            strokes.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    fill = null,
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Search = line("search", circle(11f, 11f, 7f), "M20 20l-3.5-3.5")
    val Home = line("home", "M3 10.5L12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")
    val Libraries = line(
        "libraries",
        rect(4f, 4f, 6.5f, 6.5f, 1.5f), rect(13.5f, 4f, 6.5f, 6.5f, 1.5f),
        rect(4f, 13.5f, 6.5f, 6.5f, 1.5f), rect(13.5f, 13.5f, 6.5f, 6.5f, 1.5f),
    )
    val Watchlist = line("watchlist", "M6 3h12v18l-6-4-6 4z")
    val History = line("history", "M3.5 12a8.5 8.5 0 1 0 2.5-6", "M3.5 4.5V9H8", "M12 7.5V12l3 2")
    val HistoryFilled = shapes("historyFilled", listOf("M3.5 12a8.5 8.5 0 1 0 2.5-6", "M3.5 4.5V9H8", "M12 7.5V12l3 2"), emptyList(), width = 2.8f)
    val Settings = line("settings", "M4 7h10M18 7h2M4 17h2M10 17h10", circle(16f, 7f, 2f), circle(8f, 17f, 2f))
    // Filled versions, for the selected item in the side bar.
    val SearchFilled = shapes("searchFilled", listOf("M20 20l-3.5-3.5", circle(11f, 11f, 7f)), listOf(circle(11f, 11f, 7f)), width = 2.4f)
    val HomeFilled = shapes("homeFilled", listOf("M3 10.5L12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"), listOf("M3 10.5L12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"))
    val LibrariesFilled = shapes(
        "librariesFilled",
        emptyList(),
        listOf(rect(4f, 4f, 6.5f, 6.5f, 1.5f), rect(13.5f, 4f, 6.5f, 6.5f, 1.5f), rect(4f, 13.5f, 6.5f, 6.5f, 1.5f), rect(13.5f, 13.5f, 6.5f, 6.5f, 1.5f)),
    )
    val WatchlistFilled = shapes("watchlistFilled", listOf("M6 3h12v18l-6-4-6 4z"), listOf("M6 3h12v18l-6-4-6 4z"))
    val SettingsFilled = shapes("settingsFilled", listOf("M4 7h10M18 7h2M4 17h2M10 17h10"), listOf(circle(16f, 7f, 2.8f), circle(8f, 17f, 2.8f)), width = 2.2f)

    // Icons a pinned library can use in the side bar: (outline, filled).
    private val filmBody = rect(3f, 5f, 18f, 14f, 2f)
    private val filmHoles = listOf(rect(4.6f, 7f, 2f, 2f, 0.4f), rect(4.6f, 11f, 2f, 2f, 0.4f), rect(4.6f, 15f, 2f, 2f, 0.4f), rect(17.4f, 7f, 2f, 2f, 0.4f), rect(17.4f, 11f, 2f, 2f, 0.4f), rect(17.4f, 15f, 2f, 2f, 0.4f))
    private val starPath = "M12 3.5l2.6 5.3 5.8.8-4.2 4.1 1 5.8L12 16.8l-5.2 2.7 1-5.8-4.2-4.1 5.8-.8z"
    private val sparklePath = "M12 3c.6 4.6 2.4 6.4 7 7-4.6.6-6.4 2.4-7 7-.6-4.6-2.4-6.4-7-7 4.6-.6 6.4-2.4 7-7z"
    private val folderPath = "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
    private val boltPath = "M13 2.5L5 13.5h6l-1 8 8-11h-6z"
    val pinIcons: Map<String, Pair<ImageVector, ImageVector>> by lazy {
        linkedMapOf(
        "film" to (line("film", filmBody, "M7 5v14M17 5v14") to shapes("filmF", emptyList(), listOf(filmBody) + filmHoles, evenOdd = true)),
        "tv" to (line("tv", rect(3f, 5f, 18f, 12f, 2f), "M9 21h6M12 17v4") to shapes("tvF", listOf("M9 21h6M12 17v4"), listOf(rect(3f, 5f, 18f, 12f, 2f)))),
        "star" to (line("star", starPath) to shapes("starF", listOf(starPath), listOf(starPath))),
        "heart" to (Heart to HeartFilled),
        "sparkle" to (line("sparkle", sparklePath) to shapes("sparkleF", listOf(sparklePath), listOf(sparklePath))),
        "music" to (line("music", "M10 17V5l10-2v12", circle(7.5f, 17f, 2.5f), circle(17.5f, 15f, 2.5f)) to shapes("musicF", listOf("M10 17V5l10-2v12"), listOf(circle(7.5f, 17f, 2.8f), circle(17.5f, 15f, 2.8f)))),
        "folder" to (line("folder", folderPath) to shapes("folderF", listOf(folderPath), listOf(folderPath))),
        "bolt" to (line("bolt", boltPath) to shapes("boltF", listOf(boltPath), listOf(boltPath))),
        )
    }

    val Play = solid("play", "M7 4.5v15l13-7.5z")
    val Pause = solid("pause", "M7 5h3.5a.5.5 0 0 1 .5.5v13a.5.5 0 0 1-.5.5H7a.5.5 0 0 1-.5-.5v-13A.5.5 0 0 1 7 5zM14 5h3.5a.5.5 0 0 1 .5.5v13a.5.5 0 0 1-.5.5H14a.5.5 0 0 1-.5-.5v-13a.5.5 0 0 1 .5-.5z")
    val Info = line("info", circle(12f, 12f, 9f), "M12 11v5M12 8h.01")
    // Drawn so the shape sits exactly in the middle of its 24×24 square (top 5, bottom 19), so it looks centred in a round button.
    val Heart = line("heart", "M12 19s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 9c0 5.6-7 10-7 10z")
    val HeartFilled = solid("heartFilled", "M12 19s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 9c0 5.6-7 10-7 10z")
    val Eye = line("eye", "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z", circle(12f, 12f, 3f))
    val Check = line("check", "M5 12l5 5 9-10", width = 2.4f)
    val Restart = line("restart", "M4 12a8 8 0 1 0 2.3-5.7M4 4v4h4")
    val Rewind = line("rewind", "M11 7l-5 5 5 5M18 7l-5 5 5 5", width = 2.2f)
    val Forward = line("forward", "M13 7l5 5-5 5M6 7l5 5-5 5", width = 2.2f)
    val ClosedCaptions = line("cc", rect(3f, 5f, 18f, 14f, 2.5f), "M10.6 10.3a2.2 2.2 0 1 0 0 3.4", "M16.6 10.3a2.2 2.2 0 1 0 0 3.4")
    /** Subtitles switched on: the same box, filled in, with the lines cut out of it. */
    val SubtitlesOn = ImageVector.Builder("subtitlesOn", 24.dp, 24.dp, 24f, 24f).apply {
        // Exactly the outline icon's shape (its box plus half a stroke, and its three round-ended lines), colours swapped.
        addPath(
            pathData = addPathNodes(rect(2f, 4f, 20f, 16f, 3f) + rect(6f, 12f, 6f, 2f, 1f) + rect(12f, 12f, 6f, 2f, 1f) + rect(6f, 15f, 9f, 2f, 1f)),
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.EvenOdd,
        )
    }.build()
    val Volume = line("volume", "M4 9.5v5h3.5l4.5 4V5.5l-4.5 4z", "M15.5 9a4 4 0 0 1 0 6", "M18 6.5a7.5 7.5 0 0 1 0 11")
    /** Volume away from 100%: the speaker filled in (waves kept as lines). */
    val VolumeOn = shapes("volumeOn", listOf("M15.5 9a4 4 0 0 1 0 6", "M18 6.5a7.5 7.5 0 0 1 0 11", "M4 9.5v5h3.5l4.5 4V5.5l-4.5 4z"), listOf("M4 9.5v5h3.5l4.5 4V5.5l-4.5 4z"))
    val VolumeOff = shapes("volumeOff", listOf("M4 9.5v5h3.5l4.5 4V5.5l-4.5 4z", "M16 9.5l5 5M21 9.5l-5 5"), listOf("M4 9.5v5h3.5l4.5 4V5.5l-4.5 4z"))
    val More = solid("more", circle(12f, 5f, 1.8f), circle(12f, 12f, 1.8f), circle(12f, 19f, 1.8f))
    /** Circular arrows for the 10-second buttons; the "10" is drawn inside separately. */
    val Replay = line("replay", "M4.6 9.5A8 8 0 1 1 4 12", "M4.2 4.8v4.9h4.9", width = 1.6f)
    val Skip = line("skip", "M19.4 9.5A8 8 0 1 0 20 12", "M19.8 4.8v4.9h-4.9", width = 1.6f)
    val Subtitles = line("subtitles", rect(3f, 5f, 18f, 14f, 2f), "M7 13h4M13 13h4M7 16h7")
    val Next = line("next", "M6 5l10 7-10 7zM18 5v14")
    val SkipAhead = solid("skipAhead", "M4 6.5l7.5 5.5-7.5 5.5zM12.5 6.5l7.5 5.5-7.5 5.5z")
    val PosterGrid = line("posterGrid", rect(3.5f, 4f, 4.5f, 7f, 1f), rect(9.75f, 4f, 4.5f, 7f, 1f), rect(16f, 4f, 4.5f, 7f, 1f), rect(3.5f, 13f, 4.5f, 7f, 1f), rect(9.75f, 13f, 4.5f, 7f, 1f), rect(16f, 13f, 4.5f, 7f, 1f), width = 1.6f)
    val WideGrid = line("wideGrid", rect(3f, 5f, 8f, 5.5f, 1f), rect(13f, 5f, 8f, 5.5f, 1f), rect(3f, 13.5f, 8f, 5.5f, 1f), rect(13f, 13.5f, 8f, 5.5f, 1f), width = 1.6f)
    val Rows = line("rows", "M4 6h16M4 12h16M4 18h16", width = 1.8f)
    val ChevronDown = line("chevronDown", "M6 9l6 6 6-6")
    val ChevronRight = line("chevronRight", "M9 6l6 6-6 6")
    val Trailer = line("trailer", rect(4f, 4f, 16f, 16f, 2f), "M8 4v16M16 4v16M4 9h4M4 15h4M16 9h4M16 15h4")
    val SortDesc = line("sortDesc", "M4 6h16M4 12h11M4 18h6")
    val SortAsc = line("sortAsc", "M4 6h6M4 12h11M4 18h16")
    val Filter = line("filter", "M4 6h16M7 12h10M10 18h4")
    val SortAz = line("sortAz", "M1.5 17l3.3-9.5L8.1 17M2.8 13.8h3.9", "M10.6 6.5V17M10.6 14.2c0-1.8 1-3.1 2.6-3.1 1.5 0 2.4 1.3 2.4 3.1S14.7 17.3 13.2 17.3c-1.6 0-2.6-1.3-2.6-3.1", "M22.4 12.4c-.5-.8-1.2-1.3-2.2-1.3-1.5 0-2.5 1.3-2.5 3.1s1 3.1 2.5 3.1c1 0 1.7-.5 2.2-1.3", width = 1.8f)
    val Calendar = line("calendar", rect(4f, 5f, 16f, 15f, 2f), "M4 10h16M9 3v4M15 3v4")
    val Star = line("star", "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.5 2.9 1-6.1L3.2 9.5l6.1-.9z")
    val Clock = line("clock", circle(12f, 12f, 9f), "M12 7v5l3 2")
    val PlusCircle = line("plusCircle", circle(12f, 12f, 9f), "M12 8v8M8 12h8")
    val Chart = line("chart", "M5 20V11M12 20V4M19 20v-6")
    val Close = line("close", "M6 6l12 12M18 6L6 18", width = 2.2f)
    val Backspace = line("backspace", "M21 6H8l-5 6 5 6h13zM11 10l4 4M15 10l-4 4")
    val Space = line("space", "M4 10v4h16v-4")
    val Mic = line("mic", rect(9f, 3f, 6f, 11f, 3f), "M5.5,11a6.5,6.5 0 0 0 13,0", "M12,17.5v3.5")
    val Dice = line("dice", rect(4f, 4f, 16f, 16f, 3.5f), "M8.6 8.6h.01M15.4 8.6h.01M12 12h.01M8.6 15.4h.01M15.4 15.4h.01", width = 2.2f)
    val Shuffle = line("shuffle", "M16 3h5v5", "M4 20L21 3", "M21 16v5h-5", "M15 15l6 6", "M4 4l5 5")
    val Back = line("back", "M15 5l-7 7 7 7", width = 2.2f)
    val Plus = line("plus", "M12 5v14M5 12h14")
    val User = line("user", circle(12f, 8f, 4f), "M4 21a8 8 0 0 1 16 0")
    val Server = line("server", rect(3f, 4f, 18f, 7f, 2f), rect(3f, 13f, 18f, 7f, 2f), "M7 7.5h.01M7 16.5h.01")
}

@Composable
fun Icon(vector: ImageVector, contentDescription: String?, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Image(
        painter = rememberVectorPainter(vector),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}
