package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class SignedManifestHttpClientTest {
    @Test
    fun refusesPlainHttpBeforeOpeningAnyConnection() {
        try {
            SignedManifestHttpClient().download(
                "http://example.invalid/adguard.manifest",
                "adguard",
                "not-a-key",
                0L
            )
            throw AssertionError("plain HTTP manifest URL must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals(
                "manifest 및 필터 URL은 사용자 정보가 없는 HTTPS 주소여야 합니다.",
                expected.message
            )
        }
    }

    @Test
    fun refusesManifestUrlContainingUserInfo() {
        try {
            SignedManifestHttpClient().download(
                "https://user:password@example.invalid/adguard.manifest",
                "adguard",
                "not-a-key",
                0L
            )
            throw AssertionError("URL userinfo must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals(
                "manifest 및 필터 URL은 사용자 정보가 없는 HTTPS 주소여야 합니다.",
                expected.message
            )
        }
    }
    @Test
    fun resolvesVersionBoundSidecarOnStaticCdn() {
        val manifest = """
            manifestVersion=1
            generatedAt=1794900000
            sourceId=adguard
            sourceVersion=2026.11.17-ab12cd34
            url=https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-filter-data/filters/adguard/2026.11.17-ab12cd34.txt
            sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
            minimumRules=1000
        """.trimIndent().plus("\n").toByteArray(Charsets.UTF_8)

        assertEquals(
            "https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-filter-data/adguard.2026.11.17-ab12cd34.1794900000.sig",
            detachedManifestSignatureUrl(
                "https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-filter-data/adguard.manifest",
                manifest,
                "adguard"
            )
        )
    }

    @Test
    fun refusesSidecarPathForDifferentSource() {
        val manifest = """
            generatedAt=1794900000
            sourceId=oisd_big
            sourceVersion=2026.11.17
        """.trimIndent().toByteArray(Charsets.UTF_8)
        try {
            detachedManifestSignatureUrl(
                "https://raw.githubusercontent.com/owner/repo/branch/adguard.manifest",
                manifest,
                "adguard"
            )
            throw AssertionError("source mismatch must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("manifest 서명 파일 경로가 올바르지 않습니다.", expected.message)
        }
    }

    @Test
    fun refusesUnsafeSignatureVersionPath() {
        val manifest = """
            generatedAt=1794900000
            sourceId=adguard
            sourceVersion=../latest
        """.trimIndent().toByteArray(Charsets.UTF_8)
        try {
            detachedManifestSignatureUrl(
                "https://raw.githubusercontent.com/owner/repo/branch/adguard.manifest",
                manifest,
                "adguard"
            )
            throw AssertionError("unsafe version must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("manifest 서명 파일 경로가 올바르지 않습니다.", expected.message)
        }
    }

}
