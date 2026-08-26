package com.whappy.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
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
        val type = data["type"]?.lowercase()
        // While WAPI is open, the Firestore inbox listener below is the live
        // foreground path. Keeping FCM for background only prevents duplicate
        // sounds and duplicate message cards while preserving closed-app push.
        if (WapiPresence.isForeground && type in setOf("message", "chat") && WhappyRealtimeNotifications.isReady) return
        when (type) {
            "call", "incoming_call", "direct_call" -> WhappyNotifications.showIncomingCall(
                context = this,
                callId = data["callId"].orEmpty(),
                callerName = data["callerName"] ?: title,
                callerPhotoUrl = data["callerPhotoUrl"].orEmpty(),
                video = data["video"].toBoolean(),
            )
            "call_cancel", "call_ended", "call_declined" -> {
                WhappyNotifications.cancelCall(this, data["callId"].orEmpty())
                WhappyCallEvents.notifyEnded(data["callId"].orEmpty())
            }
            "group_call" -> WhappyNotifications.showGroupCall(
                context = this,
                callId = data["callId"].orEmpty(),
                groupName = title,
                callerName = data["callerName"] ?: "Membre WAPI",
                video = data["video"].toBoolean(),
                deepLink = data["deepLink"] ?: "whappy://group-call/${data["callId"].orEmpty()}",
            )
            "live" -> WhappyNotifications.showLiveStarted(
                context = this,
                title = title,
                body = body,
                deepLink = data["deepLink"] ?: "whappy://live/${data["liveId"].orEmpty()}",
            )
            "message", "chat" -> WhappyNotifications.showMessage(
                context = this,
                title = title,
                body = body,
                senderName = data["senderName"] ?: title,
                senderPhotoUrl = data["senderPhotoUrl"].orEmpty(),
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

    private const val BRAND_COLOR = 0xFF0A92F7.toInt()
    // Android keeps a channel's sound policy after its first creation.  A new
    // id deliberately upgrades devices that installed an older silent build.
    private const val CHANNEL_MESSAGES = "wapi_messages_v5"
    private const val CHANNEL_CALLS = "whappy_calls_v5"
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
    fun showMessage(context: Context, title: String, body: String, senderName: String, senderPhotoUrl: String = "", conversationId: String) {
        if (!preferences(context).getBoolean("notify_messages", true) || isConversationMuted(context, conversationId) || !canNotify(context)) return
        ensureChannel(context)
        val safeConversationId = conversationId.ifBlank { "$title:$body" }
        val unreadCount = incrementUnreadBadge(context, safeConversationId)
        val open = openAppIntent(context, safeConversationId.hashCode())
        val senderAvatar = cachedProfileBitmap(context, senderPhotoUrl)
        val sender = Person.Builder().setName(senderName.ifBlank { title }).apply {
            senderAvatar?.let { setIcon(IconCompat.createWithAdaptiveBitmap(it)) }
        }.build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Vous").build())
            .setConversationTitle(title)
            .addMessage(body, System.currentTimeMillis(), sender)
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
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
            .apply { senderAvatar?.let { avatar -> setLargeIcon(avatar) } }
        if (preferences(context).getBoolean("protect_preview", true)) {
            notificationBuilder.setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                    .setSmallIcon(R.drawable.ic_stat_wapi)
                    .setColor(BRAND_COLOR)
                    .setContentTitle("Nouveau message WAPI")
                    .setContentText("Déverrouillez pour afficher la conversation")
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build(),
            )
        }
        val notification = notificationBuilder.build()
        val manager = NotificationManagerCompat.from(context)
        manager.notify(notificationId(safeConversationId), notification)
        manager.notify(MESSAGE_SUMMARY_ID, messageSummary(context, unreadCount))
    }

    fun isConversationMuted(context: Context, conversationId: String): Boolean {
        if (conversationId.isBlank()) return false
        val preferences = preferences(context)
        val until = preferences.getLong("muted_conversation_until_$conversationId", 0L)
        if (until == Long.MAX_VALUE || until > System.currentTimeMillis()) return true
        if (until > 0L) preferences.edit().remove("muted_conversation_until_$conversationId").apply()
        return preferences.getBoolean("muted_conversation_$conversationId", false)
    }

    fun setConversationMuted(context: Context, conversationId: String, muted: Boolean) {
        if (conversationId.isBlank()) return
        preferences(context).edit()
            .putBoolean("muted_conversation_$conversationId", muted)
            .putLong("muted_conversation_until_$conversationId", if (muted) Long.MAX_VALUE else 0L)
            .apply()
        if (muted) NotificationManagerCompat.from(context).cancel(notificationId(conversationId))
    }

    fun muteConversationFor(context: Context, conversationId: String, durationMillis: Long?) {
        if (conversationId.isBlank()) return
        val until = durationMillis?.let { System.currentTimeMillis() + it.coerceAtLeast(1L) } ?: Long.MAX_VALUE
        preferences(context).edit()
            .putBoolean("muted_conversation_$conversationId", false)
            .putLong("muted_conversation_until_$conversationId", until)
            .apply()
        NotificationManagerCompat.from(context).cancel(notificationId(conversationId))
    }

    /** Clears only the opened thread from the launcher count, not unrelated chats. */
    @SuppressLint("MissingPermission")
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
        else if (canNotify(context)) {
            runCatching { manager.notify(MESSAGE_SUMMARY_ID, messageSummary(context, remaining)) }
        }
    }

    @SuppressLint("MissingPermission")
    fun showIncomingCall(context: Context, callId: String, callerName: String, callerPhotoUrl: String = "", video: Boolean): Boolean {
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
        // Reuse the encrypted media cache: the notification shows the profile
        // photo when WAPI has already loaded it, without blocking an incoming
        // call on a network download while the application is sleeping.
        val callerAvatar = cachedProfileBitmap(context, callerPhotoUrl)
        val caller = Person.Builder().setName(callerName.ifBlank { "Contact WAPI" }).setImportant(true).apply {
            callerAvatar?.let { setIcon(IconCompat.createWithAdaptiveBitmap(it)) }
        }.build()
        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_stat_wapi)
            .setColor(BRAND_COLOR)
            .setContentTitle(caller.name)
            .setContentText(if (video) "Appel vidéo entrant" else "Appel audio entrant")
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, answer))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setVibrate(longArrayOf(0, 700, 350, 700, 350, 700))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setOngoing(true)
            .setTimeoutAfter(120_000L)
            .apply { callerAvatar?.let { avatar -> setLargeIcon(avatar) } }
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT }
        NotificationManagerCompat.from(context).notify(callNotificationId(safeCallId), notification)
        return true
    }

    @SuppressLint("MissingPermission")
    fun showGroupCall(context: Context, callId: String, groupName: String, callerName: String, video: Boolean, deepLink: String): Boolean {
        if (!preferences(context).getBoolean("notify_calls", true) || !canNotify(context)) return false
        ensureChannel(context)
        val safeCallId = callId.ifBlank { "group-${System.currentTimeMillis()}" }
        val requestCode = callNotificationId(safeCallId)
        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, requestCode, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_stat_wapi)
            .setColor(BRAND_COLOR)
            .setContentTitle(groupName.ifBlank { "Appel de groupe WAPI" })
            .setContentText("$callerName a lancé un appel ${if (video) "vidéo" else "audio"}")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(R.drawable.ic_stat_wapi, "Rejoindre", open)
            .setAutoCancel(true)
            .setTimeoutAfter(180_000L)
            .build()
        NotificationManagerCompat.from(context).notify(requestCode, notification)
        return true
    }

    @SuppressLint("MissingPermission")
    fun showLiveStarted(context: Context, title: String, body: String, deepLink: String) {
        if (!preferences(context).getBoolean("notify_messages", true) || !canNotify(context)) return
        ensureChannel(context)
        val requestCode = notificationId(deepLink)
        val open = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(Intent.ACTION_VIEW, Uri.parse(deepLink), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ACTIVITY)
            .setSmallIcon(R.drawable.ic_stat_wapi)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(R.drawable.ic_stat_wapi, "Rejoindre", open)
            .build()
        NotificationManagerCompat.from(context).notify(requestCode, notification)
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

    private fun cachedProfileBitmap(context: Context, photoUrl: String): Bitmap? {
        if (photoUrl.isBlank()) return null
        val bytes = WapiMediaStore.readCache(context, WapiMediaStore.keyFor(photoUrl), maxBytes = 20L * 1024L * 1024L) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 320) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun badgeConversationKey(conversationId: String) = BADGE_CONVERSATION_PREFIX + notificationId(conversationId)

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun notificationId(key: String): Int = key.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
    private fun callNotificationId(callId: String): Int = CALL_NOTIFICATION_BASE + notificationId(callId) % 1_000_000
}

