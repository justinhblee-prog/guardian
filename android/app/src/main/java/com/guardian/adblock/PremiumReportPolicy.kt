package com.guardian.adblock

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Privacy-preserving daily aggregates for PRO reports.
 *
 * Only counters and a local calendar date are persisted. Queried domains,
 * package names, IP addresses and raw DNS events are deliberately excluded.
 */
internal object PremiumReportPolicy {
    const val PREFERENCE_KEY = "premium_report_daily_v1"
    const val RETENTION_DAYS = 90

    data class DailyTotals(
        val date: LocalDate,
        val queries: Long = 0L,
        val blocked: Long = 0L,
        val ads: Long = 0L,
        val trackers: Long = 0L,
        val other: Long = 0L,
        val forwarded: Long = 0L,
        val failed: Long = 0L
    )

    data class Summary(
        val queries: Long,
        val blocked: Long,
        val ads: Long,
        val trackers: Long,
        val other: Long,
        val forwarded: Long,
        val failed: Long
    ) {
        val blockRatePercent: Int
            get() = if (queries <= 0L) 0 else ((blocked.toDouble() * 100.0) / queries.toDouble()).toInt().coerceIn(0, 100)
    }

    fun record(
        serialized: String,
        date: LocalDate,
        blocked: Boolean,
        forwarded: Boolean,
        category: String?
    ): String {
        val rows = parse(serialized).associateBy { it.date }.toMutableMap()
        val current = rows[date] ?: DailyTotals(date)
        val next = current.copy(
            queries = increment(current.queries),
            blocked = incrementIf(current.blocked, blocked),
            ads = incrementIf(current.ads, blocked && category == "ad"),
            trackers = incrementIf(current.trackers, blocked && category == "tracker"),
            other = incrementIf(current.other, blocked && category != "ad" && category != "tracker"),
            forwarded = incrementIf(current.forwarded, forwarded),
            failed = incrementIf(current.failed, !blocked && !forwarded)
        )
        rows[date] = next
        val oldest = date.minusDays((RETENTION_DAYS - 1).toLong())
        return rows.values
            .filter { !it.date.isBefore(oldest) && !it.date.isAfter(date) }
            .sortedBy { it.date }
            .joinToString("\n", transform = ::encode)
    }

    fun summarize(serialized: String, fromInclusive: LocalDate, toInclusive: LocalDate): Summary {
        require(!toInclusive.isBefore(fromInclusive)) { "Report date range is invalid." }
        val totals = parse(serialized).asSequence()
            .filter { !it.date.isBefore(fromInclusive) && !it.date.isAfter(toInclusive) }
            .fold(DailyTotals(fromInclusive)) { acc, row ->
                acc.copy(
                    queries = add(acc.queries, row.queries),
                    blocked = add(acc.blocked, row.blocked),
                    ads = add(acc.ads, row.ads),
                    trackers = add(acc.trackers, row.trackers),
                    other = add(acc.other, row.other),
                    forwarded = add(acc.forwarded, row.forwarded),
                    failed = add(acc.failed, row.failed)
                )
            }
        return Summary(
            totals.queries, totals.blocked, totals.ads, totals.trackers,
            totals.other, totals.forwarded, totals.failed
        )
    }

    fun prune(serialized: String, today: LocalDate): String {
        val oldest = today.minusDays((RETENTION_DAYS - 1).toLong())
        return parse(serialized).asSequence()
            .filter { !it.date.isBefore(oldest) && !it.date.isAfter(today) }
            .sortedBy { it.date }
            .joinToString("\n", transform = ::encode)
    }

    private fun parse(serialized: String): List<DailyTotals> {
        if (serialized.isBlank()) return emptyList()
        val rows = LinkedHashMap<LocalDate, DailyTotals>()
        serialized.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split('|')
            if (parts.size != 8) return@forEach
            val date = try { LocalDate.parse(parts[0]) } catch (_: DateTimeParseException) { return@forEach }
            val values = parts.drop(1).map { it.toLongOrNull()?.takeIf { n -> n >= 0L } ?: return@forEach }
            val incoming = DailyTotals(date, values[0], values[1], values[2], values[3], values[4], values[5], values[6])
            if (!isConsistent(incoming)) return@forEach
            val previous = rows[date]
            val merged = if (previous == null) incoming else DailyTotals(
                date,
                add(previous.queries, incoming.queries),
                add(previous.blocked, incoming.blocked),
                add(previous.ads, incoming.ads),
                add(previous.trackers, incoming.trackers),
                add(previous.other, incoming.other),
                add(previous.forwarded, incoming.forwarded),
                add(previous.failed, incoming.failed)
            )
            if (isConsistent(merged)) rows[date] = merged
        }
        return rows.values.toList()
    }

    private fun isConsistent(row: DailyTotals): Boolean =
        row.blocked <= row.queries &&
            add(add(row.ads, row.trackers), row.other) <= row.blocked &&
            add(add(row.blocked, row.forwarded), row.failed) <= row.queries

    private fun encode(row: DailyTotals): String = listOf(
        row.date.toString(), row.queries, row.blocked, row.ads, row.trackers,
        row.other, row.forwarded, row.failed
    ).joinToString("|")

    private fun increment(value: Long): Long = if (value == Long.MAX_VALUE) value else value + 1L
    private fun incrementIf(value: Long, condition: Boolean): Long = if (condition) increment(value) else value
    private fun add(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}
