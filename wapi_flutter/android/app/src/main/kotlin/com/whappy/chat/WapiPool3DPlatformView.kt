package com.whappy.chat

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.View
import java.util.ArrayDeque
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

/**
 * Flutter host for the WAPI Pool OpenGL table.  Match state remains owned by
 * Firebase Functions; this view is deliberately a renderer and input surface,
 * never a second game authority on the phone.
 */
internal class WapiPool3DViewFactory(
    private val messenger: BinaryMessenger,
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView =
        WapiPool3DPlatformView(context, messenger, viewId, args as? Map<*, *>)
}

private data class PoolRenderState(
    val balls: List<FloatArray> = emptyList(),
    val aimAngle: Float = 0f,
    val power: Int = 48,
    val sideSpin: Float = 0f,
    val followSpin: Float = 0f,
    val aimBonus: Int = 0,
    val moving: Boolean = false,
    val cueStroke: Float = 0f,
    val cueInHand: Boolean = false,
    val tableTheme: String = "competitionBlue",
    val cueStyle: String = "maple",
)

private class WapiPool3DPlatformView(
    context: Context,
    messenger: BinaryMessenger,
    viewId: Int,
    initial: Map<*, *>?,
) : PlatformView, MethodChannel.MethodCallHandler {
    private val table = WapiTabletop3DView(context)
    private val effects = WapiPoolSoundEffects(context)
    private val channel = MethodChannel(messenger, "wapi/pool-3d/$viewId")
    private var state = PoolRenderState()

    init {
        channel.setMethodCallHandler(this)
        table.onResume()
        table.onPoolGesture = { x, y, released ->
            channel.invokeMethod(
                "gesture",
                mapOf("x" to x.toDouble(), "y" to y.toDouble(), "released" to released),
            )
        }
        table.onPoolPullShot = { startX, startY, endX, endY, power ->
            channel.invokeMethod(
                "cuePull",
                mapOf(
                    "startX" to startX.toDouble(), "startY" to startY.toDouble(),
                    "endX" to endX.toDouble(), "endY" to endY.toDouble(),
                    "power" to power,
                ),
            )
        }
        render(parseState(initial, state))
    }

    override fun getView(): View = table

    override fun dispose() {
        table.onPause()
        table.onPoolGesture = null
        table.onPoolPullShot = null
        channel.setMethodCallHandler(null)
        effects.release()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "render" -> {
                render(parseState(call.arguments as? Map<*, *>, state))
                result.success(null)
            }
            "stroke" -> {
                // A cue never teleports from its backswing to the ball.  The
                // short acceleration ramp keeps the release visible while
                // remaining comfortably below the player's perception of
                // input lag.
                render(state.copy(cueStroke = .18f))
                table.postDelayed({ render(state.copy(cueStroke = .52f)) }, 28L)
                table.postDelayed({ render(state.copy(cueStroke = .84f)) }, 56L)
                table.postDelayed({ render(state.copy(cueStroke = 1f)) }, 82L)
                table.postDelayed({ render(state.copy(cueStroke = 0f)) }, 148L)
                result.success(null)
            }
            "sound" -> {
                val data = call.arguments as? Map<*, *> ?: emptyMap<Any, Any>()
                effects.play(
                    kind = data["kind"] as? String ?: "collision",
                    intensity = (data["intensity"] as? Number)?.toFloat() ?: .6f,
                )
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private fun render(next: PoolRenderState) {
        state = next
        table.setPoolScene(
            balls = next.balls,
            aimAngle = next.aimAngle,
            power = next.power,
            sideSpin = next.sideSpin,
            followSpin = next.followSpin,
            aimBonus = next.aimBonus,
            moving = next.moving,
            cueStroke = next.cueStroke,
            cueInHand = next.cueInHand,
            tableTheme = next.tableTheme,
            cueStyle = next.cueStyle,
        )
    }

    private fun parseState(raw: Map<*, *>?, previous: PoolRenderState): PoolRenderState {
        val data = raw ?: return previous
        val rawBalls = data["balls"] as? List<*>
        val balls = rawBalls?.mapNotNull { item ->
            val ball = item as? Map<*, *> ?: return@mapNotNull null
            val id = (ball["id"] as? Number)?.toFloat() ?: return@mapNotNull null
            val x = (ball["x"] as? Number)?.toFloat() ?: return@mapNotNull null
            val y = (ball["y"] as? Number)?.toFloat() ?: return@mapNotNull null
            val vx = (ball["vx"] as? Number)?.toFloat() ?: 0f
            val vy = (ball["vy"] as? Number)?.toFloat() ?: 0f
            val pocketed = if (ball["pocketed"] == true) 1f else 0f
            floatArrayOf(x, y, id, vx, vy, pocketed)
        } ?: previous.balls
        fun number(key: String, fallback: Float): Float = (data[key] as? Number)?.toFloat() ?: fallback
        fun text(key: String, fallback: String): String = data[key] as? String ?: fallback
        return PoolRenderState(
            balls = balls,
            aimAngle = number("aimAngle", previous.aimAngle),
            power = number("power", previous.power.toFloat()).toInt().coerceIn(10, 100),
            sideSpin = number("sideSpin", previous.sideSpin).coerceIn(-1f, 1f),
            followSpin = number("followSpin", previous.followSpin).coerceIn(-1f, 1f),
            aimBonus = number("aimBonus", previous.aimBonus.toFloat()).toInt().coerceIn(0, 4),
            moving = data["moving"] as? Boolean ?: previous.moving,
            cueStroke = number("cueStroke", previous.cueStroke).coerceIn(0f, 1f),
            cueInHand = data["cueInHand"] as? Boolean ?: previous.cueInHand,
            tableTheme = text("tableTheme", previous.tableTheme),
            cueStyle = text("cueStyle", previous.cueStyle),
        )
    }
}

/** Low-latency Android mixer for cue, ball, cushion and pocket effects. */
private class WapiPoolSoundEffects(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(7)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ready = mutableSetOf<Int>()
    private val pending = ArrayDeque<Pair<String, Float>>()
    private val cue: Int
    private val collision: Int
    private val cushion: Int
    private val pocket: Int

    init {
        // The listener must exist before load().  Fast devices can otherwise
        // finish decoding every WAV first, leaving `ready` empty and the
        // whole match permanently silent.
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status != 0) return@setOnLoadCompleteListener
            ready += sampleId
            val queued = pending.toList()
            pending.clear()
            queued.forEach { (kind, intensity) -> play(kind, intensity) }
        }
        cue = pool.load(context, R.raw.wapi_pool_cue, 1)
        collision = pool.load(context, R.raw.wapi_pool_collision, 1)
        cushion = pool.load(context, R.raw.wapi_pool_cushion, 1)
        pocket = pool.load(context, R.raw.wapi_pool_pocket, 1)
    }

    fun play(kind: String, intensity: Float) {
        val sample = when (kind) {
            "cue" -> cue
            "rail" -> cushion
            "pocket" -> pocket
            else -> collision
        }
        if (sample !in ready) {
            // Preserve the first strike even when a player enters the table
            // and breaks immediately after the OpenGL view is created.
            if (pending.size < 12) pending.addLast(kind to intensity)
            return
        }
        val force = intensity.coerceIn(.12f, 1f)
        val volume = when (kind) {
            "pocket" -> .90f
            "cue" -> .34f + force * .55f
            else -> .24f + force * .66f
        }
        val rate = when (kind) {
            "collision" -> .94f + force * .10f
            "rail" -> .92f + force * .08f
            else -> 1f
        }
        pool.play(sample, volume, volume, 1, 0, rate)
    }

    fun release() = pool.release()
}
