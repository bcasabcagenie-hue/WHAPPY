package com.whappy.chat

internal object WhappyIdentity {
    const val founderPhone = "242065465808"
    private const val founderLocalPhone = "065465808"
    const val founderName = "Cyril Bokilo"
    const val founderBusinessName = "Cyril Bokilo"
    const val founderBadgeLabel = "Fondateur"
    const val fallbackAccountName = "Utilisateur WAPI"
    const val founderChannelName = "Le Cercle de Cyril"
    const val founderChannelTagline = "Idées, projets et annonces publiés directement par Cyril Bokilo."

    fun isFounder(phoneNumber: String?): Boolean {
        val normalized = phoneNumber
            .orEmpty()
            .filter(Char::isDigit)
        return normalized == founderPhone || normalized == founderLocalPhone
    }

    fun resolveAccountName(resolvedName: String, phoneNumber: String): String {
        if (isFounder(phoneNumber)) return founderName
        val trimmed = resolvedName.trim()
        if (trimmed.isNotBlank()) return trimmed
        return fallbackAccountName
    }
}
