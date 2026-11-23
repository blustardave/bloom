package app.blush.desktop

import app.blush.tv.data.Prefs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Where the Mac app keeps its files. */
object Places {
    private val home = File(System.getProperty("user.home"))
    private val mac = System.getProperty("os.name").orEmpty().startsWith("Mac")

    /** Settings and sign-ins: ~/Library/Application Support/Bloom on the Mac. */
    val data: File = (if (mac) File(home, "Library/Application Support/Bloom") else File(home, ".config/bloom")).apply { mkdirs() }

    /** Pictures and other things that can be fetched again: ~/Library/Caches/app.blush.mac on the Mac. */
    val cache: File = (if (mac) File(home, "Library/Caches/app.blush.mac") else File(home, ".cache/bloom")).apply { mkdirs() }
}

/**
 * The Mac app's settings, as one small JSON file (the TV app uses Android's SharedPreferences for the same [Prefs]).
 * Every change is written straight away, to a temporary file first so a crash can't leave it half-written.
 */
class DesktopPrefs(private val file: File = File(Places.data, "settings.json")) : Prefs {
    private val json = Json { prettyPrint = true }
    private val values: MutableMap<String, JsonElement> = runCatching {
        json.parseToJsonElement(file.readText()) as JsonObject
    }.getOrNull()?.toMutableMap() ?: mutableMapOf()

    private fun get(key: String) = synchronized(values) { values[key] }?.takeIf { it !is JsonNull }

    override fun getString(key: String, default: String?) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: default
    override fun getBoolean(key: String, default: Boolean) = (get(key) as? JsonPrimitive)?.booleanOrNull ?: default
    override fun getInt(key: String, default: Int) = (get(key) as? JsonPrimitive)?.intOrNull ?: default
    override fun getFloat(key: String, default: Float) = (get(key) as? JsonPrimitive)?.floatOrNull ?: default
    override fun getStringSet(key: String, default: Set<String>?): Set<String>? =
        (get(key) as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet() ?: default
    override fun contains(key: String) = get(key) != null
    override fun edit(): Prefs.Editor = Editor()

    private inner class Editor : Prefs.Editor {
        private val changes = mutableMapOf<String, JsonElement?>()
        override fun putString(key: String, value: String?) = apply { changes[key] = value?.let(::JsonPrimitive) }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = JsonPrimitive(value) }
        override fun putInt(key: String, value: Int) = apply { changes[key] = JsonPrimitive(value) }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = JsonPrimitive(value) }
        override fun putStringSet(key: String, value: Set<String>?) = apply { changes[key] = value?.let { s -> JsonArray(s.map(::JsonPrimitive)) } }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun apply() {
            val text = synchronized(values) {
                for ((k, v) in changes) if (v == null) values.remove(k) else values[k] = v
                json.encodeToString(JsonObject.serializer(), JsonObject(values.toMap()))
            }
            synchronized(file) {
                val tmp = File(file.path + ".tmp")
                tmp.writeText(text)
                // Holds sign-in tokens: readable by this Mac account only.
                tmp.setReadable(false, false); tmp.setReadable(true, true)
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            }
        }
    }
}
