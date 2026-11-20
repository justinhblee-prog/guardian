package com.guardian.adblock

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    fun cacheReplacementWaitsUntilReaderFinishesItsSnapshot() {
        val folder = newFolder()
        val target = File(folder, "filter.txt").apply { writeText("old-snapshot") }
        val readerEntered = CountDownLatch(1)
        val releaseReader = CountDownLatch(1)
        val replacementFinished = CountDownLatch(1)
        val readerFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)

        val reader = Thread {
            try {
                FilterCacheStore.withLock {
                    readerEntered.countDown()
                    check(releaseReader.await(5, TimeUnit.SECONDS))
                    assertArrayEquals("old-snapshot".toByteArray(), target.readBytes())
                }
            } catch (error: Throwable) {
                readerFailure.set(error)
            }
        }
        reader.start()
        assertTrue("reader should acquire the cache lock", readerEntered.await(5, TimeUnit.SECONDS))

        val writer = Thread {
            try {
                FilterCacheStore.replace(target, "new-snapshot".toByteArray(), commitMetadata = { })
            } finally {
                replacementFinished.countDown()
            }
        }
        writer.start()
        try {
            assertFalse(
                "replacement must wait while a reader holds the cache snapshot",
                replacementFinished.await(150, TimeUnit.MILLISECONDS)
            )
        } finally {
            releaseReader.countDown()
        }

        assertTrue("replacement should finish after reader releases the lock",
            replacementFinished.await(5, TimeUnit.SECONDS))
        reader.join(5000)
        writer.join(5000)
        readerFailure.get()?.let { throw AssertionError("reader failed", it) }
        assertArrayEquals("new-snapshot".toByteArray(), target.readBytes())
    }
}
