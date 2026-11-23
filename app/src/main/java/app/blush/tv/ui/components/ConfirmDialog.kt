package app.blush.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.theme.Text
import kotlinx.coroutines.delay

private val PopupScale = 0.88f

/**
 * The look every pop-up shares with Settings: a dark rounded panel, a small bold title, and rows with the same 12dp rounded
 * highlight as the Settings rows. [swallowHeldOk]: the pop-up can open while OK is still held down (long-press), so held
 * repeats and that release are swallowed until a fresh press.
 */
@Composable
fun PopupPanel(onDismiss: () -> Unit, title: String, body: String? = null, swallowHeldOk: Boolean = true, width: androidx.compose.ui.unit.Dp = 230.dp, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        var released by remember { mutableStateOf(!swallowHeldOk) }
        Column(
            Modifier
                .width(width)
                .onPreviewKeyEvent { e ->
                    val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    if (!ok || released) false
                    else if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) { released = true; false }
                    else {
                        if (e.type == KeyEventType.KeyUp) released = true
                        true
                    }
                }
                .clip(RoundedCornerShape(18.dp))
                // See-through, with no outline: the page shows through, and only the row under the remote lights up.
                .background(Color(0x99101116))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = BlushType.label.copy(fontWeight = FontWeight.W700, fontSize = BlushType.label.fontSize * PopupScale * 1.1f), maxLines = 2)
                if (body != null) Text(body, style = BlushType.caption.copy(fontSize = BlushType.caption.fontSize * PopupScale), color = Blush.colors.textMuted)
            }
            content()
        }
    }
}

/** One row of a pop-up: Settings' action row (a label and, optionally, a line under it). */
@Composable
fun PopupRow(label: String, detail: String? = null, modifier: Modifier = Modifier, accent: Boolean = false, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        focusedScale = 1f,
        ringWidth = 0.dp,
        background = Color.Transparent,
        focusedBackground = Color.White.copy(alpha = 0.12f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = BlushType.label.copy(fontWeight = FontWeight.W600, fontSize = BlushType.label.fontSize * PopupScale), color = if (accent) Blush.colors.accent.color else Blush.colors.text)
            if (detail != null) Text(detail, style = BlushType.caption.copy(fontSize = BlushType.caption.fontSize * PopupScale), color = Blush.colors.textDim)
        }
    }
}

/** Asks the first row for the remote, repeating until it has it (a pop-up's rows are not placed the instant it appears). */
@Composable
fun rememberFirstFocus(): FocusRequester {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(15) {
            delay(40)
            if (runCatching { first.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    return first
}

/** A small yes/no pop-up. Cancel is selected first. */
@Composable
fun ConfirmDialog(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    PopupPanel(onDismiss, title, body) {
        val first = rememberFirstFocus()
        PopupRow("Cancel", modifier = Modifier.focusRequester(first), onClick = onDismiss)
        PopupRow(confirmLabel, accent = true, onClick = onConfirm)
    }
}

/** One line of a [TileMenu]. */
class MenuAction(val label: String, val run: () -> Unit)

/** The menu that opens when OK is held on a Home tile. */
@Composable
fun TileMenu(title: String, actions: List<MenuAction>, onDismiss: () -> Unit) {
    PopupPanel(onDismiss, title) {
        val first = rememberFirstFocus()
        actions.forEachIndexed { i, action ->
            PopupRow(action.label, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier, onClick = { onDismiss(); action.run() })
        }
        PopupRow("Cancel", onClick = onDismiss)
    }
}
