package app.blush.tv.data

/** What "Surprise me" on Home picks from. Kept in [Store.surpriseOptions]. */
data class SurpriseOptions(
    val kind: String = FILM,
    val genre: String? = null,
    val length: String = ANY,
    val minRating: Int = 0,
    val includeWatched: Boolean = false,
) {
    /** The allowed running time in minutes for films, or null for any. */
    val minutes: IntRange?
        get() = when (length) {
            SHORT -> 1..95
            MEDIUM -> 1..125
            LONG -> 125..1000
            else -> null
        }

    /** One line saying what it will pick, e.g. "A thriller film you haven't watched · under 2 hours · rated 7+". */
    fun describe(): String {
        val what = when (kind) { SERIES -> "series"; EITHER -> "film or series"; else -> "film" }
        val head = listOfNotNull(genre?.lowercase(), what).joinToString(" ")
        val article = if (head.first().lowercaseChar() in "aeiou") "An" else "A"
        val parts = mutableListOf("$article $head" + if (includeWatched) "" else " you haven't watched")
        if (kind != SERIES) when (length) {
            SHORT -> parts += "under 1½ hours"
            MEDIUM -> parts += "under 2 hours"
            LONG -> parts += "over 2 hours"
        }
        if (minRating > 0) parts += "rated $minRating+"
        return parts.joinToString(" · ")
    }

    companion object {
        const val FILM = "film"
        const val SERIES = "series"
        const val EITHER = "either"
        const val ANY = "any"
        const val SHORT = "short"
        const val MEDIUM = "medium"
        const val LONG = "long"
    }
}
