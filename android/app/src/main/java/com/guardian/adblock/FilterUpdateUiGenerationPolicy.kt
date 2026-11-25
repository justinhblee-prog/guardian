package com.guardian.adblock

/**
 * Prevents delayed UI work from an older filter download from overwriting
 * the controls and status of a newer user-triggered attempt.
 */
internal object FilterUpdateUiGenerationPolicy {
    fun shouldApply(callbackGeneration: Long, currentGeneration: Long): Boolean =
        callbackGeneration == currentGeneration
}
