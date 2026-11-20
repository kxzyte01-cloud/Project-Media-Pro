package com.projectmediapro.app.editor

import com.projectmediapro.app.data.media.ImportedVideo
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImporter
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.feature.captions.CaptionDocument
import com.projectmediapro.feature.captions.CaptionRepository
import com.projectmediapro.feature.captions.CaptionTemplateId
import com.projectmediapro.feature.captions.LyricsPresetRegistry
import com.projectmediapro.feature.captions.OfflineHeuristicTranscriptionEngine
import com.projectmediapro.feature.captions.PcmAudioData
import com.projectmediapro.feature.captions.PcmAudioExtractor
import java.io.File
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EditorAutoCaptionsViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun captionsWorkflow_generatePresetTemplateOffsetEditSrtAndClear() = runTest(testDispatcher) {
        val storage = ProjectMediaStorage(File(tempFolder.root, "media"))
        val videoRel = storage.clipRelativePath("p1", "v1", "mp4")
        val videoFile = requireNotNull(storage.resolve(videoRel))
        videoFile.parentFile?.mkdirs()
        videoFile.writeText("fake-video")

        val videoClip = Clip(
            id = "v1",
            mediaUri = videoRel,
            startTimeMs = 0L,
            durationMs = 4_000L,
            trimEndMs = 4_000L,
            sourceDurationMs = 4_000L,
        )
        val project = Project(
            id = "p1",
            title = "Auto Captions",
            durationMs = 4_000L,
            tracks = listOf(Track(id = "t-v", type = TrackType.VIDEO, clips = listOf(videoClip))),
        )
        val captionRepo = InMemoryCaptionRepository()
        val viewModel = EditorViewModel(
            projectId = "p1",
            repository = InMemoryProjectRepository(project),
            videoImporter = StubVideoImporter,
            mediaStorage = storage,
            captionRepository = captionRepo,
            pcmAudioExtractor = FakeTonePcmExtractor,
            transcriptionEngine = OfflineHeuristicTranscriptionEngine(testDispatcher),
        )
        advanceUntilIdle()

        viewModel.onEvent(EditorEvent.OpenCaptions)
        assertEquals(EditorDialog.Captions, (viewModel.uiState.value as EditorUiState.Loaded).activeDialog)

        viewModel.onEvent(EditorEvent.GenerateCaptions(providedScript = "Midnight city lights glow"))
        advanceUntilIdle()

        val generated = (viewModel.uiState.value as EditorUiState.Loaded).captionDocument
        assertNotNull(generated)
        val generatedDoc = requireNotNull(generated)
        assertEquals(4, generatedDoc.rawWords.size)
        assertTrue(generatedDoc.lines.isNotEmpty())
        assertEquals(generatedDoc, captionRepo.stored["p1"])

        viewModel.onEvent(EditorEvent.SelectCaptionPreset(LyricsPresetRegistry.FAST_FLOW_ID))
        advanceUntilIdle()
        assertEquals(
            LyricsPresetRegistry.FAST_FLOW_ID,
            (viewModel.uiState.value as EditorUiState.Loaded).captionDocument?.presetId,
        )

        viewModel.onEvent(EditorEvent.SelectCaptionTemplate(CaptionTemplateId.TYPEWRITER))
        advanceUntilIdle()
        val afterTemplate = requireNotNull((viewModel.uiState.value as EditorUiState.Loaded).captionDocument)
        assertEquals(CaptionTemplateId.TYPEWRITER, afterTemplate.style.templateId)
        assertTrue(afterTemplate.style.backgroundBoxEnabled)

        viewModel.onEvent(EditorEvent.UpdateCaptionOffsetUs(150_000L))
        advanceUntilIdle()
        assertEquals(
            150_000L,
            (viewModel.uiState.value as EditorUiState.Loaded).captionDocument?.globalOffsetUs,
        )

        val firstWordId = afterTemplate.rawWords.first().id
        viewModel.onEvent(EditorEvent.UpdateCaptionWordText(firstWordId, "Neon"))
        advanceUntilIdle()
        assertEquals(
            "Neon",
            (viewModel.uiState.value as EditorUiState.Loaded).captionDocument?.rawWords?.first()?.text,
        )

        val srt = """
            1
            00:00:00,500 --> 00:00:02,000
            Imported caption line
        """.trimIndent()
        viewModel.onEvent(EditorEvent.ImportSrtCaptions(srt))
        advanceUntilIdle()
        val importedDoc = requireNotNull((viewModel.uiState.value as EditorUiState.Loaded).captionDocument)
        assertEquals(3, importedDoc.rawWords.size)
        assertEquals("Imported", importedDoc.rawWords.first().text)

        viewModel.onEvent(EditorEvent.ClearCaptions)
        advanceUntilIdle()
        assertNull((viewModel.uiState.value as EditorUiState.Loaded).captionDocument)
        assertNull(captionRepo.stored["p1"])
    }


    @Test
    fun multiClipScriptAlignment_spansAllClipsAndReportsErrors() = runTest(testDispatcher) {
        val storage = ProjectMediaStorage(File(tempFolder.root, "media-multi"))
        val rel1 = storage.clipRelativePath("p2", "v1", "mp4")
        val rel2 = storage.clipRelativePath("p2", "v2", "mp4")
        listOf(rel1, rel2).forEach { rel ->
            val file = requireNotNull(storage.resolve(rel))
            file.parentFile?.mkdirs()
            file.writeText("fake-video")
        }
        val project = Project(
            id = "p2",
            title = "Multi Clip Captions",
            durationMs = 4_000L,
            tracks = listOf(
                Track(
                    id = "t-v",
                    type = TrackType.VIDEO,
                    clips = listOf(
                        Clip(id = "v1", mediaUri = rel1, startTimeMs = 0L, durationMs = 2_000L, trimEndMs = 2_000L, sourceDurationMs = 2_000L),
                        Clip(id = "v2", mediaUri = rel2, startTimeMs = 2_000L, durationMs = 2_000L, trimEndMs = 2_000L, sourceDurationMs = 2_000L),
                    ),
                ),
            ),
        )
        val viewModel = EditorViewModel(
            projectId = "p2",
            repository = InMemoryProjectRepository(project),
            videoImporter = StubVideoImporter,
            mediaStorage = storage,
            captionRepository = InMemoryCaptionRepository(),
            pcmAudioExtractor = FakeTonePcmExtractor,
            transcriptionEngine = OfflineHeuristicTranscriptionEngine(testDispatcher),
        )
        advanceUntilIdle()

        viewModel.onEvent(EditorEvent.GenerateCaptions(providedScript = "One two three four"))
        advanceUntilIdle()

        val doc = requireNotNull((viewModel.uiState.value as EditorUiState.Loaded).captionDocument)
        assertEquals(4, doc.rawWords.size)
        assertTrue(doc.rawWords.none { it.isPlaceholder })
        assertTrue(doc.rawWords.last().endUs > 2_000_000L)

        viewModel.onEvent(EditorEvent.ReportCaptionError)
        assertEquals(EditorError.CAPTIONS_FAILED, (viewModel.uiState.value as EditorUiState.Loaded).error)
    }

    @Test
    fun timelineClock_timelineToPlayerPositionMapsAcrossMultipleClips() {
        val durations = listOf(3_000L, 4_000L, 2_000L)
        assertEquals(0 to 1_500L, TimelineClock.timelineToPlayerPosition(durations, 1_500L))
        assertEquals(1 to 500L, TimelineClock.timelineToPlayerPosition(durations, 3_500L))
        assertEquals(2 to 2_000L, TimelineClock.timelineToPlayerPosition(durations, 12_000L))
        assertNull(TimelineClock.timelineToPlayerPosition(emptyList(), 1_000L))
    }


    @Test
    fun audioTrackLyricSync_extractsFromAudioTrackWhenVideoIsSilent() = runTest(testDispatcher) {
        val storage = ProjectMediaStorage(File(tempFolder.root, "media-audio-track"))
        val videoRel = storage.clipRelativePath("p3", "v1", "mp4")
        val audioRel = storage.clipRelativePath("p3", "a1", "mp3")
        listOf(videoRel, audioRel).forEach { rel ->
            val file = requireNotNull(storage.resolve(rel))
            file.parentFile?.mkdirs()
            file.writeText("fake-media")
        }
        val project = Project(
            id = "p3",
            title = "Lyric Song Edit",
            durationMs = 3_000L,
            tracks = listOf(
                Track(
                    id = "t-v",
                    type = TrackType.VIDEO,
                    clips = listOf(
                        Clip(id = "v1", mediaUri = videoRel, startTimeMs = 0L, durationMs = 3_000L, trimEndMs = 3_000L, sourceDurationMs = 3_000L),
                    ),
                ),
                Track(
                    id = "t-a",
                    type = TrackType.AUDIO,
                    clips = listOf(
                        Clip(id = "a1", mediaUri = audioRel, startTimeMs = 0L, durationMs = 3_000L, trimEndMs = 3_000L, sourceDurationMs = 3_000L, volume = 0.8f),
                    ),
                ),
            ),
        )
        val audioOnlyExtractor = object : PcmAudioExtractor {
            override suspend fun extractMono16kPcm(
                file: File,
                trimStartUs: Long,
                trimEndUs: Long,
                maxDurationUs: Long,
            ): PcmAudioData {
                if (file.extension == "mp4") {
                    return PcmAudioData(ShortArray(0), PcmAudioData.TARGET_SAMPLE_RATE_HZ, 1, 0L)
                }
                return FakeTonePcmExtractor.extractMono16kPcm(file, trimStartUs, trimEndUs, maxDurationUs)
            }
        }
        val viewModel = EditorViewModel(
            projectId = "p3",
            repository = InMemoryProjectRepository(project),
            videoImporter = StubVideoImporter,
            mediaStorage = storage,
            captionRepository = InMemoryCaptionRepository(),
            pcmAudioExtractor = audioOnlyExtractor,
            transcriptionEngine = OfflineHeuristicTranscriptionEngine(testDispatcher),
        )
        advanceUntilIdle()

        viewModel.onEvent(EditorEvent.GenerateCaptions(providedScript = "Feel the beat tonight"))
        advanceUntilIdle()

        val doc = requireNotNull((viewModel.uiState.value as EditorUiState.Loaded).captionDocument)
        assertEquals(4, doc.rawWords.size)
        assertTrue(doc.rawWords.none { it.isPlaceholder })
        assertTrue(doc.lines.isNotEmpty())
    }

    private class InMemoryCaptionRepository : CaptionRepository {
        val stored = mutableMapOf<String, CaptionDocument>()
        override suspend fun loadCaptions(projectId: String): CaptionDocument? = stored[projectId]
        override suspend fun saveCaptions(document: CaptionDocument) {
            stored[document.projectId] = document
        }
        override suspend fun deleteCaptions(projectId: String) {
            stored.remove(projectId)
        }
    }

    private object FakeTonePcmExtractor : PcmAudioExtractor {
        override suspend fun extractMono16kPcm(
            file: File,
            trimStartUs: Long,
            trimEndUs: Long,
            maxDurationUs: Long,
        ): PcmAudioData {
            val rate = PcmAudioData.TARGET_SAMPLE_RATE_HZ
            val samples = ShortArray(rate * 2) { i ->
                (sin(2.0 * Math.PI * 440.0 * i / rate) * 16_000.0).toInt().toShort()
            }
            return PcmAudioData(
                samples = samples,
                sampleRateHz = rate,
                channelCount = 1,
                durationUs = 2_000_000L,
            )
        }
    }

    private class InMemoryProjectRepository(initial: Project) : ProjectRepository {
        private val projects = mutableMapOf(initial.id to initial)
        override suspend fun listProjects(): List<Project> = projects.values.toList()
        override suspend fun getProject(id: String): Project? = projects[id]
        override suspend fun createProject(title: String): Project {
            val created = Project(id = "new", title = title)
            projects[created.id] = created
            return created
        }
        override suspend fun saveProject(project: Project) {
            projects[project.id] = project
        }
        override suspend fun deleteProject(id: String) {
            projects.remove(id)
        }
    }

    private object StubVideoImporter : VideoImporter {
        override suspend fun importVideo(projectId: String, sourceUri: String): ImportedVideo {
            return ImportedVideo(relativePath = "$projectId/stub.mp4", durationMs = 4_000L)
        }
    }
}
