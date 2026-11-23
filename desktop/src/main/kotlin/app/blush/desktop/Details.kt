@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.blush.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blush.tv.data.Repo
import app.blush.tv.ui.ageRating
import app.blush.tv.ui.components.Icons
import app.blush.tv.ui.durationText
import app.blush.tv.ui.episodeCode
import app.blush.tv.ui.progress
import app.blush.tv.ui.scoreText
import app.blush.tv.ui.theme.Blush
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// A title's page on the Mac, after the TV's details page: the banner (picture, title, details, buttons), then tabs
// underneath (Episodes / In this collection / More like this / Extras / Details) over a blurred copy of the picture.

enum class DetailsTab(val label: String) { Episodes("Episodes"), Collection("In this collection"), Similar("More like this"), Extras("Extras"), Info("Details") }

/** Everything a title's page shows. Filled in by [DetailsRoute] (or by hand for screenshots). */
class DetailsState(preview: BaseItemDto?) {
    var item by mutableStateOf(preview)
    var error by mutableStateOf<String?>(null)
    var seasons by mutableStateOf<List<BaseItemDto>>(emptyList())
    var seasonId by mutableStateOf<UUID?>(null)
    var episodes by mutableStateOf<List<BaseItemDto>?>(null)
    var nextEpisode by mutableStateOf<BaseItemDto?>(null)
    var similar by mutableStateOf<List<BaseItemDto>?>(null)
    var children by mutableStateOf<List<BaseItemDto>>(emptyList())
    var extras by mutableStateOf<List<BaseItemDto>>(emptyList())
    var favourite by mutableStateOf(preview?.userData?.isFavorite == true)
    var played by mutableStateOf(preview?.userData?.played == true)
    var tab by mutableStateOf<DetailsTab?>(null)

    val tabs: List<DetailsTab>
        get() = buildList {
            val t = item?.type
            if (t == BaseItemKind.SERIES) add(DetailsTab.Episodes)
            if (t == BaseItemKind.BOX_SET) add(DetailsTab.Collection)
            if (similar == null || similar!!.isNotEmpty()) add(DetailsTab.Similar)
            if (extras.isNotEmpty()) add(DetailsTab.Extras)
            add(DetailsTab.Info)
        }

    suspend fun load(repo: Repo, id: UUID) = coroutineScope {
        val loaded = runCatching { repo.item(id) }.getOrElse { error = "Couldn't load this title."; return@coroutineScope }
        item = loaded
        favourite = loaded.userData?.isFavorite == true
        played = loaded.userData?.played == true
        tab = tab ?: when (loaded.type) {
            BaseItemKind.SERIES -> DetailsTab.Episodes
            BaseItemKind.BOX_SET -> DetailsTab.Collection
            else -> DetailsTab.Similar
        }
        launch { extras = runCatching { repo.extras(id) }.getOrDefault(emptyList()) }
        launch {
            similar = runCatching { repo.moreLikeThis(loaded) }.getOrDefault(emptyList())
            if (similar!!.isEmpty() && tab == DetailsTab.Similar) tab = DetailsTab.Info
        }
        when (loaded.type) {
            BaseItemKind.SERIES -> {
                val next = async { runCatching { repo.nextEpisodeFor(id) }.getOrNull() }
                seasons = runCatching { repo.seasons(id) }.getOrDefault(emptyList())
                nextEpisode = next.await()
                seasonId = nextEpisode?.seasonId ?: seasons.firstOrNull()?.id
                loadEpisodes(repo)
            }
            BaseItemKind.BOX_SET -> children = runCatching { repo.collectionItems(loaded) }.getOrDefault(emptyList())
            else -> Unit
        }
    }

    suspend fun loadEpisodes(repo: Repo) {
        val series = item?.id ?: return
        val season = seasonId ?: return
        episodes = null
        episodes = runCatching { repo.episodes(series, season) }.getOrDefault(emptyList())
    }
}

