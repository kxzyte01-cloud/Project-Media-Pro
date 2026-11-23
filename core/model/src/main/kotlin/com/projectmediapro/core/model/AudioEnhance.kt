package com.projectmediapro.core.model

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pro offline audio equalization presets executed via stable RBJ biquad IIR
 * filter chains in Media3 audio export.
 */
@Serializable
enum class AudioEqPreset {
    /** Flat response with zero DSP overhead. */
    NONE,

    /** 80 Hz rumble cut + 3.2 kHz vocal presence lift for talking-head & voiceover clarity. */
    VOCAL_CLARITY,

    /** 110 Hz low-shelf punch with 35 Hz sub-rumble roll-off for music & beat edits. */
    BASS_BOOST,

    /** Gentle 180 Hz warmth + 8 kHz airy shelf for podcast & cinematic polish. */
    STUDIO_WARMTH,

    /** Vintage band-pass (350 Hz – 3.2 kHz) lo-fi radio character. */
    LOFI_RADIO,
}

/**
 * Per-clip audio enhancement settings stored as a nullable field on [Clip] so
 * existing `schemaVersion = 1` projects deserialize seamlessly.
 */
@Serializable
data class AudioEnhanceConfig(
    val eqPreset: AudioEqPreset = AudioEqPreset.NONE,
    val noiseGateEnabled: Boolean = false,
    val autoDuckMusicEnabled: Boolean = false,
) {
    val isIdentity: Boolean
        get() = eqPreset == AudioEqPreset.NONE && !noiseGateEnabled && !autoDuckMusicEnabled
}

/**
 * One spoken/caption window on the master timeline used by [AudioDuckingEnvelope]
 * to duck background music smoothly underneath speech.
 */
data class SpeechWindowMs(
    val startMs: Long,
    val endMs: Long,
)

/**
 * Smooth cosine-eased studio music ducking envelope shared 1:1 between live preview
 * and Media3 audio export.
 */
object AudioDuckingEnvelope {
    const val DUCKED_GAIN: Double = 0.28 // ~ -11 dB
    const val ATTACK_MS: Long = 140L
    const val RELEASE_MS: Long = 280L
    const val MERGE_GAP_MS: Long = 300L

    fun mergeWindows(windows: List<SpeechWindowMs>): List<SpeechWindowMs> {
        if (windows.isEmpty()) return emptyList()
        val sorted = windows
            .filter { it.endMs > it.startMs }
            .sortedBy { it.startMs }
        if (sorted.isEmpty()) return emptyList()

        val merged = mutableListOf<SpeechWindowMs>()
        var currentStart = sorted[0].startMs.coerceAtLeast(0L)
        var currentEnd = sorted[0].endMs.coerceAtLeast(currentStart)
        for (i in 1 until sorted.size) {
            val nextStart = sorted[i].startMs.coerceAtLeast(0L)
            val nextEnd = sorted[i].endMs.coerceAtLeast(nextStart)
            if (nextStart <= currentEnd + MERGE_GAP_MS) {
                if (nextEnd > currentEnd) currentEnd = nextEnd
            } else {
                merged.add(SpeechWindowMs(currentStart, currentEnd))
                currentStart = nextStart
                currentEnd = nextEnd
            }
        }
        merged.add(SpeechWindowMs(currentStart, currentEnd))
        return merged
    }

