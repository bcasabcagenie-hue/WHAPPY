package com.whappy.chat

/** Main-thread ownership includes pending preparation, not just audible playback. */
internal class WapiAudioArbiter {
    private var owner: Any? = null
    private var pauseOwner: (() -> Unit)? = null

    fun claim(next: Any, pause: () -> Unit) {
        if (owner === next) return
        val previous = pauseOwner
        owner = next
        pauseOwner = pause
        previous?.invoke()
    }

    fun release(requester: Any) {
        if (owner === requester) { owner = null; pauseOwner = null }
    }

    fun owns(requester: Any) = owner === requester

    fun pauseCurrent() {
        val previous = pauseOwner
        owner = null; pauseOwner = null
        previous?.invoke()
    }
}

internal val wapiVoicePlayback = WapiAudioArbiter()
