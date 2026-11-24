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
    @Test fun developmentTagsAndVersionNamesMustUseCanonicalNumericComponents() {
        assertEquals(48, DevUpdateManager.parseDevelopmentTagVersion("guardian-dev-v48"))
        assertEquals(0, DevUpdateManager.parseDevelopmentTagVersion("guardian-dev-v0"))
        assertEquals(null, DevUpdateManager.parseDevelopmentTagVersion("guardian-dev-v048"))
        assertEquals(null, DevUpdateManager.parseDevelopmentTagVersion("guardian-dev-v+48"))
        assertEquals(null, DevUpdateManager.parseDevelopmentTagVersion("guardian-dev-v2147483648"))

        assertTrue(DevUpdateManager.isCanonicalDevelopmentVersionName("0.7.49-dev"))
        assertTrue(DevUpdateManager.isCanonicalDevelopmentVersionName("12.0.3-dev"))
        assertFalse(DevUpdateManager.isCanonicalDevelopmentVersionName("00.7.49-dev"))
        assertFalse(DevUpdateManager.isCanonicalDevelopmentVersionName("0.07.49-dev"))
        assertFalse(DevUpdateManager.isCanonicalDevelopmentVersionName("0.7.049-dev"))
        assertFalse(DevUpdateManager.isCanonicalDevelopmentVersionName("0.7.49"))
    }

    @Test fun acceptsUnknownLengthDevelopmentApkStreamsButBoundsActualBytes() {
        val maximum = 100L * 1024L * 1024L
        assertTrue(DevUpdateManager.isAcceptableDownloadLength(-1L, maximum))
        assertTrue(DevUpdateManager.isAcceptableDownloadLength(1024L, maximum))
        assertFalse(DevUpdateManager.isAcceptableDownloadLength(0L, maximum))
        assertFalse(DevUpdateManager.isAcceptableDownloadLength(maximum + 1L, maximum))
        assertFalse(DevUpdateManager.isAcceptableDownloadLength(-1L, 0L))

        assertTrue(DevUpdateManager.matchesDownloadedLength(-1L, 1024L, maximum))
        assertTrue(DevUpdateManager.matchesDownloadedLength(1024L, 1024L, maximum))
        assertFalse(DevUpdateManager.matchesDownloadedLength(2048L, 1024L, maximum))
        assertFalse(DevUpdateManager.matchesDownloadedLength(-1L, 0L, maximum))
        assertFalse(DevUpdateManager.matchesDownloadedLength(-1L, maximum + 1L, maximum))
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
