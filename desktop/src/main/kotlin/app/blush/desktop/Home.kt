package app.blush.desktop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blush.tv.data.Repo
import app.blush.tv.ui.CategorySpec
import app.blush.tv.ui.components.Icon
import app.blush.tv.ui.components.Icons
import app.blush.tv.ui.durationText
import app.blush.tv.ui.episodeCode
import app.blush.tv.ui.posterTint
import app.blush.tv.ui.progress
import app.blush.tv.ui.theme.Blush
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import java.time.Year

// Home on the Mac: the TV app's layout (a big banner of featured titles, then rows), driven by the pointer.
// Sizes are the TV's scaled up a step for a laptop screen seen from arm's length.

private val PosterWidth = 150.dp
private val CardWidth = 252.dp
private val TileGap = 14.dp
/** How long a title stays in the banner, as on the TV. */
private const val HeroRotateMs = 9_000L

/** One Home row. [wide] rows (Continue watching) use 16:9 cards; the rest use posters. */
class HomeRowData(val key: String, val title: String, val items: List<BaseItemDto>, val wide: Boolean = false, val more: CategorySpec? = null)

/** Where pictures come from: the server, or nothing at all (screenshots). */
interface Pictures {
    fun backdrop(item: BaseItemDto): String?
    fun logo(item: BaseItemDto): String?
    fun poster(item: BaseItemDto): String?
    fun thumb(item: BaseItemDto): String?

    companion object {
        val None = object : Pictures {
            override fun backdrop(item: BaseItemDto): String? = null
            override fun logo(item: BaseItemDto): String? = null
            override fun poster(item: BaseItemDto): String? = null
            override fun thumb(item: BaseItemDto): String? = null
        }
    }
}

fun Repo.pictures() = object : Pictures {
    override fun backdrop(item: BaseItemDto) = backdropUrl(item, 1920)
    override fun logo(item: BaseItemDto) = logoUrl(item)
    override fun poster(item: BaseItemDto) = posterUrl(item, 360)
    override fun thumb(item: BaseItemDto) = thumbUrl(item, 640)
}

/** Which Settings option a row belongs to ("decade-1990" → "decade"), as on the TV. */
private fun optionKey(rowKey: String) = when {
    rowKey.startsWith("decade-") -> "decade"
    rowKey.startsWith("genre-") -> "genre"
    rowKey.startsWith("mood-") -> "moods"
    else -> rowKey
}

/**
 * Home's data. The rows are the TV app's (same titles, same order, same daily mix chosen from the date), minus the ones
 * that need the actors index (Starring… / Directed by…) and the numbered list, which come later.
 * TODO: share this recipe with the TV's HomeModel.load() instead of keeping two copies.
 */
class HomeState {
    var featured by mutableStateOf<List<BaseItemDto>>(emptyList())
    var rows by mutableStateOf<List<HomeRowData>>(emptyList())
    var loaded by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var heroIndex by mutableIntStateOf(0)
    /** How far through its time the banner's title is (0..1); the dots draw it. */
    var heroProgress by mutableFloatStateOf(0f)
    /** Titles added to / taken off the watchlist from the banner. */
    val listed = mutableStateMapOf<java.util.UUID, Boolean>()

    fun showHero(i: Int) {
        if (featured.isEmpty()) return
        heroIndex = ((i % featured.size) + featured.size) % featured.size
        heroProgress = 0f
    }

