package app.blush.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clipToBounds
import coil3.toBitmap
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.theme.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * The little click Android TV makes as the remote moves from one thing to the next. A direction button being pressed is noted
 * (by the side-bar wrapper, which sees every key); when something then takes the remote within a moment, the system's own
 * navigation sound for that direction is played (it follows the TV's "navigation sounds" setting, and a held button
 * clicks at a gentle pace rather than for every step).
 */
object NavSound {
    @Volatile private var direction = 0
    @Volatile private var pressedAt = 0L
    private var playedAt = 0L

    fun pressed(key: androidx.compose.ui.input.key.Key) {
        direction = when (key) {
            androidx.compose.ui.input.key.Key.DirectionLeft -> android.view.SoundEffectConstants.NAVIGATION_LEFT
            androidx.compose.ui.input.key.Key.DirectionRight -> android.view.SoundEffectConstants.NAVIGATION_RIGHT
            androidx.compose.ui.input.key.Key.DirectionUp -> android.view.SoundEffectConstants.NAVIGATION_UP
            androidx.compose.ui.input.key.Key.DirectionDown -> android.view.SoundEffectConstants.NAVIGATION_DOWN
            else -> return
        }
        pressedAt = android.os.SystemClock.uptimeMillis()
    }

    fun arrived(view: android.view.View) {
        val now = android.os.SystemClock.uptimeMillis()
        if (direction == 0 || now - pressedAt > 400 || now - playedAt < 70) return
        playedAt = now
        runCatching { view.playSoundEffect(direction) }
    }
}

/**
 * The core TV interaction: anything focusable grows slightly and gets a white ring when the remote lands on it.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    focusedScale: Float = 1.06f,
    ringWidth: Dp = 2.dp,
    background: Color = Color.Transparent,
    focusedBackground: Color = background,
    onFocused: (() -> Unit)? = null,
    /** Where the focus zoom grows from (read while drawing, so it can follow an animation). */
    transformOrigin: (() -> androidx.compose.ui.graphics.TransformOrigin)? = null,
    /** Holding OK down: runs this instead of a normal click once the press has gone on long enough. */
    onLongPress: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var longFired by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    // Focus is noted straight from the focus callback (no flow to collect, no effect to start): a row builds a dozen of
    // these at once, so each one's set-up cost counts.
    var focused by remember { mutableStateOf(false) }
    remember { app.blush.tv.ui.Perf.tilesBuilt.incrementAndGet() }
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val onFocusedNow by androidx.compose.runtime.rememberUpdatedState(onFocused)
    val ring = Blush.colors.focus
    val cutShape = shape
    // Tell the side bar where the remote is, so Right out of the bar can come straight back here.
    val rail = app.blush.tv.ui.LocalRailReturn.current
    val soundView = androidx.compose.ui.platform.LocalView.current
    val railRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.DisposableEffect(rail) { onDispose { if (rail?.last === railRequester) rail.last = null } }
    Box(
        modifier = modifier
            .focusRequester(railRequester)
            .onFocusChanged {
                val now = it.isFocused
                if (now != focused) {
                    focused = now
                    scope.launch { scale.animateTo(if (now) focusedScale else 1f, tween(130)) }
                    if (now) {
                        if (rail != null) rail.last = railRequester
                        NavSound.arrived(soundView)
                        onFocusedNow?.invoke()
                    }
                }
            }
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                val sc = scale.value
                scaleX = sc
                scaleY = sc
                if (transformOrigin != null) this.transformOrigin = transformOrigin()
                // Rounded corners cut by the layer itself (cheaper than a separate clip on every tile).
                this.shape = cutShape
                clip = true
            }
            .background(if (focused) focusedBackground else background)
            .then(
                if (onLongPress == null) Modifier else Modifier.onPreviewKeyEvent { e ->
                    val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    if (!ok) return@onPreviewKeyEvent false
                    when (e.type) {
                        // Key repeats only arrive while OK is held, so the first repeat is the long press.
                        KeyEventType.KeyDown -> if (e.nativeKeyEvent.repeatCount >= 1) {
                            if (!longFired) { longFired = true; onLongPress() }
                            true
                        } else false
                        // The release after a long press must not also count as a click.
                        KeyEventType.KeyUp -> if (longFired) { longFired = false; true } else false
                        else -> false
                    }
                },
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .then(if (focused && ringWidth > 0.dp) Modifier.border(ringWidth, ring, shape) else Modifier),
    ) {
        content(focused)
    }
}

