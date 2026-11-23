package app.blush.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation

/**
 * A soft, smooth blur of a picture. Fire OS 8 (Android 11) has no built-in blur, and simply stretching a tiny copy
 * of the picture looked blocky, so a small copy is properly blurred here (once, then cached) and then scaled up,
 * which comes out smooth.
 */
@Composable
fun BlurredImage(url: String?, modifier: Modifier = Modifier) {
    if (url == null) return
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .size(Size(240, 135))
            .transformations(BoxBlur(radius = 6, passes = 3))
            .crossfade(false)
            .build()
    }
    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

/** Repeated box blurs (three passes look just like a gaussian blur), run on a small bitmap. */
private class BoxBlur(private val radius: Int, private val passes: Int) : Transformation() {
    override val cacheKey = "boxblur-$radius-$passes"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        val src = input.copy(Bitmap.Config.ARGB_8888, false)
        var pixels = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        var scratch = IntArray(w * h)
        repeat(passes) {
            blurLine(pixels, scratch, w, h, horizontal = true)
            blurLine(scratch, pixels, w, h, horizontal = false)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /** One box-blur pass along rows (or columns), with a running sum so it's fast at any radius. */
    private fun blurLine(from: IntArray, to: IntArray, w: Int, h: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        val window = radius * 2 + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val c = i.coerceIn(0, length - 1)
                return if (horizontal) from[line * w + c] else from[c * w + line]
            }
            var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) {
                val p = at(i)
                r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            for (i in 0 until length) {
                val index = if (horizontal) line * w + i else i * w + line
                to[index] = (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                val add = at(i + radius + 1)
                val drop = at(i - radius)
                r += ((add shr 16) and 0xFF) - ((drop shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((drop shr 8) and 0xFF)
                b += (add and 0xFF) - (drop and 0xFF)
            }
        }
    }
}
