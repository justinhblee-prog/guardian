package com.guardian.adblock

import java.net.Inet6Address
import java.net.InetAddress

/** Accepts numeric IPv4/IPv6 literals only; hostnames are rejected to avoid DNS recursion. */
object DnsAddressValidator {
    fun normalize(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.contains('%')) return null
        if (value.contains(':')) {
            if (!value.matches(Regex("^[0-9a-fA-F:.]+$"))) return null
            return try {
                val address = InetAddress.getByName(value)
                if (address is Inet6Address) address.hostAddress.substringBefore('%') else null
            } catch (_: Exception) {
                null
            }
        }
        val parts = value.split('.')
        if (parts.size != 4) return null
        val octets = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return null }
        return octets.joinToString(".")
    }
}
