package app.blush.tv.ui.player

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.animation.core.FastOutSlowInEasing
import app.blush.tv.ui.components.PosterCard
import app.blush.tv.ui.displayTitle
import app.blush.tv.ui.components.TitleCard
import android.graphics.Typeface
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.wrapContentWidth
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import app.blush.tv.playback.PlaybackSource
import app.blush.tv.playback.SpeedFriendlyRenderersFactory
import app.blush.tv.playback.AudioMode
import app.blush.tv.playback.LangPrefs
import app.blush.tv.playback.TrackPick
import app.blush.tv.playback.preparePlayback
import app.blush.tv.playback.reportProgress
import app.blush.tv.playback.reportStart
import app.blush.tv.playback.reportStopped
import app.blush.tv.ui.AppState
import app.blush.tv.ui.Route
import app.blush.tv.ui.clockText
import app.blush.tv.ui.components.DropdownRow
import app.blush.tv.ui.components.FocusSurface
import app.blush.tv.ui.components.OptionRow
import app.blush.tv.ui.components.SidePanel
import app.blush.tv.ui.components.StepperRow
import app.blush.tv.ui.components.Icon
import app.blush.tv.ui.components.Icons
import app.blush.tv.ui.components.LogoImage
import app.blush.tv.ui.episodeCode
import app.blush.tv.ui.theme.Blush
import app.blush.tv.ui.theme.BlushType
import app.blush.tv.ui.theme.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod

private const val TICKS_PER_MS = 10_000L

