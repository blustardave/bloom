package app.blush.tv.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import app.blush.tv.ui.components.FocusSurface
import app.blush.tv.ui.components.SidePanel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import app.blush.tv.ui.GridMemory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blush.tv.ui.AppState
import app.blush.tv.ui.ContentEnd
import app.blush.tv.ui.Route
import app.blush.tv.ui.components.CenteredMessage
import app.blush.tv.ui.components.LoadingDots
import app.blush.tv.ui.components.BlurredImage
import app.blush.tv.ui.components.fadeInOnAppear
import app.blush.tv.ui.components.PosterCard
import app.blush.tv.ui.components.RemoteImage
import app.blush.tv.ui.components.SecondaryButton
import app.blush.tv.ui.displayTitle
import app.blush.tv.ui.progress
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.theme.Text
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val birthFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK)
private const val Columns = 8

/**
 * A page for one actor, director or writer: a big photo, when and where they were born, their biography,
 * and everything of theirs in the library.
 */
@Composable
fun PersonScreen(app: AppState, personId: UUID) {
    val repo = app.repo
    var person by remember { mutableStateOf<BaseItemDto?>(null) }
    var titles by remember { mutableStateOf<List<BaseItemDto>?>(null) }
    var error by remember { mutableStateOf(false) }
    var bioOpen by remember { mutableStateOf(false) }
    val firstTitle = remember { FocusRequester() }
    val bioButton = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // The poster the remote was on, so Back from a title returns to it (forgotten when this page is left with Back).
    val memory = remember { app.gridMemory.getOrPut("person:$personId") { GridMemory() } }
    val handles = remember { mutableMapOf<Int, FocusRequester>() }

    LaunchedEffect(personId) {
        // The person and their titles are asked for together, not one after the other.
        val titlesAsk = async { runCatching { repo.personTitles(personId) }.getOrDefault(emptyList()) }
        runCatching { repo.item(personId) }.onSuccess { person = it }.onFailure { error = true }
        titles = titlesAsk.await()
        val want = memory.index
        if (want > 0 && want < titles.orEmpty().size) {
            // The photo-and-name block and the heading come before the posters in the grid.
            runCatching { gridState.scrollToItem(want + 2) }
            repeat(20) {
                delay(50)
                if (runCatching { handles[want]?.requestFocus() }.getOrNull() == true) return@LaunchedEffect
            }
        }
        repeat(20) {
            delay(50)
            if (runCatching { firstTitle.requestFocus() }.isSuccess) return@LaunchedEffect
            if (runCatching { bioButton.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    val p = person
    if (p == null) {
        if (error) CenteredMessage("Couldn't load this person.") else LoadingDots()
        return
    }
    val list = titles.orEmpty()
    BackHandler(enabled = bioOpen) { bioOpen = false; scope.launch { delay(30); runCatching { bioButton.requestFocus() } } }

    Box(Modifier.fillMaxSize().background(Blush.colors.background).fadeInOnAppear()) {
        // A soft wash of their best-known title's artwork behind everything.
        list.firstOrNull()?.let { top ->
            BlurredImage(repo.backdropUrl(top, 320), Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f })
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Blush.colors.background.copy(alpha = 0.5f), 0.6f to Blush.colors.background)))
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gap = 14.dp
            val posterWidth = (maxWidth - PageStart - ContentEnd - 10.dp - gap * (Columns - 1)) / Columns
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(Columns),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = PageStart + 10.dp, end = ContentEnd, top = 40.dp, bottom = 60.dp),
                modifier = Modifier.offset(x = (-10).dp),
            ) {
                // Header: photo beside the name, a short line and the start of the biography.
                item(key = "about", span = { GridItemSpan(maxLineSpan) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Bottom) {
                        Box(
                            Modifier
                                .width(186.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Blush.colors.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                p.name.orEmpty().split(' ').mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""),
                                style = BlushType.display,
                                color = Blush.colors.textDim,
                            )
                            RemoteImage(repo.personPhotoUrl(p), Modifier.fillMaxSize())
                        }
                        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(p.name.orEmpty(), style = BlushType.display)
                            if (list.isNotEmpty()) {
                                Text(
                                    if (list.size == 1) "1 title in your library" else "${list.size} titles in your library",
                                    style = BlushType.label,
                                    color = Blush.colors.accent.color,
                                )
                            }
                            val bio = p.overview.orEmpty().trim()
                            if (bio.isNotEmpty()) {
                                Text(bio, style = BlushType.body, color = Blush.colors.textSoft, maxLines = 3)
                                if (bio.length > 220) {
                                    TextLink(
                                        "Read more",
                                        onClick = { bioOpen = true },
                                        modifier = Modifier
                                            .focusRequester(bioButton)
                                            .onFocusChanged { if (it.hasFocus && (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0)) scope.launch { gridState.animateScrollToItem(0) } },
                                    )
                                }
                            }
                        }
                    }
                }
                if (titles != null) {
                    item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (list.isEmpty()) "Nothing of theirs is in your library." else "In your library",
                            style = BlushType.rowHeader,
                            color = Blush.colors.textMuted,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                }
                itemsIndexed(list, key = { _, it -> it.id }) { i, item ->
                    val handle = remember { FocusRequester() }
                    DisposableEffect(i) {
                        handles[i] = handle
                        onDispose { if (handles[i] === handle) handles.remove(i) }
                    }
                    Box(
                        Modifier
                            .focusRequester(handle)
                            .onFocusChanged { if (it.hasFocus) memory.index = i }
                            .let { if (i == 0) it.focusRequester(firstTitle) else it }
                            // Back on the first row of posters: scroll right up so the name and biography show again.
                            // (Only when the grid is not already at the top: moving along the row must cost nothing.)
                            .let {
                                if (i < Columns) it.onFocusChanged { f ->
                                    if (f.hasFocus && (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0)) scope.launch { gridState.animateScrollToItem(0) }
                                } else it
                            },
                    ) {
                        PictureTile(
                            url = repo.posterUrl(item),
                            title = item.displayTitle(),
                            width = posterWidth,
                            aspect = 2f / 3f,
                            progress = item.progress(),
                            onClick = { app.go(Route.Details(item.id)) },
                        )
                    }
                }
            }
        }

        // The full biography, in a panel from the right that scrolls with Up and Down; Left or Back closes it.
        AnimatedVisibility(
            bioOpen,
            enter = slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(220)),
            exit = slideOutHorizontally(tween(180)) { it / 3 } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            BioPanel(p.name.orEmpty(), p.overview.orEmpty().trim(), onClose = {
                bioOpen = false
                scope.launch { delay(30); runCatching { bioButton.requestFocus() } }
            })
        }
    }
}

/** A small underlined-on-focus text button, quieter than a full button. */
@Composable
private fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(5.dp),
        focusedScale = 1f,
        ringWidth = 0.dp,
        background = Color.Transparent,
        focusedBackground = Color.White.copy(alpha = 0.14f),
        modifier = modifier.offset(x = (-6).dp),
    ) { focused ->
        Text(
            "$text  ›",
            style = BlushType.label,
            color = if (focused) Blush.colors.text else Blush.colors.textMuted,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun BioPanel(name: String, bio: String, onClose: () -> Unit) {
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { delay(50); runCatching { focus.requestFocus() } }
    SidePanel(name, onClose = onClose, modifier = Modifier.width(420.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(140f) }; true }
                        Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-140f) }; true }
                        Key.DirectionRight, Key.DirectionCenter, Key.Enter -> true
                        else -> false
                    }
                }
                .focusable(),
        ) {
            Text(
                bio,
                style = BlushType.reading.copy(lineHeight = 20.sp),
                color = Blush.colors.textSoft,
                modifier = Modifier.verticalScroll(scroll).padding(start = 10.dp, end = 6.dp, bottom = 40.dp),
            )
        }
    }
}
