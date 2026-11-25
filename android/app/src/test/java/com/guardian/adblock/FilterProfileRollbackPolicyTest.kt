package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterProfileRollbackPolicyTest {
    @Test
    fun rollsBackOnlyWhenPreviousProfileExistsAndNewCacheWasNotCommitted() {
        assertTrue(FilterProfileRollbackPolicy.shouldRollback("adguard", "hagezi_pro", "adguard"))
        assertFalse(FilterProfileRollbackPolicy.shouldRollback("adguard", "adguard", "adguard"))
        assertFalse(FilterProfileRollbackPolicy.shouldRollback("adguard", "hagezi_pro", "hagezi_pro"))
        assertFalse(FilterProfileRollbackPolicy.shouldRollback(null, "hagezi_pro", null))
        assertFalse(FilterProfileRollbackPolicy.shouldRollback("", "hagezi_pro", null))
        assertFalse(FilterProfileRollbackPolicy.shouldRollback("   ", "hagezi_pro", null))
    }
}
