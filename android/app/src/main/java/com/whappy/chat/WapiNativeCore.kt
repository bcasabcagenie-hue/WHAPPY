package com.whappy.chat

/**
 * Kotlin boundary for the same C++20 core compiled into the iOS application.
 * Every method keeps a pure Kotlin fallback for Android 6 and damaged installs.
 */
internal object WapiNativeCore {
    private val available = runCatching { System.loadLibrary("wapi_core") }.isSuccess

    fun normalizePhone(value: String?, defaultCountryCode: String = "242"): String {
        val raw = value.orEmpty()
        if (available) runCatching { nativeNormalizePhone(raw, defaultCountryCode) }
            .getOrNull()?.takeIf(String::isNotBlank)?.let { return it }
        val digits = raw.filter(Char::isDigit).removePrefix("00")
        val country = defaultCountryCode.filter(Char::isDigit)
        return if (digits.isBlank() || country.isBlank() || raw.contains('+') || digits.startsWith(country)) digits
        else country + digits
    }

    fun identityRevision(userId: String, photoUrl: String, verified: Boolean): Long =
        if (available) runCatching { nativeIdentityRevision(userId, photoUrl, verified) }.getOrDefault(fallbackRevision(userId, photoUrl, verified))
        else fallbackRevision(userId, photoUrl, verified)

    val version: String
        get() = if (available) runCatching { nativeVersion() }.getOrDefault("wapi-core/kotlin-fallback") else "wapi-core/kotlin-fallback"

    private fun fallbackRevision(userId: String, photoUrl: String, verified: Boolean): Long {
        var hash = 1125899906842597L
        "$userId\u0000$photoUrl\u0000$verified".forEach { hash = hash * 31 + it.code }
        return hash
    }

    @JvmStatic private external fun nativeNormalizePhone(input: String, defaultCountryCode: String): String
    @JvmStatic private external fun nativeIdentityRevision(userId: String, photoUrl: String, verified: Boolean): Long
    @JvmStatic private external fun nativeVersion(): String
}
