package app.blush.tv.ui.home

import androidx.compose.animation.Crossfade
import app.blush.tv.data.logged
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.blush.tv.ui.AppState
import app.blush.tv.ui.Route
import app.blush.tv.ui.ContentStart
import app.blush.tv.ui.components.BannerButtonsBottom
import app.blush.tv.ui.components.BannerButtonsGap
import app.blush.tv.ui.components.BannerTitleInfo
import app.blush.tv.ui.components.Icons
import app.blush.tv.ui.Lite
import app.blush.tv.ui.components.ControlHeight
import app.blush.tv.ui.components.FocusSurface
import app.blush.tv.ui.components.Icon
import app.blush.tv.ui.components.RoundIconButton
import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.components.restorableFocus
import app.blush.tv.ui.progress
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.Text
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

// The Home banner ("featured films"). Layout is exactly docs/HERO_SPEC.md; the code is the parked original
// (docs/parked/Hero.kt.txt) with the clicks emptied (Details and the player are out of the app for now).

/** Height of the banner. The list below it starts with a spacer of HeroHeight - 18. */
internal val HeroHeight = 456.dp

/** How long a title stays in the banner before the next one (the active dot fills up over this long). */
internal const val HeroRotateMs = 9_000L

/** The banner artwork request, sized to the screen; the same one is used to pre-load it. */
internal fun heroRequest(context: android.content.Context, url: String, sizePx: Pair<Int, Int>) =
    coil3.request.ImageRequest.Builder(context).data(url).size(sizePx.first, sizePx.second).build()

/** One picture layer of the banner artwork. */
private class BackdropLayer {
    var url by mutableStateOf<String?>(null)
    val alpha = Animatable(0f)
    var z by mutableFloatStateOf(0f)
}

/**
 * The banner artwork. Two layers take turns: the next picture loads on the layer on top while it's still invisible,
 * and only once it's ready does it fade in over the current one. So titles always melt into one another (no dip
 * to black while a picture loads), and the very first one fades in from the background.
 */
@Composable
internal fun HeroBackdrop(url: String?, sizePx: Pair<Int, Int>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layers = remember { listOf(BackdropLayer(), BackdropLayer()) }
    var top by remember { mutableIntStateOf(-1) }
    LaunchedEffect(url) {
        if (url == null || (top >= 0 && layers[top].url == url)) return@LaunchedEffect
        val next = if (top == 0) 1 else 0
        val layer = layers[next]
        val sameAsBefore = layer.url == url
        layer.alpha.snapTo(0f)
        layer.z = (if (top >= 0) layers[top].z else 0f) + 1f
        layer.url = url
        top = next
        // Already loaded on this layer (flicking back and forth), so there's no load to wait for.
        if (sameAsBefore) layer.alpha.animateTo(1f, tween(550))
    }
    Box(Modifier.fillMaxSize()) {
        layers.forEach { layer ->
            val u = layer.url ?: return@forEach
            // Exactly the request the banner pre-loads with, so it comes straight from memory.
            val request = remember(u) { heroRequest(context, u, sizePx) }
            coil3.compose.AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { scope.launch { layer.alpha.animateTo(1f, tween(550)) } },
                modifier = Modifier.fillMaxSize().zIndex(layer.z).graphicsLayer {
                    alpha = layer.alpha.value
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
            )
        }
    }
}

/** The banner's logo, details, description and buttons (and the dots under them). */
@Composable
internal fun HeroBlock(app: AppState, model: HomeModel, hero: BaseItemDto?, idle: () -> Float) {
    val repo = app.repo
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = ContentStart, bottom = HeroHeight - BannerButtonsBottom),
            verticalArrangement = Arrangement.spacedBy(BannerButtonsGap),
        ) {
            if (hero == null) return@Column
            // One title simply fades into the next.
            Crossfade(targetState = hero, animationSpec = tween(450), label = "heroText") { item -> BannerTitleInfo(repo, item, idle) }
            // Play, add to / remove from the watchlist (a heart, filled when the title is already there), and More info.
            val listed = model.listed[hero.id] ?: (hero.userData?.isFavorite == true)
            val quiet = Color.White.copy(alpha = 0.10f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HeroPlayButton(
                    if (hero.progress() != null) "Resume" else "Play",
                    progress = hero.progress(),
                    onClick = {
                        if (hero.type == BaseItemKind.SERIES) {
                        // One look-up at a time: a second press while it is on its way must not open everything twice.
                        if (!model.heroBusy) scope.launch {
                            model.heroBusy = true
                            // A series plays its next episode (or, with none to play, opens its page).
                            val episode = try { logged("Hero.HeroBlock") { repo.nextEpisodeFor(hero.id) }.getOrNull() } finally { model.heroBusy = false }
                                if (episode != null) {
                                    app.go(Route.Details(hero.id, preview = hero))
                                    app.go(Route.Player(episode))
                                } else app.go(Route.Details(hero.id, preview = hero))
                            }
                        } else {
                            // The title's page goes underneath the player, so Back from the player lands there.
                            app.go(Route.Details(hero.id, preview = hero))
                            app.go(Route.Player(hero))
                        }
                    },
                    // Remembered so leaving for the bar and coming back returns here, not to a row further down.
                    // Fixed width, so Play and Resume are the same size.
                    modifier = Modifier
                        .width(104.dp)
                        .onFocusChanged { if (it.hasFocus) model.focus.remember("hero-play") }
                        .restorableFocus(model.focus, "hero-play", isDefault = model.heroIsDefault),
                )
                RoundIconButton(
                    Icons.Info, "More info",
                    onClick = { model.focus.remember("hero-info"); app.go(Route.Details(hero.id, preview = hero)) },
                    background = quiet,
                    modifier = Modifier
                        .onFocusChanged { if (it.hasFocus) model.focus.remember("hero-info") }
                        .restorableFocus(model.focus, "hero-info"),
                )
                RoundIconButton(
                    if (listed) Icons.Check else Icons.Plus,
                    if (listed) "Remove from watchlist" else "Add to watchlist",
                    onClick = {
                        val want = !listed
                        // A press counts as attention: the banner doesn't move on to the next title under it.
                        model.heroProgress = 0f
                        model.listed[hero.id] = want
                        model.listedItems[hero.id] = hero
                        scope.launch {
                            val ok = logged("Hero.HeroBlock") { repo.setFavorite(hero.id, want) }.getOrDefault(false)
                            if (!ok) model.listed[hero.id] = !want else model.refreshProgress()
                        }
                    },
                    tint = if (listed) Blush.colors.accent.color else Blush.colors.text,
                    background = if (listed) Blush.colors.accent.color.copy(alpha = 0.22f) else quiet,
                    modifier = Modifier
                        .onFocusChanged { if (it.hasFocus) model.focus.remember("hero-list") }
                        // Right goes to the dots under the banner.
                        .onPreviewKeyEvent { e ->
                            e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight && model.focus.requestKey("hero-dots")
                        }
                        .restorableFocus(model.focus, "hero-list"),
                )
            }
        }
        if (model.featured.size > 1) {
            Dots(model, Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
        }
    }
}

