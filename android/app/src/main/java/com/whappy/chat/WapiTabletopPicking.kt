package com.whappy.chat

import kotlin.math.sqrt

internal data class WapiPickSphere(val index: Int, val x: Float, val y: Float, val z: Float, val radius: Float)

/** Picks the visible piece body before falling back to the square underneath.
 * A tall chess piece does not occupy the same screen pixels as its board tile. */
internal fun pickWapiPiece(near: FloatArray, far: FloatArray, shapes: List<WapiPickSphere>): Int? {
    val dx = far[0] - near[0]; val dy = far[1] - near[1]; val dz = far[2] - near[2]
    val a = dx * dx + dy * dy + dz * dz
    if (!a.isFinite() || a < .000001f) return null
    var closest = Float.POSITIVE_INFINITY
    var picked: Int? = null
    for (shape in shapes) {
        val ox = near[0] - shape.x; val oy = near[1] - shape.y; val oz = near[2] - shape.z
        val b = ox * dx + oy * dy + oz * dz
        val c = ox * ox + oy * oy + oz * oz - shape.radius * shape.radius
        val discriminant = b * b - a * c
        if (discriminant < 0f) continue
        val first = (-b - sqrt(discriminant)) / a
        val t = if (first >= 0f) first else (-b + sqrt(discriminant)) / a
        if (t in 0f..1f && t < closest) { closest = t; picked = shape.index }
    }
    return picked
}
