package app.blush.tv

import android.app.Application
import app.blush.tv.data.AndroidPrefs
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
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BlushApplication : Application(), SingletonImageLoader.Factory {
    lateinit var repo: Repo
        private set

    companion object {
        /** The app itself, for the few Android calls made from shared code paths (e.g. the playback profile). */
        lateinit var context: android.content.Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        context = this
        CrashLog.install(this)
        // The server code is shared with the Mac app; only the SDK set-up, device name and settings file are Android's own.
        val device = androidDevice(this)
        val jellyfin = createJellyfin {
            context = this@BlushApplication
            clientInfo = ClientInfo("Bloom", BuildConfig.VERSION_NAME)
            deviceInfo = device
        }
        repo = Repo(jellyfin, device, cacheDir, Store(AndroidPrefs(getSharedPreferences("blush", MODE_PRIVATE))))
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
