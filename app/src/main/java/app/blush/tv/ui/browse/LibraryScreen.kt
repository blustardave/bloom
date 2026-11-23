package app.blush.tv.ui.browse

import app.blush.tv.ui.components.GentleScrolling
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.blush.tv.data.Repo
import app.blush.tv.ui.components.LoadingDots
import app.blush.tv.ui.AppState
import app.blush.tv.ui.ContentEnd
import app.blush.tv.ui.ContentStart
import app.blush.tv.ui.GridMemory
import androidx.compose.ui.layout.layout
import app.blush.tv.ui.LocalRailReturn
import app.blush.tv.ui.PageTop
import app.blush.tv.ui.Route
import app.blush.tv.ui.CategorySpec

import app.blush.tv.ui.components.DropdownRow
import app.blush.tv.ui.components.Icon
import app.blush.tv.ui.components.Icons
import app.blush.tv.ui.components.LiteTile
import app.blush.tv.ui.components.OptionRow
import app.blush.tv.ui.components.Pill
import app.blush.tv.ui.components.ProgressLine
import app.blush.tv.ui.components.RemoteImage
import app.blush.tv.ui.components.SecondaryButton
import app.blush.tv.ui.components.FocusSurface
import app.blush.tv.ui.components.SidePanel
import app.blush.tv.ui.components.fadeInOnAppear
import app.blush.tv.ui.components.fadeInOver
import app.blush.tv.ui.details.PictureTile
import app.blush.tv.ui.displayTitle
import app.blush.tv.ui.progress
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.theme.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import java.util.UUID

// ---------------- What a library can be narrowed and ordered by ----------------

private enum class Sort(val label: String, val by: ItemSortBy, val descending: Boolean, val icon: androidx.compose.ui.graphics.vector.ImageVector, val short: String = label) {
    Name("A–Z", ItemSortBy.SORT_NAME, false, Icons.SortAz),
    Added("Recently added", ItemSortBy.DATE_CREATED, true, Icons.PlusCircle, "Added"),
    Released("Newest first", ItemSortBy.PREMIERE_DATE, true, Icons.Calendar, "Newest"),
    Oldest("Oldest first", ItemSortBy.PREMIERE_DATE, false, Icons.History, "Oldest"),
    Rating("Highest rated", ItemSortBy.COMMUNITY_RATING, true, Icons.Star, "Rating"),
    Length("Shortest first", ItemSortBy.RUNTIME, false, Icons.Clock, "Length"),
    Random("Random", ItemSortBy.RANDOM, false, Icons.Shuffle),
}

private enum class Show(val label: String) { All("Everything"), Unwatched("Not watched yet"), Watched("Already watched") }

private val decades = (2020 downTo 1950 step 10).toList()

/** Everything the library can be narrowed by. Several genres or decades can be chosen at once. */
private data class LibraryFilters(
    val sort: Sort = Sort.Name,
    val show: Show = Show.All,
    val favourites: Boolean = false,
    /** Titles must have at least one of these genres. */
    val genres: Set<String> = emptySet(),
    /** "Specific feel": titles must have every one of these genres. */
    val feel: Set<String> = emptySet(),
    /** Titles from any of these decades. */
    val decades: Set<Int> = emptySet(),
) {
    val active get() = show != Show.All || favourites || genres.isNotEmpty() || feel.isNotEmpty() || decades.isNotEmpty()

    /** A short summary for the toolbar, e.g. "Highest rated  ·  Not watched yet  ·  Action, Comedy". */
    fun summary() = listOfNotNull(
        sort.label,
        show.takeIf { it != Show.All }?.label,
        "Watchlist".takeIf { favourites },
        genres.sorted().joinToString(", ").takeIf { it.isNotEmpty() },
        feel.sorted().joinToString(" + ").takeIf { it.isNotEmpty() },
        decades.sorted().joinToString(", ") { "${it}s" }.takeIf { it.isNotEmpty() },
    ).joinToString("  ·  ")
}

/**
 * What the page is showing: the filters and the layout. Kept while the app runs, so Back from a title lands on the same view;
 * choosing another section from the side bar (or opening the library afresh) starts over.
 */
private fun LibraryFilters.cleared() = LibraryFilters(sort = sort)

/** How to get the remote back onto the title it was last on; each grid fills this in. */
@Stable
private class TileReturn {
    var go: () -> Boolean = { false }
}

private data class LibraryView(val filters: LibraryFilters = LibraryFilters(), val wide: Boolean = false, val byGenre: Boolean = false)

/** What a browse page lists: one library, or everything on the watchlist (the user's favourites across the whole server). */
private class BrowseSource(
    val id: String,
    val title: String,
    val types: List<BaseItemKind>,
    val parentId: UUID?,
    val watchlist: Boolean,
    /** How many titles there are before any filter (null when that isn't known up front). */
    val count: suspend () -> Int?,
    /** How the count beside the title reads, e.g. "312 films". */
    val countLabel: (Int) -> String,
)

private suspend fun Repo.libraryPage(src: BrowseSource, f: LibraryFilters, genres: List<String>, start: Int, count: Int) = browsePage(
    types = src.types,
    sortBy = f.sort.by,
    descending = f.sort.descending,
    genres = genres,
    unplayedOnly = f.show == Show.Unwatched,
    playedOnly = f.show == Show.Watched,
    favouritesOnly = f.favourites || src.watchlist,
    years = f.decades.sorted().flatMap { (it until it + 10).toList() },
    parentId = src.parentId,
    start = start,
    limit = count,
)

/** Shown as a library's count while it is still being worked out (see [FeelPager]). */
private const val UnknownTotal = Int.MAX_VALUE

/**
 * Pages through titles that have EVERY genre in [needed]. The server can only be asked for titles with any of a list of genres, so it is
 * asked for [serverGenres] a hundred at a time (in the chosen order) and the rest of the matching is done here, until a page is full.
 * The total is only known once the end has been reached.
 */
private class FeelPager(
    private val repo: Repo,
    private val src: BrowseSource,
    private val filters: LibraryFilters,
    private val serverGenres: List<String>,
    private val needed: List<String>,
) {
    private var cursor = 0
    private var matched = 0
    private var exhausted = false

    suspend fun page(count: Int): Pair<List<BaseItemDto>, Int> {
        val out = ArrayList<BaseItemDto>()
        while (out.size < count && !exhausted) {
            val (raw, total) = repo.libraryPage(src, filters, serverGenres, cursor, 100)
            cursor += raw.size
            if (raw.isEmpty() || cursor >= total) exhausted = true
            raw.filterTo(out) { item -> needed.all { it in item.genres.orEmpty() } }
        }
        matched += out.size
        return out to (if (exhausted) matched else UnknownTotal)
    }
}

