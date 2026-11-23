package com.guardian.adblock

/** Exports only aggregate counters; never includes queried domains or app identifiers. */
internal object PremiumReportCsv {
    fun render(summary: PremiumReportSummary): String = buildString {
        append('\uFEFF')
        appendLine("start_date,end_date,days_with_activity,queries,blocked,block_rate_percent,forwarded,failed,ads,trackers,other")
        appendLine(
            listOf(
                summary.startDate.toString(),
                summary.endDate.toString(),
                summary.daysWithActivity.coerceAtLeast(0).toString(),
                summary.queries.coerceAtLeast(0L).toString(),
                summary.blocked.coerceAtLeast(0L).coerceAtMost(summary.queries.coerceAtLeast(0L)).toString(),
                summary.blockRatePercent.coerceIn(0, 100).toString(),
                summary.forwarded.coerceAtLeast(0L).toString(),
                summary.failed.coerceAtLeast(0L).toString(),
                summary.ads.coerceAtLeast(0L).toString(),
                summary.trackers.coerceAtLeast(0L).toString(),
                summary.other.coerceAtLeast(0L).toString()
            ).joinToString(",")
        )
    }
}
