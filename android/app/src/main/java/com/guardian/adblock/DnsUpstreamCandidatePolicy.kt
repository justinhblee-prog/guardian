package com.guardian.adblock

import java.net.InetAddress

/** Orders resolver candidates without ever feeding Guardian's virtual DNS back into itself. */
internal object DnsUpstreamCandidatePolicy {
    fun udpCandidates(
        configured: String,
        systemDns: List<String>,
        publicFallback: List<String>,
        virtualDns: Set<String>
    ): List<String> = (listOf(configured) + systemDns + publicFallback)
        .mapNotNull(::usableResolverAddress)
        .filterNot { it in virtualDns }
        .distinct()

    /** UDP mode may retry TCP/53; explicitly selected transports must remain explicit. */
    fun transports(primary: String): List<String> =
        if (primary == "udp") listOf("udp", "tcp") else listOf(primary)

    private fun usableResolverAddress(raw: String): String? {
        val normalized = DnsAddressValidator.normalize(raw) ?: return null
        return try {
            val address = InetAddress.getByName(normalized)
            if (address.isAnyLocalAddress || address.isLoopbackAddress ||
                address.isMulticastAddress || address.isLinkLocalAddress
            ) null else normalized
        } catch (_: Exception) {
            null
        }
    }
}
