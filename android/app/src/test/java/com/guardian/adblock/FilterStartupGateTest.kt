package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterStartupGateTest {
    @Test
    fun allowsStartupWhenBundledBaselineContainsBlockRules() {
        assertTrue(FilterStartupGate.hasUsableBundledBaseline(10, 0))
        assertTrue(FilterStartupGate.hasUsableBundledBaseline(0, 1))
    }

    @Test
    fun rejectsStartupWhenBundledBaselineHasNoBlockRules() {
        assertFalse(FilterStartupGate.hasUsableBundledBaseline(0, 0))
        assertFalse(FilterStartupGate.hasUsableBundledBaseline(-1, -2))
    }

    @Test
    fun negativeCountsCannotCancelValidBaselineRules() {
        assertTrue(FilterStartupGate.hasUsableBundledBaseline(5, -20))
    }
}
