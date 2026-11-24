package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevUpdateManagerTest {
    @Test fun acceptsOnlyNewerReleaseWhoseTagMatchesManifest() {
        assertTrue(DevUpdateManager.isNewerMatchingRelease(47, 48, 48))
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
    @Test fun acceptsOnlyOfficialHttpsReleaseAssetUrls() {
        assertTrue(DevUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk"
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
            "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48"
        ))
    }

    @Test fun developmentApkRedirectsMustStayOnApprovedHttpsAssetHosts() {
        assertTrue(DevUpdateManager.isAllowedReleaseDownloadUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk"
        ))
        assertTrue(DevUpdateManager.isAllowedReleaseDownloadUrl(
            "https://release-assets.githubusercontent.com/github-production-release-asset/guardian.apk"
        ))
        assertTrue(DevUpdateManager.isAllowedReleaseDownloadUrl(
            "https://objects.githubusercontent.com/release/guardian.apk"
        ))
        assertFalse(DevUpdateManager.isAllowedReleaseDownloadUrl(
            "http://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk"
        ))
        assertFalse(DevUpdateManager.isAllowedReleaseDownloadUrl(
            "https://api.github.com/repos/justinhblee-prog/guardian/releases"
        ))
        assertFalse(DevUpdateManager.isAllowedReleaseDownloadUrl(
            "https://github.com.attacker.example/guardian.apk"
        ))
        assertFalse(DevUpdateManager.isAllowedReleaseDownloadUrl(
            "https://user@github.com/guardian.apk"
        ))
        assertFalse(DevUpdateManager.isAllowedReleaseDownloadUrl(
            "https://github.com:444/guardian.apk"
        ))
        assertFalse(DevUpdateManager.isAllowedReleaseDownloadUrl(
            "https://github.com/guardian.apk?redirect=https://evil.example"
        ))
        assertFalse(DevUpdateManager.isAllowedReleaseDownloadUrl(
            "https://github.com/attacker/repository/releases/download/v1/guardian.apk"
        ))
        assertFalse(DevUpdateManager.isAllowedReleaseDownloadUrl(
            "https://github.com:444/justinhblee-prog/guardian/releases/download/v1/guardian.apk"
        ))
        assertFalse(DevUpdateManager.isAllowedReleaseDownloadUrl(
            "https://objects.githubusercontent.com/"
        ))
    }

}