@Composable
fun DetailsScreen(
    state: DetailsState,
    pictures: Pictures,
    onOpen: (BaseItemDto) -> Unit,
    onPlay: (BaseItemDto) -> Unit,
    onSeason: (UUID) -> Unit,
    onFavourite: (Boolean) -> Unit,
    onPlayed: (Boolean) -> Unit,
) {
    val item = state.item
    val page = Blush.colors.background
    val list = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize().background(page)) {
        val bannerHeight = (maxHeight * 0.74f).coerceIn(440.dp, 720.dp)
        val bannerPx = with(LocalDensity.current) { bannerHeight.toPx() }
        // How far the page has scrolled past the banner (0 at the top, 1 once the tabs reach the top of the window).
        val blurAmount = {
            if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / (bannerPx * 0.7f)).coerceIn(0f, 1f)
        }
        // The picture stays still and the page scrolls over it; as you scroll down it turns blurred and darker (the blurred
        // copy fades in over the sharp one), so there's never an edge between the two.
        if (item != null) Box(Modifier.fillMaxSize()) {
            val url = pictures.backdrop(item)
            if (url != null) {
                AsyncImage(url, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                AsyncImage(
                    url, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = blurAmount() }.blur(60.dp),
                )
            }
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val t = blurAmount()
                    drawRect(page.copy(alpha = 0.55f * t))
                    drawRect(Brush.horizontalGradient(0f to page.copy(alpha = 0.95f), 0.42f to page.copy(alpha = 0.55f), 0.75f to Color.Transparent))
                    drawRect(Brush.verticalGradient(0f to page.copy(alpha = 0.55f), 0.14f to Color.Transparent, 0.5f to Color.Transparent, 1f to page.copy(alpha = 0.92f)))
                },
            )
        }
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 48.dp)) {
            item(key = "banner") { DetailsBanner(state, pictures, bannerHeight, onPlay, onFavourite, onPlayed) }
            item(key = "tabs") { TabsRow(state) }
            item(key = "content") {
                Box(Modifier.padding(horizontal = PageSide).padding(top = 22.dp)) {
                    when (state.tab) {
                        DetailsTab.Episodes -> EpisodesTab(state, pictures, onSeason, onPlay)
                        DetailsTab.Collection -> PosterGrid(state.children, pictures, onOpen)
                        DetailsTab.Similar -> state.similar?.let { PosterGrid(it.take(12), pictures, onOpen) } ?: Note("Finding similar titles…")
                        DetailsTab.Extras -> ExtrasGrid(state.extras, pictures, onPlay)
                        DetailsTab.Info -> item?.let { InfoTab(it) }
                        null -> Unit
                    }
                }
            }
        }
        if (state.error != null) Note(state.error, Modifier.align(Alignment.Center), error = true)
    }
}

