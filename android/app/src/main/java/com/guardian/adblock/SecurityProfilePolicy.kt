package com.guardian.adblock

/**
 * Resolves a user-selected security profile from Android network facts.
 *
 * This policy intentionally accepts a boolean trust decision from its caller instead of
 * storing or logging Wi-Fi SSIDs. Android may restrict SSID visibility, so an unknown
 * network is never silently treated as trusted.
 */
internal object SecurityProfilePolicy {
    enum class Id { HOME, WORK, PUBLIC_WIFI, MOBILE, TRAVEL, STRICT }
    enum class Transport { WIFI, CELLULAR, ETHERNET, OTHER, UNKNOWN }

    data class NetworkContext(
        val transport: Transport,
        val validated: Boolean,
        val captivePortal: Boolean,
        val metered: Boolean,
        val roaming: Boolean,
        val trustedNetwork: Boolean
    )

    data class Profile(
        val id: Id,
        val label: String,
        val filterProfileKey: String,
        val securityFilterEnabled: Boolean,
        val androidAdsFilterEnabled: Boolean,
        val automaticFilterUpdates: Boolean
    )

    private val profiles = mapOf(
        Id.HOME to Profile(Id.HOME, "집", "adguard", true, true, true),
        Id.WORK to Profile(Id.WORK, "업무", "1hosts_lite", true, true, true),
        Id.PUBLIC_WIFI to Profile(Id.PUBLIC_WIFI, "공용 Wi-Fi", "hagezi_pro", true, true, true),
        Id.MOBILE to Profile(Id.MOBILE, "모바일 데이터", "adguard", true, true, true),
        Id.TRAVEL to Profile(Id.TRAVEL, "여행", "hagezi_pro", true, true, true),
        Id.STRICT to Profile(Id.STRICT, "엄격 보호", "hagezi_pro", true, true, true)
    )

    fun profile(id: Id): Profile = profiles.getValue(id)

    /**
     * When the default route is Guardian's VPN, allNetworks may expose multiple
     * physical networks. Without an API-confirmed underlying default, choosing
     * the highest-scoring candidate can classify a non-default network as active.
     * Only a single candidate is unambiguous; all other counts must fail closed.
     */
    fun hasUnambiguousUnderlyingNetwork(candidateCount: Int): Boolean = candidateCount == 1

    /** Ignore late network callbacks once the user has disabled automatic switching. */
    fun shouldApplyAutomaticProfile(automaticSwitchEnabled: Boolean): Boolean = automaticSwitchEnabled

    /** Manual selection has no trusted metering facts; never silently opt into downloads. */
    fun manualSelection(id: Id): Profile = profile(id).copy(automaticFilterUpdates = false)

    fun hasProfileStateChanged(
        previousProfileId: String,
        previousAutomaticUpdatesAllowed: Boolean,
        nextProfile: Profile
    ): Boolean = previousProfileId != nextProfile.id.name ||
        previousAutomaticUpdatesAllowed != nextProfile.automaticFilterUpdates

    fun resolveAutomatic(context: NetworkContext): Profile {
        // Captive portals and unvalidated networks are treated as untrusted.
        val selected = when {
            context.captivePortal || !context.validated -> profile(Id.STRICT)
            context.roaming -> profile(Id.TRAVEL)
            context.transport == Transport.WIFI && context.trustedNetwork -> profile(Id.HOME)
            context.transport == Transport.WIFI -> profile(Id.PUBLIC_WIFI)
            context.transport == Transport.CELLULAR -> profile(Id.MOBILE)
            context.transport == Transport.ETHERNET && context.trustedNetwork -> profile(Id.WORK)
            else -> profile(Id.STRICT)
        }
        // Never download large filter updates automatically over a metered link.
        return selected.copy(
            automaticFilterUpdates = selected.automaticFilterUpdates && !context.metered
        )
    }
}
