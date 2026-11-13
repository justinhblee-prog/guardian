package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class FilterListProfilesTest {
    @Test fun exposesDistinctOfficialDnsFilterProfiles() {
        val profiles = FilterListManager.availableProfiles()
        assertEquals(5, profiles.size)
        assertEquals(5, profiles.map { it.key }.distinct().size)
        assertEquals("adguard", profiles.first().key)
        assertTrue(profiles.all { it.url.startsWith("https://adguardteam.github.io/HostlistsRegistry/assets/filter_") })
    }

    @Test fun everyProfileHasAReasonableMinimumValidationThreshold() {
        FilterListManager.availableProfiles().forEach { profile ->
            assertTrue(profile.minimumRules >= 100)
            assertTrue(profile.label.isNotBlank())
        }
    }

    @Test fun strictProfilesAreAvailableAsExplicitChoices() {
        val keys = FilterListManager.availableProfiles().map { it.key }.toSet()
        assertTrue("oisd_big" in keys)
        assertTrue("hagezi_normal" in keys)
        assertTrue("hagezi_pro" in keys)
    }
}
