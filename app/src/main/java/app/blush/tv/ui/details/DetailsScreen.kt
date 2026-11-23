package app.blush.tv.ui.details

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blush.tv.data.Repo
import app.blush.tv.ui.AppState
import app.blush.tv.ui.DetailsMemory
import app.blush.tv.ui.ContentEnd
import app.blush.tv.ui.ContentStart
import app.blush.tv.ui.components.BannerButtonsBottom
import app.blush.tv.ui.components.BannerButtonsGap
import app.blush.tv.ui.components.BannerDescriptionHeight
import app.blush.tv.ui.components.BannerDescriptionStyle
import app.blush.tv.ui.components.BannerInfoGap
import app.blush.tv.ui.components.BannerLogoHeight
import app.blush.tv.ui.components.BannerInfoWidth
import app.blush.tv.ui.components.BannerTitleInfo
import app.blush.tv.ui.player.PlayerReports
import app.blush.tv.ui.Route
import app.blush.tv.ui.components.Badge
import app.blush.tv.ui.components.CenteredMessage
import app.blush.tv.ui.components.FocusSurface
import app.blush.tv.ui.components.Icons
import app.blush.tv.ui.components.LiteTile
import app.blush.tv.ui.components.ProgressLine
import app.blush.tv.ui.components.LoadingDots
import app.blush.tv.ui.components.BlurredImage
import app.blush.tv.ui.components.fadeInOnAppear
import app.blush.tv.ui.components.fadeInOver
import app.blush.tv.ui.components.LogoImage
import app.blush.tv.ui.components.Pill
import app.blush.tv.ui.components.PosterCard
import app.blush.tv.ui.components.PrimaryButton
import app.blush.tv.ui.home.HeroPlayButton
import app.blush.tv.ui.components.RemoteImage
import app.blush.tv.ui.components.RoundIconButton
import app.blush.tv.ui.components.SecondaryButton
import app.blush.tv.ui.ageRating
import app.blush.tv.ui.displayTitle
import app.blush.tv.ui.scoreText
import app.blush.tv.ui.durationText
import app.blush.tv.ui.episodeCode
import app.blush.tv.ui.metaLine
import app.blush.tv.ui.progress
import app.blush.tv.ui.qualityBadges
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.theme.Text
import app.blush.tv.ui.timeLeft
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** Left edge of everything on this page (there's no side bar here). */
/** Where the lower half starts: the same left edge as the banner above it (and as Home), so the two line up. */
internal val PageStart = ContentStart

private enum class Tab(val label: String) {
    Episodes("Episodes"),
    Collection("In this collection"),
    Similar("More like this"),
    Extras("Extras"),
    Info("Details"),
}

/** Scrolling on this page is done by hand (the two "magnet" positions), never by focus moving about. */
@OptIn(ExperimentalFoundationApi::class)
private object NoAutoScroll : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun DetailsScreen(app: AppState, itemId: UUID, preview: BaseItemDto? = null) {
    val repo = app.repo
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Starts with what the previous screen knew, so the page appears at once; the full details replace it.
    var item by remember { mutableStateOf(preview?.takeIf { it.id == itemId }) }
    var error by remember { mutableStateOf<String?>(null) }
    var seasons by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var seasonId by remember { mutableStateOf<UUID?>(null) }
    var episodes by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var episodesLoaded by remember { mutableStateOf(false) }
    var nextEpisode by remember { mutableStateOf<BaseItemDto?>(null) }
    // A series has no Play button until its next episode is known; until then OK on the heart (where the remote waits) does nothing.
    var nextKnown by remember { mutableStateOf(false) }
    
    var similar by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var similarLoaded by remember { mutableStateOf(false) }
    var children by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var extras by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var favourite by remember { mutableStateOf(preview?.userData?.isFavorite == true) }
    var played by remember { mutableStateOf(preview?.userData?.played == true) }
    var tab by remember { mutableStateOf<Tab?>(null) }
    // Coming back with Back (e.g. from an actor's page): the tab, scroll and tile you were on are restored.
    val mem = remember(itemId) { app.detailsMemory.getOrPut(itemId) { DetailsMemory() } }
    val restoring = remember(itemId) { mem.armed.also { armed -> mem.armed = false; if (armed) mem.pending = mem.focused else mem.pending = null } }
    LaunchedEffect(tab) { tab?.let { mem.tab = it.name } }
    var message by remember { mutableStateOf<String?>(null) }
    val playFocus = remember { FocusRequester() }
    val tabFocus = remember { Tab.entries.associateWith { FocusRequester() } }

    LaunchedEffect(itemId) {
        // Coming back from the player: let the position it just saved reach the server first.
        PlayerReports.settle()
        runCatching { repo.item(itemId) }
            .onSuccess { loaded ->
                item = loaded
                favourite = loaded.userData?.isFavorite == true
                played = loaded.userData?.played == true
                tab = if (restoring && mem.tab != null) runCatching { Tab.valueOf(mem.tab!!) }.getOrNull() else null
                if (tab == null) tab = when (loaded.type) {
                    BaseItemKind.SERIES -> Tab.Episodes
                    BaseItemKind.BOX_SET -> Tab.Collection
                    else -> Tab.Similar
                }
                launch {
                    similar = runCatching { repo.moreLikeThis(loaded) }.getOrDefault(emptyList())
                    val loader = coil3.SingletonImageLoader.get(context)
                    // The grey tiles stay until the first two rows' pictures are in hand (two seconds at most), then they change over to the
                    // real tiles, whose pictures fade in: nothing pops in.
                    withTimeoutOrNull(2_000) {
                        similar.take(14).map { s ->
                            async { repo.posterUrl(s)?.let { runCatching { loader.execute(coil3.request.ImageRequest.Builder(context).data(it).build()) } } }
                        }.awaitAll()
                    }
                    similarLoaded = true
                    if (similar.isEmpty() && tab == Tab.Similar) tab = Tab.Info
                    // The rest are fetched quietly, ready for when you scroll down to them.
                    similar.drop(14).take(14).forEach { s -> repo.posterUrl(s)?.let { loader.enqueue(coil3.request.ImageRequest.Builder(context).data(it).build()) } }
                }
                launch { extras = runCatching { repo.extras(itemId) }.getOrDefault(emptyList()) }
                when (loaded.type) {
                    BaseItemKind.SERIES -> {
                        // Both asked of the server at once rather than one after the other.
                        val nextAsk = async { runCatching { repo.nextEpisodeFor(itemId) }.getOrNull() }
                        val seasonsAsk = async { runCatching { repo.seasons(itemId) }.getOrDefault(emptyList()) }
                        val next = nextAsk.await()
                    nextEpisode = next
                    nextKnown = true
                        seasons = seasonsAsk.await()
                        // Always opens on the left-most season; the next episode is still marked in whichever season it is in.
                        seasonId = seasons.firstOrNull()?.id
                        if (seasons.isEmpty()) episodesLoaded = true

                    }
                    BaseItemKind.BOX_SET -> children = runCatching { repo.collectionItems(loaded) }.getOrDefault(emptyList())
                    else -> {}
                }
            }
            .onFailure { error = "Couldn't load this title."; nextKnown = true }
    }

    LaunchedEffect(seasonId) {
        val series = item ?: return@LaunchedEffect
        val season = seasonId ?: return@LaunchedEffect
        episodes = runCatching { repo.episodes(series.id, season) }.getOrDefault(emptyList())
        episodesLoaded = true

    }

    // Focus goes to Play when the page appears, and again once the full details (and a series' next episode) arrive,
    // as long as you haven't moved off the buttons yet.
    var movedOn by remember { mutableStateOf(restoring && mem.pending != null) }
    var bannerFocused by remember { mutableStateOf(false) }
    var playHasFocus by remember { mutableStateOf(false) }
    val hasTarget = nextEpisode != null || episodes.isNotEmpty() || (item != null && item?.type != BaseItemKind.SERIES)
    LaunchedEffect(item?.id, hasTarget) { if (item != null && !movedOn) { delay(30); runCatching { playFocus.requestFocus() } } }
    LaunchedEffect(message) { if (message != null) { delay(4000); message = null } }

    val current = item
    if (current == null) {
        if (error != null) CenteredMessage(error!!) else LoadingDots()
        return
    }

    // What the main button plays.
    val target: BaseItemDto? = when (current.type) {
        BaseItemKind.SERIES -> nextEpisode ?: episodes.firstOrNull()
        BaseItemKind.BOX_SET -> null
        else -> current
    }
    val targetProgress = target?.progress()
    val playLabel = when {
        target == null -> null
        current.type == BaseItemKind.SERIES && targetProgress != null -> "Resume ${target.episodeCode().orEmpty()}"
        current.type == BaseItemKind.SERIES -> "Play ${target.episodeCode().orEmpty()}"
        targetProgress != null -> "Resume"
        else -> "Play"
    }

    val tabs = buildList {
        if (current.type == BaseItemKind.SERIES) add(Tab.Episodes)
        if (current.type == BaseItemKind.BOX_SET) add(Tab.Collection)
        // The tab is there from the start (with grey tiles) while it's worked out; it goes only if nothing turns up.
        if (!similarLoaded || similar.isNotEmpty()) add(Tab.Similar)
        // Only shown when the server actually has extras for this title.
        if (extras.isNotEmpty()) add(Tab.Extras)
        add(Tab.Info)
    }

    // Opened from a tile that already had the title's details, it's shown at once; otherwise it fades in when loaded.
    BoxWithConstraints(Modifier.fillMaxSize().background(Blush.colors.background).fadeInOnAppear(durationMs = 380)) {
        val screen = maxHeight
        // The tabs and the top of the first row peek in under the banner.
        val heroHeight = screen - 118.dp
        val heroPx = with(LocalDensity.current) { heroHeight.roundToPx() }
        // Written by the focus callbacks and never read while the page is built, so changing it rebuilds nothing.
        var lower by remember { mutableStateOf(restoring && mem.pending != null && mem.lower) }
        // 0 = the banner, 1 = the lower half. The whole page moves by sliding as one layer (and the picture changes with it), which
        // the graphics side does without measuring or building anything again: that is what keeps the move smooth.
        val slide = remember { androidx.compose.animation.core.Animatable(if (lower) 1f else 0f) }
        // The "magnet": focus in the lower half snaps it up to fill the screen; back in the banner snaps it down.
        LaunchedEffect(Unit) {
            androidx.compose.runtime.snapshotFlow { lower }.collectLatest { low ->
                mem.lower = low
                slide.animateTo(if (low) 1f else 0f, tween(340, easing = androidx.compose.animation.core.CubicBezierEasing(0.32f, 0f, 0.1f, 1f)))
            }
        }

        // Background: the sharp artwork, giving way to a soft (tiny, stretched) copy and a dark wash below.
        RemoteImage(repo.backdropUrl(current), Modifier.fillMaxSize().graphicsLayer { alpha = 1f - slide.value }, ContentScale.Crop, crossfade = true)
        BlurredImage(repo.backdropUrl(current, 320), Modifier.fillMaxSize().graphicsLayer { alpha = slide.value })
        Box(
            Modifier.fillMaxSize().background(
                // The same shading as the home banner, so opening a title from there looks like one continuous page.
                Brush.horizontalGradient(0f to Blush.colors.background.copy(alpha = 0.95f), 0.42f to Blush.colors.background.copy(alpha = 0.55f), 0.75f to Color.Transparent),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 0.78f to Blush.colors.background.copy(alpha = 0.85f), 1f to Blush.colors.background)))
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = slide.value }.background(Blush.colors.background.copy(alpha = 0.72f)))

        val defaultScroll = LocalBringIntoViewSpec.current
        CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll, LocalDetailsMemory provides mem) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // Taller than the screen (banner plus a full lower half), slid up and down as a single layer.
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .graphicsLayer { translationY = -heroPx * slide.value },
            ) {
                // ---------------- Banner ----------------
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                        .onFocusChanged {
                            if (it.hasFocus) { lower = false; bannerFocused = true } else if (bannerFocused) movedOn = true
                        }
                        // Down from anywhere in the banner goes to the tab that's currently showing, not whichever is nearest.
                        .focusProperties { down = tab?.let { tabFocus.getValue(it) } ?: FocusRequester.Default },
                ) {
                    // Laid out exactly like the home banner (same left edge, logo box, details line, description and
                    // button height), so coming here from "More info" keeps everything in place.
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(start = ContentStart, bottom = (heroHeight - BannerButtonsBottom).coerceAtLeast(12.dp)),
                        verticalArrangement = Arrangement.spacedBy(BannerButtonsGap),
                    ) {
                        if (current.type == BaseItemKind.EPISODE) {
                            Column(Modifier.width(500.dp), verticalArrangement = Arrangement.spacedBy(BannerInfoGap)) {
                                Text(current.seriesName.orEmpty().uppercase(), style = BlushType.kicker, color = Blush.colors.accent.color)
                                Text(current.name.orEmpty(), style = BlushType.display, maxLines = 2)
                                Row(Modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(listOfNotNull(current.episodeCode(), durationText(current.runTimeTicks), current.scoreText()).joinToString(" • "), style = BlushType.caption.copy(fontWeight = FontWeight.W500), color = Blush.colors.textSoft, maxLines = 1)
                                    current.ageRating()?.let { Badge(it) }
                                    current.qualityBadges().forEach { Badge(it) }
                                }
                                ExpandableOverview(current.overview.orEmpty(), BannerInfoWidth, ExpandedUpEpisode, playFocus)
                            }
                        } else {
                            BannerTitleInfo(
                                repo, current,
                                detailed = true,
                                extra = { current.qualityBadges().forEach { Badge(it) } },
                                description = { ExpandableOverview(current.overview.orEmpty(), BannerInfoWidth, ExpandedUp, playFocus) },
                            )
                        }
                        Row(
                            // Left on the first button (Play) does nothing: it used to be Back, which left the page on one press too many.
                            modifier = Modifier.onPreviewKeyEvent { e ->
                                if (e.key == Key.DirectionLeft && playHasFocus) {
                                    true
                                } else false
                            },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Play / Resume, Watchlist, Watched, then Restart and the time left when there is something to restart.
                            if (target != null && playLabel != null) {
                                // The same button, and the same size, as the banner's Play / Resume on Home.
                                HeroPlayButton(
                                    playLabel,
                                    progress = targetProgress,
                                    onClick = { app.go(Route.Player(target)) },
                                    modifier = Modifier.width(104.dp).focusRequester(playFocus).onFocusChanged { playHasFocus = it.hasFocus },
                                )
                            }
                            RoundIconButton(
                                if (favourite) Icons.Check else Icons.Plus,
                                if (favourite) "Remove from watchlist" else "Add to watchlist",
                                tint = if (favourite) Blush.colors.accent.color else Blush.colors.text,
                                background = if (favourite) Blush.colors.accent.color.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f),
                                onClick = {
                        if (current.type != BaseItemKind.SERIES || nextKnown) {
                            val want = !favourite
                            favourite = want
                            scope.launch { runCatching { repo.setFavorite(current.id, want) }.onFailure { favourite = !want } }
                        }
                    },
                                modifier = if (target == null) Modifier.focusRequester(playFocus).onFocusChanged { playHasFocus = it.hasFocus } else Modifier,
                            )
                            if (current.type != BaseItemKind.BOX_SET) {
                                RoundIconButton(
                                    Icons.Eye,
                                    if (played) "Mark as unwatched" else "Mark as watched",
                                    tint = if (played) Blush.colors.accent.color else Blush.colors.text,
                                    background = if (played) Blush.colors.accent.color.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f),
                                    onClick = {
                                        val want = !played
                                        played = want
                                        scope.launch { runCatching { repo.setPlayed(current.id, want) }.onFailure { played = !want } }
                                    },
                                )
                            }
                            if (target != null && targetProgress != null) {
                                RoundIconButton(Icons.Restart, "Play from the beginning", onClick = { app.go(Route.Player(target, fromStart = true)) })
                            }
                            target?.timeLeft()?.let {
                                Text(it, style = BlushType.label, color = Blush.colors.textMuted, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                    // Drawn below the buttons without pushing the banner up.
                    message?.let {
                        Text(
                            it,
                            style = BlushType.caption,
                            color = Blush.colors.textMuted,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = ContentStart, bottom = (heroHeight - BannerButtonsBottom - 22.dp).coerceAtLeast(2.dp)),
                        )
                    }
                }

                // ---------------- Lower half: tabs and their content, a full screen tall ----------------
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(screen)
                        .onFocusChanged { if (it.hasFocus) lower = true },
                ) {
                    Spacer(Modifier.height(24.dp))
                    // Tab row, with a hairline running the full width beneath it.
                    Box(Modifier.padding(start = PageStart, end = ContentEnd).fillMaxWidth()) {
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
                        Row(
                            Modifier,
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            tabs.forEach { t ->
                                TabLabel(
                                    t.label,
                                    selected = t == tab,
                                    // While the page is putting the remote back where it was (Back from an actor's page), a tab that
                                    // briefly gets focus on the way doesn't switch the page to itself.
                                    onFocused = { if (mem.pending == null) tab = t },
                                    // Up from the tabs always lands on Play (or the first button when there is nothing to play).
                                    modifier = Modifier.focusRequester(tabFocus.getValue(t)).focusProperties { up = playFocus },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultScroll) {
                        // Leaving the content upwards always lands on the tab you're looking at, not whichever tab
                        // happens to sit above the tile you were on.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .focusProperties {
                                    onExit = {
                                        val current = tab
                                        if (requestedFocusDirection == FocusDirection.Up && current != null) {
                                            tabFocus.getValue(current).requestFocus()
                                        }
                                    }
                                }
                                .focusGroup(),
                        ) {
                            when (tab) {
                                Tab.Episodes -> EpisodesTab(app, seasons, seasonId, { seasonId = it }, episodes, nextEpisode, episodesLoaded)
                                Tab.Collection -> PosterGrid(app, children)
                                Tab.Similar -> if (similarLoaded) PosterGrid(app, similar) else SimilarPlaceholder()
                                Tab.Extras -> ExtrasGrid(app, extras)
                                Tab.Info -> InfoTab(app, current)
                                null -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A tab. Three clear looks: the one the remote is on is bright white with the accent underline; the one being shown (while you're down in its content) keeps a dimmer, thinner line;
 * the rest are muted. So coming back up, you can always see which tab you're in.
 */
@Composable
private fun TabLabel(text: String, selected: Boolean, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused() }
    Column(
        modifier
            .width(IntrinsicSize.Max)
            .clickable(interactionSource = interaction, indication = null, onClick = onFocused),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text,
            // One weight for every state, so switching tabs never changes their widths.
            style = BlushType.title.copy(fontSize = 11.5.sp, fontWeight = FontWeight.W600),
            color = when {
                focused -> Color.White
                selected -> Blush.colors.textSoft
                else -> Blush.colors.textDim
            },
        )
        // A fixed-height slot, so a tab being lit never changes the height of the tab row (which moved everything below it).
        Box(Modifier.height(2.dp).fillMaxWidth()) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .height(if (focused) 2.dp else 1.5.dp)
                    .fillMaxWidth()
                    .background(
                        when {
                            focused -> Blush.colors.accent.color
                            selected -> Color.White.copy(alpha = 0.45f)
                            else -> Color.Transparent
                        },
                    ),
            )
        }
    }
}

/**
 * A grid of tiles, laid out plainly: every row that could be reached is built and kept (so moving the remote only ever moves between
 * tiles that already exist), and rows come into view by one short slide of the whole block, worked out from the row height. Nothing is
 * measured while scrolling and focus never scrolls anything by itself. Rows far below the first screenful are only built as the
 * remote nears them. [extraHeight] is room under each tile for its text; [startRow] is the row to begin on.
 */
@Composable
internal fun <T> TopAwareGrid(
    list: List<T>,
    columns: Int,
    key: (T) -> Any,
    aspect: Float = 2f / 3f,
    rowGap: Dp = 16.dp,
    extraHeight: Dp = 0.dp,
    startRow: Int = 0,
    /** Where Up goes from the top row, when that shouldn't be left to whatever lies above. */
    topUp: FocusRequester? = null,
    cell: @Composable (T, Dp) -> Unit,
) {
    val mem = LocalDetailsMemory.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().offset(x = (-10).dp).clipToBounds()) {
        val gap = 14.dp
        val top = 6.dp
        val width = (maxWidth - PageStart - ContentEnd - 10.dp - gap * (columns - 1)) / columns
        val rowHeight = width / aspect + extraHeight
        val stride = rowHeight + rowGap
        val rows = (list.size + columns - 1) / columns
        // How many whole rows fit in the space (a partly showing one is slid fully into view when the remote reaches it).
        val fit = (((maxHeight - top - 10.dp) + rowGap) / stride).toInt().coerceAtLeast(1)
        val maxFirst = (rows - fit).coerceAtLeast(0)
        fun rowOf(id: Any?): Int? = id?.let { wanted -> list.indexOfFirst { key(it) == wanted }.takeIf { it >= 0 }?.div(columns) }
        // The row at the top of the view, and the last row built so far (only ever grows, so rows already built are never torn down).
        var first by remember { mutableIntStateOf((rowOf(mem?.pending) ?: startRow).coerceIn(0, maxFirst)) }
        var built by remember { mutableIntStateOf(first + fit + 1) }
        var moved by remember { mutableStateOf(false) }
        val strideFloat = with(density) { stride.toPx() }
        val shift = remember { androidx.compose.animation.core.Animatable(first * strideFloat) }
        // Read from an effect (not in the composition), so moving to another row does not rebuild every tile of the grid.
        LaunchedEffect(Unit) {
            androidx.compose.runtime.snapshotFlow { first }.collectLatest { row ->
                shift.animateTo(row * strideFloat, tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            }
        }
        // The list arrives after the grid is first built (episodes): go to the row asked for, unless the remote has already moved.
        LaunchedEffect(list.size) {
            if (moved) return@LaunchedEffect
            val row = (rowOf(mem?.pending) ?: startRow).coerceIn(0, maxFirst)
            first = row
            built = maxOf(built, row + fit + 1)
        }
        fun reveal(row: Int) {
            moved = true
            val target = when {
                row < first -> row
                row >= first + fit -> (row - fit + 1).coerceAtMost(maxFirst)
                else -> first
            }
            if (target != first) first = target
            if (first + fit + 1 > built) built = first + fit + 1
        }
        // One focus handle per tile, so Right at the end of a row can go to the start of the next, and from the very last tile to the first.
        val handles = remember { HashMap<Int, FocusRequester>() }
        fun goTo(index: Int, pressed: Key): Boolean {
            if (index !in list.indices) return false
            // These moves are made here (the key is consumed), so the arrival click is asked for here too.
            app.blush.tv.ui.components.NavSound.pressed(pressed)
            reveal(index / columns)
            return runCatching { handles[index]?.requestFocus() }.getOrNull() == true
        }
        // A held direction repeats faster than rows can slide; repeats are paced so the remote never runs ahead of the picture.
        val lastRepeat = remember { LongArray(1) }
        Column(
            Modifier
                // The block is taller than the space it slides through: laid out at its full height, or the rows past the bottom
                // edge are squeezed to nothing (the tiles broke on the way down once there were more rows than fit).
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .padding(start = PageStart + 10.dp, top = top)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 0 &&
                        (e.key == Key.DirectionDown || e.key == Key.DirectionUp || e.key == Key.DirectionLeft || e.key == Key.DirectionRight)
                    ) {
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastRepeat[0] < 90) true else { lastRepeat[0] = now; false }
                    } else false
                }
                .graphicsLayer { translationY = -shift.value },
            verticalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            for (r in 0 until rows) {
                androidx.compose.runtime.key(r) {
                    if (r <= built) {
                        Row(Modifier.height(rowHeight), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (c in 0 until columns) {
                                val entry = list.getOrNull(r * columns + c) ?: break
                                androidx.compose.runtime.key(key(entry)) {
                                    val index = r * columns + c
                                    val handle = remember(index) { FocusRequester().also { handles[index] = it } }
                                    Box(
                                        Modifier
                                            .focusRequester(handle)
                                            .let { if (r == 0 && topUp != null) it.focusProperties { up = topUp } else it }
                                            .onPreviewKeyEvent { e ->
                                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                                val last = list.lastIndex
                                                when (e.key) {
                                                    // The end of a row carries on to the start of the next; the very last tile goes back to the first.
                                                    Key.DirectionRight -> when {
                                                        index == last && last > 0 -> goTo(0, e.key)
                                                        c == columns - 1 -> goTo(index + 1, e.key)
                                                        else -> false
                                                    }
                                                    // And back: the start of a row returns to the end of the one above.
                                                    Key.DirectionLeft -> if (c == 0 && index > 0) goTo(index - 1, e.key) else false
                                                    else -> false
                                                }
                                            }
                                            .onFocusChanged { if (it.hasFocus) reveal(r) }
                                            .restorable(key(entry)),
                                    ) { cell(entry, width) }
                                }
                            }
                        }
                    } else Spacer(Modifier.height(rowHeight))
                }
            }
        }
    }
}

