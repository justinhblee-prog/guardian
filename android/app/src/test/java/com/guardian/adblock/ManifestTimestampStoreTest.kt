package com.guardian.adblock

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestTimestampStoreTest {
    private fun newStore(): Pair<File, ManifestTimestampStore> {
        val directory = Files.createTempDirectory("guardian-manifest-state").toFile()
        val file = File(directory, "manifest-timestamps.properties")
        return file to ManifestTimestampStore(file)
    }

    @Test
    fun recordsAndReadsHighestAcceptedTimestampAcrossInstances() {
        val (file, store) = newStore()
        store.recordAccepted("base-adblock", 1000L)

        assertEquals(1000L, ManifestTimestampStore(file).lastAccepted("base-adblock"))
    }

    @Test
    fun rejectsOlderAndReplayedTimestampsWithoutChangingState() {
        val (file, store) = newStore()
        store.recordAccepted("base-adblock", 2000L)

        listOf(1999L, 2000L).forEach { timestamp ->
            try {
                store.recordAccepted("base-adblock", timestamp)
                throw AssertionError("rollback timestamp must be rejected")
            } catch (_: IllegalArgumentException) { }
        }

        assertEquals(2000L, ManifestTimestampStore(file).lastAccepted("base-adblock"))
    }

    @Test
    fun tracksSourcesIndependently() {
        val (file, store) = newStore()
        store.recordAccepted("base-adblock", 5000L)
        store.recordAccepted("security", 1000L)

        val reopened = ManifestTimestampStore(file)
        assertEquals(5000L, reopened.lastAccepted("base-adblock"))
        assertEquals(1000L, reopened.lastAccepted("security"))
    }

    @Test
    fun restoreRollsBackTimestampAndCanRemoveFirstAcceptedValue() {
        val (file, store) = newStore()
        store.recordAccepted("base-adblock", 3000L)
        store.restore("base-adblock", 2000L)
        assertEquals(2000L, ManifestTimestampStore(file).lastAccepted("base-adblock"))
        store.restore("base-adblock", 0L)
        assertEquals(0L, ManifestTimestampStore(file).lastAccepted("base-adblock"))
    }

    @Test
    fun restoringOneSourcePreservesOtherSourcesAndRemovesTemporaryFile() {
        val (file, store) = newStore()
        store.recordAccepted("base-adblock", 3000L)
        store.recordAccepted("security", 5000L)
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText("partial-state")

        store.restore("base-adblock", 2000L)

        val reopened = ManifestTimestampStore(file)
        assertEquals(2000L, reopened.lastAccepted("base-adblock"))
        assertEquals(5000L, reopened.lastAccepted("security"))
        assertFalse(temporary.exists())
    }

    @Test
    fun rejectsSourceIdsOutsideDistributionAllowlist() {
        val (_, store) = newStore()
        listOf("../escape", "source/name", "source.name", ".", "..").forEach { sourceId ->
            try {
                store.lastAccepted(sourceId)
                throw AssertionError("unsupported source id must be rejected: $sourceId")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test
    fun missingStateStartsAtZeroAndDoesNotCreateAFile() {
        val (file, store) = newStore()

        assertEquals(0L, store.lastAccepted("base-adblock"))
        assertFalse(file.exists())
    }

    @Test
    fun rejectsCorruptPersistedTimestampInsteadOfResettingRollbackProtection() {
        val (file, store) = newStore()
        file.writeText("base-adblock=not-a-number\n")

        try {
            store.lastAccepted("base-adblock")
            throw AssertionError("corrupt state must fail closed")
        } catch (_: IllegalStateException) { }
        assertTrue(file.isFile)
    }
    @Test
    fun rejectsNegativeAndNonCanonicalPersistedTimestamps() {
        val (file, store) = newStore()
        listOf("-1", "+100", "100 ", "00100", "12345678901234567").forEach { value ->
            file.writeText("base-adblock=$value\n")
            try {
                store.lastAccepted("base-adblock")
                throw AssertionError("non-canonical timestamp must fail closed: $value")
            } catch (_: IllegalStateException) { }
        }
    }

    @Test
    fun refusesToAdvanceWhenPersistedTimestampIsNegative() {
        val (file, store) = newStore()
        file.writeText("base-adblock=-1\n")
        try {
            store.recordAccepted("base-adblock", 1000L)
            throw AssertionError("corrupt rollback state must not be overwritten")
        } catch (_: IllegalStateException) { }
        assertTrue(file.readText().contains("base-adblock=-1"))
    }
}