@Composable
private fun DetailsBanner(
    state: DetailsState,
    pictures: Pictures,
    height: androidx.compose.ui.unit.Dp,
    onPlay: (BaseItemDto) -> Unit,
    onFavourite: (Boolean) -> Unit,
    onPlayed: (Boolean) -> Unit,
) {
    val item = state.item
    Box(Modifier.fillMaxWidth().height(height)) {
        if (item == null) return@Box
        Column(Modifier.align(Alignment.BottomStart).padding(start = PageSide, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.type == BaseItemKind.EPISODE) {
                    Text(item.seriesName.orEmpty().uppercase(), style = DesktopType.caption.copy(letterSpacing = 1.8.sp, fontWeight = FontWeight.W700), color = Blush.colors.accent.color)
                    Text(item.name.orEmpty(), style = DesktopType.headline.copy(fontSize = 34.sp, fontWeight = FontWeight.W800, lineHeight = 38.sp), maxLines = 2)
                } else {
                    Box(Modifier.width(440.dp).height(150.dp), contentAlignment = Alignment.BottomStart) {
                        val logo = pictures.logo(item)
                        if (logo != null) AsyncImage(logo, item.name, contentScale = ContentScale.Fit, alignment = Alignment.BottomStart, modifier = Modifier.widthIn(max = 420.dp).heightIn(max = 140.dp).fillMaxSize())
                        else Text(item.name.orEmpty(), style = DesktopType.headline.copy(fontSize = 38.sp, fontWeight = FontWeight.W800, lineHeight = 42.sp), maxLines = 2)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildList {
                            if (item.type == BaseItemKind.EPISODE) item.episodeCode()?.let { add(it) }
                            item.productionYear?.let { add(it.toString()) }
                            when (item.type) {
                                BaseItemKind.SERIES -> item.childCount?.let { add(if (it == 1) "1 Season" else "$it Seasons") }
                                else -> durationText(item.runTimeTicks)?.let { add(it) }
                            }
                            item.genres?.take(3)?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
                            item.scoreText()?.let { add(it) }
                        }.joinToString(" • "),
                        style = DesktopType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.W500),
                        color = Color(0xFF9B9EA8),
                        maxLines = 1,
                    )
                    item.ageRating()?.let { Badge(it) }
                }
                Text(item.overview.orEmpty(), style = DesktopType.body.copy(fontSize = 13.sp, lineHeight = 19.sp), color = Blush.colors.textSoft, maxLines = 4)
            }
            // What Play plays: a series' next episode, otherwise the title itself.
            val target = if (item.type == BaseItemKind.SERIES) state.nextEpisode else item
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val progress = target?.progress()
                val label = when {
                    item.type == BaseItemKind.SERIES && target != null -> (if (progress != null) "Resume " else "Play ") + (target.episodeCode() ?: "")
                    progress != null -> "Resume"
                    else -> "Play"
                }
                PlayPill(label.trim(), progress) { target?.let(onPlay) }
                RoundButton(
                    if (state.favourite) Icons.Check else Icons.Plus,
                    if (state.favourite) "Remove from watchlist" else "Add to watchlist",
                    tint = if (state.favourite) Blush.colors.accent.color else Blush.colors.text,
                    background = if (state.favourite) Blush.colors.accent.color.copy(alpha = 0.22f) else null,
                ) { onFavourite(!state.favourite) }
                RoundButton(
                    Icons.Eye,
                    if (state.played) "Mark as unwatched" else "Mark as watched",
                    tint = if (state.played) Blush.colors.accent.color else Blush.colors.text,
                    background = if (state.played) Blush.colors.accent.color.copy(alpha = 0.22f) else null,
                ) { onPlayed(!state.played) }
                if (progress != null && target != null) RoundButton(Icons.Restart, "Play from the beginning") { onPlay(target) }
            }
        }
    }
}

/** A small outlined label (age rating). */
@Composable
private fun Badge(text: String) {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, style = DesktopType.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.W700), color = Blush.colors.textSoft)
    }
}

@Composable
private fun TabsRow(state: DetailsState) {
    Box(Modifier.padding(horizontal = PageSide).fillMaxWidth()) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            state.tabs.forEach { t ->
                val h = rememberHover()
                val selected = t == state.tab
                Column(Modifier.mouseTarget(h) { state.tab = t }.probe("tab-${t.label}"), horizontalAlignment = Alignment.Start) {
                    Text(
                        t.label,
                        Modifier.padding(vertical = 10.dp),
                        style = DesktopType.title.copy(fontSize = 15.sp, fontWeight = FontWeight.W600),
                        color = when { selected -> Color.White; h.lit -> Blush.colors.textSoft; else -> Blush.colors.textDim },
                    )
                    Box(Modifier.width(if (selected) 28.dp else 0.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Blush.colors.accent.color))
                }
            }
        }
    }
}

/** Posters six across, sized to fill the width of the window (More like this shows two rows of them). */
@Composable
private fun PosterGrid(items: List<BaseItemDto>, pictures: Pictures, onOpen: (BaseItemDto) -> Unit) {
    if (items.isEmpty()) { Note("Nothing here yet."); return }
    PosterColumns(items, columns = 6, pictures = pictures, onOpen = onOpen)
}

