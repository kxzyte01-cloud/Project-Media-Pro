package com.projectmediapro.app.data.project

import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.ProjectSerializer
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/**
 * File-backed [ProjectRepository] storing one `<id>.pmp.json` file per project in [directory].
 */
class FileProjectRepository(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val mediaStorage: ProjectMediaStorage? = null,
) : ProjectRepository {

    private val mutex = Mutex()

    override suspend fun listProjects(): List<Project> = runOnIo {
        mutex.withLock {
            ensureDirectory()
            val files = directory.listFiles()
                ?: throw IOException("Could not list project directory: ${directory.absolutePath}")
            val projects = mutableListOf<Project>()

            for (file in files) {
                if (!file.isFile || !file.name.endsWith(FILE_SUFFIX)) continue
                val fileId = file.name.removeSuffix(FILE_SUFFIX)
                if (!isValidId(fileId)) continue

                val project = readProjectSafely(file) ?: continue
                if (project.id != fileId) continue
                if (!isSupportedSchema(project.schemaVersion)) continue
                projects.add(project)
            }

            projects.sortedWith(
                compareByDescending<Project> { it.updatedAt }.thenBy { it.id },
            )
        }
    }

    override suspend fun getProject(id: String): Project? {
        if (!isValidId(id)) return null

        return runOnIo {
            mutex.withLock {
                if (!directory.exists()) return@withLock null
                ensureDirectory()

                val file = projectFile(id)
                if (!file.isFile) return@withLock null

                val project = readProjectSafely(file) ?: return@withLock null
                if (project.id != id || !isSupportedSchema(project.schemaVersion)) {
                    return@withLock null
                }
                project
            }
        }
    }

    override suspend fun createProject(title: String): Project = runOnIo {
        mutex.withLock {
            ensureDirectory()

            val projectId = idProvider()
            val trackId = idProvider()
            if (!isValidId(projectId) || !isValidId(trackId) || projectId == trackId) {
                throw IOException("Generated project or track ID is invalid.")
            }
            if (projectFile(projectId).exists()) {
                throw IOException("Generated project ID already exists: $projectId")
            }

            val now = clock()
            val project = Project(
                id = projectId,
                title = normalizeTitle(title),
                durationMs = 0L,
                tracks = listOf(
                    Track(
                        id = trackId,
                        type = TrackType.VIDEO,
                        clips = emptyList(),
                    ),
                ),
                createdAt = now,
                updatedAt = now,
                schemaVersion = Project.CURRENT_SCHEMA_VERSION,
            )
            writeProjectAtomically(project)
            project
        }
    }

    override suspend fun saveProject(project: Project) {
        if (!isValidId(project.id)) {
            throw IOException("Invalid project id: ${project.id}")
        }
        if (!isSupportedSchema(project.schemaVersion)) {
            throw IOException("Unsupported project schema version: ${project.schemaVersion}")
        }

        runOnIo {
            mutex.withLock {
                ensureDirectory()
                val updated = project.copy(
                    title = normalizeTitle(project.title),
                    updatedAt = clock(),
                )
                writeProjectAtomically(updated)
            }
        }
    }

    override suspend fun deleteProject(id: String) {
        if (!isValidId(id)) {
            throw IOException("Invalid project id: $id")
        }

        runOnIo {
            mutex.withLock {
                ensureDirectory()
                val target = projectFile(id)
                if (target.exists() && !target.delete()) {
                    throw IOException("Could not delete project file: ${target.name}")
                }
            }
            try {
                mediaStorage?.deleteProjectMedia(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Best-effort cleanup: media deletion failure must not make deleteProject fail.
            }
        }
    }

    /** Run filesystem work on the configured dispatcher and normalize permission failures. */
    private suspend fun <T> runOnIo(block: suspend () -> T): T = withContext(ioDispatcher) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (denied: SecurityException) {
            throw IOException("Access to project storage was denied.", denied)
        }
    }

    private fun ensureDirectory() {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create projects directory: ${directory.absolutePath}")
        }
        if (!directory.isDirectory) {
            throw IOException("Projects path is not a directory: ${directory.absolutePath}")
        }
    }

    private fun projectFile(id: String): File = File(directory, "$id$FILE_SUFFIX")

    private fun readProjectSafely(file: File): Project? {
        return try {
            val rawJson = file.readText(Charsets.UTF_8)
            ProjectSerializer.deserialize(rawJson)
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun writeProjectAtomically(project: Project) {
        val targetFile = projectFile(project.id)
        val tempFile = File(directory, "$FILE_SUFFIX.${project.id}.${UUID.randomUUID()}.tmp")

        try {
            val bytes = ProjectSerializer.serialize(project).toByteArray(Charsets.UTF_8)
            FileOutputStream(tempFile).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw IOException("Failed to write project atomically", failure)
        } finally {
            try {
                tempFile.delete()
            } catch (_: SecurityException) {
                // Do not mask the original save result or failure with a cleanup failure.
            }
        }
    }

    companion object {
        const val FILE_SUFFIX = ".pmp.json"
        private val ID_REGEX = Regex("^[A-Za-z0-9-]{1,64}$")

        fun isValidId(id: String): Boolean = ID_REGEX.matches(id)

        fun isSupportedSchema(version: Int): Boolean = version == Project.CURRENT_SCHEMA_VERSION

        fun normalizeTitle(rawTitle: String): String =
            rawTitle.trim().take(ProjectRepository.MAX_TITLE_LENGTH)
    }
}
