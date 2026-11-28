package com.projectmediapro.app.data.export

import com.projectmediapro.core.model.CanvasAspect
import com.projectmediapro.core.model.ExportBitrate
import com.projectmediapro.core.model.ExportFrameRate
import com.projectmediapro.core.model.ExportQuality
import com.projectmediapro.core.model.Project
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

enum class BatchExportStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
}

data class BatchExportJob(
    val id: String,
    val label: String,
    val project: Project,
    val aspect: CanvasAspect,
    val quality: ExportQuality = ExportQuality.FULL_HD_1080P,
    val frameRate: ExportFrameRate = ExportFrameRate.FPS_30,
    val bitrate: ExportBitrate = ExportBitrate.STANDARD,
    val status: BatchExportStatus = BatchExportStatus.PENDING,
    val progressPercent: Int = 0,
    val savedUri: String? = null,
    val errorMessage: String? = null,
)

data class BatchExportQueueState(
    val jobs: List<BatchExportJob> = emptyList(),
    val isRunning: Boolean = false,
) {
    val completedCount: Int get() = jobs.count { it.status == BatchExportStatus.DONE }
    val failedCount: Int get() = jobs.count { it.status == BatchExportStatus.FAILED }
}

/**
 * Offline sequential multi-aspect / multi-preset batch export queue runner.
 * Mutex-guarded against re-entrant runs, atomic state updates, per-job error isolation,
 * inter-job thermal cool-down, and stale temp-file cleanup.
 */
class BatchExportQueueRunner(
    private val exporter: VideoExporter,
    private val saver: ExportSaver,
    private val planBuilder: (Project, ExportQuality, ExportFrameRate, ExportBitrate) -> ExportPlan?,
    private val tempDirectory: File,
    private val interJobDelayMs: Long = 750L,
) {
    private val _state = MutableStateFlow(BatchExportQueueState())
    val state: StateFlow<BatchExportQueueState> = _state.asStateFlow()
    private val runMutex = Mutex()

    fun enqueueMultiAspect(
        baseProject: Project,
        aspects: List<CanvasAspect>,
        quality: ExportQuality = baseProject.exportQuality,
        frameRate: ExportFrameRate = baseProject.exportFrameRate,
        bitrate: ExportBitrate = baseProject.exportBitrate,
    ): List<BatchExportJob> {
        if (_state.value.isRunning || runMutex.isLocked) return _state.value.jobs
        val jobs = aspects.distinct().mapIndexed { index, aspect ->
            val variantTitle = "${baseProject.title.ifBlank { "Project" }}_${aspect.name}"
            BatchExportJob(
                id = "batch_${index}_${aspect.name}",
                label = "${aspect.name.replace('_', ':')} (${quality.name})",
                project = baseProject.copy(
                    title = variantTitle,
                    canvasAspect = aspect,
                ),
                aspect = aspect,
                quality = quality,
                frameRate = frameRate,
                bitrate = bitrate,
            )
        }
        _state.value = BatchExportQueueState(jobs = jobs, isRunning = false)
        return jobs
    }

    suspend fun runAll() {
        if (!runMutex.tryLock()) return
        try {
            val initial = _state.value.jobs
            if (initial.isEmpty()) return
            _state.update { it.copy(isRunning = true) }
            tempDirectory.mkdirs()
            sweepStaleTempFiles()
            var ranAnyJob = false
            for (job in initial) {
                currentCoroutineContext().ensureActive()
                if (job.status == BatchExportStatus.DONE) continue
                updateJob(job.id) { it.copy(status = BatchExportStatus.RUNNING, progressPercent = 0, errorMessage = null) }
                val tempFile = File(tempDirectory, "${job.id}.mp4")
                try {
                    if (ranAnyJob && interJobDelayMs > 0L) delay(interJobDelayMs)
                    ranAnyJob = true
                    val plan = planBuilder(job.project, job.quality, job.frameRate, job.bitrate)
                    if (plan == null) {
                        updateJob(job.id) { it.copy(status = BatchExportStatus.FAILED, errorMessage = "No exportable clips") }
                        continue
                    }
                    if (tempFile.exists()) tempFile.delete()
                    exporter.export(plan, tempFile) { percent ->
                        updateJob(job.id) { it.copy(progressPercent = percent.coerceIn(0, 100)) }
                    }
                    val saved = saver.saveToGallery(tempFile, job.project.title)
                    updateJob(job.id) {
                        it.copy(
                            status = BatchExportStatus.DONE,
                            progressPercent = 100,
                            savedUri = saved.contentUri,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    updateJob(job.id) { it.copy(status = BatchExportStatus.PENDING, progressPercent = 0) }
                    throw cancelled
                } catch (failure: Exception) {
                    updateJob(job.id) {
                        it.copy(
                            status = BatchExportStatus.FAILED,
                            errorMessage = failure.message ?: "Export failed",
                        )
                    }
                } finally {
                    tempFile.delete()
                }
            }
        } finally {
            _state.update { it.copy(isRunning = false) }
            runMutex.unlock()
        }
    }

    fun reset() {
        if (_state.value.isRunning || runMutex.isLocked) return
        _state.value = BatchExportQueueState()
    }

    private fun sweepStaleTempFiles() {
        runCatching {
            tempDirectory.listFiles { file ->
                file.isFile && file.name.startsWith("batch_") && file.name.endsWith(".mp4")
            }?.forEach { it.delete() }
        }
    }

    private fun updateJob(jobId: String, transform: (BatchExportJob) -> BatchExportJob) {
        _state.update { current ->
            current.copy(
                jobs = current.jobs.map { if (it.id == jobId) transform(it) else it },
            )
        }
    }
}
