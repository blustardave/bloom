package app.blush.tv.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Shared by the TV app and the Mac app: Bloom draws text subtitles itself, so both look the same and follow the same settings.

/** One subtitle line block on screen: its text (with italics/bold kept) and whether it belongs at the top of the picture. */
data class SubLine(val text: AnnotatedString, val top: Boolean = false)

/** A subtitle with its times, from a subtitle file fetched from the server (used to shift the timing). */
data class TimedCue(val startMs: Long, val endMs: Long, val line: SubLine)

/** How text subtitles are drawn (see [SubtitleLayer]). [size] and [bottom] are fractions of the screen height. */
data class SubtitleStyle(
    val size: Float,
    val font: FontFamily,
    val weight: Int,
    val colour: Color,
    val edge: String,
    val backgroundAlpha: Float,
    val bottom: Float,
)

/** The choices offered in Settings for the subtitle look. Each list is name → value; the names are what's saved. */
object SubtitleOptions {
    val sizes = listOf("Extra small" to 0.034f, "Small" to 0.040f, "Medium" to 0.047f, "Large" to 0.056f, "Extra large" to 0.066f, "Huge" to 0.080f)
    val fonts = listOf("Bloom", "Classic", "Serif", "Condensed", "Typewriter")
    val weights = listOf("Regular" to 450, "Medium" to 650, "Bold" to 800)
    val colours = listOf(
        "White" to 0xFFF7F4F2.toInt(), "Yellow" to 0xFFF6DF6B.toInt(), "Grey" to 0xFFC9C4C8.toInt(),
        "Ice blue" to 0xFFC4E4FF.toInt(), "Mint" to 0xFFB4F0C4.toInt(), "Pink" to 0xFFF7C4DC.toInt(),
    )
    val edges = listOf("Shadow", "Outline", "Thick outline", "None")
    val backgrounds = listOf("None" to 0f, "Light" to 0.35f, "Dark" to 0.62f, "Solid" to 0.92f)
    val positions = listOf("Low" to 0.035f, "Normal" to 0.075f, "High" to 0.13f)

    /** The value saved under [name], or the [fallback]th one when the name isn't known. */
    fun <T> pick(list: List<Pair<String, T>>, name: String, fallback: Int) = (list.firstOrNull { it.first == name } ?: list[fallback]).second
}

/** Reading SRT subtitle files (the server converts any text format to SRT on request). */
object Srt {
    /** The cues showing at [ms]. */
    fun at(cues: List<TimedCue>, ms: Long): List<SubLine> = cues.filter { ms >= it.startMs && ms < it.endMs }.map { it.line }

    private val timeLine = Regex("""(\d+):(\d{1,2}):(\d{1,2})[,.](\d{1,3})\s*-->\s*(\d+):(\d{1,2}):(\d{1,2})[,.](\d{1,3})""")

    fun parse(text: String): List<TimedCue> {
        val out = ArrayList<TimedCue>()
        text.replace("\r", "").removePrefix("﻿").split(Regex("\n[ \t]*\n")).forEach { block ->
            val lines = block.trim('\n').lines()
            val at = lines.indexOfFirst { timeLine.containsMatchIn(it) }
            if (at < 0) return@forEach
            val m = timeLine.find(lines[at])!!.groupValues
            fun ms(h: String, mi: String, s: String, f: String) =
                ((h.toLong() * 60 + mi.toLong()) * 60 + s.toLong()) * 1000 + f.padEnd(3, '0').take(3).toLong()
            val start = ms(m[1], m[2], m[3], m[4])
            val end = ms(m[5], m[6], m[7], m[8])
            val body = lines.drop(at + 1).joinToString("\n").trim()
            if (body.isEmpty() || end <= start) return@forEach
            val top = Regex("""\{\\an[789]\}""").containsMatchIn(body)
            out += TimedCue(start, end, SubLine(tags(body.replace(Regex("""\{[^}]*\}"""), "")), top))
        }
        return out.sortedBy { it.startMs }
    }

