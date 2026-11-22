package com.projectmediapro.app.editor

import com.projectmediapro.feature.captions.PcmAudioData
import kotlin.math.sqrt

/**
 * Pure JVM offline onset/beat peak detector operating on 16 kHz mono PCM samples.
 * Detects rhythmic percussion / bass hits using local adaptive RMS energy flux without any
 * network access or native ML dependencies.
 */
object OfflineBeatDetector {

    private const val HOP_DURATION_MS = 20L
    private const val LOCAL_WINDOW_HOPS = 20 // 400 ms moving average window
    private const val MIN_BEAT_SPACING_MS = 280L // ~214 BPM ceiling
    private const val ENERGY_THRESHOLD_RATIO = 1.42
    private const val MIN_ABSOLUTE_RMS = 0.025
    private const val MAX_BEATS = 240

    fun detectBeatsMs(pcm: PcmAudioData): List<Long> {
        if (!pcm.hasAudio || pcm.sampleRateHz <= 0) return emptyList()
        val samples = pcm.samples
        val hopSamples = ((pcm.sampleRateHz * HOP_DURATION_MS) / 1_000L).toInt().coerceAtLeast(1)
        val frameCount = samples.size / hopSamples
        if (frameCount < 3) return emptyList()

        val energies = DoubleArray(frameCount)
        for (frame in 0 until frameCount) {
            val start = frame * hopSamples
            val end = minOf(start + hopSamples, samples.size)
            var sumSq = 0.0
            for (i in start until end) {
                val norm = samples[i].toDouble() / 32768.0
                sumSq += norm * norm
            }
            energies[frame] = sqrt(sumSq / (end - start).coerceAtLeast(1))
        }

        // Compute positive onset flux (rising energy edges)
        val flux = DoubleArray(frameCount)
        for (i in 1 until frameCount) {
            flux[i] = (energies[i] - energies[i - 1]).coerceAtLeast(0.0)
        }

        val beats = mutableListOf<Long>()
        var lastBeatMs = -MIN_BEAT_SPACING_MS
        val halfWin = LOCAL_WINDOW_HOPS / 2

        for (i in 1 until frameCount - 1) {
            val currentFlux = flux[i]
            if (currentFlux <= 0.0 || energies[i] < MIN_ABSOLUTE_RMS) continue
            // Local peak check
            if (currentFlux < flux[i - 1] || currentFlux < flux[i + 1]) continue

            val winStart = (i - halfWin).coerceAtLeast(0)
            val winEnd = (i + halfWin).coerceAtMost(frameCount - 1)
            var avgEnergy = 0.0
            for (w in winStart..winEnd) {
                avgEnergy += energies[w]
            }
            avgEnergy /= (winEnd - winStart + 1)

            if (energies[i] >= avgEnergy * ENERGY_THRESHOLD_RATIO) {
                val timeMs = i * HOP_DURATION_MS
                if (timeMs - lastBeatMs >= MIN_BEAT_SPACING_MS) {
                    beats.add(timeMs)
                    lastBeatMs = timeMs
                    if (beats.size >= MAX_BEATS) break
                }
            }
        }
        return beats
    }
}
