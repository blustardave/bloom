package app.blush.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.blush.tv.data.RailPin
import app.blush.tv.data.Repo
import app.blush.tv.data.SavedUser
import app.blush.tv.ui.home.HomeModel
import app.blush.tv.ui.theme.Accents
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

/** Screens the app can show. The side-bar tabs are the ones marked [Tab]. */
sealed interface Route {
    sealed interface Tab : Route

    data object Startup : Route
    data object Server : Route
    data object Profiles : Route
    data class SignIn(val username: String?, val userId: String?, val imageTag: String?) : Route
    data object Home : Tab
    data object Search : Tab
    data object Libraries : Tab
    data object Watchlist : Tab
    data object History : Tab
    data object Settings : Tab
    data class Library(val library: BaseItemDto, val viaRail: Boolean = false) : Route
    /** [preview]: what the previous screen already knows about the title, shown at once while the rest loads. */
    data class Details(val itemId: UUID, val preview: BaseItemDto? = null) : Route
    data class Person(val personId: UUID) : Route
    data class Category(val key: String) : Route
    data class Player(val item: BaseItemDto, val fromStart: Boolean = false) : Route
}


/** App-wide state: the screen stack, the signed-in user and the chosen accent. */
class AppState(val repo: Repo) {
    val stack = mutableStateListOf<Route>(Route.Startup)
    /** The launch splash: the screen behind it sets [splashReady] once it has what it needs; [splashDone] once it has faded away. */
    var splashReady by mutableStateOf(false)
    var splashDone by mutableStateOf(false)
    /** What was typed on the search screen, kept so coming back from a title doesn't lose it. */
    var searchQuery = ""
    /** The search result that was opened last (see the Search screen), so Back from it lands on that tile. */
    var searchReturn: String? = null
    /** Each library's sort and filters, kept so coming back from a title shows the library exactly as you left it. */
    val libraryFilters = mutableMapOf<String, Any>()
    /** Where each grid was scrolled to and which tile had the remote, so coming back from a title lands in the same place. */
    val gridMemory = mutableMapOf<String, GridMemory>()
    /** The titles a grid has loaded, kept while its page is on the way back to (lists in a shuffled order can't be fetched the same twice). */
    val gridFeeds = mutableMapOf<String, Any>()
    val current: Route get() = stack.last()

    var accent by mutableStateOf(Accents.byId(repo.store.accentId))
        private set
    var backdrop by mutableStateOf(app.blush.tv.ui.theme.Backdrops.byId(repo.store.backdropId))
        private set

    fun setBackdrop(id: String) {
        repo.store.backdropId = id
        backdrop = app.blush.tv.ui.theme.Backdrops.byId(id)
    }

    var user by mutableStateOf<SavedUser?>(null)
        private set

    /** Full lists behind each home row's "View all", by row key. */
    val categories = mutableMapOf<String, CategorySpec>()

    /** Cached so returning to Home keeps your place and doesn't reload everything. */
    var home: HomeModel? = null
        private set

    /** The navigation bar's look (see Store.pillStyle), kept here so the bar changes the moment Settings does. */
    var pillStyle by mutableStateOf(repo.store.pillStyle)
        private set

    fun choosePillStyle(value: String) {
        repo.store.pillStyle = value
        pillStyle = value
    }

    /** Libraries pinned to the side bar (kept here too, so the bar updates the moment Settings changes them). */
    var railPins by mutableStateOf<List<RailPin>>(emptyList())
        private set

    fun updateRailPins(pins: List<RailPin>) {
        // At most four libraries in the side bar.
        val kept = pins.take(MaxRailPins)
        repo.store.setRailPins(repo.store.server?.id, user?.userId, kept)
        railPins = kept
    }

    /** Set when Home's rows were changed in Settings: Home is rebuilt next time it's opened. */
    var homeChanged = false

    fun setAccent(id: String) {
        repo.store.accentId = id
        accent = Accents.byId(id)
    }

    /** Where you were on a title's page (tab and tile), restored when you come back to it with Back. */
    val detailsMemory = mutableMapOf<UUID, DetailsMemory>()

    fun go(route: Route) {
        // A double press must not open the same page twice.
        if (stack.lastOrNull() == route) return
        // Opening a title afresh starts it afresh; only coming back to it (Back) restores where you were.
        if (route is Route.Details) detailsMemory.remove(route.itemId)
        stack.add(route)
    }

