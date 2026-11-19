package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class DistributionBuildTypePolicyTest {
    @Test
    fun stableSelfUpdaterIsEnabledOnlyForDirectDistribution() {
        assertEquals(
            "The GitHub APK self-updater must be enabled only for the direct-install variant.",
            BuildConfig.BUILD_TYPE == "direct",
            BuildConfig.GUARDIAN_STABLE_UPDATER_ENABLED
        )
    }
}