    suspend fun load(repo: Repo) = coroutineScope {
        val day = java.time.LocalDate.now().toEpochDay()
        val seed = day * 31 + (repo.userId?.hashCode() ?: 0)
        fun dice(n: Int) = kotlin.random.Random(seed + n)
        launch { featured = runCatching { repo.featured(8) }.getOrDefault(emptyList()) }

        fun spec(title: String, load: suspend (Int, Int) -> Pair<List<BaseItemDto>, Int>) = CategorySpec(title, load)
        val year = Year.now().value
        val decades = listOf(1970, 1980, 1990, 2000, 2010).shuffled(dice(1)).take(1)
        val specs = buildMap {
            put("recent", spec("New on ${repo.store.server?.name ?: "your server"}") { s, n -> repo.browsePage(sortBy = ItemSortBy.DATE_CREATED, descending = true, start = s, limit = n) })
            put("new", spec("New releases") { s, n -> repo.browsePage(sortBy = ItemSortBy.PREMIERE_DATE, descending = true, years = (year - 2..year).toList(), start = s, limit = n) })
            put("top", spec("Critically acclaimed") { s, n -> repo.browsePage(sortBy = ItemSortBy.COMMUNITY_RATING, descending = true, minRating = 7.5, start = s, limit = n) })
            put("series", spec("Binge-worthy series") { s, n -> repo.browsePage(types = listOf(BaseItemKind.SERIES), minRating = 7.0, start = s, limit = n) })
            decades.forEach { d -> put("decade-$d", spec("Back to the ${d.toString().takeLast(2)}s") { s, n -> repo.browsePage(years = (d until d + 10).toList(), start = s, limit = n) }) }
            put("collections", spec("Collections") { s, n -> repo.browsePage(types = listOf(BaseItemKind.BOX_SET), sortBy = ItemSortBy.SORT_NAME, start = s, limit = n) })
            put("watchlist", spec("On your watchlist") { s, n -> repo.browsePage(favouritesOnly = true, sortBy = ItemSortBy.DATE_CREATED, descending = true, start = s, limit = n) })
            put("again", spec("Watch it again") { s, n -> repo.browsePage(playedOnly = true, sortBy = ItemSortBy.DATE_PLAYED, descending = true, start = s, limit = n) })
            put("gems", spec("Hidden gems") { s, n -> repo.browsePage(types = listOf(BaseItemKind.MOVIE), minRating = 7.2, unplayedOnly = true, sortBy = ItemSortBy.RANDOM, years = (1950..year - 5).toList(), start = s, limit = n) })
        }.toMutableMap()
        val allGenres = runCatching { withTimeoutOrNull(4_000) { repo.genres() } }.getOrNull() ?: emptyList()
        val pinned = repo.store.pinnedGenres.filter { it in allGenres }
        val genres = (pinned + (allGenres - pinned.toSet()).shuffled(dice(2))).take(maxOf(repo.store.genreRowCount, pinned.size))
        genres.forEach { g -> specs["genre-$g"] = spec(g) { s, n -> repo.browsePage(genre = g, start = s, limit = n) } }
        fun genreNamed(vararg names: String) = allGenres.firstOrNull { g -> names.any { it.equals(g, ignoreCase = true) } }
        val moods = buildList {
            fun byGenre(key: String, title: String, genre: String?, minRating: Double, vararg clash: String) {
                if (genre == null) return
                val avoid = clash.toSet()
                val pager = app.blush.tv.data.FilteredPager({ s, n -> repo.browsePage(genre = genre, minRating = minRating, sortBy = ItemSortBy.COMMUNITY_RATING, descending = true, start = s, limit = n) }) { item ->
                    item.genres.orEmpty().none { g -> avoid.any { it.equals(g, ignoreCase = true) } }
                }
                add(key to spec(title) { s, n -> pager.load(s, n) })
            }
            byGenre("mood-comedy", "Feel-good comedies", genreNamed("Comedy"), 6.3, "Thriller", "Horror", "Crime", "War", "Mystery", "Documentary", "Western", "History")
            byGenre("mood-thriller", "Edge of your seat", genreNamed("Thriller"), 6.5, "Comedy", "Animation", "Family", "Romance", "Music", "Documentary")
            byGenre("mood-romance", "Date night", genreNamed("Romance"), 6.3, "Horror", "Thriller", "War", "Crime", "Documentary", "Animation")
            byGenre("mood-scifi", "Sci-fi and fantasy", genreNamed("Science Fiction", "Sci-Fi", "Sci-Fi & Fantasy", "Fantasy"), 6.5, "Documentary")
            byGenre("mood-animation", "Animated favourites", genreNamed("Animation"), 6.5, "Horror", "Documentary")
            byGenre("mood-family", "Family time", genreNamed("Family"), 6.3, "Horror", "Thriller", "Crime", "War", "Documentary")
            byGenre("mood-horror", "After dark", genreNamed("Horror"), 6.0, "Comedy", "Animation", "Family", "Romance", "Documentary")
            byGenre("mood-docs", "True stories", genreNamed("Documentary"), 6.5)
            add("mood-classics" to spec("Timeless classics") { s, n -> repo.browsePage(years = (1930..1989).toList(), minRating = 7.4, sortBy = ItemSortBy.COMMUNITY_RATING, descending = true, start = s, limit = n) })
        }
        val chosenMoods = moods.map { it.first }.shuffled(dice(3)).take(5)
        moods.filter { it.first in chosenMoods }.forEach { (k, v) -> specs[k] = v }

        val pool = buildList {
            add("because"); add("top"); add("series")
            decades.forEach { add("decade-$it") }
            add("gems"); add("again"); add("collections")
            addAll(genres.map { "genre-$it" })
            addAll(chosenMoods)
        }
        val mixed = listOf("resume", "recent", "watchlist", "new") + pool
        val custom = repo.store.homeRowOrder
        val order = if (custom.isEmpty()) mixed else mixed.sortedBy { k -> custom.indexOf(optionKey(k)).let { if (it < 0) Int.MAX_VALUE else it } }
        val hidden = repo.store.hiddenHomeRows
        val limit = repo.store.homeRowSize

        // Rows appear top-down: one is shown once every row above it has arrived (or turned out empty).
        val done = mutableMapOf<String, HomeRowData?>()
        fun publish() {
            rows = buildList { for (k in order) { if (k !in done) break; done[k]?.let { add(it) } } }
        }
        val gate = Semaphore(6)
        for (key in order) launch {
            val row: HomeRowData? = if (optionKey(key) in hidden) null else gate.withPermit {
                runCatching {
                    when (key) {
                        "resume" -> repo.resume().takeIf { it.isNotEmpty() }?.let { HomeRowData("resume", "Continue watching", it, wide = true) }
                        "because" -> repo.lastWatched()?.let { seedItem ->
                            repo.similar(seedItem.id).takeIf { it.size >= 4 }?.let { HomeRowData("because", "Because you watched ${seedItem.name}", it) }
                        }
                        else -> specs[key]?.let { s -> s.load(0, limit).first.takeIf { it.size >= 3 }?.let { HomeRowData(key, s.title, it, more = s) } }
                    }
                }.getOrNull()
            }
            done[key] = row
            publish()
        }
        // Anything still loading after 15 s is skipped over rather than holding up the rows under it.
        val timeout = launch { delay(15_000); order.forEach { if (it !in done) done[it] = null }; publish() }
        launch { while (done.size < order.size) delay(100); timeout.cancel() }
    }.also { loaded = true; if (rows.isEmpty() && featured.isEmpty()) error = "Couldn't load Home. Check the server is on." }
}

