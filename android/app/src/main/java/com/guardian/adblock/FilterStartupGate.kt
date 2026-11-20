package com.guardian.adblock

/**
 * Separates the minimum requirement for starting the local DNS tunnel from optional
 * downloaded filter feeds. An installation can start with bundled rules and report
 * missing enhancements without pretending those feeds are active.
 */
internal object FilterStartupGate {
    fun hasUsableBundledBaseline(blockedRules: Int, importantBlockedRules: Int): Boolean =
        blockedRules > 0 || importantBlockedRules > 0

    /** Distinguish the tiny bundled recovery list from selected full community feeds. */
    fun readinessMessage(bundledRuleCount: Int, unavailableFeeds: List<String>): String {
        val count = bundledRuleCount.coerceAtLeast(0)
        return if (unavailableFeeds.isEmpty()) {
            "선택 필터 준비 완료 · 기본 규칙 ${count}개 포함"
        } else {
            "기본 규칙 ${count}개만 우선 적용 중 · 필터 업데이트 필요: ${unavailableFeeds.distinct().joinToString()}"
        }
    }

    /**
     * Parses an optional feed without allowing a stale, missing, or malformed cache
     * to take down the DNS tunnel. A failed feed becomes an empty source and is
     * surfaced to the caller; mandatory bundled rules are validated separately.
     */
    fun loadOptionalRules(
        minimumRules: Int,
        load: () -> FilterRuleParser.Parsed,
        onUnavailable: (Throwable) -> Unit
    ): FilterRuleParser.Parsed {
        require(minimumRules > 0) { "Optional filter minimum must be positive." }
        return try {
            load().also { parsed ->
                val actualRules = parsed.blocked.size + parsed.importantBlocked.size
                check(actualRules >= minimumRules) {
                    "Optional filter has too few valid block rules: $actualRules"
                }
            }
        } catch (error: Exception) {
            onUnavailable(error)
            FilterRuleParser.parse(emptyList<String>())
        }
    }
}
