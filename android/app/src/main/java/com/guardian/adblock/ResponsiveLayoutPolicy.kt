package com.guardian.adblock

/** Pure sizing policy shared by phone, tablet, and resizable multi-window layouts. */
internal object ResponsiveLayoutPolicy {
    const val TABLET_BREAKPOINT_DP = 600
    const val MAX_CONTENT_WIDTH_DP = 840

    fun horizontalPaddingDp(screenWidthDp: Int): Int = when {
        screenWidthDp >= 840 -> 40
        screenWidthDp >= TABLET_BREAKPOINT_DP -> 32
        else -> 22
    }

    fun contentWidthPx(screenWidthDp: Int, density: Float): Int {
        require(screenWidthDp > 0) { "screenWidthDp must be positive" }
        require(density > 0f && density.isFinite()) { "density must be finite and positive" }
        val widthDp = minOf(screenWidthDp, MAX_CONTENT_WIDTH_DP)
        return (widthDp * density).toInt().coerceAtLeast(1)
    }
}
