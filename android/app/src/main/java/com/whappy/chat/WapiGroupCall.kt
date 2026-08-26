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
import androidx.compose.material.icons.rounded.Radio
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
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer

/** Native P2P mesh UI for group audio, video and group-radio sessions. */
@Composable
internal fun WapiGroupCallDialog(
    group: WhappyConversation,
    invitation: WapiGroupCallInvitation?,
    requestedVideo: Boolean,
    canEnd: Boolean,
    radioMode: Boolean = false,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val engine = remember(group.id, invitation?.callId) { WapiGroupRtcEngine(context, scope) }
    var connecting by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Préparation de l’appel…") }
    var error by remember { mutableStateOf<String?>(null) }
    var microphoneEnabled by remember { mutableStateOf(true) }
    var cameraEnabled by remember { mutableStateOf(requestedVideo || invitation?.video == true) }
    var speakerOn by remember { mutableStateOf(requestedVideo || invitation?.video == true) }
    var participantCount by remember { mutableIntStateOf(invitation?.participantCount ?: 1) }
    var sessionListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var localRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var remoteRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    val wantsVideo = (invitation?.video ?: requestedVideo) && !radioMode
    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                (!wantsVideo || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED),
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        permissionsGranted = grants.values.all { it }
        if (!permissionsGranted) {
            connecting = false
            error = "Autorisez le microphone${if (wantsVideo) " et la caméra" else ""} pour rejoindre l’appel."
        }
    }

    fun routeAudio(enabled: Boolean) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            if (enabled) audioManager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let(audioManager::setCommunicationDevice)
            else audioManager.clearCommunicationDevice()
        } else @Suppress("DEPRECATION") run { audioManager.isSpeakerphoneOn = enabled }
        speakerOn = enabled
    }

    fun close(endForEveryone: Boolean) {
        scope.launch {
            sessionListener?.remove()
            engine.leave(endForEveryone)
            routeAudio(false)
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) permissionLauncher.launch(if (wantsVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO))
    }

    LaunchedEffect(permissionsGranted) {
        if (!permissionsGranted) return@LaunchedEffect
        engine.onStateChanged = { nextStatus, nextError -> status = nextStatus; if (nextError != null) error = nextError }
        runCatching { engine.start(group.id, group.source, invitation?.callId.orEmpty(), wantsVideo) }
            .onSuccess {
                microphoneEnabled = engine.microphoneEnabled
                cameraEnabled = engine.cameraEnabled
                routeAudio(wantsVideo || radioMode)
                sessionListener = FirebaseFirestore.getInstance().collection("groupCallSessions").document(engine.callId).addSnapshotListener { snapshot, failure ->
                    if (failure != null) error = "L’état de l’appel ne peut plus être synchronisé."
                    participantCount = (snapshot?.getLong("participantCount") ?: participantCount.toLong()).toInt()
                    if (snapshot?.getString("status") == "ended") { status = "Appel terminé"; engine.close() }
                }
                connecting = false
                status = if (radioMode) "Session radio en direct" else "Appel de groupe sécurisé"
            }
            .onFailure {
                connecting = false
                error = wapiUserFacingError(it, "L’appel de groupe")
            }
    }

    DisposableEffect(engine) {
        onDispose {
            sessionListener?.remove()
            localRenderer?.let { engine.detachRenderer(it, true) }
            remoteRenderer?.let { engine.detachRenderer(it, false) }
            engine.close()
            routeAudio(false)
        }
    }

    Dialog(onDismissRequest = { close(false) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF071827)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0066CF), Color(0xFF071827))))) {
                if (wantsVideo) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { rendererContext -> SurfaceViewRenderer(rendererContext).also { remoteRenderer = it; engine.attachRenderer(it, false) } },
                    )
                }
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { close(false) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Quitter", tint = Color.White) }
                        Column(Modifier.weight(1f)) {
                            Text(if (radioMode) "Radio · ${engine.groupName.ifBlank { group.peer.displayName }}" else engine.groupName.ifBlank { group.peer.displayName }, color = Color.White, fontSize = 18.sp)
                            Text("$status · $participantCount/6 participant${if (participantCount > 1) "s" else ""}", color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
                        }
                        engine.callId.takeIf(String::isNotBlank)?.let { callId ->
                            IconButton(onClick = {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Rejoignez ${if (radioMode) "la radio" else "l’appel"} ${group.peer.displayName} sur WAPI\nwhappy://group-call/$callId")
                                }, "Inviter dans l’appel"))
                            }) { Icon(Icons.Rounded.Share, "Inviter", tint = Color.White) }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (!wantsVideo) {
                        Column(Modifier.align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(118.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(if (radioMode) Icons.Rounded.Radio else Icons.Rounded.Groups, null, tint = Color.White, modifier = Modifier.size(54.dp)) }
                            Text(if (connecting) "Connexion des participants…" else if (radioMode) "Session radio du groupe" else "Appel audio de groupe", Modifier.padding(top = 14.dp), color = Color.White, fontSize = 18.sp)
                            Text("WAPI · audio et vidéo protégés", Modifier.padding(top = 6.dp), color = Color.White.copy(alpha = .66f), fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (wantsVideo && cameraEnabled) {
                        AndroidView(
                            modifier = Modifier.align(Alignment.End).size(104.dp, 150.dp).clip(RoundedCornerShape(18.dp)),
                            factory = { rendererContext -> SurfaceViewRenderer(rendererContext).also { localRenderer = it; engine.attachRenderer(it, true) } },
                        )
                    }
                    Row(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GroupCallControl(if (microphoneEnabled) Icons.Rounded.Mic else Icons.Rounded.MicOff, "Micro", microphoneEnabled) { microphoneEnabled = engine.setMicrophone(!microphoneEnabled) }
                        GroupCallControl(if (speakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff, "Haut-parleur", speakerOn) { routeAudio(!speakerOn) }
                        if (wantsVideo) GroupCallControl(if (cameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, "Caméra", cameraEnabled) { cameraEnabled = engine.setCamera(!cameraEnabled) }
                        if (wantsVideo) GroupCallControl(Icons.Rounded.Cameraswitch, "Retourner", true) { engine.switchCamera() }
                        GroupCallControl(Icons.Rounded.CallEnd, "Quitter", false, destructive = true) { close(false) }
                    }
                    if (canEnd && engine.callId.isNotBlank()) Button(onClick = { close(true) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Terminer pour tous") }
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
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (destructive) Color(0xFFE43D4F) else if (active) WhappyBlue else Color.White.copy(alpha = .18f), contentColor = Color.White),
        ) { Icon(icon, label) }
        Text(label, Modifier.padding(top = 4.dp), color = Color.White, fontSize = 8.sp, maxLines = 1)
    }
}
