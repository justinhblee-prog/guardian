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

    @Test fun restoresStickyServiceOnlyWhenProtectionWasPreviouslyEnabled() {
        assertTrue(ProtectionState.shouldRestoreAfterServiceRestart(persistedActive = true))
        assertFalse(ProtectionState.shouldRestoreAfterServiceRestart(persistedActive = false))
    }

    @Test fun durableUserIntentRestoresServiceAfterLiveTunnelFlagWasCleared() {
        assertTrue(ProtectionState.shouldRestoreAfterServiceRestart(
            persistedActive = false,
            restoreRequested = true
        ))
        assertFalse(ProtectionState.shouldRestoreAfterServiceRestart(
            persistedActive = true,
            restoreRequested = false
        ))
    }

    @Test fun explicitStopClearsRestartIntentEvenBeforeTunnelStarts() {
        assertTrue(ProtectionState.shouldPublishStoppedState(explicitStop = true, tunnelWasActive = false))
    }

    @Test fun serviceCleanupWithoutActiveTunnelDoesNotOverwriteStartupFailureState() {
        assertFalse(ProtectionState.shouldPublishStoppedState(explicitStop = false, tunnelWasActive = false))
        assertTrue(ProtectionState.shouldPublishStoppedState(explicitStop = false, tunnelWasActive = true))
    }
}
