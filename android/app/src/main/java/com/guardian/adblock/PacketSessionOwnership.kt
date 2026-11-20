package com.guardian.adblock

/**
 * Serializes ownership transitions for VPN packet-loop sessions.
 *
 * A packet reader may finish after a replacement TUN has already been committed.
 * Its cleanup callback is therefore allowed to mutate shared service state only
 * while its descriptor is still the current owner.
 */
internal class PacketSessionOwnership {
    private var owner: Any? = null

    @Synchronized
    fun activate(session: Any, onActivate: () -> Unit) {
        owner = session
        onActivate()
    }

    @Synchronized
    fun isCurrent(session: Any): Boolean = owner === session

    @Synchronized
    fun <T> withCurrent(session: Any, action: () -> T): T? {
        if (owner !== session) return null
        return action()
    }

    @Synchronized
    fun finish(session: Any, onFinish: () -> Unit): Boolean {
        if (owner !== session) return false
        owner = null
        onFinish()
        return true
    }

    @Synchronized
    fun stop(onStop: () -> Unit) {
        owner = null
        onStop()
    }
}