// ---------------- The page ----------------

private const val PosterColumns = 8
private const val WideColumns = 4
private val ToolbarHeight = 24.dp
private val ControlShape = RoundedCornerShape(50)

/** The order of the genre rows in "By genre" mode. */
private enum class GenreSort(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val short: String = label) {
    Name("A–Z", Icons.SortAz),
    Most("Most items", Icons.SortDesc, "Most"),
    Least("Least items", Icons.SortAsc, "Least"),
    Random("Random", Icons.Shuffle),
}

/** One library's titles. */
@Composable
fun LibraryScreen(app: AppState, library: BaseItemDto) {
    val repo = app.repo
    val src = remember(library.id) {
        BrowseSource(library.id.toString(), library.name.orEmpty(), repo.libraryTypes(library), library.id, false, { repo.libraryCount(library) }) { countText(it, library) }
    }
    BrowsePage(app, src)
}

/** Everything saved to the watchlist, browsed exactly like a library: sort, filter, posters or wide tiles, one grid or a row per genre. */
@Composable
fun WatchlistScreen(app: AppState) {
    val src = remember {
        BrowseSource("watchlist", "Watchlist", listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.BOX_SET), null, true, { null }) { n ->
            "%,d %s".format(n, if (n == 1) "title" else "titles")
        }
    }
    BrowsePage(app, src)
}

