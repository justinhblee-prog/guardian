package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityScreenStateTest {
    @Test fun restoresEverySupportedWorkspaceTab() {
        listOf("home", "protection", "app-exclusions", "activity", "settings", "settings-general", "settings-filtering", "settings-premium", "settings-support", "settings-about", "settings-exit").forEach { tab ->
            assertEquals(tab, ActivityScreenState.restoreTab(tab))
        }
        assertEquals("settings-premium", ActivityScreenState.restoreTab("premium"))
        assertEquals("settings-filtering", ActivityScreenState.restoreTab("rules"))
    }

    @Test fun missingOrUnknownSavedTabFallsBackToHome() {
        assertEquals("home", ActivityScreenState.restoreTab(null))
        assertEquals("home", ActivityScreenState.restoreTab(""))
        assertEquals("home", ActivityScreenState.restoreTab("unknown"))
    }
}
