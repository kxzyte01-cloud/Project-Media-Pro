package com.projectmediapro.app.data.export

import com.projectmediapro.core.model.CanvasAspect
import com.projectmediapro.core.model.ExportQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportGeometryTest {

    @Test
    fun sizeFor_fixedAspectsAt1080p_returnsExactEvenSizes() {
        assertEquals(Pair(1920, 1080), ExportGeometry.sizeFor(CanvasAspect.RATIO_16_9, ExportQuality.FULL_HD_1080P, 0, 0))
        assertEquals(Pair(1080, 1920), ExportGeometry.sizeFor(CanvasAspect.RATIO_9_16, ExportQuality.FULL_HD_1080P, 0, 0))
        assertEquals(Pair(1080, 1080), ExportGeometry.sizeFor(CanvasAspect.RATIO_1_1, ExportQuality.FULL_HD_1080P, 0, 0))
        assertEquals(Pair(1080, 1350), ExportGeometry.sizeFor(CanvasAspect.RATIO_4_5, ExportQuality.FULL_HD_1080P, 0, 0))
    }

    @Test
    fun sizeFor_fixedAspectsAt720pAnd4K_scalesCleanlyWithEvenDimensions() {
        assertEquals(Pair(1280, 720), ExportGeometry.sizeFor(CanvasAspect.RATIO_16_9, ExportQuality.HD_720P, 0, 0))
        assertEquals(Pair(720, 1280), ExportGeometry.sizeFor(CanvasAspect.RATIO_9_16, ExportQuality.HD_720P, 0, 0))
        assertEquals(Pair(720, 720), ExportGeometry.sizeFor(CanvasAspect.RATIO_1_1, ExportQuality.HD_720P, 0, 0))
        assertEquals(Pair(720, 900), ExportGeometry.sizeFor(CanvasAspect.RATIO_4_5, ExportQuality.HD_720P, 0, 0))

        assertEquals(Pair(3840, 2160), ExportGeometry.sizeFor(CanvasAspect.RATIO_16_9, ExportQuality.UHD_4K, 0, 0))
        assertEquals(Pair(2160, 3840), ExportGeometry.sizeFor(CanvasAspect.RATIO_9_16, ExportQuality.UHD_4K, 0, 0))
        assertEquals(Pair(2160, 2160), ExportGeometry.sizeFor(CanvasAspect.RATIO_1_1, ExportQuality.UHD_4K, 0, 0))
        assertEquals(Pair(2160, 2700), ExportGeometry.sizeFor(CanvasAspect.RATIO_4_5, ExportQuality.UHD_4K, 0, 0))
    }

    @Test
    fun sizeFor_original_scalesToQualityCapKeepsShapeMakesEvenAndRejectsBadInput() {
        assertEquals(Pair(1280, 720), ExportGeometry.sizeFor(CanvasAspect.ORIGINAL, ExportQuality.HD_720P, 3840, 2160))
        assertEquals(Pair(1920, 1080), ExportGeometry.sizeFor(CanvasAspect.ORIGINAL, ExportQuality.FULL_HD_1080P, 3840, 2160))
        assertEquals(Pair(3840, 2160), ExportGeometry.sizeFor(CanvasAspect.ORIGINAL, ExportQuality.UHD_4K, 3840, 2160))
        assertEquals(Pair(1080, 1920), ExportGeometry.sizeFor(CanvasAspect.ORIGINAL, ExportQuality.FULL_HD_1080P, 2160, 3840))
        assertEquals(Pair(1278, 718), ExportGeometry.sizeFor(CanvasAspect.ORIGINAL, ExportQuality.FULL_HD_1080P, 1279, 719))
        assertNull(ExportGeometry.sizeFor(CanvasAspect.ORIGINAL, ExportQuality.FULL_HD_1080P, 0, 1080))
        assertNull(ExportGeometry.sizeFor(CanvasAspect.ORIGINAL, ExportQuality.FULL_HD_1080P, 1920, -1))
    }
}
