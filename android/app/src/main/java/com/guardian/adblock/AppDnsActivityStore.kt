package com.guardian.adblock

import android.content.Context

internal data class AppDnsStats(
    val packageName: String,
    val queries: Long,
    val blocked: Long,
    val forwarded: Long,
    val failed: Long,
    val ads: Long,
    val trackers: Long,
    val other: Long
)

internal object AppDnsActivityStore {
    private const val PREFS = "guardian_app_dns_activity_v1"
    private const val KEY_PACKAGES = "tracked_packages"
    @Synchronized
    fun record(
        context: Context,
        packageName: String?,
        blocked: Boolean,
        forwarded: Boolean,
        category: DomainFilter.BlockCategory?
    ) {
        val safePackage = packageName?.takeIf {
            it.length in 3..255 && it.matches(Regex("[A-Za-z0-9_]+(?:\\\\.[A-Za-z0-9_]+)+"))
        } ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prefix = "app.$safePackage."
        val editor = prefs.edit()
        fun increment(key: String, amount: Long = 1L) {
            val old = prefs.getLong(prefix + key, 0L).coerceAtLeast(0L)
            editor.putLong(prefix + key, saturatingAdd(old, amount))
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
        val packages = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().toMutableSet()
        packages.add(safePackage)
        editor.putStringSet(KEY_PACKAGES, packages).apply()
    }

    @Synchronized
    fun snapshot(context: Context, limit: Int = 50): List<AppDnsStats> {
        require(limit in 1..200)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().mapNotNull { packageName ->
            if (!packageName.matches(Regex("[A-Za-z0-9_]+(?:\\\\.[A-Za-z0-9_]+)+"))) return@mapNotNull null
            val prefix = "app.$packageName."
            AppDnsStats(
                packageName = packageName,
                queries = prefs.getLong(prefix + "queries", 0L).coerceAtLeast(0L),
                blocked = prefs.getLong(prefix + "blocked", 0L).coerceAtLeast(0L),
                forwarded = prefs.getLong(prefix + "forwarded", 0L).coerceAtLeast(0L),
                failed = prefs.getLong(prefix + "failed", 0L).coerceAtLeast(0L),
                ads = prefs.getLong(prefix + "ads", 0L).coerceAtLeast(0L),
                trackers = prefs.getLong(prefix + "trackers", 0L).coerceAtLeast(0L),
                other = prefs.getLong(prefix + "other", 0L).coerceAtLeast(0L)
            )
        }.sortedByDescending { it.queries }.take(limit)
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}
