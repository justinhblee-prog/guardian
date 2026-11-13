package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class VpnStartCancellationGateTest {
    @Test fun stopBeforeWorkerBeginsIsNotLost() {
        val gate = VpnStartCancellationGate()
        gate.onStartRequested()
        gate.cancel()
        assertTrue(gate.isCancelled())
    }

    @Test fun aNewExplicitStartClearsPreviousCancellation() {
        val gate = VpnStartCancellationGate()
        gate.onStartRequested()
        gate.cancel()
        gate.onStartRequested()
        assertFalse(gate.isCancelled())
    }
    @Test fun cancelledStartCannotCommitItsTunDescriptor() {
        val gate = VpnStartCancellationGate()
        gate.onStartRequested()
        gate.cancel()
        var committed = false
        assertFalse(gate.tryCommitStart { committed = true })
        assertFalse(committed)
    }

    @Test fun stopAfterCommitIsSerializedAfterSuccessfulActivation() {
        val gate = VpnStartCancellationGate()
        gate.onStartRequested()
        var committed = false
        assertTrue(gate.tryCommitStart { committed = true })
        assertTrue(committed)
        gate.cancel()
        assertTrue(gate.isCancelled())
    }

}
