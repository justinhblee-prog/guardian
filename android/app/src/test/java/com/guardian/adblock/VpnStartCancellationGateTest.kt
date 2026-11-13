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
}
