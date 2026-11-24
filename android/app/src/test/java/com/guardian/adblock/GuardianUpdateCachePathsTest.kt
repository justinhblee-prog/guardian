package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GuardianUpdateCachePathsTest {
    @Test fun developmentAndStableUpdateDirectoriesAreDistinct() {
        assertEquals("updates", GuardianUpdateCachePaths.DEVELOPMENT_DIRECTORY)
        assertEquals("stable-updates", GuardianUpdateCachePaths.STABLE_DIRECTORY)
        assertNotEquals(GuardianUpdateCachePaths.DEVELOPMENT_DIRECTORY, GuardianUpdateCachePaths.STABLE_DIRECTORY)
    }
}
