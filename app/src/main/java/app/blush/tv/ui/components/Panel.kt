package app.blush.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.theme.Text

/** Text size multiplier for panel rows; Settings uses a larger one than the pop-out panels. */
val LocalPanelTextScale = androidx.compose.runtime.staticCompositionLocalOf { 1.18f }

/** Corner radius of a panel row's highlight (Settings rounds it to match its cards). */
val LocalRowCorner = androidx.compose.runtime.staticCompositionLocalOf { 12.dp }
/** How far a dropdown's options are indented from its header. */
val LocalOptionIndent = androidx.compose.runtime.staticCompositionLocalOf { 12.dp }

/**
 * The panel that slides in from the right for settings and filters: a soft dark gradient rather than a box and
 * a small title. Left or Back closes it.
 */
@Composable
fun SidePanel(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** A smaller, see-through card floating at the right (used over a playing film) rather than a full-height panel. */
    floating: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = if (floating) Modifier
        .padding(end = 20.dp, top = 20.dp, bottom = 20.dp)
        .width(272.dp)
        .heightIn(max = 420.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(Color(0xE0141519))
        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
    else Modifier
        .fillMaxHeight()
        .width(310.dp)
        .background(Brush.horizontalGradient(0f to Color(0xD9121319), 0.12f to Color(0xF2121319), 1f to Color(0xFA121319)))
    Column(
        modifier
            .then(shape)
            // Left closes the panel (it opened from the right), so it needs no close button.
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { onClose(); true } else false
            }
            .padding(start = if (floating) 14.dp else 26.dp, end = if (floating) 14.dp else 20.dp, top = if (floating) 16.dp else 24.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Indented to line up with the text in the rows below.
        Text(title, style = BlushType.title.copy(fontWeight = FontWeight.W700), modifier = Modifier.padding(start = 10.dp))
        content()
    }
}

/** A closed dropdown: the setting's name, its current choice, and a small arrow. No outlines: focus is a soft fill. */
@Composable
fun DropdownRow(label: String, value: String, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, requester: FocusRequester? = null) {
    FocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(LocalRowCorner.current),
        focusedScale = 1f,
        ringWidth = 0.dp,
        background = Color.Transparent,
        focusedBackground = Color.White.copy(alpha = 0.12f),
        modifier = modifier.fillMaxWidth().let { if (requester != null) it.focusRequester(requester) else it },
    ) { focused ->
        Row(Modifier.padding(horizontal = 10.dp, vertical = (9 * LocalPanelTextScale.current / 1.18f).dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = BlushType.label.copy(fontWeight = FontWeight.W600, fontSize = BlushType.label.fontSize * LocalPanelTextScale.current), color = if (focused) Blush.colors.text else Blush.colors.textSoft, modifier = Modifier.weight(1f))
            Text(value, style = BlushType.caption.copy(fontSize = BlushType.caption.fontSize * LocalPanelTextScale.current), color = if (expanded) Blush.colors.accent.color else Blush.colors.textMuted, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
            Icon(Icons.ChevronDown, null, Blush.colors.textDim, Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f }, size = 11.dp)
        }
    }
}

/** One choice under an open dropdown, indented; the chosen one is in the accent colour with a tick. */
@Composable
fun OptionRow(title: String, detail: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(LocalRowCorner.current),
        focusedScale = 1f,
        ringWidth = 0.dp,
        background = Color.Transparent,
        focusedBackground = Color.White.copy(alpha = 0.12f),
        modifier = modifier.fillMaxWidth().padding(start = LocalOptionIndent.current),
    ) { focused ->
        Row(Modifier.padding(horizontal = 10.dp, vertical = (6 * LocalPanelTextScale.current / 1.18f).dp), verticalAlignment = Alignment.CenterVertically) {
            // Text too long for the row slides along while the remote is on it; at the end it waits a second, then starts over.
            Column(Modifier.weight(1f).clipToBounds()) {
                SlidingText(
                    title, BlushType.label.copy(fontWeight = FontWeight.W500, fontSize = BlushType.label.fontSize * LocalPanelTextScale.current),
                    if (selected) Blush.colors.accent.color else Blush.colors.text, focused,
                )
                if (detail != null && detail != title) SlidingText(detail, BlushType.caption, Blush.colors.textMuted, focused)
            }
            if (selected) Icon(Icons.Check, null, Blush.colors.accent.color, size = 12.dp)
        }
    }
}

