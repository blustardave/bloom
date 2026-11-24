package app.blush.tv.ui.settings

import androidx.compose.foundation.background
import app.blush.tv.data.logged
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blush.tv.BuildConfig
import app.blush.tv.data.ErrorLog
import androidx.compose.foundation.layout.heightIn
import app.blush.tv.data.HomeCache
import app.blush.tv.data.RailPin
import app.blush.tv.playback.Languages
import app.blush.tv.ui.AppState
import app.blush.tv.ui.ContentEnd
import app.blush.tv.ui.ContentStart
import app.blush.tv.ui.Lite
import app.blush.tv.ui.MaxRailPins
import app.blush.tv.ui.PageTop
import app.blush.tv.ui.Perf
import app.blush.tv.ui.Route
import app.blush.tv.ui.components.DropdownRow
import app.blush.tv.ui.components.FocusSurface
import app.blush.tv.ui.components.Icon
import app.blush.tv.ui.components.Icons
import app.blush.tv.ui.components.LocalPanelTextScale
import app.blush.tv.ui.components.LocalRowCorner
import app.blush.tv.ui.components.LocalOptionIndent
import app.blush.tv.ui.components.PivotScrolling
import app.blush.tv.ui.components.glideSpec
import app.blush.tv.ui.components.OptionRow
import app.blush.tv.ui.components.RemoteImage
import app.blush.tv.ui.components.SecondaryButton
import app.blush.tv.ui.components.ToggleRow
import app.blush.tv.ui.home.HomeRowOptions
import app.blush.tv.ui.home.HomeRowOrderable
import app.blush.tv.ui.player.SubtitleLook
import app.blush.tv.ui.theme.Accents
import app.blush.tv.ui.theme.Backdrops
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.theme.Text
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

private val bitrates = listOf(
    "Original" to 120_000_000,
    "40 Mbps" to 40_000_000,
    "20 Mbps" to 20_000_000,
    "8 Mbps" to 8_000_000,
    "4 Mbps" to 4_000_000,
)

private val previewModes = listOf("on" to "With sound", "muted" to "Muted", "off" to "Off")
private val pillStyles = listOf(
    Triple("text", "Names only", "Just the names, always"),
    Triple("iconsText", "Icons with names", "Icons and names, always"),
    Triple("icons", "Icons only", "Just the icons, always"),
    Triple("sliding", "Icons, names slide out", "Icons only; the name of the item the remote is on slides out"),
)
private val genreCounts = listOf(0, 2, 4, 6, 8, 12)
private val rowSizes = listOf(10, 15, 20, 30, 40)

private enum class Tab(val label: String, val blurb: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Account("Account", "Your profile and server.", Icons.User),
    Appearance("Appearance", "Colours, the navigation bar and what Home shows.", Icons.Settings),
    NavBar("Nav Bar", "The libraries pinned to the navigation bar, in the order you want them.", Icons.Rows),
    Playback("Playback", "How films and series play.", Icons.Play),
    Subtitles("Subtitles", "One look for every film's subtitles.", Icons.Subtitles),
    About("About", "This app, your server and housekeeping.", Icons.Info),
}

/** One colour for the profile card and the chosen tab, and a brighter one when the remote is on either. */
private val RestFill = Color.White.copy(alpha = 0.08f)
private val HoverFill = Color.White.copy(alpha = 0.17f)

/**
 * Settings, in two panes: the tabs on the left (the profile card first, then the rest), and the chosen tab's options on the
 * right as rounded cards. The right side scrolls as one page: its title goes up with it and fades out behind the navigation bar.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(app: AppState) {
    var tab by remember { mutableStateOf(Tab.Appearance) }
    val tabFocus = remember { Tab.entries.associateWith { FocusRequester() } }
    val optionsFocus = remember { FocusRequester() }
    var inOptions by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { tabFocus.getValue(Tab.Appearance).requestFocus() } }

    CompositionLocalProvider(LocalPanelTextScale provides 0.88f, LocalRowCorner provides 12.dp, LocalOptionIndent provides 0.dp) {
        Row(
            Modifier
                .fillMaxSize()
                .background(Blush.colors.background)
                .padding(start = SideMargin, end = SideMargin)
                // Right from the tabs always lands on the first option. (Leaving the options to the left is handled by the
                // options pane below: it always hands the remote to the tab being shown.)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight && !inOptions) {
                        runCatching { optionsFocus.requestFocus() }
                        true
                    } else false
                },
        ) {
            Column(Modifier.width(184.dp).fillMaxHeight().padding(top = PageTop, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Settings", style = PageTitle, modifier = Modifier.padding(bottom = 14.dp))
                AccountCard(app, tab == Tab.Account, onFocused = { tab = Tab.Account }, modifier = Modifier.focusRequester(tabFocus.getValue(Tab.Account)))
                Spacer(Modifier.height(8.dp))
                Tab.entries.filter { it != Tab.Account }.forEach { t ->
                    TabItem(t.label, t.icon, selected = t == tab, onFocused = { tab = t }, modifier = Modifier.focusRequester(tabFocus.getValue(t)))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Bloom ${BuildConfig.VERSION_NAME}",
                    style = ps(BlushType.caption),
                    color = Blush.colors.textDim,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Spacer(Modifier.width(40.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onFocusChanged { inOptions = it.hasFocus }
                    .focusRequester(optionsFocus)
                    // The remote can only leave the options to the left, and then always onto the tab whose options these are.
                    .focusProperties {
                        exit = {
                            when (it) {
                                FocusDirection.Left -> tabFocus.getValue(tab)
                                FocusDirection.Up, FocusDirection.Down, FocusDirection.Right -> FocusRequester.Cancel
                                else -> FocusRequester.Default
                            }
                        }
                    }
                    .focusGroup(),
            ) {
                androidx.compose.runtime.key(tab) {
                    when (tab) {
                        Tab.Account -> AccountPane(app)
                        Tab.Appearance -> AppearancePane(app)
                        Tab.NavBar -> NavBarPane(app)
                        Tab.Playback -> PlaybackPane(app)
                        Tab.Subtitles -> SubtitlesPane(app)
                        Tab.About -> AboutPane(app)
                    }
                }
            }
        }
    }
}

private val SideMargin = 96.dp
/** The big title at the top of both panes: the same size and the same height on each side. */
private val PageTitle = BlushType.headline.copy(fontSize = 21.sp, fontWeight = FontWeight.W700)

