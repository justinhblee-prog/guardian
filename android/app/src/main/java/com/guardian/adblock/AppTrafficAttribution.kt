package com.guardian.adblock

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import java.net.InetAddress
import java.net.InetSocketAddress

/** Resolves UDP DNS socket ownership for an active VpnService (Android 10+). */
internal object AppTrafficAttribution {
    private const val UDP_PROTOCOL = 17

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

        // Do not cache by the 4-tuple: UDP source ports can be reused by a
        // different app immediately after a socket closes. Resolve the owner
        // for each datagram so a stale package policy cannot be applied to a
        // later app that inherits the same local/remote address and port.
        return runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val local = InetSocketAddress(InetAddress.getByAddress(sourceAddress), sourcePort)
            val remote = InetSocketAddress(InetAddress.getByAddress(destinationAddress), destinationPort)
            val uid = manager.getConnectionOwnerUid(UDP_PROTOCOL, local, remote)
            if (uid < 0) return null
            val packages = context.packageManager.getPackagesForUid(uid).orEmpty()
            AppPackageIdentityPolicy.singleOwnerPackage(packages)
        }.getOrNull()
    }
}

/** Shared UIDs are deliberately not attributed to an arbitrary package. */
internal object AppPackageIdentityPolicy {
    fun singleOwnerPackage(packages: Array<out String>): String? {
        val unique = packages.distinct()
        if (unique.size != 1) return null
        return unique.single().takeIf {
            it.length in 3..255 &&
                it.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))
        }
    }
}
