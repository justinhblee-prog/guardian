package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Test

class VpnConfigurationDefaultsTest {
    @Test fun broadPublicDnsIpRoutingIsOptInByDefault() {
        assertFalse(GuardianVpnService.DEFAULT_DIRECT_DNS_CAPTURE)
    }
}