@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, crossfade: Boolean = true) {
    if (url == null) return
    val context = LocalContext.current
    val request = remember(url) { ImageRequest.Builder(context).data(url).crossfade(crossfade && !app.blush.tv.ui.Lite.on).build() }
    AsyncImage(model = request, contentDescription = null, contentScale = contentScale, modifier = modifier)
}

/** Thin progress line along the bottom of a card. */
@Composable
fun BoxScope.ProgressLine(fraction: Float?) {
    if (fraction == null || fraction <= 0f) return
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(3.dp)
            .background(Color.White.copy(alpha = 0.25f)),
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).background(Blush.colors.accent.color))
    }
}

/** Portrait 2:3 poster. Shows the title over a plain tile when the server has no artwork. */
@Composable
fun PosterCard(
    title: String,
    imageUrl: String?,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    onFocused: (() -> Unit)? = null,
    /** The picture fades in over the plain tile as it arrives, rather than appearing. */
    fadeIn: Boolean = false,
) {
    FocusSurface(
        onClick = onClick,
        onFocused = onFocused,
        modifier = modifier.width(width).aspectRatio(2f / 3f),
        background = Blush.colors.surface,
    ) {
        if (imageUrl == null) {
            Text(title, style = BlushType.label, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp), maxLines = 3)
        }
        RemoteImage(imageUrl, if (fadeIn) Modifier.fillMaxSize().fadeInOver(Blush.colors.surface) else Modifier.fillMaxSize(), crossfade = !fadeIn)
        ProgressLine(progress)
    }
}

/**
 * A picture tile for grids: grows a little and gets a white ring when the remote is on it, like [FocusSurface], but built so that
 * moving the remote rebuilds nothing at all: the growth and ring are driven by one animation that only the graphics side reads.
 */
@Composable
fun LiteTile(
    width: Dp,
    aspect: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val glow = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val view = androidx.compose.ui.platform.LocalView.current
    val ring = Blush.colors.focus
    val shape = remember { RoundedCornerShape(8.dp) }
    val had = remember { BooleanArray(1) }
    Box(
        modifier
            .width(width)
            .aspectRatio(aspect)
            .onFocusChanged {
                val now = it.isFocused
                if (now != had[0]) {
                    had[0] = now
                    if (now) NavSound.arrived(view)
                    scope.launch { glow.animateTo(if (now) 1f else 0f, tween(130)) }
                }
            }
            .graphicsLayer {
                val sc = 1f + 0.06f * glow.value
                scaleX = sc
                scaleY = sc
                this.shape = shape
                clip = true
            }
            .background(Blush.colors.surface)
            .drawWithContent {
                drawContent()
                val a = glow.value
                if (a > 0f) {
                    val w = 2.dp.toPx()
                    drawRoundRect(
                        ring.copy(alpha = a),
                        topLeft = Offset(w / 2, w / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(w),
                    )
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        content = content,
    )
}

/** 16:9 tile with title and subtitle drawn over the bottom of the image. */
@Composable
fun LandscapeCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    showText: Boolean = true,
    onFocused: (() -> Unit)? = null,
) {
    FocusSurface(
        onClick = onClick,
        onFocused = onFocused,
        modifier = modifier.width(width).aspectRatio(16f / 9f),
        background = Blush.colors.surface,
    ) {
        RemoteImage(imageUrl, Modifier.fillMaxSize())
        if (showText) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xD9090A0E)),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 11.dp)) {
                Text(title, style = BlushType.label, maxLines = 1)
                if (subtitle != null) Text(subtitle, style = BlushType.caption, color = Blush.colors.textSoft, maxLines = 1)
            }
        }
        ProgressLine(progress)
    }
}

/** One height and one corner for every button in the app, so they read as a set wherever they appear. */
internal val ControlHeight = 30.dp
internal val ControlCorner = 8.dp

/**
 * The app's button: a flat, quiet block with small, evenly spaced capitals that turns solid white when the remote is on it.
 * [progress] draws a thin line under the text (e.g. how far through a film you are).
 */
