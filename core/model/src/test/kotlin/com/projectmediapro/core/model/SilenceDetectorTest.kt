package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SilenceDetectorTest {

    private fun buildToneWithSilence(sampleRateHz: Int = 16_000): ShortArray {
        // 2s tone (0..2s) + 2s silence (2..4s) + 2s tone (4..6s) = 6s total
        val totalSamples = sampleRateHz * 6
        val pcm = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val sec = i.toDouble() / sampleRateHz.toDouble()
            val isSpeech = sec < 2.0 || sec >= 4.0
            pcm[i] = if (isSpeech) {
                (sin(2.0 * PI * 440.0 * sec) * 14_000.0).toInt().toShort()
            } else {
                0
            }
        }
        return pcm
    }

    @Test
    fun detectSilencesFindsPauseAndPreserves80msSpeechPadding() {
        val pcm = buildToneWithSilence()
        val silences = SilenceDetector.detectSilences(
            samples = pcm,
            sampleRateHz = 16_000,
            config = SilenceDetectionConfig(thresholdDb = -35f, minSilenceMs = 600L, paddingMs = 80L),
        )
        assertEquals(1, silences.size)
        val s = silences.single()
        // Speech ends at 2000ms -> padded silence start should be >= 2050ms
        assertTrue("Silence start (${s.startMs}) should keep ~80ms padding after 2000ms", s.startMs in 2_050L..2_150L)
        // Speech resumes at 4000ms -> padded silence end should be <= 3950ms
        assertTrue("Silence end (${s.endMs}) should keep ~80ms padding before 4000ms", s.endMs in 3_880L..3_950L)
    }

    @Test
    fun compactSpeechPcmAndRemapTimestampsMatchOriginalTimeline() {
        val pcm = buildToneWithSilence()
        val silences = SilenceDetector.detectSilences(pcm, 16_000)
        val speech = SilenceDetector.computeSpeechIntervals(6_000L, silences)
        assertEquals(2, speech.size)

        val compacted = SilenceDetector.compactSpeechPcm(pcm, 16_000, speech)
        assertTrue("Compacted audio should save >= 1.5s of silence", compacted.savedMs >= 1_500L)
        assertTrue(compacted.floatPcm.isNotEmpty())

        // Timestamp at 500ms in first speech chunk stays 500ms
        assertEquals(500L, SilenceDetector.remapPackedTimeMs(500L, compacted.mappings))

        // Timestamp 500ms into second speech chunk should map near ~4.4s in original timeline
        val secondChunk = compacted.mappings[1]
        val remapped = SilenceDetector.remapPackedTimeMs(secondChunk.packedStartMs + 500L, compacted.mappings)
        assertEquals(secondChunk.originalStartMs + 500L, remapped)
    }

    @Test
    fun removeSilencesFromVideoClipSplitsAroundPauseAndRespectsMinClipDuration() {
        val clip = Clip(
            id = "v1",
            mediaUri = "media/v1.mp4",
            startTimeMs = 0L,
            durationMs = 6_000L,
            sourceDurationMs = 6_000L,
            trimStartMs = 0L,
            trimEndMs = 6_000L,
        )
        val project = Project(
            id = "p1",
            title = "Silence Cut Test",
            durationMs = 6_000L,
            tracks = listOf(Track(id = "tv", type = TrackType.VIDEO, clips = listOf(clip))),
        )
        val silences = listOf(
            SilenceSegment(id = "s1", startMs = 2_080L, endMs = 3_920L, selected = true),
            SilenceSegment(id = "s2", startMs = 5_800L, endMs = 5_950L, selected = false), // unchecked -> ignored
        )
        var seq = 1
        val updated = ProjectTimeline.removeSilencesFromVideoClip(
            project = project,
            clipId = "v1",
            silences = silences,
            idProvider = { "cut_${seq++}" },
        )
        val videos = updated.videoClips()
        assertEquals(2, videos.size)
        assertEquals(0L, videos[0].startTimeMs)
        assertEquals(2_080L, videos[0].durationMs)
        assertEquals(2_080L, videos[1].startTimeMs)
        assertEquals(2_080L, videos[1].durationMs) // 6000 - 3920 = 2080ms
        assertEquals(4_160L, updated.durationMs)
    }
}
