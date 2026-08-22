package com.whappy.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI-compatible connector reserved for the WAPI assistant.
 *
 * The URL is intentionally isolated here: no credential is stored in the app
 * and a non-JSON or non-2xx answer is reported as unavailable instead of being
 * presented as an AI response.
 */
object WapiAssistantGateway {
    const val endpoint = "https://mypilotis.web.app/wepi-api/v1/chat/completions"

    suspend fun ask(
        prompt: String,
        settings: WapiWepiSettings,
        history: List<Pair<Boolean, String>>,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("model", "wepi")
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", buildSystemPrompt(settings)))
                    history.takeLast(10).forEach { (fromUser, text) ->
                        put(JSONObject().put("role", if (fromUser) "user" else "assistant").put("content", text))
                    }
                    put(JSONObject().put("role", "user").put("content", prompt))
                })
            }
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = connection.responseCode
            val contentType = connection.contentType.orEmpty()
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            require(code in 200..299 && contentType.contains("application/json", ignoreCase = true)) {
                "Le service Assistant WAPI n’est pas encore publié."
            }
            val json = JSONObject(body)
            json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                ?.takeIf { it.isNotBlank() }
                ?: json.optString("content").takeIf { it.isNotBlank() }
                ?: error("Réponse Assistant WAPI invalide.")
        }
    }

    private fun buildSystemPrompt(settings: WapiWepiSettings): String = buildString {
        append("Tu es ").append(settings.assistantName.ifBlank { "l’Assistant WAPI" })
        append(", intégré à WAPI. Réponds en français, avec un ton ").append(settings.tone)
        append(". Ne prétends jamais avoir exécuté une action, confirmé un prix ou accédé à des données si ce n’est pas établi.")
        if (settings.businessName.isNotBlank()) append(" Compte : ").append(settings.businessName).append('.')
        if (settings.instructions.isNotBlank()) append(" Consignes du propriétaire : ").append(settings.instructions.take(600))
    }
}
