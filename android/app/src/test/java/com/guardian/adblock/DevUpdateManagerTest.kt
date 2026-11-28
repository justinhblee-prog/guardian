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
    @Test fun atomFallbackAcceptsOnlyCanonicalGuardianDevReleaseTags() {
        val feed = "https://github.com/justinhblee-prog/guardian/releases/tag/guardian-dev-v72\\n" +
            "https://github.com/attacker/guardian/releases/tag/guardian-dev-v999\\n" +
            "https://github.com/justinhblee-prog/guardian/releases/tag/guardian-dev-v71\\n" +
            "https://github.com/justinhblee-prog/guardian/releases/tag/guardian-dev-v72"
        assertTrue(DevUpdateManager.atomTagsFromFeed(feed) == listOf("guardian-dev-v72", "guardian-dev-v71"))
    }
    @Test fun acceptsOnlyCanonicalGuardianDevelopmentReleaseAssets() {
        val releaseBase = "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/"
        assertTrue(DevUpdateManager.isGuardianDevReleaseAssetUrl(
            releaseBase + "guardian-android-dev.apk", "guardian-android-dev.apk", 48
        ))
        assertTrue(DevUpdateManager.isGuardianDevReleaseAssetUrl(
            releaseBase + "guardian-android-dev.json", "guardian-android-dev.json", 48
        ))
        assertFalse(DevUpdateManager.isGuardianDevReleaseAssetUrl(
            releaseBase + "guardian-android-dev.apk", "guardian-android-dev.apk", 49
        ))
        assertFalse(DevUpdateManager.isGuardianDevReleaseAssetUrl(
            releaseBase + "other.apk", "guardian-android-dev.apk", 48
        ))
        assertFalse(DevUpdateManager.isGuardianDevReleaseAssetUrl(
            releaseBase + "../guardian-android-dev.apk", "guardian-android-dev.apk", 48
        ))
        assertFalse(DevUpdateManager.isGuardianDevReleaseAssetUrl(
            releaseBase + "guardian-android-dev.apk?redirect=https://evil.example",
            "guardian-android-dev.apk", 48
        ))
        assertFalse(DevUpdateManager.isGuardianDevReleaseAssetUrl(
            releaseBase + "guardian-android-dev.apk#fragment", "guardian-android-dev.apk", 48
        ))
        assertFalse(DevUpdateManager.isGuardianDevReleaseAssetUrl(
            "http://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk",
            "guardian-android-dev.apk", 48
        ))
        assertFalse(DevUpdateManager.isGuardianDevReleaseAssetUrl(
            "https://github.com.attacker.example/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk",
            "guardian-android-dev.apk", 48
        ))
        assertFalse(DevUpdateManager.isGuardianDevReleaseAssetUrl(
            "https://user@github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk",
            "guardian-android-dev.apk", 48
        ))
        assertFalse(DevUpdateManager.isGuardianDevReleaseAssetUrl(
            releaseBase + "guardian-android-dev.apk", "arbitrary.zip", 48
        ))
    }

}
