package com.guardian.adblock

import java.util.Locale

object DnsTlsHostValidator {
    private val labelPattern = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")

    fun normalize(value: String): String? {
        val raw = value.trim()
        if (raw.endsWith("..")) return null
        val host = raw.removeSuffix(".").lowercase(Locale.ROOT)
        if (host.isEmpty() || host.length > 253 || host.contains(':')) return null
        if (host.all { it.isDigit() || it == '.' }) return null
        val labels = host.split('.')
        if (labels.size < 2 || labels.any { !labelPattern.matches(it) }) return null
        return host
    }
}
