package com.guardian.adblock

/**
 * Separates the minimum requirement for starting the local DNS tunnel from optional
 * downloaded filter feeds. An installation can start with bundled rules and report
 * missing enhancements without pretending those feeds are active.
 */
internal object FilterStartupGate {
    fun hasUsableBundledBaseline(blockedRules: Int, importantBlockedRules: Int): Boolean =
        blockedRules > 0 || importantBlockedRules > 0

    /**
     * Optional cache metadata failures may disable that feed, but must not swallow
     * VM-fatal errors such as OutOfMemoryError during startup.
     */
    fun <T> optionalCacheResult(load: () -> T): T? = try {
        load()
    } catch (_: Exception) {
        null
    } catch (_: LinkageError) {
        null
    }

    fun isOptionalCacheReady(check: () -> Boolean): Boolean =
        optionalCacheResult(check) == true

    internal fun meetsMinimumRuleCount(
        blockedRules: Int,
        importantBlockedRules: Int,
        minimumRules: Int
    ): Boolean = minimumRules > 0 &&
        blockedRules >= 0 && importantBlockedRules >= 0 &&
        blockedRules.toLong() + importantBlockedRules.toLong() >= minimumRules.toLong()

    /**
     * Parses an optional feed without allowing a stale, missing, or malformed cache
     * to take down the DNS tunnel. A failed feed becomes an empty source and is
     * surfaced to the caller; mandatory bundled rules are validated separately.
     */
    fun loadOptionalRules(
        minimumRules: Int,
        load: () -> FilterRuleParser.Parsed,
        onUnavailable: (Throwable) -> Unit
    ): FilterRuleParser.Parsed {
        fun unavailable(error: Throwable): FilterRuleParser.Parsed {
            onUnavailable(error)
            return FilterRuleParser.parse(emptyList<String>())
        }
        return try {
            require(minimumRules > 0) { "Optional filter minimum must be positive." }
            load().also { parsed ->
                val actualRules = parsed.blocked.size.toLong() + parsed.importantBlocked.size.toLong()
                check(meetsMinimumRuleCount(parsed.blocked.size, parsed.importantBlocked.size, minimumRules)) {
                    "Optional filter has too few valid block rules: $actualRules"
                }
            }
        } catch (error: Exception) {
            unavailable(error)
        } catch (error: LinkageError) {
            // Optional parser/provider linkage failures may skip this feed, but
            // VM-fatal Errors (for example OutOfMemoryError) must not be swallowed.
            unavailable(error)
        }
    }
}
