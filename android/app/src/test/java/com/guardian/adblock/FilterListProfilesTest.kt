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
    @Test fun cacheMustExistAndMeetTheSelectedProfilesMinimumRuleCount() {
        assertFalse(FilterListManager.isCacheValid(false, 5000, 1000))
        assertFalse(FilterListManager.isCacheValid(true, 999, 1000))
        assertTrue(FilterListManager.isCacheValid(true, 1000, 1000))
        assertFalse(FilterListManager.isCacheValid(true, 100, 0))
    }

    @Test fun protectionRequiresCacheForTheCurrentlySelectedProfile() {
        assertTrue(FilterListManager.isProfileCacheCurrent(true, "adguard", "adguard"))
        assertFalse(FilterListManager.isProfileCacheCurrent(false, "adguard", "adguard"))
        assertFalse(FilterListManager.isProfileCacheCurrent(true, "hagezi_pro", "adguard"))
    }
    @Test fun enabledAndroidAppAdsFilterRequiresItsOwnCachedList() {
        assertTrue(FilterListManager.isProfileCacheCurrent(true, "adguard", "adguard", false, false, false, false))
        assertTrue(FilterListManager.isProfileCacheCurrent(true, "adguard", "adguard", false, true, true, true))
        assertFalse(FilterListManager.isProfileCacheCurrent(true, "adguard", "adguard", false, true, true, false))
    }

    @Test fun androidAppAdsFilterUsesOfficialAwaVenueRegistrySource() {
        assertTrue(FilterListManager.ANDROID_ADS_URL.endsWith("/filter_53.txt"))
        assertTrue(FilterListManager.MIN_ANDROID_ADS_BLOCKS >= 100)
    }

    @Test fun enabledSecurityProtectionRequiresItsOwnCachedList() {
        assertTrue(FilterListManager.isProfileCacheCurrent(true, "adguard", "adguard", false, false))
        assertTrue(FilterListManager.isProfileCacheCurrent(true, "adguard", "adguard", true, true))
        assertFalse(FilterListManager.isProfileCacheCurrent(true, "adguard", "adguard", true, false))
    }

}