/** A value changed with Left / Right (each press calls [onStep] with -1 or +1); OK calls [onClick]. */
@Composable
fun StepperRow(label: String, value: String, detail: String?, onStep: (Int) -> Unit, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    FocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(LocalRowCorner.current),
        focusedScale = 1f,
        ringWidth = 0.dp,
        background = Color.Transparent,
        focusedBackground = Color.White.copy(alpha = 0.12f),
        modifier = modifier.fillMaxWidth().onPreviewKeyEvent { e ->
            val dir = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
            if (dir == 0) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyDown) onStep(dir)
            true
        },
    ) { focused ->
        Row(Modifier.padding(horizontal = 10.dp, vertical = (7 * LocalPanelTextScale.current / 1.18f).dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = BlushType.label.copy(fontWeight = FontWeight.W600, fontSize = BlushType.label.fontSize * LocalPanelTextScale.current), color = if (focused) Blush.colors.text else Blush.colors.textSoft)
                if (detail != null && focused) Text(detail, style = BlushType.caption, color = Blush.colors.textDim)
            }
            Icon(Icons.Back, null, if (focused) Blush.colors.textSoft else Blush.colors.textDim, size = 10.dp)
            Text(value, style = BlushType.caption.copy(fontSize = BlushType.caption.fontSize * LocalPanelTextScale.current), color = Blush.colors.accent.color, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
            Icon(Icons.ChevronRight, null, if (focused) Blush.colors.textSoft else Blush.colors.textDim, size = 10.dp)
        }
    }
}

/** A hairline between groups of rows. */
@Composable
fun PanelDivider() {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).size(width = 0.dp, height = 1.dp).background(Color.White.copy(alpha = 0.08f)))
}

/** A setting that's simply on or off: its name (and an optional note) with a small switch on the right. */
@Composable
fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    FocusSurface(
        onClick = onToggle,
        shape = RoundedCornerShape(LocalRowCorner.current),
        focusedScale = 1f,
        ringWidth = 0.dp,
        background = Color.Transparent,
        focusedBackground = Color.White.copy(alpha = 0.12f),
        modifier = modifier.fillMaxWidth(),
    ) { focused ->
        Row(Modifier.padding(horizontal = 10.dp, vertical = (8 * LocalPanelTextScale.current / 1.18f).dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = BlushType.label.copy(fontWeight = FontWeight.W600, fontSize = BlushType.label.fontSize * LocalPanelTextScale.current), color = if (focused) Blush.colors.text else Blush.colors.textSoft)
                if (detail != null) Text(detail, style = BlushType.caption.copy(fontSize = BlushType.caption.fontSize * LocalPanelTextScale.current), color = Blush.colors.textMuted)
            }
            Switch(checked)
        }
    }
}

@Composable
fun Switch(checked: Boolean) {
    val accent = Blush.colors.accent.color
    val travel by androidx.compose.animation.core.animateDpAsState(if (checked) 16.dp else 0.dp, androidx.compose.animation.core.tween(160), label = "switchThumb")
    val track by androidx.compose.animation.animateColorAsState(if (checked) accent else Color.White.copy(alpha = 0.16f), androidx.compose.animation.core.tween(160), label = "switchTrack")
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(width = 36.dp, height = 20.dp)
            .background(track, RoundedCornerShape(50)),
        contentAlignment = Alignment.CenterStart,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .padding(start = 2.dp)
                .offset(x = travel)
                .size(16.dp)
                .background(if (checked) Blush.colors.accent.onColor else Color.White.copy(alpha = 0.9f), CircleShape),
        )
    }
}

/** One line of text; while [sliding] and too long, it scrolls to its end, pauses for a second, then jumps back to the start. */
@Composable
private fun SlidingText(text: String, style: androidx.compose.ui.text.TextStyle, color: Color, sliding: Boolean) {
    if (!sliding) {
        Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    val scroll = androidx.compose.foundation.rememberScrollState()
    val pxPerSecond = with(androidx.compose.ui.platform.LocalDensity.current) { 36.dp.toPx() }
    androidx.compose.runtime.LaunchedEffect(text) {
        // Only text that doesn't fit has anything to scroll.
        androidx.compose.runtime.snapshotFlow { scroll.maxValue }.first { it > 0 }
        kotlinx.coroutines.delay(500)
        while (true) {
            val end = scroll.maxValue
            scroll.animateScrollTo(end, androidx.compose.animation.core.tween((end / pxPerSecond * 1000).toInt().coerceAtLeast(200), easing = androidx.compose.animation.core.LinearEasing))
            kotlinx.coroutines.delay(1000)
            scroll.scrollTo(0)
            kotlinx.coroutines.delay(600)
        }
    }
    Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.horizontalScroll(scroll, enabled = false))
}
