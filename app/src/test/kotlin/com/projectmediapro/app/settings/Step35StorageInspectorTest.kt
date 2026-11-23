package com.projectmediapro.app.settings

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Step35StorageInspectorTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun inspectAndClearExportCacheLeavesProjectsAndWhisperUntouched() = runTest(testDispatcher) {
        val tempRoot = Files.createTempDirectory("pmp-step35-storage").toFile()
        try {
            val projectsDir = File(tempRoot, "projects").apply { mkdirs() }
            val mediaDir = File(tempRoot, "media").apply { mkdirs() }
            val whisperDir = File(tempRoot, "whisper_models").apply { mkdirs() }
            val cacheDir = File(tempRoot, "exports").apply { mkdirs() }

            File(projectsDir, "p1.pmp.json").writeBytes(ByteArray(500))
            File(mediaDir, "clip.mp4").writeBytes(ByteArray(1_500))
            File(whisperDir, "ggml-tiny.bin").writeBytes(ByteArray(4_000))
            File(cacheDir, "tmp-export.mp4").writeBytes(ByteArray(2_048))

            val inspector = AppStorageInspector(
                projectsMediaDirs = listOf(projectsDir, mediaDir),
                whisperModelsDir = whisperDir,
                exportCacheDir = cacheDir,
                ioDispatcher = testDispatcher,
            )

            val before = inspector.inspect()
            assertEquals(2_000L, before.projectsMediaBytes)
            assertEquals(4_000L, before.whisperModelsBytes)
            assertEquals(2_048L, before.exportCacheBytes)
            assertEquals(8_048L, before.totalBytes)

            val freed = inspector.clearExportCache()
            assertEquals(2_048L, freed)
            assertTrue("Export cache directory itself should remain", cacheDir.isDirectory)

            val after = inspector.inspect()
            assertEquals(2_000L, after.projectsMediaBytes)
            assertEquals(4_000L, after.whisperModelsBytes)
            assertEquals(0L, after.exportCacheBytes)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun formatBytesFormatsDeterministicUnits() {
        assertEquals("0 B", AppStorageInspector.formatBytes(0L))
        assertEquals("0 B", AppStorageInspector.formatBytes(-10L))
        assertEquals("512 B", AppStorageInspector.formatBytes(512L))
        assertEquals("2.0 KB", AppStorageInspector.formatBytes(2_048L))
        assertEquals("1.5 MB", AppStorageInspector.formatBytes(1_572_864L))
        assertEquals("1.00 GB", AppStorageInspector.formatBytes(1_073_741_824L))
    }

    @Test
    fun settingsViewModelClearExportCacheUpdatesSnapshotAndClearedBytes() = runTest(testDispatcher) {
        val tempRoot = Files.createTempDirectory("pmp-step35-vm").toFile()
        try {
            val cacheDir = File(tempRoot, "exports").apply { mkdirs() }
            File(cacheDir, "render.mp4").writeBytes(ByteArray(3_072))

            val inspector = AppStorageInspector(
                projectsMediaDirs = listOf(File(tempRoot, "projects")),
                whisperModelsDir = File(tempRoot, "whisper"),
                exportCacheDir = cacheDir,
                ioDispatcher = testDispatcher,
            )
            val viewModel = SettingsViewModel(
                themePreferencesStore = object : ThemePreferencesStore {
                    override val themePreference: Flow<ThemePreference> = MutableStateFlow(ThemePreference.SYSTEM)
                    override suspend fun setThemePreference(preference: ThemePreference) = Unit
                },
                privacyPolicyDataSource = object : PrivacyPolicyDataSource {
                    override suspend fun loadPrivacyPolicy(): String = "Policy"
                },
                appVersionName = "1.0.0",
                storageInspector = inspector,
            )

            val collector = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }

            assertNotNull(viewModel.uiState.value.storageSnapshot)
            assertEquals(3_072L, viewModel.uiState.value.storageSnapshot?.exportCacheBytes)

            viewModel.onEvent(SettingsEvent.ClearExportCache)

            assertEquals(0L, viewModel.uiState.value.storageSnapshot?.exportCacheBytes)
            assertEquals(3_072L, viewModel.uiState.value.lastClearedCacheBytes)
            collector.cancel()
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