// ---------- Screen ----------

/** Shown when something isn't built yet on the Mac (title pages, playback). */
const val ComingSoon = "Title pages and playback come in the next Mac builds."

@Composable
fun HomeScreen(
    home: HomeState,
    pictures: Pictures,
    onOpen: (BaseItemDto) -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onToggleList: (BaseItemDto, Boolean) -> Unit,
) {
    val list = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize().background(Blush.colors.background)) {
        val bannerHeight = (maxHeight * 0.74f).coerceIn(440.dp, 720.dp)
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item(key = "banner") {
                Banner(home, pictures, bannerHeight, onOpen, onPlay, onToggleList)
            }
            items(home.rows, key = { it.key }) { row ->
                HomeRowView(row, pictures, onOpen, Modifier.padding(top = if (row === home.rows.first()) 0.dp else 26.dp))
            }
            if (home.error != null) item { Note(home.error, Modifier.padding(40.dp), error = true) }
        }
    }
}

// ---------- Banner ----------

@Composable
private fun Banner(
    home: HomeState,
    pictures: Pictures,
    height: Dp,
    onOpen: (BaseItemDto) -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onToggleList: (BaseItemDto, Boolean) -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val hero = home.featured.getOrNull(home.heroIndex)
    // The banner moves on by itself, but holds still while the pointer is over it (as the TV holds while the remote is on it).
    LaunchedEffect(home.featured.size) {
        while (home.featured.size > 1) {
            delay(50)
            if (hovered) continue
            home.heroProgress += 50f / HeroRotateMs
            if (home.heroProgress >= 1f) home.showHero(home.heroIndex + 1)
        }
    }
    val page = Blush.colors.background
    Box(Modifier.fillMaxWidth().height(height).hoverable(hover)) {
        Crossfade(targetState = hero?.let { pictures.backdrop(it) }, animationSpec = tween(550), label = "backdrop") { url ->
            if (url != null) AsyncImage(url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize())
        }
        // The TV banner's two gradients: dark from the left behind the text, and melting into the page at the bottom.
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(Brush.horizontalGradient(0f to page.copy(alpha = 0.95f), 0.42f to page.copy(alpha = 0.55f), 0.75f to Color.Transparent))
                drawRect(Brush.verticalGradient(0f to page.copy(alpha = 0.55f), 0.16f to Color.Transparent, 0.55f to Color.Transparent, 0.82f to page.copy(alpha = 0.85f), 1f to page))
            },
        )
        if (hero != null) Column(
            Modifier.align(Alignment.BottomStart).padding(start = PageSide, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Crossfade(targetState = hero, animationSpec = tween(450), label = "heroText") { item -> BannerInfo(item, pictures) }
            val listed = home.listed[hero.id] ?: (hero.userData?.isFavorite == true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayPill(if (hero.progress() != null) "Resume" else "Play", hero.progress()) { onPlay(hero) }
                RoundButton(Icons.Info, "More info") { onOpen(hero) }
                RoundButton(
                    if (listed) Icons.Check else Icons.Plus,
                    if (listed) "Remove from watchlist" else "Add to watchlist",
                    tint = if (listed) Blush.colors.accent.color else Blush.colors.text,
                    background = if (listed) Blush.colors.accent.color.copy(alpha = 0.22f) else null,
                ) { onToggleList(hero, !listed) }
            }
        }
        if (home.featured.size > 1) Dots(home, Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp))
    }
}

