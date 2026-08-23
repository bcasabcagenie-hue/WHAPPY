package com.whappy.chat

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class WhappyPendingMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val replyToId: String,
    val replyText: String,
    val createdAt: Long,
    val kind: String = "text",
    val localMediaPath: String = "",
    val contentType: String = "",
    val mediaName: String = "",
    val durationSeconds: Int = 0,
    val source: String = "conversations",
    val senderName: String = "Membre WAPI",
    val senderPhotoUrl: String = "",
    val attempts: Int = 0,
)

/** Encrypted, persistent outbox used when Firestore or the network is unavailable. */
class WhappyMessageOutbox(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "whappy-outbox.db",
    null,
    1,
) {
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE message_outbox (
                id TEXT PRIMARY KEY NOT NULL,
                envelope TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0
            )""".trimIndent(),
        )
        database.execSQL("CREATE INDEX message_outbox_created ON message_outbox(created_at)")
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    suspend fun enqueue(message: WhappyPendingMessage) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("id", message.id)
            put("envelope", WhappyCryptoVault.encrypt(message.toJson()))
            put("created_at", message.createdAt)
            put("attempts", message.attempts)
        }
        writableDatabase.insertWithOnConflict("message_outbox", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun pending(limit: Int = 50): List<WhappyPendingMessage> = withContext(Dispatchers.IO) {
        readableDatabase.query(
            "message_outbox",
            arrayOf("envelope", "attempts"),
            null,
            null,
            null,
            null,
            "created_at ASC",
            limit.coerceIn(1, 200).toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val envelope = cursor.getString(0)
                    val attempts = cursor.getInt(1)
                    runCatching { pendingMessageFromJson(WhappyCryptoVault.decrypt(envelope), attempts) }
                        .getOrNull()
                        ?.let(::add)
                }
            }
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete("message_outbox", "id = ?", arrayOf(id))
    }

    suspend fun markAttempt(id: String) = withContext(Dispatchers.IO) {
        writableDatabase.execSQL(
            "UPDATE message_outbox SET attempts = attempts + 1 WHERE id = ?",
            arrayOf(id),
        )
    }

    private fun WhappyPendingMessage.toJson(): String = JSONObject()
        .put("id", id)
        .put("conversationId", conversationId)
        .put("senderId", senderId)
        .put("text", text)
        .put("replyToId", replyToId)
        .put("replyText", replyText)
        .put("createdAt", createdAt)
        .put("kind", kind)
        .put("localMediaPath", localMediaPath)
        .put("contentType", contentType)
        .put("mediaName", mediaName)
        .put("durationSeconds", durationSeconds)
        .put("source", source)
        .put("senderName", senderName)
        .put("senderPhotoUrl", senderPhotoUrl)
        .toString()

    private fun pendingMessageFromJson(value: String, attempts: Int): WhappyPendingMessage =
        JSONObject(value).let { json ->
            WhappyPendingMessage(
                id = json.getString("id"),
                conversationId = json.getString("conversationId"),
                senderId = json.getString("senderId"),
                text = json.getString("text"),
                replyToId = json.optString("replyToId"),
                replyText = json.optString("replyText"),
                createdAt = json.getLong("createdAt"),
                kind = json.optString("kind", "text"),
                localMediaPath = json.optString("localMediaPath"),
                contentType = json.optString("contentType"),
                mediaName = json.optString("mediaName"),
                durationSeconds = json.optInt("durationSeconds"),
                source = json.optString("source", "conversations"),
                senderName = json.optString("senderName", "Membre WAPI"),
                senderPhotoUrl = json.optString("senderPhotoUrl"),
                attempts = attempts,
            )
        }
}
