package com.whappy.chat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encrypted local-first message history backed by Tencent MMKV's C++/mmap core.
 * Firestore remains the shared source of truth; this cache makes opening a thread
 * immediate and preserves the latest history when the device is offline.
 */
class WhappyMessageCache(context: Context) {
    private val storage = WhappyFastStorage.preferences(context, STORAGE_NAME)

    fun read(conversationId: String, source: String): List<WhappyMessage> {
        val encoded = storage.getString(key(conversationId, source), null) ?: return emptyList()
        return runCatching {
            val values = JSONArray(encoded)
            buildList(values.length()) {
                for (index in 0 until values.length()) {
                    values.optJSONObject(index)?.toMessage()?.let(::add)
                }
            }.sortedBy { it.createdAt }
        }.getOrElse {
            storage.edit().remove(key(conversationId, source)).apply()
            emptyList()
        }
    }

    fun write(conversationId: String, source: String, messages: List<WhappyMessage>) {
        val encoded = JSONArray().apply {
            messages.takeLast(MAX_MESSAGES).forEach { put(it.toJson()) }
        }.toString()
        storage.edit().putString(key(conversationId, source), encoded).apply()
    }

    private fun key(conversationId: String, source: String) =
        "${if (source == "groups") "group" else "direct"}:$conversationId"

    private fun WhappyMessage.toJson() = JSONObject()
        .put("id", id)
        .put("text", text)
        .put("senderId", senderId)
        .put("createdAt", createdAt)
        .put("kind", kind)
        .put("mediaUrl", mediaUrl)
        .put("mediaName", mediaName)
        .put("durationSeconds", durationSeconds)
        .put("replyToId", replyToId)
        .put("replyText", replyText)
        .put("reactions", JSONObject(reactions))
        .put("deleted", deleted)
        .put("edited", edited)
        .put("deliveryState", deliveryState)
        .put("senderName", senderName)

    private fun JSONObject.toMessage(): WhappyMessage? {
        val id = optString("id")
        val senderId = optString("senderId")
        if (id.isBlank() || senderId.isBlank()) return null
        val reactionObject = optJSONObject("reactions")
        val reactions = buildMap {
            reactionObject?.keys()?.forEach { memberId ->
                reactionObject.optString(memberId).takeIf(String::isNotBlank)?.let { put(memberId, it) }
            }
        }
        return WhappyMessage(
            id = id,
            text = optString("text"),
            senderId = senderId,
            createdAt = optLong("createdAt"),
            kind = optString("kind", "text"),
            mediaUrl = optString("mediaUrl"),
            mediaName = optString("mediaName"),
            durationSeconds = optInt("durationSeconds"),
            replyToId = optString("replyToId"),
            replyText = optString("replyText"),
            reactions = reactions,
            deleted = optBoolean("deleted"),
            edited = optBoolean("edited"),
            deliveryState = optString("deliveryState", "sent"),
            senderName = optString("senderName"),
        )
    }

    private companion object {
        const val STORAGE_NAME = "whappy_message_history_v1"
        const val MAX_MESSAGES = 500
    }
}
