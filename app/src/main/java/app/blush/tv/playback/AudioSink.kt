package app.blush.tv.playback

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/**
 * Normally surround formats (Dolby Digital and so on) are passed straight to the TV or soundbar. With
 * [decodeHere] on, the sound output says it only takes plain decoded sound, so the player decodes it itself.
 * Passed-through sound can't be sped up, so this is switched on while the speed isn't normal.
 */
class AudioMode {
    @Volatile
    var decodeHere = false

    /**
     * The volume, applied to the sound itself (see [GainProcessor]) rather than asked of the device's audio output. Some
     * outputs ignore the usual volume setting for certain sound (5.1 sent straight to the TV, for one), and this cannot be ignored.
     */
    @Volatile
    var gain = 1f

    /** True while the sound being played is in a form [gain] can be applied to (then the output's own volume is left alone). */
    var gainActive by androidx.compose.runtime.mutableStateOf(false)
}

/** Scales the decoded sound by [AudioMode.gain] (16-bit or float; anything else is left alone and [AudioMode.gainActive] is false). */
@UnstableApi
private class GainProcessor(private val mode: AudioMode) : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val ok = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        mode.gainActive = ok
        return if (ok) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    /** Leaves quiet sound alone and eases peaks above 0.7 towards 1.0, so boosting past 100% does not clip harshly. */
    private fun limit(x: Float): Float {
        val a = kotlin.math.abs(x)
        if (a <= 0.7f) return x
        val u = (a - 0.7f) / 0.3f
        val y = 0.7f + 0.3f * (u / (1f + u))
        return if (x < 0f) -y else y
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val g = mode.gain
        // Turning down is a plain multiplication; only boosting (past 100%) can push peaks over the top, so only then are they eased.
        val boost = g > 1f
        if (g == 1f) {
            out.put(inputBuffer)
        } else if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            while (inputBuffer.remaining() >= 2) {
                val x = inputBuffer.short / 32768f * g
                out.putShort(((if (boost) limit(x) else x) * 32767f).toInt().toShort())
            }
            out.put(inputBuffer)
        } else {
            while (inputBuffer.remaining() >= 4) {
                val x = inputBuffer.float * g
                out.putFloat(if (boost) limit(x) else x)
            }
            out.put(inputBuffer)
        }
        out.flip()
    }
}

@UnstableApi
private class SwitchableAudioSink(sink: AudioSink, private val mode: AudioMode) : ForwardingAudioSink(sink) {
    private fun refused(format: Format) = mode.decodeHere && format.sampleMimeType != MimeTypes.AUDIO_RAW

    override fun supportsFormat(format: Format): Boolean = if (refused(format)) false else super.supportsFormat(format)

    override fun getFormatSupport(format: Format): Int =
        if (refused(format)) AudioSink.SINK_FORMAT_UNSUPPORTED else super.getFormatSupport(format)
}

/** The usual renderers, with the sound output following [mode]. */
@UnstableApi
class SpeedFriendlyRenderersFactory(context: Context, private val mode: AudioMode, private val softwareGain: Boolean = false) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
        SwitchableAudioSink(
            DefaultAudioSink.Builder(context)
                .apply { if (softwareGain) setAudioProcessors(arrayOf<AudioProcessor>(GainProcessor(mode))) }
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build(),
            mode,
        )
}
