package com.guardian.adblock

/** Pure sizing policy shared by phone, tablet, and resizable multi-window layouts. */
internal object ResponsiveLayoutPolicy {
    const val TABLET_BREAKPOINT_DP = 600
    const val MAX_CONTENT_WIDTH_DP = 840
    const val NAVIGATION_RAIL_WIDTH_DP = 88
    const val WIDE_DASHBOARD_BREAKPOINT_DP = 1000

    fun horizontalPaddingDp(screenWidthDp: Int): Int = when {
        screenWidthDp >= 840 -> 40
        screenWidthDp >= TABLET_BREAKPOINT_DP -> 32
        else -> 22
    }

    fun usesNavigationRail(screenWidthDp: Int): Boolean = screenWidthDp >= 840

    fun usesTwoColumnDashboard(screenWidthDp: Int): Boolean =
        screenWidthDp >= WIDE_DASHBOARD_BREAKPOINT_DP

    /** Keeps the top-level navigation selection visible while nested settings pages are open. */
    fun selectedNavigationTab(tab: String): String = when (tab) {
        "settings-general", "settings-filtering", "settings-support", "settings-license", "premium", "rules" -> "settings"
        else -> tab
    }

    /** Returns the parent screen for system Back, or null when the home exit prompt should be shown. */
    fun backNavigationTarget(tab: String): String? = when (tab) {
        "settings-general", "settings-filtering", "settings-support", "settings-license" -> "settings"
        "premium" -> "settings-license"
        "rules" -> "settings-filtering"
        "settings" -> "home"
        "home" -> null
        else -> "home"
    }

    fun contentAreaWidthDp(screenWidthDp: Int): Int {
        require(screenWidthDp > 0) { "screenWidthDp must be positive" }
        val railWidth = if (usesNavigationRail(screenWidthDp)) NAVIGATION_RAIL_WIDTH_DP else 0
        return (screenWidthDp - railWidth).coerceAtLeast(1)
    }

    fun contentWidthPx(screenWidthDp: Int, density: Float): Int {
        require(screenWidthDp > 0) { "screenWidthDp must be positive" }
        require(density > 0f && density.isFinite()) { "density must be finite and positive" }
        val widthDp = minOf(screenWidthDp, MAX_CONTENT_WIDTH_DP)
        return (widthDp * density).toInt().coerceAtLeast(1)
    }
}
