package com.whappy.chat

import android.content.Context
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object WhappySounds {
    private var lastKeyAt = 0L
    private var lastUiTapAt = 0L
    private var keyboardTone: ToneGenerator? = null
    private val gameSamples = mutableMapOf<Int, Int>()
    private val readySamples = mutableSetOf<Int>()
    private var gameSoundPool: SoundPool? = null

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
    /** Confirme que le microphone est réellement ouvert. */
    fun voiceRecordingStarted() = tone(ToneGenerator.TONE_PROP_PROMPT, 96, 46)
    /** Différent du son de départ : l'enregistrement est arrêté, pas encore envoyé. */
    fun voiceRecordingStopped() = tone(ToneGenerator.TONE_PROP_ACK, 118, 48)
    fun voiceRecordingCancelled() = tone(ToneGenerator.TONE_PROP_NACK, 74, 30)
    fun dice() = tone(ToneGenerator.TONE_DTMF_6, 90, 34)
    fun gameOpen() = tone(ToneGenerator.TONE_PROP_PROMPT, 135, 44)
    fun move() = tone(ToneGenerator.TONE_PROP_BEEP, 55, 24)
    fun billiardCue() = tone(ToneGenerator.TONE_DTMF_4, 78, 46)
    fun billiardPocket() = tone(ToneGenerator.TONE_PROP_ACK, 145, 52)
    fun cardFlip() = tone(ToneGenerator.TONE_DTMF_2, 70, 30)
    fun pokerAction() = tone(ToneGenerator.TONE_PROP_PROMPT, 100, 34)
    fun reward() = tone(ToneGenerator.TONE_PROP_ACK, 180, 48)
    fun impact() = tone(ToneGenerator.TONE_PROP_NACK, 160, 38)

    /** Retour très court pour les cartes et lignes WAPI. Il suit le volume
     * multimédia, se coupe avec celui-ci et reste limité pour les doubles taps. */
    fun uiTap(context: Context) {
        if (!WhappyFastStorage.preferences(context, "whappy_consumer").getBoolean("interface_sounds", true)) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastUiTapAt < 42L) return
        lastUiTapAt = now
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0) return
        runCatching { audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, .16f) }
    }

    @Synchronized
    fun preloadGames(context: Context) {
        if (gameSoundPool != null) return
        val pool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(this) { readySamples += sampleId }
        }
        gameSoundPool = pool
        listOf(
            R.raw.wapi_dice_roll,
            R.raw.wapi_piece_select,
            R.raw.wapi_piece_move,
            R.raw.wapi_piece_capture,
            R.raw.wapi_piece_crown,
            R.raw.wapi_pool_hit,
            R.raw.wapi_pool_cue,
            R.raw.wapi_pool_collision,
            R.raw.wapi_pool_cushion,
            R.raw.wapi_pool_pocket,
            R.raw.wapi_card_flip,
            R.raw.wapi_victory,
        ).forEach { resource -> gameSamples[resource] = pool.load(context.applicationContext, resource, 1) }
    }

    private fun sample(context: Context, resourceId: Int, volume: Float = .78f, rate: Float = 1f) {
        preloadGames(context)
        val pool = synchronized(this) { gameSoundPool }
        val sampleId = synchronized(this) { gameSamples[resourceId] }
        val ready = sampleId != null && synchronized(this) { sampleId in readySamples }
        if (pool != null && sampleId != null && ready) {
            pool.play(sampleId, volume, volume, 1, 0, rate.coerceIn(.5f, 2f))
            return
        }
        // First-use fallback while SoundPool is still decoding the WAV.
        runCatching {
            MediaPlayer.create(context.applicationContext, resourceId)?.apply {
                setVolume(volume, volume)
                setOnCompletionListener { player -> player.release() }
                setOnErrorListener { player, _, _ -> player.release(); true }
                start()
            }
        }
    }

    fun dice(context: Context) = sample(context, R.raw.wapi_dice_roll, .72f)
    fun pieceSelected(context: Context) = sample(context, R.raw.wapi_piece_select, .62f)
    fun move(context: Context) = sample(context, R.raw.wapi_piece_move, .64f)
    fun capture(context: Context) = sample(context, R.raw.wapi_piece_capture, .80f)
    fun crowned(context: Context) = sample(context, R.raw.wapi_piece_crown, .82f)
    fun billiardCue(context: Context, power: Int = 62) {
        val normalized = power.coerceIn(1, 100) / 100f
        // One cue contact. The break's later clicks come from real collisions,
        // not an extra sound scheduled whether or not a ball was hit.
        sample(context, R.raw.wapi_pool_cue, .30f + normalized * .60f, .96f + normalized * .08f)
    }
    fun billiardCollision(context: Context, intensity: Float = .45f) =
        sample(context, R.raw.wapi_pool_collision, intensity.coerceIn(.10f, .78f), .96f + intensity * .12f)
    fun billiardRail(context: Context, intensity: Float = .38f) =
        sample(context, R.raw.wapi_pool_cushion, intensity.coerceIn(.10f, .60f), .98f)
    fun billiardPocket(context: Context) {
        sample(context, R.raw.wapi_pool_pocket, .84f, .92f)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            { sample(context, R.raw.wapi_pool_hit, .23f, .64f) },
            58L,
        )
    }
    fun cardFlip(context: Context) = sample(context, R.raw.wapi_card_flip, .68f)
    fun reward(context: Context) = sample(context, R.raw.wapi_victory, .86f)

    /** Retour commun aux jeux de plateau : grave, bref et distinct d'une
     * collision de billard. Il évite les anciens bips génériques. */
    fun gameInvalid(context: Context) {
        sample(context, R.raw.wapi_piece_capture, .60f, .66f)
        haptic(context, strong = true)
    }

    /** Signatures King QI distinctes : la personne comprend le verdict même
     * sans regarder l'écran, contrairement au bip générique de l'interface. */
    fun quizCorrect(context: Context) {
        sample(context, R.raw.wapi_victory, .88f, 1.14f)
        haptic(context, strong = true)
    }

    fun quizWrong(context: Context) {
        sample(context, R.raw.wapi_piece_capture, .76f, .72f)
        haptic(context, strong = true)
    }

    fun quizTick(context: Context, urgent: Boolean = false) =
        sample(context, R.raw.wapi_piece_select, if (urgent) .52f else .30f, if (urgent) 1.32f else 1.08f)

    /** Audible confirmation that a call has actually left the screen/session. */
    fun callEnded(context: Context) {
        if (!WhappyFastStorage.preferences(context, "whappy_consumer").getBoolean("call_end_sounds", true)) return
        tone(ToneGenerator.TONE_PROP_ACK, 105, 42)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            { tone(ToneGenerator.TONE_PROP_BEEP2, 95, 34) },
            115L,
        )
    }

    fun previewIncomingRingtone(context: Context) {
        val ringtone = runCatching {
            RingtoneManager.getRingtone(context.applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
        }.getOrNull() ?: return
        runCatching { ringtone.play() }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ runCatching { ringtone.stop() } }, 1_650L)
    }

    fun typing(context: Context) {
        if (!WhappyFastStorage.preferences(context, "whappy_consumer").getBoolean("typing_sounds", false)) return
        val now = android.os.SystemClock.elapsedRealtime()
        // A short native key click is less electronic and tiring than a DTMF
        // beep. It follows the media volume and remains throttled while typing.
        if (now - lastKeyAt < 82L) return
        lastKeyAt = now
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        // Use the media stream deliberately: the click follows the volume the
        // person controls on the device, even when Android system touch sounds
        // have been disabled.  It remains silent when media volume is zero.
        if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0) return
        // A low native key click is less tonal than the former game sample and
        // stays in the background even during fast typing.
        runCatching { audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, .055f) }
            .onFailure {
                val generator = keyboardTone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 8).also { keyboardTone = it }
                generator.startTone(ToneGenerator.TONE_PROP_BEEP, 10)
            }
    }

    fun haptic(context: Context, strong: Boolean = false) {
        if (!WhappyFastStorage.preferences(context, "whappy_consumer").getBoolean("haptic_feedback", true)) return
        val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createOneShot(if (strong) 55L else 18L, if (strong) 125 else 55))
        else @Suppress("DEPRECATION") vibrator?.vibrate(if (strong) 55L else 18L)
    }
}
