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

    @Test fun activeTunnelDiagnosticsCannotBeOverriddenByGenericServiceMessage() {
        val detail = ProtectionDiagnostics.statusDetail(true, 12, 3, 8, 1, "off", "DNS 보호 실행 중")
        assertTrue(detail.contains("DNS 질의 12건"))
        assertTrue(detail.contains("차단 3"))
        assertTrue(detail.contains("앱 내부 광고"))
    }

    @Test fun inactiveTunnelPreservesSpecificServiceFailureMessage() {
        val detail = ProtectionDiagnostics.statusDetail(false, 0, 0, 0, 0, "off", "VPN 연결 실패: 권한 거부")
        assertTrue(detail.contains("VPN 연결 실패"))
    }

    @Test fun supportReportShowsAssetAndCacheReadinessWithoutSensitiveData() {
        val report = ProtectionDiagnostics.supportReport(
            appVersion = "0.7.14-dev",
            androidRelease = "17",
            apiLevel = 35,
            protectionActive = false,
            bundledRuleAssetAvailable = false,
            selectedProfile = "adguard",
            baseCacheBytes = 0,
            securityFilterEnabled = true,
            securityCacheBytes = 2048,
            androidAdsFilterEnabled = false,
            androidAdsCacheBytes = 0,
            autoUpdateEnabled = true,
            updateIntervalHours = 24,
            hasBaseUpdateTimestamp = false,
            signedFilterDeliveryConfigured = false,
            licenseApiConfigured = false,
            filterUpdateErrorPresent = true
        )

        assertTrue(report.contains("내장 DNS 규칙 파일: 누락 또는 읽기 실패"))
        assertTrue(report.contains("기본 필터 캐시: 없음"))
        assertTrue(report.contains("보안 필터: 있음 (2048 bytes)"))
        assertTrue(report.contains("Android 앱 광고 필터: 사용 안 함"))
        assertTrue(report.contains("최근 필터 업데이트 오류 기록: 있음"))
        assertTrue(report.contains("DNS 요청/방문 도메인"))
        assertTrue(report.contains("라이선스 키·토큰"))
        assertTrue(!report.contains("doubleclick.net"))
        assertTrue(!report.contains("https://"))
    }


    @Test fun supportReportDistinguishesPresentButInvalidCachesAndProfileMismatch() {
        val report = ProtectionDiagnostics.supportReport(
            appVersion = "0.7.14-dev",
            androidRelease = "17",
            apiLevel = 35,
            protectionActive = false,
            bundledRuleAssetAvailable = true,
            selectedProfile = "hagezi_pro",
            baseCacheBytes = 4096,
            securityFilterEnabled = true,
            securityCacheBytes = 2048,
            androidAdsFilterEnabled = true,
            androidAdsCacheBytes = 1024,
            autoUpdateEnabled = true,
            updateIntervalHours = 24,
            hasBaseUpdateTimestamp = true,
            signedFilterDeliveryConfigured = false,
            licenseApiConfigured = false,
            filterUpdateErrorPresent = false,
            baseCacheReady = false,
            cachedProfile = "adguard",
            cachedRuleCount = 1200,
            securityCacheReady = true,
            securityRuleCount = 4500,
            androidAdsCacheReady = false,
            androidAdsRuleCount = 25
        )

        assertTrue(report.contains("기본 필터 프로필 일치: 불일치"))
        assertTrue(report.contains("기본 필터 검증: 실패 또는 준비되지 않음 · 유효 규칙 1200개"))
        assertTrue("보안 필터 진단 누락: $report", report.contains("보안 필터: 있음 (2048 bytes)"))
        assertTrue("보안 필터 검증 상태 누락: $report", report.contains("검증 정상"))
        assertTrue("보안 필터 규칙 수 누락: $report", report.contains("유효 규칙 4500개"))
        assertTrue("Android 광고 필터 진단 누락: $report", report.contains("Android 앱 광고 필터: 있음 (1024 bytes)"))
        assertTrue("Android 광고 필터 검증 상태 누락: $report", report.contains("검증 실패 또는 준비되지 않음"))
        assertTrue("Android 광고 필터 규칙 수 누락: $report", report.contains("유효 규칙 25개"))
        assertTrue(!report.contains("/data/"))
        assertTrue(!report.contains("https://"))
    }

    @Test fun supportReportClampsInvalidUpdateIntervalAndShowsHealthyAsset() {
        val report = ProtectionDiagnostics.supportReport(
            appVersion = "1.0.0",
            androidRelease = "17",
            apiLevel = 35,
            protectionActive = true,
            bundledRuleAssetAvailable = true,
            selectedProfile = "hagezi_pro",
            baseCacheBytes = 4096,
            securityFilterEnabled = false,
            securityCacheBytes = 0,
            androidAdsFilterEnabled = true,
            androidAdsCacheBytes = 8192,
            autoUpdateEnabled = false,
            updateIntervalHours = 0,
            hasBaseUpdateTimestamp = true,
            signedFilterDeliveryConfigured = true,
            licenseApiConfigured = true,
            filterUpdateErrorPresent = false
        )

        assertTrue(report.contains("내장 DNS 규칙 파일: 정상"))
        assertTrue(report.contains("기본 필터 캐시: 있음 (4096 bytes)"))
        assertTrue(report.contains("업데이트 주기: 1시간"))
        assertTrue(report.contains("서명 필터 배포 설정: 설정됨"))
        assertTrue(report.contains("라이선스 API 설정: 설정됨"))
    }
    @Test fun supportReportClassifiesStartupFailureWithoutCopyingRawExceptionMessage() {
        val summary = ProtectionDiagnostics.startupFailureSummary(
            "필터 규칙 로드 실패 (FileNotFoundException): Library file not found at /data/user/0/private/path"
        )
        assertTrue(summary == "필터 규칙/리소스 로드 실패 (FileNotFoundException)")
        assertTrue(!summary.contains("Library file not found"))
        assertTrue(!summary.contains("/data/user/0"))
    }

    @Test fun supportReportDoesNotTreatNormalServiceStatusAsStartupFailure() {
        assertTrue(ProtectionDiagnostics.startupFailureSummary("DNS 보호가 실행 중입니다.") == "최근 시작 오류 기록 없음")
    }

    @Test fun supportReportRecognizesNativeLibraryStartupFailures() {
        val summary = ProtectionDiagnostics.startupFailureSummary(
            "보호 시작 중 예기치 않은 오류가 발생했습니다 (UnsatisfiedLinkError): missing native library"
        )
        assertTrue(summary.contains("VPN 시작 중 런타임 예외"))
        assertTrue(summary.contains("UnsatisfiedLinkError"))
        assertTrue(!summary.contains("missing native library"))
    }

    @Test fun supportReportIncludesRootCauseClassButNeverRawErrorMessage() {
        val root = java.io.FileNotFoundException("Library file not found at /private/app/lib")
        val wrapped = IllegalStateException("wrapper details", root)
        val chain = ProtectionDiagnostics.exceptionTypeChain(wrapped)
        assertTrue(chain == "IllegalStateException <- FileNotFoundException")
        val report = ProtectionDiagnostics.startupFailureSummary(
            "필터 규칙 로드 실패 ($chain): Library file not found at /private/app/lib"
        )
        assertTrue(report.contains("IllegalStateException <- FileNotFoundException"))
        assertTrue(!report.contains("Library file not found"))
        assertTrue(!report.contains("/private/app/lib"))
    }

    @Test fun exceptionTypeChainIsBoundedAndHandlesRepeatedCauses() {
        val root = java.io.IOException("root")
        val middle = IllegalStateException("middle", root)
        val top = RuntimeException("top", middle)
        assertTrue(
            ProtectionDiagnostics.exceptionTypeChain(top, maxDepth = 2) ==
                "RuntimeException <- IllegalStateException"
        )
    }

    @Test fun supportReportIncludesSafeNativeLibraryAndAbiInventory() {
        val report = ProtectionDiagnostics.supportReport(
            appVersion = "0.7.14-dev",
            androidRelease = "17",
            apiLevel = 35,
            protectionActive = false,
            bundledRuleAssetAvailable = true,
            selectedProfile = "adguard",
            baseCacheBytes = 1024,
            securityFilterEnabled = false,
            securityCacheBytes = 0,
            androidAdsFilterEnabled = false,
            androidAdsCacheBytes = 0,
            autoUpdateEnabled = true,
            updateIntervalHours = 24,
            hasBaseUpdateTimestamp = true,
            signedFilterDeliveryConfigured = false,
            licenseApiConfigured = false,
            filterUpdateErrorPresent = false,
            bundledRuleAssetBytes = 355,
            nativeLibraryDirectoryReadable = true,
            nativeLibraryNames = listOf("libguardian.so", "../private.txt", "libguardian.so"),
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "../../private")
        )
        assertTrue(report.contains("내장 DNS 규칙 파일: 정상 (355 bytes)"))
        assertTrue(report.contains("앱 네이티브 라이브러리 디렉터리: 읽기 가능"))
        assertTrue(report.contains("설치된 네이티브 라이브러리: libguardian.so"))
        assertTrue(report.contains("기기 ABI: arm64-v8a, armeabi-v7a"))
        assertTrue(!report.contains("../private"))
        assertTrue(!report.contains("../../private"))
    }

}