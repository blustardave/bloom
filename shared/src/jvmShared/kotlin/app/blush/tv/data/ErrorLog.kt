package app.blush.tv.data

import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Remembers things that went wrong but didn't crash the app (a server call that failed, a picture that wouldn't load...).
 *
 * Before this, those failures were caught and thrown away, so a row that stayed empty or a button that did nothing left no trace.
 * Now each one is kept here (the newest 200, repeats folded together with a count) and saved to a file so they survive a restart.
 * Settings → Recent errors shows them, so they can be photographed and sent, like the crash screen.
 */
object ErrorLog {
    class Entry(
        /** Where in Bloom it happened, e.g. "Repo.resume" or "HomeScreen.load". */
        val where: String,
        /** What went wrong: the error's type and message. */
        val what: String,
        /** The first few lines of Bloom's own code it went through (file and line), to find it again. */
        val trace: String,
        val firstAt: Long,
        var lastAt: Long,
        var count: Int,
    )

    private const val Max = 200
    private val entries = ArrayDeque<Entry>()
    private val lock = Any()
    private var file: File? = null
    // Saving happens off the calling thread (often the main one) and one at a time.
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "bloom-errorlog").apply { isDaemon = true } }
    @Volatile private var savePending = false

    /** Called once at start-up with the app's private folder. Without it, errors are still kept for this session. */
    fun init(dir: File) {
        val f = File(dir, "recent_errors.txt")
        synchronized(lock) {
            file = f
            entries.clear()
            runCatching { if (f.exists()) parse(f.readText()).forEach { entries.addLast(it) } }
        }
    }

    fun record(where: String, error: Throwable) {
        // Being cancelled is normal (you moved on before something finished); a timeout is a real failure worth keeping.
        if (error is CancellationException && error !is TimeoutCancellationException) return
        val what = describe(error)
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val same = entries.firstOrNull { it.where == where && it.what == what }
            if (same != null) {
                same.count++
                same.lastAt = now
                entries.remove(same)
                entries.addFirst(same)
            } else {
                entries.addFirst(Entry(where, what, trace(error), now, now, 1))
                while (entries.size > Max) entries.removeLast()
            }
        }
        scheduleSave()
    }

    /** Newest first. */
    fun entries(): List<Entry> = synchronized(lock) { entries.toList() }

    fun clear() {
        synchronized(lock) { entries.clear() }
        scheduleSave()
    }

    private fun describe(e: Throwable): String {
        val parts = generateSequence(e) { it.cause.takeIf { c -> c !== it } }.take(3)
            .map { t -> t::class.java.simpleName + (t.message?.takeIf { it.isNotBlank() }?.let { ": " + it.take(300) } ?: "") }
        return parts.joinToString("\n  caused by ")
    }

    private fun trace(e: Throwable): String {
        val ours = e.stackTrace.filter { it.className.startsWith("app.blush") }.take(4)
        val frames = ours.ifEmpty { e.stackTrace.take(3).toList() }
        return frames.joinToString("\n") { "${it.className.substringAfterLast('.')}.${it.methodName} (${it.fileName}:${it.lineNumber})" }
    }

    // Saving is folded: a burst of errors causes one write shortly after, not one per error.
    private fun scheduleSave() {
        if (file == null || savePending) return
        savePending = true
        writer.execute {
            Thread.sleep(1_000)
            savePending = false
            val f = file ?: return@execute
            val text = synchronized(lock) { entries.joinToString(Separator) { serialise(it) } }
            runCatching { f.writeText(text) }
        }
    }

    private const val Separator = "\n\u001E\n"
    private fun serialise(e: Entry) = listOf(e.where, e.firstAt, e.lastAt, e.count, e.what.replace("\u001F", " "), e.trace)
        .joinToString("\u001F")

    private fun parse(text: String): List<Entry> = text.split(Separator).mapNotNull { block ->
        val p = block.split("\u001F")
        if (p.size < 6) return@mapNotNull null
        Entry(p[0], p[4], p[5], p[1].toLongOrNull() ?: return@mapNotNull null, p[2].toLongOrNull() ?: 0L, p[3].toIntOrNull() ?: 1)
    }.take(Max)
}

/**
 * Like Kotlin's `runCatching`, with exactly the same result, except a failure is also written to [ErrorLog]
 * under [where] instead of disappearing.
 */
inline fun <T> logged(where: String, block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: Throwable) {
        ErrorLog.record(where, e)
        Result.failure(e)
    }