/** Reports outlive the screen, so they run on their own scope. */
private val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * The last "stopped, here's where I got to" report. The screen after the player (Details, Home's Continue watching) waits for it
 * before asking the server for the position, otherwise it can ask first and show the old one.
 */
object PlayerReports {
    @Volatile
    var lastStop: kotlinx.coroutines.Job? = null

    suspend fun settle() {
        kotlinx.coroutines.withTimeoutOrNull(3_000) { lastStop?.join() }
    }
}

@kotlin.OptIn(ExperimentalAnimationApi::class)
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(app: AppState, route: Route.Player) {
    val context = LocalContext.current
    val repo = app.repo
    val scope = rememberCoroutineScope()

    // Sends surround sound to the TV/soundbar as it is (passthrough) by default; switched to decoding it here while
    // the speed isn't normal, because passed-through sound can't be sped up (and the picture follows the sound).
    // Volume away from 100% (or a changed speed) needs the sound decoded here: passed-through surround ignores volume.
    val audioSink = remember { AudioMode().also { it.decodeHere = !repo.store.passthroughAudio } }
    val player = remember {
        val renderers = SpeedFriendlyRenderersFactory(context, audioSink, softwareGain = true)
            // Use the device's own decoders first, and the bundled FFmpeg decoder for anything they can't handle (DTS, TrueHD …).
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .setDefaultRequestProperties(repo.mediaHeaders())
        ExoPlayer.Builder(context, renderers)
            .setTrackSelector(DefaultTrackSelector(context))
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { audioSink.gain = gainFor(repo.store.playerVolume); it.volume = loudnessFor(repo.store.playerVolume) }
    }

    var item by remember { mutableStateOf(route.item) }
    var source by remember { mutableStateOf<PlaybackSource?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var panelOpen by remember { mutableStateOf(false) }
    var volumeOpen by remember { mutableStateOf(false) }
    // The "-10 s / +10 s" flash shown after a skip; repeated presses add up.
    var skipTotal by remember { mutableIntStateOf(0) }
    var lastHeldSkip by remember { mutableLongStateOf(0L) }
    var skipStamp by remember { mutableLongStateOf(0L) }
    var showBuffering by remember { mutableStateOf(false) }
    // 0–1.5: up to 100% is the player's own volume; above that the sound is boosted (Android's loudness enhancer).
    var volume by remember { mutableFloatStateOf(repo.store.playerVolume) }
    var audioSession by remember { mutableIntStateOf(player.audioSessionId) }
    /** Volume is applied to the sound itself when it can be (see AudioMode.gain); the output's own setting and the booster are the fallback. */
    fun applyVolume(v: Float) {
        audioSink.gain = gainFor(v)
        player.volume = if (audioSink.gainActive) 1f else loudnessFor(v)
    }
    LaunchedEffect(volume, audioSink.gainActive) { applyVolume(volume) }
    val booster = remember(audioSession, volume > 1f, audioSink.gainActive) {
        if (volume <= 1f || audioSession == C.AUDIO_SESSION_ID_UNSET || audioSink.gainActive) null
        else runCatching { android.media.audiofx.LoudnessEnhancer(audioSession) }.getOrNull()
    }
    DisposableEffect(booster) { onDispose { runCatching { booster?.release() } } }
    LaunchedEffect(booster, volume) {
        booster ?: return@LaunchedEffect
        // The same scale as gainFor: +9 dB at 150% (the target is in hundredths of a decibel).
        runCatching {
            booster.setTargetGain((1800 * (volume - 1f)).toInt())
            booster.enabled = true
        }
    }
    var speed by remember { mutableFloatStateOf(1f) }
    // A quality cap chosen in the player for this film only; null means the Settings default.
    var qualityCap by remember { mutableStateOf<Int?>(null) }
    // The subtitle track to bring back when the subtitles button is pressed again.
    var lastSubtitle by remember { mutableStateOf<Int?>(null) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var segments by remember { mutableStateOf<List<MediaSegmentDto>>(emptyList()) }
    var nextEpisode by remember { mutableStateOf<BaseItemDto?>(null) }
    var audioIndex by remember { mutableStateOf<Int?>(null) }
    var subtitleIndex by remember { mutableStateOf<Int?>(null) }
    // Text subtitles are drawn by Bloom (SubtitleLayer): the player's current lines, the chosen look, and a timing shift.
    var playerLines by remember { mutableStateOf<List<SubLine>>(emptyList()) }
    var subLook by remember { mutableStateOf(SubtitleLook.of(repo.store)) }
    /** Positive: subtitles later; negative: earlier. Shifting needs the whole file ([shiftedCues]), fetched from the server. */
    var subShiftMs by remember { mutableLongStateOf(0L) }
    var shiftedCues by remember { mutableStateOf<Pair<Int, List<TimedCue>>?>(null) }
    var subShiftNote by remember { mutableStateOf<String?>(null) }
    var tracksApplied by remember { mutableStateOf(false) }
    var startReported by remember { mutableStateOf(false) }
    var triedTranscode by remember { mutableStateOf(false) }
    // What's actually happening under the hood, shown in the corner with the controls so problems can be diagnosed.
    var videoDecoder by remember { mutableStateOf<String?>(null) }
    var audioDecoder by remember { mutableStateOf<String?>(null) }
    var droppedFrames by remember { mutableIntStateOf(0) }
    var audioGaps by remember { mutableIntStateOf(0) }

    val rootFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }
    val volumeFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }
    /** The first button of the credits prompt (Skip credits, or Next episode when the credits run to the end). */
    val creditsFocus = remember { FocusRequester() }
    val creditsNextFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val tileFocus = remember { FocusRequester() }
    /** True for the first 8 seconds of the intro: the Skip intro button shows on its own (with the controls up it always shows). */
    var introPrompt by remember { mutableStateOf(false) }
    val introTimer = remember { androidx.compose.animation.core.Animatable(1f) }
    /** Set when a prompt is pressed: it goes away quickly instead of the slow fade. */
    var promptQuickExit by remember { mutableStateOf(false) }
    /** Titles offered during the credits of a film (or a show's last episode). */
    var creditsTiles by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var tilesShown by remember { mutableStateOf(false) }
    var tilesDismissed by remember { mutableStateOf(false) }
    /** A prompt (Skip intro, the credits buttons, the tiles) has the remote. */
    var promptHadFocus by remember { mutableStateOf(false) }
    /** The ✕ beside Next episode: the credits play on without the prompt (until the next episode). */
    var creditsDismissed by remember { mutableStateOf(false) }

    fun poke() {
        lastInteraction = System.currentTimeMillis()
        controlsVisible = true
    }

    /** Ask the server for a stream and start it at [startMs]. */
    suspend fun load(target: BaseItemDto, startMs: Long, audio: Int?, subtitle: Int?, forceTranscode: Boolean = false) {
        error = null
        buffering = true
        // Reloading (new audio/subtitle on a converted stream, or the convert-and-retry) starts a new play session:
        // close the old one on the server and report the start of the new one.
        source?.let { old ->
            if (startReported) {
                val pos = player.currentPosition * TICKS_PER_MS
                PlayerReports.lastStop = reportScope.launch { repo.reportStopped(old, pos) }
            }
        }
        startReported = false
        // Nothing chosen yet: start in this series' language (what you last picked for it) or the Settings defaults.
        val langs = if (audio == null && subtitle == null) {
            val sid = target.seriesId?.toString()
            LangPrefs(
                audio = sid?.let { repo.store.seriesAudio(it) } ?: repo.store.defaultAudioLang,
                subtitles = sid?.let { repo.store.seriesSubtitles(it) } ?: repo.store.defaultSubtitleLang,
                audioPick = sid?.let { TrackPick.decode(repo.store.seriesAudioPick(it)) },
                subtitlePick = sid?.let { TrackPick.decode(repo.store.seriesSubtitlePick(it)) },
            )
        } else null
        val prepared = runCatching {
            // The server not answering must end in a message, not a spinner that never stops.
            kotlinx.coroutines.withTimeoutOrNull(25_000) { repo.preparePlayback(target, startMs * TICKS_PER_MS, audio, subtitle, forceTranscode, maxBitrate = qualityCap, languages = langs) }
                ?: throw IllegalStateException("the server didn't answer in time")
        }
            .getOrElse {
                val timedOut = it is IllegalStateException && it.message?.contains("in time") == true
                error = if (timedOut) "The server didn't answer.\nCheck it's on and not busy, then try again.\n\nCode B11 · no reply in 25 s"
                else "The server couldn't start this video.\nCheck it's on, then try again.\n\nCode B10 · ${it::class.simpleName}: ${it.message.orEmpty().take(120)}"
                return
            }
        source = prepared
        audioIndex = prepared.audioIndex
        subtitleIndex = prepared.subtitleIndex
        tracksApplied = false
        val subtitleConfigs = prepared.externalSubtitles.map { sub ->
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                .setId("jf-${sub.streamIndex}")
                .setMimeType(sub.mimeType)
                .setLanguage(sub.language)
                .setLabel(sub.label)
                .setSelectionFlags(if (sub.streamIndex == prepared.subtitleIndex) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        val mediaItem = MediaItem.Builder()
            .setUri(prepared.url)
            .setMediaId(target.id.toString())
            .apply { if (prepared.method == PlayMethod.TRANSCODE) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .setSubtitleConfigurations(subtitleConfigs)
            .build()
        player.setMediaItem(mediaItem, startMs)
        player.prepare()
        player.playWhenReady = true
    }

    fun stopAndReport() {
        val s = source ?: return
        val pos = player.currentPosition * TICKS_PER_MS
        // Left anywhere from the start of the credits on: it counts as watched, whatever the server's own share says.
        val credits = segments.firstOrNull { it.type == MediaSegmentType.OUTRO }?.let { pos >= it.startTicks } == true
        PlayerReports.lastStop = reportScope.launch {
            repo.reportStopped(s, pos)
            if (credits) runCatching { repo.setPlayed(s.item.id, true) }
        }
    }

    /** A title chosen from the credits tiles: the player is left for that title's page, with the watched title's page under it. */
    fun openFromCredits(target: BaseItemDto) {
        player.pause()
        stopAndReport()
        source = null
        val watched = item
        val watchedId = if (watched.type == BaseItemKind.EPISODE) watched.seriesId ?: watched.id else watched.id
        app.stack.removeAt(app.stack.lastIndex)
        if ((app.stack.lastOrNull() as? Route.Details)?.itemId != watchedId) app.stack.add(Route.Details(watchedId))
        app.go(Route.Details(target.id, target))
    }

    fun playNext() {
        val next = nextEpisode ?: return
        creditsDismissed = true
        stopAndReport()
        source = null
        startReported = false
        val idx = app.stack.lastIndex
        app.stack[idx] = Route.Player(next)
    }

    // Start (or restart when the route changes to the next episode).
    LaunchedEffect(route) {
        item = route.item
        startReported = false
        triedTranscode = false
        segments = emptyList()
        creditsDismissed = false
        creditsTiles = emptyList()
        tilesShown = false
        tilesDismissed = false
        // Tiles from the home screen don't carry everything; fetch the full item.
        val full = runCatching { repo.item(route.item.id) }.getOrDefault(route.item)
        // The resume point comes from the freshly fetched item, not the (possibly old) copy the last screen held.
        val start = if (route.fromStart) 0L else (full.userData?.playbackPositionTicks ?: 0L) / TICKS_PER_MS
        item = full
        load(full, start, null, null)
        segments = repo.segments(full)
        nextEpisode = if (full.type == BaseItemKind.EPISODE) runCatching { repo.episodeAfter(full) }.getOrNull() else null
        // A film or a show's last episode with credits: fetch what to offer during them now, so it's ready in time.
        if (nextEpisode == null && segments.any { it.type == MediaSegmentType.OUTRO }) {
            creditsTiles = runCatching {
                val base = if (full.type == BaseItemKind.EPISODE) full.seriesId?.let { repo.item(it) } else full
                base?.let { repo.moreLikeThis(it) }.orEmpty().take(5)
            }.getOrDefault(emptyList())
        }
    }

    // Player events.
    DisposableEffect(player) {
        // Times in a row the connection dropped and we tried again; back to zero once the picture is moving.
        var networkRetries = 0
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                // Tell the server straight away about pausing and resuming, rather than waiting for the next 10 s report.
                val s = source
                if (s != null && startReported) {
                    val pos = player.currentPosition * TICKS_PER_MS
                    val audio = audioIndex
                    val subtitle = subtitleIndex
                    reportScope.launch { repo.reportProgress(s, pos, !isPlaying, audio, subtitle) }
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                audioSession = audioSessionId
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                if (state == Player.STATE_READY) networkRetries = 0
                if (state == Player.STATE_READY && !startReported) {
                    startReported = true
                    val pos = player.currentPosition * TICKS_PER_MS
                    source?.let { s -> reportScope.launch { repo.reportStart(s, pos) } }
                }
                if (state == Player.STATE_ENDED) {
                    if (nextEpisode != null && repo.store.autoNextEpisode) playNext() else {
                        stopAndReport()
                        app.back()
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val s = source ?: return
                if (!tracksApplied && tracks.groups.isNotEmpty()) {
                    tracksApplied = true
                    applyTracks(player, s, audioIndex, subtitleIndex)
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                // A dropped connection says nothing about whether this device can decode the file, so wait and carry on
                // from the same spot a few times before giving up (rather than asking the server to convert it).
                val connectionProblem = e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                if (connectionProblem && networkRetries < 3) {
                    networkRetries++
                    buffering = true
                    scope.launch {
                        delay(2_000L * networkRetries)
                        player.prepare()
                    }
                    return
                }
                val s = source
                // If this device couldn't decode the file as it is, ask the server to convert it and carry on from the same spot.
                if (s != null && s.method != PlayMethod.TRANSCODE && !triedTranscode) {
                    triedTranscode = true
                    val at = player.currentPosition
                    scope.launch { load(item, at, audioIndex, subtitleIndex, forceTranscode = true) }
                } else {
                    error = if (connectionProblem) "Lost the connection to your server.\n\nCode B21 · ${e.errorCodeName}\n${s?.let { describeSource(it) }.orEmpty()}"
                    else "This video couldn't be played on this device.\n\nCode B20 · ${e.errorCodeName} (${e.errorCode})\n${s?.let { describeSource(it) }.orEmpty()}"
                }
            }
        }
        player.addListener(listener)
        val cueListener = object : Player.Listener {
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                playerLines = SubtitleText.fromCues(cueGroup.cues)
            }
        }
        player.addListener(cueListener)
        val stats = object : AnalyticsListener {
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                videoDecoder = decoderName
            }

            override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                audioDecoder = decoderName
            }

            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, count: Int, elapsedMs: Long) {
                droppedFrames += count
            }

            override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
                audioGaps++
            }
        }
        player.addAnalyticsListener(stats)
        onDispose {
            player.removeListener(cueListener)
            player.removeListener(listener)
            player.removeAnalyticsListener(stats)
            stopAndReport()
            player.release()
        }
    }

    // Leaving the app (the Home button, another app opening): stop, save the position and close the player, so
    // nothing keeps playing in the background. Coming back lands on the screen underneath (e.g. the details page).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                player.pause()
                stopAndReport()
                source = null
                if (app.current is Route.Player) app.back()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Clock: position updates, progress reports and auto-hiding the controls.
    LaunchedEffect(player) {
        var tick = 0
        while (isActive) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 } ?: ((item.runTimeTicks ?: 0L) / TICKS_PER_MS)
            // Only the controls show how much is buffered, so it is only worked out while they are up.
            if (controlsVisible) buffered = player.bufferedPosition
            if (controlsVisible && !panelOpen && !volumeOpen && player.isPlaying && System.currentTimeMillis() - lastInteraction > 3500) {
                controlsVisible = false
            }
            tick++
            if (tick % 20 == 0) {
                // Read the player here on the main thread; only the network call goes to the background.
                val pos = player.currentPosition * TICKS_PER_MS
                val paused = !player.isPlaying
                val audio = audioIndex
                val subtitle = subtitleIndex
                source?.let { s -> reportScope.launch { repo.reportProgress(s, pos, paused, audio, subtitle) } }
            }
            delay(500)
        }
    }

    var playPauseFocused by remember { mutableStateOf(false) }
    val settingsFocus = remember { FocusRequester() }
    var settingsFocused by remember { mutableStateOf(false) }
    var volumeFocused by remember { mutableStateOf(false) }

    /** Puts the remote on [target] once the controls are back on screen, retrying until it's really there. */
    fun focusWhenShown(target: FocusRequester, isThere: () -> Boolean) {
        scope.launch {
            repeat(30) {
                withFrameNanos { }
                if (isThere()) return@launch
                runCatching { target.requestFocus() }
            }
        }
    }

    /** Closes the settings panel and puts the remote back on the settings button it was opened from. */
    fun closePanel() {
        if (!panelOpen) return
        panelOpen = false
        controlsVisible = true
        poke()
        focusWhenShown(settingsFocus) { settingsFocused }
    }

    /** Closes the volume slider (Back, OK, Left or Right) and puts the remote back on the volume button. */
    fun closeVolume() {
        if (!volumeOpen) return
        volumeOpen = false
        controlsVisible = true
        poke()
        focusWhenShown(volumeFocus) { volumeFocused }
    }

    /** Speed: anything but normal needs the sound decoded here, so the audio is restarted that way first. */
    /** Makes the sound output match what's needed (decoded here for a changed speed or volume), then runs [then]. */
    fun applyAudioMode(then: () -> Unit = {}) {
        // Sound is decoded here from the start (so volume just works); only when passed through to the TV
        // does a changed speed need it switched, which restarts the audio briefly.
        val decodeHere = !repo.store.passthroughAudio || speed != 1f
        if (audioSink.decodeHere != decodeHere) {
            audioSink.decodeHere = decodeHere
            val params = player.trackSelectionParameters
            player.trackSelectionParameters = params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            scope.launch {
                delay(60)
                player.trackSelectionParameters = params
                then()
            }
        } else {
            then()
        }
    }

    fun chooseSpeed(v: Float) {
        speed = v
        applyAudioMode { player.setPlaybackSpeed(v) }
    }

    // Set when Back hides the controls: they vanish quickly, and the selection stays put until they've gone.
    var quickHide by remember { mutableStateOf(false) }
    var hiddenAt by remember { mutableLongStateOf(0L) }
    // Intro and credits come from the server's media segments (sent by Bookend): one INTRO and one OUTRO at most, fetched afresh
    // each time playback starts. Each is only "on" between its own start and end, so whatever follows the credits (a post-credits
    // scene, a next-episode preview) is left uncovered.
    val introActive by remember {
        androidx.compose.runtime.derivedStateOf {
            val t = position * TICKS_PER_MS
            segments.firstOrNull { it.type == MediaSegmentType.INTRO }?.let { t >= it.startTicks && t < it.endTicks } == true
        }
    }
    val inCredits by remember {
        androidx.compose.runtime.derivedStateOf {
            val t = position * TICKS_PER_MS
            segments.firstOrNull { it.type == MediaSegmentType.OUTRO }?.let { t >= it.startTicks && t < it.endTicks } == true
        }
    }
    /** The credits run to the very end (within a second): nothing after them to skip to. */
    val creditsToEnd = segments.firstOrNull { it.type == MediaSegmentType.OUTRO }?.let { o ->
        val total = if (duration > 0) duration * TICKS_PER_MS else item.runTimeTicks ?: 0L
        total > 0 && total - o.endTicks <= 10_000_000L
    } ?: false

    val skipVisible = introActive && (introPrompt || controlsVisible) && !panelOpen && error == null
    val creditsPromptVisible = inCredits && nextEpisode != null && !creditsDismissed && !panelOpen && error == null
    val tilesVisible = tilesShown && inCredits && !tilesDismissed && creditsTiles.isNotEmpty() && !controlsVisible && !panelOpen && error == null

    // The intro starts: Skip intro appears selected, and goes after 8 seconds (the white line round it runs out meanwhile).
    LaunchedEffect(introActive) {
        if (!introActive) { introPrompt = false; return@LaunchedEffect }
        promptQuickExit = false
        introTimer.snapTo(1f)
        introPrompt = true
        if (!controlsVisible && !panelOpen) {
            withFrameNanos { }
            delay(60)
            runCatching { skipFocus.requestFocus() }
        }
        // Counts down only while the picture is moving: paused on the intro, the button waits too.
        var left = 8000f
        var last = 0L
        while (left > 0f) {
            val now = withFrameNanos { it }
            if (last != 0L && player.isPlaying) left -= (now - last) / 1_000_000f
            last = now
            introTimer.snapTo((left / 8000f).coerceIn(0f, 1f))
        }
        introPrompt = false
    }
    // The credits start on an episode with one after it: the prompt appears selected.
    LaunchedEffect(creditsPromptVisible) {
        if (!creditsPromptVisible) return@LaunchedEffect
        promptQuickExit = false
        if (!controlsVisible) {
            withFrameNanos { }
            delay(60)
            runCatching { creditsFocus.requestFocus() }
        }
    }
    // A film's (or last episode's) credits: 5 seconds in, a few titles to watch next slide up, the first one selected.
    // (Started again when the titles arrive, in case the credits were reached before they had.)
    LaunchedEffect(inCredits, creditsTiles.isEmpty()) {
        tilesShown = false
        if (!inCredits || nextEpisode != null || tilesDismissed || creditsTiles.isEmpty()) return@LaunchedEffect
        delay(5000)
        tilesShown = true
        repeat(10) {
            delay(60)
            if (!controlsVisible && runCatching { tileFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    // Whenever every prompt has gone with the controls hidden, the remote goes back to the player (or it would have nothing to work).
    // With the controls up, a prompt that had the remote hands it to Play / Pause.
    val anyPrompt = skipVisible || creditsPromptVisible || tilesVisible
    LaunchedEffect(anyPrompt) {
        if (anyPrompt) return@LaunchedEffect
        val had = promptHadFocus
        promptHadFocus = false
        if (controlsVisible && had && !panelOpen && !volumeOpen) {
            focusWhenShown(playPauseFocus) { playPauseFocused }
            return@LaunchedEffect
        }
        delay(260)
        if (!controlsVisible && !panelOpen && !volumeOpen) runCatching { rootFocus.requestFocus() }
    }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            quickHide = false
            runCatching { playPauseFocus.requestFocus() }
        } else {
            // Keep the selected button as it was until the controls have fully faded, so it never flashes unselected.
            if (quickHide) delay(320)
            // A prompt still on screen (or the credits tiles coming back) takes the remote; otherwise the player does.
            val target = when {
                introActive && introPrompt -> skipFocus
                inCredits && nextEpisode != null && !creditsDismissed -> creditsFocus
                tilesShown && inCredits && !tilesDismissed && creditsTiles.isNotEmpty() -> tileFocus
                else -> rootFocus
            }
            if (target == tileFocus) withFrameNanos { }
            if (runCatching { target.requestFocus() }.getOrDefault(false) != true) runCatching { rootFocus.requestFocus() }
        }
    }

    /** Skip 10 seconds back (-1) or forward (1), with an on-screen flash instead of the loading dots. */
    fun skip(direction: Int) {
        if (direction < 0) player.seekBack() else player.seekForward()
        val now = System.currentTimeMillis()
        skipTotal = if (now - skipStamp < 1200 && skipTotal != 0 && (skipTotal > 0) == (direction > 0)) skipTotal + direction * 10 else direction * 10
        skipStamp = now
    }

    /** Switch audio track: the server has to restart the stream if it's converting, otherwise it's instant. */
    fun chooseAudio(index: Int) {
        audioIndex = index
        val src = source ?: return
        item.seriesId?.let { sid ->
            val stream = src.audioStreams.firstOrNull { it.index == index }
            stream?.language?.let { repo.store.rememberSeriesAudio(sid.toString(), it) }
            // The exact track too, so the next episode can find the same one (not just the first in that language).
            if (stream != null) repo.store.rememberSeriesAudioPick(sid.toString(), TrackPick.of(stream, src.audioStreams).encode())
        }
        if (src.method == PlayMethod.TRANSCODE) {
            val at = player.currentPosition
            scope.launch { load(item, at, index, subtitleIndex) }
        } else applyTracks(player, src, index, subtitleIndex)
    }

    /** Switch subtitles (null = off). Picture-based subtitles on a converted stream need the server to restart it. */
    fun chooseSubtitle(index: Int?) {
        subtitleIndex = index
        if (index != null && index >= 0) lastSubtitle = index
        val src = source ?: return
        item.seriesId?.let { sid ->
            val lang = if (index == null || index < 0) "off" else src.subtitleStreams.firstOrNull { it.index == index }?.language
            repo.store.rememberSeriesSubtitles(sid.toString(), lang)
            val stream = src.subtitleStreams.firstOrNull { it.index == index }
            val pick = if (index == null || index < 0) TrackPick.Off else stream?.let { TrackPick.of(it, src.subtitleStreams) }
            if (pick != null) repo.store.rememberSeriesSubtitlePick(sid.toString(), pick.encode())
        }
        val stream = src.subtitleStreams.firstOrNull { it.index == index }
        val needsServer = src.method == PlayMethod.TRANSCODE && stream != null && !stream.isTextSubtitleStream
        if (needsServer || (src.method == PlayMethod.TRANSCODE && index != null && src.externalSubtitles.none { it.streamIndex == index })) {
            val at = player.currentPosition
            scope.launch { load(item, at, audioIndex, index ?: -1) }
        } else applyTracks(player, src, audioIndex, index)
    }

    val subtitlesOn = subtitleIndex != null && subtitleIndex!! >= 0

    /**
     * The subtitles button: off if on; otherwise the last track used, else English, else the first one.
     * Text subtitles are preferred over picture-based ones (PGS/VobSub), which can't take the chosen style.
     */
    fun toggleSubtitles() {
        if (subtitlesOn) { chooseSubtitle(null); return }
        val streams = source?.subtitleStreams.orEmpty().sortedBy { if (it.isTextSubtitleStream) 0 else 1 }
        val pick = lastSubtitle?.takeIf { i -> streams.any { it.index == i } }
            ?: streams.firstOrNull { it.language?.startsWith("en", ignoreCase = true) == true }?.index
            ?: streams.firstOrNull()?.index
        if (pick != null) chooseSubtitle(pick)
    }

    BackHandler {
        when {
            volumeOpen -> closeVolume()
            panelOpen -> closePanel()
            // Nothing is playing: Back leaves at once, as the message says.
            error != null -> { player.pause(); stopAndReport(); source = null; app.back() }
            // The credits tiles: Back puts them away and the credits carry on.
            tilesVisible -> tilesDismissed = true
            // Paused with the controls showing: the first Back clears them too, the second leaves.
            // Back with the controls showing always just hides them.
            controlsVisible -> { quickHide = true; hiddenAt = System.currentTimeMillis(); controlsVisible = false }
            // A second press while they're still fading out is the same press arriving twice: ignore it.
            System.currentTimeMillis() - hiddenAt < 450 -> Unit
            else -> {
                // Silence straight away: the player itself is only let go once the screen has finished fading out.
                player.pause()
                stopAndReport()
                source = null
                app.back()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                // Back with the controls showing hides them (settings and the volume bar handle their own Back).
                // Taken here so nothing underneath can use it up first (that only un-selected the button).
                if (event.key == Key.Back && controlsVisible && !panelOpen && !volumeOpen && error == null) {
                    if (event.type == KeyEventType.KeyUp) {
                        quickHide = true; hiddenAt = System.currentTimeMillis(); controlsVisible = false
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type == KeyEventType.KeyDown) {
                    lastInteraction = System.currentTimeMillis()
                    when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (player.isPlaying) player.pause() else player.play(); poke(); return@onPreviewKeyEvent true }
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); return@onPreviewKeyEvent true }
                        AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); poke(); return@onPreviewKeyEvent true }
                        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { skip(1); poke(); return@onPreviewKeyEvent true }
                        AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> { skip(-1); poke(); return@onPreviewKeyEvent true }
                    }
                }
                false
            }
            .onKeyEvent { event ->
                // Only reached when nothing on screen handled the key, i.e. the controls are hidden.
                if (event.type != KeyEventType.KeyDown || controlsVisible) return@onKeyEvent false
                when (event.key) {
                    // Left / Right skip 10 seconds without bringing the controls up (hold to keep going);
                    // OK, Up and Down just bring them back.
                    Key.DirectionLeft, Key.DirectionRight -> {
                        val now = System.currentTimeMillis()
                        if (event.nativeKeyEvent.repeatCount == 0 || now - lastHeldSkip >= 140) {
                            lastHeldSkip = now
                            skip(if (event.key == Key.DirectionLeft) -1 else 1)
                        }
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown -> { poke(); true }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        // Paused for a long while: the TV's screensaver is allowed to come on, rather than a still picture being held on screen for ever.
        var pausedLong by remember { mutableStateOf(false) }
        LaunchedEffect(playing, buffering, error) {
            pausedLong = false
            if ((!playing && !buffering) || error != null) { delay(5 * 60_000L); pausedLong = true }
        }
        val pictureSubtitles = source?.subtitleStreams?.firstOrNull { it.index == subtitleIndex }?.let { !it.isTextSubtitleStream } == true
        AndroidView(
            update = {
                it.keepScreenOn = !pausedLong
                // The player draws only picture subtitles; text ones are drawn by SubtitleLayer below.
                it.subtitleView?.visibility = if (pictureSubtitles) android.view.View.VISIBLE else android.view.View.GONE
            },
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    subtitleView?.let { SubtitleLook.applyPicture(it, repo.store) }
                    this.player = player
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Text subtitles. With a timing shift, the whole subtitle file is fetched once and read at (position − shift).
        LaunchedEffect(item.id) { subShiftMs = 0L; shiftedCues = null; subShiftNote = null }
        val textSubtitle = source?.subtitleStreams?.firstOrNull { it.index == subtitleIndex }?.takeIf { it.isTextSubtitleStream }
        LaunchedEffect(textSubtitle?.index, subShiftMs != 0L, source?.item?.id) {
            val s = source ?: return@LaunchedEffect
            val idx = textSubtitle?.index ?: return@LaunchedEffect
            if (subShiftMs == 0L || shiftedCues?.first == idx) return@LaunchedEffect
            subShiftNote = "Loading the subtitles to shift them…"
            val cues = runCatching { SubtitleText.fetch(repo.api.baseUrl.orEmpty(), repo.api.accessToken.orEmpty(), s, idx) }.getOrNull()
            if (cues.isNullOrEmpty()) subShiftNote = "Couldn't load these subtitles to shift them"
            else { shiftedCues = idx to cues; subShiftNote = null }
        }
        val useShifted = subShiftMs != 0L && textSubtitle != null && shiftedCues?.first == textSubtitle.index
        var subClock by remember { mutableLongStateOf(0L) }
        LaunchedEffect(useShifted) {
            while (useShifted) { subClock = player.currentPosition; delay(40) }
        }
        val lines by remember(useShifted) {
            derivedStateOf {
                if (useShifted) SubtitleText.at(shiftedCues!!.second, subClock - subShiftMs) else playerLines
            }
        }
        val lift by androidx.compose.animation.core.animateDpAsState(if (controlsVisible) 118.dp else 0.dp, tween(220), label = "subLift")
        if (textSubtitle != null) SubtitleLayer(lines, subLook, lift = lift)

        // Short waits (like after a skip) don't show the loading dots; only real buffering does.
        LaunchedEffect(buffering) {
            if (buffering) { delay(700); showBuffering = true } else showBuffering = false
        }
        // Not moving for 15 s (25 s on a conversion) although it should be playing: ask the server to convert it once, and if that is no better, say so.
        // (Judged by the picture not advancing, not by the buffering flag: a stream that flips between "ready" and "buffering" over
        // and over never held that flag long enough, and the spinner went on for ever.)
        var stalledFor by remember { mutableIntStateOf(0) }
        /** What happened on the first try, when a stalled direct play was handed to the server to convert (for the error code). */
        var firstTry by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(source) {
            if (source == null) return@LaunchedEffect
            var last = -1L
            var lastBuffered = -1L
            var idle = 0
            while (true) {
                delay(1_000)
                val pos = player.currentPosition
                val buf = player.bufferedPosition
                // Stuck = not playing AND nothing new arriving. A slow conversion that is still delivering isn't stuck yet.
                val stuck = error == null && player.playWhenReady && !player.isPlaying && pos == last && buf <= lastBuffered
                last = pos
                lastBuffered = buf
                idle = if (stuck) idle + 1 else 0
                stalledFor = idle
                // Direct play that hasn't started after 15 s is retried as a server conversion; a conversion gets the full 25 s.
                val limit = if (source?.method != PlayMethod.TRANSCODE && !triedTranscode) 15 else 25
                if (idle >= limit) {
                    val stuckFor = idle
                    idle = 0
                    val s = source
                    if (s != null && s.method != PlayMethod.TRANSCODE && !triedTranscode) {
                        val mbps = s.mediaSource.bitrate?.let { b -> "%.0f Mb/s".format(b / 1_000_000f) } ?: "? Mb/s"
                        firstTry = "First try (${if (s.method == PlayMethod.DIRECT_PLAY) "direct" else "repackaged"}, file $mbps): stuck ${stuckFor} s at ${clockText(player.currentPosition)}, buffered to ${clockText(player.bufferedPosition)}"
                        triedTranscode = true
                        load(item, player.currentPosition, audioIndex, subtitleIndex, forceTranscode = true)
                        return@LaunchedEffect
                    } else if (s != null) {
                        error = "The server is converting this video too slowly to play it.\nIt may be too demanding for your server, or the server is busy.\n\n" +
                            "Code B31 · stuck ${stuckFor} s at ${clockText(player.currentPosition)}, buffered to ${clockText(player.bufferedPosition)}\n${describeSource(s)}" +
                            (firstTry?.let { "\n$it" } ?: "")
                        return@LaunchedEffect
                    }
                }
            }
        }
        if (showBuffering && error == null && System.currentTimeMillis() - skipStamp > 1500) {
            // The spinner stays dead centre; the note sits under it, centred, without pushing it anywhere.
            BufferingIndicator(Modifier.align(Alignment.Center))
            if (stalledFor >= 8) Column(
                Modifier.align(Alignment.Center).offset(y = 64.dp).widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Still starting… " + when (source?.method) { PlayMethod.DIRECT_PLAY -> "playing direct from the server"; PlayMethod.DIRECT_STREAM -> "the server is repackaging it"; PlayMethod.TRANSCODE -> "the server is converting it"; else -> "" },
                    style = BlushType.caption, color = Blush.colors.textMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                source?.let { Text(describeSource(it), style = BlushType.caption.copy(fontSize = 10.sp), color = Blush.colors.textDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }

        // The skip flash, on the side you skipped towards.
        var flashVisible by remember { mutableStateOf(false) }
        LaunchedEffect(skipStamp) {
            if (skipStamp == 0L) return@LaunchedEffect
            flashVisible = true
            delay(900)
            flashVisible = false
        }
        AnimatedVisibility(
            flashVisible,
            enter = fadeIn(tween(80)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            SkipFlash(skipTotal)
        }

        if (error != null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val center = androidx.compose.ui.text.style.TextAlign.Center
                Text("Something went wrong", style = BlushType.headline, textAlign = center)
                Text(error!!, style = BlushType.body, color = Blush.colors.textMuted, textAlign = center, modifier = Modifier.widthIn(max = 620.dp))
                Text("Press back to return.", style = BlushType.body, color = Blush.colors.textDim, textAlign = center)
            }
        }

        // Skip intro: on its own for the first 5 seconds of the intro, and with the controls whenever they're up.
        AnimatedVisibility(
            skipVisible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(if (promptQuickExit) 160 else 900)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = PromptEnd, bottom = PromptBottom).onFocusChanged { promptHadFocus = it.hasFocus },
        ) {
            PromptPill(
                "Skip intro",
                onClick = {
                    promptQuickExit = true
                    introPrompt = false
                    segments.firstOrNull { it.type == MediaSegmentType.INTRO }?.let { player.seekTo(it.endTicks / TICKS_PER_MS) }
                    // Skipping from the controls closes them too, straight back to the picture.
                    if (controlsVisible) { quickHide = true; hiddenAt = System.currentTimeMillis(); controlsVisible = false }
                },
                countdown = if (introPrompt) ({ introTimer.value }) else null,
                modifier = Modifier.focusRequester(skipFocus).focusProperties { down = if (controlsVisible) seekFocus else FocusRequester.Default },
            )
        }

        // The controls fade away clearly when left alone: a slower fade, with the top and bottom bars drifting off the edges.
        AnimatedVisibility(
            controlsVisible && !panelOpen && error == null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(if (quickHide) 220 else 650)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color(0xCC000000), 0.2f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color(0xEB000000),
                        ),
                    ),
                )
                // Top: title on the left; subtitles, volume and settings on the right.
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .animateEnterExit(enter = slideInVertically(tween(200)) { -it / 2 }, exit = slideOutVertically(tween(if (quickHide) 220 else 650)) { -it })
                        .fillMaxWidth()
                        .padding(start = 26.dp, end = 20.dp, top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val current = item
                        // The title's logo (the series' for an episode), about the size the name was; the name if there's none.
                        val logo = repo.logoUrl(current, 600)
                        var logoFailed by remember(logo) { mutableStateOf(false) }
                        val name = if (current.type == BaseItemKind.EPISODE) current.seriesName.orEmpty() else current.name.orEmpty()
                        if (logo != null && !logoFailed) {
                            LogoImage(logo, area = 4600f, maxWidth = 210.dp, maxHeight = 40.dp, onFailed = { logoFailed = true })
                        } else {
                            Text(name, style = BlushType.subhead.copy(), maxLines = 1)
                        }
                        if (current.type == BaseItemKind.EPISODE) {
                            Text(
                                listOfNotNull(current.episodeCode()?.replace(":", " · "), current.name).joinToString(" · "),
                                style = BlushType.body,
                                color = Blush.colors.text,
                                maxLines = 1,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (nextEpisode != null) TopIcon(Icons.Next, "Next episode", onClick = { playNext() })
                        TopIcon(
                            if (subtitlesOn) Icons.SubtitlesOn else Icons.Subtitles,
                            if (subtitlesOn) "Subtitles on" else "Subtitles off",
                            active = subtitlesOn,
                            onClick = { toggleSubtitles(); poke() },
                        )
                        TopIcon(
                            if (volume <= 0f) Icons.VolumeOff else if (volume != 1f) Icons.VolumeOn else Icons.Volume,
                            "Volume ${(volume * 100).roundToInt()}%",
                            active = volume != 1f,
                            onClick = { volumeOpen = true; poke() },
                            modifier = Modifier.focusRequester(volumeFocus).onFocusChanged { volumeFocused = it.hasFocus },
                        )
                        TopIcon(
                            Icons.More,
                            "Settings",
                            onClick = { panelOpen = true; poke() },
                            modifier = Modifier.focusRequester(settingsFocus).onFocusChanged { settingsFocused = it.hasFocus },
                        )
                    }
                }

                // Bottom: times either side of the timeline, and the three main buttons beneath.
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .animateEnterExit(enter = slideInVertically(tween(200)) { it / 2 }, exit = slideOutVertically(tween(if (quickHide) 220 else 650)) { it })
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // Each time sits centred in the space between the screen edge and the bar, level with the bar.
                        Box(Modifier.width(TimeGutter), contentAlignment = Alignment.Center) {
                            Text(clockText(position), style = BlushType.label, color = Blush.colors.text, maxLines = 1)
                        }
                        SeekBar(
                            position = position,
                            duration = duration,
                            buffered = buffered,
                            onActivity = { poke() },
                            onSeek = { target ->
                                player.seekTo(target)
                                position = target
                            },
                            onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
                            // Up from the timeline goes to Skip intro / the credits prompt while one is on screen.
                            modifier = Modifier.weight(1f).focusRequester(seekFocus).focusProperties {
                                up = when {
                                    skipVisible -> skipFocus
                                    creditsPromptVisible -> creditsFocus
                                    else -> FocusRequester.Default
                                }
                            },
                        )
                        Box(Modifier.width(TimeGutter), contentAlignment = Alignment.Center) {
                            Text("-" + clockText((duration - position).coerceAtLeast(0)), style = BlushType.label, color = Blush.colors.text, maxLines = 1)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        SkipHint(back = true)
                        PlayPauseButton(
                            playing = playing,
                            onClick = { if (player.isPlaying) player.pause() else player.play(); poke() },
                            modifier = Modifier
                                .focusRequester(playPauseFocus)
                                .onFocusChanged { playPauseFocused = it.hasFocus }
                                // Left / Right here skip 10 seconds; holding them keeps going (and the total builds up).
                                .onPreviewKeyEvent { e ->
                                    val dir = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> return@onPreviewKeyEvent false }
                                    if (e.type == KeyEventType.KeyDown) {
                                        val now = System.currentTimeMillis()
                                        if (e.nativeKeyEvent.repeatCount == 0 || now - lastHeldSkip >= 140) {
                                            lastHeldSkip = now
                                            skip(dir)
                                        }
                                        poke()
                                    }
                                    true
                                },
                        )
                        SkipHint(back = false)
                    }
                }

                if (volumeOpen) {
                    // Sits under the volume button (the second icon from the right).
                    VolumeSlider(
                        value = volume,
                        onChange = { v ->
                            volume = v
                            repo.store.playerVolume = v
                            applyVolume(v)
                            poke()
                        },
                        onClose = { closeVolume() },
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 58.dp, top = 58.dp),
                    )
                }
            }
        }

        // The credits of an episode with one after it: Next episode and Skip credits (to whatever follows them), or just Next
        // episode when the credits run to the end.
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        AnimatedVisibility(
            creditsPromptVisible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(if (promptQuickExit) 160 else 600)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = PromptEnd, bottom = PromptBottom).onFocusChanged { promptHadFocus = it.hasFocus },
        ) {
            // What the prompt shows is fixed while it's up, so it can't change into other buttons while it fades away (the next
            // episode loading changes the length the "credits run to the end" check is worked out from).
            val toEnd = remember(creditsPromptVisible) { creditsToEnd }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.onKeyEvent { e ->
                    // Left / Right move between the buttons here (on the player they'd skip 10 seconds).
                    val d = when (e.key) {
                        Key.DirectionLeft -> androidx.compose.ui.focus.FocusDirection.Left
                        Key.DirectionRight -> androidx.compose.ui.focus.FocusDirection.Right
                        else -> return@onKeyEvent false
                    }
                    if (e.type == KeyEventType.KeyDown) focusManager.moveFocus(d)
                    true
                },
            ) {
                val down = Modifier.focusProperties { down = if (controlsVisible) seekFocus else FocusRequester.Default }
                // A quiet ✕ (watch the credits), Next episode (selected), and a round skip button (to whatever follows the credits).
                SubtleClose("Watch the credits", onClick = { promptQuickExit = true; creditsDismissed = true }, modifier = down)
                PromptPill("Next episode", onClick = { promptQuickExit = true; playNext() }, modifier = Modifier.focusRequester(creditsFocus).then(down))
                if (!toEnd) PromptIconButton(
                    Icons.SkipAhead, "Skip credits",
                    onClick = {
                        promptQuickExit = true
                        segments.firstOrNull { it.type == MediaSegmentType.OUTRO }?.let { player.seekTo(it.endTicks / TICKS_PER_MS) }
                    },
                    modifier = Modifier.focusRequester(creditsNextFocus).then(down),
                )
            }
        }

        // A film's (or a show's last episode's) credits: titles to watch next. Choosing one opens its page.
        AnimatedVisibility(
            tilesVisible,
            enter = slideInVertically(tween(450, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(450)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
        ) {
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color(0x99000000), 1f to Color(0xE6000000)))
                    .onFocusChanged { promptHadFocus = it.hasFocus }
                    .padding(start = 44.dp, end = 44.dp, top = 56.dp, bottom = 34.dp),
            ) {
                // Five wide tiles across the whole width, like Continue watching; the one with the remote is described above them.
                val gap = 14.dp
                val tileWidth = (maxWidth - gap * 4) / 5
                var shown by remember { mutableStateOf(creditsTiles.firstOrNull()) }
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.onKeyEvent { e ->
                        // The arrows move between the tiles and Dismiss (on the player they'd skip or bring up the controls).
                        val d = when (e.key) {
                            Key.DirectionLeft -> androidx.compose.ui.focus.FocusDirection.Left
                            Key.DirectionRight -> androidx.compose.ui.focus.FocusDirection.Right
                            Key.DirectionUp -> androidx.compose.ui.focus.FocusDirection.Up
                            Key.DirectionDown -> androidx.compose.ui.focus.FocusDirection.Down
                            else -> return@onKeyEvent false
                        }
                        if (e.type == KeyEventType.KeyDown) focusManager.moveFocus(d)
                        true
                    },
                ) {
                    // Pulled left by the pill's inset so the ✕ lines up with the first tile's edge.
                    SubtleClose("Dismiss", onClick = { tilesDismissed = true }, label = "Dismiss", modifier = Modifier.offset(x = -CloseInset))
                    shown?.let { t ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth(0.62f).height(76.dp)) {
                            Text(t.displayTitle(), style = BlushType.headline, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            val runtime = t.runTimeTicks?.let { r -> val m = r / 600_000_000L; if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }
                            Text(
                                listOfNotNull(t.productionYear?.toString(), runtime, t.genres?.take(2)?.joinToString(", ")?.ifEmpty { null }).joinToString(" · "),
                                style = BlushType.caption, color = Blush.colors.textSoft, maxLines = 1,
                            )
                            t.overview?.let { Text(it, style = BlushType.caption, color = Blush.colors.textMuted, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        creditsTiles.forEachIndexed { i, t ->
                            val hasThumb = repo.hasThumb(t)
                            TitleCard(
                                title = t.displayTitle(),
                                imageUrl = repo.thumbUrl(t, 520),
                                logoUrl = if (hasThumb) null else repo.logoUrl(t, 500),
                                imageHasTitle = hasThumb,
                                width = tileWidth,
                                onClick = { openFromCredits(t) },
                                onFocused = { shown = t },
                                modifier = if (i == 0) Modifier.focusRequester(tileFocus) else Modifier,
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            panelOpen,
            enter = slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(220)),
            exit = slideOutHorizontally(tween(180)) { it / 3 } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            val s = source
            SettingsPanel(
                audio = s?.audioStreams.orEmpty(),
                subtitles = s?.subtitleStreams.orEmpty(),
                selectedAudio = audioIndex,
                selectedSubtitle = subtitleIndex,
                onAudio = { chooseAudio(it) },
                onSubtitle = { chooseSubtitle(it) },
                subtitleSize = repo.store.subtitleSize,
                onSubtitleSize = { step ->
                    val names = SubtitleLook.sizes.map { it.first }
                    val at = names.indexOf(repo.store.subtitleSize).let { if (it < 0) 2 else it }
                    repo.store.subtitleSize = names[(at + step).coerceIn(0, names.lastIndex)]
                    subLook = SubtitleLook.of(repo.store)
                },
                subtitleShift = if (s?.subtitleStreams?.firstOrNull { it.index == subtitleIndex }?.isTextSubtitleStream == true) subShiftMs else null,
                subtitleShiftNote = subShiftNote,
                onSubtitleShift = { subShiftMs = it.coerceIn(-30_000L, 30_000L) },
                quality = qualityCap,
                onQuality = { cap ->
                    if (cap != qualityCap) {
                        qualityCap = cap
                        val at = player.currentPosition
                        // If the file is bigger than the chosen limit, make sure the server shrinks it (otherwise it
                        // could carry on sending the original file and nothing would change).
                        val fileBitrate = s?.mediaSource?.bitrate ?: 0
                        val shrink = cap != null && (fileBitrate == 0 || fileBitrate > cap)
                        scope.launch { load(item, at, audioIndex, subtitleIndex, forceTranscode = shrink) }
                    }
                },
                qualityNow = s?.let { qualityNowText(it) },
                speed = speed,
                onSpeed = { chooseSpeed(it) },
                info = s?.let {
                    val showing = player.currentTracks.groups.any { g -> g.type == C.TRACK_TYPE_TEXT && g.isSelected }
                    statsForNerds(
                        s = it, player = player, videoDecoder = videoDecoder, audioDecoder = audioDecoder,
                        dropped = droppedFrames, gaps = audioGaps, audioIndex = audioIndex, subtitleIndex = subtitleIndex,
                        subtitlesShowing = showing, speed = speed, soundDecodedHere = audioSink.decodeHere,
                        bufferedAheadMs = (buffered - position).coerceAtLeast(0),
                    ) + buildList {
                        // What the server sent for the intro and credits (Bookend), to check timings against.
                        add(StatRow("Intro & credits"))
                        fun span(t: MediaSegmentType) = segments.firstOrNull { seg -> seg.type == t }
                            ?.let { seg -> clockText(seg.startTicks / TICKS_PER_MS) + " – " + clockText(seg.endTicks / TICKS_PER_MS) } ?: "None"
                        add(StatRow("Intro", span(MediaSegmentType.INTRO)))
                        add(StatRow("Credits", span(MediaSegmentType.OUTRO)))
                        if (nextEpisode == null) add(StatRow("Titles for the credits", if (creditsTiles.isEmpty()) "None" else "${creditsTiles.size}"))
                    }
                }.orEmpty(),
                onClose = { closePanel() },
            )
        }
    }
}

/**
 * Point ExoPlayer at the audio and subtitle tracks matching Jellyfin's stream indexes.
 * Embedded tracks are matched by their order in the file; separate subtitle files by the id we gave them.
 */
@OptIn(UnstableApi::class)
private fun applyTracks(player: ExoPlayer, source: PlaybackSource, audioIndex: Int?, subtitleIndex: Int?) {
    val groups = player.currentTracks.groups
    val builder = player.trackSelectionParameters.buildUpon()

    // A converted stream carries only the audio track that was asked for, so there's nothing to pick there.
    val audioGroups = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val embeddedAudio = source.audioStreams.filter { !it.isExternal }
    val audioPos = embeddedAudio.indexOfFirst { it.index == audioIndex }
    if (source.method != PlayMethod.TRANSCODE && audioPos >= 0 && audioPos < audioGroups.size) {
        builder.setOverrideForType(TrackSelectionOverride(audioGroups[audioPos].mediaTrackGroup, 0))
    }

    val textGroups = groups.filter { it.type == C.TRACK_TYPE_TEXT }
    if (subtitleIndex == null || subtitleIndex < 0) {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    } else {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        val external = source.externalSubtitles.firstOrNull { it.streamIndex == subtitleIndex }
        val group = if (external != null) {
            textGroups.firstOrNull { g -> g.getTrackFormat(0).id?.contains("jf-${external.streamIndex}") == true }
                ?: textGroups.firstOrNull { g -> g.getTrackFormat(0).label == external.label }
        } else {
            val externalIds = source.externalSubtitles.map { "jf-${it.streamIndex}" }
            val embeddedGroups = textGroups.filter { g -> externalIds.none { id -> g.getTrackFormat(0).id?.contains(id) == true } }
            val embedded = source.subtitleStreams.filter { !it.isExternal }
            val pos = embedded.indexOfFirst { it.index == subtitleIndex }
            embeddedGroups.getOrNull(pos)
        }
        if (group != null) builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
    }
    player.trackSelectionParameters = builder.build()
}

/** Decoders that run on the processor rather than dedicated video hardware. */
private fun isSoftware(decoder: String) =
    decoder.startsWith("c2.android.") || decoder.startsWith("OMX.google.") || decoder.contains("ffmpeg", ignoreCase = true)

private fun decoderText(decoder: String?) = when {
    decoder == null -> "starting…"
    isSoftware(decoder) -> "software ($decoder)"
    else -> "hardware ($decoder)"
}

/** One line of the stats panel. A line with no value is a section heading. */
private class StatRow(val label: String, val value: String = "")

private fun bitrateText(bps: Int?): String? = bps?.takeIf { it > 0 }?.let { if (it >= 1_000_000) "%.1f Mbps".format(it / 1_000_000.0) else "${it / 1000} kbps" }

private fun channelsText(channels: Int?, layout: String?): String? = when {
    channels == null -> layout
    layout != null && layout.isNotBlank() -> "$channels ch ($layout)"
    channels == 1 -> "Mono"
    channels == 2 -> "Stereo"
    else -> "$channels ch"
}

/** The technical details of what is playing and how, grouped like a media player's "stats for nerds". */
@OptIn(UnstableApi::class)
private fun statsForNerds(
    s: PlaybackSource,
    player: ExoPlayer,
    videoDecoder: String?,
    audioDecoder: String?,
    dropped: Int,
    gaps: Int,
    audioIndex: Int?,
    subtitleIndex: Int?,
    subtitlesShowing: Boolean,
    speed: Float,
    soundDecodedHere: Boolean,
    bufferedAheadMs: Long,
): List<StatRow> {
    val streams = s.mediaSource.mediaStreams.orEmpty()
    val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
    val audio = streams.firstOrNull { it.type == MediaStreamType.AUDIO && it.index == audioIndex }
        ?: streams.firstOrNull { it.type == MediaStreamType.AUDIO }
    val vf = player.videoFormat
    val af = player.audioFormat
    val reasons = s.url.substringAfter("TranscodeReasons=", "").substringBefore('&')
        .let { android.net.Uri.decode(it) }
        .split(',').filter { it.isNotBlank() }
    val method = when (s.method) {
        PlayMethod.DIRECT_PLAY -> "Direct play"
        PlayMethod.DIRECT_STREAM -> "Direct stream (remuxed by the server)"
        else -> "Transcode" + if (reasons.isEmpty()) "" else " (${reasons.joinToString(", ")})"
    }
    return buildList {
        add(StatRow("Playback"))
        add(StatRow("Method", method))
        s.mediaSource.container?.let { add(StatRow("Container", it.uppercase())) }
        bitrateText(s.mediaSource.bitrate)?.let { add(StatRow("Source bitrate", it)) }
        s.mediaSource.size?.takeIf { it > 0 }?.let { add(StatRow("File size", "%.2f GB".format(it / 1_073_741_824.0))) }
        add(StatRow("Playback speed", speedLabel(speed)))

        if (video != null) {
            add(StatRow("Video"))
            val profile = listOfNotNull(video.profile, video.level?.takeIf { it > 0 }?.let { "L${if (it >= 10) (it / 10.0).toString().removeSuffix(".0") else it.toString().removeSuffix(".0")}" }).joinToString(" ")
            add(StatRow("Codec", listOfNotNull(video.codec?.uppercase(), profile.ifBlank { null }).joinToString(" · ")))
            if (video.width != null && video.height != null) add(StatRow("Resolution", "${video.width}×${video.height}"))
            val fps = (vf?.frameRate?.takeIf { it > 0f }) ?: video.averageFrameRate ?: video.realFrameRate
            fps?.takeIf { it > 0f }?.let { add(StatRow("Frame rate", "%.3f fps".format(it).replace(Regex("\\.?0+ fps"), " fps"))) }
            video.bitDepth?.let { add(StatRow("Bit depth", "$it-bit")) }
            video.videoRangeType.serialName.takeIf { it != "Unknown" }?.let { add(StatRow("Dynamic range", it)) }
            bitrateText(video.bitRate ?: vf?.bitrate?.takeIf { it > 0 })?.let { add(StatRow("Video bitrate", it)) }
            add(StatRow("Decoder", decoderText(videoDecoder)))
            add(StatRow("Dropped frames", dropped.toString()))
        }

        if (audio != null) {
            add(StatRow("Audio"))
            add(StatRow("Codec", listOfNotNull(audio.codec?.uppercase(), audio.profile).joinToString(" · ")))
            channelsText(audio.channels ?: af?.channelCount?.takeIf { it > 0 }, audio.channelLayout)?.let { add(StatRow("Channels", it)) }
            (audio.sampleRate ?: af?.sampleRate?.takeIf { it > 0 })?.let { add(StatRow("Sample rate", "%.1f kHz".format(it / 1000.0).replace(".0 kHz", " kHz"))) }
            bitrateText(audio.bitRate ?: af?.bitrate?.takeIf { it > 0 })?.let { add(StatRow("Audio bitrate", it)) }
            audio.language?.let { add(StatRow("Language", languageName(it))) }
            add(StatRow("Decoder", decoderText(audioDecoder)))
            add(StatRow("Output", if (soundDecodedHere) "Decoded on this device" else "Passed through to the TV/receiver"))
            add(StatRow("Audio underruns", gaps.toString()))
        }

        add(StatRow("Subtitles"))
        val sub = s.subtitleStreams.firstOrNull { it.index == subtitleIndex }
        if (sub == null) {
            add(StatRow("Track", "Off"))
        } else {
            add(StatRow("Track", listOfNotNull(sub.language?.let { languageName(it) }, sub.codec?.uppercase()).joinToString(" · ")))
            add(
                StatRow(
                    "Delivery",
                    when (sub.deliveryMethod) {
                        SubtitleDeliveryMethod.EXTERNAL -> "Separate file"
                        SubtitleDeliveryMethod.EMBED -> "Inside the video file"
                        SubtitleDeliveryMethod.ENCODE -> "Burned into the picture by the server"
                        SubtitleDeliveryMethod.HLS -> "In the stream"
                        else -> "Unknown"
                    },
                ),
            )
            if (sub.deliveryMethod != SubtitleDeliveryMethod.ENCODE) add(StatRow("Rendering", if (subtitlesShowing) "Active" else "Not picked up by the player"))
        }

        add(StatRow("Buffer"))
        add(StatRow("Buffered ahead", "%.1f s".format(bufferedAheadMs / 1000.0)))
    }
}

/** Loading: a slim accent arc sweeping round a faint ring, on a soft dark disc. */
@Composable
private fun BufferingIndicator(modifier: Modifier = Modifier) {
    val spin = androidx.compose.animation.core.rememberInfiniteTransition(label = "loading")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(900, easing = androidx.compose.animation.core.LinearEasing)),
        label = "angle",
    )
    // The arc also grows and shrinks a little as it turns, so it feels alive rather than mechanical.
    val sweep by spin.animateFloat(
        initialValue = 50f,
        targetValue = 130f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "sweep",
    )
    val accent = Blush.colors.accent.color
    Box(modifier.size(58.dp).clip(CircleShape).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(34.dp)) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(Color.White.copy(alpha = 0.14f), style = stroke)
            drawArc(accent, startAngle = angle - 90f, sweepAngle = sweep, useCenter = false, style = stroke)
        }
    }
}

