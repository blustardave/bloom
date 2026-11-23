package app.blush.tv.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * How focus-driven scrolling moves. Compose's default is a spring, and every key press (a held key repeats many times a
 * second) starts a new spring from a standstill, so the page slows to nothing and speeds up again on each repeat: that is the
 * "wiggle". An ease-out tween starts fast, so a restart carries on at nearly the same speed and holding a key glides.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun glideSpec(durationMs: Int = 220): AnimationSpec<Float> = tween(durationMs, easing = LinearOutSlowInEasing)

/**
 * Only scroll when the focused tile would be cut off, and then just enough to show it with some breathing room.
 * (The TV default keeps the focused item at a fixed point, which pushed the home banner off the top.)
 */
@OptIn(ExperimentalFoundationApi::class)
object GentleSpec : BringIntoViewSpec by GentleScroll

@OptIn(ExperimentalFoundationApi::class)
private object GentleScroll : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = glideSpec(240)
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val margin = containerSize * 0.08f
        val end = offset + size
        return when {
            // Already fully on screen: don't move anything.
            offset >= 0f && end <= containerSize -> 0f
            size > containerSize - 2 * margin -> offset - margin
            offset < 0f -> offset - margin
            else -> end - (containerSize - margin)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GentleScrolling(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides GentleScroll, content = content)
}

/**
 * Keeps whatever has focus at a fixed spot: [pivot] is a fraction of the visible area, [startPx] a fixed offset.
 * Used so home rows stay lined up on the left and the focused row sits at the same height.
 */
@OptIn(ExperimentalFoundationApi::class)
class PivotScroll(
    private val pivot: Float = 0f,
    private val startPx: Float = 0f,
    deadZonePx: Float = 0f,
    durationMs: Int = 220,
    /** When set, nothing scrolls unless [arm] was called in the last moment (so sideways moves never nudge the page). */
    private val requireArm: Boolean = false,
) : BringIntoViewSpec {
    /** While this says true, focus never scrolls forward (e.g. a row that has already scrolled as far as it should). */
    var blockForward: () -> Boolean = { false }
    var deadZonePx: Float = deadZonePx
    private var armedUntil = 0L
    /** Allows scrolling for the next ~600 ms: call when focus is about to change rows. */
    fun arm() { armedUntil = System.nanoTime() + 600_000_000L }
    override val scrollAnimationSpec: AnimationSpec<Float> = glideSpec(durationMs)
    /** While true, focus moving doesn't scroll anything (the caller is doing its own, synchronised scroll). */
    var paused = false

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (paused) return 0f
        if (requireArm && System.nanoTime() > armedUntil) return 0f
        val distance = offset - (containerSize * pivot + startPx)
        if (distance > 0f && blockForward()) return 0f
        // Ignore tiny corrections (e.g. while moving along a row), which otherwise show as the page twitching.
        return if (kotlin.math.abs(distance) < deadZonePx) 0f else distance
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PivotScrolling(spec: BringIntoViewSpec, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/**
 * Like [GentleScrolling], but treats each focused tile as [extra] taller, for the title and details printed under it
 * (the focus box is only the picture). Scrolls only when the tile or its text would be cut off.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CaptionAwareScrolling(extra: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    val extraPx = with(androidx.compose.ui.platform.LocalDensity.current) { extra.toPx() }
    val spec = androidx.compose.runtime.remember(extraPx) {
        object : BringIntoViewSpec {
            override val scrollAnimationSpec: AnimationSpec<Float> = glideSpec(240)
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val margin = containerSize * 0.04f
                val end = offset + size + extraPx
                return when {
                    offset >= margin && end <= containerSize -> 0f
                    offset < margin -> offset - margin
                    else -> (end - containerSize).coerceAtMost(offset - margin)
                }
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/** Focus never scrolls this container: whoever owns it decides when and how far it moves. */
@OptIn(ExperimentalFoundationApi::class)
object FocusDoesNotScroll : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = glideSpec(190)
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
