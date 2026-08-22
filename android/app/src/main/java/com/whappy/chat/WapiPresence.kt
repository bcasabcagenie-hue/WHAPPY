package com.whappy.chat

import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Foreground presence is intentionally short-lived. A contact is only rendered
 * online while this heartbeat is fresh; a stalled device therefore falls back
 * to its last activity time instead of being shown online indefinitely.
 */
object WapiPresence {
    private const val HEARTBEAT_MS = 45_000L
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = false
    private var activeUserId: String? = null

    private val heartbeat = object : Runnable {
        override fun run() {
            activeUserId?.takeIf { foreground }?.let(::markOnline)
            if (foreground && activeUserId != null) handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    fun onForeground() {
        foreground = true
        activeUserId = FirebaseAuth.getInstance().currentUser?.uid ?: activeUserId
        restartHeartbeat()
    }

    fun onBackground() {
        foreground = false
        handler.removeCallbacks(heartbeat)
        activeUserId?.let(::markOffline)
    }

    fun setActiveUser(userId: String?) {
        val previous = activeUserId
        if (previous != null && previous != userId) markOffline(previous)
        activeUserId = userId
        if (foreground) restartHeartbeat()
    }

    private fun restartHeartbeat() {
        handler.removeCallbacks(heartbeat)
        activeUserId?.let(::markOnline)
        if (foreground && activeUserId != null) handler.postDelayed(heartbeat, HEARTBEAT_MS)
    }

    private fun markOnline(userId: String) {
        FirebaseFirestore.getInstance().collection("users").document(userId).set(
            mapOf(
                "presenceState" to "online",
                "presenceUpdatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        )
    }

    private fun markOffline(userId: String) {
        FirebaseFirestore.getInstance().collection("users").document(userId).set(
            mapOf(
                "presenceState" to "offline",
                "presenceUpdatedAt" to FieldValue.serverTimestamp(),
                "lastSeenAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        )
    }
}
