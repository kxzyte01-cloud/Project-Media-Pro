package com.projectmediapro.app.data.export

import java.io.File

/**
 * Joins the clips of an [ExportPlan] into one MP4 file.
 */
interface VideoExporter {
    /**
     * Writes the joined video to [outputFile] and reports progress from 0 to 100.
     * Cancelling the coroutine cancels the export and removes the partial file.
     */
    suspend fun export(plan: ExportPlan, outputFile: File, onProgress: (Int) -> Unit)
}

/**
 * Categorized failure thrown by [VideoExporter.export].
 */
class VideoExportException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception(reason.name, cause) {
    enum class Reason {
        UNSUPPORTED,
        NO_SPACE,
        FAILED,
    }
}