@Composable
private fun BrowsePage(app: AppState, src: BrowseSource) {
    val repo = app.repo
    val id = src.id
    // The view (filters and layout) is kept for the visit: coming back from a title finds it as it was.
    val saved = remember { app.libraryFilters[id] as? LibraryView ?: LibraryView(LibraryFilters(sort = if (src.watchlist) Sort.Added else Sort.Name)) }
    var filters by remember { mutableStateOf(saved.filters) }
    // Two independent choices: posters or wide tiles, and one grid or a row per genre.
    // Posters only: the poster / thumbnail switch is gone (the icon toggle at the right swaps the whole library and the genre rows).
    val wide = false
    var byGenre by remember { mutableStateOf(saved.byGenre) }
    var genreSort by remember { mutableStateOf(GenreSort.Name) }
    LaunchedEffect(filters, wide, byGenre) { app.libraryFilters[id] = LibraryView(filters, wide, byGenre) }
    // The grid follows the filters a moment after the last change, so ticking several genres in a row fetches once.
    var applied by remember { mutableStateOf(filters) }
    LaunchedEffect(filters) { delay(300); applied = filters }
    // Whether a freshly built grid puts the remote on a title. Only the first build does (and the "nothing matches" button's Clear);
    // after the toolbar or the panel has been used, the remote stays where it is.
    var grab by remember { mutableStateOf(true) }
    var genres by remember { mutableStateOf<List<String>>(emptyList()) }
    var total by remember { mutableStateOf<Int?>(null) }
    // Which dropdown is open ("genres" or "sort"), and where its button is, so the menu opens right under it.
    var menu by remember { mutableStateOf<String?>(null) }
    var closing by remember { mutableStateOf(false) }
    var filterFocused by remember { mutableStateOf(false) }
    var sortFocused by remember { mutableStateOf(false) }
    var gridHasFocus by remember { mutableStateOf(false) }
    val anchors = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    // Several genres at once: titles with ANY of them, or with ALL of them (the second is held in `feel`).
    var matchAll by remember { mutableStateOf(saved.filters.feel.isNotEmpty()) }
    val filterButton = remember { FocusRequester() }
    val sortButton = remember { FocusRequester() }
    // Up from the top tiles lands on "All" in the first toggle.
    val allButton = remember { FocusRequester() }
    // Down from the toolbar always lands on the first title.
    val entry = remember { FocusRequester() }
    // The way back to the title the remote was on (Down from the toolbar, and Right out of the side bar).
    val back = remember { TileReturn() }
    val scope = rememberCoroutineScope()
    val railReturn = LocalRailReturn.current
    DisposableEffect(railReturn) {
        railReturn?.restore = { back.go() }
        onDispose { if (railReturn?.restore != null) railReturn.restore = null }
    }

    LaunchedEffect(Unit) {
        genres = runCatching { repo.genres(src.parentId) }.getOrDefault(emptyList())
        total = src.count()
    }
    fun focusFilterButton() {
        scope.launch { delay(30); runCatching { filterButton.requestFocus() } }
    }
    fun closeMenu() {
        val was = menu ?: return
        if (closing) return
        // Closing the menu applies any choice still waiting out its short delay.
        applied = filters
        // The menu is hidden but kept until the remote has really arrived on its button, so it never passes over another control.
        closing = true
        val button = if (was == "sort") sortButton else filterButton
        scope.launch {
            val on = { if (was == "sort") sortFocused else filterFocused }
            var tries = 0
            // First while the menu is still there, then (if that never took) once it has gone; meanwhile nothing else on the page can take the remote.
            while (tries < 3 && !on()) { runCatching { button.requestFocus() }; delay(20); tries++ }
            menu = null
            tries = 0
            // Keeps asking until it takes (the page's own "lost the remote" watchdog would otherwise hand it to the first title).
            while (tries < 40 && !on()) { delay(16); runCatching { button.requestFocus() }; tries++ }
            closing = false
        }
    }

    Box(Modifier.fillMaxSize().background(Blush.colors.background)) {
        Column(Modifier.fillMaxSize().padding(start = ContentStart, top = PageTop)){
            // The title on the left; on the same line at the right, what to show, the genres, the order and the view.
            Row(
                Modifier.fillMaxWidth().padding(end = ContentEnd).onPreviewKeyEvent { e ->
                    if (e.key == Key.DirectionDown && e.type == KeyEventType.KeyDown) (back.go() || runCatching { entry.requestFocus() }.getOrDefault(false)) else false
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(src.title, style = BlushType.headline)
                total?.let { Text("%,d %s".format(it, if (it == 1) "item" else "items"), style = BlushType.label, color = Blush.colors.accent.color, modifier = Modifier.padding(start = 2.dp, top = 5.dp)) }
                Spacer(Modifier.weight(1f))
                val showOptions = if (src.watchlist) listOf("All", "Unwatched", "Watched") else listOf("All", "Unwatched", "Watched", "Watchlist")
                Segmented(
                    showOptions,
                    firstRequester = allButton,
                    quiet = closing,
                    selected = when { filters.favourites -> 3; filters.show == Show.Unwatched -> 1; filters.show == Show.Watched -> 2; else -> 0 },
                    onSelect = { i ->
                        grab = false
                        filters = filters.copy(show = when (i) { 1 -> Show.Unwatched; 2 -> Show.Watched; else -> Show.All }, favourites = i == 3)
                    },
                )
                ToolButton(
                    Icons.Filter, if (filters.genres.size + filters.feel.size > 0) "Genres · ${filters.genres.size + filters.feel.size}" else "Genres",
                    width = 84.dp,
                    quiet = closing && menu != "genres",
                    open = menu == "genres", onClick = { grab = false; menu = if (menu == "genres") null else "genres" },
                    modifier = Modifier.focusRequester(filterButton).onFocusChanged { filterFocused = it.hasFocus }.onGloballyPositioned { anchors["genres"] = it.positionInRoot().let { p -> p.x to (p.y + it.size.height) } },
                )
                // In "By genre" the same button orders the rows (A-Z, most items, least items, random).
                ToolButton(
                    if (byGenre) genreSort.icon else filters.sort.icon, if (byGenre) genreSort.short else filters.sort.short,
                    quiet = closing && menu != "sort",
                    open = menu == "sort", onClick = { grab = false; menu = if (menu == "sort") null else "sort" },
                    modifier = Modifier.widthIn(min = 64.dp).focusRequester(sortButton).onFocusChanged { sortFocused = it.hasFocus }.onGloballyPositioned { anchors["sort"] = it.positionInRoot().let { p -> p.x to (p.y + it.size.height) } },
                )
                SwapToggle(Icons.PosterGrid, Icons.WideGrid, byGenre, onClick = { grab = false; byGenre = !byGenre })
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().weight(1f).onFocusChanged { gridHasFocus = it.hasFocus }) {
                // Each combination is built afresh when it changes; the toolbar above is untouched, so the remote stays on it.
                androidx.compose.runtime.key(applied, wide, byGenre, if (byGenre) genreSort else null) {
                    val takeFocus = grab && menu == null
                    if (byGenre) {
                        GenreRows(
                            app = app,
                            genres = applied.genres.sorted().ifEmpty { genres.filter { it !in applied.feel } },
                            wide = wide,
                            memory = app.gridMemory.getOrPut("lib:${src.id}:$applied:genres:$wide") { GridMemory() },
                            takeFocus = takeFocus,
                            entry = entry,
                            toolbar = allButton,
                            back = back,
                            // A row is one genre's titles under the other filters (the genre choice itself picks which rows there are).
                            order = genreSort,
                            loadRow = { g ->
                                if (applied.feel.isEmpty()) repo.libraryPage(src, applied, listOf(g), 0, 24)
                                else FeelPager(repo, src, applied, listOf(g), (applied.feel + g).sorted()).page(24)
                            },
                            // The whole of one genre (under the same filters), as its own page; Back returns here.
                            onViewAll = { g ->
                                val key = "genre:${src.id}:$applied:$g"
                                val pager = if (applied.feel.isEmpty()) null else FeelPager(repo, src, applied, listOf(g), (applied.feel + g).sorted())
                                app.categories[key] = CategorySpec(g) { start, count -> pager?.page(count) ?: repo.libraryPage(src, applied, listOf(g), start, count) }
                                app.go(Route.Category(key))
                            },
                        )
                    } else {
                        PagedGrid(
                            app = app,
                            wide = wide,
                            keep = "lib:${src.id}:$applied:${if (wide) "wide" else "posters"}".takeIf { applied.sort == Sort.Random },
                            memory = app.gridMemory.getOrPut("lib:${src.id}:$applied:${if (wide) "wide" else "posters"}") { GridMemory() },
                            load = if (applied.feel.isEmpty()) ({ start, count -> repo.libraryPage(src, applied, applied.genres.sorted(), start, count) })
                            else {
                                // Several genres that must all match: paged through by FeelPager (it keeps its own place, so the start is ignored).
                                val pager = remember { FeelPager(repo, src, applied, applied.genres.sorted().ifEmpty { listOf(applied.feel.sorted().first()) }, applied.feel.sorted()) }
                                ({ _, count -> pager.page(count) })
                            },
                            takeFocus = takeFocus,
                            entry = entry,
                            toolbar = allButton,
                            back = back,
                            onTotal = { total = it.takeIf { n -> n != UnknownTotal } },
                            clearFilters = if (applied.active) ({ grab = true; filters = filters.cleared() }) else null,
                            idleEmpty = if (src.watchlist) IdleEmpty(
                                "Your watchlist is empty", "Press the heart on any film or series to save it here.",
                                "Find something to watch" to { app.openTab(Route.Home) },
                            ) else null,
                        )
                    }
                }
            }
        }

        // Back on the titles goes up to the options first; Back again leaves the page for the navigation bar.
        BackHandler(enabled = gridHasFocus && menu == null) { runCatching { allButton.requestFocus() } }
        menu?.let { which ->
            // Registered only while a menu is open, so it comes after (and beats) the page's own Back.
            BackHandler { closeMenu() }
            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
            val menuWidth = if (which == "genres") 150.dp else 138.dp
            val at = anchors[which] ?: (0f to 0f)
            val density = LocalDensity.current
            val maxX = with(density) { (screenWidth - menuWidth - ContentEnd).toPx() }
            Box(Modifier.align(Alignment.TopStart).graphicsLayer { alpha = if (closing) 0f else 1f }.offset { IntOffset(at.first.coerceAtMost(maxX).toInt(), at.second.toInt() + 6.dp.roundToPx()) }) {
                if (which == "genres") GenreMenu(
                    genres = genres, picked = if (matchAll) filters.feel else filters.genres, matchAll = matchAll, width = menuWidth,
                    onMode = { all ->
                        matchAll = all
                        val set = if (matchAll) filters.genres.ifEmpty { filters.feel } else filters.feel.ifEmpty { filters.genres }
                        filters = filters.copy(genres = if (all) emptySet() else set, feel = if (all) set else emptySet())
                    },
                    onPick = { g ->
                        val cur = if (matchAll) filters.feel else filters.genres
                        val next = if (g in cur) cur - g else cur + g
                        filters = if (matchAll) filters.copy(feel = next, genres = emptySet()) else filters.copy(genres = next, feel = emptySet())
                    },
                    onClear = { filters = filters.copy(genres = emptySet(), feel = emptySet()) },
                ) else if (byGenre) SortMenu(
                    GenreSort.entries.map { MenuEntry(it.icon, it.label, it == genreSort) { genreSort = it; closeMenu() } }, menuWidth,
                ) else SortMenu(
                    Sort.entries.map { sort -> MenuEntry(sort.icon, sort.label, sort == filters.sort) { filters = filters.copy(sort = sort); closeMenu() } }, menuWidth,
                )
            }
        }
    }
}

/**
 * A "View all" page: every title in one of Home's categories (a row, a genre, a mood), in a grid of posters. Put together on Home
 * (each category is described once there and kept in [AppState.categories]), so this page only lists what the category says.
 */
@Composable
fun CategoryScreen(app: AppState, key: String) {
    val spec = remember(key) { app.categories[key] }
    val memory = remember(key) { app.gridMemory.getOrPut("category:$key") { GridMemory() } }
    val entry = remember { FocusRequester() }
    val back = remember { TileReturn() }
    var total by remember { mutableStateOf<Int?>(null) }
    val railReturn = LocalRailReturn.current
    DisposableEffect(railReturn) {
        railReturn?.restore = { back.go() }
        onDispose { if (railReturn?.restore != null) railReturn.restore = null }
    }
    Column(Modifier.fillMaxSize().background(Blush.colors.background).padding(start = ContentStart, top = PageTop)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(spec?.title ?: "View all", style = BlushType.headline)
            total?.let { Text(if (it == 1) "1 title" else "$it titles", style = BlushType.label, color = Blush.colors.accent.color, modifier = Modifier.padding(bottom = 4.dp)) }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (spec == null) Message("This list isn't available any more", "Go back to Home and open it again.", null, null)
            else PagedGrid(
                app = app,
                wide = false,
                memory = memory,
                load = spec.load,
                takeFocus = true,
                entry = entry,
                // Nothing above the grid to go up to.
                toolbar = FocusRequester.Cancel,
                back = back,
                onTotal = { total = it.takeIf { n -> n != UnknownTotal } },
                clearFilters = null,
                idleEmpty = IdleEmpty("Nothing here yet", null, null),
                // Most of these lists come in a shuffled order. (The watchlist is always fetched afresh: it changes as hearts are pressed.)
                keep = "category:$key".takeIf { key != "watchlist" },
            )
        }
    }
}

