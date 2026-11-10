package com.projectmediapro.app.di

import android.content.Context
import com.projectmediapro.app.BuildConfig
import com.projectmediapro.app.data.media.AndroidVideoImportDataSource
import com.projectmediapro.app.data.media.VideoImportDataSource
import com.projectmediapro.app.data.project.FileProjectRepository
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.app.settings.DataStoreThemePreferencesStore
import com.projectmediapro.app.settings.PrivacyPolicyDataSource
import com.projectmediapro.app.settings.RawResourcePrivacyPolicyDataSource
import com.projectmediapro.app.settings.ThemePreferencesStore
import java.io.File

/** Application-scoped manual dependency container. */
class AppContainer(appContext: Context) {
    val themePreferencesStore: ThemePreferencesStore = DataStoreThemePreferencesStore(appContext)
    val privacyPolicyDataSource: PrivacyPolicyDataSource =
        RawResourcePrivacyPolicyDataSource(appContext)
    val projectRepository: ProjectRepository =
        FileProjectRepository(File(appContext.filesDir, "projects"))
    val videoImportDataSource: VideoImportDataSource =
        AndroidVideoImportDataSource(appContext, File(appContext.filesDir, "media"))
    val appVersionName: String = BuildConfig.VERSION_NAME
}
