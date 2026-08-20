package com.whappy.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
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
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val BRAND_BLUE = 0xFF1C1C58

data class WhappyCallUiState(
    val visible: Boolean = false,
    val incoming: Boolean = false,
    val video: Boolean = false,
    val peerName: String = "",
    val peerPhone: String = "",
    val status: String = "",
    val muted: Boolean = false,
    val cameraEnabled: Boolean = true,
    val error: String? = null,
)

val LocalWhappyCalls = staticCompositionLocalOf<WhappyCallController?> { null }

class WhappyCallController(private val activity: ComponentActivity) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val repository = WhappyRepository()
    private val eglBase = EglBase.create()
    private val factory: PeerConnectionFactory
    private val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val registrations = CopyOnWriteArrayList<ListenerRegistration>()
    private var incomingRegistration: ListenerRegistration? = null
    private var boundUserId = ""
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var callId = ""
    private var pendingCallId = ""
    private var pendingIncoming: DocumentSnapshot? = null
    private var remoteDescriptionReady = false
    private var answerApplied = false
    private val queuedRemoteCandidates = mutableListOf<IceCandidate>()
    private val queuedLocalCandidates = mutableListOf<IceCandidate>()
    private var pendingPermissionAction: (() -> Unit)? = null

    var state by mutableStateOf(WhappyCallUiState())
        private set

    private val permissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val allowed = grants.values.all { it }
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (allowed) action?.invoke()
        else state = state.copy(visible = true, status = "Autorisation requise", error = "Autorisez le micro et la caméra pour appeler sur Whappy.")
    }

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(activity.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun bindUser(userId: String?) {
        val next = userId.orEmpty()
        if (next == boundUserId) return
        incomingRegistration?.remove()
        incomingRegistration = null
        boundUserId = next
        if (next.isBlank()) return
        incomingRegistration = db.collection("calls").whereEqualTo("calleeId", next)
            .addSnapshotListener { snapshot, _ ->
                if (state.visible) return@addSnapshotListener
                val recent = snapshot?.documents.orEmpty().firstOrNull { document ->
                    val createdAt = document.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                    document.getString("status") == "ringing" && System.currentTimeMillis() - createdAt < 120_000L
                } ?: return@addSnapshotListener
                pendingIncoming = recent
                pendingCallId = recent.id
                state = WhappyCallUiState(
                    visible = true,
                    incoming = true,
                    video = recent.getBoolean("video") == true,
                    peerName = recent.getString("callerName") ?: "Contact Whappy",
                    status = if (recent.getBoolean("video") == true) "Appel vidéo entrant" else "Appel audio entrant",
                )
                watchCallDocument(recent.id)
            }
    }

    fun startByPhone(phone: String, video: Boolean) {
        val value = phone.trim()
        if (value.isBlank()) {
            state = WhappyCallUiState(visible = true, video = video, status = "Numéro requis", error = "Entrez un numéro Whappy complet.")
            return
        }
        activity.lifecycleScope.launch {
            state = WhappyCallUiState(visible = true, video = video, peerPhone = value, status = "Recherche du compte Whappy…")
            runCatching { withContext(Dispatchers.IO) { repository.findUserByPhone(value) } }
                .onSuccess { peer ->
                    val currentId = auth.currentUser?.uid
                    when {
                        peer == null -> state = state.copy(status = "Compte introuvable", error = "Ce numéro n’est pas encore inscrit sur Whappy.")
                        peer.uid == currentId -> state = state.copy(status = "Votre numéro", error = "Vous ne pouvez pas vous appeler vous-même.")
                        else -> start(peer, video)
                    }
                }
                .onFailure { state = state.copy(status = "Recherche impossible", error = "Vérifiez votre connexion puis réessayez.") }
        }
    }

    fun start(peer: WhappyMember, video: Boolean) {
        withCallPermissions(video) {
            activity.lifecycleScope.launch {
                beginOutgoing(peer, video)
            }
        }
    }

    fun acceptIncoming() {
        val incoming = pendingIncoming ?: return
        withCallPermissions(incoming.getBoolean("video") == true) {
            activity.lifecycleScope.launch { beginIncoming(incoming) }
        }
    }

    fun declineIncoming() {
        val id = pendingCallId
        activity.lifecycleScope.launch {
            if (id.isNotBlank()) runCatching { db.collection("calls").document(id).update(mapOf("status" to "declined", "updatedAt" to FieldValue.serverTimestamp())).await() }
            closeLocal()
        }
    }

    fun hangUp() {
        val id = callId.ifBlank { pendingCallId }
        activity.lifecycleScope.launch {
            if (id.isNotBlank()) runCatching { db.collection("calls").document(id).update(mapOf("status" to "ended", "updatedAt" to FieldValue.serverTimestamp())).await() }
            closeLocal()
        }
    }

    fun dismissError() = closeLocal()

    fun toggleMicrophone() {
        val enabled = !(localAudioTrack?.enabled() ?: true)
        localAudioTrack?.setEnabled(enabled)
        state = state.copy(muted = !enabled)
    }

    fun toggleCamera() {
        val enabled = !(localVideoTrack?.enabled() ?: true)
        localVideoTrack?.setEnabled(enabled)
        state = state.copy(cameraEnabled = enabled)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun attachRenderer(renderer: SurfaceViewRenderer, local: Boolean) {
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setEnableHardwareScaler(true)
        renderer.setMirror(local)
        if (local) {
            localRenderer = renderer
            localVideoTrack?.addSink(renderer)
        } else {
            remoteRenderer = renderer
            remoteVideoTrack?.addSink(renderer)
        }
    }

    fun detachRenderer(renderer: SurfaceViewRenderer, local: Boolean) {
        if (local) {
            localVideoTrack?.removeSink(renderer)
            if (localRenderer === renderer) localRenderer = null
        } else {
            remoteVideoTrack?.removeSink(renderer)
            if (remoteRenderer === renderer) remoteRenderer = null
        }
        runCatching { renderer.release() }
    }

    private fun withCallPermissions(video: Boolean, action: () -> Unit) {
        val required = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (video) add(Manifest.permission.CAMERA)
        }
        if (required.all { ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED }) {
            action()
        } else {
            pendingPermissionAction = action
            permissionLauncher.launch(required.toTypedArray())
        }
    }

    private suspend fun beginOutgoing(peer: WhappyMember, video: Boolean) {
        val current = auth.currentUser ?: return
        closeConnectionsOnly()
        state = WhappyCallUiState(visible = true, video = video, peerName = peer.displayName, peerPhone = peer.phoneNumber, status = "Connexion sécurisée…")
        runCatching {
            preparePeer(video, "callerCandidates")
            val offer = peerConnection!!.createOfferAwait()
            peerConnection!!.setLocalDescriptionAwait(offer)
            val reference = db.collection("calls").document()
            callId = reference.id
            reference.set(
                mapOf(
                    "callerId" to current.uid,
                    "calleeId" to peer.uid,
                    "callerName" to (current.displayName ?: "Contact Whappy"),
                    "calleeName" to peer.displayName,
                    "video" to video,
                    "status" to "ringing",
                    "offer" to mapOf("type" to "offer", "sdp" to offer.description),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            queuedLocalCandidates.toList().forEach { addCandidate(reference.id, "callerCandidates", it) }
            queuedLocalCandidates.clear()
            watchRemoteCandidates(reference.id, "calleeCandidates")
            watchCallDocument(reference.id)
            state = state.copy(status = "Sonnerie…")
        }.onFailure { failCall("L’appel Whappy n’a pas pu démarrer.") }
    }

    private suspend fun beginIncoming(incoming: DocumentSnapshot) {
        closeConnectionsOnly(keepDocumentWatch = true)
        callId = incoming.id
        pendingCallId = incoming.id
        val video = incoming.getBoolean("video") == true
        state = state.copy(incoming = false, video = video, status = "Connexion sécurisée…", error = null)
        runCatching {
            preparePeer(video, "calleeCandidates")
            val offer = incoming.get("offer") as? Map<*, *> ?: error("offer")
            val remote = SessionDescription(SessionDescription.Type.OFFER, offer["sdp"]?.toString().orEmpty())
            peerConnection!!.setRemoteDescriptionAwait(remote)
            remoteDescriptionReady = true
            flushRemoteCandidates()
            watchRemoteCandidates(incoming.id, "callerCandidates")
            val answer = peerConnection!!.createAnswerAwait()
            peerConnection!!.setLocalDescriptionAwait(answer)
            db.collection("calls").document(incoming.id).update(
                mapOf(
                    "answer" to mapOf("type" to "answer", "sdp" to answer.description),
                    "status" to "accepted",
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            queuedLocalCandidates.toList().forEach { addCandidate(incoming.id, "calleeCandidates", it) }
            queuedLocalCandidates.clear()
            state = state.copy(status = "Connecté")
        }.onFailure { failCall("Impossible d’accepter cet appel.") }
    }

    private fun preparePeer(video: Boolean, localCandidateCollection: String) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = video
        localAudioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("whappy-audio", localAudioSource).also { it.setEnabled(true) }
        if (video) {
            val capturer = createCameraCapturer() ?: error("camera")
            videoCapturer = capturer
            surfaceTextureHelper = SurfaceTextureHelper.create("WhappyCamera", eglBase.eglBaseContext)
            localVideoSource = factory.createVideoSource(false).also { source ->
                capturer.initialize(surfaceTextureHelper, activity, source.capturerObserver)
                capturer.startCapture(720, 1280, 30)
            }
            localVideoTrack = factory.createVideoTrack("whappy-video", localVideoSource).also { track ->
                track.setEnabled(true)
                localRenderer?.let(track::addSink)
            }
        }
        val servers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        )
        peerConnection = factory.createPeerConnection(servers, peerObserver(localCandidateCollection)) ?: error("peer")
        peerConnection!!.addTrack(localAudioTrack, listOf("whappy-stream"))
        localVideoTrack?.let { peerConnection!!.addTrack(it, listOf("whappy-stream")) }
    }

    private fun peerObserver(localCandidateCollection: String) = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            val id = callId.ifBlank { pendingCallId }
            if (id.isBlank()) queuedLocalCandidates += candidate
            else activity.lifecycleScope.launch { runCatching { addCandidate(id, localCandidateCollection, candidate) } }
        }
        override fun onTrack(transceiver: org.webrtc.RtpTransceiver) {
            (transceiver.receiver.track() as? VideoTrack)?.let { track -> remoteVideoTrack = track; remoteRenderer?.let(track::addSink) }
        }
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
            (receiver.track() as? VideoTrack)?.let { track -> remoteVideoTrack = track; remoteRenderer?.let(track::addSink) }
        }
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            when (newState) {
                PeerConnection.PeerConnectionState.CONNECTED -> activity.runOnUiThread { state = state.copy(status = "Connecté", error = null) }
                PeerConnection.PeerConnectionState.FAILED, PeerConnection.PeerConnectionState.CLOSED -> activity.runOnUiThread { state = state.copy(status = "Appel terminé") }
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
    }

    private fun watchCallDocument(id: String) {
        registrations += db.collection("calls").document(id).addSnapshotListener { snapshot, _ ->
            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
            val status = snapshot.getString("status") ?: return@addSnapshotListener
            if (status == "declined" || status == "ended") {
                activity.runOnUiThread { closeLocal() }
                return@addSnapshotListener
            }
            if (status == "accepted" && !answerApplied && peerConnection != null) {
                val answer = snapshot.get("answer") as? Map<*, *> ?: return@addSnapshotListener
                answerApplied = true
                activity.lifecycleScope.launch {
                    runCatching {
                        peerConnection?.setRemoteDescriptionAwait(SessionDescription(SessionDescription.Type.ANSWER, answer["sdp"]?.toString().orEmpty()))
                        remoteDescriptionReady = true
                        flushRemoteCandidates()
                        state = state.copy(status = "Connecté")
                    }.onFailure { failCall("La connexion audio/vidéo a échoué.") }
                }
            }
        }
    }

    private fun watchRemoteCandidates(id: String, collection: String) {
        registrations += db.collection("calls").document(id).collection(collection).addSnapshotListener { snapshot, _ ->
            snapshot?.documentChanges.orEmpty().filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }.forEach { change ->
                val data = change.document.data
                val candidate = IceCandidate(data["sdpMid"]?.toString(), (data["sdpMLineIndex"] as? Number)?.toInt() ?: 0, data["candidate"]?.toString().orEmpty())
                if (remoteDescriptionReady) peerConnection?.addIceCandidate(candidate) else queuedRemoteCandidates += candidate
            }
        }
    }

    private suspend fun addCandidate(id: String, collection: String, candidate: IceCandidate) {
        db.collection("calls").document(id).collection(collection).add(
            mapOf("candidate" to candidate.sdp, "sdpMid" to (candidate.sdpMid ?: "0"), "sdpMLineIndex" to candidate.sdpMLineIndex),
        ).await()
    }

    private fun flushRemoteCandidates() {
        queuedRemoteCandidates.toList().forEach { peerConnection?.addIceCandidate(it) }
        queuedRemoteCandidates.clear()
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = if (Camera2Enumerator.isSupported(activity)) Camera2Enumerator(activity) else Camera1Enumerator(true)
        val names = enumerator.deviceNames
        return (names.filter { enumerator.isFrontFacing(it) } + names.filterNot { enumerator.isFrontFacing(it) })
            .firstNotNullOfOrNull { name -> enumerator.createCapturer(name, null) }
    }

    private fun failCall(message: String) {
        state = state.copy(visible = true, status = "Connexion impossible", error = message)
        closeConnectionsOnly(keepDocumentWatch = true)
    }

    private fun closeLocal() {
        closeConnectionsOnly()
        pendingIncoming = null
        pendingCallId = ""
        callId = ""
        state = WhappyCallUiState()
    }

    private fun closeConnectionsOnly(keepDocumentWatch: Boolean = false) {
        if (!keepDocumentWatch) {
            registrations.forEach { it.remove() }
            registrations.clear()
        }
        runCatching { videoCapturer?.stopCapture() }
        videoCapturer?.dispose()
        surfaceTextureHelper?.dispose()
        localVideoTrack?.dispose()
        localVideoSource?.dispose()
        localAudioTrack?.dispose()
        localAudioSource?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        videoCapturer = null
        surfaceTextureHelper = null
        localVideoTrack = null
        remoteVideoTrack = null
        localVideoSource = null
        localAudioTrack = null
        localAudioSource = null
        peerConnection = null
        queuedLocalCandidates.clear()
        queuedRemoteCandidates.clear()
        remoteDescriptionReady = false
        answerApplied = false
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    fun release() {
        closeLocal()
        incomingRegistration?.remove()
        incomingRegistration = null
        factory.dispose()
        eglBase.release()
    }
}