// ---------------- Toolbar: show, genres, sort ----------------

/** A row of choices; the lit one is a grey pill that slides to whichever is chosen. */
@Composable
private fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    firstRequester: FocusRequester? = null,
    height: Dp = ToolbarHeight,
    quiet: Boolean = false,
) {
    val xs = remember { mutableStateMapOf<Int, Int>() }
    val ws = remember { mutableStateMapOf<Int, Int>() }
    val hx = remember { Animatable(0f) }
    val hw = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(selected, xs[selected], ws[selected]) {
        val tx = xs[selected] ?: return@LaunchedEffect
        val tw = ws[selected] ?: return@LaunchedEffect
        if (!placed) { hx.snapTo(tx.toFloat()); hw.snapTo(tw.toFloat()); placed = true }
        else {
            launch { hx.animateTo(tx.toFloat(), androidx.compose.animation.core.spring(1f, 650f)) }
            hw.animateTo(tw.toFloat(), androidx.compose.animation.core.spring(1f, 650f))
        }
    }
    val density = LocalDensity.current
    Box(modifier.height(height).background(Color.White.copy(alpha = 0.07f), ControlShape).padding(2.dp)) {
        if (placed) Box(
            Modifier
                .offset { IntOffset(hx.value.toInt(), 0) }
                .size(with(density) { hw.value.toDp() }, height - 4.dp)
                .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)),
        )
        Row {
            options.forEachIndexed { i, label ->
                FocusSurface(
                    onClick = { onSelect(i) },
                    shape = RoundedCornerShape(50),
                    focusedScale = 1f,
                    ringWidth = 0.dp,
                    background = Color.Transparent,
                    focusedBackground = if (quiet) Color.Transparent else Color(0xFFF5F5F7),
                    modifier = Modifier
                        .height(height - 4.dp)
                        .onGloballyPositioned { xs[i] = it.positionInParent().x.toInt(); ws[i] = it.size.width }
                        .let { if (i == 0 && firstRequester != null) it.focusRequester(firstRequester) else it },
                ) { focused ->
                    Text(
                        label,
                        style = BlushType.caption.copy(fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.W600),
                        color = if (focused && !quiet) Color(0xFF14151B) else if (i == selected) Blush.colors.text else Blush.colors.textMuted,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 10.dp),
                    )
                }
            }
        }
    }
}

/** Two icons with the current one lit in a round highlight; pressing it swaps to the other. */
@Composable
private fun SwapToggle(first: androidx.compose.ui.graphics.vector.ImageVector, second: androidx.compose.ui.graphics.vector.ImageVector, secondOn: Boolean, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        shape = ControlShape,
        focusedScale = 1f,
        ringWidth = 0.dp,
        background = Color.White.copy(alpha = 0.07f),
        focusedBackground = Color.White.copy(alpha = 0.22f),
        modifier = Modifier.height(ToolbarHeight),
    ) { focused ->
        Row(Modifier.align(Alignment.Center).padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(first to !secondOn, second to secondOn).forEach { (icon, on) ->
                Box(
                    Modifier.size(20.dp).background(if (on) (if (focused) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.18f)) else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, if (on) Blush.colors.text else if (focused) Blush.colors.textMuted else Blush.colors.textDim, size = 12.dp)
                }
            }
        }
    }
}

/** Two words with the current one lit; pressing it swaps to the other. */
@Composable
private fun SwapLabels(first: String, second: String, secondOn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(
        onClick = onClick,
        shape = ControlShape,
        focusedScale = 1f,
        ringWidth = 0.dp,
        background = Color.White.copy(alpha = 0.07f),
        focusedBackground = Color.White.copy(alpha = 0.22f),
        modifier = modifier.height(22.dp),
    ) { focused ->
        Row(Modifier.align(Alignment.Center).padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(first to !secondOn, second to secondOn).forEach { (label, on) ->
                Box(
                    Modifier.height(18.dp).background(if (on) (if (focused) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.18f)) else Color.Transparent, RoundedCornerShape(50)).padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, style = BlushType.caption.copy(fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.W600),
                        color = if (on) Blush.colors.text else if (focused) Blush.colors.textMuted else Blush.colors.textDim,
                    )
                }
            }
        }
    }
}

