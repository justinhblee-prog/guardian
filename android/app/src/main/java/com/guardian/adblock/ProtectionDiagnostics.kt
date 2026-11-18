package com.guardian.adblock

/** Builds truthful protection copy from observed tunnel traffic, not VPN state alone. */
object ProtectionDiagnostics {
    /**
     * Converts persisted service status into a safe, actionable startup phase.
     * Raw exception messages may contain local paths or endpoints, so only the
     * known phase and exception class are included in support reports.
     */
    fun startupFailureSummary(serviceMessage: String?): String {
        val message = serviceMessage?.trim().orEmpty()
        if (message.isEmpty()) return "최근 시작 오류 기록 없음"
        val phase = when {
            message.startsWith("포그라운드 서비스 시작 실패") -> "Android 포그라운드 서비스 시작 실패"
            message.startsWith("필터 규칙 로드 실패") -> "필터 규칙/리소스 로드 실패"
            message.startsWith("업스트림 DNS 연결을 확인하지 못해") ||
                message.startsWith("보호 시작 전 DNS 연결 확인 실패") -> "업스트림 DNS 사전 연결 확인 실패"
            message.startsWith("VPN 연결 생성 실패") -> "Android VPN 인터페이스 생성 실패"
            message.startsWith("보호 시작 중 예기치 않은 오류") -> "VPN 시작 중 런타임 예외"
            else -> return "최근 시작 오류 기록 없음"
        }
        val exceptionType = Regex("\\(([A-Za-z][A-Za-z0-9_]*(?:Exception|Error))(?::|\\))")
            .find(message)?.groupValues?.getOrNull(1)
        return phase + (exceptionType?.let { " ($it)" } ?: "")
    }
    fun statusDetail(
        active: Boolean,
        queries: Long,
        blocked: Long,
        forwarded: Long,
        failed: Long,
        privateDnsMode: String?,
        serviceMessage: String? = null
    ): String {
        if (!active) return serviceMessage?.takeIf { it.isNotBlank() }
            ?: "보호를 시작하면 시스템 가상 DNS를 통해 필터링합니다."
        val observed = "Guardian 관측 DNS 질의 ${queries.coerceAtLeast(0)}건 · 차단 ${blocked.coerceAtLeast(0)} · 업스트림 전달 ${forwarded.coerceAtLeast(0)} · 응답 실패 ${failed.coerceAtLeast(0)}."
        return when (privateDnsMode) {
            "hostname" -> "VPN 터널은 실행 중이지만 Android 비공개 DNS 공급자가 암호화 DNS를 Guardian 필터 밖으로 보낼 수 있습니다. $observed"
            "opportunistic" -> "VPN 터널은 실행 중입니다. Android 비공개 DNS 자동 모드가 네트워크에 따라 필터를 우회할 수 있습니다. $observed"
            else -> if (queries <= 0L) {
                "VPN 터널은 실행 중이지만 아직 DNS 질의를 관측하지 못했습니다. 앱이나 웹사이트를 열어 요청을 발생시켜 주세요. 실제 광고 제거 여부는 별도 확인이 필요합니다."
            } else {
                "$observed 이 수치는 Guardian이 처리한 DNS 요청이며, 앱 내부 광고나 암호화 DNS까지 차단한다는 뜻은 아닙니다."
            }
        }
    }

    /**
     * Creates a support report from non-sensitive runtime metadata only.
     * DNS history, custom rules, license credentials, URLs and public keys are intentionally excluded.
     */
    fun supportReport(
        appVersion: String,
        androidRelease: String,
        apiLevel: Int,
        protectionActive: Boolean,
        bundledRuleAssetAvailable: Boolean,
        selectedProfile: String,
        baseCacheBytes: Long,
        securityFilterEnabled: Boolean,
        securityCacheBytes: Long,
        androidAdsFilterEnabled: Boolean,
        androidAdsCacheBytes: Long,
        autoUpdateEnabled: Boolean,
        updateIntervalHours: Long,
        hasBaseUpdateTimestamp: Boolean,
        signedFilterDeliveryConfigured: Boolean,
        licenseApiConfigured: Boolean,
        filterUpdateErrorPresent: Boolean,
        lastServiceStatus: String? = null
    ): String {
        fun cacheState(enabled: Boolean, bytes: Long): String = when {
            !enabled -> "사용 안 함"
            bytes > 0L -> "있음 (${bytes} bytes)"
            else -> "없음"
        }
        return buildString {
            appendLine("Guardian AdBlock 진단 정보")
            appendLine("앱 버전: $appVersion")
            appendLine("Android: $androidRelease (API $apiLevel)")
            appendLine("VPN 보호 상태: ${if (protectionActive) "활성" else "비활성"}")
            appendLine("내장 DNS 규칙 파일: ${if (bundledRuleAssetAvailable) "정상" else "누락 또는 읽기 실패"}")
            appendLine("선택 필터 프로필: $selectedProfile")
            appendLine("기본 필터 캐시: ${cacheState(true, baseCacheBytes)}")
            appendLine("보안 필터: ${cacheState(securityFilterEnabled, securityCacheBytes)}")
            appendLine("Android 앱 광고 필터: ${cacheState(androidAdsFilterEnabled, androidAdsCacheBytes)}")
            appendLine("자동 업데이트: ${if (autoUpdateEnabled) "켜짐" else "꺼짐"}")
            appendLine("업데이트 주기: ${updateIntervalHours.coerceAtLeast(1)}시간")
            appendLine("기본 필터 업데이트 기록: ${if (hasBaseUpdateTimestamp) "있음" else "없음"}")
            appendLine("서명 필터 배포 설정: ${if (signedFilterDeliveryConfigured) "설정됨" else "미설정"}")
            appendLine("라이선스 API 설정: ${if (licenseApiConfigured) "설정됨" else "미설정"}")
            appendLine("최근 필터 업데이트 오류 기록: ${if (filterUpdateErrorPresent) "있음" else "없음"}")
            appendLine("최근 보호 시작 오류: ${startupFailureSummary(lastServiceStatus)}")
            appendLine()
            appendLine("개인정보 보호: DNS 요청/방문 도메인, 사용자 지정 차단·허용 규칙, 라이선스 키·토큰, 서버 주소 및 공개키는 이 보고서에 포함하지 않았습니다.")
        }
    }
}