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
import com.google.firebase.firestore.SetOptions
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
 * Small-group WebRTC mesh owned by WAPI. Firestore carries authenticated SDP
 * and ICE only; audio/video remain encrypted peer-to-peer. Six participants is
 * the hard limit because a phone publishes one stream per remote participant.
 */
internal class WapiGroupRtcEngine(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance("europe-west1")
    private val main = Handler(Looper.getMainLooper())
    private val egl = EglBase.create()
    private val factory: PeerConnectionFactory
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val peers = ConcurrentHashMap<String, PeerConnection>()
    private val listeners = mutableListOf<ListenerRegistration>()
    private val pairListeners = ConcurrentHashMap<String, MutableList<ListenerRegistration>>()
    private val queuedIce = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private val remoteReady = ConcurrentHashMap<String, Boolean>()
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var iceServers = fallbackIceServers()
    private var closed = false

    var callId: String = ""
        private set
    var groupName: String = "Groupe WAPI"
        private set
    var video: Boolean = false
        private set
    var host: Boolean = false
        private set
    var microphoneEnabled: Boolean = true
        private set
    var cameraEnabled: Boolean = false
        private set
    var onStateChanged: ((String, String?) -> Unit)? = null

    init {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions())
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    suspend fun start(groupId: String, groupSource: String, invitationCallId: String, requestedVideo: Boolean) {
        val callable = if (invitationCallId.isBlank()) "createGroupCallSession" else "joinGroupCallSession"
        val data = if (invitationCallId.isBlank()) mapOf("groupId" to groupId, "source" to groupSource, "video" to requestedVideo) else mapOf("callId" to invitationCallId)
        val result = functions.getHttpsCallable(callable).call(data).await().data as? Map<*, *> ?: error("Réponse d’appel WAPI invalide.")
        require(result["streamProvider"] == "wapi-webrtc-mesh") { "Le moteur WebRTC WAPI n’est pas disponible." }
        callId = result["callId"]?.toString().orEmpty()
        groupName = result["groupName"]?.toString()?.takeIf(String::isNotBlank) ?: "Groupe WAPI"
        video = result["video"] == true
        host = result["role"] == "host"
        require(callId.isNotBlank())
        iceServers = loadIceServers()
        createLocalMedia()
        functions.getHttpsCallable("setGroupCallPresence").call(mapOf("callId" to callId, "active" to true)).await()
        listenParticipants()
        publish("Appel WebRTC sécurisé", null)
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
        if (local) videoTrack?.removeSink(renderer) else remoteVideoTrack?.removeSink(renderer)
        if (local && localRenderer === renderer) localRenderer = null
        if (!local && remoteRenderer === renderer) remoteRenderer = null
        runCatching { renderer.release() }
    }

    fun setMicrophone(enabled: Boolean): Boolean {
        microphoneEnabled = enabled
        audioTrack?.setEnabled(enabled)
        return enabled
    }

    fun setCamera(enabled: Boolean): Boolean {
        cameraEnabled = video && enabled
        videoTrack?.setEnabled(cameraEnabled)
        return cameraEnabled
    }

    fun switchCamera() = capturer?.switchCamera(null)

    suspend fun leave(endForEveryone: Boolean) {
        if (callId.isNotBlank()) runCatching {
            functions.getHttpsCallable(if (endForEveryone) "endGroupCallSession" else "setGroupCallPresence")
                .call(if (endForEveryone) mapOf("callId" to callId) else mapOf("callId" to callId, "active" to false)).await()
        }
        close()
    }

    fun close() {
        if (closed) return
        closed = true
        listeners.forEach(ListenerRegistration::remove)
        pairListeners.values.flatten().forEach(ListenerRegistration::remove)
        peers.values.forEach { it.close(); it.dispose() }
        peers.clear()
        runCatching { capturer?.stopCapture() }
        capturer?.dispose()
        textureHelper?.dispose()
        videoSource?.dispose()
        audioSource?.dispose()
        factory.dispose()
        egl.release()
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    private fun createLocalMedia() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = video
        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("wapi-group-audio", audioSource).also { it.setEnabled(true) }
        microphoneEnabled = true
        if (!video) return
        capturer = createCameraCapturer() ?: error("Aucune caméra compatible n’a été trouvée.")
        videoSource = factory.createVideoSource(false)
        textureHelper = SurfaceTextureHelper.create("WapiGroupCamera", egl.eglBaseContext)
        capturer!!.initialize(textureHelper, appContext, videoSource!!.capturerObserver)
        capturer!!.startCapture(960, 540, 24)
        videoTrack = factory.createVideoTrack("wapi-group-video", videoSource).also { track ->
            track.setEnabled(true)
            localRenderer?.let(track::addSink)
        }
        cameraEnabled = true
    }

    private fun listenParticipants() {
        val localId = auth.currentUser?.uid ?: error("Connexion WAPI requise.")
        listeners += sessionRef().collection("participants").addSnapshotListener { snapshot, failure ->
            if (failure != null) { publish("Signalisation interrompue", "La connexion de groupe a été interrompue."); return@addSnapshotListener }
            snapshot?.documentChanges.orEmpty().forEach { change ->
                val remoteId = change.document.id
                val active = change.document.getBoolean("active") == true
                if (remoteId == localId) return@forEach
                if (active && !peers.containsKey(remoteId)) scope.launch { runCatching { connectPeer(localId, remoteId) }.onFailure { publish("Connexion partielle", "Un participant n’a pas pu être connecté.") } }
                if (!active) removePeer(remoteId)
            }
        }
    }

    private suspend fun connectPeer(localId: String, remoteId: String) {
        if (peers.size >= 5 || peers.containsKey(remoteId)) return
        val aId = minOf(localId, remoteId)
        val bId = maxOf(localId, remoteId)
        val initiator = localId == aId
        val peer = createPeer(remoteId, if (initiator) "aCandidates" else "bCandidates")
        peers[remoteId] = peer
        audioTrack?.let { peer.addTrack(it, listOf("wapi-group")) }
        videoTrack?.let { peer.addTrack(it, listOf("wapi-group")) }
        val pair = pairRef(aId, bId)
        val remoteCandidates = if (initiator) "bCandidates" else "aCandidates"
        listenCandidates(remoteId, pair, remoteCandidates, peer)
        pairListeners.getOrPut(remoteId) { mutableListOf() } += pair.addSnapshotListener { snapshot, _ ->
            scope.launch {
                runCatching {
                    if (initiator) {
                        val answer = snapshot?.get("answer") as? Map<*, *> ?: return@runCatching
                        if (remoteReady[remoteId] == true) return@runCatching
                        peer.setRemoteGroupAwait(SessionDescription(SessionDescription.Type.ANSWER, answer["sdp"]?.toString().orEmpty()))
                        remoteReady[remoteId] = true
                        flushIce(remoteId, peer)
                    } else {
                        val offer = snapshot?.get("offer") as? Map<*, *> ?: return@runCatching
                        if (remoteReady[remoteId] == true) return@runCatching
                        peer.setRemoteGroupAwait(SessionDescription(SessionDescription.Type.OFFER, offer["sdp"]?.toString().orEmpty()))
                        remoteReady[remoteId] = true
                        flushIce(remoteId, peer)
                        val answer = peer.createAnswerGroupAwait()
                        peer.setLocalGroupAwait(answer)
                        pair.set(mapOf("participantIds" to listOf(aId, bId), "aId" to aId, "bId" to bId, "answer" to mapOf("type" to "answer", "sdp" to answer.description), "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
                    }
                }.onFailure { publish("Connexion partielle", "La négociation WebRTC avec un participant a échoué.") }
            }
        }
        if (initiator) {
            val offer = peer.createOfferGroupAwait()
            peer.setLocalGroupAwait(offer)
            pair.set(mapOf("participantIds" to listOf(aId, bId), "aId" to aId, "bId" to bId, "offer" to mapOf("type" to "offer", "sdp" to offer.description), "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
        }
    }

    private fun createPeer(remoteId: String, localCandidates: String): PeerConnection = factory.createPeerConnection(WapiIceDefaults.rtcConfiguration(iceServers), object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            val localId = auth.currentUser?.uid ?: return
            val pair = pairRef(minOf(localId, remoteId), maxOf(localId, remoteId))
            scope.launch { runCatching { pair.collection(localCandidates).add(mapOf("ownerId" to localId, "candidate" to candidate.sdp, "sdpMid" to (candidate.sdpMid ?: "0"), "sdpMLineIndex" to candidate.sdpMLineIndex)).await() } }
        }
        override fun onTrack(transceiver: org.webrtc.RtpTransceiver) = attachRemoteVideo(transceiver.receiver.track() as? VideoTrack)
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = attachRemoteVideo(receiver.track() as? VideoTrack)
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> publish("Connecté", null)
                PeerConnection.PeerConnectionState.FAILED -> publish("Connexion partielle", "Un relais TURN WAPI peut être requis sur ce réseau.")
                else -> Unit
            }
        }
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
    }) ?: error("Le moteur WebRTC n’a pas pu créer la connexion.")

    private fun listenCandidates(remoteId: String, pair: com.google.firebase.firestore.DocumentReference, collection: String, peer: PeerConnection) {
        pairListeners.getOrPut(remoteId) { mutableListOf() } += pair.collection(collection).addSnapshotListener { snapshot, _ ->
            snapshot?.documentChanges.orEmpty().filter { it.type == DocumentChange.Type.ADDED }.forEach { change ->
                val candidate = IceCandidate(change.document.getString("sdpMid"), change.document.getLong("sdpMLineIndex")?.toInt() ?: 0, change.document.getString("candidate").orEmpty())
                if (remoteReady[remoteId] == true) peer.addIceCandidate(candidate) else queuedIce.getOrPut(remoteId) { mutableListOf() }.add(candidate)
            }
        }
    }

    private fun flushIce(remoteId: String, peer: PeerConnection) = queuedIce.remove(remoteId).orEmpty().forEach(peer::addIceCandidate)

    private fun attachRemoteVideo(track: VideoTrack?) {
        if (track == null) return
        main.post {
            remoteVideoTrack?.let { previous -> remoteRenderer?.let(previous::removeSink) }
            remoteVideoTrack = track
            remoteRenderer?.let(track::addSink)
            onStateChanged?.invoke("Vidéo connectée", null)
        }
    }

    private fun removePeer(remoteId: String) {
        pairListeners.remove(remoteId).orEmpty().forEach(ListenerRegistration::remove)
        peers.remove(remoteId)?.let { it.close(); it.dispose() }
        queuedIce.remove(remoteId)
        remoteReady.remove(remoteId)
    }

    private fun sessionRef() = db.collection("groupCallSessions").document(callId)
    private fun pairRef(aId: String, bId: String) = sessionRef().collection("peers").document("${aId}__${bId}")

    private fun fallbackIceServers() = WapiIceDefaults.fallbackServers()

    private suspend fun loadIceServers(): List<PeerConnection.IceServer> = runCatching {
        val result = withTimeoutOrNull(WapiIceDefaults.CONFIGURATION_TIMEOUT_MS) {
            functions.getHttpsCallable("getWebRtcIceServers").call().await()
        } ?: return@runCatching fallbackIceServers()
        val payload = result.data as? Map<*, *> ?: return@runCatching fallbackIceServers()
        val configured = (payload["iceServers"] as? List<*>).orEmpty().mapNotNull { raw ->
            val value = raw as? Map<*, *> ?: return@mapNotNull null
            val urls = when (val entry = value["urls"]) { is String -> listOf(entry); is List<*> -> entry.mapNotNull { it?.toString() }; else -> emptyList() }.filter(String::isNotBlank)
            if (urls.isEmpty()) return@mapNotNull null
            PeerConnection.IceServer.builder(urls).apply {
                val username = value["username"]?.toString().orEmpty(); val credential = value["credential"]?.toString().orEmpty()
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

    private fun publish(status: String, error: String?) = main.post { onStateChanged?.invoke(status, error) }
}

private suspend fun PeerConnection.createOfferGroupAwait(): SessionDescription = suspendCoroutine { continuation ->
    createOffer(object : SdpObserver {
        override fun onCreateSuccess(value: SessionDescription) = continuation.resume(value)
        override fun onCreateFailure(error: String) = continuation.resumeWithException(IllegalStateException(error))
        override fun onSetSuccess() = Unit
        override fun onSetFailure(error: String) = Unit
    }, MediaConstraints())
}

private suspend fun PeerConnection.createAnswerGroupAwait(): SessionDescription = suspendCoroutine { continuation ->
    createAnswer(object : SdpObserver {
        override fun onCreateSuccess(value: SessionDescription) = continuation.resume(value)
        override fun onCreateFailure(error: String) = continuation.resumeWithException(IllegalStateException(error))
        override fun onSetSuccess() = Unit
        override fun onSetFailure(error: String) = Unit
    }, MediaConstraints())
}

private suspend fun PeerConnection.setLocalGroupAwait(value: SessionDescription): Unit = suspendCoroutine { continuation ->
    setLocalDescription(object : SdpObserver {
        override fun onSetSuccess() = continuation.resume(Unit)
        override fun onSetFailure(error: String) = continuation.resumeWithException(IllegalStateException(error))
        override fun onCreateSuccess(value: SessionDescription) = Unit
        override fun onCreateFailure(error: String) = Unit
    }, value)
}

private suspend fun PeerConnection.setRemoteGroupAwait(value: SessionDescription): Unit = suspendCoroutine { continuation ->
    setRemoteDescription(object : SdpObserver {
        override fun onSetSuccess() = continuation.resume(Unit)
        override fun onSetFailure(error: String) = continuation.resumeWithException(IllegalStateException(error))
        override fun onCreateSuccess(value: SessionDescription) = Unit
        override fun onCreateFailure(error: String) = Unit
    }, value)
}