/** Logo (or name), one line of details and two lines of description: the TV banner's text block, a step larger. */
@Composable
fun BannerInfo(item: BaseItemDto, pictures: Pictures) {
    Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(440.dp).height(150.dp), contentAlignment = Alignment.BottomStart) {
            val logo = pictures.logo(item)
            if (logo != null) AsyncImage(
                logo, contentDescription = item.name, contentScale = ContentScale.Fit, alignment = Alignment.BottomStart,
                modifier = Modifier.widthIn(max = 420.dp).heightIn(max = 140.dp).fillMaxSize(),
            ) else Text(item.name.orEmpty(), style = DesktopType.headline.copy(fontSize = 38.sp, fontWeight = FontWeight.W800, lineHeight = 42.sp), maxLines = 2)
        }
        Text(
            buildList {
                item.productionYear?.let { add(it.toString()) }
                when (item.type) {
                    BaseItemKind.SERIES -> item.childCount?.let { add(if (it == 1) "1 Season" else "$it Seasons") }
                    else -> durationText(item.runTimeTicks)?.let { add(it) }
                }
                item.genres?.take(2)?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
            }.joinToString(" • "),
            style = DesktopType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.W500),
            color = Color(0xFF9B9EA8),
            maxLines = 1,
        )
        Text(item.overview.orEmpty(), Modifier.height(40.dp), style = DesktopType.body.copy(fontSize = 13.sp, lineHeight = 19.sp), color = Blush.colors.textSoft, maxLines = 2)
    }
}

/** The banner's Play: see-through white at rest, solid white under the pointer (the TV's focused look). */
@Composable
private fun PlayPill(label: String, progress: Float?, onClick: () -> Unit) {
    val h = rememberHover()
    val ink = Color(0xFF14151B)
    val lit = h.lit
    val bar = Blush.colors.accent.color
    Box(
        Modifier.width(124.dp).height(40.dp).clip(RoundedCornerShape(50))
            .background(if (h.pressed) Color(0xFFDDDDE2) else if (lit) Color.White else Color.White.copy(alpha = 0.16f))
            .mouseTarget(h, onClick = onClick)
            .drawBehind {
                if (progress != null && progress > 0f) {
                    val inset = 20.dp.toPx(); val y = size.height - 5.dp.toPx()
                    drawLine(Color.Gray.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(inset, y), androidx.compose.ui.geometry.Offset(size.width - inset, y), 2.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(bar, androidx.compose.ui.geometry.Offset(inset, y), androidx.compose.ui.geometry.Offset(inset + (size.width - 2 * inset) * progress.coerceIn(0f, 1f), y), 2.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Play, null, if (lit) ink else Blush.colors.text, size = 14.dp)
            Text(label, style = DesktopType.label.copy(fontSize = 13.sp), color = if (lit) ink else Blush.colors.text)
        }
    }
}

/** A round icon button (More info, watchlist): white 10% at rest, white under the pointer. */
@Composable
fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Blush.colors.text, background: Color? = null, size: Dp = 40.dp, onClick: () -> Unit) {
    val h = rememberHover()
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (h.lit) Color.White else background ?: Color.White.copy(alpha = 0.10f))
            .mouseTarget(h, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, if (h.lit) Color(0xFF14151B) else tint, size = size * 0.45f) }
}

/** The banner's position dots: the active one is a pill that fills over its time; click any dot to jump to it. */
@Composable
private fun Dots(home: HomeState, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        home.featured.indices.forEach { i ->
            val active = i == home.heroIndex
            val width by animateDpAsState(if (active) 24.dp else 6.dp, tween(300), label = "dot")
            val h = rememberHover()
            Box(Modifier.mouseTarget(h) { home.showHero(i) }.padding(horizontal = 4.dp, vertical = 8.dp)) {
                Box(
                    Modifier.width(width).height(6.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = if (h.lit) 0.7f else 0.35f))
                        .drawWithContent {
                            drawContent()
                            if (active) drawRect(Color.White, size = Size(size.width * home.heroProgress, size.height))
                        },
                )
            }
        }
    }
}

