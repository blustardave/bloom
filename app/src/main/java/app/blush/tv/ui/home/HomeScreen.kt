package app.blush.tv.ui.home

import app.blush.tv.data.SurpriseOptions
import app.blush.tv.data.logged
import app.blush.tv.ui.components.PopupPanel
import app.blush.tv.ui.components.PopupRow
import app.blush.tv.ui.components.rememberFirstFocus
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.gestures.animateScrollBy
import app.blush.tv.ui.components.prepareLogo
import app.blush.tv.ui.components.FocusDoesNotScroll
import app.blush.tv.ui.components.glideSpec
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import app.blush.tv.data.FilteredPager
import app.blush.tv.data.HomeCache
import app.blush.tv.data.SavedHome
import app.blush.tv.data.SavedRow
import app.blush.tv.data.Store
import app.blush.tv.ui.components.MenuAction
import app.blush.tv.ui.components.TileMenu
import app.blush.tv.ui.posterTint
import kotlinx.coroutines.joinAll
import app.blush.tv.data.Repo
import app.blush.tv.ui.AppState
import app.blush.tv.ui.CategorySpec
import app.blush.tv.ui.ContentStart
import app.blush.tv.ui.PageTop
import app.blush.tv.ui.Lite
import app.blush.tv.ui.LocalNavBar
import app.blush.tv.ui.Perf
import app.blush.tv.ui.RailWidth
import app.blush.tv.ui.Route
import app.blush.tv.ui.RailReturn
import app.blush.tv.ui.components.ConfirmDialog
import app.blush.tv.ui.components.FocusMemory
import app.blush.tv.ui.components.PivotScroll
import app.blush.tv.ui.components.PivotScrolling
import app.blush.tv.ui.components.FocusSurface
import app.blush.tv.ui.components.ProgressLine
import app.blush.tv.ui.components.RemoteImage
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import app.blush.tv.ui.components.SecondaryButton
import app.blush.tv.ui.components.Icon
import app.blush.tv.ui.components.Icons
import app.blush.tv.ui.components.TitleCard
import app.blush.tv.ui.components.ViewAllCard
import app.blush.tv.ui.components.restorableFocus
import app.blush.tv.ui.displayTitle
import app.blush.tv.ui.episodeCode
import app.blush.tv.ui.progress
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.theme.Text
import app.blush.tv.ui.timeLeft
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import java.time.Year

private val PosterWidth = 116.dp
private val CardWidth = 186.dp

/** A tile's key: its row and title. */
private fun tileKey(row: String, id: java.util.UUID) = "$row-$id"

/** The kinds of row Home can show, which Settings can switch off: option key to name. */
val HomeRowOptions = listOf(
    "resume" to "Continue watching",
    "recent" to "New on your server",
    "watchlist" to "On your watchlist",
    "new" to "New releases",
    "because" to "Because you watched…",
    "top" to "Critically acclaimed",
    "starring" to "Starring… (well-known faces)",
    "directed" to "Directed by…",
    "series" to "Binge-worthy series",
    "numbered" to "Numbered top list",
    "foryou" to "More of a genre you like",
    "decade" to "Back to the…s",
    "gems" to "Hidden gems",
    "again" to "Watch it again",
    "studio" to "From… (big studios)",
    "collections" to "Collections",
    "moods" to "Mood rows (comedy, thrillers, romance…)",
    "surprise" to "Surprise me",
)

/** The kinds of row a household can put in its own order (Settings → Home → Row order). */
val HomeRowOrderable = HomeRowOptions.filterNot { it.first == "surprise" }
    .flatMap { if (it.first == "foryou") listOf(it, "genre" to "Genre rows") else listOf(it) }

/** How a poster behaves under the remote (Settings → Home). */
private const val TileNow = 0       // opens the moment the remote reaches it
private const val TilePosters = 2   // never opens

/** Everything in Settings that changes which rows Home has: a saved Home built under other settings is not used. */
private fun homeSignature(store: Store) =
    listOf(store.homeRowSize, store.genreRowCount, store.pinnedGenres, store.hiddenHomeRows.sorted(), store.homeRowOrder).joinToString("|")

/** Which Settings option a row belongs to ("decade-1990" → "decade", "starring2" → "starring"…). */
private fun optionKey(rowKey: String) = when {
    rowKey.startsWith("decade-") -> "decade"
    rowKey.startsWith("starring") -> "starring"
    rowKey.startsWith("directed") -> "directed"
    rowKey.startsWith("chips-") -> "chips"
    rowKey.startsWith("genre-") -> "genre"
    rowKey.startsWith("mood-") -> "moods"
    else -> rowKey
}

/** A titled row of tiles on the home screen. */
/** Whether the "What are you in the mood for?" chip row is shown (off for now). */
private const val MoodChips = false
/** Whether the genre chip row is shown (off for now). */
private const val GenreChips = false

class HomeRow(
    val key: String,
    val title: String,
    items: List<BaseItemDto>,
    val playOnClick: Boolean = false,
    more: CategorySpec? = null,
    /** A row of grey chips (genres, moods) instead of titles. */
    val chips: List<Chip>? = null,
    /** A row with a big number by each poster (1, 2, 3…). */
    val numbered: Boolean = false,
) {
    /** Changeable so a tile can be dropped without rebuilding the row (which would send you back to its start). */
    var items by mutableStateOf(items)

    /** The row's "View all" list. Changeable because a row shown from last time's saved Home only gets it once the server has answered. */
    var more by mutableStateOf(more)

    val state = LazyListState()
}
/** Home screen data, kept alive while you browse so returning is instant. */
class HomeModel(private val repo: Repo, private val categories: MutableMap<String, CategorySpec>) {
    var rows by mutableStateOf<List<HomeRow>>(emptyList())
    var loaded by mutableStateOf(false)
    // Loading belongs to the model, not the screen: opening a title while Home is still filling in must not throw the
    // load away (it used to be cancelled, and Home was rebuilt from nothing on the way back).
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private var loading: kotlinx.coroutines.Job? = null
    /** Starts the load unless it is done or already under way. True when this call started it. */
    fun ensureLoading(): Boolean {
        if (loaded || loading?.isActive == true) return false
        loading = scope.launch { load() }
        return true
    }
    /** Waits for a load that is still under way. */
    suspend fun awaitLoading() { loading?.takeIf { it.isActive }?.join() }
    /** This Home is being replaced (rows changed in Settings, or another profile): stop anything still loading. */
    fun close() { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    /** True once enough pictures are in hand to fill the screen; the rows are not built (or shown) before that. */
    var shown by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    // When the page prefetches the next row it builds 7 of its tiles ahead of time, not the default 2, so a row scrolling in for the
    // first time doesn't build the rest of its tiles in the frame it appears (the first-scroll stutter).
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    val listState = LazyListState(prefetchStrategy = androidx.compose.foundation.lazy.LazyListPrefetchStrategy(nestedPrefetchItemCount = 7))
    val focus = FocusMemory()
    val railReturn = RailReturn()
    /** The last arrow press (the background picture warming waits while the remote is in use). */
    var lastMove = 0L
    /** Whether that press was a key being held down, and when the key went down. */
    var held = false
    var pressAt = 0L
    /** The tile whose menu is open (OK held on it), with its row. */
    var menu by mutableStateOf<Pair<HomeRow, BaseItemDto>?>(null)
    /** Picture addresses already warmed in the background. */
    val warmed = HashSet<String>()
    /** The Up/Down move in progress. */
    var moving: kotlinx.coroutines.Job? = null

    // ----- The banner (featured films) -----
    var featured by mutableStateOf<List<BaseItemDto>>(emptyList())
    /** True once the featured query has come back (with titles or without). */
    var featuredDone by mutableStateOf(false)
    var heroIndex by androidx.compose.runtime.mutableIntStateOf(0)
    var lastHeroChange by androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis())
    var pagerFocused by mutableStateOf(false)
    var heroFocused by mutableStateOf(true)
    /** Set once the first remote button is pressed (until then the start-up focus on Play is kept in place). */
    var userMoved = false
    /** The banner's Play is looking up a series' next episode. */
    var heroBusy = false
    /** Banner titles added to / removed from the watchlist from the banner's heart (the banner's own copy of the title doesn't change). */
    val listed = androidx.compose.runtime.mutableStateMapOf<java.util.UUID, Boolean>()
    /** The titles behind [listed], so a title just added from the banner can lead the watchlist row (the server can't sort by date added to it). */
    val listedItems = mutableMapOf<java.util.UUID, BaseItemDto>()

    /** The tile preview player (built once the screen is up). */
    var preview: PreviewController? = null
    /** What the banner is showing: a title only becomes this once its artwork and logo are loaded. */
    var shownHero by mutableStateOf<BaseItemDto?>(null)
    /** The rows are not built until the banner has shown (or given up), so the page fills in banner, then rows. */
    var rowsOpen by mutableStateOf(false)
    val heroIsDefault: Boolean get() = featured.isNotEmpty()
    fun showHero(index: Int) {
        if (featured.isEmpty()) return
        heroIndex = ((index % featured.size) + featured.size) % featured.size
        lastHeroChange = System.currentTimeMillis()
        heroProgress = 0f
    }
    /** How far through its time the banner's title is (0..1). It only advances while the banner is left alone, and the dots draw it. */
    var heroProgress by androidx.compose.runtime.mutableFloatStateOf(0f)

    /** Which direction key was pressed last (a tile uses it to pick how it opens and closes). */
    var lastKey: Key? = null
    /** Set when a tile is opened: the tile keeps its size while the page behind the fade is on its way out. */
    var leaving = false
    /** The thumbnail address of the tile that last lost the remote along a row, so the next one can fade up over it. */
    var prevThumb: String? = null
    /** Rows that have already faded in once (so only the first screenful fades at launch, never again). */
    val revealed = mutableSetOf<String>()
    fun noteMove(key: Key? = null) { lastMove = android.os.SystemClock.uptimeMillis(); if (key != null) lastKey = key }

    /** Set when you come back to Home from another tab: start again at the top. */
    var goToTop by mutableStateOf(false)

    /**
     * The one thing that moves the page up and down. It only does so for a moment after Up or Down (or a jump to a
     * different row), so moving sideways along a row can never nudge the page.
     */

    private fun spec(title: String, load: suspend (Int, Int) -> Pair<List<BaseItemDto>, Int>) = CategorySpec(title, load)

    /** A category the server can only partly filter: [keep] drops the rest here (see [FilteredPager]). */
    private fun filteredSpec(title: String, keep: (BaseItemDto) -> Boolean, fetch: suspend (Int, Int) -> Pair<List<BaseItemDto>, Int>): CategorySpec {
        val pager = FilteredPager(fetch, keep)
        return CategorySpec(title) { s, n -> pager.load(s, n) }
    }

    private fun shorterThan(minutes: Int): (BaseItemDto) -> Boolean = { (it.runTimeTicks ?: Long.MAX_VALUE) <= minutes * 600_000_000L }

    /** Winds every row back to its start, moving the remembered tile to the first one in its row so Back still lands somewhere visible. */
    fun forgetRowPlaces() {
        val remembered = focus.key
        rows.forEach { it.state.requestScrollToItem(0) }
        if (remembered != null) {
            val row = rows.firstOrNull { r -> r.items.any { tileKey(r.key, it.id) == remembered } }
            val first = row?.items?.firstOrNull()
            if (row != null && first != null) focus.remember(tileKey(row.key, first.id))
        }
    }