/** The page's panel scale applied to a text style, so everything in Settings reads at the same size as its dropdowns and switches. */
@Composable
private fun ps(style: androidx.compose.ui.text.TextStyle) = style.copy(fontSize = style.fontSize * LocalPanelTextScale.current)

/** The rounded box under "Settings": the profile picture, the name in big letters and the server in the accent colour. */
@Composable
private fun AccountCard(app: AppState, selected: Boolean, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val user = app.user
    FocusSurface(
        onClick = onFocused,
        onFocused = onFocused,
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1f,
        ringWidth = 0.dp,
        background = RestFill,
        focusedBackground = HoverFill,
        modifier = modifier.fillMaxWidth(),
    ) { _ ->
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Blush.colors.accent.color), contentAlignment = Alignment.Center) {
                Text(user?.name?.take(1)?.uppercase() ?: "?", style = BlushType.title, color = Blush.colors.accent.onColor)
                if (user != null) RemoteImage(app.repo.userImageUrl(user.userId, user.imageTag), Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    user?.name ?: "Profile",
                    style = BlushType.title.copy(fontSize = 12.5.sp, fontWeight = FontWeight.W700, lineHeight = 15.sp, platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                    color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    app.repo.store.server?.name ?: "Your server",
                    style = BlushType.caption.copy(fontSize = 9.sp, lineHeight = 12.sp, platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                    color = Blush.colors.accent.color,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.ChevronRight, null, Blush.colors.textMuted, size = 12.dp)
        }
    }
}

@Composable
private fun TabItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(
        onClick = onFocused,
        onFocused = onFocused,
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1f,
        ringWidth = 0.dp,
        background = if (selected) RestFill else Color.Transparent,
        focusedBackground = HoverFill,
        modifier = modifier.fillMaxWidth(),
    ) { focused ->
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val ink = if (focused || selected) Color.White else Blush.colors.textMuted
            Icon(icon, null, ink, size = 12.dp)
            Text(label, style = BlushType.title.copy(fontSize = 11.sp, fontWeight = FontWeight.W600), color = ink, modifier = Modifier.weight(1f))
        }
    }
}

/** Remembers each dropdown's header so that, when an option is picked and the list closes, the remote goes back to its header. */
@Stable
private class HeaderFocus(val state: androidx.compose.foundation.ScrollState) {
    private val requesters = mutableMapOf<String, FocusRequester>()
    fun of(key: String) = requesters.getOrPut(key) { FocusRequester() }
    /** Moves focus to the header right away, while the option you picked is still there (otherwise it would fall to the tabs). */
    fun restore(key: String) { runCatching { of(key).requestFocus() } }
}

@Composable
private fun rememberHeaderFocus(): HeaderFocus {
    val state = androidx.compose.foundation.rememberScrollState()
    return remember { HeaderFocus(state) }
}

@Composable
private fun Dd(hf: HeaderFocus, key: String, label: String, value: String, open: Boolean, onClick: () -> Unit) =
    DropdownRow(label, value, open, onClick, requester = hf.of(key))

/**
 * One tab's page: its title and blurb, then cards. It scrolls as a whole (the title included) and the top of it fades out
 * behind the navigation bar, so nothing stays fixed at the top.
 */
/** Keeps whatever has the remote clear of the navigation bar (and the faded top), and brings the page back down on the way up. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private class PaneScroll(
    private val state: androidx.compose.foundation.ScrollState,
    private val topPx: Float,
    private val bottomPx: Float,
    /** Within this much of the top, moving up goes all the way to the top, so the title shows again. */
    private val titlePx: Float,
) : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> = glideSpec(200)
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = offset + size
        return when {
            // Near the top of the page, going up shows the whole top (title included).
            // Whatever is near the top of the page (visible or not): go all the way up, so the title shows again.
            state.value > 0 && state.value + offset < titlePx -> -state.value.toFloat()
            offset < topPx -> offset - topPx
            end > containerSize - bottomPx -> minOf(end - (containerSize - bottomPx), offset - topPx)
            else -> 0f
        }
    }
}

/** What a tab's page is made of: pieces added one after another by the tab. */
private class PaneScope {
    val pieces = mutableListOf<Pair<Any?, @Composable () -> Unit>>()
    fun item(key: Any? = null, content: @Composable () -> Unit) { pieces.add(key to content) }
}

