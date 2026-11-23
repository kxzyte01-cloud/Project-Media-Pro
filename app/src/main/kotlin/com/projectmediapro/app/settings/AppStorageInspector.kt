package com.projectmediapro.app.settings

import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Snapshot of on-device storage used by Project Media Pro.
 */
data class AppStorageSnapshot(
    val projectsMediaBytes: Long,
    val whisperModelsBytes: Long,
    val exportCacheBytes: Long,
) {
    val totalBytes: Long
        get() = (projectsMediaBytes + whisperModelsBytes + exportCacheBytes).coerceAtLeast(0L)
}

/**
 * Pure JVM helper that inspects app-private storage usage and clears temporary export cache files.
 */
class AppStorageInspector(
    private val projectsMediaDirs: List<File>,
    private val whisperModelsDir: File,
    private val exportCacheDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun inspect(): AppStorageSnapshot = withContext(ioDispatcher) {
        val projectsBytes = projectsMediaDirs.sumOf { directoryBytes(it) }
        val whisperBytes = directoryBytes(whisperModelsDir)
        val cacheBytes = directoryBytes(exportCacheDir)
        AppStorageSnapshot(
            projectsMediaBytes = projectsBytes,
            whisperModelsBytes = whisperBytes,
            exportCacheBytes = cacheBytes,
        )
    }

    /**
     * Deletes all temporary files inside [exportCacheDir] while leaving the directory itself intact.
     * Returns the approximate number of bytes freed.
     */
    suspend fun clearExportCache(): Long = withContext(ioDispatcher) {
        val beforeBytes = directoryBytes(exportCacheDir)
        if (!exportCacheDir.isDirectory) return@withContext 0L
        try {
            val children = exportCacheDir.listFiles() ?: return@withContext 0L
            for (child in children) {
                child.deleteRecursively()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            // Best-effort cleanup; return actual delta below.
        }
        val afterBytes = directoryBytes(exportCacheDir)
        (beforeBytes - afterBytes).coerceAtLeast(0L)
    }

    private fun directoryBytes(rootDir: File, depth: Int = 0): Long {
        if (depth > MAX_RECURSION_DEPTH || !rootDir.exists()) return 0L
        return try {
            if (rootDir.isFile) {
                rootDir.length().coerceAtLeast(0L)
            } else if (rootDir.isDirectory) {
                val children = rootDir.listFiles() ?: return 0L
                var total = 0L
                for (child in children) {
                    total += directoryBytes(child, depth + 1)
                }
                total
            } else {
                0L
            }
        } catch (_: SecurityException) {
            0L
        }
    }

    companion object {
        private const val MAX_RECURSION_DEPTH = 8
        private const val KB = 1_024.0
        private const val MB = KB * 1_024.0
        private const val GB = MB * 1_024.0

        fun formatBytes(bytes: Long): String {
            val safe = bytes.coerceAtLeast(0L)
            return when {
                safe < 1_024L -> "$safe B"
                safe < 1_024L * 1_024L -> String.format(Locale.ROOT, "%.1f KB", safe / KB)
                safe < 1_024L * 1_024L * 1_024L -> String.format(Locale.ROOT, "%.1f MB", safe / MB)
                else -> String.format(Locale.ROOT, "%.2f GB", safe / GB)
            }
        }
    }
}