    suspend fun load() {
        // A cancelled load (you left Home before it finished) is not a failure and must not count as loaded,
        // otherwise the missing rows would never be fetched. runCatching would swallow the cancellation.
        val outcome = logged("HomeScreen.load") {
            coroutineScope {
                // Home is the same all day: everything picked at random (which decade, which genres, the order of the rows,
                // whose films) is picked from the date, so it comes out the same until tomorrow.
                val day = java.time.LocalDate.now().toEpochDay()
                val seed = day * 31 + (repo.userId?.hashCode() ?: 0)
                fun dice(n: Int) = kotlin.random.Random(seed + n)
                // Last time's finished Home, if it was built under the same Settings. Shown at once (below); and if it is from
                // today, its rows are used as they are instead of being asked for again.
                val signature = homeSignature(repo.store)
                val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { HomeCache.read(repo) }
                    ?.takeIf { it.settings == signature && it.rows.isNotEmpty() }
                val today = saved?.takeIf { it.day == day }
                val kept = today?.rows.orEmpty().associateBy { it.key + "|" + it.title }
                val resumeJob = async { logged("HomeScreen.load") { repo.resume() }.getOrDefault(emptyList()) }
                // 0: last time's Home is about to be shown; 1: it is on screen; 2: there is none, rows appear as they load.
                var mode = if (saved != null) 0 else 2
                var afterSaved: () -> Unit = {}
                if (saved != null) launch {
                    // Continue watching is the one row that must not be out of date, so it is asked for first (briefly).
                    val fresh = withTimeoutOrNull(1_200) { resumeJob.await() }
                    if (rows.isEmpty()) {
                        val off = repo.store.hiddenHomeRows
                        rows = saved.rows.mapNotNull { r ->
                            val items = if (r.key == "resume" && fresh != null) fresh else r.items
                            if (items.isEmpty() || optionKey(r.key) in off) null else HomeRow(r.key, r.title, items, playOnClick = r.playOnClick, numbered = r.numbered)
                        }
                    }
                    mode = if (rows.isNotEmpty()) 1 else 2
                    afterSaved()
                }
                // The banner's titles come first of all.
                launch {
                    featured = today?.featured?.takeIf { it.isNotEmpty() } ?: logged("HomeScreen.load") { repo.featured(8) }.getOrDefault(emptyList())
                    lastHeroChange = System.currentTimeMillis()
                    featuredDone = true
                }
                val limit = repo.store.homeRowSize
                // Rows that can change during the day are always asked for; the rest come from today's saved Home when it has them.
                val live = setOf("recent", "new", "watchlist", "again", "foryou", "numbered")
                suspend fun firstPage(key: String, spec: CategorySpec, n: Int, gate: Semaphore? = null): List<BaseItemDto> {
                    if (key !in live) kept[key + "|" + spec.title]?.let { return it.items }
                    return if (gate == null) logged("HomeScreen.firstPage") { spec.load(0, n).first }.getOrDefault(emptyList())
                    else gate.withPermit { logged("HomeScreen.firstPage") { spec.load(0, n).first }.getOrDefault(emptyList()) }
                }
                val year = Year.now().value
                val decades = listOf(1970, 1980, 1990, 2000, 2010).shuffled(dice(1)).take(1)

                // Each category is described once and used for both the row and its "View all" page.
                val specs = buildList {
                    add("recent" to spec("New on ${repo.store.server?.name ?: "your server"}") { s, n -> repo.browsePage(sortBy = ItemSortBy.DATE_CREATED, descending = true, start = s, limit = n) })
                    add("new" to spec("New releases") { s, n -> repo.browsePage(sortBy = ItemSortBy.PREMIERE_DATE, descending = true, years = (year - 2..year).toList(), start = s, limit = n) })
                    add("top" to spec("Critically acclaimed") { s, n -> repo.browsePage(sortBy = ItemSortBy.COMMUNITY_RATING, descending = true, minRating = 7.5, start = s, limit = n) })
                    add("series" to spec("Binge-worthy series") { s, n -> repo.browsePage(types = listOf(BaseItemKind.SERIES), minRating = 7.0, start = s, limit = n) })
                    decades.forEach { d ->
                        add("decade-$d" to spec("Back to the ${d.toString().takeLast(2)}s") { s, n -> repo.browsePage(years = (d until d + 10).toList(), start = s, limit = n) })
                    }
                    add("collections" to spec("Collections") { s, n -> repo.browsePage(types = listOf(BaseItemKind.BOX_SET), sortBy = ItemSortBy.SORT_NAME, start = s, limit = n) })
                    add("watchlist" to spec("On your watchlist") { s, n -> repo.browsePage(favouritesOnly = true, sortBy = ItemSortBy.DATE_CREATED, descending = true, start = s, limit = n) })
                    add("again" to spec("Watch it again") { s, n -> repo.browsePage(playedOnly = true, sortBy = ItemSortBy.DATE_PLAYED, descending = true, start = s, limit = n) })
                    add("gems" to spec("Hidden gems") { s, n -> repo.browsePage(types = listOf(BaseItemKind.MOVIE), minRating = 7.2, unplayedOnly = true, sortBy = ItemSortBy.RANDOM, years = (1950..year - 5).toList(), start = s, limit = n) })
                }
                // Genre rows: the ones pinned in Settings first, then a random mix up to the chosen number.
                val allGenres = logged("HomeScreen.firstPage") { withTimeoutOrNull(4_000) { repo.genres() } }.getOrNull() ?: emptyList()
                val pinned = repo.store.pinnedGenres.filter { it in allGenres }
                val genres = (pinned + (allGenres - pinned.toSet()).shuffled(dice(2))).take(maxOf(repo.store.genreRowCount, pinned.size))
                val hidden = repo.store.hiddenHomeRows
                val genreSpecs = genres.map { g -> "genre-$g" to spec(g) { s, n -> repo.browsePage(genre = g, start = s, limit = n) } }
                // Mood rows: a handful of genres worked into themed rows (only for genres the library actually has).
                fun genreNamed(vararg names: String) = allGenres.firstOrNull { g -> names.any { it.equals(g, ignoreCase = true) } }
                val moodSpecs = buildList {
                    // A title can carry several genres (Parasite is Comedy, Thriller and Drama), so a mood also turns away the genres that clash with it.
                    fun byGenre(key: String, title: String, genre: String?, minRating: Double, vararg clash: String) {
                        if (genre == null) return
                        val avoid = clash.toSet()
                        val keep = { item: BaseItemDto -> item.genres.orEmpty().none { g -> avoid.any { it.equals(g, ignoreCase = true) } } }
                        add(key to filteredSpec(title, keep) { s, n -> repo.browsePage(genre = genre, minRating = minRating, sortBy = ItemSortBy.COMMUNITY_RATING, descending = true, start = s, limit = n) })
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
                val moodByKey = moodSpecs.toMap()
                val chosenMoods = moodSpecs.map { it.first }.shuffled(dice(3)).take(5)

                // Chip rows are built from what is already known (no requests), so they arrive with the page.
                val chipRows = HashMap<String, HomeRow>()
                // (The mood / runtime chips are switched off for now: MoodChips.)
                if (MoodChips)
                chipRows["chips-mood"] = HomeRow("chips-mood", "What are you in the mood for?", emptyList(), chips = buildList {
                    add(Chip("Under 90 min", filteredSpec("Under 90 minutes", shorterThan(90)) { s, n -> repo.browsePage(types = listOf(BaseItemKind.MOVIE), start = s, limit = n) }))
                    moodByKey["mood-comedy"]?.let { add(Chip("Feel-good", it)) }
                    moodByKey["mood-thriller"]?.let { add(Chip("Edge of your seat", it)) }
                    add(Chip("Epic watches", spec("Epic watches") { s, n -> repo.browsePage(types = listOf(BaseItemKind.MOVIE), sortBy = ItemSortBy.RUNTIME, descending = true, start = s, limit = n) }))
                    moodByKey["mood-romance"]?.let { add(Chip("Date night", it)) }
                    moodByKey["mood-classics"]?.let { add(Chip("Timeless classics", it)) }
                    moodByKey["mood-family"]?.let { add(Chip("Family time", it)) }
                    moodByKey["mood-horror"]?.let { add(Chip("After dark", it)) }
                    moodByKey["mood-scifi"]?.let { add(Chip("Mind-bending", it)) }
                    add(Chip("Hidden gems", spec("Hidden gems") { s, n -> repo.browsePage(types = listOf(BaseItemKind.MOVIE), minRating = 7.2, unplayedOnly = true, sortBy = ItemSortBy.RANDOM, years = (1950..year - 5).toList(), start = s, limit = n) }))
                    moodByKey["mood-docs"]?.let { add(Chip("True stories", it)) }
                })

                val lastJob = async { logged("HomeScreen.byGenre") { repo.lastWatched() }.getOrNull() }
                val becauseJob = async {
                    logged("HomeScreen.byGenre") {
                        val seed = lastJob.await() ?: return@logged null
                        val similar = repo.similar(seed.id)
                        if (similar.size >= 4) seed to similar else null
                    }.getOrNull()
                }
                val loaded = mutableMapOf<String, Pair<CategorySpec, List<BaseItemDto>>>()
                var resume: List<BaseItemDto>? = null
                var because: Pair<BaseItemDto, List<BaseItemDto>>? = null

                fun row(key: String): HomeRow? {
                    val (spec, items) = loaded[key] ?: return null
                    if (items.size < 3) return null
                    categories[key] = spec
                    return HomeRow(key, spec.title, items, more = spec.takeIf { items.size >= limit })
                }

                // The rows always come in the same order (the household can change it in Settings); only which genres, moods and decades appear varies by day.
                val genreKeys = genreSpecs.map { it.first }
                val pool = buildList {
                    add("because"); add("top"); add("starring"); add("starring2"); add("directed"); add("directed2"); add("series"); add("foryou")
                    decades.forEach { add("decade-$it") }
                    add("gems"); add("again"); add("studio"); add("collections")
                    addAll(genreKeys)
                    addAll(chosenMoods)
                }
                // The chip rows and the numbered list sit at fixed places, so the page keeps a rhythm: tiles, chips, tiles, a list, chips.
                val rest = pool.toMutableList().also { if (MoodChips) it.add(minOf(4, it.size), "chips-mood") }
                // The numbered list goes in a little way down, and never next to a row that shows much the same titles.
                val similar = setOf("top", "gems", "series")
                rest.add(minOf(5, rest.size), "numbered")
                // A similar neighbour is moved to the end of the page instead.
                repeat(2) {
                    val at = rest.indexOf("numbered")
                    if (at > 0 && rest[at - 1] in similar) rest.add(rest.removeAt(at - 1))
                    val now = rest.indexOf("numbered")
                    if (now < rest.size - 1 && rest[now + 1] in similar) rest.add(rest.removeAt(now + 1))
                }
                val mixed = listOfNotNull("resume", "recent", "chips-genre".takeIf { GenreChips }, "watchlist", "new") + rest
                // A household's own order (Settings) replaces the mix: rows are put in the order of their kinds.
                val custom = repo.store.homeRowOrder
                val order = if (custom.isEmpty()) mixed else mixed.sortedBy { k -> custom.indexOf(optionKey(k)).let { if (it < 0) Int.MAX_VALUE else it } }
                // Rows still loading. The list only grows downwards: a row isn't shown until every row above it
                // has arrived (or turned out to be empty), so nothing gets slotted in above what's on screen.
                // Anything still loading after a few seconds is skipped over rather than holding up the rest.
                val pending = order.toMutableSet()
                val deadline = System.currentTimeMillis() + 3_000
                fun built(key: String): HomeRow? = when (key) {
                    "resume" -> resume?.takeIf { it.isNotEmpty() }?.let { HomeRow("resume", "Continue watching", it, playOnClick = true) }
                    "because" -> because?.let { (seed, list) -> HomeRow("because", "Because you watched ${seed.name}", list) }
                    "chips-genre", "chips-mood" -> chipRows[key]
                    "numbered" -> row(key)?.let { HomeRow(it.key, it.title, it.items, numbered = true) }
                    else -> row(key)
                }
                // Rebuilds the list in its fixed order from whatever has arrived so far.
                // The newest complete list, which is what gets saved for next time.
                var latest: List<HomeRow> = emptyList()
                fun holdsRemote(r: HomeRow) = focus.key?.let { it.startsWith(r.key + "-") || it == "more-" + r.key } == true
                /** Brings a row that is on screen up to date from its newly built twin, without replacing it (so its place is kept). */
                fun adopt(cur: HomeRow, f: HomeRow): HomeRow {
                    cur.more = f.more
                    // Never under the remote: the tile it is on could vanish.
                    if (!holdsRemote(cur) && cur.items.map { it.id } != f.items.map { it.id }) cur.items = f.items
                    return cur
                }
                fun publish() {
                    if (mode == 0) return
                    val late = System.currentTimeMillis() > deadline
                    if (mode == 1) {
                        // Last time's Home is on screen. It is brought up to date in one go once everything has arrived, rather
                        // than row by row under the remote.
                        if (pending.isNotEmpty() && System.currentTimeMillis() < deadline + 9_000) return
                        val fresh = buildList { for (key in order) { if (key in pending) continue; built(key)?.let { add(it) } } }.filter { optionKey(it.key) !in hidden }
                        latest = fresh
                        if (fresh.isEmpty()) { rows = fresh; return }
                        val cur = rows.associateBy { it.key }
                        val atTop = !userMoved || (heroFocused && listState.firstVisibleItemIndex == 0)
                        if (today != null || atTop) {
                            // Same day (the order is the same anyway), or nobody has gone down the page yet: the new order is taken.
                            val out = fresh.map { f -> cur[f.key]?.let { c -> if (c.title == f.title) adopt(c, f) else if (holdsRemote(c)) c else null } ?: f }.toMutableList()
                            // A row that has gone is kept while the remote is on it.
                            rows.forEachIndexed { i, r -> if (holdsRemote(r) && out.none { it.key == r.key }) out.add(minOf(i, out.size), r) }
                            if (featured.isEmpty() && !userMoved) focus.key = null
                            rows = out
                        } else {
                            // A new day's mix arrived while someone is already browsing yesterday's: the page stays as it is
                            // (matching rows are brought up to date), and the new mix is what the next launch shows.
                            rows.forEach { c -> fresh.firstOrNull { it.key == c.key && it.title == c.title }?.let { adopt(c, it) } }
                        }
                        return
                    }
                    // Rows already on screen keep their objects (and so their scroll position).
                    val existing = rows.associateBy { it.key }
                    rows = buildList {
                        for (key in order) {
                            if (key in pending && !late) break
                            val row = existing[key] ?: built(key) ?: continue
                            add(row)
                        }
                    }.filter { optionKey(it.key) !in hidden }
                    latest = rows
                }
                fun done(key: String) {
                    pending -= key
                    publish()
                }
                launch { delay(3_100); publish() }
                afterSaved = { publish() }
                publish()
                if (saved != null) launch {
                    while (pending.isNotEmpty() && System.currentTimeMillis() < deadline + 9_000) delay(250)
                    publish()
                }
                // Once every row is in, the finished page is kept for next time.
                launch {
                    var waited = 0
                    while (pending.isNotEmpty() && waited++ < 300) delay(200)
                    val finished = latest
                    if (pending.isEmpty() && finished.size >= 3) {
                        val snap = SavedHome(day, signature, finished.filter { it.chips == null }.map { SavedRow(it.key, it.title, it.items, it.playOnClick, it.numbered) }, featured)
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) { HomeCache.write(repo, snap) }
                    }
                }

                if (MoodChips) launch { done("chips-mood") }
                // The genre chips: most films first. Counted with one small request per genre, all at once.
                if (GenreChips) launch {
                    val names = allGenres.filterNot { it.equals("Action & Adventure", ignoreCase = true) }
                    val counted = if (names.size < 4) emptyList() else logged("HomeScreen.genreChips") {
                        withTimeoutOrNull(3_000) {
                            names.map { g -> async { g to logged("HomeScreen.genreChips") { repo.browsePage(types = listOf(BaseItemKind.MOVIE), genre = g, start = 0, limit = 1).second }.getOrDefault(0) } }.awaitAll()
                        }
                    }.getOrNull() ?: names.map { it to 1 }
                    val chosen = counted.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }.take(14)
                    if (chosen.size >= 4) {
                        chipRows["chips-genre"] = HomeRow("chips-genre", "", emptyList(), chips = chosen.map { g -> Chip(g, spec(g) { s, n -> repo.browsePage(genre = g, start = s, limit = n) }) })
                    }
                    done("chips-genre")
                }
                // The numbered list: the ten best-rated titles you haven't watched.
                launch {
                    val numberedSpec = spec("Top 10 movies (unwatched)") { s, n -> repo.browsePage(types = listOf(BaseItemKind.MOVIE), minRating = 6.0, unplayedOnly = true, sortBy = ItemSortBy.COMMUNITY_RATING, descending = true, start = s, limit = n) }
                    val items = firstPage("numbered", numberedSpec, 10)
                    loaded["numbered"] = numberedSpec to items
                    done("numbered")
                }
                launch { resume = resumeJob.await(); done("resume") }
                launch { because = becauseJob.await(); done("because") }

                /** Loads one row worked out at run time (a person, studio or favourite genre) and publishes it. */
                fun dynamicRow(key: String, spec: CategorySpec) = launch {
                    val items = firstPage(key, spec, limit)
                    loaded[key] = spec to items
                    done(key)
                }
                // "More <genre> for you", from what you watched last.
                launch {
                    val genre = lastJob.await()?.genres?.firstOrNull() ?: return@launch done("foryou")
                    dynamicRow("foryou", spec("More ${genre.lowercase()} for you") { s, n -> repo.browsePage(genre = genre, unplayedOnly = true, sortBy = ItemSortBy.COMMUNITY_RATING, descending = true, start = s, limit = n) })
                }
                // "Starring …" two well-known faces from your library, and "From …" a big studio; different each visit.
                launch {
                    delay(1_200)
                    val (people, studios) = logged("HomeScreen.dynamicRow") { repo.popularCredits() }.getOrNull()
                        ?: return@launch listOf("starring", "starring2", "studio").forEach { done(it) }
                    if (people.size < 2) done("starring2")
                    if (people.isEmpty()) done("starring")
                    if (studios.isEmpty()) done("studio")
                    people.shuffled(dice(5)).take(2).forEachIndexed { i, p ->
                        val id = p.id ?: return@forEachIndexed done(if (i == 0) "starring" else "starring2")
                        dynamicRow(if (i == 0) "starring" else "starring2", spec("Starring ${p.name}") { s, n -> repo.browsePage(personId = id, sortBy = ItemSortBy.COMMUNITY_RATING, descending = true, start = s, limit = n) })
                    }
                    studios.randomOrNull(dice(6))?.let { st ->
                        dynamicRow("studio", spec("From ${st.name}") { s, n -> repo.browsePage(studio = st.name, sortBy = ItemSortBy.COMMUNITY_RATING, descending = true, start = s, limit = n) })
                    }
                }
                // "Directed by …": directors with plenty of titles on the server.
                launch {
                    val directors = logged("HomeScreen.dynamicRow") { repo.popularDirectors(8) }.getOrDefault(emptyList()).shuffled(dice(7)).take(2)
                    if (directors.size < 2) done("directed2")
                    if (directors.isEmpty()) done("directed")
                    directors.forEachIndexed { i, d ->
                        val id = d.id ?: return@forEachIndexed done(if (i == 0) "directed" else "directed2")
                        dynamicRow(if (i == 0) "directed" else "directed2", spec("Directed by ${d.name}") { s, n -> repo.browsePage(personId = id, personKind = org.jellyfin.sdk.model.api.PersonKind.DIRECTOR, sortBy = ItemSortBy.COMMUNITY_RATING, descending = true, start = s, limit = n) })
                    }
                }
                // The rows of the first screens are fetched first, six at a time; the rest follow three at a time once those are
                // in, so start-up is not thirty requests (and thirty answers to unpack) at once on a weak device.
                val queue = (specs + moodSpecs.filter { it.first in chosenMoods } + genreSpecs).sortedBy { (key, _) -> order.indexOf(key).let { if (it < 0) Int.MAX_VALUE else it } }
                fun fetchRow(key: String, spec: CategorySpec, gate: Semaphore) = launch {
                    loaded[key] = spec to firstPage(key, spec, limit, gate)
                    done(key)
                }
                val gate = Semaphore(6)
                val firstRows = queue.take(8).map { (key, spec) -> fetchRow(key, spec, gate) }
                launch {
                    firstRows.joinAll()
                    val slow = Semaphore(3)
                    queue.drop(8).forEach { (key, spec) -> fetchRow(key, spec, slow) }
                }
            }
        }
        outcome.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        outcome.onFailure { error = "Couldn't load your library. Check the server is running." }
        // Every row quietly turns a failed request into "nothing here", so an unreachable server would look like an
        // empty library. If nothing arrived at all, check the server and say so.
        if (error == null && rows.isEmpty() && logged("HomeScreen.fetchRow") { repo.libraries() }.isFailure) {
            error = "Couldn't reach your server. Check it's on and on the same network."
        }
        this.loaded = true
    }
    /** The tile lined up at the left of [row]: where Up and Down land. */
    private fun leftTileKey(row: HomeRow): String? {
        val info = row.state.layoutInfo.visibleItemsInfo
        fun isTile(k: Any) = k is String && !k.startsWith("more")
        val item = info.firstOrNull { it.offset >= -8 && isTile(it.key) } ?: info.firstOrNull { isTile(it.key) }
        return item?.key as String?
    }

