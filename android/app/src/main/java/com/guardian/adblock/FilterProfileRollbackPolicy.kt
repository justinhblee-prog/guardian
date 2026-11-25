package com.guardian.adblock

/** Keeps a failed profile switch from stranding the app without its previous usable cache. */
internal object FilterProfileRollbackPolicy {
    fun shouldRollback(previousProfileKey: String?, selectedProfileKey: String): Boolean =
        !previousProfileKey.isNullOrBlank() && previousProfileKey != selectedProfileKey
}
