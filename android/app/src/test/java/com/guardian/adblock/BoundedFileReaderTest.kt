package com.guardian.adblock

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedFileReaderTest {
    @Test
    fun readsRegularFileWithinConfiguredLimit() {
        val directory = Files.createTempDirectory("guardian-bounded-file").toFile()
        val expected = "bounded-cache-content".toByteArray()
        val file = File(directory, "cache.txt").apply { writeBytes(expected) }

        assertArrayEquals(expected, readBoundedFile(file, expected.size))
    }

    @Test
    fun rejectsFileLargerThanConfiguredLimitBeforeReading() {
        val directory = Files.createTempDirectory("guardian-bounded-file").toFile()
        val file = File(directory, "cache.txt").apply { writeBytes(ByteArray(1025) { 0x41 }) }

        assertNull(readBoundedFile(file, 1024))
    }

    @Test
    fun rejectsEmptyAndMissingFiles() {
        val directory = Files.createTempDirectory("guardian-bounded-file").toFile()
        val empty = File(directory, "empty.txt").apply { createNewFile() }
        val missing = File(directory, "missing.txt")

        assertNull(readBoundedFile(empty, 1024))
        assertNull(readBoundedFile(missing, 1024))
    }

    @Test
    fun rejectsSymbolicLinkCachePath() {
        val directory = Files.createTempDirectory("guardian-bounded-file").toFile()
        val target = File(directory, "target.txt").apply { writeText("trusted") }
        val link = File(directory, "link.txt")
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
            assertNull(readBoundedFile(link, 1024))
        } catch (_: UnsupportedOperationException) {
            // Filesystems without symlink support cannot exercise this platform guard.
        } catch (_: SecurityException) {
            // Restricted test environments may prohibit creating symlinks.
        }
    }
    @Test
    fun acceptsSecurityFilterLargerThanPreviousTenMegabyteLimit() {
        val directory = Files.createTempDirectory("guardian-bounded-file").toFile()
        val file = File(directory, "large-security-filter.txt")
        val expectedSize = 12 * 1024 * 1024
        file.setLength(expectedSize.toLong())

        assertEquals(expectedSize, readBoundedFile(file, MAX_FILTER_DOWNLOAD_BYTES)?.size)
    }

    @Test
    fun rejectsFilterCacheLargerThanThirtyTwoMegabytes() {
        val directory = Files.createTempDirectory("guardian-bounded-file").toFile()
        val file = File(directory, "oversized-filter.txt")
        file.setLength(MAX_FILTER_DOWNLOAD_BYTES.toLong() + 1L)

        assertNull(readBoundedFile(file, MAX_FILTER_DOWNLOAD_BYTES))
    }

}