/** The banner's Play button: a white pill, like the reference. Under the remote it is pure white and a little larger. */
@Composable
internal fun HeroPlayButton(label: String, progress: Float?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = Color(0xFF14151B)
    val bar = Blush.colors.accent.color
    FocusSurface(
        onClick = onClick,
        modifier = modifier.height(ControlHeight),
        shape = RoundedCornerShape(50),
        focusedScale = 1.07f,
        ringWidth = 0.dp,
        background = Color.White.copy(alpha = 0.16f),
        focusedBackground = Color.White,
    ) { focused ->
        val content = if (focused) ink else Blush.colors.text
        // Drawn inside the button, so it grows with it.
        if (progress != null && progress > 0f) Box(
            Modifier.matchParentSize().drawBehind {
                val inset = 18.dp.toPx()
                val y = size.height - 4.dp.toPx()
                drawLine(Color.Gray.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(inset, y), androidx.compose.ui.geometry.Offset(size.width - inset, y), 1.5.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(bar, androidx.compose.ui.geometry.Offset(inset, y), androidx.compose.ui.geometry.Offset(inset + (size.width - 2 * inset) * progress.coerceIn(0f, 1f), y), 1.5.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
            },
        )
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Play, null, content, size = 12.dp)
            Text(label, style = BlushType.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.W600), color = content, maxLines = 1)
        }
    }
}

/**
 * The banner's position dots, centred under it. Only a handful show at once; the rest fade off at the sides
 * and slide into view as you move along. The active one is a small pill that fills up over the time the title stays in
 * the banner. Focus them to flick through with Left and Right; Up goes back to the buttons.
 */
@Composable
private fun Dots(model: HomeModel, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { model.pagerFocused = focused }
    val count = model.featured.size
    val window = minOf(7, count)
    val dot = 4.dp
    val activeWidth = 20.dp
    val gap = 7.dp
    val slot = dot + gap
    val first = (model.heroIndex - window / 2).coerceIn(0, (count - window).coerceAtLeast(0))
    val shift by animateDpAsState(-slot * first, tween(300), label = "dotsShift")
    val density = LocalDensity.current
    val edge = with(density) { 12.dp.toPx() }
    Box(
        modifier
            .onFocusChanged { if (it.hasFocus) model.focus.remember("hero-dots") }
            .restorableFocus(model.focus, "hero-dots")
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> { model.showHero(model.heroIndex + 1); true }
                    Key.DirectionLeft -> { model.showHero(model.heroIndex - 1); true }
                    Key.DirectionUp -> model.focus.requestKey("hero-info")
                    else -> false
                }
            }
            .focusable(interactionSource = interaction)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .width(slot * (window - 1) + activeWidth + gap)
                .height(6.dp)
                .clipToBounds()
                // Fade the dots out towards each end when there are more beyond them.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val leftFade = if (first > 0) 0f else 1f
                    val rightFade = if (first + window < count) 0f else 1f
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = leftFade),
                            edge / size.width to Color.Black,
                            1f - edge / size.width to Color.Black,
                            1f to Color.Black.copy(alpha = rightFade),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Row(Modifier.wrapContentWidth(unbounded = true, align = Alignment.Start).offset(x = shift), verticalAlignment = Alignment.CenterVertically) {
                model.featured.indices.forEach { i ->
                    val active = i == model.heroIndex
                    val width by animateDpAsState(if (active) activeWidth else dot, tween(300), label = "dot")
                    // The white fill fades out as a dot gives up its place (rather than vanishing), so nothing flashes.
                    val fillAlpha by animateFloatAsState(if (active) 1f else 0f, tween(300), label = "dotFill")
                    Box(Modifier.width(width + gap).height(6.dp), contentAlignment = Alignment.CenterStart) {
                        Box(
                            Modifier
                                .width(width)
                                .height(dot)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (active) 0.32f else 0.38f))
                                .then(
                                    Modifier.drawWithContent {
                                        drawContent()
                                        if (fillAlpha > 0.01f) {
                                            val part = if (active) (if (Lite.on) 1f else model.heroProgress) else 1f
                                            drawRect(Color.White.copy(alpha = fillAlpha), size = androidx.compose.ui.geometry.Size(size.width * part, size.height))
                                        }
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}
