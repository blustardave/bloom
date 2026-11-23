package app.blush.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blush.tv.ui.components.Icon
import app.blush.tv.ui.components.Icons
import app.blush.tv.ui.theme.Blush
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy

// Search on the Mac: a large rounded search box; titles appear as you type (a moment after you stop). Before anything
// is typed, recent searches and a random handful of titles to browse ("Something to watch") fill the page.
// The TV's extra sections (people, studios, "did you mean") come later.

private const val Columns = 7

@Composable
fun SearchScreen(
    query: String,
    onQuery: (String) -> Unit,
    results: List<BaseItemDto>?,
    searching: Boolean,
    recent: List<String>,
    suggestions: List<BaseItemDto>,
    pictures: Pictures,
    onOpen: (BaseItemDto) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = PageSide, end = PageSide, top = 18.dp, bottom = 48.dp)) {
        item {
            SearchBox(query, onQuery)
            Spacer(Modifier.height(26.dp))
        }
        item {
            when {
                query.isBlank() -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (recent.isNotEmpty()) {
                        Text("Recent searches", style = DesktopType.title.copy(fontSize = 16.sp, fontWeight = FontWeight.W600))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { recent.forEach { r -> HoverPill(r, false, onClick = { onQuery(r) }) } }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (suggestions.isNotEmpty()) {
                        Text("Something to watch", style = DesktopType.title.copy(fontSize = 16.sp, fontWeight = FontWeight.W600))
                        PosterColumns(suggestions, Columns, pictures, onOpen = onOpen)
                    }
                }
                results == null || (searching && results.isEmpty()) -> Note("Searching…", Modifier.padding(top = 20.dp))
                results.isEmpty() -> Note("Nothing matches “$query”.", Modifier.padding(top = 20.dp))
                else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${results.size} ${if (results.size == 1) "result" else "results"}", style = DesktopType.caption, color = Blush.colors.textDim)
                    PosterColumns(results, Columns, pictures, onOpen = onOpen)
                }
            }
        }
    }
}

/** A large rounded search box: magnifying glass inside, no outline, a shade lighter while typing or under the pointer. */
@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val colors = Blush.colors
    BasicTextField(
        query, onQuery,
        singleLine = true,
        textStyle = DesktopType.body.copy(fontSize = 16.sp, color = colors.text),
        cursorBrush = SolidColor(colors.accent.color),
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused }
            .hoverable(hover).pointerHoverIcon(PointerIcon.Text),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(CircleShape)
                    .background(if (focused || hovered) Color(0xFF2E2F35) else Color(0xFF26272C))
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Search, null, if (focused) colors.text else colors.textMuted, size = 18.dp)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search films, series and collections", style = DesktopType.body.copy(fontSize = 16.sp), color = colors.textDim, maxLines = 1)
                    inner()
                }
                if (query.isNotEmpty()) {
                    val h = rememberHover()
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (h.lit) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                            .mouseTarget(h) { onQuery("") }.padding(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text("Clear", style = DesktopType.label, color = if (h.lit) Color.White else colors.textMuted) }
                }
            }
        },
    )
}

@Composable
fun SearchRoute(session: Session) {
    val repo = session.repo
    var query by remember { mutableStateOf(session.lastSearch) }
    var results by remember { mutableStateOf<List<BaseItemDto>?>(null) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // A random handful to browse before typing; picked once per sign-in so it doesn't reshuffle on every visit.
        if (session.searchSuggestions.isEmpty()) {
            session.searchSuggestions = runCatching {
                repo.browse(types = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES), sortBy = ItemSortBy.RANDOM, limit = 21)
            }.getOrDefault(emptyList())
        }
    }
    LaunchedEffect(query) {
        session.lastSearch = query
        val q = query.trim()
        if (q.length < 2) { results = null; return@LaunchedEffect }
        searching = true
        delay(280) // wait for a pause in typing
        results = runCatching { repo.searchTitles(q) }.getOrDefault(emptyList())
        searching = false
    }
    SearchScreen(query, { query = it }, results, searching, repo.store.recentSearches, session.searchSuggestions, repo.pictures()) { item ->
        if (query.isNotBlank()) repo.store.addRecentSearch(query)
        session.go(Screen.Details(item.id, item))
    }
}
