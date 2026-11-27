package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class GuardianStableUpdatePolicyTest {
    @Test fun acceptsOnlyPublishedNonPrereleaseSemanticVersionTags() {
        assertTrue(GuardianStableUpdatePolicy.isStableRelease(false, false, "v1.0.0"))
        assertTrue(GuardianStableUpdatePolicy.isStableRelease(false, false, "v2.12.34"))
        assertFalse(GuardianStableUpdatePolicy.isStableRelease(true, false, "v1.0.0"))
        assertFalse(GuardianStableUpdatePolicy.isStableRelease(false, true, "v1.0.0"))
        assertFalse(GuardianStableUpdatePolicy.isStableRelease(false, false, "guardian-dev-v30"))
        assertFalse(GuardianStableUpdatePolicy.isStableRelease(false, false, "v1.0.0-rc1"))
        assertFalse(GuardianStableUpdatePolicy.isStableRelease(false, false, "v1.0"))
    }
    @Test fun developmentOnlyReleaseListDoesNotContainAStableRelease() {
        val tags = listOf("guardian-dev-v58", "guardian-dev-v57", "guardian-dev-v56")
        assertFalse(tags.any { GuardianStableUpdatePolicy.isStableRelease(false, true, it) })
    }
    @Test fun missingLatestReleaseManifestMeansNoStableReleaseIsPublished() {
        assertTrue(GuardianStableUpdatePolicy.isMissingStableReleaseStatus(404))
        assertFalse(GuardianStableUpdatePolicy.isMissingStableReleaseStatus(403))
        assertFalse(GuardianStableUpdatePolicy.isMissingStableReleaseStatus(429))
        assertFalse(GuardianStableUpdatePolicy.isMissingStableReleaseStatus(500))
    }
    @Test fun updateMustHaveStrictlyHigherVersionCode() {
        assertTrue(GuardianStableUpdatePolicy.isNewerVersion(30, 31))
        assertFalse(GuardianStableUpdatePolicy.isNewerVersion(30, 30))
        assertFalse(GuardianStableUpdatePolicy.isNewerVersion(30, 29))
    }
    @Test fun sha256MustBeExactly64HexCharacters() {
        assertTrue(GuardianStableUpdatePolicy.isSha256("a".repeat(64)))
        assertTrue(GuardianStableUpdatePolicy.isSha256("A".repeat(64)))
        assertFalse(GuardianStableUpdatePolicy.isSha256("a".repeat(63)))
        assertFalse(GuardianStableUpdatePolicy.isSha256("g".repeat(64)))
    }
}
