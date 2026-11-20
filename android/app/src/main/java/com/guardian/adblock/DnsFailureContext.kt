package com.guardian.adblock

/**
 * Keeps the last upstream DNS failure local to the request thread.
 *
 * VPN packet processing, startup preflight and manual connectivity checks may
 * forward DNS concurrently. A process-wide mutable string lets one request
 * clear or overwrite another request's diagnostic.
 */
internal class DnsFailureContext {
    private val value = ThreadLocal<String?>()

    fun clear() {
        value.remove()
    }

    fun get(): String? = value.get()

    fun set(reason: String?) {
        if (reason == null) value.remove() else value.set(reason)
    }
}