/**
 * The timeline. Left/Right move a marker without seeking straight away, getting faster the longer you hold;
 * the film jumps there once you let go (or press OK), so scrubbing a long way is quick and doesn't stall.
 */
@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    buffered: Long,
    onActivity: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var scrub by remember { mutableStateOf<Long?>(null) }
    var held by remember { mutableStateOf(false) }
    var repeats by remember { mutableIntStateOf(0) }
    // Seek a moment after the last press, once the button is released.
    LaunchedEffect(scrub, held) {
        val target = scrub ?: return@LaunchedEffect
        if (held) return@LaunchedEffect
        delay(450)
        onSeek(target)
        scrub = null
    }
    val shown = scrub ?: position
    val fraction = if (duration > 0) (shown.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .height(26.dp)
            .onKeyEvent { e ->
                val dir = when (e.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> 0
                    else -> return@onKeyEvent false
                }
                // OK: jumps straight to a position you're scrubbing to; otherwise pauses or plays.
                if (dir == 0) {
                    if (e.type == KeyEventType.KeyDown) {
                        val target = scrub
                        if (target != null) { onSeek(target); scrub = null } else onTogglePlay()
                        onActivity()
                    }
                    return@onKeyEvent true
                }
                when (e.type) {
                    KeyEventType.KeyUp -> { held = false; repeats = 0 }
                    KeyEventType.KeyDown -> {
                        held = true
                        repeats++
                        val step = when {
                            repeats > 20 -> maxOf(120_000L, duration / 40)
                            repeats > 10 -> 60_000L
                            repeats > 3 -> 30_000L
                            else -> 10_000L
                        }
                        scrub = ((scrub ?: position) + dir * step).coerceIn(0, duration.coerceAtLeast(0))
                        onActivity()
                    }
                }
                true
            }
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackHeight = if (focused) 5.dp else 3.dp
        Box(Modifier.fillMaxWidth().height(trackHeight).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.2f)))
        Box(Modifier.fillMaxWidth(bufferedFraction).height(trackHeight).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.32f)))
        Box(Modifier.fillMaxWidth(fraction).height(trackHeight).clip(RoundedCornerShape(3.dp)).background(Blush.colors.accent.color))
        // Fills the bar's full height with everything centred on the line, so the time label floating above while
        // scrubbing can't push the playhead up off the line.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight()) {
            val knob = if (focused) 14.dp else 0.dp
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * fraction - knob / 2)
                    .size(knob)
                    .clip(CircleShape)
                    .background(Color.White),
            )
            // While scrubbing, the time you'll land on floats above the marker.
            val target = scrub
            if (target != null) {
                Text(
                    clockText(target),
                    style = BlushType.label,
                    color = Color(0xFF14151B),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (maxWidth * fraction - 30.dp).coerceIn(0.dp, maxWidth - 60.dp), y = (-24).dp)
                        .width(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .padding(vertical = 2.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SkipFlash(total: Int) {
    // The arrow shows the direction and the number inside it is the running total of seconds moved.
    Box(Modifier.size(84.dp).clip(CircleShape).background(Color(0x8C000000)), contentAlignment = Alignment.Center) {
        Icon(if (total < 0) Icons.Replay else Icons.Skip, null, Color.White, size = 56.dp)
        val digits = kotlin.math.abs(total).toString()
        CenteredNumber(digits, (if (digits.length >= 3) 14 else 18).sp)
    }
}

/** A number whose digits sit exactly in the middle of the space they're given (no extra font padding above or below). */
@Composable
private fun CenteredNumber(text: String, size: androidx.compose.ui.unit.TextUnit) {
    Text(
        text,
        style = BlushType.caption.copy(
            fontSize = size,
            fontWeight = FontWeight.W800,
            lineHeight = size,
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
            ),
        ),
        color = Color.White,
        textAlign = TextAlign.Center,
    )
}

/** Plain icon in the top-right corner. A soft circle shows focus; [active] icons (subtitles on) also get an accent line. */
@Composable
private fun TopIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) = TopIconSurface(icon, description, onClick, modifier, active)

@Composable
private fun TopIconSurface(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
    active: Boolean,
) {
    FocusSurface(
        onClick = onClick,
        shape = CircleShape,
        focusedScale = 1.08f,
        ringWidth = 0.dp,
        background = Color.White.copy(alpha = 0.10f),
        focusedBackground = Color(0xFFF5F5F7),
        modifier = modifier.size(32.dp),
    ) { focused ->
        Icon(icon, description, if (focused) Color(0xFF14151B) else Blush.colors.text, Modifier.align(Alignment.Center), size = 16.dp)
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .width(10.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Blush.colors.accent.color),
            )
        }
    }
}

