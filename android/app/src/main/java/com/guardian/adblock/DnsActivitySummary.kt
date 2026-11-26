package com.guardian.adblock

import java.util.Locale

/**
 * Summarizes only the bounded recent activity log. This is not a lifetime
 * domain ranking and must not be presented as a threat taxonomy.
 */
internal object DnsActivitySummary {
    fun recentBlockedDomains(raw: String?, limit: Int = 5): String {
        if (limit <= 0) return ""
        val counts = raw.orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                if (!line.startsWith("차단 · ")) return@mapNotNull null
                line.removePrefix("차단 · ").trim().takeIf { it.isNotEmpty() }
            }
            .groupingBy { it }
            .eachCount()
        if (counts.isEmpty()) return "최근 기록에 차단된 도메인이 없습니다."

        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .joinToString("\n") { (domain, count) ->
                "$domain · ${String.format(Locale.ROOT, "%,d", count)}회"
            }
    }
}
