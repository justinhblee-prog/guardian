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
