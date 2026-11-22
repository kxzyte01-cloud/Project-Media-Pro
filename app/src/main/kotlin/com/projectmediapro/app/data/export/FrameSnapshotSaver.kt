package com.projectmediapro.app.data.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface FrameSnapshotSaver {
    suspend fun savePngToGallery(sourcePng: File, projectTitle: String): SavedExport
    suspend fun savePngToUri(sourcePng: File, destinationUri: String)
}

fun buildSnapshotFileName(
    title: String,
    timestampMs: Long,
    zone: TimeZone = TimeZone.getDefault(),
): String {
    val format = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    format.timeZone = zone
    return "${sanitizeExportName(title)}-frame-${format.format(Date(timestampMs))}.png"
}

/**
 * Saves 1-tap PNG frame snapshots to Pictures/ProjectMediaPro via MediaStore on API 29+ (zero permissions)
 * or a user-picked SAF URI on API 26-28.
 */
class AndroidFrameSnapshotSaver(
    context: Context,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FrameSnapshotSaver {

    private val appContext = context.applicationContext

    override suspend fun savePngToGallery(sourcePng: File, projectTitle: String): SavedExport =
        withContext(ioDispatcher) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                throw IOException("Saving snapshots directly to Gallery requires Android 10 or newer")
            }
            if (!sourcePng.isFile || sourcePng.length() <= 0L) {
                throw IOException("Snapshot PNG file is missing or empty")
            }
            insertAndCopyPng(sourcePng, buildSnapshotFileName(projectTitle, clockMs()))
        }

    override suspend fun savePngToUri(sourcePng: File, destinationUri: String) {
        withContext(ioDispatcher) {
            if (!sourcePng.isFile || sourcePng.length() <= 0L) {
                throw IOException("Snapshot PNG file is missing or empty")
            }
            try {
                copyPngToUri(sourcePng, Uri.parse(destinationUri))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                throw error
            } catch (error: RuntimeException) {
                throw IOException("Could not save frame snapshot", error)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun insertAndCopyPng(source: File, fileName: String): SavedExport {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ProjectMediaPro")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        return savePendingMediaStoreItem(
            insertItem = { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) },
            copyToItem = { uri -> copyPngToUri(source, uri) },
            publishItem = { uri ->
                val publishValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, publishValues, null, null)
            },
            deleteItem = { uri -> resolver.delete(uri, null, null) },
            locationHint = "Pictures/ProjectMediaPro/$fileName",
        )
    }

    private fun copyPngToUri(source: File, uri: Uri) {
        val outStream = appContext.contentResolver.openOutputStream(uri)
            ?: throw IOException("Could not open destination for snapshot")
        outStream.use { out ->
            FileInputStream(source).use { input ->
                input.copyTo(out)
            }
        }
    }
}
