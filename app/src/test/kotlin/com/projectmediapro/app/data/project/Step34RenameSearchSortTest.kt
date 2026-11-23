package com.projectmediapro.app.data.project

import com.projectmediapro.app.home.HomeEvent
import com.projectmediapro.app.home.HomeSortOrder
import com.projectmediapro.app.home.HomeViewModel
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Step34RenameSearchSortTest {

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
    fun renameProjectPersistsNormalizedTitleAndRejectsBlankOrMissing() = runTest(testDispatcher) {
        val tempRoot = Files.createTempDirectory("pmp-step34-rename").toFile()
        try {
            var now = 1_000L
            var seq = 1
            val repo = FileProjectRepository(
                directory = File(tempRoot, "projects"),
                ioDispatcher = testDispatcher,
                idProvider = { "id-${seq++}" },
                clock = { now },
            )

            val created = repo.createProject("Original Edit")
            now = 5_000L
            val renamed = repo.renameProject(created.id, "  Final Cut 1.0  ")
            assertNotNull(renamed)
            assertEquals("Final Cut 1.0", renamed?.title)
            assertEquals(5_000L, renamed?.updatedAt)

            val reloaded = repo.getProject(created.id)
            assertEquals("Final Cut 1.0", reloaded?.title)
            assertEquals(5_000L, reloaded?.updatedAt)

            assertNull(repo.renameProject(created.id, "   "))
            assertNull(repo.renameProject("missing-id", "Valid Title"))
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun homeViewModelSupportsRenameSearchAndSortOrder() = runTest(testDispatcher) {
        val tempRoot = Files.createTempDirectory("pmp-step34-vm").toFile()
        try {
            var now = 1_000L
            var seq = 1
            val repo = FileProjectRepository(
                directory = File(tempRoot, "projects"),
                ioDispatcher = testDispatcher,
                idProvider = { "id-${seq++}" },
                clock = { now },
            )

            val zeta = repo.createProject("Zeta Reel")
            now = 2_000L
            repo.createProject("Alpha Lyric")

            val vm = HomeViewModel(repo)
            assertEquals(listOf("Alpha Lyric", "Zeta Reel"), vm.uiState.value.visibleProjects.map { it.title })

            // Switch to A-Z sort
            vm.onEvent(HomeEvent.SelectSortOrder(HomeSortOrder.TITLE_ASC))
            assertEquals(listOf("Alpha Lyric", "Zeta Reel"), vm.uiState.value.visibleProjects.map { it.title })

            // Rename Zeta Reel -> Aardvark Intro and verify A-Z order moves it first
            now = 3_000L
            vm.onEvent(HomeEvent.RequestRename(zeta.id))
            assertEquals(zeta.id, vm.uiState.value.pendingRenameId)
            vm.onEvent(HomeEvent.UpdateRenameTitle("Aardvark Intro"))
            vm.onEvent(HomeEvent.ConfirmRename)

            assertNull(vm.uiState.value.pendingRenameId)
            assertEquals(
                listOf("Aardvark Intro", "Alpha Lyric"),
                vm.uiState.value.visibleProjects.map { it.title },
            )

            // Search filter (case-insensitive)
            vm.onEvent(HomeEvent.UpdateSearchQuery("lyric"))
            assertEquals(listOf("Alpha Lyric"), vm.uiState.value.visibleProjects.map { it.title })

            vm.onEvent(HomeEvent.UpdateSearchQuery("nonexistent"))
            assertTrue(vm.uiState.value.visibleProjects.isEmpty())

            vm.onEvent(HomeEvent.UpdateSearchQuery(""))
            assertEquals(2, vm.uiState.value.visibleProjects.size)
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
