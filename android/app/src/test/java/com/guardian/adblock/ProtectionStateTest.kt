package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionStateTest {
    @Test fun persistedFlagCannotClaimActiveAfterProcessRestart() {
        assertFalse(ProtectionState.isActive(persistedActive = true, tunnelActive = false))
    }

    @Test fun activeRequiresBothPersistedFlagAndLiveTunnel() {
        assertTrue(ProtectionState.isActive(persistedActive = true, tunnelActive = true))
        assertFalse(ProtectionState.isActive(persistedActive = false, tunnelActive = true))
        assertFalse(ProtectionState.isActive(persistedActive = false, tunnelActive = false))
    }
}
