package app.blush.tv.playback

import app.blush.tv.BlushApplication
import app.blush.tv.data.logged
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import app.blush.tv.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.hlsSegmentApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CodecProfile
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.ProfileCondition
import org.jellyfin.sdk.model.api.ProfileConditionType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodingProfile

/** A subtitle file the server delivers separately from the video. */
data class ExternalSubtitle(val streamIndex: Int, val url: String, val mimeType: String, val language: String?, val label: String)

/** What the player needs to start a stream. */
data class PlaybackSource(
    val item: BaseItemDto,
    val url: String,
    val method: PlayMethod,
    val mediaSource: MediaSourceInfo,
    val playSessionId: String?,
    val externalSubtitles: List<ExternalSubtitle>,
    val audioStreams: List<MediaStream>,
    val subtitleStreams: List<MediaStream>,
    val audioIndex: Int?,
    val subtitleIndex: Int?,
)

/** Describes to Jellyfin exactly what this device can play, so it only converts what it must. */
object DeviceCapabilities {
    private data class VideoSupport(val codec: String, val mime: String, val tenBitProfiles: Set<Int>)

    private val candidates = listOf(
        VideoSupport("h264", "video/avc", setOf(MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10)),
        VideoSupport("hevc", "video/hevc", setOf(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus)),
        VideoSupport("vp9", "video/x-vnd.on2.vp9", setOf(MediaCodecInfo.CodecProfileLevel.VP9Profile2, MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR)),
        VideoSupport("av1", "video/av01", setOf(MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10, MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10)),
        VideoSupport("vp8", "video/x-vnd.on2.vp8", emptySet()),
        VideoSupport("mpeg2video", "video/mpeg2", emptySet()),
        VideoSupport("mpeg4", "video/mp4v-es", emptySet()),
    )

    /** Decoders that run on the processor rather than dedicated video hardware. */
    private fun isSoftwareCodec(name: String) =
        name.startsWith("c2.android.") || name.startsWith("OMX.google.") || name.startsWith("c2.google.")

    /** For each playable codec: does it handle 10-bit, and the largest frame it can decode. */
    private data class Decoder(val codec: String, val tenBit: Boolean, val maxWidth: Int, val maxHeight: Int, val profiles: Set<String> = emptySet())

    private val decoders: List<Decoder> by lazy {
        val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        candidates.mapNotNull { c ->
            val all = infos.filter { info -> info.supportedTypes.any { it.equals(c.mime, ignoreCase = true) } }
            // Google's built-in software decoders exist on every device, so counting them would tell the server the
            // stick can play AV1, VP9 and so on that it could only run as a slideshow. Only hardware decoders count;
            // plain H.264 is the one exception, since everything must be able to fall back to it.
            val hardware = all.filter { !isSoftwareCodec(it.name) }
            val matching = if (hardware.isNotEmpty()) hardware else if (c.codec == "h264") all else emptyList()
            if (matching.isEmpty()) return@mapNotNull null
            var tenBit = false
            var maxW = 0
            var maxH = 0
            val profiles = mutableSetOf<String>()
            matching.forEach { info ->
                logged("Playback") {
                    val caps = info.getCapabilitiesForType(c.mime)
                    // The names the server uses for a stream's profile, for the profiles this decoder lists.
                    caps.profileLevels.forEach { pl ->
                        when (c.codec) {
                            "h264" -> when (pl.profile) {
                                MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline, MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline -> { profiles += "baseline"; profiles += "constrained baseline" }
                                MediaCodecInfo.CodecProfileLevel.AVCProfileMain -> profiles += "main"
                                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh -> profiles += "high"
                                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 -> profiles += "high 10"
                                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422 -> profiles += "high 4:2:2"
                            }
                        }
                    }
                    if (caps.profileLevels.any { it.profile in c.tenBitProfiles }) tenBit = true
                    caps.videoCapabilities?.let { v ->
                        maxW = maxOf(maxW, v.supportedWidths.upper)
                        maxH = maxOf(maxH, v.supportedHeights.upper)
                    }
                }
            }
            // Every Fire TV that has a hardware HEVC decoder plays 10-bit; some report only "Main" there, which made the server
            // convert ordinary 10-bit films (slow, and it often stalled).
            if (c.codec == "hevc") { tenBit = true; profiles += "main"; profiles += "main 10" }
            // A decoder that listed nothing we recognise says nothing about profiles, so none are ruled out.
            if (c.codec == "h264" && profiles.none { it == "main" || it == "high" }) profiles.clear()
            // Main and Baseline always play on anything that plays H.264 at all.
            if (c.codec == "h264" && profiles.isNotEmpty()) { profiles += "main"; profiles += "baseline"; profiles += "constrained baseline"; profiles += "high" }
            Decoder(c.codec, tenBit, maxW, maxH, profiles)
        }
    }

