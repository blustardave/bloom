package app.blush.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import app.blush.tv.ui.components.FocusSurface
import app.blush.tv.ui.components.Icon
import app.blush.tv.ui.components.Icons
import app.blush.tv.ui.theme.BlushType

/**
 * The player's intro / credits prompts. Each has a "face" (just the look, given whether it's selected) and a button around it.
 * The faces have no focus handling, so they can be drawn on their own: the screenshot test (src/test, PromptShots) renders them
 * so they can be looked at before going on a device.
 */
internal object Prompt {
    val Corner = 8.dp
    val Height = 32.dp
    val Shape = RoundedCornerShape(Corner)
    /** Resting: soft see-through white, like the player's other buttons. */
    val Rest = Color.White.copy(alpha = 0.16f)
    /** Resting, the part of the countdown still to run. */
    val RestLeft = Color.White.copy(alpha = 0.30f)
    /** Selected: near-white, with dark writing. */
    val Focus = Color(0xFFF5F5F7)
    /** Selected, the part of the countdown already run: a shade darker, so the white part reads as the time left. */
    val FocusSpent = Color(0xFFC9CACF)
    val Ink = Color(0xFF14151B)
    /** A dark layer under the resting white, so a resting button still shows up over a white or very bright picture. */
    val Under = Color.Black.copy(alpha = 0.32f)
    /** Hairline round each button: light at rest, dark when selected (so a white button doesn't vanish into a white picture). */
    val EdgeRest = Color.White.copy(alpha = 0.24f)
    val EdgeFocus = Color.Black.copy(alpha = 0.16f)
    /** Soft shadow under white writing / the ✕, for the same reason. */
    val TextShadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.7f), offset = androidx.compose.ui.geometry.Offset(0f, 1.5f), blurRadius = 4f)
}

/** The shared outline and shadow: a soft shadow under a selected button, and a hairline round every one. */
private fun Modifier.promptEdge(shape: androidx.compose.ui.graphics.Shape, focused: Boolean): Modifier =
    (if (focused) shadow(6.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black) else this)
        .clip(shape)
        .border(1.dp, if (focused) Prompt.EdgeFocus else Prompt.EdgeRest, shape)

/** How the time left on a prompt is shown. */
internal enum class Countdown {
    /** A line running round the button's edge that shortens as the time runs out. */
    Edge,
    /** A thin bar along the bottom of the button that shortens. */
    Bar,
    /** The brighter part of the button shrinking from the right. */
    Fill,
}

/**
 * A prompt button's look. [progress] (1 → 0), when given, is the time left, shown as [style]. Everything is drawn inside the
 * button's own clipped shape, so it always lines up with it.
 */
