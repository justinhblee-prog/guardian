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

    @Test
    fun refusesManifestUrlWithQueryBeforeOpeningConnection() {
        try {
            SignedManifestHttpClient().download(
                "https://example.invalid/adguard.manifest?unexpected=value",
                "adguard",
                "not-a-key",
                0L
            )
            throw AssertionError("manifest URL query must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals(
                "manifest URL에는 query 또는 fragment를 사용할 수 없습니다.",
                expected.message
            )
        }
    }

    @Test
    fun rejectsDetachedSidecarManifestUrlsWithInvalidPortOrFragment() {
        val manifest = """
            manifestVersion=1
            generatedAt=1794900000
            sourceId=adguard
            sourceVersion=2026.11.17-ab12cd34
            url=https://raw.githubusercontent.com/owner/repo/filters.txt
            sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
            minimumRules=1000
        """.trimIndent().plus("\n").toByteArray(Charsets.UTF_8)
        listOf(
            "https://raw.githubusercontent.com:99999/owner/repo/adguard.manifest",
            "https://raw.githubusercontent.com/owner/repo/adguard.manifest#fragment"
        ).forEach { url ->
            try {
                detachedManifestSignatureUrl(url, manifest, "adguard")
                throw AssertionError("unsafe sidecar manifest URL must be rejected: $url")
            } catch (expected: IllegalArgumentException) {
                assertEquals(
                    "manifest URL은 유효한 호스트·포트의 사용자 정보·fragment가 없는 HTTPS 주소여야 합니다.",
                    expected.message
                )
            }
        }
    }

    @Test
    fun refusesManifestWithOutOfRangePortBeforeOpeningConnection() {
        try {
            SignedManifestHttpClient().download(
                "https://example.invalid:99999/adguard.manifest",
                "adguard",
                "not-a-key",
                0L
            )
            throw AssertionError("out-of-range manifest port must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals(
                "manifest 및 필터 URL은 유효한 호스트·포트의 사용자 정보·fragment가 없는 HTTPS 주소여야 합니다.",
                expected.message
            )
        }
    }

}