/**
 * One tab's page: its title and blurb, then cards. It scrolls as a whole (the title included) and the top of it fades out
 * behind the navigation bar, so nothing stays fixed at the top. Everything on the page is built at once (it is small), so the
 * remote always has a next row to go to and the page can scroll to it.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun Pane(tab: Tab, hf: HeaderFocus? = null, content: PaneScope.() -> Unit) {
    val top = PageTop
    val state = hf?.state ?: androidx.compose.foundation.rememberScrollState()
    val density = LocalDensity.current
    val spec = remember(state, density) {
        PaneScroll(state, with(density) { (PageTop + 26.dp).toPx() }, with(density) { 56.dp.toPx() }, with(density) { 260.dp.toPx() })
    }
    val pane = PaneScope().also(content)
    val bg = Blush.colors.background
    PivotScrolling(spec) {
        Column(
            Modifier
                .fillMaxSize()
                .focusProperties { exit = { if (it == FocusDirection.Up || it == FocusDirection.Down) FocusRequester.Cancel else FocusRequester.Default } }
                .focusGroup()
                // The options fade out under the navigation bar: the page colour is painted over the top strip (no off-screen layer,
                // which drew every line of text in Settings through an extra bitmap and could soften it).
                .drawWithContent {
                    drawContent()
                    val end = top.toPx()
                    val start = 8.dp.toPx()
                    drawRect(Brush.verticalGradient(0f to bg, 1f to bg.copy(alpha = 0f), startY = start, endY = end), size = Size(size.width, end))
                    drawRect(bg, size = Size(size.width, start))
                }
                .androidx_verticalScroll(state)
                .padding(top = top, bottom = 110.dp),
        ) {
            // The title only, at the same height and size as "Settings" on the left, with the same gap under it, so the first option
            // starts level with the top of the profile card.
            Text(tab.label, style = PageTitle, modifier = Modifier.padding(start = 14.dp, bottom = 16.dp))
            pane.pieces.forEachIndexed { i, (k, piece) -> androidx.compose.runtime.key(k ?: i) { piece() } }
        }
    }
}

private fun Modifier.androidx_verticalScroll(state: androidx.compose.foundation.ScrollState) = this.verticalScroll(state)

/** A column that draws a faint line between its children. */
@Composable
private fun Divided(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val ys = remember { mutableListOf<Int>() }
    val line = Color.White.copy(alpha = 0.07f)
    val inset = with(LocalDensity.current) { 10.dp.toPx() }
    androidx.compose.ui.layout.Layout(
        content,
        modifier.drawBehind {
            ys.forEach { y -> drawLine(line, Offset(inset, y.toFloat()), Offset(size.width - inset, y.toFloat()), 1f) }
        },
    ) { measurables, constraints ->
        val fixed = androidx.compose.ui.unit.Constraints(
            minWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0,
            maxWidth = constraints.maxWidth, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity,
        )
        val placeables = measurables.map { it.measure(fixed) }
        ys.clear()
        var y = 0
        placeables.forEachIndexed { i, p -> if (i > 0) ys.add(y); y += p.height }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else (placeables.maxOfOrNull { it.width } ?: 0)
        layout(width, y) {
            var at = 0
            placeables.forEach { it.placeRelative(0, at); at += it.height }
        }
    }
}

/** A heading outside, and a rounded card holding the rows. */
private fun PaneScope.card(key: String, heading: String? = null, note: String? = null, boxed: Boolean = true, content: @Composable () -> Unit) {
    item(key = key) {
        Column(Modifier.padding(bottom = 22.dp)) {
            if (heading != null) Text(heading, style = ps(BlushType.label), color = Blush.colors.textMuted, modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
            Divided(if (boxed) Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 4.dp, vertical = 4.dp) else Modifier.fillMaxWidth().padding(horizontal = 4.dp)) { content() }
            if (note != null) Text(note, style = ps(BlushType.caption), color = Blush.colors.textDim, modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp))
        }
    }
}

/** A setting that isn't built yet: shown dim, so it's clear it's coming. */
@Composable
private fun SoonRow(title: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = ps(BlushType.label).copy(fontWeight = FontWeight.W600), color = Blush.colors.textDim, modifier = Modifier.weight(1f))
        Text("Coming soon", style = ps(BlushType.caption), color = Blush.colors.textDim)
    }
}

/** A button with a line saying what it does underneath. */
@Composable
private fun ActionRow(title: String, detail: String?, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    FocusSurface(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(12.dp),
        focusedScale = 1f,
        ringWidth = 0.dp,
        background = Color.Transparent,
        focusedBackground = Color.White.copy(alpha = 0.12f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = ps(BlushType.label).copy(fontWeight = FontWeight.W600), color = if (enabled) Blush.colors.text else Blush.colors.textDim)
            if (detail != null) Text(detail, style = ps(BlushType.caption), color = Blush.colors.textDim)
        }
    }
}

/** A small up or down arrow beside a row that can be moved: dim until the remote is on it, then bright and a touch bigger. */
@Composable
private fun ArrowButton(up: Boolean, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        shape = CircleShape,
        focusedScale = 1f,
        ringWidth = 0.dp,
        background = Color.Transparent,
        focusedBackground = Color.Transparent,
        modifier = Modifier.size(26.dp),
    ) { focused ->
        Icon(
            Icons.ChevronDown, if (up) "Move up" else "Move down",
            if (focused) Color.White else Color.White.copy(alpha = 0.34f),
            Modifier.align(Alignment.Center).graphicsLayer { rotationZ = if (up) 180f else 0f },
            size = if (focused) 14.dp else 12.dp,
        )
    }
}

