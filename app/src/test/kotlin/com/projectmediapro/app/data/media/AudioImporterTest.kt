package com.projectmediapro.app.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioImporterTest {

    @Test
    fun audioExtensionFor_mapsKnownMimeTypesAndFallsBackToM4a() {
        assertEquals("mp3", audioExtensionFor("audio/mpeg"))
        assertEquals("mp3", audioExtensionFor("audio/mp3"))
        assertEquals("mp3", audioExtensionFor("AUDIO/MPEG; charset=binary"))
        assertEquals("m4a", audioExtensionFor("audio/mp4"))
        assertEquals("m4a", audioExtensionFor("audio/x-m4a"))
        assertEquals("m4a", audioExtensionFor("audio/m4a"))
        assertEquals("ogg", audioExtensionFor("audio/ogg"))
        assertEquals("ogg", audioExtensionFor("audio/vorbis"))
        assertEquals("ogg", audioExtensionFor("application/ogg"))
        assertEquals("wav", audioExtensionFor("audio/wav"))
        assertEquals("wav", audioExtensionFor("audio/x-wav"))
        assertEquals("wav", audioExtensionFor("audio/wave"))
        assertEquals("wav", audioExtensionFor("audio/vnd.wave"))
        assertEquals("flac", audioExtensionFor("audio/flac"))
        assertEquals("flac", audioExtensionFor("audio/x-flac"))
        assertEquals("aac", audioExtensionFor("audio/aac"))
        assertEquals("aac", audioExtensionFor("audio/aacp"))
        assertEquals("aac", audioExtensionFor("audio/x-aac"))
        assertEquals("m4a", audioExtensionFor("audio/unknown"))
        assertEquals("m4a", audioExtensionFor(""))
        assertEquals("m4a", audioExtensionFor(null))
    }
}