/** A dropdown's button: an icon, its name (or current choice) and a small arrow. A fixed [width] keeps it from changing size as its text changes. */
@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp? = null, quiet: Boolean = false) {
    FocusSurface(
        onClick = onClick,
        shape = ControlShape,
        focusedScale = 1f,
        ringWidth = 0.dp,
        background = Color.White.copy(alpha = if (open) 0.18f else 0.07f),
        focusedBackground = if (quiet) Color.White.copy(alpha = 0.07f) else Color(0xFFF5F5F7),
        modifier = modifier.height(ToolbarHeight).let { if (width != null) it.width(width) else it },
    ) { focused ->
        val ink = if (focused && !quiet) Color(0xFF14151B) else Blush.colors.text
        Row(Modifier.align(Alignment.Center).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, ink, size = 11.dp)
            Text(label, style = BlushType.caption.copy(fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.W600), color = ink, maxLines = 1)
            Icon(Icons.ChevronDown, null, if (focused) ink else Blush.colors.textMuted, Modifier.graphicsLayer { rotationZ = if (open) 180f else 0f }, size = 9.dp)
        }
    }
}

/** The look shared by the dropdown menus: a rounded dark card. Focus cannot leave it except by Back. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MenuCard(width: Dp, content: @Composable () -> Unit) {
    Column(
        Modifier
            .width(width)
            .background(Color(0xF2171820), RoundedCornerShape(10.dp))
            .border(0.75.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(5.dp)
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) { content() }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        focusedScale = 1f,
        ringWidth = 0.dp,
        background = Color.Transparent,
        focusedBackground = Color.White.copy(alpha = 0.14f),
        modifier = modifier.fillMaxWidth(),
    ) { focused ->
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, if (selected) Blush.colors.accent.color else if (focused) Blush.colors.text else Blush.colors.textMuted, size = 11.dp)
            Text(
                label, style = BlushType.caption.copy(fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.W600),
                color = if (selected) Blush.colors.accent.color else Blush.colors.text, maxLines = 1, modifier = Modifier.weight(1f),
            )
            if (selected) Icon(Icons.Check, null, Blush.colors.accent.color, size = 10.dp)
        }
    }
}

private class MenuEntry(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val selected: Boolean, val pick: () -> Unit)

/** The order things come in. One choice, and the menu closes. */
@Composable
private fun SortMenu(entries: List<MenuEntry>, width: Dp) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(50); runCatching { first.requestFocus() } }
    MenuCard(width) {
        entries.forEach { e -> MenuRow(e.icon, e.label, e.selected, onClick = e.pick, modifier = if (e.selected) Modifier.focusRequester(first) else Modifier) }
    }
}

/**
 * Genres: tick as many as you like (the menu stays open). "Any" shows titles with at least one of the ticked genres; "All" only
 * those that have every one of them.
 */
@Composable
private fun GenreMenu(
    genres: List<String>,
    picked: Set<String>,
    matchAll: Boolean,
    width: Dp,
    onMode: (Boolean) -> Unit,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(50); runCatching { first.requestFocus() } }
    MenuCard(width) {
        Row(Modifier.fillMaxWidth().padding(bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            SwapLabels("Any", "All", secondOn = matchAll, onClick = { onMode(!matchAll) }, modifier = Modifier.focusRequester(first))
            Spacer(Modifier.weight(1f))
            // Clears every ticked genre.
            FocusSurface(
                onClick = onClear,
                shape = CircleShape,
                focusedScale = 1f,
                ringWidth = 0.dp,
                background = Color.White.copy(alpha = if (picked.isEmpty()) 0.04f else 0.09f),
                focusedBackground = Color(0xFFF5F5F7),
                modifier = Modifier.size(22.dp),
            ) { focused ->
                Icon(Icons.Close, "Clear genres", if (focused) Color(0xFF14151B) else if (picked.isEmpty()) Blush.colors.textDim else Blush.colors.text, Modifier.align(Alignment.Center), size = 9.dp)
            }
        }
        LazyColumn(Modifier.heightIn(max = 250.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(genres, key = { it }) { g -> MenuRow(null, g, g in picked, onClick = { onPick(g) }) }
        }
    }
}

// ---------------- One grid of titles, fetched a page at a time ----------------

private const val PageSize = 60

/** A list of titles fetched a page at a time. Only one page is ever on its way; a failed first page ends as "nothing here". */
@Stable
private class Feed(private val load: suspend (Int, Int) -> Pair<List<BaseItemDto>, Int>) {
    var items by mutableStateOf<List<BaseItemDto>>(emptyList())
        private set
    var total by mutableIntStateOf(-1)
        private set
    var failed by mutableStateOf(false)
        private set
    private var busy = false
    val done get() = total >= 0 && items.size >= total

    /** Fetches the next page. False when nothing more came (the end, or an error). */
    suspend fun more(count: Int = PageSize): Boolean {
        if (busy || done) return false
        busy = true
        try {
            val (page, found) = load(items.size, count)
            if (page.isEmpty()) {
                total = items.size
                return false
            }
            items = (items + page).distinctBy { it.id }
            total = found
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (total < 0) { total = 0; failed = true }
            return false
        } finally {
            busy = false
        }
    }
}

@Composable
private fun PagedGrid(
    app: AppState,
    wide: Boolean,
    memory: GridMemory,
    load: suspend (Int, Int) -> Pair<List<BaseItemDto>, Int>,
    takeFocus: Boolean,
    entry: FocusRequester,
    toolbar: FocusRequester,
    back: TileReturn,
    onTotal: (Int) -> Unit,
    clearFilters: (() -> Unit)?,
    idleEmpty: IdleEmpty?,
    /** Set for lists that must not be fetched twice (a shuffled order): what was loaded is kept under this name for the way back. */
    keep: String? = null,
) {
    val columns = if (wide) WideColumns else PosterColumns
    val feed = remember {
        (keep?.let { app.gridFeeds[it] } as? Feed)?.takeIf { !it.failed && it.items.isNotEmpty() }
            ?: Feed(load).also { if (keep != null) app.gridFeeds[keep] = it }
    }
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Coming back to a place further down, everything up to it is asked for in one go rather than a page at a time.
        if (feed.items.isEmpty()) feed.more(((memory.index + 12) * columns).coerceIn(PageSize, 600))

        // Coming back to a place further down: load enough for the grid to be put back exactly where it was.
        val need = (memory.index + 12) * columns
        while (feed.items.size < need && !feed.done) if (!feed.more()) break
        ready = true
    }
    LaunchedEffect(feed.total) { if (feed.total >= 0 && !feed.failed) onTotal(feed.total) }

    when {
        feed.failed -> Message("Couldn't load this", "Check the server is on, then come back.", null, null)
        feed.total == 0 && clearFilters == null && idleEmpty != null ->
            Message(idleEmpty.title, idleEmpty.body, idleEmpty.action, firstFocus.takeIf { takeFocus })
        feed.total == 0 -> Message(
            "Nothing matches these filters", null,
            clearFilters?.let { "Clear filters" to it }, firstFocus.takeIf { takeFocus },
        )
        !ready -> LoadingDots()
        else -> WindowGrid(
            list = feed.items,
            columns = columns,
            aspect = if (wide) 16f / 9f else 2f / 3f,
            captionHeight = if (wide) 0.dp else 34.dp,
            memory = memory,
            entry = entry,
            toolbar = toolbar,
            takeFocus = takeFocus,
            back = back,
            onNeedMore = { scope.launch { feed.more() } },
        ) { item, w ->
            if (wide) WideTile(app, item, w) else PosterTile(app, item, w, caption = true)
        }
    }
}

