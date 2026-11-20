package com.guardian.adblock

import java.util.Locale

/** Client-side validation mirrors the support API contract before network requests. */
internal object SupportInputPolicy {
    private val supportCategories = setOf("question", "bug", "suggestion", "billing", "license")

    fun isValidSupportText(value: String, minLength: Int, maxLength: Int): Boolean {
        val normalized = value.trim()
        return minLength >= 0 && maxLength >= minLength &&
            normalized.length in minLength..maxLength && !normalized.contains('\\u0000')
    }

    fun isValidSupportCategory(value: String): Boolean = value in supportCategories

    fun isValidReplyEmail(value: String): Boolean {
        val email = value.trim()
        return email.isEmpty() || (
            email.length <= 254 &&
                !email.contains('\u0000') &&
                Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)
            )
    }

    /** Server issues a 32-byte base64url access token (43 chars); accept its bounded contract. */
    fun isValidAccessToken(value: String): Boolean =
        value.length in 32..100 && value.all {
            it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_'
        }

    /** Server-generated references are GDN- plus 12 uppercase hexadecimal characters. */
    fun normalizeTicketReference(value: String): String? {
        val normalized = value.trim().uppercase(Locale.ROOT)
        return normalized.takeIf { it.matches(Regex("GDN-[A-F0-9]{12}")) }
    }
}
