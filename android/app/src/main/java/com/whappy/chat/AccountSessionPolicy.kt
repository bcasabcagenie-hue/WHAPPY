package com.whappy.chat

internal object AccountSessionPolicy {
    private const val NEW_ACCOUNT_WINDOW_MS = 5 * 60 * 1000L

    fun displayName(
        resolvedName: String,
        phoneNumber: String,
        creationTimestamp: Long,
        now: Long = System.currentTimeMillis(),
    ): String {
        if (WhappyIdentity.isFounder(phoneNumber)) return WhappyIdentity.founderName
        val stored = resolvedName.trim()
        if (stored.length >= 2) return stored
        if (now - creationTimestamp <= NEW_ACCOUNT_WINDOW_MS) return ""
        val suffix = phoneNumber.filter(Char::isDigit).takeLast(4)
        return if (suffix.isBlank()) "Membre WAPI" else "Membre $suffix"
    }
}
