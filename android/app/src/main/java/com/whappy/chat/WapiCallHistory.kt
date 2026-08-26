package com.whappy.chat

import android.content.Context

/** Local, privacy-preserving call history. It stores no audio or call media. */
object WapiCallHistory {
    private const val preferencesName = "wapi_calls"
    private const val entriesKey = "recent_calls"

    fun entries(context: Context): List<String> = WhappyFastStorage
        .preferences(context, preferencesName)
        .getStringSet(entriesKey, emptySet())
        .orEmpty()
        .toList()
        .sortedDescending()

    fun record(
        context: Context,
        name: String,
        phoneNumber: String,
        video: Boolean,
        direction: String,
        userId: String = "",
        photoUrl: String = "",
    ): List<String> {
        val entry = listOf(
            System.currentTimeMillis().toString(),
            name.replace("|", " ").ifBlank { "Contact WAPI" },
            phoneNumber.replace("|", " "),
            if (video) "video" else "audio",
            direction,
            userId.replace("|", " "),
            photoUrl.replace("|", "%7C"),
        ).joinToString("|")
        val next = (listOf(entry) + entries(context)).take(30)
        WhappyFastStorage.preferences(context, preferencesName)
            .edit()
            .putStringSet(entriesKey, next.toSet())
            .apply()
        return next
    }
}
