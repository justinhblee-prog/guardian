package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityScreenStateTest {
    @Test fun restoresEverySupportedWorkspaceTab() {
        listOf("home", "protection", "app-exclusions", "activity", "premium", "rules", "settings", "updates").forEach { tab ->
            assertEquals(tab, ActivityScreenState.restoreTab(tab))
        }
    }

    @Test fun missingOrUnknownSavedTabFallsBackToHome() {
        assertEquals("home", ActivityScreenState.restoreTab(null))
        assertEquals("home", ActivityScreenState.restoreTab(""))
        assertEquals("home", ActivityScreenState.restoreTab("unknown"))
    }

    @Test fun fallsBackToPersistedWorkspaceTabWhenActivityStateIsUnavailable() {
        assertEquals("activity", ActivityScreenState.restoreTab(null, "activity"))
        assertEquals("settings", ActivityScreenState.restoreTab("", "settings"))
        assertEquals("home", ActivityScreenState.restoreTab(null, "unknown"))
    }

    @Test fun savedActivityStateTakesPrecedenceOverPersistedTab() {
        assertEquals("protection", ActivityScreenState.restoreTab("protection", "activity"))
    }

    @Test fun restoresSettingsSubpagesAfterConfigurationChange() {
        assertEquals("settings-general", ActivityScreenState.restoreTab("settings-general"))
        assertEquals("settings-support", ActivityScreenState.restoreTab("settings-support"))
        assertEquals("settings-info", ActivityScreenState.restoreTab("settings-info"))
    }

    @Test fun restoresLicenseActivationAfterConfigurationChange() {
        assertEquals("license-activation", ActivityScreenState.restoreTab("license-activation"))
        assertEquals("license-activation", ActivityScreenState.restoreTab(null, "license-activation"))
    }
}
