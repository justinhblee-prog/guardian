package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevUpdateManagerTest {
    @Test fun onlyFetchesManifestsForVersionsNewerThanInstalledBuild() {
        assertTrue(DevUpdateManager.shouldFetchReleaseManifest(59, 60))
        assertFalse(DevUpdateManager.shouldFetchReleaseManifest(59, 59))
        assertFalse(DevUpdateManager.shouldFetchReleaseManifest(59, 58))
    }

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
    @Test fun atomFallbackBuildsOnlyCanonicalGuardianDevReleaseAssets() {
        val feed = "https://github.com/justinhblee-prog/guardian/releases/tag/guardian-dev-v72\n" +
            "https://github.com/attacker/guardian/releases/tag/guardian-dev-v999\n" +
            "https://github.com/justinhblee-prog/guardian/releases/tag/guardian-dev-v71"
        val releases = DevUpdateManager.releasesFromAtom(feed)
        assertTrue(releases.length() == 2)
        assertTrue(releases.getJSONObject(0).getString("tag_name") == "guardian-dev-v72")
        assertTrue(releases.getJSONObject(0).getJSONArray("assets").length() == 2)
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
    }

}
