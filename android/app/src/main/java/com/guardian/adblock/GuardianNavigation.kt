package com.guardian.adblock

/** The shared, approved Guardian navigation hierarchy for Android and Windows. */
data class GuardianNavigationItem(val id: String, val icon: String, val label: String)

object GuardianNavigation {
    val primaryItems = listOf(
        GuardianNavigationItem("home", "⌂", "홈"),
        GuardianNavigationItem("protection", "◈", "보호"),
        GuardianNavigationItem("app-exclusions", "▣", "앱 제외"),
        GuardianNavigationItem("activity", "▤", "활동"),
        GuardianNavigationItem("settings", "⚙", "설정")
    )

    val settingsItems = listOf("general", "filtering", "license-premium", "support", "info", "exit")

    fun isSettingsDestination(tab: String): Boolean =
        tab in setOf("settings", "settings-general", "settings-support", "settings-info", "updates", "rules", "premium")
}
