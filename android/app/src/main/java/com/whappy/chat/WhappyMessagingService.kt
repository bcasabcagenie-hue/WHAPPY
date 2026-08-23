package com.whappy.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class WhappyMessagingService : FirebaseMessagingService() {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val deviceId = token.takeLast(32).replace(Regex("[^A-Za-z0-9_-]"), "_")
        FirebaseFirestore.getInstance().collection("users").document(user.uid)
            .collection("devices").document(deviceId).set(
                mapOf("token" to token, "platform" to "android", "enabled" to true, "updatedAt" to FieldValue.serverTimestamp()),
                com.google.firebase.firestore.SetOptions.merge(),
            )
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: "WAPI"
        val body = message.notification?.body ?: data["body"] ?: "Vous avez une nouvelle activité."
        when (data["type"]?.lowercase()) {
            "call", "incoming_call" -> WhappyNotifications.showIncomingCall(
                context = this,
                callId = data["callId"].orEmpty(),
                callerName = data["callerName"] ?: title,
                video = data["video"].toBoolean(),
            )
            "message", "chat" -> WhappyNotifications.showMessage(
                context = this,
                title = title,
                body = body,
                senderName = data["senderName"] ?: title,
                conversationId = data["conversationId"].orEmpty(),
            )
            else -> WhappyNotifications.showActivity(this, title, body)
        }
    }
}

object WhappyNotifications {
    const val EXTRA_CALL_ACTION = "whappy_call_action"
    const val EXTRA_CALL_ID = "whappy_call_id"
    const val ACTION_ACCEPT_CALL = "com.whappy.chat.ACCEPT_CALL"
    const val ACTION_DECLINE_CALL = "com.whappy.chat.DECLINE_CALL"

