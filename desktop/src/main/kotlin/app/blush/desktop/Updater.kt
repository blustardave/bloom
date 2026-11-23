package app.blush.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Bloom updates itself from the GitHub release page. A file downloaded by a web browser is marked by macOS as "from the
 * internet", which is why each new copy needed Privacy & Security → Open Anyway. A download made by Bloom itself isn't
 * marked, so the new copy just opens.
 */
object Updater {
    private const val Repo = "blustardave/bloom"
    private const val ZipUrl = "https://github.com/$Repo/releases/download/latest/Bloom-mac.zip"
    private const val ReleaseApi = "https://api.github.com/repos/$Repo/releases/tags/latest"

    private val http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    /** Where Bloom.app is (passed by its launcher), or null when not running from an app bundle. */
    private val appBundle: File? get() = System.getProperty("bloom.app")?.let(::File)?.takeIf { it.name.endsWith(".app") && it.isDirectory }

    /** The Mac app's version on the release page ("0.3.170"), read from its notes line "Mac app: 0.3.170 (…)". */
    suspend fun latestVersion(): String? = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(URI(ReleaseApi)).header("Accept", "application/vnd.github+json").timeout(Duration.ofSeconds(15)).build()
        val body = http.send(request, HttpResponse.BodyHandlers.ofString()).body()
        Regex("""Mac app: (\d+(?:\.\d+)+)""").find(body)?.groupValues?.get(1)
    }

    /** True when [latest] is newer than [current] (compared number by number). */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    sealed interface Result {
        data object UpToDate : Result
        data class Installing(val version: String) : Result
        data class Failed(val why: String) : Result
    }

    /**
     * Checks the release page; if there's a newer Mac app, downloads it, unpacks it next to the running one and hands over
     * to a small script that swaps the two and opens the new one once this copy has quit. The caller then quits.
     */
    suspend fun update(progress: (String) -> Unit): Result = withContext(Dispatchers.IO) {
        runCatching {
            val latest = latestVersion() ?: return@runCatching Result.Failed("Couldn't read the release page.")
            if (!isNewer(latest, bloomVersion)) return@runCatching Result.UpToDate
            val app = appBundle ?: return@runCatching Result.Failed("Updating only works from Bloom.app.")
            progress("Downloading Bloom $latest…")
            val work = File(System.getProperty("java.io.tmpdir"), "bloom-update-${System.nanoTime()}").apply { mkdirs() }
            val zip = File(work, "Bloom-mac.zip")
            val response = http.send(HttpRequest.newBuilder(URI(ZipUrl)).timeout(Duration.ofMinutes(10)).build(), HttpResponse.BodyHandlers.ofFile(zip.toPath()))
            if (response.statusCode() != 200 || zip.length() < 1_000_000) return@runCatching Result.Failed("The download didn't complete.")
            progress("Unpacking…")
            // ditto is macOS's own unzip that keeps the app's links and permissions intact.
            val unzip = ProcessBuilder("/usr/bin/ditto", "-x", "-k", zip.path, work.path).redirectErrorStream(true).start()
            if (unzip.waitFor() != 0 || !File(work, "Bloom.app/Contents/MacOS/Bloom").exists()) return@runCatching Result.Failed("The download couldn't be unpacked.")
            // Swap once this copy has quit (its own files are in use until then), then open the new one.
            val pid = ProcessHandle.current().pid()
            val script = File(work, "swap.sh")
            script.writeText(
                """
                #!/bin/sh
                while kill -0 $pid 2>/dev/null; do sleep 0.3; done
                rm -rf "${app.path}.old"
                mv "${app.path}" "${app.path}.old" && mv "${work.path}/Bloom.app" "${app.path}" && rm -rf "${app.path}.old"
                open "${app.path}"
                rm -rf "${work.path}"
                """.trimIndent(),
            )
            ProcessBuilder("/bin/sh", "-c", "nohup /bin/sh '${script.path}' >/dev/null 2>&1 &").start().waitFor()
            Result.Installing(latest)
        }.getOrElse { Result.Failed(it.message ?: "Couldn't update.") }
    }
}
