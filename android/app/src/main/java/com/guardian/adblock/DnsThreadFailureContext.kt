package com.guardian.adblock

/** Isolates one upstream DNS operation's failure detail to its executing thread. */
internal class DnsThreadFailureContext {
    private val local = ThreadLocal<String?>()

    var current: String?
        get() = local.get()
        set(value) {
            if (value == null) local.remove() else local.set(value)
        }
}