    /**
     * Up or Down to row [index]. This is the ONLY thing that ever moves the page up or down: focus itself never scrolls
     * it (see [FocusDoesNotScroll]), so moving sideways cannot nudge the page. The row's top is glided to [anchorPx]
     * from the top of the screen, so a given row always sits at the same height (except at the very end of the page).
     */
    suspend fun goToRow(index: Int, anchorPx: Float, held: Boolean = false, pxPerMs: Float = 3f): Unit = coroutineScope<Unit> {
        val row = rows.getOrNull(index) ?: return@coroutineScope
        fun spec(distance: Float) =
            if (held) tween<Float>((kotlin.math.abs(distance) / pxPerMs).toInt().coerceIn(40, 300), easing = androidx.compose.animation.core.LinearEasing)
            else glideSpec(150)
        var key = leftTileKey(row)
        var early: kotlinx.coroutines.Job? = null
        if (key == null) {
            // The row isn't built yet (the page has not caught up with focus). NEVER jump: head towards it by an estimate,
            // and take focus only once it exists, so the tile holding focus is never thrown off screen and lost.
            val estimate = estimateDistance(index, anchorPx) ?: return@coroutineScope
            early = launch { listState.animateScrollBy(estimate, spec(estimate)) }
            var tries = 0
            while (key == null && tries < 40) {
                withFrameNanos { }
                key = leftTileKey(row)
                tries++
            }
            if (key == null) { early.cancel(); return@coroutineScope }
        }
        focus.requestKey(key)
        early?.cancel()
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == row.key } ?: return@coroutineScope
        // Item offsets count from the start of the content, which is below the top padding.
        val distance = (item.offset + info.beforeContentPadding) - anchorPx
        if (kotlin.math.abs(distance) <= 1f) return@coroutineScope
        // A tap eases out; a held key moves at one steady speed, so the page flows from row to row without stopping.
        listState.animateScrollBy(distance, spec(distance))
        Unit
    }

    /**
     * Slides the page so the "Surprise me" item sits where Up and Down put a row. It works from the last row (which is always on
     * screen when the remote is on it), so it also works while a held key still has the page part-way through its previous slide.
     */
    suspend fun settleOnSurprise(anchorPx: Float, gapPx: Float, held: Boolean, pxPerMs: Float) {
        val info = listState.layoutInfo
        val last = rows.lastOrNull()?.let { r -> info.visibleItemsInfo.firstOrNull { it.key == r.key } } ?: return
        val top = last.offset + last.size + gapPx
        val distance = (top + info.beforeContentPadding) - anchorPx
        if (kotlin.math.abs(distance) <= 1f) return
        val spec = if (held) tween<Float>((kotlin.math.abs(distance) / pxPerMs).toInt().coerceIn(40, 300), easing = androidx.compose.animation.core.LinearEasing) else glideSpec(150)
        listState.animateScrollBy(distance, spec)
    }

    /** Roughly how far to scroll to bring row [index] to [anchorPx], judged from the rows that are on screen. */
    private fun estimateDistance(index: Int, anchorPx: Float): Float? {
        val info = listState.layoutInfo
        val ref = info.visibleItemsInfo.firstOrNull { it.key is String && rows.any { r -> r.key == it.key } } ?: return null
        val stride = ref.size + 18f * 2f   // a row and the gap after it (about)
        val refIndex = rows.indexOfFirst { it.key == ref.key }
        return (ref.offset + info.beforeContentPadding) + (index - refIndex) * stride - anchorPx
    }

    /** How far (px) the row [index] is from where Up/Down puts it, and how tall it is; null if it is not on screen. */
    fun lag(index: Int, anchorPx: Float): Pair<Float, Int>? {
        val row = rows.getOrNull(index) ?: return null
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == row.key } ?: return null
        return kotlin.math.abs((item.offset + info.beforeContentPadding) - anchorPx) to item.size
    }

    /** Puts focus on the first tile of row [index] without moving the page. */
    fun focusRow(index: Int): Boolean {
        val row = rows.getOrNull(index) ?: return false
        val key = leftTileKey(row) ?: return false
        return focus.requestKey(key)
    }

    /** The Continue watching title being asked about (held OK on its tile), or null. */
    var removing by mutableStateOf<BaseItemDto?>(null)
    /** A tile to move focus to once the row has been redrawn. */
    var pendingFocus by mutableStateOf<String?>(null)
    /** The Continue watching title waiting to be dropped once focus has moved off it. */
    var pendingRemoval by mutableStateOf<java.util.UUID?>(null)

    /** Drops one title from the Continue watching row at once (and the row itself if that was the last). */
    fun removeFromResume(id: java.util.UUID) {
        val row = rows.firstOrNull { it.key == "resume" } ?: return
        val index = row.items.indexOfFirst { it.id == id }
        if (index < 0) return
        val neighbour = row.items.getOrNull(index + 1) ?: row.items.getOrNull(index - 1)
        if (neighbour == null) {
            rows = rows.filterNot { it.key == "resume" }
            return
        }
        // Focus goes to the neighbouring tile first, while the removed one is still on screen; only then is it dropped.
        // (Dropping the focused tile first sends the remote to the top of the page and back.)
        pendingRemoval = id
        pendingFocus = tileKey("resume", neighbour.id)
    }

    /** Takes the title out of the Continue watching row, keeping the row (and its scroll position) as it is. */
    fun dropFromResume(id: java.util.UUID) {
        val row = rows.firstOrNull { it.key == "resume" } ?: return
        row.items = row.items.filter { it.id != id }
        val key = pendingFocus
        if (key != null) focus.remember(key)
    }

    /** After watching something, refresh Continue watching. */
    suspend fun refreshProgress() {
        app.blush.tv.ui.player.PlayerReports.settle()
        val resume = logged("HomeScreen.refreshProgress") { repo.resume() }.getOrNull() ?: return
        val withoutResume = rows.filterNot { it.key == "resume" }
        val old = rows.firstOrNull { it.key == "resume" }
        var updated = when {
            resume.isEmpty() || "resume" in repo.store.hiddenHomeRows -> withoutResume
            // The row itself is kept, and with it your place along it; it only winds back to the start when its order changed.
            old != null -> {
                val reordered = old.items.map { it.id } != resume.map { it.id }
                old.items = resume
                if (reordered) old.state.requestScrollToItem(0)
                rows
            }
            else -> listOf(HomeRow("resume", "Continue watching", resume, playOnClick = true)) + withoutResume
        }
        refreshWatchlistRow(updated)?.let { updated = it }
        rows = updated
    }

    /** Brings the "On your watchlist" row up to date (titles added or removed since Home was built). */
    private suspend fun refreshWatchlistRow(current: List<HomeRow>): List<HomeRow>? {
        if ("watchlist" in repo.store.hiddenHomeRows) return null
        val limit = 20
        val spec = spec("On your watchlist") { s, n -> repo.browsePage(favouritesOnly = true, sortBy = ItemSortBy.DATE_CREATED, descending = true, start = s, limit = n) }
        val loaded = logged("HomeScreen.refreshWatchlistRow") { spec.load(0, limit).first }.getOrNull() ?: return null
        val added = listedItems.values.filter { listed[it.id] == true }.map { it.copy(userData = it.userData?.copy(isFavorite = true)) }
        val fresh = (added + loaded.filter { l -> listed[l.id] != false && added.none { it.id == l.id } }).take(limit)
        val old = current.firstOrNull { it.key == "watchlist" }
        if (old != null && old.items.map { it.id } == fresh.map { it.id }) return null
        val without = current.filterNot { it.key == "watchlist" }
        if (fresh.size < 3) return without
        categories["watchlist"] = spec
        val row = HomeRow("watchlist", spec.title, fresh, more = spec.takeIf { fresh.size >= limit })
        // Same place as when Home was built: after the last of the rows that come before it.
        val before = listOf("resume", "recent")
        val at = without.indexOfLast { it.key in before } + 1
        return without.toMutableList().apply { add(if (old != null) current.indexOf(old).coerceAtMost(size) else at, row) }
    }
}
@Composable
fun HomeScreen(app: AppState, model: HomeModel) {
    val repo = app.repo
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    androidx.compose.runtime.SideEffect { Perf.homeRecomposed.incrementAndGet() }
    // How posters behave under the remote, and whether the quicker movement is on (Settings → Home).
    val tileOpen = remember { when (repo.store.tileOpen ?: if (Lite.on) "posters" else "now") { "posters" -> TilePosters; else -> TileNow } }
    val quick = remember { repo.store.quickNav }

    LaunchedEffect(Unit) {
        model.leaving = false
        if (!model.ensureLoading()) {
            // Still filling in from before: give it a moment, then bring Continue watching up to date.
            withTimeoutOrNull(4_000) { model.awaitLoading() }
            model.refreshProgress()
        }
        // Once Home is up, the tag counts More like this needs are worked out quietly, ahead of the first title's page.
        delay(4_000)
        repo.warmTags()
    }
    DisposableEffect(Unit) {
        onDispose {
            // A tile that was opened kept its preview playing under the fade; it stops now the page has gone.
            model.preview?.stop()
            model.focus.leaving()
            // With "Rows remember your place" off, every row starts again from its first title, and the remote returns to that title.
            if (!repo.store.rowsRememberPlace) model.forgetRowPlaces()
        }
    }

    // Removing a Continue watching title: focus moves to its neighbour first, then the tile is dropped.
    val pendingFocus = model.pendingFocus
    LaunchedEffect(pendingFocus) {
        if (pendingFocus == null) return@LaunchedEffect
        var attempts = 0
        do {
            delay(60)
            val moved = model.focus.requestKey(pendingFocus)
            attempts++
        } while (!moved && attempts < 10)
        model.pendingRemoval?.let { model.dropFromResume(it) }
        model.pendingRemoval = null
        model.pendingFocus = null
    }
    model.removing?.let { target ->
        ConfirmDialog(
            title = "Remove from Continue watching?",
            body = if ((target.userData?.playbackPositionTicks ?: 0L) <= 0L) "${target.displayTitle()} will leave this row until you watch another episode. It isn't deleted."
                else "${target.displayTitle()} will leave this row and start again from the beginning next time. It isn't deleted.",
            confirmLabel = "Remove",
            onDismiss = { model.removing = null },
            onConfirm = {
                model.removing = null
                model.removeFromResume(target.id)
                // A next episode not started yet has no place to clear: remember to leave it out instead.
                if ((target.userData?.playbackPositionTicks ?: 0L) <= 0L) repo.store.hiddenNextUp = repo.store.hiddenNextUp + target.id.toString()
                else scope.launch { repo.clearProgress(target.id) }
            },
        )
    }

    // OK held on a tile: a short menu for the things you'd otherwise open the title's page to do.
    model.menu?.let { (row, item) ->
        val episode = item.type == BaseItemKind.EPISODE
        val played = item.userData?.played == true
        val listed = item.userData?.isFavorite == true
        TileMenu(
            title = item.displayTitle(),
            actions = buildList {
                if (row.playOnClick) add(MenuAction(if (episode) "Go to the series" else "Details") {
                    model.leaving = true
                    app.go(Route.Details(if (episode) item.seriesId ?: item.id else item.id))
                })
                add(MenuAction(if (played) "Mark as not watched" else "Mark as watched") {
                    if (row.playOnClick) {
                        // Watched means it leaves Continue watching: the remote steps to its neighbour first (as when it is removed).
                        model.removeFromResume(item.id)
                        scope.launch { repo.setPlayed(item.id, true) }
                    } else scope.launch {
                        if (repo.setPlayed(item.id, !played)) {
                            row.items = row.items.map { if (it.id == item.id) it.copy(userData = it.userData?.copy(played = !played)) else it }
                            model.refreshProgress()
                        }
                    }
                })
                if (!episode) add(MenuAction(if (listed) "Remove from watchlist" else "Add to watchlist") {
                    scope.launch {
                        if (repo.setFavorite(item.id, !listed)) {
                            row.items = row.items.map { if (it.id == item.id) it.copy(userData = it.userData?.copy(isFavorite = !listed)) else it }
                            if (row.key != "watchlist") model.refreshProgress()
                        }
                    }
                })
                if (row.playOnClick) add(MenuAction("Remove from Continue watching") { model.removing = item })
            },
            onDismiss = { model.menu = null },
        )
    }

    // Coming back from the side bar: focus returns to the same tile with the page and every row exactly where they were.
    model.railReturn.restore = {
        val index = model.listState.firstVisibleItemIndex
        val offset = model.listState.firstVisibleItemScrollOffset
        val rowPlaces = model.rows.map { r -> Triple(r.state, r.state.firstVisibleItemIndex, r.state.firstVisibleItemScrollOffset) }
        val restored = model.focus.restoreNow()
        if (restored) scope.launch {
            repeat(3) {
                withFrameNanos { }
                model.listState.scrollToItem(index, offset)
                rowPlaces.forEach { (st, i, o) -> st.scrollToItem(i, o) }
            }
        }
        restored
    }

    // Back to Home from another tab: the top of the page, on the first tile.
    LaunchedEffect(model.goToTop) {
        if (!model.goToTop) return@LaunchedEffect
        model.goToTop = false
        model.listState.scrollToItem(0)
        model.rows.forEach { it.state.requestScrollToItem(0) }
        // The banner is what the top of Home is: the remote goes to its Play button (and the banner's text and buttons come back),
        // as when the app opens. Without a banner it goes to the first tile.
        model.userMoved = false
        repeat(20) {
            if (model.shownHero != null) {
                model.heroFocused = true
                if (model.focus.requestKey("hero-play")) return@LaunchedEffect
            } else if (model.featuredDone && model.featured.isEmpty() && model.focusRow(0)) return@LaunchedEffect
            delay(50)
        }
    }

    // A row that has scrolled out of sight goes back to its start, ready for next time.
    LaunchedEffect(model) {
        // Watched by the first and last row in view (two numbers), not by building a list and a set on every frame of a scroll.
        snapshotFlow { model.listState.layoutInfo.visibleItemsInfo.let { v -> (v.firstOrNull()?.index ?: -1) * 10_000 + (v.lastOrNull()?.index ?: -1) } }
            .collect {
                val visible = model.listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
                model.rows.forEach { row ->
                    if (row.key !in visible && (row.state.firstVisibleItemIndex > 0 || row.state.firstVisibleItemScrollOffset > 0)) {
                        row.state.requestScrollToItem(0)
                    }
                }
            }
    }

    // What the stats read-out reports as going on.
    DisposableEffect(Unit) {
        Perf.context = {
            buildList {
                if (model.listState.isScrollInProgress) add("page-scroll")
                if (model.rows.any { it.state.isScrollInProgress }) add("row-scroll")
                if (model.preview?.hot == true) add("preview")
            }.joinToString("+").ifEmpty { "idle" }
        }
        onDispose { Perf.context = null }
    }

    // Where a row's top sits after Up/Down: about a third of the way down, as before.
    val anchorPx = with(density) { LocalWindowInfo.current.containerSize.height * 0.30f - 34.dp.toPx() }
        val screenWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }

    // Quietly get the first tiles of every row ready: fetched, and decoded at the size the tile is drawn, into the picture memory
    // (a poster is only ~40 KB, so a whole page of them is a few MB of a 192 MB heap). Measurements showed a first fast run down
    // the page hitched (47 of 373 frames) and an identical second run did not (0 of 465): the difference is pictures that had to
    // be decoded as each row arrived. One at a time (so it can never crowd out pictures you are waiting for), only while the
    // remote is still, and each tile only once.
    val context = androidx.compose.ui.platform.LocalContext.current

    // ----- Tile previews -----
    // One controller for the screen; its player is only built the first time a preview is wanted. A preview waits for the page and rows
    // to stand still, and everything stops the moment the remote leaves a tile or the app leaves the screen.
    val preview = remember {
        PreviewController(context, repo, scope) {
            model.listState.isScrollInProgress || model.rows.any { it.state.isScrollInProgress } ||
                android.os.SystemClock.uptimeMillis() - model.lastMove < 400
        }
    }
    model.preview = preview
    // The player is built once, in a quiet moment, so the first preview doesn't hitch the screen.
    LaunchedEffect(preview) {
        delay(8_000)
        while (android.os.SystemClock.uptimeMillis() - model.lastMove < 2_000 || model.listState.isScrollInProgress) delay(500)
        preview.warmUp()
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, preview) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) preview.stop() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); preview.destroy() }
    }

    // ----- The banner -----
    val hero = model.featured.getOrNull(model.heroIndex)
    val heroOn = model.featured.isNotEmpty()
    val heroPx = with(density) { LocalWindowInfo.current.containerSize.let { it.width to it.height } }
    // A title only becomes the one shown once its artwork and logo are loaded (4 s at most), so picture, logo, text and buttons
    // change at the same moment; the title after it is then loaded in the background, ready.
    suspend fun warm(item: BaseItemDto) {
        val loader = coil3.SingletonImageLoader.get(context)
        coroutineScope {
            repo.backdropUrl(item)?.let { url -> launch { logged("HomeScreen.prefetchHero") { loader.execute(heroRequest(context, url, heroPx)) } } }
            repo.logoUrl(item)?.let { url -> launch { logged("HomeScreen.prefetchHero") { prepareLogo(context, url) } } }
        }
    }
    LaunchedEffect(hero?.id) {
        val next = hero ?: return@LaunchedEffect
        withTimeoutOrNull(4_000) { warm(next) }
        model.shownHero = next
        // Ready both ways, so flicking through the dots (either direction) switches at once.
        // (After a short pause, so flicking quickly along the dots doesn't decode the neighbours of every title it passes.)
        delay(if (model.warmed.isEmpty()) 1_200 else 250)
        while (android.os.SystemClock.uptimeMillis() - model.lastMove < 250) delay(100)
        val n = model.featured.size.coerceAtLeast(1)
        // One and two along, both ways, nearest first, so flicking through the dots finds the next picture already in hand.
        for (offset in intArrayOf(1, n - 1, 2, n - 2)) model.featured.getOrNull((model.heroIndex + offset) % n)?.let { other -> if (other.id != next.id) warm(other) }
    }
    // Then the other titles' artwork is fetched quietly (into the disk cache only), one at a time, so none of them has to come
    // over the network when you get to it.
    LaunchedEffect(model.shownHero?.id) {
        if (model.shownHero == null || Lite.on) return@LaunchedEffect
        delay(8_000)
        val loader = coil3.SingletonImageLoader.get(context)
        for (item in model.featured) {
            val url = repo.backdropUrl(item) ?: continue
            if (!model.warmed.add(url)) continue
            while (android.os.SystemClock.uptimeMillis() - model.lastMove < 1_500 || model.preview?.hot == true) delay(250)
            // Only the download matters here (it lands in the disk cache); decoding at a thumbnail size is almost free, where a
            // full-screen decode of each title at start-up was a burst of work and memory.
            logged("HomeScreen.prefetchHero") {
                loader.execute(coil3.request.ImageRequest.Builder(context).data(url).size(160, 90).memoryCachePolicy(coil3.request.CachePolicy.DISABLED).build())
            }
            delay(300)
        }
    }
    // The page fills in in order: the banner first, then Continue watching, then the other rows. The rows are only built once
    // the banner has shown (or there is no banner, or it is taking too long).
    LaunchedEffect(Unit) {
        if (model.rowsOpen) return@LaunchedEffect
        withTimeoutOrNull(3_500) {
            snapshotFlow { model.shownHero != null || (model.featuredDone && model.featured.isEmpty()) }.first { it }
        }
        model.rowsOpen = true
    }
    val nav = LocalNavBar.current
    // The remote starts on Play (or Resume) when the app opens. Android hands its own first focus to whatever it finds
    // (often a tile) around the time the window comes up, so this doesn't stop at the first success: it keeps putting
    // the remote back on Play until you press something yourself.
    LaunchedEffect(model.shownHero != null) {
        if (model.shownHero == null || model.focus.pending) return@LaunchedEffect
        var settled = 0
        var tries = 0
        while (!model.userMoved && settled < 6 && tries < 120) {
            val onPlay = model.heroFocused && model.focus.key == "hero-play"
            if (onPlay) settled++ else { settled = 0; model.focus.requestKey("hero-play") }
            tries++
            delay(50)
        }
    }
    // The banner's clock: it runs only while you're on the banner. The navigation bar, the dots and scrolling down pause it; when it reaches the end the next title comes in.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = if (last == 0L) 0L else (now - last) / 1_000_000L
            last = now
            val running = !Lite.on && model.featured.size > 1 && model.heroFocused && !model.pagerFocused && nav?.focused != true
            if (!running) { if (dt > 100) last = 0L; delay(120); last = 0L; continue }
            model.heroProgress = (model.heroProgress + dt.coerceAtMost(100L).toFloat() / HeroRotateMs).coerceAtMost(1f)
            if (model.heroProgress >= 1f) model.showHero(model.heroIndex + 1)
        }
    }
    // After a few seconds without touching the remote on the banner, the description fades and the logo settles lower.
    var lastInput by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    var heroIdle by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            heroIdle = !Lite.on && model.heroFocused && System.currentTimeMillis() - lastInput > 5_000
        }
    }
    val idleAmount by androidx.compose.animation.core.animateFloatAsState(if (heroIdle) 1f else 0f, tween(if (heroIdle) 1200 else 400), label = "idle")
    // How far the page has scrolled; the banner moves up with it.
    val heroScroll by remember {
        androidx.compose.runtime.derivedStateOf { if (model.listState.firstVisibleItemIndex == 0) model.listState.firstVisibleItemScrollOffset else Int.MAX_VALUE / 4 }
    }
    // The banner text fades as the remote leaves it. Driven from an effect and only read when drawing, so the change of focus
    // doesn't make the whole page (every visible row) compose again in the very frame the page starts to scroll.
    val heroAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) { snapshotFlow { model.heroFocused }.collect { heroAlpha.animateTo(if (it) 1f else 0f, tween(if (it) 300 else 110)) } }
    val heroIntro by androidx.compose.animation.core.animateFloatAsState(if (model.shownHero != null) 1f else 0f, tween(600), label = "heroIntro")

    // The first rows' pictures are asked for the moment the rows are known, while the banner is still coming up (the rows
    // themselves are not built until it has), so the tiles are drawn with their pictures rather than filling in.
    val haveRows = model.rows.isNotEmpty()
    LaunchedEffect(haveRows) {
        if (!haveRows) return@LaunchedEffect
        val loader = coil3.SingletonImageLoader.get(context)
        val pw = with(density) { PosterWidth.roundToPx() }
        val ph = with(density) { PosterHeight.roundToPx() }
        val cw = with(density) { CardWidth.roundToPx() }
        for (row in model.rows.take(3)) {
            if (row.chips != null || row.numbered) continue
            for (item in row.items.take(7)) {
                val url = (if (row.playOnClick) repo.thumbUrl(item, 520) else repo.posterUrl(item)) ?: continue
                if (!model.warmed.add(url)) continue
                val size = if (row.playOnClick) coil3.size.Size(cw, cw * 9 / 16) else coil3.size.Size(pw, ph)
                loader.enqueue(coil3.request.ImageRequest.Builder(context).data(url).size(size).build())
            }
        }
    }
    LaunchedEffect(model.rows) {
        if (model.rows.isEmpty()) return@LaunchedEffect
        delay(2_500)
        val loader = coil3.SingletonImageLoader.get(context)
        val pw = with(density) { PosterWidth.roundToPx() }
        val ph = with(density) { PosterHeight.roundToPx() }
        val cw = with(density) { CardWidth.roundToPx() }
        val wide = coil3.size.Size(with(density) { WideWidth.roundToPx() }, ph)
        // First the wide thumbnail of the tile the remote lands on in each row (the left one), so moving down finds it ready.
        for (row in model.rows) {
            if (row.playOnClick || row.numbered || row.chips != null || tileOpen == TilePosters) continue
            val first = row.items.firstOrNull() ?: continue
            val url = repo.thumbUrl(first, 640) ?: repo.posterUrl(first, 640) ?: continue
            if (!model.warmed.add(url)) continue
            while (android.os.SystemClock.uptimeMillis() - model.lastMove < 1_000 || model.preview?.hot == true) delay(250)
            logged("HomeScreen.prefetchHero") { loader.execute(coil3.request.ImageRequest.Builder(context).data(url).size(wide).build()) }
            delay(40)
        }
        // The numbered list's larger pictures go first: they are the ones that used to pop in as the row scrolled into view.
        for (row in model.rows.sortedBy { if (it.numbered) 0 else 1 }) for (item in row.items.take(10)) {
            val url = if (row.playOnClick) repo.thumbUrl(item, 520) else repo.posterUrl(item)
            if (url == null || !model.warmed.add(url)) continue
            while (android.os.SystemClock.uptimeMillis() - model.lastMove < 1_000 || model.preview?.hot == true) delay(250)
            val size = if (row.playOnClick) coil3.size.Size(cw, cw * 9 / 16) else if (row.numbered) coil3.size.Size(with(density) { NumPosterWidth.roundToPx() }, with(density) { NumPosterHeight.roundToPx() }) else coil3.size.Size(pw, ph)
            logged("HomeScreen.prefetchHero") { loader.execute(coil3.request.ImageRequest.Builder(context).data(url).size(size).build()) }
            Perf.homeNote = "warming pictures: ${model.warmed.size}"
            delay(40)
        }
        Perf.homeNote = "pictures warmed: ${model.warmed.size}"
    }
    // Moving down the page, the pictures of the next two rows are asked for straight away (the quiet warming above waits while the
    // remote is in use, so a first scroll down used to find them all still to fetch).
    LaunchedEffect(model.rows.isNotEmpty()) {
        if (model.rows.isEmpty()) return@LaunchedEffect
        val loader = coil3.SingletonImageLoader.get(context)
        val pw = with(density) { PosterWidth.roundToPx() }
        val ph = with(density) { PosterHeight.roundToPx() }
        val cw = with(density) { CardWidth.roundToPx() }
        snapshotFlow { model.listState.firstVisibleItemIndex }.collect { first ->
            for (row in model.rows.drop(maxOf(0, first)).take(4)) {
                if (row.chips != null || row.numbered) continue
                for (item in row.items.take(7)) {
                    val url = (if (row.playOnClick) repo.thumbUrl(item, 520) else repo.posterUrl(item)) ?: continue
                    if (!model.warmed.add(url)) continue
                    val size = if (row.playOnClick) coil3.size.Size(cw, cw * 9 / 16) else coil3.size.Size(pw, ph)
                    loader.enqueue(coil3.request.ImageRequest.Builder(context).data(url).size(size).build())
                }
            }
        }
    }
    // The numbered list's ten pictures are asked for the moment the list exists (they are large and the server has to resize them), not
    // after the quiet wait above, so holding Down from the top finds them ready.
    val numberedItems = model.rows.firstOrNull { it.numbered }?.items
    LaunchedEffect(numberedItems) {
        val items = numberedItems ?: return@LaunchedEffect
        val loader = coil3.SingletonImageLoader.get(context)
        val size = coil3.size.Size(with(density) { NumPosterWidth.roundToPx() }, with(density) { NumPosterHeight.roundToPx() })
        for (item in items.take(10)) {
            val url = repo.posterUrl(item) ?: continue
            if (!model.warmed.add(url)) continue
            loader.enqueue(coil3.request.ImageRequest.Builder(context).data(url).size(size).build())
        }
    }
    Box(
        Modifier.fillMaxSize().background(Blush.colors.background).onPreviewKeyEvent { e ->
            lastInput = System.currentTimeMillis()
            if (e.type == KeyEventType.KeyDown) model.userMoved = true
            // Back opens the side bar: the tile the remote leaves stays large (just without its ring), as it does going Left.
            if (e.type == KeyEventType.KeyDown && (e.key == Key.Back || e.key == Key.Escape)) model.lastKey = Key.Back
            heroIdle = false
            if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionUp || e.key == Key.DirectionDown || e.key == Key.DirectionLeft || e.key == Key.DirectionRight)) {
                model.noteMove(e.key)
                model.held = e.nativeKeyEvent.repeatCount > 0
                if (!model.held) model.pressAt = android.os.SystemClock.uptimeMillis()
            }
            false
        },
    ) {
        if (heroOn) {
            // The banner's artwork. It scrolls up with the page as one piece (a translation of its own layer, which costs the
            // page nothing to draw again), so moving down to the rows is a single smooth slide, and it melts into the plain
            // background at its bottom edge.
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    translationY = -heroScroll.toFloat()
                    // Picture and both gradients are flattened into one texture, so scrolling the page only slides that texture
                    // (one pass) instead of drawing a full-screen picture and two full-screen gradients again every frame.
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                },
            ) {
                HeroBackdrop(model.shownHero?.let { repo.backdropUrl(it) }, heroPx)
                val page = Blush.colors.background
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        drawRect(Brush.horizontalGradient(0f to page.copy(alpha = 0.95f), 0.42f to page.copy(alpha = 0.55f), 0.75f to androidx.compose.ui.graphics.Color.Transparent))
                        drawRect(Brush.verticalGradient(0.45f to androidx.compose.ui.graphics.Color.Transparent, 0.78f to page.copy(alpha = 0.85f), 1f to page))
                    },
                )
            }
            // Banner text and buttons, moving up with the page.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(HeroHeight)
                    .graphicsLayer {
                        // Moves with the page as a layer (no re-measuring of the text and buttons on every frame of the scroll).
                        translationY = -heroScroll.toFloat()
                        alpha = heroAlpha.value * heroIntro * (1f - (heroScroll / 160f).coerceIn(0f, 1f))
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                    }
                    .onFocusChanged {
                        // Only switch to "on the banner" here; leaving for the side bar shouldn't change what's shown.
                        if (it.hasFocus) {
                            model.heroFocused = true
                            scope.launch { model.listState.animateScrollToItem(0) }
                        }
                    }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown || e.key != Key.DirectionDown) return@onPreviewKeyEvent false
                        // Down from any banner button: to the first tile of the first row.
                        if (model.rows.isNotEmpty()) model.moving = scope.launch { model.goToRow(0, anchorPx, false, pxPerMs = 2.0f * density.density) }
                        true
                    },
            ) {
                HeroBlock(app, model, model.shownHero) { idleAmount }
            }
        }
        PivotScrolling(FocusDoesNotScroll) {
            if (model.shown && model.rowsOpen) LazyColumn(
                state = model.listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = if (heroOn) 0.dp else PageTop + 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // The banner sits in this space, so the first row starts just under it.
                if (heroOn) item(key = "hero-space") { androidx.compose.foundation.layout.Spacer(Modifier.height(HeroHeight - 18.dp)) }
                itemsIndexed(model.rows, key = { _, row -> row.key }) { index, row ->
                    // Up and Down land on the tile lined up at the left of the next row.
                    Box(
                        Modifier.onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val step = when (e.key) { Key.DirectionDown -> 1; Key.DirectionUp -> -1; else -> return@onPreviewKeyEvent false }
                            val target = index + step
                            if (target !in model.rows.indices) {
                                // Up from the first row goes to the banner; Down from the last goes to "Surprise me", and the page slides to it just as it does between rows.
                                if (step < 0) return@onPreviewKeyEvent (if (heroOn) model.focus.requestKey("hero-play") else true)
                                if (model.focus.requestKey("surprise")) {
                                    val held = e.nativeKeyEvent.repeatCount > 0
                                    // Whatever slide is still going (to the last row) gives way to this one.
                                    model.moving?.cancel()
                                    model.moving = scope.launch { model.settleOnSurprise(anchorPx, with(density) { 18.dp.toPx() }, held, 2.0f * density.density) }
                                }
                                return@onPreviewKeyEvent true
                            }
                            // A held key repeats faster than the page can follow. A repeat is only taken once the page has
                            // nearly caught up with focus (within about half a row), so the next row is always built already.
                            val held = e.nativeKeyEvent.repeatCount > 0
                            if (held) {
                                val behind = model.lag(index, anchorPx)
                                if (behind != null && behind.first > behind.second * 0.5f) return@onPreviewKeyEvent true
                            }
                            val pace = 2.0f
                            model.moving = scope.launch { model.goToRow(target, anchorPx, held, pxPerMs = pace * density.density) }
                            true
                        },
                    ) {
                        HomeRowView(app, model, row, isFirst = index == 0, screenWidth = screenWidth, tileOpen = tileOpen, quick = quick)
                    }
                }
                // Below the last row: a full screen of room (so the last row can be put where every other row sits, instead of stopping short
                // where the list ends), with "Surprise me" at its top.
                if (model.rows.isNotEmpty()) item(key = "surprise") {
                    SurpriseMe(
                        app, model,
                        onUp = {
                            if (model.rows.isNotEmpty()) model.moving = scope.launch { model.goToRow(model.rows.lastIndex, anchorPx, false, pxPerMs = 2.0f * density.density) }
                        },
                        modifier = Modifier.fillParentMaxHeight(1f),
                    )
                }
                if (model.loaded && model.rows.isEmpty()) {
                    item(key = "empty") {
                        val problem = model.error
                        androidx.compose.foundation.layout.Column(
                            Modifier.padding(start = ContentStart, top = 60.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(if (problem != null) "Can't load Home" else "Welcome to Bloom", style = BlushType.display)
                            Text(problem ?: "Add some films or series to your server and they'll show up here.", style = BlushType.body, color = Blush.colors.textMuted)
                            if (problem != null) {
                                SecondaryButton(
                                    "Try again",
                                    null,
                                    onClick = { scope.launch { model.error = null; model.loaded = false; model.ensureLoading() } },
                                    modifier = Modifier.restorableFocus(model.focus, "retry", isDefault = true),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A quiet button under the last row that opens a title picked at random, with a small button beside it for what it picks from. */
@Composable
private fun SurpriseMe(app: AppState, model: HomeModel, onUp: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var nothing by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(app.repo.store.surpriseOptions) }
    var editing by remember { mutableStateOf(false) }
    var onOptions by remember { mutableStateOf(false) }
    val main = remember { androidx.compose.ui.focus.FocusRequester() }
    val tune = remember { androidx.compose.ui.focus.FocusRequester() }
    val roll = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.foundation.layout.Row(
        modifier.padding(start = ContentStart, top = 6.dp).onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionUp -> { onUp(); true }
                // Nothing lies below or past the options button; without this the search would jump to the banner at the top.
                Key.DirectionDown -> true
                Key.DirectionRight -> { if (!onOptions) runCatching { tune.requestFocus() }; true }
                Key.DirectionLeft -> if (onOptions) { runCatching { main.requestFocus() }; true } else false
                else -> false
            }
        },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        FocusSurface(
            onClick = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        // The dice tumbles while the pick is fetched; the page opens once both are done.
                        val tumble = launch { roll.snapTo(0f); roll.animateTo(1f, androidx.compose.animation.core.tween(800, easing = FastOutSlowInEasing)) }
                        val pick = logged("HomeScreen.SurpriseMe") { app.repo.surprise(options) }.getOrNull()
                        tumble.join()
                        busy = false
                        if (pick != null) {
                            // Back from the title lands here again, not on the row above.
                            model.leaving = true
                            app.go(Route.Details(pick.id, preview = pick))
                        } else {
                            // Says so, rather than doing nothing: the server didn't answer, or nothing matches the options.
                            nothing = true
                            delay(3_000)
                            nothing = false
                        }
                    }
                }
            },
            onFocused = { onOptions = false; model.focus.remember("surprise"); model.heroFocused = false },
            modifier = Modifier.focusRequester(main).widthIn(min = 300.dp).height(56.dp).restorableFocus(model.focus, "surprise"),
            shape = RoundedCornerShape(12.dp),
            focusedScale = 1.04f,
            ringWidth = 0.dp,
            background = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f),
            focusedBackground = androidx.compose.ui.graphics.Color(0xFFF5F5F7),
        ) { focused ->
            val ink = if (focused) androidx.compose.ui.graphics.Color(0xFF14151B) else Blush.colors.text
            val quiet = if (focused) androidx.compose.ui.graphics.Color(0xFF14151B).copy(alpha = 0.62f) else Blush.colors.textMuted
            androidx.compose.foundation.layout.Row(
                Modifier.align(Alignment.CenterStart).padding(start = 20.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    Icons.Dice, null, if (focused) ink else Blush.colors.accent.color,
                    Modifier.graphicsLayer {
                        val t = roll.value
                        rotationZ = 720f * t
                        val pop = 1f + 0.45f * kotlin.math.sin(Math.PI.toFloat() * t)
                        scaleX = pop; scaleY = pop
                        translationY = -10.dp.toPx() * kotlin.math.sin(Math.PI.toFloat() * t)
                    },
                    size = 18.dp,
                )
                Column {
                    Text(
                        if (busy) "Finding something…" else if (nothing) "Nothing matches, try other options" else "Surprise me",
                        style = BlushType.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.W600),
                        color = ink,
                    )
                    Text(options.describe(), style = BlushType.caption, color = quiet, maxLines = 1)
                }
            }
        }
        // The options: a small round button with the filter icon.
        FocusSurface(
            onClick = { editing = true },
            onFocused = { onOptions = true; model.heroFocused = false },
            modifier = Modifier.padding(top = 6.dp).focusRequester(tune).size(44.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            focusedScale = 1.06f,
            ringWidth = 0.dp,
            background = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f),
            focusedBackground = androidx.compose.ui.graphics.Color(0xFFF5F5F7),
        ) { focused ->
            Icon(Icons.Filter, "Surprise me options", if (focused) androidx.compose.ui.graphics.Color(0xFF14151B) else Blush.colors.text, Modifier.align(Alignment.Center), size = 18.dp)
        }
    }
    if (editing) SurpriseOptionsPanel(
        app,
        options,
        onChange = { options = it; app.repo.store.surpriseOptions = it },
        onDismiss = {
            editing = false
            scope.launch { repeat(10) { delay(40); if (runCatching { tune.requestFocus() }.getOrDefault(false)) return@launch } }
        },
    )
}

