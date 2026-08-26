package com.whappy.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

@RunWith(AndroidJUnit4::class)
class WapiWebRtcRelayInstrumentedTest {
    @Test
    fun twoAndroidPeersConnectAndExchangeDataThroughTurnOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions(),
        )
        val factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        val expiresAt = System.currentTimeMillis() / 1_000 + 3_600
        val username = "$expiresAt:wapi-android-test"
        val secret = "wapi-native-smoke-test-only"
        val credential = Mac.getInstance("HmacSHA1").run {
            init(SecretKeySpec(secret.toByteArray(), "HmacSHA1"))
            android.util.Base64.encodeToString(doFinal(username.toByteArray()), android.util.Base64.NO_WRAP)
        }
        val server = PeerConnection.IceServer.builder("turn:10.0.2.2:34781?transport=udp")
            .setUsername(username)
            .setPassword(credential)
            .createIceServer()
        val configuration = PeerConnection.RTCConfiguration(listOf(server)).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.RELAY
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val connected = CountDownLatch(2)
        val received = CountDownLatch(1)
        val candidatesA = CopyOnWriteArrayList<IceCandidate>()
        val candidatesB = CopyOnWriteArrayList<IceCandidate>()
        val observerA = RelayObserver(connected, candidatesA, null)
        val observerB = RelayObserver(connected, candidatesB) { channel ->
            channel.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) = Unit
                override fun onStateChange() = Unit
                override fun onMessage(buffer: DataChannel.Buffer) {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    if (bytes.decodeToString() == "wapi-turn-ok") received.countDown()
                }
            })
        }
        val peerA = requireNotNull(factory.createPeerConnection(configuration, observerA))
        val peerB = requireNotNull(factory.createPeerConnection(configuration, observerB))
        val channelA = peerA.createDataChannel("wapi-call-test", DataChannel.Init())

        try {
            val offer = peerA.createOfferBlocking()
            peerA.setLocalBlocking(offer)
            peerB.setRemoteBlocking(offer)
            val answer = peerB.createAnswerBlocking()
            peerB.setLocalBlocking(answer)
            peerA.setRemoteBlocking(answer)
            observerA.remotePeer = peerB
            observerB.remotePeer = peerA
            candidatesA.forEach(peerB::addIceCandidate)
            candidatesB.forEach(peerA::addIceCandidate)

            assertTrue("Les deux pairs Android ne se sont pas connectés via TURN", connected.await(20, TimeUnit.SECONDS))
            repeat(20) {
                if (channelA.state() == DataChannel.State.OPEN) return@repeat
                Thread.sleep(100)
            }
            assertTrue("Le canal WebRTC TURN n'est pas ouvert", channelA.state() == DataChannel.State.OPEN)
            assertTrue(channelA.send(DataChannel.Buffer(ByteBuffer.wrap("wapi-turn-ok".toByteArray()), false)))
            assertTrue("Le paquet WebRTC n'a pas traversé TURN", received.await(5, TimeUnit.SECONDS))
        } finally {
            channelA.close()
            channelA.dispose()
            peerA.close()
            peerA.dispose()
            peerB.close()
            peerB.dispose()
            factory.dispose()
        }
    }
}

private class RelayObserver(
    private val connected: CountDownLatch,
    private val candidates: MutableList<IceCandidate>,
    private val onRemoteDataChannel: ((DataChannel) -> Unit)?,
) : PeerConnection.Observer {
    @Volatile var remotePeer: PeerConnection? = null

    override fun onIceCandidate(candidate: IceCandidate) {
        val remote = remotePeer
        if (remote == null) candidates += candidate else remote.addIceCandidate(candidate)
    }
    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
        if (newState == PeerConnection.PeerConnectionState.CONNECTED) connected.countDown()
    }
    override fun onDataChannel(channel: DataChannel) { onRemoteDataChannel?.invoke(channel) }
    override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
    override fun onAddStream(stream: MediaStream) = Unit
    override fun onRemoveStream(stream: MediaStream) = Unit
    override fun onRenegotiationNeeded() = Unit
    override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) = Unit
}

private fun PeerConnection.createOfferBlocking(): SessionDescription = awaitSdp { observer ->
    createOffer(observer, MediaConstraints())
}

private fun PeerConnection.createAnswerBlocking(): SessionDescription = awaitSdp { observer ->
    createAnswer(observer, MediaConstraints())
}

private fun PeerConnection.setLocalBlocking(description: SessionDescription) = awaitSet { observer ->
    setLocalDescription(observer, description)
}

private fun PeerConnection.setRemoteBlocking(description: SessionDescription) = awaitSet { observer ->
    setRemoteDescription(observer, description)
}

private fun awaitSdp(action: (SdpObserver) -> Unit): SessionDescription {
    val latch = CountDownLatch(1)
    var value: SessionDescription? = null
    var failure: String? = null
    action(object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) { value = description; latch.countDown() }
        override fun onCreateFailure(error: String) { failure = error; latch.countDown() }
        override fun onSetSuccess() = Unit
        override fun onSetFailure(error: String) = Unit
    })
    assertTrue("SDP timeout", latch.await(10, TimeUnit.SECONDS))
    check(failure == null) { failure.orEmpty() }
    return requireNotNull(value)
}

private fun awaitSet(action: (SdpObserver) -> Unit) {
    val latch = CountDownLatch(1)
    var failure: String? = null
    action(object : SdpObserver {
        override fun onSetSuccess() { latch.countDown() }
        override fun onSetFailure(error: String) { failure = error; latch.countDown() }
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onCreateFailure(error: String) = Unit
    })
    assertTrue("Set SDP timeout", latch.await(10, TimeUnit.SECONDS))
    check(failure == null) { failure.orEmpty() }
}
