package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportInputPolicyTest {
    @Test fun supportTextMatchesServerLengthAndControlCharacterRules() {
        assertTrue(SupportInputPolicy.isValidSupportText("abc", 3, 120))
        assertTrue(SupportInputPolicy.isValidSupportText("  abc  ", 3, 120))
        assertFalse(SupportInputPolicy.isValidSupportText("ab", 3, 120))
        assertFalse(SupportInputPolicy.isValidSupportText("a".repeat(121), 3, 120))
        assertFalse(SupportInputPolicy.isValidSupportText("abc\u0000", 3, 120))
        assertFalse(SupportInputPolicy.isValidSupportText("abc", 4, 3))
    }

    @Test fun supportCategoryMatchesServerAllowList() {
        listOf("question", "bug", "suggestion", "billing", "license").forEach {
            assertTrue(SupportInputPolicy.isValidSupportCategory(it))
        }
        assertFalse(SupportInputPolicy.isValidSupportCategory("other"))
        assertFalse(SupportInputPolicy.isValidSupportCategory("BUG"))
    }

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

    @Test fun lookupAccessTokenMatchesServerBase64UrlContract() {
        assertTrue(SupportInputPolicy.isValidAccessToken("A".repeat(43)))
        assertTrue(SupportInputPolicy.isValidAccessToken("ab-_".repeat(8)))
        assertTrue(SupportInputPolicy.isValidAccessToken("A".repeat(100)))
        assertFalse(SupportInputPolicy.isValidAccessToken("A".repeat(31)))
        assertFalse(SupportInputPolicy.isValidAccessToken("A".repeat(101)))
        assertFalse(SupportInputPolicy.isValidAccessToken("A".repeat(42) + "="))
        assertFalse(SupportInputPolicy.isValidAccessToken("A".repeat(42) + " "))
        assertFalse(SupportInputPolicy.isValidAccessToken("가" + "A".repeat(42)))
    }
}
