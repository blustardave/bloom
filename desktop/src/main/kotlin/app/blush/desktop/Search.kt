@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.blush.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import app.blush.tv.ui.theme.Blush
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemDto

// Search on the Mac: type and the titles appear as you go (a moment after you stop typing). With nothing typed, your
// recent searches are offered. The TV's extra sections (people, studios, "did you mean") come later.

@Composable
fun SearchScreen(
    query: String,
    onQuery: (String) -> Unit,
    results: List<BaseItemDto>?,
    searching: Boolean,
    recent: List<String>,
    pictures: Pictures,
    onOpen: (BaseItemDto) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = PageSide, end = PageSide, top = 12.dp, bottom = 48.dp)) {
        item {
            Text("Search", style = DesktopType.headline)
            Spacer(Modifier.height(16.dp))
            Field(query, onQuery, "Films, series, collections…", Modifier.widthIn(max = 560.dp), focus = focus)
            Spacer(Modifier.height(20.dp))
        }
        item {
            when {
                query.isBlank() && recent.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Recent searches", style = DesktopType.caption, color = Blush.colors.textDim)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { recent.forEach { r -> HoverPill(r, false, onClick = { onQuery(r) }) } }
                }
                query.isBlank() -> Unit
                results == null || (searching && results.isEmpty()) -> Note("Searching…", Modifier.padding(top = 20.dp))
                results.isEmpty() -> Note("Nothing matches “$query”.", Modifier.padding(top = 20.dp))
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    results.forEach { item -> PosterTile(item, pictures, showName = true) { onOpen(item) } }
                }
            }
        }
    }
}

@Composable
fun SearchRoute(session: Session) {
    val repo = session.repo
    var query by remember { mutableStateOf(session.lastSearch) }
    var results by remember { mutableStateOf<List<BaseItemDto>?>(null) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        session.lastSearch = query
        val q = query.trim()
        if (q.length < 2) { results = null; return@LaunchedEffect }
        searching = true
        delay(280) // wait for a pause in typing
        results = runCatching { repo.searchTitles(q) }.getOrDefault(emptyList())
        searching = false
    }
    SearchScreen(query, { query = it }, results, searching, repo.store.recentSearches, repo.pictures()) { item ->
        repo.store.addRecentSearch(query)
        session.go(Screen.Details(item.id, item))
    }
}
