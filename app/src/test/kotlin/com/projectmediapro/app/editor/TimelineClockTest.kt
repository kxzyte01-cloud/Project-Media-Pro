package com.projectmediapro.app.editor

import com.projectmediapro.core.model.Clip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineClockTest {

    @Test
    fun playerToTimelineMs_mapsSingleMultipleAndClampedPositions() {
        val durations = listOf(4_000L, 3_500L, 2_000L)

        assertEquals(0L, TimelineClock.playerToTimelineMs(emptyList(), 0, 500L))
        assertEquals(0L, TimelineClock.playerToTimelineMs(durations, -1, 500L))
        assertEquals(1_200L, TimelineClock.playerToTimelineMs(durations, 0, 1_200L))
        assertEquals(5_000L, TimelineClock.playerToTimelineMs(durations, 1, 1_000L))
        assertEquals(9_500L, TimelineClock.playerToTimelineMs(durations, 2, 5_000L))
        assertEquals(7_500L, TimelineClock.playerToTimelineMs(durations, 2, -200L))
    }

    @Test
    fun isOverlayVisibleAt_usesHalfOpenStartToEndRange() {
        val overlay = Clip(
            id = "o1",
            mediaUri = "p1/o1.png",
            startTimeMs = 1_000L,
            durationMs = 2_000L,
        )

        assertFalse(TimelineClock.isOverlayVisibleAt(overlay, 999L))
        assertTrue(TimelineClock.isOverlayVisibleAt(overlay, 1_000L))
        assertTrue(TimelineClock.isOverlayVisibleAt(overlay, 2_999L))
        assertFalse(TimelineClock.isOverlayVisibleAt(overlay, 3_000L))
    }
}
