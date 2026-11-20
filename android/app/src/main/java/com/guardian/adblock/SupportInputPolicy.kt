package com.guardian.adblock

import java.util.Locale

/** Client-side validation mirrors the support API contract before network requests. */
internal object SupportInputPolicy {
    fun isValidReplyEmail(value: String): Boolean {
        val email = value.trim()
        return email.isEmpty() || (
            email.length <= 254 &&
                !email.contains('\u0000') &&
                Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)
            )
    }

    /** Server-generated references are GDN- plus 12 uppercase hexadecimal characters. */
    fun normalizeTicketReference(value: String): String? {
        val normalized = value.trim().uppercase(Locale.ROOT)
        return normalized.takeIf { it.matches(Regex("GDN-[A-F0-9]{12}")) }
    }
}
