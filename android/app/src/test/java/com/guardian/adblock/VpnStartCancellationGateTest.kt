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

    @Test fun explicitStopBeforeDelayedResumeCheckCannotCreateANewStartGeneration() {
        val gate = VpnStartCancellationGate()
        val lifecycleLock = Any()
        var restoreRequested = true
        gate.onStartRequested()

        // Models stopProtection(): durable intent is cleared and the active
        // generation is cancelled while holding the service lifecycle monitor.
        synchronized(lifecycleLock) {
            restoreRequested = false
            gate.cancel()
        }

        // Models the delayed compatibility/exclusion resume handoff. The
        // intent check and generation creation must use the same monitor.
        val resumedGeneration = synchronized(lifecycleLock) {
            if (!restoreRequested) null else gate.onStartRequested()
        }

        assertNull(resumedGeneration)
    }
}
