package com.whappy.chat

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.View
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

/** Flutter bridge for the shared OpenGL chess, draughts and Ludo table. */
internal class WapiTabletop3DViewFactory(
    private val messenger: BinaryMessenger,
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView =
        WapiTabletop3DPlatformView(context, messenger, viewId, args as? Map<*, *>)
}

private class WapiTabletop3DPlatformView(
    context: Context,
    messenger: BinaryMessenger,
    viewId: Int,
    initial: Map<*, *>?,
) : PlatformView, MethodChannel.MethodCallHandler {
    private val table = WapiTabletop3DView(context)
    private val effects = WapiTabletopSoundEffects(context)
    private val channel = MethodChannel(messenger, "wapi/tabletop-3d/$viewId")
    private var state: Map<*, *> = initial ?: emptyMap<Any, Any>()

    init {
        channel.setMethodCallHandler(this)
        table.onResume()
        table.onSquareTapped = { channel.invokeMethod("square", it) }
        table.onLudoPawnTapped = { channel.invokeMethod("pawn", it) }
        render(state)
    }

    override fun getView(): View = table

    override fun dispose() {
        table.onPause()
        table.onSquareTapped = null
        table.onLudoPawnTapped = null
        channel.setMethodCallHandler(null)
        effects.release()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (call.method != "render") {
            result.notImplemented()
            return
        }
        state = call.arguments as? Map<*, *> ?: state
        render(state)
        result.success(null)
    }

    private fun render(data: Map<*, *>) {
        fun integers(key: String): List<Int> =
            (data[key] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
        val scene = data["scene"] as? String ?: "chess"
        if (scene == "ludo") {
            val positions = integers("positions")
            val rolling = data["rolling"] == true
            effects.observeLudo(positions, rolling)
            table.setLudoScene(
                positions = positions,
                activePlayer = (data["activePlayer"] as? Number)?.toInt() ?: 0,
                dieOne = (data["dieOne"] as? Number)?.toInt() ?: 1,
                dieTwo = (data["dieTwo"] as? Number)?.toInt() ?: 1,
                rolling = rolling,
                selectablePawns = integers("selectablePawns").toSet(),
            )
            return
        }
        val board = (data["board"] as? List<*>)?.map { it as? String ?: "" } ?: emptyList()
        effects.observeBoard(board)
        table.setStrategyScene(
            scene = if (scene == "draughts") WapiTabletop3DView.Scene.CHECKERS else WapiTabletop3DView.Scene.CHESS,
            board = board,
            selected = (data["selected"] as? Number)?.toInt() ?: -1,
            legalTargets = integers("legalTargets").toSet(),
        )
    }
}

/** Low-latency physical feedback for the other tabletop games. */
private class WapiTabletopSoundEffects(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ready = mutableSetOf<Int>()
    private val pending = mutableListOf<Int>()
    private val move: Int
    private val capture: Int
    private val dice: Int
    private var board: List<String>? = null
    private var positions: List<Int>? = null
    private var rolling = false

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status != 0) return@setOnLoadCompleteListener
            ready += sampleId
            if (sampleId in pending) {
                pending.removeAll { it == sampleId }
                play(sampleId)
            }
        }
        move = pool.load(context, R.raw.wapi_piece_move, 1)
        capture = pool.load(context, R.raw.wapi_piece_capture, 1)
        dice = pool.load(context, R.raw.wapi_dice_roll, 1)
    }

    fun observeBoard(next: List<String>) {
        val previous = board
        board = next.toList()
        if (previous == null || previous == next || previous.size != next.size) return
        val oldCount = previous.count { it.isNotBlank() }
        val newCount = next.count { it.isNotBlank() }
        play(if (newCount < oldCount) capture else move)
    }

    fun observeLudo(next: List<Int>, nextRolling: Boolean) {
        val previous = positions
        positions = next.toList()
        if (nextRolling && !rolling) play(dice, .86f)
        rolling = nextRolling
        if (previous != null && previous != next) play(move)
    }

    private fun play(sample: Int, volume: Float = .72f) {
        if (sample !in ready) {
            if (pending.size < 6) pending += sample
            return
        }
        pool.play(sample, volume, volume, 1, 0, 1f)
    }

    fun release() = pool.release()
}
