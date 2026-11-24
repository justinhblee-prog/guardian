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
    }

    @Test fun updateMustHaveStrictlyHigherVersionCode() {
        assertTrue(GuardianStableUpdatePolicy.isNewerVersion(30, 31))
        assertFalse(GuardianStableUpdatePolicy.isNewerVersion(30, 30))
        assertFalse(GuardianStableUpdatePolicy.isNewerVersion(30, 29))
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

    @Test fun releaseManifestMustMatchExactlyOneApkContentSigner() {
        val signer = "a".repeat(64)
        assertTrue(GuardianStableUpdatePolicy.hasSingleExpectedSigner(listOf(signer), signer))
        assertTrue(GuardianStableUpdatePolicy.hasSingleExpectedSigner(listOf(signer.uppercase()), signer))
        assertFalse(GuardianStableUpdatePolicy.hasSingleExpectedSigner(emptyList(), signer))
        assertFalse(GuardianStableUpdatePolicy.hasSingleExpectedSigner(listOf(signer, "b".repeat(64)), signer))
        assertFalse(GuardianStableUpdatePolicy.hasSingleExpectedSigner(listOf(signer), "not-a-sha256"))
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
