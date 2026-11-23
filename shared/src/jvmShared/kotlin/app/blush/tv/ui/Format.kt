package app.blush.tv.ui

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRange

private const val TICKS_PER_MINUTE = 600_000_000L

fun durationText(ticks: Long?): String? {
    if (ticks == null || ticks <= 0) return null
    val minutes = (ticks / TICKS_PER_MINUTE).toInt()
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

fun clockText(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun BaseItemDto.progress(): Float? {
    val data = userData ?: return null
    // A saved position means it is part-way through, even when the title is also marked watched (watched before, started again):
    // "time left" is worked out the same way. The server's own percentage is not always filled in, so it is only the fallback.
    val runtime = runTimeTicks ?: 0L
    val position = data.playbackPositionTicks ?: 0L
    if (runtime > 0 && position > 0) return (position.toDouble() / runtime).coerceIn(0.0, 1.0).toFloat()
    if (data.played == true) return null
    val pct = data.playedPercentage ?: 0.0
    return if (pct > 0.0) (pct / 100.0).coerceIn(0.0, 1.0).toFloat() else null
}

fun BaseItemDto.timeLeft(): String? {
    val runtime = runTimeTicks ?: return null
    val pos = userData?.playbackPositionTicks ?: 0L
    if (pos <= 0) return null
    return durationText(runtime - pos)?.let { "$it left" }
}

fun BaseItemDto.episodeCode(): String? {
    val s = parentIndexNumber ?: return null
    val e = indexNumber ?: return null
    return "S$s:E$e"
}

/** Name to show on a tile: series name for episodes. */
fun BaseItemDto.displayTitle(): String = when (type) {
    BaseItemKind.EPISODE -> seriesName ?: name.orEmpty()
    else -> name.orEmpty()
}

/** Second line under a tile. */
fun BaseItemDto.tileSubtitle(): String? = when (type) {
    BaseItemKind.EPISODE -> listOfNotNull(episodeCode(), timeLeft() ?: name).joinToString(" · ")
    else -> timeLeft() ?: productionYear?.toString()
}

/** "2024 · 1h 58m · 12A · Drama" */
fun BaseItemDto.metaLine(): String = buildList {
    productionYear?.let { add(it.toString()) }
    when (type) {
        BaseItemKind.SERIES -> childCount?.let { add(if (it == 1) "1 season" else "$it seasons") }
        else -> durationText(runTimeTicks)?.let { add(it) }
    }
    officialRating?.let { add(it) }
    genres?.take(2)?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
}.joinToString(" · ")

/**
 * The age rating as it should be shown: "15", "PG-13", "TV-MA". Servers often hold it with a country in front ("GB-15", "US-R")
 * and the likes of "NR" / "Not Rated" / "Unrated", which say nothing, so those are tidied or left out.
 */
fun BaseItemDto.ageRating(): String? {
    val raw = officialRating?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val plain = raw.lowercase().replace(" ", "").replace("-", "")
    if (plain in setOf("nr", "notrated", "unrated", "ur", "unknown", "none", "n/a", "na", "notapplicable", "0")) return null
    // "GB-15" → "15", but "TV-MA" stays.
    val country = Regex("^([A-Z]{2,3})-(.+)$").matchEntire(raw)
    val shown = if (country != null && country.groupValues[1] != "TV") country.groupValues[2] else raw
    return shown.takeIf { it.length <= 8 }
}

/** The server's community score, "★ 7.8" (the source depends on the library's metadata provider: IMDb, TMDb …). */
fun BaseItemDto.scoreText(): String? = communityRating?.takeIf { it > 0f }?.let { "★ %.1f".format(it) }

/** Quality badges from the first media source: 4K / 1080p, HDR kind, Atmos, 5.1 … */
fun BaseItemDto.qualityBadges(): List<String> {
    val streams = mediaStreams ?: mediaSources?.firstOrNull()?.mediaStreams ?: return emptyList()
    val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
    val audio = streams.filter { it.type == MediaStreamType.AUDIO }.maxByOrNull { it.channels ?: 0 }
    return buildList {
        // Widescreen films are cropped to less than 16:9 in height, so the width is what tells the resolution.
        val w = video?.width ?: 0
        val h = video?.height ?: 0
        // Short names: 4K, UHD (1440p), HD (1080p and 720p), SD.
        when {
            w >= 3200 || h >= 2000 -> add("4K")
            w >= 2400 || h >= 1300 -> add("UHD")
            w >= 1200 || h >= 700 -> add("HD")
            w > 0 -> add("SD")
        }
        val range = video?.videoRangeType?.name.orEmpty()
        when {
            range.startsWith("DOVI") -> add("Dolby Vision")
            range.contains("PLUS") -> add("HDR10+")
            range.startsWith("HDR10") -> add("HDR10")
            range.startsWith("HLG") -> add("HLG")
            video?.videoRange == VideoRange.HDR -> add("HDR")
        }
        val codec = audio?.codec.orEmpty()
        val label = ((audio?.title ?: "") + " " + (audio?.displayTitle ?: "") + " " + (audio?.profile ?: "")).lowercase()
        if (label.contains("atmos") || (codec.equals("truehd", true) && label.contains("atmos"))) add("Atmos")
        else when (audio?.channels) {
            8 -> add("7.1")
            6 -> add("5.1")
            else -> {}
        }
    }
}

private const val Base83 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"

/**
 * The poster's overall colour, read from the tiny blurred preview the server keeps of every picture (its first four
 * characters after the size are the average colour). Shown, darkened, in the tile while the poster itself loads.
 */
fun BaseItemDto.posterTint(): androidx.compose.ui.graphics.Color? {
    val hash = imageBlurHashes?.get(org.jellyfin.sdk.model.api.ImageType.PRIMARY)?.values?.firstOrNull() ?: return null
    if (hash.length < 6) return null
    var value = 0
    for (i in 2..5) {
        val digit = Base83.indexOf(hash[i])
        if (digit < 0) return null
        value = value * 83 + digit
    }
    val r = (value shr 16) and 0xFF
    val g = (value shr 8) and 0xFF
    val b = value and 0xFF
    return androidx.compose.ui.graphics.Color(red = r * 0.55f / 255f, green = g * 0.55f / 255f, blue = b * 0.55f / 255f)
}
