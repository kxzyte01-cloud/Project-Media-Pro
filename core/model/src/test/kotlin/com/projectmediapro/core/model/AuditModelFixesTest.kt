package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditModelFixesTest {

    @Test
    fun templateApplier_clampsAndSkipsOverlaysAndTextPastVideoEnd() {
        val template = Template(
            id = "tpl-1",
            name = "Clamp Test",
            slots = listOf(
                Slot(
                    id = "v1",
                    label = "Main Clip",
                    requiredType = TrackType.VIDEO,
                    role = SlotRole.MAIN_VIDEO,
                    durationMs = 10_000L,
                ),
                Slot(
                    id = "ov-clamp",
                    label = "Clamped Overlay",
                    requiredType = TrackType.OVERLAY,
                    role = SlotRole.OVERLAY,
                    startTimeMs = 8_000L,
                    durationMs = 5_000L,
                ),
                Slot(
                    id = "ov-tail",
                    label = "Tail Overlay",
                    requiredType = TrackType.OVERLAY,
                    role = SlotRole.OVERLAY,
                    startTimeMs = 9_700L,
                    durationMs = 2_000L,
                ),
                Slot(
                    id = "ov-past",
                    label = "Past End Overlay",
                    requiredType = TrackType.OVERLAY,
                    role = SlotRole.OVERLAY,
                    startTimeMs = 15_000L,
                    durationMs = 4_000L,
                ),
            ),
            textClips = listOf(
                TemplateTextClip(id = "t-clamp", textContent = "Intro", startTimeMs = 7_000L, durationMs = 6_000L),
                TemplateTextClip(id = "t-tail", textContent = "Too Late", startTimeMs = 9_600L, durationMs = 2_000L),
                TemplateTextClip(id = "t-past", textContent = "Beyond", startTimeMs = 20_000L, durationMs = 3_000L),
            ),
        )
        val media = mapOf(
            "v1" to ResolvedTemplateMedia("clips/v1.mp4", 10_000L),
            "ov-clamp" to ResolvedTemplateMedia("overlays/o1.png", 5_000L),
            "ov-tail" to ResolvedTemplateMedia("overlays/o2.png", 2_000L),
            "ov-past" to ResolvedTemplateMedia("overlays/o3.png", 4_000L),
        )
        var counter = 0
        val project = TemplateApplier.buildProjectFromTemplate(
            baseProject = Project(id = "p1", title = "Test"),
            template = template,
            resolvedMediaBySlotId = media,
            idProvider = { "id-${++counter}" },
        )

        assertEquals(10_000L, project.durationMs)
        assertEquals(1, project.overlayClips().size)
        assertEquals(8_000L, project.overlayClips().first().startTimeMs)
        assertEquals(2_000L, project.overlayClips().first().durationMs)

        assertEquals(1, project.textClips().size)
        assertEquals(7_000L, project.textClips().first().startTimeMs)
        assertEquals(3_000L, project.textClips().first().durationMs)
    }

    @Test
    fun saturatingAdd_neverOverflowsOrWrapsNegative() {
        assertEquals(Long.MAX_VALUE, ProjectTimeline.saturatingAdd(Long.MAX_VALUE - 10L, 100L))
        assertEquals(Long.MAX_VALUE, ProjectTimeline.saturatingAdd(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(500L, ProjectTimeline.saturatingAdd(-100L, 500L))
        assertTrue(ProjectTimeline.saturatingAdd(Long.MAX_VALUE, 1L) > 0L)
    }
}
