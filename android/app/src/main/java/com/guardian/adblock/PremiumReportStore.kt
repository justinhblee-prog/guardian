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
    private const val ENTITLEMENT_CACHE_SECONDS = 30L
    private const val FLUSH_INTERVAL_MILLIS = 5_000L
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    private data class PendingCounters(
        val date: LocalDate,
        var queries: Long = 0L,
        var blocked: Long = 0L,
        var forwarded: Long = 0L,
        var failed: Long = 0L,
        var ads: Long = 0L,
        var trackers: Long = 0L,
        var other: Long = 0L
    )

    private var pending: PendingCounters? = null
    private var lastFlushAtMillis = 0L
    @Volatile private var entitlementCheckedAtSeconds = Long.MIN_VALUE
    @Volatile private var cachedEntitlementActive = false
    @Volatile private var cachedEntitlementExpiresAt = 0L

    /** Called after activation, renewal, or explicit device release. */
    fun invalidateEntitlementCache() {
        entitlementCheckedAtSeconds = Long.MIN_VALUE
    }

    private fun hasActiveProEntitlement(context: Context, nowSeconds: Long): Boolean {
        val checkedAt = entitlementCheckedAtSeconds
        if (checkedAt != Long.MIN_VALUE &&
            nowSeconds >= checkedAt &&
            nowSeconds - checkedAt < ENTITLEMENT_CACHE_SECONDS &&
            (!cachedEntitlementActive || nowSeconds < cachedEntitlementExpiresAt)
        ) {
            return cachedEntitlementActive
        }
        val entitlement = LicenseEntitlementStore.current(context, nowSeconds)
        val active = PremiumEntitlementPolicy.isEntitlementActive(entitlement, nowSeconds)
        cachedEntitlementActive = active
        cachedEntitlementExpiresAt = entitlement?.expiresAt ?: 0L
        entitlementCheckedAtSeconds = nowSeconds
        return active
    }

    /**
     * Buffers only aggregate counters; no domain, URL, package name or app identity is stored.
     * Flushes every five seconds to avoid a second disk write for every DNS packet.
     */
    @Synchronized
    fun recordDnsEvent(
        context: Context,
        timestampEpochMillis: Long,
        blocked: Boolean,
        forwarded: Boolean,
        category: DomainFilter.BlockCategory?
    ) {
        val nowSeconds = timestampEpochMillis / 1000L
        if (!hasActiveProEntitlement(context, nowSeconds)) return

        val date = Instant.ofEpochMilli(timestampEpochMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        if (pending?.date != null && pending?.date != date) {
            flushPendingLocked(context, timestampEpochMillis)
        }
        val counters = pending ?: PendingCounters(date).also { pending = it }
        counters.queries = saturatingAdd(counters.queries, 1L)
        if (blocked) counters.blocked = saturatingAdd(counters.blocked, 1L)
        if (forwarded) counters.forwarded = saturatingAdd(counters.forwarded, 1L)
        if (!blocked && !forwarded) counters.failed = saturatingAdd(counters.failed, 1L)
        when (category) {
            DomainFilter.BlockCategory.AD -> counters.ads = saturatingAdd(counters.ads, 1L)
            DomainFilter.BlockCategory.TRACKER -> counters.trackers = saturatingAdd(counters.trackers, 1L)
            DomainFilter.BlockCategory.OTHER -> counters.other = saturatingAdd(counters.other, 1L)
            null -> Unit
        }
        if (lastFlushAtMillis == 0L || timestampEpochMillis - lastFlushAtMillis >= FLUSH_INTERVAL_MILLIS) {
            flushPendingLocked(context, timestampEpochMillis)
        }
    }

    @Synchronized
    fun flushPending(context: Context) {
        flushPendingLocked(context, System.currentTimeMillis())
    }

    private fun flushPendingLocked(context: Context, nowEpochMillis: Long) {
        val counters = pending ?: return
        val key = counters.date.format(formatter)
        val prefix = "day.$key."
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        fun add(name: String, amount: Long) {
            val current = prefs.getLong(prefix + name, 0L).coerceAtLeast(0L)
            editor.putLong(prefix + name, saturatingAdd(current, amount))
        }
        add("queries", counters.queries)
        add("blocked", counters.blocked)
        add("forwarded", counters.forwarded)
        add("failed", counters.failed)
        add("ads", counters.ads)
        add("trackers", counters.trackers)
        add("other", counters.other)

        val dates = prefs.getStringSet(KEY_DATES, emptySet()).orEmpty().toMutableSet()
        dates.add(key)
        val oldest = counters.date.minusDays((RETENTION_DAYS - 1).toLong())
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
        pending = null
        lastFlushAtMillis = nowEpochMillis
    }

    @Synchronized
    fun readSummary(
        context: Context,
        windowDays: Int,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): PremiumReportSummary? {
        require(windowDays in 1..MAX_WINDOW_DAYS) { "리포트 기간은 1~90일이어야 합니다." }
        flushPendingLocked(context, nowEpochMillis)
        val nowSeconds = nowEpochMillis / 1000L
        val entitlement = LicenseEntitlementStore.current(context, nowSeconds) ?: return null
        if (!PremiumEntitlementPolicy.canUse(
                PremiumEntitlementPolicy.Feature.PREMIUM_REPORTS,
                entitlement,
                nowSeconds
            )
        ) return null

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
