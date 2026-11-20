package com.projectmediapro.app.data.export

import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ClipTransition
import com.projectmediapro.core.model.ClipVisualEffects
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.core.model.VideoFilterPreset
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportEffectsPlanTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storage: ProjectMediaStorage

    @Before
    fun setUp() {
        storage = ProjectMediaStorage(File(tempFolder.root, "media"))
    }

    @Test
    fun plan_propagatesSanitizedClipEffectsAndNormalizesIdentityToNull() {
        val rel1 = createMedia("proj-1", "v1")
        val rel2 = createMedia("proj-1", "v2")
        val project = Project(
            id = "proj-1",
            title = "Effects Export",
            durationMs = 5_000L,
            tracks = listOf(
                Track(
                    id = "t-v",
                    type = TrackType.VIDEO,
                    clips = listOf(
                        Clip(
                            id = "v1",
                            mediaUri = rel1,
                            startTimeMs = 0L,
                            durationMs = 2_000L,
                            trimEndMs = 2_000L,
                            sourceDurationMs = 2_000L,
                            visualEffects = ClipVisualEffects(
                                filter = VideoFilterPreset.BW,
                                transitionIn = ClipTransition.FADE_BLACK,
                                fadeInMs = 2_500L,
                            ),
                        ),
                        Clip(
                            id = "v2",
                            mediaUri = rel2,
                            startTimeMs = 2_000L,
                            durationMs = 3_000L,
                            trimEndMs = 3_000L,
                            sourceDurationMs = 3_000L,
                            visualEffects = ClipVisualEffects(),
                        ),
                    ),
                ),
            ),
        )

        val plan = ExportPlanner.plan(project) { storage.resolve(it) }

        assertEquals(2, plan.clips.size)
        assertEquals(2_000L, plan.clips[0].durationMs)
        assertEquals(VideoFilterPreset.BW, plan.clips[0].visualEffects?.filter)
        assertEquals(1_000L, plan.clips[0].visualEffects?.fadeInMs)
        assertNull(plan.clips[1].visualEffects)
    }

    private fun createMedia(projectId: String, clipId: String): String {
        val rel = storage.clipRelativePath(projectId, clipId, "mp4")
        val file = requireNotNull(storage.resolve(rel))
        file.parentFile?.mkdirs()
        file.writeText("video-bytes")
        return rel
    }
}
