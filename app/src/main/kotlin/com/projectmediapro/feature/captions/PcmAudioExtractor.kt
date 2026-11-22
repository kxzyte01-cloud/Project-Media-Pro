package com.projectmediapro.feature.captions

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class PcmAudioData(
    val samples: ShortArray,
    val sampleRateHz: Int = TARGET_SAMPLE_RATE_HZ,
    val channelCount: Int = 1,
    val durationUs: Long,
) {
    val hasAudio: Boolean
        get() = samples.isNotEmpty() && durationUs > 0L

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PcmAudioData) return false
        return sampleRateHz == other.sampleRateHz &&
            channelCount == other.channelCount &&
            durationUs == other.durationUs &&
            samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRateHz
        result = 31 * result + channelCount
        result = 31 * result + durationUs.hashCode()
        return result
    }

    companion object {
        const val TARGET_SAMPLE_RATE_HZ = 16_000
    }
}

class PcmExtractionException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

interface PcmAudioExtractor {
    suspend fun extractMono16kPcm(
        file: File,
        trimStartUs: Long = 0L,
        trimEndUs: Long = Long.MAX_VALUE,
        maxDurationUs: Long = DEFAULT_MAX_DURATION_US,
    ): PcmAudioData

    companion object {
        // Hard 20-minute ceiling at 16 kHz mono 16-bit PCM caps each extracted buffer at ~38.4 MB.
        const val DEFAULT_MAX_DURATION_US = 20L * 60L * CaptionTime.MICROS_PER_SEC

        fun clampMaxDurationUs(requestedUs: Long): Long =
            requestedUs.coerceIn(CaptionTime.MICROS_PER_SEC, DEFAULT_MAX_DURATION_US)
    }
}

/**
 * Pure JVM stateful linear resampler carrying fractional phase across arbitrary buffer boundaries
 * so 44.1 kHz / 48 kHz -> 16 kHz conversion accumulates zero timing drift.
 */
