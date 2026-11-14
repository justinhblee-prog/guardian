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
}
