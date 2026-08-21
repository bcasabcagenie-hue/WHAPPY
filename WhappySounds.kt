package com.whappy.chat

import android.media.AudioManager
import android.media.ToneGenerator

object WhappySounds {
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
}
