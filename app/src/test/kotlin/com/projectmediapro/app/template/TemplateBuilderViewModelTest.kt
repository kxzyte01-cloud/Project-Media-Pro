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
import com.projectmediapro.app.data.template.TemplateRepository
import com.projectmediapro.app.editor.EditorError
import com.projectmediapro.core.model.Template
import com.projectmediapro.core.model.TemplateBuilder
import com.projectmediapro.core.model.audioSlots
import com.projectmediapro.core.model.introSlot
import com.projectmediapro.core.model.mainVideoSlots
import com.projectmediapro.core.model.overlaySlots
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateBuilderViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mediaStorage: ProjectMediaStorage

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mediaStorage = ProjectMediaStorage(File(tempFolder.root, "template_media"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun builder_importsIntroOverlayAudioAndEditsSlotsCleanly() = runTest(testDispatcher) {
        val initial = Template(
            id = "tpl-1",
            name = "Channel Format",
            slots = TemplateBuilder.createDefaultSlots("in", "v1", "out"),
        )
        val repo = FakeTemplateRepository(listOf(initial))
        val overlayImporter = FakeTemplateOverlayImporter(mediaStorage)
        val vm = TemplateBuilderViewModel(
            templateId = "tpl-1",
            repository = repo,
            mediaStorage = mediaStorage,
            videoImporter = FakeTemplateVideoImporter(mediaStorage),
            audioImporter = FakeTemplateAudioImporter(mediaStorage),
            overlayImporter = overlayImporter,
        )

        val loaded = vm.uiState.value as TemplateBuilderUiState.Loaded
        assertEquals("in", loaded.selectedSlotId)

        vm.onEvent(TemplateBuilderEvent.SlotMediaPicked("in", "content://media/picker/intro"))
        val afterIntro = vm.uiState.value as TemplateBuilderUiState.Loaded
        assertNull(afterIntro.error)
        assertEquals(4_000L, afterIntro.template.introSlot()?.durationMs)

        overlayImporter.failureReason = Reason.NOT_A_VIDEO
        vm.onEvent(TemplateBuilderEvent.OverlayPicked("content://media/picker/bad"))
        assertEquals(EditorError.IMPORT_NOT_IMAGE, (vm.uiState.value as TemplateBuilderUiState.Loaded).error)

        overlayImporter.failureReason = null
        vm.onEvent(TemplateBuilderEvent.OverlayPicked("content://media/picker/logo"))
        val afterOverlay = vm.uiState.value as TemplateBuilderUiState.Loaded
        assertEquals(1, afterOverlay.template.overlaySlots().size)
        vm.onEvent(TemplateBuilderEvent.OpenOverlayDuration)
        assertTrue((vm.uiState.value as TemplateBuilderUiState.Loaded).isEditingOverlayDuration)
        vm.onEvent(TemplateBuilderEvent.ApplyOverlayDuration(8_000L))
        val afterDuration = vm.uiState.value as TemplateBuilderUiState.Loaded
        assertFalse(afterDuration.isEditingOverlayDuration)
        assertEquals(8_000L, afterDuration.template.overlaySlots()[0].durationMs)

        vm.onEvent(TemplateBuilderEvent.AddVideoSlot)
        assertEquals(2, (vm.uiState.value as TemplateBuilderUiState.Loaded).template.mainVideoSlots().size)

        vm.onEvent(TemplateBuilderEvent.AddAudioSlot)
        vm.onEvent(TemplateBuilderEvent.AddAudioSlot)
        val audios = (vm.uiState.value as TemplateBuilderUiState.Loaded).template.audioSlots()
        assertEquals(2, audios.size)
        vm.onEvent(TemplateBuilderEvent.MoveAudioSlot(audios[1].id, -1))
        val reordered = (vm.uiState.value as TemplateBuilderUiState.Loaded).template.audioSlots()
        assertEquals(audios[1].id, reordered[0].id)
    }
}

private class FakeTemplateRepository(
    initial: List<Template>,
) : TemplateRepository {
    private val map = initial.associateBy { it.id }.toMutableMap()
    override suspend fun listTemplates(): List<Template> = map.values.toList()
    override suspend fun getTemplate(id: String): Template? = map[id]
    override suspend fun createTemplate(name: String): Template {
        val created = Template(id = "new-tpl", name = name)
        map[created.id] = created
        return created
    }
    override suspend fun saveTemplate(template: Template) {
        map[template.id] = template
    }
    override suspend fun deleteTemplate(id: String) {
        map.remove(id)
    }
}

private class FakeTemplateVideoImporter(
    private val storage: ProjectMediaStorage,
) : VideoImporter {
    private var count = 0
    override suspend fun importVideo(projectId: String, sourceUri: String): ImportedVideo {
        val rel = storage.clipRelativePath(projectId, "v-${++count}", "mp4")
        val f = storage.resolve(rel)
        f?.parentFile?.mkdirs()
        f?.writeText("video")
        return ImportedVideo(rel, 4_000L)
    }
}

private class FakeTemplateAudioImporter(
    private val storage: ProjectMediaStorage,
) : AudioImporter {
    private var count = 0
    override suspend fun importAudio(projectId: String, sourceUri: String): ImportedAudio {
        val rel = storage.clipRelativePath(projectId, "a-${++count}", "m4a")
        val f = storage.resolve(rel)
        f?.parentFile?.mkdirs()
        f?.writeText("audio")
        return ImportedAudio(rel, 6_000L)
    }
}

private class FakeTemplateOverlayImporter(
    private val storage: ProjectMediaStorage,
    var failureReason: Reason? = null,
) : OverlayImporter {
    private var count = 0
    override suspend fun importOverlay(projectId: String, sourceUri: String): ImportedOverlay {
        failureReason?.let { throw VideoImportException(it) }
        val rel = storage.clipRelativePath(projectId, "o-${++count}", "png")
        val f = storage.resolve(rel)
        f?.parentFile?.mkdirs()
        f?.writeText("img")
        return ImportedOverlay(rel, 200, 200)
    }
}