    fun gainAt(timelineMs: Long, mergedWindows: List<SpeechWindowMs>): Double {
        if (mergedWindows.isEmpty() || timelineMs < 0L) return 1.0
        var minGain = 1.0
        for (w in mergedWindows) {
            val attackStart = (w.startMs - ATTACK_MS).coerceAtLeast(0L)
            val releaseEnd = if (w.endMs > Long.MAX_VALUE - RELEASE_MS) Long.MAX_VALUE else w.endMs + RELEASE_MS
            if (timelineMs < attackStart) break
            if (timelineMs > releaseEnd) continue

            val g = when {
                timelineMs in w.startMs..w.endMs -> DUCKED_GAIN
                timelineMs < w.startMs -> {
                    val span = (w.startMs - attackStart).coerceAtLeast(1L)
                    val t = ((timelineMs - attackStart).toDouble() / span.toDouble()).coerceIn(0.0, 1.0)
                    val eased = 0.5 - 0.5 * cos(PI * t)
                    1.0 - (1.0 - DUCKED_GAIN) * eased
                }
                else -> {
                    val t = ((timelineMs - w.endMs).toDouble() / RELEASE_MS.toDouble()).coerceIn(0.0, 1.0)
                    val eased = 0.5 - 0.5 * cos(PI * t)
                    DUCKED_GAIN + (1.0 - DUCKED_GAIN) * eased
                }
            }
            if (g < minGain) minGain = g
        }
        return minGain.coerceIn(DUCKED_GAIN, 1.0)
    }
}

/**
 * Normalized Direct Form II Transposed biquad filter coefficients (`a0 = 1`).
 */
data class AudioBiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
) {
    companion object {
        private const val BUTTERWORTH_Q = 0.7071067811865476

        fun highPass(sampleRate: Int, cutoffHz: Double, q: Double = BUTTERWORTH_Q): AudioBiquadCoefficients {
            val sr = sampleRate.coerceAtLeast(8_000).toDouble()
            val fc = cutoffHz.coerceIn(20.0, sr * 0.45)
            val w0 = 2.0 * PI * fc / sr
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.1))
            val a0 = 1.0 + alpha
            return AudioBiquadCoefficients(
                b0 = ((1.0 + cosW0) * 0.5) / a0,
                b1 = (-(1.0 + cosW0)) / a0,
                b2 = ((1.0 + cosW0) * 0.5) / a0,
                a1 = (-2.0 * cosW0) / a0,
                a2 = (1.0 - alpha) / a0,
            )
        }

        fun lowPass(sampleRate: Int, cutoffHz: Double, q: Double = BUTTERWORTH_Q): AudioBiquadCoefficients {
            val sr = sampleRate.coerceAtLeast(8_000).toDouble()
            val fc = cutoffHz.coerceIn(20.0, sr * 0.45)
            val w0 = 2.0 * PI * fc / sr
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.1))
            val a0 = 1.0 + alpha
            return AudioBiquadCoefficients(
                b0 = ((1.0 - cosW0) * 0.5) / a0,
                b1 = (1.0 - cosW0) / a0,
                b2 = ((1.0 - cosW0) * 0.5) / a0,
                a1 = (-2.0 * cosW0) / a0,
                a2 = (1.0 - alpha) / a0,
            )
        }

        fun peaking(sampleRate: Int, centerHz: Double, gainDb: Double, q: Double = 1.0): AudioBiquadCoefficients {
            val sr = sampleRate.coerceAtLeast(8_000).toDouble()
            val fc = centerHz.coerceIn(20.0, sr * 0.45)
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * fc / sr
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.1))
            val a0 = 1.0 + alpha / a
            return AudioBiquadCoefficients(
                b0 = (1.0 + alpha * a) / a0,
                b1 = (-2.0 * cosW0) / a0,
                b2 = (1.0 - alpha * a) / a0,
                a1 = (-2.0 * cosW0) / a0,
                a2 = (1.0 - alpha / a) / a0,
            )
        }

        fun lowShelf(sampleRate: Int, freqHz: Double, gainDb: Double, q: Double = BUTTERWORTH_Q): AudioBiquadCoefficients {
            val sr = sampleRate.coerceAtLeast(8_000).toDouble()
            val fc = freqHz.coerceIn(20.0, sr * 0.45)
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * fc / sr
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.1))
            val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
            val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
            return AudioBiquadCoefficients(
                b0 = (a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)) / a0,
                b1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)) / a0,
                b2 = (a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)) / a0,
                a1 = (-2.0 * ((a - 1.0) + (a + 1.0) * cosW0)) / a0,
                a2 = ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0,
            )
        }

        fun highShelf(sampleRate: Int, freqHz: Double, gainDb: Double, q: Double = BUTTERWORTH_Q): AudioBiquadCoefficients {
            val sr = sampleRate.coerceAtLeast(8_000).toDouble()
            val fc = freqHz.coerceIn(20.0, sr * 0.45)
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * fc / sr
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.1))
            val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
            val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
            return AudioBiquadCoefficients(
                b0 = (a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)) / a0,
                b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)) / a0,
                b2 = (a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)) / a0,
                a1 = (2.0 * ((a - 1.0) - (a + 1.0) * cosW0)) / a0,
                a2 = ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0,
            )
        }

        fun forPreset(preset: AudioEqPreset, sampleRate: Int): List<AudioBiquadCoefficients> = when (preset) {
            AudioEqPreset.NONE -> emptyList()
            AudioEqPreset.VOCAL_CLARITY -> listOf(
                highPass(sampleRate, cutoffHz = 85.0),
                peaking(sampleRate, centerHz = 3_200.0, gainDb = 4.5, q = 1.1),
            )
            AudioEqPreset.BASS_BOOST -> listOf(
                highPass(sampleRate, cutoffHz = 35.0),
                lowShelf(sampleRate, freqHz = 110.0, gainDb = 5.5),
            )
            AudioEqPreset.STUDIO_WARMTH -> listOf(
                lowShelf(sampleRate, freqHz = 180.0, gainDb = 2.5),
                highShelf(sampleRate, freqHz = 8_000.0, gainDb = 2.0),
            )
            AudioEqPreset.LOFI_RADIO -> listOf(
                highPass(sampleRate, cutoffHz = 350.0, q = 0.8),
                lowPass(sampleRate, cutoffHz = 3_200.0, q = 0.8),
            )
        }
    }
}

