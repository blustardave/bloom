package app.blush.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.blush.tv.data.Repo
import app.blush.tv.ui.posterTint
import app.blush.tv.ui.theme.Blush
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemSortBy

/**
 * The strip along the top of the window that can be dragged to move it. The window is drawn under a see-through title
 * bar, so Bloom has to say where dragging works. Main.kt provides the real one; the screenshot run leaves it empty.
 */
val LocalDragArea = staticCompositionLocalOf<@Composable (Modifier) -> Unit> { { } }

/** Room left of the top bar for the Mac's red/yellow/green window buttons. */
private val TrafficLights = 78.dp
val PageSide = 40.dp

/** One library, as drawn on the Libraries page. */
data class LibraryCard(val name: String, val imageUrl: String?, val count: Int?)

/** One title in a library grid. */
data class PosterCard(val name: String, val year: Int?, val imageUrl: String?, val tint: Color?)

// ---------- Top bar ----------

@Composable
fun TopBar(current: String, userName: String, userImage: String?, onHome: () -> Unit, onLibraries: () -> Unit, onSearch: () -> Unit, onSwitchProfile: () -> Unit, onSignOut: () -> Unit, onChangeServer: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(64.dp)
            // A soft shade from the top, so the tabs stay readable over the banner's artwork.
            .background(Brush.verticalGradient(0f to Blush.colors.background.copy(alpha = 0.75f), 1f to Color.Transparent)),
    ) {
        LocalDragArea.current(Modifier.fillMaxSize())
        Row(Modifier.fillMaxSize().padding(start = TrafficLights, end = PageSide - 2.dp), verticalAlignment = Alignment.CenterVertically) {
            val mark = remember { resourceImage("bloom_wordmark.png") }
            Image(mark, contentDescription = "Bloom", modifier = Modifier.height(20.dp).aspectRatio(430f / 112f))
            Spacer(Modifier.width(28.dp))
            TopTab("Home", selected = current == "Home", onClick = onHome)
            Spacer(Modifier.width(14.dp))
            TopTab("Libraries", selected = current == "Libraries", onClick = onLibraries)
            Spacer(Modifier.weight(1f))
            SearchButton(current == "Search", onSearch)
            Spacer(Modifier.width(12.dp))
            ProfileMenu(userName, userImage, onSwitchProfile, onSignOut, onChangeServer)
        }
    }
}

/** The magnifying glass at the top right (⌘F also opens Search). */
@Composable
private fun SearchButton(selected: Boolean, onClick: () -> Unit) {
    val h = rememberHover()
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(50))
            .background(if (h.lit || selected) Color.White.copy(alpha = if (selected) 0.22f else 0.14f) else Color.Transparent)
            .mouseTarget(h, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { app.blush.tv.ui.components.Icon(app.blush.tv.ui.components.Icons.Search, "Search", if (h.lit || selected) Color.White else Blush.colors.textSoft, size = 18.dp) }
}

@Composable
private fun TopTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val h = rememberHover()
    Column(Modifier.mouseTarget(h, onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = DesktopType.label, color = if (selected || h.lit) Color.White else Blush.colors.textMuted)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(18.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(if (selected) Blush.colors.accent.color else Color.Transparent))
    }
}

/** The profile picture at the top right; a click opens a small menu (a click anywhere else closes it). */
@Composable
private fun ProfileMenu(name: String, imageUrl: String?, onSwitchProfile: () -> Unit, onSignOut: () -> Unit, onChangeServer: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val h = rememberHover()
    Box {
        Box(Modifier.size(34.dp).probe("profile-menu").clip(RoundedCornerShape(50)).mouseTarget(h) { open = !open }, contentAlignment = Alignment.Center) {
            Avatar(name, imageUrl, if (h.lit || open) 34.dp else 30.dp)
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, 90),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier.width(200.dp).clip(RoundedCornerShape(12.dp)).background(Blush.colors.surfaceRaised).padding(6.dp),
                ) {
                    Text(name, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = DesktopType.title)
                    MenuRow("Switch profile") { open = false; onSwitchProfile() }
                    MenuRow("Sign out") { open = false; onSignOut() }
                    MenuRow("Change server") { open = false; onChangeServer() }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    val h = rememberHover()
    Text(
        text,
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (h.lit) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .mouseTarget(h, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        style = DesktopType.body,
        color = if (h.lit) Color.White else Blush.colors.textSoft,
    )
}

/**
 * Everything after sign-in: the top bar over the current page. [underBar] pages (Home) run up behind the bar; the
 * rest start below it.
 */
@Composable
fun Shell(session: Session, current: String, underBar: Boolean = false, content: @Composable () -> Unit) {
    val user = session.repo.currentUser
    Box(Modifier.fillMaxSize().background(Blush.colors.background)) {
        Box(Modifier.fillMaxSize().padding(top = if (underBar) 0.dp else 56.dp)) { content() }
        TopBar(
            current = current,
            userName = user?.name ?: "",
            userImage = user?.let { session.repo.userImageUrl(it.userId, it.imageTag) },
            onHome = { session.reset(Screen.Home) },
            onLibraries = { session.reset(Screen.Libraries) },
            onSearch = { if (session.screen != Screen.Search) session.go(Screen.Search) },
            onSwitchProfile = session::switchProfile,
            onSignOut = session::signOut,
            onChangeServer = session::changeServer,
        )
    }
}

// ---------- Libraries ----------

@Composable
fun LibrariesScreen(libraries: List<LibraryCard>?, error: String?, onOpen: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = PageSide)) {
        Spacer(Modifier.height(12.dp))
        Text("Libraries", style = DesktopType.headline)
        Spacer(Modifier.height(18.dp))
        when {
            error != null -> Note(error, error = true)
            libraries == null -> Note("Loading…")
            libraries.isEmpty() -> Note("No libraries to show.")
            else -> LazyVerticalGrid(
                GridCells.Adaptive(260.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp),
            ) {
                itemsIndexed(libraries) { i, lib -> LibraryTile(lib) { onOpen(i) } }
            }
        }
    }
}

