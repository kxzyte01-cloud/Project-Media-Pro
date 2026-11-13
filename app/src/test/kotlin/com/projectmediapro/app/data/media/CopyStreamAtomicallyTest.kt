package com.projectmediapro.app.data.media

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CopyStreamAtomicallyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun copyStreamAtomically_writesExactContentAndRemovesPartFile() {
        val dir = File(tempFolder.root, "media/proj-1")
        val finalFile = File(dir, "clip-1.mp4")
        val partFile = File(dir, "clip-1.mp4.part")
        val payload = ByteArray(150_000) { index -> (index % 251).toByte() }

        copyStreamAtomically(
            input = ByteArrayInputStream(payload),
            partFile = partFile,
            finalFile = finalFile,
        )

        assertTrue(finalFile.isFile)
        assertFalse(partFile.exists())
        assertArrayEquals(payload, finalFile.readBytes())
    }

    @Test
    fun copyStreamAtomically_whenStreamThrowsMidway_leavesNoPartFileAndReplacesNothing() {
        val dir = tempFolder.newFolder("media", "proj-1")
        val finalFile = File(dir, "clip-1.mp4")
        val partFile = File(dir, "clip-1.mp4.part")
        finalFile.writeText("original-content", Charsets.UTF_8)

        val failingInput = object : InputStream() {
            private var bytesDelivered = 0

            override fun read(): Int {
                if (bytesDelivered >= 1024) {
                    throw IOException("Mid-stream failure")
                }
                bytesDelivered++
                return 42
            }
        }

        var threw = false
        try {
            copyStreamAtomically(
                input = failingInput,
                partFile = partFile,
                finalFile = finalFile,
            )
        } catch (_: IOException) {
            threw = true
        }

        assertTrue(threw)
        assertFalse(partFile.exists())
        assertTrue(finalFile.isFile)
        assertEquals("original-content", finalFile.readText(Charsets.UTF_8))
    }
}
