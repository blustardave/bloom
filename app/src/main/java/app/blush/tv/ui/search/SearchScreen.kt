package app.blush.tv.ui.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import app.blush.tv.ui.components.PivotScroll
import app.blush.tv.ui.components.PivotScrolling
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blush.tv.data.Repo
import app.blush.tv.ui.AppState
import app.blush.tv.ui.ContentEnd
import app.blush.tv.ui.LocalNavBar
import app.blush.tv.ui.ContentStart
import app.blush.tv.ui.PageTop
import app.blush.tv.ui.Route
import app.blush.tv.ui.components.FocusSurface
import app.blush.tv.ui.components.Icon
import app.blush.tv.ui.components.Icons
import app.blush.tv.ui.components.Pill
import app.blush.tv.ui.components.PosterCard
import app.blush.tv.ui.components.RemoteImage
import app.blush.tv.ui.displayTitle
import app.blush.tv.ui.progress
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.theme.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

private const val SearchColumns = 6
/** The people row: one line, at most this many, starting at the left edge like the posters. */
private const val PeopleMax = 6

// The keyboard: 6 × 6 keys (A–Z, 1–0), then a row of space, delete, voice and clear.
private val KeySize = 34.dp
private val KeyGap = 4.dp
private val KeyboardWidth = KeySize * 6 + KeyGap * 5

/** The search bar is a little wider than the keyboard, level with it on both sides. */
private val BarOverhang = 14.dp
private val BarWidth = KeyboardWidth + BarOverhang * 2
private val BarHeight = 32.dp
private val ColumnGap = 26.dp
private val KeyboardHeight = KeySize * 7 + KeyGap * 6 + 4.dp
/** How far below the top of a key the tops of its letter sit; the first row of results starts at this height. */
private val LetterTop = 12.dp
private val SpaceSymbolWidth = 26.dp
/** The bottom row's four keys (space, delete, voice, clear) are all this wide, with the usual gap, so together they match the keys above. */
private val BottomKeyWidth = (KeyboardWidth - KeyGap * 3) / 4