/** What an empty page says when no filter is to blame. */
private class IdleEmpty(val title: String, val body: String?, val action: Pair<String, () -> Unit>?)

@Composable
private fun Message(title: String, body: String?, action: Pair<String, () -> Unit>?, focus: FocusRequester?) {
    LaunchedEffect(focus) {
        if (focus == null) return@LaunchedEffect
        repeat(10) { delay(40); if (runCatching { focus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect }
    }
    Column(Modifier.fillMaxSize().padding(bottom = 60.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = BlushType.headline)
        if (body != null) Text(body, style = BlushType.body, color = Blush.colors.textMuted, modifier = Modifier.padding(top = 8.dp))
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            SecondaryButton(action.first, null, onClick = action.second, modifier = if (focus != null) Modifier.focusRequester(focus) else Modifier)
        }
    }
}

@Composable
private fun PosterTile(app: AppState, item: BaseItemDto, width: Dp, caption: Boolean = false) {
    val tile = @Composable {
        PictureTile(
            url = app.repo.posterUrl(item),
            title = if (caption) "" else item.displayTitle(),
            width = width,
            aspect = 2f / 3f,
            progress = item.progress(),
            onClick = { app.go(Route.Details(item.id, preview = item)) },
        )
    }
    if (!caption) tile() else Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        tile()
        // The name and the year under the poster.
        Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.displayTitle(), style = BlushType.caption.copy(fontSize = 9.5.sp, lineHeight = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.W600), color = Blush.colors.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            item.productionYear?.let { Text("$it", style = BlushType.caption.copy(fontSize = 9.sp, lineHeight = 11.sp), color = Blush.colors.textMuted) }
        }
    }
}

/** A wide tile: the title's own thumbnail (which carries its name), or its backdrop with the name over it. */
@Composable
private fun WideTile(app: AppState, item: BaseItemDto, width: Dp) {
    val repo = app.repo
    val hasThumb = repo.hasThumb(item)
    LiteTile(width = width, aspect = 16f / 9f, onClick = { app.go(Route.Details(item.id, preview = item)) }) {
        RemoteImage(repo.thumbUrl(item, 520), Modifier.fillMaxSize().fadeInOver(Blush.colors.surface), crossfade = false)
        if (!hasThumb) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0xB3090A0E))))
            Text(item.displayTitle(), style = BlushType.label, maxLines = 2, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
        ProgressLine(item.progress())
    }
}

/**
 * The grid itself. Rows are laid out at a fixed height and slid as one block, so where everything is is simply arithmetic; focus never
 * scrolls anything by itself. Only the rows near the top of the view are built (the rest is empty space of the right height), so a
 * library of thousands costs no more than a screenful. Moving to a row slides it into view; a held key is paced so the remote never
 * runs ahead of the picture. Each row is its own composable that is skipped when nothing about it changed, so moving down a row builds
 * the one new row and touches nothing else.
 */
@Composable
private fun WindowGrid(
    list: List<BaseItemDto>,
    columns: Int,
    aspect: Float,
    captionHeight: Dp = 0.dp,
    memory: GridMemory,
    entry: FocusRequester,
    toolbar: FocusRequester,
    takeFocus: Boolean,
    back: TileReturn,
    onNeedMore: () -> Unit,
    cell: @Composable (BaseItemDto, Dp) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().offset(x = (-10).dp).clipToBounds().fadeInOnAppear()) {
        val gap = 9.dp
        val rowGap = 12.dp
        val top = 8.dp
        val tileWidth = (maxWidth - ContentEnd - gap * (columns - 1)) / columns
        val rowHeight = tileWidth / aspect + captionHeight
        val stride = rowHeight + rowGap
        val rows = (list.size + columns - 1) / columns
        // How many whole rows fit; a partly showing one is slid fully into view when the remote reaches it.
        val fit = (((maxHeight - top - 14.dp) + rowGap) / stride).toInt().coerceAtLeast(1)
        val maxFirst = (rows - fit).coerceAtLeast(0)
        // The top row in view. Held as a state object and read inside effects and the slide, so only the row window below reads it while building.
        val firstState = remember { mutableIntStateOf(memory.index.coerceIn(0, maxFirst)) }
        val strideFloat = with(density) { stride.toPx() }
        val shift = remember { Animatable(firstState.intValue * strideFloat) }
        LaunchedEffect(strideFloat) {
            snapshotFlow { firstState.intValue }.collectLatest { f ->
                memory.index = f
                shift.animateTo(f * strideFloat, tween(170, easing = FastOutSlowInEasing))
            }
        }
        // More titles are fetched well before the remote gets to the end of what there is.
        LaunchedEffect(fit, columns, list.size) {
            snapshotFlow { firstState.intValue }.collect { f -> if ((f + fit + 8) * columns >= list.size) onNeedMore() }
        }
        val reveal = remember(fit, maxFirst) {
            { row: Int ->
                val f = firstState.intValue
                val target = when {
                    row < f -> row
                    row >= f + fit -> (row - fit + 1).coerceAtMost(maxFirst)
                    else -> f
                }
                if (target != f) firstState.intValue = target
            }
        }

        // One focus handle per title, made when its row is first built. The remote starts on the title it was last on (when Back
        // brings the page back), otherwise on the first, and can be sent back to that title from the toolbar or the side bar.
        val handles = remember { HashMap<UUID, FocusRequester>() }
        // If the title the remote was on has gone (taken off the watchlist from its page), it starts on the one before it.
        val startId = remember(list.isNotEmpty()) {
            memory.focusedId?.takeIf { id -> list.any { it.id == id } }
                ?: list.getOrNull((memory.focusedPos - 1).coerceAtLeast(0))?.id ?: list.firstOrNull()?.id
        }
        LaunchedEffect(Unit) {
            if (!takeFocus) return@LaunchedEffect
            repeat(25) {
                delay(40)
                val handle = startId?.let { handles[it] }
                if (runCatching { handle?.requestFocus() }.getOrNull() == true) return@LaunchedEffect
            }
        }
        DisposableEffect(Unit) {
            back.go = { memory.focusedId?.let { id -> runCatching { handles[id]?.requestFocus() }.getOrNull() == true } ?: false }
            onDispose { back.go = { false } }
        }

        val lastRepeat = remember { LongArray(1) }
        val first = firstState.intValue
        val lo = (first - 1).coerceAtLeast(0)
        val hi = (first + fit + 2).coerceAtMost(rows - 1)
        Column(
            Modifier
                // The block is taller than the space it slides through: it must be laid out at its full height, not squeezed into the view.
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .padding(start = 10.dp, top = top)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 0 &&
                        (e.key == Key.DirectionDown || e.key == Key.DirectionUp || e.key == Key.DirectionLeft || e.key == Key.DirectionRight)
                    ) {
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastRepeat[0] < HeldKeyGap) true else { lastRepeat[0] = now; false }
                    } else false
                }
                .graphicsLayer { translationY = -shift.value },
            verticalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            // One empty block stands for all the rows above the built ones, and one for all those below.
            if (lo > 0) Box(Modifier.tallSpace(stride * lo - rowGap))
            for (r in lo..hi) {
                androidx.compose.runtime.key(r) {
                    GridRow(r, list, columns, rowHeight, gap, tileWidth, handles, entry, toolbar, memory, reveal, cell)
                }
            }
            if (hi < rows - 1) Box(Modifier.tallSpace(stride * (rows - 1 - hi) - rowGap))
        }
    }
}

