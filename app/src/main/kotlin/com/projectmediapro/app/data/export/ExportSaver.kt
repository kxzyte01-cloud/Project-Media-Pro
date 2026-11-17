package com.projectmediapro.app.data.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Where a finished export ended up, as a short human-readable hint.
 */
data class SavedExport(val locationHint: String)

/**
 * Copies a finished export file to a place the user can reach.
 */
interface ExportSaver {
    suspend fun saveToGallery(source: File, displayName: String): SavedExport
    suspend fun saveToUri(source: File, destinationUri: String)
}

/**
 * Keeps letters, digits, space, "-" and "_", at most 40 characters; falls back to "Project".
 */
fun sanitizeExportName(rawName: String): String {
    val filtered = buildString(rawName.length) {
        for (ch in rawName) {
            if (ch.isLetterOrDigit() || ch == ' ' || ch == '-' || ch == '_') {
                append(ch)
            }
        }
    }.trim().take(40).trim()
    return filtered.ifEmpty { "Project" }
}

/**
 * Builds "<clean title>-<yyyyMMdd-HHmmss>.mp4" for [timestampMs] in [zone].
 */
fun buildExportFileName(
    title: String,
    timestampMs: Long,
    zone: TimeZone = TimeZone.getDefault(),
): String {
    val format = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    format.timeZone = zone
    return "${sanitizeExportName(title)}-${format.format(Date(timestampMs))}.mp4"
}

/**
 * Pure helper that inserts a pending MediaStore item, copies the exported video into it, verifies
 * that publishing (`IS_PENDING = 0`) updated at least one row, and deletes the pending item on any
 * copy, publish, or cancellation failure.
 */
internal suspend fun <T : Any> savePendingMediaStoreItem(
    insertItem: () -> T?,
    copyToItem: suspend (T) -> Unit,
    publishItem: (T) -> Int,
    deleteItem: (T) -> Unit,
    locationHint: String,
): SavedExport {
    val item = insertItem() ?: throw IOException("Could not create the gallery entry")
    try {
        copyToItem(item)
        val updatedRows = publishItem(item)
        if (updatedRows <= 0) {
            throw IOException("MediaStore failed to publish the exported video (updatedRows=$updatedRows)")
        }
        return SavedExport(locationHint)
    } catch (cancelled: CancellationException) {
        runCatching { deleteItem(item) }
        throw cancelled
    } catch (failure: IOException) {
        runCatching { deleteItem(item) }
        throw failure
    } catch (failure: Exception) {
        runCatching { deleteItem(item) }
        throw IOException("Could not copy the export to the gallery", failure)
    }
}

/**
 * Android [ExportSaver]: MediaStore (Movies/ProjectMediaPro) on API 29+, or a user-chosen document URI.
 */
class AndroidExportSaver(
    context: Context,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ExportSaver {

    private val appContext = context.applicationContext

    override suspend fun saveToGallery(source: File, displayName: String): SavedExport =
        withContext(ioDispatcher) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                throw IOException("Saving to the gallery needs Android 10 or newer")
            }
            requireValidSourceFile(source)
            insertAndCopy(source, buildExportFileName(displayName, clockMs()))
        }

    override suspend fun saveToUri(source: File, destinationUri: String) {
        withContext(ioDispatcher) {
            requireValidSourceFile(source)
            try {
                copyFileToUri(source, Uri.parse(destinationUri))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                throw IOException("Could not copy the export to the chosen file", failure)
            }
        }
    }

    private fun requireValidSourceFile(source: File) {
        if (!source.isFile || source.length() <= 0L) {
            throw IOException("The exported file does not exist or is empty")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun insertAndCopy(source: File, fileName: String): SavedExport {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, GALLERY_RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val published = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        return savePendingMediaStoreItem(
            insertItem = { resolver.insert(collection, values) },
            copyToItem = { itemUri -> copyFileToUri(source, itemUri) },
            publishItem = { itemUri -> resolver.update(itemUri, published, null, null) },
            deleteItem = { itemUri -> resolver.delete(itemUri, null, null) },
            locationHint = "$GALLERY_RELATIVE_PATH/$fileName",
        )
    }

    private suspend fun copyFileToUri(source: File, targetUri: Uri) {
        val expectedBytes = source.length()
        val output = appContext.contentResolver.openOutputStream(targetUri)
            ?: throw IOException("Could not open the destination")
        var bytesWritten = 0L
        output.use { out ->
            FileInputStream(source).use { input ->
                val buffer = ByteArray(BUFFER_SIZE_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    bytesWritten += read
                }
                out.flush()
                runCatching { (out as? FileOutputStream)?.fd?.sync() }
            }
        }
        if (bytesWritten != expectedBytes) {
            throw IOException("Incomplete export copy ($bytesWritten of $expectedBytes bytes)")
        }
    }

    private companion object {
        const val GALLERY_RELATIVE_PATH = "Movies/ProjectMediaPro"
        const val BUFFER_SIZE_BYTES = 64 * 1024
    }
}
