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

    @Test fun ignoresUnrelatedBroadcastsEvenWhenProtectionWasPreviouslyEnabled() {
        assertFalse(ProtectionRestorePolicy.shouldRestore("android.intent.action.TIME_SET", true))
        assertFalse(ProtectionRestorePolicy.shouldRestore(null, true))
    }

    @Test fun activeProtectionIntentSurvivesUnexpectedServiceTeardown() {
        assertTrue(ProtectionRestorePolicy.shouldPreserveRestoreIntentOnServiceDestroy(true))
        assertFalse(ProtectionRestorePolicy.shouldPreserveRestoreIntentOnServiceDestroy(false))
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
