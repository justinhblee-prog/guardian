package com.guardian.adblock

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.SystemClock
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/** Resolves UDP DNS socket ownership for an active VpnService (Android 10+). */
internal object AppTrafficAttribution {
    private const val UDP_PROTOCOL = 17
    private const val CACHE_TTL_MILLIS = 30_000L
    private const val MAX_CACHE_ENTRIES = 2_048

    private data class FlowKey(
        val source: String,
        val sourcePort: Int,
        val destination: String,
        val destinationPort: Int
    )
    private data class CachedOwner(val packageName: String, val expiresAt: Long)
    private val cache = ConcurrentHashMap<FlowKey, CachedOwner>()

    fun resolveUdpDnsPackage(
        context: Context,
        sourceAddress: ByteArray,
        sourcePort: Int,
        destinationAddress: ByteArray,
        destinationPort: Int
    ): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        if (sourcePort !in 1..65535 || destinationPort != 53) return null
        if (sourceAddress.size !in setOf(4, 16) || destinationAddress.size != sourceAddress.size) return null

        val key = FlowKey(
            sourceAddress.toHex(), sourcePort,
            destinationAddress.toHex(), destinationPort
        )
        val now = SystemClock.elapsedRealtime()
        cache[key]?.let { cached ->
            if (cached.expiresAt > now) return cached.packageName
            cache.remove(key, cached)
        }

        return runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val local = InetSocketAddress(InetAddress.getByAddress(sourceAddress), sourcePort)
            val remote = InetSocketAddress(InetAddress.getByAddress(destinationAddress), destinationPort)
            val uid = manager.getConnectionOwnerUid(UDP_PROTOCOL, local, remote)
            if (uid < 0) return null
            val packages = context.packageManager.getPackagesForUid(uid).orEmpty()
            val packageName = AppPackageIdentityPolicy.singleOwnerPackage(packages) ?: return null
            if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
            cache[key] = CachedOwner(packageName, now + CACHE_TTL_MILLIS)
            packageName
        }.getOrNull()
    }

    internal fun clearCacheForTest() = cache.clear()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

/** Shared UIDs are deliberately not attributed to an arbitrary package. */
internal object AppPackageIdentityPolicy {
    fun singleOwnerPackage(packages: Array<out String>): String? {
        val unique = packages.distinct()
        if (unique.size != 1) return null
        return unique.single().takeIf {
            it.length in 3..255 &&
                it.matches(Regex("[A-Za-z0-9_]+(?:\\\\.[A-Za-z0-9_]+)+"))
        }
    }
}
