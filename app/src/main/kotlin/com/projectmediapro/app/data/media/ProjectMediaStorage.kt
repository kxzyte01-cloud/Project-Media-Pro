package com.projectmediapro.app.data.media

import java.io.File
import java.io.IOException

/**
 * Pure JVM helper that manages project media files inside [rootDirectory] and prevents path traversal.
 */
class ProjectMediaStorage(
    private val rootDirectory: File,
) {

    /**
     * Returns a relative path "<projectId>/<clipId>.<ext>" inside [rootDirectory].
     * Throws [IllegalArgumentException] when [projectId] or [clipId] does not match [A-Za-z0-9-]{1,64}.
     * Uses "mp4" when [extension] does not match [a-z0-9]{1,5}.
     */
    fun clipRelativePath(
        projectId: String,
        clipId: String,
        extension: String,
    ): String {
        require(ID_REGEX.matches(projectId)) { "Invalid projectId: $projectId" }
        require(ID_REGEX.matches(clipId)) { "Invalid clipId: $clipId" }
        val resolvedExtension = if (EXT_REGEX.matches(extension)) extension else DEFAULT_EXTENSION
        return "$projectId/$clipId.$resolvedExtension"
    }

    /**
     * Resolves [relativePath] to a canonical [File] strictly inside [rootDirectory], or returns null
     * if [relativePath] is empty, absolute, contains ".." or backslashes, or escapes [rootDirectory].
     */
    fun resolve(relativePath: String): File? {
        if (relativePath.isEmpty() ||
            relativePath.startsWith("/") ||
            relativePath.contains('\\') ||
            relativePath.contains("..")
        ) {
            return null
        }
        if (File(relativePath).isAbsolute) return null

        val segments = relativePath.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) {
            return null
        }

        return try {
            val canonicalRoot = rootDirectory.canonicalFile
            val canonicalTarget = File(canonicalRoot, relativePath).canonicalFile
            val rootPath = canonicalRoot.path
            val rootPrefix = if (rootPath.endsWith(File.separator)) {
                rootPath
            } else {
                "$rootPath${File.separator}"
            }
            if (canonicalTarget.path.startsWith(rootPrefix)) canonicalTarget else null
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * Deletes the clip file at [relativePath] inside [rootDirectory]. Never throws for missing files.
     */
    fun deleteClipFile(relativePath: String) {
        val file = resolve(relativePath) ?: return
        try {
            if (file.isFile) {
                file.delete()
            }
        } catch (_: SecurityException) {
            // Best-effort cleanup.
        }
    }

    /**
     * Recursively deletes the project media folder for [projectId] inside [rootDirectory].
     * Never throws for missing folders or invalid IDs.
     */
    fun deleteProjectMedia(projectId: String) {
        if (!ID_REGEX.matches(projectId)) return
        val dir = resolve(projectId) ?: return
        try {
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (_: SecurityException) {
            // Best-effort cleanup.
        }
    }

    /**
     * Returns usable bytes on the filesystem of the nearest existing ancestor of [rootDirectory].
     */
    fun availableBytes(): Long {
        var current: File? = rootDirectory.absoluteFile
        while (current != null && !current.exists()) {
            current = current.parentFile
        }
        return try {
            current?.usableSpace ?: 0L
        } catch (_: SecurityException) {
            0L
        }
    }

    companion object {
        const val DEFAULT_EXTENSION = "mp4"
        private val ID_REGEX = Regex("^[A-Za-z0-9-]{1,64}$")
        private val EXT_REGEX = Regex("^[a-z0-9]{1,5}$")
    }
}