@Composable
private fun QuietButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier, progress: Float?, accented: Boolean = false) {
    val accent = Blush.colors.accent
    FocusSurface(
        onClick = onClick,
        // The progress line is drawn rather than laid out, so it can never change the button's size.
        modifier = modifier.height(ControlHeight),
        shape = RoundedCornerShape(50),
        focusedScale = 1.07f,
        ringWidth = 0.dp,
        background = if (accented) accent.color else Color.White.copy(alpha = 0.16f),
        focusedBackground = Color(0xFFF5F5F7),
    ) { focused ->
        val content = if (focused) Color(0xFF14151B) else if (accented) accent.onColor else Blush.colors.text
        // The progress line is drawn rather than laid out (it can never change the button's size), and inside, so it grows with the button.
        if (progress != null && progress > 0f) Box(
            Modifier.matchParentSize().drawBehind {
                val inset = 14.dp.toPx()
                val y = size.height - 5.dp.toPx()
                val width = size.width - inset * 2
                // On an accent-coloured button the line is white (the accent would vanish into it).
                drawLine(if (accented) Color.White.copy(alpha = 0.4f) else Color.Gray.copy(alpha = 0.45f), Offset(inset, y), Offset(inset + width, y), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(if (accented) Color.White else accent.color, Offset(inset, y), Offset(inset + width * progress.coerceIn(0f, 1f), y), 1.5.dp.toPx(), StrokeCap.Round)
            },
        )
        Row(
            // Same room either side of the contents; the letters are spaced out, which leaves a gap after the last one, so a touch
            // less is kept on the right to make the label sit truly in the middle.
            Modifier.align(Alignment.Center).padding(horizontal = if (icon != null) 16.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The icon is lined up on the middle of the capitals (not of the line box, whose middle depends on the font), so the two
            // always sit level: the text rests on its baseline, and the icon is placed so its centre is half a capital height above it.
            val capHeight = with(androidx.compose.ui.platform.LocalDensity.current) { (11.sp * 0.71f).toPx() }
            if (icon != null) Icon(icon, null, content, Modifier.alignBy { (it.measuredHeight + capHeight.toInt()) / 2 }.offset(y = IconRaise), size = 12.dp)
            // Without the font's extra top and bottom space the capitals sit exactly in the middle of the button.
            Text(
                text,
                style = BlushType.button.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W600,
                    letterSpacing = 0.sp,
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                        androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                        androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                    ),
                ),
                color = content,
                maxLines = 1,
                modifier = Modifier.alignBy(androidx.compose.ui.layout.LastBaseline),
            )
        }
    }
}

/** Small lift so the icon's visual centre (not its box) sits on the middle of the capitals. */
private val IconRaise = (-0.75).dp

/** The main action, e.g. Play. */
@Composable
fun PrimaryButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, progress: Float? = null, accented: Boolean = false) =
    QuietButton(text, icon, onClick, modifier, progress, accented = accented)

/** A secondary action next to the main one. ([pill] is kept for older callers; every button now has the same shape.) */
@Composable
@Suppress("UNUSED_PARAMETER")
fun SecondaryButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, pill: Boolean = false) =
    QuietButton(text, icon, onClick, modifier, null)

/** An icon-only button: round, and the same height as the text buttons, so a row of them sits evenly. The icon is centred exactly. */
@Composable
fun RoundIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Blush.colors.text, size: Dp = ControlHeight, background: Color = Color.White.copy(alpha = 0.10f)) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        focusedScale = 1.06f,
        ringWidth = 0.dp,
        background = background,
        focusedBackground = Color(0xFFF5F5F7),
    ) { focused ->
        // When selected the button turns white, so a lit-up heart or tick keeps its colour only if it is not on white.
        Icon(icon, contentDescription, if (focused && tint == Blush.colors.text) Color(0xFF14151B) else tint, Modifier.align(Alignment.Center), size = 14.dp)
    }
}

/** Pill used for season pickers and option chips. */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onFocused: (() -> Unit)? = null, small: Boolean = false) {
    FocusSurface(
        onClick = onClick,
        onFocused = onFocused,
        modifier = modifier.height(if (small) 21.dp else 24.dp),
        shape = RoundedCornerShape(50),
        focusedScale = 1.06f,
        background = if (selected) Blush.colors.text else Color.White.copy(alpha = 0.12f),
        focusedBackground = if (selected) Blush.colors.text else Color.White.copy(alpha = 0.24f),
    ) {
        Text(
            text,
            style = BlushType.label.copy(fontSize = if (small) 9.sp else 10.5.sp),
            color = if (selected) Color(0xFF17181F) else Blush.colors.text,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = if (small) 9.dp else 11.dp),
        )
    }
}

