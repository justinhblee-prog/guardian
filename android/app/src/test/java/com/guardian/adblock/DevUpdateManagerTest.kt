package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevUpdateManagerTest {
    @Test fun acceptsOnlyNewerReleaseWhoseTagMatchesManifest() {
        assertTrue(DevUpdateManagerPolicy.isNewerMatchingRelease(47, 48, 48))
        assertTrue(DevUpdateManager.isNewerMatchingRelease(47, 120, 120))
    }

    @Test fun rejectsSameOrOlderInstalledVersion() {
        assertFalse(DevUpdateManager.isNewerMatchingRelease(48, 48, 48))
        assertFalse(DevUpdateManager.isNewerMatchingRelease(49, 48, 48))
    }

    @Test fun rejectsTagAndManifestVersionMismatch() {
        assertFalse(DevUpdateManager.isNewerMatchingRelease(47, 49, 48))
        assertFalse(DevUpdateManager.isNewerMatchingRelease(47, 48, 49))
    }
    @Test fun refusesRedirectTargetsOutsideTheHttpsHostAllowlist() {
        assertTrue(DevUpdateManagerPolicy.isAllowedReleaseDownloadUrl("https://release-assets.githubusercontent.com/release/apk"))
        assertTrue(DevUpdateManager.isAllowedReleaseDownloadUrl("https://objects.githubusercontent.com/release/apk"))
        assertFalse(DevUpdateManager.isAllowedReleaseDownloadUrl("https://evil.example/release/apk"))
        assertFalse(DevUpdateManager.isAllowedReleaseDownloadUrl("http://release-assets.githubusercontent.com/release/apk"))
        assertFalse(DevUpdateManager.isAllowedReleaseDownloadUrl("https://github.com:444/release/apk"))
        assertFalse(DevUpdateManager.isAllowedReleaseDownloadUrl("https://api.github.com/repos/guardian"))
    }

    @Test fun developmentManifestMustMatchExactlyOneApkContentSigner() {
        val signer = "a".repeat(64)
        assertTrue(DevUpdateManagerPolicy.hasSingleExpectedSigner(listOf(signer), signer))
        assertTrue(DevUpdateManagerPolicy.hasSingleExpectedSigner(listOf(signer.uppercase()), signer))
        assertFalse(DevUpdateManagerPolicy.hasSingleExpectedSigner(emptyList(), signer))
        assertFalse(DevUpdateManagerPolicy.hasSingleExpectedSigner(listOf(signer, "b".repeat(64)), signer))
        assertFalse(DevUpdateManagerPolicy.hasSingleExpectedSigner(listOf(signer), "invalid"))
    }

    @Test fun acceptsOnlyOfficialHttpsReleaseAssetUrls() {
        assertTrue(DevUpdateManagerPolicy.isGuardianReleaseAssetUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk",
            "guardian-android-dev.apk", 48
        ))
        assertTrue(DevUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.json",
            "guardian-android-dev.json", 48
        ))
        assertFalse(DevUpdateManager.isGuardianReleaseAssetUrl(
            "http://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk"
        ))
        assertFalse(DevUpdateManager.isGuardianReleaseAssetUrl(
            "https://evil.example/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk"
        ))
        assertFalse(DevUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com.attacker.example/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk"
        ))
        assertFalse(DevUpdateManager.isGuardianReleaseAssetUrl(
            "https://user@github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk"
        ))
        assertFalse(DevUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com:444/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk"
        ))
        assertFalse(DevUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/other.apk"
        ))
        assertFalse(DevUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v49/guardian-android-dev.apk",
            "guardian-android-dev.apk", 48
        ))
    }

}
