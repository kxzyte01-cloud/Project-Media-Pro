package com.projectmediapro.app.data.project

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.ProjectSerializationResult
import com.projectmediapro.core.model.ProjectSerializer

interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>
    suspend fun createProject(title: String): Project
    suspend fun getProject(id: String): Project?
    suspend fun saveProject(project: Project)
    suspend fun deleteProject(id: String)
}

/** Stores each project as an atomically replaced JSON file in app-private storage. */
class FileProjectRepository(
    private val directory: File,
) : ProjectRepository {
    private val mutex = Mutex()
    private val projects = MutableStateFlow<List<Project>>(emptyList())

    override fun observeProjects(): Flow<List<Project>> = flow {
        refresh()
        emitAll(projects)
    }

    override suspend fun createProject(title: String): Project {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "Project title must not be empty." }
        require(cleanTitle.length <= MAX_TITLE_LENGTH) { "Project title is too long." }
        val now = System.currentTimeMillis()
        val project = Project(
            id = UUID.randomUUID().toString(),
            title = cleanTitle,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        saveProject(project)
        return project
    }

    override suspend fun getProject(id: String): Project? = withContext(Dispatchers.IO) {
        val file = projectFile(id)
        if (!file.isFile) return@withContext null
        decode(file.readText())
    }

    override suspend fun saveProject(project: Project) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                requireValidId(project.id)
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IOException("Could not create the projects directory.")
                }
                val target = projectFile(project.id)
                val temporary = File(directory, "${project.id}.${UUID.randomUUID()}.tmp")
                try {
                    FileOutputStream(temporary).use { output ->
                        output.write(ProjectSerializer.encode(project).toByteArray(Charsets.UTF_8))
                        output.fd.sync()
                    }
                    try {
                        Files.move(
                            temporary.toPath(),
                            target.toPath(),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(
                            temporary.toPath(),
                            target.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                    refreshLocked()
                } finally {
                    if (temporary.exists()) temporary.delete()
                }
            }
        }
    }

    override suspend fun deleteProject(id: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val file = projectFile(id)
                if (file.exists() && !file.delete()) {
                    throw IOException("Could not delete the project.")
                }
                refreshLocked()
            }
        }
    }

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock { refreshLocked() }
    }

    private fun refreshLocked() {
        val saved = directory.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.name.endsWith(FILE_SUFFIX) }
            .mapNotNull { file -> runCatching { decode(file.readText()) }.getOrNull() }
            .sortedByDescending(Project::updatedAtEpochMs)
            .toList()
        projects.value = saved
    }

    private fun projectFile(id: String): File {
        requireValidId(id)
        return File(directory, "$id$FILE_SUFFIX")
    }

    private fun decode(raw: String): Project? {
        return (ProjectSerializer.decode(raw) as? ProjectSerializationResult.Success)?.project
    }

    private fun requireValidId(id: String) {
        require(ID_PATTERN.matches(id)) { "Invalid project identifier." }
    }

    private companion object {
        const val FILE_SUFFIX = ".pmp.json"
        const val MAX_TITLE_LENGTH = 80
        val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,80}")
    }
}
