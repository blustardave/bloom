package app.blush.tv

import android.app.Application
import app.blush.tv.data.Repo
import app.blush.tv.data.Store
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.allowRgb565
import coil3.request.crossfade

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BlushApplication : Application(), SingletonImageLoader.Factory {
    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        repo = Repo(this, Store(this))
    }

    // Generous image caches keep scrolling smooth on the Fire Stick.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("images")).maxSizeBytes(250L * 1024 * 1024).build() }
            .crossfade(true)
            // A new row asks for a dozen pictures at once. Decoding them all together starves the stick's four cores and drops
            // frames just as the tiles arrive, so only two are decoded at a time (and four fetched).
            .decoderCoroutineContext(kotlinx.coroutines.Dispatchers.IO.limitedParallelism(2))
            .fetcherCoroutineContext(kotlinx.coroutines.Dispatchers.IO.limitedParallelism(4))
            .eventListenerFactory { _ -> app.blush.tv.ui.Perf.imageEvents }
            // Posters and backdrops have no transparency, so they can use the 16-bit format: half the memory and
            // half the work for the Fire Stick's graphics chip each time they're drawn (logos keep full colour+alpha).
            .allowRgb565(true)
            .build()
}