@Composable
fun SearchScreen(app: AppState) {
    var query by remember { mutableStateOf(app.searchQuery) }
    // Results arrive in stages (titles, then people, then extra rows) and are kept on screen until the next
    // ones are ready, so nothing flashes while you type.
    var titles by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var people by remember { mutableStateOf<List<Repo.Credit>>(emptyList()) }
    var extras by remember { mutableStateOf<List<Repo.SearchSection>>(emptyList()) }
    var resultsFor by remember { mutableStateOf<String?>(null) }
    var didMean by remember { mutableStateOf<String?>(null) }
    var recent by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var recentSearches by remember { mutableStateOf(app.repo.store.recentSearches) }
    val firstKey = remember { FocusRequester() }
    // Up from the top tiles lands on the first suggestion (or recent search); with none, on the navigation bar.
    val firstPill = remember { FocusRequester() }
    val nav = LocalNavBar.current
    var pickedFromPill by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    fun type(text: String) {
        query = text.take(40)
        app.searchQuery = query
    }

    // What was opened last time (a poster or a person), so Back from it puts the remote on that tile instead of the keyboard.
    val restore = remember { RestoreTarget(app.searchReturn).also { app.searchReturn = null } }
    val handles = remember { HashMap<String, FocusRequester>() }
    fun handle(key: String) = handles.getOrPut(key) { FocusRequester() }

    fun open(item: BaseItemDto, key: String) {
        if (query.isNotBlank()) app.repo.store.addRecentSearch(query)
        app.searchReturn = key
        app.go(Route.Details(item.id))
    }

    val voice = rememberVoice { spoken -> type(spoken) }

    LaunchedEffect(Unit) {
        if (restore.key == null) runCatching { firstKey.requestFocus() }
        recent = runCatching { app.repo.recentlyAdded(20) }.getOrDefault(emptyList())
        // Builds the "who's who" index in the background, so people are ranked by fame once it's ready.
        app.repo.warmSearch()
    }
    LaunchedEffect(Unit) { app.repo.warmNames() }
    LaunchedEffect(query) {
        val term = query.trim()
        if (term.isEmpty()) {
            titles = emptyList(); people = emptyList(); extras = emptyList(); resultsFor = null; didMean = null
            if (restore.key == null) gridState.scrollToItem(0)
            return@LaunchedEffect
        }
        delay(180)
        val foundTitles = app.repo.searchTitles(term)
        // One row of people, laid out on the same columns as the posters.
        val foundPeople = app.repo.searchPeople(term, limit = PeopleMax)
        titles = foundTitles
        people = foundPeople
        extras = emptyList()
        resultsFor = term
        // Only offered when there is little to show: a few matches means the text was probably right.
        didMean = if (foundTitles.size < 3 && foundPeople.isEmpty()) app.repo.didYouMean(term) else null
        // New results always start at the top (the list would otherwise keep a tile from the old results in view),
        // except when the page is being put back as it was left.
        if (restore.key == null) gridState.scrollToItem(0)
        extras = app.repo.searchExtras(term, foundTitles.map { it.id }.toSet())
    }

    // Coming back from a title: wait for the results to be on screen, scroll to the tile that was opened and put the remote on it.
    LaunchedEffect(Unit) {
        val target = restore.key ?: return@LaunchedEffect
        val gridKey = if (target.startsWith("person-")) "people" else target
        var waited = 0
        while (waited < 100) {
            val term = query.trim()
            val order = buildList {
                if (term.isEmpty()) recent.forEach { add("recent-${it.id}") }
                else {
                    titles.forEach { add("t-${it.id}") }
                    if (people.isNotEmpty()) { add("people-break"); add("people") }
                    extras.forEach { section -> add("h-${section.title}"); section.items.forEach { add("${section.title}-${it.id}") } }
                }
            }
            val index = order.indexOf(gridKey)
            if (index >= 0 && (term.isEmpty() || resultsFor == term)) {
                gridState.scrollToItem(index)
                repeat(25) {
                    delay(40)
                    if (runCatching { handles[target]?.requestFocus() }.getOrNull() == true) { restore.key = null; return@LaunchedEffect }
                }
                break
            }
            delay(40)
            waited++
        }
        // It is no longer there (or never arrived): carry on as a fresh page.
        restore.key = null
        runCatching { firstKey.requestFocus() }
    }

    // Pressing a suggestion or recent search fills it in and, once its results are up, puts the remote on the first tile.
    LaunchedEffect(pickedFromPill, resultsFor, titles, people) {
        if (!pickedFromPill) return@LaunchedEffect
        val term = query.trim()
        if (resultsFor != term) return@LaunchedEffect
        val key = titles.firstOrNull()?.let { "t-${it.id}" } ?: people.firstOrNull()?.let { "person-${it.id ?: it.name}" }
        if (key != null) repeat(15) {
            delay(40)
            if (runCatching { handles[key]?.requestFocus() }.getOrNull() == true) { pickedFromPill = false; return@LaunchedEffect }
        }
        pickedFromPill = false
    }
    val hasSuggestions = if (query.trim().isEmpty()) recentSearches.isNotEmpty()
        else didMean != null || titles.any { !it.displayTitle().equals(query.trim(), ignoreCase = true) }

    /** Focusing something near the top scrolls right back up, so headings above it aren't left cut off; Up goes to the suggestions. */
    fun Modifier.topRow(isTop: Boolean) = if (!isTop) this else onFocusChanged {
        if (it.hasFocus) scope.launch { gridState.animateScrollToItem(0) }
    }.onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
            if (hasSuggestions && runCatching { firstPill.requestFocus() }.getOrDefault(false)) true
            else if (nav != null) { nav.armed = true; runCatching { nav.entry.requestFocus() }.isSuccess }
            else false
        } else false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Blush.colors.background)
            // Typing on a real keyboard (handy in the emulator) works too.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val native = e.nativeKeyEvent
                when {
                    native.keyCode == AndroidKeyEvent.KEYCODE_DEL -> { type(query.dropLast(1)); true }
                    native.keyCode == AndroidKeyEvent.KEYCODE_SPACE -> { if (query.isNotEmpty() && !query.endsWith(" ")) type("$query "); true }
                    native.unicodeChar.toChar().isLetterOrDigit() -> { type(query + native.unicodeChar.toChar().lowercaseChar()); true }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize().padding(start = ContentStart, end = ContentEnd, top = PageTop - 4.dp)) {
            // The bar and the suggestions share one line, so they sit exactly level.
            Row(Modifier.fillMaxWidth().height(BarHeight), verticalAlignment = Alignment.CenterVertically) {
                SearchField(query, voice, Modifier.width(BarWidth))
                Spacer(Modifier.width(ColumnGap))
                Suggestions(
                    query = query.trim(),
                    didMean = didMean,
                    titles = titles,
                    recentSearches = recentSearches,
                    firstPill = firstPill,
                    onPick = { type(it); pickedFromPill = true },
                    onClearRecent = {
                        app.repo.store.recentSearches = emptyList()
                        recentSearches = emptyList()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth().weight(1f)) {
                Box(Modifier.width(BarWidth), contentAlignment = Alignment.TopStart) {
                    Keyboard(
                        onKey = { type(query + it) },
                        onSpace = { if (query.isNotEmpty() && !query.endsWith(" ")) type("$query ") },
                        onDelete = { type(query.dropLast(1)) },
                        onVoice = { voice.toggle() },
                        onClear = { type("") },
                        listening = voice.listening,
                        firstKey = firstKey,
                        modifier = Modifier.padding(start = BarOverhang),
                    )
                    VoiceNote(voice)
                }
                Spacer(Modifier.width(ColumnGap))
                BoxWithConstraints(Modifier.weight(1f).fillMaxSize()) {
                    // Posters sized to fill the space beside the keyboard, five to a row.
                    val gap = 12.dp
                    val padding = 4.dp
                    val posterWidth = (maxWidth - padding * 2 - gap * (SearchColumns - 1)) / SearchColumns
                    val term = query.trim()
                    val noMatches = term.isNotEmpty() && resultsFor == term && titles.isEmpty() && people.isEmpty() && extras.isEmpty()
                    // Every row, whatever it holds, is scrolled to the same place: the focused row sits where the second row of posters
                    // does (or the top, when it cannot scroll any further), instead of being pushed just far enough to show.
                    val anchor = with(LocalDensity.current) { (LetterTop + posterWidth * 1.5f + gap).toPx() }
                    val pivot = remember(anchor) { PivotScroll(startPx = anchor, deadZonePx = 8f) }
                    PivotScrolling(pivot) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(SearchColumns),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(gap),
                        // The first row of posters starts level with the tops of the keyboard's letters (the padding makes room for the focus zoom).
                        contentPadding = PaddingValues(top = LetterTop, bottom = 72.dp, start = padding, end = padding),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        fun posters(list: List<BaseItemDto>, keyPrefix: String, first: Boolean) {
                            itemsIndexed(list, key = { _, it -> "$keyPrefix-${it.id}" }) { i, item ->
                                PosterCard(
                                    title = item.displayTitle(),
                                    imageUrl = app.repo.posterUrl(item),
                                    width = posterWidth,
                                    progress = item.progress(),
                                    onClick = { open(item, "$keyPrefix-${item.id}") },
                                    modifier = Modifier.topRow(first && i < SearchColumns).focusRequester(handle("$keyPrefix-${item.id}")),
                                )
                            }
                        }
                        fun heading(text: String) {
                            item(key = "h-$text", span = { GridItemSpan(maxLineSpan) }) {
                                Text(text, style = BlushType.rowHeader, color = Blush.colors.textMuted, modifier = Modifier.padding(top = 8.dp))
                            }
                        }

                        when {
                            // Before typing: what's new (your recent searches are among the suggestions beside the search bar).
                            term.isEmpty() -> posters(recent, "recent", first = true)
                            noMatches -> item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                                Column(Modifier.padding(top = 10.dp)) {
                                    Text("Nothing matches “$term”", style = BlushType.title)
                                    Text(
                                        if (didMean != null) "Did you mean “$didMean”? It is in the suggestions above." else "Try fewer letters, an actor, a director or a studio.",
                                        style = BlushType.body,
                                        color = Blush.colors.textMuted,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                            else -> {
                                // Titles first, with no heading; then people; then the extra rows.
                                posters(titles, "t", first = true)
                                if (people.isNotEmpty()) {
                                    // A full-width (empty) item makes the people always start a row of their own, and gives a little more room above them.
                                    item(key = "people-break", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(2.dp)) }
                                    // One line of up to six people. Each has an equal slot, so the portraits are evenly spaced and, taken
                                    // together, centred under the five posters above; a name sits centred under its portrait.
                                    item(key = "people", span = { GridItemSpan(maxLineSpan) }) {
                                        val slot = (posterWidth * SearchColumns + gap * (SearchColumns - 1)) / PeopleMax
                                        Row(Modifier.fillMaxWidth()) {
                                            people.take(PeopleMax).forEach { p ->
                                                val personKey = "person-${p.id ?: p.name}"
                                                androidx.compose.runtime.key(personKey) {
                                                    PersonCell(
                                                        app, p, slot,
                                                        onClick = {
                                                            if (query.isNotBlank()) app.repo.store.addRecentSearch(query)
                                                            app.searchReturn = personKey
                                                            p.id?.let { app.go(Route.Person(it)) }
                                                        },
                                                        modifier = Modifier.topRow(titles.isEmpty()).focusRequester(handle(personKey)),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                extras.forEach { section ->
                                    heading(section.title)
                                    posters(section.items, section.title, first = false)
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

/**
 * The line of suggestions beside the search bar. Before anything is typed: your recent searches. While typing: "Did you mean"
 * when the text is close to a title but matches nothing, then the names of the best matches. One press fills them in.
 */
@Composable
private fun Suggestions(
    query: String,
    didMean: String?,
    titles: List<BaseItemDto>,
    recentSearches: List<String>,
    onPick: (String) -> Unit,
    onClearRecent: () -> Unit,
    firstPill: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val names = remember(titles, query) {
        if (query.isEmpty()) emptyList()
        else titles.map { it.displayTitle() }.filterNot { it.equals(query, ignoreCase = true) }.distinct().take(4)
    }
    LazyRow(
        modifier.height(BarHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        if (query.isEmpty()) {
            rowItemsIndexed(recentSearches, key = { _, it -> "r-$it" }) { i, q -> Pill(q, selected = false, onClick = { onPick(q) }, small = true, modifier = if (i == 0) Modifier.focusRequester(firstPill) else Modifier) }
            if (recentSearches.isNotEmpty()) item(key = "clear") { Pill("Clear", selected = false, onClick = onClearRecent, small = true) }
        } else {
            if (didMean != null) item(key = "dym") { Pill("Did you mean ${didMean.take(28)}?", selected = true, onClick = { onPick(didMean) }, small = true, modifier = Modifier.focusRequester(firstPill)) }
            rowItemsIndexed(names, key = { _, it -> "t-$it" }) { i, name -> Pill(name.take(30), selected = false, onClick = { onPick(name) }, small = true, modifier = if (i == 0 && didMean == null) Modifier.focusRequester(firstPill) else Modifier) }
        }
    }
}

/** The typed text in a rounded bar; when it is longer than the bar it ends in an ellipsis. */
@Composable
private fun SearchField(query: String, voice: VoiceSearch, modifier: Modifier = Modifier) {
    // Trimmed line spacing, so the text is truly centred beside the magnifying glass.
    val level = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
    @Suppress("DEPRECATION")
    val noPad = PlatformTextStyle(includeFontPadding = false)
    Row(
        modifier.height(BarHeight).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Search, null, Blush.colors.textMuted, size = 14.dp)
        if (query.isNotEmpty()) {
            Text(
                query,
                style = BlushType.headline.copy(fontSize = 12.sp, fontWeight = FontWeight.W600, lineHeightStyle = level, platformStyle = noPad),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                if (voice.listening) "Listening…" else "Titles, people, studios, genres",
                style = BlushType.headline.copy(fontSize = 12.sp, fontWeight = FontWeight.W500, lineHeightStyle = level, platformStyle = noPad),
                color = if (voice.listening) Blush.colors.text else Blush.colors.textDim,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One place in the People row: a round headshot, the name and how many titles they are in, all centred in their slot. */
@Composable
private fun PersonCell(app: AppState, person: Repo.Credit, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        FocusSurface(
            onClick = onClick,
            shape = CircleShape,
            background = Blush.colors.surface,
            modifier = modifier.size(64.dp),
        ) {
            Text(
                person.name.split(' ').mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""),
                style = BlushType.label,
                color = Blush.colors.textMuted,
                modifier = Modifier.align(Alignment.Center),
            )
            RemoteImage(app.repo.personImageUrl(person), Modifier.fillMaxSize())
        }
        Text(person.name, style = BlushType.caption, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp))
        if (person.count > 0) {
            Text(if (person.count == 1) "1 title" else "${person.count} titles", style = BlushType.caption.copy(fontSize = 9.sp), color = Blush.colors.textDim, maxLines = 1)
        }
    }
}

private val KeyLetters = ('A'..'Z').map { it.toString() } + "1234567890".map { it.toString() }

/**
 * A quiet on-screen keyboard: keys have no box until the remote is on them, and then they turn into a white rounded square.
 * The bottom row sits on the same columns as the keys above: space and clear are two columns wide, delete and voice one each,
 * and their symbols are sized so their edges line up with the edges of the letters above.
 */
@Composable
private fun Keyboard(
    onKey: (String) -> Unit,
    onSpace: () -> Unit,
    onDelete: () -> Unit,
    onVoice: () -> Unit,
    onClear: () -> Unit,
    listening: Boolean,
    firstKey: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val ink = Color(0xFF14151B)
    // Up from Clear (two columns wide) goes to the 0 above its right-hand half.
    val zeroKey = remember { FocusRequester() }
    Column(modifier.width(KeyboardWidth), verticalArrangement = Arrangement.spacedBy(KeyGap)) {
        KeyLetters.chunked(6).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                row.forEachIndexed { i, k ->
                    KeyButton(
                        onClick = { onKey(k.lowercase()) },
                        modifier = Modifier.size(KeySize)
                            .let { if (rowIndex == 0 && i == 0) it.focusRequester(firstKey) else it }
                            .let { if (k == "0") it.focusRequester(zeroKey) else it },
                    ) { focused ->
                        Text(
                            k,
                            style = BlushType.title.copy(fontSize = 14.sp, fontWeight = FontWeight.W600),
                            color = if (focused) ink else Blush.colors.textSoft,
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
            KeyButton(onSpace, Modifier.size(BottomKeyWidth, KeySize)) { f ->
                // The usual space symbol (an open box), as wide as the two letters above it.
                val colour = if (f) ink else Blush.colors.textMuted
                Box(
                    Modifier.size(SpaceSymbolWidth, 9.dp).drawBehind {
                        val w = size.width
                        val h = size.height
                        val stroke = 1.8.dp.toPx()
                        val half = stroke / 2
                        val path = Path().apply {
                            moveTo(half, 0f); lineTo(half, h - half); lineTo(w - half, h - half); lineTo(w - half, 0f)
                        }
                        drawPath(path, colour, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    },
                )
            }
            // Holding OK on delete keeps deleting.
            KeyButton(onDelete, Modifier.size(BottomKeyWidth, KeySize).holdToRepeat(onDelete)) { f ->
                val colour = if (f) ink else Blush.colors.textMuted
                // The usual delete key shape (a tag pointing left with an x), drawn wider than tall.
                Box(
                    Modifier.size(22.dp, 14.dp).drawBehind {
                        val w = size.width
                        val h = size.height
                        val stroke = 1.7.dp.toPx()
                        val half = stroke / 2
                        val body = Path().apply {
                            moveTo(w * 0.32f, half); lineTo(w - half, half); lineTo(w - half, h - half)
                            lineTo(w * 0.32f, h - half); lineTo(half, h / 2); close()
                        }
                        drawPath(body, colour, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        val cx = w * 0.62f
                        val cy = h / 2
                        val r = h * 0.19f
                        drawLine(colour, androidx.compose.ui.geometry.Offset(cx - r, cy - r), androidx.compose.ui.geometry.Offset(cx + r, cy + r), stroke, StrokeCap.Round)
                        drawLine(colour, androidx.compose.ui.geometry.Offset(cx + r, cy - r), androidx.compose.ui.geometry.Offset(cx - r, cy + r), stroke, StrokeCap.Round)
                    },
                )
            }
            KeyButton(onVoice, Modifier.size(BottomKeyWidth, KeySize)) { f ->
                Icon(Icons.Mic, "Voice search", if (f) ink else if (listening) Blush.colors.accent.color else Blush.colors.textMuted, size = 17.dp)
            }
            KeyButton(onClear, Modifier.size(BottomKeyWidth, KeySize).focusProperties { up = zeroKey }) { f ->
                Text("CLEAR", style = BlushType.button.copy(fontSize = 9.sp), color = if (f) ink else Blush.colors.textMuted, maxLines = 1)
            }
        }
    }
}

/**
 * Holding OK repeats [action] (about every 60 ms after the key's own repeat starts). The press is handled here, on the way down,
 * so the release does not also count as a click.
 */
private fun Modifier.holdToRepeat(action: () -> Unit): Modifier {
    var last = 0L
    return onPreviewKeyEvent { e ->
        val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
        if (!ok) return@onPreviewKeyEvent false
        if (e.type == KeyEventType.KeyDown) {
            val now = android.os.SystemClock.uptimeMillis()
            if (e.nativeKeyEvent.repeatCount == 0 || now - last >= 60) {
                last = now
                action()
            }
        }
        true
    }
}

@Composable
private fun KeyButton(onClick: () -> Unit, modifier: Modifier, content: @Composable (Boolean) -> Unit) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.08f,
        ringWidth = 0.dp,
        background = Color.Transparent,
        focusedBackground = Color(0xFFF5F5F7),
    ) { focused ->
        Box(Modifier.align(Alignment.Center)) { content(focused) }
    }
}

// ---------------- Voice ----------------

/** A small note under the voice key (e.g. "Didn't catch that") that fades in, stays a few seconds and fades out. */
@Composable
private fun VoiceNote(voice: VoiceSearch) {
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(voice.message) {
        val m = voice.message
        if (m == null) return@LaunchedEffect
        text = m
        alpha.snapTo(0f)
        alpha.animateTo(1f, androidx.compose.animation.core.tween(160))
        delay(3_000)
        alpha.animateTo(0f, androidx.compose.animation.core.tween(500))
        voice.clearMessage()
    }
    if (text.isEmpty()) return
    // The voice key's middle: the keyboard's left edge, then space (two columns), delete, and half of the voice key.
    val micCentre = BarOverhang + (BottomKeyWidth + KeyGap) * 2 + BottomKeyWidth / 2
    Box(
        Modifier
            .offset(x = micCentre, y = KeyboardHeight + 8.dp)
            .width(0.dp)
            .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)
            .graphicsLayer { this.alpha = alpha.value },
    ) {
        Text(
            text,
            style = BlushType.caption.copy(fontSize = 11.sp),
            color = Blush.colors.text,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 220.dp)
                .background(Color(0xE61C1D26), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/**
 * Voice search through the system's speech recogniser. Whether it works depends on the device having one (many Fire TV sticks do,
 * through the voice remote); when it has none the search bar says so.
 */
@Stable
internal class VoiceSearch(private val context: Context, private val onSpoken: (String) -> Unit) {
    var listening by mutableStateOf(false)
        private set

    /** A short note shown in the empty search bar (e.g. "Didn't catch that"). */
    var message by mutableStateOf<String?>(null)
        private set

    var needsPermission: (() -> Unit)? = null
    private var recognizer: SpeechRecognizer? = null

    fun toggle() {
        if (listening) stop() else {
            message = null
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) needsPermission?.invoke() else start()
        }
    }

    fun start() {
        message = null
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            message = "Voice search isn't available on this device"
            return
        }
        runCatching {
            recognizer?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { listening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) = spoken(partialResults)
                override fun onResults(results: Bundle?) {
                    spoken(results)
                    listening = false
                }
                override fun onError(error: Int) {
                    listening = false
                    message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice search needs a connection"
                        else -> "Voice search didn't work"
                    }
                }
            })
            listening = true
            r.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                },
            )
        }.onFailure {
            listening = false
            message = "Voice search isn't available on this device"
        }
    }

    private fun spoken(bundle: Bundle?) {
        val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
        if (text.isNotEmpty()) onSpoken(text.trimEnd('.', '!', '?').lowercase())
    }

    fun stop() {
        listening = false
        runCatching { recognizer?.stopListening() }
    }

    fun destroy() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        listening = false
    }

    fun denied() { message = "Microphone not allowed for Bloom" }

    fun clearMessage() { message = null }
}

@Composable
private fun rememberVoice(onSpoken: (String) -> Unit): VoiceSearch {
    val context = LocalContext.current
    val latest by androidx.compose.runtime.rememberUpdatedState(onSpoken)
    val voice = remember { VoiceSearch(context) { latest(it) } }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.start() else voice.denied()
    }
    voice.needsPermission = { ask.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(voice) { onDispose { voice.destroy() } }
    return voice
}

/** The tile to put the remote back on when the page returns from a title: a key into the results, cleared once done. */
private class RestoreTarget(var key: String?)
