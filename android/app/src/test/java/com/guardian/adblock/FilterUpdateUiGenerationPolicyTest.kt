package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterUpdateUiGenerationPolicyTest {
    @Test
    fun appliesCallbackOnlyForCurrentAttempt() {
        assertTrue(FilterUpdateUiGenerationPolicy.shouldApply(
            callbackGeneration = 7L,
            currentGeneration = 7L
        ))
        assertFalse(FilterUpdateUiGenerationPolicy.shouldApply(
            callbackGeneration = 6L,
            currentGeneration = 7L
        ))
        assertFalse(FilterUpdateUiGenerationPolicy.shouldApply(
            callbackGeneration = 8L,
            currentGeneration = 7L
        ))
        assertTrue(FilterUpdateUiGenerationPolicy.shouldApply(
            callbackGeneration = 0L,
            currentGeneration = 0L
        ))
    }
}
