package app.blush.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blush.tv.ui.components.Icon
import app.blush.tv.ui.components.Icons
import kotlinx.coroutines.launch

// The navigation bar, designed for a pointer rather than the TV's remote:
// - Bloom's logo at the left, below and clear of the window's red/yellow/green buttons; a round Back button beside it
//   only when there's somewhere to go back to.
// - A flat dark pill in the middle with the pages. The page you're on is always a solid white pill with dark text (never
//   in doubt); the name under the pointer gets a faint highlight at once; clicking slides the white pill to the new page.
//   (The TV's highlight follows the remote; following a mouse it would lag behind and distract.)
// - Search and the profile picture at the right.

/** The bar's height; pages that start below it leave this much room. */
val BarHeight = 80.dp
private val ItemHeight = 34.dp
private val CircleSize = 42.dp
private val Ink = Color(0xFF14151B)
private val Glass = Color(0xE6292A2F)
private val Edge = Color.White.copy(alpha = 0.07f)

@Composable
fun NavBar(
    current: String,
    pins: List<Pair<String, String>>,
    userName: String,
    userImage: String?,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
    onSwitchProfile: () -> Unit,
    onSignOut: () -> Unit,
    onChangeServer: () -> Unit,
) {
    val items = listOf("Home" to "Home") + pins + listOf("Libraries" to "Libraries", "Watchlist" to "Watchlist")
    Box(Modifier.fillMaxWidth().height(BarHeight)) {
        LocalDragArea.current(Modifier.fillMaxSize())
        Row(Modifier.align(Alignment.CenterStart).padding(start = PageSide, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val mark = remember { resourceImage("bloom_wordmark.png") }
            Image(mark, contentDescription = "Bloom", modifier = Modifier.height(24.dp).aspectRatio(430f / 112f))
            if (canGoBack) {
                Spacer(Modifier.width(18.dp))
                Circle(selected = false, onClick = onBack, size = 34.dp, probe = "nav-back") { ink -> Icon(Icons.Back, "Back (⌘[)", ink, size = 15.dp) }
            }
        }
        Pill(items, current, onPick, Modifier.align(Alignment.Center).padding(top = 12.dp))
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = PageSide - 2.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Circle(selected = current == "Search", onClick = { onPick("Search") }, probe = "nav-Search") { ink -> Icon(Icons.Search, "Search (⌘F)", ink, size = 17.dp) }
            ProfileMenu(userName, userImage, onSwitchProfile, onSignOut, onChangeServer)
        }
    }
}

@Composable
private fun Pill(items: List<Pair<String, String>>, current: String, onPick: (String) -> Unit, modifier: Modifier) {
    val bounds = remember { mutableStateMapOf<String, Pair<Float, Float>>() } // key → (left, width) in px
    // The white pill slides between pages, but appears in place (rather than sliding in from nowhere) when you come from
    // a page that isn't in the bar, such as Search or a title page.
    val left = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    val shown = remember { Animatable(0f) }
    val target = bounds[current]
    LaunchedEffect(target, current) {
        if (target == null) { shown.snapTo(0f); return@LaunchedEffect }
        if (shown.value < 0.5f) {
            left.snapTo(target.first); width.snapTo(target.second); shown.snapTo(1f)
        } else {
            launch { left.animateTo(target.first, spring(dampingRatio = 0.9f, stiffness = 600f)) }
            width.animateTo(target.second, spring(dampingRatio = 0.9f, stiffness = 600f))
        }
    }
    Row(
        modifier
            .shadow(14.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .background(Glass, CircleShape)
            .border(1.dp, Edge, CircleShape)
            .padding(4.dp)
            .height(ItemHeight)
            .drawBehind {
                if (shown.value < 0.5f) return@drawBehind
                drawRoundRect(Color.White, Offset(left.value, 0f), Size(width.value, size.height), CornerRadius(size.height / 2))
            },
    ) {
        items.forEach { (key, label) ->
            val h = rememberHover()
            val selected = key == current
            Box(
                Modifier
                    .onGloballyPositioned { bounds[key] = it.positionInParent().x to it.size.width.toFloat() }
                    .probe("nav-$key")
                    .clip(CircleShape)
                    .background(if (h.lit && !selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                    .mouseTarget(h) { onPick(key) }
                    .padding(horizontal = 18.dp)
                    .height(ItemHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = DesktopType.title.copy(fontSize = 13.sp, fontWeight = FontWeight.W600),
                    color = when {
                        selected && shown.value > 0.5f -> Ink
                        h.lit || selected -> Color.White
                        else -> Color(0xFFB4B6BE)
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/** A round button in the bar: the pill's dark glass, lighter under the pointer, white when its page is open. */
@Composable
private fun Circle(selected: Boolean, onClick: () -> Unit, size: Dp = CircleSize, probe: String, content: @Composable (Color) -> Unit) {
    val h = rememberHover()
    val white = selected || h.pressed
    Box(
        Modifier.size(size).probe(probe)
            .shadow(10.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(if (white) Color.White else if (h.lit) Color(0xFF3A3B41) else Glass)
            .border(1.dp, Edge, CircleShape)
            .mouseTarget(h, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(if (white) Ink else Color.White) }
}

/** The profile picture in the bar, ringed in white under the pointer. Used by [ProfileMenu]. */
@Composable
fun ProfileCircle(name: String, imageUrl: String?, lit: Boolean) {
    Box(Modifier.size(CircleSize).then(if (lit) Modifier.border(2.dp, Color.White, CircleShape) else Modifier).padding(if (lit) 3.dp else 0.dp)) {
        Avatar(name, imageUrl, if (lit) CircleSize - 6.dp else CircleSize)
    }
}
