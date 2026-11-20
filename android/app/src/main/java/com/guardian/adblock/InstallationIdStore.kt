package com.guardian.adblock

/**
 * Serializes creation of a persistent installation identifier.
 *
 * A racing first-run activation/refresh must never send two device bindings
 * for the same app installation and strand a server-side activation slot.
 */
internal class InstallationIdStore {
    @Synchronized
    fun getOrCreate(
        readExisting: () -> String?,
        create: () -> String,
        persist: (String) -> Boolean
    ): String {
        readExisting()?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = create().also {
            require(it.isNotBlank()) { "기기 등록 식별자가 비어 있습니다." }
        }
        check(persist(generated)) { "기기 등록 식별자를 저장하지 못했습니다." }
        return generated
    }
}
