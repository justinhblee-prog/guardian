package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class VpnStartCancellationGateTest {
    @Test fun stopInvalidatesTheCurrentStartGeneration() {
        val gate = VpnStartCancellationGate()
        val start = gate.onStartRequested()
        assertTrue(gate.isCurrent(start))
        gate.cancel()
        assertFalse(gate.isCurrent(start))
    }

    @Test fun aNewStartDoesNotReviveAnOlderCancelledWorker() {
        val gate = VpnStartCancellationGate()
        val firstStart = gate.onStartRequested()
        gate.cancel()
        val secondStart = gate.onStartRequested()

        assertFalse(gate.isCurrent(firstStart))
        assertTrue(gate.isCurrent(secondStart))
        var oldCommitted = false
        var newCommitted = false
        assertFalse(gate.tryCommitStart(firstStart) { oldCommitted = true })
        assertTrue(gate.tryCommitStart(secondStart) { newCommitted = true })
        assertFalse(oldCommitted)
        assertTrue(newCommitted)
    }

    @Test fun staleStartupFailureCannotPublishOrCleanUpOverANewerStart() {
        val gate = VpnStartCancellationGate()
        val oldStart = gate.onStartRequested()
        val newStart = gate.onStartRequested()
        var staleFailurePublished = false
        var newerTunnelClosed = false

        val oldFailureCommitted = gate.tryCommitStart(oldStart) {
            staleFailurePublished = true
            newerTunnelClosed = true
        }

        assertFalse(oldFailureCommitted)
        assertFalse(staleFailurePublished)
        assertFalse(newerTunnelClosed)
        assertTrue(gate.isCurrent(newStart))
    }

    @Test fun currentStartupFailureCanCommitItsOwnCleanup() {
        val gate = VpnStartCancellationGate()
        val start = gate.onStartRequested()
        var failurePublished = false

        assertTrue(gate.tryCommitStart(start) { failurePublished = true })
        assertTrue(failurePublished)
    }

    @Test fun cancelledStartCannotCommitItsTunDescriptor() {
        val gate = VpnStartCancellationGate()
        val start = gate.onStartRequested()
        gate.cancel()
        var committed = false
        assertFalse(gate.tryCommitStart(start) { committed = true })
        assertFalse(committed)
    }

    @Test fun stopAfterCommitIsSerializedAfterSuccessfulActivation() {
        val gate = VpnStartCancellationGate()
        val start = gate.onStartRequested()
        var committed = false
        assertTrue(gate.tryCommitStart(start) { committed = true })
        assertTrue(committed)
        gate.cancel()
        assertFalse(gate.isCurrent(start))
    }

    @Test fun stalePendingGenerationCannotReplaceTheLatestStart() {
        val gate = VpnStartCancellationGate()
        val first = gate.onStartRequested()
        gate.cancel()
        val second = gate.onStartRequested()
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }
}
