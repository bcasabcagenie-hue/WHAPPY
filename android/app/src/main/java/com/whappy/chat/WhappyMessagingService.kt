package com.whappy.chat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class WhappyMessagingService : FirebaseMessagingService() {
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
        val title = message.notification?.title ?: message.data["title"] ?: "WHAPPY Business"
        val body = message.notification?.body ?: message.data["body"] ?: "Vous avez une nouvelle activité."
        WhappyNotifications.show(this, title, body)
    }
}

object WhappyNotifications {
    private const val CHANNEL_ID = "whappy_business_payments"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Paiements et commandes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Confirmations de paiement, commandes et Deals WHAPPY"
                enableVibration(true)
            },
        )
    }

    fun show(context: Context, title: String, body: String) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.whappy_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}
