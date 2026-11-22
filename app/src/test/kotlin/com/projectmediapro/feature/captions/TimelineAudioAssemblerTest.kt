package com.projectmediapro.feature.captions

import com.projectmediapro.core.model.Clip
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TimelineAudioAssemblerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun assemble_enforcesGlobal20MinuteCapOnLongSingleClipAndHighSpeedClip() = runTest {
        val file = tempFolder.newFile("long.mp4")
        val recordedMaxDurations = mutableListOf<Long>()
        val recordingExtractor = object : PcmAudioExtractor {
            override suspend fun extractMono16kPcm(
                file: File,
                trimStartUs: Long,
                trimEndUs: Long,
                maxDurationUs: Long,
            ): PcmAudioData {
                recordedMaxDurations.add(maxDurationUs)
                return PcmAudioData(ShortArray(0), PcmAudioData.TARGET_SAMPLE_RATE_HZ, 1, 0L)
            }
        }

        val twoHourClip = Clip(
            id = "v-long",
            mediaUri = "p1/long.mp4",
            startTimeMs = 0L,
            durationMs = 2L * 60L * 60L * 1_000L,
            trimStartMs = 0L,
            trimEndMs = 2L * 60L * 60L * 1_000L,
            sourceDurationMs = 2L * 60L * 60L * 1_000L,
        )
        val resultLong = TimelineAudioAssembler.assemble(
            videoClips = listOf(twoHourClip),
            audioClips = emptyList(),
            extractor = recordingExtractor,
            resolveFile = { file },
        )
        assertTrue(resultLong.wasTruncated)
        assertEquals(PcmAudioExtractor.DEFAULT_MAX_DURATION_US, resultLong.pcm.durationUs)
        assertEquals(1, recordedMaxDurations.size)
        assertTrue(
            "Expected extractor maxDurationUs <= 20 min, got ${recordedMaxDurations[0]}",
            recordedMaxDurations[0] <= PcmAudioExtractor.DEFAULT_MAX_DURATION_US,
        )

        recordedMaxDurations.clear()
        val fastLongClip = Clip(
            id = "v-fast-long",
            mediaUri = "p1/long.mp4",
            startTimeMs = 0L,
            durationMs = 15L * 60L * 1_000L,
            trimStartMs = 0L,
            trimEndMs = 30L * 60L * 1_000L,
            sourceDurationMs = 30L * 60L * 1_000L,
            playbackSpeed = 2.0f,
        )
        val resultFast = TimelineAudioAssembler.assemble(
            videoClips = listOf(fastLongClip),
            audioClips = emptyList(),
            extractor = recordingExtractor,
            resolveFile = { file },
        )
        assertTrue(resultFast.wasTruncated)
        assertEquals(1, recordedMaxDurations.size)
        assertTrue(recordedMaxDurations[0] <= PcmAudioExtractor.DEFAULT_MAX_DURATION_US)
        assertEquals(
            PcmAudioExtractor.DEFAULT_MAX_DURATION_US,
            PcmAudioExtractor.clampMaxDurationUs(Long.MAX_VALUE),
        )
    }

    @Test
    fun assemble_applies2xAndHalfSpeedResamplingToTimelinePositions() = runTest {
        val sampleRate = PcmAudioData.TARGET_SAMPLE_RATE_HZ
        val file = tempFolder.newFile("tone.mp4")

        val extractor = object : PcmAudioExtractor {
            override suspend fun extractMono16kPcm(
                file: File,
                trimStartUs: Long,
                trimEndUs: Long,
                maxDurationUs: Long,
            ): PcmAudioData {
                val totalSec = (maxDurationUs / CaptionTime.MICROS_PER_SEC).toInt().coerceAtLeast(1)
                val samples = ShortArray(totalSec * sampleRate) { idx ->
                    val sec = idx / sampleRate
                    // Place loud burst strictly in source [2s..3s) for 4s clip, or [1s..2s) for 2s clip.
                    val active = if (totalSec >= 4) sec == 2 else sec == 1
                    if (active) 20_000 else 0
                }
                return PcmAudioData(
                    samples = samples,
                    sampleRateHz = sampleRate,
                    channelCount = 1,
                    durationUs = totalSec * CaptionTime.MICROS_PER_SEC,
                )
            }
        }

        // 4s source at 2.0x -> 2s timeline; source [2s..3s) must land at timeline [1.0s..1.5s).
        val clip2x = Clip(
            id = "v-2x",
            mediaUri = "p1/tone.mp4",
            startTimeMs = 0L,
            durationMs = 2_000L,
            trimStartMs = 0L,
            trimEndMs = 4_000L,
            sourceDurationMs = 4_000L,
            playbackSpeed = 2.0f,
        )
        val out2x = TimelineAudioAssembler.assemble(
            videoClips = listOf(clip2x),
            audioClips = emptyList(),
            extractor = extractor,
            resolveFile = { file },
        )
        assertFalse(out2x.wasTruncated)
        assertEquals(2_000_000L, out2x.pcm.durationUs)
        assertEquals(0, abs(out2x.pcm.samples[sampleRate / 2].toInt()))
        assertTrue(abs(out2x.pcm.samples[(sampleRate * 5) / 4].toInt()) > 15_000)

        // 2s source at 0.5x -> 4s timeline; source [1s..2s) must land at timeline [2.0s..4.0s).
        val clipHalf = Clip(
            id = "v-half",
            mediaUri = "p1/tone.mp4",
            startTimeMs = 0L,
            durationMs = 4_000L,
            trimStartMs = 0L,
            trimEndMs = 2_000L,
            sourceDurationMs = 2_000L,
            playbackSpeed = 0.5f,
        )
        val outHalf = TimelineAudioAssembler.assemble(
            videoClips = listOf(clipHalf),
            audioClips = emptyList(),
            extractor = extractor,
            resolveFile = { file },
        )
        assertFalse(outHalf.wasTruncated)
        assertEquals(4_000_000L, outHalf.pcm.durationUs)
        assertEquals(0, abs(outHalf.pcm.samples[sampleRate].toInt()))
        assertTrue(abs(outHalf.pcm.samples[sampleRate * 3].toInt()) > 15_000)
    }

    @Test
    fun assemble_skipsMutedVideoAndFreezeFramesWhileMixingSeparateAudioTrack() = runTest {
        val sampleRate = PcmAudioData.TARGET_SAMPLE_RATE_HZ
        val videoFile = tempFolder.newFile("v.mp4")
        val freezeFile = tempFolder.newFile("f.png")
        val audioFile = tempFolder.newFile("a.mp3")
        val extractedFiles = mutableListOf<String>()

        val extractor = object : PcmAudioExtractor {
            override suspend fun extractMono16kPcm(
                file: File,
                trimStartUs: Long,
                trimEndUs: Long,
                maxDurationUs: Long,
            ): PcmAudioData {
                extractedFiles.add(file.name)
                val samples = ShortArray(sampleRate * 2) { 10_000 }
                return PcmAudioData(samples, sampleRate, 1, 2_000_000L)
            }
        }

        val mutedVideo = Clip(
            id = "v1",
            mediaUri = "p1/v.mp4",
            startTimeMs = 0L,
            durationMs = 1_000L,
            trimStartMs = 0L,
            trimEndMs = 1_000L,
            sourceDurationMs = 1_000L,
            volume = 0f,
        )
        val freezeClip = Clip(
            id = "f1",
            mediaUri = "p1/f.png",
            startTimeMs = 1_000L,
            durationMs = 1_000L,
            trimStartMs = 0L,
            trimEndMs = 1_000L,
            sourceDurationMs = 1_000L,
            volume = 0f,
            isStillImage = true,
        )
        val musicClip = Clip(
            id = "a1",
            mediaUri = "p1/a.mp3",
            startTimeMs = 0L,
            durationMs = 2_000L,
            trimStartMs = 0L,
            trimEndMs = 2_000L,
            sourceDurationMs = 2_000L,
            volume = 0.5f,
        )

        val result = TimelineAudioAssembler.assemble(
            videoClips = listOf(mutedVideo, freezeClip),
            audioClips = listOf(musicClip),
            extractor = extractor,
            resolveFile = { rel ->
                when {
                    rel.endsWith("v.mp4") -> videoFile
                    rel.endsWith("f.png") -> freezeFile
                    else -> audioFile
                }
            },
        )

        assertEquals(listOf("a.mp3"), extractedFiles)
        assertEquals(2_000_000L, result.pcm.durationUs)
        assertEquals(5_000, result.pcm.samples[sampleRate].toInt())
    }
}
