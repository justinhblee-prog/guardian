package com.guardian.adblock

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

internal data class PerAppDnsRuleParseResult(
    val normalizedRules: Set<String>,
    val invalidLines: List<String>
)

internal object PerAppDnsRulePolicy {
    const val MAX_RULES_PER_APP = 200
    fun parse(raw: String): PerAppDnsRuleParseResult {
        val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        require(lines.size <= MAX_RULES_PER_APP) { "앱별 추가 차단 규칙은 최대 200개까지 등록할 수 있습니다." }
        val valid = linkedSetOf<String>()
        val invalid = mutableListOf<String>()
        lines.forEach { line ->
            val normalized = DomainFilter.normalizeRuleDomain(line)
            if (normalized == null) invalid += line else valid += normalized
        }
        return PerAppDnsRuleParseResult(valid, invalid)
    }

    fun isValidPackageName(value: String): Boolean =
        value.length in 3..255 && value.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))

    fun invalidRulesMessage(invalidLines: List<String>): String =
        "잘못된 도메인 규칙: ${invalidLines.take(3).joinToString(", ")}"
}

/** Per-package extra block rules. These only add protection and never override global allow rules. */
internal object PerAppDnsPolicyStore {
    private const val PREFS = "guardian_per_app_dns_policy_v1"
    private const val MAX_CACHED_PACKAGES = 200
    private val cache = ConcurrentHashMap<String, Set<String>>()

    @Synchronized
    fun rules(context: Context, packageName: String?): Set<String> {
        val safePackage = packageName?.takeIf(PerAppDnsRulePolicy::isValidPackageName) ?: return emptySet()
        cache[safePackage]?.let { return it }
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("rules.$safePackage", emptySet()).orEmpty()
            .mapNotNull(DomainFilter::normalizeRuleDomain)
            .toSet()
        if (cache.size >= MAX_CACHED_PACKAGES) cache.clear()
        cache[safePackage] = stored
        return stored
    }

    @Synchronized
    fun replaceRules(context: Context, packageName: String, rawRules: String): Int {
        require(PerAppDnsRulePolicy.isValidPackageName(packageName)) { "앱 패키지 ID가 올바르지 않습니다." }
        val parsed = PerAppDnsRulePolicy.parse(rawRules)
        require(parsed.invalidLines.isEmpty()) {
            PerAppDnsRulePolicy.invalidRulesMessage(parsed.invalidLines)
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (parsed.normalizedRules.isEmpty()) editor.remove("rules.$packageName")
        else editor.putStringSet("rules.$packageName", parsed.normalizedRules)
        check(editor.commit()) { "앱별 DNS 정책을 저장하지 못했습니다." }
        cache[packageName] = parsed.normalizedRules
        return parsed.normalizedRules.size
    }

    /** Remove orphaned per-app rules and cached entries after a complete app inventory. */
    @Synchronized
    fun pruneUninstalled(
        context: Context,
        installedPackages: Set<String>,
        inventoryComplete: Boolean
    ): Set<String> {
        if (!inventoryComplete) return emptySet()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedPackages = prefs.all.keys
            .filter { it.startsWith("rules.") }
            .map { it.removePrefix("rules.") }
            .toSet()
        val cachedPackages = cache.keys.toSet()
        val stale = InstalledAppDataCleanupPolicy.stalePackages(
            storedPackages + cachedPackages, installedPackages, true
        )
        if (stale.isEmpty()) return emptySet()
        val editor = prefs.edit()
        stale.forEach { packageName -> editor.remove("rules.$packageName") }
        editor.apply()
        stale.forEach { packageName -> cache.remove(packageName) }
        return stale
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        cache.clear()
    }
}
