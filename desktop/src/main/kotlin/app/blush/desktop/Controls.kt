package app.blush.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.blush.tv.ui.theme.Blush
import coil3.compose.AsyncImage

// The Mac app's building blocks. Mouse first: everything clickable shows a hand pointer and lights up under it at once;
// keyboard focus (Tab) gets the same look, so the keyboard still works.

/** Interaction state for one control: hovered, pressed, or focused with the keyboard. */
class Hover(val source: MutableInteractionSource, val hovered: Boolean, val pressed: Boolean, val focused: Boolean) {
    val lit get() = hovered || focused
}

@Composable
fun rememberHover(): Hover {
    val source = remember { MutableInteractionSource() }
    val h by source.collectIsHoveredAsState()
    val p by source.collectIsPressedAsState()
    val f by source.collectIsFocusedAsState()
    return Hover(source, h, p, f)
}

/** Makes anything a mouse target: hand pointer, hover, click (and Enter/Space when focused with Tab). */
fun Modifier.mouseTarget(hover: Hover, enabled: Boolean = true, onClick: () -> Unit): Modifier =
    (if (enabled) pointerHoverIcon(PointerIcon.Hand) else this)
        .hoverable(hover.source, enabled)
        .clickable(interactionSource = hover.source, indication = null, enabled = enabled, onClick = onClick)

/** The main action on a screen: a white pill. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val h = rememberHover()
    val bg = when {
        !enabled -> Color.White.copy(alpha = 0.25f)
        h.pressed -> Color(0xFFD9D9DE)
        h.lit -> Color.White
        else -> Color(0xFFEDEDF0)
    }
    Box(
        modifier.height(38.dp).clip(RoundedCornerShape(50)).background(bg)
            .mouseTarget(h, enabled, onClick).padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = DesktopType.label, color = Color(0xFF17181F)) }
}

/** A secondary action: see-through until the pointer is over it. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val h = rememberHover()
    val bg = when {
        h.pressed -> Color.White.copy(alpha = 0.20f)
        h.lit -> Color.White.copy(alpha = 0.14f)
        else -> Color.White.copy(alpha = 0.07f)
    }
    Box(
        modifier.height(34.dp).clip(RoundedCornerShape(50)).background(bg).mouseTarget(h, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = DesktopType.label, color = if (h.lit) Color.White else Blush.colors.textSoft) }
}

/** Plain text that acts as a link (e.g. "Change server"). */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val h = rememberHover()
    Text(
        text,
        modifier.clip(RoundedCornerShape(6.dp)).mouseTarget(h, onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        style = DesktopType.label,
        color = if (h.lit) Color.White else Blush.colors.textMuted,
    )
}

/** A text box: rounded, a little lighter than the page, outlined in the accent colour while typing. */
@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    focus: FocusRequester? = null,
    onDone: () -> Unit = {},
) {
    val h = rememberHover()
    val colors = Blush.colors
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = DesktopType.body.copy(color = colors.text),
        cursorBrush = SolidColor(colors.text),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        interactionSource = h.source,
        modifier = modifier
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            // Return submits (the desktop text field doesn't always pass it to keyboardActions).
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { onDone(); true } else false }
            .pointerHoverIcon(PointerIcon.Text)
            .hoverable(h.source),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (h.hovered && !h.focused) colors.surfaceRaised else colors.surface)
                    .border(1.dp, if (h.focused) colors.accent.color.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = DesktopType.body, color = colors.textDim, maxLines = 1)
                inner()
            }
        },
    )
}

/** A round profile picture, or the first letter of the name on a tinted circle when there is none. */
@Composable
fun Avatar(name: String, imageUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(tintFor(name)), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), style = DesktopType.headline.copy(fontSize = DesktopType.headline.fontSize * (size.value / 96f)), color = Color.White.copy(alpha = 0.9f))
        if (imageUrl != null) AsyncImage(imageUrl, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** A steady colour per name, for avatars and tiles without pictures. */
fun tintFor(name: String): Color {
    val tints = listOf(0xFF6B4E71, 0xFF3F5E7A, 0xFF4F6B57, 0xFF7A5A3F, 0xFF5B5480, 0xFF7A4651)
    return Color(tints[Math.floorMod(name.hashCode(), tints.size)])
}

/**
 * A picture tile that grows slightly and gains a white ring under the pointer. The grow is a short ease rather than a
 * snap, so the tile doesn't jump; the ring is immediate.
 */
@Composable
fun HoverTile(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    background: Color = Blush.colors.surface,
    content: @Composable BoxScope.(hovered: Boolean) -> Unit,
) {
    val h = rememberHover()
    val scale by animateFloatAsState(if (h.lit && onClick != null) 1.035f else 1f, tween(110))
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(background)
            .then(if (onClick != null) Modifier.mouseTarget(h, onClick = onClick) else Modifier.hoverable(h.source))
            .then(if (h.lit && onClick != null) Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), shape) else Modifier),
    ) { content(h.lit) }
}

/** A short message line (errors, "Connecting…"), centred. */
@Composable
fun Note(text: String?, modifier: Modifier = Modifier, error: Boolean = false) {
    Text(
        text ?: "",
        modifier.fillMaxWidth().padding(top = 4.dp),
        style = DesktopType.label,
        color = if (error) Color(0xFFF2A0A0) else Blush.colors.textMuted,
        textAlign = TextAlign.Center,
    )
}
