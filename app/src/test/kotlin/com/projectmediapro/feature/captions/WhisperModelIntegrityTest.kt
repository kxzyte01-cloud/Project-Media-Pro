package com.projectmediapro.feature.captions

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WhisperModelIntegrityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun hasValidGgmlHeader_acceptsGgmlAndGgufAndRejectsHtmlOrShortFiles() {
        val validLittleEndian = File(tempFolder.root, "valid_le.bin").apply {
            writeBytes("lmgg\u0001\u0002\u0003\u0004".toByteArray(Charsets.US_ASCII))
        }
        val validBigEndian = File(tempFolder.root, "valid_be.bin").apply {
            writeBytes("ggml\u0001\u0002\u0003\u0004".toByteArray(Charsets.US_ASCII))
        }
        val validGguf = File(tempFolder.root, "valid_gguf.bin").apply {
            writeBytes("GGUF\u0001\u0002\u0003\u0004".toByteArray(Charsets.US_ASCII))
        }
        val htmlError = File(tempFolder.root, "error.bin").apply {
            writeText("<!DOCTYPE html><html>404</html>")
        }
        val tooShort = File(tempFolder.root, "short.bin").apply {
            writeBytes(byteArrayOf(1, 2))
        }

        assertTrue(WhisperModelManager.hasValidGgmlHeader(validLittleEndian))
        assertTrue(WhisperModelManager.hasValidGgmlHeader(validBigEndian))
        assertTrue(WhisperModelManager.hasValidGgmlHeader(validGguf))
        assertFalse(WhisperModelManager.hasValidGgmlHeader(htmlError))
        assertFalse(WhisperModelManager.hasValidGgmlHeader(tooShort))
    }

    @Test
    fun moveModelAtomically_replacesTargetCleanly() {
        val temp = File(tempFolder.root, "model.tmp").apply { writeText("lmgg-new") }
        val target = File(tempFolder.root, "model.bin").apply { writeText("lmgg-old") }

        WhisperModelManager.moveModelAtomically(temp, target)

        assertFalse(temp.exists())
        assertTrue(target.exists())
        assertEquals("lmgg-new", target.readText())
    }

    @Test
    fun toLowerHex_formatsSha256AndPinnedHashesAreValid64HexChars() {
        val digest = MessageDigest.getInstance("SHA-256").digest("abc".toByteArray(Charsets.UTF_8))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            WhisperModelManager.toLowerHex(digest),
        )
        WhisperModelVariant.entries.forEach { variant ->
            assertEquals(64, variant.sha256.length)
            assertTrue(variant.sha256.all { it in '0'..'9' || it in 'a'..'f' })
        }
    }
}
