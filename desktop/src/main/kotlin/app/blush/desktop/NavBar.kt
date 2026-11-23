package app.blush.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blush.tv.ui.components.Icon
import app.blush.tv.ui.components.Icons

// The TV's navigation bar on the Mac: a Search circle at the left, a pill in the middle with the pages (Home, pinned
// libraries, Libraries, Watchlist) and the profile picture at the right. In the pill a white highlight rests, dimmed, on
// the page you're on; under the pointer it brightens and slides to the name you're over (on the TV it follows the remote).

private val ItemHeight = 32.dp
private val PillHeight = ItemHeight + 6.dp
private val CircleSize = PillHeight
private val Ink = Color(0xFF14151B)
private val PillColour = Color(0xFF1E1F25)
/** The faint light along the top of the pill and the Search circle, as on the TV. */
private val TopHighlight = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 0.45f to Color.White.copy(alpha = 0.04f), 1f to Color.Transparent)

@Composable
fun NavBar(
    current: String,
    pins: List<Pair<String, String>>,
    userName: String,
    userImage: String?,
    onPick: (String) -> Unit,
    onSwitchProfile: () -> Unit,
    onSignOut: () -> Unit,
    onChangeServer: () -> Unit,
) {
    val items = listOf("Home" to "Home") + pins + listOf("Libraries" to "Libraries", "Watchlist" to "Watchlist")
    Box(Modifier.fillMaxWidth().height(64.dp)) {
        LocalDragArea.current(Modifier.fillMaxSize())
        // Search: a circle at the left (after the window's own buttons).
        Box(Modifier.align(Alignment.CenterStart).padding(start = 84.dp)) {
            CircleButton(selected = current == "Search", onClick = { onPick("Search") }) { ink ->
                Icon(Icons.Search, "Search (⌘F)", ink, size = 16.dp)
            }
        }
        Pill(items, current, onPick, Modifier.align(Alignment.Center))
        Box(Modifier.align(Alignment.CenterEnd).padding(end = PageSide - 2.dp)) {
            ProfileMenu(userName, userImage, onSwitchProfile, onSignOut, onChangeServer)
        }
    }
}

@Composable
private fun Pill(items: List<Pair<String, String>>, current: String, onPick: (String) -> Unit, modifier: Modifier) {
    val bounds = remember { mutableStateMapOf<String, Pair<Float, Float>>() } // key → (left, width) in px
    var hovered by remember { mutableStateOf<String?>(null) }
    val pillHover = remember { MutableInteractionSource() }
    val overPill by pillHover.collectIsHoveredAsState()
    val target = (if (overPill) hovered else null) ?: current
    val box = bounds[target] ?: bounds[current]
    // The highlight slides (a springy ease) and brightens while the pointer is in the pill.
    val left by animateFloatAsState(box?.first ?: 0f, spring(dampingRatio = 0.85f, stiffness = 520f), label = "cursorX")
    val width by animateFloatAsState(box?.second ?: 0f, spring(dampingRatio = 0.85f, stiffness = 520f), label = "cursorW")
    val bright by animateFloatAsState(if (overPill && hovered != null) 1f else 0f, tween(140), label = "cursorA")
    val grow by animateFloatAsState(if (overPill) 1f else 0.96f, tween(160), label = "pillGrow")
    val onPage = bounds.containsKey(current) || (overPill && hovered != null)
    Row(
        modifier
            .graphicsLayer { scaleX = grow; scaleY = grow }
            .shadow(10.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .background(PillColour, CircleShape)
            .background(TopHighlight, CircleShape)
            .hoverable(pillHover)
            .padding(3.dp)
            .height(ItemHeight)
            .drawBehind {
                if (box == null || !onPage) return@drawBehind
                val a = 0.22f + 0.78f * bright
                drawRoundRect(Color.White.copy(alpha = a), Offset(left, 0f), Size(width, size.height), CornerRadius(size.height / 2))
            },
    ) {
        items.forEach { (key, label) ->
            val h = rememberHover()
            androidx.compose.runtime.LaunchedEffect(h.hovered) { if (h.hovered) hovered = key }
            val lit = target == key && bright > 0.5f
            Box(
                Modifier
                    .onGloballyPositioned { bounds[key] = it.positionInParent().x to it.size.width.toFloat() }
                    .probe("nav-$key")
                    .mouseTarget(h) { onPick(key) }
                    .padding(horizontal = 16.dp)
                    .height(ItemHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = DesktopType.title.copy(fontSize = 13.sp, fontWeight = FontWeight.W600),
                    color = when {
                        lit -> Ink
                        key == current || h.lit -> Color.White
                        else -> Color(0xFFD5D7DE).copy(alpha = 0.8f)
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/** A round button the height of the pill: dark with the top highlight, white under the pointer or when its page is open. */
@Composable
private fun CircleButton(selected: Boolean, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    val h = rememberHover()
    val level by animateFloatAsState(if (h.lit) 1f else if (selected) 0.85f else 0f, tween(120), label = "circle")
    Box(
        Modifier.size(CircleSize)
            .shadow(8.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(lerp(PillColour, Color.White, level))
            .background(TopHighlight)
            .mouseTarget(h, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(if (level > 0.5f) Ink else Color.White) }
}

/** The profile picture as a circle the pill's height, ringed in white under the pointer. Used by [ProfileMenu]. */
@Composable
fun ProfileCircle(name: String, imageUrl: String?, lit: Boolean) {
    Box(Modifier.size(CircleSize).then(if (lit) Modifier.border(2.dp, Color.White, CircleShape) else Modifier).padding(if (lit) 3.dp else 0.dp)) {
        Avatar(name, imageUrl, if (lit) CircleSize - 6.dp else CircleSize)
    }
}
