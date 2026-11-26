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
    fun downloadedFilterCleanupRemovesOnlyCacheGenerationsAndPreservesOtherFiles() {
        val folder = newFolder()
        val names = listOf("adguard_dns_filter.txt", "hagezi_threat_intelligence.txt", "awavenue_android_ads.txt")
        names.forEach { name ->
            File(folder, name).writeText("cache")
            File(folder, "$name.bak").writeText("backup")
            File(folder, "$name.tmp").writeText("temporary")
        }
        File(folder, "manifest_timestamps.properties").writeText("security=123")
        File(folder, "filter_rules.txt").writeText("||doubleclick.net^")

        names.forEach { name ->
            listOf(name, "$name.bak", "$name.tmp").forEach { File(folder, it).delete() }
        }

        names.forEach { name ->
            assertFalse(File(folder, name).exists())
            assertFalse(File(folder, "$name.bak").exists())
            assertFalse(File(folder, "$name.tmp").exists())
        }
        assertTrue(File(folder, "manifest_timestamps.properties").exists())
        assertTrue(File(folder, "filter_rules.txt").exists())
    }

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
    fun recoveryRestoresBackupWhenTargetDoesNotMatchCommittedSignedHash() {
        val folder = newFolder()
        val target = File(folder, "filter.txt").apply { writeText("new-uncommitted") }
        File(folder, "filter.txt.bak").writeText("old-committed")
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest("old-committed".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

        FilterCacheStore.recover(target) { candidate ->
            candidate.isFile && FilterManifestVerifier.matchesSha256(candidate.readBytes(), expected)
        }

        assertArrayEquals("old-committed".toByteArray(), target.readBytes())
        assertFalse(File(folder, "filter.txt.bak").exists())
    }

    @Test
    fun recoveryKeepsTargetAndDeletesBackupWhenTargetMatchesCommittedSignedHash() {
        val folder = newFolder()
        val target = File(folder, "filter.txt").apply { writeText("new-committed") }
        File(folder, "filter.txt.bak").writeText("old")
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest("new-committed".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

        FilterCacheStore.recover(target) { candidate ->
            candidate.isFile && FilterManifestVerifier.matchesSha256(candidate.readBytes(), expected)
        }

        assertArrayEquals("new-committed".toByteArray(), target.readBytes())
        assertFalse(File(folder, "filter.txt.bak").exists())
    }


    @Test
    fun recoveryRejectsTargetThatDoesNotMatchCommittedSignedHash() {
        val folder = newFolder()
        val target = File(folder, "filter.txt").apply { writeText("tampered") }
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest("committed".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

        try {
            FilterCacheStore.recover(target) { candidate ->
                candidate.isFile && FilterManifestVerifier.matchesSha256(candidate.readBytes(), expected)
            }
            throw AssertionError("tampered cache must fail closed")
        } catch (_: IllegalStateException) { }
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

    @Test
    fun recoveryRestoresBoundedBackupInsteadOfOversizedUnsignedTarget() {
        val folder = newFolder()
        val target = File(folder, "filter.txt").apply {
            writeBytes(ByteArray(1024 * 1024 + 1) { 0x61 })
        }
        File(folder, "filter.txt.bak").writeText("known-good")

        FilterCacheStore.recover(target) { candidate ->
            candidate.isFile && candidate.length() in 1..1024L
        }

        assertArrayEquals("known-good".toByteArray(), target.readBytes())
        assertFalse(File(folder, "filter.txt.bak").exists())
    }

    @Test
    fun recoveryRejectsOversizedUnsignedTargetWithoutBackup() {
        val folder = newFolder()
        val target = File(folder, "filter.txt").apply {
            writeBytes(ByteArray(1024 * 1024 + 1) { 0x61 })
        }

        try {
            FilterCacheStore.recover(target) { candidate ->
                candidate.isFile && candidate.length() in 1..1024L
            }
            throw AssertionError("oversized cache must be rejected")
        } catch (_: IllegalStateException) { }
    }

}
