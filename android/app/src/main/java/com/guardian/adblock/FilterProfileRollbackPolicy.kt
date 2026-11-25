package com.guardian.adblock

/** Keeps a failed or interrupted profile switch from stranding the app without a usable cache. */
internal object FilterProfileRollbackPolicy {
    fun shouldRollback(
        previousProfileKey: String?,
        selectedProfileKey: String,
        cachedProfileKey: String?
    ): Boolean =
        !previousProfileKey.isNullOrBlank() &&
            previousProfileKey != selectedProfileKey &&
            cachedProfileKey != selectedProfileKey
}
