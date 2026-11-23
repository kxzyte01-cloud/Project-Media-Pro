package com.projectmediapro.app.di

import android.content.Context
import com.projectmediapro.app.BuildConfig
import com.projectmediapro.app.data.export.AndroidExportSaver
import com.projectmediapro.app.data.export.AndroidFrameSnapshotSaver
import com.projectmediapro.app.data.export.FrameSnapshotSaver
import com.projectmediapro.app.data.export.ExportSaver
import com.projectmediapro.app.data.export.Media3VideoExporter
import com.projectmediapro.app.data.export.VideoExporter
import com.projectmediapro.app.data.export.usableBytesNear
import com.projectmediapro.app.data.media.AndroidAudioImporter
import com.projectmediapro.app.data.media.AndroidFreezeFrameExtractor
import com.projectmediapro.app.data.media.AndroidVideoAudioExtractor
import com.projectmediapro.app.data.media.AndroidOverlayImporter
import com.projectmediapro.app.data.media.AndroidVideoImporter
import com.projectmediapro.app.data.media.AudioImporter
import com.projectmediapro.app.data.media.FreezeFrameExtractor
import com.projectmediapro.app.data.media.VideoAudioExtractor
import com.projectmediapro.app.data.media.OverlayImporter
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImporter
import com.projectmediapro.app.data.project.FileProjectRepository
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.app.data.template.FileTemplateRepository
import com.projectmediapro.app.data.template.TemplateProjectCreator
import com.projectmediapro.app.data.template.TemplateRepository
import com.projectmediapro.app.settings.AppStorageInspector
import com.projectmediapro.app.settings.DataStoreThemePreferencesStore
import com.projectmediapro.app.settings.PrivacyPolicyDataSource
import com.projectmediapro.app.settings.RawResourcePrivacyPolicyDataSource
import com.projectmediapro.app.settings.ThemePreferencesStore
import com.projectmediapro.feature.captions.CaptionRepository
import com.projectmediapro.feature.captions.FileCaptionRepository
import com.projectmediapro.feature.captions.MediaCodecPcmAudioExtractor
import com.projectmediapro.feature.captions.OfflineHeuristicTranscriptionEngine
import com.projectmediapro.feature.captions.PcmAudioExtractor
import com.projectmediapro.feature.captions.TranscriptionEngine
import com.projectmediapro.feature.captions.WhisperModelManager
import com.projectmediapro.feature.captions.WhisperTranscriptionEngine
import java.io.File

class AppContainer(appContext: Context) {
    val themePreferencesStore: ThemePreferencesStore = DataStoreThemePreferencesStore(appContext)
    val privacyPolicyDataSource: PrivacyPolicyDataSource = RawResourcePrivacyPolicyDataSource(appContext)
    val mediaStorage: ProjectMediaStorage = ProjectMediaStorage(
        rootDirectory = File(appContext.filesDir, "media"),
    )
    val videoImporter: VideoImporter = AndroidVideoImporter(
        context = appContext,
        storage = mediaStorage,
    )
    val audioImporter: AudioImporter = AndroidAudioImporter(
        context = appContext,
        storage = mediaStorage,
    )
    val overlayImporter: OverlayImporter = AndroidOverlayImporter(
        context = appContext,
        storage = mediaStorage,
    )
    val captionRepository: CaptionRepository = FileCaptionRepository(
        directory = File(appContext.filesDir, "captions"),
    )
    val projectRepository: ProjectRepository = FileProjectRepository(
        directory = File(appContext.filesDir, "projects"),
        mediaStorage = mediaStorage,
        onProjectDeleted = { projectId -> captionRepository.deleteCaptions(projectId) },
        onProjectDuplicated = { sourceProjectId, targetProjectId ->
            captionRepository.loadCaptions(sourceProjectId)?.let { document ->
                captionRepository.saveCaptions(document.copy(projectId = targetProjectId))
            }
        },
    )
    val templateMediaStorage: ProjectMediaStorage = ProjectMediaStorage(
        rootDirectory = File(appContext.filesDir, "template_media"),
    )
    val templateVideoImporter: VideoImporter = AndroidVideoImporter(
        context = appContext,
        storage = templateMediaStorage,
    )
    val templateAudioImporter: AudioImporter = AndroidAudioImporter(
        context = appContext,
        storage = templateMediaStorage,
    )
    val templateOverlayImporter: OverlayImporter = AndroidOverlayImporter(
        context = appContext,
        storage = templateMediaStorage,
    )
    val templateRepository: TemplateRepository = FileTemplateRepository(
        directory = File(appContext.filesDir, "templates"),
        mediaStorage = templateMediaStorage,
    )
    val templateProjectCreator: TemplateProjectCreator = TemplateProjectCreator(
        projectRepository = projectRepository,
        projectMediaStorage = mediaStorage,
        templateMediaStorage = templateMediaStorage,
        videoImporter = videoImporter,
        audioImporter = audioImporter,
        overlayImporter = overlayImporter,
    )
    val videoExporter: VideoExporter = Media3VideoExporter(context = appContext)
    val freezeFrameExtractor: FreezeFrameExtractor = AndroidFreezeFrameExtractor()
    val videoAudioExtractor: VideoAudioExtractor = AndroidVideoAudioExtractor()
    val exportSaver: ExportSaver = AndroidExportSaver(context = appContext)
    val frameSnapshotSaver: FrameSnapshotSaver = AndroidFrameSnapshotSaver(context = appContext)
    val exportTempDirectory: File = File(appContext.cacheDir, "exports")
    val cacheSpaceProvider: () -> Long = { usableBytesNear(exportTempDirectory) }
    val pcmAudioExtractor: PcmAudioExtractor = MediaCodecPcmAudioExtractor()
    val whisperModelManager: WhisperModelManager = WhisperModelManager(
        modelsDirectory = File(appContext.filesDir, "whisper_models"),
    )
    val transcriptionEngine: TranscriptionEngine = WhisperTranscriptionEngine(
        modelManager = whisperModelManager,
        fallbackEngine = OfflineHeuristicTranscriptionEngine(),
    )
    val storageInspector: AppStorageInspector = AppStorageInspector(
        projectsMediaDirs = listOf(
            File(appContext.filesDir, "projects"),
            File(appContext.filesDir, "media"),
            File(appContext.filesDir, "captions"),
            File(appContext.filesDir, "templates"),
            File(appContext.filesDir, "template_media"),
        ),
        whisperModelsDir = File(appContext.filesDir, "whisper_models"),
        exportCacheDir = exportTempDirectory,
    )
    val appVersionName: String = BuildConfig.VERSION_NAME
}
