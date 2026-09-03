package com.whappy.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val BRAND_BLUE = 0xFF0066CF
private const val CALL_TAG = "WapiWebRtcCall"

private data class WapiIceConfiguration(
    val servers: List<PeerConnection.IceServer>,
    val turnConfigured: Boolean,
)

data class WhappyCallUiState(
    val visible: Boolean = false,
    val incoming: Boolean = false,
    val video: Boolean = false,
    val peerName: String = "",
    val peerPhone: String = "",
    val peerPhotoUrl: String = "",
    val status: String = "",
    val muted: Boolean = false,
    val onHold: Boolean = false,
    val cameraEnabled: Boolean = true,
    val speakerOn: Boolean = false,
    val mediaReady: Boolean = false,
    val actionPending: Boolean = false,
    val minimized: Boolean = false,
    val transportLabel: String = "Internet sécurisé",
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
    private var activeBusinessPageId = ""
    private var activeCallerName = ""
    private var activeCallerPhotoUrl = ""
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
    /** True only after the current Firestore call document exists. */
    private var callDocumentReady = false
    /** Invalidates native ICE callbacks emitted by a closed peer connection. */
    @Volatile private var signalingGeneration = 0L
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
    private var outgoingRole = false
    // WebRTC invokes its observer from a native worker thread while Firestore
    // flushing happens on a coroutine. A regular MutableList can lose an early
    // audio ICE candidate exactly when a call document is being created.
    private val queuedRemoteCandidates = CopyOnWriteArrayList<IceCandidate>()
    private val queuedLocalCandidates = CopyOnWriteArrayList<IceCandidate>()
    private var pendingPermissionAction: (() -> Unit)? = null
    private var retryPeer: WhappyMember? = null
    private var retryVideo = false
    private var turnConfigured = false
    // A successful ICE configuration is reused for the next call on this
    // process. TURN credentials are valid for one hour; the short-lived cache
    // removes the visible delay when a user calls several contacts in a row.
    private var cachedIceConfiguration: WapiIceConfiguration? = null
    private var cachedIceConfigurationAt = 0L
    // Candidate counts belong to the current peer generation only. They let us
    // distinguish a handset which cannot gather a route from a carrier NAT
    // which simply needs WAPI's own TURN relay.
    private val localCandidateCount = AtomicInteger(0)
    private val remoteCandidateCount = AtomicInteger(0)
    private var audioFocusRequest: AudioFocusRequest? = null
    private var mutedByAudioFocus = false
    private var terminalActionPending = false
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        activity.runOnUiThread {
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> if (mutedByAudioFocus) {
                    mutedByAudioFocus = false
                    localAudioTrack?.setEnabled(true)
                    state = state.copy(muted = false)
                }
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> if (localAudioTrack?.enabled() == true) {
                    mutedByAudioFocus = true
                    localAudioTrack?.setEnabled(false)
                    state = state.copy(muted = true)
                }
            }
        }
    }
    private val connectionTimeout = Runnable {
        if (state.visible && !state.incoming && state.status != "Connecté" && state.error == null) {
            showMediaFailure("La connexion audio/vidéo a dépassé le délai autorisé.")
        }
    }
    private val reconnectionTimeout = Runnable {
        if (state.visible && state.status == "Reconnexion en cours…" && state.error == null) {
            showMediaFailure("La reconnexion de l’appel a dépassé le délai autorisé.")
        }
    }

    var state by mutableStateOf(WhappyCallUiState())
        private set

    init {
        WhappyCallEvents.bind { endedCallId ->
            activity.runOnUiThread {
                if (endedCallId == callId || endedCallId == pendingCallId) closeLocal()
            }
        }
    }

    private val permissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val allowed = grants.values.all { it }
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (allowed) action?.invoke()
        else {
            terminalActionPending = false
            state = state.copy(
                visible = true,
                actionPending = false,
                status = "Autorisation requise",
                error = if (retryVideo) {
                    "Autorisez le microphone et la caméra pour passer un appel vidéo."
                } else {
                    "Autorisez le microphone pour passer un appel audio."
                },
            )
        }
    }

    fun bindUser(userId: String?) {
        val next = userId.orEmpty()
        if (next == boundUserId) return
        incomingRegistration?.remove()
        incomingRegistration = null
        boundUserId = next
        if (next.isBlank()) return
        // Warm the authenticated TURN/STUN configuration before the user taps
        // Call. The call screen and remote ringing must never wait on a cold
        // Cloud Function when WAPI has already been open for a few seconds.
        activity.lifecycleScope.launch { loadIceServers() }
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
                    peerName = recent.getString("callerName") ?: "Contact WAPI",
                    peerPhotoUrl = recent.getString("callerPhotoUrl").orEmpty(),
                    status = "Sonnerie…",
                )
                // Foreground and background use one presentation path each.
                // Showing a full-screen system notification while the native
                // overlay is already visible produced two call surfaces and
                // delegated ringing to a possibly muted Android channel.
                if (WapiPresence.isForeground) {
                    WhappyNotifications.cancelCall(activity, recent.id)
                    startRinging()
                } else {
                    val notificationRings = WhappyNotifications.showIncomingCall(
                        context = activity,
                        callId = recent.id,
                        callerName = recent.getString("callerName") ?: "Contact WAPI",
                        callerPhotoUrl = recent.getString("callerPhotoUrl").orEmpty(),
                        video = recent.getBoolean("video") == true,
                    )
                    if (!notificationRings) startRinging()
                }
                watchCallDocument(recent.id)
                // The call must start ringing immediately. If an older client
                // created the call without embedding the avatar URL, hydrate
                // the photo in the background instead of delaying the ring.
                val callerId = recent.getString("callerId").orEmpty()
                if (recent.getString("callerPhotoUrl").isNullOrBlank() && callerId.isNotBlank()) {
                    activity.lifecycleScope.launch {
                        val photo = runCatching {
                            db.collection("users").document(callerId).get().await().getString("photoUrl").orEmpty()
                        }.getOrDefault("")
                        if (photo.isNotBlank() && pendingCallId == recent.id && state.visible) {
                            state = state.copy(peerPhotoUrl = photo.take(2_000))
                        }
                    }
                }
            }
    }

    fun setActiveIdentity(businessPageId: String, displayName: String, photoUrl: String) {
        activeBusinessPageId = businessPageId.trim()
        activeCallerName = displayName.trim()
        activeCallerPhotoUrl = photoUrl.trim()
        WapiCallHistory.setActiveScope(activity, activeBusinessPageId)
    }

    fun startByPhone(phone: String, video: Boolean) {
        val value = phone.trim()
        if (value.isBlank()) {
            state = WhappyCallUiState(visible = true, video = video, status = "Numéro requis", error = "Entrez un numéro WAPI complet.")
            return
        }
        activity.lifecycleScope.launch {
            state = WhappyCallUiState(visible = true, video = video, peerPhone = value, status = "Recherche du compte WAPI…")
            runCatching { withContext(Dispatchers.IO) { repository.findUserByPhone(value) } }
                .onSuccess { peer ->
                    val currentId = auth.currentUser?.uid
                    when {
                        peer == null -> state = state.copy(status = "Compte introuvable", error = "Ce numéro n’est pas encore inscrit sur WAPI.")
                        peer.uid == currentId -> state = state.copy(status = "Votre numéro", error = "Vous ne pouvez pas vous appeler vous-même.")
                        else -> start(peer, video)
                    }
                }
                .onFailure { state = state.copy(status = "Recherche impossible", error = "Vérifiez votre connexion puis réessayez.") }
        }
    }

    fun start(peer: WhappyMember, video: Boolean) {
        retryPeer = peer
        retryVideo = video
        // Render the call surface before permission prompts, TURN lookup and
        // SDP negotiation. The user sees the contact and can cancel at once.
        state = WhappyCallUiState(
            visible = true,
            video = video,
            peerName = peer.displayName,
            peerPhone = peer.phoneNumber,
            peerPhotoUrl = peer.photoUrl,
            status = "Ouverture de l’appel…",
        )
        withCallPermissions(video) {
            activity.lifecycleScope.launch {
                beginOutgoing(peer, video)
            }
        }
    }

    fun acceptIncoming(requestedCallId: String? = null) {
        if (terminalActionPending) return
        val requestedId = requestedCallId.orEmpty().ifBlank { pendingCallId }
        val available = pendingIncoming?.takeIf { requestedId.isBlank() || it.id == requestedId }
        if (available != null) {
            prepareIncomingAcceptance(available)
            return
        }
        if (requestedId.isBlank()) {
            state = state.copy(status = "Appel indisponible", error = "Cet appel n’est plus disponible.")
            return
        }
        terminalActionPending = true
        pendingCallId = requestedId
        state = state.copy(visible = true, incoming = true, actionPending = true, status = "Ouverture de l’appel…", error = null)
        activity.lifecycleScope.launch {
            val incoming = runCatching { db.collection("calls").document(requestedId).get().await() }.getOrNull()
            val valid = incoming?.takeIf { it.exists() && it.getString("status") == "ringing" }
            if (valid == null) {
                terminalActionPending = false
                state = state.copy(actionPending = false, status = "Appel terminé", error = "Cet appel a déjà été pris ou interrompu.")
            } else {
                terminalActionPending = false
                pendingIncoming = valid
                prepareIncomingAcceptance(valid)
            }
        }
    }

    private fun prepareIncomingAcceptance(incoming: DocumentSnapshot) {
        if (terminalActionPending) return
        terminalActionPending = true
        pendingIncoming = incoming
        pendingCallId = incoming.id
        state = state.copy(
            visible = true,
            incoming = true,
            video = incoming.getBoolean("video") == true,
            peerName = incoming.getString("callerName") ?: state.peerName.ifBlank { "Contact WAPI" },
            peerPhotoUrl = incoming.getString("callerPhotoUrl").orEmpty(),
            actionPending = true,
            status = "Connexion à l’appel…",
            error = null,
        )
        retryPeer = WhappyMember(
            uid = incoming.getString("callerId").orEmpty(),
            displayName = incoming.getString("callerName") ?: state.peerName.ifBlank { "Contact WAPI" },
            phoneNumber = state.peerPhone,
            photoUrl = incoming.getString("callerPhotoUrl").orEmpty(),
        )
        retryVideo = incoming.getBoolean("video") == true
        stopRinging()
        WhappyNotifications.cancelCall(activity, incoming.id)
        WapiCallHistory.record(
            activity,
            state.peerName,
            state.peerPhone,
            incoming.getBoolean("video") == true,
            "incoming",
            incoming.getString("callerId").orEmpty(),
            incoming.getString("callerPhotoUrl").orEmpty(),
            incoming.getString("calleeBusinessPageId").orEmpty(),
        )
        withCallPermissions(incoming.getBoolean("video") == true) {
            activity.lifecycleScope.launch {
                beginIncoming(incoming)
                terminalActionPending = false
            }
        }
    }

    fun declineIncoming() {
        val id = pendingCallId
        WapiCallHistory.record(
            activity,
            state.peerName,
            state.peerPhone,
            state.video,
            "missed",
            pendingIncoming?.getString("callerId").orEmpty(),
            state.peerPhotoUrl,
            pendingIncoming?.getString("calleeBusinessPageId").orEmpty(),
        )
        closeLocal()
        activity.lifecycleScope.launch {
            if (id.isNotBlank()) runCatching { db.collection("calls").document(id).update(mapOf("status" to "declined", "updatedAt" to FieldValue.serverTimestamp())).await() }
        }
    }

    fun hangUp() {
        val id = callId.ifBlank { pendingCallId }
        // Never trap the user behind a slow Firestore acknowledgement.
        closeLocal()
        activity.lifecycleScope.launch {
            if (id.isNotBlank()) runCatching { db.collection("calls").document(id).update(mapOf("status" to "ended", "updatedAt" to FieldValue.serverTimestamp())).await() }
        }
    }

    /** Keeps the native WebRTC session alive while the person uses WAPI. */
    fun minimize() {
        if (!state.visible || state.incoming || state.error != null) return
        state = state.copy(minimized = true)
    }

    fun restore() {
        if (state.visible) state = state.copy(minimized = false)
    }

    fun dismissError() = closeLocal()

    fun retryCall() {
        val peer = retryPeer ?: run { closeLocal(); return }
        val previousId = callId.ifBlank { pendingCallId }
        withCallPermissions(retryVideo) {
            activity.lifecycleScope.launch {
                if (previousId.isNotBlank()) {
                    runCatching {
                        db.collection("calls").document(previousId)
                            .update(mapOf("status" to "ended", "updatedAt" to FieldValue.serverTimestamp()))
                            .await()
                    }
                }
                beginOutgoing(peer, retryVideo)
            }
        }
    }

    fun toggleMicrophone() {
        if (state.onHold) return
        val enabled = !(localAudioTrack?.enabled() ?: true)
        mutedByAudioFocus = false
        localAudioTrack?.setEnabled(enabled)
        state = state.copy(muted = !enabled)
    }

    fun toggleCamera() {
        val enabled = !(localVideoTrack?.enabled() ?: true)
        localVideoTrack?.setEnabled(enabled)
        state = state.copy(cameraEnabled = enabled)
    }

    fun toggleSpeaker() {
        val speakerOn = routeCallAudio(preferSpeaker = !state.speakerOn)
        state = state.copy(speakerOn = speakerOn)
    }

    fun toggleHold() {
        val next = !state.onHold
        localAudioTrack?.setEnabled(if (next) false else !state.muted)
        localVideoTrack?.setEnabled(if (next) false else state.cameraEnabled)
        state = state.copy(onHold = next)
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
        // A retry must never reuse the previous document for early ICE.
        callId = ""
        pendingCallId = ""
        callDocumentReady = false
        outgoingRole = true
        state = WhappyCallUiState(visible = true, video = video, peerName = peer.displayName, peerPhone = peer.phoneNumber, peerPhotoUrl = peer.photoUrl, status = "Ouverture de l’appel…")
        WapiCallHistory.record(activity, peer.displayName, peer.phoneNumber, video, "outgoing", peer.uid, peer.photoUrl, activeBusinessPageId)
        runCatching {
            preparePeer(video, "callerCandidates")
            state = state.copy(mediaReady = true)
            val offer = peerConnection!!.createOfferAwait()
            peerConnection!!.setLocalDescriptionAwait(offer)
            val reference = db.collection("calls").document()
            callId = reference.id
            val personalPhotoUrl = runCatching {
                db.collection("users").document(current.uid).get().await().getString("photoUrl").orEmpty()
            }.getOrDefault(current.photoUrl?.toString().orEmpty())
            val callerName = activeCallerName.ifBlank { current.displayName ?: "Contact WAPI" }
            val callerPhotoUrl = if (activeBusinessPageId.isNotBlank()) activeCallerPhotoUrl else activeCallerPhotoUrl.ifBlank { personalPhotoUrl }
            reference.set(
                mapOf(
                    "callerId" to current.uid,
                    "calleeId" to peer.uid,
                    "calleeBusinessPageId" to peer.businessPageId,
                    "callerName" to callerName,
                    "callerPhotoUrl" to callerPhotoUrl.take(2_000),
                    "callerProfileType" to if (activeBusinessPageId.isBlank()) "personal" else "business",
                    "callerBusinessPageId" to activeBusinessPageId,
                    "calleeName" to peer.displayName,
                    "video" to video,
                    "status" to "ringing",
                    "offer" to mapOf("type" to "offer", "sdp" to offer.description),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            callDocumentReady = true
            queuedLocalCandidates.toList().forEach { addCandidate(reference.id, "callerCandidates", it) }
            queuedLocalCandidates.clear()
            watchRemoteCandidates(reference.id, "calleeCandidates")
            watchCallDocument(reference.id)
            state = state.copy(status = "Sonnerie…")
            startRingback()
        }.onFailure { failure ->
            Log.e(CALL_TAG, "Outgoing call setup failed", failure)
            failCall(callSetupMessage(failure, "L’appel WAPI n’a pas pu démarrer."))
        }
    }

    private suspend fun beginIncoming(incoming: DocumentSnapshot) {
        closeConnectionsOnly(keepDocumentWatch = true)
        outgoingRole = false
        callId = incoming.id
        pendingCallId = incoming.id
        // The incoming document already exists, so callee ICE can be written
        // immediately without being mistaken for a candidate of an old call.
        callDocumentReady = true
        val video = incoming.getBoolean("video") == true
        state = state.copy(incoming = false, video = video, actionPending = false, status = "Connexion à l’appel…", error = null)
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
            state = state.copy(status = "Mise en relation…")
            scheduleConnectionTimeout()
        }.onFailure { failure ->
            Log.e(CALL_TAG, "Incoming call setup failed", failure)
            failCall(callSetupMessage(failure, "Impossible d’accepter cet appel."))
        }
    }

    private suspend fun preparePeer(video: Boolean, localCandidateCollection: String) {
        requestCallAudioFocus()
        // Never leave an audio-only call on a stale music route. This is
        // especially important on foldables which do not always expose an
        // earpiece; in that case WAPI uses the speaker deliberately.
        state = state.copy(speakerOn = routeCallAudio(preferSpeaker = video))
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        localAudioSource = factory.createAudioSource(audioConstraints)
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
        // A remote/mobile-network call must not reuse a recent STUN-only
        // prewarm result when WAPI TURN became available in the meantime.
        val ice = loadIceServers(requireRelayRefresh = true)
        turnConfigured = ice.turnConfigured
        state = state.copy(transportLabel = if (ice.turnConfigured) "Relais Internet WAPI prêt" else "Connexion directe sécurisée")
        val rtcConfiguration = WapiIceDefaults.rtcConfiguration(ice.servers)
        val peerGeneration = signalingGeneration
        peerConnection = factory.createPeerConnection(rtcConfiguration, peerObserver(localCandidateCollection, peerGeneration)) ?: error("peer")
        peerConnection!!.addTrack(localAudioTrack, listOf("whappy-stream"))
        localVideoTrack?.let { peerConnection!!.addTrack(it, listOf("whappy-stream")) }
    }

    private fun peerObserver(localCandidateCollection: String, peerGeneration: Long) = object : PeerConnection.Observer {
        private fun isCurrentPeer() = WapiCallSignaling.isCurrentGeneration(peerGeneration, signalingGeneration)

        override fun onIceCandidate(candidate: IceCandidate) {
            if (!isCurrentPeer()) return
            localCandidateCount.incrementAndGet()
            val id = callId.ifBlank { pendingCallId }
            if (WapiCallSignaling.shouldQueueLocalCandidate(callDocumentReady, id)) {
                queuedLocalCandidates += candidate
            } else activity.lifecycleScope.launch {
                if (!WapiCallSignaling.isCurrentGeneration(peerGeneration, signalingGeneration)) return@launch
                runCatching { addCandidate(id, localCandidateCollection, candidate) }
                    .onFailure { failure -> reportCandidateFailure(id, failure) }
            }
        }
        override fun onTrack(transceiver: org.webrtc.RtpTransceiver) {
            if (!isCurrentPeer()) return
            (transceiver.receiver.track() as? VideoTrack)?.let { track -> remoteVideoTrack = track; remoteRenderer?.let(track::addSink) }
        }
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
            if (!isCurrentPeer()) return
            (receiver.track() as? VideoTrack)?.let { track -> remoteVideoTrack = track; remoteRenderer?.let(track::addSink) }
        }
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            if (!isCurrentPeer()) return
            when (newState) {
                PeerConnection.PeerConnectionState.CONNECTED -> activity.runOnUiThread { markMediaConnected() }
                PeerConnection.PeerConnectionState.FAILED -> activity.runOnUiThread { showMediaFailure("L’appel n’a pas pu établir la connexion.") }
                PeerConnection.PeerConnectionState.CLOSED -> activity.runOnUiThread { if (state.visible && state.error == null) state = state.copy(status = "Appel terminé") }
                else -> Unit
            }
        }
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            if (!isCurrentPeer()) return
            when (state) {
                PeerConnection.IceConnectionState.CHECKING -> activity.runOnUiThread {
                    // Native WebRTC may deliver a queued CHECKING event after
                    // CONNECTED. Never let that stale progress event turn a
                    // healthy audio call back into an endless loader.
                    if (WapiCallSignaling.shouldShowCheckingStatus(
                            this@WhappyCallController.state.visible,
                            this@WhappyCallController.state.status,
                        )
                    ) {
                        this@WhappyCallController.state = this@WhappyCallController.state.copy(status = "Mise en relation…")
                    }
                }
                PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> activity.runOnUiThread { markMediaConnected() }
                PeerConnection.IceConnectionState.DISCONNECTED -> activity.runOnUiThread {
                    this@WhappyCallController.state = this@WhappyCallController.state.copy(status = "Reconnexion en cours…")
                    scheduleReconnectionTimeout()
                }
                PeerConnection.IceConnectionState.FAILED -> activity.runOnUiThread { showMediaFailure("La connexion de l’appel a été interrompue.") }
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

    private suspend fun loadIceServers(requireRelayRefresh: Boolean = false): WapiIceConfiguration {
        val fallback = WapiIceDefaults.fallbackServers()
        val now = System.currentTimeMillis()
        cachedIceConfiguration?.let { cached ->
            val cacheLifetime = if (cached.turnConfigured) 45 * 60_000L else 8_000L
            if (now - cachedIceConfigurationAt < cacheLifetime && (!requireRelayRefresh || cached.turnConfigured)) return cached
        }
        suspend fun requestConfiguration(timeoutMs: Long): WapiIceConfiguration? = runCatching {
            val result = withTimeoutOrNull(timeoutMs) {
                FirebaseFunctions.getInstance("europe-west1")
                    .getHttpsCallable("getWebRtcIceServers")
                    .call()
                    .await()
            } ?: return@runCatching null
            val payload = result.data as? Map<*, *> ?: return@runCatching null
            val rawServers = payload["iceServers"] as? List<*> ?: return@runCatching null
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
            val merged = WapiIceDefaults.merge(servers)
            WapiIceConfiguration(
                servers = merged,
                turnConfigured = payload["turnConfigured"] == true || merged.any { server -> server.urls.any { it.startsWith("turn:") || it.startsWith("turns:") } },
            )
        }.onFailure { failure -> Log.w(CALL_TAG, "TURN configuration lookup failed", failure) }.getOrNull()

        var configuration = requestConfiguration(WapiIceDefaults.CONFIGURATION_TIMEOUT_MS)
        if (requireRelayRefresh && configuration?.turnConfigured != true) {
            // One short retry handles a cold callable/temporary radio switch;
            // after that WAPI still allows direct WebRTC instead of freezing.
            delay(260L)
            configuration = requestConfiguration(4_000L) ?: configuration
        }
        val resolved = configuration ?: WapiIceConfiguration(fallback, false)
        if (!resolved.turnConfigured) Log.w(CALL_TAG, "TURN unavailable; continuing with direct ICE only")
        cachedIceConfiguration = resolved
        cachedIceConfigurationAt = System.currentTimeMillis()
        return resolved
    }

    private fun watchCallDocument(id: String) {
        registrations += db.collection("calls").document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                reportSignalingFailure("L’appel a perdu la connexion. Vérifiez Internet puis réessayez.", error)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
            val status = snapshot.getString("status") ?: return@addSnapshotListener
            if (status == "declined" || status == "ended") {
                activity.runOnUiThread { closeLocal() }
                return@addSnapshotListener
            }
            // Only the caller applies the SDP answer. The callee created that
            // answer locally and already has the caller's offer as its remote
            // description; applying its own answer as remote would close the
            // freshly accepted call with an invalid signaling state.
            if (WapiCallSignaling.shouldApplyRemoteAnswer(outgoingRole, status, answerApplied, peerConnection != null)) {
                stopRingback()
                val answer = snapshot.get("answer") as? Map<*, *> ?: return@addSnapshotListener
                answerApplied = true
                activity.lifecycleScope.launch {
                    runCatching {
                        peerConnection?.setRemoteDescriptionAwait(SessionDescription(SessionDescription.Type.ANSWER, answer["sdp"]?.toString().orEmpty()))
                        remoteDescriptionReady = true
                        flushRemoteCandidates()
                        state = state.copy(status = "Mise en relation…")
                        scheduleConnectionTimeout()
                    }.onFailure { failure ->
                        Log.e(CALL_TAG, "Remote answer rejected", failure)
                        failCall(callSetupMessage(failure, "La connexion audio/vidéo a échoué."))
                    }
                }
            }
        }
    }

    private fun watchRemoteCandidates(id: String, collection: String) {
        registrations += db.collection("calls").document(id).collection(collection).addSnapshotListener { snapshot, error ->
            if (id != callId || !state.visible) return@addSnapshotListener
            if (error != null) {
                reportSignalingFailure("L’appel ne reçoit plus les informations nécessaires. Fermez-le puis réessayez.", error)
                return@addSnapshotListener
            }
            snapshot?.documentChanges.orEmpty().filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }.forEach { change ->
                val data = change.document.data
                val sdp = data["candidate"]?.toString().orEmpty()
                if (sdp.isBlank()) return@forEach
                val candidate = IceCandidate(data["sdpMid"]?.toString(), (data["sdpMLineIndex"] as? Number)?.toInt() ?: 0, sdp)
                remoteCandidateCount.incrementAndGet()
                if (remoteDescriptionReady) {
                    if (peerConnection?.addIceCandidate(candidate) != true) {
                        reportSignalingFailure("WAPI n’a pas pu poursuivre cet appel. Fermez-le puis réessayez.", IllegalStateException("Remote ICE candidate rejected"))
                    }
                } else {
                    queuedRemoteCandidates += candidate
                }
            }
        }
    }

    private suspend fun addCandidate(id: String, collection: String, candidate: IceCandidate) {
        db.collection("calls").document(id).collection(collection).add(
            mapOf("candidate" to candidate.sdp, "sdpMid" to (candidate.sdpMid ?: "0"), "sdpMLineIndex" to candidate.sdpMLineIndex),
        ).await()
    }

    private fun reportCandidateFailure(id: String, failure: Throwable) {
        if (id != callId || !state.visible) return
        reportSignalingFailure("WAPI n’a pas pu poursuivre cet appel. Vérifiez Internet puis réessayez.", failure)
    }

    private fun reportSignalingFailure(message: String, failure: Throwable) {
        Log.e(CALL_TAG, message, failure)
        activity.runOnUiThread {
            if (state.visible && state.error == null) {
                cancelConnectionTimeout()
                cancelReconnectionTimeout()
                state = state.copy(status = "Appel interrompu", error = message)
            }
        }
    }

    private fun flushRemoteCandidates() {
        queuedRemoteCandidates.toList().forEach { candidate ->
            if (peerConnection?.addIceCandidate(candidate) != true) {
                reportSignalingFailure("WAPI n’a pas pu poursuivre cet appel. Fermez-le puis réessayez.", IllegalStateException("Queued ICE candidate rejected"))
            }
        }
        queuedRemoteCandidates.clear()
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        // Several OEM devices advertise Camera2 support but fail when WebRTC
        // opens the camera. Try Camera2 first, then the Camera1 compatibility
        // path before declaring video unavailable.
        val enumerators = buildList<CameraEnumerator> {
            if (runCatching { Camera2Enumerator.isSupported(activity) }.getOrDefault(false)) {
                add(Camera2Enumerator(activity))
            }
            add(Camera1Enumerator(true))
        }
        return enumerators.firstNotNullOfOrNull { enumerator ->
            runCatching {
                val names = enumerator.deviceNames
                (names.filter { enumerator.isFrontFacing(it) } + names.filterNot { enumerator.isFrontFacing(it) })
                    .firstNotNullOfOrNull { name -> runCatching { enumerator.createCapturer(name, null) }.getOrNull() }
            }.getOrNull()
        }
    }

    private fun failCall(message: String) {
        cancelConnectionTimeout()
        cancelReconnectionTimeout()
        state = state.copy(visible = true, status = "Connexion impossible", error = message)
        closeConnectionsOnly(keepDocumentWatch = true)
    }

    private fun markMediaConnected() {
        cancelConnectionTimeout()
        cancelReconnectionTimeout()
        state = state.copy(status = "Connecté", error = null)
    }

    private fun showMediaFailure(detail: String) {
        cancelConnectionTimeout()
        cancelReconnectionTimeout()
        val localCandidates = localCandidateCount.get()
        val remoteCandidates = remoteCandidateCount.get()
        val problem = WapiCallSignaling.classifyTransportFailure(
            turnConfigured = turnConfigured,
            localCandidateCount = localCandidates,
            remoteCandidateCount = remoteCandidates,
        )
        Log.w(CALL_TAG, "$detail turnConfigured=$turnConfigured localCandidates=$localCandidates remoteCandidates=$remoteCandidates problem=$problem")
        state = state.copy(
            visible = true,
            status = "Appel interrompu",
            error = when (problem) {
                WapiCallSignaling.TransportFailure.LOCAL_ICE_UNAVAILABLE ->
                    "Cet appareil n’a pas réussi à se connecter. Désactivez le VPN si nécessaire, essayez un autre réseau, puis réessayez."
                WapiCallSignaling.TransportFailure.REMOTE_ICE_UNAVAILABLE ->
                    "L’autre appareil n’est pas encore prêt pour l’appel. Demandez à votre correspondant de rouvrir WAPI, puis réessayez."
                WapiCallSignaling.TransportFailure.DIRECT_PATH_BLOCKED ->
                    "Ce réseau empêche l’appel d’aboutir. Essayez un autre Wi-Fi ou vos données mobiles, puis réessayez."
                WapiCallSignaling.TransportFailure.RELAY_OR_NETWORK_FAILED ->
                    "La connexion a été interrompue. Vérifiez Internet puis touchez Réessayer."
            },
        )
    }

    private fun callSetupMessage(failure: Throwable, fallback: String): String = when {
        failure.message.orEmpty().contains("permission", ignoreCase = true) -> "Votre session n’autorise pas encore cet appel. Reconnectez-vous à WAPI puis réessayez."
        failure.message.orEmpty().contains("camera", ignoreCase = true) -> "La caméra n’a pas pu démarrer. Vérifiez son autorisation puis réessayez."
        failure.message.orEmpty().contains("peer", ignoreCase = true) -> "WAPI n’a pas pu initialiser la communication sur cet appareil. Fermez l’appel puis réessayez."
        else -> fallback
    }

    private fun scheduleConnectionTimeout() {
        cancelConnectionTimeout()
        mainHandler.postDelayed(connectionTimeout, 30_000L)
    }

    private fun cancelConnectionTimeout() = mainHandler.removeCallbacks(connectionTimeout)

    private fun scheduleReconnectionTimeout() {
        cancelReconnectionTimeout()
        mainHandler.postDelayed(reconnectionTimeout, 18_000L)
    }

    private fun cancelReconnectionTimeout() = mainHandler.removeCallbacks(reconnectionTimeout)

    private fun requestCallAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = request
            if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(CALL_TAG, "Audio focus was not granted; continuing with WebRTC audio route")
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(audioFocusListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    /**
     * Select a deterministic communication output instead of relying on the
     * last route chosen by another app. Returning the actual UI state keeps
     * the speaker button correct on Samsung foldables and wired headsets.
     */
    private fun routeCallAudio(preferSpeaker: Boolean): Boolean {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            runCatching { audioManager.isSpeakerphoneOn = preferSpeaker }
            return preferSpeaker
        }

        val devices = audioManager.availableCommunicationDevices
        val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val privateOutput = devices.firstOrNull {
            it.type in setOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            )
        }
        val requested = if (preferSpeaker) speaker else privateOutput
        val applied = requested?.let { device ->
            runCatching { audioManager.setCommunicationDevice(device) }.getOrDefault(false)
        } ?: false

        if (!applied && !preferSpeaker) {
            // Let Android choose its standard voice route. This is the only
            // safe fallback when an OEM does not expose an earpiece device.
            runCatching { audioManager.clearCommunicationDevice() }
        } else if (!applied && preferSpeaker) {
            // Some devices transiently hide the speaker while Bluetooth is
            // reconnecting. Clearing avoids a stale, unavailable route.
            runCatching { audioManager.clearCommunicationDevice() }
        }

        val actualType = audioManager.communicationDevice?.type
        return actualType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
            (actualType == null && preferSpeaker && applied)
    }

    private fun abandonCallAudioFocus() {
        mutedByAudioFocus = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    private fun closeLocal(playEndSound: Boolean = true) {
        val wasVisible = state.visible
        stopRinging()
        WhappyNotifications.cancelCall(activity, callId.ifBlank { pendingCallId })
        closeConnectionsOnly()
        pendingIncoming = null
        pendingCallId = ""
        callId = ""
        callDocumentReady = false
        terminalActionPending = false
        state = WhappyCallUiState()
        if (playEndSound && wasVisible) WhappySounds.callEnded(activity)
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
        // Set this before closing native resources: WebRTC may report a final
        // candidate asynchronously while dispose() is in progress.
        signalingGeneration += 1
        callDocumentReady = false
        cancelConnectionTimeout()
        cancelReconnectionTimeout()
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
        localCandidateCount.set(0)
        remoteCandidateCount.set(0)
        remoteDescriptionReady = false
        answerApplied = false
        outgoingRole = false
        abandonCallAudioFocus()
        audioManager.mode = AudioManager.MODE_NORMAL
        if (Build.VERSION.SDK_INT >= 31) audioManager.clearCommunicationDevice()
        else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
    }

    fun release() {
        WhappyCallEvents.bind(null)
        closeLocal(playEndSound = false)
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
    var connectedSeconds by remember(call.peerName) { mutableIntStateOf(0) }
    LaunchedEffect(call.status) {
        if (call.status != "Connecté") { connectedSeconds = 0; return@LaunchedEffect }
        while (true) { kotlinx.coroutines.delay(1_000); connectedSeconds += 1 }
    }
    if (call.minimized) {
        BackHandler(enabled = false) {}
        Surface(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
                .wapiClickable(onClick = controller::restore),
            color = Color(0xF20A2944),
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 18.dp,
        ) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(call.peerPhotoUrl, call.peerName, 42.dp, shape = RoundedCornerShape(13.dp))
                Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                    Text(call.peerName.ifBlank { "Appel WAPI" }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(if (call.status == "Connecté") "Appel en cours · toucher pour revenir" else call.status, color = Color.White.copy(alpha = .66f), fontSize = 10.sp, maxLines = 1)
                }
                FilledIconButton(onClick = controller::restore, modifier = Modifier.size(40.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = .14f))) {
                    Icon(Icons.Rounded.Phone, "Revenir à l’appel", tint = Color.White, modifier = Modifier.size(19.dp))
                }
                FilledIconButton(onClick = controller::hangUp, modifier = Modifier.padding(start = 7.dp).size(40.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFEF4444))) {
                    Icon(Icons.Rounded.CallEnd, "Raccrocher", tint = Color.White, modifier = Modifier.size(19.dp))
                }
            }
        }
        return
    }
    BackHandler { if (call.incoming) controller.declineIncoming() else controller.minimize() }
    DisposableEffect(Unit) {
        activityWindow(context)?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activityWindow(context)?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val acceptGreen = Color(0xFF22C55E)
    val declineRed = Color(0xFFEF4444)
    val connected = call.status == "Connecté"
    val callDirection = when {
        call.incoming && call.video -> "APPEL VIDÉO ENTRANT"
        call.incoming -> "APPEL AUDIO ENTRANT"
        call.video -> "APPEL VIDÉO SORTANT"
        else -> "APPEL AUDIO SORTANT"
    }
    val phaseTitle = when {
        call.error != null -> "Connexion interrompue"
        call.onHold -> "Appel en attente"
        connected -> "Communication sécurisée"
        call.incoming && call.actionPending -> "Préparation de l’appel"
        call.incoming -> "${call.peerName.ifBlank { "Votre contact" }} vous appelle"
        call.status.contains("Sonnerie", ignoreCase = true) -> "Le téléphone de votre contact sonne"
        call.status.contains("Reconnexion", ignoreCase = true) -> "Reconnexion automatique"
        call.status.contains("Mise en relation", ignoreCase = true) -> "Connexion des deux appareils"
        else -> "Préparation de la connexion"
    }
    val phaseDetail = when {
        call.error != null -> "L’appel reste ouvert : réessayez ou fermez proprement la session."
        call.onHold -> "Votre micro et votre caméra sont temporairement suspendus"
        connected -> "Audio ${if (call.video) "et vidéo " else ""}transmis en temps réel"
        call.incoming && !call.actionPending -> "Choisissez clairement Accepter ou Refuser"
        call.status.contains("Sonnerie", ignoreCase = true) -> "En attente de la réponse de ${call.peerName.ifBlank { "votre contact" }}"
        call.status.contains("Reconnexion", ignoreCase = true) -> "WAPI recherche le meilleur chemin Internet disponible"
        else -> "WAPI sécurise le micro${if (call.video) ", la caméra" else ""} et le réseau"
    }
    val pulseTransition = rememberInfiniteTransition(label = "wapi-call-pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = .96f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(1_050, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "avatar-pulse",
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF149FE6), Color(BRAND_BLUE), Color(0xFF041A31)),
                radius = 1_350f,
            ),
        ),
    ) {
        if (!call.incoming && call.error == null) {
            FilledIconButton(
                onClick = controller::minimize,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 15.dp).size(42.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = .28f)),
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Réduire l’appel", tint = Color.White)
            }
        }
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
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 15.dp),
            color = Color.Black.copy(alpha = .28f),
            shape = RoundedCornerShape(100.dp),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(callDirection, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("🔒 Session WAPI chiffrée", color = Color.White.copy(alpha = .72f), fontSize = 9.sp)
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (!call.video || !call.mediaReady || call.incoming || call.error != null) {
                Box(
                    Modifier.size(158.dp).graphicsLayer {
                        scaleX = if (call.incoming && !call.actionPending) pulse else 1f
                        scaleY = if (call.incoming && !call.actionPending) pulse else 1f
                    }.clip(CircleShape).background(Color.White.copy(alpha = .12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    UserAvatar(call.peerPhotoUrl, call.peerName, 132.dp, Modifier.clip(CircleShape))
                }
            }
            Text(call.peerName.ifBlank { "Contact WAPI" }, Modifier.padding(top = 20.dp), color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
            if (call.peerPhone.isNotBlank()) Text(call.peerPhone, Modifier.padding(top = 4.dp), color = Color.White.copy(alpha = .66f), fontSize = 12.sp)
            Surface(
                Modifier.padding(horizontal = 24.dp, vertical = 16.dp).fillMaxWidth(),
                color = Color.Black.copy(alpha = .24f),
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (connected) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(acceptGreen))
                    } else if (call.error == null) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.CallEnd, null, tint = declineRed, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            if (connectedSeconds > 0) "%02d:%02d · %s".format(connectedSeconds / 60, connectedSeconds % 60, phaseTitle) else phaseTitle,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(phaseDetail, Modifier.padding(top = 3.dp), color = Color.White.copy(alpha = .70f), fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
            call.error?.let {
                Surface(Modifier.padding(horizontal = 28.dp), color = Color(0x33FFFFFF), shape = RoundedCornerShape(18.dp)) {
                    Text(it, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = Color.White, fontSize = 13.sp)
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 18.dp),
            color = Color(0xCC071D31),
            shape = RoundedCornerShape(30.dp),
            shadowElevation = 24.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                when {
                    call.incoming -> "Répondez à l’appel"
                    call.error != null -> "La session peut être relancée"
                    connected -> "Commandes de l’appel"
                    else -> "Commandes disponibles pendant la connexion"
                },
                color = Color.White.copy(alpha = .68f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .5.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
                if (call.incoming) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(onClick = controller::declineIncoming, modifier = Modifier.size(70.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = declineRed)) { Icon(Icons.Rounded.CallEnd, "Refuser", tint = Color.White, modifier = Modifier.size(30.dp)) }
                        Text("Refuser", Modifier.padding(top = 7.dp), color = Color.White, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(onClick = { controller.acceptIncoming() }, enabled = !call.actionPending, modifier = Modifier.size(68.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = acceptGreen)) { Icon(if (call.video) Icons.Rounded.Videocam else Icons.Rounded.Phone, "Décrocher", tint = Color.White) }
                        Text(if (call.actionPending) "Connexion…" else "Décrocher", Modifier.padding(top = 7.dp), color = Color.White, fontSize = 12.sp)
                    }
                    }
                } else if (call.error != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = controller::retryCall, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(BRAND_BLUE))) { Text("Réessayer") }
                    Button(onClick = controller::dismissError, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .18f), contentColor = Color.White)) { Text("Fermer") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        WapiCallControl(if (call.onHold) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (call.onHold) "Reprendre" else "Attente", call.onHold, controller::toggleHold)
                        WapiCallControl(if (call.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, if (call.muted) "Réactiver" else "Micro", call.muted, controller::toggleMicrophone)
                        WapiCallControl(if (call.speakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff, "Haut-parleur", call.speakerOn, controller::toggleSpeaker)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        if (call.video) WapiCallControl(if (call.cameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, "Caméra", !call.cameraEnabled, controller::toggleCamera)
                        if (call.video) WapiCallControl(Icons.Rounded.Cameraswitch, "Retourner", false, controller::switchCamera)
                        WapiCallControl(Icons.Rounded.CallEnd, "Raccrocher", true, controller::hangUp, destructive = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun WapiCallControl(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit, destructive: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = onClick, modifier = Modifier.size(50.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (destructive) Color(0xFFEF4444) else if (active) Color(0xFF38BDF8) else Color.White)) { Icon(icon, label, tint = if (destructive || active) Color.White else Color(BRAND_BLUE)) }
        Text(label, Modifier.padding(top = 6.dp), color = Color.White, fontSize = 8.sp, maxLines = 1)
    }
}

private fun activityWindow(context: Context) = (context as? ComponentActivity)?.window