/** The pop-up for what "Surprise me" picks from. OK on a row steps through its choices; Genre opens a list. */
@Composable
private fun SurpriseOptionsPanel(app: AppState, o: SurpriseOptions, onChange: (SurpriseOptions) -> Unit, onDismiss: () -> Unit) {
    var pickingGenre by remember { mutableStateOf(false) }
    var backFromGenre by remember { mutableStateOf(false) }
    var genres by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { genres = logged("HomeScreen.SurpriseOptionsPanel") { app.repo.genres() }.getOrDefault(emptyList()) }
    fun <T> next(list: List<T>, now: T): T = list[(list.indexOf(now) + 1).mod(list.size)]
    if (pickingGenre) {
        PopupPanel({ pickingGenre = false; backFromGenre = true }, "Genre", swallowHeldOk = false, width = 260.dp) {
            val first = rememberFirstFocus()
            Column(
                Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val pick = { g: String? -> onChange(o.copy(genre = g)); pickingGenre = false; backFromGenre = true }
                PopupRow("Any genre", modifier = if (o.genre == null || o.genre !in genres) Modifier.focusRequester(first) else Modifier) { pick(null) }
                genres.forEach { g -> PopupRow(g, modifier = if (g == o.genre) Modifier.focusRequester(first) else Modifier) { pick(g) } }
            }
        }
        return
    }
    PopupPanel(onDismiss, "Surprise me", body = o.describe(), swallowHeldOk = false, width = 260.dp) {
        val first = rememberFirstFocus()
        val kinds = listOf(SurpriseOptions.FILM, SurpriseOptions.SERIES, SurpriseOptions.EITHER)
        PopupRow(
            "Pick from",
            when (o.kind) { SurpriseOptions.SERIES -> "Series"; SurpriseOptions.EITHER -> "Films and series"; else -> "Films" },
            modifier = if (!backFromGenre) Modifier.focusRequester(first) else Modifier,
        ) { onChange(o.copy(kind = next(kinds, o.kind))) }
        PopupRow("Genre", o.genre ?: "Any", modifier = if (backFromGenre) Modifier.focusRequester(first) else Modifier) { pickingGenre = true }
        if (o.kind != SurpriseOptions.SERIES) {
            val lengths = listOf(SurpriseOptions.ANY, SurpriseOptions.SHORT, SurpriseOptions.MEDIUM, SurpriseOptions.LONG)
            PopupRow(
                "Length",
                when (o.length) { SurpriseOptions.SHORT -> "Under 1½ hours"; SurpriseOptions.MEDIUM -> "Under 2 hours"; SurpriseOptions.LONG -> "Over 2 hours"; else -> "Any" },
            ) { onChange(o.copy(length = next(lengths, o.length))) }
        }
        PopupRow("Rating", if (o.minRating > 0) "${o.minRating}+" else "Any") { onChange(o.copy(minRating = next(listOf(0, 6, 7, 8), o.minRating))) }
        PopupRow("Already watched", if (o.includeWatched) "Included" else "Left out") { onChange(o.copy(includeWatched = !o.includeWatched)) }
        if (o != SurpriseOptions()) PopupRow("Reset") { onChange(SurpriseOptions()) }
        PopupRow("Done", accent = true, onClick = onDismiss)
    }
}

