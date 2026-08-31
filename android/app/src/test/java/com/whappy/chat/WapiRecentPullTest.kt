package com.whappy.chat
import org.junit.Assert.*
import org.junit.Test

class WapiRecentPullTest {
    @Test fun opensOnceAtThresholdWithoutWaitingForRelease() {
        val pull = WapiRecentPull(72f)
        assertFalse(pull.move(40f, true)); assertEquals(40f / 72f, pull.progress, .001f); assertTrue(pull.move(32f, true))
        assertEquals(1f, pull.progress, .001f)
        assertFalse(pull.move(100f, true))
        pull.reset(); assertTrue(pull.move(80f, true))
    }
    @Test fun cancelledAndIneligibleGesturesDoNotAccumulate() {
        val pull = WapiRecentPull(72f)
        pull.move(50f, true); pull.move(-50f, true)
        assertFalse(pull.move(50f, true))
        assertFalse(pull.move(80f, false)); assertEquals(0f, pull.distance)
        assertFalse(pull.move(Float.NaN, true))
    }
}