/** Bridges a background FCM cancellation to the in-process call controller. */
object WhappyCallEvents {
    @Volatile private var endedListener: ((String) -> Unit)? = null

    fun bind(listener: ((String) -> Unit)?) {
        endedListener = listener
    }

    fun notifyEnded(callId: String) {
        if (callId.isNotBlank()) endedListener?.invoke(callId)
    }
}

/**
 * Foreground notification path. Calls already have a direct listener; normal
 * messages use the same low-latency Firestore signal so the app does not wait
 * for a background FCM delivery while it is visible.
 */
object WhappyRealtimeNotifications {
    private var registration: com.google.firebase.firestore.ListenerRegistration? = null
    private var boundUserId = ""
    private val lastUnreadByConversation = mutableMapOf<String, Int>()
    private var initialized = false
    @Volatile
    var isReady: Boolean = false
        private set

    fun bind(context: Context, userId: String?) {
        val next = userId.orEmpty()
        if (next == boundUserId && registration != null) return
        registration?.remove()
        registration = null
        boundUserId = next
        lastUnreadByConversation.clear()
        initialized = false
        isReady = false
        if (next.isBlank()) return
        registration = FirebaseFirestore.getInstance()
            .collection("users").document(next)
            .collection("notificationState").document("inbox")
            .collection("conversations")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    isReady = false
                    return@addSnapshotListener
                }
                isReady = true
                if (!initialized) {
                    snapshot.documents.forEach { document ->
                        lastUnreadByConversation[document.id] = document.getLong("unreadMessages")?.toInt()?.coerceAtLeast(0) ?: 0
                    }
                    initialized = true
                    return@addSnapshotListener
                }
                snapshot.documentChanges.forEach { change ->
                    val document = change.document
                    val unread = document.getLong("unreadMessages")?.toInt()?.coerceAtLeast(0) ?: 0
                    val previous = lastUnreadByConversation[document.id] ?: 0
                    lastUnreadByConversation[document.id] = unread
                    if (unread > previous && WapiPresence.isForeground) {
                        WhappyNotifications.showMessage(
                            context = context,
                            title = "Nouveau message WAPI",
                            body = if (unread - previous == 1) "Nouveau message" else "${unread - previous} nouveaux messages",
                            senderName = "WAPI",
                            conversationId = document.id,
                        )
                    }
                }
                snapshot.documentChanges.filter { it.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED }
                    .forEach { lastUnreadByConversation.remove(it.document.id) }
            }
    }

    fun clear() {
        registration?.remove()
        registration = null
        boundUserId = ""
        lastUnreadByConversation.clear()
        initialized = false
        isReady = false
    }
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
