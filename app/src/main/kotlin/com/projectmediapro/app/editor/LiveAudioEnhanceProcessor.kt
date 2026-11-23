package com.projectmediapro.app.editor

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.projectmediapro.app.data.export.AudioDspChain
import com.projectmediapro.core.model.AudioEnhanceConfig
import com.projectmediapro.core.model.AudioEqPreset
import com.projectmediapro.core.model.Clip
import java.nio.ByteBuffer

/**
 * Immutable snapshot of live preview audio DSP parameters updated from the UI thread
 * and consumed lock-free on ExoPlayer's audio rendering thread.
 */
data class LiveAudioConfig(
    val gainBoost: Float = Clip.DEFAULT_AUDIO_BOOST,
    val eqPreset: AudioEqPreset = AudioEqPreset.NONE,
    val noiseGateEnabled: Boolean = false,
) {
    val isPassThrough: Boolean
        get() = gainBoost <= 1.001f && eqPreset == AudioEqPreset.NONE && !noiseGateEnabled

    companion object {
        fun from(audioBoost: Float, enhance: AudioEnhanceConfig?): LiveAudioConfig = LiveAudioConfig(
            gainBoost = if (audioBoost.isFinite()) {
                audioBoost.coerceIn(Clip.DEFAULT_AUDIO_BOOST, Clip.MAX_AUDIO_BOOST)
            } else {
                Clip.DEFAULT_AUDIO_BOOST
            },
            eqPreset = enhance?.eqPreset ?: AudioEqPreset.NONE,
            noiseGateEnabled = enhance?.noiseGateEnabled == true,
        )
    }
}

/**
 * Real-time Media3 [BaseAudioProcessor] for ExoPlayer live preview backed by the exact same
 * [AudioDspChain] used during export. Remains configured on PCM-16 streams as a zero-overhead
 * copy when flat so toggling EQ, Noise Gate, or Boost applies immediately without re-preparing.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LiveAudioEnhanceProcessor : BaseAudioProcessor() {

    @Volatile
    private var targetConfig: LiveAudioConfig = LiveAudioConfig()

    private var dspChain: AudioDspChain? = null

    fun update(audioBoost: Float, enhance: AudioEnhanceConfig?) {
        val safeBoost = if (audioBoost.isFinite()) {
            audioBoost.coerceIn(Clip.DEFAULT_AUDIO_BOOST, Clip.MAX_AUDIO_BOOST)
        } else {
            Clip.DEFAULT_AUDIO_BOOST
        }
        val preset = enhance?.eqPreset ?: AudioEqPreset.NONE
        val gateOn = enhance?.noiseGateEnabled == true
        val current = targetConfig
        if (current.gainBoost == safeBoost &&
            current.eqPreset == preset &&
            current.noiseGateEnabled == gateOn
        ) {
            return
        }
        targetConfig = LiveAudioConfig(
            gainBoost = safeBoost,
            eqPreset = preset,
            noiseGateEnabled = gateOn,
        )
    }

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.sampleRate <= 0 ||
            inputAudioFormat.channelCount <= 0
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        dspChain = null
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        val config = targetConfig
        if (config.isPassThrough) {
            dspChain = null
            val outBuffer = replaceOutputBuffer(remaining)
            outBuffer.put(inputBuffer)
            outBuffer.flip()
            return
        }

        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(1)
        val bytesPerFrame = channels * 2
        val frameCount = remaining / bytesPerFrame
        if (frameCount <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val existing = dspChain
        val chain = if (existing != null && existing.matches(sampleRate, channels, config.eqPreset, config.noiseGateEnabled)) {
            existing
        } else {
            AudioDspChain(
                sampleRate = sampleRate,
                channelCount = channels,
                eqPreset = config.eqPreset,
                noiseGateEnabled = config.noiseGateEnabled,
            ).also { dspChain = it }
        }

        val outBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)
        val safeBoost = config.gainBoost.toDouble()
        for (f in 0 until frameCount) {
            chain.processFrame(inputBuffer, outBuffer, safeBoost)
        }

        if (inputBuffer.hasRemaining()) {
            inputBuffer.position(inputBuffer.limit())
        }
        outBuffer.flip()
    }

    override fun onFlush() {
        dspChain?.reset()
    }

    override fun onReset() {
        dspChain = null
    }
}

/**
 * Media3 1.4.1 removed the `enableOffload` parameter from [DefaultRenderersFactory.buildAudioSink];
 * this shared 3-parameter override wires [processor] into ExoPlayer's [DefaultAudioSink].
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun buildLiveAudioRenderersFactory(
    context: Context,
    processor: LiveAudioEnhanceProcessor,
): DefaultRenderersFactory = object : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioProcessors(arrayOf(processor))
        .build()
}
