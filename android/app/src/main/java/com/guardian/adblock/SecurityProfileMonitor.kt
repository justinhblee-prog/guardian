package com.guardian.adblock

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import java.util.concurrent.atomic.AtomicReference

/** Persists automatic profile state without storing SSIDs, BSSIDs or network identifiers. */
internal object SecurityProfileStore {
    const val ACTION_PROFILE_CHANGED = "com.guardian.adblock.SECURITY_PROFILE_CHANGED"
    private const val PREFS = "guardian_security_profiles_v1"
    private const val KEY_AUTO_SWITCH = "automatic_profile_switch"
    private const val KEY_ACTIVE_PROFILE = "active_profile_id"
    private const val KEY_AUTO_UPDATE_ALLOWED = "automatic_filter_updates_allowed"

    fun automaticSwitchEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_SWITCH, false)

    @Synchronized
    fun setAutomaticSwitchEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_SWITCH, enabled).apply()
    }

    fun activeProfileId(context: Context): SecurityProfilePolicy.Id =
        runCatching {
            SecurityProfilePolicy.Id.valueOf(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_ACTIVE_PROFILE, SecurityProfilePolicy.Id.STRICT.name)
                    ?: SecurityProfilePolicy.Id.STRICT.name
            )
        }.getOrDefault(SecurityProfilePolicy.Id.STRICT)

    fun automaticUpdatesAllowed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_UPDATE_ALLOWED, false)

    /**
     * Serialize explicit choices with queued network callbacks. A callback that
     * arrives after this method disables automatic mode must not overwrite the
     * user's selected profile.
     */
    fun applyManualProfile(context: Context, profileId: SecurityProfilePolicy.Id) {
        // Commit manual mode under this store lock, then release it before
        // unregistering the network callback. The monitor can call back into
        // this store while holding its own lock, so never nest the locks in
        // the inverse order.
        synchronized(this) {
            setAutomaticSwitchEnabled(context, false)
        }
        SecurityProfileMonitor.stop()
        synchronized(this) {
            applyProfile(context, SecurityProfilePolicy.manualSelection(profileId))
        }
    }

    @Synchronized
    fun applyAutomaticProfile(context: Context, profile: SecurityProfilePolicy.Profile) {
        if (!SecurityProfilePolicy.shouldApplyAutomaticProfile(automaticSwitchEnabled(context))) return
        applyProfile(context, profile)
    }

    private fun applyProfile(context: Context, profile: SecurityProfilePolicy.Profile) {
        val prefs = context.getSharedPreferences(GuardianVpnService.PREFS, Context.MODE_PRIVATE)
        val currentFilter = prefs.getString(FilterListManager.KEY_FILTER_PROFILE, "adguard") ?: "adguard"
        val currentSecurity = prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
        val currentAndroidAds = prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, true)
        val changed = currentFilter != profile.filterProfileKey ||
            currentSecurity != profile.securityFilterEnabled ||
            currentAndroidAds != profile.androidAdsFilterEnabled

        prefs.edit()
            .putString(FilterListManager.KEY_FILTER_PROFILE, profile.filterProfileKey)
            .putBoolean(FilterListManager.KEY_SECURITY_ENABLED, profile.securityFilterEnabled)
            .putBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, profile.androidAdsFilterEnabled)
            .apply()
        val profilePrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousProfileId = profilePrefs.getString(KEY_ACTIVE_PROFILE, SecurityProfilePolicy.Id.STRICT.name)
            ?: SecurityProfilePolicy.Id.STRICT.name
        val previousAutomaticUpdatesAllowed = profilePrefs.getBoolean(KEY_AUTO_UPDATE_ALLOWED, false)
        val profileStateChanged = SecurityProfilePolicy.hasProfileStateChanged(
            previousProfileId, previousAutomaticUpdatesAllowed, profile
        )
        profilePrefs.edit()
            .putString(KEY_ACTIVE_PROFILE, profile.id.name)
            .putBoolean(KEY_AUTO_UPDATE_ALLOWED, profile.automaticFilterUpdates)
            .apply()
        if (profileStateChanged) {
            context.sendBroadcast(
                Intent(ACTION_PROFILE_CHANGED).setPackage(context.packageName)
            )
        }

        if (changed && prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false)) {
            runCatching {
                context.startService(
                    Intent(context, GuardianVpnService::class.java)
                        .setAction(GuardianVpnService.ACTION_RELOAD_RULES)
                )
            }
        }
    }
}

