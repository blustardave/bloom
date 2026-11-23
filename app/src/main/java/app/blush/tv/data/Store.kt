package app.blush.tv.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class SavedServer(val id: String, val name: String, val address: String)

@Serializable
data class SavedUser(
    val serverId: String,
    val userId: String,
    val name: String,
    val token: String,
    val imageTag: String? = null,
)

/** A library pinned to the side bar, with the icon chosen for it. */
@Serializable
data class RailPin(val libraryId: String, val name: String, val icon: String)

/** Small persistent store for the server, signed-in profiles and app preferences. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("blush", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var server: SavedServer?
        get() = prefs.getString("server", null)?.let { runCatching { json.decodeFromString(SavedServer.serializer(), it) }.getOrNull() }
        set(value) = prefs.edit().apply {
            if (value == null) remove("server") else putString("server", json.encodeToString(SavedServer.serializer(), value))
        }.apply()

    val users: List<SavedUser>
        get() = prefs.getString("users", null)
            ?.let { runCatching { json.decodeFromString(ListSerializer(SavedUser.serializer()), it) }.getOrNull() }
            .orEmpty()

    fun saveUser(user: SavedUser) {
        val list = users.filterNot { it.serverId == user.serverId && it.userId == user.userId } + user
        prefs.edit().putString("users", json.encodeToString(ListSerializer(SavedUser.serializer()), list)).apply()
    }

    fun removeUser(serverId: String, userId: String) {
        val list = users.filterNot { it.serverId == serverId && it.userId == userId }
        prefs.edit().putString("users", json.encodeToString(ListSerializer(SavedUser.serializer()), list)).apply()
    }

    fun clearUsers() = prefs.edit().remove("users").apply()

    /** Profiles taken off the "Who's watching?" screen on this TV ("serverId|userId"); signing in as them again brings them back. */
    private val hiddenUsers: Set<String>
        get() = prefs.getStringSet("hiddenUsers", emptySet()).orEmpty()

    fun hideUser(serverId: String, userId: String) {
        prefs.edit().putStringSet("hiddenUsers", hiddenUsers + "$serverId|$userId").apply()
    }

    fun unhideUser(serverId: String, userId: String) {
        prefs.edit().putStringSet("hiddenUsers", hiddenUsers - "$serverId|$userId").apply()
    }

    fun isHidden(serverId: String, userId: String) = "$serverId|$userId" in hiddenUsers

    var backdropId: String?
        get() = prefs.getString("backdrop", null)
        set(value) = prefs.edit().putString("backdrop", value).apply()

    var accentId: String?
        get() = prefs.getString("accent", null)
        set(value) = prefs.edit().putString("accent", value).apply()

    /** Ask Jellyfin to skip intros automatically instead of showing a button. */
    var autoSkipIntro: Boolean
        get() = prefs.getBoolean("autoSkipIntro", false)
        set(value) = prefs.edit().putBoolean("autoSkipIntro", value).apply()

    /** Send surround sound to the TV/soundbar untouched. Volume and speed then can't be changed in the app. */
    var passthroughAudio: Boolean
        get() = prefs.getBoolean("passthroughAudio", false)
        set(value) = prefs.edit().putBoolean("passthroughAudio", value).apply()

    /** Max streaming bitrate in bits per second. */
    var maxBitrate: Int
        get() = prefs.getInt("maxBitrate", 120_000_000)
        set(value) = prefs.edit().putInt("maxBitrate", value).apply()

    var preferredSubtitleLanguage: String?
        get() = prefs.getString("subLang", null)
        set(value) = prefs.edit().putString("subLang", value).apply()

    /** Shows the on-screen performance read-out. */
    var showStats: Boolean
        get() = prefs.getBoolean("showStats", false)
        set(value) = prefs.edit().putBoolean("showStats", value).apply()

    /** Home screen previews: "on", "muted" or "off". */
    var previews: String
        get() = prefs.getString("previews", "on") ?: "on"
        set(value) = prefs.edit().putString("previews", value).apply()

    /** Extra loudness in millibels (100 = 1 dB) applied during playback. */
    var volumeBoost: Int
        get() = prefs.getInt("volumeBoost", 0)
        set(value) = prefs.edit().putInt("volumeBoost", value).apply()

    /** The last few searches, newest first. */
    var recentSearches: List<String>
        get() = prefs.getString("recentSearches", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        set(value) = prefs.edit().putString("recentSearches", value.take(8).joinToString("\n")).apply()

    fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        recentSearches = listOf(q) + recentSearches.filterNot { it.equals(q, ignoreCase = true) }
    }

    /** How a library is shown: "Posters", "Wide" or "Genres". */
    var watchlistView: String
        get() = prefs.getString("watchlistView", "Posters") ?: "Posters"
        set(value) = prefs.edit().putString("watchlistView", value).apply()

    var libraryView: String
        get() = prefs.getString("libraryView", "Posters") ?: "Posters"
        set(value) = prefs.edit().putString("libraryView", value).apply()

    /** The player's volume (0–1.5), kept between films. */
    var playerVolume: Float
        get() = prefs.getFloat("playerVolume", 1f)
        set(value) = prefs.edit().putFloat("playerVolume", value).apply()

    /** Language to start films and episodes in (a code such as "eng"). */
    var defaultAudioLang: String
        get() = prefs.getString("defAudio", "eng") ?: "eng"
        set(value) = prefs.edit().putString("defAudio", value).apply()

    /** "auto" (English when the server would show subtitles), "off", or a language code. */
    var defaultSubtitleLang: String
        get() = prefs.getString("defSubs", "auto") ?: "auto"
        set(value) = prefs.edit().putString("defSubs", value).apply()

    /** The audio and subtitle language last chosen for a series ("off" for no subtitles), so its next episode matches. */
    fun seriesAudio(seriesId: String): String? = prefs.getString("serA_$seriesId", null)
    fun seriesSubtitles(seriesId: String): String? = prefs.getString("serS_$seriesId", null)
    fun rememberSeriesAudio(seriesId: String, lang: String?) {
        if (lang != null) prefs.edit().putString("serA_$seriesId", lang).apply()
    }
    fun rememberSeriesSubtitles(seriesId: String, lang: String?) {
        if (lang != null) prefs.edit().putString("serS_$seriesId", lang).apply()
    }
    /** The exact audio / subtitle track last chosen in a series (see TrackPick), so the next episode starts on the same one. */
    fun seriesAudioPick(seriesId: String): String? = prefs.getString("serAP_$seriesId", null)
    fun seriesSubtitlePick(seriesId: String): String? = prefs.getString("serSP_$seriesId", null)
    fun rememberSeriesAudioPick(seriesId: String, pick: String) = prefs.edit().putString("serAP_$seriesId", pick).apply()
    fun rememberSeriesSubtitlePick(seriesId: String, pick: String) = prefs.edit().putString("serSP_$seriesId", pick).apply()

    /** Subtitle look, chosen in Settings (see SubtitleLook). */
    var subtitleSize: String
        get() = prefs.getString("subSize", "Medium") ?: "Medium"
        set(value) = prefs.edit().putString("subSize", value).apply()

    /** Old single "style" setting (Shadow / Outline / Box), read only to carry it over to [subtitleEdge] and [subtitleBackground]. */
    private val oldSubStyle: String? get() = prefs.getString("subStyle", null)

    var subtitleEdge: String
        get() = prefs.getString("subEdge", null) ?: oldSubStyle?.let { if (it == "Box") "None" else it } ?: "Shadow"
        set(value) = prefs.edit().putString("subEdge", value).apply()

    var subtitleBackground: String
        get() = prefs.getString("subBg", null) ?: if (oldSubStyle == "Box") "Dark" else "None"
        set(value) = prefs.edit().putString("subBg", value).apply()

    var subtitleFont: String
        get() = prefs.getString("subFont", "Bloom") ?: "Bloom"
        set(value) = prefs.edit().putString("subFont", value).apply()

    var subtitleWeight: String
        get() = prefs.getString("subWeight", "Medium") ?: "Medium"
        set(value) = prefs.edit().putString("subWeight", value).apply()

    var subtitlePosition: String
        get() = prefs.getString("subPos", "Normal") ?: "Normal"
        set(value) = prefs.edit().putString("subPos", value).apply()

    var subtitleColour: String
        get() = prefs.getString("subColour", "White") ?: "White"
        set(value) = prefs.edit().putString("subColour", value).apply()

    /** Home rows switched off in Settings (by option key, see HomeRowOptions). */
    /** Next-up episodes removed from Continue watching (they come back once a later episode is watched, as the next one changes). */
    var hiddenNextUp: Set<String>
        get() = prefs.getStringSet("hiddenNextUp", emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet("hiddenNextUp", value.toList().takeLast(200).toSet()).apply()

    var hiddenHomeRows: Set<String>
        get() = prefs.getStringSet("hiddenHomeRows", emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet("hiddenHomeRows", value).apply()

    /** How many genre rows Home shows (pinned genres first, then a random mix). */
    var genreRowCount: Int
        get() = prefs.getInt("genreRowCount", 8)
        set(value) = prefs.edit().putInt("genreRowCount", value).apply()

    /** Genres that always get a row on Home. */
    /** What "Surprise me" picks from. */
    var surpriseOptions: SurpriseOptions
        get() = SurpriseOptions(
            kind = prefs.getString("surpriseKind", null) ?: SurpriseOptions.FILM,
            genre = prefs.getString("surpriseGenre", null),
            length = prefs.getString("surpriseLength", null) ?: SurpriseOptions.ANY,
            minRating = prefs.getInt("surpriseRating", 0),
            includeWatched = prefs.getBoolean("surpriseWatched", false),
        )
        set(o) = prefs.edit()
            .putString("surpriseKind", o.kind).putString("surpriseGenre", o.genre).putString("surpriseLength", o.length)
            .putInt("surpriseRating", o.minRating).putBoolean("surpriseWatched", o.includeWatched).apply()

    var pinnedGenres: List<String>
        get() = prefs.getString("pinnedGenres", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        set(value) = prefs.edit().putString("pinnedGenres", value.joinToString("\n")).apply()

    /** Lite mode: no previews, no banner rotation, no fades. For slower sticks. */
    var liteMode: Boolean
        get() = prefs.getBoolean("liteMode", false)
        set(value) = prefs.edit().putBoolean("liteMode", value).apply()

    /** The most titles a Home row holds. */
    var homeRowSize: Int
        get() = prefs.getInt("homeRowSize", 20)
        set(value) = prefs.edit().putInt("homeRowSize", value).apply()

    /** Whether Home's rows stay scrolled where you left them when you come back from a title. */
    var rowsRememberPlace: Boolean
        get() = prefs.getBoolean("rowsRememberPlace", true)
        set(value) = prefs.edit().putBoolean("rowsRememberPlace", value).apply()

    /** Carry on to the next episode by itself when one finishes. */
    var autoNextEpisode: Boolean
        get() = prefs.getBoolean("autoNextEpisode", true)
        set(value) = prefs.edit().putBoolean("autoNextEpisode", value).apply()

    /** How a Home poster behaves under the remote: "rest" (opens once you stop), "now" (opens at once), "posters" (never opens). Null: not chosen. */
    var tileOpen: String?
        get() = prefs.getString("tileOpen", null)
        set(value) = prefs.edit().apply { if (value == null) remove("tileOpen") else putString("tileOpen", value) }.apply()

    /** Quicker movement along and between Home's rows (off: exactly as it was before the option existed). */
    var quickNav: Boolean
        get() = prefs.getBoolean("quickNav", true)
        set(value) = prefs.edit().putBoolean("quickNav", value).apply()

    /** The household's own order for Home's rows, by option key. Empty: a fresh mix each day. */
    var homeRowOrder: List<String>
        get() = prefs.getString("homeRowOrder", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        set(value) = prefs.edit().putString("homeRowOrder", value.joinToString("\n")).apply()

    /** How the navigation bar's pill looks: "text" (names), "icons", "iconsText" (both) or "sliding" (icons; the active one's name slides out). */
    var pillStyle: String
        get() = prefs.getString("pillStyle", "text") ?: "text"
        set(value) = prefs.edit().putString("pillStyle", value).apply()

    /** Libraries shown as their own items in the side bar. */
    fun railPins(serverId: String?, userId: String?): List<RailPin> {
        // Pins belong to a profile ON A SERVER (library ids mean nothing on another one). A list saved before that (for the
        // profile alone, or for everybody) is handed to the first server that asks for it, once.
        val key = "railPins:$serverId:$userId"
        val older = prefs.getString("railPins:$userId", null)?.let { "railPins:$userId" } ?: "railPins".takeIf { prefs.contains(it) }
        val saved = prefs.getString(key, null) ?: older?.let { k -> prefs.getString(k, null)?.also { prefs.edit().putString(key, it).remove(k).apply() } }
        return saved?.let { runCatching { json.decodeFromString(ListSerializer(RailPin.serializer()), it) }.getOrNull() }.orEmpty()
    }

    fun setRailPins(serverId: String?, userId: String?, value: List<RailPin>) =
        prefs.edit().putString("railPins:$serverId:$userId", json.encodeToString(ListSerializer(RailPin.serializer()), value)).apply()
}