// ---------------- Account ----------------

@Composable
private fun AccountPane(app: AppState) {
    // "signout" or "server" while waiting for the second press; null otherwise.
    var confirm by remember { mutableStateOf<String?>(null) }
    val firstButton = remember { FocusRequester() }
    var changedOnce by remember { mutableStateOf(false) }
    // The button you pressed disappears, so hand the remote to the first button of the new set.
    LaunchedEffect(confirm) {
        if (!changedOnce) { changedOnce = true; return@LaunchedEffect }
        kotlinx.coroutines.delay(50)
        runCatching { firstButton.requestFocus() }
    }
    Pane(Tab.Account) {
        card("account") {
            when (confirm) {
                "signout" -> {
                    Text("Sign out of this profile? You'll need its password again.", style = ps(BlushType.body), color = Blush.colors.textSoft, modifier = Modifier.padding(10.dp))
                    ActionRow("Cancel", null, Modifier.focusRequester(firstButton)) { confirm = null }
                    ActionRow("Yes, sign out", null) { app.signOut() }
                }
                "server" -> {
                    Text("Change server? You'll pick a server and sign in again.", style = ps(BlushType.body), color = Blush.colors.textSoft, modifier = Modifier.padding(10.dp))
                    ActionRow("Cancel", null, Modifier.focusRequester(firstButton)) { confirm = null }
                    ActionRow("Yes, change server", null) { app.changeServer() }
                }
                else -> {
                    ActionRow("Switch profile", "Back to \"Who's watching?\"", Modifier.focusRequester(firstButton)) { app.switchProfile() }
                    ActionRow("Change server", "Pick another server and sign in again") { confirm = "server" }
                    ActionRow("Sign out", "You'll need this profile's password again") { confirm = "signout" }
                }
            }
        }
    }
}

// ---------------- Appearance ----------------

