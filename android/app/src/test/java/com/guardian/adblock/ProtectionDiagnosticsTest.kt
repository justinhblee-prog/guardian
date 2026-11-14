package com.guardian.adblock

import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionDiagnosticsTest {
    @Test fun activeTunnelWithNoObservedDnsDoesNotClaimFilteringIsWorking() {
        val detail = ProtectionDiagnostics.statusDetail(true, 0, 0, 0, 0, "off")
        assertTrue(detail.contains("아직 DNS 질의를 관측하지 못했습니다"))
        assertTrue(detail.contains("실제 광고 제거 여부는 별도 확인"))
    }

    @Test fun observedTrafficShowsRealCountersAndLimitsClaim() {
        val detail = ProtectionDiagnostics.statusDetail(true, 12, 3, 8, 1, "off")
        assertTrue(detail.contains("DNS 질의 12건"))
        assertTrue(detail.contains("차단 3"))
        assertTrue(detail.contains("업스트림 전달 8"))
        assertTrue(detail.contains("응답 실패 1"))
        assertTrue(detail.contains("앱 내부 광고"))
    }

    @Test fun privateDnsHostnameModeWarnsThatEncryptedDnsMayBypass() {
        val detail = ProtectionDiagnostics.statusDetail(true, 2, 1, 1, 0, "hostname")
        assertTrue(detail.contains("비공개 DNS 공급자"))
        assertTrue(detail.contains("암호화 DNS"))
    }

    @Test fun opportunisticPrivateDnsModeIsAlsoReported() {
        val detail = ProtectionDiagnostics.statusDetail(true, 0, 0, 0, 0, "opportunistic")
        assertTrue(detail.contains("비공개 DNS 자동 모드"))
        assertTrue(detail.contains("우회"))
    }

    @Test fun inactiveTunnelNeverReportsObservedProtection() {
        val detail = ProtectionDiagnostics.statusDetail(false, 99, 80, 19, 0, "off")
        assertTrue(detail.contains("보호를 시작하면"))
    }
}
