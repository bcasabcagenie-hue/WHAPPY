package com.whappy.chat

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.SoundEffectConstants

object WhappySounds {
    private var lastKeyAt = 0L

    private fun tone(type: Int, duration: Int, volume: Int = 55) {
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, volume).also { generator ->
                generator.startTone(type, duration)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ generator.release() }, duration.toLong() + 120L)
            }
        }
    }

    fun mediaAdded() = tone(ToneGenerator.TONE_PROP_ACK, 120, 42)
    fun offerSent() = tone(ToneGenerator.TONE_PROP_PROMPT, 220, 52)
    fun sent() = tone(ToneGenerator.TONE_PROP_ACK, 85, 28)
    fun dice() = tone(ToneGenerator.TONE_DTMF_6, 90, 34)
    fun move() = tone(ToneGenerator.TONE_PROP_BEEP, 55, 24)
    fun reward() = tone(ToneGenerator.TONE_PROP_ACK, 180, 48)
    fun impact() = tone(ToneGenerator.TONE_PROP_NACK, 160, 38)

    fun typing(context: Context) {
        if (!WhappyFastStorage.preferences(context, "whappy_consumer").getBoolean("typing_sounds", true)) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastKeyAt < 34L) return
        lastKeyAt = now
        (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?.playSoundEffect(SoundEffectConstants.CLICK, .12f)
    }

    fun haptic(context: Context, strong: Boolean = false) {
        if (!WhappyFastStorage.preferences(context, "whappy_consumer").getBoolean("haptic_feedback", true)) return
        val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createOneShot(if (strong) 55L else 18L, if (strong) 125 else 55))
        else @Suppress("DEPRECATION") vibrator?.vibrate(if (strong) 55L else 18L)
    }
}