// ---------- Rows ----------

@Composable
private fun HomeRowView(row: HomeRowData, pictures: Pictures, onOpen: (BaseItemDto) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Column(modifier.hoverable(hover), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(row.title, Modifier.padding(start = PageSide), style = DesktopType.title.copy(fontSize = 16.sp, fontWeight = FontWeight.W600))
        Box {
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = PageSide, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(TileGap),
            ) {
                items(row.items, key = { it.id }) { item ->
                    if (row.wide) WideCard(item, pictures) { onOpen(item) } else PosterTile(item, pictures) { onOpen(item) }
                }
            }
            // Round arrows at the ends while the pointer is over the row, for mice that can't scroll sideways.
            // (A trackpad swipe or Shift + scroll wheel moves the row too.)
            val tileHeight = if (row.wide) CardWidth * 9f / 16f else PosterWidth * 1.5f
            Box(Modifier.matchRowArrows(tileHeight)) {
                if (hovered && state.canScrollBackward) RowArrow(Icons.Back, Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
                    scope.launch { state.animateScrollBy(-state.layoutInfo.viewportSize.width * 0.8f, tween(450)) }
                }
                if (hovered && state.canScrollForward) RowArrow(Icons.ChevronRight, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                    scope.launch { state.animateScrollBy(state.layoutInfo.viewportSize.width * 0.8f, tween(450)) }
                }
            }
        }
    }
}

/** The arrows sit level with the pictures (not the captions under them). */
private fun Modifier.matchRowArrows(tileHeight: Dp) = fillMaxWidth().padding(top = 6.dp).height(tileHeight)

@Composable
private fun RowArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val h = rememberHover()
    Box(
        modifier.size(40.dp).clip(CircleShape)
            .background(if (h.lit) Color.White else Color(0xCC1C1C20))
            .mouseTarget(h, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, if (h.lit) Color(0xFF14151B) else Color.White, size = 18.dp) }
}

@Composable
private fun PosterTile(item: BaseItemDto, pictures: Pictures, onClick: () -> Unit) {
    Column(Modifier.width(PosterWidth)) {
        HoverTile(onClick, Modifier.fillMaxWidth().aspectRatio(2f / 3f).probe("tile-${item.name}"), corner = 8.dp, background = item.posterTint() ?: tintFor(item.name.orEmpty()).copy(alpha = 0.5f)) {
            val url = pictures.poster(item)
            if (url != null) AsyncImage(url, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(item.name.orEmpty(), Modifier.align(Alignment.Center).padding(10.dp), style = DesktopType.label, color = Blush.colors.textSoft)
            ProgressBar(item.progress())
        }
    }
}

/** Continue watching: the 16:9 still with a progress line, and what it is underneath. */
@Composable
private fun WideCard(item: BaseItemDto, pictures: Pictures, onClick: () -> Unit) {
    Column(Modifier.width(CardWidth), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        HoverTile(onClick, Modifier.fillMaxWidth().aspectRatio(16f / 9f).probe("card-${item.name}"), corner = 10.dp, background = tintFor(item.seriesName ?: item.name.orEmpty()).copy(alpha = 0.5f)) {
            val url = pictures.thumb(item)
            if (url != null) AsyncImage(url, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            ProgressBar(item.progress())
        }
        val line = if (item.type == BaseItemKind.EPISODE) listOfNotNull(item.seriesName, item.episodeCode()).joinToString(" · ") else item.name.orEmpty()
        Text(line, style = DesktopType.caption.copy(fontSize = 12.sp), color = Blush.colors.textMuted, maxLines = 1)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ProgressBar(progress: Float?) {
    if (progress == null || progress <= 0f) return
    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.45f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Blush.colors.accent.color))
    }
}

// ---------- Route ----------

@Composable
fun HomeRoute(session: Session) {
    val home = session.home
    LaunchedEffect(home) { if (!home.loaded && home.rows.isEmpty()) home.load(session.repo) }
    val scope = rememberCoroutineScope()
    HomeScreen(
        home,
        session.repo.pictures(),
        onOpen = { session.toast(ComingSoon) },
        onPlay = { session.toast(ComingSoon) },
        onToggleList = { item, want ->
            home.listed[item.id] = want
            scope.launch {
                val ok = runCatching { session.repo.setFavorite(item.id, want) }.getOrDefault(false)
                if (!ok) home.listed[item.id] = !want
            }
        },
    )
}