private suspend fun PeerConnection.createOfferAwait(): SessionDescription = suspendCoroutine { continuation ->
    createOffer(object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = continuation.resume(description)
        override fun onCreateFailure(error: String) = continuation.resumeWithException(IllegalStateException(error))
        override fun onSetSuccess() = Unit
        override fun onSetFailure(error: String) = Unit
    }, MediaConstraints())
}

private suspend fun PeerConnection.createAnswerAwait(): SessionDescription = suspendCoroutine { continuation ->
    createAnswer(object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = continuation.resume(description)
        override fun onCreateFailure(error: String) = continuation.resumeWithException(IllegalStateException(error))
        override fun onSetSuccess() = Unit
        override fun onSetFailure(error: String) = Unit
    }, MediaConstraints())
}

private suspend fun PeerConnection.setLocalDescriptionAwait(description: SessionDescription): Unit = suspendCoroutine { continuation ->
    setLocalDescription(object : SdpObserver {
        override fun onSetSuccess() = continuation.resume(Unit)
        override fun onSetFailure(error: String) = continuation.resumeWithException(IllegalStateException(error))
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onCreateFailure(error: String) = Unit
    }, description)
}

private suspend fun PeerConnection.setRemoteDescriptionAwait(description: SessionDescription): Unit = suspendCoroutine { continuation ->
    setRemoteDescription(object : SdpObserver {
        override fun onSetSuccess() = continuation.resume(Unit)
        override fun onSetFailure(error: String) = continuation.resumeWithException(IllegalStateException(error))
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onCreateFailure(error: String) = Unit
    }, description)
}