@Composable
fun Badge(text: String) {
    Text(
        text,
        style = BlushType.caption.copy(fontSize = BlushType.caption.fontSize * 0.85f),
        modifier = Modifier
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
fun CenteredMessage(title: String, body: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = BlushType.headline)
        if (body != null) Text(body, style = BlushType.body, color = Blush.colors.textMuted, modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * While a page loads: nothing at all (the page fades in once it's ready, see [fadeInOnAppear]). Only when the wait drags on
 * (a slow or sleeping server) does a quiet word appear, so the screen doesn't look frozen.
 */
@Composable
fun LoadingDots(modifier: Modifier = Modifier) {
    val slow = androidx.compose.runtime.produceState(false) { kotlinx.coroutines.delay(1_500); value = true }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (slow.value) Text("Loading…", style = BlushType.body, color = Blush.colors.textDim, modifier = Modifier.fadeInOnAppear())
    }
}

/** Fades this in the first time it's shown (e.g. a page's content once it has loaded). */
@Composable
/**
 * A tile's picture fading in, without a layer of its own: [cover] (the tile's own colour) is painted over the picture and thinned
 * out. (A see-through layer per tile meant an extra off-screen buffer for every tile of a new row.)
 */
fun Modifier.fadeInOver(cover: Color, durationMs: Int = 260): Modifier {
    if (app.blush.tv.ui.Lite.on) return this
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(durationMs)) }
    return this.drawWithContent {
        drawContent()
        val a = 1f - shown.value
        if (a > 0f) drawRect(cover, alpha = a)
    }
}

@Composable
fun Modifier.fadeInOnAppear(enabled: Boolean = true, durationMs: Int = 320): Modifier {
    val alpha = remember { androidx.compose.animation.core.Animatable(if (enabled && !app.blush.tv.ui.Lite.on) 0f else 1f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(durationMs)) }
    return this.graphicsLayer { this.alpha = alpha.value }
}

/**
 * A title's logo, sized by its shape so wide and tall logos look equally prominent:
 * every logo gets roughly the same area, within [maxWidth] × [maxHeight].
 */
@Composable
fun LogoImage(
    url: String,
    modifier: Modifier = Modifier,
    area: Float = 26000f,
    maxWidth: Dp = 380.dp,
    maxHeight: Dp = 130.dp,
    /** Called if the logo can't be loaded, so the caller can show the name instead. */
    onFailed: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    // Many logo files have a wide empty margin around the artwork, which made the logo look pushed away from its corner.
    // Once loaded, the transparent edges are measured and cropped off, so what shows is only the artwork itself.
    // (Already known when the banner prepared this logo ahead of time, so the logo appears at its final size straight away.)
    var crop by remember(url) { mutableStateOf<androidx.compose.ui.geometry.Rect?>(boundsCache[url]) }
    var full by remember(url) { mutableStateOf<Float?>(aspectCache[url]) }
    // Load at full resolution: the box starts tiny until the logo's shape is known, and Coil would otherwise
    // fetch a thumbnail-sized image and stretch it (the blurry logos).
    val request = remember(url) { ImageRequest.Builder(context).data(url).size(coil3.size.Size.ORIGINAL).crossfade(false).build() }
    val fullAspect = full
    val box = crop
    val ratio = if (fullAspect == null || box == null) null else (box.width * fullAspect) / box.height
    val size = if (ratio == null || ratio <= 0f) null else {
        var w = kotlin.math.sqrt(area * ratio)
        var h = w / ratio
        if (w > maxWidth.value) { w = maxWidth.value; h = w / ratio }
        if (h > maxHeight.value) { h = maxHeight.value; w = h * ratio }
        w.dp to h.dp
    }
    val image: @Composable (Modifier) -> Unit = { m ->
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            onSuccess = { state ->
                val s = state.painter.intrinsicSize
                if (s.width > 0 && s.height > 0) {
                    full = s.width / s.height
                    crop = boundsCache[url] ?: runCatching { artworkBounds(state.result.image) }.getOrNull()?.also { boundsCache[url] = it }
                        ?: androidx.compose.ui.geometry.Rect(0f, 0f, 1f, 1f)
                }
            },
            onError = { onFailed?.invoke() },
            modifier = m,
        )
    }
    if (size == null || box == null) {
        Box(modifier.width(maxWidth).height(1.dp)) { image(Modifier.size(1.dp)) }
    } else {
        // The whole picture is laid out at the size that makes the artwork fill this box, then shifted so only the artwork shows.
        Box(modifier.width(size.first).height(size.second).clipToBounds()) {
            image(
                Modifier
                    .requiredSize(size.first / box.width, size.second / box.height)
                    .offset(-(size.first / box.width) * box.left, -(size.second / box.height) * box.top),
            )
        }
    }
}

