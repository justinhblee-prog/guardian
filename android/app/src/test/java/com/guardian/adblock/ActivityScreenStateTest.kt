package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityScreenStateTest {
    @Test fun restoresEverySupportedWorkspaceTab() {
        listOf(
            "home", "protection", "app-exclusions", "activity", "premium",
            "settings", "updates", "settings-general", "settings-filtering",
            "settings-premium", "settings-support", "settings-about", "settings-updates", "settings-exit"
        ).forEach { tab ->
            assertEquals(tab, ActivityScreenState.restoreTab(tab))
        }
    }

    @Test fun restoresLegacyRulesTabIntoFilteringSubmenu() {
        assertEquals("settings-filtering", ActivityScreenState.restoreTab("rules"))
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
}
