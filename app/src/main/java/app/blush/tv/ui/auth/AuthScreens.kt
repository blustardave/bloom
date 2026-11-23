package app.blush.tv.ui.auth

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.key
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.roundToInt
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.tween
import app.blush.tv.R
import kotlinx.coroutines.flow.first
import app.blush.tv.ui.components.ConfirmDialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blush.tv.data.ConnectException
import app.blush.tv.data.SavedServer
import app.blush.tv.data.SavedUser
import app.blush.tv.ui.AppState
import app.blush.tv.ui.Route
import app.blush.tv.ui.components.FocusSurface
import app.blush.tv.ui.components.Icon
import app.blush.tv.ui.components.Icons
import app.blush.tv.ui.components.PrimaryButton
import app.blush.tv.ui.components.RemoteImage
import app.blush.tv.ui.components.SecondaryButton
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.theme.Text
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    // The jellyfish mark and "bloom" (a group of jellyfish), drawn as one picture.
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(app.blush.tv.R.drawable.bloom_wordmark),
        contentDescription = "Bloom",
        modifier = modifier.height(34.dp),
    )
}

/**
 * The launch splash, laid over whatever screen is starting up underneath. The Bloom logo fades in at the middle and waits there
 * while the app looks for the server in the background. When the next screen is ready the jellyfish slides to the middle and
 * the word "bloom" slides left behind it (fading as it goes); then the whole splash fades away, which is the next screen's
 * elements fading in.
 */
/** Where the jellyfish ends in the logo picture (px of 560 x 160), plus a hair: the word is wiped from exactly here. */
private const val EDGE = 114f

@Composable
fun SplashOverlay(app: AppState) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val height = 96.dp
    // The logo picture is 560 x 160; one picture pixel on screen:
    val unit = with(density) { height.toPx() } / 160f
    val intro = remember { androidx.compose.animation.core.Animatable(0f) }
    val move = remember { androidx.compose.animation.core.Animatable(0f) }
    val out = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, tween(900, easing = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
        val began = System.currentTimeMillis()
        // The next screen says when it has what it needs (profiles loaded, or the server search over); never wait for ever.
        kotlinx.coroutines.withTimeoutOrNull(12_000) { androidx.compose.runtime.snapshotFlow { app.splashReady }.first { it } }
        // Not a flash if everything was instant: the logo gets a short moment of its own.
        delay((250 - (System.currentTimeMillis() - began)).coerceAtLeast(0))
        move.animateTo(1f, tween(780, easing = androidx.compose.animation.core.CubicBezierEasing(0.7f, 0f, 0.2f, 1f)))
        delay(30)
        out.animateTo(0f, tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        app.splashDone = true
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = out.value
                val grow = 1f + 0.05f * (1f - out.value)
                scaleX = grow; scaleY = grow
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            }
            .background(Blush.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        // The artwork inside the picture sits left of its middle, so the pair is first shifted to be centred as a whole.
        val centre = 53.5f * unit
        // The jellyfish carries an invisible square (its right edge just past the jellyfish itself) that wipes the word away as it goes by:
        // nothing fades, the letters are simply covered, from the left, as the jellyfish and the word cross.
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.bloom_word),
            contentDescription = null,
            modifier = Modifier
                .height(height)
                .graphicsLayer {
                    translationX = centre - 170f * unit * move.value
                    // The word settles in a touch later than the jellyfish, rising very slightly.
                    alpha = ((intro.value - 0.25f) / 0.75f).coerceIn(0f, 1f)
                    translationY = (1f - alpha) * 6f * unit
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // A disc rides along with the jellyfish, and everything the jellyfish has passed is covered too, so the word is
                    // wiped from the left by a round front instead of a square one. (Pre-slide picture coordinates.)
                    val cx = (62f + 335f * move.value) * unit
                    val cy = 80f * unit
                    drawRect(
                        Color.Black, androidx.compose.ui.geometry.Offset.Zero,
                        androidx.compose.ui.geometry.Size(cx.coerceIn(0f, size.width), size.height),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
                    )
                    drawCircle(Color.Black, 50f * unit, androidx.compose.ui.geometry.Offset(cx, cy), blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
                },
        )
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.bloom_mark),
            contentDescription = "Bloom",
            modifier = Modifier.height(height).graphicsLayer {
                // Ends with the jellyfish exactly in the middle of the screen.
                translationX = centre + 165f * unit * move.value
                alpha = intro.value
                val pop = 0.9f + 0.1f * intro.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(62f / 560f, 0.5f)
                scaleX = pop; scaleY = pop
            },
        )
    }
}

