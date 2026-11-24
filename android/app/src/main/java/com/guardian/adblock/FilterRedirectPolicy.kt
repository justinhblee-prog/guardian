package com.guardian.adblock

import java.net.URL

/** Strict HTTPS-only redirect policy for downloaded filter sources. */
internal object FilterRedirectPolicy {
    private const val MAX_REDIRECTS = 5
    private val supportedStatuses = setOf(301, 302, 303, 307, 308)

    fun resolve(currentUrl: String, status: Int, location: String?, redirectsFollowed: Int): String? {
        if (status !in supportedStatuses || redirectsFollowed !in 0 until MAX_REDIRECTS) return null
        val destination = location?.trim()?.takeIf { it.isNotEmpty() && it.length <= 2048 } ?: return null
        return try {
            val target = URL(URL(currentUrl), destination)
            if (!target.protocol.equals("https", ignoreCase = true) ||
                target.host.isBlank() || target.userInfo != null ||
                target.ref != null || target.port !in listOf(-1, 443)) {
                null
            } else {
                target.toExternalForm()
            }
        } catch (_: Exception) {
            null
        }
    }
}
