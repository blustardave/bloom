package app.blush.tv.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemDto

/** One Home row as it was last shown. */
@Serializable
class SavedRow(val key: String, val title: String, val items: List<BaseItemDto>, val playOnClick: Boolean = false, val numbered: Boolean = false)

/** Home as it was last built: the day it was built, the Settings it was built under, the banner's titles and the rows. */
@Serializable
class SavedHome(val day: Long, val settings: String, val rows: List<SavedRow>, val featured: List<BaseItemDto> = emptyList())

/**
 * The finished Home page, kept on the device per profile. It is shown at once on the next launch while the server is
 * asked again, and within the same day its rows are reused as they are (so Home is the same all day).
 */
object HomeCache {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; allowSpecialFloatingPointValues = true }

    private fun file(repo: Repo): java.io.File? {
        val server = repo.store.server?.id ?: return null
        val user = repo.userId ?: return null
        return java.io.File(repo.cacheDir, "home-$server-$user.json")
    }

    fun read(repo: Repo): SavedHome? = runCatching {
        val f = file(repo)?.takeIf { it.exists() } ?: return null
        json.decodeFromString(SavedHome.serializer(), f.readText())
    }.getOrNull()

    fun write(repo: Repo, home: SavedHome) {
        runCatching {
            val f = file(repo) ?: return
            val tmp = java.io.File(f.path + ".tmp")
            tmp.writeText(json.encodeToString(SavedHome.serializer(), home))
            tmp.renameTo(f)
        }
    }

    fun forget(repo: Repo) {
        runCatching { file(repo)?.delete() }
    }
}
