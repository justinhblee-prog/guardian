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
    fun largeRuleCountsCannotOverflowStartupReadinessCheck() {
        assertTrue(FilterStartupGate.hasUsableBundledBaseline(Int.MAX_VALUE, 1))
        assertTrue(FilterStartupGate.hasUsableBundledBaseline(0, Int.MAX_VALUE))
        assertFalse(FilterStartupGate.hasUsableBundledBaseline(Int.MIN_VALUE, Int.MIN_VALUE))
    }

    @Test
    fun optionalCacheReadinessTreatsOrdinaryAndLinkageFailuresAsUnavailable() {
        assertTrue(FilterStartupGate.isOptionalCacheReady { true })
        assertFalse(FilterStartupGate.isOptionalCacheReady { false })
        assertFalse(FilterStartupGate.isOptionalCacheReady { throw IllegalStateException("cache metadata unavailable") })
        assertFalse(FilterStartupGate.isOptionalCacheReady { throw NoClassDefFoundError("optional cache provider missing") })
    }

    @Test
    fun optionalCacheReadinessDoesNotSwallowVirtualMachineFatalErrors() {
        val fatal = OutOfMemoryError("simulated startup memory exhaustion")
        try {
            FilterStartupGate.isOptionalCacheReady { throw fatal }
            throw AssertionError("Expected OutOfMemoryError to propagate")
        } catch (actual: OutOfMemoryError) {
            assertTrue(actual === fatal)
        }
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
    fun skipsOptionalFeedWhenParserLinkageFailsButKeepsVpnStartupAvailable() {
        var unavailable: Throwable? = null
        val result = FilterStartupGate.loadOptionalRules(
            minimumRules = 1,
            load = { throw NoClassDefFoundError("simulated optional parser linkage failure") },
            onUnavailable = { unavailable = it }
        )
        assertTrue(unavailable is NoClassDefFoundError)
        assertTrue(result.blocked.isEmpty())
        assertTrue(result.importantBlocked.isEmpty())
    }

    @Test
    fun doesNotSwallowVirtualMachineFatalErrorsFromOptionalFeed() {
        val fatal = OutOfMemoryError("simulated memory exhaustion")
        var unavailableCalled = false
        try {
            FilterStartupGate.loadOptionalRules(
                minimumRules = 1,
                load = { throw fatal },
                onUnavailable = { unavailableCalled = true }
            )
            throw AssertionError("Expected OutOfMemoryError to propagate")
        } catch (actual: OutOfMemoryError) {
            assertTrue(actual === fatal)
        }
        assertFalse(unavailableCalled)
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

    @Test
    fun malformedBundledAssetIsDetectedAndEmergencyBaselineIsUsable() {
        val malformed = FilterRuleParser.parse(
            listOf("# comments only", "block=not a hostname", "allow=only.example")
        )
        assertFalse(
            FilterStartupGate.hasUsableBundledBaseline(
                malformed.blocked.size,
                malformed.importantBlocked.size
            )
        )

        val emergency = FilterRuleParser.parse(DomainFilter.bootstrapRuleLines())
        assertTrue(
            FilterStartupGate.hasUsableBundledBaseline(
                emergency.blocked.size,
                emergency.importantBlocked.size
            )
        )
    }
    @Test
    fun optionalFeedMinimumCountUsesOverflowSafeLongArithmetic() {
        assertTrue(FilterStartupGate.meetsMinimumRuleCount(Int.MAX_VALUE, 1, Int.MAX_VALUE))
        assertTrue(FilterStartupGate.meetsMinimumRuleCount(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
        assertFalse(FilterStartupGate.meetsMinimumRuleCount(1, 1, Int.MAX_VALUE))
        assertFalse(FilterStartupGate.meetsMinimumRuleCount(-1, Int.MAX_VALUE, 1))
        assertFalse(FilterStartupGate.meetsMinimumRuleCount(Int.MAX_VALUE, 1, 0))
    }

}
