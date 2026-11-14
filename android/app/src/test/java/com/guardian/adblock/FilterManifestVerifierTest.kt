package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class FilterManifestVerifierTest {
    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048)
    }.generateKeyPair()

    private fun payload(): ByteArray {
        val hash = "a".repeat(64)
        return listOf(
            "manifestVersion=1",
            "generatedAt=1794650000",
            "sourceId=base-adblock",
            "sourceVersion=2026.11.14",
            "url=https://filters.example.test/base.txt",
            "sha256=$hash",
            "minimumRules=100"
        ).joinToString("\n", postfix="\n").toByteArray(StandardCharsets.UTF_8)
    }

    private fun signed(bytes: ByteArray, pair: KeyPair): String {
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(pair.private)
        signer.update(bytes)
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    @Test fun acceptsManifestOnlyWhenDetachedSignatureIsValid() {
        val pair = keyPair()
        val bytes = payload()
        val manifest = FilterManifestVerifier.parseAndVerify(
            bytes, signed(bytes, pair), Base64.getEncoder().encodeToString(pair.public.encoded)
        )
        assertEquals("base-adblock", manifest.sourceId)
        assertEquals(100, manifest.minimumRules)
        assertEquals("https://filters.example.test/base.txt", manifest.url)
    }

    @Test fun rejectsModifiedManifestAfterSignatureWasCreated() {
        val pair = keyPair()
        val bytes = payload()
        val signature = signed(bytes, pair)
        val modified = String(bytes, StandardCharsets.UTF_8)
            .replace("minimumRules=100", "minimumRules=1")
            .toByteArray(StandardCharsets.UTF_8)
        try {
            FilterManifestVerifier.parseAndVerify(
                modified, signature, Base64.getEncoder().encodeToString(pair.public.encoded)
            )
            fail("modified manifest must not verify")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsDuplicateAndUnknownManifestKeys() {
        val pair = keyPair()
        val duplicate = String(payload(), StandardCharsets.UTF_8)
            .replace("minimumRules=100\n", "minimumRules=100\nminimumRules=100\n")
            .toByteArray(StandardCharsets.UTF_8)
        val unknown = String(payload(), StandardCharsets.UTF_8)
            .replace("minimumRules=100", "unexpected=value\nminimumRules=100")
            .toByteArray(StandardCharsets.UTF_8)
        listOf(duplicate, unknown).forEach { bytes ->
            try {
                FilterManifestVerifier.parseAndVerify(
                    bytes, signed(bytes, pair), Base64.getEncoder().encodeToString(pair.public.encoded)
                )
                fail("invalid manifest must be rejected")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun rejectsHttpAndMalformedSha256EvenWhenSigned() {
        val pair = keyPair()
        val badUrl = String(payload(), StandardCharsets.UTF_8)
            .replace("https://", "http://").toByteArray(StandardCharsets.UTF_8)
        val badHash = String(payload(), StandardCharsets.UTF_8)
            .replace("a".repeat(64), "not-a-hash").toByteArray(StandardCharsets.UTF_8)
        listOf(badUrl, badHash).forEach { bytes ->
            try {
                FilterManifestVerifier.parseAndVerify(
                    bytes, signed(bytes, pair), Base64.getEncoder().encodeToString(pair.public.encoded)
                )
                fail("invalid signed manifest must be rejected")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun checksFilterContentSha256() {
        val bytes = "block=ads.example".toByteArray(StandardCharsets.UTF_8)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertTrue(FilterManifestVerifier.matchesSha256(bytes, digest))
        assertFalse(FilterManifestVerifier.matchesSha256(bytes, "0".repeat(64)))
        assertFalse(FilterManifestVerifier.matchesSha256(bytes, "invalid"))
    }

    @Test fun rejectsOversizedManifest() {
        val pair = keyPair()
        val bytes = ByteArray(16 * 1024 + 1)
        try {
            FilterManifestVerifier.parseAndVerify(
                bytes, signed(bytes, pair), Base64.getEncoder().encodeToString(pair.public.encoded)
            )
            fail("oversized manifest must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