/** Decides where to go on launch: pick a server, or straight to "Who's watching?". */
@Composable
fun StartupScreen(app: AppState) {
    LaunchedEffect(Unit) {
        val server = app.repo.store.server
        if (server == null) {
            app.replaceAll(Route.Server)
        } else {
            app.repo.useServer(server)
            app.replaceAll(Route.Profiles)
        }
    }
    // Nothing to show of its own: the splash (see SplashOverlay) is laid over this while the next screen gets ready.
    Box(Modifier.fillMaxSize())
}

/** Width of the sign-in and server forms. */
private val FormWidth = 340.dp

/**
 * The quiet backdrop for the sign-in pages: a soft accent glow in one corner and the wordmark top-left,
 * with the form centred.
 */
@Composable
private fun AuthFrame(app: AppState, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Blush.colors.background)
            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(app.accent.color.copy(alpha = 0.10f), Color.Transparent), radius = 900f, center = androidx.compose.ui.geometry.Offset(0f, 0f))),
    ) {
        Wordmark(Modifier.padding(start = 42.dp, top = 30.dp))
        Box(Modifier.align(Alignment.Center)) { content() }
    }
}

/**
 * A text box that only opens the keyboard when you press OK on it (just landing on it with the remote does nothing).
 * The keyboard's Next / Done button closes it and moves the remote to [next] (or runs [onDone]); Back closes it and stays here.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: () -> Unit = {},
    next: FocusRequester? = null,
) {
    var hasFocus by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val own = remember { FocusRequester() }
    val inner = remember { FocusRequester() }
    var innerHadFocus by remember { mutableStateOf(false) }
    var text by remember(editing) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value, androidx.compose.ui.text.TextRange(value.length))) }

    fun finish(advance: Boolean) {
        keyboard?.hide()
        editing = false
        innerHadFocus = false
        if (advance && next != null) runCatching { next.requestFocus() }
        else {
            runCatching { own.requestFocus() }
            if (advance) onDone()
        }
    }
    androidx.activity.compose.BackHandler(enabled = editing) { finish(false) }
    LaunchedEffect(editing) {
        if (!editing) return@LaunchedEffect
        delay(40)
        runCatching { inner.requestFocus() }
        keyboard?.show()
    }
    val action: KeyboardActionScope.() -> Unit = { finish(true) }
    val shape = RoundedCornerShape(11.dp)
    val textStyle = BlushType.title.copy(color = Blush.colors.text, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.W500)
    Box(
        modifier = modifier
            .width(FormWidth)
            .height(42.dp)
            .onFocusChanged { hasFocus = it.hasFocus }
            .clip(shape)
            .background(Color.White.copy(alpha = if (hasFocus) 0.16f else 0.07f))
            .then(if (hasFocus) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.85f), shape) else Modifier)
            .focusRequester(own)
            .onKeyEvent { e ->
                val ok = e.key == androidx.compose.ui.input.key.Key.DirectionCenter || e.key == androidx.compose.ui.input.key.Key.Enter || e.key == androidx.compose.ui.input.key.Key.NumPadEnter
                if (!editing && ok && e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) { editing = true; true } else false
            }
            .focusable()
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (editing) {
            BasicTextField(
                value = text,
                onValueChange = { text = it; onValueChange(it.text) },
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(Blush.colors.accent.color),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = if (next != null) ImeAction.Next else ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = action, onNext = action, onGo = action, onSend = action, onSearch = action),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(inner)
                    // If the box loses the remote some other way, editing is over.
                    .onFocusChanged {
                        if (it.isFocused) innerHadFocus = true
                        else if (innerHadFocus) { innerHadFocus = false; editing = false }
                    },
            )
        } else if (value.isEmpty()) {
            Text(placeholder, style = textStyle, color = Blush.colors.textDim, maxLines = 1)
        } else {
            Text(if (password) "•".repeat(value.length) else value, style = textStyle, maxLines = 1)
        }
    }
}

@Composable
fun ServerScreen(app: AppState) {
    val isEmulator = remember {
        Build.FINGERPRINT.contains("generic") || Build.HARDWARE.contains("ranchu") ||
            Build.HARDWARE.contains("goldfish") || Build.PRODUCT.contains("sdk")
    }
    var address by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<SavedServer>>(emptyList()) }
    var searching by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Once you've typed an address, don't pull focus away to a server that turns up later.
    var typed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fieldFocus = remember { FocusRequester() }
    val connectFocus = remember { FocusRequester() }
    val firstFound = remember { FocusRequester() }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(refresh) {
        // (A refresh keeps the remote where it is, on the Refresh button.)
        if (refresh == 0) runCatching { if (address.isNotEmpty()) connectFocus.requestFocus() else fieldFocus.requestFocus() }
        else { found = emptyList(); searching = true }
        coroutineScope {
            fun addFound(server: SavedServer) {
                if (found.none { it.id == server.id }) found = found + server
            }
            if (isEmulator) {
                launch { runCatching { withTimeout(6000) { app.repo.probe("10.0.2.2:8096") } }.onSuccess { addFound(it) } }
            }
            // Jellyfin answers a broadcast on the local network, so most servers show up on their own.
            launch {
                runCatching {
                    app.repo.discoverLocalServers().collect { info ->
                        launch { runCatching { withTimeout(6000) { app.repo.probe(info.address) } }.onSuccess { addFound(it) } }
                    }
                }
            }
        }
        searching = false
    }
    // Tells the splash this screen is ready to be shown.
    LaunchedEffect(searching, found.size) { if (!searching || found.isNotEmpty()) app.splashReady = true }
    LaunchedEffect(found.size) {
        if (found.size == 1 && !typed) {
            delay(50)
            runCatching { firstFound.requestFocus() }
        }
    }

    fun connect() {
        if (busy || address.isBlank()) return
        busy = true
        error = null
        scope.launch {
            runCatching { withTimeout(20_000) { app.repo.connect(address.trim()) } }
                .onSuccess { app.replaceAll(Route.Profiles) }
                .onFailure {
                    error = when (it) {
                        is kotlinx.coroutines.TimeoutCancellationException -> "The server took too long to answer. Check the address and that it's on."
                        is ConnectException -> it.message
                        else -> "Couldn't connect: " + generateSequence(it) { e -> e.cause }.take(3).joinToString(" ← ") { e -> e.toString() }
                    }
                }
            busy = false
        }
    }

    AuthFrame(app) {
        Column(Modifier.width(FormWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Connect to your server", style = BlushType.headline)
            Text(
                when {
                    found.isNotEmpty() -> "Found on your network. Pick one, or type an address."
                    searching -> "Looking for Jellyfin on your network…"
                    else -> "Couldn't find one automatically. Type its address below."
                },
                style = BlushType.body,
                color = Blush.colors.textMuted,
            )
            Spacer(Modifier.height(4.dp))
            found.forEachIndexed { i, server ->
                FocusSurface(
                    onClick = {
                        app.repo.connect(server)
                        app.replaceAll(Route.Profiles)
                    },
                    shape = RoundedCornerShape(11.dp),
                    focusedScale = 1.02f,
                    ringWidth = 0.dp,
                    background = Color.White.copy(alpha = 0.07f),
                    focusedBackground = Color.White.copy(alpha = 0.18f),
                    modifier = Modifier.width(FormWidth).height(52.dp).let { if (i == 0) it.focusRequester(firstFound) else it },
                ) {
                    Row(
                        Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Server, null, Blush.colors.accent.color, size = 16.dp)
                        Column {
                            Text(server.name, style = BlushType.label, maxLines = 1)
                            Text(server.address.removePrefix("http://").removePrefix("https://"), style = BlushType.caption, color = Blush.colors.textMuted, maxLines = 1)
                        }
                    }
                }
            }
            if (found.isNotEmpty()) Spacer(Modifier.height(4.dp))
            TvTextField(
                value = address,
                onValueChange = { address = it; error = null; typed = true },
                placeholder = "Server address, like 192.168.1.20:8096",
                keyboardType = KeyboardType.Uri,
                next = connectFocus,
                // Down always goes to Connect.
                modifier = Modifier.focusRequester(fieldFocus).focusProperties { down = connectFocus },
            )
            PrimaryButton(if (busy) "Connecting…" else "Connect", null, onClick = { connect() }, modifier = Modifier.fillMaxWidth().focusRequester(connectFocus))
            if (error != null) Text(error!!, style = BlushType.body, color = Color(0xFFF08A7E))
            SecondaryButton(if (searching) "Searching…" else "Refresh", null, onClick = { if (!searching) refresh += 1 }, pill = true, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

private data class Profile(val name: String, val userId: String, val imageTag: String?, val saved: SavedUser?, val hasPassword: Boolean, val slot: Int = 0)

private val TileWidth = 108.dp
private val TileGap = 26.dp

@Composable
fun ProfilesScreen(app: AppState) {
    var profiles by remember { mutableStateOf<List<Profile>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Profile?>(null) }
    /** Profiles whose tile is closing up (the list drops them once that has finished). */
    var leaving by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val addFocus = remember { FocusRequester() }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val serverName = app.repo.store.server?.name ?: "your server"
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        error = null
        profiles = null
        val saved = app.repo.savedUsersForServer()
        val public = runCatching { withTimeout(8_000) { app.repo.publicUsers() } }.onFailure { error = "Couldn't reach $serverName." }.getOrDefault(emptyList())
        // At launch, a server that can't be found goes straight to "Connect to your server" (the saved server is kept).
        if (error != null && !app.splashDone) { app.replaceAll(Route.Server); return@LaunchedEffect }
        val fromServer = public.map { u ->
            Profile(u.name ?: "User", u.id.toString(), u.primaryImageTag, saved.firstOrNull { it.userId == u.id.toString() }, u.hasPassword)
        }
        val extra = saved.filter { s -> fromServer.none { it.userId == s.userId } }.map { Profile(it.name, it.userId, it.imageTag, it, true) }
        // Profiles removed from this TV stay off the list.
        // Each keeps the colour it was given when the list was built, so removing one never recolours the rest.
        profiles = (fromServer + extra).filterNot { app.repo.isProfileHidden(it.userId) }.mapIndexed { i, p -> p.copy(slot = i) }
    }
    // Tells the splash this screen is ready to be shown (profiles in, or it has failed and says so).
    LaunchedEffect(profiles != null, error) { if (profiles != null || error != null) app.splashReady = true }
    // Focus goes to the first profile once, when the list first appears (not again when one is removed).
    LaunchedEffect(profiles != null) { if (profiles != null) runCatching { firstFocus.requestFocus() } }

    fun choose(p: Profile) {
        if (busy) return
        error = null
        val saved = p.saved
        if (saved != null) {
            busy = true
            scope.launch {
                when (app.repo.validate(saved)) {
                    true -> app.signedIn(saved)
                    false -> app.go(Route.SignIn(p.name, p.userId, p.imageTag))
                    null -> error = "Couldn't reach $serverName. Check it's on, then try again."
                }
                busy = false
            }
        } else if (!p.hasPassword) {
            busy = true
            scope.launch {
                runCatching { app.repo.signIn(p.name, "") }
                    .onSuccess { app.signedIn(it) }
                    .onFailure { app.go(Route.SignIn(p.name, p.userId, p.imageTag)) }
                busy = false
            }
        } else {
            app.go(Route.SignIn(p.name, p.userId, p.imageTag))
        }
    }

    /** Takes a profile off this TV. The remote moves to its neighbour first, while it is still there, so it is never lost. */
    fun remove(p: Profile) {
        val list = profiles ?: return
        val at = list.indexOfFirst { it.userId == p.userId }
        if (at < 0) return
        val neighbour = list.getOrNull(at + 1) ?: list.getOrNull(at - 1)
        scope.launch {
            runCatching { (neighbour?.let { requesters[it.userId] } ?: addFocus).requestFocus() }
            delay(80)
            app.repo.removeProfile(p.userId)
            leaving = leaving + p.userId
        }
    }

    removing?.let { target ->
        ConfirmDialog(
            title = "Remove ${target.name}?",
            body = "They'll be taken off this screen and their saved sign-in on this TV is forgotten. Nothing changes on your server, and you can add them back with Other user.",
            confirmLabel = "Remove",
            onDismiss = { removing = null },
            onConfirm = { removing = null; remove(target) },
        )
    }

    Box(Modifier.fillMaxSize()) {
        AuthFrame(app) {}
        val list = profiles
        if (list != null) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Who's watching?", style = BlushType.headline.copy(fontSize = 24.sp))
            Text("Profiles on $serverName", style = BlushType.body, color = Blush.colors.textMuted, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(34.dp))
            // A plain centred row. Each tile owns a slot (its width plus the gap, with the tile centred in it). A leaving tile's
            // slot narrows to nothing while the tile fades, so the others slide together to re-centre in one smooth movement.
            Row(
                Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 40.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                list.forEach { p ->
                    key(p.userId) {
                        val requester = requesters.getOrPut(p.userId) { FocusRequester() }
                        val closing = p.userId in leaving
                        val open = remember { androidx.compose.animation.core.Animatable(1f) }
                        LaunchedEffect(closing) {
                            if (!closing) return@LaunchedEffect
                            open.animateTo(0f, tween(360, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                            profiles = profiles?.filterNot { it.userId == p.userId }
                            leaving = leaving - p.userId
                        }
                        ProfileTile(
                            name = p.name,
                            imageUrl = app.repo.userImageUrl(p.userId, p.imageTag),
                            color = profileColors[p.slot % profileColors.size].takeIf { p.slot > 0 } ?: app.accent.color,
                            onClick = { choose(p) },
                            onLongPress = { removing = p },
                            open = { open.value },
                            modifier = Modifier.focusRequester(requester).let { if (p.slot == 0) it.focusRequester(firstFocus) else it },
                        )
                    }
                }
                ProfileTile(
                    name = "Other user",
                    imageUrl = null,
                    color = Color(0xFF31333E),
                    icon = true,
                    onClick = { app.go(Route.SignIn(null, null, null)) },
                    modifier = Modifier.focusRequester(addFocus).let { if (list.isEmpty()) it.focusRequester(firstFocus) else it },
                )
            }
            // As far below the profiles as the heading is above them.
            Spacer(Modifier.height(34.dp))
            SecondaryButton("Switch server", null, onClick = { app.changeServer() }, pill = true, modifier = Modifier.focusProperties { up = firstFocus })
        }
        // Problems live in their own spot at the bottom, so they never move the profiles.
        if (error != null) Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error!!, style = BlushType.body, color = Color(0xFFF08A7E))
            SecondaryButton("Try again", null, onClick = { attempt += 1 }, modifier = Modifier.padding(top = 10.dp), pill = true)
        }
    }
}

