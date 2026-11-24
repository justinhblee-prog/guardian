package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class SecurityProfilePolicyTest {
    private fun network(
        transport: SecurityProfilePolicy.Transport = SecurityProfilePolicy.Transport.WIFI,
        validated: Boolean = true,
        captive: Boolean = false,
        metered: Boolean = false,
        roaming: Boolean = false,
        trusted: Boolean = false
    ) = SecurityProfilePolicy.NetworkContext(transport, validated, captive, metered, roaming, trusted)

    @Test fun unknownWifiDefaultsToPublicWifiProfile() {
        assertEquals(
            SecurityProfilePolicy.Id.PUBLIC_WIFI,
            SecurityProfilePolicy.resolveAutomatic(network()).id
        )
    }

    @Test fun trustedWifiUsesHomeProfile() {
        assertEquals(
            SecurityProfilePolicy.Id.HOME,
            SecurityProfilePolicy.resolveAutomatic(network(trusted = true)).id
        )
    }

    @Test fun captivePortalAndUnvalidatedNetworksUseStrictProfile() {
        val captivePortal = SecurityProfilePolicy.resolveAutomatic(network(captive = true))
        assertEquals(SecurityProfilePolicy.Id.STRICT, captivePortal.id)
        assertFalse(captivePortal.automaticFilterUpdates)
        val unvalidated = SecurityProfilePolicy.resolveAutomatic(network(validated = false))
        assertEquals(SecurityProfilePolicy.Id.STRICT, unvalidated.id)
        assertFalse(unvalidated.automaticFilterUpdates)
    }

    @Test fun cellularRoamingUsesApi28CapabilityAndLegacyFallback() {
        assertTrue(SecurityProfilePolicy.isCellularRoaming(
            SecurityProfilePolicy.Transport.CELLULAR, 28, notRoamingCapabilityPresent = false,
            legacyRoaming = false
        ))
        assertFalse(SecurityProfilePolicy.isCellularRoaming(
            SecurityProfilePolicy.Transport.CELLULAR, 28, notRoamingCapabilityPresent = true,
            legacyRoaming = true
        ))
        assertTrue(SecurityProfilePolicy.isCellularRoaming(
            SecurityProfilePolicy.Transport.CELLULAR, 27, notRoamingCapabilityPresent = false,
            legacyRoaming = true
        ))
        assertFalse(SecurityProfilePolicy.isCellularRoaming(
            SecurityProfilePolicy.Transport.CELLULAR, 27, notRoamingCapabilityPresent = false,
            legacyRoaming = false
        ))
        assertFalse(SecurityProfilePolicy.isCellularRoaming(
            SecurityProfilePolicy.Transport.WIFI, 35, notRoamingCapabilityPresent = false,
            legacyRoaming = true
        ))
    }

    @Test fun roamingTakesPrecedenceOverTransportProfile() {
        assertEquals(
            SecurityProfilePolicy.Id.TRAVEL,
            SecurityProfilePolicy.resolveAutomatic(
                network(transport = SecurityProfilePolicy.Transport.CELLULAR, roaming = true)
            ).id
        )
    }

    @Test fun mobileAndTrustedEthernetResolveDeterministically() {
        assertEquals(
            SecurityProfilePolicy.Id.MOBILE,
            SecurityProfilePolicy.resolveAutomatic(
                network(transport = SecurityProfilePolicy.Transport.CELLULAR, metered = true)
            ).id
        )
        assertEquals(
            SecurityProfilePolicy.Id.WORK,
            SecurityProfilePolicy.resolveAutomatic(
                network(transport = SecurityProfilePolicy.Transport.ETHERNET, trusted = true)
            ).id
        )
    }

    @Test fun meteredNetworksDisableAutomaticFilterDownloads() {
        val result = SecurityProfilePolicy.resolveAutomatic(
            network(transport = SecurityProfilePolicy.Transport.CELLULAR, metered = true)
        )
        assertFalse(result.automaticFilterUpdates)
        val unmetered = SecurityProfilePolicy.resolveAutomatic(
            network(transport = SecurityProfilePolicy.Transport.CELLULAR, metered = false)
        )
        assertTrue(unmetered.automaticFilterUpdates)
    }

    @Test fun vpnUnderlyingNetworkMustBeUnambiguousBeforeAutomaticClassification() {
        assertFalse(SecurityProfilePolicy.hasUnambiguousUnderlyingNetwork(0))
        assertTrue(SecurityProfilePolicy.hasUnambiguousUnderlyingNetwork(1))
        assertFalse(SecurityProfilePolicy.hasUnambiguousUnderlyingNetwork(2))
        assertFalse(SecurityProfilePolicy.hasUnambiguousUnderlyingNetwork(-1))
    }

    @Test fun unknownTransportsNeverBecomeTrustedProfiles() {
        listOf(
            SecurityProfilePolicy.Transport.OTHER,
            SecurityProfilePolicy.Transport.UNKNOWN,
            SecurityProfilePolicy.Transport.ETHERNET
        ).forEach { transport ->
            assertEquals(
                SecurityProfilePolicy.Id.STRICT,
                SecurityProfilePolicy.resolveAutomatic(network(transport = transport)).id
            )
        }
    }

    @Test fun profileChangeNotificationDetectsIdAndUpdatePolicyChangesEvenWhenFiltersMatch() {
        val home = SecurityProfilePolicy.profile(SecurityProfilePolicy.Id.HOME)
        val mobile = SecurityProfilePolicy.profile(SecurityProfilePolicy.Id.MOBILE)
        assertEquals(home.filterProfileKey, mobile.filterProfileKey)
        assertTrue(SecurityProfilePolicy.hasProfileStateChanged(home.id.name, true, mobile))
        assertFalse(SecurityProfilePolicy.hasProfileStateChanged(home.id.name, true, home))
        assertTrue(SecurityProfilePolicy.hasProfileStateChanged(home.id.name, false, home))
    }

    @Test fun queuedAutomaticNetworkCallbacksAreIgnoredAfterManualModeIsSelected() {
        assertTrue(SecurityProfilePolicy.shouldApplyAutomaticProfile(true))
        assertFalse(SecurityProfilePolicy.shouldApplyAutomaticProfile(false))
    }

    @Test fun unconfiguredProfileUsesExistingStrictProfileUpdateDefault() {
        assertTrue(SecurityProfilePolicy.profile(SecurityProfilePolicy.Id.STRICT).automaticFilterUpdates)
        assertFalse(SecurityProfilePolicy.manualSelection(SecurityProfilePolicy.Id.STRICT).automaticFilterUpdates)
    }

    @Test fun scheduledUpdatesRequireBothUserOptInAndProfilePermission() {
        assertTrue(SecurityProfilePolicy.shouldScheduleAutomaticUpdates(true, true))
        assertFalse(SecurityProfilePolicy.shouldScheduleAutomaticUpdates(false, true))
        assertFalse(SecurityProfilePolicy.shouldScheduleAutomaticUpdates(true, false))
        assertFalse(SecurityProfilePolicy.shouldScheduleAutomaticUpdates(false, false))
    }

    @Test fun manualSelectionDoesNotAssumeNetworkIsUnmeteredForFilterDownloads() {
        SecurityProfilePolicy.Id.values().forEach { id ->
            val manual = SecurityProfilePolicy.manualSelection(id)
            assertEquals(id, manual.id)
            assertFalse(manual.automaticFilterUpdates)
        }
    }

    @Test fun manualProfileChoicesExposeAllSixDistinctUserFacingProfiles() {
        val labels = SecurityProfilePolicy.Id.values()
            .map { SecurityProfilePolicy.profile(it).label }
        assertEquals(6, labels.size)
        assertEquals(6, labels.toSet().size)
        assertTrue(labels.containsAll(listOf("집", "업무", "공용 Wi-Fi", "모바일 데이터", "여행", "엄격 보호")))
    }

    @Test fun everyProfileUsesAnExistingFilterProfileKey() {
        val available = FilterListManager.availableProfiles().map { it.key }.toSet()
        SecurityProfilePolicy.Id.values().forEach { id ->
            assertTrue(
                "Unknown filter profile for $id",
                SecurityProfilePolicy.profile(id).filterProfileKey in available
            )
        }
    }
}
