package com.projectmediapro.app.data.media

import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.core.model.videoClips
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Step40MediaHealthCheckerTest {

    @Test
    fun detectsMissingAndEmptyMediaIgnoresTextAndCleansUpSafely() {
        val tempRoot = Files.createTempDirectory("pmp-step40-health").toFile()
        try {
            val storage = ProjectMediaStorage(File(tempRoot, "media"))
            val healthyRel = storage.clipRelativePath("p1", "ok", "mp4")
            val emptyRel = storage.clipRelativePath("p1", "empty", "mp4")
            val missingRel = storage.clipRelativePath("p1", "gone", "mp3")

            requireNotNull(storage.resolve(healthyRel)).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(128))
            }
            requireNotNull(storage.resolve(emptyRel)).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(0))
            }

            val project = Project(
                id = "p1",
                title = "Recovery Test",
                durationMs = 5_000L,
                tracks = listOf(
                    Track(
                        id = "tv",
                        type = TrackType.VIDEO,
                        clips = listOf(
                            Clip(id = "c-ok", mediaUri = healthyRel, startTimeMs = 0L, durationMs = 3_000L),
                            Clip(id = "c-empty", mediaUri = emptyRel, startTimeMs = 3_000L, durationMs = 2_000L),
                        ),
                    ),
                    Track(
                        id = "ta",
                        type = TrackType.AUDIO,
                        clips = listOf(
                            Clip(id = "a-gone", mediaUri = missingRel, startTimeMs = 0L, durationMs = 4_000L),
                        ),
                    ),
                    Track(
                        id = "tt",
                        type = TrackType.TEXT,
                        clips = listOf(
                            Clip(id = "t1", mediaUri = "", startTimeMs = 0L, durationMs = 2_000L, textContent = "Hello"),
                        ),
                    ),
                ),
            )

            val issues = ProjectMediaHealthChecker.findMissingMedia(project, storage)
            assertEquals(setOf("c-empty", "a-gone"), issues.map { it.clipId }.toSet())

            val cleaned = ProjectMediaHealthChecker.removeMissingClips(project, issues.map { it.clipId }.toSet())
            assertEquals(1, cleaned.videoClips().size)
            assertEquals("c-ok", cleaned.videoClips().single().id)
            assertEquals(3_000L, cleaned.durationMs)
            assertTrue(ProjectMediaHealthChecker.findMissingMedia(cleaned, storage).isEmpty())
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
