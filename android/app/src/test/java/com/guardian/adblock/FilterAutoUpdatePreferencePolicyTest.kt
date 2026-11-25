package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterAutoUpdatePreferencePolicyTest {
    @Test
    fun appliesDefaultOnlyBeforeMigration() {
        assertTrue(FilterAutoUpdatePreferencePolicy.shouldEnableDefault(
            migrationAlreadyApplied = false,
            userChoiceRecorded = false
        ))
        assertFalse(FilterAutoUpdatePreferencePolicy.shouldEnableDefault(
            migrationAlreadyApplied = false,
            userChoiceRecorded = true
        ))
        assertFalse(FilterAutoUpdatePreferencePolicy.shouldEnableDefault(
            migrationAlreadyApplied = true,
            userChoiceRecorded = false
        ))
        assertFalse(FilterAutoUpdatePreferencePolicy.shouldEnableDefault(
            migrationAlreadyApplied = true,
            userChoiceRecorded = true
        ))
    }
}