@Composable
private fun PosterGrid(app: AppState, items: List<BaseItemDto>) {
    TopAwareGrid(items, columns = 7, key = { it.id }) { item, w ->
        PictureTile(
            url = app.repo.posterUrl(item),
            title = item.displayTitle(),
            width = w,
            aspect = 2f / 3f,
            progress = item.progress(),
            onClick = { app.go(Route.Details(item.id)) },
        )
    }
}

/** A picture tile of the details grids: grey until its picture arrives, then the picture fades in. Moving the remote over it rebuilds nothing. */
@Composable
internal fun PictureTile(url: String?, title: String, width: Dp, aspect: Float, progress: Float?, onClick: () -> Unit) {
    LiteTile(width = width, aspect = aspect, onClick = onClick) {
        if (url == null && title.isNotEmpty()) {
            Text(title, style = BlushType.label, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp), maxLines = 3)
        }
        RemoteImage(url, Modifier.fillMaxSize().fadeInOver(Blush.colors.surface), crossfade = false)
        ProgressLine(progress)
    }
}

@Composable
private fun ExtrasGrid(app: AppState, extras: List<BaseItemDto>) {
    TopAwareGrid(extras, columns = 4, key = { it.id }, aspect = 16f / 9f, extraHeight = 42.dp) { extra, w ->
        Column(Modifier.width(w), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            PictureTile(
                url = app.repo.thumbUrl(extra, 480) ?: app.repo.posterUrl(extra),
                title = "",
                width = w,
                aspect = 16f / 9f,
                progress = null,
                onClick = { app.go(Route.Player(extra, fromStart = true)) },
            )
            Text(extra.name.orEmpty(), style = BlushType.label, maxLines = 1)
            durationText(extra.runTimeTicks)?.let { Text(it, style = BlushType.caption, color = Blush.colors.textMuted) }
        }
    }
}

