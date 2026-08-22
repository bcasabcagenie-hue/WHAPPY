package com.whappy.chat

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.BassBoost
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.Equalizer
import android.media.audiofx.NoiseSuppressor
import java.io.File

enum class WapiMicProfile(val label: String, val detail: String, val audioSource: Int) {
    CLEAN("Clair", "Voix naturelle et précise", MediaRecorder.AudioSource.VOICE_RECOGNITION),
    WARM("Chaleureux", "Graves doux, présence intime", MediaRecorder.AudioSource.VOICE_COMMUNICATION),
    BROADCAST("Broadcast", "Présence radio et niveau constant", MediaRecorder.AudioSource.VOICE_COMMUNICATION),
    DEEP("Profond", "Voix dense pour interviews", MediaRecorder.AudioSource.VOICE_COMMUNICATION),
}

data class WapiRadioRecording(val recorder: MediaRecorder, val file: File)
data class WapiRadioEffectSupport(val noiseReduction: Boolean, val automaticGain: Boolean, val echoCancellation: Boolean)

fun detectRadioEffectSupport() = WapiRadioEffectSupport(
    noiseReduction = NoiseSuppressor.isAvailable(),
    automaticGain = AutomaticGainControl.isAvailable(),
    echoCancellation = AcousticEchoCanceler.isAvailable(),
)

@Suppress("DEPRECATION")
fun createRadioRecorder(context: Context, profile: WapiMicProfile): WapiRadioRecording {
    val file = File(WapiMediaStore.cacheDirectory(context), "wapi-radio-${System.currentTimeMillis()}.m4a")
    val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
    recorder.setAudioSource(profile.audioSource)
    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
    recorder.setAudioChannels(1)
    recorder.setAudioEncodingBitRate(192_000)
    recorder.setAudioSamplingRate(48_000)
    recorder.setMaxDuration(3_600_000)
    recorder.setOutputFile(file.absolutePath)
    recorder.prepare()
    recorder.start()
    return WapiRadioRecording(recorder, file)
}

class WapiRadioPlayback {
    private var player: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    val playing: Boolean get() = player?.isPlaying == true

    fun play(file: File, profile: WapiMicProfile, onFinished: () -> Unit) {
        release()
        val active = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
        }
        startPrepared(active, profile, onFinished)
    }

    fun playUrl(url: String, profile: WapiMicProfile, onReady: () -> Unit = {}, onFinished: () -> Unit) {
        release()
        val active = MediaPlayer()
        player = active
        active.setDataSource(url)
        active.setOnPreparedListener {
            startPrepared(active, profile, onFinished)
            onReady()
        }
        active.setOnErrorListener { _, _, _ -> release(); onFinished(); true }
        active.prepareAsync()
    }

    private fun startPrepared(active: MediaPlayer, profile: WapiMicProfile, onFinished: () -> Unit) {
        player = active
        runCatching {
            equalizer = Equalizer(0, active.audioSessionId).apply {
                enabled = true
                val range = bandLevelRange
                for (band in 0 until numberOfBands) {
                    val centerHz = getCenterFreq(band.toShort()) / 1_000
                    val requested = when (profile) {
                        WapiMicProfile.CLEAN -> 0
                        WapiMicProfile.WARM -> if (centerHz < 500) 420 else if (centerHz > 5_000) -120 else 120
                        WapiMicProfile.BROADCAST -> if (centerHz in 700..4_000) 380 else 90
                        WapiMicProfile.DEEP -> if (centerHz < 700) 620 else if (centerHz > 6_000) -180 else 80
                    }.coerceIn(range[0].toInt(), range[1].toInt())
                    setBandLevel(band.toShort(), requested.toShort())
                }
            }
            bassBoost = BassBoost(0, active.audioSessionId).apply {
                enabled = profile != WapiMicProfile.CLEAN
                setStrength(when (profile) { WapiMicProfile.DEEP -> 780; WapiMicProfile.WARM -> 420; WapiMicProfile.BROADCAST -> 220; else -> 0 }.toShort())
            }
        }
        active.setOnCompletionListener { release(); onFinished() }
        active.start()
    }

    fun stop() = release()

    fun release() {
        runCatching { equalizer?.release() }; equalizer = null
        runCatching { bassBoost?.release() }; bassBoost = null
        runCatching { player?.stop() }
        runCatching { player?.release() }; player = null
    }
}
