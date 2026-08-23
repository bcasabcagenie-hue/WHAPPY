package com.whappy.chat

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import livekit.org.webrtc.SurfaceViewRenderer

private data class WapiLiveCredentials(
    val serverUrl: String,
    val participantToken: String,
    val isHost: Boolean,
)

private data class WapiLiveComment(
    val id: String,
    val authorName: String,
    val text: String,
)

private data class WapiLiveGift(
    val id: String,
    val giftId: String,
    val label: String,
    val symbol: String,
    val senderName: String,
    val equipped: Boolean,
)

private suspend fun joinNativeLive(liveId: String): WapiLiveCredentials {
    val result = FirebaseFunctions.getInstance("europe-west1")
        .getHttpsCallable("joinLiveSession")
        .call(mapOf("liveId" to liveId))
        .await()
    @Suppress("UNCHECKED_CAST")
    val data = result.data as? Map<String, Any?> ?: error("Réponse Live WAPI invalide.")
    val url = data["serverUrl"]?.toString().orEmpty()
    val token = data["participantToken"]?.toString().orEmpty()
    require(url.startsWith("wss://") && token.isNotBlank()) { "Accès au serveur Live indisponible." }
    return WapiLiveCredentials(url, token, data["role"] == "host")
}

/**
 * Studio et lecteur Live Android 100 % natifs. Le média traverse le SFU WebRTC
 * WAPI auto-hébergé ; Firestore ne conserve que l'état, les commentaires et la
 * modération. Les jetons courts restent générés côté serveur.
 */