@Composable
private fun AppearancePane(app: AppState) {
    val hf = rememberHeaderFocus()
    val store = app.repo.store
    var open by remember { mutableStateOf<String?>(null) }
    // The panel is off at start-up whatever was saved, so the switch shows what is happening now.
    var lite by remember { mutableStateOf(store.liteMode) }
    var previews by remember { mutableStateOf(store.previews) }
    var genreCount by remember { mutableStateOf(store.genreRowCount) }
    var rowSize by remember { mutableStateOf(store.homeRowSize) }
    var keepPlace by remember { mutableStateOf(store.rowsRememberPlace) }
    var quick by remember { mutableStateOf(store.quickNav) }
    var pinned by remember { mutableStateOf(store.pinnedGenres) }
    var hidden by remember { mutableStateOf(store.hiddenHomeRows) }
    val defaultOrder = remember { HomeRowOrderable.map { it.first } }
    // The household's own order for Home's rows; kinds added since it was saved go on the end, kinds that no longer exist are dropped.
    var order by remember {
        mutableStateOf(store.homeRowOrder.filter { it in defaultOrder }.let { o -> if (o.isEmpty()) defaultOrder else o + defaultOrder.filter { it !in o } })
    }
    var genres by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { genres = logged("SettingsScreen.AppearancePane") { app.repo.genres() }.getOrDefault(emptyList()) }
    fun toggle(key: String) { open = if (open == key) null else key }
    fun changed() { app.homeChanged = true }
    fun move(rowKey: String, step: Int) {
        val at = order.indexOf(rowKey)
        val to = at + step
        if (at < 0 || to !in order.indices) return
        order = order.toMutableList().apply { add(to, removeAt(at)) }
        store.homeRowOrder = order
        changed()
    }
    fun flip(rowKey: String) {
        hidden = if (rowKey in hidden) hidden - rowKey else hidden + rowKey
        store.hiddenHomeRows = hidden
        changed()
    }

    Pane(Tab.Appearance, hf) {
        card("backdrop", "Theme", boxed = false) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Backdrops.all.forEach { b ->
                    val selected = b.id == app.backdrop.id
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FocusSurface(
                            onClick = { app.setBackdrop(b.id) },
                            shape = RoundedCornerShape(10.dp),
                            focusedScale = 1.08f,
                            background = b.background,
                            ringWidth = 2.dp,
                            // A faint outline so the darkest themes still show against the page, brighter on the chosen one.
                            modifier = Modifier.size(width = 58.dp, height = 36.dp),
                        ) {
                            Box(Modifier.fillMaxSize().border(if (selected) 1.5.dp else 1.dp, Color.White.copy(alpha = if (selected) 0.6f else 0.1f), RoundedCornerShape(10.dp)))
                            // A tiny page: a row of tiles in the theme's raised colour.
                            Row(Modifier.align(Alignment.BottomStart).padding(start = 7.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                repeat(4) { i -> Box(Modifier.size(width = 8.dp, height = 12.dp).clip(RoundedCornerShape(2.dp)).background(if (i == 0) b.surfaceRaised else b.surface)) }
                            }
                        }
                        Text(b.name, style = ps(BlushType.caption), color = if (selected) Blush.colors.text else Blush.colors.textMuted)
                    }
                }
            }
        }
        card("accent", "Accent colour", boxed = false) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Accents.all.forEach { accent ->
                    val selected = accent.id == app.accent.id
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FocusSurface(
                            onClick = { app.setAccent(accent.id) },
                            shape = CircleShape,
                            focusedScale = 1.12f,
                            background = accent.color,
                            ringWidth = 0.dp,
                            modifier = Modifier.size(30.dp),
                        ) {
                            if (selected) Icon(Icons.Check, null, accent.onColor, Modifier.align(Alignment.Center), size = 14.dp)
                        }
                        Text(accent.name, style = ps(BlushType.caption), color = if (selected) Blush.colors.text else Blush.colors.textMuted)
                    }
                }
            }
        }
        card("size", "Size") {
            SoonRow("Display size")
        }
        card("home", "Home screen") {
            Dd(hf, "previews", "Previews", previewModes.first { it.first == previews }.second, open == "previews") { toggle("previews") }
            if (open == "previews") previewModes.forEach { (value, name) ->
                OptionRow(name, null, previews == value, onClick = { previews = value; store.previews = value; open = null; hf.restore("previews"); changed() })
            }
            Dd(hf, "rowSize", "Titles per row", "$rowSize", open == "rowSize") { toggle("rowSize") }
            if (open == "rowSize") rowSizes.forEach { n ->
                OptionRow("$n titles", if (n == 20) "The usual" else null, rowSize == n, onClick = { rowSize = n; store.homeRowSize = n; open = null; hf.restore("rowSize"); changed() })
            }
            Dd(hf, "genreCount", "Genre rows", if (genreCount == 0) "None" else "$genreCount", open == "genreCount") { toggle("genreCount") }
            if (open == "genreCount") genreCounts.forEach { n ->
                OptionRow(if (n == 0) "None" else "$n rows", null, genreCount == n, onClick = { genreCount = n; store.genreRowCount = n; open = null; hf.restore("genreCount"); changed() })
            }
            Dd(
                hf, "pinned", "Always show these genres",
                when (pinned.size) { 0 -> "None"; 1 -> pinned.first(); else -> "${pinned.size} chosen" },
                open == "pinned",
            ) { toggle("pinned") }
            // Several can be chosen, so this list stays open while you pick.
            if (open == "pinned") genres.forEach { g ->
                OptionRow(g, null, g in pinned, onClick = {
                    pinned = if (g in pinned) pinned - g else pinned + g
                    store.pinnedGenres = pinned
                    changed()
                })
            }
            ToggleRow(
                "Rows remember your place", keepPlace,
                detail = "Coming back from a title finds each row where you left it",
                onToggle = { keepPlace = !keepPlace; store.rowsRememberPlace = keepPlace },
            )
            ToggleRow(
                "Quicker row movement", quick,
                detail = "Rows slide faster, quick presses are never skipped, and a held Up or Down picks up speed",
                onToggle = { quick = !quick; store.quickNav = quick },
            )
        }
        card("rows", "Home rows", note = "Select switches a row on or off. The arrows move it up or down the page.") {
            order.forEach { rowKey ->
                androidx.compose.runtime.key(rowKey) {
                    val name = HomeRowOrderable.firstOrNull { it.first == rowKey }?.second ?: rowKey
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ToggleRow(name, rowKey !in hidden, onToggle = { flip(rowKey) }, modifier = Modifier.weight(1f))
                        ArrowButton(up = true) { move(rowKey, -1) }
                        ArrowButton(up = false) { move(rowKey, 1) }
                    }
                }
            }
            ToggleRow("\"Surprise me\" button", "surprise" !in hidden, onToggle = { flip("surprise") }, detail = "At the bottom of Home")
            ActionRow("Back to the usual order", null, enabled = store.homeRowOrder.isNotEmpty()) {
                order = defaultOrder
                store.homeRowOrder = emptyList()
                changed()
            }
        }
        card("styles", "Featured film, detail page & Top 10") {
            SoonRow("Featured film style")
            SoonRow("Detail page style")
            SoonRow("Top 10 style")
        }
        card("perf", "Performance") {
            ToggleRow(
                "Lite mode", lite,
                detail = "Lower graphical load for slower sticks: no previews, no banner rotation, no fades between pictures",
                onToggle = { lite = !lite; store.liteMode = lite; Lite.on = lite },
            )
        }
    }
}

// ---------------- Nav bar ----------------

/** The libraries as last loaded, so the Nav Bar tab doesn't wait for them every time. */
private var cachedLibraries: List<BaseItemDto>? = null

