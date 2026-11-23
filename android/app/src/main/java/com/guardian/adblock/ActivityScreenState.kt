package com.guardian.adblock

/** Keeps the selected workspace tab stable when Android recreates the Activity. */
internal object ActivityScreenState {
    private val supportedTabs = setOf(
        "home", "protection", "app-exclusions", "activity", "premium",
        "rules", "settings", "updates", "settings-general", "settings-filtering",
        "settings-premium", "settings-support", "settings-about", "settings-exit"
    )

    fun restoreTab(savedTab: String?, persistedTab: String? = null): String {
        val tab = savedTab?.takeIf { it in supportedTabs }
            ?: persistedTab?.takeIf { it in supportedTabs }
            ?: "home"
        return when (tab) {
            "rules" -> "settings-filtering"
            else -> tab
        }
    }
}