    private const val BRAND_COLOR = 0xFF0094F0.toInt()
    // Android keeps a channel's sound policy after its first creation.  A new
    // id deliberately upgrades devices that installed an older silent build.
    private const val CHANNEL_MESSAGES = "wapi_messages_v5"
    private const val CHANNEL_CALLS = "whappy_calls_v3"
    private const val CHANNEL_ACTIVITY = "whappy_activity_v2"
    private const val CALL_NOTIFICATION_BASE = 6_100
    private const val MESSAGE_SUMMARY_ID = 6_001
    private const val MESSAGE_GROUP = "wapi_messages"
    private const val BADGE_TOTAL_KEY = "unread_badge_total"
    private const val BADGE_CONVERSATION_PREFIX = "unread_badge_"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val messageSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtoneAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val messageAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Nouveaux messages et réponses WAPI"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 120, 80, 120)
                    setSound(messageSound, messageAttributes)
                    enableLights(true)
                    lightColor = BRAND_COLOR
                    setShowBadge(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                },
                NotificationChannel(CHANNEL_CALLS, "Appels entrants", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Appels audio et vidéo WAPI"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 700, 350, 700, 350, 700)
                    setSound(ringtone, ringtoneAttributes)
                    setShowBadge(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                },
                NotificationChannel(CHANNEL_ACTIVITY, "Activité WAPI", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Commandes, paiements, chaînes et activité du compte"
                    enableVibration(true)
                    setShowBadge(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                },
            ),
        )
    }

    @SuppressLint("MissingPermission")
    fun showMessage(context: Context, title: String, body: String, senderName: String, conversationId: String) {
        if (!preferences(context).getBoolean("notify_messages", true) || !canNotify(context)) return
        ensureChannel(context)
        val safeConversationId = conversationId.ifBlank { "$title:$body" }
        val unreadCount = incrementUnreadBadge(context, safeConversationId)
        val open = openAppIntent(context, safeConversationId.hashCode())
        val sender = Person.Builder().setName(senderName.ifBlank { title }).build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Vous").build())
            .setConversationTitle(title)
            .addMessage(body, System.currentTimeMillis(), sender)
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_wapi)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 120, 80, 120))
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setNumber(unreadCount)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setGroup(MESSAGE_GROUP)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        val manager = NotificationManagerCompat.from(context)
        manager.notify(notificationId(safeConversationId), notification)
        manager.notify(MESSAGE_SUMMARY_ID, messageSummary(context, unreadCount))
    }

    /** Clears only the opened thread from the launcher count, not unrelated chats. */
    fun markConversationOpened(context: Context, conversationId: String) {
        if (conversationId.isBlank()) return
        val preferences = preferences(context)
        val key = badgeConversationKey(conversationId)
        val threadCount = preferences.getInt(key, 0)
        if (threadCount <= 0) return
        val remaining = (preferences.getInt(BADGE_TOTAL_KEY, 0) - threadCount).coerceAtLeast(0)
        preferences.edit().remove(key).putInt(BADGE_TOTAL_KEY, remaining).apply()
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(notificationId(conversationId))
        if (remaining == 0) manager.cancel(MESSAGE_SUMMARY_ID)
        else manager.notify(MESSAGE_SUMMARY_ID, messageSummary(context, remaining))
    }

    @SuppressLint("MissingPermission")
    fun showIncomingCall(context: Context, callId: String, callerName: String, video: Boolean): Boolean {
        if (!preferences(context).getBoolean("notify_calls", true) || !canNotify(context)) return false
        ensureChannel(context)
        val safeCallId = callId.ifBlank { "incoming-${System.currentTimeMillis()}" }
        val requestCode = notificationId(safeCallId)
        val openIntent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_CALL_ID, safeCallId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, requestCode + 2, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val answerIntent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_CALL_ACTION, ACTION_ACCEPT_CALL)
            .putExtra(EXTRA_CALL_ID, safeCallId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val answer = PendingIntent.getActivity(context, requestCode, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val declineIntent = Intent(context, WhappyCallActionReceiver::class.java)
            .setAction(ACTION_DECLINE_CALL)
            .putExtra(EXTRA_CALL_ID, safeCallId)
        val decline = PendingIntent.getBroadcast(context, requestCode + 1, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val caller = Person.Builder().setName(callerName.ifBlank { "Contact WAPI" }).setImportant(true).build()
        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_stat_wapi)
            .setColor(BRAND_COLOR)
            .setContentTitle(caller.name)
            .setContentText(if (video) "Appel vidéo entrant" else "Appel audio entrant")
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, answer))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setOngoing(true)
            .setTimeoutAfter(120_000L)
            .build()
        NotificationManagerCompat.from(context).notify(callNotificationId(safeCallId), notification)
        return true
    }

    @SuppressLint("MissingPermission")
    fun showActivity(context: Context, title: String, body: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ACTIVITY)
            .setSmallIcon(R.drawable.ic_stat_wapi)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, notificationId("$title:$body")))
            .build()
        NotificationManagerCompat.from(context).notify(notificationId("$title:$body"), notification)
    }

    fun cancelCall(context: Context, callId: String) {
        if (callId.isNotBlank()) NotificationManagerCompat.from(context).cancel(callNotificationId(callId))
    }

    private fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun preferences(context: Context) = WhappyFastStorage.preferences(context, "whappy_consumer")

    private fun incrementUnreadBadge(context: Context, conversationId: String): Int {
        val preferences = preferences(context)
        val key = badgeConversationKey(conversationId)
        val threadCount = preferences.getInt(key, 0) + 1
        val total = (preferences.getInt(BADGE_TOTAL_KEY, 0) + 1).coerceAtMost(999)
        preferences.edit().putInt(key, threadCount).putInt(BADGE_TOTAL_KEY, total).apply()
        return total
    }

    private fun messageSummary(context: Context, count: Int) = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
        .setSmallIcon(R.drawable.ic_stat_wapi)
        .setColor(BRAND_COLOR)
        .setContentTitle("WAPI")
        .setContentText("$count message${if (count > 1) "s" else ""} non lu${if (count > 1) "s" else ""}")
        .setStyle(NotificationCompat.InboxStyle().setSummaryText("$count message${if (count > 1) "s" else ""} non lu${if (count > 1) "s" else ""}"))
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setNumber(count)
        .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
        .setGroup(MESSAGE_GROUP)
        .setGroupSummary(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setAutoCancel(true)
        .setContentIntent(openAppIntent(context, MESSAGE_SUMMARY_ID))
        .build()

    private fun badgeConversationKey(conversationId: String) = BADGE_CONVERSATION_PREFIX + notificationId(conversationId)

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun notificationId(key: String): Int = key.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
    private fun callNotificationId(callId: String): Int = CALL_NOTIFICATION_BASE + notificationId(callId) % 1_000_000
}

class WhappyCallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WhappyNotifications.ACTION_DECLINE_CALL) return
        val callId = intent.getStringExtra(WhappyNotifications.EXTRA_CALL_ID).orEmpty()
        if (callId.isBlank()) return
        WhappyNotifications.cancelCall(context, callId)
        val pendingResult = goAsync()
        FirebaseFirestore.getInstance().collection("calls").document(callId).update(
            mapOf("status" to "declined", "updatedAt" to FieldValue.serverTimestamp()),
        ).addOnCompleteListener { pendingResult.finish() }
    }
}
