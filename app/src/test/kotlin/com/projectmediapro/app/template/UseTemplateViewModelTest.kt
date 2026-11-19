package com.projectmediapro.app.template

import com.projectmediapro.app.data.media.AudioImporter
import com.projectmediapro.app.data.media.ImportedAudio
import com.projectmediapro.app.data.media.ImportedOverlay
import com.projectmediapro.app.data.media.ImportedVideo
import com.projectmediapro.app.data.media.OverlayImporter
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImportException
import com.projectmediapro.app.data.media.VideoImportException.Reason
import com.projectmediapro.app.data.media.VideoImporter
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.app.data.template.TemplateProjectCreator
import com.projectmediapro.app.data.template.TemplateRepository
import com.projectmediapro.app.editor.EditorError
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.StarterTemplates
import com.projectmediapro.core.model.Template
import com.projectmediapro.core.model.audioClips
import com.projectmediapro.core.model.videoClips
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class UseTemplateViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var projectMediaStorage: ProjectMediaStorage
    private lateinit var templateMediaStorage: ProjectMediaStorage

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        projectMediaStorage = ProjectMediaStorage(File(tempFolder.root, "media"))
        templateMediaStorage = ProjectMediaStorage(File(tempFolder.root, "template_media"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun useStarterTemplate_createsProjectWithPickedVideoAndAudioAndEmitsOpenProject() = runTest(testDispatcher) {
        val projectRepo = InMemoryProjectRepo()
        val creator = TemplateProjectCreator(
            projectRepository = projectRepo,
            projectMediaStorage = projectMediaStorage,
            templateMediaStorage = templateMediaStorage,
            videoImporter = StubVideoImporter(projectMediaStorage),
            audioImporter = StubAudioImporter(projectMediaStorage),
            overlayImporter = StubOverlayImporter(projectMediaStorage),
            ioDispatcher = testDispatcher,
        )
        val vm = UseTemplateViewModel(
            templateId = StarterTemplates.MUSIC_LYRICS_REEL_ID,
            templateRepository = EmptyTemplateRepo(),
            projectCreator = creator,
        )

        val initial = vm.uiState.value as UseTemplateUiState.Loaded
        assertFalse(initial.hasAtLeastOneVideoReady)

        vm.onEvent(UseTemplateEvent.UpdateProjectTitle("My Lyrics Edit"))
        vm.onEvent(UseTemplateEvent.SlotMediaPicked("slr-v1", "content://media/picker/v1"))
        vm.onEvent(UseTemplateEvent.SlotMediaPicked("slr-a1", "content://media/picker/song"))

        val ready = vm.uiState.value as UseTemplateUiState.Loaded
        assertTrue(ready.hasAtLeastOneVideoReady)

        vm.onEvent(UseTemplateEvent.CreateProject)
        val effect = vm.effects.first()
        assertTrue(effect is UseTemplateEffect.OpenProject)
        val createdId = (effect as UseTemplateEffect.OpenProject).projectId
        val saved = projectRepo.getProject(createdId)
        assertEquals("My Lyrics Edit", saved?.title)
        assertEquals(1, saved?.videoClips()?.size)
        assertEquals(1, saved?.audioClips()?.size)
    }

    @Test
    fun createProject_mapsExactFailedSlotTrackTypeErrorAndCleansUpProject() = runTest(testDispatcher) {
        val projectRepo = InMemoryProjectRepo()
        val videoImporter = StubVideoImporter(projectMediaStorage, failureReason = Reason.NOT_A_VIDEO)
        val creator = TemplateProjectCreator(
            projectRepository = projectRepo,
            projectMediaStorage = projectMediaStorage,
            templateMediaStorage = templateMediaStorage,
            videoImporter = videoImporter,
            audioImporter = StubAudioImporter(projectMediaStorage),
            overlayImporter = StubOverlayImporter(projectMediaStorage),
            ioDispatcher = testDispatcher,
        )
        val vm = UseTemplateViewModel(
            templateId = StarterTemplates.MUSIC_LYRICS_REEL_ID,
            templateRepository = EmptyTemplateRepo(),
            projectCreator = creator,
        )

        vm.onEvent(UseTemplateEvent.SlotMediaPicked("slr-v1", "content://media/picker/bad_video"))
        vm.onEvent(UseTemplateEvent.SlotMediaPicked("slr-a1", "content://media/picker/good_audio"))
        vm.onEvent(UseTemplateEvent.CreateProject)

        val failed = vm.uiState.value as UseTemplateUiState.Loaded
        assertFalse(failed.isCreating)
        assertEquals(EditorError.IMPORT_NOT_A_VIDEO, failed.error)
        assertTrue(projectRepo.listProjects().isEmpty())
    }
}

private class InMemoryProjectRepo : ProjectRepository {
    private val map = mutableMapOf<String, Project>()
    override suspend fun listProjects(): List<Project> = map.values.toList()
    override suspend fun getProject(id: String): Project? = map[id]
    override suspend fun createProject(title: String): Project {
        val p = Project(id = "proj-${map.size + 1}", title = title)
        map[p.id] = p
        return p
    }
    override suspend fun saveProject(project: Project) {
        map[project.id] = project
    }
    override suspend fun deleteProject(id: String) {
        map.remove(id)
    }
}

private class EmptyTemplateRepo : TemplateRepository {
    override suspend fun listTemplates(): List<Template> = emptyList()
    override suspend fun getTemplate(id: String): Template? = null
    override suspend fun createTemplate(name: String): Template = Template(id = "t1", name = name)
    override suspend fun saveTemplate(template: Template) = Unit
    override suspend fun deleteTemplate(id: String) = Unit
}

private class StubVideoImporter(
    private val storage: ProjectMediaStorage,
    private val failureReason: Reason? = null,
) : VideoImporter {
    private var count = 0
    override suspend fun importVideo(projectId: String, sourceUri: String): ImportedVideo {
        failureReason?.let { throw VideoImportException(it) }
        val rel = storage.clipRelativePath(projectId, "v-${++count}", "mp4")
        storage.resolve(rel)?.apply {
            parentFile?.mkdirs()
            writeText("v")
        }
        return ImportedVideo(rel, 5_000L)
    }
}

private class StubAudioImporter(private val storage: ProjectMediaStorage) : AudioImporter {
    private var count = 0
    override suspend fun importAudio(projectId: String, sourceUri: String): ImportedAudio {
        val rel = storage.clipRelativePath(projectId, "a-${++count}", "m4a")
        storage.resolve(rel)?.apply {
            parentFile?.mkdirs()
            writeText("a")
        }
        return ImportedAudio(rel, 10_000L)
    }
}

private class StubOverlayImporter(private val storage: ProjectMediaStorage) : OverlayImporter {
    private var count = 0
    override suspend fun importOverlay(projectId: String, sourceUri: String): ImportedOverlay {
        val rel = storage.clipRelativePath(projectId, "o-${++count}", "png")
        storage.resolve(rel)?.apply {
            parentFile?.mkdirs()
            writeText("o")
        }
        return ImportedOverlay(rel, 200, 200)
    }
}