/**
 * An empty block of any height. A plain Spacer can't stand for the rows above the view in a very long list: the height it asks
 * for has a ceiling (about 260,000 pixels, some 4,800 posters down), past which the page would crash.
 */
private fun Modifier.tallSpace(height: Dp) = layout { measurable, _ ->
    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(0, 0))
    layout(0, height.roundToPx().coerceAtLeast(0)) { placeable.place(0, 0) }
}

/** How soon after one step a held direction key is allowed to step again. */
private const val HeldKeyGap = 75L

@Composable
private fun GridRow(
    r: Int,
    list: List<BaseItemDto>,
    columns: Int,
    rowHeight: Dp,
    gap: Dp,
    tileWidth: Dp,
    handles: HashMap<UUID, FocusRequester>,
    entry: FocusRequester,
    toolbar: FocusRequester,
    memory: GridMemory,
    reveal: (Int) -> Unit,
    cell: @Composable (BaseItemDto, Dp) -> Unit,
) {
    Row(Modifier.height(rowHeight), horizontalArrangement = Arrangement.spacedBy(gap)) {
        for (c in 0 until columns) {
            val item = list.getOrNull(r * columns + c) ?: break
            androidx.compose.runtime.key(item.id) {
                val handle = handles.getOrPut(item.id) { FocusRequester() }
                Box(
                    Modifier
                        .focusRequester(handle)
                        .let { if (r == 0 && c == 0) it.focusRequester(entry) else it }
                        // Up from any title in the top row goes to the Sort & filter button.
                        .let { if (r == 0) it.focusProperties { up = toolbar } else it }
                        // Up goes to the title straight above, wherever the page is scrolled to (the search for it could otherwise
                        // pass over a row that is part way off the top and land on the toolbar).
                        .onPreviewKeyEvent { e ->
                            if (r > 0 && e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
                                val above = list.getOrNull((r - 1) * columns + c)?.id?.let { handles[it] }
                                above != null && runCatching { above.requestFocus() }.getOrDefault(false)
                            } else false
                        }
                        .onFocusChanged { if (it.hasFocus) { memory.focusedId = item.id; memory.focusedPos = r * columns + c; reveal(r) } },
                ) { cell(item, tileWidth) }
            }
        }
    }
}

// ---------------- A row per genre ----------------

private val GenreHeaderHeight = 22.dp
private val GenreRowGap = 16.dp

/**
 * One row per genre, each with that genre's titles under the current filters. Every row's titles are fetched up front, a few at a
 * time, so a row below the screen has its real size before the remote gets there. Rows are laid out at a fixed height and slid as
 * one block, like [WindowGrid]; Up and Down go to the title in the same place in the next row.
 */
