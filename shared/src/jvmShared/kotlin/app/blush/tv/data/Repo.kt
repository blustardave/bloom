package app.blush.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.authenticateWithQuickConnect
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.client.extensions.personsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.studiosApi
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.discovery.RecommendedServerIssue
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.UserDto
import java.security.MessageDigest
import java.util.UUID

class ConnectException(message: String) : Exception(message)

/** Everything the app asks of the Jellyfin server goes through here. */
/** The width every poster tile asks the server for: a little over the widest a poster is drawn (about 300 pixels on a 1080p screen). */
const val PosterTileWidth = 280

/**
 * Shared by the TV app and the Mac app. Each app makes its own [jellyfin] (the SDK is set up differently on Android) and
 * says what device this is ([baseDevice]) and where cached files go ([cacheDir]).
 */
class Repo(private val jellyfin: Jellyfin, private val baseDevice: DeviceInfo, val cacheDir: java.io.File, val store: Store) {

    /** The API client for the signed-in user (or an anonymous one before sign-in). */
    var api: ApiClient = jellyfin.createApi()
        private set

    var userId: UUID? = null
        private set

    var currentUser: SavedUser? = null
        private set

    private fun deviceFor(user: String) = baseDevice.copy(
        id = MessageDigest.getInstance("SHA-1").run {
            update("${baseDevice.id}+$user".toByteArray())
            digest().joinToString("") { "%02x".format(it) }
        },
    )

    // ---------- Server & sign-in ----------

    /** Finds the Jellyfin server at [input] without saving anything. */
    suspend fun probe(input: String): SavedServer = withContext(Dispatchers.IO) {
        val servers = jellyfin.discovery.getRecommendedServers(input)
        val best = servers.firstOrNull { it.score == RecommendedServerInfoScore.GREAT }
            ?: servers.firstOrNull { it.score == RecommendedServerInfoScore.GOOD }
            ?: servers.firstOrNull { it.systemInfo.isSuccess }
            ?: throw ConnectException(
                servers.flatMap { it.issues }.firstNotNullOfOrNull { issue ->
                    when (issue) {
                        is RecommendedServerIssue.ServerUnreachable -> "Nothing answered at that address. Check the IP and port, and that the server is on."
                        is RecommendedServerIssue.SecureConnectionFailed -> "The server's HTTPS certificate wasn't accepted. Try http:// instead."
                        is RecommendedServerIssue.InvalidProductName -> "Something answered, but it isn't a Jellyfin server."
                        else -> null
                    }
                } ?: "Couldn't reach a Jellyfin server at that address.",
            )
        best.issues.firstNotNullOfOrNull { it as? RecommendedServerIssue.UnsupportedServerVersion }?.let {
            throw ConnectException("Your server runs Jellyfin ${it.version}. Bloom needs 10.11 or newer.")
        }
        val info = best.systemInfo.getOrNull() ?: throw ConnectException("That doesn't look like a Jellyfin server.")
        SavedServer(
            id = info.id ?: best.address,
            name = info.serverName ?: "Jellyfin",
            address = best.address,
        )
    }

    suspend fun connect(input: String): SavedServer {
        val server = probe(input)
        store.server = server
        api = jellyfin.createApi(baseUrl = server.address)
        return server
    }

    fun connect(server: SavedServer) {
        store.server = server
        api = jellyfin.createApi(baseUrl = server.address)
    }

    /** Servers that answer Jellyfin's discovery broadcast on the local network. */
    fun discoverLocalServers() = jellyfin.discovery.discoverLocalServers()

    fun useServer(server: SavedServer) {
        api = jellyfin.createApi(baseUrl = server.address)
    }

    suspend fun publicUsers(): List<UserDto> = withContext(Dispatchers.IO) {
        val server = store.server ?: return@withContext emptyList()
        jellyfin.createApi(baseUrl = server.address).userApi.getPublicUsers().content
    }

    suspend fun signIn(username: String, password: String): SavedUser = withContext(Dispatchers.IO) {
        val server = requireNotNull(store.server)
        val client = jellyfin.createApi(baseUrl = server.address, deviceInfo = deviceFor(username))
        val result = client.userApi.authenticateUserByName(username, password).content
        finishSignIn(server, result.accessToken, result.user)
    }

    /** Starts Quick Connect and returns (code, secret). */
    suspend fun startQuickConnect(): Pair<String, String> = withContext(Dispatchers.IO) {
        val server = requireNotNull(store.server)
        val client = jellyfin.createApi(baseUrl = server.address, deviceInfo = deviceFor("quickconnect-${UUID.randomUUID()}"))
        quickConnectClient = client
        val result = client.quickConnectApi.initiateQuickConnect().content
        result.code to result.secret
    }

    private var quickConnectClient: ApiClient? = null

    /** Returns the signed-in user once the code has been approved, otherwise null. */
    suspend fun pollQuickConnect(secret: String): SavedUser? = withContext(Dispatchers.IO) {
        val server = requireNotNull(store.server)
        val client = quickConnectClient ?: return@withContext null
        val state = client.quickConnectApi.getQuickConnectState(secret).content
        if (!state.authenticated) return@withContext null
        val result = client.userApi.authenticateWithQuickConnect(secret).content
        finishSignIn(server, result.accessToken, result.user)
    }

    private fun finishSignIn(server: SavedServer, token: String?, user: UserDto?): SavedUser {
        requireNotNull(token) { "Sign-in failed" }
        requireNotNull(user) { "Sign-in failed" }
        val saved = SavedUser(
            serverId = server.id,
            userId = user.id.toString(),
            name = user.name ?: "User",
            token = token,
            imageTag = user.primaryImageTag,
        )
        store.saveUser(saved)
        store.unhideUser(server.id, saved.userId)
        activate(saved)
        return saved
    }

    fun activate(user: SavedUser) {
        val server = requireNotNull(store.server)
        // What the library shows depends on who's signed in, so the actors/studios index starts over for a new user.
        if (currentUser?.userId != user.userId) { creditIndex = null; statsCache = null; similarCache.clear(); tagTables.clear(); nameIndex = null }
        librariesCache = null
        genresCache = null
        api = jellyfin.createApi(baseUrl = server.address, accessToken = user.token, deviceInfo = deviceFor(user.userId))
        userId = UUID.fromString(user.userId)
        currentUser = user
    }

    /**
     * Checks a remembered sign-in is still valid: true = fine, false = the server answered and turned it down
     * (e.g. the token expired), null = the server couldn't be reached in time (nothing is known about the login).
     */
    suspend fun validate(user: SavedUser): Boolean? = withContext(Dispatchers.IO) {
        activate(user)
        val result = runCatching { kotlinx.coroutines.withTimeout(12_000) { api.userApi.getCurrentUser() } }
        if (result.isSuccess) return@withContext true
        val error = result.exceptionOrNull()
        // The SDK reports an HTTP error status as InvalidStatusException; anything else is a connection problem.
        val answered = generateSequence(error) { it.cause }.any { it.javaClass.simpleName == "InvalidStatusException" }
        if (answered) false else null
    }

