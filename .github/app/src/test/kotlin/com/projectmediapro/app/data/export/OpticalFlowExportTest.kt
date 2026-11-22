package com.projectmediapro.app.data.export

import com.projectmediapro.core.model.ExportBitrate
import com.projectmediapro.core.model.ExportFrameRate
import com.projectmediapro.core.model.ExportQuality
import com.projectmediapro.core.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpticalFlowExportTest {

    @Test
    fun `project defaults opticalFlowEnabled to false for schema v1 compatibility`() {
        val project = Project(id = "p1", title = "Test")
        assertFalse(project.opticalFlowEnabled)
    }

    @Test
    fun `opticalFlowEnabled disables direct transmux fast path so GPU shader runs`() {
        val baseClip = ExportClip(
            path = "/tmp/clip.mp4",
            trimStartMs = 0L,
            trimEndMs = 3_000L,
            durationMs = 3_000L,
        )
        val planOff = ExportPlan(
            clips = listOf(baseClip),
            totalDurationMs = 3_000L,
            exportQuality = ExportQuality.FULL_HD_1080P,
            exportFrameRate = ExportFrameRate.AUTO,
            exportBitrate = ExportBitrate.STANDARD,
            opticalFlowEnabled = false,
        )
        val planOn = planOff.copy(opticalFlowEnabled = true)

        assertTrue(ExportPlanner.canTransmuxDirectly(planOff, sourceLongSidePx = 1920))
        assertFalse(ExportPlanner.canTransmuxDirectly(planOn, sourceLongSidePx = 1920))
    }

    @Test
    fun `blendStrengthForDeltaUs adapts to frame rate and rejects scene cuts`() {
        assertEquals(0f, OpticalFlowMath.blendStrengthForDeltaUs(0L), 0.0001f)
        assertEquals(0f, OpticalFlowMath.blendStrengthForDeltaUs(-10_000L), 0.0001f)
        assertEquals(0f, OpticalFlowMath.blendStrengthForDeltaUs(300_000L), 0.0001f)

        val fps60 = OpticalFlowMath.blendStrengthForDeltaUs(16_667L)
        val fps30 = OpticalFlowMath.blendStrengthForDeltaUs(33_333L)
        val slowMo = OpticalFlowMath.blendStrengthForDeltaUs(80_000L)

        assertTrue("Expected 60fps flow > 0, got $fps60", fps60 > 0.1f)
        assertTrue("Expected 30fps flow > 60fps flow", fps30 > fps60)
        assertTrue("Expected slow-mo flow > 30fps flow", slowMo > fps30)
        assertTrue("Expected slow-mo flow <= 0.42f", slowMo <= 0.42f)
    }
}
