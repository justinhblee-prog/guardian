package com.guardian.adblock

/** Navigation contract for the focused license activation entry from the Premium screen. */
internal object LicenseActivationNavigationPolicy {
    const val PREMIUM_SCREEN = "settings-premium"

    fun shouldReturnToPremium(currentTab: String, enteredFromPremium: Boolean): Boolean =
        currentTab == PREMIUM_SCREEN && enteredFromPremium
}
