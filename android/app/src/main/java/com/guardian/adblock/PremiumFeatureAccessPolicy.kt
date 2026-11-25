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

    private val knownFeatures = setOf(
        ADVANCED_FILTERS,
        APP_PROTECTION,
        THREAT_ANALYTICS,
        SECURITY_PROFILES,
        PHISHING_SCANNER,
        PREMIUM_REPORTS
    )

    fun isEntitled(
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
        proActive -> "PRO 라이선스 확인됨"
        developmentPreview -> "개발 미리보기 · 권한 미부여"
        else -> "PRO 라이선스 필요"
    }
}
