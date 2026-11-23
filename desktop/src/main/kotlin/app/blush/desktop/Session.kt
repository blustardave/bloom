package app.blush.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.blush.tv.data.Repo
import app.blush.tv.data.SavedServer
import app.blush.tv.data.SavedUser
import app.blush.tv.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

/** Where the Mac app is. Sign-in screens fill the window; the rest sit under the top bar. */
sealed interface Screen {
    data object Starting : Screen
    data object Server : Screen
    data object Profiles : Screen
    /** Typing a password for [profile], or (when null) a name and password for someone not listed. */
    data class Password(val profile: Profile?) : Screen
    data object Home : Screen
    data object Libraries : Screen
    data class Library(val library: BaseItemDto) : Screen
    /** A title's page; [preview] is what was already known about it (shown while the rest loads). */
    data class Details(val id: java.util.UUID, val preview: BaseItemDto? = null) : Screen
    data object Search : Screen
}

/** One person on "Who's watching?". [saved] is set when this Mac already holds their sign-in. */
data class Profile(val name: String, val userId: String, val imageUrl: String?, val saved: SavedUser?, val hasPassword: Boolean)

/** The Mac app's state: the shared [Repo], which screen is showing, and the way back. */
class Session(val scope: CoroutineScope) {
    private val prefs = DesktopPrefs()
    val store = Store(prefs)
    val repo: Repo

    init {
        // Jellyfin lists each signed-in device; this Mac keeps one id for good.
        val id = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() }
        val device = DeviceInfo(id = id, name = "Mac")
        val jellyfin = createJellyfin {
            clientInfo = ClientInfo("Bloom", bloomVersion)
            deviceInfo = device
        }
        repo = Repo(jellyfin, device, Places.cache, store)
    }

    var screen: Screen by mutableStateOf(Screen.Starting)
        private set

    /** Home's rows and banner, kept while browsing so coming back is instant; started afresh for each sign-in. */
    var home by mutableStateOf(HomeState())
        private set

    /** What was last typed on the Search page (kept while browsing). */
    var lastSearch = ""

    /** A short message at the bottom of the window (e.g. for things not built yet on the Mac). */
    var toastText by mutableStateOf<String?>(null)
        private set
    private var toastJob: kotlinx.coroutines.Job? = null

    fun toast(text: String) {
        toastText = text
        toastJob?.cancel()
        toastJob = scope.launch { kotlinx.coroutines.delay(2_800); toastText = null }
    }
    private val history = mutableStateListOf<Screen>()

    /** Opens [next]; Back returns here. */
    fun go(next: Screen) { history += screen; screen = next }

    /** Replaces the whole history with [next] (after signing in or out). */
    fun reset(next: Screen) {
        if (next == Screen.Home && (screen == Screen.Profiles || screen is Screen.Password || screen == Screen.Server || screen == Screen.Starting)) home = HomeState()
        history.clear(); screen = next
    }

    val canGoBack: Boolean get() = history.isNotEmpty()

    fun back() { if (history.isNotEmpty()) screen = history.removeAt(history.lastIndex) }

    /** On launch: straight to "Who's watching?" when a server is known, otherwise ask for one. */
    fun start() {
        val server = store.server
        if (server == null) { reset(Screen.Server); return }
        repo.connect(server)
        reset(Screen.Profiles)
    }

    // ---------- Server ----------

    suspend fun connect(address: String): SavedServer {
        val server = repo.connect(address)
        reset(Screen.Profiles)
        return server
    }

    fun useFound(server: SavedServer) { repo.connect(server); reset(Screen.Profiles) }

    fun changeServer() { repo.forgetServer(); reset(Screen.Server) }

    // ---------- Profiles ----------

    /** Everyone the server lists publicly, plus anyone signed in on this Mac whom the server doesn't list. */
    suspend fun profiles(): List<Profile> {
        val saved = repo.savedUsersForServer()
        val public = withTimeout(8_000) { repo.publicUsers() }
        val listed = public.map { u ->
            val id = u.id.toString()
            Profile(u.name ?: "User", id, repo.userImageUrl(id, u.primaryImageTag), saved.firstOrNull { it.userId == id }, u.hasPassword)
        }
        val extra = saved.filter { s -> listed.none { it.userId == s.userId } }
            .map { Profile(it.name, it.userId, repo.userImageUrl(it.userId, it.imageTag), it, true) }
        return (listed + extra).filterNot { repo.isProfileHidden(it.userId) }
    }

    /**
     * Clicking a profile: a remembered sign-in is checked and used; someone without a password is signed straight in;
     * otherwise the password screen opens. Returns a message to show when something went wrong.
     */
    suspend fun choose(p: Profile): String? {
        p.saved?.let { saved ->
            when (repo.validate(saved)) {
                true -> { reset(Screen.Home); return null }
                null -> return "Couldn't reach the server. Check it's on, then try again."
                false -> Unit // The sign-in has expired: ask for the password again.
            }
        }
        if (!p.hasPassword) {
            runCatching { repo.signIn(p.name, "") }.onSuccess { reset(Screen.Home); return null }
        }
        go(Screen.Password(p))
        return null
    }

    suspend fun signIn(name: String, password: String) {
        repo.signIn(name, password)
        reset(Screen.Home)
    }

    fun switchProfile() { reset(Screen.Profiles) }

    fun signOut() { repo.signOut(); reset(Screen.Profiles) }

    fun launch(block: suspend CoroutineScope.() -> Unit) = scope.launch(block = block)
}
