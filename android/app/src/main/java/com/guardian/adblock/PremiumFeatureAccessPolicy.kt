package com.guardian.adblock

/**
 * Central entitlement policy for Guardian Premium capabilities.
 *
 * A development preview is presentation-only and must never be used by a
 * production build to grant access. Feature IDs are stable keys shared by
 * UI surfaces and feature implementations.
 */
internal object PremiumFeatureAccessPolicy {
    const val ADVANCED_FILTERS = "advanced_filters"
    const val APP_PROTECTION = "app_protection"
    const val THREAT_ANALYTICS = "threat_analytics"
    const val SECURITY_PROFILES = "security_profiles"
    const val PHISHING_SCANNER = "phishing_scanner"
    const val PREMIUM_REPORTS = "premium_reports"

    data class Feature(
        val id: String,
        val name: String,
        val description: String,
        val engineStatus: String,
        val engineReady: Boolean
    )

    val features = listOf(
        Feature(ADVANCED_FILTERS, "고급 광고·추적 차단", "필터 엔진 기반 차단 정책과 사용자 규칙을 확장하는 기능", "기본 필터 엔진 연결됨 · 프리미엄 정책 확장 필요", true),
        Feature(APP_PROTECTION, "앱별 세밀한 보호 제어", "앱 단위 보호 제외와 예외 정책을 관리하는 기능", "기본 앱 제외 기능 존재 · 프리미엄 정책 연결 필요", false),
        Feature(THREAT_ANALYTICS, "실시간 위협·DNS 활동 분석", "차단 이벤트와 DNS 요청을 분류해 활동 내역으로 제공", "기본 DNS 통계 존재 · 고급 분석 엔진 개발 필요", false),
        Feature(SECURITY_PROFILES, "보안 프로필 및 자동화", "상황별 보호 정책과 자동 전환을 관리하는 기능", "프로필 자동화 엔진 개발 필요", false),
        Feature(PHISHING_SCANNER, "피싱 링크·앱 코드 검사", "의심 URL과 앱 코드의 위험도를 검사하는 기능", "검사 엔진 미연결 · 실제 탐지 안 함", false),
        Feature(PREMIUM_REPORTS, "프리미엄 리포트 및 통계", "기간별 차단량과 보호 상태를 요약하는 기능", "최근 90일 개인정보 비식별 DNS 집계 및 7일·30일 리포트 구현 · 검증 대기", false)
    )

    private val knownFeatures = features.mapTo(HashSet()) { it.id }

    fun isEntitled(
        featureId: String,
        proActive: Boolean,
        developmentPreview: Boolean
    ): Boolean = featureId in knownFeatures && proActive

    /** A paid entitlement alone never exposes an unfinished capability as usable. */
    fun isOperational(featureId: String, proActive: Boolean): Boolean =
        features.firstOrNull { it.id == featureId }?.let { proActive && it.engineReady } ?: false

    fun isVisible(
        featureId: String,
        proActive: Boolean,
        developmentPreview: Boolean
    ): Boolean = featureId in knownFeatures && (proActive || developmentPreview)

    fun statusLabel(
        featureId: String,
        proActive: Boolean,
        developmentPreview: Boolean
    ): String = when {
        featureId !in knownFeatures -> "지원하지 않는 기능"
        proActive && features.first { it.id == featureId }.engineReady -> "PRO 권한 확인 · 엔진 사용 가능"
        proActive -> "PRO 권한 확인 · 기능 엔진 개발 중"
        developmentPreview -> "개발 미리보기 · 권한 미부여"
        else -> "PRO 라이선스 필요"
    }
}
