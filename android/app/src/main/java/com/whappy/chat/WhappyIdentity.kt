package com.whappy.chat

internal object WhappyIdentity {
    const val founderPhone = "242065465808"
    const val founderName = "Happy"
    const val founderBusinessName = "Whappy by BCA"
    const val founderBadgeLabel = "Fondateur"
    const val fallbackAccountName = "Utilisateur WHAPPY"
    const val founderChannelName = "Le Cercle de Happy"
    const val founderChannelTagline = "Idées, projets et annonces publiés directement par Happy."

    fun isFounder(phoneNumber: String?): Boolean {
        val normalized = phoneNumber
            .orEmpty()
            .filter(Char::isDigit)
        return normalized == founderPhone
    }

    fun resolveAccountName(resolvedName: String, phoneNumber: String): String {
        val trimmed = resolvedName.trim()
        if (trimmed.isNotBlank()) return trimmed
        if (isFounder(phoneNumber)) return founderName
        return fallbackAccountName
    }
}