/** A reminder that Left / Right skip 10 seconds. Not selectable: the arrow keys do the skipping. */
@Composable
private fun SkipHint(back: Boolean) {
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Icon(if (back) Icons.Replay else Icons.Skip, null, Blush.colors.textSoft, size = 26.dp)
        CenteredNumber("10", 8.sp)
    }
}

private val TimeGutter = 86.dp

@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(
        onClick = onClick,
        shape = CircleShape,
        focusedScale = 1.08f,
        ringWidth = 0.dp,
        background = Color.White.copy(alpha = 0.14f),
        focusedBackground = Color(0xFFF5F5F7),
        modifier = modifier.size(42.dp),
    ) { focused ->
        Icon(
            if (playing) Icons.Pause else Icons.Play,
            if (playing) "Pause" else "Play",
            if (focused) Color(0xFF14151B) else Color.White,
            Modifier.align(Alignment.Center).padding(start = if (playing) 0.dp else 2.dp),
            size = 18.dp,
        )
    }
}

/** Up and Down change the volume; any other direction, OK or Back closes it. */
@Composable
private fun VolumeSlider(value: Float, onChange: (Float) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(30); runCatching { focus.requestFocus() } }
    Column(
        modifier
            .width(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x9914151B))
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                if (e.key != Key.Back) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyUp) onClose()
                true
            }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> { onChange(((value * 10).roundToInt() + 1).coerceAtMost(15) / 10f); true }
                    Key.DirectionDown -> { onChange(((value * 10).roundToInt() - 1).coerceAtLeast(0) / 10f); true }
                    Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onClose(); true }
                    else -> false
                }
            }
            .focusable()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The bar goes up to 150%; a small notch marks 100%, and the boosted part above it is in the accent colour.
        Box(Modifier.width(14.dp).height(120.dp), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.fillMaxWidth().fillMaxHeight((value / 1.5f).coerceIn(0f, 1f)).background(Blush.colors.accent.color))
                Box(Modifier.fillMaxWidth().fillMaxHeight((value.coerceAtMost(1f) / 1.5f).coerceIn(0f, 1f)).background(Color.White))
            }
            Box(Modifier.fillMaxHeight(1f / 1.5f).width(14.dp), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.width(12.dp).height(1.5.dp).background(Color.White.copy(alpha = 0.6f)))
            }
        }
        Text("${(value * 100).roundToInt()}", style = BlushType.caption, color = Blush.colors.text)
    }
}

