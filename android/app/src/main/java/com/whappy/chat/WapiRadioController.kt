package com.whappy.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A single player for the whole WAPI process.  Radio therefore survives a
 * navigation from Podcasts to Messages, where people can change station
 * without losing the current conversation.
 */
data class WapiRadioPlayerState(
    val episode: WapiRadioEpisode? = null,
    val playing: Boolean = false,
    val preparing: Boolean = false,
    val error: String? = null,
)

class WapiRadioController {
    private val playback = WapiRadioPlayback()

    var state = mutableStateOf(WapiRadioPlayerState())
        private set

    fun play(episode: WapiRadioEpisode, profile: WapiMicProfile = WapiMicProfile.BROADCAST) {
        if (episode.audioUrl.isBlank()) {
            state.value = state.value.copy(error = "Cet épisode ne possède pas encore de flux audio.")
            return
        }
        if (state.value.episode?.id == episode.id && state.value.playing) {
            pause()
            return
        }
        if (state.value.episode?.id == episode.id && !state.value.preparing) {
            playback.resume()
            state.value = state.value.copy(playing = true, error = null)
            return
        }
        state.value = WapiRadioPlayerState(episode = episode, preparing = true)
        runCatching {
            playback.playUrl(
                url = episode.audioUrl,
                profile = profile,
                onReady = { state.value = state.value.copy(playing = true, preparing = false, error = null) },
                onFinished = { state.value = state.value.copy(playing = false, preparing = false) },
            )
        }.onFailure {
            playback.stop()
            state.value = WapiRadioPlayerState(error = "La radio n’a pas pu démarrer. Vérifiez votre connexion.")
        }
    }

    fun pause() {
        playback.pause()
        state.value = state.value.copy(playing = false, preparing = false)
    }

    fun stop() {
        playback.stop()
        state.value = WapiRadioPlayerState()
    }

    fun release() = playback.release()
}

val LocalWapiRadio = staticCompositionLocalOf<WapiRadioController?> { null }