@Composable
private fun NavBarPane(app: AppState) {
    val hf = rememberHeaderFocus()
    var open by remember { mutableStateOf<String?>(null) }
    // Kept from the last visit, so the list is there straight away; it is refreshed quietly behind it.
    var libraries by remember { mutableStateOf(cachedLibraries) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        if (attempt > 0) libraries = null
        val fresh = logged("SettingsScreen.NavBarPane") { app.repo.libraries() }.getOrDefault(emptyList())
        if (fresh.isNotEmpty() || libraries == null) libraries = fresh
        if (fresh.isNotEmpty()) cachedLibraries = fresh
    }
    val pins = app.railPins
    fun move(pin: RailPin, step: Int) {
        val at = pins.indexOf(pin)
        val to = at + step
        if (at < 0 || to !in pins.indices) return
        app.updateRailPins(pins.toMutableList().apply { add(to, removeAt(at)) })
    }
    Pane(Tab.NavBar, hf) {
        card("navbar", "Style") {
            Dd(hf, "pill", "Navigation bar style", pillStyles.first { it.first == app.pillStyle }.second, open == "pill") { open = if (open == "pill") null else "pill" }
            if (open == "pill") pillStyles.forEach { (value, name, detail) ->
                OptionRow(name, null, app.pillStyle == value, onClick = { app.choosePillStyle(value); open = null; hf.restore("pill") })
            }
        }
        val list = libraries
        if (list == null) {
            card("loading") { Text("Loading your libraries…", style = ps(BlushType.body), color = Blush.colors.textMuted, modifier = Modifier.padding(10.dp)) }
        } else if (list.isEmpty()) {
            card("none") {
                Text("Couldn't load your libraries.", style = ps(BlushType.body), color = Blush.colors.textMuted, modifier = Modifier.padding(10.dp))
                ActionRow("Try again", null) { attempt += 1 }
            }
        } else {
            // One list: the pinned libraries first (in their order), then the others. A library keeps the same row when it is pinned or
            // taken out, so the remote stays on it instead of being dropped back onto the tabs.
            val pinned = pins.mapNotNull { pin -> list.firstOrNull { it.id.toString() == pin.libraryId }?.let { it to pin } }
            val others = list.filter { lib -> pins.none { it.libraryId == lib.id.toString() } }
            card("libs", "Libraries", note = "${pins.size} of $MaxRailPins pinned. Select pins or unpins a library; the arrows set the order of the pinned ones.") {
                // One loop with one key: a library that moves between the two groups keeps its row (and the remote).
                (pinned.map { (lib, pin) -> lib to pin } + others.map { it to null }).forEach { (lib, pin) ->
                    androidx.compose.runtime.key(lib.id) { LibraryRow(app, lib.name.orEmpty(), lib.id.toString(), pin, pins, ::move) }
                }
            }
        }
    }
}