    /** Whether the device has a Dolby Vision decoder at all (the cheaper sticks don't). */
    private val dolbyVision: Boolean by lazy {
        logged("Playback") {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals("video/dolby-vision", ignoreCase = true) }
            }
        }.getOrDefault(true)
    }

    // Anything the platform can't decode is handled by the bundled FFmpeg audio decoder.
    private val audioCodecs = listOf(
        "aac", "mp3", "mp2", "ac3", "eac3", "dts", "dca", "truehd", "mlp", "flac", "alac",
        "opus", "vorbis", "pcm_s16le", "pcm_s24le", "pcm_s32le", "pcm_f32le", "pcm_mulaw", "pcm_alaw",
    )

    private val containers = listOf("mp4", "m4v", "mkv", "webm", "mov", "ts", "mpegts", "m2ts", "avi", "flv", "ogg", "3gp")

    /** How many channels the TV or speakers take, and which surround formats can be passed straight through. */
    private data class AudioOutput(val maxChannels: Int, val passthrough: Set<String>)

    private fun audioOutput(context: Context): AudioOutput {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var channels = 2
        val passthrough = mutableSetOf<String>()
        outputs.forEach { d ->
            d.channelCounts.maxOrNull()?.let { channels = maxOf(channels, it) }
            d.encodings.forEach { e ->
                when (e) {
                    AudioFormat.ENCODING_AC3 -> passthrough += "ac3"
                    AudioFormat.ENCODING_E_AC3, AudioFormat.ENCODING_E_AC3_JOC -> passthrough += "eac3"
                    AudioFormat.ENCODING_DTS, AudioFormat.ENCODING_DTS_HD -> { passthrough += "dts"; passthrough += "dca" }
                    AudioFormat.ENCODING_DOLBY_TRUEHD -> passthrough += "truehd"
                }
            }
        }
        return AudioOutput(channels, passthrough)
    }

    /** [forPreview]: short muted-or-quiet clips, where decoding surround audio here is fine, so the server never converts audio. */
    fun profile(context: Context, maxBitrate: Int, forPreview: Boolean = false): DeviceProfile {
        val video = decoders.joinToString(",") { it.codec }
        val audio = audioCodecs.joinToString(",")
        val output = audioOutput(context)

        val baseProfiles = buildList {
            decoders.forEach { d ->
                val conditions = buildList {
                    if (!d.tenBit) add(ProfileCondition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.VIDEO_BIT_DEPTH, "8", false))
                    // Only the kinds of picture the decoder lists (no 4:2:2 / 4:4:4 / 12-bit streams that would fail after a long wait).
                    if (d.profiles.isNotEmpty() && (d.codec == "h264" || d.codec == "hevc")) {
                        add(ProfileCondition(ProfileConditionType.EQUALS_ANY, ProfileConditionValue.VIDEO_PROFILE, d.profiles.joinToString("|"), false))
                    }
                    if (d.tenBit && d.codec != "h264") add(ProfileCondition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.VIDEO_BIT_DEPTH, "10", false))
                    if (d.maxWidth > 0) add(ProfileCondition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.WIDTH, d.maxWidth.toString(), false))
                    if (d.maxHeight > 0) add(ProfileCondition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.HEIGHT, d.maxHeight.toString(), false))
                    // Dolby Vision with no ordinary HDR picture underneath (profile 5) shows in wrong colours without a Dolby Vision
                    // decoder, so the server converts it. Dolby Vision that carries HDR10/HLG/SDR as well still plays as that.
                    if (!dolbyVision && (d.codec == "hevc" || d.codec == "av1")) add(ProfileCondition(ProfileConditionType.NOT_EQUALS, ProfileConditionValue.VIDEO_RANGE_TYPE, "DOVI", false))
                }
                if (conditions.isNotEmpty()) add(CodecProfile(type = CodecType.VIDEO, codec = d.codec, conditions = conditions, applyConditions = emptyList()))
            }
            // On stereo outputs, have the server turn surround soundtracks into stereo. It only converts the audio
            // (the video is passed through untouched), so it's light work for the server, and it stops the device
            // having to decode heavy formats like DTS and TrueHD in software, which made audio stutter.
            if (output.maxChannels <= 2 && !forPreview) {
                // Only the heavy formats are mixed down by the server; AAC, AC3, E-AC3 and the like are decoded here (and mixed down
                // by the device), which keeps the server from converting a film just for its sound.
                val needsMixing = listOf("dts", "dca", "truehd", "mlp").filter { it !in output.passthrough }.joinToString(",")
                if (needsMixing.isNotEmpty())
                add(
                    CodecProfile(
                        type = CodecType.VIDEO_AUDIO,
                        codec = needsMixing,
                        conditions = listOf(ProfileCondition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.AUDIO_CHANNELS, "2", false)),
                        applyConditions = emptyList(),
                    ),
                )
            }
        }

        // On a surround output that can't take DTS / TrueHD as they are, the stick would decode them in software; keep that to 5.1
        // (7.1 is mixed to 5.1 by the server, which only touches the audio).
        val heavy = if (output.maxChannels > 2 && !forPreview) listOf("dts", "dca", "truehd", "mlp").filter { it !in output.passthrough }.joinToString(",") else ""
        val codecProfiles = if (heavy.isEmpty()) baseProfiles else baseProfiles + CodecProfile(
            type = CodecType.VIDEO_AUDIO, codec = heavy,
            conditions = listOf(ProfileCondition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.AUDIO_CHANNELS, minOf(output.maxChannels, 6).toString(), false)),
            applyConditions = emptyList(),
        )

        val textFormats = listOf("srt", "subrip", "ass", "ssa", "vtt", "webvtt", "ttml", "mov_text")
        val imageFormats = listOf("pgs", "pgssub", "dvdsub", "dvbsub", "vobsub")
        val subtitleProfiles = buildList {
            textFormats.forEach {
                add(SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EMBED))
                add(SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EXTERNAL))
            }
            imageFormats.forEach {
                add(SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EMBED))
                add(SubtitleProfile(format = it, method = SubtitleDeliveryMethod.ENCODE))
            }
        }
        val transcodeVideo = if (decoders.any { it.codec == "hevc" }) "hevc,h264" else "h264"
        return DeviceProfile(
            name = "Bloom for Fire TV",
            maxStreamingBitrate = maxBitrate,
            maxStaticBitrate = maxBitrate,
            musicStreamingTranscodingBitrate = 384_000,
            directPlayProfiles = listOf(
                DirectPlayProfile(container = containers.joinToString(","), videoCodec = video, audioCodec = audio, type = DlnaProfileType.VIDEO),
                DirectPlayProfile(container = "mp3,flac,m4a,aac,ogg,opus,wav,mka", audioCodec = audio, type = DlnaProfileType.AUDIO),
            ),
            transcodingProfiles = listOf(
                TranscodingProfile(
                    container = "ts",
                    type = DlnaProfileType.VIDEO,
                    videoCodec = transcodeVideo,
                    audioCodec = if (output.maxChannels <= 2) "aac" else "aac,ac3,eac3",
                    protocol = MediaStreamProtocol.HLS,
                    context = EncodingContext.STREAMING,
                    maxAudioChannels = if (output.maxChannels <= 2) "2" else "6",
                    minSegments = 1,
                    breakOnNonKeyFrames = true,
                    conditions = emptyList(),
                ),
                TranscodingProfile(
                    container = "mp3",
                    type = DlnaProfileType.AUDIO,
                    videoCodec = "",
                    audioCodec = "mp3",
                    protocol = MediaStreamProtocol.HTTP,
                    context = EncodingContext.STREAMING,
                    conditions = emptyList(),
                ),
            ),
            containerProfiles = emptyList(),
            codecProfiles = codecProfiles,
            subtitleProfiles = subtitleProfiles,
        )
    }
}

