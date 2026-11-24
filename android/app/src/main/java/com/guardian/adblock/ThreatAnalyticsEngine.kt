package com.guardian.adblock

/**
 * Evidence-based threat classification. It never invents a malware/phishing verdict
 * from a generic DNS block or an ad/tracker category.
 */
internal object ThreatAnalyticsEngine {
    enum class Evidence {
        SECURITY_INTELLIGENCE_LIST,
        AD_FILTER,
        TRACKER_FILTER,
        USER_BLOCK_RULE,
        UNKNOWN_BLOCK_SOURCE
    }

    enum class Severity {
        HIGH,
        INFORMATIONAL,
        UNASSESSED
    }

    data class Assessment(
        val severity: Severity,
        val evidence: Set<Evidence>,
        val title: String,
        val explanation: String
    )

    fun assess(blocked: Boolean, evidence: Set<Evidence>): Assessment? {
        if (!blocked) return null
        return when {
            Evidence.SECURITY_INTELLIGENCE_LIST in evidence -> Assessment(
                Severity.HIGH,
                evidence,
                "위협 인텔리전스 목록 일치",
                "위협 인텔리전스 차단 목록에 포함된 도메인입니다. 목록 일치만으로 구체적인 악성 행위를 확정하지 않습니다."
            )
            Evidence.AD_FILTER in evidence || Evidence.TRACKER_FILTER in evidence -> Assessment(
                Severity.INFORMATIONAL,
                evidence,
                "광고·추적 차단",
                "광고 또는 추적 필터 규칙과 일치했습니다. 악성코드 탐지로 분류하지 않습니다."
            )
            Evidence.USER_BLOCK_RULE in evidence -> Assessment(
                Severity.UNASSESSED,
                evidence,
                "사용자 지정 차단",
                "사용자가 직접 등록한 차단 규칙과 일치했습니다. 보안 위협으로 판정한 결과는 아닙니다."
            )
            else -> Assessment(
                Severity.UNASSESSED,
                evidence.ifEmpty { setOf(Evidence.UNKNOWN_BLOCK_SOURCE) },
                "분류되지 않은 DNS 차단",
                "차단은 확인됐지만 규칙 출처가 확인되지 않아 위험도를 산정하지 않았습니다."
            )
        }
    }
}
