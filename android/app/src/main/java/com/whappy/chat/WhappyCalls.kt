package com.whappy.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.google.firebase.functions.FirebaseFunctions
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

private const val BRAND_BLUE = 0xFF0066CF

data class WhappyCallUiState(
    val visible: Boolean = false,
    val incoming: Boolean = false,
    val video: Boolean = false,
    val peerName: String = "",
    val peerPhone: String = "",
    val peerPhotoUrl: String = "",
    val status: String = "",
    val muted: Boolean = false,
    val cameraEnabled: Boolean = true,
    val speakerOn: Boolean = false,
    val mediaReady: Boolean = false,
    val error: String? = null,
)

val LocalWhappyCalls = staticCompositionLocalOf<WhappyCallController?> { null }

class WhappyCallController(private val activity: ComponentActivity) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val repository = WhappyRepository()
    private val eglBaseDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { EglBase.create() }
    private val eglBase by eglBaseDelegate
    private val factoryDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(activity.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }
    private val factory by factoryDelegate
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
    private var ringtone: Ringtone? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ringbackToneDelegate = lazy(LazyThreadSafetyMode.NONE) {
        ToneGenerator(AudioManager.STREAM_VOICE_CALL, 72)
    }
    private val ringbackLoop = object : Runnable {
        override fun run() {
            if (state.visible && !state.incoming && state.status == "Sonnerie…") {
                runCatching { ringbackToneDelegate.value.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1_800) }
                mainHandler.postDelayed(this, 4_000)
            }
        }
    }
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
                    peerPhotoUrl = recent.getString("callerPhotoUrl").orEmpty(),
                    status = "Sonnerie…",
                )
                val notificationRings = WhappyNotifications.showIncomingCall(
                    context = activity,
                    callId = recent.id,
                    callerName = recent.getString("callerName") ?: "Contact WAPI",
                    callerPhotoUrl = recent.getString("callerPhotoUrl").orEmpty(),
                    video = recent.getBoolean("video") == true,
                )
                if (!notificationRings) startRinging()
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
        stopRinging()
        WhappyNotifications.cancelCall(activity, incoming.id)
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

    fun toggleSpeaker() {
        val enabled = !state.speakerOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= 31) {
            if (enabled) {
                audioManager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let(audioManager::setCommunicationDevice)
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        }
        state = state.copy(speakerOn = enabled)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun attachRenderer(renderer: SurfaceViewRenderer, local: Boolean) {
        runCatching {
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
        }.onFailure {
            state = state.copy(status = "Vidéo indisponible", error = "La caméra n’a pas pu démarrer. Vous pouvez fermer cet écran puis relancer l’appel.")
        }
    }

    fun detachRenderer(renderer: SurfaceViewRenderer, local: Boolean) {
        runCatching {
            if (local) {
                localVideoTrack?.removeSink(renderer)
                if (localRenderer === renderer) localRenderer = null
            } else {
                remoteVideoTrack?.removeSink(renderer)
                if (remoteRenderer === renderer) remoteRenderer = null
            }
            renderer.release()
        }
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
        state = WhappyCallUiState(visible = true, video = video, peerName = peer.displayName, peerPhone = peer.phoneNumber, peerPhotoUrl = peer.photoUrl, status = "Connexion sécurisée…")
        runCatching {
            preparePeer(video, "callerCandidates")
            state = state.copy(mediaReady = true)
            val offer = peerConnection!!.createOfferAwait()
            peerConnection!!.setLocalDescriptionAwait(offer)
            val reference = db.collection("calls").document()
            callId = reference.id
            val callerPhotoUrl = runCatching {
                db.collection("users").document(current.uid).get().await().getString("photoUrl").orEmpty()
            }.getOrDefault(current.photoUrl?.toString().orEmpty())
            reference.set(
                mapOf(
                    "callerId" to current.uid,
                    "calleeId" to peer.uid,
                    "callerName" to (current.displayName ?: "Contact Whappy"),
                    "callerPhotoUrl" to callerPhotoUrl.take(2_000),
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
            startRingback()
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
            state = state.copy(mediaReady = true)
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

    private suspend fun preparePeer(video: Boolean, localCandidateCollection: String) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= 31) {
            if (video) audioManager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let(audioManager::setCommunicationDevice)
            else audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = video
        }
        state = state.copy(speakerOn = video)
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
        val servers = loadIceServers()
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
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            when (state) {
                PeerConnection.IceConnectionState.CHECKING -> activity.runOnUiThread { this@WhappyCallController.state = this@WhappyCallController.state.copy(status = "Recherche du réseau…") }
                PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> activity.runOnUiThread { this@WhappyCallController.state = this@WhappyCallController.state.copy(status = "Connecté", error = null) }
                PeerConnection.IceConnectionState.DISCONNECTED -> activity.runOnUiThread { this@WhappyCallController.state = this@WhappyCallController.state.copy(status = "Reconnexion…") }
                PeerConnection.IceConnectionState.FAILED -> activity.runOnUiThread { this@WhappyCallController.state = this@WhappyCallController.state.copy(status = "Réseau d’appel indisponible", error = "Le relais d’appel n’est pas accessible sur ce réseau. Réessayez en Wi‑Fi ou avec un relais TURN configuré.") }
                else -> Unit
            }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
    }

    private suspend fun loadIceServers(): List<PeerConnection.IceServer> {
        val fallback = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        )
        return runCatching {
            val result = FirebaseFunctions.getInstance("europe-west1")
                .getHttpsCallable("getWebRtcIceServers")
                .call()
                .await()
            val payload = result.data as? Map<*, *> ?: return@runCatching fallback
            val rawServers = payload["iceServers"] as? List<*> ?: return@runCatching fallback
            val servers = rawServers.mapNotNull { raw ->
                val data = raw as? Map<*, *> ?: return@mapNotNull null
                val urls = when (val value = data["urls"]) {
                    is String -> listOf(value)
                    is List<*> -> value.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
                    else -> emptyList()
                }
                if (urls.isEmpty()) return@mapNotNull null
                val builder = PeerConnection.IceServer.builder(urls)
                data["username"]?.toString()?.takeIf(String::isNotBlank)?.let { username ->
                    data["credential"]?.toString()?.takeIf(String::isNotBlank)?.let { credential ->
                        builder.setUsername(username).setPassword(credential)
                    }
                }
                builder.createIceServer()
            }
            (servers + fallback).distinctBy { it.urls.joinToString(",") }
        }.getOrElse { fallback }
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
                stopRingback()
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
        stopRinging()
        WhappyNotifications.cancelCall(activity, callId.ifBlank { pendingCallId })
        closeConnectionsOnly()
        pendingIncoming = null
        pendingCallId = ""
        callId = ""
        state = WhappyCallUiState()
    }

    private fun startRinging() {
        stopRinging()
        ringtone = runCatching {
            RingtoneManager.getRingtone(activity, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.isLooping = true
                it.play()
            }
        }.getOrNull()
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    private fun startRingback() {
        stopRingback()
        mainHandler.post(ringbackLoop)
    }

    private fun stopRingback() {
        mainHandler.removeCallbacks(ringbackLoop)
        if (ringbackToneDelegate.isInitialized()) runCatching { ringbackToneDelegate.value.stopTone() }
    }

    private fun closeConnectionsOnly(keepDocumentWatch: Boolean = false) {
        stopRingback()
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
        if (Build.VERSION.SDK_INT >= 31) audioManager.clearCommunicationDevice()
        else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
    }

    fun release() {
        closeLocal()
        incomingRegistration?.remove()
        incomingRegistration = null
        if (factoryDelegate.isInitialized()) factory.dispose()
        if (eglBaseDelegate.isInitialized()) eglBase.release()
        if (ringbackToneDelegate.isInitialized()) ringbackToneDelegate.value.release()
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
    var connectedSeconds by remember(call.peerName) { mutableStateOf(0) }
    LaunchedEffect(call.status) {
        if (call.status != "Connecté") { connectedSeconds = 0; return@LaunchedEffect }
        while (true) { kotlinx.coroutines.delay(1_000); connectedSeconds += 1 }
    }
    BackHandler { if (call.incoming) controller.declineIncoming() else controller.hangUp() }
    DisposableEffect(Unit) {
        activityWindow(context)?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activityWindow(context)?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val acceptGreen = Color(0xFF22C55E)
    val declineRed = Color(0xFFEF4444)
    Box(Modifier.fillMaxSize().background(Color(BRAND_BLUE)), contentAlignment = Alignment.Center) {
        if (call.video && call.mediaReady && !call.incoming && call.error == null) {
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
            if (!call.video || !call.mediaReady || call.incoming || call.error != null) {
                UserAvatar(call.peerPhotoUrl, call.peerName, 112.dp, Modifier.clip(CircleShape))
            }
            Text(call.peerName.ifBlank { "WAPI CALL" }, Modifier.padding(top = 22.dp), color = Color.White, fontSize = 25.sp)
            if (call.incoming) Text(if (call.video) "Appel vidéo entrant" else "Appel audio entrant", Modifier.padding(top = 5.dp), color = Color.White.copy(alpha = .72f), fontSize = 13.sp)
            Text(if (connectedSeconds > 0) "%02d:%02d · Appel chiffré".format(connectedSeconds / 60, connectedSeconds % 60) else call.status, Modifier.padding(top = 8.dp), color = Color.White.copy(alpha = .8f))
            call.error?.let { Text(it, Modifier.padding(24.dp), color = Color.White, fontSize = 14.sp) }
            Spacer(Modifier.size(22.dp))
            if (call.incoming) {
                Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(onClick = controller::declineIncoming, modifier = Modifier.size(68.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = declineRed)) { Icon(Icons.Rounded.CallEnd, "Refuser", tint = Color.White) }
                        Text("Refuser", Modifier.padding(top = 7.dp), color = Color.White, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(onClick = controller::acceptIncoming, modifier = Modifier.size(68.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = acceptGreen)) { Icon(if (call.video) Icons.Rounded.Videocam else Icons.Rounded.Phone, "Décrocher", tint = Color.White) }
                        Text("Décrocher", Modifier.padding(top = 7.dp), color = Color.White, fontSize = 12.sp)
                    }
                }
            } else if (call.error != null) {
                Button(onClick = controller::dismissError, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(BRAND_BLUE))) { Text("Fermer") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WapiCallControl(if (call.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, if (call.muted) "Réactiver" else "Micro", call.muted, controller::toggleMicrophone)
                    WapiCallControl(if (call.speakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff, "Haut-parleur", call.speakerOn, controller::toggleSpeaker)
                    if (call.video) WapiCallControl(if (call.cameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, "Caméra", !call.cameraEnabled, controller::toggleCamera)
                    if (call.video) WapiCallControl(Icons.Rounded.Cameraswitch, "Retourner", false, controller::switchCamera)
                    WapiCallControl(Icons.Rounded.CallEnd, "Raccrocher", true, controller::hangUp, destructive = true)
                }
            }
        }
        if (!call.incoming && call.error == null) CircularProgressIndicator(Modifier.align(Alignment.TopStart).padding(22.dp).size(22.dp), color = Color.White, strokeWidth = 2.dp)
    }
}

@Composable
private fun WapiCallControl(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit, destructive: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = onClick, modifier = Modifier.size(52.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (destructive) Color(0xFFEF4444) else if (active) Color(0xFF38BDF8) else Color.White)) { Icon(icon, label, tint = if (destructive || active) Color.White else Color(BRAND_BLUE)) }
        Text(label, Modifier.padding(top = 6.dp), color = Color.White, fontSize = 9.sp, maxLines = 1)
    }
}

private fun activityWindow(context: Context) = (context as? ComponentActivity)?.window
