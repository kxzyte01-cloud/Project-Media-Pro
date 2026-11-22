package com.projectmediapro.feature.captions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcCaptionCodecTest {

    @Test
    fun parseLrc_parsesExactTimestampsStripsMetadataAndHonorsEmptyEndMarkers() {
        val lrc = """
            [id: q4abhue]
            [ar: Arctic Monkeys]
            [al: Favourite Worst Nightmare]
            [ti: 505]
            [length: 04:13]

            [00:12.41]I'm going back to 505
            [00:16.47]If it's a seven-hour flight
            [00:30.36]With your hands between your thighs
            [00:34.37]
            [00:40.94]Stop and wait a sec
        """.trimIndent()

        assertTrue(SrtCaptionCodec.isLrcFormat(lrc))
        val doc = SrtCaptionCodec.parseLrc(lrc, projectId = "p-505")

        assertTrue(doc.rawWords.isNotEmpty())
        assertTrue(doc.rawWords.none { it.text.contains("Arctic") || it.text.contains("[00:") })
        assertEquals("I'm", doc.rawWords.first().text)
        assertEquals(12_410_000L, doc.rawWords.first().startUs)

        val thighsWord = doc.rawWords.first { it.text == "thighs" }
        assertEquals(34_370_000L, thighsWord.endUs)

        val stopWord = doc.rawWords.first { it.text == "Stop" }
        assertEquals(40_940_000L, stopWord.startUs)
    }

    @Test
    fun parseSrt_autoDetectsLrcInputAndSupportsMultiTimestampLines() {
        val lrc = """
            [00:05.00][00:20.00]Repeated chorus line
            [00:08.50]
        """.trimIndent()

        assertFalse(SrtCaptionCodec.isSrtFormat(lrc))
        val doc = SrtCaptionCodec.parseSrt(lrc, projectId = "p-multi")
        val repeatedStarts = doc.rawWords.filter { it.text == "Repeated" }.map { it.startUs }
        assertEquals(listOf(5_000_000L, 20_000_000L), repeatedStarts)
    }
}
