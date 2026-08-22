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
    private var keyboardTone: ToneGenerator? = null

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
    fun cardFlip() = tone(ToneGenerator.TONE_DTMF_2, 70, 30)
    fun pokerAction() = tone(ToneGenerator.TONE_PROP_PROMPT, 100, 34)
    fun reward() = tone(ToneGenerator.TONE_PROP_ACK, 180, 48)
    fun impact() = tone(ToneGenerator.TONE_PROP_NACK, 160, 38)

    fun typing(context: Context) {
        if (!WhappyFastStorage.preferences(context, "whappy_consumer").getBoolean("typing_sounds", true)) return
        val now = android.os.SystemClock.elapsedRealtime()
        // A normal keyboard can emit 10–15 input events per second.  Keeping
        // the click at 48 ms avoids a harsh machine-gun effect while staying
        // responsive enough to feel like a native chat composer.
        if (now - lastKeyAt < 48L) return
        lastKeyAt = now
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        // Use the media stream deliberately: the click follows the volume the
        // person controls on the device, even when Android system touch sounds
        // have been disabled.  It remains silent when media volume is zero.
        if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0) return
        runCatching {
            val generator = keyboardTone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 16).also { keyboardTone = it }
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, 22)
        }.onFailure {
            // A few OEMs restrict ToneGenerator. Their native sound effect is
            // a graceful fallback rather than making typing soundless.
            runCatching { audioManager.playSoundEffect(SoundEffectConstants.CLICK, .12f) }
        }
    }

    fun haptic(context: Context, strong: Boolean = false) {
        if (!WhappyFastStorage.preferences(context, "whappy_consumer").getBoolean("haptic_feedback", true)) return
        val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createOneShot(if (strong) 55L else 18L, if (strong) 125 else 55))
        else @Suppress("DEPRECATION") vibrator?.vibrate(if (strong) 55L else 18L)
    }
}
