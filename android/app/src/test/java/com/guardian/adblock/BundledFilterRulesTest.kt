package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class BundledFilterRulesTest {
    @Test
    fun parsesBundledBlockAndImportantRules() {
        val parsed = BundledFilterRules.parse(
            StringReader("block=ads.example\nblock-important=malware.example")
        )
        assertTrue(parsed.blocked.contains("ads.example"))
        assertTrue(parsed.importantBlocked.contains("malware.example"))
        assertEquals(2, parsed.blocked.size + parsed.importantBlocked.size)
    }

    @Test
    fun rejectsEmptyOrInvalidBundledBaseline() {
        try {
            BundledFilterRules.parse(StringReader("! comment\ninvalid rule"))
            throw AssertionError("An empty bundled baseline must not be accepted.")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("기본 DNS 규칙"))
        }
    }
}
