package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class LicenseActivationNavigationPolicyTest {
    @Test fun focusedActivationReturnsToPremiumInsteadOfSettingsRoot() {
        assertTrue(
            LicenseActivationNavigationPolicy.shouldReturnToPremium(
                "settings-premium", enteredFromPremium = true
            )
        )
    }

    @Test fun ordinarySettingsNavigationKeepsExistingBackBehavior() {
        assertFalse(
            LicenseActivationNavigationPolicy.shouldReturnToPremium(
                "settings-general", enteredFromPremium = false
            )
        )
        assertFalse(
            LicenseActivationNavigationPolicy.shouldReturnToPremium(
                "settings-premium", enteredFromPremium = false
            )
        )
    }

    @Test fun premiumReturnIsNotAppliedToOtherScreens() {
        assertFalse(
            LicenseActivationNavigationPolicy.shouldReturnToPremium(
                "home", enteredFromPremium = true
            )
        )
    }
}
