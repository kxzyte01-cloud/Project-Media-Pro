package com.projectmediapro.app.data.export

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.projectmediapro.app.R

/**
 * Offline 1-tap Android ACTION_SEND Share Sheet launcher for exported MP4 videos.
 *
 * Hardened for API 26-36: validates content:// scheme, attaches ClipData + read grant
 * flag directly on the chooser Intent for reliable OEM/API 26-28 URI permission forwarding.
 */
object ShareExportHelper {

    fun shareVideo(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        if (!"content".equals(uri.scheme, ignoreCase = true)) return false
        val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?.takeIf { it.startsWith("video/") }
            ?: "video/mp4"
        val chooserTitle = context.getString(R.string.editor_export_share_chooser_title)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(chooserTitle, uri)
        }
        return try {
            context.startActivity(chooser)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