/** One library in the Nav Bar list: its switch, and (when pinned) the arrows and the choice of icon. The switch is always in the same place. */
@Composable
private fun LibraryRow(app: AppState, name: String, id: String, pin: RailPin?, pins: List<RailPin>, move: (RailPin, Int) -> Unit) {
    val firstIcon = remember { FocusRequester() }
    Column {
        // Down from the switch or the arrows lands on the first icon, not whichever is nearest.
        Row(Modifier.focusProperties { down = if (pin != null) firstIcon else FocusRequester.Default }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToggleRow(
                name, pin != null,
                detail = if (pin == null && pins.size >= MaxRailPins) "The navigation bar is full: take another library out first" else null,
                onToggle = {
                    if (pin != null) app.updateRailPins(pins - pin)
                    else if (pins.size < MaxRailPins) app.updateRailPins(pins + RailPin(id, name, Icons.pinIcons.keys.first()))
                },
                modifier = Modifier.weight(1f),
            )
            if (pin != null) {
                ArrowButton(up = true) { move(pin, -1) }
                ArrowButton(up = false) { move(pin, 1) }
            }
        }
        if (pin != null) Row(Modifier.padding(start = 12.dp, top = 2.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icons.pinIcons.entries.forEachIndexed { n, (iconName, icons) ->
                val chosen = pin.icon == iconName
                FocusSurface(
                    onClick = { app.updateRailPins(pins.map { if (it == pin) it.copy(icon = iconName) else it }) },
                    shape = CircleShape,
                    focusedScale = 1.08f,
                    ringWidth = 0.dp,
                    background = if (chosen) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                    focusedBackground = Color.White.copy(alpha = 0.24f),
                    modifier = Modifier.size(30.dp).let { if (n == 0) it.focusRequester(firstIcon) else it },
                ) {
                    Icon(if (chosen) icons.second else icons.first, iconName, if (chosen) Blush.colors.text else Blush.colors.textMuted, Modifier.align(Alignment.Center), size = 14.dp)
                }
            }
        }
    }
}

// ---------------- Playback ----------------

@Composable
private fun PlaybackPane(app: AppState) {
    val hf = rememberHeaderFocus()
    val store = app.repo.store
    var open by remember { mutableStateOf<String?>(null) }
    var bitrate by remember { mutableStateOf(store.maxBitrate) }
    var passthrough by remember { mutableStateOf(store.passthroughAudio) }
    var autoNext by remember { mutableStateOf(store.autoNextEpisode) }
    var audioLang by remember { mutableStateOf(store.defaultAudioLang) }
    var subLang by remember { mutableStateOf(store.defaultSubtitleLang) }
    val subOptions = listOf("auto" to "Automatic", "off" to "Off") + Languages.all.map { it.code to it.name }
    fun toggle(key: String) { open = if (open == key) null else key }
    Pane(Tab.Playback, hf) {
        card("lang", "Language", note = "A series remembers the audio and subtitles you pick for it, and uses them for its next episodes.") {
            Dd(hf, "audioLang", "Audio language", Languages.nameOf(audioLang), open == "audioLang") { toggle("audioLang") }
            if (open == "audioLang") Languages.all.forEach { lang ->
                OptionRow(lang.name, null, Languages.same(audioLang, lang.code), onClick = { audioLang = lang.code; store.defaultAudioLang = lang.code; open = null; hf.restore("audioLang") })
            }
            Dd(hf, "subLang", "Subtitles", subOptions.firstOrNull { it.first == subLang }?.second ?: Languages.nameOf(subLang), open == "subLang") { toggle("subLang") }
            if (open == "subLang") subOptions.forEach { (code, name) ->
                OptionRow(
                    name,
                    if (code == "auto") "English, if the film has subtitles on by default" else null,
                    subLang == code,
                    onClick = { subLang = code; store.defaultSubtitleLang = code; open = null; hf.restore("subLang") },
                )
            }
        }
        card("quality", "Quality and sound") {
            Dd(hf, "quality", "Streaming quality", bitrates.firstOrNull { it.second == bitrate }?.first ?: "Original", open == "quality") { toggle("quality") }
            if (open == "quality") bitrates.forEach { (name, value) ->
                OptionRow(name, if (value == bitrates.first().second) "Plays files as they are whenever possible" else null, bitrate == value, onClick = { bitrate = value; store.maxBitrate = value; open = null; hf.restore("quality") })
            }
            ToggleRow(
                "Send surround sound to the TV", passthrough,
                detail = "Off: the app decodes sound so the volume slider and speed work. On: untouched Dolby/DTS, but the app's volume does nothing",
                onToggle = { passthrough = !passthrough; store.passthroughAudio = passthrough },
            )
        }
        card("behaviour", "While watching") {
            ToggleRow("Play the next episode automatically", autoNext, detail = "Off: the player closes when an episode ends", onToggle = { autoNext = !autoNext; store.autoNextEpisode = autoNext })
        }
    }
}

// ---------------- Subtitles ----------------

@Composable
private fun SubtitlesPane(app: AppState) {
    val hf = rememberHeaderFocus()
    val store = app.repo.store
    var open by remember { mutableStateOf<String?>(null) }
    // Bumped on every change, so the preview redraws from the saved settings.
    var changes by remember { mutableIntStateOf(0) }
    val look = remember(changes) { SubtitleLook.of(store) }
    fun toggle(key: String) { open = if (open == key) null else key }
    Pane(Tab.Subtitles, hf) {
        item(key = "preview") {
            Box(Modifier.fillMaxWidth().padding(bottom = 20.dp), contentAlignment = Alignment.Center) { SubtitlePreview(look) }
        }
        card("text", "Text") {
            Choice(hf, open, { toggle(it) }, "size", "Size", SubtitleLook.sizes.map { it.first }, store.subtitleSize) { store.subtitleSize = it; changes++; open = null }
            Choice(hf, open, { toggle(it) }, "font", "Font", SubtitleLook.fonts, store.subtitleFont) { store.subtitleFont = it; changes++; open = null }
            Choice(hf, open, { toggle(it) }, "weight", "Weight", SubtitleLook.weights.map { it.first }, store.subtitleWeight) { store.subtitleWeight = it; changes++; open = null }
            Choice(hf, open, { toggle(it) }, "colour", "Colour", SubtitleLook.colours.map { it.first }, store.subtitleColour) { store.subtitleColour = it; changes++; open = null }
        }
        card("look", "Edge and background") {
            Choice(hf, open, { toggle(it) }, "edge", "Edge", SubtitleLook.edges, store.subtitleEdge) { store.subtitleEdge = it; changes++; open = null }
            Choice(hf, open, { toggle(it) }, "bg", "Background", SubtitleLook.backgrounds.map { it.first }, store.subtitleBackground) { store.subtitleBackground = it; changes++; open = null }
        }
        card(
            "place", "Position",
            note = "Subtitles also move up out of the way while the player's controls are showing. Picture-based subtitles (PGS) keep their own look; only their height follows this. Timing and size can also be changed while watching, in the player's settings.",
        ) {
            Choice(hf, open, { toggle(it) }, "pos", "Height on screen", SubtitleLook.positions.map { it.first }, store.subtitlePosition) { store.subtitlePosition = it; changes++; open = null }
        }
    }
}

/** A dropdown of [names] with the current one ([now]) ticked; [save] gets the one chosen. */
@Composable
private fun Choice(hf: HeaderFocus, open: String?, toggle: (String) -> Unit, key: String, label: String, names: List<String>, now: String, save: (String) -> Unit) {
    Dd(hf, key, label, now, open == key) { toggle(key) }
    if (open == key) names.forEach { v -> OptionRow(v, null, now == v, onClick = { save(v); hf.restore(key) }) }
}

private fun runtimeText(ticks: Long?): String? {
    val minutes = ((ticks ?: return null) / 600_000_000L).toInt()
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

/** One figure: its name, the value, and a line of detail. It takes focus so the list can be scrolled; if it has somewhere to go, Select goes there. */
@Composable
private fun StatRow(label: String, value: String, detail: String? = null, onClick: (() -> Unit)? = null) {
    FocusSurface(
        onClick = onClick ?: {},
        shape = RoundedCornerShape(12.dp),
        focusedScale = 1f,
        ringWidth = 0.dp,
        background = Color.Transparent,
        focusedBackground = Color.White.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = ps(BlushType.caption), color = Blush.colors.textMuted)
            Text(value, style = ps(BlushType.label).copy(fontWeight = FontWeight.W600))
            if (detail != null) Text(detail, style = ps(BlushType.caption), color = Blush.colors.textDim)
        }
    }
}

// ---------------- About ----------------