    /** SRT's <i> and <b> become italic and bold; any other tag is dropped. */
    private fun tags(body: String): AnnotatedString = buildAnnotatedString {
        var italic = 0; var bold = 0
        var i = 0
        val tag = Regex("""<\s*(/?)\s*([a-zA-Z]+)[^>]*>""")
        while (i < body.length) {
            val m = tag.find(body, i)
            val end = m?.range?.first ?: body.length
            if (end > i) {
                val start = length
                append(body.substring(i, end))
                if (italic > 0) addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                if (bold > 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
            }
            if (m == null) break
            val closing = m.groupValues[1] == "/"
            when (m.groupValues[2].lowercase()) {
                "i" -> italic = (italic + if (closing) -1 else 1).coerceAtLeast(0)
                "b" -> bold = (bold + if (closing) -1 else 1).coerceAtLeast(0)
            }
            i = m.range.last + 1
        }
    }
}

/**
 * Draws [lines] over the whole picture in the chosen [style]. [lift] raises the bottom lines (above the controls).
 * Used by the player and by the preview in Settings, so the preview is exactly what you get.
 */
@Composable
fun SubtitleLayer(lines: List<SubLine>, style: SubtitleStyle, modifier: Modifier = Modifier, lift: Dp = 0.dp) {
    if (lines.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxSize()) {
        val h = maxHeight
        val bottom = lines.filter { !it.top }
        val top = lines.filter { it.top }
        val width = Modifier.widthIn(max = maxWidth * 0.84f)
        if (bottom.isNotEmpty()) SubBlock(bottom, style, h, Modifier.align(Alignment.BottomCenter).padding(bottom = h * style.bottom + lift).then(width))
        if (top.isNotEmpty()) SubBlock(top, style, h, Modifier.align(Alignment.TopCenter).padding(top = h * 0.06f).then(width))
    }
}

@Composable
private fun SubBlock(lines: List<SubLine>, style: SubtitleStyle, screenHeight: Dp, modifier: Modifier) {
    val density = LocalDensity.current
    val sizePx = with(density) { (screenHeight * style.size).toPx() }
    val fontSize = with(density) { sizePx.toSp() }
    val text = remember(lines) {
        buildAnnotatedString { lines.forEachIndexed { i, l -> if (i > 0) append("\n"); append(l.text) } }
    }
    val base = TextStyle(
        fontFamily = style.font,
        fontWeight = FontWeight(style.weight),
        fontSize = fontSize,
        lineHeight = fontSize * 1.24f,
        textAlign = TextAlign.Center,
        color = style.colour,
    )
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val padH = sizePx * 0.32f
    Box(
        modifier
            .drawBehind {
                val l = layout ?: return@drawBehind
                if (style.backgroundAlpha <= 0f) return@drawBehind
                for (i in 0 until l.lineCount) {
                    val left = l.getLineLeft(i); val right = l.getLineRight(i)
                    if (right - left < 1f) continue
                    val top = l.getLineTop(i)
                    drawRoundRect(
                        Color.Black.copy(alpha = style.backgroundAlpha),
                        topLeft = Offset(left, top),
                        size = Size(right - left + padH * 2, l.getLineBottom(i) - top),
                        cornerRadius = CornerRadius(sizePx * 0.14f),
                    )
                }
            }
            .padding(horizontal = with(density) { padH.toDp() }),
    ) {
        when (style.edge) {
            "Outline", "Thick outline" -> BasicText(
                text,
                style = base.copy(
                    color = Color.Black.copy(alpha = 0.92f),
                    drawStyle = Stroke(width = sizePx * if (style.edge == "Outline") 0.11f else 0.2f, join = StrokeJoin.Round),
                ),
            )
        }
        BasicText(
            text,
            style = if (style.edge == "Shadow") base.copy(shadow = Shadow(Color.Black.copy(alpha = 0.88f), Offset(0f, sizePx * 0.05f), sizePx * 0.14f)) else base,
            onTextLayout = { layout = it },
        )
    }
}
