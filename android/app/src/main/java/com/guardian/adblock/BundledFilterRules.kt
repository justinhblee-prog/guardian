package com.guardian.adblock

import java.io.Reader

/**
 * The bundled DNS rules are the mandatory minimum for a usable free protection
 * session. Keep asset parsing and the non-empty baseline check identical in the
 * UI readiness check and VPN service startup path.
 */
internal object BundledFilterRules {
    const val ASSET_NAME = "filter_rules.txt"

    fun parse(reader: Reader): FilterRuleParser.Parsed {
        val parsed = FilterRuleParser.parse(reader)
        check(
            FilterStartupGate.hasUsableBundledBaseline(
                parsed.blocked.size,
                parsed.importantBlocked.size
            )
        ) {
            "앱에 포함된 기본 DNS 규칙 파일에 유효한 차단 규칙이 없습니다."
        }
        return parsed
    }
}
