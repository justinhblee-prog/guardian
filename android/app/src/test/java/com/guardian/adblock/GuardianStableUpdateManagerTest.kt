package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

class GuardianStableUpdateManagerTest {
    @Test fun acceptsOnlyExactOfficialStableReleaseAssets() {
        assertTrue(GuardianStableUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/v1.2.3/guardian-android.apk",
            "v1.2.3",
            "guardian-android.apk"
        ))
        assertTrue(GuardianStableUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/v1.2.3/guardian-android.json",
            "v1.2.3",
            "guardian-android.json"
        ))
        assertTrue(GuardianStableUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com:443/justinhblee-prog/guardian/releases/download/v1.2.3/guardian-android.apk",
            "v1.2.3",
            "guardian-android.apk"
        ))
    }

    @Test fun rejectsHostSchemePortCredentialsAndQueryVariants() {
        val invalid = listOf(
            "http://github.com/justinhblee-prog/guardian/releases/download/v1.2.3/guardian-android.apk",
            "https://github.com.attacker.example/justinhblee-prog/guardian/releases/download/v1.2.3/guardian-android.apk",
            "https://github.com:8443/justinhblee-prog/guardian/releases/download/v1.2.3/guardian-android.apk",
            "https://user@github.com/justinhblee-prog/guardian/releases/download/v1.2.3/guardian-android.apk",
            "https://github.com/justinhblee-prog/guardian/releases/download/v1.2.3/guardian-android.apk?download=1",
            "https://github.com/justinhblee-prog/guardian/releases/download/v1.2.3/guardian-android.apk#fragment"
        )
        invalid.forEach {
            assertFalse(GuardianStableUpdateManager.isGuardianReleaseAssetUrl(it, "v1.2.3", "guardian-android.apk"))
        }
    }

    @Test fun rejectsWrongTagAssetAndPathTraversal() {
        assertFalse(GuardianStableUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/v1.2.4/guardian-android.apk",
            "v1.2.3",
            "guardian-android.apk"
        ))
        assertFalse(GuardianStableUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/v1.2.3/guardian-android.apk/extra",
            "v1.2.3",
            "guardian-android.apk"
        ))
        assertFalse(GuardianStableUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/v1.2.3/../guardian-android.apk",
            "v1.2.3",
            "guardian-android.apk"
        ))
        assertFalse(GuardianStableUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/v1.2.3/other.apk",
            "v1.2.3",
            "guardian-android.apk"
        ))
        assertFalse(GuardianStableUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/v1.2.3/guardian-android.apk",
            "v1.2.3-rc1",
            "guardian-android.apk"
        ))
        assertFalse(GuardianStableUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/v1.2.3/guardian-android.apk",
            "v1.2.3",
            "other.apk"
        ))
    }

    @Test fun restrictsRedirectHostsToGitHubAndReleaseAssetCdn() {
        assertTrue(GuardianStableUpdateManager.isAllowedHttpsHost(URL("https://api.github.com/repos/owner/repo/releases")))
        assertTrue(GuardianStableUpdateManager.isAllowedHttpsHost(URL("https://github.com/owner/repo/releases")))
        assertTrue(GuardianStableUpdateManager.isAllowedHttpsHost(URL("https://release-assets.githubusercontent.com/assets/file")))
        assertTrue(GuardianStableUpdateManager.isAllowedHttpsHost(URL("https://objects.githubusercontent.com/assets/file")))
        assertFalse(GuardianStableUpdateManager.isAllowedHttpsHost(URL("http://github.com/owner/repo")))
        assertFalse(GuardianStableUpdateManager.isAllowedHttpsHost(URL("https://github.com:8443/owner/repo")))
        assertFalse(GuardianStableUpdateManager.isAllowedHttpsHost(URL("https://github.com.attacker.example/owner/repo")))
        assertFalse(GuardianStableUpdateManager.isAllowedHttpsHost(URL("https://user@github.com/owner/repo")))
        assertFalse(GuardianStableUpdateManager.isAllowedReleaseDownloadHost(URL("https://api.github.com/repos/owner/repo/releases")))
        assertTrue(GuardianStableUpdateManager.isAllowedReleaseDownloadHost(URL("https://release-assets.githubusercontent.com/assets/file")))
    }
}
