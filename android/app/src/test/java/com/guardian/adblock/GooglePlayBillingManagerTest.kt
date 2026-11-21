package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GooglePlayBillingManagerTest {
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
