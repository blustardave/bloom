package app.blush.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blush.tv.data.SavedServer
import app.blush.tv.ui.theme.Blush
import kotlinx.coroutines.withTimeout

// Sign-in on the Mac: server → "Who's watching?" → password. Each screen is drawn from plain values (so the screenshot
// run can show it without a server); the *Route functions below fetch those values and act on clicks.

@Composable
private fun Wordmark(height: Int = 40) {
    val image = remember { resourceImage("bloom_wordmark.png") }
    Image(image, contentDescription = "Bloom", modifier = Modifier.height(height.dp).aspectRatio(430f / 112f))
}

/** A centred column for the sign-in screens. */
@Composable
private fun SignInPage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Blush.colors.background), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

// ---------- Server ----------

@Composable
fun ServerScreen(
    address: String,
    onAddress: (String) -> Unit,
    onConnect: () -> Unit,
    busy: Boolean,
    error: String?,
    found: List<SavedServer>,
    searching: Boolean,
    onPick: (SavedServer) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    SignInPage {
        Wordmark()
        Spacer(Modifier.height(36.dp))
        Text("Connect to your Jellyfin server", style = DesktopType.headline)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.widthIn(max = 460.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(address, onAddress, "Server address, like 192.168.1.20:8096", Modifier.weight(1f), focus = focus, onDone = onConnect)
            Spacer(Modifier.width(10.dp))
            PrimaryButton(if (busy) "Connecting…" else "Connect", onConnect, enabled = !busy && address.isNotBlank())
        }
        Note(error, Modifier.widthIn(max = 460.dp).height(26.dp), error = true)
        Spacer(Modifier.height(18.dp))
        Text(
            if (found.isEmpty()) (if (searching) "Looking for servers on your network…" else "No servers found on your network") else "On your network",
            style = DesktopType.caption,
            color = Blush.colors.textDim,
        )
        Spacer(Modifier.height(10.dp))
        Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            found.forEach { s -> FoundServer(s) { onPick(s) } }
        }
    }
}

@Composable
private fun FoundServer(server: SavedServer, onClick: () -> Unit) {
    val h = rememberHover()
    Row(
        Modifier.fillMaxWidth().probe(server.name).clip(RoundedCornerShape(12.dp))
            .background(if (h.lit) Blush.colors.surfaceRaised else Blush.colors.surface)
            .mouseTarget(h, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(server.name, style = DesktopType.title)
            Text(server.address, style = DesktopType.caption, color = Blush.colors.textMuted)
        }
        Text("Connect ›", style = DesktopType.label, color = if (h.lit) Color.White else Blush.colors.textDim)
    }
}

@Composable
fun ServerRoute(session: Session) {
    var address by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf(listOf<SavedServer>()) }
    var searching by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        // Jellyfin servers answer a broadcast on the local network, so most show up on their own.
        runCatching {
            withTimeout(8_000) {
                session.repo.discoverLocalServers().collect { info ->
                    runCatching { withTimeout(5_000) { session.repo.probe(info.address) } }.onSuccess { s ->
                        if (found.none { it.id == s.id }) found = found + s
                    }
                }
            }
        }
        searching = false
    }
    fun connect() {
        if (busy || address.isBlank()) return
        busy = true; error = null
        session.launch {
            runCatching { session.connect(address.trim()) }.onFailure { error = it.message ?: "Couldn't connect." }
            busy = false
        }
    }
    ServerScreen(address, { address = it; error = null }, ::connect, busy, error, found, searching, session::useFound)
}

// ---------- Who's watching? ----------