@Composable
internal fun PromptFace(label: String, focused: Boolean, modifier: Modifier = Modifier, progress: Float? = null, style: Countdown = Countdown.Fill) {
    Box(
        modifier
            .height(Prompt.Height)
            .promptEdge(Prompt.Shape, focused)
            .drawBehind {
                if (!focused) drawRect(Prompt.Under)
                val p = progress?.coerceIn(0f, 1f)
                if (p == null || style != Countdown.Fill) {
                    drawRect(if (focused) Prompt.Focus else Prompt.Rest)
                } else {
                    drawRect(if (focused) Prompt.FocusSpent else Prompt.Rest)
                    if (p > 0f) drawRect(if (focused) Prompt.Focus else Prompt.RestLeft, size = size.copy(width = size.width * p))
                }
            }
            .drawWithContent {
                drawContent()
                val p = progress?.coerceIn(0f, 1f) ?: return@drawWithContent
                if (p <= 0f) return@drawWithContent
                val ink = if (focused) Prompt.Ink.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.85f)
                when (style) {
                    Countdown.Fill -> Unit
                    Countdown.Bar -> {
                        val h = 2.5.dp.toPx()
                        drawRect(ink, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - h), size = size.copy(width = size.width * p, height = h))
                    }
                    Countdown.Edge -> {
                        // The line starts at the top middle and runs clockwise; it's drawn on the inside of the clipped shape,
                        // following the same corner, so it sits exactly on the button's edge.
                        val w = 2.dp.toPx()
                        val r = Prompt.Corner.toPx()
                        val outline = androidx.compose.ui.graphics.Path().apply {
                            addRoundRect(androidx.compose.ui.geometry.RoundRect(w / 2, w / 2, size.width - w / 2, size.height - w / 2, androidx.compose.ui.geometry.CornerRadius(r - w / 2)))
                        }
                        val measure = androidx.compose.ui.graphics.PathMeasure()
                        measure.setPath(outline, false)
                        val length = measure.length
                        // Where the path starts (top-left, after the corner) to the top middle.
                        val shift = (size.width / 2f - r).coerceAtLeast(0f)
                        val part = androidx.compose.ui.graphics.Path()
                        val end = shift + length * p
                        measure.getSegment(shift, minOf(end, length), part, true)
                        if (end > length) measure.getSegment(0f, end - length, part, true)
                        drawPath(part, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Butt))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = BlushType.label.copy(
                color = if (focused) Prompt.Ink else Color.White,
                shadow = if (focused) null else Prompt.TextShadow,
                fontWeight = FontWeight.W600,
                textAlign = TextAlign.Center,
                // No font padding, so the words sit in the true middle of the button.
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
    }
}

/** A round icon button's look (the skip button beside Next episode), the same height as [PromptFace]. */
@Composable
internal fun PromptIconFace(icon: ImageVector, focused: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(Prompt.Height).promptEdge(CircleShape, focused)
            .then(if (focused) Modifier else Modifier.background(Prompt.Under)).background(if (focused) Prompt.Focus else Prompt.Rest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, if (focused) Prompt.Ink else Color.White, size = 14.dp)
    }
}

/**
 * A quiet ✕: no box at rest, a small white circle behind it when selected. With a [label] ("Dismiss") it's a small pill
 * instead: the ✕ and the word close together, the whole pill turning white when selected. The pill has [CloseInset] of space
 * before the ✕; place it that far to the left (offset) so the ✕ itself lines up with whatever is below it.
 */
@Composable
internal fun CloseFace(focused: Boolean, modifier: Modifier = Modifier, label: String? = null) {
    val tint = if (focused) Prompt.Ink else Color.White.copy(alpha = 0.78f)
    if (label == null) {
        Box(Modifier.height(Prompt.Height).then(modifier), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(if (focused) Prompt.Focus else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) { ShadowedIcon(Icons.Close, tint, 11.dp, shadow = !focused) }
        }
        return
    }
    Row(
        modifier
            .height(26.dp)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Prompt.Focus else Color.Transparent)
            .padding(start = CloseInset, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ShadowedIcon(Icons.Close, tint, 9.dp, shadow = !focused)
        BasicText(
            label,
            style = BlushType.caption.copy(
                color = tint,
                fontWeight = FontWeight.W600,
                shadow = if (focused) null else Prompt.TextShadow,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            maxLines = 1,
        )
    }
}

/** Space before the ✕ in the labelled [CloseFace]. */
internal val CloseInset = 9.dp

/** An icon with a faint dark copy behind it, so a white icon still shows over a white picture. */
@Composable
private fun ShadowedIcon(icon: ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp, shadow: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        if (shadow) Icon(icon, null, Color.Black.copy(alpha = 0.6f), size = size, modifier = Modifier.offset(y = 1.dp))
        Icon(icon, null, tint, size = size)
    }
}

/** The button around [PromptFace]. */
@Composable
internal fun PromptPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, countdown: (() -> Float)? = null) {
    FocusSurface(onClick = onClick, shape = Prompt.Shape, focusedScale = 1f, ringWidth = 0.dp, modifier = modifier) { focused ->
        PromptFace(label, focused, progress = countdown?.invoke())
    }
}

/** The button around [PromptIconFace]. */
@Composable
internal fun PromptIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(onClick = onClick, shape = CircleShape, focusedScale = 1f, ringWidth = 0.dp, modifier = modifier) { focused ->
        PromptIconFace(icon, focused)
    }
}

/** The button around [CloseFace]. */
@Composable
internal fun SubtleClose(description: String, onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(50), focusedScale = 1f, ringWidth = 0.dp, modifier = modifier) { focused ->
        CloseFace(focused, label = label)
    }
}
