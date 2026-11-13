package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterUpdateSchedulePolicyTest {
    @Test fun preservesSupportedSixHourAndDailyIntervals() {
        assertEquals(6L * 60L * 60L * 1000L, FilterUpdateScheduler.normalizeInterval(6L * 60L * 60L * 1000L))
        assertEquals(FilterUpdateScheduler.DEFAULT_INTERVAL_MS, FilterUpdateScheduler.normalizeInterval(FilterUpdateScheduler.DEFAULT_INTERVAL_MS))
    }

    @Test fun clampsInvalidOrTooShortIntervalsToWorkManagerMinimum() {
        assertEquals(FilterUpdateScheduler.MIN_INTERVAL_MS, FilterUpdateScheduler.normalizeInterval(0L))
        assertEquals(FilterUpdateScheduler.MIN_INTERVAL_MS, FilterUpdateScheduler.normalizeInterval(60_000L))
    }
}