private val profileColors = listOf(Color(0xFFF4A6B8), Color(0xFF8FD3C7), Color(0xFFF2C38B), Color(0xFFA9B8FF), Color(0xFFF08A7E))

@Composable
private fun ProfileTile(
    name: String,
    imageUrl: String?,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    open: () -> Float = { 1f },
    icon: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val slot = with(androidx.compose.ui.platform.LocalDensity.current) { (TileWidth + TileGap).roundToPx() }
    Column(
        Modifier
            .layout { measurable, _ ->
                // The tile is always laid out at full size and centred in a slot that can narrow to nothing.
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixedWidth(TileWidth.roundToPx()))
                val width = (slot * open()).roundToInt()
                layout(width, placeable.height) { placeable.place((width - placeable.width) / 2, 0) }
            }
            .graphicsLayer { alpha = open() * open() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FocusSurface(
            onClick = onClick,
            onLongPress = onLongPress,
            shape = CircleShape,
            focusedScale = 1.08f,
            background = color,
            onFocused = null,
            modifier = modifier.size(92.dp).onFocusChanged { focused = it.hasFocus },
        ) {
            if (icon) {
                Icon(Icons.Plus, null, Blush.colors.text, Modifier.align(Alignment.Center), size = 30.dp)
            } else {
                Text(
                    name.take(1).uppercase(),
                    style = BlushType.display.copy(fontSize = 36.sp),
                    color = Color(0xFF2A121B),
                    modifier = Modifier.align(Alignment.Center),
                )
                RemoteImage(imageUrl, Modifier.fillMaxSize())
            }
        }
        Text(
            name,
            style = BlushType.title.copy(fontSize = 13.sp),
            color = if (focused) Blush.colors.text else Blush.colors.textMuted,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun SignInScreen(app: AppState, route: Route.SignIn) {
    var username by remember { mutableStateOf(route.username ?: "") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var quickCode by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val signInFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Quick Connect: show a code to approve from another device signed in to Jellyfin.
    LaunchedEffect(Unit) {
        while (isActive) {
            val started = runCatching { app.repo.startQuickConnect() }.getOrNull() ?: return@LaunchedEffect
            quickCode = started.first
            // Jellyfin's code lasts about ten minutes; ask for a fresh one a little before that.
            val until = System.currentTimeMillis() + 9 * 60_000L
            while (isActive && System.currentTimeMillis() < until) {
                delay(4000)
                val user = runCatching { app.repo.pollQuickConnect(started.second) }.getOrNull()
                if (user != null) {
                    app.signedIn(user)
                    return@LaunchedEffect
                }
            }
        }
    }

    fun submit() {
        if (busy || username.isBlank()) return
        busy = true
        error = null
        scope.launch {
            runCatching { withTimeout(15_000) { app.repo.signIn(username.trim(), password) } }
                .onSuccess { app.signedIn(it) }
                .onFailure {
                    // Only a reply from the server means the details were wrong; otherwise it was a connection problem.
                    val answered = generateSequence<Throwable>(it) { e -> e.cause }.any { e -> e.javaClass.simpleName == "InvalidStatusException" }
                    error = if (answered) "That username or password didn't work." else "Couldn't reach the server. Check it's on, then try again."
                }
            busy = false
        }
    }

    AuthFrame(app) {
        Column(Modifier.width(FormWidth)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (route.userId != null) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(app.accent.color), contentAlignment = Alignment.Center) {
                        Text(username.take(1).uppercase(), style = BlushType.title, color = app.accent.onColor)
                        RemoteImage(app.repo.userImageUrl(route.userId, route.imageTag), Modifier.fillMaxSize())
                    }
                }
                Text(if (route.username != null) "Hi ${route.username}" else "Sign in", style = BlushType.headline)
            }
            Spacer(Modifier.height(8.dp))
            Text("Enter your Jellyfin password to continue.".takeIf { route.username != null } ?: "Use your Jellyfin username and password.", style = BlushType.body, color = Blush.colors.textMuted)
            Spacer(Modifier.height(24.dp))
            // Each box needs OK pressed on it to type. The keyboard's Next goes username -> password -> Sign in.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (route.username == null) {
                    TvTextField(username, { username = it }, "Username", next = passwordFocus, modifier = Modifier.focusRequester(focus).focusProperties { down = passwordFocus })
                    TvTextField(password, { password = it }, "Password", password = true, next = signInFocus, modifier = Modifier.focusRequester(passwordFocus).focusProperties { down = signInFocus })
                } else {
                    TvTextField(password, { password = it }, "Password", password = true, next = signInFocus, modifier = Modifier.focusRequester(focus).focusProperties { down = signInFocus })
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Sign in", null, onClick = { submit() }, modifier = Modifier.fillMaxWidth().focusRequester(signInFocus))
            if (error != null) {
                Spacer(Modifier.height(14.dp))
                Text(error!!, style = BlushType.body, color = Color(0xFFF08A7E))
            }
            if (quickCode != null) {
                // Quick Connect, quietly underneath: approve this TV from a phone or computer instead of typing.
                Column(
                    Modifier
                        .padding(top = 24.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Or use Quick Connect", style = BlushType.label, modifier = Modifier.weight(1f))
                        Text(quickCode!!, style = BlushType.subhead.copy(letterSpacing = 4.sp), color = Blush.colors.accent.color)
                    }
                    Text(
                        "In Jellyfin on your phone or computer, open your profile, choose Quick Connect and enter this code.",
                        style = BlushType.caption,
                        color = Blush.colors.textMuted,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SecondaryButton("Back", null, onClick = { app.back() }, pill = true)
            }
        }
    }
}