private fun subtitleMime(codec: String?): String = when (codec?.lowercase()) {
    "ass", "ssa" -> "text/x-ssa"
    "vtt", "webvtt" -> "text/vtt"
    "ttml" -> "application/ttml+xml"
    else -> "application/x-subrip"
}

/** Ask the server how to play [item] and build the URL for the chosen method. */
suspend fun Repo.preparePlayback(
    item: BaseItemDto,
    startTicks: Long,
    audioIndex: Int? = null,
    subtitleIndex: Int? = null,
    forceTranscode: Boolean = false,
    forPreview: Boolean = false,
    /** A quality cap for this stream only (bits per second); null uses the one from Settings. */
    maxBitrate: Int? = null,
    /** Languages to start in when nothing has been chosen yet. */
    languages: LangPrefs? = null,
): PlaybackSource = withContext(Dispatchers.IO) {
    val bitrate = maxBitrate ?: store.maxBitrate
    val response = api.mediaInfoApi.getPostedPlaybackInfo(
        itemId = item.id,
        data = PlaybackInfoDto(
            userId = userId,
            maxStreamingBitrate = bitrate,
            startTimeTicks = startTicks,
            audioStreamIndex = audioIndex,
            subtitleStreamIndex = subtitleIndex,
            deviceProfile = DeviceCapabilities.profile(BlushApplication.context, bitrate, forPreview),
            enableDirectPlay = !forceTranscode,
            enableDirectStream = !forceTranscode,
            enableTranscoding = true,
            allowVideoStreamCopy = true,
            allowAudioStreamCopy = true,
            autoOpenLiveStream = true,
        ),
    ).content

    val source = response.mediaSources.firstOrNull() ?: error("This item has no playable media.")
    val baseUrl = api.baseUrl.orEmpty().trimEnd('/')
    val token = api.accessToken.orEmpty()

    val (url, method) = when {
        !forceTranscode && (source.supportsDirectPlay || source.supportsDirectStream) -> {
            val streamUrl = api.videosApi.getVideoStreamUrl(
                itemId = item.id,
                static = true,
                mediaSourceId = source.id,
                playSessionId = response.playSessionId,
                tag = source.eTag,
            )
            val sep = if (streamUrl.contains('?')) '&' else '?'
            "$streamUrl${sep}api_key=$token" to (if (source.supportsDirectPlay) PlayMethod.DIRECT_PLAY else PlayMethod.DIRECT_STREAM)
        }
        source.transcodingUrl != null -> "$baseUrl${source.transcodingUrl}" to PlayMethod.TRANSCODE
        else -> error("The server can't play this item on this device.")
    }

    val streams = source.mediaStreams.orEmpty()

    // First time through (nothing chosen yet), start in the preferred languages: the series' own choice if it has
    // one, otherwise the Settings defaults. Asked for again with that choice, so a converted stream carries it too.
    if (audioIndex == null && subtitleIndex == null && !forPreview) {
        val wantLang = languages?.audio ?: "eng"
        val subMode = languages?.subtitles ?: "auto"
        fun plain(t: String?) = t?.contains("commentary", ignoreCase = true) != true
        val audioStreams = streams.filter { it.type == MediaStreamType.AUDIO }
        val matching = audioStreams.filter { Languages.same(it.language, wantLang) }
        val currentAudio = audioStreams.firstOrNull { it.index == source.defaultAudioStreamIndex }
        // The exact track chosen earlier in this series wins (same name / position, not just the first in that language).
        val picked = languages?.audioPick?.let { TrackPick.best(audioStreams, it) }?.index
        val wantAudio = picked ?: if (currentAudio != null && Languages.same(currentAudio.language, wantLang) && plain(currentAudio.title)) null
        else (matching.firstOrNull { plain(it.title) && plain(it.displayTitle) } ?: matching.firstOrNull())?.index

        val subStreams = streams.filter { it.type == MediaStreamType.SUBTITLE }
        val serverOn = source.defaultSubtitleStreamIndex?.let { it >= 0 } == true
        fun pickSub(code: String): Int? {
            val m = subStreams.filter { Languages.same(it.language, code) }
            return (m.firstOrNull { it.isForced != true && plain(it.title) } ?: m.firstOrNull { it.isForced != true } ?: m.firstOrNull())?.index
        }
        val currentSub = subStreams.firstOrNull { it.index == source.defaultSubtitleStreamIndex }
        val subPick = languages?.subtitlePick
        val pickedSub: Int? = when {
            subPick == null -> null
            subPick.off -> if (serverOn) -1 else null
            else -> TrackPick.best(subStreams, subPick)?.index
        }
        val wantSub: Int? = if (subPick != null && (subPick.off || pickedSub != null)) pickedSub else when (subMode) {
            "off" -> if (serverOn) -1 else null
            "auto" -> if (!serverOn || (currentSub != null && Languages.same(currentSub.language, "eng") && currentSub.isForced != true)) null else pickSub("eng")
            else -> pickSub(subMode)?.takeIf { it != source.defaultSubtitleStreamIndex }
        }
        val serverSub = source.defaultSubtitleStreamIndex ?: -1
        if ((wantAudio != null && wantAudio != source.defaultAudioStreamIndex) || (wantSub != null && wantSub != serverSub)) {
            return@withContext preparePlayback(
                item, startTicks,
                audioIndex = wantAudio ?: source.defaultAudioStreamIndex,
                subtitleIndex = wantSub ?: serverSub,
                forceTranscode = forceTranscode, forPreview = forPreview, maxBitrate = maxBitrate, languages = languages,
            )
        }
    }
    val external = streams
        .filter { it.type == MediaStreamType.SUBTITLE && it.deliveryMethod == SubtitleDeliveryMethod.EXTERNAL && it.deliveryUrl != null }
        .map {
            val path = it.deliveryUrl!!
            val full = if (path.startsWith("http")) path else "$baseUrl$path"
            val withKey = if (full.contains("api_key=")) full else full + (if (full.contains('?')) "&" else "?") + "api_key=$token"
            ExternalSubtitle(it.index, withKey, subtitleMime(it.codec), it.language, it.displayTitle ?: it.language ?: "Subtitles")
        }

    PlaybackSource(
        item = item,
        url = url,
        method = method,
        mediaSource = source,
        playSessionId = response.playSessionId,
        externalSubtitles = external,
        audioStreams = streams.filter { it.type == MediaStreamType.AUDIO },
        subtitleStreams = streams.filter { it.type == MediaStreamType.SUBTITLE },
        audioIndex = audioIndex ?: source.defaultAudioStreamIndex,
        subtitleIndex = subtitleIndex ?: source.defaultSubtitleStreamIndex,
    )
}

