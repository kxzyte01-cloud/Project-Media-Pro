package com.projectmediapro.app.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayImporterTest {

    @Test
    fun imageExtensionFor_mapsCommonAndGifMimeTypesAndFallsBackToPng() {
        assertEquals("png", imageExtensionFor("image/png"))
        assertEquals("jpg", imageExtensionFor("image/jpeg"))
        assertEquals("jpg", imageExtensionFor("IMAGE/JPG; charset=utf-8"))
        assertEquals("webp", imageExtensionFor("image/webp"))
        assertEquals("gif", imageExtensionFor("image/gif"))
        assertEquals("png", imageExtensionFor("image/bmp"))
        assertEquals("png", imageExtensionFor(null))
    }
}
