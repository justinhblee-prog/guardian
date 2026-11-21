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
}