@Composable
fun ProfilesScreen(
    serverName: String,
    profiles: List<Profile>?,
    error: String?,
    busyId: String?,
    onPick: (Profile) -> Unit,
    onSomeoneElse: () -> Unit,
    onChangeServer: () -> Unit,
) {
    SignInPage {
        Text("Who's watching?", style = DesktopType.headline.copy(fontSize = DesktopType.headline.fontSize * 1.3f))
        Spacer(Modifier.height(6.dp))
        Text(serverName, style = DesktopType.label, color = Blush.colors.accent.color)
        Spacer(Modifier.height(34.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            profiles.orEmpty().forEach { p -> ProfileTile(p.name, p.imageUrl, busy = busyId == p.userId) { onPick(p) } }
            ProfileTile("Someone else", null, plus = true, onClick = onSomeoneElse)
        }
        Note(error ?: if (profiles == null) "Loading profiles…" else null, Modifier.height(30.dp).padding(top = 16.dp), error = error != null)
        Spacer(Modifier.height(20.dp))
        TextLink("Change server", onChangeServer)
    }
}

@Composable
private fun ProfileTile(name: String, imageUrl: String?, busy: Boolean = false, plus: Boolean = false, onClick: () -> Unit) {
    val h = rememberHover()
    Column(Modifier.width(112.dp).probe(name).mouseTarget(h, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(104.dp).then(if (h.lit) Modifier.border(3.dp, Color.White, CircleShape) else Modifier).padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (plus) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(if (h.lit) Blush.colors.surfaceRaised else Blush.colors.surface), contentAlignment = Alignment.Center) {
                    Text("+", style = DesktopType.headline.copy(fontSize = DesktopType.headline.fontSize * 1.6f), color = Blush.colors.textSoft)
                }
            } else {
                Avatar(name, imageUrl, 92.dp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (busy) "Signing in…" else name,
            style = DesktopType.label,
            color = if (h.lit) Color.White else Blush.colors.textSoft,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
fun ProfilesRoute(session: Session) {
    var profiles by remember { mutableStateOf<List<Profile>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { session.profiles() }
            .onSuccess { profiles = it }
            .onFailure { profiles = session.repo.savedUsersForServer().map { Profile(it.name, it.userId, null, it, true) }; error = "Couldn't reach ${session.store.server?.name ?: "the server"}." }
    }
    ProfilesScreen(
        serverName = session.store.server?.name ?: "",
        profiles = profiles,
        error = error,
        busyId = busyId,
        onPick = { p ->
            if (busyId == null) {
                busyId = p.userId; error = null
                session.launch {
                    error = runCatching { session.choose(p) }.getOrElse { it.message ?: "Couldn't sign in." }
                    busyId = null
                }
            }
        },
        onSomeoneElse = { session.go(Screen.Password(null)) },
        onChangeServer = session::changeServer,
    )
}

// ---------- Password ----------

@Composable
fun PasswordScreen(
    profile: Profile?,
    name: String,
    onName: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    busy: Boolean,
    error: String?,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
) {
    val nameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { (if (profile == null) nameFocus else passwordFocus).requestFocus() } }
    SignInPage {
        if (profile != null) {
            Avatar(profile.name, profile.imageUrl, 92.dp)
            Spacer(Modifier.height(14.dp))
            Text(profile.name, style = DesktopType.headline)
        } else {
            Text("Sign in", style = DesktopType.headline)
        }
        Spacer(Modifier.height(22.dp))
        Column(Modifier.widthIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (profile == null) Field(name, onName, "Name", focus = nameFocus, onDone = { runCatching { passwordFocus.requestFocus() } })
            Field(password, onPassword, "Password", password = true, focus = passwordFocus, onDone = onSignIn)
            Spacer(Modifier.height(4.dp))
            PrimaryButton(if (busy) "Signing in…" else "Sign in", onSignIn, Modifier.fillMaxWidth(), enabled = !busy && name.isNotBlank())
        }
        Note(error, Modifier.widthIn(max = 340.dp).height(28.dp), error = true)
        Spacer(Modifier.height(10.dp))
        TextLink("‹ Back", onBack)
    }
}

@Composable
fun PasswordRoute(session: Session, profile: Profile?) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun signIn() {
        if (busy || name.isBlank()) return
        busy = true; error = null
        session.launch {
            runCatching { session.signIn(name.trim(), password) }.onFailure {
                // The SDK says "401" for a wrong name or password; anything else is a connection problem.
                error = if (generateSequence(it) { e -> e.cause }.any { e -> e.javaClass.simpleName == "InvalidStatusException" }) "That name or password wasn't right."
                else "Couldn't reach the server."
            }
            busy = false
        }
    }
    PasswordScreen(profile, name, { name = it; error = null }, password, { password = it; error = null }, busy, error, ::signIn, session::back)
}
