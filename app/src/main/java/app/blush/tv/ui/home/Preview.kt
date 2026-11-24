@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package app.blush.tv.ui.home

import android.content.Context
import app.blush.tv.data.logged
import android.os.SystemClock
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import app.blush.tv.data.Repo
import app.blush.tv.playback.AudioMode
import app.blush.tv.playback.Languages
import app.blush.tv.playback.PlaybackSource
import app.blush.tv.playback.SpeedFriendlyRenderersFactory
import app.blush.tv.playback.preparePlayback
import app.blush.tv.playback.previewClipUrl
import app.blush.tv.playback.stopPreviewClip
import app.blush.tv.ui.Perf
import app.blush.tv.ui.components.LogoImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlayMethod
import java.util.UUID
import kotlin.random.Random

/** Words in a sound track's name that mark it as an extra rather than the film's own sound. */
private val ExtraAudioWords = listOf(
    "commentary", "comment", "director", "descri", "narrat", "isolated", "score only", "trivia", "filmmaker", "cast &", "crew",
    "audio desc", "visually", "impaired", "behind the scenes",
)

/** How long the remote rests on a tile before its preview starts to come in. */
private const val HoverMs = 1_000L
/** When the clip starts being opened (paused), so it is ready by [HoverMs]. */
private const val PrepareMs = 700L
/** How long the remote must rest before the server is asked anything. */
private const val AskMs = 400L
private const val TicksPerMs = 10_000L
/** How loud a preview gets at most (of the player's full volume). */
private const val MaxVolume = 0.35f
/** Where the sound begins (and so how far it rises): a small step up, not a swell from silence. */
private const val StartVolume = 0.15f
/** How long a clip runs. */
private const val ClipMs = 60_000L

/**
 * The tile previews on Home. One clip at a time, in one player that is only built the first time a preview is wanted (so it
 * costs the start-up nothing) and is shared by every tile. Nothing here runs unless the remote rests on a tile:
 *  - a tile gaining the remote calls [hover]; losing it (moving on, up to the banner, out to the side bar) calls [release];
 *  - after [HoverMs], and once the page and rows have stopped moving, the clip is started;
 *  - when its first picture is up it fades in over the thumbnail (logo with it); at the end both fade back to the thumbnail.
 */
