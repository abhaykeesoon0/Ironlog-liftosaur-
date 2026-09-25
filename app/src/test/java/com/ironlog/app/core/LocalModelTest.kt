package com.ironlog.app.core

import com.ironlog.app.core.ai.LocalModel
import com.ironlog.app.core.ai.Sha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The checks that stand between a downloaded file and the coach using it.
 * The hash is what refuses a truncated or altered download, so it has to be
 * right for a known input; and every model in the catalogue must carry the
 * pieces the downloader relies on.
 */
class LocalModelTest {

    @Test
    fun sha256MatchesAKnownVector() {
        val file = File.createTempFile("sha", ".bin").apply { deleteOnExit() }
        file.writeText("abc")
        // The SHA-256 of "abc", as published in FIPS 180-4.
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.of(file))
    }

    @Test
    fun sha256StreamsAFileLargerThanItsBuffer() {
        val file = File.createTempFile("sha", ".bin").apply { deleteOnExit() }
        file.writeBytes(ByteArray(200_000) { (it % 251).toByte() })
        val expected = java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, Sha256.of(file))
    }

    @Test
    fun everyModelInTheCatalogueIsCompleteAndDistinct() {
        LocalModel.entries.forEach { model ->
            assertTrue("${model.name} needs an https url", model.url.startsWith("https://"))
            assertTrue("${model.name} url must end in its file name", model.url.endsWith(model.fileName))
            assertEquals("${model.name} needs a full SHA-256", 64, model.sha256.length)
            assertTrue("${model.name} needs a size", model.bytes > 100_000_000L)
        }
        assertEquals(LocalModel.entries.size, LocalModel.entries.map { it.id }.toSet().size)
        assertEquals(LocalModel.entries.size, LocalModel.entries.map { it.sha256 }.toSet().size)
        assertEquals("550 MB", LocalModel.COMPACT.sizeLabel)
        assertEquals("1.6 GB", LocalModel.BETTER.sizeLabel)
    }
}
