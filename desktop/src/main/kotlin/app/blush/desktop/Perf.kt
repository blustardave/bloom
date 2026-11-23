package app.blush.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * View → Show performance (⌥⌘P): how long the Mac app takes to answer the pointer, measured inside Bloom.
 * "Click → drawn": from the button going down to Bloom finishing the picture that shows it (the screen adds ~1 frame more).
 * "Pointer → drawn": the same for hovering. "Drawing": how long one picture takes to draw. Each shows the last and the worst.
 */
object Perf {
    var shown by mutableStateOf(false)

    /** Bumped on each click / pointer change while the panel is shown, so the window's root redraws in that same frame and
     *  the timing sees it (tiles draw in their own layers, which the root doesn't otherwise notice). */
    internal var tick by mutableStateOf(0)
    internal var pressAt = 0L
    internal var moveAt = 0L
    internal var over: String? = null
    var clickMs = 0.0; var clickWorst = 0.0
    var hoverMs = 0.0; var hoverWorst = 0.0
    var drawMs = 0.0; var drawWorst = 0.0
    var frames = 0
    private val started = System.nanoTime()

    fun report(): String {
        val up = (System.nanoTime() - started) / 1e9
        return "Click → drawn   %.1f ms (worst %.1f)\nPointer → drawn %.1f ms (worst %.1f)\nDrawing         %.1f ms (worst %.1f)\nPictures drawn  %d · open %.0f s"
            .format(clickMs, clickWorst, hoverMs, hoverWorst, drawMs, drawWorst, frames, up)
    }
}

@Composable
fun BoxScope.PerfOverlay() {
    if (!Perf.shown) return
    var text by androidx.compose.runtime.remember { mutableStateOf(Perf.report()) }
    LaunchedEffect(Unit) { while (true) { delay(300); text = Perf.report() } }
    Box(
        Modifier.align(Alignment.TopEnd).padding(top = 36.dp, end = 12.dp)
            .background(Color(0xD0000000), RoundedCornerShape(8.dp)).padding(10.dp),
    ) {
        Text(text, style = DesktopType.caption.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = Color(0xFFE8E8EA))
    }
}

/** Watches the pointer (without taking it) and times every picture drawn under it. Put on the window's root. */
fun Modifier.perfMeasured(): Modifier = this
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                when (e.type) {
                    PointerEventType.Press -> { Perf.pressAt = System.nanoTime(); if (Perf.shown) Perf.tick++ }
                    PointerEventType.Move, PointerEventType.Enter, PointerEventType.Exit -> {
                        // Only moves that change which control is under the pointer change the picture.
                        val pos = e.changes.first().position
                        val over = PointerProbe.bounds.entries.firstOrNull { it.value.contains(pos) }?.key
                        if (over != Perf.over) { Perf.over = over; if (Perf.moveAt == 0L) Perf.moveAt = System.nanoTime(); if (Perf.shown) Perf.tick++ }
                    }
                }
            }
        }
    }
    .drawWithContent {
        Perf.tick // read, so a bump redraws this
        val t = System.nanoTime()
        drawContent()
        val now = System.nanoTime()
        Perf.frames++
        Perf.drawMs = (now - t) / 1e6; if (Perf.frames > 3) Perf.drawWorst = maxOf(Perf.drawWorst, Perf.drawMs)
        if (Perf.pressAt != 0L) { Perf.clickMs = (now - Perf.pressAt) / 1e6; Perf.clickWorst = maxOf(Perf.clickWorst, Perf.clickMs); Perf.pressAt = 0L }
        if (Perf.moveAt != 0L) {
            val ms = (now - Perf.moveAt) / 1e6
            if (ms < 500) { Perf.hoverMs = ms; Perf.hoverWorst = maxOf(Perf.hoverWorst, ms) }
            Perf.moveAt = 0L
        }
    }
