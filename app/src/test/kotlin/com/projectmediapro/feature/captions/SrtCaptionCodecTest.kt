package com.projectmediapro.feature.captions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtCaptionCodecTest {

    @Test
    fun parseSrt_andExportSrt_roundTripsAndSplitsWordsBySyllables() {
        val sampleSrt = "\uFEFF" + """
            1
            00:00:01,000 --> 00:00:02,500
            <i>Hello</i> unbelievable

            2
            00:00:03,000 --> 00:00:04,200
            world today
        """.trimIndent()

        val doc = SrtCaptionCodec.parseSrt(
            srtText = sampleSrt,
            projectId = "proj_srt",
            preset = LyricsPresetRegistry.SPEECH,
        )

        assertEquals(4, doc.rawWords.size)
        assertEquals(listOf("Hello", "unbelievable", "world", "today"), doc.rawWords.map { it.text })
        assertTrue(doc.rawWords[1].durationUs > doc.rawWords[0].durationUs)

        val exported = SrtCaptionCodec.exportSrt(doc.copy(globalOffsetUs = 500_000L))
        assertTrue(exported.contains("00:00:01,500 -->"))
        assertTrue(exported.contains("Hello unbelievable"))
        assertTrue(exported.contains("world today"))
    }
}
