package app.blush.desktop

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.blush.tv.ui.theme.Blush

// The Mac app's fonts and text sizes. The colours come from the shared module (app.blush.tv.ui.theme.Colors).
// Manrope is shipped as five fixed weights (made from the TV app's variable font) because the desktop text engine
// would otherwise draw every weight at the variable font's default, which is its lightest.

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    resource = "fonts/manrope-$weight.ttf",
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(),
)

val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

/** Text sizes for a laptop screen seen from arm's length: a little larger than the TV's, which is read from the sofa at 960 dp wide. */
object DesktopType {
    private val base = TextStyle(fontFamily = Manrope, color = Color(0xFFF5F5F7))
    val headline = base.copy(fontSize = 22.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.2).sp)
    val title = base.copy(fontSize = 15.sp, fontWeight = FontWeight.W700)
    val body = base.copy(fontSize = 13.sp, fontWeight = FontWeight.W500, lineHeight = 19.sp)
    val label = base.copy(fontSize = 12.sp, fontWeight = FontWeight.W600)
    val caption = base.copy(fontSize = 11.sp, fontWeight = FontWeight.W600)
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = DesktopType.body,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    val c = if (color != Color.Unspecified) color else Blush.colors.text
    BasicText(text, modifier, style.merge(TextStyle(color = c, textAlign = textAlign)), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** A picture from the jar's resources (bloom_wordmark.png etc.). */
fun resourceImage(name: String): ImageBitmap {
    val bytes = Thread.currentThread().contextClassLoader.getResourceAsStream(name)!!.use { it.readBytes() }
    return org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
}