@Composable
private fun ExtrasGrid(items: List<BaseItemDto>, pictures: Pictures, onPlay: (BaseItemDto) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items.forEach { e ->
            Column(Modifier.width(252.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                HoverTile({ onPlay(e) }, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    pictures.thumb(e)?.let { AsyncImage(it, e.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Text(e.name.orEmpty(), style = DesktopType.label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EpisodesTab(state: DetailsState, pictures: Pictures, onSeason: (UUID) -> Unit, onPlay: (BaseItemDto) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (state.seasons.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.seasons.forEach { s -> HoverPill(s.name ?: "Season", s.id == state.seasonId, onClick = { onSeason(s.id) }) }
        }
        val episodes = state.episodes
        when {
            episodes == null -> Note("Loading episodes…")
            episodes.isEmpty() -> Note("No episodes here yet.")
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { episodes.forEach { EpisodeRow(it, pictures, it.id == state.nextEpisode?.id) { onPlay(it) } } }
        }
    }
}

@Composable
private fun EpisodeRow(e: BaseItemDto, pictures: Pictures, isNext: Boolean, onClick: () -> Unit) {
    val h = rememberHover()
    Row(
        Modifier.fillMaxWidth().widthIn(max = 980.dp).clip(RoundedCornerShape(12.dp))
            .background(if (h.lit) Color.White.copy(alpha = 0.07f) else Color.Transparent)
            .mouseTarget(h, onClick = onClick).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.width(220.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Blush.colors.surface)) {
            pictures.thumb(e)?.let { AsyncImage(it, e.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            e.progress()?.let { p ->
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.45f))) {
                    Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).height(4.dp).background(Blush.colors.accent.color))
                }
            }
        }
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(listOfNotNull(e.indexNumber?.let { "$it." }, e.name).joinToString(" "), style = DesktopType.title, maxLines = 1)
                if (isNext) Text("UP NEXT", style = DesktopType.caption.copy(fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.W700), color = Blush.colors.accent.color)
                if (e.userData?.played == true) Text("Watched", style = DesktopType.caption, color = Blush.colors.textDim)
            }
            durationText(e.runTimeTicks)?.let { Text(it, style = DesktopType.caption, color = Blush.colors.textDim) }
            Text(e.overview.orEmpty(), style = DesktopType.body, color = Blush.colors.textMuted, maxLines = 2)
        }
    }
}

private val longDate = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK)

@Composable
private fun InfoTab(item: BaseItemDto) {
    Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
        Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(item.name.orEmpty(), style = DesktopType.title.copy(fontSize = 17.sp))
            Text(item.overview.orEmpty(), style = DesktopType.body.copy(fontSize = 13.sp, lineHeight = 20.sp), color = Blush.colors.textSoft)
        }
        Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item.genres?.takeIf { it.isNotEmpty() }?.let { Fact("Genres", it.joinToString(", ")) }
            item.premiereDate?.let { Fact("Released", it.toLocalDate().format(longDate)) }
            durationText(item.runTimeTicks)?.let { Fact("Running time", it) }
            item.officialRating?.let { Fact("Rating", it) }
            item.studios?.takeIf { it.isNotEmpty() }?.let { Fact("Studio", it.mapNotNull { s -> s.name }.take(3).joinToString(", ")) }
        }
        val people = item.people.orEmpty()
        Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            people.filter { it.type.serialName == "Director" }.takeIf { it.isNotEmpty() }?.let { Fact("Directed by", it.mapNotNull { p -> p.name }.joinToString(", ")) }
            people.filter { it.type.serialName == "Writer" }.takeIf { it.isNotEmpty() }?.let { Fact("Written by", it.mapNotNull { p -> p.name }.take(4).joinToString(", ")) }
            people.filter { it.type.serialName == "Actor" }.takeIf { it.isNotEmpty() }?.let { Fact("Starring", it.mapNotNull { p -> p.name }.take(8).joinToString(", ")) }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = DesktopType.caption, color = Blush.colors.textDim)
        Text(value, style = DesktopType.label.copy(fontSize = 13.sp), color = Blush.colors.textSoft)
    }
}

// ---------- Route ----------

@Composable
fun DetailsRoute(session: Session, id: UUID, preview: BaseItemDto?) {
    val repo = session.repo
    val state = remember(id) { DetailsState(preview?.takeIf { it.id == id }) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) { state.load(repo, id) }
    DetailsScreen(
        state,
        repo.pictures(),
        onOpen = { session.go(Screen.Details(it.id, it)) },
        onPlay = { session.toast("Playback comes in the next Mac builds.") },
        onSeason = { s -> state.seasonId = s; scope.launch { state.loadEpisodes(repo) } },
        onFavourite = { want ->
            state.favourite = want
            scope.launch { if (!runCatching { repo.setFavorite(id, want) }.getOrDefault(false)) state.favourite = !want }
        },
        onPlayed = { want ->
            state.played = want
            // setPlayed answers with the new watched state, so success is "it now matches what was asked".
            scope.launch { if (runCatching { repo.setPlayed(id, want) == want }.getOrDefault(false).not()) state.played = !want }
        },
    )
}
