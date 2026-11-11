package com.projectmediapro.app.data.project

import com.projectmediapro.core.model.Project

/**
 * Persistence contract for creating, listing, loading, saving, and deleting [Project] records.
 */
interface ProjectRepository {
    companion object {
        const val MAX_TITLE_LENGTH = 60
    }

    suspend fun listProjects(): List<Project>
    suspend fun getProject(id: String): Project?
    suspend fun createProject(title: String): Project
    suspend fun saveProject(project: Project)
    suspend fun deleteProject(id: String)
}