/**
 * Stateful single-channel biquad filter (Direct Form II Transposed).
 */
class AudioBiquadFilter(
    private val coeffs: AudioBiquadCoefficients,
) {
    private var z1: Double = 0.0
    private var z2: Double = 0.0

    fun process(input: Double): Double {
        val out = coeffs.b0 * input + z1
        z1 = coeffs.b1 * input - coeffs.a1 * out + z2
        z2 = coeffs.b2 * input - coeffs.a2 * out
        return if (out.isFinite()) out else 0.0
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }
}

/**
 * Smooth linked-channel studio noise gate that gently attenuates quiet room hum
 * (-22 dB floor) below -34 dBFS without harsh choppy cutoffs.
 */
class AudioNoiseGateEnvelope(
    sampleRate: Int,
    private val threshold: Double = DEFAULT_THRESHOLD,
    private val floorGain: Double = DEFAULT_FLOOR_GAIN,
) {
    private val sr = sampleRate.coerceAtLeast(8_000).toDouble()
    private val envAttack = exp(-1.0 / (0.004 * sr))
    private val envRelease = exp(-1.0 / (0.060 * sr))
    private val gainAttack = exp(-1.0 / (0.005 * sr))
    private val gainRelease = exp(-1.0 / (0.085 * sr))

    private var envelope: Double = 0.0
    private var currentGain: Double = floorGain

    fun nextGain(framePeakNormalized: Double): Double {
        val peak = abs(framePeakNormalized)
        val envCoeff = if (peak > envelope) envAttack else envRelease
        envelope = envCoeff * envelope + (1.0 - envCoeff) * peak

        val targetGain = if (envelope >= threshold) 1.0 else floorGain
        val gainCoeff = if (targetGain > currentGain) gainAttack else gainRelease
        currentGain = gainCoeff * currentGain + (1.0 - gainCoeff) * targetGain
        return currentGain.coerceIn(floorGain, 1.0)
    }

    fun reset() {
        envelope = 0.0
        currentGain = floorGain
    }

    companion object {
        const val DEFAULT_THRESHOLD: Double = 0.020 // ~ -34 dBFS
        const val DEFAULT_FLOOR_GAIN: Double = 0.08 // ~ -22 dB
    }
}
