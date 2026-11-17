package com.projectmediapro.app.data.export

import android.system.ErrnoException
import android.system.OsConstants
import java.io.File

/**
 * Usable bytes on the filesystem of the nearest existing ancestor of [directory].
 */
fun usableBytesNear(directory: File): Long {
    var current: File? = directory.absoluteFile
    while (current != null && !current.exists()) {
        current = current.parentFile
    }
    return try {
        current?.usableSpace ?: 0L
    } catch (_: SecurityException) {
        0L
    }
}

/**
 * Returns true when [error] or any cause in its chain indicates an out-of-storage (`ENOSPC`) condition.
 */
fun isNoSpaceError(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.any { cause ->
        (cause is ErrnoException && cause.errno == OsConstants.ENOSPC) ||
            cause.message?.contains("ENOSPC", ignoreCase = true) == true ||
            cause.message?.contains("No space left", ignoreCase = true) == true
    }
