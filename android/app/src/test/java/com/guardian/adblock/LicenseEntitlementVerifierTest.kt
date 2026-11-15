package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class LicenseEntitlementVerifierTest {
    private val device = "d".repeat(64)
    private val now = 1_794_650_000L

    private fun keyPair(bits: Int = 2048): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()

    private fun payload(
        issuedAt: Long = now - 60,
        expiresAt: Long = now + 86400,
        deviceBinding: String = device
    ): ByteArray = listOf(
        "tokenVersion=1",
        "product=guardian-android",
        "entitlement=pro",
        "issuedAt=$issuedAt",
        "expiresAt=$expiresAt",
        "licenseRef=lic_test_0001",
        "deviceBinding=$deviceBinding"
    ).joinToString("\n", postfix = "\n").toByteArray(StandardCharsets.UTF_8)

    private fun sign(bytes: ByteArray, pair: KeyPair): String {
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(pair.private)
        signer.update(bytes)
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    private fun verify(bytes: ByteArray, pair: KeyPair, expectedDevice: String = device) =
        LicenseEntitlementVerifier.verify(
            bytes, sign(bytes, pair), Base64.getEncoder().encodeToString(pair.public.encoded),
            expectedDevice, now
        )

    @Test fun acceptsValidServerSignedProEntitlement() {
        val pair = keyPair()
        val entitlement = verify(payload(), pair)
        assertEquals("guardian-android", entitlement.product)
        assertEquals("pro", entitlement.entitlement)
        assertEquals("lic_test_0001", entitlement.licenseRef)
        assertEquals(device, entitlement.deviceBinding)
    }

    @Test fun rejectsPayloadChangedAfterSigning() {
        val pair = keyPair()
        val original = payload()
        val signature = sign(original, pair)
        val modified = String(original, StandardCharsets.UTF_8)
            .replace("entitlement=pro", "entitlement=free").toByteArray(StandardCharsets.UTF_8)
        try {
            LicenseEntitlementVerifier.verify(
                modified, signature, Base64.getEncoder().encodeToString(pair.public.encoded), device, now
            )
            fail("modified payload must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsExpiredTokenAndFutureIssuedToken() {
        val pair = keyPair()
        listOf(
            payload(issuedAt = now - 90000, expiresAt = now - 1),
            payload(issuedAt = now + 301, expiresAt = now + 900)
        ).forEach { bytes ->
            try {
                verify(bytes, pair)
                fail("invalid token time must be rejected")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun expiredTokenIsAcceptedOnlyForExplicitRenewalLookup() {
        val pair = keyPair()
        val expired = payload(issuedAt = now - 90000, expiresAt = now - 1)
        try {
            verify(expired, pair)
            fail("expired token must not grant Pro access")
        } catch (_: IllegalArgumentException) { }
        val renewalCredential = LicenseEntitlementVerifier.verify(
            expired,
            sign(expired, pair),
            Base64.getEncoder().encodeToString(pair.public.encoded),
            device,
            now,
            allowExpired = true
        )
        assertEquals("lic_test_0001", renewalCredential.licenseRef)
        assertTrue(renewalCredential.expiresAt < now)
    }

    @Test fun rejectsTokenBoundToAnotherDevice() {
        val pair = keyPair()
        try {
            verify(payload(deviceBinding = "e".repeat(64)), pair)
            fail("different device binding must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsDuplicateUnknownAndMissingClaims() {
        val pair = keyPair()
        val valid = String(payload(), StandardCharsets.UTF_8)
        val invalid = listOf(
            valid.replace("entitlement=pro\n", "entitlement=pro\nentitlement=pro\n"),
            valid.replace("entitlement=pro", "unexpected=x\nentitlement=pro"),
            valid.replace("licenseRef=lic_test_0001\n", "")
        )
        invalid.forEach { value ->
            try {
                verify(value.toByteArray(StandardCharsets.UTF_8), pair)
                fail("invalid claims must be rejected")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun rejectsWeakRsaPublicKey() {
        val pair = keyPair(1024)
        try {
            verify(payload(), pair)
            fail("RSA keys below 2048 bits must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun rejectsMalformedUtf8EvenWhenTokenBytesAreSigned() {
        val pair = keyPair()
        val malformed = payload() + byteArrayOf(0xC3.toByte())
        try {
            LicenseEntitlementVerifier.verify(
                malformed,
                sign(malformed, pair),
                Base64.getEncoder().encodeToString(pair.public.encoded),
                device,
                now
            )
            fail("malformed UTF-8 token must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

}
