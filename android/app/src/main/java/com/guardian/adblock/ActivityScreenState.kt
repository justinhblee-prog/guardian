package com.guardian.adblock

/** Keeps the selected workspace tab stable when Android recreates the Activity. */
internal object ActivityScreenState {
    private val supportedTabs = setOf("home", "protection", "app-exclusions", "activity", "settings", "settings-general", "settings-filtering", "settings-premium", "settings-support", "settings-about", "settings-exit")

    fun restoreTab(savedTab: String?): String = when (savedTab) {
        "premium" -> "settings-premium"
        "rules" -> "settings-filtering"
        else -> savedTab?.takeIf { it in supportedTabs } ?: "home"
    }
}
