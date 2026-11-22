package com.projectmediapro.core.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

/**
 * VITA/CapCut-style 1-tap velocity speed ramp presets.
 */
@Serializable
enum class SpeedRampPreset {
    NONE,
    MONTAGE,
    HERO,
    BULLET,
    FLASH_IN,
    JUMP_CUT,
}

/**
 * Pure piecewise-constant speed curve evaluator shared by timeline duration math,
 * Media3 video `SpeedChangeEffect`, and Media3 audio `SpeedChangingAudioProcessor`.
 */
object SpeedRampEvaluator {

    private val MONTAGE_CURVE = floatArrayOf(2.2f, 1.8f, 0.9f, 0.45f, 0.45f, 0.9f, 1.5f, 1.8f)
    private val HERO_CURVE = floatArrayOf(1.8f, 1.4f, 0.7f, 0.35f, 0.35f, 0.7f, 1.6f, 2.0f)
    private val BULLET_CURVE = floatArrayOf(1.6f, 1.2f, 0.5f, 0.30f, 0.30f, 0.5f, 1.0f, 1.3f)
    private val FLASH_IN_CURVE = floatArrayOf(2.6f, 2.0f, 1.3f, 0.95f, 0.85f, 0.85f, 0.9f, 1.0f)
    private val JUMP_CUT_CURVE = floatArrayOf(2.0f, 0.55f, 2.0f, 0.55f, 2.0f, 0.55f, 1.8f, 0.7f)

    fun segmentSpeeds(preset: SpeedRampPreset): FloatArray = when (preset) {
        SpeedRampPreset.NONE -> floatArrayOf(Clip.DEFAULT_PLAYBACK_SPEED)
        SpeedRampPreset.MONTAGE -> MONTAGE_CURVE
        SpeedRampPreset.HERO -> HERO_CURVE
        SpeedRampPreset.BULLET -> BULLET_CURVE
        SpeedRampPreset.FLASH_IN -> FLASH_IN_CURVE
        SpeedRampPreset.JUMP_CUT -> JUMP_CUT_CURVE
    }

    fun speedAt(
        preset: SpeedRampPreset,
        sourceTimeUs: Long,
        sourceDurationUs: Long,
    ): Float {
        if (preset == SpeedRampPreset.NONE || sourceDurationUs <= 0L) {
            return Clip.DEFAULT_PLAYBACK_SPEED
        }
        val speeds = segmentSpeeds(preset)
        val clampedUs = sourceTimeUs.coerceIn(0L, sourceDurationUs)
        val index = ((clampedUs * speeds.size) / sourceDurationUs).toInt().coerceIn(0, speeds.lastIndex)
        return speeds[index].coerceIn(Clip.MIN_PLAYBACK_SPEED, Clip.MAX_PLAYBACK_SPEED)
    }

    /**
     * Returns the source timestamp (in microseconds) of the next speed segment change,
     * or `-1L` when [sourceTimeUs] is in the final segment (caller maps `-1L` to `C.TIME_UNSET`).
     */
    fun nextChangeTimeUs(
        preset: SpeedRampPreset,
        sourceTimeUs: Long,
        sourceDurationUs: Long,
    ): Long {
        if (preset == SpeedRampPreset.NONE || sourceDurationUs <= 0L) return -1L
        val speeds = segmentSpeeds(preset)
        if (speeds.size <= 1) return -1L
        val clampedUs = sourceTimeUs.coerceAtLeast(0L)
        val currentIndex = ((clampedUs * speeds.size) / sourceDurationUs).toInt().coerceIn(0, speeds.lastIndex)
        val nextIndex = currentIndex + 1
        if (nextIndex >= speeds.size) return -1L
        val nextTimeUs = (sourceDurationUs * nextIndex) / speeds.size
        return if (nextTimeUs > clampedUs) nextTimeUs else -1L
    }

    fun scaledDurationMs(
        sourceSpanMs: Long,
        baseSpeed: Float,
        preset: SpeedRampPreset,
    ): Long {
        if (sourceSpanMs <= 0L) return 0L
        if (preset == SpeedRampPreset.NONE) {
            val safeSpeed = ProjectTimeline.sanitizeSpeed(baseSpeed)
            if (safeSpeed == Clip.DEFAULT_PLAYBACK_SPEED) return sourceSpanMs
            return (sourceSpanMs / safeSpeed).roundToLong().coerceAtLeast(1L)
        }
        val speeds = segmentSpeeds(preset)
        val segMs = sourceSpanMs.toDouble() / speeds.size.toDouble()
        var totalMs = 0.0
        for (s in speeds) {
            val clamped = s.coerceIn(Clip.MIN_PLAYBACK_SPEED, Clip.MAX_PLAYBACK_SPEED)
            totalMs += segMs / clamped.toDouble()
        }
        return totalMs.roundToLong().coerceAtLeast(1L)
    }
}
