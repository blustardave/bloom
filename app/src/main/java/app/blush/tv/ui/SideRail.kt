package app.blush.tv.ui

import kotlinx.coroutines.delay
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import app.blush.tv.ui.components.NavSound
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blush.tv.ui.components.Icon
import app.blush.tv.ui.components.MenuAction
import app.blush.tv.ui.components.TileMenu
import app.blush.tv.ui.components.Icons
import app.blush.tv.ui.components.RemoteImage
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.theme.Text
import kotlinx.coroutines.launch
import java.util.UUID

val RailWidth = 64.dp
/** Where every screen's content starts on the left, just clear of the side bar, and how far it stops from the right. */
val ContentStart = 22.dp
val ContentEnd = 22.dp
/** Where every page's title sits from the top, so Libraries, Watchlist, Settings, a library, Search… all line up. */
val PageTop = 56.dp
private val ExpandedWidth = 220.dp
private val IconSlot = 32.dp
private val ButtonHeight = 30.dp
/** Icons sit centred in the slim bar. */
private val IconStart = (RailWidth - IconSlot) / 2

/**
 * A screen's own way back to where you were, used when you press Right out of the side bar.
 * Screens that don't provide one fall back to Compose's own focus memory.
 */
class RailReturn {
    var restore: (() -> Boolean)? = null
    /** The last focusable (a FocusSurface) the remote was on in the screen, so Right out of the bar can go straight back to it. */
    var last: FocusRequester? = null
}

val LocalRailReturn = androidx.compose.runtime.staticCompositionLocalOf<RailReturn?> { null }


private val BarHeight = 26.dp
/** The pill (its items plus a little frame), and the two circles, are all this tall, so they line up. */
private val PillHeight = BarHeight + 4.dp
private val CircleSize = PillHeight
private val Ink = Color(0xFF14151B)
/** The faint light along the top of the pill and the Search circle. */
private val TopHighlight = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 0.45f to Color.White.copy(alpha = 0.04f), 1f to Color.Transparent)

/**
 * The navigation bar's state, shared by the bar (drawn once, above every screen) and whichever screen is showing:
 * the screen asks to enter the bar ([armed], [entry]) and the bar asks the screen to take the remote back ([toContent]).
 */
class NavBar {
    /** The bar accepts focus only while this is set (so a screen with nothing to focus yet can't hand its first focus to the bar). */
    var armed by mutableStateOf(false)
    var focused by mutableStateOf(false)
    /** Lands on the item for the current screen. */
    val entry = FocusRequester()
    /** Counts the times the screen sent the remote up into the bar, so the bar can make sure it landed on the current screen's name. */
    var entries by mutableIntStateOf(0)
    /** Set by the screen on show: puts the remote back where it was on that screen. */
    var toContent: (() -> Unit)? = null
}

val LocalNavBar = androidx.compose.runtime.staticCompositionLocalOf<NavBar?> { null }

/** One item in the pill. */
private class PillItem(val key: String, val label: String, val icon: ImageVector, val filled: ImageVector, val onClick: () -> Unit)

/**
 * The bar, drawn once above whatever screen is showing (so it stays put when the screen changes). It is there on every
 * main screen, and slides away on a title's page, the player and so on.
 */
@Composable
fun NavBarHost(app: AppState, nav: NavBar, modifier: Modifier = Modifier) {
    val route = app.current
    val here: Pair<Route.Tab, UUID?>? = when (route) {
        Route.Home -> Route.Home to null
        is Route.Category -> Route.Home to null
        Route.Search -> Route.Search to null
        Route.Libraries -> Route.Libraries to null
        Route.Watchlist -> Route.Watchlist to null
        Route.Settings -> Route.Settings to null
        is Route.Library -> Route.Libraries to route.library.id
        else -> null
    }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(here != null) {
        if (here != null && shown.value < 1f) {
            // Coming back (e.g. Back from a title's page): the screen underneath is being rebuilt for its first few frames, which
            // made the bar's slide stutter. It waits for the screen change to finish, then comes in.
            kotlinx.coroutines.delay(if (shown.value == 0f) 260 else 0)
            withFrameNanos { }
            withFrameNanos { }
            shown.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        } else shown.animateTo(if (here != null) 1f else 0f, tween(260, easing = FastOutSlowInEasing))
    }
    // While it slides away it keeps showing the screen it was on.
    val last = remember { arrayOfNulls<Pair<Route.Tab, UUID?>>(1) }
    if (here != null) last[0] = here
    val tab = last[0] ?: return
    TopBar(app, nav, tab.first, tab.second, shown = { shown.value }, modifier = modifier)
}

