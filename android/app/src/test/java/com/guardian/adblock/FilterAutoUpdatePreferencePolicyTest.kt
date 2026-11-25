package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterAutoUpdatePreferencePolicyTest {
    @Test
    fun appliesDefaultOnlyBeforeMigration() {
        assertTrue(FilterAutoUpdatePreferencePolicy.shouldApplyDefaultMigration(
            migrationAlreadyApplied = false,
            userChoiceRecorded = false
        ))
        assertTrue(FilterAutoUpdatePreferencePolicy.shouldApplyDefaultMigration(
            migrationAlreadyApplied = false,
            userChoiceRecorded = true
        ))
        assertFalse(FilterAutoUpdatePreferencePolicy.shouldApplyDefaultMigration(
            migrationAlreadyApplied = true,
            userChoiceRecorded = false
        ))
        assertFalse(FilterAutoUpdatePreferencePolicy.shouldApplyDefaultMigration(
            migrationAlreadyApplied = true,
            userChoiceRecorded = true
        ))
    }
}
