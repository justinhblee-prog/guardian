package com.guardian.adblock

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilterCacheStoreTest {
    private val folder = TemporaryFolder().apply { create() }

    @Test
    fun successfulReplacementCommitsNewBytesAndRemovesBackup() {
        val target = File(folder.root, "filter.txt").apply { writeText("old") }

        FilterCacheStore.replace(target, "new".toByteArray()) { }

        assertArrayEquals("new".toByteArray(), target.readBytes())
        assertFalse(File(folder.root, "filter.txt.bak").exists())
        assertFalse(File(folder.root, "filter.txt.tmp").exists())
    }

    @Test
    fun metadataFailureRestoresLastKnownGoodCache() {
        val target = File(folder.root, "filter.txt").apply { writeText("known-good") }

        try {
            FilterCacheStore.replace(target, "uncommitted".toByteArray()) {
                throw IllegalStateException("metadata disk failure")
            }
            throw AssertionError("Expected metadata failure")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("metadata"))
        }

        assertArrayEquals("known-good".toByteArray(), target.readBytes())
        assertFalse(File(folder.root, "filter.txt.bak").exists())
        assertFalse(File(folder.root, "filter.txt.tmp").exists())
    }

    @Test
    fun recoveryRestoresBackupWhenProcessStoppedMidReplacement() {
        val target = File(folder.root, "filter.txt")
        File(folder.root, "filter.txt.bak").writeText("known-good")

        FilterCacheStore.recover(target)

        assertArrayEquals("known-good".toByteArray(), target.readBytes())
        assertFalse(File(folder.root, "filter.txt.bak").exists())
    }

    @Test
    fun recoveryKeepsCommittedTargetAndRemovesStaleBackup() {
        val target = File(folder.root, "filter.txt").apply { writeText("new") }
        File(folder.root, "filter.txt.bak").writeText("old")

        FilterCacheStore.recover(target)

        assertArrayEquals("new".toByteArray(), target.readBytes())
        assertFalse(File(folder.root, "filter.txt.bak").exists())
    }
}
