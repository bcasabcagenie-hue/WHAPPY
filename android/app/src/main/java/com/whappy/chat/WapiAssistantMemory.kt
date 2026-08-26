package com.whappy.chat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WapiStoredAssistantMessage(
    val id: String,
    val text: String,
    val fromUser: Boolean,
    val failed: Boolean = false,
)

/**
 * Encrypted local-first memory for WIA.
 *
 * Cloud history remains the cross-device source of truth, while this cache
 * restores the conversation immediately and preserves a prompt if the app is
 * closed or the network fails before Pilotis answers.
 */
object WapiAssistantMemory {
    private const val storageName = "wapi_wepi_memory_v1"
    private const val maximumMessages = 100

    fun read(context: Context, userId: String): List<WapiStoredAssistantMessage> {
        if (userId.isBlank()) return emptyList()
        val encoded = WhappyFastStorage.preferences(context, storageName)
            .getString(key(userId), null) ?: return emptyList()
        return runCatching {
            val values = JSONArray(encoded)
            buildList {
                for (index in 0 until values.length()) {
                    val value = values.optJSONObject(index) ?: continue
                    val id = value.optString("id").trim()
                    val text = value.optString("text").trim()
                    if (id.isNotBlank() && text.isNotBlank()) {
                        add(WapiStoredAssistantMessage(id, text, value.optBoolean("fromUser"), value.optBoolean("failed")))
                    }
                }
            }.takeLast(maximumMessages)
        }.getOrElse { emptyList() }
    }

    fun write(context: Context, userId: String, messages: List<WapiStoredAssistantMessage>) {
        if (userId.isBlank()) return
        val encoded = JSONArray().apply {
            messages.takeLast(maximumMessages).forEach { message ->
                put(JSONObject()
                    .put("id", message.id.take(96))
                    .put("text", message.text.take(12_000))
                    .put("fromUser", message.fromUser)
                    .put("failed", message.failed))
            }
        }.toString()
        WhappyFastStorage.preferences(context, storageName)
            .edit()
            .putString(key(userId), encoded)
            .apply()
    }

    private fun key(userId: String) = "thread-main-${userId.take(128)}"
}
