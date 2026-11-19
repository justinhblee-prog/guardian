package com.guardian.adblock

/**
 * Separates the minimum requirement for starting the local DNS tunnel from optional
 * downloaded filter feeds. An installation can start with bundled rules and report
 * missing enhancements without pretending those feeds are active.
 */
internal object FilterStartupGate {
    fun hasUsableBundledBaseline(blockedRules: Int, importantBlockedRules: Int): Boolean =
        blockedRules.coerceAtLeast(0) + importantBlockedRules.coerceAtLeast(0) > 0
}
