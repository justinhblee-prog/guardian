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

    @Test fun observedDnsCountersExplicitlyWarnAboutAppOwnedEncryptedDnsBypass() {
        val detail = ProtectionDiagnostics.statusDetail(true, 7, 2, 5, 0, "off")
        assertTrue(detail.contains("DoH/DoT"))
        assertTrue(detail.contains("HTTPS/TLS 기반 DNS"))
        assertTrue(detail.contains("DNS 필터를 우회"))
    }

    @Test fun noObservedDnsStillExplainsEncryptedDnsCoverageLimit() {
        val detail = ProtectionDiagnostics.statusDetail(true, 0, 0, 0, 0, "off")
        assertTrue(detail.contains("DoH/DoT"))
        assertTrue(detail.contains("실제 광고 제거 여부는 별도 확인"))
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

    @Test fun exceptionTraceSummaryIdentifiesCallersWithoutLeakingMessagesOrPaths() {
        val cause = java.io.FileNotFoundException("Library file not found at /data/user/0/private/libx.so")
        cause.stackTrace = arrayOf(StackTraceElement("androidx.loader.NativeLoader", "load", "NativeLoader.kt", 42))
        val error = IllegalStateException("private endpoint https://secret.invalid", cause)
        error.stackTrace = arrayOf(StackTraceElement("com.guardian.adblock.MainActivity", "requestProtection", "MainActivity.kt", 2110))
        val summary = ProtectionDiagnostics.exceptionTraceSummary(error)
        assertTrue(summary.contains("IllegalStateException <- FileNotFoundException"))
        assertTrue(summary.contains("com.guardian.adblock.MainActivity.requestProtection:2110"))
        assertTrue(summary.contains("androidx.loader.NativeLoader.load:42"))
        assertTrue(!summary.contains("Library file not found"))
        assertTrue(!summary.contains("/data/user/0"))
        assertTrue(!summary.contains("https://secret.invalid"))
    }

    @Test fun exceptionTraceSummaryRetainsDeepNativeLoaderFrames() {
        val cause = java.io.FileNotFoundException("Library file not found at /private/device/path")
        cause.stackTrace = (1..12).map { index ->
            StackTraceElement("androidx.loader.NativeLoader", "loadStep$index", "NativeLoader.kt", 40 + index)
        }.toTypedArray()
        val error = UnsatisfiedLinkError("private native library path")
        error.stackTrace = arrayOf(
            StackTraceElement("com.guardian.adblock.MainActivity", "requestProtection", "MainActivity.kt", 2110)
        )
        error.initCause(cause)

        val summary = ProtectionDiagnostics.exceptionTraceSummary(error)
        assertTrue(summary.contains("androidx.loader.NativeLoader.loadStep12:52"))
        assertTrue(!summary.contains("/private/device/path"))
        assertTrue(!summary.contains("Library file not found"))
    }

    @Test fun exceptionTraceSummaryReservesFramesForRootCauseAfterLongWrapperStack() {
        val cause = java.io.FileNotFoundException("private loader path /data/user/0/secret/libx.so")
        cause.stackTrace = (1..12).map { index ->
            StackTraceElement("androidx.loader.NativeLoader", "loadStep$index", "NativeLoader.kt", 40 + index)
        }.toTypedArray()
        val error = UnsatisfiedLinkError("private wrapper message").apply { initCause(cause) }
        error.stackTrace = (1..24).map { index ->
            StackTraceElement("com.guardian.adblock.FrameworkWrapper", "dispatch$index", "Wrapper.kt", 100 + index)
        }.toTypedArray()

        val summary = ProtectionDiagnostics.exceptionTraceSummary(error)
        assertTrue(summary.contains("com.guardian.adblock.FrameworkWrapper.dispatch1:101"))
        assertTrue(summary.contains("androidx.loader.NativeLoader.loadStep1:41"))
        assertTrue(!summary.contains("loadStep12"))
        assertTrue(!summary.contains("/data/user/0/secret"))
        assertTrue(!summary.contains("private wrapper message"))
        val frameSection = summary.substringAfter(" | ")
        assertTrue(frameSection.split(" <- ").size <= 16)
    }

    @Test fun filterReadinessSummaryUsesOnlyKnownFilterLabels() {
        val ready = ProtectionDiagnostics.filterReadinessSummary("모든 선택 필터가 준비되었습니다.")
        assertTrue(ready.contains("모든 선택 필터 준비됨"))

        val partial = ProtectionDiagnostics.filterReadinessSummary(
            "기본 보호 규칙으로 시작했습니다. 필터 업데이트 필요: 광고·추적, 보안, Android 앱 광고"
        )
        assertTrue(partial.contains("광고·추적"))
        assertTrue(partial.contains("보안"))
        assertTrue(partial.contains("Android 앱 광고"))

        val unsafe = ProtectionDiagnostics.filterReadinessSummary(
            "기본 보호 규칙으로 시작했습니다. 필터 업데이트 필요: secret.example.com"
        )
        assertTrue(unsafe == "확인 불가")
        assertTrue(!unsafe.contains("secret.example.com"))

        val baselineMissing = ProtectionDiagnostics.filterReadinessSummary(
            "기본 보호 규칙으로 시작했습니다. 필터 업데이트 필요: 기본 필터 라이브러리 누락 (FileNotFoundException) — 내장 긴급 규칙"
        )
        assertTrue(baselineMissing.contains("내장 긴급 DNS 규칙 사용"))
        assertTrue(baselineMissing.contains("기본 필터 파일 복구 필요"))
        assertTrue(!baselineMissing.contains("FileNotFoundException"))

        val baselineAndOptionalMissing = ProtectionDiagnostics.filterReadinessSummary(
            "기본 보호 규칙으로 시작했습니다. 필터 업데이트 필요: 기본 필터 라이브러리 누락 (IOException) — 내장 긴급 규칙, 광고·추적, Android 앱 광고"
        )
        assertTrue(baselineAndOptionalMissing.contains("내장 긴급 DNS 규칙 사용"))
        assertTrue(baselineAndOptionalMissing.contains("광고·추적"))
        assertTrue(baselineAndOptionalMissing.contains("Android 앱 광고"))
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
            filterUpdateErrorPresent = true,
            filterReadiness = "기본 보호 규칙으로 시작했습니다. 필터 업데이트 필요: 광고·추적, 보안"
        )

        assertTrue(report.contains("필터 시작 준비 상태: 기본 규칙으로 시작 · 추가 필터 준비 필요: 광고·추적, 보안"))
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
        println("CACHE_DIAGNOSTICS_TEST_REPORT_BEGIN\n$report\nCACHE_DIAGNOSTICS_TEST_REPORT_END")
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

    @Test fun exceptionTypeChainBoundsTraversalEvenWhenWrapperTypesAreNotReported() {
        var cause: Throwable = IllegalStateException("private root cause")
        repeat(12) {
            cause = Throwable("private wrapper").apply { initCause(cause) }
        }

        val summary = ProtectionDiagnostics.exceptionTypeChain(cause, maxDepth = 4)
        assertTrue(summary == "UnknownError")
        assertTrue(!summary.contains("private"))
    }


    @Test fun recentErrorCopyIncludesSafeStartupTraceAndFilterFailureCodes() {
        val report = ProtectionDiagnostics.recentErrorReport(
            lastServiceStatus = "필터 규칙 로드 실패 (FileNotFoundException): /data/user/0/private/secret",
            lastStartupTrace = "IllegalStateException <- FileNotFoundException | com.guardian.Main.load:42 <- /private/path",
            filterErrorCodes = listOf("NETWORK_TIMEOUT", "TLS_FAILURE", "initial-filter-cache-retry-exhausted", "bad code with spaces"),
            filterReadiness = "기본 보호 규칙으로 시작했습니다. 필터 업데이트 필요: 광고·추적, secret.example.com"
        )
        assertTrue(report.contains("필터 규칙/리소스 로드 실패 (FileNotFoundException)"))
        assertTrue(report.contains("com.guardian.Main.load:42"))
        assertTrue(report.contains("네트워크 연결 시간 초과 (NETWORK_TIMEOUT)"))
        assertTrue(report.contains("보안 연결 검증 실패 (TLS_FAILURE)"))
        assertTrue(report.contains("초기 필터 캐시 복구 재시도 한도 초과 (INITIAL-FILTER-CACHE-RETRY-EXHAUSTED)"))
        assertTrue(report.contains("광고·추적"))
        assertTrue(!report.contains("/data/user/0"))
        assertTrue(!report.contains("/private/path"))
        assertTrue(!report.contains("privatepath"))
        assertTrue(!report.contains("secret.example.com"))
        assertTrue(!report.contains("bad code with spaces"))
    }

    @Test fun supportDiagnosticsIncludeSafeDnsSummaryWithoutResolverAddressOrUrl() {
        val report = ProtectionDiagnostics.supportReport(
            appVersion = "1.0",
            androidRelease = "15",
            apiLevel = 35,
            protectionActive = true,
            bundledRuleAssetAvailable = true,
            selectedProfile = "adguard",
            baseCacheBytes = 100L,
            securityFilterEnabled = false,
            securityCacheBytes = 0L,
            androidAdsFilterEnabled = false,
            androidAdsCacheBytes = 0L,
            autoUpdateEnabled = true,
            updateIntervalHours = 24L,
            hasBaseUpdateTimestamp = true,
            signedFilterDeliveryConfigured = false,
            licenseApiConfigured = false,
            filterUpdateErrorPresent = false,
            lastDnsDiagnostic = "업스트림 DNS 오류: DoT 후보 1 (203.0.113.8): https://resolver.private.invalid"
        )
        assertTrue(report.contains("마지막 DNS 진단: 업스트림 DNS 실패 (DOT)"))
        assertTrue(!report.contains("203.0.113.8"))
        assertTrue(!report.contains("resolver.private.invalid"))
        assertTrue(!report.contains("https://"))
    }

    @Test fun recentErrorCopyIncludesSafeDnsDiagnosticWithoutLeakingResolverDetails() {
        val report = ProtectionDiagnostics.recentErrorReport(
            lastServiceStatus = null,
            lastStartupTrace = null,
            filterErrorCodes = emptyList(),
            filterReadiness = null,
            lastDnsDiagnostic = "업스트림 DNS 오류: UDP 후보 1 (192.0.2.53): UDP: 연결 실패 https://private.invalid"
        )
        assertTrue(report.contains("마지막 DNS 진단: 업스트림 DNS 실패 (UDP)"))
        assertTrue(!report.contains("192.0.2.53"))
        assertTrue(!report.contains("private.invalid"))
        assertTrue(!report.contains("https://"))
    }

    @Test fun recentErrorCopyShowsSuccessfulDnsConnectivityCheckWithoutRawDetails() {
        val report = ProtectionDiagnostics.recentErrorReport(
            lastServiceStatus = null,
            lastStartupTrace = null,
            filterErrorCodes = emptyList(),
            filterReadiness = null,
            lastDnsDiagnostic = "업스트림 DNS 연결 테스트 성공 (DoH, 23ms)"
        )
        assertTrue(report.contains("마지막 DNS 진단: 업스트림 DNS 연결 테스트 성공"))
        assertTrue(!report.contains("23ms"))
    }

    @Test fun recentErrorCopyClearlyReportsWhenNoErrorIsRecorded() {
        val report = ProtectionDiagnostics.recentErrorReport(null, null, emptyList(), null)
        assertTrue(report.contains("최근 시작 오류 기록 없음"))
        assertTrue(report.contains("호출 경로: 기록 없음"))
        assertTrue(report.contains("필터 오류:"))
        assertTrue(report.contains("기록 없음"))
    }

    @Test fun supportReportRejectsHostnamesPathsAndUrlsInPersistedStartupTrace() {
        val report = ProtectionDiagnostics.supportReport(
            appVersion = "1.0",
            androidRelease = "15",
            apiLevel = 35,
            protectionActive = false,
            bundledRuleAssetAvailable = true,
            selectedProfile = "adguard",
            baseCacheBytes = 100L,
            securityFilterEnabled = false,
            securityCacheBytes = 0L,
            androidAdsFilterEnabled = false,
            androidAdsCacheBytes = 0L,
            autoUpdateEnabled = true,
            updateIntervalHours = 24L,
            hasBaseUpdateTimestamp = true,
            signedFilterDeliveryConfigured = false,
            licenseApiConfigured = false,
            filterUpdateErrorPresent = false,
            lastStartupTrace = "IllegalStateException <- FileNotFoundException | com.guardian.Main.load:42 <- /data/user/0/private/libx.so <- https://resolver.private.invalid <- secret.example.com"
        )

        assertTrue(report.contains("IllegalStateException <- FileNotFoundException"))
        assertTrue(report.contains("com.guardian.Main.load:42"))
        assertTrue(!report.contains("/data/user/0"))
        assertTrue(!report.contains("private/libx.so"))
        assertTrue(!report.contains("https://"))
        assertTrue(!report.contains("resolver.private.invalid"))
        assertTrue(!report.contains("secret.example.com"))
    }

    @Test fun supportReportDoesNotTreatMalformedTraceTextAsStackFrames() {
        val report = ProtectionDiagnostics.supportReport(
            appVersion = "1.0",
            androidRelease = "15",
            apiLevel = 35,
            protectionActive = false,
            bundledRuleAssetAvailable = true,
            selectedProfile = "adguard",
            baseCacheBytes = 100L,
            securityFilterEnabled = false,
            securityCacheBytes = 0L,
            androidAdsFilterEnabled = false,
            androidAdsCacheBytes = 0L,
            autoUpdateEnabled = true,
            updateIntervalHours = 24L,
            hasBaseUpdateTimestamp = true,
            signedFilterDeliveryConfigured = false,
            licenseApiConfigured = false,
            filterUpdateErrorPresent = false,
            lastStartupTrace = "https://secret.invalid | /private/path <- secret.example.com"
        )

        assertTrue(report.contains("최근 오류 호출 스택(메시지/경로 제외): 기록 없음"))
        assertTrue(!report.contains("secret.invalid"))
        assertTrue(!report.contains("/private/path"))
        assertTrue(!report.contains("secret.example.com"))
    }

}