    fun signOut() {
        val server = store.server
        val user = currentUser
        if (server != null && user != null) store.removeUser(server.id, user.userId)
        currentUser = null
        userId = null
        if (server != null) useServer(server)
    }

    fun forgetServer() {
        librariesCache = null
        genresCache = null
        creditIndex = null
        statsCache = null
        similarCache.clear()
        nameIndex = null
        store.server = null
        currentUser = null
        userId = null
        api = jellyfin.createApi()
    }

    /** Takes a profile off this TV: its saved sign-in is forgotten and it is no longer listed. Nothing changes on the server. */
    fun removeProfile(userId: String) {
        val server = store.server ?: return
        store.removeUser(server.id, userId)
        store.hideUser(server.id, userId)
    }

    fun isProfileHidden(userId: String): Boolean {
        val server = store.server ?: return false
        return store.isHidden(server.id, userId)
    }

    fun savedUsersForServer(): List<SavedUser> {
        val server = store.server ?: return emptyList()
        return store.users.filter { it.serverId == server.id }
    }

    // ---------- Images ----------

    private var imageClient: Pair<String, ApiClient>? = null

    fun userImageUrl(userId: String, tag: String?): String? {
        tag ?: return null
        val server = store.server ?: return null
        // One lightweight client per server address, not one per call.
        val client = imageClient?.takeIf { it.first == server.address }?.second
            ?: jellyfin.createApi(baseUrl = server.address).also { imageClient = server.address to it }
        return client.imageApi.getUserImageUrl(userId = UUID.fromString(userId), tag = tag)
    }

    private fun image(id: UUID, type: ImageType, tag: String?, maxWidth: Int, index: Int? = null): String =
        api.imageApi.getItemImageUrl(itemId = id, imageType = type, tag = tag, maxWidth = maxWidth,
            // Small tile pictures don't show the difference; big ones (banners, backdrops, logos) keep the higher quality.
            quality = if (maxWidth <= 400) 82 else 90, imageIndex = index)

    /**
     * The sign-in, sent as a header with every video, subtitle and preview request. Jellyfin 12 no longer accepts it in the
     * address (the old `api_key=`), which is still added as well for servers older than that.
     */
    fun mediaHeaders(): Map<String, String> = api.accessToken?.let { mapOf("Authorization" to "MediaBrowser Token=\"$it\"") } ?: emptyMap()

    /** Portrait poster. Every poster tile asks for the same width ([PosterTileWidth]), so one download serves every screen. */
    fun posterUrl(item: BaseItemDto, maxWidth: Int = PosterTileWidth): String? = when {
        item.type == BaseItemKind.EPISODE && item.seriesId != null && item.seriesPrimaryImageTag != null ->
            image(item.seriesId!!, ImageType.PRIMARY, item.seriesPrimaryImageTag, maxWidth)
        item.imageTags?.get(ImageType.PRIMARY) != null -> image(item.id, ImageType.PRIMARY, item.imageTags?.get(ImageType.PRIMARY), maxWidth)
        else -> null
    }

    /** Wide backdrop for heroes and the details page. */
    fun backdropUrl(item: BaseItemDto, maxWidth: Int = 1920): String? = when {
        !item.backdropImageTags.isNullOrEmpty() -> image(item.id, ImageType.BACKDROP, item.backdropImageTags!!.first(), maxWidth, 0)
        item.parentBackdropItemId != null && !item.parentBackdropImageTags.isNullOrEmpty() ->
            image(item.parentBackdropItemId!!, ImageType.BACKDROP, item.parentBackdropImageTags!!.first(), maxWidth, 0)
        else -> null
    }

    /** 16:9 image for landscape tiles (episodes use their own still). */
    fun thumbUrl(item: BaseItemDto, maxWidth: Int = 480): String? = when {
        item.type == BaseItemKind.EPISODE && item.imageTags?.get(ImageType.PRIMARY) != null ->
            image(item.id, ImageType.PRIMARY, item.imageTags?.get(ImageType.PRIMARY), maxWidth)
        item.imageTags?.get(ImageType.THUMB) != null -> image(item.id, ImageType.THUMB, item.imageTags?.get(ImageType.THUMB), maxWidth)
        item.parentThumbItemId != null && item.parentThumbImageTag != null ->
            image(item.parentThumbItemId!!, ImageType.THUMB, item.parentThumbImageTag, maxWidth)
        else -> backdropUrl(item, maxWidth) ?: (if (item.type == BaseItemKind.COLLECTION_FOLDER || item.type == BaseItemKind.USER_VIEW) posterUrl(item, maxWidth) else null)
    }

    fun logoUrl(item: BaseItemDto, maxWidth: Int = 800): String? = when {
        item.imageTags?.get(ImageType.LOGO) != null -> image(item.id, ImageType.LOGO, item.imageTags?.get(ImageType.LOGO), maxWidth)
        item.parentLogoItemId != null && item.parentLogoImageTag != null -> image(item.parentLogoItemId!!, ImageType.LOGO, item.parentLogoImageTag, maxWidth)
        else -> null
    }

    // ---------- Browsing ----------

    private val cardFields = listOf(ItemFields.OVERVIEW, ItemFields.GENRES, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.CHILD_COUNT)

    // Libraries and genres hardly ever change, so they're kept for a few minutes instead of asked for on every use.
    private var librariesCache: Pair<Long, List<BaseItemDto>>? = null
    private var genresCache: Pair<Long, List<String>>? = null
    private val cacheMs = 5 * 60_000L

    suspend fun libraries(): List<BaseItemDto> = withContext(Dispatchers.IO) {
        librariesCache?.let { (at, list) -> if (System.currentTimeMillis() - at < cacheMs) return@withContext list }
        api.userViewsApi.getUserViews(userId = userId).content.items
            // Only libraries Bloom can show: music, books, photos and the like would open as an empty page.
            .filter { it.collectionType !in setOf(CollectionType.LIVETV, CollectionType.MUSIC, CollectionType.MUSICVIDEOS, CollectionType.BOOKS, CollectionType.PHOTOS, CollectionType.PLAYLISTS) }
            .also { librariesCache = System.currentTimeMillis() to it }
    }

