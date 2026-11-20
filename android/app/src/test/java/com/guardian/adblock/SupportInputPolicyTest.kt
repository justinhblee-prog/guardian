package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportInputPolicyTest {
    @Test fun replyEmailMayBeOmittedOrMustMatchServerContract() {
        assertTrue(SupportInputPolicy.isValidReplyEmail(""))
        assertTrue(SupportInputPolicy.isValidReplyEmail("  "))
        assertTrue(SupportInputPolicy.isValidReplyEmail("help@example.com"))
        assertTrue(SupportInputPolicy.isValidReplyEmail(" help@example.com "))
        assertFalse(SupportInputPolicy.isValidReplyEmail("help example.com"))
        assertFalse(SupportInputPolicy.isValidReplyEmail("help@@example.com"))
        assertFalse(SupportInputPolicy.isValidReplyEmail("help@example"))
        assertFalse(SupportInputPolicy.isValidReplyEmail("help@example.com\u0000"))
        assertFalse(SupportInputPolicy.isValidReplyEmail("a".repeat(245) + "@example.com"))
    }

    @Test fun ticketReferenceIsNormalizedAndMustMatchGeneratedFormat() {
        assertEquals("GDN-ABCDEF012345", SupportInputPolicy.normalizeTicketReference(" gdn-abcdef012345 "))
        assertNull(SupportInputPolicy.normalizeTicketReference("GDN-ABCDEF01234"))
        assertNull(SupportInputPolicy.normalizeTicketReference("GDN-ABCDEF0123456"))
        assertNull(SupportInputPolicy.normalizeTicketReference("GDN-ABCDEF01234Z"))
        assertNull(SupportInputPolicy.normalizeTicketReference("OTHER-ABCDEF012345"))
        assertNull(SupportInputPolicy.normalizeTicketReference(""))
    }
}
