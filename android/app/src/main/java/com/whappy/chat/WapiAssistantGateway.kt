package com.whappy.chat

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Secure connector reserved for the WAPI assistant.
 *
 * The app calls the WAPI callable function. Pilotis remains the model
 * provider, while its bearer credential stays in WAPI Secret Manager and
 * never ships in the Android/iOS binaries.
 */
object WapiAssistantGateway {
    private val functions by lazy { FirebaseFunctions.getInstance("europe-west1") }

    data class MemoryMessage(val id: String, val fromUser: Boolean, val text: String)

    suspend fun loadHistory(threadId: String = "main"): Result<List<MemoryMessage>> = runCatching {
        val result = functions.getHttpsCallable("getWepiHistory")
            .call(mapOf("threadId" to threadId))
            .await()
        val data = result.data as? Map<*, *> ?: error("Mémoire WEPI invalide.")
        (data["messages"] as? List<*>).orEmpty().mapNotNull { raw ->
            val value = raw as? Map<*, *> ?: return@mapNotNull null
            val text = value["content"]?.toString()?.trim().orEmpty()
            val id = value["id"]?.toString()?.trim().orEmpty()
            if (text.isBlank()) null else MemoryMessage(id.ifBlank { "cloud-${text.hashCode()}" }, value["role"] == "user", text)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun ask(
        prompt: String,
        settings: WapiWepiSettings,
        history: List<Pair<Boolean, String>>,
        messageId: String,
    ): Result<String> = runCatching {
        val safeHistory = history.takeLast(10).map { (fromUser, text) ->
            hashMapOf<String, Any>(
                "fromUser" to fromUser,
                "text" to text.take(4_000),
            )
        }
        val result = functions.getHttpsCallable("askWepi").call(
            hashMapOf<String, Any>(
                "prompt" to prompt.take(4_000),
                "history" to safeHistory,
                "threadId" to "main",
                "messageId" to messageId.take(96),
            ),
        ).await()
        val data = result.data as? Map<*, *> ?: error("Réponse WEPI invalide.")
        data["text"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: error("Réponse WEPI invalide.")
    }
}
