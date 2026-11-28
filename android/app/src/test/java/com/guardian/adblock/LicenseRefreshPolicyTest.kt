package com.guardian.adblock

import java.security.KeyPairGenerator
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseRefreshPolicyTest {
    private fun validPublicKey(): String {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        return Base64.getEncoder().encodeToString(generator.generateKeyPair().public.encoded)
    }

    @Test fun runtimeConfiguredServerRequiresValidHttpsOriginAndRsaKey() {
        val key = validPublicKey()
        assertTrue(LicenseRefreshScheduler.isConfigured("https://laptop.example.ts.net", key))
        assertFalse(LicenseRefreshScheduler.isConfigured("", key))
        assertFalse(LicenseRefreshScheduler.isConfigured("https://laptop.example.ts.net", ""))
        assertFalse(LicenseRefreshScheduler.isConfigured("http://laptop.example.ts.net", key))
        assertFalse(LicenseRefreshScheduler.isConfigured("https://user@laptop.example.ts.net", key))
        assertFalse(LicenseRefreshScheduler.isConfigured("https://laptop.example.ts.net/path", key))
        assertFalse(LicenseRefreshScheduler.isConfigured("https://laptop.example.ts.net", "not-a-public-key"))
    }

    @Test fun doesNotRenewWhenMoreThanTwelveHoursRemain() {
        val now = 1_800_000_000L
        assertFalse(LicenseRefreshScheduler.shouldRenew(now + 12L * 60L * 60L + 1L, now))
    }

    @Test fun renewsAtTwelveHourBoundary() {
        val now = 1_800_000_000L
        assertTrue(LicenseRefreshScheduler.shouldRenew(now + 12L * 60L * 60L, now))
    }

    @Test fun renewsExpiredEntitlementAfterOfflinePeriod() {
        val now = 1_800_000_000L
        assertTrue(LicenseRefreshScheduler.shouldRenew(now - 1L, now))
    }

    @Test fun retriesOnlyTransientHttpFailures() {
        assertTrue(LicenseRefreshScheduler.shouldRetryHttpStatus(408))
        assertTrue(LicenseRefreshScheduler.shouldRetryHttpStatus(429))
        assertTrue(LicenseRefreshScheduler.shouldRetryHttpStatus(500))
        assertTrue(LicenseRefreshScheduler.shouldRetryHttpStatus(503))
        assertFalse(LicenseRefreshScheduler.shouldRetryHttpStatus(400))
        assertFalse(LicenseRefreshScheduler.shouldRetryHttpStatus(401))
        assertFalse(LicenseRefreshScheduler.shouldRetryHttpStatus(403))
        assertFalse(LicenseRefreshScheduler.shouldRetryHttpStatus(404))
    }
}
