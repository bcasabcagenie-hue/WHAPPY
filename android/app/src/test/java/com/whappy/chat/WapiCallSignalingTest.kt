package com.whappy.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WapiCallSignalingTest {
    @Test fun callerAppliesTheRemoteAnswerOnce() {
        assertTrue(WapiCallSignaling.shouldApplyRemoteAnswer(true, "accepted", false, true))
        assertFalse(WapiCallSignaling.shouldApplyRemoteAnswer(true, "accepted", true, true))
    }

    @Test fun calleeNeverAppliesItsOwnAnswerAsRemote() {
        assertFalse(WapiCallSignaling.shouldApplyRemoteAnswer(false, "accepted", false, true))
    }

    @Test fun answerWaitsForThePeerConnection() {
        assertFalse(WapiCallSignaling.shouldApplyRemoteAnswer(true, "accepted", false, false))
        assertFalse(WapiCallSignaling.shouldApplyRemoteAnswer(true, "ringing", false, true))
    }

    @Test fun earlyIceCandidatesWaitForTheNewCallDocument() {
        assertTrue(WapiCallSignaling.shouldQueueLocalCandidate(false, ""))
        assertTrue(WapiCallSignaling.shouldQueueLocalCandidate(false, "old-call"))
        assertFalse(WapiCallSignaling.shouldQueueLocalCandidate(true, "new-call"))
    }

    @Test fun stalePeerCallbacksCannotPolluteANewCall() {
        assertFalse(WapiCallSignaling.isCurrentGeneration(4L, 5L))
        assertTrue(WapiCallSignaling.isCurrentGeneration(5L, 5L))
    }

    @Test fun transportFailureExplainsWhichCallPathIsMissing() {
        assertEquals(
            WapiCallSignaling.TransportFailure.LOCAL_ICE_UNAVAILABLE,
            WapiCallSignaling.classifyTransportFailure(false, 0, 4),
        )
        assertEquals(
            WapiCallSignaling.TransportFailure.REMOTE_ICE_UNAVAILABLE,
            WapiCallSignaling.classifyTransportFailure(true, 2, 0),
        )
        assertEquals(
            WapiCallSignaling.TransportFailure.DIRECT_PATH_BLOCKED,
            WapiCallSignaling.classifyTransportFailure(false, 2, 3),
        )
        assertEquals(
            WapiCallSignaling.TransportFailure.RELAY_OR_NETWORK_FAILED,
            WapiCallSignaling.classifyTransportFailure(true, 2, 3),
        )
    }

    @Test fun aLateCheckingEventCannotHideAnEstablishedAudioCall() {
        assertFalse(WapiCallSignaling.shouldShowCheckingStatus(true, "Connecté"))
        assertFalse(WapiCallSignaling.shouldShowCheckingStatus(false, "Connexion du média…"))
        assertTrue(WapiCallSignaling.shouldShowCheckingStatus(true, "Connexion du média…"))
    }
}
