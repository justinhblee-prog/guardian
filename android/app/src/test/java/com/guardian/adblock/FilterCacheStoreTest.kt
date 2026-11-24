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

    @Test
    fun backupMoveThatCompletesThenThrowsRestoresOriginalCache() {
        val folder = newFolder()
        val target = File(folder, "filter.txt").apply { writeText("known-good") }
        var movedThenFailed = false
        var metadataRestored = false

        try {
            FilterCacheStore.replace(
                target,
                "replacement".toByteArray(),
                commitMetadata = { },
                rollbackMetadata = { metadataRestored = true },
                moveFile = { source, destination ->
                    Files.move(
                        source.toPath(), destination.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                    if (source == target && !movedThenFailed) {
                        movedThenFailed = true
                        throw java.io.IOException("simulated post-move failure")
                    }
                }
            )
            throw AssertionError("Expected filesystem move failure")
        } catch (_: java.io.IOException) { }

        assertTrue(metadataRestored)
        assertArrayEquals("known-good".toByteArray(), target.readBytes())
        assertFalse(File(folder, "filter.txt.bak").exists())
        assertFalse(File(folder, "filter.txt.tmp").exists())
    }

    @Test
    fun failedFirstCacheMoveRemovesPartialTargetAndTemporaryFile() {
        val folder = newFolder()
        val target = File(folder, "filter.txt")

        try {
            FilterCacheStore.replace(
                target,
                "replacement".toByteArray(),
                commitMetadata = { },
                moveFile = { _, destination ->
                    destination.writeText("partial")
                    throw java.io.IOException("simulated first cache move failure")
                }
            )
            throw AssertionError("Expected filesystem move failure")
        } catch (_: java.io.IOException) { }

        assertFalse(target.exists())
        assertFalse(File(folder, "filter.txt.bak").exists())
        assertFalse(File(folder, "filter.txt.tmp").exists())
    }


    @Test
    fun failedBackupRestoreLeavesRecoverableBackupForNextStartup() {
        val folder = newFolder()
        val target = File(folder, "filter.txt").apply { writeText("known-good") }
        var backupRestoreAttempted = false

        try {
            FilterCacheStore.replace(
                target,
                "replacement".toByteArray(),
                commitMetadata = { },
                moveFile = { source, destination ->
                    if (source.name == "filter.txt.bak" && destination == target) {
                        backupRestoreAttempted = true
                        throw java.io.IOException("simulated restore failure")
                    }
                    Files.move(
                        source.toPath(), destination.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                    if (source == target) {
                        throw java.io.IOException("simulated post-backup-move failure")
                    }
                }
            )
            throw AssertionError("Expected replacement failure")
        } catch (_: java.io.IOException) { }

        assertTrue(backupRestoreAttempted)
        assertFalse(target.exists())
        assertTrue(File(folder, "filter.txt.bak").isFile)

        // A later process startup must recover the intact previous generation.
        FilterCacheStore.recover(target)

        assertArrayEquals("known-good".toByteArray(), target.readBytes())
        assertFalse(File(folder, "filter.txt.bak").exists())
        assertFalse(File(folder, "filter.txt.tmp").exists())
    }

}
