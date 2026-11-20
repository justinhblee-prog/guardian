package com.guardian.adblock

/**
 * Releases a server-side activation if local entitlement verification or persistence
 * fails. The pending request ID is retained unless release is confirmed, so a retry
 * can replay the original server response when compensation was unsuccessful.
 */
internal fun recoverFailedActivation(
    licenseRef: String,
    clearLocalEntitlement: () -> Boolean,
    releaseServerSlot: () -> Unit,
    clearPendingRequest: () -> Boolean,
    onCleanupFailure: (Throwable) -> Unit
): Boolean {
    if (!isSafeLicenseRefForActivationCleanup(licenseRef)) return false
    return try {
        check(clearLocalEntitlement()) {
            "부분 저장된 라이선스 정보를 정리하지 못했습니다."
        }
        releaseServerSlot()
        check(clearPendingRequest()) {
            "보상 해제 후 활성화 재시도 식별자를 정리하지 못했습니다."
        }
        true
    } catch (error: Exception) {
        onCleanupFailure(error)
        false
    }
}
