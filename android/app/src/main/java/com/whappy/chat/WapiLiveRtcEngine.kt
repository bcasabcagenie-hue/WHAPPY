package com.whappy.chat

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Diffusion WebRTC native WAPI. Firestore ne transporte que SDP/ICE ; la vidéo
 * reste chiffrée de pair à pair. Le mode P2P est volontairement limité à huit
 * spectateurs afin de protéger la batterie et le débit montant de l'animateur.
 */
internal class WapiLiveRtcEngine(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance("europe-west1")
    private val main = Handler(Looper.getMainLooper())
    private val egl = EglBase.create()
    private val factory: PeerConnectionFactory
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val peers = ConcurrentHashMap<String, PeerConnection>()
    private val peerListeners = ConcurrentHashMap<String, MutableList<ListenerRegistration>>()
    private val queuedRemoteIce = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private val remoteReady = ConcurrentHashMap<String, Boolean>()
    private val registrations = mutableListOf<ListenerRegistration>()
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var liveId = ""
    private var host = false
    private var audioOnly = false
    private var closed = false
    private var iceServers: List<PeerConnection.IceServer> = fallbackIceServers()

    var onStateChanged: ((String, Boolean, String?) -> Unit)? = null

    var status: String = "Préparation du direct…"
        private set
    var error: String? = null
        private set
    var connected: Boolean = false
        private set
    var microphoneEnabled: Boolean = true
        private set
    var cameraEnabled: Boolean = true
        private set

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions(),
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    suspend fun start(id: String, expectedHost: Boolean, isAudioOnly: Boolean) {
        liveId = id
        audioOnly = isAudioOnly
        val result = functions.getHttpsCallable("joinLiveSession").call(mapOf("liveId" to id)).await()
        @Suppress("UNCHECKED_CAST")
        val payload = result.data as? Map<String, Any?> ?: error("Réponse du direct WAPI invalide.")
        require(payload["streamProvider"] == "wapi-webrtc-p2p") { "Transport WebRTC WAPI indisponible." }
        host = payload["role"] == "host"
        require(host == expectedHost) { "Le rôle du direct a changé. Rouvrez le direct." }
        iceServers = loadIceServers()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        if (host) {
            createHostMedia()
            listenForViewers()
            functions.getHttpsCallable("setLiveSessionState").call(mapOf("liveId" to id, "action" to "start")).await()
            publishState("En direct", isConnected = true)
        } else {
            val viewerId = auth.currentUser?.uid ?: error("Connexion WAPI requise.")
            createViewerPeer(viewerId)
            functions.getHttpsCallable("setLivePresence").call(mapOf("liveId" to id, "action" to "connected")).await()
            publishState("Connexion au direct…", isConnected = false)
        }
    }

    fun attachRenderer(renderer: SurfaceViewRenderer, local: Boolean) {
        renderer.init(egl.eglBaseContext, null)
        renderer.setEnableHardwareScaler(true)
        renderer.setMirror(local)
        if (local) {
            localRenderer = renderer
            videoTrack?.addSink(renderer)
        } else {
            remoteRenderer = renderer
            remoteVideoTrack?.addSink(renderer)
        }
    }

    fun detachRenderer(renderer: SurfaceViewRenderer, local: Boolean) {
        if (local) {
            videoTrack?.removeSink(renderer)
            if (localRenderer === renderer) localRenderer = null
        } else {
            remoteVideoTrack?.removeSink(renderer)
            if (remoteRenderer === renderer) remoteRenderer = null
        }
        runCatching { renderer.release() }
    }

    fun setMicrophone(enabled: Boolean): Boolean {
        microphoneEnabled = enabled
        audioTrack?.setEnabled(enabled)
        return microphoneEnabled
    }

    fun setCamera(enabled: Boolean): Boolean {
        if (audioOnly) return false
        cameraEnabled = enabled
        videoTrack?.setEnabled(enabled)
        return cameraEnabled
    }

    fun switchCamera() = capturer?.switchCamera(null)

    fun close() {
        if (closed) return
        closed = true
        registrations.forEach { it.remove() }
        registrations.clear()
        peerListeners.values.flatten().forEach { it.remove() }
        peerListeners.clear()
        peers.values.forEach { it.close(); it.dispose() }
        peers.clear()
        remoteVideoTrack?.let { track -> remoteRenderer?.let(track::removeSink) }
        videoTrack?.let { track -> localRenderer?.let(track::removeSink) }
        runCatching { capturer?.stopCapture() }
        capturer?.dispose()
        textureHelper?.dispose()
        videoSource?.dispose()
        audioSource?.dispose()
        factory.dispose()
        egl.release()
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    private fun createHostMedia() {
        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("wapi-live-audio", audioSource).also { it.setEnabled(true) }
        microphoneEnabled = true
        cameraEnabled = !audioOnly
        if (audioOnly) return
        capturer = createCameraCapturer() ?: error("Aucune caméra compatible n’a été trouvée.")
        videoSource = factory.createVideoSource(false)
        textureHelper = SurfaceTextureHelper.create("WapiLiveCamera", egl.eglBaseContext)
        capturer!!.initialize(textureHelper, appContext, videoSource!!.capturerObserver)
        capturer!!.startCapture(1280, 720, 30)
        videoTrack = factory.createVideoTrack("wapi-live-video", videoSource).also { track ->
            track.setEnabled(true)
            localRenderer?.let(track::addSink)
        }
    }

    private fun listenForViewers() {
        val peersRef = db.collection("liveSessions").document(liveId).collection("peers")
        registrations += peersRef.addSnapshotListener { snapshot, failure ->
            if (failure != null) {
                publishFailure("La signalisation du direct est interrompue.")
                return@addSnapshotListener
            }
            snapshot?.documentChanges.orEmpty()
                .filter { it.type == DocumentChange.Type.ADDED || it.type == DocumentChange.Type.MODIFIED }
                .forEach { change ->
                    val viewerId = change.document.getString("viewerId").orEmpty()
                    val state = change.document.getString("status").orEmpty()
                    if (viewerId.isNotBlank() && state !in setOf("blocked", "disconnected") && !peers.containsKey(viewerId) && peers.size < 8) {
                        scope.launch { runCatching { createHostPeer(viewerId) }.onFailure { publishFailure("Un spectateur n’a pas pu rejoindre le direct.") } }
                    }
                }
        }
    }

    private suspend fun createHostPeer(viewerId: String) {
        val peer = createPeer(viewerId, localCandidateCollection = "hostCandidates", hostSide = true)
        peers[viewerId] = peer
        audioTrack?.let { peer.addTrack(it, listOf("wapi-live")) }
        videoTrack?.let { peer.addTrack(it, listOf("wapi-live")) }
        val peerRef = peerReference(viewerId)
        peerListeners.getOrPut(viewerId) { mutableListOf() } += peerRef.addSnapshotListener { snapshot, _ ->
            val answer = snapshot?.get("answer") as? Map<*, *> ?: return@addSnapshotListener
            if (remoteReady[viewerId] == true) return@addSnapshotListener
            scope.launch {
                runCatching {
                    peer.setRemoteAwait(SessionDescription(SessionDescription.Type.ANSWER, answer["sdp"]?.toString().orEmpty()))
                    remoteReady[viewerId] = true
                    flushIce(viewerId, peer)
                }
            }
        }
        listenCandidates(viewerId, "viewerCandidates", peer)
        val offer = peer.createOfferAwait()
        peer.setLocalAwait(offer)
        peerRef.set(
            mapOf(
                "offer" to mapOf("type" to "offer", "sdp" to offer.description),
                "status" to "offered",
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

    private fun createViewerPeer(viewerId: String) {
        val peer = createPeer(viewerId, localCandidateCollection = "viewerCandidates", hostSide = false)
        peers[viewerId] = peer
        val peerRef = peerReference(viewerId)
        peerListeners.getOrPut(viewerId) { mutableListOf() } += peerRef.addSnapshotListener { snapshot, failure ->
            if (failure != null) {
                publishFailure("Le direct n’est plus joignable.")
                return@addSnapshotListener
            }
            if (snapshot?.getString("status") in setOf("blocked", "disconnected")) {
                publishFailure("Vous avez quitté ce direct.")
                return@addSnapshotListener
            }
            val offer = snapshot?.get("offer") as? Map<*, *> ?: return@addSnapshotListener
            if (remoteReady[viewerId] == true) return@addSnapshotListener
            scope.launch {
                runCatching {
                    peer.setRemoteAwait(SessionDescription(SessionDescription.Type.OFFER, offer["sdp"]?.toString().orEmpty()))
                    remoteReady[viewerId] = true
                    flushIce(viewerId, peer)
                    val answer = peer.createAnswerAwait()
                    peer.setLocalAwait(answer)
                    peerRef.set(
                        mapOf(
                            "answer" to mapOf("type" to "answer", "sdp" to answer.description),
                            "status" to "connected",
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                        com.google.firebase.firestore.SetOptions.merge(),
                    ).await()
                }.onFailure { publishFailure("La vidéo du direct n’a pas pu être négociée.") }
            }
        }
        listenCandidates(viewerId, "hostCandidates", peer)
    }

    private fun createPeer(peerId: String, localCandidateCollection: String, hostSide: Boolean): PeerConnection {
        val peer = factory.createPeerConnection(WapiIceDefaults.rtcConfiguration(iceServers), object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                scope.launch {
                    runCatching {
                        peerReference(peerId).collection(localCandidateCollection).add(
                            mapOf("candidate" to candidate.sdp, "sdpMid" to (candidate.sdpMid ?: "0"), "sdpMLineIndex" to candidate.sdpMLineIndex),
                        ).await()
                    }
                }
            }
            override fun onTrack(transceiver: org.webrtc.RtpTransceiver) = attachRemoteTrack(transceiver.receiver.track() as? VideoTrack)
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = attachRemoteTrack(receiver.track() as? VideoTrack)
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> if (!hostSide) publishState("En direct", true)
                    PeerConnection.PeerConnectionState.FAILED -> publishFailure("Connexion WebRTC impossible sur ce réseau. Un relais TURN WAPI est requis.")
                    else -> Unit
                }
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                if (!hostSide && state == PeerConnection.IceConnectionState.CHECKING) publishState("Connexion sécurisée…", false)
            }
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(channel: DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
        }) ?: error("Le moteur WebRTC n’a pas pu démarrer.")
        return peer
    }

    private fun listenCandidates(peerId: String, collection: String, peer: PeerConnection) {
        peerListeners.getOrPut(peerId) { mutableListOf() } += peerReference(peerId).collection(collection).addSnapshotListener { snapshot, _ ->
            snapshot?.documentChanges.orEmpty().filter { it.type == DocumentChange.Type.ADDED }.forEach { change ->
                val candidate = IceCandidate(
                    change.document.getString("sdpMid"),
                    change.document.getLong("sdpMLineIndex")?.toInt() ?: 0,
                    change.document.getString("candidate").orEmpty(),
                )
                if (remoteReady[peerId] == true) peer.addIceCandidate(candidate)
                else queuedRemoteIce.getOrPut(peerId) { mutableListOf() }.add(candidate)
            }
        }
    }

    private fun flushIce(peerId: String, peer: PeerConnection) {
        queuedRemoteIce.remove(peerId).orEmpty().forEach(peer::addIceCandidate)
    }

    private fun attachRemoteTrack(track: VideoTrack?) {
        if (track == null) return
        remoteVideoTrack?.let { old -> remoteRenderer?.let(old::removeSink) }
        remoteVideoTrack = track
        remoteRenderer?.let(track::addSink)
    }

    private fun peerReference(peerId: String) = db.collection("liveSessions").document(liveId).collection("peers").document(peerId)

    private fun fallbackIceServers() = WapiIceDefaults.fallbackServers()

    private suspend fun loadIceServers(): List<PeerConnection.IceServer> = runCatching {
        val result = withTimeoutOrNull(WapiIceDefaults.CONFIGURATION_TIMEOUT_MS) {
            functions.getHttpsCallable("getWebRtcIceServers").call().await()
        } ?: return@runCatching fallbackIceServers()
        val payload = result.data as? Map<*, *> ?: return@runCatching fallbackIceServers()
        val configured = (payload["iceServers"] as? List<*>).orEmpty().mapNotNull { raw ->
            val value = raw as? Map<*, *> ?: return@mapNotNull null
            val urls = when (val entry = value["urls"]) {
                is String -> listOf(entry)
                is List<*> -> entry.mapNotNull { it?.toString() }
                else -> emptyList()
            }.filter(String::isNotBlank)
            if (urls.isEmpty()) return@mapNotNull null
            PeerConnection.IceServer.builder(urls).apply {
                val username = value["username"]?.toString().orEmpty()
                val credential = value["credential"]?.toString().orEmpty()
                if (username.isNotBlank() && credential.isNotBlank()) setUsername(username).setPassword(credential)
            }.createIceServer()
        }
        WapiIceDefaults.merge(configured)
    }.getOrElse { fallbackIceServers() }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerators = buildList<CameraEnumerator> {
            if (runCatching { Camera2Enumerator.isSupported(appContext) }.getOrDefault(false)) {
                add(Camera2Enumerator(appContext))
            }
            add(Camera1Enumerator(true))
        }
        return enumerators.firstNotNullOfOrNull { enumerator ->
            runCatching {
                (enumerator.deviceNames.filter(enumerator::isFrontFacing) +
                    enumerator.deviceNames.filterNot(enumerator::isFrontFacing))
                    .firstNotNullOfOrNull { runCatching { enumerator.createCapturer(it, null) }.getOrNull() }
            }.getOrNull()
        }
    }

    private fun publishState(label: String, isConnected: Boolean) = main.post {
        status = label
        connected = isConnected
        if (isConnected) error = null
        onStateChanged?.invoke(status, connected, error)
    }

    private fun publishFailure(message: String) = main.post {
        status = "Direct interrompu"
        connected = false
        error = message
        onStateChanged?.invoke(status, connected, error)
    }
}

private suspend fun PeerConnection.createOfferAwait(): SessionDescription = suspendCoroutine { continuation ->
    createOffer(object : SdpObserver {
        override fun onCreateSuccess(value: SessionDescription) = continuation.resume(value)
        override fun onCreateFailure(error: String) = continuation.resumeWithException(IllegalStateException(error))
        override fun onSetSuccess() = Unit
        override fun onSetFailure(error: String) = Unit
    }, MediaConstraints())
}

private suspend fun PeerConnection.createAnswerAwait(): SessionDescription = suspendCoroutine { continuation ->
    createAnswer(object : SdpObserver {
        override fun onCreateSuccess(value: SessionDescription) = continuation.resume(value)
        override fun onCreateFailure(error: String) = continuation.resumeWithException(IllegalStateException(error))
        override fun onSetSuccess() = Unit
        override fun onSetFailure(error: String) = Unit
    }, MediaConstraints())
}

private suspend fun PeerConnection.setLocalAwait(value: SessionDescription): Unit = suspendCoroutine { continuation ->
    setLocalDescription(object : SdpObserver {
        override fun onSetSuccess() = continuation.resume(Unit)
        override fun onSetFailure(error: String) = continuation.resumeWithException(IllegalStateException(error))
        override fun onCreateSuccess(value: SessionDescription) = Unit
        override fun onCreateFailure(error: String) = Unit
    }, value)
}

private suspend fun PeerConnection.setRemoteAwait(value: SessionDescription): Unit = suspendCoroutine { continuation ->
    setRemoteDescription(object : SdpObserver {
        override fun onSetSuccess() = continuation.resume(Unit)
        override fun onSetFailure(error: String) = continuation.resumeWithException(IllegalStateException(error))
        override fun onCreateSuccess(value: SessionDescription) = Unit
        override fun onCreateFailure(error: String) = Unit
    }, value)
}
