package com.guardian.adblock

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Local, domain-free daily aggregates for PRO security reports. */
internal object PremiumReportStore {
    private const val PREFS = "guardian_premium_reports_v1"
    private const val KEY_DATES = "stored_dates"
    private const val RETENTION_DAYS = 90
    private const val MAX_WINDOW_DAYS = 90
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    @Synchronized
    fun recordDnsEvent(
        context: Context,
        timestampEpochMillis: Long,
        blocked: Boolean,
        forwarded: Boolean,
        category: DomainFilter.BlockCategory?
    ) {
        val nowSeconds = timestampEpochMillis / 1000L
        val entitlement = LicenseEntitlementStore.current(context, nowSeconds) ?: return
        if (!PremiumEntitlementPolicy.isEntitlementActive(entitlement, nowSeconds)) return

        val date = Instant.ofEpochMilli(timestampEpochMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val key = date.format(formatter)
        val prefix = "day.$key."
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        fun increment(name: String, amount: Long = 1L) {
            val current = prefs.getLong(prefix + name, 0L).coerceAtLeast(0L)
            editor.putLong(prefix + name, saturatingAdd(current, amount))
        }

        increment("queries")
        if (blocked) increment("blocked")
        if (forwarded) increment("forwarded")
        if (!blocked && !forwarded) increment("failed")
        when (category) {
            DomainFilter.BlockCategory.AD -> increment("ads")
            DomainFilter.BlockCategory.TRACKER -> increment("trackers")
            DomainFilter.BlockCategory.OTHER -> increment("other")
            null -> Unit
        }

        val dates = prefs.getStringSet(KEY_DATES, emptySet()).orEmpty().toMutableSet()
        dates.add(key)
        val oldest = date.minusDays((RETENTION_DAYS - 1).toLong())
        dates.toList().forEach { stored ->
            val parsed = runCatching { LocalDate.parse(stored, formatter) }.getOrNull()
            if (parsed == null || parsed.isBefore(oldest)) {
                dates.remove(stored)
                listOf("queries", "blocked", "forwarded", "failed", "ads", "trackers", "other")
                    .forEach { editor.remove("day.$stored.$it") }
            }
        }
        editor.putStringSet(KEY_DATES, dates)
        editor.apply()
    }

    @Synchronized
    fun readSummary(
        context: Context,
        windowDays: Int,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): PremiumReportSummary? {
        require(windowDays in 1..MAX_WINDOW_DAYS) { "리포트 기간은 1~90일이어야 합니다." }
        val nowSeconds = nowEpochMillis / 1000L
        val entitlement = LicenseEntitlementStore.current(context, nowSeconds) ?: return null
        if (!PremiumEntitlementPolicy.isEntitlementActive(entitlement, nowSeconds)) return null

        val end = Instant.ofEpochMilli(nowEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val start = end.minusDays((windowDays - 1).toLong())
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dates = prefs.getStringSet(KEY_DATES, emptySet()).orEmpty()
        val daily = dates.mapNotNull { raw ->
            val date = runCatching { LocalDate.parse(raw, formatter) }.getOrNull() ?: return@mapNotNull null
            if (date.isBefore(start) || date.isAfter(end)) return@mapNotNull null
            val prefix = "day.$raw."
            PremiumDailyMetrics(
                date = date,
                queries = prefs.getLong(prefix + "queries", 0L).coerceAtLeast(0L),
                blocked = prefs.getLong(prefix + "blocked", 0L).coerceAtLeast(0L),
                forwarded = prefs.getLong(prefix + "forwarded", 0L).coerceAtLeast(0L),
                failed = prefs.getLong(prefix + "failed", 0L).coerceAtLeast(0L),
                ads = prefs.getLong(prefix + "ads", 0L).coerceAtLeast(0L),
                trackers = prefs.getLong(prefix + "trackers", 0L).coerceAtLeast(0L),
                other = prefs.getLong(prefix + "other", 0L).coerceAtLeast(0L)
            )
        }
        return PremiumReportAggregator.aggregate(start, end, daily)
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}