/** One titled row of tiles. Plain on purpose: a tile does nothing until it has the remote. */
@Composable
private fun HomeRowView(app: AppState, model: HomeModel, row: HomeRow, isFirst: Boolean, screenWidth: androidx.compose.ui.unit.Dp, tileOpen: Int, quick: Boolean) {
    val repo = app.repo
    val density = LocalDensity.current
    val startPx = with(density) { ContentStart.toPx() }
    val background = Blush.colors.background
    val isChips = row.chips != null
    // Only poster rows grow a wide thumbnail under the remote; chips, numbered posters and cards keep their size.
    val growsWide = !row.playOnClick && !isChips && !row.numbered && tileOpen != TilePosters
    val tileWidth = when { isChips -> ChipWidth; row.numbered -> NumberedWidth; row.playOnClick -> CardWidth; else -> PosterWidth }
    val scope = rememberCoroutineScope()
    val total = (row.chips?.size ?: row.items.size) + (if (row.more != null) 1 else 0)
    var viewportPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val focusedIdx = remember { IntArray(1) }
    val slideJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    // Thumbnails of the next couple of tiles are fetched as the remote arrives, so they are ready when it gets there.
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefetch = remember { arrayOfNulls<coil3.request.Disposable>(6) }
    val prefetchUrl = remember { arrayOfNulls<String>(6) }
    val wideSize = with(density) { coil3.size.Size(WideWidth.roundToPx(), PosterHeight.roundToPx()) }
    fun prefetchAround(i: Int) {
        // Posters that never open have no wide picture to get ready.
        if (tileOpen == TilePosters) return
        val loader = coil3.SingletonImageLoader.get(context)
        // Asks for slot's picture unless that very one is already asked for (so holding a key does not cancel and restart it).
        fun want(slot: Int, item: BaseItemDto?) {
            val url = item?.let { repo.thumbUrl(it, 640) ?: repo.posterUrl(it, 640) }
            if (url == prefetchUrl[slot]) return
            prefetch[slot]?.dispose()
            prefetchUrl[slot] = url
            prefetch[slot] = url?.let { loader.enqueue(coil3.request.ImageRequest.Builder(context).data(it).size(wideSize).build()) }
        }
        // Along this row: the next two tiles and the one before.
        var slot = 0
        for (j in intArrayOf(i + 1, i + 2, i - 1)) { want(slot++, if (!growsWide) null else row.items.getOrNull(j)) }
        slot = 3
        // Up and down: the tile at the left of the row above and of the row below, which is where the remote lands, so it is
        // already there (not blank) when it arrives. This also runs from the Continue watching row.
        val at = model.rows.indexOf(row)
        for (r in intArrayOf(at - 1, at + 1)) {
            val other = model.rows.getOrNull(r)
            want(slot++, if (other == null || other.playOnClick || other.numbered || other.chips != null) null else other.items.getOrNull(other.state.firstVisibleItemIndex.coerceAtLeast(0)))
        }
    }
    // How many items (up to six, View all counting as one) sit on screen when the row is scrolled as far as it goes; a row
    // with no more than that fits as it is and does not scroll. Sums are in whole pixels, as the row lays itself out.
    val (lastStart, endPad) = with(density) {
        val width = if (viewportPx > 0) viewportPx else LocalWindowInfo.current.containerSize.width
        val room = width - ContentStart.roundToPx()
        // The focused poster tile is wide, so the last window must have room for one tile that size.
        val fit = if (!growsWide) room else room - (WideWidth.roundToPx() - PosterWidth.roundToPx())
        val gap = 12.dp.roundToPx()
        fun widthOf(index: Int) = if (row.more != null && index == total - 1) 112.dp.roundToPx() else tileWidth.roundToPx()
        var window = 0
        var used = 0
        while (window < minOf(6, total)) {
            val need = used + widthOf(total - 1 - window) + (if (window > 0) gap else 0)
            if (need > fit) break
            used = need
            window++
        }
        if (window == 0) { window = 1; used = widthOf(total - 1) }
        // A little spare room at the end, so the row can always reach its last position.
        val pad = (room - used).coerceAtLeast(22.dp.roundToPx()) + 8.dp.roundToPx()
        (total - window).coerceAtLeast(0) to pad.toDp()
    }
    /** Slides the row so item [index] sits at the heading (the content's left edge). Focus itself never scrolls a row. */
    suspend fun alignTo(index: Int, base: Int = 210) {
        val st = row.state
        // Worked out in absolute terms, not from where tiles happen to be drawn at this instant. In the finished layout every
        // tile before the target is a poster, so the target sits at index * (tile + gap) from the start; the row is at
        // (first visible index * (tile + gap) + how far into it) now. The focused tile being wide, or growing, or shrinking,
        // or the picture not having been re-measured yet, cannot change either figure.
        val unit = with(density) { tileWidth.roundToPx() + 12.dp.roundToPx() }
        var distance = (index * unit - (st.firstVisibleItemIndex * unit + st.firstVisibleItemScrollOffset)).toFloat()
        // A list keeps its first visible tile where it is and lays everything before it backwards from there. So when the
        // target is BEFORE the first visible tile (moving left), the target growing wide pushes its own left edge further out
        // to the left. That is known in advance, so the row starts sliding straight away (no waiting for the new layout) by the
        // distance to where the target will end up: where it is now, plus what the tiles from it up to the anchor are about to
        // change by (the target grows to wide, any wide tile in between shrinks to a poster).
        // (Any earlier slide has been cancelled by now, so the row is just where it is drawn.)
        if (growsWide && index < st.firstVisibleItemIndex) {
            val info = st.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            if (info != null) {
                // Re-anchor the list on the target, exactly where it is drawn now (so nothing moves yet). The list lays
                // things out from its anchor: anchored on the tile being left, the target growing wide pushes its own left
                // edge out to the left and everything to the right jumps back, then slides on again. Anchored on the target,
                // it grows to the right instead, as a forward move does, and only the slide itself is seen. The offset is
                // read from the layout as it stands, so this agrees with whichever layout the list is showing.
                st.requestScrollToItem(index, -info.offset)
                distance = info.offset.toFloat()
            } else {
                val posterPx = with(density) { PosterWidth.roundToPx() }
                val widePx = with(density) { WideWidth.roundToPx() }
                distance -= (widePx - posterPx)
            }
        }
        // Longer slides take a little longer, so they do not whip across.
        val dp = kotlin.math.abs(distance) / density.density
        val duration = (base * kotlin.math.sqrt((dp / 128f).coerceIn(1f, 1.3f))).toInt()
        if (kotlin.math.abs(distance) > 0.5f) st.animateScrollBy(distance, tween(duration, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0.1f, 1f)))
    }

    // ----- Quicker row movement (Settings → Home) -----
    // The remote is moved along the row from here rather than left to find its own way. A tap is never thrown away: if the
    // tile it is heading for is not on screen yet, the press is remembered and taken the moment the tile arrives. And the
    // remote can no longer slip out to the side bar from the middle of a row when Left outruns the row.
    val dest = remember { IntArray(1) { -1 } }
    val drain = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val tapAt = remember { LongArray(1) }
    fun keyAt(index: Int): String? {
        val chips = row.chips
        if (chips != null) return chips.getOrNull(index)?.let { "chip-${row.key}-${it.label}" }
        if (index == row.items.size) return if (row.more != null) "more-${row.key}" else null
        return row.items.getOrNull(index)?.let { tileKey(row.key, it.id) }
    }
    fun quickMove(e: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val dir = when (e.key) { Key.DirectionRight -> 1; Key.DirectionLeft -> -1; else -> return false }
        val held = e.nativeKeyEvent.repeatCount > 0
        val now = android.os.SystemClock.uptimeMillis()
        // Presses close together (or a held key) get a slightly shorter slide.
        val rapid = held || now - tapAt[0] < 260
        if (!held) tapAt[0] = now
        val running = dest[0] >= 0 && drain[0]?.isActive == true
        val from = if (running) dest[0] else focusedIdx[0]
        val next = from + dir
        // At either end the row is left as normal (Left from the first tile opens the side bar), except by a held key or
        // while remembered presses are still being taken.
        if (next !in 0 until total) return running || (next < 0 && held)
        val st = row.state
        val slot = with(density) { tileWidth.roundToPx() + 12.dp.roundToPx() }
        val behind = kotlin.math.abs(minOf(from, lastStart) * slot - (st.firstVisibleItemIndex * slot + st.firstVisibleItemScrollOffset)) / slot.toFloat()
        // A held key is paced by the row (its repeats come faster than a row can slide), and never runs ahead of the remote,
        // so it stops where you let go. Taps are all taken, up to five ahead.
        if (held && (running || behind > 3.0f)) return true
        if (!held && kotlin.math.abs(next - focusedIdx[0]) > 5) return true
        val key = keyAt(next) ?: return false
        if (!running && model.focus.requestKey(key)) {
            focusedIdx[0] = next
            slideJob[0]?.cancel()
            slideJob[0] = scope.launch { alignTo(minOf(next, lastStart), if (rapid) 160 else 180) }
            return true
        }
        // The tile isn't there to take the remote yet: remember where this run of presses is heading.
        dest[0] = next
        if (!running) drain[0] = scope.launch {
            var waited = 0
            while (dest[0] >= 0 && dest[0] != focusedIdx[0] && waited < 45) {
                withFrameNanos { }
                // Up, Down or Back since: the remote has gone elsewhere, so the rest of the run is forgotten.
                val last = model.lastKey
                if (last != Key.DirectionLeft && last != Key.DirectionRight) break
                val n = focusedIdx[0] + (if (dest[0] > focusedIdx[0]) 1 else -1)
                val k = keyAt(n) ?: break
                if (model.focus.requestKey(k)) {
                    focusedIdx[0] = n
                    waited = 0
                    slideJob[0]?.cancel()
                    slideJob[0] = scope.launch { alignTo(minOf(n, lastStart), 160) }
                } else waited++
            }
            dest[0] = -1
        }
        return true
    }

    // A title dropped from the row (Continue watching): the row is put back so the tile the remote is on sits at the heading. Left alone, the
    // list keeps whatever was first on screen, which can leave the tile before the remote half behind the side bar and out of reach.
    var shownTotal by remember { androidx.compose.runtime.mutableIntStateOf(total) }
    LaunchedEffect(total) {
        if (total < shownTotal) {
            val at = row.items.indexOfFirst { tileKey(row.key, it.id) == model.focus.key }.coerceAtLeast(0)
            focusedIdx[0] = at
            slideJob[0]?.cancel()
            row.state.scrollToItem(minOf(at, lastStart))
        }
        shownTotal = total
    }

    // A row of the first screenful fades in when it first arrives (rows arrive top-down as they load, Continue watching first).
    val fadeIn = remember { if (model.rows.indexOf(row) in 0..4 && model.revealed.add(row.key)) androidx.compose.animation.core.Animatable(0f) else null }
    if (fadeIn != null) LaunchedEffect(Unit) { delay(model.rows.indexOf(row).coerceAtLeast(0) * 110L); fadeIn.animateTo(1f, tween(320, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) }
    Column(
        modifier = if (fadeIn != null) Modifier.graphicsLayer { alpha = fadeIn.value } else Modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (row.title.isNotBlank()) Text(row.title, style = BlushType.rowHeader, modifier = Modifier.padding(start = ContentStart))
        PivotScrolling(FocusDoesNotScroll) {
            LazyRow(
                state = row.state,
                // Tiles scrolled off to the left fade away under the side bar (a cheap wash over the edge).
                modifier = Modifier.onSizeChanged { viewportPx = it.width }
                    // Left and Right move focus as normal, and the row is slid so the focused tile is at the heading,
                    // except that it stops once the last six items are showing.
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (quick) return@onPreviewKeyEvent quickMove(e)
                        val next = when (e.key) { Key.DirectionRight -> focusedIdx[0] + 1; Key.DirectionLeft -> focusedIdx[0] - 1; else -> return@onPreviewKeyEvent false }
                        // Holding Left to run back along a row stops at the first tile: only a fresh press opens the side bar from there.
                        if (next < 0 && e.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent true
                        if (next !in 0 until total) return@onPreviewKeyEvent false
                        // Pace: focus must not run ahead of the row. If the row is still more than a slot or so (a held key) or two and
                        // a half (taps) short of where it is heading for the tile that has the remote, the press is ignored. Going
                        // left, the tile to move to is off to the left and not built unless the row keeps up (and the remote then
                        // slips out to the side bar from the middle of the row), so that is kept tight. Going right, the next tile
                        // is always on screen, so the row may be a few slots behind: this is what makes Right as quick as Left.
                        val st = row.state
                        val slot = with(density) { tileWidth.roundToPx() + 12.dp.roundToPx() }
                        val behind = kotlin.math.abs(minOf(focusedIdx[0], lastStart) * slot - (st.firstVisibleItemIndex * slot + st.firstVisibleItemScrollOffset)) / slot.toFloat()
                        val held = e.nativeKeyEvent.repeatCount > 0
                        val limit = if (e.key == Key.DirectionRight) (if (held) 3.0f else 3.5f) else 1.2f
                        if (behind > limit) return@onPreviewKeyEvent true
                        // Stop the slide before this one outright (rather than letting the new one take over from it a moment later):
                        // two scrolls and a re-anchor at the same instant is what threw the row out when presses came quickly.
                        slideJob[0]?.cancel()
                        slideJob[0] = scope.launch { alignTo(minOf(next, lastStart)) }
                        false
                    }
                    // (No wash over the left edge any more: there is no side bar for tiles to slide under.)
                    ,
                // The row can scroll until its last two items (the last tile and View all) are at the left, so the tile
                // at the left always lines up with the heading and View all is never left on its own.
                contentPadding = PaddingValues(start = ContentStart, end = endPad, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.chips?.let { chips ->
                    itemsIndexed(chips, key = { _, c -> "chip-${row.key}-${c.label}" }) { i, chip ->
                        val key = "chip-${row.key}-${chip.label}"
                        ChipTile(
                            chip,
                            onClick = {
                                chip.spec?.let { spec ->
                                    app.categories[key] = spec
                                    model.leaving = true
                                    app.go(Route.Category(key))
                                }
                            },
                            onFocused = { model.focus.remember(key); model.heroFocused = false; focusedIdx[0] = i },
                            modifier = Modifier.restorableFocus(model.focus, key),
                        )
                    }
                }
                itemsIndexed(row.items, key = { _, it -> tileKey(row.key, it.id) }) { i, item ->
                    val key = tileKey(row.key, item.id)
                    val tileModifier = Modifier.restorableFocus(model.focus, key, isDefault = isFirst && i == 0 && model.featured.isEmpty())
                    val onFocused = { model.focus.remember(key); model.heroFocused = false; focusedIdx[0] = i; prefetchAround(i) }
                    val openMenu = { model.menu = row to item }
                    val onClick = {
                        if (row.playOnClick) {
                            // The details page goes underneath the player, so Back from the player lands there.
                            app.go(Route.Details(if (item.type == BaseItemKind.EPISODE) item.seriesId ?: item.id else item.id))
                            app.go(Route.Player(item))
                        } else {
                            model.leaving = true
                            app.go(Route.Details(item.id, preview = item))
                        }
                    }
                    if (row.numbered) {
                        NumberedTile(repo, item, i + 1, onClick = onClick, onFocused = onFocused, blockLeft = i == 0, modifier = tileModifier, onLongPress = openMenu)
                    } else if (row.playOnClick) {
                        // Continue watching: wide, with episode details underneath.
                        val episode = item.type == BaseItemKind.EPISODE
                        TitleCard(
                            title = item.displayTitle(),
                            imageUrl = repo.thumbUrl(item, 520),
                            logoUrl = if (episode || repo.hasThumb(item)) null else repo.logoUrl(item, 500),
                            imageHasTitle = episode || repo.hasThumb(item),
                            width = CardWidth,
                            progress = item.progress(),
                            caption = (if (episode) listOfNotNull(item.seriesName, item.episodeCode()) else listOfNotNull(item.timeLeft()))
                                .joinToString(" · ").ifEmpty { null },
                            onClick = onClick,
                            onFocused = { onFocused(); model.preview?.hover(key, item) },
                            overlay = { model.preview?.let { PreviewSlot(it, key, repo, item, logoArea = 2600f, logoMaxWidth = 100.dp, logoMaxHeight = 34.dp, inset = 8.dp) } },
                            modifier = tileModifier.onFocusChanged { if (!it.hasFocus && !model.leaving) model.preview?.release(key) }.animateItem(
                                fadeInSpec = null,
                                // No sliding into the gap: the row is re-aligned in one step when a title is dropped (see above).
                                placementSpec = null,
                                fadeOutSpec = null,
                            ),
                            onLongPress = openMenu,
                        )
                    } else {
                        HoverPosterTile(
                            repo = repo,
                            item = item,
                            tileKey = key,
                            preview = model.preview,
                            onClick = onClick,
                            onFocused = onFocused,
                            lastKey = { model.lastKey },
                            leavesRow = { key -> (key == Key.DirectionRight && i == row.items.size - 1 && row.more != null) || (key == Key.DirectionLeft && i == 0) || key == Key.Back || model.leaving },
                            prevThumb = { model.prevThumb },
                            setPrevThumb = { model.prevThumb = it },
                            openMode = tileOpen,
                            onLongPress = openMenu,
                            modifier = tileModifier,
                        )
                    }
                }
                val more = row.more
                if (more != null) {
                    item(key = "more") {
                        val moreKey = "more-${row.key}"
                        val openAll = { model.leaving = true; app.go(Route.Category(row.key)) }
                        val moreModifier = Modifier
                            .restorableFocus(model.focus, moreKey)
                            .onFocusChanged { if (it.hasFocus) { focusedIdx[0] = row.items.size; model.focus.remember(moreKey); model.heroFocused = false } }
                        if (row.playOnClick) ViewAllCard(CardWidth, onClick = openAll, modifier = moreModifier)
                        else ViewAllCard(PosterWidth, aspect = 2f / 3f, onClick = openAll, modifier = moreModifier)
                    }
                }
            }
        }
    }
}

private val PosterHeight = PosterWidth * 1.5f
private val WideWidth = PosterHeight * 16f / 9f

private const val OpenNone = 0     // arriving from below (Up), or leaving up or down: no animation at all
private const val OpenFade = 1     // (no longer used: along a row the thumbnail now simply changes)
private const val OpenCut = 3      // along a row: the thumbnail is just there, no fade
private const val OpenReveal = 2   // arriving from above (Down): the tile itself extends the last fifth of its width

/**
 * A poster that, while it has the remote, IS its wide thumbnail: the tile takes the wider room itself (the ring goes round the
 * thumbnail). Along a row the layout changes in one step and only the drawing is eased (nothing is re-measured): the new tile's
 * thumbnail fades up while the tile just left fades back to its poster, so it reads as one thumbnail handing over to the next.
 * Arriving from above, the tile itself extends the last fifth (ring, picture and neighbours moving together); that happens
 * once, briefly, per row change. Arriving from below nothing is animated. Only the tile that has the remote (or has just lost
 * it) carries any animation, so a row of tiles stays as light as before.
 */
@Composable
private fun HoverPosterTile(
    repo: Repo, item: BaseItemDto, tileKey: String, preview: PreviewController?, onClick: () -> Unit, onFocused: () -> Unit, lastKey: () -> Key?,
    leavesRow: (Key?) -> Boolean, prevThumb: () -> String?, setPrevThumb: (String?) -> Unit,
    /** [TileNow] or [TilePosters]. */
    openMode: Int,
    onLongPress: (() -> Unit)?,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    // Whether the tile is showing as its wide thumbnail: it has the remote (unless posters never open).
    var open by remember { mutableStateOf(false) }
    var lingering by remember { mutableStateOf(false) }
    var stayWide by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(OpenNone) }
    var extend by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var under by remember { mutableStateOf<String?>(null) }
    // The poster's own colour fills the tile until the poster arrives.
    val tint = remember(item.id) { item.posterTint() }
    Box(Modifier.onFocusChanged {
        if (it.hasFocus != focused) {
            val key = lastKey()
            mode = if (it.hasFocus) {
                when (key) { Key.DirectionLeft, Key.DirectionRight -> OpenCut; Key.DirectionDown, Key.DirectionUp -> OpenReveal; else -> OpenNone }
            } else {
                when (key) { Key.DirectionLeft, Key.DirectionRight -> OpenCut; else -> OpenNone }
            }
            extend = if (it.hasFocus && mode == OpenReveal) 0f else 1f
            // Along a row the new thumbnail fades up OVER the one just left (same pixels), never through the dark tile.
            // Whichever order the old and new tile are told in, the new one reads what the last one to gain the remote showed.
            if (it.hasFocus) {
                val mine = repo.thumbUrl(item, 640) ?: repo.posterUrl(item, 640)
                under = if (mode == OpenFade) prevThumb() else null
                setPrevThumb(mine)
            }
            // The tile just left goes straight back to its poster (no fading of its own): only the tile with the remote fades.
            // Except when the remote leaves the row sideways (to the side bar or View all): then it stays large, just without its ring.
            stayWide = !it.hasFocus && open && leavesRow(key)
            focused = it.hasFocus
            // Opening a title keeps the poster as it is (the preview too) while the page fades, so nothing flashes back to the thumbnail.
            open = (it.hasFocus && openMode != TilePosters) || (!it.hasFocus && open && leavesRow(Key.Enter))
            // The preview belongs to the tile with the remote: it starts after a rest here and goes the moment the remote leaves.
            if (openMode != TilePosters) { if (it.hasFocus) preview?.hover(tileKey, item) else if (!leavesRow(Key.Enter)) preview?.release(tileKey) }
        }
    }) {
        FocusSurface(
            onClick = onClick,
            onFocused = onFocused,
            onLongPress = onLongPress,
            modifier = modifier.height(PosterHeight).width(
                when {
                    !open && !stayWide -> PosterWidth
                    mode == OpenReveal -> WideWidth * (0.93f + 0.07f * extend)
                    else -> WideWidth
                },
            ),
            background = tint ?: Blush.colors.surface,
            // No zoom: the tile already grows, and a zoom would push its left edge off the heading. (A poster that never
            // opens gets a small one instead, so the remote is easy to follow.)
            focusedScale = if (openMode == TilePosters) 1.05f else 1f,
        ) {
            // The poster is only there while the tile is a poster (including while a tile just left fades back to it).
            if (!open && !stayWide) {
                Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).size(PosterWidth, PosterHeight)) {
                    val poster = repo.posterUrl(item)
                    if (poster == null) Text(item.displayTitle(), style = BlushType.label, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp), maxLines = 3)
                    RemoteImage(poster, Modifier.fillMaxSize(), crossfade = false)
                }
            }
            if (open || stayWide || lingering) TileThumb(repo, item, open, mode, under, preview, tileKey, onExtend = { extend = it }) { lingering = false; under = null }
            ProgressLine(item.progress())
        }
    }
}