@Composable
private fun AboutPane(app: AppState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val store = app.repo.store
    val server = store.server
    var showStats by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var onScreen by remember { mutableStateOf(store.showStats) }
    var note by remember { mutableStateOf<String?>(null) }
    if (showStats) StatisticsPopup { showStats = false }
    if (showErrors) RecentErrorsPopup { showErrors = false }
    Pane(Tab.About) {
        card("about", "Bloom") {
            StatRow("Version", "Bloom ${BuildConfig.VERSION_NAME}")
            StatRow("Server", server?.name ?: "—")
            StatRow("Address", server?.address ?: "—")
        }
        card("diag", "Diagnostics") {
            ActionRow("Statistics", "Frame speed, memory and what the app was busy with, to track down lag") { showStats = true }
            ActionRow("Recent errors", "Things that went wrong without crashing, like a row that didn't load. Take a photo to send") { showErrors = true }
            ToggleRow(
                "Show statistics on screen", onScreen,
                detail = "Keeps them in the corner while you browse, to catch lag as it happens",
                onToggle = { onScreen = !onScreen; store.showStats = onScreen; Perf.overlay = onScreen; Perf.enabled = onScreen },
            )
        }
        card("house", "Housekeeping", note = note) {
            ActionRow("Clear the picture cache", "Pictures are fetched again as you browse. Try this if artwork looks wrong or stale.") {
                scope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val loader = coil3.SingletonImageLoader.get(context)
                        loader.memoryCache?.clear()
                        loader.diskCache?.clear()
                    }
                    note = "Picture cache cleared."
                }
            }
            ActionRow("Clear search history", "Forgets the recent searches shown on the Search page.") {
                store.recentSearches = emptyList()
                note = "Search history cleared."
            }
            ActionRow("Put Home back to normal", "Switches every row back on, in the usual order, and clears pinned genres, row length and preview choices.") {
                store.hiddenHomeRows = emptySet()
                store.pinnedGenres = emptyList()
                store.genreRowCount = 8
                store.homeRowSize = 20
                store.previews = "on"
                store.rowsRememberPlace = true
                store.homeRowOrder = emptyList()
                store.tileOpen = null
                store.quickNav = true
                HomeCache.forget(app.repo)
                app.homeChanged = true
                note = "Home is back to normal. It rebuilds next time you open it."
            }
        }
    }
}

/** A little film-like frame showing the chosen subtitle look, drawn by the player's own subtitle code at the same proportions. */
@Composable
private fun SubtitlePreview(look: app.blush.tv.ui.player.SubtitleStyle) {
    Box(
        Modifier
            .padding(top = 6.dp)
            .width(288.dp)
            .height(162.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3B4A5C), Color(0xFF8A7A66), Color(0xFFE8E2D6), Color(0xFF26323F)))),
    ) {
        app.blush.tv.ui.player.SubtitleLayer(
            listOf(app.blush.tv.ui.player.SubLine(androidx.compose.ui.text.AnnotatedString("Do you like our owl?\nIt's artificial?"))),
            look,
        )
    }
}

/** The performance statistics, in a pop-up that looks like the rest of Settings. Measures only while it is open. */
@Composable
private fun StatisticsPopup(onDismiss: () -> Unit) {
    androidx.compose.runtime.DisposableEffect(Unit) {
        Perf.enabled = true
        onDispose { Perf.enabled = Perf.overlay }
    }
    app.blush.tv.ui.components.PopupPanel(onDismiss, "Statistics", "Live while this is open. Close it, use the app, and the numbers start over.", swallowHeldOk = false, width = 460.dp) {
        val text = app.blush.tv.ui.perfText()
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(text, style = BlushType.caption.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 9.5.sp, lineHeight = 12.5.sp), color = Blush.colors.textSoft)
        }
        val close = remember { FocusRequester() }
        LaunchedEffect(Unit) { repeat(15) { delay(40); if (runCatching { close.requestFocus() }.getOrDefault(false)) return@LaunchedEffect } }
        app.blush.tv.ui.components.PopupRow("Close", modifier = Modifier.focusRequester(close), onClick = onDismiss)
    }
}

/** Settings → About → Recent errors: what [ErrorLog] has kept, newest first, so it can be photographed and sent. */
@Composable
private fun RecentErrorsPopup(onDismiss: () -> Unit) {
    var entries by remember { mutableStateOf(ErrorLog.entries()) }
    val time = remember { java.text.SimpleDateFormat("d MMM HH:mm:ss", java.util.Locale.UK) }
    val body = if (entries.isEmpty()) "Nothing has gone wrong since this list was last cleared."
        else "${entries.size} kept, newest first. ${BuildConfig.VERSION_NAME}"
    app.blush.tv.ui.components.PopupPanel(onDismiss, "Recent errors", body, swallowHeldOk = false, width = 560.dp) {
        val close = app.blush.tv.ui.components.rememberFirstFocus()
        app.blush.tv.ui.components.PopupRow("Close", modifier = Modifier.focusRequester(close), onClick = onDismiss)
        if (entries.isNotEmpty()) {
            app.blush.tv.ui.components.PopupRow("Clear the list", "Start afresh before testing something") {
                ErrorLog.clear()
                entries = emptyList()
            }
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(entries.size) { i ->
                    val e = entries[i]
                    val count = if (e.count > 1) " · ${e.count} times, last ${time.format(java.util.Date(e.lastAt))}" else ""
                    FocusSurface(
                        onClick = {},
                        shape = RoundedCornerShape(12.dp),
                        focusedScale = 1f,
                        ringWidth = 0.dp,
                        background = Color.Transparent,
                        focusedBackground = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${e.where} · ${time.format(java.util.Date(e.firstAt))}$count", style = BlushType.caption.copy(fontWeight = FontWeight.W600), color = Blush.colors.textSoft)
                            Text(e.what, style = BlushType.caption, color = Blush.colors.text)
                            if (e.trace.isNotBlank()) Text(
                                e.trace,
                                style = BlushType.caption.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 9.sp, lineHeight = 11.5.sp),
                                color = Blush.colors.textDim,
                            )
                        }
                    }
                }
            }
        }
    }
}
