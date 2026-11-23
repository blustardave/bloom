package app.blush.tv.ui.player

import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.media3.common.text.Cue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bloom draws text subtitles itself (rather than the player's own view), so every option in Settings applies: font, weight,
 * colour, edge, background, size and position, and they lift above the controls when those are showing. Picture subtitles
 * (PGS / VobSub) are still drawn by the player.
 */
object SubtitleText {
    /** The player's cues as lines. */
    fun fromCues(cues: List<Cue>): List<SubLine> = cues.mapNotNull { c ->
        val t = c.text ?: return@mapNotNull null
        if (t.isBlank()) return@mapNotNull null
        SubLine(annotated(t), top = isTop(c))
    }

    private fun isTop(c: Cue): Boolean {
        if (c.line == Cue.DIMEN_UNSET) return false
        return when (c.lineType) {
            Cue.LINE_TYPE_FRACTION -> c.line < 0.35f
            Cue.LINE_TYPE_NUMBER -> c.line >= 0f && c.line < 4f
            else -> false
        }
    }

    private fun annotated(t: CharSequence): AnnotatedString {
        if (t !is Spanned) return AnnotatedString(t.toString().trim('\n'))
        return buildAnnotatedString {
            append(t.toString())
            t.getSpans(0, t.length, Any::class.java).forEach { span ->
                val s = t.getSpanStart(span); val e = t.getSpanEnd(span)
                when (span) {
                    is StyleSpan -> when (span.style) {
                        android.graphics.Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), s, e)
                        android.graphics.Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), s, e)
                        android.graphics.Typeface.BOLD_ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold), s, e)
                    }
                    is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), s, e)
                }
            }
        }
    }

    /** The cues showing at [ms] in a fetched file. */
    fun at(cues: List<TimedCue>, ms: Long): List<SubLine> = Srt.at(cues, ms)

    /** Fetches subtitle stream [index] of the playing file from the server as SRT (the server converts any text format). */
    suspend fun fetch(baseUrl: String, token: String, source: app.blush.tv.playback.PlaybackSource, index: Int): List<TimedCue> = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/Videos/${source.item.id}/${source.mediaSource.id}/Subtitles/$index/0/Stream.srt?api_key=$token"
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 60_000
        try {
            Srt.parse(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
        } finally {
            conn.disconnect()
        }
    }

    fun parseSrt(text: String): List<TimedCue> = Srt.parse(text)
}