@Composable
internal fun WapiNativeLiveRoomDialog(
    live: WhappyLive,
    expectedHost: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val functions = remember { FirebaseFunctions.getInstance("europe-west1") }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val room = remember(live.id) {
        LiveKit.create(
            context.applicationContext,
            RoomOptions(adaptiveStream = true, dynacast = true),
        )
    }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var activeVideo by remember { mutableStateOf<VideoTrack?>(null) }
    var connecting by remember { mutableStateOf(true) }
    var connectionLabel by remember { mutableStateOf("Connexion sécurisée…") }
    var fatalError by remember { mutableStateOf<String?>(null) }
    var isHost by remember { mutableStateOf(expectedHost) }
    var microphoneEnabled by remember { mutableStateOf(expectedHost) }
    var cameraEnabled by remember { mutableStateOf(expectedHost && !live.audioOnly) }
    var viewerCount by remember { mutableIntStateOf(live.viewerCount) }
    var reactionCount by remember { mutableIntStateOf(0) }
    var giftCount by remember { mutableIntStateOf(live.giftCount) }
    var allowGiftWearables by remember { mutableStateOf(live.allowGiftWearables) }
    var showGiftPanel by remember { mutableStateOf(false) }
    var activeGift by remember { mutableStateOf<WapiLiveGift?>(null) }
    var commentDraft by remember { mutableStateOf("") }
    val comments = remember { mutableStateListOf<WapiLiveComment>() }
    var eventJob by remember { mutableStateOf<Job?>(null) }
    var liveListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var commentListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var giftListener by remember { mutableStateOf<ListenerRegistration?>(null) }

    fun localVideoTrack(): LocalVideoTrack? = room.localParticipant.videoTrackPublications
        .firstOrNull()
        ?.second as? LocalVideoTrack

    fun close(endLive: Boolean) {
        scope.launch {
            runCatching {
                if (isHost && endLive) {
                    functions.getHttpsCallable("setLiveSessionState")
                        .call(mapOf("liveId" to live.id, "action" to "end"))
                        .await()
                } else if (!isHost) {
                    functions.getHttpsCallable("setLivePresence")
                        .call(mapOf("liveId" to live.id, "action" to "disconnected"))
                        .await()
                }
            }
            room.disconnect()
            onDismiss()
        }
    }

    LaunchedEffect(live.id) {
        try {
            eventJob = launch {
                room.events.events.collect { event ->
                    when (event) {
                        is RoomEvent.TrackSubscribed -> if (event.track is VideoTrack) {
                            activeVideo = event.track as VideoTrack
                        }
                        is RoomEvent.Reconnecting -> connectionLabel = "Reconnexion…"
                        is RoomEvent.Reconnected -> connectionLabel = "En direct"
                        is RoomEvent.Disconnected -> if (fatalError == null) {
                            connectionLabel = "Direct interrompu"
                        }
                        else -> Unit
                    }
                }
            }
            val credentials = joinNativeLive(live.id)
            isHost = credentials.isHost
            room.prepareConnection(credentials.serverUrl, credentials.participantToken)
            room.connect(credentials.serverUrl, credentials.participantToken)
            if (isHost) {
                microphoneEnabled = room.localParticipant.setMicrophoneEnabled(true)
                cameraEnabled = if (live.audioOnly) false else room.localParticipant.setCameraEnabled(true)
                activeVideo = if (live.audioOnly) null else localVideoTrack()
                functions.getHttpsCallable("setLiveSessionState")
                    .call(mapOf("liveId" to live.id, "action" to "start"))
                    .await()
            } else {
                functions.getHttpsCallable("setLivePresence")
                    .call(mapOf("liveId" to live.id, "action" to "connected"))
                    .await()
            }
            connecting = false
            connectionLabel = "En direct"
        } catch (error: Throwable) {
            connecting = false
            fatalError = error.localizedMessage ?: "Le direct n'a pas pu démarrer."
            room.disconnect()
        }
    }

    DisposableEffect(live.id) {
        liveListener = firestore.collection("liveSessions").document(live.id)
            .addSnapshotListener { snapshot, _ ->
                val data = snapshot?.data ?: return@addSnapshotListener
                viewerCount = (data["viewerCount"] as? Number)?.toInt() ?: 0
                reactionCount = (data["reactionCount"] as? Number)?.toInt() ?: 0
                giftCount = (data["giftCount"] as? Number)?.toInt() ?: 0
                allowGiftWearables = data["allowGiftWearables"] == true
                if (!isHost && data["status"] == "ended") fatalError = "Ce direct est terminé."
            }
        commentListener = firestore.collection("liveSessions").document(live.id)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(40)
            .addSnapshotListener { snapshot, _ ->
                val next = snapshot?.documents.orEmpty().reversed().map { document ->
                    WapiLiveComment(
                        document.id,
                        document.getString("authorName") ?: "Membre WAPI",
                        document.getString("text").orEmpty(),
                    )
                }
                comments.clear()
                comments.addAll(next)
            }
        giftListener = firestore.collection("liveSessions").document(live.id)
            .collection("gifts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, _ ->
                val document = snapshot?.documents?.firstOrNull() ?: return@addSnapshotListener
                val createdAt = document.getTimestamp("createdAt")?.toDate()?.time ?: return@addSnapshotListener
                if (System.currentTimeMillis() - createdAt > 12_000L) return@addSnapshotListener
                activeGift = WapiLiveGift(
                    id = document.id,
                    giftId = document.getString("giftId").orEmpty(),
                    label = document.getString("label") ?: "Cadeau WAPI",
                    symbol = document.getString("symbol") ?: "🎁",
                    senderName = document.getString("senderName") ?: "Spectateur WAPI",
                    equipped = document.getBoolean("equipped") == true,
                )
            }
        onDispose {
            liveListener?.remove()
            commentListener?.remove()
            giftListener?.remove()
            eventJob?.cancel()
            activeVideo?.let { track -> renderer?.let(track::removeRenderer) }
            room.disconnect()
            room.release()
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    DisposableEffect(activeVideo, renderer) {
        val track = activeVideo
        val target = renderer
        if (track != null && target != null) track.addRenderer(target)
        onDispose { if (track != null && target != null) track.removeRenderer(target) }
    }

    fun sendComment() {
        val text = commentDraft.trim().take(280)
        if (text.isBlank()) return
        commentDraft = ""
        scope.launch {
            runCatching {
                firestore.collection("liveSessions").document(live.id).collection("comments").add(
                    mapOf(
                        "authorId" to auth.currentUser?.uid.orEmpty(),
                        "authorName" to (auth.currentUser?.displayName ?: auth.currentUser?.phoneNumber ?: "Membre WAPI"),
                        "text" to text,
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
        }
    }

    fun sendGift(giftId: String) {
        showGiftPanel = false
        scope.launch {
            runCatching {
                functions.getHttpsCallable("sendLiveGift").call(mapOf("liveId" to live.id, "giftId" to giftId)).await()
            }.onFailure { connectionLabel = it.localizedMessage ?: "Le cadeau n’a pas été envoyé." }
        }
    }

    LaunchedEffect(activeGift?.id) {
        if (activeGift == null) return@LaunchedEffect
        delay(4_500)
        activeGift = null
    }

    Dialog(
        onDismissRequest = { close(endLive = false) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                if (!live.audioOnly) AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        SurfaceViewRenderer(viewContext).also { view ->
                            room.initVideoRenderer(view)
                            view.setMirror(isHost)
                            renderer = view
                        }
                    },
                    update = { it.setMirror(isHost) },
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = .68f), Color.Transparent, Color.Black.copy(alpha = .86f))),
                    ),
                )
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { close(endLive = false) },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = .35f)),
                        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Fermer", tint = Color.White) }
                        Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (live.audioOnly) "● RADIO" else "● LIVE", Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xFFF0445A)).padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 9.sp)
                                Text("  ${live.title}", color = Color.White, maxLines = 1)
                            }
                            Text("$connectionLabel · $viewerCount spectateur(s) · $reactionCount réaction(s) · $giftCount cadeau(x)", color = Color.White.copy(alpha = .72f), fontSize = 10.sp)
                        }
                        IconButton(
                            onClick = {
                                val invite = Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, live.title)
                                        putExtra(Intent.EXTRA_TEXT, "Rejoignez ${live.title} sur WAPI\nwhappy://live/${live.id}")
                                    },
                                    "Inviter au Live WAPI",
                                )
                                context.startActivity(invite)
                            },
                        ) { Icon(Icons.Rounded.Share, "Inviter", tint = Color.White) }
                        if (isHost) Button(onClick = { close(endLive = true) }) { Icon(Icons.Rounded.Stop, null); Text(" Fin") }
                    }
                    Spacer(Modifier.weight(1f))
                    if (live.audioOnly) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(104.dp).clip(CircleShape).background(WhappyBlue.copy(alpha = .78f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Radio, null, tint = Color.White, modifier = Modifier.size(50.dp)) }
                        Text(live.hostName, Modifier.padding(top = 12.dp), color = Color.White, fontSize = 18.sp)
                        Text("Radio en direct · qualité WebRTC", color = Color.White.copy(alpha = .65f), fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    LazyColumn(
                        Modifier.fillMaxWidth().fillMaxHeight(.23f).padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(comments, key = { it.id }) { comment ->
                            Text(
                                "${comment.authorName}  ${comment.text}",
                                Modifier.clip(RoundedCornerShape(13.dp)).background(Color.Black.copy(alpha = .42f)).padding(horizontal = 10.dp, vertical = 7.dp),
                                color = Color.White,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    if (!isHost && showGiftPanel) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("crown" to "👑", "glasses" to "😎", "halo" to "✨", "trophy" to "🏆").forEach { gift ->
                                FilledIconButton(
                                    onClick = { sendGift(gift.first) },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = WhappyBlue),
                                ) { Text(gift.second, fontSize = 23.sp) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = commentDraft,
                            onValueChange = { commentDraft = it.take(280) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Écrire dans le direct…") },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White.copy(alpha = .92f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                        FilledIconButton(onClick = ::sendComment, modifier = Modifier.padding(start = 7.dp), enabled = commentDraft.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Rounded.Send, "Envoyer")
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        if (isHost) {
                            WapiLiveControl(if (microphoneEnabled) Icons.Rounded.Mic else Icons.Rounded.MicOff, "Micro", microphoneEnabled) {
                                scope.launch { microphoneEnabled = room.localParticipant.setMicrophoneEnabled(!microphoneEnabled) }
                            }
                            if (!live.audioOnly) WapiLiveControl(if (cameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, "Caméra", cameraEnabled) {
                                scope.launch {
                                    cameraEnabled = room.localParticipant.setCameraEnabled(!cameraEnabled)
                                    activeVideo = if (cameraEnabled) localVideoTrack() else null
                                }
                            }
                            if (!live.audioOnly) WapiLiveControl(Icons.Rounded.Cameraswitch, "Retourner", true) { localVideoTrack()?.switchCamera() }
                            WapiLiveControl(Icons.Rounded.AutoAwesome, if (allowGiftWearables) "Cadeaux portés" else "Cadeaux simples", allowGiftWearables) {
                                scope.launch {
                                    runCatching {
                                        functions.getHttpsCallable("setLiveGiftWearables").call(mapOf("liveId" to live.id, "enabled" to !allowGiftWearables)).await()
                                    }
                                }
                            }
                        } else {
                            WapiLiveControl(Icons.Rounded.Favorite, "Réagir", true) {
                                scope.launch {
                                    runCatching {
                                        functions.getHttpsCallable("sendLiveReaction")
                                            .call(mapOf("liveId" to live.id, "reaction" to "heart"))
                                            .await()
                                    }
                                }
                            }
                            WapiLiveControl(Icons.Rounded.AutoAwesome, "Cadeau", showGiftPanel) { showGiftPanel = !showGiftPanel }
                        }
                    }
                }
                activeGift?.let { gift ->
                    Column(Modifier.align(if (gift.equipped) Alignment.TopCenter else Alignment.Center).padding(top = if (gift.equipped) 86.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { giftContext -> WapiGift3DView(giftContext).also { it.setGift(gift.giftId) } },
                                update = { it.setGift(gift.giftId) },
                                onRelease = { it.onPause() },
                            )
                            Text(gift.symbol, fontSize = 42.sp)
                        }
                        Text("${gift.senderName} offre ${gift.label}", Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = .62f)).padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White)
                        if (gift.equipped) Text("Effet portable autorisé par l’animateur", Modifier.padding(top = 6.dp), color = Color.White.copy(alpha = .75f), fontSize = 10.sp)
                    }
                }
                if (connecting) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Connexion au Live WAPI…", Modifier.padding(top = 12.dp), color = Color.White)
                }
                fatalError?.let { message ->
                    Surface(Modifier.align(Alignment.Center).padding(28.dp), color = Color.White, shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Direct indisponible", style = MaterialTheme.typography.titleLarge)
                            Text(message, Modifier.padding(vertical = 12.dp), color = WhappyMuted)
                            Button(onClick = { close(endLive = false) }) { Text("Fermer") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WapiLiveControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (active) WhappyBlue else Color.White.copy(alpha = .18f),
                contentColor = Color.White,
            ),
        ) { Icon(icon, label) }
        Text(label, Modifier.padding(top = 4.dp), color = Color.White, fontSize = 10.sp)
    }
}