/**
 * Grey tiles that stand in for "More like this" while it is worked out and its first pictures arrive. They are laid out exactly as
 * the real tiles are and are the same colour, so the change to the real ones (whose pictures then fade in) cannot be seen.
 */
@Composable
private fun SimilarPlaceholder() {
    BoxWithConstraints(Modifier.fillMaxSize().offset(x = (-10).dp)) {
        val gap = 14.dp
        val columns = 7
        val width = (maxWidth - PageStart - ContentEnd - 10.dp - gap * (columns - 1)) / columns
        Column(Modifier.padding(start = PageStart + 10.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(columns) {
                        Box(Modifier.width(width).aspectRatio(2f / 3f).background(Blush.colors.surface, RoundedCornerShape(8.dp)))
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodesTab(
    app: AppState,
    seasons: List<BaseItemDto>,
    seasonId: UUID?,
    onSeason: (UUID) -> Unit,
    episodes: List<BaseItemDto>,
    nextEpisode: BaseItemDto?,
    loaded: Boolean,
) {
    // Up from the episodes goes to the season in use, not to whichever pill happens to be above the tile.
    val selectedSeason = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (seasons.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(start = PageStart, end = ContentEnd, top = 2.dp, bottom = 2.dp)) {
                items(seasons, key = { it.id }) { s ->
                    val selected = s.id == seasonId
                    Pill(s.name ?: "Season", selected = selected, onClick = { onSeason(s.id) }, modifier = if (selected) Modifier.focusRequester(selectedSeason) else Modifier)
                }
            }
        }
        if (loaded && episodes.isEmpty()) {
            Text("No episodes here yet.", style = BlushType.body, color = Blush.colors.textMuted, modifier = Modifier.padding(start = PageStart, top = 8.dp))
        }
        androidx.compose.runtime.key(seasonId) {
            val start = episodes.indexOfFirst { it.id == nextEpisode?.id }.coerceAtLeast(0)
            TopAwareGrid(episodes, columns = 4, key = { it.id }, aspect = 16f / 9f, rowGap = 18.dp, extraHeight = 42.dp, startRow = start / 4, topUp = selectedSeason.takeIf { seasons.size > 1 }) { ep, w ->
                Column(Modifier.width(w), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    PictureTile(
                        url = app.repo.thumbUrl(ep),
                        title = "",
                        width = w,
                        aspect = 16f / 9f,
                        progress = ep.progress() ?: if (ep.userData?.played == true) 1f else null,
                        onClick = { app.go(Route.Player(ep)) },
                    )
                    Text("${ep.indexNumber ?: ""}. ${ep.name.orEmpty()}", style = BlushType.label, maxLines = 1)
                    Text(
                        listOfNotNull(durationText(ep.runTimeTicks), ep.timeLeft()).joinToString(" · "),
                        style = BlushType.caption,
                        color = Blush.colors.textMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val longDate = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK)

/**
 * The Details tab: the title and full description on the left; facts in one column and the people in another
 * on the right. People can be opened to see their own page.
 */
@Composable
private fun InfoTab(app: AppState, item: BaseItemDto) {
    val people = item.people.orEmpty()
    val directors = people.filter { it.type == PersonKind.DIRECTOR }
    val writers = people.filter { it.type == PersonKind.WRITER }.take(3)
    val cast = people.filter { it.type == PersonKind.ACTOR }.take(9)
    val streams = item.mediaStreams ?: item.mediaSources?.firstOrNull()?.mediaStreams.orEmpty()
    val audio = streams.filter { it.type == MediaStreamType.AUDIO }.mapNotNull { it.language }.distinct()
    val hasSubs = streams.any { it.type == MediaStreamType.SUBTITLE }
    val formats = item.qualityBadges() + listOfNotNull("CC".takeIf { hasSubs })

    Row(
        Modifier.fillMaxSize().padding(start = PageStart, end = ContentEnd),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(item.name.orEmpty(), style = BlushType.subhead.copy())
            item.taglines?.firstOrNull()?.let { Text(it, style = BlushType.body, color = Blush.colors.textMuted) }
            Text(item.overview.orEmpty(), style = BlushType.reading.copy(fontSize = 12.sp, lineHeight = 17.sp), color = Blush.colors.textSoft)
        }
        Column(Modifier.weight(0.75f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            durationText(item.runTimeTicks)?.let { Fact("Duration") { FactText(it) } }
            (item.premiereDate?.let { longDate.format(it) } ?: item.productionYear?.toString())?.let { Fact("Release date") { FactText(it) } }
            item.genres?.takeIf { it.isNotEmpty() }?.let { Fact("Genre") { FactText(it.joinToString(", ")) } }
            item.officialRating?.let { rating ->
                Fact("Rating") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Badge(rating)
                        item.communityRating?.let { FactText("★ %.1f".format(it)) }
                    }
                }
            }
            if (formats.isNotEmpty()) {
                Fact("Available in") {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { formats.forEach { Badge(it) } }
                }
            }
            if (audio.isNotEmpty()) Fact("Audio") { FactText(audio.take(4).joinToString(", ") { languageLabel(it) }) }
        }
        Column(Modifier.weight(0.75f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (directors.isNotEmpty()) Fact(if (directors.size == 1) "Director" else "Directors") { PeopleLinks(app, directors, "director") }
            if (cast.isNotEmpty()) Fact("Cast") { PeopleLinks(app, cast, "cast") }
            if (writers.isNotEmpty()) Fact("Writers") { PeopleLinks(app, writers, "writer") }
            item.studios?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() }?.let { Fact("Studio") { FactText(it.take(2).joinToString(", ")) } }
        }
    }
}

private fun languageLabel(code: String) =
    runCatching { Locale.forLanguageTag(code).displayLanguage.takeIf { it.isNotBlank() && it != code } }.getOrNull() ?: code.uppercase()

@Composable
private fun Fact(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = BlushType.caption, color = Blush.colors.textMuted)
        content()
    }
}

@Composable
private fun FactText(text: String) = Text(text, style = BlushType.label.copy(fontWeight = FontWeight.W600))

/** Names you can select to open that person's page. */
@Composable
private fun PeopleLinks(app: AppState, people: List<BaseItemPerson>, section: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        people.forEach { p ->
            FocusSurface(
                onClick = { app.go(Route.Person(p.id)) },
                shape = RoundedCornerShape(5.dp),
                focusedScale = 1f,
                ringWidth = 0.dp,
                background = Color.Transparent,
                focusedBackground = Color.White.copy(alpha = 0.14f),
                modifier = Modifier.offset(x = (-6).dp).restorable("$section:${p.id}"),
            ) { focused ->
                Text(
                    p.name.orEmpty(),
                    style = BlushType.label.copy(fontWeight = FontWeight.W600),
                    color = if (focused) Blush.colors.text else Blush.colors.textSoft,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Repo is used by the person screen too; kept here so both share the same helpers. */
internal fun Repo.personPhotoUrl(person: BaseItemDto, maxWidth: Int = 500) = posterUrl(person, maxWidth)

val LocalDetailsMemory = androidx.compose.runtime.staticCompositionLocalOf<DetailsMemory?> { null }

/** Remembers that the remote was on this tile, and puts it back there when the page is rebuilt after Back. */
@Composable
internal fun Modifier.restorable(id: Any): Modifier {
    val mem = LocalDetailsMemory.current ?: return this
    // Only the one tile being returned to needs the machinery to take the remote back; every other tile just notes when it has it.
    val wanted = remember { mem.pending == id }
    if (!wanted) return this.onFocusChanged { if (it.hasFocus) mem.focused = id }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Tried a few times: the page may still be settling (tabs, rows arriving) the first time.
        var tries = 0
        while (mem.pending == id && tries < 12) {
            delay(80)
            if (runCatching { requester.requestFocus() }.getOrDefault(false)) mem.pending = null
            tries++
        }
        if (mem.pending == id) mem.pending = null
    }
    return this.focusRequester(requester).onFocusChanged { if (it.hasFocus) mem.focused = id }
}

// ----- The description on the banner: select it and it opens upward so it can be read in full -----

/** The most the description can grow to (movies / episodes) before the top of the title would leave the screen. */
private val ExpandedUp: Dp = BannerDescriptionHeight + (BannerButtonsBottom - 30.dp - BannerButtonsGap - BannerDescriptionHeight - BannerInfoGap - 18.dp - BannerInfoGap - BannerLogoHeight - 24.dp)
private val ExpandedUpEpisode: Dp = BannerDescriptionHeight + 110.dp

/**
 * The banner's description: two lines, as on Home. If there is more of it, it can be selected; pressing OK opens it upward
 * (the buttons stay put) and up to [maxHeight] of it shows, with Up / Down scrolling the rest. It stays open until it is pressed again (or Back, while it has the remote).
 */
@Composable
private fun ExpandableOverview(text: String, collapsedWidth: Dp, maxHeight: Dp, downTo: FocusRequester) {
    if (text.isEmpty()) { Spacer(Modifier.height(BannerDescriptionHeight)); return }
    val density = LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = BannerDescriptionStyle
    val closedPx = with(density) { collapsedWidth.roundToPx() }
    // Worked out once per text: does it need more than two lines, and how tall is all of it when opened.
    val overflows = remember(text, closedPx) {
        measurer.measure(text, style, maxLines = 2, constraints = androidx.compose.ui.unit.Constraints(maxWidth = closedPx)).hasVisualOverflow
    }
    val fullHeight = remember(text, closedPx) {
        with(density) { measurer.measure(text, style, constraints = androidx.compose.ui.unit.Constraints(maxWidth = closedPx)).size.height.toDp() }
    }
    var open by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val scrollState = androidx.compose.foundation.rememberScrollState()
    val scope = rememberCoroutineScope()
    val height by androidx.compose.animation.core.animateDpAsState(
        if (open) fullHeight.coerceIn(BannerDescriptionHeight, maxHeight) else BannerDescriptionHeight,
        tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "overview",
    )
    val line = with(density) { 40.dp.toPx() }
    val behaviour = if (!overflows) Modifier else Modifier
        .onFocusChanged { focused = it.isFocused }
        .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.Back, Key.Escape -> if (open) { open = false; true } else false
                // Down reads further while there is more to read, then goes to the buttons (Play / Resume).
                Key.DirectionDown -> if (open && scrollState.canScrollForward) { scope.launch { scrollState.animateScrollBy(line) }; true } else { downTo.requestFocus(); true }
                Key.DirectionUp -> if (open && scrollState.canScrollBackward) { scope.launch { scrollState.animateScrollBy(-line) }; true } else false
                else -> false
            }
        }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = !open }
    Box(
        Modifier
            .wrapContentWidth(Alignment.Start, unbounded = true)
            .width(collapsedWidth)
            .height(height)
            .then(behaviour)
            // The highlight is drawn around the text rather than laid out, so selecting it moves nothing.
            .drawBehind {
                if (focused) {
                    val padX = 8.dp.toPx()
                    val padY = 4.dp.toPx()
                    drawRoundRect(
                        Color.White.copy(alpha = 0.12f),
                        topLeft = androidx.compose.ui.geometry.Offset(-padX, -padY),
                        size = androidx.compose.ui.geometry.Size(size.width + padX * 2, size.height + padY * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()),
                    )
                }
            }
            .clip(androidx.compose.ui.graphics.RectangleShape),
    ) {
        Text(
            text,
            style = style,
            color = Blush.colors.textSoft,
            maxLines = if (open) Int.MAX_VALUE else 2,
            modifier = if (open) Modifier.verticalScroll(scrollState) else Modifier,
        )
    }
}
