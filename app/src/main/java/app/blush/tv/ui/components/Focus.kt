package app.blush.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay

/**
 * Remembers which tile you were on, so coming back from a details page (or out of the side bar)
 * puts you back on the same tile.
 */
class FocusMemory {
    var key: String? = null
    var pending: Boolean = false
    internal val requesters = mutableMapOf<String, FocusRequester>()

    fun remember(key: String) {
        this.key = key
    }

    /** Call when leaving the screen. */
    fun leaving() {
        pending = key != null
    }

    /** Moves focus to the tile with this key, if it's on screen. */
    fun requestKey(key: String): Boolean {
        val requester = requesters[key] ?: return false
        return runCatching { requester.requestFocus() }.getOrDefault(false)
    }

    /** Moves focus straight back to the remembered tile, if it's on screen. */
    fun restoreNow(): Boolean {
        val requester = key?.let { requesters[it] } ?: return false
        return runCatching { requester.requestFocus() }.getOrDefault(false)
    }
}

@Composable
fun Modifier.restorableFocus(memory: FocusMemory, key: String, isDefault: Boolean = false): Modifier {
    val requester = remember { FocusRequester() }
    DisposableEffect(key) {
        memory.requesters[key] = requester
        onDispose { if (memory.requesters[key] === requester) memory.requesters.remove(key) }
    }
    // Only the one tile that has something to restore starts an effect; the rest cost nothing here.
    val restoreHere = memory.pending && memory.key == key
    val defaultHere = !memory.pending && memory.key == null && isDefault
    if (restoreHere || defaultHere) {
        LaunchedEffect(key) {
            delay(30)
            if (runCatching { requester.requestFocus() }.getOrDefault(false)) memory.pending = false
        }
    }
    return this.focusRequester(requester)
}
