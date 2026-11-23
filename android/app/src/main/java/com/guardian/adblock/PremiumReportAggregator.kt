package com.guardian.adblock

import java.time.LocalDate

internal data class PremiumDailyMetrics(
    val date: LocalDate,
    val queries: Long = 0L,
    val blocked: Long = 0L,
    val forwarded: Long = 0L,
    val failed: Long = 0L,
    val ads: Long = 0L,
    val trackers: Long = 0L,
    val other: Long = 0L
)

internal data class PremiumReportSummary(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val daysWithActivity: Int,
    val queries: Long,
    val blocked: Long,
    val forwarded: Long,
    val failed: Long,
    val ads: Long,
    val trackers: Long,
    val other: Long
) {
    val blockRatePercent: Int
        get() = if (queries <= 0L) 0 else ((blocked.coerceAtMost(queries) * 100L) / queries).toInt()
}

/** Pure aggregation kept separate from Android storage so report math is testable. */
internal object PremiumReportAggregator {
    fun aggregate(
        startDate: LocalDate,
        endDate: LocalDate,
        daily: List<PremiumDailyMetrics>
    ): PremiumReportSummary {
        require(!endDate.isBefore(startDate)) { "리포트 종료일이 시작일보다 빠릅니다." }
        val included = daily.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
        fun total(selector: (PremiumDailyMetrics) -> Long): Long =
            included.fold(0L) { sum, row ->
                val value = selector(row).coerceAtLeast(0L)
                if (Long.MAX_VALUE - sum < value) Long.MAX_VALUE else sum + value
            }
        val queries = total { it.queries }
        val blocked = total { it.blocked }.coerceAtMost(queries)
        return PremiumReportSummary(
            startDate = startDate,
            endDate = endDate,
            daysWithActivity = included.count { it.queries > 0L },
            queries = queries,
            blocked = blocked,
            forwarded = total { it.forwarded },
            failed = total { it.failed },
            ads = total { it.ads },
            trackers = total { it.trackers },
            other = total { it.other }
        )
    }
}
