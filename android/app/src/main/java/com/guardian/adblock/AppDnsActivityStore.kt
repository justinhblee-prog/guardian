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

/** Domain-free per-app counters, buffered to avoid a disk write for every DNS packet. */
internal object AppDnsActivityStore {
    private const val PREFS = "guardian_app_dns_activity_v1"
    private const val KEY_PACKAGES = "tracked_packages"
    private const val MAX_TRACKED_PACKAGES = 200
    private const val FLUSH_INTERVAL_MILLIS = 5_000L

    private data class Pending(
        var queries: Long = 0L,
        var blocked: Long = 0L,
        var forwarded: Long = 0L,
        var failed: Long = 0L,
        var ads: Long = 0L,
        var trackers: Long = 0L,
        var other: Long = 0L
    )

    private val pending = linkedMapOf<String, Pending>()
    private var knownPackages: MutableSet<String>? = null
    private var lastFlushAtMillis = 0L

    @Synchronized
    fun record(
        context: Context,
        packageName: String?,
        blocked: Boolean,
        forwarded: Boolean,
        category: DomainFilter.BlockCategory?,
        timestampEpochMillis: Long = System.currentTimeMillis()
    ) {
        val safePackage = packageName?.takeIf {
            it.length in 3..255 && it.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))
        } ?: return
        val packages = knownPackages ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_PACKAGES, emptySet()).orEmpty().toMutableSet().also { knownPackages = it }
        if (safePackage !in packages && packages.size >= MAX_TRACKED_PACKAGES) return
        packages.add(safePackage)

        val counters = pending.getOrPut(safePackage) { Pending() }
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

    @Synchronized
    fun snapshot(context: Context, limit: Int = 50): List<AppDnsStats> {
        require(limit in 1..200)
        flushPendingLocked(context, System.currentTimeMillis())
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().mapNotNull { packageName ->
            if (!packageName.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))) return@mapNotNull null
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

    /** Remove orphaned per-app counters only after a complete installed-app inventory. */
    @Synchronized
    fun pruneUninstalled(
        context: Context,
        installedPackages: Set<String>,
        inventoryComplete: Boolean
    ): Set<String> {
        if (!inventoryComplete) return emptySet()
        flushPendingLocked(context, System.currentTimeMillis())
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tracked = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().toSet()
        val stale = InstalledAppDataCleanupPolicy.stalePackages(tracked, installedPackages, true)
        pending.keys.filter { it !in installedPackages }.forEach(pending::remove)
        if (stale.isEmpty()) {
            knownPackages = tracked.toMutableSet()
            return emptySet()
        }
        val editor = prefs.edit()
        val existingKeys = prefs.all.keys
        stale.forEach { packageName ->
            val prefix = "app.$packageName."
            existingKeys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        }
        val retained = tracked - stale
        editor.putStringSet(KEY_PACKAGES, retained).apply()
        knownPackages = retained.toMutableSet()
        return stale
    }

    @Synchronized
    fun clear(context: Context) {
        pending.clear()
        knownPackages = null
        lastFlushAtMillis = 0L
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun flushPendingLocked(context: Context, nowEpochMillis: Long) {
        if (pending.isEmpty() && knownPackages == null) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        pending.forEach { (packageName, counters) ->
            val prefix = "app.$packageName."
            fun add(key: String, amount: Long) {
                val old = prefs.getLong(prefix + key, 0L).coerceAtLeast(0L)
                editor.putLong(prefix + key, saturatingAdd(old, amount))
            }
            add("queries", counters.queries)
            add("blocked", counters.blocked)
            add("forwarded", counters.forwarded)
            add("failed", counters.failed)
            add("ads", counters.ads)
            add("trackers", counters.trackers)
            add("other", counters.other)
        }
        knownPackages?.let { editor.putStringSet(KEY_PACKAGES, it.toSet()) }
        editor.apply()
        pending.clear()
        lastFlushAtMillis = nowEpochMillis
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}
