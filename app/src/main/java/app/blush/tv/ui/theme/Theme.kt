package app.blush.tv.ui.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.blush.tv.R

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(
    manrope(400),
    manrope(500),
    manrope(600),
    manrope(700),
    manrope(800),
)

object BlushType {
    private val base = TextStyle(fontFamily = Manrope, color = Color(0xFFF5F5F7))
    // A deliberately quiet scale, after Disney+: the artwork does the talking, the text stays small.
    val display = base.copy(fontSize = 30.sp, fontWeight = FontWeight.W800, letterSpacing = (-0.5).sp, lineHeight = 33.sp)
    val headline = base.copy(fontSize = 21.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.2).sp)
    val title = base.copy(fontSize = 14.sp, fontWeight = FontWeight.W700)
    /** Section headings inside a page (between a page header and a title). */
    val subhead = base.copy(fontSize = 17.sp, fontWeight = FontWeight.W700)
    /** Paragraphs meant to be read: overviews, biographies, descriptions. The same size as the side bar's names. */
    val reading = base.copy(fontSize = 13.sp, fontWeight = FontWeight.W500, lineHeight = 19.sp)
    val body = base.copy(fontSize = 12.sp, fontWeight = FontWeight.W500, lineHeight = 17.sp)
    val label = base.copy(fontSize = 11.sp, fontWeight = FontWeight.W700)
    val caption = base.copy(fontSize = 10.5.sp, fontWeight = FontWeight.W600)
    val kicker = base.copy(fontSize = 10.5.sp, fontWeight = FontWeight.W700, letterSpacing = 1.6.sp)
    /** Row titles on Home and similar lists. */
    val rowHeader = base.copy(fontSize = 13.sp, fontWeight = FontWeight.W600)
    /** Button text: small capitals, spaced out. */
    val button = base.copy(fontSize = 11.sp, fontWeight = FontWeight.W700, letterSpacing = 1.4.sp)
}

/** Small wrapper over BasicText so every label uses the app's font and colours by default. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = BlushType.body,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign = TextAlign.Unspecified,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val resolved = style.merge(
        TextStyle(
            color = if (color != Color.Unspecified) color else if (style.color != Color.Unspecified) style.color else Blush.colors.text,
            fontSize = fontSize,
            textAlign = textAlign,
        ),
    )
    BasicText(
        text = text,
        modifier = modifier,
        style = resolved,
        maxLines = maxLines,
        overflow = overflow,
    )
}