@Composable
private fun ChipButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        focusedScale = 1.05f,
        ringWidth = 0.dp,
        background = Color.White.copy(alpha = 0.12f),
        focusedBackground = Color.White.copy(alpha = 0.26f),
        modifier = Modifier.height(36.dp),
    ) {
        Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Blush.colors.text, size = 16.dp)
            Text(label, style = BlushType.label)
        }
    }
}

/** Where Skip intro and the credits buttons sit: one spot, above the controls, whether or not they're showing. */
private val PromptEnd = 44.dp
private val PromptBottom = 124.dp

/** A short line about the stream, for messages and codes: how it's played, the formats, and why the server is converting it. */
private fun describeSource(s: PlaybackSource): String {
    val streams = s.mediaSource.mediaStreams.orEmpty()
    val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
    val audio = streams.firstOrNull { it.type == MediaStreamType.AUDIO && it.index == s.audioIndex } ?: streams.firstOrNull { it.type == MediaStreamType.AUDIO }
    val how = when (s.method) { PlayMethod.DIRECT_PLAY -> "Direct"; PlayMethod.DIRECT_STREAM -> "Repackaged"; PlayMethod.TRANSCODE -> "Converted"; else -> "?" }
    val v = video?.let { listOfNotNull(it.codec?.uppercase(), it.profile, it.bitDepth?.let { b -> "$b-bit" }, it.height?.let { h -> "${h}p" }).joinToString(" ") }
    val a = audio?.let { listOfNotNull(it.codec?.uppercase(), it.channels?.let { c -> "${c}ch" }).joinToString(" ") }
    val reasons = Regex("TranscodeReasons=([^&]+)").find(s.url)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    return listOfNotNull(how, v, a, s.mediaSource.container?.uppercase(), reasons?.let { "why: $it" }).joinToString(" · ")
}

