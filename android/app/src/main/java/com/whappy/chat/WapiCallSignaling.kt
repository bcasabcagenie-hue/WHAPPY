package com.whappy.chat

/** Pure signaling decisions kept outside Android/WebRTC for regression tests. */
internal object WapiCallSignaling {
    enum class TransportFailure {
        /** This device did not gather any usable ICE route. */
        LOCAL_ICE_UNAVAILABLE,
        /** The other device never published a route through signaling. */
        REMOTE_ICE_UNAVAILABLE,
        /** Both devices answered but their NATs require a relay. */
        DIRECT_PATH_BLOCKED,
        /** A configured relay/direct path did not complete. */
        RELAY_OR_NETWORK_FAILED,
    }

    fun shouldApplyRemoteAnswer(
        outgoingRole: Boolean,
        status: String,
        answerApplied: Boolean,
        peerReady: Boolean,
    ): Boolean = outgoingRole && status == "accepted" && !answerApplied && peerReady

    /**
     * ICE may start gathering before the caller's Firestore document exists.
     * Keeping those candidates locally avoids writing them to a stale call
     * document when the user retries an audio call.
     */
    fun shouldQueueLocalCandidate(callDocumentReady: Boolean, callId: String): Boolean =
        !callDocumentReady || callId.isBlank()

    /** A callback emitted by a disposed PeerConnection belongs to no new call. */
    fun isCurrentGeneration(candidateGeneration: Long, activeGeneration: Long): Boolean =
        candidateGeneration == activeGeneration

    /**
     * Converts the facts observed by WebRTC into a recoverable, user-facing
     * failure category. This prevents the audio screen from staying forever on
     * "Mise en relation" without explaining whether the local device, the
     * other device, or a NAT relay is missing.
     */
    fun classifyTransportFailure(
        turnConfigured: Boolean,
        localCandidateCount: Int,
        remoteCandidateCount: Int,
    ): TransportFailure = when {
        localCandidateCount == 0 -> TransportFailure.LOCAL_ICE_UNAVAILABLE
        remoteCandidateCount == 0 -> TransportFailure.REMOTE_ICE_UNAVAILABLE
        !turnConfigured -> TransportFailure.DIRECT_PATH_BLOCKED
        else -> TransportFailure.RELAY_OR_NETWORK_FAILED
    }

    /** A queued ICE event must not replace an already connected call state. */
    fun shouldShowCheckingStatus(callVisible: Boolean, currentStatus: String): Boolean =
        callVisible && currentStatus != "Connecté"
}
