package com.guardian.adblock

import org.junit.Assert.assertEquals
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
    @Test fun acceptsOnlyExactOfficialGuardianDevelopmentReleaseAssets() {
        assertTrue(DevUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk"
        ))
        assertTrue(DevUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.json"
        ))
        assertTrue(DevUpdateManager.isGuardianReleaseAssetUrl(
            "https://github.com:443/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk"
        ))
    }

    @Test fun rejectsNonReleaseHostsSchemesAndCredentials() {
        val invalid = listOf(
            "http://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk",
            "https://evil.example/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk",
            "https://github.com.attacker.example/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk",
            "https://user@github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk",
            "https://github.com:8443/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk"
        )
        invalid.forEach { assertFalse(DevUpdateManager.isGuardianReleaseAssetUrl(it)) }
    }

    @Test fun rejectsNonGuardianTagsAssetsAndPathTraversal() {
        val invalid = listOf(
            "https://github.com/other/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk",
            "https://github.com/justinhblee-prog/guardian/releases/download/v48/guardian-android-dev.apk",
            "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v0/guardian-android-dev.apk",
            "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/other.apk",
            "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/../guardian-android-dev.apk",
            "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/%2e%2e/guardian-android-dev.apk",
            "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk/extra",
            "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk?download=1",
            "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v48/guardian-android-dev.apk#fragment"
        )
        invalid.forEach { assertFalse(DevUpdateManager.isGuardianReleaseAssetUrl(it)) }
    }


    @Test fun readsOnlyExactGuardianDevelopmentTagsFromAtomFeedAndSortsNumerically() {
        val feed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry><link rel="alternate" href="https://github.com/justinhblee-prog/guardian/releases/tag/guardian-dev-v57"/></entry>
              <entry><link rel="alternate" href="https://github.com/justinhblee-prog/guardian/releases/tag/guardian-dev-v58"/></entry>
              <entry><link rel="alternate" href="https://github.com/justinhblee-prog/guardian/releases/tag/guardian-dev-v9"/></entry>
              <entry><link rel="alternate" href="https://github.com/other/guardian/releases/tag/guardian-dev-v999"/></entry>
              <entry><link rel="alternate" href="https://github.com.attacker.example/justinhblee-prog/guardian/releases/tag/guardian-dev-v999"/></entry>
              <entry><link rel="alternate" href="https://github.com/justinhblee-prog/guardian/releases/tag/v999"/></entry>
            </feed>
        """.trimIndent()

        assertEquals(listOf(58, 57, 9), DevUpdateManager.developmentReleaseVersionsFromAtom(feed))
    }

    @Test fun rejectsNonAtomPayloadEvenWhenItContainsAReleaseLookingUrl() {
        val html = """<html><a href="https://github.com/justinhblee-prog/guardian/releases/tag/guardian-dev-v999">release</a></html>"""
        assertTrue(DevUpdateManager.developmentReleaseVersionsFromAtom(html).isEmpty())
    }
    @Test fun downloadDescriptorMustBeNewerAndMatchTheOfficialTagAndAsset() {
        val valid = GuardianDevUpdate(
            versionName = "0.7.49-dev",
            versionCode = 62,
            sha256 = "a".repeat(64),
            signerSha256 = "b".repeat(64),
            apkUrl = "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v62/guardian-android-dev.apk",
            notes = ""
        )

        assertTrue(DevUpdateManager.isValidDownloadDescriptor(61, valid))
        assertFalse(DevUpdateManager.isValidDownloadDescriptor(62, valid))
        assertFalse(DevUpdateManager.isValidDownloadDescriptor(63, valid))
        assertFalse(DevUpdateManager.isValidDownloadDescriptor(
            61, valid.copy(apkUrl = "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v61/guardian-android-dev.apk")
        ))
        assertFalse(DevUpdateManager.isValidDownloadDescriptor(
            61, valid.copy(apkUrl = "https://attacker.example/guardian-dev-v62/guardian-android-dev.apk")
        ))
    }

    @Test fun downloadDescriptorRejectsMalformedVersionAndDigests() {
        val valid = GuardianDevUpdate(
            versionName = "0.7.49-dev",
            versionCode = 62,
            sha256 = "a".repeat(64),
            signerSha256 = "b".repeat(64),
            apkUrl = "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v62/guardian-android-dev.apk",
            notes = ""
        )

        assertFalse(DevUpdateManager.isValidDownloadDescriptor(61, valid.copy(versionName = "0.7.49")))
        assertFalse(DevUpdateManager.isValidDownloadDescriptor(61, valid.copy(sha256 = "not-a-sha256")))
        assertFalse(DevUpdateManager.isValidDownloadDescriptor(61, valid.copy(signerSha256 = "not-a-certificate-fingerprint")))
    }


}