/**
 * Search as a circle at the left, Profile as a circle at the right, and between them a pill of names: Home, Libraries and
 * Watchlist (and any pinned libraries). A white cursor slides along the pill. While the remote is in the bar the cursor is bright;
 * out of it the cursor is dim, on the current screen's name. On a circle, the pill has nothing lit. The profile circle opens
 * Settings and Switch profile. Its header sits under the search circle on every page (see [PageTop]).
 */
@Composable
private fun TopBar(
    app: AppState,
    nav: NavBar,
    selected: Route.Tab,
    selectedLibrary: UUID?,
    shown: () -> Float,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pins = app.railPins.take(MaxRailPins)
    val pinSelected = pins.firstOrNull { it.libraryId == selectedLibrary?.toString() }
    val user = app.user

    val items = buildList {
        add(PillItem("Home", "Home", Icons.Home, Icons.HomeFilled) { app.openTab(Route.Home) })
        pins.forEach { pin ->
            val (outline, filled) = Icons.pinIcons[pin.icon] ?: Icons.pinIcons.values.first()
            add(PillItem("pin-${pin.libraryId}", pin.name, outline, filled) {
                scope.launch {
                    val library = runCatching { app.repo.libraries() }.getOrDefault(emptyList()).firstOrNull { it.id.toString() == pin.libraryId }
                    if (library != null) app.openLibrary(library)
                }
            })
        }
        add(PillItem("Libraries", "Libraries", Icons.Libraries, Icons.LibrariesFilled) { app.openTab(Route.Libraries) })
        add(PillItem("Watchlist", "Watchlist", Icons.Watchlist, Icons.WatchlistFilled) { app.openTab(Route.Watchlist) })
    }
    // Which control stands for the current screen.
    val selectedKey: String = when {
        pinSelected != null -> "pin-${pinSelected.libraryId}"
        selected == Route.Search -> "search"
        selected == Route.Settings -> "profile"
        selected == Route.Libraries -> "Libraries"
        selected == Route.Watchlist -> "Watchlist"
        else -> "Home"
    }

    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }
    val order = listOf("search") + items.map { it.key } + "profile"
    var current by remember { mutableStateOf<String?>(null) }
    val canFocus = { nav.armed && shown() > 0.5f }
    fun entry(key: String) = if (key == selectedKey) Modifier.focusRequester(nav.entry) else Modifier

    // The pill's cursor: a fractional position along its names (it slides). With the remote in the bar it sits on the name the
    // remote is on (nothing, if the remote is on a circle); out of the bar it sits, dimmer, on the current screen's name.
    val pillKeys = items.map { it.key }
    val selectedIdx = pillKeys.indexOf(selectedKey)
    val idx = if (nav.focused) pillKeys.indexOf(current) else selectedIdx
    val cursorA = animateFloatAsState(if (nav.focused) 1f else if (idx < 0) 0f else 0.3f, tween(220), label = "cursorA")
    val grow = animateFloatAsState(if (nav.focused) 1f else 0.95f, tween(220), label = "barGrow")
    val pos = remember { Animatable(selectedIdx.coerceAtLeast(0).toFloat()) }
    // Where the cursor is heading. With the remote on a circle it carries on past the end of the pill (and is cut off by its
    // edge) rather than fading, so it is already off the pill when it gets there and slides back in from that side.
    val target: Float? = when {
        idx >= 0 -> idx.toFloat()
        nav.focused && current == "search" -> -1.3f
        nav.focused && current == "profile" -> pillKeys.size + 0.3f
        else -> null
    }
    LaunchedEffect(target) {
        // A spring: a held Left or Right changes the target again and again, and a spring carries its speed from one target to the next
        // (an eased tween starts from rest every time, which showed as a stutter). It runs a little firmer when leaving by an end.
        target?.let { t -> pos.animateTo(t, spring(dampingRatio = 1f, stiffness = if (idx < 0 || pos.value < 0f || pos.value > pillKeys.size - 1f) 900f else 650f)) }
    }

    // The moment the remote leaves the bar the cursor is on the current screen's name (it dims there), so it never fades on the name
    // that was scrolled to, and coming back never starts from it.
    LaunchedEffect(nav.focused, selectedIdx) {
        // (Going in, the remote always lands on the current screen's name, so the cursor starts there as well.)
        if (selectedIdx >= 0 && pos.value != selectedIdx.toFloat()) pos.snapTo(selectedIdx.toFloat())
    }
    // Every time the remote is sent up into the bar it starts on the current screen's control, never on the one scrolled to last
    // time (a focus group can hand focus back to the child it last had).
    LaunchedEffect(nav.entries) {
        if (nav.entries == 0) return@LaunchedEffect
        withFrameNanos { }
        if (nav.focused && current != selectedKey) {
            runCatching { requester(selectedKey).requestFocus() }
        }
        if (selectedIdx >= 0) pos.snapTo(selectedIdx.toFloat())
    }

    // How the pill looks (Settings → Appearance): names only, icons only, both, or icons with the active one's name sliding out.
    // Apart from "sliding" every item has one fixed width, so the pill never changes size.
    val look = app.pillStyle
    val showIcon = look != "text"
    val showText = look != "icons"
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = BlushType.title.copy(fontSize = 11.sp, fontWeight = FontWeight.W600)
    val padPx = with(density) { 12.dp.toPx() }
    val basePx = with(density) { BarHeight.toPx() }
    val regions = items.map { measurer.measure(it.label, labelStyle).size.width + with(density) { 14.dp.toPx() } }
    val labelsOn = animateFloatAsState(if (idx >= 0) 1f else 0f, tween(220), label = "labelsOn")
    fun openness(i: Int) = when (look) {
        "sliding" -> (1f - kotlin.math.abs(pos.value - i)).coerceIn(0f, 1f) * labelsOn.value
        "iconsText" -> 1f
        else -> 0f
    }
    fun widthOf(i: Int) = if (look == "text") regions[i] - with(density) { 14.dp.toPx() } + 2 * padPx else basePx + regions[i] * openness(i)
    val heightPx = with(density) { BarHeight.roundToPx() }
    val slide = with(density) { 10.dp.toPx() }
    val lite = Lite.on

    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = ContentStart, end = ContentStart)
            .height(PillHeight)
            .graphicsLayer {
                val s = shown()
                alpha = s
                translationY = -(1f - s) * 24.dp.toPx()
            }
            .onFocusChanged {
                nav.focused = it.hasFocus
                if (!it.hasFocus) {
                    current = null
                    if (selectedIdx >= 0) scope.launch { pos.snapTo(selectedIdx.toFloat()) }
                    // Only lock the bar again once focus has really left it, not during a move between its items.
                    scope.launch {
                        withFrameNanos { }
                        if (!nav.focused) nav.armed = false
                    }
                }
            }
            .onPreviewKeyEvent { e ->
                when (e.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (e.type == KeyEventType.KeyDown) {
                            val at = order.indexOf(current).takeIf { it >= 0 } ?: 0
                            val target = (at + if (e.key == Key.DirectionLeft) -1 else 1).coerceIn(0, order.lastIndex)
                            if (target != at) runCatching { requester(order[target]).requestFocus() }
                        }
                        true
                    }
                    Key.DirectionUp -> true
                    Key.DirectionDown -> {
                        if (e.type == KeyEventType.KeyDown) nav.toContent?.invoke()
                        true
                    }
                    else -> false
                }
            }
            // One focus group for the whole bar, so moving between its items never counts as leaving it.
            .focusGroup(),
    ) {
        // Search: a circle at the left that turns white under the remote.
        Box(Modifier.align(Alignment.CenterStart)) {
            CircleButton(
                selected = selectedKey == "search", shadow = !lite, canFocus = canFocus,
                onClick = { app.openTab(Route.Search) },
                modifier = Modifier.focusRequester(requester("search")).onFocusChanged { if (it.isFocused) current = "search" }.then(entry("search")),
            ) { ink, _ -> Icon(Icons.Search, "Search", ink, Modifier.align(Alignment.Center), size = 14.dp) }
        }
        // The pill.
        Box(Modifier.align(Alignment.Center)) {
            Row(
                Modifier
                    .graphicsLayer { scaleX = grow.value; scaleY = grow.value }
                    .shadow(if (lite) 0.dp else 10.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                    .background(Color(0xFF1E1F25), CircleShape)
                    .padding(2.dp)
                    .height(BarHeight)
                    .drawBehind {
                        val n = items.size
                        val a = cursorA.value
                        if (n == 0 || a <= 0.01f) return@drawBehind
                        // Out of the bar the cursor is simply on the current screen's name, whatever the slide was doing.
                        val p = (if (nav.focused || selectedIdx < 0) pos.value else selectedIdx.toFloat()).coerceIn(-1.3f, n + 0.3f)
                        val left: Float
                        val w: Float
                        if (p < 0f) {
                            // Off the left end.
                            w = widthOf(0)
                            left = w * p
                        } else if (p > n - 1) {
                            // Off the right end.
                            val last = n - 1
                            var x = 0f
                            for (k in 0 until last) x += widthOf(k)
                            w = widthOf(last)
                            left = x + w * (p - last)
                        } else {
                            val i = p.toInt()
                            val f = p - i
                            val j = minOf(i + 1, n - 1)
                            var x = 0f
                            for (k in 0 until i) x += widthOf(k)
                            left = x + widthOf(i) * f
                            w = widthOf(i) + (widthOf(j) - widthOf(i)) * f
                        }
                        // Cut to the pill's own rounded outline (it has 2dp of padding round this area), so a cursor leaving by either end
                        // is only ever seen over the pill.
                        val pad = 2.dp.toPx()
                        val outline = androidx.compose.ui.graphics.Path().apply {
                            addRoundRect(androidx.compose.ui.geometry.RoundRect(-pad, -pad, size.width + pad, size.height + pad, CornerRadius((size.height + 2 * pad) / 2)))
                        }
                        clipPath(outline) {
                            drawRoundRect(Color.White.copy(alpha = a), Offset(left, 0f), Size(w, size.height), CornerRadius(size.height / 2))
                        }
                    },
            ) {
                items.forEachIndexed { i, item ->
                    val interaction = remember { MutableInteractionSource() }
                    val active = i == idx
                    val tint by animateColorAsState(
                        when { active && nav.focused -> Ink; active -> Color.White; else -> Blush.colors.textSoft },
                        tween(220), label = "pillInk",
                    )
                    Box(
                        Modifier
                            .layout { measurable, _ ->
                                val w = widthOf(i).toInt()
                                val p = measurable.measure(Constraints.fixed(w, heightPx))
                                layout(w, heightPx) { p.place(0, 0) }
                            }
                            .clipToBounds()
                            .focusRequester(requester(item.key))
                            .onFocusChanged { if (it.isFocused) current = item.key }
                            .then(entry(item.key))
                            .focusProperties { this.canFocus = canFocus() }
                            .clickable(interactionSource = interaction, indication = null, onClick = item.onClick),
                    ) {
                        Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).height(BarHeight), verticalAlignment = Alignment.CenterVertically) {
                            if (showIcon) Box(Modifier.size(BarHeight), contentAlignment = Alignment.Center) {
                                Icon(if (active || selectedKey == item.key) item.filled else item.icon, item.label, tint, size = 12.dp)
                            }
                            if (showText) Text(
                                item.label,
                                style = labelStyle,
                                color = tint,
                                maxLines = 1,
                                modifier = Modifier.padding(start = if (showIcon) 0.dp else 12.dp).graphicsLayer {
                                    if (look == "sliding") {
                                        val o = openness(i)
                                        alpha = o
                                        translationX = -slide * (1f - o)
                                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        // Profile: the picture as a circle at the right (no shadow, no border), the same size as Search. Under the remote it
        // grows a little and gets a white ring. OK opens Settings.
        Box(Modifier.align(Alignment.CenterEnd)) {
            CircleButton(
                selected = selectedKey == "profile", shadow = false, canFocus = canFocus, plain = true,
                onClick = { app.openTab(Route.Settings) },
                modifier = Modifier.focusRequester(requester("profile")).onFocusChanged { if (it.isFocused) current = "profile" }.then(entry("profile")),
            ) { _, focused ->
                Box(Modifier.fillMaxSize().background(app.accent.color), contentAlignment = Alignment.Center) {
                    Text(user?.name?.take(1)?.uppercase() ?: "?", style = BlushType.caption, color = app.accent.onColor)
                    if (user != null) RemoteImage(app.repo.userImageUrl(user.userId, user.imageTag), Modifier.fillMaxSize())
                }
                if (focused || selectedKey == "profile") Box(Modifier.fillMaxSize().border(2.dp, Color.White.copy(alpha = if (focused) 1f else 0.5f), CircleShape))
            }
        }
    }
}

/** A round control at one end of the bar. Under the remote it turns white (or, if [plain], just grows a little). */
@Composable
private fun CircleButton(
    selected: Boolean,
    shadow: Boolean,
    canFocus: () -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    plain: Boolean = false,
    content: @Composable BoxScope.(ink: Color, focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val grow by animateFloatAsState(if (focused) 1.14f else 1f, tween(150), label = "circleGrow")
    val level by animateFloatAsState(if (focused) 1f else if (selected) 0.3f else 0f, tween(200), label = "circleLevel")
    val fill = if (plain) Color.Transparent else lerp(Color(0xFF23242B), Color.White, level)
    Box(
        modifier
            .size(CircleSize)
            .graphicsLayer { scaleX = grow; scaleY = grow }
            .then(if (shadow) Modifier.shadow(8.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
            .background(fill, CircleShape)
            .clip(CircleShape)
            .focusProperties { this.canFocus = canFocus() }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(if (focused) Ink else Color.White, focused) }
}

/** Lays out a tab screen. The navigation bar itself is drawn above every screen (see [NavBarHost]); this is the screen's side of it. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WithRail(
    app: AppState,
    selected: Route.Tab,
    railReturn: RailReturn = remember { RailReturn() },
    selectedLibrary: UUID? = null,
    /** For a page that isn't one of the main tabs but should still open the bar on Back (a library pinned to the bar). */
    backOpensRail: Boolean = false,
    content: @Composable () -> Unit,
) {
    val nav = LocalNavBar.current ?: remember { NavBar() }
    val contentFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var contentFocused by remember { mutableStateOf(false) }
    var pressedYet by remember { mutableStateOf(false) }
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    fun backToContent() {
        var restored = railReturn.restore?.invoke() ?: false
        if (!restored) restored = railReturn.last?.let { runCatching { it.requestFocus() }.getOrDefault(false) } ?: false
        if (!restored) runCatching { contentFocus.requestFocus() }
    }

    fun enterRail() {
        nav.armed = true
        if (runCatching { nav.entry.requestFocus() }.isFailure) nav.armed = false else nav.entries++
    }

    // The bar asks this screen to take the remote back (Back or Down from the bar).
    DisposableEffect(nav) {
        val mine: () -> Unit = { backToContent() }
        nav.toContent = mine
        onDispose { if (nav.toContent === mine) nav.toContent = null }
    }

    // Safety net: a page can drop the remote (the row holding it scrolled out of the list and was discarded), after which no
    // button does anything. If, once the remote has been used, focus is in neither the page nor the bar for ~0.6 s (and no pop-up
    // has the window), it is put back where it was.
    LaunchedEffect(Unit) {
        var lost = 0
        while (true) {
            kotlinx.coroutines.delay(300)
            if (pressedYet && !contentFocused && !nav.focused && !nav.armed && windowInfo.isWindowFocused) lost++ else lost = 0
            if (lost >= 2) { lost = 0; backToContent() }
        }
    }

    // Back on one of the main tabs opens the bar (registered before the screen's own Back handling, so a screen's open panel still
    // closes first). Back from the bar returns to the screen, exactly where it was, and the cursor slides back to its item.
    androidx.activity.compose.BackHandler(enabled = !nav.focused && (app.current is Route.Tab || backOpensRail)) { enterRail() }
    androidx.activity.compose.BackHandler(enabled = nav.focused) { backToContent() }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(contentFocus)
                .onFocusChanged { contentFocused = it.hasFocus }
                .focusRestorer()
                // Every direction button is noted for the navigation click (see NavSound); nothing is consumed here.
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown) { NavSound.pressed(e.key); pressedYet = true }
                    false
                }
                // Arrow keys the screen itself didn't use: Up at the top of the screen opens the bar; the other directions
                // only move within the screen.
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val direction = when (e.key) {
                        Key.DirectionLeft -> FocusDirection.Left
                        Key.DirectionRight -> FocusDirection.Right
                        Key.DirectionUp -> FocusDirection.Up
                        Key.DirectionDown -> FocusDirection.Down
                        else -> return@onKeyEvent false
                    }
                    val railHadFocus = nav.focused
                    val moved = focusManager.moveFocus(direction)
                    when {
                        direction == FocusDirection.Up && !moved -> enterRail()
                        // Landed in the bar by any route other than Up at the top: put focus back where it was.
                        moved && nav.focused && !railHadFocus -> backToContent()
                    }
                    true
                }
                .focusGroup(),
        ) { androidx.compose.runtime.CompositionLocalProvider(LocalRailReturn provides railReturn) { content() } }
    }
    DisposableEffect(Unit) { onDispose { railReturn.restore = null } }
}
