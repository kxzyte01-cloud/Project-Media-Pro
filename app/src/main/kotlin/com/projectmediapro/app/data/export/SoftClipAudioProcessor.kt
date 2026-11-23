package com.projectmediapro.app.data.export

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.projectmediapro.core.model.AudioEnhanceConfig
import com.projectmediapro.core.model.AudioDuckingEnvelope
import com.projectmediapro.core.model.AudioEqPreset
import com.projectmediapro.core.model.SpeechWindowMs
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.tanh

/**
 * Pure-JVM soft-knee anti-clipping limiter and fade envelope math shared with unit tests.
 */
object SoftClipMath {
    private const val KNEE_START = 0.85
    private const val KNEE_HEADROOM = 0.15

    fun fadeMultiplier(
        timeMs: Long,
        clipDurationMs: Long,
        fadeInMs: Long,
        fadeOutMs: Long,
    ): Double {
        var mult = 1.0
        if (fadeInMs > 0L && timeMs < fadeInMs) {
            mult = minOf(mult, (timeMs.coerceAtLeast(0L).toDouble() / fadeInMs.toDouble()).coerceIn(0.0, 1.0))
        }
        if (fadeOutMs > 0L && clipDurationMs > 0L) {
            val fadeOutStart = (clipDurationMs - fadeOutMs).coerceAtLeast(0L)
            if (timeMs >= fadeOutStart) {
                val remaining = (clipDurationMs - timeMs).coerceAtLeast(0L)
                mult = minOf(mult, (remaining.toDouble() / fadeOutMs.toDouble()).coerceIn(0.0, 1.0))
            }
        }
        return mult
    }

    fun softLimitNormalized(sample: Double): Double {
        if (!sample.isFinite()) return 0.0
        val magnitude = abs(sample)
        if (magnitude <= KNEE_START) return sample
        val excess = (magnitude - KNEE_START) / KNEE_HEADROOM
        val limited = KNEE_START + KNEE_HEADROOM * tanh(excess)
        return sign(sample) * limited.coerceAtMost(1.0)
    }

    fun processPcm16Sample(
        sample: Short,
        gain: Double,
        fadeMultiplier: Double,
    ): Short {
        val normalized = (sample.toDouble() / 32768.0) * gain * fadeMultiplier
        val limited = softLimitNormalized(normalized)
        return (limited * 32767.0).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}

/**
 * Media3 [BaseAudioProcessor] for export applying [AudioDspChain] (Noise Gate, RBJ Biquad EQ,
 * soft-knee anti-clipping limiter) plus smooth linear Fade In / Fade Out envelopes.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SoftClipAudioProcessor(
    private val gain: Float,
    private val fadeInMs: Long = 0L,
    private val fadeOutMs: Long = 0L,
    private val clipDurationMs: Long = 0L,
    private val audioEnhance: AudioEnhanceConfig? = null,
    private val duckingWindowsMs: List<SpeechWindowMs> = emptyList(),
    private val timelineOffsetMs: Long = 0L,
) : BaseAudioProcessor() {

    private var framesProcessed: Long = 0L
    private var dspChain: AudioDspChain? = null

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.sampleRate <= 0 ||
            inputAudioFormat.channelCount <= 0
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        val safeGain = gain.coerceIn(0f, 2.0f)
        val hasFades = fadeInMs > 0L || fadeOutMs > 0L
        val enhance = audioEnhance?.takeUnless { it.isIdentity }
        val hasEqOrGate = enhance != null && (enhance.eqPreset != AudioEqPreset.NONE || enhance.noiseGateEnabled)
        val hasActiveDucking = enhance?.autoDuckMusicEnabled == true && duckingWindowsMs.isNotEmpty()
        if (abs(safeGain - 1.0f) < 0.001f && !hasFades && !hasEqOrGate && !hasActiveDucking) {
            return AudioProcessor.AudioFormat.NOT_SET
        }

        dspChain = AudioDspChain(
            sampleRate = inputAudioFormat.sampleRate,
            channelCount = inputAudioFormat.channelCount,
            eqPreset = enhance?.eqPreset ?: AudioEqPreset.NONE,
            noiseGateEnabled = enhance?.noiseGateEnabled == true,
        )
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(1)
        val bytesPerFrame = channels * 2
        val frameCount = remaining / bytesPerFrame
        if (frameCount <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val chain = dspChain ?: AudioDspChain(
            sampleRate = sampleRate,
            channelCount = channels,
            eqPreset = audioEnhance?.eqPreset ?: AudioEqPreset.NONE,
            noiseGateEnabled = audioEnhance?.noiseGateEnabled == true,
        ).also { dspChain = it }

        val outBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)
        val safeGain = gain.coerceIn(0f, 2.0f).toDouble()
        val applyDucking = audioEnhance?.autoDuckMusicEnabled == true && duckingWindowsMs.isNotEmpty()

        for (f in 0 until frameCount) {
            val timeMs = (framesProcessed * 1_000L) / sampleRate.toLong()
            val fade = SoftClipMath.fadeMultiplier(
                timeMs = timeMs,
                clipDurationMs = clipDurationMs,
                fadeInMs = fadeInMs,
                fadeOutMs = fadeOutMs,
            )
            val duck = if (applyDucking) {
                AudioDuckingEnvelope.gainAt(timelineOffsetMs + timeMs, duckingWindowsMs)
            } else {
                1.0
            }
            chain.processFrame(inputBuffer, outBuffer, safeGain * fade * duck)
            framesProcessed++
        }

        if (inputBuffer.hasRemaining()) {
            inputBuffer.position(inputBuffer.limit())
        }
        outBuffer.flip()
    }

    override fun onFlush() {
        framesProcessed = 0L
        dspChain?.reset()
    }

    override fun onReset() {
        framesProcessed = 0L
        dspChain = null
    }
}
