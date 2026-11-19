package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class GuardianUpdateActionPolicyTest {
    @Test
    fun bothActionsAreLockedWhileChecking() {
        assertEquals(
            GuardianUpdateActionState(checkEnabled = false, installEnabled = false),
            GuardianUpdateActionPolicy.resolve(checking = true, hasVerifiedApk = false)
        )
    }

    @Test
    fun onlyCheckIsEnabledBeforeAnUpdateIsDownloaded() {
        assertEquals(
            GuardianUpdateActionState(checkEnabled = true, installEnabled = false),
            GuardianUpdateActionPolicy.resolve(checking = false, hasVerifiedApk = false)
        )
    }

    @Test
    fun onlyInstallIsEnabledAfterVerifiedUpdateDownload() {
        assertEquals(
            GuardianUpdateActionState(checkEnabled = false, installEnabled = true),
            GuardianUpdateActionPolicy.resolve(checking = false, hasVerifiedApk = true)
        )
    }
}
