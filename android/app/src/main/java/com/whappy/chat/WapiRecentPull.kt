package com.whappy.chat

/** Accumulates only unconsumed pull at the top of Messages. No partial page. */
internal class WapiRecentPull(private val threshold: Float) {
    var distance = 0f
        private set
    var opened = false
        private set

    /** Progress is capped so a fast swipe never causes a visual jump. */
    val progress: Float
        get() = if (threshold <= 0f) 0f else (distance / threshold).coerceIn(0f, 1f)

    fun move(delta: Float, eligible: Boolean): Boolean {
        if (!eligible || !delta.isFinite()) { reset(); return false }
        if (opened) return false
        distance = (distance + delta).coerceIn(0f, threshold)
        if (distance < threshold) return false
        opened = true
        return true
    }

    fun reset() { distance = 0f; opened = false }
}
