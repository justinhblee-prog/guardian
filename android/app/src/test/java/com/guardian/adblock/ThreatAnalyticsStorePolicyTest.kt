package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class ThreatAnalyticsStorePolicyTest {
    @Test fun eventSummaryDoesNotExposeDomainOrUrlFields() {
        val event = ThreatAnalyticsEvent(
            1_800_000_000_000L,
            ThreatAnalyticsEngine.Severity.INFORMATIONAL,
            "광고·추적 차단"
        )
        assertEquals(ThreatAnalyticsEngine.Severity.INFORMATIONAL, event.severity)
        assertFalse(event.title.contains("example.com"))
        assertFalse(event.title.contains("https://"))
    }
}
