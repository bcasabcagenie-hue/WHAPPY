package com.whappy.chat

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/** Stable camera framing and table coordinates, independent of shot velocity. */
internal object WapiPoolPresentation {
    const val pitchDegrees = 68f
    const val fieldOfViewDegrees = 33f
    const val pocketX = 4.539f
    const val pocketZ = 2.193f
    // Visible aperture remains aligned with the existing pocket sensor; only
    // the thin flush leather surround sits outside it.
    // Wider leather mouths and a recessed throat are easier to read on a
    // phone display while still using the same radius for rendering and
    // physics. This prevents a ball from appearing to vanish through a flat
    // painted circle at the rail.
    const val pocketOpeningRadius = .345f
    const val pocketLeatherRadius = .374f
    val pockets = listOf(
        -pocketX to -pocketZ, 0f to -pocketZ, pocketX to -pocketZ,
        -pocketX to pocketZ, 0f to pocketZ, pocketX to pocketZ,
    )

    fun cameraDistance(aspect: Float): Float {
        val pitch = Math.toRadians(pitchDegrees.toDouble())
        val tangent = tan(Math.toRadians(fieldOfViewDegrees / 2.0)).toFloat()
        val safeAspect = if (aspect.isFinite()) aspect.coerceAtLeast(.4f) else 1f
        var distance = 0f
        // Fit the entire wooden frame, not only the felt. No shot-driven zoom.
        for (x in listOf(-5.4f, 5.4f)) for (z in listOf(-2.9f, 2.9f)) {
            for (y in listOf(-.55f, .50f)) {
                val depth = (y * sin(pitch) + z * cos(pitch)).toFloat()
                val vertical = (y * cos(pitch) - z * sin(pitch)).toFloat()
                distance = max(distance, max(abs(x) / (tangent * safeAspect), abs(vertical) / tangent) + depth)
            }
        }
        return distance * 1.04f
    }

    fun aimAngle(dx: Float, dy: Float): Float = kotlin.math.atan2(dy * WAPI_POOL_WORLD_HEIGHT, dx * WAPI_POOL_WORLD_WIDTH)

    fun strategyCameraDistance(aspect: Float, pitchDegrees: Float, yawDegrees: Float): Float {
        val pitch = Math.toRadians(pitchDegrees.toDouble()); val yaw = Math.toRadians(yawDegrees.toDouble())
        val tangent = tan(Math.toRadians(19.0))
        val ratio = if (aspect.isFinite()) aspect.coerceAtLeast(.35f) else 1f
        var distance = 0.0
        for (x in listOf(-4.72, 4.72)) for (z in listOf(-4.72, 4.72)) for (y in listOf(-.5, 1.7)) {
            val horizontal = x * cos(yaw) - z * sin(yaw)
            val vertical = -x * sin(yaw) * sin(pitch) + y * cos(pitch) - z * cos(yaw) * sin(pitch)
            val depth = x * sin(yaw) * cos(pitch) + y * sin(pitch) + z * cos(yaw) * cos(pitch)
            distance = max(distance, max(abs(horizontal) / (tangent * ratio), abs(vertical) / tangent) + depth)
        }
        return (distance * 1.035).toFloat()
    }
}