internal class ContinuousMono16kResampler(
    private val maxSamples: Int,
) {
    private var output = ShortArray(INITIAL_CAPACITY.coerceAtMost(maxSamples.coerceAtLeast(1)))
    private var size = 0
    private var phase = 0.0
    private var hasPrevSample = false
    private var prevSample = 0f

    val isFull: Boolean
        get() = size >= maxSamples

    fun appendMono(mono: FloatArray, sourceSampleRate: Int) {
        if (isFull || mono.isEmpty()) return
        val safeRate = sourceSampleRate.coerceAtLeast(1)

        if (safeRate == PcmAudioData.TARGET_SAMPLE_RATE_HZ) {
            val toCopy = (maxSamples - size).coerceAtMost(mono.size)
            ensureCapacity(size + toCopy)
            for (i in 0 until toCopy) {
                output[size++] = floatToShort(mono[i])
            }
            prevSample = mono.last()
            hasPrevSample = true
            phase = 0.0
            return
        }

        val step = safeRate.toDouble() / PcmAudioData.TARGET_SAMPLE_RATE_HZ.toDouble()
        val estimatedOut = ((mono.size.toDouble() / step) + 2.0).toInt()
        ensureCapacity((size + estimatedOut).coerceAtMost(maxSamples))

        var pos = phase
        while (pos < mono.size.toDouble() && size < maxSamples) {
            val idx0 = floor(pos).toInt()
            val frac = (pos - idx0).toFloat()
            val s0 = if (idx0 < 0) {
                if (hasPrevSample) prevSample else mono[0]
            } else {
                mono[idx0]
            }
            val s1 = if (idx0 + 1 < mono.size) mono[idx0 + 1] else mono.last()
            output[size++] = floatToShort(s0 + (s1 - s0) * frac)
            pos += step
        }

        phase = pos - mono.size.toDouble()
        prevSample = mono.last()
        hasPrevSample = true
    }

    fun finish(): ShortArray = if (size == output.size) output else output.copyOf(size)

    private fun floatToShort(sample: Float): Short {
        val scaled = (sample.coerceIn(-1f, 1f) * 32767f).roundToInt()
        return scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    private fun ensureCapacity(required: Int) {
        if (required <= output.size) return
        var nextCap = output.size.coerceAtLeast(1024)
        while (nextCap < required) {
            nextCap = (nextCap * 2).coerceAtMost(maxSamples)
            if (nextCap == maxSamples) break
        }
        output = output.copyOf(nextCap)
    }

    private companion object {
        const val INITIAL_CAPACITY = 32_768
    }
}

class MediaCodecPcmAudioExtractor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PcmAudioExtractor {

    override suspend fun extractMono16kPcm(
        file: File,
        trimStartUs: Long,
        trimEndUs: Long,
        maxDurationUs: Long,
    ): PcmAudioData = withContext(ioDispatcher) {
        if (!file.isFile) {
            throw PcmExtractionException("Media file does not exist: ${file.name}")
        }

        val safeTrimStartUs = trimStartUs.coerceAtLeast(0L)
        val safeMaxDurationUs = PcmAudioExtractor.clampMaxDurationUs(maxDurationUs)
        val maxSamples = ((safeMaxDurationUs / CaptionTime.MICROS_PER_SEC.toDouble()) *
            PcmAudioData.TARGET_SAMPLE_RATE_HZ)
            .toInt()
            .coerceAtLeast(PcmAudioData.TARGET_SAMPLE_RATE_HZ)

        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        var codecStarted = false

        try {
            extractor = MediaExtractor().apply {
                setDataSource(file.absolutePath)
            }

            val trackSelection = selectFirstAudioTrack(extractor)
                ?: return@withContext PcmAudioData(samples = ShortArray(0), durationUs = 0L)

            val (trackIndex, inputFormat) = trackSelection
            extractor.selectTrack(trackIndex)
            if (safeTrimStartUs > 0L) {
                extractor.seekTo(safeTrimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw PcmExtractionException("Missing audio MIME type")

            var sampleRate = inputFormat.getIntOrDefault(MediaFormat.KEY_SAMPLE_RATE, DEFAULT_SOURCE_RATE)
            var channelCount = inputFormat.getIntOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 1)
            var pcmEncoding = inputFormat.getIntOrDefault(
                MediaFormat.KEY_PCM_ENCODING,
                AudioFormat.ENCODING_PCM_16BIT,
            )

            codec = MediaCodec.createDecoderByType(mimeType).apply {
                configure(inputFormat, null, null, 0)
                start()
            }
            codecStarted = true

            val bufferInfo = MediaCodec.BufferInfo()
            val resampler = ContinuousMono16kResampler(maxSamples = maxSamples)
            var sawInputEos = false
            var sawOutputEos = false
            var idlePollsAfterEos = 0

            while (!sawOutputEos) {
                coroutineContext.ensureActive()

                if (!sawInputEos) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            val sampleTimeUs = extractor.sampleTime
                            if (sampleSize < 0 || (trimEndUs < Long.MAX_VALUE && sampleTimeUs > trimEndUs)) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    sampleTimeUs.coerceAtLeast(0L),
                                    0,
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                when {
                    outputIndex >= 0 -> {
                        idlePollsAfterEos = 0
                        try {
                            if (bufferInfo.size > 0 &&
                                bufferInfo.presentationTimeUs >= safeTrimStartUs &&
                                bufferInfo.presentationTimeUs <= trimEndUs
                            ) {
                                val outBuffer = codec.getOutputBuffer(outputIndex)
                                if (outBuffer != null) {
                                    outBuffer.position(bufferInfo.offset)
                                    outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                    val mono = decodeToMonoNormalized(
                                        buffer = outBuffer,
                                        channelCount = channelCount.coerceAtLeast(1),
                                        pcmEncoding = pcmEncoding,
                                    )
                                    resampler.appendMono(mono, sampleRate)
                                }
                            }
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 ||
                                resampler.isFull
                            ) {
                                sawOutputEos = true
                            }
                        } finally {
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }

                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        idlePollsAfterEos = 0
                        val newFormat = codec.outputFormat
                        sampleRate = newFormat.getIntOrDefault(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channelCount = newFormat.getIntOrDefault(MediaFormat.KEY_CHANNEL_COUNT, channelCount)
                        pcmEncoding = newFormat.getIntOrDefault(MediaFormat.KEY_PCM_ENCODING, pcmEncoding)
                    }

                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (sawInputEos && ++idlePollsAfterEos >= MAX_IDLE_POLLS_AFTER_EOS) {
                            sawOutputEos = true
                        }
                    }
                }
            }

            val samples = resampler.finish()
            val computedDurationUs = (samples.size.toLong() * CaptionTime.MICROS_PER_SEC) /
                PcmAudioData.TARGET_SAMPLE_RATE_HZ
            PcmAudioData(
                samples = samples,
                sampleRateHz = PcmAudioData.TARGET_SAMPLE_RATE_HZ,
                channelCount = 1,
                durationUs = computedDurationUs,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (known: PcmExtractionException) {
            throw known
        } catch (error: Exception) {
            throw PcmExtractionException("Failed to decode audio track", error)
        } finally {
            if (codec != null) {
                if (codecStarted) {
                    runCatching { codec.stop() }
                }
                runCatching { codec.release() }
            }
            if (extractor != null) {
                runCatching { extractor.release() }
            }
        }
    }

    private fun selectFirstAudioTrack(extractor: MediaExtractor): Pair<Int, MediaFormat>? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                return i to format
            }
        }
        return null
    }

    private fun MediaFormat.getIntOrDefault(key: String, defaultValue: Int): Int =
        if (containsKey(key)) getInteger(key) else defaultValue

    private fun decodeToMonoNormalized(
        buffer: ByteBuffer,
        channelCount: Int,
        pcmEncoding: Int,
    ): FloatArray {
        val ordered = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val frameCount = (ordered.remaining() / 4) / channelCount
                FloatArray(frameCount) {
                    var sum = 0f
                    for (c in 0 until channelCount) sum += ordered.float
                    (sum / channelCount).coerceIn(-1f, 1f)
                }
            }

            AudioFormat.ENCODING_PCM_8BIT -> {
                val frameCount = ordered.remaining() / channelCount
                FloatArray(frameCount) {
                    var sum = 0f
                    for (c in 0 until channelCount) {
                        val unsigned = ordered.get().toInt() and 0xFF
                        sum += (unsigned - 128) / 128f
                    }
                    (sum / channelCount).coerceIn(-1f, 1f)
                }
            }

            else -> {
                val frameCount = (ordered.remaining() / 2) / channelCount
                FloatArray(frameCount) {
                    var sum = 0f
                    for (c in 0 until channelCount) sum += ordered.short.toFloat() / 32768f
                    (sum / channelCount).coerceIn(-1f, 1f)
                }
            }
        }
    }

    private companion object {
        const val CODEC_TIMEOUT_US = 10_000L
        const val DEFAULT_SOURCE_RATE = 44_100
        const val MAX_IDLE_POLLS_AFTER_EOS = 500
    }
}
