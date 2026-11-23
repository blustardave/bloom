package app.blush.tv.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.model.api.BaseItemDto

/** The total reported while the end of a [FilteredPager] hasn't been reached (the real count isn't known yet). */
const val UnknownCount = Int.MAX_VALUE

/**
 * Pages through a list the server can only partly filter. The server is asked for the titles a hundred at a time, in order, and the
 * ones that fail [keep] are dropped here, until the page asked for is full. It keeps its own place in the server's list, so the start
 * index it is given only matters when it is 0 (which begins again from the top). One pager serves one list at a time.
 */
class FilteredPager(
    private val fetch: suspend (start: Int, count: Int) -> Pair<List<BaseItemDto>, Int>,
    private val keep: (BaseItemDto) -> Boolean,
) {
    private val lock = Mutex()
    private var cursor = 0
    private var matched = 0
    private var exhausted = false
    // Titles already handed out (the server's order can be a shuffle, which repeats titles from one request to the next).
    private val seen = HashSet<java.util.UUID>()
    // Matches found beyond the number asked for, kept for the next page.
    private val spare = ArrayDeque<BaseItemDto>()


    suspend fun load(start: Int, count: Int): Pair<List<BaseItemDto>, Int> = lock.withLock {
        if (start == 0) { cursor = 0; matched = 0; exhausted = false; seen.clear(); spare.clear() }
        val out = ArrayList<BaseItemDto>()
        while (out.size < count && spare.isNotEmpty()) out += spare.removeFirst()
        var requests = 0
        while (out.size < count && !exhausted) {
            // A very strict filter on a big library could mean a long search; give up after a good stretch rather than leave the screen waiting.
            if (requests++ >= MaxRequests) { if (out.isEmpty()) exhausted = true; break }
            val (raw, total) = fetch(cursor, RawPage)
            cursor += raw.size
            if (raw.isEmpty() || cursor >= total) exhausted = true
            for (item in raw) if (keep(item) && seen.add(item.id)) { if (out.size < count) out += item else spare += item }
        }
        matched += out.size
        out to (if (exhausted && spare.isEmpty()) matched else UnknownCount)
    }

    private companion object {
        const val RawPage = 100
        const val MaxRequests = 10
    }
}
