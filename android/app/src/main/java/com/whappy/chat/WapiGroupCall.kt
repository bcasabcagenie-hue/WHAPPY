package com.whappy.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import livekit.org.webrtc.SurfaceViewRenderer

private data class WapiGroupCallCredentials(
    val callId: String,
    val groupName: String,
    val video: Boolean,
    val serverUrl: String,
    val participantToken: String,
)

private suspend fun groupCallCredentials(groupId: String, callId: String, video: Boolean): WapiGroupCallCredentials {
    val callable = if (callId.isBlank()) "createGroupCallSession" else "joinGroupCallSession"
    val payload = if (callId.isBlank()) mapOf("groupId" to groupId, "video" to video) else mapOf("callId" to callId)
    val result = FirebaseFunctions.getInstance("europe-west1").getHttpsCallable(callable).call(payload).await()
    @Suppress("UNCHECKED_CAST")
    val value = result.data as? Map<String, Any?> ?: error("Réponse d’appel WAPI invalide.")
    val url = value["serverUrl"]?.toString().orEmpty()
    val token = value["participantToken"]?.toString().orEmpty()
    require(url.startsWith("wss://") && token.isNotBlank()) { "Serveur WebRTC WAPI indisponible." }
    return WapiGroupCallCredentials(
        callId = value["callId"]?.toString().orEmpty(),
        groupName = value["groupName"]?.toString()?.takeIf(String::isNotBlank) ?: "Groupe WAPI",
        video = value["video"] == true,
        serverUrl = url,
        participantToken = token,
    )
}

