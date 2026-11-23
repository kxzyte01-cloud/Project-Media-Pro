package com.projectmediapro.app.data.export

import com.projectmediapro.core.model.AudioBiquadCoefficients
import com.projectmediapro.core.model.AudioBiquadFilter
import com.projectmediapro.core.model.AudioEqPreset
import com.projectmediapro.core.model.AudioNoiseGateEnvelope
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shared PCM-16 multi-channel DSP chain (linked-channel Noise Gate -> RBJ Biquad EQ ->
 * Soft-Knee Anti-Clipping Limiter) consumed 1:1 by both Media3 export
 * ([SoftClipAudioProcessor]) and ExoPlayer live preview ([com.projectmediapro.app.editor.LiveAudioEnhanceProcessor]).
 */
internal class AudioDspChain(
    val sampleRate: Int,
    val channelCount: Int,
    val eqPreset: AudioEqPreset,
    val noiseGateEnabled: Boolean,
) {
    private val safeChannels: Int = channelCount.coerceAtLeast(1)
    private val channelFilters: Array<Array<AudioBiquadFilter>> = run {
        val coeffs = AudioBiquadCoefficients.forPreset(eqPreset, sampleRate)
        if (coeffs.isNotEmpty()) {
            Array(safeChannels) { Array(coeffs.size) { idx -> AudioBiquadFilter(coeffs[idx]) } }
        } else {
            emptyArray()
        }
    }
    private val noiseGate: AudioNoiseGateEnvelope? = if (noiseGateEnabled) {
        AudioNoiseGateEnvelope(sampleRate)
    } else {
        null
    }
    private val frameScratch = DoubleArray(safeChannels)

    fun matches(
        sampleRate: Int,
        channelCount: Int,
        eqPreset: AudioEqPreset,
        noiseGateEnabled: Boolean,
    ): Boolean = this.sampleRate == sampleRate &&
        this.channelCount == channelCount &&
        this.eqPreset == eqPreset &&
        this.noiseGateEnabled == noiseGateEnabled

    fun processFrame(
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
        gainMultiplier: Double,
    ) {
        var framePeak = 0.0
        for (ch in 0 until safeChannels) {
            val norm = inputBuffer.short.toDouble() / 32768.0
            frameScratch[ch] = norm
            val mag = abs(norm)
            if (mag > framePeak) framePeak = mag
        }

        val gateGain = noiseGate?.nextGain(framePeak) ?: 1.0
        val combinedGain = gainMultiplier * gateGain

        for (ch in 0 until safeChannels) {
            var sample = frameScratch[ch]
            if (ch < channelFilters.size) {
                val chain = channelFilters[ch]
                for (i in chain.indices) {
                    sample = chain[i].process(sample)
                }
            }
            val limited = SoftClipMath.softLimitNormalized(sample * combinedGain)
            val pcm16 = (limited * 32767.0)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            outputBuffer.putShort(pcm16)
        }
    }

    fun reset() {
        for (chain in channelFilters) {
            for (filter in chain) {
                filter.reset()
            }
        }
        noiseGate?.reset()
    }
}