@Composable
private fun BoxScope.TileThumb(repo: Repo, item: BaseItemDto, on: Boolean, mode: Int, under: String?, preview: PreviewController?, tileKey: String, onExtend: (Float) -> Unit, onGone: () -> Unit) {
    // A fade along a row starts a little way in (so the old picture is never fully there for even one frame), moves quickly
    // at first and settles gently.
    val t = remember { androidx.compose.animation.core.Animatable(if (mode == OpenNone || mode == OpenCut) 1f else if (mode == OpenFade) 0.85f else 0f) }
    LaunchedEffect(on) {
        when (mode) {
            OpenNone, OpenCut -> t.snapTo(1f)
            OpenFade -> t.animateTo(1f, tween(190, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0.1f, 1f)))
            else -> t.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) { onExtend(value) }
        }
        onGone()
    }
    // The thumbnail of the tile just left is held underneath, fully there, while this one fades up over it: a plain cross-fade
    // with no dip. (Fading both would let the dark tile show through in the middle.) It is dropped once the fade is done.
    if (on && mode == OpenFade && under != null) {
        Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).size(WideWidth, PosterHeight)) {
            RemoteImage(under, Modifier.fillMaxSize(), crossfade = false)
        }
    }
    Box(
        Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).size(WideWidth, PosterHeight)
            .graphicsLayer {
                alpha = if (mode == OpenFade) t.value else 1f
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            },
    ) { HoverThumb(repo, item, crossfade = mode == OpenNone)
        if (on && preview != null) PreviewSlot(preview, tileKey, repo, item)
    }
}

@Composable
private fun BoxScope.HoverThumb(repo: Repo, item: BaseItemDto, crossfade: Boolean = true) {
    // Fades in over the plain tile once it arrives (usually already fetched), rather than cutting in.
    RemoteImage(repo.thumbUrl(item, 640) ?: repo.posterUrl(item, 640), Modifier.fillMaxSize(), crossfade = crossfade)
    // A title with no thumbnail of its own gets its logo (or name) over the picture it does have.
    if (!repo.hasThumb(item)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to androidx.compose.ui.graphics.Color.Transparent, 1f to androidx.compose.ui.graphics.Color(0xCC090A0E))))
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.BottomStart) {
            val logo = repo.logoUrl(item, 500)
            if (logo != null) app.blush.tv.ui.components.LogoImage(logo, area = 5200f, maxWidth = 150.dp, maxHeight = 50.dp)
            else Text(item.displayTitle(), style = BlushType.title, maxLines = 2)
        }
    }
}
