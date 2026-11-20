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
    fun readinessClearlyDistinguishesBundledFallbackFromReadyFeeds() {
        assertTrue(
            FilterStartupGate.readinessMessage(10, listOf("광고·추적", "보안"))
                .contains("기본 규칙 10개만 우선 적용 중")
        )
        assertTrue(
            FilterStartupGate.readinessMessage(10, listOf("광고·추적", "광고·추적"))
                .endsWith("광고·추적")
        )
        assertTrue(FilterStartupGate.readinessMessage(10, emptyList()).contains("선택 필터 준비 완료"))
    }

    @Test
    fun readinessMessageNeverReportsNegativeBundledRuleCount() {
        assertTrue(FilterStartupGate.readinessMessage(-5, listOf("보안")).contains("기본 규칙 0개"))
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
    fun largeRuleCountsCannotOverflowTheBaselineGate() {
        assertTrue(FilterStartupGate.hasUsableBundledBaseline(Int.MAX_VALUE, Int.MAX_VALUE))
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
    fun rejectsInvalidOptionalFilterMinimumBeforeOpeningTheCache() {
        var loadCalled = false
        var unavailableCalled = false
        try {
            FilterStartupGate.loadOptionalRules(
                minimumRules = 0,
                load = {
                    loadCalled = true
                    FilterRuleParser.parse("block=ads.example")
                },
                onUnavailable = { unavailableCalled = true }
            )
            throw AssertionError("non-positive optional filter threshold must be rejected")
        } catch (_: IllegalArgumentException) {
            assertFalse(loadCalled)
            assertFalse(unavailableCalled)
        }
    }

    @Test
    fun doesNotSwallowFatalErrorsFromOptionalFeedLoading() {
        var unavailableCalled = false
        try {
            FilterStartupGate.loadOptionalRules(
                minimumRules = 1,
                load = { throw AssertionError("fatal runtime failure") },
                onUnavailable = { unavailableCalled = true }
            )
            throw AssertionError("fatal error should propagate")
        } catch (_: AssertionError) {
            assertFalse(unavailableCalled)
        }
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
