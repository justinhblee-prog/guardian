package com.guardian.adblock

import java.net.URI
import java.net.URL

/** Strict HTTPS-only redirect policy for downloaded filter sources. */
internal object FilterRedirectPolicy {
    private const val MAX_REDIRECTS = 5
    private val supportedStatuses = setOf(301, 302, 303, 307, 308)

    fun isAllowedHttpsUrl(url: URL): Boolean {
        val external = url.toExternalForm()
        // java.net.URL is deliberately permissive (for example, it accepts raw
        // spaces in paths); URI parsing rejects malformed request targets.
        if (external.any { it.code <= 0x20 || it.code == 0x7f || it == '\\\\' }) return false
        val uri = try {
            URI(external)
        } catch (_: Exception) {
            return false
        }
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            uri.rawFragment == null && (uri.port == -1 || uri.port in 1..65535)
    }

    fun resolve(currentUrl: String, status: Int, location: String?, redirectsFollowed: Int): String? {
        if (status !in supportedStatuses || redirectsFollowed !in 0 until MAX_REDIRECTS) return null
        val destination = location?.trim()?.takeIf { it.isNotEmpty() && it.length <= 2048 } ?: return null
        return try {
            val target = URL(URL(currentUrl), destination)
            if (!isAllowedHttpsUrl(target)) {
                null
            } else {
                target.toExternalForm()
            }
        } catch (_: Exception) {
            null
        }
    }
}
