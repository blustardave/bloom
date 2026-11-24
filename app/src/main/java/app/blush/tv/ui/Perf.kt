package app.blush.tv.ui

import android.os.Debug
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.theme.Text
import coil3.EventListener
import coil3.ImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Lite mode (see Settings): read by the screens that have something to turn down. */
object Lite {
    @Volatile var on = false
}

/**
 * Optional on-screen performance read-out (Settings → Appearance → Performance stats), for finding out what is making the app lag.
 * It measures how long every frame takes and what was going on at the time (scrolling a row, the page, a tile opening, a clip
 * playing), plus how many tiles are being built, how often the garbage collector runs and how long pictures take to load.
 */
object Perf {
    /** One line about what Home is doing in the background (shown in the stats). */
    @Volatile var homeNote: String = ""
    /** The last thing that went wrong with a tile preview, shown in the stats read-out. */
    @Volatile var previewNote: String = ""
    var enabled by mutableStateOf(false)
    /** Draw the read-out over every screen (off: it is shown in a pop-up in Settings instead). */
    var overlay = false

    /** A short label for what the current screen is doing right now (set by the screen, read once per frame). */
    @Volatile var context: (() -> String)? = null

    // Cheap counters, bumped from wherever the work happens.
    val tilesBuilt = AtomicInteger()
    val homeRecomposed = AtomicInteger()
    val imagesLoaded = AtomicInteger()
    val imagesFromMemory = AtomicInteger()
    val imageNanos = AtomicLong()

    /** Times pictures as they load (installed on the image loader). */
    private val imageKind = Regex("/Images/(\\w+)")
    val imageEvents = object : EventListener() {
        private val started = ConcurrentHashMap<ImageRequest, Long>()
        override fun onStart(request: ImageRequest) { if (enabled) started[request] = System.nanoTime() }
        override fun onSuccess(request: ImageRequest, result: SuccessResult) {
            val t = started.remove(request) ?: return
            imagesLoaded.incrementAndGet()
            if (result.dataSource == coil3.decode.DataSource.MEMORY_CACHE) imagesFromMemory.incrementAndGet()
            else imageNanos.addAndGet(System.nanoTime() - t)
        }
        override fun onError(request: ImageRequest, result: ErrorResult) {
            started.remove(request)
            // A picture that didn't load goes in Settings → Recent errors, under what kind of picture it was (Primary is the
            // poster, Backdrop the background, Logo, Thumb), so a run of failures shows up as one line with a count.
            val url = request.data.toString()
            val kind = imageKind.find(url)?.groupValues?.get(1) ?: "other"
            app.blush.tv.data.ErrorLog.record("Image ($kind)", result.throwable)
        }
        override fun onCancel(request: ImageRequest) { started.remove(request) }
    }
}

private class Bucket {
    val frames = FloatArray(400)
    var n = 0
    var count = 0
    fun add(ms: Float) {
        frames[n % frames.size] = ms; n++; count = minOf(count + 1, frames.size)
        if (ms > 33f) hitches++
        if (ms > worst) worst = ms
    }
    var hitches = 0   // frames over 33 ms
    var worst = 0f
    fun average() = if (count == 0) 0f else frames.take(count).average().toFloat()
    fun p95(): Float {
        if (count == 0) return 0f
        val sorted = frames.take(count).sorted()
        return sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.size - 1)]
    }
}

private fun gcStat(name: String) = runCatching { Debug.getRuntimeStat(name).toLong() }.getOrDefault(0L)

/** The statistics as text, measured live while the caller is on screen (the Settings pop-up shows it). */
@Composable
fun perfText(): String {
    var text by remember { mutableStateOf("Measuring…") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    LaunchedEffect(Unit) {
        val buckets = linkedMapOf<String, Bucket>()
        val worst = ArrayDeque<String>()
        var last = 0L
        var windowStart = 0L
        var framesInWindow = 0
        var worstInWindow = 0f
        var lastGc = gcStat("art.gc.gc-count"); var lastGcMs = gcStat("art.gc.gc-time")
        var lastTiles = Perf.tilesBuilt.get(); var lastRecomp = Perf.homeRecomposed.get()
        var lastImages = Perf.imagesLoaded.get(); var lastMem = Perf.imagesFromMemory.get(); var lastImgNs = Perf.imageNanos.get()
        val start = System.nanoTime()
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            if (last != 0L) {
                val ms = (now - last) / 1_000_000f
                val label = Perf.context?.invoke() ?: "other"
                buckets.getOrPut(label) { Bucket() }.add(ms)
                framesInWindow++
                if (ms > worstInWindow) worstInWindow = ms
                if (ms > 40f) {
                    worst.addFirst("%3.0f ms  %s  (at %ds)".format(ms, label, (System.nanoTime() - start) / 1_000_000_000L))
                    while (worst.size > 6) worst.removeLast()
                }
            }
            last = now
            if (windowStart == 0L) windowStart = now
            if (now - windowStart >= 1_000_000_000L) {
                val seconds = (now - windowStart) / 1e9f
                val gc = gcStat("art.gc.gc-count"); val gcMs = gcStat("art.gc.gc-time")
                val tiles = Perf.tilesBuilt.get(); val recomp = Perf.homeRecomposed.get()
                val images = Perf.imagesLoaded.get(); val mem = Perf.imagesFromMemory.get(); val imgNs = Perf.imageNanos.get()
                val loadedFresh = (images - lastImages) - (mem - lastMem)
                val rt = Runtime.getRuntime()
                text = buildString {
                    appendLine("Bloom $version")
                    appendLine("%.0f fps   worst frame %.0f ms".format(framesInWindow / seconds, worstInWindow))
                    appendLine("by activity: avg / slowest 5% / worst, hitches>33ms")
                    buckets.entries.sortedByDescending { it.value.hitches }.take(7).forEach { (k, b) ->
                        appendLine("  %-24s %3.0f/%3.0f/%3.0f ms  %d of %d".format(k, b.average(), b.p95(), b.worst, b.hitches, b.n))
                    }
                    appendLine("tiles built %d/s   Home redraws %d/s".format(((tiles - lastTiles) / seconds).toInt(), ((recomp - lastRecomp) / seconds).toInt()))
                    appendLine("GC %d runs, %d ms   heap %d / %d MB".format(gc - lastGc, gcMs - lastGcMs, (rt.totalMemory() - rt.freeMemory()) shr 20, rt.maxMemory() shr 20))
                    appendLine("pictures %d fresh (avg %d ms), %d from memory".format(loadedFresh, if (loadedFresh > 0) (imgNs - lastImgNs) / loadedFresh / 1_000_000 else 0, mem - lastMem))
                    if (Perf.homeNote.isNotEmpty()) appendLine(Perf.homeNote)
                    if (Perf.previewNote.isNotEmpty()) appendLine("preview: ${Perf.previewNote}")
                    if (worst.isNotEmpty()) {
                        appendLine("slowest recent frames:")
                        worst.forEach { appendLine("  $it") }
                    }
                }.trimEnd()
                lastGc = gc; lastGcMs = gcMs; lastTiles = tiles; lastRecomp = recomp
                lastImages = images; lastMem = mem; lastImgNs = imgNs
                windowStart = now; framesInWindow = 0; worstInWindow = 0f
            }
        }
    }
    return text
}

@Composable
fun PerfOverlay() {
    if (!Perf.enabled || !Perf.overlay) return
    val text = perfText()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        Text(
            text,
            style = BlushType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp),
            color = Color.White,
            modifier = Modifier.padding(top = 8.dp, end = 8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xCC000000)).padding(8.dp),
        )
    }
}
