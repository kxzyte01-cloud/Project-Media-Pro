package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOverlayStyleTest {

    @Test
    fun sanitized_clampsOutOfRangeAndNaNValues() {
        val dirty = TextOverlayStyle(
            textSizeFraction = Float.NaN,
            strokeWidthFraction = 9.0f,
            letterSpacingEm = -2.0f,
        )

        val clean = dirty.sanitized()

        assertEquals(TextOverlayStyle.DEFAULT_TEXT_SIZE_FRACTION, clean.textSizeFraction, 0.0001f)
        assertEquals(TextOverlayStyle.MAX_STROKE_WIDTH_FRACTION, clean.strokeWidthFraction, 0.0001f)
        assertEquals(TextOverlayStyle.MIN_LETTER_SPACING_EM, clean.letterSpacingEm, 0.0001f)
    }

    @Test
    fun presets_allProduceValidSanitizedStyles() {
        TextStylePreset.entries.forEach { preset ->
            val style = TextOverlayStyle.preset(preset)
            assertEquals(style, style.sanitized())
        }
    }

    @Test
    fun projectTimeline_addUpdatePositionTimingAndRemoveTextClip() {
        val video = Clip(
            id = "v1",
            mediaUri = "p1/v1.mp4",
            startTimeMs = 0L,
            durationMs = 10_000L,
            trimEndMs = 10_000L,
            sourceDurationMs = 10_000L,
        )
        val base = Project(
            id = "p1",
            title = "Lyric Edit",
            durationMs = 10_000L,
            tracks = listOf(Track(id = "t-v", type = TrackType.VIDEO, clips = listOf(video))),
        )

        assertSame(base, ProjectTimeline.addTextClip(base, "txt-blank", "   ", newTrackId = "t-t"))

        val added = ProjectTimeline.addTextClip(
            project = base,
            clipId = "txt-1",
            text = "  Midnight drive  ",
            style = TextOverlayStyle.preset(TextStylePreset.KARAOKE_PILL),
            newTrackId = "t-text",
        )
        assertEquals(1, added.textClips().size)
        val firstText = added.textClips().first()
        assertEquals("Midnight drive", firstText.textContent)
        assertEquals("", firstText.mediaUri)
        assertEquals(0L, firstText.startTimeMs)
        assertEquals(ProjectTimeline.DEFAULT_TEXT_DURATION_MS, firstText.durationMs)
        assertFalse(ProjectTimeline.isMediaReferenced(added, ""))

        val styled = ProjectTimeline.updateTextClip(
            project = added,
            clipId = "txt-1",
            text = "Neon lights",
            style = TextOverlayStyle.preset(TextStylePreset.NEON_GLOW),
        )
        assertEquals("Neon lights", styled.textClips().first().textContent)
        assertEquals(TextFontFamily.CONDENSED, styled.textClips().first().textStyle?.fontFamily)

        val positioned = ProjectTimeline.updateTextTransform(
            project = styled,
            clipId = "txt-1",
            centerX = 1.5f,
            centerY = -0.2f,
            widthFraction = 0.6f,
        )
        assertEquals(1f, positioned.textClips().first().overlayCenterX, 0.0001f)
        assertEquals(0f, positioned.textClips().first().overlayCenterY, 0.0001f)
        assertEquals(0.6f, positioned.textClips().first().overlayWidthFraction, 0.0001f)

        val timed = ProjectTimeline.updateTextTiming(
            project = positioned,
            clipId = "txt-1",
            startTimeMs = 8_000L,
            durationMs = 5_000L,
        )
        assertEquals(8_000L, timed.textClips().first().startTimeMs)
        assertEquals(2_000L, timed.textClips().first().durationMs)

        val removed = ProjectTimeline.removeClip(timed, "txt-1")
        assertTrue(removed.textClips().isEmpty())
    }

    @Test
    fun serializer_roundTripsTextClipsAndReadsLegacySchemaV1Json() {
        val project = ProjectTimeline.addTextClip(
            project = Project(id = "p1", title = "Roundtrip"),
            clipId = "txt-1",
            text = "Chorus line",
            style = TextOverlayStyle.preset(TextStylePreset.CINEMA_TITLE),
            newTrackId = "t-text",
        )

        val raw = ProjectSerializer.serialize(project)
        val restored = ProjectSerializer.deserialize(raw)
        assertEquals(project, restored)

        val legacyJson = """
            {
              "id": "legacy-1",
              "title": "Old Project",
              "durationMs": 3000,
              "tracks": [
                {
                  "id": "t-v",
                  "type": "VIDEO",
                  "clips": [
                    {
                      "id": "v1",
                      "mediaUri": "legacy-1/v1.mp4",
                      "startTimeMs": 0,
                      "durationMs": 3000
                    }
                  ]
                }
              ],
              "schemaVersion": 1
            }
        """.trimIndent()
        val decodedLegacy = ProjectSerializer.deserialize(legacyJson)
        assertEquals("", decodedLegacy.videoClips().first().textContent)
        assertEquals(null, decodedLegacy.videoClips().first().textStyle)
    }
}
