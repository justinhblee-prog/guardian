package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityScreenStateTest {
    @Test fun restoresEverySupportedWorkspaceTab() {
        listOf("home", "protection", "activity", "rules", "settings").forEach { tab ->
            assertEquals(tab, ActivityScreenState.restoreTab(tab))
        }
    }

    @Test fun missingOrUnknownSavedTabFallsBackToHome() {
        assertEquals("home", ActivityScreenState.restoreTab(null))
        assertEquals("home", ActivityScreenState.restoreTab(""))
        assertEquals("home", ActivityScreenState.restoreTab("unknown"))
    }
}
