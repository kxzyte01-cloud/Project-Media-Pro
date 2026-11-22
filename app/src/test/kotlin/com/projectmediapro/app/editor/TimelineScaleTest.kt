package com.projectmediapro.app.editor

import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineScaleTest {

    @Test
    fun zoomAndCoordinateConversions_clampAndRoundTripCleanly() {
        assertEquals(TimelineScale.MIN_DP_PER_SECOND, TimelineScale.clampZoom(2f), 0.0001f)
        assertEquals(TimelineScale.MAX_DP_PER_SECOND, TimelineScale.clampZoom(500f), 0.0001f)
        assertEquals(TimelineScale.DEFAULT_DP_PER_SECOND, TimelineScale.clampZoom(Float.NaN), 0.0001f)

        val dp = TimelineScale.msToDp(4_000L, 32f)
        assertEquals(128f, dp, 0.001f)
        assertEquals(4_000L, TimelineScale.dpToMs(dp, 32f))
        assertEquals(0L, TimelineScale.dpToMs(-20f, 32f))
    }

    @Test
    fun rulerStepMs_adaptsCleanlyAcrossZoomLevels() {
        assertEquals(1_000L, TimelineScale.rulerStepMs(80f))
        assertEquals(2_000L, TimelineScale.rulerStepMs(32f))
        assertEquals(5_000L, TimelineScale.rulerStepMs(12f))
    }

    @Test
    fun computeDragTrimBounds_respectsSpeedAndMinimumDuration() {
        val clip = Clip(
            id = "v1",
            mediaUri = "p1/v1.mp4",
            startTimeMs = 0L,
            durationMs = 4_000L,
            trimStartMs = 2_000L,
            trimEndMs = 10_000L,
            sourceDurationMs = 12_000L,
            playbackSpeed = 2.0f,
        )

        val (startTrimmedStart, startTrimmedEnd) = TimelineScale.computeDragTrimBounds(
            clip = clip,
            edge = TimelineTrimEdge.START,
            deltaTimelineMs = 1_000L,
        )
        assertEquals(4_000L, startTrimmedStart)
        assertEquals(10_000L, startTrimmedEnd)

        val (clampedStart, _) = TimelineScale.computeDragTrimBounds(
            clip = clip,
            edge = TimelineTrimEdge.START,
            deltaTimelineMs = 20_000L,
        )
        assertEquals(10_000L - ProjectTimeline.MIN_CLIP_DURATION_MS, clampedStart)

        val (endStart, endTrimmedEnd) = TimelineScale.computeDragTrimBounds(
            clip = clip,
            edge = TimelineTrimEdge.END,
            deltaTimelineMs = 5_000L,
        )
        assertEquals(2_000L, endStart)
        assertEquals(12_000L, endTrimmedEnd)
    }

    @Test
    fun downsamplePcmToPeaks_normalizesAmplitudesAndHandlesSilence() {
        val silent = TimelineScale.downsamplePcmToPeaks(ShortArray(100), 10)
        assertEquals(10, silent.size)
        assertTrue(silent.all { it == 0f })

        val samples = ShortArray(40) { i ->
            when {
                i < 10 -> 0
                i < 20 -> 16384
                i < 30 -> 32767
                else -> -8192
            }
        }
        val peaks = TimelineScale.downsamplePcmToPeaks(samples, 4)
        assertEquals(4, peaks.size)
        assertEquals(0f, peaks[0], 0.001f)
        assertEquals(0.5f, peaks[1], 0.02f)
        assertEquals(1.0f, peaks[2], 0.001f)
        assertEquals(0.25f, peaks[3], 0.02f)
    }
}