private val qualityOptions = listOf(
    "Default (from Settings)" to null,
    "20 Mbps · best" to 20_000_000,
    "10 Mbps" to 10_000_000,
    "6 Mbps" to 6_000_000,
    "3 Mbps · smallest" to 3_000_000,
)

/**
 * The slider is a loudness scale, not an amplitude one: halving the signal sounds only slightly quieter. Up to 100% the
 * level follows v^1.7 (50% ≈ -10 dB, which sounds about half as loud), and 100% is the file's own level.
 */
private fun loudnessFor(volume: Float): Float {
    val v = volume.coerceIn(0f, 1f)
    return Math.pow(v.toDouble(), 1.7).toFloat()
}

/** The same scale as a gain on the sound: as above up to 100%, then up to +9 dB at 150% (the processor limits peaks softly). */
private fun gainFor(volume: Float): Float =
    if (volume <= 1f) loudnessFor(volume)
    else Math.pow(10.0, (9.0 / 20.0) * ((volume.coerceAtMost(1.5f) - 1f) / 0.5)).toFloat()

private val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun streamName(stream: MediaStream, fallback: String) =
    stream.language?.let { languageName(it) } ?: stream.title ?: "$fallback ${stream.index}"

/** "Now: original file, 24 Mbps" / "Now: converted to 6 Mbps", shown above the quality choices. */
private fun qualityNowText(s: PlaybackSource): String {
    val mbps = { b: Int -> "%.0f Mbps".format(b / 1_000_000.0) }
    val sent = s.url.substringAfter("VideoBitrate=", "").substringBefore('&').toIntOrNull()
        ?: s.url.substringAfter("MaxStreamingBitrate=", "").substringBefore('&').toIntOrNull()
    return when (s.method) {
        PlayMethod.TRANSCODE -> "Now: converted by the server" + (sent?.let { ", about ${mbps(it)}" } ?: "")
        else -> "Now: the original file" + (s.mediaSource.bitrate?.let { ", ${mbps(it)}" } ?: "")
    }
}

