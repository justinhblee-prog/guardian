package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumReportBuilderTest {
    @Test fun reportContainsRealCountersAndPrivacyNotice() {
        val report = PremiumReportBuilder.build(
            PremiumReportSnapshot(
                totalQueries = 1200,
                blockedQueries = 1050,
                forwardedQueries = 140,
                failedQueries = 10,
                adBlocked = 30,
                trackerBlocked = 15,
                otherBlocked = 5,
                protectionActive = true,
                analyticsBlocked = 4,
                telemetryBlocked = 3,
                fingerprintingBlocked = 2,
                socialBlocked = 1
            ),
            0L
        )
        assertTrue(report.contains("1970-01-01T00:00:00Z"))
        assertTrue(report.contains("분석 범위: 이 앱 설치 이후 누적"))
        assertTrue(report.contains("관측 DNS 질의: 1,200"))
        assertTrue(report.contains("차단 비율: 87.5%"))
        assertTrue(report.contains("업스트림 전달 비율: 11.7%"))
        assertTrue(report.contains("응답 실패 비율: 0.8%"))
        assertTrue(report.contains("광고 차단: 30"))
        assertTrue(report.contains("분석 차단: 4"))
        assertTrue(report.contains("텔레메트리 차단: 3"))
        assertTrue(report.contains("핑거프린팅 차단: 2"))
        assertTrue(report.contains("소셜 위젯 차단: 1"))
        assertTrue(report.contains("미분류·기존 기록: 5"))
        assertTrue(report.contains("보호 상태: 활성"))
        assertTrue(report.contains("중대한 응답 실패 신호가 없습니다"))
        assertTrue(report.contains("방문 도메인, 앱 사용 기록, 사용자 규칙은 포함하지 않습니다"))
        assertTrue(report.contains("미분류·기존 기록은 과거 유형별 기록이 없거나 판단 근거가 부족한 요청입니다"))
        assertTrue(report.contains("미분류는 보안 위협 판정이 아닙니다"))
        assertFalse(report.contains("example.com"))
    }

    @Test fun emptyAndNegativeCountersProduceSafeZeroValues() {
        val report = PremiumReportBuilder.build(
            PremiumReportSnapshot(-1, -2, -3, -4, -5, -6, -7, false),
            0L
        )
        assertTrue(report.contains("관측 DNS 질의: 0"))
        assertTrue(report.contains("차단 비율: 0.0%"))
        assertTrue(report.contains("업스트림 전달 비율: 0.0%"))
        assertTrue(report.contains("응답 실패 비율: 0.0%"))
        assertTrue(report.contains("업스트림 전달: 0"))
        assertTrue(report.contains("보호가 비활성 상태입니다"))
        assertTrue(report.contains("보호 상태: 비활성"))
    }

    @Test fun blockedAndForwardedRatiosAreBoundedByObservedQueryCount() {
        val report = PremiumReportBuilder.build(
            PremiumReportSnapshot(10, 100, 100, 0, 100, 0, 0, true),
            0L
        )
        assertTrue(report.contains("차단 비율: 100.0%"))
        assertTrue(report.contains("업스트림 전달 비율: 100.0%"))
    }

    @Test fun highDnsFailureRatioProducesActionableHealthWarning() {
        val report = PremiumReportBuilder.build(
            PremiumReportSnapshot(100, 20, 70, 10, 5, 5, 10, true),
            0L
        )
        assertTrue(report.contains("응답 실패 비율: 10.0%"))
        assertTrue(report.contains("업스트림 연결과 비공개 DNS 설정을 점검하세요"))
    }

    @Test fun activeProtectionWithoutObservedQueriesIsNotReportedAsHealthy() {
        val report = PremiumReportBuilder.build(
            PremiumReportSnapshot(0, 0, 0, 0, 0, 0, 0, true),
            0L
        )
        assertTrue(report.contains("아직 관측된 DNS 질의가 없습니다"))
    }
}
