package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityScreenStateTest {
    @Test fun restoresEverySupportedWorkspaceTab() {
        listOf(
            "home", "protection", "app-exclusions", "activity", "premium", "rules", "settings",
            "settings-premium", "settings-license", "settings-license-activation",
            "settings-filtering", "settings-support", "settings-about",
            "settings-general", "settings-advanced"
        ).forEach { tab ->
            assertEquals(tab, ActivityScreenState.restoreTab(tab))
        }
    }

    @Test fun missingOrUnknownSavedTabFallsBackToHome() {
        assertEquals("home", ActivityScreenState.restoreTab(null))
        assertEquals("home", ActivityScreenState.restoreTab(""))
        assertEquals("home", ActivityScreenState.restoreTab("unknown"))
        assertEquals("home", ActivityScreenState.restoreTab("settings-not-a-real-page"))
    }

    @Test fun restoresPersistedSettingsSubmenuWhenSavedInstanceStateIsUnavailable() {
        assertEquals("settings-premium", ActivityScreenState.restoreTab(null, "settings-premium"))
        assertEquals("settings-license", ActivityScreenState.restoreTab("", "settings-license"))
        assertEquals("settings-filtering", ActivityScreenState.restoreTab(null, "settings-filtering"))
    }

    @Test fun savedInstanceTabTakesPrecedenceOverPersistedTab() {
        assertEquals("settings-about", ActivityScreenState.restoreTab("settings-about", "settings-premium"))
    }

    @Test fun invalidPersistedTabFallsBackToHome() {
        assertEquals("home", ActivityScreenState.restoreTab(null, "settings-not-a-real-page"))
    }
}
