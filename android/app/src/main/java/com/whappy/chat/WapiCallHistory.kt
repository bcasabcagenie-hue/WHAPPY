package com.whappy.chat

import android.content.Context

/** Local, privacy-preserving call history. It stores no audio or call media. */
object WapiCallHistory {
    private const val preferencesName = "wapi_calls"
    private const val entriesKey = "recent_calls"
    private const val activeScopeKey = "active_scope"

    fun setActiveScope(context: Context, businessPageId: String) {
        WhappyFastStorage.preferences(context, preferencesName)
            .edit()
            .putString(activeScopeKey, scopeValue(businessPageId))
            .apply()
    }

    fun entries(context: Context, businessPageId: String? = null): List<String> {
        val requestedScope = businessPageId?.let(::scopeValue)
        return WhappyFastStorage
            .preferences(context, preferencesName)
            .getStringSet(entriesKey, emptySet())
            .orEmpty()
            .asSequence()
            .filter { entry ->
                if (requestedScope == null) true
                else entry.split("|", limit = 8).getOrNull(7).orEmpty().ifBlank { "personal" } == requestedScope
            }
            .sortedDescending()
            .toList()
    }

    fun record(
        context: Context,
        name: String,
        phoneNumber: String,
        video: Boolean,
        direction: String,
        userId: String = "",
        photoUrl: String = "",
        businessPageId: String? = null,
    ): List<String> {
        val preferences = WhappyFastStorage.preferences(context, preferencesName)
        val scope = businessPageId?.let(::scopeValue)
            ?: preferences.getString(activeScopeKey, "personal").orEmpty().ifBlank { "personal" }
        val entry = listOf(
            System.currentTimeMillis().toString(),
            name.replace("|", " ").ifBlank { "Contact WAPI" },
            phoneNumber.replace("|", " "),
            if (video) "video" else "audio",
            direction,
            userId.replace("|", " "),
            photoUrl.replace("|", "%7C"),
            scope,
        ).joinToString("|")
        val next = (listOf(entry) + entries(context)).take(30)
        preferences.edit()
            .putStringSet(entriesKey, next.toSet())
            .apply()
        return next
    }

    private fun scopeValue(businessPageId: String): String = businessPageId.trim()
        .takeIf(String::isNotBlank)
        ?.let { "business:$it" }
        ?: "personal"
}