private fun speedLabel(v: Float) = if (v == 1f) "Normal" else "${v.toString().removeSuffix(".0")}×"

/**
 * Everything you can change mid-film, in one panel that slides in from the right. Each setting is a closed
 * row showing its current choice; OK opens it to show the options, and picking one closes it again.
 */
@Composable
private fun SettingsPanel(
    audio: List<MediaStream>,
    subtitles: List<MediaStream>,
    selectedAudio: Int?,
    selectedSubtitle: Int?,
    onAudio: (Int) -> Unit,
    onSubtitle: (Int?) -> Unit,
    subtitleSize: String,
    onSubtitleSize: (Int) -> Unit,
    subtitleShift: Long?,
    subtitleShiftNote: String?,
    onSubtitleShift: (Long) -> Unit,
    quality: Int?,
    onQuality: (Int?) -> Unit,
    qualityNow: String?,
    speed: Float,
    onSpeed: (Float) -> Unit,
    info: List<StatRow>,
    onClose: () -> Unit,
) {
    var open by remember { mutableStateOf<String?>(null) }
    val headers = remember { mapOf("subs" to FocusRequester(), "audio" to FocusRequester(), "quality" to FocusRequester(), "speed" to FocusRequester(), "info" to FocusRequester()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { delay(50); runCatching { headers.getValue("subs").requestFocus() } }
    fun close(section: String) {
        open = null
        scope.launch { delay(30); runCatching { headers.getValue(section).requestFocus() } }
    }
    fun toggle(section: String) {
        open = if (open == section) null else section
    }

    val subtitleValue = subtitles.firstOrNull { it.index == selectedSubtitle }?.let { streamName(it, "Subtitles") } ?: "Off"
    val audioValue = audio.firstOrNull { it.index == selectedAudio }?.let { streamName(it, "Track") } ?: "Default"
    val qualityValue = qualityOptions.firstOrNull { it.second == quality }?.first?.substringBefore(" ·")?.substringBefore(" (") ?: "Default"

    // Back closes the panel here and now (and isn't passed on), so it never needs a second press.
    SidePanel(
        "Settings",
        onClose = onClose,
        floating = true,
        modifier = Modifier.onPreviewKeyEvent { e ->
            if (e.key != Key.Back) return@onPreviewKeyEvent false
            // Acts when the button is let go: closing on the press let the release carry on to the player,
            // which then hid its controls too (leaving nothing selected).
            if (e.type == KeyEventType.KeyUp) {
                onClose()
            }
            true
        },
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            item(key = "subs") { DropdownRow("Subtitles", subtitleValue, open == "subs", { toggle("subs") }, requester = headers.getValue("subs")) }
            if (open == "subs") {
                item(key = "subs-off") { OptionRow("Off", null, selected = selectedSubtitle == null || selectedSubtitle < 0, onClick = { onSubtitle(null); close("subs") }) }
                items(subtitles, key = { "s${it.index}" }) { stream ->
                    OptionRow(
                        streamName(stream, "Subtitles"),
                        if (stream.isTextSubtitleStream) stream.displayTitle else "${stream.displayTitle ?: "Picture subtitles"} · picture, keeps its own style",
                        selected = stream.index == selectedSubtitle,
                        onClick = { onSubtitle(stream.index); close("subs") },
                    )
                }
            }
            if (selectedSubtitle != null && selectedSubtitle >= 0) {
                item(key = "subsize") { StepperRow("Subtitle size", subtitleSize, "◀ ▶ to change", onStep = onSubtitleSize) }
            }
            if (subtitleShift != null) {
                item(key = "subshift") {
                    val v = when {
                        subtitleShift == 0L -> "In sync"
                        subtitleShift > 0 -> "%.1f s later".format(subtitleShift / 1000f)
                        else -> "%.1f s earlier".format(-subtitleShift / 1000f)
                    }
                    StepperRow(
                        "Subtitle timing", v,
                        subtitleShiftNote ?: "◀ earlier · later ▶ · OK puts it back",
                        onStep = { onSubtitleShift(subtitleShift + it * 100L) },
                        onClick = { onSubtitleShift(0L) },
                    )
                }
            }
            item(key = "audio") { DropdownRow("Audio", audioValue, open == "audio", { toggle("audio") }, requester = headers.getValue("audio")) }
            if (open == "audio") {
                items(audio, key = { "a${it.index}" }) { stream ->
                    OptionRow(streamName(stream, "Track"), stream.displayTitle, selected = stream.index == selectedAudio, onClick = { onAudio(stream.index); close("audio") })
                }
            }
            item(key = "quality") { DropdownRow("Quality", qualityValue, open == "quality", { toggle("quality") }, requester = headers.getValue("quality")) }
            if (open == "quality") {
                if (qualityNow != null) {
                    item(key = "q-now") {
                        Text(qualityNow, style = BlushType.caption, color = Blush.colors.textMuted, modifier = Modifier.padding(start = 22.dp, end = 10.dp, bottom = 2.dp))
                    }
                }
                items(qualityOptions, key = { "q${it.second}" }) { (label, cap) ->
                    OptionRow(label, null, selected = quality == cap, onClick = { onQuality(cap); close("quality") })
                }
            }
            item(key = "speed") { DropdownRow("Speed", speedLabel(speed), open == "speed", { toggle("speed") }, requester = headers.getValue("speed")) }
            if (open == "speed") {
                items(speedOptions, key = { "v$it" }) { v ->
                    OptionRow(speedLabel(v), null, selected = speed == v, onClick = { onSpeed(v); close("speed") })
                }
            }
            if (info.isNotEmpty()) {
                item(key = "info") { DropdownRow("Stats for nerds", "", open == "info", { toggle("info") }, requester = headers.getValue("info")) }
                if (open == "info") {
                    // One continuous, scrollable list: Up / Down scroll it, and Up at the top returns to the settings above.
                    item(key = "info-body") {
                        val scroll = rememberScrollState()
                        var bodyFocused by remember { mutableStateOf(false) }
                        val scrollScope = rememberCoroutineScope()
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = if (bodyFocused) 0.14f else 0.05f))
                                .onFocusChanged { bodyFocused = it.hasFocus }
                                .onPreviewKeyEvent { e ->
                                    val down = e.key == Key.DirectionDown
                                    val up = e.key == Key.DirectionUp
                                    if (!down && !up) return@onPreviewKeyEvent false
                                    // Reaching the top hands the remote straight back to the "Stats for nerds" row.
                                    if (down && scroll.value >= scroll.maxValue) return@onPreviewKeyEvent false
                                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                                    if (up && scroll.value - 110 <= 0) {
                                        scrollScope.launch { scroll.scrollTo(0) }
                                        runCatching { headers.getValue("info").requestFocus() }
                                    } else {
                                        scrollScope.launch { scroll.animateScrollTo((scroll.value + if (down) 110 else -110).coerceIn(0, scroll.maxValue)) }
                                    }
                                    true
                                }
                                .focusable()
                                .verticalScroll(scroll)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            info.forEach { row ->
                                if (row.value.isEmpty()) {
                                    Text(
                                        row.label.uppercase(), style = BlushType.kicker, color = Blush.colors.accent.color,
                                        modifier = Modifier.padding(bottom = 2.dp),
                                    )
                                } else {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(row.label, style = BlushType.caption, color = Blush.colors.textMuted, modifier = Modifier.width(92.dp))
                                        Text(row.value, style = BlushType.caption, color = Blush.colors.textSoft, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun languageName(code: String): String =
    runCatching { java.util.Locale.forLanguageTag(code).displayLanguage.takeIf { it.isNotBlank() && it != code } }.getOrNull()
        ?: code.uppercase()
