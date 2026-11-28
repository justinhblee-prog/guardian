package com.guardian.adblock

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Local, domain-free daily aggregates for the Premium DNS report.
 * Storage contains dates and counters only; no hostnames, app IDs, IPs or URLs.
 */
internal data class DailyDnsStats(
    val date: LocalDate,
    val totalQueries: Long,
    val blockedQueries: Long,
    val forwardedQueries: Long,
    val failedQueries: Long,
    val adBlocked: Long,
    val trackerBlocked: Long,
    val otherBlocked: Long
)

internal object PremiumDailyStatsStore {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    private const val RETENTION_DAYS = 30

    fun parse(raw: String?): List<DailyDnsStats> {
        val parsed = linkedMapOf<LocalDate, DailyDnsStats>()
        raw.orEmpty().lineSequence().forEach { line ->
            val fields = line.split('|')
            if (fields.size != 8) return@forEach
            val date = runCatching { LocalDate.parse(fields[0], formatter) }.getOrNull()
                ?: return@forEach
            val values = fields.drop(1).map { it.toLongOrNull() ?: return@forEach }
            if (values.any { it < 0L }) return@forEach
            parsed.putIfAbsent(
                date,
                DailyDnsStats(
                    date = date,
                    totalQueries = values[0],
                    blockedQueries = values[1],
                    forwardedQueries = values[2],
                    failedQueries = values[3],
                    adBlocked = values[4],
                    trackerBlocked = values[5],
                    otherBlocked = values[6]
                )
            )
        }
        return parsed.values.sortedBy { it.date }
    }

    fun record(
        raw: String?,
        date: LocalDate,
        blocked: Boolean,
        forwarded: Boolean,
        category: DomainFilter.BlockCategory?
    ): String {
        val entries = parse(raw).associateBy { it.date }.toMutableMap()
        val previous = entries[date] ?: DailyDnsStats(date, 0, 0, 0, 0, 0, 0, 0)
        fun add(value: Long, increment: Long): Long =
            if (increment <= 0L) value else if (value == Long.MAX_VALUE) value
            else if (Long.MAX_VALUE - value < increment) Long.MAX_VALUE else value + increment

        val next = previous.copy(
            totalQueries = add(previous.totalQueries, 1),
            blockedQueries = add(previous.blockedQueries, if (blocked) 1 else 0),
            forwardedQueries = add(previous.forwardedQueries, if (!blocked && forwarded) 1 else 0),
            failedQueries = add(previous.failedQueries, if (!blocked && !forwarded) 1 else 0),
            adBlocked = add(previous.adBlocked, if (blocked && category == DomainFilter.BlockCategory.AD) 1 else 0),
            trackerBlocked = add(previous.trackerBlocked, if (blocked && category == DomainFilter.BlockCategory.TRACKER) 1 else 0),
            otherBlocked = add(previous.otherBlocked, if (blocked && category == DomainFilter.BlockCategory.OTHER) 1 else 0)
        )
        entries[date] = next
        val firstRetainedDate = date.minusDays((RETENTION_DAYS - 1).toLong())
        return entries.values
            .filter { !it.date.isBefore(firstRetainedDate) && !it.date.isAfter(date) }
            .sortedBy { it.date }
            .joinToString("\n") { stats ->
                listOf(
                    stats.date.format(formatter),
                    stats.totalQueries,
                    stats.blockedQueries,
                    stats.forwardedQueries,
                    stats.failedQueries,
                    stats.adBlocked,
                    stats.trackerBlocked,
                    stats.otherBlocked
                ).joinToString("|")
            }
    }
}
