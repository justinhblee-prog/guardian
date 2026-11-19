package com.guardian.adblock

internal data class GuardianUpdateActionState(
    val checkEnabled: Boolean,
    val installEnabled: Boolean
)

internal object GuardianUpdateActionPolicy {
    fun resolve(checking: Boolean, hasVerifiedApk: Boolean): GuardianUpdateActionState =
        GuardianUpdateActionState(
            checkEnabled = !checking && !hasVerifiedApk,
            installEnabled = !checking && hasVerifiedApk
        )
}