@Stable
class PreviewController(
    private val context: Context,
    private val repo: Repo,
    private val scope: CoroutineScope,
    /** True while the page or a row is moving, or a button has just been pressed. */
    private val busy: () -> Boolean,
) {
    /** The tile that currently shows (or is about to show) the video layer. */
    var activeKey by mutableStateOf<String?>(null)
        private set
    /** 0 = thumbnail, 1 = clip (and its logo). */
    val video = Animatable(0f)
    var player: ExoPlayer? = null
        private set

    private var frameReady by mutableStateOf(false)
    private var hoverKey: String? = null
    private var job: Job? = null
    /** A clip has been given to the player (so stopping it is worth doing). */
    private var loaded = false
    /** True from the moment the video layer is up until the preview is gone (background work stands aside while it is). */
    val hot: Boolean get() = activeKey != null
    private val plans = HashMap<UUID, Plan?>()

    /** What a title's preview is made from: the film or episode, how long it is, stretches to keep out of, and the sound track to use. */
    private class Plan(val target: BaseItemDto, val runtimeMs: Long, val skips: List<LongRange>, val audioIndex: Int?)
    /** The name of the server-made clip in use, so the server can be told to stop making it. */
    private var clipSession: String? = null
    /** Set when the player gave up on what it was opening (read while a clip is being opened), and when the clip is on show. */
    private var failed by mutableStateOf(false)
    private var committed = false
    private var ready by mutableStateOf(false)

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        // Preview sound is decoded on the stick (never passed through to the TV), so it can fade in and out.
        val audio = AudioMode().apply { decodeHere = true }
        val p = ExoPlayer.Builder(
            context,
            SpeedFriendlyRenderersFactory(context, audio).setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON),
        )
            // Starts after a quarter of a second of video rather than the usual 2.5 s: it's only a short clip.
            .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder().setBufferDurationsMs(2_500, 15_000, 250, 500).build())
            // Clips are fetched with the sign-in as a header, like the main player.
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    androidx.media3.datasource.DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(repo.mediaHeaders()),
                ),
            )
            .build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon().setPreferredAudioLanguages(repo.store.defaultAudioLang).build()
                volume = 0f
                // Start at the nearest keyframe: landing on an exact moment means decoding seconds of video first.
                setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
            }
        p.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() { if (activeKey != null) frameReady = true }

            override fun onPlaybackStateChanged(state: Int) { ready = state == Player.STATE_READY }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val want = repo.store.defaultAudioLang
                val groups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && it.isSupported }
                if (groups.size < 2) return
                // The best track for a preview: the right language, not a commentary / description / isolated-score track,
                // then the one the file marks as its default, then the fullest sound (most channels, then bitrate).
                fun format(g: androidx.media3.common.Tracks.Group) = g.getTrackFormat(0)
                fun extra(g: androidx.media3.common.Tracks.Group): Boolean {
                    val f = format(g)
                    val text = (f.label.orEmpty() + " " + f.id.orEmpty()).lowercase()
                    val flagged = f.roleFlags and (androidx.media3.common.C.ROLE_FLAG_COMMENTARY or androidx.media3.common.C.ROLE_FLAG_DESCRIBES_VIDEO or androidx.media3.common.C.ROLE_FLAG_ALTERNATE) != 0
                    return flagged || ExtraAudioWords.any { it in text }
                }
                fun score(g: androidx.media3.common.Tracks.Group): Long {
                    val f = format(g)
                    var v = 0L
                    if (Languages.same(f.language, want)) v += 1L shl 40
                    if (!extra(g)) v += 1L shl 38
                    if (f.selectionFlags and androidx.media3.common.C.SELECTION_FLAG_DEFAULT != 0) v += 1L shl 30
                    v += f.channelCount.coerceIn(0, 16).toLong() shl 22
                    v += (f.bitrate.coerceAtLeast(0) / 1000L).coerceAtMost(4_000_000L)
                    return v
                }
                val best = groups.maxByOrNull { score(it) } ?: return
                if (groups.firstOrNull { it.isSelected } === best) return
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setOverrideForType(androidx.media3.common.TrackSelectionOverride(best.mediaTrackGroup, 0)).build()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Perf.previewNote = "error: ${error.errorCodeName}"
                // While a clip is still being opened the job decides what to do next; once it is on show, it simply ends.
                if (committed) stop() else failed = true
            }
        })
        player = p
        return p
    }

    /** What to play for a title (asked of the server once, then remembered): null if it has no usable clip. */
    private suspend fun plan(item: BaseItemDto): Plan? {
        if (plans.containsKey(item.id)) return plans[item.id]
        val made = logged("Preview.plan") {
            val target = repo.previewSource(item) ?: return@logged null
            // The full record: tiles do not carry the sound tracks.
            val full = logged("Preview.plan") { repo.item(target.id) }.getOrDefault(target)
            val runtimeMs = ((full.runTimeTicks ?: target.runTimeTicks) ?: return@logged null) / TicksPerMs
            if (runtimeMs < 120_000) return@logged null
            val skips = logged("Preview.plan") {
                repo.segments(target.id).filter { it.endTicks > it.startTicks }.map { (it.startTicks / TicksPerMs)..(it.endTicks / TicksPerMs) }
            }.getOrDefault(emptyList())
            val streams = full.mediaStreams ?: full.mediaSources?.firstOrNull()?.mediaStreams
            Plan(full, runtimeMs, skips, streams?.let { bestAudio(it) })
        }.getOrNull()
        plans[item.id] = made
        return made
    }

    /** The sound track for a preview: the right language, not a commentary or description track, then the file's own default, then the fullest. */
    private fun bestAudio(streams: List<org.jellyfin.sdk.model.api.MediaStream>): Int? {
        val want = repo.store.defaultAudioLang
        return streams.filter { it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO }.maxByOrNull { st ->
            val text = ((st.title ?: "") + " " + (st.displayTitle ?: "")).lowercase()
            var v = 0L
            if (Languages.same(st.language, want)) v += 1L shl 40
            if (ExtraAudioWords.none { it in text }) v += 1L shl 38
            if (st.isDefault == true) v += 1L shl 30
            v += (st.channels ?: 0).coerceIn(0, 16).toLong() shl 22
            v += ((st.bitRate ?: 0) / 1000L).coerceAtMost(4_000_000L)
            v
        }?.index
    }

    /** True once the player is ready, false if it gave up or took longer than [ms]. */
    private suspend fun awaitReady(ms: Long): Boolean =
        withTimeoutOrNull(ms) { snapshotFlow { ready || failed }.first { it } } != null && !failed

    /**
     * Opens a small copy made by the server, beginning at [start], paused. The server does the jump, so this works for any file and
     * the stick fetches and decodes only a small stream.
     */
    private suspend fun openClip(p: ExoPlayer, plan: Plan, start: Long): Boolean {
        val session = java.util.UUID.randomUUID().toString().replace("-", "")
        clipSession = session
        failed = false
        ready = false
        p.playWhenReady = false
        p.setMediaItem(MediaItem.Builder().setUri(repo.previewClipUrl(plan.target, start, plan.audioIndex, session)).setMimeType(MimeTypes.VIDEO_MP2T).build())
        p.prepare()
        return awaitReady(8_000)
    }

    /** The way back if the server will not make a clip: the file itself, opened at [start]. Only used if the file can really be jumped into. */
    private suspend fun openDirect(p: ExoPlayer, plan: Plan, start: Long): Boolean {
        stopClip()
        val source = logged("Preview.openDirect") { repo.preparePlayback(plan.target, 0, forPreview = true) }.getOrNull()?.takeIf { it.method != PlayMethod.TRANSCODE } ?: return false
        failed = false
        ready = false
        p.playWhenReady = false
        p.setMediaItem(MediaItem.fromUri(source.url), start)
        p.prepare()
        if (!awaitReady(5_000)) return false
        // A file that cannot be jumped into is taken to its very start by the player (the opening credits): so it is not used.
        return kotlin.math.abs(p.currentPosition - start) <= 20_000
    }

    private fun stopClip() {
        clipSession?.let { id -> clipSession = null; scope.launch { repo.stopPreviewClip(id) } }
    }

    /** Builds the player ahead of time (call when the remote has been still for a while), so the first preview doesn't hitch. */
    fun warmUp() {
        // No previews, no player: it would sit in memory unused.
        if (repo.store.previews == "off" || app.blush.tv.ui.Lite.on) return
        ensurePlayer()
    }

    /** A tile has the remote. */
    fun hover(key: String, item: BaseItemDto) {
        stop()
        if (repo.store.previews == "off" || app.blush.tv.ui.Lite.on) return
        hoverKey = key
        job = scope.launch {
            val began = SystemClock.uptimeMillis()
            // Nothing touches the network until the remote has rested a little, and nothing touches the player until it has rested
            // a little longer, so moving along a row costs nothing. What to play is worked out once per title and remembered.
            val ask = async { delay(AskMs); plan(item) }
            delay(PrepareMs)
            val plan = ask.await() ?: return@launch
            while (busy()) delay(100)
            val (start, length) = window(plan.runtimeMs, plan.skips)
            val p = ensurePlayer()
            frameReady = false
            failed = false
            committed = false
            video.snapTo(0f)
            activeKey = key
            // Let the video layer be built and attached before the clip is opened, so its first picture has somewhere to land.
            withFrameNanos { }
            withFrameNanos { }
            p.volume = 0f
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_AUDIO).build()
            loaded = true
            // The clip is opened, paused, while the remote is still resting, so it is ready by the time the preview is due.
            val viaServer = openClip(p, plan, start)
            if (!viaServer && !openDirect(p, plan, start)) {
                Perf.previewNote = "no clip: ${item.name}"
                stop()
                return@launch
            }
            delay((HoverMs - (SystemClock.uptimeMillis() - began)).coerceAtLeast(0))
            while (busy()) delay(100)
            committed = true
            p.play()
            // If the first picture does not come soon, the tile simply stays on its thumbnail (and the clip is stopped, sound and all).
            if (withTimeoutOrNull(3_000) { snapshotFlow { frameReady }.first { it } } == null) {
                Perf.previewNote = "no picture: ${item.name}"
                stop()
                return@launch
            }
            if (!viaServer && kotlin.math.abs(p.currentPosition - start) > 30_000) {
                Perf.previewNote = "wrong place: ${item.name} at ${p.currentPosition / 1000}s, wanted ${start / 1000}s"
                stop()
                return@launch
            }
            video.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
            // The sound starts low and rises a little (over two seconds) to a modest level: never a jolt.
            if (repo.store.previews == "on") repeat(50) { i -> p.volume = StartVolume + (MaxVolume - StartVolume) * ((i + 1) / 50f); delay(40) }
            delay(length)
            // Ends gently: the sound goes, then the picture and logo fall back to the thumbnail together.
            val from = p.volume
            repeat(40) { i -> p.volume = from * (1f - (i + 1) / 40f); delay(25) }
            video.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
            stop()
        }
    }

    /** A tile has lost the remote (if it is the one the preview belongs to, the preview goes). */
    fun release(key: String) {
        if (hoverKey == key) stop()
    }

    /** Everything off, at once. */
    fun stop() {
        job?.cancel()
        job = null
        hoverKey = null
        // Moving between tiles calls this on every press, so it does nothing at all unless there is something to stop.
        if (loaded) { loaded = false; player?.let { it.volume = 0f; it.stop() } }
        committed = false
        stopClip()
        if (frameReady) frameReady = false
        if (ready) ready = false
        if (activeKey != null) activeKey = null
        if (video.value != 0f || video.isRunning) scope.launch(start = CoroutineStart.UNDISPATCHED) { video.snapTo(0f) }
    }

    fun destroy() {
        stop()
        player?.release()
        player = null
    }

    /**
     * Where a clip starts and how long it runs (ms): a minute, starting after the 10-minute mark and ending before the
     * halfway point. If that leaves no room (a film of 20 minutes or less), it comes from anywhere before halfway instead.
     */
    private fun window(runtimeMs: Long, skips: List<LongRange>): Pair<Long, Long> {
        val length = ClipMs.coerceAtMost(runtimeMs / 2)
        val latestStart = (runtimeMs / 2 - length).coerceAtLeast(0)
        // Short items can't wait for the 10-minute mark, but still skip the first two minutes (title sequence, cold open).
        val earliest = if (latestStart > 600_000) 600_000L else minOf(120_000L, latestStart)
        var start = if (latestStart > earliest) Random.nextLong(earliest, latestStart + 1) else earliest
        // Never inside an intro or recap the server has marked: start just after it instead.
        skips.firstOrNull { start in (it.first - 5_000)..it.last }?.let { start = it.last + 3_000 }
        return start to length
    }
}

