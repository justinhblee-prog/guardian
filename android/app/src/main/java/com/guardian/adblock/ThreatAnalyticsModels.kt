package com.guardian.adblock

internal data class ThreatAnalyticsEvent(
    val timestampEpochMillis: Long,
    val severity: ThreatAnalyticsEngine.Severity,
    val title: String
)

internal data class ThreatAnalyticsSummary(
    val high: Long,
    val informational: Long,
    val unassessed: Long,
    val recent: List<ThreatAnalyticsEvent>
)

internal data class ThreatAnalyticsBatch(
    val high: Long,
    val informational: Long,
    val unassessed: Long,
    val recent: List<ThreatAnalyticsEvent>
) {
    val isEmpty: Boolean
        get() = high == 0L && informational == 0L && unassessed == 0L && recent.isEmpty()
}

/** Batches only aggregate severity counters and fixed labels, never domains or URLs. */
internal class ThreatAnalyticsAccumulator(private val maxRecent: Int = 20) {
    init {
        require(maxRecent > 0)
    }

    private var high = 0L
    private var informational = 0L
    private var unassessed = 0L
    private val recent = mutableListOf<ThreatAnalyticsEvent>()

    val hasPending: Boolean
        get() = high > 0L || informational > 0L || unassessed > 0L || recent.isNotEmpty()

    fun add(
        assessment: ThreatAnalyticsEngine.Assessment?,
        timestampEpochMillis: Long
    ) {
        assessment ?: return
        when (assessment.severity) {
            ThreatAnalyticsEngine.Severity.HIGH -> high = saturatingAdd(high, 1L)
            ThreatAnalyticsEngine.Severity.INFORMATIONAL -> informational = saturatingAdd(informational, 1L)
            ThreatAnalyticsEngine.Severity.UNASSESSED -> unassessed = saturatingAdd(unassessed, 1L)
        }
        recent.add(0, ThreatAnalyticsEvent(timestampEpochMillis, assessment.severity, assessment.title))
        if (recent.size > maxRecent) recent.removeAt(recent.lastIndex)
    }

    /** Returns a non-destructive view for live UI refresh without forcing disk flushes. */
    fun snapshot(): ThreatAnalyticsBatch = ThreatAnalyticsBatch(
        high = high,
        informational = informational,
        unassessed = unassessed,
        recent = recent.toList()
    )

    fun drain(): ThreatAnalyticsBatch {
        val batch = ThreatAnalyticsBatch(high, informational, unassessed, recent.toList())
        high = 0L
        informational = 0L
        unassessed = 0L
        recent.clear()
        return batch
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}
