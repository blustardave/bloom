package app.blush.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.blush.tv.ui.player.SubLine
import app.blush.tv.ui.player.SubtitleLayer
import app.blush.tv.ui.player.SubtitleOptions
import app.blush.tv.ui.player.SubtitleStyle
import app.blush.tv.ui.theme.Blush

/**
 * Phase 1's only screen: proves the window, the shared code (colours, the subtitle renderer) and mouse handling work on
 * the Mac. Sign-in and browsing replace it in phase 2.
 */
@Composable
fun WelcomeScreen(version: String) {
    val colors = Blush.colors
    var edge by remember { mutableStateOf(SubtitleOptions.edges[0]) }
    var background by remember { mutableStateOf(SubtitleOptions.backgrounds[0].first) }
    val wordmark = remember { resourceImage("bloom_wordmark.png") }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .drawBehind {
                // A faint glow of the accent colour from the top edge.
                drawRect(
                    Brush.radialGradient(
                        listOf(colors.accent.color.copy(alpha = 0.09f), Color.Transparent),
                        center = Offset(size.width / 2, 0f),
                        radius = size.width * 0.55f,
                    ),
                )
            },
    ) {
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 640.dp).padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(wordmark, contentDescription = "Bloom", modifier = Modifier.height(56.dp).aspectRatio(430f / 112f))
            Spacer(Modifier.height(10.dp))
            Text("for Mac · early preview", style = DesktopType.caption, color = colors.textDim)
            Spacer(Modifier.height(34.dp))

            FilmFrame(edge, background)
            Spacer(Modifier.height(18.dp))

            ChoiceRow("Edge", SubtitleOptions.edges, edge) { edge = it }
            Spacer(Modifier.height(10.dp))
            ChoiceRow("Background", SubtitleOptions.backgrounds.map { it.first }, background) { background = it }

            Spacer(Modifier.height(30.dp))
            Text(
                "Signing in to your Jellyfin server and browsing your libraries come next.",
                style = DesktopType.body,
                color = colors.textMuted,
            )
        }
        Text(
            "Bloom $version",
            Modifier.align(Alignment.BottomEnd).padding(18.dp),
            style = DesktopType.caption,
            color = colors.textDim,
        )
    }
}

/** A stand-in for a film frame (a dusk sky), with a subtitle drawn by the same code the TV app's player uses. */
@Composable
private fun FilmFrame(edge: String, background: String) {
    val style = SubtitleStyle(
        size = SubtitleOptions.pick(SubtitleOptions.sizes, "Extra large", 2),
        font = Manrope,
        weight = SubtitleOptions.pick(SubtitleOptions.weights, "Medium", 1),
        colour = Color(SubtitleOptions.colours[0].second),
        edge = edge,
        backgroundAlpha = SubtitleOptions.pick(SubtitleOptions.backgrounds, background, 0),
        bottom = SubtitleOptions.pick(SubtitleOptions.positions, "Normal", 1),
    )
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.verticalGradient(
                    0f to Color(0xFF1F2B45),
                    0.48f to Color(0xFF8A5A6E),
                    0.66f to Color(0xFFE29A63),
                    0.70f to Color(0xFF3A2A33),
                    1f to Color(0xFF15131A),
                ),
            )
            drawCircle(Color(0xFFFFD49A).copy(alpha = 0.85f), radius = size.height * 0.09f, center = Offset(size.width * 0.62f, size.height * 0.63f))
            drawRect(Color(0xFF221B24), topLeft = Offset(0f, size.height * 0.68f))
        }
        SubtitleLayer(
            listOf(SubLine(AnnotatedString("Do you like our owl?\nIt's artificial?"))),
            style,
        )
    }
}

@Composable
private fun ChoiceRow(title: String, options: List<String>, selected: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.width(96.dp), style = DesktopType.label, color = Blush.colors.textDim)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { name -> HoverPill(name, name == selected, onClick = { onPick(name) }) }
        }
    }
}
