package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class GooglePlayBillingManagerTest {
    @Test
    fun supportsOnlyRecurringSubscriptionOffers() {
        assertTrue(isSupportedGuardianSubscriptionRecurrenceMode(
            com.android.billingclient.api.ProductDetails.RecurrenceMode.INFINITE_RECURRING
        ))
        assertTrue(isSupportedGuardianSubscriptionRecurrenceMode(
            com.android.billingclient.api.ProductDetails.RecurrenceMode.FINITE_RECURRING
        ))
        assertFalse(isSupportedGuardianSubscriptionRecurrenceMode(
            com.android.billingclient.api.ProductDetails.RecurrenceMode.NON_RECURRING
        ))
    }

    @Test
    fun parsesConfiguredProductIdsAndTrimsWhitespace() {
        assertEquals(
            listOf("guardian_pro_monthly", "guardian_pro_annual"),
            parseGuardianPlayProductIds(" guardian_pro_monthly, guardian_pro_annual ")
        )
    }

    @Test
    fun emptyProductConfigurationDisablesPlayOffers() {
        assertEquals(emptyList<String>(), parseGuardianPlayProductIds(" ,  "))
    }

    @Test
    fun rejectsMalformedProductIds() {
        assertThrows(IllegalArgumentException::class.java) {
            parseGuardianPlayProductIds("guardian_pro_monthly,../attacker")
        }
    }

    @Test
    fun rejectsDuplicateProductIds() {
        assertThrows(IllegalArgumentException::class.java) {
            parseGuardianPlayProductIds("guardian_pro_monthly,guardian_pro_monthly")
        }
    }

    @Test
    fun rejectsMoreThanTenSubscriptionProducts() {
        assertThrows(IllegalArgumentException::class.java) {
            parseGuardianPlayProductIds((1..11).joinToString(",") { "guardian_pro_$it" })
        }
    }
}


    @Test
    fun staleBillingCallbacksCannotLaunchAgainstDestroyedActivity() {
        assertTrue(shouldLaunchGuardianBillingFlow(false, false, false))
        assertFalse(shouldLaunchGuardianBillingFlow(true, false, false))
        assertFalse(shouldLaunchGuardianBillingFlow(false, true, false))
        assertFalse(shouldLaunchGuardianBillingFlow(false, false, true))
        assertFalse(shouldLaunchGuardianBillingFlow(true, true, true))
    }
