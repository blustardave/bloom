package app.blush.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

// Mouse and trackpad first. On the Mac nothing has "focus" the way a TV tile does: things light up under the pointer,
// the pointer turns into a hand over anything clickable, and a click acts at once. The keyboard is a shortcut layer
// on top (Space, arrows, Esc, F), not the main way round.

/** Where each named control was last drawn, so the screenshot run can move the mouse onto it (see Shots.kt). */
object PointerProbe {
    val bounds = HashMap<String, Rect>()
}

/**
 * A small pill-shaped choice: quiet at rest, brighter under the pointer, white when [selected]. Presses dip slightly so a
 * trackpad tap feels answered.
 */
@Composable
fun HoverPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, probe: String = label) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val bg by animateColorAsState(
        when {
            selected -> Color(0xFFF5F5F7)
            hovered -> Color.White.copy(alpha = 0.16f)
            else -> Color.White.copy(alpha = 0.07f)
        },
        tween(120),
    )
    val fg by animateColorAsState(
        when {
            selected -> Color(0xFF17181F)
            hovered -> Color(0xFFF5F5F7)
            else -> Color(0xFFAEB1BB)
        },
        tween(120),
    )
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(90))
    Box(
        modifier
            .onGloballyPositioned { PointerProbe.bounds[probe] = it.boundsInRoot() }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(28.dp)
            .background(bg, RoundedCornerShape(50))
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = DesktopType.label, color = fg, maxLines = 1)
    }
}
