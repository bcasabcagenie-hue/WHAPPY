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
    private const val CHANNEL_MESSAGES = "whappy_messages_v3"
    private const val CHANNEL_CALLS = "whappy_calls_v3"
    private const val CHANNEL_ACTIVITY = "whappy_activity_v2"
    private const val CALL_NOTIFICATION_BASE = 6_100

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
        val open = openAppIntent(context, conversationId.hashCode())
        val sender = Person.Builder().setName(senderName.ifBlank { title }).build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Vous").build())
            .setConversationTitle(title)
            .addMessage(body, System.currentTimeMillis(), sender)
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.wapi_icon)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setGroup("whappy_messages_${conversationId.ifBlank { "general" }}")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(conversationId.ifBlank { "$title:$body" }), notification)
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
            .setSmallIcon(R.drawable.wapi_icon)
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
            .setSmallIcon(R.drawable.wapi_icon)
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

    private fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun preferences(context: Context) = WhappyFastStorage.preferences(context, "whappy_consumer")

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
