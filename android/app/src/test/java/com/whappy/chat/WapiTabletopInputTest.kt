package com.whappy.chat

import org.junit.Assert.*
import org.junit.Test

class WapiTabletopInputTest {
    @Test fun overshootThenReturnToOriginDoesNotFire() {
        val stroke = WapiPoolStroke(200f)
        stroke.move(300f); assertEquals(100, stroke.power)
        stroke.move(-300f); assertNull(stroke.finish())
    }
    @Test fun oppositeDragThenReturnDoesNotCreatePower() {
        val stroke = WapiPoolStroke(200f)
        stroke.move(-90f); stroke.move(90f); assertNull(stroke.finish())
    }
    @Test fun intentionalStrokeOnlyCommitsOnce() {
        val stroke = WapiPoolStroke(200f)
        stroke.move(100f); assertEquals(50, stroke.finish())
        assertNull(stroke.finish())
    }
    @Test fun movingOffTheRailCancelsEvenAfterReturning() {
        val stroke = WapiPoolStroke(200f)
        stroke.move(70f, insideRail = false); stroke.move(30f)
        assertNull(stroke.finish())
    }
    @Test fun TinyAndInvalidGesturesCannotShoot() {
        for (travel in listOf(0f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val stroke = WapiPoolStroke(travel); stroke.move(100f); assertNull(stroke.finish())
        }
        val stroke = WapiPoolStroke(200f); stroke.move(7f); assertNull(stroke.finish())
    }
    @Test fun deliberateTapSelectsOnlyOnce() {
        val input = WapiTabletopInput(12f)
        input.begin(10f, 10f)
        assertTrue(input.finish(12f, 11f))
        assertFalse(input.finish(12f, 11f))
    }
    @Test fun returningAfterAnOrbitDoesNotMoveAPiece() {
        val input = WapiTabletopInput(12f)
        input.begin(10f, 10f); input.move(110f, 80f)
        assertFalse(input.finish(10f, 10f))
    }
    @Test fun PinchOrCancelledGestureNeverSelects() {
        val input = WapiTabletopInput(12f)
        input.begin(10f, 10f); input.cancel()
        assertFalse(input.finish(10f, 10f))
        input.begin(10f, 10f)
        assertTrue(input.finish(10f, 10f))
    }
}
