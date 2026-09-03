package com.whappy.chat

import android.content.Context
import android.view.View
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
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "render" -> {
                render(parseState(call.arguments as? Map<*, *>, state))
                result.success(null)
            }
            "stroke" -> {
                render(state.copy(cueStroke = 1f))
                table.postDelayed({ render(state.copy(cueStroke = 0f)) }, 118L)
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
            moving = data["moving"] as? Boolean ?: previous.moving,
            cueStroke = number("cueStroke", previous.cueStroke).coerceIn(0f, 1f),
            cueInHand = data["cueInHand"] as? Boolean ?: previous.cueInHand,
            tableTheme = text("tableTheme", previous.tableTheme),
            cueStyle = text("cueStyle", previous.cueStyle),
        )
    }
}
