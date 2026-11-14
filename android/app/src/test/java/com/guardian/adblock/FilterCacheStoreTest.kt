package com.guardian.adblock

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterCacheStoreTest {
    private fun newFolder(): File = Files.createTempDirectory("guardian-filter-cache-test").toFile()

    @Test
    fun successfulReplacementCommitsNewBytesAndRemovesBackup() {
        val folder = newFolder()
        val target = File(folder, "filter.txt").apply { writeText("old") }

        FilterCacheStore.replace(target, "new".toByteArray(), commitMetadata = { })

        assertArrayEquals("new".toByteArray(), target.readBytes())
        assertFalse(File(folder, "filter.txt.bak").exists())
        assertFalse(File(folder, "filter.txt.tmp").exists())
    }

    @Test
    fun metadataFailureRestoresLastKnownGoodCache() {
        val folder = newFolder()
        val target = File(folder, "filter.txt").apply { writeText("known-good") }

        var metadataRestored = false
        try {
            FilterCacheStore.replace(
                target,
                "uncommitted".toByteArray(),
                commitMetadata = { throw IllegalStateException("metadata disk failure") },
                rollbackMetadata = { metadataRestored = true }
            )
            throw AssertionError("Expected metadata failure")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("metadata"))
        }

        assertTrue(metadataRestored)
        assertArrayEquals("known-good".toByteArray(), target.readBytes())
        assertFalse(File(folder, "filter.txt.bak").exists())
        assertFalse(File(folder, "filter.txt.tmp").exists())
    }

    @Test
    fun recoveryRestoresBackupWhenProcessStoppedMidReplacement() {
        val folder = newFolder()
        val target = File(folder, "filter.txt")
        File(folder, "filter.txt.bak").writeText("known-good")

        FilterCacheStore.recover(target)

        assertArrayEquals("known-good".toByteArray(), target.readBytes())
        assertFalse(File(folder, "filter.txt.bak").exists())
    }

    @Test
    fun recoveryKeepsCommittedTargetAndRemovesStaleBackup() {
        val folder = newFolder()
        val target = File(folder, "filter.txt").apply { writeText("new") }
        File(folder, "filter.txt.bak").writeText("old")

        FilterCacheStore.recover(target)

        assertArrayEquals("new".toByteArray(), target.readBytes())
        assertFalse(File(folder, "filter.txt.bak").exists())
    }
}
