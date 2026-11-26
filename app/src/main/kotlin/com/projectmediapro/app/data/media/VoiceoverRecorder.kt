package com.projectmediapro.app.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A finished voiceover take ready to insert on the audio track. */
data class RecordedVoiceover(
    val relativePath: String,
    val durationMs: Long,
)

/**
 * Offline AAC voiceover recorder writing directly into [ProjectMediaStorage].
 * 44.1 kHz mono 128 kbps, MPEG_4 / AAC (.m4a). Polls maxAmplitude ~55 ms for live bars.
 */
class VoiceoverRecorder(
    context: Context,
    private val storage: ProjectMediaStorage,
    private val projectId: String,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) {
    sealed interface State {
        data object Idle : State
        data class Recording(val elapsedMs: Long, val amplitudeLevel: Float) : State
        data class Error(val message: String) : State
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var pollJob: Job? = null
    private var outputFile: File? = null
    private var relativePath: String? = null
    private var startedAtMs: Long = 0L
    private var smoothedLevel: Float = 0f

    val isRecording: Boolean get() = _state.value is State.Recording

    fun start(): Result<Unit> {
        if (isRecording) return Result.success(Unit)
        val clipId = idProvider()
        val rel = storage.clipRelativePath(projectId, clipId, "m4a")
        val target = storage.resolve(rel) ?: return Result.failure(IllegalStateException("Invalid storage path"))
        target.parentFile?.mkdirs()
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioSamplingRate(SAMPLE_RATE_HZ)
            rec.setAudioChannels(1)
            rec.setAudioEncodingBitRate(BIT_RATE_BPS)
            rec.setOutputFile(target.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            outputFile = target
            relativePath = rel
            startedAtMs = SystemClock.elapsedRealtime()
            smoothedLevel = 0f
            _state.value = State.Recording(0L, 0f)
            startPolling()
            Result.success(Unit)
        } catch (failure: Exception) {
            runCatching { rec.release() }
            target.delete()
            _state.value = State.Error(failure.message ?: "Recorder failed to start")
            Result.failure(failure)
        }
    }

    fun stop(): Result<RecordedVoiceover> {
        val rec = recorder ?: return Result.failure(IllegalStateException("Not recording"))
        val rel = relativePath ?: return Result.failure(IllegalStateException("Missing path"))
        val file = outputFile ?: return Result.failure(IllegalStateException("Missing file"))
        pollJob?.cancel()
        pollJob = null
        val stopped = runCatching { rec.stop() }
        runCatching { rec.reset() }
        runCatching { rec.release() }
        recorder = null
        outputFile = null
        relativePath = null
        _state.value = State.Idle
        if (stopped.isFailure) {
            file.delete()
            val msg = stopped.exceptionOrNull()?.message ?: "Stop failed"
            _state.value = State.Error(msg)
            return Result.failure(stopped.exceptionOrNull() ?: IllegalStateException(msg))
        }
        val durationMs = probeDurationMs(file)
        if (durationMs <= 0L) {
            file.delete()
            return Result.failure(IllegalStateException("Empty recording"))
        }
        return Result.success(RecordedVoiceover(rel, durationMs))
    }

    fun cancel() {
        pollJob?.cancel()
        pollJob = null
        recorder?.let { rec ->
            runCatching { rec.stop() }
            runCatching { rec.reset() }
            runCatching { rec.release() }
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
        relativePath = null
        _state.value = State.Idle
    }

    fun release() {
        cancel()
        scope.cancel()
    }

    private fun startPolling() {
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val rec = recorder ?: break
                val raw = runCatching { rec.maxAmplitude }.getOrDefault(0)
                val norm = (raw / 32767f).coerceIn(0f, 1f)
                smoothedLevel = smoothedLevel * 0.6f + norm * 0.4f
                val elapsed = SystemClock.elapsedRealtime() - startedAtMs
                _state.value = State.Recording(elapsed, smoothedLevel)
            }
        }
    }

    private fun probeDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: RuntimeException) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        const val SAMPLE_RATE_HZ = 44_100
        const val BIT_RATE_BPS = 128_000
        const val POLL_INTERVAL_MS = 55L
    }
}