@Composable
private fun LibraryTile(lib: LibraryCard, onClick: () -> Unit) {
    Column {
        HoverTile(onClick, Modifier.fillMaxWidth().aspectRatio(16f / 9f).probe(lib.name), background = tintFor(lib.name).copy(alpha = 0.6f)) {
            // Jellyfin's library pictures usually have the library's name painted on, so the name only goes on the tile
            // when there's no picture; it is always written underneath.
            if (lib.imageUrl != null) {
                AsyncImage(lib.imageUrl, contentDescription = lib.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(lib.name, Modifier.align(Alignment.Center), style = DesktopType.headline, color = Color.White.copy(alpha = 0.85f))
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(lib.name, style = DesktopType.title)
            Spacer(Modifier.width(8.dp))
            Text(lib.count?.let { "$it ${if (it == 1) "title" else "titles"}" } ?: "", Modifier.padding(bottom = 1.dp), style = DesktopType.caption, color = Blush.colors.textDim)
        }
    }
}

@Composable
fun LibrariesRoute(session: Session) {
    var libs by remember { mutableStateOf<List<BaseItemDto>?>(null) }
    var counts by remember { mutableStateOf(mapOf<java.util.UUID, Int>()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { session.repo.libraries() }.onSuccess { list ->
            libs = list
            counts = coroutineScope {
                list.map { l -> async { l.id to session.repo.libraryCount(l) } }.awaitAll()
                    .mapNotNull { (id, n) -> n?.let { id to it } }.toMap()
            }
        }.onFailure { error = "Couldn't load your libraries. Check the server is on." }
    }
    val cards = libs?.map { LibraryCard(it.name ?: "Library", session.repo.thumbUrl(it, 640), counts[it.id]) }
    LibrariesScreen(cards, error) { i -> libs?.getOrNull(i)?.let { session.go(Screen.Library(it)) } }
}

// ---------- One library ----------

@Composable
fun LibraryScreen(title: String, total: Int?, posters: List<PosterCard>, error: String?, onBack: () -> Unit, onNearEnd: () -> Unit, onOpen: (Int) -> Unit = {}) {
    val grid = rememberLazyGridState()
    LaunchedEffect(grid, posters.size) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (posters.isNotEmpty() && last >= posters.size - 24) onNearEnd()
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = PageSide)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextLink("‹ Libraries", onBack, Modifier.offset(x = (-8).dp))
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = DesktopType.headline)
            Spacer(Modifier.width(12.dp))
            Text(total?.let { "$it titles" } ?: "", Modifier.padding(bottom = 3.dp), style = DesktopType.caption, color = Blush.colors.textDim)
        }
        Spacer(Modifier.height(16.dp))
        if (error != null) Note(error, error = true)
        LazyVerticalGrid(
            GridCells.Adaptive(140.dp),
            state = grid,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp),
        ) {
            itemsIndexed(posters) { i, p -> Poster(p) { onOpen(i) } }
        }
    }
}

@Composable
private fun Poster(p: PosterCard, onOpen: () -> Unit) {
    Column {
        HoverTile(onOpen, Modifier.fillMaxWidth().aspectRatio(2f / 3f), corner = 8.dp, background = p.tint ?: tintFor(p.name).copy(alpha = 0.5f)) {
            if (p.imageUrl != null) AsyncImage(p.imageUrl, contentDescription = p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(p.name, Modifier.align(Alignment.Center).padding(10.dp), style = DesktopType.label, color = Blush.colors.textSoft)
        }
        Spacer(Modifier.height(7.dp))
        Text(p.name, style = DesktopType.label, maxLines = 1)
        Text(p.year?.toString() ?: " ", style = DesktopType.caption, color = Blush.colors.textDim)
    }
}

@Composable
fun LibraryRoute(session: Session, library: BaseItemDto) {
    val repo: Repo = session.repo
    var items by remember(library.id) { mutableStateOf(listOf<BaseItemDto>()) }
    var total by remember(library.id) { mutableStateOf<Int?>(null) }
    var loading by remember(library.id) { mutableStateOf(false) }
    var error by remember(library.id) { mutableStateOf<String?>(null) }
    fun more() {
        if (loading || (total != null && items.size >= total!!)) return
        loading = true
        session.launch {
            runCatching {
                repo.browsePage(types = repo.libraryTypes(library), sortBy = ItemSortBy.SORT_NAME, parentId = library.id, start = items.size, limit = 120)
            }.onSuccess { (page, count) -> items = items + page; total = count }
                .onFailure { error = "Couldn't load this library." }
            loading = false
        }
    }
    LaunchedEffect(library.id) { more() }
    val posters = remember(items) { items.map { PosterCard(it.name ?: "", it.productionYear, repo.posterUrl(it), it.posterTint()) } }
    LibraryScreen(library.name ?: "Library", total, posters, error, onBack = session::back, onNearEnd = ::more, onOpen = { i -> items.getOrNull(i)?.let { session.go(Screen.Details(it.id, it)) } })
}
