package app.blush.tv.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blush.tv.ui.components.LoadingDots
import app.blush.tv.ui.AppState
import app.blush.tv.ui.ContentEnd
import app.blush.tv.ui.ContentStart
import app.blush.tv.ui.PageTop
import app.blush.tv.ui.Route
import app.blush.tv.ui.components.CenteredMessage
import app.blush.tv.ui.components.LiteTile
import app.blush.tv.ui.components.RemoteImage
import app.blush.tv.ui.components.fadeInOnAppear
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.theme.Text
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import java.util.UUID

private val ListRoom = 14.dp

/** The server's libraries as a few wide tiles, each with how many titles it holds. */
@Composable
fun LibrariesScreen(app: AppState) {
    val repo = app.repo
    var libraries by remember { mutableStateOf<List<BaseItemDto>?>(null) }
    var counts by remember { mutableStateOf<Map<UUID, Int>>(emptyMap()) }
    val first = remember { FocusRequester() }

    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val result = runCatching { repo.libraries() }
        result.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        failed = result.isFailure
        libraries = result.getOrDefault(emptyList())
    }
    // The counts arrive one by one, all asked for at once; the tiles don't wait for them.
    LaunchedEffect(libraries) {
        val list = libraries ?: return@LaunchedEffect
        coroutineScope {
            list.forEach { lib -> launch { repo.libraryCount(lib)?.let { n -> counts = counts + (lib.id to n) } } }
        }
    }
    LaunchedEffect(libraries) {
        if (libraries.isNullOrEmpty()) return@LaunchedEffect
        repeat(20) {
            delay(40)
            if (runCatching { first.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }

    Column(Modifier.fillMaxSize().background(Blush.colors.background).padding(start = ContentStart, top = PageTop)) {
        Text("Libraries", style = BlushType.headline)
        Text(
            "Everything on ${repo.store.server?.name ?: "your server"}",
            style = BlushType.body,
            color = Blush.colors.accent.color,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(22.dp))
        val list = libraries
        when {
            list == null -> LoadingDots()
            failed -> CenteredMessage("Couldn't reach your server", "Check it's on and on the same network, then come back.")

            list.isEmpty() -> CenteredMessage("No libraries yet", "Add a library on your Jellyfin server and it will appear here.")
            // Reaches `room` past the header's left edge, so the focused tile's zoom is never cut off by the box it sits in.
            else -> BoxWithConstraints(Modifier.fillMaxSize().offset(x = -ListRoom).fadeInOnAppear()) {
                val columns = 3
                val gap = 18.dp
                // Room around the tiles for the focus zoom: the list reaches this far past the header's left edge and the tiles are
                // padded back in, so the first tile lines up exactly with the header text.
                val room = ListRoom
                val width = (maxWidth - ContentEnd - gap * (columns - 1)) / columns
                // Three to a row, all the same size, however many libraries there are. A plain scrolling column keeps every tile built, so
                // Up, Down, Left and Right find their neighbours by position, and the page follows the remote when there are more rows than fit.
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = room, top = room, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    list.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEach { lib ->
                                LibraryCard(
                                    lib, counts[lib.id], width, repo.posterUrl(lib, 700) ?: repo.thumbUrl(lib, 700),
                                    onClick = {
                                        // Opening a library afresh starts at its top; only Back from a title keeps the place.
                                        app.gridMemory.keys.removeAll { it.startsWith("lib:${lib.id}") }
                                        app.libraryFilters.remove(lib.id.toString())
                                        app.go(Route.Library(lib))
                                    },
                                    modifier = if (lib.id == list.first().id) Modifier.focusRequester(first) else Modifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(lib: BaseItemDto, count: Int?, width: androidx.compose.ui.unit.Dp, imageUrl: String?, onClick: () -> Unit, modifier: Modifier) {
    LiteTile(width = width, aspect = 16f / 9f, onClick = onClick, modifier = modifier) {
        RemoteImage(imageUrl, Modifier.fillMaxSize(), crossfade = false)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color(0xE60C0D12))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(lib.name.orEmpty(), style = BlushType.subhead.copy(), maxLines = 1)
            Text(count?.let { countText(it, lib) } ?: " ", style = BlushType.caption, color = Blush.colors.accent.color)
        }
    }
}

/** "312 films", "1 series", "48 collections"… */
internal fun countText(n: Int, lib: BaseItemDto): String {
    val (one, many) = when (lib.collectionType) {
        CollectionType.MOVIES -> "film" to "films"
        CollectionType.TVSHOWS -> "series" to "series"
        CollectionType.BOXSETS -> "collection" to "collections"
        else -> "title" to "titles"
    }
    return "%,d %s".format(n, if (n == 1) one else many)
}
