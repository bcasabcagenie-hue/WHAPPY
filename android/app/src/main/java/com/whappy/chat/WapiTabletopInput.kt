package com.whappy.chat

import kotlin.math.hypot

/** A pan that returns to its origin is still a pan, never a move selection. */
internal class WapiTabletopInput(private val slop: Float) {
    private var startX = 0f
    private var startY = 0f
    private var active = false
    var wasDragged = false; private set
    var cancelled = false; private set
    fun begin(x: Float, y: Float) { startX = x; startY = y; active = true; wasDragged = false; cancelled = false }
    fun move(x: Float, y: Float) { if (hypot(x - startX, y - startY) > slop) wasDragged = true }
    fun cancel() { cancelled = true }
    fun finish(x: Float, y: Float): Boolean {
        move(x, y)
        val tapped = active && !wasDragged && !cancelled
        active = false
        return tapped
    }
}

/** Keep the raw displacement: overshooting then returning must cancel the shot. */
internal class WapiPoolStroke(private val travel: Float) {
    private var displacement = 0f
    private var cancelled = false
    private var finished = false
    val fraction: Float get() = if (travel.isFinite() && travel > 0 && displacement.isFinite()) (displacement / travel).coerceIn(0f, 1f) else 0f
    val power: Int get() = (fraction * 100).toInt().coerceIn(10, 100)
    fun move(delta: Float, insideRail: Boolean = true) {
        if (finished) return
        if (!insideRail || !delta.isFinite()) cancelled = true
        displacement += delta
    }
    fun finish(): Int? {
        if (finished) return null
        finished = true
        return power.takeIf { !cancelled && fraction >= .04f }
    }
}
