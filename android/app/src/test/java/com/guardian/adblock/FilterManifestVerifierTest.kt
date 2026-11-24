package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class FilterManifestVerifierTest {
    private fun keyPair(bits: Int = 2048): KeyPair = KeyPairGenerator.getInstance("RSA").apply {
        initialize(bits)
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

    @Test fun rejectsNonStandardTlsPortEvenWhenManifestIsSigned() {
        val pair = keyPair()
        val bytes = String(payload(), StandardCharsets.UTF_8)
            .replace("https://filters.example.test/base.txt", "https://filters.example.test:8443/base.txt")
            .toByteArray(StandardCharsets.UTF_8)
        try {
            FilterManifestVerifier.parseAndVerify(
                bytes, signed(bytes, pair), Base64.getEncoder().encodeToString(pair.public.encoded)
            )
            fail("non-standard TLS port must be rejected")
        } catch (_: IllegalArgumentException) { }
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
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
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
    @Test fun rejectsWeakRsaSigningKey() {
        val pair = keyPair(1024)
        val bytes = payload()
        try {
            FilterManifestVerifier.parseAndVerify(
                bytes, signed(bytes, pair), Base64.getEncoder().encodeToString(pair.public.encoded)
            )
            fail("RSA keys below 2048 bits must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsOlderOrReplayedManifestTimestamp() {
        val pair = keyPair()
        val bytes = payload()
        try {
            FilterManifestVerifier.parseAndVerify(
                bytes, signed(bytes, pair), Base64.getEncoder().encodeToString(pair.public.encoded),
                nowEpochSeconds = 1794650000L,
                minimumGeneratedAtExclusive = 1794650000L
            )
            fail("replayed manifest must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsManifestGeneratedTooFarInTheFuture() {
        val pair = keyPair()
        val bytes = String(payload(), StandardCharsets.UTF_8)
            .replace("generatedAt=1794650000", "generatedAt=1900000000")
            .toByteArray(StandardCharsets.UTF_8)
        try {
            FilterManifestVerifier.parseAndVerify(
                bytes, signed(bytes, pair), Base64.getEncoder().encodeToString(pair.public.encoded),
                nowEpochSeconds = 1794650000L
            )
            fail("future-dated manifest must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsUnsupportedSourceIdCharactersEvenWhenSigned() {
        val pair = keyPair()
        val publicKey = Base64.getEncoder().encodeToString(pair.public.encoded)
        listOf("../escape", "source/name", "source.name", ".", "..").forEach { sourceId ->
            val bytes = String(payload(), StandardCharsets.UTF_8)
                .replace("sourceId=base-adblock", "sourceId=$sourceId")
                .toByteArray(StandardCharsets.UTF_8)
            try {
                FilterManifestVerifier.parseAndVerify(bytes, signed(bytes, pair), publicKey)
                fail("unsupported source id must be rejected: $sourceId")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun rejectsBlankSourceVersionEvenWhenSigned() {
        val pair = keyPair()
        val bytes = String(payload(), StandardCharsets.UTF_8)
            .replace("sourceVersion=2026.11.14", "sourceVersion=")
            .toByteArray(StandardCharsets.UTF_8)
        try {
            FilterManifestVerifier.parseAndVerify(
                bytes, signed(bytes, pair), Base64.getEncoder().encodeToString(pair.public.encoded)
            )
            fail("blank source version must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsUnsafeSourceVersionPathSegmentsEvenWhenSigned() {
        val pair = keyPair()
        val publicKey = Base64.getEncoder().encodeToString(pair.public.encoded)
        listOf("../escape", "version/one", ".", "..", "version with spaces").forEach { version ->
            val bytes = String(payload(), StandardCharsets.UTF_8)
                .replace("sourceVersion=2026.11.14", "sourceVersion=$version")
                .toByteArray(StandardCharsets.UTF_8)
            try {
                FilterManifestVerifier.parseAndVerify(bytes, signed(bytes, pair), publicKey)
                fail("unsafe source version must be rejected: $version")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun acceptsSafeSourceVersionCharacters() {
        val pair = keyPair()
        val bytes = String(payload(), StandardCharsets.UTF_8)
            .replace("sourceVersion=2026.11.14", "sourceVersion=release_2026.11-15+1")
            .toByteArray(StandardCharsets.UTF_8)
        val manifest = FilterManifestVerifier.parseAndVerify(
            bytes, signed(bytes, pair), Base64.getEncoder().encodeToString(pair.public.encoded)
        )
        assertEquals("release_2026.11-15+1", manifest.sourceVersion)
    }

    @Test fun acceptsSameSourceVersionOnlyWhenContentHashIsUnchanged() {
        val hash = "a".repeat(64)
        FilterManifestVerifier.requireImmutableSourceVersion(
            "2026.11.15", hash, "2026.11.15", hash
        )
        FilterManifestVerifier.requireImmutableSourceVersion(
            "2026.11.15", hash.uppercase(), "2026.11.15", hash
        )
        try {
            FilterManifestVerifier.requireImmutableSourceVersion(
                "2026.11.15", hash, "2026.11.15", "b".repeat(64)
            )
            fail("same sourceVersion with different content must be rejected")
        } catch (_: IllegalArgumentException) { }
        try {
            FilterManifestVerifier.requireImmutableSourceVersion(
                "2026.11.15", null, "2026.11.15", hash
            )
            fail("same sourceVersion without a stored hash must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun allowsAChangedHashOnlyForANewSourceVersion() {
        FilterManifestVerifier.requireImmutableSourceVersion(
            "2026.11.14", "a".repeat(64), "2026.11.15", "b".repeat(64)
        )
    }

    @Test fun rejectsMalformedUtf8ManifestEvenWhenSigned() {
        val pair = keyPair()
        val malformed = payload() + byteArrayOf(0xC3.toByte())
        try {
            FilterManifestVerifier.parseAndVerify(
                malformed, signed(malformed, pair),
                Base64.getEncoder().encodeToString(pair.public.encoded)
            )
            fail("malformed UTF-8 manifest must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun strictUtf8DecoderRejectsMalformedFilterPayload() {
        try {
            FilterManifestVerifier.decodeUtf8Strict(
                byteArrayOf(0x62, 0x6c, 0x6f, 0x63, 0x6b, 0x3d, 0xC3.toByte())
            )
            fail("malformed UTF-8 filter payload must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

}
