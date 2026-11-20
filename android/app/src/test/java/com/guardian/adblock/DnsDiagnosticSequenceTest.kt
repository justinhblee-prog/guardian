package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class DnsDiagnosticSequenceTest {
    @Test
    fun olderAsyncDiagnosticCannotOverwriteNewerPacketEvent() {
        val sequence = DnsDiagnosticSequence()
        val slowTest = sequence.reserve()
        val laterPacket = sequence.reserve()

        assertFalse(sequence.isCurrent(slowTest))
        assertTrue(sequence.isCurrent(laterPacket))
    }

    @Test
    fun clearingDiagnosticsInvalidatesPendingAsyncWriters() {
        val sequence = DnsDiagnosticSequence()
        val pendingTest = sequence.reserve()

        val clearBarrier = sequence.invalidate()

        assertFalse(sequence.isCurrent(pendingTest))
        assertTrue(sequence.isCurrent(clearBarrier))
    }

    @Test
    fun newestDiagnosticRemainsCurrentUntilAnotherEventStarts() {
        val sequence = DnsDiagnosticSequence()
        val first = sequence.reserve()
        val second = sequence.reserve()

        assertFalse(sequence.isCurrent(first))
        assertTrue(sequence.isCurrent(second))
    }
}