@Composable
private fun GenreRows(
    app: AppState,
    genres: List<String>,
    wide: Boolean,
    memory: GridMemory,
    takeFocus: Boolean,
    entry: FocusRequester,
    toolbar: FocusRequester,
    back: TileReturn,
    order: GenreSort,
    loadRow: suspend (String) -> Pair<List<BaseItemDto>, Int>,
    onViewAll: (String) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val data = remember { mutableStateMapOf<String, List<BaseItemDto>>() }
    val counts = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(genres) {
        val gate = Semaphore(3)
        coroutineScope {
            for (g in genres) launch {
                gate.withPermit {
                    val result = runCatching { loadRow(g) }.getOrNull()
                    counts[g] = result?.second?.takeIf { it != UnknownTotal } ?: result?.first?.size ?: 0
                    data[g] = result?.first ?: emptyList()
                }
            }
        }
    }
    // The order of the rows. By item count they are put in order once every count is known (until then, A-Z).
    val randomSeed = remember { kotlin.random.Random.nextLong() }
    val ordered = when {
        order == GenreSort.Name -> genres
        order == GenreSort.Random -> genres.shuffled(kotlin.random.Random(randomSeed))
        genres.all { it in counts } -> if (order == GenreSort.Most) genres.sortedByDescending { counts[it] ?: 0 } else genres.sortedBy { counts[it] ?: 0 }
        else -> genres
    }
    // Rows that turned out empty are left out; rows still loading hold their place at full height.
    val shown = ordered.filter { data[it]?.isEmpty() != true }

    BoxWithConstraints(Modifier.fillMaxSize().offset(x = (-10).dp).clipToBounds().fadeInOnAppear()) {
        val tileWidth = if (wide) 186.dp else 104.dp
        val tileHeight = if (wide) tileWidth * 9 / 16 else tileWidth * 3 / 2
        // Room above and below the tiles for the focus zoom.
        val rowHeight = GenreHeaderHeight + 6.dp + tileHeight + 14.dp
        val stride = rowHeight + GenreRowGap
        val top = 6.dp
        val fit = (((maxHeight - top - 14.dp) + GenreRowGap) / stride).toInt().coerceAtLeast(1)
        val maxFirst = (shown.size - fit).coerceAtLeast(0)
        val startRow = remember { memory.index }
        val startCol = remember { memory.offset }
        var first by remember { mutableIntStateOf(startRow.coerceIn(0, maxFirst)) }
        val strideFloat = with(density) { stride.toPx() }
        val shift = remember { Animatable(first * strideFloat) }
        LaunchedEffect(first) { shift.animateTo(first * strideFloat, tween(170, easing = FastOutSlowInEasing)) }

        fun reveal(row: Int) {
            val target = when {
                row < first -> row
                row >= first + fit -> (row - fit + 1).coerceAtMost(maxFirst)
                else -> first
            }
            if (target != first) first = target
        }

        // One focus handle per title that is on screen, and each row's sideways scroll, found by genre.
        val handles = remember { mutableMapOf<String, FocusRequester>() }
        val rowStates = remember { mutableMapOf<String, LazyListState>() }
        val lastCol = remember { IntArray(1) { startCol } }

        suspend fun focusTile(rowIndex: Int, col: Int) {
            val genre = shown.getOrNull(rowIndex) ?: return
            reveal(rowIndex)
            var waited = 0
            while (data[genre] == null && waited < 125) { delay(16); waited++ }
            val count = data[genre]?.size ?: return
            if (count == 0) return
            val column = col.coerceAtMost(count - 1)
            // The row is built a moment after the page slides to it; the title a moment after that.
            repeat(40) {
                if (handles["$genre|$column"] == null) rowStates[genre]?.scrollToItem(column)
                if (runCatching { handles["$genre|$column"]?.requestFocus() }.getOrNull() == true) return
                delay(16)
            }
        }

        // From the toolbar or the side bar the remote goes back to the title it was last on.
        SideEffect {
            back.go = {
                val row = memory.index
                if (shown.getOrNull(row) == null) false else { scope.launch { focusTile(row, memory.offset) }; true }
            }
        }
        DisposableEffect(Unit) { onDispose { back.go = { false } } }

        // The remote starts on the title it was last on, or the first, once that row has arrived.
        var placed by remember { mutableStateOf(!takeFocus) }
        LaunchedEffect(shown.size, data.size) {
            if (placed) return@LaunchedEffect
            val row = startRow.coerceAtMost((shown.size - 1).coerceAtLeast(0))
            val genre = shown.getOrNull(row) ?: return@LaunchedEffect
            if (data[genre].isNullOrEmpty()) return@LaunchedEffect
            placed = true
            focusTile(row, startCol)
        }

        val lastRepeat = remember { LongArray(1) }
        val lo = (first - 1).coerceAtLeast(0)
        val hi = (first + fit + 2).coerceAtMost(shown.size - 1)
        Column(
            Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .padding(top = top)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 0 &&
                        (e.key == Key.DirectionDown || e.key == Key.DirectionUp || e.key == Key.DirectionLeft || e.key == Key.DirectionRight)
                    ) {
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastRepeat[0] < HeldKeyGap) true else { lastRepeat[0] = now; false }
                    } else false
                }
                .graphicsLayer { translationY = -shift.value },
            verticalArrangement = Arrangement.spacedBy(GenreRowGap),
        ) {
            if (lo > 0) Box(Modifier.tallSpace(stride * lo - GenreRowGap))
            for (index in lo..hi) {
                val genre = shown[index]
                androidx.compose.runtime.key(genre) {
                    val rowState = rememberLazyListState()
                    DisposableEffect(genre) {
                        rowStates[genre] = rowState
                        onDispose { if (rowStates[genre] === rowState) rowStates.remove(genre) }
                    }
                    Column(
                        Modifier
                            .height(rowHeight)
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val step = when (e.key) { Key.DirectionDown -> 1; Key.DirectionUp -> -1; else -> return@onPreviewKeyEvent false }
                                val target = index + step
                                // Up from the first row goes to the Sort & filter button; Down from the last stays put.
                                if (target !in shown.indices) {
                                    if (step < 0) runCatching { toolbar.requestFocus() }
                                    return@onPreviewKeyEvent true
                                }
                                scope.launch { focusTile(target, lastCol[0]) }
                                true
                            },
                    ) {
                        Text(genre, style = BlushType.rowHeader, maxLines = 1, modifier = Modifier.height(GenreHeaderHeight).padding(start = 10.dp))
                        Spacer(Modifier.height(6.dp))
                        val list = data[genre].orEmpty()
                        // Scrolls with room to spare at each end, so the selected tile (which grows a little) is never cut off
                        // at the right edge.
                        GentleScrolling {
                        LazyRow(
                            state = rowState,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            // Room past the last title as wide as the screen, so the end of a row can scroll as far as it should.
                            contentPadding = PaddingValues(start = 10.dp, end = ContentEnd + 300.dp, top = 6.dp, bottom = 6.dp),
                            modifier = Modifier.height(tileHeight + 12.dp),
                        ) {
                            itemsIndexed(list, key = { _, it -> it.id }) { i, item ->
                                val handle = remember { FocusRequester() }
                                DisposableEffect(genre, i) {
                                    handles["$genre|$i"] = handle
                                    onDispose { if (handles["$genre|$i"] === handle) handles.remove("$genre|$i") }
                                }
                                Box(
                                    Modifier
                                        .focusRequester(handle)
                                        .let { if (index == 0 && i == 0) it.focusRequester(entry) else it }
                                        .onFocusChanged {
                                            if (it.hasFocus) {
                                                lastCol[0] = i
                                                memory.index = index
                                                memory.offset = i
                                                reveal(index)
                                            }
                                        },
                                ) { if (wide) WideTile(app, item, tileWidth) else PosterTile(app, item, tileWidth) }
                            }
                            // A full row means there may be more of this genre than the row holds.
                            if (list.size >= 24) item(key = "more") {
                                val handle = remember { FocusRequester() }
                                DisposableEffect(genre) {
                                    handles["$genre|${list.size}"] = handle
                                    onDispose { if (handles["$genre|${list.size}"] === handle) handles.remove("$genre|${list.size}") }
                                }
                                FocusSurface(
                                    onClick = { onViewAll(genre) },
                                    modifier = Modifier
                                        .focusRequester(handle)
                                        .onFocusChanged { if (it.hasFocus) { lastCol[0] = list.size; memory.index = index; memory.offset = list.size - 1; reveal(index) } }
                                        .width(tileWidth)
                                        .height(tileHeight),
                                    background = Color.White.copy(alpha = 0.07f),
                                ) { _ ->
                                    Text("View all", style = BlushType.label, color = Blush.colors.textSoft, modifier = Modifier.align(Alignment.Center))
                                }
                            }
                        }
                        }
                    }
                }
            }
            if (hi < shown.size - 1) Box(Modifier.tallSpace(stride * (shown.size - 1 - hi) - GenreRowGap))
        }
    }
}
