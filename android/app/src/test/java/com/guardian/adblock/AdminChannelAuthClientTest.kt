package com.guardian.adblock
import org.junit.Assert.*
import org.junit.Test
class AdminChannelAuthClientTest {
    @Test fun acceptsOnlyNonWhitespaceAdminTokensWithinLengthBounds() {
        assertTrue(AdminChannelAuthClient.isValidTokenFormat("a".repeat(32)))
        assertTrue(AdminChannelAuthClient.isValidTokenFormat("A9".repeat(256)))
        assertFalse(AdminChannelAuthClient.isValidTokenFormat("a".repeat(31)))
        assertFalse(AdminChannelAuthClient.isValidTokenFormat("a".repeat(513)))
        assertFalse(AdminChannelAuthClient.isValidTokenFormat("a".repeat(31) + " "))
        assertFalse(AdminChannelAuthClient.isValidTokenFormat(""))
    }
}
