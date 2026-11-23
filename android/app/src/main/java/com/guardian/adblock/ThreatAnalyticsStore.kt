package com.guardian.adblock

import android.content.Context

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

/** Stores aggregate threat classifications only; never persists the queried domain or URL. */
internal object ThreatAnalyticsStore {
    private const val PREFS = "guardian_threat_analytics_v1"
    private const val KEY_HIGH = "high"
    private const val KEY_INFORMATIONAL = "informational"
    private const val KEY_UNASSESSED = "unassessed"
    private const val KEY_RECENT = "recent_events"
    private const val MAX_RECENT = 20

    @Synchronized
    fun record(context: Context, assessment: ThreatAnalyticsEngine.Assessment?) {
        assessment ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val key = when (assessment.severity) {
            ThreatAnalyticsEngine.Severity.HIGH -> KEY_HIGH
            ThreatAnalyticsEngine.Severity.INFORMATIONAL -> KEY_INFORMATIONAL
            ThreatAnalyticsEngine.Severity.UNASSESSED -> KEY_UNASSESSED
        }
        val old = prefs.getLong(key, 0L).coerceAtLeast(0L)
        editor.putLong(key, if (Long.MAX_VALUE - old < 1L) Long.MAX_VALUE else old + 1L)
        val event = System.currentTimeMillis().toString() + "|" +
            assessment.severity.name + "|" + assessment.title
        val previous = prefs.getString(KEY_RECENT, "").orEmpty()
            .lineSequence().filter { it.isNotBlank() }.toList()
        editor.putString(KEY_RECENT, (listOf(event) + previous).take(MAX_RECENT).joinToString("\n"))
            .apply()
    }

    fun summary(context: Context): ThreatAnalyticsSummary {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val recent = prefs.getString(KEY_RECENT, "").orEmpty().lineSequence()
            .mapNotNull { line ->
                val parts = line.split('|', limit = 3)
                if (parts.size != 3) return@mapNotNull null
                val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
                val severity = runCatching { ThreatAnalyticsEngine.Severity.valueOf(parts[1]) }.getOrNull()
                    ?: return@mapNotNull null
                ThreatAnalyticsEvent(timestamp, severity, parts[2])
            }.take(MAX_RECENT).toList()
        return ThreatAnalyticsSummary(
            high = prefs.getLong(KEY_HIGH, 0L).coerceAtLeast(0L),
            informational = prefs.getLong(KEY_INFORMATIONAL, 0L).coerceAtLeast(0L),
            unassessed = prefs.getLong(KEY_UNASSESSED, 0L).coerceAtLeast(0L),
            recent = recent
        )
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
