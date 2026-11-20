package com.projectmediapro.feature.captions

import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

/**
 * Assembles a single timeline-aligned 16 kHz mono [PcmAudioData] buffer by extracting and mixing
 * trimmed [videoClips] and [audioClips] (e.g., music or voiceover tracks for lyric videos).
 */
object TimelineAudioAssembler {

    suspend fun assemble(
        videoClips: List<Clip>,
        audioClips: List<Clip>,
        extractor: PcmAudioExtractor,
        resolveFile: (String) -> File?,
    ): PcmAudioData {
        val sampleRate = PcmAudioData.TARGET_SAMPLE_RATE_HZ
        val maxSamples = ((PcmAudioExtractor.DEFAULT_MAX_DURATION_US * sampleRate) / CaptionTime.MICROS_PER_SEC)
            .toInt()
            .coerceAtLeast(1)

        val videoDurationMs = videoClips.sumOf { ProjectTimeline.effectiveDurationMs(it).coerceAtLeast(0L) }
        val audioDurationMs = audioClips.sumOf { ProjectTimeline.effectiveDurationMs(it).coerceAtLeast(0L) }
        val targetDurationMs = if (videoDurationMs > 0L) videoDurationMs else audioDurationMs
        if (targetDurationMs <= 0L) {
            return PcmAudioData(ShortArray(0), sampleRate, 1, 0L)
        }

        val totalSamples = ((targetDurationMs * sampleRate) / 1_000L)
            .toInt()
            .coerceIn(1, maxSamples)
        val mixBuffer = IntArray(totalSamples)

        mixTrackInto(
            clips = videoClips,
            useClipVolume = false,
            sampleRate = sampleRate,
            mixBuffer = mixBuffer,
            extractor = extractor,
            resolveFile = resolveFile,
        )
        mixTrackInto(
            clips = audioClips,
            useClipVolume = true,
            sampleRate = sampleRate,
            mixBuffer = mixBuffer,
            extractor = extractor,
            resolveFile = resolveFile,
        )

        val merged = ShortArray(totalSamples) { i ->
            mixBuffer[i].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val mergedDurationUs = (totalSamples.toLong() * CaptionTime.MICROS_PER_SEC) / sampleRate
        return PcmAudioData(
            samples = merged,
            sampleRateHz = sampleRate,
            channelCount = 1,
            durationUs = mergedDurationUs,
        )
    }

    private suspend fun mixTrackInto(
        clips: List<Clip>,
        useClipVolume: Boolean,
        sampleRate: Int,
        mixBuffer: IntArray,
        extractor: PcmAudioExtractor,
        resolveFile: (String) -> File?,
    ) {
        var sampleCursor = 0
        for (clip in clips) {
            if (sampleCursor >= mixBuffer.size) break
            val clipDurationMs = ProjectTimeline.effectiveDurationMs(clip)
            if (clipDurationMs <= 0L) continue
            val clipSamples = ((clipDurationMs * sampleRate) / 1_000L)
                .toInt()
                .coerceIn(1, mixBuffer.size - sampleCursor)
            val gain = if (useClipVolume) clip.volume.coerceIn(0f, 1f) else 1f
            val file = resolveFile(clip.mediaUri)

            if (gain > 0f && file != null && file.isFile) {
                val (trimStartMs, trimEndMs) = ProjectTimeline.resolvedTrimBounds(clip)
                val trimStartUs = trimStartMs * CaptionTime.MICROS_PER_MS
                val trimEndUs = if (trimEndMs > trimStartMs) {
                    trimEndMs * CaptionTime.MICROS_PER_MS
                } else {
                    Long.MAX_VALUE
                }
                val pcm = try {
                    extractor.extractMono16kPcm(
                        file = file,
                        trimStartUs = trimStartUs,
                        trimEndUs = trimEndUs,
                        maxDurationUs = clipDurationMs * CaptionTime.MICROS_PER_MS,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: PcmExtractionException) {
                    null
                }
                if (pcm != null && pcm.samples.isNotEmpty()) {
                    val count = minOf(pcm.samples.size, clipSamples)
                    for (i in 0 until count) {
                        val scaled = if (gain == 1f) {
                            pcm.samples[i].toInt()
                        } else {
                            (pcm.samples[i] * gain).roundToInt()
                        }
                        mixBuffer[sampleCursor + i] += scaled
                    }
                }
            }
            sampleCursor += clipSamples
        }
    }
}
