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
}
