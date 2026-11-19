package com.projectmediapro.app.data.template

import com.projectmediapro.core.model.Template

interface TemplateRepository {
    suspend fun listTemplates(): List<Template>
    suspend fun getTemplate(id: String): Template?
    suspend fun createTemplate(name: String): Template
    suspend fun saveTemplate(template: Template)
    suspend fun deleteTemplate(id: String)

    companion object {
        const val MAX_NAME_LENGTH = 80
    }
}
