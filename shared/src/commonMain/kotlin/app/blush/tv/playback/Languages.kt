package app.blush.tv.playback

/** The languages offered for default audio and subtitles, with every code a server might use for each. */
object Languages {
    class Lang(val name: String, val codes: Set<String>) {
        /** The code stored in settings. */
        val code: String get() = codes.first()
    }

    val all = listOf(
        Lang("English", linkedSetOf("eng", "en")),
        Lang("Japanese", linkedSetOf("jpn", "ja")),
        Lang("French", linkedSetOf("fre", "fra", "fr")),
        Lang("German", linkedSetOf("ger", "deu", "de")),
        Lang("Spanish", linkedSetOf("spa", "es")),
        Lang("Italian", linkedSetOf("ita", "it")),
        Lang("Portuguese", linkedSetOf("por", "pt")),
        Lang("Dutch", linkedSetOf("dut", "nld", "nl")),
        Lang("Swedish", linkedSetOf("swe", "sv")),
        Lang("Danish", linkedSetOf("dan", "da")),
        Lang("Norwegian", linkedSetOf("nor", "nob", "no")),
        Lang("Finnish", linkedSetOf("fin", "fi")),
        Lang("Polish", linkedSetOf("pol", "pl")),
        Lang("Russian", linkedSetOf("rus", "ru")),
        Lang("Turkish", linkedSetOf("tur", "tr")),
        Lang("Arabic", linkedSetOf("ara", "ar")),
        Lang("Hindi", linkedSetOf("hin", "hi")),
        Lang("Korean", linkedSetOf("kor", "ko")),
        Lang("Chinese", linkedSetOf("chi", "zho", "zh", "cmn")),
        Lang("Thai", linkedSetOf("tha", "th")),
    )

    fun find(code: String?): Lang? {
        val c = code?.lowercase()?.substringBefore('-')?.takeIf { it.isNotBlank() } ?: return null
        return all.firstOrNull { c in it.codes }
    }

    fun nameOf(code: String?): String = find(code)?.name ?: code ?: "Unknown"

    /** True when two language codes mean the same language, whichever spelling each uses. */
    fun same(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        val la = find(a)
        val lb = find(b)
        return if (la != null && lb != null) la === lb else a.equals(b, ignoreCase = true)
    }
}

/**
 * Which languages to start a film or episode with. [audio] is a language code; [subtitles] is "auto" (English if
 * the server would show subtitles at all), "off", or a language code.
 */
class LangPrefs(
    val audio: String,
    val subtitles: String,
    /** The exact track last chosen in this series (a closer match than language alone), or null. */
    val audioPick: TrackPick? = null,
    /** As [audioPick] for subtitles; [TrackPick.Off] when they were turned off. */
    val subtitlePick: TrackPick? = null,
)

/**
 * A track chosen in one episode, described so the same one can be found in the next: its language, its name ("Signs & Songs",
 * "Full", "Commentary"), whether it's forced, its format, and which of the tracks in that language it was.
 */
data class TrackPick(val language: String?, val title: String?, val forced: Boolean, val codec: String?, val ordinal: Int, val off: Boolean = false) {
    fun encode(): String = if (off) "off" else listOf(language.orEmpty(), title.orEmpty(), if (forced) "1" else "0", codec.orEmpty(), ordinal.toString()).joinToString("\u0001")

    companion object {
        val Off = TrackPick(null, null, false, null, 0, off = true)

        fun decode(s: String?): TrackPick? {
            if (s.isNullOrEmpty()) return null
            if (s == "off") return Off
            val p = s.split("\u0001")
            if (p.size < 5) return null
            return TrackPick(p[0].ifEmpty { null }, p[1].ifEmpty { null }, p[2] == "1", p[3].ifEmpty { null }, p[4].toIntOrNull() ?: 0)
        }

        /** Describes [stream] among [all] tracks of the same kind. */
        fun of(stream: org.jellyfin.sdk.model.api.MediaStream, all: List<org.jellyfin.sdk.model.api.MediaStream>): TrackPick {
            val sameLang = all.filter { Languages.same(it.language, stream.language) || (it.language == null && stream.language == null) }
            return TrackPick(stream.language, stream.title, stream.isForced == true, stream.codec, sameLang.indexOfFirst { it.index == stream.index }.coerceAtLeast(0))
        }

        /** The track in [candidates] most like [pick]: same language first, then same name, forced-ness, format and position. */
        fun best(candidates: List<org.jellyfin.sdk.model.api.MediaStream>, pick: TrackPick): org.jellyfin.sdk.model.api.MediaStream? {
            if (candidates.isEmpty()) return null
            val pool = candidates.filter { Languages.same(it.language, pick.language) || (it.language == null && pick.language == null) }
                .ifEmpty { if (pick.language == null) candidates else return null }
            fun norm(t: String?) = t?.trim()?.lowercase().orEmpty()
            return pool.withIndex().maxByOrNull { (i, s) ->
                (if (norm(s.title) == norm(pick.title)) 8 else 0) +
                    (if ((s.isForced == true) == pick.forced) 4 else 0) +
                    (if (s.codec.equals(pick.codec, ignoreCase = true)) 2 else 0) +
                    (if (i == pick.ordinal) 1 else 0)
            }?.value
        }
    }
}
