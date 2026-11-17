package com.projectmediapro.app.data.export

import java.io.IOException
import java.util.TimeZone
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExportNamingTest {

    // Proves that unsafe characters are removed, the length is capped at 40, and an empty result becomes "Project".
    @Test
    fun sanitizeExportName_filtersCapsAndFallsBack() {
        assertEquals("Project", sanitizeExportName("   "))
        assertEquals("Project", sanitizeExportName("///???***"))
        assertEquals("My_Video-1", sanitizeExportName("  My_Video-1!@#  "))
        assertEquals(40, sanitizeExportName("A".repeat(65)).length)
    }

    // Proves that the gallery file name is "<clean title>-<yyyyMMdd-HHmmss>.mp4" in the given time zone.
    @Test
    fun buildExportFileName_usesCleanTitleAndTimestamp() {
        val utc = TimeZone.getTimeZone("UTC")

        assertEquals(
            "Holiday Cut-20231114-221320.mp4",
            buildExportFileName("Holiday Cut!", 1_700_000_000_000L, utc),
        )
        assertEquals(
            "Project-20231114-221320.mp4",
            buildExportFileName("///", 1_700_000_000_000L, utc),
        )
    }

    // Proves that savePendingMediaStoreItem reports success only when publishItem updates >= 1 row,
    // and deletes the pending entry while throwing IOException when publishItem returns 0.
    @Test
    fun savePendingMediaStoreItem_verifiesPublishRowCount_andCleansUpOnFailure() = runTest {
        var deleted = false
        val saved = savePendingMediaStoreItem(
            insertItem = { "content://media/external/video/media/42" },
            copyToItem = {},
            publishItem = { 1 },
            deleteItem = { deleted = true },
            locationHint = "Movies/ProjectMediaPro/Demo.mp4",
        )
        assertEquals("Movies/ProjectMediaPro/Demo.mp4", saved.locationHint)
        assertFalse(deleted)

        var deletedOnUnpublished = false
        try {
            savePendingMediaStoreItem(
                insertItem = { "content://media/external/video/media/43" },
                copyToItem = {},
                publishItem = { 0 },
                deleteItem = { deletedOnUnpublished = true },
                locationHint = "Movies/ProjectMediaPro/Demo.mp4",
            )
            fail("Expected IOException when MediaStore publish returns 0 updated rows")
        } catch (_: IOException) {
            assertTrue(deletedOnUnpublished)
        }
    }
}
