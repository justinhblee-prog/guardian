package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumReportBuilderTest {
    @Test fun reportContainsRealCountersAndPrivacyNotice() {
        val report = PremiumReportBuilder.build(
            PremiumReportSnapshot(
                totalQueries = 200,
                blockedQueries = 50,
                forwardedQueries = 140,
                failedQueries = 10,
                adBlocked = 30,
                trackerBlocked = 15,
                otherBlocked = 5,
                protectionActive = true
            ),
            0L
        )
        assertTrue(report.contains("1970-01-01T00:00:00Z"))
        assertTrue(report.contains("관측 DNS 질의: 200"))
        assertTrue(report.contains("차단 비율: 25.0%"))
        assertTrue(report.contains("광고 차단: 30"))
        assertTrue(report.contains("보호 상태: 활성"))
        assertTrue(report.contains("방문 도메인, 앱 사용 기록, 사용자 규칙은 포함하지 않습니다"))
        assertFalse(report.contains("example.com"))
    }

    @Test fun emptyAndNegativeCountersProduceSafeZeroValues() {
        val report = PremiumReportBuilder.build(
            PremiumReportSnapshot(-1, -2, -3, -4, -5, -6, -7, false),
            0L
        )
        assertTrue(report.contains("관측 DNS 질의: 0"))
        assertTrue(report.contains("차단 비율: 0.0%"))
        assertTrue(report.contains("업스트림 전달: 0"))
        assertTrue(report.contains("보호 상태: 비활성"))
    }

    @Test fun blockedRatioIsBoundedByObservedQueryCount() {
        val report = PremiumReportBuilder.build(
            PremiumReportSnapshot(10, 100, 0, 0, 100, 0, 0, true),
            0L
        )
        assertTrue(report.contains("차단 비율: 100.0%"))
    }
}
