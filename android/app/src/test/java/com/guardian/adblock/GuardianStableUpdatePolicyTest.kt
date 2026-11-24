package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class GuardianStableUpdatePolicyTest {
    @Test fun acceptsOnlyPublishedNonPrereleaseSemanticVersionTags() {
        assertTrue(GuardianStableUpdatePolicy.isStableRelease(false, false, "v1.0.0"))
        assertTrue(GuardianStableUpdatePolicy.isStableRelease(false, false, "v2.12.34"))
        assertFalse(GuardianStableUpdatePolicy.isStableRelease(true, false, "v1.0.0"))
        assertFalse(GuardianStableUpdatePolicy.isStableRelease(false, true, "v1.0.0"))
        assertFalse(GuardianStableUpdatePolicy.isStableRelease(false, false, "guardian-dev-v30"))
        assertFalse(GuardianStableUpdatePolicy.isStableRelease(false, false, "v1.0.0-rc1"))
        assertFalse(GuardianStableUpdatePolicy.isStableRelease(false, false, "v1.0"))
        assertFalse(GuardianStableUpdatePolicy.isStableRelease(false, false, "v01.0.0"))
        assertFalse(GuardianStableUpdatePolicy.isStableRelease(false, false, "v1.00.0"))
        assertFalse(GuardianStableUpdatePolicy.isStableRelease(false, false, "v1.0.00"))
    }

    @Test fun versionCodesRejectFractionsStringsAndIntegerOverflow() {
        assertEquals(1, GuardianStableUpdatePolicy.parseSupportedVersionCode(1))
        assertEquals(Int.MAX_VALUE, GuardianStableUpdatePolicy.parseSupportedVersionCode(Int.MAX_VALUE))
        assertNull(GuardianStableUpdatePolicy.parseSupportedVersionCode(null))
        assertNull(GuardianStableUpdatePolicy.parseSupportedVersionCode("62"))
        assertNull(GuardianStableUpdatePolicy.parseSupportedVersionCode(1.5))
        assertNull(GuardianStableUpdatePolicy.parseSupportedVersionCode(0))
        assertNull(GuardianStableUpdatePolicy.parseSupportedVersionCode(-1))
        assertNull(GuardianStableUpdatePolicy.parseSupportedVersionCode(Int.MAX_VALUE.toLong() + 1L))
        assertNull(GuardianStableUpdatePolicy.parseSupportedVersionCode(Double.NaN))
        assertNull(GuardianStableUpdatePolicy.parseSupportedVersionCode(Double.POSITIVE_INFINITY))

        assertEquals(62, GuardianStableUpdatePolicy.parseArchiveVersionCode(62L))
        assertEquals(Int.MAX_VALUE, GuardianStableUpdatePolicy.parseArchiveVersionCode(Int.MAX_VALUE.toLong()))
        assertNull(GuardianStableUpdatePolicy.parseArchiveVersionCode(0L))
        assertNull(GuardianStableUpdatePolicy.parseArchiveVersionCode(-1L))
        assertNull(GuardianStableUpdatePolicy.parseArchiveVersionCode(Int.MAX_VALUE.toLong() + 1L))
    }

    @Test fun updateMustHaveStrictlyHigherVersionCode() {
        assertTrue(GuardianStableUpdatePolicy.isNewerVersion(30, 31))
        assertFalse(GuardianStableUpdatePolicy.isNewerVersion(30, 30))
        assertFalse(GuardianStableUpdatePolicy.isNewerVersion(30, 29))
    }

    @Test fun acceptsUnknownStreamingLengthButEnforcesActualByteBounds() {
        val maximum = 150L * 1024L * 1024L
        assertTrue(GuardianStableUpdatePolicy.isAcceptableDownloadLength(-1L, maximum))
        assertTrue(GuardianStableUpdatePolicy.isAcceptableDownloadLength(1024L, maximum))
        assertFalse(GuardianStableUpdatePolicy.isAcceptableDownloadLength(0L, maximum))
        assertFalse(GuardianStableUpdatePolicy.isAcceptableDownloadLength(maximum + 1L, maximum))
        assertFalse(GuardianStableUpdatePolicy.isAcceptableDownloadLength(-1L, 0L))

        assertTrue(GuardianStableUpdatePolicy.matchesDownloadedLength(-1L, 1024L, maximum))
        assertTrue(GuardianStableUpdatePolicy.matchesDownloadedLength(1024L, 1024L, maximum))
        assertFalse(GuardianStableUpdatePolicy.matchesDownloadedLength(2048L, 1024L, maximum))
        assertFalse(GuardianStableUpdatePolicy.matchesDownloadedLength(-1L, 0L, maximum))
        assertFalse(GuardianStableUpdatePolicy.matchesDownloadedLength(-1L, maximum + 1L, maximum))
    }

    @Test fun semanticReleaseOrderingDoesNotDependOnGithubPublicationOrder() {
        assertTrue(GuardianStableUpdatePolicy.compareVersionTags("v1.10.0", "v1.9.9") > 0)
        assertTrue(GuardianStableUpdatePolicy.compareVersionTags("v2.0.0", "v1.99.99") > 0)
        assertEquals(0, GuardianStableUpdatePolicy.compareVersionTags("v1.2.3", "v1.2.3"))
        assertTrue(GuardianStableUpdatePolicy.compareVersionTags("v1.2.3", "v1.2.4") < 0)
    }

    @Test fun rejectsUnboundedVersionComponents() {
        assertFalse(GuardianStableUpdatePolicy.isStableRelease(
            draft = false,
            prerelease = false,
            tag = "v999999999999999999999.0.0"
        ))
    }

    @Test fun sha256MustBeExactly64HexCharacters() {
        assertTrue(GuardianStableUpdatePolicy.isSha256("a".repeat(64)))
        assertTrue(GuardianStableUpdatePolicy.isSha256("A".repeat(64)))
        assertFalse(GuardianStableUpdatePolicy.isSha256("a".repeat(63)))
        assertFalse(GuardianStableUpdatePolicy.isSha256("g".repeat(64)))
    }

    @Test fun stableApkRedirectsMustRemainOnOfficialHttpsAssetHosts() {
        assertTrue(GuardianStableUpdateManager.isAllowedReleaseDownloadUrl(
            "https://release-assets.githubusercontent.com/github-production-release-asset/guardian.apk"
        ))
        assertTrue(GuardianStableUpdateManager.isAllowedReleaseDownloadUrl(
            "https://objects.githubusercontent.com/release/guardian.apk"
        ))
        assertFalse(GuardianStableUpdateManager.isAllowedReleaseDownloadUrl(
            "https://evil.example/guardian.apk"
        ))
        assertFalse(GuardianStableUpdateManager.isAllowedReleaseDownloadUrl(
            "http://release-assets.githubusercontent.com/guardian.apk"
        ))
        assertFalse(GuardianStableUpdateManager.isAllowedReleaseDownloadUrl(
            "https://api.github.com/repos/guardian"
        ))
        assertFalse(GuardianStableUpdateManager.isAllowedReleaseDownloadUrl(
            "https://user@github.com/guardian.apk"
        ))
        assertFalse(GuardianStableUpdateManager.isAllowedReleaseDownloadUrl(
            "https://github.com:444/guardian.apk"
        ))
        assertFalse(GuardianStableUpdateManager.isAllowedReleaseDownloadUrl(
            "https://github.com/attacker/repository/releases/download/v1/guardian.apk"
        ))
        assertFalse(GuardianStableUpdateManager.isAllowedReleaseDownloadUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/v1/guardian.apk#fragment"
        ))
        assertFalse(GuardianStableUpdateManager.isAllowedReleaseDownloadUrl(
            "https://release-assets.githubusercontent.com/#fragment"
        ))
        assertFalse(GuardianStableUpdateManager.isAllowedReleaseDownloadUrl(
            "https://objects.githubusercontent.com/"
        ))
        assertTrue(GuardianStableUpdateManager.isAllowedReleaseDownloadUrl(
            "https://release-assets.githubusercontent.com/github-production-release-asset/guardian.apk?token=temporary"
        ))
    }

    @Test fun releaseAssetMustMatchExactOfficialTagAndAssetPath() {
        val base = "https://github.com/justinhblee-prog/guardian/releases/download/v1.2.3/"
        assertTrue(GuardianStableUpdatePolicy.isOfficialReleaseAssetUrl(
            base + "guardian-android.json", "v1.2.3", "guardian-android.json"
        ))
        assertTrue(GuardianStableUpdatePolicy.isOfficialReleaseAssetUrl(
            base + "guardian-android.apk", "v1.2.3", "guardian-android.apk"
        ))
        assertFalse(GuardianStableUpdatePolicy.isOfficialReleaseAssetUrl(
            base + "guardian-android.apk", "v1.2.4", "guardian-android.apk"
        ))
        assertFalse(GuardianStableUpdatePolicy.isOfficialReleaseAssetUrl(
            base + "other.apk", "v1.2.3", "guardian-android.apk"
        ))
        assertFalse(GuardianStableUpdatePolicy.isOfficialReleaseAssetUrl(
            base + "guardian-android.apk/extra", "v1.2.3", "guardian-android.apk"
        ))
        assertFalse(GuardianStableUpdatePolicy.isOfficialReleaseAssetUrl(
            base + "guardian-android.apk?download=1", "v1.2.3", "guardian-android.apk"
        ))
        assertFalse(GuardianStableUpdatePolicy.isOfficialReleaseAssetUrl(
            "https://evil.example/justinhblee-prog/guardian/releases/download/v1.2.3/guardian-android.apk",
            "v1.2.3", "guardian-android.apk"
        ))
        assertFalse(GuardianStableUpdatePolicy.isOfficialReleaseAssetUrl(
            base + "guardian-android.apk", "v1.2.3-rc1", "guardian-android.apk"
        ))
    }
}