/**
 * The preview layer of a tile: the clip over the thumbnail, a shade and the title's logo at the bottom left. Fills the tile's
 * picture area; shows nothing unless the preview belongs to [tileKey]. Only the tile with the remote ever composes it.
 */
@Composable
internal fun BoxScope.PreviewSlot(
    preview: PreviewController,
    tileKey: String,
    repo: Repo,
    item: BaseItemDto,
    logoArea: Float = 5200f,
    logoMaxWidth: Dp = 150.dp,
    logoMaxHeight: Dp = 50.dp,
    inset: Dp = 12.dp,
) {
    if (preview.activeKey != tileKey) return
    val player = preview.player ?: return
    PreviewVideo(player) { preview.video.value }
    // A title with no thumbnail of its own already carries its logo; the rest bring it in with the clip.
    if (repo.hasThumb(item)) {
        val logo = repo.logoUrl(item, 500)
        if (logo != null) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = preview.video.value }
                    .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xCC090A0E))),
            )
            // Sized from the tile itself (a fixed size looked tiny on a big tile) and pinned to its bottom-left corner.
            BoxWithConstraints(Modifier.fillMaxSize().padding(inset).graphicsLayer { alpha = preview.video.value }) {
                val w = minOf(maxWidth * 0.5f, maxOf(logoMaxWidth, maxWidth * 0.42f))
                val h = minOf(maxHeight * 0.3f, maxOf(logoMaxHeight, maxHeight * 0.26f))
                LogoImage(logo, modifier = Modifier.align(Alignment.BottomStart), area = maxOf(logoArea, w.value * h.value * 0.55f), maxWidth = w, maxHeight = h)
            }
        }
    }
}

/** The clip, filling its tile (cropped to the tile's shape, not squashed). */
@Composable
private fun PreviewVideo(player: ExoPlayer, alpha: () -> Float) {
    fun ratioOf(v: VideoSize) = if (v.width > 0 && v.height > 0) v.width * v.pixelWidthHeightRatio / v.height else null
    var videoRatio by remember { mutableFloatStateOf(ratioOf(player.videoSize) ?: (16f / 9f)) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) { ratioOf(videoSize)?.let { videoRatio = it } }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha() }, contentAlignment = Alignment.Center) {
        val tileRatio = maxWidth / maxHeight
        val videoWidth = if (videoRatio > tileRatio) maxHeight * videoRatio else maxWidth
        val videoHeight = if (videoRatio > tileRatio) maxHeight else maxWidth / videoRatio
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
            onRelease = { runCatching { player.clearVideoTextureView(it) } },
            modifier = Modifier.requiredSize(videoWidth, videoHeight),
        )
    }
}
