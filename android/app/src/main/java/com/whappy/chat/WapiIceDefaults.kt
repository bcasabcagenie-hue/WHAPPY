package com.whappy.chat

import org.webrtc.PeerConnection

/**
 * One resilient ICE policy shared by private calls, group calls and lives.
 *
 * STUN keeps direct WebRTC free and peer-to-peer. TURN servers returned by the
 * authenticated WAPI callable are prepended when available so restrictive
 * carrier networks can relay media without changing any call UI or protocol.
 */
internal object WapiIceDefaults {
    const val CONFIGURATION_TIMEOUT_MS = 8_000L

    private val stunUrls = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302",
        "stun:stun2.l.google.com:19302",
        "stun:stun3.l.google.com:19302",
        "stun:stun4.l.google.com:19302",
    )

    fun fallbackServers(): List<PeerConnection.IceServer> = stunUrls.map { url ->
        PeerConnection.IceServer.builder(url).createIceServer()
    }

    fun merge(configured: List<PeerConnection.IceServer>): List<PeerConnection.IceServer> =
        (configured + fallbackServers()).distinctBy { server -> server.urls.joinToString("|") }

    fun rtcConfiguration(servers: List<PeerConnection.IceServer>) =
        PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize = 4
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            keyType = PeerConnection.KeyType.ECDSA
        }
}
