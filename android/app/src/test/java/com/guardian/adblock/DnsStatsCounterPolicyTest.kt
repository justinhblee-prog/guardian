package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class DnsStatsCounterPolicyTest {
    @Test fun incrementsOrdinaryCounters() {
        assertEquals(1L, DnsStatsCounterPolicy.add(0L))
        assertEquals(43L, DnsStatsCounterPolicy.add(42L))
        assertEquals(42L, DnsStatsCounterPolicy.add(42L, 0L))
    }

    @Test fun negativePersistedValuesAreRecoveredWithoutGoingNegative() {
        assertEquals(1L, DnsStatsCounterPolicy.add(-1L))
        assertEquals(0L, DnsStatsCounterPolicy.add(Long.MIN_VALUE, 0L))
    }

    @Test fun additionsSaturateAtLongMaximum() {
        assertEquals(Long.MAX_VALUE, DnsStatsCounterPolicy.add(Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, DnsStatsCounterPolicy.add(Long.MAX_VALUE - 1L, 10L))
    }

    @Test fun negativeIncrementIsIgnored() {
        assertEquals(7L, DnsStatsCounterPolicy.add(7L, -9L))
    }
}
