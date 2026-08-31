package com.whappy.chat

import org.junit.Assert.*
import org.junit.Test

class WapiAudioArbiterTest {
    @Test fun recordingStopsActiveOrPreparingNoteAndClearsOwnership() {
        val arbiter = WapiAudioArbiter(); val owner = Any(); var paused = 0
        arbiter.claim(owner) { paused++ }
        arbiter.pauseCurrent(); arbiter.pauseCurrent()
        assertEquals(1, paused); assertFalse(arbiter.owns(owner))
    }
    @Test fun aNewNotePausesThePreviousNoteEvenWhileItLoads() {
        val arbiter = WapiAudioArbiter(); val first = Any(); val second = Any()
        var firstWantsPlayback = true
        arbiter.claim(first) { firstWantsPlayback = false; arbiter.release(first) }
        arbiter.claim(second) {}
        assertFalse(firstWantsPlayback)
        assertTrue(arbiter.owns(second))
        assertFalse(arbiter.owns(first))
    }
    @Test fun disposingAnOldNoteCannotStopTheNewOne() {
        val arbiter = WapiAudioArbiter(); val first = Any(); val second = Any()
        arbiter.claim(first) {}; arbiter.claim(second) {}; arbiter.release(first)
        assertTrue(arbiter.owns(second))
    }
    @Test fun sameOwnerDoesNotPauseItselfAndReleasedOwnerIsNotRetained() {
        val arbiter = WapiAudioArbiter(); val first = Any(); var pauses = 0
        arbiter.claim(first) { pauses++ }; arbiter.claim(first) { pauses++ }
        assertEquals(0, pauses)
        arbiter.release(first); arbiter.claim(Any()) {}
        assertEquals(0, pauses)
        assertFalse(arbiter.owns(first))
    }
}
