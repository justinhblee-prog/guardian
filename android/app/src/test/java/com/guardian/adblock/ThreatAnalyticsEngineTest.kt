package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class ThreatAnalyticsEngineTest {
    @Test fun securityIntelMatchIsReportedWithExplicitEvidenceNotSpecificMalwareClaim() {
        val result = ThreatAnalyticsEngine.assess(
            blocked = true,
            evidence = setOf(ThreatAnalyticsEngine.Evidence.SECURITY_INTELLIGENCE_LIST)
        )!!
        assertEquals(ThreatAnalyticsEngine.Severity.HIGH, result.severity)
        assertTrue(result.title.contains("위협 인텔리전스"))
        assertTrue(result.explanation.contains("구체적인 악성 행위를 확정하지 않습니다"))
    }

    @Test fun adAndTrackerBlocksRemainInformational() {
        val result = ThreatAnalyticsEngine.assess(
            blocked = true,
            evidence = setOf(
                ThreatAnalyticsEngine.Evidence.AD_FILTER,
                ThreatAnalyticsEngine.Evidence.TRACKER_FILTER
            )
        )!!
        assertEquals(ThreatAnalyticsEngine.Severity.INFORMATIONAL, result.severity)
        assertFalse(result.title.contains("악성코드"))
    }

    @Test fun userRuleDoesNotBecomeAThreatVerdict() {
        val result = ThreatAnalyticsEngine.assess(
            blocked = true,
            evidence = setOf(ThreatAnalyticsEngine.Evidence.USER_BLOCK_RULE)
        )!!
        assertEquals(ThreatAnalyticsEngine.Severity.UNASSESSED, result.severity)
    }

    @Test fun knownAdOrTrackerEvidenceRemainsInformationalWhenUserRuleAlsoMatches() {
        val result = ThreatAnalyticsEngine.assess(
            blocked = true,
            evidence = setOf(
                ThreatAnalyticsEngine.Evidence.USER_BLOCK_RULE,
                ThreatAnalyticsEngine.Evidence.AD_FILTER
            )
        )!!
        assertEquals(ThreatAnalyticsEngine.Severity.INFORMATIONAL, result.severity)
        assertTrue(result.evidence.contains(ThreatAnalyticsEngine.Evidence.USER_BLOCK_RULE))
        assertTrue(result.evidence.contains(ThreatAnalyticsEngine.Evidence.AD_FILTER))
    }

    @Test fun unknownBlockSourceIsNotAssignedAnInventedRiskScore() {
        val result = ThreatAnalyticsEngine.assess(blocked = true, evidence = emptySet())!!
        assertEquals(ThreatAnalyticsEngine.Severity.UNASSESSED, result.severity)
        assertEquals(
            setOf(ThreatAnalyticsEngine.Evidence.UNKNOWN_BLOCK_SOURCE),
            result.evidence
        )
    }

    @Test fun allowedOrForwardedRequestsDoNotCreateThreatAssessment() {
        assertNull(
            ThreatAnalyticsEngine.assess(
                blocked = false,
                evidence = setOf(ThreatAnalyticsEngine.Evidence.SECURITY_INTELLIGENCE_LIST)
            )
        )
    }

    @Test fun securityEvidenceTakesPrecedenceOverGenericFilterEvidence() {
        val result = ThreatAnalyticsEngine.assess(
            blocked = true,
            evidence = setOf(
                ThreatAnalyticsEngine.Evidence.AD_FILTER,
                ThreatAnalyticsEngine.Evidence.SECURITY_INTELLIGENCE_LIST
            )
        )!!
        assertEquals(ThreatAnalyticsEngine.Severity.HIGH, result.severity)
    }
}
