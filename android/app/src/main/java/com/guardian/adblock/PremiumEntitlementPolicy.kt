package com.guardian.adblock

/**
 * Fail-closed entitlement policy for the six advertised Guardian PRO capabilities.
 *
 * A valid signed entitlement is necessary but not sufficient: a capability remains
 * unavailable until its production implementation is explicitly registered here.
 * Debug/preview UI must never be used as an authorization signal.
 */
internal object PremiumEntitlementPolicy {
    enum class Feature(val displayName: String) {
        ADVANCED_FILTERING("고급 광고·추적 차단"),
        PER_APP_PROTECTION("앱별 세밀한 보호 제어"),
        THREAT_ANALYTICS("실시간 위협·DNS 활동 분석"),
        SECURITY_AUTOMATION("보안 프로필 및 자동화"),
        PHISHING_AND_APP_SCAN("피싱 링크·앱 코드 검사"),
        PREMIUM_REPORTS("프리미엄 리포트 및 통계")
    }

    // Keep empty until a feature has a real engine, UI action path and passing tests.
    // Adding a feature here is a security-sensitive release decision.
    private val productionReadyFeatures: Set<Feature> = setOf(Feature.PREMIUM_REPORTS)

    fun isEntitlementActive(
        entitlement: LicenseEntitlementVerifier.Entitlement?,
        nowEpochSeconds: Long
    ): Boolean {
        if (entitlement == null) return false
        return entitlement.product in setOf("guardian", "guardian-android") &&
            entitlement.entitlement == "pro" &&
            entitlement.issuedAt > 0L &&
            entitlement.issuedAt <= nowEpochSeconds + 300L &&
            entitlement.expiresAt > nowEpochSeconds &&
            entitlement.licenseRef.matches(Regex("[a-zA-Z0-9._-]{8,128}")) &&
            entitlement.deviceBinding.matches(Regex("[a-fA-F0-9]{64}"))
    }

    fun canUse(
        feature: Feature,
        entitlement: LicenseEntitlementVerifier.Entitlement?,
        nowEpochSeconds: Long
    ): Boolean = feature in productionReadyFeatures &&
        isEntitlementActive(entitlement, nowEpochSeconds)

    internal fun productionReadyFeaturesForTest(): Set<Feature> = productionReadyFeatures
}