    suspend fun randomMovie(): BaseItemDto? = withContext(Dispatchers.IO) {
        api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.MOVIE),
            recursive = true,
            sortBy = listOf(ItemSortBy.RANDOM),
            imageTypes = listOf(ImageType.BACKDROP),
            limit = 1,
            fields = cardFields,
            enableUserData = true,
        ).content.items.firstOrNull()
    }

    /** A film picked at random for "Surprise me": one you haven't watched if there is any, otherwise any film at all. */
    /** A random title for "Surprise me", within the chosen [o]ptions; null when nothing matches (or the server didn't answer). */
    suspend fun surprise(o: SurpriseOptions = store.surpriseOptions): BaseItemDto? = withContext(Dispatchers.IO) {
        val types = when (o.kind) {
            SurpriseOptions.SERIES -> listOf(BaseItemKind.SERIES)
            SurpriseOptions.EITHER -> listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
            else -> listOf(BaseItemKind.MOVIE)
        }
        val range = o.minutes
        api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = types,
            recursive = true,
            sortBy = listOf(ItemSortBy.RANDOM),
            isPlayed = if (o.includeWatched) null else false,
            genres = listOfNotNull(o.genre),
            minCommunityRating = o.minRating.takeIf { it > 0 }?.toDouble(),
            // The server can't filter by length: with a length chosen, a few are fetched and the first that fits is taken.
            limit = if (range == null) 1 else 40,
            fields = cardFields,
            enableUserData = true,
        ).content.items.firstOrNull { item ->
            if (range == null || item.type == BaseItemKind.SERIES) true
            else { val m = (item.runTimeTicks ?: 0L) / 600_000_000L; m > 0 && m in range }
        }
    }

    /** A handful of random films and series that have artwork, for the rotating banner. */
    suspend fun featured(count: Int = 5): List<BaseItemDto> = withContext(Dispatchers.IO) {
        api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
            recursive = true,
            sortBy = listOf(ItemSortBy.RANDOM),
            imageTypes = listOf(ImageType.BACKDROP),
            hasOverview = true,
            limit = count,
            fields = cardFields,
            enableUserData = true,
        ).content.items
    }

    /** Generic query for home rows. */
    suspend fun browse(
        types: List<BaseItemKind> = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
        sortBy: ItemSortBy = ItemSortBy.RANDOM,
        descending: Boolean = false,
        genre: String? = null,
        minRating: Double? = null,
        unplayedOnly: Boolean = false,
        maxRuntimeMinutes: Int? = null,
        years: List<Int> = emptyList(),
        personId: UUID? = null,
        studio: String? = null,
        parentId: UUID? = null,
        favouritesOnly: Boolean = false,
        start: Int = 0,
        limit: Int = 20,
    ): List<BaseItemDto> = browsePage(types, sortBy, descending, genre, minRating, unplayedOnly, maxRuntimeMinutes, years, personId, studio, parentId, favouritesOnly, start, limit).first

    /** Same as [browse] but also returns the total number of matches, for paging through a grid. */
    suspend fun browsePage(
        types: List<BaseItemKind> = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
        sortBy: ItemSortBy = ItemSortBy.RANDOM,
        descending: Boolean = false,
        genre: String? = null,
        minRating: Double? = null,
        unplayedOnly: Boolean = false,
        maxRuntimeMinutes: Int? = null,
        years: List<Int> = emptyList(),
        personId: UUID? = null,
        studio: String? = null,
        parentId: UUID? = null,
        favouritesOnly: Boolean = false,
        start: Int = 0,
        limit: Int = 20,
        playedOnly: Boolean = false,
        /** With [personId]: only titles where they have this role (e.g. directors). */
        personKind: PersonKind? = null,
        /** Several genres at once (all must match); [genre] is the single-genre form. */
        genres: List<String> = emptyList(),
    ): Pair<List<BaseItemDto>, Int> = withContext(Dispatchers.IO) {
        val result = api.itemsApi.getItems(
            userId = userId,
            parentId = parentId,
            includeItemTypes = types,
            recursive = true,
            sortBy = listOf(sortBy, ItemSortBy.SORT_NAME),
            // One direction per sort key: the chosen one, then the name tie-break always A to Z.
            sortOrder = listOf(if (descending) SortOrder.DESCENDING else SortOrder.ASCENDING, SortOrder.ASCENDING),
            genres = genres.ifEmpty { genre?.let { listOf(it) } ?: emptyList() },
            minCommunityRating = minRating,
            isPlayed = when {
                unplayedOnly -> false
                playedOnly -> true
                else -> null
            },
            isFavorite = if (favouritesOnly) true else null,
            years = years,
            personIds = personId?.let { listOf(it) } ?: emptyList(),
            personTypes = personKind?.let { listOf(it.serialName) } ?: emptyList(),
            studios = studio?.let { listOf(it) } ?: emptyList(),
            startIndex = start,
            limit = if (maxRuntimeMinutes != null) limit * 4 else limit,
            fields = cardFields,
            imageTypeLimit = 1,
            enableUserData = true,
        ).content
        val items = if (maxRuntimeMinutes == null) result.items
        // The server can't filter by length, so fetch extra and filter here.
        else result.items.filter { (it.runTimeTicks ?: Long.MAX_VALUE) <= maxRuntimeMinutes * 600_000_000L }.take(limit)
        items to result.totalRecordCount
    }

    /** One search section: a heading and its titles. */
    data class SearchSection(val title: String, val items: List<BaseItemDto>)

    /** Someone (or a studio) and how many titles in the library they're credited on. */
    data class Credit(val id: UUID?, val name: String, val count: Int, val imageTag: String?, val acted: Int = 0, val directed: Int = 0)

    /**
     * Everyone credited in the library, most-credited first, built once per session in the background.
     * This is what makes "B" suggest the actor you're most likely to mean rather than the first alphabetically.
     */
    private class CreditIndex(val people: List<Credit>, val studios: List<Credit>)

    private val indexLock = Mutex()
    private var creditIndex: CreditIndex? = null

    suspend fun warmSearch() {
        // The counting is real work, so keep it off the main thread.
        runCatching { withContext(Dispatchers.Default) { credits() } }
    }

    /** The index as it was last worked out for this profile, kept on the device for a day so it isn't rebuilt on every launch. */
    private fun creditFile(): java.io.File? {
        val server = store.server?.id ?: return null
        val user = userId ?: return null
        return java.io.File(cacheDir, "credits-$server-$user.tsv")
    }

    private fun readCredits(allowOld: Boolean = false): CreditIndex? = runCatching {
        val file = creditFile()?.takeIf { it.exists() && (allowOld || System.currentTimeMillis() - it.lastModified() < 24 * 3_600_000L) } ?: return null
        val people = ArrayList<Credit>()
        val studios = ArrayList<Credit>()
        file.forEachLine { line ->
            val f = line.split('\t')
            when {
                f[0] == "P" && f.size == 7 -> people += Credit(UUID.fromString(f[1]), f[2], f[3].toInt(), f[4].ifEmpty { null }, f[5].toInt(), f[6].toInt())
                f[0] == "S" && f.size == 3 -> studios += Credit(null, f[1], f[2].toInt(), null)
            }
        }
        CreditIndex(people, studios).takeIf { people.isNotEmpty() }
    }.getOrNull()

    private fun writeCredits(index: CreditIndex) {
        runCatching {
            val file = creditFile() ?: return
            fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
            val tmp = java.io.File(file.path + ".tmp")
            tmp.bufferedWriter().use { w ->
                index.people.forEach { c -> if (c.id != null) w.write("P\t${c.id}\t${clean(c.name)}\t${c.count}\t${clean(c.imageTag.orEmpty())}\t${c.acted}\t${c.directed}\n") }
                index.studios.forEach { c -> w.write("S\t${clean(c.name)}\t${c.count}\n") }
            }
            tmp.renameTo(file)
        }
    }

    private suspend fun credits(): CreditIndex = indexLock.withLock {
        creditIndex?.let { return@withLock it }
        readCredits()?.let { creditIndex = it; return@withLock it }
        // More than a day old: it is used as it is (nobody waits for the scan), and a new one is worked out behind it.
        readCredits(allowOld = true)?.let { old ->
            creditIndex = old
            val who = userId
            background.launch {
                runCatching { scanCredits() }.onSuccess { if (userId == who) { creditIndex = it; writeCredits(it) } }
            }
            return@withLock old
        }
        scanCredits().also { creditIndex = it; writeCredits(it) }
    }

    private val background = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    /** Reads every film and series once to count who appears in what. */
    private suspend fun scanCredits(): CreditIndex {
        val people = HashMap<UUID, Credit>()
        val studios = HashMap<String, Int>()
        var start = 0
        val page = 400
        while (true) {
            val result = api.itemsApi.getItems(
                userId = userId,
                recursive = true,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                fields = listOf(ItemFields.PEOPLE, ItemFields.STUDIOS),
                enableImages = false,
                enableUserData = false,
                startIndex = start,
                limit = page,
            ).content
            result.items.forEach { item ->
                // Each person counts once per title, whatever their roles in it.
                val crew = item.people.orEmpty().filter { it.type == PersonKind.ACTOR || it.type == PersonKind.DIRECTOR }
                crew.distinctBy { it.id }.forEach { p ->
                    val name = p.name ?: return@forEach
                    val old = people[p.id]
                    // Counted once per title, and separately as an actor and as a director (so "Starring" and "Directed by" rows
                    // each use the right people, rather than a director being "starring" in their own films).
                    val acts = crew.any { it.id == p.id && it.type == PersonKind.ACTOR }
                    val directs = crew.any { it.id == p.id && it.type == PersonKind.DIRECTOR }
                    people[p.id] = Credit(
                        p.id, name, (old?.count ?: 0) + 1, old?.imageTag ?: p.primaryImageTag,
                        (old?.acted ?: 0) + (if (acts) 1 else 0), (old?.directed ?: 0) + (if (directs) 1 else 0),
                    )
                }
                item.studios.orEmpty().mapNotNull { it.name }.distinct().forEach { studios[it] = (studios[it] ?: 0) + 1 }
            }
            start += result.items.size
            if (result.items.size < page) break
        }
        return CreditIndex(
            people = people.values.sortedByDescending { it.count },
            studios = studios.map { (name, n) -> Credit(null, name, n, null) }.sortedByDescending { it.count },
        )
    }


    /** The most-credited people and studios in the library, for rows like "Starring …" on Home. */
    suspend fun popularCredits(people: Int = 15, studios: Int = 8): Pair<List<Credit>, List<Credit>> = withContext(Dispatchers.IO) {
        val index = credits()
        // "Starring" is for people who are actually in the films, not directors (who are credited on them too).
        index.people.filter { it.acted >= 3 }.sortedByDescending { it.acted }.take(people) to index.studios.filter { it.count >= 3 }.take(studios)
    }

    /** Figures about the server and what has been watched, for Settings → Statistics. */
    class ServerStats(
        val movies: Int,
        val series: Int,
        val episodes: Int,
        val watchedTitles: Int,
        val watchedHours: Double,
        val longest: BaseItemDto?,
        val largest: BaseItemDto?,
        val largestBytes: Long,
        val actors: List<Credit>,
        val directors: List<Credit>,
        val studios: List<Credit>,
        val genres: List<Pair<String, Int>>,
    )

    private var statsCache: ServerStats? = null

    /**
     * Works the statistics out (a few quick counts, a look through every film for the longest and the largest file, and a sum of
     * everything played). It takes a while on a big library, so [progress] says what it is doing, and the result is kept for the session.
     */
    suspend fun serverStats(force: Boolean = false, progress: (String) -> Unit = {}): ServerStats = withContext(Dispatchers.IO) {
        if (!force) statsCache?.let { return@withContext it }
        suspend fun count(kind: BaseItemKind) = runCatching {
            api.itemsApi.getItems(userId = userId, recursive = true, includeItemTypes = listOf(kind), limit = 0, enableTotalRecordCount = true).content.totalRecordCount
        }.getOrDefault(0)
        progress("Counting titles…")
        val movies = count(BaseItemKind.MOVIE)
        val series = count(BaseItemKind.SERIES)
        val episodes = count(BaseItemKind.EPISODE)

        progress("Adding up what you've watched…")
        var ticks = 0.0
        var watched = 0
        var from = 0
        while (true) {
            val page = runCatching {
                api.itemsApi.getItems(
                    userId = userId, recursive = true, includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE), isPlayed = true,
                    enableImages = false, enableUserData = true, startIndex = from, limit = 1000,
                ).content.items
            }.getOrDefault(emptyList())
            page.forEach { item ->
                val plays = (item.userData?.playCount ?: 1).coerceAtLeast(1)
                ticks += (item.runTimeTicks ?: 0L).toDouble() * plays
            }
            watched += page.size
            from += page.size
            if (page.size < 1000) break
        }

        var longest: BaseItemDto? = null
        var largest: BaseItemDto? = null
        var largestBytes = 0L
        from = 0
        while (true) {
            progress("Looking through your films… $from of $movies")
            val page = runCatching {
                api.itemsApi.getItems(
                    userId = userId, recursive = true, includeItemTypes = listOf(BaseItemKind.MOVIE), fields = listOf(ItemFields.MEDIA_SOURCES),
                    enableImages = false, enableUserData = false, startIndex = from, limit = 200,
                ).content.items
            }.getOrDefault(emptyList())
            page.forEach { item ->
                if ((item.runTimeTicks ?: 0L) > (longest?.runTimeTicks ?: 0L)) longest = item
                val size = item.mediaSources?.maxOfOrNull { it.size ?: 0L } ?: 0L
                if (size > largestBytes) { largestBytes = size; largest = item }
            }
            from += page.size
            if (page.size < 200) break
        }

        progress("Finding the most-credited names…")
        val index = credits()
        progress("Counting genres…")
        val genreCounts = coroutineScope {
            val names = runCatching { genres() }.getOrDefault(emptyList())
            names.map { g -> async { g to runCatching { browsePage(genre = g, start = 0, limit = 1).second }.getOrDefault(0) } }.awaitAll()
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(8)

        ServerStats(
            movies, series, episodes, watched, ticks / 10_000_000.0 / 3600.0, longest, largest, largestBytes,
            index.people.filter { it.acted > 0 }.sortedByDescending { it.acted }.take(6),
            index.people.filter { it.directed > 0 }.sortedByDescending { it.directed }.take(6),
            index.studios.take(6),
            genreCounts,
        ).also { statsCache = it }
    }

    /** The directors with the most titles in the library, for "Directed by …" rows. */
    suspend fun popularDirectors(count: Int = 10): List<Credit> = withContext(Dispatchers.IO) {
        credits().people.filter { it.directed >= 3 }.sortedByDescending { it.directed }.take(count)
    }

    /** Does [name] match what's been typed so far? Either it starts with it, or one of its words does. */
    private fun matchTier(name: String, term: String): Int {
        val n = name.lowercase()
        val t = term.lowercase().trim()
        return when {
            n.startsWith(t) || n.removePrefix("the ").startsWith(t) -> 2
            n.split(' ', '-', ':', '.').any { it.startsWith(t) } || n.contains(" $t") -> 1
            n.contains(t) -> 0
            else -> -1
        }
    }

    /** The best-known matching people, for the search screen's "People" suggestions. */
    suspend fun searchPeople(term: String, limit: Int = 6): List<Credit> = withContext(Dispatchers.IO) {
        // Never wait for the index while typing: if it's still being built, use the server's own search for now.
        val index = creditIndex
        if (index != null) {
            index.people.asSequence()
                .map { it to matchTier(it.name, term) }
                .filter { it.second >= 1 }
                .sortedWith(compareByDescending<Pair<Credit, Int>> { it.first.count }.thenByDescending { it.second })
                .map { it.first }
                .take(limit)
                .toList()
        } else {
            // Index unavailable: fall back to the server's own (alphabetical) person search.
            runCatching { api.personsApi.getPersons(searchTerm = term, limit = limit, userId = userId).content.items }
                .getOrDefault(emptyList())
                .map { Credit(it.id, it.name.orEmpty(), 0, it.imageTags?.get(ImageType.PRIMARY)) }
        }
    }

    fun personImageUrl(credit: Credit, maxWidth: Int = 200): String? =
        credit.id?.let { id -> credit.imageTag?.let { image(id, ImageType.PRIMARY, it, maxWidth) } }

    /** Matching films, series and collections: names starting with the text first, then better-rated. */
    suspend fun searchTitles(term: String): List<BaseItemDto> = withContext(Dispatchers.IO) {
        runCatching { search(term) }.getOrDefault(emptyList())
            .map { it to matchTier(it.name.orEmpty(), term) }
            .sortedWith(compareByDescending<Pair<BaseItemDto, Int>> { it.second }.thenByDescending { it.first.communityRating ?: 0f })
            .map { it.first }
    }

    /**
     * Extra rows after the titles and the people: films from the biggest matching studio, and in a matching genre.
     * (A matching person has their own page, reached from the People row, so there is no row of their films here.)
     * Anything already in [exclude] is left out.
     */
    suspend fun searchExtras(term: String, exclude: Set<UUID>): List<SearchSection> = withContext(Dispatchers.IO) {
        kotlinx.coroutines.coroutineScope {
            val studio = creditIndex?.studios?.firstOrNull { matchTier(it.name, term) >= 1 }
            val genresJob = async {
                runCatching { api.genresApi.getGenres(searchTerm = term, limit = 1, userId = userId, enableTotalRecordCount = false).content.items }.getOrDefault(emptyList())
            }
            val seen = exclude.toMutableSet()
            val extra = buildList {
                studio?.let { st ->
                    add(async { "From ${st.name}" to runCatching { browse(studio = st.name, sortBy = ItemSortBy.COMMUNITY_RATING, descending = true, limit = 30) }.getOrDefault(emptyList()) })
                }
                genresJob.await().forEach { genre ->
                    add(async { genre.name.orEmpty() to runCatching { browse(genre = genre.name, sortBy = ItemSortBy.COMMUNITY_RATING, descending = true, limit = 30) }.getOrDefault(emptyList()) })
                }
            }.map { it.await() }
            extra.mapNotNull { (heading, items) ->
                val fresh = items.filter { seen.add(it.id) }
                if (fresh.isEmpty()) null else SearchSection(heading, fresh)
            }
        }
    }

    /** How many films/series a library holds. */
    suspend fun libraryCount(library: BaseItemDto): Int? = withContext(Dispatchers.IO) {
        runCatching {
            api.itemsApi.getItems(
                userId = userId,
                parentId = library.id,
                recursive = true,
                includeItemTypes = libraryTypes(library),
                limit = 0,
                enableTotalRecordCount = true,
            ).content.totalRecordCount
        }.getOrNull()
    }

    /** The kinds of item a library is browsed by. */
    fun libraryTypes(library: BaseItemDto): List<BaseItemKind> = when (library.collectionType) {
        CollectionType.MOVIES -> listOf(BaseItemKind.MOVIE)
        CollectionType.TVSHOWS -> listOf(BaseItemKind.SERIES)
        CollectionType.BOXSETS -> listOf(BaseItemKind.BOX_SET)
        else -> listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.VIDEO)
    }

    /** Films and series for one person, best-rated first (tapping a person on the search screen). */
    suspend fun creditsFor(person: Credit): List<BaseItemDto> {
        val id = person.id ?: return emptyList()
        return runCatching { browse(personId = id, sortBy = ItemSortBy.COMMUNITY_RATING, descending = true, limit = 60) }.getOrDefault(emptyList())
    }

    /** Something playable for a preview: a film itself, or a random episode of a series. */
    suspend fun previewSource(item: BaseItemDto): BaseItemDto? = withContext(Dispatchers.IO) {
        when (item.type) {
            BaseItemKind.MOVIE, BaseItemKind.EPISODE, BaseItemKind.VIDEO -> item
            // A series previews its very first episode (season 1, episode 1), so nothing is given away.
            BaseItemKind.SERIES -> api.itemsApi.getItems(
                userId = userId,
                parentId = item.id,
                recursive = true,
                includeItemTypes = listOf(BaseItemKind.EPISODE),
                sortBy = listOf(ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER),
                sortOrder = listOf(SortOrder.ASCENDING),
                limit = 6,
            ).content.items.firstOrNull { (it.parentIndexNumber ?: 1) >= 1 }
            // A collection previews one of its films.
            BaseItemKind.BOX_SET -> api.itemsApi.getItems(
                userId = userId,
                parentId = item.id,
                includeItemTypes = listOf(BaseItemKind.MOVIE),
                sortBy = listOf(ItemSortBy.RANDOM),
                limit = 1,
            ).content.items.firstOrNull()
            else -> null
        }
    }

    fun hasThumb(item: BaseItemDto) = item.imageTags?.get(ImageType.THUMB) != null || item.parentThumbImageTag != null

    /** Genre names that have at least a few titles. */
    suspend fun genres(parentId: UUID? = null): List<String> = withContext(Dispatchers.IO) {
        if (parentId == null) genresCache?.let { (at, list) -> if (System.currentTimeMillis() - at < cacheMs) return@withContext list }
        api.genresApi.getGenres(
            userId = userId,
            parentId = parentId,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
            enableTotalRecordCount = false,
        ).content.items.mapNotNull { it.name }.also { if (parentId == null) genresCache = System.currentTimeMillis() to it }
    }

    /** The film or series most recently watched, to base "Because you watched" on. */
    suspend fun lastWatched(): BaseItemDto? = withContext(Dispatchers.IO) {
        val recent = api.itemsApi.getItems(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
            recursive = true,
            isPlayed = true,
            sortBy = listOf(ItemSortBy.DATE_PLAYED),
            sortOrder = listOf(SortOrder.DESCENDING),
            limit = 1,
            fields = cardFields,
        ).content.items.firstOrNull() ?: return@withContext null
        if (recent.type == BaseItemKind.EPISODE && recent.seriesId != null) item(recent.seriesId!!) else recent
    }

    /**
     * Continue watching: one tile per film or series, in the order they were last watched. A series shows the episode you're
     * part-way through, or, once you've finished one, the next episode (Jellyfin's "Next up"), so a show you're watching doesn't
     * vanish when an episode ends. The order comes from the watch history (newest first), not from the resume list alone.
     */
    suspend fun resume(): List<BaseItemDto> = withContext(Dispatchers.IO) {
        kotlinx.coroutines.coroutineScope {
            val partialJob = async {
                api.itemsApi.getResumeItems(
                    userId = userId,
                    limit = 40,
                    fields = cardFields,
                    mediaTypes = listOf(org.jellyfin.sdk.model.api.MediaType.VIDEO),
                    enableUserData = true,
                ).content.items
            }
            val nextJob = async {
                runCatching {
                    api.tvShowsApi.getNextUp(userId = userId, limit = 60, fields = cardFields, enableUserData = true, enableResumable = false, enableRewatching = false).content.items
                }.getOrDefault(emptyList())
            }
            val historyJob = async { runCatching { history(0, 80).first }.getOrDefault(emptyList()) }
            val partial = partialJob.await().sortedByDescending { it.userData?.lastPlayedDate }
            val hidden = store.hiddenNextUp
            val next = nextJob.await().filter { it.seriesId != null && it.id.toString() !in hidden }.associateBy { it.seriesId!! }
            val resumeFor = partial.groupBy { it.seriesId ?: it.id }.mapValues { it.value.first() }
            val out = LinkedHashMap<UUID, BaseItemDto>()
            for (h in historyJob.await()) {
                val key = h.seriesId ?: h.id
                if (key in out) continue
                val finished = h.userData?.played == true
                val pick = when {
                    // The last thing played in this series was finished: the next episode, if there is one.
                    finished && h.seriesId != null -> next[key] ?: resumeFor[key]?.takeIf { r -> r.id != h.id }
                    // Finished film: done with it.
                    finished -> null
                    else -> resumeFor[key]
                }
                if (pick != null) out[key] = pick
            }
            // Anything part-watched that's older than the history checked above.
            partial.forEach { val k = it.seriesId ?: it.id; if (k !in out) out[k] = it }
            out.values.take(20)
        }
    }

    suspend fun nextUp(): List<BaseItemDto> = withContext(Dispatchers.IO) {
        api.tvShowsApi.getNextUp(userId = userId, limit = 20, fields = cardFields, enableUserData = true, enableResumable = false).content.items
    }


    suspend fun item(id: UUID): BaseItemDto = withContext(Dispatchers.IO) {
        api.userLibraryApi.getItem(itemId = id, userId = userId).content
    }

    /**
     * Extra videos stored with a title on the server: trailers, interviews, behind the scenes and so on
     * (files in "trailers", "extras", "featurettes"… folders next to it).
     */
    suspend fun extras(id: UUID): List<BaseItemDto> = withContext(Dispatchers.IO) {
        kotlinx.coroutines.coroutineScope {
            val trailers = async { runCatching { api.userLibraryApi.getLocalTrailers(itemId = id, userId = userId).content }.getOrDefault(emptyList()) }
            val special = async { runCatching { api.userLibraryApi.getSpecialFeatures(itemId = id, userId = userId).content }.getOrDefault(emptyList()) }
            (trailers.await() + special.await()).distinctBy { it.id }
        }
    }

    private val similarCache = java.util.concurrent.ConcurrentHashMap<UUID, List<BaseItemDto>>()
    private val similarAt = java.util.concurrent.ConcurrentHashMap<UUID, Long>()

    /** Everything in the library a person is credited on, newest first. */
    suspend fun personTitles(personId: UUID): List<BaseItemDto> =
        runCatching { browse(personId = personId, sortBy = ItemSortBy.PREMIERE_DATE, descending = true, limit = 120) }.getOrDefault(emptyList())

    suspend fun seasons(seriesId: UUID): List<BaseItemDto> = withContext(Dispatchers.IO) {
        api.tvShowsApi.getSeasons(seriesId = seriesId, userId = userId, enableUserData = true).content.items
            // Season 1, 2, 3 … with Specials (season 0) last, so the left-most is always the first real season.
            .sortedBy { it.indexNumber?.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }
    }

    suspend fun episodes(seriesId: UUID, seasonId: UUID): List<BaseItemDto> = withContext(Dispatchers.IO) {
        api.tvShowsApi.getEpisodes(
            seriesId = seriesId,
            seasonId = seasonId,
            userId = userId,
            fields = listOf(ItemFields.OVERVIEW, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
            enableUserData = true,
        ).content.items
    }

    /** The episode a series should resume or start from. */
    suspend fun nextEpisodeFor(seriesId: UUID): BaseItemDto? = withContext(Dispatchers.IO) {
        api.tvShowsApi.getNextUp(userId = userId, seriesId = seriesId, limit = 1, enableResumable = true, enableUserData = true).content.items.firstOrNull()
    }

    /** The episode that follows [episode] in the series, if any. */
    suspend fun episodeAfter(episode: BaseItemDto): BaseItemDto? = withContext(Dispatchers.IO) {
        val seriesId = episode.seriesId ?: return@withContext null
        val list = api.tvShowsApi.getEpisodes(seriesId = seriesId, userId = userId, adjacentTo = episode.id, enableUserData = true).content.items
        val index = list.indexOfFirst { it.id == episode.id }
        if (index >= 0) list.getOrNull(index + 1) else null
    }

    suspend fun similar(id: UUID): List<BaseItemDto> = withContext(Dispatchers.IO) {
        api.libraryApi.getSimilarItems(itemId = id, userId = userId, limit = 28, fields = cardFields).content.items
    }

    /**
     * "More like this" for the details page: titles that really are like [item], best first.
     *
     * Candidates come from three places: anything by the same director(s), anything sharing a genre, and the
     * server's own suggestions. Each is then scored on what it has in common:
     *  - same director: 10 each · same writer: 3 each
     *  - each shared genre: 3, plus up to 3 more for how closely the genre mixes match (so "Sci-Fi, Thriller" next
     *    to "Sci-Fi, Thriller" beats it next to "Sci-Fi, Comedy, Family")
     *  - each shared lead actor (top 6 billed): 2 · shared studio: 1 · shared tag/keyword: 1 (up to 3)
     *  - made within 5 years: 1.5 (within 10: 0.75) · and a nudge for higher-rated titles to break ties.
     * So the same director and genres come first, and a single shared genre comes last.
     */
    suspend fun moreLikeThis(item: BaseItemDto, count: Int = 14): List<BaseItemDto> {
        // Kept for half an hour, so titles and tags added on the server show up without restarting the app.
        val now = System.currentTimeMillis()
        similarCache[item.id]?.takeIf { now - (similarAt[item.id] ?: 0L) < 30 * 60_000L }?.let { return it }
        return computeMoreLikeThis(item, count).also { similarCache[item.id] = it; similarAt[item.id] = now }
    }

    /** How many films (or series) carry each tag, and how many there are in all. */
    private class TagTable(val total: Int, val counts: Map<String, Int>, val at: Long)
    private val tagTables = java.util.concurrent.ConcurrentHashMap<BaseItemKind, TagTable>()
    private val tagLock = Mutex()

    /** Built by one pass over the library (tags only, 1,000 titles a request), then kept for six hours. */
    private suspend fun tagTable(type: BaseItemKind): TagTable = tagLock.withLock {
        tagTables[type]?.takeIf { System.currentTimeMillis() - it.at < 6 * 3_600_000L }?.let { return@withLock it }
        val counts = HashMap<String, Int>()
        var total = 0
        runCatching {
            var start = 0
            while (true) {
                val page = api.itemsApi.getItems(
                    userId = userId, recursive = true, includeItemTypes = listOf(type), fields = listOf(ItemFields.TAGS),
                    enableUserData = false, enableImages = false, enableTotalRecordCount = false, startIndex = start, limit = 1000,
                ).content.items
                page.forEach { it.tags.orEmpty().toSet().forEach { t -> counts[t] = (counts[t] ?: 0) + 1 } }
                total += page.size
                if (page.size < 1000) break
                start += 1000
            }
        }
        TagTable(total, counts, System.currentTimeMillis()).also { if (total > 0) tagTables[type] = it }
    }

    /** Gets the tag tables ready in the background (called once Home has loaded), so the first title's page doesn't wait for them. */
    fun warmTags() {
        background.launch { runCatching { tagTable(BaseItemKind.MOVIE); tagTable(BaseItemKind.SERIES) } }
    }

    private suspend fun computeMoreLikeThis(item: BaseItemDto, count: Int): List<BaseItemDto> = withContext(Dispatchers.IO) {
        val type = item.type
        val types = if (type == BaseItemKind.SERIES) listOf(BaseItemKind.SERIES) else listOf(BaseItemKind.MOVIE)
        val fields = cardFields + listOf(ItemFields.PEOPLE, ItemFields.STUDIOS, ItemFields.TAGS)
        val people = item.people.orEmpty()
        val directors = people.filter { it.type == org.jellyfin.sdk.model.api.PersonKind.DIRECTOR }.map { it.id }.toSet()
        val writers = people.filter { it.type == org.jellyfin.sdk.model.api.PersonKind.WRITER }.map { it.id }.toSet()
        val leads = people.filter { it.type == org.jellyfin.sdk.model.api.PersonKind.ACTOR }.take(6).map { it.id }.toSet()
        val genres = item.genres.orEmpty().toSet()
        val studios = item.studios.orEmpty().mapNotNull { it.name }.toSet()
        val tags = item.tags.orEmpty().toSet()
        // How rare each of this title's tags is in the library: a tag on 3 films says far more than one on 400.
        // One table per kind of title (built once by a single pass over the library), not a request per tag.
        val table = tagTable(types.first())
        val libraryCount = table.total
        val tagCounts = table.counts
        /** 0..1: near 1 for a tag only a handful of titles share, near 0 for one most of the library has. */
        fun tagWeight(t: String): Double {
            val n = tagCounts[t] ?: return 0.0
            if (n <= 1 || libraryCount <= 1) return 0.0
            return (kotlin.math.ln(libraryCount.toDouble() / n) / kotlin.math.ln(libraryCount.toDouble())).coerceIn(0.0, 1.0)
        }
        // The distinctive tags (not on more than a fifth of the library) are what the extra search looks for.
        val telling = tags.filter { (tagCounts[it] ?: 0) in 2..maxOf(2, libraryCount / 5) }.sortedByDescending { tagWeight(it) }.take(10)

        val candidates = kotlinx.coroutines.coroutineScope {
            fun query(block: suspend () -> List<BaseItemDto>) = async { runCatching { block() }.getOrDefault(emptyList()) }
            val byTags = if (telling.isEmpty()) null else query {
                api.itemsApi.getItems(
                    userId = userId, recursive = true, includeItemTypes = types,
                    tags = telling, limit = 80, fields = fields, enableUserData = true,
                ).content.items
            }
            val byDirector = if (directors.isEmpty()) null else query {
                api.itemsApi.getItems(
                    userId = userId, recursive = true, includeItemTypes = types,
                    personIds = directors.toList(), limit = 24, fields = fields, enableUserData = true,
                ).content.items
            }
            val byGenre = if (genres.isEmpty()) null else query {
                api.itemsApi.getItems(
                    userId = userId, recursive = true, includeItemTypes = types,
                    genres = genres.toList(), sortBy = listOf(ItemSortBy.COMMUNITY_RATING), sortOrder = listOf(SortOrder.DESCENDING),
                    limit = 40, fields = fields, enableUserData = true,
                ).content.items
            }
            val server = query {
                api.libraryApi.getSimilarItems(itemId = item.id, userId = userId, limit = 24, fields = fields).content.items
            }
            listOfNotNull(byDirector, byGenre, byTags, server).flatMap { it.await() }
        }.distinctBy { it.id }.filter { it.id != item.id && it.type in types }

        fun score(c: BaseItemDto): Double {
            val cp = c.people.orEmpty()
            val cGenres = c.genres.orEmpty().toSet()
            val sharedGenres = (genres intersect cGenres).size
            var s = 0.0
            s += 10.0 * cp.count { it.type == org.jellyfin.sdk.model.api.PersonKind.DIRECTOR && it.id in directors }
            s += 3.0 * cp.count { it.type == org.jellyfin.sdk.model.api.PersonKind.WRITER && it.id in writers }
            s += 3.0 * sharedGenres
            val union = (genres union cGenres).size
            if (union > 0) s += 3.0 * sharedGenres / union
            s += 2.0 * cp.filter { it.type == org.jellyfin.sdk.model.api.PersonKind.ACTOR }.take(6).count { it.id in leads }
            s += 1.0 * c.studios.orEmpty().count { it.name in studios }
            // Shared tags, each counted by how rare it is (capped, so a long tag list can't outweigh everything else).
            s += minOf(14.0, 4.0 * c.tags.orEmpty().filter { it in tags }.sumOf { tagWeight(it) })
            val years = if (item.productionYear != null && c.productionYear != null) kotlin.math.abs(item.productionYear!! - c.productionYear!!) else null
            if (years != null) s += if (years <= 5) 1.5 else if (years <= 10) 0.75 else 0.0
            s += 0.5 * ((c.communityRating ?: 6f) / 10f)
            return s
        }
        candidates
            .map { it to score(it) }
            // Must have something real in common (a genre or a person), not just era or rating.
            .filter { (c, s) -> s >= 3.0 }
            .sortedByDescending { it.second }
            .take(count)
            .map { it.first }
    }

    /** Every film and series name (plain and simplified), fetched once, for spotting a mistyped search. */
    private class NameIndex(val names: List<String>, val plain: List<String>)

    @Volatile
    private var nameIndex: NameIndex? = null
    private val nameLock = Mutex()

    private fun simplify(text: String): String =
        text.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").trim().replace(Regex(" +"), " ").removePrefix("the ")

    private suspend fun names(): NameIndex = nameLock.withLock {
        nameIndex?.let { return@withLock it }
        val all = ArrayList<String>()
        var start = 0
        while (true) {
            val page = api.itemsApi.getItems(
                userId = userId,
                recursive = true,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                fields = emptyList(),
                enableImages = false,
                enableUserData = false,
                startIndex = start,
                limit = 1000,
            ).content.items
            page.mapNotNullTo(all) { it.name }
            start += page.size
            if (page.size < 1000) break
        }
        val distinct = all.distinct()
        NameIndex(distinct, distinct.map { simplify(it) }).also { nameIndex = it }
    }

    suspend fun warmNames() {
        runCatching { withContext(Dispatchers.Default) { names() } }
    }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /**
     * "Did you mean": the title that [term] is closest to, when it is close but not an actual match (a letter wrong, missing or
     * swapped). Null when the text already matches a title, or nothing is near. Compares with the whole name, with its start (for
     * text that is still being typed) and, for a single word, with each word of the name.
     */
    suspend fun didYouMean(term: String): String? = withContext(Dispatchers.Default) {
        val q = simplify(term)
        if (q.length < 3) return@withContext null
        val index = nameIndex ?: return@withContext null
        val allowed = when {
            q.length <= 4 -> 1
            q.length <= 8 -> 2
            else -> 3
        }
        var best: String? = null
        var bestScore = Int.MAX_VALUE
        for (i in index.plain.indices) {
            val n = index.plain[i]
            if (n.contains(q)) return@withContext null
            var d = minOf(editDistance(q, n), editDistance(q, n.take(q.length)))
            if (!q.contains(' ')) {
                for (word in n.split(' ')) if (word.length >= 3) d = minOf(d, editDistance(q, word))
            }
            if (d in 1..allowed) {
                // Closer wins; between equals, the shorter (more likely the one meant).
                val score = d * 1000 + n.length
                if (score < bestScore) { bestScore = score; best = index.names[i] }
            }
        }
        best
    }

    suspend fun search(term: String): List<BaseItemDto> = withContext(Dispatchers.IO) {
        api.itemsApi.getItems(
            userId = userId,
            searchTerm = term,
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.BOX_SET),
            limit = 40,
            fields = cardFields,
            enableUserData = true,
        ).content.items
    }

    suspend fun recentlyAdded(limit: Int = 24): List<BaseItemDto> = withContext(Dispatchers.IO) {
        api.itemsApi.getItems(
            userId = userId,
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
            sortBy = listOf(ItemSortBy.DATE_CREATED),
            sortOrder = listOf(SortOrder.DESCENDING),
            limit = limit,
            fields = cardFields,
            enableUserData = true,
        ).content.items
    }



    suspend fun collectionItems(collection: BaseItemDto): List<BaseItemDto> = withContext(Dispatchers.IO) {
        api.itemsApi.getItems(userId = userId, parentId = collection.id, fields = cardFields, enableUserData = true).content.items
    }

    suspend fun setFavorite(id: UUID, favourite: Boolean): Boolean = withContext(Dispatchers.IO) {
        val data = if (favourite) api.userLibraryApi.markFavoriteItem(itemId = id, userId = userId)
        else api.userLibraryApi.unmarkFavoriteItem(itemId = id, userId = userId)
        // Succeeded when the server now says what was asked for (removing answers "not a favourite", which is not a failure).
        data.content.isFavorite == favourite
    }

    /** Takes a title out of Continue watching by clearing where you'd got to (it stays unwatched, just not "in progress"). */
    suspend fun clearProgress(id: UUID): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            api.itemsApi.updateItemUserData(
                itemId = id,
                userId = userId,
                data = org.jellyfin.sdk.model.api.UpdateUserItemDataDto(playbackPositionTicks = 0L),
            )
        }.isSuccess
    }

    /**
     * Everything that's been played, most recent first: finished or not (unfinished ones carry their progress).
     * Returns the page and the total, which stops short once the never-played items start.
     */
    suspend fun history(start: Int, count: Int): Pair<List<BaseItemDto>, Int> = withContext(Dispatchers.IO) {
        val result = api.itemsApi.getItems(
            userId = userId,
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
            sortBy = listOf(ItemSortBy.DATE_PLAYED),
            sortOrder = listOf(SortOrder.DESCENDING),
            startIndex = start,
            limit = count,
            fields = cardFields,
            enableUserData = true,
        ).content
        val played = result.items.takeWhile { it.userData?.lastPlayedDate != null }
        played to (if (played.size < result.items.size) start + played.size else result.totalRecordCount)
    }

    /** Empties the watch history: every played title is marked unwatched (which also drops its resume point). */
    suspend fun clearHistory(): Boolean = withContext(Dispatchers.IO) {
        var ok = true
        repeat(50) {
            val page = runCatching { history(0, 200).first }.getOrNull() ?: return@withContext false
            if (page.isEmpty()) return@withContext ok
            page.forEach { item ->
                if (runCatching { api.playStateApi.markUnplayedItem(itemId = item.id, userId = userId) }.isFailure) ok = false
            }
        }
        ok
    }

    suspend fun setPlayed(id: UUID, played: Boolean): Boolean = withContext(Dispatchers.IO) {
        val data = if (played) api.playStateApi.markPlayedItem(itemId = id, userId = userId)
        else api.playStateApi.markUnplayedItem(itemId = id, userId = userId)
        data.content.played
    }

    suspend fun segments(id: UUID): List<MediaSegmentDto> = withContext(Dispatchers.IO) {
        runCatching {
            api.mediaSegmentsApi.getItemSegments(itemId = id, includeSegmentTypes = listOf(MediaSegmentType.INTRO, MediaSegmentType.OUTRO, MediaSegmentType.RECAP, MediaSegmentType.PREVIEW))
                .content.items
        }.getOrDefault(emptyList())
    }

    /** The intro / credits markers for [item] (sent to Jellyfin by Bookend), fetched afresh each time. */
    suspend fun segments(item: BaseItemDto): List<MediaSegmentDto> = segments(item.id)
}
