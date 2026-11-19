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

    @Test
    fun keepsParsedOptionalFeedWhenItMeetsMinimumRuleCount() {
        var unavailableCalled = false
        val result = FilterStartupGate.loadOptionalRules(
            minimumRules = 2,
            load = { FilterRuleParser.parse("block=ads.example\nblock=tracker.example") },
            onUnavailable = { unavailableCalled = true }
        )
        assertTrue(result.blocked.contains("ads.example"))
        assertTrue(result.blocked.contains("tracker.example"))
        assertFalse(unavailableCalled)
    }

    @Test
    fun skipsOptionalFeedWhenCacheReadFails() {
        var unavailableCalled = false
        val result = FilterStartupGate.loadOptionalRules(
            minimumRules = 1,
            load = { throw IllegalStateException("cache missing") },
            onUnavailable = { unavailableCalled = true }
        )
        assertTrue(unavailableCalled)
        assertTrue(result.blocked.isEmpty())
        assertTrue(result.importantBlocked.isEmpty())
    }

    @Test
    fun skipsOptionalFeedWhenParsedRuleCountIsBelowMinimum() {
        var unavailableCalled = false
        val result = FilterStartupGate.loadOptionalRules(
            minimumRules = 2,
            load = { FilterRuleParser.parse("block=only.example") },
            onUnavailable = { unavailableCalled = true }
        )
        assertTrue(unavailableCalled)
        assertTrue(result.blocked.isEmpty())
    }
}
