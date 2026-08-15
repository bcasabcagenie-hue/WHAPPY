package com.whappy.chat

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed interface WhappyLink {
    data class Contact(val phone: String) : WhappyLink
    data class Channel(val id: String) : WhappyLink
    data class Search(val query: String) : WhappyLink

    companion object {
        fun parse(value: String): WhappyLink? {
            val raw = value.trim()
            if (raw.isBlank()) return null

            val legacyContact = "WHAPPY:CONTACT:"
            if (raw.startsWith(legacyContact, ignoreCase = true)) {
                val payload = raw.substring(legacyContact.length)
                return PhoneNumberFormatter.normalizeAny(payload)?.let(::Contact)
            }

            return runCatching {
                val uri = URI(raw)
                val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
                val scheme = uri.scheme.orEmpty().lowercase()
                val hasAuthority = uri.host.orEmpty().isNotBlank()
                val host = uri.host.orEmpty().lowercase()
                val action = if (scheme == "whappy") {
                    if (hasAuthority) host else segments.firstOrNull().orEmpty().lowercase()
                } else {
                    segments.firstOrNull().orEmpty().lowercase()
                }
                val payload = if (scheme == "whappy") {
                    if (segments.isNotEmpty()) {
                        if (hasAuthority) segments.firstOrNull() else segments.getOrNull(1)
                    } else {
                        queryParameter(uri.rawQuery, "phone")
                    }
                } else {
                    segments.drop(1).firstOrNull() ?: segments.firstOrNull()
                }
                when (action) {
                    "contact" -> PhoneNumberFormatter.normalizeAny(decode(payload))?.let(::Contact)
                    "channel", "chaine" -> decode(payload).takeIf { it.matches(Regex("[A-Za-z0-9_-]{2,160}")) }?.let(::Channel)
                    "search", "recherche" -> run {
                        val query = queryParameter(uri.rawQuery, "q")
                            ?: queryParameter(uri.rawQuery, "query")
                        (if (query.isNullOrBlank()) segments.drop(1).firstOrNull() ?: segments.firstOrNull() else query)
                            ?.ifEmpty { null }
                            ?.take(120)
                            ?.takeIf { it.isNotBlank() }
                            ?.let(::Search)
                    }
                    else -> null
                }
            }.getOrNull()
        }

        private fun queryParameter(query: String?, name: String): String? = query.orEmpty().split('&').firstNotNullOfOrNull { part ->
            val pieces = part.split('=', limit = 2)
            if (decode(pieces.firstOrNull().orEmpty()) == name) decode(pieces.getOrElse(1) { "" }) else null
        }

        private fun decode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.toString())
    }
}