/**
 * Automatic network-profile switching is DEBUG-only until PRO entitlement and
 * Android device validation are complete. Unknown Wi-Fi is treated as public.
 */
internal object SecurityProfileMonitor {
    private val callbackRef = AtomicReference<ConnectivityManager.NetworkCallback?>(null)
    @Volatile private var managerRef: ConnectivityManager? = null

    @Synchronized
    fun refresh(context: Context) {
        val app = context.applicationContext
        val active = app.getSharedPreferences(GuardianVpnService.PREFS, Context.MODE_PRIVATE)
            .getBoolean(GuardianVpnService.KEY_ACTIVE, false)
        if (!BuildConfig.DEBUG || !active || !SecurityProfileStore.automaticSwitchEnabled(app)) {
            stop()
            return
        }
        if (Build.VERSION.SDK_INT < 24 || callbackRef.get() != null) return
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                manager.getNetworkCapabilities(network)?.let { apply(app, it, manager) }
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                apply(app, capabilities, manager)
            }
            override fun onLost(network: Network) {
                val activeNetwork = manager.activeNetwork
                val capabilities = activeNetwork?.let(manager::getNetworkCapabilities)
                if (capabilities == null) applyUnknown(app) else apply(app, capabilities, manager)
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }.onSuccess {
            managerRef = manager
            callbackRef.set(callback)
            val activeNetwork = manager.activeNetwork
            val capabilities = activeNetwork?.let(manager::getNetworkCapabilities)
            if (capabilities == null) applyUnknown(app) else apply(app, capabilities, manager)
        }
    }

    @Synchronized
    fun stop() {
        val callback = callbackRef.getAndSet(null) ?: return
        val manager = managerRef
        managerRef = null
        if (manager != null) runCatching { manager.unregisterNetworkCallback(callback) }
    }

    private fun applyUnknown(context: Context) {
        val unknown = SecurityProfilePolicy.NetworkContext(
            SecurityProfilePolicy.Transport.UNKNOWN,
            validated = false,
            captivePortal = false,
            metered = true,
            roaming = false,
            trustedNetwork = false
        )
        SecurityProfileStore.applyAutomaticProfile(context, SecurityProfilePolicy.resolveAutomatic(unknown))
    }

    private fun apply(
        context: Context,
        reportedCapabilities: NetworkCapabilities,
        manager: ConnectivityManager
    ) {
        // The default route can be Guardian's own VPN interface. Prefer a physical
        // underlying network when Android exposes one, otherwise use the reported facts.
        val capabilities = if (reportedCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            val physicalNetworks = manager.allNetworks.mapNotNull(manager::getNetworkCapabilities)
                .filterNot { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
            if (SecurityProfilePolicy.hasUnambiguousUnderlyingNetwork(physicalNetworks.size)) {
                physicalNetworks.single()
            } else {
                // The VPN is active but its physical default cannot be inferred
                // unambiguously. Keep it UNKNOWN/strict rather than trusting a
                // merely high-scoring, possibly non-default secondary network.
                reportedCapabilities
            }
        } else reportedCapabilities
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> SecurityProfilePolicy.Transport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> SecurityProfilePolicy.Transport.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> SecurityProfilePolicy.Transport.ETHERNET
            else -> SecurityProfilePolicy.Transport.OTHER
        }
        val network = SecurityProfilePolicy.NetworkContext(
            transport = transport,
            validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            captivePortal = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
            metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            roaming = false,
            trustedNetwork = false
        )
        SecurityProfileStore.applyAutomaticProfile(context, SecurityProfilePolicy.resolveAutomatic(network))
    }
}