/** Native multi-party call UI. Every participant publishes through the self-hosted WebRTC SFU. */
@Composable
internal fun WapiGroupCallDialog(
    group: WhappyConversation,
    invitation: WapiGroupCallInvitation?,
    requestedVideo: Boolean,
    canEnd: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val functions = remember { FirebaseFunctions.getInstance("europe-west1") }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val room = remember(group.id, invitation?.callId) { LiveKit.create(context.applicationContext, RoomOptions(adaptiveStream = true, dynacast = true)) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var credentials by remember { mutableStateOf<WapiGroupCallCredentials?>(null) }
    var connecting by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Préparation de l’appel sécurisé…") }
    var microphoneEnabled by remember { mutableStateOf(true) }
    var cameraEnabled by remember { mutableStateOf(requestedVideo || invitation?.video == true) }
    var speakerOn by remember { mutableStateOf(requestedVideo || invitation?.video == true) }
    var remoteVideo by remember { mutableStateOf<VideoTrack?>(null) }
    var remoteRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var localRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var participantCount by remember { mutableIntStateOf(invitation?.participantCount ?: 0) }
    var eventsJob by remember { mutableStateOf<Job?>(null) }
    var callListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                (!(requestedVideo || invitation?.video == true) || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED),
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        permissionsGranted = grants.values.all { it }
        if (!permissionsGranted) {
            connecting = false
            error = "Autorisez le microphone${if (requestedVideo || invitation?.video == true) " et la caméra" else ""} pour rejoindre l’appel."
        }
    }

    fun localVideo(): LocalVideoTrack? = room.localParticipant.videoTrackPublications.firstOrNull()?.second as? LocalVideoTrack

    fun routeAudio(enabled: Boolean) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            if (enabled) audioManager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let(audioManager::setCommunicationDevice)
            else audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        }
        speakerOn = enabled
    }

    fun close(end: Boolean) {
        scope.launch {
            credentials?.callId?.takeIf(String::isNotBlank)?.let { callId ->
                runCatching {
                    functions.getHttpsCallable(if (end) "endGroupCallSession" else "setGroupCallPresence")
                        .call(if (end) mapOf("callId" to callId) else mapOf("callId" to callId, "active" to false))
                        .await()
                }
            }
            room.disconnect()
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(
                if (requestedVideo || invitation?.video == true) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                else arrayOf(Manifest.permission.RECORD_AUDIO),
            )
        }
    }

    LaunchedEffect(permissionsGranted) {
        if (!permissionsGranted) return@LaunchedEffect
        try {
            eventsJob = launch {
                room.events.events.collect { event ->
                    when (event) {
                        is RoomEvent.TrackSubscribed -> if (event.track is VideoTrack) remoteVideo = event.track as VideoTrack
                        is RoomEvent.Reconnecting -> status = "Reconnexion…"
                        is RoomEvent.Reconnected -> status = "Connecté"
                        is RoomEvent.Disconnected -> status = "Appel terminé"
                        else -> Unit
                    }
                }
            }
            val joined = groupCallCredentials(group.id, invitation?.callId.orEmpty(), requestedVideo)
            credentials = joined
            cameraEnabled = joined.video
            routeAudio(joined.video)
            room.prepareConnection(joined.serverUrl, joined.participantToken)
            room.connect(joined.serverUrl, joined.participantToken)
            microphoneEnabled = room.localParticipant.setMicrophoneEnabled(true)
            cameraEnabled = if (joined.video) room.localParticipant.setCameraEnabled(true) else false
            functions.getHttpsCallable("setGroupCallPresence").call(mapOf("callId" to joined.callId, "active" to true)).await()
            callListener = firestore.collection("groupCallSessions").document(joined.callId).addSnapshotListener { snapshot, _ ->
                participantCount = (snapshot?.getLong("participantCount") ?: participantCount.toLong()).toInt()
                if (snapshot?.getString("status") == "ended") {
                    status = "Appel terminé"
                    room.disconnect()
                }
            }
            connecting = false
            status = "Connecté"
        } catch (failure: Throwable) {
            connecting = false
            error = failure.localizedMessage ?: "L’appel de groupe n’a pas pu démarrer."
            room.disconnect()
        }
    }

    DisposableEffect(remoteVideo, remoteRenderer) {
        val track = remoteVideo
        val renderer = remoteRenderer
        if (track != null && renderer != null) track.addRenderer(renderer)
        onDispose { if (track != null && renderer != null) track.removeRenderer(renderer) }
    }
    DisposableEffect(credentials?.callId) {
        onDispose {
            callListener?.remove()
            eventsJob?.cancel()
            remoteVideo?.let { track -> remoteRenderer?.let(track::removeRenderer) }
            localVideo()?.let { track -> localRenderer?.let(track::removeRenderer) }
            room.disconnect()
            room.release()
            routeAudio(false)
        }
    }

    Dialog(onDismissRequest = { close(false) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF071827)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0066CF), Color(0xFF071827))))) {
                if (credentials?.video == true && remoteVideo != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { rendererContext -> SurfaceViewRenderer(rendererContext).also { renderer -> room.initVideoRenderer(renderer); remoteRenderer = renderer } },
                    )
                }
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { close(false) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Quitter", tint = Color.White) }
                        Column(Modifier.weight(1f)) {
                            Text(credentials?.groupName ?: group.peer.displayName, color = Color.White, fontSize = 18.sp)
                            Text("$status · $participantCount participant${if (participantCount > 1) "s" else ""}", color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
                        }
                        credentials?.callId?.takeIf(String::isNotBlank)?.let { callId ->
                            IconButton(onClick = {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Rejoignez l’appel ${group.peer.displayName} sur WAPI\nwhappy://group-call/$callId")
                                }, "Partager le lien d’appel"))
                            }) { Icon(Icons.Rounded.Share, "Inviter", tint = Color.White) }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (credentials?.video != true || remoteVideo == null) {
                        Column(Modifier.align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(112.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Groups, null, tint = Color.White, modifier = Modifier.size(52.dp)) }
                            Text(if (connecting) "Connexion des participants…" else "Appel audio de groupe", Modifier.padding(top = 14.dp), color = Color.White, fontSize = 18.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (credentials?.video == true && cameraEnabled) {
                        AndroidView(
                            modifier = Modifier.align(Alignment.End).size(104.dp, 150.dp).clip(RoundedCornerShape(18.dp)),
                            factory = { rendererContext -> SurfaceViewRenderer(rendererContext).also { renderer -> room.initVideoRenderer(renderer); renderer.setMirror(true); localRenderer = renderer; localVideo()?.addRenderer(renderer) } },
                        )
                    }
                    Row(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GroupCallControl(if (microphoneEnabled) Icons.Rounded.Mic else Icons.Rounded.MicOff, "Micro", microphoneEnabled) { scope.launch { microphoneEnabled = room.localParticipant.setMicrophoneEnabled(!microphoneEnabled) } }
                        GroupCallControl(if (speakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff, "Haut-parleur", speakerOn) { routeAudio(!speakerOn) }
                        if (credentials?.video == true) GroupCallControl(if (cameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, "Caméra", cameraEnabled) { scope.launch { cameraEnabled = room.localParticipant.setCameraEnabled(!cameraEnabled) } }
                        if (credentials?.video == true) GroupCallControl(Icons.Rounded.Cameraswitch, "Retourner", true) { localVideo()?.switchCamera() }
                        GroupCallControl(Icons.Rounded.CallEnd, "Quitter", false, destructive = true) { close(false) }
                    }
                    if (canEnd && credentials?.callId?.isNotBlank() == true) Button(onClick = { close(true) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Terminer pour tous") }
                }
                if (connecting) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                error?.let { message ->
                    Surface(Modifier.align(Alignment.Center).padding(28.dp), shape = RoundedCornerShape(24.dp), color = Color.White) {
                        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Appel indisponible", color = Color(0xFF0B2239), fontSize = 20.sp)
                            Text(message, Modifier.padding(vertical = 12.dp), color = Color(0xFF627286))
                            Button(onClick = { close(false) }) { Text("Fermer") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCallControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (destructive) Color(0xFFE43D4F) else if (active) WhappyBlue else Color.White.copy(alpha = .18f),
                contentColor = Color.White,
            ),
        ) { Icon(icon, label) }
        Text(label, Modifier.padding(top = 4.dp), color = Color.White, fontSize = 8.sp, maxLines = 1)
    }
}
