package com.whappy.chat

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/** Speech must yield to incoming calls and other audio, including on Android 6. */
@Suppress("DEPRECATION")
internal class WapiVoiceAudioFocus(context: Context) {
    private val manager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    var onLoss: () -> Unit = {}
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change < 0) main.post { onLoss() }
    }
    fun acquire() = manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    fun release() { manager.abandonAudioFocus(listener) }
}
