package com.projectmediapro.feature.captions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsPacingEngineTest {

    @Test
    fun buildLines_fastFlowPreset_neverOverlapsAndRespectsMaxWordsPerLine() {
        val rapidWords = List(12) { idx ->
            val startUs = idx * 80_000L
            WordTiming(
                id = "w-$idx",
                text = if (idx % 3 == 0) "multisyllable" else "bar$idx",
                startUs = startUs,
                endUs = startUs + 40_000L,
                syllableCount = if (idx % 3 == 0) 4 else 1,
            )
        }

        val lines = LyricsPacingEngine.buildLines(rapidWords, LyricsPresetRegistry.FAST_FLOW)
        assertTrue(lines.isNotEmpty())

        for (i in lines.indices) {
            val line = lines[i]
            assertTrue(
                "Line $i word count (${line.words.size}) exceeded Fast Flow budget",
                line.words.size <= LyricsPresetRegistry.FAST_FLOW.maxWordsPerLine + 1,
            )
            assertTrue(line.endUs > line.startUs)
            if (i + 1 < lines.size) {
                assertTrue(line.endUs <= lines[i + 1].startUs)
            }
            for (w in line.words.indices) {
                val word = line.words[w]
                assertTrue(word.endUs > word.startUs)
                if (w + 1 < line.words.size) {
                    assertTrue(word.endUs <= line.words[w + 1].startUs)
                }
            }
        }
    }

    @Test
    fun buildLines_syllableDensityFloor_expandsShortMultiSyllableWordWhenRoomExists() {
        val word = WordTiming(
            id = "w-0",
            text = "unbelievable",
            startUs = 1_000_000L,
            endUs = 1_050_000L,
            syllableCount = 5,
        )
        val preset = LyricsPresetRegistry.MELODIC
        val lines = LyricsPacingEngine.buildLines(listOf(word), preset)

        val evaluatedWord = lines.single().words.single()
        assertEquals(5L * preset.syllableDensityFloorUsPerSyllable, evaluatedWord.durationUs)
        assertTrue(lines.single().endUs - lines.single().startUs >= preset.minLineDurationUs)
    }

    @Test
    fun rebuildWithUpdatedWord_updatesTextClearsPlaceholderAndDeletesWhenBlank() {
        val initialWords = listOf(
            WordTiming("w-1", "[1]", 0L, 400_000L, 1, isPlaceholder = true),
            WordTiming("w-2", "world", 450_000L, 900_000L, 1),
        )
        val doc = CaptionDocument(
            projectId = "proj_1",
            rawWords = initialWords,
            lines = LyricsPacingEngine.buildLines(initialWords, LyricsPresetRegistry.SPEECH),
            presetId = LyricsPresetRegistry.SPEECH_ID,
        )

        val edited = LyricsPacingEngine.rebuildWithUpdatedWord(doc, "w-1", "universe")
        val updatedWord = edited.rawWords.first { it.id == "w-1" }
        assertEquals("universe", updatedWord.text)
        assertEquals(3, updatedWord.syllableCount)
        assertFalse(updatedWord.isPlaceholder)

        val deleted = LyricsPacingEngine.rebuildWithUpdatedWord(edited, "w-1", "   ")
        assertEquals(1, deleted.rawWords.size)
        assertEquals("w-2", deleted.rawWords.single().id)
    }
}
