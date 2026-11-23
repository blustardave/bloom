package app.blush.tv

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.blush.tv.ui.components.Icons
import app.blush.tv.ui.player.CloseFace
import app.blush.tv.ui.player.Countdown
import app.blush.tv.ui.player.PromptFace
import app.blush.tv.ui.player.PromptIconFace
import app.blush.tv.ui.theme.BlushTheme
import app.blush.tv.ui.theme.BlushType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the player's prompts to PNGs (app/build/shots), so they can be looked at without a device. CI uploads them as the
 * "bloom-shots" artifact. The screen is a Fire TV's 960 × 540 dp (1920 × 1080 px).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi", application = android.app.Application::class)
class PromptShots {
    /** Shows [content] in a plain activity and draws the window into a PNG. */
    private fun shot(name: String, content: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { BlushTheme { content() } }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val view = activity.window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(image))
        val dir = File("build/shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A stand-in for a film frame: dark, uneven colour, with a bright patch, so see-through buttons are judged fairly. */
    @Composable
    private fun FakeFrame(modifier: Modifier = Modifier) {
        Box(
            modifier.drawBehind {
                drawRect(Brush.linearGradient(listOf(Color(0xFF1C2A3A), Color(0xFF3A2C24), Color(0xFF0E1116)), Offset.Zero, Offset(size.width, size.height)))
                drawCircle(Brush.radialGradient(listOf(Color(0x88E8C9A0), Color.Transparent), Offset(size.width * 0.72f, size.height * 0.35f), size.height * 0.45f), size.height * 0.45f, Offset(size.width * 0.72f, size.height * 0.35f))
                drawRect(Color(0xFF6A7C8C).copy(alpha = 0.35f), Offset(size.width * 0.1f, size.height * 0.62f), size.copy(width = size.width * 0.5f, height = size.height * 0.08f))
            },
        )
    }

    @Composable
    private fun Caption(text: String) = BasicText(text, style = BlushType.caption.copy(color = Color.White.copy(alpha = 0.6f)))

    @Test
    fun buttons() = shot("buttons") {
        Box(Modifier.size(960.dp, 540.dp)) {
            FakeFrame(Modifier.fillMaxSize())
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(Countdown.Edge, Countdown.Bar, Countdown.Fill).forEach { style ->
                    Caption("Skip intro, countdown as $style — resting 65% / selected 65% / selected 15% / no countdown")
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        PromptFace("Skip intro", focused = false, progress = 0.65f, style = style)
                        PromptFace("Skip intro", focused = true, progress = 0.65f, style = style)
                        PromptFace("Skip intro", focused = true, progress = 0.15f, style = style)
                        PromptFace("Skip intro", focused = false)
                        PromptFace("Skip intro", focused = true)
                    }
                }
                Caption("Credits — Next episode selected / skip selected / ✕ selected")
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CloseFace(false); PromptFace("Next episode", true); PromptIconFace(Icons.SkipAhead, false)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CloseFace(false); PromptFace("Next episode", false); PromptIconFace(Icons.SkipAhead, true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CloseFace(true); PromptFace("Next episode", false); PromptIconFace(Icons.SkipAhead, false)
                    }
                }
                Caption("Credits tiles — Dismiss resting / selected")
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { CloseFace(false, label = "Dismiss"); CloseFace(true, label = "Dismiss") }
            }
        }
    }

    /** Subtitle looks, each over a dark and a bright patch of picture. */
    @Test
    fun subtitles() = shot("subtitles") {
        val L = app.blush.tv.ui.player.SubtitleLook
        val base = app.blush.tv.ui.player.SubtitleStyle(0.09f, L.fontFamily("Bloom"), 650, Color(0xFFF7F4F2), "Shadow", 0f, 0.08f)
        val looks = listOf(
            "Shadow" to base,
            "Outline · Classic" to base.copy(edge = "Outline", font = L.fontFamily("Classic")),
            "Thick outline · Yellow" to base.copy(edge = "Thick outline", colour = Color(0xFFF6DF6B)),
            "Dark box · Serif" to base.copy(edge = "None", backgroundAlpha = 0.62f, font = L.fontFamily("Serif"), weight = 450),
            "Solid · Condensed" to base.copy(edge = "None", backgroundAlpha = 0.92f, font = L.fontFamily("Condensed")),
            "Light · Typewriter · Ice" to base.copy(edge = "Shadow", backgroundAlpha = 0.35f, font = L.fontFamily("Typewriter"), colour = Color(0xFFC4E4FF)),
        )
        val line = listOf(
            app.blush.tv.ui.player.SubLine(androidx.compose.ui.text.buildAnnotatedString {
                append("Do you like our owl?\n")
                pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)); append("It's artificial?"); pop()
            }),
        )
        Column(Modifier.size(960.dp, 540.dp).background(Color(0xFF161618)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            looks.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (name, look) ->
                        Column {
                            Box(
                                Modifier.size(300.dp, 220.dp).background(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF26323F), Color(0xFF7A6A58), Color(0xFFF2EEE6))),
                                ),
                            ) { app.blush.tv.ui.player.SubtitleLayer(line, look) }
                            androidx.compose.foundation.text.BasicText(name, style = androidx.compose.ui.text.TextStyle(color = Color.White))
                        }
                    }
                }
            }
        }
    }

    /** Every button over a plain white picture and a pale sky, to check they still show up. */
    @Test
    fun brightFrame() = shot("bright-frame") {
        Column(Modifier.size(960.dp, 540.dp)) {
            listOf(Color.White, Color(0xFFDCE6F0)).forEach { bg ->
                Column(
                    Modifier.fillMaxWidth().weight(1f).background(bg).padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PromptFace("Skip intro", focused = false, progress = 0.6f)
                        PromptFace("Skip intro", focused = true, progress = 0.6f)
                        PromptFace("Skip intro", focused = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CloseFace(false); PromptFace("Next episode", true); PromptIconFace(Icons.SkipAhead, false)
                        Box(Modifier.size(24.dp))
                        CloseFace(true); PromptFace("Next episode", false); PromptIconFace(Icons.SkipAhead, true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { CloseFace(false, label = "Dismiss"); CloseFace(true, label = "Dismiss") }
                }
            }
        }
    }

    /** The whole screen with the credits prompt where the player puts it (44 dp from the right, 124 dp up). */
    @Test
    fun creditsFrame() = shot("credits-frame") {
        Box(Modifier.size(960.dp, 540.dp)) {
            FakeFrame(Modifier.fillMaxSize())
            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = 44.dp, bottom = 124.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) { CloseFace(false); PromptFace("Next episode", true); PromptIconFace(Icons.SkipAhead, false) }
        }
    }

    /** The credits tiles laid out as the player does (five across, details above), with plain blocks for the pictures. */
    @Test
    fun tilesFrame() = shot("tiles-frame") {
        Box(Modifier.size(960.dp, 540.dp)) {
            FakeFrame(Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color(0x99000000), 1f to Color(0xE6000000)))
                    .padding(start = 44.dp, end = 44.dp, top = 56.dp, bottom = 34.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CloseFace(false, label = "Dismiss", modifier = Modifier.offset(x = -app.blush.tv.ui.player.CloseInset))
                    Column(Modifier.fillMaxWidth(0.62f).height(76.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        BasicText("Inglourious Basterds", style = BlushType.headline)
                        BasicText("2009 · 2h 33m · War, Drama", style = BlushType.caption.copy(color = Color.White.copy(alpha = 0.75f)))
                        BasicText("In Nazi-occupied France, a group of Jewish-American soldiers plan to take down the Third Reich's leadership, while a cinema owner plots her own revenge.", style = BlushType.caption.copy(color = Color.White.copy(alpha = 0.55f)), maxLines = 2)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val colours = listOf(0xFF5A4636, 0xFF2E3F52, 0xFF4B2F3A, 0xFF34463A, 0xFF4A4A30)
                        colours.forEachIndexed { i, c ->
                            Box(
                                Modifier.width(163.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(c))
                                    .then(if (i == 0) Modifier.drawBehind { } else Modifier),
                            )
                        }
                    }
                }
            }
        }
    }
}
