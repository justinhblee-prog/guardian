package com.guardian.adblock

/** Keeps the selected workspace tab stable when Android recreates the Activity. */
internal object ActivityScreenState {
    private val supportedTabs = setOf("home", "protection", "app-exclusions", "activity", "premium", "rules", "settings", "updates")

    fun restoreTab(savedTab: String?): String =
        savedTab?.takeIf { it in supportedTabs } ?: "home"
}
