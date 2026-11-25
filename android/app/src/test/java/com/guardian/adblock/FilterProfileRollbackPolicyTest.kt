package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterProfileRollbackPolicyTest {
    @Test
    fun rollsBackOnlyWhenARealPreviousProfileExistsAndSelectionChanged() {
        assertTrue(FilterProfileRollbackPolicy.shouldRollback("adguard", "hagezi_pro"))
        assertFalse(FilterProfileRollbackPolicy.shouldRollback("adguard", "adguard"))
        assertFalse(FilterProfileRollbackPolicy.shouldRollback(null, "hagezi_pro"))
        assertFalse(FilterProfileRollbackPolicy.shouldRollback("", "hagezi_pro"))
        assertFalse(FilterProfileRollbackPolicy.shouldRollback("   ", "hagezi_pro"))
    }
}