private val boundsCache = java.util.concurrent.ConcurrentHashMap<String, androidx.compose.ui.geometry.Rect>()
private val aspectCache = java.util.concurrent.ConcurrentHashMap<String, Float>()

/**
 * Loads a logo into the picture memory and works out its crop and shape off the main thread, so when it's shown it
 * appears at its final size at once instead of popping in small and then resizing.
 */
suspend fun prepareLogo(context: android.content.Context, url: String) {
    val request = ImageRequest.Builder(context).data(url).size(coil3.size.Size.ORIGINAL).crossfade(false).build()
    val result = coil3.SingletonImageLoader.get(context).execute(request)
    val image = (result as? coil3.request.SuccessResult)?.image ?: return
    if (image.width <= 0 || image.height <= 0) return
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val bounds = boundsCache[url] ?: runCatching { artworkBounds(image) }.getOrNull()
        if (bounds != null) { boundsCache[url] = bounds; aspectCache[url] = image.width.toFloat() / image.height }
    }
}

/** The part of an image that isn't (nearly) transparent, as fractions of its width and height. */
private fun artworkBounds(image: coil3.Image): androidx.compose.ui.geometry.Rect {
    val w = 128
    val h = (w * image.height.toFloat() / image.width.coerceAtLeast(1)).toInt().coerceIn(8, 256)
    val bmp = image.toBitmap(w, h)
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    var minX = w; var minY = h; var maxX = -1; var maxY = -1
    for (y in 0 until h) for (x in 0 until w) {
        if ((px[y * w + x] ushr 24) > 24) {
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
        }
    }
    if (maxX < 0) return androidx.compose.ui.geometry.Rect(0f, 0f, 1f, 1f)
    // A hair of breathing room, so antialiased edges aren't clipped.
    return androidx.compose.ui.geometry.Rect(
        (minX - 1).coerceAtLeast(0) / w.toFloat(), (minY - 1).coerceAtLeast(0) / h.toFloat(),
        (maxX + 2).coerceAtMost(w) / w.toFloat(), (maxY + 2).coerceAtMost(h) / h.toFloat(),
    )
}

/**
 * Wide 16:9 tile. Uses the server's thumbnail (which usually has the title on it); otherwise the backdrop
 * with the logo, or the name, laid over it.
 */
@Composable
fun TitleCard(
    title: String,
    imageUrl: String?,
    logoUrl: String?,
    imageHasTitle: Boolean,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    caption: String? = null,
    /** Right-aligned on the caption line (e.g. the date watched). */
    captionEnd: String? = null,
    onFocused: (() -> Unit)? = null,
    /** Drawn over the artwork, e.g. a preview clip. */
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FocusSurface(
            onClick = onClick,
            onFocused = onFocused,
            onLongPress = onLongPress,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            background = Blush.colors.surface,
        ) {
                RemoteImage(imageUrl, Modifier.fillMaxSize())
            if (!imageHasTitle) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0xB3090A0E))))
                if (logoUrl != null) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.BottomCenter) {
                        LogoImage(logoUrl, area = width.value * width.value * 0.09f, maxWidth = width * 0.75f, maxHeight = width * 0.22f)
                    }
                } else {
                    Text(title, style = BlushType.label, maxLines = 2, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                }
            }
            overlay?.invoke(this)
            ProgressLine(progress)
        }
        if (caption != null || captionEnd != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(caption.orEmpty(), style = BlushType.caption, color = Blush.colors.textMuted, maxLines = 1, modifier = Modifier.weight(1f))
            if (captionEnd != null) Text(captionEnd, style = BlushType.caption, color = Blush.colors.textDim, maxLines = 1, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

/** Last tile of a home row: opens the full category. */
@Composable
fun ViewAllCard(width: Dp, onClick: () -> Unit, modifier: Modifier = Modifier, aspect: Float = 16f / 9f) {
    // Not a tile: just a round arrow button with its label, as tall as the tiles beside it so the row stays level.
    FocusSurface(
        onClick = onClick,
        modifier = modifier.width(112.dp).height(width / aspect),
        shape = RoundedCornerShape(14.dp),
        ringWidth = 0.dp,
        // Just the round button lights up (it turns white); nothing behind it.
        focusedScale = 1f,
        background = Color.Transparent,
        focusedBackground = Color.Transparent,
    ) { focused ->
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(if (focused) Color.White else Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Forward, null, if (focused) Color(0xFF14151B) else Blush.colors.text, size = 22.dp)
            }
            Text("View all", style = BlushType.label, color = if (focused) Blush.colors.text else Blush.colors.textMuted)
        }
    }
}
