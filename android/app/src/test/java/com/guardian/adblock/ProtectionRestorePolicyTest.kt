package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionRestorePolicyTest {
    @Test fun restoresAfterBootOnlyWhenProtectionWasPreviouslyEnabled() {
        assertTrue(ProtectionRestorePolicy.shouldRestore(
            ProtectionRestorePolicy.ACTION_BOOT_COMPLETED, true
        ))
        assertFalse(ProtectionRestorePolicy.shouldRestore(
            ProtectionRestorePolicy.ACTION_BOOT_COMPLETED, false
        ))
    }

    @Test fun restoresAfterPackageReplacementOnlyWhenProtectionWasPreviouslyEnabled() {
        assertTrue(ProtectionRestorePolicy.shouldRestore(
            ProtectionRestorePolicy.ACTION_MY_PACKAGE_REPLACED, true
        ))
        assertFalse(ProtectionRestorePolicy.shouldRestore(
            ProtectionRestorePolicy.ACTION_MY_PACKAGE_REPLACED, false
        ))
    }

    @Test fun restoresAfterPackageReplacementWhenDurableIntentSurvivesServiceShutdown() {
        assertTrue(ProtectionRestorePolicy.shouldRestore(
            ProtectionRestorePolicy.ACTION_MY_PACKAGE_REPLACED,
            persistedActive = false,
            restoreRequested = true
        ))
    }

    @Test fun explicitUserStopOverridesStalePersistedActiveFlag() {
        assertFalse(ProtectionRestorePolicy.shouldRestore(
            ProtectionRestorePolicy.ACTION_MY_PACKAGE_REPLACED,
            persistedActive = true,
            restoreRequested = false
        ))
        assertFalse(ProtectionRestorePolicy.shouldRestore(
            ProtectionRestorePolicy.ACTION_BOOT_COMPLETED,
            persistedActive = true,
            restoreRequested = false
        ))
    }

    @Test fun ignoresUnrelatedBroadcastsEvenWhenProtectionWasPreviouslyEnabled() {
        assertFalse(ProtectionRestorePolicy.shouldRestore("android.intent.action.TIME_SET", true))
        assertFalse(ProtectionRestorePolicy.shouldRestore(null, true))
    }

    @Test fun explicitStoppedStateDoesNotTriggerRestore() {
        assertFalse(ProtectionRestorePolicy.shouldRestore(
            ProtectionRestorePolicy.ACTION_BOOT_COMPLETED, false
        ))
        assertFalse(ProtectionRestorePolicy.shouldRestore(
            ProtectionRestorePolicy.ACTION_MY_PACKAGE_REPLACED, false
        ))
    }
}