suspend fun Repo.reportStart(source: PlaybackSource, positionTicks: Long) = withContext(Dispatchers.IO) {
    logged("Playback.reportStart") {
        api.playStateApi.reportPlaybackStart(
            PlaybackStartInfo(
                canSeek = true,
                itemId = source.item.id,
                mediaSourceId = source.mediaSource.id,
                audioStreamIndex = source.audioIndex,
                subtitleStreamIndex = source.subtitleIndex,
                isPaused = false,
                isMuted = false,
                positionTicks = positionTicks,
                playMethod = source.method,
                playSessionId = source.playSessionId,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }.onFailure { android.util.Log.w("BloomReport", "playback report failed", it) }
}

suspend fun Repo.reportProgress(source: PlaybackSource, positionTicks: Long, paused: Boolean, audioIndex: Int?, subtitleIndex: Int?) = withContext(Dispatchers.IO) {
    logged("Playback.reportProgress") {
        api.playStateApi.reportPlaybackProgress(
            PlaybackProgressInfo(
                canSeek = true,
                itemId = source.item.id,
                mediaSourceId = source.mediaSource.id,
                audioStreamIndex = audioIndex,
                subtitleStreamIndex = subtitleIndex,
                isPaused = paused,
                isMuted = false,
                positionTicks = positionTicks,
                playMethod = source.method,
                playSessionId = source.playSessionId,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }.onFailure { android.util.Log.w("BloomReport", "playback report failed", it) }
}

suspend fun Repo.reportStopped(source: PlaybackSource, positionTicks: Long) = withContext(Dispatchers.IO) {
    logged("Playback.reportStopped") {
        api.playStateApi.reportPlaybackStopped(
            PlaybackStopInfo(
                itemId = source.item.id,
                mediaSourceId = source.mediaSource.id,
                positionTicks = positionTicks,
                playSessionId = source.playSessionId,
                failed = false,
            ),
        )
    }.onFailure { android.util.Log.w("BloomReport", "stop report failed", it) }
    // Afterwards, in one direct save: when it was watched, and (if needed) the place.
    // - "Last watched" is set to now, so Continue watching puts it first. The server normally does this from the reports above,
    //   but if a report goes missing the title kept an old (or no) date and sank to the end of the row.
    // - The server forgets a place that is under its "minimum resume" share of the film (5% by default, so a few minutes of a
    //   long film vanish from Continue watching). Anything past 15 seconds and not near the end is put back.
    val runtime = source.mediaSource.runTimeTicks ?: source.item.runTimeTicks ?: 0L
    if (positionTicks >= 150_000_000L) {
        val keepPlace = runtime <= 0L || positionTicks < runtime * 0.9
        logged("Playback.reportStopped") {
            api.itemsApi.updateItemUserData(
                itemId = source.item.id,
                userId = userId,
                data = org.jellyfin.sdk.model.api.UpdateUserItemDataDto(
                    playbackPositionTicks = if (keepPlace) positionTicks else null,
                    lastPlayedDate = java.time.LocalDateTime.now(),
                ),
            )
        }.onFailure { android.util.Log.w("BloomReport", "saving watched date failed", it) }
    }
}

/** Tells the server to stop converting a stream we no longer need (used for previews, which don't save progress). */
suspend fun Repo.stopConversion(source: PlaybackSource) = withContext(Dispatchers.IO) {
    val session = source.playSessionId ?: return@withContext
    if (source.method != PlayMethod.TRANSCODE) return@withContext
    logged("Playback.stopConversion") { api.hlsSegmentApi.stopEncodingProcess(deviceId = api.deviceInfo.id, playSessionId = session) }
}

/**
 * A small copy of [item] for a preview, made by the server as it is fetched and starting at [startMs]: 480p, about 1.5 Mbit/s,
 * stereo. The server does the jump to the start point itself, so it does not depend on the file being a kind that can be
 * jumped into (some cannot, and would begin at the very start), and the stick only ever fetches and decodes a small stream
 * however large the original is. [sessionId] names it, so it can be told to stop ([stopPreviewClip]).
 */
fun Repo.previewClipUrl(item: BaseItemDto, startMs: Long, audioIndex: Int?, sessionId: String): String {
    val base = api.baseUrl.orEmpty().trimEnd('/')
    val query = buildList {
        add("Static" to "false")
        add("StartTimeTicks" to (startMs * 10_000L).toString())
        add("VideoCodec" to "h264")
        add("AudioCodec" to "aac")
        add("MaxWidth" to "854")
        add("MaxHeight" to "480")
        add("VideoBitRate" to "1500000")
        add("AudioBitRate" to "96000")
        add("AudioChannels" to "2")
        add("MaxAudioChannels" to "2")
        add("TranscodingMaxAudioChannels" to "2")
        if (audioIndex != null) add("AudioStreamIndex" to audioIndex.toString())
        add("EnableAutoStreamCopy" to "false")
        add("AllowVideoStreamCopy" to "false")
        add("AllowAudioStreamCopy" to "false")
        add("DeviceId" to api.deviceInfo.id)
        add("PlaySessionId" to sessionId)
        add("api_key" to api.accessToken.orEmpty())
    }.joinToString("&") { (k, v) -> "$k=${android.net.Uri.encode(v)}" }
    return "$base/Videos/${item.id}/stream.ts?$query"
}

/** Tells the server to stop making a preview clip (it also stops when the stick closes the connection). */
suspend fun Repo.stopPreviewClip(sessionId: String) = withContext(Dispatchers.IO) {
    logged("Playback.stopPreviewClip") { api.hlsSegmentApi.stopEncodingProcess(deviceId = api.deviceInfo.id, playSessionId = sessionId) }
}
