package com.guardian.adblock

/** Keeps the selected workspace tab stable when Android recreates the Activity. */
internal object ActivityScreenState {
    private val supportedTabs = setOf(
        "home", "protection", "app-exclusions", "activity", "premium", "rules",
        "settings", "updates", "settings-general", "settings-support", "settings-info",
        "license-activation"
    )

    fun restoreTab(savedTab: String?, persistedTab: String? = null): String =
        savedTab?.takeIf { it in supportedTabs }
            ?: persistedTab?.takeIf { it in supportedTabs }
            ?: "home"
}