@Composable
fun WhappyCallOverlay(controller: WhappyCallController) {
    val call = controller.state
    if (!call.visible) return
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Color(BRAND_BLUE)), contentAlignment = Alignment.Center) {
        if (call.video && !call.incoming && call.error == null) {
            AndroidView(
                factory = { SurfaceViewRenderer(context).also { controller.attachRenderer(it, false) } },
                modifier = Modifier.fillMaxSize(),
                onRelease = { controller.detachRenderer(it, false) },
            )
            AndroidView(
                factory = { SurfaceViewRenderer(context).also { controller.attachRenderer(it, true) } },
                modifier = Modifier.align(Alignment.TopEnd).padding(18.dp).size(116.dp, 172.dp),
                onRelease = { controller.detachRenderer(it, true) },
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (!call.video || call.incoming || call.error != null) {
                Box(Modifier.size(112.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Text(call.peerName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.joinToString("").take(2).uppercase().ifBlank { "W" }, color = Color(BRAND_BLUE), fontSize = 34.sp)
                }
            }
            Text(call.peerName.ifBlank { "WHAPPY CALL" }, Modifier.padding(top = 22.dp), color = Color.White, fontSize = 25.sp)
            Text(call.status, Modifier.padding(top = 8.dp), color = Color.White.copy(alpha = .8f))
            call.error?.let { Text(it, Modifier.padding(24.dp), color = Color.White, fontSize = 14.sp) }
            Spacer(Modifier.size(22.dp))
            if (call.incoming) {
                Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    FilledIconButton(onClick = controller::declineIncoming, modifier = Modifier.size(64.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)) { Icon(Icons.Rounded.CallEnd, "Refuser", tint = Color(BRAND_BLUE)) }
                    FilledIconButton(onClick = controller::acceptIncoming, modifier = Modifier.size(64.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)) { Icon(if (call.video) Icons.Rounded.Videocam else Icons.Rounded.Phone, "Accepter", tint = Color(BRAND_BLUE)) }
                }
            } else if (call.error != null) {
                Button(onClick = controller::dismissError, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(BRAND_BLUE))) { Text("Fermer") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledIconButton(onClick = controller::toggleMicrophone, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)) { Icon(if (call.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, "Micro", tint = Color(BRAND_BLUE)) }
                    if (call.video) FilledIconButton(onClick = controller::toggleCamera, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)) { Icon(if (call.cameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, "Caméra", tint = Color(BRAND_BLUE)) }
                    if (call.video) FilledIconButton(onClick = controller::switchCamera, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)) { Icon(Icons.Rounded.Cameraswitch, "Changer de caméra", tint = Color(BRAND_BLUE)) }
                    FilledIconButton(onClick = controller::hangUp, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)) { Icon(Icons.Rounded.CallEnd, "Raccrocher", tint = Color(BRAND_BLUE)) }
                }
            }
        }
        if (!call.incoming && call.error == null) CircularProgressIndicator(Modifier.align(Alignment.TopStart).padding(22.dp).size(22.dp), color = Color.White, strokeWidth = 2.dp)
    }
}