    /** Side-bar navigation: tabs replace each other rather than piling up. */
    fun openTab(tab: Route.Tab) {
        // Choosing a section from the side bar starts it afresh; only coming back from a title keeps the place.
        gridMemory.clear()
        gridFeeds.clear()
        detailsMemory.clear()
        libraryFilters.clear()
        searchReturn = null
        if (tab == Route.Home && homeChanged) {
            homeChanged = false
            home?.close(); home = HomeModel(repo, categories)
        }
        if (tab == Route.Home) home?.let { it.heroFocused = true; it.goToTop = true }
        // Whatever was open (a title, a library, another tab) is left behind: Back from a section never replays the pages you left by
        // choosing it. Home stays at the bottom, so Back from any section still ends on Home.
        if (stack.firstOrNull() == Route.Home) {
            if (stack.size > 1) stack.removeRange(1, stack.size)
            if (tab != Route.Home) stack.add(tab)
        } else {
            while (stack.size > 1 && stack.last() is Route.Tab && stack.last() != Route.Home) stack.removeAt(stack.lastIndex)
            if (stack.last() != tab) stack.add(tab)
        }
    }

    /** A library pinned to the side bar: opened like a section, leaving whatever was open behind. */
    fun openLibrary(library: BaseItemDto) {
        gridMemory.clear()
        gridFeeds.clear()
        detailsMemory.clear()
        libraryFilters.clear()
        searchReturn = null
        if (stack.firstOrNull() == Route.Home && stack.size > 1) stack.removeRange(1, stack.size)
        go(Route.Library(library, viaRail = true))
    }

    fun replaceAll(route: Route) {
        stack.clear()
        stack.add(route)
    }

    /** Returns false when there's nowhere to go back to, so the app can close. */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        val left = stack.removeAt(stack.lastIndex)
        // A page's kept list goes when the page does.
        if (left is Route.Category) gridFeeds.remove("category:${left.key}")
        if (left is Route.Person) gridMemory.remove("person:${left.personId}")
        if (left is Route.Library) gridFeeds.keys.removeAll { it.startsWith("lib:") }
        (stack.last() as? Route.Details)?.let { page ->
            // Back from the player lands on the page afresh (the remote on Play); back from another title or a person's page puts
            // the page back as it was left.
            if (left is Route.Player) detailsMemory.remove(page.itemId) else detailsMemory[page.itemId]?.armed = true
        }
        // Leaving Settings by Back lands on Home too, so changes made to Home's rows apply here as well.
        if (stack.last() == Route.Home && homeChanged) {
            homeChanged = false
            home?.close(); home = HomeModel(repo, categories)
        }
        return true
    }

    fun signedIn(saved: SavedUser) {
        user = saved
        railPins = repo.store.railPins(saved.serverId, saved.userId)

        home?.close(); home = HomeModel(repo, categories)
        replaceAll(Route.Home)
    }

    fun switchProfile() {
        user = null
        railPins = emptyList()
        home?.close(); home = null

        // These belong to the profile that just left.
        categories.clear()
        detailsMemory.clear()
        searchQuery = ""
        searchReturn = null
        libraryFilters.clear()
        gridMemory.clear()
        gridFeeds.clear()
        replaceAll(Route.Profiles)
    }

    fun signOut() {
        repo.signOut()
        switchProfile()
    }

    fun changeServer() {
        repo.forgetServer()
        user = null
        railPins = emptyList()
        home?.close(); home = null

        categories.clear()
        detailsMemory.clear()
        searchQuery = ""
        searchReturn = null
        libraryFilters.clear()
        gridMemory.clear()
        gridFeeds.clear()
        replaceAll(Route.Server)
    }
}

/** A grid's place: the first row showing, and the tile the remote was on. */
class GridMemory(var index: Int = 0, var offset: Int = 0, var focusedId: java.util.UUID? = null, var focusedPos: Int = 0)

class DetailsMemory {
    var tab: String? = null
    var lower: Boolean = false
    /** The tile or name the remote was on. */
    var focused: Any? = null
    /** Set when Back returns to the page; the page restores once, then clears it. */
    var armed: Boolean = false
    /** What to put the remote on as the page rebuilds. */
    var pending: Any? = null
}

/** How many libraries Settings lets you pin to the side bar. */
const val MaxRailPins = 4
