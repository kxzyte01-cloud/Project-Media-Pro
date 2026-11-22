package com.projectmediapro.feature.captions

import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline
import java.io.File
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException

data class AssembledTimelineAudio(
    val pcm: PcmAudioData,
    val wasTruncated: Boolean,
)

/**
 * Assembles a single timeline-aligned 16 kHz mono [PcmAudioData] buffer by extracting and mixing
 * trimmed, speed-adjusted [videoClips] and [audioClips] directly into a single bounded 16-bit
 * buffer capped at [PcmAudioExtractor.DEFAULT_MAX_DURATION_US].
 */
object TimelineAudioAssembler {

    suspend fun assemble(
        videoClips: List<Clip>,
        audioClips: List<Clip>,
        extractor: PcmAudioExtractor,
        resolveFile: (String) -> File?,
    ): AssembledTimelineAudio {
        val sampleRate = PcmAudioData.TARGET_SAMPLE_RATE_HZ
        val maxDurationMs = PcmAudioExtractor.DEFAULT_MAX_DURATION_US / CaptionTime.MICROS_PER_MS
        val maxSamples = ((PcmAudioExtractor.DEFAULT_MAX_DURATION_US * sampleRate) / CaptionTime.MICROS_PER_SEC)
            .toInt()
            .coerceAtLeast(1)

        val videoDurationMs = videoClips.sumOf { ProjectTimeline.effectiveDurationMs(it).coerceAtLeast(0L) }
        val audioDurationMs = audioClips.sumOf { ProjectTimeline.effectiveDurationMs(it).coerceAtLeast(0L) }
        val targetDurationMs = if (videoDurationMs > 0L) videoDurationMs else audioDurationMs
        if (targetDurationMs <= 0L) {
            return AssembledTimelineAudio(
                pcm = PcmAudioData(ShortArray(0), sampleRate, 1, 0L),
                wasTruncated = false,
            )
        }

        var wasTruncated = targetDurationMs > maxDurationMs
        val clampedDurationMs = targetDurationMs.coerceAtMost(maxDurationMs)
        val totalSamples = ((clampedDurationMs * sampleRate) / 1_000L)
            .toInt()
            .coerceIn(1, maxSamples)
        // Mix directly into a single ShortArray with saturating addition instead of holding both an
        // IntArray (76.8 MB at 20 min) and ShortArray (38.4 MB) simultaneously.
        val mixBuffer = ShortArray(totalSamples)

        if (mixTrackInto(videoClips, sampleRate, mixBuffer, extractor, resolveFile)) {
            wasTruncated = true
        }
        if (mixTrackInto(audioClips, sampleRate, mixBuffer, extractor, resolveFile)) {
            wasTruncated = true
        }

        val mergedDurationUs = (totalSamples.toLong() * CaptionTime.MICROS_PER_SEC) / sampleRate
        return AssembledTimelineAudio(
            pcm = PcmAudioData(
                samples = mixBuffer,
                sampleRateHz = sampleRate,
                channelCount = 1,
                durationUs = mergedDurationUs,
            ),
            wasTruncated = wasTruncated,
        )
    }

    private suspend fun mixTrackInto(
        clips: List<Clip>,
        sampleRate: Int,
        mixBuffer: ShortArray,
        extractor: PcmAudioExtractor,
        resolveFile: (String) -> File?,
    ): Boolean {
        var sampleCursor = 0
        var sourceTruncated = false
        for (clip in clips) {
            if (sampleCursor >= mixBuffer.size) break
            val clipDurationMs = ProjectTimeline.effectiveDurationMs(clip)
            if (clipDurationMs <= 0L) continue
            val remainingSamples = mixBuffer.size - sampleCursor
            val clipSamples = ((clipDurationMs * sampleRate) / 1_000L)
                .toInt()
                .coerceIn(1, remainingSamples)
            val gain = if (clip.isStillImage) 0f else ProjectTimeline.sanitizeVolume(clip.volume)
            val file = resolveFile(clip.mediaUri)

            if (!clip.isStillImage && gain > 0f && file != null && file.isFile) {
                val speed = ProjectTimeline.effectiveSpeed(clip)
                val (trimStartMs, trimEndMs) = ProjectTimeline.resolvedTrimBounds(clip)
                val trimStartUs = trimStartMs * CaptionTime.MICROS_PER_MS

                val timelineWindowUs = (clipSamples.toLong() * CaptionTime.MICROS_PER_SEC) / sampleRate
                val requiredSourceUs = (timelineWindowUs.toDouble() * speed.toDouble())
                    .roundToLong()
                    .coerceAtLeast(CaptionTime.MICROS_PER_MS)
                if (requiredSourceUs > PcmAudioExtractor.DEFAULT_MAX_DURATION_US) {
                    sourceTruncated = true
                }
                val cappedSourceUs = requiredSourceUs.coerceAtMost(PcmAudioExtractor.DEFAULT_MAX_DURATION_US)

                val explicitTrimEndUs = if (trimEndMs > trimStartMs) {
                    trimEndMs * CaptionTime.MICROS_PER_MS
                } else {
                    Long.MAX_VALUE
                }
                val boundedTrimEndUs = if (trimStartUs <= Long.MAX_VALUE - cappedSourceUs) {
                    minOf(explicitTrimEndUs, trimStartUs + cappedSourceUs)
                } else {
                    explicitTrimEndUs
                }

                val pcm = try {
                    extractor.extractMono16kPcm(
                        file = file,
                        trimStartUs = trimStartUs,
                        trimEndUs = boundedTrimEndUs,
                        maxDurationUs = cappedSourceUs,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: PcmExtractionException) {
                    null
                }
                if (pcm != null && pcm.samples.isNotEmpty()) {
                    mixResampledClip(
                        sourceSamples = pcm.samples,
                        speed = speed,
                        gain = gain,
                        mixBuffer = mixBuffer,
                        destOffset = sampleCursor,
                        destCount = clipSamples,
                    )
                }
            }
            sampleCursor += clipSamples
        }
        return sourceTruncated
    }

    private fun mixResampledClip(
        sourceSamples: ShortArray,
        speed: Float,
        gain: Float,
        mixBuffer: ShortArray,
        destOffset: Int,
        destCount: Int,
    ) {
        if (speed == Clip.DEFAULT_PLAYBACK_SPEED) {
            val count = minOf(sourceSamples.size, destCount)
            for (i in 0 until count) {
                val sample = if (gain == 1f) {
                    sourceSamples[i].toInt()
                } else {
                    (sourceSamples[i] * gain).roundToInt()
                }
                val idx = destOffset + i
                mixBuffer[idx] = (mixBuffer[idx].toInt() + sample)
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            return
        }

        val lastSourceIndex = sourceSamples.lastIndex
        val step = speed.toDouble()
        for (i in 0 until destCount) {
            val srcPos = i * step
            val idx0 = floor(srcPos).toInt()
            if (idx0 > lastSourceIndex) break
            val idx1 = (idx0 + 1).coerceAtMost(lastSourceIndex)
            val frac = (srcPos - idx0).toFloat()
            val interpolated = sourceSamples[idx0] + (sourceSamples[idx1] - sourceSamples[idx0]) * frac
            val scaled = (interpolated * gain).roundToInt()
            val destIdx = destOffset + i
            mixBuffer[destIdx] = (mixBuffer[destIdx].toInt() + scaled)
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }
}
