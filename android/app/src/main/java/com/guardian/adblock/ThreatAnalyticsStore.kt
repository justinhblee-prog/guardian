package com.guardian.adblock

import android.content.Context
import android.os.SystemClock

/** Persists aggregate threat classifications only; queried domains and URLs are never stored. */
internal object ThreatAnalyticsStore {
    private const val PREFS = "guardian_threat_analytics_v1"
    private const val KEY_HIGH = "high"
    private const val KEY_INFORMATIONAL = "informational"
    private const val KEY_UNASSESSED = "unassessed"
    private const val KEY_RECENT = "recent_events"
    private const val MAX_RECENT = 20
    private const val FLUSH_INTERVAL_MILLIS = 5_000L

    private val pending = ThreatAnalyticsAccumulator(MAX_RECENT)
    private var lastFlushAtElapsedMillis = 0L

    @Synchronized
    fun record(context: Context, assessment: ThreatAnalyticsEngine.Assessment?) {
        if (assessment == null) return
        pending.add(assessment, System.currentTimeMillis())
        val now = SystemClock.elapsedRealtime()
        if (lastFlushAtElapsedMillis == 0L ||
            now - lastFlushAtElapsedMillis >= FLUSH_INTERVAL_MILLIS
        ) {
            flushPendingLocked(context, now)
        }
    }

    @Synchronized
    fun flushPending(context: Context) {
        flushPendingLocked(context, SystemClock.elapsedRealtime())
    }

    @Synchronized
    fun summary(context: Context): ThreatAnalyticsSummary {
        val now = SystemClock.elapsedRealtime()
        // Preserve the five-second write buffer even when the live preview is
        // refreshed more often by status broadcasts. The non-destructive pending
        // snapshot below makes those events visible without an early disk write.
        if (lastFlushAtElapsedMillis == 0L || now - lastFlushAtElapsedMillis >= FLUSH_INTERVAL_MILLIS) {
            flushPendingLocked(context, now)
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedRecent = prefs.getString(KEY_RECENT, "").orEmpty().lineSequence()
            .mapNotNull { line ->
                val parts = line.split('|', limit = 3)
                if (parts.size != 3) return@mapNotNull null
                val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
                val severity = runCatching {
                    ThreatAnalyticsEngine.Severity.valueOf(parts[1])
                }.getOrNull() ?: return@mapNotNull null
                ThreatAnalyticsEvent(timestamp, severity, parts[2])
            }.toList()
        val pendingSnapshot = pending.snapshot()
        val storedSummary = ThreatAnalyticsSummary(
            high = prefs.getLong(KEY_HIGH, 0L).coerceAtLeast(0L),
            informational = prefs.getLong(KEY_INFORMATIONAL, 0L).coerceAtLeast(0L),
            unassessed = prefs.getLong(KEY_UNASSESSED, 0L).coerceAtLeast(0L),
            recent = storedRecent
        )
        return ThreatAnalyticsSummaryPolicy.merge(storedSummary, pendingSnapshot, MAX_RECENT)
    }

    @Synchronized
    fun clear(context: Context) {
        pending.drain()
        lastFlushAtElapsedMillis = 0L
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun flushPendingLocked(context: Context, nowElapsedMillis: Long) {
        val batch = pending.drain()
        if (batch.isEmpty) {
            lastFlushAtElapsedMillis = nowElapsedMillis
            return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putLong(KEY_HIGH, saturatingAdd(
            prefs.getLong(KEY_HIGH, 0L).coerceAtLeast(0L), batch.high
        ))
        editor.putLong(KEY_INFORMATIONAL, saturatingAdd(
            prefs.getLong(KEY_INFORMATIONAL, 0L).coerceAtLeast(0L), batch.informational
        ))
        editor.putLong(KEY_UNASSESSED, saturatingAdd(
            prefs.getLong(KEY_UNASSESSED, 0L).coerceAtLeast(0L), batch.unassessed
        ))
        val previous = prefs.getString(KEY_RECENT, "").orEmpty()
            .lineSequence().filter { it.isNotBlank() }.toList()
        val newest = batch.recent.map { event ->
            event.timestampEpochMillis.toString() + "|" + event.severity.name + "|" + event.title
        }
        editor.putString(KEY_RECENT, (newest + previous).take(MAX_RECENT).joinToString("\n"))
        editor.apply()
        lastFlushAtElapsedMillis = nowElapsedMillis
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}
